package com.factionclash.logic;

import com.factionclash.logic.TreeShape.Kind;
import com.factionclash.logic.TreeShape.Tree;
import com.factionclash.logic.TreeShape.Verdict;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TreeShapeTest {
    /** A small synthetic world: soil below y = 0, air above, with blocks set by hand. */
    private static final class Grid implements TreeShape.Cells {
        final Map<Long, Kind> cells = new HashMap<>();

        @Override
        public Kind at(int x, int y, int z) {
            Kind k = cells.get(key(x, y, z));
            return k != null ? k : y < 0 ? Kind.GROUND : Kind.AIR;
        }

        Grid set(int x, int y, int z, Kind k) {
            cells.put(key(x, y, z), k);
            return this;
        }

        Grid trunk(int x, int z, int height) {
            for (int y = 0; y < height; y++) {
                set(x, y, z, Kind.SAME_LOG);
            }
            return this;
        }

        /** A 5x5 canopy, 2 layers, around the top of a trunk of the given height. */
        Grid canopy(int x, int z, int height, Kind leaves) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = height - 1; y <= height; y++) {
                        if ((dx != 0 || dz != 0 || y >= height) && at(x + dx, y, z + dz) == Kind.AIR) {
                            set(x + dx, y, z + dz, leaves);
                        }
                    }
                }
            }
            return this;
        }

        static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }
    }

    private static Grid oak(int height) {
        return new Grid().trunk(0, 0, height).canopy(0, 0, height, Kind.NATURAL_LEAVES);
    }

    private static boolean has(Tree t, int x, int y, int z) {
        return t.logs().stream().anyMatch(p -> p[0] == x && p[1] == y && p[2] == z);
    }

    @Test
    void naturalOakIsFelledWhole() {
        Tree t = TreeShape.survey(oak(5), 0, 0, 0, false);
        assertEquals(Verdict.OK, t.verdict());
        assertEquals(5, t.logs().size(), "every log of the trunk");
        for (int i = 0; i < 5; i++) {
            assertEquals(4 - i, t.logs().get(i)[1], "logs come top-down");
        }
        assertEquals(4, t.topDy());
        assertFalse(t.big());
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(oak(5).set(0, -1, 0, Kind.OTHER), 0, 0, 0, false).verdict(),
                "a trunk on stone is not a natural tree");
    }

    @Test
    void frameLogTouchingPlanksAborts() {
        // a house corner: a log column with planks against it, and natural leaves nearby (a hedge does not help)
        Grid g = oak(4).set(1, 1, 0, Kind.STRUCTURE);
        assertEquals(Verdict.TOUCHES_STRUCTURE, TreeShape.survey(g, 0, 0, 0, false).verdict());
        // a built block counts only when it touches a log face to face: one diagonally above the top log does not
        assertEquals(Verdict.OK, TreeShape.survey(oak(4).set(1, 4, 1, Kind.STRUCTURE), 0, 0, 0, false).verdict());
        // standing on a floor of planks is touching it too
        assertEquals(Verdict.TOUCHES_STRUCTURE, TreeShape.survey(oak(4).set(0, -1, 0, Kind.STRUCTURE), 0, 0, 0, false).verdict());
    }

    @Test
    void logTouchingAnotherSpeciesAborts() {
        Grid g = oak(5).set(0, 2, 1, Kind.OTHER_LOG);
        Tree t = TreeShape.survey(g, 0, 0, 0, false);
        assertEquals(Verdict.TOUCHES_STRUCTURE, t.verdict());
        assertFalse(has(t, 0, 2, 1), "another kind of log never joins the flood");
    }

    @Test
    void strippedLogDoesNotJoinTheFlood() {
        // a stripped log (another block than the stump's) sits diagonally above the top log: the flood would take
        // a same-kind log there, but this one stays, and since it touches no face the tree is still fine
        Grid g = oak(4).set(1, 4, 1, Kind.OTHER_LOG);
        Tree t = TreeShape.survey(g, 0, 0, 0, false);
        assertFalse(has(t, 1, 4, 1));
        assertEquals(4, t.logs().size());
        assertEquals(Verdict.OK, t.verdict());
        // the same position as a log of the stump's kind joins
        Tree same = TreeShape.survey(oak(4).set(1, 4, 1, Kind.SAME_LOG), 0, 0, 0, false);
        assertTrue(has(same, 1, 4, 1));
        assertEquals(5, same.logs().size());
    }

    @Test
    void persistentLeavesOnlyIsNotNatural() {
        Grid g = new Grid().trunk(0, 0, 4).canopy(0, 0, 4, Kind.PERSISTENT_LEAVES);
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(g, 0, 0, 0, false).verdict());
        // one natural leaf within 2 of the top is enough
        g.set(2, 5, 2, Kind.NATURAL_LEAVES);
        assertEquals(Verdict.OK, TreeShape.survey(g, 0, 0, 0, false).verdict());
        // but not 3 away
        Grid far = new Grid().trunk(0, 0, 4).canopy(0, 0, 4, Kind.PERSISTENT_LEAVES).set(3, 3, 0, Kind.NATURAL_LEAVES);
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(far, 0, 0, 0, false).verdict());
        // no leaves at all: a pillar of logs
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(new Grid().trunk(0, 0, 4), 0, 0, 0, false).verdict());
    }

    @Test
    void tooTallTreeIsRejectedBeforeTheFirstCut() {
        Tree tall = TreeShape.survey(oak(10), 0, 0, 0, false); // top log at dy 9
        assertEquals(Verdict.TOO_TALL, tall.verdict());
        assertEquals(9, tall.topDy());
        Tree fine = TreeShape.survey(oak(9), 0, 0, 0, false); // top log at dy 8
        assertEquals(Verdict.OK, fine.verdict());
        assertEquals(TreeShape.MAX_FELL_DY, fine.topDy());
        // a branch high up counts as much as the trunk
        Grid branch = oak(6).set(1, 6, 0, Kind.SAME_LOG).set(2, 7, 0, Kind.SAME_LOG).set(3, 8, 0, Kind.SAME_LOG).set(3, 9, 1, Kind.SAME_LOG);
        assertEquals(Verdict.TOO_TALL, TreeShape.survey(branch, 0, 0, 0, false).verdict());
        assertEquals(Verdict.TOO_TALL, TreeShape.survey(oak(10), 0, 0, 0, true).verdict(), "also when resuming");
    }

    @Test
    void twoByTwoStumpIsFound() {
        Grid g = new Grid();
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                g.trunk(x, z, 6);
            }
        }
        g.canopy(0, 0, 6, Kind.NATURAL_LEAVES);
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            Tree t = TreeShape.survey(g, c[0], 0, c[1], false);
            assertTrue(t.big(), "stump " + c[0] + "," + c[1] + " is part of a 2x2 trunk");
            assertEquals(Verdict.OK, t.verdict());
            assertEquals(24, t.logs().size(), "all four columns are one tree");
            assertArrayEquals(new int[]{0, 0}, TreeShape.twoByTwoCorner(g, c[0], 0, c[1]), "the north-west corner");
        }
        assertFalse(TreeShape.survey(oak(5), 0, 0, 0, false).big());
        assertNull(TreeShape.twoByTwoCorner(oak(5), 0, 0, 0));
        // three columns are not a 2x2 trunk
        Grid three = new Grid().trunk(0, 0, 5).trunk(1, 0, 5).trunk(0, 1, 5).canopy(0, 0, 5, Kind.NATURAL_LEAVES);
        assertFalse(TreeShape.survey(three, 0, 0, 0, false).big());
    }

    @Test
    void floodBoundsHold() {
        // sideways: a long horizontal log at stump height reaches only 7 out
        Grid side = oak(4);
        for (int x = 1; x <= 12; x++) {
            side.set(x, 0, 0, Kind.SAME_LOG);
        }
        Tree s = TreeShape.survey(side, 0, 0, 0, false);
        assertTrue(s.logs().stream().allMatch(p -> Math.abs(p[0]) <= TreeShape.MAX_SPREAD && Math.abs(p[2]) <= TreeShape.MAX_SPREAD));
        assertTrue(has(s, 7, 0, 0));
        assertFalse(has(s, 8, 0, 0));
        // up: a 40-high column is followed to +24 only
        Tree up = TreeShape.survey(new Grid().trunk(0, 0, 40), 0, 0, 0, false);
        assertEquals(TreeShape.MAX_RISE + 1, up.logs().size());
        assertEquals(TreeShape.MAX_RISE, up.topDy());
        // never more than 96 logs: a solid 9x9x9 block of logs
        Grid block = new Grid();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                block.trunk(x, z, 9);
            }
        }
        assertEquals(TreeShape.MAX_LOGS, TreeShape.survey(block, 0, 0, 0, false).logs().size());
        // nothing below the stump is followed
        Grid below = oak(4).set(1, -1, 0, Kind.SAME_LOG);
        assertFalse(has(TreeShape.survey(below, 0, 0, 0, false), 1, -1, 0));
    }

    @Test
    void acaciaDiagonalBranchesFollowed() {
        // an acacia: trunk 0..3, then a branch going up diagonally, logs touching only at their edges
        Grid g = new Grid().trunk(0, 0, 4);
        g.set(1, 4, 1, Kind.SAME_LOG).set(2, 5, 2, Kind.SAME_LOG).set(-1, 4, 0, Kind.SAME_LOG).set(-2, 5, -1, Kind.SAME_LOG);
        g.canopy(2, 2, 6, Kind.NATURAL_LEAVES);
        Tree t = TreeShape.survey(g, 0, 0, 0, false);
        assertEquals(Verdict.OK, t.verdict());
        assertEquals(8, t.logs().size());
        assertTrue(has(t, 2, 5, 2) && has(t, -2, 5, -1), "both diagonal branches");
        assertEquals(5, t.logs().get(0)[1], "a branch top comes first");
    }

    @Test
    void resumeSkipsTheGroundRule() {
        // the top of this tree is already cut: two logs left, no leaves near the new top, and (on a slope) no soil
        Grid g = new Grid().trunk(0, 0, 2).set(0, -1, 0, Kind.OTHER);
        assertEquals(Verdict.TOO_SMALL, TreeShape.survey(g, 0, 0, 0, false).verdict());
        Tree t = TreeShape.survey(g, 0, 0, 0, true);
        assertEquals(Verdict.OK, t.verdict());
        assertEquals(2, t.logs().size());
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(new Grid().trunk(0, 0, 4).set(0, -1, 0, Kind.OTHER), 0, 0, 0, false).verdict());
        // nothing left at the stump: resuming finds nothing to fell
        Tree gone = TreeShape.survey(new Grid(), 0, 0, 0, true);
        assertEquals(Verdict.OK, gone.verdict());
        assertTrue(gone.logs().isEmpty());
        assertEquals(Verdict.TOO_SMALL, TreeShape.survey(new Grid(), 0, 0, 0, false).verdict());
        assertEquals(List.of(), TreeShape.survey(new Grid(), 0, 0, 0, false).logs());
    }
}
