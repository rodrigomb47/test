package com.factionclash.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of a player-style mine (no Minecraft classes, unit-tested).
 *
 * <p>Access is a 2x2 spiral staircase inside the surface hut: step k stands one block lower than step k-1 and turns
 * 90 degrees, so every column keeps a solid floor every four blocks (no long falls) and the whole shaft fits in a
 * 2x2 footprint. Each level has a "landing" step on the far side of the shaft, from which a classic branch mine
 * runs forward: a 2-high main tunnel with 2-high side branches every 4 blocks.
 *
 * <p>Coordinates: f = forward (away from the base), s = sideways, y relative to the hut floor (y0 = 0).
 */
public final class SpiralMine {
    /** Spiral cells (f, s) in walking order. Steps whose cell has f = 2 face the tunnels. */
    public static final int[][] CELLS = {{1, 0}, {2, 0}, {2, 1}, {1, 1}};
    /** PROPOSED level depths (absolute Y): tier 1 is 6 below the surface; then the vanilla ore bands. */
    public static final int[] TIER_TARGET_Y = {Integer.MIN_VALUE, 0, 32, 0, -48};
    public static final int MIN_Y = -56;
    public static final int MAIN_START = 3;
    public static final int MAIN_LENGTH = 40;
    public static final int BRANCH_LENGTH = 10;
    public static final int BRANCH_SPACING = 4;

    private SpiralMine() {
    }

    public static int[] cell(int step) {
        return CELLS[Math.floorMod(step - 1, 4)];
    }

    /** Standing height (relative to the hut floor) of spiral step k. */
    public static int standY(int step) {
        return -step;
    }

    /**
     * Number of spiral steps from the hut floor down to the landing of {@code level} (1-based; levels above 4 are
     * free extra floors 6 blocks apart). Landings always sit on a step whose cell has f = 2. Returns -1 when the
     * level would be below the world's mining floor.
     */
    public static int landingSteps(int surfaceY, int level) {
        int prev = 0;
        for (int t = 1; t <= level; t++) {
            int target = t == 1 ? surfaceY - 6 : (t <= 4 ? TIER_TARGET_Y[t] : surfaceY - prev - 6);
            int n = Math.max(prev + 6, surfaceY - target);
            while (Math.floorMod(n - 1, 4) != 1 && Math.floorMod(n - 1, 4) != 2) {
                n++;
            }
            if (surfaceY - n < MIN_Y) {
                return -1;
            }
            prev = n;
        }
        return prev;
    }

    /** Absolute Y of a level's tunnels, or Integer.MIN_VALUE if the level does not exist. */
    public static int levelY(int surfaceY, int level) {
        int n = landingSteps(surfaceY, level);
        return n < 0 ? Integer.MIN_VALUE : surfaceY - n;
    }

    /** One planned action in local coordinates. */
    public record Cell(int f, int s, int dy, boolean torch) {
    }

    /**
     * The branch-mine plan of a level, relative to the landing (dy 0 = tunnel floor level). Columns are dug top
     * first; torches stand on the tunnel floor every 8 blocks of main tunnel and in each branch.
     */
    public static List<Cell> tunnelPlan(int landingSide) {
        List<Cell> out = new ArrayList<>();
        for (int f = MAIN_START; f < MAIN_START + MAIN_LENGTH; f++) {
            out.add(new Cell(f, landingSide, 1, false));
            out.add(new Cell(f, landingSide, 0, false));
            if ((f - MAIN_START) % 8 == 4) {
                out.add(new Cell(f, landingSide, 0, true));
            }
            if (f > MAIN_START && (f - MAIN_START) % BRANCH_SPACING == 0) {
                for (int side : new int[]{-1, 1}) {
                    for (int d = 1; d <= BRANCH_LENGTH; d++) {
                        int s = landingSide + side * d;
                        out.add(new Cell(f, s, 1, false));
                        out.add(new Cell(f, s, 0, false));
                        if (d == 6) {
                            out.add(new Cell(f, s, 0, true));
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Steps from {@code fromStep}+1 to {@code toStep}: each step digs its column top-down (stand+2, +1, +0), with a
     * wall torch every 8 steps (dy 1, torch flag).
     */
    public static List<Cell> spiralPlan(int fromStep, int toStep) {
        List<Cell> out = new ArrayList<>();
        for (int k = fromStep + 1; k <= toStep; k++) {
            int[] c = cell(k);
            int y = standY(k);
            out.add(new Cell(c[0], c[1], y + 2, false));
            out.add(new Cell(c[0], c[1], y + 1, false));
            out.add(new Cell(c[0], c[1], y, false));
            if (k % 8 == 0) {
                out.add(new Cell(c[0], c[1], y + 1, true));
            }
        }
        return out;
    }

    /** True if (f, s, y) is open (air) space of the spiral down to {@code steps}. */
    public static boolean inShaft(int f, int s, int y, int steps) {
        if (y >= 0) {
            return false;
        }
        for (int k = 1; k <= steps; k++) {
            int[] c = cell(k);
            if (c[0] == f && c[1] == s && y >= standY(k) && y <= standY(k) + 2) {
                return true;
            }
        }
        return false;
    }
}
