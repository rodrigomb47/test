package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Aggression;
import com.factionclash.logic.hero.HeroDef.Bond;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.hero.HeroDef.Trait;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Personality} (HEROES_V2 5.4, 8): {@code t_eff} and its clamp, {@code k} from the strength, the 20-point rule,
 * the fight AI's readings, the noise, compatibility and loyalty.
 */
class PersonalityTest {
    private static final double EPS = 1e-9;

    @Test
    void tEffScalesAroundTheMiddleAndClamps() {
        assertEquals(0.5, Personality.eff(0.9, 0), EPS, "k = 0: everyone neutral");
        assertEquals(0.5, Personality.eff(0.0, 0), EPS);
        assertEquals(0.9, Personality.eff(0.9, 1), EPS, "k = 1: the trait itself");
        assertEquals(1.0, Personality.eff(0.9, 2), EPS, "k = 2: 1.3 clamped to 1");
        assertEquals(0.0, Personality.eff(0.1, 2), EPS, "-0.3 clamped to 0");
        assertEquals(0.7, Personality.eff(0.6, 2), EPS);
        assertEquals(0.55, Personality.eff(0.6, 0.5), EPS);
        assertEquals(1.0, Personality.k(100), EPS);
        assertEquals(0, Personality.k(-50), EPS);
        assertEquals(2, Personality.k(500), EPS);
        Personality p = Personality.defaults(Temperament.BATTLE_MANIAC);
        assertEquals(0.95, p.eff(Trait.AGGRESSION, 1), EPS);
        assertEquals(1.0, p.eff(Trait.AGGRESSION, 2), EPS);
        assertEquals(0.5, p.eff(Trait.AGGRESSION, 0), EPS);
    }

    @Test
    void traitsStayWithinTwentyOfTheTemperament() {
        assertEquals(75, Personality.adjust(Temperament.BATTLE_MANIAC, Trait.AGGRESSION, 10), "95 - 20");
        assertEquals(100, Personality.adjust(Temperament.BATTLE_MANIAC, Trait.AGGRESSION, 150), "and at most 100");
        assertEquals(25, Personality.adjust(Temperament.BATTLE_MANIAC, Trait.CAUTION, 90), "5 + 20");
        assertEquals(0, Personality.adjust(Temperament.BATTLE_MANIAC, Trait.CAUTION, -5));
        assertEquals(60, Personality.adjust(Temperament.STOIC, Trait.AGGRESSION, 60), "within 20: as written");
        assertEquals(0, Personality.adjust(null, Trait.AGGRESSION, -1), "no temperament: 0-100 only");
        Map<Trait, Integer> wild = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            wild.put(t, 100);
        }
        Personality p = new Personality(Temperament.SHY, wild).adjusted();
        for (Trait t : Trait.values()) {
            int d = com.factionclash.logic.hero.HeroCatalogue.defaultTraits(Temperament.SHY).get(t);
            assertTrue(p.raw(t) <= d + 20 && p.raw(t) >= d - 20, t.id());
        }
        assertEquals(50, Personality.neutral().raw(Trait.LOYALTY));
        assertEquals(100, new Personality(null, Map.of(Trait.HONOUR, 300)).raw(Trait.HONOUR), "kept in 0-100");
    }

    @Test
    void theFightAiReadsTheRawTraits() {
        assertEquals(Aggression.BOLD, Personality.defaults(Temperament.HOT_BLOODED).aggressionWord());
        assertEquals(Aggression.CAUTIOUS, Personality.defaults(Temperament.SHY).aggressionWord());
        assertEquals(Aggression.STEADY, Personality.defaults(Temperament.STOIC).aggressionWord());
        assertEquals(23, new Personality(Temperament.TSUNDERE, Map.of(Trait.AGGRESSION, 65, Trait.CAUTION, 45))
                .retreatBelow(25), "Natsuho: 22.5 rounds up");
    }

    @Test
    void noise() {
        assertEquals(0.2 * 0.6, Personality.defaults(Temperament.BATTLE_MANIAC).noiseSigma(1), EPS);
        assertEquals(2 * 0.2 * 0.95, Personality.defaults(Temperament.AIRHEAD).noiseSigma(1), EPS, "doubled");
        assertEquals(0.2 * 0.5, Personality.defaults(Temperament.AIRHEAD).noiseSigma(0), EPS, "k = 0: neutral");
    }

    @Test
    void compatibilityAndLoyalty() {
        Personality leader = Personality.defaults(Temperament.STOIC);
        assertEquals(1, leader.compatibility(leader, null), EPS, "the same traits, no affinity with itself");
        Personality hot = Personality.defaults(Temperament.HOT_BLOODED);
        // |70-80| 0.4 + |80-50| 0.3 + |65-30| 0.2 + |65-45| 0.1 = 4 + 9 + 7 + 2 = 22 -> 0.78, + 0.2 (admires stoic)
        assertEquals(0.98, hot.compatibility(leader, null), EPS);
        assertEquals(1.28, hot.compatibility(leader, Bond.FRIEND), EPS);
        assertEquals(0.78, hot.compatibility(leader, Bond.RIVAL), EPS);
        assertEquals(-0.02, hot.compatibility(leader, Bond.SWORN_ENEMY), EPS);
        assertEquals(0.3, Personality.bondTerm(Bond.MENTOR), EPS);
        assertEquals(0, Personality.bondTerm(null), EPS);
        assertEquals(0.45, Personality.JOINS_AT, EPS);
        assertEquals(0.6 * 0.75 + 0.4 * 0.98 + 0.2, hot.loyalty(0.98, 20), EPS);
    }
}
