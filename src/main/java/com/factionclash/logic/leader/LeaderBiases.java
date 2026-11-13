package com.factionclash.logic.leader;

import com.factionclash.logic.MinePlanner;
import com.factionclash.logic.hero.HeroDef.EconomyFocus;
import com.factionclash.logic.hero.HeroDef.Raids;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.leader.Temperaments.Flag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How a leader's traits weight its faction's director (HEROES_V2 5.5): one pure method per knob. Each reads the
 * traits as {@code t_eff} ({@link Personality#eff}), so with {@code k = 0} every faction is neutral, and a neutral
 * personality (every trait 50) gives exactly today's constant ({@code TODAY_*}, which {@code LeaderBiasesTest} checks
 * against the game's own source). The leader's {@code economy_focus} moves civilian needs up and, for a focus with no
 * civilian role, changes one knob; it is not a trait, so it is switched off at {@code k = 0} together with the
 * temperament's special rules, which keeps "{@code personalityStrength = 0} reproduces today" true. Nothing is read
 * by the director yet (L3 wires it in). Pure, unit-tested ({@code LeaderBiasesTest}).
 */
public final class LeaderBiases {
    // ------------------------------------------------------------------ today's constants (the neutral values)

    /** {@code FactionDirector.raids}: the interval from {@code war.raidIntervalTicks}, x1. */
    public static final double TODAY_RAID_INTERVAL_MULT = 1;
    /** No raid before one interval from the campaign's start. */
    public static final double TODAY_FIRST_RAID_INTERVALS = 1;
    /** A counter-attack may go out after a quarter of the interval. */
    public static final double TODAY_COUNTER_WINDOW = 0.25;
    /** ... when the party outnumbers the defence 3 to 2. */
    public static final double TODAY_COUNTER_ODDS = 1.5;
    /** A real attack needs the party at two thirds of the defence. */
    public static final double TODAY_ATTACK_ODDS = 2.0 / 3;
    /** A third of the garrison stays home ({@code (soldiers + 2) / 3}). */
    public static final double TODAY_GARRISON_SHARE = 1.0 / 3;
    /** The party falls back once more than half is down. */
    public static final double TODAY_FALL_BACK = 0.5;
    /** {@code needs}: no new soldier past 12. */
    public static final int TODAY_SOLDIER_CAP = 12;
    /** At least 3 swordsmen at home. */
    public static final int TODAY_HOME_SWORDSMEN = 3;
    /** A trader once the base has 14 people. */
    public static final int TODAY_TRADER_POPULATION = 14;
    /** An enchanter once the faction's XP reaches level 10. */
    public static final int TODAY_ENCHANTER_LEVEL = 10;
    /** A new house when the free beds are 1 or fewer. */
    public static final int TODAY_HOUSE_FREE_BEDS = 1;
    /** Storage grows once it is fuller than 70%. */
    public static final double TODAY_STORAGE_EXPANSION = 0.7;
    /** {@code FactionDirector.REPAIR_SCAN_BUDGET}. */
    public static final int TODAY_REPAIR_SCAN_BUDGET = 384;
    /** {@code TraderJob.TRIP_INTERVAL}. */
    public static final long TODAY_TRADER_TRIP_TICKS = 12000;
    /** The raid target cost today: {@code defence x 1000 + distance}. */
    public static final double TODAY_DEFENCE_WEIGHT = 1000;

    // ------------------------------------------------------------------ the leader

    /** The effective traits (0-1), the focus (empty at k = 0) and the temperament's flags (none at k = 0). */
    private final double a, h, m, c, g, s, l, v;
    private final List<EconomyFocus> focus;
    private final Temperament temperament;
    private final boolean rules;
    private final double k;

    private LeaderBiases(Personality p, double k, List<EconomyFocus> focus) {
        this.k = Math.max(0, Math.min(2, k));
        a = p.eff(Trait.AGGRESSION, this.k);
        h = p.eff(Trait.HONOUR, this.k);
        m = p.eff(Trait.AMBITION, this.k);
        c = p.eff(Trait.CAUTION, this.k);
        g = p.eff(Trait.GREED, this.k);
        s = p.eff(Trait.SOCIABILITY, this.k);
        l = p.eff(Trait.LOYALTY, this.k);
        v = p.eff(Trait.VOLATILITY, this.k);
        rules = this.k > 0;
        this.focus = rules && focus != null ? List.copyOf(focus) : List.of();
        temperament = p.temperament();
    }

    /**
     * A leader's biases: its personality, {@code k} ({@link Personality#k}) and its economy focus (the leader block's,
     * else its temperament's default).
     */
    public static LeaderBiases of(Personality p, double k, List<EconomyFocus> focus) {
        return new LeaderBiases(p, k, focus);
    }

    /** No leader, or {@code k = 0}: today's director. */
    public static final LeaderBiases NEUTRAL = new LeaderBiases(Personality.neutral(), 1, List.of());

    private boolean flag(Flag f) {
        return rules && Temperaments.has(temperament, f);
    }

    private boolean focused(EconomyFocus f) {
        return focus.contains(f);
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /** A half rounds up (also below zero: -0.5 is 0). */
    static int round(double x) {
        return (int) Math.floor(x + 0.5 + 1e-9);
    }

    // ------------------------------------------------------------------ raids

    /** Raid interval multiplier: {@code 1 + (0.5 - a) + 0.4 (c - 0.5)}, 0.5-1.8. */
    public double raidIntervalMultiplier() {
        return clamp(1 + (0.5 - a) + 0.4 * (c - 0.5), 0.5, 1.8);
    }

    public long raidIntervalTicks(long configTicks) {
        return Math.round(configTicks * raidIntervalMultiplier());
    }

    /** No raids before this many intervals from the start: {@code 1 + 0.6 (h - 0.5) + 0.6 (c - 0.5)}, 0.5-1.6. */
    public double firstRaidIntervals() {
        return clamp(1 + 0.6 * (h - 0.5) + 0.6 * (c - 0.5), 0.5, 1.6);
    }

    /** The first raid's earliest tick after the start; an ancient sage's never before day 3 (48000 ticks). */
    public long firstRaidTicks(long configTicks) {
        long t = Math.round(configTicks * firstRaidIntervals());
        return flag(Flag.NO_RAIDS_BEFORE_DAY_3) ? Math.max(t, 2 * 24000L) : t;
    }

    /** Counter-attack window (share of the interval): {@code 0.25 + 0.25 (c - a)}, 0.1-0.5. */
    public double counterWindow() {
        return clamp(0.25 + 0.25 * (c - a), 0.1, 0.5);
    }

    /** Counter-attack odds (party : defence): {@code 1.5 + 0.8 (c - 0.5) - 0.6 (a - 0.5)}, 1.0-2.2. */
    public double counterOdds() {
        return clamp(1.5 + 0.8 * (c - 0.5) - 0.6 * (a - 0.5), 1.0, 2.2);
    }

    /** Attack odds needed: {@code 0.667 + 0.6 (c - 0.5) - 0.4 (a - 0.5)}, 0.4-1.2 (neutral exactly 2/3). */
    public double attackOdds() {
        return clamp(TODAY_ATTACK_ODDS + 0.6 * (c - 0.5) - 0.4 * (a - 0.5), 0.4, 1.2);
    }

    /** Garrison share kept home: {@code 0.333 + 0.3 (c - 0.5) - 0.1 (a - 0.5)}, 0.15-0.6 (neutral exactly 1/3). */
    public double garrisonShare() {
        return clamp(TODAY_GARRISON_SHARE + 0.3 * (c - 0.5) - 0.1 * (a - 0.5), 0.15, 0.6);
    }

    /** Soldiers kept home of a garrison: the share rounded up (today {@code (n + 2) / 3}). */
    public int garrisonKept(int soldiers) {
        return (int) Math.ceil(soldiers * garrisonShare() - 1e-9);
    }

    /** Minimum home defenders: {@code homeDefenders + round(2 (c - 0.5))}, +1 with a defence focus, at least 0. */
    public int minHomeDefenders(int configHomeDefenders) {
        return Math.max(0, configHomeDefenders + round(2 * (c - 0.5)) + (focused(EconomyFocus.DEFENCE) ? 1 : 0));
    }

    /** The party falls back once it has lost this share: {@code 0.5 + 0.3 (a - .5) - 0.3 (c - .5) + 0.2 (l - .5)}, 0.3-0.8. */
    public double fallBackShare() {
        return clamp(0.5 + 0.3 * (a - 0.5) - 0.3 * (c - 0.5) + 0.2 * (l - 0.5), 0.3, 0.8);
    }

    /**
     * Whether the leader goes with a raid: it leads when {@code a >= 0.65} and {@code c < 0.6}; it stays home when
     * {@code c >= 0.7} or {@code a < 0.35}; else it joins joint and counter raids.
     */
    public Raids leaderRaids() {
        if (a >= 0.65 - 1e-9 && c < 0.6) {
            return Raids.LEADS;
        }
        if (c >= 0.7 - 1e-9 || a < 0.35) {
            return Raids.STAYS_HOME;
        }
        return Raids.JOINS;
    }

    /** A raid its leader was on falls back when the leader falls or escapes and {@code (c + 1 - l) / 2 > 0.5}. */
    public boolean raidFallsBackWithoutLeader() {
        return (c + 1 - l) / 2 > 0.5;
    }

    /** One vengeance raid after its leader's death (when the odds allow): {@code a >= 0.7}, or a hot-blooded leader. */
    public boolean vengeanceRaid() {
        return a >= 0.7 - 1e-9 || flag(Flag.VENGEANCE_ANY_AGGRESSION);
    }

    // ------------------------------------------------------------------ the army

    /** Soldier cap: {@code 12 x clamp(1 + 0.8 (a - .5) + 0.4 (m - .5) - 0.2 (g - .5), 0.6, 1.6)}, +1 military focus. */
    public int soldierCap() {
        double f = clamp(1 + 0.8 * (a - 0.5) + 0.4 * (m - 0.5) - 0.2 * (g - 0.5), 0.6, 1.6);
        return round(TODAY_SOLDIER_CAP * f) + (focused(EconomyFocus.MILITARY) ? 1 : 0);
    }

    /** Home swordsmen minimum: {@code 3 + round(2 (c - 0.5))}, 2-4. */
    public int homeSwordsmen() {
        return (int) clamp(TODAY_HOME_SWORDSMEN + round(2 * (c - 0.5)), 2, 4);
    }

    /** Ranged share: {@code rangedShare + 0.3 (c - 0.5) - 0.2 (a - 0.5)}, 0.15-0.7. */
    public double rangedShare(double configRangedShare) {
        return clamp(configRangedShare + 0.3 * (c - 0.5) - 0.2 * (a - 0.5), 0.15, 0.7);
    }

    // ------------------------------------------------------------------ the economy

    /** Trader at population: {@code round(14 - 10 (s - 0.5) - 6 (g - 0.5))}, 6-20. */
    public int traderPopulation() {
        return (int) clamp(round(TODAY_TRADER_POPULATION - 10 * (s - 0.5) - 6 * (g - 0.5)), 6, 20);
    }

    /** Enchanter at XP level: {@code 10 - round(6 (m - 0.5))}, 7-13. */
    public int enchanterLevel() {
        return (int) clamp(TODAY_ENCHANTER_LEVEL - round(6 * (m - 0.5)), 7, 13);
    }

    /** Recruitment interval multiplier: {@code 1 - 0.6 (m - 0.5)}, 0.7-1.3, x0.9 with a people focus. */
    public double recruitIntervalMultiplier() {
        double x = clamp(1 - 0.6 * (m - 0.5), 0.7, 1.3);
        return focused(EconomyFocus.PEOPLE) ? x * 0.9 : x;
    }

    /** Food reserve multiplier ({@code Logistics}): {@code 1 + 0.8 (c - 0.5) - 0.4 (m - 0.5)}, 0.6-1.5. */
    public double foodReserveMultiplier() {
        return clamp(1 + 0.8 * (c - 0.5) - 0.4 * (m - 0.5), 0.6, 1.5);
    }

    /** House when free beds are this many or fewer: {@code 1 + round(4 max(0, m - 0.5))}, +1 growth focus, +1 genki. */
    public int houseFreeBeds() {
        int x = (int) clamp(TODAY_HOUSE_FREE_BEDS + round(4 * Math.max(0, m - 0.5)), 1, 3);
        return x + (focused(EconomyFocus.GROWTH) ? 1 : 0) + (flag(Flag.BUILDS_AHEAD) ? 1 : 0);
    }

    /** Storage expansion when fuller than: {@code 0.7 - 0.3 (g - 0.5)}, 0.55-0.85. */
    public double storageExpansion() {
        return clamp(TODAY_STORAGE_EXPANSION - 0.3 * (g - 0.5), 0.55, 0.85);
    }

    /** Repair scan budget: {@code 384 x (1 + 0.6 (c - 0.5))}, rounded. */
    public int repairScanBudget() {
        return round(TODAY_REPAIR_SCAN_BUDGET * (1 + 0.6 * (c - 0.5)));
    }

    /** Trader trip interval multiplier ({@code TraderJob}): {@code 1 - 0.8 (s - 0.5)}, 0.6-1.4. */
    public double traderTripMultiplier() {
        return clamp(1 - 0.8 * (s - 0.5), 0.6, 1.4);
    }

    public long traderTripTicks() {
        return Math.round(TODAY_TRADER_TRIP_TICKS * traderTripMultiplier());
    }

    /** Amounts kept back from selling: {@code x (1 + 0.8 (c - 0.5) - 0.6 (g - 0.5))}, 0.5-1.5. */
    public double keepBackMultiplier() {
        return clamp(1 + 0.8 * (c - 0.5) - 0.6 * (g - 0.5), 0.5, 1.5);
    }

    /** One sell list's keep-back amount at this leader's multiplier (rounded). */
    public int keepBack(int todayAmount) {
        return round(todayAmount * keepBackMultiplier());
    }

    /** The miners' gold and diamond wants: {@code x (1 + (g - 0.5))}, 0.5-1.5, rounded. */
    public double wantMultiplier() {
        return clamp(1 + (g - 0.5), 0.5, 1.5);
    }

    public int goldWanted() {
        return round(MinePlanner.GOLD_WANTED * wantMultiplier());
    }

    public int diamondsWanted() {
        return round(MinePlanner.DIAMONDS_WANTED * wantMultiplier());
    }

    /** The economy focus as it applies (empty at {@code k = 0}). */
    public List<EconomyFocus> focus() {
        return focus;
    }

    /**
     * The "Need order" knob: the civilian roles the focus moves ahead of the other civilian needs, in focus order
     * (role ids as {@code NpcRole}'s, lower case). Soldiers' own rules are untouched; {@code military} and
     * {@code defence} move no role (they change the soldier cap and the minimum home defenders instead).
     */
    public List<String> rolesFirst() {
        Set<String> out = new LinkedHashSet<>();
        for (EconomyFocus f : focus) {
            out.addAll(rolesOf(f));
        }
        return new ArrayList<>(out);
    }

    /** The roles a focus moves up (5.5's table). */
    public static List<String> rolesOf(EconomyFocus f) {
        return switch (f) {
            case MINING -> List.of("miner", "smelter");
            case FARMING -> List.of("farmer", "animal_carer", "butcher");
            case FOOD -> List.of("chef", "fisherman", "farmer");
            case TRADE -> List.of("trader", "hauler");
            case GROWTH -> List.of("builder");
            case PEOPLE -> List.of("chef", "hauler");
            case CRAFT -> List.of("blacksmith", "smelter");
            case LORE -> List.of("enchanter");
            case BUILDING -> List.of("builder", "woodcutter");
            case SCOUTING -> List.of("scout", "cave_explorer");
            case MILITARY, DEFENCE -> List.of();
        };
    }

    // ------------------------------------------------------------------ raid targets

    /** {@code fd = 1 - 2 max(0, a m - 0.25)}: a bold, ambitious leader seeks strong rivals. */
    public double defenceWeight() {
        return 1 - 2 * Math.max(0, a * m - 0.25);
    }

    /** {@code fdist = 1 + 4 x territorial} (delinquents and lone wolves). */
    public double distanceWeight() {
        return 1 + 4 * (flag(Flag.TERRITORIAL) ? 1 : 0);
    }

    /** The ambush bonus (h <= 0.3) against a faction whose army its scouts saw marching: -1500, doubled for a trickster. */
    public static final double AMBUSH_BONUS = 1500;

    /**
     * The raid target cost (lower wins; today {@code defence x 1000 + distance}): {@code 1000 x defencePower x fd +
     * distance x fdist - 2000 x lootEstimate x max(0, g - 0.5) x 2 - 1000 x isLair x ((h - .5) + (c - .5) - (a - .5))},
     * less the ambush bonus. {@code defencePower} is in soldier units with heroes at their war power (8.1);
     * {@code lootEstimate} 0-1 from what scouts reported (a lair 0.2).
     */
    public double targetCost(double defencePower, double distance, double lootEstimate, boolean lair,
                             boolean armySeenMarching) {
        double cost = TODAY_DEFENCE_WEIGHT * defencePower * defenceWeight() + distance * distanceWeight()
                - 2000 * Math.max(0, Math.min(1, lootEstimate)) * Math.max(0, g - 0.5) * 2
                - 1000 * (lair ? 1 : 0) * ((h - 0.5) + (c - 0.5) - (a - 0.5));
        if (armySeenMarching && ambushes()) {
            cost -= AMBUSH_BONUS * (flag(Flag.AMBUSH_DOUBLED) ? 2 : 1);
        }
        return cost;
    }

    /** {@code h <= 0.3}: it takes the ambush bonus. */
    public boolean ambushes() {
        return h <= 0.3 + 1e-9;
    }

    /** The mercy rule: {@code h >= 0.7} (or a gentle giant) spares a faction that lost a base in the last day. */
    public boolean sparesBeaten() {
        return h >= 0.7 - 1e-9 || flag(Flag.ALWAYS_SPARES);
    }

    /** The noise on every decision: sigma {@code 0.2 x v}, doubled for an airhead. */
    public double noiseSigma() {
        return 0.2 * v * (flag(Flag.DOUBLED_NOISE) ? 2 : 1);
    }

    /** The effective trait (0-1) the knobs read. */
    public double trait(Trait t) {
        return switch (t) {
            case AGGRESSION -> a;
            case HONOUR -> h;
            case AMBITION -> m;
            case CAUTION -> c;
            case GREED -> g;
            case SOCIABILITY -> s;
            case LOYALTY -> l;
            case VOLATILITY -> v;
        };
    }

    public double k() {
        return k;
    }
}
