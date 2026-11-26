package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Intel.Recon;
import com.factionclash.logic.campaign.Intel.ReconFile;
import com.factionclash.logic.campaign.ReconRules.Climb;
import com.factionclash.logic.campaign.ReconRules.Column;
import com.factionclash.logic.campaign.ReconRules.Ending;
import com.factionclash.logic.campaign.ReconRules.Perch;
import com.factionclash.logic.campaign.ReconRules.Placement;
import com.factionclash.logic.campaign.ReconRules.ScoutReport;
import com.factionclash.logic.campaign.ReconRules.Side;
import com.factionclash.logic.campaign.ReconRules.Target;
import com.factionclash.logic.campaign.ReconRules.Verdict;
import com.factionclash.logic.campaign.Whereabouts.State;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused reconnaissance ({@code docs/design/recon.md} 10.1 items 1 to 7): the good look, the estimate, the merge, the
 * council's orders and their endings, the counter-attack after a strike, the watch accumulator, and the vantage, the
 * climb and the hold.
 */
class ReconTest {
    private static final int INTERVAL = 36000;

    static Recon recon(int soldiers, int points, boolean middle, long watch, long at) {
        return new Recon(soldiers, 1.5, Map.of(), Intel.coverage(points), middle, watch, at, "pillar 12", "Edrin Cole", Set.of());
    }

    static ReconFile judged(long at) {
        return new ReconFile(recon(3, 12, true, 1200, at), 0, 0, 0, false);
    }

    // ---- 1. The good look ----

    @Test
    void aGoodLookHasEightOfTheTwentyFivePointsAndTheMiddleForHalfAMinute() {
        assertTrue(recon(3, 8, true, 1200, 100).good(), "8 of 25 with the middle");
        assertFalse(recon(3, 7, true, 1200, 100).good(), "7 of 25: blind, whatever it counted");
        assertFalse(recon(3, 20, false, 1200, 100).good(), "20 of 25 without the middle: the guards' routes missed");
        assertFalse(recon(3, 8, true, 599, 100).good(), "a watch cut short before 600 ticks");
        assertTrue(recon(3, 8, true, 600, 100).good(), "600 ticks is enough");
        assertTrue(recon(0, 25, true, 1200, 100).good(), "a good look at an empty yard is a real 'about 0'");
        assertEquals(8, recon(0, 8, true, 1200, 100).points());
        assertEquals(0.32, Intel.coverage(8), 1e-9);
    }

    // ---- 2. The estimate from a focused report (kept here, apart from IntelTest's old estimate, which R5 deletes) ----

    @Test
    void noEstimateWithoutAFreshGoodRecon() {
        long at = 50_000;
        assertNull(Intel.estimateRecon(null, at), "nobody watched");
        Recon blind = recon(0, 0, false, 1200, at);
        assertNull(Intel.estimateRecon(blind, at), "a blind recon is never 'about 0'");
        assertNull(Intel.estimateRecon(recon(4, 20, false, 1200, at), at), "20 of 25 without the middle is blind too");
        assertNull(Intel.estimateRecon(recon(4, 12, true, 599, at), at), "a watch cut short before 600");
        Recon good = recon(4, 12, true, 1200, at);
        assertNotNull(Intel.estimateRecon(good, at + 12000), "fresh for half a day");
        assertNull(Intel.estimateRecon(good, at + 12001), "stale: a new recon, never an assumption");
        assertTrue(Intel.usable(good, at + 12000));
        assertFalse(Intel.usable(good, at + 12001));
    }

    @Test
    void theEstimateIsTheFacesCountedAndTheHeroesOfThatWatch() {
        long at = 50_000;
        Recon r = new Recon(4, 1.8, Map.of("hinae", 6.8), Intel.coverage(10), true, 1200, at, "pillar 13", "Edrin Cole",
                Set.of(java.util.UUID.randomUUID()));
        Intel.Estimate e = Intel.estimateRecon(r, at + 6000);
        assertEquals(4, e.soldiers(), 1e-9, "the faces counted, with no extrapolation to the yard out of view");
        assertEquals(1.8, e.power(), 1e-9);
        assertEquals(6.8, e.heroPower(), 1e-9, "only the heroes of that watch");
        assertEquals(4, e.heads());
        Recon emptyYard = new Recon(0, 0, Map.of(), Intel.coverage(25), true, 1200, at, "pillar 12", "Edrin Cole", Set.of());
        Intel.Estimate empty = Intel.estimateRecon(emptyYard, at);
        assertEquals(0, empty.soldiers(), 1e-9, "a good look at an empty yard is a real 'about 0'");
        assertEquals(1, empty.power(), 1e-9, "a bare soldier when none was seen");
        assertEquals(0, empty.heroPower(), 1e-9);
        // the same estimate however old within the freshness: no drift, no fading
        assertEquals(e, Intel.estimateRecon(r, at + 12000));
    }

