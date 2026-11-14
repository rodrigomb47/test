"""The parts library (design 13.3): small JSON files that add bones and cubes to the model and paint over the base
body. A hero's art.parts entry picks a part and maps its logical colour slots to palette slots.

Part file fields:
  id, category, params {name: {min, max, default}}, vars {name: expression} (G6c: named values over the params and
  the base dims), slots {main|trim|alt|detail: default palette slot},
  occupies [...], conflicts [glob...], requires [part ids] (C1: the parts it is worn over, for lint and the
  sheets: tails/cat_puff over tails/cat_long), bones [{name, parent, pivot}], cubes [...], paint [...],
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
  segment that continues into the next one), tip {slot, frac, edge} (G6a: the bottom (or top) fraction of a strand's
  side faces and its end cap in another slot: red tips on a black mane, black tips on fox ears).
Hero entry fields besides part/color/trim/alt/detail/params/phase: left {color, trim, alt, detail} (G6a: the mirrored
  left-hand copies take these slots, for odd-coloured ears or a two-tone pair).
Paint op fields (paint over existing cubes, usually the base body, or the part's own cubes with a glob starting
  with "#": "#bell*" is every cube the part named bell..., a sleeve's lining): cubes [glob...], faces [...], y [lo, hi],
  x [lo, hi], z [lo, hi], abs_x [lo, hi] (|x|, for symmetric regions), side r|l, vee [y_tip, slope] (only where
  |x| <= (y - y_tip) * slope: a V neckline), slot, material, pattern, trim {slot, edge, width}, tone. Ranges are
  model pixels at rest, lower bound inclusive.
Generators (style pass 2, gen.py): `strands` (rows of rotated strands, panels, frills), `chains` (bones along a
  polyline with a box per segment: big tails) and `groups` (cubes sharing one rotation and pivot).
Weapon parts (category weapons) are authored in grip space (grip centre at 0,0,0, blade along -z, as a held item at
  rest) with cube roles hilt or blade, a scabbard cube list and a tip point; see apply_weapon. A hero's weapon entry
  may light pieces from a phase on (`phase_glow`, C1: glowing shells on pN_ bones; _phase_glow).
"""
import fnmatch
import os

import numpy as np

import emblems
import gen
from expr import evaluate
from palette import FIXED, SLOTS
from rig import BASE_SLOTS, FACES, Bone, Cube, mirror_name
from rot import file_angles, file_matrix
from util import HEROSRC, HerogenError, load_json

LOGICAL_SLOTS = ("main", "trim", "alt", "detail")
MATERIALS = ("cloth", "skin", "hair", "metal", "leather", "fur", "scale", "wood", "paper", "lacquer", "bone", "gem",
             "glow")
PATTERNS = ("stripes", "checks", "plaid", "dots", "pleats", "waves", "scales", "hemp", "petals", "patches", "solid")
TONE_PATTERNS = ("pleats",)          # patterns that only shade, without a second colour
CUTOUT_PREFIXES = ("jagged_", "tip_jagged_", "bangs_jagged_")
CUTOUTS = ("tri_up", "tri_down", "peak", "round_bottom", "round_top", "fur_tips", "torn", "lace")
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
        # "vars": {name: expression}: named values over the params and the base dims, in file order (G6c: a
        # prop's form scale, used by every length of the part)
        for k, expr in (part.get("vars") or {}).items():
            if k in self.env:
                raise HerogenError("%s: var %r is already a param or a base dim" % (self.where, k))
            self.env[k] = float(evaluate(expr, self.env))
        self.slots = dict(part.get("slots", {}))
        for logical, key in (("main", "color"), ("trim", "trim"), ("alt", "alt"), ("detail", "detail")):
            if entry.get(key):
                self.slots[logical] = entry[key]
        # "left": {"color": ..., "alt": ...} recolours the mirrored (left-hand) copies only: odd ears, a calico tail
        self.left_slots = dict(self.slots)
        for logical, key in (("main", "color"), ("trim", "trim"), ("alt", "alt"), ("detail", "detail")):
            if (entry.get("left") or {}).get(key):
                self.left_slots[logical] = entry["left"][key]
        self.side = "r"
        self.copies = {}
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
        name = (self.left_slots if self.side == "l" else self.slots).get(name, name)
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
        spec["seed"] = int(self.ev(spec.get("seed", 0)))
        if "center" in spec:
            spec["center"] = self.at(spec["center"])
            spec["radius"] = float(self.ev(spec.get("radius", 6)))
        return spec

    def stamp(self, spec, face_required=True):
        """An emblem stamp {emblem (a name, or "@" for the hero entry's `emblem`), slot (default alt), slot2 (the
        emblem's "+" texels; default the slot one tone lighter), face, size, offset [du, dv] texels, flip}; a
        mirrored (left-hand) copy stamps the mirrored face, flipped."""
        if spec is None:
            return None
        spec = dict(spec)
        name = spec.get("emblem", "@")
        if name == "@":
            name = self.entry.get("emblem")
            if not name:
                return None              # the part offers a stamp; this hero entry chose no emblem
        size = spec.get("size")
        emblems.sizes(name)
        faces = spec.get("face", "north")
        faces = [faces] if isinstance(faces, str) else list(faces)
        for face in faces:
            if face not in FACES:
                raise HerogenError("%s: stamp face %r is unknown" % (self.where, face))
        flip = bool(spec.get("flip"))
        if self.side == "l":
            faces = [{"east": "west", "west": "east"}.get(f, f) for f in faces]
            flip = not flip
        out = {"emblem": name, "size": int(self.ev(size)) if size is not None else None,
               "slot": self.slot(spec.get("slot", "alt")),
               "slot2": self.slot(spec["slot2"]) if spec.get("slot2") else None, "face": faces[0],
               "faces": tuple(faces),
               "offset": tuple(float(v) for v in self.ev(spec.get("offset", [0, 0]))), "flip": flip,
               "tone": int(spec.get("tone", 0)), "rotate": int(spec.get("rotate", 0)) % 360}
        if out["rotate"] not in (0, 90, 180, 270):
            raise HerogenError("%s: stamp rotate must be 0, 90, 180 or 270" % self.where)
        if out["size"] is not None:
            emblems.bitmap(name, out["size"])
        out["rows"] = None
        if "center" in spec:
            out["center"] = self.at(spec["center"])
        return out

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
                glow=_glow(ctx, spec), tone=int(spec.get("tone", 0)),
                density=density, shade=shade, outline=bool(spec.get("outline", True)), tip=_tip(ctx, spec.get("tip")),
                stamp=ctx.stamp(spec.get("stamp")))


