package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.Field;
import com.factionclash.logic.hero.HeroDef.EffectType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON Schema (draft 2020-12) of hero format 2 (HEROES_V2 12), generated from {@link HeroCatalogue}'s field
 * tables and word lists, so the schema and the validator cannot disagree ({@code HeroSchemaGenTest} holds it to the
 * committed {@code docs/heroes-v2/hero2.schema.json}; {@code ./gradlew heroSchema} rewrites that file).
 *
 * <p>The schema describes a <em>clean</em> file: one the reader loads with nothing to report. It is stricter than
 * the reader, which also accepts synonyms, misspelled fields, text numbers, values out of range and format 1's
 * fields (each ADJUSTED or a NOTE): words are their canonical spelling, numbers are within their ranges, and the
 * fields format 2 reads only to ignore or convert ({@code unlock_level}, {@code skills}, {@code empowered},
 * {@code base_abilities}, {@code stats.size}) are marked {@code deprecated}. Keys starting with {@code _} are
 * comments anywhere. Rules across fields (a form's own fields, class minimums, caps, followup strings, references
 * between moves, states and phases) are the reader's (14.1), not the schema's.
 */
public final class HeroSchemaGen {
    private HeroSchemaGen() {
    }

    public static final String ID = "urn:factionclash:hero:2";

    /** Word lists that name game items or registry ids: written as patterns that allow a {@code minecraft:} prefix. */
    private static final List<String> REGISTRY_VOCABS = List.of("gear", "projectile", "item_look", "held_prop",
            "potion", "particle", "enchantment");

    /** The format-2 fields format 2 reads only to convert or ignore them (each with what happens). */
    private static final Map<String, String> DEPRECATED = Map.of(
            "unlock_level", "Read and ignored: every ability works from level 1 (HEROES_V2 8).",
            "skills", "Format 1's skills; read as moves (each id from its name).",
            "empowered", "Format 1's empowered; read as a state.",
            "base_abilities", "Format 1's base abilities; read as talents.",
            "size", "Replaced by the art's scale; read and ignored.");

    private static final Map<String, Object> defs = new LinkedHashMap<>();

