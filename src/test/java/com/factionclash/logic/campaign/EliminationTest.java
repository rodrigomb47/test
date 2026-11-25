package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Elimination.HeroFate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A faction's fall (the owner's rule of 2026-11-25): who counts as fallen, and what becomes of its heroes. */
class EliminationTest {
    @Test
    void onlyAFactionEliminatedAtARecordedTimeHasFallen() {
        assertTrue(Elimination.fallen(false, 48_000), "eliminated on day 2");
        assertFalse(Elimination.fallen(true, 0), "a faction in play");
        assertFalse(Elimination.fallen(false, 0), "not alive but never eliminated (an old save's default, no campaign): its members live");
        assertFalse(Elimination.fallen(true, 48_000), "alive again (a new campaign's faction) has not fallen");
    }

    @Test
    void theStampIsNeverZero() {
        assertEquals(1, Elimination.stamp(0), "an elimination on the first tick still counts");
        assertEquals(1, Elimination.stamp(-5));
        assertEquals(72_000, Elimination.stamp(72_000));
        assertTrue(Elimination.fallen(false, Elimination.stamp(0)));
    }

    @Test
    void aHerosFateFollowsWhereItIs() {
        assertEquals(HeroFate.DIES_THERE, Elimination.heroFate(true, false, true), "loaded: killed there, its death told by the feed");
        assertEquals(HeroFate.TOLD_AND_FREED, Elimination.heroFate(true, false, false), "away, unloaded: one line now, free");
        assertEquals(HeroFate.FREED, Elimination.heroFate(false, false, false), "waiting to rise: free, no second line");
        assertEquals(HeroFate.FREED, Elimination.heroFate(false, false, true), "a corpse still in its death animation: free, no second line");
    }

    @Test
    void aHeroPresumedLostIsAliveWhereItIsAndDiesWithItsFaction() {
        // its last fall was a presumption (its entity went missing): nobody has told its death, so the fall tells it
        assertEquals(HeroFate.TOLD_AND_FREED, Elimination.heroFate(false, true, false),
                "presumed lost, away: its death with the faction is told once, not freed in silence");
        assertEquals(HeroFate.DIES_THERE, Elimination.heroFate(false, true, true),
                "presumed lost but standing in a loaded place: killed there, its death told by the feed");
    }
}
