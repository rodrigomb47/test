package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroTimings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server timing files ({@code data/factionclash/hero_timings/<file>.json}, HEROES_V2 4.8), read from the mod file
 * (like the visual files, so a dedicated server has them) and looked up through a hero's animation chain, the same
 * order the client plays from: the hero's own file, its style library, the core library. The server clears an action
 * code once its clip has played; the move runner (A5, {@link HeroCombat}) times a move's wind-up, hits and recovery from
 * the same records. GameTests may register a timing file of their own for their fixtures ({@link #registerTransient});
 * the generated files are never written here.
 */
public final class HeroTimingFiles {
    private static final Map<String, HeroTimings> CACHE = new ConcurrentHashMap<>();
    /** Timing files GameTests register for their fixtures (they win over a file of the same name). */
    private static final Map<String, HeroTimings> TRANSIENT = new ConcurrentHashMap<>();
    private static final HeroTimings NONE = new HeroTimings("", -1, Map.of());

    private HeroTimingFiles() {
    }

    /** The clip named {@code animation} as the hero's chain resolves it, or null. */
    public static HeroTimings.Clip clip(HeroEntry hero, String animation) {
        for (String file : hero.visual().animationChain()) {
            HeroTimings.Clip c = file(file).clips().get(animation);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** One timing file (an empty one when the mod has no such file). */
    public static HeroTimings file(String name) {
        HeroTimings t = TRANSIENT.isEmpty() ? null : TRANSIENT.get(name);
        return t != null ? t : CACHE.computeIfAbsent(name, HeroTimingFiles::read);
    }

    /**
     * A test fixture's timing file under {@code name} (a name its fixture's animation chain lists) until the returned
     * handle is closed. Never used for the mod's own heroes.
     */
    public static AutoCloseable registerTransient(String name, HeroTimings timings) {
        TRANSIENT.put(name, timings);
        return () -> TRANSIENT.remove(name, timings);
    }

    /** Forgets the files (read again on the next lookup; at every server start, with the hero registry). */
    public static void reload() {
        CACHE.clear();
    }

    private static HeroTimings read(String name) {
        Path p = HeroRegistry.modPath("data", FactionClash.MODID, "hero_timings", name + ".json");
        if (p == null || !Files.isRegularFile(p)) {
            return NONE;
        }
        try {
            return HeroTimings.parse(name, Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            FactionClash.LOGGER.warn("Heroes: timing file {} could not be read: {}", name, e.toString());
            return NONE;
        }
    }
}
