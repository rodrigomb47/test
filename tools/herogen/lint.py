"""herogen lint (HEROES_V2 13.3, task G6a): the parts library on every base, and the sample outfits.

For every part (or the ids and globs given):
- on each of the six bases in the standard build, alone on a mannequin (short hair, the default face, a sword at
  the hip; a hair part replaces the hair; G6c: a weapon is carried as its part says, in its hand, sheath and grip,
  and checked in each hand pose of its style, grips.py): the whole build (assembly, packing into
  the texture, painting, the animation chain), the asset checks of `herogen check` (budgets, whole-texel UVs, islands
  inside the texture, the clear face, the face planes), every cube above the ground in the rest pose, and the
  coplanar-face rule (no two coplanar faces of different colour in the idle stance and the combat stance with the
  sword drawn, from six directions: they z-fight in game);
- on the slim and large builds, and at the minimum and maximum of each param on the smallest and the largest base:
  assembly, packing and the geometry checks (widths and params must not break a part);
- once: every `conflicts` glob names an existing part, every mirrored cube (`<name>:l`) is the exact mirror of its
  right-hand cube, and the part declares what it `occupies` (weapons excepted).
Then every sample outfit (herosrc/style/outfits.json: the slice heroes' looks, combinations a single part never
shows, such as a sash over a hakama band) on all six bases, with the same checks as a part.

Problems are returned as lines "<what>: <problem>"; `herogen lint` prints them and fails on any.
"""
import fnmatch
import glob
import json
import os

import numpy as np

import build
import validate
from geo import GeoModel, geo_json
from parts import apply_part, apply_weapon, load_part, weapon_defaults
from rig import BASE_IDS, BUILDS, Model
from rot import rotate_points
from util import HEROSRC, HerogenError, load_json
from uvpack import pack

OUTFITS = os.path.join(HEROSRC, "style", "outfits.json")
# the mannequin a part is linted on: neutral body layer, short hair, the default face of the family
LINT_PALETTE = {"skin": "#F1D0B8", "hair": "#4A3F3A", "eyes": "#4F78C8", "primary": "#B5473A",
                "secondary": "#E4DCC8", "accent": "#3F5A86", "trim": "#D9A944", "metal": "#B9C1CD",
                "leather": "#5E4031", "hair_accent": "#C9A27E", "glow": "#8FE3FF"}
SLOT_MAP = {"color": "primary", "trim": "trim", "alt": "secondary", "detail": "accent"}
# the parts sheets dress the mannequin more naturally: a lighter hair colour (strands read), animal parts in the hair
# colours, horns in ivory, headwear in its own default slots
SHEET_PALETTE = dict(LINT_PALETTE, hair="#A8744C", hair_accent="#F2E6D4", accent="#3A3542")
SHEET_SLOTS = {"ears": {"color": "hair", "alt": "blush", "detail": "accent"},
               "tails": {"color": "hair", "alt": "hair_accent", "detail": "accent"},
               "wings": {"color": "accent", "alt": "secondary", "trim": "trim"},
               "horns": {"color": "secondary", "alt": "primary", "detail": "trim"}}
GROUND_EPS = 0.01
# G6c: the emblem the mannequin's stamps take (a part offers a stamp with "emblem": "@"; a hero entry names it), and
# the params a part is linted and shown with where its defaults would leave it empty
EMBLEM = "crest"
LINT_PARAMS = {"markings/crest": {"back": 1, "chest": 1, "sleeves": 1}}
# a part's own default colour that would vanish on the sheet mannequin (the crest's cream on a cream shirt)
SHEET_PART_SLOTS = {"markings/crest": {"color": "primary"}}


def weapon_entry(pid, params=None, sheet=False):
    """The art.weapon entry a weapon part is linted and shown with: its own hand, sheath and grip (the part's
    defaults), the mounted copy when it has one, the mannequin's emblem, and the lint colours (the sheets keep the
    part's own slots: steel, leather, wood)."""
    d = weapon_defaults(pid)
    entry = {"prop": pid, "emblem": EMBLEM}
    if not sheet:
        entry.update(SLOT_MAP)
    if params:
        entry["params"] = params
    if d["mount"]:
        entry["mount"] = True
    return entry


def part_ids(patterns=None):
    """Every part id of the library (herosrc/parts/<category>/<name>.json), or those matching the globs given."""
    root = os.path.join(HEROSRC, "parts")
    ids = sorted(os.path.relpath(p, root)[:-5].replace(os.sep, "/")
                 for p in glob.glob(os.path.join(root, "*", "*.json")))
    if patterns:
        ids = [i for i in ids if any(fnmatch.fnmatchcase(i, p) for p in patterns)]
    return ids


