package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.Field;
import com.factionclash.logic.hero.HeroCatalogue.Vocab;
import com.factionclash.logic.hero.HeroDef.Role;
import com.factionclash.logic.hero.HeroDef.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HeroCatalogue} is the single source of the format: it must say what the specification and the Python
 * reference checker say. This test reads {@code tools/herotool/herotool.py}'s tables (word lists, allowlists, caps,
 * role presets, trigger forms, placeholders) and {@code HERO_FORMAT.md} Appendix C (synonyms) and compares.
 */
class HeroCatalogueTest {
    private static String py;

    @BeforeAll
    static void load() throws IOException {
        py = Files.readString(Path.of("tools/herotool/herotool.py"));
    }

    private static String block(String name) {
        Matcher m = Pattern.compile("(?m)^" + name + " = (.*?)(?=^\\S)", Pattern.DOTALL).matcher(py);
        assertTrue(m.find(), name + " in herotool.py");
        return m.group(1);
    }

    private static List<String> words(String text) {
        List<String> l = new ArrayList<>();
        Matcher m = Pattern.compile("\"([a-z0-9_.]+)\"").matcher(text);
        while (m.find()) {
            l.add(m.group(1));
        }
        return l;
    }

    private static List<String> pyList(String name) {
        return words(block(name));
    }

    /** The words of format 1 (the reference checker's format): format 2's additions are in HeroCatalogueV2Test. */
    private static <E extends Enum<E> & HeroDef.Word> List<String> ids(Class<E> c) {
        List<String> l = new ArrayList<>();
        for (E e : HeroDef.Word.inFormat(c, 1)) {
            l.add(e.id());
        }
        return l;
    }

    @Test
    void theWordListsAreTheReferenceCheckers() {
        assertEquals(pyList("FACTIONS"), HeroCatalogue.FACTIONS);
        assertEquals(pyList("ELEMENTS"), ids(HeroDef.Element.class));
        assertEquals(pyList("RARITIES"), ids(HeroDef.Rarity.class));
        assertEquals(pyList("ROLES"), ids(Role.class));
        assertEquals(pyList("GROWTH"), ids(HeroDef.Growth.class));
        assertEquals(pyList("FOCUS"), ids(HeroDef.Focus.class));
        assertEquals(pyList("AGGRESSION"), ids(HeroDef.Aggression.class));
        // equal treatment (HEROES_V2 6.15): v1's player-only words are not words any more; each is read as another
        List<String> protects = new ArrayList<>(pyList("PROTECTS"));
        assertTrue(protects.remove("players"));
        assertEquals(protects, ids(HeroDef.Protects.class));
        assertEquals("allies", HeroCatalogue.EQUAL_READINGS.get("protects").get("players").readAs());
        assertEquals(pyList("RAIDS"), ids(HeroDef.Raids.class));
        assertEquals(pyList("WORKER_ROLES"), HeroCatalogue.WORKER_ROLES);
        List<String> duties = new ArrayList<>(List.of("guard", "scout"));
        duties.addAll(pyList("WORKER_ROLES"));
        assertEquals(duties, HeroCatalogue.DUTIES);
        List<String> roles = new ArrayList<>(pyList("WORKER_ROLES"));
        roles.addAll(List.of("scout", "swordsman", "archer", "crossbowman", "axe_fighter"));
        assertEquals(roles, HeroCatalogue.ROLE_IDS);
        assertEquals(pyList("STYLES"), ids(HeroDef.Style.class));
        assertEquals(pyList("ITEM_LOOKS"), HeroCatalogue.ITEM_LOOKS);
        assertEquals(pyList("HELD_PROPS"), HeroCatalogue.HELD_PROPS);
        assertEquals(pyList("FORMS"), ids(HeroDef.Form.class));
        assertEquals(pyList("ZONE_AT"), ids(HeroDef.ZoneAt.class));
        assertEquals(pyList("USE_WHEN"), ids(HeroDef.UseWhen.class));
        assertEquals(pyList("TO"), ids(HeroDef.To.class));
        assertEquals(pyList("CONDITIONS"), ids(HeroDef.ConditionType.class));
        List<String> kinds = new ArrayList<>(pyList("KINDS"));
        kinds.set(kinds.indexOf("player"), "person");
        assertTrue(kinds.remove("npc"));
        assertEquals(kinds, ids(HeroDef.TargetKind.class));
        assertEquals(java.util.Set.of("player", "npc"), HeroCatalogue.EQUAL_READINGS.get("target_kind").keySet());
        HeroCatalogue.EQUAL_READINGS.forEach((vocab, words) -> words.forEach((word, reading) -> {
            assertFalse(HeroCatalogue.vocab(vocab).has(word), word + " is no longer a word of " + vocab);
            assertTrue(HeroCatalogue.vocab(vocab).has(reading.readAs()), word + " is read as a word of " + vocab);
        }));
        assertEquals(pyList("EFFECT_TYPES"), ids(HeroDef.EffectType.class));
        assertEquals(pyList("STATS"), ids(HeroDef.Stat.class));
        assertEquals(pyList("POSES"), ids(HeroDef.Pose.class));
        assertEquals(pyList("TRIGGERS"), ids(HeroDef.Trigger.class));
        assertEquals(pyList("PASSIVE_FORMS"), ids(HeroDef.PassiveForm.class));
        assertEquals(pyList("AFFIXES"), HeroCatalogue.AURA_AFFIXES);
        assertEquals(pyList("BASE_TYPES"), ids(HeroDef.BaseType.class));
        assertEquals(pyList("EVENTS"), ids(HeroDef.LineEvent.class));
        assertEquals(pyList("PARTICLES"), HeroCatalogue.PARTICLES);
        assertEquals(45, HeroCatalogue.PARTICLES.size());
        assertEquals(pyList("SOUNDS"), HeroCatalogue.SOUNDS);
        assertEquals(61, HeroCatalogue.SOUNDS.size());
        List<String> gear = new ArrayList<>(words(block("GEAR").split("\\+")[0]));
        for (String m : List.of("leather", "chainmail", "iron", "golden")) {
            for (String p : List.of("helmet", "chestplate", "leggings", "boots")) {
                gear.add(m + "_" + p);
            }
        }
        assertEquals(new java.util.TreeSet<>(gear), new java.util.TreeSet<>(HeroCatalogue.GEAR.keySet()));
    }