    // ---- 3. Merging (recon.md 4's table; the pure rule, which R2's Report.mergeBase will follow) ----

    @Test
    void mergingKeepsTheNewestGoodReconAndCountsTheFailuresSince() {
        Recon good = recon(4, 12, true, 1200, 10_000);
        ReconFile kept = Intel.mergeRecon(ReconFile.NONE, good, Side.WEST.bit());
        assertSame(good, kept.recon());
        assertEquals(0, kept.failures());
        assertEquals(0, kept.sidesTried(), "a good recon clears the sides tried");
        // a passing look (a position only) never clears, ages or replaces it, on the same trip or much later
        assertEquals(kept, Intel.mergeRecon(kept, null, 0));
        assertEquals(kept, Intel.mergeRecon(kept, null, Side.ALL));
        // a blind recon keeps it and is a failed attempt, with its sides
        ReconFile blind = Intel.mergeRecon(kept, recon(0, 4, true, 1200, 20_000), Side.WEST.bit() | Side.EAST.bit());
        assertSame(good, blind.recon(), "the good recon stays");
        assertEquals(1, blind.failures());
        assertEquals(20_000, blind.failedAt());
        assertEquals(Side.WEST.bit() | Side.EAST.bit(), blind.sidesTried());
        ReconFile blind2 = Intel.mergeRecon(blind, recon(0, 10, false, 1200, 30_000), Side.SOUTH.bit());
        assertEquals(2, blind2.failures());
        assertEquals(Side.WEST.bit() | Side.EAST.bit() | Side.SOUTH.bit(), blind2.sidesTried());
        // a newer good one replaces it and clears the back-off
        Recon newer = recon(2, 15, true, 1200, 40_000);
        ReconFile replaced = Intel.mergeRecon(blind2, newer, Side.NORTH.bit());
        assertSame(newer, replaced.recon());
        assertEquals(0, replaced.failures());
        assertEquals(0, replaced.sidesTried());
        assertFalse(ReconRules.backingOff(replaced, 40_001));
        // a late scout's good recon, older than the kept one, is ignored
        assertEquals(replaced, Intel.mergeRecon(replaced, recon(9, 20, true, 1200, 35_000), 0));
        assertEquals(replaced, Intel.mergeRecon(replaced, recon(9, 20, true, 1200, 40_000), 0), "as old: the kept one stays");
        // a late blind recon (or lost scout) from before the kept good one is no failure in a row
        assertEquals(replaced, Intel.mergeRecon(replaced, recon(0, 0, false, 1200, 39_000), Side.WEST.bit()));
        assertEquals(replaced, Intel.reconFailed(replaced, 39_000, Side.WEST.bit()));
        // ruins drop it, and stay ruins
        ReconFile ruins = Intel.reconRuins();
        assertNull(ruins.recon());
        assertTrue(ruins.fallen());
        assertEquals(ruins, Intel.mergeRecon(ruins, recon(3, 20, true, 1200, 90_000), 0), "ruins stay ruins");
        assertEquals(ruins, Intel.reconFailed(ruins, 90_000, 0));
        assertEquals(ReconFile.NONE, Intel.mergeRecon(null, null, 0));
    }

    // ---- 4. Orders ----

    @Test
    void ordersArePlacedHalfADayBeforeTheIntervalEndsOrTheFirstWarsGate() {
        assertEquals(1000 + INTERVAL, ReconRules.nextRaidAt(0, 1000, INTERVAL), "before any raid: the first war's gate");
        assertEquals(50_000 + INTERVAL, ReconRules.nextRaidAt(50_000, 1000, INTERVAL), "after a raid: its interval");
        long next = ReconRules.nextRaidAt(50_000, 1000, INTERVAL);
        assertFalse(ReconRules.inWindow(next - ReconRules.LEAD_TICKS - 1, next));
        assertTrue(ReconRules.inWindow(next - ReconRules.LEAD_TICKS, next));
        assertTrue(ReconRules.inWindow(next + 50_000, next), "past the interval: still in the window");
        assertEquals(12000, ReconRules.LEAD_TICKS);
        assertEquals(ReconRules.FRESH_TICKS, ReconRules.LEAD_TICKS, "an answered order is fresh at the interval");
    }

    @Test
    void noOrderWhileARaidIsOutOrWithNoParty() {
        assertTrue(ReconRules.ordersNow(false, 3, true, false));
        assertTrue(ReconRules.ordersNow(false, 3, false, true), "an urgent recon outside the window");
        assertFalse(ReconRules.ordersNow(false, 3, false, false), "neither the window nor a strike");
        assertFalse(ReconRules.ordersNow(true, 3, true, true), "its own raid is out");
        assertFalse(ReconRules.ordersNow(false, 0, true, true), "it could not send a party");
    }