def lint_hero(pid, base_id, params=None, sheet=False):
    """A mannequin on `base_id` wearing part `pid` (an in-memory hero). `sheet` dresses it for the parts sheets
    (natural colours) instead of the lint colours (every slot distinct, so a coplanar face cannot hide)."""
    form, fam = base_id.split("_")
    family = "female" if fam == "f" else "male"
    parts = [{"part": "hair/short_crop", "color": "hair"}]
    art = {"base": family, "form": form, "build": "standard",
           "palette": dict(SHEET_PALETTE if sheet else LINT_PALETTE), "parts": parts,
           "base_layer": {"base_top": "secondary", "base_bottom": "accent", "shoes": "leather"}}
    entry = {"part": pid, "emblem": EMBLEM}
    cat = pid.split("/")[0]
    if not sheet:
        entry.update(SLOT_MAP)
    elif cat in SHEET_SLOTS:
        entry.update(SHEET_SLOTS[cat])
    elif cat not in ("headwear", "accessories", "markings"):
        entry.update(SLOT_MAP)          # clothes in the sheet colours; headwear and details in their own slots
    if sheet:
        entry.update(SHEET_PART_SLOTS.get(pid, {}))
    params = dict(LINT_PARAMS.get(pid, {}), **(params or {}))
    if params:
        entry["params"] = params
    style = None
    if pid.startswith("weapons/"):
        # G6c: the prop as its part says it is carried (hand, sheath, grip), in the hand poses of its style
        art["weapon"] = weapon_entry(pid, params, sheet)
        style = weapon_defaults(pid)["style"]
    elif not sheet:
        # a sword at the hip: the coplanar rule also runs on the combat stance (arms raised: the ends of sleeves,
        # cuffs and gauntlets show) and on a scabbard against skirts and coats
        art["weapon"] = {"prop": "weapons/sword_plain", "hand": "right", "sheathed": "hip"}
    if pid.startswith("hair/"):
        parts[0] = dict(entry, color="hair", detail="hair_accent", alt="primary" if sheet else "secondary")
    elif not pid.startswith("weapons/"):
        parts.append(entry)
    safe = pid.replace("/", "_")
    anims = {"blink": "normal"}
    if style:
        anims["style"] = style
    return {"format": 2, "id": "lint_%s_%s" % (safe, base_id), "name": "%s on %s" % (pid, base_id),
            "gender": family, "art": art, "animations": anims}


def outfit_hero(outfit, base_id):
    """A sample outfit (an entry of herosrc/style/outfits.json) on a base: its palette, parts, face and weapon."""
    form, fam = base_id.split("_")
    family = "female" if fam == "f" else "male"
    art = json.loads(json.dumps(outfit["art"]))
    art.update({"base": family, "form": form, "build": art.get("build", "standard")})
    return {"format": 2, "id": "outfit_%s_%s" % (outfit["id"], base_id), "name": outfit.get("name", outfit["id"]),
            "gender": family, "art": art, "animations": json.loads(json.dumps(outfit.get("animations",
                                                                                     {"blink": "normal"})))}


def load_outfits():
    data = load_json(OUTFITS)
    return data["outfits"]


def _below_ground(model):
    out = []
    for c in model.cubes:
        x0, y0, z0 = c.origin
        sx, sy, sz = c.size
        pts = [(x0 + a, y0 + b, z0 + d) for a in (0, sx) for b in (0, sy) for d in (0, sz)]
        if c.rotation is not None:
            pts = rotate_points(pts, c.rotation, c.pivot)
        low = min(p[1] for p in pts) - c.inflate
        if low < -GROUND_EPS:
            out.append("%s reaches %.2f px below the ground" % (c.name, -low))
    return out


def full_check(hero, ties=True):
    """Build an in-memory hero and run the asset checks, the ground rule and (optionally) the coplanar-face rule."""
    import io

    from PIL import Image

    import cli
    hid = hero["id"]
    try:
        files, b = build.build_files(hid, hero)
    except HerogenError as e:
        return [str(e)]
    paths = build.out_paths(hid)
    geo = GeoModel(json.loads(files[paths["geo"]]))
    tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
    problems = validate.check_built(geo, tex, asymmetric=b.spec["asymmetric"])
    problems += _below_ground(b.model)
    if ties:
        problems += cli.tie_problems(hid, files, b, hint=False)
    problems += hand_pose_problems(hero, files, b, ties)
    return problems


def hand_pose_problems(hero, files, b, ties=True):
    """G6c: the weapon in each hand pose of its style (grips.py): clear of the body and the ground, the second hand
    on a two-handed prop, and (with `ties`) the coplanar rule with the weapon drawn."""
    import grips
    if grips.style_of_weapon(hero)[0] is None:
        return []
    assets = None
    if ties:
        from anim import library as animlib
        from render.scene import HeroAssets
        files = dict(files)
        files.update(animlib.core().files())
        files.update(animlib.style(animlib.style_of(b.hero)).files())
        assets = HeroAssets(hero["id"], files, b.hero)
    return grips.problems(hero, b, assets, ties)


