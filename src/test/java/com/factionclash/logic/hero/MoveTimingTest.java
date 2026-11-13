package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MovePlan.Plan;
import com.factionclash.logic.hero.MoveTiming.Interrupt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveTiming} (HEROES_V2 4.8, 6.4; SLICE_PLAN 2 "Move runner"; task A5): the hero's speed from Haste, Fatigue,
 * Slowness and the attack-speed stat, a playback speed that never shortens a wind-up or a recovery under its class
 * minimum and that {@code DATA_ACTION} carries exactly, the plan scaled by it, and which interrupt stops what.
 */
class MoveTimingTest {
    @Test
    void speedComesFromHasteFatigueSlownessAndTheStat() {
        assertEquals(1.0, MoveTiming.speed(0, 0, 0, 0), 1e-9);
        assertEquals(1.2, MoveTiming.speed(0, 2, 0, 0), 1e-9, "Haste II: +10% a level");
        assertEquals(0.9, MoveTiming.speed(0, 0, 1, 0), 1e-9, "Mining Fatigue I: -10%");
        assertEquals(0.7, MoveTiming.speed(0, 0, 0, 2), 1e-9, "Slowness II: -15% a level");
        assertEquals(1.3, MoveTiming.speed(30, 0, 0, 0), 1e-9, "the attack_speed stat's +30%");
        assertEquals(HeroActions.MAX_SPEED, MoveTiming.speed(30, 10, 0, 0), 1e-9, "never above 2.0");
        assertEquals(HeroActions.MIN_SPEED, MoveTiming.speed(-30, 0, 5, 6), 1e-9, "never under 0.5");
        assertEquals(1.0, MoveTiming.speed(Double.NaN, 0, 0, 0), 1e-9);
    }

    @Test
    void playbackIsADataActionStepAtOrBelowTheRequest() {
        for (double s = 0.5; s <= 2.0; s += 0.013) {
            double q = MoveTiming.quantizeDown(s);
            assertTrue(q <= s + 1e-9, "never faster than asked: " + s + " -> " + q);
            assertEquals(q, HeroActions.speed(HeroActions.packAction(64, 0, q, 0)), 1e-9,
                    "the client plays exactly what the server divides by: " + q);
        }
        assertEquals(1.0, MoveTiming.quantizeDown(1.0), 1e-12, "1.0 is a step");
        assertEquals(2.0, MoveTiming.quantizeDown(2.0), 1e-12, "2.0 is a step");
    }

    @Test
    void playbackNeverShortensAWindupOrRecoveryUnderTheClassMinimum() {
        // a heavy move (0.8 s minimums) with a 0.9 s wind-up and 1.2 s recovery: at most 18/16 = 1.125 fast
        double s = MoveTiming.playback(2.0, 18, 24, MoveClass.HEAVY);
        assertTrue(s <= 1.125 + 1e-9 && s > 1.09, "held by the wind-up: " + s);
        assertTrue(Math.ceil(18 / s - 1e-9) >= 16, "the wind-up stays at 16 ticks or more");
        // the recovery can hold it too
        double r = MoveTiming.playback(2.0, 40, 18, MoveClass.HEAVY);
        assertTrue(r <= 18 / 16.0 + 1e-9, "held by the recovery: " + r);
        // slowing is never held back
        assertEquals(MoveTiming.quantizeDown(0.7), MoveTiming.playback(0.7, 16, 16, MoveClass.HEAVY), 1e-9);
        // a light move with room to spare plays at the full request
        assertEquals(2.0, MoveTiming.playback(2.0, 20, 18, MoveClass.LIGHT), 1e-9);
    }

    @Test
    void aScaledPlanDividesEveryTickAndRoundsUp() {
        Plan base = new Plan(20, new int[] {20, 26, 32}, 34, 50);
        Plan fast = MoveTiming.scale(base, 2.0, MoveClass.LIGHT);
        assertEquals(10, fast.windup(), "the hit lands windup / speed ticks after the start");
        assertArrayEquals(new int[] {10, 13, 16}, fast.hits());
        assertEquals(17, fast.activeEnd());
        assertEquals(17 + 8, fast.recoveryEnd(), "the 16-tick recovery halves");
        Plan same = MoveTiming.scale(base, 1.0, MoveClass.LIGHT);
        assertEquals(base, same, "speed 1 changes nothing");
        Plan slow = MoveTiming.scale(base, 0.5, MoveClass.LIGHT);
        assertEquals(40, slow.windup());
        assertArrayEquals(new int[] {40, 52, 64}, slow.hits());
    }

    @Test
    void aScaledPlanKeepsItsClassMinimumsAndItsHitsApart() {
        // a broken file: a heavy move with a 10-tick wind-up is held to 16, and its hits move with it
        Plan base = new Plan(10, new int[] {10, 11}, 12, 14);
        Plan p = MoveTiming.scale(base, 1.0, MoveClass.HEAVY);
        assertEquals(16, p.windup());
        assertArrayEquals(new int[] {16, 17}, p.hits());
        assertTrue(p.recoveryEnd() - p.activeEnd() >= 16, "the recovery minimum");
        // at 2x, hits one tick apart stay one tick apart
        Plan q = MoveTiming.scale(new Plan(20, new int[] {20, 21, 22}, 23, 40), 2.0, MoveClass.LIGHT);
        assertArrayEquals(new int[] {10, 11, 12}, q.hits());
    }

