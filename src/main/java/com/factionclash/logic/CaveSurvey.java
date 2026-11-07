package com.factionclash.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules for exploring a cave on foot (no Minecraft types, so they are unit-testable). A cell is a position
 * an NPC can stand in (floor below, room for feet and head). Moves are one block sideways, stepping up or down at
 * most one block, so every route an explorer records can be walked back the same way by a miner.
 */
public final class CaveSurvey {
    /** PROPOSED: survey waypoints are at least this far apart (blocks). */
    public static final double SPACING = 7.0;
    /** PROPOSED: a new waypoint is 6..24 walking steps from the explorer's current one. */
    public static final int MIN_STEPS = 6;
    public static final int MAX_STEPS = 24;
    /** PROPOSED: the explorer turns back after this many waypoints or this far (blocks) from the entrance. */
    public static final int MAX_WAYPOINTS = 48;
    public static final int MAX_RANGE = 64;

    private CaveSurvey() {
    }

    /** What the world looks like to the survey. */
    public interface Cells {
        /** Floor below, feet and head room free. */
        boolean standable(int x, int y, int z);

        /** Nothing to bump into (air, torch, plant). */
        boolean open(int x, int y, int z);
    }

    // ------------------------------------------------------------------ packed positions (BlockPos.asLong layout)

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long p) {
        return (int) (p >> 38);
    }

    public static int y(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int z(long p) {
        return (int) (p << 26 >> 38);
    }

    public static double dist2(long a, long b) {
        double dx = x(a) - x(b), dy = y(a) - y(b), dz = z(a) - z(b);
        return dx * dx + dy * dy + dz * dz;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Breadth-first walk over standable cells from {@code start}: returns each reachable cell with its distance in
     * steps, up to {@code maxSteps} steps and {@code maxNodes} cells.
     */
    public static Map<Long, Integer> walk(long start, Cells cells, int maxSteps, int maxNodes) {
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty() && dist.size() < maxNodes) {
            long p = queue.poll();
            int d = dist.get(p);
            if (d >= maxSteps) {
                continue;
            }
            for (int[] s : SIDES) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = x(p) + s[0], ny = y(p) + dy, nz = z(p) + s[1];
                    long n = pack(nx, ny, nz);
                    if (dist.containsKey(n) || !cells.standable(nx, ny, nz)) {
                        continue;
                    }
                    // stepping up needs head room above the current cell, stepping down needs it above the lower cell
                    if (dy == 1 && !cells.open(x(p), y(p) + 2, z(p))) {
                        continue;
                    }
                    if (dy == -1 && !cells.open(nx, y(p) + 1, nz)) {
                        continue;
                    }
                    dist.put(n, d + 1);
                    queue.add(n);
                    break; // one height per column: the lowest walkable step is enough
                }
            }
        }
        return dist;
    }

    /** True if {@code p} keeps the survey spacing from every waypoint already visited. */
    public static boolean unexplored(long p, List<Long> visited, double spacing) {
        for (long v : visited) {
            if (dist2(p, v) < spacing * spacing) {
                return false;
            }
        }
        return true;
    }

    /**
     * Picks the next waypoint from a walk: the nearest (in steps) cell at least {@code minSteps} away that keeps
     * the spacing from all visited waypoints, preferring the deeper cell on ties (caves are explored downward).
     * Returns null when the walk shows nothing new.
     */
    public static Long next(Map<Long, Integer> walk, List<Long> visited, int minSteps, double spacing) {
        Long best = null;
        int bestSteps = Integer.MAX_VALUE;
        for (Map.Entry<Long, Integer> e : walk.entrySet()) {
            int d = e.getValue();
            if (d < minSteps || !unexplored(e.getKey(), visited, spacing)) {
                continue;
            }
            if (d < bestSteps || (d == bestSteps && best != null && y(e.getKey()) < y(best))) {
                best = e.getKey();
                bestSteps = d;
            }
        }
        return best;
    }

    /**
     * Frontier cells worth coming back to: cells far out in a walk (at least {@code minSteps}) that are still
     * unexplored. Sparse: no two kept frontier cells are closer than the spacing.
     */
    public static List<Long> frontier(Map<Long, Integer> walk, List<Long> visited, int minSteps, double spacing) {
        List<Long> out = new ArrayList<>();
        List<Map.Entry<Long, Integer>> far = new ArrayList<>(walk.entrySet());
        far.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<Long, Integer> e : far) {
            if (e.getValue() >= minSteps && unexplored(e.getKey(), visited, spacing) && unexplored(e.getKey(), out, spacing)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Waypoint indices from the root (index 0, the entrance) to waypoint {@code k}, following parent links. */
    public static List<Integer> route(int[] parent, int k) {
        List<Integer> out = new ArrayList<>();
        int guard = 0;
        for (int i = k; i >= 0 && guard++ <= parent.length; i = parent[i]) {
            out.add(i);
            if (i == 0) {
                break;
            }
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * Waypoints to walk through to get from waypoint {@code a} to waypoint {@code b} along the recorded tree:
     * up to their common ancestor, then down to {@code b} (excludes {@code a}, includes {@code b}).
     */
    public static List<Integer> treePath(int[] parent, int a, int b) {
        List<Integer> ra = route(parent, a), rb = route(parent, b);
        int k = 0;
        while (k < ra.size() && k < rb.size() && ra.get(k).equals(rb.get(k))) {
            k++;
        }
        List<Integer> path = new ArrayList<>();
        for (int i = ra.size() - 2; i >= k - 1 && i >= 0; i--) {
            path.add(ra.get(i));
        }
        for (int i = k; i < rb.size(); i++) {
            path.add(rb.get(i));
        }
        return path;
    }

    /** A ledge cell and a direction: one block placed at (ledge + direction, ledge y - 2) makes a step down. */
    public record Step(long ledge, int dx, int dz) {
        /** Where the step block goes. */
        public long block() {
            return pack(x(ledge) + dx, y(ledge) - 2, z(ledge) + dz);
        }

        /** The new standing cell on top of the step block. */
        public long onto() {
            return pack(x(ledge) + dx, y(ledge) - 1, z(ledge) + dz);
        }
    }

    /**
     * Finds where to build a step down a drop: a reachable ledge next to a column that is open from head height
     * down past the step position (a drop of two blocks or more) with a floor no deeper than {@code maxDepth}
     * blocks, leading somewhere unexplored. Players get down such drops by placing blocks as steps, which also
     * keeps the way back up walkable. Nearest ledge (in steps) first; null if there is none.
     */
    public static Step dropStep(Map<Long, Integer> walk, Cells cells, List<Long> visited, double spacing, int maxDepth) {
        List<Map.Entry<Long, Integer>> byDistance = new ArrayList<>(walk.entrySet());
        byDistance.sort(Map.Entry.comparingByValue());
        for (Map.Entry<Long, Integer> e : byDistance) {
            long c = e.getKey();
            int cx = x(c), cy = y(c), cz = z(c);
            for (int[] s : SIDES) {
                int nx = cx + s[0], nz = cz + s[1];
                if (!cells.open(nx, cy + 1, nz) || !cells.open(nx, cy, nz) || !cells.open(nx, cy - 1, nz) || !cells.open(nx, cy - 2, nz)) {
                    continue;
                }
                int depth = 2;
                while (depth <= maxDepth && cells.open(nx, cy - 1 - depth, nz)) {
                    depth++;
                }
                if (depth > maxDepth) {
                    continue; // no floor in sight: not somewhere to build down into
                }
                long floorCell = pack(nx, cy - depth, nz);
                if (unexplored(floorCell, visited, spacing)) {
                    return new Step(c, s[0], s[1]);
                }
            }
        }
        return null;
    }

    /** The waypoint closest to {@code target} (a marked ore), used to pick the route a miner walks. */
    public static int nearestWaypoint(long[] waypoints, long target) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < waypoints.length; i++) {
            double d = dist2(waypoints[i], target);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
