package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Band;
import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.MoveSelector.Pick;
import com.factionclash.logic.hero.MoveSelector.Posture;
import com.factionclash.logic.hero.MoveSelector.Source;
import com.factionclash.logic.hero.Sense.TargetState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveSelector} (HEROES_V2 6.3, 6.8, 14.3): bands, novelty, posture, prefer, the score, seeded picks, the
 * opening gap (no move, reaction answer or ultimate before it), the gaps by phase and enrage, the global cooldown,
 * charges and refunds, followup strings, reactions that read any target with one roll per occurrence, the arena and
 * footing filters, ultimates and the opener, no token check, and the frames' class minimums.
 */
class MoveSelectorTest {
    private static final double EPS = 1e-9;
    private static final String DMG = "{\"type\": \"damage\", \"amount\": 2}";
    private static final String BOSS3 = "\"boss\": {\"weak_points\": [\"back\"], \"phases\": [{\"at\": 100}, {\"at\": 60},"
            + " {\"at\": 25}]";

    static String move(String id, String form, String extra) {
        String head = "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"form\": \"" + form + "\"";
        if (extra.contains("\"effects\"")) {
            return head + ", " + extra + "}";
        }
        return head + (extra.isEmpty() ? "" : ", " + extra) + ", \"effects\": [" + DMG + "]}";
    }

    static HeroDef.V2 kit(String boss, String... moves) {
        String more = "\"moves\": [" + String.join(", ", moves) + "]";
        if (boss != null) {
            more += ", " + boss;
        }
        return V2Files.def(V2Files.hero2(more)).v2();
    }

    static HeroDef.V2 kitWith(String extraTop, String boss, String... moves) {
        String more = "\"moves\": [" + String.join(", ", moves) + "]" + (boss != null ? ", " + boss : "")
                + ", " + extraTop;
        return V2Files.def(V2Files.hero2(more)).v2();
    }

    static Sense at(double distance) {
        return new Sense().target(new TargetState().distance(distance));
    }

    /** A target worth an ultimate (v1 big_fight: 40 max health or more). */
    static Sense big(double distance) {
        return new Sense().target(new TargetState().distance(distance).health(1, 40));
    }

