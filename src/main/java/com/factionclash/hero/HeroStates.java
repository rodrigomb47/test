package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.StatBonus;
import com.factionclash.logic.hero.HeroRules;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * The state a hero is in (HEROES_V2 12.9, v1 18; task A4): a stance, a transformation, a rage, entered by an
 * {@code empower} effect (a phase or a passive enter one the same way) for its seconds; entering again refreshes it, a
 * different state replaces it. While it lasts: its stat bonuses (held on the hero's own {@link HeroStatus}, each
 * +/-30% at most, so they show and save like any stat), its {@code on_hit} effects beside the basic attack's (one
 * shared on-hit cap), its size ({@code generic.scale}, never past the 1.98-block hitbox), its particle aura every half
 * second, its face (a mood held), {@code skills_locked} and its own move list (read by the move selector through the
 * hero's {@code Sense.state}). Entering plays {@code state.<n>} and says its line. Saved with the hero (its name and
 * end as a game time). The bones a state shows or hides wait for the client's state flag (A5).
 */
final class HeroStates {
    /** The size modifier a state's {@code size} sets ({@code generic.scale}, base-multiplied). */
    static final ResourceLocation SIZE_ID = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_state_size");
    /** The source its stat bonuses carry in the hero's status book. */
    static final String SOURCE = "state";

    private final HeroEntity hero;
    private String name;
    private long until;

    HeroStates(HeroEntity hero) {
        this.hero = hero;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    /** The state it is in now, or null. */
    String name() {
        return name != null && now() < until ? name : null;
    }

    long until() {
        return name == null ? 0 : until;
    }

    /** Its definition (null when it is in none, or its definition has no such state any more). */
    HeroDef.State def() {
        String n = name();
        HeroDef d = hero.combat().def();
        return n == null || d == null || d.v2() == null ? null : d.v2().state(n);
    }

    /** Its index among the hero's states (the {@code state.<n>} slot, 0-2), or -1. */
    private int slot(String n) {
        HeroDef d = hero.combat().def();
        if (d == null || d.v2() == null) {
            return -1;
        }
        List<HeroDef.State> l = d.v2().states();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).name().equals(n)) {
                return i;
            }
        }
        return -1;
    }

    /** Enters (or refreshes) state {@code n} for {@code seconds}. False when the hero has no such state. */
    boolean enter(String n, double seconds) {
        HeroDef d = hero.combat().def();
        HeroDef.State st = n == null || d == null || d.v2() == null ? null : d.v2().state(n);
        if (st == null || seconds <= 0) {
            return false;
        }
        boolean fresh = !n.equals(name());
        if (name != null && !n.equals(name)) {
            exit();
        }
        long now = now();
        name = n;
        until = now + Math.max(1, HeroRules.ticks(seconds));
        HeroStatus status = HeroStatus.of(hero);
        for (StatBonus b : st.stats()) {
            status.book().stat(SOURCE, key(b), b.stat(), b.percent(), seconds, 1, now);
        }
        status.book().consumeStatsDirty();
        HeroStatus.applyStats(hero, now);
        size(st.size());
        if (fresh) {
            int slot = slot(n);
            if (slot >= 0 && slot < 3) {
                hero.playAction(HeroActions.FIRST_STATE + slot, 1.0, 0);
            }
            if (st.line() != null && !st.line().isBlank()) {
                hero.speak(LineEvent.SKILL, st.line());
            }
            HeroEffects.particles(hero.level(), hero, particle(st), 12);
            HeroSounds.stateEntered(hero, n);
        }
        return true;
    }

    private static String key(StatBonus b) {
        return "state." + b.stat().id();
    }

    /** Leaves its state: its bonuses and its size go. */
    void exit() {
        if (name == null) {
            return;
        }
        HeroDef d = hero.combat().def();
        HeroDef.State st = d == null || d.v2() == null ? null : d.v2().state(name);
        HeroStatus status = HeroStatus.peek(hero);
        if (status != null) {
            boolean any = false;
            if (st != null) {
                for (StatBonus b : st.stats()) {
                    any |= status.book().removeStat(SOURCE, key(b));
                }
            }
            if (any) {
                status.book().consumeStatsDirty();
                HeroStatus.applyStats(hero, now());
            }
        }
        size(100);
        name = null;
        until = 0;
    }

    private void size(int percent) {
        AttributeInstance scale = hero.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        double v = Math.max(100, Math.min(110, percent)) / 100.0 - 1;
        AttributeModifier old = scale.getModifier(SIZE_ID);
        if (old != null && Math.abs(old.amount() - v) < 1e-9) {
            return;
        }
        scale.removeModifier(SIZE_ID);
        if (v > 0) {
            scale.addTransientModifier(new AttributeModifier(SIZE_ID, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        hero.refreshDimensions();
    }

    /** Every tick: the state ends on time; its aura every half second while it lasts. */
    void tick() {
        if (name == null) {
            return;
        }
        if (now() >= until) {
            exit();
            return;
        }
        if (hero.tickCount % 10 == 0) {
            HeroDef.State st = def();
            if (st == null) {
                exit();
                return;
            }
            HeroEffects.particles(hero.level(), hero, particle(st), 3);
        }
    }

    private ParticleOptions particle(HeroDef.State st) {
        HeroDef d = hero.combat().def();
        HeroDef.Element e = d == null || d.element() == null ? HeroDef.Element.PHYSICAL : d.element();
        HeroDef.Look look = new HeroDef.Look(st.particle(), st.color(), null, HeroDef.Pose.NONE, false, false);
        return HeroEffects.particle(look, e, d == null ? HeroDef.NO_COLOR : d.color());
    }

    /** The mood its state holds on its face (-1: none). */
    int mood() {
        HeroDef.State st = def();
        return st == null || st.face() == null ? -1 : HeroActions.moodCode(st.face());
    }

    /** The state's own {@code on_hit} effects (none outside a state). */
    List<HeroDef.Effect> onHit() {
        HeroDef.State st = def();
        return st == null ? List.of() : st.onHit();
    }

    void save(CompoundTag h) {
        if (name() != null) {
            h.putString("state", name);
            h.putLong("stateUntil", until);
        }
    }

    /** Loaded: the stats came back with the status book; the size is set again (it is not saved). */
    void load(CompoundTag h) {
        if (h.contains("state")) {
            name = h.getString("state");
            until = h.getLong("stateUntil");
        } else {
            name = null;
            until = 0;
        }
    }

    /** After loading, once the level's time is known: an ended state is left, a running one gets its size back. */
    void restore() {
        if (name == null) {
            return;
        }
        HeroDef.State st = def();
        if (st == null) {
            exit();
        } else {
            size(st.size());
        }
    }
}
