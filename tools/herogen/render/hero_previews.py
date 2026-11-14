"""A hero's own preview set (C1-C4): docs/heroes-v2/previews/heroes/<id>/.

Every image shows the hero only in its own body form (the owner's one-body rule, HEROES_V2 3.6), built from its
source in herosrc/heroes and playing its written animation chain (its own file, its style library, the core) through
the controller emulation of the G2-G10 previews (an action blends for 2 ticks from the pose it cuts; the base
controller's queue blends for 4), with HeroFace's frames, face_ctrl keys and blinks:

  turnaround.png    eight views round the hero and one from above, the weapon drawn, the far level of detail and the
                    last phase at night (render/sheet.turnaround)
  faces.png         the head from the front in each of the 12 moods (its visual.json mood table, overrides applied),
                    a blink midway and closed, both talk frames, and a 3/4 view
  phases.png        each phase look from the front and the back (3/4), and the last one at night (the glow layer)
  move_<id>.gif     one per move of its kit, played over combat.idle as the server sends it; `ultimate.gif` plays the
                    ultimate and, at its hit, the state it enters (the state's own clip, as HeroStates starts it)
  string.gif        the basic string (attack.1 > attack.2 > attack.3) and the kit's followup chains, each code sent as
                    the last ends
  moments.gif       intro, the phase transitions, taunt, victory and the clips that have no action code yet (last
                    stand, escape), each over the base it plays on
  idles.gif         personal.1-3 over move.idle
  locomotion.gif    walk, run and the acrobat's set with its own overrides (Natsuho: a cat's twist on move.air_jump)
  sheet.png         every clip of its own file, 7 frames each from the action's start
`herogen hero-previews <id> [--check]` writes (or checks) them. The captions fit any hero (the pronoun from its base);
the lines that name what a hero's phase looks, escape and variants show are in OWN_CAPTIONS.
"""
import os

from PIL import Image, ImageDraw

from anim.clip import TICK
from anim.core_preview import SequencePlayback
from anim.writer import TRANSITION_TICKS
from render import gif, raster
from render.scene import HeroAssets, to_image
from render.sheet import BG, DIM, INK, NIGHT, font, head_bounds, head_image, save, turnaround
from util import ROOT

OUT_ROOT = os.path.join(ROOT, "docs", "heroes-v2", "previews", "heroes")
YAWS = ((-35.0, 8.0, "3/4 front"), (70.0, 6.0, None))           # None: the hero's right side (captions)
SCALE, FPS = 4, 20
LEAD, TAIL = 0.3, 0.35
ACTION_BLEND = TRANSITION_TICKS["action"] * TICK
MOODS = ("calm", "happy", "angry", "focused", "hurt", "tired", "sad", "smug", "surprised", "asleep", "flustered",
         "determined")


# A hero's own captions, over the defaults of captions(): what its phase looks, escape and variants show.
OWN_CAPTIONS = {
    "natsuho_komezaki": {
        "phases": "the phase looks (her phase 2 puffs the tail's fur, phase 3 lights the eyes)",
        "phase.2": "phase.2 (her tail puffs up)",
        "phase.3": "phase.3 (her eyes glow)",
        "escape": "escape, the cat bolts (no code yet), over the run",
        "air_jump": "air_jump (her cat twist)",
    },
}


def captions(a):
    """The captions drawn into a hero's images: generic ones (the pronoun from its base), then its OWN_CAPTIONS."""
    pos, subj = ("her", "she") if a.visual.get("base", "").endswith("_f") else ("his", "he")
    c = {"pos": pos, "side": "%s right side" % pos,
         "turnaround": "turnaround (%s own body, the only one %s has)" % (pos, subj),
         "phases": "the phase looks",
         "phase.2": "phase.2", "phase.3": "phase.3",
         "escape": "escape (no code yet), over the run",
         "air_jump": "air_jump"}
    c.update(OWN_CAPTIONS.get(a.hero.get("id"), {}))
    return c


def out_dir(hero_id):
    return os.path.join(OUT_ROOT, hero_id)


