package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DirtPatchTest {
    /** An open grass field at height 64, with columns changed by hand. */
    private static final class Field implements DirtPatch.Ground {
        final Map<Long, DirtPatch.Column> changed = new HashMap<>();
        final Set<Long> unseen = new HashSet<>(), kept = new HashSet<>();
        int allowedAsked;

        @Override
        public DirtPatch.Column at(int x, int z) {
            long k = DirtPatch.key(x, z);
            if (unseen.contains(k)) {
                return null;
            }
            return changed.getOrDefault(k, new DirtPatch.Column(64, true, true));
        }

        @Override
        public boolean allowed(int x, int z) {
            allowedAsked++;
            return !kept.contains(DirtPatch.key(x, z));
        }

        Field set(int x, int z, int top, boolean standable, boolean diggable) {
            changed.put(DirtPatch.key(x, z), new DirtPatch.Column(top, standable, diggable));
            return this;
        }

        /** Digs the cell: its floor, a block lower, is ground one stands on (stone under the grass). */
        void dig(int[] c) {
            set(c[0], c[2], c[1] - 1, true, false);
        }
    }

    private static Set<Long> keys(List<int[]> cells) {
        Set<Long> out = new HashSet<>();
        for (int[] c : cells) {
            out.add(DirtPatch.key(c[0], c[2]));
        }
        return out;
    }

    @Test
    void anOpenFieldIsDugInPairsOfRowsWithARidgeBetween() {
        Field f = new Field();
        List<int[]> all = DirtPatch.patch(f, 0, 0, true, 1000);
        // a 7x7 window, rows along x: dz -3, -2, 0, 1, 3 are dug; -1 and 2 are ridges
        assertEquals(35, all.size());
        for (int[] c : all) {
            assertEquals(64, c[1], "only the top block is dug");
            assertNotEquals(2, Math.floorMod(c[2], 3), "no cell on a ridge row: " + c[2]);
            assertTrue(Math.abs(c[0]) <= DirtPatch.WINDOW && Math.abs(c[2]) <= DirtPatch.WINDOW);
        }
        assertArrayEquals(new int[]{0, 64, 0}, all.get(0), "the seed first");
        List<int[]> some = DirtPatch.patch(f, 0, 0, true, 22);
        assertEquals(22, some.size(), "never more than needed");
        for (int i = 1; i < some.size(); i++) {
            int[] a = some.get(i - 1), b = some.get(i);
            assertTrue(a[0] * a[0] + a[2] * a[2] <= b[0] * b[0] + b[2] * b[2], "nearest the seed first");
        }
        // along z, the ridges are columns of x instead
        for (int[] c : DirtPatch.patch(f, 10, 10, false, 1000)) {
            assertNotEquals(2, Math.floorMod(c[0] - 10, 3));
        }
        assertTrue(DirtPatch.patch(f, 0, 0, true, 0).isEmpty());
    }

    @Test
    void everyDugCellKeepsAStepOutOneBlockUp() {
        Field f = new Field();
        List<int[]> p = DirtPatch.patch(f, 5, -7, 1000);
        Set<Long> patch = keys(p);
        for (int[] c : p) {
            assertTrue(DirtPatch.mayDigNow(f, patch, c[0], c[1], c[2]), "each cell may be dug, in any order");
            f.dig(c);
        }
        for (int[] c : p) {
            boolean out = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                DirtPatch.Column n = f.at(c[0] + d[0], c[2] + d[1]);
                boolean dug = patch.contains(DirtPatch.key(c[0] + d[0], c[2] + d[1]));
                assertTrue(n.top() == (dug ? 63 : 64), "a dug cell is never more than a block below a neighbour");
                out |= !dug && n.standable() && n.top() == 64;
            }
            assertTrue(out, "a side neighbour one block up is left to step out onto");
        }
    }

    @Test
    void neverBesideADropAStepUpWaterOrSomethingThatIsNotGround() {
        Field f = new Field()
                .set(2, 0, 63, false, false)   // water (not ground to stand on)
                .set(-2, 2, 62, true, true)    // a drop of two
                .set(0, -3, 65, true, true)    // a step up
                .set(-3, 0, 64, true, false);  // stone: ground to stand on, not dug itself
        List<int[]> p = DirtPatch.patch(f, 0, 0, true, 1000);
        Set<Long> patch = keys(p);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertFalse(patch.contains(DirtPatch.key(2 + dx, dz)), "nothing by the water");
                assertFalse(patch.contains(DirtPatch.key(-2 + dx, 2 + dz)), "nothing by the drop");
                assertFalse(patch.contains(DirtPatch.key(dx, -3 + dz)), "nothing by the step up");
            }
        }
        assertFalse(patch.contains(DirtPatch.key(-3, 0)), "stone is not dug");
        assertTrue(patch.contains(DirtPatch.key(-2, 0)), "stone beside a cell is ground to step out onto");
        // a column not seen (not loaded) counts as neither
        f.unseen.add(DirtPatch.key(1, 1));
        assertFalse(keys(DirtPatch.patch(f, 0, 0, true, 1000)).contains(DirtPatch.key(0, 0)));
    }

    @Test
    void aPlaceKeptFromDiggingIsGroundToStepOutOnto() {
        Field f = new Field();
        f.kept.add(DirtPatch.key(0, 0));
        f.kept.add(DirtPatch.key(1, 0));
        Set<Long> patch = keys(DirtPatch.patch(f, 0, 0, true, 1000));
        assertFalse(patch.contains(DirtPatch.key(0, 0)) || patch.contains(DirtPatch.key(1, 0)), "kept places are not dug");
        assertTrue(patch.contains(DirtPatch.key(-1, 0)) && patch.contains(DirtPatch.key(0, 1)), "their neighbours are");
        assertFalse(DirtPatch.mayDigNow(f, Set.of(DirtPatch.key(0, 0)), 0, 64, 0), "nor at dig time");
        // the costly rule is asked only for cells that pass every other one
        Field g = new Field().set(0, 0, 63, false, false);
        g.allowedAsked = 0;
        assertTrue(DirtPatch.patch(g, 0, 0, true, 1000).size() < 35);
        assertEquals(DirtPatch.patch(g, 0, 0, true, 1000).size(), g.allowedAsked / 2);
    }

    @Test
    void anOldPatchIsNeverDugDeeperNorWidened() {
        Field f = new Field();
        List<int[]> first = DirtPatch.patch(f, 0, 0, true, 1000);
        Set<Long> old = keys(first);
        first.forEach(f::dig);
        // every seed around it, both ways: no cell on the old patch or beside one of its cells
        for (int sx = -6; sx <= 6; sx++) {
            for (int sz = -6; sz <= 6; sz++) {
                for (boolean along : new boolean[]{true, false}) {
                    for (int[] c : DirtPatch.patch(f, sx, sz, along, 1000)) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                assertFalse(old.contains(DirtPatch.key(c[0] + dx, c[2] + dz)),
                                        "a new cell at " + c[0] + "," + c[2] + " touches the old patch");
                            }
                        }
                        assertEquals(64, c[1]);
                    }
                }
            }
        }
        // the old floor is level ground below its rim: even a fresh field of diggable floor is not dug there
        for (int[] c : first) {
            f.set(c[0], c[2], 63, true, true);
        }
        for (int[] c : DirtPatch.patch(f, 0, 0, true, 1000)) {
            assertFalse(old.contains(DirtPatch.key(c[0], c[2])), "never a second block deep");
        }
    }

    @Test
    void theStepOutIsCheckedAgainAtDigTime() {
        Field f = new Field();
        List<int[]> p = DirtPatch.patch(f, 0, 0, true, 1000);
        Set<Long> patch = keys(p);
        assertArrayEquals(new int[]{0, 64, 0}, p.get(0));
        assertTrue(DirtPatch.mayDigNow(f, patch, 0, 64, 0));
        f.dig(new int[]{1, 64, 0}); // a patch neighbour dug: fine
        assertTrue(DirtPatch.mayDigNow(f, patch, 0, 64, 0));
        // its only side neighbour outside the patch (the ridge at dz = -1) dug by someone else: no step out
        f.dig(new int[]{0, 64, -1});
        assertFalse(DirtPatch.mayDigNow(f, patch, 0, 64, 0), "no step out left");
        // already dug, or not at the planned height
        f.dig(new int[]{3, 64, 3});
        assertFalse(DirtPatch.mayDigNow(f, patch, 3, 64, 3));
        // a neighbour that became a hole (two lower) or is not loaded
        f.set(-1, 4, 62, true, true);
        assertFalse(DirtPatch.mayDigNow(f, patch, 0, 64, 3));
        f.unseen.add(DirtPatch.key(-3, 1));
        assertFalse(DirtPatch.mayDigNow(f, patch, -2, 64, 0));
        assertTrue(DirtPatch.mayDigNow(f, patch, 2, 64, 1), "the rest of the patch still may be dug");
    }

    @Test
    void theRowsRunTheWayThatGivesMoreAndSmallPatchesAreLeft() {
        // a strip of grass 7 long in z and 2 wide in x, stone around it
        Field f = new Field();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                boolean grass = (x == 0 || x == 1) && Math.abs(z) <= 3;
                f.set(x, z, 64, true, grass);
            }
        }
        List<int[]> along = DirtPatch.patch(f, 0, 0, true, 1000), across = DirtPatch.patch(f, 0, 0, false, 1000);
        assertEquals(10, along.size(), "rows along x: 5 rows of 2");
        assertEquals(14, across.size(), "rows along z: the two columns of 7");
        assertEquals(14, DirtPatch.patch(f, 0, 0, 1000).size(), "the larger is chosen");
        assertEquals(6, DirtPatch.patch(f, 0, 0, 6).size());
        assertTrue(DirtPatch.worthDigging(4, 22));
        assertFalse(DirtPatch.worthDigging(3, 22), "too small a patch for a long need");
        assertTrue(DirtPatch.worthDigging(2, 2), "enough for what is still needed");
        assertFalse(DirtPatch.worthDigging(0, 0));
    }

    @Test
    void seedsLieOnALatticeNearestFirst() {
        List<int[]> s = DirtPatch.seeds(100, -40, DirtPatch.SEARCH_RADIUS, DirtPatch.SEED_SPACING);
        assertEquals(69, s.size(), "the lattice points within 24");
        assertArrayEquals(new int[]{100, -40}, s.get(0));
        int last = -1;
        for (int[] p : s) {
            int dx = p[0] - 100, dz = p[1] + 40;
            assertEquals(0, Math.floorMod(dx, 5));
            assertEquals(0, Math.floorMod(dz, 5));
            int d = dx * dx + dz * dz;
            assertTrue(d <= 24 * 24 && d >= last);
            last = d;
        }
        assertNotEquals(DirtPatch.key(1, 2), DirtPatch.key(2, 1));
        assertNotEquals(DirtPatch.key(-1, 0), DirtPatch.key(0, -1));
    }
}
