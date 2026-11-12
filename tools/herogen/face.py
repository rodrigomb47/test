"""Faces (design 4.6 and 13.5): eye, mouth and cheek frames at double resolution, and the head front (rig v2).

The head is a 10 px cube whose front is drawn at double density: a 20x20 texel face. The layout comes from the rig's
`face` block (head-local, y 0 at the chin):
  face rows 0-3   forehead (bangs cover it)
  face rows 4-13  the eyes plane, 20x10 texels: rows 0-1 brows, row 2 skin, row 3 the upper lash, rows 4-9 the eye
                  body (six rows: anime eyes are tall)
  face rows 14-15 the cheeks plane, 16x2 (cut-out: blush, sweat, tears), under the eyes
  face rows 16-19 the mouth plane, 8x4 (columns 6-13 of the face)
Rest brows sit on row 1 and leave row 2 as skin, so a brow never merges with the lash; expressions move them up to row
0 or down to row 2. The two eyes mirror about the plane's centre line, keep their body inside columns 1-18 (an outer
lash flick may reach column 0 or 19) and have 2, 4 or 6 texels between their inner corners (mirroring makes the gap
even). In the frames the viewer's left eye is the hero's right eye (column 0 is the hero's right, -x).

Eyes are drawn the way the style guide asks (docs/heroes-v2/notes/artgen.md, "Style guide"): a heavy upper lash with
an outer flick, an iris that runs dark under the lash to light at the bottom, a 2x2 pupil, a 2x2 white highlight in
the upper outer iris and a small second highlight low on the inner side, little white, a shadowed white under the lash.

The head front (20x20) is skin with transparent holes exactly under the opaque eyes plane (face rows 4-13) and under
the mouth plane's inner columns (rows 16-19, columns 7-12; see head_front_holes); the cheeks plane in front of rows
14-15 is cut-out.
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

# Frame and plane geometry (rig v2 face block; checked against the rig by tests and validate).
EW, EH = 20, 10                # eye frame
LASH = 3                       # upper lash row of the eye frame
BODY = 4                       # first eye body row
BODY_MAX = 6                   # eye body rows 4-9
MW, MH = 8, 4                  # mouth frame
CW, CH = 16, 2                 # cheek frame
FACE = 20                      # head front texels
EYES_AT = (4, 0)               # (row, column) of each plane on the head front
CHEEKS_AT = (14, 2)
MOUTH_AT = (16, 6)

# Eye templates: body width and height, and per body row the (outer, inner) inset of the opening.
EYE_TEMPLATES = {
    "round": {"w": 6, "h": 6, "rows": [(0, 0), (0, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 4},
    "almond": {"w": 6, "h": 5, "rows": [(1, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 4},
    "sharp": {"w": 7, "h": 5, "rows": [(0, 1), (0, 0), (0, 0), (1, 0), (2, 1)], "iris": 4},
    "droopy": {"w": 6, "h": 5, "rows": [(1, 0), (0, 0), (0, 0), (0, 0), (0, 1)], "iris": 4},
    "cat": {"w": 7, "h": 6, "rows": [(0, 1), (0, 0), (0, 0), (0, 0), (1, 0), (2, 1)], "iris": 4},
    "sleepy": {"w": 6, "h": 5, "rows": [(0, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 4, "lid": 1},
    "wide": {"w": 7, "h": 6, "rows": [(0, 0), (0, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 5},
    "narrow": {"w": 6, "h": 4, "rows": [(1, 1), (0, 0), (0, 0), (1, 1)], "iris": 3},
    "heavy_lid": {"w": 6, "h": 6, "rows": [(0, 0), (0, 0), (0, 0), (0, 0), (0, 0), (1, 1)], "iris": 4, "lid": 1},
}
BROWS = ("thin_arched", "straight", "straight_thick", "worried", "bold", "none")
MOUTHS = {"small": 2, "soft": 2, "wide": 4, "cat": 2, "stern": 4}
PUPILS = ("round", "slit", "star", "ring", "none")
FANGS = ("none", "left", "right", "both")

FAMILY_DEFAULTS = {
    "female": {"eyes": "round", "lashes": 2, "brows": "thin_arched", "mouth": "small", "closed": "curve"},
    "male": {"eyes": "almond", "lashes": 1, "brows": "straight", "mouth": "small", "closed": "straight"},
}


def check_layout(rig):
    """The constants above must match the rig's face block (one source of truth for the file, one for the code)."""
    face = rig["face"]
    planes = face["planes"]
    problems = []
    for name, want in (("eyes", (EW, EH)), ("mouth", (MW, MH)), ("cheeks", (CW, CH))):
        if tuple(planes[name]["frame"]) != want:
            problems.append("rig face plane %s frame is %s, face.py draws %s" % (name, planes[name]["frame"], want))
    size = rig["head"]["size"]
    d = face["density"]
    if size * d != FACE:
        problems.append("rig head front is %d texels, face.py draws %d" % (size * d, FACE))
    for name, (row, col) in (("eyes", EYES_AT), ("cheeks", CHEEKS_AT), ("mouth", MOUTH_AT)):
        p = planes[name]
        at = (int(round((size - p["origin"][1] - p["size"][1]) * d)), int(round((p["origin"][0] + size / 2.0) * d)))
        if at != (row, col):
            problems.append("rig face plane %s sits at face row/column %s, face.py expects %s" % (name, at, (row, col)))
    rows = face["eye_rows"]
    if rows["lash"] != LASH or rows["body"] != [BODY, BODY + BODY_MAX - 1]:
        problems.append("rig eye rows %s differ from face.py" % rows)
    return problems


