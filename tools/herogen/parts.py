"""The parts library (design 13.3): small JSON files that add bones and cubes to the model and paint over the base
body. A hero's art.parts entry picks a part and maps its logical colour slots to palette slots.

Part file fields:
  id, category, params {name: {min, max, default}}, slots {main|trim|alt|detail: default palette slot},
  occupies [...], conflicts [glob...], bones [{name, parent, pivot}], cubes [...], paint [...],
  locators {bone: [x, y, z]}, secondary {chain: [...], stiffness, damping},
  offset [x, y, z] (added to every cube origin and pivot, bone pivot, locator and paint range: hair, ears and hats
  are authored head-local with offset [0, "head_y", 0], so y 0 is the chin, the face is at z -5 and the head spans
  x and z -5..5, y 0..10 on every form), density (texels per pixel for the part's cubes, default 1; 2 for parts on
  the head, which is drawn at double density).
Cube fields: bone, origin, size (numbers or expressions over params and base dims), slot, material, pattern, cutout,
  taper, trim {slot, edge, width}, mirror (build the left copy from the right), fine (own fine_ bone for level of
  detail), inflate, rotation + pivot (cube rotation, degrees), faces (subset), glow, tone, name, density, shade
  ("vertical": lit top, darker tip, the default for hair; "flat": none; "mass": no vertical shading, no hem outline
  and the top face lit like the sides, for the stacked slabs of one solid such as an ear, whose dark 1 px sides and
  lit ledges would otherwise read as stripes), skip_if_empty (leave the cube out when a param makes one of its sizes
  0, for optional pieces such as an ahoge), outline (false: no dark hem row, for a
  segment that continues into the next one).
Paint op fields (paint over existing cubes, usually the base body): cubes [glob...], faces [...], y [lo, hi],
  x [lo, hi], z [lo, hi], abs_x [lo, hi] (|x|, for symmetric regions), side r|l, vee [y_tip, slope] (only where
  |x| <= (y - y_tip) * slope: a V neckline), slot, material, pattern, trim {slot, edge, width}, tone. Ranges are
  model pixels at rest, lower bound inclusive.
Generators (style pass 2, gen.py): `strands` (rows of rotated strands, panels, frills), `chains` (bones along a
  polyline with a box per segment: big tails) and `groups` (cubes sharing one rotation and pivot).
Weapon parts (category weapons) are authored in grip space (grip centre at 0,0,0, blade along -z, as a held item at
  rest) with cube roles hilt or blade, a scabbard cube list and a tip point; see apply_weapon.
"""
import fnmatch
import os

import gen
from expr import evaluate
from palette import FIXED, SLOTS
from rig import BASE_SLOTS, FACES, Bone, Cube, mirror_name
from util import HEROSRC, HerogenError, load_json

LOGICAL_SLOTS = ("main", "trim", "alt", "detail")
MATERIALS = ("cloth", "skin", "hair", "metal", "leather", "fur", "scale", "wood", "paper", "lacquer", "bone", "gem",
             "glow")
PATTERNS = ("stripes", "checks", "plaid", "dots", "pleats")
TONE_PATTERNS = ("pleats",)          # patterns that only shade, without a second colour
CUTOUT_PREFIXES = ("jagged_", "tip_jagged_", "bangs_jagged_")
CUTOUTS = ("tri_up", "tri_down", "peak", "round_bottom", "round_top", "fur_tips", "torn")
SHADES = ("vertical", "flat", "mass")


def part_path(part_id):
    if "/" not in part_id or ".." in part_id:
        raise HerogenError("part id %r must be <category>/<name>" % part_id)
    return os.path.join(HEROSRC, "parts", part_id + ".json")


def load_part(part_id):
    part = load_json(part_path(part_id))
    if part.get("id") != part_id:
        raise HerogenError("part %s: id field is %r" % (part_id, part.get("id")))
    return part


def _check_cutout(name, where):
    if name is None:
        return
    if name in CUTOUTS:
        return
    for p in CUTOUT_PREFIXES:
        if name.startswith(p) and name[len(p):].isdigit():
            return
    raise HerogenError("%s: cutout %r is unknown" % (where, name))


