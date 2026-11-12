import unittest

from helpers import HEROGEN  # noqa: F401

from rig import BASE_IDS, BUILDS, FORMS, HEIGHT_KEYS, Model, load_base, load_rig, mirror_name, required_bones

REQUIRED = ["root", "body", "waist", "chest", "neck", "head", "face", "eyes", "mouth", "cheeks", "face_ctrl",
            "arm_r", "forearm_r", "hand_r", "item_r", "weapon_r", "arm_l", "forearm_l", "hand_l", "item_l", "weapon_l",
            "sash", "leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l",
            "vfx_tip_r", "vfx_tip_l", "vfx_chest", "vfx_head", "vfx_feet"]

# Rig v3 (HEROES_V2.md 4.3, style pass 2): height to the head top per form (head 10 px on every form) and the
# standard widths: chest, waist, hips, upper arm, leg (w x d), shoulder pivot x
HEIGHT = {"chibi": 35, "teen": 45, "adult": 52}
TABLE = {
    "chibi_f": ((6, 3.5), (5.5, 3.5), (6.5, 4), (2.5, 2.5), (3, 3), 4.25),
    "chibi_m": ((6.5, 4), (6, 3.5), (6.5, 4), (2.75, 2.75), (3, 3), 4.625),
    "teen_f": ((6.5, 4), (5.5, 3.5), (7, 4), (2.75, 2.75), (3.25, 3.25), 4.625),
    "teen_m": ((7.5, 4), (6.5, 3.5), (7.5, 4), (3, 3), (3.5, 3.5), 5.25),
    "adult_f": ((7.5, 4.5), (6, 4), (8, 4.5), (3, 3), (3.75, 3.75), 5.25),
    "adult_m": ((9, 5), (7.5, 4.5), (8.5, 4.5), (3.5, 3.5), (4, 4), 6.25),
}
HAIR = 3.0          # a typical hair volume above the head top, for the visual head share


