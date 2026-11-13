import unittest

from helpers import HEROGEN  # noqa: F401

import build
from parts import apply_part, apply_weapon
from rig import Model
from util import HerogenError


def inline(cubes=None, bones=None, **kw):
    p = {"category": "test", "slots": {"main": "primary"}, "cubes": cubes or [], "bones": bones or []}
    p.update(kw)
    return {"part": p}


BOX = {"origin": [-1, 10, -1], "size": [1, 1, 1]}


class PartsTest(unittest.TestCase):
    def test_mirror_and_bones(self):
        m = Model("t", "adult_f")
        apply_part(m, inline(bones=[{"name": "lock_r", "parent": "head", "pivot": [-3, 30, 0]}],
                             cubes=[dict(BOX, bone="lock_r", mirror=True), dict(BOX, bone="ear_r", mirror=True)]), 0)
        r, l = m.cube("inline/0#0"), m.cube("inline/0#0:l")
        self.assertEqual((r.bone, l.bone), ("lock_r", "lock_l"))
        self.assertEqual(l.origin, (0.0, 10.0, -1.0))
        self.assertEqual(m.bones["lock_l"].pivot, (3.0, 30.0, 0.0), "declared bones mirror on demand")
        self.assertEqual(m.cube("inline/0#1:l").bone, "ear_l", "rig bones mirror by name")
        self.assertIn("ear_r", m.bones)

    def test_phase_and_fine_bones(self):
        m = Model("t", "adult_f")
        apply_part(m, dict(inline(bones=[{"name": "tail_b_1", "parent": "body", "pivot": [0, 14, 2]}],
                                  cubes=[dict(BOX, bone="tail_b_1"), dict(BOX, bone="halo")]), phase=2), 0)
        self.assertIn("p2_tail_b_1", m.bones, "a phase part's own bones are renamed")
        self.assertNotIn("tail_b_1", m.bones)
        self.assertEqual(m.bones["p2_halo"].parent, "halo", "rig bones get a nested phase bone")
        self.assertEqual(m.bones["p2_halo"].pivot, m.bones["halo"].pivot)
        apply_part(m, inline(cubes=[dict(BOX, bone="hat", fine=True)]), 1)
        self.assertEqual(m.cube("inline/1#0").bone, "fine_hat")
        self.assertEqual(m.phase_bones(), {"p2_tail_b_1": 2, "p2_halo": 2})
        self.assertIn("fine_hat", m.fine_bones())

    def test_params_and_expressions(self):
        m = Model("t", "adult_m")
        part = inline(cubes=[{"bone": "chest", "origin": ["-chest_w / 2", 18, 0], "size": ["length", 1, 1]}],
                      params={"length": {"min": 1, "max": 5, "default": 2}})
        apply_part(m, dict(part, params={"length": 4}), 0)
        c = m.cube("inline/0#0")
        self.assertEqual(c.origin[0], -4.5)
        self.assertEqual(c.size[0], 4.0)
        for bad in ({"length": 9}, {"width": 1}):
            with self.assertRaises(HerogenError):
                apply_part(Model("t", "adult_m"), dict(part, params=bad), 0)

    def test_errors(self):
        cases = [
            inline(cubes=[dict(BOX, bone="tentacle")]),
            inline(cubes=[dict(BOX, bone="head", slot="plaid_red")]),
            inline(cubes=[dict(BOX, bone="head", material="velvet")]),
            inline(cubes=[dict(BOX, bone="head", cutout="lace_zz")]),
            inline(cubes=[dict(BOX, bone="head", rotation=[0, 10, 0])]),
            inline(paint=[{"cubes": ["no_such_cube"], "slot": "primary"}]),
            inline(bones=[{"name": "tail_1", "parent": "head", "pivot": [0, 0, 0]}]),
            {"part": "weapons/sword_plain"},
            {"part": "hair/does_not_exist"},
        ]
        for i, entry in enumerate(cases):
            with self.subTest(i=i), self.assertRaises(HerogenError):
                apply_part(Model("t", "adult_f", "slim"), entry, 0)

    def test_tags(self):
        m = Model("t", "adult_f", "slim")
        apply_part(m, {"part": "hair/high_ponytail_long"}, 0)
        with self.assertRaises(HerogenError):
            apply_part(m, {"part": "hair/short_crop"}, 1)          # both occupy hair_top

    def test_weapon_in_hand_and_sheathed(self):
        m = Model("t", "adult_f")
        apply_weapon(m, {"prop": "weapons/sword_plain", "hand": "right", "sheathed": "hip"})
        hand = [c for c in m.cubes if c.bone == "weapon_r"]
        sheath = [c for c in m.cubes if c.bone == "weapon_back"]
        self.assertEqual(len(hand), 4)
        self.assertEqual(len(sheath), 4, "hilt cubes plus the scabbard")
        pv = m.bones["weapon_r"].pivot
        grip = hand[0]
        self.assertAlmostEqual(grip.origin[0] + grip.size[0] / 2, pv[0])
        tip = m.bones["vfx_tip_r"].pivot
        self.assertAlmostEqual(tip[2], pv[2] - 15.25, msg="tip locator at the blade end (default blade 13)")
        for c in sheath:
            self.assertEqual(c.rotation, (55.0, 0.0, 0.0))
            self.assertGreater(c.pivot[0], 0, "a right-hand sword hangs on the left hip (+x)")
        self.assertEqual(m.bones["weapon_back"].parent, "body", "a hip scabbard follows the hips, not the chest")
        m2 = Model("t", "adult_f")
        apply_weapon(m2, {"prop": "weapons/sword_plain", "hand": "pair", "sheathed": "back"})
        self.assertEqual(m2.bones["weapon_back"].parent, "back", "a back carry follows the chest")
        self.assertEqual(len([c for c in m2.cubes if c.bone == "weapon_l"]), 4)
        self.assertEqual(len([c for c in m2.cubes if c.bone == "weapon_back"]), 8)
        with self.assertRaises(HerogenError):
            apply_weapon(Model("t", "adult_f", "slim"), {"prop": "hair/short_crop"})
        # G6c: a weapon_back a part already uses (under the chest) cannot move to the hips, so the hip scabbard
        # gets a second sheathed bone under the hips; the part's cube stays where it was
        m3 = Model("t", "adult_f", "slim")
        apply_part(m3, inline(cubes=[dict(BOX, bone="weapon_back")]), 0)
        apply_weapon(m3, {"prop": "weapons/sword_plain", "sheathed": "hip"})
        self.assertEqual(m3.bones["weapon_back"].parent, "back")
        self.assertEqual(m3.bones["weapon_back_2"].parent, "body")
        self.assertEqual(len([c for c in m3.cubes if c.bone == "weapon_back_2"]), 4)
        with self.assertRaises(HerogenError):
            apply_weapon(Model("t", "adult_f"), {"prop": "weapons/sword_plain", "sheathed": "belt"})

    def test_test_rig_assembles(self):
        m = build.assemble(build.load_hero("test_rig"))
        for b in ("tail_hair_1", "tail_hair_2", "fine_tail_hair_2", "ear_l", "ear_r", "tail_1", "tail_5",
                  "skirt_f", "skirt_l", "skirt_b", "skirt_r", "weapon_back", "fine_sash", "p2_halo"):
            self.assertIn(b, m.bones)
        self.assertLessEqual(len(m.bones), 100)
        self.assertLessEqual(len(m.cubes), 300)


