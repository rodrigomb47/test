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
        self.assertEqual((spec["eyes"], spec["lashes"]), ("almond", 1))
        for bad in ({"eyes": "huge"}, {"width": 8}, {"width": 4}, {"spacing": 3}, {"width": 7, "spacing": 6},
                    {"lashes": 4}, {"iris_width": 5}, {"brows": "unibrow"}, {"pupil": "x"}, {"fang": "middle"},
                    {"iris": "red"}, {"height": 7}):
            with self.subTest(bad=bad), self.assertRaises(HerogenError):
                frames(bad)

    def check_eye_set(self, spec, col):
        imgs = [F.eye_frame(spec, col, k)[0] for k in range(12)]
        skin = np.array(col.skin + (255,), dtype=np.uint8)
        white = np.array([255, 255, 255, 255], dtype=np.uint8)
        sclera = np.array(col.sclera + (255,), dtype=np.uint8)
        for k, img in enumerate(imgs):
            self.assertEqual(img.shape, (F.EH, F.EW, 4))
            self.assertTrue((img[:, :, 3] == 255).all(), "frame %d opaque" % k)
            rows = slice(F.LASH, F.EH) if k == 9 else slice(0, F.EH)
            self.assertTrue(np.array_equal(img[rows], img[rows, ::-1]), "frame %d mirrors" % k)
            allowed = {tuple(skin[:3]), tuple(col.lash), tuple(col.lash_soft)}
            for c in (0, F.EW - 1):
                self.assertTrue({tuple(p[:3]) for p in img[:, c]} <= allowed,
                                "frame %d: only the lash flick may reach column %d" % (k, c))
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
        for width, spacing in ((5, 2), (5, 4), (5, 6), (6, 2), (6, 4), (6, 6), (7, 2), (7, 4)):
            spec, col, _ = frames({"width": width, "spacing": spacing})
            self.check_eye_set(spec, col)

    def test_rest_brows_clear_of_the_lash(self):
        """Rest brows sit on rows 0-1 with a skin row (2) under them, so brow and lash never merge into one dark blob
        (only an upward lash flick may enter row 2, outside the brow's columns)."""
        for eyes in F.EYE_TEMPLATES:
            for lashes in range(3):
                for brows in ("thin_arched", "straight", "none"):
                    spec, col, _ = frames({"eyes": eyes, "lashes": lashes, "brows": brows})
                    skin = np.array(col.skin + (255,), dtype=np.uint8)
                    for k in (0, 1, 2, 3, 6, 10):
                        img = F.eye_frame(spec, col, k)[0]
                        self.assertTrue((img[2] == skin).all(), "%s/%d/%s frame %d row 2" % (eyes, lashes, brows, k))

    def test_blink_frames_keep_the_brows(self):
        """open, half and closed (the blink) share their brow rows, so brows never jump during a blink."""
        for brows in F.BROWS:
            spec, col, _ = frames({"brows": brows})
            rows = [F.eye_frame(spec, col, k)[0][0:2] for k in (0, 1, 2)]
            self.assertTrue(np.array_equal(rows[0], rows[1]) and np.array_equal(rows[0], rows[2]), brows)

    def test_expression_brows(self):
        spec, col, _ = frames({})
        brow = np.array(col.brow + (255,), dtype=np.uint8)

        def brow_rows(k):
            img = F.eye_frame(spec, col, k)[0]
            return [sorted({c for c in range(F.EW // 2) if (img[r, c] == brow).all()}) for r in (0, 1, 2, 3)]
        angry, sad = brow_rows(4), brow_rows(5)
        self.assertTrue(angry[2] and max(angry[2]) > max(angry[0]), "angry: the inner end drops toward the nose")
        self.assertTrue(sad[2] and min(sad[2]) < min(sad[0]), "sad: the outer end drops, the inner end stays up")
        surprised = brow_rows(6)
        self.assertTrue(surprised[0] and not surprised[1], "surprised: brows raised to row 0")

    def test_big_eye_structure(self):
        """The v2 eye: heavy lash on row 3, six body rows, an iris that runs dark (under the lash) to light (bottom),
        a 2x2 pupil and a white highlight of more than one texel."""
        spec, col, _ = frames({"eyes": "round", "lashes": 2})
        img = F.eye_frame(spec, col, 0)[0]
        right = img[:, :F.EW // 2]
        lash = np.array(col.lash + (255,), dtype=np.uint8)
        self.assertGreaterEqual(int((right[F.LASH] == lash).all(axis=1).sum()), spec["width"], "lash spans the eye")
        iris = [tuple(c) for c in col.iris]
        icol = [c for c in range(F.EW // 2) if tuple(right[F.BODY, c, :3]) == iris[0]][-1]
        tones = [iris.index(tuple(right[F.BODY + r, icol, :3])) for r in range(spec["height"])
                 if tuple(right[F.BODY + r, icol, :3]) in iris]
        self.assertEqual(tones[0], 0, "darkest tone under the lash")
        self.assertEqual(tones[-1], 4, "lightest tone at the bottom")
        self.assertEqual(tones, sorted(tones), "the iris lightens downward")
        white = (right.reshape(-1, 4)[:, :3] == 255).all(axis=1).sum()
        self.assertGreaterEqual(int(white), 3, "a 2x2-ish main highlight")
        pupil = (right.reshape(-1, 4)[:, :3] == np.array(col.pupil)).all(axis=1).sum()
        self.assertGreaterEqual(int(pupil), 2)

    def test_layout_matches_the_rig(self):
        from rig import load_rig
        self.assertEqual(F.check_layout(load_rig()), [])

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
        self.assertEqual(alpha.shape, (20, 20))
        self.assertFalse(alpha[4:14, :].any(), "hole under the eyes plane (rows 4-13)")
        self.assertFalse(alpha[16:20, 7:13].any(), "hole under the mouth plane's inner columns")
        self.assertTrue((alpha[16:20, 6] == 255).all() and (alpha[16:20, 13] == 255).all(),
                        "skin behind the mouth plane's edge columns, so an angled view never sees into the head")
        self.assertTrue((alpha[0:4, :] == 255).all(), "forehead")
        self.assertTrue((alpha[14:16, :] == 255).all(), "skin behind the cut-out cheeks plane")
        self.assertTrue((alpha[16:20, 0:6] == 255).all() and (alpha[16:20, 14:20] == 255).all())
        # every mouth frame's edge columns are exactly that skin (coincident texels must not differ)
        for mouth in F.MOUTHS:
            for fang in F.FANGS:
                spec, col, _ = frames({"mouth": mouth, "fang": fang})
                for k in range(8):
                    m = F.mouth_frame(spec, col, k)
                    for c in (0, F.MW - 1):
                        self.assertTrue((m[:, c] == np.array(col.skin + (255,), dtype=np.uint8)).all(),
                                        "%s/%s mouth frame %d column %d" % (mouth, fang, k, c))

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
