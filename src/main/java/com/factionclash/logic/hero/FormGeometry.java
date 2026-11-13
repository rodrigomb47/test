package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Direction;

/**
 * The shapes and motions of the ability forms (HERO_FORMAT.md 10.1, 13.4; HEROES_V2 6.14), as plain arithmetic the
 * game side feeds with entity positions: who is inside a cone or on a line, the fan of a volley, the next link of a
 * chain, and the impulses of a leap, a dash, a hop of the hero's own ({@code move_self}), a knockback's lift and a
 * pull. Impulses follow vanilla's air physics ({@link LeapSolver}: gravity 0.08, drag 0.98 vertical and 0.91
 * horizontal per tick), so every motion is physical: a velocity, never a teleport. Nothing here knows who a receiver
 * is. Pure, unit-tested ({@code FormGeometryTest}).
 */
public final class FormGeometry {
    private FormGeometry() {
    }

    /** A volley's projectiles fly this many degrees apart (v1 10.1). */
    public static final double VOLLEY_SPACING = 10;
    /** A knock-up lifts at most one block, whoever is lifted (HEROES_V2 6.14 rule 2). */
    public static final double KNOCK_UP_MAX = 1.0;
    /** A dash pushes the hero for 0.5 s (v1 13.4: "a 0.5 s push") and catches enemies within 1.5 blocks of its path. */
    public static final int DASH_TICKS = 10;
    public static final double DASH_REACH = 1.5;
    /** The fastest horizontal impulse a leap or a hop may use (blocks per tick; {@link LeapSolver#MAX_HORIZONTAL}). */
    public static final double MAX_HORIZONTAL = LeapSolver.MAX_HORIZONTAL;
    /** A leap's arc rises this far above the higher of its two ends (a visible arc, never a skim). */
    public static final double LEAP_CLEARANCE = 1.0;
    /** A pull moves its receiver two blocks per point of strength, never closer than 1.5 blocks to the hero. */
    public static final double PULL_BLOCKS_PER_STRENGTH = 2, PULL_STOP = 1.5;
    /** The small hop a pull gives its receiver, so the ground does not eat it (blocks per tick, up). */
    public static final double PULL_LIFT = 0.3;
    /** {@code knockback} strength 1 is vanilla's knockback of 0.4 (a sword hit), as v1 13.4 maps it. */
    public static final double KNOCKBACK_PER_STRENGTH = 0.4;
    /**
     * Vanilla's horizontal friction for a tick that starts on ordinary ground (block friction 0.6 x 0.91): a receiver
     * pushed while standing loses this much of its speed after its first tick, before the air's 0.91 takes over.
     */
    public static final double GROUND_FRICTION = 0.6 * 0.91;

    // ------------------------------------------------------------------ selection

    /**
     * Whether a point {@code (dx, dy, dz)} from the hero (its feet to the receiver's centre) lies in a cone: within
     * {@code range} plus the receiver's half width, and within half the cone's {@code angle} of the hero's horizontal
     * facing {@code (fx, fz)}. A receiver right on top of the hero (under half a block away) is inside.
     */
    public static boolean inCone(double dx, double dy, double dz, double fx, double fz, double angleDeg, double range,
                                 double halfWidth) {
        double h = Math.sqrt(dx * dx + dz * dz);
        double d = Math.sqrt(h * h + dy * dy);
        if (d > range + halfWidth || Math.abs(dy) > range) {
            return false;
        }
        if (h < 0.5) {
            return true;
        }
        double fl = Math.sqrt(fx * fx + fz * fz);
        if (fl < 1e-9) {
            return false;
        }
        double cos = (dx * fx + dz * fz) / (h * fl);
        return cos >= Math.cos(Math.toRadians(Math.max(0, Math.min(360, angleDeg)) / 2) ) - 1e-9;
    }

