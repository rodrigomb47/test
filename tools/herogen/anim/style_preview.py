"""Previews of the style libraries (task G5): docs/heroes-v2/previews/anim/styles/.

Each slice style is shown on two bases, a chibi and an adult, worn by the sample outfit (herosrc/style/outfits.json)
whose weapon uses it, so the props are the slice's own: Natsuho's claws (fists), Hinae's wand and talismans and
Yugen's fan (focus), Benika's club (hammer), Kagenori's sword (katana), Tobimaru's reversed blade and kunai
(twin_blades), Ilsabet's sword and shield (sword_shield), Ambrose's staff (staff). Per style:

  <style>_string.gif   the basic string attack.1 -> attack.2 -> attack.3 back to back over combat.idle, as the server
                       sends it (each code when the last one ends), weapon drawn
  <style>_set.gif      the rest of the library in turn: draw (from the idle, sheathed), guard -> guard.loop ->
                       guard.hit -> guard.hit.loop, dodge, attack.ranged (where the style has one), taunt, intro,
                       victory, sheathe, the style's combat.run, and its work strokes (tools as preview props)
  <style>.png          a contact sheet of every clip the style defines (7 frames each, both heroes)

The films play the written animation files through the controller emulation of render/gif.py and
anim/core_preview.py (an action blends for 2 ticks from the pose it cuts), with HeroFace's frames and blinks. What
G5 adds is the weapon flag: `draw` starts sheathed and `sheathe` drawn, and the flag swaps on their
`weapon:drawn` / `weapon:sheathed` markers, as the server will swap it from the timing file; with it the base
controller switches between move.idle and combat.idle with its 4-tick blend (HEROES_V2 4.4), which moves the legs
the action leaves to it. `herogen anim-previews --styles [--check]` writes (or checks) them.
"""
import copy
import os

from PIL import Image, ImageDraw

from anim.clip import TICK
from anim.core_preview import PROPS, SequencePlayback
from anim.writer import TRANSITION_TICKS
from render import gif, raster
from render.scene import HeroAssets, to_image
from render.sheet import BG, DIM, INK, font
from util import ROOT

OUT_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "anim", "styles")
YAW, PITCH, SCALE, FPS = -40.0, 10.0, 3, 20
LEAD, TAIL = 0.3, 0.3
BASE_BLEND = TRANSITION_TICKS["base"] * TICK

# style: [(sample outfit, base)]: the chibi first, then the adult
HEROES = {
    "fists": [("natsuho", "chibi_f"), ("natsuho", "adult_f")],
    "focus": [("hinae", "chibi_f"), ("yugen", "adult_m")],
    "hammer": [("benika", "chibi_f"), ("benika", "adult_f")],
    "katana": [("kagenori", "chibi_m"), ("kagenori", "adult_m")],
    "twin_blades": [("tobimaru", "chibi_m"), ("tobimaru", "adult_m")],
    "sword_shield": [("ilsabet", "chibi_f"), ("ilsabet", "adult_f")],
    "staff": [("ambrose", "chibi_m"), ("ambrose", "adult_m")],
}
STRING = ("attack.1", "attack.2", "attack.3")
WORK_PROPS = {"work.chop": "axe", "work.mine": "pickaxe"}


def outfit_hero(oid, base):
    import lint
    outfit = [o for o in lint.load_outfits() if o["id"] == oid][0]
    return lint.outfit_hero(outfit, base)


def prop_variant(hero, prop):
    """The hero with a work tool at item_r (anim/core_preview's preview props), for a style's work strokes."""
    hero = copy.deepcopy(hero)
    hero["id"] = "%s_%s" % (hero["id"], prop)
    cubes = []
    for i, (o, s, mat) in enumerate(PROPS[prop]):
        cubes.append(dict(mat, name="prop%d" % i, bone="hand_r", size=list(s),
                          origin=["-shoulder_x + %g" % o[0], "hand_y + %g" % o[1], o[2]]))
    hero["art"].setdefault("parts", []).append({"part": {"id": "inline/preview_%s" % prop, "category": "props",
                                                         "cubes": cubes}})
    return hero


