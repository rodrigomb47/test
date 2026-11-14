"""The named pose library (HEROES_V2.md 13.6): herosrc/anim/poses/*.json.

A pose file is {"poses": {name: pose}}; a pose is
  bones     {bone: [rx, ry, rz]}   file degrees, added to the bone's rest rotation
  position  {bone: [x, y, z]}      file pixels (usually only `root`)
  scale     {bone: [sx, sy, sz]}   (C1) the bone's scale (1 is its size; GeckoLib scales about the pivot): a part
            bone that grows or shrinks, such as Hinae's ring of talismans unfolding from her chest; mirrored with the
            bones (same values), inherited
  extends   another pose's name; its bones are inherited and this pose's own entries override them
  mirror    true: every right-side entry without a left counterpart gets one, mirrored (rx, -ry, -rz; x -> -x),
            and the other way round
  knees     degrees of knee bend with the feet kept in place: thigh -k, shin +2k, foot -k on both legs (added)
  fix       a list of "level_feet" (soles parallel to the ground: foot = -(thigh + shin) about x and -thigh about z),
            "ground" (root y set so the lowest sole corner touches y 0: the compromise over the three forms) and
            "floor" (G4: root y set so the lowest point of the base body touches y 0 on EVERY form: the knee of a
            kneel, the seat of a sit, the back of a lying pose; a form-scaled key, anim/clip.py)
  floor     globs of the bones whose base cubes may touch the floor (default: all of the base body)
  form      {bone: [kx, ky, kz]}  degrees per pixel of FORM_VAR added to that bone's rotation (anim/clip.py): a
            rotation that differs by body form, written as "v + k * v.fc_dhip" like a form-scaled root (polish
            after the art review: the slam folds the hips deeper on the longer forms so the club head reaches the
            ground on all three; rotations otherwise transfer unchanged). Mirrored with the bones; inherited
Conventions (file space, derived from rot.file_matrix and pinned by tests): positive rx leans an upright bone forward
(its top toward -z) and swings a hanging bone backward; a forward arm or leg swing is negative rx; elbow flexion is
negative rx on forearm_*, knee flexion positive rx on shin_*; positive ry turns toward the hero's right; positive rz
tilts an upright bone's top toward the hero's left and swings a hanging bone toward the hero's right (raising arm_r
sideways is positive rz, arm_l negative).
"""
import glob
import os

from rig import mirror_name
from util import HEROSRC, HerogenError, load_json, rel

POSE_DIR = os.path.join(HEROSRC, "anim", "poses")
FIXES = ("level_feet", "ground", "floor")
K_PREFIX = "k:"             # chans[bone]["k:position"]: the FORM_VAR coefficient of a form-scaled position


class Pose:
    def __init__(self, name, rotation=None, position=None, fixes=(), knees=0.0, floor=None, form=None, scale=None):
        self.name = name
        self.rotation = dict(rotation or {})
        self.position = dict(position or {})
        self.scale = dict(scale or {})
        self.fixes = tuple(fixes)
        self.knees = float(knees)
        self.floor = list(floor) if floor else None
        self.form = dict(form or {})

    def chans(self):
        """{bone: {"rotation": v, "position": v, "k:rotation": FORM_VAR coefficients}}"""
        out = {}
        for b, v in self.rotation.items():
            out.setdefault(b, {})["rotation"] = tuple(v)
        for b, v in self.position.items():
            out.setdefault(b, {})["position"] = tuple(v)
        for b, v in self.scale.items():
            out.setdefault(b, {})["scale"] = tuple(v)
        for b, k in self.form.items():
            out.setdefault(b, {}).setdefault("rotation", (0.0, 0.0, 0.0))
            out[b][K_PREFIX + "rotation"] = tuple(k)
        return out


def mirror_rotation(v):
    return (v[0], -v[1], -v[2])


def mirror_position(v):
    return (-v[0], v[1], v[2])


def _mirrored(entries, fn):
    out = dict(entries)
    for k, v in entries.items():
        m = mirror_name(k)
        if m != k and m not in entries:
            out[m] = fn(v)
    return out


def _vec3(v, where):
    if not isinstance(v, (list, tuple)) or len(v) != 3 or not all(isinstance(x, (int, float)) for x in v):
        raise HerogenError("%s: expected [x, y, z], got %r" % (where, v))
    return tuple(float(x) for x in v)


