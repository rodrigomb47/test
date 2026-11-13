package com.factionclash.logic.hero;

import com.factionclash.logic.hero.LeapSolver.Arc;
import com.factionclash.logic.hero.LeapSolver.Leap;
import com.factionclash.logic.hero.LeapSolver.Limits;
import com.factionclash.logic.hero.LeapSolver.Slice;
import com.factionclash.logic.hero.LeapSolver.TakeOff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MOBILITY_AND_WORK 1.2, 1.6, 3.3 and 3.9: the leap solver re-simulates vanilla's air physics. */
class LeapSolverTest {
    private static final Limits ANY = new Limits(1.0, LeapSolver.MAX_HORIZONTAL);

    @Test
    void jumpHeightsMatchTheVanillaSimulationOfOneTwo() {
        // 1.2: 0.42 = 1.25, 0.52 = 1.84, 0.62 = 2.52, 0.75 = 3.54 blocks (vanilla's 1.2522 for a plain jump)
        assertEquals(1.2522, LeapSolver.apex(0.42), 1e-4);
        assertEquals(1.84, LeapSolver.apex(0.52), 0.005);
        assertEquals(2.52, LeapSolver.apex(0.62), 0.005);
        assertEquals(3.54, LeapSolver.apex(0.75), 0.005);
        // the attribute a height needs, rounded up to 0.01 so the hero always reaches it
        assertEquals(0.42, LeapSolver.attributeFor(1.25), 1e-9);
        assertEquals(0.52, LeapSolver.attributeFor(1.8), 1e-9);
        assertEquals(0.62, LeapSolver.attributeFor(2.5), 1e-9);
        assertEquals(0.75, LeapSolver.attributeFor(3.5), 1e-9);
        for (double h = 1.25; h <= 4.0; h += 0.05) {
            double a = LeapSolver.attributeFor(h);
            assertTrue(LeapSolver.apex(a) >= h - 1e-9, "reaches " + h);
            assertTrue(LeapSolver.apex(a - 0.01) < h, "the least on the grid for " + h);
        }
        assertEquals(0, LeapSolver.strengthFor(0));
        assertEquals(0, LeapSolver.apex(0));
    }

    @Test
    void theLeapsOfOneSixAreTheSolversLowestSpeedImpulses() {
        double[][] doc = {{3, 0.42, 0.52}, {5, 0.50, 0.72}, {6, 0.54, 0.82}, {8, 0.63, 1.00}};
        for (double[] r : doc) {
            int gap = (int) r[0];
            Leap l = LeapSolver.solveGap(gap, 0, ANY, LeapSolver.flat(gap, 0, 10));
            assertNotNull(l, "gap " + gap);
            assertEquals(r[1], l.vertical(), 1e-9, "vertical for " + gap);
            assertEquals(r[2], l.horizontal(), 1e-9, "horizontal for " + gap);
            assertTrue(l.ticks() >= 12 && l.ticks() <= 17, "12-17 ticks in the air: " + l.ticks());
            assertTrue(l.apex() >= 1.25 && l.apex() <= 2.6, "apex 1.25 to 2.6: " + l.apex());
            assertTrue(Math.abs(l.landingX() - (gap + 1)) <= LeapSolver.TOLERANCE + 1e-9);
        }
        // the first draft's (0.65, 0.90) for 6 overshoots onto the cell beyond
        Arc a = LeapSolver.simulate(0, 0, 0.90, 0.65, LeapSolver.flat(6, 0, 10), -64);
        assertTrue(a.landed());
        assertTrue(a.x() > 7.5, "lands a full block too far: " + a.x());
        // a 1-block rise needs about 0.07 more vertical
        for (int gap : new int[]{3, 5, 6, 8}) {
            Leap flat = LeapSolver.solveGap(gap, 0, ANY, LeapSolver.flat(gap, 0, 10));
            Leap up = LeapSolver.solveGap(gap, 1, ANY, LeapSolver.flat(gap, 1, 10));
            assertNotNull(up);
            double more = up.vertical() - flat.vertical();
            assertTrue(more >= 0.05 && more <= 0.09, gap + ": " + more);
        }
    }

