package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.TalentId;
import com.factionclash.logic.hero.HeroRules;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The talent catalogue's numbers (HEROES_V2 7.2; PROPOSED). The numbers belong to the mod, not to the hero files, so
 * 200 authored heroes cannot break the economy. Every talent works from level 1 and only its numbers grow, by tier: I
 * from level 1, II from 10, III from 20 (the owner's "all abilities but not full power": no part of a talent is
 * missing at tier I, {@code mentor}'s recruit level and {@code spymaster}'s raid warning included). A talent works at
 * the base where its hero is (inside the walls or within 16 blocks), not on a raid; one talent of a leader may be
 * {@code realm} and then also works at every other base of the faction at tier I; at a base the strongest copy of
 * each talent counts, never the sum. Pure, unit-tested ({@code TalentsTest}); the game hooks come with L5 and L10.
 */
public final class Talents {
    private Talents() {
    }

    /** A talent works at the base its hero is at: inside the walls or within this many blocks of them. */
    public static final double AT_BASE_MARGIN = 16;

    /** One talent: what it does, its numbers by tier (each quantity I, II, III) and the code it hooks into. */
    public record Spec(TalentId id, String text, Map<String, double[]> numbers, String hook) {
        public Spec {
            Map<String, double[]> m = new LinkedHashMap<>();
            numbers.forEach((k, v) -> m.put(k, v.clone()));
            numbers = Collections.unmodifiableMap(m);
        }

        /** A quantity at a tier (1-3; outside is clamped). */
        public double at(String quantity, int tier) {
            double[] v = numbers.get(quantity);
            if (v == null) {
                throw new IllegalArgumentException(id.id() + " has no " + quantity);
            }
            return v[Math.max(1, Math.min(3, tier)) - 1];
        }
    }

    private static final Map<TalentId, Spec> SPECS = new EnumMap<>(TalentId.class);

