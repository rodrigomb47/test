package com.factionclash.logic.hero;

import com.factionclash.logic.hero.FormGeometry.Impulse;
import com.factionclash.logic.hero.HeroDef.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormGeometry} (v1 10.1, 13.4; HEROES_V2 6.14): cones, lines, the volley fan, chains, and the physical
 * impulses of leaps, dashes, hops, knock-ups and pulls, checked against vanilla's air physics by simulation.
 */
class FormGeometryTest {
    private static final double EPS = 1e-9;

    @Test
    void aConeHoldsWhatIsInFrontWithinItsAngleAndRange() {
        // facing +z, a 90-degree cone of 4 blocks
        assertTrue(FormGeometry.inCone(0, 0, 3, 0, 1, 90, 4, 0.3), "straight ahead");
        assertTrue(FormGeometry.inCone(2, 0, 2.2, 0, 1, 90, 4, 0.3), "44 degrees off");
        assertFalse(FormGeometry.inCone(2.2, 0, 2, 0, 1, 90, 4, 0.3), "48 degrees off");
        assertFalse(FormGeometry.inCone(0, 0, -3, 0, 1, 90, 4, 0.3), "behind");
        assertFalse(FormGeometry.inCone(0, 0, 4.5, 0, 1, 90, 4, 0.3), "too far");
        assertTrue(FormGeometry.inCone(0, 0, 4.25, 0, 1, 90, 4, 0.3), "its edge is inside");
        assertTrue(FormGeometry.inCone(0.2, 0, -0.2, 0, 1, 90, 4, 0.3), "right on top of the hero");
        assertFalse(FormGeometry.inCone(0, 5, 1, 0, 1, 90, 4, 0.3), "high above");
        assertTrue(FormGeometry.inCone(-3, 0, 0.1, 0, 1, 180, 4, 0.3), "a half circle reaches the side");
    }

    @Test
    void aLineTouchesWhatIsNearItsSegment() {
        assertTrue(FormGeometry.onLine(5, 1, 0.6, 0.3, 0, 1, 0, 8, 1, 0, 1));
        assertFalse(FormGeometry.onLine(5, 1, 1.0, 0.3, 0, 1, 0, 8, 1, 0, 1), "1 block to the side of a 1-wide line");
        assertTrue(FormGeometry.onLine(5, 1, 1.0, 0.3, 0, 1, 0, 8, 1, 0, 3), "a 3-wide line");
        assertFalse(FormGeometry.onLine(9.5, 1, 0, 0.3, 0, 1, 0, 8, 1, 0, 1), "past where the wall stopped it");
        assertEquals(1.0, FormGeometry.segmentDistance(0, 1, 0, 0, 0, 0, 0, 0, 0), EPS, "a point segment");
    }

    @Test
    void aVolleyFansTenDegreesApart() {
        assertArrayEquals(new double[] {-10, 0, 10}, FormGeometry.fanOffsets(3), EPS);
        assertArrayEquals(new double[] {-15, -5, 5, 15}, FormGeometry.fanOffsets(4), EPS);
        assertArrayEquals(new double[] {0}, FormGeometry.fanOffsets(1), EPS);
    }

    @Test
    void aChainJumpsToTheNearestNewEnemyWithinItsRadius() {
        double[] xs = {3, 1, 9}, ys = {0, 0, 0}, zs = {0, 0, 0};
        boolean[] used = new boolean[3];
        int a = FormGeometry.chainNext(0, 0, 0, xs, ys, zs, used, 4);
        assertEquals(1, a);
        used[a] = true;
        int b = FormGeometry.chainNext(xs[a], ys[a], zs[a], xs, ys, zs, used, 4);
        assertEquals(0, b);
        used[b] = true;
        assertEquals(-1, FormGeometry.chainNext(xs[b], ys[b], zs[b], xs, ys, zs, used, 4), "9 is 6 away");
    }

    /** Vanilla's air physics for a take-off: where the feet come down to {@code rise}. */
    private static double[] fly(double vh, double vy, double rise) {
        double x = 0, y = 0;
        for (int n = 0; n < 200; n++) {
            x += vh;
            y += vy;
            vh *= LeapSolver.HORIZONTAL_DRAG;
            vy = (vy - LeapSolver.GRAVITY) * LeapSolver.VERTICAL_DRAG;
            if (vy < 0 && y <= rise) {
                return new double[] {x, y, n + 1};
            }
        }
        return new double[] {x, y, 200};
    }

