package com.factionclash.logic.leader;

import com.factionclash.logic.hero.EscapeRules;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef.EconomyFocus;
import com.factionclash.logic.hero.HeroDef.RecruitCondition;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.hero.HeroDef.WhenLosing;
import com.factionclash.logic.leader.Temperaments.Flag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Temperaments} against HEROES_V2 Appendix A, row by row; the affinity; the flags milestone 1 acts on. */
class TemperamentsTest {
    @Test
    void everyRowMatchesAppendixA() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/HEROES_V2.md"));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Temperament | a | h | m | c | g | s | l | v | Special rule |")) {
                start = i + 2;
            }
        }
        assertTrue(start > 0, "Appendix A's table");
        Set<Temperament> seen = EnumSet.noneOf(Temperament.class);
        for (int i = start; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String[] c = lines.get(i).split("\\|", -1);
            Temperament t = HeroCatalogue.enumOf(Temperament.class, c[1].trim());
            assertNotNull(t, c[1]);
            Temperaments.Row r = Temperaments.of(t);
            Map<Trait, Integer> tr = r.traits();
            for (Trait q : Trait.values()) {
                assertEquals(Integer.parseInt(c[2 + q.ordinal()].trim()), tr.get(q), t.id() + " " + q.id());
            }
            assertEquals(c[10].trim(), r.rule(), t.id());
            assertEquals(HeroCatalogue.enumOf(EconomyFocus.class, c[11].trim()), r.focus(), t.id());
            assertEquals(HeroCatalogue.enumOf(RecruitCondition.class, c[12].trim()), r.recruit(), t.id());
            String[] ac = c[13].split("/");
            assertEquals(HeroCatalogue.enumOf(Temperament.class, ac[0].trim()), r.admires(), t.id());
            assertEquals(HeroCatalogue.enumOf(Temperament.class, ac[1].trim()), r.clashes(), t.id());
            assertEquals(r.focus(), Temperaments.focus(t));
            seen.add(t);
        }
        assertEquals(EnumSet.allOf(Temperament.class), seen);
    }

    @Test
    void affinityFromAdmiresAndClashes() {
        assertEquals(0.2, Temperaments.affinity(Temperament.HOT_BLOODED, Temperament.STOIC), 1e-9, "admires");
        assertEquals(0.2, Temperaments.affinity(Temperament.STOIC, Temperament.HOT_BLOODED), 1e-9, "both ways");
        assertEquals(-0.2, Temperaments.affinity(Temperament.HOT_BLOODED, Temperament.SCHEMER), 1e-9, "clashes");
        assertEquals(-0.2, Temperaments.affinity(Temperament.SCHEMER, Temperament.HOT_BLOODED), 1e-9);
        assertEquals(0, Temperaments.affinity(Temperament.HOT_BLOODED, Temperament.SHY), 1e-9);
        assertEquals(0, Temperaments.affinity(null, Temperament.SHY), 1e-9);
        // a righteous leader and a schemer: righteous clashes with schemer, schemer clashes with righteous
        assertEquals(-0.2, Temperaments.affinity(Temperament.RIGHTEOUS, Temperament.SCHEMER), 1e-9);
        // admired one way and clashed the other: 0 (charmer admires genki; genki admires charmer: +0.2)
        assertEquals(0.2, Temperaments.affinity(Temperament.CHARMER, Temperament.GENKI), 1e-9);
        int pairs = 0;
        for (Temperament a : Temperament.values()) {
            for (Temperament b : Temperament.values()) {
                double x = Temperaments.affinity(a, b);
                assertEquals(x, Temperaments.affinity(b, a), 1e-9, "symmetric");
                assertTrue(x == 0 || Math.abs(x) == 0.2);
                pairs += x != 0 ? 1 : 0;
            }
        }
        assertTrue(pairs > 30);
    }

    @Test
    void theFlagsMilestoneOneActsOn() {
        assertTrue(Temperaments.has(Temperament.LONE_WOLF, Flag.TERRITORIAL));
        assertTrue(Temperaments.has(Temperament.DELINQUENT, Flag.TERRITORIAL));
        assertTrue(Temperaments.has(Temperament.CHUUNIBYOU, Flag.OPENS_WITH_ULTIMATE));
        assertTrue(Temperaments.has(Temperament.GENKI, Flag.BUILDS_AHEAD));
        assertTrue(Temperaments.has(Temperament.AIRHEAD, Flag.DOUBLED_NOISE));
        assertTrue(Temperaments.has(Temperament.TRICKSTER, Flag.AMBUSH_DOUBLED));
        assertTrue(Temperaments.has(Temperament.GENTLE_GIANT, Flag.ALWAYS_SPARES));
        assertTrue(Temperaments.has(Temperament.ANCIENT_SAGE, Flag.NO_RAIDS_BEFORE_DAY_3));
        assertTrue(Temperaments.has(Temperament.HOT_BLOODED, Flag.VENGEANCE_ANY_AGGRESSION));
        assertTrue(Temperaments.has(Temperament.BATTLE_MANIAC, Flag.STRONGEST_BEATABLE));
        assertFalse(Temperaments.has(null, Flag.TERRITORIAL));
        assertFalse(Temperaments.has(Temperament.STOIC, Flag.TERRITORIAL));
        // the sleepy rule is the one EscapeRules reads: always a last stand
        assertTrue(Temperaments.has(Temperament.SLEEPY, Flag.BOLD_UNDER_HALF));
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(Temperament.SLEEPY, 0, 0, 100));
        // each flag belongs to one temperament or two (territorial)
        for (Flag f : Flag.values()) {
            long n = java.util.Arrays.stream(Temperament.values()).filter(t -> Temperaments.has(t, f)).count();
            assertTrue(n >= 1 && n <= 2, f + ": " + n);
        }
    }
}
