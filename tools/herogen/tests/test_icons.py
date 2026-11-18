"""R6 and B5: the artifact and boss token icons (icons.py) and their contact sheet (render/iconsheet.py).

The vanilla item rules (16x16 on a transparent canvas, binary alpha, lit from the top left, at most eight colours a
material), the tiers by material richness (ARTIFACTS.md 1.2), the catalogue's ids and names (ARTIFACTS.md 7 with the
reviewed names and the revision's growing and engineered artifacts, the sigil, BOSS_HEROES.md 3.4's two tokens), the
slow pulse of the signatures and tokens, the growing artifacts' four icon states, determinism, and the committed
textures and sheets against a fresh build. Each rule has a sabotaged case that must fail."""
import copy
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

from helpers import HEROGEN  # noqa: F401  (sets the path)

import icons
import palette
import util


def _png(a):
    return util.png_bytes(Image.fromarray(np.asarray(a, dtype=np.uint8), "RGBA"))


class _Sources:
    """A copy of herosrc/artifacts in a temporary folder, to sabotage."""

    def __init__(self):
        self.tmp = tempfile.mkdtemp(prefix="icons_")
        self.src = os.path.join(self.tmp, "artifacts")
        shutil.copytree(icons.SRC_DIR, self.src)
        self.out = os.path.join(self.tmp, "item")
        with open(os.path.join(self.src, "icons.json"), encoding="utf-8") as f:
            self.doc = json.load(f)

    def save(self):
        with open(os.path.join(self.src, "icons.json"), "w", encoding="utf-8") as f:
            json.dump(self.doc, f)

    def edit_shapes(self, change):
        """Apply change(shapes) to this copy's shapes.json."""
        path = os.path.join(self.src, "shapes.json")
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
        change(doc["shapes"])
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f)

    def problems(self):
        """(errors, notes) with the textures a fresh build of these sources would write."""
        self.save()
        try:
            files = icons.build_files(self.src, self.out)
        except Exception:                              # noqa: BLE001 - a broken spec may not paint at all
            files = {}
        return icons.problems(self.src, self.out, files=files)

    def close(self):
        shutil.rmtree(self.tmp)


class IconSetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shapes, cls.materials, cls.specs = icons.load()
        cls.files = icons.build_files()

    def test_committed_icons_pass_every_check(self):
        """`herogen icons --check`: the specs, the catalogue, the colours, the textures (and they are fresh)."""
        errors, notes = icons.problems()
        self.assertEqual(errors, [])
        for aid in self.specs:                         # notes only about others (artifacts whose icons come later)
            self.assertFalse(any(aid + ".png" in n or n.startswith(aid + ":") for n in notes), aid)

    def test_build_is_deterministic(self):
        """Two builds in one process, and one in a process with another hash seed, give the same bytes."""
        again = icons.build_files()
        self.assertEqual(self.files, again)
        digest = hashlib.sha256(b"".join(self.files[k] for k in sorted(self.files))).hexdigest()
        code = ("import hashlib, icons; f = icons.build_files(); "
                "print(hashlib.sha256(b''.join(f[k] for k in sorted(f))).hexdigest())")
        env = dict(os.environ, PYTHONHASHSEED="12345")
        out = subprocess.run([sys.executable, "-c", code], cwd=HEROGEN, env=env, capture_output=True, text=True,
                             check=True).stdout.strip()
        self.assertEqual(out, digest)

    def test_every_catalogue_item_has_an_icon_under_its_name(self):
        """The 61 artifacts of ARTIFACTS.md 7 (12 components, 28 advanced of which 8 grow, 13 greater of which 3 are
        engineered, 6 rare finds, 2 signatures), the Artificer's Sigil and the two boss tokens: one texture each (four
        for a growing artifact), named by the item id the item side registers, with the catalogue's (reviewed) name
        and tier."""
        cat = icons.catalogue()
        tiers, kinds = {}, {}
        for entry in cat.values():
            tiers[entry.tier] = tiers.get(entry.tier, 0) + 1
            kinds[entry.kind] = kinds.get(entry.kind, 0) + 1
        for tier, n in {"component": 12, "advanced": 28, "greater": 13, "rare": 6, "signature": 2}.items():
            self.assertGreaterEqual(tiers.get(tier, 0), n, tier)
        self.assertGreaterEqual(kinds.get("growing", 0), 8)
        self.assertGreaterEqual(kinds.get("engineered", 0), 3)
        want = {k for k, v in cat.items() if v.tier is not None} | set(icons.EXTRA_ITEMS)
        self.assertGreaterEqual(len(want), 64)
        self.assertLessEqual(want, set(self.specs))
        self.assertLessEqual(set(self.specs), set(cat) | set(icons.EXTRA_ITEMS))
        for aid in want:
            self.assertIn(os.path.join(icons.OUT_DIR, aid + ".png"), self.files)
            growing = cat[aid].kind == "growing" if aid in cat else False
            self.assertEqual(bool(self.specs[aid].get("growth")), growing, aid)
            for n in (1, 2, 3):
                self.assertEqual(os.path.join(icons.OUT_DIR, "%s_%d.png" % (aid, n)) in self.files, growing, aid)
        for aid, name in (("marrow_torc", "Marrow Torc"), ("menders_brazier", "Mender's Brazier"),
                          ("starless_pearl", "Starless Pearl"), ("hollow_star", "Hollow Star"),
                          ("spirecore", "Spirecore"), ("wither_token", "Wither Hero Token"),
                          ("ender_dragon_token", "Ender Dragon Hero Token"),
                          ("bloodglass_ampoule", "Bloodglass Ampoule"), ("rivals_cord", "Rival's Cord"),
                          ("observer_lens", "Observer Lens")):
            self.assertEqual(self.specs[aid]["name"], name)

    def test_textures_follow_the_vanilla_item_rules(self):
        """RGBA, 16x16 (16x64: four frames for the pulsing ones), alpha 0 or 255, a silhouette that never changes
        between frames, an .mcmeta exactly for the pulsing ones."""
        owners = icons.texture_owners(self.specs)
        self.assertEqual(len(owners), sum(len(icons.textures(k, v)) for k, v in self.specs.items()))
        for name, (aid, _) in owners.items():
            spec = self.specs[aid]
            png = self.files[os.path.join(icons.OUT_DIR, name + ".png")]
            meta = self.files.get(os.path.join(icons.OUT_DIR, name + ".png.mcmeta"))
            im = Image.open(io.BytesIO(png))
            self.assertEqual(im.mode, "RGBA", name)
            pulsing = spec["tier"] in icons.PULSING
            self.assertEqual(im.size, (16, 64) if pulsing else (16, 16), name)
            a = np.array(im)[:, :, 3]
            self.assertTrue(np.isin(a, (0, 255)).all(), name)
            self.assertEqual(meta is not None, pulsing, name)
            self.assertEqual(icons.check_image(name, png, meta, spec), [], name)
        self.assertEqual(len(self.files), len(owners) + 4)          # the textures and the four .mcmeta
        self.assertEqual(sorted(k for k, s in self.specs.items() if s["tier"] in icons.PULSING),
                         ["ender_dragon_token", "hollow_star", "spirecore", "wither_token"])

    def test_check_image_catches_broken_textures(self):
        spec = self.specs["knapped_flint"]
        good = np.array(icons.image_of("knapped_flint", spec, self.shapes, self.materials))
        self.assertEqual(icons.check_image("x", _png(good), None, spec), [])
        soft = good.copy()                             # a semi-transparent pixel
        ys, xs = np.nonzero(good[:, :, 3])
        soft[ys[0], xs[0], 3] = 128
        self.assertTrue(any("semi-transparent" in p for p in icons.check_image("x", _png(soft), None, spec)))
        big = np.zeros((32, 32, 4), dtype=np.uint8)
        big[:16, :16] = good
        self.assertTrue(any("not 16x16" in p for p in icons.check_image("x", _png(big), None, spec)))
        self.assertTrue(any("still icon" in p for p in icons.check_image("x", _png(good), b"{}", spec)))
        tok = self.specs["wither_token"]
        anim = np.array(icons.image_of("wither_token", tok, self.shapes, self.materials))
        meta = util.dumps_json(icons.mcmeta(tok)).encode("utf-8")
        self.assertEqual(icons.check_image("t", _png(anim), meta, tok), [])
        self.assertTrue(any("missing" in p for p in icons.check_image("t", _png(anim), None, tok)))
        moved = anim.copy()
        moved[16:32] = np.roll(moved[16:32], 1, axis=1)   # frame 2's silhouette shifted
        self.assertTrue(any("alpha differs" in p for p in icons.check_image("t", _png(moved), meta, tok)))
        fast = json.dumps({"animation": {"frametime": 2}}).encode("utf-8")
        self.assertTrue(any("want frametime" in p for p in icons.check_image("t", _png(anim), fast, tok)))

    def test_at_most_eight_colours_a_material(self):
        """Every material paints from one seven-tone ramp (and the white of a sparkle): at most eight colours, and
        sabotaged sparkles that paint a new colour on each pixel are caught."""
        for aid, spec in self.specs.items():
            for key, n in icons.material_colours(aid, spec, self.shapes, self.materials).items():
                self.assertLessEqual(n, icons.MAX_MATERIAL_COLOURS, "%s %s" % (aid, key))
        real = icons._sparkles

        def gaudy(out, g, parts, spec, solid):
            real(out, g, parts, spec, solid)
            ys, xs = np.nonzero(out[:, :, 3])
            out[ys, xs, 0] = (np.arange(len(xs)) * 5) % 256
        icons._sparkles = gaudy
        try:
            errors, _ = icons.problems(files=self.files)
        finally:
            icons._sparkles = real
        self.assertTrue(any("paints" in e and "colours" in e for e in errors))

    def test_tiers_by_material_richness(self):
        """ARTIFACTS.md 1.2: components one material; advanced a setting and a gem in their element's colour;
        greater a second precious material with a one-pixel white highlight; rare finds a gem; signatures a setting,
        a precious part and the pulse; the element foci wear their element's gem."""
        cat = icons.catalogue()
        looks = [(aid, spec, st) for aid, spec in self.specs.items() for st in icons.growth_states(spec)]
        self.assertEqual(len(looks), len(icons.texture_owners(self.specs)))
        for aid, spec, state in looks:
            g, parts = icons.resolve(spec, self.shapes, state)
            roles = {p["role"] for p in parts.values()}
            tier = spec["tier"]
            if tier == "component":
                self.assertFalse(roles & {"setting", "gem", "precious", "glow"}, aid)
                self.assertEqual(len({spec["parts"][c] for c, p in parts.items() if p["role"] == "body"}), 1, aid)
            if tier == "advanced":
                self.assertTrue({"setting", "gem"} <= roles, aid)
                self.assertNotIn("precious", roles, aid)
            if tier in ("greater", "signature"):
                self.assertIn("precious", roles, aid)
                out, mat = icons._paint(aid, spec, self.shapes, self.materials, 0, state)
                prec = {spec["parts"][c] for c, p in parts.items() if p["role"] == "precious"}
                white = [(y, x) for y in range(16) for x in range(16)
                         if mat[y, x] in prec and tuple(out[y, x, :3]) == icons.WHITE]
                self.assertEqual(len(white), 1, aid)
            if tier == "rare":
                self.assertIn("gem", roles, aid)
            if aid in cat and cat[aid].element:
                self.assertEqual(spec["gem"], cat[aid].element, aid)

    def test_tier_rules_catch_sabotaged_specs(self):
        cases = [
            ("knapped_flint", lambda s: s.update(shape=self.specs["ironbound_whetstone"]["shape"], gem="physical",
                                                 parts=dict(self.specs["ironbound_whetstone"]["parts"])),
             "a component icon may not have a setting part"),
            ("kiln_heart", lambda s: s.pop("gem"), "gem None is not one of the eight element colours"),
            ("kiln_heart", lambda s: s.update(gem="frost"), "a fire focus with a frost gem"),
            ("blackforge_girdle", lambda s: s["parts"].update({k: s["parts"]["m"] for k, v in s["parts"].items()
                                                               if v == "netherite"}), "must be a second material"),
            ("kiln_heart", lambda s: s.update(tier="rare"), "tier rare, the catalogue says advanced"),
            ("lapis_lens", lambda s: s.update(tier="advanced"), "an advanced icon needs a gem part"),
            ("hale_locket", lambda s: s.update(name="Hale Pendant"), "the catalogue says 'Hale Locket'"),
            ("runebound_signet", lambda s: s.update(gem="lilac"), "not one of the eight element colours"),
        ]
        for aid, change, want in cases:
            src = _Sources()
            try:
                change(src.doc["icons"][aid])
                errors, _ = src.problems()
            finally:
                src.close()
            self.assertTrue(any(want in e for e in errors), "%s: %s not in %s" % (aid, want, errors))

    def test_a_missing_icon_is_an_error_and_adding_one_is_data_only(self):
        """A catalogue id without a spec fails the check; a spec (and a shape, when no shape fits) is all a new
        icon needs. A second look of an item (a growth stage, a hollow copy) names the item in `variant_of`."""
        src = _Sources()
        try:
            del src.doc["icons"]["tuff_ward"]
            errors, _ = src.problems()
            self.assertTrue(any(e.startswith("tuff_ward (Tuff Ward): no icon") for e in errors), errors)
            src.doc["icons"]["tuff_ward"] = copy.deepcopy(self.specs["tuff_ward"])
            hollow = copy.deepcopy(self.specs["wither_token"])
            hollow.update(name="Hollow Wither Token", what="the token gone grey", variant_of="wither_token")
            hollow["glow"] = "#8A8A8A"
            src.doc["icons"]["wither_token_hollow"] = hollow
            errors, _ = src.problems()
            self.assertEqual(errors, [])
            files = icons.build_files(src.src, src.out)
            self.assertIn(os.path.join(src.out, "wither_token_hollow.png"), files)
            hollow["variant_of"] = "wither_skull"
            errors, _ = src.problems()
            self.assertTrue(any("`variant_of` 'wither_skull'" in e for e in errors), errors)
            del hollow["variant_of"]
            errors, _ = src.problems()
            self.assertTrue(any("neither in the catalogue" in e for e in errors), errors)
        finally:
            src.close()

    def test_artifacts_under_other_headings_wait_for_their_icons(self):
        """An artifact under a catalogue heading that names no tier (say a future "Tinkered forms") without an icon
        is a NOTE, not an error; one under a tier heading (the revision's Growing and Engineered included) is an
        error."""
        src = _Sources()
        try:
            md = os.path.join(src.tmp, "ARTIFACTS.md")
            with open(icons.ARTIFACTS_MD, encoding="utf-8") as f:
                text = f.read()
            i = text.index("\n## ", text.index("The catalogue"))
            extra = ("\n### 7.97 Tinkered forms (1)\n| Artifact (id) | Recipe |\n|---|---|\n"
                     "| Ember Cord (`ember_cord`): a cord that chars | x |\n"
                     "### 7.98 Growing artifacts, late (1)\n| Rust Bell (`rust_bell`): it rusts | x |\n"
                     "### 7.99 Advanced, late (1)\n| Ash Pin (`ash_pin`) | x |\n")
            with open(md, "w", encoding="utf-8") as f:
                f.write(text[:i] + extra + text[i:])
            src.save()
            errors, notes = icons.problems(src.src, src.out, md, files=icons.build_files(src.src, src.out))
        finally:
            src.close()
        self.assertTrue(any("ember_cord" in n and "no icon yet" in n for n in notes), notes)
        self.assertFalse(any("ember_cord" in e for e in errors), errors)
        self.assertTrue(any(e.startswith("ash_pin (Ash Pin): no icon") for e in errors), errors)
        self.assertTrue(any(e.startswith("rust_bell (Rust Bell): no icon") for e in errors), errors)

    def test_catalogue_reads_tiers_by_heading_name(self):
        """The catalogue is read by its headings' names, not their numbers, so a renumbered or a longer
        ARTIFACTS.md still reads; a row under a heading that names no tier is known by id and name only."""
        text = "\n".join([
            "## 9. The catalogue", "",
            "### 9.1 Components (1, crafting table)", "| Artifact (id) | Recipe | Stats |", "|---|---|---|",
            "| Knapped Flint (`knapped_flint`) | 3 flint | attack +10% |",
            "### 9.2 Advanced (1)", "| Artifact (id) | Recipe | Stats |",
            "| Kiln Heart (`kiln_heart`) | S: Lapis Lens + Block of Coal | fire damage +12%, ability power +6% |",
            "### 9.3 Tinkered forms (2)", "| Kiln Heart, Stoked (`kiln_heart_stoked`) | a tinkerer | x |",
            "| Ember Cord (`ember_cord`): a cord that chars as it grows | Knapped Flint + Coal | `blood`: 300 |",
            "### 9.4 Signature artifacts (1)", "Designed there: **Hollow Star** (`hollow_star`, the Wither's).",
            "### 9.5 The build tree", "```", "Knapped Flint (`knapped_flint`) -> x", "```",
            "### 9.6 Growing artifacts (1)",
            "| Bloodglass Ampoule (`bloodglass_ampoule`): an ampoule that fills | S: x | `blood`: 300 |",
            "### 9.7 Engineered artifacts (1)", "| Observer Lens (`observer_lens`) | Bench | ability power +12% |",
            "## 10. Stacking", "| Not An Artifact (`stacking_row`) | x |"])
        cat = icons.catalogue(text=text)
        self.assertEqual(cat, {"knapped_flint": ("Knapped Flint", "component", None, None),
                               "kiln_heart": ("Kiln Heart", "advanced", "fire", None),
                               "kiln_heart_stoked": ("Kiln Heart, Stoked", None, None, None),
                               "ember_cord": ("Ember Cord", None, None, None),
                               "hollow_star": ("Hollow Star", "signature", None, None),
                               "bloodglass_ampoule": ("Bloodglass Ampoule", "advanced", None, "growing"),
                               "observer_lens": ("Observer Lens", "greater", None, "engineered")})
        self.assertEqual(cat["bloodglass_ampoule"].kind, "growing")
        self.assertEqual(cat["kiln_heart"].element, "fire")
        with self.assertRaises(util.HerogenError):
            icons.catalogue(text="## 7. Something else\n| A (`a`) | b |")

    def test_growing_artifacts_have_four_states(self):
        """ARTIFACTS.md 1.2: a growing artifact's icon has four states (empty, a third, two thirds, full), written as
        <id>.png and <id>_1.png .. <id>_3.png; each differs from the one before, and the full state is the look the
        sheet enlarges."""
        growing = sorted(k for k, v in self.specs.items() if v.get("growth"))
        self.assertEqual(growing, ["bloodglass_ampoule", "graveward_bell", "hearthroot_seed", "rivals_cord",
                                   "scarbark_amulet", "skyroad_pennant", "veinseeker_geode", "vigil_lantern"])
        for aid in growing:
            spec = self.specs[aid]
            self.assertEqual(icons.textures(aid, spec), [(aid, 0), (aid + "_1", 1), (aid + "_2", 2),
                                                         (aid + "_3", 3)])
            ims = [np.array(icons.image_of(aid, spec, self.shapes, self.materials, st)) for st in range(4)]
            for st in range(1, 4):
                self.assertFalse(np.array_equal(ims[st], ims[st - 1]), (aid, st))
            for st, (name, _) in enumerate(icons.textures(aid, spec)):
                png = self.files[os.path.join(icons.OUT_DIR, name + ".png")]
                self.assertTrue(np.array_equal(np.array(Image.open(io.BytesIO(png))), ims[st]), name)
            self.assertTrue(np.array_equal(ims[3], np.array(icons.image_of(aid, spec, self.shapes, self.materials))))
        self.assertEqual(icons.textures("kiln_heart", self.specs["kiln_heart"]), [("kiln_heart", None)])

    def test_stage_parts_resolve_by_state(self):
        """A stage part is drawn as its `else` below its stage and as its `as` (or itself) from it on; '.' is the
        empty canvas for either; a spec without growth draws every stage (its full look)."""
        grid = ["." * 16] * 6 + ["....mmmmmmmm...."] * 2 + ["....mAAAAAAm...."] * 2 + ["....mBBBBBBm...."] * 2 + \
            ["....CCCCCCCC...."] + ["....mmmmmmmm...."] + ["." * 16] * 2
        shapes = {"jar": {"parts": {"m": {"role": "body"}, "a": {"role": "accent"},
                                    "A": {"stage": 2, "else": "m", "as": "a"},
                                    "B": {"stage": 1, "else": "m", "as": "a"},
                                    "C": {"stage": 3, "else": "m", "as": "."}}, "grid": grid}}
        spec = {"tier": "component", "shape": "jar", "growth": True, "parts": {"m": "iron"}, "accent": "iron"}
        letters = [set(icons.resolve(spec, shapes, st)[0].flatten()) - {"."} for st in range(4)]
        self.assertEqual(letters, [{"m"}, {"m", "a"}, {"m", "a"}, {"m", "a"}])
        g1, g3 = icons.resolve(spec, shapes, 1)[0], icons.resolve(spec, shapes, 3)[0]
        self.assertEqual(int((g1 == "a").sum()), 12)
        self.assertEqual(int((g3 == "a").sum()), 24)
        self.assertEqual(int((g3 != ".").sum()), int((g1 != ".").sum()) - 8)     # C left the canvas at stage 3
        self.assertTrue(np.array_equal(icons.resolve(dict(spec, growth=None), shapes)[0], g3))
        lit = copy.deepcopy(shapes)                     # a highlight cut into the liquid, drawn before there is any
        lit["jar"]["parts"]["d"] = {"role": "detail", "of": "a", "tone": 2}
        lit["jar"]["grid"] = list(grid)
        lit["jar"]["grid"][11] = "....mBBBBBdm...."
        with self.assertRaises(util.HerogenError):
            icons.resolve(spec, lit, 0)
        self.assertIn("d", icons.resolve(spec, lit, 1)[1])
        for bad in ({"stage": 4, "else": "m"}, {"stage": 1, "else": "B"}, {"stage": 1, "else": "m", "as": "A"},
                    {"stage": 1, "else": "q"}, {"stage": 1, "else": "m", "optional": True}):
            broken = copy.deepcopy(shapes["jar"])
            broken["parts"]["C"] = bad
            with self.assertRaises(util.HerogenError, msg=bad):
                icons.check_shape("jar", broken)

    def test_growth_rules_catch_sabotaged_sources(self):
        """A growing artifact without states, states on an artifact that does not grow, a state that looks like the
        one before, a tier rule broken in one state only, and a state texture that is another icon's name all fail."""
        def drop_growth(src):
            del src.doc["icons"]["bloodglass_ampoule"]["growth"]

        def grow_flint(src):
            src.doc["icons"]["knapped_flint"]["growth"] = True

        def grow_kiln(src):
            spec = src.doc["icons"]["kiln_heart"]
            spec.update(growth=True, shape="ampoule", gem="fire", accent="blood",
                        parts={"m": "bloodglass", "s": "gold"})

        def flat_stage(src):
            def change(shapes):
                for p in shapes["ampoule"]["parts"].values():
                    if p.get("stage") == 2:
                        p["stage"] = 3
            src.edit_shapes(change)

        def late_gem(src):
            def change(shapes):
                shape = shapes["geode"]
                shape["parts"]["Z"] = {"stage": 1, "else": "h", "as": "g"}
                shape["grid"] = [r.replace("g", "Z").replace("q", "Z") for r in shape["grid"]]
            src.edit_shapes(change)

        def clash(src):
            twin = copy.deepcopy(src.doc["icons"]["bloodglass_ampoule"])
            del twin["growth"]
            twin["variant_of"] = "bloodglass_ampoule"
            src.doc["icons"]["bloodglass_ampoule_1"] = twin

        cases = [(drop_growth, "a growing artifact has four icon states"),
                 (grow_flint, "a growing artifact's shape needs stage parts"),
                 (grow_kiln, "the catalogue does not list it as a growing artifact"),
                 (flat_stage, "growth state 2 looks the same as state 1"),
                 (late_gem, "an advanced icon needs a gem part (ARTIFACTS.md 1.2) (growth state 0)"),
                 (clash, "is also the texture of the icon bloodglass_ampoule_1")]
        for change, want in cases:
            src = _Sources()
            try:
                change(src)
                errors, _ = src.problems()
            finally:
                src.close()
            self.assertTrue(any(want in e for e in errors), "%s: %s not in %s" % (change.__name__, want, errors))

    def test_painter_lights_from_the_top_left(self):
        """A bevelled square: its outline is the darkest tone, its inner top and left edges a tone lighter and its
        inner bottom and right edges a tone darker than its face; a domed disc is brightest up and left."""
        grid = ["." * 16] * 4 + ["....mmmmmmmm...."] * 8 + ["." * 16] * 4
        shapes = {"sq": {"parts": {"m": {"role": "body"}}, "grid": grid}}
        mats = {"iron": {"colour": "#8C8C8C"}}
        spec = {"tier": "component", "shape": "sq", "parts": {"m": "iron"}, "what": "x", "name": "x"}
        out = icons.paint("sq", spec, shapes, mats).astype(int)
        ramp = palette.ramp_n("#8C8C8C", icons.LO, icons.HI, step=0.09)
        tone = {tuple(c): i + icons.LO for i, c in enumerate(ramp)}
        t = np.array([[tone.get(tuple(out[y, x, :3]), 99) if out[y, x, 3] else 99 for x in range(16)]
                      for y in range(16)])
        self.assertTrue((t[4, 4:12] == icons.LO).all() and (t[4:12, 4] == icons.LO).all())    # outline
        self.assertEqual(t[5, 7], 1)                    # inner top edge
        self.assertEqual(t[7, 5], 1)                    # inner left edge
        self.assertEqual(t[10, 7], -1)                  # inner bottom edge
        self.assertEqual(t[7, 10], -1)                  # inner right edge
        self.assertEqual(t[7, 7], 0)                    # the face
        disc = ["................"] * 3 + ["......mmmm......", ".....mmmmmm.....", "....mmmmmmmm...."] + \
            ["....mmmmmmmm...."] * 4 + [".....mmmmmm.....", "......mmmm......"] + ["................"] * 4
        shapes["disc"] = {"parts": {"m": {"role": "body", "profile": "dome"}}, "grid": disc}
        out = icons.paint("disc", dict(spec, shape="disc"), shapes, mats).astype(int)
        lum = out[:, :, :3].sum(axis=2) * (out[:, :, 3] > 0)
        y, x = np.unravel_index(int(np.argmax(lum)), lum.shape)
        self.assertLess(x, 8)
        self.assertLess(y, 8)

    def test_ramp_n(self):
        r = palette.ramp_n("#4A494F", -3, 3, step=0.11)
        self.assertEqual(len(r), 7)
        self.assertEqual(r[3], palette.parse_hex("#4A494F"))
        lum = [palette.rgb_to_oklch(c)[0] for c in r]
        self.assertEqual(lum, sorted(lum))
        self.assertEqual(len(set(r)), 7)

    def test_cli(self):
        import cli
        self.assertEqual(cli.main(["icons", "--check"]), 0)


class IconSheetTest(unittest.TestCase):
    def test_sheet_is_fresh(self):
        """`herogen icons sheet --check`: the committed contact sheet (and the mock inventory, when the client jar
        is in the Gradle cache) match a fresh render."""
        from render import iconsheet
        problems, _ = iconsheet.check()
        self.assertEqual(problems, [])

    def test_inventory_uses_vanilla_textures_without_copying_them(self):
        from render import iconsheet
        shapes, materials, specs = icons.load()
        owners = icons.texture_owners(specs)
        for row in iconsheet.INVENTORY_ROWS:
            self.assertEqual(len(row), 9)
            self.assertTrue(any(k in owners for k in row) and any(k not in owners for k in row))
        shown = {owners[k][0] for row in iconsheet.INVENTORY_ROWS for k in row if k in owners}
        self.assertTrue({"bloodglass_ampoule", "observer_lens", "wither_token"} <= shown)
        root = os.path.join(util.ROOT, "src", "main", "resources", "assets")
        self.assertFalse(os.path.isdir(os.path.join(root, "minecraft")))
        for aid in specs:
            self.assertTrue(os.path.isfile(os.path.join(icons.OUT_DIR, aid + ".png")), aid)


if __name__ == "__main__":
    unittest.main()
