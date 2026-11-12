"""Faces, rig v3 (style pass 2): low wide-set eyes, eye shape families, iris, pupil and highlight styles, lash and
flick, brows, mouths, blush, marks and wear; every combination keeps the 12 eye frames, blinking and the moods."""
import itertools
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


def rgb_set(img):
    return {tuple(p[:3]) for p in img.reshape(-1, 4)}


class FaceSpecTest(unittest.TestCase):
    def test_defaults_and_errors(self):
        spec, _, _ = frames({}, "female")
        self.assertEqual((spec["eyes"], spec["lashes"], spec["brows"], spec["flick"]), ("round", 2, "thin_arched", "up"))
        spec, _, _ = frames({}, "male")
        self.assertEqual((spec["eyes"], spec["lashes"], spec["flick"]), ("almond", 1, "none"))
        self.assertEqual(frames({"eyes": "tsurime"})[0]["eyes"], "sharp", "aliases")
        self.assertEqual(frames({"eyes": "cat"})[0]["pupil"], "slit", "a shape's default pupil")
        self.assertEqual(frames({"highlights": 1})[0]["highlights"], "one", "the v2 integer form still reads")
        self.assertEqual(frames({"blush": "dots"})[0]["blush_style"], "dots")
        for bad in ({"eyes": "huge"}, {"width": 8}, {"width": 4}, {"spacing": 3}, {"width": 7, "spacing": 8},
                    {"lashes": 4}, {"iris_width": 5}, {"brows": "unibrow"}, {"pupil": "x"}, {"fang": "middle"},
                    {"iris": "red"}, {"iris_style": "plaid"}, {"flick": "sideways"}, {"highlights": 3},
                    {"glasses": "monocle"}, {"eyepatch": "middle"}, {"glasses": "round", "eyepatch": "r"},
                    {"marks": [{"type": "tattoo"}]}, {"marks": [{"type": "scar", "at": "knee"}]},
                    {"marks": [{"type": "face_paint", "color": "plaid"}]}, {"blush": "stars"}):
            with self.subTest(bad=bad), self.assertRaises(HerogenError):
                s, c, r = frames(bad)
                F.FaceColours(r, s, r["_hex"])

    def test_layout_matches_the_rig(self):
        from rig import load_rig
        self.assertEqual(F.check_layout(load_rig()), [])

    def test_eyes_low_and_wide(self):
        """Eye centre about 0.30 of the head height above the chin (references: 0.25-0.31; rig v2: 0.45) and the
        default gap between the eyes 0.3 of the face width (references 0.25-0.43; rig v2 0.1-0.2)."""
        spec, col, _ = frames({})
        img = F.eye_frame(spec, col, 0)[0]
        rows = [r for r in range(F.EH) if (img[r, :, :3] == np.array(col.sclera)).all(axis=1).any()
                or any(tuple(p[:3]) in {tuple(c) for c in col.iris} for p in img[r])]
        top, bottom = F.EYES_AT[0] + min(rows), F.EYES_AT[0] + max(rows)
        centre = 10 - (top + bottom + 1) / 4.0               # head-local px (face rows are 0.5 px)
        self.assertTrue(0.25 <= centre / 10.0 <= 0.35, centre)
        self.assertGreaterEqual(spec["spacing"] / float(F.EW), 0.3)


