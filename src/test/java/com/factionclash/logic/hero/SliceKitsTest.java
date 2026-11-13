package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.Move;
import com.factionclash.logic.hero.HeroDef.StandsFor;
import com.factionclash.logic.hero.HeroDef.WhenLosing;
import com.factionclash.logic.hero.HeroReader.Loaded;
import com.factionclash.logic.hero.HeroReader.Source;
import com.factionclash.logic.hero.Problems.Code;
import com.factionclash.logic.hero.Problems.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HEROES_V2 15.2 F2: the eight slice heroes' kits of SLICE_PLAN 3 written as format-2 server definitions
 * ({@code src/test/resources/heroes/slice}, test data: the authored heroes come from {@code herosrc/} in C1-C4) load
 * with nothing to report, keep every rule of format 2 ({@link V2Sanity}) and give the end of a fight, focus, mobility
 * and weights their sheets give. Where SLICE_PLAN's number is above a cap or under a floor, the fixture writes the
 * cap or the floor; {@link #theNumbersTheFixturesLoweredAreReallyOverTheirCaps} shows each one would be adjusted.
 */
class SliceKitsTest {
    /** SLICE_PLAN 3's order. */
    static final List<String> IDS = List.of("natsuho_komezaki", "yugen_kagaribi", "benika_akagane", "hinae_shirasuzu",
            "kagenori_tsurumine", "tobimaru_usugumo", "ilsabet_frostmere", "ambrose_nettlewick");

    private static final Path DIR = Path.of("src/test/resources/heroes/slice");
    private static Loaded loaded;

    static List<Source> sources() throws IOException {
        List<Source> l = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : s.sorted().toList()) {
                l.add(new Source(p.getFileName().toString(), Files.readString(p)));
            }
        }
        return l;
    }

    static synchronized Loaded all() throws IOException {
        if (loaded == null) {
            loaded = HeroReader.readAll(sources(), HeroReader.Options.BUILTIN);
        }
        return loaded;
    }

    static HeroDef load(String id) throws IOException {
        HeroDef d = all().hero(id);
        assertNotNull(d, id + "\n" + all().problems().report());
        return d;
    }

    @Test
    void theEightSliceKitsLoadClean() throws IOException {
        Loaded l = all();
        assertEquals(8, l.heroes().size(), l.problems().report());
        assertTrue(l.problems().all().isEmpty(), l.problems().report());
        for (String id : IDS) {
            HeroDef d = load(id);
            V2Sanity.sane(d);
            assertEquals(2, d.format());
            assertNotNull(d.v2().ultimate(), id);
            assertEquals(3, d.v2().boss().phases().size(), id + ": every slice hero is epic: three phases");
            assertEquals(HeroDef.Rarity.EPIC, d.rarity());
            assertTrue(d.v2().leader().eligible(), id);
            assertNotNull(d.v2().personality().temperament(), id);
        }
    }

    @Test
    void eachFileReadsAloneAsItReadsWithTheOthers() throws IOException {
        // the across-file checks (leader names, relationships, the flyers' share) add nothing to a clean roster
        for (Source s : sources()) {
            Loaded one = HeroReader.readAll(List.of(s), HeroReader.Options.BUILTIN);
            assertEquals(1, one.heroes().size(), s.path());
            HeroDef d = one.heroes().values().iterator().next();
            assertEquals(all().hero(d.id()), d, s.path());
            assertTrue(one.problems().all().stream().allMatch(p -> p.severity() == Severity.NOTE
                    && p.code().equals(Code.RELATION_UNKNOWN.id())), one.problems().report());
        }
    }

    @Test
    void theEndOfAFightIsAsTheSheetsSay() throws IOException {
        // SLICE_PLAN 3, "When a fight is lost": kind, where an escaping hero stands anyway, retreat_below, flee_at
        Object[][] sheet = {
                {"natsuho_komezaki", WhenLosing.ESCAPE, List.of(StandsFor.HOME), 23, 8},
                {"yugen_kagaribi", WhenLosing.ESCAPE, List.of(), 45, 15},
                {"benika_akagane", WhenLosing.LAST_STAND, null, 7, -1},
                {"hinae_shirasuzu", WhenLosing.ESCAPE, List.of(StandsFor.HOME), 44, 15},
                {"kagenori_tsurumine", WhenLosing.LAST_STAND, null, 28, -1},
                {"tobimaru_usugumo", WhenLosing.ESCAPE, List.of(), 32, 11},
                {"ilsabet_frostmere", WhenLosing.LAST_STAND, null, 22, -1},
                {"ambrose_nettlewick", WhenLosing.ESCAPE, List.of(), 38, 13},
        };
        for (Object[] s : sheet) {
            HeroDef d = load((String) s[0]);
            HeroDef.EndOfFight e = d.v2().end();
            assertEquals(s[1], e.kind(), (String) s[0]);
            assertEquals(s[3], d.behaviour().retreatBelow(), (String) s[0]);
            if (e.kind() == WhenLosing.ESCAPE) {
                assertEquals(s[2], e.standsFor(), (String) s[0]);
                assertEquals(s[4], e.fleeAt(), (String) s[0]);
                assertTrue(e.sheetLine().startsWith("escapes under " + s[4] + "%"), e.sheetLine());
            } else {
                assertEquals("fights to the death", e.sheetLine(), (String) s[0]);
            }
        }
        assertEquals("Kagenori Tsurumine: fights to the death", HeroReader2.endLine(load("kagenori_tsurumine")));
    }

    /**
     * Two kit details the review of F1-F5 found the fixtures had lost (SLICE_PLAN 3.2 and 3.8): Yugen's contract marks
     * an attacker that hurt him "by melee" (an attacker within the close band), not an archer; the great brew rains
     * for 2 s after its 3 s channel (its active part is the 3 + 2).
     */
    @Test
    void contractAnswersMeleeAndTheGreatBrewRainsTwoSeconds() throws IOException {
        HeroDef.Passive contract = load("yugen_kagaribi").passives().stream()
                .filter(p -> p.name().equals("Contract")).findFirst().orElseThrow();
        assertEquals(HeroDef.Trigger.ON_HURT, contract.trigger());
        assertEquals(25, contract.chance());
        assertEquals(new HeroDef.Condition(HeroDef.ConditionType.TARGET_CLOSE, (int) HeroCatalogue.CLOSE_MAX, null, null,
                null), contract.cond(), "only an attacker in melee reach, within the close band");
        HeroDef.Move brew = load("ambrose_nettlewick").v2().move("great_brew");
        assertEquals(2, brew.ability().duration(), 1e-9, "ten splashes over 2 s");
        assertEquals(10, brew.ability().count());
        assertEquals(5.0, brew.frames().active(), 1e-9, "the 3 s channel, then the 2 s of rain");
        assertEquals(15, brew.channel(), "breaks at 15% of his health");
    }

    @Test
    void personalityDrivesFocusAndAggression() throws IOException {
        assertEquals(HeroDef.Focus.STRONGEST, load("benika_akagane").behaviour().focus(), "a battle maniac");
        assertEquals(HeroDef.Focus.STRONGEST, load("ilsabet_frostmere").behaviour().focus(), "a righteous knight");
        assertEquals(HeroDef.Aggression.BOLD, load("natsuho_komezaki").behaviour().aggression());
        assertEquals(HeroDef.Aggression.BOLD, load("benika_akagane").behaviour().aggression());
        for (String id : IDS) {
            HeroDef d = load(id);
            Map<HeroDef.Trait, Integer> dflt = HeroCatalogue.defaultTraits(d.v2().personality().temperament());
            for (HeroDef.Trait t : HeroDef.Trait.values()) {
                assertTrue(Math.abs(d.v2().personality().trait(t) - dflt.get(t)) <= HeroCatalogue.TRAIT_DRIFT,
                        id + " " + t.id());
            }
        }
    }

    @Test
    void mobilityIsAsMobilityAndWorkEightFourSays() throws IOException {
        HeroDef.Mobility ambrose = load("ambrose_nettlewick").v2().mobility();
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.FLYER, 2), ambrose.primary());
        assertEquals(HeroDef.Carrier.BROOM, ambrose.carrier().kind());
        assertEquals(List.of(2), ambrose.aerial().phases(), "he takes to the air from phase 2");
        assertEquals(List.of(HeroDef.Avoid.WATER), load("natsuho_komezaki").v2().mobility().avoid(), "her water weak"
                + " point");
        assertEquals(new HeroDef.TraitTier(HeroDef.MobilityTrait.ACROBAT, 3), load("natsuho_komezaki").v2().mobility()
                .primary());
        long flyers = IDS.stream().filter(id -> {
            try {
                return load(id).v2().mobility().primary().trait() == HeroDef.MobilityTrait.FLYER;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).count();
        assertEquals(1, flyers, "one flyer in eight: 12.5%, under the 15% roster share");
    }

    @Test
    void movesKeepTheirSheetClassesBandsAndStrings() throws IOException {
        HeroDef kagenori = load("kagenori_tsurumine");
        Move thunder = V2Files.move(kagenori, "thunderclap_draw");
        assertEquals(18, thunder.ability().numbers().weight(), 1e-6);
        assertEquals(11, thunder.ability().numbers().floor(), 1e-6);
        assertEquals(HeroDef.MoveClass.HEAVY, thunder.cls());
        HeroDef natsuho = load("natsuho_komezaki");
        Move zoomies = V2Files.move(natsuho, "zoomies");
        assertTrue(zoomies.repeatable());
        assertEquals(List.of(new HeroDef.Followup("zoomies", 50, 0)), zoomies.followups());
        assertEquals(List.of(3), zoomies.phases());
        assertEquals(List.of(2, 3), V2Files.move(natsuho, "cat_reflex").phases());
        assertEquals(HeroDef.Decal.RING, V2Files.move(natsuho, "cat_reflex").telegraph().shape(), "a heavy counter's"
                + " stance ring");
        assertEquals("frenzy", ((HeroDef.Empower) natsuho.v2().ultimate().ability().effects().get(0)).state());
        HeroDef ambrose = load("ambrose_nettlewick");
        Move hops = V2Files.move(ambrose, "broom_hops");
        assertEquals(2, hops.followups().get(0).max(), "three hops: the move and two repeats");
        for (String id : IDS) {
            HeroDef d = load(id);
            for (Move m : d.v2().allMoves()) {
                if (HeroBalance.stunsEnemies(m.ability().effects())) {
                    assertTrue(m.cls().atLeast(HeroDef.MoveClass.HEAVY), id + " " + m.id());
                }
                assertFalse(m.bands().isEmpty(), id + " " + m.id());
            }
        }
    }

    /**
     * Each number the fixtures wrote at a cap or a floor instead of SLICE_PLAN 3's (or HEROES_V2 12.18's) value: put
     * back, it is adjusted with the code given. Recorded in kit-gaps.md ("F2: numbers over a cap").
     */
    @Test
    void theNumbersTheFixturesLoweredAreReallyOverTheirCaps() throws IOException {
        Object[][] lowered = {
                // hero, as the fixture writes it, as the sheet gives it, the code
                {"natsuho_komezaki", "{\"type\": \"shield\", \"amount\": 4}", "{\"type\": \"shield\", \"amount\": 8}",
                        Code.SLOT_CAP},
                {"natsuho_komezaki", "\"on_hit\": [{\"type\": \"dot\", \"amount\": 0.5, \"seconds\": 2}]",
                        "\"on_hit\": [{\"type\": \"dot\", \"amount\": 1, \"seconds\": 3}]", Code.SHARED_ON_HIT},
                {"kagenori_tsurumine", "\"phases\": [1, 2, 3], \"cooldown\": 10,", "\"phases\": [1, 2, 3], \"cooldown\":"
                        + " 6,", Code.COOLDOWN_FLOOR},
        };
        for (Object[] c : lowered) {
            String text = Files.readString(DIR.resolve(c[0] + ".json"));
            assertTrue(text.contains((String) c[1]), c[0] + ": " + c[1]);
            Loaded l = HeroReader.readAll(List.of(new Source(c[0] + ".json", text.replace((String) c[1],
                    (String) c[2]))), HeroReader.Options.BUILTIN);
            assertNotNull(l.hero((String) c[0]));
            assertEquals(1, V2Files.of(l, (Code) c[3]).size(), c[0] + " " + c[2] + "\n" + l.problems().report());
            assertEquals(Severity.ADJUSTED, V2Files.of(l, (Code) c[3]).get(0).severity());
        }
    }
}
