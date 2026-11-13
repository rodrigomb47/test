package com.factionclash.logic.hero;

import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Situation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A flyer's travel plan over the heightmap (MOBILITY_AND_WORK 2.2, 2.3, 3.5, 3.7 and 3.9). The planner samples a
 * supplied height function ({@code MOTION_BLOCKING}, which counts leaves, in the game) every 4 blocks along the line
 * and 2 blocks to each side, the highest of the three, and lays a polyline at the cruise layer, 6-10 blocks above the
 * highest terrain along each segment and under the ceiling of the situation. It plans one 24-block waypoint at a time
 * (at most 48 samples an extension) and only over loaded terrain: a sample that is not loaded ends the plan there, and
 * the hero waits at the edge (hovering, at most 3 s) and then lands. Out of a raid or a scouting round a route gives
 * hostile bases a berth (a cost of x3 within their walls box plus 16 blocks, not a ban); on a raid it crosses them.
 *
 * <p>Around the plan: the stamina reserve (the best landing seen plus 25%), the legal landing cells, the take-off
 * headroom over the footprint, the forward probe for obstacles between samples, the end of a flight that is stuck in
 * the air (a head bump for 10 ticks, or less than 1 block of progress in 2 s), and the choice between flying and
 * walking for a waypoint. Pure, unit-tested ({@code FlightPlannerTest}); A11's {@code HeroFlightNavigation} reads it.
 */
public final class FlightPlanner {
    private FlightPlanner() {
    }

    private static final double EPS = 1e-9;

    /** Samples every 4 blocks along the line, 2 blocks to each side; one 24-block waypoint, at most 48 samples. */
    public static final int SAMPLE_STEP = 4, SIDE = 2, WAYPOINT = 24, MAX_SAMPLES = 48;
    /** The cruise layer: 6-10 blocks above the highest terrain along a segment. */
    public static final double CRUISE_MIN = 6, CRUISE_MAX = 10;
    /** FlyingPathNavigation (a 3D search) only within 16 blocks of take-off and landing. */
    public static final double NEAR_ENDS = 16;
    /** At the edge of loaded terrain it hovers at most 3 s, then lands. */
    public static final int EDGE_WAIT_TICKS = 3 * BossRules.TPS;
    /** A flying hero's traveller ticket is 5x5 chunks (radius 2) instead of a worker's 3x3 (radius 1). */
    public static final int TICKET_RADIUS_FLYING = 2, TICKET_RADIUS = 1;

    /** The surface height of a column (the y a hero would stand at on its top), or NaN when it is not loaded. */
    public interface Heights {
        double at(int x, int z);
    }

    /** One sampling station: where, the highest of its three samples, and whether all three were loaded. */
    public record Station(double x, double z, double terrain, boolean loaded) {
    }

    /** A point of the plan. */
    public record Point(double x, double y, double z) {
    }

    /** How a leg ended: at its waypoint, or at the edge of loaded terrain. */
    public enum End { WAYPOINT, UNLOADED }

    /** One leg: its points at the cruise layer (the first is the start), how it ended, and its sample count. */
    public record Leg(List<Point> points, End end, int samples) {
        public Point last() {
            return points.get(points.size() - 1);
        }
    }

    // ================================================================== sampling (3.5)

    /** The three samples of a station: on the line and 2 blocks to each side of it, the highest; NaN when unloaded. */
    static double station(Heights h, double x, double z, double dirX, double dirZ) {
        double best = Double.NEGATIVE_INFINITY;
        for (int s = -1; s <= 1; s++) {
            double sx = x - dirZ * SIDE * s, sz = z + dirX * SIDE * s;
            double v = h.at((int) Math.floor(sx), (int) Math.floor(sz));
            if (Double.isNaN(v)) {
                return Double.NaN;
            }
            best = Math.max(best, v);
        }
        return best;
    }

    /**
     * The stations from a point toward another, every 4 blocks and at the end, at most one waypoint (24 blocks) on;
     * sampling stops at the first station that is not loaded (it is returned, marked).
     */
    public static List<Station> stations(Heights h, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        List<Station> out = new ArrayList<>();
        if (len < EPS) {
            double t = station(h, x0, z0, 1, 0);
            out.add(new Station(x0, z0, t, !Double.isNaN(t)));
            return out;
        }
        double ux = dx / len, uz = dz / len, reach = Math.min(len, WAYPOINT);
        for (double d = 0; ; d = Math.min(reach, d + SAMPLE_STEP)) {
            double x = x0 + ux * d, z = z0 + uz * d, t = station(h, x, z, ux, uz);
            out.add(new Station(x, z, t, !Double.isNaN(t)));
            if (Double.isNaN(t) || d >= reach - EPS) {
                break;
            }
        }
        return out;
    }

