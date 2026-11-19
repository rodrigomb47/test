package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FightMobility} (MOBILITY_AND_WORK 3.7, 5.1, 5.2, 7.3-7.5; task A12): the aerial window (its take-off chosen
 * like a move, at most 3 light ranged moves in the air, a dive or a rain any time in it, the touch-and-go between
 * dives and the landing after the last), the dive's straight line and marker, the apex of a jump, the come-back of the
 * reach clock, the reach flight, the strike from a link, the archers' order and the soldiers' wait under an airborne
 * hero. None of it reads whether anyone is a player.
 */
class FightMobilityTest {
    private static final double EPS = 1e-9;

    @Test
    void anAerialWindowOpensOnlyInItsPhasesAndItsTakeOffWeighsAsAMove() {
        HeroDef.Aerial a = new HeroDef.Aerial(List.of(2, 3), 8, 3, 4);
        assertFalse(FightMobility.aerialPhase(a, 1));
        assertTrue(FightMobility.aerialPhase(a, 2) && FightMobility.aerialPhase(a, 3));
        assertFalse(FightMobility.aerialPhase(null, 2), "no aerial block, no window (a glider, a grounded flyer)");
        assertEquals(4, FightMobility.takeOffWeight(a), EPS);
        assertEquals(0, FightMobility.takeOffWeight(new HeroDef.Aerial(List.of(2), 8, 3, 0)), EPS, "0 never takes off");
        // weight 4 against moves scoring 12 in all: a quarter of the rolls
        assertTrue(FightMobility.takeOffChosen(4, 12, 0.24));
        assertFalse(FightMobility.takeOffChosen(4, 12, 0.26));
        assertTrue(FightMobility.takeOffChosen(4, 0, 0.99), "with nothing else to do it takes off");
        assertFalse(FightMobility.takeOffChosen(0, 0, 0.0));
    }

