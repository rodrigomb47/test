package com.factionclash.logic.hero;

import com.factionclash.logic.hero.Problems.Problem;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java checker against the Python reference checker ({@code tools/herotool/herotool.py}) on the v1 examples: each
 * case under {@code src/test/resources/heroes/crosscheck/} is one example hero with one rule broken, written by
 * {@code tools/herotool/crosscheck.py} together with what the reference checker reported ({@code expected.tsv}). The
 * Java checker must report the same outcomes at the same places (a path may name the field inside the place the
 * reference names, or the reverse), and nothing else that is not a NOTE.
 *
 * <p>Where the two knowingly differ, the cases stay clear of it: the reference computes cooldown floors from uncapped
 * potion values while the Java checker caps first (v1 26.1 step 4 order); its schema allows every form the widest
 * range while the Java checker uses each form's own (v1 10.1); it stops at the first schema error while the Java
 * checker clamps and goes on; and it still takes v1's player-only words ({@code protects: players}, the
 * {@code target_is} kinds {@code player} and {@code npc}) as written, which the Java checker reads as words that treat
 * everyone alike (the owner's equal-treatment rule, HEROES_V2 6.15; {@code HeroCatalogue.EQUAL_READINGS}).
 *
 * <p>To add a case: add it to {@code CASES} in {@code tools/herotool/crosscheck.py} and run that script (it needs the
 * Python {@code jsonschema} package); it rewrites the whole folder and {@code expected.tsv}.
 */
class HeroCrossCheckTest {
    private static final Path DIR = Path.of("src/test/resources/heroes/crosscheck");

    private record Entry(String outcome, String path) {
    }

    private static Map<String, List<Entry>> expected() throws IOException {
        Map<String, List<Entry>> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(DIR.resolve("expected.tsv"))) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            List<Entry> l = m.computeIfAbsent(p[0], k -> new ArrayList<>());
            if (!p[1].equals("-")) {
                l.add(new Entry(p[1], p.length > 2 ? p[2] : ""));
            }
        }
        return m;
    }

    static boolean samePlace(String a, String b) {
        return a.equals(b) || b.startsWith(a + ".") || b.startsWith(a + "[") || a.startsWith(b + ".")
                || a.startsWith(b + "[");
    }

    @Test
    void everyCaseHasItsFile() throws IOException {
        Map<String, List<Entry>> exp = expected();
        assertTrue(exp.size() >= 30, "the cross-check cases are there: " + exp.size());
        for (String c : exp.keySet()) {
            try (Stream<Path> s = Files.list(DIR.resolve(c))) {
                assertEquals(1, s.count(), c);
            }
        }
        try (Stream<Path> s = Files.list(DIR)) {
            assertEquals(exp.size(), s.filter(Files::isDirectory).count(), "no case folder without its expected lines");
        }
    }

    /** The three unchanged examples are the files of {@code docs/heroes/examples}, and both checkers pass them. */
    @Test
    void theUnchangedCasesAreTheExamples() throws IOException {
        Map<String, List<Entry>> exp = expected();
        for (String id : List.of("bram_ostler", "sela_quickfern", "maudie_brine")) {
            String name = "unchanged_" + id.substring(0, id.indexOf('_'));
            assertEquals(List.of(), exp.get(name), "the reference checker has nothing to say about " + id);
            HeroDef fromCase = HeroReader.read(id + ".json", Files.readString(DIR.resolve(name).resolve(id + ".json")))
                    .hero(id);
            HeroDef fromDocs = HeroReader.read(id + ".json", Files.readString(Path.of("docs/heroes/examples/" + id
                    + ".json"))).hero(id);
            assertEquals(fromDocs, fromCase, id + ": run tools/herotool/crosscheck.py after changing an example");
        }
    }

    @TestFactory
    Stream<DynamicTest> theJavaCheckerReportsWhatTheReferenceReports() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> e : expected().entrySet()) {
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> check(e.getKey(), e.getValue())));
        }
        return tests.stream();
    }

    private static void check(String name, List<Entry> want) throws IOException {
        Path file;
        try (Stream<Path> s = Files.list(DIR.resolve(name))) {
            file = s.findFirst().orElseThrow();
        }
        HeroReader.Loaded l = HeroReader.read(file.getFileName().toString(), Files.readString(file));
        List<Problem> got = l.problems().all();
        StringBuilder all = new StringBuilder();
        for (Problem p : got) {
            all.append("\n  ").append(p.severity().label).append(' ').append(p.machinePath()).append(": ")
                    .append(p.format());
        }
        Set<String> missing = new LinkedHashSet<>();
        for (Entry w : want) {
            boolean found = got.stream().anyMatch(p -> p.severity().label.equals(w.outcome)
                    && samePlace(w.path, p.machinePath()));
            if (!found) {
                missing.add(w.outcome + " at " + w.path);
            }
        }
        Set<String> extra = new LinkedHashSet<>();
        for (Problem p : got) {
            if (p.severity() == Problems.Severity.NOTE) {
                continue;
            }
            boolean found = want.stream().anyMatch(w -> p.severity().label.equals(w.outcome)
                    && samePlace(w.path, p.machinePath()));
            if (!found) {
                extra.add(p.format());
            }
        }
        assertTrue(missing.isEmpty() && extra.isEmpty(), name + ": the reference reported " + want
                + "; missing in Java: " + missing + "; Java only: " + extra + "; Java said:" + all);
    }
}
