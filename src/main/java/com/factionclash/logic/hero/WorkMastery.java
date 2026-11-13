package com.factionclash.logic.hero;

import com.factionclash.logic.Affix;
import com.factionclash.logic.AffixRoller;
import com.factionclash.logic.hero.HeroDef.TalentId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How well a hero works outside battle (MOBILITY_AND_WORK part 2: sections 9-13). The action stays player-like (a block
 * still takes ticks to break, furnaces burn real fuel, loot comes from vanilla tables); the hero is better at it.
 * Every hero at its duty has a <b>proficiency</b> by tier (I at levels 1-9, II at 10-19, III from 20): work speed,
 * backpack, reach, wear, hunger and work XP, or a farther alarm reach on guard duty. Its primary talent adds a
 * <b>specialty</b> when the hero does that talent's work itself (no part missing at tier I, the owner's "all abilities
 * but not full power"), and its guard and court talents gain <b>Presence</b> at home (one tier higher, tier IV above
 * III). Every number is capped (13): work speed at most 2x a worker with the same gear, an extra loot roll at most
 * 75% and never more than one, virtual enchantment levels within the vanilla maximum with the tool's own and none
 * with Silk Touch, wear and hunger at least 40% and 50% of a worker's, 36 slots, a 40% price cut, 2x furnace speed.
 * Virtual levels and an extra roll never apply to the same drop: it takes whichever gains more (the one-or-the-other
 * rule), so one ore block yields at most 2.2x the plain table.
 *
 * <p>The worker's side ({@link #workerSpeed} and the rest) is exactly today's code, so {@code FactionNpc}'s work API
 * returns the same numbers for every worker and soldier ({@code WorkMasteryTest} reads the sources). Pure, unit-tested
 * ({@code WorkMasteryTest}); L5m wires it into the jobs.
 */
public final class WorkMastery {
    private WorkMastery() {
    }

    private static final double EPS = 1e-9;

    // ================================================================== a worker's numbers (today's code)

    /** A worker's backpack: 18 slots ({@code FactionNpc}'s container). */
    public static final int WORKER_BACKPACK = 18;

    /** A worker's work speed: 1 + Diligence ({@code BlockWork}). */
    public static double workerSpeed(Map<Affix, Integer> affixes) {
        return 1 + AffixRoller.total(affixes, Affix.DILIGENCE);
    }

    /** A worker's wear: Thrifty spares that share of every wearing event ({@code FactionNpc.sparesGear}). */
    public static double workerWear(Map<Affix, Integer> affixes) {
        return 1 - AffixRoller.total(affixes, Affix.THRIFTY);
    }

    /** A worker's hunger: Hardy keeps that share of all exhaustion unfelt ({@code FactionNpc.hardy}). */
    public static double workerHunger(Map<Affix, Integer> affixes) {
        return Math.max(0, 1 - AffixRoller.total(affixes, Affix.HARDY));
    }

    // ================================================================== kinds of work and drops

    /** The timers a work speed shortens (12.1, 12.3). */
    public enum Action { BREAK, CRAFT, BUILD, SORT, LOAD }

    /** What a roll or a loot calculation is for. */
    public enum Kind {
        ORE(Affix.PROSPECTOR), LEAVES(null), CROP(Affix.GREEN_THUMB), FISH(Affix.ANGLER), KILL(null),
        REGROWTH(Affix.REGROWTH), SHEARING(null), DISH(null), LOAF(null), RECRUIT(null);

        /** The affix whose stacks give this kind's extra roll today (null: none). */
        public final Affix affix;

        Kind(Affix affix) {
            this.affix = affix;
        }
    }

    /** The enchantments a hero's tool may carry virtual levels of, for the loot calculation only (12.2). */
    public enum Ench {
        FORTUNE(true), LOOTING(true), LUCK_OF_THE_SEA(false), LURE(false);

        /** Whether it changes how many items drop (the one-or-the-other rule reads these). */
        public final boolean count;

        Ench(boolean count) {
            this.count = count;
        }
    }

    /** The vanilla maximum of every one of them: III. */
    public static final int VANILLA_MAX = 3;

    // ================================================================== proficiency (10)

    /** One tier of proficiency. */
    public record Proficiency(int tier, double workSpeed, int backpack, double reach, double wearCut, double hungerCut,
                              double xpBonus, int alarmReach) {
    }

