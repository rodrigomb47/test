"""Polish after the art review (G5's open items): the shield no longer pops when the draw and the sheathe swap it
between the back and the arm, the club's second hand stays on the handle through the slam, and the slam's club head
reaches the ground on every form (a form-scaled hip fold: the pose library's `form` field). No hit tick or timing file
entry moved."""
import json
import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401  (sets the path)

import build
import grips
import lint
from anim import library, poses
from anim.clip import TICK, form_var, layered
from anim.skeleton import Skeleton
from anim.writer import animation_file
from rig import BASE_IDS
from rot import file_matrix
from util import HerogenError, ROOT, dumps_json, load_json


def outfit_hero(oid, base):
    o = [x for x in lint.load_outfits() if x["id"] == oid][0]
    return lint.outfit_hero(o, base)


def action_pose(lib, name, t, d):
    c = lib.by_name[name]
    over = lib.by_name.get(c.meta["over"]) or library.core().by_name[c.meta["over"]]
    return layered(over, c, t, t % over.length, d=d)


def shield_corners(model, world):
    """[(drawn corner, carried corner)] in world space: each corner of the shield on the left arm and the same corner
    of the carried copy on the back (the carry is a rigid map of the prop, fitted from the cube centres)."""
    pairs = {}
    for c in model.cubes:
        if "kite_shield#" not in c.name:
            continue
        k = c.name.split("#")[1]
        if c.name.startswith("weapon_l:"):
            pairs.setdefault(k, [None, None])[0] = c
        elif c.name.startswith("weapon_back"):
            pairs.setdefault(k, [None, None])[1] = c
    pairs = [v for _, v in sorted(pairs.items()) if None not in v]
    a = np.array([np.array(x.origin) + np.array(x.size) / 2 for x, _ in pairs])
    b = np.array([np.array(y.origin) + np.array(y.size) / 2 for _, y in pairs])
    u, _, vt = np.linalg.svd((a - a.mean(0)).T @ (b - b.mean(0)))
    r = vt.T @ u.T
    t = b.mean(0) - r @ a.mean(0)
    out = []
    for x, y in pairs:
        o, s = np.array(x.origin), np.array(x.size)
        for i in (0, 1):
            for j in (0, 1):
                for k in (0, 1):
                    p = o + s * np.array([i, j, k])
                    rx, tx = world[x.bone]
                    ry, ty = world[y.bone]
                    out.append((rx @ p + tx, ry @ (r @ p + t) + ty))
    return out


class ShieldSwapTest(unittest.TestCase):
    """At the tick the weapon flag swaps (draw 0.35 s, sheathe 0.55 s), the shield on the arm lies where the carried
    one hangs: corners 2.0-3.3 px apart on average on every base (centres 12-17 px apart and a 50-70 degree turn
    before)."""

    def test_the_arm_copy_takes_the_carried_copys_place(self):
        lib = library.style("sword_shield")
        for base in BASE_IDS:
            m = build.assemble(outfit_hero("ilsabet", base))
            sk, d = Skeleton.from_model(m), form_var(m.env)
            for name in ("draw", "sheathe"):
                clip = lib.by_name[name]
                swap = [e.t for e in clip.events if e.kind == "instruction" and e.value.startswith("weapon:")]
                self.assertEqual(len(swap), 1)
                pairs = shield_corners(m, sk.world(action_pose(lib, name, swap[0], d)))
                dist = [float(np.linalg.norm(p - q)) for p, q in pairs]
                with self.subTest(base=base, clip=name):
                    self.assertLess(np.mean(dist), 3.5, "the shield jumps at the swap")
                    self.assertLess(max(dist), 5.5)

    def test_the_swap_ticks_did_not_move(self):
        lib = library.style("sword_shield")
        for name, t in (("draw", 0.35), ("sheathe", 0.55)):
            ev = [e.t for e in lib.by_name[name].events if e.kind == "instruction" and e.value.startswith("weapon:")]
            self.assertEqual(ev, [t])

    def test_draw_and_sheathe_keep_the_props_clear(self):
        lib = library.style("sword_shield")
        core = library.core()
        for base in ("chibi_f", "chibi_m", "teen_f", "adult_f", "adult_m"):
            hero = outfit_hero("ilsabet", base)
            b = build.build(hero)
            self.assertEqual(grips.animation_problems(hero, b, lib, lambda n: lib.by_name.get(n) or
                                                      core.by_name.get(n), clips=["draw", "sheathe"]), [], base)


