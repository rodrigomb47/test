package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.MoveClass;

/**
 * A hero's mobility in a fight (MOBILITY_AND_WORK 3.7, 5.1, 5.2 and 7.2-7.5; task A12), the part that needs no world:
 * the aerial windows of a flyer (7.4: the take-off chosen like a move, at most 3 light ranged moves in the air, the
 * {@code dive} and its touch-and-go string, the landing and the ground time), the dive's straight line, what the
 * fight-reach clock makes a hero do when it fires (5.1.6, 5.1.15: come back), the reach flight of a flyer outside its
 * aerial phases (3.7), the basic attack from a link on a target that left no standable cell beside it, the default
 * answer of a flyer to a drawn bow (7.5), and the defenders' answer to an intruding hero (5.2: archers take an airborne
 * hero first, the alarm box 16 blocks up, the approach seen from 24 blocks). Nothing here reads whether anyone is a
 * player: the enemies engaged, the target and the pursuers are of any kind. Pure, unit-tested
 * ({@code FightMobilityTest}); {@code hero/HeroFightMobility} applies it.
 */
public final class FightMobility {
    private FightMobility() {
    }

    private static final double EPS = 1e-9;

    // ================================================================== aerial windows (7.4)

    /** A take-off's weight when the file gives none (8.1 {@code aerial.weight}). */
    public static final int TAKE_OFF_WEIGHT_DEFAULT = 4;
    /** At most this many light ranged moves per take-off (5.1.5). */
    public static final int AIR_LIGHT_MOVES = 3;
    /** A touch-and-go between two dives of one window: 0.5 s on the ground (7.3). */
    public static final int TOUCH_DOWN_TICKS = 10;
    /** After the last dive, at least this long on the ground (the punish window, 7.3). */
    public static final int RECOVERY_MIN_TICKS = 20;
    /** In a window it hovers this far above its target's feet (under the fight's ceiling) and this far off it. */
    public static final double HOVER_ABOVE = 4, HOVER_DISTANCE = 4;
    /** A flyer's default reaction (7.5): a drawn bow while it is in the air: it lands or dives, 60%, once in 8 s. */
    public static final int BOW_ANSWER_CHANCE = 60, BOW_ANSWER_COOLDOWN_TICKS = 8 * BossRules.TPS;
    /** The take-off headroom an aerial window needs (3.5; the arena sample: under 4 blocks, no window). */
    public static final int AERIAL_HEADROOM = MobilityRules.TAKE_OFF_HEADROOM;

    /** Whether the aerial block lets the hero take to the air in this phase (null: no aerial windows). */
    public static boolean aerialPhase(HeroDef.Aerial a, int phase) {
        return a != null && a.phases().contains(phase);
    }

    /** The take-off's weight among the moves: the file's (the reader gives 4 when it names none; 0 never takes off). */
    public static double takeOffWeight(HeroDef.Aerial a) {
        return a == null ? 0 : Math.max(0, a.weight());
    }

    /**
     * The take-off chosen like a move (7.4): against the summed score of the moves eligible now, by weight; with
     * {@code roll} in [0, 1). A weight of 0 never takes off; with no move eligible it always does.
     */
    public static boolean takeOffChosen(double weight, double movesScore, double roll) {
        if (weight <= 0) {
            return false;
        }
        return roll * (weight + Math.max(0, movesScore)) < weight;
    }

    /**
     * What may start in the air (5.1.5): a light ranged move ({@code shot}, {@code volley}, {@code bolt},
     * {@code barrage}), at most 3 per take-off, and the wind-up of a telegraphed {@code dive} or {@code rain}. Nothing
     * else: melee is for the ground (or a dive).
     */
    public static boolean airMoveAllowed(Form f, MoveClass c, int lightsUsed) {
        return airMoveAllowed(f, c, lightsUsed, false);
    }