    @Test
    void aTargetQualifiesWhenNeverJudgedStaleOrBlindAndNotBackingOff() {
        long now = 100_000;
        assertTrue(ReconRules.ordinaryQualifies(ReconFile.NONE, now, false), "never judged");
        assertTrue(ReconRules.ordinaryQualifies(null, now, false), "never judged (no file)");
        assertFalse(ReconRules.ordinaryQualifies(judged(now - 12000), now, false), "fresh: 12000 old");
        assertTrue(ReconRules.ordinaryQualifies(judged(now - 12001), now, false), "stale: 12001 old");
        assertFalse(ReconRules.ordinaryQualifies(ReconFile.NONE, now, true), "one order per target");
        assertFalse(ReconRules.ordinaryQualifies(Intel.reconRuins(), now, false), "ruins are never watched");
        ReconFile blind = Intel.mergeRecon(ReconFile.NONE, recon(0, 3, false, 1200, now - 1000), Side.WEST.bit());
        assertFalse(ReconRules.ordinaryQualifies(blind, now, false), "a blind attempt backs off");
        assertTrue(ReconRules.ordinaryQualifies(blind, now - 1000 + 12000, false), "and qualifies again after it");
        assertEquals(2, ReconRules.ORDERS_MAX);
    }

    @Test
    void theBackOffDoublesWithEachFailureInARowUpToTwoDays() {
        assertEquals(0, ReconRules.backoffTicks(0));
        assertEquals(12000, ReconRules.backoffTicks(1));
        assertEquals(24000, ReconRules.backoffTicks(2));
        assertEquals(48000, ReconRules.backoffTicks(3));
        assertEquals(48000, ReconRules.backoffTicks(4), "the cap");
        assertEquals(48000, ReconRules.backoffTicks(70), "no overflow");
        ReconFile f = new ReconFile(null, 1000, 2, 0, false);
        assertTrue(ReconRules.backingOff(f, 1000 + 23999));
        assertFalse(ReconRules.backingOff(f, 1000 + 24000));
        // a lost scout: the order's ending counts the failure once, and the back-off doubles
        ReconFile once = Intel.reconFailed(ReconFile.NONE, 5000, 0);
        ReconFile twice = Intel.reconFailed(once, 20000, 0);
        assertEquals(2, twice.failures());
        assertTrue(ReconRules.backingOff(twice, 20000 + 23999));
        assertFalse(ReconRules.backingOff(twice, 20000 + 24000));
    }

    @Test
    void theOrdersGoUrgentFirstThenNeverJudgedNearestThenStaleWeakest() {
        Target urgentFar = new Target(1, true, true, true, 9, 400);
        Target urgentNear = new Target(2, true, false, true, 9, 300);
        Target unjudgedFar = new Target(3, false, true, false, 0, 250);
        Target unjudgedNear = new Target(4, false, true, false, 0, 150);
        Target staleWeak = new Target(5, false, true, true, 2, 300);
        Target staleStrong = new Target(6, false, true, true, 7, 100);
        Target staleWeakNear = new Target(7, false, true, true, 2, 200);
        List<Target> all = List.of(urgentFar, unjudgedFar, staleWeak, urgentNear, staleStrong, unjudgedNear, staleWeakNear);
        assertEquals(new Placement(urgentNear, true), ReconRules.next(all, false, false), "urgent first, the nearest");
        assertEquals(new Placement(unjudgedNear, false), ReconRules.next(all, true, false), "never judged, nearest first");
        List<Target> judgedOnly = List.of(staleWeak, staleStrong, staleWeakNear);
        assertEquals(new Placement(staleWeakNear, false), ReconRules.next(judgedOnly, false, false),
                "stale: the lowest last estimate, then the nearest");
        assertNull(ReconRules.next(all, true, true), "two orders open: one urgent and one ordinary per faction");
        assertNull(ReconRules.next(judgedOnly, false, true), "an ordinary order is open and nothing is urgent");
        assertEquals(new Placement(urgentNear, true), ReconRules.next(all, false, true), "the urgent slot is its own");
        // an urgent target whose slot is taken may still be watched as an ordinary one
        assertEquals(new Placement(urgentFar, false), ReconRules.next(List.of(urgentFar), true, false));
    }

