package com.factionclash.logic.hero;

import java.util.SplittableRandom;

/**
 * The face's two other clocks (HEROES_V2 4.6): the talking mouth (talk A, talk B, neutral, each held 2-3 ticks, with a
 * little noise: now and then a step is skipped) and the mood hold (a new mood
 * shows only once the shown one has held {@link #MOOD_HOLD} ticks, so a mood flickering on the server never flickers
 * the face). Pure and deterministic for a seed ({@code FaceClockTest}).
 */
public final class FaceClock {
    /** A mood change holds at least this long before the next one shows. */
    public static final int MOOD_HOLD = 10;

    private final SplittableRandom random;

    private int talkBeat = -1;
    private long talkUntil = Long.MIN_VALUE;

    private int shownMood = -1;
    private long moodSince = Long.MIN_VALUE;

    public FaceClock(long seed) {
        this.random = new SplittableRandom(seed ^ 0x5DEECE66DL);
    }

    /**
     * The talking mouth frame at tick {@code now} (called while the talking flag is set): beats of 2-3 ticks, mostly
     * talk A, talk B, neutral in turn, now and then skipping a step; never the same frame two beats running.
     */
    public int talkMouth(long now) {
        if (talkBeat < 0 || now >= talkUntil || now < talkUntil - 3) {
            talkBeat = talkBeat < 0 ? FaceRules.MOUTH_TALK_A : nextBeat(talkBeat);
            talkUntil = now + 2 + random.nextInt(2);
        }
        return talkBeat;
    }

    private int nextBeat(int beat) {
        boolean skip = random.nextInt(5) == 0;
        return switch (beat) {
            case FaceRules.MOUTH_TALK_A -> skip ? FaceRules.MOUTH_NEUTRAL : FaceRules.MOUTH_TALK_B;
            case FaceRules.MOUTH_TALK_B -> skip ? FaceRules.MOUTH_TALK_A : FaceRules.MOUTH_NEUTRAL;
            default -> skip ? FaceRules.MOUTH_TALK_B : FaceRules.MOUTH_TALK_A;
        };
    }

    /** Stops the talking cycle (the next line starts on talk A). */
    public void stopTalking() {
        talkBeat = -1;
        talkUntil = Long.MIN_VALUE;
    }

    /** The mood shown at tick {@code now} when the server says {@code wanted}. */
    public int mood(long now, int wanted) {
        if (shownMood < 0 || now < moodSince) {
            shownMood = wanted;
            moodSince = now - MOOD_HOLD; // the first mood seen is not a change: the next one may show at once
        } else if (wanted != shownMood && now - moodSince >= MOOD_HOLD) {
            shownMood = wanted;
            moodSince = now;
        }
        return shownMood;
    }
}
