package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StuckRecoveryTest {
    @Test
    void movesAtMostTwoAcrossAndOneVertically() {
        StuckRecovery.Spot s = StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> true);
        assertNotNull(s);
        assertTrue(Math.abs(s.x()) <= 2 && Math.abs(s.z()) <= 2);
        assertTrue(Math.abs(s.y() - 64) <= 1);
        assertEquals(2, s.x(), "should step toward the goal");
    }

    @Test
    void onlyChoosesSafeSpots() {
        StuckRecovery.Spot s = StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> x == 1 && z == 1 && y == 65);
        assertEquals(new StuckRecovery.Spot(1, 65, 1), s);
        assertNull(StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> false));
    }

    @Test
    void neverMovesAwayFromTheGoal() {
        // the only safe spot is behind the NPC: no teleport
        assertNull(StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> x == -2));
    }
}
