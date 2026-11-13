package com.factionclash.logic.hero;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla's air physics re-simulated for a hero's jumps and leaps (MOBILITY_AND_WORK 1.2, 1.6 and 3.3). Each tick a
 * living entity in the air moves by its velocity, then its vertical speed becomes {@code (v - 0.08) x 0.98} and its
 * horizontal speed {@code x 0.91} ({@code LivingEntity.travel}); a jump or a leap impulse is set on the take-off tick
 * and moves the hero in that same tick ({@code jumpFromGround} runs before {@code travel}). Collisions are resolved as
 * {@code Entity.collideWithShapes} does: the vertical move first, against the blocks under or over the hero's box where
 * it stands at the start of the tick, then the horizontal move. Mobs get almost no air control, so none is simulated.
 *
 * <p>A leap is solved on a vertical slice along its direction: column 0 is the start cell (its centre at x = 0) and
 * the target cell's centre is at x = its column. A block in the slice has a collision height (1 for a full block, 1.5
 * for a fence or a wall, 0.5 for a slab), so a fence top is a landing like any other. The solver returns the
 * lowest-speed impulse on a 0.01 grid (vertical from a plain jump's 0.42) that lands on the target cell's top within
 * 0.15 blocks of its centre while the hero's box (0.6 wide, 1.8 tall by default) clears every block along the arc; no
 * solution means no link. The jump heights of 1.2 come out of the same simulation: strength 0.42 is 1.25 blocks, 0.52
 * is 1.84, 0.62 is 2.52, 0.75 is 3.54; and the leaps of 1.6: (0.42, 0.52) for a 3-block gap, (0.50, 0.72) for 5,
 * (0.54, 0.82) for 6, (0.63, 1.00) for 8. Pure, unit-tested ({@code LeapSolverTest}).
 */
public final class LeapSolver {
    private LeapSolver() {
    }

    /** Vanilla's gravity and drags for a living entity in the air. */
    public static final double GRAVITY = 0.08, VERTICAL_DRAG = 0.98, HORIZONTAL_DRAG = 0.91;
    /** The hero lands within this distance of the target cell's centre (it is 0.6 wide, so it stands well). */
    public static final double TOLERANCE = 0.15;
    /** A plain jump's strength ({@code generic.jump_strength} 0.42 = 1.25 blocks): the least vertical impulse. */
    public static final double PLAIN_JUMP = 0.42;
    /** The impulse grid's step and the fastest horizontal impulse a leap may use (an 8-block gap needs 1.00). */
    public static final double STEP = 0.01, MAX_HORIZONTAL = 1.2;
    /** The least horizontal impulse the solver tries (a leap moves; a jump in place is no link). */
    public static final double MIN_HORIZONTAL = 0.1;
    /** A leap that has not landed after this many ticks never will (the slices are bounded). */
    public static final int MAX_TICKS = 100;
    /** The hero stands still before a leap: its speed under this, in blocks per tick. */
    public static final double STILL_SPEED = 0.05;

    private static final double EPS = 1e-9;

    /** The hero's box: half its width and its height. {@link #HERO} is the 0.6 x 1.8 of MOBILITY_AND_WORK 3.3. */
    public record Body(double halfWidth, double height) {
        public static final Body HERO = new Body(0.3, 1.8);
    }

    // ------------------------------------------------------------------ jumps (1.2)

    /** The apex (blocks above the take-off) of a straight jump with this vertical impulse. */
    public static double apex(double strength) {
        double y = 0, v = strength, best = 0;
        for (int t = 0; t < MAX_TICKS && v > 0; t++) {
            y += v;
            best = Math.max(best, y);
            v = (v - GRAVITY) * VERTICAL_DRAG;
        }
        return best;
    }

    /** The exact vertical impulse whose apex is {@code height} blocks (bisection; 0 for no height). */
    public static double strengthFor(double height) {
        if (height <= 0) {
            return 0;
        }
        double lo = 0, hi = 4;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (apex(mid) < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /**
     * The {@code generic.jump_strength} attribute a jump height needs: {@link #strengthFor} rounded up to 0.01, so
     * the hero always reaches the height (1.8 blocks: 0.52, which jumps 1.84; 2.5: 0.62; 3.5: 0.75; 1.25: 0.42).
     */
    public static double attributeFor(double height) {
        double s = strengthFor(height);
        return Math.ceil(s * 100 - 1e-6) / 100.0;
    }

    // ------------------------------------------------------------------ the slice

    /**
     * Blocks on the vertical slice along a leap: {@code column} 0 is the start cell (its centre at x = 0, spanning
     * -0.5 to 0.5), {@code y} a block's height index (feet at y = 0 stand on the block at -1). The value is the
     * block's collision height from the bottom of its cell: 0 for nothing (air, water, a flower), 1 for a full block,
     * 1.5 for a fence or a wall, 0.5 for a slab.
     */
    public interface Slice {
        double height(int column, int y);
    }

    /** A slice of full blocks and air. */
    public interface SolidSlice extends Slice {
        boolean solid(int column, int y);

        @Override
        default double height(int column, int y) {
            return solid(column, y) ? 1 : 0;
        }
    }

    /**
     * Level ground with a gap: the start cell and everything behind it with its top at 0, {@code gap} columns with no
     * floor down to {@code -depth}, then the landing cell and everything beyond it with its top at {@code rise}.
     */
    public static Slice flat(int gap, int rise, int depth) {
        return (SolidSlice) (c, y) -> {
            int top = c <= 0 ? 0 : c <= gap ? -depth : rise;
            return y < top && y >= -depth - 1;
        };
    }

    private static int column(double x) {
        return (int) Math.floor(x + 0.5);
    }

    /** Whether a block of the slice meets the vertical range [y0, y1) in a column (open at the edges). */
    private static boolean meets(Slice s, int c, double y0, double y1) {
        for (int k = (int) Math.floor(y0) - 1; k <= (int) Math.floor(y1 - EPS); k++) {
            double h = s.height(c, k);
            if (h > 0 && k < y1 - EPS && k + h > y0 + EPS) {
                return true;
            }
        }
        return false;
    }

    /** The columns the box [x - w, x + w] overlaps (open at the edges). */
    private static int firstColumn(double x, double w) {
        return column(x - w + EPS);
    }

    private static int lastColumn(double x, double w) {
        return column(x + w - EPS);
    }

    /**
     * The first column the box meets moving horizontally from {@code x} to {@code nx} at feet height {@code y}
     * ({@link Integer#MIN_VALUE}: none).
     */
    private static int firstBlocked(Slice s, double x, double nx, double y, Body b) {
        double w = b.halfWidth();
        if (nx > x) {
            for (int c = lastColumn(x, w) + 1; c <= lastColumn(nx, w); c++) {
                if (meets(s, c, y, y + b.height())) {
                    return c;
                }
            }
        } else {
            for (int c = firstColumn(x, w) - 1; c >= firstColumn(nx, w); c--) {
                if (meets(s, c, y, y + b.height())) {
                    return c;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ the flight of one impulse

    /** How a simulated arc ended. */
    public enum End { LANDED, HEAD_BUMP, HIT_WALL, FELL }

    /** A simulated arc: how it ended, where (x, feet y), after how many ticks, and its highest feet height. */
    public record Arc(End end, double x, double y, int ticks, double apex) {
        public boolean landed() {
            return end == End.LANDED;
        }
    }

    public static Arc simulate(double x, double y, double vx, double vy, Slice s, double floorY) {
        return simulate(x, y, vx, vy, s, floorY, Body.HERO);
    }

    /**
     * The arc of a hero at ({@code x}, {@code y}) with velocity ({@code vx}, {@code vy}) along the slice, until it
     * lands on a block top, bumps its head, runs into a block's side (a leap that does not clear its arc) or falls
     * below {@code floorY} (into the gap).
     */
    public static Arc simulate(double x, double y, double vx, double vy, Slice s, double floorY, Body b) {
        double top = y, w = b.halfWidth();
        for (int t = 1; t <= MAX_TICKS; t++) {
            boolean landed = false;
            // the vertical move first, at the box's x where it is
            if (vy < 0) {
                double ny = y + vy, floor = Double.NEGATIVE_INFINITY;
                for (int c = firstColumn(x, w); c <= lastColumn(x, w); c++) {
                    for (int k = (int) Math.floor(ny) - 1; k <= (int) Math.floor(y + EPS); k++) {
                        double h = s.height(c, k);
                        double blockTop = k + h;
                        if (h > 0 && blockTop <= y + EPS && blockTop > ny - EPS) {
                            floor = Math.max(floor, blockTop);
                        }
                    }
                }
                if (floor > Double.NEGATIVE_INFINITY) {
                    y = floor;
                    landed = true;
                } else {
                    y = ny;
                }
            } else if (vy > 0) {
                for (int c = firstColumn(x, w); c <= lastColumn(x, w); c++) {
                    if (meets(s, c, y + b.height(), y + b.height() + vy)) {
                        return new Arc(End.HEAD_BUMP, x, y, t, top);
                    }
                }
                y += vy;
            }
            top = Math.max(top, y);
            // then the horizontal move
            if (vx != 0) {
                double nx = x + vx;
                int wall = firstBlocked(s, x, nx, y, b);
                if (wall != Integer.MIN_VALUE) {
                    if (!landed) {
                        return new Arc(End.HIT_WALL, x, y, t, top);
                    }
                    // landed and stopped by a wall beyond: vanilla clamps the box against the wall's face
                    nx = vx > 0 ? wall - 0.5 - w : wall + 0.5 + w;
                }
                x = nx;
            }
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            vx *= HORIZONTAL_DRAG;
            if (landed) {
                return new Arc(End.LANDED, x, y, t, top);
            }
            if (y < floorY) {
                return new Arc(End.FELL, x, y, t, top);
            }
        }
        return new Arc(End.FELL, x, y, MAX_TICKS, top);
    }

    /** Whether an arc lands on the target cell's top (feet at {@code targetY}) within the tolerance of its centre. */
    public static boolean onTarget(Arc a, double targetX, double targetY) {
        return a.landed() && Math.abs(a.y() - targetY) < 1e-6 && Math.abs(a.x() - targetX) <= TOLERANCE + EPS;
    }

    /**
     * MOBILITY_AND_WORK 3.3, in the air: at the apex the rest of the arc is re-simulated from the actual state; false
     * when it no longer lands on the target (a hit, knockback, an entity in the way).
     */
    public static boolean stillLands(double x, double y, double vx, double vy, double targetX, double targetY,
                                     Slice s) {
        return onTarget(simulate(x, y, vx, vy, s, targetY - 64), targetX, targetY);
    }

    // ------------------------------------------------------------------ solving a leap (3.3)

    /**
     * The hero's current limits on a leap impulse (from its ceiling and level, 1.1): the vertical impulse of its
     * jump height, and the horizontal impulse of the slowest leap over its widest gap on level ground (so a hero
     * whose gap is 3 cannot leap 8 with a flat, fast impulse).
     */
    public record Limits(double maxVertical, double maxHorizontal) {
        /** A leap that may go as high as the hero's jump and as fast as any leap (the grid's bound). */
        public static Limits jump(double jumpHeight) {
            return new Limits(Math.max(PLAIN_JUMP, attributeFor(jumpHeight)), MAX_HORIZONTAL);
        }

        /**
         * The limits of a hero with this widest gap (whole blocks) and jump height: no leap at all under a 2-block gap
         * (a horizontal limit of 0).
         */
        public static Limits forGap(int gap, double jumpHeight) {
            double v = Math.max(PLAIN_JUMP, attributeFor(jumpHeight));
            long key = ((long) gap << 32) ^ Math.round(v * 100);
            return LIMITS.computeIfAbsent(key, k -> {
                if (gap < 2) {
                    return new Limits(v, 0);
                }
                Leap l = solveGap(gap, 0, new Limits(v, MAX_HORIZONTAL), flat(gap, 0, 10));
                return new Limits(v, l == null ? 0 : l.horizontal());
            });
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, Limits> LIMITS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A solved leap: the impulse, its ticks in the air, apex above the take-off, landing x. */
    public record Leap(double vertical, double horizontal, int ticks, double apex, double landingX) {
        public double speed() {
            return Math.hypot(vertical, horizontal);
        }
    }

    private static double grid(double v) {
        return Math.round(v / STEP) * STEP;
    }

    public static Leap solve(double startX, double startY, double targetX, double targetY, Limits lim, Slice s) {
        return solve(startX, startY, targetX, targetY, lim, s, Body.HERO);
    }

    /**
     * The lowest-speed impulse within the limits that takes the hero from ({@code startX}, {@code startY}) to the
     * centre of the cell at {@code targetX} with its feet at {@code targetY} on the slice, or null when none does.
     * {@code startX} need not be a cell centre: an acrobat's running take-off is re-solved from where it is.
     */
    public static Leap solve(double startX, double startY, double targetX, double targetY, Limits lim, Slice s,
                             Body b) {
        if (targetX <= startX) {
            return null;
        }
        Leap best = null;
        double floorY = Math.min(startY, targetY) - 64;
        for (double vy = PLAIN_JUMP; vy <= lim.maxVertical() + EPS; vy = grid(vy + STEP)) {
            if (best != null && vy >= best.speed() - EPS) {
                break; // any impulse from here on is faster
            }
            for (double vx = MIN_HORIZONTAL; vx <= lim.maxHorizontal() + EPS; vx = grid(vx + STEP)) {
                if (best != null && Math.hypot(vx, vy) >= best.speed() - EPS) {
                    break;
                }
                Arc a = simulate(startX, startY, vx, vy, s, floorY, b);
                if (onTarget(a, targetX, targetY)) {
                    best = new Leap(vy, vx, a.ticks(), a.apex() - startY, a.x());
                    break;
                }
                if (a.landed() && a.x() > targetX + TOLERANCE + 1) {
                    break; // faster only lands farther
                }
            }
        }
        return best;
    }

    /** A gap leap from the near cell's centre over {@code gap} cells to the cell beyond, its top {@code rise} higher. */
    public static Leap solveGap(int gap, double rise, Limits lim, Slice s) {
        return solve(0, 0, gap + 1, rise, lim, s);
    }

    /**
     * MOBILITY_AND_WORK 3.3, in the air: an acrobat whose arc no longer lands spends its air jump (vertical speed set
     * to {@code airJump}) with a new horizontal speed within the limit; the horizontal speed that lands closest to the
     * centre, or NaN when none lands (any other hero lets the fall happen and its path is rebuilt where it lands).
     */
    public static double airJumpCorrection(double x, double y, double airJump, double targetX, double targetY,
                                           Limits lim, Slice s) {
        double bestVx = Double.NaN, bestErr = Double.POSITIVE_INFINITY;
        for (double vx = 0; vx <= lim.maxHorizontal() + EPS; vx = grid(vx + STEP)) {
            Arc a = simulate(x, y, vx, airJump, s, targetY - 64);
            if (onTarget(a, targetX, targetY)) {
                double err = Math.abs(a.x() - targetX);
                if (err < bestErr - EPS) {
                    bestErr = err;
                    bestVx = vx;
                }
            }
        }
        return bestVx;
    }

    // ------------------------------------------------------------------ take-off (3.3)

    /**
     * What the hero stands on and is under at the start cell: its speed (blocks per tick), the block's jump and speed
     * factors (honey 0.5 and 0.4, soul sand speed 0.4), whether it is in water, powder snow or a cobweb, and whether
     * it has Slowness, Levitation or Slow Falling (each changes the arc).
     */
    public record TakeOff(double speed, double jumpFactor, double speedFactor, boolean inWater,
                          boolean inPowderSnow, boolean inCobweb, boolean slowness, boolean levitation,
                          boolean slowFalling) {
        public static final TakeOff STILL = new TakeOff(0, 1, 1, false, false, false, false, false, false);
    }

    /**
     * Why a link may not start from here (the hero walks instead), or null when it may: the hero must stand still
     * (except an acrobat's running take-off, which is re-solved from its actual position and velocity), on a block with
     * full jump and speed factors, out of water, powder snow and cobwebs, without Slowness, Levitation or Slow Falling.
     */
    public static String refusal(TakeOff t, boolean runningTakeOff) {
        if (t.inWater()) {
            return "in water";
        }
        if (t.inPowderSnow()) {
            return "in powder snow";
        }
        if (t.inCobweb()) {
            return "in a cobweb";
        }
        if (t.jumpFactor() < 1 - EPS) {
            return "on a block that holds a jump back (honey)";
        }
        if (t.speedFactor() < 1 - EPS) {
            return "on a block that slows (soul sand)";
        }
        if (t.slowness() || t.levitation() || t.slowFalling()) {
            return "under an effect that changes the arc (Slowness, Levitation or Slow Falling)";
        }
        if (!runningTakeOff && t.speed() >= STILL_SPEED) {
            return "still moving";
        }
        return null;
    }

    // ------------------------------------------------------------------ the table (3.9)

    /**
     * The precomputed table of 3.9: for every gap 2..{@code maxGap} and every rise in {@code minRise..maxRise} on
     * level ground, the lowest-speed leap with no limit but the grid's. A hero's current limits pick from it; when
     * the table's leap is over them, {@link #lookup} solves within them (and finds none when no slower leap lands).
     */
    public static final class Table {
        private final Map<Long, Leap> leaps = new HashMap<>();
        private final int maxGap, minRise, maxRise;

        public Table(int maxGap, int minRise, int maxRise) {
            this.maxGap = maxGap;
            this.minRise = minRise;
            this.maxRise = maxRise;
            Limits any = new Limits(strengthFor(maxGap / 2.0 + 2), MAX_HORIZONTAL);
            for (int g = 2; g <= maxGap; g++) {
                for (int r = minRise; r <= maxRise; r++) {
                    Leap l = solveGap(g, r, any, flat(g, r, 10));
                    if (l != null) {
                        leaps.put(key(g, r), l);
                    }
                }
            }
        }

        private static long key(int gap, int rise) {
            return ((long) gap << 32) ^ (rise & 0xffffffffL);
        }

        /** The table's leap for a gap and a rise on level ground (null: none, or outside the table). */
        public Leap get(int gap, int rise) {
            return leaps.get(key(gap, rise));
        }

        public int size() {
            return leaps.size();
        }

        /**
         * The leap a hero with these limits takes over this gap and rise on level ground (null: none). The table's
         * leap is the slowest that lands, so when it is within the limits it is the answer; when it is not, no leap
         * within lower limits is slower, but one with a lower vertical and a faster horizontal may be, so the solver
         * runs within the limits.
         */
        public Leap lookup(int gap, int rise, Limits lim) {
            Leap l = gap <= maxGap && rise >= minRise && rise <= maxRise ? get(gap, rise) : null;
            if (l != null && l.vertical() <= lim.maxVertical() + EPS && l.horizontal() <= lim.maxHorizontal() + EPS) {
                return l;
            }
            return solveGap(gap, rise, lim, flat(gap, rise, 10));
        }
    }
}
