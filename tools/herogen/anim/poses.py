"""The named pose library (HEROES_V2.md 13.6): herosrc/anim/poses/*.json.

A pose file is {"poses": {name: pose}}; a pose is
  bones     {bone: [rx, ry, rz]}   file degrees, added to the bone's rest rotation
  position  {bone: [x, y, z]}      file pixels (usually only `root`)
  extends   another pose's name; its bones are inherited and this pose's own entries override them
  mirror    true: every right-side entry without a left counterpart gets one, mirrored (rx, -ry, -rz; x -> -x),
            and the other way round
  knees     degrees of knee bend with the feet kept in place: thigh -k, shin +2k, foot -k on both legs (added)
  fix       a list of "level_feet" (soles parallel to the ground: foot = -(thigh + shin) about x and -thigh about z)
            and "ground" (root y set so the lowest sole corner touches y 0: the compromise over the three forms)
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
FIXES = ("level_feet", "ground")


class Pose:
    def __init__(self, name, rotation=None, position=None, fixes=(), knees=0.0):
        self.name = name
        self.rotation = dict(rotation or {})
        self.position = dict(position or {})
        self.fixes = tuple(fixes)
        self.knees = float(knees)

    def chans(self):
        """{bone: {"rotation": v, "position": v}}"""
        out = {}
        for b, v in self.rotation.items():
            out.setdefault(b, {})["rotation"] = tuple(v)
        for b, v in self.position.items():
            out.setdefault(b, {})["position"] = tuple(v)
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
        if k not in ("bones", "position", "extends", "mirror", "knees", "fix"):
            raise HerogenError("%s %s: unknown field %r" % (where, name, k))
    rot = {b: _vec3(v, "%s %s.bones.%s" % (where, name, b)) for b, v in (spec.get("bones") or {}).items()}
    pos = {b: _vec3(v, "%s %s.position.%s" % (where, name, b)) for b, v in (spec.get("position") or {}).items()}
    if spec.get("mirror"):
        rot = _mirrored(rot, mirror_rotation)
        pos = _mirrored(pos, mirror_position)
    fixes = list(spec.get("fix", []))
    for f in fixes:
        if f not in FIXES:
            raise HerogenError("%s %s: fix %r is unknown (%s)" % (where, name, f, ", ".join(FIXES)))
    knees = float(spec.get("knees", 0.0))
    if spec.get("extends"):
        base = lookup(spec["extends"])
        rot = dict(base.rotation, **rot)
        pos = dict(base.position, **pos)
        fixes = list(base.fixes) + [f for f in fixes if f not in base.fixes]
        knees = knees if "knees" in spec else base.knees
    return Pose(name, rot, pos, fixes, knees)


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
    return chans
