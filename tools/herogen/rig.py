"""Rig v3, the base bodies (three forms x two families, each in three builds) and the in-memory model (bones and
cubes) the other modules fill.

Coordinates are Bedrock model pixels as written to the .geo.json: feet at y 0, the hero faces north (-z), and the
hero's RIGHT side is -x (Bedrock and GeckoLib mirror x when they bake, so -x in the file is the hero's right on screen).
Cube origins and bone pivots are absolute model coordinates, as in Bedrock geometry.

A base is a form (chibi, teen, adult: the heights) and a family (female, male: the widths), in a build (slim,
standard, large: width overrides). The head is the same 10 px cube on every form, so the face planes, the face
texture layout and head-anchored parts (hair, ears, hats: `offset` [0, "head_y", 0]) fit every base. Rig v3 (the
second style pass) keeps v2's bone names and the 10 px head, makes the bodies longer and draws the whole model at the
rig's `render.scale` (0.56), so heads read about vanilla-sized on bodies of anime proportions.
"""
import fnmatch
import os
from dataclasses import dataclass, field
from typing import Optional

from expr import evaluate
from util import HEROSRC, HerogenError, load_json

FACES = ("north", "south", "east", "west", "up", "down")
SIDE_FACES = ("north", "south", "east", "west")
FORMS = ("chibi", "teen", "adult")
FAMILIES = ("female", "male")
BUILDS = ("slim", "standard", "large")
BASE_IDS = ("chibi_f", "chibi_m", "teen_f", "teen_m", "adult_f", "adult_m")
RIG_FILE = "rig_v3.json"
HEIGHT_KEYS = ("ankle_y", "knee_y", "hip_y", "waist_y", "chest_y", "chest_top", "head_y", "arm_top", "shoulder_y",
               "elbow_y", "wrist_y", "hand_bottom", "hand_y", "toe")
BASE_SLOTS = ("skin", "base_top", "base_bottom", "shoes")
DEFAULT_BASE_LAYER = {"skin": "skin", "base_top": "secondary", "base_bottom": "accent", "shoes": "leather"}

_RIG_CACHE = {}


def load_rig(path=None):
    path = path or os.path.join(HEROSRC, "rig", RIG_FILE)
    if path not in _RIG_CACHE:
        rig = load_json(path)
        names = [b["name"] for b in rig["bones"]]
        if len(set(names)) != len(names):
            raise HerogenError("rig: duplicate bone names")
        for b in rig["bones"]:
            if b["parent"] is not None and b["parent"] not in names:
                raise HerogenError("rig: bone %s has unknown parent %s" % (b["name"], b["parent"]))
        _RIG_CACHE[path] = rig
    return _RIG_CACHE[path]


def required_bones(rig=None):
    rig = rig or load_rig()
    return [b["name"] for b in rig["bones"] if b.get("required")]


def base_id_for(form, family):
    if form not in FORMS:
        raise HerogenError("form %r is unknown (%s)" % (form, ", ".join(FORMS)))
    if family not in FAMILIES:
        raise HerogenError("family %r is unknown (%s)" % (family, ", ".join(FAMILIES)))
    return "%s_%s" % (form, family[0])


def load_base(base_id, build="standard"):
    """A base's file with the build's width overrides applied to its dims."""
    if base_id not in BASE_IDS:
        raise HerogenError("base %r is unknown (bases: %s)" % (base_id, ", ".join(BASE_IDS)))
    if build not in BUILDS:
        raise HerogenError("build %r is unknown (%s)" % (build, ", ".join(BUILDS)))
    base = load_json(os.path.join(HEROSRC, "bases", base_id + ".json"))
    template = load_json(os.path.join(HEROSRC, "bases", base.get("template", "_template") + ".json"))
    base["cubes"] = template["cubes"] + base.get("extra_cubes", [])
    over = (base.get("builds") or {}).get(build, {}) if build != "standard" else {}
    for k in over:
        if k in HEIGHT_KEYS:
            raise HerogenError("base %s: build %s may change widths only, not %s" % (base_id, build, k))
    base["dims"] = dict(base["dims"], **over)
    base["build"] = build
    return base


