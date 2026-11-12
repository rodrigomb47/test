package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.npc.NpcRole;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One hero the registry knows: its id, the name and title it goes by, its visual side and where its client files
 * are. A1a has no kit format yet: the name comes from the id unless a config override gives one, and the hero fights
 * as a plain soldier of {@link #role()}. A1b adds the definition (moves, lines) beside this.
 *
 * @param ownModel  whether {@code geo/hero/<id>.geo.json} ships; without it the hero is drawn with the neutral
 *                  {@code _fallback} model (test fixtures, a removed hero)
 * @param overrides the config override's fields that changed this hero (empty without one)
 */
public record HeroEntry(String id, String name, String title, HeroVisual visual, Source source, boolean ownModel,
                        List<String> overrides) {

    /**
     * Where the entry came from: the mod's files, a test's fixture, or a preview fixture (an operator's
     * {@code config/factionclash/hero_fixtures/} on a server, a resource pack's visual file on a client): drawn and lined
     * up for screenshots, never given to a faction.
     */
    public enum Source { BUILTIN, TRANSIENT, FIXTURE }

    /** A preview fixture: it may be lined up and posed, never given to a faction (so never drafted). */
    public boolean previewOnly() {
        return source == Source.FIXTURE;
    }

    /** The soldier role it counts as: the one that fits its weapon style (HEROES_V2 3.2). */
    public NpcRole role() {
        NpcRole r = NpcRole.byId(HeroIdentity.combatRoleId(visual.style()));
        return r == null ? NpcRole.SWORDSMAN : r;
    }

    /**
     * The client asks for these every frame (model, texture, the phase-2 texture), so each is made once per model id
     * and kept (a few heroes' worth of entries) instead of a new string and {@code ResourceLocation} per call.
     */
    private static final Map<String, ResourceLocation> MODELS = new ConcurrentHashMap<>(), TEXTURES = new ConcurrentHashMap<>(),
            PHASE2_TEXTURES = new ConcurrentHashMap<>();

    /** The geometry the client draws. */
    public ResourceLocation model() {
        return MODELS.computeIfAbsent(modelId(), m -> rl("geo/hero/" + m + ".geo.json"));
    }

    public ResourceLocation texture() {
        return TEXTURES.computeIfAbsent(modelId(), m -> rl("textures/entity/hero/" + m + ".png"));
    }

    /** The phase-2 texture ({@code <model>_p2.png}; drawn from phase 2 on when the visual file says it has one). */
    public ResourceLocation phase2Texture() {
        return PHASE2_TEXTURES.computeIfAbsent(modelId(), m -> rl("textures/entity/hero/" + m + "_p2.png"));
    }

    /** The first file of the animation chain (the hero's own); the rest are {@link #animationFallbacks()}. */
    public ResourceLocation animation() {
        return rl("animations/hero/" + visual.animationChain().get(0) + ".animation.json");
    }

    /** The style library, then the core library: where GeckoLib looks for an animation the hero's own file lacks. */
    public ResourceLocation[] animationFallbacks() {
        List<String> chain = visual.animationChain();
        ResourceLocation[] out = new ResourceLocation[Math.max(0, chain.size() - 1)];
        for (int i = 1; i < chain.size(); i++) {
            out[i - 1] = rl("animations/hero/" + chain.get(i) + ".animation.json");
        }
        return out;
    }

    /** The id whose model and texture are drawn: its own, or the fallback. */
    public String modelId() {
        return ownModel ? id : HeroIdentity.FALLBACK;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, path);
    }
}
