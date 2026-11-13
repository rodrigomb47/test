package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroDef.LineEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hero says, to whom, how often, and its name tag (v1 23, 25.8; HEROES_V2 12.15; task A1b). */
class HeroSpeechTest {
    private static HeroDef withLines(String lines) {
        return V2Files.def(V2Files.hero2("\"lines\": " + lines));
    }

    @Test
    void everyGenericLineUsesOnlyItsEventsPlaceholders() {
        Pattern ph = Pattern.compile("\\{([a-z_]+)}");
        for (Map.Entry<LineEvent, List<String>> e : HeroSpeech.DEFAULT_LINES.entrySet()) {
            List<String> allowed = HeroCatalogue.PLACEHOLDERS_2.get(e.getKey());
            assertFalse(e.getValue().isEmpty(), e.getKey().id());
            for (String line : e.getValue()) {
                assertTrue(line.length() <= 120 && !line.contains("\n"), line);
                Matcher m = ph.matcher(line);
                while (m.find()) {
                    assertTrue(allowed != null && allowed.contains(m.group(1)), e.getKey().id() + ": {" + m.group(1) + "} in " + line);
                }
            }
        }
        for (LineEvent e : List.of(LineEvent.SUMMONED, LineEvent.GREETING, LineEvent.FIGHT_START, LineEvent.KILL,
                LineEvent.LOW_HEALTH, LineEvent.ALLY_FALLEN, LineEvent.VICTORY, LineEvent.LEVEL_UP, LineEvent.FALLEN,
                LineEvent.REVIVED, LineEvent.ESCAPE, LineEvent.LAST_STAND)) {
            assertTrue(HeroSpeech.DEFAULT_LINES.containsKey(e), "a generic line for " + e.id());
        }
        assertFalse(HeroSpeech.DEFAULT_LINES.containsKey(LineEvent.PACT_OFFER), "diplomacy needs the hero's own words");
    }

    @Test
    void aHerosOwnLineComesFirstThenAGenericOneThenNothing() {
        HeroDef d = withLines("{\"kill\": [\"One.\", \"Two.\", \"Three.\"]}");
        assertEquals("One.", HeroSpeech.pick(d, LineEvent.KILL, true, 0));
        assertEquals("Two.", HeroSpeech.pick(d, LineEvent.KILL, true, 0.5));
        assertEquals("Three.", HeroSpeech.pick(d, LineEvent.KILL, true, 0.99));
        assertEquals("Three.", HeroSpeech.pick(d, LineEvent.KILL, true, 1.0), "a roll of 1 stays in the list");
        assertEquals("One.", HeroSpeech.pick(d, LineEvent.KILL, true, -3), "a negative roll reads as 0");
        assertEquals("One.", HeroSpeech.pick(d, LineEvent.KILL, false, 0), "its own lines need no defaults");
        assertEquals(HeroSpeech.DEFAULT_LINES.get(LineEvent.VICTORY).get(0), HeroSpeech.pick(d, LineEvent.VICTORY, true, 0));
        assertNull(HeroSpeech.pick(d, LineEvent.VICTORY, false, 0), "heroes.defaultLines off: silent");
        assertNull(HeroSpeech.pick(d, LineEvent.PACT_OFFER, true, 0), "no generic line for diplomacy");
        assertEquals(HeroSpeech.DEFAULT_LINES.get(LineEvent.KILL).get(0), HeroSpeech.pick(null, LineEvent.KILL, true, 0),
                "a dormant hero (no definition) has the generic lines");
        assertNull(HeroSpeech.pick(null, LineEvent.KILL, false, 0));
    }

    @Test
    void placeholdersAreFilledAndAMissingOneReadsSomeone() {
        assertEquals("Face me, Zombie!", HeroSpeech.fill("Face me, {enemy}!", Map.of("enemy", "Zombie")));
        assertEquals("someone is down.", HeroSpeech.fill("{enemy} is down.", Map.of()));
        assertEquals("someone is down.", HeroSpeech.fill("{enemy} is down.", Map.of("enemy", " ")));
        assertEquals("Hi $1 \\ there", HeroSpeech.fill("Hi {player} there", Map.of("player", "$1 \\")), "no regex in names");
        assertEquals("Ira of the Tide Alliance", HeroSpeech.fill("{name} of the {faction}", Map.of("name", "Ira",
                "faction", "Tide Alliance")));
        assertEquals("{Not} a {placeholder", HeroSpeech.fill("{Not} a {placeholder", Map.of()), "only {lower_case} words");
    }

