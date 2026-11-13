package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Source;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HeroSchemaGen}: the committed {@code docs/heroes-v2/hero2.schema.json} is what the generator writes (rewrite
 * it with {@code ./gradlew heroSchema}), its word lists are the catalogue's, and the eight slice fixtures validate
 * against it while broken copies do not. The validation here is a small subset of JSON Schema 2020-12 (the keywords
 * the generator writes); the file was also checked with Python's {@code jsonschema} (Draft202012Validator).
 */
class HeroSchemaGenTest {
    private static final Path GOLDEN = Path.of("docs/heroes-v2/hero2.schema.json");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema() {
        HeroJson.Result r = HeroJson.parse(HeroSchemaGen.schema2(), 1 << 20, 64, 1 << 16);
        assertTrue(r.error() == null, String.valueOf(r.error()));
        return (Map<String, Object>) HeroReaderRobustnessTest.model(r.root());
    }

    private static Object json(String text) {
        HeroJson.Result r = HeroJson.parse(text);
        assertTrue(r.error() == null, String.valueOf(r.error()));
        return HeroReaderRobustnessTest.model(r.root());
    }

    @Test
    void theCommittedSchemaIsTheGeneratedOne() throws IOException {
        String golden = Files.readString(GOLDEN);
        assertEquals(HeroSchemaGen.schema2(), golden, "docs/heroes-v2/hero2.schema.json is stale: run ./gradlew"
                + " heroSchema");
        assertEquals(HeroSchemaGen.schema2(), HeroSchemaGen.schema2(), "the same every time");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theWordListsAreTheCatalogues() {
        Map<String, Object> s = schema();
        assertEquals("https://json-schema.org/draft/2020-12/schema", s.get("$schema"));
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        assertEquals(HeroCatalogue.TOP_2.keySet(), props.keySet());
        assertEquals(Map.of("const", 2.0), props.get("format"));
        Map<String, Object> defs = (Map<String, Object>) s.get("$defs");
        Map<String, Object> move = (Map<String, Object>) ((Map<String, Object>) defs.get("move")).get("properties");
        assertEquals(HeroCatalogue.MOVE.keySet(), move.keySet());
        assertEquals(HeroCatalogue.vocab("form2").words, ((Map<String, Object>) move.get("form")).get("enum"));
        Map<String, Object> effect = (Map<String, Object>) defs.get("effect");
        Map<String, Object> type = (Map<String, Object>) ((Map<String, Object>) effect.get("properties")).get("type");
        assertEquals(HeroCatalogue.vocab("effect_type2").words, type.get("enum"));
        assertEquals(HeroDef.EffectType.values().length, ((List<Object>) effect.get("allOf")).size(), "a case per"
                + " effect type");
        Map<String, Object> lines = (Map<String, Object>) ((Map<String, Object>) defs.get("lines")).get("properties");
        assertEquals(HeroCatalogue.vocab("line_event2").words, List.copyOf(lines.keySet()));
        // no word of the schema singles out players, yields or holds a token
        List<String> words = new ArrayList<>();
        collectEnums(s, words);
        assertTrue(words.size() > 300, "enums: " + words.size());
        for (String w : words) {
            assertFalse(Set.of("player", "players", "npc", "yield", "spared", "token", "tokens", "target_is_player",
                    "players_engaged", "on_yield").contains(w), w);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectEnums(Object n, List<String> out) {
        if (n instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if ("enum".equals(e.getKey())) {
                    for (Object w : (List<Object>) e.getValue()) {
                        out.add(String.valueOf(w));
                    }
                } else {
                    collectEnums(e.getValue(), out);
                }
            }
        } else if (n instanceof List<?> l) {
            for (Object x : l) {
                collectEnums(x, out);
            }
        }
    }

    @Test
    void theSliceFixturesValidateAndBrokenCopiesDoNot() throws IOException {
        Map<String, Object> s = schema();
        for (Source src : SliceKitsTest.sources()) {
            List<String> errors = new ArrayList<>();
            new Mini(s).check(json(src.text()), s, "", errors);
            assertTrue(errors.isEmpty(), src.path() + ": " + errors);
        }
        String natsuho = Files.readString(Path.of("src/test/resources/heroes/slice/natsuho_komezaki.json"));
        Map<String, Consumer<Map<String, Object>>> broken = Map.of(
                "unknown field", d -> d.put("powers", 3.0),
                "format 1's starter_for", d -> d.put("starter_for", "stoneguard"),
                "a yield line", d -> lines(d).put("yield", List.of("Mercy")),
                "a removed boss field", d -> map(d, "boss").put("damage_vs_players", 50.0),
                "a bad form", d -> moves(d).get(0).put("form", "teleport"),
                "weight 11", d -> moves(d).get(0).put("weight", 11.0),
                "target_is_player", d -> moves(d).get(0).put("prefer_when", "target_is_player"),
                "a bad sound id", d -> moves(d).get(2).put("look", new java.util.LinkedHashMap<>(Map.of("sound",
                        "othermod:boom"))),
                "another type's field", d -> effects(moves(d).get(1)).get(0).put("seconds", 3.0),
                "format 3", d -> d.put("format", 3.0));
        for (Map.Entry<String, Consumer<Map<String, Object>>> b : broken.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) json(natsuho);
            b.getValue().accept(d);
            List<String> errors = new ArrayList<>();
            new Mini(s).check(d, s, "", errors);
            assertFalse(errors.isEmpty(), b.getKey());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ok = (Map<String, Object>) json(natsuho);
        moves(ok).get(0).put("_note", "a comment");
        moves(ok).get(0).put("unlock_level", 5.0);
        moves(ok).get(0).put("band", "any");
        List<String> errors = new ArrayList<>();
        new Mini(s).check(ok, s, "", errors);
        assertTrue(errors.isEmpty(), "comments, a deprecated field and \"any\" are allowed: " + errors);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> d, String k) {
        return (Map<String, Object>) d.get(k);
    }

    private static Map<String, Object> lines(Map<String, Object> d) {
        return map(d, "lines");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> moves(Map<String, Object> d) {
        return (List<Map<String, Object>>) d.get("moves");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> effects(Map<String, Object> m) {
        return (List<Map<String, Object>>) m.get("effects");
    }

    /** The keywords {@link HeroSchemaGen} writes, checked as JSON Schema 2020-12 defines them. */
    private record Mini(Map<String, Object> root) {
        @SuppressWarnings("unchecked")
        void check(Object v, Object schemaNode, String at, List<String> err) {
            if (!(schemaNode instanceof Map<?, ?> raw)) {
                return;
            }
            Map<String, Object> s = (Map<String, Object>) raw;
            if (s.containsKey("$ref")) {
                String ref = (String) s.get("$ref");
                assertTrue(ref.startsWith("#/$defs/"), ref);
                Object target = ((Map<String, Object>) root.get("$defs")).get(ref.substring(8));
                assertNotNull(target, ref);
                check(v, target, at, err);
            }
            if (s.containsKey("const") && !same(s.get("const"), v)) {
                err.add(at + ": not " + s.get("const"));
            }
            if (s.containsKey("enum") && ((List<Object>) s.get("enum")).stream().noneMatch(x -> same(x, v))) {
                err.add(at + ": " + v + " is not in the list");
            }
            if (s.containsKey("not")) {
                List<String> e = new ArrayList<>();
                check(v, s.get("not"), at, e);
                if (e.isEmpty()) {
                    err.add(at + ": not allowed");
                }
            }
            if (s.containsKey("type") && !typeOk((String) s.get("type"), v)) {
                err.add(at + ": not a " + s.get("type"));
                return;
            }
            if (v instanceof Double d) {
                if (s.containsKey("minimum") && d < (Double) s.get("minimum")) {
                    err.add(at + ": under " + s.get("minimum"));
                }
                if (s.containsKey("maximum") && d > (Double) s.get("maximum")) {
                    err.add(at + ": over " + s.get("maximum"));
                }
            }
            if (v instanceof String str) {
                if (s.containsKey("pattern") && !Pattern.compile((String) s.get("pattern")).matcher(str).find()) {
                    err.add(at + ": " + str + " does not match");
                }
                if (s.containsKey("minLength") && str.length() < (Double) s.get("minLength")) {
                    err.add(at + ": too short");
                }
                if (s.containsKey("maxLength") && str.length() > (Double) s.get("maxLength")) {
                    err.add(at + ": too long");
                }
            }
            if (v instanceof List<?> l) {
                if (s.containsKey("minItems") && l.size() < (Double) s.get("minItems")) {
                    err.add(at + ": too few items");
                }
                if (s.containsKey("maxItems") && l.size() > (Double) s.get("maxItems")) {
                    err.add(at + ": too many items");
                }
                for (int i = 0; i < l.size() && s.containsKey("items"); i++) {
                    check(l.get(i), s.get("items"), at + "/" + i, err);
                }
            }
            if (v instanceof Map<?, ?> om) {
                Map<String, Object> o = (Map<String, Object>) om;
                for (Object r : (List<Object>) s.getOrDefault("required", List.of())) {
                    if (!o.containsKey(r)) {
                        err.add(at + ": " + r + " is missing");
                    }
                }
                Map<String, Object> props = (Map<String, Object>) s.getOrDefault("properties", Map.of());
                Map<String, Object> pp = (Map<String, Object>) s.getOrDefault("patternProperties", Map.of());
                for (Map.Entry<String, Object> e : o.entrySet()) {
                    boolean matched = props.containsKey(e.getKey());
                    if (matched) {
                        check(e.getValue(), props.get(e.getKey()), at + "/" + e.getKey(), err);
                    }
                    for (String p : pp.keySet()) {
                        if (Pattern.compile(p).matcher(e.getKey()).find()) {
                            matched = true;
                        }
                    }
                    Object ap = s.get("additionalProperties");
                    if (!matched && Boolean.FALSE.equals(ap)) {
                        err.add(at + ": " + e.getKey() + " is not a field");
                    } else if (!matched && ap instanceof Map<?, ?>) {
                        check(e.getValue(), ap, at + "/" + e.getKey(), err);
                    }
                    if (s.containsKey("propertyNames")) {
                        check(e.getKey(), s.get("propertyNames"), at + "/" + e.getKey() + " (name)", err);
                    }
                }
                if (s.containsKey("minProperties") && o.size() < (Double) s.get("minProperties")) {
                    err.add(at + ": empty");
                }
                if (s.containsKey("maxProperties") && o.size() > (Double) s.get("maxProperties")) {
                    err.add(at + ": too many fields");
                }
                Map<String, Object> dr = (Map<String, Object>) s.getOrDefault("dependentRequired", Map.of());
                for (Map.Entry<String, Object> e : dr.entrySet()) {
                    if (o.containsKey(e.getKey())) {
                        for (Object r : (List<Object>) e.getValue()) {
                            if (!o.containsKey(r)) {
                                err.add(at + ": " + e.getKey() + " needs " + r);
                            }
                        }
                    }
                }
            }
            if (s.containsKey("oneOf")) {
                int ok = 0;
                for (Object opt : (List<Object>) s.get("oneOf")) {
                    List<String> e = new ArrayList<>();
                    check(v, opt, at, e);
                    ok += e.isEmpty() ? 1 : 0;
                }
                if (ok != 1) {
                    err.add(at + ": matches " + ok + " of oneOf");
                }
            }
            if (s.containsKey("anyOf")) {
                boolean any = false;
                for (Object opt : (List<Object>) s.get("anyOf")) {
                    List<String> e = new ArrayList<>();
                    check(v, opt, at, e);
                    any |= e.isEmpty();
                }
                if (!any) {
                    err.add(at + ": matches none of anyOf");
                }
            }
            for (Object part : (List<Object>) s.getOrDefault("allOf", List.of())) {
                Map<String, Object> c = (Map<String, Object>) part;
                if (c.containsKey("if")) {
                    List<String> e = new ArrayList<>();
                    check(v, c.get("if"), at, e);
                    if (e.isEmpty()) {
                        check(v, c.get("then"), at, err);
                    }
                } else {
                    check(v, c, at, err);
                }
            }
        }

        private static boolean same(Object a, Object b) {
            return a == null ? b == null : a.equals(b) || a instanceof Number x && b instanceof Number y
                    && x.doubleValue() == y.doubleValue();
        }

        private static boolean typeOk(String type, Object v) {
            return switch (type) {
                case "object" -> v instanceof Map;
                case "array" -> v instanceof List;
                case "string" -> v instanceof String;
                case "boolean" -> v instanceof Boolean;
                case "number" -> v instanceof Double;
                case "integer" -> v instanceof Double d && d == Math.rint(d);
                default -> throw new AssertionError("type " + type);
            };
        }
    }
}
