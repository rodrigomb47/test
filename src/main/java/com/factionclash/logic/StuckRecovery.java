package com.factionclash.logic;

/**
 * Picks the destination of the small stuck-recovery teleport (spec 13): at most 2 blocks horizontally and
 * 1 block up or down, to a validated safe spot, preferring the one closest to where the NPC is heading.
 */
public final class StuckRecovery {
    public static final int MAX_HORIZONTAL = 2;
    public static final int MAX_VERTICAL = 1;

    private StuckRecovery() {
    }

    @FunctionalInterface
    public interface SafetyCheck {
        /** True when an NPC can stand with its feet at (x, y, z). */
        boolean isSafeStandingSpot(int x, int y, int z);
    }

    public record Spot(int x, int y, int z) {
    }

    /**
     * @return the best safe spot that is closer to the goal than the current position, or null if none.
     */
    public static Spot choose(int x, int y, int z, double goalX, double goalY, double goalZ, SafetyCheck check) {
        double here = dist2(x + 0.5, y, z + 0.5, goalX, goalY, goalZ);
        Spot best = null;
        double bestD = here;
        for (int dy = -MAX_VERTICAL; dy <= MAX_VERTICAL; dy++) {
            for (int dx = -MAX_HORIZONTAL; dx <= MAX_HORIZONTAL; dx++) {
                for (int dz = -MAX_HORIZONTAL; dz <= MAX_HORIZONTAL; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx, ny = y + dy, nz = z + dz;
                    double d = dist2(nx + 0.5, ny, nz + 0.5, goalX, goalY, goalZ);
                    if (d < bestD - 1.0e-6 && check.isSafeStandingSpot(nx, ny, nz)) {
                        best = new Spot(nx, ny, nz);
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }

    private static double dist2(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }
}
