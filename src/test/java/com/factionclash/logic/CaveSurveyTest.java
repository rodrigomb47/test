package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveSurveyTest {
    /** A synthetic cave: the open (air) cells; everything else is rock. */
    private static final class Cave implements CaveSurvey.Cells {
        final Set<Long> air = new HashSet<>();

        void carve(int x, int y, int z) {
            air.add(CaveSurvey.pack(x, y, z));
        }

        /** A corridor two blocks high with its floor at y - 1. */
        void corridor(int x0, int x1, int y, int z) {
            for (int x = x0; x <= x1; x++) {
                carve(x, y, z);
                carve(x, y + 1, z);
            }
        }

        @Override
        public boolean open(int x, int y, int z) {
            return air.contains(CaveSurvey.pack(x, y, z));
        }

        @Override
        public boolean standable(int x, int y, int z) {
            return open(x, y, z) && open(x, y + 1, z) && !open(x, y - 1, z);
        }
    }

    @Test
    void packedPositionsRoundTrip() {
        for (int[] p : new int[][]{{0, 0, 0}, {-30000, -64, 29999}, {123, 319, -456}, {-1, -1, -1}}) {
            long l = CaveSurvey.pack(p[0], p[1], p[2]);
            assertEquals(p[0], CaveSurvey.x(l));
            assertEquals(p[1], CaveSurvey.y(l));
            assertEquals(p[2], CaveSurvey.z(l));
        }
    }

    @Test
    void walkClimbsOneBlockStepsButNotTwo() {
        Cave c = new Cave();
        c.corridor(0, 9, 10, 0);
        // a one-block step up at x=10..19 (head room above the step: y=12 open over x=9)
        c.carve(9, 12, 0);
        c.corridor(10, 19, 11, 0);
        c.carve(10, 13, 0);
        // a two-block ledge at x=20..25: not walkable from x=19
        c.corridor(20, 25, 13, 0);
        Map<Long, Integer> w = CaveSurvey.walk(CaveSurvey.pack(0, 10, 0), c, 100, 10000);
        assertEquals(9, w.get(CaveSurvey.pack(9, 10, 0)));
        assertEquals(10, w.get(CaveSurvey.pack(10, 11, 0)), "one-block step up must be walkable");
        assertEquals(19, w.get(CaveSurvey.pack(19, 11, 0)));
        assertFalse(w.containsKey(CaveSurvey.pack(20, 13, 0)), "a two-block ledge cannot be climbed");
        // and the same step is walkable downward from the far side
        Map<Long, Integer> back = CaveSurvey.walk(CaveSurvey.pack(19, 11, 0), c, 100, 10000);
        assertTrue(back.containsKey(CaveSurvey.pack(0, 10, 0)), "routes must be walkable back");
    }

    @Test
    void walkRespectsStepAndNodeLimits() {
        Cave c = new Cave();
        c.corridor(0, 99, 5, 0);
        Map<Long, Integer> w = CaveSurvey.walk(CaveSurvey.pack(0, 5, 0), c, 24, 10000);
        assertEquals(25, w.size()); // steps 0..24
        Map<Long, Integer> small = CaveSurvey.walk(CaveSurvey.pack(0, 5, 0), c, 1000, 10);
        assertTrue(small.size() <= 10);
    }

    @Test
    void nextWaypointIsTheClosestUnexploredCellPreferringDeeper() {
        Cave c = new Cave();
        c.corridor(-30, 30, 20, 0);
        // a side passage going down one block at x=-8 (deeper at the same walking distance as x=+8)
        long start = CaveSurvey.pack(0, 20, 0);
        Map<Long, Integer> w = CaveSurvey.walk(start, c, CaveSurvey.MAX_STEPS, 10000);
        Long n = CaveSurvey.next(w, List.of(start), CaveSurvey.MIN_STEPS, CaveSurvey.SPACING);
        assertNotNull(n);
        assertTrue(Math.abs(CaveSurvey.x(n)) >= CaveSurvey.SPACING, "keeps the spacing from visited waypoints");
        assertEquals(Math.ceil(CaveSurvey.SPACING), Math.abs(CaveSurvey.x(n)), "closest such cell");
        // everything explored -> nothing new
        List<Long> all = List.of(start, CaveSurvey.pack(-7, 20, 0), CaveSurvey.pack(7, 20, 0), CaveSurvey.pack(-14, 20, 0),
                CaveSurvey.pack(14, 20, 0), CaveSurvey.pack(-21, 20, 0), CaveSurvey.pack(21, 20, 0), CaveSurvey.pack(-28, 20, 0),
                CaveSurvey.pack(28, 20, 0));
        Map<Long, Integer> wide = CaveSurvey.walk(start, c, 100, 10000);
        assertNull(CaveSurvey.next(wide, all, 1, CaveSurvey.SPACING));
    }

    @Test
    void deeperCellWinsTies() {
        Cave c = new Cave();
        c.corridor(0, 8, 30, 0);       // east: level
        c.corridor(-3, 0, 30, 0);
        c.carve(-4, 31, 0);
        c.corridor(-8, -4, 29, 0);     // west: one step down at x=-4
        long start = CaveSurvey.pack(0, 30, 0);
        Map<Long, Integer> w = CaveSurvey.walk(start, c, 24, 10000);
        Long n = CaveSurvey.next(w, List.of(start), 8, 1.0);
        assertNotNull(n);
        assertEquals(29, CaveSurvey.y(n), "on equal walking distance the deeper cell is explored first");
    }

    @Test
    void routeFollowsParentsFromTheEntrance() {
        int[] parent = {-1, 0, 1, 1, 3};
        assertEquals(List.of(0, 1, 3, 4), CaveSurvey.route(parent, 4));
        assertEquals(List.of(0), CaveSurvey.route(parent, 0));
        long[] wps = {CaveSurvey.pack(0, 0, 0), CaveSurvey.pack(10, 0, 0), CaveSurvey.pack(20, -5, 0)};
        assertEquals(2, CaveSurvey.nearestWaypoint(wps, CaveSurvey.pack(22, -6, 1)));
    }

    @Test
    void treePathGoesUpToTheCommonAncestorAndDown() {
        //      0
        //      |
        //      1
        //     / \
        //    2   3
        //        |
        //        4
        int[] parent = {-1, 0, 1, 1, 3};
        assertEquals(List.of(1, 3, 4), CaveSurvey.treePath(parent, 2, 4));
        assertEquals(List.of(3, 1, 2), CaveSurvey.treePath(parent, 4, 2));
        assertEquals(List.of(3, 1, 0), CaveSurvey.treePath(parent, 4, 0), "back to the entrance");
        assertEquals(List.of(1, 3, 4), CaveSurvey.treePath(parent, 0, 4), "from the entrance");
        assertEquals(List.of(), CaveSurvey.treePath(parent, 3, 3));
        assertEquals(List.of(3), CaveSurvey.treePath(parent, 4, 3));
    }

    @Test
    void stepsAreBuiltDownADropUntilTheFloorIsWalkable() {
        Cave c = new Cave();
        c.corridor(0, 3, 10, 0);                 // a ledge, floor at y=9
        for (int x = 4; x <= 12; x++) {          // a pit east of it, floor at y=5, open up to y=11
            for (int y = 6; y <= 11; y++) {
                c.carve(x, y, 0);
            }
        }
        long start = CaveSurvey.pack(0, 10, 0);
        long pitFloor = CaveSurvey.pack(10, 6, 0);
        Map<Long, Integer> w = CaveSurvey.walk(start, c, 100, 10000);
        assertFalse(w.containsKey(pitFloor), "a four-block drop is not walkable without steps");
        int built = 0;
        while (!w.containsKey(pitFloor) && built < 10) {
            CaveSurvey.Step st = CaveSurvey.dropStep(w, c, List.of(start), 1.0, 8);
            assertNotNull(st, "a step should be possible");
            c.air.remove(st.block()); // place the block
            assertTrue(c.standable(CaveSurvey.x(st.onto()), CaveSurvey.y(st.onto()), CaveSurvey.z(st.onto())));
            built++;
            w = CaveSurvey.walk(start, c, 100, 10000);
        }
        assertTrue(w.containsKey(pitFloor), "steps lead to the pit floor");
        assertEquals(3, built, "one block per step: 3 steps for a 4-block drop");
        Map<Long, Integer> back = CaveSurvey.walk(pitFloor, c, 100, 10000);
        assertTrue(back.containsKey(start), "the steps can be walked back up");
    }

    @Test
    void noStepsIntoABottomlessDrop() {
        Cave c = new Cave();
        c.corridor(0, 3, 30, 0);
        for (int y = 0; y <= 31; y++) {
            c.carve(4, y, 0);                    // a shaft 30 deep
        }
        Map<Long, Integer> w = CaveSurvey.walk(CaveSurvey.pack(0, 30, 0), c, 100, 10000);
        assertNull(CaveSurvey.dropStep(w, c, List.of(), 1.0, 8));
    }

    @Test
    void frontierIsSparseAndUnexplored() {
        Cave c = new Cave();
        c.corridor(-40, 40, 0, 0);
        long start = CaveSurvey.pack(0, 0, 0);
        Map<Long, Integer> w = CaveSurvey.walk(start, c, 40, 10000);
        List<Long> f = CaveSurvey.frontier(w, List.of(start), CaveSurvey.MIN_STEPS, CaveSurvey.SPACING);
        assertFalse(f.isEmpty());
        for (int i = 0; i < f.size(); i++) {
            assertTrue(CaveSurvey.dist2(f.get(i), start) >= CaveSurvey.SPACING * CaveSurvey.SPACING);
            for (int j = i + 1; j < f.size(); j++) {
                assertTrue(CaveSurvey.dist2(f.get(i), f.get(j)) >= CaveSurvey.SPACING * CaveSurvey.SPACING);
            }
        }
    }
}
