"""Artifact and token icons (ARTIFACTS.md 1.2-1.3, task R6; BOSS_HEROES.md 3.4, task B5).

16x16 item textures in the vanilla style, generated from two source files:

- `herosrc/artifacts/shapes.json`: the shape library. A shape is a 16x16 grid of letters ('.' is empty); each letter
  is a part with a role (`body`, `setting`, `precious`, `gem`, `glow`, `fitting`, `cord`, `accent`, `detail`) and a
  profile (`dome`, `bevel`, `flat`, `rod`). A part may be `optional`: when an icon gives it no material it is drawn
  as the part named by its `else`, so one silhouette serves an artifact and the greater artifact built on it.
  A part may carry a growth `stage` (1-3): below that state its pixels are drawn as its `else`, from it on as the
  part named by its `as` (or as itself; '.' is the empty canvas for either), so one grid holds a growing artifact's
  four states.
  Shapes are silhouettes, not paintings: every tone comes from the painter.
- `herosrc/artifacts/icons.json`: the materials (a tone-0 colour and a lightness step for `palette.ramp_n`, an
  optional texture) and one spec per icon: its tier, its shape (optionally mirrored), a material per part letter,
  the gem's element, the glow colour, the accent material and, for a growing artifact, `"growth": true`.

The painter (`paint`) follows what vanilla's items do (read from the 1.21.1 client textures): binary alpha, one
object lit from the top left, the darkest tone of each solid part as its outline where it meets the empty canvas
(cords, rods and glass rims have none, as vanilla's string, feathers and bottles), the inner top-left edges a tone
lighter and the inner bottom-right edges a tone darker, domed parts shaded across their whole area with a one-pixel
specular at their brightest point, and a white sparkle on each gem. Every material is one `palette.ramp_n` ramp of
seven tones, so a material never paints more than eight colours (its seven tones and the white of a sparkle).

Tiers by material richness (1.2), checked by `check_spec`: a component is one material (with cords and fittings);
advanced artifacts add a setting and a gem in their element's colour; greater artifacts add a second precious
material and a one-pixel white highlight on it; rare finds carry a gem; signatures carry a setting, a precious part
and a pulse. Rare finds and signatures get the enchantment glint from the item (`enchantment_glint_override`), never
from the texture. Signatures and the two boss tokens pulse slowly: four frames stacked vertically (16x64) and a
`.png.mcmeta` with `interpolate`, the glowing parts dim, mid, bright, mid; the alpha is the same in every frame.

Output: `src/main/resources/assets/factionclash/textures/item/<item id>.png` (and `.png.mcmeta` for the pulsing
ones), named by the item ids the item side registers (R2, B2): the artifact ids of the catalogue, `artificers_sigil`,
`wither_token` and `ender_dragon_token`. An icon's id in icons.json is its texture's name. A growing artifact (ARTIFACTS.md
1.2 and 7.8) has four textures, its growth states: `<id>.png` (empty) and `<id>_1.png`, `<id>_2.png`, `<id>_3.png`
(a third, two thirds, full), which the item side picks with an item property as a bow's pull picks its icon. Any other
second look of an item (a hollow copy) is a spec whose id starts with the item's id and names it in `variant_of`; the
item side points a model override at it. Deterministic: integer pixel maths after the ramps, hashes from util.

The checks (`problems`, run by `herogen icons --check` and `herogen check`) read the catalogue from ARTIFACTS.md's "The
catalogue" section by its headings' names (Components, Advanced, Greater, Rare finds, Signature, and the revision's
Growing (advanced) and Engineered (greater)), so a new artifact under one of those headings is an error until icons.json
has its spec, and one under a heading that names no tier a NOTE: adding an icon is a data change (a spec, and a shape if
none fits).
"""
import os

import numpy as np

import palette
import util
from util import HerogenError

SIZE = 16
SRC_DIR = os.path.join(util.HEROSRC, "artifacts")
OUT_DIR = os.path.join(util.ROOT, "src", "main", "resources", "assets", "factionclash", "textures", "item")

LO, HI = -3, 3                       # material tones; a ramp's index is tone - LO
WHITE = (255, 255, 255)

# The eight element colours (HERO_FORMAT 6.1's particle table, as each particle reads in game, lifted to a gem's
# saturation): physical the crit spark's warm grey, fire the flame, frost the snowflake's tint (0.92, 0.96, 1.0),
# shock a hot electric yellow (the spark is a white flash; yellow is the roster's storm colour), venom and blight
# their dust colours exactly, arcane the enchanting glyphs' lavender (0.9, 0.9, 1.0) deepened to the glint's violet,
# radiant the end rod's warm white (its fade colour #F2DEC9).
ELEMENTS = {
    "physical": "#B4ABA0",
    "fire": "#FF7A1A",
    "frost": "#8FD3F7",
    "shock": "#FFDD3C",
    "venom": "#5BAE3C",
    "blight": "#2E2A33",
    "arcane": "#A46BF0",
    "radiant": "#FFF0C4",
}

