package com.factionclash.logic.hero;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SoundIndex} (SOUND.md sections 4.7, 7 and 15; task S2): the committed index reads, every string of every hero
 * map and of the cue map resolves to an event of the index or a vanilla one, the slice fixtures' moves, states,
 * passives and weak points line up with their maps, every subtitle key is in the language file, and the timing rules
 * of section 7 (the class cue, its delay so {@code hit_at} lands on the release, impacts cycling, the poise crack).
 */
class SoundIndexTest {
    private static final Path INDEX = Path.of("src/main/resources/assets/factionclash/sounds/hero_sfx.json");
    private static final Path LANG = Path.of("src/main/resources/assets/factionclash/lang/en_us.json");
    private static final Path VANILLA = Path.of("tools/sfxgen/vanilla_sound_ids.txt");
    private static SoundIndex index;
    private static Set<String> vanilla;

    @BeforeAll
    static void read() throws IOException {
        index = SoundIndex.parse(Files.readString(INDEX, StandardCharsets.UTF_8));
        vanilla = new HashSet<>();
        for (String l : Files.readAllLines(VANILLA, StandardCharsets.UTF_8)) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                vanilla.add(t.contains(":") ? t : "minecraft:" + t);
            }
        }
    }

    private static boolean resolves(String id) {
        return index.event(id) != null || vanilla.contains(id);
    }

    @Test
    void theCommittedIndexReads() {
        assertTrue(index.events().size() >= 240, "events: " + index.events().size());
        assertEquals(8, index.heroes().size());
        for (SoundIndex.Event e : index.events().values()) {
            assertTrue(e.id().startsWith("factionclash:hero."), e.id());
            assertTrue(e.range() >= 12 && e.range() <= 48, e.id() + " range " + e.range());
            assertTrue(e.pitchLo() > 0 && e.pitchLo() <= e.pitchHi(), e.id());
            assertTrue(e.volumeLo() > 0 && e.volumeHi() <= 2, e.id() + " volume " + e.volumeHi());
            assertFalse(e.subtitle().isEmpty(), e.id());
            for (SoundIndex.Companion c : e.with()) {
                assertNotNull(index.event(c.sound()), e.id() + " companion " + c.sound());
                assertTrue(c.delay() >= 0 && c.delay() <= 10, e.id());
            }
        }
        SoundIndex.Event heavy = index.event(index.cue("telegraph.heavy"));
        assertEquals(0.78, heavy.hitAt(), 1e-9);
        assertEquals(SoundIndex.Plays.SERVER, heavy.plays());
        assertTrue(Double.isNaN(index.event(index.cue("telegraph.light")).hitAt()), "a light cue plays at the start");
        // the gourd swig and its burp after 10 ticks (section 4.6)
        SoundIndex.Event swig = index.event("factionclash:hero.benika_akagane.gourd_swig");
        assertEquals(1, swig.with().size());
        assertEquals(10, swig.with().get(0).delay());
    }

    /** SOUND.md 15.4: "every string of every hero map and of cues resolves to a registered event or a vanilla one". */
    @Test
    void everyHeroMapSlotAndCueResolves() {
        Set<String> bad = new TreeSet<>();
        for (Map.Entry<String, String> c : index.cues().entrySet()) {
            if (!resolves(c.getValue())) {
                bad.add("cue " + c.getKey() + " = " + c.getValue());
            }
        }
        for (SoundIndex.HeroMap m : index.heroes().values()) {
            for (String id : m.ids()) {
                if (!resolves(id)) {
                    bad.add(m.id() + ": " + id);
                }
            }
            assertNotNull(m.voice(), m.id());
            assertNotNull(m.voice().talk(), m.id());
            assertTrue(m.voice().rate() >= 2 && m.voice().rate() <= 4, m.id());
            assertNotNull(m.voice().fallbackBlip(), m.id());
            assertTrue(vanilla.contains(SoundIndex.noteBlock(m.voice().fallbackBlip())), m.id() + " fallback blip");
            assertTrue(m.ids().size() > 10, m.id());
        }
        assertTrue(bad.isEmpty(), "unresolved: " + bad);
        for (String key : SoundIndex.CUES) {
            assertNotNull(index.cue(key), "the cue map has no " + key);
        }
    }

    /** No hero yields or is spared (HEROES_V2 6.13): the bar's fled and fallen, the last stand, the revival. */
    @Test
    void theCueMapEndsFightsWithoutAYield() {
        assertNull(index.cue("yield"));
        assertNull(index.cue("spared"));
        for (String id : index.events().keySet()) {
            assertFalse(id.endsWith(".yield") || id.endsWith(".spared"), id);
        }
        assertEquals("factionclash:hero.cue.fled", index.cue(SoundIndex.FLED));
        assertEquals("factionclash:hero.cue.fallen", index.cue(SoundIndex.FALLEN));
        assertEquals("sting", index.event(index.cue(SoundIndex.FALLEN)).kind());
        assertEquals(32, index.event(index.cue(SoundIndex.FALLEN)).range(), 1e-9);
        assertEquals(SoundIndex.Plays.SERVER, index.event(index.cue(SoundIndex.LAST_STAND)).plays());
        assertNotNull(index.event(index.cue(SoundIndex.REVIVED)));
    }

    /** Every subtitle key of the index is in the language file, and no hero subtitle there is stale. */
    @Test
    void everySubtitleIsInTheLanguageFile() throws IOException {
        Map<String, Object> lang = JsonTree.object(JsonTree.parse(Files.readString(LANG, StandardCharsets.UTF_8)));
        Set<String> keys = new TreeSet<>();
        for (SoundIndex.Event e : index.events().values()) {
            keys.add(e.subtitle());
            assertTrue(lang.get(e.subtitle()) instanceof String s && !s.isBlank(), "no subtitle line for " + e.subtitle());
        }
        for (String k : lang.keySet()) {
            if (k.startsWith("subtitles.factionclash.hero.")) {
                assertTrue(keys.contains(k), "stale subtitle " + k);
            }
        }
        assertEquals("Hero flees", lang.get("subtitles.factionclash.hero.cue.fled"));
    }

    /** The slice fixtures and their maps agree: moves (with their class), states, passives and weak points. */
    @Test
    void theSliceFixturesLineUpWithTheirMaps() throws IOException {
        for (SoundIndex.HeroMap m : index.heroes().values()) {
            HeroDef d = SliceKitsTest.load(m.id());
            HeroDef.V2 v = d.v2();
            Set<String> moves = new HashSet<>();
            for (HeroDef.Move mv : v.moves()) {
                moves.add(mv.id());
                SoundIndex.MoveSounds s = m.moves().get(mv.id());
                assertNotNull(s, m.id() + ": move " + mv.id() + " has no sounds");
                assertEquals(mv.cls().id(), s.cls(), m.id() + "." + mv.id() + " class");
            }
            if (v.ultimate() != null) {
                moves.add(v.ultimate().id());
                assertEquals("ultimate", m.moves().get(v.ultimate().id()).cls(), m.id());
            }
            for (String k : m.moves().keySet()) {
                assertTrue(k.equals("basic") || moves.contains(k), m.id() + ": the map's move " + k + " is not the hero's");
            }
            Set<String> states = new HashSet<>();
            v.states().forEach(st -> states.add(st.name()));
            for (String k : m.states().keySet()) {
                assertTrue(states.contains(k), m.id() + ": state " + k);
            }
            for (String k : m.passives().keySet()) {
                boolean found = false;
                for (HeroDef.Passive p : d.passives()) {
                    found |= k.equals(SoundIndex.passiveKey(m.passives().keySet(), p.name()));
                }
                assertTrue(found, m.id() + ": passive " + k + " names none of the hero's passives");
            }
            Set<String> weak = new HashSet<>();
            v.boss().weakPoints().forEach(w -> weak.add(w.point().id()));
            for (String k : m.weakPoints().keySet()) {
                assertTrue(weak.contains(k), m.id() + ": weak point " + k);
            }
            for (String k : m.phases().keySet()) {
                assertTrue(Integer.parseInt(k) >= 2 && Integer.parseInt(k) <= 3, m.id() + " phase " + k);
            }
        }
    }

    // ------------------------------------------------------------------ the rules of section 7

    @Test
    void theClassCueIsTimedSoItsHitAtLandsOnTheRelease() {
        assertEquals(10, SoundIndex.cueDelay(26, 0.78), "a 1.3 s heavy wind-up: the cue at tick 10 (26 - 16)");
        assertEquals(0, SoundIndex.cueDelay(16, 0.78), "a wind-up shorter than the swell: at once");
        assertEquals(0, SoundIndex.cueDelay(8, 0.78));
        assertEquals(10, SoundIndex.cueDelay(30, 1.0), "area");
        assertEquals(10, SoundIndex.cueDelay(40, 1.5), "ultimate");
        assertEquals(0, SoundIndex.cueDelay(26, Double.NaN), "light and unblockable cues play at the start");
        assertEquals(0, SoundIndex.cueDelay(0, 0.78));
        // with the committed cues: the hit lands hit_at after the cue starts
        for (String c : List.of("telegraph.heavy", "telegraph.area", "telegraph.ultimate", "telegraph.dive")) {
            SoundIndex.Event e = index.event(index.cue(c));
            assertTrue(e.hasHitAt(), c);
            int windup = 60;
            int at = SoundIndex.cueDelay(windup, e.hitAt());
            assertEquals(Math.round(e.hitAt() * 20), windup - at, c);
            assertTrue(e.hitAt() <= e.length(), c + ": hit_at inside the cue");
        }
    }

    @Test
    void theClassCueOfAMove() {
        assertEquals("telegraph.light", SoundIndex.telegraphCue("light", false, false));
        assertEquals("telegraph.heavy", SoundIndex.telegraphCue("heavy", false, false));
        assertEquals("telegraph.area", SoundIndex.telegraphCue("AREA", false, false));
        assertEquals("telegraph.ultimate", SoundIndex.telegraphCue("ultimate", false, false));
        assertEquals(SoundIndex.UNBLOCKABLE, SoundIndex.telegraphCue("heavy", true, false), "a grab is unblockable");
        assertEquals(SoundIndex.DIVE, SoundIndex.telegraphCue("heavy", false, true));
        assertNull(SoundIndex.telegraphCue(null, false, false));
        assertNull(SoundIndex.telegraphCue("basic", false, false));
        for (String c : List.of("light", "heavy", "area", "ultimate")) {
            assertNotNull(index.cue(SoundIndex.telegraphCue(c, false, false)), c);
        }
        assertNotNull(index.cue(SoundIndex.UNBLOCKABLE));
        assertNotNull(index.cue(SoundIndex.DIVE));
    }

    @Test
    void impactsCycleByShot() {
        SoundIndex.MoveSounds barrage = index.hero("ambrose_nettlewick").moves().get("potion_barrage");
        assertEquals(3, barrage.impact().size());
        assertEquals(barrage.impact().get(0), barrage.impact(0));
        assertEquals(barrage.impact().get(1), barrage.impact(1));
        assertEquals(barrage.impact().get(2), barrage.impact(2));
        assertEquals(barrage.impact().get(0), barrage.impact(3), "a list cycles");
        SoundIndex.MoveSounds hiss = index.hero("natsuho_komezaki").moves().get("hiss");
        assertNull(hiss.impact(0), "no impact");
        assertEquals("factionclash:hero.natsuho_komezaki.claw_hit",
                index.hero("natsuho_komezaki").moves().get("basic").impact(7), "one impact for every shot");
    }

    @Test
    void passivesFindTheirKey() {
        Set<String> keys = Set.of("nine_lives", "bewitched_full", "hex_complete");
        assertEquals("nine_lives", SoundIndex.passiveKey(keys, "Nine Lives"));
        assertEquals("bewitched_full", SoundIndex.passiveKey(keys, "Bewitched"), "a key for a passive's moment");
        assertEquals("hex_complete", SoundIndex.passiveKey(keys, "Hex Complete"));
        assertNull(SoundIndex.passiveKey(keys, "Cat Crit"));
        assertNull(SoundIndex.passiveKey(keys, "Lives"), "only the name or the name and a moment");
        assertNull(SoundIndex.passiveKey(Set.of(), "Nine Lives"));
        assertEquals("a_b_c", SoundIndex.slug(" A-b  c! "));
    }

    @Test
    void poiseCracksOncePerCycleAtThreeQuarters() {
        assertTrue(SoundIndex.poiseCracks(70, 76, 100));
        assertTrue(SoundIndex.poiseCracks(0, 75, 100));
        assertFalse(SoundIndex.poiseCracks(75, 80, 100), "already past the line");
        assertFalse(SoundIndex.poiseCracks(10, 74, 100));
        assertFalse(SoundIndex.poiseCracks(10, 80, 0), "no bar");
    }

    @Test
    void anActionCodeNamesItsMoveForTheLoop() {
        SoundIndex.HeroMap ambrose = index.hero("ambrose_nettlewick");
        List<String> visual = List.of("potion_barrage", "cauldron", "great_brew");
        SoundIndex.MoveSounds brew = SoundIndex.moveOfCode(ambrose, visual, HeroActions.FIRST_MOVE + 2);
        assertNotNull(brew);
        assertEquals("factionclash:hero.ambrose_nettlewick.brew_loop", brew.loop());
        assertTrue(index.event(brew.loop()).loop());
        assertNull(SoundIndex.moveOfCode(ambrose, visual, HeroActions.FIRST_MOVE + 5), "past the visual list");
        assertNull(SoundIndex.moveOfCode(ambrose, visual, HeroActions.actionCode("stagger")));
        assertEquals("ultimate", SoundIndex.moveOfCode(ambrose, visual, HeroActions.ULTIMATE).cls());
        assertNull(SoundIndex.moveOfCode(null, visual, HeroActions.ULTIMATE));
    }

    @Test
    void aHeroMapReadsFromATestsJson() {
        String json = "{\"voice\": {\"talk\": \"factionclash:hero.voice.natsuho_komezaki\", \"rate\": 2, \"melody\": [0, 3],"
                + " \"fallback_blip\": \"bell\", \"hurt\": {\"event\": \"minecraft:entity.player.hurt\", \"pitch\": 1.2}},"
                + " \"moves\": {\"slam\": {\"class\": \"heavy\", \"windup\": \"a\", \"impact\": [\"b\", \"c\"]}},"
                + " \"steps\": {\"volume\": 0.5, \"with\": \"d\"}}";
        SoundIndex.HeroMap m = SoundIndex.hero("fx", JsonTree.object(JsonTree.parse(json)));
        assertEquals("a", m.moves().get("slam").windup());
        assertEquals("c", m.moves().get("slam").impact(1));
        assertEquals(0.5, m.stepsVolume(), 1e-9);
        assertEquals("d", m.stepsWith());
        assertEquals(1.2, m.voice().hurt().pitch(), 1e-9);
        assertNull(m.voice().death());
        assertEquals(List.of(0.0, 3.0), m.voice().melody());
        assertTrue(m.states().isEmpty() && m.passives().isEmpty() && m.intro() == null);
        SoundIndex withIt = index.withHero("fx", m);
        assertEquals(m, withIt.hero("fx"));
        assertNull(index.hero("fx"), "the index itself is unchanged");
    }

    @Test
    void pickStaysInItsRange() {
        assertEquals(0.9, SoundIndex.pick(0.9, 1.1, 0), 1e-9);
        assertEquals(1.0, SoundIndex.pick(0.9, 1.1, 0.5), 1e-9);
        assertEquals(1.1, SoundIndex.pick(0.9, 1.1, 1.0), 1e-9);
        assertEquals(1.0, SoundIndex.pick(1.0, 1.0, 0.7), 1e-9);
        assertEquals("minecraft:block.note_block.xylophone", SoundIndex.noteBlock("xylophone"));
        assertEquals("minecraft:block.note_block.harp", SoundIndex.noteBlock(null));
    }
}
