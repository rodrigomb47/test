import unittest

import numpy as np

from helpers import HEROGEN, mannequin  # noqa: F401

import build
import palette as P
from paint import Painter


def luma(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def cube_named(model, name):
    """A cube by its name, or by the name a part gave it (parts prefix their cube names with the part id)."""
    for c in model.cubes:
        if c.name == name or c.name.endswith("#" + name):
            return c
    raise KeyError(name)


def face_pixels(b, cube_name, face):
    c = cube_named(b.model, cube_name)
    u, v, w, h = c.uv[face]
    return b.image[v:v + h, u:u + w]


class PaintTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        hero = mannequin("adult_f", parts=[
            {"part": {"category": "test", "slots": {"main": "primary"}, "cubes": [
                {"name": "lock", "bone": "hair_r", "origin": [-6, 20, -1], "size": [1, 6, 1], "slot": "hair",
                 "material": "hair"},
                {"name": "jag", "bone": "hair_l", "origin": [6, 20, -1], "size": [4, 6, 1], "slot": "hair",
                 "material": "hair", "cutout": "jagged_2"},
                {"name": "box", "bone": "hat", "origin": [-3, 40, -3], "size": [6, 6, 6], "slot": "primary",
                 "material": "cloth"}]}}],
            palette={"primary": "#5C6B8A", "hair": "#34466F"})
        cls.b = build.build(hero)

    def test_light_by_face(self):
        def mean(face):
            px = face_pixels(self.b, "box", face)[:2]       # top rows: no lower-third band
            return np.mean([luma(p) for p in px.reshape(-1, 4)])
        self.assertGreater(mean("up"), mean("north"))
        self.assertGreater(mean("west"), mean("east"), "light from the hero's front-left: left side brighter")
        self.assertGreater(mean("north"), mean("down"))

    def test_ramps_only(self):
        ramps = P.resolve({"primary": "#5C6B8A", "hair": "#34466F"})
        allowed = {tuple(c) for c in ramps["primary"]}
        for p in face_pixels(self.b, "box", "north").reshape(-1, 4):
            self.assertIn(tuple(p[:3]), allowed)

    def test_hem_outline(self):
        px = face_pixels(self.b, "lock", "north")
        self.assertLess(luma(px[-1, 0]), luma(px[2, 0]), "a free hair edge gets a darker outline row")

    def test_ao_under_the_sleeve(self):
        px = face_pixels(self.b, "hand_r", "north")
        self.assertLess(luma(px[0, 0]), luma(px[1, 0]), "the hand's top row sits in the forearm's shadow")

    def test_cutout(self):
        px = face_pixels(self.b, "jag", "north")
        self.assertTrue((px[0, :, 3] == 255).all())
        self.assertTrue((px[-1, :, 3] == 0).any(), "jagged bottom edge")
        self.assertTrue((px[-1, :, 3] == 255).any(), "tooth tips stay")

    def test_skin_never_outlined(self):
        ramps = P.resolve({})
        skin = {tuple(c) for c in ramps["skin"][1:]}
        for p in face_pixels(self.b, "hand_r", "north")[1:].reshape(-1, 4):
            self.assertIn(tuple(p[:3]), skin)


if __name__ == "__main__":
    unittest.main()


def tone_of(ramp, px):
    """Index -2..2 of a pixel on a ramp."""
    return [tuple(c) for c in ramp].index(tuple(px[:3])) - 2


class ShadingRulesTest(unittest.TestCase):
    """Review fixes: a garment is shaded once (not once per body cube), occlusion only between regions, soft edges where
    cloth rests on the body, cutouts carried to every face of the cube, outlines on cut tips."""

    @classmethod
    def setUpClass(cls):
        hero = mannequin("adult_f", parts=[
            {"part": {"category": "test", "slots": {"main": "primary"}, "cubes": [
                {"name": "panel", "bone": "cape_1", "origin": [-3, 14, 4], "size": [6, 7, 0.5], "slot": "primary",
                 "material": "cloth"},
                {"name": "ear", "bone": "ear_r", "origin": [-4, 32.25, -1.25], "size": [3, 3, 1], "slot": "hair",
                 "material": "hair", "cutout": "tri_up"},
                {"name": "fringe", "bone": "bangs", "origin": [-4, 40, -5], "size": [8, 4, 1], "slot": "hair",
                 "material": "hair", "cutout": "jagged_3"},
                {"name": "lock", "bone": "hair_back", "origin": [-1, 40, 6], "size": [3, 6, 3], "slot": "hair",
                 "material": "hair", "taper": 0.4}]}}],
            palette={"primary": "#5C6B8A", "hair": "#34466F", "secondary": "#8E96AA"})
        cls.b = build.build(hero)
        cls.ramps = cls.b.ramps

    def px(self, cube, face):
        return face_pixels(self.b, cube, face)

    def test_one_garment_is_not_striped(self):
        chest = self.px("chest", "south")                  # continues into the waist below: no band on its last row
        self.assertTrue((chest[-1] == chest[2]).all(), "no lower-third band where the garment continues")
        waist = self.px("waist", "south")                  # under the wider chest, but the same garment: no occlusion
        self.assertTrue((waist[0] == waist[1]).all(), "no occlusion row inside one garment")
        shin = self.px("shin_r", "south")
        self.assertTrue((shin[0] == shin[1]).all(), "no occlusion at the knee inside one garment")

    def test_band_where_the_garment_ends(self):
        panel = self.px("panel", "south")                  # 7 rows hanging free: rows 5-6 are the lower third
        ramp = self.ramps["primary"]
        cols = [i for i in range(panel.shape[1]) if tone_of(ramp, panel[1, i]) == tone_of(ramp, panel[4, i]) == 0]
        self.assertTrue(cols, "a column without a fold line")
        i = cols[0]
        self.assertEqual(tone_of(ramp, panel[5, i]), -1, "lower third one tone darker")
        self.assertEqual(tone_of(ramp, panel[6, i]), -2, "hem outline at the darkest tone")

    def test_cloth_resting_on_the_body_gets_a_soft_edge(self):
        bust = self.px("bust", "north")
        ramp = self.ramps["secondary"]
        self.assertEqual(tone_of(ramp, bust[-2, 1]) - tone_of(ramp, bust[-1, 1]), 1,
                         "the bust's lower edge is a one-tone fold, not a two-tone hem line")

    def test_cutout_carries_to_every_face(self):
        def keep(name, face, i, j):
            c = cube_named(self.b.model, name)
            _, _, W, H = c.uv[face]
            return Painter.keep(c, face, i, j, W, H)
        ear_up = self.px("ear", "up")
        self.assertEqual(list(ear_up[0, :, 3] > 0), [False, True, False], "the ear's top is only its tip")
        ear_side = self.px("ear", "east")
        self.assertFalse(ear_side[0, 0, 3], "the thin ear's narrow face follows the triangle (no full-height strip)")
        self.assertTrue(ear_side[-1, 0, 3])
        fringe_n, fringe_d = self.px("fringe", "north"), self.px("fringe", "down")
        for i in range(fringe_n.shape[1]):
            self.assertEqual(bool(fringe_d[0, i, 3]), bool(fringe_n[-1, i, 3]), "fringe bottom column %d" % i)
        self.assertTrue((fringe_d[:, :, 3] == 0).any() and (fringe_d[:, :, 3] > 0).any())
        lock_d = self.px("lock", "down")
        self.assertFalse(lock_d[0, 0, 3], "the tapered lock has no full-size flat bottom")
        self.assertTrue(lock_d[1, 1, 3])
        self.assertTrue(keep("lock", "north", 1, 0))

    def test_cut_tips_are_outlined(self):
        fringe = self.px("fringe", "north")
        ramp = self.ramps["hair"]
        H = fringe.shape[0]
        cut_cols = [i for i in range(fringe.shape[1]) if not fringe[-1, i, 3]]
        self.assertTrue(cut_cols)
        for i in cut_cols:
            last = max(j for j in range(H) if fringe[j, i, 3])
            self.assertLess(luma(fringe[last, i]), luma(fringe[last - 1, i]) + 1e-9, "column %d tip" % i)
            self.assertIn(tone_of(ramp, fringe[last, i]), (-2,), "column %d tip is outlined" % i)