# Tier -> (name colour, label): Minecraft's chat colours of ARTIFACTS.md 1.3 and BOSS_HEROES.md 3.4.
TIER_COLOURS = {
    "component": ("#FFFF55", "Components: yellow, vanilla uncommon"),
    "advanced": ("#55FFFF", "Advanced: aqua, vanilla rare"),
    "greater": ("#FF55FF", "Greater: light purple, vanilla epic"),
    "rare": ("#FF55FF", "Rare finds: light purple, glint"),
    "signature": ("#FFAA00", "Signatures: gold, glint, a slow pulse"),
    "sigil": ("#FFFFFF", "The smithing template"),
    "token": ("#FF55FF", "Boss hero tokens: epic, glint, a slow pulse"),
}
TIERS = tuple(TIER_COLOURS)
PULSING = ("signature", "token")
GLINT = ("rare", "signature", "token")

ROLES = ("body", "setting", "precious", "gem", "glow", "fitting", "cord", "accent", "detail")
PROFILES = ("dome", "bevel", "flat", "rod")
OUTLINES = (True, False, "shadow")
LAYS = ("twist", "rows")
TEXTURES = ("speckle", "grain", "wound", "none")
OUTLINED = ("body", "setting", "precious", "gem", "glow", "fitting", "accent")

PULSE_FRAMES = (-1, 0, 1, 0)          # the glowing parts' tone offset per frame
GROWTH_STATES = 4                     # a growing artifact's icons: empty, a third, two thirds, full (ARTIFACTS.md 1.2)
PULSE_FRAMETIME = 10                  # ticks per frame: a 2 s cycle, interpolated
MAX_MATERIAL_COLOURS = 8              # seven tones and a white sparkle

# Tier rules: roles an icon of the tier must have, roles it may not (ARTIFACTS.md 1.2).
TIER_RULES = {
    "component": ((), ("setting", "gem", "precious", "glow")),
    "sigil": (("accent",), ("gem", "precious", "glow")),
    "advanced": (("setting", "gem"), ("precious", "glow")),
    "greater": (("setting", "gem", "precious"), ("glow",)),
    "rare": (("gem",), ()),
    "signature": (("setting", "precious"), ()),
    "token": (("glow",), ()),
}


# ------------------------------------------------------------------ sources

def load(src_dir=SRC_DIR):
    """The shape library and the icon file: (shapes, materials, icons)."""
    shapes = util.load_json(os.path.join(src_dir, "shapes.json"))
    icons = util.load_json(os.path.join(src_dir, "icons.json"))
    for doc, name in ((shapes, "shapes.json"), (icons, "icons.json")):
        if doc.get("format") != 1:
            raise HerogenError("herosrc/artifacts/%s: format must be 1" % name)
    return shapes["shapes"], icons["materials"], icons["icons"]


def check_shape(name, shape):
    """The grid as a (16, 16) array of letters; raises HerogenError on a malformed shape."""
    rows = shape.get("grid")
    if not isinstance(rows, list) or len(rows) != SIZE or any(not isinstance(r, str) or len(r) != SIZE
                                                              for r in rows):
        raise HerogenError("shape %s: grid must be 16 rows of 16 characters" % name)
    g = np.array([list(r) for r in rows])
    parts = shape.get("parts", {})
    for ch in sorted(set(g.flatten()) - {"."}):
        if ch not in parts:
            raise HerogenError("shape %s: letter %r has no part" % (name, ch))
    named = set()                                            # parts that appear through another part's else or as
    for ch, p in sorted(parts.items()):
        if "stage" not in p:
            continue
        if p.get("stage") not in range(1, GROWTH_STATES) or p.get("optional"):
            raise HerogenError("shape %s: part %s: a growth stage is 1 to %d (and the part not optional)"
                               % (name, ch, GROWTH_STATES - 1))
        for key in ("else", "as"):
            q = p.get(key)
            if q is None and key == "as" or q == ".":
                continue
            if q not in parts or "stage" in parts[q] or parts[q].get("optional"):
                raise HerogenError("shape %s: stage part %s: `%s` must name a part without a stage (or '.' for "
                                   "none)" % (name, ch, key))
            named.add(q)
        if not (g == ch).any():
            raise HerogenError("shape %s: part %s is not in the grid" % (name, ch))
    for ch, p in sorted(parts.items()):
        if "stage" in p and "as" in p:
            continue                                         # drawn as another part: no role of its own
        if p.get("role") not in ROLES:
            raise HerogenError("shape %s: part %s: role %r is not one of %s" % (name, ch, p.get("role"),
                                                                                ", ".join(ROLES)))
        if p.get("profile", "bevel") not in PROFILES:
            raise HerogenError("shape %s: part %s: profile %r is not one of %s" % (name, ch, p.get("profile"),
                                                                                  ", ".join(PROFILES)))
        if p.get("outline", True) not in OUTLINES:
            raise HerogenError("shape %s: part %s: outline %r is not true, false or \"shadow\"" % (name, ch,
                                                                                                p.get("outline")))
        if p.get("lay", "twist") not in LAYS:
            raise HerogenError("shape %s: part %s: lay %r is not one of %s" % (name, ch, p.get("lay"),
                                                                              ", ".join(LAYS)))
        if p.get("texture", "none") not in TEXTURES:
            raise HerogenError("shape %s: part %s: texture %r is not one of %s" % (name, ch, p.get("texture"),
                                                                                  ", ".join(TEXTURES)))
        if p["role"] == "detail" and (p.get("of") not in parts or parts[p["of"]]["role"] == "detail"):
            raise HerogenError("shape %s: detail %s must name the part it is cut into (`of`)" % (name, ch))
        if p.get("optional") and p.get("else") != "." and (p.get("else") not in parts
                                                            or parts[p["else"]].get("optional")):
            raise HerogenError("shape %s: optional part %s must name the part it falls back to (`else`, or '.' "
                               "for none)" % (name, ch))
        if not (g == ch).any() and ch not in named and \
                not any(q.get("optional") and q.get("else") == ch for q in parts.values()):
            raise HerogenError("shape %s: part %s is not in the grid" % (name, ch))
    return g


