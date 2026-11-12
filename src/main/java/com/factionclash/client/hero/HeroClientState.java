package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroLook;
import com.factionclash.logic.hero.HeroLod;
import com.factionclash.logic.hero.Sway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the client keeps per hero between frames (held in {@link HeroEntity#clientState()}): its face clocks, its level
 * of detail (with the one-block band), the springs of its chains, the bone names its renderer treats specially, and
 * the last frame time. Rebuilt when the hero's look changes (a resource reload, another id).
 */
final class HeroClientState {
    final HeroLook look;
    final HeroFace face;
    final Set<String> tint;
    final List<Sway> sways = new ArrayList<>();
    HeroLod.Level lod = HeroLod.Level.FULL;
    /** The phase look makes the eyes glow. */
    boolean phaseGlow;
    double lastFrameSeconds = -1;
    float hurtFlinch;
    float earsDown;
    /**
     * The bone visibility plan ({@code HeroModel.visibility}): the bones it sets and whether each shows, for the model,
     * phase, level of detail and weapon flag in {@link #visKey}/{@link #visModel}; worked out again when those change.
     */
    int visKey = -1;
    Object visModel;
    String[] visNames;
    boolean[] visShown;

    private HeroClientState(HeroEntity hero, HeroLook look) {
        this.look = look;
        this.face = new HeroFace(hero.getUUID(), look.face(), hero.tickCount);
        this.tint = new HashSet<>(look.tint());
        for (HeroLook.Chain c : look.chains()) {
            sways.add(new Sway(c.stiffness(), c.damping()));
        }
    }

    /** The hero's state, made (or remade for a new look) on first use. */
    static HeroClientState of(HeroEntity hero) {
        HeroLook look = hero.entry().visual().look();
        if (hero.clientState() instanceof HeroClientState s && s.look == look) {
            return s;
        }
        HeroClientState s = new HeroClientState(hero, look);
        hero.setClientState(s);
        return s;
    }
}
