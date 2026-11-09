package com.factionclash.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a scout steps aside to when monsters close in (PROPOSED values): a point 12 to 20 blocks away in one of 16
 * directions, clear of every monster by that monster's keep-away distance, reached by a straight run that never takes
 * it nearer a monster than {@link #PASS_GAP} (or than it already is), and - among the clear points - as far as possible
 * on the side of where the scout was going, so that stepping aside is a detour rather than a U-turn. Running back the
 * way the last escape went is penalised, so two groups cannot bounce the scout between them. Pure rules: the world
 * adapter checks the ground (loaded, standable, dry, no drop), the campaign map and the route.
 */
public final class EscapeRoute {
    private EscapeRoute() {
    }

    public static final int DIRECTIONS = 16;
    /** Run lengths tried in each direction, the preferred first. */
    public static final int[] DISTANCES = {16, 20, 12};
    /** The straight run to an escape point passes no monster closer than this (or than it already is). */
    public static final double PASS_GAP = 4.0;
    /** Clearance beyond a monster's keep-away distance is worth this much at most: past that, the route side decides. */
    static final double CLEARANCE_CAP = 4.0;
    /**
     * A point is clear this far beyond every monster's keep-away distance: the scout stops within 1.5 blocks of it, and
     * a point right at the keep-away distance would leave it standing just inside it (a failed escape).
     */
    public static final double CLEAR_MARGIN = 2.0;
    /** Weight of the route side (dot product with the way to the target), of running away from the monsters' centre, and of reversing the last escape. */
    static final double TARGET_WEIGHT = 8.0, AWAY_WEIGHT = 1.0, REVERSE_WEIGHT = 6.0;

    /** A monster at (x, z) and how far from it counts as clear. */
    public record Threat(double x, double z, double keepAway) {
    }

    /**
     * A candidate escape point (block x, z), the unit direction of the run, its margin (the least distance to a
     * monster minus that monster's keep-away distance; clear when at least {@link #CLEAR_MARGIN}) and its score
     * (higher is better).
     */
    public record Option(int x, int z, double dirX, double dirZ, double margin, double score) {
        public boolean clear() {
            return margin >= CLEAR_MARGIN;
        }
    }

    /** The least distance from (x, z) to a monster minus that monster's keep-away distance (large when there are none). */
    public static double margin(double x, double z, List<Threat> threats) {
        double m = Double.MAX_VALUE;
        for (Threat t : threats) {
            m = Math.min(m, Math.hypot(x - t.x(), z - t.z()) - t.keepAway());
        }
        return m;
    }

    /**
     * Every escape point whose straight run is allowed, best first: clear points by score, then the points that are not
     * clear by margin (the least bad). The scout stands at (sx, sz); with {@code hasTarget} it was going to (tx, tz);
     * (prevX, prevZ) is the direction of its last escape in this encounter, or (0, 0).
     */
    public static List<Option> rank(double sx, double sz, List<Threat> threats, double tx, double tz, boolean hasTarget,
                                    double prevX, double prevZ) {
        double[] toTarget = unit(tx - sx, tz - sz, hasTarget);
        double cx = 0, cz = 0;
        for (Threat t : threats) {
            cx += t.x();
            cz += t.z();
        }
        double[] away = threats.isEmpty() ? new double[]{0, 0} : unit(sx - cx / threats.size(), sz - cz / threats.size(), true);
        List<Option> out = new ArrayList<>();
        for (int i = 0; i < DIRECTIONS; i++) {
            double a = 2 * Math.PI * i / DIRECTIONS;
            double dx = Math.cos(a), dz = Math.sin(a);
            for (int d : DISTANCES) {
                if (!runAllowed(sx, sz, dx, dz, d, threats)) {
                    continue;
                }
                int x = (int) Math.floor(sx + dx * d), z = (int) Math.floor(sz + dz * d);
                double m = margin(x + 0.5, z + 0.5, threats);
                double score = Math.min(m, CLEARANCE_CAP) + TARGET_WEIGHT * (dx * toTarget[0] + dz * toTarget[1])
                        + AWAY_WEIGHT * (dx * away[0] + dz * away[1]) - REVERSE_WEIGHT * Math.max(0, -(dx * prevX + dz * prevZ));
                out.add(new Option(x, z, dx, dz, m, score));
            }
        }
        out.sort(Comparator.comparing((Option o) -> !o.clear()).thenComparingDouble(o -> o.clear() ? -o.score() : -o.margin()));
        return out;
    }

    /**
     * Whether a straight run of {@code length} blocks from (sx, sz) in direction (dx, dz) keeps at least
     * {@link #PASS_GAP} from every monster - or, for a monster already nearer than that, never gets nearer than it is.
     */
    static boolean runAllowed(double sx, double sz, double dx, double dz, double length, List<Threat> threats) {
        for (Threat t : threats) {
            double rx = t.x() - sx, rz = t.z() - sz;
            double now = Math.hypot(rx, rz);
            double along = Math.max(0, Math.min(length, rx * dx + rz * dz));
            double closest = Math.hypot(rx - dx * along, rz - dz * along);
            if (closest < Math.min(PASS_GAP, now) - 1.0E-6) {
                return false;
            }
        }
        return true;
    }

    private static double[] unit(double x, double z, boolean use) {
        double l = Math.hypot(x, z);
        return !use || l < 1.0E-6 ? new double[]{0, 0} : new double[]{x / l, z / l};
    }
}
