"""Art review of G6c, G5 and G10 (2026-11-13, notes/artgen.md "Art review: G6c, G5, G10"): the air streaming past a
flying hero (`wind` in the secondary bake), nested leg shells on their own inner planes, joints that cannot z-fight
(the forearm and the shin 0.1 px narrower), the hair kit's back slab off the head's back plane, the braided crown's
back layers, and Benika's stripes and horn that light up in her phase look."""
import json
import unittest

import numpy as np
from helpers import mannequin

import build
from anim import library, secondary
from anim import skeleton as S
from parts import INNER_MARGIN, LEG_BONES, apply_part, load_part
from rig import BASE_IDS, Model
from rot import rotate_points
from util import HerogenError

BUILDS = ("slim", "standard", "large")


def outfit(oid, base=None):
    import lint
    o = [x for x in lint.load_outfits() if x["id"] == oid][0]
    return lint.outfit_hero(o, base or o["base"])


def tip_dir(world, bone, d):
    r, _ = world[bone]
    v = r @ np.asarray(d, dtype=float)
    return v / np.linalg.norm(v)


class WindTest(unittest.TestCase):
    """Flight and gliding: capes, tails, ponytails and coat tails trail behind instead of hanging toward the
    ground or standing up off a level body (the G10 previews: Yugen's tails stood straight up in the glide, Maren's
    coat tails hung under her in fast flight)."""

    def sample(self, name, t=0.0):
        sk = S.for_base(S.REFERENCE_BASE)
        return sk, sk.world(library.core().by_name[name].sample(t))

    def test_the_glide_trails_the_cape_and_the_tail(self):
        sk, w = self.sample("move.glide")
        for bone, d in (("cape_2", (0, -1, 0.1)), ("tail_5", (0, 1, 0.4))):
            v = tip_dir(w, bone, d)
            self.assertGreater(v[2], 0.6, "%s points back (+z), not %s" % (bone, np.round(v, 2)))
            self.assertGreater(v[2], abs(v[1]), bone)

    def test_fast_flight_streams_the_ponytail_and_skirt_back(self):
        sk, w = self.sample("move.fly_fast")
        self.assertGreater(tip_dir(w, "tail_hair_3", (0, -1, 0.1))[2], 0.6)
        self.assertGreater(tip_dir(w, "skirt_b", (0, -1, 0))[2], 0.6)

    def test_ground_clips_feel_no_wind(self):
        for name in ("move.idle", "move.walk", "move.run", "move.sprint", "move.land"):
            self.assertNotIn("wind", library.core().by_name[name].meta, name)

    def test_an_action_takes_the_wind_of_its_base(self):
        core = library.core().by_name
        self.assertEqual(core["move.ride_cast"].meta["wind"], core["move.ride"].meta["wind"])

    def test_a_take_off_ramps_the_wind_in_and_a_landing_fades_it(self):
        w = secondary.Wind({"strength": 0.6, "ramp": [0.3, 0.5]})
        self.assertEqual(w.at(0.2), 0.0)
        self.assertAlmostEqual(w.at(0.4), 0.3)
        self.assertAlmostEqual(w.at(0.6), 0.6)
        f = secondary.Wind({"strength": 0.6, "fade": [0.0, 0.2]})
        self.assertAlmostEqual(f.at(0.0), 0.6)
        self.assertEqual(f.at(0.3), 0.0)

    def test_wind_is_validated(self):
        for bad in ({"strength": 0}, {"dir": [0, 0, 0]}, {"gust": 1}, {"ramp": [0.5, 0.2]}):
            with self.assertRaises(HerogenError, msg=bad):
                secondary.Wind(bad)

    def test_without_the_wind_the_glide_cape_hangs(self):
        """Sabotage: the same glide baked without wind lets the cape hang toward the ground."""
        name = "move.glide"
        recipe = dict(library._recipes_in(library.core_recipe_paths())[name])
        recipe.pop("wind")
        built = library._build("t", {name: recipe}, library.pose_library(), [library.core()], "move.idle",
                               [])
        sk = S.for_base(S.REFERENCE_BASE)
        v = tip_dir(sk.world(built.by_name[name].sample(0.0)), "cape_2", (0, -1, 0.1))
        self.assertLess(v[2], 0.6, "without wind the cape does not trail")


