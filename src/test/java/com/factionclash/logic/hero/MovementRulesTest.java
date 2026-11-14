package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task A10: the per-tick movement rules (MOBILITY_AND_WORK 1.6-1.9, 2.1-2.4, 4.2, 5.3). */
class MovementRulesTest {
    private static final double EPS = 1e-9;

    @Test
    void airPoiseOfEachCounterIsTheTableOf53() {
        assertEquals(20, MovementRules.AirHit.ARROW.poise);
        assertEquals(30, MovementRules.AirHit.BOLT.poise);
        assertEquals(15, MovementRules.AirHit.MULTISHOT_BOLT.poise);
        assertEquals(40, MovementRules.AirHit.TRIDENT.poise);
        assertEquals(35, MovementRules.AirHit.WIND_CHARGE.poise);
        assertEquals(10, MovementRules.AirHit.SNOWBALL.poise);
        assertEquals(15, MovementRules.AirHit.FISHING_HOOK.poise);
        assertEquals(25, MovementRules.AirHit.MELEE.poise);
        assertEquals(40, MovementRules.AirHit.MELEE_CRIT.poise);
        // five arrows down a flyer, four do not (2.4: a bar of 100)
        BossRules.AirPoise p = new BossRules.AirPoise();
        for (int i = 0; i < 4; i++) {
            assertFalse(p.hit(MovementRules.AirHit.ARROW.poise, i), "arrow " + (i + 1));
        }
        assertTrue(p.hit(MovementRules.AirHit.ARROW.poise, 4), "the fifth arrow downs it");
        // three bolts and a snowball (Ambrose's sheet)
        BossRules.AirPoise q = new BossRules.AirPoise();
        q.hit(30, 0);
        q.hit(30, 1);
        q.hit(30, 2);
        assertTrue(q.hit(10, 3));
    }

    @Test
    void aMeleeBlowCountsOnlyOnALowFlyer() {
        assertTrue(MovementRules.lowFlyer(63, 60));
        assertTrue(MovementRules.lowFlyer(61.5, 60));
        assertFalse(MovementRules.lowFlyer(63.1, 60), "more than 3 blocks up: out of a blow's air poise");
    }

    @Test
    void stunFreezeSlownessTwoAndLightningDownAtOnce() {
        assertTrue(MovementRules.downsAtOnce(true, false, -1, false));
        assertTrue(MovementRules.downsAtOnce(false, true, -1, false));
        assertTrue(MovementRules.downsAtOnce(false, false, 1, false), "Slowness II");
        assertTrue(MovementRules.downsAtOnce(false, false, -1, true));
        assertFalse(MovementRules.downsAtOnce(false, false, 0, false), "Slowness I only halves the climb rate");
        assertFalse(MovementRules.downsAtOnce(false, false, -1, false));
        assertEquals(0.5, MovementRules.climbRateFactor(0), EPS);
        assertEquals(1.0, MovementRules.climbRateFactor(-1), EPS);
    }

    @Test
    void aGlideSinksAtItsRateAndKeepsItsForwardSpeed() {
        // Ambrose at level 1 (15.9): sink 4.0 b/s, forward 6.5 b/s
        assertEquals(-0.2, MovementRules.glideVertical(-0.6, 4.0, false), EPS);
        assertEquals(-0.1, MovementRules.glideVertical(-0.1, 4.0, false), EPS, "slower than the sink rate: unchanged");
        assertEquals(0.3, MovementRules.glideVertical(0.3, 4.0, false), EPS, "a gust's rise is gravity's");
        assertEquals(-0.3, MovementRules.glideVertical(-0.9, 4.0, true), EPS, "past its time: 6 b/s");
        assertEquals(0.325, MovementRules.glideForward(6.5, false), EPS);
        assertEquals(0, MovementRules.glideForward(6.5, true), EPS);
        assertTrue(MovementRules.opensGlide(-0.5, 4));
        assertTrue(MovementRules.opensGlide(-0.5, Double.POSITIVE_INFINITY), "no ground below: it glides");
        assertFalse(MovementRules.opensGlide(-0.5, 3.9), "a drop under 4 blocks: no glide");
        assertFalse(MovementRules.opensGlide(-0.1, 10), "not falling yet");
    }

