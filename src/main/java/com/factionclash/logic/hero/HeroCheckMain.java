package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Options;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Problem;
import com.factionclash.logic.hero.Problems.Severity;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code ./gradlew checkHeroes} (HEROES_V2 13.7 and 14.1): the kit rules over the built-in hero definitions, with no
 * game running. Reads every {@code .json} under the folders given (default
 * {@code src/main/resources/data/factionclash/heroes}) as built-in heroes, prints the report of v1 26.4, one line
 * per hero for the end of its fights and its mobility at levels 1, 10 and 20 (MOBILITY_AND_WORK 8.3), and fails when a hero is NOT LOADED or a part of one is LEFT OUT. ADJUSTED
 * and NOTE lines are printed and pass, unless {@code --strict} is given (then anything to report fails). A folder
 * that does not exist yet is an empty roster, not a failure.
 */
public final class HeroCheckMain {
    private HeroCheckMain() {
    }

    public static final String DEFAULT_DIR = "src/main/resources/data/factionclash/heroes";

    public static void main(String[] args) throws IOException {
        int code = run(args, System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** The check; 0 when it passes, 1 when it fails, 2 for a bad argument. */
    public static int run(String[] args, PrintStream out) throws IOException {
        boolean strict = false;
        List<Path> dirs = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--strict")) {
                strict = true;
            } else if (a.startsWith("--")) {
                out.println("checkHeroes: unknown option " + a + " (use --strict, then folders)");
                return 2;
            } else if (!a.isBlank()) {
                dirs.add(Path.of(a));
            }
        }
        if (dirs.isEmpty()) {
            dirs.add(Path.of(DEFAULT_DIR));
        }
        List<Source> sources = new ArrayList<>();
        for (Path d : dirs) {
            if (!Files.isDirectory(d)) {
                out.println("checkHeroes: " + d + " does not exist yet; no heroes there");
                continue;
            }
            try (Stream<Path> s = Files.walk(d)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".json") && Files.isRegularFile(x)).sorted()
                        .toList()) {
                    sources.add(new Source(d.relativize(p).toString().replace('\\', '/'), Files.readString(p,
                            StandardCharsets.UTF_8)));
                }
            }
        }
        if (sources.isEmpty()) {
            out.println("checkHeroes: no hero files; nothing to check");
            return 0;
        }
        Loaded l = HeroReader.readAll(sources, Options.BUILTIN);
        String report = l.problems().report();
        if (!report.isEmpty()) {
            out.print(report);
        }
        for (HeroDef d : l.heroes().values()) {
            if (d.v2() != null && d.format() >= 2) {
                out.println(HeroReader2.endLine(d));
                HeroDef.Mobility m = d.v2().mobility();
                if (m != null) {
                    // MOBILITY_AND_WORK 8.3: its mobility numbers at levels 1, 10 and 20
                    for (String line : MobilityRules.sheet(m.primary(), m.secondary(), MobilityRules.Config.DEFAULT)) {
                        out.println("  mobility: " + line);
                    }
                }
            }
        }
        out.println(l.problems().summary(l.heroes().keySet(), l.notLoaded()));
        boolean fail = !l.notLoaded().isEmpty();
        for (Problem p : l.problems().all()) {
            if (strict || p.severity() == Severity.NOT_LOADED || p.severity() == Severity.PART_LEFT_OUT) {
                fail = true;
            }
        }
        out.println(fail ? "checkHeroes: FAILED" : "checkHeroes: passed (" + sources.size() + " files)");
        return fail ? 1 : 0;
    }
}