def _glow(ctx, spec):
    """A cube's or paint op's glow: true (always in the glow mask), "phase" (only in the phase look's texture,
    `_p2`: stripes that light up from phase 2) or false; material glow always glows."""
    g = spec.get("glow", False)
    if g not in (True, False, "phase"):
        raise HerogenError("%s: glow must be true, false or \"phase\"" % ctx.where)
    if spec.get("material") == "glow":
        return True
    return g


def _tip(ctx, spec):
    """A strand's end in another colour ({"slot", "frac" of the side faces' rows, "edge" bottom or top}): red tips on
    black hair, dark tips on fox ears."""
    if spec is None:
        return None
    edge = spec.get("edge", "bottom")
    if edge not in ("bottom", "top"):
        raise HerogenError("%s: tip edge %r is unknown (bottom, top)" % (ctx.where, edge))
    frac = float(ctx.ev(spec.get("frac", 0.3)))
    if not 0 < frac <= 1:
        raise HerogenError("%s: tip frac must be in 0..1" % ctx.where)
    return {"slot": ctx.slot(spec.get("slot", "alt")), "frac": frac, "edge": edge}


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
        ctx.side = "l"
        try:
            mc = _cube_from_spec(ctx, spec, 0, mbone, morigin, size, ":l")
        finally:
            ctx.side = "r"
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

    ctx.copies = gen.chains(ctx, lambda name, parent, pivot: _declare_one(ctx, name, parent, pivot), add)
    gen.strands(ctx, add)


def _paint_ops(ctx):
    model = ctx.model
    for n, op in enumerate(ctx.part.get("paint", [])):
        if not gen._wanted(ctx, op):
            continue
        # a glob starting with "#" names the part's own cubes ("#bell*": every bell panel, mirrored copies too)
        globs = [ctx.prefix + ctx.pid + g if g.startswith("#") else g for g in op.get("cubes", ["*"])]
        stamp = ctx.stamp(op.get("stamp"))
        if "stamp" in op and stamp is None:
            continue                       # an emblem slot the hero entry left empty
        if stamp is not None and "center" not in stamp:
            raise HerogenError("%s: paint op %d's stamp needs a center" % (ctx.where, n))
        # an overlay op (and a stamp without its own slot) keeps what is under it and adds only its pattern or
        # emblem: calico patches over any hair, a crest over any coat
        overlay = bool(op.get("overlay", stamp is not None and "slot" not in op))
        rec = {"part": ctx.pid, "cubes": globs, "faces": op.get("faces"),
               "slot": None if overlay and "slot" not in op else ctx.slot(op.get("slot", "main")),
               "material": _material(ctx, op.get("material", "cloth")),
               "pattern": ctx.pattern(op.get("pattern")), "side": op.get("side"), "tone": int(op.get("tone", 0)),
               "trim": ctx.trim(op.get("trim")), "order": (ctx.index, n), "overlay": overlay, "stamp": stamp,
               "glow": _glow(ctx, {"glow": op.get("glow", False)})}
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
            if op.get("optional"):
                continue                   # a marking over a piece this hero does not wear (calico without a tail)
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