def assets(hero_id):
    import build
    hero = build.load_hero(hero_id)
    a = HeroAssets.build(hero)
    a.name = "%s (%s, %s)" % (hero.get("name", hero_id), a.visual["base"], a.visual["build"])
    return a


# ------------------------------------------------------------------ stills

def faces(a, path, scale=13):
    """The head from the front in the 12 moods, a blink (half, closed), both talk frames, and a 3/4 view."""
    pose = a.stance()
    front = raster.View(0.0, 4.0, scale)
    hb = head_bounds(a, front, pose)
    cells = []
    for m in MOODS:
        cells.append((m, a.mood(m)))
    e, mo, c = a.mood("calm")
    cells += [("blink (half)", (1, mo, c)), ("blink (closed)", (2, mo, c)), ("talk A", (e, 2, c)),
              ("talk B", (e, 3, c))]
    imgs = [(label, head_image(a, front, pose, fr, bounds=hb)) for label, fr in cells]
    imgs.append(("3/4", head_image(a, raster.View(-32.0, 8.0, scale), pose, a.mood("calm"))))
    cw = max(im.width for _, im in imgs)
    ch = max(im.height for _, im in imgs)
    per_row = 9
    rows = [imgs[i:i + per_row] for i in range(0, len(imgs), per_row)]
    sheet = Image.new("RGBA", (per_row * (cw + 10) + 30, len(rows) * (ch + 44) + 60), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "%s: the 12 moods (visual.json's table, %s overrides applied), a blink, both talk frames"
           % (a.name, captions(a)["pos"]), fill=INK, font=font(18))
    y = 46
    for row in rows:
        x = 16
        for label, im in row:
            sheet.alpha_composite(im, (x + (cw - im.width) // 2, y + (ch - im.height)))
            d.text((x + cw // 2, y + ch + 4), label, fill=DIM, font=font(13), anchor="ma")
            x += cw + 10
        y += ch + 44
    save(sheet, path)


def phases(a, path, scale=6):
    """Each phase look from the 3/4 front and the 3/4 back, and the last phase at night with its glow."""
    pose = a.stance()
    looks = sorted({1} | {p["phase"] for p in a.visual.get("phases", [])})
    glow_phase = [p["phase"] for p in a.visual.get("phases", []) if p.get("eyes_glow")]
    cells = []
    views = [raster.View(-35.0, 10.0, scale), raster.View(145.0, 10.0, scale)]
    yb = a.bounds(views, (pose,), phase=max(looks))
    for ph in looks:
        face = (10, 0, 0) if ph in glow_phase or any(g <= ph for g in glow_phase) else a.mood("calm")
        for v, side in zip(views, ("front", "back")):
            xb = a.bounds([v], (pose,), phase=ph)
            cells.append(("phase %d, %s" % (ph, side), to_image(a.render(v, pose, face=face, phase=ph,
                                                                         bounds=(xb[0], xb[1], yb[2], yb[3])))))
    v = views[0]
    xb = a.bounds([v], (pose,), phase=max(looks))
    face = (10, 0, 0) if glow_phase else a.mood("calm")
    cells.append(("phase %d at night" % max(looks), to_image(a.render(v, pose, face=face, phase=max(looks),
                                                                      brightness=NIGHT,
                                                                      bounds=(xb[0], xb[1], yb[2], yb[3])))))
    W = sum(im.width + 14 for _, im in cells) + 30
    H = max(im.height for _, im in cells) + 90
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "%s: %s" % (a.name, captions(a)["phases"]), fill=INK, font=font(18))
    x = 16
    for label, im in cells:
        d.text((x + im.width // 2, 40), label, fill=DIM, font=font(13), anchor="ma")
        sheet.alpha_composite(im, (x, H - 16 - im.height))
        x += im.width + 14
    save(sheet, path)


# ------------------------------------------------------------------ films

class Shot:
    """A labelled stretch of a film: actions back to back over a base loop, each started when the one before ends
    (or at an explicit offset), and the phase look and weapon flag to draw it with."""

    def __init__(self, label, actions, base="combat.idle", drawn=True, phase=1, at=None, hold=0.0):
        self.label, self.actions, self.base, self.drawn, self.phase = label, actions, base, drawn, phase
        self.at = at or {}
        self.hold = hold

    def playback(self, a):
        t, seq = LEAD, []
        for name in self.actions:
            clip = a.chain.get(name)
            if clip is None:
                raise KeyError("%s has no %s" % (a.id, name))
            start = self.at.get(name, t)
            seq.append((name, start))
            t = start + ACTION_BLEND + clip.length
        return SequencePlayback(a.chain, self.base, seq, a.form_var()), t + TAIL + self.hold


def film(a, shots, fps=FPS, scale=SCALE):
    """RGBA frames: the shots in turn, the hero from two views side by side, the label and time on top and the
    events written over the frame they fire on."""
    plans = []
    for shot in shots:
        pb, dur = shot.playback(a)
        plans.append((shot, pb, dur))
    views = [raster.View(y, p, scale) for y, p, _ in YAWS]
    bounds = []
    for v in views:
        bs = []
        for shot, pb, dur in plans:
            poses = [pb.pose(t) for t in gif.ticks(0.0, dur, 5)]
            bs.append(a.bounds([v], poses, phase=shot.phase, drawn=shot.drawn))
        b = (min(x[0] for x in bs), max(x[1] for x in bs), min(x[2] for x in bs), max(x[3] for x in bs))
        bounds.append((b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0))
    small, tiny = font(13), font(11)
    side = captions(a)["side"]
    frames = []
    for shot, pb, dur in plans:
        sim = a.face_sim("calm", 0)
        for t in gif.ticks(0.0, dur, fps):
            pose = pb.pose(t)
            face = sim.frames(t, pose, blink_ok=pb.action_phase(t) is None)
            cells = [to_image(a.render(v, pose, face=face, phase=shot.phase, drawn=shot.drawn, bounds=b, pad=2))
                     for v, b in zip(views, bounds)]
            evs = gif._event_lines(pb.events(t - 1.0 / fps + 1e-6, t + 1e-6))
            gap = 18
            w = max(460, sum(c.width for c in cells) + gap * (len(cells) + 1))
            h = max(c.height for c in cells) + 66
            im = Image.new("RGBA", (w, h), BG)
            d = ImageDraw.Draw(im)
            d.text((10, 6), "%s  %.2f s" % (shot.label, t), fill=INK, font=small)
            for k, line in enumerate(evs[:2]):
                d.text((w // 2, 24 + 14 * k), line, fill=(200, 30, 30, 255), font=small, anchor="ma")
            x = gap + (w - sum(c.width for c in cells) - gap * (len(cells) + 1)) // 2
            for c, (_, _, label) in zip(cells, YAWS):
                im.alpha_composite(c, (x, h - 20 - c.height))
                d.text((x + c.width // 2, h - 16), label or side, fill=DIM, font=tiny, anchor="ma")
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


def loco_film(a, fps=FPS, scale=SCALE):
    """The hero's locomotion through the base controller's queue (anim/loco_preview), from two views."""
    from anim.loco_preview import Segment
    segs = [Segment("walk > run", [("move.walk", 1.0), ("move.run", 1.2)]),
            Segment("crouch_jump (the tell) > leap > %s > leap > land_roll" % captions(a)["air_jump"],
                    [("move.crouch_jump", None), ("move.leap", 0.35), ("move.air_jump", None), ("move.leap", 0.4),
                     ("move.land_roll", None)]),
            Segment("wall_kick > leap > land", [("move.wall_kick", None), ("move.leap", 0.4), ("move.land", None)]),
            Segment("climb > climb_idle (clinging) > mantle", [("move.climb", 1.0), ("move.climb_idle", 1.0),
                                                                ("move.mantle", None)])]
    views = [raster.View(y, p, scale) for y, p, _ in YAWS]
    plans = []
    for seg in segs:
        pb, dur = seg.build(a)
        plans.append((seg, pb, dur))
    bounds = []
    for v in views:
        bs = [a.bounds([v], [pb.pose(t) for t in gif.ticks(0.0, dur, 5)]) for _, pb, dur in plans]
        b = (min(x[0] for x in bs), max(x[1] for x in bs), min(x[2] for x in bs), max(x[3] for x in bs))
        bounds.append((b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0))
    small, tiny = font(13), font(11)
    side = captions(a)["side"]
    frames = []
    for seg, pb, dur in plans:
        sim = a.face_sim("calm", 0)
        for t in gif.ticks(0.0, dur, fps):
            pose = pb.pose(t)
            face = sim.frames(t, pose, blink_ok=True)
            cells = [to_image(a.render(v, pose, face=face, bounds=b, pad=2)) for v, b in zip(views, bounds)]
            evs = gif._event_lines(pb.events(t - 1.0 / fps + 1e-6, t + 1e-6))
            gap = 18
            w = max(460, sum(c.width for c in cells) + gap * (len(cells) + 1))
            h = max(c.height for c in cells) + 66
            im = Image.new("RGBA", (w, h), BG)
            d = ImageDraw.Draw(im)
            d.text((10, 6), "%s  %.2f s" % (seg.label, t), fill=INK, font=small)
            for k, line in enumerate(evs[:2]):
                d.text((w // 2, 24 + 14 * k), line, fill=(200, 30, 30, 255), font=small, anchor="ma")
            x = gap + (w - sum(c.width for c in cells) - gap * (len(cells) + 1)) // 2
            for c, (_, _, label) in zip(cells, YAWS):
                im.alpha_composite(c, (x, h - 20 - c.height))
                d.text((x + c.width // 2, h - 16), label or side, fill=DIM, font=tiny, anchor="ma")
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


def own_clips(a):
    """The clips of the hero's own file (variants included), in its order."""
    from anim import library as animlib
    return [c.name for c in animlib.hero(a.hero).clips]


def sheet(a, path, per_clip=7, scale=2):
    """Every clip of the hero's own file: a row each, `per_clip` frames over the blend and the clip, 3/4 front view."""
    v = raster.View(-35.0, 8.0, scale)
    rows = []
    for name in own_clips(a):
        clip = a.chain.get(name)
        kind = clip.meta.get("kind", "base")
        if kind == "base":
            pb = SequencePlayback(a.chain, name, [], a.form_var())
            times = [clip.length * i / per_clip for i in range(per_clip)]
            drawn = False
        else:
            base = clip.meta.get("over") or "combat.idle"
            pb = SequencePlayback(a.chain, base, [(name, 0.0)], a.form_var())
            span = ACTION_BLEND + clip.length
            times = [span * i / (per_clip - 1) for i in range(per_clip)]
            drawn = base == "combat.idle"
        poses = [pb.pose(t) for t in times]
        b = a.bounds([v], poses, drawn=drawn)
        b = (b[0] - 1, b[1] + 1, min(b[2], -1.0), b[3] + 1)
        sim = a.face_sim("calm", 0)
        cells = [(t, to_image(a.render(v, p, face=sim.frames(t, p, blink_ok=False), drawn=drawn, bounds=b, pad=2)))
                 for t, p in zip(times, poses)]
        rows.append((name, cells))
    cw = max(c.width for _, cells in rows for _, c in cells)
    ch = max(c.height for _, cells in rows for _, c in cells)
    W = 16 + per_clip * (cw + 10)
    H = 40 + len(rows) * (ch + 40)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((10, 8), "%s: every clip of %s own file; times from the action's start, the 2-tick blend first"
           % (a.name, captions(a)["pos"]), fill=INK, font=font(13))
    y = 36
    for name, cells in rows:
        d.text((10, y), name, fill=INK, font=font(11))
        x = 10
        for t, c in cells:
            out.alpha_composite(c, (x + (cw - c.width) // 2, y + 16 + ch - c.height))
            d.text((x + cw // 2, y + 18 + ch), "%.2f s" % t, fill=DIM, font=font(11), anchor="ma")
            x += cw + 10
        y += ch + 40
    out.convert("RGB").save(path, format="PNG", optimize=True)


# ------------------------------------------------------------------ the set

def gif_shots(a):
    """{file name: [Shot]} of the films, from the hero's kit (its source's moves, ultimate, states and followups)."""
    hero = a.hero
    moves = [m["id"] for m in hero.get("moves", []) or []]
    phase_of = {}
    for m in hero.get("moves", []) or []:
        phase_of[m["id"]] = min(m.get("phases", [1]) or [1])
    out = {}
    for mid in moves:
        name = "move." + mid
        if a.chain.get(name) is not None:
            out["move_%s.gif" % mid] = [Shot("move.%s (move slot %d)" % (mid, moves.index(mid)), [name],
                                             phase=phase_of[mid])]
    if a.chain.get("ultimate") is not None:
        ult = a.chain.get("ultimate")
        acts, at = ["ultimate"], {}
        states = [s.get("name") for s in hero.get("states", []) or []]
        for st in states:
            if a.chain.get("state." + st) is not None:
                hits = ult.hits()
                if hits:
                    acts.append("state." + st)
                    at["state." + st] = LEAD + ACTION_BLEND + hits[0]
                break
        out["ultimate.gif"] = [Shot("ultimate > %s at its hit" % " > ".join(acts[1:]) if len(acts) > 1 else
                                    "ultimate", acts, at=at)]
    chain = ["attack.1", "attack.2", "attack.3"]
    shots = [Shot("the basic string: attack.1 > attack.2 > attack.3", chain)]
    for m in hero.get("moves", []) or []:
        for f in m.get("followups", []) or []:
            if f.get("move") != m["id"] and a.chain.get("move." + f["move"]) is not None:
                shots.append(Shot("move.%s > move.%s (its followup, %d%%)" % (m["id"], f["move"], f.get("chance", 100)),
                                  ["move." + m["id"], "move." + f["move"]], phase=phase_of.get(m["id"], 1)))
    out["string.gif"] = shots
    moments = []
    cap = captions(a)
    for name, label, base, drawn, phase in (
            ("intro", "intro (code 16)", "combat.idle", True, 1),
            ("phase.2", cap["phase.2"], "combat.idle", True, 2),
            ("phase.3", cap["phase.3"], "combat.idle", True, 3),
            ("taunt", "taunt (code 14)", "combat.idle", True, 1),
            ("victory", "victory (code 20)", "combat.idle", True, 1),
            ("last_stand", "last_stand (no code yet)", "combat.idle", True, 3),
            ("escape", cap["escape"], "move.run", False, 3)):
        if a.chain.get(name) is not None and name in own_clips(a):
            moments.append(Shot(label, [name], base=base, drawn=drawn, phase=phase))
    out["moments.gif"] = moments
    out["idles.gif"] = [Shot("%s (a rare idle)" % n, [n], base="move.idle", drawn=False)
                        for n in ("personal.1", "personal.2", "personal.3") if n in own_clips(a)]
    return out


def write_all(hero_id, out=None, gifs=True):
    out = out or out_dir(hero_id)
    os.makedirs(out, exist_ok=True)
    a = assets(hero_id)
    paths = {}
    p = os.path.join(out, "turnaround.png")
    turnaround(a, p, scale=6, title="%s: %s" % (a.name, captions(a)["turnaround"]))
    paths["turnaround.png"] = p
    for name, fn in (("faces.png", faces), ("phases.png", phases)):
        p = os.path.join(out, name)
        fn(a, p)
        paths[name] = p
    p = os.path.join(out, "sheet.png")
    sheet(a, p)
    paths["sheet.png"] = p
    if gifs:
        for name, shots in sorted(gif_shots(a).items()):
            if not shots:
                continue
            p = os.path.join(out, name)
            gif.write_gif(film(a, shots), p, fps=FPS)
            paths[name] = p
        p = os.path.join(out, "locomotion.gif")
        gif.write_gif(loco_film(a), p, fps=FPS)
        paths["locomotion.gif"] = p
    return paths


def check(hero_id, out=None, gifs=True):
    """The committed previews against a fresh render: problem lines."""
    import tempfile
    out = out or out_dir(hero_id)
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(hero_id, tmp, gifs)
        for name, path in sorted(fresh.items()):
            have = os.path.join(out, name)
            with open(path, "rb") as f:
                new = f.read()
            if not os.path.exists(have):
                problems.append("%s: missing" % name)
                continue
            with open(have, "rb") as f:
                if f.read() != new:
                    problems.append("%s: differs from a fresh render" % name)
    return problems
