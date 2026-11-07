package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpiralMineTest {
    @Test
    void everyColumnKeepsAFloorBetweenSteps() {
        // In each 2x2 column, the open space of consecutive steps 4 apart never merges: a solid floor remains.
        int steps = 200;
        for (int[] c : SpiralMine.CELLS) {
            for (int y = -steps; y < 0; y++) {
                boolean open = SpiralMine.inShaft(c[0], c[1], y, steps);
                boolean openBelow = SpiralMine.inShaft(c[0], c[1], y - 1, steps);
                boolean openBelow2 = SpiralMine.inShaft(c[0], c[1], y - 2, steps);
                boolean openBelow3 = SpiralMine.inShaft(c[0], c[1], y - 3, steps);
                assertFalse(open && openBelow && openBelow2 && openBelow3, "column " + c[0] + "," + c[1] + " open 4 deep at " + y);
            }
        }
    }

    @Test
    void consecutiveStepsAreWalkable() {
        for (int k = 2; k < 120; k++) {
            int[] a = SpiralMine.cell(k - 1), b = SpiralMine.cell(k);
            assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]), "steps must be orthogonal neighbours");
            int ya = SpiralMine.standY(k - 1), yb = SpiralMine.standY(k);
            assertEquals(ya - 1, yb);
            // moving from A to B at A's height needs B open at A's feet and head height (y >= 0 is the open hut)
            assertTrue(open(b, ya, k) && open(b, ya + 1, k), "step " + k + " not walkable from step " + (k - 1));
            // standing needs a solid floor below
            assertFalse(SpiralMine.inShaft(b[0], b[1], yb - 1, k + 200));
        }
    }

    private static boolean open(int[] c, int y, int steps) {
        return y >= 0 || SpiralMine.inShaft(c[0], c[1], y, steps);
    }

    @Test
    void levelsReachOreBandsAndFaceTheTunnels() {
        int surface = 66;
        int prevY = surface;
        for (int level = 1; level <= 4; level++) {
            int n = SpiralMine.landingSteps(surface, level);
            assertTrue(n > 0);
            assertEquals(2, SpiralMine.cell(n)[0], "landing must be on the tunnel side of the shaft");
            int y = SpiralMine.levelY(surface, level);
            assertTrue(y <= prevY - 6, "each level is at least 6 deeper");
            prevY = y;
        }
        assertTrue(SpiralMine.levelY(surface, 2) <= 32);
        assertTrue(SpiralMine.levelY(surface, 3) <= 0);
        assertTrue(SpiralMine.levelY(surface, 4) <= -48 && SpiralMine.levelY(surface, 4) >= SpiralMine.MIN_Y);
        assertEquals(-1, SpiralMine.landingSteps(surface, 60), "no levels below the mining floor, nor endless infill");
    }

    @Test
    void infillLevelsFitBetweenExistingOnesOnceTheBottomIsReached() {
        int surface = 70;
        java.util.List<Integer> all = new java.util.ArrayList<>();
        int level = 1;
        for (int n = SpiralMine.landingSteps(surface, level); n > 0; n = SpiralMine.landingSteps(surface, ++level)) {
            assertEquals(2, SpiralMine.cell(n)[0], "every landing faces the tunnels");
            assertTrue(surface - n >= SpiralMine.MIN_Y);
            all.add(n);
        }
        int regular = 0;
        while (SpiralMine.isRegular(surface, regular + 1)) {
            regular++;
        }
        assertTrue(all.size() > regular, "infill levels exist after the bottom");
        int deepest = all.subList(0, regular).stream().mapToInt(Integer::intValue).max().orElseThrow();
        for (int i = regular; i < all.size(); i++) {
            assertTrue(all.get(i) < deepest, "infill levels lie above the bottom, inside the dug shaft");
        }
        java.util.List<Integer> sorted = new java.util.ArrayList<>(all);
        sorted.sort(null);
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i) - sorted.get(i - 1) >= 6, "levels at least 6 apart: " + sorted);
        }
    }

    @Test
    void branchMineIsTwoHighAndCoversBranchesEveryFourBlocks() {
        List<SpiralMine.Cell> plan = SpiralMine.tunnelPlan(0);
        Set<String> seen = new HashSet<>();
        int branches = 0;
        for (SpiralMine.Cell c : plan) {
            if (!c.torch()) {
                assertTrue(seen.add(c.f() + "," + c.s() + "," + c.dy()), "duplicate cell");
                if (c.s() == -SpiralMine.BRANCH_LENGTH && c.dy() == 0) {
                    branches++;
                }
            }
        }
        for (SpiralMine.Cell c : plan) {
            if (!c.torch() && c.dy() == 0) {
                assertTrue(seen.contains(c.f() + "," + c.s() + ",1"), "tunnel must be 2 high");
            }
        }
        assertEquals((SpiralMine.MAIN_LENGTH - 1) / SpiralMine.BRANCH_SPACING, branches);
        // the main tunnel starts right next to the landing cell (f = 2)
        assertEquals(SpiralMine.MAIN_START, plan.get(0).f());
    }

    @Test
    void lengtheningKeepsTheDugPartAndAddsMoreTunnel() {
        for (int sd = 0; sd <= 1; sd++) {
            final int side = sd;
            java.util.List<SpiralMine.Cell> first = SpiralMine.tunnelPlan(side, 0), longer = SpiralMine.tunnelPlan(side, 1);
            // progress through the shorter plan stays valid: the longer plan starts with exactly the same cells
            assertEquals(first, longer.subList(0, first.size()));
            assertEquals(SpiralMine.planSize(1), longer.size());
            assertEquals(SpiralMine.tunnelPlan(side), first);
            int maxF = longer.stream().mapToInt(SpiralMine.Cell::f).max().orElse(0);
            assertEquals(SpiralMine.MAIN_START + 2 * SpiralMine.MAIN_LENGTH - 1, maxF);
            // the added stretch has its own branches, like the first one
            long added = longer.subList(first.size(), longer.size()).stream().filter(c -> c.s() != side).count();
            org.junit.jupiter.api.Assertions.assertTrue(added > 0, "the lengthened tunnel has no branches");
        }
        org.junit.jupiter.api.Assertions.assertTrue(SpiralMine.regularLevels(70) >= 4);
        org.junit.jupiter.api.Assertions.assertSame(SpiralMine.cachedPlan(1, 2), SpiralMine.cachedPlan(1, 2));
    }
}
