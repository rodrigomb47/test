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

    /** The war council's methods in {@code FactionDirector} (knowledge review of 2026-11-19, known leak 1). */
    private static final String[] COUNCIL = {"private static void raids(", "static BaseState reportedBase(", "private static String waitingWhy(",
            "private static void watchTarget(", "static void deliverRaidIntel(", "public static String forceRaid(", "private static void launch(",
            "static List<Sightings.Base> reportedSettlements("};
    /** What the council may never read of an enemy: its roster's defenders, its own soldier power, its heroes, the truth of a fall. */
    private static final String[] ENEMY_TRUTHS = {"defenders(", "defencePower(", "heroDefence(", "heroWarPower(", "lairMobs", "data.heroes",
            "HeroState", ".destroyed ", ".destroyed)", ".destroyed;", ".destroyed&", ".destroyed|"};
    /**
     * {@code x.soldierPower}, {@code x.raiders} and {@code x.sightings}: only the council's own faction ({@code f}) and its
     * plan ({@code plan}, {@code targetPlan}). The sightings are the faction's own (recon.md 5.5, task R11): what its people
     * saw of the enemy away from their bases, never another faction's.
     */
    private static final java.util.regex.Pattern OWNED = java.util.regex.Pattern.compile("\\b(\\w+)\\.(soldierPower|raiders|sightings)\\b");
    /**
     * What a report holds besides its focused recon (recon.md 7, task R5): the passing look's count and note, and the old
     * look, losses and heroes. A field read, not a method call ({@code r.heroes()} is the recon's).
     */
    private static final java.util.regex.Pattern NOT_THE_RECON = java.util.regex.Pattern.compile("\\.(count|note|look|losses|lossAt|heroes)\\b(?!\\s*\\()");

    /**
     * The war council judges an enemy base only by what its own people saw ({@code docs/design/knowledge.md}): since task
     * R5 its scouts' focused recons ({@code docs/design/recon.md}). Its methods never read another faction's roster (the
     * defenders' count), soldier power, raid party or heroes, nor a base's true fall. A line that reads the truth for the
     * engine or the operators (the party's own roll on the spot, the dump's line) says so with {@code // truth:}.
     */
    @Test
    void theWarCouncilJudgesAnEnemyOnlyByWhatItsPeopleSaw() throws IOException {
        Path file = Path.of("src/main/java/com/factionclash/campaign/FactionDirector.java");
        String src = Files.readString(file);
        List<String> found = new ArrayList<>();
        int lines = 0;
        for (String head : COUNCIL) {
            int at = src.indexOf(head);
            assertTrue(at >= 0, "no council method " + head);
            int open = src.indexOf('{', at), depth = 0, end = open;
            for (int i = open; i < src.length(); i++) {
                char c = src.charAt(i);
                depth += c == '{' ? 1 : c == '}' ? -1 : 0;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
            for (String line : src.substring(at, end).split("\\n")) {
                lines++;
                String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
                if (line.trim().startsWith("*") || line.contains(ENGINE_MARK)) {
                    continue;
                }
                for (String t : ENEMY_TRUTHS) {
                    if (code.contains(t)) {
                        found.add(head + " reads " + t + ": " + line.trim());
                    }
                }
                java.util.regex.Matcher m = OWNED.matcher(code);
                while (m.find()) {
                    if (!m.group(1).equals("f") && !m.group(1).equals("plan") && !m.group(1).equals("targetPlan")) {
                        found.add(head + " reads another's " + m.group(2) + ": " + line.trim());
                    }
                }
                if (code.contains("data.faction(")) {
                    found.add(head + " reads another faction's state: " + line.trim());
                }
            }
        }
        assertTrue(lines > 150, "too little of the council scanned: " + lines);
        assertTrue(found.isEmpty(), "the war council reads what its people never saw: " + found);
    }

    /**
     * The owner's rule (2026-11-25, recon.md 0, task R5): "Only estimate enemy defence when the scout or other related role
     * makes a report of what he saw while focused on this particilar task". Of a base's report the war council reads only
     * its focused recon ({@code Report.recon}): never a passing look's count or note, nor the old look, losses or heroes.
     * It plans on that recon ({@code Reserve.plan}), with what the faction's own people saw of the enemy away from their
     * bases ({@code FactionState.sightings}, task R11: only upward, only on top of the recon), and an operator's raid does
     * the same.
     */
    @Test
    void theWarCouncilReadsOnlyTheFocusedRecon() throws IOException {
        Path file = Path.of("src/main/java/com/factionclash/campaign/FactionDirector.java");
        String src = Files.readString(file);
        List<String> found = new ArrayList<>();
        int plans = 0, recons = 0, ownSightings = 0;
        for (String head : COUNCIL) {
            int at = src.indexOf(head);
            assertTrue(at >= 0, "no council method " + head);
            int open = src.indexOf('{', at), depth = 0, end = open;
            for (int i = open; i < src.length(); i++) {
                char c = src.charAt(i);
                depth += c == '{' ? 1 : c == '}' ? -1 : 0;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
            for (String line : src.substring(at, end).split("\\n")) {
                String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
                if (line.trim().startsWith("*")) {
                    continue;
                }
                plans += code.contains("Reserve.plan(") ? 1 : 0;
                ownSightings += code.contains("f.sightings.forBase(") ? 1 : 0;
                recons += code.contains(".recon") ? 1 : 0;
                if (NOT_THE_RECON.matcher(code).find()) {
                    found.add(head + ": " + line.trim());
                }
            }
        }
        assertTrue(plans >= 2, "the council and the operators' raid should plan on Reserve.plan: " + plans);
        assertTrue(ownSightings >= 2, "the council and the operators' raid should plan with the faction's own sightings: " + ownSightings);
        assertTrue(recons >= 2, "the council should read the reports' recon: " + recons);
        assertTrue(found.isEmpty(), "the war council reads a report beyond its focused recon: " + found);
        assertFalse(Files.readString(Path.of("src/main/java/com/factionclash/campaign/Report.java")).matches("(?s).*public\\s+\\S*Look\\s+look\\b.*"),
                "a report still keeps a look at the yard");
    }

    /**
     * The war council's recon orders ({@code campaign/ReconOrders}, recon.md 2.1, task R3) are placed and settled from what
     * the faction itself knows: its reports, the strikes it saw, its roster's view of its scouts and what they told at the
     * station. The class takes no level and no campaign data, so it can read no enemy's records, no base's true state and
     * no entity; nor any truth the council may not read.
     */
    @Test
    void theReconOrdersReadOnlyTheFactionsOwnKnowledge() throws IOException {
        Path file = Path.of("src/main/java/com/factionclash/campaign/ReconOrders.java");
        List<String> found = new ArrayList<>();
        String[] world = {"ServerLevel", "CampaignData", "data.", ".bases", "getEntit", "level.", "reportedBase("};
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
            if (line.trim().startsWith("*") || line.trim().startsWith("/**")) {
                continue;
            }
            for (String t : world) {
                if (code.contains(t)) {
                    found.add((i + 1) + " reads " + t + ": " + line.trim());
                }
            }
            for (String t : ENEMY_TRUTHS) {
                if (code.contains(t)) {
                    found.add((i + 1) + " reads " + t + ": " + line.trim());
                }
            }
            if (RECORD_TRUTH.matcher(code).find()) {
                found.add((i + 1) + " reads a record's truth: " + line.trim());
            }
        }
        assertTrue(lines.size() > 100, "too little of the orders scanned: " + lines.size());
        assertTrue(found.isEmpty(), "the recon orders read what the faction never saw: " + found);
    }

    /**
     * {@code hero/} holds a hero's faction-facing lines and decisions too (knowledge review of 2026-11-19): it never reads
     * the kill feed or kill credit, but for the engine's writes ({@code KillCredits.push}, the tracker's clock, and
     * {@code HeroKillFeed.fellAway}: the feed's line for a hero that died unloaded with its faction), the kill feed itself
     * (player-side presentation) and the presentation lines that say so with {@code // truth:}.
     */
    @Test
    void heroCodeReadsTheKillFeedAndKillCreditOnlyForPresentation() throws IOException {
        Path dir = Path.of("src/main/java/com/factionclash/hero");
        List<String> found = new ArrayList<>();
        int marked = 0;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java") && !f.getFileName().toString().equals("HeroKillFeed.java")).toList()) {
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("*") || line.contains("KillCredits.push(") || line.contains("KillCredit.TRACKER_TICKS")
                            || line.contains("HeroKillFeed.fellAway(")
                            || line.contains("KillCredit.Kind.")) {
                        continue;
                    }
                    for (String t : TRUTHS) {
                        if (line.contains(t)) {
                            if (line.contains(ENGINE_MARK)) {
                                marked++;
                            } else {
                                found.add(p.getFileName() + ":" + (i + 1) + " reads " + t);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "hero code reads kill credit or the kill feed for a decision: " + found);
        assertTrue(marked <= 2, "more presentation reads than the fall's line and the death message's name: " + marked);
    }
}
