package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Aggression;
import com.factionclash.logic.hero.HeroDef.BaseType;
import com.factionclash.logic.hero.HeroDef.ConditionType;
import com.factionclash.logic.hero.HeroDef.Creature;
import com.factionclash.logic.hero.HeroDef.EffectType;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.GearSlot;
import com.factionclash.logic.hero.HeroDef.Growth;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.PassiveForm;
import com.factionclash.logic.hero.HeroDef.Protects;
import com.factionclash.logic.hero.HeroDef.Raids;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.Stat;
import com.factionclash.logic.hero.HeroDef.Style;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroDef.UseWhen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single source of the hero format (HERO_FORMAT.md, "v1"; v1 26.1 step 2): every field with its kind, unit, range
 * and default; every word list with its synonyms (v1 Appendix C); the vanilla allowlists (Appendix B); role presets
 * and growth profiles (5.2, 5.3); the form, trigger, placeholder and recipient tables; slot caps and the weight
 * constants. {@link HeroReader} and {@link HeroBalance} read their numbers from here and nowhere else, so the JSON
 * Schema generator of HEROES_V2 15.2 F2 can be driven by the same tables. Pure Java, no Minecraft classes.
 */
public final class HeroCatalogue {
    private HeroCatalogue() {
    }

    // ------------------------------------------------------------------ file limits (v1 3, 26.5)

    /** The newest {@code format} this build reads. */
    public static final int MAX_FORMAT = 1;
    public static final int MAX_FILE_BYTES = 128 * 1024;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_STRING = 1000;
    public static final int MAX_LOADED = 64;
    /** At most this many problems are listed per hero (v1 26.5: a file can never flood the log or the chat). */
    public static final int MAX_PROBLEMS_PER_HERO = 200;
    public static final int ID_MIN = 3, ID_MAX = 32, MARK_ID_MIN = 2, MARK_ID_MAX = 24;

    // ------------------------------------------------------------------ fields

    public enum Kind { INT, NUM, BOOL, TEXT, WORD, COLOR, ID, MARK, LIST, OBJECT, AMOUNT, CONDITION, MAP }

    /** What a number measures; text such as {@code "8 seconds"} or {@code "25%"} is read in the field's unit. */
    public enum Unit {
        NONE(""), SECONDS("seconds"), BLOCKS("blocks"), PERCENT("%"), HEALTH("health points"), DEGREES("degrees"),
        LEVEL("");

        public final String label;

        Unit(String label) {
            this.label = label;
        }
    }

    /**
     * One field of an object. Numbers: {@code min}..{@code max}, default {@code dflt} (NaN: no default, the field is
     * absent). Text: {@code min}..{@code max} characters. Lists: {@code min}..{@code max} items. Words: the
     * {@link Vocab} named {@code vocab}.
     */
    public record Field(String name, Kind kind, Unit unit, double min, double max, double dflt, String vocab,
                        boolean required) {
        public boolean whole() {
            return kind == Kind.INT;
        }
    }

    private static Field num(String n, Unit u, double min, double max, double dflt) {
        return new Field(n, Kind.NUM, u, min, max, dflt, null, false);
    }

    private static Field whole(String n, Unit u, int min, int max, double dflt) {
        return new Field(n, Kind.INT, u, min, max, dflt, null, false);
    }

    private static Field word(String n, String vocab) {
        return new Field(n, Kind.WORD, Unit.NONE, 0, 0, Double.NaN, vocab, false);
    }

    private static Field text(String n, int min, int max) {
        return new Field(n, Kind.TEXT, Unit.NONE, min, max, Double.NaN, null, false);
    }

    private static Field list(String n, int min, int max) {
        return new Field(n, Kind.LIST, Unit.NONE, min, max, Double.NaN, null, false);
    }

    private static Field of(String n, Kind k) {
        return new Field(n, k, Unit.NONE, 0, 0, Double.NaN, null, false);
    }

    private static Field req(Field f) {
        return new Field(f.name, f.kind, f.unit, f.min, f.max, f.dflt, f.vocab, true);
    }

    private static Map<String, Field> shape(Field... fs) {
        Map<String, Field> m = new LinkedHashMap<>();
        for (Field f : fs) {
            m.put(f.name, f);
        }
        return Collections.unmodifiableMap(m);
    }

    public static final Map<String, Field> TOP = shape(
            req(whole("format", Unit.NONE, 1, 99, Double.NaN)), of("id", Kind.ID), req(text("name", 1, 24)),
            text("title", 0, 32), text("description", 0, 300), word("rarity", "rarity"),
            word("starter_for", "faction"), list("factions", 1, 4), word("skin_model", "skin_model"),
            of("color", Kind.COLOR), word("element", "element"), of("stats", Kind.OBJECT),
            of("resistances", Kind.MAP), list("gear", 0, 6), req(of("behaviour", Kind.OBJECT)),
            req(of("basic_attack", Kind.OBJECT)), list("skills", 0, 4), of("ultimate", Kind.OBJECT),
            list("passives", 0, 4), of("empowered", Kind.OBJECT), list("base_abilities", 0, 3), of("lines", Kind.MAP));

    public static final Map<String, Field> STATS = shape(
            num("health", Unit.HEALTH, 20, 40, Double.NaN), num("attack", Unit.HEALTH, 0, 4, Double.NaN),
            num("armor", Unit.NONE, 0, 8, Double.NaN), whole("speed", Unit.PERCENT, 90, 120, Double.NaN),
            whole("knockback_resistance", Unit.PERCENT, 0, 60, Double.NaN), whole("size", Unit.PERCENT, 85, 110, 100),
            word("growth", "growth"));

    public static final Map<String, Field> BEHAVIOUR = shape(
            req(word("combat_role", "combat_role")), word("focus", "focus"),
            num("keep_distance", Unit.BLOCKS, 0, 16, Double.NaN), word("aggression", "aggression"),
            word("protects", "protects"), whole("retreat_below", Unit.PERCENT, 0, 60, Double.NaN),
            word("raids", "raids"), word("duty", "duty"));

    public static final Map<String, Field> BASIC_ATTACK = shape(
            req(word("style", "style")), word("projectile", "item_look"), word("held_item", "held_prop"),
            num("amount", Unit.HEALTH, 2, 10, 4), num("interval", Unit.SECONDS, 0.8, 3, 1.5),
            word("element", "element"), list("on_hit", 1, 3), of("every_nth_hit", Kind.OBJECT));

    /** Fields of {@code basic_attack} only style {@code thrown} uses. */
    public static final List<String> THROWN_ONLY = List.of("projectile", "held_item", "amount", "interval", "element");

    public static final Map<String, Field> NTH_HIT = shape(req(whole("n", Unit.NONE, 2, 6, Double.NaN)),
            req(list("effects", 1, 3)));

    /** Skills and the ultimate share one shape (v1 10); form fields are ranged per form ({@link #FORMS}). */
    public static final Map<String, Field> ABILITY = shape(
            req(text("name", 1, 24)), text("description", 0, 200), req(word("form", "form")),
            num("range", Unit.BLOCKS, 2, 24, Double.NaN), num("radius", Unit.BLOCKS, 0, 12, Double.NaN),
            whole("count", Unit.NONE, 2, 5, 3), whole("angle", Unit.DEGREES, 30, 120, 70),
            num("width", Unit.BLOCKS, 1, 3, 1), whole("hits", Unit.NONE, 1, 5, 1),
            num("hit_interval", Unit.SECONDS, 0.2, 1, 0.3), word("projectile", "projectile"),
            word("zone_at", "zone_at"), num("delay", Unit.SECONDS, 0, 3, Double.NaN),
            num("duration", Unit.SECONDS, 0, 10, 0), num("interval", Unit.SECONDS, 0.5, 3, 1),
            num("windup", Unit.SECONDS, 0, 2, Double.NaN), num("cooldown", Unit.SECONDS, 2, 120, 8),
            whole("charges", Unit.NONE, 1, 3, 1), whole("energy_gain", Unit.NONE, 0, 30, 10),
            whole("charge_rate", Unit.PERCENT, 50, 150, 100), word("use_when", "use_when"),
            num("use_value", Unit.NONE, 2, 90, Double.NaN), whole("unlock_level", Unit.LEVEL, 1, 30, 1),
            req(list("effects", 1, 5)), of("look", Kind.OBJECT), text("line", 1, 120));