    /** The schema as pretty-printed JSON (two-space indent, a newline at the end). */
    public static synchronized String schema2() {
        defs.clear();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("$id", ID);
        root.put("title", "Faction Clash hero file, format 2");
        root.put("description", "A hero definition of format 2 (HEROES_V2.md section 12), as the reader loads it with"
                + " nothing to report. Generated from HeroCatalogue by HeroSchemaGen; do not edit by hand.");
        Map<String, Object> top = object(HeroCatalogue.TOP_2, "top");
        root.putAll(top);
        buildDefs();
        root.put("$defs", new LinkedHashMap<>(defs));
        StringBuilder sb = new StringBuilder();
        write(root, sb, 0);
        return sb.append('\n').toString();
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/heroes-v2/hero2.schema.json");
        Files.writeString(out, schema2(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out);
    }

    // ------------------------------------------------------------------ the definitions

    private static void buildDefs() {
        def("hero_id", pattern("^[a-z][a-z0-9_]{" + (HeroCatalogue.ID_MIN - 1) + "," + (HeroCatalogue.ID_MAX - 1)
                + "}$"));
        def("mark_id", pattern("^[a-z][a-z0-9_]{" + (HeroCatalogue.MARK_ID_MIN - 1) + ","
                + (HeroCatalogue.MARK_ID_MAX - 1) + "}$"));
        def("color", pattern("^#[0-9A-Fa-f]{6}$"));
        def("sound", pattern("^(?:" + HeroCatalogue.SOUND_ID_REGEX + ")$"));
        def("item_id", pattern("^(minecraft:)?[a-z0-9_]+$"));
        def("bone", pattern("^[a-z][a-z0-9_]{0,47}$"));
        def("line", text(1, HeroCatalogue.LINE_MAX));
        def("amount", amount());
        def("condition", oneOf(enumOf("condition2"), object(HeroCatalogue.CONDITION_2, "condition")));
        def("effect", effect());
        def("effects", array(ref("effect"), 1, 5));
        def("look", object(HeroCatalogue.LOOK, "look"));
        def("stat_bonus", object(HeroCatalogue.STAT_BONUS, "stat_bonus"));
        def("stats", object(HeroCatalogue.STATS, "stats"));
        def("behaviour", object(HeroCatalogue.BEHAVIOUR_2, "behaviour"));
        def("basic_attack", object(HeroCatalogue.BASIC_ATTACK_2, "basic_attack"));
        def("every_nth_hit", object(HeroCatalogue.NTH_HIT, "every_nth_hit"));
        def("gear_item", oneOf(ref("gear_id"), object(HeroCatalogue.GEAR_ITEM, "gear_item")));
        def("gear_id", registry("gear"));
        def("frames", object(HeroCatalogue.FRAMES, "frames"));
        def("followup", oneOf(ref("mark_id"), object(HeroCatalogue.FOLLOWUP, "followup")));
        def("channel", oneOf(Map.of("type", "boolean"), integer(5, 30),
                object(HeroCatalogue.CHANNEL, "channel")));
        def("telegraph", oneOf(enumOf("decal"), object(HeroCatalogue.TELEGRAPH, "telegraph")));
        def("strike", object(HeroCatalogue.STRIKE, "strike"));
        def("move", object(HeroCatalogue.MOVE, "move"));
        Map<String, Field> ult = new LinkedHashMap<>(HeroCatalogue.MOVE);
        def("ultimate", object(ult, "ultimate"));
        def("passive", object(HeroCatalogue.PASSIVE_2, "passive"));
        def("state", object(HeroCatalogue.STATE, "state"));
        def("empowered", object(HeroCatalogue.EMPOWERED, "empowered"));
        def("boss", object(HeroCatalogue.BOSS, "boss"));
        def("phase", object(HeroCatalogue.PHASE, "phase"));
        def("transition", object(HeroCatalogue.TRANSITION, "transition"));
        def("shockwave", object(HeroCatalogue.SHOCKWAVE, "shockwave"));
        def("reaction", object(HeroCatalogue.REACTION, "reaction"));
        def("weak_point", oneOf(enumOf("weak_point"), object(HeroCatalogue.WEAK_SPOT, "weak_point")));
        def("enrage", object(HeroCatalogue.ENRAGE, "enrage"));
        def("add", oneOf(enumOf("add"), object(HeroCatalogue.ADD, "add")));
        def("intro", oneOf(ref("line"), object(HeroCatalogue.INTRO, "intro")));
        def("arena", oneOf(enumOf("arena"), object(HeroCatalogue.ARENA, "arena")));
        def("personality", object(HeroCatalogue.PERSONALITY, "personality"));
        def("leader", object(HeroCatalogue.LEADER, "leader"));
        def("colors", object(HeroCatalogue.COLORS, "colors"));
        def("banner", object(HeroCatalogue.BANNER, "banner"));
        def("banner_layer", object(HeroCatalogue.BANNER_LAYER, "banner_layer"));
        def("talent", oneOf(enumOf("talent"), object(HeroCatalogue.TALENT, "talent")));
        def("base_ability", Map.of("type", "object", "deprecated", true, "description", DEPRECATED.get(
                "base_abilities")));
        def("voice", object(HeroCatalogue.VOICE, "voice"));
        def("recruit", object(HeroCatalogue.RECRUIT, "recruit"));
        def("relationship", object(HeroCatalogue.RELATIONSHIP, "relationship"));
        def("revival", object(HeroCatalogue.REVIVAL, "revival"));
        def("mobility", object(HeroCatalogue.MOBILITY, "mobility"));
        def("trait_tier", object(HeroCatalogue.TRAIT_TIER, "trait_tier"));
        def("carrier", object(HeroCatalogue.CARRIER, "carrier"));
        def("aerial", object(HeroCatalogue.AERIAL, "aerial"));
        def("generated", object(HeroCatalogue.GENERATED, "generated"));
        def("generated_move", object(HeroCatalogue.GENERATED_MOVE, "generated_move"));
        def("lines", lines());
    }

    private static void def(String name, Object schema) {
        defs.put(name, schema);
    }

    // ------------------------------------------------------------------ objects and their fields

    /** An object of a field table; {@code where} names it for the links below. */
    private static Map<String, Object> object(Map<String, Field> shape, String where) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        List<String> req = new ArrayList<>();
        for (Field f : shape.values()) {
            if (f.required()) {
                req.add(f.name());
            }
        }
        if (!req.isEmpty()) {
            o.put("required", req);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        for (Field f : shape.values()) {
            props.put(f.name(), field(f, where));
        }
        o.put("properties", props);
        o.put("patternProperties", Map.of("^_", Map.of()));
        o.put("additionalProperties", false);
        return o;
    }

    /** One field's schema: its link when it names another part, else from its kind. */
    private static Object field(Field f, String where) {
        Object linked = link(where, f);
        Object s = linked != null ? linked : plain(f);
        String dep = DEPRECATED.get(f.name());
        boolean deprecated = dep != null && (!f.name().equals("size") || where.equals("stats"));
        if (deprecated) {
            Map<String, Object> m = new LinkedHashMap<>(s instanceof Map<?, ?> mm ? cast(mm) : Map.of());
            m.put("deprecated", true);
            m.put("description", dep);
            return m;
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /** The fields that hold another part of the hero (the tables do not say which, so this is the map of 12). */
    private static Object link(String where, Field f) {
        String n = f.name();
        return switch (where + "." + n) {
            case "top.format" -> Map.of("const", 2);
            case "top.stats" -> ref("stats");
            case "top.resistances" -> resistances();
            case "top.gear" -> array(ref("gear_item"), (int) f.min(), (int) f.max());
            case "top.behaviour" -> ref("behaviour");
            case "top.basic_attack" -> ref("basic_attack");
            case "top.moves", "top.skills" -> array(ref("move"), (int) f.min(), (int) f.max());
            case "top.ultimate" -> ref("ultimate");
            case "top.passives" -> array(ref("passive"), (int) f.min(), (int) f.max());
            case "top.states" -> array(ref("state"), (int) f.min(), (int) f.max());
            case "top.empowered" -> ref("empowered");
            case "top.boss" -> ref("boss");
            case "top.personality" -> ref("personality");
            case "top.leader" -> ref("leader");
            case "top.talents" -> array(ref("talent"), (int) f.min(), (int) f.max());
            case "top.base_abilities" -> array(ref("base_ability"), (int) f.min(), (int) f.max());
            case "top.voice" -> ref("voice");
            case "top.lines" -> ref("lines");
            case "top.recruit" -> ref("recruit");
            case "top.relationships" -> array(ref("relationship"), (int) f.min(), (int) f.max());
            case "top.revival" -> ref("revival");
            case "top.mobility" -> ref("mobility");
            case "top.art", "top.animations" -> Map.of("type", "object", "description", "Read by the art generator"
                    + " (herogen) only; the server ignores it.");
            case "top.generated" -> ref("generated");
            case "basic_attack.on_hit", "state.on_hit", "empowered.on_hit" -> array(ref("effect"), (int) f.min(),
                    (int) f.max());
            case "basic_attack.every_nth_hit" -> ref("every_nth_hit");
            case "every_nth_hit.effects", "strike.effects", "passive.effects", "transition.effects" ->
                    array(ref("effect"), (int) f.min(), (int) f.max());
            case "move.effects", "ultimate.effects" -> array(ref("effect"), (int) f.min(), (int) f.max());
            case "move.look", "ultimate.look", "passive.look" -> ref("look");
            case "move.frames", "ultimate.frames", "generated_move.frames" -> ref("frames");
            case "move.band", "ultimate.band" -> oneOf(bandWord(), array(bandWord(), (int) f.min(), (int) f.max()));
            case "move.phases", "ultimate.phases", "aerial.phases" -> array(integer(1, HeroCatalogue.MAX_PHASES),
                    (int) f.min(), (int) f.max());
            case "move.prefer_when", "ultimate.prefer_when", "passive.if", "reaction.when" -> ref("condition");
            case "move.followups", "ultimate.followups" -> array(ref("followup"), (int) f.min(), (int) f.max());
            case "move.channel", "ultimate.channel" -> ref("channel");
            case "move.telegraph", "ultimate.telegraph" -> ref("telegraph");
            case "move.per_shot", "ultimate.per_shot" -> array(array(ref("effect"), 0, 3), (int) f.min(),
                    (int) f.max());
            case "move.strikes", "ultimate.strikes" -> array(ref("strike"), (int) f.min(), (int) f.max());
            case "move.awakened" -> Map.of("not", Map.of(), "description", "Only on the ultimate.");
            case "ultimate.awakened" -> ref("ultimate");
            case "state.stats", "empowered.stats", "phase.stats" -> array(ref("stat_bonus"), (int) f.min(),
                    (int) f.max());
            case "state.moves", "phase.moves", "phase.states" -> array(ref("mark_id"), (int) f.min(), (int) f.max());
            case "state.show", "state.hide" -> array(ref("bone"), (int) f.min(), (int) f.max());
            case "boss.phases" -> array(ref("phase"), (int) f.min(), (int) f.max());
            case "boss.reactions" -> array(ref("reaction"), (int) f.min(), (int) f.max());
            case "boss.weak_points" -> array(ref("weak_point"), (int) f.min(), (int) f.max());
            case "boss.enrage" -> ref("enrage");
            case "boss.adds", "phase.adds" -> array(ref("add"), (int) f.min(), (int) f.max());
            case "boss.intro" -> ref("intro");
            case "boss.arena" -> ref("arena");
            case "phase.transition" -> ref("transition");
            case "transition.shockwave" -> ref("shockwave");
            case "enrage.triggers" -> array(enumOf("enrage_trigger"), (int) f.min(), (int) f.max());
            case "personality.traits" -> traits();
            case "personality.stands_for" -> array(enumOf("stands_for"), (int) f.min(), (int) f.max());
            case "personality.likes", "personality.dislikes" -> array(ref("item_id"), (int) f.min(), (int) f.max());
            case "personality.quirks" -> array(ref("line"), (int) f.min(), (int) f.max());
            case "leader.faction_names" -> array(text(1, 32), (int) f.min(), (int) f.max());
            case "leader.colors" -> ref("colors");
            case "leader.banner" -> ref("banner");
            case "leader.economy_focus" -> array(enumOf("economy_focus"), (int) f.min(), (int) f.max());
            case "banner.patterns" -> array(ref("banner_layer"), (int) f.min(), (int) f.max());
            case "talent.roles" -> array(enumOf("role"), (int) f.min(), (int) f.max());
            case "mobility.primary", "mobility.secondary" -> ref("trait_tier");
            case "mobility.carrier" -> ref("carrier");
            case "mobility.aerial" -> ref("aerial");
            case "mobility.avoid" -> array(enumOf("avoid"), (int) f.min(), (int) f.max());
            case "generated.moves" -> array(ref("generated_move"), (int) f.min(), (int) f.max());
            case "gear_item.enchantments" -> enchantments();
            case "look.sound" -> ref("sound");
            default -> null;
        };
    }

    /** A field from its kind alone. */
    private static Object plain(Field f) {
        return switch (f.kind()) {
            case INT -> integer(f.min(), f.max());
            case NUM -> number(f.min(), f.max());
            case BOOL -> Map.of("type", "boolean");
            case TEXT -> text((int) f.min(), (int) f.max());
            case WORD -> word(f.vocab());
            case COLOR -> ref("color");
            case ID -> ref("hero_id");
            case MARK -> ref("mark_id");
            case LIST -> array(Map.of(), (int) f.min(), (int) f.max());
            case OBJECT, MAP -> Map.of("type", "object");
            case AMOUNT -> ref("amount");
            case CONDITION -> ref("condition");
        };
    }

    /** A band, or "any" (every band). */
    private static Map<String, Object> bandWord() {
        List<String> w = new ArrayList<>(HeroCatalogue.vocab("band").words);
        w.add("any");
        return Map.of("enum", w);
    }

    private static Object word(String vocab) {
        String name = HeroCatalogue.vocabName(vocab, 2);
        return REGISTRY_VOCABS.contains(name) ? registry(name) : enumOf(name);
    }

    // ------------------------------------------------------------------ effects, amounts, maps

    /**
     * An effect: its {@code type} and the fields every effect has, then, for each type, its own fields (as v1's
     * schema: the other types' fields are refused by {@code propertyNames}).
     */
    private static Map<String, Object> effect() {
        Map<String, Field> all = new LinkedHashMap<>(HeroCatalogue.EFFECT_COMMON);
        Map<EffectType, Map<String, Field>> own = new LinkedHashMap<>();
        for (EffectType t : EffectType.values()) {
            Map<String, Field> m = new LinkedHashMap<>(HeroCatalogue.EFFECTS.get(t));
            m.putAll(HeroCatalogue.EFFECTS_2.getOrDefault(t, Map.of()));
            own.put(t, m);
            for (Field f : m.values()) {
                all.putIfAbsent(f.name(), f);
            }
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("required", List.of("type"));
        Map<String, Object> props = new LinkedHashMap<>();
        for (Field f : HeroCatalogue.EFFECT_COMMON.values()) {
            props.put(f.name(), field(f, "effect"));
        }
        for (String k : all.keySet()) {
            props.putIfAbsent(k, Map.of());
        }
        o.put("properties", props);
        o.put("patternProperties", Map.of("^_", Map.of()));
        o.put("additionalProperties", false);
        List<Object> cases = new ArrayList<>();
        for (Map.Entry<EffectType, Map<String, Field>> e : own.entrySet()) {
            Map<String, Object> when = new LinkedHashMap<>();
            when.put("required", List.of("type"));
            when.put("properties", Map.of("type", Map.of("const", e.getKey().id())));
            Map<String, Object> then = new LinkedHashMap<>();
            List<String> req = new ArrayList<>();
            Map<String, Object> tp = new LinkedHashMap<>();
            for (Field f : e.getValue().values()) {
                tp.put(f.name(), field(f, "effect"));
                if (f.required()) {
                    req.add(f.name());
                }
            }
            if (!req.isEmpty()) {
                then.put("required", req);
            }
            if (!tp.isEmpty()) {
                then.put("properties", tp);
            }
            List<String> names = new ArrayList<>(HeroCatalogue.EFFECT_COMMON.keySet());
            names.addAll(e.getValue().keySet());
            then.put("propertyNames", Map.of("anyOf", List.of(Map.of("enum", names), Map.of("pattern", "^_"))));
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("if", when);
            c.put("then", then);
            cases.add(c);
        }
        o.put("allOf", cases);
        return o;
    }

    private static Object amount() {
        Map<String, Object> obj = object(HeroCatalogue.AMOUNT, "amount");
        obj.put("minProperties", 1);
        obj.put("dependentRequired", Map.of("per_mark_stack", List.of("mark"), "mark", List.of("per_mark_stack")));
        return oneOf(number(0, 30), obj);
    }

    private static Object resistances() {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String e : HeroCatalogue.vocab("element").words) {
            props.put(e, integer(HeroCatalogue.RESIST_MIN, HeroCatalogue.RESIST_MAX));
        }
        return closed(props);
    }

    private static Object traits() {
        Map<String, Object> props = new LinkedHashMap<>();
        for (HeroDef.Trait t : HeroDef.Trait.values()) {
            props.put(t.id(), integer(0, 100));
        }
        return closed(props);
    }

    private static Object enchantments() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("propertyNames", registry("enchantment"));
        o.put("additionalProperties", integer(1, 5));
        o.put("maxProperties", HeroCatalogue.MAX_ENCHANTS_PER_ITEM);
        return o;
    }

    /** {@code lines}: each format-2 event, a list of up to 12 lines ({@code unlock} never fires: deprecated). */
    private static Object lines() {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String e : HeroCatalogue.vocab("line_event2").words) {
            Map<String, Object> l = new LinkedHashMap<>(array(ref("line"), 1, HeroCatalogue.MAX_LINES_PER_EVENT_2));
            if (e.equals("unlock")) {
                l.put("deprecated", true);
                l.put("description", "Never fires for a format-2 hero (every ability works from level 1).");
            }
            props.put(e, l);
        }
        return closed(props);
    }

    private static Map<String, Object> closed(Map<String, Object> props) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("properties", props);
        o.put("patternProperties", Map.of("^_", Map.of()));
        o.put("additionalProperties", false);
        return o;
    }

