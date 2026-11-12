package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroSize;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MoodRules;
import com.factionclash.npc.FactionNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.Locale;

/**
 * A hero (HEROES_V2 3): a faction NPC with everything a faction NPC has (inventory, hunger, gear and wear, traits,
 * levels, its job, navigation, stuck recovery, roster record, alarms, war council) plus a hero id, the synced fields
 * the animated model is driven by (10.1) and a hitbox sized from its model (3.6). It counts as one soldier of the
 * role that fits its weapon style and fights with that role's basic attacks until the kit engine (A3) exists.
 *
 * <p>GeckoLib is on both sides, so implementing {@link GeoEntity} is safe on a dedicated server; nothing here
 * touches a client class. Its three animation controllers are {@link HeroAnimator}'s (A2); they run only in the client
 * renderer. The server keeps the synced flags the model reads: the weapon is drawn while it fights, its mood follows
 * {@link MoodRules}, a played action code is cleared once its clip has played (the timing files). An operator can force
 * a mood, the weapon flag, a base pose and any flag for screenshots ({@code /fc admin hero pose}); a <b>preview</b> is a
 * display copy for screenshots ({@code /fc admin hero preview|lineup}): it stands still, cannot be hurt, has no job,
 * hunger or roster entry, is not the campaign's hero and is never counted by the director.
 */
