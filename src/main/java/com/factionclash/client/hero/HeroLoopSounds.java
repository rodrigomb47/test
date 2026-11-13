package com.factionclash.client.hero;

import com.factionclash.config.FCConfig;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroLocalSounds;
import com.factionclash.hero.HeroSounds;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.SoundIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * A hero's looping sounds on the client (SOUND.md sections 7 and 15.5, task S2): a move's {@code loop} while its action
 * code says the channel lasts (the code of that move, the same sequence), and {@code cues.glide} while {@code DATA_LOCO}
 * says glide. Each is a tickable sound instance that follows the hero and stops by itself when its moment ends, the
 * hero goes or its volume setting is 0. Installed through {@link HeroLocalSounds#setLoops} by the client setup.
 */
public final class HeroLoopSounds {
    private HeroLoopSounds() {
    }

    /** What loops on one hero now. */
    private static final class State {
        Loop channel;
        int channelAction = Integer.MIN_VALUE;
        Loop glide;
    }

    /** Every client tick of a hero (from {@link HeroLocalSounds}). */
    public static void tick(HeroEntity hero) {
        Object o = HeroLocalSounds.loopState(hero);
        State st = o instanceof State s ? s : null;
        int action = hero.action();
        int code = HeroActions.code(action);
        // a move's loop: started on a new action whose move has one
        if (st == null || st.channelAction != action) {
            String loop = null;
            SoundIndex.HeroMap map = HeroSounds.map(hero.heroId());
            if (map != null && hero.isAlive()) {
                List<String> moves = hero.entry().visual().look().moves();
                SoundIndex.MoveSounds m = SoundIndex.moveOfCode(map, moves, code);
                loop = m == null ? null : m.loop();
            }
            if (loop != null || st != null) {
                if (st == null) {
                    st = new State();
                    HeroLocalSounds.setLoopState(hero, st);
                }
                st.channelAction = action;
                if (loop != null && (st.channel == null || st.channel.isStopped())) {
                    final int started = action;
                    st.channel = start(hero, loop, () -> hero.action() == started);
                }
            }
        }
        // the glide's wind
        boolean gliding = HeroActions.LOCO_MODES.get(HeroActions.locoMode(hero.loco())).equals("glide") && hero.isAlive();
        if (gliding && (st == null || st.glide == null || st.glide.isStopped())) {
            if (st == null) {
                st = new State();
                HeroLocalSounds.setLoopState(hero, st);
            }
            String id = HeroSounds.index().cue(SoundIndex.GLIDE);
            st.glide = id == null ? null : start(hero, id,
                    () -> HeroActions.LOCO_MODES.get(HeroActions.locoMode(hero.loco())).equals("glide"));
        }
    }

    private static Loop start(HeroEntity hero, String id, java.util.function.BooleanSupplier lasts) {
        Holder<SoundEvent> h = HeroSounds.holder(id);
        if (h == null) {
            return null;
        }
        SoundIndex.Event e = HeroSounds.index().event(id);
        float vol = (float) ((e == null ? 1.0 : e.volume(hero.getRandom().nextDouble())) * FCConfig.HERO_SOUND_VOLUME.get());
        if (!(vol > 0)) {
            return null;
        }
        float pitch = (float) (e == null ? 1.0 : e.pitch(hero.getRandom().nextDouble()));
        Loop l = new Loop(hero, h.value(), HeroSounds.source(e), vol, pitch, lasts);
        Minecraft.getInstance().getSoundManager().play(l);
        return l;
    }

    /** A loop that follows its hero and stops when its moment ends. */
    static final class Loop extends AbstractTickableSoundInstance {
        private final HeroEntity hero;
        private final java.util.function.BooleanSupplier lasts;

        Loop(HeroEntity hero, SoundEvent event, SoundSource source, float volume, float pitch,
             java.util.function.BooleanSupplier lasts) {
            super(event, source, SoundInstance.createUnseededRandom());
            this.hero = hero;
            this.lasts = lasts;
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.pitch = pitch;
            this.x = hero.getX();
            this.y = hero.getY();
            this.z = hero.getZ();
        }

        @Override
        public void tick() {
            if (hero.isRemoved() || !hero.isAlive() || !lasts.getAsBoolean()) {
                stop();
                return;
            }
            x = hero.getX();
            y = hero.getY();
            z = hero.getZ();
        }
    }
}
