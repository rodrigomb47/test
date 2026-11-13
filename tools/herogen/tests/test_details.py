"""G6c: the details of the parts library (accessories, markings, weapons), the painter's patterns and emblem stamps,
the weapon carries (hand, grip, sheaths, an off-hand from a phase, the staff-broom's mount point) and the hand poses
every slice weapon is held in (grips.py)."""
import glob
import json
import os
import unittest

import numpy as np

from helpers import mannequin

import build
import emblems
import grips
import lint
import visual
from paint import Painter, has_phase_glow
from parts import IDENT, P_FLAT, apply_part, apply_weapon, load_part, stamp_through, weapon_defaults
from rig import BASE_IDS, Cube, Model
from render.scene import HeroAssets
from util import HEROSRC, HerogenError

SLICE_WEAPONS = ("weapons/claw_gauntlet", "weapons/folding_fan", "weapons/iron_club", "weapons/purification_wand",
                 "weapons/talisman_fan", "weapons/katana", "weapons/short_blade", "weapons/kunai",
                 "weapons/longsword", "weapons/kite_shield", "weapons/cauldron_staff")
SLICE_STYLES = ("fists", "focus", "hammer", "katana", "twin_blades", "sword_shield", "staff")
G6C_ACCESSORIES = ("choker_bell", "crow", "foxfire_lanterns", "gourd", "kunai_holster", "prayer_beads", "rope_belt",
                   "talisman_ring", "talisman_strips", "vial_bandolier")


class _Built:
    """What grips.problems reads of a build (the model), without painting."""

    def __init__(self, model):
        self.model = model


def outfit(oid):
    return [o for o in lint.load_outfits() if o["id"] == oid][0]


def face_texels(img, cube, face):
    u, v, w, h = cube.uv[face]
    return img[v:v + h, u:u + w]


def rgb(ramps, slot, tone=0):
    return tuple(int(c) for c in ramps[slot][tone + 2])


class LibraryTest(unittest.TestCase):
    def test_slice_column_of_13_3(self):
        """The G6c part of the slice column: 10 accessories for the slice heroes, 3 markings, a weapon for every
        slice hero (11 props for 10 weapon looks: Kagenori's two swords are one part at two blade lengths)."""
        ids = lint.part_ids()
        for name in G6C_ACCESSORIES:
            self.assertIn("accessories/" + name, ids)
        self.assertEqual(sorted(i for i in ids if i.startswith("markings/")),
                         ["markings/arm_stripes", "markings/calico", "markings/crest"])
        for pid in SLICE_WEAPONS:
            self.assertIn(pid, ids)

    def test_every_slice_weapon_names_its_carry(self):
        """Each slice weapon says how it is held (hand, grip), carried (sheath) and in which style's hand poses."""
        for pid in SLICE_WEAPONS:
            with self.subTest(part=pid):
                d = weapon_defaults(pid)
                self.assertIn(d["style"], SLICE_STYLES)
                self.assertTrue(grips.style_poses(d["style"]), "the style has hand poses")
                self.assertEqual(grips.style_poses(d["style"])[0], d["style"] + ".stance")

    def test_emblems_are_well_formed(self):
        self.assertEqual(emblems.check_all(), [])
        rows = emblems.bitmap("leaf", 5)
        self.assertEqual(emblems.rotated(emblems.rotated(rows, 180), 180), rows)
        self.assertEqual(emblems.rotated(rows, 90)[0], "".join(r[0] for r in reversed(rows)))
        self.assertEqual(len(emblems.bitmap("lily", fit=(8, 8))), 7, "the largest size that fits")
        self.assertEqual(len(emblems.bitmap("lily", fit=(3, 3))), 5, "the smallest when none fits")
        with self.assertRaises(HerogenError):
            emblems.bitmap("lily", 6)

    def test_every_emblem_a_source_names_exists(self):
        names = set()

        def walk(o):
            if isinstance(o, dict):
                for k, v in o.items():
                    if k == "emblem" and isinstance(v, str) and v != "@":
                        names.add(v)
                    walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        for path in glob.glob(os.path.join(HEROSRC, "parts", "*", "*.json")) + [lint.OUTFITS]:
            with open(path) as f:
                walk(json.load(f))
        self.assertTrue(names)
        self.assertLessEqual(names, set(emblems.EMBLEMS))