class _Ctx:
    """Resolves one part entry: params, slot mapping, phase prefix."""

    def __init__(self, model, part, entry, index):
        self.model = model
        self.part = part
        self.entry = entry
        self.pid = part["id"]
        self.where = "part %s (art.parts[%d])" % (self.pid, index)
        self.env = dict(model.env)
        given = entry.get("params", {}) or {}
        for k in given:
            if k not in part.get("params", {}):
                raise HerogenError("%s: unknown param %r" % (self.where, k))
        for k, spec in part.get("params", {}).items():
            v = float(given.get(k, spec["default"]))
            if not spec.get("min", v) <= v <= spec.get("max", v):
                raise HerogenError("%s: param %s = %g is outside %g..%g" % (self.where, k, v, spec["min"], spec["max"]))
            self.env[k] = v
        self.slots = dict(part.get("slots", {}))
        for logical, key in (("main", "color"), ("trim", "trim"), ("alt", "alt"), ("detail", "detail")):
            if entry.get(key):
                self.slots[logical] = entry[key]
        phase = entry.get("phase")
        if phase not in (None, 1, 2, 3):
            raise HerogenError("%s: phase must be 1, 2 or 3" % self.where)
        self.prefix = "p%d_" % phase if phase else ""
        self.renames = {}
        self.index = index
        off = part.get("offset", [0, 0, 0])
        self.offset = tuple(evaluate(off, self.env))
        if len(self.offset) != 3:
            raise HerogenError("%s: offset must be [x, y, z]" % self.where)
        self.density = int(part.get("density", 1))
        if self.density not in (1, 2):
            raise HerogenError("%s: density must be 1 or 2" % self.where)

    def slot(self, name):
        name = self.slots.get(name, name)
        if name in SLOTS or name in FIXED or name in BASE_SLOTS:
            return name
        if name in LOGICAL_SLOTS:
            raise HerogenError("%s: slot %r has no colour (set it in the part's slots or the hero entry)"
                               % (self.where, name))
        else:
            raise HerogenError("%s: slot %r is not a palette slot" % (self.where, name))
        return name

    def ev(self, v):
        return evaluate(v, self.env)

    def at(self, v):
        """A point expression moved by the part's offset."""
        x, y, z = evaluate(v, self.env)
        return (x + self.offset[0], y + self.offset[1], z + self.offset[2])

    def pattern(self, spec):
        if spec is None:
            return None
        if isinstance(spec, str):
            spec = {"type": spec}
        spec = dict(spec)
        if spec.get("type") not in PATTERNS:
            raise HerogenError("%s: pattern %r is unknown (patterns: %s)" % (self.where, spec.get("type"),
                                                                         ", ".join(PATTERNS)))
        if spec["type"] in TONE_PATTERNS:
            spec["slots"] = []
            return spec
        colours = self.entry.get("pattern_colors")
        slots = spec.get("slots") or colours or ["alt"]
        spec["slots"] = [self.slot(s) for s in slots]
        return spec

    def trim(self, spec):
        if spec is None:
            return None
        spec = dict(spec)
        spec["slot"] = self.slot(spec.get("slot", "trim"))
        spec.setdefault("edge", "bottom")
        spec.setdefault("width", 1)
        if spec["edge"] not in ("bottom", "top", "both", "all"):
            raise HerogenError("%s: trim edge %r is unknown" % (self.where, spec["edge"]))
        if "material" in spec and spec["material"] not in MATERIALS:
            raise HerogenError("%s: material %r is unknown" % (self.where, spec["material"]))
        return spec


def _material(ctx, m):
    if m not in MATERIALS:
        raise HerogenError("%s: material %r is unknown (materials: %s)" % (ctx.where, m, ", ".join(MATERIALS)))
    return m


def _declare_one(ctx, name, parent, pivot):
    """Declare a part bone, or move a rig bone's pivot (it must keep its rig parent)."""
    model = ctx.model
    parent = ctx.renames.get(parent, parent)
    if ctx.prefix:
        new = ctx.prefix + name
        ctx.renames[name] = new
        model.declare_bone(new, parent, pivot, ctx.where)
    elif name in model.catalogue and model.catalogue[name].source == "rig":
        rigb = model.catalogue[name]
        if rigb.parent != parent:
            raise HerogenError("%s: rig bone %s must keep its parent %s" % (ctx.where, name, rigb.parent))
        rigb.pivot = tuple(pivot)
        model.use_bone(name)
    else:
        model.declare_bone(name, parent, pivot, ctx.where)


def _declare_bones(ctx):
    for b in ctx.part.get("bones", []):
        _declare_one(ctx, b["name"], b["parent"], ctx.at(b["pivot"]))


def _target_bone(ctx, bone, fine):
    model = ctx.model
    if bone in ctx.renames:
        bone = ctx.renames[bone]
    elif ctx.prefix:
        bone = model.nested_bone(ctx.prefix, bone, ctx.where).name
    if fine and not bone.startswith("fine_"):
        bone = model.nested_bone("fine_", bone, ctx.where).name
    model.use_bone(bone)
    return bone


