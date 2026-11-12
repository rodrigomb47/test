package com.factionclash.logic.hero;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped timing files (HEROES_V2 4.8) against the controllers: every base clip was written for the base
 * controller's blend and every action for the action controller's, so the events fire on the frame their pose shows.
 */
class HeroTimingsTest {
    private static final Path DIR = Path.of("src/main/resources/data/factionclash/hero_timings");

    private static List<HeroTimings> shipped() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().map(p -> {
                try {
                    String f = p.getFileName().toString();
                    return HeroTimings.parse(f.substring(0, f.length() - 5), Files.readString(p));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }

    @Test
    void everyClipWasWrittenForTheTransitionItsControllerUses() throws IOException {
        List<HeroTimings> files = shipped();
        assertFalse(files.isEmpty(), "no timing files in " + DIR.toAbsolutePath());
        int clips = 0;
        for (HeroTimings t : files) {
            assertEquals(2, t.format(), t.file() + ": format");
            for (HeroTimings.Clip c : t.clips().values()) {
                clips++;
                int expected = c.isAction() ? HeroClips.ACTION_TRANSITION : HeroClips.BASE_TRANSITION;
                assertEquals(expected, c.transitionTicks(), t.file() + " " + c.name() + " (" + c.kind() + ")");
                assertEquals(c.transitionTicks() + c.ticks(), c.totalTicks(), t.file() + " " + c.name() + ": total = blend + clip");
                for (HeroTimings.Event e : c.events()) {
                    assertTrue(e.tick() >= c.transitionTicks() && e.tick() < c.totalTicks(),
                            t.file() + " " + c.name() + ": event at " + e.tick() + " outside the clip");
                }
            }
        }
        assertTrue(clips >= 5, "the core and katana clips: " + clips);
    }

    @Test
    void theKatanaSwingHitsAtTickTen() throws IOException {
        HeroTimings katana = shipped().stream().filter(t -> t.file().equals("_style_katana")).findFirst().orElseThrow();
        HeroTimings.Clip a = katana.clips().get("attack.1");
        assertTrue(a.isAction());
        assertFalse(a.loops());
        assertEquals(10, a.firstHit(), "cut at clip 0.40 s + the 2-tick blend");
        assertEquals(10, a.windup());
        assertEquals(1, a.active());
        assertEquals(9, a.recovery());
        assertEquals(20, a.totalTicks());
        assertTrue(katana.clips().get("combat.idle").loops());
        assertEquals(-1, katana.clips().get("combat.idle").firstHit());
    }

    @Test
    void anActionLastsItsTotalDividedByTheSpeed() {
        assertEquals(20, HeroTimings.ticksAt(20, 1.0));
        assertEquals(10, HeroTimings.ticksAt(20, 2.0));
        assertEquals(40, HeroTimings.ticksAt(20, 0.5));
        assertEquals(16, HeroTimings.ticksAt(20, 1.25));
        assertEquals(15, HeroTimings.ticksAt(20, 1.4), "rounded up: the clip has played");
        assertEquals(20, HeroTimings.ticksAt(20, 0), "a broken speed is 1");
    }
}