    /** The distance from a point to the segment a-b (a point when a and b meet). */
    public static double segmentDistance(double px, double py, double pz, double ax, double ay, double az,
                                         double bx, double by, double bz) {
        double vx = bx - ax, vy = by - ay, vz = bz - az;
        double len2 = vx * vx + vy * vy + vz * vz;
        double t = len2 < 1e-12 ? 0 : ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * vx - px, cy = ay + t * vy - py, cz = az + t * vz - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /**
     * Whether a receiver whose centre is {@code p} and whose half width is {@code halfWidth} is touched by a line of
     * {@code width} blocks from a to b ({@code line}: b is where the first solid block stops it).
     */
    public static boolean onLine(double px, double py, double pz, double halfWidth, double ax, double ay, double az,
                                 double bx, double by, double bz, double width) {
        return segmentDistance(px, py, pz, ax, ay, az, bx, by, bz) <= Math.max(0.5, width) / 2 + halfWidth;
    }

    /** The yaw offsets (degrees) of a volley's projectiles, centred on the aim and {@link #VOLLEY_SPACING} apart. */
    public static double[] fanOffsets(int count) {
        int n = Math.max(1, count);
        double[] o = new double[n];
        for (int i = 0; i < n; i++) {
            o[i] = (i - (n - 1) / 2.0) * VOLLEY_SPACING;
        }
        return o;
    }

    /**
     * A chain's next link: the index of the candidate nearest to the last link {@code (x, y, z)} within
     * {@code radius}, never one already used; -1 when none is left. {@code xs}, {@code ys}, {@code zs} are the
     * candidates' positions.
     */
    public static int chainNext(double x, double y, double z, double[] xs, double[] ys, double[] zs, boolean[] used,
                                double radius) {
        int best = -1;
        double bestD = radius * radius + 1e-9;
        for (int i = 0; i < xs.length; i++) {
            if (used[i]) {
                continue;
            }
            double dx = xs[i] - x, dy = ys[i] - y, dz = zs[i] - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ motion

    /** A take-off: horizontal speed along the aim and vertical speed (blocks per tick), and the ticks in the air. */
    public record Impulse(double horizontal, double vertical, int airTicks) {
    }

    /**
     * Ticks until feet that leave the ground with vertical speed {@code vy} come back down to {@code rise} blocks
     * (vanilla's order: move, then {@code (v - 0.08) x 0.98}); at most {@link LeapSolver#MAX_TICKS}.
     */
    public static int airTicks(double vy, double rise) {
        double y = 0, v = vy;
        for (int n = 1; n <= LeapSolver.MAX_TICKS; n++) {
            y += v;
            v = (v - LeapSolver.GRAVITY) * LeapSolver.VERTICAL_DRAG;
            if (v < 0 && y <= rise) {
                return n;
            }
        }
        return LeapSolver.MAX_TICKS;
    }

    /** The horizontal speed that covers {@code distance} in {@code ticks} of air drag (0.91 per tick). */
    public static double airSpeedFor(double distance, int ticks) {
        int n = Math.max(1, ticks);
        double sum = (1 - Math.pow(LeapSolver.HORIZONTAL_DRAG, n)) / (1 - LeapSolver.HORIZONTAL_DRAG);
        return Math.max(0, distance) / sum;
    }

    /** The horizontal distance a take-off at {@code horizontal} covers in {@code ticks} of air drag. */
    public static double airDistance(double horizontal, int ticks) {
        int n = Math.max(0, ticks);
        return horizontal * (1 - Math.pow(LeapSolver.HORIZONTAL_DRAG, n)) / (1 - LeapSolver.HORIZONTAL_DRAG);
    }

    /**
     * A leap to a spot {@code distance} blocks away and {@code rise} blocks up (negative: down): the arc rises
     * {@link #LEAP_CLEARANCE} above the higher end, and the horizontal speed brings the hero down on the spot, at most
     * {@link #MAX_HORIZONTAL} (a leap that needs more lands short, as v1 13.4 says of walls).
     */
    public static Impulse leap(double distance, double rise) {
        double apex = Math.max(rise, 0) + LEAP_CLEARANCE;
        double vy = Math.max(LeapSolver.PLAIN_JUMP, LeapSolver.strengthFor(apex));
        int n = airTicks(vy, rise);
        double vh = Math.min(MAX_HORIZONTAL, airSpeedFor(distance, n));
        return new Impulse(vh, vy, n);
    }

    /** A dash's push each tick of its {@link #DASH_TICKS}: the range spread over them (blocks per tick). */
    public static double dashSpeed(double range) {
        return Math.max(0, range) / DASH_TICKS;
    }

    /**
     * A hop of the hero's own ({@code move_self}): {@code back} and {@code forward} are a plain jump that carries it
     * {@code distance} blocks; {@code up} rises {@code distance} blocks straight up. Walls stop it (vanilla collision).
     */
    public static Impulse hop(Direction d, double distance) {
        if (d == Direction.UP) {
            double vy = LeapSolver.strengthFor(Math.max(0.5, distance));
            return new Impulse(0, vy, airTicks(vy, 0));
        }
        double vy = LeapSolver.PLAIN_JUMP;
        int n = airTicks(vy, 0);
        return new Impulse(Math.min(MAX_HORIZONTAL, airSpeedFor(distance, n)), vy, n);
    }

    /** Vanilla knockback strength for a {@code knockback} effect's strength (1-5). */
    public static double knockbackStrength(double strength) {
        return KNOCKBACK_PER_STRENGTH * Math.max(0, strength);
    }

    /**
     * The upward speed of a knockback's {@code up} (blocks), held to {@link #KNOCK_UP_MAX}: given only to a receiver
     * standing on the ground (v1 13.4), so repeated hits never juggle anyone into fall damage.
     */
    public static double liftImpulse(double upBlocks) {
        double up = Math.min(KNOCK_UP_MAX, Math.max(0, upBlocks));
        return up <= 0 ? 0 : LeapSolver.strengthFor(up);
    }

    /**
     * The horizontal distance a speed {@code horizontal} given to an entity standing on the ground covers in
     * {@code ticks} in the air: its first tick ends with ground friction, the rest with the air's.
     */
    public static double groundStartDistance(double horizontal, int ticks) {
        int n = Math.max(1, ticks);
        return horizontal * (1 + GROUND_FRICTION * airDistance(1, n - 1));
    }

    /**
     * A pull's horizontal speed toward the hero for a receiver {@code distance} blocks away, standing on the ground:
     * two blocks per point of strength, never closer than {@link #PULL_STOP}, carried by a small hop of
     * {@link #PULL_LIFT} (the receiver's first tick still ends on the ground, so its friction is counted).
     */
    public static double pullSpeed(double strength, double distance) {
        double blocks = Math.min(PULL_BLOCKS_PER_STRENGTH * Math.max(0, strength), Math.max(0, distance - PULL_STOP));
        return blocks / groundStartDistance(1, airTicks(PULL_LIFT, 0));
    }
    // ------------------------------------------------------------------ format 2's forms (HEROES_V2 6.5; task A5)

    /** A {@code charge} at 100% runs this fast (blocks per tick: 5 blocks a second, about a sprint). */
    public static final double CHARGE_BASE_SPEED = 0.25;
    /** A charge never runs longer than this (a guard against a broken file: 16 blocks at 120% take 54 ticks). */
    public static final int CHARGE_MAX_TICKS = 80;
    /** A wave's front hits a grounded receiver within this much of it (6.5: "the front is within 0.75 blocks"). */
    public static final double WAVE_BAND = 0.75;
    /** A wave reaches receivers whose feet are within this height of its centre (it rolls along the ground). */
    public static final double WAVE_HEIGHT = 1.5;
    /** A rain impact hits enemies within this much of where it lands (6.5: 1-2 blocks; F3's decal of 1.5). */
    public static final double RAIN_IMPACT_RADIUS = 1.5;
    /** A grab pulls its receiver toward the hero's hand at most this fast (blocks per tick) and holds it there. */
    public static final double GRAB_PULL_MAX = 0.8, GRAB_PULL_GAIN = 0.5;
    /** The hand a grab holds its receiver in: this far in front of the hero, this high above its feet. */
    public static final double GRAB_HAND_FORWARD = 1.0, GRAB_HAND_UP = 0.6;
    /** A tether pulls back at most this fast (blocks per tick), and at least this fast once past its length. */
    public static final double TETHER_PULL_MAX = 0.6, TETHER_PULL_MIN = 0.15;

    /** A charge's speed in blocks per tick for its {@code speed} percent (120-200). */
    public static double chargeSpeed(double speedPercent) {
        return CHARGE_BASE_SPEED * Math.max(0, speedPercent) / 100.0;
    }

    /** The ticks a charge of {@code range} blocks at {@code speedPercent} runs (at least one, at most the cap). */
    public static int chargeTicks(double range, double speedPercent) {
        double v = chargeSpeed(speedPercent);
        if (!(v > 0)) {
            return 1;
        }
        return (int) Math.max(1, Math.min(CHARGE_MAX_TICKS, Math.ceil(Math.max(0, range) / v - 1e-9)));
    }

    /** A wave's front, in blocks from its centre, {@code ticks} after its release at {@code blocksPerSecond}. */
    public static double waveFront(int ticks, double blocksPerSecond) {
        return Math.max(0, ticks) * Math.max(0, blocksPerSecond) / 20.0;
    }

    /** The ticks a wave rolls before its front passes {@code radius} (it ends then). */
    public static int waveTicks(double radius, double blocksPerSecond) {
        if (!(blocksPerSecond > 0)) {
            return 1;
        }
        return (int) Math.max(1, Math.ceil(Math.max(0, radius) * 20.0 / blocksPerSecond - 1e-9));
    }

    /**
     * Whether a wave's front at {@code front} blocks hits a receiver {@code distance} blocks (horizontally) from its
     * centre whose half width is {@code halfWidth}, feet {@code dy} above the centre: only on the ground (jump to dodge),
     * within {@link #WAVE_BAND} of the front, inside the wave's radius.
     */
    public static boolean waveHits(double distance, double halfWidth, double dy, boolean onGround, double front,
                                   double radius) {
        return onGround && Math.abs(dy) <= WAVE_HEIGHT && front <= radius + WAVE_BAND
                && Math.abs(distance - front) <= WAVE_BAND + Math.max(0, halfWidth);
    }

    /**
     * A point in a disc of {@code radius} from two uniform numbers in [0, 1): spread evenly over the area (a rain's
     * impacts around its target). Returns {x, z}.
     */
    public static double[] discPoint(double u1, double u2, double radius) {
        double a = 2 * Math.PI * u1;
        double r = Math.max(0, radius) * Math.sqrt(Math.max(0, Math.min(1, u2)));
        return new double[] {Math.cos(a) * r, Math.sin(a) * r};
    }

    /**
     * A sweep's line {@code t} ticks into a sweep of {@code durationTicks} across {@code arc} degrees, in degrees from
     * the hero's facing at its start (measured as {@link #turn} turns): from -arc/2 to +arc/2, evenly.
     */
    public static double sweepAngle(int t, int durationTicks, double arc) {
        double f = durationTicks <= 0 ? 1 : Math.max(0, Math.min(1, t / (double) durationTicks));
        return -arc / 2 + arc * f;
    }

    /**
     * Whether a receiver at {@code (dx, dz)} from the hero (horizontal), {@code halfWidth} wide, is touched by a
     * sweep's line between {@code fromDeg} and {@code toDeg} (degrees from the facing {@code (fx, fz)}, measured as
     * {@link #turn} turns) this tick: within {@code range} and inside that wedge widened by the line's {@code width}.
     * Blocks are the caller's (a ray to the receiver).
     */
    public static boolean inSweep(double dx, double dz, double fx, double fz, double fromDeg, double toDeg,
                                  double range, double width, double halfWidth) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > range + halfWidth) {
            return false;
        }
        if (d < 0.5) {
            return true;
        }
        double fl = Math.sqrt(fx * fx + fz * fz);
        if (fl < 1e-9) {
            return false;
        }
        double facing = Math.atan2(fz, fx), at = Math.atan2(dz, dx);
        double rel = Math.toDegrees(wrap(at - facing));
        double margin = Math.toDegrees(Math.atan2(Math.max(0.5, width) / 2 + Math.max(0, halfWidth), d));
        double lo = Math.min(fromDeg, toDeg) - margin, hi = Math.max(fromDeg, toDeg) + margin;
        return rel >= lo - 1e-9 && rel <= hi + 1e-9;
    }

    /** An angle in radians wrapped to (-pi, pi]. */
    static double wrap(double a) {
        double r = a % (2 * Math.PI);
        if (r <= -Math.PI) {
            r += 2 * Math.PI;
        } else if (r > Math.PI) {
            r -= 2 * Math.PI;
        }
        return r;
    }

    /** The unit direction {@code deg} degrees from {@code (fx, fz)}, turned the same way {@link #inSweep} measures. */
    public static double[] turn(double fx, double fz, double deg) {
        double l = Math.sqrt(fx * fx + fz * fz);
        if (l < 1e-9) {
            return new double[] {0, 0};
        }
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        double x = fx / l, z = fz / l;
        return new double[] {x * c - z * s, x * s + z * c};
    }

    /**
     * The velocity that holds a grabbed receiver at the hero's hand: toward it at half the gap a tick, at most
     * {@link #GRAB_PULL_MAX}; {@code (dx, dy, dz)} is from the receiver to the hand. A velocity, never a teleport.
     */
    public static double[] grabHold(double dx, double dy, double dz) {
        double vx = dx * GRAB_PULL_GAIN, vy = dy * GRAB_PULL_GAIN, vz = dz * GRAB_PULL_GAIN;
        double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (l > GRAB_PULL_MAX) {
            double k = GRAB_PULL_MAX / l;
            vx *= k;
            vy *= k;
            vz *= k;
        }
        return new double[] {vx, vy, vz};
    }

    /**
     * The directions a throw tries, in degrees from the hero's facing, in order: straight ahead, then 45 and 90 degrees
     * to either side, then behind. The game takes the first whose landing is safe (6.12: never toward lava or a drop);
     * when none is, it throws ahead softened.
     */
    public static final double[] THROW_TRIES = {0, 45, -45, 90, -90, 135, -135, 180};

    /** A throw's horizontal speed for {@code strength} (1-4: as a knock-back of that strength) and its lift. */
    public static Impulse throwImpulse(double strength, double upBlocks) {
        double h = knockbackStrength(strength);
        double vy = Math.max(PULL_LIFT, liftImpulse(upBlocks));
        return new Impulse(h, vy, airTicks(vy, 0));
    }

    /** Where a throw would land (blocks from the start, horizontally): the arc of {@link #throwImpulse}, then a skid. */
    public static double throwDistance(double strength, double upBlocks) {
        Impulse i = throwImpulse(strength, upBlocks);
        return airDistance(i.horizontal(), i.airTicks()) + i.horizontal() * Math.pow(LeapSolver.HORIZONTAL_DRAG, i.airTicks())
                * GROUND_FRICTION / (1 - GROUND_FRICTION);
    }

    /**
     * A tether's pull: the speed (blocks per tick) toward its anchor for a receiver {@code distance} blocks away on a
     * tether of {@code max} blocks; 0 while it is within the length.
     */
    public static double tetherPull(double distance, double max) {
        double over = distance - Math.max(0, max);
        if (!(over > 0)) {
            return 0;
        }
        return Math.min(TETHER_PULL_MAX, Math.max(TETHER_PULL_MIN, over * 0.5));
    }
}
