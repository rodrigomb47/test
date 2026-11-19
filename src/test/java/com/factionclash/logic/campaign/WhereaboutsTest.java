package com.factionclash.logic.campaign;

import com.factionclash.logic.campaign.Whereabouts.Event;
import com.factionclash.logic.campaign.Whereabouts.Knowing;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.logic.campaign.Whereabouts.Step;
import com.factionclash.logic.campaign.Whereabouts.Waits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhereaboutsTest {
    private static final Waits WAITS = new Waits(2400, 12000);

    @Test
    void onlyHereAwayAndMissingAreCounted() {
        assertTrue(State.HERE.counted());
        assertTrue(State.AWAY.counted());
        assertTrue(State.MISSING.counted(), "a missing member is not replaced yet");
        assertFalse(State.PRESUMED_DEAD.counted(), "a presumed-dead member is replaced");
        assertFalse(State.KNOWN_DEAD.counted());
        assertEquals(State.MISSING, State.byName("MISSING", State.HERE));
        assertEquals(State.HERE, State.byName("nonsense", State.HERE));
        assertEquals(State.KNOWN_DEAD, State.byName("", State.KNOWN_DEAD));
    }

    @Test
    void anAwayMemberGoesMissingOnlyPastItsExpectedReturnAndTheGrace() {
        long expected = 30000;
        assertEquals(new Step(State.AWAY, Event.NONE), Whereabouts.waits(State.AWAY, expected, expected, 0, WAITS));
        assertEquals(new Step(State.AWAY, Event.NONE), Whereabouts.waits(State.AWAY, expected + 2400, expected, 0, WAITS),
                "the grace is still running at its last tick");
        assertEquals(new Step(State.MISSING, Event.MISSING), Whereabouts.waits(State.AWAY, expected + 2401, expected, 0, WAITS));
        // a member at home is never missing, whatever its old expectation
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.waits(State.HERE, 10_000_000, expected, 0, WAITS));
    }

    @Test
    void aMissingMemberIsPresumedDeadAfterTheWait() {
        long since = 50000;
        assertEquals(new Step(State.MISSING, Event.NONE), Whereabouts.waits(State.MISSING, since + 11999, 0, since, WAITS));
        assertEquals(new Step(State.PRESUMED_DEAD, Event.PRESUMED_DEAD), Whereabouts.waits(State.MISSING, since + 12000, 0, since, WAITS));
        // the waits never touch the dead
        assertEquals(new Step(State.PRESUMED_DEAD, Event.NONE), Whereabouts.waits(State.PRESUMED_DEAD, since + 1_000_000, 0, since, WAITS));
        assertEquals(new Step(State.KNOWN_DEAD, Event.NONE), Whereabouts.waits(State.KNOWN_DEAD, since + 1_000_000, 0, since, WAITS));
    }

    @Test
    void comingHomeEndsEveryAbsence() {
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.seenHome(State.HERE));
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.seenHome(State.AWAY), "back on time says nothing");
        assertEquals(new Step(State.HERE, Event.BACK), Whereabouts.seenHome(State.MISSING));
        assertEquals(new Step(State.HERE, Event.REJOINED), Whereabouts.seenHome(State.PRESUMED_DEAD));
        assertEquals(new Step(State.HERE, Event.REJOINED), Whereabouts.seenHome(State.KNOWN_DEAD), "alive at home is alive");
    }

    @Test
    void onlyAMemberSeenAtHomeLeaves() {
        assertEquals(new Step(State.AWAY, Event.LEFT), Whereabouts.seenAway(State.HERE));
        for (State s : new State[] {State.AWAY, State.MISSING, State.PRESUMED_DEAD, State.KNOWN_DEAD}) {
            assertEquals(new Step(s, Event.NONE), Whereabouts.seenAway(s), "away again changes nothing for " + s);
        }
    }

    @Test
    void whoKnowsADeath() {
        assertEquals(Knowing.AT_ONCE, Whereabouts.atDeath(true, true, 0, 0), "inside a home area");
        assertEquals(Knowing.AT_ONCE, Whereabouts.atDeath(true, false, 1, 0), "a witness at home");
        assertEquals(Knowing.AT_ONCE, Whereabouts.atDeath(true, false, 1, 3), "a witness at home tells before those away");
        assertEquals(Knowing.CARRIED, Whereabouts.atDeath(true, false, 0, 1), "only witnesses away");
        assertEquals(Knowing.UNSEEN, Whereabouts.atDeath(true, false, 0, 0), "nobody saw it");
        assertEquals(Knowing.AT_ONCE, Whereabouts.atDeath(false, false, 0, 0), "farDeathsUnknown off: the old behaviour");
    }

    @Test
    void learningOfADeath() {
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED), Whereabouts.learnsDeath(State.HERE, false));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED), Whereabouts.learnsDeath(State.AWAY, false));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED_REPORTED), Whereabouts.learnsDeath(State.AWAY, true));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED_REPORTED), Whereabouts.learnsDeath(State.MISSING, true));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED_CONFIRMED), Whereabouts.learnsDeath(State.PRESUMED_DEAD, true));
        assertEquals(new Step(State.KNOWN_DEAD, Event.NONE), Whereabouts.learnsDeath(State.KNOWN_DEAD, true),
                "a second carrier's news tells nothing");
    }

    @Test
    void tripPlans() {
        assertEquals(0, Whereabouts.walkTicks(0));
        assertEquals(0, Whereabouts.walkTicks(-5));
        assertEquals(3200, Whereabouts.walkTicks(320), "320 blocks at 0.1 a tick");
        assertEquals(11, Whereabouts.walkTicks(1.01));
        // a scout's plan: what is left of its expedition's bound from the departure
        assertEquals(24000, Whereabouts.scoutPlan(5000, 0, 24000), "no departure: the whole bound");
        assertEquals(20000, Whereabouts.scoutPlan(9000, 5000, 24000));
        assertEquals(0, Whereabouts.scoutPlan(40000, 5000, 24000), "already past its bound: back now");
        // a raider: what is left of its raid's plan (knowledge review of 2026-11-19)
        assertEquals(17000, Whereabouts.raidPlan(11000, 28000));
        assertEquals(0, Whereabouts.raidPlan(200000, 28000), "past the raid's plan: back now");
        // a raid launched before the plan was kept: the raid's hard limit and the walk back, as before
        assertEquals(10000 + 72000 + 1500, Whereabouts.raidPlannedBy(10000, 0, 150));
        assertEquals(10000 + 18000, Whereabouts.raidPlannedBy(10000, 18000, 150));
        assertEquals(24000 - 100 + 3200, Whereabouts.planFrom(100, Whereabouts.TRADER_ROAD_TICKS, 320));
    }

    @Test
    void aScoutKilledFarAwayAndNeverSeenIsMissingThenPresumedDead() {
        // the whole timeline of the owner's case, with the default waits
        long departed = 1000, overdue = 24000;
        long left = 1500;
        Step s = Whereabouts.seenAway(State.HERE);
        assertEquals(State.AWAY, s.state());
        long expected = left + Whereabouts.scoutPlan(left, departed, overdue);
        assertEquals(departed + overdue, expected);
        // it dies at 6000, nobody saw it: the state does not change
        assertEquals(Knowing.UNSEEN, Whereabouts.atDeath(true, false, 0, 0));
        State st = s.state();
        long t = expected + 2401;
        Step m = Whereabouts.waits(st, t, expected, 0, WAITS);
        assertEquals(Event.MISSING, m.event());
        Step p = Whereabouts.waits(m.state(), t + 12000, expected, t, WAITS);
        assertEquals(Event.PRESUMED_DEAD, p.event());
        assertFalse(p.state().counted(), "now replaced");
        // and a scout coming home late on the same timeline rejoins
        assertEquals(Event.REJOINED, Whereabouts.seenHome(p.state()).event());
    }

    @Test
    void oldRecords() {
        assertEquals(State.HERE, Whereabouts.legacy(true, false));
        assertEquals(State.AWAY, Whereabouts.legacy(true, true));
        assertEquals(State.KNOWN_DEAD, Whereabouts.legacy(false, true));
        assertEquals(State.KNOWN_DEAD, Whereabouts.legacy(false, false));
    }

    @Test
    void days() {
        assertEquals(1, Whereabouts.day(0));
        assertEquals(1, Whereabouts.day(23999));
        assertEquals(2, Whereabouts.day(24000));
        assertEquals(1, Whereabouts.day(-50));
    }

    @Test
    void theFactionsLines() {
        String who = "Ash Rook (Scout)";
        assertNull(Whereabouts.line(Event.NONE, who, null, 0, 0, 0, 0, 0, false));
        assertNull(Whereabouts.line(Event.LEFT, who, null, 0, 0, 0, 0, 0, false));
        assertEquals("Ash Rook (Scout) was killed", Whereabouts.line(Event.KILLED, who, null, 0, 0, 0, 0, 0, false));
        assertEquals("Mara Vey brings news: Ash Rook (Scout) was killed near 120, -40 on day 2",
                Whereabouts.line(Event.KILLED_REPORTED, who, "Mara Vey", 120, -40, 30000, 0, 0, false));
        assertEquals("Mara Vey brings news: Ash Rook (Scout), presumed dead, was killed near 120, -40 on day 2",
                Whereabouts.line(Event.KILLED_CONFIRMED, who, "Mara Vey", 120, -40, 30000, 0, 0, false));
        assertEquals("Ash Rook (Scout) is missing: expected back by day 3",
                Whereabouts.line(Event.MISSING, who, null, 0, 0, 0, 50000, 0, false));
        assertEquals("Ash Rook (Scout) is presumed dead (missing since day 3)",
                Whereabouts.line(Event.PRESUMED_DEAD, who, null, 0, 0, 0, 0, 52000, false));
        assertEquals("Ash Rook (Scout) is back", Whereabouts.line(Event.BACK, who, null, 0, 0, 0, 0, 0, false));
        assertEquals("Ash Rook (Scout), presumed dead, has come back and rejoins",
                Whereabouts.line(Event.REJOINED, who, null, 0, 0, 0, 0, 0, false));
        // a hero falls rather than is killed
        assertEquals("Mara Vey brings news: Natsuho Komezaki has fallen near 5, 6 on day 1",
                Whereabouts.line(Event.KILLED_REPORTED, "Natsuho Komezaki", "Mara Vey", 5, 6, 100, 0, 0, true));
        assertEquals("Mara Vey brings news: The raid on Grove Covenant has ended",
                Whereabouts.carried("Mara Vey", "The raid on Grove Covenant has ended"));
        assertEquals("Natsuho Komezaki fell where none of the faction saw it and returns to Ember Hold",
                Whereabouts.unseenFall("Natsuho Komezaki", "Ember Hold"));
    }

    // ------------------------------------------------------------------ review of 2026-11-19

    /**
     * {@code farDeathsUnknown} turned off: a death not yet known is known now (it stayed counted for ever); a member
     * alive, or already known or presumed dead, is left as it is.
     */
    @Test
    void underTheOldRuleEveryDeathStillCountedIsKnown() {
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED), Whereabouts.knownUnderOldRule(true, State.AWAY));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED), Whereabouts.knownUnderOldRule(true, State.MISSING));
        assertEquals(new Step(State.KNOWN_DEAD, Event.KILLED), Whereabouts.knownUnderOldRule(true, State.HERE));
        assertEquals(new Step(State.AWAY, Event.NONE), Whereabouts.knownUnderOldRule(false, State.AWAY), "alive away stays away");
        assertEquals(new Step(State.MISSING, Event.NONE), Whereabouts.knownUnderOldRule(false, State.MISSING));
        assertEquals(new Step(State.PRESUMED_DEAD, Event.NONE), Whereabouts.knownUnderOldRule(true, State.PRESUMED_DEAD),
                "a death presumed is not told again");
        assertEquals(new Step(State.KNOWN_DEAD, Event.NONE), Whereabouts.knownUnderOldRule(true, State.KNOWN_DEAD));
    }

    /**
     * The council at home believes its raid is out until it hears of its end, until it no longer counts anyone of the
     * party (each raider known or presumed dead), or until the raid's own bound has passed (then it knows the raid is
     * over, whatever became of the party).
     */
    @Test
    void theCouncilBelievesItsRaidIsOutUntilItHearsOrTheBoundPasses() {
        long start = 100_000, planned = start + 18_000, grace = WAITS.grace();
        assertTrue(Whereabouts.raidStillOutAtHome(true, true, start + 1, planned, grace), "nobody brought the end home yet");
        assertTrue(Whereabouts.raidStillOutAtHome(true, true, planned + grace, planned, grace), "the plan and the grace's last tick");
        assertFalse(Whereabouts.raidStillOutAtHome(true, true, planned + grace + 1, planned, grace), "past them its raiders are overdue");
        assertFalse(Whereabouts.raidStillOutAtHome(false, true, start + 1, planned, grace), "the end was heard");
        assertFalse(Whereabouts.raidStillOutAtHome(true, false, start + 1, planned, grace), "every raider known dead: the party is no more");
    }

    /**
     * Known leak 5 (knowledge review of 2026-11-19): the council expects its raid back by its own plan (the march to the
     * rally, the muster's longest wait, the assault, the march home), not by the raid's 3-day limit. A party wiped out
     * unseen at a target 300 blocks away is taken as lost after about three quarters of a day, as its raiders go missing.
     */
    @Test
    void aRaidWipedOutUnseenIsTakenAsLostByItsPlanNotItsLimit() {
        long plan = Whereabouts.raidPlanTicks(272, 300);
        assertEquals(2720 + Whereabouts.RAID_MUSTER_TICKS + Whereabouts.RAID_ASSAULT_TICKS + 3000, plan);
        long start = 50_000, planned = Whereabouts.raidPlannedBy(start, plan, 300), grace = WAITS.grace();
        assertTrue(planned + grace - start < Whereabouts.TICKS_PER_DAY, "a 300-block raid is overdue within the day: " + (planned - start));
        assertFalse(Whereabouts.raidStillOutAtHome(true, true, planned + grace + 1, planned, grace));
        assertTrue(planned + grace < start + Whereabouts.RAID_LIMIT_TICKS, "the plan ends long before the raid's hard limit");
        // its raiders, seen leaving at the launch, are missing at the same moment the council gives the raid up
        long expectedBy = start + Whereabouts.raidPlan(start, planned);
        assertEquals(new Step(State.MISSING, Event.MISSING), Whereabouts.waits(State.AWAY, planned + grace + 1, expectedBy, 0, WAITS));
    }

    /**
     * Known leak 6 (knowledge review of 2026-11-19): a member near a home area's edge changes side only past its band, so
     * a miner at a branch face just past the edge is not seen leaving and coming back dozens of times a day; a missing
     * member seen anywhere inside is back at once. A tiny test base has no band.
     */
    @Test
    void aMemberNearTheEdgeChangesSideOnlyPastItsBand() {
        assertEquals(8, Whereabouts.edgeBand(64));
        assertEquals(0, Whereabouts.edgeBand(6));
        assertEquals(8, Whereabouts.edgeBand(400), "at most 8 blocks");
        int band = 8;
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.sighted(State.HERE, 5, band), "just past the edge: still here");
        assertEquals(new Step(State.AWAY, Event.LEFT), Whereabouts.sighted(State.HERE, 9, band), "past the band: it left");
        assertEquals(new Step(State.AWAY, Event.NONE), Whereabouts.sighted(State.AWAY, -3, band), "just inside: still away");
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.sighted(State.AWAY, -8, band), "the band inside: home");
        assertEquals(new Step(State.HERE, Event.BACK), Whereabouts.sighted(State.MISSING, -1, band), "a missing member seen inside is back");
        assertEquals(new Step(State.HERE, Event.REJOINED), Whereabouts.sighted(State.PRESUMED_DEAD, 0, band));
        assertEquals(new Step(State.MISSING, Event.NONE), Whereabouts.sighted(State.MISSING, 30, band), "away and missing: no change");
        // no band: the edge itself
        assertEquals(new Step(State.AWAY, Event.LEFT), Whereabouts.sighted(State.HERE, 1, 0));
        assertEquals(new Step(State.HERE, Event.NONE), Whereabouts.sighted(State.AWAY, 0, 0));
        assertEquals(new Step(State.AWAY, Event.LEFT), Whereabouts.sighted(State.HERE, Integer.MAX_VALUE, 0), "no home area at all");
    }

    /**
     * Known leak 6: a miner's shift is fixed at the storehouse (until the coming nightfall, at least half a day), and its
     * trip's plan is what is left of it plus the walk home, the same expected return however often it crosses the edge.
     */
    @Test
    void aMinersShiftIsPlannedAtTheStorehouse() {
        long left = 240_000; // dawn of day 11 (day time 0)
        long end = Whereabouts.shiftEnd(left, 0);
        assertEquals(left + 13000, end, "from dawn: until nightfall");
        assertEquals(left + Whereabouts.DEFAULT_TRIP_TICKS + 6000, Whereabouts.shiftEnd(left + 6000, 6000), "from noon: half a day");
        // crossing the edge out at two different times gives the same expected return (plus the walk from each point)
        long first = left + 3000, second = left + 9000;
        assertEquals(end + 700, first + Whereabouts.shiftPlan(first, end, 70));
        assertEquals(end + 700, second + Whereabouts.shiftPlan(second, end, 70));
        assertEquals(700, Whereabouts.shiftPlan(end + 500, end, 70), "past its shift: the walk home");
    }

    /** A hero risen unseen far away tells its own fall when it comes home: where, and on which day. */
    @Test
    void aHeroTellsItsOwnFall() {
        assertEquals("Ira fell near 120, -40 on day 3 where none of the faction saw it, and has risen again",
                Whereabouts.ownFall("Ira", 120, -40, 2 * Whereabouts.TICKS_PER_DAY + 5));
    }

    /**
     * A day's remote work (live check of 2026-11-19): a worker setting out at dawn is expected back at nightfall plus the
     * walk home, which outlasts half a day; one setting out late in the day keeps half a day; one setting out at night
     * is expected by the next nightfall.
     */
    @Test
    void aDaysRemoteWorkIsExpectedBackAtNightfall() {
        assertEquals(13000 + 800, Whereabouts.dayShiftPlan(0, 80), "dawn: the whole working day and the walk home");
        assertEquals(1000 + 13000 + 700, Whereabouts.dayShiftPlan(23000, 70), "before dawn: until the coming nightfall");
        assertEquals(1000 + 13000 + 700, Whereabouts.dayShiftPlan(24000 * 5 + 23000, 70), "the world's day time counts in days");
        assertEquals(Whereabouts.DEFAULT_TRIP_TICKS, Whereabouts.dayShiftPlan(6000, 80), "from noon, never under half a day");
        assertEquals(Whereabouts.DEFAULT_TRIP_TICKS, Whereabouts.dayShiftPlan(12900, 80), "just before dusk");
        assertEquals(7000 + 13000 + 500, Whereabouts.dayShiftPlan(17000, 50), "at night: by the next nightfall");
        // the live case: out from dawn to dusk (13716 ticks), now well inside the plan and its grace
        assertTrue(13716 < Whereabouts.dayShiftPlan(23400, 70) + WAITS.grace());
        assertTrue(13716 > Whereabouts.DEFAULT_TRIP_TICKS, "the default plan alone left it 700 ticks of grace");
    }

    /**
     * Review of the knowledge review (2026-11-19): the party on the spot keeps the council's plan. It turns home in time
     * to be back by it (the launch plus the plan, less the walk home), never later than the raid's hard limit; a raid
     * launched before the plan was kept turns home at the limit.
     */
    @Test
    void aRaidPartyTurnsHomeInTimeToBeBackByItsPlan() {
        long start = 50_000;
        long plan = Whereabouts.raidPlanTicks(272, 300);
        long turn = Whereabouts.raidTurnHomeAt(start, plan, 300);
        assertEquals(start + plan - Whereabouts.walkTicks(300), turn);
        assertEquals(Whereabouts.raidPlannedBy(start, plan, 300), turn + Whereabouts.walkTicks(300), "home by the plan");
        assertTrue(turn - start >= Whereabouts.RAID_MUSTER_TICKS + Whereabouts.RAID_ASSAULT_TICKS, "time for the muster and the assault");
        assertEquals(start + Whereabouts.RAID_LIMIT_TICKS, Whereabouts.raidTurnHomeAt(start, 0, 300), "an old raid keeps its limit");
        assertEquals(start + Whereabouts.RAID_LIMIT_TICKS, Whereabouts.raidTurnHomeAt(start, 500_000, 300), "never past the limit");
        assertEquals(start, Whereabouts.raidTurnHomeAt(start, 100, 300), "a plan shorter than the walk: at once");
    }
}
