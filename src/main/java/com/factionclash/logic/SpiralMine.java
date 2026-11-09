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
     * True if step k is a landing: its cell has f = 2, on the far side of the shaft, where a level's tunnels start.
     */
    public static boolean isLanding(int k) {
        return Math.floorMod(k - 1, 4) == 1 || Math.floorMod(k - 1, 4) == 2;
    }

    /** The first landing step (cell facing the tunnels) at or below step n: the nearest one at least as deep. */
    public static int landingAtOrBelow(int n) {
        while (!isLanding(n)) {
            n++;
        }
        return n;
    }

    /** The last landing step at or above step n: the nearest one no deeper than n. */
    public static int landingAtOrAbove(int n) {
        while (!isLanding(n)) {
            n--;
        }
        return n;
    }

    /** Size of a level's plan after {@code rounds} lengthenings (the same for either landing side). */
    public static int planSize(int rounds) {
        return cachedPlan(0, rounds).size();
    }

    private static final java.util.Map<Integer, List<Cell>> PLANS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cached, read-only {@link #tunnelPlan(int, int)}: plans are rebuilt often and only depend on these two numbers. */
    public static List<Cell> cachedPlan(int landingSide, int rounds) {
        return PLANS.computeIfAbsent(landingSide * 64 + rounds, k -> java.util.Collections.unmodifiableList(tunnelPlan(landingSide, rounds)));
    }

    /** One planned action in local coordinates. */
    public record Cell(int f, int s, int dy, boolean torch) {
    }

    /**
     * The branch-mine plan of a level, relative to the landing (dy 0 = tunnel floor level). Columns are dug top
     * first; torches stand on the tunnel floor every 8 blocks of main tunnel and in each branch.
     */
    public static List<Cell> tunnelPlan(int landingSide) {
        return tunnelPlan(landingSide, 0);
    }

    /**
     * The plan lengthened {@code rounds} times: each round continues the main tunnel another {@link #MAIN_LENGTH}
     * blocks out, with its branches. Earlier cells keep their order, so progress through a shorter plan stays valid.
     */
    public static List<Cell> tunnelPlan(int landingSide, int rounds) {
        List<Cell> out = new ArrayList<>();
        for (int f = MAIN_START; f < MAIN_START + MAIN_LENGTH * (rounds + 1); f++) {
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

    /**
     * The step whose open cells include (f, s, y), y relative to the hut floor; 0 when it is not a cell of the spiral
     * (at or above the floor, outside the 2x2 column, or the floor between two steps of a column).
     */
    public static int shaftStepOf(int f, int s, int y) {
        if (y >= 0) {
            return 0;
        }
        // step k is open from standY(k) to standY(k) + 2; of three steps in a row only one is in a given column
        for (int k = Math.max(1, -y); k <= -y + 2; k++) {
            int[] c = cell(k);
            if (c[0] == f && c[1] == s) {
                return k;
            }
        }
        return 0;
    }

    /**
     * The index in {@link #spiralPlan}({@code fromStep}, ...) of the cell of step {@code k} (deeper than
     * {@code fromStep}) at height {@code y}, one of its three open rows: the plan digs each step's column top-down,
     * with a torch after every eighth step.
     */
    public static int spiralIndex(int fromStep, int k, int y) {
        int before = 0;
        for (int j = fromStep + 1; j < k; j++) {
            before += j % 8 == 0 ? 4 : 3;
        }
        return before + (standY(k) + 2 - y);
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
