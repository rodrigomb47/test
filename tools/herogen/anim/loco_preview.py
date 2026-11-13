"""Previews of the mobility set (task G10): docs/heroes-v2/previews/anim/locomotion/.

The locomotion clips play on the base controller as a queue (MOBILITY_AND_WORK 4.2: a one-shot, then the loop the
mode asks for): each clip blends in over the base controller's 4 ticks from the pose shown when it starts, exactly as
GeckoLib starts a queued animation, so a one-shot that ends on its loop's first frame hands over invisibly. An action
(move.ride_cast, move.air_dive) plays over it on the action controller (2-tick blend), and a winged hero's wings play
the secondary controller's loop (sec.wings_fold, sec.wings_flap, sec.wings_glide; 6-tick blend) on top. The carrier on
the mount bone shows while the hero takes off, rides and lands (A10 shows it for the fly, glide and ride modes);
with Ambrose's staff-broom his hand and back copies hide meanwhile (render/scene.py).

Per group a GIF at 20 fps (one frame a tick) and a contact sheet:
  jumps        crouch_jump > leap > land, jump > leap > air_jump > leap > land_roll, jump > leap > land_heavy,
               wall_kick > leap > land, mantle
  climb_glide  climb_idle, climb, climb_down, glide_start > glide > gust > glide > land
  flight       take-off > fly > fly_fast > fly_hover > fly_land, winded, downed (with feathered wings and their loops)
  ground       sprint > skid, charge_run > skid, the generic dive (move.air_dive over move.leap)
  ride         take-off > ride > ride_cast > ride > fly_land on a mounts/broom carrier
on a chibi and an adult (Tansy, chibi_f, and Maren, adult_f: a tail, twin tails, sleeves and a skirt; a ponytail, a
cape, armour and greaves), and one GIF per slice hero's variant (MOBILITY_AND_WORK 15) on its own base and on a chibi:
hero_natsuho (the cat twist), hero_yugen (the fox lope), hero_kagenori (the crane leap), hero_ambrose (the broom,
side-saddle, on his staff). `herogen anim-previews --locomotion [--groups ...] [--check]` writes or checks them.
"""
import copy
import os

from PIL import Image, ImageDraw

from anim.clip import TICK
from anim.writer import TRANSITION_TICKS
from render import gif, raster
from render.scene import HeroAssets, to_image
from render.sheet import BG, DIM, INK, font
from util import ROOT

OUT_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "anim", "locomotion")
HEROES = ("style_tansy", "style_maren")
YAW, PITCH, SCALE, FPS = -55.0, 10.0, 3, 20
LEAD, TAIL = 0.3, 0.35
BASE_BLEND = TRANSITION_TICKS["base"] * TICK
ACTION_BLEND = TRANSITION_TICKS["action"] * TICK
SEC_BLEND = 6 * TICK                       # the secondary controller's transition (HEROES_V2 4.4)
WINGS = {"part": "wings/feathered", "color": "secondary", "alt": "secondary", "trim": "trim"}
BROOM = {"part": "mounts/broom"}


def _blend(a, b, f):
    out = {bone: dict(chans) for bone, chans in b.items()}
    for bone, chans in a.items():
        for ch, v in chans.items():
            rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
            to = b.get(bone, {}).get(ch, rest)
            out.setdefault(bone, {})[ch] = tuple(x + (y - x) * f for x, y in zip(v, to))
    return out


