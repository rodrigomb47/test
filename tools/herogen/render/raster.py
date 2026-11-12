"""The software preview renderer (HEROES_V2.md 13.8, task G2): a hero's built geometry and texture, posed by an
animation sample, drawn the way Minecraft 1.21.1 and GeckoLib 4.9.3 draw an entity in daylight. It reads the written
files (geometry, PNGs), so it shows what the game gets.

What it emulates:
- **Geometry** (pinned to GeckoLib's code by tests/test_geckolib.py): GeckoLib bakes the file x-mirrored (render
  x = -file x); a cube rotation (rx, ry, rz) turns (-rx, -ry, rz) about the cube pivot, X first, then Y, then Z;
  every face takes its per-face UV rectangle the way vanilla box UV would (uvpack). A bone is placed as
  RenderUtil.prepMatrixForBone does: translate by the animated position (x negated), go to the pivot, rotate Z then
  Y then X by the rest rotation plus the animation's (file degrees, x and y negated), scale, go back from the pivot;
  children inherit their parent's transform, and a hidden bone hides its children.
- **Entity transform**: GeoEntityRenderer turns the model by 180 - body yaw about Y, so body yaw 0 faces south (+z).
- **Lighting**: vanilla's entity shaders (minecraft_mix_light) light every vertex from two fixed world directions,
  L0 = normalize(0.2, 1, -0.7) and L1 = normalize(-0.2, 1, 0.7):
  light = min(1, (max(0, n.L0) + max(0, n.L1)) * 0.6 + 0.4). In 1.21 the camera rotation is in the model-view
  matrix, not in the entity's pose stack, so `n` is the world normal and the light does not follow the camera: a hero
  facing south or north has its front and back at 0.74, its sides at 0.50, the top of its head at 1.0 and the soles at
  0.4. The normal is GeckoLib's: the face's direction through the pose's normal matrix, then
  RenderUtil.fixInvertedFlatCube (a cube with a zero size flips a negative x when its y or z size is zero, a negative y
  when its x or z size is zero, a negative z when its x or y size is zero; read from the 4.9.3 jar). The light map
  multiplies the result (`brightness`, 1.0 in full daylight).
- **entityCutoutNoCull**: no face culling, a texel with alpha below 0.1 is discarded, nearest texel sampling, the
  nearest surface wins.
- **Faction tint**: the sash bones' vertex colour multiplies their texels.
- **Glow layer** (AutoGlowingGeoLayer, translucent emissive): the glow mask texel of the visible surface, lit by
  the diffuse light but not by the light map, blended over the base by its alpha.

Camera: orthographic by default, or perspective from `distance` model pixels in front of the target point. `yaw` is
the camera's angle round the hero (0 in front, -90 sees the hero's left side, 180 its back) and `pitch` raises the
camera; neither changes the light, which belongs to the world (`body_yaw`). Pixel centres are sampled once (no
anti-aliasing), so the output is pixel-crisp at any integer scale.
"""
import math

import numpy as np