    @Test
    void theAllowlistsAndCapsAreTheReferenceCheckers() {
        Pattern pair = Pattern.compile("\"([a-z_]+)\": \\((-?\\d+), (-?\\d+)\\)");
        Map<String, String> helpful = new LinkedHashMap<>(), harmful = new LinkedHashMap<>(),
                stats = new LinkedHashMap<>();
        for (Object[] t : new Object[][]{{"HELPFUL_POTIONS", helpful}, {"HARMFUL_POTIONS", harmful},
                {"STAT_CAPS", stats}}) {
            Matcher m = pair.matcher(block((String) t[0]));
            while (m.find()) {
                @SuppressWarnings("unchecked") Map<String, String> into = (Map<String, String>) t[1];
                into.put(m.group(1), m.group(2) + "/" + m.group(3));
            }
        }
        Map<String, String> javaHelpful = new LinkedHashMap<>(), javaHarmful = new LinkedHashMap<>(),
                javaStats = new LinkedHashMap<>();
        HeroCatalogue.HELPFUL_POTIONS.forEach((k, v) -> javaHelpful.put(k, v.maxLevel() + "/" + (int) v.maxSeconds()));
        HeroCatalogue.HARMFUL_POTIONS.forEach((k, v) -> javaHarmful.put(k, v.maxLevel() + "/" + (int) v.maxSeconds()));
        HeroCatalogue.STAT_RANGES.forEach((k, v) -> javaStats.put(k.id(), (int) v.min() + "/" + (int) v.max()));
        assertEquals(helpful, javaHelpful);
        assertEquals(harmful, javaHarmful);
        assertEquals(stats, javaStats);
        Map<String, Integer> ench = new LinkedHashMap<>();
        Matcher e = Pattern.compile("\"([a-z_]+)\": (\\d+)").matcher(block("ENCHANTS"));
        while (e.find()) {
            ench.put(e.group(1), Integer.parseInt(e.group(2)));
        }
        Map<String, Integer> javaEnch = new LinkedHashMap<>();
        HeroCatalogue.ENCHANTS.forEach((k, v) -> javaEnch.put(k, v.maxLevel()));
        assertEquals(ench, javaEnch);
        Matcher s = Pattern.compile("\"([a-z_]+)\": \\((\\d+), ([\\d.]+)\\)").matcher(block("SUMMONS"));
        int n = 0;
        while (s.find()) {
            HeroCatalogue.SummonSpec spec = HeroCatalogue.SUMMONS.get(HeroCatalogue.enumOf(HeroDef.Creature.class,
                    s.group(1)));
            assertEquals(Integer.parseInt(s.group(2)), spec.maxCount(), s.group(1));
            assertEquals(Double.parseDouble(s.group(3)), spec.weight(), 1e-9, s.group(1));
            n++;
        }
        assertEquals(HeroDef.Creature.values().length, n);
        Matcher c = Pattern.compile("\"([a-z_]+)\": dict\\(damage=(\\d+), dot=(\\d+), heal=(\\d+), shield=(\\d+),"
                + " stun=(\\d+)\\)").matcher(block("CAPS"));
        Map<String, Slot> slots = Map.of("on_hit", Slot.ON_HIT, "nth_hit", Slot.NTH_HIT, "skill", Slot.SKILL,
                "ultimate", Slot.ULTIMATE, "passive", Slot.PASSIVE);
        n = 0;
        while (c.find()) {
            HeroCatalogue.SlotCaps caps = HeroCatalogue.CAPS.get(slots.get(c.group(1)));
            assertEquals(List.of(c.group(2), c.group(3), c.group(4), c.group(5), c.group(6)), List.of(
                    Problems.num(caps.damage()), Problems.num(caps.dot()), Problems.num(caps.heal()),
                    Problems.num(caps.shield()), Problems.num(caps.stun())), c.group(1));
            n++;
        }
        assertEquals(Slot.values().length, n);
        Matcher sp = Pattern.compile("\"([a-z]+)\": (\\d+)").matcher(block("STYLE_POWER"));
        while (sp.find()) {
            assertEquals(Double.parseDouble(sp.group(2)), HeroCatalogue.referenceAttack(HeroCatalogue.enumOf(
                    HeroDef.Style.class, sp.group(1)), 4, 0), 1e-9, sp.group(1));
        }
    }

