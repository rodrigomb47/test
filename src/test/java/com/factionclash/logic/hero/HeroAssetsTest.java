package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroAssetCheck.Chain;
import com.factionclash.logic.hero.HeroAssetCheck.Entry;
import com.factionclash.logic.hero.HeroAssetCheck.Json;
import com.factionclash.logic.hero.HeroAssetCheck.Required;
import com.factionclash.logic.hero.HeroAssetCheck.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.factionclash.logic.hero.HeroAssetCheck.get;
import static com.factionclash.logic.hero.HeroAssetCheck.list;
import static com.factionclash.logic.hero.HeroAssetCheck.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HEROES_V2 14.2 and 14.3, 15.2 row G7: the asset checks over {@code src/main/resources} without Python
 * ({@link HeroAssetCheck}, the Java mirror of {@code tools/herogen/assets.py}). Every built-in hero's geometry,
 * textures, {@code visual.json}, animation and timing files exist and agree; SLICE_PLAN 6.3's three negative tests (a
 * marker moved by 3 ticks, a model over the cube budget, a missing eye frame) and the other breaks each fail, on
 * temporary copies, never on the committed files; the rules one by one; the eight slice kits against the style
 * libraries as written; and, where python3 with NumPy and Pillow is installed, {@code herogen}'s own check gives the
 * same messages on the same files.
 */
class HeroAssetsTest {
    static final Path ROOT = Path.of("").toAbsolutePath();
    static final String RIG = "test_rig";
    static final Map<String, String> SLICE_STYLES = Map.of("natsuho_komezaki", "fists", "yugen_kagaribi", "focus",
            "benika_akagane", "hammer", "hinae_shirasuzu", "focus", "kagenori_tsurumine", "katana",
            "tobimaru_usugumo", "twin_blades", "ilsabet_frostmere", "sword_shield", "ambrose_nettlewick", "staff");

    // ------------------------------------------------------------------ helpers

    /** A temporary root with what the check reads for one hero: its committed files and its server definition. */
    static Path copyRoot(Path tmp, String heroId) throws IOException {
        List<String> paths = new ArrayList<>(HeroAssetCheck.committedFiles(ROOT, heroId).keySet());
        HeroAssetCheck.Definition d = HeroAssetCheck.definitionOf(ROOT, heroId);
        if (d != null) {
            paths.add(d.path());
        }
        for (String p : paths) {
            Path to = tmp.resolve(p);
            Files.createDirectories(to.getParent());
            Files.copy(ROOT.resolve(p), to);
        }
        return tmp;
    }

    static void editJson(Path root, String path, Consumer<Map<String, Object>> fn) throws IOException {
        Path full = root.resolve(path);
        Map<String, Object> data = obj(Json.parse(Files.readAllBytes(full)));
        fn.accept(data);
        Files.writeString(full, Json.write(data), StandardCharsets.UTF_8);
    }

    static HeroAssetCheck.Texture readTexture(Path root, String heroId) throws IOException {
        return HeroAssetCheck.Texture.read(Files.readAllBytes(root.resolve(HeroAssetCheck.outPaths(heroId)
                .get("texture"))));
    }

