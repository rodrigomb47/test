"""Previews of the core animation library (task G4): docs/heroes-v2/previews/anim/core/.

Per group (movement, work, emotes, combat reactions) a GIF of every clip played in turn and a contact sheet, on two
contrasting bases: Tansy (chibi_f: cat ears and tail, twin tails, bell sleeves, a skirt) and Garrick (adult_m: a
ponytail, bell sleeves, a long hakama). Both play the written animation files through the controller emulation of
render/gif.py with HeroFace's frames (render/face_sim.py): the base loop runs, an action starts with the action
controller's 2-tick blend from the pose it interrupts (so a chain such as stun -> stun.loop, or work.fish_cast ->
work.fish_wait -> work.fish_wait.loop -> work.fish_reel, plays as the game plays it), and the face follows the clips'
face_ctrl keys, the mood and the blinks.

Work tools are preview props: an inline part on `hand_r` at item_r's grip, the handle along the hand's -z and the
head at the far end with its striking side toward the hand's -y (pick points, axe edge, hoe blade), the convention
the work animations are authored for. In game, A2's item layer draws the vanilla tool at item_r; it must hold it the
same way (HEROES_V2 4.7). Combat reactions are shown over move.idle (the core copies) and, for the katana style's
restyled copies, a second GIF over combat.idle with a sword.
"""
import copy
import os

from PIL import Image, ImageDraw

from render import gif, raster
from render.animfile import Playback
from render.scene import HeroAssets, to_image
from render.sheet import BG, DIM, INK, font
from util import ROOT

OUT_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "anim", "core")
HEROES = ("style_tansy", "style_garrick")          # chibi_f and adult_m: the two most different bodies
YAW, PITCH, SCALE, FPS = -40.0, 10.0, 3, 20
LEAD, TAIL = 0.25, 0.25

# (clip, seconds to show for a base loop, prop)
MOVEMENT = [("move.crouch", 2.4, None), ("move.swim", 2.4, None), ("move.fall", 1.6, None), ("move.climb", 2.0, None),
            ("rest.sit", 4.0, None), ("rest.sleep", 4.0, None), ("carry.loop", 2.0, None)]
WORK_TOOLS = [("work.mine", "pickaxe"), ("work.chop", "axe"), ("work.till", "hoe"), ("work.plant", None),
              ("work.harvest", None), ("work.build", "block"), ("work.smith", "hammer"), ("work.sweep", "broom"),
              ("work.tend", None)]
WORK_HOME = [("work.cook", "ladle"), ("work.read", "book"), ("work.pray", None), ("work.eat", "food")]
FISHING = ["work.fish_cast", "work.fish_wait", ("work.fish_wait.loop", 2.0), "work.fish_reel"]
EMOTES = ["emote.greet", "emote.bow", "emote.wave", "emote.laugh", "emote.angry", "emote.sad", "emote.think",
          "emote.cheer", "emote.talk", "emote.shrug", "personal.1", "personal.2", "personal.3", "founding"]
COMBAT = ["hurt.heavy", ["stun", ("stun.loop", 2.0)], "interrupt", "dodge", "stagger", "enrage", "intro", "victory",
          ["defeated", ("defeated.loop", 2.0), "spared"], ("death", 1.6)]

# ------------------------------------------------------------------ preview props (grip space: the handle along -z)
WOOD = {"slot": "leather", "material": "wood"}
METAL = {"slot": "metal", "material": "metal"}
PROPS = {
    "pickaxe": [((-0.5, -0.5, -11.0), (1, 1, 12.5), WOOD), ((-0.6, -4.5, -12.4), (1.2, 9, 1.6), METAL)],
    "axe": [((-0.5, -0.5, -10.0), (1, 1, 11.5), WOOD), ((-0.4, -4.2, -11.0), (0.8, 4.2, 3.2), METAL)],
    "hoe": [((-0.5, -0.5, -12.0), (1, 1, 13.5), WOOD), ((-0.5, -3.6, -12.6), (1, 3.6, 1.4), METAL)],
    "hammer": [((-0.5, -0.5, -8.0), (1, 1, 9.5), WOOD), ((-1.25, -2.6, -9.6), (2.5, 5.2, 2.6), METAL)],
    "ladle": [((-0.35, -0.35, -7.0), (0.7, 0.7, 8.5), WOOD), ((-1.0, -1.8, -8.6), (2, 1.8, 2), METAL)],
    "broom": [((-0.45, -0.45, -15.0), (0.9, 0.9, 19), WOOD),
              ((-1.8, -1.2, -20.5), (3.6, 2.4, 5.5), {"slot": "trim", "material": "cloth"})],
    "book": [((-2.5, -0.6, -4.5), (5, 1.2, 6), {"slot": "accent", "material": "leather"})],
    "block": [((-2.5, -2.5, -5.0), (5, 5, 5), WOOD)],
    "food": [((-1.2, -1.2, -2.6), (2.4, 2.4, 2.4), {"slot": "trim", "material": "cloth"})],
    "rod": [((-0.35, -0.35, -22.0), (0.7, 0.7, 23.5), WOOD), ((-0.8, -1.3, -3.2), (1.6, 1.0, 1.6), METAL)],
}
CLIP_PROPS = dict([(c, p) for c, p in WORK_TOOLS + WORK_HOME] + [(c, "rod") for c in
                                                                  ("work.fish_cast", "work.fish_wait",
                                                                   "work.fish_wait.loop", "work.fish_reel")])