LEG_BONES = {"r": ("leg_r", "shin_r", "foot_r"), "l": ("leg_l", "shin_l", "foot_l")}
INNER_MARGIN = 0.05         # px a leg shell's inner side stays short of the centre line (G6b)
INNER_STEP = 0.025          # px between the inner sides of two nested shells (art review)
INNER_MIN = 0.02            # px: a nested inner side closer to the leg than this goes inside the leg instead
INNER_HIDE = 0.12           # px inside the leg for a hidden inner side (clear of the narrower shin's side, 0.05)
JOINT_REACH = 1.5           # px: shells this close in height count as overlapping (a bent knee brings them together)


def _leg_side(model, bone):
    """"r" or "l" when the bone is a leg bone or nested under one (fine_, pN_), else None."""
    b = bone
    while b is not None:
        for side, names in LEG_BONES.items():
            if b in names:
                return side
        node = model.bones.get(b)
        b = node.parent if node is not None else None
    return None


def settle_leg_shells(model):
    """Nested leg shells get their own inner-side planes (art review). On the inner side of a leg there is no room
    for the layer convention: the gap to the centre line is 0.1-0.15 px on every base, so G6b's clamp
    (`max(0.05, min(e, hip_x - leg_w / 2 - 0.05))`) puts the inner side of every shell (a boot shaft at 0.3, its cuff
    at 0.55, a trouser gather at 0.2, a holster strap) on one plane 0.05 px from the centre line, where two shells
    worn on one another z-fight as soon as a stance spreads the legs. Among the shells whose inner sides lie on that
    side of the leg and overlap in height (rest pose, JOINT_REACH margin, every bone of the leg: a bent knee keeps them in
    one plane), the outermost (largest outer offset: the layer convention) keeps the plane; each shell under it moves
    its inner side INNER_STEP px toward the leg, so the outer layer is the one seen between the legs, as on the outer
    side; a shell that would come closer than INNER_MIN to the leg ends inside the leg instead (it is covered there by
    the shells over it). Only the inner sides of unrotated non-base cubes between the leg's inner face and the centre
    line move; everything else keeps its geometry."""
    env = model.env
    gap = env["hip_x"] - env["leg_w"] / 2.0
    outer = env["hip_x"] + env["leg_w"] / 2.0
    plane = gap - INNER_MARGIN
    for side in ("r", "l"):
        shells = []
        for c in model.cubes:
            if c.part == "base" or c.rotation is not None or c.inflate or _leg_side(model, c.bone) != side:
                continue
            x0, sx = c.origin[0], c.size[0]
            if side == "r":
                inner, e = x0 + sx, -x0 - outer         # inner side at +x (toward the centre), outer at -x
                s = inner + gap
            else:
                inner, e = x0, x0 + sx - outer
                s = gap - inner
            if 1e-4 < s <= gap + 1e-6:
                shells.append([c, round(e, 6), s])
        if len(shells) < 2:
            continue
        shells.sort(key=lambda t: (-t[1], t[0].name))
        level = {}
        for i, (c, e, s) in enumerate(shells):
            y0, y1 = c.origin[1], c.origin[1] + c.size[1]
            over = [level[o.name] for o, _, _ in shells[:i]
                    if o.origin[1] - JOINT_REACH < y1 and y0 < o.origin[1] + o.size[1] + JOINT_REACH]
            level[c.name] = (max(over) + 1) if over else 0
        for c, e, s in shells:
            if level[c.name] == 0:
                continue
            want = plane - INNER_STEP * level[c.name]
            if want < INNER_MIN:
                want = -INNER_HIDE                       # inside the leg: covered by the shells over it
            x0, sx = c.origin[0], c.size[0]
            if side == "r":
                c.size = (sx + (want - s),) + tuple(c.size[1:])
            else:
                c.origin = (x0 - (want - s),) + tuple(c.origin[1:])
                c.size = (sx + (want - s),) + tuple(c.size[1:])


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
    if part.get("category") == "mounts":
        # G10 (MOBILITY_AND_WORK 4.3): a carrier drawn on the rig's `mount` bone; `carrier` names its kind
        if not any(c.part == ctx.pid and c.bone.endswith("mount") for c in model.cubes):
            raise HerogenError("%s: a mounts part puts its cubes on the `mount` bone" % ctx.where)
        model.carrier_kinds.setdefault("mount", []).append(part.get("carrier", "mount"))
    if part.get("secondary"):
        sec = dict(part["secondary"])
        # a chain built several times ("copies": a fox's tails) declares one spring chain per copy
        for chain in ctx.copies.get(tuple(sec.get("chain", [])), [sec.get("chain", [])]):
            model.secondary.append(dict(sec, part=ctx.pid, chain=[ctx.renames.get(c, c) for c in chain]))
    model.parts_used.append(ctx.pid)
    return ctx