# ------------------------------------------------------------------ spec

def face_spec(art_face, family, palette_hex):
    """Resolve art.face with family defaults into the parameters the painter uses."""
    f = dict(FAMILY_DEFAULTS[family])
    f.update({"pupil": "round", "iris": palette_hex["eyes"], "highlights": 2, "spacing": 4, "fang": "none",
              "blush": False, "lower_lash": family == "female", "overrides": {}})
    f.update(art_face or {})
    if f["eyes"] not in EYE_TEMPLATES:
        raise HerogenError("art.face.eyes %r is unknown (%s)" % (f["eyes"], ", ".join(EYE_TEMPLATES)))
    t = EYE_TEMPLATES[f["eyes"]]
    f.setdefault("width", t["w"])
    f.setdefault("height", t["h"])
    f.setdefault("iris_width", min(t["iris"], f["width"] - 2) if isinstance(f["width"], int) else t["iris"])
    checks = [("width", 5, 7), ("height", 4, 6), ("iris_width", 3, 5), ("lashes", 0, 3), ("highlights", 1, 2)]
    for key, lo, hi in checks:
        if not isinstance(f[key], int) or isinstance(f[key], bool) or not lo <= f[key] <= hi:
            raise HerogenError("art.face.%s must be a whole number %d-%d" % (key, lo, hi))
    if f["iris_width"] > f["width"] - 2:
        raise HerogenError("art.face.iris_width %d leaves no white in a %d-wide eye" % (f["iris_width"], f["width"]))
    if f["spacing"] not in (2, 4, 6):
        raise HerogenError("art.face.spacing must be 2, 4 or 6 (the eyes mirror, so the gap is even)")
    inner = EW // 2 - 1 - f["spacing"] // 2
    if inner - f["width"] + 1 < 1:
        raise HerogenError("art.face: width %d with spacing %d reaches column 0 (the eyes stay inside columns 1-%d)"
                           % (f["width"], f["spacing"], EW - 2))
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
        self.sclera_shade = mix(ramps["sclera"][1], self.lash, 0.12)
        self.white = (255, 255, 255)
        self.iris = ramp(spec["iris"])
        self.glow = ramps["glow"]
        self.pupil = mix(self.iris[0], self.lash, 0.55)
        self.shine = mix(self.white, self.iris[4], 0.45)
        self.mouth_line = mix(skin[0], ramps["mouth_dark"][2], 0.5)
        self.mouth_soft = skin[0]
        self.mouth_dark = ramps["mouth_dark"][2]
        self.tongue = ramps["tongue"][2]
        self.teeth = ramps["teeth"][2]
        self.teeth_shade = ramps["teeth"][1]
        self.blush_light = mix(self.skin, ramps["blush"][2], 0.42)
        self.blush = mix(self.skin, ramps["blush"][2], 0.62)
        self.blush_line = mix(self.skin, ramps["blush"][1], 0.85)
        self.tear = ramps["tear"][2]
        self.tear_light = ramps["tear"][4]


