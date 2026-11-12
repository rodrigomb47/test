"""UV template and packer (design 13.2).

Every cube face gets its own rectangle of WHOLE texels: round(face size x density) with halves rounded up, at least 1
(a 2.5 px arm face uses 3 texels). A cube's faces are laid out as a vanilla skin net (top and bottom above; right
side, front, left side, back below), so seams stay adjacent and the texture reads like a Minecraft skin in an editor.
Islands are packed largest first (height, then width) at the first free spot in row-major order with a 1-texel gutter
around everything already placed. Base body cubes pack into the base region (0, 0, 64, 64); part cubes into the rest
of the texture outside the fixed template regions; the texture grows from 128x128 to 256x128 when parts need it.

Net orientation of each face rectangle (u to the right, v down), which is vanilla box UV:
  north (front): left edge = hero's right (-x), top = up      south (back): left edge = hero's left (+x)
  east (hero's right side, -x): left edge = back (+z)       west (hero's left side, +x): left edge = front (-z)
  up and down: left edge = hero's right (-x), bottom row = front (-z)
"""
import math

import numpy as np

from util import HerogenError

TEMPLATE_KEYS = ("eyes", "mouth", "cheeks", "head_front")


def texels(size, density=1):
    """Whole texels for a face edge: halves round up, minimum 1."""
    return max(1, int(math.floor(size * density + 0.5)))


def net(cube):
    """Island (w, h, {face: (du, dv, fw, fh)}) of a cube's non-fixed faces, cropped to the faces present."""
    sx, sy, sz = cube.size
    d = cube.density
    flat = cube.flat_axis
    faces = [f for f in cube.faces if f not in cube.fixed]
    if flat is not None:
        # a zero-thickness cube has one visible face (rendering is cull-free, two would z-fight)
        allowed = {0: ("east", "west"), 1: ("up", "down"), 2: ("north", "south")}[flat]
        faces = [f for f in faces if f in allowed][:1]
    W = texels(sx, d) if sx > 1e-9 else 0
    H = texels(sy, d) if sy > 1e-9 else 0
    D = texels(sz, d) if sz > 1e-9 else 0
    layout = {
        "up": (D, 0, W, D), "down": (D + W, 0, W, D),
        "east": (0, D, D, H), "north": (D, D, W, H), "west": (D + W, D, D, H), "south": (2 * D + W, D, W, H),
    }
    rects = {f: layout[f] for f in faces if layout[f][2] > 0 and layout[f][3] > 0}
    if not rects:
        return 0, 0, {}
    u0 = min(r[0] for r in rects.values())
    v0 = min(r[1] for r in rects.values())
    rects = {f: (r[0] - u0, r[1] - v0, r[2], r[3]) for f, r in rects.items()}
    w = max(r[0] + r[2] for r in rects.values())
    h = max(r[1] + r[3] for r in rects.values())
    return w, h, rects


class Packer:
    """First-fit packer on an occupancy grid; every placed island keeps a 1-texel gutter to everything else."""

    def __init__(self, width, height, reserved=(), area=None):
        self.w, self.h = width, height
        self.occ = np.zeros((height, width), dtype=bool)
        for (u, v, w, h) in reserved:
            self.occ[v:v + h, u:u + w] = True
        if area is not None:
            u, v, w, h = area
            mask = np.ones((height, width), dtype=bool)
            mask[v:v + h, u:u + w] = False
            self.occ |= mask

    def _dilated(self):
        o = self.occ
        d = o.copy()
        d[1:, :] |= o[:-1, :]
        d[:-1, :] |= o[1:, :]
        d[:, 1:] |= o[:, :-1]
        d[:, :-1] |= o[:, 1:]
        d[1:, 1:] |= o[:-1, :-1]
        d[1:, :-1] |= o[:-1, 1:]
        d[:-1, 1:] |= o[1:, :-1]
        d[:-1, :-1] |= o[1:, 1:]
        return d

    def place(self, w, h, rects=None):
        """Place an island of size w x h whose texels are `rects` [(du, dv, fw, fh)] (default: the whole box) at the
        first free position in row-major order; only its face rectangles are tested and marked, so small islands can
        nest in the empty corners of a skin net."""
        if w > self.w or h > self.h:
            return None
        rects = rects or [(0, 0, w, h)]
        d = self._dilated().astype(np.int32)
        sat = np.zeros((self.h + 1, self.w + 1), dtype=np.int32)
        sat[1:, 1:] = d.cumsum(0).cumsum(1)
        ny, nx = self.h - h + 1, self.w - w + 1
        ok = np.ones((ny, nx), dtype=bool)
        for (du, dv, fw, fh) in rects:
            win = sat[fh:, fw:] - sat[:-fh, fw:] - sat[fh:, :-fw] + sat[:-fh, :-fw]
            ok &= win[dv:dv + ny, du:du + nx] == 0
        free = np.argwhere(ok)
        if len(free) == 0:
            return None
        v, u = (int(free[0][0]), int(free[0][1]))      # argwhere is row-major: lowest v, then lowest u
        for (du, dv, fw, fh) in rects:
            self.occ[v + dv:v + dv + fh, u + du:u + du + fw] = True
        return u, v