public class HeroEntity extends FactionNpc implements GeoEntity {
    /** The hero id ({@code ""} until set; set once at spawn). */
    private static final EntityDataAccessor<String> DATA_HERO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    /** The action the {@code action} controller plays: code, sequence, speed, variant ({@link HeroActions}). */
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    /** Phase, enraged, super armour, weapon drawn, talking, wind-up, yielding ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Mood, intensity, blush, glowing eyes ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Locomotion mode, one-shot and tired (MOBILITY_AND_WORK 4.2); reserved here, driven by the mobility engine (A10). */
    private static final EntityDataAccessor<Byte> DATA_LOCO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);

    /**
     * A base pose an operator forced for screenshots ({@link HeroClips#FORCED}; 0: the hero's own motion). Not in the
     * design's 10.1 list: an operator tool, one byte, only ever non-zero on a hero posed by hand.
     */
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);

    /** After its last blow or shot it keeps the weapon drawn this long (ticks). */
    public static final int WEAPON_DRAWN_AFTER_FIGHT = 100;

    /** Wings, tails and long weapons reach past the hitbox: the culling box is this much larger (3.1). */
    public static final double CULLING_MARGIN = 1.5;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final HeroAnimator animator = new HeroAnimator(this);

    /** A display copy (screenshots), not the campaign's hero. */
    private boolean preview;
    /** An operator's mood code (-1: the hero's own), weapon flag (-1 own, 0 sheathed, 1 drawn). */
    private int forcedMood = -1;
    private int forcedWeapon = -1;
    /** The tick the played action's clip ends (the code is then cleared), or -1. */
    private int actionEndsAt = -1;
    /** With the marker log on ({@link HeroAnimator#LOG_MARKERS}): the played action's clip, start tick and hit ticks. */
    private String actionLogClip;
    private int actionLogStart;
    private double actionLogSpeed = 1.0;
    private int[] actionLogHits = new int[0];
    /** The client renderer's per-hero state (face clocks, level of detail, sway); null on a server. */
    private Object clientState;

    public HeroEntity(EntityType<? extends HeroEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FactionNpc.createAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HERO, "");
        builder.define(DATA_ACTION, HeroActions.CODE_NONE);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_MOOD, (byte) 0);
        builder.define(DATA_LOCO, (byte) 0);
        builder.define(DATA_POSE, (byte) 0);
    }

    // ------------------------------------------------------------------ identity

    public String heroId() {
        return entityData.get(DATA_HERO);
    }

    /** The registry entry it is drawn and sized with (the fallback for an unknown, dormant hero). */
    public HeroEntry entry() {
        return HeroRegistry.getOrFallback(heroId());
    }

    /** Whether the registry knows its id (a hero whose id it does not know is dormant: plain soldier, fallback look). */
    public boolean dormant() {
        return HeroRegistry.get(heroId()) == null;
    }

    /**
     * Sets the hero id: its size and art scale follow ({@code generic.scale} starts at the model's {@code scale}).
     * Called once, before the entity joins the level, and on loading.
     */
    public void setHeroId(String id) {
        entityData.set(DATA_HERO, id == null ? "" : id);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(entry().visual().scale());
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HERO.equals(key)) {
            refreshDimensions();
        }
    }

    /** Every hero looks for enemies, whatever its duty (v1 25.6). */
    @Override
    public boolean isGuardDuty() {
        return true;
    }

    // ------------------------------------------------------------------ size

    /**
     * The hitbox of HEROES_V2 3.6 from the model: vanilla multiplies these dimensions by {@code generic.scale}, so they
     * are the clamped size divided by it, and the box never exceeds 1.98 blocks however large the scale grows.
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float s = getScale();
        if (!(s > 0)) {
            s = 1.0F;
        }
        HeroSize.Size size = entry().visual().size(s);
        return EntityDimensions.scalable((float) (size.width() / s), (float) (size.height() / s))
                .withEyeHeight((float) (size.eyeHeight() / s));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(CULLING_MARGIN);
    }

    // ------------------------------------------------------------------ synced fields

    public int action() {
        return entityData.get(DATA_ACTION);
    }

    public void setAction(int packed) {
        entityData.set(DATA_ACTION, packed);
    }

    /**
     * Starts an action: a new sequence number, so even the same action again restarts on the client. Its code is
     * cleared again when the clip has played (its timing file's total ticks divided by the speed), so a player who
     * starts tracking the hero later does not see an old action; channels and loops play until the next code.
     */
    public void playAction(int code, double speed, int variant) {
        int packed = HeroActions.nextAction(action(), code, speed, variant);
        setAction(packed);
        actionEndsAt = -1;
        String clip = HeroClips.actionClip(code, entry().visual().look().moves(), entry().visual().look().states());
        HeroTimings.Clip t = clip == null ? null : HeroTimingFiles.clip(entry(), clip);
        if (t != null && !t.loops() && HeroTimingFiles.clip(entry(), clip + ".loop") == null && t.totalTicks() > 0) {
            actionEndsAt = tickCount + HeroTimings.ticksAt(t.totalTicks(), HeroActions.speed(packed));
        }
        actionLogHits = new int[0];
        if (HeroAnimator.LOG_MARKERS && t != null && !level().isClientSide) {
            double played = HeroActions.speed(packed);
            actionLogClip = clip;
            actionLogStart = tickCount;
            actionLogSpeed = played;
            actionLogHits = t.events().stream().filter(e -> "hit".equals(e.type()))
                    .mapToInt(e -> HeroTimings.ticksAt(e.tick(), played)).toArray();
            FactionClash.LOGGER.info("[Heroes] {} server starts {} at speed {} (server tick {}; hit at +{})", heroId(),
                    clip, String.format(Locale.ROOT, "%.2f", played), tickCount, Arrays.toString(actionLogHits));
        }
    }

    /** The marker log's server side: the timing file's hits, on the tick the server counts them (4.8). */
    private void logServerHits() {
        int since = tickCount - actionLogStart;
        for (int hit : actionLogHits) {
            if (since == hit) {
                FactionClash.LOGGER.info("[Heroes] {} server {} hit at +{} ticks (server tick {}, speed {})", heroId(),
                        actionLogClip, since, tickCount, String.format(Locale.ROOT, "%.2f", actionLogSpeed));
            }
        }
    }

    /** Ends the action now (code 0: the client's action controller stops, the base pose takes over). */
    public void stopAction() {
        actionEndsAt = -1;
        if (HeroActions.code(action()) != HeroActions.CODE_NONE) {
            setAction(HeroActions.nextAction(action(), HeroActions.CODE_NONE, 1.0, 0));
        }
    }

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public void setState(byte state) {
        entityData.set(DATA_STATE, state);
    }

    public boolean stateFlag(int flag) {
        return HeroActions.has(state(), flag);
    }

    public void setStateFlag(int flag, boolean on) {
        setState(HeroActions.with(state(), flag, on));
    }

    /** The talking flag (DATA_STATE bit 5): set while a line of dialogue shows; the face cycles its talking mouth. */
    public boolean isTalking() {
        return stateFlag(HeroActions.STATE_TALKING);
    }

    public void setTalking(boolean talking) {
        setStateFlag(HeroActions.STATE_TALKING, talking);
    }

    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    public void setMood(byte mood) {
        entityData.set(DATA_MOOD, mood);
    }

    public byte loco() {
        return entityData.get(DATA_LOCO);
    }

    public void setLoco(byte loco) {
        entityData.set(DATA_LOCO, loco);
    }

    /** The base pose an operator forced (an index of {@link HeroClips#FORCED}; 0: none). */
    public int forcedPose() {
        return entityData.get(DATA_POSE);
    }

    public void setForcedPose(int pose) {
        entityData.set(DATA_POSE, (byte) (pose >= 0 && pose < HeroClips.FORCED.size() ? pose : 0));
    }

    /** An operator's mood (-1: the hero's own, by {@link MoodRules}). */
    public void setForcedMood(int mood) {
        forcedMood = mood >= 0 && mood < HeroActions.MOODS.size() ? mood : -1;
    }

    public int forcedMood() {
        return forcedMood;
    }

    /** An operator's weapon flag: -1 the hero's own (drawn while fighting), 0 sheathed, 1 drawn. */
    public void setForcedWeapon(int weapon) {
        forcedWeapon = weapon == 0 || weapon == 1 ? weapon : -1;
    }

    public int forcedWeapon() {
        return forcedWeapon;
    }

    /** Clears every forced value: mood, weapon, base pose, the state flags (the phase stays) and the action. */
    public void clearPose() {
        forcedMood = -1;
        forcedWeapon = -1;
        setForcedPose(0);
        setState(HeroActions.withPhase(0, HeroActions.phase(state())));
        setMood((byte) 0);
        stopAction();
    }

    // ------------------------------------------------------------------ server: flags the model reads

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            updateFlags();
        }
    }

    /**
     * The weapon is drawn while it fights and for 5 s after its last blow; the mood follows {@link MoodRules} (an
     * operator's forced values first; the hurt wince is vanilla's 10-tick hurt time); a played action's code is cleared
     * when its clip has played.
     */
    private void updateFlags() {
        boolean fighting = getTarget() != null && getTarget().isAlive();
        boolean drawn = forcedWeapon >= 0 ? forcedWeapon == 1 : fighting || tickCount - lastAttackTick() < WEAPON_DRAWN_AFTER_FIGHT;
        if (stateFlag(HeroActions.STATE_WEAPON_DRAWN) != drawn) {
            setStateFlag(HeroActions.STATE_WEAPON_DRAWN, drawn);
        }
        int mood = MoodRules.pick(forcedMood, hurtTime > 0, isSleeping(), stateFlag(HeroActions.STATE_WINDUP), fighting,
                getHealth() / Math.max(1F, getMaxHealth()));
        byte moodByte = mood();
        if (HeroActions.mood(moodByte) != mood) {
            setMood((byte) ((moodByte & ~0xF) | mood));
        }
        if (actionLogHits.length > 0) {
            logServerHits();
        }
        if (actionEndsAt >= 0 && tickCount >= actionEndsAt) {
            stopAction();
        }
    }

    // ------------------------------------------------------------------ preview copies

    /** A display copy for screenshots (see the class comment). */
    public boolean isPreview() {
        return preview;
    }

    /** Makes this a display copy: no AI, cannot be hurt or targeted, never despawns. Called before it joins the level. */
    public void makePreview() {
        preview = true;
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        refreshName();
    }

    @Override
    public boolean isDisplayOnly() {
        return preview;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !preview && super.canBeSeenAsEnemy();
    }

    @Override
    public void refreshName() {
        if (preview) {
            setCustomName(net.minecraft.network.chat.Component.literal(npcName()).withStyle(getFaction().color)
                    .append(net.minecraft.network.chat.Component.literal(" (preview)").withStyle(net.minecraft.ChatFormatting.GRAY)));
            return;
        }
        super.refreshName();
    }

    // ------------------------------------------------------------------ client

    /** The controllers' owner (HEROES_V2 4.4). */
    public HeroAnimator animator() {
        return animator;
    }

    /** The client renderer's state for this hero (an opaque object; null until first drawn, always null on a server). */
    public Object clientState() {
        return clientState;
    }

    public void setClientState(Object state) {
        this.clientState = state;
    }

    // ------------------------------------------------------------------ kit

    /** Better gear replacing a kit item: the kit item vanishes instead of going to the backpack (and the storehouse). */
    @Override
    public void equip(ItemStack stack) {
        if (!stack.isEmpty()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR && HeroKit.isKit(getItemBySlot(slot)) && fits(stack, slot)) {
                    setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        super.equip(stack);
    }

    private static boolean fits(ItemStack stack, EquipmentSlot slot) {
        EquipmentSlot target = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot()
                : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        return target == slot;
    }

    /** Kit items never drop: they vanish here and the hero gets them back when it returns. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (HeroKit.isKit(getItemBySlot(slot))) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < inventory().getContainerSize(); i++) {
            if (HeroKit.isKit(inventory().getItem(i))) {
                inventory().setItem(i, ItemStack.EMPTY);
            }
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl && isDeadOrDying()) {
            HeroManager.onDeath(sl.getServer(), this);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    /** The base, action and secondary controllers (HEROES_V2 4.4; {@link HeroAnimator}). */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        animator.register(controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag h = new CompoundTag();
        h.putString("id", heroId());
        if (preview) {
            h.putBoolean("preview", true);
        }
        if (forcedMood >= 0) {
            h.putInt("mood", forcedMood);
        }
        if (forcedWeapon >= 0) {
            h.putInt("weapon", forcedWeapon);
        }
        if (forcedPose() > 0) {
            h.putInt("pose", forcedPose());
        }
        tag.put("FcHero", h);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag h = tag.getCompound("FcHero");
        setHeroId(h.getString("id"));
        preview = h.getBoolean("preview");
        forcedMood = h.contains("mood") ? h.getInt("mood") : -1;
        forcedWeapon = h.contains("weapon") ? h.getInt("weapon") : -1;
        setForcedPose(h.getInt("pose"));
        if (preview) {
            refreshName();
        }
    }
}
