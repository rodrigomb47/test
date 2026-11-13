package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroAssembly;
import com.factionclash.logic.hero.HeroCatalogue;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroIdentity;
import com.factionclash.logic.hero.HeroOverride;
import com.factionclash.logic.hero.HeroReader;
import com.factionclash.logic.hero.Problems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Every hero the mod knows, by id (HEROES_V2 12.1). Two sides:
 * <ul>
 * <li><b>The visual side</b> (A1a): each {@code assets/factionclash/heroes/<id>.visual.json} in the mod jar (read from
 * the mod file, so a dedicated server, which loads no client assets, knows every hero's size too). Ids starting with
 * {@code _} are the shared libraries and the neutral {@code _fallback} model, never heroes.</li>
 * <li><b>The definitions</b> (A1b): the server files {@code data/factionclash/heroes/<id>.json}, read by
 * {@link HeroDefinitions} (a reload listener, so {@code /reload} reads them again and a datapack may replace one), each
 * with the operator's override {@code config/factionclash/heroes/<id>.json} merged over it, then checked as built-in
 * heroes ({@link HeroAssembly}: the format-2 reader, the sense rules and the balance pass). Only the mod's own heroes
 * load (the owner's answer of 2026-11-12); a definition that is broken or gone leaves its hero <b>dormant</b>
 * (HEROES_V2 3.4: a plain soldier with the neutral model, name tag {@code (dormant)}).</li>
 * </ul>
 * A hero is listed when it has either side; {@link HeroEntry#def()} is null for a dormant one. Test fixtures come
 * from {@link #registerTransient} (a visual file and a definition, or none). {@link #reload()} reads the visual files,
 * the overrides and the preview fixtures again and re-checks the definitions it last had; {@code /fc hero reload}
 * reads the definitions from the server's packs as well ({@link #reload(ResourceManager)}).
 *
 * <p><b>Preview fixtures</b> (A2): visual files an operator puts in {@code config/factionclash/hero_fixtures/} (for
 * example the art pipeline's six style fixtures, built into a temporary folder) are registered as fixtures at every
 * server start, so {@code /fc admin hero lineup} can stand them up for screenshots; the client draws them from a
 * resource pack holding their art, whose visual files the client model registers with {@link #setClientEntries}. A
 * fixture is never given to a faction, so the director never drafts it; a built-in hero's id always wins.
 */
public final class HeroRegistry {
    private static final String SUFFIX = ".visual.json";
    /** The mod's own visual files, by id ({@code _fallback} included). */
    private static volatile Map<String, HeroEntry> loaded;
    /** Every hero, built-in or a test's: the visual side with its checked definition (null: dormant). */
    private static volatile Map<String, HeroEntry> heroes = Map.of();
    private static volatile List<String> problems = List.of();
    /** The definition check's report of the last assembly (every problem with file and line). */
    private static volatile HeroReader.Loaded definitions = new HeroReader.Loaded(Map.of(), Map.of(), List.of(), new Problems());
    /** The server definitions the reload listener last read, and the operator's overrides. */
    private static volatile List<HeroReader.Source> definitionSources = List.of();
    private static volatile List<HeroReader.Source> overrideSources = List.of();
    private static volatile Set<String> shipped;
    private static final Map<String, Transient> TRANSIENT = new ConcurrentHashMap<>();
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
        HeroEntry b = heroMap().get(id);
        if (b != null) {
            return b;
        }
        HeroEntry f = fixtures.get(id);
        return f != null ? f : clientEntries.get(id);
    }

    /** The checked definition of a hero, or null (unknown or dormant). */
    public static HeroDef definition(String id) {
        HeroEntry e = get(id);
        return e == null ? null : e.def();
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
        List<HeroEntry> out = new ArrayList<>(heroMap().values());
        for (HeroEntry f : fixtures.values()) {
            if (!heroMap().containsKey(f.id())) {
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

    /** What was wrong with the visual files and the folders at the last load (one line each, naming the file). */
    public static List<String> problems() {
        return problems;
    }

    /** The definitions' check at the last assembly: the loaded heroes, those not loaded and every problem. */
    public static HeroReader.Loaded report() {
        return definitions;
    }

    /** Re-reads the jar's visual files, the config overrides and the preview fixtures, and re-checks the definitions. */
    public static synchronized void reload() {
        List<String> found = new ArrayList<>();
        Map<String, HeroEntry> map = new LinkedHashMap<>();
        Map<String, HeroEntry> fx = new LinkedHashMap<>();
        try {
            readBuiltIn(map, found);
            overrideSources = readOverrides(found);
            readFixtures(map, fx, found);
        } catch (RuntimeException e) {
            found.add("hero files could not be read: " + e);
            FactionClash.LOGGER.error("Hero files could not be read", e);
        }
        loaded = Collections.unmodifiableMap(map);
        fixtures = Collections.unmodifiableMap(fx);
        problems = List.copyOf(found);
        HeroTimingFiles.reload();
        assemble();
        logLoad(found, fx);
        writeReport();
    }

    /**
     * {@code /fc hero reload}: the server definitions read again from the server's packs ({@code server} null: the
     * ones the reload listener last read), then {@link #reload()}.
     */
    public static synchronized void reload(ResourceManager resources) {
        if (resources != null) {
            definitionSources = HeroDefinitions.read(resources);
        }
        reload();
    }

    /**
     * The reload listener's result ({@code /reload}, a server start): the definitions are checked again at once, with
     * the config overrides and the visual files read again too, so {@code /reload} sees an override an operator changed
     * or removed (found on the dedicated-server check: the overrides of the last load were kept).
     */
    static synchronized void setDefinitionSources(List<HeroReader.Source> sources) {
        definitionSources = List.copyOf(sources);
        reload();
    }

    /** The server definitions as last read (for {@code /fc hero check}, which reads them again without applying). */
    public static List<HeroReader.Source> definitionSources() {
        return definitionSources;
    }

    /**
     * Checks a set of definitions the way the game would, without applying them ({@code /fc hero check}): the given
     * server definitions (null: the last read), the overrides as the config folder holds them now, and the fixtures.
     */
    public static HeroAssembly.Result check(List<HeroReader.Source> sources) {
        List<String> ignored = new ArrayList<>();
        return HeroAssembly.assemble(new HeroAssembly.Input(sources != null ? sources : definitionSources, shipped(),
                readOverrides(ignored), transientDefinitions(), HeroReader.Options.BUILTIN));
    }

    private static void logLoad(List<String> found, Map<String, HeroEntry> fx) {
        HeroReader.Loaded d = definitions;
        FactionClash.LOGGER.info("Heroes: {} loaded ({}){}; definitions: {}", heroes.size(), String.join(", ", heroes.keySet()),
                found.isEmpty() ? "" : ", " + found.size() + " file problem(s)",
                d.problems().summary(d.heroes().keySet(), d.notLoaded()).replaceFirst("^Heroes: ", ""));
        if (!fx.isEmpty()) {
            FactionClash.LOGGER.info("Heroes: {} preview fixture(s) from config/{}/hero_fixtures ({})", fx.size(), FactionClash.MODID,
                    String.join(", ", fx.keySet()));
        }
        for (String p : found) {
            FactionClash.LOGGER.warn("Heroes: {}", p);
        }
        for (Problems.Problem p : d.problems().all()) {
            if (p.severity() == Problems.Severity.NOTE) {
                FactionClash.LOGGER.info(p.log());
            } else {
                FactionClash.LOGGER.warn(p.log());
            }
        }
        for (Map.Entry<String, HeroEntry> e : heroes.entrySet()) {
            if (e.getValue().dormant()) {
                FactionClash.LOGGER.warn("[Heroes] {} has no valid definition: dormant (a plain soldier with the neutral look)", e.getKey());
            }
        }
    }

    /**
     * Puts the two sides together: every hero with a visual file or a loaded definition (and every test fixture), its
     * name and title from the definition, its look from its visual file (the neutral model without one).
     */
    private static void assemble() {
        Map<String, HeroEntry> visuals = loaded();
        HeroAssembly.Result r;
        try {
            r = HeroAssembly.assemble(new HeroAssembly.Input(definitionSources, shipped(), overrideSources,
                    transientDefinitions(), HeroReader.Options.BUILTIN));
        } catch (RuntimeException e) {
            FactionClash.LOGGER.error("Hero definitions could not be checked", e);
            r = new HeroAssembly.Result(new HeroReader.Loaded(Map.of(), Map.of(), List.of(), new Problems()), Map.of());
        }
        Set<String> ids = new TreeSet<>();
        for (String id : visuals.keySet()) {
            if (HeroIdentity.isHeroId(id)) {
                ids.add(id);
            }
        }
        ids.addAll(r.loaded().heroes().keySet());
        ids.addAll(TRANSIENT.keySet());
        Map<String, HeroEntry> out = new LinkedHashMap<>();
        HeroEntry fb = fallback();
        for (String id : ids) {
            if (!HeroIdentity.isHeroId(id)) {
                continue;
            }
            Transient t = TRANSIENT.get(id);
            HeroEntry v = t != null ? t.entry() : visuals.get(id);
            HeroVisual visual = v != null ? v.visual() : fb.visual();
            boolean own = v != null && v.ownModel();
            HeroEntry.Source src = t != null ? HeroEntry.Source.TRANSIENT : HeroEntry.Source.BUILTIN;
            HeroDef d = r.loaded().hero(id);
            String name = d != null ? d.name() : v != null ? v.name() : HeroIdentity.nameFromId(id);
            String title = d != null && d.title() != null ? d.title() : "";
            out.put(id, new HeroEntry(id, name, title, visual, src, own, r.overridden().getOrDefault(id, List.of()), d));
        }
        heroes = Collections.unmodifiableMap(out);
        definitions = r.loaded();
    }

    private static Map<String, HeroEntry> heroMap() {
        loaded();
        return heroes;
    }

    private static Map<String, HeroEntry> loaded() {
        Map<String, HeroEntry> m = loaded;
        if (m == null) {
            reload();
            m = loaded;
        }
        return m;
    }

    /** The ids the mod ships a server definition for ({@code data/factionclash/heroes/<id>.json} in the mod file). */
    private static Set<String> shipped() {
        Set<String> s = shipped;
        if (s == null) {
            Set<String> ids = new TreeSet<>();
            Path dir = modPath("data", FactionClash.MODID, "heroes");
            if (dir != null && Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".json"))
                            .forEach(p -> ids.add(HeroReader.idFromFileName(p.getFileName().toString())));
                } catch (IOException e) {
                    FactionClash.LOGGER.warn("Heroes: the mod's definition folder could not be listed: {}", e.getMessage());
                }
            }
            s = Collections.unmodifiableSet(ids);
            shipped = s;
        }
        return s;
    }

    private static List<HeroReader.Source> transientDefinitions() {
        List<HeroReader.Source> out = new ArrayList<>();
        for (Transient t : TRANSIENT.values()) {
            if (t.definition() != null) {
                out.add(new HeroReader.Source("fixture/" + t.entry().id() + ".json", t.definition()));
            }
        }
        return out;
    }

    /** Writes {@code config/factionclash/heroes/_report.txt}: every problem of the last load, grouped per file (v1 26.4). */
    private static void writeReport() {
        Path dir = configDir();
        if (dir == null || net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null) {
            return; // a client reading its visual files has no definitions to report on
        }
        HeroReader.Loaded d = definitions;
        StringBuilder sb = new StringBuilder("Faction Clash hero definitions: what the last load reported.\n")
                .append("Built-in heroes come from data/").append(FactionClash.MODID).append("/heroes in the mod; a file here named <id>.json\n")
                .append("overrides that hero's top-level fields (never id, art, animations or format).\n\n")
                .append(d.problems().summary(d.heroes().keySet(), d.notLoaded())).append("\n\n");
        for (String p : problems) {
            sb.append(p).append('\n');
        }
        String r = d.problems().report();
        sb.append(r.isEmpty() ? "Nothing to report.\n" : r);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("_report.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            FactionClash.LOGGER.warn("Heroes: config/{}/heroes/_report.txt could not be written: {}", FactionClash.MODID, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Registers a hero for a test's duration from the text of a visual file (fixtures are drawn with the
     * {@code _fallback} model), with a minimal format-2 definition for its style ({@link #fixtureDefinition}). Closing
     * the handle removes it again. A fixture replaces a built-in hero of the same id while it is registered.
     */
    public static Transient registerTransient(String id, String visualJson) {
        JsonObject o = JsonParser.parseString(visualJson).getAsJsonObject();
        String style = o.has("style") ? o.get("style").getAsString() : "fists";
        String name = o.has("name") ? o.get("name").getAsString() : HeroIdentity.nameFromId(id);
        return registerTransient(id, visualJson, fixtureDefinition(id, name, style, ""));
    }

    /**
     * The same with the text of its definition ({@code null}: none, so the hero is dormant; a broken text is checked
     * and reported like a built-in one). Registering an id again replaces the earlier fixture.
     */
    public static synchronized Transient registerTransient(String id, String visualJson, String definitionJson) {
        if (!HeroIdentity.isHeroId(id)) {
            throw new IllegalArgumentException("not a hero id: " + id);
        }
        JsonObject o = JsonParser.parseString(visualJson).getAsJsonObject();
        HeroVisual v = HeroVisual.parse(id, o);
        String name = o.has("name") ? o.get("name").getAsString() : HeroIdentity.nameFromId(id);
        HeroEntry e = new HeroEntry(id, name, "", v, HeroEntry.Source.TRANSIENT, false, List.of());
        Transient t = new Transient(e, definitionJson);
        loaded();
        TRANSIENT.put(id, t);
        assemble();
        return t;
    }

    /**
     * A minimal format-2 definition for a test fixture of a weapon style: a striker (a marksman for {@code bow} and
     * {@code crossbow}) with that style's basic attack, one dash move, one phase and a weak point, and the fields a
     * built-in hero must have, so it loads with nothing to report. {@code more} adds top-level fields (",
     * \"revival\": {...}").
     */
    public static String fixtureDefinition(String id, String name, String style, String more) {
        String s = style == null ? "" : style.toLowerCase(java.util.Locale.ROOT);
        String basic = s.equals("bow") || s.equals("crossbow") ? s : "melee";
        String role = basic.equals("melee") ? "striker" : "marksman";
        return "{\"format\": 2, \"id\": \"" + id + "\", \"name\": \"" + name + "\", \"gender\": \"female\","
                + " \"archetype\": \"samurai\", \"behaviour\": {\"combat_role\": \"" + role + "\"}, \"basic_attack\":"
                + " {\"style\": \"" + basic + "\"}, \"moves\": [{\"id\": \"lunge\", \"name\": \"Lunge\", \"form\": \"dash\","
                + " \"effects\": [{\"type\": \"damage\", \"amount\": 2}]}], \"boss\": {\"weak_points\": [\"back\"],"
                + " \"phases\": [{\"at\": 100}]}, \"personality\": {\"temperament\": \"stoic\"}, \"leader\":"
                + " {\"eligible\": false}" + (more == null ? "" : more) + "}";
    }

    /** A registered fixture; {@link #close()} unregisters it (once, and only if it was not replaced since). */
    public record Transient(HeroEntry entry, String definition) implements AutoCloseable {
        @Override
        public void close() {
            synchronized (HeroRegistry.class) {
                if (TRANSIENT.remove(entry.id(), this)) {
                    assemble();
                }
            }
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

    /**
     * Operator overrides: {@code config/factionclash/heroes/<id>.json} (names starting with {@code _} or {@code .},
     * such as {@code _report.txt}, are never read). Their text is merged over the built-in definitions when the heroes
     * are assembled ({@link HeroOverride}); an override with no built-in hero is reported there.
     */
    private static List<HeroReader.Source> readOverrides(List<String> found) {
        Path dir = configDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".json") && !n.startsWith("_") && !n.startsWith(".") && Files.isRegularFile(p);
            }).sorted().toList();
        } catch (IOException e) {
            found.add("config/factionclash/heroes could not be listed: " + e.getMessage());
            return List.of();
        }
        List<HeroReader.Source> out = new ArrayList<>();
        for (Path p : files) {
            String file = "config/" + FactionClash.MODID + "/heroes/" + p.getFileName();
            try {
                if (Files.size(p) > HeroCatalogue.MAX_FILE_BYTES * 2L) {
                    found.add(file + ": larger than " + HeroCatalogue.MAX_FILE_BYTES / 1024 + " KB; ignored");
                    continue;
                }
                out.add(new HeroReader.Source(file, Files.readString(p, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                found.add(file + ": " + e.getMessage());
            }
        }
        return out;
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
