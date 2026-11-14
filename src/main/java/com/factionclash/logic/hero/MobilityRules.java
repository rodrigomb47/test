package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.MobilityTrait;
import com.factionclash.logic.hero.HeroDef.TraitTier;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A hero's mobility at its level (MOBILITY_AND_WORK 1-2, 5.1 and 7; the owner's answer of 2026-11-12, "all abilities
 * but not full power"). The authored tier of a trait is its <b>ceiling</b>, not an unlock gate: a hero has every
 * feature of its ceiling and the tiers below it from level 1, and its numbers grow with the power
 * {@code p(L) = 0.6 + 0.4 x (min(L, 20) - 1) / 19} (both ends config keys) in one of three ways: a bonus over a
 * worker's value, a reach a worker does not have, or a wait or cost where less is better. Link sizes round down to
 * whole blocks, and no number goes below the smallest use of its feature (a leaper's jump of 1.8 blocks, a gap of 2).
 *
 * <p>Around the profile: the flight stamina and its drain, the take-off rules, the air budget of a fight and of a
 * raid's approach, the altitude ceilings, the travel bonuses that are off in a fight, the inputs of the fight-reach
 * clock (counted from engaged enemies, whoever they are) and which rules of 5.1 hold while escaping. Nothing here
 * reads whether anyone is a player. Ordinary NPCs have no mobility: a worker's profile is {@link #NONE}. Pure,
 * unit-tested ({@code MobilityRulesTest}); A10-A12 read it.
 */
public final class MobilityRules {
    private MobilityRules() {
    }

    private static final double EPS = 1e-9;

    // ================================================================== the power curve (1.1, 8.5)

    /** {@code mobility.levelPowerAtOne} (percent) and {@code mobility.fullPowerLevel}. */
    public static final int POWER_AT_ONE_DEFAULT = 60, POWER_AT_ONE_MIN = 30, POWER_AT_ONE_MAX = 100;
    public static final int FULL_POWER_LEVEL_DEFAULT = 20, FULL_POWER_LEVEL_MIN = 10, FULL_POWER_LEVEL_MAX = 30;

    /** The two config keys of the curve, clamped into their ranges. */
    public record Curve(int powerAtOne, int fullPowerLevel) {
        public static final Curve DEFAULT = new Curve(POWER_AT_ONE_DEFAULT, FULL_POWER_LEVEL_DEFAULT);

        public Curve {
            powerAtOne = Math.max(POWER_AT_ONE_MIN, Math.min(POWER_AT_ONE_MAX, powerAtOne));
            fullPowerLevel = Math.max(FULL_POWER_LEVEL_MIN, Math.min(FULL_POWER_LEVEL_MAX, fullPowerLevel));
        }
    }

    /** The power at a hero level: 60% at 1, 79% at 10, 100% from 20 (levels above add nothing to mobility). */
    public static double power(int level, Curve c) {
        double a = c.powerAtOne() / 100.0;
        int l = Math.max(1, Math.min(level, c.fullPowerLevel()));
        return a + (1 - a) * (l - 1) / (c.fullPowerLevel() - 1);
    }

    public static double power(int level) {
        return power(level, Curve.DEFAULT);
    }

    // ================================================================== the three kinds of number (1.1)

    /** How a number scales with the power. */
    public enum Scaling {
        /** A bonus over what a worker has: worker + p x (ceiling - worker). */
        BONUS,
        /** A reach a worker does not have: p x ceiling. */
        REACH,
        /** A wait or a cost where less is better: ceiling / p. */
        WAIT
    }

    public static double scale(Scaling k, double worker, double ceiling, double p) {
        return switch (k) {
            case BONUS -> worker + p * (ceiling - worker);
            case REACH -> p * ceiling;
            case WAIT -> ceiling / p;
        };
    }

    /** A link size (a gap, a jump-up, a climb) in whole blocks: rounded down (a 6-block gap at 79% is 4.7, so 4). */
    public static int linkBlocks(double v) {
        return (int) Math.floor(v + 1e-6);
    }

    // ================================================================== worker values and floors

    /** A worker jumps 1.25 blocks, falls 3 safely, steps 0.6, walks 4.3 blocks a second and swims at x1.0. */
    public static final double WORKER_JUMP = 1.25, WORKER_SAFE_FALL = 3, WORKER_STEP = 0.6, WALK_SPEED = 4.3,
            WORKER_SWIM = 1.0;
    /** The step height of a hero whose trait gives it whole blocks (a feature: it walks up full blocks). */
    public static final double FULL_STEP = 1.0;
    /** The floors of 1.1: a leaper jumps at least 1.8 blocks (a field fence's jump-up), a gap is at least 2. */
    public static final double LEAPER_JUMP_FLOOR = 1.8;
    public static final int GAP_FLOOR = 2;
    /** A jump-up link needs the jump height (or the reach with air jumps) minus this; links rise 1.5-3 blocks. */
    public static final double JUMP_UP_MARGIN = 0.3, JUMP_UP_MIN = 1.5, JUMP_UP_MAX = 3;
    /** Vanilla ladder physics: a climber goes up at 2.35 blocks a second and down at 3. */
    public static final double CLIMB_UP_SPEED = 2.35, CLIMB_DOWN_SPEED = 3;
    /** A glider launches from drops of 4 blocks or more; a gust lifts 1.5; past its time it sinks at 6 b/s. */
    public static final double GLIDE_MIN_DROP = 4, GUST_LIFT = 1.5, GLIDE_TIMED_OUT_SINK = 6;
    /** A hover hero sinks at 4 b/s when nothing holds it. */
    public static final double HOVER_SINK = 4;
    /** The sprint burst and the traversal charge last 3 s. */
    public static final double BURST_SECONDS = 3;
    /** A climber III climbs any height ("any" is a feature); searches stop here. */
    public static final int ANY_HEIGHT = 256;

    // ================================================================== features and stats (1.3, 2.1)

    /** What a hero can do at all, whole from level 1 (1.1: on/off values and "any" are features). */
    public enum Feature {
        STEP_ONE, SPRINT_BURST, TRAVERSAL_CHARGE, WEB_IMMUNE, LANDING_ROLL, AIR_JUMP, WALL_KICK, CHIMNEY, MANTLE,
        NO_FALL_DAMAGE, CLIMB, CLING, WORK_CLINGING, CLIMB_ANY, OVERHANG, GLIDE, GLIDE_FROM_APEX, FLIGHT, DIVE,
        DIVE_FROM_AIR, HOVER, NEVER_TRAMPLES, DIVING, AMPHIBIOUS, UNDERWATER_MINING, WATER_BREATHING, SURFACE_LEAP,
        NEVER_FREEZES, POWDER_SNOW_FREE, FIREPROOF, LAVA_WALKING
    }

    /** A number with its scaling, the worker's value, whether it is a link size, and its floor. */
    public enum Stat {
        JUMP_HEIGHT(Scaling.BONUS, WORKER_JUMP, false), SAFE_FALL(Scaling.BONUS, WORKER_SAFE_FALL, false),
        HOVER_STEP(Scaling.BONUS, FULL_STEP, false), TRAVEL_SPEED(Scaling.BONUS, 0, false),
        SPRINT_BOOST(Scaling.BONUS, 0, false), CHARGE_BOOST(Scaling.BONUS, 0, false),
        MOVEMENT_EFFICIENCY(Scaling.BONUS, 0, false), KNOCKBACK(Scaling.BONUS, 0, false),
        TRAVEL_HUNGER_CUT(Scaling.BONUS, 0, false), SWIM_SPEED(Scaling.BONUS, WORKER_SWIM, false),
        WATER_EFFICIENCY(Scaling.BONUS, 0, false), OXYGEN(Scaling.BONUS, 0, false),
        CRUISE_SPEED(Scaling.BONUS, WALK_SPEED, false), GLIDE_FORWARD(Scaling.BONUS, WALK_SPEED, false),
        HOVER_HEIGHT(Scaling.BONUS, 0, false), SNOW_SPEED(Scaling.BONUS, 0, false),
        AIR_JUMP_HEIGHT(Scaling.REACH, 0, false), GAP(Scaling.REACH, 0, true), CLIMB_HEIGHT(Scaling.REACH, 0, true),
        GLIDE_SECONDS(Scaling.REACH, 0, false), STAMINA(Scaling.REACH, 0, false), CLIMB_RATE(Scaling.REACH, 0, false),
        REGAIN(Scaling.REACH, 0, false), HOVER_GAP(Scaling.REACH, 0, true),
        SPRINT_COOLDOWN(Scaling.WAIT, 0, false), CHARGE_COOLDOWN(Scaling.WAIT, 0, false),
        TAKE_OFF(Scaling.WAIT, 0, false), GLIDE_SINK(Scaling.WAIT, 0, false);

        public final Scaling scaling;
        public final double worker;
        public final boolean link;

        Stat(Scaling scaling, double worker, boolean link) {
            this.scaling = scaling;
            this.worker = worker;
            this.link = link;
        }

        /** Of two values from a primary and a secondary, the one that counts: the larger, or the shorter wait. */
        double better(double a, double b) {
            if (Double.isNaN(a)) {
                return b;
            }
            if (Double.isNaN(b)) {
                return a;
            }
            return scaling == Scaling.WAIT ? Math.min(a, b) : Math.max(a, b);
        }
    }

    /** Counts are features too (1.1): a ceiling-III acrobat has its two air jumps at level 1. */
    public enum Count { AIR_JUMPS, GUSTS }

    private static final double NA = Double.NaN;

    /** One family's full numbers (level 20) at ceilings I / II / III, the tier each feature starts at, its counts. */
    public static final class Family {
        final MobilityTrait trait;
        final Map<Stat, double[]> stats = new EnumMap<>(Stat.class);
        final Map<Feature, Integer> features = new EnumMap<>(Feature.class);
        final Map<Count, int[]> counts = new EnumMap<>(Count.class);

        Family(MobilityTrait trait) {
            this.trait = trait;
        }

        Family stat(Stat s, double a, double b, double c) {
            stats.put(s, new double[]{a, b, c});
            return this;
        }

        Family feature(Feature f, int fromTier) {
            features.put(f, fromTier);
            return this;
        }

        Family count(Count k, int a, int b, int c) {
            counts.put(k, new int[]{a, b, c});
            return this;
        }

        /** A full value (level 20) at a ceiling (1-3); NaN when the family has none at that ceiling. */
        public double full(Stat s, int tier) {
            double[] v = stats.get(s);
            return v == null ? NA : v[clampTier(tier) - 1];
        }

        public boolean has(Feature f, int tier) {
            Integer from = features.get(f);
            return from != null && clampTier(tier) >= from;
        }

        public int count(Count k, int tier) {
            int[] v = counts.get(k);
            return v == null ? 0 : v[clampTier(tier) - 1];
        }

        public Set<Stat> stats() {
            return Collections.unmodifiableSet(stats.keySet());
        }

        public Set<Feature> features() {
            return Collections.unmodifiableSet(features.keySet());
        }
    }

    static int clampTier(int t) {
        return Math.max(1, Math.min(3, t));
    }

    private static final Map<MobilityTrait, Family> FAMILIES = new EnumMap<>(MobilityTrait.class);

    private static Family family(MobilityTrait t) {
        Family f = new Family(t);
        FAMILIES.put(t, f);
        return f;
    }

    static {
        family(MobilityTrait.RUNNER).stat(Stat.TRAVEL_SPEED, 10, 15, 20).feature(Feature.STEP_ONE, 2)
                .stat(Stat.MOVEMENT_EFFICIENCY, 0, 0.5, 1.0).stat(Stat.TRAVEL_HUNGER_CUT, 20, 35, 50)
                .feature(Feature.SPRINT_BURST, 3).stat(Stat.SPRINT_BOOST, NA, NA, 40)
                .stat(Stat.SPRINT_COOLDOWN, NA, NA, 15);
        family(MobilityTrait.CHARGER).feature(Feature.STEP_ONE, 1).stat(Stat.KNOCKBACK, 0.1, 0.2, 0.3)
                .stat(Stat.SAFE_FALL, 5, 7, 9).stat(Stat.MOVEMENT_EFFICIENCY, 0.25, 0.5, 1.0)
                .feature(Feature.TRAVERSAL_CHARGE, 2).stat(Stat.CHARGE_BOOST, NA, 60, 60)
                .stat(Stat.CHARGE_COOLDOWN, NA, 20, 12).feature(Feature.WEB_IMMUNE, 2);
        family(MobilityTrait.LEAPER).stat(Stat.JUMP_HEIGHT, 1.8, 2.5, 3.5).stat(Stat.GAP, 5, 6, 8)
                .stat(Stat.SAFE_FALL, 6, 10, 16).feature(Feature.LANDING_ROLL, 2).feature(Feature.DIVE, 1);
        family(MobilityTrait.ACROBAT).feature(Feature.AIR_JUMP, 1).count(Count.AIR_JUMPS, 1, 1, 2)
                .stat(Stat.AIR_JUMP_HEIGHT, 1.25, 1.25, 1.25).stat(Stat.JUMP_HEIGHT, 1.25, 1.5, 1.5)
                .stat(Stat.GAP, 4, 5, 6).stat(Stat.SAFE_FALL, 8, 16, NA).feature(Feature.NO_FALL_DAMAGE, 3)
                .feature(Feature.STEP_ONE, 2).feature(Feature.WALL_KICK, 2).feature(Feature.CHIMNEY, 2)
                .feature(Feature.MANTLE, 3).feature(Feature.LANDING_ROLL, 1).feature(Feature.DIVE, 1);
        family(MobilityTrait.CLIMBER).feature(Feature.CLIMB, 1).feature(Feature.CLING, 1)
                .stat(Stat.CLIMB_HEIGHT, 6, 16, NA).feature(Feature.CLIMB_ANY, 3).feature(Feature.WORK_CLINGING, 2)
                .stat(Stat.SAFE_FALL, NA, 6, 6).feature(Feature.MANTLE, 3).feature(Feature.OVERHANG, 3);
        family(MobilityTrait.GLIDER).feature(Feature.GLIDE, 1).stat(Stat.GLIDE_SINK, 3.0, 2.4, 2.0)
                .stat(Stat.GLIDE_FORWARD, 7, 8, 10).stat(Stat.GLIDE_SECONDS, 10, 15, 20)
                .feature(Feature.GLIDE_FROM_APEX, 2).count(Count.GUSTS, 0, 1, 2).feature(Feature.DIVE, 1)
                .feature(Feature.DIVE_FROM_AIR, 1);
        family(MobilityTrait.FLYER).feature(Feature.FLIGHT, 1).stat(Stat.STAMINA, 30, 45, 60)
                .stat(Stat.CRUISE_SPEED, 6, 7, 8).stat(Stat.CLIMB_RATE, 2, 3, 4).stat(Stat.TAKE_OFF, 30, 25, 20)
                .stat(Stat.REGAIN, 2.0, 2.5, 3.0)
                // it glides as a glider of its tier
                .feature(Feature.GLIDE, 1).stat(Stat.GLIDE_SINK, 3.0, 2.4, 2.0).stat(Stat.GLIDE_FORWARD, 7, 8, 10)
                .stat(Stat.GLIDE_SECONDS, 10, 15, 20).feature(Feature.GLIDE_FROM_APEX, 2)
                .count(Count.GUSTS, 0, 1, 2).feature(Feature.DIVE, 1).feature(Feature.DIVE_FROM_AIR, 1)
                .stat(Stat.SAFE_FALL, 6, 6, 6);
        family(MobilityTrait.HOVER).feature(Feature.HOVER, 1).stat(Stat.HOVER_HEIGHT, 0.5, 1.0, 1.5)
                .stat(Stat.HOVER_GAP, 2, 3, 4).stat(Stat.HOVER_STEP, 1.0, 1.5, 2.0).feature(Feature.STEP_ONE, 1)
                .feature(Feature.NEVER_TRAMPLES, 1);
        family(MobilityTrait.SWIMMER).stat(Stat.SWIM_SPEED, 1.5, 2.0, 2.5).stat(Stat.WATER_EFFICIENCY, 0.33, 0.66, 1.0)
                .stat(Stat.OXYGEN, 2, 5, 5).feature(Feature.DIVING, 2).feature(Feature.AMPHIBIOUS, 2)
                .feature(Feature.UNDERWATER_MINING, 2).feature(Feature.WATER_BREATHING, 3)
                .feature(Feature.SURFACE_LEAP, 3);
        family(MobilityTrait.SNOWSTRIDE).feature(Feature.NEVER_FREEZES, 1).feature(Feature.POWDER_SNOW_FREE, 1)
                .stat(Stat.SNOW_SPEED, NA, 20, 20);
        family(MobilityTrait.EMBER_STRIDE).feature(Feature.FIREPROOF, 1).feature(Feature.LAVA_WALKING, 2);
    }

    /** A family's table (every trait has one). */
    public static Family table(MobilityTrait t) {
        return FAMILIES.get(t);
    }

    // ================================================================== the profile

    /**
     * Everything a hero's mobility gives at one level: its features, its numbers (NaN where it has none) and its
     * counts. {@link #NONE} is a worker's (and a format-1 hero's, and every hero's with {@code mobility.enabled} off).
     */
    public static final class Profile {
        private final Set<Feature> features;
        private final Map<Stat, Double> stats;
        private final Map<Count, Integer> counts;
        private final Map<Stat, MobilityTrait> statSources;
        private final Map<Feature, MobilityTrait> featureSources;
        private final double power;
        private final boolean flightSwitchedOff;

        Profile(Set<Feature> features, Map<Stat, Double> stats, Map<Count, Integer> counts, double power,
                boolean flightSwitchedOff) {
            this(features, stats, counts, Map.of(), Map.of(), power, flightSwitchedOff);
        }

        Profile(Set<Feature> features, Map<Stat, Double> stats, Map<Count, Integer> counts,
                Map<Stat, MobilityTrait> statSources, Map<Feature, MobilityTrait> featureSources, double power,
                boolean flightSwitchedOff) {
            this.features = Collections.unmodifiableSet(features.isEmpty() ? EnumSet.noneOf(Feature.class)
                    : EnumSet.copyOf(features));
            this.stats = Collections.unmodifiableMap(stats.isEmpty() ? new EnumMap<>(Stat.class)
                    : new EnumMap<>(stats));
            this.counts = Collections.unmodifiableMap(counts.isEmpty() ? new EnumMap<>(Count.class)
                    : new EnumMap<>(counts));
            this.statSources = statSources.isEmpty() ? Map.of() : Collections.unmodifiableMap(
                    new EnumMap<>(statSources));
            this.featureSources = featureSources.isEmpty() ? Map.of() : Collections.unmodifiableMap(
                    new EnumMap<>(featureSources));
            this.power = power;
            this.flightSwitchedOff = flightSwitchedOff;
        }

        /** The trait a number comes from (the better of primary and secondary; null when the hero has none). */
        public MobilityTrait source(Stat s) {
            return statSources.get(s);
        }

        /** The trait a feature comes from (the primary when both give it; null when the hero has none). */
        public MobilityTrait source(Feature f) {
            return featureSources.get(f);
        }

        public boolean has(Feature f) {
            return features.contains(f);
        }

        public Set<Feature> features() {
            return features;
        }

        /** The number at this level, or NaN when the hero has none of it. */
        public double raw(Stat s) {
            Double v = stats.get(s);
            return v == null ? NA : v;
        }

        public int count(Count k) {
            return counts.getOrDefault(k, 0);
        }

        /** The power this profile was made at (0 for {@link #NONE}). */
        public double power() {
            return power;
        }

        /** {@code mobility.flight} was off: a flyer glides at its ceiling instead. */
        public boolean flightSwitchedOff() {
            return flightSwitchedOff;
        }

        /** Whether the hero has any mobility at all. */
        public boolean any() {
            return !features.isEmpty() || !stats.isEmpty();
        }

        // -------- the numbers as the game reads them, with workers' values where the hero has none

        public double stepHeight() {
            double s = has(Feature.STEP_ONE) ? FULL_STEP : WORKER_STEP;
            double hover = raw(Stat.HOVER_STEP);
            return Double.isNaN(hover) ? s : Math.max(s, hover);
        }

        public double jumpHeight() {
            double j = raw(Stat.JUMP_HEIGHT);
            return Double.isNaN(j) ? WORKER_JUMP : j;
        }

        /** The {@code generic.jump_strength} attribute value (0.42 for a worker's 1.25 blocks). */
        public double jumpStrength() {
            return LeapSolver.attributeFor(jumpHeight());
        }

        public double airJumpHeight() {
            double a = raw(Stat.AIR_JUMP_HEIGHT);
            return Double.isNaN(a) ? 0 : a;
        }

        /** How high the hero reaches with its jump and every air jump (Natsuho: 2.9 at level 1, 4.0 at 20). */
        public double reachUp() {
            return jumpHeight() + count(Count.AIR_JUMPS) * airJumpHeight();
        }

        /** The highest jump-up link: reach minus 0.3, at most 3 (0 when under the 1.5 of a field fence). */
        public double jumpUp() {
            double r = Math.min(JUMP_UP_MAX, reachUp() - JUMP_UP_MARGIN);
            return r + EPS >= JUMP_UP_MIN ? r : 0;
        }

        /** The widest gap link in whole blocks (0: none). */
        public int gap() {
            double g = raw(Stat.GAP);
            return Double.isNaN(g) ? 0 : (int) g;
        }

        public double safeFall() {
            if (has(Feature.NO_FALL_DAMAGE)) {
                return Double.POSITIVE_INFINITY;
            }
            double f = raw(Stat.SAFE_FALL);
            return Double.isNaN(f) ? WORKER_SAFE_FALL : f;
        }

        /** The highest climb in whole blocks (0: not a climber; {@link #ANY_HEIGHT} for "any"). */
        public int climbHeight() {
            if (!has(Feature.CLIMB)) {
                return 0;
            }
            if (has(Feature.CLIMB_ANY)) {
                return ANY_HEIGHT;
            }
            double c = raw(Stat.CLIMB_HEIGHT);
            return Double.isNaN(c) ? 0 : (int) c;
        }

        public boolean glides() {
            return has(Feature.GLIDE);
        }

        public boolean flies() {
            return has(Feature.FLIGHT);
        }

        /** Blocks forward per block of drop while gliding (forward / sink; 0 when it does not glide). */
        public double glideRatio() {
            double s = raw(Stat.GLIDE_SINK), f = raw(Stat.GLIDE_FORWARD);
            return Double.isNaN(s) || Double.isNaN(f) ? 0 : f / s;
        }

        public int hoverGap() {
            double g = raw(Stat.HOVER_GAP);
            return Double.isNaN(g) ? 0 : (int) g;
        }

        /** The take-off threshold as a share of stamina (0.42 for Ambrose at level 1). */
        public double takeOffShare() {
            double t = raw(Stat.TAKE_OFF);
            return Double.isNaN(t) ? 1 : t / 100.0;
        }

        /** Whether the hero leaves the ground by its own power (the {@code dive}, MOBILITY_AND_WORK 7.3). */
        public boolean airborne() {
            return has(Feature.DIVE);
        }

        @Override
        public String toString() {
            return "Profile" + features + stats + counts;
        }
    }

    /** A worker's mobility: none. */
    public static final Profile NONE = new Profile(EnumSet.noneOf(Feature.class), new EnumMap<>(Stat.class),
            new EnumMap<>(Count.class), 0, false);

    /** {@code mobility.staminaPercent}: scales flight stamina, 50-200. */
    public static final int STAMINA_PERCENT_MIN = 50, STAMINA_PERCENT_MAX = 200, STAMINA_PERCENT_DEFAULT = 100;

    /** The config keys a profile reads (8.5). */
    public record Config(boolean enabled, boolean flight, Curve curve, int staminaPercent) {
        public static final Config DEFAULT = new Config(true, true, Curve.DEFAULT, STAMINA_PERCENT_DEFAULT);

        public Config {
            curve = curve == null ? Curve.DEFAULT : curve;
            staminaPercent = Math.max(STAMINA_PERCENT_MIN, Math.min(STAMINA_PERCENT_MAX, staminaPercent));
        }
    }

    /**
     * The profile of a hero's mobility block at a level. A null block (a format-1 hero) or {@code mobility.enabled}
     * off gives {@link #NONE}. The primary and the secondary apply together; where both set the same number the
     * better counts (the larger, or the shorter wait), features and counts add up. With {@code mobility.flight} off a
     * flyer keeps its glide and its dive but not its flight.
     */
    public static Profile profile(HeroDef.Mobility m, int level, Config cfg) {
        if (m == null || !cfg.enabled() || m.primary() == null) {
            return NONE;
        }
        return profile(m.primary(), m.secondary(), level, cfg);
    }

    public static Profile profile(TraitTier primary, TraitTier secondary, int level, Config cfg) {
        if (primary == null || !cfg.enabled()) {
            return NONE;
        }
        double p = power(level, cfg.curve());
        Set<Feature> feats = EnumSet.noneOf(Feature.class);
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        Map<Count, Integer> counts = new EnumMap<>(Count.class);
        Map<Stat, MobilityTrait> statSrc = new EnumMap<>(Stat.class);
        Map<Feature, MobilityTrait> featSrc = new EnumMap<>(Feature.class);
        for (TraitTier tt : secondary == null ? new TraitTier[]{primary} : new TraitTier[]{primary, secondary}) {
            Family f = FAMILIES.get(tt.trait());
            int tier = clampTier(tt.tier());
            for (Map.Entry<Feature, Integer> e : f.features.entrySet()) {
                if (tier >= e.getValue() && feats.add(e.getKey())) {
                    featSrc.put(e.getKey(), tt.trait());
                }
            }
            for (Map.Entry<Count, int[]> e : f.counts.entrySet()) {
                counts.merge(e.getKey(), e.getValue()[tier - 1], Math::max);
            }
            for (Map.Entry<Stat, double[]> e : f.stats.entrySet()) {
                double full = e.getValue()[tier - 1];
                if (Double.isNaN(full)) {
                    continue;
                }
                Stat s = e.getKey();
                double v = valueAt(tt.trait(), s, full, p);
                if (s == Stat.STAMINA) {
                    v = v * cfg.staminaPercent() / 100.0;
                }
                double before = stats.containsKey(s) ? stats.get(s) : NA;
                double now = s.better(before, v);
                if (Double.isNaN(before) || now != before) {
                    statSrc.put(s, tt.trait());
                }
                stats.put(s, now);
            }
        }
        counts.values().removeIf(c -> c == 0);
        boolean off = false;
        if (!cfg.flight() && feats.contains(Feature.FLIGHT)) {
            feats.remove(Feature.FLIGHT);
            featSrc.remove(Feature.FLIGHT);
            for (Stat s : FLIGHT_STATS) {
                stats.remove(s);
                statSrc.remove(s);
            }
            off = true;
        }
        return new Profile(feats, stats, counts, statSrc, featSrc, p, off);
    }

    public static Profile profile(TraitTier primary, TraitTier secondary, int level) {
        return profile(primary, secondary, level, Config.DEFAULT);
    }

    /** The numbers of powered flight, which a flyer loses with {@code mobility.flight} off (its glide stays). */
    static final Stat[] FLIGHT_STATS = {Stat.STAMINA, Stat.CRUISE_SPEED, Stat.CLIMB_RATE, Stat.TAKE_OFF, Stat.REGAIN};

    /**
     * One number of one family at power {@code p}: scaled by its kind, a link size rounded down, then the floors of
     * 1.1 (a leaper's jump 1.8, a gap or hover gap 2).
     */
    public static double valueAt(MobilityTrait trait, Stat s, double full, double p) {
        double v = scale(s.scaling, s.worker, full, p);
        if (s.link) {
            v = linkBlocks(v);
        }
        if (s == Stat.JUMP_HEIGHT && trait == MobilityTrait.LEAPER) {
            v = Math.max(v, LEAPER_JUMP_FLOOR);
        }
        if (s == Stat.GAP || s == Stat.HOVER_GAP) {
            v = Math.max(v, GAP_FLOOR);
        }
        return v;
    }

    // ================================================================== flight (2.1, 2.2)

    /** What a flyer is doing in the air, for its stamina. */
    public enum Activity { POWERED, CRUISE, HOVER, GLIDE }

    /** The situation a hero moves in (2.3, 5.1): a trip, a raid's approach outside a fight, a fight, an escape. */
    public enum Situation { TRAVEL, RAID, FIGHT, ESCAPE }

    /** Drain per second: powered 1.0, cruising out of combat 0.5, hovering in place 2.0, gliding 0. */
    public static final double DRAIN_POWERED = 1.0, DRAIN_CRUISE = 0.5, DRAIN_HOVER = 2.0, DRAIN_GLIDE = 0;
    /** A backpack more than half full x1.5; while fighting x1.5; escaping x1.0 (and no air budget). */
    public static final double DRAIN_LOADED = 1.5, DRAIN_FIGHTING = 1.5, DRAIN_ESCAPING = 1.0;
    /** Stamina comes back on the ground after 1 s. */
    public static final double REGAIN_DELAY = 1;
    /** At 25% the hero heads for the nearest landing cell; at 0 it only glides and is exhausted until 50%. */
    public static final double LOW_SHARE = 0.25, RESTED_SHARE = 0.5;
    /** Take-off needs 4 blocks of headroom over its footprint; it lasts 0.6 s and pushes 1.5 blocks up. */
    public static final int TAKE_OFF_HEADROOM = 4;
    public static final double TAKE_OFF_SECONDS = 0.6, TAKE_OFF_PUSH = 1.5, LANDING_SECONDS = 0.4;

    /** Stamina drained per second (cruising in a fight is powered flight: nobody cruises in a fight). */
    public static double drain(Activity a, boolean loaded, Situation s) {
        double base = switch (a) {
            case POWERED -> DRAIN_POWERED;
            case CRUISE -> s == Situation.FIGHT ? DRAIN_POWERED : DRAIN_CRUISE;
            case HOVER -> DRAIN_HOVER;
            case GLIDE -> DRAIN_GLIDE;
        };
        double k = s == Situation.FIGHT ? DRAIN_FIGHTING : s == Situation.ESCAPE ? DRAIN_ESCAPING : 1;
        return base * (loaded ? DRAIN_LOADED : 1) * k;
    }

    /**
     * A flyer's stamina in seconds of powered flight (2.1, 2.2): drained in the air, regained on the ground after
     * 1 s at the profile's rate; at 0 the hero is exhausted (no take-off) until it is back at 50%.
     */
    public static final class Stamina {
        private final double max, regain;
        private double value;
        private boolean exhausted;
        private double groundSeconds;

        public Stamina(Profile p) {
            this(p.raw(Stat.STAMINA), p.raw(Stat.REGAIN));
        }

        public Stamina(double max, double regainPerSecond) {
            this.max = Double.isNaN(max) ? 0 : max;
            this.regain = Double.isNaN(regainPerSecond) ? 0 : regainPerSecond;
            this.value = this.max;
        }

        /** {@code seconds} in the air at a drain rate. */
        public void air(double seconds, double drainPerSecond) {
            groundSeconds = 0;
            value = Math.max(0, value - seconds * drainPerSecond);
            if (value <= EPS) {
                value = 0;
                exhausted = true;
            }
        }

        /** {@code seconds} on the ground: nothing in the first second, then the regain rate. */
        public void ground(double seconds) {
            double before = groundSeconds;
            groundSeconds += seconds;
            double counted = Math.max(0, groundSeconds - Math.max(before, REGAIN_DELAY));
            value = Math.min(max, value + counted * regain);
            if (exhausted && value + EPS >= RESTED_SHARE * max) {
                exhausted = false;
            }
        }

        public double value() {
            return value;
        }

        /**
         * Sets the stamina (a load from disk, a level-up that changes the maximum, an operator or a test): clamped to
         * 0-max; at 0 the hero is exhausted, and {@code exhausted} keeps it so until it is back at 50%.
         */
        public void set(double v, boolean exhausted) {
            value = Math.max(0, Math.min(max, v));
            this.exhausted = exhausted && value + EPS < RESTED_SHARE * max || value <= EPS && max > 0;
            groundSeconds = 0;
        }

        public double max() {
            return max;
        }

        public double share() {
            return max <= 0 ? 0 : value / max;
        }

        public boolean exhausted() {
            return exhausted;
        }

        /** Under 25%: head for the nearest landing cell (the wings droop: the {@code tired} bit of {@code DATA_LOCO}). */
        public boolean low() {
            return share() < LOW_SHARE - EPS;
        }

        /** Empty: it can only glide. */
        public boolean empty() {
            return value <= EPS;
        }
    }

    /**
     * The average speed of a long straight flight with rests (2.1): it flies its stamina out cruising (0.5 a second),
     * lands, waits 1 s and regains it all, and goes on. A ceiling-I flyer: 4.7 b/s at level 20, 3.7 at level 1;
     * Ambrose (II): 4.4 at level 1, 5.8 at 20. Walking is 4.3.
     */
    public static double averageFlightSpeed(Profile p) {
        double st = p.raw(Stat.STAMINA), cruise = p.raw(Stat.CRUISE_SPEED), regain = p.raw(Stat.REGAIN);
        if (!p.flies() || Double.isNaN(st) || st <= 0 || regain <= 0) {
            return 0;
        }
        double flight = st / DRAIN_CRUISE;
        return flight * cruise / (flight + REGAIN_DELAY + st / regain);
    }

    /** What a take-off is checked against (2.2, 3.5, 7.4). */
    public record TakeOffState(double staminaShare, boolean exhausted, int headroom, boolean thunderstorm,
                               Situation situation, boolean downedRecently, boolean groundTimeMet, boolean lowCeiling) {
    }

    /**
     * Why a take-off may not start (null: it may): it must fly (with {@code mobility.flight} on), have 4 blocks of
     * headroom over its footprint and no low ceiling, its stamina at the threshold and not exhausted, not within 12 s
     * of a downing, its ground time of the air budget met, and out of combat no thunderstorm (an escape flies in any
     * weather).
     */
    public static String takeOffRefusal(Profile p, TakeOffState s) {
        if (!p.flies()) {
            return p.flightSwitchedOff() ? "flight is switched off (mobility.flight): it glides" : "it does not fly";
        }
        if (s.headroom() < TAKE_OFF_HEADROOM || s.lowCeiling()) {
            return "less than " + TAKE_OFF_HEADROOM + " blocks of headroom";
        }
        if (s.exhausted()) {
            return "exhausted until its stamina is back at " + Math.round(RESTED_SHARE * 100) + "%";
        }
        if (s.staminaShare() + EPS < p.takeOffShare()) {
            return "stamina under its take-off threshold of " + Math.round(p.takeOffShare() * 100) + "%";
        }
        if (s.downedRecently()) {
            return "downed in the last 12 s";
        }
        if (!s.groundTimeMet()) {
            return "not on the ground long enough since its last landing";
        }
        if (s.thunderstorm() && (s.situation() == Situation.TRAVEL || s.situation() == Situation.RAID)) {
            return "a thunderstorm: it travels on foot";
        }
        return null;
    }

    /**
     * The stamina rule of 3.5 that replaces a landing at each leg's end: keep enough to reach the best landing cell
     * already seen plus 25%, and land when it would not.
     */
    public static final double RESERVE_FACTOR = 1.25;

    public static boolean mustLandForReserve(double stamina, double secondsToBestLanding, double drainPerSecond) {
        return stamina + EPS < RESERVE_FACTOR * secondsToBestLanding * drainPerSecond;
    }

    // ================================================================== the air budget (5.1.4)

    /** {@code mobility.bossAirSeconds}: the air budget per take-off in any fight (the key keeps its name). */
    public static final int FIGHT_AIR_DEFAULT = 8, FIGHT_AIR_MIN = 4, FIGHT_AIR_MAX = 10;
    /** At least 6 s on the ground before the next take-off (4 s in phase 3, never less). */
    public static final double GROUND_SECONDS = 6, GROUND_SECONDS_PHASE_3 = 4;
    /** Airborne at most 40% of any 30 s (12 s of every 30, whatever the take-offs). */
    public static final int WINDOW_TICKS = 30 * BossRules.TPS;
    public static final double WINDOW_SHARE = 0.4;
    /** A raid's approach outside a fight: at most 10 s per take-off, at least 6 s on the ground between. */
    public static final double RAID_AIR_SECONDS = 10;

    public static int fightAirSeconds(int config) {
        return Math.max(FIGHT_AIR_MIN, Math.min(FIGHT_AIR_MAX, config));
    }

    /**
     * The air budget's seconds per take-off in a situation (NaN: no limit but stamina, for a trip and an escape): a
     * fight's {@code mobility.bossAirSeconds}, and in an aerial window no more than the file's {@code max_air}; a
     * raid's approach 10.
     */
    public static double airLimit(Situation s, int bossAirSeconds, double authoredMaxAir) {
        return switch (s) {
            case FIGHT -> {
                double l = fightAirSeconds(bossAirSeconds);
                yield authoredMaxAir > 0 ? Math.min(l, authoredMaxAir) : l;
            }
            case RAID -> RAID_AIR_SECONDS;
            case TRAVEL, ESCAPE -> NA;
        };
    }

    /** The least time on the ground between two take-offs (0: none, on a trip or an escape). */
    public static double groundLimit(Situation s, int phase) {
        return switch (s) {
            case FIGHT -> phase >= 3 ? GROUND_SECONDS_PHASE_3 : GROUND_SECONDS;
            case RAID -> GROUND_SECONDS;
            case TRAVEL, ESCAPE -> 0;
        };
    }

    /**
     * The air budget of one hero, ticked once a game tick: how long it has been up since its take-off, how long on
     * the ground since its landing, and its airborne ticks in the last 30 s. Players and NPCs count the same: the
     * budget does not know who the enemy is.
     */
    public static final class AirBudget {
        private final boolean[] window = new boolean[WINDOW_TICKS];
        private int windowAir;
        private long tick = -1;
        private boolean airborne;
        private long since = Long.MIN_VALUE / 4;

        /** One game tick at {@code now} (ticks in a row; a skipped tick counts as on the ground). */
        public void tick(long now, boolean inAir) {
            if (tick >= 0 && now > tick + 1) {
                for (long t = tick + 1; t < now && t <= tick + WINDOW_TICKS; t++) {
                    put(t, false);
                }
            }
            if (inAir != airborne) {
                airborne = inAir;
                since = now;
            }
            put(now, inAir);
            tick = now;
        }

        private void put(long t, boolean v) {
            int i = (int) Math.floorMod(t, (long) WINDOW_TICKS);
            if (window[i]) {
                windowAir--;
            }
            window[i] = v;
            if (v) {
                windowAir++;
            }
        }

        public boolean airborne() {
            return airborne;
        }

        /** Seconds in the air since the take-off (0 on the ground). */
        public double airSeconds(long now) {
            return airborne ? (now - since) / (double) BossRules.TPS : 0;
        }

        /** Seconds on the ground since the landing (0 in the air; long ago before the first take-off). */
        public double groundSeconds(long now) {
            return airborne ? 0 : (now - since) / (double) BossRules.TPS;
        }

        /** Airborne ticks within the last 30 s. */
        public int windowAirTicks() {
            return windowAir;
        }

        /** Whether the hero must land now: its seconds per take-off used, or 40% of the last 30 s spent in the air. */
        public boolean mustLand(long now, Situation s, int bossAirSeconds, double authoredMaxAir) {
            if (!airborne) {
                return false;
            }
            double limit = airLimit(s, bossAirSeconds, authoredMaxAir);
            if (Double.isNaN(limit)) {
                return false;
            }
            return airSeconds(now) + EPS >= limit || s == Situation.FIGHT && windowAir >= WINDOW_SHARE * WINDOW_TICKS;
        }

        /** Whether its ground time and the 30 s window allow a take-off now. */
        public boolean groundTimeMet(long now, Situation s, int phase) {
            if (airborne) {
                return false;
            }
            if (groundSeconds(now) + EPS < groundLimit(s, phase)) {
                return false;
            }
            return s != Situation.FIGHT || windowAir < WINDOW_SHARE * WINDOW_TICKS;
        }
    }

    // ================================================================== altitude (2.3)

    /** {@code mobility.travelAltitude}: the travel (and escape) ceiling above the ground, 8-48. */
    public static final int TRAVEL_ALTITUDE_DEFAULT = 24, TRAVEL_ALTITUDE_MIN = 8, TRAVEL_ALTITUDE_MAX = 48;
    /** A raid's approach and wall crossing: 10 blocks above the ground under it (a wall top counts as ground). */
    public static final double RAID_CEILING = 10;

    public static int travelAltitude(int config) {
        return Math.max(TRAVEL_ALTITUDE_MIN, Math.min(TRAVEL_ALTITUDE_MAX, config));
    }

    /**
     * The highest a flyer may be (absolute y): a trip or an escape {@code mobility.travelAltitude} above the ground
     * under it; a raid's approach 10; a fight 6 above the ground or above the highest engaged enemy standing on a
     * block (BossRules), whoever that enemy is.
     */
    public static double ceiling(Situation s, double groundY, Collection<Double> engagedEnemyFeet, int travelConfig) {
        return switch (s) {
            case TRAVEL, ESCAPE -> groundY + travelAltitude(travelConfig);
            case RAID -> groundY + RAID_CEILING;
            case FIGHT -> BossRules.fightCeiling(groundY, engagedEnemyFeet);
        };
    }

    // ================================================================== what holds where (5.1, 7.2, 7.7)

    /** The rules of 5.1 by number. */
    public enum Rule {
        NO_TELEPORT(1), WALLS_ARE_TERRAIN(2), ALTITUDE_CEILING(3), AIR_BUDGET(4), STRIKES(5), REACH_IN_THE_AIR(6),
        AIR_POISE(7), CLIMBER_KNOCKED_OFF(8), UNFOLLOWABLE_PLACES(9), TRAVEL_BONUSES_OFF(10), MOVES_UNCHANGED(11),
        NO_CARRYING(12), HAZARDS(13), CREATIVE_IGNORED(14), COME_BACK(15);

        public final int number;

        Rule(int number) {
            this.number = number;
        }
    }

    /**
     * Whether a rule of 5.1 holds in a situation. In a fight all of them. While escaping (and a scout's step aside,
     * which uses an escape's movement) the air budget, the reach in the air, the unfollowable places and the come-back
     * rule are lifted and the travel bonuses are on (an escape is travel); the ceiling is the travel one; no
     * teleporting, air poise, the climber's grip, no carrying, the hazards and the move caps still hold, and the hero
     * stays a boss (cap, poise: {@link #bossRulesHold}). A trip has only the physical rules; a raid's approach adds
     * its own air budget and ceiling.
     */
    public static boolean holds(Rule r, Situation s) {
        return switch (r) {
            case NO_TELEPORT, WALLS_ARE_TERRAIN, ALTITUDE_CEILING, AIR_POISE, CLIMBER_KNOCKED_OFF, MOVES_UNCHANGED,
                 NO_CARRYING, HAZARDS, CREATIVE_IGNORED -> true;
            case AIR_BUDGET -> s == Situation.FIGHT || s == Situation.RAID;
            case STRIKES, REACH_IN_THE_AIR, UNFOLLOWABLE_PLACES, COME_BACK, TRAVEL_BONUSES_OFF -> s == Situation.FIGHT;
        };
    }

    /** The boss layer (the per-hit cap, poise, weak points) holds in every situation, an escape included (7.7). */
    public static boolean bossRulesHold(Situation s) {
        return true;
    }

    /** The runner's speed and sprint, the charger's traversal charge and knockback bonus: on except in a fight. */
    public static boolean travelBonusesOn(Situation s) {
        return !holds(Rule.TRAVEL_BONUSES_OFF, s);
    }

    /** An escaping hero starts no moves and no basic attacks; it runs (cornered, it fights back). */
    public static boolean mayAttack(Situation s, boolean cornered) {
        return s != Situation.ESCAPE || cornered;
    }

    /** Places nobody can follow count as unreachable after this long while fighting: deep water 4 s, lava 2 s. */
    public static final int DEEP_WATER_TICKS = 4 * BossRules.TPS, LAVA_TICKS = 2 * BossRules.TPS;
    /** Deep water: more than 3 blocks under the surface. */
    public static final double DEEP_WATER = 3;

    /**
     * The fight-reach clock's input (5.1.6, 5.1.9, 5.1.15): whether no engaged enemy can reach the hero now. In the
     * air: more than 3 blocks above every engaged enemy's standing height ({@link BossRules#aboveReach}). On ground:
     * when none of them can get to it as it moves ({@code enemiesCanReach} false: an ordinary person's moves for
     * anyone without links, a hero's own links for an enemy hero; {@code HeroLinks}). In deep water for 4 s or on lava
     * for 2 s. Who the enemies are does not count; with no engaged enemy nothing is out of reach.
     */
    public static boolean outOfReach(boolean airborne, double heroFeetY, Collection<Double> enemyStandingY,
                                     boolean enemiesCanReach, int deepWaterTicks, int lavaTicks) {
        if (enemyStandingY.isEmpty()) {
            return false;
        }
        if (deepWaterTicks >= DEEP_WATER_TICKS || lavaTicks >= LAVA_TICKS) {
            return true;
        }
        if (airborne) {
            return BossRules.aboveReach(heroFeetY, enemyStandingY);
        }
        return !enemiesCanReach;
    }

    // ================================================================== vanilla attributes (1.2, 1.4-1.13)

    /** The vanilla and NeoForge attributes the mobility engine (A10) sets, with their defaults. */
    public enum Attr {
        JUMP_STRENGTH("minecraft:generic.jump_strength", LeapSolver.PLAIN_JUMP),
        STEP_HEIGHT("minecraft:generic.step_height", WORKER_STEP),
        SAFE_FALL_DISTANCE("minecraft:generic.safe_fall_distance", WORKER_SAFE_FALL),
        FALL_DAMAGE_MULTIPLIER("minecraft:generic.fall_damage_multiplier", 1),
        MOVEMENT_SPEED("minecraft:generic.movement_speed", 0.25),
        MOVEMENT_EFFICIENCY("minecraft:generic.movement_efficiency", 0),
        KNOCKBACK_RESISTANCE("minecraft:generic.knockback_resistance", 0),
        SWIM_SPEED("neoforge:swim_speed", 1),
        WATER_MOVEMENT_EFFICIENCY("minecraft:generic.water_movement_efficiency", 0),
        OXYGEN_BONUS("minecraft:generic.oxygen_bonus", 0),
        SUBMERGED_MINING_SPEED("minecraft:player.submerged_mining_speed", 0.2),
        BURNING_TIME("minecraft:generic.burning_time", 1);

        public final String id;
        public final double vanillaDefault;

        Attr(String id, double vanillaDefault) {
            this.id = id;
            this.vanillaDefault = vanillaDefault;
        }
    }

    /** The three vanilla modifier operations. */
    public enum Op { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }

    /**
     * One attribute modifier, named {@code factionclash:mobility_<trait>} after the trait that gives it (1.1), so a
     * level change or a config reload replaces it instead of stacking it, and removing the trait's names removes it.
     */
    public record Modifier(Attr attr, String id, double amount, Op op) {
    }

    /** The modifier name of a trait. */
    public static String modifierId(MobilityTrait t) {
        return "factionclash:mobility_" + t.id();
    }

    /** A Submerged Mining Speed of 1: full speed under water (Aqua Affinity). */
    public static final double FULL_SUBMERGED_MINING = 1.0;

    /**
     * The attribute modifiers of a profile now (empty for {@link #NONE}, so a worker, a format-1 hero and every hero
     * with {@code mobility.enabled} off move exactly as today). {@code travelling}: on a trip with no combat goal
     * running (an escape is travel, 5.1.10), which switches the runner's speed on; {@code fasterThanWalking}: its
     * horizontal speed is above walking pace, which with {@code travelling} switches the charger's knockback bonus on.
     * Jumps, step height, safe fall, movement and water efficiency, swimming and the footings hold in fights too.
     */
    public static java.util.List<Modifier> modifiers(Profile p, boolean travelling, boolean fasterThanWalking) {
        java.util.List<Modifier> out = new java.util.ArrayList<>();
        if (!p.any()) {
            return out;
        }
        double jump = p.jumpStrength();
        if (jump > LeapSolver.PLAIN_JUMP + EPS) {
            out.add(new Modifier(Attr.JUMP_STRENGTH, modifierId(p.source(Stat.JUMP_HEIGHT)), round4(jump
                    - LeapSolver.PLAIN_JUMP), Op.ADD_VALUE));
        }
        double step = p.stepHeight();
        if (step > WORKER_STEP + EPS) {
            MobilityTrait src = step > FULL_STEP + EPS || !p.has(Feature.STEP_ONE) ? p.source(Stat.HOVER_STEP)
                    : p.source(Feature.STEP_ONE);
            out.add(new Modifier(Attr.STEP_HEIGHT, modifierId(src), round4(step - WORKER_STEP), Op.ADD_VALUE));
        }
        if (p.has(Feature.NO_FALL_DAMAGE)) {
            out.add(new Modifier(Attr.FALL_DAMAGE_MULTIPLIER, modifierId(p.source(Feature.NO_FALL_DAMAGE)), -1,
                    Op.ADD_MULTIPLIED_TOTAL));
        } else if (!Double.isNaN(p.raw(Stat.SAFE_FALL)) && p.safeFall() > WORKER_SAFE_FALL + EPS) {
            out.add(new Modifier(Attr.SAFE_FALL_DISTANCE, modifierId(p.source(Stat.SAFE_FALL)), round4(p.safeFall()
                    - WORKER_SAFE_FALL), Op.ADD_VALUE));
        }
        if (travelling) {
            add(out, p, Attr.MOVEMENT_SPEED, Stat.TRAVEL_SPEED, 0.01, Op.ADD_MULTIPLIED_BASE);
            if (fasterThanWalking) {
                add(out, p, Attr.KNOCKBACK_RESISTANCE, Stat.KNOCKBACK, 1, Op.ADD_VALUE);
            }
        }
        add(out, p, Attr.MOVEMENT_EFFICIENCY, Stat.MOVEMENT_EFFICIENCY, 1, Op.ADD_VALUE);
        double swim = p.raw(Stat.SWIM_SPEED);
        if (!Double.isNaN(swim) && swim > WORKER_SWIM + EPS) {
            out.add(new Modifier(Attr.SWIM_SPEED, modifierId(p.source(Stat.SWIM_SPEED)), round4(swim - WORKER_SWIM),
                    Op.ADD_MULTIPLIED_BASE));
        }
        add(out, p, Attr.WATER_MOVEMENT_EFFICIENCY, Stat.WATER_EFFICIENCY, 1, Op.ADD_VALUE);
        add(out, p, Attr.OXYGEN_BONUS, Stat.OXYGEN, 1, Op.ADD_VALUE);
        if (p.has(Feature.UNDERWATER_MINING)) {
            out.add(new Modifier(Attr.SUBMERGED_MINING_SPEED, modifierId(p.source(Feature.UNDERWATER_MINING)),
                    round4(FULL_SUBMERGED_MINING - Attr.SUBMERGED_MINING_SPEED.vanillaDefault), Op.ADD_VALUE));
        }
        if (p.has(Feature.FIREPROOF)) {
            out.add(new Modifier(Attr.BURNING_TIME, modifierId(p.source(Feature.FIREPROOF)), -1,
                    Op.ADD_MULTIPLIED_TOTAL));
        }
        return out;
    }

    private static void add(java.util.List<Modifier> out, Profile p, Attr a, Stat s, double unit, Op op) {
        double v = p.raw(s);
        if (!Double.isNaN(v) && v > EPS) {
            out.add(new Modifier(a, modifierId(p.source(s)), round4(v * unit), op));
        }
    }

    private static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }

    /** The attribute's value with the modifiers on its vanilla default (vanilla's order: add, base share, total). */
    public static double attributeValue(Attr a, java.util.List<Modifier> mods) {
        double base = a.vanillaDefault, add = 0, multBase = 0, multTotal = 1;
        for (Modifier m : mods) {
            if (m.attr() != a) {
                continue;
            }
            switch (m.op()) {
                case ADD_VALUE -> add += m.amount();
                case ADD_MULTIPLIED_BASE -> multBase += m.amount();
                case ADD_MULTIPLIED_TOTAL -> multTotal *= 1 + m.amount();
            }
        }
        double v = base + add;
        return (v + v * multBase) * multTotal;
    }

    /** A burst (the runner III sprint, the charger II-III traversal charge): extra speed, its seconds, its wait. */
    public record Burst(double speedBonus, double seconds, double cooldown) {
    }

    /** The sprint burst or the traversal charge at this level (null: the hero has none). Travel only (5.1.10). */
    public static Burst burst(Profile p, Feature f) {
        Stat boost = f == Feature.SPRINT_BURST ? Stat.SPRINT_BOOST : f == Feature.TRAVERSAL_CHARGE ? Stat.CHARGE_BOOST
                : null;
        Stat wait = f == Feature.SPRINT_BURST ? Stat.SPRINT_COOLDOWN : Stat.CHARGE_COOLDOWN;
        if (boost == null || !p.has(f) || Double.isNaN(p.raw(boost))) {
            return null;
        }
        return new Burst(p.raw(boost) / 100.0, BURST_SECONDS, p.raw(wait));
    }

    /** A runner's sprint burst needs a straight of 8 blocks with nothing within 2 blocks ahead (1.4). */
    public static final int BURST_STRAIGHT = 8, BURST_CLEAR_AHEAD = 2;

    /** How high a flyer gets in {@code seconds} of a take-off: the 1.5-block push, then its climb rate (3.7). */
    public static double riseInAir(Profile p, double seconds) {
        double rate = p.raw(Stat.CLIMB_RATE);
        return p.flies() && !Double.isNaN(rate) ? TAKE_OFF_PUSH + rate * Math.max(0, seconds) : 0;
    }

    // ================================================================== the sheet (8.3)

    /** The levels the sheet prints (8.3: as v1 22.2 does for stats). */
    public static final int[] SHEET_LEVELS = {1, 10, 20};

    /**
     * The validator's sheet lines for a hero's mobility: its features (whole from level 1), then each number at
     * levels 1, 10 and 20, as 15.9 shows them ("gap (blocks) 3 / 4 / 6"). Empty for no mobility.
     */
    public static java.util.List<String> sheet(TraitTier primary, TraitTier secondary, Config cfg) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Profile[] at = new Profile[SHEET_LEVELS.length];
        for (int i = 0; i < at.length; i++) {
            at[i] = profile(primary, secondary, SHEET_LEVELS[i], cfg);
        }
        if (!at[0].any()) {
            return out;
        }
        java.util.List<String> feats = new java.util.ArrayList<>();
        for (Feature f : at[0].features()) {
            feats.add(f.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        }
        if (at[0].count(Count.AIR_JUMPS) > 0) {
            feats.add(at[0].count(Count.AIR_JUMPS) + " air jump" + (at[0].count(Count.AIR_JUMPS) > 1 ? "s" : ""));
        }
        if (at[0].count(Count.GUSTS) > 0) {
            feats.add(at[0].count(Count.GUSTS) + " gust" + (at[0].count(Count.GUSTS) > 1 ? "s" : "") + " a glide");
        }
        out.add("features from level 1: " + String.join(", ", feats));
        line(out, at, "jump height (blocks)", p -> p.raw(Stat.JUMP_HEIGHT), 2);
        line(out, at, "each air jump (blocks)", p -> p.raw(Stat.AIR_JUMP_HEIGHT), 2);
        if (at[0].count(Count.AIR_JUMPS) > 0) {
            line(out, at, "reach up with every air jump (blocks)", Profile::reachUp, 1);
        }
        line(out, at, "gap (blocks)", p -> p.raw(Stat.GAP), 0);
        line(out, at, "climb height (blocks)", p -> p.has(Feature.CLIMB_ANY) ? NA : p.raw(Stat.CLIMB_HEIGHT), 0);
        line(out, at, "safe fall (blocks)", p -> p.has(Feature.NO_FALL_DAMAGE) ? NA : p.raw(Stat.SAFE_FALL), 1);
        line(out, at, "travel speed (%)", p -> p.raw(Stat.TRAVEL_SPEED), 0);
        line(out, at, "movement efficiency", p -> p.raw(Stat.MOVEMENT_EFFICIENCY), 2);
        line(out, at, "travel hunger cut (%)", p -> p.raw(Stat.TRAVEL_HUNGER_CUT), 0);
        line(out, at, "knockback resistance while travelling", p -> p.raw(Stat.KNOCKBACK), 2);
        line(out, at, "sprint burst (%)", p -> p.raw(Stat.SPRINT_BOOST), 0);
        line(out, at, "sprint burst every (s)", p -> p.raw(Stat.SPRINT_COOLDOWN), 0);
        line(out, at, "traversal charge (%)", p -> p.raw(Stat.CHARGE_BOOST), 0);
        line(out, at, "traversal charge every (s)", p -> p.raw(Stat.CHARGE_COOLDOWN), 0);
        line(out, at, "swim speed (x)", p -> p.raw(Stat.SWIM_SPEED), 2);
        line(out, at, "water movement efficiency", p -> p.raw(Stat.WATER_EFFICIENCY), 2);
        line(out, at, "oxygen bonus", p -> p.raw(Stat.OXYGEN), 1);
        line(out, at, "stamina (s)", p -> p.raw(Stat.STAMINA), 1);
        line(out, at, "cruise speed (b/s)", p -> p.raw(Stat.CRUISE_SPEED), 1);
        line(out, at, "climb rate (b/s)", p -> p.raw(Stat.CLIMB_RATE), 1);
        line(out, at, "take-off needs (% stamina)", p -> p.raw(Stat.TAKE_OFF), 0);
        line(out, at, "regained on the ground (per s)", p -> p.raw(Stat.REGAIN), 1);
        line(out, at, "glide sink (b/s)", p -> p.raw(Stat.GLIDE_SINK), 1);
        line(out, at, "glide forward (b/s)", p -> p.raw(Stat.GLIDE_FORWARD), 1);
        line(out, at, "glide ratio (blocks per block of drop)", p -> p.glides() ? p.glideRatio() : NA, 1);
        line(out, at, "glide time (s)", p -> p.raw(Stat.GLIDE_SECONDS), 0);
        line(out, at, "hover height (blocks)", p -> p.raw(Stat.HOVER_HEIGHT), 2);
        line(out, at, "hover gap (blocks)", p -> p.raw(Stat.HOVER_GAP), 0);
        line(out, at, "speed on snow and ice (%)", p -> p.raw(Stat.SNOW_SPEED), 0);
        return out;
    }

    private static void line(java.util.List<String> out, Profile[] at, String label,
                             java.util.function.ToDoubleFunction<Profile> f, int decimals) {
        double first = f.applyAsDouble(at[0]);
        if (Double.isNaN(first)) {
            return;
        }
        StringBuilder sb = new StringBuilder(label).append(' ');
        for (int i = 0; i < at.length; i++) {
            sb.append(i == 0 ? "" : " / ").append(String.format(java.util.Locale.ROOT, "%." + decimals + "f",
                    f.applyAsDouble(at[i]) + (decimals == 0 ? 1e-9 : 0)));
        }
        out.add(sb.toString());
    }
}
