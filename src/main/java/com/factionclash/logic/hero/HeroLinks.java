package com.factionclash.logic.hero;

import com.factionclash.logic.hero.MobilityRules.Feature;
import com.factionclash.logic.hero.MobilityRules.Profile;
import com.factionclash.logic.hero.MobilityRules.Situation;
import com.factionclash.logic.hero.MobilityRules.Stat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A hero's ground links on a pure grid model (MOBILITY_AND_WORK 3.2-3.4, 3.7, 5.1): the extra neighbours
 * {@code HeroNodeEvaluator} (A11) offers where the vanilla neighbour fails (a hole, a wall, a fluid), at most 8 per
 * node: <b>jump-up</b> onto a ledge 1.5-3 blocks higher, <b>gap</b> leaps the {@link LeapSolver} finds, <b>drops</b>
 * down to the safe fall distance, <b>climbs</b> up a wall face, <b>chimneys</b> up a 1-2 wide shaft and <b>glides</b>
 * down from an edge. Their sizes come from the hero's {@link Profile} at its level; every link is checked against the
 * hazard rules before it is offered (5.1.13); a risky gap (lava, fire, the void or a drop deeper than the safe fall
 * under the arc) is refused in a fight and needs a wide landing out of one or while escaping (3.3). The walls of any
 * base are terrain like any other (3.7): nothing here knows whose blocks they are; only the travel cost gives hostile
 * bases a berth.
 *
 * <p>Around the links: whether engaged enemies can reach where the hero stands (the input of the fight-reach clock of
 * 5.1.15, the same for a player and an NPC soldier: an ordinary person walks, sprint-jumps gaps of up to 3 blocks,
 * steps up 1 and towers up 2; an enemy hero uses its own links), and the strike from a link of 3.7 (a basic attack
 * only, only where no standable cell lies within melee reach of the target). Pure, unit-tested ({@code HeroLinksTest}).
 */
public final class HeroLinks {
    private HeroLinks() {
    }

    private static final double EPS = 1e-6;

    /** At most this many extra neighbours per node (3.9). */
    public static final int MAX_LINKS = 8;
    /** A worker's own neighbours: a step up of one full block, a drop of at most 3 (vanilla's). */
    public static final double VANILLA_RISE = 1, VANILLA_DROP = 3;
    /** Gap links start at 2 blocks; a gap landing is at most 1 block higher (and at most 1 lower). */
    public static final int GAP_MIN = 2;
    public static final double GAP_LANDING_RISE = 1;
    /** Climb links start at 2 blocks (a 1-block rise is a step). */
    public static final int CLIMB_MIN = 2;
    /** A chimney is at least 3 blocks high; the search stops at this height (PROPOSED: wall kicks have no limit). */
    public static final int CHIMNEY_MIN = 3, CHIMNEY_MAX = 32;
    /** A glide link reaches at most 32 blocks forward (3.2). */
    public static final int GLIDE_MAX = 32;
    /** Melee reach (feet to feet): an enemy is in reach within 3 blocks, as 5.1.6's "more than 3 blocks above". */
    public static final double MELEE_REACH = BossRules.REACH_ABOVE;
    /** An ordinary person (a player or an NPC soldier): sprint-jumps gaps of up to 3, towers up 2 blocks (5.1.15). */
    public static final int PERSON_GAP = 3;
    public static final double PERSON_TOWER = 2;
    /** The reach searches stay within this many blocks and nodes. */
    public static final int SEARCH_RADIUS = 32, SEARCH_NODES = 4096;
    /** In combat: a combat goal runs, a hostile is within 16 blocks, or the hero was hurt in the last 5 s (3.3). */
    public static final double COMBAT_HOSTILE_RANGE = 16;
    public static final int COMBAT_DAMAGE_TICKS = 5 * BossRules.TPS;
    /** Out of a raid or a scouting round, routes give hostile bases a berth: x3 within their walls box + 16 (3.7). */
    public static final double BERTH_COST = 3, BERTH_MARGIN = 16;

    // ================================================================== the grid

