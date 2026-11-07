package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StuckRecoveryTest {
    @Test
    void movesWithinTheDefaultRange() {
        StuckRecovery.Spot s = StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> true);
        assertNotNull(s);
        assertTrue(Math.abs(s.x()) <= StuckRecovery.MAX_HORIZONTAL && Math.abs(s.z()) <= StuckRecovery.MAX_HORIZONTAL);
        assertTrue(Math.abs(s.y() - 64) <= StuckRecovery.MAX_VERTICAL);
        assertEquals(4, s.x(), "should hop as far toward the goal as allowed (default 4)");
    }

    @Test
    void respectsAConfiguredRange() {
        StuckRecovery.Spot s = StuckRecovery.choose(0, 64, 0, 20.5, 70, 0.5, 2, 1, (x, y, z) -> true);
        assertNotNull(s);
        assertEquals(2, s.x());
        assertEquals(65, s.y(), "one block up at most");
        StuckRecovery.Spot far = StuckRecovery.choose(0, 64, 0, 20.5, 70, 0.5, 8, 4, (x, y, z) -> true);
        assertEquals(8, far.x());
        assertEquals(68, far.y());
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
        assertNull(StuckRecovery.choose(0, 64, 0, 20.5, 64, 0.5, (x, y, z) -> x < 0));
    }
}
