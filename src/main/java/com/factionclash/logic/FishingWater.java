package com.factionclash.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for where a fisherman may fish (no Minecraft classes, unit-tested). A fishing spot is a water source
 * the bobber floats on: real water at least 2 deep, open to the sky, part of a body of at least {@link #MIN_BODY}
 * surface blocks, with no farmland within {@link #FARMLAND_CLEARANCE} blocks (so no water that hydrates a farm, the
 * base's irrigation channel or a player's), and a dry place to stand 2 to 5 blocks away on the bank, from where the
 * fisherman can see the bobber. Whether it is vanilla "open water" (which lets the treasure catches drop) follows
 * the fishing hook's own 5x5 test exactly.
 */
public final class FishingWater {
    /** The fishing spot's state, as saved on the fishery: not looked for yet, found, or none within reach. */
    public static final int UNSEARCHED = 0, VALID = 1, NONE = 2;
    /** PROPOSED: the smallest body of water worth fishing (surface source blocks) and how far the flood counts. */
    public static final int MIN_BODY = 25, BODY_CAP = 400;
    /** PROPOSED: no farmland this close to the bobber; vanilla farmland is watered from 4 blocks away. */
    public static final int FARMLAND_CLEARANCE = 5;
    /** PROPOSED: the stand lies this far from the bobber (Chebyshev) and this much higher than the water's surface. */
    public static final int STAND_MIN = 2, STAND_MAX = 5, STAND_RISE_MIN = 1, STAND_RISE_MAX = 2;
    /** PROPOSED: a base that found no fishing water does not look again for this long (two days). */
    public static final long NONE_MEMORY = 48000;

    private FishingWater() {
    }

    /** The world as the fishing rules see it. */
    public interface Cells {
        /** A water source block. */
        boolean waterSource(int x, int y, int z);

        /** What vanilla counts as above the water: air or a lily pad. */
        boolean aboveWater(int x, int y, int z);

        /** What vanilla counts as inside the water: a water source with nothing to bump into (also kelp, seagrass). */
        boolean insideWater(int x, int y, int z);

        boolean farmland(int x, int y, int z);

        /** The sky can be seen from here (vanilla: full sky light). */
        boolean sky(int x, int y, int z);

        /** Solid ground to stand on: a sturdy top face that is not farmland, leaves, ice or a fluid. */
        boolean sturdyGround(int x, int y, int z);

        /** Room for a body: nothing to bump into and no fluid. */
        boolean clear(int x, int y, int z);

        /** Nothing blocks the view from the eyes of someone standing at s to the middle of the water block b. */
        boolean sight(int sx, int sy, int sz, int bx, int by, int bz);

        /** Whether a fisherman may stand here at all (the zone rules of the world adapter); everywhere by default. */
        default boolean standAllowed(int x, int y, int z) {
            return true;
        }
    }

    /** A fishing spot: the bobber's water block, the fisherman's stand (feet), open water or not, the body's size. */
    public record Spot(int bx, int by, int bz, int sx, int sy, int sz, boolean open, int body) {
    }

    /**
     * The fishing spot at bobber (x, y, z), or null when it is not one: the water must be a source with air or a lily
     * pad above and water below (at least 2 deep), under open sky, in a body of at least {@link #MIN_BODY} surface
     * blocks, with no farmland near, and with a stand on the bank.
     */
    public static Spot evaluate(Cells c, int x, int y, int z) {
        if (!c.waterSource(x, y, z) || !c.aboveWater(x, y + 1, z) || !c.waterSource(x, y - 1, z)) {
            return null;
        }
        if (!c.sky(x, y + 1, z)) {
            return null;
        }
        int body = bodySize(c, x, y, z, BODY_CAP);
        if (body < MIN_BODY || farmlandNear(c, x, y, z, FARMLAND_CLEARANCE)) {
            return null;
        }
        int[] s = stand(c, x, y, z);
        if (s == null) {
            return null;
        }
        return new Spot(x, y, z, s[0], s[1], s[2], openWater(c, x, y, z), body);
    }

    /**
     * The cheap check made at every cast: the bobber's block is still a water source with air above, the stand can
     * still be stood on, and no farmland has appeared near the water.
     */
    public static boolean stillFishable(Cells c, int bx, int by, int bz, int sx, int sy, int sz) {
        return c.waterSource(bx, by, bz) && c.aboveWater(bx, by + 1, bz) && c.sturdyGround(sx, sy - 1, sz)
                && c.clear(sx, sy, sz) && c.clear(sx, sy + 1, sz) && !farmlandNear(c, bx, by, bz, FARMLAND_CLEARANCE);
    }

    /**
     * How many surface water blocks (sources with air or a lily pad above) on the bobber's layer are joined to it
     * side by side, counting up to {@code cap}.
     */
    public static int bodySize(Cells c, int x, int y, int z, int cap) {
        if (!surface(c, x, y, z)) {
            return 0;
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> open = new ArrayDeque<>();
        seen.add(key(x, z));
        open.add(new int[]{x, z});
        int n = 0;
        while (!open.isEmpty() && n < cap) {
            int[] p = open.poll();
            n++;
            for (int[] d : SIDES) {
                int nx = p[0] + d[0], nz = p[1] + d[1];
                if (seen.add(key(nx, nz)) && surface(c, nx, y, nz)) {
                    open.add(new int[]{nx, nz});
                }
            }
        }
        return n;
    }

    /** Any farmland within {@code r} blocks sideways of (x, y, z), from one below to one above it. */
    public static boolean farmlandNear(Cells c, int x, int y, int z, int r) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (c.farmland(x + dx, y + dy, z + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private enum Layer { INSIDE, ABOVE, INVALID }

    /**
     * The fishing hook's open-water test, block for block: the four 5x5 layers from one below to two above the bobber
     * must each be all inside water or all above water, the lowest may not be above water, and no layer inside water
     * may lie above one that is above water.
     */
    public static boolean openWater(Cells c, int x, int y, int z) {
        Layer prev = Layer.INVALID;
        for (int dy = -1; dy <= 2; dy++) {
            Layer layer = layer(c, x, y + dy, z);
            switch (layer) {
                case ABOVE -> {
                    if (prev == Layer.INVALID) {
                        return false;
                    }
                }
                case INSIDE -> {
                    if (prev == Layer.ABOVE) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
            prev = layer;
        }
        return true;
    }

    private static Layer layer(Cells c, int x, int y, int z) {
        Layer out = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Layer l = c.aboveWater(x + dx, y, z + dz) ? Layer.ABOVE : c.insideWater(x + dx, y, z + dz) ? Layer.INSIDE : Layer.INVALID;
                if (out == null) {
                    out = l;
                } else if (out != l) {
                    return Layer.INVALID;
                }
            }
        }
        return out == null ? Layer.INVALID : out;
    }

    /** Stand offsets from the bobber, nearest first: {dx, dy, dz}. */
    private static final List<int[]> STAND_OFFSETS = new ArrayList<>();

    static {
        for (int dx = -STAND_MAX; dx <= STAND_MAX; dx++) {
            for (int dz = -STAND_MAX; dz <= STAND_MAX; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb < STAND_MIN) {
                    continue;
                }
                for (int dy = STAND_RISE_MIN; dy <= STAND_RISE_MAX; dy++) {
                    STAND_OFFSETS.add(new int[]{dx, dy, dz});
                }
            }
        }
        STAND_OFFSETS.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[2] * o[2]).thenComparingInt(o -> o[1])
                .thenComparingInt(o -> o[0]).thenComparingInt(o -> o[2]));
    }

    /**
     * The nearest place to stand (feet, {x, y, z}) for fishing at bobber (x, y, z): 2 to 5 blocks away (Chebyshev),
     * 1 or 2 blocks higher than the water, on solid ground, with room for a body, in sight of the bobber and allowed
     * by {@link Cells#standAllowed}. Null when there is none.
     */
    public static int[] stand(Cells c, int x, int y, int z) {
        for (int[] o : STAND_OFFSETS) {
            int sx = x + o[0], sy = y + o[1], sz = z + o[2];
            if (c.sturdyGround(sx, sy - 1, sz) && c.clear(sx, sy, sz) && c.clear(sx, sy + 1, sz)
                    && c.standAllowed(sx, sy, sz) && c.sight(sx, sy, sz, x, y, z)) {
                return new int[]{sx, sy, sz};
            }
        }
        return null;
    }

    /**
     * Whether the base may count on fishing water: it has a spot, or has not looked yet, or its last full search
     * (forage included) failed at least {@link #NONE_MEMORY} ticks ago, so the world may have changed since.
     */
    public static boolean waterKnown(int spot, long searchedAt, long now) {
        return spot == VALID || spot == UNSEARCHED || now - searchedAt >= NONE_MEMORY;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean surface(Cells c, int x, int y, int z) {
        return c.waterSource(x, y, z) && c.aboveWater(x, y + 1, z);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
