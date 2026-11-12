"""Asset checks on built files (the geometry and texture half of design 14.2; G7 adds animations, kit frames,
distinctness and the Java HeroAssetsTest). Every function returns a list of problem strings (empty = fine).

Checked here: geometry format_version; texture size matches the geometry and is an allowed size; bone and cube
budgets; bone names; parents before children; required bones; whole-texel UVs; UV islands inside the texture and not
overlapping (face planes on their fixed regions); the alpha holes under the eyes and mouth planes (face.head_front_holes)
and, where a plane edge covers painted head front, identical texels; every eye, mouth and cheek frame present and
distinct; the eyes mirror (frame 9 may raise one brow); nothing in front of the face below y 30 within |x| < 3.5.
"""
import math
import re

import numpy as np

from face import frame_rect, head_front_holes
from render.ortho import rotation_matrix
from rig import load_rig, required_bones

NAME = re.compile(r"^[a-z][a-z0-9_]*$")


def _whole(v):
    return float(v) == math.floor(float(v))


def check_geometry(geo, tex, rig=None):
    rig = rig or load_rig()
    out = []
    budgets = rig["budgets"]
    th, tw = tex.shape[:2]
    if (geo.tex_w, geo.tex_h) != (tw, th):
        out.append("texture is %dx%d but the geometry says %dx%d" % (tw, th, geo.tex_w, geo.tex_h))
    if [geo.tex_w, geo.tex_h] not in budgets["textures"]:
        out.append("texture size %dx%d is not allowed (%s)" % (geo.tex_w, geo.tex_h,
                                                              ", ".join("%dx%d" % tuple(t) for t in budgets["textures"])))
    if len(geo.order) > budgets["bones"]:
        out.append("%d bones (budget %d)" % (len(geo.order), budgets["bones"]))
    if len(geo.cubes) > budgets["cubes"]:
        out.append("%d cubes (budget %d)" % (len(geo.cubes), budgets["cubes"]))
    seen = set()
    for name in geo.order:
        if not NAME.match(name):
            out.append("bone name %r is not lower_snake_case" % name)
        parent = geo.parent(name)
        if parent is not None and parent not in seen:
            out.append("bone %s comes before its parent %s" % (name, parent))
        seen.add(name)
    if len(seen) != len(geo.order):
        out.append("duplicate bone names")
    for req in required_bones(rig):
        if req not in seen:
            out.append("required bone %s is missing" % req)
    out += _check_uvs(geo, rig)
    out += _check_clear_face(geo, rig)
    return out


def _check_uvs(geo, rig):
    out = []
    tpl = rig["uv_template"]
    planes = rig["face"]["planes"]
    expected = {
        "eyes": (tpl["eyes"][0], tpl["eyes"][1]) + tuple(planes["eyes"]["frame"]),
        "mouth": (tpl["mouth"][0], tpl["mouth"][1]) + tuple(planes["mouth"]["frame"]),
        "cheeks": (tpl["cheeks"][0], tpl["cheeks"][1]) + tuple(planes["cheeks"]["frame"]),
    }
    occ = np.zeros((geo.tex_h, geo.tex_w), dtype=np.int32)
    owner = {}
    for n, c in enumerate(geo.cubes):
        for face, (uv, size) in sorted(c.raw_uv.items()):
            if not all(_whole(v) for v in tuple(uv) + tuple(size)):
                out.append("bone %s: %s face has a fractional UV %s %s" % (c.bone, face, list(uv), list(size)))
                continue
            u, v, w, h = (int(a) for a in c.uv[face])
            if w <= 0 or h <= 0:
                out.append("bone %s: %s face has an empty or mirrored UV rectangle" % (c.bone, face))
                continue
            if u < 0 or v < 0 or u + w > geo.tex_w or v + h > geo.tex_h:
                out.append("bone %s: %s face UV (%d, %d, %d, %d) leaves the texture" % (c.bone, face, u, v, w, h))
                continue
            if c.bone in expected:
                if face != "north" or (u, v, w, h) != expected[c.bone]:
                    out.append("face plane %s is not on its fixed region %s" % (c.bone, expected[c.bone]))
                continue
            if c.bone == "head" and face == "north":
                if (u, v, w, h) != tuple(tpl["head_front"]):
                    out.append("head front is not on its fixed region %s" % (tpl["head_front"],))
                continue
            region = occ[v:v + h, u:u + w]
            if region.any():
                other = owner.get(int(region[region > 0][0]), "?")
                out.append("bone %s: %s face UV overlaps %s" % (c.bone, face, other))
            key = len(owner) + 1
            owner[key] = "%s/%s" % (c.bone, face)
            region[region == 0] = key
    for key in ("eyes", "mouth", "cheeks"):
        if not any(c.bone == key for c in geo.cubes):
            out.append("face plane %s has no cube" % key)
    return out


