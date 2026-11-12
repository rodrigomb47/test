package com.factionclash.logic.hero;

import java.util.SplittableRandom;

/**
 * When a hero blinks (HEROES_V2 4.6 rule 5): the next blink after a random interval of its profile (normal 2.5-6 s,
 * nervous 1.5-3.5 s, calm 4-7 s, sleepy 3-5 s with a slow blink), a blink is half, closed, half (1, 2, 1 ticks; sleepy
 * 2, 4, 2), a double blink by chance (15%, two blinks two open ticks apart), seeded from the entity's UUID so heroes
 * never blink in step, and no blinking while the server flags a wind-up or a channel: a blink in progress opens at
 * once, and an overdue blink waits until a few ticks after the wind-up ends. Pure and deterministic for a seed and the
 * sequence of calls ({@code BlinkScheduleTest}); the client keeps one per hero and asks it every frame.
 */
public final class BlinkSchedule {
    /** The eye frame for "not blinking". */
    public static final int NONE = -1;
    /** Open ticks between the two blinks of a double blink. */
    public static final int DOUBLE_GAP = 2;
    /** An overdue blink waits at least this long after a wind-up or channel ends. */
    public static final int RESUME_DELAY = 6;

    /** A blink profile: the interval between blinks (seconds), the half/closed/half lengths (ticks), the double chance. */
    public record Profile(double minSeconds, double maxSeconds, int halfTicks, int closedTicks, int halfOutTicks, double doubleChance) {
        public static final Profile NORMAL = new Profile(2.5, 6.0, 1, 2, 1, 0.15);
        public static final Profile NERVOUS = new Profile(1.5, 3.5, 1, 2, 1, 0.15);
        public static final Profile CALM = new Profile(4.0, 7.0, 1, 2, 1, 0.15);
        /** A slow 0.4 s blink. */
        public static final Profile SLEEPY = new Profile(3.0, 5.0, 2, 4, 2, 0.15);

        /** The named profile ({@code normal}, {@code nervous}, {@code calm}, {@code sleepy}); unknown names are normal. */
        public static Profile named(String name) {
            return switch (name == null ? "" : name) {
                case "nervous" -> NERVOUS;
                case "calm" -> CALM;
                case "sleepy" -> SLEEPY;
                default -> NORMAL;
            };
        }

        /** A profile from a visual file's numbers; nonsense values fall back to {@code base}'s. */
        public static Profile of(Profile base, double min, double max, int half, int closed, int halfOut, double doubleChance) {
            boolean intervalOk = min > 0 && max >= min && max < 60;
            boolean ticksOk = half >= 0 && closed >= 1 && halfOut >= 0 && half + closed + halfOut <= 20;
            return new Profile(intervalOk ? min : base.minSeconds, intervalOk ? max : base.maxSeconds,
                    ticksOk ? half : base.halfTicks, ticksOk ? closed : base.closedTicks, ticksOk ? halfOut : base.halfOutTicks,
                    doubleChance >= 0 && doubleChance <= 1 ? doubleChance : base.doubleChance);
        }

        public int blinkTicks() {
            return halfTicks + closedTicks + halfOutTicks;
        }
    }

    private final Profile profile;
    private final SplittableRandom random;
    /** The tick the next blink starts. */
    private long next;
    /** The current blink's frames (one per tick) and the tick it started, or null. */
    private int[] sequence;
    private long started;
    private long last = Long.MIN_VALUE;

    /** A schedule seeded from {@code seed} (the entity's UUID bits); the first blink comes within one interval of {@code now}. */
    public BlinkSchedule(long seed, Profile profile, long now) {
        this.profile = profile;
        this.random = new SplittableRandom(seed);
        this.next = now + Math.round(random.nextDouble() * profile.maxSeconds * 20);
        this.last = now;
    }

    public Profile profile() {
        return profile;
    }

    /** The tick the next blink is due (for tests and the debug view). */
    public long nextBlink() {
        return next;
    }

    /**
     * The eye frame the blink shows at game tick {@code now}: {@link #NONE}, {@link FaceRules#EYES_HALF} or
     * {@link FaceRules#EYES_CLOSED}. {@code suppressed}: the server's wind-up or channel flag (no blinking).
     */
    public int eyeAt(long now, boolean suppressed) {
        if (now < last || now - last > 20L * 60) {
            // time went backwards (a reload) or a long gap (the hero was out of sight): start a fresh interval
            sequence = null;
            next = now + interval();
        }
        last = now;
        if (suppressed) {
            sequence = null;
            next = Math.max(next, now + 1 + RESUME_DELAY); // RESUME_DELAY open ticks after the last suppressed one
            return NONE;
        }
        if (sequence == null && now >= next) {
            sequence = sequence(random.nextDouble() < profile.doubleChance);
            started = next;
            if (now - started >= sequence.length) {
                started = now; // overdue (a wind-up just ended): blink now
            }
        }
        if (sequence != null) {
            long t = now - started;
            if (t >= 0 && t < sequence.length) {
                return sequence[(int) t];
            }
            if (t >= sequence.length) {
                next = started + sequence.length + interval();
                sequence = null;
            }
        }
        return NONE;
    }

    /** One blink, or two {@link #DOUBLE_GAP} open ticks apart, as one frame per tick. */
    private int[] sequence(boolean doubleBlink) {
        int one = profile.blinkTicks();
        int[] s = new int[doubleBlink ? one * 2 + DOUBLE_GAP : one];
        fill(s, 0);
        if (doubleBlink) {
            for (int i = one; i < one + DOUBLE_GAP; i++) {
                s[i] = NONE;
            }
            fill(s, one + DOUBLE_GAP);
        }
        return s;
    }

    private void fill(int[] s, int from) {
        int i = from;
        for (int k = 0; k < profile.halfTicks; k++) {
            s[i++] = FaceRules.EYES_HALF;
        }
        for (int k = 0; k < profile.closedTicks; k++) {
            s[i++] = FaceRules.EYES_CLOSED;
        }
        for (int k = 0; k < profile.halfOutTicks; k++) {
            s[i++] = FaceRules.EYES_HALF;
        }
    }

    /** A random interval of the profile, in ticks. */
    private long interval() {
        double s = profile.minSeconds + random.nextDouble() * (profile.maxSeconds - profile.minSeconds);
        return Math.max(1, Math.round(s * 20));
    }

    /** A seed from a UUID's two halves (so two heroes never share one). */
    public static long seed(long most, long least) {
        long z = most * 0x9E3779B97F4A7C15L ^ least;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
