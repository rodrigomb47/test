package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroBalance.Ref;
import com.factionclash.logic.hero.HeroBalance.Totals;
import com.factionclash.logic.hero.HeroCatalogue.PotionCap;
import com.factionclash.logic.hero.HeroCatalogue.Reach;
import com.factionclash.logic.hero.HeroCatalogue.SlotCaps;
import com.factionclash.logic.hero.HeroCatalogue.ValueRange;
import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Effect;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.GearItem;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.To;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A hero file can never hurt the server (v1 26.5) and the game never sees an out-of-range number (v1 0, decision 5):
 * thousands of broken copies of the example heroes and of a hero that uses every effect, form and trigger are read.
 * The reader must never throw; a hero either loads or is reported NOT LOADED; every message is well formed; and every
 * hero that loads obeys every range, cap, floor and recipient rule of the format, whatever the file said. The damage
 * is seeded, so a failure names the case that made it.
 */
class HeroReaderRobustnessTest {
    private static final double EPS = 1e-6;

    // ------------------------------------------------------------------ a hero with everything in it

    /** Every effect type, every form, every trigger and every base ability at least once. */
    static String everything() {
        StringBuilder sb = new StringBuilder("{\"format\": 1, \"id\": \"all_of_it\", \"name\": \"All Of It\", \"title\":"
                + " \"the Complete\", \"description\": \"Uses the whole catalogue.\", \"rarity\": \"epic\","
                + " \"starter_for\": \"tide_alliance\", \"factions\": [\"tide_alliance\", \"stoneguard\"],"
                + " \"skin_model\": \"slim\", \"color\": \"#3366CC\", \"element\": \"frost\", \"stats\": {\"health\": 30,"
                + " \"attack\": 2, \"armor\": 3, \"speed\": 104, \"knockback_resistance\": 20, \"size\": 108, \"growth\":"
                + " \"mystic\"}, \"resistances\": {\"fire\": -20, \"frost\": 40}, \"gear\": [{\"item\": \"trident\","
                + " \"name\": \"Tidefork\", \"enchantments\": {\"impaling\": 2, \"loyalty\": 1}}, \"shield\","
                + " \"turtle_helmet\", {\"item\": \"iron_boots\", \"enchantments\": {\"depth_strider\": 2}}],"
                + " \"behaviour\": {\"combat_role\": \"skirmisher\", \"focus\": \"heroes_first\", \"keep_distance\": 5,"
                + " \"aggression\": \"bold\", \"protects\": \"heart\", \"retreat_below\": 15, \"raids\": \"stays_home\","
                + " \"duty\": \"fisherman\"}, \"basic_attack\": {\"style\": \"trident\", \"on_hit\": [{\"type\": \"dot\","
                + " \"amount\": 1, \"seconds\": 3, \"max_stacks\": 2}, {\"type\": \"mark\", \"mark\": \"soaked\","
                + " \"max_stacks\": 4}, {\"type\": \"heal\", \"to\": \"self\", \"amount\": 1}], \"every_nth_hit\": {\"n\": 3,"
                + " \"effects\": [{\"type\": \"stun\", \"seconds\": 1}, {\"type\": \"damage\", \"amount\":"
                + " {\"base\": 1, \"mark\": \"soaked\", \"per_mark_stack\": 1}, \"drain\": 20}, {\"type\":"
                + " \"clear_marks\", \"mark\": \"soaked\"}]}}, \"skills\": [");
        String[] skills = {
                "{\"name\": \"Riptide Cut\", \"form\": \"melee\", \"radius\": 2, \"hits\": 2, \"hit_interval\": 0.4,"
                        + " \"cooldown\": 40, \"charges\": 2, \"effects\": [{\"type\": \"damage\", \"amount\": {\"attack_percent\": 60,"
                        + " \"target_missing_health_percent\": 10}}, {\"type\": \"knockback\", \"strength\": 3, \"up\": 1},"
                        + " {\"type\": \"ignite\", \"seconds\": 2, \"chance\": 50}, {\"type\": \"freeze\", \"if\":"
                        + " {\"condition\": \"target_is\", \"kind\": \"monster\"}}, {\"type\": \"shield\", \"to\":"
                        + " \"allies\", \"radius\": 6, \"amount\": {\"armor_percent\": 20}}]}",
                "{\"name\": \"Undertow\", \"form\": \"zone\", \"zone_at\": \"target\", \"range\": 14, \"radius\": 4,"
                        + " \"delay\": 1, \"duration\": 3, \"interval\": 1, \"cooldown\": 30, \"use_when\": \"enemies_near\","
                        + " \"use_value\": 3, \"effects\": [{\"type\": \"pull\", \"strength\": 2}, {\"type\":"
                        + " \"effect\", \"id\": \"slowness\", \"level\": 2, \"seconds\": 4}, {\"type\": \"stat\","
                        + " \"stat\": \"armor\", \"percent\": -20, \"seconds\": 6, \"max_stacks\": 2}, {\"type\":"
                        + " \"taunt\", \"seconds\": 2}, {\"type\": \"dispel\"}], \"look\": {\"particle\": \"dust\","
                        + " \"color\": \"#2244AA\", \"sound\": \"entity.dolphin.splash\", \"fangs\": true}}",
                "{\"name\": \"Low Tide\", \"form\": \"allies\", \"radius\": 9, \"cooldown\": 15, \"use_when\": \"out_of_combat\","
                        + " \"effects\": [{\"type\": \"heal\", \"amount\": {\"target_max_health_percent\": 15}},"
                        + " {\"type\": \"cleanse\"}, {\"type\": \"energy\", \"amount\": 10}, {\"type\":"
                        + " \"cooldown_cut\", \"seconds\": 3}, {\"type\": \"effect\", \"id\": \"water_breathing\","
                        + " \"seconds\": 20}], \"line\": \"Breathe.\"}",
                "{\"name\": \"Breaker\", \"form\": \"leap\", \"range\": 9, \"radius\": 3, \"windup\": 0.8, \"cooldown\": 14,"
                        + " \"unlock_level\": 4, \"use_when\": \"target_far\", \"use_value\": 7, \"effects\": [{\"type\":"
                        + " \"damage\", \"amount\": 6, \"element\": \"shock\"}, {\"type\": \"summon\", \"creature\":"
                        + " \"snow_golem\", \"count\": 2, \"seconds\": 15, \"name\": \"Foam\"}, {\"type\": \"empower\","
                        + " \"seconds\": 8}, {\"type\": \"mark\", \"mark\": \"focus\", \"to\": \"self\", \"glow\": true}]}",
        };
        sb.append(String.join(", ", skills)).append("], \"ultimate\": {\"name\": \"Maelstrom\", \"form\": \"nova\","
                + " \"radius\": 7, \"hits\": 3, \"hit_interval\": 0.5, \"windup\": 1, \"charge_rate\": 120, \"effects\":"
                + " [{\"type\": \"damage\", \"amount\": {\"base\": 3, \"max_health_percent\": 10}}, {\"type\": \"stun\","
                + " \"seconds\": 1}, {\"type\": \"move_self\", \"direction\": \"up\", \"distance\": 3}, {\"type\":"
                + " \"effect\", \"id\": \"invisibility\", \"to\": \"self\", \"seconds\": 4}], \"look\": {\"lightning\":"
                + " true, \"pose\": \"spear_throw\"}, \"line\": \"Drown.\"}, \"empowered\": {\"name\": \"High Water\","
                + " \"stats\": [{\"stat\": \"speed\", \"percent\": 20}, {\"stat\": \"damage_taken\", \"percent\": -15}],"
                + " \"on_hit\": [{\"type\": \"damage\", \"amount\": 2, \"element\": \"frost\"}], \"particle\": \"splash\","
                + " \"size\": 101, \"skills_locked\": false, \"line\": \"The tide is in.\"}, \"passives\": [");
        List<String> passives = new ArrayList<>();
        int i = 0;
        for (Trigger t : Trigger.values()) {
            if (passives.size() == 4) {
                break;
            }
            if (i++ % 4 != 0) {
                continue;
            }
            passives.add(passiveFor(t));
        }
        sb.append(String.join(", ", passives)).append("], \"base_abilities\": [{\"type\": \"aura\", \"name\":"
                + " \"Net Mender\", \"affix\": \"angler\", \"stacks\": 2, \"roles\": [\"fisherman\", \"chef\"]}, {\"type\":"
                + " \"mend\", \"percent\": 6}, {\"type\": \"lookout\", \"radius\": 40}], \"lines\": {\"greeting\":"
                + " [\"Fair winds, {player}.\"], \"kill\": [\"Under you go, {enemy}.\"], \"unlock\": [\"{ability}, at"
                + " level {level}.\"], \"skill\": [\"{ability}!\"]}}");
        return sb.toString();
    }