    private static void spec(TalentId id, String text, String hook, Object... qs) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (int i = 0; i < qs.length; i += 2) {
            m.put((String) qs[i], (double[]) qs[i + 1]);
        }
        SPECS.put(id, new Spec(id, text, m, hook));
    }

    private static double[] t(double a, double b, double c) {
        return new double[]{a, b, c};
    }

    static {
        spec(TalentId.AURA, "NPCs of the listed roles count extra stacks of one work affix", "FactionNpc.affix",
                "stacks", t(1, 2, 3));
        spec(TalentId.MASTER_CHEF, "meals give more saturation; the chef's batches are faster", "CookingJob, ChefJob",
                "saturation_percent", t(10, 15, 20), "batch_faster_percent", t(10, 20, 30));
        spec(TalentId.FORGEMASTER, "blacksmith work faster; repairs in the cheapest order; Thrifty for smiths",
                "BlacksmithJob", "work_faster_percent", t(15, 25, 35), "thrifty_stacks", t(1, 1, 1));
        spec(TalentId.SURVEYOR, "mine site search radius; the diamond layer one level sooner with an iron pickaxe",
                "MineSites, MinePlanner", "search_radius", t(8, 16, 24), "diamond_levels_sooner", t(1, 1, 1));
        spec(TalentId.FORESTER, "a larger grove; Regrowth", "WoodcutterJob", "grove_saplings", t(4, 8, 12),
                "regrowth_stacks", t(1, 1, 1));
        spec(TalentId.HARVEST_BLESSING, "Green Thumb for farmers; replant first after a harvest", "FarmerJob",
                "green_thumb_stacks", t(1, 2, 3));
        spec(TalentId.BEASTMASTER, "more breeding stock; breeding without idle waits", "PenJob, ButcherJob",
                "breeding_stock", t(2, 4, 6));
        spec(TalentId.ANGLER_LORD, "fishing water searched farther; Angler", "FishingSpots", "search_blocks",
                t(16, 32, 48), "angler_stacks", t(1, 1, 1));
        spec(TalentId.EMBER_KEEPER, "smelting faster; furnaces refuelled before they go cold", "SmelterJob, FurnaceWork",
                "smelt_faster_percent", t(10, 20, 30));
        spec(TalentId.QUARTERMASTER, "shortages warned earlier; haulers carry one more stack; a smaller food reserve",
                "Logistics, HaulerJob", "warn_days_earlier", t(0.5, 1, 1.5), "hauler_extra_stacks", t(1, 1, 1),
                "food_reserve_multiplier", t(0.9, 0.85, 0.8));
        spec(TalentId.RALLY, "recruits cost less food", "Recruitment", "recruit_food_cheaper_percent", t(10, 15, 20));
        spec(TalentId.MENTOR, "NPCs within 16 blocks gain more work XP; recruits start at a higher level",
                "FactionNpc.awardXp, Recruitment", "work_xp_percent", t(15, 25, 35), "recruit_level", t(1.5, 2, 3));
        spec(TalentId.RENOWN, "a shorter recruitment interval; one extra affix roll per recruit",
                "FactionDirector.recruitment, AffixRoller", "recruit_interval_multiplier", t(0.9, 0.8, 0.7),
                "extra_affix_rolls", t(1, 1, 1));
        spec(TalentId.DRILL, "soldiers gain work XP per in-game hour by day", "FactionNpc.awardXp",
                "soldier_xp_per_hour", t(1, 2, 3));
        spec(TalentId.FESTIVAL_HOST, "unlocks the festival edict; festival morale", "morale, edicts",
                "festival_morale", t(15, 20, 25));
        spec(TalentId.STEWARD, "a second base run by this hero uses its personality for that base's knobs",
                "LeaderBiases source per base", "bases", t(1, 1, 1));
        spec(TalentId.MEND, "out of combat, allies within 16 blocks heal a share of max health every 10 s", "v1 mend",
                "heal_percent", t(5, 7, 10), "every_seconds", t(10, 10, 10), "radius", t(16, 16, 16));
        spec(TalentId.TRIAGE, "in a fight, an ally under 30% within 12 blocks is healed once per 30 s for one food item",
                "combat tick, FactionStorage.extract", "heal", t(4, 6, 8), "below_percent", t(30, 30, 30),
                "radius", t(12, 12, 12), "every_seconds", t(30, 30, 30));
        spec(TalentId.REVIVAL_RITE, "fallen heroes of the faction return sooner (never under 30 s)", "HeroManager revival",
                "sooner_percent", t(HeroRules.REVIVAL_RITE_PERCENT[1], HeroRules.REVIVAL_RITE_PERCENT[2],
                        HeroRules.REVIVAL_RITE_PERCENT[3]));
        spec(TalentId.PURIFIER, "allies at the base lose poison, wither and hunger effects", "FactionNpc.tick",
                "every_seconds", t(30, 20, 10));
        spec(TalentId.LOOKOUT, "a line-of-sight alarm", "v1 lookout", "alarm_radius", t(32, 40, 48));
        spec(TalentId.CARTOGRAPHER, "scouts reach farther; each report marks the 3x3 sectors around it explored",
                "ScoutJob, FactionState.explored", "scout_reach", t(16, 32, 48), "explored_sectors", t(9, 9, 9));
        spec(TalentId.SPYMASTER, "base reports carry garrison counts and whether the leader is home; enemy raids"
                + " announced before they reach the walls", "ScoutJob notes, Report.count", "raid_warning_minutes",
                t(0.5, 1, 2), "garrison_counts", t(1, 1, 1));
        spec(TalentId.PATHFINDER, "NPCs and heroes of the base, and member players inside its work belt, walk faster"
                + " outside the walls", "MOVEMENT_SPEED modifier", "speed_percent", t(5, 8, 10));
        spec(TalentId.ENVOY, "pact and peace acceptance; opinion of this faction recovers twice as fast",
                "DiplomacyRules", "acceptance", t(0.1, 0.2, 0.3), "opinion_recovery", t(2, 2, 2));
        spec(TalentId.INTIMIDATION, "rival war councils see a stronger defence; strangers' hostile threshold lower",
                "director target cost", "defence_seen_multiplier", t(1.15, 1.3, 1.45), "stranger_threshold",
                t(-10, -10, -10));
        spec(TalentId.MERCHANT_PRINCE, "trader trips closer together; Haggler; trades with wandering traders",
                "TraderJob", "trip_multiplier", t(0.8, 0.65, 0.5), "haggler_stacks", t(1, 2, 2));
        spec(TalentId.TRIBUTE_MASTER, "peace treaties with weaker factions carry a tribute, carried by a hauler on foot",
                "Diplomacy, HaulerJob", "tributes", t(1, 1, 1));
        spec(TalentId.SAGE_OF_LETTERS, "the enchanter joins at a lower XP level; a library project",
                "enchantingWorthwhile, Project.Type.LIBRARY", "enchanter_level", t(7, 6, 5));
        spec(TalentId.ARCHITECT, "builders faster; more house lots; one style monument", "BuilderJob, BaseLayout",
                "build_faster_percent", t(10, 15, 20), "extra_lots", t(2, 3, 4));
        spec(TalentId.ALCHEMIST, "a brewing project and potions for soldiers once Nether wart is stored",
                "future brewer job", "potion_projects", t(1, 1, 1));
        spec(TalentId.ENGINEER, "rails laid faster", "future rail engineer", "rails_faster_percent", t(20, 30, 40));
        spec(TalentId.WARDEN_OF_WALLS, "a larger defence alarm radius; a larger repair scan budget",
                "defenceAlarm, repairScan", "alarm_radius_plus", t(8, 16, 24), "repair_budget_multiplier",
                t(1.5, 2, 2.5));
        spec(TalentId.TACTICIAN, "raid parties fall back later and gather at cover; joint raids need less strength",
                "FactionDirector.raids", "fall_back_later_percent", t(10, 10, 10), "joint_less_percent",
                t(10, 15, 20));
        spec(TalentId.WAR_DRUMMER, "soldiers, heroes and member players within 16 blocks attack faster during a"
                + " defence", "ATTACK_SPEED modifier", "attack_speed_percent", t(5, 8, 10), "radius", t(16, 16, 16));
    }

    public static Spec spec(TalentId id) {
        return SPECS.get(id);
    }

    /** A quantity of a talent at a tier. */
    public static double value(TalentId id, String quantity, int tier) {
        return SPECS.get(id).at(quantity, tier);
    }

    /** A talent's tier at a hero level: I from 1, II from 10, III from 20. */
    public static int tier(int level) {
        return HeroRules.talentTier(level);
    }

    /** Whether a talent is live in a milestone (0: it waits for its system: alchemist, engineer). */
    public static boolean live(TalentId id, int milestone) {
        return id.milestone > 0 && id.milestone <= milestone;
    }

    /**
     * {@code mentor}'s starting level for the {@code n}-th recruit (0-based) at a tier: tier I every second recruit at
     * level 2 (the others 1), tier II level 2, tier III level 3.
     */
    public static int mentorRecruitLevel(int tier, int n) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> n % 2 == 1 ? 2 : 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    /**
     * One hero's copy of a talent, as a base sees it: the hero's level, whether this talent is its {@code realm}
     * talent (a leader's), and whether the hero is at this base now (inside the walls or within 16 blocks, not on a
     * raid, not away after an escape, not dead).
     */
    public record Copy(TalentId id, int level, boolean realm, boolean atBase) {
    }

    /**
     * The tier of each talent working at a base: the strongest copy counts, never the sum; a copy at the base works
     * at its hero's tier, a {@code realm} copy elsewhere at tier I, any other copy not at all.
     */
    public static Map<TalentId, Integer> atBase(List<Copy> copies) {
        Map<TalentId, Integer> out = new EnumMap<>(TalentId.class);
        for (Copy c : copies) {
            int tier = c.atBase() ? tier(c.level()) : c.realm() ? 1 : 0;
            if (tier > 0) {
                out.merge(c.id(), tier, Math::max);
            }
        }
        return out;
    }

    /**
     * The enchanter's XP level with {@code sage_of_letters} at a tier (0: none) beside the leader's
     * {@link LeaderBiases#enchanterLevel}: the lower of the two, so the talent never makes it later (kit-gaps 9).
     */
    public static int enchanterLevel(int biasesLevel, int sageTier) {
        return sageTier <= 0 ? biasesLevel : Math.min(biasesLevel,
                (int) value(TalentId.SAGE_OF_LETTERS, "enchanter_level", sageTier));
    }

    /**
     * The repair scan budget with {@code warden_of_walls} at a tier (0: none): the leader's
     * {@link LeaderBiases#repairScanBudget} times the talent's multiplier (kit-gaps 9).
     */
    public static int repairScanBudget(int biasesBudget, int wardenTier) {
        return wardenTier <= 0 ? biasesBudget : (int) Math.round(biasesBudget
                * value(TalentId.WARDEN_OF_WALLS, "repair_budget_multiplier", wardenTier));
    }

    /** At most this many talents per hero, and at most one {@code realm}. */
    public static final int MAX_PER_HERO = 3, MAX_REALM = 1;
}