    @Test
    void rolePresetsTriggerFormsValuesAndPlaceholders() {
        Matcher r = Pattern.compile("\"([a-z]+)\": dict\\(([^)]*)\\)").matcher(block("ROLE_DEFAULTS"));
        int n = 0;
        while (r.find()) {
            Map<String, String> kv = new TreeMap<>();
            for (String part : r.group(2).replaceAll("\\s+", " ").split(", ")) {
                String[] p = part.split("=");
                kv.put(p[0].trim(), p[1].trim().replace("\"", ""));
            }
            HeroCatalogue.RolePreset p = HeroCatalogue.ROLES.get(HeroCatalogue.enumOf(Role.class, r.group(1)));
            Map<String, String> java = new TreeMap<>(Map.ofEntries(Map.entry("health", Problems.num(p.health())),
                    Map.entry("attack", Problems.num(p.attack())), Map.entry("armor", Problems.num(p.armor())),
                    Map.entry("speed", String.valueOf(p.speed())),
                    Map.entry("knockback_resistance", String.valueOf(p.knockbackResistance())),
                    Map.entry("growth", p.growth().id()), Map.entry("focus", p.focus().id()),
                    Map.entry("keep_distance", Problems.num(p.keepDistance())),
                    Map.entry("protects", p.protects().id()), Map.entry("aggression", p.aggression().id()),
                    Map.entry("retreat_below", String.valueOf(p.retreatBelow())), Map.entry("raids", p.raids().id())));
            assertEquals(kv, java, r.group(1));
            n++;
        }
        assertEquals(HeroDef.Word.inFormat(Role.class, 1).size(), n);
        Matcher t = Pattern.compile("\"([a-z_]+)\": \\[([^\\]]*)\\]").matcher(block("TRIGGER_FORMS"));
        n = 0;
        while (t.find()) {
            HeroCatalogue.TriggerSpec ts = HeroCatalogue.TRIGGERS.get(HeroCatalogue.enumOf(HeroDef.Trigger.class,
                    t.group(1)));
            assertEquals(words(t.group(2)), ts.forms().stream().map(HeroDef.Word::id).toList(), t.group(1));
            n++;
        }
        assertEquals(HeroDef.Word.inFormat(HeroDef.Trigger.class, 1).size(), n);
        Matcher d = Pattern.compile("\"([a-z_]+)\": \"([a-z_]+)\"").matcher(block("TRIGGER_DEFAULT_FORM"));
        while (d.find()) {
            assertEquals(d.group(2), HeroCatalogue.TRIGGERS.get(HeroCatalogue.enumOf(HeroDef.Trigger.class,
                    d.group(1))).defaultForm().id(), "default form of " + d.group(1));
        }
        Matcher v = Pattern.compile("\"([a-z_]+)\": \\((\\d+), (\\d+), (\\d+)\\)").matcher(block("TRIGGER_VALUE"));
        while (v.find()) {
            HeroCatalogue.ValueRange vr = HeroCatalogue.TRIGGERS.get(HeroCatalogue.enumOf(HeroDef.Trigger.class,
                    v.group(1))).value();
            assertEquals(v.group(2) + "/" + v.group(3) + "/" + v.group(4), Problems.num(vr.min()) + "/"
                    + Problems.num(vr.max()) + "/" + Problems.num(vr.dflt()), v.group(1));
        }
        Matcher u = Pattern.compile("\"([a-z_]+)\": \\((\\d+), (\\d+), (\\d+)\\)").matcher(block("USE_VALUE"));
        n = 0;
        while (u.find()) {
            HeroCatalogue.ValueRange vr = HeroCatalogue.useValue(HeroCatalogue.enumOf(HeroDef.UseWhen.class,
                    u.group(1)));
            assertEquals(u.group(2) + "/" + u.group(3) + "/" + u.group(4), Problems.num(vr.min()) + "/"
                    + Problems.num(vr.max()) + "/" + Problems.num(vr.dflt()), u.group(1));
            n++;
        }
        assertEquals(6, n);
        Matcher ph = Pattern.compile("\"([a-z_]+)\": \\[([^\\]]*)\\]").matcher(block("PLACEHOLDERS"));
        n = 0;
        while (ph.find()) {
            assertEquals(words(ph.group(2)), HeroCatalogue.PLACEHOLDERS.get(HeroCatalogue.enumOf(HeroDef.LineEvent.class,
                    ph.group(1))), ph.group(1));
            n++;
        }
        assertEquals(HeroDef.Word.inFormat(HeroDef.LineEvent.class, 1).size(), n);
    }

