package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlinkScheduleTest {
    /** The frames of every tick from 0 to {@code ticks}. */
    private static int[] run(BlinkSchedule b, int ticks) {
        int[] out = new int[ticks];
        for (int t = 0; t < ticks; t++) {
            out[t] = b.eyeAt(t, false);
        }
        return out;
    }

    /** Start ticks of each blink (a run of non-NONE frames after an open tick). */
    private static List<Integer> starts(int[] f) {
        List<Integer> s = new ArrayList<>();
        for (int t = 0; t < f.length; t++) {
            if (f[t] != BlinkSchedule.NONE && (t == 0 || f[t - 1] == BlinkSchedule.NONE)) {
                s.add(t);
            }
        }
        return s;
    }

    @Test
    void aBlinkIsHalfClosedClosedHalf() {
        int[] f = run(new BlinkSchedule(42, BlinkSchedule.Profile.NORMAL, 0), 2000);
        int s = starts(f).get(0);
        assertEquals(FaceRules.EYES_HALF, f[s]);
        assertEquals(FaceRules.EYES_CLOSED, f[s + 1]);
        assertEquals(FaceRules.EYES_CLOSED, f[s + 2]);
        assertEquals(FaceRules.EYES_HALF, f[s + 3]);
    }

    @Test
    void intervalsStayInTheProfileAndSomeBlinksAreDouble() {
        for (BlinkSchedule.Profile p : List.of(BlinkSchedule.Profile.NORMAL, BlinkSchedule.Profile.NERVOUS, BlinkSchedule.Profile.CALM)) {
            int[] f = run(new BlinkSchedule(7, p, 0), 20 * 60 * 20);
            List<Integer> s = starts(f);
            int doubles = 0, singles = 0;
            for (int i = 1; i < s.size(); i++) {
                int gap = s.get(i) - s.get(i - 1);
                if (gap == p.blinkTicks() + BlinkSchedule.DOUBLE_GAP) {
                    doubles++;
                    continue;
                }
                singles++;
                // from the end of a (double) blink to the next start: the profile's interval, within a tick
                int end = s.get(i - 1) + p.blinkTicks();
                if (i >= 2 && s.get(i - 1) - s.get(i - 2) == p.blinkTicks() + BlinkSchedule.DOUBLE_GAP) {
                    end = s.get(i - 1) + p.blinkTicks();
                }
                int interval = s.get(i) - end;
                assertTrue(interval >= p.minSeconds() * 20 - 1 && interval <= p.maxSeconds() * 20 + 1,
                        p + ": interval " + interval + " ticks");
            }
            double share = doubles / (double) (doubles + singles);
            assertTrue(share > 0.07 && share < 0.25, p + ": double blinks " + share);
        }
    }

    @Test
    void sleepyBlinksAreSlow() {
        int[] f = run(new BlinkSchedule(3, BlinkSchedule.Profile.SLEEPY, 0), 4000);
        int s = starts(f).get(0);
        int len = 0;
        while (f[s + len] != BlinkSchedule.NONE) {
            len++;
        }
        assertTrue(len == 8 || len == 8 * 2 + BlinkSchedule.DOUBLE_GAP, "a 0.4 s blink: " + len);
        assertEquals(BlinkSchedule.Profile.SLEEPY, BlinkSchedule.Profile.named("sleepy"));
        assertEquals(BlinkSchedule.Profile.NORMAL, BlinkSchedule.Profile.named("whatever"));
    }

    @Test
    void theSeedDecidesSoHeroesNeverBlinkInStep() {
        int[] a = run(new BlinkSchedule(BlinkSchedule.seed(1, 2), BlinkSchedule.Profile.NORMAL, 0), 6000);
        int[] a2 = run(new BlinkSchedule(BlinkSchedule.seed(1, 2), BlinkSchedule.Profile.NORMAL, 0), 6000);
        int[] b = run(new BlinkSchedule(BlinkSchedule.seed(1, 3), BlinkSchedule.Profile.NORMAL, 0), 6000);
        assertEquals(starts(a), starts(a2), "deterministic for a seed");
        assertNotEquals(starts(a), starts(b), "two UUIDs, two rhythms");
        assertTrue(starts(a).get(0) <= 6 * 20, "the first blink comes within one interval");
    }

    @Test
    void noBlinkDuringAWindupAndAnOverdueBlinkWaitsAfterIt() {
        BlinkSchedule b = new BlinkSchedule(11, BlinkSchedule.Profile.NERVOUS, 0);
        long due = b.nextBlink();
        // a wind-up from just before the blink was due until 3 s later
        for (long t = 0; t < due - 1; t++) {
            assertEquals(BlinkSchedule.NONE, b.eyeAt(t, false));
        }
        for (long t = due - 1; t < due + 60; t++) {
            assertEquals(BlinkSchedule.NONE, b.eyeAt(t, true), "blinked during a wind-up at " + t);
        }
        long end = due + 60;
        for (long t = end; t < end + BlinkSchedule.RESUME_DELAY; t++) {
            assertEquals(BlinkSchedule.NONE, b.eyeAt(t, false), "too soon after the wind-up at " + t);
        }
        assertEquals(FaceRules.EYES_HALF, b.eyeAt(end + BlinkSchedule.RESUME_DELAY, false), "the overdue blink comes");
    }

    @Test
    void aWindupOpensABlinkInProgress() {
        BlinkSchedule b = new BlinkSchedule(5, BlinkSchedule.Profile.NORMAL, 0);
        long due = b.nextBlink();
        for (long t = 0; t <= due + 1; t++) {
            b.eyeAt(t, false);
        }
        assertEquals(BlinkSchedule.NONE, b.eyeAt(due + 2, true), "eyes open at once: the concentration tell");
    }

    @Test
    void aLongGapOrTimeGoingBackStartsAFreshInterval() {
        BlinkSchedule b = new BlinkSchedule(9, BlinkSchedule.Profile.NORMAL, 0);
        b.eyeAt(10, false);
        b.eyeAt(100_000, false);
        assertTrue(b.nextBlink() > 100_000 && b.nextBlink() <= 100_000 + 6 * 20 + 1, "fresh after a gap: " + b.nextBlink());
        b.eyeAt(50, false);
        assertTrue(b.nextBlink() > 50 && b.nextBlink() <= 50 + 6 * 20 + 1, "fresh after a reload: " + b.nextBlink());
    }

    @Test
    void profilesFromTheVisualFileFallBackOnNonsense() {
        BlinkSchedule.Profile p = BlinkSchedule.Profile.of(BlinkSchedule.Profile.NORMAL, 4, 7, 1, 2, 1, 0.15);
        assertEquals(BlinkSchedule.Profile.CALM, p);
        BlinkSchedule.Profile bad = BlinkSchedule.Profile.of(BlinkSchedule.Profile.NORMAL, 7, 4, 0, 0, 0, 3);
        assertEquals(BlinkSchedule.Profile.NORMAL, bad);
    }
}