    private static final Proficiency[] PROFICIENCY = {
            new Proficiency(1, 0.15, 21, 0.5, 0.10, 0.10, 0.10, 8),
            new Proficiency(2, 0.25, 24, 1.0, 0.20, 0.15, 0.20, 12),
            new Proficiency(3, 0.35, 27, 1.5, 0.30, 0.20, 0.30, 16)};

    /** The proficiency at a hero level: tier I at levels 1-9, II at 10-19, III from 20. */
    public static Proficiency proficiency(int level) {
        return PROFICIENCY[HeroRules.talentTier(level) - 1];
    }

    // ================================================================== caps (13)

    /** Work speed at most 2x a worker with the same gear (Efficiency, Haste and beacons outside the cap). */
    public static final double MAX_WORK_SPEED = 2.0;
    /** An extra loot roll per action: at most 75%, never more than one. */
    public static final double MAX_EXTRA_ROLL = 0.75;
    /** Wear and hunger at least 40% and 50% of a worker's. */
    public static final double MIN_WEAR = 0.4, MIN_HUNGER = 0.5;
    /** A backpack holds at most 36 slots. */
    public static final int MAX_BACKPACK = 36;
    /** Prices at most 40% cheaper (Haggler and the specialty together). */
    public static final double MAX_PRICE_CUT = 0.40;
    /** A furnace or smoker it tends at most 2x vanilla speed. */
    public static final double MAX_FURNACE_SPEED = 2.0;
    /** A scout's trips at most 40% faster with runner and pathfinder together. */
    public static final double MAX_TRIP_BONUS = 0.40;
    /** {@code work.outputCap}: the balance flag, 3.0 (1.5-5.0). */
    public static final double OUTPUT_CAP_DEFAULT = 3.0, OUTPUT_CAP_MIN = 1.5, OUTPUT_CAP_MAX = 5.0;

    public static double outputCap(double config) {
        return Math.max(OUTPUT_CAP_MIN, Math.min(OUTPUT_CAP_MAX, config));
    }

    // ================================================================== specialties (11.1)

    /** A specialty: the duty it works at and its numbers by tier (each quantity I, II, III). */
    public record Specialty(TalentId id, String duty, Map<String, double[]> numbers) {
        public Specialty {
            Map<String, double[]> m = new LinkedHashMap<>();
            numbers.forEach((k, v) -> m.put(k, v.clone()));
            numbers = Collections.unmodifiableMap(m);
        }

        /** A quantity at a tier (1-3, clamped), or 0 when the specialty has none of it. */
        public double at(String quantity, int tier) {
            double[] v = numbers.get(quantity);
            return v == null ? 0 : v[Math.max(1, Math.min(3, tier)) - 1];
        }
    }

    private static final Map<TalentId, Specialty> SPECIALTIES = new EnumMap<>(TalentId.class);

    private static double[] t(double a, double b, double c) {
        return new double[]{a, b, c};
    }

