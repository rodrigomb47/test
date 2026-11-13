"""Procedural painting (design 13.4): every texel of every cube face is painted from its 3D position.

Per texel: the slot and material come from the cube (or the base layer) and the paint ops of the parts that cover
that point; the tone (-2..+2 on the slot's ramp) is the sum of
- light by face direction: top +1, front and back 0, the hero's left side 0, right side -1, bottom -2 (light from the
  hero's front-left);
- a one-tone darker band on the lowest third of tall faces (5+ texels; hair: the lowest 40% of faces 4+ texels tall)
  for cloth, hair, leather, fur, scale, wood,
  only where the region ends at the face's bottom edge (a garment that continues onto the cube below is shaded once, at
  its hem, not once per body cube, which would read as stripes);
- ambient occlusion: -1 on the top row of a cube that sits under a cube at least as wide (cuffs, collars, belts),
  only where the region above is a different one (a sleeve over a hand, a collar over a shirt);
- an outline: -2 on the last row of a clothing or hair region (hems, sleeve ends, bangs) and on the lowest texel a
  cutout leaves in a column (jagged tips), never on skin;
- material detail (one soft fold on big cloth faces, metal specular band, leather stitches, fur noise...);
- vertical shading (`shade: vertical`, the default for hair): the top 30% of a side face one tone lighter, so every
  strand or lock reads lit at the root and dark at the tip, as pixel-art hair is drawn;
- pattern tone and the op's or cube's own tone.
Style pass 2 paints flat, the way the references do (2-3 tones per face): hair has no painted strand lines (the strands
are cubes, lit at the root and dark at the tip by the vertical shading), cloth has at most one fold per big face.
Patterns use whole-texel 3D coordinates, so stripes and checks line up across neighbouring faces. Cutouts set alpha 0.
A cutout is authored on the side faces, and the other faces follow it so the solid stays consistent: the top and
bottom faces keep only what the side faces keep on their edge rows (a tapered or jagged tip has no flat full-size cap),
and a thin cube (one texel deep) cuts its narrow faces like the broad face's edge column (a triangular ear has no
full-height side strip).
"""
import math

import numpy as np

import emblems
from rig import SIDE_FACES
from rot import file_matrix
from uvpack import texel_point, texels
from util import hash_ints, hash_unit, seed_of

