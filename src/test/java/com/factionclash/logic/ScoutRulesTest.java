package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoutRulesTest {
    @Test
    void reportExperienceGrowsWithDistance() {
        assertEquals(12, ScoutRules.reportXp(12, 10));
        assertEquals(15, ScoutRules.reportXp(12, 100));
        assertTrue(ScoutRules.reportXp(3, 300) > ScoutRules.reportXp(3, 30));
    }

    @Test
    void overdueOnlyWhileAway() {
        assertFalse(ScoutRules.isOverdue(0, 100000, 24000));
        assertFalse(ScoutRules.isOverdue(1000, 20000, 24000));
        assertTrue(ScoutRules.isOverdue(1000, 30000, 24000));
    }
}
