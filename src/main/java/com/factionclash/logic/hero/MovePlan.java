package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Ability;
import com.factionclash.logic.hero.HeroDef.Form;
import com.factionclash.logic.hero.HeroDef.Frames;
import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.Strike;

import java.util.Arrays;
import java.util.List;

/**
 * When the parts of one move happen (HEROES_V2 6.3 and 6.4; HERO_FORMAT.md 10.3): a move goes select -> wind-up ->
 * resolve at its hit ticks -> recovery. The plan counts game ticks from the move's start: the wind-up ends on the
 * first hit tick, the active part runs from it to {@link Plan#activeEnd}, and the recovery (the punish window) ends on
 * {@link Plan#recoveryEnd}.
 *
 * <p>The frames come in from outside: the move's authored (or herogen's generated) {@code frames}, or those of the
 * hero's timing file with its {@code hit} markers ({@link #of(Move, Frames, int[])}); the runner then scales the plan by
 * the hero's speed ({@link MoveTiming#scale}). Hit ticks by form:
 * <ul>
 * <li>{@code melee}, {@code bolt}, {@code nova}, {@code cone}, {@code line} with {@code hits} n: the first at the end
 * of the wind-up, then one every {@code hit_interval} (v1 10.3);</li>
 * <li>{@code combo}: one per strike; a strike with its own {@code windup} follows the previous strike's {@code active}
 * plus that wind-up, the others share the move's active part evenly, so the last strike lands at its end;</li>
 * <li>{@code chain}: one jump per target, 0.15 s apart (v1 10.1);</li>
 * <li>{@code barrage}: one shot per {@code count}, {@code interval} apart; {@code wave}: one ring per {@code waves},
 * {@code interval} apart; {@code rain}: one impact called per {@code count}, spread over its {@code duration} (each lands
 * its {@code delay} later); task A5;</li>
 * <li>every other form: one release at the end of the wind-up (a shot or volley is launched, a zone is laid, a leap
 * takes off, a dash or a charge starts, a sweep begins to turn, a grab reaches, a counter stance ends; what they hit
 * is resolved later by the projectile, the zone or the motion). A sweep's active part lasts at least its
 * {@code duration} and a grab's at least its hold.</li>
 * </ul>
 * With a timing file's {@code hit} markers, the markers are the hits when there are as many as the form has.
 * Pure, unit-tested ({@code MovePlanTest}).
 */
public final class MovePlan {
    private MovePlan() {
    }

    /** A chain jumps to its next target this many ticks after the last (0.15 s, v1 10.1). */
    public static final int CHAIN_JUMP_TICKS = 3;
    /** The longest any move may take (a guard against a broken file): 30 s. */
    public static final int MAX_TICKS = 600;

    /**
     * The schedule of one move: the wind-up ticks (the first hit), every hit tick (sorted, from the start), the end of
     * the active part and of the recovery.
     */
    public record Plan(int windup, int[] hits, int activeEnd, int recoveryEnd) {
        public Plan {
            hits = hits.clone();
        }

        public int hitCount() {
            return hits.length;
        }

        public int hit(int i) {
            return hits[i];
        }

        /** The index of the hit that lands on tick {@code t}, or -1. */
        public int hitAt(int t) {
            for (int i = 0; i < hits.length; i++) {
                if (hits[i] == t) {
                    return i;
                }
            }
            return -1;
        }