def geometry_check(pid, base_id, build_name="standard", params=None):
    """Assembly, packing and the geometry checks only (no painting): the cheap pass for builds and param extremes."""
    m = Model("lint", base_id, build_name)
    try:
        if pid.startswith("weapons/"):
            apply_weapon(m, weapon_entry(pid, params or {}))
        else:
            entry = {"part": pid, "params": dict(LINT_PARAMS.get(pid, {}), **(params or {})), "emblem": EMBLEM}
            entry.update(SLOT_MAP)
            apply_part(m, entry, 0)
        pack(m)
    except HerogenError as e:
        return [str(e)]
    geo = GeoModel(json.loads(json.dumps(geo_json(m))))
    return validate.check_geometry(geo, np.zeros((m.tex_h, m.tex_w, 4), dtype=np.uint8)) + _below_ground(m)


def static_check(pid, all_ids):
    """Base-independent rules: conflicts name parts, mirrored cubes mirror, occupies is declared."""
    part = load_part(pid)
    out = []
    for g in part.get("conflicts", []):
        if not any(fnmatch.fnmatchcase(i, g) for i in all_ids):
            out.append("conflicts %r matches no part" % g)
    if not part.get("occupies") and part.get("category") != "weapons":
        out.append("declares no occupies tags (two parts on one slot could not be refused)")
    m = Model("lint", "adult_m")
    try:
        if pid.startswith("weapons/"):
            apply_weapon(m, weapon_entry(pid))
        else:
            entry = {"part": pid, "emblem": EMBLEM}
            if pid in LINT_PARAMS:
                entry["params"] = dict(LINT_PARAMS[pid])
            entry.update(SLOT_MAP)
            apply_part(m, entry, 0)
    except HerogenError as e:
        return out + [str(e)]
    by_name = {c.name: c for c in m.cubes}
    for c in m.cubes:
        if not c.name.endswith(":l") or c.part != pid:
            continue
        r = by_name.get(c.name[:-2])
        if r is None:
            out.append("%s has no right-hand cube" % c.name)
            continue
        want = (round(-(r.origin[0] + r.size[0]), 3), round(r.origin[1], 3), round(r.origin[2], 3))
        got = tuple(round(v, 3) for v in c.origin)
        if want != got or tuple(round(v, 3) for v in r.size) != tuple(round(v, 3) for v in c.size):
            out.append("%s is not the mirror of %s" % (c.name, r.name))
        if r.rotation is not None:
            mr = (r.rotation[0], -r.rotation[1], -r.rotation[2])
            if c.rotation is None or any(abs(a - b) > 1e-6 for a, b in zip(mr, c.rotation)):
                out.append("%s is not rotated as the mirror of %s" % (c.name, r.name))
    return out


def _job(job):
    kind = job[0]
    if kind == "part":
        _, pid, base_id, ties = job
        return "%s on %s" % (pid, base_id), full_check(lint_hero(pid, base_id), ties)
    if kind == "geometry":
        _, pid, base_id, build_name, params = job
        what = "%s on %s %s%s" % (pid, base_id, build_name,
                                  " " + ", ".join("%s=%g" % kv for kv in sorted(params.items())) if params else "")
        return what, geometry_check(pid, base_id, build_name, params)
    if kind == "outfit":
        _, outfit, base_id, ties = job
        return "outfit %s on %s" % (outfit["id"], base_id), full_check(outfit_hero(outfit, base_id), ties)
    raise ValueError(kind)


def jobs(ids, ties=True, outfits=True, builds=True, params=True):
    out = []
    for pid in ids:
        for base_id in BASE_IDS:
            out.append(("part", pid, base_id, ties))
        if builds:
            for base_id in BASE_IDS:
                for build_name in BUILDS:
                    if build_name != "standard":
                        out.append(("geometry", pid, base_id, build_name, {}))
        if params:
            spec = load_part(pid).get("params", {})
            for name, p in sorted(spec.items()):
                for v in (p.get("min"), p.get("max")):
                    if v is None:
                        continue
                    for base_id in ("chibi_f", "adult_m"):
                        out.append(("geometry", pid, base_id, "standard", {name: v}))
    if outfits:
        for o in load_outfits():
            for base_id in BASE_IDS:
                out.append(("outfit", o, base_id, ties))
    return out


def run(patterns=None, ties=True, outfits=True, workers=None, progress=None):
    """Lint the library (or the parts matching `patterns`) and the sample outfits. Returns the problem lines."""
    all_ids = part_ids()
    ids = part_ids(patterns)
    if patterns and not ids:
        raise HerogenError("no part matches %s" % ", ".join(patterns))
    problems = []
    for pid in ids:
        problems += ["%s: %s" % (pid, p) for p in static_check(pid, all_ids)]
    todo = jobs(ids, ties=ties, outfits=outfits and not patterns)
    workers = workers or min(4, os.cpu_count() or 1)
    if workers > 1:
        import multiprocessing
        ctx = multiprocessing.get_context("fork")
        with ctx.Pool(workers) as pool:
            results = pool.imap(_job, todo, chunksize=2)
            for what, found in results:
                problems += ["%s: %s" % (what, p) for p in found]
                if progress:
                    progress(what, found)
    else:
        for job in todo:
            what, found = _job(job)
            problems += ["%s: %s" % (what, p) for p in found]
            if progress:
                progress(what, found)
    return problems
