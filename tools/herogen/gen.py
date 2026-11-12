"""Part generators (style pass 2): many rotated cubes from a few numbers.

The references build hair from dozens of thin strands fanned and tilted at different depths, skirts and sleeves
from flared panels, frills from rings of small slabs, ears from stacked slabs and big tails from overlapping boxes
on a bone chain (docs/heroes-v2/notes/reference-study.md 5, reference-survey.md 5-6). Writing each of those cubes by
hand would make 250-cube heroes unmaintainable, so a part can carry generators next to its plain `cubes`:

`strands` (a list of groups): a row of boxes, each hanging from an anchor point (or standing on it with "dir": "up"),
  rotated about the anchor so its far end swings outward ("flare", degrees), sideways ("fan", spread from -fan on the
  first to +fan on the last, so a positive fan parts a fringe and a negative one draws it together; "zigzag",
  alternating; "lean", the same for every strand: a side-swept fringe), turned to face the path's outward direction.
  - "path": "line" from "from" to "to" (points), with "out" the outward direction (default [0, 0, -1], the front);
    or "arc" around "center" [x, z] with "radius" [rx, rz] at height "y", over "angles" [a0, a1] in degrees
    (0 = the front, -z; 90 = the hero's left, +x; 180 = the back; -90 = the hero's right), outward = away from the
    centre.
  - "count", "width", "depth", "length" (a number or a list, cycled; expressions allowed), "jitter" (a deterministic
    +-length variation, in steps of 0.25 px), "layers" (outward offsets, cycled: depth layering), "drop" (anchor
    height offsets, cycled), "tone" (cycled), "flare" and "width" may be lists too (cycled).
  - "clear_face" (default true) shortens any strand that would reach in front of the eyes (the clear face rule), and
    every strand is shortened to stay above the ground (a long tress on a chibi).
  - "bones_by_angle" {bone: [a0, a1]} (arc only): each strand goes to the bone whose sector holds its angle (skirt
    panels on skirt_f, skirt_b, skirt_l, skirt_r).
  - cube fields shared by the group: bone, slot, material (default hair), taper, cutout, trim, shade, density,
    inflate, fine (default true: strands are level-of-detail), mirror (a mirrored copy, for side groups), name.
`chains` (a list): bones along a polyline and a box per segment aligned with it, with optional extra boxes rolled
  about the segment axis ("extra": [{"roll": 30, "scale": 0.85}]) to round the cross-section (big tails), and the
  last segment optionally tapered ("taper_last", "cutout_last", "slot_last").
  - "bones" [names], "parent", "points" (one more than bones), "sections" [[w, d]] per segment, "overlap" (each box
    reaches this far past both ends of its segment), "slots" (cycled), material, fine_extra (extra boxes on fine_).
`groups` (a list): {"rotation", "pivot", "cubes": [...]}: plain cubes that share one rotation about one pivot (an
  ear's stacked slabs tilted as one piece).
Every generator may carry "when": <expression>; it is skipped when that is 0 (an optional ahoge).
All coordinates are part coordinates (the part's offset is added, so head parts stay head-local) and expressions
over the part's params and the base dims.
"""
import math

import numpy as np

from rot import about, align, file_angles, file_matrix, rotate_points, rx, ry, rz
from util import HerogenError, hash_unit, seed_of

GROUND = 0.25          # strands stay this far above the ground


def _wanted(ctx, g):
    """A generator with "when": <expression> is skipped when the expression is 0 (an optional ahoge, a second tail)."""
    return "when" not in g or abs(float(ctx.ev(g["when"]))) > 1e-9


def _cycle(v, i):
    if isinstance(v, list):
        return v[i % len(v)]
    return v


def _num_list(ctx, v):
    """A scalar or a list of scalars (each an expression)."""
    if isinstance(v, list):
        return [float(ctx.ev(x)) for x in v]
    return float(ctx.ev(v))


