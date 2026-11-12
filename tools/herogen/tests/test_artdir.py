"""Art direction pass 2 (docs/heroes-v2/notes/artgen.md): the fixes made after comparing the style pass 2 previews with
the owner's references. Hair shades gold-to-orange with a lit root (its own ramp), the crown is a parted mass instead
of a pinwheel of flat strands and sits lower (a smaller visual head), the hair covers the back of the head, jagged
slabs keep their outer columns (no striped "barcode" through their sides), stacked ear slabs read as one mass, and the
stubble and plaster marks read as what they are."""
import unittest

import numpy as np

from helpers import HEROGEN, mannequin  # noqa: F401

import build
import face as F
import palette as P
from paint import Painter, jag_depth
from parts import apply_part
from rig import Model
from rot import rotate_points
from test_style import library
from test_paint import cube_named, face_pixels


def hue_dist(a, b):
    return abs((a - b + 540.0) % 360.0 - 180.0)


def lch(rgb):
    return P.rgb_to_oklch(rgb)


def corners(c):
    o, s = c.origin, c.size
    return [(o[0] + a, o[1] + b, o[2] + d) for a in (0, s[0]) for b in (0, s[1]) for d in (0, s[2])]


class HairRampTest(unittest.TestCase):
    def test_warm_hair_shades_toward_orange_and_keeps_its_chroma(self):
        for h in ("#D9B04C", "#7B5A44", "#B9532C"):
            r = P.ramp(h, "hair")
            L, C, hue = lch(P.parse_hex(h))
            self.assertEqual(r[2], P.parse_hex(h), "tone 0 is the authored colour")
            Ls = [lch(c)[0] for c in r]
            self.assertEqual(Ls, sorted(Ls), h)
            self.assertLessEqual(hue_dist(lch(r[0])[2], P.HAIR_WARM_HUE), hue_dist(hue, P.HAIR_WARM_HUE) + 0.5, h)
            self.assertGreaterEqual(lch(r[0])[1], C * 0.95, "hair shadows stay saturated (gold to orange): %s" % h)
            self.assertGreaterEqual(Ls[4] - Ls[2], 0.17, "a wider lit step than cloth: %s" % h)

    def test_cool_hair_shades_toward_violet(self):
        r = P.ramp("#A98AD6", "hair")
        hue = lch(P.parse_hex("#A98AD6"))[2]
        self.assertLess(hue_dist(lch(r[0])[2], P.SHADOW_HUE), hue_dist(hue, P.SHADOW_HUE))

    def test_dark_hair_gets_a_cool_visible_sheen(self):
        for h in ("#26282E", "#2F3B5C"):
            r = P.ramp(h, "hair")
            L, C, hue = lch(P.parse_hex(h))
            self.assertGreaterEqual(lch(r[3])[0] - L, 0.11, "black and navy strands still read: %s" % h)
            self.assertLess(hue_dist(lch(r[4])[2], P.HAIR_SHEEN_HUE), 40, "a blue sheen: %s" % h)
            self.assertNotIn((0, 0, 0), r)

    def test_palette_uses_the_hair_ramp_for_hair_only(self):
        ramps = P.resolve({"hair": "#D9B04C", "primary": "#D9B04C"})
        self.assertEqual(ramps["hair"], P.ramp("#D9B04C", "hair"))
        self.assertEqual(ramps["primary"], P.ramp("#D9B04C"))
        self.assertNotEqual(ramps["hair"], ramps["primary"])