def base_env(base, rig=None):
    """Expression names of a base: its dims plus the head facts (head_s, head_top, height)."""
    rig = rig or load_rig()
    env = {k: float(v) for k, v in base["dims"].items()}
    size = float(rig["head"]["size"])
    env["head_s"] = size
    env["head_top"] = env["head_y"] + size
    env["height"] = env["head_top"]
    env["render_scale"] = float(rig.get("render", {}).get("scale", 1.0))
    return env


def render_scale(rig=None):
    """Model pixels to world pixels (the renderer scales the whole model by it, on top of art.scale)."""
    rig = rig or load_rig()
    return float(rig.get("render", {}).get("scale", 1.0))


def mirror_name(name):
    """arm_r -> arm_l, upper_arm_r -> upper_arm_l, ear_l_tip -> ear_r_tip; names without a side are unchanged."""
    for a, b in (("_r", "_l"), ("_l", "_r")):
        if name.endswith(a):
            return name[:-len(a)] + b
    for a, b in (("_r_", "_l_"), ("_l_", "_r_")):
        if a in name:
            return name.replace(a, b, 1)
    return name


def side_of(name):
    if name.endswith("_r") or "_r_" in name:
        return "r"
    if name.endswith("_l") or "_l_" in name:
        return "l"
    return None


@dataclass
class Bone:
    name: str
    parent: Optional[str]
    pivot: tuple
    rotation: tuple = (0.0, 0.0, 0.0)
    required: bool = False
    kind: str = "bone"
    source: str = "rig"


@dataclass
class Cube:
    name: str
    bone: str
    origin: tuple
    size: tuple
    slot: str
    material: str
    part: str = "base"
    inflate: float = 0.0
    rotation: Optional[tuple] = None
    pivot: Optional[tuple] = None
    faces: tuple = FACES
    fixed: dict = field(default_factory=dict)     # face -> uv_template region name
    density: int = 1
    pattern: Optional[dict] = None
    shade: Optional[str] = None                   # extra light ramp: "vertical" (top lit, tip dark)
    cutout: Optional[str] = None
    taper: Optional[float] = None
    trim: Optional[dict] = None
    glow: bool = False
    tone: int = 0
    outline: bool = True                          # dark hem row and lower band at the bottom of side faces
    uv: dict = field(default_factory=dict)        # face -> (u, v, w, h) in whole texels, net orientation
    order: int = 0

    @property
    def box(self):
        (x, y, z), (sx, sy, sz) = self.origin, self.size
        return (x, y, z, x + sx, y + sy, z + sz)

    @property
    def flat_axis(self):
        for i, s in enumerate(self.size):
            if abs(s) < 1e-9:
                return i
        return None