def growth_states(spec):
    """The states an icon is drawn in: 0-3 for a growing artifact, else one (None: every stage part drawn)."""
    return list(range(GROWTH_STATES)) if spec.get("growth") else [None]


def textures(icon_id, spec):
    """[(texture name, growth state)]: `<id>` alone, or a growing artifact's `<id>`, `<id>_1`, `<id>_2`, `<id>_3`."""
    return [(icon_id if not s else "%s_%d" % (icon_id, s), s) for s in growth_states(spec)]


def resolve(spec, shapes, state=None):
    """(grid, parts) of an icon: the shape's grid mirrored if the spec says so, its stage parts drawn for the growth
    `state` (0-3; None draws them all: the full state, or a shape that does not grow), its optional parts without a
    material folded into their `else` part, and the parts that remain."""
    name = spec["shape"]
    shape = shapes[name]
    g = check_shape(name, shape)
    mats = spec.get("parts", {})
    for ch, p in sorted(shape["parts"].items()):
        if "stage" in p:
            reached = state is None or state >= p["stage"]
            g = np.where(g == ch, p.get("as", ch) if reached else p["else"], g)
    parts = {}
    for ch, p in shape["parts"].items():
        if "stage" in p and "as" in p:
            continue
        if p.get("optional") and ch not in mats:
            g = np.where(g == ch, p["else"], g)
        else:
            parts[ch] = p
    for ch, p in parts.items():
        if p["role"] == "detail" and p["of"] not in parts:
            g = np.where(g == ch, p["of"], g)
    parts = {ch: p for ch, p in parts.items() if (g == ch).any()}
    for ch, p in sorted(parts.items()):
        if p["role"] == "detail" and p["of"] not in parts:
            raise HerogenError("shape %s: detail %s is drawn in growth state %s, and the part it is cut into (%s) "
                               "is not" % (name, ch, state, p["of"]))
    if spec.get("flip"):
        g = g[:, ::-1]
    return g, parts


def check_spec(icon_id, spec, shapes, materials):
    """Problems with one icon spec (an empty list when it is fine). A growing artifact's spec is checked in each of
    its four growth states (a problem of some states only names them), and its states must each look different."""
    tier = spec.get("tier")
    if tier not in TIERS:
        return ["%s: tier %r is not one of %s" % (icon_id, tier, ", ".join(TIERS))]
    if spec.get("shape") not in shapes:
        return ["%s: shape %r is not in the library" % (icon_id, spec.get("shape"))]
    if spec.get("growth") not in (None, True):
        return ["%s: growth must be true (a growing artifact) or left out" % icon_id]
    states = growth_states(spec)
    try:
        resolved = [resolve(spec, shapes, st) for st in states]
    except HerogenError as e:
        return ["%s: %s" % (icon_id, e)]
    per_state = [_check_state(icon_id, spec, g, parts, materials) for g, parts in resolved]
    out = []
    for i, msgs in enumerate(per_state):
        for m in msgs:
            if all(m in other for other in per_state):
                if m not in out:
                    out.append(m)
            else:
                out.append("%s (growth state %d)" % (m, states[i]))
    for ch in sorted(spec.get("parts", {})):
        if ch not in shapes[spec["shape"]]["parts"]:
            out.append("%s: material given for %r, which the shape %s does not have" % (icon_id, ch, spec["shape"]))
    if spec.get("growth"):
        if not any("stage" in p for p in shapes[spec["shape"]]["parts"].values()):
            out.append("%s: a growing artifact's shape needs stage parts (its four states, ARTIFACTS.md 1.2)"
                       % icon_id)
        else:
            for i in range(1, len(states)):
                if np.array_equal(resolved[i][0], resolved[i - 1][0]):
                    out.append("%s: growth state %d looks the same as state %d (give a part `stage` %d)"
                               % (icon_id, states[i], states[i - 1], states[i]))
    if not spec.get("what"):
        out.append("%s: no `what` (what the icon shows, for the sheet)" % icon_id)
    return out


