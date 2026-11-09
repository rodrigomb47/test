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
    void shaftStepOfNamesTheStepOfEveryOpenCell() {
        int steps = 130;
        for (int f = -1; f <= 4; f++) {
            for (int s = -1; s <= 2; s++) {
                for (int y = -steps - 4; y <= 2; y++) {
                    int k = SpiralMine.shaftStepOf(f, s, y);
                    assertEquals(SpiralMine.inShaft(f, s, y, steps), k > 0 && k <= steps, "cell " + f + "," + s + "," + y + " step " + k);
                    if (k > 0) {
                        assertTrue(SpiralMine.inShaft(f, s, y, k) && !SpiralMine.inShaft(f, s, y, k - 1), "step " + k + " opens " + f + "," + s + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void spiralIndexMatchesThePlan() {
        for (int from = 0; from <= 20; from++) {
            List<SpiralMine.Cell> plan = SpiralMine.spiralPlan(from, from + 30);
            for (int i = 0; i < plan.size(); i++) {
                SpiralMine.Cell c = plan.get(i);
                if (c.torch() || c.dy() >= 0) {
                    continue; // (the first steps' top cells lie at or above the floor of the mouth: not in the shaft)
                }
                int k = SpiralMine.shaftStepOf(c.f(), c.s(), c.dy());
                assertTrue(k > from, "plan cell " + c + " belongs to step " + k);
                assertEquals(i, SpiralMine.spiralIndex(from, k, c.dy()), "index of " + c + " in the plan from step " + from);
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
        org.junit.jupiter.api.Assertions.assertSame(SpiralMine.cachedPlan(1, 2), SpiralMine.cachedPlan(1, 2));
    }

    @Test
    void landingHelpersFaceTheTunnels() {
        for (int n = 1; n <= 300; n++) {
            int below = SpiralMine.landingAtOrBelow(n), above = SpiralMine.landingAtOrAbove(n);
            assertTrue(Math.floorMod(below - 1, 4) == 1 || Math.floorMod(below - 1, 4) == 2, "landing at or below " + n + ": " + below);
            assertTrue(Math.floorMod(above - 1, 4) == 1 || Math.floorMod(above - 1, 4) == 2, "landing at or above " + n + ": " + above);
            assertTrue(below >= n && above <= n, "below is as deep or deeper, above as shallow or shallower: " + n);
            for (int k = n; k < below; k++) {
                assertFalse(SpiralMine.isLanding(k), "a nearer landing below " + n + " at " + k);
            }
            for (int k = above + 1; k <= n; k++) {
                assertFalse(SpiralMine.isLanding(k), "a nearer landing above " + n + " at " + k);
            }
            if (n >= 2) {
                assertEquals(2, SpiralMine.cell(below)[0], "a landing's cell faces the tunnels");
            }
        }
    }
}
