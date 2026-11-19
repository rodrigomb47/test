package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Estimate;
import com.factionclash.logic.campaign.Intel.Look;
import com.factionclash.logic.campaign.Intel.Prior;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        Estimate e = Intel.estimate(null, 0, 100_000, 0, LIKE_OURS, 100_000);
        assertEquals(9, e.soldiers(), EPS);
        assertEquals(1.5, e.power(), EPS);
        assertEquals(0, e.heroPower(), EPS, "heroes nobody saw are not assumed");
        assertEquals(9, e.heads());
        assertEquals(6, Intel.estimate(null, 3, 100_000, 0, LIKE_OURS, 100_000).soldiers(), EPS, "losses seen at our walls come off the prior");
        assertEquals(0, Intel.estimate(null, 30, 100_000, 0, LIKE_OURS, 100_000).soldiers(), EPS, "never below none");
    }

    @Test
    void aFullViewCountsWhatWasSeen() {
        long now = 50_000;
        Estimate e = Intel.estimate(new Look(4, 1.0, 2.0, 0, now), 0, now, 0, LIKE_OURS, now);
        assertEquals(4, e.soldiers(), EPS);
        assertEquals(2.0, e.power(), EPS, "their visible gear");
        assertEquals(8.0, e.soldierPower(), EPS);
    }

    /** Walls hide most of a yard: what was out of view holds what the prior says. */
    @Test
    void aPartialViewAddsThePriorForTheYardOutOfView() {
        long now = 50_000;
        Estimate e = Intel.estimate(new Look(1, 0.25, 2.0, 0, now), 0, now, 0, LIKE_OURS, now);
        assertEquals(1 + 9 * 0.75, e.soldiers(), EPS);
        assertEquals((1 * 2.0 + 6.75 * 1.5) / 7.75, e.power(), EPS, "the soldiers out of view have the prior's power");
        Estimate blind = Intel.estimate(new Look(0, 0, 0, 0, now), 0, now, 0, LIKE_OURS, now);
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
        assertEquals(0, Intel.estimate(empty, 0, seen, 0, LIKE_OURS, seen).soldiers(), EPS);
        assertEquals(4.5, Intel.estimate(empty, 0, seen + Intel.FORGET_TICKS / 2, 0, LIKE_OURS, seen + Intel.FORGET_TICKS / 2).soldiers(), EPS, "half way back");
        assertEquals(9, Intel.estimate(empty, 0, seen + Intel.FORGET_TICKS, 0, LIKE_OURS, seen + Intel.FORGET_TICKS).soldiers(), EPS, "a two-day-old look says little");
        assertEquals(9, Intel.estimate(empty, 0, seen + 10 * Intel.FORGET_TICKS, 0, LIKE_OURS, seen + 10 * Intel.FORGET_TICKS).soldiers(), EPS, "never above the prior");
        assertEquals(1.5, Intel.estimate(empty, 0, seen, 0, LIKE_OURS, seen).power(), EPS, "no soldier seen: the prior's power");
    }

    @Test
    void neverBelowWhatWasSeenUntilLossesAreSeen() {
        long seen = 100_000;
        Look strong = new Look(12, 1.0, 2.5, 0, seen);
        assertEquals(12, Intel.estimate(strong, 0, seen + 5 * Intel.FORGET_TICKS, 0, LIKE_OURS, seen + 5 * Intel.FORGET_TICKS).soldiers(), EPS,
                "a garrison seen larger than the prior stays as seen");
        assertEquals(8, Intel.estimate(strong, 4, seen, 0, LIKE_OURS, seen).soldiers(), EPS, "four of them seen falling at our walls");
    }

    @Test
    void heroesSeenAreCountedAsTheCouncilCountsThem() {
        long now = 1;
        Estimate e = Intel.estimate(new Look(3, 1.0, 1.0, 7.5, now), 0, now, 7.5, LIKE_OURS, now + 40_000);
        assertEquals(7.5, e.heroPower(), EPS, "the heroes' power the council counts now (Intel.heroPower)");
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

    // ------------------------------------------------------------------ the review of the knowledge review (2026-11-19)

    /**
     * Review finding 3: soldiers seen falling at our walls weaken the estimate while the loss is fresh and count for less
     * as it ages (the base hires replacements), as a look does; before, losses seen days ago kept a garrison believed empty.
     */
    @Test
    void lossesSeenLongAgoCountForLessAsTheyAge() {
        long seen = 100_000;
        Look full = new Look(9, 1.0, 2.0, 0, seen);
        assertEquals(1, Intel.estimate(full, 8, seen, 0, LIKE_OURS, seen).soldiers(), EPS, "eight of nine just fell at our walls");
        assertEquals(5, Intel.estimate(full, 8, seen, 0, LIKE_OURS, seen + Intel.FORGET_TICKS / 2).soldiers(), EPS,
                "half a forgetting later, half the losses count");
        assertEquals(9, Intel.estimate(full, 8, seen, 0, LIKE_OURS, seen + 3 * Whereabouts.TICKS_PER_DAY).soldiers(), EPS,
                "three days later the garrison is believed whole again");
        assertEquals(3, Intel.estimate(null, 6, seen, 0, LIKE_OURS, seen).soldiers(), EPS, "fresh losses come off the prior");
        assertEquals(9, Intel.estimate(null, 6, seen, 0, LIKE_OURS, seen + Intel.FORGET_TICKS).soldiers(), EPS, "and fade from it");
        assertEquals(1.0, Intel.weight(seen, seen - 50), EPS, "a sighting dated ahead of the clock counts fully");
    }

    /**
     * Review finding 4: a hero seen at a base counts while the sighting is fresh and less as it ages; before, a hero seen
     * once was counted there for ever at full strength.
     */
    @Test
    void aHeroSightingCountsForLessAsItAges() {
        long seen = 200_000;
        List<Intel.Hero> two = List.of(new Intel.Hero(6.0, seen), new Intel.Hero(4.0, seen + Intel.FORGET_TICKS / 2));
        assertEquals(6.0 + 4.0, Intel.heroPower(two, seen + 0), 1e-6, "both fresh (the later one dated ahead counts fully)");
        assertEquals(3.0 + 4.0, Intel.heroPower(two, seen + Intel.FORGET_TICKS / 2), 1e-6);
        assertEquals(0 + 2.0, Intel.heroPower(two, seen + Intel.FORGET_TICKS), 1e-6);
        assertEquals(0, Intel.heroPower(two, seen + 3 * Whereabouts.TICKS_PER_DAY), 1e-6, "three days later neither counts");
    }

    /** The heroes a report keeps: "id=power@at"; an entry saved before the sightings kept their time reads as seen with its look. */
    @Test
    void theHeroListKeepsEachHerosNewestSighting() {
        Map<String, Intel.Hero> old = Intel.heroes("natsuho=6.5,hinae=5.0", 1000);
        assertEquals(new Intel.Hero(6.5, 1000), old.get("natsuho"));
        assertEquals(new Intel.Hero(5.0, 1000), old.get("hinae"));
        Map<String, Intel.Hero> now = Intel.heroes("hinae=5.5@9000,bad=x@1,=3@4,nothing", 0);
        assertEquals(Map.of("hinae", new Intel.Hero(5.5, 9000)), now, "damaged entries are dropped");
        Map<String, Intel.Hero> merged = Intel.mergeHeroes(old, now);
        assertEquals(new Intel.Hero(6.5, 1000), merged.get("natsuho"), "a hero not seen again keeps its old sighting");
        assertEquals(new Intel.Hero(5.5, 9000), merged.get("hinae"), "the newest sighting wins");
        assertEquals(new Intel.Hero(5.5, 9000), Intel.mergeHeroes(now, old).get("hinae"), "whichever report is older");
        assertEquals(merged, Intel.heroes(Intel.heroes(merged), 0), "the saved form reads back");
        assertTrue(Intel.heroes(Map.<String, Intel.Hero>of()).isEmpty() && Intel.heroes((String) null, 5).isEmpty() && Intel.heroes("", 5).isEmpty());
    }

    /**
     * Review finding 5: against a garrison none of its people has seen (the prior: like its own, by heads and gear), the
     * council's party of every soldier beyond a third went only with 9 or 12 soldiers (at exact equality, so a float
     * rounding of the gear's power could stop it) and never with 10 or 11. Two thirds in whole soldiers: from 7 soldiers on,
     * whatever the gear; never with 6 (three against six); the same answer whether the prior's power is a float or not.
     */
    @Test
    void againstAGarrisonLikeItsOwnTheCouncilGoesFromSevenSoldiersWhateverItsGear() {
        double[] gear = {1.0, 1.15, 1.35, 1.6, 1.85, 2.25, 2.8, 3.0};
        for (double p : gear) {
            double pf = (float) p; // FactionState.soldierPower is a float
            for (int s = 6; s <= 12; s++) {
                int party = Intel.partySize(s, s, 3, 3);
                assertTrue(party >= 3, "a party from " + s);
                Estimate prior = Intel.estimate(new Look(0, 0, 0, 0, 1), 0, 1, 0, new Prior(s, pf), 1);
                boolean go = Intel.twoThirds(party * p, prior.soldierPower(), prior.power());
                assertEquals(s >= 7, go, s + " soldiers in gear " + p + ": party " + party);
            }
        }
    }

    /** Two thirds in whole soldiers, and the old rule where the defence is a whole number of thirds. */
    @Test
    void twoThirdsCountsWholeSoldiers() {
        assertTrue(Intel.twoThirds(4, 6, 1), "four against six");
        assertFalse(Intel.twoThirds(3.9, 6, 1), "a tenth of a soldier short");
        assertTrue(Intel.twoThirds(6, 10, 1), "six against ten: two thirds is six whole soldiers");
        assertFalse(Intel.twoThirds(5, 10, 1));
        assertTrue(Intel.twoThirds(3, 4.5, 1), "three against four and a half (an estimate)");
        assertTrue(Intel.twoThirds(0, 0, 1), "an empty yard");
        assertTrue(Intel.twoThirds(1, 1.4, 1), "less than one and a half soldiers: any party");
        assertTrue(Intel.twoThirds(8 * 2.0, 12 * 2.0, 2.0), "eight iron soldiers against twelve, in power units");
        assertFalse(Intel.twoThirds(7.9 * 2.0, 12 * 2.0, 2.0));
        assertTrue(Intel.twoThirds(6 * 1.35, 9 * (double) (float) 1.35, (float) 1.35), "a float rounding never decides it");
    }

    /**
     * A counter-attack outnumbers the defence 3 to 2, its half again counted to the nearest whole soldier: three against two
     * soldiers whose four fallen comrades were seen a moment ago (the losses already fading by a hair) still go.
     */
    @Test
    void aCounterAttackOutnumbersTheDefenceThreeToTwo() {
        Estimate weakened = Intel.estimate(new Look(6, 1.0, 1.0, 0, 100), 4, 100, 0, LIKE_OURS, 101);
        assertTrue(weakened.soldiers() > 2, "the losses fade from the first tick: " + weakened.soldiers());
        assertTrue(Intel.threeToTwo(3, weakened.soldierPower(), weakened.power()), "three against about two");
        assertFalse(Intel.threeToTwo(3, 2.4, 1), "three against two and a half (four would be needed)");
        assertTrue(Intel.threeToTwo(4, 2.4, 1));
        assertTrue(Intel.threeToTwo(3 * 2.0, 2 * 2.0, 2.0), "in power units");
        assertFalse(Intel.threeToTwo(2.9 * 2.0, 2 * 2.0, 2.0));
        assertTrue(Intel.threeToTwo(0, 0, 1));
    }

    /** The council's party: every soldier beyond a third, at least three, three always at home, never a hero. */
    @Test
    void thePartyIsEverySoldierBeyondAThird() {
        assertEquals(0, Intel.partySize(5, 5, 3, 3), "five: three must stay, two cannot go");
        assertEquals(3, Intel.partySize(6, 6, 3, 3));
        assertEquals(4, Intel.partySize(7, 7, 3, 3));
        assertEquals(6, Intel.partySize(9, 9, 3, 3));
        assertEquals(6, Intel.partySize(10, 10, 3, 3));
        assertEquals(8, Intel.partySize(12, 12, 3, 3), "the config's example: twelve raid with eight");
        assertEquals(5, Intel.partySize(10, 5, 3, 3), "a hero (or five) at home never goes");
        assertEquals(0, Intel.partySize(10, 2, 3, 3), "too few to draft");
    }
}
