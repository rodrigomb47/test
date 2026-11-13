package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An operator's override merged over a built-in hero (HEROES_V2 12.1, task A1b). */
class HeroOverrideTest {
    private static final String FILE = "config/factionclash/heroes/test_hero.json";
    private static final Source BUILT_IN = new Source("test_hero.json", V2Files.hero2("\"title\": \"the Original\""));

    private static HeroOverride.Merged merge(String text) {
        return HeroOverride.merge(BUILT_IN, new Source(FILE, text));
    }

    private static Loaded read(HeroOverride.Merged m) {
        return HeroReader.readAll(List.of(m.source()), Options.DEFAULT);
    }

    @Test
    void itsTopLevelFieldsReplaceTheBuiltInOnesWhole() {
        HeroOverride.Merged m = merge("{\"name\": \"Renamed\", \"title\": \"the Changed\", \"stats\": {\"health\": 30}}");
        assertEquals(List.of("name", "title", "stats"), m.fields());
        assertTrue(m.problems().isEmpty(), () -> m.problems().toString());
        Loaded l = read(m);
        HeroDef d = l.hero("test_hero");
        assertNotNull(d, l.problems().report());
        assertEquals("Renamed", d.name());
        assertEquals("the Changed", d.title());
        assertEquals(30, d.stats().health(), 1e-9, "a new field is added");
        assertEquals("striker", d.behaviour().role().id(), "fields it does not name stay the built-in's");
        assertTrue(l.problems().all().isEmpty(), l.problems().report());
    }

    @Test
    void aReplacedBlockIsReplacedAsAWholeNotMerged() {
        // the built-in boss has a weak point and one phase; the override's boss has only a poise: no weak point left
        Loaded l = read(merge("{\"boss\": {\"poise\": 120}}"));
        HeroDef d = l.hero("test_hero");
        assertEquals(120, d.v2().boss().poise());
        assertTrue(d.v2().boss().phases().size() > 1, "the rarity's default phases, not the built-in's one");
    }

    @Test
    void idArtAnimationsAndFormatStayTheBuiltInsWithANote() {
        HeroOverride.Merged m = merge("{\"id\": \"other\", \"art\": {}, \"animations\": {}, \"format\": 1, \"name\": \"N\"}");
        assertEquals(List.of("name"), m.fields());
        assertEquals(4, m.problems().stream().filter(p -> p.code().equals(Code.OVERRIDE_FIXED.id())).count());
        for (Problem p : m.problems()) {
            assertEquals(Severity.NOTE, p.severity());
            assertEquals(FILE, p.file());
            assertTrue(p.line() > 0, "the override's own line");
        }
        HeroDef d = read(m).hero("test_hero");
        assertEquals(2, d.format(), "still read as format 2");
        assertEquals("test_hero", d.id());
    }

    @Test
    void commentsAreSkippedAndTheJsonReadersNotesNameTheOverride() {
        HeroOverride.Merged m = merge("// my change\n{\"_why\": \"a test\", \"name\": \"N\",}");
        assertEquals(List.of("name"), m.fields(), "an _ key is a comment");
        assertTrue(m.problems().stream().anyMatch(p -> p.code().equals("json.comments") && p.file().equals(FILE)));
        assertTrue(m.problems().stream().anyMatch(p -> p.code().equals("json.trailing_comma") && p.file().equals(FILE)));
    }

    @Test
    void anUnreadableOverrideIsLeftOutAndTheBuiltInLoadsAsItIs() {
        HeroOverride.Merged m = merge("{\"name\": \"A\" \"title\": \"B\"}");
        assertSame(BUILT_IN, m.source());
        assertTrue(m.fields().isEmpty());
        Problem p = m.problems().get(0);
        assertEquals(Code.OVERRIDE_UNREADABLE.id(), p.code());
        assertEquals(Severity.PART_LEFT_OUT, p.severity());
        assertEquals(1, p.line());
        assertTrue(p.message().contains("The built-in hero loads as it is"), p.message());
        HeroOverride.Merged list = merge("[{\"name\": \"A\"}]");
        assertSame(BUILT_IN, list.source());
        assertTrue(list.problems().get(0).message().contains("not a list"), list.problems().get(0).message());
        assertEquals("the Original", read(m).hero("test_hero").title());
    }

    @Test
    void anOverrideWithNothingToApplyLeavesTheBuiltInUntouched() {
        assertSame(BUILT_IN, merge("{}").source());
        assertSame(BUILT_IN, merge("{\"_note\": \"nothing\"}").source());
        assertSame(BUILT_IN, merge("{\"id\": \"x\"}").source());
    }

    @Test
    void aBrokenBuiltInWaitsWithoutAMessageOfItsOwn() {
        Source broken = new Source("test_hero.json", "{\"format\": 2,");
        HeroOverride.Merged m = HeroOverride.merge(broken, new Source(FILE, "{\"name\": \"N\"}"));
        assertSame(broken, m.source());
        assertTrue(m.problems().isEmpty(), "the built-in's own NOT LOADED says it");
    }

    @Test
    void eachProblemNamesTheFileItsFieldCameFromWithItsOwnLine() {
        // a bad value in a field the override wrote (line 3 of the override) and one in the built-in's own fields
        String built = V2Files.hero2("\"element\": \"wind\"");
        HeroOverride.Merged m = HeroOverride.merge(new Source("test_hero.json", built),
                new Source(FILE, "{\n  \"name\": \"N\",\n  \"stats\": {\"speed\": 150}\n}"));
        Loaded l = read(m);
        Problem speed = l.problems().all().stream().filter(p -> p.machinePath().equals("stats.speed")).findFirst().orElseThrow();
        assertEquals(FILE, speed.file());
        assertEquals(3, speed.line());
        Problem element = l.problems().all().stream().filter(p -> p.machinePath().equals("element")).findFirst().orElseThrow();
        assertEquals("test_hero.json", element.file());
        assertEquals("test_hero.json", m.source().fileOf("moves[0].effects[0]"));
        assertEquals(FILE, m.source().fileOf("stats"));
        assertEquals("test_hero.json", m.source().fileOf(""));
        assertEquals("test_hero.json", m.source().fileOf(null));
    }

    @Test
    void anOverrideCanBreakTheHeroAndTheReportSaysWhere() {
        Loaded l = read(merge("{\n\"behaviour\": {\"combat_role\": \"wizard\"}\n}"));
        assertNull(l.hero("test_hero"), "an override that removes the core leaves the hero not loaded (dormant in game)");
        Problem p = l.problems().all().stream().filter(x -> x.severity() == Severity.NOT_LOADED).findFirst().orElseThrow();
        assertEquals(FILE, p.file());
        assertEquals(2, p.line());
    }
}