class RigTest(unittest.TestCase):
    def test_bone_counts(self):
        rig = load_rig()
        self.assertEqual(rig["id"], "hero_rig_v3")
        self.assertEqual(len(rig["bones"]), 66, "v2's 64 plus tail_4 and tail_5 (five-bone tails)")
        self.assertEqual(sorted(required_bones(rig)), sorted(REQUIRED))
        self.assertEqual(len(REQUIRED), 33)
        self.assertEqual(rig["budgets"]["bones"], 100)
        self.assertEqual(rig["budgets"]["cubes"], 300)
        self.assertEqual(rig["budgets"]["lod_cubes"], 130)
        self.assertEqual(rig["budgets"]["textures"], [[256, 128], [256, 256]])
        self.assertEqual(rig["render"]["scale"], 0.56)

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
        self.assertEqual(mirror_name("twin_r_1"), "twin_l_1")
        self.assertEqual(mirror_name("bangs"), "bangs")

    def test_bases_match_the_table(self):
        for bid, (chest, waist, hips, arm, leg, sx) in TABLE.items():
            d = load_base(bid)["dims"]
            self.assertEqual((d["chest_w"], d["chest_d"]), chest, bid)
            self.assertEqual((d["waist_w"], d["waist_d"]), waist, bid)
            self.assertEqual((d["hips_w"], d["hips_d"]), hips, bid)
            self.assertEqual((d["arm_w"], d["arm_d"]), arm, bid)
            self.assertEqual((d["leg_w"], d["leg_d"]), leg, bid)
            self.assertEqual(d["shoulder_x"], sx, bid)

    def test_forms(self):
        """Three forms of clearly different height with the same 10 px head; with about 3 px of hair the visual head
        share is about 0.24 (adult), 0.27 (teen) and 0.34 (chibi), the references' range (reference-study.md 3); drawn
        at the render scale the adult stays about player height and every form is under the 1.98-block hitbox cap,
        the chibi above the 1.2-block floor."""
        scale = load_rig()["render"]["scale"]
        for bid in BASE_IDS:
            m = Model("t", bid)
            self.assertEqual(m.env["head_top"], HEIGHT[m.form], bid)
            head = m.cube("head")
            self.assertEqual(head.size, (10.0, 10.0, 10.0), bid)
            self.assertLessEqual(m.env["head_top"] * scale / 16, 1.98, bid)
            self.assertGreaterEqual(m.env["head_top"] * scale / 16, 1.2, bid)
            self.assertEqual(m.env["render_scale"], scale)
        share = {f: (10.0 + HAIR) / (HEIGHT[f] + HAIR) for f in FORMS}
        self.assertTrue(0.22 <= share["adult"] <= 0.26, share)
        self.assertTrue(0.25 <= share["teen"] <= 0.29, share)
        self.assertTrue(0.31 <= share["chibi"] <= 0.36, share)
        self.assertGreaterEqual(HEIGHT["adult"] - HEIGHT["teen"], 6, "adults clearly taller")

    def test_builds_change_widths_only(self):
        for bid in BASE_IDS:
            std = load_base(bid)["dims"]
            for build in BUILDS:
                d = load_base(bid, build)["dims"]
                for k in HEIGHT_KEYS:
                    self.assertEqual(d[k], std[k], "%s %s %s" % (bid, build, k))
            self.assertLess(load_base(bid, "slim")["dims"]["chest_w"], std["chest_w"], bid)
            self.assertGreater(load_base(bid, "large")["dims"]["chest_w"], std["chest_w"], bid)

    def test_forms_share_names_and_heights(self):
        """Every base has the same bone catalogue; within a form (both families, every build) every pivot height is
        the same, so an animation keyed for the form fits each of its bodies exactly."""
        models = {(bid, b): Model("t", bid, b) for bid in BASE_IDS for b in BUILDS}
        ref = models[("adult_f", "standard")]
        for (bid, b), m in models.items():
            self.assertEqual(set(m.catalogue), set(ref.catalogue), bid)
            self.assertEqual(sorted(m.bones), sorted(REQUIRED), bid)
            same_form = models[(m.form + "_f", "standard")]
            for name, bone in m.catalogue.items():
                self.assertEqual(bone.pivot[1], same_form.catalogue[name].pivot[1], "%s %s %s height" % (bid, b, name))

    def test_body_geometry(self):
        for bid in BASE_IDS:
            for build in BUILDS:
                m = Model("t", bid, build)
                d = m.base["dims"]
                box = {c.name: c.box for c in m.cubes}
                self.assertAlmostEqual(min(b[1] for b in box.values()), 0.0, msg="feet at y 0")
                self.assertAlmostEqual(box["head"][4], m.env["head_top"])
                self.assertAlmostEqual(box["thigh_r"][1], d["knee_y"])
                # arms hang right next to the chest, on the hero's right at -x
                self.assertAlmostEqual(box["upper_arm_r"][3], -d["chest_w"] / 2, msg="%s %s" % (bid, build))
                self.assertAlmostEqual(box["upper_arm_l"][0], d["chest_w"] / 2, msg="%s %s" % (bid, build))
                # legs stay inside the hips and do not overlap
                self.assertLessEqual(box["thigh_l"][3], d["hips_w"] / 2 + 1e-9, "%s %s" % (bid, build))
                self.assertGreaterEqual(box["thigh_l"][0], -1e-9, "%s %s" % (bid, build))
                self.assertLessEqual(box["thigh_r"][3], 1e-9, "%s %s" % (bid, build))
                # the hands hang between the hip line and mid-thigh (references: hip line on the chibi, mid-thigh on
                # the tall form)
                self.assertLessEqual(box["hand_r"][1], d["hip_y"], bid)
                self.assertGreaterEqual(box["hand_r"][1], (d["hip_y"] + d["knee_y"]) / 2 - 0.5, bid)
                # legs carry the height: about 0.35 (chibi) to 0.47 (adult) of the head-top height
                self.assertTrue(0.33 <= d["hip_y"] / m.env["head_top"] <= 0.5, bid)
                self.assertEqual(any(c.name == "bust" for c in m.cubes), bid in ("adult_f", "teen_f"), bid)
                for c in m.cubes:
                    self.assertIn(c.bone, m.bones)
                # the face planes sit on the head front at the same head-local place on every form: the eye body
                # (frame rows 4-9) spans y 1.5-4.5, its centre 0.30 of the head height above the chin
                self.assertAlmostEqual(m.cube("eyes").origin[1] - d["head_y"], 1.5)
                self.assertAlmostEqual(m.cube("mouth").origin[1] - d["head_y"], 0.0)


if __name__ == "__main__":
    unittest.main()