    @Test
    void aFlyerCruisesClimbsAndStopsOnItsPoint() {
        double[] v = new double[3];
        // far east and level: cruise speed (Ambrose level 1: 5.9 b/s), no vertical
        MovementRules.flightVelocity(30, 0, 0, 5.9, 1.8, 10, v);
        assertEquals(5.9 / 20, v[0], 1e-9);
        assertEquals(0, v[1], EPS);
        assertEquals(0, v[2], EPS);
        // straight up: its climb rate at most
        MovementRules.flightVelocity(0, 20, 0, 5.9, 1.8, 30, v);
        assertEquals(1.8 / 20, v[1], 1e-9);
        // the ceiling holds it down whatever the point asks
        MovementRules.flightVelocity(0, 20, 0, 5.9, 1.8, 0, v);
        assertEquals(0, v[1], EPS);
        MovementRules.flightVelocity(0, 0, 0, 5.9, 1.8, -2, v);
        assertTrue(v[1] < 0, "above its ceiling it comes down");
        assertTrue(v[1] >= -MovementRules.DESCENT / 20 - EPS);
        // near the point it slows: no overshoot
        MovementRules.flightVelocity(0.2, 0, 0, 5.9, 1.8, 10, v);
        assertTrue(v[0] <= 0.2 + EPS && v[0] > 0);
        assertTrue(MovementRules.arrived(0.1, 0.1, -0.1));
        assertFalse(MovementRules.arrived(0.4, 0, 0));
    }

    @Test
    void theFlightActivityDecidesTheDrain() {
        assertEquals(MobilityRules.Activity.HOVER, MovementRules.flightActivity(true, false, MobilityRules.Situation.TRAVEL));
        assertEquals(MobilityRules.Activity.CRUISE, MovementRules.flightActivity(false, true, MobilityRules.Situation.TRAVEL));
        assertEquals(MobilityRules.Activity.POWERED, MovementRules.flightActivity(false, true, MobilityRules.Situation.FIGHT));
        assertEquals(MobilityRules.Activity.POWERED, MovementRules.flightActivity(false, true, MobilityRules.Situation.ESCAPE));
        assertEquals(0.5, MobilityRules.drain(MobilityRules.Activity.CRUISE, false, MobilityRules.Situation.TRAVEL), EPS);
        assertEquals(1.5, MobilityRules.drain(MobilityRules.Activity.POWERED, false, MobilityRules.Situation.FIGHT), EPS);
        assertEquals(2.0, MobilityRules.drain(MobilityRules.Activity.HOVER, false, MobilityRules.Situation.TRAVEL), EPS);
    }

    @Test
    void landingsByLeapFallAndRoll() {
        assertEquals(MovementRules.Landing.LAND_HEAVY, MovementRules.landing(true, 0.5, false), "a leap lands heavily");
        assertEquals(MovementRules.Landing.LAND_ROLL, MovementRules.landing(true, 0.5, true), "leaper II+ and acrobats roll");
        assertEquals(MovementRules.Landing.LAND, MovementRules.landing(false, 2.5, false));
        assertEquals(MovementRules.Landing.LAND_HEAVY, MovementRules.landing(false, 5, false));
        assertEquals(MovementRules.Landing.NONE, MovementRules.landing(false, 1.2, false), "a hop is no landing");
        assertEquals(MovementRules.Landing.NONE, MovementRules.landing(false, 1.2, true));
        assertTrue(MovementRules.slows(MovementRules.Landing.LAND_HEAVY));
        assertFalse(MovementRules.slows(MovementRules.Landing.LAND_ROLL), "the roll: no landing slowdown (1.6)");
    }

    @Test
    void wallKicksAlternateAndAirJumpsCount() {
        assertTrue(MovementRules.wallKickAllowed(MovementRules.NO_FACE, 2));
        assertFalse(MovementRules.wallKickAllowed(2, 2), "not off the same face twice in a row");
        assertTrue(MovementRules.wallKickAllowed(2, 0), "a chimney alternates");
        assertFalse(MovementRules.wallKickAllowed(2, -1));
        assertTrue(MovementRules.airJumpLeft(1, 2));
        assertFalse(MovementRules.airJumpLeft(2, 2));
        assertFalse(MovementRules.airJumpLeft(0, 0));
    }

    @Test
    void aBurstRunsItsSecondsAndWaitsFromItsStart() {
        MobilityRules.Burst b = new MobilityRules.Burst(0.24, 3, 25);
        MovementRules.BurstClock c = new MovementRules.BurstClock();
        assertTrue(c.start(0, b));
        assertTrue(c.active(59));
        assertFalse(c.active(60), "3 s");
        assertFalse(c.start(100, b), "its cooldown counts from its start");
        assertTrue(c.start(500, b), "25 s later");
        c.stop(510);
        assertFalse(c.active(511));
        assertFalse(c.start(520, null));
    }