# ------------------------------------------------------------------ weapons
#
# A weapon part is authored once in grip space: the grip centre at 0,0,0 and the blade (head, business end) along -z,
# as a held item at rest, the striking side toward -y. Cube roles: "hilt" (default: in the hand and in the sheath),
# "blade" (in the hand only), "worn" (claws' gauntlet: on the hand bone at all times when sheathed "worn"). A
# `scabbard` list is added to the sheathed copy; with "scabbard_stays" the scabbard hangs on its own `sheath` bone and
# stays at the hip while the weapon is drawn (a katana's saya). Cubes may be rotated (rotation + pivot in grip space).
#
# Placements (each a signed axis permutation P, a translation and an optional rotation about a mount point), built
# for a right-hand weapon and mirrored for a left-hand one:
#   hand        on weapon_r at the grip; "grip": "reverse" turns the prop 180 degrees about the hand's y axis (an
#               ice-pick grip: the blade leaves the little-finger side and lies along the forearm when it is raised)
#   hip         cross-draw: the opposite hip, blade down and swung back 55 degrees (hilt forward and up)
#   hip_same    the hip on the drawing hand's side (a short sword beside the long one, on one side)
#   back        diagonally on the back, blade down, the hilt over the drawing hand's shoulder
#   back_up     diagonally on the back, head up and its striking side outward (a staff: the crook away from the head)
#   obi         thrust through the sash at the front of the opposite hip, edge up (a katana); obi_same: the same side
#   back_flat   flat on the back, face out (a shield)
#   waist_back  across the small of the back, the blade toward the other side (a short blade, a fan, a wand)
#   worn        "worn" and "hilt" cubes stay on the hand (a gauntlet), blades only when drawn (claws)
#   none        nowhere when sheathed (the prop is put away)
# and "mount" (G10's carriers: a staff-broom): the whole prop on the `mount` bone (a top-level bone at the feet,
# MOBILITY_AND_WORK 4.3) with the prop's `mount.seat` point at `mount.height` above the ground and its head forward.

def _grip_box(spec, ctx):
    o = ctx.ev(spec["origin"])
    s = ctx.ev(spec["size"])
    return o, s


IDENT = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
P_DOWN = ((1, 0, 0), (0, 0, 1), (0, -1, 0))        # (x, y, z) -> (x, z, -y): forward (-z) becomes down
P_UP = ((1, 0, 0), (0, 0, -1), (0, 1, 0))          # (x, y, z) -> (x, -z, y): forward becomes up
P_UP_OUT = ((-1, 0, 0), (0, 0, -1), (0, -1, 0))    # forward becomes up and the striking side (-y) faces back (+z):
#                                                    a staff on the back, its crook and what hangs from it outward
P_REVERSE = ((-1, 0, 0), (0, 1, 0), (0, 0, -1))    # 180 degrees about y: the blade points back along the forearm
P_ACROSS = ((0, 0, -1), (0, 1, 0), (1, 0, 0))      # forward (-z) becomes the hero's left (+x)
P_FLAT = ((0, 1, 0), (0, 0, -1), (-1, 0, 0))       # the outer side (-x) faces back (+z), forward (-z) points up:
#                                                    a shield flat on the back, its top up and its face out

# a face's normal and its texel axes as seen from outside (uvpack.texel_point: u = column, v = row)
FACE_AXES = {"north": ((0, 0, -1), (1, 0, 0), (0, -1, 0)), "south": ((0, 0, 1), (-1, 0, 0), (0, -1, 0)),
             "east": ((-1, 0, 0), (0, 0, -1), (0, -1, 0)), "west": ((1, 0, 0), (0, 0, 1), (0, -1, 0)),
             "up": ((0, 1, 0), (1, 0, 0), (0, 0, -1)), "down": ((0, -1, 0), (1, 0, 0), (0, 0, -1))}


def stamp_through(stamp, p):
    """A stamp spec on a grip-space face carried through the axis permutation p: the face it lands on and the extra
    quarter turns that keep the emblem upright as seen from outside."""
    if not stamp or p is IDENT:
        return stamp
    faces = stamp.get("face", "north")
    if not isinstance(faces, str):
        outs = [stamp_through(dict(stamp, face=f), p) for f in faces]
        return dict(outs[0], face=[o["face"] for o in outs])
    pm = np.array(p, dtype=float)
    n, u, v = (np.array(a, dtype=float) for a in FACE_AXES[faces])
    n2, u2 = pm @ n, pm @ u
    face = next(f for f, ax in FACE_AXES.items() if np.allclose(ax[0], n2))
    _, fu, fv = (np.array(a, dtype=float) for a in FACE_AXES[face])
    if np.allclose(u2, fu):
        q = 0
    elif np.allclose(u2, fv):
        q = 90
    elif np.allclose(u2, -fu):
        q = 180
    else:
        q = 270
    return dict(stamp, face=face, rotate=(int(stamp.get("rotate", 0)) + q) % 360)


def _transform_box(o, s, mode):
    """Grip-space box -> axis-permuted box. 'hand': as authored; 'down': blade points down; 'up': blade points up."""
    p = {"hand": IDENT, "down": P_DOWN, "up": P_UP}[mode]
    return _perm_box(o, s, p)


