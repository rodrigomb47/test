package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroIdentity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Every hero the mod knows, by id (HEROES_V2 12.1). A1a reads the visual side: each
 * {@code assets/factionclash/heroes/<id>.visual.json} in the mod jar (read from the mod file, so a dedicated server,
 * which loads no client assets, knows every hero's size too), then the operator's overrides in
 * {@code config/factionclash/heroes/<id>.json} (their {@code name} and {@code title} replace the built-in ones; never
 * {@code id}, {@code art} or {@code animations}), then fixtures registered for a test with
 * {@link #registerTransient}. Ids starting with {@code _} are the shared libraries and the neutral {@code _fallback}
 * model, never heroes. Read lazily on both sides and again when a server starts. A1b adds the server definitions
 * ({@code data/factionclash/heroes/}) and {@code /fc hero reload}.
 *
 * <p><b>Preview fixtures</b> (A2): visual files an operator puts in {@code config/factionclash/hero_fixtures/} (for
 * example the art pipeline's six style fixtures, built into a temporary folder) are registered as fixtures at every
 * server start, so {@code /fc admin hero lineup} can stand them up for screenshots; the client draws them from a
 * resource pack holding their art, whose visual files the client model registers with {@link #setClientEntries}. A
 * fixture is never given to a faction, so the director never drafts it; a built-in hero's id always wins.
 */
public final class HeroRegistry {
    private static final String SUFFIX = ".visual.json";
    private static volatile Map<String, HeroEntry> loaded;
    private static volatile List<String> problems = List.of();
    private static final Map<String, HeroEntry> TRANSIENT = new ConcurrentHashMap<>();
    /** Preview fixtures from the server's config folder (refilled at every reload). */
    private static volatile Map<String, HeroEntry> fixtures = Map.of();
    /** The client's resource-pack visuals for ids the mod does not ship (set by the client model; empty on a server). */
    private static volatile Map<String, HeroEntry> clientEntries = Map.of();

    private HeroRegistry() {
    }

    /** The hero with this id (a test fixture first), or null. The fallback model is not a hero. */
    public static HeroEntry get(String id) {
        if (id == null || !HeroIdentity.isHeroId(id)) {
            return null;
        }
        HeroEntry t = TRANSIENT.get(id);
        if (t != null) {
            return t;
        }
        HeroEntry b = loaded().get(id);
        if (b != null) {
            return b;
        }
        HeroEntry f = fixtures.get(id);
        return f != null ? f : clientEntries.get(id);
    }

    /** The hero with this id, or the neutral fallback entry (for an unknown or dormant hero's size and model). */
    public static HeroEntry getOrFallback(String id) {
        HeroEntry e = get(id);
        return e != null ? e : fallback();
    }

    /** The neutral rig every unknown hero is drawn and sized with. */
    public static HeroEntry fallback() {
        HeroEntry f = loaded().get(HeroIdentity.FALLBACK);
        return f != null ? f : BUILT_IN_FALLBACK;
    }

    /** Every hero (fixtures included), by id. */
    public static List<HeroEntry> heroes() {
        List<HeroEntry> out = new ArrayList<>();
        for (HeroEntry e : loaded().values()) {
            if (HeroIdentity.isHeroId(e.id()) && !TRANSIENT.containsKey(e.id())) {
                out.add(e);
            }
        }
        out.addAll(TRANSIENT.values());
        for (HeroEntry f : fixtures.values()) {
            if (!TRANSIENT.containsKey(f.id())) {
                out.add(f);
            }
        }
        out.sort(Comparator.comparing(HeroEntry::id));
        return out;
    }

    /**
     * Registers the client's own visual files (a resource pack's) for ids the mod does not ship, as preview fixtures;
     * called by the client model after each resource reload. Replaces the previous set.
     */
    public static void setClientEntries(Map<String, HeroEntry> entries) {
        Map<String, HeroEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, HeroEntry> e : entries.entrySet()) {
            if (HeroIdentity.isHeroId(e.getKey()) && !loaded().containsKey(e.getKey())) {
                m.put(e.getKey(), e.getValue());
            }
        }
        clientEntries = Collections.unmodifiableMap(m);
    }

    /** What was wrong with the files at the last load (one line each, naming the file). */
    public static List<String> problems() {
        return problems;
    }

    /** Re-reads the jar's visual files and the config overrides (fixtures stay). */
    public static synchronized void reload() {
        List<String> found = new ArrayList<>();
        Map<String, HeroEntry> map = new LinkedHashMap<>();
        Map<String, HeroEntry> fx = new LinkedHashMap<>();
        try {
            readBuiltIn(map, found);
            readOverrides(map, found);
            readFixtures(map, fx, found);
        } catch (RuntimeException e) {
            found.add("hero files could not be read: " + e);
            FactionClash.LOGGER.error("Hero files could not be read", e);
        }
        loaded = Collections.unmodifiableMap(map);
        fixtures = Collections.unmodifiableMap(fx);
        problems = List.copyOf(found);
        HeroTimingFiles.reload();
        long heroes = map.keySet().stream().filter(HeroIdentity::isHeroId).count();
        FactionClash.LOGGER.info("Heroes: {} loaded ({}){}", heroes, String.join(", ", map.keySet().stream().filter(HeroIdentity::isHeroId).toList()),
                found.isEmpty() ? "" : ", " + found.size() + " problem(s)");
        if (!fx.isEmpty()) {
            FactionClash.LOGGER.info("Heroes: {} preview fixture(s) from config/{}/hero_fixtures ({})", fx.size(), FactionClash.MODID,
                    String.join(", ", fx.keySet()));
        }
        for (String p : found) {
            FactionClash.LOGGER.warn("Heroes: {}", p);
        }
    }

    private static Map<String, HeroEntry> loaded() {
        Map<String, HeroEntry> m = loaded;
        if (m == null) {
            reload();
            m = loaded;
        }
        return m;
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Registers a hero for a test's duration from the text of a visual file (fixtures are drawn with the
     * {@code _fallback} model). Closing the handle removes it again. A fixture replaces a built-in hero of the same id
     * while it is registered.
     */
    public static Transient registerTransient(String id, String visualJson) {
        if (!HeroIdentity.isHeroId(id)) {
            throw new IllegalArgumentException("not a hero id: " + id);
        }
        JsonObject o = JsonParser.parseString(visualJson).getAsJsonObject();
        HeroVisual v = HeroVisual.parse(id, o);
        String name = o.has("name") ? o.get("name").getAsString() : HeroIdentity.nameFromId(id);
        HeroEntry e = new HeroEntry(id, name, "", v, HeroEntry.Source.TRANSIENT, false, List.of());
        TRANSIENT.put(id, e);
        return new Transient(e);
    }

    /** A registered fixture; {@link #close()} unregisters it (once, and only if it was not replaced since). */
    public record Transient(HeroEntry entry) implements AutoCloseable {
        @Override
        public void close() {
            TRANSIENT.remove(entry.id(), entry);
        }
    }

    // ------------------------------------------------------------------ files

    private static void readBuiltIn(Map<String, HeroEntry> map, List<String> found) {
        Path dir = modPath("assets", FactionClash.MODID, "heroes");
        if (dir == null || !Files.isDirectory(dir)) {
            found.add("no hero folder in the mod jar (assets/" + FactionClash.MODID + "/heroes)");
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        } catch (IOException e) {
            found.add("the hero folder could not be listed: " + e.getMessage());
            return;
        }
        for (Path p : files) {
            String file = p.getFileName().toString();
            String id = file.substring(0, file.length() - SUFFIX.length());
            if (!HeroIdentity.isHeroId(id) && !id.equals(HeroIdentity.FALLBACK)) {
                found.add(file + ": not a hero id, skipped");
                continue;
            }
            JsonObject o = readObject(p, file, found);
            if (o == null) {
                continue;
            }
            HeroVisual v = HeroVisual.parse(id, o);
            v.problems().forEach(x -> found.add(file + ": " + x));
            boolean ownModel = exists(found, file, "geo", "hero", id + ".geo.json");
            if (ownModel) {
                exists(found, file, "textures", "entity", "hero", id + ".png");
            }
            for (String a : v.animationChain()) {
                exists(found, file, "animations", "hero", a + ".animation.json");
            }
            map.put(id, new HeroEntry(id, HeroIdentity.nameFromId(id), "", v, HeroEntry.Source.BUILTIN, ownModel, List.of()));
        }
    }

    /**
     * Preview fixtures: {@code config/factionclash/hero_fixtures/<id>.visual.json}, registered with their own model
     * (the client draws them from a resource pack); an id the mod ships is skipped.
     */
    private static void readFixtures(Map<String, HeroEntry> builtIn, Map<String, HeroEntry> out, List<String> found) {
        Path dir = configDir() == null ? null : configDir().getParent().resolve("hero_fixtures");
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        } catch (IOException e) {
            found.add("config/factionclash/hero_fixtures could not be listed: " + e.getMessage());
            return;
        }
        for (Path p : files) {
            String file = "config/factionclash/hero_fixtures/" + p.getFileName();
            String id = p.getFileName().toString().substring(0, p.getFileName().toString().length() - SUFFIX.length());
            if (!HeroIdentity.isHeroId(id) || builtIn.containsKey(id)) {
                found.add(file + ": not a hero id, or the id of a shipped hero; skipped");
                continue;
            }
            JsonObject o = readObject(p, file, found);
            if (o != null) {
                out.put(id, fixtureEntry(id, o, found, file));
            }
        }
    }

    /** A preview fixture's entry from its visual file (its own model, name from the id). */
    public static HeroEntry fixtureEntry(String id, JsonObject o, List<String> found, String file) {
        HeroVisual v = HeroVisual.parse(id, o);
        if (found != null) {
            v.problems().forEach(x -> found.add(file + ": " + x));
        }
        return new HeroEntry(id, HeroIdentity.nameFromId(id), "preview fixture", v, HeroEntry.Source.FIXTURE, true, List.of());
    }

    /** Operator overrides: {@code config/factionclash/heroes/<id>.json}, name and title only in this build. */
    private static void readOverrides(Map<String, HeroEntry> map, List<String> found) {
        Path dir = configDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            found.add("config/factionclash/heroes could not be listed: " + e.getMessage());
            return;
        }
        for (Path p : files) {
            String file = "config/factionclash/heroes/" + p.getFileName();
            String id = p.getFileName().toString().substring(0, p.getFileName().toString().length() - ".json".length());
            HeroEntry base = map.get(id);
            if (base == null || !HeroIdentity.isHeroId(id)) {
                found.add(file + ": no built-in hero \"" + id + "\" to override; ignored");
                continue;
            }
            JsonObject o = readObject(p, file, found);
            if (o == null) {
                continue;
            }
            Overlay r = overlay(base, o);
            r.notes().forEach(x -> found.add(file + ": " + x));
            map.put(id, r.entry());
        }
    }

    /** The result of applying an override file: the new entry and what was ignored. */
    public record Overlay(HeroEntry entry, List<String> notes) {
    }

    /**
     * Applies an override's top-level fields to a built-in hero: {@code name} and {@code title} replace its own;
     * {@code id}, {@code art} and {@code animations} can never be overridden; other fields are not read in this build.
     */
    public static Overlay overlay(HeroEntry base, JsonObject o) {
        List<String> notes = new ArrayList<>();
        List<String> used = new ArrayList<>(base.overrides());
        String name = base.name(), title = base.title();
        for (Map.Entry<String, JsonElement> f : o.entrySet()) {
            String k = f.getKey();
            if (k.startsWith("_")) {
                continue; // a comment
            }
            switch (k) {
                case "name", "title" -> {
                    if (!f.getValue().isJsonPrimitive() || f.getValue().getAsString().isBlank()) {
                        notes.add(k + " is not a text; ignored");
                    } else if (k.equals("name")) {
                        name = f.getValue().getAsString().strip();
                        used.add(k);
                    } else {
                        title = f.getValue().getAsString().strip();
                        used.add(k);
                    }
                }
                case "id", "art", "animations", "format" -> {
                    if (!k.equals("format")) {
                        notes.add(k + " can never be overridden; ignored");
                    }
                }
                default -> notes.add(k + " is not read in this build; ignored");
            }
        }
        return new Overlay(new HeroEntry(base.id(), name, title, base.visual(), base.source(), base.ownModel(), List.copyOf(used)), notes);
    }

    private static JsonObject readObject(Path p, String file, List<String> found) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(r);
            if (e.isJsonObject()) {
                return e.getAsJsonObject();
            }
            found.add(file + ": not a JSON object");
        } catch (IOException | RuntimeException e) {
            found.add(file + ": " + e.getMessage());
        }
        return null;
    }

    /** Whether the mod jar has {@code assets/factionclash/<path>}; a missing file is a problem of {@code file}. */
    private static boolean exists(List<String> found, String file, String... path) {
        String[] full = new String[path.length + 2];
        full[0] = "assets";
        full[1] = FactionClash.MODID;
        System.arraycopy(path, 0, full, 2, path.length);
        Path p = modPath(full);
        if (p != null && Files.isRegularFile(p)) {
            return true;
        }
        found.add(file + ": " + String.join("/", full) + " is missing");
        return false;
    }

    /** A path inside the mod's own files (the jar, or the build output in development). */
    public static Path modPath(String... path) {
        ModList mods = ModList.get();
        IModFileInfo info = mods == null ? null : mods.getModFileById(FactionClash.MODID);
        return info == null ? null : info.getFile().findResource(path);
    }

    private static Path configDir() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FactionClash.MODID).resolve("heroes");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Used only when even {@code _fallback.visual.json} cannot be read: the adult rig, drawn with the fallback files. */
    private static final HeroEntry BUILT_IN_FALLBACK = new HeroEntry(HeroIdentity.FALLBACK, "Hero", "",
            new HeroVisual(HeroIdentity.FALLBACK, "adult_m", "adult", "standard", HeroVisual.DEFAULT_HEIGHT_PX, HeroVisual.DEFAULT_HEAD_Y,
                    HeroVisual.DEFAULT_RENDER_SCALE, 1.0, "fists", List.of(HeroIdentity.FALLBACK, "_style_fists", "_core"),
                    false, false, 256, 128, HeroLook.DEFAULT, List.of()),
            HeroEntry.Source.BUILTIN, false, List.of());
}
