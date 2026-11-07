package com.factionclash.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay configuration. Every numeric value here is a PROPOSED default: the original project's
 * numbers were not available, so they are exposed for tuning and documented in BALANCE.md.
 */
public final class FCConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAP_RADIUS;
    public static final ModConfigSpec.IntValue HUMAN_FACTIONS;
    public static final ModConfigSpec.IntValue MONSTER_LAIRS;
    public static final ModConfigSpec.IntValue MINE_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue CHOP_TICKS_PER_LOG;
    public static final ModConfigSpec.IntValue TEND_TICKS;
    public static final ModConfigSpec.IntValue RECRUIT_FOOD_VALUE;
    public static final ModConfigSpec.IntValue RECRUIT_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SCOUT_RANGE;
    public static final ModConfigSpec.IntValue SCOUT_OVERDUE_TICKS;
    public static final ModConfigSpec.IntValue REPORT_STALE_TICKS;
    public static final ModConfigSpec.DoubleValue RANGED_SHARE;
    public static final ModConfigSpec.IntValue HOME_DEFENDERS;
    public static final ModConfigSpec.IntValue RAID_PARTY_SIZE;
    public static final ModConfigSpec.IntValue RAID_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LAIR_BASE_CAP;
    public static final ModConfigSpec.IntValue LAIR_MAX_CAP;
    public static final ModConfigSpec.IntValue LAIR_RAID_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue KEEP_FACTION_AREAS_LOADED;
    public static final ModConfigSpec.BooleanValue AUTO_START;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Campaign layout (PROPOSED defaults)").push("campaign");
        MAP_RADIUS = b.comment("Half-width in blocks of the square campaign map centred on world spawn.")
                .defineInRange("mapRadius", 320, 160, 4096);
        HUMAN_FACTIONS = b.comment("Number of human factions (Ember Legion, Grove Covenant, Tide Alliance, Stoneguard).")
                .defineInRange("humanFactions", 4, 2, 4);
        MONSTER_LAIRS = b.comment("Number of The Hollow monster lairs.")
                .defineInRange("monsterLairs", 2, 0, 4);
        AUTO_START = b.comment("Start a campaign automatically when a world without one loads.")
                .define("autoStart", false);
        KEEP_FACTION_AREAS_LOADED = b.comment("Keep bases, workplaces and travelling NPCs' chunks ticking so factions progress away from players.")
                .define("keepFactionAreasLoaded", true);
        b.pop();

        b.comment("Workspace production (PROPOSED defaults)").push("work");
        MINE_CYCLE_TICKS = b.comment("Base ticks per mine output with a wooden pickaxe; better pickaxes are faster.")
                .defineInRange("mineCycleTicks", 140, 20, 2400);
        CHOP_TICKS_PER_LOG = b.comment("Ticks to chop one log in a tree plot.")
                .defineInRange("chopTicksPerLog", 24, 5, 400);
        TEND_TICKS = b.comment("Ticks per tending action on a sapling (each action advances growth like bone meal).")
                .defineInRange("tendTicks", 120, 20, 2400);
        b.pop();

        b.comment("Recruitment (PROPOSED defaults)").push("recruitment");
        RECRUIT_FOOD_VALUE = b.comment("Hunger points of food consumed per recruit (bread=5, cooked beef=8, ...).")
                .defineInRange("foodValue", 20, 0, 400);
        RECRUIT_INTERVAL_TICKS = b.comment("Minimum ticks between recruitments at one base.")
                .defineInRange("intervalTicks", 1200, 100, 72000);
        RANGED_SHARE = b.comment("Desired share of soldiers that are archers.")
                .defineInRange("rangedShare", 0.4, 0.0, 1.0);
        b.pop();

        b.comment("Scouting and war (PROPOSED defaults)").push("war");
        SCOUT_RANGE = b.comment("Maximum distance from home a scout explores.")
                .defineInRange("scoutRange", 160, 32, 2048);
        SCOUT_OVERDUE_TICKS = b.comment("A scout away longer than this is presumed lost and replaced.")
                .defineInRange("scoutOverdueTicks", 24000, 1200, 240000);
        REPORT_STALE_TICKS = b.comment("Enemy sightings older than this are shown as stale.")
                .defineInRange("reportStaleTicks", 24000, 1200, 480000);
        HOME_DEFENDERS = b.comment("Soldiers a faction always keeps at home.")
                .defineInRange("homeDefenders", 3, 0, 20);
        RAID_PARTY_SIZE = b.comment("Soldiers sent on a raid.")
                .defineInRange("raidPartySize", 3, 1, 20);
        RAID_INTERVAL_TICKS = b.comment("Minimum ticks between raids launched by one faction.")
                .defineInRange("raidIntervalTicks", 36000, 1200, 480000);
        LAIR_BASE_CAP = b.comment("Monsters a Hollow lair keeps at the start.")
                .defineInRange("lairBaseCap", 5, 0, 40);
        LAIR_MAX_CAP = b.comment("Monsters a Hollow lair grows to over time.")
                .defineInRange("lairMaxCap", 12, 0, 80);
        LAIR_RAID_INTERVAL_TICKS = b.comment("Minimum ticks between Hollow raids from one lair.")
                .defineInRange("lairRaidIntervalTicks", 30000, 1200, 480000);
        b.pop();
        SPEC = b.build();
    }

    private FCConfig() {
    }
}