    /** What a cell holds: air, a full block, a fence or wall (1.5 tall), fluids and the hazards. */
    public enum Cell {
        AIR, SOLID, FENCE, WATER, LAVA, FIRE, POWDER_SNOW, COBWEB;

        /** The collision height from the bottom of the cell. */
        public double collision() {
            return this == SOLID ? 1 : this == FENCE ? 1.5 : 0;
        }

        boolean holds() {
            return this == SOLID || this == FENCE;
        }
    }

    /** The world as the links see it; below {@link #minY} is the void. */
    public interface Grid {
        Cell at(int x, int y, int z);

        default int minY() {
            return -64;
        }
    }

    /** A feet cell. */
    public record Pos(int x, int y, int z) {
        public Pos add(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static Cell at(Grid g, int x, int y, int z) {
        return y < g.minY() ? Cell.AIR : g.at(x, y, z);
    }

    /** The feet height of a hero standing in this cell (on a block's top, a fence's at +0.5), or NaN. */
    public static double standY(Grid g, Pos p) {
        if (p.y() - 1 < g.minY()) {
            return Double.NaN;
        }
        Cell below = g.at(p.x(), p.y() - 1, p.z());
        if (!below.holds() || at(g, p.x(), p.y(), p.z()) != Cell.AIR || at(g, p.x(), p.y() + 1, p.z()) != Cell.AIR) {
            return Double.NaN;
        }
        if (below == Cell.FENCE && at(g, p.x(), p.y() + 2, p.z()) != Cell.AIR) {
            return Double.NaN;
        }
        return below == Cell.FENCE ? p.y() + 0.5 : p.y();
    }

    public static boolean standable(Grid g, Pos p) {
        return !Double.isNaN(standY(g, p));
    }

    /** What lies under a column from a height down: the first cell that is not air, its top, and the depth to it. */
    public record Floor(Cell cell, double top, boolean isVoid) {
        public double depthBelow(double feetY) {
            return isVoid ? Double.POSITIVE_INFINITY : feetY - top;
        }
    }

    /** The first non-air cell at or below {@code fromY} in a column (the void below {@link Grid#minY}). */
    public static Floor floor(Grid g, int x, int fromY, int z) {
        for (int y = fromY; y >= g.minY(); y--) {
            Cell c = g.at(x, y, z);
            if (c != Cell.AIR) {
                return new Floor(c, c.holds() ? y + c.collision() : y + 1, false);
            }
        }
        return new Floor(Cell.AIR, Double.NEGATIVE_INFINITY, true);
    }

    /** The standable cell in a column whose feet height is within [lo, hi], the highest first (null: none). */
    static Pos standIn(Grid g, int x, int z, double lo, double hi) {
        for (int y = (int) Math.floor(hi + EPS); y >= (int) Math.floor(lo) - 1; y--) {
            Pos c = new Pos(x, y, z);
            double s = standY(g, c);
            if (!Double.isNaN(s) && s >= lo - EPS && s <= hi + EPS) {
                return c;
            }
        }
        return null;
    }

    /**
     * Whether a worker's own neighbour in this direction works: level ground, a step of one full block up, or a drop
     * of at most 3 onto a block (vanilla's walk evaluator). A fluid, a wall or a deeper hole fails it.
     */
    public static boolean vanillaNeighbour(Grid g, Pos p, int dx, int dz) {
        double sp = standY(g, p);
        if (Double.isNaN(sp)) {
            return false;
        }
        int qx = p.x() + dx, qz = p.z() + dz;
        if (standIn(g, qx, qz, sp - 0.5, sp + VANILLA_RISE) != null) {
            return true;
        }
        Floor f = floor(g, qx, p.y(), qz);
        double depth = f.depthBelow(sp);
        return f.cell().holds() && depth >= -EPS && depth <= VANILLA_DROP + EPS
                && standable(g, new Pos(qx, (int) Math.floor(f.top() + EPS), qz));
    }

    // ================================================================== links

    public enum Kind { JUMP_UP, GAP, DROP, CLIMB, CHIMNEY, GLIDE }

    /**
     * One link: its kind, the cells it joins, its path cost, the leap of a gap link (null otherwise), and whether its
     * gap was risky (it was offered only with a wide landing, out of a fight or while escaping).
     */
    public record Link(Kind kind, Pos from, Pos to, double cost, LeapSolver.Leap leap, boolean risky) {
    }

    /**
     * Whether the hero is in combat for the risky-gap rule (3.3): a combat goal runs, a hostile is within 16 blocks,
     * or it took damage in the last 5 s.
     */
    public static boolean inCombat(boolean combatGoalRunning, double nearestHostile, int ticksSinceHurt) {
        return combatGoalRunning || nearestHostile <= COMBAT_HOSTILE_RANGE + EPS || ticksSinceHurt < COMBAT_DAMAGE_TICKS;
    }

    /** The situation the risky-gap rule reads: in combat a fight, unless the hero escapes. */
    public static Situation linkSituation(boolean inCombat, boolean escaping) {
        return escaping ? Situation.ESCAPE : inCombat ? Situation.FIGHT : Situation.TRAVEL;
    }

    /**
     * The links from a node, cheapest first and at most 8, for a hero with this mobility now. A profile with no
     * mobility (a worker, a soldier) has none, so ordinary NPCs move exactly as today.
     */
    public static List<Link> links(Grid g, Pos p, Profile m, Situation s) {
        List<Link> out = new ArrayList<>();
        double sp = standY(g, p);
        if (!m.any() || Double.isNaN(sp)) {
            return out;
        }
        for (int[] d : DIRS) {
            if (vanillaNeighbour(g, p, d[0], d[1])) {
                continue;
            }
            add(out, jumpUp(g, p, sp, d[0], d[1], m));
            add(out, gap(g, p, sp, d[0], d[1], m, s));
            add(out, drop(g, p, sp, d[0], d[1], m));
            add(out, climb(g, p, sp, d[0], d[1], m));
            add(out, glide(g, p, sp, d[0], d[1], m));
        }
        add(out, chimney(g, p, sp, m));
        out.sort(Comparator.comparingDouble(Link::cost));
        return out.size() > MAX_LINKS ? new ArrayList<>(out.subList(0, MAX_LINKS)) : out;
    }

    private static void add(List<Link> out, Link l) {
        if (l != null) {
            out.add(l);
        }
    }

    /** Every cell of a column from {@code y0} to {@code y1} (inclusive) is air. */
    private static boolean open(Grid g, int x, int y0, int y1, int z) {
        for (int y = y0; y <= y1; y++) {
            if (at(g, x, y, z) != Cell.AIR) {
                return false;
            }
        }
        return true;
    }

    /** Jump-up: a ledge 1.5-3 blocks higher one block across, within the hero's jump height (with air jumps) - 0.3. */
    static Link jumpUp(Grid g, Pos p, double sp, int dx, int dz, Profile m) {
        double max = m.jumpUp();
        if (max < MobilityRules.JUMP_UP_MIN - EPS) {
            return null;
        }
        Pos t = standIn(g, p.x() + dx, p.z() + dz, sp + VANILLA_RISE + EPS, sp + max);
        if (t == null) {
            return null;
        }
        double rise = standY(g, t) - sp;
        // headroom over the start: it rises in place to the ledge, then steps over it
        int top = (int) Math.floor(sp + rise + LeapSolver.Body.HERO.height() - EPS);
        if (rise < MobilityRules.JUMP_UP_MIN - EPS || !open(g, p.x(), p.y(), top, p.z())) {
            return null;
        }
        return new Link(Kind.JUMP_UP, p, t, 2 + rise, null, false);
    }

    /** The vertical slice along a direction from a node, relative to its feet cell. */
    static LeapSolver.Slice slice(Grid g, Pos p, int dx, int dz) {
        return (c, k) -> at(g, p.x() + c * dx, p.y() + k, p.z() + c * dz).collision();
    }

    /** Whether a floor under an arc is a hazard for this hero: lava, fire, powder snow, the void or deeper than its fall. */
    static boolean hazardFloor(Floor f, double feetY, Profile m) {
        boolean fireproof = m.has(Feature.FIREPROOF);
        if (f.isVoid() || f.cell() == Cell.POWDER_SNOW) {
            return true;
        }
        if ((f.cell() == Cell.LAVA || f.cell() == Cell.FIRE) && !fireproof) {
            return true;
        }
        if (f.cell() == Cell.WATER) {
            return false; // water breaks a fall
        }
        return f.depthBelow(feetY) > m.safeFall() + EPS;
    }

    /**
     * A gap leap: 2 to the hero's current gap width of holes (no floor within a block of its feet), landing at most 1
     * block higher or lower on the first cell beyond them, where the leap solver lands it within 0.15 blocks of the
     * centre with 2 blocks of headroom along the arc. Never aimed next to a drop deeper than its safe fall (the cell
     * beyond the landing); a risky gap is refused in a fight and needs a wide landing otherwise.
     */
    static Link gap(Grid g, Pos p, double sp, int dx, int dz, Profile m, Situation s) {
        int width = m.gap();
        if (width < GAP_MIN) {
            return null;
        }
        boolean risky = false;
        for (int n = 1; n <= width + 1; n++) {
            int cx = p.x() + n * dx, cz = p.z() + n * dz;
            Pos t = standIn(g, cx, cz, sp - GAP_LANDING_RISE, sp + GAP_LANDING_RISE);
            if (t != null) {
                int w = n - 1;
                if (w < GAP_MIN) {
                    return null;
                }
                double ty = standY(g, t);
                LeapSolver.Leap leap = LeapSolver.solve(0, sp - p.y(), n, ty - p.y(),
                        LeapSolver.Limits.forGap(width, m.jumpHeight()), slice(g, p, dx, dz));
                if (leap == null) {
                    return null;
                }
                // the cell beyond the landing: a wall stops an overshoot, a standable cell makes a wide landing
                int bx = cx + dx, bz = cz + dz;
                boolean wall = at(g, bx, t.y(), bz).holds() || at(g, bx, t.y() + 1, bz).holds();
                boolean wide = wall || standIn(g, bx, bz, ty - GAP_LANDING_RISE, ty + GAP_LANDING_RISE) != null;
                if (!wall && hazardFloor(floor(g, bx, t.y() + 1, bz), ty, m)) {
                    return null; // never next to a drop deeper than its safe fall (1.6, 5.1.13)
                }
                if (risky && (s == Situation.FIGHT || !wide)) {
                    return null;
                }
                return new Link(Kind.GAP, p, t, 1 + w / 2.0, leap, risky);
            }
            Floor f = floor(g, cx, p.y() + 1, cz);
            if (!f.isVoid() && f.top() >= sp - GAP_LANDING_RISE - EPS && f.cell().holds()) {
                return null; // not a hole: a wall or a ledge the hero would walk or jump up to
            }
            risky |= hazardFloor(f, sp, m);
        }
        return null;
    }

    /** A drop deeper than a worker's 3 blocks, down to the hero's safe fall distance, onto a standable cell. */
    static Link drop(Grid g, Pos p, double sp, int dx, int dz, Profile m) {
        double safe = m.safeFall();
        if (safe <= VANILLA_DROP + EPS) {
            return null;
        }
        int qx = p.x() + dx, qz = p.z() + dz;
        Floor f = floor(g, qx, p.y() + 1, qz);
        if (!f.cell().holds()) {
            return null;
        }
        double depth = f.depthBelow(sp);
        Pos t = new Pos(qx, (int) Math.floor(f.top() + EPS), qz);
        if (depth <= VANILLA_DROP + EPS || depth > safe + EPS || !standable(g, t)
                || !open(g, qx, t.y(), p.y() + 1, qz)) {
            return null;
        }
        return new Link(Kind.DROP, p, t, 1 + depth / 4, null, false);
    }

    /**
     * A climb up the wall face in front of the hero to the standable cell on top, when its height is within the
     * hero's current climb height and every face cell is solid; the hero's own column must be open in front of the
     * face. A climber III also climbs round a one-block overhang at the top of the face.
     */
    static Link climb(Grid g, Pos p, double sp, int dx, int dz, Profile m) {
        int max = m.climbHeight();
        if (max < CLIMB_MIN) {
            return null;
        }
        int qx = p.x() + dx, qz = p.z() + dz;
        int h = 0;
        while (h <= max + 1 && at(g, qx, p.y() + h, qz).holds()) {
            h++;
        }
        if (h < CLIMB_MIN || h > max + 1) {
            return null;
        }
        // the hero's column in front of the face, up to the top and its head above the edge
        for (int k = 0; k <= h + 1; k++) {
            if (at(g, p.x(), p.y() + k, p.z()) == Cell.AIR) {
                continue;
            }
            return overhang(g, p, sp, dx, dz, m, k);
        }
        Pos t = new Pos(qx, p.y() + h, qz);
        double ty = standY(g, t);
        if (Double.isNaN(ty) || ty - sp > max + EPS) {
            return null;
        }
        return new Link(Kind.CLIMB, p, t, 1.5 * (ty - sp), null, false);
    }

    /**
     * A climber III's way round a one-block overhang: the block at height {@code k} over its climbing column juts out
     * from the face; it climbs out round it (the column behind it open) and up onto it.
     */
    private static Link overhang(Grid g, Pos p, double sp, int dx, int dz, Profile m, int k) {
        if (!m.has(Feature.OVERHANG) || k < CLIMB_MIN || !at(g, p.x(), p.y() + k, p.z()).holds()
                || !open(g, p.x(), p.y(), p.y() + k - 1, p.z())) {
            return null;
        }
        int ox = p.x() - dx, oz = p.z() - dz;
        if (!open(g, ox, p.y() + k - 1, p.y() + k + 2, oz)) {
            return null;
        }
        Pos t = new Pos(p.x(), p.y() + k + 1, p.z());
        double ty = standY(g, t);
        if (Double.isNaN(ty) || ty - sp > m.climbHeight() + EPS) {
            return null;
        }
        return new Link(Kind.CLIMB, p, t, 1.5 * (ty - sp) + 1, null, false);
    }

    /** Whether the cell at height y of a shaft is enclosed: walls on both sides along one axis, 1 or 2 apart. */
    private static boolean enclosed(Grid g, int x, int y, int z) {
        for (int[] a : new int[][]{{1, 0}, {0, 1}}) {
            boolean minus = at(g, x - a[0], y, z - a[1]).holds(), plus = at(g, x + a[0], y, z + a[1]).holds();
            if (minus && plus) {
                return true;
            }
            if (minus && at(g, x + a[0], y, z + a[1]) == Cell.AIR && at(g, x + 2 * a[0], y, z + 2 * a[1]).holds()) {
                return true;
            }
            if (plus && at(g, x - a[0], y, z - a[1]) == Cell.AIR && at(g, x - 2 * a[0], y, z - 2 * a[1]).holds()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A chimney (acrobat II-III): up a shaft 1-2 blocks wide by alternating wall kicks, to the first standable cell
     * beside its top, when that is higher than a jump-up link reaches.
     */
    static Link chimney(Grid g, Pos p, double sp, Profile m) {
        if (!m.has(Feature.CHIMNEY)) {
            return null;
        }
        int h = 0;
        while (h < CHIMNEY_MAX && at(g, p.x(), p.y() + h, p.z()) == Cell.AIR && enclosed(g, p.x(), p.y() + h, p.z())) {
            h++;
        }
        if (h < CHIMNEY_MIN) {
            return null;
        }
        if (!open(g, p.x(), p.y() + h, p.y() + h + 1, p.z())) {
            return null; // a capped shaft
        }
        for (int[] d : DIRS) {
            Pos t = standIn(g, p.x() + d[0], p.z() + d[1], p.y() + h - 1, p.y() + h + 1);
            if (t != null) {
                double rise = standY(g, t) - sp;
                if (rise > m.jumpUp() + EPS) {
                    return new Link(Kind.CHIMNEY, p, t, 2 * rise, null, false);
                }
            }
        }
        return null;
    }

    /**
     * A glide (gliders and flyers): from an edge with a drop of 4 blocks or more, along the descent line of the
     * hero's current glide ratio, to the cell where it meets the ground, at most 32 blocks on and within its glide
     * time; the line must be clear and the landing standable and lower than the edge.
     */
    static Link glide(Grid g, Pos p, double sp, int dx, int dz, Profile m) {
        if (!m.glides()) {
            return null;
        }
        double ratio = m.glideRatio(), forward = m.raw(Stat.GLIDE_FORWARD), seconds = m.raw(Stat.GLIDE_SECONDS);
        if (ratio <= 0 || Double.isNaN(forward) || Double.isNaN(seconds)) {
            return null;
        }
        Floor edge = floor(g, p.x() + dx, p.y() + 1, p.z() + dz);
        if (edge.depthBelow(sp) < MobilityRules.GLIDE_MIN_DROP - EPS) {
            return null;
        }
        double height = LeapSolver.Body.HERO.height();
        for (int n = 1; n <= GLIDE_MAX; n++) {
            int cx = p.x() + n * dx, cz = p.z() + n * dz;
            // the feet cross this column from the height at its near edge to the height at its far edge
            double enter = sp - (n - 0.5) / ratio, exit = sp - (n + 0.5) / ratio;
            for (int y = (int) Math.floor(exit); y <= (int) Math.floor(enter + height - EPS); y++) {
                Cell c = at(g, cx, y, cz);
                if (c.holds() && y + c.collision() > enter + EPS && y < enter + height - EPS) {
                    return null; // the line runs into the side of a block
                }
            }
            Floor f = floor(g, cx, (int) Math.floor(enter + EPS), cz);
            if (!f.isVoid() && f.top() >= exit - EPS) {
                if (!f.cell().holds() || n / forward > seconds + EPS) {
                    return null; // into water, lava or fire, or past its glide time
                }
                Pos t = new Pos(cx, (int) Math.floor(f.top() + EPS), cz);
                double ty = standY(g, t);
                if (Double.isNaN(ty) || ty > sp - 1 + EPS || n < 2) {
                    return null;
                }
                return new Link(Kind.GLIDE, p, t, n / 2.0, null, false);
            }
        }
        return null;
    }

    /** The cost multiplier of a node on a route (3.7): x3 near a hostile base on a trip or an escape, else x1. */
    public static double berth(Situation s, boolean nearHostileBase) {
        return nearHostileBase && (s == Situation.TRAVEL || s == Situation.ESCAPE) ? BERTH_COST : 1;
    }

    // ================================================================== reach (5.1.15, 3.7)

    /**
     * An engaged enemy: where it stands and how it moves. {@link MobilityRules#NONE} moves as an ordinary person, a
     * player or an NPC soldier alike (there is no field for which it is); an enemy hero's own profile adds its links.
     */
    public record Enemy(Pos at, Profile mobility) {
    }

    /**
     * Where an ordinary person's feet are in a cell: standing on a block, or swimming at the surface of water (the
     * cell above the water's top block, feet 0.4 into it); NaN otherwise.
     */
    static double personY(Grid g, Pos p) {
        double s = standY(g, p);
        if (!Double.isNaN(s)) {
            return s;
        }
        if (p.y() - 1 >= g.minY() && g.at(p.x(), p.y() - 1, p.z()) == Cell.WATER
                && at(g, p.x(), p.y(), p.z()) == Cell.AIR && at(g, p.x(), p.y() + 1, p.z()) == Cell.AIR) {
            return p.y() - 0.4;
        }
        return Double.NaN;
    }

    /** The cell a person stands or swims in, in a column, with its feet within [lo, hi], the highest first. */
    static Pos personIn(Grid g, int x, int z, double lo, double hi) {
        for (int y = (int) Math.floor(hi + EPS); y >= (int) Math.floor(lo) - 1; y--) {
            Pos c = new Pos(x, y, z);
            double s = personY(g, c);
            if (!Double.isNaN(s) && s >= lo - EPS && s <= hi + EPS) {
                return c;
            }
        }
        return null;
    }

    /**
     * One step of an ordinary person from a node (5.1.15: a player and an NPC soldier alike): walking level, a step
     * up, towering up to 2 blocks and stepping over, a drop of at most 3, swimming across water, and a sprint-jump
     * over a gap of up to 3 blocks landing no higher.
     */
    static List<Pos> personSteps(Grid g, Pos p) {
        List<Pos> out = new ArrayList<>();
        double sp = personY(g, p);
        if (Double.isNaN(sp)) {
            return out;
        }
        boolean swimming = Double.isNaN(standY(g, p));
        double up = swimming ? VANILLA_RISE : PERSON_TOWER;
        for (int[] d : DIRS) {
            int qx = p.x() + d[0], qz = p.z() + d[1];
            Pos near = personIn(g, qx, qz, sp - 1, sp + up);
            if (near != null && open(g, p.x(), p.y(), (int) Math.floor(personY(g, near) + 1.8 - EPS), p.z())) {
                out.add(near); // level, a step up, towering up to 2 and stepping over, or into the water
            } else {
                Floor f = floor(g, qx, p.y() + 1, qz);
                double depth = f.depthBelow(sp);
                if (depth >= -EPS && depth <= VANILLA_DROP + EPS) {
                    Pos down = new Pos(qx, (int) Math.floor(f.top() + EPS), qz);
                    if (!Double.isNaN(personY(g, down))) {
                        out.add(down);
                    }
                }
            }
            if (swimming) {
                continue;
            }
            // a sprint-jump over a gap of up to 3, landing no higher
            for (int n = 2; n <= PERSON_GAP + 1; n++) {
                int cx = p.x() + n * d[0], cz = p.z() + n * d[1];
                Floor between = floor(g, p.x() + (n - 1) * d[0], p.y() + 1, p.z() + (n - 1) * d[1]);
                if (!between.isVoid() && between.top() >= sp - 1 - EPS) {
                    break;
                }
                Pos t = standIn(g, cx, cz, sp - VANILLA_DROP, sp);
                if (t != null) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    /** The cells a mover can step to from a node: a person's steps, and a hero's links on top. */
    static List<Pos> steps(Grid g, Pos p, Profile m) {
        List<Pos> out = personSteps(g, p);
        if (m.any()) {
            for (Link l : links(g, p, m, Situation.FIGHT)) {
                out.add(l.to());
            }
        }
        return out;
    }

    /** Feet-to-feet distance between two standing cells. */
    static double distance(Grid g, Pos a, Pos b) {
        double ya = personY(g, a), yb = personY(g, b);
        return Math.sqrt(sq(a.x() - b.x()) + sq((Double.isNaN(ya) ? a.y() : ya) - (Double.isNaN(yb) ? b.y() : yb))
                + sq(a.z() - b.z()));
    }

    /**
     * The distance from a standing cell to a target, letting the mover tower up to 2 blocks in place first when the
     * target is above it and the cells over its head are open (an ordinary person's tower of 5.1.15).
     */
    static double reachDistance(Grid g, Pos from, Pos target) {
        double d = distance(g, from, target);
        double fy = personY(g, from), ty = personY(g, target);
        if (Double.isNaN(fy) || Double.isNaN(ty) || ty <= fy || !Double.isNaN(fy) && Double.isNaN(standY(g, from))) {
            return d; // no tower from the water, nor toward something lower
        }
        int up = 0;
        while (up < PERSON_TOWER && at(g, from.x(), from.y() + 2 + up, from.z()) == Cell.AIR) {
            up++;
        }
        double dy = Math.max(0, ty - fy - up);
        return Math.sqrt(sq(from.x() - target.x()) + sq(dy) + sq(from.z() - target.z()));
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * Whether a mover starting at {@code from} can get within melee reach of {@code target} (a standable cell within
     * 3 blocks of it, its own cell excluded unless {@code mayStandOn}) by its own moves, within the search bounds.
     */
    static boolean canReach(Grid g, Pos from, Profile m, Pos target, boolean mayStandOn) {
        ArrayDeque<Pos> q = new ArrayDeque<>();
        Set<Pos> seen = new HashSet<>();
        q.add(from);
        seen.add(from);
        while (!q.isEmpty() && seen.size() <= SEARCH_NODES) {
            Pos c = q.poll();
            if ((mayStandOn || !c.equals(target)) && reachDistance(g, c, target) <= MELEE_REACH + EPS) {
                return true;
            }
            for (Pos n : steps(g, c, m)) {
                if (Math.abs(n.x() - from.x()) <= SEARCH_RADIUS && Math.abs(n.z() - from.z()) <= SEARCH_RADIUS
                        && seen.add(n)) {
                    q.add(n);
                }
            }
        }
        return false;
    }

    /**
     * Whether any engaged enemy can get within melee reach of the hero standing at {@code hero} as it moves: the
     * fight-reach clock's ground input (5.1.15). With no engaged enemy the hero is never out of reach.
     */
    public static boolean anyEnemyReaches(Grid g, Pos hero, List<Enemy> engaged) {
        if (engaged.isEmpty()) {
            return true;
        }
        for (Enemy e : engaged) {
            if (canReach(g, e.at(), e.mobility(), hero, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether taking a link in a fight puts the hero where no engaged enemy can reach it, which starts the 4 s
     * reach clock ({@link BossRules.ReachClock}); links toward an enemy, out of a pit, or back to ground an enemy
     * stands on do not. While escaping the clock never runs, so this is not asked.
     */
    public static boolean leavesEnemiesBehind(Grid g, Link l, List<Enemy> engaged) {
        return !anyEnemyReaches(g, l.to(), engaged);
    }

    // ================================================================== the strike from a link (3.7)

    /** Where a basic attack lands from: clinging below the target's feet, a jump's apex, or flying beside it. */
    public enum StrikeFrom { CLING, APEX, AIR }

    /**
     * Whether a standable cell for the hero lies within melee reach of the target that the hero can get to (the
     * target's own cell does not count): a 1x1 pillar top leaves none, a ledge with room beside the target does.
     */
    public static boolean standableInReach(Grid g, Pos hero, Profile m, Pos target) {
        return canReach(g, hero, m, target, false);
    }

    /**
     * The strike from a link (3.7): only a basic attack, never a move, and only when the hero has no standable cell
     * within melee reach of its target.
     */
    public static boolean mayStrikeFromLink(boolean basicAttack, boolean standableInReach) {
        return basicAttack && !standableInReach;
    }

    /**
     * Where the hero's basic attack may land from, for a target whose feet are {@code above} blocks over the ground
     * the hero stands on beside it (null: none of its mobility holds it in reach): a climber that climbs to just
     * below the target's feet, a flyer beside it, a leaper or acrobat whose apex brings the target within reach.
     */
    public static StrikeFrom strikeFrom(Profile m, double above) {
        if (m.has(Feature.CLIMB) && m.climbHeight() + EPS >= Math.ceil(above - EPS) - 1) {
            return StrikeFrom.CLING;
        }
        if (m.flies()) {
            return StrikeFrom.AIR;
        }
        double apex = m.has(Feature.AIR_JUMP) || !Double.isNaN(m.raw(Stat.JUMP_HEIGHT)) ? m.reachUp() : 0;
        if (apex > 0 && Math.hypot(1, Math.max(0, above - apex)) <= MELEE_REACH + EPS) {
            return StrikeFrom.APEX;
        }
        return null;
    }

    /** A climber II-III may instead break the block under the target's feet at player speed, while clinging. */
    public static boolean mayUndermineFromCling(Profile m) {
        return m.has(Feature.WORK_CLINGING);
    }
}
