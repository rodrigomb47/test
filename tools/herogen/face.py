"""Faces (design 4.6 and 13.5): eye, mouth and cheek frames at double resolution, and the head front.

Layout on the 16x8 eye plane (the same for every hero, so frames line up): brows in rows 0-1, the upper lash in row 2,
the eye body in rows 3-7; rest brows use row 0 only, so a skin row separates them from the lash. The two eyes mirror
about the plane's centre line, stay inside columns 1-14 (column 0 and 15 are where side locks may hang) and have 2 or 4
texels between their inner corners (mirroring makes the gap even).
In the frames the viewer's left eye is the hero's right eye (column 0 is the hero's right, -x).

The head front (16x16) is skin with transparent holes exactly under the opaque eyes plane (rows 2-9) and under the mouth
plane's inner columns (rows 12-15, columns 5-10; see head_front_holes); the cheeks plane in front of rows 10-11 is
cut-out (transparent except blush, sweat, tears).
"""
import numpy as np

from palette import brow_colour, lash_colour, mix, parse_hex, ramp
from util import HerogenError

EYE_FRAMES = ["open", "half", "closed", "happy", "angry", "sad", "surprised", "pain", "focused", "smug", "glowing",
              "sleeping"]
MOUTH_FRAMES = ["neutral", "smile", "talk_a", "talk_b", "grit", "frown", "cat", "fang_grin"]
CHEEK_FRAMES = ["none", "blush", "sweat", "tears"]
OPEN_FRAMES = (0, 4, 5, 6, 8, 9, 10)          # frames that show an iris with a highlight

MOODS = ["calm", "happy", "angry", "focused", "hurt", "tired", "sad", "smug", "surprised", "asleep", "flustered",
         "determined"]
MOOD_FRAMES = {   # eyes, mouth, cheeks
    "calm": (0, 0, 0), "happy": (3, 1, 0), "angry": (4, 4, 0), "focused": (8, 0, 0), "hurt": (7, 4, 0),
    "tired": (1, 0, 2), "sad": (5, 5, 0), "smug": (9, 1, 0), "surprised": (6, 2, 0), "asleep": (11, 0, 0),
    "flustered": (6, 5, 1), "determined": (4, 0, 0),
}
BLINK_OVER = [0, 4, 5, 8, 9]                  # eye frames a blink may replace
BLINK_PROFILES = {
    "normal": {"interval": [2.5, 6.0], "ticks": [1, 2, 1]},
    "nervous": {"interval": [1.5, 3.5], "ticks": [1, 2, 1]},
    "calm": {"interval": [4.0, 7.0], "ticks": [1, 2, 1]},
    "sleepy": {"interval": [3.0, 5.0], "ticks": [2, 4, 2]},
}

