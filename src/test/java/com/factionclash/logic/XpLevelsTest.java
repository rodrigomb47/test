package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpLevelsTest {
    @Test
    void vanillaLevelThresholds() {
        // the well-known totals: level 15 = 315 points, level 30 = 1395 points
        assertEquals(315, XpLevels.pointsFor(15));
        assertEquals(1395, XpLevels.pointsFor(30));
        assertEquals(0, XpLevels.levelFor(6));
        assertEquals(1, XpLevels.levelFor(7));
        assertEquals(30, XpLevels.levelFor(1395));
        assertEquals(29, XpLevels.levelFor(1394));
    }

    @Test
    void spendingKeepsProgressAndNeverGoesNegative() {
        long p = XpLevels.pointsFor(30) + 56; // half way into level 30 (112 needed)
        long after = XpLevels.spend(p, 3);
        assertEquals(27, XpLevels.levelFor(after));
        long into = after - XpLevels.pointsFor(27);
        assertTrue(into > 0 && into < XpLevels.needForNext(27), "progress into the level is kept: " + into);
        assertEquals(0, XpLevels.spend(XpLevels.pointsFor(2), 3));
    }
}
