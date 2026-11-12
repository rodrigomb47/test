package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.npc.ToolKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

import java.util.List;
import java.util.Locale;

/**
 * A hero's three animation controllers (HEROES_V2 4.4, 4.8): {@code base} (4-tick blend: idle, combat idle, walk, run
 * and the locomotion clips, from how the hero moves, the weapon-drawn flag and {@code DATA_LOCO}), {@code action}
 * (2-tick blend: the clip of the {@code DATA_ACTION} code, restarted by each new sequence number and played at its
 * speed; death; a fresh swing's work clip while no action runs) and {@code secondary} (6-tick blend: {@code sec.*}
 * loops, paused beyond 32 blocks). The blends are the ones the timing files were written for
 * ({@link HeroClips#BASE_TRANSITION}, checked by {@code HeroTimingsTest}).
 *
 * <p>Common code with no client class: GeckoLib runs these handlers only inside the client renderer. What the hero's
 * animation chain holds is asked of the {@link Library} the client model installs; on a server none is ever asked.
 */
public final class HeroAnimator {
    /** What animations a hero's model can play (installed by the client model; nothing on a server). */
    public interface Library {
        boolean has(HeroEntity hero, String animation);
    }

    private static volatile Library library = (h, n) -> false;

    /** {@code -Dfactionclash.heroMarkers=true}: log every animation marker with its tick (the A2 timing check). */
    public static final boolean LOG_MARKERS = Boolean.getBoolean("factionclash.heroMarkers");

    public static void setLibrary(Library l) {
        library = l == null ? (h, n) -> false : l;
    }

    private final HeroEntity hero;

    // base
    private String baseClip;
    private RawAnimation baseRaw;
    private double basePlayback = 1.0;
    private double groundSpeed;
    private double verticalSpeed;
    private int speedTick = Integer.MIN_VALUE;

    // action
    private int lastSequence = -1;
    private RawAnimation actionRaw;
    /** The action was started this frame: the controller still reports the previous clip's state until it runs. */
    private boolean actionFresh;
    private String actionName;
    private int actionStartTick;
    private double actionStartTime;
    /** Game time of the frame being animated (ticks + partial tick), for the marker log. */
    private double frameTime;
    private double actionSpeed = 1.0;
    private boolean dying;
    private boolean wasSwinging;
    private int lastSwingTime;
    private int swingTick = Integer.MIN_VALUE;

    // secondary
    private boolean secondaryPaused;
    private static final RawAnimation SECONDARY = RawAnimation.begin().thenLoop("sec.loop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    public HeroAnimator(HeroEntity hero) {
        this.hero = hero;
    }

    public void register(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<HeroEntity> base = new AnimationController<>(hero, "base", HeroClips.BASE_TRANSITION, this::base);
        base.setSoundKeyframeHandler(this::onSound);
        base.setCustomInstructionKeyframeHandler(this::onMarker);
        base.setParticleKeyframeHandler(e -> {
        });
        AnimationController<HeroEntity> action = new AnimationController<>(hero, "action", HeroClips.ACTION_TRANSITION, this::action);
        action.setSoundKeyframeHandler(this::onSound);
        action.setCustomInstructionKeyframeHandler(this::onMarker);
        action.setParticleKeyframeHandler(e -> {
        });
        AnimationController<HeroEntity> secondary = new AnimationController<>(hero, "secondary", HeroClips.SECONDARY_TRANSITION, this::secondary);
        secondary.setSoundKeyframeHandler(this::onSound);
        secondary.setCustomInstructionKeyframeHandler(e -> {
        });
        secondary.setParticleKeyframeHandler(e -> {
        });
        controllers.add(base, action, secondary);
    }

    /** Pauses the secondary controller (the renderer's level of detail: beyond 32 blocks). */
    public void setSecondaryPaused(boolean paused) {
        this.secondaryPaused = paused;
    }

    /** The base clip playing (for the debug view), or null. */
    public String baseClip() {
        return baseClip;
    }

    /** The action clip playing, or null. */
    public String actionClip() {
        return actionRaw == null ? null : actionName;
    }

    private boolean has(String animation) {
        return library.has(hero, animation);
    }

    // ------------------------------------------------------------------ base

