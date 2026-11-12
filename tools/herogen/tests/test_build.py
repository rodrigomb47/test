"""The G1 acceptance: a test rig with face frames builds byte-identically twice, and every committed output matches a
fresh build."""
import hashlib
import io
import json
import os
import subprocess
import sys
import unittest

import numpy as np
from PIL import Image

from helpers import HEROGEN, mannequin

import build
import validate
from geo import GeoModel
from rig import BASE_IDS
from util import ROOT


def digest(files):
    return {k: hashlib.sha256(v).hexdigest() for k, v in sorted(files.items())}


def png_chunks(data):
    out, pos = [], 8
    while pos < len(data):
        n = int.from_bytes(data[pos:pos + 4], "big")
        out.append(data[pos + 4:pos + 8].decode("ascii"))
        pos += 12 + n
    return out


class BuildTest(unittest.TestCase):
    def test_test_rig_builds_byte_identically_twice(self):
        a, _ = build.build_files("test_rig")
        b, _ = build.build_files("test_rig")
        self.assertEqual(digest(a), digest(b))
        self.assertGreaterEqual(len(a), 6)

    def test_identical_across_processes(self):
        code = ("import sys, json, hashlib; sys.path.insert(0, %r); import build; f, _ = build.build_files('test_rig');"
                "print(json.dumps({k: hashlib.sha256(v).hexdigest() for k, v in sorted(f.items())}))") % HEROGEN
        runs = []
        for seed in ("1", "2"):
            env = dict(os.environ, PYTHONHASHSEED=seed)
            out = subprocess.run([sys.executable, "-c", code], env=env, capture_output=True, text=True, check=True)
            runs.append(json.loads(out.stdout))
        self.assertEqual(runs[0], runs[1])

    def test_committed_outputs_are_fresh(self):
        for hid in build.hero_ids():
            with self.subTest(hero=hid):
                files, _ = build.build_files(hid)
                self.assertEqual(build.check_files(hid, files, ROOT), [])

    def test_check_detects_a_stale_file(self):
        files, _ = build.build_files("test_rig")
        path = build.out_paths("test_rig")["geo"]
        files[path] = files[path] + b" "
        self.assertTrue(any("differs" in p for p in build.check_files("test_rig", files, ROOT)))

    def test_outputs_pass_the_asset_checks(self):
        heroes = [(hid, None) for hid in build.hero_ids()] + [(m["id"], m) for m in map(mannequin, BASE_IDS)]
        for hid, hero in heroes:
            with self.subTest(hero=hid):
                files, b = build.build_files(hid, hero)
                paths = build.out_paths(hid)
                geo = GeoModel(json.loads(files[paths["geo"]]))
                tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
                self.assertEqual(validate.check_built(geo, tex), [])

    def test_test_rig_outputs(self):
        files, b = build.build_files("test_rig")
        p = build.out_paths("test_rig")
        for key in ("geo", "texture", "glowmask", "p2", "p2_glowmask", "animation", "visual"):
            self.assertIn(p[key], files, key)
        for key in ("texture", "glowmask", "p2"):
            self.assertEqual(png_chunks(files[p[key]]), ["IHDR", "IDAT", "IEND"], "no metadata in %s" % key)
        anim = json.loads(files[p["animation"]])
        self.assertEqual(anim, {"format_version": "1.8.0", "animations": {}})
        vis = json.loads(files[p["visual"]])
        self.assertEqual(vis["bones"]["tint"], ["fine_sash_knot", "sash"])
        self.assertEqual(vis["bones"]["phase"], {"p2_halo": 2})
        self.assertEqual(len(vis["face"]["moods"]), 12)
        self.assertEqual(vis["texture"]["width"], 256)
        self.assertEqual((vis["form"], vis["height_px"], vis["head_y"]), ("adult", 33, 23))
        self.assertTrue(vis["texture"]["glow_outside_face"], "the halo glows")
        self.assertEqual(vis["phases"][0]["texture"], "p2")
        geo = json.loads(files[p["geo"]])
        self.assertEqual(geo["format_version"], "1.12.0")
        text = files[p["geo"]].decode()
        self.assertNotIn(".0,", text, "whole numbers are written without .0")
        # the phase-2 palette only recolours the eyes: everything outside the eye frames is unchanged
        base = np.array(Image.open(io.BytesIO(files[p["texture"]])))
        p2 = np.array(Image.open(io.BytesIO(files[p["p2"]])))
        diff = np.argwhere((base != p2).any(axis=2))
        self.assertTrue(len(diff) > 0)
        self.assertTrue(((diff[:, 0] < 30) & (diff[:, 1] >= 64) & (diff[:, 1] < 144)).all(), "only the eye frames change")

    def test_geometry_numbers_are_rounded(self):
        files, _ = build.build_files("test_rig")
        geo = json.loads(files[build.out_paths("test_rig")["geo"]])

        def walk(o):
            if isinstance(o, float):
                self.assertEqual(o, round(o, 3))
            elif isinstance(o, dict):
                for v in o.values():
                    walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        walk(geo)

    def test_hero_source_errors(self):
        from util import HerogenError
        hero = mannequin("teen_f")
        for mutate in (lambda h: h["art"].update(build="huge"), lambda h: h["art"].update(base="robot"),
                       lambda h: h["art"].update(form="giant"),
                       lambda h: h["art"].update(base_layer={"shirt": "primary"})):
            h = json.loads(json.dumps(hero))
            mutate(h)
            with self.assertRaises(HerogenError):
                build.build(h)


if __name__ == "__main__":
    unittest.main()