class HairKitTest(unittest.TestCase):
    def test_parted_crown_low_top_and_covered_nape(self):
        """Every hair style: a parted crown (mirrored strands lying over the dome from the centre line, no pinwheel of
        flat strands from one point), the hair top at most 3 px over the head (the visual head share), and the back
        of the head painted as hair (no bald strip under the strands)."""
        for pid in [p for p in library() if p.startswith("hair/")]:
            with self.subTest(part=pid):
                b = build.build(mannequin("teen_f", parts=[{"part": pid, "color": "hair"}], palette={
                    "hair": "#7B5A44", "skin": "#F9D9C3"}))
                m = b.model
                hy, top = m.env["head_y"], m.env["head_top"]
                cubes = [c for c in m.cubes if c.part == pid]
                part = [c for c in cubes if "#part" in c.name]
                self.assertEqual(len(part), 10, "five strands each side of the part")
                ends = [np.mean([q[0] for q in rotate_points(corners(c), c.rotation, c.pivot)]) for c in part]
                self.assertEqual(sum(1 for x in ends if x < -1), 5, "five lie toward the right, five the left")
                self.assertFalse([c for c in cubes if "whorl" in c.name or "crown_top" in c.name])
                hair_top = max(max(p[1] for p in (rotate_points(corners(c), c.rotation, c.pivot)
                                                   if c.rotation is not None else corners(c)))
                               for c in cubes if "ahoge" not in c.bone and "#spikes" not in c.name)
                self.assertLessEqual(hair_top - top, 3.0, "hair %.2f px over the head" % (hair_top - top))
                back = face_pixels(b, "head", "south")
                hair = {tuple(c) for c in b.ramps["hair"]}
                self.assertTrue(all(tuple(p[:3]) in hair for p in back.reshape(-1, 4)), "the nape is hair")
                side = face_pixels(b, "head", "east")
                skin = {tuple(c) for c in b.ramps["skin"]}
                self.assertTrue(any(tuple(p[:3]) in skin for p in side.reshape(-1, 4)), "the temple stays skin")
                self.assertTrue(any(tuple(p[:3]) in hair for p in side.reshape(-1, 4)), "behind the ear is hair")

    def test_strands_have_a_lit_root_and_a_dark_tip(self):
        b = build.build(mannequin("adult_f", parts=[{"part": "hair/long_layered", "color": "hair"}],
                                  palette={"hair": "#D9B04C"}))
        ramp = [tuple(c) for c in b.ramps["hair"]]
        c = next(c for c in b.model.cubes if "#back" in c.name and c.size[1] >= 10)
        px = face_pixels(b, c.name, "south")
        col = next(i for i in range(px.shape[1]) if px[-1, i, 3] or px[-2, i, 3])
        tones = [ramp.index(tuple(px[j, col, :3])) - 2 for j in range(px.shape[0]) if px[j, col, 3]]
        self.assertGreater(tones[0], tones[len(tones) // 2], "root lighter than the middle")
        self.assertGreater(tones[len(tones) // 2], tones[-1], "middle lighter than the tip")
        self.assertGreaterEqual(sum(1 for t in tones if t == tones[0]), int(len(tones) * 0.3) - 1,
                                "the lit root is about 30% of the strand")


class CutoutAndShadeTest(unittest.TestCase):
    def test_jag_keeps_its_outer_columns(self):
        for n, W, d in ((3, 8, 1), (5, 11, 2), (6, 12, 2), (2, 5, 1)):
            self.assertEqual(jag_depth(n, 0, W, d), 0)
            self.assertEqual(jag_depth(n, W - 1, W, d), 0)
            self.assertTrue(any(jag_depth(n, i, W, d) for i in range(W)), "still jagged inside")

    def test_jagged_slab_sides_are_closed(self):
        """A jagged slab's narrow faces follow its outer columns, so they stay whole and its bottom face never shows
        through them from inside (the "barcode" of the side views)."""
        b = build.build(mannequin("teen_f", parts=[{"part": "hair/long_layered", "color": "hair"}]))
        c = cube_named(b.model, "back_base")
        self.assertTrue(c.cutout.startswith("jagged_"))
        for face in ("east", "west"):
            px = face_pixels(b, c.name, face)
            self.assertTrue((px[:, :, 3] == 255).all(), face)
        self.assertTrue((face_pixels(b, c.name, "south")[-1, :, 3] == 0).any(), "the broad face is still jagged")

    def test_stacked_ear_slabs_read_as_one_mass(self):
        b = build.build(mannequin("chibi_f", parts=[{"part": "ears/cat", "color": "hair"}],
                                  palette={"hair": "#A98AD6"}))
        ramp = [tuple(c) for c in b.ramps["hair"]]
        for k in range(5):
            c = cube_named(b.model, "ear_%d" % k)
            self.assertEqual(c.shade, "mass")
            up, north = face_pixels(b, c.name, "up"), face_pixels(b, c.name, "north")
            tu = {ramp.index(tuple(p[:3])) for p in up.reshape(-1, 4) if p[3]}
            tn = {ramp.index(tuple(p[:3])) for p in north.reshape(-1, 4) if p[3]}
            self.assertEqual(tu, tn, "ledge %d is not a light stripe" % k)
        tip = cube_named(b.model, "ear_6")
        self.assertLess(ramp.index(tuple(face_pixels(b, tip.name, "north")[0, 0, :3])), 2, "a dark tip")

    def test_mass_shade_is_a_known_shade(self):
        m = Model("t", "teen_f")
        apply_part(m, {"part": "ears/cat", "color": "hair"}, 0)
        self.assertTrue(any(c.shade == "mass" for c in m.cubes))


class MarksTest(unittest.TestCase):
    def layer(self, marks, **extra):
        ramps = P.resolve({"skin": "#EEC9AD", "hair": "#26282E"})
        spec = F.face_spec(dict({"marks": marks}, **extra), "male", ramps["_hex"])
        col = F.FaceColours(ramps, spec, ramps["_hex"])
        return F.skin_layer(spec, col), col

    def test_stubble_is_a_soft_solid_shadow(self):
        img, col = self.layer(["stubble"])
        row = [tuple(p[:3]) for p in img[F.FACE - 1]]
        self.assertTrue(all(c != col.skin for c in row), "the whole chin row is shaded")
        for c in range(1, F.FACE - 2):
            self.assertFalse(row[c] == row[c + 2] != row[c + 1], "no every-other-texel stitch pattern at %d" % c)

    def test_plaster_lies_across_the_cheek(self):
        img, col = self.layer([{"type": "bandage", "side": "l"}])
        mask = np.array([[tuple(p[:3]) in (col.bandage, col.bandage_pad) for p in row] for row in img])
        rows, cols = np.nonzero(mask)
        self.assertEqual(len(rows), 8)
        self.assertGreater(cols.max() - cols.min() + 1, rows.max() - rows.min() + 1, "wider than tall")
        self.assertTrue(cols.max() < F.FACE, "inside the face")

    def test_cat_mouth_line_is_bold(self):
        ramps = P.resolve({"skin": "#FFE3D7", "hair": "#A98AD6"})
        spec = F.face_spec({"mouth": "cat"}, "female", ramps["_hex"])
        col = F.FaceColours(ramps, spec, ramps["_hex"])
        img = F.mouth_frame(spec, col, 0)
        colours = {tuple(p[:3]) for p in img.reshape(-1, 4)}
        self.assertIn(col.mouth_line_bold, colours)
        self.assertLess(sum(col.mouth_line_bold), sum(col.mouth_line))


if __name__ == "__main__":
    unittest.main()