        /** The stage at tick {@code t} (ticks since the start). */
        public MoveStage stageAt(int t) {
            if (t < windup) {
                return MoveStage.WINDUP;
            }
            if (t < activeEnd) {
                return MoveStage.ACTIVE;
            }
            return t < recoveryEnd ? MoveStage.RECOVERY : MoveStage.DONE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Plan p && p.windup == windup && Arrays.equals(p.hits, hits) && p.activeEnd == activeEnd
                    && p.recoveryEnd == recoveryEnd;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * windup + Arrays.hashCode(hits)) + activeEnd) + recoveryEnd;
        }

        @Override
        public String toString() {
            return "Plan[windup=" + windup + ", hits=" + Arrays.toString(hits) + ", activeEnd=" + activeEnd
                    + ", recoveryEnd=" + recoveryEnd + "]";
        }
    }

    /** Where a running move is. */
    public enum MoveStage { WINDUP, ACTIVE, RECOVERY, DONE }

    /** The plan of {@code m} with its own authored frames (A3). */
    public static Plan of(Move m) {
        return of(m, m.frames());
    }

    /** The plan of {@code m} with {@code f} (its own frames, or a timing file's without markers). */
    public static Plan of(Move m, Frames f) {
        return of(m, f, null);
    }

    /**
     * The plan of {@code m} with {@code f} at speed 1 and, from a timing file, its {@code hit} markers in ticks from the
     * action's start ({@code markers}; null or a different count: the form's own hit ticks). The runner scales it.
     */
    public static Plan of(Move m, Frames f, int[] markers) {
        Ability a = m.ability();
        int windup = clampTicks(HeroRules.ticks(f.windup()));
        int active = clampTicks(HeroRules.ticks(f.active()));
        int recovery = clampTicks(HeroRules.ticks(f.recovery()));
        int[] hits = hits(m, windup, active);
        if (markers != null && markers.length == hits.length && markers.length > 0 && sorted(markers)) {
            hits = markers.clone();
            windup = Math.min(MAX_TICKS - 1, Math.max(0, hits[0]));
        }
        int last = hits.length == 0 ? windup : hits[hits.length - 1];
        // the active part covers every hit: at least one tick past the last
        int activeEnd = Math.max(windup + active, last + 1);
        if (m.form() == Form.SWEEP) {
            activeEnd = Math.max(activeEnd, windup + (int) Math.max(1, HeroRules.ticks(a.duration())));
        } else if (m.form() == Form.GRAB) {
            activeEnd = Math.max(activeEnd, windup + BossRules.GRAB_HOLD_TICKS + 1);
        }
        activeEnd = Math.min(MAX_TICKS, activeEnd);
        int recoveryEnd = Math.min(MAX_TICKS, activeEnd + recovery);
        return new Plan(windup, hits, activeEnd, Math.max(activeEnd, recoveryEnd));
    }

    private static boolean sorted(int[] h) {
        for (int i = 0; i < h.length; i++) {
            if (h[i] < 0 || i > 0 && h[i] <= h[i - 1]) {
                return false;
            }
        }
        return true;
    }

    private static int clampTicks(long t) {
        return (int) Math.max(0, Math.min(MAX_TICKS, t));
    }

    static int[] hits(Move m, int windup, int active) {
        Ability a = m.ability();
        Form form = m.form();
        return switch (form) {
            case COMBO -> comboHits(m.strikes(), windup, active);
            case CHAIN -> spaced(windup, Math.max(1, a.count()), CHAIN_JUMP_TICKS);
            case BARRAGE -> spaced(windup, Math.max(1, a.count()), Math.max(1, (int) HeroRules.ticks(a.interval())));
            case WAVE -> spaced(windup, Math.max(1, m.extra().waves()), Math.max(1, (int) HeroRules.ticks(a.interval())));
            case RAIN -> {
                int n = Math.max(1, a.count());
                yield spaced(windup, n, Math.max(1, (int) Math.round(HeroRules.ticks(a.duration()) / (double) n)));
            }
            default -> HeroCatalogue.FORMS.containsKey(form) && HeroCatalogue.formUsesHits(form)
                    ? spaced(windup, Math.max(1, a.hits()), Math.max(1, (int) HeroRules.ticks(a.hitInterval())))
                    : new int[] {windup};
        };
    }

    private static int[] spaced(int first, int n, int gap) {
        int[] h = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = Math.min(MAX_TICKS - 1, first + i * gap);
        }
        return h;
    }

    /**
     * A combo's strikes: the first at the end of the wind-up; then a strike with its own {@code windup} waits the
     * previous strike's {@code active} and that wind-up, and a strike without one is spread evenly over the move's
     * active part. Each strike lands at least one tick after the last.
     */
    static int[] comboHits(List<Strike> strikes, int windup, int active) {
        int n = Math.max(1, strikes.size());
        int[] h = new int[n];
        h[0] = windup;
        int even = n > 1 ? Math.max(1, Math.round((float) active / (n - 1))) : 0;
        for (int i = 1; i < n; i++) {
            Strike s = strikes.get(i);
            int gap = s.windup() > 0
                    ? (int) (HeroRules.ticks(strikes.get(i - 1).active()) + HeroRules.ticks(s.windup()))
                    : even;
            h[i] = Math.min(MAX_TICKS - 1, h[i - 1] + Math.max(1, gap));
        }
        return h;
    }
}
