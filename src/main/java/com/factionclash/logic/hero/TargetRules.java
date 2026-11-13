package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Aggression;
import com.factionclash.logic.hero.HeroDef.Focus;
import com.factionclash.logic.hero.HeroDef.Protects;
import com.factionclash.logic.hero.HeroDef.Role;

import java.util.List;

/**
 * How a hero picks and keeps its target and spaces itself (HERO_FORMAT.md 8 and 5.2; HEROES_V2 3.5 and 6.2; task A4).
 * Every enemy is a candidate, whoever it is: a player, an NPC, a hero or a monster, read the same way, and there are
 * <b>no attack tokens</b>: nothing here counts how many heroes already press a candidate (owner, 2026-11-13), so any
 * number of heroes may take the same target. Pure, unit-tested ({@code TargetRulesTest}).
 */
public final class TargetRules {
    private TargetRules() {
    }

    /** The target is chosen again every 2 s (v1 8). */
    public static final int RECHECK_TICKS = 40;
    /** A hero with honour this high or more lets an escaping hero go (HEROES_V2 5.4, 3.5). */
    public static final int SPARES_THE_FLEEING_HONOUR = 60;
    /** A skirmisher steps this far back after each melee hit until its next swing is ready (v1 5.2). */
    public static final double SKIRMISH_STEP = 3;

    /** v1 8: the search radius by aggression (cautious 12, steady 20, bold 28 blocks). */
    public static double searchRadius(Aggression a) {
        return switch (a == null ? Aggression.STEADY : a) {
            case CAUTIOUS -> 12;
            case STEADY -> 20;
            case BOLD -> 28;
        };
    }

    /** v1 8: how far it chases from its base when defending (cautious 32, steady 56 as soldiers, bold 80). */
    public static double leash(Aggression a) {
        return switch (a == null ? Aggression.STEADY : a) {
            case CAUTIOUS -> 32;
            case STEADY -> 56;
            case BOLD -> 80;
        };
    }

    /**
     * One enemy as the target goal sees it. {@code attackingProtected}: it attacks someone the hero protects (its
     * {@code protects} word decides who that is; the game fills it the same way for a player and an NPC);
     * {@code escapingHero}: a hero escaping a lost fight; {@code current}: the hero's target now.
     */
    public record Candidate(double distance, double health, double maxHealth, boolean ranged, boolean hero,
                            boolean attackingProtected, boolean escapingHero, boolean current) {
    }

    /** Whether a candidate may be taken at all: an escaping hero is let go by an honourable one (5.4). */
    public static boolean allowed(Candidate c, int honour) {
        return !(c.escapingHero() && honour >= SPARES_THE_FLEEING_HONOUR);
    }

    /**
     * The candidate to take (an index into {@code cs}, -1 when none is allowed): first one that attacks whom the hero
     * protects (when it protects anyone), then by {@code focus} (v1 8: nearest; weakest, fewest health points left;
     * strongest, most max health; ranged first; threat to allies; heroes first), ties to the nearer, and a
     * candidate as good as the current target keeps the current one (no flip-flopping between equals).
     */
    public static int pick(Focus focus, Protects protects, int honour, List<Candidate> cs) {
        int best = -1;
        for (int i = 0; i < cs.size(); i++) {
            Candidate c = cs.get(i);
            if (!allowed(c, honour)) {
                continue;
            }
            if (best < 0 || better(focus, protects, c, cs.get(best))) {
                best = i;
            }
        }
        return best;
    }

    /** Whether {@code a} is a better target than {@code b}. */
    static boolean better(Focus focus, Protects protects, Candidate a, Candidate b) {
        boolean guards = protects != null && protects != Protects.NONE || focus == Focus.THREAT_TO_ALLIES;
        if (guards && a.attackingProtected() != b.attackingProtected()) {
            return a.attackingProtected();
        }
        int c = compare(focus == null ? Focus.NEAREST : focus, a, b);
        if (c != 0) {
            return c < 0;
        }
        if (a.current() != b.current()) {
            return a.current();
        }
        return a.distance() < b.distance() - 1e-9;
    }

    /** Negative when {@code a} comes first by the focus alone, 0 when they tie. */
    static int compare(Focus f, Candidate a, Candidate b) {
        return switch (f) {
            case NEAREST, THREAT_TO_ALLIES -> Double.compare(round(a.distance()), round(b.distance()));
            case WEAKEST -> Double.compare(round(a.health()), round(b.health()));
            case STRONGEST -> Double.compare(round(b.maxHealth()), round(a.maxHealth()));
            case RANGED_FIRST -> a.ranged() != b.ranged() ? (a.ranged() ? -1 : 1)
                    : Double.compare(round(a.distance()), round(b.distance()));
            case HEROES_FIRST -> a.hero() != b.hero() ? (a.hero() ? -1 : 1)
                    : Double.compare(round(a.distance()), round(b.distance()));
        };
    }

    /** Half-point steps: two candidates within half a block or half a health point of each other tie. */
    private static double round(double v) {
        return Math.round(v * 2) / 2.0;
    }

    // ------------------------------------------------------------------ spacing (v1 5.2 and 8)

    /**
     * How a melee hero spaces itself (v1 5.2, 8): {@code CLOSE} closes to melee (keep distance 0); {@code SKIRMISH}
     * steps 3 blocks back after each hit until its next swing is ready (the skirmisher); {@code HOLD} keeps its
     * {@code keep_distance} while a move of its can land from there, backing away like an archer when an enemy comes
     * closer than half of it, and closes in when it has nothing ready (a melee support or controller).
     */
    public enum Spacing { CLOSE, SKIRMISH, HOLD }

    public static Spacing spacing(Role role, double keepDistance) {
        if (keepDistance <= 0) {
            return Spacing.CLOSE;
        }
        return role == Role.SKIRMISHER ? Spacing.SKIRMISH : Spacing.HOLD;
    }

    /** Ranged and holding heroes back away when an enemy is closer than half their keep distance (v1 8). */
    public static boolean backAway(double distance, double keepDistance) {
        return keepDistance > 0 && distance < keepDistance / 2;
    }
}
