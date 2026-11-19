package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Knockback;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Pull;

import java.util.Arrays;
import java.util.List;

/**
 * Terrain is a weapon (HEROES_V2 6.17, the owner's answer of 2026-11-18: "its fine for heroes abilities to push someone
 * of a cliff or to lava"; task T1). A hero's pushes land at full strength wherever they send an enemy, and its AI aims
 * them at edges on purpose:
 *
 * <ul>
 * <li><b>Edges</b>, sampled every 2 s round the hero's target in {@link #DIRECTIONS} directions out to {@link #REACH}
 * blocks (a {@link Ring}): <i>lethal</i> ({@link #LETHAL}) is lava, the void, or a drop whose fall damage would take at
 * least half the receiver's health left; <i>harmful</i> ({@link #HARMFUL}) is fire, a magma block or a campfire, powder
 * snow, cactus or a berry bush, a shorter drop past the receiver's safe fall, and water against a hero with a
 * {@code water} weak point. What the receiver cannot be hurt by is no edge for it ({@link #value}): lava and fire for
 * a fire-proof one, drops for one with no fall damage or a soft landing (Slow Falling, a glider, a flyer), though the
 * void still counts as harmful for those. The same reading for a player, an NPC, a hero and a monster: a
 * {@link Receiver} has no "is a player" field.</li>
 * <li><b>The pick</b> ({@link #pickFactor}): a move that pushes ({@link #push}: a knockback of 1 or more, a grab, a
 * pull) whose push would carry its target to an edge gets x(1 + the edge's value) on its score: up to x2.</li>
 * <li><b>The approach</b> ({@link #approach}, {@link Walk}): with such a move ready within 2 s, the hero walks round to the
 * target's far side, so the target stands between it and the edge, at the move's reach; if it is not there within 3 s
 * it picks as usual and leaves that target alone for 10 s.</li>
 * <li><b>The aim, by form</b>: a knockback, a wave or a nova pushes away from the hero, so the approach aims it; a charge
 * or a dash runs its line through the target toward the edge (the hero stops short of it, as every dash does); a grab
 * throws toward the nearest lethal edge in its throw's reach, in the order of {@link FormGeometry#THROW_TRIES}, then a
 * harmful one, else straight ahead ({@link #throwTry}); a pull is picked when an edge lies between the hero and its
 * target.</li>
 * </ul>
 *
 * <p>No shorter tells: nothing here changes a wind-up, a decal or a sound. Pure Java, unit-tested ({@code EdgePlayTest}).
 */
public final class EdgePlay {
    private EdgePlay() {
    }

    /** Directions sampled round the target (22.5 degrees apart: direction i points at angle i x 22.5 from +x to +z). */
    public static final int DIRECTIONS = 16;
    /** Blocks sampled out from the target in each direction. */
    public static final int REACH = 8;
    /** The ring is sampled every 2 s, with the arena (6.12), and again when the target moved away from its centre. */
    public static final int SAMPLE_TICKS = 40;
    public static final double RESAMPLE_MOVE = 1.5;
    /** An edge's value: lethal x2 on the score, harmful x1.5. */
    public static final double LETHAL = 1, HARMFUL = 0.5;
    /** A push move ready within this many ticks makes the hero walk round (2 s). */
    public static final int READY_TICKS = 40;
    /** The walk round gets this long to arrive (3 s), and a target it gave up on is left alone this long (10 s). */
    public static final int APPROACH_TICKS = 60, GIVE_UP_TICKS = 200;
    /** The hero stands right when its line to the target is within half a direction's sector (11.25 degrees) of the edge's. */
    public static final double ALIGNED_DEGREES = 180.0 / DIRECTIONS;
    /** The walk round stands this far from the target (the move's reach less half a block, within these). */
    public static final double STAND_MIN = 2, STAND_MAX = 4;
    /** A push's lift adds about this much to a drop's fall (vanilla's knockback lifts 0.4 a tick from the ground). */
    public static final double PUSH_APEX = 1;
    /** A knockback counts as a push from this strength (6.17: "a knockback of 1 or more"). */
    public static final double MIN_PUSH = 1;
    /** A grab throws with this strength when its move names none (A5). */
    public static final double DEFAULT_THROW = 2;