    @Test
    void aLeapComesDownOnItsSpot() {
        for (double d : new double[] {3, 5, 8}) {
            for (double rise : new double[] {-1, 0, 1}) {
                Impulse i = FormGeometry.leap(d, rise);
                double[] end = fly(i.horizontal(), i.vertical(), rise);
                assertEquals(d, end[0], 0.15, "distance " + d + " rise " + rise);
                assertEquals(i.airTicks(), (int) end[2]);
                assertTrue(i.horizontal() <= FormGeometry.MAX_HORIZONTAL + EPS, "no more than a block and a fifth a tick");
                assertTrue(LeapSolver.apex(i.vertical()) >= Math.max(0, rise) + FormGeometry.LEAP_CLEARANCE - 0.01,
                        "a visible arc");
            }
        }
        Impulse far = FormGeometry.leap(40, 0);
        assertEquals(FormGeometry.MAX_HORIZONTAL, far.horizontal(), EPS, "too far: it lands short");
    }

    @Test
    void aHopCarriesItsDistance() {
        Impulse back = FormGeometry.hop(Direction.BACK, 4);
        assertEquals(4, fly(back.horizontal(), back.vertical(), 0)[0], 0.05);
        assertEquals(LeapSolver.PLAIN_JUMP, back.vertical(), EPS);
        Impulse up = FormGeometry.hop(Direction.UP, 3);
        assertEquals(0, up.horizontal(), EPS);
        assertEquals(3, LeapSolver.apex(up.vertical()), 0.02);
    }

    @Test
    void aDashSpreadsItsRangeOverHalfASecond() {
        assertEquals(10, FormGeometry.DASH_TICKS);
        assertEquals(0.6, FormGeometry.dashSpeed(6), EPS);
        assertEquals(0, FormGeometry.dashSpeed(-1), EPS);
    }

    @Test
    void aKnockUpNeverLiftsMoreThanABlock() {
        assertEquals(0, FormGeometry.liftImpulse(0), EPS);
        assertEquals(0.5, LeapSolver.apex(FormGeometry.liftImpulse(0.5)), 0.02);
        assertEquals(1.0, LeapSolver.apex(FormGeometry.liftImpulse(3)), 0.02, "v1 allowed 3; 6.14 holds it to 1");
        assertEquals(0.8, FormGeometry.knockbackStrength(2), EPS);
    }

    /** A take-off from the ground: the first tick ends with ground friction, then the air's drag. */
    private static double flyFromGround(double vh, double vy) {
        double x = 0, y = 0;
        boolean first = true;
        for (int n = 0; n < 200; n++) {
            x += vh;
            y += vy;
            vh *= first ? FormGeometry.GROUND_FRICTION : LeapSolver.HORIZONTAL_DRAG;
            first = false;
            vy = (vy - LeapSolver.GRAVITY) * LeapSolver.VERTICAL_DRAG;
            if (vy < 0 && y <= 0) {
                return x;
            }
        }
        return x;
    }

    @Test
    void aPullBringsItsReceiverTwoBlocksPerStrengthButNotOntoTheHero() {
        assertEquals(2, flyFromGround(FormGeometry.pullSpeed(1, 10), FormGeometry.PULL_LIFT), 1e-6);
        assertEquals(6, flyFromGround(FormGeometry.pullSpeed(3, 10), FormGeometry.PULL_LIFT), 1e-6);
        assertEquals(1.5, flyFromGround(FormGeometry.pullSpeed(3, 3), FormGeometry.PULL_LIFT), 1e-6, "stops 1.5 short");
        assertEquals(0, FormGeometry.pullSpeed(2, 1), EPS, "already at the hero");
        assertTrue(FormGeometry.pullSpeed(1, 10) < FormGeometry.airSpeedFor(2, FormGeometry.airTicks(FormGeometry.PULL_LIFT, 0))
                * 2, "the ground's friction is counted");
    }
    // ------------------------------------------------------------------ format 2's forms (task A5)

    @Test
    void aChargeRunsItsRangeAtItsSpeed() {
        assertEquals(0.4, FormGeometry.chargeSpeed(160), EPS, "160% of 5 blocks a second, per tick");
        assertEquals(25, FormGeometry.chargeTicks(10, 160), "10 blocks at 0.4 a tick");
        assertEquals(32, FormGeometry.chargeTicks(14, 180), "Natsuho's zoomies: 14 blocks at 0.45 a tick");
        assertTrue(FormGeometry.chargeTicks(16, 120) <= FormGeometry.CHARGE_MAX_TICKS);
        assertEquals(1, FormGeometry.chargeTicks(10, 0));
    }