# ------------------------------------------------------------------ eyes

class EyeCanvas:
    """One eye frame (EW x EH). Drawing uses (row, x) where x is the eye-local column (0 = outer corner) of the hero's
    right eye; `put` draws it and its mirror for the left eye unless `only` names one side ('r' or 'l')."""

    def __init__(self, spec, col):
        self.img = np.zeros((EH, EW, 4), dtype=np.uint8)
        self.img[:, :] = col.skin + (255,)
        self.glow = np.zeros((EH, EW, 4), dtype=np.uint8)
        self.W, self.H = spec["width"], spec["height"]
        self.b = EW // 2 - 1 - spec["spacing"] // 2       # inner corner column of the hero's right eye
        self.a = self.b - self.W + 1

    def cols(self, x):
        c = self.a + x
        return c, EW - 1 - c

    def put(self, row, x, rgb, only=None, glow=False):
        if not 0 <= row < EH:
            return
        cr, cl = self.cols(x)
        for side, c in (("r", cr), ("l", cl)):
            if only and side != only:
                continue
            if 0 <= c < EW:
                self.img[row, c] = tuple(rgb) + (255,)
                if glow:
                    self.glow[row, c] = tuple(rgb) + (255,)

    def get(self, row, x):
        return tuple(self.img[row, self.cols(x)[0], :3])


def _span(spec, r):
    o, i = spec["rows"][r]
    return o, spec["width"] - 1 - i


def _iris_cols(spec, shrink):
    I = max(1, spec["iris_width"] - shrink)
    start = int(np.floor((spec["width"] - I) / 2.0 + 0.5))
    return list(range(start, start + I))


def _iris_tone(rr, n):
    """Iris ramp index for visible row rr of n: dark under the lash, light at the bottom (the anime gradient)."""
    if rr == 0:
        return 0
    if rr == n - 1:
        return 4
    if rr == n - 2 and n >= 4:
        return 3
    return 1 if rr == 1 else 2


