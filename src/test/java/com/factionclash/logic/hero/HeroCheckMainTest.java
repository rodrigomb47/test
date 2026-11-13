package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code ./gradlew checkHeroes} ({@link HeroCheckMain}): what passes, what fails, and what it prints. */
class HeroCheckMainTest {
    private record Run(int code, String out) {
    }

    private static Run run(String... args) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int code = HeroCheckMain.run(args, new PrintStream(b, true, StandardCharsets.UTF_8));
        return new Run(code, b.toString(StandardCharsets.UTF_8));
    }

    @Test
    void theSliceFixturesPass() throws IOException {
        Run r = run("src/test/resources/heroes/slice");
        assertEquals(0, r.code(), r.out());
        assertTrue(r.out().contains("Heroes: 8 loaded"), r.out());
        assertTrue(r.out().contains("Kagenori Tsurumine: fights to the death"), r.out());
        assertTrue(r.out().contains("Natsuho Komezaki: escapes under 8%, never from its home"), r.out());
        // the mobility sheet of MOBILITY_AND_WORK 8.3 and 15.9: levels 1, 10 and 20
        assertTrue(r.out().contains("  mobility: stamina (s) 27.0 / 35.5 / 45.0"), r.out());
        assertTrue(r.out().contains("  mobility: climb height (blocks) 9 / 12 / 16"), r.out());
        assertTrue(r.out().endsWith("checkHeroes: passed (8 files)\n"), r.out());
        assertEquals(0, run("--strict", "src/test/resources/heroes/slice").code(), "nothing to report at all");
    }

    @Test
    void aMissingOrEmptyFolderIsAnEmptyRoster(@TempDir Path dir) throws IOException {
        Run missing = run(dir.resolve("none").toString());
        assertEquals(0, missing.code());
        assertTrue(missing.out().contains("does not exist yet"), missing.out());
        assertEquals(0, run(dir.toString()).code());
    }

    @Test
    void aHeroNotLoadedOrAPartLeftOutFails(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("test_hero.json"), V2Files.hero2("\"passives\": [{\"name\": \"P\", \"trigger\":"
                + " \"on_yield\", \"effects\": [{\"type\": \"cleanse\"}]}]"));
        Run part = run(dir.toString());
        assertEquals(1, part.code(), part.out());
        assertTrue(part.out().contains("PART LEFT OUT."), part.out());
        assertTrue(part.out().endsWith("checkHeroes: FAILED\n"), part.out());
        Files.writeString(dir.resolve("test_hero.json"), "{\"format\": 2, \"id\": \"test_hero\"}");
        Run dead = run(dir.toString());
        assertEquals(1, dead.code(), dead.out());
        assertTrue(dead.out().contains("NOT LOADED."), dead.out());
    }

    @Test
    void adjustmentsPassUnlessStrict(@TempDir Path dir) throws IOException {
        // built-in heroes need their identity: a NOTE; a wind-up under the class minimum: ADJUSTED
        Files.writeString(dir.resolve("test_hero.json"), V2Files.hero2("\"moves\": [" + V2Files.LUNGE.replace(
                "\"form\": \"dash\"", "\"form\": \"dash\", \"frames\": {\"windup\": 0.2}") + "]"));
        Run r = run(dir.toString());
        assertEquals(0, r.code(), r.out());
        assertTrue(r.out().contains("ADJUSTED."), r.out());
        assertEquals(1, run("--strict", dir.toString()).code());
        assertEquals(2, run("--loud", dir.toString()).code(), "an unknown option");
    }
}
