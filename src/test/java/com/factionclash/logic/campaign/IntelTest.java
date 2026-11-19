package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Estimate;
import com.factionclash.logic.campaign.Intel.Look;
import com.factionclash.logic.campaign.Intel.Prior;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The war council's estimate of an enemy base (knowledge review of 2026-11-19, known leak 1): only what the faction's
 * own people saw, older looks counting for less, a prior where nobody looked, the losses seen since.
 */
class IntelTest {
    private static final double EPS = 1e-9;
    private static final Prior LIKE_OURS = new Prior(9, 1.5);

    @Test
    void aBaseNobodyLookedAtIsAssumedLikeOurOwn() {
        Estimate e = Intel.estimate(null, 0, LIKE_OURS, 100_000);
        assertEquals(9, e.soldiers(), EPS);
        assertEquals(1.5, e.power(), EPS);
        assertEquals(0, e.heroPower(), EPS, "heroes nobody saw are not assumed");
        assertEquals(9, e.heads());
        assertEquals(6, Intel.estimate(null, 3, LIKE_OURS, 100_000).soldiers(), EPS, "losses seen at our walls come off the prior");
        assertEquals(0, Intel.estimate(null, 30, LIKE_OURS, 100_000).soldiers(), EPS, "never below none");
    }

    @Test
    void aFullViewCountsWhatWasSeen() {
        long now = 50_000;
        Estimate e = Intel.estimate(new Look(4, 1.0, 2.0, 0, now), 0, LIKE_OURS, now);
        assertEquals(4, e.soldiers(), EPS);
        assertEquals(2.0, e.power(), EPS, "their visible gear");
        assertEquals(8.0, e.soldierPower(), EPS);
    }

    /** Walls hide most of a yard: what was out of view holds what the prior says. */
    @Test
    void aPartialViewAddsThePriorForTheYardOutOfView() {
        long now = 50_000;
        Estimate e = Intel.estimate(new Look(1, 0.25, 2.0, 0, now), 0, LIKE_OURS, now);
        assertEquals(1 + 9 * 0.75, e.soldiers(), EPS);
        assertEquals((1 * 2.0 + 6.75 * 1.5) / 7.75, e.power(), EPS, "the soldiers out of view have the prior's power");
        Estimate blind = Intel.estimate(new Look(0, 0, 0, 0, now), 0, LIKE_OURS, now);
        assertEquals(9, blind.soldiers(), EPS, "a look with nothing of the yard in view is the prior");
    }

    /**
     * Known leak 1: a garrison seen gone is believed gone (the council may strike it); as the look ages it is believed to
     * fill up again, back to the prior after FORGET_TICKS. One that marched out unseen is never seen gone.
     */
    @Test
    void aGarrisonSeenGoneIsBelievedGoneUntilTheLookAges() {
        long seen = 100_000;
        Look empty = new Look(0, 1.0, 0, 0, seen);
        assertEquals(0, Intel.estimate(empty, 0, LIKE_OURS, seen).soldiers(), EPS);
        assertEquals(4.5, Intel.estimate(empty, 0, LIKE_OURS, seen + Intel.FORGET_TICKS / 2).soldiers(), EPS, "half way back");
        assertEquals(9, Intel.estimate(empty, 0, LIKE_OURS, seen + Intel.FORGET_TICKS).soldiers(), EPS, "a two-day-old look says little");
        assertEquals(9, Intel.estimate(empty, 0, LIKE_OURS, seen + 10 * Intel.FORGET_TICKS).soldiers(), EPS, "never above the prior");
        assertEquals(1.5, Intel.estimate(empty, 0, LIKE_OURS, seen).power(), EPS, "no soldier seen: the prior's power");
    }

    @Test
    void neverBelowWhatWasSeenUntilLossesAreSeen() {
        long seen = 100_000;
        Look strong = new Look(12, 1.0, 2.5, 0, seen);
        assertEquals(12, Intel.estimate(strong, 0, LIKE_OURS, seen + 5 * Intel.FORGET_TICKS).soldiers(), EPS,
                "a garrison seen larger than the prior stays as seen");
        assertEquals(8, Intel.estimate(strong, 4, LIKE_OURS, seen).soldiers(), EPS, "four of them seen falling at our walls");
    }

    @Test
    void heroesSeenAreCountedAsSeen() {
        long now = 1;
        Estimate e = Intel.estimate(new Look(3, 1.0, 1.0, 7.5, now), 0, LIKE_OURS, now + 40_000);
        assertEquals(7.5, e.heroPower(), EPS);
    }

    /** Of two looks at one base, the one with more of the yard in view; with about as much in view, the newer. */
    @Test
    void theBetterLookSawMoreOfTheYard() {
        Look far = new Look(1, 0.2, 1.0, 0, 10), near = new Look(5, 0.9, 1.0, 0, 5), later = new Look(4, 0.95, 1.0, 0, 20);
        assertSame(near, Intel.better(far, near));
        assertSame(near, Intel.better(near, far));
        assertSame(later, Intel.better(near, later), "about as much in view: the newer");
        assertSame(near, Intel.better(null, near));
        assertSame(far, Intel.better(far, null));
        assertNull(Intel.better(null, null));
    }

    /** A lair nobody looked at is assumed as grown as the campaign's days allow (lairs grow a monster a day). */
    @Test
    void aLairNobodyLookedAtGrowsWithTheDays() {
        assertEquals(5, Intel.lairPrior(5, 12, 0), EPS);
        assertEquals(6.5, Intel.lairPrior(5, 12, 36_000), EPS);
        assertEquals(12, Intel.lairPrior(5, 12, 24_000 * 30), EPS, "never past the lairs' cap");
    }

    @Test
    void aLookKeepsItsNumbersInRange() {
        Look l = new Look(-3, 1.7, 1.0, -2, 0);
        assertEquals(0, l.soldiers());
        assertEquals(1.0, l.visible(), EPS);
        assertEquals(0, l.heroPower(), EPS);
    }
}
