package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.MoveClass;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.MovePlan.Plan;

/**
 * The move runner's rules (HEROES_V2 4.8 and 6.3-6.4, SLICE_PLAN 2 "Move runner"; task A5): how fast a move plays, how
 * its plan scales with that speed, and what stops it.
 *
 * <ul>
 * <li><b>Speed</b> (4.8): Haste, Mining Fatigue, Slowness and the {@code attack_speed} stat change it, 0.5-2.0. Haste
 * and Mining Fatigue move it by vanilla's attack-speed share per level (10%), Slowness by vanilla's walking share per
 * level (15%), the stat by its percent ({@link #speed}).</li>
 * <li><b>Playback</b>: the speed the client plays the move's clip at, which is the speed the server divides the plan by
 * ({@link #playback}). A wind-up and a recovery never shorten below their class minimum (6.4: "telegraphs never shorten
 * below these minimums, even in phase 3 or enraged"), so the speed is held to the one that keeps both at their minimum;
 * then it is rounded down to a step {@code DATA_ACTION} can carry ({@link HeroActions#speedStep}), so the client's clip
 * and the server's plan use exactly the same number and the clip's {@code hit} marker falls on the server's hit tick.</li>
 * <li><b>The scaled plan</b> ({@link #scale}): every tick of the plan divided by the playback speed and rounded up (the
 * server never lands a hit before the clip shows it), hits kept one tick apart at least, the wind-up and the recovery
 * held to their class minimum.</li>
 * <li><b>Interrupts</b> ({@link Interrupt}): a stun or a stagger stops a move at any stage; a knock-back stronger than
 * 0.5 (after knock-back resistance) stops a wind-up, except a {@code super_armor} move's or an ultimate's; damage of the
 * move's {@code channel} share breaks a channelled move in its wind-up or active part (a 2 s recovery follows); an
 * escape cancels a wind-up and lets an active part finish its tick; the target dying, going or moving out of reach calls a
 * wind-up off with its charge kept. A wind-up stopped by a stun, a stagger, a knock-back or an escape gives half the
 * cooldown back (v1 10.3).</li>
 * </ul>
 * Nothing here knows who hit the hero or whom it fights. Pure, unit-tested ({@code MoveTimingTest}).
 */
public final class MoveTiming {
    private MoveTiming() {
    }

    /** Haste and Mining Fatigue change attack speed by this share per level (vanilla's attack-speed modifiers). */
    public static final double HASTE_PER_LEVEL = 0.10, FATIGUE_PER_LEVEL = 0.10;
    /** Slowness slows a move as it slows walking: this share per level (vanilla's movement modifier). */
    public static final double SLOWNESS_PER_LEVEL = 0.15;
    /** A knock-back stronger than this (after resistance) in a wind-up interrupts it (HEROES_V2 4.8; a plain blow is 0.4). */
    public static final double KNOCKBACK_INTERRUPT = 0.5;
    /** A broken channel leaves the hero in this long a recovery (6.9: 2 s). */
    public static final int CHANNEL_BREAK_RECOVERY = BossRules.CHANNEL_BROKEN_RECOVERY_TICKS;
    /** A grab that misses costs this long a recovery (6.5: 1.2 s). */
    public static final int GRAB_MISS_RECOVERY = 24;

    // ------------------------------------------------------------------ speed

    /**
     * A hero's move speed: x (1 + the {@code attack_speed} stat / 100) x (1 + 10% a Haste level) x (1 - 10% a Mining
     * Fatigue level) x (1 - 15% a Slowness level), held to 0.5-2.0. Levels are 1 for level I (0: none).
     */
    public static double speed(double attackSpeedPercent, int haste, int fatigue, int slowness) {
        double s = (1 + finite(attackSpeedPercent) / 100.0) * (1 + HASTE_PER_LEVEL * Math.max(0, haste))
                * Math.max(0, 1 - FATIGUE_PER_LEVEL * Math.max(0, fatigue))
                * Math.max(0, 1 - SLOWNESS_PER_LEVEL * Math.max(0, slowness));
        return clamp(s);
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }

    static double clamp(double s) {
        if (!Double.isFinite(s)) {
            return 1.0;
        }
        return Math.max(HeroActions.MIN_SPEED, Math.min(HeroActions.MAX_SPEED, s));
    }

    /** The speed of a step of {@code DATA_ACTION} at or below {@code s} (the client plays exactly this number). */
    public static double quantizeDown(double s) {
        double c = clamp(s);
        long step = (long) Math.floor((c - HeroActions.MIN_SPEED) * HeroActions.SPEED_STEPS
                / (HeroActions.MAX_SPEED - HeroActions.MIN_SPEED) + 1e-9);
        step = Math.max(0, Math.min(HeroActions.SPEED_STEPS, step));
        return HeroActions.MIN_SPEED + step * (HeroActions.MAX_SPEED - HeroActions.MIN_SPEED) / HeroActions.SPEED_STEPS;
    }

    /** A class's least wind-up and recovery in ticks (6.4). */
    public static int minWindup(MoveClass c) {
        return c == null ? 0 : (int) HeroRules.ticks(c.minWindup);
    }

    public static int minRecovery(MoveClass c) {
        return c == null ? 0 : (int) HeroRules.ticks(c.minRecovery);
    }