def parse(name, spec, lookup, where="pose"):
    """A Pose from its spec; `lookup(name)` resolves `extends`."""
    for k in spec:
        if k not in ("bones", "position", "scale", "extends", "mirror", "knees", "fix", "floor", "form", "_comment"):
            raise HerogenError("%s %s: unknown field %r" % (where, name, k))
    rot = {b: _vec3(v, "%s %s.bones.%s" % (where, name, b)) for b, v in (spec.get("bones") or {}).items()}
    pos = {b: _vec3(v, "%s %s.position.%s" % (where, name, b)) for b, v in (spec.get("position") or {}).items()}
    form = {b: _vec3(v, "%s %s.form.%s" % (where, name, b)) for b, v in (spec.get("form") or {}).items()}
    scale = {b: _vec3(v, "%s %s.scale.%s" % (where, name, b)) for b, v in (spec.get("scale") or {}).items()}
    for b, v in scale.items():
        if min(v) <= 0:
            raise HerogenError("%s %s.scale.%s: a scale must be above 0, got %r" % (where, name, b, list(v)))
    if spec.get("mirror"):
        rot = _mirrored(rot, mirror_rotation)
        pos = _mirrored(pos, mirror_position)
        form = _mirrored(form, mirror_rotation)
        scale = _mirrored(scale, tuple)
    fixes = list(spec.get("fix", []))
    for f in fixes:
        if f not in FIXES:
            raise HerogenError("%s %s: fix %r is unknown (%s)" % (where, name, f, ", ".join(FIXES)))
    knees = float(spec.get("knees", 0.0))
    floor = spec.get("floor")
    if spec.get("extends"):
        base = lookup(spec["extends"])
        rot = dict(base.rotation, **rot)
        pos = dict(base.position, **pos)
        form = dict(base.form, **form)
        scale = dict(base.scale, **scale)
        inherited = [f for f in base.fixes if not (f == "ground" and "floor" in fixes)
                     and not (f == "floor" and "ground" in fixes)]     # a pose's own placement fix wins
        fixes = inherited + [f for f in fixes if f not in inherited]
        knees = knees if "knees" in spec else base.knees
        floor = floor if floor is not None else base.floor
    if "floor" in fixes and "ground" in fixes:
        raise HerogenError("%s %s: fix ground and floor exclude each other" % (where, name))
    return Pose(name, rot, pos, fixes, knees, floor, form, scale)


class Library:
    """Every pose of herosrc/anim/poses (and any extra specs, such as a hero's own `poses`)."""

    def __init__(self, specs=None, where="herosrc/anim/poses"):
        self.specs = dict(specs or {})
        self.where = where
        self._done = {}
        self._busy = set()

    @classmethod
    def load(cls, directory=POSE_DIR):
        specs = {}
        for path in sorted(glob.glob(os.path.join(directory, "*.json"))):
            for name, spec in load_json(path).get("poses", {}).items():
                if name in specs:
                    raise HerogenError("%s: pose %s is defined twice" % (rel(path), name))
                specs[name] = spec
        return cls(specs)

    def with_specs(self, extra, where):
        lib = Library(dict(self.specs), where)
        for name, spec in (extra or {}).items():
            lib.specs[name] = spec
        return lib

    def get(self, name):
        if name in self._done:
            return self._done[name]
        if name not in self.specs:
            raise HerogenError("pose %r is unknown" % name)
        if name in self._busy:
            raise HerogenError("pose %s extends itself" % name)
        self._busy.add(name)
        pose = parse(name, self.specs[name], self.get, "pose")
        self._busy.discard(name)
        self._done[name] = pose
        return pose

    def names(self):
        return sorted(self.specs)


def resolve(pose, skeletons):
    """The pose's channels with its knee bend and fixes applied; `skeletons`: one per body form (or one)."""
    if not isinstance(skeletons, (list, tuple)):
        skeletons = [skeletons]
    chans = pose.chans()

    def rot(b):
        return list(chans.get(b, {}).get("rotation", (0.0, 0.0, 0.0)))

    if pose.knees:
        k = pose.knees
        for s in ("r", "l"):
            for bone, f in (("leg_" + s, -1.0), ("shin_" + s, 2.0), ("foot_" + s, -1.0)):
                v = rot(bone)
                v[0] += f * k
                chans.setdefault(bone, {})["rotation"] = tuple(v)
    if "level_feet" in pose.fixes:
        for s in ("r", "l"):
            leg, shin = rot("leg_" + s), rot("shin_" + s)
            foot = rot("foot_" + s)
            chans.setdefault("foot_" + s, {})["rotation"] = (-(leg[0] + shin[0]), foot[1], -(leg[2] + shin[2]))
    if "ground" in pose.fixes:
        root = list(chans.get("root", {}).get("position", (0.0, 0.0, 0.0)))
        root[1] = 0.0
        chans.setdefault("root", {})["position"] = tuple(root)
        lows = [sk.lowest_sole(chans) for sk in skeletons]
        root[1] = -0.5 * (min(lows) + max(lows))
        chans["root"]["position"] = tuple(root)
    if "floor" in pose.fixes:
        a, b, _ = floor_fit(chans, skeletons, pose.floor)
        root = list(chans.get("root", {}).get("position", (0.0, 0.0, 0.0)))
        root[1] += a
        chans.setdefault("root", {})["position"] = tuple(root)
        chans["root"][K_PREFIX + "position"] = (0.0, b, 0.0)
    return chans


def floor_fit(chans, skeletons, globs=None):
    """(a, b, worst residual): the root lift a + b x FORM_VAR that puts the lowest base-body point of the pose on
    y 0, fitted over the forms' exact lifts (least squares; exact for two forms, within the residual for three)."""
    rows = []
    for sk in skeletons:
        low, _ = sk.lowest_body(chans, globs)
        rows.append((sk.d, -low))
    if len(rows) == 1:
        return rows[0][1], 0.0, 0.0
    n = float(len(rows))
    md = sum(d for d, _ in rows) / n
    my = sum(y for _, y in rows) / n
    var = sum((d - md) ** 2 for d, _ in rows)
    b = sum((d - md) * (y - my) for d, y in rows) / var if var > 1e-12 else 0.0
    a = my - b * md
    worst = max(abs(a + b * d - y) for d, y in rows)
    return a, b, worst
