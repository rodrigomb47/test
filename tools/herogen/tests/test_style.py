"""Rig v3 style system (style pass 2): the parts library on every base, the style fixtures, and the part features the
style relies on (head-local offset, density, V regions, optional pieces, silhouette cutouts, hair as one mass, dense
rotated strands, distinct faces)."""
import glob
import io
import json
import os
import unittest

import numpy as np
from PIL import Image

from helpers import HEROGEN, mannequin

import build
import face as F
import lint
import validate
from geo import GeoModel, geo_json
from paint import Painter
from parts import apply_part, apply_weapon
from rig import BASE_IDS, Model
from util import HEROSRC
from uvpack import pack


def library():
    out = []
    for path in sorted(glob.glob(os.path.join(HEROSRC, "parts", "*", "*.json"))):
        pid = os.path.relpath(path, os.path.join(HEROSRC, "parts"))[:-5].replace(os.sep, "/")
        out.append(pid)
    return out


def checked(model):
    """Geometry problems of an assembled, packed model (no painting: a blank texture of the right size)."""
    geo = GeoModel(json.loads(json.dumps(geo_json(model))))
    return validate.check_geometry(geo, np.zeros((model.tex_h, model.tex_w, 4), dtype=np.uint8))


class LibraryTest(unittest.TestCase):
    def test_every_part_on_every_base(self):
        """herogen lint in miniature: each part alone (over the parts it requires) on each base assembles, packs and
        passes the geometry checks (budgets, UVs, the clear face), and its cubes stay above the ground."""
        for pid in library():
            for bid in BASE_IDS:
                with self.subTest(part=pid, base=bid):
                    m = Model("lint", bid)
                    if pid.startswith("weapons/"):
                        # G6c: carried as the part says (its hand, sheath, grip and mount)
                        apply_weapon(m, lint.weapon_entry(pid))
                    else:
                        # C1: a part is worn over the parts it `requires` (tails/cat_puff over tails/cat_long)
                        reqs = lint.required_entries(pid)
                        for i, r in enumerate(reqs):
                            apply_part(m, r, i)
                        apply_part(m, {"part": pid, "color": "primary", "trim": "trim", "alt": "secondary",
                                       "detail": "accent"}, len(reqs))
                    pack(m)
                    self.assertEqual(checked(m), [])
                    for c in m.cubes:
                        if c.rotation is None:
                            self.assertGreaterEqual(c.origin[1], -1e-9, "%s below the ground" % c.name)

    def test_head_parts_are_form_independent(self):
        """Head-anchored parts (offset head_y) put the same cubes at the same head-local place on every form."""
        for pid in [p for p in library() if p.split("/")[0] in ("hair", "ears", "headwear")]:
            local = {}
            for bid in ("chibi_f", "adult_m"):
                m = Model("t", bid)
                apply_part(m, {"part": pid, "color": "hair", "trim": "trim", "alt": "blush"}, 0)
                hy = m.env["head_y"]
                local[bid] = sorted((c.name, c.origin[0], round(c.origin[1] + c.size[1] - hy, 3), c.origin[2])
                                    for c in m.cubes if c.part == pid)
            # tops match (lengths may be clamped to keep long hair above a chibi's ankles)
            self.assertEqual(local["chibi_f"], local["adult_m"], pid)

    def test_hair_styles(self):
        """Every hair style is a dense mass of strands (style pass 2): at least 45 cubes, most of them rotated (fanned
        and flared, the references' main technique) and on fine_ bones (level of detail), at 1 texel per px, over a
        cap and a fringe in front of the forehead (z < -5 over y head_y + 8..10); no strand in front of the eyes
        (the clear face)."""
        styles = [p for p in library() if p.startswith("hair/")]
        self.assertGreaterEqual(len(styles), 7)
        for pid in styles:
            m = Model("t", "teen_f")
            apply_part(m, {"part": pid, "color": "hair", "trim": "trim"}, 0)
            hy = m.env["head_y"]
            cubes = [c for c in m.cubes if c.part == pid]
            self.assertTrue(all(c.density == 1 for c in cubes), pid)
            self.assertGreaterEqual(len(cubes), 45, "%s is a dense mass of strands" % pid)
            rotated = [c for c in cubes if c.rotation is not None]
            self.assertGreater(len(rotated), len(cubes) * 0.6, "%s: most strands are fanned or flared" % pid)
            fine = [c for c in cubes if c.bone.startswith("fine_")]
            self.assertGreater(len(fine), len(cubes) * 0.6, "%s: strands are level-of-detail" % pid)
            fringe = [c for c in cubes if c.box[2] < -5 and c.box[1] <= hy + 8.5 and c.box[4] >= hy + 10
                      and c.box[0] < -3 and c.box[3] > 3]
            self.assertTrue(fringe, "%s covers the forehead" % pid)
            self.assertTrue(any(c.bone == "hair_top" for c in cubes), pid)


class StyleFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ids = build.style_ids()
        cls.built = {hid: build.build_files(hid) for hid in cls.ids}

    def test_every_base_has_a_character(self):
        bases = {self.built[h][1].model.base_id for h in self.ids}
        self.assertEqual(bases, set(BASE_IDS), "an original character on each form, in each family")
        height = {}
        for _, b in self.built.values():
            height.setdefault(b.model.form, set()).add(b.model.env["head_top"])
        self.assertEqual({f: len(h) for f, h in height.items()}, {"chibi": 1, "teen": 1, "adult": 1})
        chibi, teen, adult = (min(height[f]) for f in ("chibi", "teen", "adult"))
        self.assertGreaterEqual(teen - chibi, 4, "the small forms are clearly shorter")
        self.assertGreaterEqual(adult - teen, 4)

    def test_fixtures_pass_the_asset_checks_and_budgets(self):
        for hid, (files, b) in self.built.items():
            with self.subTest(hero=hid):
                p = build.out_paths(hid)
                geo = GeoModel(json.loads(files[p["geo"]]))
                tex = np.array(Image.open(io.BytesIO(files[p["texture"]])).convert("RGBA"))
                self.assertEqual(validate.check_built(geo, tex, asymmetric=b.spec["asymmetric"]), [])
                self.assertLessEqual(len(b.model.bones), 100)
                self.assertLessEqual(len(b.model.cubes), 300)
                self.assertGreaterEqual(len(b.model.cubes), 110, "detail: well past rig v2's 54-76 cubes")
                self.assertLessEqual(validate.lod_cubes(geo), 130)
                self.assertGreaterEqual(sum(1 for c in b.model.cubes if c.rotation is not None), 50,
                                        "rotated strands, panels and plates")

    def test_faces_are_distinct(self):
        """The owner's 'their faces are quite similar': every pair of fixtures differs in at least five face features
        (eye shape, iris style, pupil, highlights, lash, flick, spacing, brows, mouth, blush, marks, wear, iris hue)
        and no two share an eye shape or a mouth."""
        specs = {hid: b.spec for hid, (_, b) in self.built.items()}
        ids = sorted(specs)
        for i, a in enumerate(ids):
            for c in ids[i + 1:]:
                with self.subTest(a=a, b=c):
                    self.assertGreaterEqual(F.distinct(specs[a], specs[c]), 5)
        self.assertEqual(len({s["eyes"] for s in specs.values()}), len(ids))
        self.assertEqual(len({s["mouth"] for s in specs.values()}), len(ids))

    def test_fixtures_are_deterministic_and_never_shipped(self):
        for hid in self.ids:
            again, _ = build.build_files(hid)
            self.assertEqual(again, self.built[hid][0], hid)
            self.assertNotIn(hid, build.hero_ids(), "style fixtures are not written to the mod's resources")
            self.assertTrue(build.hero_path(hid).endswith(os.path.join("style", hid + ".hero.json")))