    /**
     * The speed a move plays at: {@code requested} ({@link #speed}), but never so fast that its wind-up of
     * {@code windup} ticks or its recovery of {@code recovery} ticks (at speed 1) would end under its class minimum, then
     * rounded down to a {@code DATA_ACTION} step.
     */
    public static double playback(double requested, int windup, int recovery, MoveClass cls) {
        double s = clamp(requested);
        int mw = minWindup(cls), mr = minRecovery(cls);
        if (windup > 0 && mw > 0) {
            s = Math.min(s, windup / (double) mw);
        }
        if (recovery > 0 && mr > 0) {
            s = Math.min(s, recovery / (double) mr);
        }
        return quantizeDown(s);
    }

    /** {@code ticks} at playback speed {@code s}: divided and rounded up (a clip at s reaches that tick no sooner). */
    public static int at(int ticks, double s) {
        double sp = clamp(s);
        return (int) Math.min(MovePlan.MAX_TICKS, Math.ceil(Math.max(0, ticks) / sp - 1e-9));
    }

    /**
     * {@code base} (a plan at speed 1) played at {@code s}: each tick divided by s and rounded up, the hits kept at least a
     * tick apart and inside the active part, the wind-up and the recovery never under the class minimum (a wind-up held
     * longer moves every hit with it).
     */
    public static Plan scale(Plan base, double s, MoveClass cls) {
        int w = at(base.windup(), s);
        int shift = Math.max(0, minWindup(cls) - w);
        w += shift;
        int[] hits = new int[base.hitCount()];
        for (int i = 0; i < hits.length; i++) {
            int h = at(base.hit(i), s) + shift;
            if (i == 0) {
                h = Math.max(h, w);
            } else {
                h = Math.max(h, hits[i - 1] + 1);
            }
            hits[i] = Math.min(MovePlan.MAX_TICKS - 1, h);
        }
        int last = hits.length == 0 ? w : hits[hits.length - 1];
        int activeEnd = Math.min(MovePlan.MAX_TICKS, Math.max(at(base.activeEnd(), s) + shift, last + 1));
        int recovery = Math.max(minRecovery(cls), at(base.recoveryEnd() - base.activeEnd(), s));
        int recoveryEnd = Math.min(MovePlan.MAX_TICKS, activeEnd + recovery);
        return new Plan(w, hits, activeEnd, Math.max(activeEnd, recoveryEnd));
    }

    // ------------------------------------------------------------------ interrupts

    /** What can stop a running move (HEROES_V2 4.8, 6.9, 6.13; v1 10.3). */
    public enum Interrupt {
        /** stunned by anyone (A4) */
        STUN,
        /** staggered at full poise (A6a calls the hook) */
        STAGGER,
        /** knocked back harder than {@link #KNOCKBACK_INTERRUPT} in its wind-up */
        KNOCKBACK,
        /** a channelled move took its {@code channel} share of max health in damage */
        CHANNEL,
        /** an escape starts (6.13; A6/A12) */
        ESCAPE,
        /** the hero died */
        DEATH,
        /** its target died, went or moved out of reach in the wind-up (the charge is kept) */
        TARGET_LOST
    }

    /**
     * Whether {@code why} stops a move now at {@code stage}. {@code superArmor}: the move is {@code super_armor} or an
     * ultimate (a knock-back does not stop it); {@code channelled}: it has a {@code channel} share.
     */
    public static boolean stops(Interrupt why, MoveStage stage, boolean superArmor, boolean channelled) {
        if (stage == null || stage == MoveStage.DONE) {
            return false;
        }
        return switch (why) {
            case STUN, STAGGER, DEATH -> true;
            case KNOCKBACK -> stage == MoveStage.WINDUP && !superArmor;
            case CHANNEL -> channelled && (stage == MoveStage.WINDUP || stage == MoveStage.ACTIVE);
            case ESCAPE -> stage == MoveStage.WINDUP || stage == MoveStage.ACTIVE;
            case TARGET_LOST -> stage == MoveStage.WINDUP;
        };
    }

    /**
     * Whether a stopped move gives half its cooldown back: only one stopped in its wind-up by a stun, a stagger, a
     * knock-back or an escape (v1 10.3). A move whose target went keeps its whole charge instead; a broken channel is a
     * punish and gives nothing back; a dead hero's cooldowns no longer matter.
     */
    public static boolean refunds(Interrupt why, MoveStage stage) {
        return stage == MoveStage.WINDUP && (why == Interrupt.STUN || why == Interrupt.STAGGER
                || why == Interrupt.KNOCKBACK || why == Interrupt.ESCAPE);
    }

    /** The clip an interrupt plays (appendix D): {@code stun}, {@code stagger}, {@code interrupt}, or none. */
    public static String clip(Interrupt why) {
        return switch (why) {
            case STUN -> "stun";
            case STAGGER -> "stagger";
            case KNOCKBACK, CHANNEL -> "interrupt";
            case ESCAPE, DEATH, TARGET_LOST -> null;
        };
    }

    /** Whether a knock-back of vanilla strength {@code strength} on a hero with {@code resistance} (0-1) interrupts. */
    public static boolean knockbackInterrupts(double strength, double resistance) {
        double r = Math.max(0, Math.min(1, finite(resistance)));
        return finite(strength) * (1 - r) > KNOCKBACK_INTERRUPT + 1e-9;
    }
}
