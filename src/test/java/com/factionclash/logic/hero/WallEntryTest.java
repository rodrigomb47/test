package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.DuelStyle;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.TraitTier;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.WallEntry.After;
import com.factionclash.logic.hero.WallEntry.Approach;
import com.factionclash.logic.hero.WallEntry.Entry;
import com.factionclash.logic.hero.WallEntry.Exit;
import com.factionclash.logic.hero.WallEntry.Gate;
import com.factionclash.logic.hero.WallEntry.Goal;
import com.factionclash.logic.hero.WallEntry.GoalEnd;
import com.factionclash.logic.hero.WallEntry.GoalInput;
import com.factionclash.logic.hero.WallEntry.GoalState;
import com.factionclash.logic.hero.WallEntry.Wall;
import com.factionclash.logic.hero.WallEntry.Way;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 3.7 and 5.2: the way in, the way out and the goal inside of a raiding hero. */
class WallEntryTest {
    private static final Wall WOODEN = Wall.basic(Gate.WOODEN);
    private static final Approach FRESH = Approach.FRESH;

    private static Profile p(MobilityTrait t, int tier, int level) {
        return MobilityRules.profile(new TraitTier(t, tier), null, level);
    }

    private static Profile p(MobilityTrait t, int tier, MobilityTrait s, int sTier, int level) {
        return MobilityRules.profile(new TraitTier(t, tier), new TraitTier(s, sTier), level);
    }

    private static Entry in(Profile p, Wall w) {
        return WallEntry.choose(p, w, FRESH);
    }

    // ------------------------------------------------------------------ the way in by family, ceiling and level

    @Test
    void aFlyerFliesOverTheWallRingAndOutAgain() {
        Profile ambrose = p(MobilityTrait.FLYER, 2, 1);
        assertEquals(new Entry(Way.FLY_OVER, Exit.FLIGHT), in(ambrose, WOODEN));
        assertTrue(in(ambrose, WOODEN).overTheWall());
        // 24 blocks at 5.9 b/s with the take-off and the landing: about 5 s, inside the raid's 10 s per take-off
        assertEquals(5.05, WallEntry.flightSeconds(ambrose, 24), 0.01);
        // too long a flight for the raid's air budget, a storm or its threshold not met: it walks in with the soldiers
        assertEquals(Way.GATE, WallEntry.choose(ambrose, WOODEN, new Approach(1, 80, false, true)).way());
        assertEquals(Way.GATE, WallEntry.choose(ambrose, WOODEN, new Approach(1, 24, false, false)).way(),
                "the take-off is refused (a thunderstorm on a raid's approach, its threshold, headroom)");
        // stamina for the flight, x1.5 loaded
        double need = WallEntry.flightSeconds(ambrose, 24) / 27;
        assertEquals(Way.FLY_OVER, WallEntry.choose(ambrose, WOODEN, new Approach(need + 0.001, 24, false, true))
                .way());
        assertEquals(Way.GATE, WallEntry.choose(ambrose, WOODEN, new Approach(need + 0.001, 24, true, true)).way());
        // mobility.flight off: it glides at its ceiling, and a glide from the ground does not clear a wall
        Profile grounded = MobilityRules.profile(new TraitTier(MobilityTrait.FLYER, 2), null, 20,
                new MobilityRules.Config(true, false, MobilityRules.Curve.DEFAULT, 100));
        assertEquals(Way.GATE, in(grounded, WOODEN).way());
    }

