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
        m = Model("t", "f_standard")
        apply_part(m, inline(bones=[{"name": "lock_r", "parent": "head", "pivot": [-3, 30, 0]}],
                             cubes=[dict(BOX, bone="lock_r", mirror=True), dict(BOX, bone="ear_r", mirror=True)]), 0)
        r, l = m.cube("inline/0#0"), m.cube("inline/0#0:l")
        self.assertEqual((r.bone, l.bone), ("lock_r", "lock_l"))
        self.assertEqual(l.origin, (0.0, 10.0, -1.0))
        self.assertEqual(m.bones["lock_l"].pivot, (3.0, 30.0, 0.0), "declared bones mirror on demand")
        self.assertEqual(m.cube("inline/0#1:l").bone, "ear_l", "rig bones mirror by name")
        self.assertIn("ear_r", m.bones)

    def test_phase_and_fine_bones(self):
        m = Model("t", "f_standard")
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
        m = Model("t", "m_standard")
        part = inline(cubes=[{"bone": "chest", "origin": ["-chest_w / 2", 18, 0], "size": ["length", 1, 1]}],
                      params={"length": {"min": 1, "max": 5, "default": 2}})
        apply_part(m, dict(part, params={"length": 4}), 0)
        c = m.cube("inline/0#0")
        self.assertEqual(c.origin[0], -4.0)
        self.assertEqual(c.size[0], 4.0)
        for bad in ({"length": 9}, {"width": 1}):
            with self.assertRaises(HerogenError):
                apply_part(Model("t", "m_standard"), dict(part, params=bad), 0)

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
                apply_part(Model("t", "f_slim"), entry, 0)

    def test_tags(self):
        m = Model("t", "f_slim")
        apply_part(m, {"part": "hair/high_ponytail_long"}, 0)
        with self.assertRaises(HerogenError):
            apply_part(m, {"part": "hair/short_crop"}, 1)          # both occupy hair_top

    def test_weapon_in_hand_and_sheathed(self):
        m = Model("t", "f_standard")
        apply_weapon(m, {"prop": "weapons/sword_plain", "hand": "right", "sheathed": "hip"})
        hand = [c for c in m.cubes if c.bone == "weapon_r"]
        sheath = [c for c in m.cubes if c.bone == "weapon_back"]
        self.assertEqual(len(hand), 4)
        self.assertEqual(len(sheath), 4, "hilt cubes plus the scabbard")
        pv = m.bones["weapon_r"].pivot
        grip = hand[0]
        self.assertAlmostEqual(grip.origin[0] + grip.size[0] / 2, pv[0])
        tip = m.bones["vfx_tip_r"].pivot
        self.assertAlmostEqual(tip[2], pv[2] - 11.25, msg="tip locator at the blade end")
        for c in sheath:
            self.assertEqual(c.rotation, (55.0, 0.0, 0.0))
            self.assertGreater(c.pivot[0], 0, "a right-hand sword hangs on the left hip (+x)")
        self.assertEqual(m.bones["weapon_back"].parent, "body", "a hip scabbard follows the hips, not the chest")
        m2 = Model("t", "f_standard")
        apply_weapon(m2, {"prop": "weapons/sword_plain", "hand": "pair", "sheathed": "back"})
        self.assertEqual(m2.bones["weapon_back"].parent, "back", "a back carry follows the chest")
        self.assertEqual(len([c for c in m2.cubes if c.bone == "weapon_l"]), 4)
        self.assertEqual(len([c for c in m2.cubes if c.bone == "weapon_back"]), 8)
        with self.assertRaises(HerogenError):
            apply_weapon(Model("t", "f_slim"), {"prop": "hair/short_crop"})
        m3 = Model("t", "f_slim")
        apply_part(m3, inline(cubes=[dict(BOX, bone="weapon_back")]), 0)
        with self.assertRaises(HerogenError):
            apply_weapon(m3, {"prop": "weapons/sword_plain", "sheathed": "hip"})

    def test_test_rig_assembles(self):
        m = build.assemble(build.load_hero("test_rig"))
        for b in ("tail_hair_1", "tail_hair_2", "fine_tail_hair_tip", "ear_l", "ear_r", "tail_1", "tail_3",
                  "skirt_f", "skirt_l", "cape_1", "cape_2", "weapon_back", "fine_sash_knot", "p2_halo"):
            self.assertIn(b, m.bones)
        self.assertLessEqual(len(m.bones), 72)
        self.assertLessEqual(len(m.cubes), 140)


if __name__ == "__main__":
    unittest.main()
