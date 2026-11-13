"""`herogen build`: hero source -> model, texture(s), visual.json and the hero's own animation file; plus the shared
animation libraries.

Outputs (paths relative to the repository root):
  src/main/resources/assets/factionclash/geo/hero/<id>.geo.json
  src/main/resources/assets/factionclash/textures/entity/hero/<id>.png, <id>_glowmask.png, <id>_p2.png, ...
  src/main/resources/assets/factionclash/animations/hero/<id>.animation.json   (anim/library.py, G3)
  src/main/resources/data/factionclash/hero_timings/<id>.json                  (its server timing file)
  src/main/resources/assets/factionclash/heroes/<id>.visual.json
Shared (library_files): animations/hero/_core.animation.json and _style_<style>.animation.json for every style a
hero uses or that has recipes, each with its timing file. The server definition (data/factionclash/heroes/<id>.json,
with each move's `frames` taken from the timing data, anim/timing.py) is written by the kit step (F2), not here.
Builds are pure functions of the sources: `build_files` returns {path: bytes}, which `--check` compares to the
committed files.
"""
import glob
import os

from PIL import Image

import face as facemod
import palette
from anim import library as animlib
from geo import geo_json
from paint import has_phase_glow, paint_model
from parts import apply_part, apply_weapon, part_path, settle_leg_shells
from rig import BASE_SLOTS, BUILDS, DEFAULT_BASE_LAYER, FORMS, RIG_FILE, Model, base_id_for
from util import HEROSRC, ROOT, HerogenError, dumps_json, load_json, png_bytes, rel, sha256_bytes
from uvpack import pack
from visual import visual_json

ASSETS = "src/main/resources/assets/factionclash"


STYLE_DIR = os.path.join(HEROSRC, "style")


def hero_path(hero_id):
    """herosrc/heroes/<id>.hero.json, or herosrc/style/<id>.hero.json for the style fixtures (built for previews and
    tests only; their assets are never written to the mod's resources)."""
    path = os.path.join(HEROSRC, "heroes", hero_id + ".hero.json")
    style = os.path.join(STYLE_DIR, hero_id + ".hero.json")
    return style if not os.path.exists(path) and os.path.exists(style) else path


def hero_ids():
    return sorted(os.path.basename(p)[:-len(".hero.json")] for p in glob.glob(os.path.join(HEROSRC, "heroes",
                                                                                           "*.hero.json")))


def style_ids():
    return sorted(os.path.basename(p)[:-len(".hero.json")] for p in glob.glob(os.path.join(STYLE_DIR,
                                                                                           "*.hero.json")))


def out_paths(hero_id):
    return {
        "geo": "%s/geo/hero/%s.geo.json" % (ASSETS, hero_id),
        "texture": "%s/textures/entity/hero/%s.png" % (ASSETS, hero_id),
        "glowmask": "%s/textures/entity/hero/%s_glowmask.png" % (ASSETS, hero_id),
        "p2": "%s/textures/entity/hero/%s_p2.png" % (ASSETS, hero_id),
        "p2_glowmask": "%s/textures/entity/hero/%s_p2_glowmask.png" % (ASSETS, hero_id),
        "animation": animlib.out_paths(hero_id)["animation"],
        "timing": animlib.out_paths(hero_id)["timing"],
        "visual": "%s/heroes/%s.visual.json" % (ASSETS, hero_id),
    }


def load_hero(hero_id):
    path = hero_path(hero_id)
    hero = load_json(path)
    if hero.get("format") != 2:
        raise HerogenError("%s: format must be 2" % rel(path))
    if hero.get("id") != hero_id:
        raise HerogenError("%s: id is %r, expected %r (the file name)" % (rel(path), hero.get("id"), hero_id))
    if "art" not in hero:
        raise HerogenError("%s: no art block" % rel(path))
    return hero


def base_id_of(hero):
    """(base id, build) of a hero: art.form (chibi, teen, adult; default adult) and art.base (female, male; default
    the gender) pick the base, art.build (slim, standard, large) its widths."""
    art = hero["art"]
    family = art.get("base") or hero.get("gender")
    if family not in ("female", "male"):
        raise HerogenError("%s: art.base (or gender) must be female or male" % hero["id"])
    form = art.get("form", "adult")
    if form not in FORMS:
        raise HerogenError("%s: art.form %r is unknown (%s)" % (hero["id"], form, ", ".join(FORMS)))
    build = art.get("build", "standard")
    if build not in BUILDS:
        raise HerogenError("%s: art.build %r is unknown (%s)" % (hero["id"], build, ", ".join(BUILDS)))
    return base_id_for(form, family), build