LIGHT = {"up": 1, "north": 0, "south": 0, "west": 0, "east": -1, "down": -2}
BANDED = {"cloth", "hair", "leather", "fur", "scale", "wood"}
OUTLINED = {"cloth", "hair", "leather", "fur"}
RESTING = {"cloth", "leather"}
SILHOUETTE_CUTS = {"tri_up", "tri_down", "peak", "round_top", "round_bottom"}   # cut through the front and back           # materials whose edges against the body get a soft shadow instead
EPS = 1e-6
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (-1, 0, 0), "west": (1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


ROOT_LIT = 0.3           # vertical shading: this share of a strand's side face, from the root, is one tone lighter


def _band_rows(mat, H):
    """Rows of the darker band at the bottom of a tall face: the lowest third, for hair the lowest 40% (art direction
    pass 2: a strand reads lit root, mid tone, dark lower part and a darker tip, the references' gold-to-orange
    strands)."""
    return max(1, (H * 2) // 5) if mat == "hair" else H // 3


def face_light(cube, face):
    """Baked light of a face: by the direction it faces once the cube's own rotation is applied (a tail segment
    turned upside down gets its light from below, not from its local top)."""
    if cube.rotation is None:
        return LIGHT[face]
    n = file_matrix(cube.rotation) @ np.array(NORMALS[face], dtype=float)
    best = max(NORMALS, key=lambda f: float(np.dot(n, NORMALS[f])))
    return LIGHT[best]


def _in(rng, v):
    return rng is None or (rng[0] - EPS <= v < rng[1] - EPS)


def jag_depth(n, i, W, dmax):
    """Rows cut from the bottom of column i for n teeth across W columns: each tooth is a whole number of columns
    with its tip (depth 0) on its middle column and the cut deepening toward its edges. The two outermost columns are
    never cut (art direction pass 2): a slab's narrow side faces follow those columns, and a cut there opened the
    slab's side so its bottom face showed through from inside as a striped "barcode" in side views."""
    if W >= 3 and i in (0, W - 1):
        return 0
    teeth = max(1, min(n, W // 2 if W >= 2 else 1))
    tw = W / float(teeth)
    k = int(np.floor((i + 0.5) / tw))
    start, end = int(np.floor(k * tw + 0.5)), int(np.floor((k + 1) * tw + 0.5)) - 1
    p, t = i - start, (end - start) // 2
    span = max(1, t, end - start - t)
    return int(np.floor(dmax * abs(p - t) / float(span) + 0.5))


def stamp_char(stamp, rows, col, row):
    """The emblem character at (col, row) of a stamp's bitmap (flipped for a mirrored copy), "." outside."""
    h, w = len(rows), len(rows[0])
    if not (0 <= row < h and 0 <= col < w):
        return "."
    return rows[row][w - 1 - col if stamp["flip"] else col]


def stamp_slot(stamp, ch, slot):
    """(slot, tone delta) a stamp character gives a texel whose own slot is `slot`; None for "."."""
    if ch == "#":
        return stamp["slot"], stamp["tone"]
    if ch == "-":
        return stamp["slot"], stamp["tone"] - 1
    if ch == "+":
        return (stamp["slot2"], 0) if stamp["slot2"] else (stamp["slot"], stamp["tone"] + 1)
    if ch == "o":
        return (stamp["slot2"], -1) if stamp["slot2"] else (stamp["slot"], stamp["tone"])
    return None


def face_uv(face, p, c):
    """(u, v) of point p from centre c on a face, as seen from outside (u to the right, v down; uvpack's
    orientation): front and back as seen from in front and behind, the right side with the back on the left, the
    left side with the front on the left, the top with the front at the bottom."""
    dx, dy, dz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    if face == "north":
        return dx, -dy
    if face == "south":
        return -dx, -dy
    if face == "east":
        return -dz, -dy
    if face == "west":
        return dz, -dy
    if face == "up":
        return dx, -dz
    return dx, -dz                    # the bottom face: rows toward the front, as uvpack.texel_point lays them


class Painter:
    def __init__(self, model, ramps, base_layer, phase_look=False):
        self.model = model
        self.phase_look = phase_look          # painting the phase texture (_p2): "phase" glow lights up
        self.ramps = ramps
        self.base_layer = base_layer
        self.seed = seed_of(model.hero_id)
        self.ops = sorted(model.paint_ops, key=lambda o: o["order"])
        self.op_cubes = [set(c.name for c in model.cubes_matching(o["cubes"])) for o in self.ops]
        self.solid = [c for c in model.cubes if c.flat_axis is None and c.rotation is None]
        self._ao = {c.name: self._ao_top(c) for c in model.cubes}
        self.head_y = model.env["head_y"]
        self._rot = {}

    # -------------------------------------------------------------- geometry helpers

    def _ao_top(self, c):
        if c.flat_axis is not None or c.rotation is not None:
            return False
        x0, y0, z0, x1, y1, z1 = c.box
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        area = (x1 - x0) * (z1 - z0)
        for d in self.solid:
            if d is c:
                continue
            a0, b0, c0, a1, b1, c1 = d.box
            if abs(b0 - y1) < 0.01 and a0 - EPS <= cx <= a1 + EPS and c0 - EPS <= cz <= c1 + EPS \
                    and (a1 - a0) * (c1 - c0) >= area - 0.01:
                return True
        return False

    def _cube_above(self, c, p):
        """The solid cube whose bottom sits on this cube's top at p (the one _ao_top found)."""
        x, _, z = p
        y = c.box[4]
        for d in self.solid:
            if d is c:
                continue
            a0, b0, c0, a1, b1, c1 = d.box
            if abs(b0 - y) < 0.01 and a0 - EPS <= x <= a1 + EPS and c0 - EPS <= z <= c1 + EPS:
                return d
        return None

    def _cube_below(self, c, p):
        x, _, z = p
        y = c.box[1]
        for d in self.solid:
            if d is c:
                continue
            a0, b0, c0, a1, b1, c1 = d.box
            if abs(b1 - y) < 0.01 and a0 - 0.6 <= x <= a1 + 0.6 and c0 - 0.6 <= z <= c1 + 0.6:
                return d
        return None

    # -------------------------------------------------------------- what is at a point

    def resolve(self, cube, face, p, row=None, rows=None):
        """(slot, material, pattern, tone, is_trim, group) of the texel at model point p on a face of cube."""
        slot, mat, pattern, tone = cube.slot, cube.material, cube.pattern, cube.tone
        # one region per cube, except hair: touching hair cubes of one colour are one mass (a cap, its dome and the
        # strands over it are not outlined against each other, only where the hair ends)
        group = ("cube", cube.name if cube.part != "base" else cube.slot) if cube.material != "hair" \
            else ("hair", cube.slot)
        is_trim = False
        if cube.trim and face in SIDE_FACES and row is not None:
            w, edge = cube.trim["width"], cube.trim["edge"]
            hit = (edge in ("bottom", "both", "all") and row >= rows - w) or (edge in ("top", "both", "all") and row < w)
            if hit:
                slot, mat = cube.trim["slot"], cube.trim.get("material", mat)
                pattern, is_trim = None, True
        tip = cube.tip
        if tip is not None:
            cap = "down" if tip["edge"] == "bottom" else "up"
            if face == cap:
                slot = tip["slot"]
            elif face in SIDE_FACES and row is not None:
                n = max(1, int(round(rows * tip["frac"])))
                if (tip["edge"] == "bottom" and row >= rows - n) or (tip["edge"] == "top" and row < n):
                    slot = tip["slot"]
            if slot == tip["slot"] and cube.material == "hair":
                group = ("hair", slot)
        x, y, z = p
        overlays, glow = [], cube.glow
        for op, names in zip(self.ops, self.op_cubes):
            if cube.name not in names:
                continue
            if op["faces"] and face not in op["faces"]:
                continue
            if op["side"] == "r" and x >= 0 or op["side"] == "l" and x <= 0:
                continue
            if not (_in(op["y"], y) and _in(op["x"], x) and _in(op["z"], z) and _in(op["abs_x"], abs(x))):
                continue
            vee = op.get("vee")
            if vee is not None and not (y >= vee[0] - EPS and abs(x) <= (y - vee[0]) * vee[1] + EPS):
                continue
            if op.get("overlay"):
                # keeps the texel's own slot and material; adds a pattern or an emblem on top (G6c)
                st = op.get("stamp")
                if st is not None:
                    if face not in st["faces"]:
                        continue
                    rows = emblems.rotated(emblems.bitmap(st["emblem"], st["size"]), st["rotate"])
                    u, v = face_uv(face, p, st["center"])
                    d = cube.density
                    col = int(math.floor(u * d + len(rows[0]) / 2.0 + st["offset"][0] + EPS))
                    row = int(math.floor(v * d + len(rows) / 2.0 + st["offset"][1] + EPS))
                    hit = stamp_slot(st, stamp_char(st, rows, col, row), slot)
                    if hit is None:
                        continue
                    overlays.append(("stamp", hit))
                    if op.get("glow"):
                        glow = op["glow"]
                elif op["pattern"]:
                    overlays.append(("pattern", op["pattern"], op.get("glow")))
                continue
            slot, mat, pattern, tone, is_trim = op["slot"], op["material"], op["pattern"], op["tone"], False
            overlays = []
            glow = op.get("glow") or cube.glow
            group = ("op",) + tuple(op["order"])
            t = op["trim"]
            if t and op["y"] is not None:
                lo, hi = op["y"]
                w = t["width"]
                if (t["edge"] in ("bottom", "both", "all") and y < lo + w) or \
                        (t["edge"] in ("top", "both", "all") and y >= hi - w):
                    slot, mat = t["slot"], t.get("material", mat)
                    pattern, is_trim = None, True
        slot = self.base_layer.get(slot, slot)
        return slot, mat, pattern, tone, is_trim, group, tuple(overlays), glow

    # -------------------------------------------------------------- detail

    def material_tone(self, cube, mat, face, i, j, W, H, p):
        s = self.seed
        side = face in SIDE_FACES
        k = cube.order * 8 + ["north", "south", "east", "west", "up", "down"].index(face)
        if mat == "cloth":
            # one soft fold line on big flat faces only (style pass 2: flat painting, the geometry carries the
            # volume; folds every few texels read as stripes on long limbs and flared panels)
            if side and cube.rotation is None and H >= 8 and W >= 6 and j >= 2:
                if i == 1 + hash_ints(s, k, 1) % max(1, W - 2):
                    return -1
            return 0
        if mat == "hair":
            return 0             # style pass 2: no painted strand lines, the strands are cubes
        if mat == "metal":
            if side:
                if W >= 3 and i == W // 3:
                    return 2
                if j == 0:
                    return 1
                if W >= 3 and i == W - 1:
                    return -1
            return 0
        if mat == "leather":
            return -1 if side and H >= 4 and j == 1 and i % 2 == 0 else 0
        if mat == "fur":
            # soft tufts: a few darker and lighter texels (style pass 2: flatter than the v2 noise)
            r = hash_unit(s, k, i // 2, j)
            return -1 if r < 0.16 else (1 if r > 0.9 else 0)
        if mat == "scale":
            if j % 2 == 1:
                return -1
            return 1 if (i + j // 2) % 2 == 0 else 0
        if mat == "wood":
            h = int(np.floor(p[0] if face in ("north", "south", "up", "down") else p[2]))
            return -1 if hash_ints(s, 77, h) % 4 == 0 else 0
        if mat == "lacquer":
            return 2 if side and H >= 3 and j == 1 else 0
        if mat == "bone":
            return -1 if hash_unit(s, k, i, j) < 0.12 else 0
        if mat == "gem":
            if (i + j) % 3 == 0:
                return 1
            return -1 if (i + 2 * j) % 4 == 3 else 0
        if mat == "glow":
            return 1
        return 0

    def world_point(self, cube, p):
        """Where a rest-space point of a cube ends up after the cube's own rotation (strands are rotated boxes)."""
        if cube.rotation is None:
            return p
        m = self._rot.get(cube.name)
        if m is None:
            m = self._rot[cube.name] = file_matrix(cube.rotation)
        pv = np.array(cube.pivot, dtype=float)
        return tuple(pv + m @ (np.array(p, dtype=float) - pv))

    @staticmethod
    def pattern_at(pattern, face, p, density, world=None):
        """(slot or None, tone delta) of a pattern at a point. `world`: the point after the cube's own rotation
        (patches follow the real surface, so a blob runs across the strands of a hair mass)."""
        if not pattern:
            return None, 0
        vx, vy, vz = (int(np.floor(c * density + EPS)) for c in p)
        h = vz if face in ("east", "west") else vx
        kind = pattern["type"]
        n = max(1, int(pattern.get("size", pattern.get("width", 1))))
        alt = pattern["slots"][0] if pattern["slots"] else None
        if kind == "stripes":
            d = pattern.get("dir", "h")
            key = vy if d == "h" else (h if d == "v" else vy + h)
            return (alt if (key // n) % 2 == 1 else None), 0
        if kind == "checks":
            return (alt if ((vx // n) + (vy // n) + (vz // n)) % 2 == 1 else None), 0
        if kind == "plaid":
            hb, vb = vy % (2 * n) == 0, h % (2 * n) == 0
            if hb and vb:
                return alt, -1
            return (alt if hb or vb else None), 0
        if kind == "dots":
            row = vy // n
            hh = h + (row % 2) * (n // 2)
            return (alt if vy % n == 0 and hh % n == 0 else None), 0
        if kind == "pleats":
            return None, (-1 if (h // n) % 2 == 1 else 0)
        if kind == "solid":
            return alt, 0                          # the whole region (an overlay band: a stripe of a marking)
        alt2 = pattern["slots"][1] if len(pattern["slots"]) > 1 else alt
        if kind in ("waves", "scales"):
            # overlapping discs of radius n on a brick lattice (2n across, n up, every other row offset by n). waves
            # (seigaiha): each lower row is in front, so the top halves show as fans, each with an outer and an
            # inner ring; scales: each upper row is in front, so the bottom halves hang as scales with a rim and a
            # darker lower half
            px, py = h + 0.5, vy + 0.5
            row0 = int(math.floor(py / n))
            rows = range(row0 - 1, row0 + 2) if kind == "waves" else range(row0 + 1, row0 - 2, -1)
            for r in rows:
                off = (r % 2) * n
                cx = math.floor((px - off) / (2.0 * n) + 0.5) * 2 * n + off
                dist = math.hypot(px - cx, py - r * n)
                if dist > n:
                    continue
                if kind == "waves":
                    if dist > n - 1 or (n >= 4 and abs(dist - n * 0.55) <= 0.5):
                        return alt, 0
                    return None, 0
                if dist > n - 1:
                    return alt, 0
                return None, (-1 if py < r * n - n * 0.35 else 0)
            return None, 0
        if kind == "hemp":
            # hemp-leaf: a lattice of verticals and both diagonals every 2n texels (six-pointed stars)
            p2 = 2 * n
            if h % p2 == 0 or (h + vy) % p2 == 0 or (h - vy) % p2 == 0:
                return alt, 0
            return None, 0
        if kind == "petals":
            # small five-texel blossoms on a staggered grid (period n, default 5), the heart in the second slot
            p2 = max(4, n)
            row = vy // p2
            hh = h + (row % 2) * (p2 // 2)
            a, b = hh % p2, vy % p2
            if (a, b) == (1, 1):
                return alt2, 0
            if (a, b) in ((0, 1), (2, 1), (1, 0), (1, 2)):
                return alt, 0
            return None, 0
        if kind == "patches":
            # soft blobs (calico, piebald): a smooth value noise over the 3D texel grid, cell n (default 4); the
            # first slot where it is high, the second where it is low
            cell = max(2, n)
            seed = 0x9E3779B97F4A7C15 ^ pattern.get("seed", 0)
            q = np.array(world if world is not None else p, dtype=float)
            centre = pattern.get("center")
            if centre is not None:
                # by direction from a centre (the head's): every layer of strands shares one blob, where a 3D
                # noise would change from the inner ring to the outer one and speckle the mass
                d = q - np.array(centre, dtype=float)
                q = d / (np.linalg.norm(d) or 1.0) * float(pattern.get("radius", 6.0))
            q = np.floor(q * density + EPS) / float(density)
            fx, fy, fz = (float(c) / cell for c in q)
            ix, iy, iz = int(math.floor(fx)), int(math.floor(fy)), int(math.floor(fz))
            tx, ty, tz = fx - ix, fy - iy, fz - iz
            tx, ty, tz = (t * t * (3 - 2 * t) for t in (tx, ty, tz))
            acc = 0.0
            for dx in (0, 1):
                for dy in (0, 1):
                    for dz in (0, 1):
                        w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                        acc += w * hash_unit(seed, ix + dx, iy + dy, iz + dz)
            hi, lo = pattern.get("high", 0.62), pattern.get("low", 0.3)
            if acc >= hi:
                return alt, 0
            if len(pattern["slots"]) > 1 and acc < lo:
                return alt2, 0
            return None, 0
        return None, 0

    @staticmethod
    def own_keep(cube, face, i, j, W, H):
        """Alpha of a side-face texel after the cube's own cutout and taper (the authored shape)."""
        if face not in SIDE_FACES:
            return True
        cut = cube.cutout
        if cube.taper is not None:
            half = W / 2.0 * (1.0 - (1.0 - cube.taper) * (j + 0.5) / H)
            if abs(i + 0.5 - W / 2.0) > half + 0.25:
                return False
        if not cut:
            return True
        if cut == "tri_up":
            return abs(i + 0.5 - W / 2.0) <= (j + 1) * (W / 2.0) / H + EPS
        if cut == "tri_down":
            return abs(i + 0.5 - W / 2.0) <= (H - j) * (W / 2.0) / H + EPS
        if cut == "round_bottom":
            return not (j == H - 1 and W >= 3 and i in (0, W - 1))
        if cut == "round_top":
            return not (j == 0 and W >= 3 and i in (0, W - 1))
        if cut == "peak":
            # an animal ear: full width in the lower third, a triangle above it with its tip on the top row
            base = H - max(1, H // 3)
            if j >= base:
                return True
            return abs(i + 0.5 - W / 2.0) <= (j + 1) * (W / 2.0) / base + EPS
        if cut == "fur_tips":
            return not (j == H - 1 and H >= 2 and i % 2 == 1)
        if cut == "lace":
            # a lace edge: scallops on the bottom row (every other texel cut) and a row of holes above it
            if j == H - 1 and H >= 2:
                return i % 2 == 0
            if j == H - 3 and H >= 4:
                return i % 4 != 1
            return True
        if cut == "torn":
            depth = hash_ints(len(cube.name), i) % 3
            return j < H - min(depth, H - 1)
        for prefix, dmax in (("bangs_jagged_", 1), ("tip_jagged_", 2), ("jagged_", 2)):
            if cut.startswith(prefix):
                return j < H - jag_depth(int(cut[len(prefix):]), i, W, min(dmax, H - 1))
        return True

    @classmethod
    def keep(cls, cube, face, i, j, W, H):
        """Alpha of a texel: the cube's cutout, carried to every face so the solid stays consistent.

        A taper narrows every side face on its own (a strand is pointed in every view); caps keep what the side faces
        keep on their edge rows. A cutout without a taper is a silhouette cut through the cube's broad pair of faces
        (north/south for the shape cuts tri_up, tri_down, peak, round_top and round_bottom and for cubes wider than
        deep; east/west for cubes deeper than wide): the narrow faces and the caps follow the broad face's edge
        column and edge row, so an ear or a jagged side cover has no full-height strip on its narrow sides."""
        if cube.taper is None and not cube.cutout:
            return True
        if cube.flat_axis is not None:
            return cls.own_keep(cube, face, i, j, W, H)
        d = cube.density
        wx, hy, dz = texels(cube.size[0], d), texels(cube.size[1], d), texels(cube.size[2], d)
        if cube.taper is not None:
            if face in ("up", "down"):
                r = hy - 1 if face == "down" else 0
                return cls.own_keep(cube, "north", i, r, wx, hy) and cls.own_keep(cube, "west", dz - 1 - j, r, dz,
                                                                                    hy)
            return cls.own_keep(cube, face, i, j, W, H)
        r = hy - 1 if face == "down" else 0
        if cube.cutout in SILHOUETTE_CUTS or wx >= dz:
            if face in ("north", "south"):
                return cls.own_keep(cube, face, i, j, W, H)
            if face in ("east", "west"):
                return cls.own_keep(cube, "north", 0 if face == "east" else wx - 1, j, wx, hy)
            return cls.own_keep(cube, "north", i, r, wx, hy)
        if face in ("east", "west"):
            return cls.own_keep(cube, face, i, j, W, H)
        if face in ("north", "south"):
            return cls.own_keep(cube, "west", 0 if face == "north" else dz - 1, j, dz, hy)
        return cls.own_keep(cube, "west", dz - 1 - j, r, dz, hy)

    # -------------------------------------------------------------- paint

    def paint(self, img, glow):
        for cube in self.model.cubes:
            for face, rect in sorted(cube.uv.items()):
                if face in cube.fixed:
                    continue
                self.paint_face(cube, face, rect, img, glow)

    def paint_face(self, cube, face, rect, img, glow):
        u, v, W, H = rect
        side = face in SIDE_FACES
        grid = []
        for j in range(H):
            row = []
            for i in range(W):
                p = texel_point(cube, face, i, j, rect)
                row.append((p,) + self.resolve(cube, face, p, j, H))
            grid.append(row)
        kept = [[self.keep(cube, face, i, j, W, H) for i in range(W)] for j in range(H)]
        ends = [self._outline(cube, face, grid, i, H - 1, W, H, grid[H - 1][i][6]) for i in range(W)] if side else []
        st = cube.stamp if cube.stamp is not None and face in cube.stamp["faces"] else None
        if st is not None:
            fit = (W, H) if st["rotate"] in (0, 180) else (H, W)
            rows = emblems.rotated(emblems.bitmap(st["emblem"], st["size"], fit=fit), st["rotate"])
            i0 = (W - len(rows[0])) // 2 + int(st["offset"][0])
            j0 = (H - len(rows)) // 2 + int(st["offset"][1])
        for j in range(H):
            for i in range(W):
                p, slot, mat, pattern, tone, is_trim, group, overlays, lit = grid[j][i]
                pslot, ptone = self.pattern_at(pattern, face, p, cube.density)
                if pslot:
                    slot = pslot
                for kind, what, *rest in overlays:
                    if kind == "pattern":
                        wp = self.world_point(cube, p) if what["type"] == "patches" else None
                        oslot, otone = self.pattern_at(what, face, p, cube.density, wp)
                        if oslot:
                            slot, ptone = oslot, 0
                            if rest and rest[0]:
                                lit = rest[0]
                        ptone += otone
                    else:                                  # an op's emblem: (slot, tone)
                        slot, ptone = what[0], what[1]
                if st is not None:
                    hit = stamp_slot(st, stamp_char(st, rows, i - i0, j - j0), slot)
                    if hit is not None:
                        slot, ptone = hit
                t = (0 if face == "up" and cube.shade == "mass" else face_light(cube, face)) + tone + ptone
                if side and cube.outline and cube.shade != "mass":
                    if mat in BANDED and H >= (4 if mat == "hair" else 5) and j >= H - _band_rows(mat, H) and ends[i] \
                            and all(grid[k][i][6] == group for k in range(j, H)):
                        t -= 1
                    if j == 0 and self._ao[cube.name] and self._ao_differs(cube, face, p, group):
                        t -= 1
                    if mat in OUTLINED and not is_trim and kept[j][i] and (
                            self._outline(cube, face, grid, i, j, W, H, group)
                            or (H >= 3 and j + 1 < H and not kept[j + 1][i])):
                        # a cloth edge lying against the body (the bust, a collar) is a soft shadow, not a hem line
                        t -= 1 if mat in RESTING and self._rests_on_body(cube, face, p) else 2
                if side and H >= 3 and (cube.shade == "vertical" or (cube.shade is None and mat == "hair")) \
                        and (j + 0.5) / H < ROOT_LIT:
                    t += 1
                t += self.material_tone(cube, mat, face, i, j, W, H, p)
                t = max(-2, min(2, t))
                r, g, b = self.ramps[slot][t + 2]
                a = 255 if kept[j][i] else 0
                img[v + j, u + i] = (r, g, b, a)
                if a and (lit is True or mat == "glow" or (lit == "phase" and self.phase_look)):
                    glow[v + j, u + i] = (r, g, b, 255)

    def _rests_on_body(self, cube, face, p):
        """True when another solid cube sits right behind this face at p (the face's cube is attached to it)."""
        n = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (1, 0, 0), "west": (-1, 0, 0)}.get(face)
        if n is None:
            return False
        x0, y0, z0, x1, y1, z1 = cube.box
        depth = {"north": z1 - z0, "south": z1 - z0, "east": x1 - x0, "west": x1 - x0}[face]
        q = (p[0] + n[0] * (depth + 0.25), p[1] + 0.25, p[2] + n[2] * (depth + 0.25))
        for d in self.solid:
            if d is cube:
                continue
            a0, b0, c0, a1, b1, c1 = d.box
            if a0 <= q[0] <= a1 and b0 <= q[1] <= b1 and c0 <= q[2] <= c1:
                return True
        return False

    def _ao_differs(self, cube, face, p, group):
        """Ambient occlusion only where another region sits on top (a sleeve over a hand), not inside one garment."""
        above = self._cube_above(cube, p)
        if above is None:
            return True
        q = (p[0], above.box[1] + 0.25, p[2])
        return self.resolve(above, face, q)[5] != group

    def _outline(self, cube, face, grid, i, j, W, H, group):
        if j + 1 < H:
            return grid[j + 1][i][6] != group
        p = grid[j][i][0]
        below = self._cube_below(cube, p)
        if below is None:
            return True
        q = (p[0], below.box[4] - 0.25, p[2])
        return self.resolve(below, face, q)[5] != group


def new_canvas(w, h):
    return np.zeros((h, w, 4), dtype=np.uint8)


def paint_model(model, ramps, base_layer, phase_look=False):
    """Paint every non-fixed cube face. Returns (colour, glow) RGBA arrays. `phase_look`: the phase texture (_p2),
    where "phase" glow lights up."""
    img = new_canvas(model.tex_w, model.tex_h)
    glow = new_canvas(model.tex_w, model.tex_h)
    Painter(model, ramps, base_layer, phase_look).paint(img, glow)
    return img, glow


def has_phase_glow(model):
    """True when a cube or paint op glows only in the phase look."""
    return any(c.glow == "phase" for c in model.cubes) or any(o.get("glow") == "phase" for o in model.paint_ops)
