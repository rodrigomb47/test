package com.factionclash.logic.hero;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Java mirror of {@code tools/herogen/assets.py} (HEROES_V2 14.2, 15.2 row G7): the file-level asset rules on
 * what the mod ships, read from a repository root without Python. One method per Python function, in the same order,
 * with the same messages: {@code check_json}, {@code check_geometry} (the format version, then
 * {@code validate.check_geometry}, {@code _check_uvs}, {@code _check_clear_face} and {@code check_face_texture}),
 * {@code check_visual}, {@code check_chain}, {@code check_channels}, {@code check_face_keys},
 * {@code anim/quality.check_timing}, {@code check_codes} with {@code required_actions}, {@code check_lists} and
 * {@code check_frames}; plus the own-file animation budget of {@code quality.check_built(own_file=True)}, which Python
 * applies to the built clips. JSON is read as Python's {@code json} reads it (strict: no NaN, no comments; integers and
 * floats kept apart so messages print numbers as Python does), a PNG as Pillow's {@code convert("RGBA")}. The rig's
 * numbers ({@code herosrc/rig/rig_v3.json}) are constants here; {@code HeroAssetsTest} holds them to the rig file when
 * it is present. The action-code table is {@link HeroActions#actionName}, which Python reads from the Java source.
 * Used by {@code HeroAssetsTest} (and {@code ./gradlew verifyHeroAssets}).
 */
final class HeroAssetCheck {
    private HeroAssetCheck() {
    }

    static final String ASSETS = "src/main/resources/assets/factionclash";
    static final String DEF_DIR = "src/main/resources/data/factionclash/heroes";
    static final String SLICE_DIR = "src/test/resources/heroes/slice";
    static final String VISUAL_DIR = ASSETS + "/heroes";
    static final String CORE = "_core";
    static final double TICK = 0.05;
    static final int[] FACE_RANGES = {12, 8, 4};
    static final double LOOP_TOL = 0.01;
    static final int MAX_STATES = 3;
    static final String GEO_FORMAT = "1.12.0";
    static final Map<String, Integer> TRANSITION_TICKS = Map.of("base", 4, "action", 2);

    // ------------------------------------------------------------------ the rig (rig_v3.json) and face.py's layout

    static final int BUDGET_BONES = 100, BUDGET_CUBES = 300, BUDGET_LOD_CUBES = 130, BUDGET_OWN_ANIMATIONS = 24;
    static final List<List<Integer>> TEXTURES = List.of(List.of(256, 128), List.of(256, 256));
    static final List<String> REQUIRED_BONES = List.of("root", "body", "waist", "chest", "neck", "head", "face", "eyes",
            "mouth", "cheeks", "face_ctrl", "arm_r", "forearm_r", "hand_r", "item_r", "weapon_r", "arm_l", "forearm_l",
            "hand_l", "item_l", "weapon_l", "sash", "leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l",
            "vfx_tip_r", "vfx_tip_l", "vfx_chest", "vfx_head", "vfx_feet");
    /** {@code uv_template.head_front}: u, v, w, h. */
    static final List<Integer> HEAD_FRONT = List.of(144, 0, 20, 20);

    /** A face plane: its {@code uv_template} corner, frame size, grid columns and frame count. */
    record Plane(String name, int u, int v, int w, int h, int columns, int frames) {
        /** {@code face.frame_rect}: (u, v, w, h) of frame k. */
        int[] frame(int k) {
            return new int[]{u + (k % columns) * w, v + (k / columns) * h, w, h};
        }

        /** {@code check_visual}'s expected {@code face.regions} entry. */
        Map<String, Object> region() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("u", (long) u);
            m.put("v", (long) v);
            m.put("w", (long) w);
            m.put("h", (long) h);
            m.put("columns", (long) columns);
            m.put("frames", (long) frames);
            return m;
        }
    }

    static final Plane EYES = new Plane("eyes", 64, 0, 20, 10, 4, 12);
    static final Plane MOUTH = new Plane("mouth", 64, 30, 8, 3, 8, 8);
    static final Plane CHEEKS = new Plane("cheeks", 64, 33, 16, 2, 4, 4);
    static final List<Plane> PLANES = List.of(EYES, MOUTH, CHEEKS);
    static final double HEAD_FRONT_Z = -5, CLEAR_BELOW_Y = 5.5, CLEAR_HALF_WIDTH = 4.5, EYES_ORIGIN_Y = 1.5;
    /** face.py: the head front's size in texels, the upper lash row, and where the eyes and mouth planes sit on it. */
    static final int FACE = 20, LASH = 3;
    static final int[] EYES_AT = {7, 0}, MOUTH_AT = {17, 6};

    // ------------------------------------------------------------------ paths (build and anim/library out_paths)

    static Map<String, String> outPaths(String heroId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("geo", ASSETS + "/geo/hero/" + heroId + ".geo.json");
        m.put("texture", ASSETS + "/textures/entity/hero/" + heroId + ".png");
        m.put("glowmask", ASSETS + "/textures/entity/hero/" + heroId + "_glowmask.png");
        m.put("p2", ASSETS + "/textures/entity/hero/" + heroId + "_p2.png");
        m.put("p2_glowmask", ASSETS + "/textures/entity/hero/" + heroId + "_p2_glowmask.png");
        m.putAll(chainPaths(heroId));
        m.put("visual", VISUAL_DIR + "/" + heroId + ".visual.json");
        return m;
    }

    static Map<String, String> chainPaths(String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("animation", ASSETS + "/animations/hero/" + name + ".animation.json");
        m.put("timing", "src/main/resources/data/factionclash/hero_timings/" + name + ".json");
        return m;
    }

    // ------------------------------------------------------------------ inputs

    /** The ids of every {@code <id>.visual.json} under a root (every built-in hero's art, the fallback included). */
    static List<String> heroIds(Path root) throws IOException {
        Path d = root.resolve(VISUAL_DIR);
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(d)) {
            try (var s = Files.list(d)) {
                for (Path p : s.sorted().toList()) {
                    String n = p.getFileName().toString();
                    if (n.endsWith(".visual.json")) {
                        out.add(n.substring(0, n.length() - ".visual.json".length()));
                    }
                }
            }
        }
        return out;
    }

    /**
     * {@code committed_files}: path to bytes of what the mod ships for this hero, its outputs and the animation and
     * timing files of the chain its visual.json names (a missing file is absent; the chain rule reports it).
     */
    static Map<String, byte[]> committedFiles(Path root, String heroId) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        List<String> wanted = new ArrayList<>(outPaths(heroId).values());
        Path visual = root.resolve(outPaths(heroId).get("visual"));
        if (Files.exists(visual)) {
            Object chain = null;
            try {
                chain = get(Json.parse(Files.readAllBytes(visual)), "animation_chain");
            } catch (Json.Error e) {
                // check_json reports it
            }
            for (Object name : list(chain)) {
                if (name instanceof String s) {
                    wanted.addAll(chainPaths(s).values());
                }
            }
        }
        for (String p : wanted) {
            Path full = root.resolve(p);
            if (Files.exists(full)) {
                files.put(p, Files.readAllBytes(full));
            }
        }
        return files;
    }

    /** A hero's server definition and the file it came from. */
    record Definition(Map<String, Object> json, String path) {
    }

    /**
     * {@code definition_of}: the built-in definition {@code data/factionclash/heroes/<id>.json}, else the slice's
     * format-2 fixture (the slice's real kit until C1-C4 write the definitions); null when neither exists.
     */
    static Definition definitionOf(Path root, String heroId) throws IOException {
        for (String d : List.of(DEF_DIR, SLICE_DIR)) {
            String p = d + "/" + heroId + ".json";
            Path full = root.resolve(p);
            if (Files.exists(full)) {
                Map<String, Object> json = obj(Json.parse(Files.readAllBytes(full)));
                return new Definition(json, p);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the action code table (HeroActions)

    /** Code to name of every code {@link HeroActions#actionName} names ({@code state.N}, {@code move.N}: slots). */
    static Map<Integer, String> actionTable() {
        Map<Integer, String> t = new TreeMap<>();
        for (int c = 1; c < 256; c++) {
            String n = HeroActions.actionName(c);
            if (n != null) {
                t.put(c, n);
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ the chain

    /** One animation file of a chain as written, with its timing file (either null when missing or not JSON). */
    record Entry(String name, Map<String, Object> animation, Map<String, Object> timing) {
    }

    /** One look-up: the file that has the animation, its entry there and its timing entry (null if none). */
    record Found(String file, Map<String, Object> animation, Map<String, Object> timing) {
    }

    /** The animation files of a hero's chain as written, in look-up order (the hero's own, its style, the core). */
    record Chain(List<Entry> entries) {
        static Chain of(Map<String, Object> visual, Map<String, byte[]> files) {
            List<Entry> e = new ArrayList<>();
            for (Object name : list(get(visual, "animation_chain"))) {
                Map<String, String> p = chainPaths(pyStr(name));
                e.add(new Entry(pyStr(name), obj(maybeJson(files.get(p.get("animation")))),
                        obj(maybeJson(files.get(p.get("timing"))))));
            }
            return new Chain(e);
        }

        List<String> names() {
            return entries.stream().map(Entry::name).toList();
        }

        Found find(String anim) {
            for (Entry e : entries) {
                Map<String, Object> anims = obj(get(e.animation, "animations"));
                if (e.animation != null && anims != null && anims.containsKey(anim)) {
                    return new Found(e.name, obj(anims.get(anim)), obj(get(get(e.timing, "animations"), anim)));
                }
            }
            return null;
        }

        boolean has(String anim) {
            return find(anim) != null;
        }
    }

    static Object maybeJson(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return Json.parse(data);
        } catch (Json.Error e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the result

    record Result(List<String> problems, List<String> notes) {
    }

    /**
     * {@code check_files}: every file-level rule on one hero's files (path to bytes: its outputs and its chain's
     * animation and timing files) and its definition (null for none).
     */
    static Result checkFiles(String heroId, Map<String, byte[]> files, Map<String, Object> definition) {
        Map<Integer, String> table = actionTable();
        List<String> problems = new ArrayList<>(checkJson(files));
        Map<String, String> paths = outPaths(heroId);
        Map<String, Object> visual = obj(maybeJson(files.get(paths.get("visual"))));
        if (visual == null) {
            problems.add(paths.get("visual") + ": missing or not JSON");
            return new Result(problems, List.of());
        }
        Geometry geo = checkGeometry(heroId, files, visual, problems);
        problems.addAll(checkVisual(heroId, files, visual, geo));
        problems.addAll(checkChain(heroId, visual, files));
        Chain chain = Chain.of(visual, files);
        for (Entry e : chain.entries()) {
            if (e.animation() != null) {
                problems.addAll(checkChannels(e.name(), e.animation()));
                problems.addAll(checkFaceKeys(e.name(), e.animation()));
            }
        }
        problems.addAll(checkOwnBudget(heroId, chain));
        problems.addAll(checkTiming(chain));
        problems.addAll(checkCodes(visual, definition, chain, table));
        Result lists = checkLists(visual, definition, chain, table);
        problems.addAll(lists.problems());
        problems.addAll(checkFrames(definition, chain));
        return new Result(problems, lists.notes());
    }

    /** {@code check_committed}: {@link #checkFiles} on the committed files and the hero's server definition. */
    static Result checkCommitted(Path root, String heroId) throws IOException {
        Definition d;
        try {
            d = definitionOf(root, heroId);
        } catch (Json.Error e) {
            return new Result(List.of("the definition of " + heroId + " is not valid JSON (" + e.getMessage() + ")"),
                    List.of());
        }
        return checkFiles(heroId, committedFiles(root, heroId), d == null ? null : d.json());
    }

    // ------------------------------------------------------------------ check_json

    static List<String> checkJson(Map<String, byte[]> files) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
            if (e.getKey().endsWith(".json")) {
                try {
                    Json.parse(e.getValue());
                } catch (Json.Error x) {
                    out.add(e.getKey() + ": not valid JSON (" + x.getMessage() + ")");
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ geometry (geo.GeoModel)

    /** One cube: its bone, box, rotation and pivot (null when not rotated) and per-face UVs as written. */
    record Cube(String bone, double[] origin, double[] size, double[] rotation, double[] pivot,
                Map<String, double[]> rawUv) {
        /** {@code from_file_uv}: the net rectangle (u, v, w, h); the bottom face is written flipped in v. */
        double[] uv(String face) {
            double[] r = rawUv.get(face);
            return face.equals("down") ? new double[]{r[0], r[1] + r[3], r[2], -r[3]} : r.clone();
        }
    }

    /** A parsed {@code .geo.json}: bones in file order (duplicates kept in {@code order}), cubes in order. */
    record Geometry(double texW, double texH, List<String> order, Map<String, Map<String, Object>> bones,
                    List<Cube> cubes) {
        static Geometry of(Map<String, Object> data) {
            Map<String, Object> geo = obj(list(data.get("minecraft:geometry")).get(0));
            Map<String, Object> desc = obj(require(geo, "description"));
            double tw = num(require(desc, "texture_width")), th = num(require(desc, "texture_height"));
            List<String> order = new ArrayList<>();
            Map<String, Map<String, Object>> bones = new LinkedHashMap<>();
            List<Cube> cubes = new ArrayList<>();
            for (Object b0 : list(require(geo, "bones"))) {
                Map<String, Object> b = obj(b0);
                String name = (String) require(b, "name");
                bones.put(name, b);
                order.add(name);
                for (Object c0 : list(b.get("cubes"))) {
                    Map<String, Object> c = obj(c0);
                    Map<String, Object> uv = obj(c.get("uv"));
                    if (uv == null) {
                        throw new IllegalArgumentException("bone " + name + ": a cube without per-face uv");
                    }
                    Map<String, double[]> raw = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> f : uv.entrySet()) {
                        double[] at = vec(require(obj(f.getValue()), "uv"));
                        double[] sz = vec(require(obj(f.getValue()), "uv_size"));
                        raw.put(f.getKey(), new double[]{at[0], at[1], sz[0], sz[1]});
                    }
                    cubes.add(new Cube(name, vec(require(c, "origin")), vec(require(c, "size")),
                            c.containsKey("rotation") ? vec(c.get("rotation")) : null,
                            c.containsKey("pivot") ? vec(c.get("pivot")) : null, raw));
                }
            }
            return new Geometry(tw, th, order, bones, cubes);
        }

        String parent(String name) {
            Object p = bones.get(name).get("parent");
            return p == null ? null : pyStr(p);
        }
    }

    private static Object require(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) {
            throw new IllegalArgumentException("'" + key + "'");
        }
        return m.get(key);
    }

    private static double[] vec(Object o) {
        List<Object> l = list(o);
        if (o == null || l.isEmpty() && !(o instanceof List)) {
            throw new IllegalArgumentException("not a list: " + pyRepr(o));
        }
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = num(l.get(i));
        }
        return v;
    }

    /** An RGBA image as Pillow's {@code convert("RGBA")} gives it: rows of RGBA bytes. */
    record Texture(int w, int h, int[] rgba) {
        int at(int x, int y, int c) {
            return rgba[(y * w + x) * 4 + c];
        }

        /** The RGBA texels of a rectangle, row by row (null when it leaves the image). */
        int[][] rect(int u, int v, int rw, int rh) {
            if (u < 0 || v < 0 || u + rw > w || v + rh > h) {
                return null;
            }
            int[][] out = new int[rh * rw][];
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    int i = ((v + y) * w + u + x) * 4;
                    out[y * rw + x] = Arrays.copyOfRange(rgba, i, i + 4);
                }
            }
            return out;
        }

        static Texture read(byte[] png) throws IOException {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                throw new IOException("cannot identify image file");
            }
            int w = img.getWidth(), h = img.getHeight();
            int[] out = new int[w * h * 4];
            Raster r = img.getRaster();
            int bands = r.getNumBands();
            boolean direct = img.getColorModel() instanceof ComponentColorModel
                    && r.getSampleModel().getSampleSize(0) <= 16;
            int bits = r.getSampleModel().getSampleSize(0);
            int[] px = new int[bands];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 4;
                    if (direct) {
                        // the samples as stored: Pillow keeps a grey level as R = G = B and 16-bit samples' high byte
                        r.getPixel(x, y, px);
                        int shift = bits > 8 ? bits - 8 : 0;
                        if (bands >= 3) {
                            out[i] = px[0] >> shift;
                            out[i + 1] = px[1] >> shift;
                            out[i + 2] = px[2] >> shift;
                            out[i + 3] = bands >= 4 ? px[3] >> shift : 255;
                        } else {
                            out[i] = out[i + 1] = out[i + 2] = px[0] >> shift;
                            out[i + 3] = bands == 2 ? px[1] >> shift : 255;
                        }
                    } else {
                        int argb = img.getRGB(x, y);
                        out[i] = argb >> 16 & 0xFF;
                        out[i + 1] = argb >> 8 & 0xFF;
                        out[i + 2] = argb & 0xFF;
                        out[i + 3] = argb >>> 24;
                    }
                }
            }
            return new Texture(w, h, out);
        }
    }

    /**
     * {@code check_geometry}: the geometry and texture must exist, the format version is 1.12.0, then
     * {@code validate.check_built}. Problems go to {@code out}; returns the parsed geometry or null.
     */
    static Geometry checkGeometry(String heroId, Map<String, byte[]> files, Map<String, Object> visual,
                                  List<String> out) {
        Map<String, String> paths = outPaths(heroId);
        for (String key : List.of("geo", "texture")) {
            if (!files.containsKey(paths.get(key))) {
                out.add(paths.get(key) + ": missing");
                return null;
            }
        }
        Map<String, Object> data;
        try {
            data = obj(Json.parse(files.get(paths.get("geo"))));
        } catch (Json.Error e) {
            return null; // check_json reports it
        }
        if (data == null || !GEO_FORMAT.equals(data.get("format_version"))) {
            out.add(paths.get("geo") + ": format_version " + pyRepr(get(data, "format_version"))
                    + ", GeckoLib's geometry is " + GEO_FORMAT);
            return null;
        }
        Geometry geo;
        Texture tex;
        try {
            geo = Geometry.of(data);
            tex = Texture.read(files.get(paths.get("texture")));
        } catch (RuntimeException | IOException e) {
            out.add("geometry or texture unreadable: " + e.getMessage());
            return null;
        }
        boolean asym = truthy(get(get(visual, "face"), "asymmetric"));
        out.addAll(checkGeometryRules(geo, tex));
        out.addAll(checkFaceTexture(tex, asym));
        return geo;
    }

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern MASK_BONE = Pattern.compile("^(?:p[23]_)?(?:fine_)?mask$");

    /** {@code validate.check_geometry}: sizes, budgets, bone names, the required bones, UVs and the clear face. */
    static List<String> checkGeometryRules(Geometry geo, Texture tex) {
        List<String> out = new ArrayList<>();
        if (geo.texW() != tex.w() || geo.texH() != tex.h()) {
            out.add(String.format("texture is %dx%d but the geometry says %dx%d", tex.w(), tex.h(), (long) geo.texW(),
                    (long) geo.texH()));
        }
        boolean allowed = false;
        for (List<Integer> t : TEXTURES) {
            allowed |= t.get(0) == geo.texW() && t.get(1) == geo.texH();
        }
        if (!allowed) {
            out.add(String.format("texture size %dx%d is not allowed (256x128, 256x256)", (long) geo.texW(),
                    (long) geo.texH()));
        }
        if (geo.order().size() > BUDGET_BONES) {
            out.add(geo.order().size() + " bones (budget " + BUDGET_BONES + ")");
        }
        if (geo.cubes().size() > BUDGET_CUBES) {
            out.add(geo.cubes().size() + " cubes (budget " + BUDGET_CUBES + ")");
        }
        int lod = lodCubes(geo);
        if (lod > BUDGET_LOD_CUBES) {
            out.add(lod + " cubes outside fine_ bones (budget " + BUDGET_LOD_CUBES + "): move detail (strands, frills,"
                    + " trims) onto fine_ bones");
        }
        Set<String> seen = new HashSet<>();
        for (String name : geo.order()) {
            if (!NAME.matcher(name).find()) {
                out.add("bone name " + pyRepr(name) + " is not lower_snake_case");
            }
            String parent = geo.parent(name);
            if (parent != null && !seen.contains(parent)) {
                out.add("bone " + name + " comes before its parent " + parent);
            }
            seen.add(name);
        }
        if (seen.size() != geo.order().size()) {
            out.add("duplicate bone names");
        }
        for (String req : REQUIRED_BONES) {
            if (!seen.contains(req)) {
                out.add("required bone " + req + " is missing");
            }
        }
        out.addAll(checkUvs(geo));
        out.addAll(checkClearFace(geo));
        return out;
    }

    /** {@code validate.lod_cubes}: cubes still drawn when every fine_ bone (and everything under it) is hidden. */
    static int lodCubes(Geometry geo) {
        Set<String> fine = new HashSet<>();
        for (String name : geo.order()) {
            String parent = geo.parent(name);
            if (name.startsWith("fine_") || parent != null && fine.contains(parent)) {
                fine.add(name);
            }
        }
        int n = 0;
        for (Cube c : geo.cubes()) {
            if (!fine.contains(c.bone())) {
                n++;
            }
        }
        return n;
    }

    private static boolean whole(double v) {
        return v == Math.floor(v);
    }

    /** {@code validate._check_uvs}. */
    static List<String> checkUvs(Geometry geo) {
        List<String> out = new ArrayList<>();
        Map<String, int[]> expected = new LinkedHashMap<>();
        for (Plane p : PLANES) {
            expected.put(p.name(), new int[]{p.u(), p.v(), p.w(), p.h()});
        }
        int tw = (int) geo.texW(), th = (int) geo.texH();
        int[] occ = new int[Math.max(0, tw) * Math.max(0, th)];
        List<String> owner = new ArrayList<>();
        owner.add("?");
        for (Cube c : geo.cubes()) {
            for (String face : new TreeSet<>(c.rawUv().keySet())) {
                double[] raw = c.rawUv().get(face);
                if (!(whole(raw[0]) && whole(raw[1]) && whole(raw[2]) && whole(raw[3]))) {
                    out.add("bone " + c.bone() + ": " + face + " face has a fractional UV "
                            + pyRepr(List.of(pyNumber(raw[0]), pyNumber(raw[1]))) + " "
                            + pyRepr(List.of(pyNumber(raw[2]), pyNumber(raw[3]))));
                    continue;
                }
                double[] r = c.uv(face);
                int u = (int) r[0], v = (int) r[1], w = (int) r[2], h = (int) r[3];
                if (w <= 0 || h <= 0) {
                    out.add("bone " + c.bone() + ": " + face + " face has an empty or mirrored UV rectangle");
                    continue;
                }
                if (u < 0 || v < 0 || u + w > geo.texW() || v + h > geo.texH()) {
                    out.add(String.format("bone %s: %s face UV (%d, %d, %d, %d) leaves the texture", c.bone(), face, u,
                            v, w, h));
                    continue;
                }
                if (expected.containsKey(c.bone())) {
                    int[] e = expected.get(c.bone());
                    if (!face.equals("north") || !Arrays.equals(new int[]{u, v, w, h}, e)) {
                        out.add("face plane " + c.bone() + " is not on its fixed region (" + e[0] + ", " + e[1] + ", "
                                + e[2] + ", " + e[3] + ")");
                    }
                    continue;
                }
                if (c.bone().equals("head") && face.equals("north")) {
                    if (!List.of(u, v, w, h).equals(HEAD_FRONT)) {
                        out.add("head front is not on its fixed region " + HEAD_FRONT);
                    }
                    continue;
                }
                int first = 0;
                for (int y = v; y < v + h && first == 0; y++) {
                    for (int x = u; x < u + w; x++) {
                        if (occ[y * tw + x] != 0) {
                            first = occ[y * tw + x];
                            break;
                        }
                    }
                }
                if (first != 0) {
                    out.add("bone " + c.bone() + ": " + face + " face UV overlaps " + owner.get(first));
                }
                int key = owner.size();
                owner.add(c.bone() + "/" + face);
                for (int y = v; y < v + h; y++) {
                    for (int x = u; x < u + w; x++) {
                        if (occ[y * tw + x] == 0) {
                            occ[y * tw + x] = key;
                        }
                    }
                }
            }
        }
        for (Plane p : PLANES) {
            if (geo.cubes().stream().noneMatch(c -> c.bone().equals(p.name()))) {
                out.add("face plane " + p.name() + " has no cube");
            }
        }
        return out;
    }

    /** {@code rot.file_matrix}: Rz(-rz) . Ry(ry) . Rx(-rx), the rotation a file rotation applies in file space. */
    static double[][] fileMatrix(double[] a) {
        return mul(mul(rz(-a[2]), ry(a[1])), rx(-a[0]));
    }

    private static double[][] rx(double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    private static double[][] ry(double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    private static double[][] rz(double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    m[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return m;
    }

    /** {@code validate._face_points}: the corners of one face of a cube after its own rotation, file coordinates. */
    static List<double[]> facePoints(Cube c, String face) {
        double x = c.origin()[0], y = c.origin()[1], z = c.origin()[2];
        double x1 = x + c.size()[0], y1 = y + c.size()[1], z1 = z + c.size()[2];
        List<double[]> pts = new ArrayList<>();
        double[] xs = {x, x1}, ys = {y, y1}, zs = {z, z1};
        boolean side = face.equals("east") || face.equals("west"), front = face.equals("north") || face.equals("south");
        for (double p : side ? ys : xs) {
            for (double q : front ? ys : zs) {
                pts.add(switch (face) {
                    case "north" -> new double[]{p, q, z};
                    case "south" -> new double[]{p, q, z1};
                    case "east" -> new double[]{x, p, q};
                    case "west" -> new double[]{x1, p, q};
                    case "up" -> new double[]{p, y1, q};
                    case "down" -> new double[]{p, y, q};
                    default -> throw new IllegalArgumentException("unknown face " + face);
                });
            }
        }
        if (c.rotation() == null) {
            return pts;
        }
        double[][] m = fileMatrix(c.rotation());
        double[] pv = c.pivot() != null ? c.pivot() : new double[3];
        List<double[]> out = new ArrayList<>();
        for (double[] p : pts) {
            double[] d = {p[0] - pv[0], p[1] - pv[1], p[2] - pv[2]};
            double[] r = new double[3];
            for (int i = 0; i < 3; i++) {
                r[i] = pv[i] + m[i][0] * d[0] + m[i][1] * d[1] + m[i][2] * d[2];
            }
            out.add(r);
        }
        return out;
    }

    /**
     * {@code validate._check_clear_face}: no face of any cube (the face planes and the head aside) in front of the
     * face between the chin and the lash line within |x| < 4.5; a face cover on a {@code mask} bone may cover the
     * mouth, never the eyes.
     */
    static List<String> checkClearFace(Geometry geo) {
        List<String> out = new ArrayList<>();
        Map<String, Object> head = geo.bones().get("head");
        if (head == null) {
            return out; // the required-bone rule reports it
        }
        double headY = vec(head.get("pivot"))[1];
        double front = HEAD_FRONT_Z, limY = headY + CLEAR_BELOW_Y, half = CLEAR_HALF_WIDTH;
        double eyesBottom = headY + EYES_ORIGIN_Y;
        Set<String> flagged = new HashSet<>();
        for (Cube c : geo.cubes()) {
            if (Set.of("eyes", "mouth", "cheeks", "head").contains(c.bone())) {
                continue;
            }
            boolean cover = MASK_BONE.matcher(c.bone()).find();
            for (String face : c.rawUv().keySet()) {
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                for (double[] p : facePoints(c, face)) {
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                    minZ = Math.min(minZ, p[2]);
                }
                if (cover && maxY <= eyesBottom + 1e-6) {
                    continue;
                }
                if (minZ < front - 1e-6 && minY < limY - 1e-6 && maxY > headY + 1e-6 && minX < half - 1e-6
                        && maxX > -half + 1e-6 && !flagged.contains(c.bone())) {
                    flagged.add(c.bone());
                    out.add("bone " + c.bone() + ": a cube sits in front of the face below y " + pyG(limY)
                            + " within |x| < " + pyG(half));
                }
            }
        }
        return out;
    }

    /** {@code face.head_front_holes}: the transparent texels of the head front (row, column). */
    static boolean hole(int row, int col) {
        if (row >= EYES_AT[0] && row < EYES_AT[0] + EYES.h()) {
            return true;
        }
        return row >= MOUTH_AT[0] && row < MOUTH_AT[0] + MOUTH.h() && col >= MOUTH_AT[1] + 1
                && col < MOUTH_AT[1] + MOUTH.w() - 1;
    }

    /** {@code validate.check_face_texture}. */
    static List<String> checkFaceTexture(Texture tex, boolean asymmetric) {
        List<String> out = new ArrayList<>();
        int hu = HEAD_FRONT.get(0), hv = HEAD_FRONT.get(1), hw = HEAD_FRONT.get(2), hh = HEAD_FRONT.get(3);
        int needW = Math.max(hu + hw, EYES.u() + EYES.columns() * EYES.w());
        int needH = Math.max(hv + hh, CHEEKS.v() + CHEEKS.h());
        if (tex.w() < needW || tex.h() < needH) {
            out.add("texture " + tex.w() + "x" + tex.h() + " is too small for the face layout");
            return out;
        }
        boolean shown = false, solid = false;
        for (int r = 0; r < hh; r++) {
            for (int c = 0; c < hw; c++) {
                int a = tex.at(hu + c, hv + r, 3);
                if (hole(r, c) ? a != 0 : a != 255) {
                    if (hole(r, c)) {
                        shown = true;
                    } else {
                        solid = true;
                    }
                }
            }
        }
        if (shown) {
            out.add("head front is not transparent under the eyes and mouth planes");
        }
        if (solid) {
            out.add("head front has transparent texels outside the plane holes");
        }
        for (Object[] pl : new Object[][]{{EYES, EYES_AT}, {MOUTH, MOUTH_AT}}) {
            Plane p = (Plane) pl[0];
            int[] at = (int[]) pl[1];
            for (int k = 0; k < p.frames(); k++) {
                int[] fr = p.frame(k);
                int[][] f = tex.rect(fr[0], fr[1], fr[2], fr[3]);
                int[][] under = tex.rect(hu + at[1], hv + at[0], fr[2], fr[3]);
                boolean any = false, same = true;
                for (int i = 0; i < f.length; i++) {
                    if (under[i][3] == 255) {
                        any = true;
                        same &= Arrays.equals(f[i], under[i]);
                    }
                }
                if (any && !same) {
                    out.add(p.name() + " frame " + k + " differs from the head front where they overlap (edge texels"
                            + " must be the same skin)");
                }
            }
        }
        for (Plane p : PLANES) {
            boolean opaque = p != CHEEKS;
            List<int[][]> frames = new ArrayList<>();
            for (int k = 0; k < p.frames(); k++) {
                int[] fr = p.frame(k);
                int[][] f = tex.rect(fr[0], fr[1], fr[2], fr[3]);
                frames.add(f);
                boolean notOpaque = false, anyAlpha = false;
                for (int[] t : f) {
                    notOpaque |= t[3] != 255;
                    anyAlpha |= t[3] != 0;
                }
                if (opaque && notOpaque) {
                    out.add(p.name() + " frame " + k + " is not fully opaque (missing frame?)");
                }
                if (!opaque && k > 0 && !anyAlpha) {
                    out.add(p.name() + " frame " + k + " is empty");
                }
            }
            if (!opaque) {
                boolean anyAlpha = false;
                for (int[] t : frames.get(0)) {
                    anyAlpha |= t[3] != 0;
                }
                if (anyAlpha) {
                    out.add("cheeks frame 0 must be transparent");
                }
            }
            for (int i = 0; i < frames.size(); i++) {
                for (int j = i + 1; j < frames.size(); j++) {
                    if (Arrays.deepEquals(frames.get(i), frames.get(j))) {
                        out.add(p.name() + " frames " + i + " and " + j + " are identical");
                    }
                }
            }
            if (p == EYES && !asymmetric) {
                for (int k = 0; k < frames.size(); k++) {
                    int[][] f = frames.get(k);
                    boolean mirrors = true;
                    for (int r = k == 9 ? LASH : 0; r < p.h(); r++) {
                        for (int c = 0; c < p.w(); c++) {
                            mirrors &= Arrays.equals(f[r * p.w() + c], f[r * p.w() + p.w() - 1 - c]);
                        }
                    }
                    if (!mirrors) {
                        out.add("eyes frame " + k + ": the eyes do not mirror");
                    }
                }
            }
            if (p == EYES) {
                int[] skin = frames.get(0)[(p.h() - 1) * p.w()];
                for (int k = 0; k < frames.size(); k++) {
                    boolean blank = true;
                    for (int[] t : frames.get(k)) {
                        blank &= t[0] == skin[0] && t[1] == skin[1] && t[2] == skin[2];
                    }
                    if (blank) {
                        out.add("eyes frame " + k + " is blank skin (missing frame?)");
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ check_visual, check_chain

    static List<String> checkVisual(String heroId, Map<String, byte[]> files, Map<String, Object> visual,
                                    Geometry geo) {
        List<String> out = new ArrayList<>();
        Map<String, String> paths = outPaths(heroId);
        Object tex = visual.get("texture");
        Object tw = get(tex, "width"), th = get(tex, "height");
        if (geo != null && !(pyEq(tw, pyNumber(geo.texW())) && pyEq(th, pyNumber(geo.texH())))) {
            out.add("visual.json texture " + pyStr(tw) + "x" + pyStr(th) + ", the geometry " + (long) geo.texW() + "x"
                    + (long) geo.texH());
        }
        if (truthy(get(tex, "glowmask")) && !files.containsKey(paths.get("glowmask"))) {
            out.add("visual.json names a glow mask, " + paths.get("glowmask") + " is missing");
        }
        boolean wantsP2 = truthy(get(tex, "p2"));
        for (Object ph : list(visual.get("phases"))) {
            wantsP2 |= "p2".equals(get(ph, "texture"));
        }
        if (wantsP2 && !files.containsKey(paths.get("p2"))) {
            out.add("visual.json names a phase texture, " + paths.get("p2") + " is missing");
        }
        Object regions = get(get(visual, "face"), "regions");
        for (Plane p : PLANES) {
            Object have = get(regions, p.name());
            if (!pyEq(have, p.region())) {
                out.add("visual.json face.regions." + p.name() + " is " + pyStr(have) + ", the rig's is "
                        + pyStr(p.region()));
            }
        }
        return out;
    }

    static List<String> checkChain(String heroId, Map<String, Object> visual, Map<String, byte[]> files) {
        List<String> out = new ArrayList<>();
        Object chain = visual.get("animation_chain");
        Object style = visual.get("style");
        List<Object> want = new ArrayList<>();
        want.add(heroId);
        want.add(truthy(style) ? "_style_" + pyStr(style) : null);
        want.add(CORE);
        if (!pyEq(chain, want)) {
            out.add("visual.json animation_chain is " + pyStr(chain) + ", expected " + pyStr(want)
                    + " (the hero's file, its style library, the core)");
        }
        for (Object name : list(chain)) {
            for (Map.Entry<String, String> e : new TreeMap<>(chainPaths(pyStr(name))).entrySet()) {
                if (!files.containsKey(e.getValue())) {
                    out.add(e.getValue() + ": missing (" + e.getKey() + " file of the chain; GeckoLib needs every file"
                            + " of it)");
                }
            }
        }
        return out;
    }

    /** {@code quality.check_built(own_file=True)}'s budget, on the written file: at most 24 animations of its own. */
    static List<String> checkOwnBudget(String heroId, Chain chain) {
        for (Entry e : chain.entries()) {
            Map<String, Object> anims = obj(get(e.animation(), "animations"));
            if (e.name().equals(heroId) && anims != null && anims.size() > BUDGET_OWN_ANIMATIONS) {
                return List.of(e.name() + ": " + anims.size() + " animations in the hero's own file (at most "
                        + BUDGET_OWN_ANIMATIONS + ")");
            }
        }
        return List.of();
    }

    // ------------------------------------------------------------------ check_channels, check_face_keys

    /** {@code _value}: the value a written key holds after its time (a vector or a jump's post). */
    static Object value(Object key) {
        if (key instanceof Map<?, ?> m) {
            if (m.containsKey("post")) {
                return m.get("post");
            }
            if (m.containsKey("vector")) {
                return m.get("vector");
            }
            return null;
        }
        return key;
    }

    static boolean close(Object a, Object b) {
        if (!(a instanceof List<?> la && b instanceof List<?> lb) || la.size() != lb.size()) {
            return pyEq(a, b);
        }
        for (int i = 0; i < la.size(); i++) {
            Object x = la.get(i), y = lb.get(i);
            if (x instanceof Number nx && y instanceof Number ny) {
                if (Math.abs(nx.doubleValue() - ny.doubleValue()) > LOOP_TOL) {
                    return false;
                }
            } else if (!pyEq(x, y)) {
                return false;
            }
        }
        return true;
    }

    static List<String> checkChannels(String fileName, Map<String, Object> animJson) {
        List<String> out = new ArrayList<>();
        Map<String, Object> anims = obj(get(animJson, "animations"));
        if (anims == null) {
            return out;
        }
        for (Map.Entry<String, Object> an : new TreeMap<>(anims).entrySet()) {
            Map<String, Object> a = obj(an.getValue());
            double length = num(a.getOrDefault("animation_length", 0L));
            boolean looping = Boolean.TRUE.equals(a.get("loop"));
            Map<String, Object> bones = obj(a.get("bones"));
            if (bones == null) {
                continue;
            }
            for (Map.Entry<String, Object> b : new TreeMap<>(bones).entrySet()) {
                Map<String, Object> chans = obj(b.getValue());
                if (chans == null) {
                    continue;
                }
                for (Map.Entry<String, Object> ch : new TreeMap<>(chans).entrySet()) {
                    String where = fileName + ": " + an.getKey() + " " + b.getKey() + "." + ch.getKey();
                    Map<String, Object> keys = obj(ch.getValue());
                    if (keys == null || keys.isEmpty()) {
                        out.add(where + ": no keys");
                        continue;
                    }
                    List<Double> times = new ArrayList<>();
                    for (String ts : keys.keySet()) {
                        try {
                            times.add(Double.parseDouble(ts.strip()));
                        } catch (NumberFormatException e) {
                            out.add(where + ": key time " + pyRepr(ts) + " is not a number");
                            times = null;
                            break;
                        }
                    }
                    if (times == null) {
                        continue;
                    }
                    for (int i = 1; i < times.size(); i++) {
                        if (times.get(i) <= times.get(i - 1)) {
                            out.add(where + ": key times do not ascend in file order");
                            break;
                        }
                    }
                    if (times.get(0) > 1e-9) {
                        out.add(where + ": the first key is at " + pyFloat(times.get(0)) + " s, not 0");
                    }
                    double last = times.get(times.size() - 1);
                    if (last > length + 1e-6) {
                        out.add(where + ": a key at " + pyFloat(last) + " s is after the end (" + pyFloat(length)
                                + " s)");
                    }
                    if (looping) {
                        List<Object> vals = new ArrayList<>(keys.values());
                        if (Math.abs(last - length) > 1e-6) {
                            out.add(where + ": a loop channel without a key at the end");
                        } else if (!close(value(vals.get(0)), value(vals.get(vals.size() - 1)))) {
                            out.add(where + ": the loop does not close (" + pyStr(value(vals.get(0))) + " at 0, "
                                    + pyStr(value(vals.get(vals.size() - 1))) + " at the end)");
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<Long> rounded(Object v) {
        List<Long> out = new ArrayList<>();
        for (Object x : list(v)) {
            out.add(x instanceof Number n ? (long) Math.rint(n.doubleValue()) : Long.MIN_VALUE);
        }
        return out;
    }

    static List<String> checkFaceKeys(String fileName, Map<String, Object> animJson) {
        List<String> out = new ArrayList<>();
        Map<String, Object> anims = obj(get(animJson, "animations"));
        if (anims == null) {
            return out;
        }
        for (Map.Entry<String, Object> an : new TreeMap<>(anims).entrySet()) {
            Map<String, Object> bones = obj(get(get(an.getValue(), "bones"), "face_ctrl"));
            if (bones == null || bones.isEmpty()) {
                continue;
            }
            String where = fileName + ": " + an.getKey() + " face_ctrl";
            for (String ch : new TreeSet<>(bones.keySet())) {
                if (!ch.equals("position")) {
                    out.add(where + ": keys " + ch + " (only position carries face frames)");
                }
            }
            Object prev = null;
            Map<String, Object> position = obj(bones.get("position"));
            for (Map.Entry<String, Object> k : (position == null ? Map.<String, Object>of() : position).entrySet()) {
                Object val = value(k.getValue());
                List<Object> l = val instanceof List<?> ? list(val) : null;
                if (l == null || l.size() != 3 || !l.stream().allMatch(x -> x instanceof Number)) {
                    out.add(where + " at " + k.getKey() + ": " + pyRepr(val) + " is not three numbers");
                    break;
                }
                for (int i = 0; i < 3; i++) {
                    double v = num(l.get(i));
                    double r = Math.rint(v);
                    if (Math.abs(v - r) > 1e-6 || !(0 <= r && r <= FACE_RANGES[i])) {
                        out.add(where + " at " + k.getKey() + ": " + pyStr(val) + " is not whole frame numbers in range"
                                + " (eyes 0-12, mouth 0-8, cheeks 0-4)");
                        break;
                    }
                }
                if (prev != null && !rounded(val).equals(rounded(prev))) {
                    Object pre = k.getValue() instanceof Map<?, ?> m ? m.get("pre") : null;
                    if (pre == null || !rounded(pre).equals(rounded(prev))) {
                        out.add(where + " at " + k.getKey() + ": changes " + pyStr(prev) + " -> " + pyStr(val)
                                + " without a jump (pre/post): it would slide through other frames");
                    }
                }
                prev = val;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ anim/quality.check_timing

    /** A marker: its time (rounded to 4 decimals as Python's {@code round}) and label. */
    record Mark(double t, String label) implements Comparable<Mark> {
        public int compareTo(Mark o) {
            int c = Double.compare(t, o.t);
            return c != 0 ? c : label.compareTo(o.label);
        }

        public String toString() {
            return "(" + pyFloat(t) + ", " + pyRepr(label) + ")";
        }
    }

    static List<String> checkTiming(Chain chain) {
        List<String> out = new ArrayList<>();
        for (Entry e : chain.entries()) {
            if (e.animation() == null || e.timing() == null) {
                continue; // check_chain / check_json report it
            }
            for (String p : checkTimingPair(e.animation(), e.timing())) {
                out.add(e.name() + ": " + p);
            }
        }
        return out;
    }

    /** {@code quality.check_timing}: the timing file agrees with the animation file's markers. */
    static List<String> checkTimingPair(Map<String, Object> animJson, Map<String, Object> timingJson) {
        List<String> out = new ArrayList<>();
        Map<String, Object> anims = orEmpty(obj(animJson.get("animations")));
        Map<String, Object> timed = orEmpty(obj(timingJson.get("animations")));
        if (!new TreeSet<>(anims.keySet()).equals(new TreeSet<>(timed.keySet()))) {
            out.add("timing file lists " + pyRepr(new ArrayList<Object>(new TreeSet<>(timed.keySet())))
                    + ", the animation file " + pyRepr(new ArrayList<Object>(new TreeSet<>(anims.keySet()))));
        }
        for (Map.Entry<String, Object> an : new TreeMap<>(anims).entrySet()) {
            String name = an.getKey();
            Map<String, Object> a = orEmpty(obj(an.getValue()));
            Map<String, Object> t = obj(timed.get(name));
            if (t == null) {
                continue;
            }
            Object len = a.getOrDefault("animation_length", 0L);
            if (Math.abs(num(len) - num(t.get("length"))) > TICK / 2) {
                out.add(name + ": length " + pyStr(len) + " in the animation, " + pyStr(t.get("length"))
                        + " in the timing file");
            }
            List<Mark> marks = new ArrayList<>();
            for (Map.Entry<String, Object> tl : orEmpty(obj(a.get("timeline"))).entrySet()) {
                for (String part : pyStr(tl.getValue()).split(";", -1)) {
                    if (!part.isEmpty()) {
                        marks.add(new Mark(round4(Double.parseDouble(tl.getKey().strip())), part));
                    }
                }
            }
            for (Map.Entry<String, Object> s : orEmpty(obj(a.get("sound_effects"))).entrySet()) {
                marks.add(new Mark(round4(Double.parseDouble(s.getKey().strip())), "sfx:" + pyStr(get(s.getValue(),
                        "effect"))));
            }
            for (Map.Entry<String, Object> p : orEmpty(obj(a.get("particle_effects"))).entrySet()) {
                marks.add(new Mark(round4(Double.parseDouble(p.getKey().strip())), "particle:" + pyStr(get(p.getValue(),
                        "effect"))));
            }
            List<Mark> timedMarks = new ArrayList<>();
            for (Object ev : list(t.getOrDefault("events", List.of()))) {
                Object type = get(ev, "type"), val = get(ev, "value");
                String label = !truthy(val) ? pyStr(type) : pyStr(type) + ":" + pyStr(val);
                double et = num(get(ev, "t"));
                timedMarks.add(new Mark(round4(et), label));
                if (Math.abs(num(get(ev, "tick")) * TICK - et) > 1e-6) {
                    out.add(name + ": event " + label + " tick " + (long) num(get(ev, "tick"))
                            + " does not match its time " + pyFixed(et, 3));
                }
            }
            List<Mark> sm = new ArrayList<>(marks), st = new ArrayList<>(timedMarks);
            sm.sort(null);
            st.sort(null);
            if (!sm.equals(st)) {
                out.add(name + ": markers " + sm + " in the animation, " + st + " in the timing file");
            }
            Object kind = t.getOrDefault("kind", "base");
            Object blend = t.get("transition_ticks");
            Integer want = kind instanceof String k ? TRANSITION_TICKS.get(k) : null;
            if (!pyEq(blend, want == null ? null : (Object) (long) want)) {
                out.add(name + ": transition_ticks " + pyStr(blend) + ", the " + pyStr(kind) + " controller blends "
                        + (want == null ? "None" : want) + " ticks");
                continue;
            }
            if (!pyEq(t.get("total_ticks"), pyNumber(num(t.getOrDefault("ticks", -1L)) + num(blend)))) {
                out.add(name + ": total_ticks " + pyStr(t.get("total_ticks")) + " is not ticks + transition_ticks");
            }
            List<Double> hits = new ArrayList<>();
            for (Mark m : marks) {
                if (m.label().equals("hit") || m.label().equals("cast")) {
                    hits.add(m.t());
                }
            }
            hits.sort(null);
            Object fr = t.get("frames");
            if (!hits.isEmpty()) {
                if (!truthy(fr)) {
                    out.add(name + ": hit markers but no frames in the timing file");
                } else if (Math.abs(num(get(fr, "windup")) - hits.get(0)) > TICK + 1e-9) {
                    out.add(name + ": wind-up " + pyFixed(num(get(fr, "windup")), 2) + " s in the timing file,"
                            + " first hit at " + pyFixed(hits.get(0), 2) + " s");
                } else if (Math.abs(num(get(fr, "windup")) + num(get(fr, "active")) + num(get(fr, "recovery"))
                        - num(t.get("length")) - num(blend) * TICK) > 1e-6) {
                    out.add(name + ": frames do not add up to the blend and the length");
                }
            } else if (truthy(fr)) {
                out.add(name + ": frames in the timing file but no hit markers");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ codes, lists, frames

    private static List<Map<String, Object>> defMoves(Map<String, Object> definition) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object m : list(get(definition, "moves"))) {
            if (m instanceof Map<?, ?> && truthy(get(m, "id"))) {
                out.add(obj(m));
            }
        }
        return out;
    }

    private static List<Object> defStates(Map<String, Object> definition) {
        List<Object> out = new ArrayList<>();
        for (Object s : list(get(definition, "states"))) {
            if (s instanceof Map<?, ?>) {
                out.add(get(s, "name"));
            }
        }
        return out;
    }

    private static int phases(Map<String, Object> definition) {
        return list(get(get(definition, "boss"), "phases")).size();
    }

    private static Object bare(Object name, String prefix) {
        return name instanceof String s && s.startsWith(prefix) ? s.substring(prefix.length()) : name;
    }

    /** One action the server can send: its code, the animation name it resolves to and why it is required. */
    record Required(int code, String name, String why) {
    }

    /** {@code required_actions} (HEROES_V2 appendix D). */
    static List<Required> requiredActions(Map<String, Object> visual, Map<String, Object> definition, Chain chain,
                                          Map<Integer, String> table) {
        List<Required> out = new ArrayList<>();
        Map<String, Object> basic = orEmpty(obj(get(definition, "basic_attack")));
        boolean ranged = truthy(definition) && basic.get("style") != null && !"melee".equals(basic.get("style"));
        Map<String, Object> ult = obj(get(definition, "ultimate"));
        List<Object> states = list(visual.get("states"));
        List<Object> moves = list(visual.get("moves"));
        for (Map.Entry<Integer, String> e : new TreeMap<>(table).entrySet()) {
            int code = e.getKey();
            String name = e.getValue();
            if (name.startsWith("state.")) {
                int i = Integer.parseInt(name.substring(6)) - 1;
                if (i < states.size()) {
                    out.add(new Required(code, "state." + pyStr(bare(states.get(i), "state.")),
                            "state slot " + (i + 1)));
                }
            } else if (name.startsWith("move.") && name.substring(5).chars().allMatch(Character::isDigit)) {
                int i = Integer.parseInt(name.substring(5));
                if (i < moves.size()) {
                    out.add(new Required(code, "move." + pyStr(bare(moves.get(i), "move.")), "move slot " + i));
                }
            } else if (name.equals("attack.ranged")) {
                if (ranged) {
                    out.add(new Required(code, name, "a ranged basic attack (" + pyStr(basic.get("style")) + ")"));
                }
            } else if (name.equals("phase.2") || name.equals("phase.3")) {
                if (phases(definition) >= name.charAt(name.length() - 1) - '0') {
                    out.add(new Required(code, name, phases(definition) + " boss phases"));
                }
            } else if (name.equals("ultimate")) {
                if (truthy(ult)) {
                    out.add(new Required(code, name, "the ultimate " + pyStr(ult.get("id"))));
                }
            } else if (name.equals("ultimate.awakened")) {
                if (truthy(ult) && ult.get("awakened") instanceof Map<?, ?>) {
                    out.add(new Required(code, name, "the awakened ultimate " + pyStr(get(ult.get("awakened"), "id"))));
                }
            } else if (name.equals("ultimate.end")) {
                if (chain.has("ultimate.loop")) {
                    out.add(new Required(code, name, "the chain has ultimate.loop"));
                }
            } else {
                out.add(new Required(code, name, "every hero"));
            }
        }
        return out;
    }

    static List<String> checkCodes(Map<String, Object> visual, Map<String, Object> definition, Chain chain,
                                   Map<Integer, String> table) {
        List<String> out = new ArrayList<>();
        for (Required r : requiredActions(visual, definition, chain, table)) {
            if (!chain.has(r.name())) {
                out.add("action code " + r.code() + " (" + r.name() + ", " + r.why() + ") does not resolve through "
                        + String.join(", ", chain.names()));
            }
        }
        return out;
    }

    /** {@code check_lists}: visual.json moves and states against the server definition (problems, notes). */
    static Result checkLists(Map<String, Object> visual, Map<String, Object> definition, Chain chain,
                             Map<Integer, String> table) {
        List<String> out = new ArrayList<>(), notes = new ArrayList<>();
        List<Object> moves = list(visual.get("moves"));
        List<Object> states = list(visual.get("states"));
        int slots = HeroActions.MOVE_SLOTS;
        if (moves.size() > slots) {
            out.add("visual.json lists " + moves.size() + " moves; the action codes have " + slots + " move slots");
        }
        if (states.size() > MAX_STATES) {
            out.add("visual.json lists " + states.size() + " states; the action codes have " + MAX_STATES
                    + " state slots");
        }
        Set<String> distinct = new HashSet<>();
        for (Object m : moves) {
            distinct.add(pyRepr(m));
        }
        if (distinct.size() != moves.size()) {
            out.add("visual.json lists a move twice: " + pyRepr(moves));
        }
        if (definition == null) {
            return new Result(out, notes);
        }
        List<Object> bareMoves = new ArrayList<>();
        for (Object m : moves) {
            bareMoves.add(bare(m, "move."));
        }
        List<Map<String, Object>> dmoves = defMoves(definition);
        List<Object> withClip = new ArrayList<>(), ids = new ArrayList<>();
        for (Map<String, Object> m : dmoves) {
            ids.add(m.get("id"));
            if (chain.has("move." + pyStr(m.get("id")))) {
                withClip.add(m.get("id"));
            }
        }
        if (!pyEq(bareMoves, withClip)) {
            out.add("visual.json moves " + pyRepr(bareMoves) + ", the definition's moves " + pyRepr(ids) + " (its"
                    + " order; a move may be left out only when the chain has no move.<id> for it)");
        }
        for (Map<String, Object> m : dmoves) {
            String id = pyStr(m.get("id"));
            if (chain.has("move." + id)) {
                continue;
            }
            if (truthy(m.get("frames")) || truthy(generated(definition, id, false))) {
                out.add("move " + id + ": the definition gives frames but the chain has no move." + id);
            } else {
                notes.add("move " + id + " has no clip (move." + id + "): the server plays its look's telegraph pose");
            }
        }
        List<Object> bareStates = new ArrayList<>();
        for (Object s : states) {
            bareStates.add(bare(s, "state."));
        }
        List<Object> dstates = defStates(definition);
        if (!pyEq(bareStates, dstates)) {
            out.add("visual.json states " + pyRepr(states) + ", the definition's states " + pyRepr(dstates));
        }
        return new Result(out, notes);
    }

    private static Object generated(Map<String, Object> definition, String moveId, boolean ultimate) {
        Object gen = get(definition, "generated");
        if (ultimate) {
            Object u = get(gen, "ultimate");
            return u instanceof Map<?, ?> ? get(u, "frames") : null;
        }
        for (Object g : list(get(gen, "moves"))) {
            if (g instanceof Map<?, ?> && pyEq(get(g, "id"), moveId)) {
                return get(g, "frames");
            }
        }
        return null;
    }

    static long ticks(Object seconds) {
        return (long) Math.rint(num(seconds) / TICK);
    }

    /** {@code check_frames}: each move's clip against the definition's frames, within one tick (4.8, 14.2). */
    static List<String> checkFrames(Map<String, Object> definition, Chain chain) {
        List<String> out = new ArrayList<>();
        record Item(String anim, Object authored, Object generated, String what) {
        }
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> m : defMoves(definition)) {
            String id = pyStr(m.get("id"));
            items.add(new Item("move." + id, m.get("frames"), generated(definition, id, false), "move " + id));
        }
        Object ult = get(definition, "ultimate");
        if (ult instanceof Map<?, ?>) {
            items.add(new Item("ultimate", get(ult, "frames"), generated(definition, null, true), "the ultimate"));
            Object aw = get(ult, "awakened");
            if (aw instanceof Map<?, ?>) {
                items.add(new Item("ultimate.awakened", get(aw, "frames"), null, "the awakened ultimate"));
            }
        }
        for (Item it : items) {
            Found found = chain.find(it.anim());
            if (found == null) {
                continue; // lists / codes report a missing clip
            }
            Map<String, Object> ft = obj(get(found.timing(), "frame_ticks"));
            for (Object[] lf : new Object[][]{{"frames", it.authored()}, {"generated frames", it.generated()}}) {
                String label = (String) lf[0];
                if (!(lf[1] instanceof Map<?, ?> fr)) {
                    continue;
                }
                if (!truthy(ft)) {
                    out.add(it.what() + ": " + it.anim() + " (" + found.file() + ") has no hit or cast marker; the"
                            + " definition's " + label + " are " + pyRepr(fr));
                    continue;
                }
                for (String k : List.of("windup", "active", "recovery")) {
                    if (!fr.containsKey(k)) {
                        continue;
                    }
                    long want = ticks(fr.get(k));
                    double have = ft.containsKey(k) ? num(ft.get(k)) : -99;
                    if (Math.abs(have - want) > 1) {
                        long shown = ft.containsKey(k) ? (long) num(ft.get(k)) : -1;
                        String hit = k.equals("windup") ? ", its first hit or cast at tick " + shown + " of the action"
                                : "";
                        out.add(it.what() + ": " + k + " " + shown + " ticks in " + found.file() + " (" + it.anim()
                                + hit + "), " + want + " in the definition's " + label + " ("
                                + pyFixed(num(fr.get(k)), 2)
                                + " s): more than one tick apart");
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Python's values: access, truth, equality, repr

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static Map<String, Object> orEmpty(Map<String, Object> m) {
        return m == null ? Map.of() : m;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static Object get(Object o, String key) {
        return o instanceof Map<?, ?> m ? m.get(key) : null;
    }

    static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        throw new IllegalArgumentException("not a number: " + pyRepr(o));
    }

    /** A double as Python would hold it after JSON: an int when the value is whole and was meant as one. */
    static Object pyNumber(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object) (long) d : (Object) d;
    }

    static boolean truthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (o instanceof String s) {
            return !s.isEmpty();
        }
        if (o instanceof List<?> l) {
            return !l.isEmpty();
        }
        if (o instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
    }

    /** Python's {@code ==} on JSON values (1 == 1.0, lists and dicts element by element). */
    static boolean pyEq(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!pyEq(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            if (!x.keySet().equals(y.keySet())) {
                return false;
            }
            for (Object k : x.keySet()) {
                if (!pyEq(x.get(k), y.get(k))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    /** Python's {@code str()}: a string as itself, anything else as its repr. */
    static String pyStr(Object o) {
        return o instanceof String s ? s : pyRepr(o);
    }

    /** Python's {@code repr()} of a JSON value. */
    static String pyRepr(Object o) {
        if (o == null) {
            return "None";
        }
        if (o instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (o instanceof Long || o instanceof Integer) {
            return o.toString();
        }
        if (o instanceof Number n) {
            return pyFloat(n.doubleValue());
        }
        if (o instanceof String s) {
            boolean dq = s.contains("'") && !s.contains("\"");
            char q = dq ? '"' : '\'';
            StringBuilder b = new StringBuilder().append(q);
            for (char c : s.toCharArray()) {
                if (c == '\\') {
                    b.append("\\\\");
                } else if (c == q) {
                    b.append('\\').append(c);
                } else if (c == '\n') {
                    b.append("\\n");
                } else if (c == '\t') {
                    b.append("\\t");
                } else if (c == '\r') {
                    b.append("\\r");
                } else if (c < 0x20 || c == 0x7F) {
                    b.append(String.format("\\x%02x", (int) c));
                } else {
                    b.append(c);
                }
            }
            return b.append(q).toString();
        }
        if (o instanceof List<?> l) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                b.append(i > 0 ? ", " : "").append(pyRepr(l.get(i)));
            }
            return b.append(']').toString();
        }
        if (o instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(first ? "" : ", ").append(pyRepr(e.getKey())).append(": ").append(pyRepr(e.getValue()));
                first = false;
            }
            return b.append('}').toString();
        }
        return o.toString();
    }

    /** Python's {@code repr(float)}: the shortest digits, fixed from 1e-4 up to 1e16, else 1e-05 style. */
    static String pyFloat(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == 0) {
            return 1 / d < 0 ? "-0.0" : "0.0";
        }
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        String digits = bd.unscaledValue().toString();
        int exp = digits.length() - 1 - bd.scale();
        String sign = d < 0 ? "-" : "";
        if (exp >= -4 && exp < 16) {
            String s = bd.toPlainString();
            return sign + (s.contains(".") ? s : s + ".0");
        }
        String mant = digits.length() > 1 ? digits.charAt(0) + "." + digits.substring(1) : digits;
        return sign + mant + "e" + (exp < 0 ? "-" : "+") + (Math.abs(exp) < 10 ? "0" : "") + Math.abs(exp);
    }

    /** Python's {@code "%.Nf"}: the exact binary value rounded half to even. */
    static String pyFixed(double d, int places) {
        return new BigDecimal(d).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Python's {@code "%g"} for the plain numbers the messages print. */
    static String pyG(double d) {
        BigDecimal bd = new BigDecimal(d).round(new java.math.MathContext(6, RoundingMode.HALF_EVEN))
                .stripTrailingZeros();
        String s = bd.toPlainString();
        return s.equals("-0") ? "-0" : s;
    }

    /** Python's {@code round(x, 4)}: correctly rounded on the exact binary value. */
    static double round4(double d) {
        return new BigDecimal(d).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    // ------------------------------------------------------------------ JSON as Python's json module reads it

    /**
     * A strict JSON reader that gives what Python's {@code json.loads(..., parse_constant=refuse)} gives: objects in
     * file order (a repeated key keeps its first place and its last value), integers as {@link Long}, other numbers as
     * {@link Double}; UTF-8 only, no byte-order mark, no NaN or Infinity, no comments, no trailing commas, no control
     * characters in strings.
     */
    static final class Json {
        private final String s;
        private int i;

        static final class Error extends RuntimeException {
            Error(String m) {
                super(m);
            }
        }

        private Json(String s) {
            this.s = s;
        }

        static Object parse(byte[] data) {
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
                return parse(text);
            } catch (CharacterCodingException e) {
                throw new Error("not UTF-8: " + e.getMessage());
            }
        }

        static Object parse(String text) {
            Json p = new Json(text);
            p.ws();
            Object v = p.value(0);
            p.ws();
            if (p.i != p.s.length()) {
                throw p.error("Extra data");
            }
            return v;
        }

        private Error error(String what) {
            int line = 1, col = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new Error(what + ": line " + line + " column " + col + " (char " + i + ")");
        }

        private void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                i++;
            }
        }

        private Object value(int depth) {
            if (depth > 1000) {
                throw error("nested too deep");
            }
            if (i >= s.length()) {
                throw error("Expecting value");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw error("Expecting property name enclosed in double quotes");
                }
                String k = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw error("Expecting ':' delimiter");
                }
                i++;
                ws();
                m.put(k, value(depth + 1));
                ws();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                if (i >= s.length() || s.charAt(i) != ',') {
                    throw error("Expecting ',' delimiter");
                }
                i++;
            }
        }

        private List<Object> array(int depth) {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                ws();
                l.add(value(depth + 1));
                ws();
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                if (i >= s.length() || s.charAt(i) != ',') {
                    throw error("Expecting ',' delimiter");
                }
                i++;
            }
        }

        private String string() {
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error("Unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c < 0x20) {
                    i--;
                    throw error("Invalid control character");
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw error("Unterminated string");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length() || !s.substring(i, i + 4).matches("[0-9a-fA-F]{4}")) {
                            throw error("Invalid \\uXXXX escape");
                        }
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> {
                        i--;
                        throw error("Invalid \\escape");
                    }
                }
            }
        }

        private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(\\.[0-9]+)?([eE][-+]?[0-9]+)?");

        private Object number() {
            var m = NUMBER.matcher(s).region(i, s.length());
            if (!m.lookingAt()) {
                throw error("Expecting value");
            }
            String t = m.group();
            i = m.end();
            if (m.group(1) == null && m.group(2) == null) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(t);
                }
            }
            return Double.parseDouble(t);
        }

        private Object word(String w, Object v) {
            if (!s.startsWith(w, i)) {
                throw error("Expecting value");
            }
            i += w.length();
            return v;
        }

        // -------------------------------------------------------------- writing (for the tests' edited copies)

        /** Writes a value as JSON (numbers as Python's json.dumps writes them). */
        static String write(Object o) {
            StringBuilder b = new StringBuilder();
            write(o, b);
            return b.toString();
        }

        private static void write(Object o, StringBuilder b) {
            if (o == null) {
                b.append("null");
            } else if (o instanceof Boolean x) {
                b.append(x ? "true" : "false");
            } else if (o instanceof Long || o instanceof Integer) {
                b.append(o);
            } else if (o instanceof Number n) {
                b.append(pyFloat(n.doubleValue()));
            } else if (o instanceof String str) {
                b.append('"');
                for (char c : str.toCharArray()) {
                    switch (c) {
                        case '"' -> b.append("\\\"");
                        case '\\' -> b.append("\\\\");
                        case '\n' -> b.append("\\n");
                        case '\r' -> b.append("\\r");
                        case '\t' -> b.append("\\t");
                        default -> {
                            if (c < 0x20 || c > 0x7E) {
                                b.append(String.format("\\u%04x", (int) c));
                            } else {
                                b.append(c);
                            }
                        }
                    }
                }
                b.append('"');
            } else if (o instanceof List<?> l) {
                b.append('[');
                for (int k = 0; k < l.size(); k++) {
                    if (k > 0) {
                        b.append(", ");
                    }
                    write(l.get(k), b);
                }
                b.append(']');
            } else if (o instanceof Map<?, ?> m) {
                b.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        b.append(", ");
                    }
                    first = false;
                    write(String.valueOf(e.getKey()), b);
                    b.append(": ");
                    write(e.getValue(), b);
                }
                b.append('}');
            } else {
                throw new IllegalArgumentException("not a JSON value: " + o);
            }
        }
    }

    /** Sorted copy helper for messages. */
    static <T> List<T> sorted(java.util.Collection<T> c, Comparator<? super T> cmp) {
        List<T> l = new ArrayList<>(c);
        l.sort(cmp);
        return l;
    }
}
