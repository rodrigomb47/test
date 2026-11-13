package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.TalentId;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.leader.Talents.Copy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Talents} (HEROES_V2 7.2): every tier number of the catalogue's table, the milestones, every talent working at
 * tier I, the strongest copy at a base and the realm rule.
 */
class TalentsTest {
    private static final Pattern TRIPLE = Pattern.compile("(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)");

    @Test
    void theTierNumbersAndMilestonesOfSevenTwo() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/HEROES_V2.md"));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Talent | I / II / III (PROPOSED) | Hook (existing code) | Milestone |")) {
                start = i + 2;
            }
        }
        assertTrue(start > 0);
        Set<TalentId> seen = EnumSet.noneOf(TalentId.class);
        for (int i = start; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String[] c = lines.get(i).split("\\|", -1);
            String id = c[1].trim().replace("`", "").split("\\s+")[0];
            TalentId t = HeroCatalogue.enumOf(TalentId.class, id);
            assertNotNull(t, id);
            Talents.Spec spec = Talents.spec(t);
            String ms = c[4].trim();
            assertEquals(ms.equals("later") ? 0 : Integer.parseInt(ms), t.milestone, id);
            Matcher m = TRIPLE.matcher(c[2]);
            while (m.find()) {
                if (t == TalentId.MENTOR && c[2].substring(Math.max(0, m.start() - 2), m.start()).contains("-")) {
                    continue; // "level 1-2/2/3": mentorRecruitLevel, below
                }
                double[] want = {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                        Double.parseDouble(m.group(3))};
                boolean found = false;
                for (double[] v : spec.numbers().values()) {
                    found |= Math.abs(v[0] - want[0]) < 1e-9 && Math.abs(v[1] - want[1]) < 1e-9
                            && Math.abs(v[2] - want[2]) < 1e-9;
                }
                assertTrue(found, id + ": " + m.group());
            }
            seen.add(t);
        }
        assertEquals(EnumSet.allOf(TalentId.class), seen, "all 35");
    }

    @Test
    void everyTalentWorksAtTierOneAndGrows() {
        for (TalentId t : TalentId.values()) {
            Talents.Spec s = Talents.spec(t);
            assertFalse(s.numbers().isEmpty(), t.id());
            for (Map.Entry<String, double[]> e : s.numbers().entrySet()) {
                double[] v = e.getValue();
                assertTrue(v[0] != 0, t.id() + " " + e.getKey() + " works at tier I");
                // each quantity changes one way only: better at each tier or the same
                boolean up = v[1] >= v[0] && v[2] >= v[1], down = v[1] <= v[0] && v[2] <= v[1];
                assertTrue(up || down, t.id() + " " + e.getKey());
            }
            assertTrue(s.hook() != null && !s.hook().isBlank());
        }
        // the owner's answer: mentor's recruit level and spymaster's warning start at tier I
        assertEquals(1, Talents.mentorRecruitLevel(1, 0));
        assertEquals(2, Talents.mentorRecruitLevel(1, 1), "every second recruit at level 2");
        assertEquals(2, Talents.mentorRecruitLevel(2, 0));
        assertEquals(3, Talents.mentorRecruitLevel(3, 7));
        assertEquals(0.5, Talents.value(TalentId.SPYMASTER, "raid_warning_minutes", 1), 1e-9);
        // revival_rite is HeroRules' table
        for (int tier = 1; tier <= 3; tier++) {
            assertEquals(HeroRules.REVIVAL_RITE_PERCENT[tier], Talents.value(TalentId.REVIVAL_RITE, "sooner_percent",
                    tier), 1e-9);
        }
        assertEquals(8, Talents.value(TalentId.SURVEYOR, "search_radius", 0), 1e-9, "tiers clamp to 1-3");
        assertEquals(24, Talents.value(TalentId.SURVEYOR, "search_radius", 9), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Talents.value(TalentId.SURVEYOR, "nothing", 1));
    }

    @Test
    void tiersByLevelAndMilestones() {
        assertEquals(1, Talents.tier(1));
        assertEquals(1, Talents.tier(9));
        assertEquals(2, Talents.tier(10));
        assertEquals(3, Talents.tier(20));
        assertEquals(3, Talents.tier(30));
        int live1 = 0;
        for (TalentId t : TalentId.values()) {
            live1 += Talents.live(t, 1) ? 1 : 0;
        }
        assertEquals(15, live1, "milestone 1 makes 15 talents live");
        assertFalse(Talents.live(TalentId.ALCHEMIST, 99), "waits for its system");
        assertTrue(Talents.live(TalentId.TRIBUTE_MASTER, 3));
        assertFalse(Talents.live(TalentId.TRIBUTE_MASTER, 2));
    }

    @Test
    void theStrongestCopyCountsAndTheRealmReachesEveryBaseAtTierOne() {
        List<Copy> copies = new ArrayList<>();
        copies.add(new Copy(TalentId.MENTOR, 12, false, true));   // tier II here
        copies.add(new Copy(TalentId.MENTOR, 25, false, true));   // tier III here
        copies.add(new Copy(TalentId.MENTOR, 30, false, false));  // away: nothing
        copies.add(new Copy(TalentId.LOOKOUT, 25, true, false));  // a leader's realm talent elsewhere: tier I
        copies.add(new Copy(TalentId.DRILL, 25, false, false));   // on a raid: nothing
        Map<TalentId, Integer> at = Talents.atBase(copies);
        assertEquals(3, at.get(TalentId.MENTOR), "the strongest copy, never the sum");
        assertEquals(1, at.get(TalentId.LOOKOUT));
        assertFalse(at.containsKey(TalentId.DRILL));
        assertEquals(Map.of(TalentId.LOOKOUT, 3), Talents.atBase(List.of(new Copy(TalentId.LOOKOUT, 25, true,
                false), new Copy(TalentId.LOOKOUT, 20, true, true))), "a realm talent at its own base: its tier");
        assertTrue(Talents.atBase(List.of()).isEmpty());
        assertEquals(3, Talents.MAX_PER_HERO);
        assertEquals(1, Talents.MAX_REALM);
    }
}
