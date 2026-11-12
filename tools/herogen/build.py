"""`herogen build`: hero source -> model, texture(s), visual.json and the hero's own animation file.

Outputs (paths relative to the repository root):
  src/main/resources/assets/factionclash/geo/hero/<id>.geo.json
  src/main/resources/assets/factionclash/textures/entity/hero/<id>.png, <id>_glowmask.png, <id>_p2.png, ...
  src/main/resources/assets/factionclash/animations/hero/<id>.animation.json
  src/main/resources/assets/factionclash/heroes/<id>.visual.json
The server definition (data/factionclash/heroes/<id>.json) is written by the kit step (G3/F2), not here.
Builds are pure functions of the sources: `build_files` returns {path: bytes}, which `--check` compares to the
committed files.
"""
import glob
import os

from PIL import Image

import face as facemod
import palette
from geo import geo_json
from paint import paint_model
from parts import apply_part, apply_weapon, part_path
from rig import BASE_SLOTS, DEFAULT_BASE_LAYER, Model
from util import HEROSRC, ROOT, HerogenError, dumps_json, load_json, png_bytes, rel, sha256_bytes
from uvpack import pack
from visual import visual_json

ASSETS = "src/main/resources/assets/factionclash"
EMPTY_ANIMATIONS = {"format_version": "1.8.0", "animations": {}}


def hero_path(hero_id):
    return os.path.join(HEROSRC, "heroes", hero_id + ".hero.json")


def hero_ids():
    return sorted(os.path.basename(p)[:-len(".hero.json")] for p in glob.glob(os.path.join(HEROSRC, "heroes",
                                                                                           "*.hero.json")))


def out_paths(hero_id):
    return {
        "geo": "%s/geo/hero/%s.geo.json" % (ASSETS, hero_id),
        "texture": "%s/textures/entity/hero/%s.png" % (ASSETS, hero_id),
        "glowmask": "%s/textures/entity/hero/%s_glowmask.png" % (ASSETS, hero_id),
        "p2": "%s/textures/entity/hero/%s_p2.png" % (ASSETS, hero_id),
        "p2_glowmask": "%s/textures/entity/hero/%s_p2_glowmask.png" % (ASSETS, hero_id),
        "animation": "%s/animations/hero/%s.animation.json" % (ASSETS, hero_id),
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
    art = hero["art"]
    family = art.get("base") or hero.get("gender")
    if family not in ("female", "male"):
        raise HerogenError("%s: art.base (or gender) must be female or male" % hero["id"])
    build = art.get("build", "standard")
    if build not in ("slim", "standard", "large"):
        raise HerogenError("%s: art.build %r is unknown (slim, standard, large)" % (hero["id"], build))
    return family[0] + "_" + build


class Built:
    """Everything one build produced, in memory."""

    def __init__(self, hero, model, spec, ramps, image, glow, p2=None, p2_glow=None):
        self.hero, self.model, self.spec, self.ramps = hero, model, spec, ramps
        self.image, self.glow, self.p2, self.p2_glow = image, glow, p2, p2_glow


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
    model = Model(hero["id"], base_id_of(hero))
    for i, entry in enumerate(art.get("parts", []) or []):
        apply_part(model, entry, i)
    if art.get("weapon"):
        apply_weapon(model, art["weapon"])
    budgets = model.rig["budgets"]
    if len(model.bones) > budgets["bones"]:
        raise HerogenError("%s: %d bones (budget %d)" % (hero["id"], len(model.bones), budgets["bones"]))
    if len(model.cubes) > budgets["cubes"]:
        raise HerogenError("%s: %d cubes (budget %d)" % (hero["id"], len(model.cubes), budgets["cubes"]))
    pack(model)
    return model


def paint_all(model, hero, art_palette=None, face_changes=None):
    art = hero["art"]
    ramps = palette.resolve(art_palette if art_palette is not None else art.get("palette"), model.family)
    face = dict(art.get("face") or {})
    face.update(face_changes or {})
    spec = facemod.face_spec(face, model.family, ramps["_hex"])
    img, glow = paint_model(model, ramps, base_layer_of(art))
    facemod.paint_face(img, glow, model.rig, spec, ramps, ramps["_hex"])
    return spec, ramps, img, glow


def build(hero):
    model = assemble(hero)
    spec, ramps, img, glow = paint_all(model, hero)
    p2 = p2_glow = None
    phases = hero["art"].get("phases", []) or []
    changes = [ph for ph in phases if ph.get("palette")]
    if changes:
        pal = dict(hero["art"].get("palette", {}))
        for ph in sorted(changes, key=lambda p: p["phase"]):
            pal.update(ph["palette"])
        # a phase that recolours the eyes recolours the iris too, even when art.face.iris was authored
        face_changes = {"iris": pal["eyes"]} if any("eyes" in ph["palette"] for ph in changes) else None
        _, _, p2, p2_glow = paint_all(model, hero, pal, face_changes)
    return Built(hero, model, spec, ramps, img, glow, p2, p2_glow)


def source_hash(hero, model):
    """Hash of every source the build read, so a stale output is easy to spot."""
    paths = [hero_path(hero["id"]), os.path.join(HEROSRC, "rig", "rig_v1.json"),
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
    files = {
        paths["geo"]: dumps_json(geo_json(b.model)).encode("utf-8"),
        paths["texture"]: png_bytes(to_image(b.image)),
        paths["animation"]: dumps_json(EMPTY_ANIMATIONS).encode("utf-8"),
        paths["visual"]: dumps_json(visual_json(hero, b.model, b.spec, textures,
                                                source_hash(hero, b.model))).encode("utf-8"),
    }
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
