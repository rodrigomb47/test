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
    private static final String[] FACTION_SIDE = {"campaign", "work", "npc", "logic/campaign"};
    private static final String[] TRUTHS = {"HeroKillFeed", "KillCredits", "KillCredit."};

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
}
