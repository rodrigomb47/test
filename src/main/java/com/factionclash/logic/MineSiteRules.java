package com.factionclash.logic;

/**
 * Pure rules for where a mine's tunnels lie (no Minecraft classes, unit-tested). A mine reserves a corridor: the
 * ground its branch mines can ever take up, from just behind the shaft to the end of the longest lengthening round
 * the site allows, and as wide as the branches. Corridors of different mines must not overlap, and must keep clear
 * of bases and the edge of the map.
 *
 * <p>Directions are given as a unit step (fx, fz) along the tunnels; "side" is the clockwise direction, (-fz, fx),
 * as in {@code Direction.getClockWise()}.
 */
public final class MineSiteRules {
    /** First forward cell of the corridor: two behind the shaft (the shaft is at f 1..2). */
    public static final int CORRIDOR_BACK = -2;
    /** Side extent of the corridor: the branches reach 10 to either side of the landing (side 0 or 1), plus one. */
    public static final int CORRIDOR_SIDE_MIN = -SpiralMine.BRANCH_LENGTH - 1, CORRIDOR_SIDE_MAX = SpiralMine.BRANCH_LENGTH + 2;

    private MineSiteRules() {
    }

    /** The last forward cell of a branch mine lengthened {@code rounds} times. */
    public static int corridorEnd(int rounds) {
        return SpiralMine.MAIN_START - 1 + SpiralMine.MAIN_LENGTH * (Math.max(0, rounds) + 1);
    }

    /** A rectangle on the ground (x and z, both ends included). */
    public record Rect(int minX, int minZ, int maxX, int maxZ) {
        public Rect inflate(int n) {
            return new Rect(minX - n, minZ - n, maxX + n, maxZ + n);
        }

        public boolean overlaps(Rect o) {
            return minX <= o.maxX && o.minX <= maxX && minZ <= o.maxZ && o.minZ <= maxZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /** The ground between forward cells {@code f0..f1} and side cells {@code s0..s1} of a mine at (ox, oz). */
    public static Rect area(int ox, int oz, int fx, int fz, int f0, int f1, int s0, int s1) {
        int sx = -fz, sz = fx;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int f : new int[]{f0, f1}) {
            for (int s : new int[]{s0, s1}) {
                int x = ox + f * fx + s * sx, z = oz + f * fz + s * sz;
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
        }
        return new Rect(minX, minZ, maxX, maxZ);
    }

    /** The corridor a mine at (ox, oz) facing (fx, fz) reserves for {@code rounds} lengthening rounds. */
    public static Rect corridor(int ox, int oz, int fx, int fz, int rounds) {
        return area(ox, oz, fx, fz, CORRIDOR_BACK, corridorEnd(rounds), CORRIDOR_SIDE_MIN, CORRIDOR_SIDE_MAX);
    }

    /** The stretch a lengthening round adds to the corridor: round {@code round} (1 or more) beyond round - 1. */
    public static Rect segment(int ox, int oz, int fx, int fz, int round) {
        return area(ox, oz, fx, fz, corridorEnd(round - 1) + 1, corridorEnd(round), CORRIDOR_SIDE_MIN, CORRIDOR_SIDE_MAX);
    }

    /** True if two rectangles come within {@code margin} of each other. */
    public static boolean overlaps(Rect a, Rect b, int margin) {
        return a.inflate(margin).overlaps(b);
    }
}
