package com.factionclash.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * How a fighter gets within striking distance of a target it cannot simply walk to, the way a player would: walk, step
 * up a block, drop down, break a block in the way (one it may break, and only as long as that takes with what it
 * holds), build itself up a block or two with blocks it carries, or break the block under a target standing too high
 * to hit. A cheapest-first search over a small box around both, in rough ticks of work; null when there is no way
 * worth the time, in which case the fighter keeps out of reach and watches.
 * <p>
 * Cells are block positions; an entity stands in a cell (its feet) and the one above (its head), on top of the
 * block below. Melee lands from a cell next to the target's column (diagonals too) at most one block above or below
 * it, as a mob's hit box reaches sideways but not up. Pure logic: the {@link World} answers what fills a cell.
 */
public final class ReachPlanner {
    /** {@link World#cell}: nothing in the way (air, a plant, a door or gate it can open). */
    public static final int OPEN = 0;
    /** {@link World#cell}: a block it must not or cannot break. Any other value is the ticks it takes to break. */
    public static final int FIXED = -1;
    /** PROPOSED: rough ticks to walk a block, step up one, and jump up placing a block underfoot. */
    static final int WALK = 5, CLIMB = 8, PILLAR = 12;
    /** PROPOSED: how far around the fighter and the target the search looks, and how far down it drops at most. */
    public static final int MARGIN = 4, MAX_DROP = 3;
    /**
     * A fighter breaking the block under the target must be within a player's block reach of it (4.5 from the eyes);
     * planned half a block short of that, as it may stand anywhere in its cell, not only in the middle.
     */
    static final double BLOCK_REACH = 4.0;

    private ReachPlanner() {
    }

    /** What the world holds. Coordinates are block positions. */
    public interface World {
        /** {@link #OPEN}, {@link #FIXED}, or the ticks it takes this fighter to break the block there. */
        int cell(int x, int y, int z);

        /** One can stand on top of the block at (x, y, z): a solid top no higher than a full block. */
        boolean floor(int x, int y, int z);

        /** A block from the backpack can be placed at (x, y, z) (it is empty: air, grass, no door). */
        boolean placeable(int x, int y, int z);
    }

    public record Cell(int x, int y, int z) {
        Cell add(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }
    }

    /**
     * One move from the cell the fighter stands in to the next: the blocks it breaks first (top down), and whether it
     * places a block underfoot to rise one ({@code pillar}). A last step with {@code from == to} only breaks the block
     * under the target.
     */
    public record Step(Cell from, Cell to, List<Cell> breaks, boolean pillar) {
        public boolean work() {
            return pillar || !breaks.isEmpty();
        }
    }