def _cube_points(c):
    x, y, z = c.origin
    sx, sy, sz = c.size
    pts = [(x + a * sx, y + b * sy, z + d * sz) for a in (0, 1) for b in (0, 1) for d in (0, 1)]
    if c.rotation is None:
        return pts
    m = rotation_matrix(c.rotation[0], c.rotation[1], c.rotation[2])
    pv = np.array(c.pivot)
    return [tuple(pv + m @ (np.array(p) - pv)) for p in pts]


def _check_clear_face(geo, rig):
    """Nothing but the face planes may sit in front of the face below y 30 within |x| < 3.5 (4.6)."""
    out = []
    lim_y = rig["face"]["clear_below_y"]
    half = rig["face"]["clear_half_width"]
    for c in geo.cubes:
        if c.bone in ("eyes", "mouth", "cheeks", "head"):
            continue
        pts = _cube_points(c)
        xs, ys, zs = zip(*pts)
        if min(zs) < -4 - 1e-6 and min(ys) < lim_y - 1e-6 and max(ys) > 24 + 1e-6 \
                and min(xs) < half - 1e-6 and max(xs) > -half + 1e-6:
            out.append("bone %s: a cube sits in front of the face below y %g within |x| < %g" % (c.bone, lim_y, half))
    return out


def check_face_texture(tex, rig=None):
    rig = rig or load_rig()
    out = []
    tpl = rig["uv_template"]
    hu, hv, hw, hh = tpl["head_front"]
    head = tex[hv:hv + hh, hu:hu + hw, 3]
    holes = head_front_holes()
    if (head[holes] != 0).any():
        out.append("head front is not transparent under the eyes and mouth planes")
    if (head[~holes] != 255).any():
        out.append("head front has transparent texels outside the plane holes")
    # where a plane covers painted head-front texels (the mouth's edge columns), every frame must match them exactly,
    # or the two coincident surfaces would flicker between different colours
    for plane, (r0, c0) in (("eyes", (2, 0)), ("mouth", (12, 4))):
        n = rig["face"]["planes"][plane]["frames"]
        for k in range(n):
            u, v, w, h = frame_rect(rig, plane, k)
            f = tex[v:v + h, u:u + w]
            under = tex[hv + r0:hv + r0 + h, hu + c0:hu + c0 + w]
            covered = under[:, :, 3] == 255
            if covered.any() and not np.array_equal(f[covered], under[covered]):
                out.append("%s frame %d differs from the head front where they overlap (edge texels must be the "
                           "same skin)" % (plane, k))
    for plane, n, opaque in (("eyes", 12, True), ("mouth", 8, True), ("cheeks", 4, False)):
        frames = []
        for k in range(n):
            u, v, w, h = frame_rect(rig, plane, k)
            f = tex[v:v + h, u:u + w]
            frames.append(f)
            if opaque and (f[:, :, 3] != 255).any():
                out.append("%s frame %d is not fully opaque (missing frame?)" % (plane, k))
            if not opaque and k > 0 and not f[:, :, 3].any():
                out.append("%s frame %d is empty" % (plane, k))
        if not opaque and frames[0][:, :, 3].any():
            out.append("cheeks frame 0 must be transparent")
        for i in range(n):
            for j in range(i + 1, n):
                if np.array_equal(frames[i], frames[j]):
                    out.append("%s frames %d and %d are identical" % (plane, i, j))
        if plane == "eyes":
            for k, f in enumerate(frames):
                rows = slice(2, 8) if k == 9 else slice(0, 8)
                if not np.array_equal(f[rows], f[rows, ::-1]):
                    out.append("eyes frame %d: the eyes do not mirror" % k)
            skin = frames[0][7, 0].copy()
            for k, f in enumerate(frames):
                if (f[:, :, :3] == skin[:3]).all():
                    out.append("eyes frame %d is blank skin (missing frame?)" % k)
    return out


def check_built(geo, tex):
    return check_geometry(geo, tex) + check_face_texture(tex)
