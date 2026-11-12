import unittest

from helpers import HEROGEN  # noqa: F401

import palette as P
from util import HerogenError


def hue_dist(a, b):
    return abs((a - b + 540.0) % 360.0 - 180.0)


class PaletteTest(unittest.TestCase):
    def test_hex(self):
        self.assertEqual(P.parse_hex("#B5423A"), (181, 66, 58))
        self.assertEqual(P.to_hex((181, 66, 58)), "#B5423A")
        for bad in ("B5423A", "#B5423", "#GGGGGG", 5):
            with self.assertRaises(HerogenError):
                P.parse_hex(bad)

    def test_oklch_round_trip(self):
        for h in ("#B5423A", "#34466F", "#F3D5C0", "#000000", "#FFFFFF", "#00FF00", "#808080"):
            rgb = P.parse_hex(h)
            back = P.oklch_to_rgb(*P.rgb_to_oklch(rgb))
            for a, b in zip(rgb, back):
                self.assertLessEqual(abs(a - b), 1, h)

    def test_ramp_rules(self):
        for h in ("#B5423A", "#34466F", "#2E7A4E", "#D9B04C", "#C9CED6"):
            r = P.ramp(h)
            self.assertEqual(len(r), 5)
            self.assertEqual(r[2], P.parse_hex(h), "tone 0 is the authored colour")
            Ls = [P.rgb_to_oklch(c)[0] for c in r]
            self.assertEqual(Ls, sorted(Ls), "lightness rises with the tone: %s" % h)
            L, C, hue = P.rgb_to_oklch(P.parse_hex(h))
            if C > 0.05:
                self.assertLess(hue_dist(P.rgb_to_oklch(r[0])[2], P.SHADOW_HUE), hue_dist(hue, P.SHADOW_HUE) + 0.5,
                                "shadows lean blue-violet: %s" % h)
                self.assertLess(P.rgb_to_oklch(r[0])[1], C, "shadows lose chroma: %s" % h)
                self.assertLess(hue_dist(P.rgb_to_oklch(r[4])[2], P.HIGHLIGHT_HUE),
                                hue_dist(hue, P.HIGHLIGHT_HUE) + 0.5, "highlights lean yellow: %s" % h)

    def test_skin_shadows_lean_red(self):
        skin = "#F3D5C0"
        hue = P.rgb_to_oklch(P.parse_hex(skin))[2]
        shadow = P.rgb_to_oklch(P.ramp(skin, "skin")[0])[2]
        self.assertLess(hue_dist(shadow, P.SKIN_SHADOW_HUE), hue_dist(hue, P.SKIN_SHADOW_HUE))

    def test_no_pure_black_or_white_off_tone_zero(self):
        for h in ("#000000", "#FFFFFF", "#101010", "#F8F8F8"):
            r = P.ramp(h)
            for t in (0, 1, 3, 4):
                self.assertNotIn(r[t], [(0, 0, 0), (255, 255, 255)], h)

    def test_resolve(self):
        ramps = P.resolve({"hair": "#34466F", "skin_shade": "#D9A890"})
        for slot in P.SLOTS:
            if slot != "skin_shade":
                self.assertIn(slot, ramps)
        for slot in P.FIXED:
            self.assertIn(slot, ramps)
        self.assertEqual(ramps["skin"][1], (0xD9, 0xA8, 0x90))
        self.assertEqual(ramps["_hex"]["trim"], ramps["_hex"]["accent"])
        with self.assertRaises(HerogenError):
            P.resolve({"shirt": "#FFFFFF"})

    def test_checks(self):
        self.assertAlmostEqual(P.delta_e76("#123456", "#123456"), 0.0)
        self.assertAlmostEqual(P.delta_e76("#000000", "#FFFFFF"), 100.0, places=1)
        self.assertEqual(P.check_palette({"primary": "#B5423A", "secondary": "#EEE7DA", "accent": "#2C2F3B"}), [])
        notes = P.check_palette({"primary": "#808080", "secondary": "#828282", "accent": "#000000"})
        self.assertTrue(any("delta-E" in n for n in notes))
        self.assertTrue(any("greyscale" in n for n in notes))
        self.assertTrue(any("pure black" in n for n in notes))


if __name__ == "__main__":
    unittest.main()