class PatternTest(unittest.TestCase):
    def sample(self, spec, n=24):
        return [[Painter.pattern_at(spec, "north", (i + 0.5, j + 0.5, 0.0), 1) for i in range(n)] for j in range(n)]

    def test_new_patterns_draw_both_colours(self):
        """waves, scales, hemp-leaf, petals and patches put the pattern's slots on part of a panel and leave the rest
        to the panel's own colour; petals and patches use their second slot too."""
        for kind, size in (("waves", 4), ("scales", 3), ("hemp", 3), ("petals", 5), ("patches", 5)):
            with self.subTest(pattern=kind):
                got = [s for row in self.sample({"type": kind, "size": size, "slots": ["a", "b"], "seed": 0})
                       for s, _ in row]
                share = got.count("a") / float(len(got))
                self.assertGreater(share, 0.05)
                self.assertLess(share, 0.8)
                self.assertIn(None, got)
                if kind in ("petals", "patches"):
                    self.assertIn("b", got)

    def test_patches_are_seeded_and_deterministic(self):
        a = self.sample({"type": "patches", "size": 4, "slots": ["a", "b"], "seed": 0})
        self.assertEqual(a, self.sample({"type": "patches", "size": 4, "slots": ["a", "b"], "seed": 0}))
        self.assertNotEqual(a, self.sample({"type": "patches", "size": 4, "slots": ["a", "b"], "seed": 7}))

    def test_lace_cutout(self):
        c = Cube(name="t", bone="chest", origin=(0.0, 0.0, 0.0), size=(8.0, 6.0, 1.0), slot="primary",
                 material="cloth", part="t", cutout="lace")
        keep = [[Painter.own_keep(c, "north", i, j, 8, 6) for i in range(8)] for j in range(6)]
        self.assertEqual(keep[5], [True, False] * 4, "scallops on the free edge")
        self.assertEqual(keep[3], [True, False, True, True] * 2, "a row of holes above them")
        self.assertTrue(all(keep[0]))


class StampTest(unittest.TestCase):
    def test_a_cube_stamp_paints_its_emblem_centred(self):
        """A cube's stamp paints the emblem's '#' texels in its slot, centred on the face; the rest keeps the cube's
        own paint."""
        part = {"part": {"category": "test", "slots": {"main": "primary"}, "cubes": [
            {"name": "plate", "bone": "chest", "origin": [-4, 30, -5], "size": [9, 9, 0.5], "material": "paper",
             "outline": False, "stamp": {"emblem": "crest", "face": "north", "slot": "accent", "size": 7}}]}}
        b = build.build(mannequin("adult_m", [part]))
        cube = [c for c in b.model.cubes if c.name.endswith("#plate")][0]
        tex = face_texels(b.image, cube, "north")
        rows = emblems.bitmap("crest", 7)
        accent = {rgb(b.ramps, "accent", t) for t in (-1, 0, 1)}
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                px = tuple(int(c) for c in tex[1 + j, 1 + i, :3])
                if ch == "#":
                    self.assertIn(px, accent, (i, j))
                elif ch == ".":
                    self.assertNotIn(px, accent, (i, j))

    def test_a_crest_takes_the_hero_entry_emblem_or_none(self):
        """markings/crest stamps the entry's `emblem` (`@`) over the clothes; an entry without one paints nothing."""
        coat = {"part": "outfit_top/haori", "color": "primary", "alt": "secondary", "trim": "trim"}
        with_crest = build.build(mannequin("adult_m", [coat, {"part": "markings/crest", "emblem": "crane",
                                                              "color": "secondary"}]))
        without = build.build(mannequin("adult_m", [coat, {"part": "markings/crest", "color": "secondary"}]))
        plain = build.build(mannequin("adult_m", [coat]))
        self.assertFalse(np.array_equal(with_crest.image, plain.image))
        self.assertTrue(np.array_equal(without.image, plain.image))

    def test_stamps_stay_upright_through_a_carry(self):
        """A shield's emblem authored on its outer face (east in grip space, turned 90 degrees) lands on the face
        that looks out of the back carry, still upright (P_FLAT: the outer side faces back)."""
        s = stamp_through({"emblem": "lily", "face": "east", "rotate": 90}, P_FLAT)
        self.assertEqual(s["face"], "south")
        self.assertIs(stamp_through({"face": "east"}, IDENT)["face"], "east")
        part = load_part("weapons/kite_shield")
        face = [c for c in part["cubes"] if c["name"] == "face"][0]
        self.assertEqual(face["stamp"]["face"], "east")

    def test_whiskers_face_paint(self):
        plain = build.build(mannequin("teen_f"))
        cat = build.build(mannequin("teen_f", face={"marks": [{"type": "face_paint", "style": "whiskers",
                                                               "color": "accent"}]}))
        self.assertFalse(np.array_equal(plain.image, cat.image))
        with self.assertRaises(HerogenError):
            build.build(mannequin("teen_f", face={"marks": [{"type": "face_paint", "style": "tiger"}]}))