    @Test
    void locoOneShotsKeepTheirModeFlipTheToggleAndClear() {
        MovementRules.Loco l = new MovementRules.Loco();
        l.mode(MovementRules.GROUND);
        assertEquals(0, l.pack(), "a fresh hero walks");
        l.oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_SPECIAL, 2);
        l.mode(MovementRules.GROUND);
        byte first = l.pack();
        assertEquals(MovementRules.AIRBORNE, HeroActions.locoMode(first), "the one-shot shows the mode that names it");
        assertEquals(HeroActions.LOCO_ONE_SHOT_SPECIAL, HeroActions.locoOneShot(first));
        assertEquals("move.air_jump", HeroClips.oneShotClip(HeroActions.locoMode(first), HeroActions.locoOneShot(first)));
        l.oneShot(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_SPECIAL, 2);
        byte second = l.pack();
        assertTrue(HeroActions.locoToggle(first) != HeroActions.locoToggle(second), "a second air jump flips the toggle");
        l.tick();
        assertTrue(l.playing());
        l.tick();
        assertFalse(l.playing(), "cleared once played");
        assertEquals(MovementRules.GROUND, HeroActions.locoMode(l.pack()));
        assertEquals(HeroActions.LOCO_ONE_SHOT_NONE, HeroActions.locoOneShot(l.pack()));
        l.tired(true);
        assertTrue(HeroActions.locoTired(l.pack()));
        l.oneShot(MovementRules.DOWNED, HeroActions.LOCO_ONE_SHOT_START, 40);
        l.cut();
        assertFalse(l.playing());
    }

    @Test
    void theMountShowsOnlyWhileFlyingGlidingOrRiding() {
        assertTrue(MovementRules.showsMount(MovementRules.FLY));
        assertTrue(MovementRules.showsMount(MovementRules.FLY_HOVER));
        assertTrue(MovementRules.showsMount(MovementRules.GLIDE));
        assertTrue(MovementRules.showsMount(MovementRules.RIDE));
        assertFalse(MovementRules.showsMount(MovementRules.GROUND));
        assertFalse(MovementRules.showsMount(MovementRules.DOWNED), "downed, the carrier folds");
        assertTrue(MovementRules.winded(true, true, 0.01));
        assertFalse(MovementRules.winded(true, true, 0.2), "walking: it walks");
        assertFalse(MovementRules.winded(false, true, 0));
    }

    @Test
    void everyOneShotNamesAG10ClipWithItsLengthAndItsLoop() {
        String[][] table = {
                {"ground", "move.crouch_jump", "move.land", "move.land_roll"},
                {"airborne", "move.jump", "move.land_heavy", "move.air_jump"},
                {"glide", "move.glide_start", "move.land", "move.gust"},
                {"fly", "move.takeoff", "move.fly_land", null},
                {"ride", "move.takeoff", "move.fly_land", null},
                {"climb", null, null, "move.mantle"},
                {"cling", null, null, "move.wall_kick"},
                {"charge_run", null, "move.skid", null},
                {"downed", "move.downed", null, null}};
        for (String[] row : table) {
            int mode = MovementRules.mode(row[0]);
            for (int k = 1; k <= 3; k++) {
                assertEquals(row[k], HeroClips.oneShotClip(mode, k), row[0] + " kind " + k);
            }
        }
        assertNull(HeroClips.oneShotClip(MovementRules.GROUND, HeroActions.LOCO_ONE_SHOT_NONE));
        assertEquals(6, HeroClips.oneShotTicks("move.crouch_jump"), "the crouch tell is 0.3 s");
        assertEquals(12, HeroClips.oneShotTicks("move.takeoff"), "the take-off tell is 0.6 s");
        assertEquals(8, HeroClips.oneShotTicks("move.fly_land"), "the landing is 0.4 s");
        assertEquals(40, HeroClips.oneShotTicks("move.downed"), "downed 2 s");
        assertEquals("move.leap", HeroClips.oneShotSettle(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_START));
        assertEquals("move.idle", HeroClips.oneShotSettle(MovementRules.AIRBORNE, HeroActions.LOCO_ONE_SHOT_END));
        assertEquals("move.ride", HeroClips.oneShotSettle(MovementRules.RIDE, HeroActions.LOCO_ONE_SHOT_START),
                "a broom's take-off settles into the ride");
        assertEquals("move.fly", HeroClips.oneShotSettle(MovementRules.FLY, HeroActions.LOCO_ONE_SHOT_START));
        assertEquals("move.glide", HeroClips.oneShotSettle(MovementRules.GLIDE, HeroActions.LOCO_ONE_SHOT_SPECIAL));
        assertTrue(HeroClips.oneShotKey(1, 3, true) != HeroClips.oneShotKey(1, 3, false), "a new toggle is a new key");
    }

    @Test
    void wingsFoldBeatAndSpreadByMode() {
        assertEquals("sec.wings_fold", HeroClips.wingLoop(MovementRules.GROUND));
        assertEquals("sec.wings_flap", HeroClips.wingLoop(MovementRules.FLY));
        assertEquals("sec.wings_flap", HeroClips.wingLoop(MovementRules.FLY_HOVER));
        assertEquals("sec.wings_glide", HeroClips.wingLoop(MovementRules.GLIDE));
        assertEquals("sec.wings_fold", HeroClips.wingLoop(MovementRules.RIDE), "a mount rider's wings stay folded");
        assertEquals(0.7, HeroClips.wingSpeed("sec.wings_flap", true), EPS, "tired: a slower beat");
        assertEquals(1.0, HeroClips.wingSpeed("sec.wings_glide", true), EPS);
    }
}