class Built:
    """Everything one build produced, in memory."""

    def __init__(self, hero, model, spec, ramps, image, glow, p2=None, p2_glow=None):
        self.hero, self.model, self.spec, self.ramps = hero, model, spec, ramps
        self.image, self.glow, self.p2, self.p2_glow = image, glow, p2, p2_glow
        self.animations = None          # the hero's own animation file (anim.library.Built), set by build_files


def base_layer_of(art):
    layer = dict(DEFAULT_BASE_LAYER)
    for k, v in (art.get("base_layer") or {}).items():
        if k not in BASE_SLOTS:
            raise HerogenError("art.base_layer: %r is not a base slot (%s)" % (k, ", ".join(BASE_SLOTS)))
        layer[k] = v
    return layer


def assemble(hero):
    """Model with parts and weapon applied and UVs packed (not painted)."""
    art = hero["art"]
    base_id, build_name = base_id_of(hero)
    model = Model(hero["id"], base_id, build_name)
    for i, entry in enumerate(art.get("parts", []) or []):
        apply_part(model, entry, i)
    if art.get("weapon"):
        apply_weapon(model, art["weapon"])
    settle_leg_shells(model)
    budgets = model.rig["budgets"]
    if len(model.bones) > budgets["bones"]:
        raise HerogenError("%s: %d bones (budget %d)" % (hero["id"], len(model.bones), budgets["bones"]))
    if len(model.cubes) > budgets["cubes"]:
        raise HerogenError("%s: %d cubes (budget %d)" % (hero["id"], len(model.cubes), budgets["cubes"]))
    pack(model)
    return model


def paint_all(model, hero, art_palette=None, face_changes=None, phase_look=False):
    art = hero["art"]
    ramps = palette.resolve(art_palette if art_palette is not None else art.get("palette"), model.family)
    face = dict(art.get("face") or {})
    face.update(face_changes or {})
    spec = facemod.face_spec(face, model.family, ramps["_hex"])
    img, glow = paint_model(model, ramps, base_layer_of(art), phase_look)
    facemod.paint_face(img, glow, model.rig, spec, ramps, ramps["_hex"])
    return spec, ramps, img, glow


def build(hero):
    model = assemble(hero)
    spec, ramps, img, glow = paint_all(model, hero)
    p2 = p2_glow = None
    phases = hero["art"].get("phases", []) or []
    changes = [ph for ph in phases if ph.get("palette")]
    # G6c: a cube or paint op with glow "phase" lights up in the phase look (Benika's arm stripes from phase 2), so
    # a hero with a later phase gets the phase texture even without a palette change
    phase_glow = has_phase_glow(model) and any(ph.get("phase", 1) >= 2 for ph in phases)
    if changes or phase_glow:
        pal = dict(hero["art"].get("palette", {}))
        for ph in sorted(changes, key=lambda p: p["phase"]):
            pal.update(ph["palette"])
        # a phase that recolours the eyes recolours the iris too, even when art.face.iris was authored
        face_changes = {"iris": pal["eyes"]} if any("eyes" in ph["palette"] for ph in changes) else None
        _, _, p2, p2_glow = paint_all(model, hero, pal, face_changes, phase_look=True)
    return Built(hero, model, spec, ramps, img, glow, p2, p2_glow)


def source_hash(hero, model):
    """Hash of every source the build read, so a stale output is easy to spot."""
    paths = [hero_path(hero["id"]), os.path.join(HEROSRC, "rig", RIG_FILE),
             os.path.join(HEROSRC, "bases", model.base_id + ".json"),
             os.path.join(HEROSRC, "bases", model.base.get("template", "_template") + ".json")]
    for pid in sorted(set(model.parts_used + ([hero["art"]["weapon"]["prop"]] if hero["art"].get("weapon") else []))):
        if not pid.startswith("inline/"):
            paths.append(part_path(pid))
    data = b""
    for p in paths:
        if os.path.exists(p):
            with open(p, "rb") as f:
                data += rel(p).encode() + b"\0" + f.read() + b"\0"
    return sha256_bytes(data)