    public static final List<String> SKILL_ONLY = List.of("cooldown", "charges", "energy_gain");

    public static final Map<String, Field> EFFECT_COMMON = shape(req(word("type", "effect_type")), word("to", "to"),
            num("radius", Unit.BLOCKS, 2, 12, 8), whole("chance", Unit.PERCENT, 1, 100, 100),
            of("if", Kind.CONDITION));

    /** Each effect type's own fields (v1 13). */
    public static final Map<EffectType, Map<String, Field>> EFFECTS = new EnumMap<>(EffectType.class);

    static {
        EFFECTS.put(EffectType.DAMAGE, shape(req(of("amount", Kind.AMOUNT)), word("element", "element"),
                whole("drain", Unit.PERCENT, 0, 50, 0)));
        EFFECTS.put(EffectType.DOT, shape(req(of("amount", Kind.AMOUNT)), num("seconds", Unit.SECONDS, 2, 10, 4),
                word("element", "element"), whole("max_stacks", Unit.NONE, 1, 5, 1)));
        EFFECTS.put(EffectType.HEAL, shape(req(of("amount", Kind.AMOUNT))));
        EFFECTS.put(EffectType.SHIELD, shape(req(of("amount", Kind.AMOUNT)), num("seconds", Unit.SECONDS, 2, 30, 10)));
        EFFECTS.put(EffectType.EFFECT, shape(req(word("id", "potion")), whole("level", Unit.LEVEL, 1, 3, 1),
                req(num("seconds", Unit.SECONDS, 1, 30, Double.NaN))));
        EFFECTS.put(EffectType.STAT, shape(req(word("stat", "stat")), req(whole("percent", Unit.PERCENT, -50, 60,
                Double.NaN)), num("seconds", Unit.SECONDS, 2, 30, 8), whole("max_stacks", Unit.NONE, 1, 5, 1)));
        EFFECTS.put(EffectType.KNOCKBACK, shape(num("strength", Unit.NONE, 1, 5, 2), num("up", Unit.BLOCKS, 0, 3, 0)));
        EFFECTS.put(EffectType.PULL, shape(num("strength", Unit.NONE, 1, 3, 1)));
        EFFECTS.put(EffectType.IGNITE, shape(num("seconds", Unit.SECONDS, 1, 8, 4)));
        EFFECTS.put(EffectType.FREEZE, shape(num("seconds", Unit.SECONDS, 1, 6, 3)));
        EFFECTS.put(EffectType.STUN, shape(num("seconds", Unit.SECONDS, 0.5, 2, 1)));
        EFFECTS.put(EffectType.TAUNT, shape(num("seconds", Unit.SECONDS, 1, 8, 3)));
        EFFECTS.put(EffectType.CLEANSE, shape());
        EFFECTS.put(EffectType.DISPEL, shape());
        EFFECTS.put(EffectType.ENERGY, shape(req(whole("amount", Unit.NONE, 5, 30, Double.NaN))));
        EFFECTS.put(EffectType.COOLDOWN_CUT, shape(req(num("seconds", Unit.SECONDS, 1, 10, Double.NaN))));
        EFFECTS.put(EffectType.MOVE_SELF, shape(word("direction", "direction"), num("distance", Unit.BLOCKS, 2, 8, 4)));
        EFFECTS.put(EffectType.MARK, shape(req(of("mark", Kind.MARK)), whole("stacks", Unit.NONE, 1, 5, 1),
                whole("max_stacks", Unit.NONE, 1, 10, 5), num("seconds", Unit.SECONDS, 2, 30, 10),
                of("glow", Kind.BOOL)));
        EFFECTS.put(EffectType.CLEAR_MARKS, shape(req(of("mark", Kind.MARK))));
        EFFECTS.put(EffectType.EMPOWER, shape(req(num("seconds", Unit.SECONDS, 3, 20, Double.NaN))));
        EFFECTS.put(EffectType.SUMMON, shape(req(word("creature", "creature")), whole("count", Unit.NONE, 1, 3, 1),
                num("seconds", Unit.SECONDS, 10, 60, 20), text("name", 1, 24)));
    }

    public static final Map<String, Field> AMOUNT = shape(num("base", Unit.HEALTH, 0, 30, 0),
            whole("attack_percent", Unit.PERCENT, 0, 300, 0), whole("max_health_percent", Unit.PERCENT, 0, 40, 0),
            whole("armor_percent", Unit.PERCENT, 0, 100, 0), whole("target_max_health_percent", Unit.PERCENT, 0, 20, 0),
            whole("target_missing_health_percent", Unit.PERCENT, 0, 30, 0), of("mark", Kind.MARK),
            num("per_mark_stack", Unit.HEALTH, 0, 6, 0));

    public static final Map<String, Field> CONDITION = shape(req(word("condition", "condition")),
            whole("value", Unit.NONE, 1, 99, Double.NaN), of("mark", Kind.MARK), word("kind", "target_kind"));

    public static final Map<String, Field> LOOK = shape(word("particle", "particle"), of("color", Kind.COLOR),
            word("sound", "sound"), word("pose", "pose"), of("lightning", Kind.BOOL), of("fangs", Kind.BOOL));

    public static final Map<String, Field> PASSIVE = shape(req(text("name", 1, 24)), text("description", 0, 200),
            req(word("trigger", "trigger")), num("trigger_value", Unit.NONE, 3, 60, Double.NaN), of("mark", Kind.MARK),
            whole("chance", Unit.PERCENT, 1, 100, 100), num("cooldown", Unit.SECONDS, 1, 120, 1),
            word("form", "passive_form"), num("radius", Unit.BLOCKS, 2, 12, Double.NaN),
            word("projectile", "projectile"), of("if", Kind.CONDITION), req(list("effects", 1, 3)),
            of("look", Kind.OBJECT), text("line", 1, 120), whole("unlock_level", Unit.LEVEL, 1, 30, 1));

    public static final Map<String, Field> EMPOWERED = shape(req(text("name", 1, 24)), text("description", 0, 200),
            list("stats", 1, 4), list("on_hit", 1, 2), word("particle", "particle"), of("color", Kind.COLOR),
            whole("size", Unit.PERCENT, 100, 110, 100), of("skills_locked", Kind.BOOL), text("line", 1, 120));

    public static final Map<String, Field> STAT_BONUS = shape(req(word("stat", "stat")),
            req(whole("percent", Unit.PERCENT, -30, 30, Double.NaN)));

    public static final Map<String, Field> BASE_COMMON = shape(req(word("type", "base_type")), text("name", 1, 24),
            whole("unlock_level", Unit.LEVEL, 1, 30, 1));

    public static final Map<BaseType, Map<String, Field>> BASES = new EnumMap<>(BaseType.class);