    static void writeTexture(Path root, String heroId, HeroAssetCheck.Texture t) throws IOException {
        BufferedImage img = new BufferedImage(t.w(), t.h(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < t.h(); y++) {
            for (int x = 0; x < t.w(); x++) {
                img.setRGB(x, y, t.at(x, y, 3) << 24 | t.at(x, y, 0) << 16 | t.at(x, y, 1) << 8 | t.at(x, y, 2));
            }
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        Files.write(root.resolve(HeroAssetCheck.outPaths(heroId).get("texture")), b.toByteArray());
    }

    /** Sets texels (x0..x1-1, y0..y1-1) of a texture to one RGBA value. */
    static void paint(HeroAssetCheck.Texture t, int x0, int y0, int x1, int y1, int[] rgba) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                System.arraycopy(rgba, 0, t.rgba(), (y * t.w() + x) * 4, 4);
            }
        }
    }

    static List<String> problems(Path root) throws IOException {
        return HeroAssetCheck.checkCommitted(root, RIG).problems();
    }

    static boolean anyContains(List<String> l, String part) {
        return l.stream().anyMatch(m -> m.contains(part));
    }

    private static final Map<String, Entry> LIBRARIES = new TreeMap<>();

    /** A committed animation file and its timing file, as a chain entry. */
    static synchronized Entry library(String name) throws IOException {
        if (!LIBRARIES.containsKey(name)) {
            Map<String, String> p = HeroAssetCheck.chainPaths(name);
            LIBRARIES.put(name, new Entry(name, obj(Json.parse(Files.readAllBytes(ROOT.resolve(p.get("animation"))))),
                    obj(Json.parse(Files.readAllBytes(ROOT.resolve(p.get("timing")))))));
        }
        return LIBRARIES.get(name);
    }

    /** The real style library and core as written, under a synthetic own file (name to animation and timing). */
    static Chain libChain(String style, Map<String, Object[]> own) throws IOException {
        Map<String, Object> anims = new LinkedHashMap<>(), timings = new LinkedHashMap<>();
        own.forEach((n, v) -> {
            anims.put(n, v[0]);
            timings.put(n, v[1]);
        });
        return new Chain(List.of(new Entry("t_hero", Map.of("animations", anims), Map.of("animations", timings)),
                library("_style_" + style), library(HeroAssetCheck.CORE)));
    }

    static Object[] empty() {
        return new Object[]{Map.of(), Map.of()};
    }

    /** A minimal written animation and its timing entry with these frames (seconds, from the action's start). */
    static Object[] clipWithFrames(Map<String, Object> frames) {
        Map<String, Object> ft = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, Object> e : frames.entrySet()) {
            long t = HeroAssetCheck.ticks(e.getValue());
            ft.put(e.getKey(), t);
            total += t;
        }
        double length = Math.round((total * HeroAssetCheck.TICK - 0.1) * 1000) / 1000.0;
        Map<String, Object> anim = new LinkedHashMap<>(Map.of("animation_length", length, "bones", Map.of()));
        Map<String, Object> timing = new LinkedHashMap<>();
        timing.put("length", length);
        timing.put("ticks", total - 2);
        timing.put("kind", "action");
        timing.put("transition_ticks", 2L);
        timing.put("total_ticks", total);
        timing.put("frames", new LinkedHashMap<>(frames));
        timing.put("frame_ticks", ft);
        timing.put("events", List.of());
        return new Object[]{anim, timing};
    }

    static Map<String, Object> frames(double windup, double active, double recovery) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("windup", windup);
        m.put("active", active);
        m.put("recovery", recovery);
        return m;
    }

    static Map<String, Object> parse(String json) {
        return obj(Json.parse(json));
    }

    static Map<String, Object> visual(List<?> moves, List<?> states) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("moves", new ArrayList<>(moves));
        v.put("states", new ArrayList<>(states));
        return v;
    }

    // ------------------------------------------------------------------ the committed heroes

    @Test
    void everyBuiltInHeroPasses() throws IOException {
        List<String> ids = HeroAssetCheck.heroIds(ROOT);
        assertTrue(ids.contains(RIG) && ids.contains("_fallback"), ids.toString());
        for (String id : ids) {
            Result r = HeroAssetCheck.checkCommitted(ROOT, id);
            assertEquals(List.of(), r.problems(), id + ": " + String.join("\n", r.problems()));
            // the one NOTE the rules give: a move whose definition gives no frames and the chain no clip (G7 notes)
            for (String n : r.notes()) {
                assertTrue(n.contains(" has no clip (move."), id + ": " + n);
            }
        }
    }

    @Test
    void theDefinitionIsTheBuiltInOneElseTheSliceFixture() throws IOException {
        assertEquals(HeroAssetCheck.DEF_DIR + "/test_rig.json", HeroAssetCheck.definitionOf(ROOT, RIG).path());
        assertNull(HeroAssetCheck.definitionOf(ROOT, "_fallback"));
        for (String id : SLICE_STYLES.keySet()) {
            String want = Files.exists(ROOT.resolve(HeroAssetCheck.DEF_DIR + "/" + id + ".json"))
                    ? HeroAssetCheck.DEF_DIR : HeroAssetCheck.SLICE_DIR;
            assertEquals(want + "/" + id + ".json", HeroAssetCheck.definitionOf(ROOT, id).path());
        }
    }

    @Test
    void theActionTableIsHeroActions() {
        Map<Integer, String> t = HeroAssetCheck.actionTable();
        assertEquals("attack.1", t.get(1));
        assertEquals("attack.ranged", t.get(4));
        assertEquals("phase.2", t.get(17));
        assertEquals("phase.3", t.get(18));
        assertEquals("state.1", t.get(24));
        assertEquals("work.mine", t.get(32));
        assertEquals("emote.greet", t.get(48));
        assertEquals("move.0", t.get(64));
        assertEquals("move.11", t.get(75));
        assertEquals("ultimate", t.get(76));
        assertEquals("ultimate.end", t.get(77));
        assertEquals("ultimate.awakened", t.get(78));
        assertFalse(t.containsKey(0) || t.containsKey(27) || t.containsKey(79));
    }

    /** The rig's numbers the checker holds as constants are the rig file's (face.check_layout's rule, in Java). */
    @Test
    void theRigConstantsAreTheRigFiles() throws IOException {
        Path rigFile = ROOT.resolve("herosrc/rig/rig_v3.json");
        assumeTrue(Files.exists(rigFile), "no herosrc/rig/rig_v3.json in this checkout");
        Map<String, Object> rig = obj(Json.parse(Files.readAllBytes(rigFile)));
        Map<String, Object> budgets = obj(rig.get("budgets"));
        assertEquals(HeroAssetCheck.BUDGET_BONES, HeroAssetCheck.num(budgets.get("bones")));
        assertEquals(HeroAssetCheck.BUDGET_CUBES, HeroAssetCheck.num(budgets.get("cubes")));
        assertEquals(HeroAssetCheck.BUDGET_LOD_CUBES, HeroAssetCheck.num(budgets.get("lod_cubes")));
        assertEquals(HeroAssetCheck.BUDGET_OWN_ANIMATIONS, HeroAssetCheck.num(budgets.get("own_animations")));
        assertTrue(HeroAssetCheck.pyEq(budgets.get("textures"), HeroAssetCheck.TEXTURES.stream()
                .map(l -> l.stream().map(Integer::longValue).toList()).toList()));
        List<String> required = new ArrayList<>();
        for (Object b : list(rig.get("bones"))) {
            if (HeroAssetCheck.truthy(get(b, "required"))) {
                required.add((String) get(b, "name"));
            }
        }
        assertEquals(HeroAssetCheck.REQUIRED_BONES, required);
        Map<String, Object> tpl = obj(rig.get("uv_template"));
        assertTrue(HeroAssetCheck.pyEq(tpl.get("head_front"), List.of(144L, 0L, 20L, 20L)));
        Map<String, Object> face = obj(rig.get("face"));
        for (HeroAssetCheck.Plane p : HeroAssetCheck.PLANES) {
            Map<String, Object> plane = obj(get(face.get("planes"), p.name()));
            assertTrue(HeroAssetCheck.pyEq(list(tpl.get(p.name())).subList(0, 2), List.of((long) p.u(), (long) p.v())),
                    p.name());
            assertTrue(HeroAssetCheck.pyEq(plane.get("frame"), List.of((long) p.w(), (long) p.h())), p.name());
            assertEquals(p.columns(), HeroAssetCheck.num(list(plane.get("grid")).get(0)), p.name());
            assertEquals(p.frames(), HeroAssetCheck.num(plane.get("frames")), p.name());
            // where the plane sits on the head front (face.check_layout)
            double size = HeroAssetCheck.num(get(rig.get("head"), "size")), d = HeroAssetCheck.num(face.get("density"));
            List<Object> o = list(plane.get("origin")), s = list(plane.get("size"));
            int row = (int) Math.rint((size - HeroAssetCheck.num(o.get(1)) - HeroAssetCheck.num(s.get(1))) * d);
            int col = (int) Math.rint((HeroAssetCheck.num(o.get(0)) + size / 2) * d);
            if (p == HeroAssetCheck.EYES) {
                assertEquals(List.of(HeroAssetCheck.EYES_AT[0], HeroAssetCheck.EYES_AT[1]), List.of(row, col));
                assertEquals(HeroAssetCheck.EYES_ORIGIN_Y, HeroAssetCheck.num(o.get(1)));
            } else if (p == HeroAssetCheck.MOUTH) {
                assertEquals(List.of(HeroAssetCheck.MOUTH_AT[0], HeroAssetCheck.MOUTH_AT[1]), List.of(row, col));
            }
        }
        assertEquals(HeroAssetCheck.FACE, HeroAssetCheck.num(get(rig.get("head"), "size")) * HeroAssetCheck.num(
                face.get("density")));
        assertEquals(HeroAssetCheck.LASH, HeroAssetCheck.num(get(face.get("eye_rows"), "lash")));
        assertEquals(HeroAssetCheck.CLEAR_BELOW_Y, HeroAssetCheck.num(face.get("clear_below_y")));
        assertEquals(HeroAssetCheck.CLEAR_HALF_WIDTH, HeroAssetCheck.num(face.get("clear_half_width")));
        assertEquals(HeroAssetCheck.HEAD_FRONT_Z, HeroAssetCheck.num(get(rig.get("head"), "front_z")));
    }

    // ------------------------------------------------------------------ SLICE_PLAN 6.3's negative tests and more

    @Test
    void aCleanCopyPasses(@TempDir Path tmp) throws IOException {
        assertEquals(List.of(), problems(copyRoot(tmp, RIG)));
    }

    @Test
    void aMarkerMovedByThreeTicksFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.chainPaths("_style_katana").get("animation"), d -> {
            Map<String, Object> tl = obj(get(get(d.get("animations"), "attack.1"), "timeline"));
            tl.put("0.65", tl.remove("0.5")); // the hit 3 ticks late; the timing file still says tick 10
        });
        List<String> p = problems(root);
        assertTrue(anyContains(p, "_style_katana: attack.1: markers [(0.4, 'sfx:minecraft:entity.player.attack.sweep'),"
                + " (0.65, 'hit'), (0.65, 'vfx:slash_arc')] in the animation, [(0.4, 'sfx:minecraft:entity.player"
                + ".attack.sweep'), (0.5, 'hit'), (0.5, 'vfx:slash_arc')] in the timing file"), String.join("\n", p));
        assertTrue(anyContains(p, "_style_katana: attack.1: wind-up 0.50 s in the timing file, first hit at 0.65 s"),
                String.join("\n", p));
    }

    @Test
    void aTimingFileMovedByThreeTicksFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.chainPaths("_style_katana").get("timing"), d -> {
            Map<String, Object> a = obj(get(d.get("animations"), "attack.1"));
            obj(a.get("frames")).put("windup", 0.65);
            obj(a.get("frame_ticks")).put("windup", 13L);
        });
        List<String> p = problems(root);
        assertTrue(anyContains(p, "attack.1: wind-up 0.65 s in the timing file, first hit at 0.50 s"),
                String.join("\n", p));
    }

    /** The kit half of "hit markers equal the server frames": a move's clip against its definition's frames. */
    @Test
    void aDefinitionThreeTicksAfterItsClipFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        Map<String, Object> katana = obj(get(library("_style_katana").animation().get("animations"), "attack.1"));
        Map<String, Object> katanaTiming = obj(get(library("_style_katana").timing().get("animations"), "attack.1"));
        // the test rig's lunge gets attack.1's clip as its own move.lunge, and the definition its frames
        editJson(root, HeroAssetCheck.chainPaths(RIG).get("animation"),
                d -> obj(d.get("animations")).put("move.lunge", katana));
        editJson(root, HeroAssetCheck.chainPaths(RIG).get("timing"),
                d -> obj(d.get("animations")).put("move.lunge", katanaTiming));
        editJson(root, HeroAssetCheck.outPaths(RIG).get("visual"), d -> d.put("moves", List.of("lunge")));
        String def = HeroAssetCheck.DEF_DIR + "/test_rig.json";
        editJson(root, def, d -> {
            for (Object m : list(d.get("moves"))) {
                obj(m).put("frames", frames(0.5, 0.05, 0.45));
            }
        });
        Result clean = HeroAssetCheck.checkCommitted(root, RIG);
        assertEquals(List.of(), clean.problems());
        assertEquals(List.of(), clean.notes(), "every move has its clip: nothing to note");
        editJson(root, def, d -> {
            for (Object m : list(d.get("moves"))) {
                obj(obj(m).get("frames")).put("windup", 0.65);
            }
        });
        List<String> p = problems(root);
        assertEquals(List.of("move lunge: windup 10 ticks in test_rig (move.lunge, its first hit or cast at tick 10 of"
                + " the action), 13 in the definition's frames (0.65 s): more than one tick apart"), p);
        // and a move with frames whose clip is missing is an error, not a NOTE
        editJson(root, HeroAssetCheck.chainPaths(RIG).get("animation"), d -> obj(d.get("animations")).clear());
        editJson(root, HeroAssetCheck.chainPaths(RIG).get("timing"), d -> obj(d.get("animations")).clear());
        p = problems(root);
        assertTrue(anyContains(p, "move lunge: the definition gives frames but the chain has no move.lunge"),
                String.join("\n", p));
        assertTrue(anyContains(p, "action code 64 (move.lunge, move slot 0) does not resolve through test_rig,"
                + " _style_katana, _core"), String.join("\n", p));
    }

    @Test
    void aModelOverTheCubeBudgetFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"), d -> {
            for (Object b : list(get(list(d.get("minecraft:geometry")).get(0), "bones"))) {
                if ("body".equals(get(b, "name"))) {
                    List<Object> cubes = new ArrayList<>(list(get(b, "cubes")));
                    for (int i = 0; i < 120; i++) {
                        cubes.add(Json.parse(Json.write(cubes.get(0))));
                    }
                    obj(b).put("cubes", cubes);
                }
            }
        });
        List<String> p = problems(root);
        assertTrue(anyContains(p, " cubes (budget 300)"), String.join("\n", p));
        assertTrue(anyContains(p, " cubes outside fine_ bones (budget 130): move detail (strands, frills, trims) onto"
                + " fine_ bones"), String.join("\n", p));
        assertTrue(anyContains(p, "bone body: north face UV overlaps body/north"), "the copies overlap each other");
    }

    @Test
    void aMissingEyeFrameFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        HeroAssetCheck.Texture t = readTexture(root, RIG);
        paint(t, 64, 10, 84, 20, new int[]{0, 0, 0, 0}); // eye frame 4 (row 1, column 0 of the 4 x 3 grid) erased
        writeTexture(root, RIG, t);
        List<String> p = problems(root);
        assertTrue(p.contains("eyes frame 4 is not fully opaque (missing frame?)"), String.join("\n", p));
    }

    @Test
    void aBlankSkinEyeFrameFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        HeroAssetCheck.Texture t = readTexture(root, RIG);
        int[] skin = {t.at(64, 9, 0), t.at(64, 9, 1), t.at(64, 9, 2), t.at(64, 9, 3)};
        paint(t, 64, 10, 84, 20, skin); // eye frame 4 painted over with frame 0's bottom-row skin
        writeTexture(root, RIG, t);
        List<String> p = problems(root);
        assertTrue(p.contains("eyes frame 4 is blank skin (missing frame?)"), String.join("\n", p));
    }

    @Test
    void eyesThatDoNotMirrorFailUnlessTheFaceIsAsymmetric(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        HeroAssetCheck.Texture t = readTexture(root, RIG);
        paint(t, 64, 8, 65, 9, new int[]{255, 0, 0, 255}); // one texel of frame 0's left edge, row 8
        writeTexture(root, RIG, t);
        assertTrue(problems(root).contains("eyes frame 0: the eyes do not mirror"), String.join("\n", problems(root)));
        editJson(root, HeroAssetCheck.outPaths(RIG).get("visual"), d -> obj(d.get("face")).put("asymmetric", true));
        assertFalse(anyContains(problems(root), "do not mirror"), String.join("\n", problems(root)));
    }

    @Test
    void aCubeInFrontOfTheFaceAndAPlaneOffItsRegionFail(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"), d -> {
            for (Object b : list(get(list(d.get("minecraft:geometry")).get(0), "bones"))) {
                if ("chest".equals(get(b, "name"))) {
                    // a visor over the eyes: in front of the head front (z -5), between the chin and the lash line
                    List<Object> cubes = new ArrayList<>(list(get(b, "cubes")));
                    cubes.add(parse("{\"origin\": [-3, 44, -6], \"size\": [6, 1, 1], \"uv\": {\"north\": {\"uv\":"
                            + " [250, 120], \"uv_size\": [6, 1]}}}"));
                    obj(b).put("cubes", cubes);
                }
                if ("eyes".equals(get(b, "name"))) {
                    Map<String, Object> north = obj(get(get(list(get(b, "cubes")).get(0), "uv"), "north"));
                    north.put("uv", List.of(65L, 0L));
                }
            }
        });
        List<String> p = problems(root);
        assertTrue(p.contains("bone chest: a cube sits in front of the face below y 47.5 within |x| < 4.5"),
                String.join("\n", p));
        assertTrue(p.contains("face plane eyes is not on its fixed region (64, 0, 20, 10)"), String.join("\n", p));
    }

    @Test
    void aMissingChainFileFails(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        Files.delete(root.resolve(HeroAssetCheck.chainPaths("_style_katana").get("timing")));
        List<String> p = problems(root);
        assertTrue(anyContains(p, "_style_katana.json: missing (timing file of the chain; GeckoLib needs every file of"
                + " it)"), String.join("\n", p));
    }

    @Test
    void brokenJsonAndAGeometryVersionFail(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        Path visual = root.resolve(HeroAssetCheck.outPaths(RIG).get("visual"));
        byte[] data = Files.readAllBytes(visual);
        Files.write(visual, java.util.Arrays.copyOf(data, data.length - 10));
        List<String> p = problems(root);
        assertTrue(anyContains(p, "test_rig.visual.json: not valid JSON"), String.join("\n", p));
        assertTrue(anyContains(p, "test_rig.visual.json: missing or not JSON"), String.join("\n", p));
        Files.write(visual, data);
        editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"), d -> d.put("format_version", "1.8.0"));
        p = problems(root);
        assertTrue(anyContains(p, "test_rig.geo.json: format_version '1.8.0', GeckoLib's geometry is 1.12.0"),
                String.join("\n", p));
        // strict: NaN, a comment or a trailing comma is not JSON, as Python's json reads it
        for (String bad : List.of("{\"a\": NaN}", "{\"a\": 1} // x", "{\"a\": [1, 2,]}", "{'a': 1}", "{\"a\": 01}")) {
            Files.writeString(visual, bad);
            assertTrue(anyContains(problems(root), "test_rig.visual.json: not valid JSON"), bad);
        }
    }

    @Test
    void visualTextureAndRegionsMustMatch(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.outPaths(RIG).get("visual"), d -> {
            obj(d.get("texture")).put("height", 256L);
            obj(get(get(d.get("face"), "regions"), "mouth")).put("v", 31L);
        });
        List<String> p = problems(root);
        assertTrue(p.contains("visual.json texture 256x256, the geometry 256x128"), String.join("\n", p));
        assertTrue(p.contains("visual.json face.regions.mouth is {'columns': 8, 'frames': 8, 'h': 3, 'u': 64, 'v': 31,"
                + " 'w': 8}, the rig's is {'u': 64, 'v': 30, 'w': 8, 'h': 3, 'columns': 8, 'frames': 8}"),
                String.join("\n", p));
    }

    @Test
    void aDefinitionWithPhasesNeedsThePhaseClips(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        editJson(root, HeroAssetCheck.DEF_DIR + "/test_rig.json", d -> obj(d.get("boss")).put("phases",
                list(Json.parse("[{\"at\": 100}, {\"at\": 60}, {\"at\": 25}]"))));
        List<String> p = problems(root);
        assertTrue(p.contains("action code 17 (phase.2, 3 boss phases) does not resolve through test_rig,"
                + " _style_katana, _core"), String.join("\n", p));
        assertTrue(anyContains(p, "action code 18 (phase.3"), String.join("\n", p));
    }

    @Test
    void ownAnimationsOverTheBudgetFail(@TempDir Path tmp) throws IOException {
        Path root = copyRoot(tmp, RIG);
        Object[] clip = clipWithFrames(frames(0.5, 0.05, 0.45));
        for (String kind : List.of("animation", "timing")) {
            editJson(root, HeroAssetCheck.chainPaths(RIG).get(kind), d -> {
                for (int i = 0; i < 25; i++) {
                    obj(d.get("animations")).put("emote.extra" + i, kind.equals("animation") ? clip[0] : clip[1]);
                }
            });
        }
        assertTrue(problems(root).contains("test_rig: 25 animations in the hero's own file (at most 24)"),
                String.join("\n", problems(root)));
    }

    // ------------------------------------------------------------------ the file-level rules one by one

    static Map<String, Object> written(String name, String keys, boolean loop, double length) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("animation_length", length);
        a.put("bones", Map.of("face_ctrl", Map.of("position", obj(Json.parse(keys)))));
        if (loop) {
            a.put("loop", true);
        }
        return Map.of("animations", Map.of(name, a));
    }

    static Map<String, Object> written(String keys) {
        return written("x", keys, false, 1.0);
    }

    @Test
    void faceKeysJumpAndStayWhole() {
        assertEquals(List.of(), HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\": [0, 0, 0]}, \"0.2\":"
                + " {\"pre\": [0, 0, 0], \"post\": [5, 4, 0]}, \"0.5\": {\"vector\": [5, 4, 0]}}")));
        assertEquals(List.of("f: x face_ctrl at 0.2: changes [0, 0, 0] -> [5, 4, 0] without a jump (pre/post): it would"
                + " slide through other frames"), HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\":"
                + " [0, 0, 0]}, \"0.2\": {\"vector\": [5, 4, 0]}}")));
        assertEquals(List.of("f: x face_ctrl at 0.0: [0.5, 0, 0] is not whole frame numbers in range (eyes 0-12, mouth"
                + " 0-8, cheeks 0-4)"), HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\": [0.5, 0,"
                + " 0]}}")));
        assertTrue(anyContains(HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\": [13, 0, 0]}}")),
                "in range"));
        assertTrue(anyContains(HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\": [0, 0, 0]}, \"0.2\":"
                + " {\"pre\": [3, 0, 0], \"post\": [5, 0, 0]}}")), "without a jump"));
        assertTrue(anyContains(HeroAssetCheck.checkFaceKeys("f", written("{\"0.0\": {\"vector\": [0, 0]}}")),
                "is not three numbers"));
    }

    @Test
    void channelsAscendStartAtZeroAndLoopsClose() {
        String zero = "{\"vector\": [0, 0, 0]}";
        assertEquals(List.of(), HeroAssetCheck.checkChannels("f", written("x", "{\"0.0\": " + zero + ", \"1.0\": "
                + zero + "}", true, 1.0)));
        assertEquals(List.of("f: x face_ctrl.position: key times do not ascend in file order"),
                HeroAssetCheck.checkChannels("f", written("{\"0.0\": " + zero + ", \"0.6\": {\"vector\": [1, 0, 0]},"
                        + " \"0.3\": {\"vector\": [2, 0, 0]}}")));
        assertEquals(List.of("f: x face_ctrl.position: the first key is at 0.1 s, not 0"),
                HeroAssetCheck.checkChannels("f", written("{\"0.1\": " + zero + "}")));
        assertEquals(List.of("f: x face_ctrl.position: a key at 1.2 s is after the end (1.0 s)"),
                HeroAssetCheck.checkChannels("f", written("{\"0.0\": " + zero + ", \"1.2\": " + zero + "}")));
        assertEquals(List.of("f: x face_ctrl.position: the loop does not close ([0, 0, 0] at 0, [3, 0, 0] at the end)"),
                HeroAssetCheck.checkChannels("f", written("x", "{\"0.0\": " + zero + ", \"1.0\": {\"vector\": [3, 0,"
                        + " 0]}}", true, 1.0)));
        assertEquals(List.of("f: x face_ctrl.position: a loop channel without a key at the end"),
                HeroAssetCheck.checkChannels("f", written("x", "{\"0.0\": " + zero + ", \"0.8\": " + zero + "}", true,
                        1.0)));
        String molang = "{\"vector\": [0, \"1 + 2 * v.fc_dhip\", 0]}";
        assertEquals(List.of(), HeroAssetCheck.checkChannels("f", written("x", "{\"0.0\": " + molang + ", \"1.0\": "
                + molang + "}", true, 1.0)));
        assertEquals(List.of("f: x face_ctrl.position: no keys"), HeroAssetCheck.checkChannels("f",
                written("{}")));
    }

    @Test
    void theWrittenLibrariesPassTheFileRules() throws IOException {
        for (String name : List.of("_core", "_style_fists", "_style_focus", "_style_hammer", "_style_katana",
                "_style_staff", "_style_sword_shield", "_style_twin_blades")) {
            Entry e = library(name);
            List<String> p = new ArrayList<>(HeroAssetCheck.checkChannels(name, e.animation()));
            p.addAll(HeroAssetCheck.checkFaceKeys(name, e.animation()));
            p.addAll(HeroAssetCheck.checkTimingPair(e.animation(), e.timing()));
            assertEquals(List.of(), p, name);
        }
    }

    @Test
    void timingFilesAgreeWithTheirAnimationFiles() throws IOException {
        Map<String, Object> anim = obj(Json.parse(Json.write(library("_style_katana").animation())));
        Map<String, Object> timing = obj(Json.parse(Json.write(library("_style_katana").timing())));
        Map<String, Object> t = obj(get(timing.get("animations"), "attack.1"));
        obj(list(t.get("events")).get(1)).put("tick", 11L);
        t.put("transition_ticks", 4L);
        List<String> p = HeroAssetCheck.checkTimingPair(anim, timing);
        assertTrue(p.contains("attack.1: event hit tick 11 does not match its time 0.500"), p.toString());
        assertTrue(p.contains("attack.1: transition_ticks 4, the action controller blends 2 ticks"), p.toString());
        obj(get(timing.get("animations"), "attack.2")).put("length", 2.0);
        obj(timing.get("animations")).remove("attack.3");
        p = HeroAssetCheck.checkTimingPair(anim, timing);
        assertTrue(anyContains(p, "attack.2: length "), p.toString());
        assertTrue(anyContains(p, "timing file lists ['attack.1', 'attack.2', 'combat.idle'"), p.toString());
    }

    @Test
    void codesFollowTheDefinition() throws IOException {
        Map<Integer, String> table = HeroAssetCheck.actionTable();
        Chain chain = libChain("katana", Map.of());
        Map<String, Object> visual = visual(List.of(), List.of());
        assertEquals(List.of(), HeroAssetCheck.checkCodes(visual, null, chain, table));
        Map<String, Object> thrown = parse("{\"basic_attack\": {\"style\": \"thrown\"}, \"boss\": {\"phases\":"
                + " [{\"at\": 100}]}}");
        List<String> p = HeroAssetCheck.checkCodes(visual, thrown, chain, table);
        assertEquals(List.of("action code 4 (attack.ranged, a ranged basic attack (thrown)) does not resolve through"
                + " t_hero, _style_katana, _core"), p);
        assertEquals(List.of(), HeroAssetCheck.checkCodes(visual, thrown, libChain("focus", Map.of()), table));
        p = HeroAssetCheck.checkCodes(visual, parse("{\"ultimate\": {\"id\": \"big\", \"awakened\": {\"id\":"
                + " \"bigger\"}}}"), chain, table);
        assertEquals(List.of("action code 76", "action code 78"), p.stream().map(m -> m.split(" \\(")[0]).sorted()
                .toList());
        p = HeroAssetCheck.checkCodes(visual(List.of("cut"), List.of("rage")), null, chain, table);
        assertTrue(anyContains(p, "action code 64 (move.cut, move slot 0)"), p.toString());
        assertTrue(anyContains(p, "action code 24 (state.rage, state slot 1)"), p.toString());
        p = HeroAssetCheck.checkCodes(visual, parse("{\"ultimate\": {\"id\": \"u\"}}"), libChain("katana",
                Map.of("ultimate", empty(), "ultimate.loop", empty())), table);
        assertTrue(anyContains(p, "action code 77 (ultimate.end"), p.toString());
        // the required set: every fixed code, the conditional ones only when the definition calls for them
        List<String> names = HeroAssetCheck.requiredActions(visual, null, chain, table).stream()
                .map(Required::name).toList();
        assertTrue(names.containsAll(List.of("attack.1", "guard", "work.mine", "emote.greet", "personal.3", "defeated",
                "spared", "founding")), names.toString());
        assertFalse(names.contains("attack.ranged") || names.contains("phase.2") || names.contains("ultimate"),
                names.toString());
    }

    @Test
    void moveAndStateListsFollowTheDefinition() throws IOException {
        Map<Integer, String> table = HeroAssetCheck.actionTable();
        Map<String, Object> definition = parse("{\"moves\": [{\"id\": \"a\", \"frames\": {\"windup\": 0.5}}, {\"id\":"
                + " \"b\"}, {\"id\": \"c\"}], \"states\": [{\"name\": \"rage\"}]}");
        Chain chain = libChain("katana", Map.of("move.a", empty(), "move.c", empty()));
        Result r = HeroAssetCheck.checkLists(visual(List.of("a", "c"), List.of("rage")), definition, chain, table);
        assertEquals(List.of(), r.problems());
        assertEquals(List.of("move b has no clip (move.b): the server plays its look's telegraph pose"), r.notes());
        r = HeroAssetCheck.checkLists(visual(List.of("move.a", "move.c"), List.of("state.rage")), definition, chain,
                table);
        assertEquals(List.of(), r.problems(), "the move. and state. prefixes are allowed");
        r = HeroAssetCheck.checkLists(visual(List.of("c", "a"), List.of("rage")), definition, chain, table);
        assertTrue(r.problems().contains("visual.json moves ['c', 'a'], the definition's moves ['a', 'b', 'c'] (its"
                + " order; a move may be left out only when the chain has no move.<id> for it)"), r.problems()
                .toString());
        r = HeroAssetCheck.checkLists(visual(List.of("a", "c"), List.of()), definition, chain, table);
        assertTrue(r.problems().contains("visual.json states [], the definition's states ['rage']"),
                r.problems().toString());
        r = HeroAssetCheck.checkLists(visual(List.of("c"), List.of("rage")), definition, libChain("katana",
                Map.of("move.c", empty())), table);
        assertTrue(r.problems().contains("move a: the definition gives frames but the chain has no move.a"),
                r.problems().toString());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            many.add("m" + i);
        }
        r = HeroAssetCheck.checkLists(visual(many, List.of()), null, chain, table);
        assertTrue(r.problems().contains("visual.json lists 13 moves; the action codes have 12 move slots"),
                r.problems().toString());
        r = HeroAssetCheck.checkLists(visual(List.of("a", "a"), List.of("x", "y", "z", "w")), null, chain, table);
        assertTrue(r.problems().contains("visual.json lists 4 states; the action codes have 3 state slots"),
                r.problems().toString());
        assertTrue(r.problems().contains("visual.json lists a move twice: ['a', 'a']"), r.problems().toString());
    }

    @Test
    void framesWithinOneTick() throws IOException {
        Map<String, Object> definition = parse("{\"moves\": [{\"id\": \"pounce\", \"frames\": {\"windup\": 0.8,"
                + " \"active\": 0.2, \"recovery\": 1.0}}]}");
        Chain ok = libChain("fists", Map.of("move.pounce", clipWithFrames(frames(0.85, 0.2, 0.95))));
        assertEquals(List.of(), HeroAssetCheck.checkFrames(definition, ok));
        Chain late = libChain("fists", Map.of("move.pounce", clipWithFrames(frames(0.95, 0.2, 0.85))));
        List<String> p = HeroAssetCheck.checkFrames(definition, late);
        assertEquals(List.of("move pounce: windup 19 ticks in t_hero (move.pounce, its first hit or cast at tick 19 of"
                + " the action), 16 in the definition's frames (0.80 s): more than one tick apart", "move pounce:"
                + " recovery 17 ticks in t_hero (move.pounce), 20 in the definition's frames (1.00 s): more than one"
                + " tick apart"), p);
        Object[] noMarker = clipWithFrames(frames(0.8, 0.2, 1.0));
        obj(noMarker[1]).remove("frame_ticks");
        p = HeroAssetCheck.checkFrames(definition, libChain("fists", Map.of("move.pounce", noMarker)));
        assertEquals(List.of("move pounce: move.pounce (t_hero) has no hit or cast marker; the definition's frames are"
                + " {'windup': 0.8, 'active': 0.2, 'recovery': 1.0}"), p);
        Map<String, Object> gen = parse("{\"moves\": [{\"id\": \"pounce\"}], \"generated\": {\"moves\": [{\"id\":"
                + " \"pounce\", \"frames\": {\"windup\": 0.6}}]}}");
        assertTrue(anyContains(HeroAssetCheck.checkFrames(gen, ok), "generated frames"));
        Map<String, Object> ult = parse("{\"ultimate\": {\"id\": \"u\", \"frames\": {\"windup\": 1.5, \"active\": 0.2,"
                + " \"recovery\": 1.5}}}");
        p = HeroAssetCheck.checkFrames(ult, libChain("fists", Map.of("ultimate", clipWithFrames(frames(1.5, 0.3,
                1.5)))));
        assertTrue(anyContains(p, "the ultimate: active 6 ticks"), p.toString());
        // Python's round: 0.125 s and 0.225 s are 2.5 and 4.5 ticks, which round half to even, as herogen counts
        assertEquals(2, HeroAssetCheck.ticks(0.125));
        assertEquals(4, HeroAssetCheck.ticks(0.225));
        assertEquals(6, HeroAssetCheck.ticks(0.325));
    }

    // ------------------------------------------------------------------ the eight slice kits

    static Map<String, Object[]> ownClips(Map<String, Object> d) {
        Map<String, Object[]> own = new LinkedHashMap<>();
        for (Object m : list(d.get("moves"))) {
            own.put("move." + get(m, "id"), clipWithFrames(obj(get(m, "frames"))));
        }
        own.put("ultimate", clipWithFrames(obj(get(d.get("ultimate"), "frames"))));
        for (Object s : list(d.get("states"))) {
            own.put("state." + get(s, "name"), empty());
        }
        for (int n = 2; n <= list(get(d.get("boss"), "phases")).size(); n++) {
            own.put("phase." + n, empty());
        }
        return own;
    }

    static Map<String, Object> visualOf(Map<String, Object> d) {
        return visual(list(d.get("moves")).stream().map(m -> get(m, "id")).toList(),
                list(d.get("states")).stream().map(s -> get(s, "name")).toList());
    }

    static Map<String, Object> sliceKit(String id) throws IOException {
        return obj(Json.parse(Files.readAllBytes(ROOT.resolve(HeroAssetCheck.SLICE_DIR + "/" + id + ".json"))));
    }

    @Test
    void theSliceKitsResolveWithTheirClips() throws IOException {
        Map<Integer, String> table = HeroAssetCheck.actionTable();
        for (Map.Entry<String, String> e : new TreeMap<>(SLICE_STYLES).entrySet()) {
            Map<String, Object> d = sliceKit(e.getKey());
            Chain chain = libChain(e.getValue(), ownClips(d));
            Map<String, Object> visual = visualOf(d);
            List<String> problems = new ArrayList<>(HeroAssetCheck.checkCodes(visual, d, chain, table));
            problems.addAll(HeroAssetCheck.checkLists(visual, d, chain, table).problems());
            problems.addAll(HeroAssetCheck.checkFrames(d, chain));
            assertEquals(List.of(), problems, e.getKey());
            List<String> names = HeroAssetCheck.requiredActions(visual, d, chain, table).stream().map(Required::name)
                    .toList();
            assertEquals("thrown".equals(get(d.get("basic_attack"), "style")), names.contains("attack.ranged"),
                    e.getKey());
            assertTrue(names.contains("phase.3"), e.getKey());
        }
    }

    @Test
    void aMissingSignatureIsNamed() throws IOException {
        Map<Integer, String> table = HeroAssetCheck.actionTable();
        Map<String, Object> d = sliceKit("hinae_shirasuzu");
        Map<String, Object[]> own = ownClips(d);
        own.remove("move.purify");
        own.remove("phase.2");
        Chain chain = libChain("focus", own);
        Map<String, Object> visual = visualOf(d);
        List<String> p = HeroAssetCheck.checkCodes(visual, d, chain, table);
        assertTrue(anyContains(p, "(move.purify, move slot 2)"), p.toString());
        assertTrue(anyContains(p, "(phase.2, 3 boss phases)"), p.toString());
        assertTrue(anyContains(HeroAssetCheck.checkLists(visual, d, chain, table).problems(),
                "move purify: the definition gives frames"));
    }

    // ------------------------------------------------------------------ herogen gives the same messages

    /** python3 with NumPy and Pillow, the generator's own check (tools/herogen/assets.py), or null when absent. */
    static List<Result> python(List<Path> roots) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("python3", "-c", String.join("\n",
                "import json, sys",
                "sys.path.insert(0, 'tools/herogen')",
                "import assets",
                "table = assets.action_table()",
                "out = []",
                "for root in sys.argv[1:]:",
                "    p, n = assets.check_committed('test_rig', root, table)",
                "    out.append({'problems': p, 'notes': n})",
                "print(json.dumps(out))")));
        roots.forEach(r -> cmd.add(r.toString()));
        Process proc;
        try {
            proc = new ProcessBuilder(cmd).directory(ROOT.toFile()).redirectErrorStream(false).start();
        } catch (IOException e) {
            return null;
        }
        byte[] out = proc.getInputStream().readAllBytes();
        String err = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!proc.waitFor(300, TimeUnit.SECONDS) || proc.exitValue() != 0) {
            assumeTrue(!err.contains("No module named"), "herogen's Python modules are not importable: " + err);
            throw new AssertionError("herogen failed: " + err);
        }
        List<Result> results = new ArrayList<>();
        for (Object o : list(Json.parse(out))) {
            results.add(new Result(list(get(o, "problems")).stream().map(Object::toString).toList(),
                    list(get(o, "notes")).stream().map(Object::toString).toList()));
        }
        return results;
    }

    /** A JSON syntax error's detail is the parser's own wording: compared up to it. */
    static List<String> comparable(List<String> problems) {
        return problems.stream().map(m -> m.contains(": not valid JSON (") ? m.substring(0, m.indexOf(" (")) : m)
                .toList();
    }

    @Test
    void herogenGivesTheSameMessagesOnTheSameFiles(@TempDir Path tmp) throws Exception {
        List<Path> roots = new ArrayList<>();
        List<String> what = new ArrayList<>();
        Map<String, ThrowingEdit> breaks = new LinkedHashMap<>();
        breaks.put("the committed files", root -> {
        });
        breaks.put("a marker moved by 3 ticks", root -> editJson(root, HeroAssetCheck.chainPaths("_style_katana")
                .get("animation"), d -> {
            Map<String, Object> tl = obj(get(get(d.get("animations"), "attack.1"), "timeline"));
            tl.put("0.65", tl.remove("0.5"));
        }));
        breaks.put("a timing file moved by 3 ticks", root -> editJson(root, HeroAssetCheck.chainPaths("_style_katana")
                .get("timing"), d -> obj(get(get(d.get("animations"), "attack.1"), "frames")).put("windup", 0.65)));
        breaks.put("over the cube budget", root -> editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"), d -> {
            for (Object b : list(get(list(d.get("minecraft:geometry")).get(0), "bones"))) {
                if ("body".equals(get(b, "name"))) {
                    List<Object> cubes = new ArrayList<>(list(get(b, "cubes")));
                    for (int i = 0; i < 120; i++) {
                        cubes.add(cubes.get(0));
                    }
                    obj(b).put("cubes", cubes);
                }
            }
        }));
        breaks.put("a missing eye frame", root -> {
            HeroAssetCheck.Texture t = readTexture(root, RIG);
            paint(t, 64, 10, 84, 20, new int[]{0, 0, 0, 0});
            paint(t, 150, 8, 151, 9, new int[]{1, 2, 3, 255}); // and a texel showing through the eyes' hole
            writeTexture(root, RIG, t);
        });
        breaks.put("a blank skin eye frame and eyes that do not mirror", root -> {
            HeroAssetCheck.Texture t = readTexture(root, RIG);
            int[] skin = {t.at(64, 9, 0), t.at(64, 9, 1), t.at(64, 9, 2), t.at(64, 9, 3)};
            paint(t, 64, 10, 84, 20, skin);
            paint(t, 64, 8, 65, 9, new int[]{255, 0, 0, 255});
            paint(t, 64, 30, 72, 33, new int[]{9, 9, 9, 255}); // mouth frame 0 differs from the head front's edge
            writeTexture(root, RIG, t);
        });
        breaks.put("a missing chain file", root -> Files.delete(root.resolve(HeroAssetCheck.chainPaths("_style_katana")
                .get("timing"))));
        breaks.put("broken JSON", root -> {
            Path v = root.resolve(HeroAssetCheck.outPaths(RIG).get("visual"));
            byte[] data = Files.readAllBytes(v);
            Files.write(v, java.util.Arrays.copyOf(data, data.length - 10));
        });
        breaks.put("a geometry version", root -> editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"),
                d -> d.put("format_version", "1.8.0")));
        breaks.put("visual.json against the geometry and the rig", root -> editJson(root,
                HeroAssetCheck.outPaths(RIG).get("visual"), d -> {
                    obj(d.get("texture")).put("height", 256L);
                    obj(get(get(d.get("face"), "regions"), "mouth")).put("v", 31L);
                    d.put("animation_chain", List.of(RIG, "_style_katana"));
                    d.put("states", List.of("rage"));
                }));
        breaks.put("three phases, a ranged basic and an ultimate", root -> editJson(root, HeroAssetCheck.DEF_DIR
                + "/test_rig.json", d -> {
                    obj(d.get("boss")).put("phases", list(Json.parse("[{\"at\": 100}, {\"at\": 60}, {\"at\": 25}]")));
                    obj(d.get("basic_attack")).put("style", "thrown");
                    d.put("ultimate", parse("{\"id\": \"u\", \"frames\": {\"windup\": 1.5}, \"awakened\": {\"id\":"
                            + " \"v\"}}"));
                }));
        breaks.put("bones, UVs and the face", root -> editJson(root, HeroAssetCheck.outPaths(RIG).get("geo"), d -> {
            for (Object b : list(get(list(d.get("minecraft:geometry")).get(0), "bones"))) {
                if ("chest".equals(get(b, "name"))) {
                    List<Object> cubes = new ArrayList<>(list(get(b, "cubes")));
                    cubes.add(parse("{\"origin\": [-3, 44, -6], \"size\": [6, 1, 1], \"rotation\": [0, 10, 5],"
                            + " \"pivot\": [0, 44, -6], \"uv\": {\"north\": {\"uv\": [250, 120.5], \"uv_size\":"
                            + " [6, 1]}, \"south\": {\"uv\": [255, 127], \"uv_size\": [6, 1]}, \"down\": {\"uv\":"
                            + " [240, 100], \"uv_size\": [6, 1]}}}"));
                    obj(b).put("cubes", cubes);
                }
                if ("eyes".equals(get(b, "name"))) {
                    obj(get(get(list(get(b, "cubes")).get(0), "uv"), "north")).put("uv", List.of(65L, 0L));
                }
                if ("sash".equals(get(b, "name"))) {
                    obj(b).put("name", "Sash");
                }
                if ("leg_l".equals(get(b, "name"))) {
                    obj(b).put("parent", "foot_l");
                }
            }
        }));
        breaks.put("face keys, channels and an own file", root -> {
            editJson(root, HeroAssetCheck.chainPaths("_core").get("animation"), d -> {
                Map<String, Object> idle = obj(get(d.get("animations"), "emote.greet"));
                Map<String, Object> bones = obj(idle.get("bones"));
                bones.put("face_ctrl", parse("{\"position\": {\"0.0\": {\"vector\": [0, 0, 0]}, \"0.2\": {\"vector\":"
                        + " [5, 4.5, 0]}}, \"rotation\": {\"0.0\": [0, 0, 0]}}"));
                bones.put("zz_late", parse("{\"rotation\": {\"0.1\": [1, 2, 3], \"0.05\": [0, 0, 0], \"9.0\": [1, 1,"
                        + " 1]}}"));
            });
            Object[] clip = clipWithFrames(frames(0.5, 0.05, 0.45));
            editJson(root, HeroAssetCheck.chainPaths(RIG).get("animation"),
                    d -> obj(d.get("animations")).put("move.lunge", clip[0]));
        });
        breaks.put("a loop that does not close and timing entries that disagree", root -> {
            editJson(root, HeroAssetCheck.chainPaths("_core").get("animation"), d -> {
                Map<String, Object> carry = obj(get(d.get("animations"), "carry.loop"));
                obj(carry.get("bones")).put("zz_open", parse("{\"rotation\": {\"0.0\": [0, 0, 0], \""
                        + HeroAssetCheck.pyFloat(HeroAssetCheck.num(carry.get("animation_length")))
                        + "\": {\"vector\": [4, 0, 0]}}}"));
            });
            editJson(root, HeroAssetCheck.chainPaths("_style_katana").get("timing"), d -> {
                Map<String, Object> a = obj(d.get("animations"));
                obj(a.get("attack.2")).put("length", 3.0);
                obj(list(get(a.get("attack.3"), "events")).get(0)).put("tick", 99L);
                obj(a.get("guard")).put("transition_ticks", 3L);
                obj(a.get("guard")).put("frames", frames(0.5, 0.05, 0.45));
                obj(a.get("dodge")).put("total_ticks", 1L);
            });
        });
        breaks.put("cheek and mouth frames", root -> {
            HeroAssetCheck.Texture t = readTexture(root, RIG);
            paint(t, 64, 33, 65, 34, new int[]{200, 10, 10, 255}); // a texel in the empty cheek frame 0
            paint(t, 96, 33, 112, 35, new int[]{0, 0, 0, 0}); // cheek frame 2 erased
            for (int y = 30; y < 33; y++) {
                for (int x = 72; x < 80; x++) { // mouth frame 1 a copy of frame 2
                    System.arraycopy(t.rgba(), (y * t.w() + x + 8) * 4, t.rgba(), (y * t.w() + x) * 4, 4);
                }
            }
            paint(t, 160, 3, 161, 4, new int[]{0, 0, 0, 0}); // a hole in the forehead
            writeTexture(root, RIG, t);
        });
        breaks.put("a definition three ticks after its clip, and generated frames", root -> {
            Map<String, Object> katana = obj(get(library("_style_katana").animation().get("animations"), "attack.1"));
            Map<String, Object> timing = obj(get(library("_style_katana").timing().get("animations"), "attack.1"));
            editJson(root, HeroAssetCheck.chainPaths(RIG).get("animation"),
                    d -> obj(d.get("animations")).put("move.lunge", katana));
            editJson(root, HeroAssetCheck.chainPaths(RIG).get("timing"),
                    d -> obj(d.get("animations")).put("move.lunge", timing));
            editJson(root, HeroAssetCheck.outPaths(RIG).get("visual"), d -> d.put("moves", List.of("lunge")));
            editJson(root, HeroAssetCheck.DEF_DIR + "/test_rig.json", d -> {
                for (Object m : list(d.get("moves"))) {
                    obj(m).put("frames", frames(0.65, 0.05, 0.6));
                }
                d.put("generated", parse("{\"moves\": [{\"id\": \"lunge\", \"frames\": {\"windup\": 0.4,"
                        + " \"recovery\": 0.45}}]}"));
            });
        });
        breaks.put("the geometry's description and bones", root -> editJson(root, HeroAssetCheck.outPaths(RIG)
                .get("geo"), d -> {
                    Map<String, Object> g = obj(list(d.get("minecraft:geometry")).get(0));
                    obj(g.get("description")).put("texture_height", 256L);
                    List<Object> bones = new ArrayList<>(list(g.get("bones")));
                    for (Object b : bones) {
                        if ("mouth".equals(get(b, "name"))) {
                            obj(b).remove("cubes");
                        }
                        if ("head".equals(get(b, "name"))) {
                            obj(get(get(list(get(b, "cubes")).get(0), "uv"), "north")).put("uv", List.of(140L, 0L));
                        }
                    }
                    for (int i = 0; i < 40; i++) {
                        bones.add(parse("{\"name\": \"fine_extra" + (i % 39) + "\", \"parent\": \"body\","
                                + " \"pivot\": [0, 0, 0]}"));
                    }
                    // a cloth mask may cover the mouth, never the eyes
                    bones.add(parse("{\"name\": \"mask\", \"parent\": \"head\", \"pivot\": [0, 42, 0], \"cubes\":"
                            + " [{\"origin\": [-3, 42, -6], \"size\": [6, 1.5, 1], \"uv\": {\"north\": {\"uv\": [240,"
                            + " 90], \"uv_size\": [6, 2]}}}]}"));
                    bones.add(parse("{\"name\": \"p2_fine_mask\", \"parent\": \"head\", \"pivot\": [0, 42, 0],"
                            + " \"cubes\": [{\"origin\": [-3, 42, -6], \"size\": [6, 3, 1], \"uv\": {\"north\":"
                            + " {\"uv\": [240, 80], \"uv_size\": [6, 3]}}}]}"));
                    g.put("bones", bones);
                }));
        for (Map.Entry<String, ThrowingEdit> e : breaks.entrySet()) {
            Path root = copyRoot(Files.createDirectory(tmp.resolve("r" + roots.size())), RIG);
            e.getValue().apply(root);
            roots.add(root);
            what.add(e.getKey());
        }
        List<Result> py;
        try {
            Process probe = new ProcessBuilder("python3", "-c", "import numpy, PIL").start();
            assumeTrue(probe.waitFor(60, TimeUnit.SECONDS) && probe.exitValue() == 0,
                    "python3 with NumPy and Pillow is not installed");
        } catch (IOException ex) {
            assumeTrue(false, "python3 is not installed");
        }
        py = python(roots);
        assumeTrue(py != null, "python3 is not installed");
        int differences = 0;
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            Result java = HeroAssetCheck.checkCommitted(roots.get(i), RIG);
            if (!comparable(java.problems()).equals(comparable(py.get(i).problems()))
                    || !java.notes().equals(py.get(i).notes())) {
                differences++;
                report.append("\n").append(what.get(i)).append(":\n  java   ").append(String.join("\n         ",
                        java.problems())).append("\n  python ").append(String.join("\n         ",
                        py.get(i).problems())).append("\n  notes  ").append(java.notes()).append(" / ")
                        .append(py.get(i).notes());
            }
            if (i > 0) {
                assertFalse(java.problems().isEmpty(), what.get(i) + " must fail");
            }
        }
        assertEquals(0, differences, report.toString());
    }

    @FunctionalInterface
    interface ThrowingEdit {
        void apply(Path root) throws IOException;
    }
}