class StylePlayback(SequencePlayback):
    """SequencePlayback with the weapon flag: it starts `drawn` (or sheathed) and swaps on the actions'
    weapon:drawn / weapon:sheathed markers; the base controller plays combat.idle while drawn and move.idle while
    sheathed, switching with its 4-tick blend (only the bones no action keys show it)."""

    def __init__(self, chain, actions, drawn=True, d=0.0, base=None):
        first = base or ("combat.idle" if drawn else "move.idle")
        super().__init__(chain, first, actions, d)
        self.fixed_base = base
        self.swaps = []                                    # (time, drawn after)
        state = drawn
        for clip, at in self.seq:
            for e in sorted(clip.events, key=lambda e: e.t):
                if e.kind == "instruction" and e.value in ("weapon:drawn", "weapon:sheathed"):
                    state = e.value == "weapon:drawn"
                    self.swaps.append((at + e.t, state))
        self.start_drawn = drawn

    def drawn(self, t):
        state = self.start_drawn
        for at, s in self.swaps:
            if t + 1e-9 >= at:
                state = s
        return state

    def _base_clip(self, drawn):
        return self.chain.get("combat.idle" if drawn else "move.idle")

    def base_pose(self, t):
        if self.fixed_base is not None:
            return self.base.sample(t, self.d)
        state, since, prev = self.start_drawn, None, None
        for at, s in self.swaps:
            if t + 1e-9 >= at and s != state:
                prev, state, since = state, s, at
        pose = self._base_clip(state).sample(t, self.d)
        if since is not None and t - since < BASE_BLEND - 1e-9:
            old = self._base_clip(prev).sample(since, self.d)
            f = (t - since) / BASE_BLEND
            for bone, chans in old.items():
                for ch, v in chans.items():
                    rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
                    to = pose.get(bone, {}).get(ch, rest)
                    pose.setdefault(bone, {})[ch] = tuple(a + (b - a) * f for a, b in zip(v, to))
        return pose


class Segment:
    """A labelled stretch of a film: actions back to back (name, seconds to show a loop for), or a base loop."""

    def __init__(self, items, drawn=True, prop=None, label=None, base_loop=None):
        self.items, self.drawn, self.prop = items, drawn, prop
        self.base_loop = base_loop
        self.label = label or " > ".join(n for n, _ in items)

    def build(self, assets):
        chain = assets.chain
        d = assets.form_var()
        if self.base_loop:
            pb = StylePlayback(chain, [], self.drawn, d, base=self.base_loop)
            return pb, self.items[0][1] or chain.get(self.base_loop).length
        at, actions = LEAD, []
        blend = TRANSITION_TICKS["action"] * TICK
        for name, secs in self.items:
            clip = chain.get(name)
            if clip is None:
                raise KeyError("%s has no %s" % (assets.id, name))
            actions.append((name, at))
            at += blend + (secs if secs is not None else clip.length)
        return StylePlayback(chain, actions, self.drawn, d), at + TAIL


def _has(style, name):
    from anim import library as animlib
    return name in animlib.style(style).by_name


def string_segments(style):
    return [Segment([(n, None) for n in STRING], label="the string: attack.1 > attack.2 > attack.3")]


def set_segments(style):
    out = []
    if _has(style, "draw"):
        out.append(Segment([("draw", None)], drawn=False))
    if _has(style, "guard"):
        out.append(Segment([("guard", None), ("guard.loop", 0.8), ("guard.hit", None), ("guard.hit.loop", 0.5)],
                           label="guard > .loop > guard.hit > .loop"))
    for name in ("dodge", "attack.ranged", "taunt", "intro", "victory"):
        if _has(style, name):
            out.append(Segment([(name, None)]))
    if _has(style, "sheathe"):
        out.append(Segment([("sheathe", None)]))
    if _has(style, "combat.run"):
        out.append(Segment([("combat.run", 1.2)], base_loop="combat.run", label="combat.run (the run, weapon drawn)"))
    for name, prop in sorted(WORK_PROPS.items()):
        if _has(style, name):
            out.append(Segment([(name, None)], drawn=False, prop=prop, label="%s (preview tool)" % name))
    return out


def _assets(cache, oid, base, prop=None):
    key = (oid, base, prop)
    if key not in cache:
        hero = outfit_hero(oid, base)
        if prop:
            hero = prop_variant(hero, prop)
        a = HeroAssets.build(hero)
        a.name = "%s, %s" % (hero["name"].split(" ")[0], base)
        cache[key] = a
    return cache[key]


def _plans(style, segs, cache):
    plans = []
    for seg in segs:
        row, dur = [], 0.0
        for oid, base in HEROES[style]:
            a = _assets(cache, oid, base, seg.prop)
            pb, d = seg.build(a)
            dur = max(dur, d)
            row.append((a, pb))
        plans.append((seg, row, dur))
    return plans


