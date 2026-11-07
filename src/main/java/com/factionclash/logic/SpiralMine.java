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
        if (level <= 0) {
            return 0;
        }
        int[] all = ALL_LANDINGS.computeIfAbsent(surfaceY, y -> {
            List<Integer> regular = regularLandings(y);
            List<Integer> list = new ArrayList<>(regular);
            list.addAll(infillLandings(regular));
            return list.stream().mapToInt(Integer::intValue).toArray();
        });
        return level <= all.length ? all[level - 1] : -1;
    }

    /** Landing steps of every level (regular then infill) by surface height: the same for every call, so cached. */
    private static final java.util.Map<Integer, int[]> ALL_LANDINGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Landing steps of the levels dug downward: tiers 1-4, then a floor every 6 blocks down to the mining floor. */
    static List<Integer> regularLandings(int surfaceY) {
        List<Integer> out = new ArrayList<>();
        int prev = 0;
        for (int t = 1; t < 64; t++) {
            int target = t == 1 ? surfaceY - 6 : (t <= 4 ? TIER_TARGET_Y[t] : surfaceY - prev - 6);
            int n = landingAtOrBelow(Math.max(prev + 6, surfaceY - target));
            if (surfaceY - n < MIN_Y) {
                break;
            }
            out.add(n);
            prev = n;
        }
        return out;
    }

    /**
     * Once the shaft is at the bottom, more branch mines fit between levels that are far apart: one in the middle
     * of every gap of 12 or more blocks, deepest first, until no such gap remains. The spiral already passes
     * these depths, so they need no digging down. PROPOSED.
     */
    static List<Integer> infillLandings(List<Integer> regular) {
        List<Integer> all = new ArrayList<>(regular);
        List<Integer> added = new ArrayList<>();
        boolean changed = true;
        while (changed && added.size() < 32) {
            changed = false;
            all.sort(null);
            for (int i = all.size() - 1; i > 0; i--) {
                int upper = all.get(i - 1), lower = all.get(i);
                if (lower - upper < 12) {
                    continue;
                }
                int mid = landingAtOrBelow((upper + lower) / 2);
                if (mid - upper >= 6 && lower - mid >= 6) {
                    all.add(mid);
                    added.add(mid);
                    changed = true;
                    break;
                }
            }
        }
        return added;
    }

    /** The first landing step (cell facing the tunnels) at or below step n. */
    private static int landingAtOrBelow(int n) {
        while (Math.floorMod(n - 1, 4) != 1 && Math.floorMod(n - 1, 4) != 2) {
            n++;
        }
        return n;
    }

    /** True if a level is dug downward (the spiral must be extended to reach it) rather than an infill level. */
    public static boolean isRegular(int surfaceY, int level) {
        return level <= regularLandings(surfaceY).size();
    }

    /** Size of a level's plan after {@code rounds} lengthenings (the same for either landing side). */
    public static int planSize(int rounds) {
        return tunnelPlan(0, rounds).size();
    }

    /** Number of regular levels (tiers 1-4 and the floors below them, not the infill levels in between). */
    public static int regularLevels(int surfaceY) {
        return regularLandings(surfaceY).size();
    }

    private static final java.util.Map<Integer, List<Cell>> PLANS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cached, read-only {@link #tunnelPlan(int, int)}: plans are rebuilt often and only depend on these two numbers. */
    public static List<Cell> cachedPlan(int landingSide, int rounds) {
        return PLANS.computeIfAbsent(landingSide * 64 + rounds, k -> java.util.Collections.unmodifiableList(tunnelPlan(landingSide, rounds)));
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
