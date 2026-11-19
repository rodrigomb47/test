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
        // a raider: the raid's bound from its launch, and the walk back
        assertEquals(72000 - 1000 + 1500, Whereabouts.raidPlan(11000, 10000, 150));
        assertEquals(1500, Whereabouts.raidPlan(200000, 10000, 150), "past the raid's bound: only the walk back");
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
        long start = 100_000;
        assertTrue(Whereabouts.raidStillOutAtHome(true, true, start + 1, start), "nobody brought the end home yet");
        assertTrue(Whereabouts.raidStillOutAtHome(true, true, start + Whereabouts.RAID_LIMIT_TICKS, start), "the bound's last tick");
        assertFalse(Whereabouts.raidStillOutAtHome(true, true, start + Whereabouts.RAID_LIMIT_TICKS + 1, start), "past the bound it is over");
        assertFalse(Whereabouts.raidStillOutAtHome(false, true, start + 1, start), "the end was heard");
        assertFalse(Whereabouts.raidStillOutAtHome(true, false, start + 1, start), "every raider known dead: the party is no more");
    }

    /** A hero risen unseen far away tells its own fall when it comes home: where, and on which day. */
    @Test
    void aHeroTellsItsOwnFall() {
        assertEquals("Ira fell near 120, -40 on day 3 where none of the faction saw it, and has risen again",
                Whereabouts.ownFall("Ira", 120, -40, 2 * Whereabouts.TICKS_PER_DAY + 5));
    }
}