def _perm_box(o, s, p):
    p = np.array(p, dtype=float)
    a = p @ np.array(o, dtype=float)
    b = p @ (np.array(o, dtype=float) + np.array(s, dtype=float))
    lo, hi = np.minimum(a, b), np.maximum(a, b)
    return tuple(float(v) for v in lo), tuple(float(v) for v in hi - lo)


def _placed(o, s, rot, pivot, p, t, rs=None, m=None):
    """A grip-space box (with its own rotation about `pivot`, or none) under x -> m + Rs (P x + t - m).
    Returns (origin, size, rotation or None, pivot or None) for a Bedrock cube."""
    o2, s2 = _perm_box(o, s, p)
    o2 = tuple(a + b for a, b in zip(o2, t))
    pm = np.array(p, dtype=float)
    if rot is None:
        if rs is None:
            return o2, s2, None, None
        return o2, s2, tuple(rs), tuple(m)
    r1 = pm @ file_matrix(rot) @ pm.T
    q1 = pm @ np.array(pivot, dtype=float) + np.array(t, dtype=float)
    if rs is None:
        return o2, s2, file_angles(r1), tuple(float(v) for v in q1)
    msr = file_matrix(rs)
    mv = np.array(m, dtype=float)
    q2 = mv + msr @ (q1 - mv)
    d = q2 - q1
    return (tuple(a + float(b) for a, b in zip(o2, d)), s2, file_angles(msr @ r1), tuple(float(v) for v in q2))


def _mirror_placed(o, s, rot, pivot):
    o = (-(o[0] + s[0]), o[1], o[2])
    if rot is not None:
        rot = (rot[0], -rot[1], -rot[2])
        pivot = (-pivot[0], pivot[1], pivot[2])
    return o, s, rot, pivot


SHEATH = {
    # mount point of a RIGHT-hand weapon (absolute; mirrored for the left hand), axis permutation, rotation (file
    # degrees about the mount point), parent of the sheathed bones. GeckoLib bakes file rotations as (-x, -y, z) in
    # its x-mirrored space, so +55 about x swings a hanging blade backward, and -35 about z puts a back-carried hilt
    # over the hero's right shoulder. A hip scabbard hangs from the hips (`body`), so a waist twist or chest lean does
    # not swing it through the leg; a back carry follows the chest. Heights follow the form.
    "hip": (lambda env: (env["hips_w"] / 2 + 0.75, env["waist_y"], -0.5), P_DOWN, (55.0, 0.0, 0.0), "body"),
    "hip_same": (lambda env: (-(env["hips_w"] / 2 + 0.75), env["waist_y"], -0.5), P_DOWN, (55.0, 0.0, 0.0), "body"),
    # thrust through the sash at the front of the opposite hip, hilt forward and up in front of the belly, the
    # scabbard sweeping back along the outside of the hip (a katana)
    "obi": (lambda env: (env["hips_w"] / 2 - 0.2, env["waist_y"] + 0.4, -env["hips_d"] / 2 - 0.7), P_DOWN,
            (66.0, 22.0, 6.0), "body"),
    "obi_same": (lambda env: (-(env["hips_w"] / 2 - 0.2), env["waist_y"] + 0.4, -env["hips_d"] / 2 - 0.7), P_DOWN,
                 (66.0, -22.0, -6.0), "body"),
    "back": (lambda env: (0.0, (env["chest_y"] + env["chest_top"]) / 2, env["chest_d"] / 2 + 1.0), P_DOWN,
             (0.0, 0.0, -35.0), "back"),
    "back_up": (lambda env: (0.0, (env["chest_y"] + env["chest_top"]) / 2, env["chest_d"] / 2 + 1.0), P_UP_OUT,
                (0.0, 0.0, 30.0), "back"),
    "back_flat": (lambda env: (0.0, (env["waist_y"] + env["chest_top"]) / 2, env["chest_d"] / 2 + 1.25), P_FLAT,
                  None, "back"),
    "waist_back": (lambda env: (0.0, env["waist_y"] - 0.5, env["hips_d"] / 2 + 1.0), P_ACROSS, (0.0, 0.0, -12.0),
                   "body"),
}
SHEATHS = tuple(SHEATH) + ("worn", "none")
GRIPS = ("forward", "reverse")


def _weapon_ctx(model, part, entry, index, where):
    ctx = _Ctx(model, part, entry, index)
    ctx.where = where
    return ctx


def _sheath_bone(model, name, parent, where):
    """weapon_back (the rig's sheathed bone, re-parented for a hip carry) or a second sheathed bone when the first
    already hangs from another parent."""
    rig_bone = model.catalogue["weapon_back"]
    if name == "weapon_back":
        if rig_bone.parent != parent:
            if "weapon_back" in model.bones:
                return None
            model.catalogue["weapon_back"] = Bone("weapon_back", parent, (0.0, model.env["waist_y"], -0.5),
                                                  required=rig_bone.required, kind=rig_bone.kind, source="weapon")
        return model.use_bone("weapon_back")
    if name not in model.catalogue:
        pv = model.catalogue["weapon_back"].pivot if model.catalogue["weapon_back"].parent == parent else \
            (0.0, model.env["waist_y"], -0.5)
        model.declare_bone(name, parent, pv, where)
    if model.catalogue[name].parent != parent:
        raise HerogenError("%s: %s already hangs from %s" % (where, name, model.catalogue[name].parent))
    return model.use_bone(name)