    /** Runs checks every 5 ticks from {@code from} until a pick (or {@code until}); returns the pick's tick. */
    static long firstPick(MoveSelector s, long from, long until, Sense sense, Pick[] out) {
        for (long t = from; t <= until; t += MoveSelector.CHECK_TICKS) {
            Pick p = s.check(t, sense);
            if (p != null) {
                out[0] = p;
                return t;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ bands, score, posture

    @Test
    void bandsAndBandFit() {
        assertEquals(Band.CLOSE, MoveSelector.bandOf(4));
        assertEquals(Band.MID, MoveSelector.bandOf(4.01));
        assertEquals(Band.MID, MoveSelector.bandOf(10));
        assertEquals(Band.FAR, MoveSelector.bandOf(10.5));
        assertEquals(Band.FAR, MoveSelector.bandOf(40));
        assertEquals(1, MoveSelector.bandFit(List.of(Band.CLOSE), Band.CLOSE), EPS);
        assertEquals(0.25, MoveSelector.bandFit(List.of(Band.CLOSE), Band.MID), EPS, "an adjacent band");
        assertEquals(0, MoveSelector.bandFit(List.of(Band.CLOSE), Band.FAR), EPS);
        assertEquals(0.25, MoveSelector.bandFit(List.of(Band.MID, Band.FAR), Band.CLOSE), EPS);
        assertEquals(1, MoveSelector.bandFit(List.of(), Band.FAR), EPS);
    }

    @Test
    void theScoreIsWeightTimesBandPreferNoveltyAndPosture() {
        HeroDef.V2 k = kit(null, move("slash", "melee", "\"weight\": 6, \"band\": [\"close\"], \"prefer_when\":"
                        + " {\"condition\": \"target_blocking\"}"),
                move("burst", "nova", "\"weight\": 4, \"radius\": 4"),
                move("pool", "zone", "\"weight\": 5, \"zone_at\": \"self\", \"radius\": 3"));
        MoveSelector neutral = new MoveSelector(k, 50, 50, false, 1);
        Move slash = k.move("slash"), burst = k.move("burst"), pool = k.move("pool");
        assertEquals(6, neutral.score(slash, at(2)), EPS);
        assertEquals(12, neutral.score(slash, at(2).target(new TargetState().distance(2).blocking(true))), EPS,
                "prefer_when holds: x2");
        assertEquals(1.5, neutral.score(slash, at(6)), EPS, "mid is next to close: x0.25");
        assertEquals(0, neutral.score(slash, at(15)), EPS, "far: x0");
        MoveSelector bold = new MoveSelector(k, 100, 0, false, 1);
        assertEquals(6 * 1.3, bold.score(slash, at(2)), 1e-9, "offence: 1 + 0.6 (a - 0.5)");
        assertEquals(5 * 0.7, bold.score(pool, at(2)), 1e-9, "zoning: 1 + 0.6 (c - 0.5)");
        assertEquals(Posture.OFFENSIVE, MoveSelector.posture(burst));
        assertEquals(Posture.ZONING, MoveSelector.posture(pool));
        // novelty: the last move scores 0 (not repeatable), one of the last 3 x0.35
        Sense s = at(2);
        neutral.fightStarted(0);
        Pick[] p = new Pick[1];
        firstPick(neutral, 0, 200, s, p);
        Move first = p[0].move();
        assertEquals(0, neutral.score(first, s), EPS, "the last move");
        for (Move m : List.of(slash, burst, pool)) {
            if (m != first) {
                assertTrue(neutral.score(m, s) > 0);
            }
        }
    }

    @Test
    void novelty() {
        HeroDef.V2 k = kit(null, move("a1", "melee", "\"weight\": 5"), move("b1", "nova", "\"weight\": 5"),
                move("c1", "melee", "\"weight\": 5, \"repeatable\": true"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 3);
        Sense s = at(2);
        m.fightStarted(0);
        List<String> order = new ArrayList<>();
        long t = 0;
        for (int i = 0; i < 6; i++) {
            Pick[] p = new Pick[1];
            t = firstPick(m, t, t + 400, s, p);
            order.add(p[0].move().id());
            m.moveEnded(t + 1, 1, false);
            t += 1;
        }
        for (int i = 1; i < order.size(); i++) {
            if (order.get(i).equals(order.get(i - 1))) {
                assertEquals("c1", order.get(i), "only a repeatable move may come twice in a row: " + order);
            }
        }
        // a repeatable last move still counts as recent
        MoveSelector r = new MoveSelector(kit(null, move("c1", "melee", "\"weight\": 5, \"repeatable\": true")), 50,
                50, false, 3);
        r.fightStarted(0);
        Pick[] p = new Pick[1];
        firstPick(r, 0, 200, s, p);
        assertEquals(5 * 0.35, r.score(p[0].move(), s), 1e-9);
    }

    // ------------------------------------------------------------------ the opening gap (6.3)

    @Test
    void nothingStartsBeforeTheOpeningGapSoNothingLandsBeforeNineteenTenths() {
        HeroDef.V2 k = kitWith("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"line\":"
                        + " \"Now!\", \"effects\": [" + DMG + "]}", null,
                move("slash", "melee", "\"class\": \"light\", \"weight\": 5"),
                move("smash", "melee", "\"class\": \"heavy\", \"weight\": 5"));
        long minLight = Long.MAX_VALUE, minHeavy = Long.MAX_VALUE, minUlt = Long.MAX_VALUE;
        for (long seed = 0; seed < 200; seed++) {
            for (int opener = 0; opener < 2; opener++) {
                MoveSelector m = new MoveSelector(k, 50, 50, opener == 1, seed);
                m.fightStarted(1000);
                assertTrue(m.gapEndsAt() >= 1030 && m.gapEndsAt() <= 1050, "a full phase-1 gap: " + m.gapEndsAt());
                Pick[] p = new Pick[1];
                Sense s = big(2).energy(100);
                long t = firstPick(m, 1000, 1300, s, p);
                long lands = t - 1000 + HeroRules.ticks(MoveSelector.frames(p[0].move(), 1.0).windup());
                switch (p[0].move().cls()) {
                    case LIGHT -> minLight = Math.min(minLight, lands);
                    case HEAVY -> minHeavy = Math.min(minHeavy, lands);
                    default -> minUlt = Math.min(minUlt, lands);
                }
            }
        }
        assertTrue(minLight >= 38, "1.9 s: " + minLight);
        assertTrue(minHeavy >= 46, "2.3 s: " + minHeavy);
        assertTrue(minUlt < Long.MAX_VALUE && minHeavy < Long.MAX_VALUE, "each class was picked");
        assertTrue(minUlt >= 60, "3.0 s: " + minUlt);
    }

    @Test
    void aReactionAnswerWaitsForTheOpeningGapAndTheIntro() {
        HeroDef.V2 k = kit(V2Files.BOSS.replace("]}", "], \"reactions\": [{\"when\": \"target_blocking\", \"move\":"
                        + " \"smash\", \"chance\": 100, \"cooldown\": 10}]}"),
                move("slash", "melee", "\"weight\": 5"), move("smash", "melee", "\"class\": \"heavy\", \"weight\": 0"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 5);
        m.fightStarted(0);
        Sense s = new Sense().target(new TargetState().distance(2).blocking(true));
        assertNull(m.check(0, s), "the shield is up at once, the answer waits");
        assertNotNull(m.pendingReaction());
        Pick[] p = new Pick[1];
        long t = firstPick(m, 5, 300, s, p);
        assertTrue(t >= 30);
        assertEquals("smash", p[0].move().id());
        assertEquals(Source.REACTION, p[0].source());
        // an intro moves the opening gap to its end
        MoveSelector i = new MoveSelector(k, 50, 50, false, 5);
        i.fightStarted(0);
        i.introEnded(25);
        assertTrue(i.gapEndsAt() >= 55, "from the end of the intro");
        assertEquals(-1, firstPick(i, 0, 50, s, p));
    }

    // ------------------------------------------------------------------ gaps and the global cooldown

    @Test
    void theGapByPhaseAndEnrage() {
        HeroDef.V2 k = kit(null, move("slash", "melee", "\"weight\": 5"));
        int[][] range = {{30, 50}, {20, 40}, {12, 30}};
        for (long seed = 0; seed < 300; seed++) {
            MoveSelector m = new MoveSelector(k, 50, 50, false, seed);
            for (int ph = 1; ph <= 3; ph++) {
                m.moveEnded(1000, ph, false);
                long g = m.gapEndsAt() - 1000;
                assertTrue(g >= range[ph - 1][0] && g <= range[ph - 1][1], "phase " + ph + ": " + g);
                m.moveEnded(1000, ph, true);
                long e = m.gapEndsAt() - 1000;
                assertTrue(e >= Math.round(range[ph - 1][0] * 0.7) && e <= Math.round(range[ph - 1][1] * 0.7),
                        "enraged x0.7, phase " + ph + ": " + e);
            }
            m.fightStarted(1000);
            assertTrue(m.gapEndsAt() - 1000 >= 30, "the opening gap is never shortened");
        }
    }

    @Test
    void theGlobalCooldownIsOneSecondBetweenStarts() {
        HeroDef.V2 k = kit(null, move("a1", "melee", "\"weight\": 5"), move("b1", "melee", "\"weight\": 5"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 9);
        Sense s = at(2);
        m.fightStarted(0);
        Pick[] p = new Pick[1];
        long t = firstPick(m, 0, 200, s, p);
        m.moveEnded(t + 1, 3, true); // cut short: the phase-3 enraged gap can end before 1 s has passed
        for (long u = t + 5; u < t + 20; u += 5) {
            assertNull(m.check(u, s), "within 1 s of the last start");
        }
        assertNotNull(m.check(t + 35, s));
    }

    // ------------------------------------------------------------------ charges and refunds

    @Test
    void cooldownsChargesAndRefunds() {
        HeroDef.V2 k = kit(null, move("jab", "melee", "\"weight\": 5, \"repeatable\": true, \"charges\": 2,"
                + " \"cooldown\": 12"));
        Move jab = k.move("jab");
        long cd = HeroRules.ticks(jab.ability().cooldown());
        assertTrue(cd >= 200, "the file's cooldown (or its floor): " + cd);
        assertEquals(2, jab.ability().charges());
        MoveSelector m = new MoveSelector(k, 50, 50, false, 2);
        Sense s = at(2);
        m.fightStarted(0);
        Pick[] p = new Pick[1];
        long t1 = firstPick(m, 0, 200, s, p);
        assertEquals(1, m.charges(jab, t1, 0));
        m.moveEnded(t1 + 1, 1, false);
        long t2 = firstPick(m, t1 + 1, t1 + 200, s, p);
        assertTrue(t2 > 0);
        assertEquals(0, m.charges(jab, t2, 0));
        m.moveEnded(t2 + 1, 1, false);
        assertEquals(-1, firstPick(m, t2 + 1, t1 + cd - 1, s, p), "no charge until the first comes back");
        assertEquals(1, m.charges(jab, t1 + cd, 0), "one cooldown after the first use");
        assertEquals(2, m.charges(jab, t1 + 2 * cd, 0));
        // a cancelled start is not spent; an interrupted wind-up refunds half the cooldown
        MoveSelector c = new MoveSelector(k, 50, 50, false, 2);
        c.fightStarted(0);
        long u = firstPick(c, 0, 200, s, p);
        c.cancelled(jab, u);
        assertEquals(2, c.charges(jab, u, 0));
        c.moveEnded(u + 1, 1, false);
        long v = firstPick(c, u + 1, u + 200, s, p);
        c.interrupted(jab, v);
        assertEquals(1, c.charges(jab, v + cd / 2 - 1, 0));
        assertEquals(2, c.charges(jab, v + cd / 2, 0), "half the cooldown refunded");
    }

    // ------------------------------------------------------------------ followups (6.3)

    @Test
    void followupsFormStringsOfAtMostFour() {
        HeroDef.V2 k = kit(null, move("rush", "dash", "\"weight\": 5, \"range\": 6, \"followups\": [{\"move\":"
                        + " \"cut\", \"chance\": 100}]"),
                move("cut", "melee", "\"weight\": 0"),
                move("zoom", "melee", "\"weight\": 0, \"repeatable\": true, \"followups\": [{\"move\": \"zoom\","
                        + " \"chance\": 100}]"),
                move("hop", "melee", "\"weight\": 0, \"repeatable\": true, \"followups\": [{\"move\": \"hop\","
                        + " \"chance\": 100, \"max\": 2}]"),
                move("never", "melee", "\"weight\": 0, \"followups\": [{\"move\": \"cut\", \"chance\": 0}]"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 4);
        m.fightStarted(0);
        Pick[] p = new Pick[1];
        long t = firstPick(m, 0, 200, at(5), p);
        assertEquals("rush", p[0].move().id(), "the only move with weight");
        assertNull(m.followup(t + 10, p[0].move(), at(8)), "the cut needs its reach after the dash");
        Pick f = m.followup(t + 10, p[0].move(), at(2));
        assertEquals("cut", f.move().id());
        assertEquals(Source.FOLLOWUP, f.source());
        assertEquals(2, m.stringLength());
        assertNull(m.followup(t + 20, k.move("never"), at(2)), "chance 0");
        // a repeatable move follows itself: at most 4 moves in the string, repeats included, or max repeats
        HeroDef.V2 zk = kit(null, move("zoom", "melee", "\"weight\": 5, \"repeatable\": true, \"followups\":"
                + " [{\"move\": \"zoom\", \"chance\": 100}]"));
        assertEquals(4, runString(zk), "zoomies at most 4 times in a row");
        HeroDef.V2 hk = kit(null, move("hop", "melee", "\"weight\": 5, \"repeatable\": true, \"followups\":"
                + " [{\"move\": \"hop\", \"chance\": 100, \"max\": 2}]"));
        assertEquals(3, runString(hk), "max 2: three hops");
    }

    /** Picks the kit's one move and follows it while followups start; returns how many times it ran. */
    private static int runString(HeroDef.V2 k) {
        MoveSelector m = new MoveSelector(k, 50, 50, false, 4);
        m.fightStarted(0);
        Pick[] p = new Pick[1];
        long t = firstPick(m, 0, 200, at(2), p);
        int runs = 1;
        Pick f = m.followup(t + 10, p[0].move(), at(2));
        while (f != null) {
            runs++;
            assertEquals(runs, m.stringLength());
            t += 10;
            f = m.followup(t + 10, f.move(), at(2));
        }
        m.moveEnded(t + 30, 1, false);
        assertEquals(0, m.stringLength());
        return runs;
    }

    // ------------------------------------------------------------------ reactions (6.8)

    @Test
    void reactionsReplaceTheNextPickAndWaitForAPhaseThatHasTheirMove() {
        String boss = BOSS3 + ", \"reactions\": [{\"when\": \"target_blocking\", \"move\": \"splitter\", \"chance\":"
                + " 100, \"cooldown\": 10}]}";
        HeroDef.V2 k = kit(boss, move("slash", "melee", "\"weight\": 5"),
                move("splitter", "melee", "\"class\": \"heavy\", \"weight\": 0, \"phases\": [2, 3]"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 6);
        m.fightStarted(0);
        Sense blocking = new Sense().target(new TargetState().distance(2).blocking(true));
        Pick[] p = new Pick[1];
        long t = firstPick(m, 0, 200, blocking, p);
        assertEquals("slash", p[0].move().id(), "phase 1 has no splitter: the reaction waits (kit-gaps 7)");
        m.moveEnded(t + 20, 1, false);
        blocking.phase(2);
        t = firstPick(m, t + 20, t + 300, blocking, p);
        assertEquals("splitter", p[0].move().id());
        assertEquals(Source.REACTION, p[0].source());
        assertEquals("target_blocking", p[0].reaction().when().type().id());
        m.moveEnded(t + 20, 2, false);
        long next = firstPick(m, t + 20, t + 199, blocking, p);
        assertEquals("slash", p[0].move().id(), "the reaction's cooldown: 10 s");
        assertTrue(next > 0);
    }

    @Test
    void eachOccurrenceGetsOneRoll() {
        String boss = V2Files.BOSS.replace("]}", "], \"reactions\": [{\"when\": \"target_eating\", \"move\":"
                + " \"rush\", \"chance\": 50, \"cooldown\": 30}]}");
        HeroDef.V2 k = kit(boss, move("slash", "melee", "\"weight\": 5"), move("rush", "melee", "\"weight\": 0"));
        int answered = 0;
        for (long seed = 0; seed < 400; seed++) {
            MoveSelector m = new MoveSelector(k, 50, 50, false, seed);
            m.fightStarted(0);
            Sense eating = new Sense().target(new TargetState().distance(2).eating(true));
            Pick[] p = new Pick[1];
            firstPick(m, 0, 300, eating, p);
            answered += p[0].move().id().equals("rush") ? 1 : 0;
        }
        // six checks while the gap runs: one roll would answer about half; a roll per check about 98%
        assertTrue(answered > 400 * 0.35 && answered < 400 * 0.65, "one roll per occurrence: " + answered);
    }

    @Test
    void aShieldFromAnNpcOrAPlayerDrawsTheSameAnswers() {
        String boss = V2Files.BOSS.replace("]}", "], \"reactions\": [{\"when\": \"target_blocking\", \"move\":"
                + " \"bash\", \"chance\": 60, \"cooldown\": 8}]}");
        HeroDef.V2 k = kit(boss, move("slash", "melee", "\"weight\": 5"), move("poke", "melee", "\"weight\": 3"),
                move("bash", "melee", "\"class\": \"heavy\", \"weight\": 0"));
        for (long seed = 0; seed < 50; seed++) {
            List<String> a = run(k, seed, "an NPC soldier"), b = run(k, seed, "a mock player");
            assertEquals(a, b, "the same seed, the same picks");
        }
    }

    /** A fight against a target that raises its shield every other second; the Sense has no field for who it is. */
    private static List<String> run(HeroDef.V2 k, long seed, String who) {
        assertFalse(who.isEmpty());
        MoveSelector m = new MoveSelector(k, 50, 50, false, seed);
        m.fightStarted(0);
        List<String> picks = new ArrayList<>();
        for (long t = 0; t < 2400; t += 5) {
            Sense s = new Sense().target(new TargetState().distance(2).blocking((t / 40) % 2 == 0));
            Pick p = m.check(t, s);
            if (p != null) {
                picks.add(t + ":" + p.move().id());
                m.moveEnded(t + 30, 1, false);
            }
        }
        return picks;
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void footingArenaAndControlFilters() {
        HeroDef.V2 k = kit(null, move("rush", "dash", "\"weight\": 5, \"range\": 6"),
                move("hop", "leap", "\"weight\": 5, \"range\": 6"),
                move("zap", "shot", "\"weight\": 5, \"range\": 12"),
                move("guard", "self", "\"weight\": 5, \"effects\": [{\"type\": \"cleanse\"}]"),
                move("lift", "melee", "\"weight\": 0, \"effects\": [{\"type\": \"knockback\", \"strength\": 1, \"up\":"
                        + " 1}]"));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 1);
        Move rush = k.move("rush"), hop = k.move("hop"), zap = k.move("zap"), guard = k.move("guard");
        Sense s = at(5);
        assertTrue(m.eligible(rush, 0, s) && m.eligible(hop, 0, s) && m.eligible(zap, 0, s) && m.eligible(guard, 0, s));
        s.inWater = true;
        assertFalse(m.eligible(rush, 0, s), "no dash while swimming");
        assertFalse(m.eligible(hop, 0, s), "no leap while swimming");
        s.inWater = false;
        s.dashPathHazard = true;
        assertFalse(m.eligible(rush, 0, s), "water or lava along the path");
        assertTrue(m.eligible(hop, 0, s));
        s.dashPathHazard = false;
        s.lowCeiling = true;
        assertFalse(m.eligible(hop, 0, s), "a ceiling of 3 blocks: no leap");
        assertTrue(m.eligible(rush, 0, s));
        s.lowCeiling = false;
        s.airborne = true;
        assertTrue(m.eligible(zap, 0, s), "light ranged moves from the air");
        assertFalse(m.eligible(rush, 0, s));
        assertFalse(m.eligible(guard, 0, s));
        s.airborne = false;
        s.climbing = true;
        assertFalse(m.eligible(zap, 0, s), "no move starts while clinging");
        s.climbing = false;
        assertFalse(m.eligible(zap, 0, at(13)), "out of reach");
        assertFalse(m.eligible(zap, 0, new Sense().target(new TargetState().distance(5).visible(false))), "unseen");
        Sense none = new Sense();
        none.target.present = false;
        assertTrue(m.eligible(guard, 0, none), "a self move needs no target");
        assertFalse(m.eligible(zap, 0, none));
        // control: nothing is asked while stunned, silenced or escaping (a cornered hero asks again)
        for (int i = 0; i < 3; i++) {
            Sense c = at(5);
            c.stunned = i == 0;
            c.silenced = i == 1;
            c.escaping = i == 2;
            m.fightStarted(0);
            assertEquals(-1, firstPick(m, 0, 300, c, new Pick[1]));
            assertNull(m.followup(10, rush, c));
        }
    }

    @Test
    void theUltimateNeedsFullEnergyAndItsGapAndTheOpenerFiresIt() {
        HeroDef.V2 k = kitWith("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"weight\": 5,"
                + " \"line\": \"Now!\", \"effects\": [" + DMG + "]}", null, move("slash", "melee", "\"weight\": 0"));
        Move big = k.ultimate();
        assertTrue(big.ability().numbers().minGap() >= 20);
        MoveSelector m = new MoveSelector(k, 50, 50, false, 1);
        m.fightStarted(0);
        Pick[] p = new Pick[1];
        assertEquals(-1, firstPick(m, 0, 300, big(2).energy(99), p), "99 energy: nothing (slash has weight 0)");
        long t = firstPick(m, 300, 600, big(2).energy(100), p);
        assertEquals("big", p[0].move().id());
        m.moveEnded(t + 1, 1, false);
        long gap = HeroRules.ticks(big.ability().numbers().minGap());
        assertEquals(-1, firstPick(m, t + 1, t + gap - 1, big(2).energy(100), p), "its least gap after the last");
        assertEquals(0, m.charges(big, t + gap - 1, 100));
        assertEquals(1, m.charges(big, t + gap, 100), "ready again (novelty still keeps it from coming twice in a row)");
        assertEquals(0, m.charges(big, t + gap, 99), "and only with full energy");
        // the chuunibyou's rule: the first move of a fight is the ultimate when it is ready
        HeroDef.V2 k2 = kitWith("\"ultimate\": {\"id\": \"big\", \"name\": \"Big\", \"form\": \"nova\", \"weight\": 1,"
                + " \"line\": \"Now!\", \"effects\": [" + DMG + "]}", null, move("slash", "melee", "\"weight\": 10"));
        for (long seed = 0; seed < 20; seed++) {
            MoveSelector o = new MoveSelector(k2, 50, 50, true, seed);
            o.fightStarted(0);
            firstPick(o, 0, 300, big(2).energy(100), p);
            assertEquals(Source.OPENER, p[0].source());
            assertEquals("big", p[0].move().id());
        }
    }

    // ------------------------------------------------------------------ seeds, tokens

    @Test
    void picksAreSeededAndWeighted() {
        HeroDef.V2 k = kit(null, move("a1", "melee", "\"weight\": 9"), move("b1", "melee", "\"weight\": 1"));
        Map<String, Integer> count = new HashMap<>();
        for (long seed = 0; seed < 400; seed++) {
            MoveSelector m = new MoveSelector(k, 50, 50, false, seed);
            m.fightStarted(0);
            Pick[] p = new Pick[1];
            firstPick(m, 0, 300, at(2), p);
            count.merge(p[0].move().id(), 1, Integer::sum);
            MoveSelector again = new MoveSelector(k, 50, 50, false, seed);
            again.fightStarted(0);
            Pick[] q = new Pick[1];
            firstPick(again, 0, 300, at(2), q);
            assertEquals(p[0].move().id(), q[0].move().id(), "the same seed, the same pick");
        }
        assertTrue(count.get("a1") > 300 && count.getOrDefault("b1", 0) > 15, "about 9 to 1: " + count);
    }

    @Test
    void threeHeroesPressOneTargetWithNoTokens() {
        HeroDef.V2 k = kit(null, move("slash", "melee", "\"class\": \"heavy\", \"weight\": 5"));
        Sense s = at(2);
        for (long seed = 0; seed < 3; seed++) {
            MoveSelector hero = new MoveSelector(k, 50, 50, false, seed * 31);
            hero.fightStarted(0);
            Pick[] p = new Pick[1];
            long t = firstPick(hero, 0, 60, s, p);
            assertTrue(t >= 30 && t <= 55, "each presses at its own gap, nobody waits for a turn: " + t);
        }
    }

    // ------------------------------------------------------------------ reach, posture, frames

    @Test
    void reachPostureAndKnockUps() {
        HeroDef.V2 k = kit(null, move("slash", "melee", "\"weight\": 5"),
                move("burst", "nova", "\"weight\": 5, \"radius\": 5"),
                move("pool", "zone", "\"weight\": 5, \"zone_at\": \"target\", \"range\": 14, \"radius\": 3"),
                move("ward", "zone", "\"weight\": 5, \"zone_at\": \"self\", \"radius\": 4"),
                move("stance", "counter", "\"weight\": 5"),
                move("guard", "self", "\"weight\": 5, \"effects\": [{\"type\": \"cleanse\"}]"),
                move("lift", "melee", "\"weight\": 5, \"effects\": [{\"type\": \"knockback\", \"strength\": 1,"
                        + " \"up\": 1}]"));
        assertEquals(3.5, MoveSelector.reach(k.move("slash")), EPS);
        assertEquals(5, MoveSelector.reach(k.move("burst")), EPS);
        assertEquals(14, MoveSelector.reach(k.move("pool")), EPS);
        assertEquals(4, MoveSelector.reach(k.move("ward")), EPS);
        assertEquals(4, MoveSelector.reach(k.move("stance")), EPS);
        assertEquals(Double.POSITIVE_INFINITY, MoveSelector.reach(k.move("guard")), EPS);
        assertEquals(Posture.DEFENSIVE, MoveSelector.posture(k.move("stance")));
        assertEquals(Posture.DEFENSIVE, MoveSelector.posture(k.move("guard")));
        assertTrue(MoveSelector.knocksUp(k.move("lift")));
        assertFalse(MoveSelector.knocksUp(k.move("slash")));
    }

    @Test
    void framesScaleButNeverUnderTheClassMinimum() {
        HeroDef.V2 k = kit(null, move("smash", "melee", "\"class\": \"heavy\", \"weight\": 5, \"frames\":"
                + " {\"windup\": 1.0, \"active\": 0.2, \"recovery\": 1.2}"));
        Move m = k.move("smash");
        assertEquals(new Frames(1.0, 0.2, 1.2), MoveSelector.frames(m, 1.0));
        Frames fast = MoveSelector.frames(m, 2.0);
        assertEquals(0.8, fast.windup(), EPS, "a heavy wind-up never under 0.8 s");
        assertEquals(0.1, fast.active(), EPS);
        assertEquals(0.8, fast.recovery(), EPS);
        Frames slow = MoveSelector.frames(m, 0.5);
        assertEquals(2.0, slow.windup(), EPS);
        assertEquals(2.4, slow.recovery(), EPS);
        assertEquals(MoveSelector.frames(m, 2.0), MoveSelector.frames(m, 9.0), "speed clamped to 2");
        assertEquals(MoveSelector.frames(m, 1.0), MoveSelector.frames(m, Double.NaN));
    }

    // ------------------------------------------------------------------ states (A4, HEROES_V2 12.9)

    @Test
    void aStateLocksSkillsOrReplacesTheMoveList() {
        HeroDef.V2 kit = kitWith("\"states\": [{\"name\": \"rage\", \"stats\": [{\"stat\": \"attack\", \"percent\": 20}],"
                        + " \"skills_locked\": true}, {\"name\": \"stance\", \"stats\": [{\"stat\": \"armor\", \"percent\": 20}],"
                        + " \"moves\": [\"cut\"]}]", null,
                move("cut", "melee", ""), move("bash", "melee", ""),
                move("enrage", "self", "\"effects\": [{\"type\": \"empower\", \"seconds\": 8, \"state\": \"rage\"}]"));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 1);
        Move cut = kit.move("cut"), bash = kit.move("bash"), enrage = kit.move("enrage");
        Sense sense = at(2);
        assertTrue(s.stateAllows(cut, sense) && s.stateAllows(bash, sense) && s.stateAllows(enrage, sense));
        sense.state = "rage";
        sense.empowered = true;
        assertFalse(s.stateAllows(cut, sense), "skills_locked: only basic attacks and the ultimate");
        assertFalse(s.stateAllows(enrage, sense));
        sense.state = "stance";
        assertTrue(s.stateAllows(cut, sense), "its own move list");
        assertFalse(s.stateAllows(bash, sense), "replaced while the stance lasts");
        assertEquals("rage", MoveSelector.onlyEnters(enrage, kit));
        assertNull(MoveSelector.onlyEnters(cut, kit));
        assertFalse(s.eligible(bash, 0, sense), "the selector itself reads it");
    }

    @Test
    void aMoveThatOnlyEntersTheStateItIsInIsNotCastAgain() {
        HeroDef.V2 kit = kitWith("\"states\": [{\"name\": \"frenzy\", \"stats\": [{\"stat\": \"speed\", \"percent\": 20}]}]",
                null, move("rush", "self", "\"effects\": [{\"type\": \"empower\", \"seconds\": 8, \"state\": \"frenzy\"}]"),
                move("cut", "melee", ""));
        MoveSelector s = new MoveSelector(kit, 50, 50, false, 1);
        Sense sense = at(2);
        sense.state = "frenzy";
        sense.empowered = true;
        assertFalse(s.stateAllows(kit.move("rush"), sense), "v1 18: not while already in it");
        assertTrue(s.stateAllows(kit.move("cut"), sense));
        sense.state = null;
        sense.empowered = false;
        assertTrue(s.stateAllows(kit.move("rush"), sense));
    }
}
