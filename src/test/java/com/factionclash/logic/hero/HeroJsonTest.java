package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroJson.Code;
import com.factionclash.logic.hero.HeroJson.Issue;
import com.factionclash.logic.hero.HeroJson.Obj;
import com.factionclash.logic.hero.HeroJson.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HERO_FORMAT.md 31.1 {@code HeroJsonTest}: positions, what is forgiven, every syntax error class, the limits. */
class HeroJsonTest {
    private static Result ok(String s) {
        Result r = HeroJson.parse(s);
        assertTrue(r.ok(), () -> "should parse: " + r.error());
        return r;
    }

    private static Issue err(String s, Code code, int line, int col) {
        Result r = HeroJson.parse(s);
        assertFalse(r.ok(), "should fail: " + s);
        assertEquals(code, r.error().code(), r.error().message());
        assertEquals(line, r.error().line(), "line of " + r.error());
        assertEquals(col, r.error().col(), "column of " + r.error());
        assertNull(r.root());
        return r.error();
    }

    private static boolean noted(Result r, Code c) {
        return r.notes().stream().anyMatch(n -> n.code() == c);
    }

    @Test
    void keysAndValuesKeepTheirLineAndColumn() {
        Result r = ok("{\n  \"name\": \"Bram\",\n  \"stats\": { \"health\": 36 },\n  \"gear\": [\"shield\", true, null]\n}");
        Obj o = (Obj) r.root();
        assertEquals(1, o.line());
        assertEquals(1, o.col());
        HeroJson.Member name = o.member("name");
        assertEquals(2, name.line());
        assertEquals(3, name.col());
        assertEquals(11, name.value().col());
        assertEquals("Bram", ((HeroJson.Str) name.value()).value());
        Obj stats = (Obj) o.get("stats");
        assertEquals(3, stats.line());
        assertEquals(12, stats.col());
        HeroJson.Num h = (HeroJson.Num) stats.get("health");
        assertEquals(36, h.value());
        assertEquals("36", h.text());
        assertTrue(h.integral());
        HeroJson.Arr gear = (HeroJson.Arr) o.get("gear");
        assertEquals(3, gear.items().size());
        assertEquals(4, gear.items().get(1).line());
        assertInstanceOf(HeroJson.Bool.class, gear.items().get(1));
        assertInstanceOf(HeroJson.Null.class, gear.items().get(2));
        assertTrue(r.notes().isEmpty());
        assertEquals(List.of("name", "stats", "gear"), o.members().stream().map(HeroJson.Member::key).toList());
    }

    @Test
    void numbersAndEscapesReadAsJsonSays() {
        Obj o = (Obj) ok("{\"a\": -1.5e2, \"b\": 0.25, \"c\": \"x\\\"y\\\\z\\n\\u00e9\\/\", \"d\": 1.0}").root();
        assertEquals(-150, ((HeroJson.Num) o.get("a")).value());
        assertEquals(0.25, ((HeroJson.Num) o.get("b")).value());
        assertEquals("x\"y\\z\n\u00e9/", ((HeroJson.Str) o.get("c")).value());
        assertEquals("1.0", ((HeroJson.Num) o.get("d")).text());
        assertTrue(((HeroJson.Num) o.get("d")).integral());
        assertFalse(((HeroJson.Num) o.get("b")).integral());
        assertInstanceOf(HeroJson.Arr.class, ok("[{\"id\": \"a\"}, {\"id\": \"b\"}]").root(), "a list of heroes");
    }

    @Test
    void aByteOrderMarkIsIgnoredWithANote() {
        Result r = ok("\uFEFF{\"a\": 1}");
        assertTrue(noted(r, Code.BOM));
        assertEquals(1, ((Obj) r.root()).col());
    }

    @Test
    void windowsLineEndsKeepTheLineNumbers() {
        Result r = ok("{\r\n  \"a\": 1,\r\n  \"b\": 2\r\n}\r\n");
        assertTrue(noted(r, Code.CRLF));
        assertEquals(3, ((Obj) r.root()).member("b").line());
        assertEquals(3, ((Obj) r.root()).member("b").col());
        Issue e = err("{\r\n  \"a\": 1\r\n  \"b\": 2\r\n}", Code.MISSING_COMMA, 3, 3);
        assertTrue(e.message().contains("\"b\""));
    }

