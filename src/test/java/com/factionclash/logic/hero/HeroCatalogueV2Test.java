package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Word;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Format 2's word lists and tables against their sources: the roster checker ({@code tools/roster/check_roster.py}:
 * archetypes, the default primary trait and flight carrier of each, talents, aura affixes, duel styles, the airborne
 * traits), HEROES_V2 Appendix A (the 30 temperaments' traits), the line events' placeholders, and the format split
 * (format 1's words frozen, format 2's a superset whose synonyms never shadow a word).
 */
class HeroCatalogueV2Test {
    private static String roster() throws IOException {
        return Files.readString(Path.of("tools/roster/check_roster.py"));
    }

    /** A Python list literal assigned to {@code name}, as strings. */
    private static List<String> pyList(String src, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + " = \\[(.*?)]", Pattern.DOTALL).matcher(src);
        assertTrue(m.find(), name);
        List<String> l = new ArrayList<>();
        Matcher s = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (s.find()) {
            l.add(s.group(1));
        }
        return l;
    }

    /** A Python dict literal of string to list-of-strings (or string), in order. */
    private static Map<String, List<String>> pyDict(String src, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + " = \\{(.*?)}", Pattern.DOTALL).matcher(src);
        assertTrue(m.find(), name);
        Map<String, List<String>> d = new LinkedHashMap<>();
        Matcher e = Pattern.compile("\"([a-z_]+)\": (\\[[^]]*]|\"[^\"]*\")").matcher(m.group(1));
        while (e.find()) {
            List<String> v = new ArrayList<>();
            Matcher s = Pattern.compile("\"([^\"]+)\"").matcher(e.group(2));
            while (s.find()) {
                v.add(s.group(1));
            }
            d.put(e.group(1), v);
        }
        return d;
    }

    private static <E extends Enum<E> & Word> List<String> ids(Class<E> c) {
        List<String> l = new ArrayList<>();
        for (E e : c.getEnumConstants()) {
            l.add(e.id());
        }
        return l;
    }

    @Test
    void archetypesAndTheirMobilityMatchTheRosterChecker() throws IOException {
        Map<String, List<String>> arch = pyDict(roster(), "ARCH_MOBILITY");
        assertEquals(54, arch.size());
        assertEquals(List.copyOf(arch.keySet()), HeroCatalogue.ARCHETYPES);
        for (Map.Entry<String, List<String>> e : arch.entrySet()) {
            assertEquals(e.getValue().get(0), HeroCatalogue.ARCH_PRIMARY.get(e.getKey()).id(), e.getKey());
        }
        Map<String, List<String>> carrier = pyDict(roster(), "FLIGHT_CARRIER");
        assertEquals(carrier.keySet(), HeroCatalogue.ARCH_CARRIER.keySet());
        for (Map.Entry<String, List<String>> e : carrier.entrySet()) {
            String kind = HeroCatalogue.ARCH_CARRIER.get(e.getKey()).id();
            String drawn = e.getValue().get(0);
            assertTrue(drawn.contains(kind) || kind.equals("light") && drawn.contains("wings")
                    || kind.equals("crane") && drawn.contains("crane"), e.getKey() + ": " + drawn + " as " + kind);
            assertTrue(arch.get(e.getKey()).contains("flyer"), e.getKey() + " flies");
        }
        List<String> primary = pyList(roster(), "MOBILITY_PRIMARY");
        List<String> traits = new ArrayList<>();
        for (HeroDef.MobilityTrait t : HeroDef.MobilityTrait.values()) {
            if (!t.footing()) {
                traits.add(t.id());
            }
        }
        assertEquals(primary, traits);
        Set<String> airborne = new HashSet<>();
        for (HeroDef.MobilityTrait t : HeroDef.MobilityTrait.values()) {
            if (t.airborne()) {
                airborne.add(t.id());
            }
        }
        assertEquals(Set.of("leaper", "acrobat", "glider", "flyer"), airborne, "check_roster.py AIRBORNE");
    }

    @Test
    void talentsAffixesAndDuelStylesMatchTheRosterChecker() throws IOException {
        assertEquals(pyList(roster(), "TALENTS"), ids(HeroDef.TalentId.class));
        assertEquals(pyList(roster(), "AURA_AFFIXES"), HeroCatalogue.AURA_AFFIXES);
        List<String> duels = new ArrayList<>(pyList(roster(), "DUELS"));
        assertEquals(duels, ids(HeroDef.DuelStyle.class).stream().map(s -> s.replace("-", "_")).toList());
    }

    @Test
    void temperamentTraitsMatchAppendixA() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/HEROES_V2.md"));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Temperament | a | h | m | c | g | s | l | v |")) {
                start = i + 2;
            }
        }
        assertTrue(start > 0);
        int rows = 0;
        for (int i = start; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String[] c = lines.get(i).split("\\|", -1);
            HeroDef.Temperament t = HeroCatalogue.enumOf(HeroDef.Temperament.class, c[1].trim());
            assertNotNull(t, c[1]);
            int[] want = new int[8];
            for (int k = 0; k < 8; k++) {
                want[k] = Integer.parseInt(c[2 + k].trim());
            }
            assertEquals(java.util.Arrays.toString(want), java.util.Arrays.toString(HeroCatalogue.TEMPERAMENT_TRAITS
                    .get(t)), t.id());
            rows++;
        }
        assertEquals(30, rows);
        assertEquals(30, HeroDef.Temperament.values().length);
        assertEquals(List.of("aggression", "honour", "ambition", "caution", "greed", "sociability", "loyalty",
                "volatility"), ids(HeroDef.Trait.class), "the table's column order");
        for (int v : HeroCatalogue.defaultTraits(null).values()) {
            assertEquals(50, v, "no temperament: all 50");
        }
    }

    @Test
    void everyFormatTwoLineEventHasItsPlaceholders() {
        for (HeroDef.LineEvent e : Word.inFormat(HeroDef.LineEvent.class, 2)) {
            List<String> ph = HeroCatalogue.PLACEHOLDERS_2.get(e);
            assertNotNull(ph, e.id());
            for (String p : ph) {
                assertTrue(HeroCatalogue.ALL_PLACEHOLDERS_2.contains(p), e.id() + " {" + p + "}");
            }
            if (ph.contains("enemy")) {
                assertFalse(ph.contains("player"), e.id() + ": a fight line names its enemy, whoever it is");
            }
        }
        assertTrue(HeroCatalogue.PLACEHOLDERS_2.get(HeroDef.LineEvent.GIFT_LIKED).contains("item"));
        assertTrue(HeroCatalogue.PLACEHOLDERS_2.get(HeroDef.LineEvent.FALLEN).contains("enemy"));
        assertTrue(HeroCatalogue.PLACEHOLDERS_2.get(HeroDef.LineEvent.GREETING).contains("player"), "a greeting is"
                + " presentation: it may name the player it greets");
    }

    @Test
    void formatOneWordsAreFrozenAndFormatTwoIsASuperset() {
        Map<String, String> pairs = Map.of("combat_role", "combat_role2", "form", "form2", "effect_type",
                "effect_type2", "condition", "condition2", "trigger", "trigger2", "line_event", "line_event2");
        for (Map.Entry<String, String> p : pairs.entrySet()) {
            HeroCatalogue.Vocab v1 = HeroCatalogue.vocab(p.getKey()), v2 = HeroCatalogue.vocab(p.getValue());
            assertTrue(v2.words.containsAll(v1.words), p.getValue());
            assertTrue(v2.words.size() > v1.words.size(), p.getValue());
            assertEquals(p.getKey(), HeroCatalogue.vocabName(p.getKey(), 1));
            assertEquals(p.getValue(), HeroCatalogue.vocabName(p.getKey(), 2));
        }
        // the v1 synonyms that became words of their own in format 2
        assertEquals("dash", HeroCatalogue.vocab("form").resolve("charge"));
        assertEquals("charge", HeroCatalogue.vocab("form2").resolve("charge"));
        assertEquals("shield", HeroCatalogue.vocab("effect_type").resolve("barrier"));
        assertEquals("barrier", HeroCatalogue.vocab("effect_type2").resolve("barrier"));
        assertEquals(HeroDef.Form.values().length, HeroCatalogue.vocab("form2").words.size());
        for (HeroDef.Form f : HeroDef.Form.values()) {
            assertNotNull(HeroCatalogue.FORMS.get(f), f.id());
            assertTrue(f.since() == 1 || f.since() == 2, f.id());
        }
        assertEquals(9, HeroDef.Form.values().length - Word.inFormat(HeroDef.Form.class, 1).size(),
                "8 new forms of HEROES_V2 6.5 and the dive of MOBILITY_AND_WORK 7.3");
        assertEquals(7, HeroDef.EffectType.values().length - Word.inFormat(HeroDef.EffectType.class, 1).size(),
                "7 new effects");
        // no synonym hides a word of its own list
        for (HeroCatalogue.Vocab v : HeroCatalogue.vocabs().values()) {
            for (String syn : v.synonyms.keySet()) {
                assertFalse(v.words.contains(syn), v.name + ": " + syn);
                assertTrue(v.words.contains(v.synonyms.get(syn)), v.name + ": " + syn);
            }
        }
    }

    @Test
    void bannerPatternsAreVanillas() {
        assertEquals(42, HeroCatalogue.BANNER_PATTERNS.size());
        assertTrue(HeroCatalogue.BANNER_PATTERNS.contains("globe") && HeroCatalogue.BANNER_PATTERNS.contains("flow")
                && HeroCatalogue.BANNER_PATTERNS.contains("guster"));
        assertTrue(HeroCatalogue.BANNER_PATTERNS.contains(HeroCatalogue.REFUSED_PATTERN), "a vanilla pattern, which the"
                + " reader names to refuse it (a trademark)");
        assertEquals(16, HeroDef.Dye.values().length);
    }

    @Test
    void soundIdForms() {
        assertTrue(HeroCatalogue.soundIdForm("entity.cat.hiss"));
        assertTrue(HeroCatalogue.soundIdForm("factionclash:hero.kit.fire_burst"));
        assertFalse(HeroCatalogue.soundIdForm("factionclash:other.thing"), "the mod's own sounds are hero.*");
        assertFalse(HeroCatalogue.soundIdForm("UPPER.case"));
    }
}
