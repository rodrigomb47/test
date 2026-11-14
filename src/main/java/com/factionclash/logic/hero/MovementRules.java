package com.factionclash.logic.hero;

/**
 * The per-tick rules of a hero's physical movement (MOBILITY_AND_WORK 1.6-1.9, 2.2, 2.4, 4.2 and 5.3; task A10), the
 * part of the mobility engine that needs no world: the air poise each counter adds, what downs a hero at once, the
 * glide's and the flight's velocity for one tick, the landing a hero plays, the wall-kick rule, the bursts' clock and
 * the {@code DATA_LOCO} one-shot bookkeeping. {@code hero/HeroMovement} applies them to the entity. Nothing here reads
 * whether anyone is a player: a counter adds the same air poise whoever uses it (5.3). Pure, unit-tested
 * ({@code MovementRulesTest}).
 */
public final class MovementRules {
    private MovementRules() {
    }

    /** A game tick in seconds. */
    public static final double TICK = 1.0 / BossRules.TPS;

    // ================================================================== air poise (2.4, 5.3)

    /** What hit an airborne hero, with the air poise it adds (5.3), the same whoever used it. */
    public enum AirHit {
        /** a bow's arrow (and a hero's own projectile): 5 arrows down a flyer */
        ARROW(20),
        /** a crossbow bolt */
        BOLT(30),
        /** one of a Multishot crossbow's side bolts */
        MULTISHOT_BOLT(15),
        /** a thrown trident */
        TRIDENT(40),
        /** a wind charge (it also ends a glide) */
        WIND_CHARGE(35),
        /** a snowball or an egg */
        SNOWBALL(10),
        /** a fishing rod's hook */
        FISHING_HOOK(15),
        /** a melee blow on a low flyer (within 3 blocks up) */
        MELEE(25),
        /** a critical melee blow on a low flyer */
        MELEE_CRIT(40);

        public final double poise;

        AirHit(double poise) {
            this.poise = poise;
        }
    }

    /** A melee blow counts for air poise when the hero's feet are at most this far above the attacker's. */
    public static final double LOW_FLYER = 3;

    /** Whether a melee blow from feet at {@code attackerFeetY} reaches a hero in the air at {@code heroFeetY} (5.3). */
    public static boolean lowFlyer(double heroFeetY, double attackerFeetY) {
        return heroFeetY - attackerFeetY <= LOW_FLYER + 1e-9;
    }

    /**
     * Whether an airborne hero is downed at once (2.4): a stun, a freeze, Slowness II or worse ({@code amplifier} 1
     * or more; -1 for none) or a lightning strike. Slowness I only halves a flyer's climb rate.
     */
    public static boolean downsAtOnce(boolean stunned, boolean frozen, int slownessAmplifier, boolean lightning) {
        return stunned || frozen || slownessAmplifier >= 1 || lightning;
    }

    /** A flyer's climb rate under Slowness I: halved (5.3). */
    public static double climbRateFactor(int slownessAmplifier) {
        return slownessAmplifier == 0 ? 0.5 : 1.0;
    }

    // ================================================================== the glide (1.9)

    /**
     * The vertical speed (blocks a tick) a glide allows after {@code vy}: a fall no faster than its sink rate (after
     * its time cap, 6 b/s), and a rise (a gust) left to vanilla's gravity.
     */
    public static double glideVertical(double vy, double sinkPerSecond, boolean timedOut) {
        double sink = (timedOut ? MobilityRules.GLIDE_TIMED_OUT_SINK : sinkPerSecond) * TICK;
        return vy < -sink ? -sink : vy;
    }

    /** The forward speed (blocks a tick) a glide keeps: its forward speed, none after its time cap. */
    public static double glideForward(double forwardPerSecond, boolean timedOut) {
        return timedOut ? 0 : forwardPerSecond * TICK;
    }

    /** A hero falls faster than this (blocks a tick, downward) before its glide opens by itself. */
    public static final double GLIDE_OPEN_SPEED = 0.2;

    /**
     * Whether a hero that can glide opens its glide as it falls (1.9: "launches from drops of 4+ blocks"): it falls
     * faster than {@link #GLIDE_OPEN_SPEED} and the ground is at least 4 blocks below its feet (or none within the
     * search, a drop of {@link Double#POSITIVE_INFINITY}).
     */
    public static boolean opensGlide(double vy, double dropBelow) {
        return vy < -GLIDE_OPEN_SPEED && dropBelow >= MobilityRules.GLIDE_MIN_DROP;
    }

    // ================================================================== powered flight (2.1-2.3)