    @Test
    void aCodeFenceIsRemovedAndPositionsStayThoseOfTheFile() {
        Result r = ok("```json\n{\n  \"a\": 1\n}\n```\n");
        assertTrue(noted(r, Code.CODE_FENCE));
        assertFalse(noted(r, Code.CODE_BLOCK));
        assertEquals(3, ((Obj) r.root()).member("a").line());
        Result chat = ok("Sure! Here is your hero:\n\n```json\n{\"a\": 1,\n \"b\": 2}\n```\nEnjoy, and tell me if...");
        assertTrue(noted(chat, Code.CODE_BLOCK), "a pasted chat answer: the first block is read");
        assertEquals(5, ((Obj) chat.root()).member("b").line());
        assertEquals(2, ((Obj) chat.root()).member("b").col());
        Result unclosed = ok("```\n{\"a\": 1}\n");
        assertTrue(noted(unclosed, Code.CODE_FENCE), "an unclosed fence reads to the end");
    }

    @Test
    void commentsAndTrailingCommasAreForgivenOnceEach() {
        Result r = ok("// my hero\n{\n  \"a\": 1, /* two */\n  \"b\": [1, 2,],\n  \"c\": {\"d\": 1,},\n}");
        Issue c = r.notes().stream().filter(n -> n.code() == Code.COMMENTS).findFirst().orElseThrow();
        assertEquals(1, c.line());
        assertTrue(c.message().contains("2 comments"));
        Issue t = r.notes().stream().filter(n -> n.code() == Code.TRAILING_COMMA).findFirst().orElseThrow();
        assertEquals(4, t.line());
        assertTrue(t.message().contains("3 commas"), t.message());
        assertEquals(2, r.notes().size());
        assertEquals(2, ((HeroJson.Arr) ((Obj) r.root()).get("b")).items().size());
        Result one = ok("{\"a\": 1 // end\n}");
        assertEquals("a comment was ignored", one.notes().get(0).message());
    }

    @Test
    void eachSyntaxErrorHasItsPlaceAndAPlainMessage() {
        assertTrue(err("{\n  \"name\": \"Sela\"\n  \"stats\": {}\n}", Code.MISSING_COMMA, 3, 3).message()
                .startsWith("a comma is missing before \"stats\". In JSON, fields are separated by commas"));
        assertTrue(err("[1 2]", Code.MISSING_COMMA, 1, 4).message().contains("list"));
        err("{\"a\": 1 5}", Code.MISSING_COMMA, 1, 9);
        assertTrue(err("{\"a\" 1}", Code.MISSING_COLON, 1, 6).message().contains("colon is missing after \"a\""));
        assertTrue(err("{name: 1}", Code.KEY_QUOTES, 1, 2).message().contains("\"name\""));
        assertTrue(err("{'name': 1}", Code.SINGLE_QUOTES, 1, 2).message().contains("double quotes"));
        err("{\"a\": 'x'}", Code.SINGLE_QUOTES, 1, 7);
        assertTrue(err("{\"a\": \u201Cx\u201D}", Code.CURLY_QUOTES, 1, 7).message().contains("straight"));
        err("{\u201Ca\u201D: 1}", Code.CURLY_QUOTES, 1, 2);
        assertTrue(err("{\"a\": \"x\n}", Code.UNCLOSED_STRING, 1, 7).message().contains("closing \""));
        err("{\"a\": \"x", Code.UNCLOSED_STRING, 1, 7);
        assertTrue(err("{\"a\": \"x\\q\"}", Code.BAD_ESCAPE, 1, 9).message().contains("\\q"));
        err("{\"a\": \"\\u12G4\"}", Code.BAD_ESCAPE, 1, 8);
        assertTrue(err("{\"a\": fire}", Code.UNQUOTED_TEXT, 1, 7).message().contains("\"fire\""));
        assertTrue(err("{\"a\": True}", Code.UNQUOTED_TEXT, 1, 7).message().contains("lower case"));
        err("{\"a\": #}", Code.UNQUOTED_TEXT, 1, 7);
        err("{,}", Code.UNQUOTED_TEXT, 1, 2);
        assertTrue(err("{\"a\": .5}", Code.BAD_NUMBER, 1, 7).message().contains("0.5"));
        assertTrue(err("{\"a\": +5}", Code.BAD_NUMBER, 1, 7).message().contains("+"));
        assertTrue(err("{\"a\": 05}", Code.BAD_NUMBER, 1, 7).message().contains("start with 0"));
        assertTrue(err("{\"a\": 5.}", Code.BAD_NUMBER, 1, 7).message().contains("digit after"));
        assertTrue(err("{\"a\": 1e999}", Code.BAD_NUMBER, 1, 7).message().contains("too large"));
        assertTrue(err("{\n\"a\": [1, 2\n", Code.UNCLOSED, 2, 6).message().contains("never closed"));
        err("{\"a\": 1", Code.UNCLOSED, 1, 1);
        assertTrue(err("{\"a\": [1, 2}", Code.WRONG_CLOSE, 1, 12).message().contains("opened at line 1"));
        err("[1, }", Code.WRONG_CLOSE, 1, 5);
        err("{\"a\": 1]", Code.WRONG_CLOSE, 1, 8);
        assertTrue(err("{\"a\": 1} {\"b\": 2}", Code.TRAILING_TEXT, 1, 10).message().contains("[ {...}, {...} ]"));
        Issue dup = err("{\n\"a\": 1,\n\"a\": 2}", Code.DUPLICATE_KEY, 3, 1);
        assertTrue(dup.message().contains("first at line 2"));
        assertTrue(ok("{\"_c\": 1, \"_c\": 2}").ok(), "comment keys may repeat");
        err("{\"a\": /* x", Code.UNCLOSED_COMMENT, 1, 7);
        err("{\"a\": }", Code.MISSING_VALUE, 1, 7);
        err("{\"a\":", Code.MISSING_VALUE, 1, 6);
    }

