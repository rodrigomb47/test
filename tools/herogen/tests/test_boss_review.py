"""B5 review (HEROES_V2.md 14.4 gate 2 for the two boss heroes and the icons, by a separate art director): the
findings it fixed stay fixed.

- The Ender Dragon's wing membranes read as purple wings against her black dress (they were the near-black
  `secondary`, 9% from `primary` in greyscale, and vanished from behind and in greyscale).
- Her tail curls round her feet outside the floor-length skirt (it swept through the skirt's side panels), and her
  sky gaze draws the wings back clear of her hair (the tipped-back head pushed the hair through the wing arms).
- The Wither's cape tatters hang from the cape's back face, no lower than its hem (they hung inside the smoke gown's
  flare and showed as blue specks through its back panels).
- Ghast-Tear Torc no longer looks like Marrow Torc: its tears were two-pixel parts drawn in their darkest tone (the
  outline ate them), grey on the slot's grey.
- phases.png shows an earlier phase whose look is mostly light (the phase texture's glow) at night too.

Clipping is measured as in the review: points sampled inside each cube of one part (a 5x5x5 grid, 5% in from the
faces) that lie inside a cube of another (5% in from its faces), the animated pose at each sampled time."""
import unittest

import numpy as np

import icons
import palette
from anim import skeleton as S
from render import hero_previews as hp
from render.scene import HeroAssets
from rot import file_matrix

_G = (np.arange(5) + 0.5) / 5 * 0.9 + 0.05
GRID = np.array([(x, y, z) for x in _G for y in _G for z in _G])


def _frame(c):
    m = file_matrix(c.rotation) if c.rotation is not None else np.eye(3)
    pv = np.array(c.pivot if c.rotation is not None else (0, 0, 0), dtype=float)
    return m, pv


def penetration(sk, a_cubes, b_cubes, pose):
    """Sample points of the A cubes inside B cubes for a pose ({bone: {channel: value}})."""
    w = sk.world(pose)
    bs = []
    for cb in b_cubes:
        rb, tb = w[cb.bone]
        m, pv = _frame(cb)
        o, s = np.array(cb.origin, dtype=float), np.array(cb.size, dtype=float)
        bs.append((rb, tb, m, pv, o + 0.05 * s, o + 0.95 * s))
    total = 0
    for ca in a_cubes:
        r, t = w[ca.bone]
        m, pv = _frame(ca)
        o, s = np.array(ca.origin, dtype=float), np.array(ca.size, dtype=float)
        pts = (r @ ((m @ ((o + GRID * s) - pv).T).T + pv).T).T + t
        for rb, tb, mb, pvb, lo, hi in bs:
            q = (mb.T @ ((rb.T @ (pts - tb).T).T - pvb).T).T + pvb
            total += int(np.all((q > lo) & (q < hi), axis=1).sum())
    return total


def worst_over(a, sk, a_cubes, b_cubes, clip, n=24, base="move.idle"):
    """The most points of A inside B over an action played on `base` (with the secondary and wing loops)."""
    pb = a.playback(base, clip, 0.0)
    length = pb.length()
    worst = 0
    for i in range(n):
        t = length * i / (n - 1) * 0.999
        pose = hp.wings(a, hp.secondary(a, pb.pose(t), t), t, hp.wing_loop_of(clip))
        worst = max(worst, penetration(sk, a_cubes, b_cubes, pose))
    return worst


class DragonReviewTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.a = HeroAssets.build("the_ender_dragon")
        cls.model = cls.a.built.model
        cls.sk = S.Skeleton.from_model(cls.model)

    def part(self, prefix, skip=()):
        hidden = set(self.a.hidden(1))
        return [c for c in self.model.cubes if (c.part or "").startswith(prefix) and c.bone not in hidden
                and not c.name.endswith(skip)]

    def test_membranes_read_against_the_dress(self):
        """The webs paint `hair_accent` (the purple of her under-layer), at least 15% from `primary` in greyscale
        (sabotage: the old `secondary` is 9% away); the veins are the brighter `accent`."""
        webs = [c for c in self.model.cubes if c.part == "wings/dragon" and "#web" in c.name]
        self.assertEqual(len(webs), 10)
        self.assertEqual({c.slot for c in webs}, {"hair_accent"})
        pal = self.a.built.hero["art"]["palette"]
        self.assertGreaterEqual(palette.greyscale_contrast(pal["primary"], pal["hair_accent"]), 0.15)
        self.assertLess(palette.greyscale_contrast(pal["primary"], pal["secondary"]), 0.15)
        entry = [p for p in self.a.built.hero["art"]["parts"] if p["part"] == "wings/dragon"][0]
        self.assertEqual((entry["alt"], entry["detail"]), ("hair_accent", "accent"))

    def test_tail_curls_outside_the_skirt(self):
        """personal.2: at most 70 sample points of the tail inside the skirt on any tick (the tail's root through the
        back panels at rest is about 40; the committed curl of B5 swept it through the side panels: about 195)."""
        tail, skirt = self.part("tails/"), self.part("outfit_bottom/")
        rest = penetration(self.sk, tail, skirt, hp.wings(self.a, self.a.stance(), 0.0))
        self.assertLess(rest, 60)
        self.assertLessEqual(worst_over(self.a, self.sk, tail, skirt, "personal.2"), 70)

    def test_gaze_draws_the_wings_clear_of_the_hair(self):
        """personal.3: the wing bones (not the short roots, which pass under the hair to the back by design) at most
        150 points inside the hair (B5's gaze: about 290)."""
        wings = self.part("wings/", skip=("#root", "#root:l"))
        hair = self.part("hair/")
        self.assertLessEqual(worst_over(self.a, self.sk, wings, hair, "personal.3", n=16), 150)


class WitherReviewTest(unittest.TestCase):
    def test_tatters_ride_over_the_gown(self):
        """The cape's tatters start on the cape's back face and end no lower than its hem, and at rest none is inside
        the gown (B5's tatters: 112 points at rest, inside the gown's flare on every tick of the idle)."""
        a = HeroAssets.build("the_wither")
        model = a.built.model
        sk = S.Skeleton.from_model(model)
        tatters = [c for c in model.cubes if c.part == "back/cape_torn" and "#tatter" in c.name]
        gown = [c for c in model.cubes if c.part == "robes/smoke_gown"]
        self.assertEqual(len(tatters), 6)
        self.assertEqual(penetration(sk, tatters, gown, a.stance()), 0)
        low = [c for c in model.cubes if c.part == "back/cape_torn" and c.name.endswith("#low")][0]
        for c in tatters:
            self.assertGreater(c.origin[1], low.origin[1] - 1.5, c.name)        # no lower than the hem (1.5 px)


class IconReviewTest(unittest.TestCase):
    def test_ghast_tear_torc_shows_its_tears(self):
        """The tears below the terminals carry light tones, not only their outline (B5's: 9 of 10 pixels the darkest
        tone and the highlight, grey on the slot's grey; now at least four pixels at tone -1 or lighter or white),
        and the two torcs differ in at least 14 pixels."""
        shapes, materials, specs = icons.load(icons.SRC_DIR)
        spec = specs["ghast_tear_torc"]
        g, parts = icons.resolve(spec, shapes, None)
        rgba = icons.paint("ghast_tear_torc", spec, shapes, materials)
        tears = g == "p"
        ramp = [tuple(int(v) for v in c) for c in icons.ramp_of("p", parts["p"], spec, materials)]
        light = 0
        for y, x in zip(*np.nonzero(tears)):
            c = tuple(int(v) for v in rgba[y, x, :3])
            light += c not in ramp or ramp.index(c) >= 2
        self.assertGreaterEqual(int(tears.sum()), 12)
        self.assertGreaterEqual(light, 4)
        marrow = icons.paint("marrow_torc", specs["marrow_torc"], shapes, materials)
        self.assertGreaterEqual(int((marrow != rgba).any(-1).sum()), 14)


class PhaseSheetTest(unittest.TestCase):
    def test_night_cells(self):
        """A night cell for every phase drawn from the phase texture and for the last phase; heroes whose phases keep
        the base texture get only the last one, as before."""
        def visual(textures):
            return {"phases": [{"phase": i + 2, "texture": t} for i, t in enumerate(textures)]}
        self.assertEqual(hp.night_phases(visual(["p2", "p2"])), [2, 3])
        self.assertEqual(hp.night_phases(visual(["base", "base"])), [3])
        self.assertEqual(hp.night_phases(visual(["p2"])), [2])
        self.assertEqual(hp.night_phases({"phases": []}), [1])


if __name__ == "__main__":
    unittest.main()
