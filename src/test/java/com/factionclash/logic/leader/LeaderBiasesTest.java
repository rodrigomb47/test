package com.factionclash.logic.leader;

import com.factionclash.logic.MinePlanner;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.EconomyFocus;
import com.factionclash.logic.hero.HeroDef.Raids;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LeaderBiases} (HEROES_V2 5.5, 14.3): every knob gives today's constant for a neutral personality, and those
 * constants are checked against the game's own source; each knob is monotonic in the traits it lists and clamped;
 * {@code k = 0} is neutral whatever the leader (traits, focus and temperament rules alike); 5.5's worked table; and
 * the eight slice heroes' kit sheets, number for number.
 */
class LeaderBiasesTest {
    private static final double EPS = 1e-9;

    private static Personality p(Temperament t, int a, int h, int m, int c, int g, int s, int l, int v) {
        Map<Trait, Integer> tr = new EnumMap<>(Trait.class);
        int[] x = {a, h, m, c, g, s, l, v};
        for (Trait q : Trait.values()) {
            tr.put(q, x[q.ordinal()]);
        }
        return new Personality(t, tr);
    }

    private static LeaderBiases b(Personality p) {
        return LeaderBiases.of(p, 1, List.of());
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of("src/main/java/com/factionclash/" + path));
    }

    private static void contains(String src, String what, String file) {
        assertTrue(src.contains(what), file + " no longer says \"" + what + "\": update LeaderBiases' neutral value");
    }

    // ------------------------------------------------------------------ neutral equals today

    @Test
    void neutralEqualsTodaysConstantsInTheGamesOwnSource() throws IOException {
        LeaderBiases n = LeaderBiases.NEUTRAL;
        String config = source("config/FCConfig.java"), director = source("campaign/FactionDirector.java"),
                trader = source("work/TraderJob.java");
        // war.raidIntervalTicks, homeDefenders, rangedShare and the recruitment interval as configured
        contains(config, "defineInRange(\"raidIntervalTicks\", 36000", "FCConfig");
        assertEquals(36000, n.raidIntervalTicks(36000));
        assertEquals(1, n.raidIntervalMultiplier(), EPS);
        contains(config, "defineInRange(\"homeDefenders\", 3", "FCConfig");
        assertEquals(3, n.minHomeDefenders(3));
        contains(config, "defineInRange(\"rangedShare\", 0.4", "FCConfig");
        assertEquals(0.4, n.rangedShare(0.4), EPS);
        contains(config, "defineInRange(\"intervalTicks\", 1200", "FCConfig");
        assertEquals(1, n.recruitIntervalMultiplier(), EPS);
        // FactionDirector.raids
        contains(director, "t - data.startedAt < interval", "FactionDirector");
        assertEquals(LeaderBiases.TODAY_FIRST_RAID_INTERVALS, n.firstRaidIntervals(), EPS);
        assertEquals(36000, n.firstRaidTicks(36000));
        contains(director, "t - f.lastRaidAt < interval / 4", "FactionDirector");
        assertEquals(0.25, n.counterWindow(), EPS);
        contains(director, "partyPower * 2 >= defencePower * 3", "FactionDirector");
        assertEquals(1.5, n.counterOdds(), EPS);
        contains(director, "partyPower * 3 < defencePower * 2", "FactionDirector");
        assertEquals(2.0 / 3, n.attackOdds(), EPS);
        contains(director, "(soldiers.size() + 2) / 3", "FactionDirector");
        for (int s = 0; s <= 40; s++) {
            assertEquals((s + 2) / 3, n.garrisonKept(s), "garrison of " + s);
        }
        contains(director, "f.raiders.size() * 2 < f.raidParty", "FactionDirector");
        assertEquals(0.5, n.fallBackShare(), EPS);
        contains(director, "double score = defence * 1000.0 + Math.sqrt(", "FactionDirector");
        for (double d : new double[]{0, 3, 7.5}) {
            for (double dist : new double[]{0, 40, 250}) {
                assertEquals(d * 1000 + dist, n.targetCost(d, dist, 0.6, false, true), 1e-9, "today's target cost");
                assertEquals(d * 1000 + dist, n.targetCost(d, dist, 0.2, true, false), 1e-9, "lairs too");
            }
        }
        // FactionDirector.needs and projects
        contains(director, "if (soldiers < 12 &&", "FactionDirector");
        assertEquals(12, n.soldierCap());
        contains(director, "if (living(f, b, NpcRole.SWORDSMAN) < 3)", "FactionDirector");
        assertEquals(3, n.homeSwordsmen());
        contains(director, "private static final int TRADER_POPULATION = 14;", "FactionDirector");
        assertEquals(14, n.traderPopulation());
        contains(director, "f.xpBank >= com.factionclash.logic.XpLevels.pointsFor(10)", "FactionDirector");
        assertEquals(10, n.enchanterLevel());
        contains(director, "if (b.beds.size() - pop <= 1)", "FactionDirector");
        assertEquals(1, n.houseFreeBeds());
        contains(director, "FactionStorage.fillRatio(level, b) > 0.7", "FactionDirector");
        assertEquals(0.7, n.storageExpansion(), EPS);
        contains(director, "private static final int REPAIR_SCAN_BUDGET = 384;", "FactionDirector");
        assertEquals(384, n.repairScanBudget());
        // TraderJob, Logistics, MinePlanner
        contains(trader, "private static final long TRIP_INTERVAL = 12000;", "TraderJob");
        assertEquals(12000, n.traderTripTicks());
        assertEquals(1, n.keepBackMultiplier(), EPS);
        assertEquals(64, n.keepBack(64), "the trader's sell list as it is");
        assertEquals(1, n.foodReserveMultiplier(), EPS, "Logistics.foodReserve as it is");
        assertEquals(MinePlanner.GOLD_WANTED, n.goldWanted());
        assertEquals(MinePlanner.DIAMONDS_WANTED, n.diamondsWanted());
        assertEquals(List.of(), n.rolesFirst(), "the need order as it is");
        assertEquals(0, n.noiseSigma() - 0.1, EPS, "sigma 0.2 x 0.5 (the noise is only applied by L3)");
    }

    @Test
    void kZeroIsNeutralWhateverTheLeader() {
        Personality[] extremes = {p(Temperament.BATTLE_MANIAC, 100, 0, 100, 0, 100, 0, 0, 100),
                p(Temperament.AIRHEAD, 0, 100, 0, 100, 0, 100, 100, 0), Personality.defaults(Temperament.GENKI),
                Personality.defaults(Temperament.LONE_WOLF), Personality.defaults(Temperament.TRICKSTER),
                Personality.defaults(Temperament.ANCIENT_SAGE)};
        LeaderBiases n = LeaderBiases.NEUTRAL;
        for (Personality x : extremes) {
            LeaderBiases z = LeaderBiases.of(x, 0, List.of(EconomyFocus.MILITARY, EconomyFocus.DEFENCE,
                    EconomyFocus.GROWTH, EconomyFocus.PEOPLE, EconomyFocus.MINING));
            assertEquals(n.raidIntervalTicks(36000), z.raidIntervalTicks(36000));
            assertEquals(n.firstRaidTicks(36000), z.firstRaidTicks(36000), "no day-3 rule at k = 0");
            assertEquals(n.counterWindow(), z.counterWindow(), EPS);
            assertEquals(n.counterOdds(), z.counterOdds(), EPS);
            assertEquals(n.attackOdds(), z.attackOdds(), EPS);
            assertEquals(n.garrisonShare(), z.garrisonShare(), EPS);
            assertEquals(n.minHomeDefenders(3), z.minHomeDefenders(3), "no defence focus at k = 0");
            assertEquals(n.fallBackShare(), z.fallBackShare(), EPS);
            assertEquals(n.soldierCap(), z.soldierCap(), "no military focus at k = 0");
            assertEquals(n.homeSwordsmen(), z.homeSwordsmen());
            assertEquals(n.rangedShare(0.4), z.rangedShare(0.4), EPS);
            assertEquals(n.traderPopulation(), z.traderPopulation());
            assertEquals(n.enchanterLevel(), z.enchanterLevel());
            assertEquals(n.recruitIntervalMultiplier(), z.recruitIntervalMultiplier(), EPS, "no people focus");
            assertEquals(n.foodReserveMultiplier(), z.foodReserveMultiplier(), EPS);
            assertEquals(n.houseFreeBeds(), z.houseFreeBeds(), "no growth focus, no genki rule at k = 0");
            assertEquals(n.storageExpansion(), z.storageExpansion(), EPS);
            assertEquals(n.repairScanBudget(), z.repairScanBudget());
            assertEquals(n.traderTripTicks(), z.traderTripTicks());
            assertEquals(n.keepBackMultiplier(), z.keepBackMultiplier(), EPS);
            assertEquals(n.goldWanted(), z.goldWanted());
            assertEquals(n.diamondsWanted(), z.diamondsWanted());
            assertEquals(List.of(), z.rolesFirst());
            assertEquals(n.targetCost(4, 100, 1, true, true), z.targetCost(4, 100, 1, true, true), EPS,
                    "no territorial or ambush rule at k = 0");
            assertEquals(n.noiseSigma(), z.noiseSigma(), EPS, "no doubled noise at k = 0");
            assertEquals(n.leaderRaids(), z.leaderRaids());
            assertFalse(z.sparesBeaten() || z.ambushes() || z.vengeanceRaid());
        }
    }

    // ------------------------------------------------------------------ monotonic, clamped

    /** Each knob against each trait it lists: +1 means it grows with the trait, -1 shrinks. */
    @Test
    void everyKnobIsMonotonicInItsTraitsAndClamped() {
        Object[][] knobs = {
                {"raid interval", (ToDoubleFunction<LeaderBiases>) LeaderBiases::raidIntervalMultiplier,
                        Map.of(Trait.AGGRESSION, -1, Trait.CAUTION, 1), 0.5, 1.8},
                {"first raid", (ToDoubleFunction<LeaderBiases>) LeaderBiases::firstRaidIntervals,
                        Map.of(Trait.HONOUR, 1, Trait.CAUTION, 1), 0.5, 1.6},
                {"counter window", (ToDoubleFunction<LeaderBiases>) LeaderBiases::counterWindow,
                        Map.of(Trait.CAUTION, 1, Trait.AGGRESSION, -1), 0.1, 0.5},
                {"counter odds", (ToDoubleFunction<LeaderBiases>) LeaderBiases::counterOdds,
                        Map.of(Trait.CAUTION, 1, Trait.AGGRESSION, -1), 1.0, 2.2},
                {"attack odds", (ToDoubleFunction<LeaderBiases>) LeaderBiases::attackOdds,
                        Map.of(Trait.CAUTION, 1, Trait.AGGRESSION, -1), 0.4, 1.2},
                {"garrison", (ToDoubleFunction<LeaderBiases>) LeaderBiases::garrisonShare,
                        Map.of(Trait.CAUTION, 1, Trait.AGGRESSION, -1), 0.15, 0.6},
                {"home defenders", (ToDoubleFunction<LeaderBiases>) x -> x.minHomeDefenders(3),
                        Map.of(Trait.CAUTION, 1), 2.0, 4.0},
                {"fall back", (ToDoubleFunction<LeaderBiases>) LeaderBiases::fallBackShare,
                        Map.of(Trait.AGGRESSION, 1, Trait.CAUTION, -1, Trait.LOYALTY, 1), 0.3, 0.8},
                {"soldier cap", (ToDoubleFunction<LeaderBiases>) LeaderBiases::soldierCap,
                        Map.of(Trait.AGGRESSION, 1, Trait.AMBITION, 1, Trait.GREED, -1), 7.0, 19.0},
                {"home swordsmen", (ToDoubleFunction<LeaderBiases>) LeaderBiases::homeSwordsmen,
                        Map.of(Trait.CAUTION, 1), 2.0, 4.0},
                {"ranged share", (ToDoubleFunction<LeaderBiases>) x -> x.rangedShare(0.4),
                        Map.of(Trait.CAUTION, 1, Trait.AGGRESSION, -1), 0.15, 0.7},
                {"trader", (ToDoubleFunction<LeaderBiases>) LeaderBiases::traderPopulation,
                        Map.of(Trait.SOCIABILITY, -1, Trait.GREED, -1), 6.0, 20.0},
                {"enchanter", (ToDoubleFunction<LeaderBiases>) LeaderBiases::enchanterLevel,
                        Map.of(Trait.AMBITION, -1), 7.0, 13.0},
                {"recruit interval", (ToDoubleFunction<LeaderBiases>) LeaderBiases::recruitIntervalMultiplier,
                        Map.of(Trait.AMBITION, -1), 0.7, 1.3},
                {"food reserve", (ToDoubleFunction<LeaderBiases>) LeaderBiases::foodReserveMultiplier,
                        Map.of(Trait.CAUTION, 1, Trait.AMBITION, -1), 0.6, 1.5},
                {"house", (ToDoubleFunction<LeaderBiases>) LeaderBiases::houseFreeBeds,
                        Map.of(Trait.AMBITION, 1), 1.0, 3.0},
                {"storage", (ToDoubleFunction<LeaderBiases>) LeaderBiases::storageExpansion,
                        Map.of(Trait.GREED, -1), 0.55, 0.85},
                {"repair budget", (ToDoubleFunction<LeaderBiases>) LeaderBiases::repairScanBudget,
                        Map.of(Trait.CAUTION, 1), 269.0, 500.0},
                {"trader trips", (ToDoubleFunction<LeaderBiases>) LeaderBiases::traderTripMultiplier,
                        Map.of(Trait.SOCIABILITY, -1), 0.6, 1.4},
                {"keep back", (ToDoubleFunction<LeaderBiases>) LeaderBiases::keepBackMultiplier,
                        Map.of(Trait.CAUTION, 1, Trait.GREED, -1), 0.5, 1.5},
                {"gold wanted", (ToDoubleFunction<LeaderBiases>) LeaderBiases::goldWanted,
                        Map.of(Trait.GREED, 1), 8.0, 24.0},
                {"defence weight", (ToDoubleFunction<LeaderBiases>) LeaderBiases::defenceWeight,
                        Map.of(Trait.AGGRESSION, -1, Trait.AMBITION, -1), -0.5, 1.0},
                {"noise", (ToDoubleFunction<LeaderBiases>) LeaderBiases::noiseSigma,
                        Map.of(Trait.VOLATILITY, 1), 0.0, 0.2}};
        for (Object[] kn : knobs) {
            String name = (String) kn[0];
            @SuppressWarnings("unchecked")
            ToDoubleFunction<LeaderBiases> f = (ToDoubleFunction<LeaderBiases>) kn[1];
            @SuppressWarnings("unchecked")
            Map<Trait, Integer> dirs = (Map<Trait, Integer>) kn[2];
            double lo = (double) kn[3], hi = (double) kn[4];
            for (double k : new double[]{0.5, 1, 2}) {
                for (Trait t : Trait.values()) {
                    double prev = Double.NaN;
                    boolean moved = false;
                    for (int v = 0; v <= 100; v += 5) {
                        Map<Trait, Integer> tr = new EnumMap<>(Trait.class);
                        for (Trait q : Trait.values()) {
                            tr.put(q, 50);
                        }
                        tr.put(t, v);
                        double x = f.applyAsDouble(LeaderBiases.of(new Personality(null, tr), k, List.of()));
                        assertTrue(x >= lo - 1e-9 && x <= hi + 1e-9, name + " out of " + lo + "-" + hi + ": " + x);
                        if (!Double.isNaN(prev)) {
                            int d = dirs.getOrDefault(t, 0);
                            if (d == 0) {
                                assertEquals(prev, x, 1e-9, name + " should not read " + t.id());
                            } else {
                                assertTrue(d > 0 ? x >= prev - 1e-9 : x <= prev + 1e-9, name + " not monotonic in "
                                        + t.id() + " at " + v + ", k " + k);
                                moved |= Math.abs(x - prev) > 1e-9;
                            }
                        }
                        prev = x;
                    }
                    if (dirs.containsKey(t)) {
                        assertTrue(moved, name + " never moves with " + t.id() + " at k " + k);
                    }
                }
            }
        }
    }

    @Test
    void extremesStayInsideTheirRangesAtStrengthTwo() {
        for (int corner = 0; corner < 256; corner++) {
            int[] x = new int[8];
            for (int i = 0; i < 8; i++) {
                x[i] = (corner >> i & 1) == 1 ? 100 : 0;
            }
            LeaderBiases b = LeaderBiases.of(p(null, x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7]), 2, List.of());
            assertTrue(b.raidIntervalMultiplier() >= 0.5 && b.raidIntervalMultiplier() <= 1.8);
            assertTrue(b.soldierCap() >= 7 && b.soldierCap() <= 19, "" + b.soldierCap());
            assertTrue(b.defenceWeight() >= -0.5 - 1e-9 && b.defenceWeight() <= 1);
            for (Trait t : Trait.values()) {
                assertTrue(b.trait(t) >= 0 && b.trait(t) <= 1, "t_eff is clamped");
            }
        }
    }

    // ------------------------------------------------------------------ the worked numbers

    @Test
    void theWorkedNumbersOfFiveFive() {
        // HEROES_V2 5.5 at the temperaments' defaults, k = 1
        Object[][] rows = {
                {null, 36000L, 0.667, 1.0 / 3, 3, 12, 0.40, 14},
                {Temperament.BATTLE_MANIAC, 18000L, 0.40, 0.15, 2, 18, 0.175, 16},
                {Temperament.SCHEMER, 40680L, 0.807, 0.40, 3, 13, 0.47, 13},
                {Temperament.SWEET_NATURED, 45000L, 0.767, 0.36, 3, 10, 0.45, 12},
                {Temperament.STOIC, 38160L, 0.757, 0.38, 3, 12, 0.445, 17}};
        for (Object[] r : rows) {
            Temperament t = (Temperament) r[0];
            LeaderBiases b = b(t == null ? Personality.neutral() : Personality.defaults(t));
            String n = t == null ? "neutral" : t.id();
            assertEquals((long) r[1], b.raidIntervalTicks(36000), n);
            assertEquals((double) r[2], b.attackOdds(), 0.0015, n);
            assertEquals((double) r[3], b.garrisonShare(), 0.006, n + " garrison");
            assertEquals((int) r[4], b.minHomeDefenders(3), n);
            assertEquals((int) r[5], b.soldierCap(), n);
            assertEquals((double) r[6], b.rangedShare(0.4), 0.006, n);
            assertEquals((int) r[7], b.traderPopulation(), n);
        }
    }

    /** One slice hero's numbers as its kit sheet prints them ({@code docs/heroes-v2/kits}). */
    private record Sheet(String id, long raid, double first, double odds, double window, double counter,
                         Raids leads, double fall, double garrison, int defenders, int swordsmen, int cap,
                         double ranged, int trader, double trips, double keep, int enchanter, double recruit,
                         double food, int house, double storage, int repair, int gold, int diamonds, double fd,
                         double lair, double sigma, boolean mercy, boolean ambush) {
    }

    @Test
    void theSliceKitSheetsAgreeNumberForNumber() throws IOException {
        Sheet[] sheets = {
                new Sheet("natsuho_komezaki", 29880, 1.06, 0.577, 0.20, 1.37, Raids.LEADS, 0.60, 0.30, 3, 3, 14,
                        0.355, 15, 1.08, 0.93, 9, 0.94, 0.92, 1, 0.685, 372, 17, 8, 0.72, 50, 0.09, false, false),
                new Sheet("yugen_kagaribi", 40680, 0.94, 0.807, 0.31, 1.69, Raids.STAYS_HOME, 0.385, 0.40, 3, 3, 13,
                        0.47, 12, 0.92, 1.04, 8, 0.79, 1.02, 2, 0.64, 430, 19, 10, 0.735, 50, 0.05, false, true),
                new Sheet("benika_akagane", 18000, 0.85, 0.40, 0.10, 1.0, Raids.LEADS, 0.78, 0.15, 2, 2, 19, 0.175,
                        15, 0.96, 0.79, 9, 0.88, 0.6, 2, 0.775, 280, 12, 6, 0.17, 700, 0.12, true, false),
                new Sheet("hinae_shirasuzu", 44640, 1.21, 0.807, 0.325, 1.70, Raids.STAYS_HOME, 0.47, 0.38, 3, 3, 10,
                        0.47, 12, 0.68, 1.23, 11, 1.06 * 0.9, 1.12, 1, 0.775, 407, 12, 6, 1, -550, 0.07, true, false),
                new Sheet("kagenori_tsurumine", 38160, 1.33, 0.757, 0.29, 1.62, Raids.JOINS, 0.525, 0.38, 3, 3, 13,
                        0.445, 18, 1.16, 1.27, 10, 1.03, 1.14, 1, 0.775, 419, 12, 6, 1, -550, 0.02, true, false),
                new Sheet("tobimaru_usugumo", 35640, 1.06, 0.707, 0.26, 1.55, Raids.JOINS, 0.495, 0.36, 3, 3, 13,
                        0.42, 19, 1.32, 1.14, 11, 1.06, 1.12, 1, 0.73, 407, 14, 7, 1, -50, 0.06, false, false),
                new Sheet("ilsabet_frostmere", 37440, 1.3, 0.727, 0.275, 1.58, Raids.JOINS, 0.54, 0.36, 4, 3, 13,
                        0.43, 15, 0.92, 1.29, 10, 0.9, 1.08, 1, 0.805, 407, 10, 5, 1, -500, 0.05, true, false),
                new Sheet("ambrose_nettlewick", 42120, 1.09, 0.757, 0.30, 1.63, Raids.JOINS, 0.44, 0.36, 3, 3, 12,
                        0.445, 14, 0.96, 1.04, 8, 0.85, 0.94, 2, 0.70, 396, 16, 8, 0.975, -300, 0.14, false, false)};
        for (Sheet sh : sheets) {
            var d = com.factionclash.logic.hero.SliceFixtures.load(sh.id());
            Personality p = Personality.of(d.v2().personality());
            LeaderBiases b = LeaderBiases.of(p, 1, d.v2().leader().economyFocus());
            String n = sh.id();
            assertEquals(sh.raid(), b.raidIntervalTicks(36000), n);
            assertEquals(sh.first(), b.firstRaidIntervals(), 0.0051, n);
            assertEquals(sh.odds(), b.attackOdds(), 0.0015, n);
            assertEquals(sh.window(), b.counterWindow(), 0.0051, n);
            assertEquals(sh.counter(), b.counterOdds(), 0.0051, n);
            assertEquals(sh.leads(), b.leaderRaids(), n);
            assertEquals(sh.fall(), b.fallBackShare(), 0.0051, n);
            assertEquals(sh.garrison(), b.garrisonShare(), 0.006, n);
            assertEquals(sh.defenders(), b.minHomeDefenders(3), n);
            assertEquals(sh.swordsmen(), b.homeSwordsmen(), n);
            assertEquals(sh.cap(), b.soldierCap(), n);
            assertEquals(sh.ranged(), b.rangedShare(0.4), 0.0051, n);
            assertEquals(sh.trader(), b.traderPopulation(), n);
            assertEquals(sh.trips(), b.traderTripMultiplier(), 0.0051, n);
            assertEquals(sh.keep(), b.keepBackMultiplier(), 0.0051, n);
            assertEquals(sh.enchanter(), b.enchanterLevel(), n);
            assertEquals(sh.recruit(), b.recruitIntervalMultiplier(), 0.0051, n);
            assertEquals(sh.food(), b.foodReserveMultiplier(), 0.0051, n);
            assertEquals(sh.house(), b.houseFreeBeds(), n);
            assertEquals(sh.storage(), b.storageExpansion(), 0.0051, n);
            assertEquals(sh.repair(), b.repairScanBudget(), n);
            assertEquals(sh.gold(), b.goldWanted(), n);
            assertEquals(sh.diamonds(), b.diamondsWanted(), n);
            assertEquals(sh.fd(), b.defenceWeight(), 0.0051, n);
            assertEquals(sh.lair(), b.targetCost(0, 0, 0, true, false), 0.5, n + " lair term");
            assertEquals(sh.sigma(), b.noiseSigma(), 0.0051, n);
            assertEquals(sh.mercy(), b.sparesBeaten(), n);
            assertEquals(sh.ambush(), b.ambushes(), n);
        }
    }

    // ------------------------------------------------------------------ focus, rules, the rest

    @Test
    void theEconomyFocusMovesRolesOrOneKnob() {
        Personality n = Personality.neutral();
        assertEquals(List.of("miner", "smelter", "chef", "fisherman", "farmer"),
                LeaderBiases.of(n, 1, List.of(EconomyFocus.MINING, EconomyFocus.FOOD)).rolesFirst());
        assertEquals(List.of("chef", "fisherman", "farmer", "hauler"),
                LeaderBiases.of(n, 1, List.of(EconomyFocus.FOOD, EconomyFocus.PEOPLE)).rolesFirst(),
                "in focus order, each role once");
        assertEquals(13, LeaderBiases.of(n, 1, List.of(EconomyFocus.MILITARY)).soldierCap(), "military: cap +1");
        assertEquals(4, LeaderBiases.of(n, 1, List.of(EconomyFocus.DEFENCE)).minHomeDefenders(3));
        assertEquals(2, LeaderBiases.of(n, 1, List.of(EconomyFocus.GROWTH)).houseFreeBeds(), "growth: +1");
        assertEquals(0.9, LeaderBiases.of(n, 1, List.of(EconomyFocus.PEOPLE)).recruitIntervalMultiplier(), EPS);
        for (EconomyFocus f : EconomyFocus.values()) {
            for (String r : LeaderBiases.rolesOf(f)) {
                assertTrue(HeroCatalogue.ROLE_IDS.contains(r) && !List.of("swordsman", "archer", "crossbowman",
                        "axe_fighter").contains(r), r + " is a civilian role");
            }
        }
        assertTrue(LeaderBiases.rolesOf(EconomyFocus.MILITARY).isEmpty());
        assertTrue(LeaderBiases.rolesOf(EconomyFocus.DEFENCE).isEmpty());
    }

    @Test
    void temperamentRulesAndHardRules() {
        assertEquals(3, b(Personality.defaults(Temperament.GENKI)).houseFreeBeds(), "1 + round(4 x 0.25) + genki");
        assertEquals(5, b(Personality.defaults(Temperament.LONE_WOLF)).distanceWeight(), EPS, "territorial");
        assertEquals(5, b(Personality.defaults(Temperament.DELINQUENT)).distanceWeight(), EPS);
        assertEquals(1, b(Personality.defaults(Temperament.STOIC)).distanceWeight(), EPS);
        LeaderBiases trick = b(Personality.defaults(Temperament.TRICKSTER));
        assertTrue(trick.ambushes(), "honour 25");
        assertEquals(-3000, trick.targetCost(0, 0, 0, false, true) - trick.targetCost(0, 0, 0, false, false), EPS,
                "the trickster's ambush bonus doubled");
        LeaderBiases schemer = b(Personality.defaults(Temperament.SCHEMER));
        assertEquals(-1500, schemer.targetCost(0, 0, 0, false, true) - schemer.targetCost(0, 0, 0, false, false), EPS);
        assertTrue(b(Personality.defaults(Temperament.GENTLE_GIANT)).sparesBeaten());
        assertTrue(b(p(Temperament.GENTLE_GIANT, 20, 65, 30, 60, 20, 70, 85, 15)).sparesBeaten(),
                "a gentle giant spares whatever its honour");
        assertFalse(b(p(null, 50, 69, 50, 50, 50, 50, 50, 50)).sparesBeaten());
        assertTrue(b(p(null, 50, 70, 50, 50, 50, 50, 50, 50)).sparesBeaten(), "h >= 0.7");
        assertTrue(b(p(null, 50, 30, 50, 50, 50, 50, 50, 50)).ambushes(), "h <= 0.3");
        assertEquals(2 * 0.2 * 0.95, b(Personality.defaults(Temperament.AIRHEAD)).noiseSigma(), 1e-9, "doubled noise");
        assertEquals(48000, b(Personality.defaults(Temperament.ANCIENT_SAGE)).firstRaidTicks(36000),
                "no raids before day 3");
        assertTrue(b(p(Temperament.HOT_BLOODED, 60, 70, 65, 20, 30, 65, 75, 55)).vengeanceRaid(),
                "hot-blooded: vengeance even under 70");
        assertTrue(b(p(null, 70, 50, 50, 50, 50, 50, 50, 50)).vengeanceRaid());
        assertFalse(b(p(null, 69, 50, 50, 50, 50, 50, 50, 50)).vengeanceRaid());
        // the leader and its raids
        assertEquals(Raids.LEADS, b(p(null, 65, 50, 50, 59, 50, 50, 50, 50)).leaderRaids());
        assertEquals(Raids.JOINS, b(p(null, 65, 50, 50, 60, 50, 50, 50, 50)).leaderRaids());
        assertEquals(Raids.STAYS_HOME, b(p(null, 65, 50, 50, 70, 50, 50, 50, 50)).leaderRaids());
        assertEquals(Raids.STAYS_HOME, b(p(null, 34, 50, 50, 50, 50, 50, 50, 50)).leaderRaids());
        assertEquals(Raids.JOINS, b(p(null, 35, 50, 50, 50, 50, 50, 50, 50)).leaderRaids());
        assertTrue(b(p(null, 50, 50, 50, 50, 50, 50, 40, 50)).raidFallsBackWithoutLeader(), "(c + 1 - l) / 2 > 0.5");
        assertFalse(b(p(null, 50, 50, 50, 50, 50, 50, 50, 50)).raidFallsBackWithoutLeader());
        // loot and lairs
        LeaderBiases greedy = b(p(null, 50, 50, 50, 50, 100, 50, 50, 50));
        assertEquals(-2000, greedy.targetCost(0, 0, 1, false, false), EPS, "2000 x 1 x 0.5 x 2");
        assertEquals(-1000, greedy.targetCost(0, 0, 0.5, false, false), EPS);
        assertEquals(0, b(p(null, 50, 50, 50, 50, 40, 50, 50, 50)).targetCost(0, 0, 1, false, false), EPS,
                "loot only tempts greed over 0.5");
        // rounding: a half rounds up, below zero too
        assertEquals(0, LeaderBiases.round(-0.5));
        assertEquals(-1, LeaderBiases.round(-0.51));
        assertEquals(12, LeaderBiases.round(11.5));
        assertEquals(13, LeaderBiases.round(12.5));
    }

    @Test
    void talentsCombineWithTheBiases() {
        // kit-gaps 9: Ambrose's sage_of_letters (7 at tier I) beside his enchanter level 8: the lower
        assertEquals(7, Talents.enchanterLevel(8, 1));
        assertEquals(5, Talents.enchanterLevel(8, 3));
        assertEquals(6, Talents.enchanterLevel(6, 1), "the talent never makes it later");
        assertEquals(8, Talents.enchanterLevel(8, 0));
        // Ilsabet's warden_of_walls on her 407: x1.5 / x2 / x2.5
        assertEquals(611, Talents.repairScanBudget(407, 1));
        assertEquals(814, Talents.repairScanBudget(407, 2));
        assertEquals(1018, Talents.repairScanBudget(407, 3));
        assertEquals(407, Talents.repairScanBudget(407, 0));
    }
}