    @Test
    void aWaveHitsOnlyTheGroundAtItsFront() {
        assertEquals(4.0, FormGeometry.waveFront(10, 8), EPS, "8 blocks a second: 4 blocks after 10 ticks");
        assertEquals(20, FormGeometry.waveTicks(8, 8));
        assertTrue(FormGeometry.waveHits(4.2, 0.3, 0, true, 4.0, 8), "at the front, on the ground");
        assertFalse(FormGeometry.waveHits(4.2, 0.3, 0, false, 4.0, 8), "jump to dodge");
        assertFalse(FormGeometry.waveHits(6.0, 0.3, 0, true, 4.0, 8), "the front has not reached it yet");
        assertFalse(FormGeometry.waveHits(2.0, 0.3, 0, true, 4.0, 8), "the front has passed it");
        assertFalse(FormGeometry.waveHits(4.0, 0.3, 3, true, 4.0, 8), "on a ledge above the ring");
        assertFalse(FormGeometry.waveHits(9.5, 0.3, 0, true, 9.5, 8), "beyond the radius");
    }

    @Test
    void rainImpactsSpreadEvenlyOverTheirDisc() {
        int inner = 0;
        for (int i = 0; i < 1000; i++) {
            double[] p = FormGeometry.discPoint((i * 0.618034) % 1, (i * 0.381966 + 0.5) % 1, 5);
            double r = Math.sqrt(p[0] * p[0] + p[1] * p[1]);
            assertTrue(r <= 5 + EPS);
            if (r <= 2.5) {
                inner++;
            }
        }
        assertTrue(inner > 180 && inner < 320, "a quarter of the area holds about a quarter of the impacts: " + inner);
    }

    @Test
    void aSweepTurnsAcrossItsArcAndCatchesWhatItPasses() {
        assertEquals(-60, FormGeometry.sweepAngle(0, 20, 120), EPS);
        assertEquals(0, FormGeometry.sweepAngle(10, 20, 120), EPS);
        assertEquals(60, FormGeometry.sweepAngle(20, 20, 120), EPS);
        // facing +x; a receiver straight ahead is caught by the tick whose line passes 0 degrees
        assertTrue(FormGeometry.inSweep(5, 0, 1, 0, -6, 0, 8, 1, 0.3));
        assertFalse(FormGeometry.inSweep(5, 0, 1, 0, 20, 30, 8, 1, 0.3), "the line is elsewhere this tick");
        assertFalse(FormGeometry.inSweep(10, 0, 1, 0, -6, 6, 8, 1, 0.3), "out of range");
        double[] d = FormGeometry.turn(1, 0, 25);
        assertTrue(FormGeometry.inSweep(d[0] * 6, d[1] * 6, 1, 0, 20, 30, 8, 1, 0.3), "turn and inSweep agree on the sign");
        assertFalse(FormGeometry.inSweep(d[0] * 6, d[1] * 6, 1, 0, -30, -20, 8, 1, 0.3));
    }

    @Test
    void aGrabHoldsByVelocityNeverATeleport() {
        double[] v = FormGeometry.grabHold(1, 0, 0);
        assertEquals(0.5, v[0], EPS, "half the gap a tick");
        double[] far = FormGeometry.grabHold(10, 0, 0);
        assertEquals(FormGeometry.GRAB_PULL_MAX, Math.sqrt(far[0] * far[0] + far[1] * far[1] + far[2] * far[2]), EPS,
                "never faster than its cap");
        assertEquals(0, FormGeometry.THROW_TRIES[0], EPS, "ahead first");
        assertEquals(180, FormGeometry.THROW_TRIES[FormGeometry.THROW_TRIES.length - 1], EPS, "behind last");
        Impulse t = FormGeometry.throwImpulse(3, 1);
        assertEquals(1.2, t.horizontal(), EPS, "a throw of 3 is a knock-back of 3");
        assertTrue(t.vertical() > 0.3 && t.vertical() <= LeapSolver.strengthFor(1) + EPS, "lifted by up to a block: " + t.vertical());
        assertTrue(FormGeometry.throwDistance(3, 1) > FormGeometry.throwDistance(1, 0), "a harder throw goes further");
    }

    @Test
    void aTetherPullsBackOnlyPastItsLength() {
        assertEquals(0, FormGeometry.tetherPull(5, 6), EPS);
        assertEquals(FormGeometry.TETHER_PULL_MIN, FormGeometry.tetherPull(6.1, 6), EPS);
        assertEquals(FormGeometry.TETHER_PULL_MAX, FormGeometry.tetherPull(20, 6), EPS);
    }
}