def _sheathed_bone(model, parent, where):
    b = _sheath_bone(model, "weapon_back", parent, where)
    if b is not None:
        return b.name
    return _sheath_bone(model, "weapon_back_2", parent, where).name


def _add(model, ctx, spec, n, bone, placed, name, side, perm=None):
    o, s, rot, pv = placed
    if perm is not None and spec.get("stamp"):
        spec = dict(spec, stamp=stamp_through(spec["stamp"], perm))
    if spec.get("phase") is not None:
        # a piece that appears from a phase on (a staff's broom bristles from phase 2): a pN_ bone under its bone
        ph = int(spec["phase"])
        if ph not in (2, 3):
            raise HerogenError("%s: a weapon cube's phase must be 2 or 3" % ctx.where)
        if not bone.startswith("p%d_" % ph):
            bone = model.nested_bone("p%d_" % ph, bone, ctx.where).name
            name = "p%d_%s" % (ph, name)
    fine = spec.get("fine")
    if fine not in (None, True, False, "sheathed"):
        raise HerogenError("%s: a weapon cube's fine must be true, false or \"sheathed\"" % ctx.where)
    if fine == "sheathed":
        # detail only in the carried copy (a guard on a scabbarded sword at the hip), whole in the hand
        fine = "weapon_r" not in bone and "weapon_l" not in bone and not bone.startswith("hand_")
    if fine and not bone.startswith("fine_"):
        bone = model.nested_bone("fine_", bone, ctx.where).name
    if side == "l":
        o, s, rot, pv = _mirror_placed(o, s, rot, pv)
    ctx.side = side
    try:
        c = _cube_from_spec(ctx, {k: v for k, v in spec.items() if k not in ("rotation", "pivot", "phase", "fine")},
                            n, bone, o, s)
    finally:
        ctx.side = "r"
    c.name = name
    c.rotation, c.pivot = rot, pv
    model.add_cube(c)
    return c


def _spec_rot(ctx, spec):
    if "rotation" not in spec:
        return None, None
    if "pivot" not in spec:
        raise HerogenError("%s: a rotated cube needs a pivot" % ctx.where)
    return tuple(float(v) for v in ctx.ev(spec["rotation"])), tuple(float(v) for v in ctx.ev(spec["pivot"]))