class EyeFrameTest(unittest.TestCase):
    def check_eye_set(self, spec, col, symmetric=True):
        imgs = [F.eye_frame(spec, col, k)[0] for k in range(12)]
        white = np.array([255, 255, 255, 255], dtype=np.uint8)
        sclera = np.array(col.sclera + (255,), dtype=np.uint8)
        for k, img in enumerate(imgs):
            self.assertEqual(img.shape, (F.EH, F.EW, 4))
            self.assertTrue((img[:, :, 3] == 255).all(), "frame %d opaque" % k)
            if symmetric:
                rows = slice(F.LASH, F.EH) if k == 9 else slice(0, F.EH)
                self.assertTrue(np.array_equal(img[rows], img[rows, ::-1]), "frame %d mirrors" % k)
            has_white = (img.reshape(-1, 4) == white).all(1).any()
            if k in F.OPEN_FRAMES and spec["highlights"] != "none":
                self.assertTrue(has_white, "open frame %d has a highlight" % k)
            if k in (2, 11, 3, 7):
                self.assertFalse((img.reshape(-1, 4) == sclera).all(1).any(), "closed frame %d shows no eye white" % k)
        for i in range(12):
            for j in range(i + 1, 12):
                self.assertFalse(np.array_equal(imgs[i], imgs[j]), "frames %d and %d differ" % (i, j))
        return imgs

    def test_every_shape_lash_weight_and_pupil(self):
        for eyes in F.EYE_SHAPES:
            for lashes in range(4):
                for pupil in F.PUPILS:
                    with self.subTest(eyes=eyes, lashes=lashes, pupil=pupil):
                        spec, col, _ = frames({"eyes": eyes, "lashes": lashes, "pupil": pupil})
                        self.check_eye_set(spec, col)

    def test_iris_styles_highlights_and_flicks(self):
        for style, hl, flick in itertools.product(F.IRIS_STYLES, F.HIGHLIGHTS, F.FLICKS):
            with self.subTest(style=style, highlights=hl, flick=flick):
                spec, col, _ = frames({"iris_style": style, "highlights": hl, "flick": flick, "lashes": 3})
                self.check_eye_set(spec, col)

    def test_spacing_and_width(self):
        for width in (5, 6, 7):
            for spacing in F.SPACINGS:
                if 2 * width + spacing > F.EW:
                    continue
                with self.subTest(width=width, spacing=spacing):
                    spec, col, _ = frames({"width": width, "spacing": spacing})
                    self.check_eye_set(spec, col)

    def test_shapes_differ(self):
        """Every eye shape draws a different open eye (the 'faces look the same' feedback)."""
        seen = {}
        for eyes in F.EYE_SHAPES:
            spec, col, _ = frames({"eyes": eyes})
            img = F.eye_frame(spec, col, 0)[0]
            key = img.tobytes()
            self.assertNotIn(key, seen, "%s draws like %s" % (eyes, seen.get(key)))
            seen[key] = eyes

    def test_rest_brows_clear_of_the_lash(self):
        """Rest brows sit on rows 0-1 with a skin row (2) under them, so brow and lash never merge (lash weights 0-2,
        no glasses: a rim may sit on row 2)."""
        for eyes in F.EYE_SHAPES:
            for lashes in range(3):
                for brows in ("thin_arched", "straight", "angled", "short", "none"):
                    spec, col, _ = frames({"eyes": eyes, "lashes": lashes, "brows": brows})
                    skin = np.array(col.skin + (255,), dtype=np.uint8)
                    for k in (0, 1, 2, 3, 6, 10):
                        img = F.eye_frame(spec, col, k)[0]
                        self.assertTrue((img[2] == skin).all(), "%s/%d/%s frame %d row 2" % (eyes, lashes, brows, k))

    def test_blink_frames_keep_the_brows(self):
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

    def test_eye_structure(self):
        """A lash over the whole eye, an iris that runs dark (under the lash) to light (bottom), a 2x2 pupil and a
        white highlight of more than one texel."""
        spec, col, _ = frames({"eyes": "round", "lashes": 2})
        img = F.eye_frame(spec, col, 0)[0]
        right = img[:, :F.EW // 2]
        lash = np.array(col.lash + (255,), dtype=np.uint8)
        lash_cols = {c for r in (F.LASH, F.LASH + 1) for c in range(F.EW // 2) if (right[r, c] == lash).all()}
        self.assertGreaterEqual(len(lash_cols), spec["width"], "lash spans the eye")
        iris = [tuple(c) for c in col.iris]
        icol = [c for c in range(F.EW // 2) if tuple(right[F.BODY, c, :3]) == iris[0]][-1]
        tones = [iris.index(tuple(right[r, icol, :3])) for r in range(F.BODY, F.EH) if tuple(right[r, icol, :3]) in iris]
        self.assertEqual(tones[0], 0, "darkest tone under the lash")
        self.assertEqual(tones[-1], 4, "lightest tone at the bottom")
        self.assertEqual(tones, sorted(tones), "the iris lightens downward")
        white = (right.reshape(-1, 4)[:, :3] == 255).all(axis=1).sum()
        self.assertGreaterEqual(int(white), 3, "a 2x2-ish main highlight")
        pupil = (right.reshape(-1, 4)[:, :3] == np.array(col.pupil)).all(axis=1).sum()
        self.assertGreaterEqual(int(pupil), 2)

    def test_sleepy_eyes_hide_the_iris_top(self):
        """A lidded shape draws the iris as if the eye were open: the lid hides its darkest rows (the jitome look)."""
        spec, col, _ = frames({"eyes": "sleepy"})
        img = F.eye_frame(spec, col, 0)[0]
        self.assertNotIn(tuple(col.iris[0]), rgb_set(img[:, :F.EW // 2]))
        spec, col, _ = frames({"eyes": "round"})
        self.assertIn(tuple(col.iris[0]), rgb_set(F.eye_frame(spec, col, 0)[0]))

    def test_glow_only_in_frame_10(self):
        spec, col, _ = frames({})
        for k in range(12):
            glow = F.eye_frame(spec, col, k)[1]
            self.assertEqual(bool(glow[:, :, 3].any()), k == 10, k)


class WearAndMarksTest(unittest.TestCase):
    def test_glasses_in_every_frame(self):
        for kind in F.GLASSES:
            spec, col, _ = frames({"glasses": kind})
            imgs = EyeFrameTest.check_eye_set(self, spec, col)
            for k, img in enumerate(imgs):
                self.assertIn(tuple(col.rim), rgb_set(img), "%s rims in frame %d" % (kind, k))
            self.assertFalse(spec["asymmetric"])

    def test_eye_patch_hides_one_eye_in_every_frame(self):
        spec, col, _ = frames({"eyepatch": "r"})
        self.assertTrue(spec["asymmetric"])
        imgs = EyeFrameTest.check_eye_set(self, spec, col, symmetric=False)
        g = F.FaceGeom(spec)
        cols = slice(g.a, g.b + 1)
        for k, img in enumerate(imgs):
            self.assertTrue(np.array_equal(img[F.BODY:, cols], imgs[0][F.BODY:, cols]), "patch unchanged in frame %d" % k)
        left = slice(F.EW - 1 - g.b, F.EW - g.a)
        self.assertFalse(np.array_equal(imgs[0][:, left], imgs[2][:, left]), "the other eye still blinks")

    def test_heterochromia(self):
        spec, col, _ = frames({"iris2": "#4FA3D9"})
        self.assertTrue(spec["asymmetric"])
        img = F.eye_frame(spec, col, 0)[0]
        self.assertTrue(rgb_set(img[:, :F.EW // 2]) & {tuple(c) for c in col.iris})
        self.assertTrue(rgb_set(img[:, F.EW // 2:]) & {tuple(c) for c in col.iris2})
        self.assertFalse(rgb_set(img[:, F.EW // 2:]) & ({tuple(c) for c in col.iris} - {tuple(c) for c in col.iris2}))

    def test_marks_show_and_stay_in_place(self):
        """Every mark changes the calm face; a mark under the eyes plane is drawn into every eye frame where the eye
        leaves skin, and the head front and the planes agree (one skin layer)."""
        plain_spec, plain_col, _ = frames({})
        plain = F.skin_layer(plain_spec, plain_col)
        for mark in ({"type": "freckles"}, {"type": "beauty_mark", "side": "l"},
                     {"type": "beauty_mark", "at": "mouth", "side": "r"}, {"type": "scar", "at": "eye", "side": "r"},
                     {"type": "scar", "at": "cheek", "side": "l"}, {"type": "scar", "at": "nose", "side": "both"},
                     {"type": "face_paint", "style": "stripes"}, {"type": "face_paint", "style": "tear"},
                     {"type": "face_paint", "style": "dots"}, {"type": "face_paint", "style": "chevron"},
                     {"type": "stubble"}, {"type": "bandage", "side": "r"}):
            with self.subTest(mark=mark):
                spec, col, _ = frames({"marks": [mark]})
                skin = F.skin_layer(spec, col)
                self.assertFalse(np.array_equal(skin, plain), "the mark shows")
                self.assertEqual(spec["asymmetric"], mark.get("side", "both" if mark["type"] in
                                 ("freckles", "face_paint", "stubble") else "r") != "both")
                head = F.head_front(spec, col, skin)
                opaque = head[:, :, 3] == 255
                self.assertTrue(np.array_equal(head[opaque], skin[opaque]), "head front = skin layer")
                for k in range(8):
                    m = F.mouth_frame(spec, col, k, skin)
                    r0, c0 = F.MOUTH_AT
                    for c in (0, F.MW - 1):
                        self.assertTrue(np.array_equal(m[:, c], skin[r0:r0 + F.MH, c0 + c]), "mouth edge column")

    def test_mouths_and_cheeks(self):
        for mouth in F.MOUTHS:
            for fang in F.FANGS:
                spec, col, _ = frames({"mouth": mouth, "fang": fang})
                ms = [F.mouth_frame(spec, col, k) for k in range(8)]
                for i in range(8):
                    self.assertTrue((ms[i][:, :, 3] == 255).all())
                    for j in range(i + 1, 8):
                        self.assertFalse(np.array_equal(ms[i], ms[j]), "%s/%s mouth %d = %d" % (mouth, fang, i, j))
        neutral = {}
        for mouth in F.MOUTHS:
            spec, col, _ = frames({"mouth": mouth})
            neutral.setdefault(F.mouth_frame(spec, col, 0).tobytes(), []).append(mouth)
        self.assertTrue(all(len(v) == 1 for v in neutral.values()), "every mouth style has its own rest mouth")
        for style in F.BLUSH_STYLES:
            spec, col, _ = frames({"blush_style": style})
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
        self.assertFalse(alpha[7:17, :].any(), "hole under the eyes plane (rows 7-16)")
        self.assertFalse(alpha[17:20, 7:13].any(), "hole under the mouth plane's inner columns")
        self.assertTrue((alpha[17:20, 6] == 255).all() and (alpha[17:20, 13] == 255).all(),
                        "skin behind the mouth plane's edge columns, so an angled view never sees into the head")
        self.assertTrue((alpha[0:7, :] == 255).all(), "forehead")
        self.assertTrue((alpha[17:20, 0:6] == 255).all() and (alpha[17:20, 14:20] == 255).all())

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

    def test_distinct(self):
        a, _, _ = frames({})
        b, _, _ = frames({"eyes": "sharp", "brows": "angled", "mouth": "smirk", "marks": ["freckles"],
                          "iris": "#4FA3D9"})
        self.assertEqual(F.distinct(a, a), 0)
        self.assertGreaterEqual(F.distinct(a, b), 5)


if __name__ == "__main__":
    unittest.main()