def prop_hero(hero_id, prop):
    """A preview variant of a hero holding a prop in the right hand (built in memory; nothing is written)."""
    import build
    hero = copy.deepcopy(build.load_hero(hero_id))
    if not prop:
        return hero
    hero["id"] = "%s_%s" % (hero_id, prop)
    cubes = []
    for i, (o, s, mat) in enumerate(PROPS[prop]):
        cubes.append(dict(mat, name="prop%d" % i, bone="hand_r", size=list(s),
                          origin=["-shoulder_x + %g" % o[0], "hand_y + %g" % o[1], o[2]]))
    hero["art"].setdefault("parts", []).append({"part": {"id": "inline/preview_%s" % prop, "category": "props",
                                                         "cubes": cubes}})
    return hero


class SequencePlayback(Playback):
    """A base loop and a chain of actions (name, start): each action blends for the action controller's transition
    from the pose shown at its start (GeckoLib blends from the current pose), then plays; the next one cuts it."""

    def __init__(self, chain, base, actions, d=0.0):
        super().__init__(chain, base, None, 0.0, d)
        self.seq = []
        for name, at in actions:
            clip = chain.get(name)
            if clip is None:
                raise KeyError(name)
            self.seq.append((clip, float(at)))

    def _active(self, t):
        cur = None
        for i, (clip, at) in enumerate(self.seq):
            if t + 1e-9 >= at:
                cur = i
        return cur

    def pose(self, t):
        i = self._active(t)
        if i is None:
            return self.base_pose(t)
        clip, at = self.seq[i]
        dt = t - at
        if clip.loop == "once" and dt > self.blend + clip.length + 1e-9:
            return self.base_pose(t)          # finished: the action controller stops and the base shows
        if dt < self.blend - 1e-9:
            start = self.pose(at - 1e-6) if at > 0 else self.base_pose(at)
            first = clip.sample(0.0, self.d)
            f = dt / self.blend
            pose = self.base_pose(t)
            for bone, chans in first.items():
                for ch, v in chans.items():
                    rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
                    a = start.get(bone, {}).get(ch, rest)
                    pose.setdefault(bone, {})[ch] = tuple(x + (y - x) * f for x, y in zip(a, v))
            return pose
        pose = self.base_pose(t)
        local = dt - self.blend
        if clip.loop == "loop" and clip.length > 0:
            local = local % clip.length
        for bone, chans in clip.sample(local, self.d).items():
            pose.setdefault(bone, {}).update(chans)
        return pose

    def events(self, t0, t1):
        out = []
        for clip, at in self.seq:
            for e in clip.events:
                if t0 <= at + e.t < t1:
                    out.append((at + e.t, e.kind, e.value))
        return sorted(out)

    def windup_until(self):
        return None

    def action_phase(self, t):
        return "play" if self._active(t) is not None else None


def _segment_items(entry):
    """[(clip name, seconds to show or None)] of a group entry: a name, (name, seconds) or a chain list."""
    if isinstance(entry, list):
        return [e if isinstance(e, tuple) else (e, None) for e in entry]
    if isinstance(entry, tuple):
        return [entry]
    return [(entry, None)]


