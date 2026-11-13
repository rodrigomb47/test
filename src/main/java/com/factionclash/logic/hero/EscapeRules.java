package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.EndOfFight;
import com.factionclash.logic.hero.HeroDef.StandsFor;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import com.factionclash.logic.hero.HeroDef.WhenLosing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a losing fight ends for a hero (HEROES_V2 6.13, the owner's decision of 2026-11-13: "a hero shouldnt yield.
 * they either try to escape or fight until die depending of what they want"): the parts a hero file decides, read
 * when it loads. Its kind (escape or last stand) from its aggression, honour and caution, a temperament rule and the
 * authored override; where an escaping hero stands anyway (its home, beside its leader) from its loyalty; and its
 * escape threshold {@code flee_at}. Nothing here takes the enemy's kind as an input: there is no rule for players.
 * The runtime decision ({@code decide}: a lost raid or base, the 10-minute window, a cornered hero) comes with
 * F5/A12 on top of these. Pure, unit-tested ({@code EscapeRulesTest}).
 */
public final class EscapeRules {
    private EscapeRules() {
    }

    /** aggression + honour - caution at or above this: a last stand (raw 0-100 values, never scaled). */
    public static final int NERVE_LAST_STAND = 65;
    /** Loyalty at which an escaping hero stands at home, and beside its fighting leader. */
    public static final int HOME_LOYALTY = 70, LEADER_LOYALTY = 85;
    /** {@code flee_at}: a third of {@code retreat_below} within these, then under the last phase by the margin. */
    public static final int FLEE_MIN = 8, FLEE_MAX = 15, FLEE_FLOOR = 5, LAST_PHASE_MARGIN = 10;
    /** An escaping hero whose last phase starts under this leaves as the phase begins (a NOTE). */
    public static final int LATE_LAST_PHASE = 18;

    public static int nerve(int aggression, int honour, int caution) {
        return aggression + honour - caution;
    }

    /**
     * The kind its traits and temperament give, before any authored override: a last stand when the nerve is 65 or
     * more; {@code sleepy} always stands (its rule "bold under half health": once it is that awake it sees the fight
     * through).
     */
    public static WhenLosing byTraits(Temperament t, int aggression, int honour, int caution) {
        if (t == Temperament.SLEEPY) {
            return WhenLosing.LAST_STAND;
        }
        return nerve(aggression, honour, caution) >= NERVE_LAST_STAND ? WhenLosing.LAST_STAND : WhenLosing.ESCAPE;
    }

    public static WhenLosing byTraits(Temperament t, Map<Trait, Integer> traits) {
        return byTraits(t, traits.getOrDefault(Trait.AGGRESSION, 50), traits.getOrDefault(Trait.HONOUR, 50),
                traits.getOrDefault(Trait.CAUTION, 50));
    }

    /**
     * The hero's kind: the authored {@code personality.when_losing} first, then a v1 {@code retreat_below} the file
     * wrote as 0 ("never retreats"), then {@link #byTraits}.
     */
    public static WhenLosing kind(Temperament t, Map<Trait, Integer> traits, WhenLosing authored, int retreatBelow,
                                  boolean retreatAuthored) {
        if (authored != null) {
            return authored;
        }
        if (retreatAuthored && retreatBelow == 0) {
            return WhenLosing.LAST_STAND;
        }
        return byTraits(t, traits);
    }

    /** Where an escaping hero stands anyway: the authored list ({@code []} nowhere), else from its loyalty. */
    public static List<StandsFor> standsFor(int loyalty, List<StandsFor> authored) {
        if (authored != null) {
            return List.copyOf(authored);
        }
        List<StandsFor> l = new ArrayList<>();
        if (loyalty >= HOME_LOYALTY) {
            l.add(StandsFor.HOME);
        }
        if (loyalty >= LEADER_LOYALTY) {
            l.add(StandsFor.LEADER);
        }
        return List.copyOf(l);
    }

    /** A half rounds up (the escape threshold, and a {@code retreat_below} from the personality: kit-gaps 11). */
    public static int roundHalfUp(double v) {
        return (int) Math.floor(v + 0.5 + 1e-9);
    }

    /**
     * HEROES_V2 5.4: the {@code retreat_below} a hero's personality gives when the file writes none: the role's
     * default + 20 (c - 0.5) - 10 (a - 0.5), with the raw traits as shares, clamped to 0-60 and rounded half up to a
     * whole percent.
     */
    public static int retreatBelow(int roleDefault, int aggression, int caution) {
        double v = roleDefault + 20 * (caution / 100.0 - 0.5) - 10 * (aggression / 100.0 - 0.5);
        return roundHalfUp(Math.max(0, Math.min(60, v)));
    }

    /**
     * {@code flee_at} (percent of max health): a third of {@code retreat_below}, a half rounding up, within 8-15, and
     * never above the last phase's start less 10, with a floor of 5. A phase 3 that starts at 25 is fought down to
     * 15-8%.
     */
    public static int fleeAt(int retreatBelow, int lastPhaseAt) {
        int f = Math.max(FLEE_MIN, Math.min(FLEE_MAX, roundHalfUp(retreatBelow / 3.0)));
        f = Math.min(f, lastPhaseAt - LAST_PHASE_MARGIN);
        return Math.max(FLEE_FLOOR, f);
    }

    /** Everything at once, for the reader. */
    public static EndOfFight end(Temperament t, Map<Trait, Integer> traits, WhenLosing authored,
                                 List<StandsFor> authoredStands, int retreatBelow, boolean retreatAuthored,
                                 int lastPhaseAt) {
        WhenLosing k = kind(t, traits, authored, retreatBelow, retreatAuthored);
        List<StandsFor> s = k == WhenLosing.LAST_STAND ? List.of()
                : standsFor(traits.getOrDefault(Trait.LOYALTY, 50), authoredStands);
        return new EndOfFight(k, s, fleeAt(retreatBelow, lastPhaseAt), retreatBelow);
    }

    /** The sheet's line: "fights to the death", "escapes under 15%" or "escapes under 8%, never from its home". */
    public static String sheetLine(EndOfFight e) {
        if (e.kind() == WhenLosing.LAST_STAND) {
            return "fights to the death";
        }
        String s = "escapes under " + e.fleeAt() + "%";
        boolean home = e.standsFor().contains(StandsFor.HOME), leader = e.standsFor().contains(StandsFor.LEADER);
        if (home && leader) {
            return s + ", never from its home or from beside its leader";
        }
        if (home) {
            return s + ", never from its home";
        }
        return leader ? s + ", never from beside its leader" : s;
    }
}
