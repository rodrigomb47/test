package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroLook;
import com.factionclash.logic.hero.BlinkSchedule;
import com.factionclash.logic.hero.FaceClock;
import com.factionclash.logic.hero.FaceRules;
import com.factionclash.logic.hero.HeroActions;

import java.util.UUID;

/**
 * One hero's face on this client (HEROES_V2 4.6): which eye, mouth and cheek frame its face planes show this frame.
 * The rules are {@link FaceRules}; the clocks are a {@link BlinkSchedule} seeded from the entity's UUID (heroes never
 * blink in step) and a {@link FaceClock} (the talking mouth, the 10-tick mood hold). Fed after GeckoLib's animation
 * pass with the {@code face_ctrl} bone's position (authored frame keys) and the synced mood and state bytes. No
 * network traffic: blinking and talking are decided here.
 */
public final class HeroFace {
    private final BlinkSchedule blink;
    private final FaceClock clock;
    private final HeroLook.Face face;
    private FaceRules.Frames frames = FaceRules.Frames.CALM;
    private boolean talking;

    public HeroFace(UUID uuid, HeroLook.Face face, long now) {
        long seed = BlinkSchedule.seed(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
        this.face = face;
        this.blink = new BlinkSchedule(seed, face.blink(), now);
        this.clock = new FaceClock(seed);
    }

    /** The look this face was made for (a new one is made when the hero's look changes). */
    public HeroLook.Face look() {
        return face;
    }

    /** The frames shown now. */
    public FaceRules.Frames frames() {
        return frames;
    }

    /**
     * Picks this frame's face. {@code ctrlX/Y/Z}: the {@code face_ctrl} bone's position after the animation pass (eye,
     * mouth, cheek frame + 1; 0 or a fraction: no override); {@code live}: within the face distance (16 blocks);
     * {@code phaseGlow}: the current phase look makes the eyes glow (besides the mood byte's own flag).
     */
    public FaceRules.Frames update(HeroEntity hero, long tick, float ctrlX, float ctrlY, float ctrlZ, boolean live, boolean phaseGlow) {
        byte moodByte = hero.mood();
        int mood = clock.mood(tick, HeroActions.mood(moodByte));
        FaceRules.Frames moodFrames = face.moods().get(mood);
        boolean nowTalking = live && hero.isTalking();
        if (!nowTalking && talking) {
            clock.stopTalking();
        }
        talking = nowTalking;
        boolean suppressed = hero.stateFlag(HeroActions.STATE_WINDUP);
        int blinkEyes = live ? blink.eyeAt(tick, suppressed) : BlinkSchedule.NONE;
        frames = FaceRules.choose(new FaceRules.Input(hero.isDeadOrDying(), !live, moodFrames, phaseGlow || HeroActions.eyesGlow(moodByte),
                HeroActions.blush(moodByte), FaceRules.authored(ctrlX, FaceRules.EYE_FRAMES),
                FaceRules.authored(ctrlY, FaceRules.MOUTH_FRAMES), FaceRules.authored(ctrlZ, FaceRules.CHEEK_FRAMES),
                talking, talking ? clock.talkMouth(tick) : FaceRules.MOUTH_NEUTRAL, blinkEyes, face.blinkOver()));
        return frames;
    }
}
