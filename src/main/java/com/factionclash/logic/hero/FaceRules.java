package com.factionclash.logic.hero;

import java.util.Arrays;
import java.util.List;

/**
 * Which face frame each face plane shows (HEROES_V2 4.6): the eye, mouth and cheek frames a hero's {@code eyes},
 * {@code mouth} and {@code cheeks} planes are shifted to, by the priority rules of 4.6, and where a frame sits in the
 * texture. The client's {@code HeroFace} feeds it every frame; the clocks (blinks, the talking mouth, the mood hold)
 * are {@link BlinkSchedule} and {@link FaceClock}. Pure, unit-tested ({@code FaceRulesTest}).
 *
 * <p>Priority, highest first: (1) dead: eyes closed, mouth neutral, no cheeks; (6, applied second because it freezes
 * everything below it) beyond 16 blocks the face stays on the mood frame; (2) an action animation's authored
 * {@code face_ctrl} keys, per plane; (3) talking: the mouth cycles; (4) the mood's frames, with the mood byte's glowing
 * eyes and blush; (5) a blink, only over the open-type eyes the visual file lists ({@code blink.over_eyes}).
 */
public final class FaceRules {
    private FaceRules() {
    }

    // eye frames (the 4 x 3 strip of 20 x 10 texels)
    public static final int EYES_OPEN = 0, EYES_HALF = 1, EYES_CLOSED = 2, EYES_HAPPY = 3, EYES_ANGRY = 4, EYES_SAD = 5,
            EYES_SURPRISED = 6, EYES_PAIN = 7, EYES_FOCUSED = 8, EYES_SMUG = 9, EYES_GLOWING = 10, EYES_SLEEPING = 11;
    public static final int EYE_FRAMES = 12;
    // mouth frames (8 of 8 x 3)
    public static final int MOUTH_NEUTRAL = 0, MOUTH_SMILE = 1, MOUTH_TALK_A = 2, MOUTH_TALK_B = 3, MOUTH_GRIT = 4,
            MOUTH_FROWN = 5, MOUTH_CAT = 6, MOUTH_FANG_GRIN = 7;
    public static final int MOUTH_FRAMES = 8;
    // cheek frames (4 of 16 x 2)
    public static final int CHEEKS_NONE = 0, CHEEKS_BLUSH = 1, CHEEKS_SWEAT = 2, CHEEKS_TEARS = 3;
    public static final int CHEEK_FRAMES = 4;

    /** Beyond this distance (blocks) the face is frozen on its mood frame and the cheeks plane is hidden. */
    public static final double FACE_DISTANCE = 16;
    /** An authored {@code face_ctrl} value counts only within this of a whole number (transitions pass fractions). */
    public static final double WHOLE_NUMBER_TOLERANCE = 0.05;
    /** Eyes a blink may close: open, angry, sad, focused, smug (the visual file can list others). */
    public static final List<Integer> DEFAULT_BLINK_OVER = List.of(EYES_OPEN, EYES_ANGRY, EYES_SAD, EYES_FOCUSED, EYES_SMUG);

    /** One frame per plane. */
    public record Frames(int eyes, int mouth, int cheeks) {
        public static final Frames CALM = new Frames(EYES_OPEN, MOUTH_NEUTRAL, CHEEKS_NONE);
    }

    /**
     * The default mood table (eyes / mouth / cheeks, HEROES_V2 4.6 and the generator's table): what a hero without
     * its own table shows; a hero's {@code visual.json} carries its resolved table (overrides, blush and fang applied).
     */
    public static final List<Frames> DEFAULT_MOODS = List.of(
            new Frames(EYES_OPEN, MOUTH_NEUTRAL, CHEEKS_NONE),       // calm
            new Frames(EYES_HAPPY, MOUTH_SMILE, CHEEKS_NONE),        // happy
            new Frames(EYES_ANGRY, MOUTH_GRIT, CHEEKS_NONE),         // angry
            new Frames(EYES_FOCUSED, MOUTH_NEUTRAL, CHEEKS_NONE),    // focused
            new Frames(EYES_PAIN, MOUTH_GRIT, CHEEKS_NONE),          // hurt
            new Frames(EYES_HALF, MOUTH_NEUTRAL, CHEEKS_SWEAT),      // tired
            new Frames(EYES_SAD, MOUTH_FROWN, CHEEKS_NONE),          // sad
            new Frames(EYES_SMUG, MOUTH_SMILE, CHEEKS_NONE),         // smug
            new Frames(EYES_SURPRISED, MOUTH_TALK_A, CHEEKS_NONE),   // surprised
            new Frames(EYES_SLEEPING, MOUTH_NEUTRAL, CHEEKS_NONE),   // asleep
            new Frames(EYES_SURPRISED, MOUTH_FROWN, CHEEKS_BLUSH),   // flustered
            new Frames(EYES_ANGRY, MOUTH_NEUTRAL, CHEEKS_NONE));     // determined

