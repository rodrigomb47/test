package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Condition;
import com.factionclash.logic.hero.HeroDef.ConditionType;
import com.factionclash.logic.hero.HeroDef.TargetKind;
import com.factionclash.logic.hero.Sense.TargetState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Conditions}: every condition type of format 1 and 2 against what a hero senses, read the same for every
 * target (a person is a player, an NPC or a hero alike), and v1's {@code use_when} rules.
 */
class ConditionsTest {
    private static Condition c(ConditionType t, int v) {
        return new Condition(t, v, null, null, null);
    }

    private static Sense s() {
        return new Sense().target(new TargetState().distance(5));
    }

    @Test
    void everyConditionTypeIsRead() {
        Set<ConditionType> seen = EnumSet.noneOf(ConditionType.class);
        // health shares: strictly under / over
        Sense h = s().health(0.4);
        h.target.health(0.3, 20);
        check(seen, ConditionType.TARGET_HEALTH_BELOW, 31, h, true);
        check(seen, ConditionType.TARGET_HEALTH_BELOW, 30, h, false);
        check(seen, ConditionType.TARGET_HEALTH_ABOVE, 29, h, true);
        check(seen, ConditionType.SELF_HEALTH_BELOW, 41, h, true);
        check(seen, ConditionType.SELF_HEALTH_BELOW, 40, h, false);
        check(seen, ConditionType.SELF_HEALTH_ABOVE, 39, h, true);
        // marks
        Sense m = s().mark("toll", 3);
        m.target.mark("hex", 2);
        assertTrue(Conditions.holds(new Condition(ConditionType.TARGET_HAS_MARK, 2, "hex", null, null), m));
        assertFalse(Conditions.holds(new Condition(ConditionType.TARGET_HAS_MARK, 3, "hex", null, null), m));
        assertTrue(Conditions.holds(new Condition(ConditionType.TARGET_HAS_MARK, 0, "hex", null, null), m),
                "least stacks default 1");
        assertTrue(Conditions.holds(new Condition(ConditionType.SELF_HAS_MARK, 3, "toll", null, null), m));
        assertFalse(Conditions.holds(new Condition(ConditionType.SELF_HAS_MARK, 1, "other", null, null), m));
        seen.add(ConditionType.TARGET_HAS_MARK);
        seen.add(ConditionType.SELF_HAS_MARK);
        // target flags
        Sense f = s();
        for (ConditionType t : new ConditionType[]{ConditionType.TARGET_BURNING, ConditionType.TARGET_FREEZING,
                ConditionType.TARGET_SLOWED, ConditionType.TARGET_STUNNED, ConditionType.TARGET_HAS_DOT,
                ConditionType.TARGET_BLOCKING, ConditionType.TARGET_AIRBORNE, ConditionType.TARGET_DRAWING_BOW,
                ConditionType.TARGET_EATING, ConditionType.TARGET_BEHIND, ConditionType.TARGET_UNREACHABLE,
                ConditionType.TARGET_FLYING, ConditionType.TARGET_IN_WATER}) {
            check(seen, t, 0, f, false);
        }
        TargetState on = f.target;
        on.burning = on.freezing = on.slowed = on.stunned = on.hasDot = on.blocking = on.airborne = on.drawingBow =
                on.eating = on.behind = on.unreachable = on.flying = on.inWater = true;
        for (ConditionType t : new ConditionType[]{ConditionType.TARGET_BURNING, ConditionType.TARGET_FREEZING,
                ConditionType.TARGET_SLOWED, ConditionType.TARGET_STUNNED, ConditionType.TARGET_HAS_DOT,
                ConditionType.TARGET_BLOCKING, ConditionType.TARGET_AIRBORNE, ConditionType.TARGET_DRAWING_BOW,
                ConditionType.TARGET_EATING, ConditionType.TARGET_BEHIND, ConditionType.TARGET_UNREACHABLE,
                ConditionType.TARGET_FLYING, ConditionType.TARGET_IN_WATER}) {
            check(seen, t, 0, f, true);
        }
        f.target.present = false;
        assertFalse(Conditions.holds(c(ConditionType.TARGET_BLOCKING, 0), f), "no target: no target condition");
        // heights and distances
        Sense d = s();
        d.target.above(3);
        check(seen, ConditionType.TARGET_ABOVE, 3, d, true);
        check(seen, ConditionType.TARGET_ABOVE, 4, d, false);
        d.target.above(-2);
        check(seen, ConditionType.TARGET_BELOW, 2, d, true);
        check(seen, ConditionType.TARGET_BELOW, 3, d, false);
        check(seen, ConditionType.TARGET_FAR, 5, d, true);
        check(seen, ConditionType.TARGET_FAR, 6, d, false);
        check(seen, ConditionType.TARGET_CLOSE, 5, d, true);
        check(seen, ConditionType.TARGET_CLOSE, 4, d, false);
        // the hero's own state
        Sense o = s().phase(2).enraged(true).engaged(3).state("rage");
        o.addsAlive = 2;
        o.inHomeBase = o.night = o.raining = o.lowCeiling = o.inWater = true;
        o.empowered = o.shielded = o.airborne = o.flying = o.climbing = true;
        o.staminaShare = 0.2;
        o.ally(5, 0.9, false).ally(11, 0.5, true).ally(30, 0.2, true);
        check(seen, ConditionType.PHASE, 2, o, true);
        check(seen, ConditionType.PHASE, 3, o, false);
        check(seen, ConditionType.ENRAGED, 0, o, true);
        check(seen, ConditionType.ENEMIES_ENGAGED, 3, o, true);
        check(seen, ConditionType.ENEMIES_ENGAGED, 4, o, false);
        check(seen, ConditionType.ALLIES_NEAR, 2, o, true);
        check(seen, ConditionType.ALLIES_NEAR, 3, o, false);
        check(seen, ConditionType.ADDS_ALIVE, 2, o, true);
        check(seen, ConditionType.ADDS_ALIVE, 3, o, false);
        check(seen, ConditionType.ALLY_HERO_HEALTH_BELOW, 21, o, true);
        check(seen, ConditionType.ALLY_HERO_HEALTH_BELOW, 20, o, false);
        for (ConditionType t : new ConditionType[]{ConditionType.IN_HOME_BASE, ConditionType.IS_NIGHT,
                ConditionType.IS_RAINING, ConditionType.LOW_CEILING, ConditionType.IN_WATER,
                ConditionType.SELF_EMPOWERED, ConditionType.SELF_SHIELDED, ConditionType.SELF_AIRBORNE,
                ConditionType.SELF_FLYING, ConditionType.SELF_CLIMBING}) {
            check(seen, t, 0, o, true);
            check(seen, t, 0, s(), false);
        }
        check(seen, ConditionType.SELF_STAMINA_BELOW, 21, o, true);
        check(seen, ConditionType.SELF_STAMINA_BELOW, 20, o, false);
        assertTrue(Conditions.holds(new Condition(ConditionType.SELF_STATE, 0, null, null, "rage"), o));
        assertFalse(Conditions.holds(new Condition(ConditionType.SELF_STATE, 0, null, null, "calm"), o));
        assertFalse(Conditions.holds(new Condition(ConditionType.SELF_STATE, 0, null, null, null), o));
        seen.add(ConditionType.SELF_STATE);
        // target_is
        Sense k = s();
        k.target.kind(TargetKind.PERSON);
        assertTrue(Conditions.holds(new Condition(ConditionType.TARGET_IS, 0, null, TargetKind.PERSON, null), k));
        assertFalse(Conditions.holds(new Condition(ConditionType.TARGET_IS, 0, null, TargetKind.HERO, null), k));
        Sense hero = s();
        hero.target.kind(TargetKind.HERO);
        assertTrue(Conditions.holds(new Condition(ConditionType.TARGET_IS, 0, null, TargetKind.PERSON, null), hero),
                "a hero is a person");
        assertFalse(Conditions.holds(new Condition(ConditionType.TARGET_IS, 0, null, null, null), hero));
        seen.add(ConditionType.TARGET_IS);
        assertTrue(Conditions.holds(null, s()), "no condition holds");
        assertEquals(EnumSet.allOf(ConditionType.class), seen, "every condition type has a case");
    }