class LocoPlayback:
    """The base controller's queue (steps: [(clip name, start)]: each blends from the pose shown at its start), an
    action over it (actions: [(name, start)]), and a wing loop on the secondary controller (wings: [(name, start)])."""

    def __init__(self, chain, steps, d=0.0, actions=(), wings=(), riding=None):
        self.chain, self.d = chain, float(d)
        self.ride_window = riding               # (from, to): the carrier on the mount bone shows

        def clips(items):
            out = []
            for name, at in items:
                clip = chain.get(name)
                if clip is None:
                    raise KeyError(name)
                out.append((clip, float(at)))
            return out
        self.steps, self.actions, self.wings = clips(steps), clips(actions), clips(wings)
        self._cache = {}

    @staticmethod
    def _local(clip, dt, blend):
        local = dt - blend
        if clip.loop == "loop" and clip.length > 0:
            return local % clip.length
        return min(max(local, 0.0), clip.length)

    def _layer(self, items, t, blend, under):
        """The pose of a controller's queue at t (None before its first item)."""
        cur = None
        for i, (clip, at) in enumerate(items):
            if t + 1e-9 >= at:
                cur = i
        if cur is None:
            return None
        clip, at = items[cur]
        dt = t - at
        if dt < blend - 1e-9:
            start = self._layer(items, at - 1e-6, blend, under) if cur > 0 else under(at)
            return _blend(start or {}, clip.sample(0.0, self.d), dt / blend)
        return clip.sample(self._local(clip, dt, blend), self.d)

    def base_pose(self, t):
        key = round(t, 6)
        if key not in self._cache:
            self._cache[key] = self._layer(self.steps, t, BASE_BLEND, lambda x: {}) or {}
        return self._cache[key]

    def pose(self, t):
        pose = {b: dict(c) for b, c in self.base_pose(t).items()}
        for items, blend in ((self.actions, ACTION_BLEND), (self.wings, SEC_BLEND)):
            cur = [(c, at) for c, at in items if t + 1e-9 >= at]
            if not cur:
                continue
            clip, at = cur[-1]
            if clip.loop == "once" and t - at > blend + clip.length + 1e-9:
                continue                     # an action that has finished: the base shows
            over = self._layer(items, t, blend, lambda x: self.base_pose(x))
            for bone, chans in (over or {}).items():
                pose.setdefault(bone, {}).update(chans)
        return pose

    def events(self, t0, t1):
        out = []
        for clip, at in self.steps + self.actions:
            for e in clip.events:
                if t0 <= at + e.t < t1:
                    out.append((at + e.t, e.kind, e.value))
        return sorted(out)

    def windup_until(self):
        return None

    def riding(self, t):
        return self.ride_window is not None and self.ride_window[0] - 1e-9 <= t <= self.ride_window[1] + 1e-9


class Segment:
    """A labelled stretch of a film: base clips in a queue [(name, seconds a loop shows, or None)], actions over it at
    [(name, after which step index)], the wing loop to play with each step, and whether the carrier shows."""

    def __init__(self, label, items, actions=(), wings=None, riding=False):
        self.label, self.items, self.actions, self.wings, self.riding = label, items, list(actions), wings, riding

    def build(self, assets):
        chain = assets.chain
        at, steps, starts = LEAD, [], []
        for name, secs in self.items:
            clip = chain.get(name)
            if clip is None:
                raise KeyError("%s has no %s" % (assets.id, name))
            steps.append((name, at))
            starts.append(at)
            at += BASE_BLEND + (secs if secs is not None else clip.length)
        actions = [(name, starts[i] + BASE_BLEND + float(delay)) for name, i, delay in self.actions]
        wings = []
        if self.wings and "wing_r" in assets.geo.bones:
            for (name, _), start in zip(self.items, starts):
                w = self.wings.get(name, self.wings.get("*"))
                if w and (not wings or wings[-1][0] != w):
                    wings.append((w, start))
        window = None
        if self.riding:
            # A10 shows the carrier from the take-off's start until the landing has played
            window = (starts[0], at)
        return LocoPlayback(chain, [("move.idle", 0.0)] + steps, assets.form_var(), actions, wings, window), at + TAIL


def _wings_for(names):
    out = {"*": "sec.wings_fold"}
    for n in names:
        if n in ("move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover"):
            out[n] = "sec.wings_flap"
        elif n in ("move.glide_start", "move.glide", "move.gust"):
            out[n] = "sec.wings_glide"
    return out


