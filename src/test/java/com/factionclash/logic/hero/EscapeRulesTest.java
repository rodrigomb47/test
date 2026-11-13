package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.EndOfFight;
import com.factionclash.logic.hero.HeroDef.StandsFor;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.hero.HeroDef.WhenLosing;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EscapeRules}: the end of a fight a hero file decides (HEROES_V2 6.13, owner decision 2026-11-13: no yield;
 * escape or fight to the death by personality). The 30 temperaments against Appendix A's "When a fight goes badly"
 * table, the slice heroes, the sleepy rule, the authored override, a v1 retreat_below of 0, where an escaping hero
 * stands anyway, the escape threshold and its rounding and bounds, and the sheet line.
 */
class EscapeRulesTest {
    private static Map<Trait, Integer> traits(Temperament t) {
        return HeroCatalogue.defaultTraits(t);
    }

    private static Map<Trait, Integer> traits(int a, int h, int c, int loyalty) {
        Map<Trait, Integer> m = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            m.put(t, 50);
        }
        m.put(Trait.AGGRESSION, a);
        m.put(Trait.HONOUR, h);
        m.put(Trait.CAUTION, c);
        m.put(Trait.LOYALTY, loyalty);
        return m;
    }

    @Test
    void theThirtyTemperamentsMatchAppendixA() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/heroes-v2/HEROES_V2.md"));
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("| Temperament | Nerve | When losing | Stands anyway |")) {
                start = i + 2;
            }
        }
        assertTrue(start > 0, "the table of Appendix A");
        int rows = 0, stand = 0, homeEscapers = 0;
        for (int i = start; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            String[] c = lines.get(i).split("\\|", -1);
            Temperament t = HeroCatalogue.enumOf(Temperament.class, c[1].trim());
            int nerve = Integer.parseInt(c[2].trim());
            String kind = c[3].trim(), anyway = c[4].trim();
            Map<Trait, Integer> tr = traits(t);
            assertEquals(nerve, EscapeRules.nerve(tr.get(Trait.AGGRESSION), tr.get(Trait.HONOUR), tr.get(Trait.CAUTION)),
                    t.id());
            WhenLosing k = EscapeRules.byTraits(t, tr);
            assertEquals(kind.startsWith("last stand") ? WhenLosing.LAST_STAND : WhenLosing.ESCAPE, k, t.id());
            EndOfFight e = EscapeRules.end(t, tr, null, null, 25, false, 25);
            if (k == WhenLosing.ESCAPE) {
                List<StandsFor> want = anyway.startsWith("nowhere") ? List.of() : anyway.contains("beside its leader")
                        ? List.of(StandsFor.HOME, StandsFor.LEADER) : List.of(StandsFor.HOME);
                assertEquals(want, e.standsFor(), t.id());
                homeEscapers += e.standsFor().contains(StandsFor.HOME) ? 1 : 0;
            } else {
                assertTrue(e.standsFor().isEmpty(), t.id());
                stand++;
            }
            rows++;
        }
        assertEquals(30, rows);
        assertEquals(12, stand, "twelve stand");
        assertEquals(9, homeEscapers, "nine of the eighteen never run from their own base");
    }

    @Test
    void theNerveDecidesAtSixtyFive() {
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(null, 50, 65, 50), "65: a last stand");
        assertEquals(WhenLosing.ESCAPE, EscapeRules.byTraits(null, 50, 64, 50), "64: an escape");
        assertEquals(WhenLosing.ESCAPE, EscapeRules.byTraits(null, traits(50, 50, 50, 50)), "neutral: 50");
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(null, 100, 100, 0));
        assertEquals(WhenLosing.ESCAPE, EscapeRules.byTraits(null, 0, 0, 100));
        assertEquals(WhenLosing.ESCAPE, EscapeRules.byTraits(null, new EnumMap<>(Trait.class)), "missing traits are 50");
    }

    @Test
    void aSleepyHeroAlwaysStands() {
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(Temperament.SLEEPY, 0, 0, 100));
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(Temperament.SLEEPY, traits(Temperament.SLEEPY)));
        assertEquals(WhenLosing.ESCAPE, EscapeRules.kind(Temperament.SLEEPY, traits(Temperament.SLEEPY),
                WhenLosing.ESCAPE, 25, false), "but the file may say otherwise");
    }

    @Test
    void theAuthoredKindWinsThenARetreatBelowOfZero() {
        Map<Trait, Integer> bold = traits(90, 90, 10, 50), timid = traits(10, 10, 90, 50);
        assertEquals(WhenLosing.ESCAPE, EscapeRules.kind(null, bold, WhenLosing.ESCAPE, 25, false));
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.kind(null, timid, WhenLosing.LAST_STAND, 25, false));
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.kind(null, timid, null, 0, true), "\"never retreats\"");
        assertEquals(WhenLosing.ESCAPE, EscapeRules.kind(null, timid, null, 0, false), "a 0 the personality gave is"
                + " not the file's word");
        assertEquals(WhenLosing.ESCAPE, EscapeRules.kind(null, timid, WhenLosing.ESCAPE, 0, true), "the override"
                + " first");
        assertEquals(WhenLosing.ESCAPE, EscapeRules.kind(null, timid, null, 30, true));
    }

    @Test
    void anEscapingHeroStandsAtHomeAndBesideItsLeaderByLoyalty() {
        assertEquals(List.of(), EscapeRules.standsFor(69, null));
        assertEquals(List.of(StandsFor.HOME), EscapeRules.standsFor(70, null));
        assertEquals(List.of(StandsFor.HOME), EscapeRules.standsFor(84, null));
        assertEquals(List.of(StandsFor.HOME, StandsFor.LEADER), EscapeRules.standsFor(85, null));
        assertEquals(List.of(), EscapeRules.standsFor(100, List.of()), "[] is nowhere");
        assertEquals(List.of(StandsFor.LEADER), EscapeRules.standsFor(10, List.of(StandsFor.LEADER)));
        // a last stand stands everywhere: no list
        assertEquals(List.of(), EscapeRules.end(null, traits(90, 90, 10, 95), null, null, 25, false, 25).standsFor());
    }

    @Test
    void retreatBelowFromThePersonality() {
        assertEquals(25, EscapeRules.retreatBelow(25, 50, 50), "neutral traits keep the role's");
        assertEquals(28, EscapeRules.retreatBelow(25, 25, 50), "27.5 rounds up");
        assertEquals(45, EscapeRules.retreatBelow(40, 45, 70), "Yugen: 44.5 rounds up");
        assertEquals(23, EscapeRules.retreatBelow(25, 65, 45), "Natsuho: 22.5 rounds up");
        assertEquals(0, EscapeRules.retreatBelow(5, 100, 0), "never under 0");
        assertEquals(60, EscapeRules.retreatBelow(55, 0, 100), "never over 60");
        assertEquals(3, EscapeRules.roundHalfUp(2.5));
        assertEquals(2, EscapeRules.roundHalfUp(2.4999));
        assertEquals(15, EscapeRules.roundHalfUp(44.5 / 3), "14.83");
        assertEquals(8, EscapeRules.roundHalfUp(22.5 / 3), "7.5 rounds up");
    }

    @Test
    void fleeAtIsAThirdWithinEightAndFifteenUnderTheLastPhase() {
        assertEquals(8, EscapeRules.fleeAt(10, 25), "3.3: at least 8");
        assertEquals(8, EscapeRules.fleeAt(23, 25), "7.67");
        assertEquals(9, EscapeRules.fleeAt(28, 25));
        assertEquals(10, EscapeRules.fleeAt(29, 50), "9.67");
        assertEquals(15, EscapeRules.fleeAt(44, 25), "14.67, and 25 - 10 = 15");
        assertEquals(15, EscapeRules.fleeAt(60, 50), "20: at most 15");
        assertEquals(12, EscapeRules.fleeAt(60, 22), "the last phase less 10");
        assertEquals(5, EscapeRules.fleeAt(60, 12), "never under 5");
        assertEquals(5, EscapeRules.fleeAt(0, 1));
        assertEquals(15, EscapeRules.fleeAt(45, 100), "one phase: 100 - 10 does not bind");
    }

    @Test
    void theSliceHeroesEndAsTheirSheetsSay() {
        // SLICE_PLAN 3, "When a fight is lost" (temperament, traits from the sheet, when_losing, retreat_below)
        Object[][] heroes = {
                {"natsuho_komezaki", Temperament.TSUNDERE, traits(65, 65, 45, 70), WhenLosing.ESCAPE, 23,
                        "escapes under 8%, never from its home"},
                {"yugen_kagaribi", Temperament.SCHEMER, traits(45, 15, 70, 30), null, 45, "escapes under 15%"},
                {"benika_akagane", Temperament.BATTLE_MANIAC, traits(95, 55, 5, 55), null, 7, "fights to the death"},
                {"hinae_shirasuzu", Temperament.SWEET_NATURED, traits(30, 75, 60, 80), null, 44,
                        "escapes under 15%, never from its home"},
                {"kagenori_tsurumine", Temperament.STOIC, traits(50, 80, 65, 85), null, 28, "fights to the death"},
                {"tobimaru_usugumo", Temperament.LONE_WOLF, traits(55, 60, 60, 40), null, 32, "escapes under 11%"},
                {"ilsabet_frostmere", Temperament.RIGHTEOUS, traits(60, 95, 45, 80), null, 22, "fights to the death"},
                {"ambrose_nettlewick", Temperament.ECCENTRIC_GENIUS, traits(35, 50, 55, 50), null, 38,
                        "escapes under 13%"},
        };
        for (Object[] h : heroes) {
            @SuppressWarnings("unchecked")
            Map<Trait, Integer> tr = (Map<Trait, Integer>) h[2];
            EndOfFight e = EscapeRules.end((Temperament) h[1], tr, (WhenLosing) h[3], null, (int) h[4], false, 25);
            assertEquals(h[5], EscapeRules.sheetLine(e), (String) h[0]);
        }
        // Natsuho's traits alone would stand: 65 + 65 - 45 = 85
        assertEquals(WhenLosing.LAST_STAND, EscapeRules.byTraits(Temperament.TSUNDERE, traits(65, 65, 45, 70)));
    }

    @Test
    void theSheetLine() {
        assertEquals("fights to the death", EscapeRules.sheetLine(new EndOfFight(WhenLosing.LAST_STAND, List.of(), 9,
                28)));
        assertEquals("escapes under 12%", EscapeRules.sheetLine(new EndOfFight(WhenLosing.ESCAPE, List.of(), 12, 36)));
        assertEquals("escapes under 8%, never from its home", EscapeRules.sheetLine(new EndOfFight(WhenLosing.ESCAPE,
                List.of(StandsFor.HOME), 8, 23)));
        assertEquals("escapes under 8%, never from beside its leader", EscapeRules.sheetLine(new EndOfFight(
                WhenLosing.ESCAPE, List.of(StandsFor.LEADER), 8, 23)));
        assertEquals("escapes under 10%, never from its home or from beside its leader", EscapeRules.sheetLine(
                new EndOfFight(WhenLosing.ESCAPE, List.of(StandsFor.HOME, StandsFor.LEADER), 10, 30)));
    }

    @Test
    void noRuleTakesTheEnemysKind() {
        // the API has no parameter for who the enemy is (equal treatment: the same end against players and NPCs)
        for (java.lang.reflect.Method m : EscapeRules.class.getDeclaredMethods()) {
            for (java.lang.reflect.Parameter p : m.getParameters()) {
                String n = p.getType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                assertTrue(!n.contains("player") && !n.contains("entity"), m.getName() + " " + n);
            }
        }
    }
}