# Eye templates: body width and height, and per body row the (outer, inner) inset of the opening.
EYE_TEMPLATES = {
    "round": {"w": 5, "h": 5, "rows": [(1, 1), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 3},
    "almond": {"w": 5, "h": 4, "rows": [(1, 0), (0, 0), (0, 0), (1, 1)], "iris": 3},
    "sharp": {"w": 6, "h": 4, "rows": [(0, 1), (0, 0), (1, 0), (2, 1)], "iris": 3},
    "droopy": {"w": 5, "h": 4, "rows": [(2, 0), (1, 0), (0, 0), (0, 1)], "iris": 3},
    "cat": {"w": 6, "h": 5, "rows": [(0, 1), (0, 0), (0, 0), (1, 0), (2, 1)], "iris": 3},
    "sleepy": {"w": 5, "h": 4, "rows": [(1, 1), (0, 0), (0, 0), (1, 1)], "iris": 3, "lid": 1},
    "wide": {"w": 6, "h": 5, "rows": [(1, 1), (0, 0), (0, 0), (0, 0), (0, 0)], "iris": 4},
    "narrow": {"w": 5, "h": 4, "rows": [(1, 1), (0, 0), (0, 0), (1, 1)], "iris": 2},
    "heavy_lid": {"w": 5, "h": 5, "rows": [(0, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 3, "lid": 1},
}
BROWS = ("thin_arched", "straight", "straight_thick", "worried", "bold", "none")
MOUTHS = {"small": 2, "soft": 2, "wide": 4, "cat": 2, "stern": 4}
PUPILS = ("round", "slit", "star", "ring", "none")
FANGS = ("none", "left", "right", "both")

FAMILY_DEFAULTS = {
    "female": {"eyes": "round", "lashes": 2, "brows": "thin_arched", "mouth": "small", "closed": "curve"},
    "male": {"eyes": "almond", "lashes": 0, "brows": "straight", "mouth": "small", "closed": "straight"},
}


# ------------------------------------------------------------------ spec

def face_spec(art_face, family, palette_hex):
    """Resolve art.face with family defaults into the parameters the painter uses."""
    f = dict(FAMILY_DEFAULTS[family])
    f.update({"pupil": "round", "iris": palette_hex["eyes"], "highlights": 2, "spacing": 2, "fang": "none",
              "blush": False, "lower_lash": False, "overrides": {}})
    f.update(art_face or {})
    if f["eyes"] not in EYE_TEMPLATES:
        raise HerogenError("art.face.eyes %r is unknown (%s)" % (f["eyes"], ", ".join(EYE_TEMPLATES)))
    t = EYE_TEMPLATES[f["eyes"]]
    f.setdefault("width", t["w"])
    f.setdefault("height", t["h"])
    f.setdefault("iris_width", t["iris"])
    checks = [("width", 4, 6), ("height", 4, 5), ("iris_width", 2, 4), ("lashes", 0, 3), ("highlights", 1, 2)]
    for key, lo, hi in checks:
        if not isinstance(f[key], int) or not lo <= f[key] <= hi:
            raise HerogenError("art.face.%s must be a whole number %d-%d" % (key, lo, hi))
    if f["spacing"] not in (2, 4):
        raise HerogenError("art.face.spacing must be 2 or 4 (the eyes mirror, so the gap is even)")
    inner = 7 - f["spacing"] // 2
    if inner - f["width"] + 1 < 1:
        raise HerogenError("art.face: width %d with spacing %d leaves column 0 (side locks) covered"
                           % (f["width"], f["spacing"]))
    for key, allowed in (("brows", BROWS), ("mouth", MOUTHS), ("pupil", PUPILS), ("fang", FANGS),
                         ("closed", ("curve", "straight"))):
        if f[key] not in allowed:
            raise HerogenError("art.face.%s %r is unknown (%s)" % (key, f[key], ", ".join(allowed)))
    parse_hex(f["iris"])
    rows = list(t["rows"])
    while len(rows) < f["height"]:
        rows.insert(len(rows) - 1, (0, 0))
    f["rows"] = rows[:f["height"] - 1] + [rows[-1]] if len(rows) > f["height"] else rows
    f["lid"] = t.get("lid", 0)
    return f


def mood_table(spec):
    """Mood -> {eyes, mouth, cheeks} frame numbers, with the hero's overrides applied."""
    table = {}
    for m in MOODS:
        e, mo, c = MOOD_FRAMES[m]
        if spec.get("blush") and m == "happy":
            c = 1
        if spec.get("fang") != "none" and m == "smug":
            mo = 7
        table[m] = {"eyes": e, "mouth": mo, "cheeks": c}
    for mood, ov in (spec.get("overrides") or {}).items():
        if mood not in table:
            raise HerogenError("art.face.overrides: mood %r is unknown (%s)" % (mood, ", ".join(MOODS)))
        for key, names in (("eyes", EYE_FRAMES), ("mouth", MOUTH_FRAMES), ("cheeks", CHEEK_FRAMES)):
            if key in ov:
                v = ov[key]
                if isinstance(v, str):
                    if v not in names:
                        raise HerogenError("art.face.overrides.%s.%s: frame %r is unknown" % (mood, key, v))
                    v = names.index(v)
                if not isinstance(v, int) or not 0 <= v < len(names):
                    raise HerogenError("art.face.overrides.%s.%s: frame %r is out of range" % (mood, key, v))
                table[mood][key] = v
    return table


# ------------------------------------------------------------------ colours

class FaceColours:
    def __init__(self, ramps, spec, palette_hex):
        skin = ramps["skin"]
        self.skin = skin[2]
        self.skin_dark = skin[1]
        self.lash = lash_colour(palette_hex["hair"])
        self.lash_soft = mix(self.lash, self.skin, 0.35)
        self.brow = brow_colour(palette_hex["hair"])
        self.brow_soft = mix(self.brow, self.skin, 0.45)
        self.sclera = ramps["sclera"][2]
        self.white = (255, 255, 255)
        self.iris = ramp(spec["iris"])
        self.glow = ramps["glow"]
        self.pupil = mix(self.iris[0], self.lash, 0.5)
        self.mouth_line = mix(skin[0], ramps["mouth_dark"][2], 0.5)
        self.mouth_soft = skin[0]
        self.mouth_dark = ramps["mouth_dark"][2]
        self.tongue = ramps["tongue"][2]
        self.teeth = ramps["teeth"][2]
        self.teeth_shade = ramps["teeth"][1]
        self.blush_light = mix(self.skin, ramps["blush"][2], 0.42)
        self.blush = mix(self.skin, ramps["blush"][2], 0.62)
        self.tear = ramps["tear"][2]
        self.tear_light = ramps["tear"][4]


# ------------------------------------------------------------------ eyes

class EyeCanvas:
    """One 16x8 eye frame. Drawing uses (row, x) where x is the eye-local column (0 = outer corner) of the hero's
    right eye; `put` draws it and its mirror for the left eye unless `only` names one side ('r' or 'l')."""

    def __init__(self, spec, col):
        self.img = np.zeros((8, 16, 4), dtype=np.uint8)
        self.img[:, :] = col.skin + (255,)
        self.glow = np.zeros((8, 16, 4), dtype=np.uint8)
        self.W, self.H = spec["width"], spec["height"]
        self.b = 7 - spec["spacing"] // 2          # inner corner column of the hero's right eye
        self.a = self.b - self.W + 1

    def cols(self, x):
        c = self.a + x
        return c, 15 - c

    def put(self, row, x, rgb, only=None, glow=False):
        if not 0 <= row < 8:
            return
        cr, cl = self.cols(x)
        for side, c in (("r", cr), ("l", cl)):
            if only and side != only:
                continue
            if 0 <= c < 16:
                self.img[row, c] = tuple(rgb) + (255,)
                if glow:
                    self.glow[row, c] = tuple(rgb) + (255,)


def _span(spec, r):
    o, i = spec["rows"][r]
    return o, spec["width"] - 1 - i


def _iris_cols(spec, shrink):
    I = max(1, spec["iris_width"] - shrink)
    start = int(np.floor((spec["width"] - I) / 2.0 + 0.5))
    return list(range(start, start + I))


def _draw_open(cv, spec, col, lid=0, shrink=0, iris_rel_lid=True, highlight=True, pupil=True, glow=False,
               white_top=False, lash_shift=None):
    """An open eye with the upper lid at body row `lid`. iris_rel_lid: iris tones count from the lid (a narrowed eye
    shows the whole iris); otherwise from the top (a half-closed eye hides the iris top)."""
    W, H = spec["width"], spec["height"]
    iris = col.glow if glow else col.iris
    icols = _iris_cols(spec, shrink)
    lid = max(lid, spec["lid"])
    n = H - lid if iris_rel_lid else H
    for r in range(lid, H):
        row = 3 + r
        o, i = _span(spec, r)
        rr = r - lid if iris_rel_lid else r
        # iris gradient: dark under the lid, lighter toward the bottom (anime style)
        tone = 0 if rr == 0 else (3 if rr == n - 1 else (1 if rr == 1 else 2))
        for x in range(o, i + 1):
            cv.put(row, x, col.sclera)
            if x in icols and not (white_top and r == lid):
                cv.put(row, x, iris[tone], glow=glow)
    vis = [r for r in range(lid, H)]
    if pupil and len(vis) >= 2:
        prow = 3 + vis[min(len(vis) - 1, 1 if len(vis) < 4 else 2)]
        mid = icols[len(icols) // 2]
        ptype = spec["pupil"]
        if ptype == "round":
            for x in (icols[(len(icols) - 1) // 2], mid):
                cv.put(prow, x, col.white if glow else col.pupil, glow=glow)
        elif ptype == "slit":
            for r in vis[:-1]:
                cv.put(3 + r, mid, col.white if glow else col.pupil, glow=glow)
        elif ptype == "star":
            cv.put(prow, mid, col.white, glow=glow)
            cv.put(prow - 1, mid, iris[4], glow=glow)
        elif ptype == "ring":
            cv.put(prow, mid, col.white if glow else col.pupil, glow=glow)
            for x in icols:
                if x != mid:
                    cv.put(prow, x, iris[4], glow=glow)
    _draw_lash(cv, spec, col, lid, lash_shift)
    if spec["lower_lash"]:
        o, i = _span(spec, H - 1)
        for x in range(o, o + 2):
            cv.put(3 + H - 1, x, col.lash_soft)
    if highlight and vis:
        # the main highlight goes on the topmost iris (or pupil) texel the lid and lashes left visible (upper half)
        iris_px = {tuple(c) for c in iris} | {tuple(col.pupil)}
        spots = [(3 + r, x) for r in vis[:max(2, len(vis) // 2 + 1)] for x in icols
                 if tuple(cv.img[3 + r, cv.cols(x)[0], :3]) in iris_px]
        if spots:
            hrow, hx = spots[0]
            cv.put(hrow, hx, col.white, glow=glow)
            if len(icols) >= 4 and hx + 1 in icols:
                cv.put(hrow, hx + 1, col.white, glow=glow)
        if spec["highlights"] >= 2 and len(vis) >= 3:
            cv.put(3 + vis[-1] - (1 if len(vis) >= 4 else 0), icols[-1], iris[4], glow=glow)


def _draw_lash(cv, spec, col, lid, shift=None):
    """Upper lash above body row `lid`; shift(x) moves single texels down (angry, sad)."""
    W = spec["width"]
    weight = spec["lashes"]
    row = 2 + lid
    o, i = _span(spec, min(lid, spec["height"] - 1))
    xs = range(o, i + 1) if weight == 0 else range(0, W)
    for x in xs:
        dy = shift(x) if shift else 0
        c = col.lash if weight > 0 else col.lash_soft
        cv.put(row + dy, x, c)
        if dy > 0:
            cv.put(row, x, col.skin)
    if weight >= 2:
        if cv.a - 1 >= 1:
            cv.put(row, -1, col.lash)
        cv.put(row + 1, 0, col.lash)
    if weight >= 3:
        cv.put(row + 1, 1, col.lash)


def _draw_line(cv, col, points, colour=None):
    for (row, x) in points:
        cv.put(row, x, colour or col.lash)


def _closed_points(spec, row, curve):
    W = spec["width"]
    pts = []
    for x in range(W):
        end = x in (0, W - 1)
        pts.append((row - 1 if (curve and end) else row, x))
    return pts


def _brow_points(spec, mood):
    """(row, x, soft) of the hero's right brow (mirrored for the left); x 0 = outer end, soft = a lighter tail texel.

    Rest brows sit on row 0 and leave row 1 as skin, so they never touch the lash on row 2 (a brow on row 1 above the
    lash reads as one heavy dark blob at game distance). Row 1 is used only where the frame moves the lash away from
    it (angry and sad shift lash texels down, focused, smug and sleeping lower the lid) or for the deliberately heavy
    shapes (straight_thick, bold, worried). Every frame of a blink (open, half, closed) uses the same rest brows, so
    the brows never jump while the hero blinks.
    """
    W = spec["width"]
    shape = spec["brows"]
    if shape == "none":
        return []
    xs = list(range(0, W - 1))
    n = len(xs)
    half = n // 2
    if mood == "angry":                       # inner end down toward the nose (the lash's inner half drops a row)
        pts = [(0 if k < half else 1, x, False) for k, x in enumerate(xs)] + [(2, xs[-1], False)]
    elif mood in ("sad", "pain"):             # inner end up, outer end down (the lash's outer end drops a row)
        pts = [(1 if k < half else 0, x, False) for k, x in enumerate(xs)]
    elif mood == "surprised":                 # high and full
        pts = [(0, x, False) for x in xs]
    elif mood in ("focused", "lowered"):      # the lid is lowered, so row 1 is free above it
        pts = [(1, x, False) for x in xs]
        if mood == "focused":
            pts.append((2, xs[-1], False))
    elif mood == "sleeping":                  # relaxed and low, the lash line sits at the bottom of the eye
        pts = [(1, x, x == xs[0]) for x in xs]
    elif shape == "thin_arched" or mood == "happy":
        pts = [(0, x, k == 0) for k, x in enumerate(xs)]          # tapered outer tail
    elif shape == "worried":
        pts = [(1, x, True) for x in xs[:max(1, half - 1)]] + [(0, x, False) for x in xs[max(1, half - 1):]]
    else:
        pts = [(0, x, False) for x in xs]
    if shape in ("straight_thick", "bold") and mood not in ("sleeping", "surprised", "angry", "sad", "pain"):
        rows = {(r, x) for r, x, _ in pts}
        for (r, x, _) in list(pts):
            if r == 0 and (r + 1, x) not in rows and (shape == "straight_thick" or _inner(x, W)):
                pts.append((1, x, shape == "straight_thick" and x == xs[0]))
    return pts


def _inner(x, W):
    """True for the inner half of a brow (x 0 is the outer end)."""
    return x >= (W - 1) // 2


def _draw_brows(cv, spec, col, mood, only=None):
    for (row, x, soft) in _brow_points(spec, mood):
        cv.put(row, x, col.brow_soft if soft else col.brow, only=only)


def eye_frame(spec, col, frame):
    """Paint eye frame `frame` (0-11). Returns (rgba, glow) 8x16 arrays."""
    cv = EyeCanvas(spec, col)
    H = spec["height"]
    name = EYE_FRAMES[frame]
    if name == "open":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col)
    elif name == "half":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col, lid=H // 2, iris_rel_lid=False, highlight=False, pupil=False)
    elif name == "closed":
        _draw_brows(cv, spec, col, "neutral")
        _draw_line(cv, col, _closed_points(spec, 3 + int(np.floor((H - 1) * 2 / 3.0 + 0.5)),
                                           spec["closed"] == "curve"))
    elif name == "happy":
        # a rounded arch: interior on row 4, the ends a row lower; heavier lashes extend the outer end
        _draw_brows(cv, spec, col, "neutral")
        W = spec["width"]
        for x in range(W):
            cv.put(5 if x in (0, W - 1) else 4, x, col.lash)
        if spec["lashes"] >= 2:
            cv.put(6, 0, col.lash)
    elif name == "angry":
        _draw_brows(cv, spec, col, "angry")
        W = spec["width"]
        _draw_open(cv, spec, col, shrink=1, lash_shift=lambda x: 1 if x >= W // 2 else 0)
    elif name == "sad":
        _draw_brows(cv, spec, col, "sad")
        _draw_open(cv, spec, col, lash_shift=lambda x: 1 if x <= 1 else 0)
        cv.put(3 + H - 1, _iris_cols(spec, 0)[0], col.tear)
    elif name == "surprised":
        _draw_brows(cv, spec, col, "surprised")
        _draw_open(cv, spec, col, shrink=1, white_top=True)
    elif name == "pain":
        _draw_brows(cv, spec, col, "pain")
        W = spec["width"]
        hh = 5 if H >= 5 else H
        xs = (W - 3) // 2
        for r in range(hh):
            cv.put(3 + r, xs + min(r, hh - 1 - r), col.lash)
    elif name == "focused":
        _draw_brows(cv, spec, col, "focused")
        _draw_open(cv, spec, col, lid=H - int(np.floor(0.6 * H + 0.5)))
    elif name == "smug":
        _draw_brows(cv, spec, col, "lowered", only="r")
        _draw_brows(cv, spec, col, "surprised", only="l")
        _draw_open(cv, spec, col, lid=H // 2)
    elif name == "glowing":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col, glow=True)
    elif name == "sleeping":
        _draw_brows(cv, spec, col, "sleeping")
        _draw_line(cv, col, _closed_points(spec, 3 + H - 1, True))
    return cv.img, cv.glow


# ------------------------------------------------------------------ mouth and cheeks

def mouth_frame(spec, col, frame):
    img = np.zeros((4, 8, 4), dtype=np.uint8)
    img[:, :] = col.skin + (255,)

    def put(r, c, rgb):
        if 0 <= r < 4 and 0 <= c < 8:
            img[r, c] = tuple(rgb) + (255,)

    t = spec["mouth"]
    w = MOUTHS[t]
    c0, c1 = 4 - w // 2, 3 + w // 2
    line = col.mouth_soft if t == "soft" else col.mouth_line
    name = MOUTH_FRAMES[frame]
    if name == "neutral":
        for c in range(c0, c1 + 1):
            put(1, c, line)
    elif name == "smile":
        if t == "cat":
            for c in (2, 5):
                put(1, c, line)
            for c in (3, 4):
                put(2, c, line)
        else:
            for c in range(c0, c1 + 1):
                put(1, c, line)
            put(0, c0 - 1, line)
            if t != "stern":
                put(0, c1 + 1, line)
    elif name == "talk_a":
        for c in (3, 4):
            put(1, c, col.mouth_dark)
            put(2, c, col.tongue)
    elif name == "talk_b":
        put(1, 2, col.mouth_dark)
        put(1, 5, col.mouth_dark)
        put(1, 3, col.teeth)
        put(1, 4, col.teeth)
        for c in range(2, 6):
            put(2, c, col.tongue if c in (3, 4) else col.mouth_dark)
    elif name == "grit":
        for r in (1, 2):
            put(r, 1, col.mouth_dark)
            put(r, 6, col.mouth_dark)
            for c in range(2, 6):
                put(r, c, col.teeth if r == 1 else col.teeth_shade)
    elif name == "frown":
        for c in range(c0, c1 + 1):
            put(1, c, line)
        put(2, c0 - 1, line)
        put(2, c1 + 1, line)
    elif name == "cat":
        for c in (1, 3, 4, 6):
            put(1, c, line)
        for c in (2, 5):
            put(2, c, line)
    elif name == "fang_grin":
        for c in range(2, 6):
            put(1, c, line)
        put(0, 1, line)
        put(0, 6, line)
        fang = spec["fang"]
        if fang in ("right", "both"):
            put(2, 2, col.teeth)
        if fang in ("left", "both"):
            put(2, 5, col.teeth)
        if fang == "none":
            put(2, 3, col.teeth)
            put(2, 4, col.teeth)
    return img


def cheek_frame(spec, col, frame):
    img = np.zeros((2, 12, 4), dtype=np.uint8)

    def put(r, c, rgb):
        img[r, c] = tuple(rgb) + (255,)

    name = CHEEK_FRAMES[frame]
    if name == "blush":
        for base in (0, 8):
            for c in range(base, base + 4):
                put(0, c, col.blush_light)
            for c in (base + 1, base + 2):
                put(1, c, col.blush)
    elif name == "sweat":
        put(0, 11, col.tear_light)
        put(1, 11, col.tear)
        put(1, 10, col.tear)
    elif name == "tears":
        for c in (2, 9):
            put(0, c, col.tear_light)
            put(1, c, col.tear)
        put(1, 1, col.tear)
        put(1, 10, col.tear)
    return img


def head_front_holes():
    """16x16 mask of the transparent texels of the head front. Exactly under the eyes plane (rows 2-9, full width); under
    the mouth plane (rows 12-15) without the plane's two edge columns, which are plain skin in every mouth frame: the
    head front keeps that skin behind them, so the 0.05 px gap between plane and head shows skin, never the inside of
    the head, when the face is seen at an angle (the two coincident texels are the same colour, so no flicker)."""
    holes = np.zeros((16, 16), dtype=bool)
    holes[2:10, :] = True
    holes[12:16, 5:11] = True
    return holes


def head_front(spec, col):
    """16x16 head front: skin, darker jaw corners, transparent under the eyes and mouth planes."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    img[:, :] = col.skin + (255,)
    for r, c in ((15, 0), (15, 1), (15, 14), (15, 15), (14, 0), (14, 15)):
        img[r, c] = col.skin_dark + (255,)
    img[head_front_holes()] = 0
    return img


# ------------------------------------------------------------------ whole face

def paint_face(img, glow, rig, spec, ramps, palette_hex):
    """Write every frame and the head front into the texture at the fixed template regions."""
    col = FaceColours(ramps, spec, palette_hex)
    tpl = rig["uv_template"]
    eu, ev = tpl["eyes"][:2]
    for k in range(12):
        f, g = eye_frame(spec, col, k)
        u, v = eu + (k % 4) * 16, ev + (k // 4) * 8
        img[v:v + 8, u:u + 16] = f
        glow[v:v + 8, u:u + 16] = g
    mu, mv = tpl["mouth"][:2]
    for k in range(8):
        img[mv:mv + 4, mu + k * 8:mu + k * 8 + 8] = mouth_frame(spec, col, k)
    cu, cv_ = tpl["cheeks"][:2]
    for k in range(4):
        img[cv_:cv_ + 2, cu + k * 12:cu + k * 12 + 12] = cheek_frame(spec, col, k)
    hu, hv = tpl["head_front"][:2]
    img[hv:hv + 16, hu:hu + 16] = head_front(spec, col)


def frame_rect(rig, plane, k):
    """(u, v, w, h) of frame k of a face plane."""
    tpl = rig["uv_template"]
    p = rig["face"]["planes"][plane]
    fw, fh = p["frame"]
    cols = p["grid"][0]
    u, v = tpl[plane][:2]
    return (u + (k % cols) * fw, v + (k // cols) * fh, fw, fh)


def compose_face(tex, rig, eyes, mouth, cheeks):
    """The 16x16 face as the game shows it for a frame combination (planes over the head front)."""
    hu, hv = rig["uv_template"]["head_front"][:2]
    out = tex[hv:hv + 16, hu:hu + 16].copy()
    for plane, k, (r0, c0) in (("eyes", eyes, (2, 0)), ("mouth", mouth, (12, 4)), ("cheeks", cheeks, (10, 2))):
        u, v, w, h = frame_rect(rig, plane, k)
        src = tex[v:v + h, u:u + w]
        mask = src[:, :, 3] > 25
        region = out[r0:r0 + h, c0:c0 + w]
        region[mask] = src[mask]
    return out