class GeneratorTest(unittest.TestCase):
    """Style pass 2 generators (gen.py): strands, chains and rotated groups."""

    def strand_part(self, **group):
        g = {"bone": "bangs", "path": "line", "from": [-4, 10.5, -5.6], "to": [4, 10.5, -5.6], "count": 5,
             "width": 1.5, "depth": 1, "length": 4, "flare": 12}
        g.update(group)
        return {"part": {"category": "test", "slots": {"main": "hair"}, "offset": [0, "head_y", 0], "strands": [g]}}

    def test_strands_hang_and_flare_outward(self):
        from rot import rotate_points
        m = Model("t", "teen_f")
        apply_part(m, self.strand_part(), 0)
        cubes = [c for c in m.cubes if c.part == "inline/0"]
        self.assertEqual(len(cubes), 5)
        self.assertTrue(all(c.bone == "fine_bangs" for c in cubes), "strands are level-of-detail by default")
        for c in cubes:
            x, y, z = c.origin
            sx, sy, sz = c.size
            tip = rotate_points([(x + sx / 2, y, z + sz / 2)], c.rotation, c.pivot)[0]
            self.assertLess(tip[2], c.pivot[2] - 0.5, "the free end swings outward (to the front, -z)")
            self.assertLess(tip[1], c.pivot[1] - 3, "and still hangs down")

    def test_fan_zigzag_and_layers(self):
        m = Model("t", "teen_f")
        apply_part(m, self.strand_part(fan=20, zigzag=5, layers=[0, 0.5]), 0)
        cubes = [c for c in m.cubes if c.part == "inline/0"]
        self.assertNotEqual(cubes[0].rotation, cubes[-1].rotation)
        self.assertAlmostEqual(cubes[1].pivot[2] - cubes[0].pivot[2], -0.5, msg="every other strand a layer out")

    def test_clear_face_and_ground(self):
        m = Model("t", "chibi_f")
        apply_part(m, self.strand_part(length=12, flare=0), 0)
        lim = m.env["head_y"] + m.rig["face"]["clear_below_y"]
        for c in (c for c in m.cubes if c.part == "inline/0"):
            self.assertGreaterEqual(c.origin[1], lim - 1e-6, "a fringe strand is shortened above the eyes")
        m = Model("t", "chibi_f")
        apply_part(m, self.strand_part(bone="hair_back", **{"from": [-4, 10, 6], "to": [4, 10, 6]}, length=60), 0)
        from rot import box_corners, rotate_points
        for c in (c for c in m.cubes if c.part == "inline/0"):
            low = min(p[1] for p in rotate_points(box_corners(c.origin, c.size), c.rotation, c.pivot))
            self.assertGreaterEqual(low, 0.25 - 1e-6, "long hair stops above the ground")

    def test_arc_mirror_and_sectors(self):
        m = Model("t", "adult_f")
        part = {"part": {"category": "test", "slots": {"main": "primary"}, "strands": [
            {"bone": "skirt", "path": "arc", "y": "waist_y", "radius": ["hips_w / 2", "hips_d / 2"],
             "angles": [-180, 135], "count": 8, "width": 2, "depth": 0.5, "length": 6, "flare": 20,
             "material": "cloth", "fine": False,
             "bones_by_angle": {"skirt_f": [-45, 45], "skirt_l": [45, 135], "skirt_b": [135, 225],
                                "skirt_r": [-135, -45]}}]}}
        apply_part(m, part, 0)
        bones = {c.bone for c in m.cubes if c.part == "inline/0"}
        self.assertEqual(bones, {"skirt_f", "skirt_l", "skirt_b", "skirt_r"})
        m = Model("t", "adult_f")
        apply_part(m, self.strand_part(bone="hair_r", mirror=True, **{"from": [-5.75, 9.5, -4], "to": [-5.75, 9.5, -3]},
                                       count=2, out=[-1, 0, 0]), 0)
        left = [c for c in m.cubes if c.bone == "fine_hair_l"]
        self.assertEqual(len(left), 2)
        self.assertEqual(left[0].rotation[1], -next(c for c in m.cubes if c.bone == "fine_hair_r").rotation[1])

    def test_chain_follows_its_points(self):
        from rot import rotate_points
        m = Model("t", "adult_f")
        apply_part(m, {"part": "tails/fluffy", "color": "hair", "alt": "hair_accent"}, 0)
        for k in range(1, 6):
            self.assertIn("tail_%d" % k, m.bones)
        self.assertEqual(m.bones["tail_2"].parent, "tail_1")
        main = [c for c in m.cubes if c.part == "tails/fluffy" and c.name.endswith("_0")]
        self.assertEqual(len(main), 5)
        for k, c in enumerate(main[:-1]):
            nxt = m.bones["tail_%d" % (k + 2)].pivot
            x, y, z = c.origin
            sx, sy, sz = c.size
            end = rotate_points([(x + sx / 2, y + 1.0, z + sz / 2)], c.rotation, c.pivot)[0]   # overlap 1.0
            self.assertLess(sum((a - b) ** 2 for a, b in zip(end, nxt)) ** 0.5, 0.05, "segment %d ends at the next pivot" % k)
        extra = [c for c in m.cubes if c.part == "tails/fluffy" and not c.name.endswith("_0")]
        self.assertTrue(extra and all(c.bone.startswith("fine_") for c in extra), "rolled boxes are detail")

    def test_groups_and_when(self):
        m = Model("t", "teen_f")
        apply_part(m, {"part": "ears/cat"}, 0)
        ear = [c for c in m.cubes if c.part == "ears/cat" and c.bone in ("ear_r", "fine_ear_r")]
        self.assertTrue(len({c.rotation for c in ear}) == 1 and ear[0].rotation is not None, "one rotation for the ear")
        m = Model("t", "teen_f")
        apply_part(m, {"part": "hair/long_layered", "params": {"ahoge": 0}}, 0)
        self.assertNotIn("ahoge", m.bones, "a chain with when = 0 is left out")
        m = Model("t", "teen_f")
        apply_part(m, {"part": "hair/long_layered", "params": {"ahoge": 1}}, 0)
        self.assertIn("ahoge_tip", m.bones)


if __name__ == "__main__":
    unittest.main()
