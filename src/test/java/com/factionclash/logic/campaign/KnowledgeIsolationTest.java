package com.factionclash.logic.campaign;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The faction's AI knows only what its people saw ({@code docs/design/missing_npcs.md} 8): the hero kill feed is
 * player-side presentation that every player sees at once, and kill credit is the truth of who killed whom. Neither may
 * be read by the campaign (the director, recruitment, the roll call, the war council), the jobs or the NPC entity.
 */
class KnowledgeIsolationTest {
    private static final String[] FACTION_SIDE = {"campaign", "work", "npc", "logic/campaign", "info", "achieve"};
    private static final String[] TRUTHS = {"HeroKillFeed", "KillCredits", "KillCredit."};
    /** A roster entry's truth (docs/design/missing_npcs.md 1): what happened, not what the faction knows. */
    private static final java.util.regex.Pattern RECORD_TRUTH =
            java.util.regex.Pattern.compile("\\.(dead|vanished|witnessed|diedAt|diedPos)\\b|\\.gone\\(\\)");
    /** Where the truth may be read: the record itself and the roll call that keeps it apart from the knowledge. */
    private static final java.util.Set<String> TRUTH_KEEPERS = java.util.Set.of("campaign/NpcRecord.java", "campaign/RollCall.java");
    /** A line that reads the truth for the engine, not for the faction's mind, says so. */
    private static final String ENGINE_MARK = "// truth:";

    @Test
    void theFactionSideNeverReadsTheKillFeedOrKillCredit() throws IOException {
        Path root = Path.of("src/main/java/com/factionclash");
        assertTrue(Files.isDirectory(root), "run from the project directory: " + root.toAbsolutePath());
        List<String> found = new ArrayList<>();
        int files = 0;
        for (String pkg : FACTION_SIDE) {
            Path dir = root.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "no package " + dir);
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                    files++;
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        for (String t : TRUTHS) {
                            if (lines.get(i).contains(t)) {
                                found.add(root.relativize(p) + ":" + (i + 1) + " reads " + t);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(files > 40, "too few sources scanned: " + files);
        assertTrue(found.isEmpty(), "the faction side reads a truth it could not know: " + found);
    }

    /**
     * The faction side reads a roster entry's knowledge ({@code alive}, {@code where}), never its truth ({@code dead},
     * {@code vanished}, {@code witnessed}, {@code diedAt}, {@code diedPos}, {@code gone()}), outside the record and the
     * roll call; a line that reads it for the engine (the raid party on the spot) carries the {@code // truth:} mark and
     * says why (review of 2026-11-19).
     */
    @Test
    void theFactionSideNeverReadsARecordsTruth() throws IOException {
        Path root = Path.of("src/main/java/com/factionclash");
        List<String> found = new ArrayList<>();
        int marked = 0;
        for (String pkg : FACTION_SIDE) {
            try (Stream<Path> s = Files.list(root.resolve(pkg))) {
                for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (TRUTH_KEEPERS.contains(rel)) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
                        if (line.trim().startsWith("*") || !RECORD_TRUTH.matcher(code).find()) {
                            continue;
                        }
                        if (line.contains(ENGINE_MARK)) {
                            marked++;
                        } else {
                            found.add(rel + ":" + (i + 1) + ": " + line.trim());
                        }
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "the faction side reads a record's truth: " + found);
        assertTrue(marked <= 2, "more engine reads of the truth than the raid party's two: " + marked);
    }
}