class Model:
    """Bones and cubes of one hero (or one bare base), before UV packing and painting."""

    def __init__(self, hero_id, base_id, build="standard", rig=None):
        self.hero_id = hero_id
        self.rig = rig or load_rig()
        self.base = load_base(base_id, build)
        self.base_id = base_id
        self.build = build
        self.form = self.base["form"]
        self.family = self.base["family"]
        self.env = base_env(self.base, self.rig)
        self.catalogue = {}            # every rig bone, pivots resolved
        self.rig_order = []
        for b in self.rig["bones"]:
            pivot = tuple(evaluate(b["pivot"], self.env))
            self.catalogue[b["name"]] = Bone(b["name"], b["parent"], pivot, required=bool(b.get("required")),
                                             kind=b.get("kind", "bone"))
            self.rig_order.append(b["name"])
        self.bones = {}                # active bones
        self.extra_order = []          # bones declared by parts, in declaration order
        self.cubes = []
        self.paint_ops = []
        self.secondary = []
        self.occupied = {}
        self.parts_used = []
        self.notes = []
        self.tex_w, self.tex_h = self.rig["budgets"]["textures"][0]
        for name in self.rig_order:
            if self.catalogue[name].required:
                self.use_bone(name)
        self._add_base_cubes()

    # -------------------------------------------------------------- bones

    def use_bone(self, name):
        """Activate a rig or declared bone and its ancestors."""
        if name in self.bones:
            return self.bones[name]
        if name not in self.catalogue:
            raise HerogenError("bone %r is neither a rig bone nor declared by a part" % name)
        b = self.catalogue[name]
        if b.parent is not None:
            self.use_bone(b.parent)
        self.bones[name] = b
        return b

    def declare_bone(self, name, parent, pivot, source, rotation=(0.0, 0.0, 0.0)):
        if name in self.catalogue:
            raise HerogenError("%s: bone %r already exists" % (source, name))
        self.use_bone(parent)
        b = Bone(name, parent, tuple(pivot), tuple(rotation), source=source)
        self.catalogue[name] = b
        self.extra_order.append(name)
        self.bones[name] = b
        return b

    def nested_bone(self, prefix, name, source):
        """A visibility bone (fine_, p2_, ...) under `name` with the same pivot."""
        child = prefix + name
        if child not in self.catalogue:
            parent = self.use_bone(name)
            self.declare_bone(child, name, parent.pivot, source)
        return self.use_bone(child)

    def ordered_bones(self):
        """Parents before children: rig order first, then part bones in declaration order, depth first."""
        children = {}
        order_index = {n: i for i, n in enumerate(self.rig_order + self.extra_order)}
        for b in self.bones.values():
            children.setdefault(b.parent, []).append(b.name)
        out = []

        def walk(n):
            out.append(self.bones[n])
            for c in sorted(children.get(n, []), key=lambda k: order_index[k]):
                walk(c)

        for r in sorted(children.get(None, []), key=lambda k: order_index[k]):
            walk(r)
        return out

    # -------------------------------------------------------------- cubes

    def add_cube(self, cube):
        if any(c.name == cube.name for c in self.cubes):
            raise HerogenError("cube name %r is used twice" % cube.name)
        self.use_bone(cube.bone)
        cube.order = len(self.cubes)
        self.cubes.append(cube)
        return cube

    def cube(self, name):
        for c in self.cubes:
            if c.name == name:
                return c
        raise KeyError(name)

    def cubes_matching(self, patterns):
        return [c for c in self.cubes if any(fnmatch.fnmatchcase(c.name, p) for p in patterns)]

    def _add_base_cubes(self):
        for spec in self.base["cubes"]:
            origin = tuple(evaluate(spec["origin"], self.env))
            size = tuple(evaluate(spec["size"], self.env))
            if spec.get("skip_if_empty") and any(abs(s) < 1e-9 for s in size):
                continue
            variants = [(spec["name"], spec["bone"], origin)]
            if spec.get("mirror"):
                variants.append((mirror_name(spec["name"]), mirror_name(spec["bone"]),
                                 (-(origin[0] + size[0]), origin[1], origin[2])))
            for name, bone, org in variants:
                self.add_cube(Cube(name=name, bone=bone, origin=org, size=size, slot=spec["slot"],
                                   material=spec["material"], part="base", fixed=dict(spec.get("fixed", {}))))
        face = self.rig["face"]
        hy = self.env["head_y"]
        for plane in ("eyes", "mouth", "cheeks"):
            p = face["planes"][plane]
            o = p["origin"]                      # head-local: y 0 is the head's bottom
            self.add_cube(Cube(name=plane, bone=plane, origin=(o[0], o[1] + hy, o[2]), size=tuple(p["size"]),
                               slot="skin", material="face", part="base", faces=("north",),
                               fixed={"north": plane}, density=face["density"]))

    # -------------------------------------------------------------- facts

    def bone_names(self):
        return [b.name for b in self.ordered_bones()]

    def phase_bones(self):
        out = {}
        for n in self.bones:
            for p in (1, 2, 3):
                if n.startswith("p%d_" % p):
                    out[n] = p
        return out

    def fine_bones(self):
        return sorted(n for n in self.bones if n.startswith("fine_"))

    def tint_bones(self):
        return sorted(n for n in self.bones if n == "sash" or n.startswith("fine_sash"))