def _bounds(plans, view):
    out = {}
    for seg, row, dur in plans:
        times = gif.ticks(0.0, dur, 4)
        for i, (a, pb) in enumerate(row):
            for drawn in (True, False):
                b = a.bounds([view], [pb.pose(t) for t in times], drawn=drawn)
                old = out.get(i)
                out[i] = b if old is None else (min(old[0], b[0]), max(old[1], b[1]), min(old[2], b[2]),
                                                max(old[3], b[3]))
    return {i: (b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0) for i, b in out.items()}


def film(style, segs, cache=None, scale=SCALE, fps=FPS):
    """RGBA frames: the segments in turn, the chibi and the adult side by side, the clip and time on top and the
    events written over the frame they fire on."""
    cache = {} if cache is None else cache
    plans = _plans(style, segs, cache)
    view = raster.View(YAW, PITCH, scale)
    bounds = _bounds(plans, view)
    small, tiny = font(13), font(11)
    frames = []
    for seg, row, dur in plans:
        sims = [a.face_sim("calm", i) for i, (a, _) in enumerate(row)]
        for t in gif.ticks(0.0, dur, fps):
            cells, evs = [], []
            for i, ((a, pb), fs) in enumerate(zip(row, sims)):
                pose = pb.pose(t)
                face = fs.frames(t, pose, blink_ok=True)
                cells.append(to_image(a.render(view, pose, face=face, drawn=pb.drawn(t), bounds=bounds[i], pad=2)))
                evs.append(gif._event_lines(pb.events(t - 1.0 / fps + 1e-6, t + 1e-6)))
            gap = 18
            w = max(420, sum(c.width for c in cells) + gap * (len(cells) + 1))
            h = max(c.height for c in cells) + 62
            im = Image.new("RGBA", (w, h), BG)
            d = ImageDraw.Draw(im)
            d.text((10, 6), "%s: %s  %.2f s" % (style, seg.label, t), fill=INK, font=small)
            x = gap + (w - sum(c.width for c in cells) - gap * (len(cells) + 1)) // 2
            for (a, _), c, lines in zip(row, cells, evs):
                im.alpha_composite(c, (x, h - 20 - c.height))
                d.text((x + c.width // 2, h - 16), a.name, fill=DIM, font=tiny, anchor="ma")
                for j, line in enumerate(lines[:2]):
                    d.text((x + c.width // 2, 24 + 14 * j), line, fill=(200, 30, 30, 255), font=small, anchor="ma")
                x += c.width + gap
            frames.append(im)
    W = max(f.width for f in frames)
    H = max(f.height for f in frames)
    out = []
    for f in frames:
        if f.size != (W, H):
            g = Image.new("RGBA", (W, H), BG)
            g.alpha_composite(f, (0, H - f.height))
            f = g
        out.append(f)
    return out


def sheet_clips(style):
    """The clips a style defines (its own recipes, not the restyled reactions), in the order of the library list."""
    from anim import library as animlib
    from util import load_json
    own = list(load_json(animlib.style_recipe_path(style)).get("animations", {}))
    order = ["combat.idle", "attack.1", "attack.2", "attack.3", "attack.ranged", "draw", "sheathe", "guard",
             "guard.loop", "guard.hit", "dodge", "intro", "victory", "taunt", "combat.run", "work.chop", "work.mine"]
    return [n for n in order if n in own] + sorted(n for n in own if n not in order and not n.endswith(".hit.loop"))


def sheet(style, cache=None, per_clip=7, scale=2):
    """A contact sheet: one row per clip, `per_clip` frames over the blend and the clip, both heroes in each cell."""
    cache = {} if cache is None else cache
    view = raster.View(YAW, PITCH, scale)
    rows = []
    for name in sheet_clips(style):
        prop = WORK_PROPS.get(name)
        a0 = _assets(cache, HEROES[style][0][0], HEROES[style][0][1], prop)
        clip = a0.chain.get(name)
        if name in ("combat.idle", "combat.run"):
            seg = Segment([(name, clip.length)], base_loop=name, drawn=True)
        else:
            seg = Segment([(name, clip.length if clip.loop == "loop" else None)], drawn=name != "draw" and
                          name not in WORK_PROPS, prop=prop)
        plan = _plans(style, [seg], cache)[0]
        _, row, dur = plan
        lead = 0.0 if seg.base_loop else LEAD
        times = [lead + (dur - lead - (0.0 if seg.base_loop else TAIL)) * i / (per_clip - 1) for i in range(per_clip)]
        cells = []
        for t in times:
            pair = []
            for i, (a, pb) in enumerate(row):
                pose = pb.pose(t)
                face = a.face_sim("calm", i).frames(t, pose, blink_ok=False)
                b = a.bounds([view], [pb.pose(x) for x in times], drawn=True)
                b2 = a.bounds([view], [pb.pose(x) for x in times], drawn=False)
                b = (min(b[0], b2[0]) - 1, max(b[1], b2[1]) + 1, min(b[2], b2[2], -1.0), max(b[3], b2[3]) + 1)
                pair.append(to_image(a.render(view, pose, face=face, drawn=pb.drawn(t), bounds=b, pad=2)))
            w = sum(p.width for p in pair) + 6
            hh = max(p.height for p in pair)
            cell = Image.new("RGBA", (w, hh), BG)
            x = 0
            for p in pair:
                cell.alpha_composite(p, (x, hh - p.height))
                x += p.width + 6
            cells.append((t - lead, cell))
        rows.append((name + ("  (preview %s)" % prop if prop else ""), cells))
    f13, f11 = font(13), font(11)
    cw = max(c.width for _, cells in rows for _, c in cells)
    ch = max(c.height for _, cells in rows for _, c in cells)
    W = 16 + per_clip * (cw + 10)
    H = 40 + len(rows) * (ch + 40)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    names = " and ".join("%s (%s)" % (o, b) for o, b in HEROES[style])
    d.text((10, 8), "_style_%s: every clip the style defines, %s; times from the action's start, the 2-tick blend "
                    "first" % (style, names), fill=INK, font=f13)
    y = 36
    for label, cells in rows:
        d.text((10, y), label, fill=INK, font=f11)
        x = 10
        for t, c in cells:
            out.alpha_composite(c, (x + (cw - c.width) // 2, y + 16 + ch - c.height))
            d.text((x + cw // 2, y + 18 + ch), "%.2f s" % t, fill=DIM, font=f11, anchor="ma")
            x += cw + 10
        y += ch + 40
    return out


def write_all(out=OUT_DIR, styles=None, gifs=True):
    """Write <style>_string.gif, <style>_set.gif and <style>.png for each style. Returns {name: path}."""
    os.makedirs(out, exist_ok=True)
    paths = {}
    for style in styles or list(HEROES):
        cache = {}
        p = os.path.join(out, "%s.png" % style)
        sheet(style, cache).convert("RGB").save(p, format="PNG", optimize=True)
        paths[style + ".png"] = p
        if gifs:
            for kind, segs in (("string", string_segments(style)), ("set", set_segments(style))):
                p = os.path.join(out, "%s_%s.gif" % (style, kind))
                gif.write_gif(film(style, segs, cache), p, fps=FPS)
                paths["%s_%s.gif" % (style, kind)] = p
    if styles is None:
        with open(os.path.join(out, "README.txt"), "w", encoding="utf-8") as f:
            f.write("Style library previews (G5), written by `python3 tools/herogen/herogen.py anim-previews "
                    "--styles`.\n\n<style>_string.gif: attack.1 > attack.2 > attack.3 back to back over "
                    "combat.idle.\n<style>_set.gif: draw (from the idle), guard > .loop > guard.hit > .loop, dodge, "
                    "attack.ranged, taunt, intro, victory, sheathe, combat.run, work strokes.\n<style>.png: every clip "
                    "the style defines.\n\nHeroes (sample outfits of herosrc/style/outfits.json with their slice "
                    "weapons):\n" + "\n".join("%s: %s" % (s, ", ".join("%s on %s" % hb for hb in h))
                                              for s, h in HEROES.items()) + "\n")
        paths["README.txt"] = os.path.join(out, "README.txt")
    return paths


def check(out=OUT_DIR, styles=None, gifs=True):
    """The committed previews against a fresh render: problem lines."""
    import tempfile
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(tmp, styles, gifs)
        for name, path in sorted(fresh.items()):
            have = os.path.join(out, name)
            with open(path, "rb") as f:
                new = f.read()
            if not os.path.exists(have):
                problems.append("%s: missing" % name)
            else:
                with open(have, "rb") as f:
                    if f.read() != new:
                        problems.append("%s: differs from a fresh render" % name)
    return problems
