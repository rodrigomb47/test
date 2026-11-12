"""Minimal orthographic preview renderer (G1 stopgap until G2's renderer exists).

Reads a written .geo.json and its PNG, so it shows exactly what the game gets. Conventions emulated from GeckoLib's
baking: positions are mirrored in x (render x = -file x), cube and bone rotations become (-rx, -ry, rz) and apply
in the order Z, Y, X about their pivot. Faces map their UV rectangle as vanilla box UV does (uvpack docstring).
Rendering is cull-free with alpha cut-out at 0.1 like entityCutoutNoCull, nearest-neighbour sampling and simple
block-style face shading (top 1.0, front and back 0.8, sides 0.6, bottom 0.5). Rest pose only.
"""
import math

import numpy as np

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def rotation_matrix(deg_x, deg_y, deg_z):
    ax, ay, az = (math.radians(a) for a in (deg_x, deg_y, deg_z))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx          # a vertex is rotated about X first, then Y, then Z


def _face_corners(face, b):
    """Corners (TL, TR, BR, BL) of a face's UV rectangle, in file coordinates."""
    x0, y0, z0, x1, y1, z1 = b
    return {
        "north": [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        "south": [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        "east": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "west": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (-1, 0, 0), "west": (1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


def _to_render(p):
    return np.array([-p[0], p[1], p[2]], dtype=float)


def _bone_matrices(geo):
    """Rest transforms of every bone (render space): a function p -> p for each bone, parents applied."""
    mats = {}
    for name in geo.order:
        b = geo.bones[name]
        parent = b.get("parent")
        rot = b.get("rotation")
        local = None
        if rot and any(abs(a) > 1e-9 for a in rot):
            local = (_to_render(b["pivot"]), rotation_matrix(-rot[0], -rot[1], rot[2]))
        mats[name] = (mats.get(parent, []) if parent else []) + ([local] if local else [])
    return mats


def _apply_chain(chain, p):
    for pivot, m in reversed(chain):
        p = pivot + m @ (p - pivot)
    return p


def quads(geo, hidden=(), uv_shift=None):
    """Every visible face: (corners (4x3, render space), rect (u, v, w, h), normal, face name, bone)."""
    chains = _bone_matrices(geo)
    hidden_set = set()
    for name in geo.order:
        parent = geo.parent(name)
        if name in hidden or (parent in hidden_set):
            hidden_set.add(name)
    out = []
    for c in geo.cubes:
        if c.bone in hidden_set:
            continue
        inf = c.inflate
        x, y, z = c.origin
        sx, sy, sz = c.size
        b = (x - inf, y - inf, z - inf, x + sx + inf, y + sy + inf, z + sz + inf)
        crot = None
        if c.rotation is not None:
            crot = (_to_render(c.pivot), rotation_matrix(-c.rotation[0], -c.rotation[1], c.rotation[2]))
        for face, rect in c.uv.items():
            pts = []
            for p in _face_corners(face, b):
                q = _to_render(p)
                if crot:
                    q = crot[0] + crot[1] @ (q - crot[0])
                q = _apply_chain(chains[c.bone], q)
                pts.append(q)
            n = _to_render(NORMALS[face])
            if crot:
                n = crot[1] @ n
            if uv_shift and c.bone in uv_shift:
                du, dv = uv_shift[c.bone]
                rect = (rect[0] + du, rect[1] + dv, rect[2], rect[3])
            out.append((np.array(pts), rect, n, face, c.bone))
    return out


def render(geo, tex, yaw=0.0, scale=8, hidden=(), uv_shift=None, tint=None, pad=2, bounds=None):
    """RGBA image of the model seen from the front (camera on -z looking +z) after turning it by `yaw` degrees
    (negative yaw shows the hero's left side). `tint` maps bone names to an RGB multiplier."""
    tex = np.asarray(tex)
    th, tw = tex.shape[:2]
    view = rotation_matrix(0, yaw, 0)
    items = []
    for pts, rect, n, face, bone in quads(geo, hidden, uv_shift):
        items.append(((view @ pts.T).T, rect, n, face, bone))
    if bounds is None:
        allp = np.concatenate([it[0] for it in items])
        bounds = (allp[:, 0].min(), allp[:, 0].max(), allp[:, 1].min(), allp[:, 1].max())
    xmin, xmax, ymin, ymax = bounds
    W = int(math.ceil((xmax - xmin) * scale)) + 2 * pad
    H = int(math.ceil((ymax - ymin) * scale)) + 2 * pad
    img = np.zeros((H, W, 4), dtype=np.uint8)
    depth = np.full((H, W), np.inf)

    def to_screen(p):
        # screen right is -x in render space (camera looks along +z), screen down is -y
        return ((xmax - p[0]) * scale + pad, (ymax - p[1]) * scale + pad, p[2])

    for vp, rect, n, face, bone in items:
        u, v, w, h = rect
        s = [to_screen(p) for p in vp]
        uvs = [(u, v), (u + w, v), (u + w, v + h), (u, v + h)]
        nn = n / (np.linalg.norm(n) or 1)
        light = 0.6 * nn[0] ** 2 + 0.8 * nn[2] ** 2 + (1.0 if nn[1] > 0 else 0.5) * nn[1] ** 2
        mult = np.array(tint.get(bone, (255, 255, 255)) if tint else (255, 255, 255), dtype=float) / 255.0
        for tri in ((0, 1, 2), (0, 2, 3)):
            _raster(img, depth, [s[i] for i in tri], [uvs[i] for i in tri], tex, rect, light * mult, tw, th)
    return img


def _raster(img, depth, pts, uvs, tex, rect, mult, tw, th):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = pts
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    H, W = depth.shape
    bx0, bx1 = max(0, int(math.floor(min(x0, x1, x2)))), min(W - 1, int(math.ceil(max(x0, x1, x2))))
    by0, by1 = max(0, int(math.floor(min(y0, y1, y2)))), min(H - 1, int(math.ceil(max(y0, y1, y2))))
    if bx1 < bx0 or by1 < by0:
        return
    ys, xs = np.mgrid[by0:by1 + 1, bx0:bx1 + 1]
    px, py = xs + 0.5, ys + 0.5
    w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
    w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
    w2 = 1.0 - w0 - w1
    eps = -1e-7
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    z = w0 * z0 + w1 * z1 + w2 * z2
    u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
    v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
    ru, rv, rw, rh = rect
    tu = np.clip(np.floor(u), min(ru, ru + rw), max(ru, ru + rw) - 1).astype(int)
    tv = np.clip(np.floor(v), min(rv, rv + rh), max(rv, rv + rh) - 1).astype(int)
    tu = np.clip(tu, 0, tw - 1)
    tv = np.clip(tv, 0, th - 1)
    texel = tex[tv, tu]
    ok = inside & (texel[..., 3] >= 26) & (z < depth[ys, xs] - 1e-6)
    if not ok.any():
        return
    col = np.clip(texel[..., :3] * mult, 0, 255).astype(np.uint8)
    yy, xx = ys[ok], xs[ok]
    depth[yy, xx] = z[ok]
    img[yy, xx, :3] = col[ok]
    img[yy, xx, 3] = 255


def model_bounds(geo, yaws, hidden=()):
    """Common screen bounds for several views, so they line up at the same scale."""
    xs, ys = [], []
    for yaw in yaws:
        view = rotation_matrix(0, yaw, 0)
        for pts, *_ in quads(geo, hidden):
            vp = (view @ pts.T).T
            xs += [vp[:, 0].min(), vp[:, 0].max()]
            ys += [vp[:, 1].min(), vp[:, 1].max()]
    return min(xs), max(xs), min(ys), max(ys)
