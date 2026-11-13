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

    // ================================================================== the runtime decision (F3)

    private static final EndOfFight ESCAPER = new EndOfFight(WhenLosing.ESCAPE, List.of(), 12, 36);
    private static final EndOfFight HOME_ESCAPER = new EndOfFight(WhenLosing.ESCAPE, List.of(StandsFor.HOME), 8, 23);
    private static final EndOfFight LOYAL = new EndOfFight(WhenLosing.ESCAPE, List.of(StandsFor.HOME,
            StandsFor.LEADER), 10, 30);
    private static final EndOfFight STANDER = new EndOfFight(WhenLosing.LAST_STAND, List.of(), 9, 28);

    /** Afield, its heart standing, no leader near, no rebirth, escapes on, never escaped, at tick 100000. */
    private static EscapeRules.Context afield() {
        return new EscapeRules.Context(false, true, false, false, true, 100_000, 0, 600);
    }

    private static EscapeRules.Context ctx(boolean atHome, boolean heart, boolean leader, boolean rebirth,
                                           boolean enabled, long lastEscapeAt) {
        return new EscapeRules.Context(atHome, heart, leader, rebirth, enabled, 100_000, lastEscapeAt, 600);
    }

    @Test
    void underItsThresholdAnEscaperBreaksOffAndAStanderSaysItsLineOnce() {
        EscapeRules.Context c = afield();
        assertEquals(EscapeRules.Outcome.FIGHT_ON, EscapeRules.lowHealth(ESCAPER, 0.12, false, c), "12% is not under 12");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(ESCAPER, 0.1199, false, c));
        assertEquals(EscapeRules.Outcome.FIGHT_ON, EscapeRules.lowHealth(STANDER, 0.5, false, c));
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(STANDER, 0.05, false, c),
                "the first time under the flee_at it would have had");
        assertEquals(EscapeRules.Outcome.FIGHT_ON, EscapeRules.lowHealth(STANDER, 0.01, true, c), "once per fight");
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(STANDER, 0.001, false, c),
                "a last stand never escapes, at any health");
    }

    @Test
    void noEscapeInsideTheWindowWithEscapesOffOrBeforeAnUnspentRebirth() {
        long now = 100_000;
        assertTrue(EscapeRules.inWindow(now, now - 11_999, 600), "within 600 s of its last escape");
        assertTrue(!EscapeRules.inWindow(now, now - 12_000, 600), "600 s later the window is over");
        assertTrue(!EscapeRules.inWindow(now, 0, 600), "never escaped");
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(ESCAPER, 0.05, false,
                ctx(false, true, false, false, true, now - 6000)), "caught again inside the window: a last stand");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(ESCAPER, 0.05, false,
                ctx(false, true, false, false, true, now - 12_000)));
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(ESCAPER, 0.05, false,
                ctx(false, true, false, false, false, 0)), "heroes.escape = false: every hero fights to the death");
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(ESCAPER, 0.05, false,
                ctx(false, true, false, true, true, 0)), "an unspent rebirth first");
        // the window's config is clamped (120-1800 s)
        assertTrue(EscapeRules.inWindow(now, now - 2399, 10), "at least 120 s");
        assertEquals(1800, EscapeRules.windowSeconds(99_999));
        assertEquals(300, EscapeRules.awaySeconds(300));
        assertEquals(60, EscapeRules.awaySeconds(1));
        assertEquals(1200, EscapeRules.awaySeconds(5000));
    }

    @Test
    void theSituationRulesHoldAnEscaperAndEndWithTheirReason() {
        assertEquals(StandsFor.HOME, EscapeRules.heldBy(HOME_ESCAPER, ctx(true, true, false, false, true, 0)));
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(HOME_ESCAPER, 0.05, false,
                ctx(true, true, false, false, true, 0)), "it does not abandon its own people");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(HOME_ESCAPER, 0.05, false,
                ctx(false, true, false, false, true, 0)), "afield it runs");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(HOME_ESCAPER, 0.05, false,
                ctx(true, false, false, false, true, 0)), "the heart is gone: the stand ends");
        assertEquals(StandsFor.LEADER, EscapeRules.heldBy(LOYAL, ctx(false, true, true, false, true, 0)));
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(LOYAL, 0.05, false,
                ctx(false, true, false, false, true, 0)), "the leader fell or left: an escaper again");
        assertEquals(null, EscapeRules.heldBy(HOME_ESCAPER, ctx(false, true, true, false, true, 0)),
                "loyal to its home, not to its leader");
        assertEquals(null, EscapeRules.heldBy(STANDER, ctx(true, true, true, false, true, 0)),
                "a last stand needs no situation");
    }

    @Test
    void aLostRaidSendsAnEscaperHomeAndAStanderFinishesItsFight() {
        EscapeRules.Context c = afield();
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lostRaid(ESCAPER, true, false, c), "in the target");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lostRaid(ESCAPER, false, true, c), "fighting");
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.lostRaid(ESCAPER, false, false, c),
                "elsewhere it walks home with the party");
        assertEquals(EscapeRules.Outcome.FINISH_FIGHT, EscapeRules.lostRaid(STANDER, true, true, c));
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.lostRaid(STANDER, true, false, c));
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.lostRaid(ESCAPER, true, true,
                ctx(false, true, false, false, true, 100_000 - 100)), "inside the window: home with the party");
        assertEquals(EscapeRules.Outcome.FINISH_FIGHT, EscapeRules.lostRaid(LOYAL, true, true,
                ctx(false, true, true, false, true, 0)), "held beside its fighting leader");
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lostRaid(ESCAPER, true, true,
                ctx(false, true, false, true, true, 0)), "a rebirth holds only the health threshold");
    }

    @Test
    void aLostBaseEndsTheHomeStandAtOnceWhateverItsHealth() {
        EscapeRules.Context home = ctx(true, true, false, false, true, 0);
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lostBase(HOME_ESCAPER, true, home));
        assertEquals(EscapeRules.Outcome.FINISH_FIGHT, EscapeRules.lostBase(STANDER, true, home));
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.lostBase(STANDER, false, home));
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.lostBase(HOME_ESCAPER, true,
                ctx(true, true, false, false, true, 100_000 - 100)), "inside the window: to the next base on foot");
        assertEquals(EscapeRules.Outcome.FINISH_FIGHT, EscapeRules.lostBase(LOYAL, true,
                ctx(true, true, true, false, true, 0)), "its leader still fights beside it");
    }

    @Test
    void theRefugeIsNeverTheBaseItFlees() {
        List<EscapeRules.BaseOption> standing = List.of(new EscapeRules.BaseOption("home", 90),
                new EscapeRules.BaseOption("east", 200), new EscapeRules.BaseOption("west", 150));
        assertEquals("home", EscapeRules.refuge(EscapeRules.Trigger.LOW_HEALTH, "home", null, standing), "afield");
        assertEquals("home", EscapeRules.refuge(EscapeRules.Trigger.LOST_RAID, "home", "rival", standing));
        assertEquals("west", EscapeRules.refuge(EscapeRules.Trigger.LOW_HEALTH, "home", "home", standing),
                "a fight at home: the nearest other base");
        assertEquals("west", EscapeRules.refuge(EscapeRules.Trigger.LOST_BASE, "home", "home",
                standing.subList(1, 3)), "home fell: the nearest standing base");
        assertEquals(null, EscapeRules.refuge(EscapeRules.Trigger.LOW_HEALTH, "home", "home",
                List.of(new EscapeRules.BaseOption("home", 0))), "no other base: the wild");
        assertEquals("east", EscapeRules.refuge(EscapeRules.Trigger.LOW_HEALTH, "home", null,
                List.of(new EscapeRules.BaseOption("east", 300))), "its home has fallen");
        assertEquals(null, EscapeRules.refuge(EscapeRules.Trigger.LOST_BASE, "home", "home", List.of()));
    }

    @Test
    void anEscapeAlwaysEndsAndCannotLoop() {
        // cornered for 3 s: it fights back; a way opens: it runs again
        assertTrue(!EscapeRules.cornered(59));
        assertTrue(EscapeRules.cornered(60));
        // over at the refuge's walls or 10 s with no pursuer within 48 blocks
        assertTrue(EscapeRules.escapeOver(true, 0));
        assertTrue(!EscapeRules.escapeOver(false, 199));
        assertTrue(EscapeRules.escapeOver(false, 200));
        // away 300 s and above half health
        long at = 50_000;
        assertTrue(!EscapeRules.mayReturn(at + 5999, at, 300, 0.9));
        assertTrue(!EscapeRules.mayReturn(at + 6000, at, 300, 0.5), "above half, not at half");
        assertTrue(EscapeRules.mayReturn(at + 6000, at, 300, 0.51));
        // the sequence: escape at t0, back at t0 + 300 s, beaten again at t0 + 400 s: a last stand, not a second run
        long t0 = 200_000;
        EscapeRules.Context again = new EscapeRules.Context(false, true, false, false, true, t0 + 8000, t0, 600);
        assertEquals(EscapeRules.Outcome.LAST_STAND_BEAT, EscapeRules.lowHealth(ESCAPER, 0.05, false, again));
        EscapeRules.Context later = new EscapeRules.Context(false, true, false, false, true, t0 + 12_000, t0, 600);
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.lowHealth(ESCAPER, 0.05, false, later),
                "a new lost fight after the window");
    }

    @Test
    void anHonourableHeroLetsAnEscaperGo() {
        assertTrue(EscapeRules.heroPursues(59));
        assertTrue(!EscapeRules.heroPursues(60));
        assertTrue(!EscapeRules.heroPursues(95));
    }

    // ================================================================== F5: the rest of MOBILITY_AND_WORK 7.7

    @Test
    void decideDispatchesEachTrigger() {
        EscapeRules.Context c = afield();
        assertEquals(EscapeRules.Outcome.ESCAPE, EscapeRules.decide(ESCAPER, EscapeRules.Trigger.LOW_HEALTH, 0.05,
                false, false, true, c));
        assertEquals(EscapeRules.Outcome.FIGHT_ON, EscapeRules.decide(ESCAPER, EscapeRules.Trigger.LOW_HEALTH, 0.5,
                false, false, true, c));
        assertEquals(EscapeRules.Outcome.GO_ON_FOOT, EscapeRules.decide(ESCAPER, EscapeRules.Trigger.LOST_RAID, 0.9,
                false, false, false, c));
        assertEquals(EscapeRules.Outcome.FINISH_FIGHT, EscapeRules.decide(STANDER, EscapeRules.Trigger.LOST_BASE, 0.9,
                false, false, true, ctx(true, true, false, false, true, 0)));
        // whoever it fights: the context has no field for the enemy, so a player and NPCs give the same outcome
        for (EscapeRules.Trigger t : EscapeRules.Trigger.values()) {
            for (EndOfFight e : List.of(ESCAPER, HOME_ESCAPER, LOYAL, STANDER)) {
                EscapeRules.Outcome a = EscapeRules.decide(e, t, 0.05, false, true, true, c);
                EscapeRules.Outcome b = EscapeRules.decide(e, t, 0.05, false, true, true, c);
                assertEquals(a, b);
            }
        }
    }

    @Test
    void aScoutsStepAsideIsAnEscapesMovementOnly() throws IOException {
        // ScoutJob's rules: soldiers or badly hurt among monsters: home; a monster about or in the way: aside and on
        String scout = Files.readString(Path.of("src/main/java/com/factionclash/work/ScoutJob.java"));
        assertTrue(scout.contains("BADLY_HURT = " + (float) EscapeRules.SCOUT_BADLY_HURT + "F"), "ScoutJob's 40%");
        assertEquals(EscapeRules.Departure.SCOUT_RETREAT, EscapeRules.scoutDeparture(false, true, 1, false, false));
        assertEquals(EscapeRules.Departure.SCOUT_RETREAT, EscapeRules.scoutDeparture(false, false, 0.39, true,
                false));
        assertEquals(EscapeRules.Departure.SCOUT_STEP_ASIDE, EscapeRules.scoutDeparture(false, false, 0.4, true,
                false));
        assertEquals(EscapeRules.Departure.SCOUT_STEP_ASIDE, EscapeRules.scoutDeparture(false, false, 0.1, false,
                true), "a monster in the way, none about");
        assertEquals(null, EscapeRules.scoutDeparture(false, false, 1, false, false));
        assertEquals(null, EscapeRules.scoutDeparture(true, true, 0.1, true, true), "engaged: a fight like any other");
        for (EscapeRules.Departure d : EscapeRules.Departure.values()) {
            assertTrue(d.usesEscapeMovement(), "any mobility: " + d);
            assertEquals(d == EscapeRules.Departure.ESCAPE, d.isEscape(), "no bar, window, stay away, morale or line");
        }
        assertTrue(EscapeRules.Departure.SCOUT_STEP_ASIDE.goesOnWithItsRound());
        assertTrue(!EscapeRules.Departure.SCOUT_RETREAT.goesOnWithItsRound());
        // the movement an escape uses: the rules of 5.1 a fight keeps are lifted, the counterplay is not
        MobilityRules.Situation esc = MobilityRules.Situation.ESCAPE;
        for (MobilityRules.Rule r : List.of(MobilityRules.Rule.AIR_BUDGET, MobilityRules.Rule.REACH_IN_THE_AIR,
                MobilityRules.Rule.UNFOLLOWABLE_PLACES, MobilityRules.Rule.COME_BACK)) {
            assertTrue(!MobilityRules.holds(r, esc), r.name());
        }
        for (MobilityRules.Rule r : List.of(MobilityRules.Rule.NO_TELEPORT, MobilityRules.Rule.AIR_POISE,
                MobilityRules.Rule.NO_CARRYING, MobilityRules.Rule.HAZARDS)) {
            assertTrue(MobilityRules.holds(r, esc), r.name());
        }
        assertTrue(MobilityRules.travelBonusesOn(esc), "an escape is travel");
        assertTrue(!MobilityRules.mayAttack(esc, false) && MobilityRules.mayAttack(esc, true), "cornered: fights back");
        // it stays a boss: the per-hit cap and poise have no escaping input, so a hit lands as in the fight
        assertTrue(MobilityRules.bossRulesHold(esc));
        BossRules.Capped hit = BossRules.capHit(100, 400, 12);
        assertEquals(48, hit.landed(), 1e-9, "12% of 400, escaping or not");
        assertEquals(52, hit.toPoise(), 1e-9, "the excess to poise");
    }

    @Test
    void pursuersAreTheFledFightsEnemiesOfEveryKind() {
        assertTrue(EscapeRules.pursues(true, false, false, 0), "a player or a soldier engaged in the fight");
        assertTrue(EscapeRules.pursues(false, true, false, 0), "anything whose target it is");
        assertTrue(!EscapeRules.pursues(false, false, false, 0));
        assertTrue(EscapeRules.pursues(true, false, true, 59));
        assertTrue(!EscapeRules.pursues(true, true, true, 60), "an honourable hero lets it go");
        // the wild: 64 blocks from the fight for its stay-away time
        long at = 10_000;
        assertTrue(EscapeRules.keepFarther(40, at + 100, at, 300));
        assertTrue(!EscapeRules.keepFarther(64, at + 100, at, 300));
        assertTrue(!EscapeRules.keepFarther(10, at + 6000, at, 300), "after its stay away it walks home");
    }

    @Test
    void anEscapeGivesNoRewardsAndCostsMoraleAtHome() {
        assertTrue(!EscapeRules.rewards(EscapeRules.End.ESCAPED), "nothing drops, no orbs, no crest, no fragment");
        assertTrue(EscapeRules.rewards(EscapeRules.End.DIED), "killed during the escape: it has died");
        // morale: -4 at its home for an escape, -6 everywhere for a leader's; -8 and -20 for deaths; nothing elsewhere
        assertEquals(-4, EscapeRules.moraleDelta(EscapeRules.End.ESCAPED, false, true, true));
        assertEquals(0, EscapeRules.moraleDelta(EscapeRules.End.ESCAPED, false, true, false));
        assertEquals(-6, EscapeRules.moraleDelta(EscapeRules.End.ESCAPED, true, true, false));
        assertEquals(-6, EscapeRules.moraleDelta(EscapeRules.End.ESCAPED, true, true, true));
        assertEquals(-8, EscapeRules.moraleDelta(EscapeRules.End.DIED, false, true, true));
        assertEquals(-20, EscapeRules.moraleDelta(EscapeRules.End.DIED, true, true, false));
        for (EscapeRules.End e : EscapeRules.End.values()) {
            for (boolean leader : new boolean[]{false, true}) {
                assertEquals(0, EscapeRules.moraleDelta(e, leader, false, false), "nothing for the side it fled");
            }
        }
        // the war council while it is away: no raid party, no defence but its refuge's
        assertTrue(EscapeRules.countsInDefence("west", "west"));
        assertTrue(!EscapeRules.countsInDefence("home", "west"));
        assertTrue(!EscapeRules.countsInDefence("home", null), "in the wild: no base's defence");
        assertTrue(!EscapeRules.inRaidParty(true));
        assertTrue(EscapeRules.inRaidParty(false));
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
