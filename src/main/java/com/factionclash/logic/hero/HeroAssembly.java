package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The game's set of hero definitions (HEROES_V2 12.1; task A1b): the server definitions read from
 * {@code data/factionclash/heroes/} (a datapack may replace a built-in hero's file, never add a hero: the owner's
 * answer of 2026-11-12, built-in heroes only), each with an operator's override from
 * {@code config/factionclash/heroes/<id>.json} merged over it ({@link HeroOverride}), then the fixtures a test
 * registered, then all of it checked as built-in heroes by {@link HeroReader#readAll}. Every step's messages go into
 * the one report. An override can never take a hero away (v1 26.5, 25.5): one that leaves its hero unable to load is
 * not applied, the built-in hero loads as it is, and the report says why the override was left out, as it does for an
 * override it cannot read. Pure, unit-tested ({@code HeroAssemblyTest}).
 */
public final class HeroAssembly {
    private HeroAssembly() {
    }

    /**
     * @param definitions the server definitions (a datapack's replacement already in place of the built-in file)
     * @param shipped     the ids the mod itself ships a definition for; null accepts every id (the checker's view)
     * @param overrides   the operator's override files
     * @param fixtures    test fixtures: always accepted, and they replace a definition of the same id
     */
    public record Input(List<Source> definitions, Set<String> shipped, List<Source> overrides, List<Source> fixtures,
                        Options options) {
        public Input {
            definitions = List.copyOf(definitions);
            overrides = List.copyOf(overrides);
            fixtures = List.copyOf(fixtures);
        }
    }

    /** What loaded (the reader's result with the assembly's messages first) and the fields each override changed. */
    public record Result(Loaded loaded, Map<String, List<String>> overridden) {
    }

    public static Result assemble(Input in) {
        Problems problems = new Problems();
        List<String> notLoaded = new ArrayList<>();
        Map<String, Source> byId = new TreeMap<>();
        Set<String> fixtureIds = new java.util.HashSet<>();
        for (Source s : in.fixtures()) {
            fixtureIds.add(HeroReader.idFromFileName(s.path()));
        }
        for (Source s : in.definitions()) {
            String id = HeroReader.idFromFileName(s.path());
            if (fixtureIds.contains(id)) {
                continue; // a test's fixture stands in for it
            }
            if (in.shipped() != null && !in.shipped().contains(id)) {
                problems.add(new Problem(Severity.NOT_LOADED, Code.NOT_BUILTIN.id(), id, s.path(), 0, 0, "", "",
                        "\"" + id + "\" is not one of the mod's heroes: only the built-in heroes load. An operator"
                        + " changes a built-in hero with config/factionclash/heroes/<id>.json", null));
                notLoaded.add(id);
                continue;
            }
            byId.put(id, s);
        }
        for (Source s : in.fixtures()) {
            byId.put(HeroReader.idFromFileName(s.path()), s);
        }
        Map<String, List<String>> overridden = new LinkedHashMap<>();
        Map<String, Source> builtIns = new LinkedHashMap<>();
        Map<String, String> overrideFiles = new LinkedHashMap<>();
        for (Source o : in.overrides()) {
            String id = HeroReader.idFromFileName(o.path());
            Source base = byId.get(id);
            if (base == null) {
                problems.add(new Problem(Severity.NOTE, Code.OVERRIDE_NO_HERO.id(), id, o.path(), 0, 0, "", "",
                        "there is no built-in hero \"" + id + "\" to override; the file is ignored", null));
                continue;
            }
            HeroOverride.Merged m = HeroOverride.merge(base, o);
            m.problems().forEach(problems::add);
            if (!m.fields().isEmpty()) {
                builtIns.putIfAbsent(id, base);
                overrideFiles.put(id, o.path());
                byId.put(id, m.source());
                overridden.put(id, m.fields());
            }
        }
        Loaded l = HeroReader.readAll(new ArrayList<>(byId.values()), in.options());
        // an override that leaves its hero unable to load is not applied: the built-in hero loads as it is
        List<String> rescued = new ArrayList<>();
        for (String id : overridden.keySet()) {
            if (!l.heroes().containsKey(id)
                    && HeroReader.readAll(List.of(builtIns.get(id)), in.options()).heroes().containsKey(id)) {
                rescued.add(id);
            }
        }
        if (!rescued.isEmpty()) {
            Loaded merged = l;
            for (String id : rescued) {
                byId.put(id, builtIns.get(id));
                overridden.remove(id);
                problems.add(new Problem(Severity.PART_LEFT_OUT, Code.OVERRIDE_BREAKS.id(), id, overrideFiles.get(id), 0,
                        0, "", "", "with the override the hero cannot load, for the reasons that follow; the override"
                        + " was not applied and the built-in hero loads as it is", "Override"));
                for (Problem p : merged.problems().all()) {
                    if (id.equals(p.hero()) && p.severity() == Severity.NOT_LOADED) {
                        problems.add(new Problem(Severity.PART_LEFT_OUT, p.code(), p.hero(), p.file(), p.line(),
                                p.column(), p.path(), p.machinePath(), p.message(), "Override"));
                    }
                }
            }
            l = HeroReader.readAll(new ArrayList<>(byId.values()), in.options());
        }
        problems.addAll(l.problems());
        List<String> nl = new ArrayList<>(notLoaded);
        nl.addAll(l.notLoaded());
        Loaded all = new Loaded(l.heroes(), l.files(), Collections.unmodifiableList(nl), problems);
        Map<String, List<String>> ov = new TreeMap<>();
        overridden.forEach((k, v) -> {
            if (all.heroes().containsKey(k)) {
                ov.put(k, v);
            }
        });
        return new Result(all, Collections.unmodifiableMap(ov));
    }
}
