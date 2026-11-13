package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Condition;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.TargetKind;
import com.factionclash.logic.hero.HeroDef.UseWhen;

/**
 * Whether a condition holds (HERO_FORMAT.md 11, HEROES_V2 6.6 and 6.8, MOBILITY_AND_WORK 7.5) and whether a move's
 * v1 {@code use_when} holds (HERO_FORMAT.md 10.4), against what the hero {@link Sense}s. The same test for every
 * target: a raised shield is {@code target_blocking} whether a player or an NPC soldier raises it, and no condition
 * asks whether anyone is a player ({@code target_is} reads {@code person} for players, NPCs and heroes alike). For an
 * effect's {@code if}, the caller puts the receiver in {@link Sense#target}. Pure, unit-tested ({@code
 * ConditionsTest}).
 */
public final class Conditions {
    private Conditions() {
    }

    /** {@code allies_near}: allies within 12 blocks (HeroCatalogue's condition table). */
    public static final double ALLIES_NEAR_RADIUS = 12;
    /** v1 {@code big_fight}: 3 or more enemies within 12 blocks, or a target with 40+ max health, or a hero or boss. */
    public static final double BIG_FIGHT_RADIUS = 12, BIG_FIGHT_HEALTH = 40;
    public static final int BIG_FIGHT_ENEMIES = 3;
    /** v1 {@code enemies_near} for single-target forms: within 6 blocks of the target. */
    public static final double ENEMIES_NEAR_TARGET_RADIUS = 6;
    /** v1 {@code out_of_combat}: no enemy for 5 s. */
    public static final int OUT_OF_COMBAT_TICKS = 100;

    /** Whether {@code c} holds; null holds. */
    public static boolean holds(Condition c, Sense s) {
        if (c == null) {
            return true;
        }
        Sense.TargetState t = s.target;
        int v = c.value();
        return switch (c.type()) {
            case TARGET_HEALTH_BELOW -> t.present && t.healthShare * 100 < v;
            case TARGET_HEALTH_ABOVE -> t.present && t.healthShare * 100 > v;
            case SELF_HEALTH_BELOW -> s.healthShare * 100 < v;
            case SELF_HEALTH_ABOVE -> s.healthShare * 100 > v;
            case TARGET_HAS_MARK -> t.present && t.marks.getOrDefault(c.mark(), 0) >= Math.max(1, v);
            case SELF_HAS_MARK -> s.marks.getOrDefault(c.mark(), 0) >= Math.max(1, v);
            case TARGET_BURNING -> t.present && t.burning;
            case TARGET_FREEZING -> t.present && t.freezing;
            case TARGET_SLOWED -> t.present && t.slowed;
            case TARGET_STUNNED -> t.present && t.stunned;
            case TARGET_HAS_DOT -> t.present && t.hasDot;
            case TARGET_IS -> t.present && is(t, c.kind());
            case SELF_EMPOWERED -> s.empowered;
            case SELF_SHIELDED -> s.shielded;
            case TARGET_BLOCKING -> t.present && t.blocking;
            case TARGET_AIRBORNE -> t.present && t.airborne;
            case TARGET_DRAWING_BOW -> t.present && t.drawingBow;
            case TARGET_EATING -> t.present && t.eating;
            case TARGET_BEHIND -> t.present && t.behind;
            case TARGET_ABOVE -> t.present && t.heightAbove >= v;
            case TARGET_BELOW -> t.present && -t.heightAbove >= v;
            case TARGET_UNREACHABLE -> t.present && t.unreachable;
            case TARGET_FAR -> t.present && t.distance >= v;
            case TARGET_CLOSE -> t.present && t.distance <= v;
            case TARGET_FLYING -> t.present && t.flying;
            case TARGET_IN_WATER -> t.present && t.inWater;
            case PHASE -> s.phase == v;
            case ENRAGED -> s.enraged;
            case ENEMIES_ENGAGED -> s.enemiesEngaged >= Math.max(1, v);
            case ALLIES_NEAR -> s.alliesWithin(ALLIES_NEAR_RADIUS) >= Math.max(1, v);
            case ADDS_ALIVE -> s.addsAlive >= Math.max(1, v);
            case ALLY_HERO_HEALTH_BELOW -> s.lowestAlly(Double.MAX_VALUE, true) * 100 < v;
            case IN_HOME_BASE -> s.inHomeBase;
            case IS_NIGHT -> s.night;
            case IS_RAINING -> s.raining;
            case LOW_CEILING -> s.lowCeiling;
            case IN_WATER -> s.inWater;
            case SELF_STATE -> c.state() != null && c.state().equals(s.state);
            case SELF_AIRBORNE -> s.airborne;
            case SELF_FLYING -> s.flying;
            case SELF_CLIMBING -> s.climbing;
            case SELF_STAMINA_BELOW -> s.staminaShare * 100 < v;
        };
    }

