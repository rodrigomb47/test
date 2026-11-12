"""GeckoLib / Bedrock geometry writer (format_version 1.12.0) and a small reader for the preview and the checks.

Per-face UVs only (never box UV): every face has a whole-texel rectangle from the packer. The bottom face is written
flipped in v (uv at the rectangle's bottom edge, negative height), which is how GeckoLib's own box-UV mapping
addresses it, so a face looks exactly as it would with box UV (vanilla skin orientation).
"""
import json

from uvpack import from_file_uv, to_file_uv
from util import HerogenError

FORMAT_VERSION = "1.12.0"


def cube_json(c):
    d = {"origin": list(c.origin), "size": list(c.size), "uv": {}}
    for face, rect in sorted(c.uv.items()):
        uv, size = to_file_uv(face, rect)
        d["uv"][face] = {"uv": uv, "uv_size": size}
    if c.inflate:
        d["inflate"] = c.inflate
    if c.rotation is not None and any(abs(a) > 1e-9 for a in c.rotation):
        d["rotation"] = list(c.rotation)
        d["pivot"] = list(c.pivot)
    return d


def geo_json(model):
    bones = []
    by_bone = {}
    for c in sorted(model.cubes, key=lambda c: c.order):
        by_bone.setdefault(c.bone, []).append(c)
    for b in model.ordered_bones():
        d = {"name": b.name, "pivot": list(b.pivot)}
        if b.parent:
            d["parent"] = b.parent
        if any(abs(a) > 1e-9 for a in b.rotation):
            d["rotation"] = list(b.rotation)
        cubes = [cube_json(c) for c in by_bone.get(b.name, [])]
        if cubes:
            d["cubes"] = cubes
        bones.append(d)
    return {
        "format_version": FORMAT_VERSION,
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.factionclash.hero.%s" % model.hero_id.lstrip("_"),
                "texture_width": model.tex_w,
                "texture_height": model.tex_h,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": bones,
        }],
    }


class GeoCube:
    def __init__(self, bone, d):
        self.bone = bone
        self.origin = tuple(d["origin"])
        self.size = tuple(d["size"])
        self.inflate = d.get("inflate", 0)
        self.rotation = tuple(d["rotation"]) if "rotation" in d else None
        self.pivot = tuple(d["pivot"]) if "pivot" in d else None
        if "uv" not in d or not isinstance(d["uv"], dict):
            raise HerogenError("bone %s: a cube without per-face uv" % bone)
        self.uv = {f: from_file_uv(f, v["uv"], v["uv_size"]) for f, v in d["uv"].items()}
        self.raw_uv = {f: (tuple(v["uv"]), tuple(v["uv_size"])) for f, v in d["uv"].items()}


class GeoModel:
    """A parsed .geo.json: bones in file order, cubes with net rectangles."""

    def __init__(self, data):
        if data.get("format_version") != FORMAT_VERSION:
            raise HerogenError("geometry format_version is %r, expected %s" % (data.get("format_version"),
                                                                              FORMAT_VERSION))
        geo = data["minecraft:geometry"][0]
        desc = geo["description"]
        self.tex_w, self.tex_h = desc["texture_width"], desc["texture_height"]
        self.bones = {}
        self.order = []
        self.cubes = []
        for b in geo["bones"]:
            self.bones[b["name"]] = b
            self.order.append(b["name"])
            for c in b.get("cubes", []):
                self.cubes.append(GeoCube(b["name"], c))

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def parent(self, name):
        return self.bones[name].get("parent")
