import unittest

import numpy as np

from helpers import HEROGEN  # noqa: F401

import face as F
import palette as P
from util import HerogenError

PAL = {"skin": "#F3D5C0", "hair": "#34466F", "eyes": "#E0A033"}


def frames(spec_in, family="female"):
    ramps = P.resolve(PAL)
    spec = F.face_spec(spec_in, family, ramps["_hex"])
    col = F.FaceColours(ramps, spec, ramps["_hex"])
    return spec, col, ramps


class FaceTest(unittest.TestCase):
    def test_defaults_and_errors(self):
        spec, _, _ = frames({}, "female")
        self.assertEqual((spec["eyes"], spec["lashes"], spec["brows"]), ("round", 2, "thin_arched"))
        spec, _, _ = frames({}, "male")
        self.assertEqual((spec["eyes"], spec["lashes"]), ("almond", 0))
        for bad in ({"eyes": "huge"}, {"width": 7}, {"spacing": 3}, {"width": 6, "spacing": 4}, {"lashes": 4},
                    {"brows": "unibrow"}, {"pupil": "x"}, {"fang": "middle"}, {"iris": "red"}):
            with self.subTest(bad=bad), self.assertRaises(HerogenError):
                frames(bad)

    def check_eye_set(self, spec, col):
        imgs = [F.eye_frame(spec, col, k)[0] for k in range(12)]
        skin = np.array(col.skin + (255,), dtype=np.uint8)
        white = np.array([255, 255, 255, 255], dtype=np.uint8)
        sclera = np.array(col.sclera + (255,), dtype=np.uint8)
        for k, img in enumerate(imgs):
            self.assertTrue((img[:, :, 3] == 255).all(), "frame %d opaque" % k)
            rows = slice(2, 8) if k == 9 else slice(0, 8)
            self.assertTrue(np.array_equal(img[rows], img[rows, ::-1]), "frame %d mirrors" % k)
            for c in (0, 15):
                self.assertTrue((img[2:, c] == skin).all(), "frame %d keeps column %d free for side locks" % (k, c))
            has_white = (img.reshape(-1, 4) == white).all(1).any()
            if k in F.OPEN_FRAMES:
                self.assertTrue(has_white, "open frame %d has a highlight" % k)
            if k in (2, 11, 3, 7):
                self.assertFalse((img.reshape(-1, 4) == sclera).all(1).any(), "closed frame %d shows no eye white" % k)
        for i in range(12):
            for j in range(i + 1, 12):
                self.assertFalse(np.array_equal(imgs[i], imgs[j]), "frames %d and %d differ" % (i, j))

    def test_every_template_and_lash_weight(self):
        for eyes in F.EYE_TEMPLATES:
            for lashes in range(4):
                for pupil in F.PUPILS:
                    with self.subTest(eyes=eyes, lashes=lashes, pupil=pupil):
                        spec, col, _ = frames({"eyes": eyes, "lashes": lashes, "pupil": pupil})
                        self.check_eye_set(spec, col)

    def test_spacing_and_width(self):
        for width, spacing in ((4, 2), (4, 4), (5, 2), (5, 4), (6, 2)):
            spec, col, _ = frames({"width": width, "spacing": spacing})
            self.check_eye_set(spec, col)

    def test_glow_only_in_frame_10(self):
        spec, col, _ = frames({})
        for k in range(12):
            glow = F.eye_frame(spec, col, k)[1]
            self.assertEqual(bool(glow[:, :, 3].any()), k == 10, k)

    def test_mouth_and_cheeks(self):
        for mouth in F.MOUTHS:
            for fang in F.FANGS:
                spec, col, _ = frames({"mouth": mouth, "fang": fang})
                ms = [F.mouth_frame(spec, col, k) for k in range(8)]
                for i in range(8):
                    self.assertTrue((ms[i][:, :, 3] == 255).all())
                    for j in range(i + 1, 8):
                        self.assertFalse(np.array_equal(ms[i], ms[j]), "%s/%s mouth %d = %d" % (mouth, fang, i, j))
        spec, col, _ = frames({})
        cs = [F.cheek_frame(spec, col, k) for k in range(4)]
        self.assertFalse(cs[0][:, :, 3].any())
        for k in (1, 2, 3):
            self.assertTrue(cs[k][:, :, 3].any())
            self.assertTrue(np.array_equal(np.unique(cs[k][:, :, 3]), [0, 255]), "cut-out alpha only")

    def test_head_front_holes(self):
        spec, col, _ = frames({})
        hf = F.head_front(spec, col)
        alpha = hf[:, :, 3]
        self.assertFalse(alpha[2:10, :].any(), "hole under the eyes plane (rows 2-9)")
        self.assertFalse(alpha[12:16, 4:12].any(), "hole under the mouth plane")
        self.assertTrue((alpha[0:2, :] == 255).all())
        self.assertTrue((alpha[10:12, :] == 255).all(), "skin behind the cut-out cheeks plane")
        self.assertTrue((alpha[12:16, 0:4] == 255).all() and (alpha[12:16, 12:16] == 255).all())

    def test_mood_table(self):
        spec, _, _ = frames({"blush": True, "fang": "left",
                             "overrides": {"happy": {"eyes": "focused", "mouth": 1}, "calm": {"cheeks": 1}}})
        t = F.mood_table(spec)
        self.assertEqual(list(t), F.MOODS)
        self.assertEqual(t["happy"], {"eyes": 8, "mouth": 1, "cheeks": 1})
        self.assertEqual(t["calm"]["cheeks"], 1)
        self.assertEqual(t["smug"]["mouth"], 7, "fang heroes grin with the fang")
        for bad in ({"grumpy": {"eyes": 0}}, {"calm": {"eyes": 12}}, {"calm": {"mouth": "yell"}}):
            spec, _, _ = frames({"overrides": bad})
            with self.assertRaises(HerogenError):
                F.mood_table(spec)


if __name__ == "__main__":
    unittest.main()
