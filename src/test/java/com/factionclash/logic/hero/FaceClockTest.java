package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceClockTest {
    @Test
    void theTalkingMouthCyclesEveryTwoOrThreeTicks() {
        FaceClock c = new FaceClock(1);
        Set<Integer> seen = new HashSet<>();
        int last = -1, run = 0, longest = 0, changes = 0;
        for (long t = 0; t < 400; t++) {
            int m = c.talkMouth(t);
            seen.add(m);
            if (m == last) {
                run++;
            } else {
                if (last >= 0) {
                    changes++;
                }
                run = 1;
            }
            longest = Math.max(longest, run);
            last = m;
        }
        assertEquals(Set.of(FaceRules.MOUTH_TALK_A, FaceRules.MOUTH_TALK_B, FaceRules.MOUTH_NEUTRAL), seen);
        assertTrue(longest <= 3, "a frame held " + longest + " ticks");
        assertTrue(changes > 400 / 6, "the mouth moves: " + changes + " changes in 20 s");
    }

    @Test
    void aStopRestartsOnTalkA() {
        FaceClock c = new FaceClock(2);
        for (long t = 0; t < 7; t++) {
            c.talkMouth(t);
        }
        c.stopTalking();
        int m = c.talkMouth(8);
        assertEquals(FaceRules.MOUTH_TALK_A, m, "a new line opens the mouth");
    }

    @Test
    void aMoodHoldsTenTicksBeforeTheNextShows() {
        FaceClock c = new FaceClock(3);
        assertEquals(0, c.mood(100, 0));
        assertEquals(2, c.mood(101, 2), "the first change shows at once (calm held long enough)");
        assertEquals(2, c.mood(105, 4), "held");
        assertEquals(2, c.mood(110, 4), "still held at 9 ticks");
        assertEquals(4, c.mood(111, 4), "shows after 10");
        assertEquals(4, c.mood(112, 0));
        assertEquals(4, c.mood(120, 4), "a flicker back within the hold never shows");
    }
}