ALPHA_CUTOUT = 26                 # 0.1 of 255, rounded up: entityCutoutNoCull discards alpha < 0.1
CHUNK_FRAGMENTS = 400_000         # candidate pixels of small faces rasterised at once
BIG_FACE = 2_000                  # a face whose screen box has more pixels is rasterised on its own
L0 = np.array([0.2, 1.0, -0.7]) / math.sqrt(0.2 ** 2 + 1.0 + 0.7 ** 2)
L1 = np.array([-0.2, 1.0, 0.7]) / math.sqrt(0.2 ** 2 + 1.0 + 0.7 ** 2)
LIGHT_POWER, AMBIENT = 0.6, 0.4
FACES = ("north", "south", "east", "west", "up", "down")
# Outward face directions in FILE coordinates (east is the hero's right, file -x).
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (-1, 0, 0), "west": (1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


# ------------------------------------------------------------------ maths

def rotation_matrix(deg_x, deg_y, deg_z):
    """Rz . Ry . Rx: a vertex is turned about X first, then Y, then Z (PoseStack.mulPose Z, Y, X)."""
    ax, ay, az = (math.radians(a) for a in (deg_x, deg_y, deg_z))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def to_render(p):
    return np.array([-p[0], p[1], p[2]], dtype=float)


def face_corners(face, b):
    """Corners (TL, TR, BR, BL) of a face's UV rectangle, in file coordinates; b = (x0, y0, z0, x1, y1, z1)."""
    x0, y0, z0, x1, y1, z1 = b
    return {
        "north": [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        "south": [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        "east": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "west": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def entity_matrix(body_yaw=0.0):
    """GeoEntityRenderer.applyRotations: the model turned by 180 - body yaw about Y (render space to world)."""
    return rotation_matrix(0.0, 180.0 - body_yaw, 0.0)


def vanilla_light(normal):
    """minecraft_mix_light for one world-space normal (normalised here)."""
    n = np.asarray(normal, dtype=float)
    n = n / (np.linalg.norm(n) or 1.0)
    acc = max(0.0, float(n @ L0)) + max(0.0, float(n @ L1))
    return min(1.0, acc * LIGHT_POWER + AMBIENT)


def fix_inverted_flat_cube(size, normal):
    """GeckoLib 4.9.3 RenderUtil.fixInvertedFlatCube on a world normal, `size` the cube's file size."""
    n = np.array(normal, dtype=float)
    if n[0] < 0 and (size[1] == 0 or size[2] == 0):
        n[0] = -n[0]
    if n[1] < 0 and (size[0] == 0 or size[2] == 0):
        n[1] = -n[1]
    if n[2] < 0 and (size[0] == 0 or size[1] == 0):
        n[2] = -n[2]
    return n


def view_matrix(yaw, pitch=0.0):
    """Model to view space: the camera, `yaw` degrees round the hero and `pitch` degrees above it, looks along +z
    (screen right is view -x, screen up is +y)."""
    return rotation_matrix(-pitch, 0, 0) @ rotation_matrix(0, yaw, 0)


class View:
    """Where the camera is. `scale` is screen pixels per model pixel; `distance` (model pixels from the target)
    makes it a perspective camera, None an orthographic one; `target` is the model point it looks at."""

    def __init__(self, yaw=0.0, pitch=0.0, scale=4, distance=None, target=(0.0, 0.0, 0.0), body_yaw=0.0):
        self.yaw, self.pitch, self.scale = float(yaw), float(pitch), scale
        self.distance = distance
        self.target = to_render(target)
        self.body_yaw = float(body_yaw)
        self.matrix = view_matrix(self.yaw, self.pitch)

    def to_view(self, pts):
        """(N, 3) render-space points to view space (z grows away from the camera)."""
        return (self.matrix @ (np.asarray(pts, dtype=float) - self.target).T).T

    def w(self, z):
        """The perspective divisor of view depth z (1 for the orthographic camera)."""
        if self.distance is None:
            return np.ones_like(z)
        return (self.distance + z) / self.distance


# ------------------------------------------------------------------ the posed mesh

def _pose_of(pose, name):
    """(rotation, position, scale) a pose gives a bone. A pose entry is file-degree rotations (rx, ry, rz) or
    {"rotation": ..., "position": ..., "scale": ...} as Clip.sample gives (file degrees and pixels)."""
    if not pose or name not in pose:
        return None, None, None
    v = pose[name]
    if isinstance(v, dict):
        return v.get("rotation"), v.get("position"), v.get("scale")
    return v, None, None


def bone_transforms(geo, pose=None):
    """{bone: (A, t)}: the affine map p -> A p + t from the bone's own render space to the model's, parents applied."""
    out = {}
    for name in geo.order:
        b = geo.bones[name]
        rot = list(b.get("rotation") or (0.0, 0.0, 0.0))
        add_rot, add_pos, add_scale = _pose_of(pose, name)
        if add_rot is not None:
            rot = [rot[i] + add_rot[i] for i in range(3)]
        a = rotation_matrix(-rot[0], -rot[1], rot[2]) if any(abs(x) > 1e-12 for x in rot) else np.eye(3)
        if add_scale is not None and any(abs(s - 1.0) > 1e-12 for s in add_scale):
            a = a @ np.diag([float(s) for s in add_scale])
        pivot = to_render(b["pivot"])
        off = to_render(add_pos) if add_pos is not None else np.zeros(3)
        t = off + pivot - a @ pivot
        parent = b.get("parent")
        if parent in out:
            pa, pt = out[parent]
            a, t = pa @ a, pa @ t + pt
        out[name] = (a, t)
    return out


def hidden_set(geo, hidden):
    """The hidden bones and everything under them (GeckoLib hides a hidden bone's children)."""
    hs = set()
    for name in geo.order:
        if name in hidden or geo.parent(name) in hs:
            hs.add(name)
    return hs


class Quad:
    __slots__ = ("pts", "rect", "normal", "face", "bone", "size")

    def __init__(self, pts, rect, normal, face, bone, size):
        self.pts, self.rect, self.normal, self.face, self.bone, self.size = pts, rect, normal, face, bone, size

    def __iter__(self):                 # (corners, rect, normal, face, bone), the G1 tuple
        return iter((self.pts, self.rect, self.normal, self.face, self.bone))

    def __getitem__(self, i):
        return (self.pts, self.rect, self.normal, self.face, self.bone)[i]


def quads(geo, hidden=(), uv_shift=None, pose=None):
    """Every face drawn: Quad(corners TL, TR, BR, BL in render space, rect (u, v, w, h), unit normal in render space,
    face name, bone, the cube's file size)."""
    xf = bone_transforms(geo, pose)
    hs = hidden_set(geo, hidden)
    normal_mats = {}
    out = []
    for c in geo.cubes:
        if c.bone in hs:
            continue
        a, t = xf[c.bone]
        if c.bone not in normal_mats:
            normal_mats[c.bone] = np.linalg.inv(a).T
        inf = c.inflate
        x, y, z = c.origin
        sx, sy, sz = c.size
        box = (x - inf, y - inf, z - inf, x + sx + inf, y + sy + inf, z + sz + inf)
        crot = None
        if c.rotation is not None:
            crot = (to_render(c.pivot), rotation_matrix(-c.rotation[0], -c.rotation[1], c.rotation[2]))
        for face, rect in c.uv.items():
            pts = np.array([to_render(p) for p in face_corners(face, box)])
            n = to_render(NORMALS[face])
            if crot:
                pts = crot[0] + (crot[1] @ (pts - crot[0]).T).T
                n = crot[1] @ n
            pts = (a @ pts.T).T + t
            n = normal_mats[c.bone] @ n
            n = n / (np.linalg.norm(n) or 1.0)
            if uv_shift and c.bone in uv_shift:
                du, dv = uv_shift[c.bone]
                rect = (rect[0] + du, rect[1] + dv, rect[2], rect[3])
            out.append(Quad(pts, rect, n, face, c.bone, tuple(c.size)))
    return out


def quad_light(q, body_yaw=0.0):
    """The diffuse light GeckoLib and the vanilla shader give this face (world normal, flat-cube fix)."""
    n = entity_matrix(body_yaw) @ q.normal
    return vanilla_light(fix_inverted_flat_cube(q.size, n))


# ------------------------------------------------------------------ rasteriser

def projected_bounds(qs, view):
    """(xmin, xmax, ymin, ymax) of the quads' projections (model pixels at the target's depth)."""
    if not qs:
        return (0.0, 1.0, 0.0, 1.0)
    pts = np.concatenate([q.pts for q in qs])
    v = view.to_view(pts)
    w = view.w(v[:, 2])
    xs, ys = v[:, 0] / w, v[:, 1] / w
    return float(xs.min()), float(xs.max()), float(ys.min()), float(ys.max())


def model_bounds(geo, views, hidden=(), pose=None):
    """Common bounds of several views (View objects), so they line up at one scale."""
    qs = quads(geo, hidden, None, pose)
    bs = [projected_bounds(qs, v) for v in views]
    return (min(b[0] for b in bs), max(b[1] for b in bs), min(b[2] for b in bs), max(b[3] for b in bs))


class Frame:
    """A rendered image with its buffers: rgba (H, W, 4) uint8, depth (view z, inf where empty), quad index per
    pixel (-1 where empty), the quads, the texel each pixel shows, and the screen mapping."""

    def __init__(self, rgba, depth, ids, qs, bounds, scale, pad, texels=None):
        self.rgba, self.depth, self.ids, self.quads = rgba, depth, ids, qs
        self.bounds, self.scale, self.pad = bounds, scale, pad
        self.texels = texels                    # (H, W, 2) texel (u, v) of the visible surface

    def bone_at(self, x, y):
        i = int(self.ids[y, x])
        return self.quads[i].bone if i >= 0 else None

    def to_screen(self, view, p):
        """Pixel (x, y) of a render-space point."""
        v = view.to_view(np.asarray([p], dtype=float))[0]
        w = float(view.w(np.array([v[2]]))[0])
        xmin, xmax, ymin, ymax = self.bounds
        return (xmax - v[0] / w) * self.scale + self.pad, (ymax - v[1] / w) * self.scale + self.pad


def render(geo, tex, view=None, pose=None, hidden=(), uv_shift=None, tint=None, glow=None, bounds=None, pad=2,
           brightness=1.0, frame=False, **legacy):
    """Draw the hero. `tex` (and `glow`) are RGBA arrays; `tint` maps bone names to an RGB multiplier (0-255);
    `bounds` (xmin, xmax, ymin, ymax) in projected model pixels fixes the framing (default: the model's own).
    Returns the RGBA array, or a Frame with frame=True. The G1 keywords yaw, scale and pitch still work."""
    if view is None:
        view = View(legacy.pop("yaw", 0.0), legacy.pop("pitch", 0.0), legacy.pop("scale", 8))
    if legacy:
        raise TypeError("unknown arguments %s" % ", ".join(sorted(legacy)))
    tex = np.asarray(tex)
    th, tw = tex.shape[:2]
    qs = quads(geo, hidden, uv_shift, pose)
    if bounds is None:
        bounds = projected_bounds(qs, view)
    xmin, xmax, ymin, ymax = bounds
    scale = view.scale
    W = int(math.ceil((xmax - xmin) * scale - 1e-9)) + 2 * pad
    H = int(math.ceil((ymax - ymin) * scale - 1e-9)) + 2 * pad
    rgb = np.zeros((H, W, 3), dtype=np.float64)
    alpha = np.zeros((H, W), dtype=np.uint8)
    depth = np.full((H, W), np.inf)
    ids = np.full((H, W), -1, dtype=np.int32)
    tuv = np.zeros((H, W, 2), dtype=np.int32)
    lit = np.zeros((H, W, 3), dtype=np.float64)       # diffuse light x tint of the visible surface (glow layer)
    ent = entity_matrix(view.body_yaw)
    mults = np.zeros((len(qs), 3))
    for qi, q in enumerate(qs):
        shade = vanilla_light(fix_inverted_flat_cube(q.size, ent @ q.normal))
        tint_rgb = tint.get(q.bone, (255, 255, 255)) if tint else (255, 255, 255)
        mults[qi] = shade * np.array(tint_rgb, dtype=float) / 255.0
    if qs:
        geom = _project(qs, view, bounds, scale, pad, H, W)
        bufs = (rgb, alpha, depth, ids, tuv, lit)
        # chunks of faces in draw order, about CHUNK_FRAGMENTS candidate pixels each (bounded memory at any scale)
        # small faces in batches of about CHUNK_FRAGMENTS candidate pixels, a large face on its own (no sort needed)
        counts = geom["count"]
        screen = (xmax, ymax, scale, pad)
        lo, acc = 0, 0
        for i in range(len(qs)):
            c = int(counts[i])
            if c > BIG_FACE:
                if lo < i:
                    _raster_chunk(geom, lo, i, tex, mults, brightness, bufs, screen)
                _raster_chunk(geom, i, i + 1, tex, mults, brightness, bufs, screen)
                lo, acc = i + 1, 0
                continue
            acc += c
            if acc >= CHUNK_FRAGMENTS:
                _raster_chunk(geom, lo, i + 1, tex, mults, brightness, bufs, screen)
                lo, acc = i + 1, 0
        if lo < len(qs):
            _raster_chunk(geom, lo, len(qs), tex, mults, brightness, bufs, screen)
    out = np.zeros((H, W, 4), dtype=np.uint8)
    if glow is not None:
        g = np.asarray(glow)
        on = ids >= 0
        gt = g[tuv[..., 1], tuv[..., 0]]
        ga = np.where(on & (gt[..., 3] >= ALPHA_CUTOUT), gt[..., 3] / 255.0, 0.0)[..., None]
        rgb = rgb * (1.0 - ga) + gt[..., :3] * lit * ga
    out[..., :3] = np.clip(np.rint(rgb), 0, 255).astype(np.uint8)
    out[..., 3] = alpha
    if frame:
        return Frame(out, depth, ids, qs, bounds, scale, pad, tuv)
    return out


def _project(qs, view, bounds, scale, pad, H, W):
    """Per face: its view-space corners, the inverse of its projective map (s, t, 1) -> (x, y, w), whether it can be
    drawn (not edge-on, in front of the camera), its screen box and its UV rectangle."""
    xmin, xmax, ymin, ymax = bounds
    n = len(qs)
    pts = np.array([q.pts for q in qs])                              # (n, 4, 3)
    v = view.to_view(pts.reshape(-1, 3)).reshape(n, 4, 3)
    w = view.w(v[..., 2])
    m = np.zeros((n, 3, 3))
    m[:, 0, 0], m[:, 0, 1], m[:, 0, 2] = v[:, 1, 0] - v[:, 0, 0], v[:, 3, 0] - v[:, 0, 0], v[:, 0, 0]
    m[:, 1, 0], m[:, 1, 1], m[:, 1, 2] = v[:, 1, 1] - v[:, 0, 1], v[:, 3, 1] - v[:, 0, 1], v[:, 0, 1]
    m[:, 2, 0], m[:, 2, 1], m[:, 2, 2] = w[:, 1] - w[:, 0], w[:, 3] - w[:, 0], w[:, 0]
    det = np.linalg.det(m)
    good = (np.abs(det) >= 1e-9) & np.all(w > 1e-6, axis=1)          # not edge-on, in front of the camera
    m[~good] = np.eye(3)
    minv = np.linalg.inv(m)
    sx = (xmax - v[..., 0] / w) * scale + pad
    sy = (ymax - v[..., 1] / w) * scale + pad
    bx0 = np.maximum(0, np.floor(sx.min(axis=1))).astype(np.int64)
    bx1 = np.minimum(W - 1, np.ceil(sx.max(axis=1))).astype(np.int64)
    by0 = np.maximum(0, np.floor(sy.min(axis=1))).astype(np.int64)
    by1 = np.minimum(H - 1, np.ceil(sy.max(axis=1))).astype(np.int64)
    bw = np.where(good, np.maximum(0, bx1 - bx0 + 1), 0)
    bh = np.where(good, np.maximum(0, by1 - by0 + 1), 0)
    return {"v": v, "minv": minv, "bx0": bx0, "by0": by0, "bw": bw, "bh": bh, "count": bw * bh,
            "rects": np.array([q.rect for q in qs], dtype=float), "W": W}


def _raster_chunk(geom, lo, hi, tex, mults, brightness, bufs, screen):
    """Faces lo..hi at once. Each face is the parallelogram P0 + s (P1 - P0) + t (P3 - P0); every pixel centre in its
    screen box is taken back through the face's projective map (affine for the orthographic camera), so (s, t) gives
    the texel and the depth exactly. Within the chunk the nearest fragment of a pixel wins (ties: the earlier face,
    GeckoLib's draw order); it is then depth-tested against what earlier chunks drew."""
    rgb, alpha, depth, ids, tuv, lit = bufs
    xmax, ymax, scale, pad = screen
    W = geom["W"]
    th, tw = tex.shape[:2]
    v, minv = geom["v"][lo:hi], geom["minv"][lo:hi]
    bx0, by0, bw, bh = geom["bx0"][lo:hi], geom["by0"][lo:hi], geom["bw"][lo:hi], geom["bh"][lo:hi]
    n = hi - lo
    count = bw * bh
    total = int(count.sum())
    if total == 0:
        return
    qi = np.repeat(np.arange(n), count)
    local = np.arange(total) - np.repeat(np.cumsum(count) - count, count)
    px = bx0[qi] + local % bw[qi]
    py = by0[qi] + local // bw[qi]
    xp = xmax - (px + 0.5 - pad) / scale
    yp = ymax - (py + 0.5 - pad) / scale
    mi = minv[qi]
    hs = mi[:, 0, 0] * xp + mi[:, 0, 1] * yp + mi[:, 0, 2]
    ht = mi[:, 1, 0] * xp + mi[:, 1, 1] * yp + mi[:, 1, 2]
    hw = mi[:, 2, 0] * xp + mi[:, 2, 1] * yp + mi[:, 2, 2]
    pos = hw > 0
    hw = np.where(pos, hw, 1.0)
    s, t = hs / hw, ht / hw
    eps = 1e-7
    keep = pos & (s >= -eps) & (s <= 1 + eps) & (t >= -eps) & (t <= 1 + eps)
    qi, px, py, s, t = qi[keep], px[keep], py[keep], s[keep], t[keep]
    if not len(qi):
        return
    z = v[qi, 0, 2] + s * (v[qi, 1, 2] - v[qi, 0, 2]) + t * (v[qi, 3, 2] - v[qi, 0, 2])
    rects = geom["rects"][lo:hi]                                     # (n, 4) u, v, w, h
    ru, rv, rw, rh = rects[qi, 0], rects[qi, 1], rects[qi, 2], rects[qi, 3]
    tu = np.clip(np.floor(ru + s * rw), np.minimum(ru, ru + rw), np.maximum(ru, ru + rw) - 1)
    tv = np.clip(np.floor(rv + t * rh), np.minimum(rv, rv + rh), np.maximum(rv, rv + rh) - 1)
    tu = np.clip(tu, 0, tw - 1).astype(np.int64)
    tv = np.clip(tv, 0, th - 1).astype(np.int64)
    texel = tex[tv, tu]
    keep = texel[:, 3] >= ALPHA_CUTOUT
    qi, px, py, z, tu, tv, texel = qi[keep], px[keep], py[keep], z[keep], tu[keep], tv[keep], texel[keep]
    if not len(qi):
        return
    pix = py * W + px
    if n == 1:
        sel = np.arange(len(pix))                                    # one face: every pixel once
    else:
        order = np.lexsort((qi, np.round(z * 1e5), pix))            # per pixel: nearest, then first drawn
        pix_o = pix[order]
        first = np.ones(len(order), dtype=bool)
        first[1:] = pix_o[1:] != pix_o[:-1]
        sel = order[first]
    flat_depth = depth.reshape(-1)
    win = z[sel] < flat_depth[pix[sel]] - 1e-6
    sel = sel[win]
    if not len(sel):
        return
    yy, xx = py[sel], px[sel]
    gq = qi[sel]
    depth[yy, xx] = z[sel]
    rgb[yy, xx] = texel[sel][:, :3] * (mults[lo + gq] * brightness)
    alpha[yy, xx] = 255
    ids[yy, xx] = lo + gq
    tuv[yy, xx, 0] = tu[sel]
    tuv[yy, xx, 1] = tv[sel]
    lit[yy, xx] = mults[lo + gq]
