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
 * kind touching it, no log higher than it can reach (from the ground, or from a scaffold pillar when it carries the
 * blocks for one), not so big that the flood had to stop short of it, at least a few logs, natural leaves near the
 * top and soil under the stump. Anything else is left standing before the first cut, so no floating logs remain. It
 * also says where the woodcutter stands to cut a log at the end ({@link #fromStumpColumn}): in the cut stump's
 * column, or right under a log that sticks out too far sideways; and, for a tree too tall for that, which column
 * beside the trunk it builds its scaffold pillar in and how high ({@link #pillarSpot}), so that every log comes
 * within reach on the way down.
 */
public final class TreeShape {
    /**
     * What a block is, as far as felling a tree goes. SAME_LOG is the stump's exact block, in any axis. KEPT_LOG is
     * that block where the woodcutter may not fell it (wood by the walls, in another faction's land, by a known
     * village): a tree reaching into it, even diagonally, is left whole, or that log would be left hanging.
     */
    public enum Kind { AIR, SAME_LOG, OTHER_LOG, NATURAL_LEAVES, PERSISTENT_LEAVES, GROUND, STRUCTURE, OTHER, KEPT_LOG }

    /**
     * The survey's answer. TOO_BIG: the same log runs on past the flood's bounds (7 sideways, 32 up or 192 logs), as in
     * a square of dark oaks grown into each other, so only part of it was seen and none of it is cut. TOO_TALL: a log
     * higher than the woodcutter can reach this time (8 above the stump from the ground, 32 from a scaffold).
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
    /**
     * Flood bounds: 7 blocks sideways, 32 up, 192 logs (a giant jungle tree is up to 31 tall with about 150 logs, a mega
     * spruce up to 29 with about 115). The same log going on past them makes the tree TOO_BIG.
     */
    public static final int MAX_SPREAD = 7, MAX_RISE = 32, MAX_LOGS = 192;
    /**
     * PROPOSED: the highest log of a tree felled from a scaffold pillar, above the stump (mega spruces reach 28, giant
     * jungle trees 30).
     */
    public static final int MAX_TALL_DY = MAX_RISE;
    /** PROPOSED: the tallest scaffold pillar (blocks); a top log 32 up needs at most 25 from any column beside the trunk. */
    public static final int MAX_PILLAR = 28;
    /** PROPOSED: scaffold blocks carried beyond the pillar's height, to mend it if a block below goes missing. */
    public static final int PILLAR_SPARE = 2;
    /**
     * PROPOSED: the reach counted on from the pillar (eyes to a block's centre): {@link #REACH} less a quarter block,
     * since the woodcutter may stand that far from its column's centre.
     */
    public static final double PILLAR_REACH = REACH - 0.25;
    /** Natural leaves must lie within this many blocks sideways of the top log (from 1 below to 2 above it). */
    public static final int LEAF_RADIUS = 2;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private TreeShape() {
    }

    /**
     * Surveys the tree whose stump is at (x, y, z) for a woodcutter felling from the ground: no log more than
     * {@link #MAX_FELL_DY} up. See {@link #survey(Cells, int, int, int, boolean, int)}.
     */
    public static Tree survey(Cells cells, int x, int y, int z, boolean resume) {
        return survey(cells, x, y, z, resume, MAX_FELL_DY);
    }

    /**
     * Surveys the tree whose stump is at (x, y, z), for a woodcutter that reaches logs up to {@code maxDy} above the
     * stump ({@link #MAX_FELL_DY} from the ground, up to {@link #MAX_TALL_DY} with blocks for a scaffold). With
     * {@code resume} (a tree this woodcutter has started to fell) the size, leaf and soil rules are skipped, since the
     * top is already gone; the bounds, contact and height rules stay.
     */
    public static Tree survey(Cells cells, int x, int y, int z, boolean resume, int maxDy) {
        List<int[]> logs = new ArrayList<>();
        if (cells.at(x, y, z) != Kind.SAME_LOG) {
            return new Tree(resume ? Verdict.OK : Verdict.TOO_SMALL, logs, false, 0);
        }
        Deque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{x, y, z});
        // the same log beyond the bounds, or still waiting when the count ran out: part of the wood was never seen
        boolean cutShort = false;
        // the tree's own wood where the flood would go on but may not (a giant jungle tree's branch end running
        // diagonally into the margin by the walls): felling the rest would leave that log hanging, so the tree counts
        // as touching a structure (the face rule below misses a branch's diagonal steps)
        boolean touches = false;
        while (!queue.isEmpty() && !cutShort) {
            int[] p = queue.poll();
            if (!seen.add(key(p[0] - x, p[1] - y, p[2] - z))) {
                continue;
            }
            Kind kind = cells.at(p[0], p[1], p[2]);
            if (kind != Kind.SAME_LOG) {
                touches |= kind == Kind.KEPT_LOG;
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
        if (touches) {
            return new Tree(Verdict.TOUCHES_STRUCTURE, logs, big, topDy);
        }
        for (int[] p : logs) {
            for (int[] f : FACES) {
                Kind k = cells.at(p[0] + f[0], p[1] + f[1], p[2] + f[2]);
                if (k == Kind.STRUCTURE || k == Kind.OTHER_LOG || k == Kind.KEPT_LOG) {
                    return new Tree(Verdict.TOUCHES_STRUCTURE, logs, big, topDy);
                }
            }
        }
        if (topDy > maxDy) {
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
     * True when a log {@code dy} above the stump (and dx, dz from it) is cut from the ground: up to
     * {@link #MAX_FELL_DY}, from the stump's column or right under it ({@link #fromStumpColumn}, {@link #fromBelow}).
     * Every log of a tree no taller than that is; anything else needs a scaffold.
     */
    public static boolean fromGround(int dx, int dy, int dz) {
        return dy <= MAX_FELL_DY && (fromStumpColumn(dx, dy, dz) || fromBelow(dy));
    }

    /** Where a scaffold pillar stands and how high it is built (blocks; the woodcutter's feet end up that far up). */
    public record Pillar(int x, int z, int height) {
    }

    /** The world beside a trunk, as the pillar choice sees it. */
    @FunctionalInterface
    public interface Room {
        /**
         * The highest pillar that can be built in column (x, z) from the stump's level: firm ground under it and the
         * cells above clear (air, plants, natural leaves or the tree's own logs, which are cut on the way up) for its
         * height plus two (the woodcutter's body and its jump). Negative where no pillar may stand.
         */
        int at(int x, int z);
    }

    /**
     * The pillar heights (blocks under the woodcutter's feet) from which the centre of the log at (lx, ly, lz) is
     * within {@link #PILLAR_REACH} of the eyes, standing in column (cx, cz) on a pillar rising from level {@code sy}:
     * {lowest, highest}, or null when it is never in reach from that column.
     */
    public static int[] standRange(int cx, int cz, int sy, int lx, int ly, int lz) {
        double d2 = (double) (lx - cx) * (lx - cx) + (double) (lz - cz) * (lz - cz);
        double r2 = PILLAR_REACH * PILLAR_REACH;
        if (d2 > r2) {
            return null;
        }
        double w = Math.sqrt(r2 - d2), mid = ly - sy + 0.5 - EYE;
        int lo = (int) Math.ceil(mid - w), hi = (int) Math.floor(mid + w);
        return lo <= hi ? new int[]{lo, hi} : null;
    }

    /** The columns beside a trunk where a pillar may stand: the 8 around a 1x1 trunk, the 12 around a 2x2 one. */
    public static List<int[]> besideTrunk(int sx, int sz, boolean big) {
        int size = big ? 2 : 1;
        List<int[]> out = new ArrayList<>();
        for (int dx = -1; dx <= size; dx++) {
            for (int dz = -1; dz <= size; dz++) {
                if (dx < 0 || dz < 0 || dx >= size || dz >= size) {
                    out.add(new int[]{sx + dx, sz + dz});
                }
            }
        }
        return out;
    }

    /**
     * Where to build the scaffold pillar for a tree too tall to fell from the ground (stump at sx, sy, sz, its logs
     * absolute {x, y, z}): among the columns beside the trunk ({@link #besideTrunk}) that {@code room} allows, one from
     * which every log is in reach at some height on the way down from the pillar's top to the ground, except the logs
     * {@link #fromGround} reaches (with {@code groundToo}), which are cut afterwards from the ground as for any tree.
     * The pillar is as low as that allows; of equal ones, the column that reaches the most logs itself wins, then one
     * straight beside the trunk rather than at a corner. Null when no column reaches them all (wide branches on every side, or no room): the tree is left standing
     * whole. With {@code groundToo} false every log must come within reach from the pillar (scaffold blocks left
     * standing are taken down this way, standing beside them).
     */
    public static Pillar pillarSpot(List<int[]> logs, int sx, int sy, int sz, boolean big, Room room, boolean groundToo) {
        Pillar best = null;
        int bestCovered = -1;
        boolean bestCorner = true;
        int size = big ? 2 : 1;
        for (int[] c : besideTrunk(sx, sz, big)) {
            boolean corner = (c[0] < sx || c[0] >= sx + size) && (c[1] < sz || c[1] >= sz + size);
            int max = Math.min(MAX_PILLAR, room.at(c[0], c[1]));
            if (max < 0) {
                continue;
            }
            int need = 0, covered = 0;
            boolean all = true;
            for (int[] p : logs) {
                int[] r = standRange(c[0], c[1], sy, p[0], p[1], p[2]);
                boolean pillar = r != null && r[1] >= 0 && r[0] <= max;
                if (pillar) {
                    covered++;
                }
                if (groundToo && fromGround(p[0] - sx, p[1] - sy, p[2] - sz)) {
                    continue;
                }
                if (!pillar) {
                    all = false;
                    break;
                }
                need = Math.max(need, Math.max(0, r[0]));
            }
            if (!all || need > max) {
                continue;
            }
            if (best == null || need < best.height() || need == best.height()
                    && (covered > bestCovered || covered == bestCovered && bestCorner && !corner)) {
                best = new Pillar(c[0], c[1], need);
                bestCovered = covered;
                bestCorner = corner;
            }
        }
        return best;
    }

    /**
     * The scaffold blocks to carry before walking to a tree whose top log is {@code topDy} above the stump: the pillar
     * the farthest column beside a 2x2 trunk would need (a diagonal corner, 3 across from the far trunk column), plus
     * {@link #PILLAR_SPARE}; 0 for a tree felled from the ground. The survey on arrival works out the real height.
     */
    public static int scaffoldNeed(int topDy) {
        if (topDy <= MAX_FELL_DY) {
            return 0;
        }
        double w = Math.sqrt(PILLAR_REACH * PILLAR_REACH - 8.0);
        return Math.max(1, (int) Math.ceil(topDy + 0.5 - EYE - w)) + PILLAR_SPARE;
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