class LegShellTest(unittest.TestCase):
    """Nested leg shells (a boot cuff over its shaft, trousers under boots) have their own inner-side planes: on the
    inner side of a leg G6b's clamp put them all 0.05 px from the centre line, where they z-fight as soon as a combat
    stance spreads the legs (the 63 coplanar problems G5 recorded)."""

    def inner_planes(self, model, side):
        out = []
        for c in model.cubes:
            if c.part == "base" or c.rotation is not None:
                continue
            b = c.bone
            while b is not None and b not in LEG_BONES[side]:
                b = model.bones[b].parent if b in model.bones else None
            if b is None:
                continue
            x = c.origin[0] + c.size[0] if side == "r" else c.origin[0]
            out.append((c, x))
        return out

    def test_boot_cuff_and_shaft_differ_on_every_base(self):
        for base in BASE_IDS:
            for build_name in BUILDS:
                m = Model("t", base, build_name)
                apply_part(m, {"part": "legwear/boots"}, 0)
                build.settle_leg_shells(m)
                planes = {c.name.split("#")[1]: x for c, x in self.inner_planes(m, "r")}
                with self.subTest(base=base, build=build_name):
                    self.assertGreaterEqual(abs(planes["shaft"] - planes["cuff"]), 0.02 - 1e-9)
                    self.assertGreater(planes["cuff"], planes["shaft"], "the cuff (outer) is the one seen")
                    self.assertLessEqual(planes["cuff"], -INNER_MARGIN + 1e-9, "short of the centre line")

    def test_layers_of_two_parts(self):
        m = Model("t", "adult_m")
        apply_part(m, {"part": "outfit_bottom/trousers", "params": {"wraps": 1}}, 0)
        apply_part(m, {"part": "legwear/tabi_boots"}, 1)
        build.settle_leg_shells(m)
        shells = self.inner_planes(m, "r")
        gap = m.env["hip_x"] - m.env["leg_w"] / 2.0
        leg_inner, shin_inner = -gap, -gap - 0.05
        for i, (a, xa) in enumerate(shells):
            self.assertGreater(abs(xa - leg_inner), 0.01, a.name)
            self.assertGreater(abs(xa - shin_inner), 0.01, a.name)
            for b, xb in shells[i + 1:]:
                ya = (a.origin[1], a.origin[1] + a.size[1])
                yb = (b.origin[1], b.origin[1] + b.size[1])
                if ya[0] < yb[1] and yb[0] < ya[1] and xa > leg_inner and xb > leg_inner:
                    self.assertGreater(abs(xa - xb), 0.019, "%s / %s" % (a.name, b.name))


class JointTest(unittest.TestCase):
    def test_bending_segments_are_narrower(self):
        """A knee, an elbow, a wrist or an ankle bends about x, which keeps the side faces of the two segments in
        one plane: the forearm and the shin are 0.05 px a side narrower, so the wedge they overlap in cannot
        z-fight (the knee wedge G5 recorded on Ilsabet and Natsuho)."""
        for base in BASE_IDS:
            for build_name in BUILDS:
                m = Model("t", base, build_name)
                c = {x.name: x for x in m.cubes if x.part == "base"}
                for seg, outer in (("shin_r", ("thigh_r", "foot_r")), ("forearm_r", ("upper_arm_r", "hand_r"))):
                    for o in outer:
                        with self.subTest(base=base, build=build_name, seg=seg, other=o):
                            self.assertAlmostEqual(c[o].size[0] - c[seg].size[0], 0.1, places=6)
                            self.assertAlmostEqual(c[seg].origin[0] - c[o].origin[0], 0.05, places=6)


class HairKitTest(unittest.TestCase):
    def test_the_back_slab_is_off_the_heads_back_plane(self):
        """Every hair style's back slab starts 0.1 px behind the head (it lay on the head's back face and z-fought
        through its jagged hem whenever the head was still: the G6c and G5 open item)."""
        import glob
        import os
        from util import HEROSRC
        for path in sorted(glob.glob(os.path.join(HEROSRC, "parts", "hair", "*.json"))):
            with open(path) as f:
                part = json.load(f)
            back = [c for c in part["cubes"] if c["name"] == "back_base"]
            with self.subTest(part=part["id"]):
                self.assertEqual(len(back), 1)
                self.assertGreaterEqual(float(back[0]["origin"][2]), 5.1)

    def test_the_braid_crowns_back_shows_strands(self):
        m = Model("t", "teen_f")
        apply_part(m, {"part": "hair/braid_crown"}, 0)
        slab = [c for c in m.cubes if c.name.endswith("#back_base")][0]
        outer = slab.origin[2] + slab.size[2]
        back = [c for c in m.cubes if "#back" in c.name and c is not slab]
        self.assertGreaterEqual(len(back), 7)
        out = []
        for c in back:
            (x0, y0, z0), (sx, sy, sz) = c.origin, c.size
            pts = [(x0 + a, y0 + b, z0 + d) for a in (0, sx) for b in (0, sy) for d in (0, sz)]
            if c.rotation is not None:
                pts = rotate_points(pts, c.rotation, c.pivot)
            out.append(max(p[2] for p in pts) > outer)
        self.assertGreaterEqual(sum(out), 4, "the middle strands lie over the back slab, not inside it")