    private PlayState base(AnimationState<HeroEntity> state) {
        frameTime = hero.tickCount + state.getPartialTick();
        if (hero.isDeadOrDying()) {
            baseClip = null;
            return PlayState.STOP;
        }
        updateSpeed();
        int forced = hero.forcedPose();
        List<String> candidates;
        double ground = groundSpeed;
        if (forced > 0) {
            candidates = HeroClips.forced(forced);
            ground = HeroClips.forcedSpeed(forced);
        } else {
            boolean sitting = hero.isPassenger() || hero.getPose() == Pose.SITTING;
            candidates = HeroClips.baseCandidates(new HeroClips.Motion(groundSpeed, verticalSpeed, hero.onGround(), hero.isInWater(),
                    hero.isCrouching(), hero.isSleeping(), sitting, hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN),
                    HeroActions.locoMode(hero.loco())));
        }
        String clip = HeroClips.first(candidates, this::has);
        if (clip == null) {
            baseClip = null;
            return PlayState.STOP;
        }
        AnimationController<HeroEntity> c = state.getController();
        double playback = HeroClips.playback(clip, ground);
        if (!clip.equals(baseClip)) {
            if (LOG_MARKERS) {
                FactionClash.LOGGER.info("[Heroes] {} base {} at {}x (ground {} blocks/tick{})", hero.heroId(), clip,
                        String.format(Locale.ROOT, "%.1f", playback), String.format(Locale.ROOT, "%.3f", ground), forced > 0 ? ", forced" : "");
            }
            baseClip = clip;
            baseRaw = RawAnimation.begin().thenLoop(clip);
            basePlayback = playback;
            c.setAnimationSpeed(playback);
        } else if (HeroClips.speedChanged(basePlayback, playback)) {
            basePlayback = playback;
            c.setAnimationSpeed(playback);
        }
        return state.setAndContinue(baseRaw);
    }

    /** Ground and vertical speed from the client's interpolated positions, smoothed once per tick. */
    private void updateSpeed() {
        if (hero.tickCount == speedTick) {
            return;
        }
        boolean first = speedTick == Integer.MIN_VALUE || hero.tickCount - speedTick > 5;
        speedTick = hero.tickCount;
        double dx = hero.getX() - hero.xo, dz = hero.getZ() - hero.zo;
        double h = Math.sqrt(dx * dx + dz * dz);
        double v = hero.getY() - hero.yo;
        if (HeroClips.isTeleport(h)) {
            return; // a teleport keeps the speed it had (HeroClips.TELEPORT_STEP)
        }
        if (first) {
            groundSpeed = h;
            verticalSpeed = v;
        } else {
            groundSpeed = groundSpeed * 0.6 + h * 0.4;
            verticalSpeed = verticalSpeed * 0.5 + v * 0.5;
        }
    }

    // ------------------------------------------------------------------ action

    private PlayState action(AnimationState<HeroEntity> state) {
        AnimationController<HeroEntity> c = state.getController();
        if (hero.isDeadOrDying()) {
            if (!dying) {
                dying = true;
                actionRaw = null;
                if (has("death")) {
                    c.forceAnimationReset();
                    c.setAnimationSpeed(1.0);
                    actionName = "death";
                    actionRaw = DEATH;
                    actionStartTick = hero.tickCount;
                }
            }
            return actionRaw == null ? PlayState.STOP : state.setAndContinue(actionRaw);
        }
        dying = false;
        int packed = hero.action();
        int seq = HeroActions.sequence(packed);
        if (seq != lastSequence) {
            boolean firstSight = lastSequence < 0;
            lastSequence = seq;
            int code = HeroActions.code(packed);
            actionRaw = null;
            // a client that starts tracking mid-cast plays the action from its start (4.8, accepted as cosmetic)
            List<String> stages = code == HeroActions.CODE_NONE ? List.of()
                    : HeroClips.actionStages(code, hero.entry().visual().look().moves(), hero.entry().visual().look().states(), this::has);
            if (!stages.isEmpty()) {
                start(c, stages, HeroActions.speed(packed));
            } else if (code != HeroActions.CODE_NONE && !firstSight) {
                FactionClash.LOGGER.debug("Hero {}: action code {} ({}) has no clip in its chain", hero.heroId(), code, HeroActions.actionName(code));
            }
        }
        if (actionRaw == null) {
            String work = freshSwing();
            if (work != null) {
                start(c, List.of(work), 1.0);
            }
        }
        if (actionRaw == null) {
            return PlayState.STOP;
        }
        if (actionFresh) {
            actionFresh = false; // hasAnimationFinished still describes the previous clip this frame
        } else if (c.hasAnimationFinished()) {
            actionRaw = null;
            return PlayState.STOP;
        }
        return state.setAndContinue(actionRaw);
    }

    private void start(AnimationController<HeroEntity> c, List<String> stages, double speed) {
        RawAnimation raw = RawAnimation.begin().thenPlay(stages.get(0));
        for (int i = 1; i < stages.size(); i++) {
            raw = raw.thenLoop(stages.get(i));
        }
        actionRaw = raw;
        actionFresh = true;
        actionName = stages.get(0);
        actionStartTick = hero.tickCount;
        actionStartTime = frameTime;
        actionSpeed = speed;
        c.forceAnimationReset();
        c.setAnimationSpeed(speed);
        if (LOG_MARKERS) {
            FactionClash.LOGGER.info("[Heroes] {} starts {} at speed {} (client tick {})", hero.heroId(), actionName,
                    String.format(Locale.ROOT, "%.2f", speed), hero.tickCount);
        }
    }

    /**
     * A swing that started since the last tick (HEROES_V2 4.8 "Work": today's jobs and basic attacks call
     * {@code swing}): the clip of the held tool, the first one the chain has; null otherwise.
     */
    private String freshSwing() {
        if (hero.tickCount == swingTick) {
            return null;
        }
        swingTick = hero.tickCount;
        boolean swinging = hero.swinging;
        boolean fresh = swinging && (!wasSwinging || hero.swingTime < lastSwingTime);
        wasSwinging = swinging;
        lastSwingTime = hero.swingTime;
        if (!fresh) {
            return null;
        }
        boolean fighting = hero.getTarget() != null || hero.stateFlag(HeroActions.STATE_WEAPON_DRAWN);
        return HeroClips.first(HeroClips.swingCandidates(held(hero.getMainHandItem()), fighting), this::has);
    }

    static HeroClips.Held held(ItemStack s) {
        if (s.isEmpty()) {
            return HeroClips.Held.NOTHING;
        }
        if (ToolKind.PICKAXE.matches(s)) {
            return HeroClips.Held.PICKAXE;
        }
        if (ToolKind.SHOVEL.matches(s)) {
            return HeroClips.Held.SHOVEL;
        }
        if (ToolKind.AXE.matches(s)) {
            return HeroClips.Held.AXE;
        }
        if (ToolKind.HOE.matches(s)) {
            return HeroClips.Held.HOE;
        }
        if (ToolKind.SWORD.matches(s) || ToolKind.BOW.matches(s) || ToolKind.CROSSBOW.matches(s) || HeroKit.isKit(s)) {
            return HeroClips.Held.WEAPON;
        }
        return HeroClips.Held.OTHER;
    }

    // ------------------------------------------------------------------ secondary

    private PlayState secondary(AnimationState<HeroEntity> state) {
        if (secondaryPaused || hero.isDeadOrDying() || !has("sec.loop")) {
            return PlayState.STOP;
        }
        return state.setAndContinue(SECONDARY);
    }

    // ------------------------------------------------------------------ events

    /** Sound keyframes play at the hero, on this client only (HEROES_V2 4.8 "Client-only effects"). */
    private void onSound(SoundKeyframeEvent<HeroEntity> e) {
        ResourceLocation id = ResourceLocation.tryParse(e.getKeyframeData().getSound());
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound != null && hero.level().isClientSide) {
            hero.level().playLocalSound(hero.getX(), hero.getY(), hero.getZ(), sound, hero.getSoundSource(), 0.8F,
                    0.95F + hero.getRandom().nextFloat() * 0.1F, false);
        }
    }

    /**
     * Markers ({@code hit}, {@code cast}, {@code vfx:<id>} ...): the server times the gameplay from the timing files, so
     * the client only shows them; with {@link #LOG_MARKERS} each is logged with its tick, so the swing's {@code hit} can
     * be checked against the timing file (tick 10 of {@code attack.1}, divided by the playback speed).
     */
    private void onMarker(CustomInstructionKeyframeEvent<HeroEntity> e) {
        if (LOG_MARKERS) {
            boolean action = e.getController().getName().equals("action");
            FactionClash.LOGGER.info("[Heroes] {} {} marker \"{}\" at animation tick {} (written at {}; {} game ticks after the action "
                            + "started, speed {}; a frame late at most)", hero.heroId(), action ? actionName : baseClip,
                    e.getKeyframeData().getInstructions(), String.format(Locale.ROOT, "%.2f", e.getAnimationTick()),
                    String.format(Locale.ROOT, "%.2f", e.getKeyframeData().getStartTick()),
                    action ? String.format(Locale.ROOT, "%.2f", frameTime - actionStartTime) : "-", String.format(Locale.ROOT, "%.2f", actionSpeed));
        }
    }
}
