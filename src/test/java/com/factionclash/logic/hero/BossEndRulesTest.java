package com.factionclash.logic.hero;

import com.factionclash.logic.hero.BossRules.Douse;
import com.factionclash.logic.hero.BossRules.DouseEffect;
import com.factionclash.logic.hero.BossRules.Engagement;
import com.factionclash.logic.hero.BossRules.Fight;
import com.factionclash.logic.hero.BossRules.FightEvent;
import com.factionclash.logic.hero.BossRules.UnreachableClock;
import com.factionclash.logic.hero.EscapeRules.Escape;
import com.factionclash.logic.hero.EscapeRules.EscapeStage;
import com.factionclash.logic.hero.HeroDef.WeakPoint;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task A6b's pure rules (HEROES_V2 6.9, 6.10, 6.12, 6.13; MOBILITY_AND_WORK 7.7): the enrage's share of the +25% cap,
 * the fight ended by an escape and backdated by an operator, the douse and its rate, the intro's length, the bar's text
 * and who sees which bars, the unreachable clock, the engaged attackers' ids, and an escape from its start to its end.
 * None of them takes whether anyone is a player.
 */
class BossEndRulesTest {
    private static final double EPS = 1e-9;

    @Test
    void enrageAddsFifteenWithinTheTwentyFiveCap() {
        assertEquals(15, BossRules.enrageBonus(0), EPS);
        assertEquals(15, BossRules.enrageBonus(10), EPS, "10 + 15 = 25: all of it");
        assertEquals(5, BossRules.enrageBonus(20), EPS, "20 + 5 = 25");
        assertEquals(0, BossRules.enrageBonus(30), EPS, "never below 0");
        assertEquals(15, BossRules.enrageBonus(-10), EPS, "a malus does not make room for more");
        // the same as the sum rule of bossDamageBonus
        for (double other : new double[]{0, 5, 12.5, 20, 25}) {
            assertEquals(BossRules.bossDamageBonus(java.util.List.of(other, BossRules.ENRAGE_DAMAGE_PERCENT)),
                    other + BossRules.enrageBonus(other), EPS, "other " + other);
        }
    }

    @Test
    void enrageAfterItsSecondsOrAtOnceByAHardTrigger() {
        double after = BossRules.enrageAfter(0, false);
        assertEquals(180, after, EPS);
        assertEquals(240, BossRules.enrageAfter(0, true), EPS);
        assertFalse(BossRules.enraged(179.9, after, false));
        assertTrue(BossRules.enraged(180, after, false));
        assertTrue(BossRules.enraged(1, after, true), "a hard trigger enrages at once");
    }

    @Test
    void anEscapeEndsTheFightAtOnceAndAnOperatorMayBackdateIt() {
        Fight f = new Fight();
        assertFalse(f.end(5), "no fight to end");
        assertEquals(FightEvent.STARTED, f.update(100, true));
        f.backdate(BossRules.TPS * 170);
        assertEquals(170.25, f.seconds(105), 1e-6, "backdated 170 s, 5 ticks in");
        assertTrue(f.end(110));
        assertFalse(f.active());
        assertEquals(110, f.endedAt());
        assertFalse(f.mayHeal(110 + BossRules.HEAL_DELAY_TICKS - 1));
        assertTrue(f.mayHeal(110 + BossRules.HEAL_DELAY_TICKS));
        f.backdate(1000);
        assertEquals(0, f.seconds(200), EPS, "nothing out of a fight");
        assertEquals(FightEvent.STARTED, f.update(300, true), "the next engaged enemy starts a new fight");
    }

    @Test
    void aDouseWorksOnItsOwnWeakPointOnceInTwentySeconds() {
        Set<WeakPoint> water = EnumSet.of(WeakPoint.WATER), snow = EnumSet.of(WeakPoint.SNOWBALL);
        assertTrue(BossRules.douses(water, Douse.WATER, 1000, 0));
        assertFalse(BossRules.douses(water, Douse.SNOWBALL, 1000, 0), "a snowball on a water-hater only");
        assertTrue(BossRules.douses(snow, Douse.SNOWBALL, 1000, 0));
        assertFalse(BossRules.douses(snow, Douse.SNOWBALL, 1000 + BossRules.DOUSE_EVERY_TICKS - 1, 1000));
        assertTrue(BossRules.douses(snow, Douse.SNOWBALL, 1000 + BossRules.DOUSE_EVERY_TICKS, 1000));
        assertFalse(BossRules.douses(EnumSet.noneOf(WeakPoint.class), Douse.WATER, 1000, 0));
        DouseEffect fire = BossRules.douse(true), cat = BossRules.douse(false);
        assertTrue(fire.endsState() && !fire.flinches(), "a fire hero's state ends");
        assertTrue(!cat.endsState() && cat.flinches(), "any other flinches");
        assertEquals(-10, fire.dealtPercent(), EPS);
        assertEquals(100, cat.ticks());
    }

    @Test
    void theIntroLastsItsClipWithinOneToOneAndAHalfSeconds() {
        assertEquals(25, BossRules.introTicks(0));
        assertEquals(20, BossRules.introTicks(5));
        assertEquals(30, BossRules.introTicks(80));
        assertEquals(27, BossRules.introTicks(27));
        assertTrue(BossRules.introDue(-1, 24000 * 3 + 5, true));
        assertFalse(BossRules.introDue(3, 24000 * 3 + 5, true), "once a day");
        assertFalse(BossRules.introDue(-1, 0, false), "not for monsters alone");
    }