    @Test
    void everyGapAndRiseInEachTiersRangeLandsOnTheCellCentre() {
        // leaper ceilings 5 / 6 / 8 blocks, acrobat 4 / 5 / 6, at full power; rises -1..1
        LeapSolver.Table table = new LeapSolver.Table(8, -1, 1);
        for (int gap = 2; gap <= 8; gap++) {
            for (int rise = -1; rise <= 1; rise++) {
                Leap l = table.get(gap, rise);
                assertNotNull(l, gap + " / " + rise);
                Arc a = LeapSolver.simulate(0, 0, l.horizontal(), l.vertical(), LeapSolver.flat(gap, rise, 10), -64);
                assertTrue(LeapSolver.onTarget(a, gap + 1, rise), gap + " / " + rise + ": " + a);
                assertTrue(LeapSolver.stillLands(0, 0, l.horizontal(), l.vertical(), gap + 1, rise,
                        LeapSolver.flat(gap, rise, 10)));
            }
        }
        assertEquals(21, table.size());
        // the hero's limits pick from the table, or solve within them
        Limits leaperI = Limits.forGap(3, 1.8);
        assertEquals(0.52, leaperI.maxVertical(), 1e-9, "its jump: 1.8 blocks");
        assertEquals(0.52, leaperI.maxHorizontal(), 1e-9, "the slowest leap over 3 blocks");
        assertEquals(0.42, table.lookup(3, 0, leaperI).vertical(), 1e-9);
        assertNull(table.lookup(8, 0, leaperI), "a 3-block leaper does not carry 8 blocks");
        assertNull(table.lookup(5, 0, leaperI));
        Limits leaperIII = Limits.forGap(8, 3.5);
        assertEquals(new Limits(0.75, 1.0), leaperIII);
        assertEquals(0.63, table.lookup(8, 0, leaperIII).vertical(), 1e-9);
        assertEquals(0, Limits.forGap(1, 3.5).maxHorizontal(), "no leap under a 2-block gap");
        // an acrobat III jumps only 1.5 blocks, but its 6-block gap is a flatter, faster leap within its jump
        Limits acrobat = Limits.forGap(6, 1.5);
        assertEquals(0.47, acrobat.maxVertical(), 1e-9);
        Leap six = table.lookup(6, 0, acrobat);
        assertTrue(six.vertical() <= 0.47 + 1e-9 && six.horizontal() > 0.82, six.toString());
        assertNotNull(table.lookup(9, 0, ANY), "outside the table it is solved");
    }

    @Test
    void impossibleLinksAreRefused() {
        // too wide for the limits
        assertNull(LeapSolver.solveGap(8, 0, Limits.forGap(5, 1.8), LeapSolver.flat(8, 0, 10)));
        // a rise of 3 across 5 with a plain jump
        assertNull(LeapSolver.solveGap(5, 3, Limits.forGap(5, 1.25), LeapSolver.flat(5, 3, 10)));
        // backwards
        assertNull(LeapSolver.solve(2, 0, 1, 0, ANY, LeapSolver.flat(3, 0, 10)));
        // a low ceiling over the gap: the box needs 2 blocks of headroom along the arc
        Slice capped = (LeapSolver.SolidSlice) (c, y) -> y < (c <= 0 ? 0 : c <= 4 ? -10 : 0) && y >= -11 || y == 2;
        assertNull(LeapSolver.solveGap(4, 0, ANY, capped), "a ceiling 2 blocks over the feet");
        Arc bump = LeapSolver.simulate(0, 0, 0.5, 0.5, capped, -64);
        assertEquals(LeapSolver.End.HEAD_BUMP, bump.end());
        // a wall in the middle of the arc
        Slice wall = (LeapSolver.SolidSlice) (c, y) -> c == 3 ? y < 4 && y >= -11 : y < (c <= 0 ? 0 : c <= 5 ? -10
                : 0) && y >= -11;
        assertNull(LeapSolver.solveGap(5, 0, Limits.forGap(6, 2.5), wall), "a 4-high wall over a 2.5-block jump");
        assertNotNull(LeapSolver.solveGap(5, 0, ANY, wall), "a 5.9-block jump clears it");
        assertEquals(LeapSolver.End.HIT_WALL, LeapSolver.simulate(0, 0, 0.72, 0.5, wall, -64).end());
        // into the gap
        assertEquals(LeapSolver.End.FELL, LeapSolver.simulate(0, 0, 0.2, 0.42, LeapSolver.flat(5, 0, 100), -20).end());
    }