def _mirror_bone(ctx, bone):
    """The left counterpart of a right-side bone; part-declared bones are mirrored on demand."""
    model = ctx.model
    m = mirror_name(bone)
    if m == bone or m in model.catalogue:
        model.use_bone(m)
        return m
    src = model.catalogue[bone]
    parent = mirror_name(src.parent) if src.parent else None
    if parent not in model.catalogue:
        parent = src.parent
    model.declare_bone(m, parent, (-src.pivot[0], src.pivot[1], src.pivot[2]), ctx.where)
    return m


def _cube_from_spec(ctx, spec, n, bone, origin, size, name_suffix=""):
    faces = tuple(spec.get("faces", FACES))
    for f in faces:
        if f not in FACES:
            raise HerogenError("%s: face %r is unknown" % (ctx.where, f))
    _check_cutout(spec.get("cutout"), ctx.where)
    rotation = tuple(ctx.ev(spec["rotation"])) if "rotation" in spec else None
    pivot = ctx.at(spec["pivot"]) if "pivot" in spec else None
    if rotation is not None and pivot is None:
        raise HerogenError("%s: a rotated cube needs a pivot" % ctx.where)
    shade = spec.get("shade")
    if shade is not None and shade not in SHADES:
        raise HerogenError("%s: shade %r is unknown (%s)" % (ctx.where, shade, ", ".join(SHADES)))
    density = int(spec.get("density", ctx.density))
    if density not in (1, 2):
        raise HerogenError("%s: density must be 1 or 2" % ctx.where)
    return Cube(name=spec.get("name", "%s#%d" % (ctx.pid, n)) + name_suffix, bone=bone, origin=tuple(origin),
                size=tuple(size), slot=ctx.slot(spec.get("slot", "main")),
                material=_material(ctx, spec.get("material", "cloth")), part=ctx.pid,
                inflate=float(ctx.ev(spec.get("inflate", 0))), rotation=rotation, pivot=pivot, faces=faces,
                pattern=ctx.pattern(spec.get("pattern")), cutout=spec.get("cutout"),
                taper=float(spec["taper"]) if "taper" in spec else None, trim=ctx.trim(spec.get("trim")),
                glow=bool(spec.get("glow")) or spec.get("material") == "glow", tone=int(spec.get("tone", 0)),
                density=density, shade=shade, outline=bool(spec.get("outline", True)))


def _place(ctx, spec, name, bone, origin, size, rotation=None, pivot=None, mirror=False):
    """Add one cube (and its mirrored copy) to the model."""
    model = ctx.model
    bone = _target_bone(ctx, bone, spec.get("fine"))
    spec = dict(spec, name=name)
    cube = _cube_from_spec(ctx, spec, 0, bone, origin, size)
    if rotation is not None:
        cube.rotation, cube.pivot = tuple(rotation), tuple(pivot)
    if ctx.prefix:
        cube.name = ctx.prefix + cube.name
    model.add_cube(cube)
    if mirror:
        mbone = _mirror_bone(ctx, bone)
        morigin = (-(origin[0] + size[0]), origin[1], origin[2])
        mc = _cube_from_spec(ctx, spec, 0, mbone, morigin, size, ":l")
        if cube.rotation is not None:
            mc.rotation = (cube.rotation[0], -cube.rotation[1], -cube.rotation[2])
            mc.pivot = (-cube.pivot[0], cube.pivot[1], cube.pivot[2])
        if ctx.prefix:
            mc.name = ctx.prefix + mc.name
        model.add_cube(mc)


def _add_cubes(ctx):
    specs = list(ctx.part.get("cubes", [])) + gen.groups(ctx)
    for n, spec in enumerate(specs):
        origin = ctx.at(spec["origin"])
        size = ctx.ev(spec["size"])
        if any(s < 0 for s in size):
            raise HerogenError("%s: cube %d has a negative size" % (ctx.where, n))
        if spec.get("skip_if_empty") and any(abs(s) < 1e-9 for s in size):
            continue                       # an optional piece switched off by a param (an ahoge of height 0)
        rotation = tuple(ctx.ev(spec["rotation"])) if "rotation" in spec else None
        pivot = ctx.at(spec["pivot"]) if "pivot" in spec else None
        if rotation is not None and pivot is None:
            raise HerogenError("%s: a rotated cube needs a pivot" % ctx.where)
        name = "%s#%s" % (ctx.pid, spec["name"]) if "name" in spec else "%s#%d" % (ctx.pid, n)
        _place(ctx, spec, name, spec["bone"], origin, size, rotation, pivot, bool(spec.get("mirror")))

    def add(spec, origin, size, rotation, pivot, bone, name, mirror):
        _place(ctx, spec, name, bone, origin, size, rotation, pivot, mirror)

    gen.chains(ctx, lambda name, parent, pivot: _declare_one(ctx, name, parent, pivot), add)
    gen.strands(ctx, add)