    /** A flyer slows down within this many blocks of its target, so it stops on it instead of overshooting. */
    public static final double ARRIVE_SLOWDOWN = 0.35;
    /** Within this distance a flyer is on its target and hovers. */
    public static final double ARRIVED = 0.3;
    /** It descends at most this fast (blocks a second), or at its climb rate when that is faster. */
    public static final double DESCENT = 3.0;
    /** A forced landing descends at this rate (blocks a second): it heads straight down for the ground. */
    public static final double LANDING_DESCENT = 4.0;

    /**
     * The velocity (blocks a tick) a powered flyer flies with this tick toward a point {@code (dx, dy, dz)} away, into
     * {@code out}: horizontally at its cruise speed, slowing within reach of the point; vertically at most its climb
     * rate up and {@link #DESCENT} (or its climb rate) down, and never above {@code ceilingDy} (the altitude ceiling
     * minus its feet height, 2.3). No allocation: the movement engine calls it every tick.
     */
    public static void flightVelocity(double dx, double dy, double dz, double cruisePerSecond, double climbPerSecond,
                                      double ceilingDy, double[] out) {
        double h = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.min(cruisePerSecond * TICK, h * ARRIVE_SLOWDOWN);
        if (h > 1e-6) {
            out[0] = dx / h * speed;
            out[2] = dz / h * speed;
        } else {
            out[0] = 0;
            out[2] = 0;
        }
        double target = Math.min(dy, ceilingDy);
        double up = climbPerSecond * TICK, down = Math.max(DESCENT, climbPerSecond) * TICK;
        out[1] = Math.max(-down, Math.min(up, target * ARRIVE_SLOWDOWN));
    }

    /** Whether a flyer is on its point (it hovers there). */
    public static boolean arrived(double dx, double dy, double dz) {
        return dx * dx + dz * dz < ARRIVED * ARRIVED && Math.abs(dy) < ARRIVED;
    }

    /**
     * What a flyer's stamina is draining for (2.1): hovering in place, cruising (a trip out of combat while it moves),
     * or powered flight (a climb, a fight, an escape).
     */
    public static MobilityRules.Activity flightActivity(boolean hovering, boolean moving, MobilityRules.Situation s) {
        if (hovering || !moving) {
            return MobilityRules.Activity.HOVER;
        }
        return s == MobilityRules.Situation.TRAVEL ? MobilityRules.Activity.CRUISE : MobilityRules.Activity.POWERED;
    }

    // ================================================================== landings (4.1)

    /** The landing a hero plays. */
    public enum Landing { NONE, LAND, LAND_HEAVY, LAND_ROLL }

    /** A fall of this many blocks or more plays a landing (smaller hops and steps do not). */
    public static final double LANDING_FALL = 2;
    /** A landing without the roll slows the hero this many ticks after a leap or a heavy fall (1.6). */
    public static final int LANDING_SLOW_TICKS = 10;
    /** ... to this share of its speed. */
    public static final double LANDING_SLOW_FACTOR = 0.5;

    /**
     * The landing after an airtime: a hero with the landing roll rolls out of a leap or any fall it plays; a leap (its
     * own impulse) or a fall of 4 blocks or more lands heavily otherwise; a fall of 2 blocks or more lands; a hop lands
     * as walking does (nothing).
     */
    public static Landing landing(boolean afterLeap, double fallBlocks, boolean landingRoll) {
        boolean heavy = afterLeap || fallBlocks >= MobilityRules.GLIDE_MIN_DROP;
        if (!heavy && fallBlocks < LANDING_FALL) {
            return Landing.NONE;
        }
        if (landingRoll) {
            return Landing.LAND_ROLL;
        }
        return heavy ? Landing.LAND_HEAVY : Landing.LAND;
    }

    /** Whether a landing slows the hero for a moment (a heavy one without the roll). */
    public static boolean slows(Landing l) {
        return l == Landing.LAND_HEAVY;
    }

    // ================================================================== air jumps and wall kicks (1.7)

    /** A wall kick pushes this far away from the wall (blocks a tick). */
    public static final double WALL_KICK_PUSH = 0.4;
    /** No face: the hero has not kicked off a wall in this airtime. */
    public static final int NO_FACE = -1;

    /**
     * Whether a wall kick off face {@code face} (0-3, the horizontal directions) may follow the last one: never off the
     * same face twice in a row in one airtime (a chimney alternates its two walls; one wall is climbed, not kicked up).
     */
    public static boolean wallKickAllowed(int lastFace, int face) {
        return face >= 0 && face != lastFace;
    }

    /** Whether an air jump is left: the count of the hero's ceiling (1.7), reset on landing. */
    public static boolean airJumpLeft(int used, int count) {
        return used < count;
    }

