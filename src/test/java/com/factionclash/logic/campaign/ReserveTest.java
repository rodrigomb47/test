package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Recon;
import com.factionclash.logic.campaign.Reserve.Plan;
import com.factionclash.logic.campaign.Sightings.Sighting;
import com.factionclash.logic.campaign.Sightings.Source;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plan a council judges a target by ({@code docs/design/recon.md} 5.5, 10.1 items 10, 12 and 13): the faces counted
 * are a floor, and the plan adds the larger of half again the count and the soldiers seen away, with the heroes known.
 */
class ReserveTest {
    static final long NOW = 100_000;
    static final long REVIVE = 12000;
    static final double EPS = 1e-9;

    static Recon counted(int soldiers, double power, Map<String, Double> heroes, Set<UUID> faces) {
        return new Recon(soldiers, power, heroes, Intel.coverage(12), true, 1200, NOW - 1000, "pillar 13", "Edrin Cole", faces);
    }

    static Recon counted(int soldiers) {
        return counted(soldiers, 1.0, Map.of(), Set.of());
    }

    static Sighting away(UUID id, long at) {
        return new Sighting(id, "stoneguard", "", 1.0, at, 0, 0, Source.HOME, "", Sightings.NO_BASE, false);
    }

    static List<Sighting> away(int n) {
        List<Sighting> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(away(UUID.randomUUID(), NOW - 2000 + i));
        }
        return out;
    }

    static Sighting hero(String id, double power, long at, Source source, String by) {
        return new Sighting(UUID.randomUUID(), "stoneguard", id, power, at, 0, 0, source, by, Sightings.NO_BASE, false);
    }

    static Plan plan(Recon r, List<Sighting> sightings) {
        return Reserve.plan(r, NOW, Reserve.SHARE, sightings, REVIVE);
    }

    /** The fewest soldiers of {@code power} each that a council sends at the interval against this plan. */
    static int neededAtInterval(Plan p, double power) {
        int n = 0;
        while (!Intel.twoThirds(n * power, p.power(), p.unit())) {
            n++;
        }
        return n;
    }

    static int neededToCounter(Plan p) {
        int n = 0;
        while (!Intel.threeToTwo(n * p.unit(), p.power(), p.unit())) {
            n++;
        }
        return n;
    }

    // ---- 10. The plan ----

    @Test
    void theReserveIsTheLargerOfHalfAgainTheCountAndTheSoldiersSeenAway() {
        assertEquals(0.5, Reserve.SHARE);
        assertEquals(6, plan(counted(4), List.of()).soldiers(), "4 counted, nothing seen away: 4 and 2");
        Plan p = plan(counted(4), away(3));
        assertEquals(7, p.soldiers(), "4 counted and 3 seen away: 4 and 3, the larger term, not 9");
        assertTrue(p.fromSightings());
        assertEquals(3, p.reserve());
        assertEquals(0, plan(counted(0), List.of()).soldiers(), "an empty yard and nobody seen away");
        assertEquals(5, plan(counted(0), away(5)).soldiers(), "an empty yard and 5 seen away: the army that may come back");
        assertEquals(6, plan(counted(4), away(2)).soldiers(), "2 seen away: the share is the larger");
        assertFalse(plan(counted(4), away(2)).fromSightings());
        assertEquals(2, plan(counted(1), List.of()).soldiers(), "one and half of one, rounded up");
    }

    @Test
    void aFaceTheWatchCountedOrOneSeenToFallIsNotInTheReserve() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Recon r = counted(2, 1.0, Map.of(), new LinkedHashSet<>(List.of(a, b)));
        Plan p = plan(r, List.of(away(a, NOW - 500), away(b, NOW - 500), away(c, NOW - 500), away(d, NOW - 500)));
        assertEquals(2, p.seenAway().size(), "a and b were counted in their yard: once, in the count");
        assertEquals(4, p.soldiers());
        Plan fell = plan(counted(2), List.of(away(c, NOW - 500).fell(NOW - 400), away(d, NOW - 500)));
        assertEquals(1, fell.seenAway().size(), "one seen to fall is no reserve");
        assertEquals(3, fell.soldiers(), "the share (1) and one seen away: the larger is 1");
    }

    @Test
    void aSightingCountsForHalfADayAndNoLonger() {
        Plan in = plan(counted(0), List.of(away(UUID.randomUUID(), NOW - 12000)));
        assertEquals(1, in.soldiers(), "seen 12000 ticks ago: in");
        Plan out = plan(counted(0), List.of(away(UUID.randomUUID(), NOW - 12001)));
        assertEquals(0, out.soldiers(), "seen 12001 ticks ago: out");
    }

    @Test
    void twoThirdsOfThePlanIsTheCountHeadForHead() {
        for (int c = 1; c <= 11; c++) {
            Plan p = plan(counted(c), List.of());
            assertEquals(c + (c + 1) / 2, p.soldiers());
            assertTrue(Intel.twoThirds(c, p.power(), p.unit()), c + " counted: a party of " + c + " goes");
            assertFalse(Intel.twoThirds(c - 1, p.power(), p.unit()), c + " counted: a party of " + (c - 1) + " waits");
        }
    }

    @Test
    void thePlansUnitIsTheMeanPowerOfItsSoldiers() {
        Plan p = plan(counted(2, 2.0, Map.of(), Set.of()), away(3));
        assertEquals(2 * 2.0 + 3 * 1.0, p.soldierPower(), EPS, "the seen-away term at their own power");
        assertEquals(7.0 / 5, p.unit(), EPS);
        Plan share = plan(counted(4, 2.0, Map.of(), Set.of()), List.of());
        assertEquals(12.0, share.soldierPower(), EPS, "the share at the watch's mean power");
        assertEquals(2.0, share.unit(), EPS);
        Plan none = plan(counted(0, 0, Map.of(), Set.of()), List.of());
        assertEquals(1, none.unit(), EPS, "an empty yard and nobody seen away: a bare soldier");
        assertEquals(0, none.power(), EPS);
        assertEquals(1, none.countedPower(), EPS);
    }

    @Test
    void theHeroesKnownJoinThePlanOnceEach() {
        Recon r = counted(4, 1.0, Map.of("hinae", 6.8), Set.of());
        Plan seenAtWalls = plan(r, List.of(hero("natsuho", 5.0, NOW - 3000, Source.HOME, "")));
        assertEquals(Map.of("hinae", 6.8, "natsuho", 5.0), seenAtWalls.heroes(), "a hero seen at our walls the watch missed joins");
        assertEquals(11.8, seenAtWalls.heroPower(), EPS);
        Plan earlierWatch = plan(r, List.of(hero("natsuho", 5.0, NOW - 9000, Source.WATCH, "Edrin Cole")));
        assertEquals(5.0, earlierWatch.heroes().get("natsuho"), EPS, "one an earlier watch saw and this one missed");
        Plan once = plan(r, List.of(hero("hinae", 4.0, NOW - 3000, Source.HOME, ""), hero("hinae", 5.5, NOW - 2000, Source.FIELD, "Ann")));
        assertEquals(Map.of("hinae", 6.8), once.heroes(), "the watch saw her: counted once, at the watch's power");
        assertTrue(once.knownHeroes().isEmpty());
        Plan twice = plan(counted(4), List.of(hero("hinae", 4.0, NOW - 3000, Source.HOME, ""), hero("hinae", 5.5, NOW - 2000, Source.FIELD, "Ann")));
        assertEquals(Map.of("hinae", 5.5), twice.heroes(), "seen twice away: once, at the newest sighting");
        assertEquals(1, twice.knownHeroes().size());
        Plan stale = plan(counted(4), List.of(hero("natsuho", 5.0, NOW - 12001, Source.HOME, "")));
        assertTrue(stale.heroes().isEmpty(), "a hero seen more than half a day ago is out");
    }

    @Test
    void aHeroSeenFallingCountsFromItsFallUntilHalfADayAfterItsReturn() {
        long fell = 10_000;
        Sighting s = hero("hinae", 6.8, fell, Source.HOME, "").fell(fell);
        Recon r = new Recon(3, 1.0, Map.of(), Intel.coverage(12), true, 1200, fell, "pillar 13", "Edrin Cole", Set.of());
        assertEquals(6.8, Reserve.plan(r, fell + 1, Reserve.SHARE, List.of(s), REVIVE).heroPower(), EPS, "just fallen: she rises");
        Recon later = new Recon(3, 1.0, Map.of(), Intel.coverage(12), true, 1200, fell + 20_000, "pillar 13", "Edrin Cole", Set.of());
        assertEquals(6.8, Reserve.plan(later, fell + REVIVE + 12000, Reserve.SHARE, List.of(s), REVIVE).heroPower(), EPS,
                "12000 after her expected return");
        assertEquals(0, Reserve.plan(later, fell + REVIVE + 12001, Reserve.SHARE, List.of(s), REVIVE).heroPower(), EPS, "not after");
    }

    @Test
    void aKnownHeroWithNoSoldierBesideItCountsNothing() {
        Plan p = plan(counted(0), List.of(hero("hinae", 6.8, NOW - 3000, Source.HOME, "")));
        assertEquals(6.8, p.heroPower(), EPS);
        assertEquals(0, p.power(), EPS, "the soldier backbone: a hero alone counts nothing");
        assertTrue(p.heroesAlone());
        assertEquals("Hinae Shirasuzu (seen at our walls on day 5; alone: not counted)", Reserve.heroText(p, "hinae", "Hinae Shirasuzu"));
        Plan backed = plan(counted(2, 1.5, Map.of(), Set.of()), List.of(hero("hinae", 6.8, NOW - 3000, Source.HOME, "")));
        assertEquals(4.5 + 6.75, backed.power(), EPS, "three soldiers at 1.5 hold up to 6.75 of hero");
    }

    @Test
    void lairsAndCounterAttacksTakeNoShare() {
        assertEquals(0, Reserve.share(true, false));
        assertEquals(0, Reserve.share(true, true));
        assertEquals(0, Reserve.share(false, true));
        assertEquals(0.5, Reserve.share(false, false));
        Plan lair = Reserve.plan(counted(5), NOW, Reserve.share(true, false), List.of(), REVIVE);
        assertEquals(5, lair.soldiers(), "a lair is judged on the monsters counted");
        // lairs start at 5 monsters: the smallest party, 3 soldiers at 1.5 (4.5 in monsters), may raid one only with no share
        assertTrue(Intel.twoThirds(3 * 1.5, lair.power(), lair.unit()));
        Plan withShare = Reserve.plan(counted(5), NOW, Reserve.SHARE, List.of(), REVIVE);
        assertFalse(Intel.twoThirds(3 * 1.5, withShare.power(), withShare.unit()), "5 counted make a plan of 8, two thirds 5");
    }

    @Test
    void anEmptyLedgerPlansTheCountItsShareAndTheWatchsHeroes() {
        Recon r = counted(4, 1.5, Map.of("hinae", 6.8), Set.of());
        Plan p = plan(r, List.of());
        assertEquals(6, p.soldiers());
        assertEquals(Map.of("hinae", 6.8), p.heroes());
        assertEquals(9.0, p.soldierPower(), EPS);
        assertEquals(9.0 + 6.8, p.power(), EPS);
        assertEquals(1.5, p.unit(), EPS);
        assertEquals(p, Reserve.plan(r, NOW, Reserve.SHARE, null, REVIVE), "no ledger at all is an empty one");
    }

    /**
     * Section 5.4's replay: soldiers at 1.5, a hero at 6.8, the soldiers needed at the interval without and with the
     * reserve (5 / 6 against 3 and Hinae; 3 / 5 against 2 and Hinae, where the backbone binds; 9 / 12; 5 / 7; 3 / 5; 0 / 0).
     */
    @Test
    void theReplaysSoldiersNeededWithoutAndWithTheReserve() {
        int[][] rows = {{3, 1, 5, 6}, {2, 1, 3, 5}, {9, 1, 9, 12}, {4, 1, 5, 7}, {5, 0, 3, 5}, {0, 0, 0, 0}, {6, 0, 4, 6}};
        for (int[] row : rows) {
            Map<String, Double> heroes = new HashMap<>();
            if (row[1] == 1) {
                heroes.put("hinae", 6.8);
            }
            Recon r = counted(row[0], 1.5, heroes, Set.of());
            Plan without = Reserve.plan(r, NOW, 0, List.of(), REVIVE);
            Plan with = Reserve.plan(r, NOW, Reserve.SHARE, List.of(), REVIVE);
            String what = row[0] + (row[1] == 1 ? " + a hero" : "");
            assertEquals(row[2], neededAtInterval(without, 1.5), what + " without the reserve");
            assertEquals(row[3], neededAtInterval(with, 1.5), what + " with it");
        }
    }

    // ---- 12. Never an estimate ----

    @Test
    void noPlanWithoutAFreshGoodReconWhateverTheSightingsHold() {
        List<Sighting> many = away(10);
        many.add(hero("hinae", 6.8, NOW - 100, Source.HOME, ""));
        assertNull(Reserve.plan(null, NOW, Reserve.SHARE, many, REVIVE), "no recon");
        Recon blind = new Recon(0, 0, Map.of(), Intel.coverage(3), false, 1200, NOW - 100, "pillar 20", "Edrin Cole", Set.of());
        assertNull(Reserve.plan(blind, NOW, Reserve.SHARE, many, REVIVE), "a blind recon");
        Recon old = new Recon(4, 1, Map.of(), Intel.coverage(12), true, 1200, NOW - 12001, "pillar 13", "Edrin Cole", Set.of());
        assertNull(Reserve.plan(old, NOW, Reserve.SHARE, many, REVIVE), "a stale recon");
        assertNotNull(Reserve.plan(old, NOW - 1, Reserve.SHARE, many, REVIVE));
    }

    // ---- 13. The counter-attack's plan ----

    @Test
    void theCounterAttacksPlanIsTheCountAndTheSeenAwayWithNoShare() {
        int[][] needs = {{3, 5}, {4, 6}, {5, 8}};
        for (int[] n : needs) {
            Plan p = Reserve.plan(counted(n[0]), NOW, Reserve.share(false, true), List.of(), REVIVE);
            assertEquals(n[0], p.soldiers(), "no share");
            assertEquals(n[1], neededToCounter(p), n[0] + " counted need " + n[1] + " at 3 to 2");
        }
        Plan strikersSeen = Reserve.plan(counted(3), NOW, Reserve.share(false, true), away(2), REVIVE);
        assertEquals(5, strikersSeen.soldiers(), "the strikers seen walking away are in it");
        assertEquals(8, neededToCounter(strikersSeen));
    }

    // ---- The lines' reserve (recon.md 5.4) ----

    @Test
    void theLineSaysWhatTheReserveRestsOn() {
        assertEquals("a reserve of 2 (half again the 4 counted)", Reserve.reserveText(plan(counted(4), List.of()), "Stoneguard"));
        assertEquals("no soldier of theirs seen away", Reserve.reserveText(plan(counted(0), List.of()), "Stoneguard"));
        List<Sighting> home = away(3);
        assertEquals("a reserve of 3 (3 Stoneguard soldiers seen at our walls on day 5, not seen to fall)",
                Reserve.reserveText(plan(counted(2), home), "Stoneguard"));
        List<Sighting> mixed = new ArrayList<>(away(2));
        mixed.add(new Sighting(UUID.randomUUID(), "stoneguard", "", 1, NOW - 100, 0, 0, Source.FIELD, "Edrin Cole", Sightings.NO_BASE, false));
        assertEquals("a reserve of 3 (2 Stoneguard soldiers seen at our walls on day 5 and 1 met by scout Edrin Cole on day 5, not seen to fall)",
                Reserve.reserveText(plan(counted(0), mixed), "Stoneguard"));
        Plan withHero = plan(counted(4, 1, Map.of("hinae", 6.8), Set.of()), List.of(hero("benika", 6.8, NOW - 30_000 + 18_000, Source.WATCH, "Edrin Cole")));
        assertEquals("Hinae Shirasuzu", Reserve.heroText(withHero, "hinae", "Hinae Shirasuzu"), "the watch saw her");
        assertEquals("Benika (seen by scout Edrin Cole on day 4)", Reserve.heroText(withHero, "benika", "Benika"));
        assertEquals("a quarter again", Reserve.shareWords(0.25));
        assertEquals("as many again", Reserve.shareWords(1.0));
    }
    // ---- 15. The council's lines (recon.md 5.4, task R5; the sightings' forms come with R11) ----

    @Test
    void theLinesSayWhatThePlanRestsOn() {
        Map<String, String> names = Map.of("hinae", "Hinae Shirasuzu", "benika", "Benika");
        // the share's reason, and a hero the watch saw
        Plan four = plan(counted(4, 1.5, Map.of("hinae", 6.8), Set.of()), List.of());
        String against = Reserve.defenceText(four, false, "Stoneguard", names);
        assertEquals("about 4 defenders and Hinae Shirasuzu (scout Edrin Cole, day 5) and a reserve of 2 (half again the 4 counted)", against);
        assertEquals("Launching a raid with 7 soldiers on the Stoneguard settlement reported at 400, 67, 16, against " + against,
                Reserve.launchLine(7, "the Stoneguard settlement", "400, 67, 16", against));
        assertEquals("waiting on the Stoneguard settlement: 6 soldiers to send, 7 needed against " + against,
                Reserve.waitLine("the Stoneguard settlement", 6, 7, against));
        // the design's example: 4 counted at 1.5, Hinae at 6.8 and a reserve of 2 need 7 soldiers of the same gear
        assertEquals(7, Reserve.needed(four, 1.5, false), "two thirds of the plan in whole soldiers");
        assertEquals(6, Reserve.needed(plan(counted(6), List.of()), 1.0, false), "6 counted, plan 9: two thirds is 6");
        assertEquals(3, Reserve.needed(Reserve.plan(counted(2), NOW, Reserve.share(false, true), List.of(), REVIVE), 1.0, true),
                "a counter-attack on 2 counted, no share: 3 to 2 is 3");
        // an empty yard
        assertEquals("an empty yard (scout Edrin Cole, day 5) and no soldier of theirs seen away",
                Reserve.defenceText(plan(counted(0), List.of()), false, "Stoneguard", names));
        // a hero alone: the backbone counts her at nothing, and the line says so
        assertEquals("an empty yard but for Hinae Shirasuzu (alone: not counted) (scout Edrin Cole, day 5) and no soldier of theirs seen away",
                Reserve.defenceText(plan(counted(0, 1, Map.of("hinae", 6.8), Set.of()), List.of()), false, "Stoneguard", names));
        // two heroes and one soldier; an id no registry knows is shown as it is
        Map<String, Double> two = new java.util.LinkedHashMap<>();
        two.put("hinae", 6.8);
        two.put("unknown_hero", 4.0);
        assertEquals("about 1 defender, Hinae Shirasuzu and unknown_hero (scout Edrin Cole, day 5) and a reserve of 1 (half again the 1 counted)",
                Reserve.defenceText(plan(counted(1, 1, two, Set.of()), List.of()), false, "Stoneguard", names));
        // a counter-attack's plan has no reserve
        assertEquals("about 3 defenders (scout Edrin Cole, day 5) and no soldier of theirs seen away",
                Reserve.defenceText(Reserve.plan(counted(3), NOW, Reserve.share(false, true), List.of(), REVIVE), false, "Stoneguard", names));
        // a lair: its monsters, and no reserve in its line
        Plan lair = Reserve.plan(counted(6), NOW, Reserve.share(true, false), List.of(), REVIVE);
        assertEquals("about 6 monsters (scout Edrin Cole, day 5)", Reserve.defenceText(lair, true, "The Hollow", names));
        assertEquals("no monsters (scout Edrin Cole, day 5)", Reserve.defenceText(Reserve.plan(counted(0), NOW, 0, List.of(), REVIVE), true, "The Hollow", names));
        // an operator's raid with no usable recon
        assertEquals("Launching a raid with 1 soldier on the Hollow lair reported at 1, 2, 3, with no scout's report of it",
                Reserve.launchLine(1, "the Hollow lair", "1, 2, 3", null));
        // a recon with no scout's name
        Recon unnamed = new Recon(2, 1, Map.of(), Intel.coverage(12), true, 1200, NOW - 1000, "pillar 13", "", Set.of());
        assertTrue(Reserve.defenceText(plan(unnamed, List.of()), false, "Stoneguard", names).contains("(a scout's watch, day 5)"));
    }
}