class Segment:
    """One labelled stretch of a film: a base loop shown for a while, or an action (or a chain) over a base."""

    def __init__(self, items, base="move.idle", prop=None, label=None):
        self.items, self.base, self.prop = items, base, prop
        self.label = label or " > ".join(n for n, _ in items)

    def build(self, assets):
        """(playback, duration) on one hero."""
        chain = assets.chain
        first = chain.get(self.items[0][0])
        d = assets.form_var()
        if len(self.items) == 1 and first.meta.get("kind", "base") == "base" and first.loop == "loop" and \
                self.items[0][0].split(".")[0] in ("move", "rest", "carry"):
            secs = self.items[0][1] or first.length
            return Playback(chain, self.items[0][0], None, 0.0, d), secs
        at, actions = LEAD, []
        blend = Playback(chain, self.base).blend
        for name, secs in self.items:
            clip = chain.get(name)
            actions.append((name, at))
            at += blend + (secs if secs is not None else clip.length)
        return SequencePlayback(chain, self.base, actions, d), at + TAIL


def segments(group):
    out = []
    if group == "movement":
        for name, secs, prop in MOVEMENT:
            out.append(Segment([(name, secs)], prop=prop))
    elif group == "work":
        for name, prop in WORK_TOOLS:
            out.append(Segment([(name, None)], prop=prop))
    elif group == "work_home":
        out.append(Segment(_segment_items(FISHING), prop="rod", label="work.fish_cast > fish_wait > .loop > fish_reel"))
        for name, prop in WORK_HOME:
            out.append(Segment([(name, None)], prop=prop))
    elif group == "emotes":
        for name in EMOTES:
            out.append(Segment([(name, None)]))
    elif group in ("combat", "combat_katana"):
        base = "combat.idle" if group == "combat_katana" else "move.idle"
        for entry in COMBAT:
            out.append(Segment(_segment_items(entry), base=base))
    else:
        raise ValueError(group)
    return out


GROUPS = {
    "movement": "Movement and rest (base loops): crouch, swim, fall, climb, sit, sleep, carry",
    "work": "Work with tools (actions over move.idle): mine, chop, till, plant, harvest, build, smith, sweep, tend",
    "work_home": "Work at home: fishing (cast, wait, the wait loop, reel), cook, read, pray, eat",
    "emotes": "Emotes, the personal idle generics and founding",
    "combat": "Combat reactions over move.idle: hurt, stun (+ loop), interrupt, dodge, stagger, enrage, intro, "
              "victory, defeated (+ loop) and spared, death (held)",
    "combat_katana": "The same reactions restyled for the katana style (over its combat.idle, sword drawn)",
}


def _assets(cache, hero_id, prop, katana=False):
    key = (hero_id, prop, katana)
    if key not in cache:
        if katana:
            from anim.preview import sword_variant
            h = sword_variant(hero_id) if hero_id == "style_tansy" else hero_id
            cache[key] = HeroAssets.build(h)
        else:
            cache[key] = HeroAssets.build(prop_hero(hero_id, prop) if prop else hero_id)
        if not hasattr(cache[key], "name") or not cache[key].name:
            cache[key].name = hero_id
    return cache[key]