    private static void spec(TalentId id, String duty, Object... qs) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (int i = 0; i < qs.length; i += 2) {
            m.put((String) qs[i], (double[]) qs[i + 1]);
        }
        SPECIALTIES.put(id, new Specialty(id, duty, m));
    }

    static {
        spec(TalentId.SURVEYOR, "miner", "fortune", t(1, 2, 3), "vein_wider", t(1, 1, 1));
        spec(TalentId.FORESTER, "woodcutter", "fortune", t(1, 2, 3));
        spec(TalentId.HARVEST_BLESSING, "farmer", "fortune", t(1, 2, 3), "replants_in_pass", t(1, 1, 1));
        spec(TalentId.BEASTMASTER, "animal_carer", "pairs_per_round", t(2, 3, 4), "shearing_roll_percent",
                t(25, 40, 55), "feeds_babies", t(1, 1, 1));
        spec(TalentId.ANGLER_LORD, "fisherman", "luck_of_the_sea", t(1, 2, 3), "lure", t(1, 2, 3));
        spec(TalentId.EMBER_KEEPER, "smelter", "furnace_faster_percent", t(25, 40, 55), "extra_furnaces", t(1, 2, 2));
        spec(TalentId.MASTER_CHEF, "chef", "smoker_faster_percent", t(20, 30, 40), "second_dish_percent",
                t(15, 25, 35), "second_loaf_percent", t(20, 30, 40));
        spec(TalentId.FORGEMASTER, "blacksmith", "craft_faster_percent", t(25, 40, 55), "repair_unit_percent",
                t(30, 35, 40), "repair_levels_less", t(1, 2, 3));
        spec(TalentId.QUARTERMASTER, "hauler", "backpack", t(30, 33, 36), "sort_faster_percent", t(25, 40, 55));
        spec(TalentId.MERCHANT_PRINCE, "trader", "price_cut_percent", t(10, 20, 30), "backpack", t(30, 33, 36),
                "extra_trip_every_days", t(3, 2, 1));
        spec(TalentId.TRIBUTE_MASTER, "trader", "price_cut_percent", t(10, 15, 20), "tribute_more_percent",
                t(25, 50, 75));
        spec(TalentId.SAGE_OF_LETTERS, "enchanter", "rerolls", t(0.5, 1, 2), "best_of_three", t(1, 1, 1));
        spec(TalentId.ALCHEMIST, "enchanter", "rerolls", t(0.5, 1, 1), "best_of_three", t(1, 1, 1),
                "brew_faster_percent", t(25, 40, 55));
        spec(TalentId.ARCHITECT, "builder", "build_faster_percent", t(20, 30, 40), "place_above", t(2, 3, 4));
        spec(TalentId.ENGINEER, "builder", "build_faster_percent", t(10, 20, 30), "rails_faster_percent",
                t(25, 40, 55));
        spec(TalentId.CARTOGRAPHER, "scout", "scout_range", t(32, 48, 64), "sector_side", t(5, 5, 5));
        spec(TalentId.SPYMASTER, "scout", "approach_blocks", t(24, 20, 16), "names_heroes", t(1, 1, 1));
        spec(TalentId.PATHFINDER, "scout", "trip_faster_percent", t(15, 25, 35), "reroutes", t(1, 1, 1));
        spec(TalentId.AURA, "", "stacks_above_cap", t(2, 3, 4));
    }

    /** A production talent's specialty (null for a guard or court talent: those have Presence, 11.2). */
    public static Specialty specialty(TalentId id) {
        return SPECIALTIES.get(id);
    }

    /** The job of an {@code aura_<affix>} talent ({@code check_roster.py} {@code TALENT_DUTY}). */
    private static final Map<Affix, String> AURA_DUTY = new EnumMap<>(Map.of(Affix.PROSPECTOR, "miner",
            Affix.GREEN_THUMB, "farmer", Affix.REGROWTH, "woodcutter", Affix.MASON, "builder", Affix.DILIGENCE,
            "builder", Affix.HARDY, "hauler", Affix.THRIFTY, "smelter", Affix.MARKSMAN, "guard", Affix.DUELIST,
            "guard", Affix.ANGLER, "fisherman"));

    /**
     * The duty a talent's specialty works at (MOBILITY_AND_WORK 9: the roster's {@code TALENT_DUTY}): a production
     * talent's job, an {@code aura_<affix>} the affix's job, and guard for every guard and court talent.
     */
    public static String duty(TalentId talent, Affix auraAffix) {
        if (talent == TalentId.AURA) {
            return auraAffix == null ? "guard" : AURA_DUTY.getOrDefault(auraAffix, "guard");
        }
        Specialty s = SPECIALTIES.get(talent);
        return s == null ? "guard" : s.duty();
    }

    // ================================================================== a hero at work

    /**
     * A hero at work: its level, its duty, its primary talent (its specialty: null for none) with the affix of an
     * {@code aura} talent, its own affix stacks, and the config switches {@code work.mastery} (false: it works like a
     * worker) and {@code work.specialties} (false: proficiency only).
     */
    public record Worker(int level, String duty, TalentId talent, Affix auraAffix, Map<Affix, Integer> affixes,
                         boolean mastery, boolean specialties) {
        public Worker {
            affixes = affixes == null ? Map.of() : Map.copyOf(affixes);
        }

        /** A hero with both switches on. */
        public static Worker hero(int level, String duty, TalentId talent, Affix auraAffix, Map<Affix, Integer> a) {
            return new Worker(level, duty, talent, auraAffix, a, true, true);
        }
    }

    /** Its proficiency applies: mastery on and the job is its duty (a leader's court visit passes its duty). */
    public static boolean proficient(Worker w, String job) {
        return w.mastery() && job != null && job.equals(w.duty());
    }

    /** Its specialty applies: proficient, specialties on, and the job is its talent's job. */
    public static boolean specialised(Worker w, String job) {
        return proficient(w, job) && w.specialties() && w.talent() != null && job.equals(duty(w.talent(),
                w.auraAffix()));
    }

    /** The specialty number at the hero's tier, or 0 when its specialty does not apply to this job. */
    static double spec(Worker w, String job, String quantity) {
        if (!specialised(w, job)) {
            return 0;
        }
        Specialty s = SPECIALTIES.get(w.talent());
        return s == null ? 0 : s.at(quantity, HeroRules.talentTier(w.level()));
    }

    /**
     * The hero's total of an affix: its own stacks within the cap and, with an {@code aura} specialty for that affix
     * at work, 2 / 3 / 4 more stacks above the cap (11.1).
     */
    public static double affix(Worker w, String job, Affix a) {
        double own = AffixRoller.total(w.affixes(), a);
        if (w.talent() == TalentId.AURA && w.auraAffix() == a) {
            own += spec(w, job, "stacks_above_cap") * a.perStack;
        }
        return own;
    }

    /** The specialty's speed for one action (craft: forgemaster; build: architect, engineer; sort: quartermaster). */
    static double specialtySpeed(Worker w, String job, Action a) {
        return switch (a) {
            case CRAFT -> spec(w, job, "craft_faster_percent") / 100;
            case BUILD -> spec(w, job, "build_faster_percent") / 100;
            case SORT -> spec(w, job, "sort_faster_percent") / 100;
            case BREAK, LOAD -> 0;
        };
    }

    /**
     * {@code workSpeed()} (12.1): a worker's 1 + Diligence; a hero at its duty adds its proficiency, its specialty for
     * this action and the base's talents for its job ({@code talentsAtBase}, a share), all together at most 2x. A
     * hero on guard duty has no work speed bonus (its alarm reach instead).
     */
    public static double workSpeed(Worker w, String job, Action a, double talentsAtBase) {
        if (!proficient(w, job)) {
            return workerSpeed(w.affixes());
        }
        double prof = "guard".equals(job) ? 0 : proficiency(w.level()).workSpeed();
        double sum = 1 + affix(w, job, Affix.DILIGENCE) + prof + specialtySpeed(w, job, a) + talentsAtBase;
        return Math.min(MAX_WORK_SPEED, sum);
    }

    /** {@code backpackSlots()}: 18 for a worker; at its duty the larger of its proficiency and its specialty, 36 at most. */
    public static int backpackSlots(Worker w, String job) {
        if (!proficient(w, job)) {
            return WORKER_BACKPACK;
        }
        int b = Math.max(proficiency(w.level()).backpack(), (int) spec(w, job, "backpack"));
        return Math.min(MAX_BACKPACK, b);
    }

    /** {@code workReach()}: the job's constant, plus 0.5 / 1.0 / 1.5 for a hero at its duty. */
    public static double workReach(Worker w, String job, double jobReach) {
        return proficient(w, job) ? jobReach + proficiency(w.level()).reach() : jobReach;
    }

    /** {@code wearMultiplier()}: a worker's Thrifty; a hero at its duty also its proficiency's cut, at least 40%. */
    public static double wearMultiplier(Worker w, String job) {
        if (!proficient(w, job)) {
            return workerWear(w.affixes());
        }
        double v = (1 - affix(w, job, Affix.THRIFTY)) * (1 - proficiency(w.level()).wearCut());
        return Math.max(MIN_WEAR, v);
    }

    /** {@code hungerMultiplier()}: a worker's Hardy; a hero at its duty also its proficiency's cut, at least 50%. */
    public static double hungerMultiplier(Worker w, String job) {
        if (!proficient(w, job)) {
            return workerHunger(w.affixes());
        }
        double v = (1 - affix(w, job, Affix.HARDY)) * (1 - proficiency(w.level()).hungerCut());
        return Math.max(MIN_HUNGER, v);
    }

    /** Work XP: x1.1 / 1.2 / 1.3 at its duty, x1 otherwise. */
    public static double xpMultiplier(Worker w, String job) {
        return proficient(w, job) ? 1 + proficiency(w.level()).xpBonus() : 1;
    }

    /** On guard duty a hero answers alarms 8 / 12 / 16 blocks farther (instead of a work speed). */
    public static int alarmReachBonus(Worker w) {
        return proficient(w, "guard") ? proficiency(w.level()).alarmReach() : 0;
    }

    /**
     * {@code extraRollChance(kind)}: a worker's affix chance; a hero adds its {@code aura} stacks, its specialty's
     * own rolls (a beastmaster's shearing, a master chef's second dish and loaf) and {@code rally}'s second recruit,
     * at most 75% (one extra roll, never two).
     */
    public static double extraRollChance(Worker w, String job, Kind k) {
        double affix = k.affix == null ? 0 : proficient(w, job) ? affix(w, job, k.affix)
                : AffixRoller.total(w.affixes(), k.affix);
        if (!w.mastery()) {
            return affix;
        }
        double own = switch (k) {
            case SHEARING -> spec(w, job, "shearing_roll_percent") / 100;
            case DISH -> spec(w, job, "second_dish_percent") / 100;
            case LOAF -> spec(w, job, "second_loaf_percent") / 100;
            case RECRUIT -> w.talent() == TalentId.RALLY ? personal(TalentId.RALLY, "two_recruits_percent",
                    HeroRules.talentTier(w.level())) / 100 : 0;
            default -> 0;
        };
        return Math.min(MAX_EXTRA_ROLL, affix + own);
    }

    /**
     * {@code virtualLevel(enchantment, kind)} (12.2): the specialty's levels added to the hero's own tool for this
     * kind of loot (surveyor: Fortune on ores; forester: Fortune on leaves; harvest blessing: Fortune on crops; angler
     * lord: Luck of the Sea and Lure on fish; renown: Looting on its own kills), never above the vanilla maximum with
     * the tool's own, and no Fortune on a Silk Touch tool. 0 for a worker.
     */
    public static int virtualLevel(Worker w, String job, Ench e, Kind k, int toolLevel, boolean silkTouch) {
        if (!w.mastery()) {
            return 0;
        }
        double add = switch (e) {
            case FORTUNE -> (k == Kind.ORE && w.talent() == TalentId.SURVEYOR
                    || k == Kind.LEAVES && w.talent() == TalentId.FORESTER
                    || k == Kind.CROP && w.talent() == TalentId.HARVEST_BLESSING) ? spec(w, job, "fortune") : 0;
            case LUCK_OF_THE_SEA -> k == Kind.FISH ? spec(w, job, "luck_of_the_sea") : 0;
            case LURE -> k == Kind.FISH ? spec(w, job, "lure") : 0;
            case LOOTING -> k == Kind.KILL && w.talent() == TalentId.RENOWN ? personal(TalentId.RENOWN,
                    "virtual_looting", HeroRules.talentTier(w.level())) : 0;
        };
        if (e == Ench.FORTUNE && silkTouch) {
            return 0;
        }
        return (int) Math.max(0, Math.min(add, VANILLA_MAX - Math.max(0, toolLevel)));
    }

    // ================================================================== the one-or-the-other rule (12.2, 13)

    /** Vanilla's sapling chances from leaves at Fortune 0-3 (the leaves tables). */
    private static final double[] SAPLING = {0.05, 0.0625, 0.083333336, 0.1};
    /** Vanilla's crop bonus: a binomial of (3 + Fortune) tries at 0.5714 over one base item (carrots, potatoes). */
    private static final double CROP_P = 0.5714, CROP_EXTRA = 3;

    /**
     * How many items a drop gives at a Fortune or Looting level, against none (expected): ores 1 + F(F+1) / 2(F+2)
     * (1.33 / 1.75 / 2.2), crops the binomial bonus, leaves the sapling chance, a kill one item plus Looting's
     * uniform 0..L. Other kinds (and Luck of the Sea, Lure) change no count: 1.
     */
    public static double countGain(Kind k, int level) {
        int f = Math.max(0, Math.min(VANILLA_MAX, level));
        return switch (k) {
            case ORE -> 1 + f * (f + 1) / (2.0 * (f + 2));
            case CROP -> (1 + (CROP_EXTRA + f) * CROP_P) / (1 + CROP_EXTRA * CROP_P);
            case LEAVES -> SAPLING[f] / SAPLING[0];
            case KILL -> 1 + f / 2.0;
            default -> 1;
        };
    }

    /** Which bonus a drop takes. */
    public enum Bonus { NONE, VIRTUAL_LEVELS, EXTRA_ROLL }

    /** A drop's bonus: which, the virtual levels it carries and the extra roll's chance (one of the two is 0). */
    public record Drop(Bonus bonus, int virtualLevels, double rollChance) {
        /** The expected items against the plain table with the tool's own enchantment. */
        public double gain(Kind k, int toolLevel) {
            return switch (bonus) {
                case NONE -> 1;
                case VIRTUAL_LEVELS -> countGain(k, toolLevel + virtualLevels) / countGain(k, toolLevel);
                case EXTRA_ROLL -> 1 + rollChance;
            };
        }
    }

    /**
     * The one-or-the-other rule (13): a drop never takes both virtual levels and an extra roll; it takes whichever has
     * the larger expected gain (virtual: the count with the levels against the tool's own; the roll: 1 + its chance,
     * since an extra roll uses the tool's real enchantments only), the roll on a tie. Luck of the Sea and Lure change
     * no count: the main catch carries them and a second catch rolls with the real rod, so they never multiply.
     */
    public static Drop drop(Kind k, int virtualLevels, double rollChance, int toolLevel) {
        double roll = Math.max(0, Math.min(MAX_EXTRA_ROLL, rollChance));
        double virtual = virtualLevels <= 0 ? 1 : countGain(k, toolLevel + virtualLevels) / countGain(k, toolLevel);
        if (virtual <= 1 + EPS && roll <= EPS) {
            return new Drop(Bonus.NONE, 0, 0);
        }
        return virtual > 1 + roll + EPS ? new Drop(Bonus.VIRTUAL_LEVELS, virtualLevels, 0)
                : new Drop(Bonus.EXTRA_ROLL, 0, roll);
    }

    /** The whole decision for one drop by a hero at work: its virtual Fortune or Looting, else its extra roll. */
    public static Drop drop(Worker w, String job, Kind k, int toolLevel, boolean silkTouch) {
        Ench e = k == Kind.KILL ? Ench.LOOTING : Ench.FORTUNE;
        return drop(k, virtualLevel(w, job, e, k, toolLevel, silkTouch), extraRollChance(w, job, k), toolLevel);
    }

    // ================================================================== furnaces, prices, repairs, trips (11.1, 12.3)

    /**
     * A furnace or smoker a hero tends (12.3): each tick adds its bonus to an accumulator and moves the cooking
     * progress on by whole ticks, never past the last tick (vanilla's tick finishes the item and pays the XP); the
     * fuel is untouched, so each item burns less. The speed is at most 2x vanilla (the base's talents included).
     */
    public static final class FurnaceBoost {
        private final double bonus;
        private double carry;

        /** {@code bonus}: the share over vanilla (0.55 for an ember keeper III), with the base's talents. */
        public FurnaceBoost(double bonus) {
            this.bonus = Math.max(0, Math.min(MAX_FURNACE_SPEED - 1, bonus));
        }

        public double bonus() {
            return bonus;
        }

        /** The extra progress (whole ticks) to add this tick, never past {@code total - 1}. */
        public int tick(int progress, int total) {
            carry += bonus;
            int add = (int) Math.floor(carry + EPS);
            add = Math.max(0, Math.min(add, total - 1 - progress));
            carry = Math.min(carry - add, 1); // what the last tick could not use is not banked
            return add;
        }
    }

    /** The furnace bonus of a hero tending one at its duty: its specialty's and the base's talents', capped. */
    public static double furnaceBonus(Worker w, String job, boolean smoker, double talentsAtBase) {
        double s = spec(w, job, smoker ? "smoker_faster_percent" : "furnace_faster_percent") / 100;
        return Math.max(0, Math.min(MAX_FURNACE_SPEED - 1, s + talentsAtBase));
    }

    /** The price cut a trading hero gets: Haggler and its specialty together, at most 40%. */
    public static double priceCut(Worker w, String job) {
        double haggler = AffixRoller.total(w.affixes(), Affix.HAGGLER);
        return Math.min(MAX_PRICE_CUT, haggler + spec(w, job, "price_cut_percent") / 100);
    }

    /** The special price difference of an offer (12.3): floor(cut x the offer's base cost), as Haggler's today. */
    public static int priceDiff(double cut, int baseCost) {
        return -(int) Math.floor(Math.max(0, Math.min(MAX_PRICE_CUT, cut)) * baseCost + EPS);
    }

    /** A forgemaster's anvil repair: the levels it costs, at least 1 (vanilla's cost less 1 / 2 / 3). */
    public static int repairCost(Worker w, String job, int vanillaCost) {
        return Math.max(1, vanillaCost - (int) spec(w, job, "repair_levels_less"));
    }

    /** The share of durability one material unit restores at the anvil: vanilla 25%, a forgemaster 30 / 35 / 40%. */
    public static double repairUnit(Worker w, String job) {
        double s = spec(w, job, "repair_unit_percent");
        return s > 0 ? s / 100 : 0.25;
    }

    /** A scout's trip speed bonus: the runner's travel bonus and pathfinder's together, at most +40%. */
    public static double tripBonus(Worker w, String job, double runnerTravelBonus) {
        return Math.min(MAX_TRIP_BONUS, Math.max(0, runnerTravelBonus) + spec(w, job, "trip_faster_percent") / 100);
    }

    /**
     * {@code sage_of_letters}' rerolls at its {@code n}-th enchanting (0-based) when no offer is worth it: tier I once
     * at every second enchanting, II once, III twice (an {@code alchemist} as sage I / II / II until brewing exists).
     */
    public static int rerolls(Worker w, String job, int n) {
        double r = spec(w, job, "rerolls");
        if (r <= 0) {
            return 0;
        }
        return r < 1 ? (n % 2 == 1 ? 1 : 0) : (int) r;
    }

    // ================================================================== Presence (11.2)

    /** The guard and court talents: at their base they count one tier higher, tier IV above III. */
    public static final Set<TalentId> PRESENCE = Collections.unmodifiableSet(java.util.EnumSet.of(TalentId.RALLY,
            TalentId.MENTOR, TalentId.RENOWN, TalentId.DRILL, TalentId.FESTIVAL_HOST, TalentId.STEWARD, TalentId.MEND,
            TalentId.TRIAGE, TalentId.REVIVAL_RITE, TalentId.PURIFIER, TalentId.LOOKOUT, TalentId.ENVOY,
            TalentId.INTIMIDATION, TalentId.WARDEN_OF_WALLS, TalentId.TACTICIAN, TalentId.WAR_DRUMMER));

    private static final Map<TalentId, Map<String, Double>> TIER_IV = new EnumMap<>(TalentId.class);
    private static final Map<TalentId, Map<String, double[]>> PERSONAL = new EnumMap<>(TalentId.class);

    private static void iv(TalentId id, Object... qs) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < qs.length; i += 2) {
            m.put((String) qs[i], ((Number) qs[i + 1]).doubleValue());
        }
        TIER_IV.put(id, Collections.unmodifiableMap(m));
    }

    private static void personal(TalentId id, Object... qs) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (int i = 0; i < qs.length; i += 2) {
            m.put((String) qs[i], (double[]) qs[i + 1]);
        }
        PERSONAL.put(id, Collections.unmodifiableMap(m));
    }

    static {
        // tier IV, in Talents' quantity names (the quantities 11.2 leaves out keep their tier-III value)
        iv(TalentId.RALLY, "recruit_food_cheaper_percent", 25);
        iv(TalentId.MENTOR, "work_xp_percent", 45, "recruit_level", 4);
        iv(TalentId.RENOWN, "recruit_interval_multiplier", 0.6);
        iv(TalentId.DRILL, "soldier_xp_per_hour", 4);
        iv(TalentId.FESTIVAL_HOST, "festival_morale", 30);
        iv(TalentId.STEWARD);
        iv(TalentId.MEND, "heal_percent", 12);
        iv(TalentId.TRIAGE, "heal", 10);
        iv(TalentId.REVIVAL_RITE, "sooner_percent", 50);
        iv(TalentId.PURIFIER, "every_seconds", 5);
        iv(TalentId.LOOKOUT, "alarm_radius", 56);
        iv(TalentId.ENVOY, "acceptance", 0.4);
        iv(TalentId.INTIMIDATION, "defence_seen_multiplier", 1.6);
        iv(TalentId.WARDEN_OF_WALLS, "alarm_radius_plus", 32, "repair_budget_multiplier", 3);
        iv(TalentId.TACTICIAN, "joint_less_percent", 25);
        iv(TalentId.WAR_DRUMMER, "attack_speed_percent", 12);
        // each one's personal effect, I / II / III
        personal(TalentId.RALLY, "two_recruits_percent", t(15, 25, 35));
        personal(TalentId.MENTOR, "job_nearby_faster_percent", t(5, 8, 10), "radius", t(16, 16, 16));
        personal(TalentId.RENOWN, "virtual_looting", t(1, 2, 3));
        personal(TalentId.DRILL, "sparring_soldiers", t(2, 3, 4), "sparring_xp", t(10, 15, 20));
        personal(TalentId.FESTIVAL_HOST, "festival_longer_percent", t(25, 40, 50));
        personal(TalentId.STEWARD, "full_tier_at_its_base", t(1, 1, 1));
        personal(TalentId.MEND, "hand_heal", t(4, 6, 8), "every_seconds", t(20, 20, 20), "channel_seconds",
                t(2, 2, 2), "radius", t(16, 16, 16));
        personal(TalentId.TRIAGE, "meals_carried", t(2, 3, 4));
        personal(TalentId.REVIVAL_RITE, "absorption_hearts", t(4, 6, 8), "for_seconds", t(120, 120, 120));
        personal(TalentId.PURIFIER, "cleanse_every_seconds", t(60, 45, 30), "radius", t(8, 8, 8));
        personal(TalentId.LOOKOUT, "sight_alarm_plus", t(16, 16, 16), "from_height", t(6, 6, 6));
        personal(TalentId.ENVOY, "acceptance_in_person", t(0.1, 0.15, 0.2));
        personal(TalentId.INTIMIDATION, "raiders_fall_back_sooner_percent", t(10, 15, 20));
        personal(TalentId.WARDEN_OF_WALLS, "repair_speed_vs_builder", t(1.5, 1.5, 1.5));
        personal(TalentId.TACTICIAN, "gather_at_cover", t(1, 1, 1));
        personal(TalentId.WAR_DRUMMER, "defence_speed_percent", t(5, 8, 10), "radius", t(24, 24, 24));
    }

    /**
     * A guard or court talent's tier at its base (11.2): at the base (inside the walls or within 16 blocks) and not on
     * a raid it counts one tier higher, up to IV; elsewhere its own tier. Other talents keep their tier.
     */
    public static int presenceTier(TalentId id, int tier, boolean atBase, boolean onRaid) {
        int t = Math.max(1, Math.min(3, tier));
        return PRESENCE.contains(id) && atBase && !onRaid ? t + 1 : t;
    }

    /** A guard or court talent's base-wide number at a tier 1-4 (tier IV: 11.2's table; I-III: {@code Talents}). */
    public static double presenceValue(TalentId id, String quantity, int tier) {
        if (tier >= 4 && TIER_IV.containsKey(id)) {
            Double v = TIER_IV.get(id).get(quantity);
            if (v != null) {
                return v;
            }
        }
        return com.factionclash.logic.leader.Talents.value(id, quantity, Math.min(3, tier));
    }

    /** A guard or court talent's personal effect at the hero's own tier (Presence lifts the base-wide part only). */
    public static double personal(TalentId id, String quantity, int tier) {
        Map<String, double[]> m = PERSONAL.get(id);
        double[] v = m == null ? null : m.get(quantity);
        return v == null ? 0 : v[Math.max(1, Math.min(3, tier)) - 1];
    }

    /** The quantities 11.2 gives a tier-IV number for. */
    public static Set<String> tierFourQuantities(TalentId id) {
        Map<String, Double> m = TIER_IV.get(id);
        return m == null ? Set.of() : m.keySet();
    }

    // ================================================================== the output flag (13)

    /**
     * A day's output against a worker's (13, the worked example): a hero that breaks blocks {@code speed} times as
     * fast and gains {@code perAction} per action, over a day that is {@code workShare} work and the rest walking,
     * hauling, eating and sleeping, makes perAction / ((1 - share) + share / speed) times as much.
     */
    public static double dailyOutput(double workShare, double speed, double perAction) {
        double s = Math.max(0, Math.min(1, workShare));
        return perAction / ((1 - s) + s / Math.max(EPS, speed));
    }

    /** The campaign check's flag: a level-20 hero's daily output over {@code work.outputCap} times a worker's. */
    public static boolean outputFlag(double heroDaily, double workerDaily, double outputCapConfig) {
        return workerDaily > 0 && heroDaily > outputCap(outputCapConfig) * workerDaily + EPS;
    }
}
