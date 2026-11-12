"""Procedural painting (design 13.4): every texel of every cube face is painted from its 3D position.

Per texel: the slot and material come from the cube (or the base layer) and the paint ops of the parts that cover
that point; the tone (-2..+2 on the slot's ramp) is the sum of
- light by face direction: top +1, front and back 0, the hero's left side 0, right side -1, bottom -2 (light from the
  hero's front-left);
- a one-tone darker band on the lowest third of tall faces (5+ texels) for cloth, hair, leather, fur, scale, wood;
- ambient occlusion: -1 on the top row of a cube that sits under a cube at least as wide (cuffs, collars, belts);
- an outline: -2 on the last row of a clothing or hair region (hems, sleeve ends, bangs), never on skin;
- material detail (cloth folds, hair strands and a crown ring, metal specular band, leather stitches, fur noise...);
- pattern tone and the op's or cube's own tone.
Patterns use whole-texel 3D coordinates, so stripes and checks line up across neighbouring faces. Cutouts set alpha 0.
"""
import numpy as np

from rig import SIDE_FACES
from uvpack import texel_point
from util import hash_ints, hash_unit, seed_of

LIGHT = {"up": 1, "north": 0, "south": 0, "west": 0, "east": -1, "down": -2}
BANDED = {"cloth", "hair", "leather", "fur", "scale", "wood"}
OUTLINED = {"cloth", "hair", "leather", "fur"}
EPS = 1e-6


def _in(rng, v):
    return rng is None or (rng[0] - EPS <= v < rng[1] - EPS)


def jag_depth(n, i, W, dmax):
    """Rows cut from the bottom of column i for n teeth across W columns: each tooth is a whole number of columns
    with its tip (depth 0) on its middle column and the cut deepening toward its edges."""
    teeth = max(1, min(n, W // 2 if W >= 2 else 1))
    tw = W / float(teeth)
    k = int(np.floor((i + 0.5) / tw))
    start, end = int(np.floor(k * tw + 0.5)), int(np.floor((k + 1) * tw + 0.5)) - 1
    p, t = i - start, (end - start) // 2
    span = max(1, t, end - start - t)
    return int(np.floor(dmax * abs(p - t) / float(span) + 0.5))


class Painter:
    def __init__(self, model, ramps, base_layer):
        self.model = model
        self.ramps = ramps
        self.base_layer = base_layer
        self.seed = seed_of(model.hero_id)
        self.ops = sorted(model.paint_ops, key=lambda o: o["order"])
        self.op_cubes = [set(c.name for c in model.cubes_matching(o["cubes"])) for o in self.ops]
        self.solid = [c for c in model.cubes if c.flat_axis is None and c.rotation is None]
        self._ao = {c.name: self._ao_top(c) for c in model.cubes}

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
        group = ("cube", cube.name if cube.part != "base" else cube.slot)
        is_trim = False
        if cube.trim and face in SIDE_FACES and row is not None:
            w, edge = cube.trim["width"], cube.trim["edge"]
            hit = (edge in ("bottom", "both", "all") and row >= rows - w) or (edge in ("top", "both", "all") and row < w)
            if hit:
                slot, mat = cube.trim["slot"], cube.trim.get("material", mat)
                pattern, is_trim = None, True
        x, y, z = p
        for op, names in zip(self.ops, self.op_cubes):
            if cube.name not in names:
                continue
            if op["faces"] and face not in op["faces"]:
                continue
            if op["side"] == "r" and x >= 0 or op["side"] == "l" and x <= 0:
                continue
            if not (_in(op["y"], y) and _in(op["x"], x) and _in(op["z"], z) and _in(op["abs_x"], abs(x))):
                continue
            slot, mat, pattern, tone, is_trim = op["slot"], op["material"], op["pattern"], op["tone"], False
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
        return slot, mat, pattern, tone, is_trim, group

    # -------------------------------------------------------------- detail

    def material_tone(self, cube, mat, face, i, j, W, H, p):
        s = self.seed
        side = face in SIDE_FACES
        k = cube.order * 8 + ["north", "south", "east", "west", "up", "down"].index(face)
        if mat == "cloth":
            if side and H >= 6 and W >= 4 and j >= 2:
                period = 3 + hash_ints(s, k, 1) % 2
                off = hash_ints(s, k, 2) % period
                if (i + off) % period == 0:
                    return -1
            return 0
        if mat == "hair":
            if side:
                if 31.0 <= p[1] < 31.75:
                    return 2 if i % 4 == 1 else 1
                if j >= 1 and (i + hash_ints(s, k, 3)) % 3 == 0:
                    return -1
                return 0
            return -1 if hash_unit(s, k, i, j) < 0.25 else 0
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
            r = hash_unit(s, k, i, j)
            return -1 if r < 0.33 else (1 if r > 0.84 else 0)
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

    @staticmethod
    def pattern_at(pattern, face, p, density):
        """(slot or None, tone delta) of a pattern at a point."""
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
        return None, 0

    @staticmethod
    def keep(cube, face, i, j, W, H):
        """Alpha of a texel after the cube's cutout and taper (only side faces are cut)."""
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
        if cut == "round_bottom":
            return not (j == H - 1 and W >= 3 and i in (0, W - 1))
        if cut == "fur_tips":
            return not (j == H - 1 and H >= 2 and i % 2 == 1)
        if cut == "torn":
            depth = hash_ints(len(cube.name), i) % 3
            return j < H - min(depth, H - 1)
        for prefix, dmax in (("bangs_jagged_", 1), ("tip_jagged_", 2), ("jagged_", 2)):
            if cut.startswith(prefix):
                return j < H - jag_depth(int(cut[len(prefix):]), i, W, min(dmax, H - 1))
        return True

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
        for j in range(H):
            for i in range(W):
                p, slot, mat, pattern, tone, is_trim, group = grid[j][i]
                pslot, ptone = self.pattern_at(pattern, face, p, cube.density)
                if pslot:
                    slot = pslot
                t = LIGHT[face] + tone + ptone
                if side:
                    if mat in BANDED and H >= 5 and j >= H - H // 3:
                        t -= 1
                    if j == 0 and self._ao[cube.name]:
                        t -= 1
                    if mat in OUTLINED and not is_trim and self._outline(cube, face, grid, i, j, W, H, group):
                        t -= 2
                t += self.material_tone(cube, mat, face, i, j, W, H, p)
                t = max(-2, min(2, t))
                r, g, b = self.ramps[slot][t + 2]
                a = 255 if self.keep(cube, face, i, j, W, H) else 0
                img[v + j, u + i] = (r, g, b, a)
                if (cube.glow or mat == "glow") and a:
                    glow[v + j, u + i] = (r, g, b, 255)

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


def paint_model(model, ramps, base_layer):
    """Paint every non-fixed cube face. Returns (colour, glow) RGBA arrays."""
    img = new_canvas(model.tex_w, model.tex_h)
    glow = new_canvas(model.tex_w, model.tex_h)
    Painter(model, ramps, base_layer).paint(img, glow)
    return img, glow