    // ================================================================== bursts (1.4, 1.5)

    /**
     * The runner's sprint burst or the charger's traversal charge (travel only, 5.1.10): it starts when the way ahead
     * is a clear straight and the burst is ready, lasts its seconds, and is ready again its cooldown after it started.
     */
    public static final class BurstClock {
        private long startedAt = Long.MIN_VALUE / 4;
        private long endsAt = Long.MIN_VALUE / 4;

        /** Starts the burst at {@code now} when it is ready; returns whether it started. */
        public boolean start(long now, MobilityRules.Burst b) {
            if (b == null || active(now) || !ready(now, b)) {
                return false;
            }
            startedAt = now;
            endsAt = now + Math.round(b.seconds() * BossRules.TPS);
            return true;
        }

        public boolean ready(long now, MobilityRules.Burst b) {
            return b != null && now - startedAt >= Math.round(b.cooldown() * BossRules.TPS);
        }

        public boolean active(long now) {
            return now < endsAt;
        }

        /** Ends the burst now (a turn, an obstacle, a fight); its cooldown still runs from its start. */
        public void stop(long now) {
            endsAt = Math.min(endsAt, now);
        }
    }

    // ================================================================== DATA_LOCO (4.2)

    /**
     * The {@code DATA_LOCO} byte as the server keeps it: the mode, the one-shot playing (named by the mode and its kind,
     * {@link HeroClips#oneShotClip}) with the ticks it has left, the sequence toggle that flips with every new one-shot
     * (so two air jumps in a row both play) and the tired bit. A one-shot keeps its mode for its whole length, so a
     * client that misses its first tick still plays the right clip; the one-shot bits clear when it has played.
     */
    public static final class Loco {
        private int mode;
        private int oneShot;
        private int oneShotMode;
        private int oneShotTicks;
        private boolean toggle;
        private boolean tired;

        /** Starts a one-shot of {@code kind} named by {@code shotMode}, lasting {@code ticks}. */
        public void oneShot(int shotMode, int kind, int ticks) {
            oneShotMode = shotMode & 0xF;
            oneShot = kind & 0b11;
            oneShotTicks = Math.max(1, ticks);
            toggle = !toggle;
        }

        /** The mode the hero is in (shown once no one-shot plays). */
        public void mode(int m) {
            mode = m & 0xF;
        }

        public void tired(boolean t) {
            tired = t;
        }

        /** One tick: a one-shot that has played clears. */
        public void tick() {
            if (oneShotTicks > 0 && --oneShotTicks == 0) {
                oneShot = HeroActions.LOCO_ONE_SHOT_NONE;
            }
        }

        /** Whether a one-shot plays now. */
        public boolean playing() {
            return oneShot != HeroActions.LOCO_ONE_SHOT_NONE;
        }

        /** Ends a one-shot at once (a downing cuts a landing short). */
        public void cut() {
            oneShot = HeroActions.LOCO_ONE_SHOT_NONE;
            oneShotTicks = 0;
        }

        public int shownMode() {
            return playing() ? oneShotMode : mode;
        }

        public int mode() {
            return mode;
        }

        public int oneShotKind() {
            return oneShot;
        }

        public boolean toggle() {
            return toggle;
        }

        /** The byte to sync. */
        public byte pack() {
            return HeroActions.packLoco(shownMode(), oneShot, toggle, tired);
        }

        /** Restores the toggle from a synced byte (a hero loaded from disk goes on flipping from where it was). */
        public void restore(byte packed) {
            toggle = HeroActions.locoToggle(packed);
        }
    }

    /** The mode code of a {@code DATA_LOCO} name. */
    public static int mode(String name) {
        return HeroActions.LOCO_MODES.indexOf(name);
    }

    public static final int GROUND = mode("ground"), AIRBORNE = mode("airborne"), GLIDE = mode("glide"), FLY = mode("fly"),
            FLY_HOVER = mode("fly_hover"), CLIMB = mode("climb"), CLING = mode("cling"), CHARGE_RUN = mode("charge_run"),
            SPRINT = mode("sprint"), DOWNED = mode("downed"), WINDED = mode("winded"), RIDE = mode("ride");

    /** Whether a mode shows a mount carrier (4.3: shown only while the mode is fly, glide or ride). */
    public static boolean showsMount(int mode) {
        return mode == FLY || mode == FLY_HOVER || mode == GLIDE || mode == RIDE;
    }

    /** A winded flyer (exhausted, on the ground and standing) pants (2.2). */
    public static boolean winded(boolean exhausted, boolean onGround, double horizontalSpeed) {
        return exhausted && onGround && horizontalSpeed < 0.05;
    }
}
