package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Aggression;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.Protects;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.TargetRules.Candidate;
import com.factionclash.logic.hero.TargetRules.Spacing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TargetRules} (HERO_FORMAT.md 8, 5.2; HEROES_V2 3.5, 6.2; task A4): radii and leashes by aggression, each
 * focus, protection first, the current target kept between equals, an honourable hero letting an escaping one go,
 * no attack tokens, and spacing by role and keep distance.
 */
class TargetRulesTest {
    static Candidate c(double dist, double hp, double max) {
        return new Candidate(dist, hp, max, false, false, false, false, false);
    }

    @Test
    void radiiAndLeashesByAggression() {
        assertEquals(12, TargetRules.searchRadius(Aggression.CAUTIOUS));
        assertEquals(20, TargetRules.searchRadius(Aggression.STEADY));
        assertEquals(28, TargetRules.searchRadius(Aggression.BOLD));
        assertEquals(20, TargetRules.searchRadius(null), "steady by default");
        assertEquals(32, TargetRules.leash(Aggression.CAUTIOUS));
        assertEquals(56, TargetRules.leash(Aggression.STEADY), "the soldiers' leash");
        assertEquals(80, TargetRules.leash(Aggression.BOLD));
    }

    @Test
    void eachFocusPicksItsOwn() {
        List<Candidate> cs = List.of(
                c(3, 20, 20),
                new Candidate(9, 4, 30, true, false, false, false, false),
                new Candidate(12, 60, 120, false, true, false, false, false));
        assertEquals(0, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, cs));
        assertEquals(1, TargetRules.pick(Focus.WEAKEST, Protects.NONE, 50, cs), "fewest health points left");
        assertEquals(2, TargetRules.pick(Focus.STRONGEST, Protects.NONE, 50, cs), "most max health");
        assertEquals(1, TargetRules.pick(Focus.RANGED_FIRST, Protects.NONE, 50, cs));
        assertEquals(2, TargetRules.pick(Focus.HEROES_FIRST, Protects.NONE, 50, cs));
        assertEquals(0, TargetRules.pick(Focus.THREAT_TO_ALLIES, Protects.NONE, 50, cs), "nobody threatens: the nearest");
        assertEquals(-1, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, List.of()));
    }

    @Test
    void protectionComesFirst() {
        List<Candidate> cs = List.of(c(3, 20, 20), new Candidate(15, 20, 20, false, false, true, false, false));
        assertEquals(1, TargetRules.pick(Focus.NEAREST, Protects.ALLIES, 50, cs), "the one attacking an ally");
        assertEquals(1, TargetRules.pick(Focus.THREAT_TO_ALLIES, Protects.NONE, 50, cs));
        assertEquals(0, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, cs), "it protects nobody");
    }

    @Test
    void theCurrentTargetIsKeptBetweenEquals() {
        List<Candidate> cs = List.of(c(5.1, 20, 20), new Candidate(5.2, 20, 20, false, false, false, false, true));
        assertEquals(1, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, cs), "within half a block: no flip-flop");
        List<Candidate> clear = List.of(c(2, 20, 20), new Candidate(6, 20, 20, false, false, false, false, true));
        assertEquals(0, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, clear), "a clearly nearer one wins");
    }

    @Test
    void anHonourableHeroLetsAnEscapingHeroGo() {
        Candidate fleeing = new Candidate(2, 5, 150, false, true, false, true, true);
        List<Candidate> cs = List.of(fleeing, c(10, 20, 20));
        assertEquals(1, TargetRules.pick(Focus.HEROES_FIRST, Protects.NONE, 60, cs));
        assertEquals(0, TargetRules.pick(Focus.HEROES_FIRST, Protects.NONE, 59, cs), "honour 59 gives chase");
        assertFalse(TargetRules.allowed(fleeing, 75));
        assertTrue(TargetRules.allowed(c(1, 1, 1), 100));
    }

    @Test
    void noAttackTokensAnyNumberOfHeroesMayTakeOneTarget() {
        // the same candidate list for three heroes: each picks the same target; nothing counts who else presses it
        List<Candidate> cs = List.of(c(4, 20, 20), c(9, 20, 20));
        for (int hero = 0; hero < 3; hero++) {
            assertEquals(0, TargetRules.pick(Focus.NEAREST, Protects.NONE, 50, cs));
        }
    }

    @Test
    void spacingByRoleAndKeepDistance() {
        assertEquals(Spacing.CLOSE, TargetRules.spacing(Role.STRIKER, 0));
        assertEquals(Spacing.SKIRMISH, TargetRules.spacing(Role.SKIRMISHER, 4));
        assertEquals(Spacing.HOLD, TargetRules.spacing(Role.SUPPORT, 8));
        assertEquals(Spacing.CLOSE, TargetRules.spacing(Role.SKIRMISHER, 0), "a skirmisher told to close in");
        assertTrue(TargetRules.backAway(3.9, 8));
        assertFalse(TargetRules.backAway(4, 8));
        assertFalse(TargetRules.backAway(1, 0));
    }
}
