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
    void branchRunningDiagonallyIntoKeptWoodLeavesTheTreeWhole() {
        // a giant jungle tree's branch leaves the trunk one block out and one up at a time (diagonal steps only)
        Grid g = oak(12).set(1, 9, 1, Kind.SAME_LOG).set(2, 10, 2, Kind.SAME_LOG);
        Tree free = TreeShape.survey(g, 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.OK, free.verdict());
        assertTrue(has(free, 2, 10, 2), "a branch's diagonal steps join the flood");
        // its end lies in wood the woodcutter may not fell (by the walls): felling the rest would leave it hanging
        g.set(3, 11, 3, Kind.KEPT_LOG);
        Tree kept = TreeShape.survey(g, 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.TOUCHES_STRUCTURE, kept.verdict(), "a branch running into kept wood leaves the tree whole");
        assertFalse(has(kept, 3, 11, 3));
        assertEquals(Verdict.TOUCHES_STRUCTURE, TreeShape.survey(oak(4).set(1, 2, 0, Kind.KEPT_LOG), 0, 0, 0, false).verdict(),
                "face to face as well");
        // a built block diagonally beside a branch is still no contact: only the tree's own wood counts there
        assertEquals(Verdict.OK, TreeShape.survey(oak(12).set(1, 9, 1, Kind.SAME_LOG).set(2, 10, 2, Kind.STRUCTURE), 0, 0, 0, false,
                TreeShape.MAX_TALL_DY).verdict());
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
        assertEquals(Verdict.TOO_BIG, s.verdict(), "the log goes on past 7: only part of it was seen, none of it is cut");
        // up: a 40-high column is followed to +24 only
        Tree up = TreeShape.survey(new Grid().trunk(0, 0, 40), 0, 0, 0, false);
        assertEquals(TreeShape.MAX_RISE + 1, up.logs().size());
        assertEquals(TreeShape.MAX_RISE, up.topDy());
        assertEquals(Verdict.TOO_BIG, up.verdict());
        // never more than 96 logs: a solid 9x9x9 block of logs
        Grid block = new Grid();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                block.trunk(x, z, 9);
            }
        }
        assertEquals(TreeShape.MAX_LOGS, TreeShape.survey(block, 0, 0, 0, false).logs().size());
        assertEquals(Verdict.TOO_BIG, TreeShape.survey(block, 0, 0, 0, false).verdict());
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

    @Test
    void woodRunningPastTheBoundsIsNeverCutInPart() {
        // a log reaching exactly 7 out is a whole tree
        Grid seven = oak(4);
        for (int x = 1; x <= 7; x++) {
            seven.set(x, 1, 0, Kind.SAME_LOG);
        }
        assertEquals(Verdict.OK, TreeShape.survey(seven, 0, 0, 0, false).verdict());
        assertEquals(Verdict.TOO_BIG, TreeShape.survey(seven.set(8, 1, 0, Kind.SAME_LOG), 0, 0, 0, false).verdict(), "one more is not");
        // a planted square of dark oaks a block apart, grown into each other: far more than 96 logs, every top within
        // reach and nothing else touching. Felling the first 96 would leave the others' tops hanging over their stubs.
        Grid farm = new Grid();
        for (int tx = 0; tx <= 6; tx += 3) {
            for (int tz = 0; tz <= 6; tz += 3) {
                for (int i = 0; i <= 1; i++) {
                    for (int j = 0; j <= 1; j++) {
                        farm.trunk(tx + i, tz + j, 7);
                    }
                }
                // each tree's branches reach the next one's trunk at the canopy
                farm.set(tx + 2, 5, tz, Kind.SAME_LOG).set(tx, 5, tz + 2, Kind.SAME_LOG);
            }
        }
        farm.canopy(0, 0, 7, Kind.NATURAL_LEAVES);
        Tree t = TreeShape.survey(farm, 0, 0, 0, false);
        assertTrue(t.logs().size() <= TreeShape.MAX_LOGS);
        assertEquals(Verdict.TOO_BIG, t.verdict(), "the flood had to stop short of the farm: nothing is cut");
        // one such tree alone is fine
        Grid one = new Grid();
        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                one.trunk(i, j, 7);
            }
        }
        one.canopy(0, 0, 7, Kind.NATURAL_LEAVES);
        assertEquals(Verdict.OK, TreeShape.survey(one, 0, 0, 0, false).verdict());
        // and the same when resuming a tree whose wood was found to run on
        assertEquals(Verdict.TOO_BIG, TreeShape.survey(farm, 0, 0, 0, true).verdict());
    }

    @Test
    void aCherryBranchEndIsCutFromBelowNotLeftFloating() {
        // a vanilla cherry: 8 tall, a branch going 5 out with its end log 7 up (the trunk top), leaves around the end
        Grid g = new Grid().trunk(0, 0, 8);
        g.set(1, 4, 0, Kind.SAME_LOG).set(2, 5, 0, Kind.SAME_LOG).set(3, 5, 0, Kind.SAME_LOG).set(4, 6, 0, Kind.SAME_LOG)
                .set(5, 7, 0, Kind.SAME_LOG);
        g.canopy(5, 0, 8, Kind.NATURAL_LEAVES).canopy(0, 0, 8, Kind.NATURAL_LEAVES);
        Tree t = TreeShape.survey(g, 0, 0, 0, false);
        assertEquals(Verdict.OK, t.verdict(), "the cherry is felled, not refused");
        assertTrue(has(t, 5, 7, 0));
        // from the cut stump's column the branch end is out of reach (7.72 from the eyes, more where the walk stops)...
        assertFalse(TreeShape.fromStumpColumn(5, 7, 0));
        // ...but standing right under it, it is (5.9 up)
        assertTrue(TreeShape.fromBelow(7));
        // the stump column reaches everything straight up to +8, and a little sideways
        for (int dy = 0; dy <= TreeShape.MAX_FELL_DY; dy++) {
            assertTrue(TreeShape.fromStumpColumn(0, dy, 0), "straight up at +" + dy);
            assertTrue(TreeShape.fromBelow(dy), "from below at +" + dy);
        }
        assertFalse(TreeShape.fromStumpColumn(0, TreeShape.MAX_FELL_DY + 1, 0));
        assertTrue(TreeShape.fromStumpColumn(2, 7, 0));
        // an acacia's top 3 out and 8 up is 7.51 from the column's centre: from below as well
        assertFalse(TreeShape.fromStumpColumn(3, 8, 0));
        assertFalse(TreeShape.fromStumpColumn(-4, 7, 0), "the stop allowance counts: 7.11 + 0.45 is out of reach");
        // a low branch end far out is still in reach from the column
        assertTrue(TreeShape.fromStumpColumn(7, 1, 0));
    }

    /** A 2x2 trunk of the given height with its north-west corner at (0, 0), a canopy around its top. */
    private static Grid bigTrunk(int height) {
        Grid g = new Grid();
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                g.trunk(x, z, height);
            }
        }
        return g.canopy(0, 0, height, Kind.NATURAL_LEAVES).canopy(1, 1, height, Kind.NATURAL_LEAVES);
    }

    private static java.util.List<int[]> logsOf(Tree t) {
        return t.logs();
    }

    /** True when every log is in reach from column (cx, cz) at some pillar height from 0 to h (or from the ground). */
    private static boolean everyLogReached(java.util.List<int[]> logs, TreeShape.Pillar p, boolean groundToo) {
        for (int[] q : logs) {
            if (groundToo && TreeShape.fromGround(q[0], q[1], q[2])) {
                continue;
            }
            boolean reached = false;
            for (int h = 0; h <= p.height() && !reached; h++) {
                double dx = q[0] - p.x(), dz = q[2] - p.z(), dy = q[1] + 0.5 - (h + TreeShape.EYE);
                reached = Math.sqrt(dx * dx + dz * dz + dy * dy) <= TreeShape.PILLAR_REACH + 1e-9;
            }
            if (!reached) {
                return false;
            }
        }
        return true;
    }

    @Test
    void tallTreesAreFelledOnlyWithinTheScaffoldBounds() {
        // a mega spruce, 20 tall on a 2x2 trunk: too tall from the ground, fine for a woodcutter with a scaffold
        Tree ground = TreeShape.survey(bigTrunk(20), 0, 0, 0, false);
        assertEquals(Verdict.TOO_TALL, ground.verdict());
        Tree tall = TreeShape.survey(bigTrunk(20), 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.OK, tall.verdict());
        assertEquals(80, tall.logs().size());
        assertTrue(tall.big());
        assertEquals(19, tall.topDy());
        // the tallest giant jungle tree (31, 2x2: 124 logs) is still seen whole
        Tree giant = TreeShape.survey(bigTrunk(31), 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.OK, giant.verdict());
        assertEquals(124, giant.logs().size());
        // 33 tall runs past the flood's 32: never cut in part
        assertEquals(Verdict.TOO_BIG, TreeShape.survey(bigTrunk(34), 0, 0, 0, false, TreeShape.MAX_TALL_DY).verdict());
        // the other rules hold for tall trees too: persistent leaves only, or a built block against the trunk
        Grid hedge = new Grid().trunk(0, 0, 14).canopy(0, 0, 14, Kind.PERSISTENT_LEAVES);
        assertEquals(Verdict.NOT_NATURAL, TreeShape.survey(hedge, 0, 0, 0, false, TreeShape.MAX_TALL_DY).verdict());
        assertEquals(Verdict.TOUCHES_STRUCTURE,
                TreeShape.survey(bigTrunk(20).set(2, 12, 0, Kind.STRUCTURE), 0, 0, 0, false, TreeShape.MAX_TALL_DY).verdict());
    }

    @Test
    void aPillarBesideTheTrunkReachesEveryLog() {
        Tree t = TreeShape.survey(bigTrunk(20), 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        TreeShape.Pillar p = TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> TreeShape.MAX_PILLAR, true);
        assertNotNull(p);
        assertTrue(p.x() >= -1 && p.x() <= 2 && p.z() >= -1 && p.z() <= 2 && !(p.x() >= 0 && p.x() <= 1 && p.z() >= 0 && p.z() <= 1),
                "beside the 2x2 trunk, not in it: " + p);
        assertTrue(everyLogReached(t.logs(), p, true), "every log out of reach from the ground is reached from the pillar " + p);
        // the lowest such pillar: one block less leaves the top log out of reach
        assertFalse(everyLogReached(t.logs(), new TreeShape.Pillar(p.x(), p.z(), p.height() - 1), true));
        assertEquals(12, TreeShape.besideTrunk(0, 0, true).size());
        assertEquals(8, TreeShape.besideTrunk(0, 0, false).size());
        // a single tall jungle trunk, 13 tall: a pillar of 4
        Grid jungle = new Grid().trunk(0, 0, 13).canopy(0, 0, 13, Kind.NATURAL_LEAVES);
        Tree j = TreeShape.survey(jungle, 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        TreeShape.Pillar pj = TreeShape.pillarSpot(j.logs(), 0, 0, 0, false, (x, z) -> TreeShape.MAX_PILLAR, true);
        assertNotNull(pj);
        assertEquals(4, pj.height());
        assertTrue(Math.abs(pj.x()) + Math.abs(pj.z()) == 1, "straight beside the trunk, not diagonally: " + pj);
        // no room anywhere (a pillar may not stand by the trunk): no pillar, the tree is left whole
        assertNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> -1, true));
        // room for only 5 blocks: the top is out of reach
        assertNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> 5, true));
    }

    @Test
    void wideBranchesMustAllBeReachedFromOnePillar() {
        // a giant jungle tree: 2x2 trunk 24 tall, branches ending 4 out past the south-east corner and 2 out past the
        // north-west one (vanilla's branch ends lie from -2 to 5 of the trunk's north-west corner)
        Grid g = bigTrunk(24);
        g.set(4, 17, 4, Kind.SAME_LOG).set(5, 18, 5, Kind.SAME_LOG).set(3, 16, 3, Kind.SAME_LOG).set(2, 16, 2, Kind.SAME_LOG);
        g.set(-1, 19, -1, Kind.SAME_LOG).set(-2, 20, -2, Kind.SAME_LOG);
        g.canopy(5, 5, 19, Kind.NATURAL_LEAVES).canopy(-2, -2, 21, Kind.NATURAL_LEAVES);
        Tree t = TreeShape.survey(g, 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.OK, t.verdict());
        assertTrue(has(t, 5, 18, 5) && has(t, -2, 20, -2));
        TreeShape.Pillar p = TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> TreeShape.MAX_PILLAR, true);
        assertNotNull(p);
        assertTrue(everyLogReached(t.logs(), p, true), "both branch ends from one pillar: " + p);
        // the north-west corner column cannot reach the far branch end (8.5 across): with only that one free, the
        // tree is refused rather than left with a floating branch
        assertNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> x == -1 && z == -1 ? TreeShape.MAX_PILLAR : -1, true));
        // a column in a branch's way is fine: its logs are cut on the way up
        assertNotNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> x == 2 && z == 2 ? TreeShape.MAX_PILLAR : -1, true));
    }

    @Test
    void lowBranchEndsAreLeftToTheGroundUnlessEverythingMustBeReached() {
        // a tall trunk with a long low branch ending 7 out and 7 up: cut from the ground under it, as before
        Grid g = new Grid().trunk(0, 0, 14).canopy(0, 0, 14, Kind.NATURAL_LEAVES);
        g.set(1, 4, 0, Kind.SAME_LOG).set(2, 5, 0, Kind.SAME_LOG).set(3, 5, 0, Kind.SAME_LOG).set(4, 6, 0, Kind.SAME_LOG)
                .set(5, 7, 0, Kind.SAME_LOG).set(6, 7, 0, Kind.SAME_LOG).set(7, 7, 0, Kind.SAME_LOG);
        Tree t = TreeShape.survey(g, 0, 0, 0, false, TreeShape.MAX_TALL_DY);
        assertEquals(Verdict.OK, t.verdict());
        assertTrue(TreeShape.fromGround(7, 7, 0));
        // only the column west of the trunk is free: the branch end is 8 across from it, out of reach at any height
        TreeShape.Room west = (x, z) -> x == -1 && z == 0 ? TreeShape.MAX_PILLAR : -1;
        assertNotNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, false, west, true), "the branch end is cut from the ground");
        assertNull(TreeShape.pillarSpot(t.logs(), 0, 0, 0, false, west, false), "everything from the pillar: out of reach");
        // scaffold blocks left standing, 12 high: taken down from a pillar beside them, a low column from the ground
        java.util.List<int[]> blocks = new java.util.ArrayList<>();
        for (int y = 0; y < 12; y++) {
            blocks.add(new int[]{0, y, 0});
        }
        TreeShape.Pillar beside = TreeShape.pillarSpot(blocks, 0, 0, 0, false, (x, z) -> TreeShape.MAX_PILLAR, false);
        assertNotNull(beside);
        assertEquals(3, beside.height(), "the top block, 11 up, is in reach from 3 up");
        assertEquals(0, TreeShape.pillarSpot(blocks.subList(0, 8), 0, 0, 0, false, (x, z) -> TreeShape.MAX_PILLAR, false).height());
    }

    @Test
    void scaffoldBlocksCarriedCoverThePillar() {
        assertEquals(0, TreeShape.scaffoldNeed(TreeShape.MAX_FELL_DY));
        for (int h = TreeShape.MAX_FELL_DY + 2; h <= TreeShape.MAX_TALL_DY + 1; h++) {
            Tree t = TreeShape.survey(bigTrunk(h), 0, 0, 0, false, TreeShape.MAX_TALL_DY);
            assertEquals(Verdict.OK, t.verdict(), "a 2x2 trunk " + h + " tall");
            TreeShape.Pillar p = TreeShape.pillarSpot(t.logs(), 0, 0, 0, true, (x, z) -> TreeShape.MAX_PILLAR, true);
            assertNotNull(p, "a pillar for a trunk " + h + " tall");
            assertTrue(p.height() + TreeShape.PILLAR_SPARE <= TreeShape.scaffoldNeed(t.topDy()),
                    "the blocks fetched for a top " + t.topDy() + " up cover the pillar of " + p.height());
            assertTrue(TreeShape.scaffoldNeed(t.topDy()) <= TreeShape.MAX_PILLAR + TreeShape.PILLAR_SPARE);
        }
        // the reach window from a column: a log level with the eyes, 1 across, is in reach from heights around it
        int[] r = TreeShape.standRange(0, 0, 0, 1, 10, 0);
        assertNotNull(r);
        assertTrue(r[0] <= 9 && r[1] >= 9 && r[0] > 0);
        assertNull(TreeShape.standRange(0, 0, 0, 8, 10, 0), "8 across is out of reach at any height");
    }
}