    @Test
    void notJsonAtAll() {
        assertTrue(err("name: Bram\nformat: 1\n", Code.YAML, 1, 1).message().contains("YAML"));
        err("---\nname: x", Code.YAML, 1, 1);
        assertTrue(err("Here is the hero you asked for.", Code.NOT_JSON, 1, 1).message().startsWith("a hero file"
                + " starts with {"));
        err("", Code.EMPTY, 1, 1);
        err("  \n\t\n", Code.EMPTY, 1, 1);
    }

    @Test
    void sizeDepthAndStringLimits() {
        String big = "{\"a\": \"" + "x".repeat(900) + "\"}";
        Result r = HeroJson.parse(big, 800, 8, 1000);
        assertEquals(Code.TOO_BIG, r.error().code());
        assertTrue(r.error().message().contains("at most 1 KB"), r.error().message());
        assertTrue(HeroJson.parse(big, 1000, 8, 1000).ok(), "exactly under the limit");
        assertEquals(1001, HeroJson.utf8Length("{\"a\": \"" + "x".repeat(990) + "\u00e9\"}"), "UTF-8 counts bytes");
        assertEquals(4, HeroJson.utf8Length("\uD83D\uDE00"), "a surrogate pair is 4 bytes");
        ok("[[[[[[[[1]]]]]]]]");
        err("[[[[[[[[[1]]]]]]]]]", Code.TOO_DEEP, 1, 9);
        ok("{\"a\": {\"b\": {\"c\": {\"d\": {\"e\": {\"f\": {\"g\": {\"h\": 1}}}}}}}}");
        err("{\"a\": {\"b\": {\"c\": {\"d\": {\"e\": {\"f\": {\"g\": {\"h\": {\"i\": 1}}}}}}}}}", Code.TOO_DEEP, 1, 49);
        ok("{\"a\": \"" + "x".repeat(1000) + "\"}");
        err("{\"a\": \"" + "x".repeat(1001) + "\"}", Code.LONG_STRING, 1, 7);
        assertEquals(HeroCatalogue.MAX_FILE_BYTES, 131072);
        assertEquals(Code.TOO_BIG, HeroJson.parse("[" + "1,".repeat(70000) + "1]").error().code());
    }
}