    // ------------------------------------------------------------------ edges

    /** What lies where a push could carry the receiver (the game reads it from the blocks). */
    public enum Ground { FLOOR, WALL, LAVA, FIRE, POWDER_SNOW, THORNS, WATER, VOID }

    /**
     * What a receiver can be hurt by, read the same way whoever it is: its health left, vanilla's safe fall and fall
     * damage multiplier, fire-proof (fire-immune or an active Fire Resistance), a soft landing (Slow Falling, a glider or a
     * flyer, which glides as a glider of its tier), whether it freezes in powder snow, a {@code water} weak point, and its
     * knockback resistance (0-1).
     */
    public record Receiver(double health, double safeFall, double fallMultiplier, boolean fireproof, boolean softLanding,
                           boolean freezes, boolean waterWeak, double knockbackResistance) {
        /** A plain receiver with vanilla's numbers (tests). */
        public static Receiver plain(double health) {
            return new Receiver(health, 3, 1, false, false, true, false, 0);
        }
    }

    /** Vanilla's fall damage for a drop of {@code drop} blocks after a push (its lift included). */
    public static double fallDamage(double drop, Receiver r) {
        if (r.softLanding() || drop <= 0) {
            return 0;
        }
        return Math.max(0, Math.ceil((drop + PUSH_APEX - r.safeFall()) * Math.max(0, r.fallMultiplier()) - 1e-9));
    }

    /**
     * An edge's value for {@code r}: {@link #LETHAL}, {@link #HARMFUL} or 0 (none for it). A ground's own hazard and a
     * drop of {@code drop} blocks onto it both count; the worse wins. Water takes the fall (no fall damage).
     */
    public static double value(Ground g, double drop, Receiver r) {
        double ground = switch (g) {
            case LAVA -> r.fireproof() ? 0 : LETHAL;
            case FIRE -> r.fireproof() ? 0 : HARMFUL;
            case POWDER_SNOW -> r.freezes() ? HARMFUL : 0;
            case THORNS -> HARMFUL;
            case WATER -> r.waterWeak() ? HARMFUL : 0;
            case VOID -> r.softLanding() || r.fallMultiplier() <= 0 ? HARMFUL : LETHAL;
            default -> 0;
        };
        if (g == Ground.VOID || g == Ground.WATER || g == Ground.WALL) {
            return ground;
        }
        double dmg = fallDamage(drop, r);
        double fall = dmg <= 0 ? 0 : dmg >= r.health() / 2 ? LETHAL : HARMFUL;
        return Math.max(ground, fall);
    }

    /** The direction index nearest the horizontal direction (dx, dz). */
    public static int sector(double dx, double dz) {
        double a = Math.atan2(dz, dx);
        int i = (int) Math.round(a / (2 * Math.PI / DIRECTIONS));
        return Math.floorMod(i, DIRECTIONS);
    }

    /** The direction index nearest (dx, dz) turned by {@code deg} degrees (as {@link FormGeometry#turn}; no allocation). */
    public static int turnedSector(double dx, double dz, double deg) {
        double a = Math.atan2(dz, dx) + Math.toRadians(deg);
        int i = (int) Math.round(a / (2 * Math.PI / DIRECTIONS));
        return Math.floorMod(i, DIRECTIONS);
    }

    public static double dirX(int i) {
        return Math.cos(i * 2 * Math.PI / DIRECTIONS);
    }

    public static double dirZ(int i) {
        return Math.sin(i * 2 * Math.PI / DIRECTIONS);
    }

    /** A push lands this close to its distance: an edge counts when it lies within this much of where the push ends. */
    public static final double LAND_SLACK = 0.5;

    /**
     * The edges round one receiver: in each direction the nearest lethal edge and the nearest harmful one, each with how
     * far it runs ({@code near} to {@code far}: a pool's far side; infinite for a drop or the void, where whatever passes
     * the edge goes down), none within {@link #REACH} infinite. A push counts an edge when its landing falls in it or past
     * a drop ({@link #valueWithin}): a small pool a strong push flies over is no edge for that push. Reused from sample to
     * sample (nothing is allocated).
     */
    public static final class Ring {
        private final double[] lethal = new double[DIRECTIONS], harmful = new double[DIRECTIONS];
        private final double[] lethalFar = new double[DIRECTIONS], harmfulFar = new double[DIRECTIONS];
        public double centreX, centreY, centreZ;
        public long sampledAt = Long.MIN_VALUE / 4;
        public int receiverId = Integer.MIN_VALUE;