    /** {@code target_is}: a person is a player, a faction NPC or a hero; the game fills the kinds the same way. */
    static boolean is(Sense.TargetState t, TargetKind k) {
        if (k == null) {
            return false;
        }
        if (k == TargetKind.PERSON) {
            return t.kinds.contains(TargetKind.PERSON) || t.kinds.contains(TargetKind.HERO);
        }
        return t.kinds.contains(k);
    }

    /**
     * v1 10.4: whether a move's {@code use_when} holds. {@code always} needs nothing more than the reach check the
     * selector makes; the others read the sense. {@code out_of_combat} is for friendly forms only (v1 reads it as
     * {@code always} on other forms, which the reader already did).
     */
    public static boolean useWhen(Move m, Sense s) {
        UseWhen w = m.ability().useWhen();
        double v = m.ability().useValue();
        if (w == null) {
            return true;
        }
        Form f = m.form();
        return switch (w) {
            case ALWAYS -> true;
            case SELF_HEALTH_BELOW -> s.healthShare * 100 < v;
            case ALLY_HEALTH_BELOW -> s.lowestAlly(allyReach(m), false) * 100 < v;
            case TARGET_HEALTH_BELOW -> s.target.present && s.target.healthShare * 100 < v;
            case ENEMIES_NEAR -> enemiesInArea(m, s) >= v;
            case TARGET_FAR -> s.target.present && s.target.distance > v;
            case TARGET_CLOSE -> s.target.present && s.target.distance < v;
            case BIG_FIGHT -> bigFight(s);
            case OUT_OF_COMBAT -> !f.friendly() || s.ticksWithoutEnemy >= OUT_OF_COMBAT_TICKS;
        };
    }

    /** v1 {@code big_fight}: 3+ enemies within 12 blocks, a target with 40+ max health or a hero or boss, or under half. */
    public static boolean bigFight(Sense s) {
        if (s.enemiesNearSelf(BIG_FIGHT_RADIUS) >= BIG_FIGHT_ENEMIES || s.healthShare < 0.5) {
            return true;
        }
        Sense.TargetState t = s.target;
        return t.present && (t.maxHealth >= BIG_FIGHT_HEALTH || t.kinds.contains(TargetKind.HERO)
                || t.kinds.contains(TargetKind.BOSS));
    }

    /** v1 {@code enemies_near}: in the ability's area (its radius around where it lands), or 6 blocks around the target. */
    static int enemiesInArea(Move m, Sense s) {
        double r = m.ability().radius();
        Form f = m.form();
        boolean selfCentred = f == Form.NOVA || f == Form.WAVE || f == Form.SWEEP || f == Form.CONE
                || (f == Form.ZONE && m.ability().zoneAt() != HeroDef.ZoneAt.TARGET);
        if (selfCentred) {
            double reach = f == Form.CONE || f == Form.SWEEP ? m.ability().range() : r;
            return s.enemiesNearSelf(reach > 0 ? reach : ENEMIES_NEAR_TARGET_RADIUS);
        }
        return s.enemiesNearTarget(r > 0 ? r : ENEMIES_NEAR_TARGET_RADIUS);
    }

    /** How far an ally form reaches: {@code ally} its range, {@code allies} its radius, {@code self} 0. */
    static double allyReach(Move m) {
        return switch (m.form()) {
            case ALLY -> m.ability().range();
            case ALLIES -> m.ability().radius();
            default -> Math.max(m.ability().radius(), m.ability().range());
        };
    }
}