def segments(group):
    if group == "jumps":
        return [Segment("crouch_jump > leap > land", [("move.crouch_jump", None), ("move.leap", 0.6),
                                                       ("move.land", None)]),
                Segment("jump > leap > air_jump > leap > land_roll",
                        [("move.jump", None), ("move.leap", 0.3), ("move.air_jump", None), ("move.leap", 0.3),
                         ("move.land_roll", None)]),
                Segment("jump > leap > land_heavy", [("move.jump", None), ("move.leap", 0.5),
                                                     ("move.land_heavy", None)]),
                Segment("wall_kick > leap > land", [("move.wall_kick", None), ("move.leap", 0.4),
                                                    ("move.land", None)]),
                Segment("mantle", [("move.mantle", None)])]
    if group == "climb_glide":
        return [Segment("climb_idle (clinging)", [("move.climb_idle", 2.4)]),
                Segment("climb (G4) > climb_down", [("move.climb", 1.0), ("move.climb_down", 2.0)]),
                Segment("fall > glide_start > glide > gust > glide > land",
                        [("move.fall", 0.4), ("move.glide_start", None), ("move.glide", 1.2), ("move.gust", None),
                         ("move.glide", 0.6), ("move.land", None)])]
    if group == "flight":
        fly = ["move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover", "move.fly_land"]
        return [Segment("takeoff > fly > fly_fast > fly_hover > fly_land",
                        [("move.takeoff", None), ("move.fly", 1.6), ("move.fly_fast", 1.6), ("move.fly_hover", 1.6),
                         ("move.fly_land", None)], wings=_wings_for(fly)),
                Segment("winded (wings drooped: sec.wings_fold)", [("move.winded", 2.4)], wings=_wings_for([])),
                Segment("downed (shot down)", [("move.downed", None)], wings=_wings_for([]))]
    if group == "ground":
        return [Segment("sprint > skid", [("move.sprint", 1.5), ("move.skid", None)]),
                Segment("charge_run > skid", [("move.charge_run", 1.8), ("move.skid", None)]),
                Segment("leap > air_dive (an action: wind-up, plunge, hit, recovery)",
                        [("move.leap", 2.4)], actions=[("move.air_dive", 0, 0.1)])]
    if group == "ride":
        return [Segment("takeoff > ride > ride_cast > ride > fly_land (a mounts/broom carrier)",
                        [("move.takeoff", None), ("move.ride", 2.8), ("move.fly_land", None)],
                        actions=[("move.ride_cast", 1, 0.8)], riding=True)]
    raise ValueError(group)


GROUPS = {
    "jumps": "Jumps and landings: crouch_jump (the tell) > leap > land; jump > air_jump (the flip) > land_roll; "
             "jump > land_heavy; wall_kick; mantle",
    "climb_glide": "Climbing and gliding: climb_idle, climb_down, glide_start > glide > gust > land",
    "flight": "Flight with worn wings (feathered; their loops on the secondary controller): takeoff > fly > fly_fast "
              "> fly_hover > fly_land; winded; downed",
    "ground": "Runner and charger: sprint > skid, charge_run > skid; the generic dive (move.air_dive)",
    "ride": "Riding a carrier (a mounts/broom on the mount bone): takeoff > ride > ride_cast > fly_land",
}
HERO_GIFS = {
    "hero_natsuho": ("natsuho", ["teen_f", "chibi_f"], "Natsuho's cat twist (move.air_jump, variant cat_twist)",
                     [Segment("jump > leap > air_jump (cat twist) > leap > land_roll",
                              [("move.jump", None), ("move.leap", 0.3), ("move.air_jump", None), ("move.leap", 0.4),
                               ("move.land_roll", None)])]),
    "hero_yugen": ("yugen", ["adult_m", "chibi_m"], "Yugen's fox lope (move.sprint, variant fox_lope)",
                   [Segment("run (core) > sprint (the fox lope) > skid", [("move.run", 1.2), ("move.sprint", 2.4),
                                                                          ("move.skid", None)])]),
    "hero_kagenori": ("kagenori", ["adult_m", "chibi_m"], "Kagenori's crane leap (move.leap, variant crane_leap)",
                      [Segment("crouch_jump > leap (the crane) > land_heavy",
                               [("move.crouch_jump", None), ("move.leap", 2.4), ("move.land_heavy", None)])]),
    "hero_ambrose": ("ambrose", ["adult_m", "chibi_m"], "Ambrose on his staff-broom (variant broom: side-saddle)",
                     [Segment("takeoff > ride > ride_cast (a vial) > ride > fly_land",
                              [("move.takeoff", None), ("move.ride", 2.8), ("move.fly_land", None)],
                              actions=[("move.ride_cast", 1, 0.8)], riding=True)]),
}


def _hero(hero_id, parts):
    import build
    hero = copy.deepcopy(build.load_hero(hero_id))
    if parts:
        hero["id"] = hero_id + "_" + "_".join(p["part"].split("/")[1] for p in parts)
        hero["art"].setdefault("parts", []).extend(copy.deepcopy(parts))
    return hero


