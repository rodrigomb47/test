"""Faces (design 4.6 and 13.5): eye, mouth and cheek frames at double resolution, and the head front (rig v3).

The head is a 10 px cube whose front is drawn at double density: a 20x20 texel face. Rig v3 puts the eyes low and
wide, the way the owner's references and the other creators' models do (docs/heroes-v2/notes/reference-study.md and
reference-survey.md):
  face rows 0-6    forehead (the bangs cover it, and usually the brows too)
  face rows 7-16   the eyes plane, 20x10 texels: rows 0-1 brows, row 2 skin, row 3 the upper lash (or lower, for a
                   shape whose corner sits lower), rows 4-9 the eye body; the eye centre sits 0.30 of the head
                   height above the chin
  face rows 17-19  the mouth plane, 8x3 (columns 6-13 of the face), the mouth line on its middle row
  face rows 17-18  the cut-out cheeks plane, 16x2 (columns 2-17): blush, sweat, tears
In the frames the viewer's left eye is the hero's right eye (column 0 is the hero's right, -x). The two eyes mirror
about the plane's centre line unless the face says otherwise (heterochromia, an eye patch, a one-sided mark).

**Every hero gets its own face** (the "faces are similar" feedback): a face is a combination of
- an eye **shape** family (`EYE_SHAPES`): round, wide (big and sparkly), almond, sharp (tsurime, upturned), droopy
  (tareme), narrow, sleepy (jitome, half-lidded), heavy_lid, cat, small; each shape is a per-column opening, so a
  sloped lash line, a raised or lowered outer corner and a lidded look come from the shape itself;
- the **iris** style (gradient, two_tone, ring, solid, glossy), size and colour (a second colour for heterochromia),
  the **pupil** (round, dot, slit, none, star, ring) and the **highlights** (one, two, sparkle, inner, none);
- the **lash** weight 0-3, the outer **flick** (up, down, none) and an optional lower lash;
- the eye **spacing** (2-8 texels between the inner corners);
- the **brows** (thin_arched, straight, straight_thick, worried, bold, angled, short, none);
- the **mouth** style (small, soft, wide, cat, stern, fang, pout, smirk) and fangs;
- the **blush** style (soft, hatch, dots) and whether the hero blushes when happy;
- **marks** (freckles, beauty_mark, scar, face_paint, stubble, bandage) and **wear** (glasses round, square or half;
  an eye patch), painted into every frame they cross so blinking and the 12 moods keep working.
`signature` and `distinct` compare two faces feature by feature (the style fixtures are checked to differ in at
least five).

All frames are drawn over one **skin layer** (skin, jaw shading and the marks): the head front is that layer with
holes under the opaque eyes and mouth planes, the eye frames start from its rows 7-16 and the mouth frames from its
rows 17-19, so marks line up across planes and the mouth plane's edge columns always equal the head front behind
them (the 0.05 px gap between plane and head shows the same skin at an angle).
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

# Frame and plane geometry (rig v3 face block; checked against the rig by tests and validate).
EW, EH = 20, 10                # eye frame
LASH = 3                       # upper lash row of a shape whose opening starts on body row 0
BODY = 4                       # first eye body row
BODY_MAX = 6                   # eye body rows 4-9
MW, MH = 8, 3                  # mouth frame
CW, CH = 16, 2                 # cheek frame
FACE = 20                      # head front texels
EYES_AT = (7, 0)               # (row, column) of each plane on the head front
CHEEKS_AT = (17, 2)
MOUTH_AT = (17, 6)

# Eye shapes: the opening per column, outer corner first, as (top, bottom) body rows (0-5, frame rows 4-9). The lash
# follows the top edge, so the shape alone gives a round, upturned, drooping or lidded eye. "lid": rows of the
# opening a heavy upper lid covers at rest (the iris is drawn as if the eye were open and the lid hides its top).
EYE_SHAPES = {
    "round": {"cols": [(1, 4), (0, 5), (0, 5), (0, 5), (0, 5), (1, 4)], "iris": 4},
    "wide": {"cols": [(1, 4), (0, 5), (0, 5), (0, 5), (0, 5), (0, 5), (1, 5)], "iris": 5, "highlights": "sparkle"},
    "almond": {"cols": [(1, 3), (0, 4), (0, 5), (0, 5), (1, 5), (2, 5)], "iris": 4},
    "sharp": {"cols": [(0, 3), (0, 4), (0, 5), (1, 5), (1, 5), (2, 5), (2, 4)], "iris": 4},
    "droopy": {"cols": [(2, 5), (1, 5), (0, 5), (0, 5), (0, 5), (1, 4)], "iris": 4},
    "narrow": {"cols": [(3, 4), (2, 5), (2, 5), (2, 5), (2, 5), (2, 5), (3, 4)], "iris": 3},
    "sleepy": {"cols": [(1, 5), (0, 5), (0, 5), (0, 5), (0, 5), (1, 5)], "iris": 4, "lid": 2},
    "heavy_lid": {"cols": [(1, 4), (0, 5), (0, 5), (0, 5), (0, 5), (1, 4)], "iris": 4, "lid": 1},
    "cat": {"cols": [(0, 1), (0, 3), (0, 5), (0, 5), (1, 5), (2, 5), (3, 4)], "iris": 4, "pupil": "slit"},
    "small": {"cols": [(2, 4), (1, 5), (1, 5), (1, 5), (2, 4)], "iris": 3},
}
SHAPE_ALIASES = {"tsurime": "sharp", "tareme": "droopy", "jitome": "sleepy", "sparkly": "wide"}
EYE_TEMPLATES = EYE_SHAPES                      # rig v2 name, kept for callers
IRIS_STYLES = ("gradient", "two_tone", "ring", "solid", "glossy")
PUPILS = ("round", "dot", "slit", "none", "star", "ring")
HIGHLIGHTS = ("one", "two", "sparkle", "inner", "none")
FLICKS = ("up", "down", "none")
BROWS = ("thin_arched", "straight", "straight_thick", "worried", "bold", "angled", "short", "none")
MOUTHS = {"small": 2, "soft": 2, "wide": 4, "cat": 4, "stern": 4, "fang": 2, "pout": 2, "smirk": 3}
FANGS = ("none", "left", "right", "both")
BLUSH_STYLES = ("soft", "hatch", "dots")
MARKS = ("freckles", "beauty_mark", "scar", "face_paint", "stubble", "bandage")
GLASSES = ("round", "square", "half")
SPACINGS = (2, 4, 6, 8)

FAMILY_DEFAULTS = {
    "female": {"eyes": "round", "lashes": 2, "flick": "up", "lower_lash": True, "brows": "thin_arched",
               "mouth": "small", "closed": "curve"},
    "male": {"eyes": "almond", "lashes": 1, "flick": "none", "lower_lash": False, "brows": "straight",
             "mouth": "small", "closed": "straight"},
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

def _shape_cols(shape, width):
    """The shape's columns stretched or squeezed to `width` by repeating or dropping middle columns."""
    cols = list(EYE_SHAPES[shape]["cols"])
    while len(cols) < width:
        m = len(cols) // 2
        cols.insert(m, cols[m])
    while len(cols) > width:
        del cols[len(cols) // 2]
    return cols


def _marks(raw):
    out = []
    for i, m in enumerate(raw or []):
        if isinstance(m, str):
            m = {"type": m}
        m = dict(m)
        if m.get("type") not in MARKS:
            raise HerogenError("art.face.marks[%d]: type %r is unknown (%s)" % (i, m.get("type"), ", ".join(MARKS)))
        m.setdefault("side", "both" if m["type"] in ("freckles", "face_paint", "stubble") else "r")
        if m["side"] not in ("r", "l", "both"):
            raise HerogenError("art.face.marks[%d]: side must be r, l or both" % i)
        if m["type"] == "beauty_mark":
            m.setdefault("at", "eye")
            if m["at"] not in ("eye", "mouth"):
                raise HerogenError("art.face.marks[%d]: a beauty_mark is at eye or mouth" % i)
        if m["type"] == "scar":
            m.setdefault("at", "eye")
            if m["at"] not in ("eye", "cheek", "nose"):
                raise HerogenError("art.face.marks[%d]: a scar is at eye, cheek or nose" % i)
        if m["type"] == "face_paint":
            m.setdefault("style", "stripes")
            m.setdefault("color", "accent")
            if m["style"] not in ("stripes", "tear", "dots", "chevron"):
                raise HerogenError("art.face.marks[%d]: face_paint style is stripes, tear, dots or chevron" % i)
        out.append(m)
    return out


def face_spec(art_face, family, palette_hex):
    """Resolve art.face with family and shape defaults into the parameters the painter uses."""
    art_face = dict(art_face or {})
    f = dict(FAMILY_DEFAULTS[family])
    f.update({"iris": palette_hex["eyes"], "iris2": None, "iris_style": "gradient", "spacing": 6, "fang": "none",
              "blush": False, "blush_style": "hatch", "marks": [], "glasses": None, "glasses_color": "metal",
              "eyepatch": None, "overrides": {}})
    shape = art_face.get("eyes", f["eyes"])
    shape = SHAPE_ALIASES.get(shape, shape)
    if shape not in EYE_SHAPES:
        raise HerogenError("art.face.eyes %r is unknown (%s)" % (shape, ", ".join(sorted(EYE_SHAPES))))
    t = EYE_SHAPES[shape]
    f["pupil"] = t.get("pupil", "round")
    f["highlights"] = t.get("highlights", "two")
    f.update(art_face)
    f["eyes"] = shape
    if isinstance(f["blush"], str):                     # "blush": "dots" = blushes when happy, in that style
        if f["blush"] not in BLUSH_STYLES + ("none",):
            raise HerogenError("art.face.blush %r is unknown (%s, none)" % (f["blush"], ", ".join(BLUSH_STYLES)))
        if f["blush"] != "none":
            f["blush_style"] = f["blush"]
        f["blush"] = f["blush"] != "none"
    if f["blush_style"] not in BLUSH_STYLES:
        raise HerogenError("art.face.blush_style %r is unknown (%s)" % (f["blush_style"], ", ".join(BLUSH_STYLES)))
    hl = f["highlights"]
    if isinstance(hl, int) and not isinstance(hl, bool):
        if hl not in (1, 2):
            raise HerogenError("art.face.highlights must be 1, 2 or one of %s" % ", ".join(HIGHLIGHTS))
        f["highlights"] = "one" if hl == 1 else "two"
    f.setdefault("width", len(t["cols"]))
    if not isinstance(f["width"], int) or isinstance(f["width"], bool) or not 5 <= f["width"] <= 7:
        raise HerogenError("art.face.width must be a whole number 5-7")
    f.setdefault("iris_width", min(t["iris"], f["width"] - 2))
    for key, lo, hi in (("iris_width", 2, 5), ("lashes", 0, 3)):
        if not isinstance(f[key], int) or isinstance(f[key], bool) or not lo <= f[key] <= hi:
            raise HerogenError("art.face.%s must be a whole number %d-%d" % (key, lo, hi))
    if f["iris_width"] > f["width"] - 2:
        raise HerogenError("art.face.iris_width %d leaves no white in a %d-wide eye" % (f["iris_width"], f["width"]))
    if f["spacing"] not in SPACINGS:
        raise HerogenError("art.face.spacing must be 2, 4, 6 or 8 (the eyes mirror, so the gap is even)")
    if 2 * f["width"] + f["spacing"] > EW:
        raise HerogenError("art.face: two %d-wide eyes %d apart do not fit the %d-texel face"
                           % (f["width"], f["spacing"], EW))
    for key, allowed in (("brows", BROWS), ("mouth", MOUTHS), ("pupil", PUPILS), ("fang", FANGS),
                         ("closed", ("curve", "straight")), ("iris_style", IRIS_STYLES), ("flick", FLICKS),
                         ("highlights", HIGHLIGHTS)):
        if f[key] not in allowed:
            raise HerogenError("art.face.%s %r is unknown (%s)" % (key, f[key], ", ".join(allowed)))
    if f["glasses"] is not None and f["glasses"] not in GLASSES:
        raise HerogenError("art.face.glasses %r is unknown (%s)" % (f["glasses"], ", ".join(GLASSES)))
    if f["eyepatch"] not in (None, "r", "l"):
        raise HerogenError("art.face.eyepatch must be r or l (the hero's right or left eye)")
    if f["glasses"] and f["eyepatch"]:
        raise HerogenError("art.face: glasses and an eye patch together are not drawn")
    parse_hex(f["iris"])
    if f["iris2"] is not None:
        parse_hex(f["iris2"])
    f["marks"] = _marks(f["marks"])
    f["cols"] = _shape_cols(shape, f["width"])
    f["lid"] = t.get("lid", 0)
    f["asymmetric"] = bool(f["eyepatch"] or f["iris2"] or any(m["side"] != "both" for m in f["marks"]))
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


# ------------------------------------------------------------------ distinctness

SIGNATURE_KEYS = ("eyes", "iris_style", "pupil", "highlights", "lashes", "flick", "spacing", "brows", "mouth",
                  "blush_style", "marks", "wear")


def signature(spec):
    """The features that make a face recognisable, one value per SIGNATURE_KEYS entry."""
    marks = tuple(sorted("%s:%s" % (m["type"], m.get("style", m.get("at", ""))) for m in spec["marks"]))
    wear = spec["glasses"] or ("patch" if spec["eyepatch"] else None)
    vals = dict(spec, marks=marks, wear=wear)
    return tuple(vals[k] for k in SIGNATURE_KEYS)


def distinct(spec_a, spec_b):
    """How many face features differ (the iris hue counts as one more when the two hues are far apart)."""
    n = sum(1 for a, b in zip(signature(spec_a), signature(spec_b)) if a != b)
    from palette import rgb_to_oklch
    ha = rgb_to_oklch(parse_hex(spec_a["iris"]))[2]
    hb = rgb_to_oklch(parse_hex(spec_b["iris"]))[2]
    if abs((ha - hb + 180) % 360 - 180) > 40:
        n += 1
    return n


# ------------------------------------------------------------------ colours

class FaceColours:
    def __init__(self, ramps, spec, palette_hex):
        skin = ramps["skin"]
        self.skin = skin[2]
        self.skin_dark = skin[1]
        self.skin_deep = skin[0]
        self.lash = lash_colour(palette_hex["hair"])
        self.lash_soft = mix(self.lash, self.skin, 0.35)
        self.lid_crease = mix(self.lash, self.skin, 0.6)
        self.brow = brow_colour(palette_hex["hair"])
        self.brow_soft = mix(self.brow, self.skin, 0.45)
        self.sclera = ramps["sclera"][2]
        self.sclera_shade = mix(ramps["sclera"][1], self.lash, 0.12)
        self.white = (255, 255, 255)
        self.iris = ramp(spec["iris"])
        self.iris2 = ramp(spec["iris2"]) if spec.get("iris2") else self.iris
        self.glow = ramps["glow"]
        self.pupil = mix(self.iris[0], self.lash, 0.55)
        self.pupil2 = mix(self.iris2[0], self.lash, 0.55)
        self.shine = mix(self.white, self.iris[4], 0.45)
        self.shine2 = mix(self.white, self.iris2[4], 0.45)
        self.mouth_line = mix(skin[0], ramps["mouth_dark"][2], 0.5)
        self.mouth_soft = skin[0]
        self.lip = mix(skin[1], ramps["blush"][2], 0.35)
        self.mouth_dark = ramps["mouth_dark"][2]
        self.tongue = ramps["tongue"][2]
        self.teeth = ramps["teeth"][2]
        self.teeth_shade = ramps["teeth"][1]
        self.blush_light = mix(self.skin, ramps["blush"][2], 0.42)
        self.blush = mix(self.skin, ramps["blush"][2], 0.62)
        self.blush_line = mix(self.skin, ramps["blush"][1], 0.85)
        self.tear = ramps["tear"][2]
        self.tear_light = ramps["tear"][4]
        self.freckle = mix(skin[0], (138, 86, 58), 0.45)
        self.mole = mix(self.lash, skin[0], 0.25)
        self.scar = mix(skin[0], ramps["blush"][1], 0.4)
        self.scar_light = mix(skin[3], ramps["blush"][3], 0.25)
        self.stubble = mix(skin[1], parse_hex(palette_hex["hair"]), 0.35)
        self.bandage = (244, 240, 230)
        self.bandage_pad = mix((214, 186, 150), skin[0], 0.3)
        self.rim = ramps[spec["glasses_color"]][1] if spec.get("glasses") else None
        self.rim_light = ramps[spec["glasses_color"]][3] if spec.get("glasses") else None
        self.patch = mix(ramps["leather"][1], self.lash, 0.35)
        self.patch_light = ramps["leather"][2]
        self.patch_edge = mix(ramps["leather"][0], self.lash, 0.7)
        self.paint = {}
        for m in spec["marks"]:
            if m["type"] == "face_paint":
                slot = m.get("color", "accent")
                if slot not in ramps or slot == "_hex":
                    raise HerogenError("art.face.marks: face_paint colour %r is not a palette slot" % slot)
                self.paint[slot] = ramps[slot][2]


# ------------------------------------------------------------------ the skin layer and its marks

class FaceGeom:
    """Where the eyes sit on the face, for marks and wear: eye-local column x of the hero's right eye -> face column."""

    def __init__(self, spec):
        self.W = spec["width"]
        self.b = EW // 2 - 1 - spec["spacing"] // 2       # inner corner column of the hero's right eye
        self.a = self.b - self.W + 1                      # outer corner column

    def col(self, x, side="r"):
        c = self.a + x
        return c if side == "r" else EW - 1 - c


def _sides(m):
    return ("r", "l") if m["side"] == "both" else (m["side"],)


def skin_layer(spec, col):
    """FACE x FACE: the face's skin with the jaw shading and every mark (no holes)."""
    img = np.zeros((FACE, FACE, 4), dtype=np.uint8)
    img[:, :] = col.skin + (255,)
    last = FACE - 1
    for r, c in ((last, 0), (last, 1), (last, last - 1), (last, last), (last - 1, 0), (last - 1, last)):
        img[r, c] = col.skin_dark + (255,)
    g = FaceGeom(spec)

    def put(r, c, rgb):
        if 0 <= r < FACE and 0 <= c < FACE:
            img[r, c] = tuple(rgb) + (255,)

    for m in spec["marks"]:
        t = m["type"]
        for side in _sides(m):
            def fc(x):
                return g.col(x, side)
            if t == "freckles":
                for (r, x) in ((17, 1), (17, 3), (18, 2), (16, -1)):
                    put(r, fc(x), col.freckle)
                if spec["spacing"] >= 4:
                    put(15, fc(g.W), col.freckle)
            elif t == "beauty_mark":
                if m["at"] == "eye":
                    put(17, fc(0), col.mole)
                else:
                    put(18, MOUTH_AT[1] + MW - 1 if side == "l" else MOUTH_AT[1], col.mole)
            elif t == "scar":
                if m["at"] == "eye":
                    x = max(1, g.W // 2 - 1)
                    for r in range(3, 19):
                        put(r, fc(x), col.scar)
                    put(4, fc(x), col.scar_light)
                elif m["at"] == "cheek":
                    for k, (r, x) in enumerate(((16, -1), (17, 0), (18, 1), (19, 2))):
                        put(r, fc(x), col.scar if k % 2 == 0 else col.scar_light)
                else:   # across the nose bridge, between the eyes
                    for c in range(g.b + 1, EW - 1 - g.b):
                        put(13, c, col.scar)
                    put(12, g.b + 1, col.scar_light)
            elif t == "face_paint":
                paint = col.paint[m.get("color", "accent")]
                style = m["style"]
                if style == "stripes":
                    for r in (17, 19):
                        for x in (-1, 0, 1):
                            put(r, fc(x), paint)
                elif style == "tear":
                    for r in (17, 18):
                        put(r, fc(1), paint)
                    put(19, fc(1), mix(paint, col.skin, 0.4))
                elif style == "dots":
                    for x in (0, 2, 4):
                        put(17, fc(x), paint)
                elif style == "chevron":
                    for (r, x) in ((17, 0), (18, 1), (17, 2)):
                        put(r, fc(x), paint)
            elif t == "stubble":
                # a soft shadow along the jaw and the chin, every other texel
                for c in range(1, 19):
                    if c % 2 == 1:
                        put(19, c, col.stubble)
                for c in (0, 19):
                    put(18, c, col.stubble)
                break                                      # both sides done at once
            elif t == "bandage":
                for x in (0, 1, 2):
                    put(17, fc(x), col.bandage)
                    put(18, fc(x), col.bandage)
                put(17, fc(1), col.bandage_pad)
                put(18, fc(1), col.bandage_pad)
    # glasses: the bottom rims sit just under the eyes plane, on the skin (the planes carry the rest)
    if spec.get("glasses") in ("round", "square"):
        for side in ("r", "l"):
            xs = range(0, g.W) if spec["glasses"] == "square" else range(1, g.W - 1)
            for x in xs:
                put(17, g.col(x, side), col.rim)
    return img


# ------------------------------------------------------------------ eyes

class EyeCanvas:
    """One eye frame (EW x EH). Drawing uses (row, x) where x is the eye-local column (0 = outer corner) of the hero's
    right eye; `put` draws it and its mirror for the left eye unless `only` names one side ('r' or 'l')."""

    def __init__(self, spec, col, skin):
        self.skin_rows = np.array(skin[EYES_AT[0]:EYES_AT[0] + EH, :], dtype=np.uint8)
        self.img = self.skin_rows.copy()
        self.glow = np.zeros((EH, EW, 4), dtype=np.uint8)
        self.W = spec["width"]
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

    def put_side(self, row, x, rgbs, glow=False):
        """Different colours on the two sides (heterochromia): rgbs = (right, left)."""
        if rgbs[0] == rgbs[1]:
            self.put(row, x, rgbs[0], glow=glow)
        else:
            self.put(row, x, rgbs[0], only="r", glow=glow)
            self.put(row, x, rgbs[1], only="l", glow=glow)

    def get(self, row, x):
        return tuple(self.img[row, self.cols(x)[0], :3])


def _opening(spec, lid_fn=None):
    """{x: (top, bottom)} of the visible opening, body rows; lid_fn(x) lowers the top (an angry or narrowed lid)."""
    out = {}
    for x, (t, b) in enumerate(spec["cols"]):
        top = t + spec["lid"] + (lid_fn(x) if lid_fn else 0)
        if top <= b:
            out[x] = (top, b)
    return out


def _iris_cols(spec, shrink):
    I = max(1, spec["iris_width"] - shrink)
    cols = spec["cols"]
    heights = [b - t + 1 for t, b in cols]
    W = len(cols)
    # centre the iris where the opening is tallest (a sharp or droopy shape is not symmetric)
    best, start = -1, 0
    for s in range(0, W - I + 1):
        h = sum(heights[s:s + I]) * 4 - abs((s + I / 2.0) - W / 2.0)
        if h > best:
            best, start = h, s
    return list(range(start, start + I))


def _iris_tone(style, rr, n, edge):
    """Iris ramp index for visible row rr of n (rr 0 under the lash); edge: the column is the iris's outer column."""
    if style == "two_tone":
        return 0 if rr == 0 else (1 if rr < (n + 1) // 2 else (4 if rr == n - 1 else 3))
    if style == "ring":
        if rr == 0 or edge:
            return 0
        return 4 if rr == n - 1 else 3
    if style == "solid":
        return 1 if rr < n - 1 else 3
    if style == "glossy":
        if rr == 0:
            return 0
        return 4 if rr >= n - 2 else (1 if rr == 1 else 2)
    # gradient: dark under the lash, light at the bottom (the anime gradient)
    if rr == 0:
        return 0
    if rr == n - 1:
        return 4
    if rr == n - 2 and n >= 4:
        return 3
    return 1 if rr == 1 else 2


def _lash_rows(spec, opening):
    """{x: row} of the upper lash (frame rows) over an opening; gaps between neighbours filled later."""
    return {x: BODY + t - 1 for x, (t, b) in opening.items()}


def _draw_lash(cv, spec, col, opening, weight=None):
    weight = spec["lashes"] if weight is None else weight
    rows = _lash_rows(spec, opening)
    if not rows:
        return rows
    xs = sorted(rows)
    colour = col.lash if weight > 0 else col.lash_soft
    for x in xs:
        cv.put(rows[x], x, colour)
    # keep the line connected where the shape steps by more than one row
    for x0, x1 in zip(xs, xs[1:]):
        r0, r1 = rows[x0], rows[x1]
        if abs(r1 - r0) > 1:
            step = 1 if r1 > r0 else -1
            for r in range(r0 + step, r1, step):
                cv.put(r, x0 if step > 0 else x1, colour)
    if weight >= 2:
        r0 = rows[xs[0]]
        flick = spec["flick"]
        if flick == "up":
            cv.put(r0 - 1 if weight >= 3 else r0, -1, col.lash)
            if weight >= 3:
                cv.put(r0, -1, col.lash)
        elif flick == "down":
            cv.put(r0 + 1, -1, col.lash)
            if weight >= 3:
                cv.put(r0 + 2, -1, col.lash_soft)
        else:
            cv.put(r0, -1, col.lash)
    if weight >= 3:
        # a heavier line: the first opening row of the outer half is lash too
        for x in xs[:max(1, len(xs) // 3)]:
            t = opening[x][0]
            if opening[x][1] > t:
                cv.put(BODY + t, x, col.lash_soft)
    return rows


def _draw_lower_lash(cv, spec, col, opening):
    if not spec["lower_lash"] or 0 not in opening:
        return
    b = opening[0][1]
    if b < BODY_MAX - 1:
        cv.put(BODY + b + 1, 0, col.lash_soft)
    else:
        cv.put(BODY + b, -1, col.lash_soft)


def _draw_open(cv, spec, col, lid_fn=None, shrink=0, highlight=True, pupil=True, glow=False, white_top=False,
               small_pupil=False, virtual=True):
    """An open eye. virtual: iris tones count from the shape's own top (a lowered lid hides the dark top rows, the
    way a half-closed eye does); otherwise from the visible top (a narrowed eye shows the whole gradient)."""
    opening = _opening(spec, lid_fn)
    icols = _iris_cols(spec, shrink)
    style = spec["iris_style"]
    irises = (col.glow, col.glow) if glow else (col.iris, col.iris2)
    pupils = (col.white, col.white) if glow else (col.pupil, col.pupil2)
    shines = (col.shine, col.shine2)
    for x, (t, b) in opening.items():
        n_vis = b - t + 1
        for r in range(t, b + 1):
            cv.put(BODY + r, x, col.sclera_shade if r - t < max(1, n_vis // 2) else col.sclera)
    itop = {}
    for x in icols:
        if x not in opening:
            continue
        t, b = opening[x]
        t0 = spec["cols"][x][0] if virtual else t
        n = b - t0 + 1
        itop[x] = t
        for r in range(t, b + 1):
            if white_top and r == t:
                continue
            tone = _iris_tone(style, r - t0, n, x in (icols[0], icols[-1]) and len(icols) >= 4)
            cv.put_side(BODY + r, x, (irises[0][tone], irises[1][tone]), glow=glow)
    if not itop:
        _draw_lash(cv, spec, col, opening)
        return
    vis_rows = sorted({r for x in itop for r in range(itop[x], opening[x][1] + 1)})
    mid = icols[len(icols) // 2]
    if pupil and len(vis_rows) >= 3 and mid in opening:
        t, b = opening[mid]
        t0 = spec["cols"][mid][0] if virtual else t
        ptop = max(t, t0 + (1 if b - t0 <= 3 else 2))
        ptype = "dot" if small_pupil and spec["pupil"] in ("round", "ring") else spec["pupil"]
        k = (len(icols) - 1) // 2
        pcols = [mid] if len(icols) < 4 or ptype == "dot" else [icols[k], icols[k + 1]]
        prows = [r for r in ([ptop] if ptype == "dot" else [ptop, ptop + 1]) if r <= b]
        if ptype in ("round", "dot"):
            for r in prows:
                for x in pcols:
                    cv.put_side(BODY + r, x, pupils, glow=glow)
        elif ptype == "slit":
            for r in range(max(t, t0 + 1), b):
                cv.put_side(BODY + r, mid, pupils, glow=glow)
        elif ptype == "star":
            cv.put(BODY + ptop, mid, col.white, glow=glow)
            for x in pcols:
                if x != mid:
                    cv.put_side(BODY + ptop, x, (irises[0][4], irises[1][4]), glow=glow)
            if ptop + 1 <= b:
                cv.put_side(BODY + ptop + 1, mid, (irises[0][4], irises[1][4]), glow=glow)
        elif ptype == "ring":
            for r in prows:
                for x in pcols:
                    cv.put_side(BODY + r, x, pupils, glow=glow)
            for x in icols:
                if x not in pcols and x in opening and opening[x][0] <= prows[0] <= opening[x][1]:
                    cv.put_side(BODY + prows[0], x, (irises[0][4], irises[1][4]), glow=glow)
    _draw_lash(cv, spec, col, opening)
    _draw_lower_lash(cv, spec, col, opening)
    if highlight:
        _draw_highlights(cv, spec, col, opening, icols, itop, irises, pupils, shines, glow)


def _draw_highlights(cv, spec, col, opening, icols, itop, irises, pupils, shines, glow):
    kind = spec["highlights"]
    iris_px = {tuple(c) for c in irises[0]} | {tuple(pupils[0])}
    order = list(icols) if kind != "inner" else list(reversed(icols))
    spots = []
    for x in order:
        if x not in itop:
            continue
        t, b = opening[x]
        for r in range(t, min(b, t + 2) + 1):
            if r > t or b - t <= 2:
                if cv.get(BODY + r, x) in iris_px:
                    spots.append((BODY + r, x))
                    break
    if kind == "none":
        if spots:
            r, x = spots[0]
            cv.put_side(r, x, shines, glow=glow)
        return
    if not spots:
        spots = [(BODY + r, x) for x in order if x in itop for r in range(itop[x], opening[x][1] + 1)
                 if cv.get(BODY + r, x) in iris_px][:1]
    if not spots:
        return
    hrow, hx = spots[0]
    cv.put(hrow, hx, col.white, glow=glow)
    big = len(icols) >= 4 and (opening[hx][1] - opening[hx][0]) >= 3
    step = 1 if kind != "inner" else -1
    if big and hx + step in itop and opening[hx + step][0] <= hrow - BODY:
        cv.put(hrow, hx + step, col.white, glow=glow)
    if big and hrow + 1 < BODY + opening[hx][1]:
        cv.put(hrow + 1, hx, col.white, glow=glow)
    if kind in ("two", "sparkle", "inner"):
        lx = icols[-1] if kind != "inner" else icols[0]
        if lx in opening:
            lb = opening[lx][1]
            lr = lb - (1 if lb - opening[lx][0] >= 4 else 0)
            if lr >= opening[lx][0] + 1:
                cv.put_side(BODY + lr, lx, shines if kind != "sparkle" else (col.white, col.white), glow=glow)
    if kind == "sparkle":
        # a twinkle in the white beside the iris and a light lower iris row
        tx = icols[-1] + 1
        if tx in opening:
            cv.put(BODY + opening[tx][0] + 1, tx, col.white, glow=glow)
        x0 = icols[0]
        if x0 in opening:
            b0 = opening[x0][1]
            cv.put_side(BODY + b0, x0, shines, glow=glow)


def _closed_points(spec, frac, curve):
    """A closed lid: a dark line across the eye at `frac` of its height (the lash bar dropped), its ends one row
    higher when curved (a relaxed lid bows downward); heavier lashes carry the outer end one texel further out."""
    pts = []
    W = spec["width"]
    for x, (t, b) in enumerate(spec["cols"]):
        r = BODY + int(np.floor(t + (b - t) * frac + 0.5))
        if curve and x in (0, W - 1):
            r -= 1
        pts.append((r, x))
    if spec["lashes"] >= 2:
        r0 = pts[0][0]
        pts.append((r0 + (1 if spec["flick"] == "down" else 0), -1))
    return pts


def _draw_closed(cv, spec, col, frac, curve, thick=None):
    pts = _closed_points(spec, frac, curve)
    for (r, x) in pts:
        cv.put(r, x, col.lash)
    heavy = spec["lashes"] >= 2 if thick is None else thick
    if heavy:
        for (r, x) in pts[1:spec["width"] - 1]:
            if r + 1 <= BODY + BODY_MAX - 1:
                cv.put(r + 1, x, col.lash_soft)


def _brow_points(spec, mood):
    """(row, x, soft) of the hero's right brow (mirrored for the left); x 0 = outer end, soft = a lighter tail texel.

    Rest brows sit on rows 0-1 and leave row 2 as skin, so they never touch a lash on row 3. Expressions move them:
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
    thick = shape in ("straight_thick", "bold", "angled", "short")
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
    elif shape == "short":
        c = max(1, W // 2 - 1)
        pts = [(r, x, False) for r in (0, 1) for x in (c, c + 1)]
        return pts
    elif shape == "thin_arched" or mood == "happy":
        pts = [(1 if k in (0, n - 1) else 0, x, k == 0) for k, x in enumerate(xs)]   # arched, tapered outer tail
    elif shape == "worried":
        pts = [(1, x, k == 0) for k, x in enumerate(xs[:half])] + [(0, x, False) for x in xs[half:]]
    elif shape == "angled":                   # stern: high outside, low and heavy toward the nose
        pts = [(0 if k < half else 1, x, False) for k, x in enumerate(xs)]
    else:
        pts = [(1, x, False) for x in xs]
    if thick and mood not in ("sleeping", "surprised", "angry", "sad", "pain", "focused", "lowered"):
        rows = {(r, x) for r, x, _ in pts}
        for (r, x, _) in list(pts):
            if shape == "angled":
                if r == 1 and (0, x) not in rows and x >= W - 3:
                    pts.append((0, x, False))
                elif r == 0 and (1, x) not in rows and x == half - 1:
                    pts.append((1, x, False))
            elif r == 1 and (0, x) not in rows and (shape == "straight_thick" or _inner(x, W)):
                pts.append((0, x, False))
    elif thick and mood in ("angry", "focused"):
        rows = {(r, x) for r, x, _ in pts}
        for (r, x, _) in list(pts):
            if r - 1 >= 0 and (r - 1, x) not in rows and x >= half:
                pts.append((r - 1, x, False))
    return pts


def _inner(x, W):
    """True for the inner half of a brow (x 0 is the outer end)."""
    return x >= (W - 1) // 2


def _draw_brows(cv, spec, col, mood, only=None):
    for (row, x, soft) in _brow_points(spec, mood):
        cv.put(row, x, col.brow_soft if soft else col.brow, only=only)


def _draw_wear(cv, spec, col):
    """Glasses rims and an eye patch, the same in every frame (a blink happens behind them)."""
    W = spec["width"]
    g = spec.get("glasses")
    if g:
        top = min(_lash_rows(spec, _opening(spec)).values()) - 1
        top = max(2, top)
        rim, light = col.rim, col.rim_light
        if g == "half":
            for x in range(-1, W + 1):
                cv.put(top, x, rim)
            cv.put(top + 1, -1, rim)
            cv.put(top + 1, W, rim)
            cv.put(top, -1, light)
        else:
            xs = range(0, W) if g == "square" else range(1, W - 1)
            for x in xs:
                cv.put(top, x, rim)
            side_from = top if g == "square" else top + 1
            for r in range(side_from, EH):
                cv.put(r, -1, rim)
                cv.put(r, W, rim)
            if g == "round":
                cv.put(top + 1, 0, rim)
                cv.put(top + 1, W - 1, rim)
            cv.put(top, 1 if g == "round" else 0, light)
        # the bridge between the two lenses
        b = cv.b
        for c in range(b + 2, EW - 1 - b - 1):
            cv.img[top + 1, c] = tuple(rim) + (255,)
    side = spec.get("eyepatch")
    if side:
        top, bottom = LASH + 1, EH - 1
        skin = cv.skin_rows
        for r in range(LASH - 1, EH):                     # nothing of that eye shows: its lash and flick too
            for x in range(-1, W + 1):
                c = cv.cols(x)[0 if side == "r" else 1]
                if 0 <= c < EW:
                    cv.img[r, c] = skin[r, c]
        for r in range(top, bottom + 1):
            for x in range(0, W):
                corner = (r in (top, bottom)) and x in (0, W - 1)
                if corner:
                    continue
                edge = r in (top, bottom) or x in (0, W - 1)
                cv.put(r, x, col.patch_edge if edge else col.patch, only=side)
        for x in range(2, W - 2):
            cv.put(top + 1, x, col.patch_light, only=side)
        # the strap: from the patch's outer top corner to the temple, and from its inner top corner up the forehead
        cv.put(top, -1, col.patch_edge, only=side)
        cv.put(top - 1, -2, col.patch_edge, only=side)
        cv.put(top - 1, W - 1, col.patch_edge, only=side)
        for r in range(0, top - 1):
            cv.put(r, W - 1 + (top - 1 - r + 1) // 2, col.patch_edge, only=side)


def eye_frame(spec, col, frame, skin=None):
    """Paint eye frame `frame` (0-11). Returns (rgba, glow) EH x EW arrays."""
    if skin is None:
        skin = skin_layer(spec, col)
    cv = EyeCanvas(spec, col, skin)
    W = spec["width"]
    H = max(b for _, b in spec["cols"]) - min(t for t, _ in spec["cols"]) + 1
    name = EYE_FRAMES[frame]
    curve = spec["closed"] == "curve"
    if name == "open":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col)
    elif name == "half":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col, lid_fn=lambda x: max(1, (H - spec["lid"]) // 2), highlight=False, pupil=False)
    elif name == "closed":
        _draw_brows(cv, spec, col, "neutral")
        _draw_closed(cv, spec, col, 0.55, curve)
    elif name == "happy":
        # a rounded arch (^): the middle rows up, the ends down; heavier lashes extend the outer end
        _draw_brows(cv, spec, col, "happy")
        top = BODY + 1
        for x in range(W):
            d = min(x, W - 1 - x)
            cv.put(top + (2 if d == 0 else (1 if d == 1 else 0)), x, col.lash)
        if spec["lashes"] >= 2:
            cv.put(top + 3, 0, col.lash)
            cv.put(top + 2, -1, col.lash_soft)
    elif name == "angry":
        _draw_brows(cv, spec, col, "angry")
        _draw_open(cv, spec, col, lid_fn=lambda x: 1 if x >= W // 2 else 0, shrink=1)
    elif name == "sad":
        _draw_brows(cv, spec, col, "sad")
        _draw_open(cv, spec, col, lid_fn=lambda x: 1 if x <= 1 else 0)
        ic = _iris_cols(spec, 0)[0]
        cv.put(BODY + spec["cols"][ic][1], ic, col.tear)
    elif name == "surprised":
        _draw_brows(cv, spec, col, "surprised")
        _draw_open(cv, spec, col, shrink=1, white_top=True, small_pupil=True, virtual=False)
    elif name == "pain":
        _draw_brows(cv, spec, col, "pain")
        rows = 5
        x0 = max(0, (W - 4) // 2)
        for r in range(rows):
            x = x0 + min(r, rows - 1 - r)
            cv.put(BODY + r, x, col.lash)
            cv.put(BODY + r, x + 1, col.lash)
    elif name == "focused":
        _draw_brows(cv, spec, col, "focused")
        cut = max(1, H - int(np.floor(0.6 * H + 0.5)) - spec["lid"])
        _draw_open(cv, spec, col, lid_fn=lambda x: cut, virtual=False)
    elif name == "smug":
        _draw_brows(cv, spec, col, "lowered", only="r")
        _draw_brows(cv, spec, col, "surprised", only="l")
        _draw_open(cv, spec, col, lid_fn=lambda x: max(1, (H - spec["lid"]) // 2))
    elif name == "glowing":
        _draw_brows(cv, spec, col, "neutral")
        _draw_open(cv, spec, col, glow=True)
    elif name == "sleeping":
        _draw_brows(cv, spec, col, "sleeping")
        _draw_closed(cv, spec, col, 0.8, True, thick=False)
    _draw_wear(cv, spec, col)
    return cv.img, cv.glow


# ------------------------------------------------------------------ mouth and cheeks

def mouth_frame(spec, col, frame, skin=None):
    if skin is None:
        skin = skin_layer(spec, col)
    r0, c0_ = MOUTH_AT
    img = np.array(skin[r0:r0 + MH, c0_:c0_ + MW], dtype=np.uint8)

    def put(r, c, rgb):
        if 0 <= r < MH and 1 <= c < MW - 1:          # the edge columns stay the skin layer
            img[r, c] = tuple(rgb) + (255,)

    t = spec["mouth"]
    w = MOUTHS[t]
    c0 = 4 - (w + 1) // 2 if t != "smirk" else 3
    c1 = c0 + w - 1
    line = col.mouth_soft if t == "soft" else col.mouth_line
    name = MOUTH_FRAMES[frame]
    fang = spec["fang"]

    def omega(dark=False):
        for c, r in ((1, 1), (2, 2), (3, 1), (4, 1), (5, 2), (6, 1)):
            put(r, c, line)
        if dark:
            put(2, 3, col.mouth_dark)
            put(2, 4, col.mouth_dark)

    if name == "neutral":
        if t == "cat":
            omega()
        elif t == "stern":
            for c in range(c0, c1 + 1):
                put(1, c, line)
            put(1, c0 - 1, col.mouth_soft)
            put(1, c1 + 1, col.mouth_soft)
        elif t == "pout":
            for c in range(c0, c1 + 1):
                put(1, c, line)
                put(2, c, col.lip)
        elif t == "smirk":
            for c in range(c0, c1 + 1):
                put(1, c, line)
            put(0, c1 + 1, line)
        else:
            for c in range(c0, c1 + 1):
                put(1, c, line)
        if t == "fang" or (fang != "none" and t not in ("cat",)):
            if fang in ("right", "both", "none"):
                put(2, c0, col.teeth)
            if fang in ("left", "both"):
                put(2, c1, col.teeth)
    elif name == "smile":
        if t == "cat":
            omega()
            put(0, 1, line)
            put(0, 6, line)
        else:
            for c in range(c0, c1 + 1):
                put(1, c, line)
            put(0, c0 - 1, line)
            if t not in ("stern", "smirk"):
                put(0, c1 + 1, line)
            elif t == "smirk":
                put(0, c1 + 1, line)
                put(0, c1 + 2, line)
    elif name == "talk_a":
        for c in (3, 4):
            put(1, c, col.mouth_dark)
            put(2, c, col.tongue)
        if t in ("wide", "stern", "cat"):
            put(1, 2, col.mouth_dark)
            put(1, 5, col.mouth_dark)
    elif name == "talk_b":
        put(0, 2, col.mouth_dark)
        put(0, 5, col.mouth_dark)
        put(0, 3, col.teeth)
        put(0, 4, col.teeth)
        for c in range(2, 6):
            put(1, c, col.tongue if c in (3, 4) else col.mouth_dark)
        put(2, 3, col.mouth_dark)
        put(2, 4, col.mouth_dark)
        if t == "wide":
            put(1, 1, col.mouth_dark)
            put(1, 6, col.mouth_dark)
    elif name == "grit":
        for r in (0, 1):
            put(r, 1, col.mouth_dark)
            put(r, 6, col.mouth_dark)
            for c in range(2, 6):
                put(r, c, col.teeth if r == 0 else col.teeth_shade)
        put(2, 2, line)
        put(2, 5, line)
    elif name == "frown":
        for c in range(c0, c1 + 1):
            put(1, c, line)
        put(2, c0 - 1, line)
        put(2, c1 + 1, line)
        if t == "pout":
            put(0, 3, col.lip)
    elif name == "cat":
        omega(dark=t == "cat")
        if t == "cat":
            put(0, 1, line)
    elif name == "fang_grin":
        for c in range(2, 6):
            put(1, c, line)
        put(0, 1, line)
        put(0, 6, line)
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
        if 0 <= r < CH and 0 <= c < CW:
            img[r, c] = tuple(rgb) + (255,)

    g = FaceGeom(spec)
    name = CHEEK_FRAMES[frame]
    if name == "blush":
        style = spec["blush_style"]
        # under the outer halves of the eyes (cheek columns = face columns - 2)
        for side in ("r", "l"):
            cs = sorted(g.col(x, side) - CHEEKS_AT[1] for x in range(0, max(3, g.W - 2)))
            lo, hi = max(0, cs[0]), min(CW - 1, cs[-1])
            if style == "dots":
                mid = (lo + hi) // 2
                for c in (mid - 1, mid, mid + 1):
                    put(0, c, col.blush)
                    put(1, c, col.blush)
                put(0, mid - 1, col.blush_light)
                put(0, mid + 1, col.blush_light)
            else:
                for c in range(lo, hi + 1):
                    put(0, c, col.blush_light)
                for c in range(lo + 1, hi):
                    put(1, c, col.blush)
                if style == "hatch":
                    for c in range(lo + 1, hi, 2):
                        put(0, c, col.blush_line)
    elif name == "sweat":
        put(0, CW - 1, col.tear_light)
        put(1, CW - 1, col.tear)
        put(1, CW - 2, col.tear)
    elif name == "tears":
        for side in ("r", "l"):
            ic = _iris_cols(spec, 0)
            c = min(CW - 2, max(1, g.col(ic[0], side) - CHEEKS_AT[1]))
            put(0, c, col.tear_light)
            put(1, c, col.tear)
            put(1, c + (-1 if side == "r" else 1), col.tear)
    return img


def head_front_holes():
    """FACE x FACE mask of the transparent texels of the head front. Exactly under the eyes plane (face rows 7-16,
    full width); under the mouth plane (rows 17-19) without the plane's two edge columns, which are the skin layer in
    every mouth frame: the head front keeps that skin behind them, so the 0.05 px gap between plane and head shows
    skin, never the inside of the head, when the face is seen at an angle (the two coincident texels are the same
    colour, so no flicker)."""
    holes = np.zeros((FACE, FACE), dtype=bool)
    holes[EYES_AT[0]:EYES_AT[0] + EH, :] = True
    holes[MOUTH_AT[0]:MOUTH_AT[0] + MH, MOUTH_AT[1] + 1:MOUTH_AT[1] + MW - 1] = True
    return holes


def head_front(spec, col, skin=None):
    """FACE x FACE head front: the skin layer, transparent under the eyes and mouth planes."""
    img = np.array(skin if skin is not None else skin_layer(spec, col), dtype=np.uint8)
    # the eye patch's strap continues over the forehead to the temple (under the bangs)
    side = spec.get("eyepatch")
    if side:
        # the strap continues from the patch's inner top corner up across the forehead (mostly under the bangs)
        g = FaceGeom(spec)
        x0 = g.W - 1 + (LASH + 1) // 2           # where the strap leaves the eyes plane's top row
        for k in range(0, EYES_AT[0]):
            c = g.col(x0 + (k + 1) // 2, side)
            if 0 <= c < FACE:
                img[EYES_AT[0] - 1 - k, c] = col.patch_edge + (255,)
    img[head_front_holes()] = 0
    return img


# ------------------------------------------------------------------ whole face

def paint_face(img, glow, rig, spec, ramps, palette_hex):
    """Write every frame and the head front into the texture at the fixed template regions."""
    col = FaceColours(ramps, spec, palette_hex)
    skin = skin_layer(spec, col)
    for k in range(12):
        f, g = eye_frame(spec, col, k, skin)
        u, v, w, h = frame_rect(rig, "eyes", k)
        img[v:v + h, u:u + w] = f
        glow[v:v + h, u:u + w] = g
    for k in range(8):
        u, v, w, h = frame_rect(rig, "mouth", k)
        img[v:v + h, u:u + w] = mouth_frame(spec, col, k, skin)
    for k in range(4):
        u, v, w, h = frame_rect(rig, "cheeks", k)
        img[v:v + h, u:u + w] = cheek_frame(spec, col, k)
    hu, hv = rig["uv_template"]["head_front"][:2]
    img[hv:hv + FACE, hu:hu + FACE] = head_front(spec, col, skin)


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