    /**
     * The same, on a reach flight too: a flyer outside its aerial phases flies in a fight only to reach a target no walk
     * reaches, with no moves in the air and its basic attack only (MOBILITY_AND_WORK 3.7 and 7.4, which narrow 5.1.5 for
     * that flight: the rule of the 2026-11-19 live check, where Ambrose wound up Great Brew on a reach flight).
     */
    public static boolean airMoveAllowed(Form f, MoveClass c, int lightsUsed, boolean reachFlight) {
        if (reachFlight) {
            return false;
        }
        if (f == Form.DIVE || f == Form.RAIN) {
            return true;
        }
        boolean ranged = f == Form.SHOT || f == Form.VOLLEY || f == Form.BOLT || f == Form.BARRAGE;
        return ranged && c == MoveClass.LIGHT && lightsUsed < AIR_LIGHT_MOVES;
    }

    /** Whether a move counts toward the 3 light ranged moves of a take-off. */
    public static boolean countsAsAirLight(Form f) {
        return f == Form.SHOT || f == Form.VOLLEY || f == Form.BOLT || f == Form.BARRAGE;
    }

    /** What follows a dive's impact. */
    public enum AfterDive { TOUCH_AND_GO, LAND }

    /**
     * After a dive's impact (7.3, 7.4): a touch-and-go (0.5 s on the ground, then up again for the next dive) while
     * the window has dives left ({@code dives} of the file, 1-3), the air budget has at least a second left and the
     * stamina is over its take-off share; otherwise it stays down, with the full recovery after this last dive.
     */
    public static AfterDive afterDive(int divesUsed, int divesAllowed, double airSecondsLeft, double staminaShare,
                                      double takeOffShare) {
        if (divesUsed < Math.max(1, divesAllowed) && airSecondsLeft >= 1 - EPS && staminaShare + EPS >= takeOffShare) {
            return AfterDive.TOUCH_AND_GO;
        }
        return AfterDive.LAND;
    }

    /**
     * One aerial window of a flyer (7.4): from the take-off to the landing. It counts the light ranged moves and the
     * dives it played; the air budget, the ceiling and the ground time are {@link MobilityRules.AirBudget}'s.
     */
    public static final class Window {
        private boolean open;
        private int lights, dives;
        private long openedAt = Long.MIN_VALUE / 4;

        public void open(long now) {
            open = true;
            lights = 0;
            dives = 0;
            openedAt = now;
        }

        public void close() {
            open = false;
        }

        public boolean isOpen() {
            return open;
        }

        public long openedAt() {
            return openedAt;
        }

        /** A move started in the air: a light ranged one counts toward the 3, a dive toward the window's dives. */
        public void moveStarted(Form f) {
            if (!open) {
                return;
            }
            if (f == Form.DIVE) {
                dives++;
            } else if (countsAsAirLight(f)) {
                lights++;
            }
        }

        public int lights() {
            return open ? lights : 0;
        }

        public int dives() {
            return open ? dives : 0;
        }
    }

    // ================================================================== the dive (7.3)

    /** A dive flies its straight line at this speed (blocks a tick: 18 b/s) and gives up after this long. */
    public static final double DIVE_SPEED = 0.9;
    public static final int DIVE_TIMEOUT = 40;
    /** It has come down when it is this close to its marker. */
    public static final double DIVE_ARRIVED = 0.6;