    /** The moves, cheapest first found, and their rough cost in ticks; {@code undermine} when the last step breaks the block under the target. */
    public record Plan(List<Step> steps, int cost, boolean undermine) {
        /** Index of the first step that needs work (breaking or building), or -1 if it is all walking. */
        public int firstWork() {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).work()) {
                    return i;
                }
            }
            return -1;
        }

        /** Blocks placed on the way (each pillar step uses one). */
        public int blocksUsed() {
            return (int) steps.stream().filter(Step::pillar).count();
        }
    }

    /** Whether a fighter standing in {@code c} can hit a target standing in {@code t} (both feet cells). */
    public static boolean canStrike(World w, Cell c, Cell t) {
        int dx = Math.abs(c.x() - t.x()), dz = Math.abs(c.z() - t.z()), dy = Math.abs(c.y() - t.y());
        if (dx > 1 || dz > 1 || dx + dz == 0 || dy > 1) {
            return false;
        }
        if (dx == 1 && dz == 1) {
            // across a corner: something open beside it at eye level, or the corner hides the target
            int eye = Math.max(c.y(), t.y()) + 1;
            return w.cell(t.x(), eye, c.z()) == OPEN || w.cell(c.x(), eye, t.z()) == OPEN;
        }
        return true;
    }

    /**
     * The cheapest plan from {@code start} to a cell from which the fighter can strike {@code target}, or to break the
     * block under it (when {@code undermine}); at most {@code pillars} blocks placed and {@code maxCost} ticks of work.
     * An empty plan when it can strike from where it stands; null when there is none.
     */
    public static Plan plan(World w, Cell start, Cell target, int pillars, boolean undermine, int maxCost) {
        return new Search(w, start, target, pillars, undermine, maxCost).run();
    }

    private static final class Search {
        final World w;
        final Cell start, target;
        final int pillars;
        final boolean undermine;
        final int maxCost;
        final int x0, y0, z0, sx, sy, sz;
        final int layers;
        final int[] dist;
        final Step[] via;
        final int[] parent;
        /** Cache of {@link World#cell}, offset by one below and two above the search box. */
        final int[] cells;
        final byte[] floors;
        final int cy0, csy;
        /** Cost to break the block under the target (0: not breakable or not asked). */
        final int underCost;

        Search(World w, Cell start, Cell target, int pillars, boolean undermine, int maxCost) {
            this.w = w;
            this.start = start;
            this.target = target;
            this.pillars = Math.max(0, pillars);
            this.maxCost = maxCost;
            x0 = Math.min(start.x(), target.x()) - MARGIN;
            z0 = Math.min(start.z(), target.z()) - MARGIN;
            y0 = Math.min(start.y(), target.y()) - MAX_DROP;
            sx = Math.max(start.x(), target.x()) + MARGIN - x0 + 1;
            sz = Math.max(start.z(), target.z()) + MARGIN - z0 + 1;
            sy = Math.max(start.y(), target.y()) + 2 - y0 + 1;
            layers = this.pillars + 1;
            int n = sx * sy * sz * layers;
            dist = new int[n];
            Arrays.fill(dist, Integer.MAX_VALUE);
            via = new Step[n];
            parent = new int[n];
            cy0 = y0 - 1;
            csy = sy + 3;
            cells = new int[sx * csy * sz];
            Arrays.fill(cells, Integer.MIN_VALUE);
            floors = new byte[sx * csy * sz];
            int u = undermine && target.y() - 1 > start.y() - MAX_DROP ? w.cell(target.x(), target.y() - 1, target.z()) : FIXED;
            this.undermine = undermine && u > 0;
            underCost = this.undermine ? u : 0;
        }

        boolean inside(int x, int y, int z) {
            return x >= x0 && x < x0 + sx && y >= y0 && y < y0 + sy && z >= z0 && z < z0 + sz;
        }

        int state(Cell c, int used) {
            return (((c.x() - x0) * sy + (c.y() - y0)) * sz + (c.z() - z0)) * layers + used;
        }

        Cell cellOf(int state) {
            int s = state / layers;
            int z = s % sz;
            s /= sz;
            int y = s % sy;
            int x = s / sy;
            return new Cell(x + x0, y + y0, z + z0);
        }

        int cache(int x, int y, int z) {
            if (x < x0 || x >= x0 + sx || z < z0 || z >= z0 + sz || y < cy0 || y >= cy0 + csy) {
                return -1;
            }
            return ((x - x0) * csy + (y - cy0)) * sz + (z - z0);
        }

        int cell(int x, int y, int z) {
            int i = cache(x, y, z);
            if (i < 0) {
                return w.cell(x, y, z);
            }
            if (cells[i] == Integer.MIN_VALUE) {
                cells[i] = w.cell(x, y, z);
            }
            return cells[i];
        }

        boolean floor(int x, int y, int z) {
            int i = cache(x, y, z);
            if (i < 0) {
                return w.floor(x, y, z);
            }
            if (floors[i] == 0) {
                floors[i] = (byte) (w.floor(x, y, z) ? 1 : 2);
            }
            return floors[i] == 1;
        }

        /** Cost to clear these cells (breaking what is in them), appending them to {@code out}; -1 if one may not be. */
        int clear(List<Cell> out, Cell... cs) {
            int cost = 0;
            for (Cell c : cs) {
                int v = cell(c.x(), c.y(), c.z());
                if (v == FIXED) {
                    return -1;
                }
                if (v > 0) {
                    cost += v;
                    out.add(c);
                }
            }
            return cost;
        }

        boolean undermineFrom(Cell c) {
            if (!undermine || c.x() == target.x() && c.z() == target.z() || target.y() - c.y() < 2) {
                return false;
            }
            double ex = c.x() + 0.5 - (target.x() + 0.5), ey = c.y() + 1.62 - (target.y() - 0.5), ez = c.z() + 0.5 - (target.z() + 0.5);
            return ex * ex + ey * ey + ez * ez <= BLOCK_REACH * BLOCK_REACH;
        }

        Plan run() {
            if (!inside(start.x(), start.y(), start.z())) {
                return null;
            }
            if (canStrike(w, start, target)) {
                return new Plan(List.of(), 0, false);
            }
            PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
            int s0 = state(start, 0);
            dist[s0] = 0;
            open.add(new long[]{0, s0});
            int bestCost = Integer.MAX_VALUE, bestState = -1;
            boolean bestUnder = false;
            while (!open.isEmpty()) {
                long[] top = open.poll();
                int d = (int) top[0], s = (int) top[1];
                if (d != dist[s]) {
                    continue;
                }
                if (d >= bestCost) {
                    break;
                }
                Cell c = cellOf(s);
                if (canStrike(w, c, target)) {
                    bestCost = d;
                    bestState = s;
                    bestUnder = false;
                    break;
                }
                if (undermineFrom(c) && d + underCost + WALK < bestCost) {
                    bestCost = d + underCost + WALK;
                    bestState = s;
                    bestUnder = true;
                }
                expand(open, s, c, d, s % layers);
            }
            if (bestState < 0 || bestCost > maxCost) {
                return null;
            }
            List<Step> steps = new ArrayList<>();
            if (bestUnder) {
                Cell c = cellOf(bestState);
                steps.add(new Step(c, c, List.of(new Cell(target.x(), target.y() - 1, target.z())), false));
            }
            for (int s = bestState; s != s0; s = parent[s]) {
                steps.add(via[s]);
            }
            Collections.reverse(steps);
            return new Plan(steps, bestCost, bestUnder);
        }

        void relax(PriorityQueue<long[]> open, int from, int d, Step step, int used, int cost) {
            Cell to = step.to();
            if (cost < 0 || !inside(to.x(), to.y(), to.z())) {
                return;
            }
            int nd = d + cost;
            if (nd > maxCost) {
                return;
            }
            int s = state(to, used);
            if (nd < dist[s]) {
                dist[s] = nd;
                via[s] = step;
                parent[s] = from;
                open.add(new long[]{nd, s});
            }
        }

        void expand(PriorityQueue<long[]> open, int s, Cell p, int d, int used) {
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dir : dirs) {
                Cell n = p.add(dir[0], 0, dir[1]);
                // level: the neighbour's feet and head cells cleared, standing on what is below
                if (floor(n.x(), n.y() - 1, n.z())) {
                    List<Cell> br = new ArrayList<>(2);
                    int c = clear(br, n.add(0, 1, 0), n);
                    relax(open, s, d, new Step(p, n, br, false), used, c < 0 ? -1 : WALK + c);
                }
                // up a block: onto the neighbour's block, with head room above it and above the fighter
                Cell up = n.add(0, 1, 0);
                if (floor(n.x(), n.y(), n.z()) && cell(n.x(), n.y(), n.z()) != OPEN) {
                    List<Cell> br = new ArrayList<>(3);
                    int c = clear(br, p.add(0, 2, 0), up.add(0, 1, 0), up);
                    relax(open, s, d, new Step(p, up, br, false), used, c < 0 ? -1 : CLIMB + c);
                }
                // down: out over the edge and down to the first floor, at most MAX_DROP blocks
                List<Cell> col = new ArrayList<>();
                col.add(n.add(0, 1, 0));
                col.add(n);
                for (int k = 1; k <= MAX_DROP; k++) {
                    Cell q = n.add(0, -k, 0);
                    col.add(q);
                    if (floor(q.x(), q.y() - 1, q.z())) {
                        List<Cell> br = new ArrayList<>(col.size());
                        int c = clear(br, col.toArray(new Cell[0]));
                        relax(open, s, d, new Step(p, q, br, false), used, c < 0 ? -1 : WALK + k + c);
                        break;
                    }
                    if (cell(q.x(), q.y(), q.z()) == FIXED) {
                        break;
                    }
                }
            }
            // build up a block where it stands: never higher than the target, and only near it
            if (used < pillars && p.y() < target.y() && Math.max(Math.abs(p.x() - target.x()), Math.abs(p.z() - target.z())) <= 2
                    && w.placeable(p.x(), p.y(), p.z())) {
                List<Cell> br = new ArrayList<>(1);
                int c = clear(br, p.add(0, 2, 0));
                relax(open, s, d, new Step(p, p.add(0, 1, 0), br, true), used + 1, c < 0 ? -1 : PILLAR + c);
            }
        }
    }
}