def _place_weapon(model, w, index, side, prefix=""):
    """One prop (the main weapon or the off-hand one) on one side. Returns the sheathed bones it filled."""
    prop = w["prop"]
    part = load_part(prop)
    if part.get("category") != "weapons":
        raise HerogenError("art.weapon prop %s is not a weapons part" % prop)
    entry = {k: w[k] for k in ("color", "trim", "alt", "detail", "params", "emblem") if k in w}
    where = "art.weapon (%s)" % prop
    ctx = _weapon_ctx(model, part, entry, 100 + index, where)
    grip = w.get("grip", part.get("grip", "forward"))
    if grip not in GRIPS:
        raise HerogenError("%s: grip %r is unknown (%s)" % (where, grip, ", ".join(GRIPS)))
    cubes = part.get("cubes", [])
    glow = w.get("glow", False)
    sheathed = w.get("sheathed", part.get("sheath", "none"))
    if sheathed not in SHEATHS:
        raise HerogenError("%s: sheathed %r is unknown (%s)" % (where, sheathed, ", ".join(SHEATHS)))
    tag = prop if not prefix else prefix + prop
    # in the hand
    hand_bone = "weapon_" + side
    pv = model.use_bone(hand_bone).pivot
    if prefix:
        hand_bone = model.nested_bone(prefix, hand_bone, where).name
    pv_r = (-abs(pv[0]), pv[1], pv[2])             # the right hand's grip; the left copy is its mirror
    p_hand = P_REVERSE if grip == "reverse" else IDENT
    shells = _phase_glow(w, part, where)
    for n, spec in enumerate(cubes):
        role = spec.get("role", "hilt")
        if role not in ("hilt", "blade", "worn"):
            raise HerogenError("%s: cube role %r is unknown (hilt, blade, worn)" % (where, role))
        if sheathed == "worn" and role != "blade":
            continue
        o, s = _grip_box(spec, ctx)
        rot, q = _spec_rot(ctx, spec)
        c = _add(model, ctx, spec, n, hand_bone, _placed(o, s, rot, q, p_hand, pv_r), "%s:%s#%d" % (hand_bone, tag, n),
                 side, p_hand)
        if glow and role == "blade":
            c.glow = glow
        if shells and _shell_wanted(shells, spec):
            o2, s2, spec2 = _shell(shells, o, s, spec)
            _add(model, ctx, spec2, n, hand_bone, _placed(o2, s2, rot, q, p_hand, pv_r),
                 "%s:%s#%dglow" % (hand_bone, tag, n), side, p_hand)
    tip = ctx.ev(part.get("tip", [0, 0, 0]))
    tip = tuple(float(v) for v in np.array(p_hand, dtype=float) @ np.array(tip, dtype=float))
    loc = model.use_bone("vfx_tip_" + side)
    loc.pivot = (pv[0] + (tip[0] if side == "r" else -tip[0]), pv[1] + tip[1], pv[2] + tip[2])
    filled = []
    if sheathed == "worn":
        hb = "hand_" + side
        for n, spec in enumerate(cubes):
            if spec.get("role", "hilt") == "blade":
                continue
            o, s = _grip_box(spec, ctx)
            rot, q = _spec_rot(ctx, spec)
            _add(model, ctx, spec, n, hb, _placed(o, s, rot, q, p_hand, pv_r), "%s:%s#%d" % (hb, tag, n), side, p_hand)
        return filled
    if sheathed == "none":
        return filled
    mount_fn, perm, rs, parent = SHEATH[sheathed]
    off = tuple(float(v) for v in ctx.ev(w.get("sheath_offset", [0, 0, 0])))
    mount = tuple(a + b for a, b in zip(mount_fn(model.env), off))
    stays = bool(part.get("scabbard_stays"))
    hilts = [s for s in cubes if s.get("role", "hilt") != "blade"]
    scab = part.get("scabbard", [])
    targets = []                                   # (bone, cube specs, name tag)
    bone = _sheathed_bone(model, parent, where)
    if prefix:
        # an off-hand drawn from phase N: its sheathed copy shows from phase N while sheathed (pN_ under the
        # sheathed bone) and, before phase N, at all times (`holstered`, hidden from phase N by the phase look)
        targets.append((model.nested_bone(prefix, bone, where).name, hilts + ([] if stays else scab)))
        hb = "holstered"
        if hb not in model.catalogue:
            model.declare_bone(hb, parent, model.catalogue[bone].pivot, where)
        model.use_bone(hb)
        targets.append((hb, hilts + ([] if stays else scab)))
        model.phase_hide.setdefault(int(prefix[1]), [])
        if hb not in model.phase_hide[int(prefix[1])]:
            model.phase_hide[int(prefix[1])].append(hb)
    else:
        targets.append((bone, hilts + ([] if stays else scab)))
    if stays and scab:
        sb = "sheath" if parent == "body" else "sheath_" + parent
        if sb not in model.catalogue:
            model.declare_bone(sb, parent, model.catalogue[bone].pivot, where)
        model.use_bone(sb)
        targets.append((sb, scab))
    for tb, specs in targets:
        filled.append(tb)
        for n, spec in enumerate(specs):
            o, s = _grip_box(spec, ctx)
            rot, q = _spec_rot(ctx, spec)
            placed = _placed(o, s, rot, q, perm, mount, rs, mount if rs is not None else None)
            _add(model, ctx, spec, n, tb, placed, "%s:%s:%s#%d" % (tb, side, tag, n), side, perm)
            if shells and _shell_wanted(shells, spec):
                o2, s2, spec2 = _shell(shells, o, s, spec)
                placed = _placed(o2, s2, rot, q, perm, mount, rs, mount if rs is not None else None)
                _add(model, ctx, spec2, n, tb, placed, "%s:%s:%s#%dglow" % (tb, side, tag, n), side, perm)
    return filled


SHELL = 0.05        # px a phase-glow shell stands out round the piece it lights (the face planes' 0.05 px rule)


def _phase_glow(w, part, where):
    """A weapon entry's `phase_glow` {phase: 2 or 3, cubes: [globs on the part's cube names], slot}: those pieces
    light up from that phase (C1: Hinae's paper streamers glow in her phase 3). Each gets a glowing shell SHELL px
    round it on a pN_ bone (hidden before the phase by the phase look, HEROES_V2 4.3), painted in `slot` (default
    the piece's own), in the hand and on the carried copy. A shell rather than a phase texture: the phase texture
    (`_p2`) shows from phase 2 on, so a phase-3 glow needs its own pieces. None without the field."""
    pg = w.get("phase_glow")
    if not pg:
        return None
    for k in pg:
        if k not in ("phase", "cubes", "slot"):
            raise HerogenError("%s: phase_glow field %r is unknown (phase, cubes, slot)" % (where, k))
    if pg.get("phase") not in (2, 3):
        raise HerogenError("%s: phase_glow.phase must be 2 or 3" % where)
    globs = list(pg.get("cubes") or [])
    names = [c.get("name", "") for c in part.get("cubes", [])]
    for g in globs:
        if not any(fnmatch.fnmatchcase(n, g) for n in names):
            raise HerogenError("%s: phase_glow cube glob %r matches no cube of %s" % (where, g, part["id"]))
    if not globs:
        raise HerogenError("%s: phase_glow needs cubes" % where)
    return {"phase": int(pg["phase"]), "cubes": globs, "slot": pg.get("slot")}


