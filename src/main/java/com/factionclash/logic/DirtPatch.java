package com.factionclash.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Where a woodcutter digs its own dirt for a scaffold when the storehouse has none (pure, unit-tested): the top layer
 * of a flat patch of open ground, as a player would scrape it, never a hole.
 *
 * <ul>
 *   <li>Only the top block of a column is dug, so a dug cell is exactly one block below the ground around it.</li>
 *   <li>A cell is dug only when all eight of its neighbours are ground one can stand on at the same height: no drop,
 *       no step up, no water, no trunk beside it. This also keeps a patch off the cells of any earlier patch (they lie
 *       a block lower than their rims), so nothing is ever dug a second block deep.</li>
 *   <li>The patch is dug in pairs of rows with every third row left standing (a ridge), so every dug cell has a side
 *       neighbour that is never dug, at the old height: a one-block step out, whatever is dug around it.</li>
 *   <li>It spreads over a window around a seed (nearest the seed first), rather than going down in one place.</li>
 * </ul>
 */
public final class DirtPatch {
    private DirtPatch() {
    }

    /** PROPOSED: a patch is laid out over the window of this half-size around its seed (7x7). */
    public static final int WINDOW = 3;
    /** Rows of a patch repeat every 3: two dug, then one left standing as a ridge. */
    public static final int ROW_PERIOD = 3;
    /** PROPOSED: a patch of fewer cells is not dug (unless that is all the woodcutter still needs). */
    public static final int MIN_CELLS = 4;
    /** PROPOSED: seeds for a patch lie on a lattice this far apart, within this radius of the tree waiting for the dirt. */
    public static final int SEED_SPACING = 5, SEARCH_RADIUS = 24;

    /**
     * One column of ground: the height of its top block, whether one can stand on it (a solid top with two free cells
     * above it and no fluid), and whether its top could be dug, as far as the column itself goes (earth that drops
     * dirt, a firm floor under it, open sky).
     */
    public record Column(int top, boolean standable, boolean diggable) {
    }

    /** The ground as the woodcutter sees it. */
    @FunctionalInterface
    public interface Ground {
        /** Column (x, z), or null for one it cannot see (not loaded). */
        Column at(int x, int z);

        /**
         * Whether the top of (x, z) may be dug where it lies: nothing growing beside it and no place kept from digging.
         * Asked last, and only for a cell that passes every other rule (it is the costly one).
         */
        default boolean allowed(int x, int z) {
            return true;
        }
    }

    /** The key of column (x, z) in a patch's set of cells. */
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** True when (x, z) lies on a ridge row of a patch seeded at (sx, sz) with its rows along x ({@code alongX}) or z. */
    public static boolean ridge(int x, int z, int sx, int sz, boolean alongX) {
        int across = alongX ? z - sz : x - sx;
        return Math.floorMod(across, ROW_PERIOD) == ROW_PERIOD - 1;
    }

    /**
     * True when the top of (x, z) may be dug as part of a new patch: it may be dug, each of its eight neighbours is
     * ground one can stand on at the same height, and the place allows it ({@link Ground#allowed}).
     */
    public static boolean flatAround(Ground g, int x, int z) {
        Column c = g.at(x, z);
        if (c == null || !c.diggable() || !c.standable()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Column n = g.at(x + dx, z + dz);
                if (n == null || !n.standable() || n.top() != c.top()) {
                    return false;
                }
            }
        }
        return g.allowed(x, z);
    }

    /**
     * The cells of a patch seeded at (sx, sz) with its rows along x ({@code alongX}) or z: cells of the window that are
     * {@link #flatAround} and not on a ridge row, nearest the seed first, at most {@code need}. Each cell is
     * {x, top, z}: the block to dig.
     */
    public static List<int[]> patch(Ground g, int sx, int sz, boolean alongX, int need) {
        List<int[]> out = new ArrayList<>();
        if (need <= 0) {
            return out;
        }
        for (int dx = -WINDOW; dx <= WINDOW; dx++) {
            for (int dz = -WINDOW; dz <= WINDOW; dz++) {
                int x = sx + dx, z = sz + dz;
                if (!ridge(x, z, sx, sz, alongX) && flatAround(g, x, z)) {
                    out.add(new int[]{x, g.at(x, z).top(), z});
                }
            }
        }
        out.sort(Comparator.comparingInt((int[] c) -> (c[0] - sx) * (c[0] - sx) + (c[2] - sz) * (c[2] - sz))
                .thenComparingInt(c -> c[0]).thenComparingInt(c -> c[2]));
        return out.size() > need ? new ArrayList<>(out.subList(0, need)) : out;
    }

    /** The larger of the two patches seeded at (sx, sz), rows along x or along z (along x on a tie). */
    public static List<int[]> patch(Ground g, int sx, int sz, int need) {
        List<int[]> a = patch(g, sx, sz, true, need);
        if (a.size() >= need) {
            return a;
        }
        List<int[]> b = patch(g, sx, sz, false, need);
        return b.size() > a.size() ? b : a;
    }

    /** True when a patch of {@code size} cells is worth digging for {@code need} more blocks. */
    public static boolean worthDigging(int size, int need) {
        return size > 0 && size >= Math.min(need, MIN_CELLS);
    }

    /**
     * At dig time, with the world as it is now: true when the top of (x, z), planned at height {@code y} as a cell of
     * the patch {@code patch} (keys of {@link #key}), may be dug. It must still be diggable at that height and allowed
     * there; each neighbour must be a cell of the patch (dug or not: one block lower, or level) or ground one can stand
     * on at that height; and at least one side neighbour outside the patch must be ground one can stand on at that
     * height, the step out.
     */
    public static boolean mayDigNow(Ground g, Set<Long> patch, int x, int y, int z) {
        Column c = g.at(x, z);
        if (c == null || c.top() != y || !c.diggable() || !c.standable()) {
            return false;
        }
        boolean stepOut = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Column n = g.at(x + dx, z + dz);
                if (n == null || !n.standable()) {
                    return false;
                }
                boolean inPatch = patch.contains(key(x + dx, z + dz));
                if (inPatch ? n.top() != y && n.top() != y - 1 : n.top() != y) {
                    return false;
                }
                if (!inPatch && (dx == 0 || dz == 0)) {
                    stepOut = true;
                }
            }
        }
        return stepOut && g.allowed(x, z);
    }

    /**
     * Seeds for a patch: the lattice points {@link #SEED_SPACING} apart around (cx, cz) within {@code radius}, nearest
     * first, as {x, z}.
     */
    public static List<int[]> seeds(int cx, int cz, int radius, int spacing) {
        List<int[]> out = new ArrayList<>();
        int n = radius / spacing;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                int dx = i * spacing, dz = j * spacing;
                if (dx * dx + dz * dz <= radius * radius) {
                    out.add(new int[]{cx + dx, cz + dz});
                }
            }
        }
        out.sort(Comparator.comparingInt((int[] p) -> (p[0] - cx) * (p[0] - cx) + (p[1] - cz) * (p[1] - cz))
                .thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
        return out;
    }
}