def _draw_open(cv, spec, col, lid=0, shrink=0, iris_rel_lid=True, highlight=True, pupil=True, glow=False,
               white_top=False, lash_shift=None, small_pupil=False):
    """An open eye with the upper lid at body row `lid`. iris_rel_lid: iris tones count from the lid (a narrowed eye
    shows the whole iris); otherwise from the top (a half-closed eye hides the iris top)."""
    H = spec["height"]
    iris = col.glow if glow else col.iris
    icols = _iris_cols(spec, shrink)
    lid = max(lid, spec["lid"])
    n = H - lid if iris_rel_lid else H
    for r in range(lid, H):
        row = BODY + r
        o, i = _span(spec, r)
        rr = r - lid if iris_rel_lid else r
        for x in range(o, i + 1):
            # the white is shadowed under the lash (top half), so the white highlight stands out against it
            cv.put(row, x, col.sclera_shade if r - lid < max(1, (H - lid) // 2) else col.sclera)
            if x in icols and not (white_top and r == lid):
                cv.put(row, x, iris[_iris_tone(rr, n)], glow=glow)
    vis = list(range(lid, H))
    mid = icols[len(icols) // 2]
    if pupil and len(vis) >= 3:
        top = vis[min(len(vis) - 2, 1 if len(vis) <= 4 else 2)]
        ptype = spec["pupil"]
        dark = col.white if glow else col.pupil
        pcols = [mid] if small_pupil or len(icols) < 4 else [icols[(len(icols) - 1) // 2], mid]
        prows = [top] if small_pupil else [top, top + 1]
        if ptype == "round":
            for r in prows:
                for x in pcols:
                    cv.put(BODY + r, x, dark, glow=glow)
        elif ptype == "slit":
            for r in vis[1:-1]:
                cv.put(BODY + r, mid, dark, glow=glow)
        elif ptype == "star":
            cv.put(BODY + top, mid, col.white, glow=glow)
            cv.put(BODY + top + 1, mid, iris[4], glow=glow)
            for x in pcols:
                if x != mid:
                    cv.put(BODY + top, x, iris[4], glow=glow)
        elif ptype == "ring":
            for r in prows:
                for x in pcols:
                    cv.put(BODY + r, x, dark, glow=glow)
            for x in icols:
                if x not in pcols:
                    cv.put(BODY + prows[0], x, iris[4], glow=glow)
    _draw_lash(cv, spec, col, lid, lash_shift)
    if spec["lower_lash"]:
        o, _ = _span(spec, H - 1)
        cv.put(BODY + H - 1, max(0, o - 1), col.lash_soft)
        if o == 0:
            cv.put(BODY + H - 1, 0, col.lash_soft)
    if highlight and vis:
        # the main highlight goes on the topmost iris (or pupil) texels the lid and lashes left visible, outer side
        iris_px = {tuple(c) for c in iris} | {tuple(col.pupil)}
        spots = [(BODY + r, x) for r in vis[1:max(3, len(vis) // 2 + 1)] for x in icols if cv.get(BODY + r, x) in iris_px]
        if not spots:
            spots = [(BODY + r, x) for r in vis for x in icols if cv.get(BODY + r, x) in iris_px]
        if spots:
            hrow, hx = spots[0]
            cv.put(hrow, hx, col.white, glow=glow)
            big = len(icols) >= 4 and len(vis) >= 4
            if big and hx + 1 in icols:
                cv.put(hrow, hx + 1, col.white, glow=glow)
            if big and hrow + 1 < BODY + H - 1:
                cv.put(hrow + 1, hx, col.white, glow=glow)
        if spec["highlights"] >= 2 and len(vis) >= 3:
            cv.put(BODY + vis[-1] - (1 if len(vis) >= 5 else 0), icols[-1], col.shine, glow=glow)


def _draw_lash(cv, spec, col, lid, shift=None):
    """Upper lash above body row `lid`; shift(x) moves single texels down (angry, sad). Weight 0 is a soft line over
    the opening, 1 a dark line over the whole eye, 2 adds the outer corner (one texel out and one down), 3 an upward
    outer flick."""
    W = spec["width"]
    weight = spec["lashes"]
    row = LASH + lid
    o, i = _span(spec, min(lid, spec["height"] - 1))
    xs = range(o, i + 1) if weight == 0 else range(0, W)
    for x in xs:
        dy = shift(x) if shift else 0
        c = col.lash if weight > 0 else col.lash_soft
        cv.put(row + dy, x, c)
        if dy > 0:
            cv.put(row, x, col.skin)
    if weight >= 2:
        if cv.a - 1 >= 0:
            cv.put(row, -1, col.lash)
        cv.put(row + 1, 0, col.lash)
    if weight >= 3:
        if cv.a - 1 >= 0:
            cv.put(row - 1, -1, col.lash)
        cv.put(row + 2, 0, col.lash_soft)


def _draw_line(cv, col, points, colour=None):
    for (row, x) in points:
        cv.put(row, x, colour or col.lash)


def _closed_points(spec, row, curve):
    """A closed lid: a line across the eye, its ends one row higher when curved (a relaxed lid bows downward);
    heavier lashes carry the outer end one texel further out."""
    W = spec["width"]
    pts = []
    for x in range(W):
        end = x in (0, W - 1)
        pts.append((row - 1 if (curve and end) else row, x))
    if spec["lashes"] >= 2:
        pts.append((row - 1 if curve else row, -1))
    return pts


def _brow_points(spec, mood):
    """(row, x, soft) of the hero's right brow (mirrored for the left); x 0 = outer end, soft = a lighter tail texel.

    Rest brows sit on row 1 and leave row 2 as skin, so they never touch the lash on row 3. Expressions move them:
    raised to row 0 (surprised), the inner end down to row 2 (angry, focused), the outer end down (sad, pain). Every
    frame of a blink (open, half, closed) uses the same rest brows, so the brows never jump while the hero blinks.
    """
    W = spec["width"]
    shape = spec["brows"]
    if shape == "none":
        return []
    xs = list(range(0, W - 1))
    n = len(xs)
    half = n // 2
    if mood == "angry":                       # outer end high, inner end down toward the nose
        pts = [(0 if k < half - 1 else (1 if k < n - 1 else 2), x, False) for k, x in enumerate(xs)]
    elif mood in ("sad", "pain"):             # inner end up, outer end down
        pts = [(2 if k == 0 else (1 if k < half else 0), x, False) for k, x in enumerate(xs)]
    elif mood == "surprised":                 # high and full
        pts = [(0, x, False) for x in xs]
    elif mood in ("focused", "lowered"):      # the lid is lowered, so row 2 is free above it
        pts = [(2, x, k == 0) for k, x in enumerate(xs)]
        if mood == "focused":
            pts[-1] = (2, xs[-1], False)
            pts.append((3, xs[-1], False))
    elif mood == "sleeping":                  # relaxed: rest brows with a soft outer end
        pts = [(1, x, k == 0) for k, x in enumerate(xs)]
    elif shape == "thin_arched" or mood == "happy":
        pts = [(1 if k in (0, n - 1) else 0, x, k == 0) for k, x in enumerate(xs)]   # arched, tapered outer tail
    elif shape == "worried":
        pts = [(1, x, k == 0) for k, x in enumerate(xs[:half])] + [(0, x, False) for x in xs[half:]]
    else:
        pts = [(1, x, False) for x in xs]
    if shape in ("straight_thick", "bold") and mood not in ("sleeping", "surprised", "angry", "sad", "pain",
                                                            "focused", "lowered"):
        rows = {(r, x) for r, x, _ in pts}
        for (r, x, _) in list(pts):
            if r == 1 and (0, x) not in rows and (shape == "straight_thick" or _inner(x, W)):
                pts.append((0, x, False))
    return pts


def _inner(x, W):
    """True for the inner half of a brow (x 0 is the outer end)."""
    return x >= (W - 1) // 2


def _draw_brows(cv, spec, col, mood, only=None):
    for (row, x, soft) in _brow_points(spec, mood):
        cv.put(row, x, col.brow_soft if soft else col.brow, only=only)


def eye_frame(spec, col, frame):
    """Paint eye frame `frame` (0-11). Returns (rgba, glow) EH x EW arrays."""
    cv = EyeCanvas(spec, col)
    H, W = spec["height"], spec["width"]
    name = EYE_FRAMES[frame]
    if name == "open":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col)
    elif name == "half":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col, lid=H // 2, iris_rel_lid=False, highlight=False, pupil=False)
    elif name == "closed":
        _draw_brows(cv, spec, col, "neutral")
        _draw_line(cv, col, _closed_points(spec, BODY + int(np.floor((H - 1) * 0.5 + 0.5)), spec["closed"] == "curve"))
    elif name == "happy":
        # a rounded arch (^): the middle two rows up, the ends down; heavier lashes extend the outer end
        _draw_brows(cv, spec, col, "neutral")
        top = BODY + 1
        for x in range(W):
            d = min(x, W - 1 - x)
            cv.put(top + (2 if d == 0 else (1 if d == 1 else 0)), x, col.lash)
        if spec["lashes"] >= 2:
            cv.put(top + 3, 0, col.lash)
    elif name == "angry":
        _draw_brows(cv, spec, col, "angry")
        _draw_open(cv, spec, col, shrink=1, lash_shift=lambda x: 1 if x >= W // 2 else 0)
    elif name == "sad":
        _draw_brows(cv, spec, col, "sad")
        _draw_open(cv, spec, col, lash_shift=lambda x: 1 if x <= 1 else 0)
        cv.put(BODY + H - 1, _iris_cols(spec, 0)[0], col.tear)
    elif name == "surprised":
        _draw_brows(cv, spec, col, "surprised")
        _draw_open(cv, spec, col, shrink=1, white_top=True, small_pupil=True)
    elif name == "pain":
        _draw_brows(cv, spec, col, "pain")
        rows = min(5, H)
        reach = (rows - 1) // 2
        x0 = max(0, (W - reach - 2) // 2)
        for r in range(rows):
            x = x0 + min(r, rows - 1 - r)
            cv.put(BODY + r, x, col.lash)
            cv.put(BODY + r, x + 1, col.lash)
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
        _draw_line(cv, col, _closed_points(spec, BODY + H - 2, True))
    return cv.img, cv.glow


# ------------------------------------------------------------------ mouth and cheeks

def mouth_frame(spec, col, frame):
    img = np.zeros((MH, MW, 4), dtype=np.uint8)
    img[:, :] = col.skin + (255,)

    def put(r, c, rgb):
        if 0 <= r < MH and 0 <= c < MW:
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
        put(3, 3, col.mouth_dark)
        put(3, 4, col.mouth_dark)
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
    img = np.zeros((CH, CW, 4), dtype=np.uint8)

    def put(r, c, rgb):
        img[r, c] = tuple(rgb) + (255,)

    name = CHEEK_FRAMES[frame]
    if name == "blush":
        # two soft ovals under the outer halves of the eyes, with the anime hatch lines
        for base in (0, CW - 5):
            for c in range(base, base + 5):
                put(0, c, col.blush_light)
            for c in range(base + 1, base + 4):
                put(1, c, col.blush)
            for c in (base + 1, base + 3):
                put(0, c, col.blush_line)
    elif name == "sweat":
        put(0, CW - 1, col.tear_light)
        put(1, CW - 1, col.tear)
        put(1, CW - 2, col.tear)
    elif name == "tears":
        for c in (2, CW - 3):
            put(0, c, col.tear_light)
            put(1, c, col.tear)
        put(1, 1, col.tear)
        put(1, CW - 2, col.tear)
    return img


def head_front_holes():
    """FACE x FACE mask of the transparent texels of the head front. Exactly under the eyes plane (face rows 4-13,
    full width); under the mouth plane (rows 16-19) without the plane's two edge columns, which are plain skin in every
    mouth frame: the head front keeps that skin behind them, so the 0.05 px gap between plane and head shows skin,
    never the inside of the head, when the face is seen at an angle (the two coincident texels are the same colour, so
    no flicker)."""
    holes = np.zeros((FACE, FACE), dtype=bool)
    holes[EYES_AT[0]:EYES_AT[0] + EH, :] = True
    holes[MOUTH_AT[0]:MOUTH_AT[0] + MH, MOUTH_AT[1] + 1:MOUTH_AT[1] + MW - 1] = True
    return holes


def head_front(spec, col):
    """FACE x FACE head front: skin, darker jaw corners, transparent under the eyes and mouth planes."""
    img = np.zeros((FACE, FACE, 4), dtype=np.uint8)
    img[:, :] = col.skin + (255,)
    last = FACE - 1
    for r, c in ((last, 0), (last, 1), (last, last - 1), (last, last), (last - 1, 0), (last - 1, last)):
        img[r, c] = col.skin_dark + (255,)
    img[head_front_holes()] = 0
    return img


# ------------------------------------------------------------------ whole face

def paint_face(img, glow, rig, spec, ramps, palette_hex):
    """Write every frame and the head front into the texture at the fixed template regions."""
    col = FaceColours(ramps, spec, palette_hex)
    for k in range(12):
        f, g = eye_frame(spec, col, k)
        u, v, w, h = frame_rect(rig, "eyes", k)
        img[v:v + h, u:u + w] = f
        glow[v:v + h, u:u + w] = g
    for k in range(8):
        u, v, w, h = frame_rect(rig, "mouth", k)
        img[v:v + h, u:u + w] = mouth_frame(spec, col, k)
    for k in range(4):
        u, v, w, h = frame_rect(rig, "cheeks", k)
        img[v:v + h, u:u + w] = cheek_frame(spec, col, k)
    hu, hv = rig["uv_template"]["head_front"][:2]
    img[hv:hv + FACE, hu:hu + FACE] = head_front(spec, col)


def frame_rect(rig, plane, k):
    """(u, v, w, h) of frame k of a face plane."""
    tpl = rig["uv_template"]
    p = rig["face"]["planes"][plane]
    fw, fh = p["frame"]
    cols = p["grid"][0]
    u, v = tpl[plane][:2]
    return (u + (k % cols) * fw, v + (k // cols) * fh, fw, fh)


def compose_face(tex, rig, eyes, mouth, cheeks):
    """The FACE x FACE face as the game shows it for a frame combination (planes over the head front)."""
    hu, hv = rig["uv_template"]["head_front"][:2]
    out = tex[hv:hv + FACE, hu:hu + FACE].copy()
    for plane, k, (r0, c0) in (("eyes", eyes, EYES_AT), ("mouth", mouth, MOUTH_AT), ("cheeks", cheeks, CHEEKS_AT)):
        u, v, w, h = frame_rect(rig, plane, k)
        src = tex[v:v + h, u:u + w]
        mask = src[:, :, 3] > 25
        region = out[r0:r0 + h, c0:c0 + w]
        region[mask] = src[mask]
    return out
