package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureRulesTest {
    @Test
    void classifiesTheThreeKinds() {
        assertEquals(StructureRules.Sight.VILLAGE, StructureRules.classify("minecraft:village_plains", true));
        assertEquals(StructureRules.Sight.VILLAGE, StructureRules.classify("somepack:village_jungle", true));
        assertEquals(StructureRules.Sight.PILLAGER_OUTPOST, StructureRules.classify("minecraft:pillager_outpost", false));
        assertEquals(StructureRules.Sight.WOODLAND_MANSION, StructureRules.classify("minecraft:mansion", false));
        assertNull(StructureRules.classify("minecraft:stronghold", false));
        assertNull(StructureRules.classify("minecraft:mineshaft", false));
        assertNull(StructureRules.classify("minecraft:village_plains", false), "only the village tag makes a village");
        assertNull(StructureRules.classify(null, false));
    }

    @Test
    void gapToABox() {
        assertEquals(0.0, StructureRules.gap(5.5, 5.5, 0, 0, 10, 10), 1.0E-9, "inside");
        assertEquals(10.0, StructureRules.gap(-10, 5, 0, 0, 10, 10), 1.0E-9, "west of it");
        assertEquals(9.0, StructureRules.gap(20, 5, 0, 0, 10, 10), 1.0E-9, "east of it (the box ends at 11)");
        assertEquals(5.0, StructureRules.gap(-3, -4, 0, 0, 10, 10), 1.0E-9, "off a corner");
    }

    @Test
    void sightReachesFortyEightBlocksFromTheBounds() {
        assertTrue(StructureRules.inSight(-48, 5, 0, 0, 10, 10));
        assertFalse(StructureRules.inSight(-49, 5, 0, 0, 10, 10));
        assertTrue(StructureRules.inSight(5, 5, 0, 0, 10, 10));
    }

    @Test
    void villageNames() {
        assertEquals("plains village", StructureRules.villageName("minecraft:village_plains"));
        assertEquals("snowy village", StructureRules.villageName("minecraft:village_snowy"));
        assertEquals("village", StructureRules.villageName("somepack:hamlet"));
        assertEquals("village", StructureRules.villageName(null));
    }

    @Test
    void aLookFromAfarDoesNotEraseKnownTraders() {
        assertEquals(5, StructureRules.mergedTraders(5, 0));
        assertEquals(3, StructureRules.mergedTraders(5, 3), "a newer count of traders replaces the old one");
        assertEquals(0, StructureRules.mergedTraders(0, 0));
        assertEquals(4, StructureRules.mergedTraders(0, 4));
    }
}