ORDERS = (
    ("height", lambda it: (-it[1][1], -it[1][0], it[0].order)),
    ("area", lambda it: (-sum(r[2] * r[3] for r in it[1][2].values()), -it[1][1], it[0].order)),
    ("width", lambda it: (-it[1][0], -it[1][1], it[0].order)),
)


def _pack_items(packer_factory, items):
    """Try each order (largest first by height, then by area, then by width); the first that fits wins."""
    for _, key in ORDERS:
        packer = packer_factory()
        placed = {}
        for c, (w, h, rects) in sorted(items, key=key):
            at = packer.place(w, h, list(rects.values()))
            if at is None:
                break
            placed[c.name] = {f: (at[0] + r[0], at[1] + r[1], r[2], r[3]) for f, r in rects.items()}
        else:
            return placed
    return None


def pack(model):
    """Assign cube.uv for every face and set model.tex_w / tex_h."""
    tpl = model.rig["uv_template"]
    base_area = tuple(tpl["base"])
    fixed_rects = {k: tuple(tpl[k]) for k in TEMPLATE_KEYS}
    face = model.rig["face"]
    for c in model.cubes:
        c.uv = {}
        for f, region in c.fixed.items():
            if region == "head_front":
                c.uv[f] = fixed_rects["head_front"]
            elif region in face["planes"]:
                fw, fh = face["planes"][region]["frame"]
                u, v = fixed_rects[region][:2]
                c.uv[f] = (u, v, fw, fh)
            else:
                raise HerogenError("cube %s: unknown fixed region %r" % (c.name, region))
            dens = face["density"]               # fixed regions are the double-density face texels
            size_u = texels(c.size[0], dens)
            size_v = texels(c.size[1], dens)
            if (size_u, size_v) != c.uv[f][2:]:
                raise HerogenError("cube %s: face %s is %dx%d texels but region %s is %dx%d"
                                   % (c.name, f, size_u, size_v, region, c.uv[f][2], c.uv[f][3]))
    items = [(c, net(c)) for c in model.cubes]
    items = [it for it in items if it[1][2]]
    base_items = [it for it in items if it[0].part == "base"]
    part_items = [it for it in items if it[0].part != "base"]
    by_name = {c.name: c for c, _ in items}

    placed = _pack_items(lambda: Packer(128, 128, area=base_area), base_items)
    if placed is None:
        raise HerogenError("base %s: the body cubes do not fit the base region %s" % (model.base_id, base_area))
    for name, uv in placed.items():
        by_name[name].uv.update(uv)

    for width in (128, 256):
        reserved = [base_area] + list(fixed_rects.values())
        placed = _pack_items(lambda: Packer(width, 128, reserved=reserved), part_items)
        if placed is not None:
            for name, uv in placed.items():
                by_name[name].uv.update(uv)
            model.tex_w, model.tex_h = width, 128
            return
    raise HerogenError("%s: the parts do not fit a 256x128 texture" % model.hero_id)


# ------------------------------------------------------------------ file convention

def to_file_uv(face, rect):
    """Net rectangle -> Bedrock per-face {uv, uv_size}. 'down' is stored flipped in v (GeckoLib's own box-UV
    mapping gives the bottom face uv (u, v + h) with size (w, -h)), so the face looks exactly like box UV."""
    u, v, w, h = rect
    if face == "down":
        return [u, v + h], [w, -h]
    return [u, v], [w, h]


def from_file_uv(face, uv, uv_size):
    u, v = uv
    w, h = uv_size
    if face == "down":
        return (u, v + h, w, -h)
    return (u, v, w, h)


def texel_point(cube, face, i, j, rect=None):
    """Model-space point (x, y, z) at the centre of texel column i, row j of a face (unrotated cube)."""
    x0, y0, z0, x1, y1, z1 = cube.box
    _, _, W, H = rect or cube.uv[face]
    fu, fv = (i + 0.5) / W, (j + 0.5) / H
    sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
    if face == "north":
        return (x0 + fu * sx, y1 - fv * sy, z0)
    if face == "south":
        return (x1 - fu * sx, y1 - fv * sy, z1)
    if face == "east":
        return (x0, y1 - fv * sy, z1 - fu * sz)
    if face == "west":
        return (x1, y1 - fv * sy, z0 + fu * sz)
    if face == "up":
        return (x0 + fu * sx, y1, z1 - fv * sz)
    if face == "down":
        return (x0 + fu * sx, y0, z1 - fv * sz)
    raise ValueError(face)
