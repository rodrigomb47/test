package com.factionclash.logic.leader;

import com.factionclash.logic.hero.HeroDef.Dye;
import com.factionclash.logic.hero.HeroDef.Gender;
import com.factionclash.logic.hero.HeroDef.Rarity;
import com.factionclash.logic.hero.HeroDef.Temperament;
import com.factionclash.logic.leader.LeaderDraft.Candidate;
import com.factionclash.logic.leader.LeaderDraft.Cast;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LeaderDraft} (HEROES_V2 5.2): seeded and reproducible, each rule, the relaxation order, distinct
 * {@code dye_primary} never relaxed, both genders, war balance, fixed leaders and the rarity weights.
 */
class LeaderDraftTest {
    private static final String[] ARCH = {"cat", "fox", "wolf", "oni", "samurai", "knight", "witch", "elf", "ninja",
            "shrine", "idol", "smith", "siren", "phoenix", "angel", "reaper"};

    /** A varied pool: 32 heroes, dyes, archetypes and temperaments spread, both genders, all aggressions. */
    static List<Candidate> pool() {
        List<Candidate> l = new ArrayList<>();
        Dye[] dyes = Dye.values();
        Temperament[] ts = Temperament.values();
        for (int i = 0; i < 32; i++) {
            Dye d = dyes[i % 16];
            l.add(new Candidate("h" + i, Rarity.values()[i % 4], ARCH[(i * 7) % ARCH.length],
                    i % 2 == 0 ? Gender.FEMALE : Gender.MALE, ts[(i * 11) % ts.length], 20 + (i * 37) % 80,
                    Palette.DYE_RGB.get(d), d, true));
        }
        return l;
    }

    static Map<String, Candidate> byId(List<Candidate> pool) {
        return pool.stream().collect(Collectors.toMap(Candidate::id, c -> c));
    }