    /**
     * The value an action animation keyed on {@code face_ctrl} (eye, mouth or cheek frame + 1 on x, y or z) as a
     * frame: -1 for "no override" (0, out of range, or not within 0.05 of a whole number, which is what a controller
     * transition or GeckoLib's reset lerp passes through for a few ticks).
     */
    public static int authored(double value, int frames) {
        if (!Double.isFinite(value)) {
            return -1;
        }
        double whole = Math.rint(value);
        if (Math.abs(value - whole) > WHOLE_NUMBER_TOLERANCE) {
            return -1;
        }
        int n = (int) whole;
        return n >= 1 && n <= frames ? n - 1 : -1;
    }

    /** What the face sees this frame. */
    public record Input(boolean dead, boolean far, Frames mood, boolean eyesGlow, boolean blush, int authoredEyes,
                        int authoredMouth, int authoredCheeks, boolean talking, int talkMouth, int blinkEyes,
                        List<Integer> blinkOver) {
    }

    /** The frames shown, by the priority of 4.6 (see the class comment). */
    public static Frames choose(Input in) {
        if (in.dead()) {
            return new Frames(EYES_CLOSED, MOUTH_NEUTRAL, CHEEKS_NONE);
        }
        Frames m = in.mood() == null ? Frames.CALM : in.mood();
        List<Integer> over = in.blinkOver() == null ? DEFAULT_BLINK_OVER : in.blinkOver();
        boolean openType = over.contains(m.eyes());
        int eyes = in.eyesGlow() && openType ? EYES_GLOWING : m.eyes();
        int mouth = m.mouth();
        int cheeks = in.blush() && m.cheeks() == CHEEKS_NONE ? CHEEKS_BLUSH : m.cheeks();
        if (in.far()) {
            return new Frames(eyes, mouth, cheeks);
        }
        if (in.blinkEyes() >= 0 && openType) {
            eyes = in.blinkEyes();
        }
        if (in.talking()) {
            mouth = in.talkMouth();
        }
        if (in.authoredEyes() >= 0) {
            eyes = in.authoredEyes();
        }
        if (in.authoredMouth() >= 0) {
            mouth = in.authoredMouth();
        }
        if (in.authoredCheeks() >= 0) {
            cheeks = in.authoredCheeks();
        }
        return new Frames(clamp(eyes, EYE_FRAMES), clamp(mouth, MOUTH_FRAMES), clamp(cheeks, CHEEK_FRAMES));
    }

    private static int clamp(int frame, int frames) {
        return frame < 0 || frame >= frames ? 0 : frame;
    }

    // ------------------------------------------------------------------ texture regions

    /**
     * A strip of frames in the texture (texels): frame 0 at (u, v), frames laid out left to right in rows of
     * {@code columns}. The face planes' UVs map frame 0; the renderer shifts them by {@link #offsetU}/{@link #offsetV}.
     */
    public record Region(int u, int v, int w, int h, int columns, int frames) {
        public static final Region EYES = new Region(64, 0, 20, 10, 4, EYE_FRAMES);
        public static final Region MOUTH = new Region(64, 30, 8, 3, 8, MOUTH_FRAMES);
        public static final Region CHEEKS = new Region(64, 33, 16, 2, 4, CHEEK_FRAMES);

        /** Texels right of frame 0 (a frame out of range shows frame 0). */
        public int offsetU(int frame) {
            int f = frame < 0 || frame >= frames ? 0 : frame;
            return (f % Math.max(1, columns)) * w;
        }

        /** Texels below frame 0. */
        public int offsetV(int frame) {
            int f = frame < 0 || frame >= frames ? 0 : frame;
            return (f / Math.max(1, columns)) * h;
        }

        /** The UV shift as a fraction of the baked texture size (GeckoLib bakes UVs normalised: texel / size). */
        public float shiftU(int frame, double textureWidth) {
            return textureWidth > 0 ? (float) (offsetU(frame) / textureWidth) : 0F;
        }

        public float shiftV(int frame, double textureHeight) {
            return textureHeight > 0 ? (float) (offsetV(frame) / textureHeight) : 0F;
        }
    }

    /**
     * A hero's mood table from its own rows (12, in the mood order of {@link HeroActions#MOODS}), with the per-hero
     * overrides on top; a missing or short table is filled from {@link #DEFAULT_MOODS}.
     */
    public static List<Frames> moodTable(List<Frames> own, java.util.Map<Integer, Frames> overrides) {
        Frames[] out = DEFAULT_MOODS.toArray(new Frames[0]);
        if (own != null) {
            for (int i = 0; i < Math.min(own.size(), out.length); i++) {
                if (own.get(i) != null) {
                    out[i] = own.get(i);
                }
            }
        }
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                if (k != null && k >= 0 && k < out.length && v != null) {
                    out[k] = v;
                }
            });
        }
        return List.copyOf(Arrays.asList(out));
    }
}