    @Test
    void eachEventHasItsAudience() {
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.SUMMONED));
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.FALLEN));
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.REVIVED));
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.LEVEL_UP));
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.BASE_ATTACKED));
        assertEquals(HeroSpeech.Audience.FACTION, HeroSpeech.audience(LineEvent.FOUNDING));
        assertEquals(HeroSpeech.Audience.PLAYER, HeroSpeech.audience(LineEvent.GREETING));
        assertEquals(HeroSpeech.Audience.PLAYER, HeroSpeech.audience(LineEvent.GIFT_LIKED));
        assertEquals(HeroSpeech.Audience.NEARBY, HeroSpeech.audience(LineEvent.FIGHT_START));
        assertEquals(HeroSpeech.Audience.NEARBY, HeroSpeech.audience(LineEvent.KILL));
        assertEquals(HeroSpeech.Audience.NEARBY, HeroSpeech.audience(LineEvent.ESCAPE));
        assertEquals(HeroSpeech.Audience.NEARBY, HeroSpeech.audience(LineEvent.LAST_STAND));
        for (LineEvent e : LineEvent.values()) {
            HeroSpeech.audience(e); // every event has one
        }
    }

    @Test
    void theChatLineAndTheNameTag() {
        assertEquals("<★ Bram Ostler> Eyes on me!", HeroSpeech.chatLine("Bram Ostler", "Eyes on me!"));
        assertEquals("★ Bram Ostler · the Gatewarden", HeroSpeech.tagName("Bram Ostler", "the Gatewarden"));
        assertEquals("★ Bram Ostler", HeroSpeech.tagName("Bram Ostler", ""));
        assertEquals("★ Bram Ostler", HeroSpeech.tagName("Bram Ostler", null));
        assertEquals("[Fighting 80%]", HeroSpeech.fightTag(80, true));
        assertEquals("[Fighting 100%]", HeroSpeech.fightTag(250, true), "clamped");
        assertEquals("[Fighting 0%]", HeroSpeech.fightTag(-4, true));
        assertEquals("[Fighting]", HeroSpeech.fightTag(80, false), "heroes.showEnergy off");
        assertEquals("(dormant)", HeroSpeech.DORMANT);
        assertEquals(60, HeroSpeech.BUBBLE_TICKS, "3 s (v1 23)");
    }

    @Test
    void theRateLimitOfV1() {
        HeroSpeech.Clock c = new HeroSpeech.Clock();
        assertTrue(c.allows(LineEvent.KILL, 0, 20), "nothing said yet, even at game time 0");
        c.said(LineEvent.KILL, 1000);
        assertFalse(c.allows(LineEvent.FIGHT_START, 1000 + 399, 20), "one line per 20 s");
        assertTrue(c.allows(LineEvent.FIGHT_START, 1000 + 400, 20));
        assertTrue(c.allows(LineEvent.FIGHT_START, 1000 + 200, 10), "the cooldown is the config's");
        for (LineEvent e : List.of(LineEvent.SUMMONED, LineEvent.FALLEN, LineEvent.REVIVED, LineEvent.ULTIMATE)) {
            assertTrue(c.allows(e, 1001, 20), e.id() + " ignores the clock");
        }
        assertTrue(c.allows(LineEvent.GREETING, 1001, 20), "a greeting has its own clock");
        c.said(LineEvent.GREETING, 1001);
        assertEquals(1000, c.lastLine(), "a greeting does not move the line clock");
        assertFalse(c.allows(LineEvent.GREETING, 1001 + 99, 20), "5 s between greetings");
        assertTrue(c.allows(LineEvent.GREETING, 1001 + 100, 20));
        HeroSpeech.Clock idle = new HeroSpeech.Clock();
        idle.said(LineEvent.IDLE, 5000);
        assertFalse(idle.allows(LineEvent.IDLE, 5000 + 15 * 60 * 20 - 1, 20), "idle at most every 15 minutes");
        assertTrue(idle.allows(LineEvent.IDLE, 5000 + 15 * 60 * 20, 20));
        assertFalse(idle.allows(LineEvent.KILL, 5000 + 100, 20), "an idle line also counts as a line");
        HeroSpeech.Clock zero = new HeroSpeech.Clock();
        zero.said(LineEvent.KILL, 0);
        assertEquals(1, zero.lastLine(), "a line at game time 0 still counts");
    }
}