def _check_state(icon_id, spec, g, parts, materials):
    """The materials and the tier rules of one resolved look of an icon."""
    out = []
    tier = spec["tier"]
    mats = spec.get("parts", {})
    for ch, p in sorted(parts.items()):
        role = p["role"]
        if role in ("gem", "glow", "detail"):
            continue
        m = mats.get(ch) or (spec.get("accent") if role == "accent" else None)
        if m is None:
            out.append("%s: part %s (%s) has no material" % (icon_id, ch, role))
        elif m not in materials:
            out.append("%s: part %s: material %r is not in the materials table" % (icon_id, ch, m))
    roles = {p["role"] for p in parts.values()}
    if "gem" in roles and spec.get("gem") not in ELEMENTS:
        out.append("%s: gem %r is not one of the eight element colours (%s)" % (icon_id, spec.get("gem"),
                                                                               ", ".join(ELEMENTS)))
    if "gem" not in roles and spec.get("gem"):
        out.append("%s: a gem is given and the shape has no gem part" % icon_id)
    if "glow" in roles:
        try:
            glow_hex(spec)
        except HerogenError as e:
            out.append("%s: %s" % (icon_id, e))
    need, refuse = TIER_RULES[tier]
    a = "an" if tier[0] in "aeiou" else "a"
    for r in need:
        if r not in roles:
            out.append("%s: %s %s icon needs a %s part (ARTIFACTS.md 1.2)" % (icon_id, a, tier, r))
    for r in refuse:
        if r in roles:
            out.append("%s: %s %s icon may not have a %s part (ARTIFACTS.md 1.2)" % (icon_id, a, tier, r))
    body = {mats.get(ch) for ch, p in parts.items() if p["role"] == "body"}
    if tier in ("component", "sigil") and len(body) > 1:
        out.append("%s: a component is one material; its body parts use %s" % (icon_id, ", ".join(sorted(body))))
    if tier in ("greater", "signature"):
        main = {mats.get(ch) for ch, p in parts.items() if p["role"] in ("body", "setting")}
        prec = {mats.get(ch) for ch, p in parts.items() if p["role"] == "precious"}
        if prec & main:
            out.append("%s: the precious part must be a second material (%s is also its body or setting)"
                       % (icon_id, ", ".join(sorted(prec & main))))
    return out


def glow_hex(spec):
    g = spec.get("glow")
    if g in ELEMENTS:
        return ELEMENTS[g]
    try:
        palette.parse_hex(g)
    except HerogenError:
        raise HerogenError("glow %r is neither an element nor #RRGGBB" % (g,))
    return g


# ------------------------------------------------------------------ ramps

def ramp_of(ch, p, spec, materials):
    """Seven RGB tones (-3..+3) for a part."""
    role = p["role"]
    if role == "gem":
        return palette.ramp_n(ELEMENTS[spec["gem"]], LO, HI, step=float(p.get("step", 0.1)))
    if role == "glow":
        return palette.ramp_n(glow_hex(spec), LO, HI, step=float(p.get("step", 0.07)))
    name = spec.get("parts", {}).get(ch) or (spec.get("accent") if role == "accent" else None)
    mat = materials[name]
    return palette.ramp_n(mat["colour"], LO, HI, step=float(mat.get("step", 0.09)))


def texture_of(ch, p, spec, materials):
    if "texture" in p:
        return p["texture"]
    if p["role"] in ("gem", "glow", "detail"):
        return "none"
    name = spec.get("parts", {}).get(ch) or (spec.get("accent") if p["role"] == "accent" else None)
    return materials.get(name, {}).get("texture", "none")


# ------------------------------------------------------------------ painter

def _shift(mask, dy, dx):
    """The mask's value at (y - dy, x - dx): the neighbour on the side (dy, dx) points away from. False off the
    canvas."""
    out = np.zeros_like(mask)
    h, w = mask.shape
    ys = slice(max(dy, 0), h + min(dy, 0))
    xs = slice(max(dx, 0), w + min(dx, 0))
    yd = slice(max(-dy, 0), h + min(-dy, 0))
    xd = slice(max(-dx, 0), w + min(-dx, 0))
    out[ys, xs] = mask[yd, xd]
    return out


def _sides(mask):
    """(up, down, left, right): whether each pixel's neighbour on that side is in the mask."""
    return _shift(mask, 1, 0), _shift(mask, -1, 0), _shift(mask, 0, 1), _shift(mask, 0, -1)


def _dome(mask):
    """A tone offset across a domed part: up to +2 at its top-left and -2 at its bottom-right (the light from the
    top left), along the diagonal from the part's centre in units of its half-size."""
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return np.zeros(mask.shape, dtype=np.int32)
    cx = (xs.min() + xs.max()) / 2.0
    cy = (ys.min() + ys.max()) / 2.0
    half = max(1.0, (xs.max() - xs.min() + ys.max() - ys.min()) / 2.0)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    d = -((xx - cx) + (yy - cy)) / half
    off = np.floor(d * 2.2 + 0.5).astype(np.int32)
    return np.clip(off, -2, 2) * mask