    // ------------------------------------------------------------------ small builders

    private static Map<String, Object> ref(String def) {
        return Map.of("$ref", "#/$defs/" + def);
    }

    private static Map<String, Object> oneOf(Object... options) {
        return Map.of("oneOf", List.of(options));
    }

    private static Map<String, Object> enumOf(String vocab) {
        return Map.of("enum", HeroCatalogue.vocab(vocab).words);
    }

    private static Map<String, Object> registry(String vocab) {
        StringBuilder sb = new StringBuilder("^(minecraft:)?(");
        List<String> w = HeroCatalogue.vocab(vocab).words;
        for (int i = 0; i < w.size(); i++) {
            sb.append(i == 0 ? "" : "|").append(w.get(i).replace(".", "\\."));
        }
        return pattern(sb.append(")$").toString());
    }

    private static Map<String, Object> pattern(String p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("pattern", p);
        return m;
    }

    private static Map<String, Object> text(int min, int max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("minLength", min);
        m.put("maxLength", max);
        return m;
    }

    private static Map<String, Object> integer(double min, double max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("minimum", min);
        m.put("maximum", max);
        return m;
    }

    private static Map<String, Object> number(double min, double max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "number");
        m.put("minimum", min);
        m.put("maximum", max);
        return m;
    }

    private static Map<String, Object> array(Object items, int min, int max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        if (min > 0) {
            m.put("minItems", min);
        }
        m.put("maxItems", max);
        m.put("items", items);
        return m;
    }

    // ------------------------------------------------------------------ writing

    private static void write(Object v, StringBuilder sb, int indent) {
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : orderedEntries(m)) {
                pad(sb, indent + 2);
                str(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, indent + 2);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            boolean flat = l.stream().allMatch(x -> x instanceof String || x instanceof Number);
            if (flat) {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    write(l.get(i), sb, indent);
                }
                sb.append(']');
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                pad(sb, indent + 2);
                write(l.get(i), sb, indent + 2);
                sb.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            pad(sb, indent);
            sb.append(']');
        } else if (v instanceof String s) {
            str(s, sb);
        } else if (v instanceof Double d) {
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : String.valueOf(d));
        } else {
            sb.append(v);
        }
    }

    /** Map.of has no order: such maps are written sorted by key; the generator's own maps keep their order. */
    private static List<? extends Map.Entry<?, ?>> orderedEntries(Map<?, ?> m) {
        List<Map.Entry<?, ?>> l = new ArrayList<>(m.entrySet());
        if (!(m instanceof LinkedHashMap<?, ?>)) {
            l.sort((a, b) -> String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())));
        }
        return l;
    }

    private static void pad(StringBuilder sb, int n) {
        sb.append(" ".repeat(n));
    }

    private static void str(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
