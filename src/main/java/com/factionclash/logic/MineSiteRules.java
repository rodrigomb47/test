package com.factionclash.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for where a mine's tunnels lie (no Minecraft classes, unit-tested). A mine reserves a corridor: the
 * ground its branch mines can ever take up, from just behind the shaft to the end of the longest lengthening round
 * the site allows, and as wide as the branches. Corridors of different mines must not overlap, and must keep clear
 * of bases and the edge of the map.
 *
 * <p>Directions are given as a unit step (fx, fz) along the tunnels; "side" is the clockwise direction, (-fz, fx),
 * as in {@code Direction.getClockWise()}.
 *
 * <p>A miner choosing a site looks at points on square rings around its base ({@link #candidates}), lets the
 * tunnels run away from the base ({@link #facingsAwayFrom}), reserves as many lengthening rounds as fit
 * ({@link #maxRoundsThatFit}) and takes the site with the lowest {@link #score}.
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

    /**
     * The directions (fx, fz) a mine at offset (dx, dz) from its base centre may run its tunnels: the one pointing
     * most directly away from the base first, then the two at right angles to it (the more outward one first),
     * leaving out any that points back toward the base. Empty at the centre itself.
     */
    public static List<int[]> facingsAwayFrom(int dx, int dz) {
        List<int[]> out = new ArrayList<>();
        if (dx == 0 && dz == 0) {
            return out;
        }
        int[] main = Math.abs(dx) >= Math.abs(dz) ? new int[]{Integer.signum(dx), 0} : new int[]{0, Integer.signum(dz)};
        out.add(main);
        int[] a = {-main[1], main[0]}, b = {main[1], -main[0]};
        int da = a[0] * dx + a[1] * dz, db = b[0] * dx + b[1] * dz;
        for (int[] p : da >= db ? new int[][]{a, b} : new int[][]{b, a}) {
            if (p[0] * dx + p[1] * dz >= 0) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Points (x, z) to look at for a mine site around (cx, cz): square rings at Chebyshev distance {@code rMin},
     * {@code rMin + step}, ... up to {@code rMax}, inner rings first, with a point every {@code step} blocks along
     * each ring, corners included.
     */
    public static List<int[]> candidates(int cx, int cz, int rMin, int rMax, int step) {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> out = new ArrayList<>();
        int st = Math.max(1, step);
        for (int r = Math.max(1, rMin); r <= rMax; r += st) {
            List<Integer> along = new ArrayList<>();
            for (int t = -r; t < r; t += st) {
                along.add(t);
            }
            along.add(r);
            for (int t : along) {
                for (int[] d : new int[][]{{t, -r}, {r, t}, {-t, r}, {-r, -t}}) {
                    long key = ((long) d[0] << 32) ^ (d[1] & 0xffffffffL);
                    if (seen.add(key)) {
                        out.add(new int[]{cx + d[0], cz + d[1]});
                    }
                }
            }
        }
        return out;
    }

    /**
     * The most lengthening rounds (0 to {@code maxReach}) a mine at (ox, oz) facing (fx, fz) can reserve: its whole
     * corridor ({@link #corridor}) must overlap none of the {@code obstacles} (already widened by the gap each
     * keeps) and lie inside {@code map} (null: no edge). -1 when even the first branch mine does not fit.
     */
    public static int maxRoundsThatFit(int ox, int oz, int fx, int fz, int maxReach, List<Rect> obstacles, Rect map) {
        for (int r = 0; r <= maxReach; r++) {
            Rect c = corridor(ox, oz, fx, fz, r);
            boolean fits = map == null || (map.contains(c.minX(), c.minZ()) && map.contains(c.maxX(), c.maxZ()));
            for (int i = 0; fits && i < obstacles.size(); i++) {
                fits = !obstacles.get(i).overlaps(c);
            }
            if (!fits) {
                return r - 1;
            }
        }
        return maxReach;
    }

    /** PROPOSED: score weights: per point of corridor penalty, per block of uneven ground at the mouth, for loose ground. */
    public static final int PENALTY_WEIGHT = 2, RELIEF_WEIGHT = 4, LOOSE_COST = 10;

    /**
     * How good a site is, lower is better: the walk from the nearer gate, plus twice the corridor penalty (water
     * over the tunnels, ground dipping toward them), four per block of unevenness around the mouth, and 10 when the
     * ground there is sand or gravel that has to be propped.
     */
    public static double score(double gateDistance, int penalty, int collarRelief, boolean loose) {
        return gateDistance + PENALTY_WEIGHT * penalty + RELIEF_WEIGHT * collarRelief + (loose ? LOOSE_COST : 0);
    }
}