def _anchors(ctx, g):
    """[(anchor point, outward unit vector (x, z), angle or None)] along the group's path."""
    n = int(ctx.ev(g.get("count", 1)))
    if n < 1:
        return []
    path = g.get("path", "line")
    out = []
    if path == "line":
        a = np.array(ctx.at(g["from"]), dtype=float)
        b = np.array(ctx.at(g["to"]), dtype=float)
        o = np.array(ctx.ev(g.get("out", [0, 0, -1])), dtype=float)
        o = o / (np.linalg.norm(o) or 1.0)
        for i in range(n):
            t = 0.5 if n == 1 else i / float(n - 1)
            out.append((a + (b - a) * t, (o[0], o[2]), None))
    elif path == "arc":
        cx, cz = ctx.ev(g.get("center", [0, 0]))
        r = ctx.ev(g["radius"])
        rxv, rzv = (r, r) if not isinstance(r, list) else r
        y = ctx.ev(g["y"])
        a0, a1 = ctx.ev(g["angles"])
        for i in range(n):
            t = 0.5 if n == 1 else i / float(n - 1)
            ang = a0 + (a1 - a0) * t
            s, c = math.sin(math.radians(ang)), math.cos(math.radians(ang))
            p = np.array([cx + rxv * s, y, cz - rzv * c], dtype=float) + np.array(ctx.offset)
            # the ellipse normal at that point
            nx, nz = s / max(rxv, 1e-6), -c / max(rzv, 1e-6)
            ln = math.hypot(nx, nz) or 1.0
            out.append((p, (nx / ln, nz / ln), ang))
    else:
        raise HerogenError("%s: strands path %r is unknown (line, arc)" % (ctx.where, path))
    return out


def strand_matrix(out_xz, flare, side, up=False):
    """File-space rotation of a strand: yaw so its depth axis faces `out_xz`, tilt its far end outward by `flare`
    degrees and sideways (toward its local +x, the path's tangent) by `side` degrees."""
    nx, nz = out_xz
    beta = math.degrees(math.atan2(-nx, -nz))
    if up:
        return ry(beta) @ rz(-side) @ rx(-flare)
    return ry(beta) @ rz(side) @ rx(flare)


def _clip(ctx, origin, size, angles, pivot, up, clear_face):
    """Shorten a strand (from its free end) until it stays above the ground and, for clear_face, out of the zone
    in front of the eyes. Returns the new (origin, size) or None when nothing is left."""
    model = ctx.model
    face = model.rig["face"]
    hy = model.env["head_y"]
    front = model.rig["head"]["front_z"]
    lim = hy + face["clear_below_y"]
    half = face["clear_half_width"]
    o, s = list(origin), list(size)
    for _ in range(200):
        pts = rotate_points(_corners(o, s), angles, pivot)
        lowest = min(p[1] for p in pts)
        bad = lowest < GROUND
        if clear_face and not bad:
            zs = [p[2] for p in pts]
            ys = [p[1] for p in pts]
            xs = [p[0] for p in pts]
            if min(zs) < front - 1e-6 and min(ys) < lim - 1e-6 and max(ys) > hy + 1e-6 \
                    and min(xs) < half - 1e-6 and max(xs) > -half + 1e-6:
                bad = True
        if not bad:
            return tuple(o), tuple(s)
        if s[1] <= 0.5 + 1e-9:
            return None
        s[1] -= 0.25
        if not up:
            o[1] += 0.25
    return None


def _corners(o, s):
    return [(o[0] + a, o[1] + b, o[2] + c) for a in (0, s[0]) for b in (0, s[1]) for c in (0, s[2])]


def strands(ctx, add_cube):
    """Expand the part's `strands` groups into cube specs and pass them to add_cube(spec, origin, size, rotation,
    pivot, bone, name_suffix, mirror_of)."""
    seed = seed_of(ctx.pid) ^ int(ctx.entry.get("seed", 0))
    for gi, g in enumerate(ctx.part.get("strands", [])):
        if not _wanted(ctx, g):
            continue
        anchors = _anchors(ctx, g)
        n = len(anchors)
        up = g.get("dir", "down") == "up"
        width = _num_list(ctx, g.get("width", 1))
        depth = _num_list(ctx, g.get("depth", 1))
        length = _num_list(ctx, g.get("length", 3))
        flare = _num_list(ctx, g.get("flare", 0))
        fan = float(ctx.ev(g.get("fan", 0)))
        zig = float(ctx.ev(g.get("zigzag", 0)))
        lean = float(ctx.ev(g.get("lean", 0)))
        jitter = float(ctx.ev(g.get("jitter", 0)))
        layers = _num_list(ctx, g.get("layers", 0))
        drop = _num_list(ctx, g.get("drop", 0))
        tones = g.get("tone", 0)
        sector = g.get("bones_by_angle")
        clear_face = bool(g.get("clear_face", True))
        base = {k: v for k, v in g.items() if k in ("slot", "material", "taper", "cutout", "trim", "shade",
                                                     "density", "inflate", "glow", "faces")}
        base.setdefault("material", "hair")
        for i, (p, o_xz, ang) in enumerate(anchors):
            w, d = _cycle(width, i), _cycle(depth, i)
            L = _cycle(length, i)
            if jitter:
                L += math.floor((hash_unit(seed, gi, i) * 2 - 1) * jitter * 4 + 0.5) / 4.0
            L = max(0.5, L)
            lay = _cycle(layers, i)
            p = np.array(p, dtype=float) + np.array([o_xz[0] * lay, _cycle(drop, i), o_xz[1] * lay])
            t = 0.0 if n == 1 else (i / float(n - 1)) * 2 - 1
            side = lean + fan * t + (zig if i % 2 == 0 else -zig)
            m = strand_matrix(o_xz, _cycle(flare, i), side, up)
            angles = file_angles(m)
            origin = (p[0] - w / 2.0, p[1] if up else p[1] - L, p[2] - d / 2.0)
            size = (w, L, d)
            clipped = _clip(ctx, origin, size, angles, tuple(p), up, clear_face)
            if clipped is None:
                continue
            origin, size = clipped
            bone = g["bone"]
            if sector and ang is not None:
                a = ((ang + 180.0) % 360.0) - 180.0
                for bname, (b0, b1) in sector.items():
                    if b0 - 1e-6 <= a <= b1 + 1e-6 or b0 - 1e-6 <= a + 360 <= b1 + 1e-6:
                        bone = bname
                        break
            spec = dict(base, tone=int(_cycle(tones, i)), fine=g.get("fine", True))
            name = "%s#%s%d" % (ctx.pid, g.get("name", "s%d_" % gi), i)
            add_cube(spec, origin, size, angles, tuple(p), bone, name, bool(g.get("mirror")))