    private static void check(Set<ConditionType> seen, ConditionType t, int v, Sense s, boolean want) {
        assertEquals(want, Conditions.holds(c(t, v), s), t.id() + " " + v);
        seen.add(t);
    }

    @Test
    void anNpcAndAPlayerRaisingAShieldReadTheSame() {
        // the Sense is filled from the target's own state with the same test; it has no field for who it is
        Sense npc = new Sense().target(new TargetState().distance(3).blocking(true).kind(TargetKind.PERSON));
        Sense player = new Sense().target(new TargetState().distance(3).blocking(true).kind(TargetKind.PERSON));
        for (ConditionType t : ConditionType.values()) {
            assertEquals(Conditions.holds(c(t, 2), npc), Conditions.holds(c(t, 2), player), t.id());
        }
    }

    @Test
    void useWhenRules() {
        String dmg = "\"effects\": [{\"type\": \"damage\", \"amount\": 2}]";
        HeroDef d = V2Files.def(V2Files.hero2("\"moves\": ["
                + "{\"id\": \"always\", \"name\": \"A\", \"form\": \"melee\", " + dmg + "},"
                + "{\"id\": \"low\", \"name\": \"B\", \"form\": \"melee\", \"use_when\": \"self_health_below\","
                + " \"use_value\": 50, " + dmg + "},"
                + "{\"id\": \"mend\", \"name\": \"C\", \"form\": \"allies\", \"radius\": 8, \"effects\": [{\"type\":"
                + " \"heal\", \"amount\": 2}]},"
                + "{\"id\": \"finish\", \"name\": \"D\", \"form\": \"melee\", \"use_when\": \"target_health_below\","
                + " \"use_value\": 30, " + dmg + "},"
                + "{\"id\": \"crowd\", \"name\": \"E\", \"form\": \"nova\", \"radius\": 4, \"use_when\":"
                + " \"enemies_near\", \"use_value\": 3, " + dmg + "},"
                + "{\"id\": \"close\", \"name\": \"F\", \"form\": \"dash\", \"use_when\": \"target_far\", \"use_value\":"
                + " 6, " + dmg + "},"
                + "{\"id\": \"away\", \"name\": \"G\", \"form\": \"nova\", \"use_when\": \"target_close\","
                + " \"use_value\": 3, " + dmg + "},"
                + "{\"id\": \"rest\", \"name\": \"H\", \"form\": \"self\", \"use_when\": \"out_of_combat\","
                + " \"effects\": [{\"type\": \"cleanse\"}]},"
                + "{\"id\": \"single\", \"name\": \"I\", \"form\": \"shot\", \"use_when\": \"enemies_near\","
                + " \"use_value\": 2, " + dmg + "}]"));
        HeroDef.V2 k = d.v2();
        Sense s = new Sense().target(new TargetState().distance(5).health(0.5, 20));
        assertTrue(Conditions.useWhen(k.move("always"), s));
        assertFalse(Conditions.useWhen(k.move("low"), s));
        assertTrue(Conditions.useWhen(k.move("low"), s.health(0.49)));
        assertFalse(Conditions.useWhen(k.move("mend"), s), "nobody hurt in reach");
        s.ally(9, 0.2, false);
        assertFalse(Conditions.useWhen(k.move("mend"), s), "out of its radius");
        s.ally(6, 0.55, false);
        assertTrue(Conditions.useWhen(k.move("mend"), s), "the default: an ally under 60%");
        assertFalse(Conditions.useWhen(k.move("finish"), s));
        s.target.health(0.29, 20);
        assertTrue(Conditions.useWhen(k.move("finish"), s));
        Sense crowd = new Sense().target(new TargetState().distance(2)).enemy(3, 2).enemy(4, 4).enemy(6, 6);
        assertTrue(Conditions.useWhen(k.move("crowd"), crowd), "three within its radius of 4");
        crowd.target.distance = 5;
        assertFalse(Conditions.useWhen(k.move("crowd"), crowd));
        Sense pair = new Sense().target(new TargetState().distance(10)).enemy(15, 5);
        assertTrue(Conditions.useWhen(k.move("single"), pair), "single target: within 6 of the target");
        assertTrue(Conditions.useWhen(k.move("close"), new Sense().target(new TargetState().distance(7))));
        assertFalse(Conditions.useWhen(k.move("close"), new Sense().target(new TargetState().distance(6))));
        assertTrue(Conditions.useWhen(k.move("away"), new Sense().target(new TargetState().distance(2.9))));
        assertFalse(Conditions.useWhen(k.move("away"), new Sense().target(new TargetState().distance(3))));
        Sense calm = new Sense();
        calm.ticksWithoutEnemy = 99;
        assertFalse(Conditions.useWhen(k.move("rest"), calm));
        calm.ticksWithoutEnemy = 100;
        assertTrue(Conditions.useWhen(k.move("rest"), calm), "no enemy for 5 s");
    }

    @Test
    void bigFight() {
        assertFalse(Conditions.bigFight(new Sense().target(new TargetState().distance(3))));
        assertTrue(Conditions.bigFight(new Sense().target(new TargetState().distance(3)).enemy(5, 3).enemy(11, 9)),
                "3 within 12 blocks, the target included");
        assertTrue(Conditions.bigFight(new Sense().target(new TargetState().distance(3).health(1, 40))));
        assertTrue(Conditions.bigFight(new Sense().target(new TargetState().distance(3).kind(TargetKind.HERO))));
        assertTrue(Conditions.bigFight(new Sense().target(new TargetState().distance(3).kind(TargetKind.BOSS))));
        assertTrue(Conditions.bigFight(new Sense().target(new TargetState().distance(3)).health(0.49)));
    }
}
