package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroJson.Member;
import com.factionclash.logic.hero.HeroJson.Obj;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An operator's override of a built-in hero (HEROES_V2 12.1, {@code config/factionclash/heroes/<id>.json}): its
 * top-level fields replace the built-in hero's whole fields of the same name (a {@code boss} in the override replaces
 * the whole built-in {@code boss}), new ones are added, and {@code id}, {@code art}, {@code animations} and
 * {@code format} always stay the built-in's. The merged hero is then checked like any built-in one; each problem in a
 * field the override wrote names the override file and its own line ({@link Source#fileOf}). Pure, unit-tested
 * ({@code HeroOverrideTest}).
 */
public final class HeroOverride {
    /** Fields an override can never change. */
    public static final Set<String> FIXED = Set.of("id", "art", "animations", "format");

    private HeroOverride() {
    }

    /**
     * The built-in hero with an override merged over it ({@code source}: the built-in itself when nothing applies),
     * the override's fields that replaced or added one ({@code fields}, in the override's order) and what the merge
     * reported.
     */
    public record Merged(Source source, List<String> fields, List<Problem> problems) {
        public Merged {
            fields = List.copyOf(fields);
            problems = List.copyOf(problems);
        }
    }

    public static Merged merge(Source builtIn, Source override) {
        String id = HeroReader.idFromFileName(builtIn.path());
        List<Problem> out = new ArrayList<>();
        Obj base;
        if (builtIn.tree() != null) {
            base = builtIn.tree();
        } else {
            HeroJson.Result b = HeroJson.parse(builtIn.text());
            if (!b.ok() || !(b.root() instanceof Obj o)) {
                // the built-in itself is broken: its own NOT LOADED says so; the override waits for it
                return new Merged(builtIn, List.of(), List.of());
            }
            base = o;
        }
        String file = override.path();
        HeroJson.Result r = HeroJson.parse(override.text());
        for (HeroJson.Issue n : r.notes()) {
            out.add(new Problem(Severity.NOTE, "json." + n.code().name().toLowerCase(Locale.ROOT), id, file, n.line(),
                    n.col(), "", "", n.message(), null));
        }
        if (!r.ok()) {
            HeroJson.Issue e = r.error();
            out.add(new Problem(Severity.PART_LEFT_OUT, Code.OVERRIDE_UNREADABLE.id(), id, file, e.line(), e.col(), "",
                    "", "the override could not be read: " + lowerFirst(e.message()) + ". The built-in hero loads as"
                    + " it is", "Override"));
            return new Merged(builtIn, List.of(), out);
        }
        if (!(r.root() instanceof Obj o)) {
            out.add(new Problem(Severity.PART_LEFT_OUT, Code.OVERRIDE_UNREADABLE.id(), id, file, r.root().line(),
                    r.root().col(), "", "", "an override is one object { ... } of the fields to replace, not "
                    + r.root().what() + ". The built-in hero loads as it is", "Override"));
            return new Merged(builtIn, List.of(), out);
        }
        Map<String, Member> merged = new LinkedHashMap<>();
        for (Member m : base.members()) {
            merged.putIfAbsent(m.key(), m);
        }
        List<String> fields = new ArrayList<>();
        Map<String, String> files = new LinkedHashMap<>();
        for (Member m : o.members()) {
            String k = m.key();
            if (k.startsWith("_")) {
                continue; // a comment
            }
            if (FIXED.contains(k)) {
                out.add(new Problem(Severity.NOTE, Code.OVERRIDE_FIXED.id(), id, file, m.line(), m.col(), k, k,
                        "\"" + k + "\" can never be overridden (only the built-in hero's counts); ignored", null));
                continue;
            }
            if (files.containsKey(k)) {
                continue; // a repeated key: the JSON reader's note names it, the first one counts
            }
            merged.put(k, m);
            fields.add(k);
            files.put(k, file);
        }
        if (fields.isEmpty()) {
            return new Merged(builtIn, List.of(), out);
        }
        Obj tree = new Obj(base.line(), base.col(), List.copyOf(merged.values()));
        return new Merged(new Source(builtIn.path(), builtIn.text(), tree, files), fields, out);
    }

    private static String lowerFirst(String s) {
        String t = s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        return t.isEmpty() ? t : Character.toLowerCase(t.charAt(0)) + t.substring(1);
    }
}
