package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VoiceLine} (SOUND.md section 6, task S2): one syllable every {@code rate} ticks, a pause between words and a
 * longer one at punctuation, a random step of the melody with the jitter, the last syllable before a {@code ?} 12%
 * higher, nothing past the speech bubble's 3 s.
 */
class VoiceLineTest {
    private static final List<Double> FLAT = List.of(0.0);

    @Test
    void syllablesAreVowelGroups() {
        assertEquals(1, VoiceLine.syllables("cat"));
        assertEquals(2, VoiceLine.syllables("harbour"));
        assertEquals(3, VoiceLine.syllables("Natsuho"));
        assertEquals(4, VoiceLine.syllables("unbelievably"), "at most four");
        assertEquals(1, VoiceLine.syllables("hmph"), "a word with no vowel still says something");
        assertEquals(0, VoiceLine.syllables("..."), "punctuation alone says nothing");
        assertEquals(0, VoiceLine.syllables("—"));
        assertEquals(2, VoiceLine.syllables("42"), "digits are spoken");
    }

    @Test
    void oneSyllableEveryRateTicksWithPauses() {
        // "hello, cat" at rate 2: hel-lo (ticks 0, 2), the word gap (1) and the comma's pause (2 x 2), cat at tick 9
        List<VoiceLine.Syllable> s = VoiceLine.schedule("hello, cat", 2, FLAT, 0, () -> 0.5);
        assertEquals(3, s.size());
        assertEquals(0, s.get(0).tick());
        assertEquals(2, s.get(1).tick());
        assertEquals(2 + 2 + VoiceLine.WORD_GAP + VoiceLine.PAUSE_SLOTS * 2, s.get(2).tick());
        // without the comma the next word follows after the word gap only
        List<VoiceLine.Syllable> t = VoiceLine.schedule("hello cat", 3, FLAT, 0, () -> 0.5);
        assertEquals(3 + 3 + VoiceLine.WORD_GAP, t.get(2).tick());
        for (VoiceLine.Syllable x : s) {
            assertEquals(1.0, x.pitch(), 1e-9, "a flat melody with no jitter");
        }
    }

    @Test
    void aQuestionRisesOnItsLastSyllable() {
        List<VoiceLine.Syllable> s = VoiceLine.schedule("Did you bring fish?", 2, FLAT, 0, () -> 0.1);
        assertEquals(4, s.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, s.get(i).pitch(), 1e-9);
        }
        assertEquals(VoiceLine.QUESTION_RISE, s.get(3).pitch(), 1e-9);
        List<VoiceLine.Syllable> quoted = VoiceLine.schedule("\"Really?\"", 2, FLAT, 0, () -> 0.1);
        assertEquals(VoiceLine.QUESTION_RISE, quoted.get(quoted.size() - 1).pitch(), 1e-9, "a closing quote is skipped");
        assertEquals(1.0, quoted.get(0).pitch(), 1e-9, "only the last syllable");
    }

    @Test
    void theMelodyAndJitterShapeThePitch() {
        List<Double> melody = List.of(0.0, 0.0, 2.0, 3.0, 5.0, -2.0);
        Random r = new Random(7);
        List<VoiceLine.Syllable> s = VoiceLine.schedule("This harbour is mine now, don't get the wrong idea", 2, melody, 0.04,
                r::nextDouble);
        assertTrue(s.size() > 10);
        double lo = Math.pow(2, -2 / 12.0) * 0.96, hi = Math.pow(2, 5 / 12.0) * 1.04;
        boolean varied = false;
        for (VoiceLine.Syllable x : s) {
            assertTrue(x.pitch() >= lo - 1e-9 && x.pitch() <= hi + 1e-9, "pitch " + x.pitch());
            varied |= Math.abs(x.pitch() - s.get(0).pitch()) > 0.02;
        }
        assertTrue(varied, "the melody moves");
        // the top step and the lowest draw
        List<VoiceLine.Syllable> top = VoiceLine.schedule("a", 2, melody, 0.04, () -> 0.999);
        assertEquals(Math.pow(2, -2 / 12.0) * (1 + 0.04 * (2 * 0.999 - 1)), top.get(0).pitch(), 1e-9);
        List<VoiceLine.Syllable> bottom = VoiceLine.schedule("a", 2, melody, 0.04, () -> 0.0);
        assertEquals(0.96, bottom.get(0).pitch(), 1e-9);
    }

    @Test
    void nothingPastTheBubble() {
        String longLine = "na ".repeat(200);
        List<VoiceLine.Syllable> s = VoiceLine.schedule(longLine, 2, FLAT, 0, () -> 0.5);
        assertTrue(s.get(s.size() - 1).tick() < VoiceLine.MAX_TICKS);
        assertTrue(s.size() > 15);
        assertTrue(VoiceLine.schedule("", 2, FLAT, 0, () -> 0.5).isEmpty());
        assertTrue(VoiceLine.schedule("  ", 2, FLAT, 0, () -> 0.5).isEmpty());
        assertTrue(VoiceLine.schedule(null, 2, FLAT, 0, () -> 0.5).isEmpty());
        assertEquals(1, VoiceLine.schedule("a", 0, List.of(), 0, () -> 0.5).size(), "rate and melody fall back");
    }
}