    static {
        BASES.put(BaseType.AURA, shape(req(word("affix", "affix")), whole("stacks", Unit.NONE, 1, 3, 1),
                num("radius", Unit.BLOCKS, 8, 32, 24), list("roles", 1, 32)));
        BASES.put(BaseType.MEND, shape(whole("percent", Unit.PERCENT, 1, 10, 5), num("every", Unit.SECONDS, 5, 30, 10),
                num("radius", Unit.BLOCKS, 8, 24, 16)));
        BASES.put(BaseType.LOOKOUT, shape(num("radius", Unit.BLOCKS, 16, 48, 32)));
        BASES.put(BaseType.RALLY, shape(whole("percent", Unit.PERCENT, 5, 25, 10)));
        BASES.put(BaseType.DRILL, shape(whole("xp", Unit.NONE, 1, 3, 1), num("radius", Unit.BLOCKS, 8, 24, 16)));
    }

    public static final Map<String, Field> GEAR_ITEM = shape(req(word("item", "gear")), text("name", 1, 24),
            of("enchantments", Kind.MAP));

    public static final int MAX_LINES_PER_EVENT = 8, LINE_MAX = 120;
    public static final int MAX_ENCHANTS_PER_ITEM = 3, MAX_KIT_ENCHANT_LEVELS = 6;
    public static final int RESIST_MIN = -30, RESIST_MAX = 60;

    // ------------------------------------------------------------------ word lists (vocabularies)

    /**
     * The words a field accepts, with synonyms read as a word (ADJUSTED) and a noun for messages ("an element").
     * Lookups use {@link #norm}: case, spaces, hyphens and a {@code minecraft:} prefix do not matter.
     */
    public static final class Vocab {
        public final String name;
        public final String noun;
        public final List<String> words;
        public final Map<String, String> synonyms = new LinkedHashMap<>();
        private final Set<String> set;

        Vocab(String name, String noun, List<String> words) {
            this.name = name;
            this.noun = noun;
            this.words = List.copyOf(words);
            this.set = new LinkedHashSet<>(words);
        }

        Vocab syn(String target, String... from) {
            if (!set.contains(target)) {
                throw new IllegalStateException(name + ": synonym target " + target);
            }
            for (String f : from) {
                if (set.contains(f)) {
                    throw new IllegalStateException(name + ": a synonym may not override the word " + f);
                }
                synonyms.put(f, target);
            }
            return this;
        }

        public boolean has(String w) {
            return set.contains(w);
        }

        /** The word as written, its synonym's word, or null when it is not in this list. */
        public String resolve(String normalised) {
            return set.contains(normalised) ? normalised : synonyms.get(normalised);
        }

        /** "physical, fire, frost" for messages. */
        public String allowed() {
            return String.join(", ", words);
        }
    }

    private static final Map<String, Vocab> VOCABS = new LinkedHashMap<>();

    private static Vocab vocab(String name, String noun, List<String> words) {
        Vocab v = new Vocab(name, noun, words);
        VOCABS.put(name, v);
        return v;
    }

    private static <E extends Enum<E> & HeroDef.Word> Vocab vocab(String name, String noun, Class<E> cls) {
        List<String> w = new ArrayList<>();
        for (E e : cls.getEnumConstants()) {
            w.add(e.id());
        }
        return vocab(name, noun, w);
    }

    public static Vocab vocab(String name) {
        Vocab v = VOCABS.get(name);
        if (v == null) {
            throw new IllegalArgumentException("no word list " + name);
        }
        return v;
    }

    public static Map<String, Vocab> vocabs() {
        return Collections.unmodifiableMap(VOCABS);
    }

    /** A word of v1 that told players and NPCs apart, the word it is read as now, and why (for the message). */
    public record EqualReading(String readAs, String why) {
    }

    /**
     * Equal treatment (the owner's rule of 2026-11-12, HEROES_V2 6 and 6.15: "player only features interactions to be
     * removed"): by word list, the v1 words that gave players a rule of their own and what each is read as (ADJUSTED),
     * so no loaded hero can prefer, avoid or single out players. They are not words of the lists any more.
     */
    public static final Map<String, Map<String, EqualReading>> EQUAL_READINGS = Map.of(
            "protects", Map.of("players", new EqualReading("allies", "\"allies\" is everyone of its side, players"
                    + " included")),
            "target_kind", Map.of(
                    "player", new EqualReading("person", "a person is a player, an NPC or a hero alike"),
                    "npc", new EqualReading("person", "a person is a player, an NPC or a hero alike")));