    @Test
    void theBarShowsNameTitlePipsAndTheUltimate() {
        assertEquals("◆◇◇", BossRules.pips(1, 3));
        assertEquals("◆◆◆", BossRules.pips(5, 3));
        assertEquals("◇", BossRules.pips(0, 0));
        assertEquals("★ Natsuho · the Harbour Cat  ◆◆◇", BossRules.barName("Natsuho", "the Harbour Cat", 2, 3, false));
        assertEquals("★ Yugen  ◆◇ ⚡", BossRules.barName("Yugen", null, 1, 2, true));
        assertEquals("★ Yugen · fled", BossRules.endName("Yugen", true));
        assertEquals("★ Benika · fallen", BossRules.endName("Benika", false));
    }

    @Test
    void aPlayerSeesTheThreeNearestFightingHeroesWithinThirtyTwo() {
        assertArrayEquals(new int[]{2, 0, 3}, BossRules.nearestBars(new double[]{10, 40, 3, 20, 25}));
        assertArrayEquals(new int[]{1}, BossRules.nearestBars(new double[]{-1, 32, 33}), "another level, exactly 32, beyond");
        assertArrayEquals(new int[0], BossRules.nearestBars(new double[0]));
        assertArrayEquals(new int[]{0, 1}, BossRules.nearestBars(new double[]{5, 5}), "ties by order");
    }

    @Test
    void theUnreachableClockGivesUpAfterThirtySecondsOnOneTarget() {
        UnreachableClock c = new UnreachableClock();
        for (int t = 20; t < BossRules.UNREACHABLE_GIVE_UP_TICKS; t += 20) {
            assertFalse(c.tick(7, true, 20), "at " + t);
        }
        assertTrue(c.tick(7, true, 20));
        c.reset();
        for (int i = 0; i < 20; i++) {
            c.tick(7, true, 20);
        }
        assertFalse(c.tick(7, false, 20), "reachable again: from 0");
        assertEquals(0, c.ticks());
        assertFalse(c.tick(7, true, 590));
        assertFalse(c.tick(8, true, 20), "another target: from 0");
        assertEquals(20, c.ticks());
    }

    @Test
    void theEngagementListsItsAttackersAndFindsAPerson() {
        Engagement e = new Engagement();
        e.hurtBy(11, 100);
        e.hurtBy(12, 100);
        assertEquals(2, e.remembered());
        assertEquals(11, e.id(0));
        assertEquals(12, e.id(1));
        assertTrue(e.any(150, id -> id == 12));
        assertFalse(e.any(150, id -> id == 13));
        assertFalse(e.any(100 + BossRules.ENGAGE_MEMORY_TICKS, id -> true), "a blow 15 s old does not count");
    }

    @Test
    void anEscapeRunsIsCorneredRunsAgainAndEnds() {
        Escape e = new Escape(1000, 5, 5, null);
        assertEquals(EscapeStage.RUNNING, e.tick(20, true, true, false));
        assertEquals(EscapeStage.RUNNING, e.tick(20, false, true, false), "1 s without a way");
        assertEquals(EscapeStage.RUNNING, e.tick(20, false, true, false), "2 s");
        assertEquals(EscapeStage.CORNERED, e.tick(20, false, true, false), "3 s: cornered, it fights back");
        assertEquals(EscapeStage.CORNERED, e.tick(20, false, true, false));
        assertEquals(EscapeStage.RUNNING, e.tick(20, true, true, false), "a way opens: it runs again");
        assertEquals(0, e.noWayTicks());
        for (int i = 0; i < 9; i++) {
            assertEquals(EscapeStage.RUNNING, e.tick(20, true, false, false), "clear for " + (i + 1) + " s");
        }
        assertEquals(EscapeStage.RUNNING, e.tick(20, true, true, false), "a pursuer comes back: the clear count restarts");
        assertEquals(0, e.clearTicks());
        for (int i = 0; i < 9; i++) {
            e.tick(20, true, false, false);
        }
        assertEquals(EscapeStage.OVER, e.tick(20, true, false, false), "10 s with no pursuer within 48");
        assertEquals(EscapeStage.OVER, e.tick(20, false, true, false), "over stays over");
        Escape home = new Escape(0, 0, 0, "7");
        assertEquals(EscapeStage.OVER, home.tick(20, true, true, true), "inside its refuge's walls");
        assertEquals("7", home.refuge());
    }

    @Test
    void theWildPointLiesStraightAwayFromTheFight() {
        double[] p = EscapeRules.awayPoint(10, 0, 0, 0, 0, 72);
        assertEquals(72, p[0], 1e-9);
        assertEquals(0, p[1], 1e-9);
        double[] q = EscapeRules.awayPoint(0, 0, 0, 0, 90, 72); // on the spot: along its facing (yaw 90 faces -X)
        assertEquals(-72, q[0], 1e-9);
        assertEquals(0, q[1], 1e-6);
        assertTrue(EscapeRules.keepFarther(63.9, 100, 0, 300));
        assertFalse(EscapeRules.keepFarther(64, 100, 0, 300));
        assertFalse(EscapeRules.keepFarther(10, 300 * 20, 0, 300), "after its stay-away time");
    }
}
