package com.factionclash.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure shape check of a tree before a woodcutter fells it (no Minecraft classes, unit-tested). From the stump it
 * follows the logs of the stump's own kind, the way the old flood did (sideways, diagonally and one up at a time),
 * and decides whether this is a natural tree the woodcutter can take down whole: no built block or log of another
 * kind touching it, no log higher than it can reach from the ground, not so big that the flood had to stop short of
 * it, at least a few logs, natural leaves near the top and soil under the stump. Anything else is left standing before
 * the first cut, so no floating logs remain. It also says where the woodcutter stands to cut a log at the end
 * ({@link #fromStumpColumn}): in the cut stump's column, or right under a log that sticks out too far sideways.
 */
public final class TreeShape {
    /** What a block is, as far as felling a tree goes. SAME_LOG is the stump's exact block, in any axis. */
    public enum Kind { AIR, SAME_LOG, OTHER_LOG, NATURAL_LEAVES, PERSISTENT_LEAVES, GROUND, STRUCTURE, OTHER }

    /**
     * The survey's answer. TOO_BIG: the same log runs on past the flood's bounds (7 sideways, 24 up or 96 logs), as in
     * a square of dark oaks grown into each other, so only part of it was seen and none of it is cut.
     */
    public enum Verdict { OK, TOO_SMALL, NOT_NATURAL, TOUCHES_STRUCTURE, TOO_TALL, TOO_BIG }

    /** The world as seen by the survey. */
    @FunctionalInterface
    public interface Cells {
        Kind at(int x, int y, int z);
    }

    /**
     * The result: the verdict, every log found (absolute {x, y, z}, highest first), whether the stump is one corner
     * of a 2x2 trunk, and how far above the stump the highest log is.
     */
    public record Tree(Verdict verdict, List<int[]> logs, boolean big, int topDy) {
        public boolean ok() {
            return verdict == Verdict.OK;
        }
    }

    /** PROPOSED: the highest log a woodcutter can still reach from the stump column (eyes 1.62 up, reach 7.5). */
    public static final int MAX_FELL_DY = 8;
    /** PROPOSED: how far a woodcutter reaches for a log (eyes to the log's centre), its eye height, and how far from a
     * column's centre the walk there may stop. */
    public static final double REACH = 7.5, EYE = 1.62, STOP = 0.45;
    /** PROPOSED: fewer logs than this is not a tree (a stump, a fence-post log). */
    public static final int MIN_LOGS = 3;
    /** Flood bounds, as before: 7 blocks sideways, 24 up, 96 logs. The same log going on past them makes the tree TOO_BIG. */
    public static final int MAX_SPREAD = 7, MAX_RISE = 24, MAX_LOGS = 96;
    /** Natural leaves must lie within this many blocks sideways of the top log (from 1 below to 2 above it). */
    public static final int LEAF_RADIUS = 2;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private TreeShape() {
    }

    /**
     * Surveys the tree whose stump is at (x, y, z). With {@code resume} (a tree this woodcutter has started to fell)
     * the size, leaf and soil rules are skipped, since the top is already gone; the bounds, contact and height rules
     * stay.
     */
    public static Tree survey(Cells cells, int x, int y, int z, boolean resume) {
        List<int[]> logs = new ArrayList<>();
        if (cells.at(x, y, z) != Kind.SAME_LOG) {
            return new Tree(resume ? Verdict.OK : Verdict.TOO_SMALL, logs, false, 0);
        }
        Deque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{x, y, z});
        // the same log beyond the bounds, or still waiting when the count ran out: part of the wood was never seen
        boolean cutShort = false;
        while (!queue.isEmpty() && !cutShort) {
            int[] p = queue.poll();
            if (!seen.add(key(p[0] - x, p[1] - y, p[2] - z)) || cells.at(p[0], p[1], p[2]) != Kind.SAME_LOG) {
                continue;
            }
            if (Math.abs(p[0] - x) > MAX_SPREAD || Math.abs(p[2] - z) > MAX_SPREAD || p[1] < y || p[1] > y + MAX_RISE
                    || logs.size() >= MAX_LOGS) {
                cutShort = true;
                continue;
            }
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            queue.add(new int[]{p[0] + dx, p[1] + dy, p[2] + dz});
                        }
                    }
                }
            }
        }
        logs.sort(Comparator.comparingInt((int[] p) -> p[1]).reversed());
        int topDy = logs.get(0)[1] - y;
        boolean big = twoByTwoCorner(cells, x, y, z) != null;
        if (cutShort) {
            // cutting what was seen would leave the rest hanging (a neighbour's top over its grounded stub)
            return new Tree(Verdict.TOO_BIG, logs, big, topDy);
        }
        for (int[] p : logs) {
            for (int[] f : FACES) {
                Kind k = cells.at(p[0] + f[0], p[1] + f[1], p[2] + f[2]);
                if (k == Kind.STRUCTURE || k == Kind.OTHER_LOG) {
                    return new Tree(Verdict.TOUCHES_STRUCTURE, logs, big, topDy);
                }
            }
        }
        if (topDy > MAX_FELL_DY) {
            return new Tree(Verdict.TOO_TALL, logs, big, topDy);
        }
        if (!resume) {
            if (logs.size() < MIN_LOGS) {
                return new Tree(Verdict.TOO_SMALL, logs, big, topDy);
            }
            int[] top = logs.get(0);
            if (!naturalLeavesNear(cells, top[0], top[1], top[2]) || cells.at(x, y - 1, z) != Kind.GROUND) {
                return new Tree(Verdict.NOT_NATURAL, logs, big, topDy);
            }
        }
        return new Tree(Verdict.OK, logs, big, topDy);
    }

    /**
     * True when a log at (dx, dy, dz) from the stump (its centre against the stump column's) is in reach of a woodcutter
     * standing in the cut stump's column, allowing for a walk that stops up to {@link #STOP} from the column's centre.
     * Logs straight above are in reach up to {@link #MAX_FELL_DY}; a log far out on a branch (a cherry's tip five out
     * and seven up, an acacia's crown) is not, and is cut standing right under it instead ({@link #fromBelow}).
     */
    public static boolean fromStumpColumn(int dx, int dy, int dz) {
        double v = dy + 0.5 - EYE;
        return Math.sqrt(dx * dx + dz * dz + v * v) + STOP <= REACH;
    }

    /**
     * True when a log {@code dy} above the stump's level is in reach of a woodcutter standing on that level right
     * under it (its walk there stopping within 1 block of the log's column): every log up to {@link #MAX_FELL_DY}.
     */
    public static boolean fromBelow(int dy) {
        double v = dy + 0.5 - EYE;
        return Math.sqrt(1.0 + v * v) <= REACH;
    }

    /**
     * The north-west corner (smallest x and z) of a 2x2 trunk the stump at (x, y, z) belongs to, as {x, z}: one of the
     * four 2x2 squares containing it has the stump's log at this height in all four columns, each on soil. Null for
     * an ordinary 1x1 trunk.
     */
    public static int[] twoByTwoCorner(Cells cells, int x, int y, int z) {
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                boolean all = true;
                for (int i = 0; i <= 1 && all; i++) {
                    for (int j = 0; j <= 1 && all; j++) {
                        int cx = x + ox + i, cz = z + oz + j;
                        all = cells.at(cx, y, cz) == Kind.SAME_LOG && cells.at(cx, y - 1, cz) == Kind.GROUND;
                    }
                }
                if (all) {
                    return new int[]{x + ox, z + oz};
                }
            }
        }
        return null;
    }

    /** Non-persistent leaves around the top log: within {@link #LEAF_RADIUS} sideways, from 1 below to 2 above. */
    public static boolean naturalLeavesNear(Cells cells, int x, int y, int z) {
        for (int dx = -LEAF_RADIUS; dx <= LEAF_RADIUS; dx++) {
            for (int dz = -LEAF_RADIUS; dz <= LEAF_RADIUS; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (cells.at(x + dx, y + dy, z + dz) == Kind.NATURAL_LEAVES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
    }
}
