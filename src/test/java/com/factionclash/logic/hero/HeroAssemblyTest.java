package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game's set of hero definitions: built-ins only, overrides merged, fixtures, one report (task A1b). */
class HeroAssemblyTest {
    private static Source def(String id, String more) {
        return new Source("data/factionclash/heroes/" + id + ".json", V2Files.hero2(more).replace("test_hero", id));
    }

    private static HeroAssembly.Result assemble(List<Source> defs, Set<String> shipped, List<Source> overrides,
                                                List<Source> fixtures) {
        return HeroAssembly.assemble(new HeroAssembly.Input(defs, shipped, overrides, fixtures, Options.DEFAULT));
    }

    @Test
    void onlyTheModsOwnHeroesLoad() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", ""), def("beta_hero", "")), Set.of("alpha_hero"), List.of(),
                List.of());
        assertNotNull(r.loaded().hero("alpha_hero"));
        assertNull(r.loaded().hero("beta_hero"), "a datapack may replace a built-in hero, never add one");
        assertTrue(r.loaded().notLoaded().contains("beta_hero"));
        var p = r.loaded().problems().all().stream().filter(x -> x.code().equals(Code.NOT_BUILTIN.id())).toList();
        assertEquals(1, p.size());
        assertEquals(Severity.NOT_LOADED, p.get(0).severity());
        assertEquals("data/factionclash/heroes/beta_hero.json", p.get(0).file());
    }

    @Test
    void withoutAShippedListEveryDefinitionIsAccepted() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", ""), def("beta_hero", "")), null, List.of(), List.of());
        assertEquals(Set.of("alpha_hero", "beta_hero"), r.loaded().heroes().keySet());
        assertTrue(r.loaded().problems().all().isEmpty(), r.loaded().problems().report());
    }

    @Test
    void anOverrideChangesItsHeroAndIsRecorded() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", ""), def("beta_hero", "")), Set.of("alpha_hero", "beta_hero"),
                List.of(new Source("config/factionclash/heroes/alpha_hero.json", "{\"name\": \"Alpha Prime\", \"rarity\": \"epic\"}")),
                List.of());
        assertEquals("Alpha Prime", r.loaded().hero("alpha_hero").name());
        assertEquals("epic", r.loaded().hero("alpha_hero").rarity().id());
        assertEquals("Test Hero", r.loaded().hero("beta_hero").name(), "the other hero is untouched");
        assertEquals(Map.of("alpha_hero", List.of("name", "rarity")), r.overridden());
    }

    @Test
    void anOverrideWithNoBuiltInHeroIsIgnoredWithANote() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", "")), null,
                List.of(new Source("config/factionclash/heroes/ghost_hero.json", "{\"name\": \"Boo\"}")), List.of());
        assertNull(r.loaded().hero("ghost_hero"));
        assertTrue(r.loaded().problems().has(Code.OVERRIDE_NO_HERO.id()));
        assertTrue(r.overridden().isEmpty());
    }

    /**
     * An override can never take a hero away (v1 26.5, 25.5; the same as an override the reader cannot parse): one that
     * leaves the hero unable to load is not applied, the built-in hero loads as it is, and the report says why, at the
     * override's own line, as PART LEFT OUT.
     */
    @Test
    void anOverrideThatBreaksItsHeroIsLeftOutAndTheBuiltInLoads() {
        String file = "config/factionclash/heroes/alpha_hero.json";
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", "\"title\": \"the First\""), def("beta_hero", "")),
                null, List.of(new Source(file, "{\"title\": \"the Broken\",\n\"basic_attack\": {\"style\": \"laser\"}}")),
                List.of());
        HeroDef d = r.loaded().hero("alpha_hero");
        assertNotNull(d, "the built-in hero still loads:\n" + r.loaded().problems().report());
        assertEquals("the First", d.title(), "none of the override applies, not even its good fields");
        assertNotNull(r.loaded().hero("beta_hero"));
        assertTrue(r.overridden().isEmpty(), "the override is not recorded as applied");
        assertTrue(r.loaded().notLoaded().isEmpty(), "nothing is reported not loaded");
        List<Problems.Problem> ps = r.loaded().problems().all();
        assertTrue(ps.stream().noneMatch(p -> p.severity() == Severity.NOT_LOADED), r.loaded().problems().report());
        Problems.Problem lead = ps.stream().filter(p -> p.code().equals(Code.OVERRIDE_BREAKS.id())).findFirst()
                .orElseThrow();
        assertEquals(file, lead.file());
        assertEquals(Severity.PART_LEFT_OUT, lead.severity());
        assertTrue(lead.format().endsWith("Override PART LEFT OUT."), lead.format());
        Problems.Problem why = ps.stream().filter(p -> p.machinePath().startsWith("basic_attack")).findFirst()
                .orElseThrow(() -> new AssertionError(r.loaded().problems().report()));
        assertEquals(file, why.file(), "the reason names the override");
        assertEquals(2, why.line(), "at its own line");
        assertEquals(Severity.PART_LEFT_OUT, why.severity());
        assertTrue(why.message().contains("laser"), why.format());
    }

    @Test
    void anOverrideOfABrokenBuiltInLeavesItNotLoaded() {
        Source broken = new Source("data/factionclash/heroes/alpha_hero.json", "{\"format\": 2, \"id\": \"alpha_hero\"}");
        HeroAssembly.Result r = assemble(List.of(broken), null,
                List.of(new Source("config/factionclash/heroes/alpha_hero.json", "{\"title\": \"T\"}")), List.of());
        assertNull(r.loaded().hero("alpha_hero"), "an override cannot mend a built-in that has no name");
        assertTrue(r.loaded().notLoaded().contains("alpha_hero"));
        assertTrue(r.loaded().problems().all().stream().noneMatch(p -> p.code().equals(Code.OVERRIDE_BREAKS.id())),
                "the override did not break it; the built-in was broken already");
    }

    @Test
    void aFixtureIsAlwaysAcceptedAndReplacesADefinitionOfItsId() {
        Source fixture = new Source("fixture/alpha_hero.json", V2Files.hero2("").replace("test_hero", "alpha_hero")
                .replace("\"Test Hero\"", "\"Alpha Fixture\""));
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", "")), Set.of(), List.of(), List.of(fixture));
        assertEquals("Alpha Fixture", r.loaded().hero("alpha_hero").name());
        assertTrue(r.loaded().problems().all().isEmpty(), "the fixture replaced the unshipped definition: "
                + r.loaded().problems().report());
    }

    @Test
    void theAssemblysMessagesComeFirstInTheOneReport() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", "\"element\": \"wind\""), def("beta_hero", "")),
                Set.of("alpha_hero"), List.of(new Source("config/factionclash/heroes/zeta_hero.json", "{}")), List.of());
        List<String> codes = r.loaded().problems().all().stream().map(Problems.Problem::code).toList();
        assertEquals(Code.NOT_BUILTIN.id(), codes.get(0));
        assertEquals(Code.OVERRIDE_NO_HERO.id(), codes.get(1));
        assertTrue(codes.contains(Code.WORD_DEFAULT.id()), "the reader's own after them: " + codes);
    }
}
