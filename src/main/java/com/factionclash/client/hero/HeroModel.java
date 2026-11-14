package com.factionclash.client.hero;

import com.factionclash.FactionClash;
import com.factionclash.hero.HeroAnimator;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroLook;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.logic.hero.FormScale;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.HeroLod;
import com.factionclash.logic.hero.Sway;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The one GeckoLib model every hero is drawn with (HEROES_V2 4.4, 4.5, 4.9).
 *
 * <p><b>Files.</b> Geometry, texture (the {@code _p2} texture from phase 2 when the hero has one) and the animation
 * chain come from the hero's registry entry: its own file, its style library, the core library. Every file in the
 * chain must exist (GeckoLib throws when it looks a name up in a missing file), so the chain is cut to the files the
 * client has, and each missing file is logged once; a hero whose geometry is missing (an unknown id, a fixture) is
 * drawn with the neutral {@code _fallback} model, its texture and chain.
 *
 * <p><b>After the animation pass</b> ({@link #setCustomAnimations}, every frame): the head look (the libraries key
 * {@code neck}, never {@code head}); the face ({@link HeroFace}: blink, mood, talking, the {@code face_ctrl} keys);
 * the procedural touches: chains trailing a turn ({@link Sway}, from {@code visual.json} {@code secondary}), a small
 * hit flinch (none under super armour), ears flattened while hurt or angry; and bone visibility, set on every frame
 * because GeckoLib shares a model's bones between instances: {@code fine_} bones by distance, phase bones and phase
 * looks, the weapon in hand or sheathed, the cheeks with the face freeze, the carrier on the {@code mount} bone only
 * while the hero flies, glides or rides (G10; a staff-broom's hand and back copies hide meanwhile); locators track their
 * position within 24 blocks.
 */
public class HeroModel extends GeoModel<HeroEntity> {
    private static final ResourceLocation FALLBACK_MODEL = rl("geo/hero/" + HeroIdentity.FALLBACK + ".geo.json");
    private static final ResourceLocation FALLBACK_TEXTURE = rl("textures/entity/hero/" + HeroIdentity.FALLBACK + ".png");

    /** The resolved chain of each animation chain (by its file names), valid for one GeckoLib cache. */
    private static final Map<List<String>, ResourceLocation[]> CHAINS = new HashMap<>();
    /**
     * What each resolved chain has, by animation name. Keyed by the chain array itself (by identity: {@link #chain}
     * hands out one array per chain until the cache changes), so asking costs two map lookups and no allocation: the
     * controllers ask on every frame.
     */
    private static final Map<ResourceLocation[], Map<String, Boolean>> HAS = new IdentityHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static Map<ResourceLocation, BakedAnimations> resolvedFor;

    /** {@code v.fc_dhip} of the hero being animated (one supplier, set once per frame, read by GeckoLib's Molang). */
    private static double dhip;
    private static final DoubleSupplier DHIP = () -> dhip;

    /** The bones of the chain being swayed (reused: one model serves every hero, on the render thread). */
    private final List<GeoBone> chainBones = new ArrayList<>();

    public HeroModel() {
        HeroAnimator.setLibrary(new HeroAnimator.Library() {
            @Override
            public boolean has(HeroEntity hero, String animation) {
                return HeroModel.this.has(hero, animation);
            }

            @Override
            public Object version(HeroEntity hero) {
                return chain(hero);
            }

            @Override
            public boolean hasBone(HeroEntity hero, String bone) {
                return HeroModel.this.hasBone(hero, bone);
            }
        });
    }

    /** Which bones each baked model has, asked by the wings controller every frame (cached per model and name). */
    private static final Map<BakedGeoModel, Map<String, Boolean>> BONES = new IdentityHashMap<>();

    /** Whether the hero's baked model (its own or the neutral one) has this bone. */
    boolean hasBone(HeroEntity hero, String bone) {
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(getModelResource(hero));
        if (model == null) {
            return false;
        }
        synchronized (BONES) {
            return BONES.computeIfAbsent(model, m -> new HashMap<>()).computeIfAbsent(bone, b -> model.getBone(b).isPresent());
        }
    }

    /** Forgets every resolved chain (a resource reload). */
    static void forgetResolved() {
        synchronized (CHAINS) {
            CHAINS.clear();
            HAS.clear();
            resolvedFor = null;
        }
    }

    // ------------------------------------------------------------------ files

    /** Whether the hero's own geometry is loaded; otherwise it is drawn with the fallback model. */
    private static boolean ownModelLoaded(HeroEntity hero) {
        HeroEntry e = hero.entry();
        if (GeckoLibCache.getBakedModels().containsKey(e.model())) {
            return true;
        }
        warnOnce("model:" + e.id(), "Heroes: no geometry " + e.model() + " for " + e.id() + "; drawn with the neutral model");
        return false;
    }

    /** (GeckoLib 4.9 marks the one-argument forms deprecated but still abstract; the renderer-aware forms call them.) */
    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(HeroEntity hero) {
        return ownModelLoaded(hero) ? hero.entry().model() : FALLBACK_MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(HeroEntity hero) {
        if (!ownModelLoaded(hero)) {
            return FALLBACK_TEXTURE;
        }
        HeroEntry e = hero.entry();
        if (e.visual().phaseTexture() && HeroActions.phase(hero.state()) >= 2) {
            ResourceLocation p2 = e.phase2Texture();
            if (HeroRenderer.resourceExists(p2)) {
                return p2;
            }
        }
        return e.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(HeroEntity hero) {
        return chain(hero)[0];
    }

    /** The style library, then the core library: a hero overrides any shared animation by defining it. */
    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(HeroEntity hero) {
        ResourceLocation[] c = chain(hero);
        ResourceLocation[] out = new ResourceLocation[c.length - 1];
        System.arraycopy(c, 1, out, 0, out.length);
        return out;
    }

    /** The hero's chain, cut to the files this client has (at least one entry, even if it is missing too). */
    private static ResourceLocation[] chain(HeroEntity hero) {
        List<String> names = ownModelLoaded(hero) ? hero.entry().visual().animationChain()
                : HeroRegistry.fallback().visual().animationChain();
        Map<ResourceLocation, BakedAnimations> cache = GeckoLibCache.getBakedAnimations();
        synchronized (CHAINS) {
            if (resolvedFor != cache) {
                CHAINS.clear();
                HAS.clear();
                resolvedFor = cache;
            }
            ResourceLocation[] c = CHAINS.get(names);
            if (c == null) {
                List<ResourceLocation> ok = new ArrayList<>();
                for (String n : names) {
                    ResourceLocation r = rl("animations/hero/" + n + ".animation.json");
                    if (cache.containsKey(r)) {
                        ok.add(r);
                    } else {
                        warnOnce("anim:" + n, "Heroes: animation file " + r + " (in the chain " + names + ") is missing; the chain goes on without it");
                    }
                }
                if (ok.isEmpty()) {
                    ok.add(rl("animations/hero/" + names.get(0) + ".animation.json"));
                }
                c = ok.toArray(new ResourceLocation[0]);
                CHAINS.put(names, c);
            }
            return c;
        }
    }

    /** Whether the hero's chain has an animation (asked by the controllers; cached per resolved chain). */
    boolean has(HeroEntity hero, String animation) {
        ResourceLocation[] c = chain(hero);
        synchronized (CHAINS) {
            Map<String, Boolean> known = HAS.computeIfAbsent(c, k -> new HashMap<>());
            Boolean b = known.get(animation);
            if (b == null) {
                b = false;
                Map<ResourceLocation, BakedAnimations> cache = GeckoLibCache.getBakedAnimations();
                for (ResourceLocation r : c) {
                    BakedAnimations a = cache.get(r);
                    if (a != null && a.getAnimation(animation) != null) {
                        b = true;
                        break;
                    }
                }
                known.put(animation, b);
            }
            return b;
        }
    }

    private static void warnOnce(String key, String message) {
        synchronized (WARNED) {
            if (WARNED.add(key)) {
                FactionClash.LOGGER.warn(message);
            }
        }
    }

    // ------------------------------------------------------------------ before the animation pass

    /**
     * Form-scaled keys (HEROES_V2 4.4, G4): the core library writes the root heights of kneels, sits, sleep and death
     * as {@code "v + k * v.fc_dhip"}; the variable is this model's hip height (the {@code leg_r} pivot) minus the middle
     * of the forms' hip heights. GeckoLib's variables are global, so it is set for the hero about to be animated
     * (applyMolangQueries runs before its controllers); a model without {@code leg_r} plays the middle-form value.
     */
    @Override
    public void applyMolangQueries(AnimationState<HeroEntity> state, double animTime) {
        super.applyMolangQueries(state, animTime);
        GeoBone leg = bone("leg_r");
        dhip = leg == null ? 0 : FormScale.of(leg.getPivotY());
        MathParser.setVariable(FormScale.VARIABLE, DHIP);
    }

    // ------------------------------------------------------------------ after the animation pass

    @Override
    public void setCustomAnimations(HeroEntity hero, long instanceId, AnimationState<HeroEntity> state) {
        HeroClientState s = HeroClientState.of(hero);
        HeroLook look = s.look;
        float partial = state.getPartialTick();
        double now = hero.tickCount + partial;
        double dt = s.lastFrameSeconds < 0 ? 0 : Math.max(0, Math.min(0.25, (now - s.lastFrameSeconds) / 20.0));
        s.lastFrameSeconds = now;

        // head look (the libraries key the neck, never the head)
        GeoBone head = bone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null && !hero.isDeadOrDying()) {
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -60F, 60F) * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -80F, 80F) * Mth.DEG_TO_RAD);
        }

        // the face, from the face_ctrl keys the animation pass left
        int phase = HeroActions.phase(hero.state());
        boolean phaseGlow = false;
        for (HeroLook.Phase p : look.phases()) {
            if (p.phase() <= phase && p.eyesGlow()) {
                phaseGlow = true;
            }
        }
        s.phaseGlow = phaseGlow;
        GeoBone ctrl = bone("face_ctrl");
        s.face.update(hero, hero.tickCount, ctrl == null ? 0 : ctrl.getPosX(), ctrl == null ? 0 : ctrl.getPosY(),
                ctrl == null ? 0 : ctrl.getPosZ(), s.lod.face(), phaseGlow);

        // procedural touches
        boolean superArmour = hero.stateFlag(HeroActions.STATE_SUPER_ARMOUR);
        float hurt = hero.hurtTime > 0 && !superArmour ? Math.max(0, (hero.hurtTime - partial) / 10F) : 0F;
        GeoBone chest = bone("chest");
        if (chest != null && hurt > 0) {
            chest.setRotX(chest.getRotX() + 7F * Mth.DEG_TO_RAD * hurt); // a lean back
        }
        int mood = HeroActions.mood(hero.mood());
        boolean earsBack = hero.hurtTime > 0 || mood == HeroActions.moodCode("angry") || mood == HeroActions.moodCode("hurt");
        s.earsDown += (float) ((earsBack ? 1 : 0) - s.earsDown) * (float) Math.min(1, dt * 10);
        if (s.earsDown > 0.01F) {
            GeoBone l = bone("ear_l"), r = bone("ear_r");
            if (l != null) {
                l.setRotX(l.getRotX() + 25F * Mth.DEG_TO_RAD * s.earsDown);
                l.setRotZ(l.getRotZ() + 15F * Mth.DEG_TO_RAD * s.earsDown);
            }
            if (r != null) {
                r.setRotX(r.getRotX() + 25F * Mth.DEG_TO_RAD * s.earsDown);
                r.setRotZ(r.getRotZ() - 15F * Mth.DEG_TO_RAD * s.earsDown);
            }
        }
        if (s.lod.secondary()) {
            float bodyYaw = Mth.rotLerp(partial, hero.yBodyRotO, hero.yBodyRot);
            for (int i = 0; i < look.chains().size() && i < s.sways.size(); i++) {
                double lag = s.sways.get(i).step(bodyYaw, dt);
                swayChain(look.chains().get(i).bones(), (float) lag);
            }
        }

        visibility(hero, s, look, phase);
        // GeoBone's setters mark a bone as changed, and GeckoLib's next pass leaves a changed bone where it is instead of
        // resetting it: without this the head look and every offset above would add up frame after frame on a bone no
        // animation keys (a head turning round and round). The offsets are this frame's only.
        for (GeoBone b : getAnimationProcessor().getRegisteredBones()) {
            b.resetStateChanges();
        }
    }

    /**
     * Bends a chain against its lag, spread over its bones (each takes an equal share, so the tip trails the most). A
     * chain that hangs down swings sideways (z); one that points back turns (y). Groups of loose bones (two ears) and
     * single bones are left to the baked animations.
     */
    private void swayChain(List<String> names, float lagDegrees) {
        if (names.size() < 2 || Math.abs(lagDegrees) < 0.05F) {
            return;
        }
        List<GeoBone> bones = chainBones;
        bones.clear();
        for (int i = 0; i < names.size(); i++) {
            GeoBone b = bone(names.get(i));
            if (b == null || !bones.isEmpty() && b.getParent() != bones.get(bones.size() - 1)) {
                return; // not a parent-to-child chain on this model
            }
            bones.add(b);
        }
        GeoBone first = bones.get(0), last = bones.get(bones.size() - 1);
        float dy = Math.abs(last.getPivotY() - first.getPivotY());
        float dh = (float) Math.hypot(last.getPivotX() - first.getPivotX(), last.getPivotZ() - first.getPivotZ());
        float share = -lagDegrees * Mth.DEG_TO_RAD / bones.size();
        for (int i = 0; i < bones.size(); i++) {
            GeoBone b = bones.get(i);
            if (dy > dh) {
                b.setRotZ(b.getRotZ() + share);
            } else {
                b.setRotY(b.getRotY() + share);
            }
        }
    }

    /**
     * Bone visibility for this hero (the model's bones are shared, so it is applied every frame): a bone shows when its
     * phase rule, its level of detail and its weapon rule all let it. The plan (which bones, shown or hidden) depends
     * only on the model, the phase, the fine and face levels of detail and the weapon flag, so it is worked out again
     * only when one of those changes ({@link #plan}); each frame just applies it.
     */
    private void visibility(HeroEntity hero, HeroClientState s, HeroLook look, int phase) {
        boolean drawn = hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN);
        // MOBILITY_AND_WORK 4.3 (task A10): the carrier on the mount bone only while it flies, glides or rides
        boolean riding = com.factionclash.logic.hero.MovementRules.showsMount(HeroActions.locoMode(hero.loco()));
        int key = phase & 7 | (s.lod.fine() ? 8 : 0) | (s.lod.face() ? 16 : 0) | (drawn ? 32 : 0) | (riding ? 64 : 0);
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(getModelResource(hero));
        if (key != s.visKey || model != s.visModel || s.visNames == null) {
            Map<String, Boolean> visible = plan(s, look, phase, drawn, riding);
            s.visNames = visible.keySet().toArray(new String[0]);
            s.visShown = new boolean[s.visNames.length];
            for (int i = 0; i < s.visNames.length; i++) {
                s.visShown[i] = visible.get(s.visNames[i]);
            }
            s.visKey = key;
            s.visModel = model;
        }
        for (int i = 0; i < s.visNames.length; i++) {
            GeoBone b = bone(s.visNames[i]);
            if (b != null) {
                b.setHidden(!s.visShown[i]);
            }
        }
        List<String> locators = look.locators();
        for (int i = 0; i < locators.size(); i++) {
            GeoBone b = bone(locators.get(i));
            if (b != null) {
                b.setTrackingMatrices(s.lod.locators());
            }
        }
    }

    /**
     * Which bones show (see {@link #visibility}). Phase rule: a {@code pN_} bone (or one the visual file lists with a
     * phase) from phase N on, a {@code p1_} bone only in phase 1, then each phase look up to the current phase shows and
     * hides its own lists, the later phase winning; {@code fine_} bones by distance; the weapon's drawn and sheathed
     * bones by the weapon flag; the cheeks with the face.
     */
    private Map<String, Boolean> plan(HeroClientState s, HeroLook look, int phase, boolean drawn, boolean riding) {
        Map<String, Boolean> byPhase = new HashMap<>();
        for (Map.Entry<String, Integer> e : look.phaseBones().entrySet()) {
            int p = e.getValue();
            byPhase.put(e.getKey(), p == 1 ? phase == 1 : phase >= p);
        }
        List<HeroLook.Phase> looks = new ArrayList<>(look.phases());
        looks.sort(java.util.Comparator.comparingInt(HeroLook.Phase::phase));
        for (HeroLook.Phase p : looks) {
            if (p.phase() <= phase) {
                p.show().forEach(b -> byPhase.put(b, true));
                p.hide().forEach(b -> byPhase.put(b, false));
            }
        }
        Map<String, Boolean> visible = new HashMap<>(byPhase);
        for (String b : look.fine()) {
            visible.merge(b, s.lod.fine(), Boolean::logicalAnd);
        }
        // the naming rules hold for bones a visual file forgot to list
        for (GeoBone b : getAnimationProcessor().getRegisteredBones()) {
            String n = b.getName();
            if (n.startsWith("fine_") && !visible.containsKey(n)) {
                visible.put(n, s.lod.fine());
            } else if (!byPhase.containsKey(n)) {
                int p = look.phaseOf(n);
                if (p > 0) {
                    visible.merge(n, p == 1 ? phase == 1 : phase >= p, Boolean::logicalAnd);
                }
            }
        }
        boolean weaponIsMount = riding && look.carrierWeapon();
        for (String b : look.weaponDrawn()) {
            visible.merge(b, drawn && !weaponIsMount, Boolean::logicalAnd);
        }
        for (String b : look.weaponSheathed()) {
            visible.merge(b, !drawn && !weaponIsMount, Boolean::logicalAnd);
        }
        for (String b : look.mount()) {
            visible.merge(b, riding, Boolean::logicalAnd);
        }
        visible.merge("cheeks", s.lod.face(), Boolean::logicalAnd);
        return visible;
    }

    private GeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }

    /** The level of detail at this distance after the hero's last one (the renderer's call, before the animation pass). */
    static void updateLod(HeroEntity hero, double distance) {
        HeroClientState s = HeroClientState.of(hero);
        s.lod = HeroLod.of(distance, s.lod);
        hero.animator().setSecondaryPaused(!s.lod.secondary());
    }

    /** The baked model's texture size (GeckoLib bakes UVs normalised by it). */
    static double textureWidth(BakedGeoModel model) {
        return model.properties() == null ? 256 : model.properties().textureWidth();
    }

    static double textureHeight(BakedGeoModel model) {
        return model.properties() == null ? 128 : model.properties().textureHeight();
    }

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, path);
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