def _paint_ops(ctx):
    model = ctx.model
    for n, op in enumerate(ctx.part.get("paint", [])):
        rec = {"part": ctx.pid, "cubes": list(op.get("cubes", ["*"])), "faces": op.get("faces"),
               "slot": ctx.slot(op.get("slot", "main")), "material": _material(ctx, op.get("material", "cloth")),
               "pattern": ctx.pattern(op.get("pattern")), "side": op.get("side"), "tone": int(op.get("tone", 0)),
               "trim": ctx.trim(op.get("trim")), "order": (ctx.index, n)}
        for k, axis in enumerate(("x", "y", "z")):
            rec[axis] = tuple(v + ctx.offset[k] for v in ctx.ev(op[axis])) if axis in op else None
        rec["abs_x"] = tuple(ctx.ev(op["abs_x"])) if "abs_x" in op else None
        if "vee" in op:
            y_tip, slope = ctx.ev(op["vee"])
            rec["vee"] = (y_tip + ctx.offset[1], slope)
        else:
            rec["vee"] = None
        if rec["faces"]:
            for f in rec["faces"]:
                if f not in FACES:
                    raise HerogenError("%s: face %r is unknown" % (ctx.where, f))
        if not model.cubes_matching(rec["cubes"]):
            raise HerogenError("%s: paint op %d matches no cube (%s)" % (ctx.where, n, ", ".join(rec["cubes"])))
        model.paint_ops.append(rec)


def _check_tags(ctx):
    model = ctx.model
    for pattern in ctx.part.get("conflicts", []):
        for used in model.parts_used:
            if fnmatch.fnmatchcase(used, pattern):
                raise HerogenError("%s conflicts with %s" % (ctx.where, used))
    for used in model.parts_used:
        other = load_part(used) if not used.startswith("inline/") else {}
        for pattern in other.get("conflicts", []):
            if fnmatch.fnmatchcase(ctx.pid, pattern):
                raise HerogenError("%s conflicts with %s" % (ctx.where, used))
    for tag in ctx.part.get("occupies", []):
        if tag in model.occupied and not ctx.prefix:
            raise HerogenError("%s: %s is already taken by %s" % (ctx.where, tag, model.occupied[tag]))
        model.occupied.setdefault(tag, ctx.pid)


def apply_part(model, entry, index):
    """Apply one art.parts entry ({part: id or inline object, color, trim, alt, detail, params, phase, ...})."""
    if isinstance(entry.get("part"), dict):
        part = dict(entry["part"])
        part.setdefault("id", "inline/%d" % index)
        if not part["id"].startswith("inline/"):
            raise HerogenError("art.parts[%d]: an inline part's id must start with inline/" % index)
    else:
        part = load_part(entry["part"])
    if part.get("category") == "weapons":
        raise HerogenError("art.parts[%d]: weapons are placed through art.weapon" % index)
    ctx = _Ctx(model, part, entry, index)
    _check_tags(ctx)
    _declare_bones(ctx)
    _add_cubes(ctx)
    _paint_ops(ctx)
    for name, pos in part.get("locators", {}).items():
        b = model.use_bone(name)
        if b.kind != "locator":
            raise HerogenError("%s: %s is not a locator" % (ctx.where, name))
        b.pivot = ctx.at(pos)
    if part.get("secondary"):
        sec = dict(part["secondary"])
        sec["chain"] = [ctx.renames.get(c, c) for c in sec.get("chain", [])]
        sec["part"] = ctx.pid
        model.secondary.append(sec)
    model.parts_used.append(ctx.pid)
    return ctx


# ------------------------------------------------------------------ weapons

def _grip_box(spec, ctx):
    o = ctx.ev(spec["origin"])
    s = ctx.ev(spec["size"])
    return o, s


def _transform_box(o, s, mode):
    """Grip-space box -> axis-permuted box. 'hand': as authored; 'down': blade points down; 'up': blade points up."""
    x0, y0, z0 = o
    x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    if mode == "hand":
        return (x0, y0, z0), (s[0], s[1], s[2])
    if mode == "down":      # (x, y, z) -> (x, z, -y): forward (-z) becomes down
        return (x0, z0, -y1), (s[0], s[2], s[1])
    if mode == "up":        # (x, y, z) -> (x, -z, y): forward (-z) becomes up
        return (x0, -z1, y0), (s[0], s[2], s[1])
    raise ValueError(mode)