    @Test
    void theSynonymsAreAppendixC() throws IOException {
        String doc = Files.readString(Path.of("docs/heroes/HERO_FORMAT.md"));
        String appendix = doc.substring(doc.indexOf("## Appendix C"));
        Map<String, String> rows = Map.of("`combat_role`", "combat_role", "`element`", "element", "`form`", "form",
                "effect `type`", "effect_type", "`use_when`", "use_when", "`trigger`", "trigger",
                "base ability `type`", "base_type", "`lines` events", "line_event");
        int found = 0;
        for (String line : appendix.split("\n")) {
            if (!line.startsWith("| ") || line.startsWith("| Field") || line.startsWith("|---")) {
                continue;
            }
            String[] cells = line.split("\\|");
            String field = cells[1].trim();
            String vocab = rows.get(field);
            assertNotNull(vocab, "an Appendix C row the test does not know: " + field);
            Map<String, String> want = new TreeMap<>();
            Matcher m = Pattern.compile("((?:`[a-z_]+`(?:, )?)+) → ([a-z_]+)").matcher(cells[2]);
            while (m.find()) {
                Matcher w = Pattern.compile("`([a-z_]+)`").matcher(m.group(1));
                while (w.find()) {
                    want.put(w.group(1), m.group(2));
                }
            }
            assertEquals(want, new TreeMap<>(HeroCatalogue.vocab(vocab).synonyms), field);
            found++;
        }
        assertEquals(rows.size(), found, "every Appendix C row");
    }

