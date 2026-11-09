package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMineTest {
    @Test
    void levelsReachOreBandsAndFaceTheTunnels() {
        int surface = 66;
        int prevY = surface;
        for (int level = 1; level <= 4; level++) {
            int n = LegacyMine.landingSteps(surface, level);
            assertTrue(n > 0);
            assertEquals(2, SpiralMine.cell(n)[0], "landing must be on the tunnel side of the shaft");
            int y = LegacyMine.levelY(surface, level);
            assertTrue(y <= prevY - 6, "each level is at least 6 deeper");
            prevY = y;
        }
        assertTrue(LegacyMine.levelY(surface, 2) <= 32);
        assertTrue(LegacyMine.levelY(surface, 3) <= 0);
        assertTrue(LegacyMine.levelY(surface, 4) <= -48 && LegacyMine.levelY(surface, 4) >= LegacyMine.MIN_Y);
        assertEquals(-1, LegacyMine.landingSteps(surface, 60), "no levels below the mining floor, nor endless infill");
    }

    @Test
    void infillLevelsFitBetweenExistingOnesOnceTheBottomIsReached() {
        int surface = 70;
        java.util.List<Integer> all = new java.util.ArrayList<>();
        int level = 1;
        for (int n = LegacyMine.landingSteps(surface, level); n > 0; n = LegacyMine.landingSteps(surface, ++level)) {
            assertEquals(2, SpiralMine.cell(n)[0], "every landing faces the tunnels");
            assertTrue(surface - n >= LegacyMine.MIN_Y);
            all.add(n);
        }
        int regular = 0;
        while (LegacyMine.isRegular(surface, regular + 1)) {
            regular++;
        }
        assertTrue(all.size() > regular, "infill levels exist after the bottom");
        int deepest = all.subList(0, regular).stream().mapToInt(Integer::intValue).max().orElseThrow();
        for (int i = regular; i < all.size(); i++) {
            assertTrue(all.get(i) < deepest, "infill levels lie above the bottom, inside the dug shaft");
        }
        java.util.List<Integer> sorted = new java.util.ArrayList<>(all);
        sorted.sort(null);
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i) - sorted.get(i - 1) >= 6, "levels at least 6 apart: " + sorted);
        }
    }

    @Test
    void regularLevelsOf70AtLeastFour() {
        assertTrue(LegacyMine.regularLevels(70) >= 4);
    }

    private static List<Integer> steps(LegacyMine.Migrated m) {
        return m.levels().stream().map(LegacyMine.Level::step).toList();
    }

    private static List<Integer> progress(LegacyMine.Migrated m) {
        return m.levels().stream().map(LegacyMine.Level::progress).toList();
    }

    private static List<Integer> rounds(LegacyMine.Migrated m) {
        return m.levels().stream().map(LegacyMine.Level::rounds).toList();
    }

    @Test
    void migrateTier3Mine() {
        LegacyMine.Migrated m = LegacyMine.migrate(66, 3, 0, 100, 0, 0, -1);
        assertEquals(List.of(6, 34, 66), steps(m));
        assertEquals(66, m.bottom());
        assertEquals(2, m.cur());
        assertEquals(List.of(0, 0, 100), progress(m));
        assertEquals(List.of(0, 0, 0), rounds(m));
        assertEquals(-1, m.upgradeStep());
    }

    @Test
    void migrateLengtheningMine() {
        LegacyMine.Migrated m = LegacyMine.migrate(66, 4, 1, 700, 2, 3, -1);
        assertEquals(List.of(6, 34, 66, 114, 122), steps(m));
        assertEquals(List.of(1, 1, 2, 2, 2), rounds(m), "levels above the one being lengthened are a round short");
        assertEquals(2, m.cur());
        assertEquals(List.of(0, 0, 700, 0, 0), progress(m));
        assertEquals(122, m.bottom());
        assertEquals(-1, m.upgradeStep());
    }

    @Test
    void migrateWhileDiggingDeeper() {
        LegacyMine.Migrated m = LegacyMine.migrate(66, 2, 0, 300, 0, 0, 37);
        assertEquals(List.of(6, 34, 66), steps(m));
        assertEquals(34, m.bottom(), "the next level is not reached yet");
        assertEquals(2, m.cur(), "the level being dug down to is the current one");
        assertEquals(37, m.upgradeStep(), "the shaft plan from the old bottom is the same, so its step is kept");
        assertEquals(List.of(0, 300, 0), progress(m), "the old deepest level keeps its progress");
    }

    @Test
    void migrateInfillUpgradeIsImmediate() {
        LegacyMine.Migrated m = LegacyMine.migrate(66, 4, 1, 250, 0, 0, 0);
        assertEquals(List.of(6, 34, 66, 114, 122, 90), steps(m), "the first infill level sits in the middle of the widest gap");
        assertEquals(5, m.cur());
        assertEquals(-1, m.upgradeStep(), "the shaft already passes an infill level");
        assertEquals(122, m.bottom());
        assertEquals(250, m.levels().get(4).progress(), "the old deepest level keeps its progress");
    }

    @Test
    void migrateCorruptOrTooLow() {
        LegacyMine.Migrated corrupt = LegacyMine.migrate(66, 0, 0, 10, 0, 0, -1);
        assertTrue(corrupt.levels().isEmpty());
        assertEquals(0, corrupt.bottom());
        assertEquals(-1, corrupt.cur());
        LegacyMine.Migrated low = LegacyMine.migrate(-55, 1, 0, 0, 0, 0, 3);
        assertTrue(low.levels().isEmpty(), "no level fits above the old mining floor");
        assertEquals(0, low.bottom());
        assertEquals(-1, low.cur());
        assertEquals(-1, low.upgradeStep());
    }

    @Test
    void newHutMineStartsWithItsFirstLevel() {
        LegacyMine.Migrated m = LegacyMine.migrate(70, 1, 0, 0, 0, 0, -1);
        assertEquals(List.of(6), steps(m));
        assertEquals(6, m.bottom());
        assertEquals(0, m.cur());
        assertEquals(-1, m.upgradeStep());
    }
}
