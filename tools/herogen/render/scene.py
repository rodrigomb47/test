"""A hero ready to render: its built files loaded (geometry, textures, glow masks, visual.json, the animation chain)
and the per-frame rules HeroModel and HeroRenderer apply (HEROES_V2.md 4.6, 4.7, 4.9), all read from visual.json the
way the game reads them: the face frame offsets (UV shift of the `eyes`, `mouth` and `cheeks` bones), the faction tint
of the `tint` bones, the phase bones and texture, the drawn or sheathed weapon, the fine bones hidden beyond the fine
distance, and the glow layer.

`HeroAssets.build(id_or_hero)` builds in memory (style fixtures and preview variants too); `HeroAssets.committed(id)`
reads the files the mod ships. Either way the renderer only sees written bytes.
"""
import io
import json
import os

import numpy as np
from PIL import Image

from geo import GeoModel
from render import raster
from render.animfile import AnimChain, Playback
from render.face_sim import FaceSim
from util import ROOT

SASH_TINT = (74, 118, 196)          # a faction blue for the previews
PITCH = 10.0


def _png(data):
    return np.array(Image.open(io.BytesIO(data)).convert("RGBA"))


class HeroAssets:
    def __init__(self, hero_id, files, hero=None):
        """`files` maps the build's relative paths (build.out_paths and the libraries' paths) to bytes."""
        import build
        from anim import library as animlib
        self.id = hero_id
        self.hero = hero
        paths = build.out_paths(hero_id)
        self.geo = GeoModel(json.loads(files[paths["geo"]]))
        self.visual = json.loads(files[paths["visual"]])
        self.tex = _png(files[paths["texture"]])
        self.glow = _png(files[paths["glowmask"]]) if paths["glowmask"] in files else None
        self.tex_p2 = _png(files[paths["p2"]]) if paths["p2"] in files else None
        self.glow_p2 = _png(files[paths["p2_glowmask"]]) if paths["p2_glowmask"] in files else None
        chain = []
        for name in self.visual.get("animation_chain", [hero_id]):
            p = animlib.out_paths(name)["animation"]
            if p in files:
                chain.append((name, files[p]))
        self.chain = AnimChain(chain)
        self.name = (hero or {}).get("name", hero_id)

    @classmethod
    def build(cls, hero):
        """Build a hero id (herosrc/heroes or herosrc/style) or an in-memory hero object, with the shared libraries
        of its chain built fresh too."""
        import build
        from anim import library as animlib
        hero_id = hero["id"] if isinstance(hero, dict) else hero
        files, b = build.build_files(hero_id, hero if isinstance(hero, dict) else None)
        files = dict(files)
        files.update(animlib.core().files())
        files.update(animlib.style(animlib.style_of(b.hero)).files())
        a = cls(hero_id, files, b.hero)
        a.built = b
        return a

    @classmethod
    def committed(cls, hero_id):
        """The files the mod ships for this hero (and the libraries of its chain), read from the repository."""
        import build
        from anim import library as animlib
        files = {}
        wanted = list(build.out_paths(hero_id).values())
        visual_path = build.out_paths(hero_id)["visual"]
        with open(os.path.join(ROOT, visual_path), "rb") as f:
            visual = json.loads(f.read())
        for name in visual.get("animation_chain", []):
            wanted.append(animlib.out_paths(name)["animation"])
        for p in wanted:
            full = os.path.join(ROOT, p)
            if os.path.exists(full):
                with open(full, "rb") as f:
                    files[p] = f.read()
        hero = None
        try:
            hero = build.load_hero(hero_id)
        except Exception:                                   # noqa: BLE001 - the files are enough to render
            pass
        return cls(hero_id, files, hero)

    # -------------------------------------------------------------- the rules HeroModel applies per frame

    def bones(self, group):
        return self.visual["bones"].get(group, [])

    def hidden(self, phase=1, drawn=False, far=False):
        """Bones hidden in a frame: phase bones of a later phase, the sheathed copy while the weapon is drawn (or the
        drawn one while it is sheathed), and the fine bones beyond the fine distance (4.9)."""
        out = [b for b, p in self.visual["bones"].get("phase", {}).items() if phase < p]
        for ph in self.visual.get("phases", []):
            if ph["phase"] <= phase:
                out += ph.get("hide", [])
        if drawn:
            out += self.bones("weapon_sheathed")
        else:
            out += self.bones("weapon_drawn")
        if far:
            out += self.bones("fine")
        return out

    def face_shift(self, eyes=0, mouth=0, cheeks=0):
        """The UV shift (texels) HeroRenderer gives the face plane bones for these frames (4.6)."""
        out = {}
        for plane, k in (("eyes", eyes), ("mouth", mouth), ("cheeks", cheeks)):
            r = self.visual["face"]["regions"][plane]
            if not 0 <= k < r["frames"]:
                raise ValueError("%s frame %d (0-%d)" % (plane, k, r["frames"] - 1))
            out[plane] = ((k % r["columns"]) * r["w"], (k // r["columns"]) * r["h"])
        return out

    def tint(self, rgb=SASH_TINT):
        return {b: tuple(rgb) for b in self.bones("tint")} if rgb else None

    def textures(self, phase=1):
        """(texture, glow mask) of a phase look: the _p2 texture from phase 2 when the phase entry asks for it."""
        use_p2 = any(p["phase"] <= phase and p.get("texture") == "p2" for p in self.visual.get("phases", []))
        if use_p2 and self.tex_p2 is not None:
            return self.tex_p2, self.glow_p2
        return self.tex, self.glow

    def mood(self, name):
        m = self.visual["face"]["moods"][name]
        return (m["eyes"], m["mouth"], m["cheeks"])

    def face_sim(self, mood="calm", instance=0):
        return FaceSim(self.visual, "%s#%d" % (self.id, instance), mood)

    def playback(self, base=None, action=None, action_at=0.0):
        """A Playback of this hero's chain; base defaults to combat.idle under an action (if the chain has it), else
        move.idle."""
        if base is None:
            base = "combat.idle" if action and self.chain.get("combat.idle") else "move.idle"
        return Playback(self.chain, base, action, action_at)

    # -------------------------------------------------------------- drawing

    def render(self, view, pose=None, face=(0, 0, 0), phase=1, drawn=False, far=False, tint=SASH_TINT,
               bounds=None, pad=2, brightness=1.0, glow=True, frame=False):
        """One image (RGBA array, or a raster.Frame with frame=True)."""
        pose = {b: v for b, v in (pose or {}).items() if b in self.geo.bones}
        tex, gl = self.textures(phase)
        return raster.render(self.geo, tex, view, pose=pose, hidden=self.hidden(phase, drawn, far),
                             uv_shift=self.face_shift(*face), tint=self.tint(tint), glow=gl if glow else None,
                             bounds=bounds, pad=pad, brightness=brightness, frame=frame)

    def ties(self, view, pose=None, face=(0, 0, 0), phase=1, drawn=False, far=False, bounds=None, pad=2):
        """raster.coplanar_ties for this hero in a frame: (mask, {(bone, bone): pixels}, Frame)."""
        pose = {b: v for b, v in (pose or {}).items() if b in self.geo.bones}
        tex, _ = self.textures(phase)
        return raster.coplanar_ties(self.geo, tex, view, pose=pose, hidden=self.hidden(phase, drawn, far),
                                    uv_shift=self.face_shift(*face), tint=self.tint(), bounds=bounds, pad=pad)

    def bounds(self, views, poses=(None,), phase=1, drawn=False, far=False):
        """Common bounds of several views and poses."""
        bs = []
        for pose in poses:
            pose = {b: v for b, v in (pose or {}).items() if b in self.geo.bones}
            bs.append(raster.model_bounds(self.geo, views, self.hidden(phase, drawn, far), pose))
        return (min(b[0] for b in bs), max(b[1] for b in bs), min(b[2] for b in bs), max(b[3] for b in bs))

    def stance(self, drawn=False):
        """The pose the hero stands in: the first frame of combat.idle with the weapon drawn, else of move.idle (the
        bind pose if the chain has neither)."""
        name = "combat.idle" if drawn and self.chain.get("combat.idle") else "move.idle"
        clip = self.chain.get(name)
        return clip.sample(0.0) if clip is not None else {}


def to_image(arr):
    return Image.fromarray(arr if not isinstance(arr, raster.Frame) else arr.rgba, "RGBA")