    /**
     * The dive's velocity for one tick (7.3: pushed along a straight line to the marker, stopped by blocks): toward
     * the marker at {@link #DIVE_SPEED}, never past it. {@code out} gets {x, y, z}.
     */
    public static void diveVelocity(double dx, double dy, double dz, double[] out) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double s = d < EPS ? 0 : Math.min(DIVE_SPEED, d) / d;
        out[0] = dx * s;
        out[1] = dy * s;
        out[2] = dz * s;
    }

    /**
     * Ticks from a jump of vertical speed {@code v} (blocks a tick) to its apex on vanilla's air physics (gravity 0.08,
     * drag 0.98): an apex dive jumps this long before its wind-up ends.
     */
    public static int apexTicks(double v) {
        int n = 0;
        while (v > 0 && n < 40) {
            v = (v - LeapSolver.GRAVITY) * LeapSolver.VERTICAL_DRAG;
            n++;
        }
        return n;
    }

    /** The dive's marker over a range: its target's feet, or the farthest point of its range toward them. */
    public static double[] diveMarker(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                                      double range) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= range + EPS || d < EPS) {
            return new double[]{toX, toY, toZ};
        }
        double k = range / d;
        return new double[]{fromX + dx * k, fromY + dy * k, fromZ + dz * k};
    }

    // ================================================================== the fight-reach clock (5.1.6, 5.1.15)

    /** What the hero does when its reach clock fires. */
    public enum ComeBack { NONE, LAND, RETURN }

    /** It walks back toward its nearest engaged enemy at most this long after the clock fired. */
    public static final int RETURN_TICKS = 5 * BossRules.TPS;

    /**
     * The clock fired (4 s where no engaged enemy can reach it, out of a telegraphed wind-up): in the air it lands
     * (a flyer's flight ends; a glider or a leaper is already coming down), on ground no enemy reaches it goes back
     * toward them. Never while escaping (the clock does not run then).
     */
    public static ComeBack comeBack(boolean fired, boolean escaping, boolean flying) {
        if (!fired || escaping) {
            return ComeBack.NONE;
        }
        return flying ? ComeBack.LAND : ComeBack.RETURN;
    }

    // ================================================================== reach flights and strikes from a link (3.7)

    /**
     * A reach flight (3.7): a flyer outside its aerial phases takes off in a fight only to reach a target no walk or
     * other link reaches, a target standing on a block (one in the air is waited for), when it may take off.
     */
    public static boolean reachFlight(boolean flies, boolean aerialPhase, boolean reachedOnFoot, boolean targetStanding,
                                      boolean mayTakeOff) {
        return flies && !aerialPhase && !reachedOnFoot && targetStanding && mayTakeOff;
    }

    /** A reach flight holds beside its target this far off (horizontal) and level with its feet plus this. */
    public static final double REACH_BESIDE = 1.6, REACH_ABOVE_FEET = 0.4;

    /** A basic attack from a link lands at most once in this long (a soldier's melee interval). */
    public static final int LINK_STRIKE_TICKS = 20;

    /**
     * Whether the basic attack may land from where the hero's mobility holds it (3.7): never a move, only a basic
     * attack, only on a target that left no standable cell within melee reach (a 1x1 pillar top), within reach and
     * in sight, and only from a cling, a jump's apex or the air.
     */
    public static boolean strikeFromLink(boolean offTheGround, boolean standableInReach, double distance, double reach,
                                         boolean sight) {
        return offTheGround && !standableInReach && distance <= reach + EPS && sight;
    }

    // ================================================================== the defenders (5.2)

    /** An archer keeps an airborne hero as its target at least this long (so it does not switch at every landing). */
    public static final int AIRBORNE_TARGET_HOLD = 3 * BossRules.TPS;
    /** The defence alarm's box reaches this far up from the base's centre (12 before A12: a flyer over the wall counts). */
    public static final int ALARM_UP = 16;
    /** A base NPC that sees an enemy hero this close raises the alarm before it reaches the walls. */
    public static final double APPROACH_SIGHT = 24;

    /**
     * An archer's target order (5.2): an enemy hero in the air first, then an intruding hero on the walls or inside,
     * then the rest; higher is first.
     */
    public static int archerPriority(boolean hero, boolean airborne, boolean intruding) {
        if (!hero) {
            return 0;
        }
        return airborne ? 2 : intruding ? 1 : 0;
    }

    /**
     * Whether a soldier waits under an enemy instead of planning toward it (3.8): an enemy hero that is airborne,
     * gliding or flying (its {@code DATA_LOCO} mode); a soldier never pillars toward something moving in the air.
     */
    public static boolean holdUnder(boolean hero, int locoMode) {
        return hero && (locoMode == MovementRules.AIRBORNE || locoMode == MovementRules.GLIDE
                || locoMode == MovementRules.FLY || locoMode == MovementRules.FLY_HOVER || locoMode == MovementRules.RIDE);
    }
}
