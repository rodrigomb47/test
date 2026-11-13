package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Passive;
import com.factionclash.logic.hero.HeroDef.Trigger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hero's passives at run time (HERO_FORMAT.md 17; HEROES_V2 6.6; task A4): which of them an event sets off, with
 * their internal cooldowns (already raised to the passive floors when the hero was read), {@code chance},
 * {@code once_per_fight}, the low-health re-arm, the once-per-ally memory of {@code on_ally_low_health}, the period of
 * {@code every} and <b>the loop guard</b> of v1 17.3:
 * <ul>
 * <li>damage from passives and summons never fires {@code on_hit}, {@code on_crit} or {@code on_ally_attack} passives
 * of any hero ({@link Event#fromPassiveOrSummon});</li>
 * <li>a passive's effects never fire that same passive (it is marked while its effects land: {@link #tryFire} then
 * {@link #done});</li>
 * <li>at most {@link #MAX_FIRINGS_PER_SECOND} firings per hero per second; the excess is dropped with one warning
 * ({@link #takeWarning}).</li>
 * </ul>
 * The {@code if} condition is checked by the caller before {@link #tryFire} (it needs what the hero senses). Nothing
 * here knows who caused an event beyond the event itself: a player and an NPC set a passive off the same way. Pure,
 * unit-tested ({@code PassiveBookTest}).
 */
public final class PassiveBook {
    /** v1 17.3: at most this many passive firings per hero per second. */
    public static final int MAX_FIRINGS_PER_SECOND = 8;
    public static final int SECOND = 20;
    /** {@code on_low_health} re-arms 20 points above its value (v1 17.1). */
    public static final double LOW_HEALTH_REARM = 20;
    /** {@code on_ally_low_health} fires once per ally per 20 s (v1 17.1). */
    public static final int ALLY_LOW_HEALTH_REPEAT = 20 * SECOND;
    /** {@code on_fight_start}: a target after 10 s without one (v1 17.1). */
    public static final int FIGHT_START_QUIET = 10 * SECOND;
    /** {@code on_ally_*} reach: allies within 12 blocks (v1 17.1); {@code on_ally_ultimate} 24. */
    public static final double ALLY_RADIUS = 12, ALLY_ULTIMATE_RADIUS = 24;
    /** {@code bolt} and {@code shot} passive forms reach 16 blocks (v1 17.2). */
    public static final double BOLT_RANGE = 16;

    /**
     * One event: its trigger, the mark that filled ({@code on_mark_full}), a health share (the hero's for
     * {@code on_low_health}, the ally's for {@code on_ally_low_health}), the ally it concerns (a stable key), the
     * phase ({@code on_phase}), and whether passive or summon damage caused it (the loop guard).
     */
    public record Event(Trigger trigger, String mark, double healthShare, String allyKey, int phase,
                        boolean fromPassiveOrSummon) {
        public static Event of(Trigger t) {
            return new Event(t, null, 1, null, 0, false);
        }

        public Event fromPassive(boolean b) {
            return new Event(trigger, mark, healthShare, allyKey, phase, b);
        }
    }

    /** Why a passive did not fire (for tests and the log). */
    public enum Result { FIRED, NOT_THIS_TRIGGER, LOOP_GUARD, OWN_EFFECTS, ONCE_PER_FIGHT, COOLDOWN, NOT_ARMED, ALLY_REPEAT,
        CHANCE, RATE }

    private final List<Passive> passives;
    private final Map<Trigger, int[]> byTrigger = new EnumMap<>(Trigger.class);
    private final long[] readyAt;
    private final boolean[] usedThisFight;
    private final boolean[] armed;
    private final boolean[] firing;
    private final long[] nextEvery;
    private final List<Map<String, Long>> allyFired = new ArrayList<>();
    private long windowStart = Long.MIN_VALUE;
    private int windowCount;
    private boolean warnDue, warned;
    private long dropped;

    public PassiveBook(List<Passive> passives) {
        this.passives = List.copyOf(passives);
        int n = this.passives.size();
        readyAt = new long[n];
        usedThisFight = new boolean[n];
        armed = new boolean[n];
        firing = new boolean[n];
        nextEvery = new long[n];
        Map<Trigger, List<Integer>> m = new EnumMap<>(Trigger.class);
        for (int i = 0; i < n; i++) {
            Passive p = this.passives.get(i);
            m.computeIfAbsent(p.trigger(), k -> new ArrayList<>()).add(i);
            armed[i] = true;
            nextEvery[i] = -1;
            readyAt[i] = Long.MIN_VALUE;
            allyFired.add(new HashMap<>());
        }
        m.forEach((t, l) -> byTrigger.put(t, l.stream().mapToInt(Integer::intValue).toArray()));
    }

    public List<Passive> passives() {
        return passives;
    }

    public Passive get(int i) {
        return passives.get(i);
    }

    private static final int[] NONE = new int[0];

    /** The indices of the passives with this trigger (none: an empty array, nothing allocated). */
    public int[] withTrigger(Trigger t) {
        return byTrigger.getOrDefault(t, NONE);
    }

    public boolean has(Trigger t) {
        return byTrigger.containsKey(t);
    }

    /** Whether the triggers v1 17.3 guards may be set off by this event. */
    public static boolean guarded(Trigger t) {
        return t == Trigger.ON_HIT || t == Trigger.ON_CRIT || t == Trigger.ON_ALLY_ATTACK;
    }

    /**
     * Whether passive {@code i} fires for event {@code e} at {@code now}, {@code roll} a number 0-99 for its chance.
     * When it fires its cooldown starts, it counts toward the per-second limit and it is marked as firing until
     * {@link #done}; the caller then applies its effects.
     */
    public Result tryFire(int i, Event e, long now, int roll) {
        Passive p = passives.get(i);
        if (p.trigger() != e.trigger()) {
            return Result.NOT_THIS_TRIGGER;
        }
        if (e.trigger() == Trigger.ON_MARK_FULL && (p.mark() == null || !p.mark().equals(e.mark()))) {
            return Result.NOT_THIS_TRIGGER;
        }
        if (e.trigger() == Trigger.ON_PHASE && p.triggerValue() > 0 && (int) Math.round(p.triggerValue()) != e.phase()) {
            return Result.NOT_THIS_TRIGGER;
        }
        if (e.fromPassiveOrSummon() && guarded(e.trigger())) {
            return Result.LOOP_GUARD;
        }
        if (firing[i]) {
            return Result.OWN_EFFECTS;
        }
        if (p.oncePerFight() && usedThisFight[i]) {
            return Result.ONCE_PER_FIGHT;
        }
        if (now < readyAt[i]) {
            return Result.COOLDOWN;
        }
        if (e.trigger() == Trigger.ON_LOW_HEALTH) {
            if (!armed[i] || e.healthShare() * 100 >= value(p)) {
                return Result.NOT_ARMED;
            }
        }
        if (e.trigger() == Trigger.ON_ALLY_LOW_HEALTH) {
            if (e.healthShare() * 100 >= value(p)) {
                return Result.NOT_ARMED;
            }
            Long last = e.allyKey() == null ? null : allyFired.get(i).get(e.allyKey());
            if (last != null && now - last < ALLY_LOW_HEALTH_REPEAT) {
                return Result.ALLY_REPEAT;
            }
        }
        if (p.chance() < 100 && roll >= p.chance()) {
            return Result.CHANCE;
        }
        if (windowStart == Long.MIN_VALUE || now - windowStart >= SECOND) {
            windowStart = now;
            windowCount = 0;
        }
        if (windowCount >= MAX_FIRINGS_PER_SECOND) {
            dropped++;
            if (!warned) {
                warnDue = true;
                warned = true;
            }
            return Result.RATE;
        }
        windowCount++;
        readyAt[i] = now + HeroRules.ticks(p.cooldown());
        usedThisFight[i] = true;
        if (e.trigger() == Trigger.ON_LOW_HEALTH) {
            armed[i] = false;
        }
        if (e.trigger() == Trigger.ON_ALLY_LOW_HEALTH && e.allyKey() != null) {
            Map<String, Long> m = allyFired.get(i);
            if (m.size() > 64) {
                m.entrySet().removeIf(x -> now - x.getValue() >= ALLY_LOW_HEALTH_REPEAT);
            }
            m.put(e.allyKey(), now);
        }
        firing[i] = true;
        return Result.FIRED;
    }

    /** Passive {@code i}'s effects have landed: it may fire again (after its cooldown). */
    public void done(int i) {
        firing[i] = false;
    }

    /** Whether passive {@code i} is landing its effects now (its own effects never set it off again). */
    public boolean firing(int i) {
        return firing[i];
    }

    /** The health share (percent) a low-health trigger fires under: its value, 35 by default (v1 17.1). */
    static double value(Passive p) {
        return p.triggerValue() > 0 ? p.triggerValue() : 35;
    }

    /** The hero's health share changed: an {@code on_low_health} passive re-arms 20 points above its value. */
    public void health(double share) {
        for (int i : withTrigger(Trigger.ON_LOW_HEALTH)) {
            if (!armed[i] && share * 100 >= value(passives.get(i)) + LOW_HEALTH_REARM) {
                armed[i] = true;
            }
        }
    }

    /** A new fight: {@code once_per_fight} passives may fire again; {@code every} counts from now. */
    public void fightStarted(long now) {
        java.util.Arrays.fill(usedThisFight, false);
        for (int i : withTrigger(Trigger.EVERY)) {
            nextEvery[i] = now + period(passives.get(i));
        }
    }

    /** The period of an {@code every} passive (its value, already raised to its floor when read; 10 s by default). */
    public static long period(Passive p) {
        return HeroRules.ticks(p.triggerValue() > 0 ? p.triggerValue() : 10);
    }

    /**
     * Whether {@code every} passive {@code i} is due at {@code now} while fighting; its next time is booked whether
     * or not it then fires. Out of a fight it waits for the next {@link #fightStarted}.
     */
    public boolean everyDue(int i, long now, boolean fighting) {
        if (!fighting) {
            nextEvery[i] = -1;
            return false;
        }
        if (nextEvery[i] < 0) {
            nextEvery[i] = now + period(passives.get(i));
            return false;
        }
        if (now < nextEvery[i]) {
            return false;
        }
        nextEvery[i] = now + period(passives.get(i));
        return true;
    }

    /** True once, the first time a firing was dropped by the per-second limit (the caller logs one warning). */
    public boolean takeWarning() {
        boolean w = warnDue;
        warnDue = false;
        return w;
    }

    /** How many firings the per-second limit has dropped. */
    public long dropped() {
        return dropped;
    }

    /** When passive {@code i} may fire again (game time; {@code Long.MIN_VALUE}: now). */
    public long readyAt(int i) {
        return readyAt[i];
    }
}