    @Test
    void aClimberScalesAFiveHighWallFromItsCeilingAndLevel() {
        assertEquals(new Entry(Way.CLIMB_OVER, Exit.CLIMB), in(p(MobilityTrait.CLIMBER, 2, 1), WOODEN),
                "a ceiling-II climber from level 1");
        assertEquals(Way.GATE, in(p(MobilityTrait.CLIMBER, 1, 12), WOODEN).way(), "a ceiling-I climber climbs 4");
        assertEquals(Way.CLIMB_OVER, in(p(MobilityTrait.CLIMBER, 1, 13), WOODEN).way(), "and 5 from level 13");
        assertEquals(Way.CLIMB_OVER, in(p(MobilityTrait.CLIMBER, 3, 1), Wall.basic(Gate.SHUT)).way(),
                "climbs any height, and back up the inside face: no gate needed");
        // a secondary climber climbs as a primary does: Tobimaru from level 1, Natsuho from level 13
        Profile tobimaru = p(MobilityTrait.ACROBAT, 3, MobilityTrait.CLIMBER, 2, 1);
        assertEquals(new Entry(Way.CLIMB_OVER, Exit.CLIMB), in(tobimaru, WOODEN));
        assertEquals(Way.GATE, in(p(MobilityTrait.ACROBAT, 3, MobilityTrait.CLIMBER, 1, 1), WOODEN).way(),
                "Natsuho reaches 2.9 with her air jumps and climbs 3: the gate");
        assertEquals(Way.CLIMB_OVER, in(p(MobilityTrait.ACROBAT, 3, MobilityTrait.CLIMBER, 1, 13), WOODEN).way());
        // down inside within its reach: a 20-deep inside face is no way in for a climber that climbs 9
        Wall moat = new Wall(5, 20, Gate.WOODEN, 0, 0, 0, 0);
        assertEquals(Way.GATE, in(p(MobilityTrait.CLIMBER, 2, 1), moat).way(), "climbs 9, falls 4.8 safely");
        assertEquals(Way.CLIMB_OVER, in(tobimaru, moat).way(), "no fall damage: it drops into the yard");
    }

