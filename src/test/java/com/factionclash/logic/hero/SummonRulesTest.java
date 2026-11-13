package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Creature;
import com.factionclash.logic.hero.SummonRules.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SummonRules} (HERO_FORMAT.md 13.5, 16.2, 20; task A4): at most 3 alive per hero (the oldest go), one iron
 * golem a hero (a new one replaces it), per-use limits by creature, 12 per faction (fewer are called, nobody else's
 * go), and the leash and vanishing distances.
 */
class SummonRulesTest {
    @Test
    void perUseLimits() {
        assertEquals(new Plan(3, 0, 0), SummonRules.plan(Creature.WOLF, 3, 0, 0, 0));
        assertEquals(new Plan(2, 0, 0), SummonRules.plan(Creature.SNOW_GOLEM, 3, 0, 0, 0), "snow golems at most 2");
        assertEquals(new Plan(1, 0, 0), SummonRules.plan(Creature.IRON_GOLEM, 3, 0, 0, 0), "one iron golem");
        assertEquals(Plan.NONE, SummonRules.plan(Creature.WOLF, 0, 0, 0, 0));
    }

    @Test
    void threeAHeroTheOldestGo() {
        assertEquals(new Plan(2, 0, 1), SummonRules.plan(Creature.WOLF, 2, 2, 0, 2));
        assertEquals(new Plan(3, 0, 3), SummonRules.plan(Creature.WOLF, 3, 3, 0, 3));
    }

    @Test
    void aNewIronGolemReplacesTheOld() {
        assertEquals(new Plan(1, 1, 0), SummonRules.plan(Creature.IRON_GOLEM, 1, 2, 1, 2));
        assertEquals(new Plan(1, 1, 0), SummonRules.plan(Creature.IRON_GOLEM, 1, 3, 1, 3), "its old golem makes the room");
    }

    @Test
    void twelveAFaction() {
        assertEquals(Plan.NONE, SummonRules.plan(Creature.WOLF, 3, 0, 0, 12), "a full faction calls none");
        assertEquals(new Plan(1, 0, 0), SummonRules.plan(Creature.WOLF, 3, 0, 0, 11));
        assertEquals(new Plan(1, 0, 1), SummonRules.plan(Creature.WOLF, 1, 3, 0, 12), "its own oldest makes room");
        assertEquals(new Plan(2, 0, 2), SummonRules.plan(Creature.WOLF, 2, 3, 0, 11));
    }

    @Test
    void leashAndVanishing() {
        assertFalse(SummonRules.shouldFollow(16));
        assertTrue(SummonRules.shouldFollow(16.5));
        assertFalse(SummonRules.tooFar(48));
        assertTrue(SummonRules.tooFar(48.1));
    }
}