def to_image(arr):
    return Image.fromarray(arr, "RGBA")


def build_files(hero_id, hero=None):
    hero = hero or load_hero(hero_id)
    b = build(hero)
    paths = out_paths(hero_id)
    textures = {"width": b.model.tex_w, "height": b.model.tex_h, "glowmask": bool(b.glow[:, :, 3].any()),
                "glow_outside_face": _glow_outside_face(b), "p2": b.p2 is not None}
    anims = animlib.hero(hero, hero_path(hero_id))
    files = {
        paths["geo"]: dumps_json(geo_json(b.model)).encode("utf-8"),
        paths["texture"]: png_bytes(to_image(b.image)),
        paths["visual"]: dumps_json(visual_json(hero, b.model, b.spec, textures,
                                                source_hash(hero, b.model))).encode("utf-8"),
    }
    files.update(anims.files())
    b.animations = anims
    if textures["glowmask"]:
        files[paths["glowmask"]] = png_bytes(to_image(b.glow))
    if b.p2 is not None:
        files[paths["p2"]] = png_bytes(to_image(b.p2))
        if b.p2_glow[:, :, 3].any():
            files[paths["p2_glowmask"]] = png_bytes(to_image(b.p2_glow))
    return files, b


def _glow_outside_face(b):
    """True when the glow mask has texels other than the glowing eye frame (the renderer can skip the glow layer
    unless that frame shows when this is false)."""
    g = b.glow[:, :, 3].copy()
    u, v, w, h = facemod.frame_rect(b.model.rig, "eyes", 10)
    g[v:v + h, u:u + w] = 0
    return bool(g.any())


def library_styles():
    """The style libraries the mod ships: every style a hero in herosrc/heroes uses, and every style with recipes."""
    styles = set(animlib.styles_with_recipes())
    for hid in hero_ids():
        styles.add(animlib.style_of(load_hero(hid)))
    return sorted(styles)


def library_files():
    """{path: bytes} of the shared animation libraries and their timing files."""
    return animlib.library_files(library_styles())


def stale_library_files(files, root=ROOT):
    """Library outputs on disk that the build no longer produces (a style nobody uses any more)."""
    out = []
    for pattern in (animlib.ANIM_OUT % "_*", animlib.TIMING_OUT % "_*"):
        for full in sorted(glob.glob(os.path.join(root, pattern))):
            path = rel(full)
            stem = os.path.basename(full).split(".")[0]
            if path not in files and (stem == animlib.CORE or stem.startswith("_style_")):
                out.append(path)
    return out


def check_library_files(files, root=ROOT):
    problems = []
    for path, data in sorted(files.items()):
        full = os.path.join(root, path)
        if not os.path.exists(full):
            problems.append("%s: missing (run herogen build)" % path)
            continue
        with open(full, "rb") as f:
            if f.read() != data:
                problems.append("%s: differs from a fresh build (run herogen build)" % path)
    for path in stale_library_files(files, root):
        problems.append("%s: stale (no hero uses that library any more)" % path)
    return problems


def write_files(files, root=ROOT):
    for path, data in sorted(files.items()):
        full = os.path.join(root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)


def stale_files(hero_id, files, root=ROOT):
    """Outputs of this hero on disk that the build no longer produces (e.g. a _p2.png after a phase was removed)."""
    out = []
    for key, path in out_paths(hero_id).items():
        if path not in files and os.path.exists(os.path.join(root, path)):
            out.append(path)
    return out


def check_files(hero_id, files, root=ROOT):
    """Differences between a fresh build and the files on disk."""
    problems = []
    for path, data in sorted(files.items()):
        full = os.path.join(root, path)
        if not os.path.exists(full):
            problems.append("%s: missing (run herogen build %s)" % (path, hero_id))
            continue
        with open(full, "rb") as f:
            if f.read() != data:
                problems.append("%s: differs from a fresh build (run herogen build %s)" % (path, hero_id))
    for path in stale_files(hero_id, files, root):
        problems.append("%s: stale (the build no longer writes it)" % path)
    return problems
