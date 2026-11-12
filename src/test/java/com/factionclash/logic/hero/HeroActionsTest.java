package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroActionsTest {
    @Test
    void anActionPacksItsFourParts() {
        int a = HeroActions.packAction(17, 5, 1.0, 9);
        assertEquals(17, HeroActions.code(a));
        assertEquals(5, HeroActions.sequence(a));
        assertEquals(1.0, HeroActions.speed(a), 1e-12, "step 21 is exactly 1.0");
        assertEquals(9, HeroActions.variant(a));
        assertEquals(0, a >>> 24, "bits 24-31 stay reserved");
        int max = HeroActions.packAction(255, 15, 2.0, 63);
        assertEquals(255, HeroActions.code(max));
        assertEquals(15, HeroActions.sequence(max));
        assertEquals(2.0, HeroActions.speed(max), 1e-12);
        assertEquals(63, HeroActions.variant(max));
    }

    @Test
    void speedIsClampedAndRoundedToItsSteps() {
        assertEquals(0.5, HeroActions.speed(HeroActions.packAction(1, 0, 0.1, 0)), 1e-12);
        assertEquals(2.0, HeroActions.speed(HeroActions.packAction(1, 0, 9.0, 0)), 1e-12);
        assertEquals(HeroActions.NORMAL_SPEED_STEP, HeroActions.speedStep(Double.NaN));
        double s = HeroActions.speed(HeroActions.packAction(1, 0, 1.3, 0));
        assertTrue(Math.abs(s - 1.3) <= 1.5 / 63 / 2 + 1e-12, "within half a step: " + s);
    }

    @Test
    void aNewActionAdvancesTheSequenceSoARepeatRestarts() {
        int a = HeroActions.packAction(1, 15, 1.0, 0);
        int b = HeroActions.nextAction(a, 1, 1.0, 0);
        assertEquals(0, HeroActions.sequence(b), "wraps after 15");
        assertNotEquals(a, b, "the same attack again is a different value, so the client sees it");
        assertEquals(1, HeroActions.sequence(HeroActions.nextAction(b, 2, 1.0, 0)));
    }

    @Test
    void stateBitsAreIndependent() {
        byte s = 0;
        s = HeroActions.with(s, HeroActions.STATE_TALKING, true);
        assertEquals(1 << 5, s & 0xFF, "talking is bit 5");
        assertTrue(HeroActions.has(s, HeroActions.STATE_TALKING));
        assertFalse(HeroActions.has(s, HeroActions.STATE_WINDUP));
        s = HeroActions.withPhase(s, 3);
        assertEquals(3, HeroActions.phase(s));
        assertTrue(HeroActions.has(s, HeroActions.STATE_TALKING), "the phase leaves the flags alone");
        s = HeroActions.with(s, HeroActions.STATE_YIELDING, true);
        assertTrue(s < 0, "bit 7 is the sign bit of the byte");
        assertTrue(HeroActions.has(s, HeroActions.STATE_YIELDING));
        s = HeroActions.with(s, HeroActions.STATE_TALKING, false);
        assertFalse(HeroActions.has(s, HeroActions.STATE_TALKING));
        assertEquals(3, HeroActions.phase(s));
        assertEquals(1, HeroActions.phase(0), "a fresh hero is in phase 1");
        assertEquals(4, HeroActions.phase(HeroActions.withPhase(0, 9)), "clamped to 4");
    }

    @Test
    void moodPacksAndUnknownCodesReadAsCalm() {
        byte m = HeroActions.packMood(HeroActions.moodCode("flustered"), 2, true, false);
        assertEquals("flustered", HeroActions.MOODS.get(HeroActions.mood(m)));
        assertEquals(2, HeroActions.moodIntensity(m));
        assertTrue(HeroActions.blush(m));
        assertFalse(HeroActions.eyesGlow(m));
        assertTrue(HeroActions.eyesGlow(HeroActions.packMood(0, 0, false, true)));
        assertEquals(0, HeroActions.mood(15), "a stray code reads as calm");
        assertEquals(12, HeroActions.MOODS.size());
        assertEquals(0, HeroActions.moodCode("calm"), "a fresh hero (all bits 0) is calm");
        assertEquals(-1, HeroActions.moodCode("bored"));
    }

    @Test
    void locoPacksModeOneShotToggleAndTired() {
        byte l = HeroActions.packLoco(HeroActions.LOCO_MODES.indexOf("glide"), HeroActions.LOCO_ONE_SHOT_START, true, true);
        assertEquals("glide", HeroActions.LOCO_MODES.get(HeroActions.locoMode(l)));
        assertEquals(HeroActions.LOCO_ONE_SHOT_START, HeroActions.locoOneShot(l));
        assertTrue(HeroActions.locoToggle(l));
        assertTrue(HeroActions.locoTired(l));
        assertEquals("ground", HeroActions.LOCO_MODES.get(HeroActions.locoMode(0)), "a fresh hero walks");
        assertEquals(16, HeroActions.LOCO_MODES.size());
    }

    @Test
    void theActionTableFollowsAppendixD() {
        assertNull(HeroActions.actionName(0));
        assertEquals("attack.1", HeroActions.actionName(1));
        assertEquals("attack.ranged", HeroActions.actionName(4));
        assertEquals("hurt.heavy", HeroActions.actionName(11));
        assertEquals("eat", HeroActions.actionName(15));
        assertEquals("founding", HeroActions.actionName(23));
        assertEquals("state.3", HeroActions.actionName(26));
        assertNull(HeroActions.actionName(27));
        assertEquals("work.mine", HeroActions.actionName(32));
        assertEquals("work.eat", HeroActions.actionName(47));
        assertEquals("emote.greet", HeroActions.actionName(48));
        assertEquals("personal.3", HeroActions.actionName(60));
        assertEquals("move.0", HeroActions.actionName(64));
        assertEquals("move.11", HeroActions.actionName(75));
        assertEquals("ultimate", HeroActions.actionName(76));
        assertEquals("ultimate.awakened", HeroActions.actionName(78));
        assertNull(HeroActions.actionName(79));
        assertNull(HeroActions.actionName(200));
        Set<String> names = new HashSet<>();
        for (int c = 1; c < 256; c++) {
            String n = HeroActions.actionName(c);
            if (n != null) {
                assertTrue(names.add(n), "duplicate name " + n);
                assertEquals(c, HeroActions.actionCode(n), "round trip " + n);
            }
        }
        assertEquals(-1, HeroActions.actionCode("attack.9"));
    }
}
