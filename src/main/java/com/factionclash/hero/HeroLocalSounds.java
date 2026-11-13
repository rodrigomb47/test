package com.factionclash.hero;

import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.SoundIndex;
import com.factionclash.logic.hero.VoiceLine;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Hero sounds the client plays itself (SOUND.md sections 5-7, task S2): the {@code sfx:} sound keyframes of its
 * animations (the moves' {@code release} and {@code end}, phases, the intro, idles), their companions, and its voice
 * blips while a line shows ({@code HeroVoice} of section 15.5). Common code with no client class: everything goes through
 * {@link Level#playLocalSound}, which plays only on a client, and is called only on the client side (GeckoLib's handlers
 * and the hero's client tick). The looping sounds need the client's sound manager: the client installs them through
 * {@link #setLoops}.
 *
 * <p>Pitch and volume come from the index (1.0 for a vanilla id it does not list), the volume times
 * {@code heroes.soundVolume}. Voices: one syllable of {@code hero.voice.<id>} every {@code rate} ticks from the line the
 * hero says ({@link VoiceLine}), at volume 1 (the files carry the line's level); a hero with no voice of its own speaks
 * in note-block blips from its definition's v1 {@code voice} (instrument, pitch, rate); off with {@code heroes.voiceBlips}.
 */
public final class HeroLocalSounds {
    /** The client's looping sounds (a move's {@code loop} while its channel lasts, the glide's wind). */
    public interface Loops {
        void tick(HeroEntity hero);
    }

    private static volatile Loops loops = h -> {
    };

    /** Installed by the client; nothing runs on a server. */
    public static void setLoops(Loops l) {
        loops = l == null ? h -> {
        } : l;
    }

    /** The melody, jitter and volume of the note-block fallback voice (a hero with no generated voice). */
    static final List<Double> FALLBACK_MELODY = List.of(0.0, 0.0, 2.0, 4.0, -3.0);
    static final double FALLBACK_JITTER = 0.04;
    static final float FALLBACK_VOLUME = 0.5F;
    static final int FALLBACK_RATE = 3;

    /** One hero's client-side sound state (made on its first client tick). */
    static final class State {
        private final List<long[]> due = new ArrayList<>(2);
        private final List<String> dueIds = new ArrayList<>(2);
        private String lineSeen = "";
        private List<VoiceLine.Syllable> syllables = List.of();
        private Voice voice;
        private int lineStart;
        private int next;
        /** The client's loop state for this hero (owned by {@link Loops}). */
        Object loopState;
    }

    private HeroLocalSounds() {
    }

    /** An animation's sound keyframe on {@code hero} (a full id, or a bare vanilla one as Minecraft parses it). */
    public static void keyframe(HeroEntity hero, String sound) {
        if (hero.level().isClientSide && sound != null && !sound.isBlank()) {
            play(hero, sound.trim(), 0);
        }
    }

    /** Plays {@code id} at the hero on this client, with its companions after their delays. */
    static void play(HeroEntity hero, String id, int depth) {
        Holder<SoundEvent> h = HeroSounds.holder(id);
        if (h == null) {
            return;
        }
        SoundIndex.Event e = HeroSounds.index().event(id);
        Level level = hero.level();
        float vol = (float) ((e == null ? 1.0 : e.volume(hero.getRandom().nextDouble())) * FCConfig.HERO_SOUND_VOLUME.get());
        float pitch = (float) (e == null ? 1.0 : e.pitch(hero.getRandom().nextDouble()));
        if (vol > 0) {
            level.playLocalSound(hero.getX(), hero.getY(), hero.getZ(), h.value(), e == null ? hero.getSoundSource() : HeroSounds.source(e),
                    vol, pitch, false);
        }
        if (e != null && depth < SoundIndex.MAX_COMPANION_DEPTH) {
            for (SoundIndex.Companion c : e.with()) {
                if (c.delay() <= 0) {
                    play(hero, c.sound(), depth + 1);
                } else {
                    State st = state(hero);
                    st.due.add(new long[] {hero.tickCount + c.delay(), depth + 1});
                    st.dueIds.add(c.sound());
                }
            }
        }
    }

    private static State state(HeroEntity hero) {
        State s = hero.localSounds;
        if (s == null) {
            s = new State();
            hero.localSounds = s;
        }
        return s;
    }

    /** The loop state the client keeps for a hero. */
    public static Object loopState(HeroEntity hero) {
        return state(hero).loopState;
    }

    public static void setLoopState(HeroEntity hero, Object o) {
        state(hero).loopState = o;
    }

    /** Every client tick of a hero: companions due, its voice, its loops. */
    static void clientTick(HeroEntity hero) {
        State st = state(hero);
        if (!st.due.isEmpty()) {
            for (int i = st.due.size() - 1; i >= 0; i--) {
                long[] d = st.due.get(i);
                if (d[0] <= hero.tickCount) {
                    String id = st.dueIds.get(i);
                    st.due.remove(i);
                    st.dueIds.remove(i);
                    play(hero, id, (int) d[1]);
                }
            }
        }
        voice(hero, st);
        loops.tick(hero);
    }

    // ------------------------------------------------------------------ the voice (SOUND.md 6)

    private static void voice(HeroEntity hero, State st) {
        if (!hero.isTalking() || !FCConfig.HERO_VOICE_BLIPS.get() || !hero.isAlive()) {
            st.next = st.syllables.size();
            if (!hero.isTalking()) {
                st.lineSeen = hero.spokenLine(); // a line that ended is never spoken late
            }
            return;
        }
        String line = hero.spokenLine();
        if (!line.equals(st.lineSeen)) {
            st.lineSeen = line;
            int nl = line.indexOf('\n');
            String text = nl >= 0 ? line.substring(nl + 1) : line;
            Voice v = voiceOf(hero);
            st.voice = v;
            st.syllables = VoiceLine.schedule(text, v.rate, v.melody, v.jitter, hero.getRandom()::nextDouble);
            st.lineStart = hero.tickCount;
            st.next = 0;
        }
        int at = hero.tickCount - st.lineStart;
        while (st.next < st.syllables.size() && st.syllables.get(st.next).tick() <= at) {
            syllable(hero, st.voice, st.syllables.get(st.next).pitch());
            st.next++;
        }
    }

    /** A hero's voice as the client plays it: its generated blips, or the note-block fallback. */
    record Voice(String event, int rate, List<Double> melody, double jitter, double pitch, boolean generated) {
    }

    static Voice voiceOf(HeroEntity hero) {
        SoundIndex.HeroMap map = HeroSounds.map(hero.heroId());
        if (map != null && map.voice() != null && map.voice().talk() != null && HeroSounds.holder(map.voice().talk()) != null) {
            SoundIndex.Voice v = map.voice();
            return new Voice(v.talk(), v.rate(), v.melody(), v.jitter(), 1.0, true);
        }
        HeroDef d = HeroRegistry.definition(hero.heroId());
        HeroDef.Voice v = d == null || d.v2() == null ? null : d.v2().voice();
        String blip = v == null || v.blip() == null ? (map != null && map.voice() != null ? map.voice().fallbackBlip() : null)
                : v.blip().id();
        return new Voice(SoundIndex.noteBlock(blip), v != null && v.rate() > 0 ? v.rate() : FALLBACK_RATE, FALLBACK_MELODY,
                FALLBACK_JITTER, v != null && v.pitch() > 0 ? v.pitch() : 1.0, false);
    }

    private static void syllable(HeroEntity hero, Voice v, double pitch) {
        Holder<SoundEvent> h = HeroSounds.holder(v.event());
        if (h == null) {
            return;
        }
        SoundIndex.Event e = HeroSounds.index().event(v.event());
        double p = pitch * v.pitch() * (e == null ? 1.0 : e.pitch(hero.getRandom().nextDouble()));
        float vol = (float) ((v.generated() ? 1.0 : FALLBACK_VOLUME) * FCConfig.HERO_SOUND_VOLUME.get());
        if (vol > 0) {
            hero.level().playLocalSound(hero.getX(), hero.getEyeY(), hero.getZ(), h.value(), SoundSource.VOICE, vol,
                    (float) Math.max(0.5, Math.min(2.0, p)), false);
        }
    }
}