class FeatureTest(unittest.TestCase):
    def test_offset_and_density(self):
        part = {"part": {"category": "test", "slots": {"main": "hair"}, "offset": [0, "head_y", 0], "density": 2,
                         "cubes": [{"bone": "bangs", "origin": [-1, 8, -6], "size": [2, 2, 1], "material": "hair"},
                                   {"bone": "bangs", "origin": [1, 8, -6], "size": [2, 2, 1], "density": 1}]}}
        m = Model("t", "chibi_m")
        apply_part(m, part, 0)
        a, b = m.cube("inline/0#0"), m.cube("inline/0#1")
        self.assertEqual(a.origin, (-1.0, 8.0 + m.env["head_y"], -6.0))
        self.assertEqual((a.density, b.density), (2, 1))
        pack(m)
        self.assertEqual(a.uv["north"][2:], (4, 4))
        self.assertEqual(b.uv["north"][2:], (2, 2))

    def test_skip_if_empty(self):
        m = Model("t", "teen_f")
        apply_part(m, {"part": "hair/twin_tails", "params": {"ahoge": 0}}, 0)
        self.assertFalse(any(c.bone == "ahoge" for c in m.cubes))
        m = Model("t", "teen_f")
        apply_part(m, {"part": "hair/twin_tails", "params": {"ahoge": 1}}, 0)
        self.assertTrue(any(c.bone == "ahoge" for c in m.cubes))

    def test_vee_neckline(self):
        hero = mannequin("adult_f", parts=[{"part": "outfit_top/kimono_wide", "color": "primary", "alt": "secondary",
                                            "trim": "trim"}], palette={"primary": "#2E9E92", "secondary": "#F3EADA",
                                                                       "trim": "#E4B24A"})
        b = build.build(hero)
        c = b.model.cube("chest")
        u, v, w, h = c.uv["north"]
        px = b.image[v:v + h, u:u + w]
        alt = {tuple(t) for t in b.ramps["secondary"]}
        top = [tuple(p[:3]) in alt for p in px[0]]
        bottom = [tuple(p[:3]) in alt for p in px[-1]]
        self.assertTrue(any(top) and sum(top) > sum(bottom), "the V is wide at the neck and narrows downward")
        self.assertTrue(top[w // 2] and not top[0] and not top[-1], "centred, with the garment at the sides")

    def test_peak_ears_have_no_side_strips(self):
        """A cut-out ear is cut through its front: its side faces only keep where the front keeps its edge column."""
        b = build.build(mannequin("teen_f", parts=[{"part": "ears/round", "color": "hair", "alt": "blush"}]))
        ear = next(c for c in b.model.cubes if c.bone == "ear_r" and c.cutout == "round_top" and c.size[2] > 1)
        for face in ("east", "west"):
            _, _, W, H = ear.uv[face]
            self.assertFalse(Painter.keep(ear, face, 0, 0, W, H), "no full-height strip on the %s side" % face)
            self.assertTrue(Painter.keep(ear, face, 0, H - 1, W, H))
        _, _, W, H = ear.uv["north"]
        self.assertTrue(Painter.keep(ear, "north", W // 2, 0, W, H), "the tip")
        self.assertFalse(Painter.keep(ear, "north", 0, 0, W, H))
        self.assertTrue(all(Painter.keep(ear, "north", i, H - 1, W, H) for i in range(W)), "a full-width base")

    def test_hair_cubes_are_one_mass(self):
        """A strand resting on the cap is not outlined against it (the same hair region); a free tip is."""
        hero = mannequin("adult_f", parts=[{"part": {"category": "test", "slots": {"main": "hair"}, "density": 2,
                                                     "offset": [0, "head_y", 0], "cubes": [
            {"name": "cap", "bone": "hair_top", "origin": [-5.5, 6, -5.5], "size": [11, 5, 11], "material": "hair"},
            {"name": "tuft", "bone": "hair_top", "origin": [-2, 11, -2], "size": [4, 1, 4], "material": "hair"},
            {"name": "lock", "bone": "hair_r", "origin": [-7, 2, -2], "size": [1, 4, 1], "material": "hair"}]}}],
            palette={"hair": "#B9532C"})
        b = build.build(hero)
        ramp = [tuple(c) for c in b.ramps["hair"]]

        def tones(name):
            c = b.model.cube("inline/0#" + name)
            u, v, w, h = c.uv["north"]
            return [ramp.index(tuple(p[:3])) - 2 for p in b.image[v + h - 1, u:u + w] if tuple(p[:3]) in ramp]
        self.assertNotIn(-2, tones("tuft"), "no outline where the tuft sits on the cap")
        self.assertTrue(all(t <= -1 for t in tones("lock")), "a free lock tip is dark")


if __name__ == "__main__":
    unittest.main()
