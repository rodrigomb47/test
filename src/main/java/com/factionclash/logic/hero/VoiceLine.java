package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * A spoken line as voice blips (SOUND.md section 6, task S2; pure): while a hero's line shows, the client plays one
 * syllable every {@code rate} ticks, a random variant of its voice pitched by a random step of its {@code melody}
 * (semitones) and a {@code jitter}; the last syllable before a {@code ?} is 12% higher; a pause at {@code ,} {@code .}
 * {@code !} {@code ?} (and {@code ;} {@code :}) and between words. Nothing is played past the speech bubble's 3 s.
 *
 * <p>A word has as many syllables as it has groups of vowels ({@code y} counts) and digits, at least one and at most
 * {@link #MAX_SYLLABLES_PER_WORD}; a "word" with no letter or digit says nothing (it can still end a sentence).
 */
public final class VoiceLine {
    /** One syllable: ticks after the line starts, and its pitch factor. */
    public record Syllable(int tick, double pitch) {
    }

    /** The question's last syllable is this much higher. */
    public static final double QUESTION_RISE = 1.12;
    /** Extra ticks between two words. */
    public static final int WORD_GAP = 1;
    /** The pause after punctuation, in syllable slots. */
    public static final int PAUSE_SLOTS = 2;
    public static final int MAX_SYLLABLES_PER_WORD = 4;
    /** Nothing after the bubble's 3 s ({@link HeroSpeech#BUBBLE_TICKS}). */
    public static final int MAX_TICKS = HeroSpeech.BUBBLE_TICKS;

    private VoiceLine() {
    }

    /**
     * The syllables of {@code text} at {@code rate} ticks each; {@code random} gives uniform draws in [0, 1) (the melody
     * step, then the jitter, for each syllable).
     */
    public static List<Syllable> schedule(String text, int rate, List<Double> melody, double jitter, DoubleSupplier random) {
        List<Syllable> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        int r = Math.max(1, rate);
        List<Double> steps = melody == null || melody.isEmpty() ? List.of(0.0) : melody;
        int tick = 0;
        for (String word : text.trim().split("\\s+")) {
            int n = syllables(word);
            char end = lastPunctuation(word);
            for (int i = 0; i < n; i++) {
                if (tick >= MAX_TICKS) {
                    return out;
                }
                double step = steps.get(Math.min(steps.size() - 1, (int) (random.getAsDouble() * steps.size())));
                double p = Math.pow(2, step / 12.0) * (1 + jitter * (2 * random.getAsDouble() - 1));
                if (end == '?' && i == n - 1) {
                    p *= QUESTION_RISE;
                }
                out.add(new Syllable(tick, p));
                tick += r;
            }
            if (n > 0) {
                tick += WORD_GAP;
            }
            if (end != 0) {
                tick += PAUSE_SLOTS * r;
            }
        }
        return out;
    }

    /** How many syllables a word says (vowel groups, 1 to 4; 0 with no letter or digit). */
    public static int syllables(String word) {
        int groups = 0;
        boolean inVowel = false;
        boolean any = false;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                any = true;
            }
            if (Character.isDigit(c)) {
                groups++; // each digit is said on its own
                inVowel = false;
                continue;
            }
            boolean v = "aeiouy".indexOf(c) >= 0;
            if (v && !inVowel) {
                groups++;
            }
            inVowel = v;
        }
        if (!any) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_SYLLABLES_PER_WORD, groups));
    }

    /** The punctuation a word ends with ({@code , . ! ? ; :} or an ellipsis; closing quotes skipped), or 0. */
    static char lastPunctuation(String word) {
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (c == '"' || c == '\'' || c == ')' || c == '”' || c == '’') {
                continue;
            }
            if (c == '…') {
                return '.';
            }
            return ",.!?;:".indexOf(c) >= 0 ? c : 0;
        }
        return 0;
    }
}
