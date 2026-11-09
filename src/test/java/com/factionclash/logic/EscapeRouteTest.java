package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscapeRouteTest {
    private static EscapeRoute.Threat zombie(double x, double z) {
        return new EscapeRoute.Threat(x, z, 12);
    }

    private static EscapeRoute.Option best(List<EscapeRoute.Threat> threats, double tx, double tz, boolean hasTarget, double px, double pz) {
        List<EscapeRoute.Option> all = EscapeRoute.rank(0.5, 0.5, threats, tx, tz, hasTarget, px, pz);
        assertFalse(all.isEmpty(), "no escape point at all");
        return all.get(0);
    }

    private static double dist(EscapeRoute.Option o, double x, double z) {
        return Math.hypot(o.x() + 0.5 - x, o.z() + 0.5 - z);
    }

    @Test
    void monstersBehindLeaveTheWayAheadOpen() {
        EscapeRoute.Option o = best(List.of(zombie(-5, 0), zombie(-6, 2), zombie(-6, -2)), 200, 0, true, 0, 0);
        assertTrue(o.clear(), "the way ahead should be clear");
        assertTrue(o.dirX() > 0.9, "should keep going toward the target, went " + o.dirX() + ", " + o.dirZ());
    }

    @Test
    void monstersAheadMeanADetourNotAUTurn() {
        List<EscapeRoute.Threat> ahead = List.of(zombie(6.5, 0.5), zombie(7.5, 1.5));
        EscapeRoute.Option o = best(ahead, 200, 0.5, true, 0, 0);
        assertTrue(o.clear(), "a clear detour exists");
        assertTrue(o.dirX() > 0.3, "should still gain ground toward the target, went " + o.dirX() + ", " + o.dirZ());
        assertTrue(Math.abs(o.dirZ()) > 0.5, "should step to one side, not run through them");
        for (EscapeRoute.Threat t : ahead) {
            assertTrue(dist(o, t.x(), t.z()) >= t.keepAway() + EscapeRoute.CLEAR_MARGIN, "escape point too close to a monster");
        }
    }

    @Test
    void aPointRightAtTheKeepAwayDistanceIsNotClear() {
        // the scout stops within 1.5 blocks of its escape point: one barely at the keep-away distance would leave it
        // standing inside it, so it does not count as clear
        assertFalse(new EscapeRoute.Option(0, 0, 1, 0, 0.5, 10).clear());
        assertTrue(new EscapeRoute.Option(0, 0, 1, 0, EscapeRoute.CLEAR_MARGIN, 10).clear());
        for (EscapeRoute.Option o : EscapeRoute.rank(0.5, 0.5, List.of(zombie(5.5, 0.5)), 200, 0.5, true, 0, 0)) {
            if (o.clear()) {
                assertTrue(dist(o, 5.5, 0.5) >= 12 + EscapeRoute.CLEAR_MARGIN - 1.0E-9, "a clear point within reach of the zombie");
            }
        }
    }

    @Test
    void withoutATargetItRunsAwayFromTheGroup() {
        EscapeRoute.Option o = best(List.of(zombie(4.5, 4.5), zombie(5.5, 3.5)), 0, 0, false, 0, 0);
        assertTrue(o.clear());
        double ax = -0.707, az = -0.707;
        assertTrue(o.dirX() * ax + o.dirZ() * az > 0.7, "should run away from the group, went " + o.dirX() + ", " + o.dirZ());
    }

    @Test
    void twoGroupsOnEitherSidePushItForward() {
        // groups east and west, target to the south (+z): squeezing out between them, not into either
        List<EscapeRoute.Threat> groups = List.of(zombie(8.5, 0.5), zombie(9.5, 1.5), zombie(-7.5, 0.5), zombie(-8.5, -0.5));
        EscapeRoute.Option o = best(groups, 0.5, 300, true, 0, 0);
        assertTrue(o.clear(), "a way out between the groups exists");
        assertTrue(o.dirZ() > 0.7, "should go on toward the target between the groups, went " + o.dirX() + ", " + o.dirZ());
    }

    @Test
    void theLastEscapeIsNotReversed() {
        // a lone monster straight north, no target: east and west are equally good, but the last escape went east
        List<EscapeRoute.Threat> north = List.of(zombie(0.5, -5.5));
        EscapeRoute.Option o = best(north, 0, 0, false, 1, 0);
        assertTrue(o.dirX() >= -1.0E-9, "should not run back west, went " + o.dirX() + ", " + o.dirZ());
        EscapeRoute.Option w = best(north, 0, 0, false, -1, 0);
        assertTrue(w.dirX() <= 1.0E-9, "should not run back east, went " + w.dirX() + ", " + w.dirZ());
    }

    @Test
    void creepersAndArchersGetMoreRoom() {
        List<EscapeRoute.Threat> creeper = List.of(new EscapeRoute.Threat(3.5, 0.5, 16));
        EscapeRoute.Option o = best(creeper, 200, 0.5, true, 0, 0);
        assertTrue(o.clear());
        assertTrue(dist(o, 3.5, 0.5) >= 16, "too close to the creeper: " + dist(o, 3.5, 0.5));
    }

    @Test
    void theRunNeverBrushesPastAMonster() {
        List<EscapeRoute.Threat> ahead = List.of(zombie(6.5, 0.5));
        for (EscapeRoute.Option o : EscapeRoute.rank(0.5, 0.5, ahead, 200, 0.5, true, 0, 0)) {
            // the closest point of the straight run to the monster
            double rx = 6.0, along = Math.max(0, rx * o.dirX());
            double closest = Math.hypot(rx - o.dirX() * along, -o.dirZ() * along);
            assertTrue(closest >= EscapeRoute.PASS_GAP - 1.0E-6, "a run passes " + closest + " from the monster");
        }
    }

    @Test
    void surroundedClosely_theBestIsStillBetterThanStandingStill() {
        List<EscapeRoute.Threat> ring = List.of(new EscapeRoute.Threat(6.5, 0.5, 30), new EscapeRoute.Threat(-5.5, 0.5, 30),
                new EscapeRoute.Threat(0.5, 6.5, 30), new EscapeRoute.Threat(0.5, -5.5, 30));
        List<EscapeRoute.Option> all = EscapeRoute.rank(0.5, 0.5, ring, 0, 0, false, 0, 0);
        assertFalse(all.isEmpty());
        assertFalse(all.get(0).clear(), "nothing is clear of a 30-block keep-away");
        assertTrue(all.get(0).margin() > EscapeRoute.margin(0.5, 0.5, ring), "the least bad point should beat standing still");
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).margin() >= all.get(i).margin(), "points that are not clear go by margin");
        }
    }

    @Test
    void hemmedInOnAllSidesThereIsNoRun() {
        List<EscapeRoute.Threat> tight = List.of(zombie(3.5, 0.5), zombie(-2.5, 0.5), zombie(0.5, 3.5), zombie(0.5, -2.5));
        assertTrue(EscapeRoute.rank(0.5, 0.5, tight, 200, 0.5, true, 0, 0).isEmpty(), "every run passes closer to a monster");
    }

    @Test
    void clearPointsComeFirst() {
        List<EscapeRoute.Option> all = EscapeRoute.rank(0.5, 0.5, List.of(zombie(5.5, 0.5)), 200, 0.5, true, 0, 0);
        boolean seenUnclear = false;
        for (EscapeRoute.Option o : all) {
            if (!o.clear()) {
                seenUnclear = true;
            } else {
                assertFalse(seenUnclear, "a clear point ranked after one that is not");
            }
        }
    }

    @Test
    void marginWithoutMonstersIsLarge() {
        assertTrue(EscapeRoute.margin(0, 0, List.of()) > 1000);
        assertEquals(-2.0, EscapeRoute.margin(0, 0, List.of(zombie(10, 0))), 1.0E-9);
    }
}
