package com.factionclash.logic;

import java.util.List;

/** Pure geometry for levelled building pads (rectangles given as {x1, z1, x2, z2}, corners in any order). */
public final class Pads {
    private Pads() {
    }

    /** Chebyshev distance from (x, z) to the nearest pad rectangle; 0 inside one. */
    public static int distance(List<int[]> pads, int x, int z) {
        int best = Integer.MAX_VALUE;
        for (int[] r : pads) {
            int dx = Math.max(0, Math.max(Math.min(r[0], r[2]) - x, x - Math.max(r[0], r[2])));
            int dz = Math.max(0, Math.max(Math.min(r[1], r[3]) - z, z - Math.max(r[1], r[3])));
            best = Math.min(best, Math.max(dx, dz));
        }
        return best;
    }
}
