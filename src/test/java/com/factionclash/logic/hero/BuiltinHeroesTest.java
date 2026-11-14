package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HEROES_V2 14.3 (and SLICE_PLAN 7): every hero the mod ships ({@code data/factionclash/heroes/*.json}) loads as the
 * game loads it ({@link HeroAssembly}, built-in heroes, no overrides) with no NOT LOADED and no PART LEFT OUT; ids are
 * unique and are their file names; the roster never holds more than 100 heroes of one gender (100 and 100 when it is
 * complete). Until C1-C4 ship the slice's definitions, the slice's format-2 fixtures stand in for the ones not shipped
 * yet: shipped and slice together load clean, with unique ids, four women and four men.
 */
class BuiltinHeroesTest {
    static final Path DIR = Path.of(HeroCheckMain.DEFAULT_DIR);
    static final Path SLICE = Path.of(HeroAssetCheck.SLICE_DIR);
    /** ROSTER_PLAN: 200 heroes, 100 women and 100 men. */
    static final int ROSTER = 200;

    static List<Source> read(Path dir) throws IOException {
        List<Source> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".json")).sorted().toList()) {
                out.add(new Source("data/factionclash/heroes/" + p.getFileName(), Files.readString(p,
                        StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    static Loaded assemble(List<Source> definitions) {
        Set<String> shipped = new HashSet<>();
        for (Source s : definitions) {
            shipped.add(HeroReader.idFromFileName(s.path()));
        }
        return HeroAssembly.assemble(new HeroAssembly.Input(definitions, shipped, List.of(), List.of(),
                HeroReader.Options.BUILTIN)).loaded();
    }

    static void assertLoadsClean(List<Source> definitions, Loaded l) {
        String report = l.problems().report();
        assertEquals(List.of(), l.notLoaded(), report);
        for (Problem p : l.problems().all()) {
            assertFalse(p.severity() == Severity.NOT_LOADED || p.severity() == Severity.PART_LEFT_OUT, p.log());
        }
        assertEquals(definitions.size(), l.heroes().size(), report);
    }

    /** The id each file declares, by file: unique, and the file's own name (v1 2.1). */
    static void assertIdsUnique(List<Source> definitions, Loaded l) {
        Map<String, String> byId = new TreeMap<>();
        for (Source s : definitions) {
            String fileId = HeroReader.idFromFileName(s.path());
            assertTrue(HeroIdentity.isHeroId(fileId), s.path() + ": not a hero id");
            assertNotNull(l.hero(fileId), s.path() + ": no hero \"" + fileId + "\" loaded from it");
            String before = byId.put(l.hero(fileId).id(), s.path());
            assertEquals(null, before, "the id \"" + fileId + "\" is used by " + before + " and " + s.path());
            assertEquals(fileId, l.hero(fileId).id(), s.path());
        }
        for (Problem p : l.problems().all()) {
            assertFalse(p.code().equals(Problems.Code.ID_MISMATCH.id())
                            || p.code().equals(Problems.Code.ID_MISSING.id()),
                    "a shipped hero's id is its file name: " + p.log());
        }
    }

    /** At most 100 of each gender; when the roster is complete, exactly 100 and 100. */
    static int[] genders(Loaded l) {
        int f = 0, m = 0;
        for (HeroDef d : l.heroes().values()) {
            if (d.v2() == null || d.v2().gender() == null) {
                continue; // format 1 has no gender
            }
            if (d.v2().gender() == HeroDef.Gender.FEMALE) {
                f++;
            } else {
                m++;
            }
        }
        return new int[]{f, m};
    }

    @Test
    void everyShippedHeroLoadsWithNothingLeftOut() throws IOException {
        List<Source> defs = read(DIR);
        assertFalse(defs.isEmpty(), "the mod ships at least the test rig's definition");
        Loaded l = assemble(defs);
        assertLoadsClean(defs, l);
        assertIdsUnique(defs, l);
        assertTrue(l.heroes().containsKey("test_rig"), l.heroes().keySet().toString());
    }

    @Test
    void theRosterKeepsItsGenders() throws IOException {
        Loaded l = assemble(read(DIR));
        int[] g = genders(l);
        assertTrue(g[0] <= ROSTER / 2 && g[1] <= ROSTER / 2, "women " + g[0] + ", men " + g[1]);
        if (l.heroes().size() >= ROSTER) {
            assertEquals(ROSTER / 2, g[0], "a complete roster is 100 women and 100 men");
            assertEquals(ROSTER / 2, g[1], "a complete roster is 100 women and 100 men");
        }
    }

    /** SLICE_PLAN 7: the eight load cleanly, no duplicate ids, four women and four men (shipped or still fixtures). */
    @Test
    void theSliceLoadsWithTheShippedHeroes() throws IOException {
        List<Source> shipped = read(DIR);
        Set<String> ids = new HashSet<>();
        shipped.forEach(s -> ids.add(HeroReader.idFromFileName(s.path())));
        List<Source> all = new ArrayList<>(shipped);
        for (Source s : read(SLICE)) {
            if (!ids.contains(HeroReader.idFromFileName(s.path()))) {
                all.add(s);
            }
        }
        Loaded l = assemble(all);
        assertLoadsClean(all, l);
        assertIdsUnique(all, l);
        int f = 0, m = 0;
        for (String id : SliceKitsTest.IDS) {
            HeroDef d = l.hero(id);
            assertNotNull(d, id);
            if (d.v2().gender() == HeroDef.Gender.FEMALE) {
                f++;
            } else {
                m++;
            }
        }
        assertEquals(4, f, "four women in the slice");
        assertEquals(4, m, "four men in the slice");
    }

    /** The rules above catch what they are for (each one broken on purpose, on test text, never on shipped files). */
    @Test
    void theChecksCatchADuplicateABrokenHeroAPartLeftOutAndAWrongId() {
        List<Source> twice = List.of(new Source("data/factionclash/heroes/test_hero.json", V2Files.hero2("")),
                new Source("config/test_hero.json", V2Files.hero2("")));
        Loaded dup = HeroReader.readAll(twice, HeroReader.Options.BUILTIN);
        assertThrows(AssertionError.class, () -> assertLoadsClean(twice, dup));
        List<Source> broken = List.of(new Source("data/factionclash/heroes/test_hero.json",
                "{\"format\": 2, \"id\": \"test_hero\"}"));
        assertThrows(AssertionError.class, () -> assertLoadsClean(broken, assemble(broken)));
        List<Source> part = List.of(new Source("data/factionclash/heroes/test_hero.json", V2Files.hero2(
                "\"passives\": [{\"name\": \"P\", \"trigger\": \"on_yield\", \"effects\": [{\"type\":"
                        + " \"cleanse\"}]}]")));
        assertThrows(AssertionError.class, () -> assertLoadsClean(part, assemble(part)));
        List<Source> renamed = List.of(new Source("data/factionclash/heroes/other_hero.json", V2Files.hero2("")));
        Loaded r = assemble(renamed);
        assertLoadsClean(renamed, r);
        assertThrows(AssertionError.class, () -> assertIdsUnique(renamed, r));
    }
}
