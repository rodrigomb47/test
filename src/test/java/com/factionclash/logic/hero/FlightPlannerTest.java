package com.factionclash.logic.hero;

import com.factionclash.logic.hero.FlightPlanner.Box;
import com.factionclash.logic.hero.FlightPlanner.End;
import com.factionclash.logic.hero.FlightPlanner.Heights;
import com.factionclash.logic.hero.FlightPlanner.Landing;
import com.factionclash.logic.hero.FlightPlanner.Leg;
import com.factionclash.logic.hero.FlightPlanner.Mode;
import com.factionclash.logic.hero.FlightPlanner.ModeInput;
import com.factionclash.logic.hero.FlightPlanner.Point;
import com.factionclash.logic.hero.FlightPlanner.Probe;
import com.factionclash.logic.hero.FlightPlanner.Station;
import com.factionclash.logic.hero.FlightPlanner.Surface;
import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.TraitTier;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Situation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 2.2, 2.3, 3.5, 3.7 and 3.9: the flight planner over a supplied height function. */
class FlightPlannerTest {
    private static final Heights FLAT = (x, z) -> 64;

    private static Profile flyer(int tier, int level) {
        return MobilityRules.profile(new TraitTier(MobilityTrait.FLYER, tier), null, level);
    }

    // ------------------------------------------------------------------ sampling (3.5, 3.9)

    @Test
    void stationsEveryFourBlocksOneWaypointAtATime() {
        List<Station> st = FlightPlanner.stations(FLAT, 0.5, 0.5, 100.5, 0.5);
        assertEquals(7, st.size(), "0, 4, ... 24: one 24-block waypoint");
        for (int i = 0; i < st.size(); i++) {
            assertEquals(0.5 + 4 * i, st.get(i).x(), 1e-9);
            assertTrue(st.get(i).loaded());
            assertEquals(64, st.get(i).terrain(), 1e-9);
        }
        assertTrue(st.size() * 3 <= FlightPlanner.MAX_SAMPLES, "at most 48 samples an extension");
        // a short hop ends on its end point, not past it
        List<Station> hop = FlightPlanner.stations(FLAT, 0, 0, 0, 10);
        assertEquals(4, hop.size(), "0, 4, 8 and the end at 10");
        assertEquals(10, hop.get(3).z(), 1e-9);
        // a diagonal keeps its 4-block spacing
        List<Station> diag = FlightPlanner.stations(FLAT, 0, 0, 30, 30);
        assertEquals(Math.hypot(diag.get(1).x(), diag.get(1).z()), 4, 1e-9);
        assertEquals(1, FlightPlanner.stations(FLAT, 3, 3, 3, 3).size(), "no length: one station");
    }