    @Test
    void theSameSeedDrawsTheSameCastAndEveryRuleHolds() {
        List<Candidate> pool = pool();
        Map<String, Candidate> by = byId(pool);
        Set<List<String>> casts = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) {
            Cast c = LeaderDraft.draw(pool, 4, List.of(), seed);
            assertEquals(c, LeaderDraft.draw(pool, 4, List.of(), seed), "reproducible from its seed");
            assertEquals(0, c.relaxed(), "this pool allows every rule");
            List<Candidate> cs = c.leaders().stream().map(by::get).toList();
            assertEquals(4, cs.size());
            assertEquals(4, cs.stream().map(Candidate::archetype).distinct().count(), "rule 1");
            assertEquals(4, cs.stream().map(Candidate::dyePrimary).distinct().count(), "rule 2: dyes");
            for (Candidate a : cs) {
                for (Candidate b : cs) {
                    if (a != b) {
                        assertTrue(Palette.deltaE(a.primary(), b.primary()) >= 25, "rule 2: delta-E");
                    }
                }
            }
            assertEquals(4, cs.stream().map(Candidate::temperament).distinct().count(), "rule 3");
            assertTrue(cs.stream().anyMatch(x -> x.gender() == Gender.FEMALE)
                    && cs.stream().anyMatch(x -> x.gender() == Gender.MALE), "rule 4");
            assertTrue(cs.stream().anyMatch(x -> x.aggression() < 70), "rule 5: not all at 70+");
            assertTrue(cs.stream().anyMatch(x -> x.aggression() >= 55), "rule 5: one at 55+");
            casts.add(c.leaders());
        }
        assertTrue(casts.size() > 30, "different seeds, different casts: " + casts.size());
        assertEquals(12345, LeaderDraft.seed(12345, 7, 9), "leaders.seed when set");
        assertEquals(7 ^ 9, LeaderDraft.seed(0, 7, 9), "else world seed XOR start time");
    }

    private static Candidate c(String id, String arch, Gender g, Temperament t, int a, Dye d) {
        return new Candidate(id, Rarity.COMMON, arch, g, t, a, Palette.DYE_RGB.get(d), d, true);
    }

    @Test
    void theRulesRelaxFromTheLast() {
        // everyone aggressive: rule 5 is dropped, the others hold
        List<Candidate> hot = List.of(c("a", "cat", Gender.FEMALE, Temperament.STOIC, 90, Dye.RED),
                c("b", "fox", Gender.MALE, Temperament.SHY, 90, Dye.BLUE),
                c("c", "oni", Gender.FEMALE, Temperament.GENKI, 90, Dye.YELLOW));
        assertEquals(1, LeaderDraft.draw(hot, 3, List.of(), 1).relaxed());
        // one gender only: rules 5 and 4 dropped
        List<Candidate> women = List.of(c("a", "cat", Gender.FEMALE, Temperament.STOIC, 50, Dye.RED),
                c("b", "fox", Gender.FEMALE, Temperament.SHY, 60, Dye.BLUE));
        assertEquals(2, LeaderDraft.draw(women, 2, List.of(), 1).relaxed());
        // one temperament only: 5, 4 and 3
        List<Candidate> stoics = List.of(c("a", "cat", Gender.FEMALE, Temperament.STOIC, 50, Dye.RED),
                c("b", "fox", Gender.MALE, Temperament.STOIC, 60, Dye.BLUE));
        assertEquals(3, LeaderDraft.draw(stoics, 2, List.of(), 1).relaxed());
        // primaries close but different dyes: the delta-E part of rule 2 goes, never the dyes
        List<Candidate> close = List.of(new Candidate("a", Rarity.COMMON, "cat", Gender.FEMALE, Temperament.STOIC, 50,
                        0x3AB3DA, Dye.LIGHT_BLUE, true),
                new Candidate("b", Rarity.COMMON, "fox", Gender.MALE, Temperament.SHY, 60, 0x3BB4DB, Dye.CYAN, true));
        assertEquals(4, LeaderDraft.draw(close, 2, List.of(), 1).relaxed());
        // one archetype only: rule 1 goes last
        List<Candidate> cats = List.of(c("a", "cat", Gender.FEMALE, Temperament.STOIC, 50, Dye.RED),
                c("b", "cat", Gender.MALE, Temperament.SHY, 60, Dye.BLUE));
        assertEquals(5, LeaderDraft.draw(cats, 2, List.of(), 1).relaxed());
    }

    @Test
    void twoLeadersNeverShareADye() {
        List<Candidate> reds = List.of(c("a", "cat", Gender.FEMALE, Temperament.STOIC, 50, Dye.RED),
                c("b", "fox", Gender.MALE, Temperament.SHY, 60, Dye.RED),
                c("c", "oni", Gender.FEMALE, Temperament.GENKI, 60, Dye.RED));
        Cast cast = LeaderDraft.draw(reds, 3, List.of(), 5);
        assertEquals(1, cast.leaders().stream().filter(Objects::nonNull).count(), "one red faction, the rest legacy");
        assertNull(cast.leaders().get(2));
        assertEquals(6, cast.relaxed());
        assertEquals(3, cast.leaders().size());
    }

    @Test
    void fixedLeadersComeFirstAndIneligibleHeroesNever() {
        List<Candidate> pool = new ArrayList<>(pool());
        pool.add(new Candidate("nope", Rarity.LEGENDARY, "tengu", Gender.MALE, Temperament.TYRANT, 50, 0x000001,
                Dye.BLACK, false));
        for (long seed = 0; seed < 20; seed++) {
            Cast c = LeaderDraft.draw(pool, 4, List.of("h5", "h9"), seed);
            assertEquals("h5", c.leaders().get(0));
            assertEquals("h9", c.leaders().get(1));
            assertTrue(!c.leaders().contains("nope"), "leader.eligible false");
        }
        Cast unknown = LeaderDraft.draw(pool(), 2, List.of("nobody"), 3);
        assertEquals(2, unknown.leaders().size(), "an unknown fixed id is ignored");
    }

    @Test
    void rarityWeightsFourFourTwoOne() {
        List<Candidate> pool = List.of(
                new Candidate("common", Rarity.COMMON, "cat", Gender.FEMALE, Temperament.STOIC, 50, 0xB02E26, Dye.RED,
                        true),
                new Candidate("legend", Rarity.LEGENDARY, "fox", Gender.FEMALE, Temperament.SHY, 50, 0x3C44AA,
                        Dye.BLUE, true));
        int legend = 0;
        for (long seed = 0; seed < 2000; seed++) {
            legend += LeaderDraft.draw(pool, 1, List.of(), seed).leaders().get(0).equals("legend") ? 1 : 0;
        }
        assertEquals(2000 / 5.0, legend, 60, "1 in 5 against a common");
        assertNotEquals(0, legend);
    }
}