def film(group, cache=None, scale=SCALE, fps=FPS):
    """RGBA frames of a group: the segments in turn, the two heroes side by side, the clip's name and time on top."""
    cache = {} if cache is None else cache
    katana = group == "combat_katana"
    frames = []
    small, tiny = font(13), font(11)
    segs = segments(group)
    # one framing for the whole film, so every clip stands on the same ground line
    plans = []
    for seg in segs:
        row = []
        dur = 0.0
        for hid in HEROES:
            a = _assets(cache, hid, seg.prop, katana)
            pb, d = seg.build(a)
            dur = max(dur, d)
            row.append((a, pb))
        plans.append((seg, row, dur))
    view = raster.View(YAW, PITCH, scale)
    bounds = {}
    for seg, row, dur in plans:
        times = gif.ticks(0.0, dur, 4)
        for a, pb in row:
            b = a.bounds([view], [pb.pose(t) for t in times], drawn=katana)
            key = a.visual["base"]
            old = bounds.get(key)
            bounds[key] = b if old is None else (min(old[0], b[0]), max(old[1], b[1]), min(old[2], b[2]),
                                                 max(old[3], b[3]))
    for key, b in bounds.items():
        bounds[key] = (b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0)
    for seg, row, dur in plans:
        sims = [a.face_sim("calm", i) for i, (a, _) in enumerate(row)]
        for t in gif.ticks(0.0, dur, fps):
            cells, evs = [], []
            for (a, pb), fs in zip(row, sims):
                pose = pb.pose(t)
                face = fs.frames(t, pose, blink_ok=True)
                cells.append(to_image(a.render(view, pose, face=face, drawn=katana, bounds=bounds[a.visual["base"]],
                                               pad=2)))
                evs.append(gif._event_lines(pb.events(t - 1.0 / fps + 1e-6, t + 1e-6)))
            gap = 18
            w = max(420, sum(c.width for c in cells) + gap * (len(cells) + 1))
            h = max(c.height for c in cells) + 62
            im = Image.new("RGBA", (w, h), BG)
            d = ImageDraw.Draw(im)
            d.text((10, 6), "%s  %.2f s" % (seg.label, t), fill=INK, font=small)
            x = gap + (w - sum(c.width for c in cells) - gap * (len(cells) + 1)) // 2
            for (a, _), c, lines in zip(row, cells, evs):
                im.alpha_composite(c, (x, h - 20 - c.height))
                d.text((x + c.width // 2, h - 16), "%s (%s)" % (a.name, a.visual["base"]), fill=DIM, font=tiny,
                       anchor="ma")
                for j, line in enumerate(lines[:2]):
                    d.text((x + c.width // 2, 24 + 14 * j), line, fill=(200, 30, 30, 255), font=small, anchor="ma")
                x += c.width + gap
            frames.append(im)
    # pad every frame to the largest size (segments may differ by a few pixels)
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


def sheet(group, cache=None, per_clip=7, scale=2):
    """A contact sheet of a group: one row per segment, `per_clip` evenly spaced frames, both heroes in each cell."""
    cache = {} if cache is None else cache
    katana = group == "combat_katana"
    segs = segments(group)
    view = raster.View(YAW, PITCH, scale)
    rows = []
    for seg in segs:
        row = []
        dur = 0.0
        for hid in HEROES:
            a = _assets(cache, hid, seg.prop, katana)
            pb, dd = seg.build(a)
            dur = max(dur, dd)
            row.append((a, pb))
        times = [LEAD + (dur - LEAD - TAIL) * i / (per_clip - 1) for i in range(per_clip)]
        cells = []
        for t in times:
            pair = []
            for i, (a, pb) in enumerate(row):
                pose = pb.pose(t)
                face = a.face_sim("calm", i).frames(t, pose, blink_ok=False)
                b = a.bounds([view], [pb.pose(x) for x in times], drawn=katana)
                pair.append(to_image(a.render(view, pose, face=face, drawn=katana,
                                              bounds=(b[0] - 1, b[1] + 1, min(b[2], -1.0), b[3] + 1), pad=2)))
            w = sum(p.width for p in pair) + 6
            hh = max(p.height for p in pair)
            cell = Image.new("RGBA", (w, hh), BG)
            x = 0
            for p in pair:
                cell.alpha_composite(p, (x, hh - p.height))
                x += p.width + 6
            cells.append((t, cell))
        rows.append((seg.label, cells))
    f13, f11 = font(13), font(11)
    cw = max(c.width for _, cells in rows for _, c in cells)
    ch = max(c.height for _, cells in rows for _, c in cells)
    W = 16 + per_clip * (cw + 10)
    H = 40 + len(rows) * (ch + 40)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((10, 8), GROUPS[group] + "  (%s and %s)" % HEROES, fill=INK, font=f13)
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


def write_all(out=OUT_DIR, groups=None, gifs=True):
    """Write the core previews: <group>.gif and <group>.png per group. Returns {name: path}."""
    os.makedirs(out, exist_ok=True)
    cache = {}
    paths = {}
    for group in groups or list(GROUPS):
        p = os.path.join(out, "%s.png" % group)
        img = sheet(group, cache)
        img.convert("RGB").save(p, format="PNG", optimize=True)
        paths[group + ".png"] = p
        if gifs:
            p = os.path.join(out, "%s.gif" % group)
            gif.write_gif(film(group, cache), p, fps=FPS)
            paths[group + ".gif"] = p
    with open(os.path.join(out, "README.txt"), "w", encoding="utf-8") as f:
        f.write("Core animation library previews (G4), written by `python3 tools/herogen/herogen.py anim-previews "
                "--core`.\n\n" + "\n".join("%s.gif / %s.png: %s" % (g, g, GROUPS[g]) for g in groups or GROUPS)
                + "\n\nHeroes: %s (chibi_f) and %s (adult_m). Tools are preview props held at item_r.\n" % HEROES)
    paths["README.txt"] = os.path.join(out, "README.txt")
    return paths


def check(out=OUT_DIR, groups=None, gifs=True):
    """The committed previews against a fresh render (a stale preview is a wrong preview): problem lines."""
    import tempfile
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(tmp, groups, gifs)
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