        public Ring() {
            clear();
        }

        public Ring clear() {
            Arrays.fill(lethal, Double.POSITIVE_INFINITY);
            Arrays.fill(harmful, Double.POSITIVE_INFINITY);
            Arrays.fill(lethalFar, Double.POSITIVE_INFINITY);
            Arrays.fill(harmfulFar, Double.POSITIVE_INFINITY);
            return this;
        }

        /** Records a drop-like edge of {@code value} at {@code distance} blocks in direction {@code dir} (the nearest is kept). */
        public Ring edge(int dir, double distance, double value) {
            return edge(dir, distance, Double.POSITIVE_INFINITY, value);
        }

        /** Records an edge from {@code near} to {@code far} (a pool's extent; infinite for a drop), the nearest kept. */
        public Ring edge(int dir, double near, double far, double value) {
            int i = Math.floorMod(dir, DIRECTIONS);
            if (value >= LETHAL) {
                if (near < lethal[i]) {
                    lethal[i] = near;
                    lethalFar[i] = Math.max(near, far);
                }
            } else if (value >= HARMFUL && near < harmful[i]) {
                harmful[i] = near;
                harmfulFar[i] = Math.max(near, far);
            }
            return this;
        }

        public double lethal(int dir) {
            return lethal[Math.floorMod(dir, DIRECTIONS)];
        }

        public double harmful(int dir) {
            return harmful[Math.floorMod(dir, DIRECTIONS)];
        }

        /**
         * The best edge value a push of {@code blocks} in direction {@code dir} lands in (0: none): it reaches the edge
         * and does not fly past a pool's far side ({@link #LAND_SLACK} either way).
         */
        public double valueWithin(int dir, double blocks) {
            int i = Math.floorMod(dir, DIRECTIONS);
            if (lethal[i] <= blocks + LAND_SLACK && lethalFar[i] >= blocks - LAND_SLACK) {
                return LETHAL;
            }
            return harmful[i] <= blocks + LAND_SLACK && harmfulFar[i] >= blocks - LAND_SLACK ? HARMFUL : 0;
        }

        /** The distance to the edge a push of {@code blocks} in {@code dir} lands in (infinite: none). */
        public double landedEdge(int dir, double blocks) {
            int i = Math.floorMod(dir, DIRECTIONS);
            double v = valueWithin(i, blocks);
            return v >= LETHAL ? lethal[i] : v > 0 ? harmful[i] : Double.POSITIVE_INFINITY;
        }

        /** Whether any edge was found. */
        public boolean any() {
            for (int i = 0; i < DIRECTIONS; i++) {
                if (lethal[i] < Double.POSITIVE_INFINITY || harmful[i] < Double.POSITIVE_INFINITY) {
                    return true;
                }
            }
            return false;
        }

        /** Whether it should be sampled again: older than 2 s, another receiver, or the receiver moved away. */
        public boolean stale(long now, int receiver, double x, double z) {
            if (receiver != receiverId || now - sampledAt >= SAMPLE_TICKS) {
                return true;
            }
            double dx = x - centreX, dz = z - centreZ;
            return dx * dx + dz * dz > RESAMPLE_MOVE * RESAMPLE_MOVE;
        }
    }

    // ------------------------------------------------------------------ pushes

    /** How a move pushes its target: away from the hero, toward it (a pull), any way (a grab's throw), or not at all. */
    public enum PushKind { NONE, AWAY, TOWARD, ANY }

    /** A move's push: its kind, its strength (a knockback's, a pull's or a throw's), its lift and its reach. */
    public record Push(PushKind kind, double strength, double up, double reach) {
        public static final Push NONE = new Push(PushKind.NONE, 0, 0, 0);