    static String passiveFor(Trigger t) {
        String fx = switch (t) {
            case ALWAYS -> "{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 10}";
            case ON_ALLY_LOW_HEALTH, ON_ALLY_HURT, ON_ALLY_ULTIMATE -> "{\"type\": \"shield\", \"amount\": 3}";
            case ON_KILL, ON_SKILL, ON_ULTIMATE, ON_FIGHT_START, ON_LOW_HEALTH, EVERY ->
                    "{\"type\": \"stat\", \"stat\": \"attack_speed\", \"percent\": 15, \"seconds\": 4}";
            default -> "{\"type\": \"damage\", \"amount\": 2}";
        };
        return "{\"name\": \"On " + t.id() + "\", \"trigger\": \"" + t.id() + "\"" + (t == Trigger.ON_MARK_FULL
                ? ", \"mark\": \"soaked\"" : "") + (t == Trigger.ALWAYS || t == Trigger.EVERY ? ""
                : ", \"cooldown\": 5") + ", \"effects\": [" + fx + "]}";
    }

    // ------------------------------------------------------------------ a small JSON model the test can damage

    static Object model(HeroJson.Node n) {
        return switch (n) {
            case HeroJson.Obj o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (HeroJson.Member mem : o.members()) {
                    m.put(mem.key(), model(mem.value()));
                }
                yield m;
            }
            case HeroJson.Arr a -> {
                List<Object> l = new ArrayList<>();
                for (HeroJson.Node i : a.items()) {
                    l.add(model(i));
                }
                yield l;
            }
            case HeroJson.Str s -> s.value();
            case HeroJson.Num x -> x.value();
            case HeroJson.Bool b -> b.value();
            case HeroJson.Null x -> null;
        };
    }

    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                sb.append(first ? "" : ", ");
                first = false;
                write(e.getKey(), sb);
                sb.append(": ");
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                sb.append(i == 0 ? "" : ", ");
                write(l.get(i), sb);
            }
            sb.append(']');
        } else if (o instanceof String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        } else if (o instanceof Double d) {
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : String.valueOf(d));
        } else {
            sb.append(o);
        }
    }

    /** A place in the model: a map entry or a list slot. */
    private record Slot2(Object holder, Object key) {
    }

    @SuppressWarnings("unchecked")
    private static void places(Object o, List<Slot2> out) {
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                out.add(new Slot2(m, e.getKey()));
                places(e.getValue(), out);
            }
        } else if (o instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                out.add(new Slot2(l, i));
                places(l.get(i), out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object get(Slot2 s) {
        return s.holder instanceof Map<?, ?> m ? m.get(s.key) : ((List<Object>) s.holder).get((Integer) s.key);
    }

    @SuppressWarnings("unchecked")
    private static void set(Slot2 s, Object v) {
        if (s.holder instanceof Map<?, ?>) {
            ((Map<String, Object>) s.holder).put((String) s.key, v);
        } else {
            ((List<Object>) s.holder).set((Integer) s.key, v);
        }
    }

    private static final List<String> WORDS = new ArrayList<>();

    static {
        for (HeroCatalogue.Vocab v : HeroCatalogue.vocabs().values()) {
            WORDS.addAll(v.words);
            WORDS.addAll(v.synonyms.keySet());
        }
        WORDS.addAll(List.of("", " ", "x", "Fire ", "minecraft:slowness", "absorption", "diamond_sword", "mending",
                "vitality", "{target}", "{Enemy} and {enemy}", "8 seconds", "25%", "true", "#12345", "#ABCDEF",
                "a\nb", "§cred", "nobody", "9lives", "y".repeat(300), "😀".repeat(20)));
    }

    private static Object junk(Random r) {
        return switch (r.nextInt(16)) {
            case 0 -> null;
            case 1 -> true;
            case 2 -> false;
            case 3 -> 0.0;
            case 4 -> -1.0;
            case 5 -> 1e9;
            case 6 -> -1e9;
            case 7 -> 3.7;
            case 8 -> 0.001;
            case 9 -> new ArrayList<>();
            case 10 -> new LinkedHashMap<String, Object>();
            case 11 -> new ArrayList<>(List.of(1.0, "x"));
            case 12 -> new LinkedHashMap<>(Map.of("type", "damage", "amount", 99.0));
            case 13 -> (double) r.nextInt(200) - 50;
            default -> WORDS.get(r.nextInt(WORDS.size()));
        };
    }

    @SuppressWarnings("unchecked")
    private static Object copy(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> c = new LinkedHashMap<>();
            ((Map<String, Object>) m).forEach((k, v) -> c.put(k, copy(v)));
            return c;
        }
        if (o instanceof List<?> l) {
            List<Object> c = new ArrayList<>();
            l.forEach(v -> c.add(copy(v)));
            return c;
        }
        return o;
    }

    /** One random change: junk for a value, a lost or misspelled field, a list grown past its limit, a moved part. */
    @SuppressWarnings("unchecked")
    private static void damage(Object root, Random r) {
        List<Slot2> all = new ArrayList<>();
        places(root, all);
        Slot2 s = all.get(r.nextInt(all.size()));
        switch (r.nextInt(8)) {
            case 0, 1, 2 -> set(s, junk(r));
            case 3 -> {
                if (s.holder instanceof Map<?, ?> m) {
                    m.remove(s.key);
                } else {
                    ((List<Object>) s.holder).remove((int) (Integer) s.key);
                }
            }
            case 4 -> {
                if (s.holder instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) s.holder;
                    String k = (String) s.key;
                    Object v = m.remove(k);
                    String nk = switch (r.nextInt(4)) {
                        case 0 -> k.length() > 2 ? k.substring(0, k.length() - 1) : k + "x";
                        case 1 -> k.toUpperCase();
                        case 2 -> k.length() > 3 ? k.substring(0, 1) + k.charAt(2) + k.charAt(1) + k.substring(3) : "_" + k;
                        default -> WORDS.get(r.nextInt(WORDS.size())).replace("\n", "");
                    };
                    m.putIfAbsent(nk, v);
                }
            }
            case 5 -> {
                Object v = get(s);
                if (v instanceof List<?> l && !l.isEmpty()) {
                    List<Object> list = (List<Object>) l;
                    int n = 1 + r.nextInt(12);
                    for (int i = 0; i < n; i++) {
                        list.add(copy(list.get(r.nextInt(list.size()))));
                    }
                } else if (v instanceof Double d) {
                    set(s, d * (r.nextBoolean() ? 50 : -3) + r.nextInt(3));
                } else {
                    set(s, junk(r));
                }
            }
            case 6 -> set(s, copy(get(all.get(r.nextInt(all.size())))));
            default -> {
                Object v = get(s);
                if (v instanceof String) {
                    set(s, WORDS.get(r.nextInt(WORDS.size())));
                } else if (v instanceof Double d) {
                    set(s, r.nextBoolean() ? String.valueOf(d) + (r.nextBoolean() ? " seconds" : "%") : d + 0.4);
                } else {
                    set(s, junk(r));
                }
            }
        }
    }

    // ------------------------------------------------------------------ what every loaded hero must obey

    private static void inRange(double v, double min, double max, String what) {
        assertTrue(v >= min - EPS && v <= max + EPS, what + " " + v + " outside " + min + ".." + max);
    }

    private static void effectRules(List<Effect> fx, Slot slot, boolean friendly, boolean receiverIsHero, int max,
                                    String where) {
        assertTrue(fx.size() <= max, where + ": " + fx.size() + " effects");
        int damage = 0, moves = 0;
        for (Effect e : fx) {
            String w = where + " " + e;
            Reach reach = HeroCatalogue.reach(e);
            To to = e.c().to();
            switch (reach) {
                case ENEMY -> assertTrue(to == To.TARGETS && !friendly, "an enemy-only effect on allies: " + w);
                case ALLY -> assertTrue(friendly || to != To.TARGETS, "an ally-only effect on enemies: " + w);
                case SELF -> assertEquals(To.SELF, to, w);
                default -> assertTrue(to == To.SELF || to == To.TARGETS && !friendly && !receiverIsHero, w);
            }
            inRange(e.c().chance(), 1, 100, w + " chance");
            inRange(e.c().radius(), 2, 12, w + " radius");
            if (e.c().cond() != null) {
                HeroDef.Condition c = e.c().cond();
                assertEquals(HeroCatalogue.conditionNeedsMark(c.type()), c.mark() != null, w);
                assertEquals(c.type() == HeroDef.ConditionType.TARGET_IS, c.kind() != null, w);
            }
            switch (e) {
                case HeroDef.Damage x -> {
                    damage++;
                    inRange(x.drain(), 0, 50, w);
                    amount(x.amount(), w);
                }
                case HeroDef.Dot x -> {
                    inRange(x.seconds(), 2, 10, w);
                    inRange(x.maxStacks(), 1, 5, w);
                    amount(x.amount(), w);
                }
                case HeroDef.Heal x -> amount(x.amount(), w);
                case HeroDef.Shield x -> {
                    inRange(x.seconds(), 2, 30, w);
                    amount(x.amount(), w);
                }
                case HeroDef.PotionEffect x -> {
                    PotionCap cap = x.helpful() ? HeroCatalogue.HELPFUL_POTIONS.get(x.id())
                            : HeroCatalogue.HARMFUL_POTIONS.get(x.id());
                    assertNotNull(cap, w);
                    inRange(x.level(), 1, cap.maxLevel(), w + " level");
                    inRange(x.seconds(), 1, cap.maxSeconds(), w + " seconds");
                    if (HeroCatalogue.SELF_ONLY_POTIONS.contains(x.id())) {
                        assertTrue(to == To.SELF || to == To.TARGETS && receiverIsHero, "only the hero: " + w);
                    }
                }
                case HeroDef.StatMod x -> {
                    ValueRange r = HeroCatalogue.STAT_RANGES.get(x.stat());
                    inRange(x.percent(), r.min(), r.max(), w);
                    inRange(x.maxStacks(), 1, 5, w);
                }
                case HeroDef.Knockback x -> {
                    inRange(x.strength(), 1, 5, w);
                    inRange(x.up(), 0, 3, w);
                }
                case HeroDef.Pull x -> inRange(x.strength(), 1, 3, w);
                case HeroDef.Ignite x -> inRange(x.seconds(), 1, 8, w);
                case HeroDef.Freeze x -> inRange(x.seconds(), 1, 6, w);
                case HeroDef.Stun x -> {
                    inRange(x.seconds(), 0, 2, w);
                    assertTrue(slot != Slot.ON_HIT, "a stun on every hit: " + w);
                }
                case HeroDef.Taunt x -> inRange(x.seconds(), 1, 8, w);
                case HeroDef.Energy x -> inRange(x.amount(), 5, 30, w);
                case HeroDef.CooldownCut x -> inRange(x.seconds(), 1, 10, w);
                case HeroDef.MoveSelf x -> {
                    moves++;
                    inRange(x.distance(), 2, 8, w);
                }
                case HeroDef.Mark x -> {
                    inRange(x.stacks(), 1, 5, w);
                    inRange(x.maxStacks(), 1, 10, w);
                    inRange(x.seconds(), 2, 30, w);
                    assertTrue(x.mark().matches("[a-z][a-z0-9_]{1,23}"), w);
                }
                case HeroDef.ClearMarks x -> assertTrue(x.mark().matches("[a-z][a-z0-9_]{1,23}"), w);
                case HeroDef.Empower x -> inRange(x.seconds(), 3, 20, w);
                case HeroDef.Summon x -> {
                    inRange(x.count(), 1, HeroCatalogue.SUMMONS.get(x.creature()).maxCount(), w);
                    inRange(x.seconds(), 10, 60, w);
                    assertTrue(slot == Slot.SKILL || slot == Slot.ULTIMATE, "a summon outside skills: " + w);
                }
                default -> {
                }
            }
        }
        assertTrue(damage <= 2, where + ": " + damage + " damage effects");
        assertTrue(moves <= 1, where + ": " + moves + " move_self");
    }

    private static void amount(HeroDef.Amount a, String w) {
        inRange(a.base(), 0, 30, w);
        inRange(a.attackPercent(), 0, 300, w);
        inRange(a.maxHealthPercent(), 0, 40, w);
        inRange(a.armorPercent(), 0, 100, w);
        inRange(a.targetMaxHealthPercent(), 0, 20, w);
        inRange(a.targetMissingHealthPercent(), 0, 30, w);
        inRange(a.perMarkStack(), 0, 6, w);
        assertTrue(a.mark() != null || a.perMarkStack() == 0, w);
    }

    private static void capped(Totals t, SlotCaps c, double zone, String where) {
        assertTrue(t.damage() <= c.damage() * zone + EPS, where + " damage " + t.damage());
        assertTrue(t.dot() <= c.dot() + EPS, where + " dot " + t.dot());
        assertTrue(t.heal() <= c.heal() * zone + EPS, where + " heal " + t.heal());
        assertTrue(t.shield() <= c.shield() + EPS, where + " shield " + t.shield());
        assertTrue(t.stun() <= c.stun() + EPS, where + " stun " + t.stun());
    }

    private static void text(String s, int max, String what) {
        assertTrue(s == null || s.length() <= max, what + " is " + (s == null ? 0 : s.length()) + " long");
        assertTrue(s == null || s.chars().noneMatch(ch -> ch < 0x20 || ch == 0x7F || ch == '§'), what + ": " + s);
    }

    private static void ability(Ability a, HeroDef d, Ref ref, String where) {
        text(a.name(), 24, where);
        assertFalse(a.name().isEmpty(), where);
        text(a.description(), 200, where);
        text(a.line(), 120, where);
        HeroCatalogue.FormSpec fs = HeroCatalogue.FORMS.get(a.form());
        if (fs.usesRange() && !(a.form() == Form.ZONE && a.zoneAt() != HeroDef.ZoneAt.TARGET)) {
            inRange(a.range(), fs.rangeMin(), fs.rangeMax(), where + " range");
        }
        if (fs.usesRadius()) {
            inRange(a.radius(), fs.radiusMin(), fs.radiusMax(), where + " radius");
        }
        inRange(a.count(), 2, 5, where);
        inRange(a.angle(), 30, 120, where);
        inRange(a.width(), 1, 3, where);
        inRange(a.hits(), 1, 5, where);
        inRange(a.hitInterval(), 0.2, 1, where);
        inRange(a.delay(), 0, 3, where);
        inRange(a.duration(), 0, 10, where);
        inRange(a.interval(), 0.5, 3, where);
        inRange(a.windup(), 0, 2, where);
        inRange(a.charges(), 1, 3, where);
        inRange(a.energyGain(), 0, 30, where);
        inRange(a.chargeRate(), 50, 150, where);
        inRange(a.unlockLevel(), 1, 30, where);
        ValueRange vr = HeroCatalogue.useValue(a.useWhen());
        if (vr != null) {
            inRange(a.useValue(), vr.min(), vr.max(), where + " use_value");
        }
        assertTrue(a.useWhen() != HeroDef.UseWhen.OUT_OF_COMBAT || a.form().friendly(), where);
        assertTrue(!a.look().lightning() || a.slot() == Slot.ULTIMATE, where);
        assertTrue(!a.look().fangs() || HeroCatalogue.fangsForm(a.form()), where);
        assertFalse(a.effects().isEmpty(), where + " has no effects");
        effectRules(a.effects(), a.slot(), a.form().friendly(), a.form() == Form.SELF, 5, where);
        for (Effect e : a.effects()) {
            assertTrue(!(e instanceof HeroDef.MoveSelf) || a.form() != Form.LEAP && a.form() != Form.DASH, where);
            assertTrue(!(e instanceof HeroDef.Empower) || d.empowered() != null, where);
        }
        capped(HeroBalance.totals(a.effects(), a.effectiveHits(), a.pulses(), ref), HeroCatalogue.CAPS.get(a.slot()),
                a.pulses() > 1 ? HeroCatalogue.ZONE_CAP_FACTOR : 1, where);
        double w = HeroBalance.abilityWeight(a, ref, d.empowered());
        assertEquals(w, a.numbers().weight(), EPS, where + " weight");
        if (a.slot() == Slot.SKILL) {
            assertTrue(a.cooldown() >= HeroBalance.cooldownFloor(w, a.charges()) - EPS, where + " cooldown "
                    + a.cooldown() + " under its floor");
            assertTrue(a.cooldown() >= 2, where);
        } else {
            inRange(a.numbers().minGap(), 20, 60, where + " gap");
        }
    }

    /** Every range, cap, floor and rule of the format, on a definition the reader produced. */
    static void sane(HeroDef d) {
        String id = d.id();
        assertTrue(HeroReader.validId(id), id);
        assertEquals(1, d.format());
        text(d.name(), 24, "name");
        assertFalse(d.name().isEmpty());
        text(d.title(), 32, "title");
        text(d.description(), 300, "description");
        assertTrue(d.starterFor() == null || HeroCatalogue.FACTIONS.contains(d.starterFor()));
        assertTrue(!d.factions().isEmpty() && d.factions().size() <= 4 && HeroCatalogue.FACTIONS.containsAll(
                d.factions()) && Set.copyOf(d.factions()).size() == d.factions().size());
        assertTrue(d.color() == HeroDef.NO_COLOR || d.color() >= 0 && d.color() <= 0xFFFFFF);
        inRange(d.stats().health(), 20, 40, "health");
        inRange(d.stats().attack(), 0, 4, "attack");
        inRange(d.stats().armor(), 0, 8, "armor");
        inRange(d.stats().speed(), 90, 120, "speed");
        inRange(d.stats().knockbackResistance(), 0, 60, "knockback resistance");
        inRange(d.stats().size(), 85, 110, "size");
        d.resistances().values().forEach(v -> inRange(v, -30, 60, "resistance"));
        inRange(d.behaviour().keepDistance(), 0, 16, "keep_distance");
        inRange(d.behaviour().retreatBelow(), 0, 60, "retreat_below");
        assertTrue(HeroCatalogue.DUTIES.contains(d.behaviour().duty()));
        // gear
        assertTrue(d.gear().size() <= 7, "6 items and the style's own");
        Set<HeroDef.GearSlot> slots = EnumSet.noneOf(HeroDef.GearSlot.class);
        int levels = 0;
        for (GearItem g : d.gear()) {
            assertTrue(slots.add(g.slot()), "two items in " + g.slot());
            assertEquals(HeroCatalogue.GEAR.get(g.item()).slot, g.slot());
            text(g.name(), 24, "gear name");
            assertTrue(g.enchantments().size() <= 3);
            Set<String> exclusive = new java.util.HashSet<>();
            for (Map.Entry<String, Integer> e : g.enchantments().entrySet()) {
                HeroCatalogue.Enchant en = HeroCatalogue.ENCHANTS.get(e.getKey());
                assertNotNull(en, e.getKey());
                assertTrue(en.accepts().contains(HeroCatalogue.GEAR.get(g.item())), e.getKey() + " on " + g.item());
                inRange(e.getValue(), 1, en.maxLevel(), e.getKey());
                assertTrue(en.exclusive() == null || exclusive.add(en.exclusive()), e.getKey());
                levels += e.getValue();
            }
        }
        assertTrue(levels <= 6, levels + " enchantment levels");
        HeroDef.Style style = d.basicAttack().style();
        if (style != HeroDef.Style.THROWN) {
            GearItem hand = d.gear().stream().filter(g -> g.slot() == HeroDef.GearSlot.MAINHAND).findFirst()
                    .orElse(null);
            assertNotNull(hand, "a " + style + " hero without its weapon");
            assertTrue(HeroCatalogue.servesStyle(HeroCatalogue.GEAR.get(hand.item()), style), hand.item());
        }
        // basic attack
        Ref ref = HeroBalance.ref(d);
        inRange(d.basicAttack().amount(), 2, 10, "thrown amount");
        inRange(d.basicAttack().interval(), 0.8, 3, "thrown interval");
        if (style == HeroDef.Style.THROWN) {
            assertTrue(d.basicAttack().amount() / d.basicAttack().interval() <= HeroCatalogue.THROWN_MAX_DPS + EPS);
        }
        SlotCaps onHit = HeroCatalogue.CAPS.get(Slot.ON_HIT);
        effectRules(d.basicAttack().onHit(), Slot.ON_HIT, false, false, 3, "on_hit");
        Totals basic = HeroBalance.totals(d.basicAttack().onHit(), 1, 1, ref);
        capped(basic, onHit, 1, "on_hit");
        if (d.basicAttack().everyNthHit() != null) {
            inRange(d.basicAttack().everyNthHit().n(), 2, 6, "n");
            assertFalse(d.basicAttack().everyNthHit().effects().isEmpty());
            effectRules(d.basicAttack().everyNthHit().effects(), Slot.NTH_HIT, false, false, 3, "finisher");
            capped(HeroBalance.totals(d.basicAttack().everyNthHit().effects(), 1, 1, ref),
                    HeroCatalogue.CAPS.get(Slot.NTH_HIT), 1, "finisher");
        }
        // abilities
        assertTrue(d.skills().size() <= 4);
        for (Ability a : d.skills()) {
            assertEquals(Slot.SKILL, a.slot());
            ability(a, d, ref, "skill " + a.name());
        }
        if (d.ultimate() != null) {
            assertEquals(Slot.ULTIMATE, d.ultimate().slot());
            ability(d.ultimate(), d, ref, "ultimate");
        }
        // passives
        assertTrue(d.passives().size() <= 4);
        for (Passive p : d.passives()) {
            String w = "passive " + p.name();
            text(p.name(), 24, w);
            assertTrue(HeroCatalogue.TRIGGERS.get(p.trigger()).forms().contains(p.form()), w + " form " + p.form());
            assertFalse(p.effects().isEmpty(), w);
            effectRules(p.effects(), Slot.PASSIVE, p.form().friendly(), p.form() == HeroDef.PassiveForm.SELF, 3, w);
            capped(HeroBalance.totals(p.effects(), 1, 1, ref), HeroCatalogue.CAPS.get(Slot.PASSIVE), 1, w);
            inRange(p.chance(), 1, 100, w);
            inRange(p.unlockLevel(), 1, 30, w);
            assertEquals(p.trigger() == Trigger.ON_MARK_FULL, p.mark() != null, w);
            double weight = HeroBalance.passiveWeight(p, ref, d.empowered());
            double floor = HeroBalance.passiveFloor(weight, p.chance(), p.trigger());
            ValueRange vr = HeroCatalogue.TRIGGERS.get(p.trigger()).value();
            if (p.trigger() == Trigger.ALWAYS) {
                for (Effect e : p.effects()) {
                    assertTrue(e instanceof HeroDef.StatMod s && s.seconds() == 0 && Math.abs(s.percent())
                            <= HeroCatalogue.ALWAYS_STAT_CAP, w + ": " + e);
                }
            } else if (p.trigger() == Trigger.EVERY) {
                assertTrue(p.triggerValue() >= floor - EPS && p.triggerValue() >= vr.min(), w + " period");
            } else {
                assertTrue(p.cooldown() >= floor - EPS, w + " cooldown " + p.cooldown() + " under " + floor);
                if (vr != null) {
                    inRange(p.triggerValue(), vr.min(), vr.max(), w);
                }
            }
            for (Effect e : p.effects()) {
                assertTrue(p.trigger() == Trigger.ALWAYS || !(e instanceof HeroDef.StatMod s) || s.seconds() >= 2, w);
                assertTrue(!(e instanceof HeroDef.Energy) || p.trigger() != Trigger.ON_ULTIMATE, w);
                assertTrue(!(e instanceof HeroDef.Empower) || d.empowered() != null, w);
            }
        }
        // the empowered state
        if (d.empowered() != null) {
            HeroDef.Empowered e = d.empowered();
            text(e.name(), 24, "empowered");
            assertTrue(e.stats().size() <= 4);
            for (HeroDef.StatBonus b : e.stats()) {
                ValueRange r = HeroBalance.statRange(b.stat(), HeroCatalogue.EMPOWERED_STAT_CAP);
                inRange(b.percent(), r.min(), r.max(), "empowered " + b.stat());
            }
            inRange(e.size(), 100, 110, "empowered size");
            effectRules(e.onHit(), Slot.ON_HIT, false, false, 2, "empowered on_hit");
            Totals t = HeroBalance.totals(e.onHit(), 1, 1, ref);
            capped(new Totals(t.damage() + basic.damage(), t.dot() + basic.dot(), t.heal() + basic.heal(),
                    t.shield() + basic.shield(), t.stun() + basic.stun()), onHit, 1, "on_hit while empowered");
        }
        // base abilities
        assertTrue(d.baseAbilities().size() <= 3);
        for (HeroDef.BaseAbility b : d.baseAbilities()) {
            inRange(b.unlockLevel(), 1, 30, "base ability");
            text(b.name(), 24, "base ability");
            switch (b) {
                case HeroDef.Aura a -> {
                    assertTrue(HeroCatalogue.AURA_AFFIXES.contains(a.affix()), a.affix());
                    inRange(a.stacks(), 1, 3, "aura");
                    inRange(a.radius(), 8, 32, "aura");
                    assertTrue(!a.roles().isEmpty() && HeroCatalogue.ROLE_IDS.containsAll(a.roles()));
                }
                case HeroDef.Mend m -> {
                    inRange(m.percent(), 1, 10, "mend");
                    inRange(m.every(), 5, 30, "mend");
                    inRange(m.radius(), 8, 24, "mend");
                }
                case HeroDef.Lookout l -> inRange(l.radius(), 16, 48, "lookout");
                case HeroDef.Rally r -> inRange(r.percent(), 5, 25, "rally");
                case HeroDef.Drill x -> {
                    inRange(x.xp(), 1, 3, "drill");
                    inRange(x.radius(), 8, 24, "drill");
                }
            }
        }
        // lines
        Pattern ph = Pattern.compile("\\{([A-Za-z_]+)}");
        for (Map.Entry<LineEvent, List<String>> e : d.lines().entrySet()) {
            assertTrue(!e.getValue().isEmpty() && e.getValue().size() <= 8, e.getKey().id());
            for (String line : e.getValue()) {
                text(line, 120, "line");
                assertFalse(line.isEmpty());
                Matcher m = ph.matcher(line);
                while (m.find()) {
                    assertTrue(HeroCatalogue.PLACEHOLDERS.get(e.getKey()).contains(m.group(1)), e.getKey().id() + ": "
                            + line);
                }
            }
        }
        // the caps and floors are stable: checking the checked hero again changes nothing
        HeroDef again = HeroBalance.apply(d, (w, f, c, m) -> fail("the balance pass is not stable: " + c + " " + m));
        assertEquals(d, again);
    }

    private static void wellFormed(Loaded l, String file) {
        for (Problem p : l.problems().all()) {
            String f = p.format();
            assertEquals(file, p.file());
            assertTrue(f.startsWith(file) && f.endsWith(" " + p.severity().label + "."), f);
            assertFalse(p.message().isBlank(), f);
            // the only "null" a message may name is the file's own JSON null; any other is a Java null that leaked
            assertFalse(f.replace("not null", "").replace("is null, which means nothing", "").contains("null"),
                    "a null in a message: " + f);
            assertFalse(f.contains("NaN"), "a NaN in a message: " + f);
            assertTrue(p.line() >= 0 && p.column() >= 0, f);
            assertNotNull(p.path(), f);
            assertNotNull(p.machinePath(), f);
            assertTrue(p.log().startsWith("[Heroes] " + p.severity().label + " " + p.code() + " " + file), p.log());
            assertEquals(p.severity() == Severity.PART_LEFT_OUT, f.endsWith("PART LEFT OUT.") , f);
        }
    }

    private static void check(String file, String text, String label) {
        Loaded l;
        try {
            l = HeroReader.read(file, text);
        } catch (RuntimeException | StackOverflowError e) {
            throw new AssertionError(label + ": the reader threw " + e + " on\n" + text, e);
        }
        try {
            wellFormed(l, file);
            String id = HeroReader.idFromFileName(file);
            if (l.hero(id) != null) {
                assertTrue(l.problems().of(id).stream().noneMatch(p -> p.severity() == Severity.NOT_LOADED), label);
                assertTrue(l.notLoaded().isEmpty(), label);
                sane(l.hero(id));
            } else {
                assertEquals(List.of(id), l.notLoaded(), label);
                assertTrue(l.problems().count(Severity.NOT_LOADED) >= 1, label);
                assertTrue(l.heroes().isEmpty(), label);
            }
        } catch (AssertionError e) {
            throw new AssertionError(label + ": " + e.getMessage() + "\nfile:\n" + text + "\nreport:\n"
                    + l.problems().report(), e);
        }
    }

    // ------------------------------------------------------------------ the tests

    private static Map<String, String> bases() throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String id : List.of("bram_ostler", "sela_quickfern", "maudie_brine")) {
            m.put(id + ".json", Files.readString(Path.of("docs/heroes/examples/" + id + ".json")));
        }
        m.put("all_of_it.json", everything());
        return m;
    }

    @Test
    void theHeroWithEverythingLoadsCleanAndObeysEveryRule() {
        Loaded l = HeroReader.read("all_of_it.json", everything());
        HeroDef d = l.hero("all_of_it");
        assertNotNull(d, l.problems().report());
        assertTrue(l.problems().all().isEmpty(), "it loads with nothing to report:\n" + l.problems().report());
        sane(d);
        Set<HeroDef.EffectType> types = EnumSet.noneOf(HeroDef.EffectType.class);
        HeroBalance.effectLists(d).forEach(list -> list.forEach(e -> types.add(e.type())));
        assertEquals(EnumSet.allOf(HeroDef.EffectType.class), types, "every effect type is in it");
    }

    @Test
    void theUndamagedHeroesObeyEveryRule() throws IOException {
        for (Map.Entry<String, String> b : bases().entrySet()) {
            check(b.getKey(), b.getValue(), b.getKey());
            Object back = model(HeroJson.parse(b.getValue()).root());
            assertEquals(HeroReader.read(b.getKey(), b.getValue()).heroes(), HeroReader.read(b.getKey(),
                    write(back)).heroes(), "the test's own JSON writer keeps the hero: " + b.getKey());
        }
    }

    @Test
    void noDamagedFileBreaksTheReaderOrARule() throws IOException {
        int loaded = 0, refused = 0;
        for (Map.Entry<String, String> b : bases().entrySet()) {
            Object clean = model(HeroJson.parse(b.getValue()).root());
            for (int seed = 0; seed < 1500; seed++) {
                Random r = new Random(seed * 31L + b.getKey().hashCode());
                Object m = copy(clean);
                int changes = 1 + r.nextInt(4);
                for (int i = 0; i < changes; i++) {
                    damage(m, r);
                }
                String text = write(m);
                check(b.getKey(), text, b.getKey() + " seed " + seed);
                if (HeroReader.read(b.getKey(), text).heroes().isEmpty()) {
                    refused++;
                } else {
                    loaded++;
                }
            }
        }
        assertTrue(loaded > 3000 && refused > 100, "both outcomes are exercised: " + loaded + " loaded, " + refused
                + " refused");
    }

    @Test
    void everyTriggerWithEveryFormAndEffectObeysTheRules() {
        String[] effects = {"{\"type\": \"damage\", \"amount\": 30}", "{\"type\": \"heal\", \"amount\": 30}",
                "{\"type\": \"shield\", \"amount\": 30, \"to\": \"allies\"}", "{\"type\": \"stun\", \"seconds\": 2}",
                "{\"type\": \"mark\", \"mark\": \"mk\"}", "{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": 50}",
                "{\"type\": \"stat\", \"stat\": \"armor\", \"percent\": -50, \"seconds\": 30}",
                "{\"type\": \"effect\", \"id\": \"invisibility\", \"seconds\": 30}",
                "{\"type\": \"summon\", \"creature\": \"wolf\", \"count\": 3}", "{\"type\": \"energy\", \"amount\": 30}",
                "{\"type\": \"dot\", \"amount\": 10, \"seconds\": 10}", "{\"type\": \"empower\", \"seconds\": 20}"};
        int n = 0;
        for (Trigger t : Trigger.values()) {
            for (HeroDef.PassiveForm f : HeroDef.PassiveForm.values()) {
                for (String fx : effects) {
                    String text = HeroReaderTest.hero("\"empowered\": {\"name\": \"E\"}, \"passives\": [{\"name\": \"P\","
                            + " \"trigger\": \"" + t.id() + "\", \"form\": \"" + f.id() + "\", \"mark\": \"mk\","
                            + " \"cooldown\": 1, \"trigger_value\": 3, \"chance\": 100, \"effects\": [" + fx + ", " + fx
                            + "]}]");
                    check("test_hero.json", text, t.id() + "/" + f.id() + "/" + fx);
                    n++;
                }
            }
        }
        for (Form f : Form.values()) {
            for (String fx : effects) {
                for (String slot : List.of("skills", "ultimate")) {
                    String a = "{\"name\": \"A\", \"form\": \"" + f.id() + "\", \"hits\": 5, \"count\": 5, \"duration\":"
                            + " 10, \"interval\": 0.5, \"cooldown\": 2, \"charges\": 3, \"effects\": [" + fx + ", " + fx
                            + ", " + fx + "]}";
                    String text = HeroReaderTest.hero("\"empowered\": {\"name\": \"E\", \"on_hit\": [" + fx + "]}, \""
                            + slot + "\": " + (slot.equals("skills") ? "[" + a + "]" : a) + ", \"basic_attack\":"
                            + " {\"style\": \"melee\", \"on_hit\": [" + fx + "], \"every_nth_hit\": {\"n\": 2,"
                            + " \"effects\": [" + fx + "]}}").replace("\"basic_attack\": {\"style\": \"melee\"}, ", "");
                    check("test_hero.json", text, f.id() + "/" + slot + "/" + fx);
                    n++;
                }
            }
        }
        assertEquals(Trigger.values().length * HeroDef.PassiveForm.values().length * effects.length
                + Form.values().length * effects.length * 2, n);
    }
}
