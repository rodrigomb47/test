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
        assertEquals(-1, SpiralMine.landingSteps(surface, 40), "no levels below the mining floor");
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
}