        /**
         * How far it carries a receiver with knockback resistance {@code resistance} standing {@code distance} from the
         * hero, blocks: a knockback by {@link FormGeometry#knockbackDistance}, a throw by its arc, a pull two blocks a
         * point and never closer than 1.5 to the hero.
         */
        public double blocks(double resistance, double distance) {
            double keep = 1 - Math.max(0, Math.min(1, resistance));
            return switch (kind) {
                case AWAY -> FormGeometry.knockbackDistance(strength, resistance);
                case ANY -> FormGeometry.throwDistance(strength, up) * keep;
                case TOWARD -> Math.min(FormGeometry.PULL_BLOCKS_PER_STRENGTH * strength,
                        Math.max(0, distance - FormGeometry.PULL_STOP)) * keep;
                case NONE -> 0;
            };
        }
    }

    /**
     * A move's push (6.17 "the pick"): a grab throws any way; a pull draws toward the hero; any other move with a
     * knockback of {@link #MIN_PUSH} or more (on its own effects or any strike's) pushes away from the hero, whatever its
     * form (a melee blow, a charge, a dash, a wave, a nova, a leap's landing). Anything else does not push.
     */
    public static Push push(Move m) {
        double reach = MoveSelector.reach(m);
        if (m.form() == Form.GRAB) {
            double s = m.extra().throwStrength() > 0 ? m.extra().throwStrength() : DEFAULT_THROW;
            return new Push(PushKind.ANY, s, m.extra().up(), reach);
        }
        double kb = 0, pull = 0;
        for (List<Effect> l : m.effectLists()) {
            for (Effect e : l) {
                if (e instanceof Knockback k) {
                    kb = Math.max(kb, k.strength());
                } else if (e instanceof Pull p) {
                    pull = Math.max(pull, p.strength());
                }
            }
        }
        if (kb >= MIN_PUSH) {
            return new Push(PushKind.AWAY, kb, 0, reach);
        }
        if (pull > 0) {
            return new Push(PushKind.TOWARD, pull, 0, reach);
        }
        return Push.NONE;
    }

    /**
     * The pick's factor for a move with push {@code p} (6.17 1): 1 + the value of the edge its push would carry the
     * target to, from where the hero stands now ({@code ux, uz}: the unit direction from the hero to its target,
     * {@code distance} apart, the target's knockback resistance {@code resistance}); 1 with no ring.
     */
    public static double pickFactor(Push p, Ring ring, double ux, double uz, double distance, double resistance) {
        if (ring == null || p == null || p.kind() == PushKind.NONE) {
            return 1;
        }
        double blocks = p.blocks(resistance, distance);
        double v = switch (p.kind()) {
            case AWAY -> ring.valueWithin(sector(ux, uz), blocks);
            case TOWARD -> {
                // a pull: the edge lies between the target and the hero, inside the pull
                int d = sector(-ux, -uz);
                double reach = Math.min(blocks, distance - 1);
                yield ring.lethal(d) <= reach ? LETHAL : ring.harmful(d) <= reach ? HARMFUL : 0;
            }
            case ANY -> {
                double best = 0;
                for (double deg : FormGeometry.THROW_TRIES) {
                    best = Math.max(best, ring.valueWithin(turnedSector(ux, uz, deg), blocks));
                }
                yield best;
            }
            case NONE -> 0;
        };
        return 1 + v;
    }