    @Test
    void aFenceIsALandingOneAndAHalfHigh() {
        // the start, a 2-block gap, then a fence (1.5 tall) to land on
        Slice fence = (c, y) -> {
            if (c <= 0) {
                return y < 0 && y >= -5 ? 1 : 0;
            }
            if (c <= 2) {
                return 0;
            }
            return y == 0 ? 1.5 : y < 0 && y >= -5 ? 1 : 0;
        };
        Leap l = LeapSolver.solve(0, 0, 3, 1.5, ANY, fence);
        assertNotNull(l);
        assertTrue(LeapSolver.apex(l.vertical()) > 1.5);
        assertNull(LeapSolver.solve(0, 0, 3, 1.5, Limits.jump(1.25), fence), "a plain jump does not clear a fence");
    }

    @Test
    void anAcrobatsAirJumpCorrectsAnArcThatNoLongerLands() {
        // knocked short: at x = 2, feet 0.5 over the level, falling at 0.2 with no forward speed
        Slice s = LeapSolver.flat(5, 0, 10);
        assertTrue(!LeapSolver.stillLands(2, 0.5, 0, -0.2, 6, 0, s));
        double vx = LeapSolver.airJumpCorrection(2, 0.5, LeapSolver.PLAIN_JUMP, 6, 0, ANY, s);
        assertTrue(!Double.isNaN(vx));
        assertTrue(LeapSolver.stillLands(2, 0.5, vx, LeapSolver.PLAIN_JUMP, 6, 0, s));
        assertTrue(Double.isNaN(LeapSolver.airJumpCorrection(2, -3, LeapSolver.PLAIN_JUMP, 9, 0, new Limits(0.42, 0.3),
                LeapSolver.flat(8, 0, 10))), "too far to correct: it falls and the path is rebuilt");
    }

    @Test
    void honeySoulSandWaterAndArcChangingEffectsRefuseATakeOff() {
        assertNull(LeapSolver.refusal(TakeOff.STILL, false));
        assertEquals("in water", LeapSolver.refusal(new TakeOff(0, 1, 1, true, false, false, false, false, false),
                false));
        assertEquals("in powder snow", LeapSolver.refusal(new TakeOff(0, 1, 1, false, true, false, false, false,
                false), false));
        assertEquals("in a cobweb", LeapSolver.refusal(new TakeOff(0, 1, 1, false, false, true, false, false, false),
                false));
        assertEquals("on a block that holds a jump back (honey)", LeapSolver.refusal(new TakeOff(0, 0.5, 0.4, false,
                false, false, false, false, false), false));
        assertEquals("on a block that slows (soul sand)", LeapSolver.refusal(new TakeOff(0, 1, 0.4, false, false, false,
                false, false, false), false));
        String effect = "under an effect that changes the arc (Slowness, Levitation or Slow Falling)";
        assertEquals(effect, LeapSolver.refusal(new TakeOff(0, 1, 1, false, false, false, true, false, false), false));
        assertEquals(effect, LeapSolver.refusal(new TakeOff(0, 1, 1, false, false, false, false, true, false), false));
        assertEquals(effect, LeapSolver.refusal(new TakeOff(0, 1, 1, false, false, false, false, false, true), false));
        TakeOff running = new TakeOff(0.2, 1, 1, false, false, false, false, false, false);
        assertEquals("still moving", LeapSolver.refusal(running, false));
        assertNull(LeapSolver.refusal(running, true), "an acrobat's running take-off is re-solved where it is");
        assertNull(LeapSolver.refusal(new TakeOff(0.049, 1, 1, false, false, false, false, false, false), false));
        // a running take-off: solved from the actual position, not the cell centre
        Leap fromRun = LeapSolver.solve(0.3, 0, 5, 0, ANY, LeapSolver.flat(4, 0, 10));
        assertNotNull(fromRun);
        assertTrue(Math.abs(fromRun.landingX() - 5) <= LeapSolver.TOLERANCE + 1e-9);
    }
}