class ClubTest(unittest.TestCase):
    """The slam (hammer attack.3) on every base: the club head reaches the ground at the hit (it stopped 6.2 px above
    it on a teen and 8.9 on an adult), and the second hand stays within 2.1 px of the handle on every tick (3.9 px
    at 0.75 s before)."""

    @classmethod
    def setUpClass(cls):
        cls.lib = library.style("hammer")
        cls.rows = {}
        for base in BASE_IDS:
            hero = outfit_hero("benika", base)
            m = build.assemble(hero)
            d = form_var(m.env)
            rows = []
            clip = cls.lib.by_name["attack.3"]
            for i in range(int(round(clip.length / TICK)) + 1):
                t = round(i * TICK, 4)
                rows.append((t, grips.measure(m, hero, action_pose(cls.lib, "attack.3", t, d))))
            cls.rows[base] = rows

    def test_the_club_head_lands_on_the_ground_on_every_form(self):
        for base, rows in self.rows.items():
            low = dict(rows)[0.55]["low"]
            with self.subTest(base=base):
                self.assertGreater(low, -0.5, "into the ground")
                self.assertLess(low, 0.8, "above the ground")

    def test_the_second_hand_stays_on_the_handle(self):
        for base, rows in self.rows.items():
            worst = max(m["grip2"] for _, m in rows)
            self.assertLess(worst, 2.1, base)

    def test_the_hit_did_not_move(self):
        clip = self.lib.by_name["attack.3"]
        self.assertEqual([e.t for e in clip.events if e.kind == "instruction" and e.value == "hit"], [0.55])
        committed = load_json("%s/src/main/resources/data/factionclash/hero_timings/_style_hammer.json" % ROOT)
        self.assertEqual(committed["animations"]["attack.3"]["frame_ticks"], {"windup": 13, "active": 1,
                                                                            "recovery": 15})


class FormScaledRotationTest(unittest.TestCase):
    """The pose library's `form` field: degrees per pixel of FORM_VAR on a bone's rotation."""

    def test_a_form_field_scales_the_rotation_with_the_form(self):
        lib = poses.Library({"a": {"bones": {"body": [10, 2, 0]}, "form": {"body": [1.5, 0, 0]}},
                             "b": {"extends": "a", "bones": {"chest": [3, 0, 0]}},
                             "m": {"bones": {"arm_r": [5, 6, 7]}, "form": {"arm_r": [1, 2, 3]}, "mirror": True}})
        ch = lib.get("b").chans()
        self.assertEqual(ch["body"]["rotation"], (10.0, 2.0, 0.0))
        self.assertEqual(ch["body"][poses.K_PREFIX + "rotation"], (1.5, 0.0, 0.0), "inherited")
        self.assertEqual(lib.get("m").chans()["arm_l"][poses.K_PREFIX + "rotation"], (1.0, -2.0, -3.0), "mirrored")
        with self.assertRaises(HerogenError):
            poses.Library({"x": {"form": {"body": [1, 2]}}}).get("x")

    def test_the_slam_writes_a_molang_rotation_and_reads_it_back_per_form(self):
        clip = library.style("hammer").by_name["attack.3"]
        tr = clip.tracks[("body", "rotation")]
        self.assertTrue(tr.scaled())
        doc = json.loads(dumps_json(animation_file([clip])))
        key = doc["animations"]["attack.3"]["bones"]["body"]["rotation"]["0.55"]["vector"]
        self.assertIsInstance(key[0], str)
        self.assertIn("v.fc_dhip", key[0])
        from render.animfile import parse_file
        back = parse_file(doc)["attack.3"].tracks[("body", "rotation")]
        for d in (-6.25, 1.75, 6.25):
            self.assertAlmostEqual(back.sample(0.55, d)[0], tr.sample(0.55, d)[0], places=2)
        self.assertLess(tr.sample(0.55, -6.25)[0], tr.sample(0.55, 6.25)[0] - 20, "the adult folds deeper")

    def test_rotation_turns_follow_the_file_convention(self):
        """A form-scaled rotation is in file degrees like a constant (GeckoLib 4.9.3 converts a non-constant
        rotation to radians and negates x and y at run time, as it does a constant at load): the FK of the sampled
        value is what the pose gives."""
        m = build.assemble(outfit_hero("benika", "adult_f"))
        d = form_var(m.env)
        pose = library.style("hammer").by_name["attack.3"].sample(0.55, d)
        r = pose["body"]["rotation"]
        w = Skeleton.from_model(m).world({"body": {"rotation": r}})
        self.assertTrue(np.allclose(w["body"][0], file_matrix(r), atol=1e-9))


if __name__ == "__main__":
    unittest.main()