def _shell_wanted(shells, spec):
    return any(fnmatch.fnmatchcase(spec.get("name", ""), g) for g in shells["cubes"])


def _shell(shells, o, s, spec):
    """The glowing shell of a weapon piece: its grip-space box SHELL px larger every way, from the phase on."""
    o2 = [float(a) - SHELL for a in o]
    s2 = [float(b) + 2 * SHELL for b in s]
    spec2 = {k: v for k, v in spec.items() if k not in ("stamp", "pattern", "trim", "tip", "name")}
    spec2.update(phase=shells["phase"], glow=True, outline=False)
    if shells["slot"]:
        spec2["slot"] = shells["slot"]
    return o2, s2, spec2


def _place_mount(model, w, index):
    """The prop on the `mount` bone for a carrier that is the weapon (G10: Ambrose's staff-broom)."""
    prop = w["prop"]
    part = load_part(prop)
    spec_m = part.get("mount")
    where = "art.weapon (%s) mount" % prop
    if not spec_m:
        raise HerogenError("%s: the prop declares no mount (seat, height)" % where)
    entry = {k: w[k] for k in ("color", "trim", "alt", "detail", "params", "emblem") if k in w}
    ctx = _weapon_ctx(model, part, entry, 150 + index, where)
    seat = np.array(ctx.ev(spec_m["seat"]), dtype=float)
    height = float(ctx.ev(spec_m.get("height", "hip_y - leg_d / 2 - 0.6")))
    # the rig's `mount` bone (G10): under `root`, so a whole-body offset (a hover bob) carries the mount with its
    # rider; its pivot is the seat (the underside of level thighs, the prop's `mount.height` by default)
    if model.subtree_has_cubes("mount") if "mount" in model.bones else False:
        raise HerogenError("%s: the hero already has a carrier on the mount bone (a mounts/* part): one carrier" % where)
    loc = model.declare_bone("mount", "root", (0.0, height, 0.0), where) if "mount" not in model.catalogue \
        else model.use_bone("mount")
    t = (-seat[0], height - seat[1], -seat[2])
    for n, spec in enumerate(part.get("cubes", [])):
        o, s = _grip_box(spec, ctx)
        rot, q = _spec_rot(ctx, spec)
        _add(model, ctx, spec, n, loc.name, _placed(o, s, rot, q, IDENT, t), "mount:%s#%d" % (prop, n), "r")
    model.mount = {"prop": prop, "seat": [0.0, height, 0.0], "bone": "mount", "kind": spec_m.get("kind", "broom")}


HANDS = ("right", "left", "both", "pair")


def weapon_defaults(prop):
    """What a weapon part says about how it is carried: `hand` (right; left: a shield; pair: claws; both: one prop
    for two hands, the left on `grip2`), `style` (the style library whose hand poses it is held in), `sheath`,
    `grip`, `grip2` (the second hand's grip point in grip space) and whether it has a `mount`."""
    part = load_part(prop)
    if part.get("category") != "weapons":
        raise HerogenError("art.weapon prop %s is not a weapons part" % prop)
    hand = part.get("hand", "right")
    if hand not in HANDS:
        raise HerogenError("part %s: hand %r is unknown (%s)" % (prop, hand, ", ".join(HANDS)))
    return {"hand": hand, "style": part.get("style"), "sheath": part.get("sheath", "none"),
            "grip": part.get("grip", "forward"), "grip2": part.get("grip2"), "mount": bool(part.get("mount")),
            "offhand": part.get("offhand")}


def apply_weapon(model, weapon, index=0):
    """Place art.weapon: the prop in hand (weapon_r / weapon_l) and the sheathed copy (weapon_back), an optional
    off-hand prop (`offhand`, in the left hand, from a phase with `phase`) and the mounted copy (`mount`)."""
    prop = weapon.get("prop")
    if not prop:
        return
    hand = weapon.get("hand", weapon_defaults(prop)["hand"])
    if hand not in ("right", "left", "both", "pair"):
        raise HerogenError("art.weapon.hand %r is unknown" % hand)
    sides = {"right": ["r"], "both": ["r"], "left": ["l"], "pair": ["r", "l"]}[hand]
    for side in sides:
        _place_weapon(model, weapon, index, side)
    off = weapon.get("offhand")
    if off:
        if hand in ("left", "pair", "both"):
            raise HerogenError("art.weapon.offhand needs the main weapon in the right hand only")
        phase = off.get("phase")
        if phase not in (None, 2, 3):
            raise HerogenError("art.weapon.offhand.phase must be 2 or 3")
        _place_weapon(model, off, index + 1, "l", "p%d_" % phase if phase else "")
    if weapon.get("mount"):
        _place_mount(model, weapon, index)