    @Test
    void theThreeWideSamplingCatchesASpireBesideTheLine() {
        // a 1-block spire 2 blocks to the side of the line, at the station 8 blocks on; a line-only sampler misses it
        Heights spire = (x, z) -> x == 8 && z == 2 ? 90 : 64;
        List<Station> st = FlightPlanner.stations(spire, 8.5 - 8, 0.5, 100.5, 0.5);
        assertEquals(90, st.get(2).terrain(), 1e-9, "the side sample sees it");
        Leg leg = FlightPlanner.leg(spire, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        for (Point p : leg.points().subList(1, leg.points().size())) {
            if (Math.abs(p.x() - 4.5) < 1e-9 || Math.abs(p.x() - 8.5) < 1e-9) {
                assertTrue(p.y() >= 96 - 1e-9, "6 over the spire on the segments that pass it: " + p);
            }
        }
        // the other side too
        Heights other = (x, z) -> x == 8 && z == -2 ? 90 : 64;
        assertEquals(90, FlightPlanner.stations(other, 0.5, 0.5, 100.5, 0.5).get(2).terrain(), 1e-9);
        // a spire between two stations on the line is the forward probe's: it climbs while the ceiling allows
        Heights between = (x, z) -> x == 6 && z == 0 ? 90 : 64;
        assertTrue(FlightPlanner.stations(between, 0.5, 0.5, 100.5, 0.5).stream().allMatch(s -> s.terrain() == 64));
        assertEquals(Probe.CLIMB, FlightPlanner.probe(true, 70, 90, 64 + 24 + 30));
        assertEquals(Probe.TURN, FlightPlanner.probe(true, 70, 90, 88), "the climb would pass the ceiling");
        assertEquals(Probe.TURN, FlightPlanner.probe(true, 88, 80, 88), "already at the ceiling");
        assertEquals(Probe.CLEAR, FlightPlanner.probe(false, 70, 90, 88));
        assertEquals(4, FlightPlanner.PROBE_TICKS);
        assertEquals(4, FlightPlanner.PROBE_AHEAD, 1e-9);
    }

    // ------------------------------------------------------------------ the cruise layer (2.2, 2.3)

    @Test
    void theCruiseLayerIsSixToTenAboveTheTerrainWithoutBobbing() {
        Leg low = FlightPlanner.leg(FLAT, new Point(0.5, 64, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertEquals(End.WAYPOINT, low.end());
        assertEquals(7, low.points().size(), "the start and six stations");
        for (Point p : low.points().subList(1, 7)) {
            assertEquals(70, p.y(), 1e-9, "from the ground it climbs to the bottom of the layer");
        }
        assertEquals(24.5, low.last().x(), 1e-9);
        Leg mid = FlightPlanner.leg(FLAT, new Point(0.5, 73, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertTrue(mid.points().stream().allMatch(p -> p.y() == 73), "inside the layer it keeps its altitude");
        Leg high = FlightPlanner.leg(FLAT, new Point(0.5, 95, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertEquals(74, high.last().y(), 1e-9, "above the layer it comes down to its top");
        // a hill: the layer follows the higher of each segment's two stations
        Heights hill = (x, z) -> x >= 10 ? 80 : 64;
        Leg up = FlightPlanner.leg(hill, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertEquals(70, up.points().get(1).y(), 1e-9, "at 4 over the low ground");
        assertEquals(86, up.points().get(2).y(), 1e-9, "at 8 it is already 6 over the hill the next segment crosses");
        assertEquals(86, up.points().get(3).y(), 1e-9);
        // and a cliff down: it never sinks below 6 over the segment's high end
        Heights cliff = (x, z) -> x >= 10 ? 40 : 64;
        Leg down = FlightPlanner.leg(cliff, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertEquals(70, down.points().get(3).y(), 1e-9, "the segment from 8 to 12 still passes the cliff top");
        assertEquals(50, down.points().get(4).y(), 1e-9, "then down to the top of the low layer");
        for (Leg l : List.of(low, mid, high, up, down)) {
            assertTrue(l.samples() <= FlightPlanner.MAX_SAMPLES);
        }
    }

    @Test
    void theCeilingsOfASituationBoundTheLayer() {
        // the travel ceiling (mobility.travelAltitude, 8-48) is under the layer's top when it is set low
        Leg travel8 = FlightPlanner.leg(FLAT, new Point(0.5, 95, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 8);
        assertEquals(72, travel8.last().y(), 1e-9, "8 above the ground");
        Leg escape = FlightPlanner.leg(FLAT, new Point(0.5, 95, 0.5), 100.5, 0.5, Situation.ESCAPE, List.of(), 24);
        assertEquals(74, escape.last().y(), 1e-9, "an escape: the travel ceiling, the layer's top");
        // a raid's approach: 10 above the ground, a wall top counting as ground
        assertEquals(74, FlightPlanner.ceiling(Situation.RAID, 64, List.of(), 24), 1e-9);
        assertEquals(79, FlightPlanner.ceiling(Situation.RAID, 69, List.of(), 24), 1e-9, "over a 5-high wall");
        // a fight: 6 above the ground, or above the highest engaged enemy standing on a block, whoever it is
        Leg fight = FlightPlanner.leg(FLAT, new Point(0.5, 95, 0.5), 100.5, 0.5, Situation.FIGHT, List.of(), 24);
        assertEquals(70, fight.last().y(), 1e-9);
        List<Double> onPillar = List.of(64.0, 84.0);
        assertEquals(90, FlightPlanner.ceiling(Situation.FIGHT, 64, onPillar, 24), 1e-9,
                "a player or an NPC archer on a 20-block pillar: the same feet, the same ceiling");
        assertEquals(Probe.CLIMB, FlightPlanner.probe(true, 70, 85, FlightPlanner.ceiling(Situation.FIGHT, 64,
                onPillar, 24)), "it may rise to the enemy on the pillar");
        assertEquals(Probe.TURN, FlightPlanner.probe(true, 70, 85, FlightPlanner.ceiling(Situation.FIGHT, 64,
                List.of(64.0), 24)));
        // on a raid or a scouting round: the raid's ceiling only until an enemy engages it, then the fight's
        assertEquals(Situation.RAID, FlightPlanner.raidSituation(false));
        assertEquals(Situation.FIGHT, FlightPlanner.raidSituation(true));
        assertEquals(74, FlightPlanner.ceiling(FlightPlanner.raidSituation(false), 64, List.of(), 24), 1e-9);
        assertEquals(70, FlightPlanner.ceiling(FlightPlanner.raidSituation(true), 64, List.of(64.0), 24), 1e-9);
    }

    // ------------------------------------------------------------------ loaded terrain, landing, stamina (3.5)

    @Test
    void anUnloadedSampleEndsThePlanAndTheHeroWaitsThenLands() {
        Heights edge = (x, z) -> x > 10 ? Double.NaN : 64;
        Leg leg = FlightPlanner.leg(edge, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL, List.of(), 24);
        assertEquals(End.UNLOADED, leg.end());
        assertEquals(8.5, leg.last().x(), 1e-9, "the last loaded station");
        List<Station> st = FlightPlanner.stations(edge, 0.5, 0.5, 100.5, 0.5);
        assertFalse(st.get(st.size() - 1).loaded(), "sampling stops at the first unloaded station");
        assertEquals(4, st.size());
        // one unloaded side sample is enough: it never flies into a chunk that is not loaded
        Heights side = (x, z) -> x == 4 && z == 2 ? Double.NaN : 64;
        assertEquals(End.UNLOADED, FlightPlanner.leg(side, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL,
                List.of(), 24).end());
        // the start itself unloaded: nothing to fly
        Leg none = FlightPlanner.leg((x, z) -> Double.NaN, new Point(0.5, 70, 0.5), 100.5, 0.5, Situation.TRAVEL,
                List.of(), 24);
        assertEquals(End.UNLOADED, none.end());
        assertEquals(1, none.points().size());
        // at the edge it hovers at most 3 s, then lands
        assertFalse(FlightPlanner.landAtEdge(59));
        assertTrue(FlightPlanner.landAtEdge(60));
        assertEquals(2.0, MobilityRules.drain(MobilityRules.Activity.HOVER, false, Situation.TRAVEL), 1e-9);
        // its traveller ticket is 5x5 while it flies (the next waypoint always loaded), 3x3 on the ground
        assertEquals(2, FlightPlanner.ticketRadius(true));
        assertEquals(1, FlightPlanner.ticketRadius(false));
    }

    @Test
    void theStaminaReserveIsTheBestLandingSeenPlusAQuarter() {
        List<Landing> seen = List.of(new Landing(100), new Landing(40));
        // 40 blocks at 6 b/s is 6.67 s, at a cruise drain of 0.5: 3.33, plus 25%: 4.17
        assertTrue(FlightPlanner.mustLand(4.1, seen, 6, 0.5));
        assertFalse(FlightPlanner.mustLand(4.2, seen, 6, 0.5));
        assertTrue(FlightPlanner.mustLand(30, List.of(), 6, 0.5), "no landing seen: it lands where it is");
        // loaded and in a fight the drain grows, so the reserve does too
        double fighting = MobilityRules.drain(MobilityRules.Activity.POWERED, true, Situation.FIGHT);
        assertEquals(2.25, fighting, 1e-9);
        assertTrue(FlightPlanner.mustLand(18, seen, 6, fighting), "40 / 6 x 2.25 x 1.25 = 18.75");
    }

    @Test
    void legalLandingsAndTheTakeOffColumn() {
        assertTrue(FlightPlanner.legalLanding(Surface.GROUND, 2, false, false));
        assertFalse(FlightPlanner.legalLanding(Surface.GROUND, 1, false, false), "2 blocks of headroom");
        for (Surface s : List.of(Surface.FARMLAND, Surface.LAVA, Surface.FIRE, Surface.POWDER_SNOW, Surface.WATER)) {
            assertFalse(FlightPlanner.legalLanding(s, 9, false, true), s.name());
        }
        assertFalse(FlightPlanner.legalLanding(Surface.GROUND, 9, true, false), "a hostile yard only on purpose");
        assertTrue(FlightPlanner.legalLanding(Surface.GROUND, 9, true, true), "a raid or a scouting round");
        // the headroom over the whole footprint, not only over its head: an overhang beside it stops the take-off
        java.util.function.IntBinaryOperator open = (x, z) -> x == 1 ? 2 : 30;
        assertEquals(30, FlightPlanner.takeOffHeadroom(0.5, 0.5, 0.3, open), "one column");
        assertEquals(2, FlightPlanner.takeOffHeadroom(0.9, 0.5, 0.3, open), "the box overlaps the overhang");
        assertEquals(2, FlightPlanner.takeOffHeadroom(0.9, 0.9, 0.3, open), "2x2 at most");
        assertTrue(FlightPlanner.takeOffHeadroom(0.9, 0.5, 0.3, open) < MobilityRules.TAKE_OFF_HEADROOM);
    }

    // ------------------------------------------------------------------ stuck in the air (3.5)

    @Test
    void aHeadBumpOrNoProgressEndsTheFlight() {
        FlightPlanner.Watch w = new FlightPlanner.Watch();
        double left = 40;
        for (int i = 0; i < 9; i++) {
            assertFalse(w.tick(true, left -= 0.3), "bumping its head under leaves, tick " + i);
        }
        assertFalse(w.tick(false, left -= 0.3), "a clear tick resets the bumps");
        for (int i = 0; i < 9; i++) {
            assertFalse(w.tick(true, left -= 0.3));
        }
        assertTrue(w.tick(true, left -= 0.3), "10 ticks in a row: the flight ends");
        // no progress: under 1 block in 2 s
        FlightPlanner.Watch p = new FlightPlanner.Watch();
        assertFalse(p.tick(false, 30));
        for (int i = 1; i < 40; i++) {
            assertFalse(p.tick(false, 30 - i * 0.02), "tick " + i);
        }
        assertTrue(p.tick(false, 30 - 40 * 0.02), "0.8 blocks in 2 s");
        p.reset();
        assertFalse(p.stuck(), "a new waypoint starts the count again");
        FlightPlanner.Watch moving = new FlightPlanner.Watch();
        for (int i = 0; i < 400; i++) {
            assertFalse(moving.tick(false, 100 - i * 0.2), "a hero that flies on is never stuck");
        }
        // the leg is then a dead link for 10 minutes
        assertEquals(12_000, FlightPlanner.DEAD_LEG_TICKS);
    }

    // ------------------------------------------------------------------ hostile bases (3.7)

    @Test
    void aHostileBaseIsCrossedOnARaidAndSkirtedOnATrip() {
        List<Box> bases = List.of(new Box(100, 100, 148, 148));
        List<double[]> line = List.of(new double[]{0, 124}, new double[]{250, 124});
        // the berth is the walls box plus 16 blocks: 80 blocks of the line, paid x3 on a trip
        assertEquals(250 + 2 * 80, FlightPlanner.cost(line, bases, Situation.TRAVEL), 1e-6);
        assertEquals(250, FlightPlanner.cost(line, bases, Situation.RAID), 1e-6, "a raid crosses it");
        assertEquals(250, FlightPlanner.cost(line, bases, Situation.FIGHT), 1e-6);
        assertEquals(250 + 2 * 80, FlightPlanner.cost(line, bases, Situation.ESCAPE), 1e-6);
        List<double[]> trip = FlightPlanner.route(0, 124, 250, 124, bases, Situation.TRAVEL);
        assertTrue(trip.size() > 2, "it goes round");
        double tripCost = FlightPlanner.cost(trip, bases, Situation.TRAVEL);
        assertTrue(tripCost < 410, "cheaper than crossing: " + tripCost);
        double inside = 0;
        for (int i = 1; i < trip.size(); i++) {
            double[] a = trip.get(i - 1), b = trip.get(i);
            inside += FlightPlanner.inside(bases.get(0).grow(HeroLinks.BERTH_MARGIN), a[0], a[1], b[0], b[1]);
        }
        assertEquals(0, inside, 1e-6, "the way round stays out of the berth");
        assertEquals(2, FlightPlanner.route(0, 124, 250, 124, bases, Situation.RAID).size(), "on a raid: the line");
        // a cost, not a ban: a base that is the trip's end is still reached
        List<double[]> into = FlightPlanner.route(0, 124, 124, 124, bases, Situation.TRAVEL);
        double[] end = into.get(into.size() - 1);
        assertEquals(124, end[0], 1e-9);
        // far from any base the line is the route
        assertEquals(2, FlightPlanner.route(0, 0, 50, 0, bases, Situation.TRAVEL).size());
        assertEquals(0, FlightPlanner.inside(new Box(0, 0, 10, 10), 20, 20, 30, 30), 1e-9);
        assertEquals(10, FlightPlanner.inside(new Box(0, 0, 10, 10), -5, 5, 15, 5), 1e-9);
    }

    // ------------------------------------------------------------------ flying or walking (3.5, 2.1)

    @Test
    void theModeOfAWaypoint() {
        Profile ambrose = flyer(2, 1), young = flyer(1, 1);
        ModeInput longFlat = new ModeInput(60, 62, false, 60, true, true, 10, false, Situation.TRAVEL, false);
        assertEquals(Mode.FLY, FlightPlanner.mode(ambrose, longFlat), "4.4 b/s beats walking's 4.3");
        assertEquals(Mode.WALK, FlightPlanner.mode(young, longFlat), "a young ceiling-I flyer walks long flat trips");
        assertEquals(Mode.FLY, FlightPlanner.mode(flyer(1, 20), longFlat), "4.7 b/s at level 20");
        ModeInput shortFlat = new ModeInput(30, 31, false, 30, true, true, 10, false, Situation.TRAVEL, false);
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, shortFlat), "40 blocks or less on flat ground: it walks");
        ModeInput detour = new ModeInput(30, 45, false, 30, true, true, 10, false, Situation.TRAVEL, false);
        assertEquals(Mode.FLY, FlightPlanner.mode(young, detour), "the ground route is 1.5 times the line");
        ModeInput noRoute = new ModeInput(30, Double.POSITIVE_INFINITY, false, 30, true, true, 10, false,
                Situation.TRAVEL, false);
        assertEquals(Mode.FLY, FlightPlanner.mode(young, noRoute), "no ground route at all");
        ModeInput river = new ModeInput(30, 31, true, 30, true, true, 10, false, Situation.TRAVEL, false);
        assertEquals(Mode.FLY, FlightPlanner.mode(young, river), "water, a ravine, a cliff or a wall it cannot link");
        // and it walks when it cannot fly the leg
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, false, true, 10, false,
                Situation.TRAVEL, false)), "the stamina does not cover the leg");
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, true, false, 10, false,
                Situation.TRAVEL, false)), "no legal landing");
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, true, true, 3, false,
                Situation.TRAVEL, false)), "headroom under 4: a tunnel, a dense forest");
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, true, true, 10, true,
                Situation.TRAVEL, false)), "a thunderstorm out of combat");
        assertEquals(Mode.FLY, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, true, true, 10, true,
                Situation.ESCAPE, false)), "an escape flies in any weather");
        assertEquals(Mode.WALK, FlightPlanner.mode(ambrose, new ModeInput(30, 45, true, 60, true, true, 10, false,
                Situation.TRAVEL, true)), "exhausted");
        assertEquals(Mode.WALK, FlightPlanner.mode(MobilityRules.NONE, river), "a worker walks");
        Profile switchedOff = MobilityRules.profile(new TraitTier(MobilityTrait.FLYER, 2), null, 20,
                new MobilityRules.Config(true, false, MobilityRules.Curve.DEFAULT, 100));
        assertEquals(Mode.WALK, FlightPlanner.mode(switchedOff, river), "mobility.flight off: it glides, not flies");
        assertNotEquals(FlightPlanner.mode(ambrose, longFlat), FlightPlanner.mode(young, longFlat));
    }
}
