package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class AffixRollerTest {
    @Test
    void repeatedRollsNeverExceedCaps() {
        SplittableRandom r = new SplittableRandom(7);
        for (Affix.Job job : Affix.Job.values()) {
            Map<Affix, Integer> cur = new EnumMap<>(Affix.class);
            for (int i = 0; i < 500; i++) {
                Affix a = AffixRoller.roll(job, cur, r);
                if (a == null) {
                    break;
                }
                assertTrue(a.appliesTo(job));
                cur = AffixRoller.apply(cur, a);
                cur.forEach((k, v) -> assertTrue(v <= k.maxStacks, k + " exceeded cap"));
            }
            // once everything applicable is capped no further affix is granted
            assertNull(AffixRoller.roll(job, cur, r));
        }
    }

    @Test
    void jobAffixesOnlyForTheirJob() {
        assertTrue(Affix.PROSPECTOR.appliesTo(Affix.Job.MINER));
        assertFalse(Affix.PROSPECTOR.appliesTo(Affix.Job.WOODCUTTER));
        assertTrue(Affix.REGROWTH.appliesTo(Affix.Job.WOODCUTTER));
        assertTrue(Affix.VITALITY.appliesTo(Affix.Job.OTHER));
    }

    @Test
    void applyIsCappedEvenWhenForced() {
        Map<Affix, Integer> cur = new EnumMap<>(Affix.class);
        for (int i = 0; i < 20; i++) {
            cur = AffixRoller.apply(cur, Affix.PROSPECTOR);
        }
        assertEquals(Affix.PROSPECTOR.maxStacks, cur.get(Affix.PROSPECTOR));
        assertEquals(Affix.PROSPECTOR.maxStacks * Affix.PROSPECTOR.perStack, AffixRoller.total(cur, Affix.PROSPECTOR), 1e-9);
    }

    @Test
    void experienceCurveGrantsOneRollPerLevel() {
        AffixRoller.LevelResult r = AffixRoller.addXp(0, 0, 39);
        assertEquals(0, r.levelsGained());
        r = AffixRoller.addXp(0, 39, 1);
        assertEquals(1, r.levelsGained());
        assertEquals(1, r.level());
        assertEquals(0, r.xp());
        int big = AffixRoller.xpToNext(0) + AffixRoller.xpToNext(1) + AffixRoller.xpToNext(2) + 5;
        r = AffixRoller.addXp(0, 0, big);
        assertEquals(3, r.levelsGained());
        assertEquals(5, r.xp());
        assertEquals(0, AffixRoller.addXp(5, 10, 0).levelsGained());
        assertEquals(AffixRoller.MAX_LEVEL, AffixRoller.addXp(0, 0, 10_000_000).level());
    }
}
