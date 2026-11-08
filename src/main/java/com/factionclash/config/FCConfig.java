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
    public static final ModConfigSpec.IntValue UNSTUCK_HORIZONTAL;
    public static final ModConfigSpec.IntValue UNSTUCK_VERTICAL;
    public static final ModConfigSpec.BooleanValue HUNGER;
    public static final ModConfigSpec.BooleanValue TOOL_WEAR;
    public static final ModConfigSpec.DoubleValue WORK_EXHAUSTION_PER_DAY;
    public static final ModConfigSpec.BooleanValue ACHIEVEMENTS;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_ACHIEVEMENTS;
    public static final ModConfigSpec.BooleanValue GRANT_ACHIEVEMENTS_TO_PLAYERS;

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

        b.comment("NPC movement (PROPOSED defaults)").push("movement");
        UNSTUCK_HORIZONTAL = b.comment("How far across (blocks) the stuck-recovery teleport may move an NPC to a safe spot nearer its goal.")
                .defineInRange("unstuckHorizontal", 4, 1, 8);
        UNSTUCK_VERTICAL = b.comment("How far up or down (blocks) the stuck-recovery teleport may move an NPC.")
                .defineInRange("unstuckVertical", 2, 1, 4);
        b.pop();
        b.comment("Survival: NPCs get hungry and their gear wears out like a player's").push("survival");
        HUNGER = b.comment("NPCs have a food bar like a player's (food, saturation, exhaustion): they eat from what they carry,",
                        "regenerate only when fed and starve when empty (down to 10 health on Easy, 1 on Normal, death on Hard).")
                .define("hunger", true);
        TOOL_WEAR = b.comment("Tools, weapons, bows and armour lose durability as a player's do and break when used up.",
                        "The blacksmith repairs good gear at the anvil and crafts replacements.")
                .define("toolWear", true);
        WORK_EXHAUSTION_PER_DAY = b.comment("Exhaustion a working NPC builds up per in-game day on top of what its actions cost the vanilla way",
                        "(walking costs a player nothing). 4 exhaustion = 1 food or saturation point. PROPOSED: 30 (~1.5 bread a day).")
                .defineInRange("workExhaustionPerDay", 30.0, 0.0, 400.0);
        b.pop();
        b.comment("Faction achievements: the vanilla advancements, made by a faction").push("achievements");
        ACHIEVEMENTS = b.comment("Factions make the vanilla advancements through what their NPCs do the way a player would,",
                        "and through their players' own advancements.")
                .define("enabled", true);
        ANNOUNCE_ACHIEVEMENTS = b.comment("Announce a faction's advancements in chat, like a player's (only those vanilla announces).")
                .define("announce", true);
        GRANT_ACHIEVEMENTS_TO_PLAYERS = b.comment("Also give the faction's advancements to its players as real advancements",
                        "(on joining, logging in, and whenever the faction makes a new one).")
                .define("grantToFactionPlayers", false);
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