    @Test
    void inTheAirOnlyThreeLightRangedMovesAndTheTelegraphedDiveOrRain() {
        for (Form f : List.of(Form.SHOT, Form.VOLLEY, Form.BOLT, Form.BARRAGE)) {
            assertTrue(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 0), f.name());
            assertTrue(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 2), f.name());
            assertFalse(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 3), f + ": the fourth of a take-off");
            assertFalse(FightMobility.airMoveAllowed(f, MoveClass.HEAVY, 0), f + ": a heavy shot is no light move");
        }
        assertTrue(FightMobility.airMoveAllowed(Form.DIVE, MoveClass.HEAVY, 3), "a dive any time in the window");
        assertTrue(FightMobility.airMoveAllowed(Form.RAIN, MoveClass.AREA, 3), "a rain's wind-up in the air");
        for (Form f : List.of(Form.MELEE, Form.COMBO, Form.LEAP, Form.DASH, Form.CHARGE, Form.GRAB, Form.NOVA, Form.CONE)) {
            assertFalse(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 0), f + ": melee is for the ground (5.1.5)");
        }
        FightMobility.Window w = new FightMobility.Window();
        w.moveStarted(Form.SHOT);
        assertEquals(0, w.lights(), "nothing counts while the window is shut");
        w.open(100);
        w.moveStarted(Form.SHOT);
        w.moveStarted(Form.BARRAGE);
        w.moveStarted(Form.DIVE);
        w.moveStarted(Form.RAIN);
        assertEquals(2, w.lights());
        assertEquals(1, w.dives());
        w.open(400);
        assertEquals(0, w.lights(), "a new take-off starts its own count");
    }

    @Test
    void aDiveTouchesDownAndGoesAgainWhileTheWindowHasDivesThenLandsForItsRecovery() {
        // dives 3: the first two touch and go, the third lands for its full recovery
        assertEquals(FightMobility.AfterDive.TOUCH_AND_GO, FightMobility.afterDive(1, 3, 6, 0.8, 0.3));
        assertEquals(FightMobility.AfterDive.TOUCH_AND_GO, FightMobility.afterDive(2, 3, 4, 0.8, 0.3));
        assertEquals(FightMobility.AfterDive.LAND, FightMobility.afterDive(3, 3, 4, 0.8, 0.3));
        assertEquals(FightMobility.AfterDive.LAND, FightMobility.afterDive(1, 1, 6, 0.8, 0.3), "one dive a window");
        assertEquals(FightMobility.AfterDive.LAND, FightMobility.afterDive(1, 3, 0.5, 0.8, 0.3), "no air budget left");
        assertEquals(FightMobility.AfterDive.LAND, FightMobility.afterDive(1, 3, 6, 0.2, 0.3), "under its take-off share");
        assertEquals(10, FightMobility.TOUCH_DOWN_TICKS, "0.5 s on the ground between two dives");
        assertEquals(20, FightMobility.RECOVERY_MIN_TICKS, "the punish window after the last");
    }

    @Test
    void theDiveFliesAStraightLineToItsMarkerNeverPastIt() {
        double[] v = new double[3];
        FightMobility.diveVelocity(6, -3, 0, v);
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        assertEquals(FightMobility.DIVE_SPEED, len, 1e-9);
        assertEquals(-0.5, v[1] / v[0], 1e-9, "along the line");
        FightMobility.diveVelocity(0.3, -0.1, 0.2, v);
        assertEquals(0.3, v[0], 1e-9, "never past the marker");
        assertEquals(-0.1, v[1], 1e-9);
        // its marker: the target's feet within its range, else the farthest point of its range toward them
        double[] m = FightMobility.diveMarker(0, 10, 0, 6, 4, 0, 12);
        assertEquals(6, m[0], EPS);
        assertEquals(4, m[1], EPS);
        m = FightMobility.diveMarker(0, 0, 0, 20, 0, 0, 8);
        assertEquals(8, m[0], EPS);
    }

    @Test
    void anApexDiveJumpsSoItsApexComesAsTheWindUpEnds() {
        // a plain jump (0.42) peaks after 6 ticks, a leaper II's (1.84-block jump) a little later: LeapSolver's air physics
        assertEquals(6, FightMobility.apexTicks(0.42));
        double v = LeapSolver.attributeFor(1.84);
        int n = FightMobility.apexTicks(v);
        double y = 0, vv = v;
        for (int i = 0; i < n; i++) {
            y += vv;
            vv = (vv - LeapSolver.GRAVITY) * LeapSolver.VERTICAL_DRAG;
        }
        assertTrue(vv <= 0 && y > 1.7, "at the top after " + n + " ticks, " + y + " up");
        assertEquals(0, FightMobility.apexTicks(0));
    }

    @Test
    void theReachClockBringsAFlyerDownAndAWalkerBackNeverAnEscaper() {
        assertEquals(FightMobility.ComeBack.NONE, FightMobility.comeBack(false, false, true));
        assertEquals(FightMobility.ComeBack.LAND, FightMobility.comeBack(true, false, true));
        assertEquals(FightMobility.ComeBack.RETURN, FightMobility.comeBack(true, false, false));
        assertEquals(FightMobility.ComeBack.NONE, FightMobility.comeBack(true, true, true), "an escape may go where nobody follows");
        // the clock itself: 4 s out of reach, held through a wind-up, restarted in reach
        BossRules.ReachClock c = new BossRules.ReachClock();
        int fired = -1;
        for (int t = 0; t < 200 && fired < 0; t++) {
            if (c.tick(true, t >= 20 && t < 40, false)) {
                fired = t;
            }
        }
        assertEquals(BossRules.REACH_LIMIT_TICKS + 20 - 1, fired, "4 s, the 1 s wind-up not counted");
        // the airborne input: more than 3 above every engaged enemy's standing height, a player's or an NPC's alike
        assertTrue(BossRules.aboveReach(70.0 + FightMobility.HOVER_ABOVE, List.of(70.0)), "a window's hover is out of reach");
        assertFalse(BossRules.aboveReach(70 + FightMobility.REACH_ABOVE_FEET + 0.0, List.of(70.0)), "a reach flight is not");
    }

    @Test
    void aReachFlightOnlyForAFlyerOutOfItsAerialPhasesToAStandingTargetNoWalkReaches() {
        assertTrue(FightMobility.reachFlight(true, false, false, true, true));
        assertFalse(FightMobility.reachFlight(false, false, false, true, true), "no flyer");
        assertFalse(FightMobility.reachFlight(true, true, false, true, true), "an aerial phase has its window");
        assertFalse(FightMobility.reachFlight(true, false, true, true, true), "a walk or a link reaches it");
        assertFalse(FightMobility.reachFlight(true, false, false, false, true), "a target in the air is waited for");
        assertFalse(FightMobility.reachFlight(true, false, false, true, false), "it may not take off now");
        // the strike from a link: a basic attack only, only with no standable cell in reach, from off the ground
        assertTrue(FightMobility.strikeFromLink(true, false, 2.0, 3.5, true));
        assertFalse(FightMobility.strikeFromLink(false, false, 2.0, 3.5, true), "on the ground it walks and swings");
        assertFalse(FightMobility.strikeFromLink(true, true, 2.0, 3.5, true), "a cell beside the target: it lands there");
        assertFalse(FightMobility.strikeFromLink(true, false, 4.0, 3.5, true), "out of reach");
        assertFalse(FightMobility.strikeFromLink(true, false, 2.0, 3.5, false), "no sight");
    }

    @Test
    void archersTakeAnAirborneHeroFirstAndSoldiersWaitUnderIt() {
        assertEquals(2, FightMobility.archerPriority(true, true, false));
        assertEquals(2, FightMobility.archerPriority(true, true, true));
        assertEquals(1, FightMobility.archerPriority(true, false, true), "then an intruding hero");
        assertEquals(0, FightMobility.archerPriority(true, false, false));
        assertEquals(0, FightMobility.archerPriority(false, true, true), "the rest by the usual goals");
        for (int mode : new int[]{MovementRules.AIRBORNE, MovementRules.GLIDE, MovementRules.FLY, MovementRules.FLY_HOVER,
                MovementRules.RIDE}) {
            assertTrue(FightMobility.holdUnder(true, mode), "mode " + mode);
        }
        for (int mode : new int[]{MovementRules.GROUND, MovementRules.CLIMB, MovementRules.CLING, MovementRules.DOWNED}) {
            assertFalse(FightMobility.holdUnder(true, mode), "mode " + mode);
        }
        assertFalse(FightMobility.holdUnder(false, MovementRules.FLY), "only an enemy hero");
        assertEquals(16, FightMobility.ALARM_UP);
        assertEquals(24, FightMobility.APPROACH_SIGHT, EPS);
        assertEquals(60, FightMobility.AIRBORNE_TARGET_HOLD);
    }

    @Test
    void theSelectorChoosesATakeOffLikeAMoveAfterTheGapAndCountsLightMovesInTheAir() {
        HeroDef.V2 k = MoveSelectorTest.kit(null, MoveSelectorTest.move("vial", "shot", "\"class\": \"light\", \"range\": 12"),
                MoveSelectorTest.move("smash", "melee", "\"class\": \"heavy\""));
        MoveSelector m = new MoveSelector(k, 50, 50, false, 7);
        Sense s = MoveSelectorTest.at(6);
        m.fightStarted(0);
        assertFalse(m.chooseTakeOff(0, s, 100), "never inside the opening gap");
        int took = 0;
        long t = 200;
        for (int i = 0; i < 400; i++, t += 40) {
            if (m.chooseTakeOff(t, s, 4)) {
                took++;
            }
        }
        assertTrue(took > 40 && took < 400, "a share of the checks: " + took);
        assertFalse(m.chooseTakeOff(t + 1, s, 0), "weight 0");
        Sense stunned = MoveSelectorTest.at(6);
        stunned.stunned = true;
        assertFalse(m.chooseTakeOff(t + 100, stunned, 100));
        // in the air: the light shot until 3 a take-off, never the melee
        Sense air = MoveSelectorTest.at(6);
        air.airborne = true;
        assertTrue(m.eligible(k.move("vial"), t + 200, air));
        Sense close = MoveSelectorTest.at(1.5);
        close.airborne = true;
        assertFalse(m.eligible(k.move("smash"), t + 200, close), "melee in the air");
        air.airLights = 3;
        assertFalse(m.eligible(k.move("vial"), t + 200, air), "the fourth light move of a take-off");
        // a reach flight (3.7, 7.4): no move at all in the air, the light shot included; on the ground it is no flight
        Sense reach = MoveSelectorTest.at(6);
        reach.airborne = true;
        reach.reachFlight = true;
        assertFalse(m.eligible(k.move("vial"), t + 200, reach), "a move on a reach flight");
        for (Form f : new Form[]{Form.SHOT, Form.RAIN, Form.DIVE, Form.BARRAGE}) {
            assertFalse(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 0, true), f + " on a reach flight");
            assertTrue(FightMobility.airMoveAllowed(f, MoveClass.LIGHT, 0, false), f + " in an aerial window");
        }
    }
}