def _outfit(oid, base):
    import lint
    outfit = [o for o in lint.load_outfits() if o["id"] == oid][0]
    return lint.outfit_hero(outfit, base)


def _assets(cache, hero):
    key = hero["id"]
    if key not in cache:
        a = HeroAssets.build(hero)
        a.name = hero.get("name", key)
        cache[key] = a
    return cache[key]


def group_heroes(group, cache):
    parts = {"flight": [WINGS], "ride": [BROOM]}.get(group, [])
    return [_assets(cache, _hero(h, parts)) for h in HEROES]


def _film(segs, heroes, scale=SCALE, fps=FPS):
    """RGBA frames: the segments in turn, the heroes side by side, the label and time on top, events above heroes."""
    frames = []
    small, tiny = font(13), font(11)
    view = raster.View(YAW, PITCH, scale)
    plans = []
    for seg in segs:
        row, dur = [], 0.0
        for a in heroes:
            pb, d = seg.build(a)
            dur = max(dur, d)
            row.append((a, pb))
        plans.append((seg, row, dur))
    bounds = {}
    for seg, row, dur in plans:
        times = gif.ticks(0.0, dur, 4)
        for a, pb in row:
            b = a.bounds([view], [pb.pose(t) for t in times], riding=seg.riding)
            old = bounds.get(a.id)
            bounds[a.id] = b if old is None else (min(old[0], b[0]), max(old[1], b[1]), min(old[2], b[2]),
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
                cells.append(to_image(a.render(view, pose, face=face, bounds=bounds[a.id], pad=2,
                                               riding=pb.riding(t))))
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
                d.text((x + c.width // 2, h - 16), "%s (%s)" % (a.name.split(" ")[0], a.visual["base"]), fill=DIM,
                       font=tiny, anchor="ma")
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


def _sheet(title, segs, heroes, per_clip=7, scale=2):
    view = raster.View(YAW, PITCH, scale)
    rows = []
    for seg in segs:
        row, dur = [], 0.0
        for a in heroes:
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
                b = a.bounds([view], [pb.pose(x) for x in times], riding=seg.riding)
                pair.append(to_image(a.render(view, pose, face=face, riding=pb.riding(t),
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
    d.text((10, 8), title + "  (%s)" % ", ".join("%s %s" % (a.name, a.visual["base"]) for a in heroes), fill=INK,
           font=f13)
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
    """Write the locomotion previews: <group>.gif and <group>.png per group, hero_<name>.gif and .png per slice
    variant. Returns {name: path}."""
    os.makedirs(out, exist_ok=True)
    cache, paths = {}, {}
    wanted = groups or list(GROUPS) + list(HERO_GIFS)
    for group in wanted:
        if group in GROUPS:
            heroes, segs, title = group_heroes(group, cache), segments(group), GROUPS[group]
        else:
            oid, bases, title, segs = HERO_GIFS[group]
            heroes = [_assets(cache, _outfit(oid, b)) for b in bases]
        p = os.path.join(out, "%s.png" % group)
        _sheet(title, segs, heroes).convert("RGB").save(p, format="PNG", optimize=True)
        paths[group + ".png"] = p
        if gifs:
            p = os.path.join(out, "%s.gif" % group)
            gif.write_gif(_film(segs, heroes), p, fps=FPS)
            paths[group + ".gif"] = p
    with open(os.path.join(out, "README.txt"), "w", encoding="utf-8") as f:
        f.write("Mobility set previews (G10), written by `python3 tools/herogen/herogen.py anim-previews "
                "--locomotion`.\n\n"
                + "\n".join("%s.gif / %s.png: %s" % (g, g, GROUPS[g]) for g in GROUPS) + "\n"
                + "\n".join("%s.gif / %s.png: %s (%s)" % (g, g, v[2], ", ".join(v[1])) for g, v in HERO_GIFS.items())
                + "\n\nCore groups on %s (chibi_f) and %s (adult_f); flight adds feathered wings, ride a mounts/broom "
                  "(preview variants built in memory). One-shots and loops play as the base controller's queue "
                  "(4-tick blends), actions over it (2 ticks), wing loops on the secondary controller (6 ticks).\n"
                % HEROES)
    paths["README.txt"] = os.path.join(out, "README.txt")
    return paths


def check(out=OUT_DIR, groups=None, gifs=True):
    """The committed previews against a fresh render: problem lines."""
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