class MarkingTest(unittest.TestCase):
    def test_calico_is_an_overlay_on_the_hair(self):
        """Calico patches change only part of the hair and keep the rest of it exactly as painted without them."""
        hair = {"part": "hair/bob", "color": "hair"}
        plain = build.build(mannequin("teen_f", [hair]))
        calico = build.build(mannequin("teen_f", [hair, {"part": "markings/calico", "alt": "accent",
                                                         "detail": "secondary"}]))
        cubes = [c for c in plain.model.cubes if c.part == "hair/bob"]
        changed = total = 0
        for c in cubes:
            for face in c.uv:
                a, b = face_texels(plain.image, c, face), face_texels(calico.image, c, face)
                diff = np.any(a != b, axis=2)
                changed += int(diff.sum())
                total += diff.size
        self.assertGreater(changed / float(total), 0.1)
        self.assertLess(changed / float(total), 0.8)

    def test_arm_stripes_glow_only_in_the_phase_look(self):
        """Benika's stripes glow from phase 2: the phase texture's glow mask carries them, the base mask does not,
        and visual.json points phase 2 at the phase texture even without a palette change."""
        hero = mannequin("adult_f", [{"part": "markings/arm_stripes", "color": "primary"}],
                         phases=[{"phase": 2}, {"phase": 3}])
        b = build.build(hero)
        self.assertTrue(has_phase_glow(b.model))
        self.assertIsNotNone(b.p2)
        base_glow = int((b.glow[:, :, 3] > 0).sum())
        p2_glow = int((b.p2_glow[:, :, 3] > 0).sum())
        self.assertGreater(p2_glow, base_glow)
        textures = {"width": b.model.tex_w, "height": b.model.tex_h, "glowmask": True, "glow_outside_face": True,
                    "p2": True}
        v = visual.visual_json(hero, b.model, b.spec, textures, "x")
        self.assertEqual([p["texture"] for p in v["phases"]], ["p2", "p2"])
        no_phase = build.build(mannequin("adult_f", [{"part": "markings/arm_stripes", "color": "primary"}]))
        self.assertIsNone(no_phase.p2)

    def test_talisman_ring_is_a_phase_two_silhouette(self):
        m = Model("t", "adult_f")
        apply_part(m, {"part": "accessories/talisman_ring", "phase": 2}, 0)
        ring = [c for c in m.cubes if c.part == "accessories/talisman_ring"]
        self.assertEqual(len(ring), 7)
        self.assertTrue(all(c.bone.startswith("p2_") and c.glow is True for c in ring))
        for c in ring:
            centre = np.array(c.pivot)
            self.assertGreater(np.hypot(centre[0], centre[2]), m.env["shoulder_x"] + m.env["arm_w"] / 2 + 5,
                               "the talismans float clear of the arms")


