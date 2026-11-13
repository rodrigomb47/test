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

    @Test
    void anOverrideThatBreaksItsHeroIsNotRecordedAsApplied() {
        HeroAssembly.Result r = assemble(List.of(def("alpha_hero", "")), null,
                List.of(new Source("config/factionclash/heroes/alpha_hero.json", "{\"basic_attack\": {\"style\": \"laser\"}}")),
                List.of());
        assertNull(r.loaded().hero("alpha_hero"), "the merged hero is checked like any other: it does not load");
        assertTrue(r.overridden().isEmpty());
        assertTrue(r.loaded().notLoaded().contains("alpha_hero"));
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
