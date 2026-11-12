package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for the pure rules ({@code logic/} runs in unit tests without Minecraft's libraries): objects
 * become {@code Map<String, Object>} in file order, arrays {@code List<Object>}, numbers {@code Double}, plus
 * {@code String}, {@code Boolean} and {@code null}. Strict enough for the generator's files; a syntax error throws
 * {@link IllegalArgumentException} with the offset.
 */
public final class JsonTree {
    private final String s;
    private int i;

    private JsonTree(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        JsonTree p = new JsonTree(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw p.error("trailing text");
        }
        return v;
    }

    /** The object at the root, or an empty map when the root is something else. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object v) {
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    public static int integer(Map<String, Object> o, String key) {
        return o.get(key) instanceof Number n ? (int) Math.round(n.doubleValue()) : -1;
    }

    public static String string(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v instanceof String str ? str : v == null ? "" : String.valueOf(v);
    }

    private Object value() {
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> obj();
            case '[' -> arr();
            case '"' -> str();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> num();
        };
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw error("expected a key");
            }
            String k = str();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            char c = next();
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = next();
            if (c == ']') {
                return l;
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String str() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return b.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("bad escape");
                        }
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Double num() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw error("unexpected character");
        }
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) {
            throw error("unexpected word");
        }
        i += w.length();
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected " + c);
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON: " + what + " at offset " + i);
    }
}
