"""Colour maths and palette ramps (design 13.4).

Every palette slot becomes a 5-tone ramp in OKLCH, tones -2..+2:
- lightness changes by 0.08 per step;
- shadows shift hue toward blue-violet by up to 12 degrees (6 per step) and lose chroma; skin shadows shift toward
  red instead and keep their chroma;
- highlights shift toward yellow by up to 8 degrees (4 per step);
- tone 0 is the authored colour, exactly.
Hair (and the fur and ears painted in the hair colour) gets its own ramp, `kind="hair"` (art direction pass 2): the
owner's references shade hair strongly, gold to orange, with a pale lit root, so hair steps are larger, shadows keep
their chroma and lean warm (orange-red) for warm hair or blue-violet for cool hair, and dark hair gets a wider, cooler
highlight step so a black or navy mass still shows its strands (anime blue sheen).
Lightness is clamped so no ramp reaches pure black or pure white (those are kept for eye highlights).
"""
import math

from util import HerogenError

SHADOW_HUE = 285.0       # OKLCH hue of blue-violet
SKIN_SHADOW_HUE = 28.0   # OKLCH hue of a warm red
HIGHLIGHT_HUE = 100.0    # OKLCH hue of yellow
STEP_L = 0.08
HAIR_WARM_HUE = 35.0     # OKLCH hue of a warm orange-red: where gold, brown, ginger and red hair shade toward
HAIR_SHEEN_HUE = 255.0   # OKLCH hue of a cool blue: the sheen of dark (black, navy) hair
HAIR_DOWN, HAIR_UP = 0.085, 0.095
L_MIN, L_MAX = 0.14, 0.965

HAIR_SLOTS = ("hair", "hair_accent")
SLOTS = ["skin", "skin_shade", "hair", "hair_accent", "eyes", "primary", "secondary", "accent", "trim", "metal",
         "leather", "glow"]

DEFAULTS = {
    "skin": "#F3D5C0",
    "hair": "#4A3B36",
    "eyes": "#5B7BC8",
    "primary": "#5C6B8A",
    "secondary": "#E6E1D6",
    "accent": "#2E3140",
    "metal": "#C9CED6",
    "leather": "#6B4A33",
    "glow": "#FFE38A",
}

# Fixed slots every hero has (not authored). `sash` is painted greyscale and tinted by the renderer with the faction
# colour (design 4.7), so its tone 0 is a light grey that multiplies well.
FIXED = {
    "sash": "#D2D2D2",
    "sclera": "#F6F2EE",
    "highlight": "#FFFFFF",
    "mouth_dark": "#6E2E3A",
    "tongue": "#D9737A",
    "teeth": "#F5F1EA",
    "blush": "#EE7E8C",
    "tear": "#A6D8F2",
}


# ------------------------------------------------------------------ conversions

def parse_hex(h):
    if isinstance(h, (list, tuple)) and len(h) == 3:
        return tuple(int(v) for v in h)
    if not isinstance(h, str) or len(h) != 7 or h[0] != "#":
        raise HerogenError("colour %r is not #RRGGBB" % (h,))
    try:
        return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
    except ValueError:
        raise HerogenError("colour %r is not #RRGGBB" % (h,))


def to_hex(rgb):
    return "#%02X%02X%02X" % tuple(rgb)


def _to_lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _from_lin(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return c * 255.0


def _cbrt(x):
    return math.copysign(abs(x) ** (1.0 / 3.0), x)


def rgb_to_oklab(rgb):
    r, g, b = (_to_lin(c) for c in rgb)
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_, m_, s_ = _cbrt(l), _cbrt(m), _cbrt(s)
    return (0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
            1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
            0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_)


def oklab_to_lin(lab):
    L, a, b = lab
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)


def rgb_to_oklch(rgb):
    L, a, b = rgb_to_oklab(rgb)
    C = math.hypot(a, b)
    h = math.degrees(math.atan2(b, a)) % 360.0
    return L, C, h


def _in_gamut(lin, eps=1e-7):
    return all(-eps <= c <= 1 + eps for c in lin)


def oklch_to_rgb(L, C, h):
    """OKLCH to 8-bit sRGB; out-of-gamut colours keep L and h and lose chroma (binary search, 24 steps)."""
    hr = math.radians(h)

    def lin_of(c):
        return oklab_to_lin((L, c * math.cos(hr), c * math.sin(hr)))

    lin = lin_of(C)
    if not _in_gamut(lin):
        lo, hi = 0.0, C
        for _ in range(24):
            mid = (lo + hi) / 2
            if _in_gamut(lin_of(mid)):
                lo = mid
            else:
                hi = mid
        lin = lin_of(lo)
    return tuple(int(math.floor(min(255.0, max(0.0, _from_lin(min(1.0, max(0.0, c))))) + 0.5)) for c in lin)


def rgb_to_lab(rgb):
    """CIELab (D65), for CIE76 delta-E."""
    r, g, b = (_to_lin(c) for c in rgb)
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / 1.0
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116

    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def delta_e76(c1, c2):
    a, b = rgb_to_lab(parse_hex(c1)), rgb_to_lab(parse_hex(c2))
    return math.sqrt(sum((p - q) ** 2 for p, q in zip(a, b)))


def luma(rgb):
    """Greyscale value 0..1 as Pillow's "L" conversion computes it (ITU-R 601)."""
    r, g, b = parse_hex(rgb)
    return (299 * r + 587 * g + 114 * b) / 1000.0 / 255.0


def greyscale_contrast(c1, c2):
    return abs(luma(c1) - luma(c2))


def mix(c1, c2, t):
    a, b = parse_hex(c1), parse_hex(c2)
    return tuple(int(math.floor(a[i] + (b[i] - a[i]) * t + 0.5)) for i in range(3))