class RingTest(unittest.TestCase):
    def test_full_rings_have_no_doubled_panel(self):
        """An arc's angles include both ends, so a ring authored over [-180, 179.999] put its last panel on its
        first (flared_skirt, pleated_skirt, hakama, court_robe: a doubled back panel whose side faces z-fought,
        lint on chibi_f). A full ring ends one step short of 180."""
        import glob
        import os
        from util import HEROSRC
        for path in sorted(glob.glob(os.path.join(HEROSRC, "parts", "**", "*.json"), recursive=True)):
            with open(path) as f:
                part = json.load(f)
            for g in part.get("strands", []) or []:
                if g.get("path") != "arc" or not isinstance(g.get("angles"), list):
                    continue
                a0, a1 = (float(a) for a in g["angles"])
                n = int(g.get("count", 1))
                if n < 2:
                    continue
                step = (a1 - a0) / (n - 1)
                with self.subTest(part=part["id"], strands=g.get("name")):
                    if a1 - a0 > 300.0:          # a ring: the gap closing it is at least half a step
                        self.assertGreaterEqual(360.0 - (a1 - a0), 0.5 * step - 0.01,
                                                "the last panel would lie on the first")


class BenikaTest(unittest.TestCase):
    def test_stripes_and_horn_light_up_in_the_phase_look(self):
        """Her sample outfit names phases, so the stripes and the horn (glow "phase") are in the phase texture's
        glow mask; the first G6c sheet's "phase 2 at night" showed nothing (no phases, so no phase texture)."""
        for base in ("chibi_f", "teen_f", "adult_f"):
            b = build.build(outfit("benika", base))
            self.assertIsNotNone(b.p2, base)
            mask = b.p2_glow[:, :, 3] > 0
            base_mask = b.glow[:, :, 3] > 0
            for name in ("forearm_r", "horns/single_curved#horn0_0"):
                cubes = [c for c in b.model.cubes if c.name == name or c.name.startswith(name + ":")]
                self.assertTrue(cubes, name)
                lit = sum(int(mask[v:v + h, u:u + w].sum()) for c in cubes for (u, v, w, h) in
                          [tuple(int(x) for x in r) for r in c.uv.values()])
                base_lit = sum(int(base_mask[v:v + h, u:u + w].sum()) for c in cubes for (u, v, w, h) in
                               [tuple(int(x) for x in r) for r in c.uv.values()])
                with self.subTest(base=base, cube=name):
                    self.assertGreater(lit, base_lit)

    def test_the_stripes_are_whole_texel_rows(self):
        """Each forearm band is one whole texel row of the forearm (rows start at the elbow) on every base."""
        from expr import evaluate
        part = load_part("markings/arm_stripes")
        for base in BASE_IDS:
            env = Model("stripes", base).env
            for op in [o for o in part["paint"] if o.get("when") == "fore"]:
                lo, hi = (float(evaluate(v, env)) for v in op["y"])
                with self.subTest(base=base, y=op["y"]):
                    self.assertAlmostEqual(hi - lo, 1.0)
                    self.assertAlmostEqual((env["elbow_y"] - hi) % 1.0, 0.0)

    def test_a_stripe_shows_between_the_cuff_and_the_shell_on_every_form(self):
        """Her pushed-up sleeve's cuff and the short gauntlet leave a bare band of forearm (about 0.95 px on the
        chibi, 2.35 on the teen, 3.75 on the adult): at least one glowing row lies in it, and both on her own form
        (the adult). The first fix put the chibi's only band on the wrist row, under the shell."""
        for base, rows_wanted in (("chibi_f", 1), ("teen_f", 1), ("adult_f", 2)):
            b = build.build(outfit("benika", base))
            m, env = b.model, b.model.env
            arm = [c for c in m.cubes if c.name == "forearm_r"][0]
            shell = [c for c in m.cubes if c.name == "armour/gauntlets#shell"][0]
            cuff = [c for c in m.cubes if c.name == "outfit_top/open_coat#cuff"][0]
            bare = (shell.origin[1] + shell.size[1], cuff.origin[1])
            u, v, w, h = (int(x) for x in arm.uv["north"])
            lit = (b.p2_glow[v:v + h, u:u + w, 3] > 0).any(axis=1)
            shown = 0
            for k in np.nonzero(lit)[0]:
                y0, y1 = env["elbow_y"] - k - 1, env["elbow_y"] - k          # row k counts down from the elbow
                if min(y1, bare[1]) - max(y0, bare[0]) >= 0.7:
                    shown += 1
            with self.subTest(base=base, bare=bare):
                self.assertGreaterEqual(shown, rows_wanted)


if __name__ == "__main__":
    unittest.main()
