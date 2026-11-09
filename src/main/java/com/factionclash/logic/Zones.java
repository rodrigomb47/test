package com.factionclash.logic;

/**
 * Pure zone arithmetic for where workers may work (no Minecraft classes, unit-tested). Base zones are measured on
 * the walls box: the Chebyshev distance from the base centre against the walls' half-size, at any height. The
 * campaign map is a square of {@code radius} around its centre; a radius of 0 means no map edge.
 */
public final class Zones {
    private Zones() {
    }

    /** Horizontal Chebyshev distance (the larger of |dx| and |dz|). */
    public static int chebyshev(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs(ax - bx), Math.abs(az - bz));
    }

    /** True when an offset (dx, dz) from a base centre lies within the walls' half-size plus {@code margin}. */
    public static boolean insideHalf(int dx, int dz, int half, int margin) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= half + margin;
    }

    /** The walls' half-size to use: a GameTest override when set (0 or more), else the layout's. */
    public static int half(int testHalf, int layoutHalf) {
        return testHalf >= 0 ? testHalf : layoutHalf;
    }

    /** The work radius to use: a GameTest override when set (above 0), else the configured one. */
    public static int workRadius(int testRadius, int configured) {
        return testRadius > 0 ? testRadius : configured;
    }

    /**
     * True when (x, z) lies on the campaign map at least {@code margin} blocks inside its edge. A map radius of 0 has
     * no edge, so everything is inside.
     */
    public static boolean mapInside(int cx, int cz, int radius, int x, int z, int margin) {
        if (radius <= 0) {
            return true;
        }
        int r = radius - margin;
        return Math.abs(x - cx) <= r && Math.abs(z - cz) <= r;
    }

    /**
     * (x, z) moved onto the map, {@code margin} blocks inside its edge (each axis clamped on its own), as {x, z}.
     * With a map radius of 0 the point is returned unchanged; a margin wider than the map clamps to the centre.
     */
    public static int[] mapClamp(int cx, int cz, int radius, int x, int z, int margin) {
        if (radius <= 0) {
            return new int[]{x, z};
        }
        int r = Math.max(0, radius - margin);
        return new int[]{clamp(x, cx - r, cx + r), clamp(z, cz - r, cz + r)};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