# ------------------------------------------------------------------ ramps

def _shift_hue(h, target, amount):
    d = (target - h + 540.0) % 360.0 - 180.0
    if abs(d) <= amount:
        return target % 360.0
    return (h + math.copysign(amount, d)) % 360.0


def _hair_ramp(base):
    L, C, h = rgb_to_oklch(base)
    warm = h < 115.0 or h > 345.0
    dark = L < 0.42
    out = []
    for t in (-2, -1, 0, 1, 2):
        if t == 0:
            out.append(base)
            continue
        if t < 0:
            Lt = max(L_MIN, L + (0.06 if dark else HAIR_DOWN) * t)
            ht = _shift_hue(h, HAIR_WARM_HUE if warm else SHADOW_HUE, 7.0 * -t)
            Ct = C * (1.0 + 0.06 * -t)
            if C < 0.02:
                ht, Ct = SHADOW_HUE, max(Ct, 0.008 * -t)
        else:
            Lt = min(L_MAX, L + (0.12 if dark else HAIR_UP) * t)
            if dark:
                # black and navy hair: a cool sheen, a little more chroma, so the lit strands read
                ht = _shift_hue(h, HAIR_SHEEN_HUE, 18.0 * t) if C >= 0.012 else HAIR_SHEEN_HUE
                Ct = max(C * (1.0 + 0.1 * t), 0.02 * t)
            else:
                ht = _shift_hue(h, HIGHLIGHT_HUE, 5.0 * t)
                Ct = C * (1.0 - 0.08 * t)
        out.append(oklch_to_rgb(Lt, Ct, ht))
    return out


def ramp(colour, kind="default"):
    """Five RGB tuples for tones -2..+2 (index tone + 2). `kind`: "default", "skin" or "hair"."""
    base = parse_hex(colour)
    if kind == "hair":
        return _hair_ramp(base)
    L, C, h = rgb_to_oklch(base)
    out = []
    for t in (-2, -1, 0, 1, 2):
        if t == 0:
            out.append(base)
            continue
        Lt = min(L_MAX, max(L_MIN, L + STEP_L * t))
        if t < 0:
            if kind == "skin":
                ht = _shift_hue(h, SKIN_SHADOW_HUE, 6.0 * -t)
                Ct = C * (1.0 + 0.04 * -t)
            else:
                ht = _shift_hue(h, SHADOW_HUE, 6.0 * -t)
                Ct = C * (1.0 - 0.12 * -t)
                if C < 0.02:            # greys get cool shadows instead of none
                    ht, Ct = SHADOW_HUE, max(Ct, 0.006 * -t)
        else:
            ht = _shift_hue(h, HIGHLIGHT_HUE, 4.0 * t)
            Ct = C * (1.0 - 0.05 * t)
        out.append(oklch_to_rgb(Lt, Ct, ht))
    return out


def resolve(art_palette, family="female"):
    """Palette slot name -> ramp. Derived slots are filled from authored ones."""
    pal = dict(DEFAULTS)
    for k, v in (art_palette or {}).items():
        if k not in SLOTS and k not in FIXED:
            raise HerogenError("palette slot %r is unknown (slots: %s)" % (k, ", ".join(SLOTS)))
        parse_hex(v)
        pal[k] = v
    pal.setdefault("trim", pal["accent"])
    if "hair_accent" not in pal:
        pal["hair_accent"] = to_hex(ramp(pal["hair"])[4])
    ramps = {}
    for k, v in pal.items():
        if k == "skin_shade":
            continue
        ramps[k] = ramp(v, "skin" if k == "skin" else ("hair" if k in HAIR_SLOTS else "default"))
    if "skin_shade" in pal:
        shade = parse_hex(pal["skin_shade"])
        ramps["skin"][1] = shade
        ramps["skin"][0] = ramp(pal["skin_shade"], "skin")[1]
    for k, v in FIXED.items():
        ramps.setdefault(k, ramp(v))
    ramps["_hex"] = {k: v for k, v in pal.items()}
    return ramps


def lash_colour(hair_hex):
    """Eye lines: a dark tone of the hair hue, never pure black."""
    L, C, h = rgb_to_oklch(parse_hex(hair_hex))
    return oklch_to_rgb(min(0.27, max(0.18, L - 0.3)), min(C, 0.05), h)


def brow_colour(hair_hex):
    L, C, h = rgb_to_oklch(parse_hex(hair_hex))
    return oklch_to_rgb(min(0.45, max(0.25, L - 0.18)), C * 0.9, _shift_hue(h, SHADOW_HUE, 6))


def check_palette(pal_hex):
    """Notes for an authored palette: main colour regions must differ (delta-E >= 10) and the two main colours must
    read in greyscale (luma difference >= 0.15). Returns a list of messages (empty when fine)."""
    notes = []
    main = [s for s in ("primary", "secondary", "accent") if s in pal_hex]
    for i in range(len(main)):
        for j in range(i + 1, len(main)):
            d = delta_e76(pal_hex[main[i]], pal_hex[main[j]])
            if d < 10:
                notes.append("palette: %s and %s differ by delta-E %.1f (< 10)" % (main[i], main[j], d))
    if "primary" in pal_hex and "secondary" in pal_hex:
        g = greyscale_contrast(pal_hex["primary"], pal_hex["secondary"])
        if g < 0.15:
            notes.append("palette: primary and secondary differ by %.0f%% in greyscale (< 15%%)" % (g * 100))
    for k, v in pal_hex.items():
        if parse_hex(v) in ((0, 0, 0), (255, 255, 255)):
            notes.append("palette: %s is pure %s" % (k, "black" if parse_hex(v) == (0, 0, 0) else "white"))
    return notes
