"""Negative tests: each deliberately broken asset must fail with a clear message."""
import copy
import io
import json
import unittest

import numpy as np
from PIL import Image

from helpers import HEROGEN  # noqa: F401

import build
import face as F
import validate
from geo import GeoModel
from rig import load_rig


class ValidateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        files, cls.b = build.build_files("test_rig")
        paths = build.out_paths("test_rig")
        cls.geo_data = json.loads(files[paths["geo"]])
        cls.tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))

    def problems(self, geo_data=None, tex=None):
        return validate.check_built(GeoModel(geo_data or self.geo_data), self.tex if tex is None else tex)

    def bones(self, data):
        return data["minecraft:geometry"][0]["bones"]

    def assertProblem(self, text, problems):
        self.assertTrue(any(text in p for p in problems), "expected %r in %s" % (text, problems))

    def test_clean(self):
        self.assertEqual(self.problems(), [])

    def test_fractional_uv(self):
        d = copy.deepcopy(self.geo_data)
        c = next(b for b in self.bones(d) if b["name"] == "chest")["cubes"][0]
        c["uv"]["north"]["uv_size"][0] = 6.5
        self.assertProblem("fractional UV", self.problems(d))

    def test_overlapping_islands(self):
        d = copy.deepcopy(self.geo_data)
        bones = self.bones(d)
        chest = next(b for b in bones if b["name"] == "chest")["cubes"][0]
        waist = next(b for b in bones if b["name"] == "waist")["cubes"][0]
        waist["uv"]["north"]["uv"] = list(chest["uv"]["north"]["uv"])
        self.assertProblem("overlaps", self.problems(d))

    def test_uv_outside_texture(self):
        d = copy.deepcopy(self.geo_data)
        next(b for b in self.bones(d) if b["name"] == "chest")["cubes"][0]["uv"]["north"]["uv"] = [254, 126]
        self.assertProblem("leaves the texture", self.problems(d))

    def test_missing_required_bone(self):
        d = copy.deepcopy(self.geo_data)
        d["minecraft:geometry"][0]["bones"] = [b for b in self.bones(d) if b["name"] != "vfx_feet"]
        self.assertProblem("required bone vfx_feet", self.problems(d))

    def test_over_cube_budget(self):
        d = copy.deepcopy(self.geo_data)
        chest = next(b for b in self.bones(d) if b["name"] == "chest")
        chest["cubes"] = chest["cubes"] * 301
        self.assertProblem("cubes (budget 300)", self.problems(d))

    def test_over_bone_budget(self):
        d = copy.deepcopy(self.geo_data)
        for i in range(60):
            self.bones(d).append({"name": "extra_%d" % i, "parent": "root", "pivot": [0, 0, 0]})
        self.assertProblem("bones (budget 100)", self.problems(d))

    def test_parent_order_and_names(self):
        d = copy.deepcopy(self.geo_data)
        bones = self.bones(d)
        bones.append(bones.pop(0))          # root last
        bones.append({"name": "Bad-Name", "parent": "root", "pivot": [0, 0, 0]})
        p = self.problems(d)
        self.assertProblem("comes before its parent root", p)
        self.assertProblem("lower_snake_case", p)

    def test_plane_off_its_region(self):
        d = copy.deepcopy(self.geo_data)
        next(b for b in self.bones(d) if b["name"] == "eyes")["cubes"][0]["uv"]["north"]["uv"] = [84, 0]
        self.assertProblem("face plane eyes", self.problems(d))

    def test_texture_size_mismatch(self):
        d = copy.deepcopy(self.geo_data)
        d["minecraft:geometry"][0]["description"]["texture_height"] = 256
        self.assertProblem("geometry says 256x256", self.problems(d))

    def test_missing_eye_frame(self):
        tex = self.tex.copy()
        u, v, w, h = F.frame_rect(load_rig(), "eyes", 5)
        tex[v:v + h, u:u + w] = 0
        self.assertProblem("eyes frame 5 is not fully opaque", self.problems(tex=tex))

    def test_blank_or_duplicate_eye_frame(self):
        tex = self.tex.copy()
        rig = load_rig()
        u, v, w, h = F.frame_rect(rig, "eyes", 7)
        tex[v:v + h, u:u + w] = tex[v + h - 1, u]          # blank skin
        p = self.problems(tex=tex)
        self.assertProblem("eyes frame 7 is blank", p)
        tex = self.tex.copy()
        u2, v2, _, _ = F.frame_rect(rig, "eyes", 0)
        tex[v:v + h, u:u + w] = tex[v2:v2 + h, u2:u2 + w]
        self.assertProblem("eyes frames 0 and 7 are identical", self.problems(tex=tex))

    def test_unmirrored_eyes(self):
        tex = self.tex.copy()
        u, v, _, _ = F.frame_rect(load_rig(), "eyes", 0)
        tex[v + 6, u + 3] = (255, 0, 0, 255)
        self.assertProblem("eyes frame 0: the eyes do not mirror", self.problems(tex=tex))

    def test_head_front_without_holes(self):
        tex = self.tex.copy()
        hu, hv, _, _ = load_rig()["uv_template"]["head_front"]
        tex[hv + 10, hu + 4] = (200, 150, 120, 255)          # face row 10 is under the eyes plane
        self.assertProblem("not transparent under the eyes and mouth planes", self.problems(tex=tex))

    def test_mouth_edge_must_match_the_head_front(self):
        tex = self.tex.copy()
        u, v, _, _ = F.frame_rect(load_rig(), "mouth", 3)
        tex[v + 1, u + 7] = (90, 30, 40, 255)          # paint into the mouth plane's edge column
        self.assertProblem("mouth frame 3 differs from the head front", self.problems(tex=tex))

    def test_cube_in_front_of_the_face(self):
        head_y = 42           # the test rig is a rig v3 adult: bangs must stop at head_y + 5.5
        d = copy.deepcopy(self.geo_data)
        bangs = next(b for b in self.bones(d) if b["name"] == "bangs")
        long_bangs = copy.deepcopy(bangs["cubes"][0])
        long_bangs.pop("rotation", None)
        long_bangs.pop("pivot", None)
        long_bangs["origin"] = [-1, head_y + 4, -5.6]
        long_bangs["size"] = [2, 3, 0.5]
        bangs["cubes"].append(long_bangs)
        self.assertProblem("in front of the face below y 47.5", self.problems(d))

    def test_rotated_cube_in_front_of_the_face(self):
        """The clear face is tested on rotated corners, with GeckoLib's sense of rotation: a long strand inside the
        head (behind the face) whose tip swings forward (file rotation x negative) comes out in front of the eyes and
        is caught; swung backward (x positive) it stays behind the face."""
        head_y = 42
        d = copy.deepcopy(self.geo_data)
        bangs = next(b for b in self.bones(d) if b["name"] == "bangs")
        strand = copy.deepcopy(bangs["cubes"][0])
        strand["origin"] = [-0.5, head_y + 2, -4.5]
        strand["size"] = [1, 8, 0.25]
        strand["pivot"] = [0, head_y + 10, -4.5]
        bangs["cubes"] = [strand]
        strand["rotation"] = [20, 0, 0]
        self.assertFalse(any("front of the face" in p for p in validate._check_clear_face(GeoModel(d), load_rig())),
                         "swung back, it stays behind the face")
        strand["rotation"] = [-20, 0, 0]
        self.assertProblem("in front of the face", validate._check_clear_face(GeoModel(d), load_rig()))

    def test_lod_budget(self):
        """At most budgets.lod_cubes cubes outside fine_ bones."""
        d = copy.deepcopy(self.geo_data)
        chest = next(b for b in self.bones(d) if b["name"] == "chest")
        chest["cubes"] = chest["cubes"] * 131
        self.assertProblem("cubes outside fine_ bones (budget 130)", self.problems(d))
        self.assertLessEqual(validate.lod_cubes(GeoModel(self.geo_data)), load_rig()["budgets"]["lod_cubes"])

    def test_open_cap_and_side_locks_are_fine(self):
        """Faces, not boxes: a cap without its north face may enclose the head, and a lock beside the face (|x| > 4.5)
        may hang below the lash line."""
        head_y = 42
        d = copy.deepcopy(self.geo_data)
        top = next(b for b in self.bones(d) if b["name"] == "hair_top")
        cap = {"origin": [-5.5, head_y + 2, -5.5], "size": [11, 9, 11],
               "uv": {f: {"uv": [0, 100], "uv_size": [1, 1]} for f in ("up", "east", "west", "south")}}
        top["cubes"] = [cap]
        self.assertFalse(any("front of the face" in p for p in validate._check_clear_face(GeoModel(d), load_rig())))
        cap["uv"]["north"] = {"uv": [0, 100], "uv_size": [1, 1]}
        self.assertProblem("in front of the face", validate._check_clear_face(GeoModel(d), load_rig()))


if __name__ == "__main__":
    unittest.main()