    @Test
    void aJumpUpLinkNeverTopsAFiveHighWall() {
        for (MobilityTrait t : MobilityTrait.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                for (int level : new int[]{1, 20, 30}) {
                    Profile pr = p(t, tier, level);
                    assertFalse(WallEntry.leapsOn(pr, WOODEN), t + " " + tier + " at " + level);
                }
            }
        }
        // a lower or broken wall, or a player's fort: 3 high for a leaper III, 2 for Kagenori from level 13
        Wall three = new Wall(3, 3, Gate.WOODEN, 0, 0, 0, 0);
        assertEquals(new Entry(Way.LEAP_ON, Exit.JUMP_UP), in(p(MobilityTrait.LEAPER, 3, 20), three));
        Wall two = new Wall(2, 2, Gate.WOODEN, 0, 0, 0, 0);
        Profile kagenori1 = p(MobilityTrait.LEAPER, 2, MobilityTrait.RUNNER, 1, 1);
        Profile kagenori13 = p(MobilityTrait.LEAPER, 2, MobilityTrait.RUNNER, 1, 13);
        assertEquals(Way.GATE, in(kagenori1, two).way());
        assertEquals(new Entry(Way.LEAP_ON, Exit.JUMP_UP), in(kagenori13, two));
        // a gap link onto the top from higher ground outside, then a drop link inside within its safe fall
        Wall ledge = new Wall(5, 5, Gate.WOODEN, 0, 0, 5, 4);
        assertEquals(new Entry(Way.LEAP_ON, Exit.GATE), in(p(MobilityTrait.LEAPER, 1, 20), ledge),
                "gap 5 over 4, safe fall 6 over 5; out by the gate");
        assertEquals(Way.GATE, in(p(MobilityTrait.LEAPER, 1, 1), ledge).way(), "gap 3 at level 1");
        assertEquals(Way.GATE, in(p(MobilityTrait.LEAPER, 3, 20), new Wall(5, 5, Gate.WOODEN, 0, 0, 3, 2)).way(),
                "the ledge is 2 under the top: a landing at most 1 higher");
        assertEquals(Way.GATE, in(p(MobilityTrait.LEAPER, 1, 20), new Wall(5, 9, Gate.WOODEN, 0, 0, 5, 4)).way(),
                "a 9-deep yard is beyond its safe fall of 6");
    }

    @Test
    void aGliderGlidesOverFromAHeightAndLeavesByTheGate() {
        Profile glider = p(MobilityTrait.GLIDER, 1, 20);
        Wall tree = new Wall(5, 5, Gate.WOODEN, 12, 10, 0, 0);
        assertEquals(new Entry(Way.GLIDE_OVER, Exit.GATE), in(glider, tree), "12 - 10 / 2.33 = 7.7 over a 5 wall");
        assertEquals(Way.GATE, in(glider, new Wall(5, 5, Gate.WOODEN, 12, 20, 0, 0)).way(), "too far to carry");
        assertEquals(Way.GATE, in(glider, WOODEN).way(), "nothing to launch from");
        assertEquals(Way.GATE, in(glider, new Wall(5, 5, Gate.SHUT, 12, 10, 0, 0)).way(),
                "a glide only goes down: no way over without a gate out");
        assertEquals(new Entry(Way.GLIDE_OVER, Exit.GATE), in(glider, new Wall(5, 5, Gate.BUTTON_OR_LEVER, 12, 10, 0,
                0)), "a button beside an iron door is a way out");
        assertEquals(Way.GLIDE_OVER, in(glider, new Wall(5, 5, Gate.BREAKABLE, 12, 10, 0, 0)).way(),
                "a gateway it may break open at player speed");
        // its own jump apex (ceiling II-III) clears only a low wall
        Profile apex = p(MobilityTrait.GLIDER, 3, MobilityTrait.LEAPER, 2, 20);
        assertTrue(WallEntry.glidesOver(apex, new Wall(2, 2, Gate.WOODEN, 0, 0, 0, 0)));
        assertFalse(WallEntry.glidesOver(apex, WOODEN));
        assertFalse(WallEntry.glidesOver(p(MobilityTrait.GLIDER, 1, 20), new Wall(1, 1, Gate.WOODEN, 0, 0, 0, 0)),
                "a ceiling-I glider has no apex launch");
    }

    @Test
    void theGroundFamiliesTakeTheGateAndAChargerLeadsTheBreach() {
        for (MobilityTrait t : List.of(MobilityTrait.RUNNER, MobilityTrait.HOVER, MobilityTrait.SWIMMER)) {
            for (Gate g : Gate.values()) {
                assertEquals(Way.GATE, in(p(t, 3, 20), Wall.basic(g)).way(), t + " " + g);
            }
        }
        Profile benika = p(MobilityTrait.CHARGER, 3, MobilityTrait.LEAPER, 1, 1);
        assertEquals(Way.GATE, in(benika, WOODEN).way(), "an unchanged gate: the soldiers need no breach");
        assertEquals(Way.LEAD_THE_BREACH, in(benika, Wall.basic(Gate.BUTTON_OR_LEVER)).way());
        assertEquals(Way.LEAD_THE_BREACH, in(benika, Wall.basic(Gate.SHUT)).way());
        assertEquals(new Entry(Way.GATE, Exit.NONE), in(p(MobilityTrait.RUNNER, 2, 1), Wall.basic(Gate.SHUT)));
        // ordinary NPCs, a format-1 hero and every hero with mobility.enabled off: the gate, whatever the wall
        for (Gate g : Gate.values()) {
            assertEquals(Way.GATE, in(MobilityRules.NONE, Wall.basic(g)).way());
            assertEquals(Way.GATE, in(MobilityRules.NONE, new Wall(1, 1, g, 30, 1, 1, 0)).way(), "even a low wall");
        }
        Profile off = MobilityRules.profile(new TraitTier(MobilityTrait.FLYER, 3), null, 20,
                new MobilityRules.Config(false, true, MobilityRules.Curve.DEFAULT, 100));
        assertEquals(Way.GATE, in(off, WOODEN).way());
    }

    // ------------------------------------------------------------------ the way out

    @Test
    void noWayOverWithoutAWayOut() {
        // the gate: anything but shut from inside
        assertTrue(Gate.WOODEN.opensFromInside() && Gate.BUTTON_OR_LEVER.opensFromInside()
                && Gate.BREAKABLE.opensFromInside());
        assertFalse(Gate.SHUT.opensFromInside());
        assertTrue(Gate.WOODEN.soldiersOpen());
        assertFalse(Gate.BUTTON_OR_LEVER.soldiersOpen(), "an iron door is worked from inside");
        // a climber's way out is the inside face
        Profile climberII = p(MobilityTrait.CLIMBER, 2, 1);
        assertEquals(Exit.CLIMB, WallEntry.wayOut(climberII, Wall.basic(Gate.SHUT), FRESH, false));
        assertEquals(Exit.NONE, WallEntry.wayOut(climberII, new Wall(5, 12, Gate.SHUT, 0, 0, 0, 0), FRESH, false));
        // a flyer's: its stamina back at its take-off threshold within 20 s on the ground after the way in
        Profile big = MobilityRules.profile(new TraitTier(MobilityTrait.FLYER, 3), null, 20,
                new MobilityRules.Config(true, true, MobilityRules.Curve.DEFAULT, 200));
        assertEquals(120, big.raw(MobilityRules.Stat.STAMINA), 1e-9);
        double flight = WallEntry.flightSeconds(big, 24);
        Approach empty = new Approach(flight / 120, 24, false, true);
        assertFalse(WallEntry.regainsAfter(big, empty, true), "it lands exhausted and 57 of the 60 it needs come back");
        assertEquals(new Entry(Way.FLY_OVER, Exit.GATE), WallEntry.choose(big, WOODEN, empty), "the gate then");
        assertEquals(Way.GATE, WallEntry.choose(big, Wall.basic(Gate.SHUT), empty).way(), "and with no gate: not over");
        assertTrue(WallEntry.regainsAfter(big, new Approach(0.5, 24, false, true), true));
        assertEquals(new Entry(Way.FLY_OVER, Exit.FLIGHT), WallEntry.choose(big, Wall.basic(Gate.SHUT),
                new Approach(0.5, 24, false, true)));
        assertFalse(WallEntry.regainsAfter(MobilityRules.NONE, FRESH, false));
        // shut in after all: links, the gate, breaking what Reach allows, then the watchdog and stuck recovery
        assertEquals(List.of("links", "gate", "break at player speed", "stall watchdog and stuck recovery"),
                WallEntry.SHUT_IN_STEPS);
        // the campaign check: nobody still inside 60 s after the raid ended
        assertFalse(WallEntry.stillInsideTooLong(true, 1199));
        assertTrue(WallEntry.stillInsideTooLong(true, 1200));
        assertFalse(WallEntry.stillInsideTooLong(false, 99_999));
        // the choice waits for the muster and the last 24 blocks
        assertFalse(WallEntry.chooseNow(false, 10));
        assertFalse(WallEntry.chooseNow(true, 24.5));
        assertTrue(WallEntry.chooseNow(true, 24));
    }

    // ------------------------------------------------------------------ the goal inside

    private static GoalInput goalIn(boolean held, Focus f, Role r, DuelStyle d, int greed, boolean known,
                                    boolean seen, boolean archers, boolean store) {
        return new GoalInput(held, f, r, d, greed, known, seen, archers, store);
    }

    @Test
    void theGoalInsideByThePartysStateThenPersonalityAndRole() {
        Focus n = Focus.NEAREST;
        assertEquals(Goal.OPEN_THE_WAY, WallEntry.goal(goalIn(true, Focus.HEROES_FIRST, Role.MARKSMAN, null, 90, true,
                true, true, true)), "its party held outside comes first");
        assertEquals(Goal.HUNT_THE_HERO, WallEntry.goal(goalIn(false, Focus.HEROES_FIRST, Role.STRIKER, null, 50, true,
                false, true, true)), "heroes first, the home hero known to be home");
        assertEquals(Goal.HEART, WallEntry.goal(goalIn(false, Focus.HEROES_FIRST, Role.STRIKER, null, 50, false, false,
                false, true)), "no hero known at home");
        assertEquals(Goal.HUNT_THE_HERO, WallEntry.goal(goalIn(false, n, Role.VANGUARD, null, 50, false, true, true,
                true)), "a defending hero seen inside");
        assertEquals(Goal.CLEAR_THE_ARCHERS, WallEntry.goal(goalIn(false, n, Role.MARKSMAN, null, 90, false, false,
                true, true)));
        assertEquals(Goal.CLEAR_THE_ARCHERS, WallEntry.goal(goalIn(false, n, Role.SUPPORT, DuelStyle.ZONER, 50, false,
                false, true, true)), "a zoner");
        assertEquals(Goal.STOREHOUSE, WallEntry.goal(goalIn(false, n, Role.MARKSMAN, null, 65, false, false, false,
                true)), "no archers to clear: a greedy marksman loots");
        assertEquals(Goal.HEART, WallEntry.goal(goalIn(false, n, Role.STRIKER, null, 64, false, false, true, true)));
        assertEquals(Goal.HEART, WallEntry.goal(goalIn(false, n, Role.STRIKER, null, 100, false, false, true, false)),
                "the storehouse is gone");
    }

    private static GoalState state(boolean raidOver, int noProgress) {
        return new GoalState(raidOver, noProgress, false, false, 0, true, false, true, false);
    }

    @Test
    void everyGoalEndsOnItsConditionTheRaidsEndOrSixtySecondsWithoutProgress() {
        for (Goal g : Goal.values()) {
            assertEquals(GoalEnd.ONGOING, WallEntry.end(g, state(false, 0)), g.name());
            assertEquals(GoalEnd.ONGOING, WallEntry.end(g, state(false, 1199)), g.name());
            assertEquals(GoalEnd.NO_PROGRESS, WallEntry.end(g, state(false, 1200)), g.name());
            assertEquals(GoalEnd.RAID_OVER, WallEntry.end(g, state(true, 0)), g.name());
        }
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.OPEN_THE_WAY, new GoalState(false, 0, true, false, 0, true,
                false, true, false)), "every living raider inside the walls box");
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.HUNT_THE_HERO, new GoalState(false, 0, false, true, 0, true,
                false, true, false)), "dead or escaped");
        assertEquals(GoalEnd.ONGOING, WallEntry.end(Goal.HUNT_THE_HERO, new GoalState(false, 0, false, false, 1199,
                true, false, true, false)));
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.HUNT_THE_HERO, new GoalState(false, 0, false, false, 1200, true,
                false, true, false)), "not seen inside within 60 s");
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.CLEAR_THE_ARCHERS, new GoalState(false, 0, false, false, 0,
                false, false, true, false)), "no archer in its range");
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.STOREHOUSE, new GoalState(false, 0, false, false, 0, true, true,
                true, false)), "one backpack of loot");
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.STOREHOUSE, new GoalState(false, 0, false, false, 0, true, false,
                false, false)), "the storehouse gone");
        assertEquals(GoalEnd.DONE, WallEntry.end(Goal.HEART, new GoalState(false, 0, false, false, 0, true, false,
                true, true)));
        // then: home with the loot, or the party at the heart; the raid's end decides for itself
        assertEquals(After.CARRY_LOOT_HOME, WallEntry.after(Goal.STOREHOUSE, GoalEnd.DONE, true));
        assertEquals(After.JOIN_AT_THE_HEART, WallEntry.after(Goal.STOREHOUSE, GoalEnd.DONE, false));
        assertEquals(After.JOIN_AT_THE_HEART, WallEntry.after(Goal.OPEN_THE_WAY, GoalEnd.NO_PROGRESS, false));
        assertEquals(After.JOIN_AT_THE_HEART, WallEntry.after(Goal.HUNT_THE_HERO, GoalEnd.DONE, false));
        assertEquals(After.RAID_ENDS, WallEntry.after(Goal.STOREHOUSE, GoalEnd.RAID_OVER, true));
    }

    @Test
    void nothingAsksWhoDefendsTheBase() {
        for (java.lang.reflect.Method m : WallEntry.class.getDeclaredMethods()) {
            for (java.lang.reflect.Parameter q : m.getParameters()) {
                String t = q.getType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(t.contains("player") || t.contains("entity"), m.getName() + " " + t);
            }
        }
        for (Class<?> c : WallEntry.class.getDeclaredClasses()) {
            if (c.isRecord()) {
                for (java.lang.reflect.RecordComponent rc : c.getRecordComponents()) {
                    assertFalse(rc.getName().toLowerCase(java.util.Locale.ROOT).contains("player"),
                            c.getSimpleName() + "." + rc.getName());
                }
            }
        }
    }
}
