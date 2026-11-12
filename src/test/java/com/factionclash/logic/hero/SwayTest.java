package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwayTest {
    private static final double FRAME = 1 / 60.0;

    @Test
    void aTurnLeavesTheChainBehindAndItSettles() {
        Sway s = new Sway(0.35, 0.6);
        assertEquals(0, s.step(0, FRAME), 1e-9, "starts on its target");
        double lag = 0;
        // the body turns 90 degrees in a quarter second
        for (int i = 1; i <= 15; i++) {
            lag = s.step(i * 6.0, FRAME);
        }
        assertTrue(lag < -3, "the chain trails the turn: " + lag);
        double peakOver = 0;
        for (int i = 0; i < 240; i++) {
            lag = s.step(90, FRAME);
            peakOver = Math.max(peakOver, lag);
        }
        assertTrue(peakOver > 0.1, "it swings through: " + peakOver);
        assertEquals(0, lag, 0.05, "and settles");
    }

    @Test
    void aSteadyBodyNeverMovesTheChainAndTheLagIsCapped() {
        Sway s = new Sway(0.5, 0.7);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, s.step(37, FRAME), 1e-9);
        }
        Sway fast = new Sway(0.0, 0.0);
        fast.step(0, FRAME);
        double lag = fast.step(170, 0.25);
        assertTrue(Math.abs(lag) <= Sway.LIMIT + 1e-9, "capped: " + lag);
    }

    @Test
    void aTurnThroughTheSeamIsShort() {
        Sway s = new Sway(0.4, 0.75);
        s.step(179, FRAME);
        double lag = s.step(-179, FRAME);
        assertTrue(Math.abs(lag) < 3, "two degrees of turn, not 358: " + lag);
        assertEquals(-179 + 360, Sway.unwrap(-179, 179), 1e-9);
        assertEquals(10, Sway.unwrap(370, 0), 1e-9);
    }
}