    // ================================================================== the ceiling (2.3)

    /**
     * The ceiling over a station: a trip or an escape {@code travelAltitude} above the ground under it, a raid's
     * approach or a scouting round 10 above it (a wall top counts as ground), a fight 6 above the ground or above the
     * highest engaged enemy standing on a block, whoever that enemy is.
     */
    public static double ceiling(Situation s, double groundY, Collection<Double> engagedEnemyFeet, int travelAltitude) {
        return MobilityRules.ceiling(s, groundY, engagedEnemyFeet, travelAltitude);
    }

    /** On a raid or a scouting round: the raid's ceiling until an enemy engages it, then the fight's. */
    public static Situation raidSituation(boolean engaged) {
        return engaged ? Situation.FIGHT : Situation.RAID;
    }

    // ================================================================== a leg at the cruise layer

    /**
     * One leg from {@code from} toward ({@code toX}, {@code toZ}), at most one waypoint on: a point at each station at
     * the cruise layer, 6-10 blocks above the highest terrain of the segment it ends and of the one it starts (so it
     * is over an obstacle when it reaches the obstacle's station), kept at the altitude it has when that is inside the
     * layer (no bobbing), and never above the ceiling of the situation over that terrain (the ground under the hero
     * somewhere along those segments; every ceiling is at least 6 above it, so the layer always fits). A station that
     * is not loaded ends the leg at the last loaded one.
     */
    public static Leg leg(Heights h, Point from, double toX, double toZ, Situation s,
                          Collection<Double> engagedEnemyFeet, int travelAltitude) {
        List<Station> st = stations(h, from.x(), from.z(), toX, toZ);
        List<Point> pts = new ArrayList<>();
        pts.add(from);
        double alt = from.y();
        Station prev = st.get(0);
        End end = End.WAYPOINT;
        for (int i = 1; i < st.size(); i++) {
            Station cur = st.get(i);
            if (!cur.loaded()) {
                end = End.UNLOADED;
                break;
            }
            // the segment it ends and the one it starts (when the next station is loaded): it is over an obstacle
            // when it reaches the obstacle's station, not after it
            double top = Math.max(cur.terrain(), prev.loaded() ? prev.terrain() : cur.terrain());
            if (i + 1 < st.size() && st.get(i + 1).loaded()) {
                top = Math.max(top, st.get(i + 1).terrain());
            }
            double lo = top + CRUISE_MIN, hi = top + CRUISE_MAX;
            double ceil = ceiling(s, top, engagedEnemyFeet, travelAltitude);
            alt = Math.min(Math.max(alt, lo), Math.min(hi, ceil));
            pts.add(new Point(cur.x(), alt, cur.z()));
            prev = cur;
        }
        if (st.size() == 1 && !st.get(0).loaded()) {
            end = End.UNLOADED;
        }
        return new Leg(pts, end, st.size() * 3);
    }

    // ================================================================== hostile bases (3.7)

    /** A hostile base's walls box in the horizontal plane. */
    public record Box(double minX, double minZ, double maxX, double maxZ) {
        public Box grow(double m) {
            return new Box(minX - m, minZ - m, maxX + m, maxZ + m);
        }

        boolean inside(double x, double z) {
            return x > minX + EPS && x < maxX - EPS && z > minZ + EPS && z < maxZ - EPS;
        }
    }

