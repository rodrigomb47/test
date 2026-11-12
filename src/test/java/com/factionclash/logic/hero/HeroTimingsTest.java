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

    /**
     * Each clip is written for the controller that plays it: every clip the {@code base} controller can choose (from
     * any motion, locomotion mode or forced pose) is a base clip in the timing files, and every clip the {@code action}
     * controller can play (every action code's animation and its {@code .loop}, the swing's work clips, death) is an
     * action clip. Otherwise its events would fire a blend early or late (HEROES_V2 4.8).
     */
    @Test
    void everyClipIsWrittenForTheControllerThatPlaysIt() throws IOException {
        java.util.Set<String> base = new java.util.TreeSet<>();
        for (int flags = 0; flags < 128; flags++) {
            for (int mode = 0; mode < 16; mode++) {
                for (double speed : new double[]{0, 0.1, 0.3}) {
                    base.addAll(HeroClips.baseCandidates(new HeroClips.Motion(speed, (flags & 64) != 0 ? -1 : 0, (flags & 1) != 0,
                            (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0, mode)));
                }
            }
        }
        for (int code = 1; code < HeroClips.FORCED.size(); code++) {
            base.addAll(HeroClips.forced(code));
        }
        java.util.Set<String> action = new java.util.TreeSet<>(List.of("death"));
        for (int code = 1; code < 256; code++) {
            String name = HeroClips.actionClip(code, List.of(), List.of());
            if (name != null) {
                action.add(name);
                action.add(name + ".loop");
            }
        }
        for (HeroClips.Held held : HeroClips.Held.values()) {
            action.addAll(HeroClips.swingCandidates(held, false));
            action.addAll(HeroClips.swingCandidates(held, true));
        }
        java.util.Set<String> both = new java.util.TreeSet<>(base);
        both.retainAll(action);
        assertTrue(both.isEmpty(), "played by both controllers: " + both);
        int checked = 0;
        for (HeroTimings t : shipped()) {
            for (String name : base) {
                HeroTimings.Clip c = t.clips().get(name);
                if (c != null) {
                    assertFalse(c.isAction(), t.file() + " " + name + " is played by the base controller but written as an action");
                    checked++;
                }
            }
            for (String name : action) {
                HeroTimings.Clip c = t.clips().get(name);
                if (c != null) {
                    assertTrue(c.isAction(), t.file() + " " + name + " is played by the action controller but written as a base clip");
                    checked++;
                }
            }
        }
        assertTrue(checked >= 20, "clips checked: " + checked);
    }
}