def _texture(t, inner, kind, seed, ch):
    if kind in ("none", None):
        return t
    t = t.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            if not inner[y, x]:
                continue
            if kind == "speckle":
                if util.hash_unit(seed, ord(ch), x, y) < 0.2:
                    t[y, x] += -1 if util.hash_unit(seed, x, y, 7) < 0.65 else 1
            elif kind == "grain":
                if y % 2 == 1 and util.hash_unit(seed, ord(ch), x // 2, y) < 0.45:
                    t[y, x] -= 1
            elif kind == "wound":
                if (x + y) % 3 == 0:
                    t[y, x] -= 1
    return np.where(inner, np.clip(t, -2, 2), t)


def paint(icon_id, spec, shapes, materials, frame=0, state=None):
    """One frame of an icon: a (16, 16, 4) uint8 array. `frame` indexes PULSE_FRAMES for the pulsing tiers, `state`
    is a growing artifact's growth state (0-3; None: every stage part drawn)."""
    return _paint(icon_id, spec, shapes, materials, frame, state)[0]


def material_key(ch, p, spec):
    """The material a part paints with: a material name, `gem:<element>` or `glow:<colour>`."""
    if p["role"] == "gem":
        return "gem:" + spec["gem"]
    if p["role"] == "glow":
        return "glow:" + glow_hex(spec)
    return spec.get("parts", {}).get(ch) or (spec.get("accent") if p["role"] == "accent" else None)


def _paint(icon_id, spec, shapes, materials, frame=0, state=None):
    """(rgba, materials): the frame and, per pixel, the key of the material that painted it (None if empty). The
    texture hashes are seeded by the icon's id, so a growth state's stone or wood grain stays where it was."""
    g, parts = resolve(spec, shapes, state)
    obj = g != "."
    seed = util.seed_of("icon:" + icon_id)
    pulse = PULSE_FRAMES[frame % len(PULSE_FRAMES)] if spec["tier"] in PULSING else 0
    up_o, down_o, left_o, right_o = _sides(obj)
    edge = obj & ~(up_o & down_o & left_o & right_o)          # touches the empty canvas
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    tone = np.zeros((SIZE, SIZE), dtype=np.int32)
    ramps = {}
    solid = {}                                                 # part -> its pixels (details included)
    for ch, p in parts.items():
        solid.setdefault(p["of"] if p["role"] == "detail" else ch, []).append(ch)
    for ch in sorted(parts):
        p = parts[ch]
        role = p["role"]
        if role == "detail":
            continue
        ramps[ch] = ramp_of(ch, p, spec, materials)
        mask = np.isin(g, solid[ch])
        profile = p.get("profile", "dome" if role in ("gem", "glow") else "bevel")
        outline = p.get("outline", role in OUTLINED and profile != "rod")
        if role == "cord":
            lay = p.get("lay", "twist")
            if lay == "rows":                       # thread wound on a spool: one row light, the next dark
                t = np.where(yy % 2 == 0, 1, -1)
                t = t - (mask & ~_shift(mask, 0, -1)).astype(np.int32)
            else:                                   # a twisted cord or a chain: alternate along it
                t = np.where((xx + yy) % 2 == 0, 1, -1)
            t = np.where(mask, np.clip(t, -2, 2), 0)
        else:
            if outline == "shadow":                 # the darkest tone only on the sides away from the light
                rim = mask & edge & (~down_o | ~right_o)
            else:
                rim = (mask & edge) if outline else np.zeros_like(mask)
            inner = mask & ~rim
            up, down, left, right = _sides(inner)
            lit = (inner & (~up | ~left) & down & right).astype(np.int32)
            dark = (inner & (~down | ~right) & up & left).astype(np.int32)
            if profile == "flat":
                t = np.zeros((SIZE, SIZE), dtype=np.int32)
            elif profile == "dome":
                t = _dome(inner) + lit - dark
            elif profile == "rod":
                t = _dome(inner) // 2 + lit - dark
            else:
                t = lit - dark
            t = np.clip(t, -2, 2)
            if role in ("gem", "glow"):
                # where a gem sits in its setting its edge is a tone darker
                seat = inner & ~_shift(mask, 1, 0) | inner & ~_shift(mask, -1, 0) | \
                    inner & ~_shift(mask, 0, 1) | inner & ~_shift(mask, 0, -1)
                t = np.where(seat & (t > -2), t - 1, t)
            t = _texture(t, inner, texture_of(ch, p, spec, materials), seed, ch)
            if p.get("specular", profile == "dome") and inner.sum() >= 4:
                score = np.where(inner, t * 64 - (xx + yy), -10 ** 6)
                y, x = np.unravel_index(int(np.argmax(score)), score.shape)
                t[y, x] = HI
            t = np.where(rim, LO, t)
        if role == "glow":
            t = np.where(mask & (t > LO), np.clip(t + pulse, LO + 1, HI), t)
        tone = np.where(mask, t, tone)
    for ch in sorted(parts):
        p = parts[ch]
        if p["role"] == "detail":
            keep = p.get("keep_outline", True)
            m = (g == ch) & ~(edge & keep)
            tone = np.where(m, int(p.get("tone", -2)), tone)
    out = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    mat = np.full((SIZE, SIZE), None, dtype=object)
    for y in range(SIZE):
        for x in range(SIZE):
            ch = g[y, x]
            if ch == ".":
                continue
            p = parts[ch]
            src = p["of"] if p["role"] == "detail" else ch
            out[y, x, :3] = ramps[src][int(tone[y, x]) - LO]
            out[y, x, 3] = 255
            mat[y, x] = material_key(src, parts[src], spec)
    _sparkles(out, g, parts, spec, solid)
    return out, mat


def _sparkles(out, g, parts, spec, solid):
    """A white sparkle on each gem (its top-left inner pixel) and, for the greater and signature tiers, the
    one-pixel white highlight on the precious part (its brightest, top-left-most pixel)."""
    for ch, p in sorted(parts.items()):
        rich = p["role"] == "precious" and spec["tier"] in ("greater", "signature")
        if p["role"] != "gem" and not rich:
            continue
        mask = np.isin(g, solid[ch])
        if mask.sum() < 4 or p.get("sparkle") is False:
            continue
        up, down, left, right = _sides(mask)
        inner = mask & up & left & down & right
        if not inner.any():
            inner = mask
        ys, xs = np.nonzero(inner)
        if rich:
            lum = out[ys, xs, :3].astype(np.int32).sum(axis=1)
            k = int(np.lexsort((xs, ys, -lum))[0])
        else:
            k = int(np.argmin(ys * 32 + xs))
        out[ys[k], xs[k], :3] = WHITE


def frames(icon_id, spec, shapes, materials, state=None):
    n = len(PULSE_FRAMES) if spec["tier"] in PULSING else 1
    return [paint(icon_id, spec, shapes, materials, f, state) for f in range(n)]


def image_of(icon_id, spec, shapes, materials, state=None):
    """The texture as an RGBA PIL image: 16x16, or 16x64 (four frames stacked) for a pulsing icon; a growing
    artifact's `state` (0-3; None: the full look)."""
    from PIL import Image
    return Image.fromarray(np.concatenate(frames(icon_id, spec, shapes, materials, state), axis=0), "RGBA")


def mcmeta(spec):
    if spec["tier"] not in PULSING:
        return None
    return {"animation": {"frametime": PULSE_FRAMETIME, "interpolate": True}}


def build_files(src_dir=SRC_DIR, out_dir=OUT_DIR):
    """{path: bytes} of every icon texture and animation file."""
    shapes, materials, icons = load(src_dir)
    files = {}
    for icon_id in sorted(icons):
        spec = icons[icon_id]
        meta = mcmeta(spec)
        for name, state in textures(icon_id, spec):
            files[os.path.join(out_dir, name + ".png")] = util.png_bytes(image_of(icon_id, spec, shapes, materials,
                                                                                  state))
            if meta is not None:
                files[os.path.join(out_dir, name + ".png.mcmeta")] = util.dumps_json(meta).encode("utf-8")
    return files


def texture_owners(icons):
    """{texture name: (icon id, growth state)} of every texture the specs make."""
    out = {}
    for icon_id in sorted(icons):
        for name, state in textures(icon_id, icons[icon_id]):
            out.setdefault(name, (icon_id, state))
    return out


# ------------------------------------------------------------------ the catalogue and the checks

ARTIFACTS_MD = os.path.join(util.ROOT, "docs", "heroes-v2", "ARTIFACTS.md")
# The catalogue's headings, by the first word of their title: (word, tier, kind). ARTIFACTS.md 7.1-7.5 ("Components
# (12, crafting table)", "Advanced (...)", "Greater (...)", "Rare finds (...)", "Signature artifacts (...)") and the
# revision's 7.8 "Growing artifacts" (advanced, with four growth states: 1.2, 1.3) and 7.9 "Engineered artifacts"
# (greater, 1.3). Read by name, not number, so a renumbered or a longer catalogue still reads.
CATALOGUE_TIERS = (("component", "component", None), ("advanced", "advanced", None), ("greater", "greater", None),
                   ("rare", "rare", None), ("signature", "signature", None), ("growing", "advanced", "growing"),
                   ("engineered", "greater", "engineered"))
# Items that are not catalogue artifacts: the smithing template (ARTIFACTS.md 1.1) and the boss hero tokens
# (BOSS_HEROES.md 3.4).
EXTRA_ITEMS = {"artificers_sigil": ("Artificer's Sigil", "sigil"),
               "wither_token": ("Wither Hero Token", "token"),
               "ender_dragon_token": ("Ender Dragon Hero Token", "token")}
ELEMENT_WORDS = tuple(ELEMENTS)


class Entry(tuple):
    """A catalogue row: (name, tier, element, kind); `kind` is "growing", "engineered" or None."""
    __slots__ = ()

    def __new__(cls, name, tier, element=None, kind=None):
        return tuple.__new__(cls, (name, tier, element, kind))

    name = property(lambda self: self[0])
    tier = property(lambda self: self[1])
    element = property(lambda self: self[2])
    kind = property(lambda self: self[3])


def catalogue(path=ARTIFACTS_MD, text=None):
    """{id: Entry(name, tier, element or None, kind or None)} read from ARTIFACTS.md's "The catalogue" section: every
    table row whose first cell is "Name (`id`)" (a description may follow it), its tier and kind from the `###` heading
    above it (CATALOGUE_TIERS; tier None under a heading that names none: an artifact the icon check knows by id and
    name only), its element from an "<element> damage" stat; and the bold "**Name** (`id`" signatures."""
    import re
    if text is None:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except FileNotFoundError:
            raise HerogenError("%s: not found (the artifact catalogue)" % util.rel(path))
    lines = text.splitlines()
    start = next((i for i, l in enumerate(lines) if re.match(r"## (\d+\. )?The catalogue\b", l)), None)
    if start is None:
        raise HerogenError("%s: no \"## The catalogue\" section" % util.rel(path))
    out = {}
    tier = kind = None
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        if line.startswith("### "):
            title = re.sub(r"^### [\d.]*\s*", "", line).lower()
            tier, kind = next(((t, k) for word, t, k in CATALOGUE_TIERS if title.startswith(word)), (None, None))
            continue
        m = re.match(r"\| ([^|`]+?) \(`([a-z0-9_]+)`\)[^|]*\|", line)
        if m:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            stats = cells[2] if len(cells) > 2 else ""
            el = re.match(r"(%s) damage" % "|".join(ELEMENT_WORDS), stats)
            out.setdefault(m.group(2), Entry(m.group(1).strip(), tier, el.group(1) if el else None, kind))
            continue
        if tier == "signature":
            for name, aid in re.findall(r"\*\*([^*]+)\*\* \(`([a-z0-9_]+)`", line):
                out.setdefault(aid, Entry(name, tier, None, kind))
    if not out:
        raise HerogenError("%s: the catalogue section lists no artifact" % util.rel(path))
    return out


def material_colours(icon_id, spec, shapes, materials):
    """{material key: the most colours it paints in one frame (of any growth state)}."""
    worst = {}
    n = len(PULSE_FRAMES) if spec["tier"] in PULSING else 1
    for f, state in [(f, st) for st in growth_states(spec) for f in range(n)]:
        out, mat = _paint(icon_id, spec, shapes, materials, f, state)
        seen = {}
        for y in range(SIZE):
            for x in range(SIZE):
                if mat[y, x] is not None:
                    seen.setdefault(mat[y, x], set()).add(tuple(int(v) for v in out[y, x, :3]))
        for k, v in seen.items():
            worst[k] = max(worst.get(k, 0), len(v))
    return worst


def check_image(name, png, meta, spec):
    """Problems with one written texture (bytes) and its .mcmeta (bytes or None): the vanilla item rules."""
    import io
    import json
    from PIL import Image
    out = []
    try:
        im = Image.open(io.BytesIO(png))
        im.load()
    except Exception as e:                                   # noqa: BLE001 - any decoding failure is the problem
        return ["%s.png: not a readable PNG (%s)" % (name, e)]
    if im.mode != "RGBA":
        out.append("%s.png: mode %s, not RGBA" % (name, im.mode))
        im = im.convert("RGBA")
    a = np.array(im)
    pulsing = spec is not None and spec["tier"] in PULSING
    want = (SIZE * len(PULSE_FRAMES), SIZE) if pulsing else (SIZE, SIZE)
    if a.shape[:2] != want:
        out.append("%s.png: %dx%d, not %dx%d%s" % (name, a.shape[1], a.shape[0], want[1], want[0],
                                                   " (four 16x16 frames)" if pulsing else ""))
        return out
    alpha = a[:, :, 3]
    if not np.isin(alpha, (0, 255)).all():
        out.append("%s.png: %d semi-transparent pixels (vanilla items use alpha 0 or 255)"
                   % (name, int((~np.isin(alpha, (0, 255))).sum())))
    frames_a = [alpha[i * SIZE:(i + 1) * SIZE] for i in range(a.shape[0] // SIZE)]
    for i, fa in enumerate(frames_a):
        n = int((fa > 0).sum())
        if n < 20:
            out.append("%s.png: frame %d has %d opaque pixels (empty?)" % (name, i, n))
    if any(not np.array_equal(frames_a[0], fa) for fa in frames_a[1:]):
        out.append("%s.png: the frames' alpha differs (an interpolated pulse must keep the silhouette)" % name)
    if pulsing:
        if meta is None:
            out.append("%s.png.mcmeta: missing (a pulsing icon needs its animation file)" % name)
        else:
            try:
                anim = json.loads(meta.decode("utf-8")).get("animation", {})
            except ValueError as e:
                anim = {}
                out.append("%s.png.mcmeta: not JSON (%s)" % (name, e))
            if anim.get("frametime") != PULSE_FRAMETIME or anim.get("interpolate") is not True:
                out.append("%s.png.mcmeta: animation %r, want frametime %d and interpolate" % (name, anim,
                                                                                               PULSE_FRAMETIME))
    elif meta is not None:
        out.append("%s.png.mcmeta: a still icon has no animation file" % name)
    return out


def problems(src_dir=SRC_DIR, out_dir=OUT_DIR, catalogue_path=ARTIFACTS_MD, files=None):
    """(errors, notes) of the icon set: the specs (tier rules, materials, gems, growth states), the catalogue (every
    artifact of ARTIFACTS.md's catalogue section, the sigil and the two tokens have an icon with the catalogue's name
    and tier, an element focus wears its element's gem, exactly the growing artifacts have four growth states; a
    `variant_of` spec is a second look of one of them), the colours per material, the written files (vanilla item
    rules), and the committed files against a fresh build. `files`: the committed {path: bytes} to check (default:
    read out_dir)."""
    errors, notes = [], []
    shapes, materials, icons = load(src_dir)
    for name, shape in sorted(shapes.items()):
        try:
            check_shape(name, shape)
        except HerogenError as e:
            errors.append(str(e))
    used = {s.get("shape") for s in icons.values()}
    for name in sorted(set(shapes) - used):
        notes.append("shape %s is used by no icon" % name)
    for icon_id, spec in sorted(icons.items()):
        errors += check_spec(icon_id, spec, shapes, materials)
    owners = texture_owners(icons)
    for icon_id, spec in sorted(icons.items()):
        for name, state in textures(icon_id, spec)[1:]:
            if name in icons:
                errors.append("%s: its growth state %d's texture %s.png is also the texture of the icon %s"
                              % (icon_id, state, name, name))
    if errors:
        return errors, notes
    cat = catalogue(catalogue_path)
    want = dict((k, (v[0], v[1])) for k, v in cat.items())
    want.update(EXTRA_ITEMS)
    later = []
    for aid in sorted(set(want) - set(icons)):
        if want[aid][1] is None:
            later.append(aid)
            continue
        errors.append("%s (%s): no icon: add a spec to herosrc/artifacts/icons.json (and a shape to shapes.json if "
                      "none fits), then run herogen icons" % (aid, want[aid][0]))
    if later:
        notes.append("%d artifacts listed under catalogue headings that name none of the five tiers have no icon yet "
                     "(a spec each in herosrc/artifacts/icons.json, docs/heroes-v2/notes/artgen.md \"R6 and B5\"): %s"
                     % (len(later), ", ".join(later)))
    for aid in sorted(set(icons) - set(want)):
        base = icons[aid].get("variant_of")
        if base is None:
            errors.append("%s: an icon for an id that is neither in the catalogue (ARTIFACTS.md 7) nor the sigil or a "
                          "token (a second look of an item names it in `variant_of`)" % aid)
        elif base not in want or not aid.startswith(base + "_"):
            errors.append("%s: `variant_of` %r must be a catalogue item, the sigil or a token, and the id must start "
                          "with it and an underscore" % (aid, base))
    for aid in sorted(set(icons) & set(want)):
        name, tier = want[aid]
        spec = icons[aid]
        if spec.get("variant_of"):
            errors.append("%s: an item's own icon has no `variant_of`" % aid)
        if spec.get("name") != name:
            errors.append("%s: named %r, the catalogue says %r" % (aid, spec.get("name"), name))
        if tier is None:
            notes.append("%s: listed under a catalogue heading that names no tier; its icon's tier (%s) is not "
                         "checked" % (aid, spec["tier"]))
        elif spec["tier"] != tier:
            errors.append("%s: tier %s, the catalogue says %s" % (aid, spec["tier"], tier))
        entry = cat.get(aid, Entry(name, tier))
        if entry.element and spec.get("gem") != entry.element:
            errors.append("%s: a %s focus with a %s gem" % (aid, entry.element, spec.get("gem")))
        if entry.kind == "growing" and not spec.get("growth"):
            errors.append("%s: a growing artifact has four icon states (ARTIFACTS.md 1.2): give its spec "
                          "\"growth\": true and its shape stage parts" % aid)
        if spec.get("growth") and entry.kind != "growing" and tier is not None:
            errors.append("%s: growth states, and the catalogue does not list it as a growing artifact" % aid)
    for icon_id, spec in sorted(icons.items()):
        for key, n in sorted(material_colours(icon_id, spec, shapes, materials).items()):
            if n > MAX_MATERIAL_COLOURS:
                errors.append("%s: %s paints %d colours (at most %d)" % (icon_id, key, n, MAX_MATERIAL_COLOURS))
    fresh = build_files(src_dir, out_dir)
    if files is None:
        files = {}
        if os.path.isdir(out_dir):
            for fn in sorted(os.listdir(out_dir)):
                with open(os.path.join(out_dir, fn), "rb") as f:
                    files[os.path.join(out_dir, fn)] = f.read()
    for path in sorted(fresh):
        if path not in files:
            errors.append("%s: missing (run herogen icons)" % util.rel(path))
        elif files[path] != fresh[path]:
            errors.append("%s: differs from a fresh build (run herogen icons)" % util.rel(path))
    for path in sorted(set(files) - set(fresh)):
        notes.append("%s: not made by herogen icons" % util.rel(path))
    for path in sorted(p for p in files if p.endswith(".png") and p in fresh):
        name = os.path.basename(path)[:-4]
        owner = owners.get(name)
        errors += check_image(name, files[path], files.get(path + ".mcmeta"), icons[owner[0]] if owner else None)
    return errors, notes


def stale_files(files, out_dir=OUT_DIR):
    """Files in out_dir that a fresh build (`files`) does not make: an old icon's texture, or another item's."""
    if not os.path.isdir(out_dir):
        return []
    return [os.path.join(out_dir, fn) for fn in sorted(os.listdir(out_dir)) if os.path.join(out_dir, fn) not in files]


def write(files):
    for path, data in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