    /**
     * A grab's throw (6.17 3, A5's order of tries, edges first): the index in {@link FormGeometry#THROW_TRIES} (turned
     * from the hero's facing {@code fx, fz}) of the try whose line reaches the nearest lethal edge within
     * {@code blocks}, else the nearest harmful one, the earlier try on a tie; 0 (straight ahead) with none. The ring is
     * the receiver's, sampled where it is held.
     */
    public static int throwTry(Ring ring, double fx, double fz, double blocks) {
        if (ring == null) {
            return 0;
        }
        int best = -1;
        double bestValue = 0, bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < FormGeometry.THROW_TRIES.length; i++) {
            int s = turnedSector(fx, fz, FormGeometry.THROW_TRIES[i]);
            double v = ring.valueWithin(s, blocks);
            if (v <= 0) {
                continue;
            }
            double dist = ring.landedEdge(s, blocks);
            if (v > bestValue || v == bestValue && dist < bestDist - 1e-9) {
                best = i;
                bestValue = v;
                bestDist = dist;
            }
        }
        return Math.max(0, best);
    }

    // ------------------------------------------------------------------ the approach

    /**
     * Where the hero should stand for a push of {@code blocks} that pushes away from it: {@code (dx, dz)} from the
     * target, on the far side from the edge in direction {@code dir}, {@code value} the edge's.
     */
    public record Spot(double dx, double dz, int dir, double value) {
    }

    /** How far from the target the walk round stands for a move of reach {@code reach}. */
    public static double standOff(double reach) {
        double r = Double.isFinite(reach) ? reach - 0.5 : STAND_MAX;
        return Math.max(STAND_MIN, Math.min(STAND_MAX, r));
    }

    /**
     * 6.17 2: the spot for the walk round. Among the directions with an edge within {@code blocks} of the target (not
     * one {@code skip} rejects), the best value first, then the one nearest the hero ({@code heroDx, heroDz}: the hero
     * from the target), so the hero walks the least; the spot stands {@link #standOff} from the target on the far side.
     * Null with no edge in reach.
     */
    public static Spot approach(Ring ring, double blocks, double reach, double heroDx, double heroDz,
                                java.util.function.IntPredicate skip) {
        if (ring == null) {
            return null;
        }
        double stand = standOff(reach);
        Spot best = null;
        double bestWalk = Double.POSITIVE_INFINITY;
        for (int i = 0; i < DIRECTIONS; i++) {
            double v = ring.valueWithin(i, blocks);
            if (v <= 0 || skip != null && skip.test(i)) {
                continue;
            }
            double sx = -dirX(i) * stand, sz = -dirZ(i) * stand;
            double walk = Math.hypot(sx - heroDx, sz - heroDz);
            if (best == null || v > best.value() || v == best.value() && walk < bestWalk - 1e-9) {
                best = new Spot(sx, sz, i, v);
                bestWalk = walk;
            }
        }
        return best;
    }

    /**
     * Whether the hero already stands right for an edge in direction {@code dir}: its line to the target ({@code ux,
     * uz}, from the hero) falls in that direction's sector (within {@link #ALIGNED_DEGREES}), the same reading the pick's
     * factor makes, so a hero that stands right is one whose push the pick doubles.
     */
    public static boolean aligned(double ux, double uz, int dir) {
        if (Math.hypot(ux, uz) < 1e-9) {
            return false;
        }
        return sector(ux, uz) == Math.floorMod(dir, DIRECTIONS);
    }

    /** What the walk round does at one check. */
    public enum Step { NONE, ALIGNED, WALK, GAVE_UP }

    /**
     * The walk round's clock (6.17 2): it starts when the hero begins to walk round a target, and if the hero is not
     * standing right within {@link #APPROACH_TICKS} it gives up and leaves that target alone for {@link #GIVE_UP_TICKS},
     * so a fight never turns into circling.
     */
    public static final class Walk {
        private int target = Integer.MIN_VALUE;
        private long started;
        private int dir = -1;
        private int blockedTarget = Integer.MIN_VALUE;
        private long blockedUntil = Long.MIN_VALUE / 4;

        /**
         * One check against target {@code targetId}: {@code spot} the edge to aim at (null: none in reach, or no push
         * move ready), {@code aligned} whether the hero stands right for it.
         */
        public Step step(long now, int targetId, Spot spot, boolean aligned) {
            if (blocked(now, targetId) || spot == null) {
                reset();
                return Step.NONE;
            }
            if (aligned) {
                reset();
                return Step.ALIGNED;
            }
            if (target != targetId) {
                target = targetId;
                started = now;
            }
            dir = spot.dir();
            if (now - started >= APPROACH_TICKS) {
                blockedTarget = targetId;
                blockedUntil = now + GIVE_UP_TICKS;
                reset();
                return Step.GAVE_UP;
            }
            return Step.WALK;
        }

        /** Whether the walk round leaves {@code targetId} alone now (it gave up on it within 10 s). */
        public boolean blocked(long now, int targetId) {
            return targetId == blockedTarget && now < blockedUntil;
        }

        /** Whether a walk round is under way. */
        public boolean walking() {
            return target != Integer.MIN_VALUE;
        }

        /** The edge direction it walks for (-1: none). */
        public int dir() {
            return dir;
        }

        public void reset() {
            target = Integer.MIN_VALUE;
            dir = -1;
        }
    }
}