class WeaponTest(unittest.TestCase):
    def test_reverse_grip_lays_the_blade_along_the_forearm(self):
        """short_blade's `grip: reverse` turns the prop 180 degrees about the hand's y: its blade leaves the hand
        toward +z (back along the forearm when the arm is raised), and its tip locator follows."""
        m = Model("t", "adult_m")
        apply_weapon(m, {"prop": "weapons/short_blade"})
        pv = m.bones["weapon_r"].pivot
        blade = [c for c in m.cubes if c.bone == "weapon_r" and c.name.endswith("#3")][0]
        self.assertGreater(blade.origin[2], pv[2])
        self.assertGreater(m.bones["vfx_tip_r"].pivot[2], pv[2] + 5)

    def test_claws_are_worn(self):
        """Claw gauntlets: the glove and plate stay on the hands at all times (sheathed `worn`), the claws only in
        the drawn bones, on both hands (`pair`)."""
        m = Model("t", "teen_f")
        apply_weapon(m, {"prop": "weapons/claw_gauntlet"})
        for side in ("r", "l"):
            self.assertEqual(len([c for c in m.cubes if c.bone == "hand_" + side and c.part != "base"]), 2)
            drawn = [c for c in m.cubes if c.bone.endswith("weapon_" + side)]
            self.assertEqual(len(drawn), 6)
        self.assertNotIn("weapon_back", m.bones)

    def test_scabbard_stays_and_offhand_from_phase_three(self):
        """Kagenori: the katana's scabbard stays on its own `sheath` bone when drawn; the short sword (an off-hand
        drawn from phase 3) is in p3_weapon_l, and its holstered copy is hidden from phase 3 by visual.json."""
        hero = lint.outfit_hero(outfit("kagenori"), "adult_m")
        b = build.build(hero)
        m = b.model
        self.assertEqual(m.bones["sheath"].parent, "body")
        self.assertTrue(any(c.bone == "sheath" for c in m.cubes))
        self.assertTrue(any(c.bone == "p3_weapon_l" for c in m.cubes))
        self.assertTrue(any(c.bone == "holstered" for c in m.cubes))
        textures = {"width": m.tex_w, "height": m.tex_h, "glowmask": False, "glow_outside_face": False, "p2": False}
        v = visual.visual_json(hero, m, b.spec, textures, "x")
        self.assertIn("weapon_l", v["bones"]["weapon_drawn"])
        self.assertNotIn("sheath", v["bones"]["weapon_sheathed"])
        p3 = [p for p in v["phases"] if p["phase"] == 3][0]
        self.assertIn("holstered", p3["hide"])
        self.assertEqual(v["weapon"]["offhand"], {"sheathed": "obi_same", "phase": 3})

    def test_shield_on_the_left_and_flat_on_the_back(self):
        m = Model("t", "adult_f")
        apply_weapon(m, {"prop": "weapons/longsword", "sheathed": "hip",
                         "offhand": {"prop": "weapons/kite_shield", "sheathed": "back_flat", "emblem": "lily"}})
        self.assertTrue(any(c.bone == "weapon_l" for c in m.cubes))
        self.assertEqual(m.bones["weapon_back"].parent, "body", "the sword hangs at the hip")
        self.assertEqual(m.bones["weapon_back_2"].parent, "back", "the shield rides on the back")
        flat = [c for c in m.cubes if c.bone == "weapon_back_2" and c.stamp]
        self.assertEqual({c.stamp["face"] for c in flat}, {"south"}, "the emblem looks out of the back")
        with self.assertRaises(HerogenError):
            apply_weapon(Model("t", "adult_f"), {"prop": "weapons/kite_shield", "hand": "left",
                                                 "offhand": {"prop": "weapons/kunai"}})

    def test_staff_broom_mount_point(self):
        """Ambrose's staff is also his broom (G10): `mount` is a bone under root whose pivot is the seat, at the
        prop's `mount.height` above the ground, carrying a whole copy of the staff head forward; visual.json lists
        it, and the renderer shows it only while riding (and the hand and back copies then hide)."""
        hero = lint.outfit_hero(outfit("ambrose"), "teen_m")
        a = HeroAssets.build(hero)
        m = a.built.model
        self.assertEqual(m.bones["mount"].parent, "root")
        self.assertAlmostEqual(m.bones["mount"].pivot[1], m.env["hip_y"] - m.env["leg_d"] / 2)
        mounted = [c for c in m.cubes if "mount" in c.bone]
        self.assertEqual(len(mounted), len(load_part("weapons/cauldron_staff")["cubes"]))
        pot = [c for c in mounted if c.name.endswith("#8")][0]
        self.assertLess(pot.origin[2], -10, "the head (the cauldron) is forward")
        self.assertEqual(a.visual["bones"]["mount"], ["mount"])
        self.assertEqual(a.visual["weapon"]["mount"]["bone"], "mount")
        self.assertIn("mount", a.hidden(phase=2))
        riding = a.hidden(phase=2, riding=True)
        self.assertNotIn("mount", riding)
        self.assertIn("weapon_r", riding)
        self.assertIn("weapon_back", riding)
        self.assertTrue(any(c.bone == "p2_mount" for c in mounted), "the bristles sprout from phase 2")


