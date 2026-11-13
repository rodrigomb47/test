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
    public static final ModConfigSpec.BooleanValue PACTS;
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
    public static final ModConfigSpec.IntValue MINE_SITE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_OPEN_MINES;
    public static final ModConfigSpec.IntValue FOREST_RADIUS;
    public static final ModConfigSpec.IntValue GROVE_SIZE;
    public static final ModConfigSpec.IntValue FISHING_RADIUS;
    public static final ModConfigSpec.BooleanValue FISHING_TREASURE;
    public static final ModConfigSpec.BooleanValue REMOTE_WORK_IN_DAYLIGHT_ONLY;
    public static final ModConfigSpec.IntValue HERO_REVIVE_SECONDS;
    public static final ModConfigSpec.IntValue HERO_MIN_REVIVE_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> HERO_DIALOGUE;
    public static final ModConfigSpec.IntValue HERO_DIALOGUE_RADIUS;
    public static final ModConfigSpec.IntValue HERO_DIALOGUE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue HERO_DEFAULT_LINES;
    public static final ModConfigSpec.BooleanValue HERO_SPEECH_BUBBLES;
    public static final ModConfigSpec.BooleanValue HERO_SHOW_ENERGY;
    public static final ModConfigSpec.IntValue HERO_POWER_SCALE;
    public static final ModConfigSpec.IntValue HERO_MAX_HIT;
    public static final ModConfigSpec.IntValue HERO_MAX_DOT_PER_SECOND;
    public static final ModConfigSpec.BooleanValue HERO_SUMMONS;
    public static final ModConfigSpec.IntValue HERO_XP_RATE;
    public static final ModConfigSpec.IntValue BOSS_HEALTH_SCALE;
    public static final ModConfigSpec.BooleanValue BOSS_TELEGRAPH_DECALS;
    public static final ModConfigSpec.BooleanValue BOSS_BARS;
    public static final ModConfigSpec.BooleanValue HERO_ESCAPE;
    public static final ModConfigSpec.IntValue HERO_ESCAPE_AWAY_SECONDS;
    public static final ModConfigSpec.IntValue HERO_ESCAPE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue HERO_SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue HERO_VOICE_BLIPS;
    public static final ModConfigSpec.BooleanValue HERO_TELEGRAPH_CUES;
    public static final ModConfigSpec.IntValue HERO_POWER_PERCENT;

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

        b.comment("Workers: where miners, woodcutters and fishermen work (PROPOSED defaults)").push("workplaces");
        MINE_SITE_RADIUS = b.comment("Farthest distance (blocks, along either axis) from the base centre at which a miner opens a mine.",
                        "The nearest is fixed at the walls' half-size + 8 (32 blocks).")
                .defineInRange("mineSiteRadius", 64, 40, 128);
        MAX_OPEN_MINES = b.comment("Mines a base may be working at once (unfinished and not resting). Each has a miner of its own;",
                        "while every site is taken, a spare miner helps in one, on another of its levels.")
                .defineInRange("maxOpenMines", 3, 1, 8);
        FOREST_RADIUS = b.comment("Woodcutters fell natural trees within this many blocks of the base centre.",
                        "When there are none, they walk 72 blocks out from the centre to look for more. At least 41, so a new base's starter trees are in it.")
                .defineInRange("forestRadius", 64, com.factionclash.logic.ForestRules.MIN_FOREST_RADIUS, 128);
        GROVE_SIZE = b.comment("Saplings the woodcutter keeps growing in the base's always-ticking work area, so treeless land still",
                        "gives wood (twice as many while the base is short of wood). 0 = only replant felled trees.")
                .defineInRange("groveSize", com.factionclash.logic.ForestRules.DEFAULT_GROVE_SIZE, 0,
                        com.factionclash.logic.ForestRules.MAX_GROVE_SIZE);
        FISHING_RADIUS = b.comment("Fishing water is searched for within this many blocks of the base centre.",
                        "Water a fisherman finds while foraging may be up to 40 blocks further.")
                .defineInRange("fishingRadius", 64, 32, 128);
        FISHING_TREASURE = b.comment("In open water (5x5, 2 deep, open sky, as for a player) the vanilla treasure catches can be fished.")
                .define("fishingTreasure", true);
        REMOTE_WORK_IN_DAYLIGHT_ONLY = b.comment("Between dusk and dawn (day time 13000-23000), woodcutters and fishermen do not work more than",
                        "16 blocks outside the walls, where the guards cannot protect them.")
                .define("remoteWorkInDaylightOnly", true);
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
        PACTS = b.comment("When a human faction conquers another, the surviving factions (two or more) swear a pact against",
                        "the conqueror: no fighting between them, the conqueror's bases revealed, joint raids on it.")
                .define("pacts", true);
        HOME_DEFENDERS = b.comment("Soldiers a faction always keeps at home.")
                .defineInRange("homeDefenders", 3, 0, 20);
        RAID_PARTY_SIZE = b.comment("Smallest raid party. A faction sends every soldier beyond a third of its garrison (keeping at",
                        "least homeDefenders at home), so with 12 soldiers it raids with 8.")
                .defineInRange("raidPartySize", 3, 1, 20);
        RAID_INTERVAL_TICKS = b.comment("Minimum ticks between raids launched by one faction.")
                .defineInRange("raidIntervalTicks", 36000, 1200, 480000);
        LAIR_BASE_CAP = b.comment("Monsters a Hollow lair keeps at the start.")
                .defineInRange("lairBaseCap", 5, 0, 40);
        LAIR_MAX_CAP = b.comment("Monsters a Hollow lair grows to over time.")
                .defineInRange("lairMaxCap", 12, 0, 80);
        LAIR_RAID_INTERVAL_TICKS = b.comment("Minimum ticks between Hollow raids from one lair.")
                .defineInRange("lairRaidIntervalTicks", 30000, 1200, 480000);
        HERO_POWER_PERCENT = b.comment("How much a hero weighs in the war councils' odds, in percent of its war power (PROPOSED: 100;",
                        "a level-10 epic leader is worth about five iron soldiers). 0 counts a hero as one soldier by heads, as before.")
                .defineInRange("heroPowerPercent", 100, 0, 200);
        b.pop();

        b.comment("Heroes (in development: only the test model ships yet; an operator gives one with /fc admin hero give)").push("heroes");
        HERO_REVIVE_SECONDS = b.comment("A hero that falls returns at its home base this many seconds later (PROPOSED: 600, ten minutes).",
                        "A hero's own revival ability and the revival_rite talent can change that, never under minReviveSeconds.")
                .defineInRange("reviveSeconds", 600, 30, 3600);
        HERO_MIN_REVIVE_SECONDS = b.comment("No ability brings a fallen hero back sooner than this (PROPOSED: 30), so it never rises in the fight",
                        "that killed it.")
                .defineInRange("minReviveSeconds", 30, 30, 3600);
        HERO_DIALOGUE = b.comment("Who hears heroes' lines: local (players within dialogueRadius; faction news to the faction), faction",
                        "(only the hero's own faction) or off.")
                .define("dialogue", "local", v -> v instanceof String t && (t.equals("local") || t.equals("faction") || t.equals("off")));
        HERO_DIALOGUE_RADIUS = b.comment("Blocks within which players hear a hero's local lines.")
                .defineInRange("dialogueRadius", 32, 8, 128);
        HERO_DIALOGUE_COOLDOWN_SECONDS = b.comment("At most one line per hero this often (summoned, fallen, revived, ultimate, intro, enrage, escape, last stand, phase changes and greetings ignore it).")
                .defineInRange("dialogueCooldownSeconds", 20, 5, 300);
        HERO_DEFAULT_LINES = b.comment("Short generic lines for the events a hero's own file has no line for.")
                .define("defaultLines", true);
        HERO_SPEECH_BUBBLES = b.comment("Show a hero's line above its head for 3 seconds as well as in chat.")
                .define("speechBubbles", true);
        HERO_SHOW_ENERGY = b.comment("Show a fighting hero's energy on its name tag: [Fighting 80%].")
                .define("showEnergy", true);
        HERO_POWER_SCALE = b.comment("Multiplies every amount of every hero's moves (damage, damage over time, heals, shields), in percent",
                        "(PROPOSED: 100). The same against every target, player or NPC.")
                .defineInRange("powerScale", 100, 25, 200);
        HERO_MAX_HIT = b.comment("One hit of a hero's move deals at most this much damage, whoever it lands on (PROPOSED: 40).")
                .defineInRange("maxHit", 40, 10, 100);
        HERO_MAX_DOT_PER_SECOND = b.comment("One hero's damage over time on one target deals at most this much per second (PROPOSED: 6).")
                .defineInRange("maxDotPerSecond", 6, 1, 20);
        HERO_SUMMONS = b.comment("Heroes may summon wolves and golems that fight for their side for a while (at most 3 a hero, 12 a faction).")
                .define("summons", true);
        HERO_XP_RATE = b.comment("Multiplies the experience heroes earn from fights, raids, defences and days alive, in percent (PROPOSED: 100).")
                .defineInRange("xpRate", 100, 0, 500);
        HERO_ESCAPE = b.comment("A hero of the escaping kind breaks off a fight it is losing and runs (HEROES_V2 6.13). false: every hero",
                        "fights to the death.")
                .define("escape", true);
        HERO_ESCAPE_AWAY_SECONDS = b.comment("How long an escaped hero stays away from those it fled, in seconds (PROPOSED: 300).")
                .defineInRange("escapeAwaySeconds", 300, 60, 1200);
        HERO_ESCAPE_COOLDOWN_SECONDS = b.comment("At most one escape per hero in this many seconds (PROPOSED: 600); caught again inside",
                        "that time, it fights to the death.")
                .defineInRange("escapeCooldownSeconds", 600, 120, 1800);
        HERO_SOUND_VOLUME = b.comment("Multiplies the volume of every hero sound and cue (SOUND.md; 0 silences them). At most 1: a louder",
                        "play would only be clamped by the game and carry farther.")
                .defineInRange("soundVolume", 1.0, 0.0, 1.0);
        HERO_VOICE_BLIPS = b.comment("Heroes speak their lines in voice blips (each hero's own voice, or a note block for a hero without one).")
                .define("voiceBlips", true);
        HERO_TELEGRAPH_CUES = b.comment("Play the shared wind-up cue of a hero's move class (light, heavy, area, ultimate, unblockable), timed",
                        "to end on the hit. false leaves only the hero's own wind-up sounds (presentation only).")
                .define("telegraphCues", true);
        b.pop();

        b.comment("Heroes as bosses: one mode against anyone, player or NPC (HEROES_V2 6)").push("boss");
        BOSS_HEALTH_SCALE = b.comment("Multiplies every hero's boss health, in percent (PROPOSED: 100). A hero's health is its v1 health x its",
                        "health_scale (rarity: common 250, rare 300, epic 350, legendary 400) x its role factor, whoever and however many it fights.")
                .defineInRange("healthScale", 100, 25, 300);
        BOSS_TELEGRAPH_DECALS = b.comment("Draw a growing ground mark under a hero's heavy, area and ultimate moves for the players within 32",
                        "blocks (presentation only: a move's timing, reach and damage never depend on it).")
                .define("telegraphDecals", true);
        BOSS_BARS = b.comment("Show a fighting hero's boss bar, and its intro title once a day, to the players within 32 blocks",
                        "(presentation only: nothing in a fight depends on it).")
                .define("bossBars", true);
        b.pop();
        SPEC = b.build();
    }

    private FCConfig() {
    }
}