    @Test
    void theTablesCoverEveryWord() {
        for (HeroDef.Form f : HeroDef.Form.values()) {
            assertNotNull(HeroCatalogue.FORMS.get(f), f.id());
        }
        for (HeroDef.EffectType t : HeroDef.EffectType.values()) {
            assertNotNull(HeroCatalogue.EFFECTS.get(t), t.id());
        }
        for (HeroDef.BaseType t : HeroDef.BaseType.values()) {
            assertNotNull(HeroCatalogue.BASES.get(t), t.id());
        }
        for (HeroDef.Growth g : HeroDef.Growth.values()) {
            assertNotNull(HeroCatalogue.GROWTH.get(g), g.id());
        }
        for (HeroDef.Stat s : HeroDef.Stat.values()) {
            assertNotNull(HeroCatalogue.STAT_RANGES.get(s), s.id());
        }
        for (Slot s : Slot.values()) {
            assertNotNull(HeroCatalogue.CAPS.get(s), s.id());
        }
        // every word field names a list that exists; every allowed potion has a class
        List<Map<String, Field>> shapes = new ArrayList<>(List.of(HeroCatalogue.TOP, HeroCatalogue.STATS,
                HeroCatalogue.BEHAVIOUR, HeroCatalogue.BASIC_ATTACK, HeroCatalogue.NTH_HIT, HeroCatalogue.ABILITY,
                HeroCatalogue.EFFECT_COMMON, HeroCatalogue.AMOUNT, HeroCatalogue.CONDITION, HeroCatalogue.LOOK,
                HeroCatalogue.PASSIVE, HeroCatalogue.EMPOWERED, HeroCatalogue.STAT_BONUS, HeroCatalogue.BASE_COMMON,
                HeroCatalogue.GEAR_ITEM));
        shapes.addAll(HeroCatalogue.EFFECTS.values());
        shapes.addAll(HeroCatalogue.BASES.values());
        for (Map<String, Field> sh : shapes) {
            for (Field f : sh.values()) {
                if (f.kind() == HeroCatalogue.Kind.WORD) {
                    Vocab v = HeroCatalogue.vocab(f.vocab());
                    assertFalse(v.words.isEmpty(), f.name());
                }
                if (f.kind() == HeroCatalogue.Kind.INT || f.kind() == HeroCatalogue.Kind.NUM) {
                    assertTrue(f.min() <= f.max(), f.name());
                    assertTrue(Double.isNaN(f.dflt()) || f.dflt() >= f.min() && f.dflt() <= f.max(), f.name());
                }
            }
        }
        for (HeroCatalogue.FormSpec fs : HeroCatalogue.FORMS.values()) {
            assertTrue(!fs.usesRange() || fs.rangeMin() <= fs.rangeDefault() && fs.rangeDefault() <= fs.rangeMax());
            assertTrue(!fs.usesRadius() || fs.radiusMin() <= fs.radiusDefault()
                    && fs.radiusDefault() <= fs.radiusMax());
        }
        assertEquals("slow_falling", HeroCatalogue.norm("  Minecraft:Slow - Falling "));
        assertEquals("entity.blaze.shoot", HeroCatalogue.norm("minecraft:entity.blaze.shoot"));
        assertTrue(HeroCatalogue.tooGood("netherite_axe") && HeroCatalogue.tooGood("diamond_helmet"));
        assertFalse(HeroCatalogue.tooGood("iron_axe"));
    }
}
