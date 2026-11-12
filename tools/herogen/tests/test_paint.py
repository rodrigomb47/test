import unittest

import numpy as np

from helpers import HEROGEN, mannequin  # noqa: F401

import build
import palette as P


def luma(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def face_pixels(b, cube_name, face):
    c = b.model.cube(cube_name)
    u, v, w, h = c.uv[face]
    return b.image[v:v + h, u:u + w]


class PaintTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        hero = mannequin("f_standard", parts=[
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
