package com.factionclash.logic.leader;

import com.factionclash.logic.hero.EscapeRules;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A personality (HEROES_V2 5.4): a temperament and eight traits on 0-100. Every director formula reads a trait as
 * {@code t_eff = clamp(0.5 + k (t - 0.5), 0, 1)} with {@code k = leaders.personalityStrength / 100} (0-2): {@code k = 0}
 * makes every faction neutral (today's numbers), and the clamp keeps products of two traits sane at {@code k = 2}. A
 * hero's own fight AI reads the raw traits (6.3's posture, 6.13's nerve), never {@code t_eff}. Each trait stays
 * within 20 of its temperament's default so the label stays true (the reader applies it as ADJUSTED;
 * {@link #adjust} is the same rule). Also the volatility noise of 5.4 and the compatibility and loyalty of 8.
 * Pure, unit-tested ({@code PersonalityTest}).
 */
public record Personality(Temperament temperament, Map<Trait, Integer> traits) {
    /** {@code leaders.personalityStrength}: 0-200%, 100 by default. */
    public static final int STRENGTH_MIN = 0, STRENGTH_MAX = 200, STRENGTH_DEFAULT = 100;
    /** Volatility's noise: sigma 0.2 x v on every decision. */
    public static final double NOISE_PER_VOLATILITY = 0.2;

    public Personality {
        Map<Trait, Integer> m = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            m.put(t, Math.max(0, Math.min(100, traits.getOrDefault(t, 50))));
        }
        traits = Collections.unmodifiableMap(m);
    }

    /** No temperament, every trait 50: what {@code k = 0} makes of everyone. */
    public static Personality neutral() {
        return new Personality(null, Map.of());
    }

    /** A temperament's defaults (Appendix A). */
    public static Personality defaults(Temperament t) {
        return new Personality(t, HeroCatalogue.defaultTraits(t));
    }

    /** A hero file's personality. */
    public static Personality of(HeroDef.Personality p) {
        return p == null ? neutral() : new Personality(p.temperament(), p.traits());
    }

    public int raw(Trait t) {
        return traits.get(t);
    }

    /** {@code k} from {@code leaders.personalityStrength} in percent, clamped to 0-2. */
    public static double k(int strengthPercent) {
        return Math.max(STRENGTH_MIN, Math.min(STRENGTH_MAX, strengthPercent)) / 100.0;
    }

    /** {@code t_eff = clamp(0.5 + k (t - 0.5), 0, 1)} for a trait share {@code t} (0-1). */
    public static double eff(double t, double k) {
        return Math.max(0, Math.min(1, 0.5 + k * (t - 0.5)));
    }

    /** A trait's {@code t_eff}. */
    public double eff(Trait t, double k) {
        return eff(raw(t) / 100.0, k);
    }

    /** A trait value kept within 20 of its temperament's default and within 0-100 (5.4's authoring rule). */
    public static int adjust(Temperament temperament, Trait t, int value) {
        int v = Math.max(0, Math.min(100, value));
        if (temperament == null) {
            return v;
        }
        int d = HeroCatalogue.defaultTraits(temperament).get(t);
        return Math.max(d - HeroCatalogue.TRAIT_DRIFT, Math.min(d + HeroCatalogue.TRAIT_DRIFT, v));
    }

    /** This personality with every trait kept within 20 of its temperament's default. */
    public Personality adjusted() {
        Map<Trait, Integer> m = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            m.put(t, adjust(temperament, t, raw(t)));
        }
        return new Personality(temperament, m);
    }

    /** The fight AI's aggression word from the raw trait: bold at 65 or more, cautious at 35 or less. */
    public HeroDef.Aggression aggressionWord() {
        return HeroCatalogue.aggressionWord(raw(Trait.AGGRESSION));
    }

    /** {@code retreat_below} from the role's default and the raw traits (5.4; whole percent, a half rounds up). */
    public int retreatBelow(int roleDefault) {
        return EscapeRules.retreatBelow(roleDefault, raw(Trait.AGGRESSION), raw(Trait.CAUTION));
    }

    /** The noise on every decision: sigma 0.2 x v (t_eff), doubled for an airhead (at k above 0). */
    public double noiseSigma(double k) {
        double s = NOISE_PER_VOLATILITY * eff(Trait.VOLATILITY, k);
        return k > 0 && Temperaments.has(temperament, Temperaments.Flag.DOUBLED_NOISE) ? 2 * s : s;
    }

    // ------------------------------------------------------------------ compatibility and loyalty (8, milestone 3)

    /** The relationship term of compatibility: friend, sibling, mentor, student, admirer +0.3; rival -0.2; sworn enemy -1. */
    public static double bondTerm(HeroDef.Bond b) {
        if (b == null) {
            return 0;
        }
        return switch (b) {
            case FRIEND, SIBLING, MENTOR, STUDENT, ADMIRER -> 0.3;
            case RIVAL -> -0.2;
            case SWORN_ENEMY -> -1;
        };
    }

    /**
     * A hero's compatibility with a leader (8): {@code 1 - (0.4|h - h_L| + 0.3|a - a_L| + 0.2|s - s_L| + 0.1|m - m_L|)}
     * + the temperaments' affinity (+-0.2) + the relationship. Traits as 0-1 shares.
     */
    public double compatibility(Personality leader, HeroDef.Bond bond) {
        double d = 0.4 * Math.abs(raw(Trait.HONOUR) - leader.raw(Trait.HONOUR))
                + 0.3 * Math.abs(raw(Trait.AGGRESSION) - leader.raw(Trait.AGGRESSION))
                + 0.2 * Math.abs(raw(Trait.SOCIABILITY) - leader.raw(Trait.SOCIABILITY))
                + 0.1 * Math.abs(raw(Trait.AMBITION) - leader.raw(Trait.AMBITION));
        return 1 - d / 100 + Temperaments.affinity(temperament, leader.temperament) + bondTerm(bond);
    }

    /** A hero joins a leader at compatibility 0.45 or more (0.3 for paid mercenaries). */
    public static final double JOINS_AT = 0.45, JOINS_PAID_AT = 0.3;

    /** Loyalty to its faction (8): {@code 0.6 l + 0.4 comp + bond / 100}. */
    public double loyalty(double compatibility, int bondPoints) {
        return 0.6 * raw(Trait.LOYALTY) / 100.0 + 0.4 * compatibility + bondPoints / 100.0;
    }
}