    @Test
    void anUrgentReconIsDueAfterAStrikeOnceTheFightIsOverAndOncePerQuarterInterval() {
        long lastRaid = 100_000, struck = 105_000, now = lastRaid + INTERVAL / 4;
        assertEquals(9000, ReconRules.urgentGap(INTERVAL));
        assertTrue(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, struck, 0, 0));
        assertFalse(ReconRules.urgentDue(now - 1, struck, lastRaid, INTERVAL, struck, 0, 0), "the counter-attack window is not open");
        assertFalse(ReconRules.urgentDue(now, lastRaid - 1, lastRaid, INTERVAL, 0, 0, 0), "struck before our last raid");
        assertFalse(ReconRules.urgentDue(now, 0, lastRaid, INTERVAL, 0, 0, 0), "never struck");
        assertFalse(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, now - 599, 0, 0), "the alarm is not quiet yet");
        assertTrue(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, now - 600, 0, 0), "quiet for 600 ticks");
        assertFalse(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, struck, now - 8999, 0), "an urgent order a moment ago");
        assertTrue(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, struck, now - 9000, 0), "a quarter interval since");
        assertFalse(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, struck, 0, struck + 1), "a good recon since the strike");
        assertTrue(ReconRules.urgentDue(now, struck, lastRaid, INTERVAL, struck, 0, struck - 1), "a recon from before it");
        // the target: no good recon since the strike, even a fresh one from before it
        assertTrue(ReconRules.urgentQualifies(judged(struck - 100), struck + 50, false, struck));
        assertFalse(ReconRules.urgentQualifies(judged(struck + 10), struck + 50, false, struck));
        assertFalse(ReconRules.urgentQualifies(ReconFile.NONE, struck + 50, true, struck), "already ordered");
    }

    @Test
    void anOrderNobodyTookAsksForASecondScoutAfterSixThousandTicks() {
        assertFalse(ReconRules.secondScoutNeeded(false, 1000, 1000 + 5999, 1));
        assertTrue(ReconRules.secondScoutNeeded(false, 1000, 1000 + 6000, 1));
        assertFalse(ReconRules.secondScoutNeeded(false, 1000, 1000 + 6000, 2), "two scouts at most");
        assertFalse(ReconRules.secondScoutNeeded(true, 1000, 1000 + 60000, 1), "taken");
        assertEquals(2, ReconRules.MAX_SCOUTS);
    }

    /** Every ending of recon.md 2.1's table: an order whose scout came home empty-handed must not block later orders. */
    @Test
    void everyOrderEnds() {
        long ordered = 10_000, now = 15_000;
        ReconFile none = ReconFile.NONE;
        assertEquals(Ending.DONE, ReconRules.settle(true, ScoutReport.GOOD, State.HERE, true, ordered, now, none));
        assertEquals(Ending.BLIND, ReconRules.settle(true, ScoutReport.BLIND, State.HERE, true, ordered, now, none));
        Ending empty = ReconRules.settle(true, ScoutReport.EMPTY, State.HERE, true, ordered, now, none);
        assertEquals(Ending.EMPTY_HANDED, empty);
        assertTrue(empty.ends(), "an empty-handed scout ends its order");
        assertTrue(empty.countsFailure());
        assertFalse(Ending.BLIND.countsFailure(), "a blind recon counted once, when it was merged");
        assertTrue(Ending.BLIND.ends());
        for (State lost : new State[]{State.MISSING, State.PRESUMED_DEAD, State.KNOWN_DEAD}) {
            Ending e = ReconRules.settle(true, ScoutReport.NONE, lost, true, ordered, now, none);
            assertEquals(Ending.LOST, e, lost.name());
            assertTrue(e.ends() && e.countsFailure());
        }
        assertEquals(Ending.LOST, ReconRules.settle(true, ScoutReport.NONE, null, true, ordered, now, none), "no record of its scout");
        Ending retrained = ReconRules.settle(true, ScoutReport.NONE, State.HERE, false, ordered, now, none);
        assertEquals(Ending.REOPENED, retrained);
        assertFalse(retrained.ends() || retrained.countsFailure(), "reopened, not a failure");
        assertEquals(Ending.OPEN, ReconRules.settle(true, ScoutReport.NONE, State.AWAY, true, ordered, now, none), "on its way");
        // untaken
        assertEquals(Ending.OPEN, ReconRules.settle(false, ScoutReport.NONE, null, false, ordered, ordered + 11999, none));
        assertEquals(Ending.DROPPED, ReconRules.settle(false, ScoutReport.NONE, null, false, ordered, ordered + 12000, none),
                "untaken for half a day");
        assertEquals(Ending.DROPPED, ReconRules.settle(false, ScoutReport.NONE, null, false, ordered, now, judged(ordered + 1)),
                "the target turned fresh by other means");
        assertEquals(Ending.OPEN, ReconRules.settle(false, ScoutReport.NONE, null, false, ordered, now, judged(ordered - 1)),
                "a recon from before the order does not answer it");
        assertEquals(Ending.DROPPED, ReconRules.settle(false, ScoutReport.NONE, null, false, ordered, now, Intel.reconRuins()),
                "the target was seen in ruins");
        // a taken order on ruins waits for its scout (the table drops only untaken orders)
        assertEquals(Ending.OPEN, ReconRules.settle(true, ScoutReport.NONE, State.AWAY, true, ordered, now, Intel.reconRuins()));
        // the back-off from the endings: a lost scout counts one failure
        ReconFile after = Intel.reconFailed(none, now, Side.WEST.bit());
        assertEquals(1, after.failures());
        assertTrue(ReconRules.backingOff(after, now + 1));
    }

    // ---- 5. The counter-attack ----

    @Test
    void insideTheIntervalThreeToTwoGoesOnlyAgainstAFactionThatStruckUs() {
        assertEquals(Verdict.COUNTER_ATTACK, ReconRules.verdict(false, true, 5, 3, 1));
        assertEquals(Verdict.WAIT, ReconRules.verdict(false, true, 4, 3, 1), "3 counted need 5 at 3 to 2");
        assertEquals(Verdict.WAIT, ReconRules.verdict(false, false, 5, 3, 1),
                "a fresh ordinary recon at 3 to 2 inside the interval waits");
        assertEquals(Verdict.WAIT, ReconRules.verdict(false, false, 50, 0, 1), "even against an empty yard");
        assertEquals(Verdict.ATTACK, ReconRules.verdict(true, false, 2, 3, 1), "at the interval: two thirds, struck or not");
        assertEquals(Verdict.WAIT, ReconRules.verdict(true, false, 1, 3, 1));
        assertTrue(ReconRules.struckSince(120, 100));
        assertFalse(ReconRules.struckSince(100, 100), "struck at the launch of our last raid: before it");
        assertFalse(ReconRules.struckSince(0, 0), "never struck");
    }

    /** A weaker base of a faction that did not strike us does not stop the counter-attack on the striker's. */
    @Test
    void insideTheIntervalOnlyTheStrikersSettlementsAreCandidates() {
        record Base(String name, boolean settlement, boolean struck, double plan) {
        }
        Base striker = new Base("striker", true, true, 3);
        Base bystander = new Base("bystander", true, false, 1);
        Base lair = new Base("lair", false, false, 0.5);
        List<Base> all = List.of(striker, bystander, lair);
        double party = 5;
        // inside the interval
        assertTrue(ReconRules.considersTargets(false, true));
        Base inside = all.stream().filter(b -> ReconRules.candidate(false, b.settlement(), b.struck()))
                .min(Comparator.comparingDouble(Base::plan)).orElseThrow();
        assertEquals(striker, inside, "the bystander's weaker base is no candidate inside the interval");
        assertEquals(Verdict.COUNTER_ATTACK, ReconRules.verdict(false, inside.struck(), party, inside.plan(), 1));
        assertFalse(ReconRules.candidate(false, false, true), "a lair never strikes: no counter-attack on a lair");
        // at the interval every usable report is a candidate again: the weakest goes first
        Base atInterval = all.stream().filter(b -> ReconRules.candidate(true, b.settlement(), b.struck()))
                .min(Comparator.comparingDouble(Base::plan)).orElseThrow();
        assertEquals(lair, atInterval);
        assertEquals(Verdict.ATTACK, ReconRules.verdict(true, atInterval.struck(), party, atInterval.plan(), 1));
        // inside the interval with no strike: no candidate and no waiting line
        assertFalse(ReconRules.considersTargets(false, false));
        assertTrue(ReconRules.considersTargets(true, false));
        assertTrue(all.stream().noneMatch(b -> ReconRules.candidate(false, b.settlement(), false)));
    }

    // ---- 7. The vantage, the climb and the hold ----

    @Test
    void theVantageBandIsSixtyOneToSeventyTwoFromTheCentre() {
        assertTrue(ReconRules.inBand(0, -61, false));
        assertFalse(ReconRules.inBand(0, -60, false), "60: inside the guards' sight");
        assertFalse(ReconRules.inBand(-53, 0, false), "the designer's first standoff");
        assertTrue(ReconRules.inBand(72, 0, false));
        assertFalse(ReconRules.inBand(73, 0, false), "past the focused look's reach");
        assertTrue(ReconRules.inBand(8, -71, false), "8 off the axis: 71.4 from the centre");
        assertFalse(ReconRules.inBand(61, 40, false), "Chebyshev 61 but 72.9 away");
        assertFalse(ReconRules.inBand(55, 55, false), "a corner inside the standoff");
        assertEquals(61, ReconRules.STANDOFF);
        assertEquals(72, ReconRules.SIGHT);
        List<Column> north = ReconRules.columns(Side.NORTH, false);
        assertEquals(new Column(0, -61, 0, 61), north.get(0), "the axis, the nearest the standoff");
        for (int i = 0; i < 12; i++) {
            assertEquals(new Column(0, -61 - i, 0, 61 + i), north.get(i), "the axis first, nearest the standoff first");
        }
        assertEquals(new Column(-1, -61, -1, 61), north.get(12), "then one block off it");
        for (Column c : north) {
            assertTrue(Math.max(Math.abs(c.dx()), Math.abs(c.dz())) >= 61 && c.dx() * c.dx() + c.dz() * c.dz() <= 72 * 72, c.toString());
            assertTrue(Math.abs(c.lateral()) <= 8 && c.dz() < 0, c.toString());
        }
        assertTrue(north.stream().anyMatch(c -> c.lateral() == 8 && c.depth() == 71));
        assertTrue(north.stream().noneMatch(c -> c.lateral() == 8 && c.depth() == 72));
        assertEquals(new Column(61, 0, 0, 61), ReconRules.columns(Side.EAST, false).get(0));
        assertEquals(new Column(-61, 3, 3, 61), ReconRules.columns(Side.WEST, false).stream()
                .filter(c -> c.lateral() == 3).findFirst().orElseThrow());
    }

    @Test
    void aLairIsWatchedFromTheGroundOnAGapAxisAtFortySixToSixtyFour() {
        assertFalse(ReconRules.inBand(45, 0, true));
        assertTrue(ReconRules.inBand(46, 0, true));
        assertTrue(ReconRules.inBand(0, 64, true));
        assertFalse(ReconRules.inBand(0, 65, true));
        List<Column> south = ReconRules.columns(Side.SOUTH, true);
        assertEquals(19, south.size(), "46 to 64 on the axis");
        assertTrue(south.stream().allMatch(c -> c.lateral() == 0 && c.dx() == 0 && c.dz() >= 46 && c.dz() <= 64));
        assertEquals(new Column(0, 46, 0, 46), south.get(0));
    }

    @Test
    void theSidesGoGateFacingHomeThenTheOtherGateThenFacingHomeThenTheLast() {
        int gates = Side.WEST.bit() | Side.EAST.bit();
        List<Side> fromSouthWest = ReconRules.sideOrder(-200, 150, gates);
        assertEquals(List.of(Side.WEST, Side.EAST, Side.SOUTH, Side.NORTH), fromSouthWest);
        assertEquals(List.of(Side.EAST, Side.WEST, Side.NORTH, Side.SOUTH), ReconRules.sideOrder(100, -300, gates));
        assertEquals(List.of(Side.NORTH, Side.EAST, Side.WEST, Side.SOUTH), ReconRules.sideOrder(50, -300, Side.ALL),
                "a lair: gaps on every side, the one facing home first");
        // two sides a trip; the next starts at the first side not yet tried, so two trips cover all four
        assertEquals(List.of(Side.WEST, Side.EAST), ReconRules.tripSides(fromSouthWest, 0));
        int afterOne = ReconRules.mask(ReconRules.tripSides(fromSouthWest, 0));
        assertEquals(List.of(Side.SOUTH, Side.NORTH), ReconRules.tripSides(fromSouthWest, afterOne));
        assertEquals(List.of(Side.EAST, Side.SOUTH), ReconRules.tripSides(fromSouthWest, Side.WEST.bit()),
                "a blind side is not tried first twice");
        assertEquals(List.of(Side.NORTH, Side.WEST), ReconRules.tripSides(fromSouthWest, Side.ALL & ~Side.NORTH.bit()),
                "the last untried side, then the first tried");
        assertEquals(List.of(Side.WEST, Side.EAST), ReconRules.tripSides(fromSouthWest, Side.ALL),
                "every side tried and blind: start again");
        assertEquals(2, ReconRules.SIDES);
        // a good recon clears the sides tried; a blind one adds its sides
        ReconFile blind = Intel.mergeRecon(ReconFile.NONE, recon(0, 2, false, 1200, 100), afterOne);
        assertEquals(afterOne, blind.sidesTried());
        assertEquals(0, Intel.mergeRecon(blind, recon(2, 10, true, 1200, 200), 0).sidesTried());
    }

    @Test
    void itClimbsTwoPastTheFirstGoodLookNeverPastTwenty() {
        assertTrue(ReconRules.looksAt(0), "once from the ground");
        assertFalse(ReconRules.looksAt(1));
        assertFalse(ReconRules.looksAt(3));
        assertTrue(ReconRules.looksAt(4));
        assertEquals(Climb.UP, ReconRules.climb(11, -1));
        assertEquals(Climb.UP, ReconRules.climb(12, 12), "good at 12: on to 14");
        assertEquals(Climb.UP, ReconRules.climb(13, 12));
        assertEquals(Climb.WATCH, ReconRules.climb(14, 12));
        assertEquals(14, ReconRules.stopHeight(12));
        assertEquals(20, ReconRules.stopHeight(19), "never past the cap");
        assertEquals(Climb.WATCH, ReconRules.climb(20, 19));
        assertEquals(Climb.UP, ReconRules.climb(19, -1));
        assertEquals(Climb.NEXT_SIDE, ReconRules.climb(20, -1), "no good look at the cap: the next side");
        assertEquals(2, ReconRules.stopHeight(0), "good from high ground: two blocks up all the same");
        assertEquals(22, ReconRules.BLOCKS);
        assertTrue(ReconRules.BLOCKS >= ReconRules.PILLAR_MAX);
    }

    @Test
    void anEnemyNearKeepsTheScoutUpAHitBringsItDownAndTheHoldIsBounded() {
        long start = 10_000, watch = 1200, end = start + watch;
        assertTrue(ReconRules.threat(16, true));
        assertFalse(ReconRules.threat(16.5, true));
        assertFalse(ReconRules.threat(4, false), "out of its sight");
        assertEquals(Perch.WATCH, ReconRules.perch(start + 600, start, watch, true, false, false), "a patrol below: it keeps watching");
        assertEquals(Perch.RETREAT, ReconRules.perch(start + 600, start, watch, true, true, false), "hit");
        assertEquals(Perch.RETREAT, ReconRules.perch(start + 600, start, watch, false, false, true), "a pillar block broken");
        assertEquals(Perch.DOWN, ReconRules.perch(end, start, watch, false, false, false), "the watch is over");
        assertEquals(Perch.HOLD, ReconRules.perch(end, start, watch, true, false, false), "an enemy near: it holds");
        assertEquals(Perch.HOLD, ReconRules.perch(end + ReconRules.HOLD_MAX_TICKS - 1, start, watch, true, false, false));
        assertEquals(Perch.DOWN, ReconRules.perch(end + ReconRules.HOLD_MAX_TICKS, start, watch, true, false, false),
                "then comes down regardless");
        assertEquals(Perch.DOWN, ReconRules.perch(end + 100_000, start, watch, true, false, false));
        // the trip's plan allows for the hold: two climbs and descents (about 660) and the walk round (about 2000)
        assertTrue(ReconRules.VANTAGE_TICKS >= ReconRules.HOLD_MAX_TICKS + 660 + 2000);
        // a watch cut short counts only past 600 ticks and when good
        assertFalse(recon(3, 12, true, 599, 100).good());
        assertTrue(recon(3, 12, true, 600, 100).good());
    }

    // ---- 6. The watch accumulator (task R2) ----

    /** The mask of the grid points (x, z). */
    private static int points(int[]... xz) {
        int m = 0;
        for (int[] p : xz) {
            m |= 1 << (p[0] * ReconWatch.GRID + p[1]);
        }
        return m;
    }

    private static ReconWatch.Sample sample(int mask, Map<java.util.UUID, Double> soldiers, Map<String, Double> heroes, long at) {
        return new ReconWatch.Sample(mask, soldiers, heroes, at);
    }

    @Test
    void aWatchCountsEachFaceOnceOverThirtySamples() {
        java.util.UUID walker = java.util.UUID.randomUUID(), guard = java.util.UUID.randomUUID();
        ReconWatch w = new ReconWatch();
        long start = 20_000;
        for (int i = 0; i < 30; i++) {
            // the walker in view every other sample, the guard at its post in every one
            Map<java.util.UUID, Double> seen = i % 2 == 0 ? Map.of(walker, 1.0, guard, 2.0) : Map.of(guard, 2.0);
            w.add(sample(points(new int[]{0, 0}, new int[]{0, 1}), seen, Map.of(), start + 40L * i));
        }
        Recon r = w.recon("pillar 12", "Edrin Cole");
        assertEquals(2, r.soldiers(), "each soldier counted once, not once per sample");
        assertEquals(Set.of(walker, guard), r.faces(), "the faces counted, by UUID");
        assertEquals(1.5, r.power(), 1e-9, "the mean visible power of the distinct soldiers");
        assertEquals(29 * 40, r.watchTicks(), "from the first sample to the last");
        assertEquals(start + 29 * 40, r.at(), "the watch's last sample");
        assertEquals("pillar 12", r.vantage());
        assertEquals("Edrin Cole", r.by());
        // a soldier's power is the one of its newest sighting (it picked up a sword)
        w.add(sample(0, Map.of(walker, 2.0), Map.of(), start + 30 * 40));
        assertEquals(2, w.soldiers().size());
        assertEquals(2.0, w.soldiers().get(walker), 1e-9);
        assertEquals(2.0, w.power(), 1e-9);
        // a null sample (the base out of sight) changes nothing
        long before = w.watchTicks();
        w.add(null);
        assertEquals(before, w.watchTicks());
        // nothing seen yet: no faces, power 0, no watch
        ReconWatch empty = new ReconWatch();
        assertTrue(empty.empty());
        assertEquals(0, empty.power(), 1e-9);
        assertEquals(0, empty.watchTicks());
        assertEquals(0, empty.recon("ground", "x").soldiers());
        assertFalse(empty.recon("ground", "x").good());
    }

    @Test
    void aWatchSavedAndLoadedMidwayStillCountsEachFaceOnce() {
        java.util.UUID walker = java.util.UUID.randomUUID();
        ReconWatch w = new ReconWatch();
        long start = 30_000;
        for (int i = 0; i < 15; i++) {
            w.add(sample(points(new int[]{2, 2}), Map.of(walker, 1.0), Map.of("hinae", 6.8), start + 40L * i));
        }
        // saved with the scout's job and loaded after a restart: what the save keeps, nothing more
        ReconWatch loaded = ReconWatch.restore(w.mask(), new java.util.HashMap<>(w.soldiers()), new java.util.HashMap<>(w.heroes()),
                w.first(), w.last());
        for (int i = 15; i < 30; i++) {
            loaded.add(sample(points(new int[]{2, 3}), Map.of(walker, 1.0), Map.of(), start + 40L * i));
        }
        Recon r = loaded.recon("pillar 13", "Edrin Cole");
        assertEquals(1, r.soldiers(), "the soldier seen before and after the load is one face");
        assertEquals(29 * 40, r.watchTicks(), "the watch runs on from its first sample before the save");
        assertEquals(1, ReconWatch.restore(w.mask(), null, null, w.first(), w.last()).points(), "the points seen before the save");
        assertEquals(Map.of("hinae", 6.8), r.heroes(), "a hero seen before the save is kept, at its power");
        assertEquals(6.8, r.heroPower(), 1e-9);
        assertEquals(2, r.points());
    }

    @Test
    void theYardPointsSeenAreAUnionAndTheMiddleSeenOnceIsEnough() {
        ReconWatch w = new ReconWatch();
        long start = 40_000;
        // three corner points from one height, four other points from the next; the middle in view in one sample only
        w.add(sample(points(new int[]{0, 0}, new int[]{0, 4}, new int[]{4, 0}), Map.of(), Map.of(), start));
        w.add(sample(points(new int[]{1, 1}, new int[]{1, 2}, new int[]{1, 3}, new int[]{0, 0}), Map.of(), Map.of(), start + 40));
        w.add(sample(points(new int[]{2, 2}), Map.of(), Map.of(), start + 80));
        for (int i = 3; i < 16; i++) {
            w.add(sample(points(new int[]{3, 3}), Map.of(), Map.of(), start + 40L * i));
        }
        assertEquals(8, w.points(), "the union: a point seen twice counts once");
        assertTrue(w.middle(), "the middle seen once is enough");
        Recon r = w.recon("pillar 14", "Edrin Cole");
        assertEquals(8, r.points());
        assertEquals(0.32, r.coverage(), 1e-9);
        assertTrue(r.middle());
        assertEquals(600, r.watchTicks());
        assertTrue(r.good(), "8 of 25 with the middle, watched 600 ticks: good, and an empty yard is a real 'about 0'");
        assertEquals(0, r.soldiers());
        // the middle is the grid's centre point, and a sample knows it
        assertEquals(12, ReconWatch.MIDDLE);
        assertTrue(sample(1 << ReconWatch.MIDDLE, null, null, 0).middle());
        assertFalse(sample(ReconWatch.ALL_POINTS & ~(1 << ReconWatch.MIDDLE), null, null, 0).middle());
        assertEquals(24, sample(ReconWatch.ALL_POINTS & ~(1 << ReconWatch.MIDDLE), null, null, 0).points());
        assertEquals(25, sample(-1, null, null, 0).points(), "a mask never holds more than the 25 points");
        // without the middle, a wide look is blind
        ReconWatch corners = new ReconWatch();
        corners.add(sample(ReconWatch.ALL_POINTS & ~(1 << ReconWatch.MIDDLE), Map.of(), Map.of(), start));
        corners.add(sample(0, Map.of(), Map.of(), start + 1200));
        assertFalse(corners.recon("pillar 12", "Edrin Cole").good(), "24 of 25 without the middle: blind");
    }
}