class HandPoseTest(unittest.TestCase):
    """Every slice weapon in each hand pose of its style (15.2 G6c), on all six bases."""

    def test_every_slice_weapon_in_every_hand_pose_on_every_base(self):
        for o in lint.load_outfits():
            for base in BASE_IDS:
                hero = lint.outfit_hero(o, base)
                with self.subTest(outfit=o["id"], base=base):
                    self.assertEqual(grips.problems(hero, _Built(build.assemble(hero)), ties=False), [])

    def test_props_point_where_the_pose_says(self):
        """On the teen (the form the poses are solved on): a cast points the prop at the target, a slam brings the
        club head down in front, a reverse grip lays the blade back, a claw strike leads with the claws, the shield
        faces the target."""
        from anim.skeleton import Skeleton

        def world(oid, name, base="teen_f"):
            hero = lint.outfit_hero(outfit(oid), base)
            m = build.assemble(hero)
            return m, Skeleton.from_model(m).world(grips.pose(name, 0.0))

        def axis(m, w, bone, v):
            return w[bone][0] @ np.array(v, dtype=float)

        def point(m, w, bone):
            return Skeleton.apply(w, bone, m.bones[bone].pivot)

        fwd = np.array([0.0, 0.0, -1.0])
        m, w = world("hinae", "focus.cast")
        self.assertGreater(axis(m, w, "weapon_r", (0, 0, -1)) @ fwd, 0.8)
        m, w = world("ambrose", "staff.cast")
        self.assertGreater(axis(m, w, "weapon_r", (0, 0, -1)) @ fwd, 0.8)
        m, w = world("benika", "hammer.slam")
        tip, grip = point(m, w, "vfx_tip_r"), point(m, w, "weapon_r")
        self.assertLess(tip[1], grip[1] - 8)
        self.assertLess(tip[2], grip[2] - 8)
        m, w = world("tobimaru", "twin_blades.stance")
        self.assertGreater(point(m, w, "vfx_tip_r")[2], point(m, w, "weapon_r")[2] + 4)
        self.assertGreater(axis(m, w, "weapon_l", (0, 0, -1)) @ fwd, 0.8, "the kunai points at the target")
        m, w = world("natsuho", "fists.strike")
        self.assertGreater(axis(m, w, "weapon_r", (0, -1, 0)) @ fwd, 0.9, "the claws lead")
        m, w = world("ilsabet", "sword_shield.stance")
        self.assertGreater(axis(m, w, "weapon_l", (1, 0, 0)) @ fwd, 0.8, "the shield's face looks forward")
        self.assertGreater(axis(m, w, "weapon_l", (0, 0, -1))[1], 0.9, "its top is up")

    def test_a_prop_through_the_head_fails(self):
        """Sabotage: the first draft's cast (the arm raised, the wand upright beside the face) runs the wand into a
        chibi's head, and the check says so."""
        import anim.library as animlib
        from anim import poses as P
        lib = animlib.pose_library()
        spec = json.loads(json.dumps(lib.specs["focus.cast"]))
        spec["bones"].update({"arm_r": [-112, 4, 8], "forearm_r": [-12, 0, 0], "hand_r": [-10, 0, 0]})
        lib2 = P.Library(dict(lib.specs, **{"focus.cast": spec}))
        chans = P.resolve(lib2.get("focus.cast"), animlib.form_skeletons())
        hero = lint.outfit_hero(outfit("hinae"), "chibi_f")
        m = build.assemble(hero)
        got = grips.measure(m, hero, chans)
        self.assertGreater(max(v for k, v in got["body"].items() if k[1] == "head"), grips.BODY_DEPTH)


if __name__ == "__main__":
    unittest.main()