SHEATH = {
    # mount point (absolute), axis mode, cube rotation (file degrees about the mount point), parent of weapon_back.
    # GeckoLib bakes file rotations as (-x, -y, z) in its x-mirrored space, so +55 about x swings a hanging blade
    # backward, and -35 about z puts a back-carried hilt over the hero's right shoulder. A hip scabbard hangs from the
    # hips (`body`), so a waist twist or chest lean does not swing it through the leg; a back carry follows the chest.
    # Heights follow the form (the waist line for a hip sheath, mid-chest for a back carry).
    "hip": (lambda env, side: (side * (env["hips_w"] / 2 + 0.75), env["waist_y"], -0.5), "down", (55.0, 0.0, 0.0),
            "body"),
    "back": (lambda env, side: (0.0, (env["chest_y"] + env["chest_top"]) / 2, env["chest_d"] / 2 + 1.0), "down",
             (0.0, 0.0, -35.0), "back"),
}


def apply_weapon(model, weapon, index=0):
    """Place art.weapon: the prop in hand (weapon_r / weapon_l) and the sheathed copy (weapon_back)."""
    prop = weapon.get("prop")
    if not prop:
        return
    part = load_part(prop)
    if part.get("category") != "weapons":
        raise HerogenError("art.weapon.prop %s is not a weapons part" % prop)
    entry = {k: weapon[k] for k in ("color", "trim", "alt", "detail", "params") if k in weapon}
    ctx = _Ctx(model, part, entry, 100 + index)
    ctx.where = "art.weapon (%s)" % prop
    hand = weapon.get("hand", "right")
    if hand not in ("right", "left", "both", "pair"):
        raise HerogenError("art.weapon.hand %r is unknown" % hand)
    sides = {"right": ["r"], "both": ["r"], "left": ["l"], "pair": ["r", "l"]}[hand]
    cubes = part.get("cubes", [])
    glow = bool(weapon.get("glow"))
    for side in sides:
        bone = "weapon_" + side
        pv = model.use_bone(bone).pivot
        for n, spec in enumerate(cubes):
            o, s = _grip_box(spec, ctx)
            ox = o[0] if side == "r" else -(o[0] + s[0])
            org = (pv[0] + ox, pv[1] + o[1], pv[2] + o[2])
            c = _cube_from_spec(ctx, spec, n, bone, org, s, ":" + side)
            c.name = "%s:%s#%d" % (bone, prop, n)
            if glow and spec.get("role") == "blade":
                c.glow = True
            model.add_cube(c)
        tip = ctx.ev(part.get("tip", [0, 0, 0]))
        loc = model.use_bone("vfx_tip_" + side)
        loc.pivot = (pv[0] + (tip[0] if side == "r" else -tip[0]), pv[1] + tip[1], pv[2] + tip[2])
    sheathed = weapon.get("sheathed", "none")
    if sheathed == "none":
        return
    if sheathed not in SHEATH:
        raise HerogenError("art.weapon.sheathed %r is unknown (hip, back, none)" % sheathed)
    mount_fn, mode, rot, parent = SHEATH[sheathed]
    rig_bone = model.catalogue["weapon_back"]
    if parent != rig_bone.parent:
        if "weapon_back" in model.bones:
            raise HerogenError("art.weapon: weapon_back is already used by a part, so a %s sheath cannot move it"
                               % sheathed)
        model.catalogue["weapon_back"] = Bone("weapon_back", parent, (0.0, model.env["waist_y"], -0.5),
                                              required=rig_bone.required, kind=rig_bone.kind, source="weapon")
    for side in sides:
        mside = 1 if side == "r" else -1      # a right-hand weapon hangs on the left hip
        mount = mount_fn(model.env, mside)
        specs = [s for s in cubes if s.get("role", "hilt") != "blade"] + part.get("scabbard", [])
        for n, spec in enumerate(specs):
            o, s = _grip_box(spec, ctx)
            o2, s2 = _transform_box(o, s, mode)
            ox = o2[0] if side == "r" else -(o2[0] + s2[0])
            org = (mount[0] + ox, mount[1] + o2[1], mount[2] + o2[2])
            c = _cube_from_spec(ctx, spec, n, "weapon_back", org, s2, ":sheathed_" + side)
            c.name = "weapon_back:%s:%s#%d" % (side, prop, n)
            r = rot if side == "r" else (rot[0], -rot[1], -rot[2])
            c.rotation, c.pivot = r, mount
            model.add_cube(c)
