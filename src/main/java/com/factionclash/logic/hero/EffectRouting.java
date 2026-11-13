package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.Reach;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.EffectType;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.To;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Who receives each effect of a move (HERO_FORMAT.md 10.2 and 13) and which moves the engine runs today. The same
 * rules for every receiver: enemies are whoever the hero's side is hostile to (players, NPCs, heroes and monsters
 * alike), allies its own side (players of the faction included). Pure, unit-tested ({@code AbilityRuntimeTest}).
 */
public final class EffectRouting {
    private EffectRouting() {
    }

    /** The receivers an effect goes to (v1 10.2). */
    public enum Receivers {
        /** whoever the form affects ({@code to: targets}) */
        FORM,
        /** the hero, once per use */
        SELF,
        /** {@code to: allies} in a nova, zone or leap: the allies inside the same area */
        ALLIES_IN_AREA,
        /** {@code to: allies} elsewhere: the hero and its allies within the effect's radius of the hero */
        ALLIES_AROUND,
        /** {@code to: ally}: the most hurt ally within the effect's radius, possibly the hero */
        MOST_HURT_ALLY
    }

    public static Receivers receivers(Effect e, Form form) {
        if (HeroCatalogue.reach(e) == Reach.SELF) {
            return Receivers.SELF;
        }
        To to = e.c().to() == null ? To.TARGETS : e.c().to();
        return switch (to) {
            case TARGETS -> Receivers.FORM;
            case SELF -> Receivers.SELF;
            case ALLIES -> form == Form.NOVA || form == Form.ZONE || form == Form.LEAP ? Receivers.ALLIES_IN_AREA
                    : Receivers.ALLIES_AROUND;
            case ALLY -> Receivers.MOST_HURT_ALLY;
        };
    }

    /**
     * Whether an effect lands once per use rather than on each hit (v1 10.3): the self-only effects
     * ({@code move_self}, {@code cooldown_cut}, {@code empower}, {@code summon}...) and every {@code to: self}
     * effect ("the hero, once per use").
     */
    public static boolean oncePerUse(Effect e) {
        return receivers(e, Form.SELF) == Receivers.SELF;
    }

    /**
     * Whether an effect may land on a receiver (v1 13's recipient classes): enemy-only effects on enemies, ally-only
     * effects on the hero's side (the hero included), self-only effects on the hero, and marks on enemies or, sent
     * to the hero, on itself.
     */
    public static boolean mayReach(Effect e, boolean enemy, boolean self) {
        return switch (HeroCatalogue.reach(e)) {
            case ENEMY -> enemy;
            case ALLY -> !enemy;
            case SELF -> self;
            case EITHER -> enemy || self;
        };
    }

    // ------------------------------------------------------------------ what the engine runs (A3, A4)

    /**
     * The forms the engine runs: v1's fourteen and the combo (task A3) and format 2's other seven (task A5:
     * {@code charge}, {@code wave}, {@code rain}, {@code sweep}, {@code grab}, {@code counter}, {@code barrage}).
     * TODO(A12): {@code dive} (MOBILITY_AND_WORK 7.3) needs the airborne runtime; until then a dive move is never picked,
     * answered or followed, so a hero with one fights on the ground with the rest of its kit.
     */
    public static final Set<Form> FORMS = EnumSet.of(Form.MELEE, Form.SHOT, Form.VOLLEY, Form.BOLT, Form.CHAIN,
            Form.CONE, Form.LINE, Form.NOVA, Form.ZONE, Form.LEAP, Form.DASH, Form.SELF, Form.ALLY, Form.ALLIES,
            Form.COMBO, Form.CHARGE, Form.WAVE, Form.RAIN, Form.SWEEP, Form.GRAB, Form.COUNTER, Form.BARRAGE);

    /**
     * The effects the engine runs: A3's (HERO_FORMAT.md 32 phase 4), A4's control and the rest of v1 ({@code stun},
     * {@code taunt}, {@code dispel}, {@code empower}, {@code summon}) and format 2's seven (task A5: {@code guard_break},
     * {@code poise}, {@code silence}, {@code tether}, {@code barrier}, {@code spawn_add}, {@code call_guards}): all of
     * them.
     */
    public static final Set<EffectType> EFFECTS = EnumSet.allOf(EffectType.class);

    /**
     * Whether the engine runs the whole move: its form and every effect of it (its effects, each strike's and each
     * shot's). A move the engine could run only in part is never picked, so nothing plays half a move.
     */
    public static boolean runs(Move m) {
        if (m == null || !FORMS.contains(m.form())) {
            return false;
        }
        for (List<Effect> l : m.effectLists()) {
            if (!runs(l)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every effect of a list is one the engine runs: a {@code spawn_add} or a barrier's {@code focus} only with
     * an add of milestone 1 (the {@code decoy}, HEROES_V2 6.11); the spirit adds come with A8.
     */
    public static boolean runs(List<Effect> effects) {
        for (Effect e : effects) {
            if (!EFFECTS.contains(e.type())) {
                return false;
            }
            if (e instanceof HeroDef.SpawnAdd a && (a.add() == null || a.add().milestone > 1)
                    || e instanceof HeroDef.Barrier b && b.focus() != null && b.focus().milestone > 1) {
                return false;
            }
        }
        return true;
    }
}
