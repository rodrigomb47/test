import unittest

from helpers import HEROGEN  # noqa: F401

from rig import BASE_IDS, Model, load_base, load_rig, mirror_name, required_bones

REQUIRED = ["root", "body", "waist", "chest", "neck", "head", "face", "eyes", "mouth", "cheeks", "face_ctrl",
            "arm_r", "forearm_r", "hand_r", "item_r", "weapon_r", "arm_l", "forearm_l", "hand_l", "item_l", "weapon_l",
            "sash", "leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l",
            "vfx_tip_r", "vfx_tip_l", "vfx_chest", "vfx_head", "vfx_feet"]

# HEROES_V2.md 4.3: chest, waist, hips, upper arm, leg (w x d), shoulder pivot x
TABLE = {
    "f_slim": ((6, 3), (5, 2.5), (6.5, 3.5), (2, 2), (2.5, 2.5), 4),
    "f_standard": ((7, 3.5), (5.5, 3), (7, 3.5), (2.5, 2.5), (3, 3), 4.75),
    "f_large": ((8, 4), (6.5, 3.5), (8, 4), (3, 3), (3.5, 3.5), 5.5),
    "m_slim": ((7, 3.5), (6, 3), (6.5, 3), (2.5, 2.5), (3, 3), 4.75),
    "m_standard": ((8, 4), (7, 3.5), (7, 3.5), (3, 3), (3.5, 3.5), 5.5),
    "m_large": ((9.5, 5), (8.5, 4.5), (8.5, 4.5), (4, 4), (4, 4), 6.75),
}


class RigTest(unittest.TestCase):
    def test_bone_counts(self):
        rig = load_rig()
        self.assertEqual(len(rig["bones"]), 61, "the full tree has 61 bones (4.3)")
        self.assertEqual(sorted(required_bones(rig)), sorted(REQUIRED))
        self.assertEqual(len(REQUIRED), 33)
        self.assertEqual(rig["budgets"]["bones"], 72)
        self.assertEqual(rig["budgets"]["cubes"], 140)

    def test_tree_is_ordered(self):
        seen = set()
        for b in load_rig()["bones"]:
            if b["parent"] is not None:
                self.assertIn(b["parent"], seen, b["name"])
            seen.add(b["name"])

    def test_mirror_names(self):
        self.assertEqual(mirror_name("arm_r"), "arm_l")
        self.assertEqual(mirror_name("upper_arm_l"), "upper_arm_r")
        self.assertEqual(mirror_name("ear_r_tip"), "ear_l_tip")
        self.assertEqual(mirror_name("bangs"), "bangs")

    def test_bases_match_the_design_table(self):
        for bid, (chest, waist, hips, arm, leg, sx) in TABLE.items():
            d = load_base(bid)["dims"]
            self.assertEqual((d["chest_w"], d["chest_d"]), chest, bid)
            self.assertEqual((d["waist_w"], d["waist_d"]), waist, bid)
            self.assertEqual((d["hips_w"], d["hips_d"]), hips, bid)
            self.assertEqual((d["arm_w"], d["arm_d"]), arm, bid)
            self.assertEqual((d["leg_w"], d["leg_d"]), leg, bid)
            self.assertEqual(d["shoulder_x"], sx, bid)

    def test_bases_share_names_and_heights(self):
        models = {bid: Model("t", bid) for bid in BASE_IDS}
        ref = models["f_standard"]
        for bid, m in models.items():
            self.assertEqual(set(m.catalogue), set(ref.catalogue), bid)
            for name, b in m.catalogue.items():
                self.assertEqual(b.pivot[1], ref.catalogue[name].pivot[1], "%s %s height" % (bid, name))
            self.assertEqual(sorted(m.bones), sorted(REQUIRED), bid)

    def test_body_geometry(self):
        for bid in BASE_IDS:
            m = Model("t", bid)
            d = m.base["dims"]
            box = {c.name: c.box for c in m.cubes}
            self.assertAlmostEqual(min(b[1] for b in box.values()), 0.0, msg="feet at y 0")
            self.assertAlmostEqual(box["head"][4], 32.0)
            self.assertAlmostEqual(box["thigh_r"][1], 6.5, msg="knee at 6.5")
            # arms hang right next to the chest, on the hero's right at -x
            self.assertAlmostEqual(box["upper_arm_r"][3], -d["chest_w"] / 2, msg=bid)
            self.assertAlmostEqual(box["upper_arm_l"][0], d["chest_w"] / 2, msg=bid)
            # legs stay inside the hips and do not overlap
            self.assertLessEqual(box["thigh_l"][3], d["hips_w"] / 2 + 1e-9, bid)
            self.assertGreaterEqual(box["thigh_l"][0], -1e-9, bid)
            self.assertLessEqual(box["thigh_r"][3], 1e-9, bid)
            self.assertEqual(any(c.name == "bust" for c in m.cubes), bid.startswith("f_"), bid)
            for c in m.cubes:
                self.assertIn(c.bone, m.bones)


if __name__ == "__main__":
    unittest.main()