    /** The length of a segment inside a box (the part of the line a plan pays x3 for). */
    static double inside(Box b, double x0, double z0, double x1, double z1) {
        // clip the segment against the box (Liang-Barsky)
        double t0 = 0, t1 = 1, dx = x1 - x0, dz = z1 - z0;
        double[] p = {-dx, dx, -dz, dz};
        double[] q = {x0 - b.minX(), b.maxX() - x0, z0 - b.minZ(), b.maxZ() - z0};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < EPS) {
                if (q[i] < 0) {
                    return 0;
                }
                continue;
            }
            double t = q[i] / p[i];
            if (p[i] < 0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
        }
        return t1 > t0 ? (t1 - t0) * Math.hypot(dx, dz) : 0;
    }

    /** The cost of a horizontal polyline: its length, x3 inside a hostile base's berth when the situation pays it. */
    public static double cost(List<double[]> poly, List<Box> hostile, Situation s) {
        double total = 0;
        for (int i = 1; i < poly.size(); i++) {
            double[] a = poly.get(i - 1), b = poly.get(i);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]), in = 0;
            for (Box box : hostile) {
                in = Math.max(in, inside(box.grow(HeroLinks.BERTH_MARGIN), a[0], a[1], b[0], b[1]));
            }
            total += len + (HeroLinks.berth(s, true) - 1) * in;
        }
        return total;
    }

    /**
     * The horizontal route from one point to another: the straight line, or, when the situation gives hostile bases a
     * berth (a trip or an escape), the cheapest of the line and the ways round each base's berth by its corners. On a
     * raid, a scouting round or in a fight the line (bases cost nothing extra).
     */
    public static List<double[]> route(double x0, double z0, double x1, double z1, List<Box> hostile, Situation s) {
        List<double[]> best = List.of(new double[]{x0, z0}, new double[]{x1, z1});
        double bestCost = cost(best, hostile, s);
        if (HeroLinks.berth(s, true) <= 1) {
            return best;
        }
        for (Box b : hostile) {
            Box g = b.grow(HeroLinks.BERTH_MARGIN + 1);
            double[][] c = {{g.minX(), g.minZ()}, {g.maxX(), g.minZ()}, {g.maxX(), g.maxZ()}, {g.minX(), g.maxZ()}};
            for (int i = 0; i < 4; i++) {
                for (List<double[]> cand : List.of(List.of(new double[]{x0, z0}, c[i], new double[]{x1, z1}),
                        List.of(new double[]{x0, z0}, c[i], c[(i + 1) % 4], new double[]{x1, z1}),
                        List.of(new double[]{x0, z0}, c[(i + 1) % 4], c[i], new double[]{x1, z1}))) {
                    double k = cost(cand, hostile, s);
                    if (k < bestCost - EPS) {
                        bestCost = k;
                        best = cand;
                    }
                }
            }
        }
        return best;
    }

    // ================================================================== landing and take-off (2.2, 3.5)

    /** What a landing cell stands on. */
    public enum Surface { GROUND, FARMLAND, LAVA, FIRE, POWDER_SNOW, WATER }

    /**
     * Whether a cell is a legal landing (2.2): standable with 2 blocks of headroom, never farmland (a landing
     * tramples it), lava, fire, powder snow or water; inside a hostile base only on purpose (a raid or a scouting
     * round), since its archers shoot what lands there.
     */
    public static boolean legalLanding(Surface s, int headroom, boolean insideHostileBase, boolean onPurpose) {
        return s == Surface.GROUND && headroom >= 2 && (!insideHostileBase || onPurpose);
    }

    /**
     * The take-off headroom (3.5): the open height over the feet through the full column the take-off rises
     * through, the least over every cell of the hero's footprint (the cells its 0.6-wide box overlaps, at most 2x2),
     * so it never launches into an overhang. {@code openAbove} gives the open blocks over the feet of one column.
     */
    public static int takeOffHeadroom(double x, double z, double halfWidth,
                                      java.util.function.IntBinaryOperator openAbove) {
        int least = Integer.MAX_VALUE;
        for (int cx = (int) Math.floor(x - halfWidth + EPS); cx <= (int) Math.floor(x + halfWidth - EPS); cx++) {
            for (int cz = (int) Math.floor(z - halfWidth + EPS); cz <= (int) Math.floor(z + halfWidth - EPS); cz++) {
                least = Math.min(least, openAbove.applyAsInt(cx, cz));
            }
        }
        return least;
    }

    /** A landing cell seen in loaded terrain, with its distance from the hero now. */
    public record Landing(double distance) {
    }

    /**
     * The stamina rule of 3.5: keep enough to reach the best landing seen (the nearest) at cruise plus 25%; true when
     * the hero must land now (or when it has seen none and is not on the ground).
     */
    public static boolean mustLand(double stamina, List<Landing> seen, double cruiseSpeed, double drainPerSecond) {
        double best = Double.POSITIVE_INFINITY;
        for (Landing l : seen) {
            best = Math.min(best, l.distance());
        }
        if (Double.isInfinite(best)) {
            return true;
        }
        return MobilityRules.mustLandForReserve(stamina, best / Math.max(EPS, cruiseSpeed), drainPerSecond);
    }

    // ================================================================== in the air (3.5)

    /** What the forward probe makes the flight control do. */
    public enum Probe { CLEAR, CLIMB, TURN }

    /** The forward probe runs every 4 ticks, 4 blocks ahead along the heading. */
    public static final int PROBE_TICKS = 4;
    public static final double PROBE_AHEAD = 4;

    /**
     * An obstacle between samples (a spruce top, a tower, a cliff edge): with a solid block 4 blocks ahead the hero
     * climbs at its climb rate until the probe is clear, or turns along the planner's next waypoint if the climb would
     * pass the altitude ceiling.
     */
    public static Probe probe(boolean blockedAhead, double y, double obstacleTop, double ceiling) {
        if (!blockedAhead) {
            return Probe.CLEAR;
        }
        return obstacleTop + 1 <= ceiling + EPS && y < ceiling - EPS ? Probe.CLIMB : Probe.TURN;
    }

    /** A head bump for this many ticks, or less than 1 block of progress in 2 s, ends a flight. */
    public static final int HEAD_BUMP_TICKS = 10, PROGRESS_TICKS = 2 * BossRules.TPS;
    public static final double PROGRESS_BLOCKS = 1;
    /** The leg is then a dead link for 10 minutes. */
    public static final int DEAD_LEG_TICKS = 10 * 60 * BossRules.TPS;

    /**
     * Stuck in the air (3.5): a flyer wedged under leaves or a ceiling. Ticked once a tick in flight; it reports the
     * end when the hero has bumped its head while climbing for 10 ticks in a row, or made less than 1 block of
     * progress toward its waypoint in 2 s. The hero then descends at its glide rate (or falls with its safe fall) and
     * the trip goes on with the ground navigation.
     */
    public static final class Watch {
        private int bumps;
        private double bestRemaining = Double.POSITIVE_INFINITY;
        private int sinceProgress;

        /** One tick: whether its head hit something while it climbed, and its distance left to the waypoint. */
        public boolean tick(boolean headBumpClimbing, double remaining) {
            bumps = headBumpClimbing ? bumps + 1 : 0;
            if (remaining < bestRemaining - PROGRESS_BLOCKS + EPS) {
                bestRemaining = remaining;
                sinceProgress = 0;
            } else {
                sinceProgress++;
            }
            return stuck();
        }

        public boolean stuck() {
            return bumps >= HEAD_BUMP_TICKS || sinceProgress >= PROGRESS_TICKS;
        }

        /** A new waypoint: progress is counted from here. */
        public void reset() {
            bumps = 0;
            bestRemaining = Double.POSITIVE_INFINITY;
            sinceProgress = 0;
        }
    }

    // ================================================================== flying or walking (3.5)

    /** Fly when the ground route is at least 1.5 times the straight line, or the trip is longer than 40 blocks... */
    public static final double DETOUR_FACTOR = 1.5, LONG_TRIP = 40;

    /** What decides a waypoint's mode. */
    public record ModeInput(double straight, double groundRoute, boolean groundCrossesWhatItCannotLink,
                            double tripLength, boolean staminaCoversLeg, boolean legalLanding, int headroom,
                            boolean thunderstorm, Situation situation, boolean exhausted) {
    }

    public enum Mode { FLY, WALK }

    /**
     * The mode for one waypoint: walk when it does not fly, finds no legal landing, has under 4 blocks of headroom,
     * is exhausted, or a thunderstorm rages outside a fight (an escape flies in any weather); otherwise fly when the
     * stamina covers the leg and the ground route is 1.5 times the straight line or longer (no ground route counts),
     * or crosses water, a ravine, a cliff or a wall the hero cannot link over, or the trip is longer than 40 blocks
     * and its average flight speed at its level beats walking.
     */
    public static Mode mode(Profile p, ModeInput in) {
        if (!p.flies() || !in.legalLanding() || in.headroom() < MobilityRules.TAKE_OFF_HEADROOM || in.exhausted()
                || in.thunderstorm() && (in.situation() == Situation.TRAVEL || in.situation() == Situation.RAID)
                || !in.staminaCoversLeg()) {
            return Mode.WALK;
        }
        boolean detour = Double.isInfinite(in.groundRoute()) || in.groundRoute() >= DETOUR_FACTOR * in.straight() - EPS;
        boolean longAndFaster = in.tripLength() > LONG_TRIP + EPS
                && MobilityRules.averageFlightSpeed(p) > MobilityRules.WALK_SPEED + EPS;
        return detour || in.groundCrossesWhatItCannotLink() || longAndFaster ? Mode.FLY : Mode.WALK;
    }

    /**
     * At the edge of loaded terrain (a leg that ended {@link End#UNLOADED}): it hovers in place (2.0 drain) while the
     * next waypoint loads, and lands after 3 s. True when it must land now.
     */
    public static boolean landAtEdge(int ticksWaited) {
        return ticksWaited >= EDGE_WAIT_TICKS;
    }

    /** A flying hero carries a 5x5 chunk ticket, released on landing (3.5). */
    public static int ticketRadius(boolean flying) {
        return flying ? TICKET_RADIUS_FLYING : TICKET_RADIUS;
    }
}
