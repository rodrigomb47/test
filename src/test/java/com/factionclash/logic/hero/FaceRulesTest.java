package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.factionclash.logic.hero.FaceRules.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FaceRulesTest {
    private static final Frames ANGRY = DEFAULT_MOODS.get(HeroActions.moodCode("angry"));
    private static final Frames HAPPY = DEFAULT_MOODS.get(HeroActions.moodCode("happy"));

    private static Input in(boolean dead, boolean far, Frames mood, int authoredEyes, int authoredMouth, boolean talking, int blink) {
        return new Input(dead, far, mood, false, false, authoredEyes, authoredMouth, -1, talking, MOUTH_TALK_B, blink, DEFAULT_BLINK_OVER);
    }

    @Test
    void theDefaultTableHasTwelveMoodsInTheSyncedOrder() {
        assertEquals(12, DEFAULT_MOODS.size());
        assertEquals(new Frames(EYES_OPEN, MOUTH_NEUTRAL, CHEEKS_NONE), DEFAULT_MOODS.get(HeroActions.moodCode("calm")));
        assertEquals(new Frames(EYES_SLEEPING, MOUTH_NEUTRAL, CHEEKS_NONE), DEFAULT_MOODS.get(HeroActions.moodCode("asleep")));
        assertEquals(new Frames(EYES_SURPRISED, MOUTH_FROWN, CHEEKS_BLUSH), DEFAULT_MOODS.get(HeroActions.moodCode("flustered")));
        assertEquals(new Frames(EYES_HALF, MOUTH_NEUTRAL, CHEEKS_SWEAT), DEFAULT_MOODS.get(HeroActions.moodCode("tired")));
    }

    @Test
    void deadClosesTheEyesAboveEverything() {
        assertEquals(new Frames(EYES_CLOSED, MOUTH_NEUTRAL, CHEEKS_NONE), choose(in(true, false, HAPPY, EYES_GLOWING, MOUTH_SMILE, true, -1)));
    }

    @Test
    void authoredKeysBeatTalkingAndBlinkingPerPlane() {
        Frames f = choose(in(false, false, ANGRY, EYES_SMUG, -1, true, EYES_CLOSED));
        assertEquals(EYES_SMUG, f.eyes(), "the authored eye frame wins over the blink");
        assertEquals(MOUTH_TALK_B, f.mouth(), "no authored mouth: the talking mouth");
        f = choose(in(false, false, ANGRY, -1, MOUTH_FANG_GRIN, true, EYES_CLOSED));
        assertEquals(EYES_CLOSED, f.eyes(), "no authored eye: the blink over angry eyes");
        assertEquals(MOUTH_FANG_GRIN, f.mouth(), "the authored mouth wins over talking");
    }

    @Test
    void talkingDrivesTheMouthAndTheEyesFollowTheMood() {
        Frames f = choose(in(false, false, ANGRY, -1, -1, true, -1));
        assertEquals(new Frames(EYES_ANGRY, MOUTH_TALK_B, CHEEKS_NONE), f);
    }

    @Test
    void aBlinkClosesOnlyOpenTypeEyes() {
        assertEquals(EYES_HALF, choose(in(false, false, Frames.CALM, -1, -1, false, EYES_HALF)).eyes());
        assertEquals(EYES_CLOSED, choose(in(false, false, ANGRY, -1, -1, false, EYES_CLOSED)).eyes());
        // happy (closed arcs), surprised, pain, sleeping: never blink
        assertEquals(EYES_HAPPY, choose(in(false, false, HAPPY, -1, -1, false, EYES_CLOSED)).eyes());
        Frames asleep = DEFAULT_MOODS.get(HeroActions.moodCode("asleep"));
        assertEquals(EYES_SLEEPING, choose(in(false, false, asleep, -1, -1, false, EYES_CLOSED)).eyes());
        // a hero's own list
        Input own = new Input(false, false, HAPPY, false, false, -1, -1, -1, false, 0, EYES_CLOSED, List.of(EYES_HAPPY));
        assertEquals(EYES_CLOSED, choose(own).eyes());
    }

    @Test
    void farAwayTheFaceStaysOnTheMoodFrame() {
        Frames f = choose(in(false, true, ANGRY, EYES_SMUG, MOUTH_SMILE, true, EYES_CLOSED));
        assertEquals(ANGRY, f, "no authored keys, no talking, no blink beyond 16 blocks");
    }

    @Test
    void glowingEyesAndBlushComeFromTheMoodByte() {
        Input glow = new Input(false, false, ANGRY, true, true, -1, -1, -1, false, 0, -1, DEFAULT_BLINK_OVER);
        assertEquals(new Frames(EYES_GLOWING, MOUTH_GRIT, CHEEKS_BLUSH), choose(glow));
        // a blink still closes glowing eyes (they glow over an open-type eye)
        Input glowBlink = new Input(false, false, ANGRY, true, false, -1, -1, -1, false, 0, EYES_CLOSED, DEFAULT_BLINK_OVER);
        assertEquals(EYES_CLOSED, choose(glowBlink).eyes());
        // happy eyes do not glow; a mood with its own cheek frame keeps it
        Frames tired = DEFAULT_MOODS.get(HeroActions.moodCode("tired"));
        Input both = new Input(false, false, tired, true, true, -1, -1, -1, false, 0, -1, DEFAULT_BLINK_OVER);
        assertEquals(CHEEKS_SWEAT, choose(both).cheeks());
        assertEquals(EYES_HALF, choose(both).eyes());
    }

    @Test
    void faceCtrlCountsOnlyNearAWholeNumber() {
        assertEquals(-1, authored(0, EYE_FRAMES), "0 is no override");
        assertEquals(0, authored(1, EYE_FRAMES), "1 is frame 0");
        assertEquals(4, authored(5.0, EYE_FRAMES));
        assertEquals(4, authored(5.04, EYE_FRAMES));
        assertEquals(4, authored(4.96, EYE_FRAMES));
        assertEquals(-1, authored(4.9, EYE_FRAMES), "a transition passing through 4.9 shows nothing");
        assertEquals(-1, authored(2.5, EYE_FRAMES), "a midpoint never flashes a frame");
        assertEquals(-1, authored(13, EYE_FRAMES), "out of range");
        assertEquals(-1, authored(-1, EYE_FRAMES));
        assertEquals(-1, authored(Double.NaN, EYE_FRAMES));
        assertEquals(7, authored(8, MOUTH_FRAMES));
        assertEquals(-1, authored(9, MOUTH_FRAMES));
    }

    @Test
    void framesSitOnTheFixedRegionsAndShiftNormalised() {
        assertEquals(0, Region.EYES.offsetU(0));
        assertEquals(60, Region.EYES.offsetU(3));
        assertEquals(0, Region.EYES.offsetV(3));
        assertEquals(0, Region.EYES.offsetU(4));
        assertEquals(10, Region.EYES.offsetV(4));
        assertEquals(40, Region.EYES.offsetU(10));
        assertEquals(20, Region.EYES.offsetV(10), "frame 10 (glowing) is row 2, column 2");
        assertEquals(56, Region.MOUTH.offsetU(7));
        assertEquals(0, Region.MOUTH.offsetV(7));
        assertEquals(48, Region.CHEEKS.offsetU(3));
        assertEquals(0, Region.EYES.offsetU(12), "out of range shows frame 0");
        assertEquals(40 / 256F, Region.EYES.shiftU(10, 256), 1e-7);
        assertEquals(20 / 128F, Region.EYES.shiftV(10, 128), 1e-7);
        assertEquals(20 / 256F, Region.EYES.shiftV(10, 256), 1e-7, "read from the baked size, never hard-coded");
    }

    @Test
    void aHeroTableAndItsOverridesReplaceTheDefaults() {
        Frames calmHappy = new Frames(EYES_OPEN, MOUTH_SMILE, CHEEKS_NONE);
        List<Frames> own = new java.util.ArrayList<>(DEFAULT_MOODS);
        own.set(HeroActions.moodCode("happy"), calmHappy);
        List<Frames> t = moodTable(own, Map.of(HeroActions.moodCode("smug"), Frames.CALM));
        assertEquals(calmHappy, t.get(HeroActions.moodCode("happy")), "a stoic hero's happy");
        assertEquals(Frames.CALM, t.get(HeroActions.moodCode("smug")));
        assertEquals(DEFAULT_MOODS.get(2), t.get(2));
        assertEquals(DEFAULT_MOODS, moodTable(null, null));
        assertEquals(DEFAULT_MOODS.get(11), moodTable(List.of(Frames.CALM), null).get(11), "a short table is filled");
    }
}