    /** Lower case, {@code minecraft:} dropped, spaces and hyphens as {@code _}, repeated {@code _} merged. */
    public static String norm(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("minecraft:")) {
            t = t.substring("minecraft:".length());
        }
        t = t.replace(' ', '_').replace('-', '_');
        while (t.contains("__")) {
            t = t.replace("__", "_");
        }
        return t;
    }

    /** The enum constant of a word this catalogue produced. */
    public static <E extends Enum<E>> E enumOf(Class<E> cls, String id) {
        return Enum.valueOf(cls, id.toUpperCase(Locale.ROOT));
    }

    public static final List<String> FACTIONS = List.of("ember_legion", "grove_covenant", "tide_alliance",
            "stoneguard");
    public static final List<String> WORKER_ROLES = List.of("builder", "miner", "farmer", "woodcutter", "blacksmith",
            "chef", "smelter", "animal_carer", "butcher", "fisherman", "cave_explorer", "hauler", "enchanter",
            "trader");
    /** Every NPC role id ({@code NpcRole}): workers, the scout and the four soldier roles. */
    public static final List<String> ROLE_IDS;
    public static final List<String> DUTIES;

    static {
        List<String> r = new ArrayList<>(WORKER_ROLES);
        r.addAll(List.of("scout", "swordsman", "archer", "crossbowman", "axe_fighter"));
        ROLE_IDS = List.copyOf(r);
        List<String> d = new ArrayList<>(List.of("guard", "scout"));
        d.addAll(WORKER_ROLES);
        DUTIES = List.copyOf(d);
    }

    // ------------------------------------------------------------------ allowlists (Appendix B)

    /** A potion effect's caps: highest level, longest seconds (B.1). */
    public record PotionCap(int maxLevel, double maxSeconds) {
    }

    public static final Map<String, PotionCap> HELPFUL_POTIONS = ordered(
            "speed", new PotionCap(2, 20), "haste", new PotionCap(2, 30), "strength", new PotionCap(2, 12),
            "regeneration", new PotionCap(2, 10), "resistance", new PotionCap(2, 10),
            "fire_resistance", new PotionCap(1, 30), "water_breathing", new PotionCap(1, 30),
            "jump_boost", new PotionCap(2, 20), "slow_falling", new PotionCap(1, 10),
            "invisibility", new PotionCap(1, 6));
    public static final Map<String, PotionCap> HARMFUL_POTIONS = ordered(
            "slowness", new PotionCap(3, 8), "weakness", new PotionCap(2, 8), "mining_fatigue", new PotionCap(2, 10),
            "poison", new PotionCap(2, 8), "wither", new PotionCap(2, 6), "glowing", new PotionCap(1, 15),
            "levitation", new PotionCap(1, 3), "blindness", new PotionCap(1, 5), "darkness", new PotionCap(1, 6));
    /** Potion effects only the hero itself may receive. */
    public static final Set<String> SELF_ONLY_POTIONS = Set.of("invisibility");
    /** Vanilla potion effects refused with a pointer to the effect type that does the job. */
    public static final Map<String, String> REFUSED_POTIONS = ordered(
            "absorption", "use the \"shield\" effect", "instant_health", "use the \"heal\" effect",
            "instant_damage", "use the \"damage\" effect", "health_boost", "", "saturation", "", "hunger", "",
            "nausea", "", "night_vision", "", "luck", "", "unluck", "", "conduit_power", "", "dolphins_grace", "",
            "bad_omen", "", "raid_omen", "", "trial_omen", "", "hero_of_the_village", "", "wind_charged", "",
            "weaving", "", "oozing", "", "infested", "");

    public static final List<String> PARTICLES = List.of("angry_villager", "ash", "bubble_pop", "cherry_leaves",
            "cloud", "crimson_spore", "crit", "damage_indicator", "dragon_breath", "dust", "electric_spark", "enchant",
            "enchanted_hit", "end_rod", "explosion", "firework", "flame", "glow", "gust", "happy_villager", "heart",
            "large_smoke", "nautilus", "note", "poof", "portal", "reverse_portal", "scrape", "sculk_soul",
            "small_flame", "small_gust", "smoke", "snowflake", "sonic_boom", "soul", "soul_fire_flame",
            "spore_blossom_air", "splash", "sweep_attack", "totem_of_undying", "trial_spawner_detection",
            "warped_spore", "wax_on", "white_ash", "witch");

    public static final List<String> SOUNDS = List.of("block.amethyst_block.chime", "block.amethyst_cluster.break",
            "block.anvil.land", "block.beacon.activate", "block.beacon.power_select", "block.bell.use",
            "block.conduit.activate", "block.enchantment_table.use", "block.fire.ambient", "block.glass.break",
            "block.note_block.bell", "block.respawn_anchor.charge", "block.respawn_anchor.deplete",
            "block.sculk_shrieker.shriek", "entity.arrow.shoot", "entity.blaze.ambient", "entity.blaze.shoot",
            "entity.breeze.shoot", "entity.breeze.wind_burst", "entity.dolphin.splash", "entity.elder_guardian.curse",
            "entity.ender_dragon.growl", "entity.evoker.cast_spell", "entity.evoker.prepare_attack",
            "entity.evoker.prepare_summon", "entity.experience_orb.pickup", "entity.generic.explode",
            "entity.ghast.shoot", "entity.glow_squid.squirt", "entity.goat.ram_impact", "entity.illusioner.cast_spell",
            "entity.illusioner.mirror_move", "entity.illusioner.prepare_blindness", "entity.iron_golem.attack",
            "entity.lightning_bolt.thunder", "entity.player.attack.crit", "entity.player.attack.knockback",
            "entity.player.attack.strong", "entity.player.attack.sweep", "entity.player.hurt_freeze",
            "entity.player.levelup", "entity.player.splash", "entity.ravager.roar", "entity.snowball.throw",
            "entity.vex.charge", "entity.warden.sonic_boom", "entity.wind_charge.wind_burst", "entity.wither.shoot",
            "entity.wolf.howl", "event.raid.horn", "item.book.page_turn", "item.crossbow.shoot", "item.firecharge.use",
            "item.flintandsteel.use", "item.mace.smash_ground", "item.shield.block", "item.totem.use",
            "item.trident.hit", "item.trident.riptide_1", "item.trident.throw", "item.trident.thunder");

    /** What kind of thing a gear item is: its equipment slot and the enchantments it accepts. */
    public enum GearKind {
        SWORD(GearSlot.MAINHAND), AXE(GearSlot.MAINHAND), MACE(GearSlot.MAINHAND), BOW(GearSlot.MAINHAND),
        CROSSBOW(GearSlot.MAINHAND), TRIDENT(GearSlot.MAINHAND), SHIELD(GearSlot.OFFHAND), HELMET(GearSlot.HEAD),
        CHESTPLATE(GearSlot.CHEST), LEGGINGS(GearSlot.LEGS), BOOTS(GearSlot.FEET);

        public final GearSlot slot;

        GearKind(GearSlot slot) {
            this.slot = slot;
        }

        public boolean armour() {
            return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
        }
    }

    /** B.4: iron is the best a hero starts with. */
    public static final Map<String, GearKind> GEAR;

    static {
        Map<String, GearKind> g = new LinkedHashMap<>();
        for (String m : List.of("wooden", "stone", "iron", "golden")) {
            g.put(m + "_sword", GearKind.SWORD);
        }
        for (String m : List.of("wooden", "stone", "iron", "golden")) {
            g.put(m + "_axe", GearKind.AXE);
        }
        g.put("mace", GearKind.MACE);
        g.put("bow", GearKind.BOW);
        g.put("crossbow", GearKind.CROSSBOW);
        g.put("trident", GearKind.TRIDENT);
        g.put("shield", GearKind.SHIELD);
        g.put("turtle_helmet", GearKind.HELMET);
        for (String m : List.of("leather", "chainmail", "iron", "golden")) {
            g.put(m + "_helmet", GearKind.HELMET);
            g.put(m + "_chestplate", GearKind.CHESTPLATE);
            g.put(m + "_leggings", GearKind.LEGGINGS);
            g.put(m + "_boots", GearKind.BOOTS);
        }
        GEAR = Collections.unmodifiableMap(g);
    }

    /** Diamond and netherite gear: refused, the hero can still take it from the storehouse. */
    public static boolean tooGood(String item) {
        return item.startsWith("diamond_") || item.startsWith("netherite_");
    }

    /** The basic item a style needs when the kit lacks it (v1 7); null for thrown. */
    public static String styleItem(Style s) {
        return switch (s) {
            case MELEE -> "stone_sword";
            case BOW -> "bow";
            case CROSSBOW -> "crossbow";
            case TRIDENT -> "trident";
            case THROWN -> null;
        };
    }

    /** Whether a main-hand item serves the style (a melee hero holds any sword, axe or mace). */
    public static boolean servesStyle(GearKind k, Style s) {
        return switch (s) {
            case MELEE -> k == GearKind.SWORD || k == GearKind.AXE || k == GearKind.MACE;
            case BOW -> k == GearKind.BOW;
            case CROSSBOW -> k == GearKind.CROSSBOW;
            case TRIDENT -> k == GearKind.TRIDENT;
            case THROWN -> true;
        };
    }

    /** An allowed enchantment: its highest level, the item kinds that accept it (1.21.1 tags) and its exclusive set. */
    public record Enchant(int maxLevel, Set<GearKind> accepts, String exclusive) {
    }

    private static final Set<GearKind> ARMOUR = Set.of(GearKind.HELMET, GearKind.CHESTPLATE, GearKind.LEGGINGS,
            GearKind.BOOTS);

    /**
     * B.5, with the item kinds from vanilla's {@code supported_items} and exclusive sets
     * ({@code data/minecraft/enchantment/*.json}, {@code tags/item/enchantable/*}, {@code tags/enchantment/exclusive_set/*}
     * of 1.21.1).
     */
    public static final Map<String, Enchant> ENCHANTS = ordered(
            "sharpness", new Enchant(2, Set.of(GearKind.SWORD, GearKind.AXE), "damage"),
            "smite", new Enchant(3, Set.of(GearKind.SWORD, GearKind.AXE, GearKind.MACE), "damage"),
            "bane_of_arthropods", new Enchant(3, Set.of(GearKind.SWORD, GearKind.AXE, GearKind.MACE), "damage"),
            "knockback", new Enchant(1, Set.of(GearKind.SWORD), null),
            "fire_aspect", new Enchant(1, Set.of(GearKind.SWORD, GearKind.MACE), null),
            "sweeping_edge", new Enchant(2, Set.of(GearKind.SWORD), null),
            "density", new Enchant(2, Set.of(GearKind.MACE), "damage"),
            "breach", new Enchant(1, Set.of(GearKind.MACE), "damage"),
            "power", new Enchant(2, Set.of(GearKind.BOW), null),
            "punch", new Enchant(1, Set.of(GearKind.BOW), null),
            "flame", new Enchant(1, Set.of(GearKind.BOW), null),
            "quick_charge", new Enchant(2, Set.of(GearKind.CROSSBOW), null),
            "piercing", new Enchant(2, Set.of(GearKind.CROSSBOW), "crossbow"),
            "multishot", new Enchant(1, Set.of(GearKind.CROSSBOW), "crossbow"),
            "impaling", new Enchant(2, Set.of(GearKind.TRIDENT), "damage"),
            "loyalty", new Enchant(1, Set.of(GearKind.TRIDENT), null),
            "protection", new Enchant(2, ARMOUR, "armor"),
            "fire_protection", new Enchant(2, ARMOUR, "armor"),
            "blast_protection", new Enchant(2, ARMOUR, "armor"),
            "projectile_protection", new Enchant(2, ARMOUR, "armor"),
            "thorns", new Enchant(1, ARMOUR, null),
            "feather_falling", new Enchant(2, Set.of(GearKind.BOOTS), null),
            "respiration", new Enchant(1, Set.of(GearKind.HELMET), null),
            "aqua_affinity", new Enchant(1, Set.of(GearKind.HELMET), null),
            "depth_strider", new Enchant(2, Set.of(GearKind.BOOTS), null),
            "unbreaking", new Enchant(3, Set.of(GearKind.values()), null));
    /** B.5's refused enchantments, each with its reason. */
    public static final Map<String, String> REFUSED_ENCHANTS = ordered(
            "mending", "kit items come back on revival and never need mending",
            "binding_curse", "curses are not for heroes", "vanishing_curse", "curses are not for heroes",
            "frost_walker", "it changes blocks", "channeling", "its lightning starts fires",
            "riptide", "a riptide trident cannot be thrown", "wind_burst", "it launches the hero",
            "looting", "it is not a combat enchantment for a hero", "fortune", "it is a mining enchantment",
            "silk_touch", "it is a mining enchantment", "efficiency", "it is a mining enchantment",
            "infinity", "ranged heroes already have their one arrow", "luck_of_the_sea", "it is a fishing enchantment",
            "lure", "it is a fishing enchantment", "soul_speed", "it is not available to heroes",
            "swift_sneak", "it is not available to heroes");

    /** B.6: projectiles that fly like arrows (gravity); every other look flies straight. */
    public static final List<String> ARROW_LOOKS = List.of("arrow", "spectral_arrow", "trident");
    public static final List<String> ITEM_LOOKS = List.of("snowball", "fire_charge", "wind_charge", "slime_ball",
            "magma_cream", "amethyst_shard", "prismarine_shard", "prismarine_crystals", "echo_shard", "blaze_powder",
            "feather", "bone", "flint", "heart_of_the_sea", "nautilus_shell");
    public static final List<String> HELD_PROPS = List.of("stick", "blaze_rod", "breeze_rod", "book",
            "amethyst_shard", "echo_shard", "prismarine_shard", "heart_of_the_sea", "nautilus_shell", "feather", "bone",
            "spyglass", "compass", "clock", "lantern", "soul_lantern", "poppy", "cornflower");

    /** B.8: most per cast and weight factor. */
    public record SummonSpec(int maxCount, double weight) {
    }

    public static final Map<Creature, SummonSpec> SUMMONS = new EnumMap<>(Map.of(
            Creature.WOLF, new SummonSpec(3, 1.0), Creature.SNOW_GOLEM, new SummonSpec(2, 0.5),
            Creature.IRON_GOLEM, new SummonSpec(1, 3.0)));

    /** Work affixes an aura may raise (v1 21); attribute and scouting affixes are refused there. */
    public static final List<String> AURA_AFFIXES = List.of("diligence", "prospector", "regrowth", "green_thumb",
            "mason", "marksman", "thrifty", "hardy", "duelist", "angler", "haggler");
    public static final List<String> REFUSED_AURA_AFFIXES = List.of("vitality", "swiftness", "might", "toughness",
            "pathfinder");

    static {
        vocab("rarity", "a rarity", HeroDef.Rarity.class);
        vocab("faction", "a faction", FACTIONS);
        vocab("skin_model", "a skin model", List.of("classic", "slim"));
        vocab("element", "an element", HeroDef.Element.class)
                .syn("physical", "neutral", "normal", "true").syn("fire", "flame", "heat")
                .syn("frost", "ice", "cold", "water").syn("shock", "lightning", "electric", "thunder", "storm")
                .syn("venom", "poison", "nature", "toxic").syn("blight", "shadow", "dark", "decay", "void")
                .syn("arcane", "magic", "mystic", "spirit").syn("radiant", "holy", "light", "sacred");
        vocab("combat_role", "a combat role", Role.class)
                .syn("vanguard", "tank", "guardian", "defender", "protector")
                .syn("striker", "fighter", "warrior", "dps", "brawler", "assassin", "berserker")
                .syn("skirmisher", "rogue", "duelist", "skirmish")
                .syn("marksman", "archer", "ranger", "sniper", "gunner", "caster", "mage")
                .syn("support", "healer", "buffer", "medic", "bard", "sustain");
        vocab("growth", "a growth profile", Growth.class);
        vocab("focus", "a focus", Focus.class);
        vocab("aggression", "an aggression", Aggression.class);
        vocab("protects", "a protects word", Protects.class);
        vocab("raids", "a raids word", Raids.class);
        vocab("duty", "a duty", DUTIES);
        vocab("style", "a basic attack style", Style.class);
        vocab("item_look", "a thrown look", ITEM_LOOKS);
        List<String> proj = new ArrayList<>(ARROW_LOOKS);
        proj.addAll(ITEM_LOOKS);
        vocab("projectile", "a projectile", proj);
        vocab("held_prop", "a held prop", HELD_PROPS);
        vocab("form", "a form", Form.class)
                .syn("melee", "strike", "slash", "hit").syn("shot", "projectile", "arrow", "fireball")
                .syn("volley", "multishot", "barrage", "fan").syn("bolt", "beam", "ray", "spell", "instant")
                .syn("chain", "bounce").syn("cone", "breath", "sweep", "arc").syn("line", "pierce", "lance")
                .syn("nova", "aoe", "burst", "shockwave", "around_self")
                .syn("zone", "ground", "circle", "area", "rain", "field").syn("leap", "jump", "slam")
                .syn("dash", "charge", "rush").syn("self", "buff_self").syn("ally", "heal_ally")
                .syn("allies", "team", "party", "group");
        vocab("zone_at", "a zone place", HeroDef.ZoneAt.class);
        vocab("use_when", "a use_when rule", UseWhen.class)
                .syn("self_health_below", "hp_below", "low_health")
                .syn("ally_health_below", "ally_low_health", "heal_needed")
                .syn("target_health_below", "execute", "finisher").syn("enemies_near", "crowd", "many_enemies")
                .syn("target_far", "gap_close").syn("target_close", "escape", "danger").syn("big_fight", "boss")
                .syn("out_of_combat", "idle", "peace");
        vocab("to", "a receiver", HeroDef.To.class);
        vocab("condition", "a condition", ConditionType.class);
        vocab("target_kind", "a kind", HeroDef.TargetKind.class);
        vocab("effect_type", "an effect type", EffectType.class)
                .syn("damage", "dmg", "attack", "hit").syn("dot", "damage_over_time", "bleed", "burn_damage")
                .syn("heal", "heal_percent", "restore").syn("shield", "barrier", "absorb", "absorption")
                .syn("effect", "potion", "buff", "debuff", "status").syn("stat", "modifier", "stat_buff")
                .syn("knockback", "push").syn("ignite", "burn", "fire").syn("freeze", "chill", "frost")
                .syn("stun", "daze", "paralyze").syn("taunt", "provoke").syn("cleanse", "purify")
                .syn("dispel", "strip", "purge").syn("energy", "charge").syn("cooldown_cut", "reset", "refresh")
                .syn("move_self", "hop", "jump_back", "evade").syn("mark", "stack", "brand")
                .syn("clear_marks", "consume_marks", "detonate").syn("empower", "transform", "enhance", "stance");
        List<String> potions = new ArrayList<>(HELPFUL_POTIONS.keySet());
        potions.addAll(HARMFUL_POTIONS.keySet());
        vocab("potion", "a potion effect", potions);
        vocab("stat", "a stat", Stat.class);
        vocab("direction", "a direction", HeroDef.Direction.class);
        vocab("creature", "a summon", Creature.class);
        vocab("trigger", "a trigger", Trigger.class)
                .syn("on_hit", "on_attack").syn("on_crit", "on_critical").syn("on_hurt", "on_damaged", "when_hit")
                .syn("on_skill", "on_cast").syn("every", "periodic", "interval").syn("on_fight_start", "on_combat_start")
                .syn("on_ally_attack", "follow_up").syn("on_mark_full", "on_stacks_full");
        vocab("passive_form", "a passive form", PassiveForm.class);
        vocab("particle", "a particle", PARTICLES);
        vocab("sound", "a sound", SOUNDS);
        vocab("pose", "a pose", HeroDef.Pose.class);
        vocab("base_type", "a base ability type", BaseType.class)
                .syn("aura", "work_aura", "buff_workers").syn("mend", "heal_between_fights", "medic", "regen")
                .syn("lookout", "watch", "sentry").syn("rally", "leadership", "recruit_discount")
                .syn("drill", "training", "drill_master");
        vocab("affix", "a work affix", AURA_AFFIXES);
        vocab("role", "an NPC role", ROLE_IDS);
        vocab("gear", "a gear item", new ArrayList<>(GEAR.keySet()));
        vocab("enchantment", "an enchantment", new ArrayList<>(ENCHANTS.keySet()));
        vocab("line_event", "a line event", LineEvent.class)
                .syn("summoned", "join", "recruited").syn("greeting", "hello", "talk").syn("fallen", "death", "dies")
                .syn("revived", "respawn", "return").syn("victory", "win").syn("level_up", "levelup")
                .syn("fight_start", "combat_start");
    }

    // ------------------------------------------------------------------ role presets and growth (v1 5.2, 5.3)

    public record RolePreset(double health, double attack, double armor, int speed, int knockbackResistance,
                             Growth growth, Focus focus, double keepDistance, Protects protects, Aggression aggression,
                             int retreatBelow, Raids raids) {
    }

    public static final Map<Role, RolePreset> ROLES = new EnumMap<>(Role.class);

    static {
        ROLES.put(Role.VANGUARD, new RolePreset(34, 1, 3, 95, 30, Growth.TOUGH, Focus.THREAT_TO_ALLIES, 0,
                Protects.ALLIES, Aggression.STEADY, 20, Raids.LEADS));
        ROLES.put(Role.STRIKER, new RolePreset(28, 2, 1, 100, 10, Growth.FIERCE, Focus.WEAKEST, 0, Protects.NONE,
                Aggression.BOLD, 25, Raids.LEADS));
        ROLES.put(Role.SKIRMISHER, new RolePreset(26, 2, 0, 108, 0, Growth.SWIFT, Focus.WEAKEST, 4, Protects.NONE,
                Aggression.BOLD, 30, Raids.JOINS));
        ROLES.put(Role.MARKSMAN, new RolePreset(24, 0, 1, 100, 0, Growth.BALANCED, Focus.WEAKEST, 10, Protects.NONE,
                Aggression.CAUTIOUS, 40, Raids.JOINS));
        ROLES.put(Role.SUPPORT, new RolePreset(26, 0, 1, 100, 0, Growth.MYSTIC, Focus.THREAT_TO_ALLIES, 8,
                Protects.ALLIES, Aggression.CAUTIOUS, 40, Raids.JOINS));
    }

    /** Per level above 1 (PROPOSED): health, attack, armour, speed %, ability power %. */
    public record GrowthStep(double health, double attack, double armor, double speedPercent,
                             double abilityPowerPercent) {
    }

    public static final Map<Growth, GrowthStep> GROWTH = new EnumMap<>(Growth.class);

    static {
        GROWTH.put(Growth.BALANCED, new GrowthStep(0.5, 0.06, 0.06, 0, 1));
        GROWTH.put(Growth.TOUGH, new GrowthStep(0.8, 0.03, 0.12, 0, 0.5));
        GROWTH.put(Growth.FIERCE, new GrowthStep(0.3, 0.12, 0.03, 0, 1));
        GROWTH.put(Growth.SWIFT, new GrowthStep(0.4, 0.06, 0.03, 0.3, 1));
        GROWTH.put(Growth.MYSTIC, new GrowthStep(0.4, 0.03, 0.03, 0, 2));
    }

    public static final int MAX_LEVEL = 30;

    /** Roles that fight in melee: a {@code thrown} style there gets a NOTE (v1 9). */
    public static boolean meleeRole(Role r) {
        return r == Role.VANGUARD || r == Role.STRIKER || r == Role.SKIRMISHER;
    }

    /** The reference attack power (v1 6.2): melee 6, bow 6, crossbow 7, trident 8, thrown its amount; + stats.attack. */
    public static double referenceAttack(Style style, double thrownAmount, double statsAttack) {
        double base = switch (style) {
            case MELEE, BOW -> 6;
            case CROSSBOW -> 7;
            case TRIDENT -> 8;
            case THROWN -> thrownAmount;
        };
        return base + statsAttack;
    }

    /** Full iron armour points the reference Amount adds to {@code stats.armor} (v1 12). */
    public static final double REFERENCE_WORN_ARMOR = 15;
    /** The receiver of a reference Amount: 20 max health, 10 missing (v1 12). */
    public static final double REFERENCE_TARGET_MAX = 20, REFERENCE_TARGET_MISSING = 10;
    /** A mark's stacks for the reference when the file never applies it. */
    public static final int REFERENCE_MARK_STACKS = 5;

    // ------------------------------------------------------------------ forms (v1 10.1)

    /**
     * A form's own ranges: range and radius (NaN default: the form does not use it), and which optional fields it
     * uses. {@code rangeFixed}: melee reaches exactly 3.5 blocks.
     */
    public record FormSpec(double rangeMin, double rangeMax, double rangeDefault, boolean rangeFixed,
                           double radiusMin, double radiusMax, double radiusDefault, boolean count, boolean angle,
                           boolean width, boolean hits, boolean projectile, boolean zone) {
        public boolean usesRange() {
            return !Double.isNaN(rangeDefault);
        }

        public boolean usesRadius() {
            return !Double.isNaN(radiusDefault);
        }
    }

    private static final double NA = Double.NaN;
    public static final Map<Form, FormSpec> FORMS = new EnumMap<>(Form.class);

    static {
        FORMS.put(Form.MELEE, new FormSpec(3.5, 3.5, 3.5, true, 0, 3, 0, false, false, false, true, false, false));
        FORMS.put(Form.SHOT, new FormSpec(4, 24, 16, false, 0, 4, 0, false, false, false, false, true, false));
        FORMS.put(Form.VOLLEY, new FormSpec(4, 24, 16, false, 0, 3, 0, true, false, false, false, true, false));
        FORMS.put(Form.BOLT, new FormSpec(4, 20, 12, false, 0, 3, 0, false, false, false, true, false, false));
        FORMS.put(Form.CHAIN, new FormSpec(4, 16, 10, false, 2, 6, 4, true, false, false, false, false, false));
        FORMS.put(Form.CONE, new FormSpec(2, 8, 5, false, NA, NA, NA, false, true, false, true, false, false));
        FORMS.put(Form.LINE, new FormSpec(4, 16, 8, false, NA, NA, NA, false, false, true, true, false, false));
        FORMS.put(Form.NOVA, new FormSpec(NA, NA, NA, false, 2, 8, 4, false, false, false, true, false, false));
        FORMS.put(Form.ZONE, new FormSpec(4, 20, 12, false, 2, 6, 3, false, false, false, false, false, true));
        FORMS.put(Form.LEAP, new FormSpec(3, 10, 8, false, 1, 4, 2.5, false, false, false, false, false, false));
        FORMS.put(Form.DASH, new FormSpec(3, 8, 6, false, NA, NA, NA, false, false, false, false, false, false));
        FORMS.put(Form.SELF, new FormSpec(NA, NA, NA, false, NA, NA, NA, false, false, false, false, false, false));
        FORMS.put(Form.ALLY, new FormSpec(4, 16, 10, false, NA, NA, NA, false, false, false, false, false, false));
        FORMS.put(Form.ALLIES, new FormSpec(NA, NA, NA, false, 4, 12, 8, false, false, false, false, false, false));
    }

    public static boolean formUsesHits(Form f) {
        return FORMS.get(f).hits();
    }

    /** Forms whose receivers can be several entities (multiplier A = 1.5 in the weight, v1 15). */
    public static boolean areaForm(Form f) {
        return f == Form.NOVA || f == Form.CONE || f == Form.LINE || f == Form.ZONE || f == Form.CHAIN
                || f == Form.LEAP || f == Form.ALLIES;
    }

    /** Wind-up default (v1 10): the ultimate 0.5, a leap 0.3, other skills 0. */
    public static double defaultWindup(Slot slot, Form f) {
        return slot == Slot.ULTIMATE ? 0.5 : f == Form.LEAP ? 0.3 : 0;
    }

    /** {@code use_when} default (v1 10.4). */
    public static UseWhen defaultUseWhen(Slot slot, Form f) {
        if (slot == Slot.ULTIMATE) {
            return UseWhen.BIG_FIGHT;
        }
        return f == Form.ALLY || f == Form.ALLIES ? UseWhen.ALLY_HEALTH_BELOW : UseWhen.ALWAYS;
    }

    /** The one number a {@code use_when} rule needs: its range and default, or null when it needs none. */
    public record ValueRange(double min, double max, double dflt) {
    }

    public static ValueRange useValue(UseWhen w) {
        return switch (w) {
            case SELF_HEALTH_BELOW -> new ValueRange(10, 90, 50);
            case ALLY_HEALTH_BELOW -> new ValueRange(10, 90, 60);
            case TARGET_HEALTH_BELOW -> new ValueRange(10, 90, 30);
            case ENEMIES_NEAR -> new ValueRange(2, 6, 2);
            case TARGET_FAR -> new ValueRange(4, 16, 6);
            case TARGET_CLOSE -> new ValueRange(2, 6, 3);
            default -> null;
        };
    }

    /** The visual fangs rise along these forms (v1 10.5). */
    public static boolean fangsForm(Form f) {
        return f == Form.LINE || f == Form.NOVA || f == Form.ZONE;
    }

    /** The pose a form shows when the look gives none: melee forms swing (v1 10.5). */
    public static HeroDef.Pose defaultPose(Form f) {
        return f == Form.MELEE || f == Form.LEAP || f == Form.DASH ? HeroDef.Pose.SWING : HeroDef.Pose.NONE;
    }

    // ------------------------------------------------------------------ conditions (v1 11)

    public static boolean conditionNeedsValue(ConditionType t) {
        return t == ConditionType.TARGET_HEALTH_BELOW || t == ConditionType.TARGET_HEALTH_ABOVE
                || t == ConditionType.SELF_HEALTH_BELOW || t == ConditionType.SELF_HEALTH_ABOVE;
    }

    public static boolean conditionNeedsMark(ConditionType t) {
        return t == ConditionType.TARGET_HAS_MARK || t == ConditionType.SELF_HAS_MARK;
    }

    // ------------------------------------------------------------------ recipient classes (v1 13)

    public enum Reach { ENEMY, ALLY, SELF, EITHER }

    /** Who an effect type may reach; {@code effect} and {@code stat} decide by their id or sign ({@link #reach}). */
    public static Reach reach(HeroDef.Effect e) {
        return switch (e.type()) {
            case DAMAGE, DOT, KNOCKBACK, PULL, IGNITE, FREEZE, STUN, TAUNT, DISPEL -> Reach.ENEMY;
            case HEAL, SHIELD, CLEANSE, ENERGY -> Reach.ALLY;
            case COOLDOWN_CUT, MOVE_SELF, EMPOWER, SUMMON -> Reach.SELF;
            case EFFECT -> ((HeroDef.PotionEffect) e).helpful() ? Reach.ALLY : Reach.ENEMY;
            case STAT -> ((HeroDef.StatMod) e).helpful() ? Reach.ALLY : Reach.ENEMY;
            case MARK, CLEAR_MARKS -> Reach.EITHER;
        };
    }

    // ------------------------------------------------------------------ passives (v1 17)

    public record TriggerSpec(List<PassiveForm> forms, ValueRange value) {
        public PassiveForm defaultForm() {
            return forms.get(0);
        }
    }

    public static final Map<Trigger, TriggerSpec> TRIGGERS = new EnumMap<>(Trigger.class);

    private static void trig(Trigger t, ValueRange v, PassiveForm... forms) {
        TRIGGERS.put(t, new TriggerSpec(List.of(forms), v));
    }

    static {
        PassiveForm s = PassiveForm.SELF, te = PassiveForm.TRIGGER_ENEMY, ta = PassiveForm.TRIGGER_ALLY,
                al = PassiveForm.ALLY, as = PassiveForm.ALLIES, n = PassiveForm.NOVA, b = PassiveForm.BOLT,
                sh = PassiveForm.SHOT;
        ValueRange low = new ValueRange(10, 60, 35);
        trig(Trigger.ALWAYS, null, s);
        trig(Trigger.ON_HIT, null, te, s, n, as, b, sh);
        trig(Trigger.ON_CRIT, null, te, s, n, as, b, sh);
        trig(Trigger.ON_HURT, null, te, s, n, as, b, sh);
        trig(Trigger.ON_KILL, null, s, n, as);
        trig(Trigger.ON_SKILL, null, s, al, as, n);
        trig(Trigger.ON_ULTIMATE, null, s, as, n);
        trig(Trigger.ON_FIGHT_START, null, s, as, n);
        trig(Trigger.ON_LOW_HEALTH, low, s, as, n);
        trig(Trigger.ON_ALLY_LOW_HEALTH, low, ta, as, s);
        trig(Trigger.ON_ALLY_HURT, null, ta, te, s, b, sh);
        trig(Trigger.ON_ALLY_ATTACK, null, te, b, sh);
        trig(Trigger.ON_ALLY_ULTIMATE, null, s, as, ta);
        trig(Trigger.ON_MARK_FULL, null, te, s, n);
        trig(Trigger.EVERY, new ValueRange(3, 30, 10), s, al, as, n);
    }

    /** A passive's area radius when the file gives none: nova 4, ally and allies 8. */
    public static double passiveRadius(PassiveForm f) {
        return f == PassiveForm.NOVA ? 4 : 8;
    }

    public static boolean passiveFormUsesRadius(PassiveForm f) {
        return f == PassiveForm.NOVA || f == PassiveForm.ALLY || f == PassiveForm.ALLIES;
    }

    /** Stat modifiers held by an {@code always} passive stay within this many percent either way (v1 16.2). */
    public static final int ALWAYS_STAT_CAP = 20;
    /** Empowered state stats (v1 18). */
    public static final int EMPOWERED_STAT_CAP = 30;
    /** {@code stats.size} x {@code empowered.size} never above this (2-high tunnels). */
    public static final int MAX_SIZE = 110;

    /** v1 13.3: each stat's own range. */
    public static final Map<Stat, ValueRange> STAT_RANGES = new EnumMap<>(Stat.class);

    static {
        STAT_RANGES.put(Stat.ATTACK, new ValueRange(-30, 40, 0));
        STAT_RANGES.put(Stat.DAMAGE_DEALT, new ValueRange(-30, 40, 0));
        STAT_RANGES.put(Stat.DAMAGE_TAKEN, new ValueRange(-40, 30, 0));
        STAT_RANGES.put(Stat.ARMOR, new ValueRange(-50, 50, 0));
        STAT_RANGES.put(Stat.SPEED, new ValueRange(-40, 40, 0));
        STAT_RANGES.put(Stat.ATTACK_SPEED, new ValueRange(-30, 30, 0));
        STAT_RANGES.put(Stat.HEALING_RECEIVED, new ValueRange(-50, 50, 0));
        STAT_RANGES.put(Stat.KNOCKBACK_RESISTANCE, new ValueRange(0, 60, 0));
        STAT_RANGES.put(Stat.CRIT_DAMAGE, new ValueRange(0, 50, 0));
        STAT_RANGES.put(Stat.ENERGY_GAIN, new ValueRange(0, 50, 0));
    }

    // ------------------------------------------------------------------ dialogue (v1 23)

    public static final Map<LineEvent, List<String>> PLACEHOLDERS = new EnumMap<>(LineEvent.class);

    static {
        List<String> nf = List.of("name", "faction"), npf = List.of("name", "player", "faction"),
                ne = List.of("name", "enemy"), nea = List.of("name", "enemy", "ability"), n = List.of("name");
        PLACEHOLDERS.put(LineEvent.SUMMONED, nf);
        PLACEHOLDERS.put(LineEvent.GREETING, npf);
        PLACEHOLDERS.put(LineEvent.IDLE, npf);
        PLACEHOLDERS.put(LineEvent.FIGHT_START, ne);
        PLACEHOLDERS.put(LineEvent.SKILL, nea);
        PLACEHOLDERS.put(LineEvent.ULTIMATE, nea);
        PLACEHOLDERS.put(LineEvent.LOW_HEALTH, n);
        PLACEHOLDERS.put(LineEvent.KILL, ne);
        PLACEHOLDERS.put(LineEvent.ALLY_FALLEN, List.of("name", "ally"));
        PLACEHOLDERS.put(LineEvent.VICTORY, n);
        PLACEHOLDERS.put(LineEvent.RAID_START, nf);
        PLACEHOLDERS.put(LineEvent.BASE_ATTACKED, nf);
        PLACEHOLDERS.put(LineEvent.LEVEL_UP, List.of("name", "level"));
        PLACEHOLDERS.put(LineEvent.UNLOCK, List.of("name", "level", "ability"));
        PLACEHOLDERS.put(LineEvent.FALLEN, n);
        PLACEHOLDERS.put(LineEvent.REVIVED, n);
    }

    /** Every placeholder some event knows; one outside this set leaves its line out. */
    public static final Set<String> ALL_PLACEHOLDERS = Set.of("name", "faction", "player", "enemy", "ability", "ally",
            "level");

    /** Events a hero says whatever its dialogue cooldown (v1 23); {@code greeting} has its own short one. */
    public static final Set<LineEvent> UNLIMITED_EVENTS = Set.of(LineEvent.SUMMONED, LineEvent.FALLEN,
            LineEvent.REVIVED, LineEvent.ULTIMATE);
    public static final double GREETING_COOLDOWN_SECONDS = 5;
    public static final double IDLE_EVERY_SECONDS = 15 * 60;

    /** The chance a line is said when its event happens (v1 23; 100 when not listed). */
    public static int lineChance(LineEvent e) {
        return switch (e) {
            case FIGHT_START -> 50;
            case SKILL -> 25;
            case KILL -> 30;
            default -> 100;
        };
    }

    // ------------------------------------------------------------------ caps and weights (v1 14-16)

    /** Level-1 reference health points per use and per receiver (v1 16.1); stun in seconds (0: not allowed). */
    public record SlotCaps(double damage, double dot, double heal, double shield, double stun) {
    }

    public static final Map<Slot, SlotCaps> CAPS = new EnumMap<>(Slot.class);

    static {
        CAPS.put(Slot.ON_HIT, new SlotCaps(4, 4, 2, 2, 0));
        CAPS.put(Slot.NTH_HIT, new SlotCaps(8, 8, 4, 4, 1));
        CAPS.put(Slot.SKILL, new SlotCaps(16, 16, 8, 8, 2));
        CAPS.put(Slot.ULTIMATE, new SlotCaps(30, 24, 14, 12, 3));
        CAPS.put(Slot.PASSIVE, new SlotCaps(8, 8, 4, 4, 1));
    }

    /** A lasting zone may deal or heal this many times the slot cap over all its pulses (v1 16.1). */
    public static final double ZONE_CAP_FACTOR = 1.5;
    /** v1 15: area receivers, the volley factor per projectile, a conditional effect, leap and dash. */
    public static final double AREA_WEIGHT = 1.5, VOLLEY_WEIGHT_PER_SHOT = 0.75, CONDITION_WEIGHT = 0.75,
            MOVE_FORM_WEIGHT = 2;
    /** v1 14: least seconds between two ultimates is the weight, clamped to these. */
    public static final double ULTIMATE_GAP_MIN = 20, ULTIMATE_GAP_MAX = 60;
    /** v1 9: a {@code thrown} basic attack deals at most this many health points per second. */
    public static final double THROWN_MAX_DPS = 5;
    /** v1 17.4: the {@code on_ally_attack} passive floor. */
    public static final double ALLY_ATTACK_FLOOR = 2;

    /** v1 6.2: an Amount uses at most this many times the reference attack power and armour at hit time. */
    public static final double GEAR_CAP = 1.5;

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> ordered(Object... kv) {
        Map<String, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (V) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    /** The shape (field table) of an object kind, by the names the reader and the schema generator use. */
    public static Map<String, Field> shapeOf(String kind) {
        return switch (kind) {
            case "top" -> TOP;
            case "stats" -> STATS;
            case "behaviour" -> BEHAVIOUR;
            case "basic_attack" -> BASIC_ATTACK;
            case "every_nth_hit" -> NTH_HIT;
            case "ability" -> ABILITY;
            case "amount" -> AMOUNT;
            case "condition" -> CONDITION;
            case "look" -> LOOK;
            case "passive" -> PASSIVE;
            case "empowered" -> EMPOWERED;
            case "stat_bonus" -> STAT_BONUS;
            case "gear_item" -> GEAR_ITEM;
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