def groups(ctx):
    """Cube specs of the part's `groups`, each cube with the group's rotation and pivot."""
    out = []
    for gi, g in enumerate(ctx.part.get("groups", [])):
        if not _wanted(ctx, g):
            continue
        for c in g.get("cubes", []):
            if "rotation" in c:
                raise HerogenError("%s: a cube in a rotated group has its own rotation" % ctx.where)
            c = dict(c)
            c["rotation"] = g["rotation"]
            c["pivot"] = g["pivot"]
            out.append(c)
    return out


def chains(ctx, declare, add_cube):
    """Bones along a polyline and boxes aligned with each segment (big tails, ponytails)."""
    for ci, ch in enumerate(ctx.part.get("chains", [])):
        if not _wanted(ctx, ch):
            continue
        names = ch["bones"]
        pts = [np.array(ctx.at(p), dtype=float) for p in ch["points"]]
        if len(pts) != len(names) + 1:
            raise HerogenError("%s: chain %d needs one more point than bones" % (ctx.where, ci))
        sections = [ctx.ev(s) for s in ch["sections"]]
        overlap = float(ctx.ev(ch.get("overlap", 0.5)))
        slots = ch.get("slots", "main")
        parent = ch.get("parent", "body")
        for i, name in enumerate(names):
            declare(name, parent if i == 0 else names[i - 1], tuple(pts[i]))
        extra = ch.get("extra", [])
        for i, name in enumerate(names):
            a, b = pts[i], pts[i + 1]
            u = b - a
            ln = float(np.linalg.norm(u))
            if ln < 1e-6:
                raise HerogenError("%s: chain %d has a zero-length segment" % (ctx.where, ci))
            u = u / ln
            w, d = _cycle(sections, i)
            L = ln + 2 * overlap
            last = i == len(names) - 1
            base_m = align((0.0, -1.0, 0.0), u)
            boxes = [(1.0, 0.0, False)] + [(float(e.get("scale", 0.85)), float(e.get("roll", 0)), True)
                                           for e in extra]
            for k, (sc, roll, is_extra) in enumerate(boxes):
                if last and is_extra and not ch.get("extra_last", False):
                    continue
                m = about(u, roll) @ base_m if roll else base_m
                ww, dd = w * sc, d * sc
                origin = (a[0] - ww / 2.0, a[1] - L + overlap, a[2] - dd / 2.0)
                spec = {"slot": ch.get("slot_last", _cycle(slots, i)) if last else _cycle(slots, i),
                        "material": ch.get("material", "fur"), "fine": bool(is_extra and ch.get("fine_extra")),
                        "tone": int(_cycle(ch.get("tone", 0), i)), "shade": "flat", "outline": last}
                if last and "taper_last" in ch:
                    spec["taper"] = ch["taper_last"]
                if last and "cutout_last" in ch:
                    spec["cutout"] = ch["cutout_last"]
                add_cube(spec, origin, (ww, L, dd), file_angles(m), tuple(a), name,
                         "%s#%s%d_%d" % (ctx.pid, ch.get("name", "c%d_" % ci), i, k), False)


def check_matrix(angles):
    """For tests: the file-space matrix of file angles."""
    return file_matrix(angles)
