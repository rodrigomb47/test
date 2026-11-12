"""Forward kinematics in file space: where GeckoLib puts every bone for a pose.

GeckoLib's bone transform (RenderUtil.prepMatrixForBone, read from 4.9.3): translate by the animated position (x
mirrored, so file pixels), move to the pivot, rotate Z then Y then X (rest rotation plus the animated one, both
file degrees), scale, move back. In file coordinates a bone therefore maps a point p of its subtree to

    position + pivot + M (p - pivot),   M = rot.file_matrix(rest + animated)

and its parent's transform follows. `Skeleton.world` returns, per bone, (R, T) with world(p) = R p + T.
"""
import fnmatch
import functools

import numpy as np

from anim.clip import form_var
from rig import BASE_IDS, Model
from rot import file_matrix

ZERO = (0.0, 0.0, 0.0)
FACE_PLANES = ("eyes", "mouth", "cheeks")


class Skeleton:
    def __init__(self, bones, env=None, body=None):
        """bones: (name, parent, pivot, rest rotation), parents before children. body: {bone: [corner points]} of the
        base body's cubes (the contact points of the `floor` fix and the body ground rule)."""
        self.order = []
        self.parent, self.pivot, self.rest = {}, {}, {}
        for name, parent, pivot, rest in bones:
            if parent is not None and parent not in self.parent:
                raise ValueError("bone %s comes before its parent %s" % (name, parent))
            self.order.append(name)
            self.parent[name] = parent
            self.pivot[name] = np.array(pivot, dtype=float)
            self.rest[name] = tuple(float(a) for a in (rest or ZERO))
        self.env = dict(env or {})
        self.body = {b: [np.array(p, dtype=float) for p in pts] for b, pts in (body or {}).items()}
        # FORM_VAR of this body (anim.clip): what HeroModel sets for its form-scaled keys
        self.d = form_var(self.env) if "hip_y" in self.env else 0.0

    @classmethod
    def from_model(cls, model):
        """Every rig bone of a model's catalogue (pivots resolved for its base), plus the bones its parts declared,
        and the corners of its base body cubes (not the face planes)."""
        names = list(model.rig_order) + list(model.extra_order)
        bones = []
        for n in names:
            b = model.catalogue[n]
            bones.append((n, b.parent, b.pivot, b.rotation))
        body = {}
        for c in model.cubes:
            if c.part != "base" or c.bone in FACE_PLANES:
                continue
            x0, y0, z0, x1, y1, z1 = c.box
            body.setdefault(c.bone, []).extend((x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1))
        return cls(bones, model.env, body)

    @classmethod
    def from_geo(cls, geo):
        return cls([(n, geo.bones[n].get("parent"), geo.bones[n]["pivot"], geo.bones[n].get("rotation"))
                    for n in geo.order])

    def has(self, bone):
        return bone in self.parent

    def chain_to_root(self, bone):
        out = []
        while bone is not None:
            out.append(bone)
            bone = self.parent[bone]
        return out

    def world(self, pose=None, only=None):
        """{bone: (R, T)} for a pose {bone: {"rotation": (rx, ry, rz), "position": (x, y, z)}} (file units).
        `only`: compute just these bones and their ancestors."""
        pose = pose or {}
        need = None
        if only is not None:
            need = set()
            for b in only:
                if b in self.parent:
                    need.update(self.chain_to_root(b))
        out = {}
        for name in self.order:
            if need is not None and name not in need:
                continue
            chans = pose.get(name, {})
            rot = chans.get("rotation", ZERO)
            rest = self.rest[name]
            m = file_matrix((rest[0] + rot[0], rest[1] + rot[1], rest[2] + rot[2]))
            d = np.array(chans.get("position", ZERO), dtype=float)
            piv = self.pivot[name]
            t_local = d + piv - m @ piv
            p = self.parent[name]
            if p is None:
                out[name] = (m, t_local)
            else:
                rp, tp = out[p]
                out[name] = (rp @ m, rp @ t_local + tp)
        return out

    @staticmethod
    def apply(world, bone, p):
        r, t = world[bone]
        return r @ np.asarray(p, dtype=float) + t

    # -------------------------------------------------------------- the feet

    def sole_points(self, side):
        """The four bottom corners of a foot cube at rest (the base template's foot: leg_w wide, leg_d + toe deep)."""
        e = self.env
        sx = -1.0 if side == "r" else 1.0
        x0, x1 = sx * e["hip_x"] - e["leg_w"] / 2.0, sx * e["hip_x"] + e["leg_w"] / 2.0
        z0, z1 = -e["leg_d"] / 2.0 - e["toe"], e["leg_d"] / 2.0
        return [(x, 0.0, z) for x in (x0, x1) for z in (z0, z1)]

    def soles(self, pose):
        """World positions of both feet's sole corners: {"r": [4 points], "l": [4 points]}."""
        w = self.world(pose, only=("foot_r", "foot_l"))
        return {s: [self.apply(w, "foot_" + s, p) for p in self.sole_points(s)] for s in ("r", "l")}

    def lowest_sole(self, pose):
        return min(float(p[1]) for pts in self.soles(pose).values() for p in pts)

    # -------------------------------------------------------------- the whole body

    def body_bones(self, globs=None):
        if not globs:
            return sorted(self.body)
        return sorted(b for b in self.body if any(fnmatch.fnmatchcase(b, g) for g in globs))

    def body_points(self, pose, globs=None):
        """World positions of the base body's cube corners: {bone: [points]} (the bones matching `globs`, or all)."""
        bones = self.body_bones(globs)
        w = self.world(pose, only=bones)
        return {b: [self.apply(w, b, p) for p in self.body[b]] for b in bones}

    def lowest_body(self, pose, globs=None):
        """The lowest point of the base body (or of the bones matching `globs`): its height and its bone."""
        best, bone = None, None
        for b, pts in self.body_points(pose, globs).items():
            y = min(float(p[1]) for p in pts)
            if best is None or y < best:
                best, bone = y, b
        return best, bone


@functools.lru_cache(maxsize=None)
def for_base(base_id, build="standard"):
    """The bare skeleton of a base (every rig bone, rig pivots)."""
    if base_id not in BASE_IDS:
        raise ValueError(base_id)
    return Skeleton.from_model(Model("_skel_" + base_id, base_id, build))


REFERENCE_BASE = "teen_f"      # the skeleton recipes are expanded and springs simulated on (the middle form)
FORM_BASES = ("chibi_f", "teen_f", "adult_f")   # the three forms leg-length-dependent offsets are solved over
