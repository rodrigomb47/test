"""C1 review gate 2 (HEROES_V2 14.4): the art director's fixes for Natsuho and Hinae, and the library previews' cast
under the owner's one-body rule (HEROES_V2 3.6: a hero is shown only in its own body; library sheets that prove one file
fits every body use the unnamed style fixtures)."""
import copy
import math
import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401  (sets the path)

import build
import lint
from anim.clip import form_var
from anim.skeleton import Skeleton
from render import raster
from render.scene import HeroAssets


def _tail_segment_angles(b, clip, t):
    """Each of the four tail segments' angle from straight up (degrees) at clip time t, on the built hero's body."""
    m = b.model
    w = Skeleton.from_model(m).world(clip.sample(t, form_var(m.env)))
    pts = [Skeleton.apply(w, "tail_%d" % i, m.catalogue["tail_%d" % i].pivot) for i in range(1, 6)]
    out = []
    for p, q in zip(pts, pts[1:]):
        v = q - p
        out.append(math.degrees(math.acos(max(-1.0, min(1.0, v[1] / np.linalg.norm(v))))))
    return out


class NatsuhoGate2Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hero = build.load_hero("natsuho_komezaki")
        cls.files, cls.b = build.build_files("natsuho_komezaki")

    def test_hiss_tail_stands_straight_up_through_the_bristle(self):
        """The hiss's tell (kit sheet: "ears flat, tail puffed"; the clip: "the tail straight up and trembling"): every
        segment of the tail within 35 degrees of straight up from the jolt to the blast. Keying only tail_1 and tail_2
        (C1 as committed) curled segments 3-5 back over at 42-107 degrees, a relaxed curl rather than a bristle."""
        clip = self.b.animations.by_name["move.hiss"]
        for t in (0.40, 0.50, 0.60, 0.70, 0.80, 0.90):
            angles = _tail_segment_angles(self.b, clip, t)
            self.assertLess(max(angles), 35.0, "t=%.2f: %s" % (t, ["%.0f" % a for a in angles]))

    def test_puffed_tail_wears_the_calico(self):
        """Phase 2's puffed fur (`tails/cat_puff`, cubes on p2_tail_* bones) carries the calico patches like the tail
        it covers: a share of its texels differ from the same hero built without `markings/calico` (none did when the
        marking painted only `tails/*`)."""
        bare = copy.deepcopy(self.hero)
        bare["art"]["parts"] = [p for p in bare["art"]["parts"] if p.get("part") != "markings/calico"]
        _, plain = build.build_files("natsuho_komezaki", bare)
        puff = [c for c in self.b.model.cubes if c.part == "tails/cat_puff"]
        self.assertTrue(puff)
        by_name = {c.name: c for c in plain.model.cubes}
        changed = total = 0
        for c in puff:
            self.assertEqual(c.uv, by_name[c.name].uv)          # an overlay adds no cube: the packing is the same
            for face, (u, v, w, h) in c.uv.items():
                u, v, w, h = int(u), int(v), int(w), int(h)
                a, p = self.b.image[v:v + h, u:u + w], plain.image[v:v + h, u:u + w]
                diff = np.any(a != p, axis=2)
                changed += int(diff.sum())
                total += diff.size
        self.assertGreater(changed / float(total), 0.1)
        self.assertLess(changed / float(total), 0.9)

    def test_tufts_are_short_flicks(self):
        """The bob's tufts (Natsuho's two) flick out at most 3 px from the side of the head (they stood out 3.5 px at
        72 degrees, like antennae, in the front view)."""
        m = self.b.model
        tufts = [c for c in m.cubes if c.part == "hair/bob" and "tuft" in c.name]
        self.assertEqual(len(tufts), 2)
        head = [c for c in m.cubes if c.part == "base" and c.bone == "head"]
        half = max(abs(x) for c in head for x in (c.box[0], c.box[3]))
        for c in tufts:
            self.assertLessEqual(max(c.size), 3.0, c.name)
            self.assertLess(max(abs(c.box[0]), abs(c.box[3])), half + 3.0, c.name)


class HinaeGate2Test(unittest.TestCase):
    def test_no_crown_strand_shows_through_the_fringe(self):
        """From the front, no crown strand of `hair/hime_long` is visible inside the fringe's outline: the two front
        crown strands are shorter (3.5) and end behind it. At their old lengths (4.75, 5) their dark tips showed
        between the fringe strands as a streak on her forehead, 153 px at 8x."""
        a = HeroAssets.build("hinae_shirasuzu")
        rect_of = {}
        for c in a.built.model.cubes:
            if c.part == "hair/hime_long":
                for r in c.uv.values():
                    rect_of[tuple(int(x) for x in r)] = c.name
        fr = a.render(raster.View(0.0, 0.0, 8), a.stance(False), frame=True)
        kind = np.zeros(fr.ids.shape, dtype=np.int8)          # 1 fringe, 2 crown
        for i, q in enumerate(fr.quads):
            n = rect_of.get(tuple(int(x) for x in q.rect), "")
            if "#fringe" in n:
                kind[fr.ids == i] = 1
            elif "#crown" in n:
                kind[fr.ids == i] = 2
        fy, fx = np.where(kind == 1)
        self.assertTrue(len(fy))
        box = kind[fy.min():fy.max() + 1, fx.min():fx.max() + 1]
        self.assertEqual(int((box == 2).sum()), 0)


class PreviewCastTest(unittest.TestCase):
    """The owner's one-body rule in the library previews: no slice hero on a body it does not have."""

    def test_sample_outfits_are_on_the_heros_one_body(self):
        """Every sample outfit's `base` (the only body outfits.png and weapons_poses.png draw it on) is an adult:
        all eight slice heroes are adults (SLICE_PLAN 3)."""
        for o in lint.load_outfits():
            self.assertTrue(o["base"].startswith("adult_"), o["id"])

    def test_outfit_sheet_draws_each_outfit_on_its_own_body_only(self):
        from render import partsheet
        labels = [label for label, _ in partsheet.OUTFIT_VIEWS]
        self.assertEqual(len(labels), len(set(labels)))
        self.assertNotIn("chibi f", labels)                      # no per-base columns any more

    def test_locomotion_hero_gifs_show_one_body(self):
        """hero_<name>.gif: a hero with a source in its own body, else its sample outfit on its one body only."""
        from anim import loco_preview
        outfits = {o["id"]: o for o in lint.load_outfits()}
        for group, (hid, bases, _, _) in loco_preview.HERO_GIFS.items():
            if bases is None:
                hero = build.load_hero(hid)
                self.assertEqual(hero["art"].get("form", "adult"), "adult", group)
            else:
                self.assertEqual(bases, [outfits[hid]["base"]], group)

    def test_style_sheets_show_no_named_hero(self):
        """The style sheets' cast is unnamed style fixtures in their own bodies (test_styles checks the weapons)."""
        from anim import style_preview
        for style, rows in style_preview.HEROES.items():
            for fixture_id, oid in rows:
                hero = style_preview.armed_fixture(fixture_id, oid)
                self.assertTrue(fixture_id.startswith("style_"))
                self.assertEqual(style_preview.base_of(hero), build.base_id_of(build.load_hero(fixture_id))[0])


if __name__ == "__main__":
    unittest.main()
