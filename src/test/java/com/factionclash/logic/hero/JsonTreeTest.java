package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTreeTest {
    @Test
    void readsTheGeneratorsShapes() {
        Object v = JsonTree.parse("{\"a\": [1, 2.5, -3e1], \"b\": {\"c\": \"x\\\"y\\u0041\"}, \"t\": true, \"n\": null, \"f\": false}");
        Map<String, Object> o = JsonTree.object(v);
        assertEquals(List.of(1.0, 2.5, -30.0), o.get("a"));
        assertEquals("x\"yA", JsonTree.object(o.get("b")).get("c"));
        assertEquals(Boolean.TRUE, o.get("t"));
        assertNull(o.get("n"));
        assertEquals(List.of("a", "b", "t", "n", "f"), List.copyOf(o.keySet()), "file order");
        assertEquals(1, JsonTree.integer(Map.of("k", 1.0), "k"));
        assertEquals(-1, JsonTree.integer(Map.of(), "k"));
        assertEquals(Map.of(), JsonTree.object(JsonTree.parse("[]")));
    }

    @Test
    void syntaxErrorsThrow() {
        assertThrows(IllegalArgumentException.class, () -> JsonTree.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> JsonTree.parse("[1, 2"));
        assertThrows(IllegalArgumentException.class, () -> JsonTree.parse("{} x"));
    }
}