    @Test
    void aTimingFilesMarkersAreTheHits() {
        Move m = MovePlanTest.move("claws", "combo", "\"class\": \"light\", \"frames\": {\"windup\": 0.45, \"active\": 0.5,"
                + " \"recovery\": 0.35}, \"strikes\": [{\"effects\": [{\"type\": \"damage\", \"amount\": 2}]},"
                + " {\"effects\": [{\"type\": \"damage\", \"amount\": 2}]}, {\"effects\": [{\"type\": \"damage\","
                + " \"amount\": 3}]}]");
        Plan p = MovePlan.of(m, new Frames(0.5, 0.45, 0.4), new int[] {10, 13, 19});
        assertArrayEquals(new int[] {10, 13, 19}, p.hits(), "the clip's own hit markers");
        assertEquals(10, p.windup());
        Plan other = MovePlan.of(m, new Frames(0.5, 0.45, 0.4), new int[] {10, 13});
        assertEquals(3, other.hitCount(), "markers that do not match the strikes are not used");
        Plan bad = MovePlan.of(m, new Frames(0.5, 0.45, 0.4), new int[] {10, 9, 19});
        assertEquals(10, bad.hit(0));
        assertTrue(bad.hit(1) > bad.hit(0), "unsorted markers are not used");
    }

    @Test
    void whatStopsAMove() {
        // a stun or a stagger stops it at any stage
        for (MoveStage st : List.of(MoveStage.WINDUP, MoveStage.ACTIVE, MoveStage.RECOVERY)) {
            assertTrue(MoveTiming.stops(Interrupt.STUN, st, true, false), st.toString());
            assertTrue(MoveTiming.stops(Interrupt.STAGGER, st, false, false), st.toString());
        }
        assertFalse(MoveTiming.stops(Interrupt.STUN, MoveStage.DONE, false, false));
        // a knock-back only a wind-up, never through super armour or an ultimate
        assertTrue(MoveTiming.stops(Interrupt.KNOCKBACK, MoveStage.WINDUP, false, false));
        assertFalse(MoveTiming.stops(Interrupt.KNOCKBACK, MoveStage.ACTIVE, false, false));
        assertFalse(MoveTiming.stops(Interrupt.KNOCKBACK, MoveStage.WINDUP, true, false));
        // a channel only a channelled move, in its wind-up or active part
        assertTrue(MoveTiming.stops(Interrupt.CHANNEL, MoveStage.ACTIVE, false, true));
        assertFalse(MoveTiming.stops(Interrupt.CHANNEL, MoveStage.ACTIVE, false, false));
        assertFalse(MoveTiming.stops(Interrupt.CHANNEL, MoveStage.RECOVERY, false, true));
        // an escape a wind-up or an active part; a lost target only a wind-up
        assertTrue(MoveTiming.stops(Interrupt.ESCAPE, MoveStage.ACTIVE, true, false));
        assertFalse(MoveTiming.stops(Interrupt.ESCAPE, MoveStage.RECOVERY, false, false));
        assertTrue(MoveTiming.stops(Interrupt.TARGET_LOST, MoveStage.WINDUP, false, false));
        assertFalse(MoveTiming.stops(Interrupt.TARGET_LOST, MoveStage.ACTIVE, false, false));
    }

    @Test
    void halfTheCooldownComesBackOnlyForAWindupStoppedByForce() {
        assertTrue(MoveTiming.refunds(Interrupt.STUN, MoveStage.WINDUP));
        assertTrue(MoveTiming.refunds(Interrupt.KNOCKBACK, MoveStage.WINDUP));
        assertTrue(MoveTiming.refunds(Interrupt.STAGGER, MoveStage.WINDUP));
        assertTrue(MoveTiming.refunds(Interrupt.ESCAPE, MoveStage.WINDUP));
        assertFalse(MoveTiming.refunds(Interrupt.STUN, MoveStage.ACTIVE), "a move past its wind-up was used");
        assertFalse(MoveTiming.refunds(Interrupt.CHANNEL, MoveStage.WINDUP), "a broken channel is a punish");
        assertFalse(MoveTiming.refunds(Interrupt.TARGET_LOST, MoveStage.WINDUP), "a lost target keeps its whole charge");
    }

    @Test
    void interruptsPlayTheirClips() {
        assertEquals("stun", MoveTiming.clip(Interrupt.STUN));
        assertEquals("stagger", MoveTiming.clip(Interrupt.STAGGER));
        assertEquals("interrupt", MoveTiming.clip(Interrupt.KNOCKBACK));
        assertEquals("interrupt", MoveTiming.clip(Interrupt.CHANNEL));
        assertNull(MoveTiming.clip(Interrupt.TARGET_LOST));
        for (Interrupt i : Interrupt.values()) {
            String c = MoveTiming.clip(i);
            assertTrue(c == null || HeroActions.actionCode(c) > 0, "a code of appendix D: " + c);
        }
    }

    @Test
    void aKnockbackInterruptsAboveHalfAfterResistance() {
        assertFalse(MoveTiming.knockbackInterrupts(0.4, 0), "a plain blow");
        assertFalse(MoveTiming.knockbackInterrupts(0.5, 0), "a sprinting blow: not above 0.5");
        assertTrue(MoveTiming.knockbackInterrupts(1.0, 0), "Knockback I and a sprint");
        assertFalse(MoveTiming.knockbackInterrupts(1.0, 0.6), "a sturdy hero shrugs it off");
        assertTrue(MoveTiming.knockbackInterrupts(0.8, 0.3));
    }
}
