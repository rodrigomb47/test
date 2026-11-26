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
  string.gif        the basic attack (attack.ranged twice for a thrown or shot one), the basic string (attack.1 >
                    attack.2 > attack.3) and the kit's followup chains, each code sent as
                    the last ends
  moments.gif       intro, the phase transitions, taunt, victory and the clips that have no action code yet (last
                    stand, escape, a boss hero's rebirth), each over the base it plays on
  idles.gif         personal.1-3 over move.idle
  locomotion.gif    walk, run and the set its mobility traits use, with its own overrides (Natsuho, an acrobat: a
                    cat's twist on move.air_jump; Hinae, a runner and swimmer: the run, the swim and a plain jump;
                    the Wither, a flyer who hovers: her drift, hover and flight set; Benika, a charger and leaper:
                    the charging run and skid, the crouch jump, leap and heavy landing; Kagenori, a leaper II and
                    runner: his crane leap and the landing roll)
  sheet.png         every clip of its own file, 7 frames each from the action's start
  wings.png         (a winged hero only, B5) the wings folded as at rest and under the fold loop, spread on the
                    ground, and in flight: the glide, the top and the bottom of the beat
`herogen hero-previews <id> [--check]` writes (or checks) them. The captions fit any hero (the pronoun from its base);
the lines that name what a hero's phase looks, escape and variants show are in OWN_CAPTIONS.

A clip is drawn in the phase it belongs to: a move from the first phase of its kit, phase.N in phase N, the last stand
in the last phase, and any clip that keys a pN_ bone (Hinae's ring of talismans) from phase N, so what it moves
shows. The secondary controller's `sec.loop` (C1: Hinae's ring orbits her) plays under every film and sheet frame, as
HeroAnimator plays it whenever the chain has one. A winged model's wings controller (B5: the Ender Dragon) plays the
wing loop the clip's mode asks for (folded on the ground, beating in powered flight, spread in a glide) over that,
keying only wing_r and wing_l (HeroAnimator.wings, HeroClips.wingLoop); a model without wings is drawn as before.

`herogen hero-previews --lineup [--check]` writes lineup.png in the folder above: every hero with a source in
herosrc/heroes (the fallback aside) side by side in its own body at its own scale, the test rig among them for size.
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
    "hinae_shirasuzu": {
        "phases": "the phase looks (2: a ring of seven talismans; 3: glowing eyes, golden streamers)",
        "phase.2": "phase.2 (the ring of talismans unfolds)",
        "phase.3": "phase.3 (her eyes and the streamers light up)",
        "escape": "escape, an apologetic bow (no code yet), over the run",
    },
    "benika_akagane": {
        "phases": "the phase looks (2: the horn and the arm stripes flare ember and glow; 3: glowing eyes)",
        "phase.2": "phase.2 (the horn flares)",
        "phase.3": "phase.3 (steam from the horn, glowing eyes)",
    },
    "yugen_kagaribi": {
        "phases": "the phase looks (1: three tails; 2: six fanned wide; 3: nine, the middle three tipped with foxfire, "
                  "glowing eyes)",
        "phase.2": "phase.2 (six tails fan out, a laugh behind the sleeve)",
        "phase.3": "phase.3 (nine tails, the lanterns flare)",
        "escape": "escape, a sleeve and a smirk (no code yet), over the run",
        "sprint": "sprint (his fox lope)",
    },
    "kagenori_tsurumine": {
        "phases": "the phase looks (2: the plain helmet; 3: glowing eyes and blade edges, the short sword drawn)",
        "phase.2": "phase.2 (the helmet goes on)",
        "phase.3": "phase.3 (the short sword drawn, the eyes and edges glow)",
    },
    "ambrose_nettlewick": {
        "phases": "the phase looks (2: broom bristles at the staff's foot; 3: glowing eyes, the hat's brim lifted)",
        "phase.2": "phase.2 (the bristles sprout, a test hop on the staff)",
        "phase.3": "phase.3 (the brim flicked up, the eyes glow)",
        "escape": "escape, clutching his hat (no code yet), over the run",
    },
    "the_wither": {
        "phases": "the phase looks (2: soul-blue stays, gem and hem; 3: no familiars, burning tips, blazing eyes); "
                  "the rebirth, blue-white",
        "phase.2": "phase.2 (the wither armour flares)",
        "phase.3": "phase.3 (she tears the skulls away)",
        "rebirth": "rebirth (no code yet), blue-white from the soul sand",
    },
    "the_ender_dragon": {
        "phases": "the phase looks (2: glowing wing veins and horn tips; 3: glowing eyes and dorsal spines)",
        "phase.2": "phase.2 (wings spread, the roar)",
        "phase.3": "phase.3 (her eyes and spines ignite)",
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
         "rebirth": "rebirth (no code yet)",
         "air_jump": "air_jump", "sprint": "sprint"}
    c.update(OWN_CAPTIONS.get(a.hero.get("id"), {}))
    return c


def out_dir(hero_id):
    return os.path.join(OUT_ROOT, hero_id)


def secondary(a, pose, t):
    """The secondary controller's `sec.loop` laid over a pose at wall time t (it plays all the time, keying only its
    own bones, after the base and action controllers: HeroAnimator). The pose itself when the chain has none."""
    clip = a.chain.get("sec.loop")
    if clip is None:
        return pose
    out = {b: dict(cs) for b, cs in pose.items()}
    for b, cs in clip.sample(t % clip.length, a.form_var()).items():
        out.setdefault(b, {}).update(cs)
    return out


FLAP = ("move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover", "move.fly_land")
GLIDE = ("move.glide_start", "move.glide", "move.gust", "move.circle")


def wing_loop_of(name):
    """B5: the wing loop the wings controller plays under a clip (HeroClips.wingLoop by DATA_LOCO's mode): beating in
    powered flight with its take-off and landing, spread in a glide (and the circle, a banked glide), folded
    otherwise."""
    if name in FLAP:
        return "sec.wings_flap"
    if name in GLIDE:
        return "sec.wings_glide"
    return "sec.wings_fold"


def wings(a, pose, t, loop="sec.wings_fold"):
    """B5: a winged model's wing loop laid over a pose at wall time t (the wings controller runs after the others and
    keys only wing_r and wing_l). The pose itself for a model without wings or a chain without the loop."""
    if "wing_r" not in a.geo.bones:
        return pose
    clip = a.chain.get(loop)
    if clip is None:
        return pose
    out = {b: dict(cs) for b, cs in pose.items()}
    for b, cs in clip.sample(t % clip.length if clip.length else 0.0, a.form_var()).items():
        out.setdefault(b, {}).update(cs)
    return out


def phase_of_clip(a, name):
    """The phase a clip is drawn in: a move's first kit phase, phase.N's N, the last stand's last phase, and at least
    the phase of any pN_ bone it keys (so a ring that shows from phase 2 is seen when a clip moves it)."""
    hero = a.hero or {}
    phase = 1
    if name.startswith("move."):
        for m in hero.get("moves", []) or []:
            if "move." + m["id"] == name:
                phase = min(m.get("phases", [1]) or [1])
    elif name.startswith("phase.") and name[6:].isdigit():
        phase = int(name[6:])
    elif name == "last_stand":
        phase = max([1] + [int(p["phase"]) for p in a.visual.get("phases", [])])
    elif name == "rebirth":
        # B5: a boss hero's rebirth comes before her last phase (the Wither's at 30%, between phases 2 and 3)
        phase = max([1] + [int(p["phase"]) - 1 for p in a.visual.get("phases", [])])
    elif name.startswith("state."):
        # C2: a state a boss phase enters (Benika's Rage from phase 2) is drawn in that phase's look
        for i, p in enumerate(((hero.get("boss") or {}).get("phases") or []), start=1):
            if name[len("state."):] in (p.get("states") or []):
                phase = i
                break
    clip = a.chain.get(name)
    keyed = {b for b, _ in clip.tracks} if clip is not None else set()
    for b, n in a.visual["bones"].get("phase", {}).items():
        if b in keyed:
            phase = max(phase, int(n))
    return phase


def look_of_clip(a, name):
    """B5: "rebirth" for the rebirth clip of a hero with a rebirth texture (build.rebirth_look), else None."""
    return "rebirth" if name == "rebirth" and getattr(a, "tex_rebirth", None) is not None else None


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


def night_phases(visual):
    """The phases phases.png draws at night: the last phase, as before, and (B5 review) every earlier phase drawn from
    the phase texture (`_p2`, whose `glow: phase` regions light up only in its glow mask, as the Ender Dragon's
    phase-2 veins and horn tips do), so a look that is mostly light shows in the sheet. Heroes whose phases keep the
    base texture get the last phase only."""
    looks = sorted({1} | {p["phase"] for p in visual.get("phases", [])})
    return [ph for ph in looks[1:-1] if any(p["phase"] == ph and p.get("texture") == "p2"
                                            for p in visual.get("phases", []))] + [looks[-1]]


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
    if getattr(a, "tex_rebirth", None) is not None:
        # B5: the rebirth look (build.rebirth_look) in the phase the rebirth plays in
        ph = phase_of_clip(a, "rebirth")
        xb = a.bounds([v], (pose,), phase=ph)
        cells.append(("the rebirth (phase %d)" % ph, to_image(a.render(v, pose, face=a.mood("calm"), phase=ph,
                                                                         look="rebirth",
                                                                         bounds=(xb[0], xb[1], yb[2], yb[3])))))
    for ph in night_phases(a.visual)[:-1]:
        xb = a.bounds([v], (pose,), phase=ph)
        face = (10, 0, 0) if any(g <= ph for g in glow_phase) else a.mood("calm")
        cells.append(("phase %d at night" % ph, to_image(a.render(v, pose, face=face, phase=ph, brightness=NIGHT,
                                                                  bounds=(xb[0], xb[1], yb[2], yb[3])))))
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

    def __init__(self, label, actions, base="combat.idle", drawn=True, phase=1, at=None, hold=0.0, look=None,
                 wing_loop="sec.wings_fold"):
        self.label, self.actions, self.base, self.drawn, self.phase = label, actions, base, drawn, phase
        self.look = look                # B5: "rebirth" draws the rebirth texture
        self.wing_loop = wing_loop      # B5: the wings controller's loop (a winged model only)
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
            poses = [wings(a, secondary(a, pb.pose(t), t), t, shot.wing_loop) for t in gif.ticks(0.0, dur, 5)]
            bs.append(a.bounds([v], poses, phase=shot.phase, drawn=shot.drawn))
        b = (min(x[0] for x in bs), max(x[1] for x in bs), min(x[2] for x in bs), max(x[3] for x in bs))
        bounds.append((b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0))
    small, tiny = font(13), font(11)
    side = captions(a)["side"]
    frames = []
    for shot, pb, dur in plans:
        sim = a.face_sim("calm", 0)
        for t in gif.ticks(0.0, dur, fps):
            pose = wings(a, secondary(a, pb.pose(t), t), t, shot.wing_loop)
            face = sim.frames(t, pose, blink_ok=pb.action_phase(t) is None)
            cells = [to_image(a.render(v, pose, face=face, phase=shot.phase, drawn=shot.drawn, bounds=b, pad=2,
                                       look=shot.look))
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


def mobility_traits(a):
    mob = (a.hero or {}).get("mobility") or {}
    return [(mob.get(k) or {}).get("trait") for k in ("primary", "secondary") if mob.get(k)]


# C3: the base clips of a rider's flight (HeroMovement: the carrier shows from the take-off until the landing has played)
RIDE_CLIPS = ("move.takeoff", "move.ride", "move.fly_land")


def rides_mount(a):
    """C3: the hero's carrier rides on the rig's `mount` bone (visual.json `carrier`: a broom part or a weapon that is
    its own carrier, Ambrose's staff-broom)."""
    return ((getattr(a, "visual", None) or {}).get("carrier") or {}).get("bone") == "mount"


def mobility_tier(a, trait):
    """C3: the tier (1-3) of one of a hero's mobility traits, 0 when it lacks the trait."""
    mob = (a.hero or {}).get("mobility") or {}
    for k in ("primary", "secondary"):
        if (mob.get(k) or {}).get("trait") == trait:
            return int(mob[k].get("tier", 1))
    return 0


def loco_segments(a):
    """The locomotion a hero's mobility traits use (MOBILITY_AND_WORK 1, 4.1): an acrobat's jumps, wall kicks and
    mantle; a flyer's or hover hero's drift, hover and flight set (B5); otherwise the walk and run, a runner's own
    sprint burst when its file overrides it (C2: Yugen's fox lope), a charger's charging run and skid (C2), a leaper's
    crouch jump, leap and heavy landing (C2; the landing roll for a leaper II or III, C3) or else a plain jump, the ladder
    climb, and the swim for a swimmer."""
    from anim.loco_preview import Segment
    traits = mobility_traits(a)
    if "acrobat" in traits:
        return [Segment("walk > run", [("move.walk", 1.0), ("move.run", 1.2)]),
                Segment("crouch_jump (the tell) > leap > %s > leap > land_roll" % captions(a)["air_jump"],
                        [("move.crouch_jump", None), ("move.leap", 0.35), ("move.air_jump", None),
                         ("move.leap", 0.4), ("move.land_roll", None)]),
                Segment("wall_kick > leap > land", [("move.wall_kick", None), ("move.leap", 0.4),
                                                    ("move.land", None)]),
                Segment("climb > climb_idle (clinging) > mantle", [("move.climb", 1.0), ("move.climb_idle", 1.0),
                                                                    ("move.mantle", None)])]
    if "flyer" in traits or "hover" in traits:
        # B5: a hero who floats or flies (the Wither): her drift, the hover loops and the flight set, no jumps or
        # ladder (MOBILITY_AND_WORK 1.10, 1.11); a winged one (the Ender Dragon) with her wing loops, her circle and
        # her perch when her own file has them
        from anim.loco_preview import _wings_for
        winged = "wing_r" in a.geo.bones
        own = set(own_clips(a))
        if winged:
            segs = [Segment("walk > run", [("move.walk", 1.0), ("move.run", 1.2)], wings=_wings_for([]))]
        else:
            segs = [Segment("walk > run", [("move.walk", 1.6), ("move.run", 1.2)])]
        if "hover" in traits:
            segs.append(Segment("hover > drift (hover)", [("move.hover", 2.0), ("move.drift", 1.6)]))
        if "flyer" in traits and rides_mount(a):
            # C3: a flyer on a mount carrier (Ambrose's staff-broom) flies in the ride mode (HeroMovement: every
            # flight state of a rider shows move.ride, never move.fly*), and glides with the carrier shown
            segs.append(Segment("takeoff (the tell) > ride > ride_cast > ride > fly_land (on his carrier)",
                                [("move.takeoff", None), ("move.ride", 2.4), ("move.fly_land", None)],
                                actions=[("move.ride_cast", 1, 0.8)], riding=True))
            segs.append(Segment("glide (on his carrier, when his stamina runs out)", [("move.glide", 2.4)],
                                riding=True))
        elif "flyer" in traits:
            fly = [("move.takeoff", None), ("move.fly", 2.0), ("move.fly_fast", 1.0), ("move.fly_hover", 1.6),
                   ("move.fly_land", None)]
            segs.append(Segment("takeoff (the tell) > fly > fly_fast > fly_hover > fly_land", fly,
                                wings=_wings_for([n for n, _ in fly]) if winged else None))
            if winged and "move.circle" in own:
                glide = [("move.glide", 1.2), ("move.gust", None), ("move.glide", 0.6), ("move.circle", 2.4)]
                segs.append(Segment("glide > gust > glide > circle (banking round her target)", glide,
                                    wings={"*": "sec.wings_glide"}))
            else:
                segs.append(Segment("glide", [("move.glide", 2.4)]))
        if winged and "rest.perch" in own:
            segs.append(Segment("rest.perch (perched on a high point)", [("rest.perch", 3.0)], wings=_wings_for([])))
        return segs
    segs = [Segment("walk > run%s" % (" (a runner)" if "runner" in traits else ""),
                    [("move.walk", 1.0), ("move.run", 1.6)])]
    if "runner" in traits and (a.hero or {}).get("animations") and "move.sprint" in own_clips(a):
        # C2: a runner whose own file overrides the sprint burst (Yugen's fox lope, MOBILITY_AND_WORK 15.2) shows it
        segs.append(Segment("run > %s, the runner's sprint burst" % captions(a)["sprint"],
                            [("move.run", 0.6), ("move.sprint", 1.8)]))
    if "charger" in traits:
        # C2: a charger's traversal charge and the skid that stops it (MOBILITY_AND_WORK 1.5)
        segs.append(Segment("charge_run > skid (a charger's traversal charge)",
                            [("move.charge_run", 1.8), ("move.skid", None)]))
    if "leaper" in traits:
        # C2: a leaper's jump: the 0.3 s crouch (the tell), the leap and the heavy landing (MOBILITY_AND_WORK 1.6);
        # C3: a leaper of tier II or III lands in the landing roll instead (MOBILITY_AND_WORK 1.6, 4.1: Kagenori)
        land = "move.land_roll" if mobility_tier(a, "leaper") >= 2 else "move.land_heavy"
        segs.append(Segment("crouch_jump (the tell) > leap > %s (a leaper%s)" % (
                                land[5:], " II: the landing roll" if land == "move.land_roll" else ""),
                            [("move.crouch_jump", None), ("move.leap", 0.5), (land, None)]))
    else:
        segs.append(Segment("jump > leap > land", [("move.jump", None), ("move.leap", 0.35), ("move.land", None)]))
    if "swimmer" in traits:
        segs.append(Segment("swim (a swimmer)", [("move.swim", 2.4)]))
    segs.append(Segment("climb (a ladder)", [("move.climb", 1.0)]))
    return segs


def loco_film(a, fps=FPS, scale=SCALE):
    """The hero's locomotion through the base controller's queue (anim/loco_preview), from two views."""
    segs = loco_segments(a)
    views = [raster.View(y, p, scale) for y, p, _ in YAWS]
    plans = []
    for seg in segs:
        pb, dur = seg.build(a)
        plans.append((seg, pb, dur))
    bounds = []
    for v in views:
        bs = [a.bounds([v], [secondary(a, pb.pose(t), t) for t in gif.ticks(0.0, dur, 5)]) for _, pb, dur in plans]
        b = (min(x[0] for x in bs), max(x[1] for x in bs), min(x[2] for x in bs), max(x[3] for x in bs))
        bounds.append((b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0))
    small, tiny = font(13), font(11)
    side = captions(a)["side"]
    frames = []
    for seg, pb, dur in plans:
        sim = a.face_sim("calm", 0)
        for t in gif.ticks(0.0, dur, fps):
            pose = secondary(a, pb.pose(t), t)
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
    return [c.name for c in own_built(a).clips]


def own_built(a):
    """The hero's own animation file as the generator built it (its clips keep their meta: kind, the base an action
    plays over; the clips read back from the written file do not)."""
    from anim import library as animlib
    if getattr(a, "_own_built", None) is None:
        a._own_built = animlib.hero(a.hero)
    return a._own_built


def sheet(a, path, per_clip=7, scale=2):
    """Every clip of the hero's own file: a row each, `per_clip` frames over the blend and the clip, 3/4 front view."""
    v = raster.View(-35.0, 8.0, scale)
    rows = []
    for name in own_clips(a):
        clip = a.chain.get(name)
        meta = own_built(a).by_name[name].meta
        kind = meta.get("kind", "base")
        phase = phase_of_clip(a, name)
        # C3: a rider's broom clips (and an action over the ride) show the carrier on the mount bone, as in game
        riding = rides_mount(a) and (name in RIDE_CLIPS or meta.get("over") == "move.ride")
        if name.startswith("sec."):
            # a secondary loop plays over whatever the base shows: here the combat stance
            pb = SequencePlayback(a.chain, "combat.idle", [], a.form_var())
            times = [clip.length * i / per_clip for i in range(per_clip)]
            drawn = True
        elif kind == "base":
            pb = SequencePlayback(a.chain, name, [], a.form_var())
            times = [clip.length * i / per_clip for i in range(per_clip)]
            drawn = False
        else:
            base = meta.get("over") or "combat.idle"
            pb = SequencePlayback(a.chain, base, [(name, 0.0)], a.form_var())
            span = ACTION_BLEND + clip.length
            times = [span * i / (per_clip - 1) for i in range(per_clip)]
            drawn = base == "combat.idle"
        poses = [wings(a, secondary(a, pb.pose(t), t), t, wing_loop_of(name)) for t in times]
        b = a.bounds([v], poses, drawn=drawn, phase=phase, riding=riding)
        b = (b[0] - 1, b[1] + 1, min(b[2], -1.0), b[3] + 1)
        sim = a.face_sim("calm", 0)
        look = look_of_clip(a, name)
        cells = [(t, to_image(a.render(v, p, face=sim.frames(t, p, blink_ok=False), drawn=drawn, bounds=b, pad=2,
                                       phase=phase, look=look, riding=riding)))
                 for t, p in zip(times, poses)]
        label = name if phase == 1 else "%s (phase %d)" % (name, phase)
        rows.append(("%s, the rebirth look" % label if look else label, cells))
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


def wings_sheet(a, path, scale=5):
    """B5: a winged hero's wings: folded as at rest and under the fold loop (as the game shows her on the ground),
    spread on the ground (the part bones of its glide's first frame on the standing figure, under the fold loop: her
    intro and phase 2), and in flight (the glide under the glide loop; the top and the bottom of the beat under the
    flap loop)."""
    stance = a.stance()

    def with_parts(pose, clip_name, t):
        clip = a.chain.get(clip_name)
        out = {b: dict(cs) for b, cs in pose.items()}
        if clip is None:
            return out
        for b, cs in clip.sample(t, a.form_var()).items():
            if b.startswith("wing_") and b not in ("wing_r", "wing_l"):
                out.setdefault(b, {}).update(cs)
        return out

    def clip_pose(name, t):
        clip = a.chain.get(name)
        return clip.sample(t, a.form_var()) if clip is not None else stance

    rows = [
        ("folded, at rest (the bind pose)", stance),
        ("folded, under the fold loop (on the ground in game)", wings(a, stance, 0.0, "sec.wings_fold")),
        ("spread on the ground (under the fold loop)", wings(a, with_parts(stance, "move.glide", 0.0), 0.0,
                                                             "sec.wings_fold")),
        ("the glide (move.glide, the glide loop)", wings(a, clip_pose("move.glide", 0.0), 0.0, "sec.wings_glide")),
        ("the beat's top (move.fly, the flap loop at 0 s)", wings(a, clip_pose("move.fly", 0.0), 0.0,
                                                                  "sec.wings_flap")),
        ("the beat's bottom (the flap loop at 0.2 s)", wings(a, clip_pose("move.fly", 0.2), 0.2, "sec.wings_flap")),
    ]
    views = [("front", raster.View(0.0, 8.0, scale)), ("3/4 front", raster.View(-35.0, 10.0, scale)),
             ("side", raster.View(90.0, 6.0, scale)), ("back", raster.View(180.0, 10.0, scale)),
             ("from above", raster.View(180.0, 60.0, scale))]
    cells = []
    for label, pose in rows:
        b = a.bounds([v for _, v in views], (pose,))
        cells.append((label, [(vl, to_image(a.render(v, pose, face=a.mood("calm"), bounds=(b[0] - 1, b[1] + 1,
                                                                                          b[2] - 1, b[3] + 1))))
                              for vl, v in views]))
    cw = max(im.width for _, row in cells for _, im in row)
    W = 20 + len(views) * (cw + 12)
    H = 46 + sum(max(im.height for _, im in row) + 44 for _, row in cells)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((16, 12), "%s: %s wings folded and open (the wings controller's loops on wing_r and wing_l)"
           % (a.name, captions(a)["pos"]), fill=INK, font=font(18))
    y = 46
    for label, row in cells:
        h = max(im.height for _, im in row)
        d.text((12, y), label, fill=INK, font=font(13))
        x = 12
        for vl, im in row:
            out.alpha_composite(im, (x + (cw - im.width) // 2, y + 18 + h - im.height))
            d.text((x + cw // 2, y + 22 + h), vl, fill=DIM, font=font(11), anchor="ma")
            x += cw + 12
        y += h + 44
    save(out, path)


# ------------------------------------------------------------------ the set

def phase_states(hero):
    """C2: the states a boss phase enters (`boss.phases[].states`)."""
    return {st for p in ((hero.get("boss") or {}).get("phases") or []) for st in (p.get("states") or [])}


def followup_only(a, followups):
    """C2: the ids of the followups that never play alone (their move's weight is 0) and have a clip."""
    weights = {m["id"]: m.get("weight", 1) for m in (a.hero or {}).get("moves", []) or []}
    return [f["move"] for f in followups or [] if weights.get(f.get("move")) == 0
            and a.chain.get("move." + f["move"]) is not None]


def active_end(a, name):
    """C2: the wall time (the action starting at LEAD) its active part ends: one tick after its last hit or cast
    (file times count from the action's start), when the move runner starts a followup."""
    hits = a.chain.get(name).hits()
    return LEAD + (max(hits) if hits else a.chain.get(name).length) + TICK


def gif_shots(a):
    """{file name: [Shot]} of the films, from the hero's kit (its source's moves, ultimate, states and followups)."""
    hero = a.hero
    moves = [m["id"] for m in hero.get("moves", []) or []]
    phase_of = {}
    for m in hero.get("moves", []) or []:
        phase_of[m["id"]] = min(m.get("phases", [1]) or [1])
    out = {}
    parents = {}
    for src, fols in [("ultimate", (hero.get("ultimate") or {}).get("followups"))] + \
            [("move." + m["id"], m.get("followups")) for m in hero.get("moves", []) or []]:
        for f in followup_only(a, fols):
            parents.setdefault(f, src)
    for mid in moves:
        name = "move." + mid
        if a.chain.get(name) is None:
            continue
        if mid in parents and a.chain.get(parents[mid]) is not None:
            # C2: a move that only ever follows another (weight 0) is shown after it, from the end of its active
            # part (it starts on that move's last pose: Crash Waves on the crater of Iron Mountain Crash)
            src = parents[mid]
            out["move_%s.gif" % mid] = [Shot("%s > move.%s (move slot %d, its followup)" % (src, mid, moves.index(mid)),
                                             [src, name], at={name: active_end(a, src)},
                                             phase=max(phase_of_clip(a, src), phase_of_clip(a, name)))]
            continue
        out["move_%s.gif" % mid] = [Shot("move.%s (move slot %d)" % (mid, moves.index(mid)), [name],
                                         phase=phase_of_clip(a, name))]
    if a.chain.get("ultimate") is not None:
        ult = a.chain.get("ultimate")
        acts, at = ["ultimate"], {}
        # C2: a state a boss phase enters (Benika's Rage) is not the ultimate's
        states = [s.get("name") for s in hero.get("states", []) or [] if s.get("name") not in phase_states(hero)]
        for st in states:
            if a.chain.get("state." + st) is not None:
                hits = ult.hits()
                if hits:
                    acts.append("state." + st)
                    at["state." + st] = LEAD + ACTION_BLEND + hits[0]
                break
        ph = phase_of_clip(a, "ultimate")
        label = "ultimate > %s at its hit" % " > ".join(acts[1:]) if len(acts) > 1 else "ultimate"
        for f in followup_only(a, (hero.get("ultimate") or {}).get("followups")):
            # C2: a followup that never plays alone (weight 0: Benika's Crash Waves) follows from the end of the
            # ultimate's active part, as the move runner starts it
            acts.append("move." + f)
            at["move." + f] = active_end(a, "ultimate")
            label += " > move.%s (its followup, from the end of the active part)" % f
        out["ultimate.gif"] = [Shot(label if ph == 1 else "%s (phase %d)" % (label, ph), acts, at=at, phase=ph)]
    chain = ["attack.1", "attack.2", "attack.3"]
    shots = []
    basic = (hero.get("basic_attack") or {}).get("style", "melee")
    if basic != "melee" and a.chain.get("attack.ranged") is not None:
        # a thrown or shot basic attack plays attack.ranged (code 4); the string is the style's melee
        shots.append(Shot("the basic attack (%s): attack.ranged > attack.ranged" % basic,
                          ["attack.ranged", "attack.ranged"]))
    shots.append(Shot("the basic string: attack.1 > attack.2 > attack.3", chain))
    for m in hero.get("moves", []) or []:
        for f in m.get("followups", []) or []:
            if f.get("move") != m["id"] and a.chain.get("move." + f["move"]) is not None:
                shots.append(Shot("move.%s > move.%s (its followup, %d%%)" % (m["id"], f["move"], f.get("chance", 100)),
                                  ["move." + m["id"], "move." + f["move"]],
                                  phase=max(phase_of_clip(a, "move." + m["id"]),
                                            phase_of_clip(a, "move." + f["move"]))))
    out["string.gif"] = shots
    moments = []
    cap = captions(a)
    for name, label, base, drawn, phase in (
            ("intro", "intro (code 16)", "combat.idle", True, 1),
            ("phase.2", cap["phase.2"], "combat.idle", True, 2),
            ("rebirth", cap["rebirth"], "combat.idle", True, phase_of_clip(a, "rebirth")),
            ("phase.3", cap["phase.3"], "combat.idle", True, 3),
            ("taunt", "taunt (code 14)", "combat.idle", True, 1),
            ("victory", "victory (code 20)", "combat.idle", True, 1),
            ("last_stand", "last_stand (no code yet)", "combat.idle", True, 3),
            ("escape", cap["escape"], "move.run", False, 3)):
        if a.chain.get(name) is not None and name in own_clips(a):
            acts, at = [name], {}
            if name.startswith("phase."):
                # C2: the state the boss phase enters plays as its transition ends (Benika's Rage after phase.2)
                bp = ((hero.get("boss") or {}).get("phases") or [])
                n = int(name[len("phase."):])
                if n <= len(bp):
                    for st in bp[n - 1].get("states") or []:
                        if a.chain.get("state." + st) is not None:
                            secs = ((bp[n - 1].get("transition") or {}).get("seconds")) or a.chain.get(name).length
                            acts.append("state." + st)
                            at["state." + st] = LEAD + float(secs)
                            label += " > state.%s (as the transition ends)" % st
                            break
            moments.append(Shot(label, acts, base=base, drawn=drawn, phase=phase, look=look_of_clip(a, name),
                                at=at))
    out["moments.gif"] = moments
    out["idles.gif"] = [Shot("%s (a rare idle)" % n, [n], base="move.idle", drawn=False)
                        for n in ("personal.1", "personal.2", "personal.3") if n in own_clips(a)]
    return out


# ------------------------------------------------------------------ the lineup of the heroes with a source

LINEUP_SCALE = 8
FIXTURES_LAST = ("test_rig",)


def lineup_ids():
    """Every hero with a source in herosrc/heroes but the fallback: the roster heroes by name, then the test rig (the
    pipeline fixture, there for size)."""
    import build
    ids = [h for h in build.hero_ids() if not h.startswith("_")]
    return sorted(h for h in ids if h not in FIXTURES_LAST) + [h for h in FIXTURES_LAST if h in ids]


def lineup(path, ids=None, scale=LINEUP_SCALE):
    """The heroes side by side on grass, each in its own body at its own scale (art.scale: one model pixel is
    scale x art.scale screen pixels), feet on one line: the idle stance from the 3/4 front, and below it the combat
    stance with the weapon drawn from the front, in the first phase and the last."""
    from render.sheet import backdrop, ground_col, ground_row, height_blocks, shadow
    rows = [("idle stance, 3/4 front", -24.0, 10.0, False, False), ("weapon drawn, front", 0.0, 6.0, True, False),
            ("weapon drawn, last phase", 0.0, 6.0, True, True)]
    heroes = []
    for hid in ids or lineup_ids():
        a = HeroAssets.build(hid)
        heroes.append(a)
    cells = []          # per row: [(image, feet row, feet column)]
    for label, yaw, pitch, drawn, last in rows:
        out = []
        for a in heroes:
            k = scale * float(a.visual.get("scale", 1.0))
            v = raster.View(yaw, pitch, k)
            pose = a.stance(drawn)
            phase = max([1] + [int(p["phase"]) for p in a.visual.get("phases", [])]) if last else 1
            pose = wings(a, secondary(a, pose, 0.0), 0.0)
            b = a.bounds([v], (pose,), phase=phase, drawn=drawn)
            b = (b[0] - 1, b[1] + 1, min(b[2], -1.0), b[3] + 1)
            face = (10, 0, 0) if last and any(p.get("eyes_glow") for p in a.visual.get("phases", [])) else \
                a.mood("calm")
            im = to_image(a.render(v, pose, face=face, phase=phase, drawn=drawn, bounds=b, pad=2))
            out.append((im, ground_row(v, b, 2), ground_col(v, b, 2)))
        cells.append(out)
    f16, f13 = font(16), font(13)
    measure = ImageDraw.Draw(Image.new("RGBA", (1, 1)))

    def label_of(a):
        v = a.visual
        name = (a.hero or {}).get("name", a.id)
        return name, "%s %s, %d%%: %.2f blocks" % (v["base"], v["build"], round(v.get("scale", 1.0) * 100),
                                                 height_blocks(a))
    gap = 30
    slots = []
    for i, a in enumerate(heroes):
        n, d = label_of(a)
        slots.append(max(max(cells[r][i][0].width for r in range(len(rows))),
                         int(measure.textlength(n, font=f16)) + 12, int(measure.textlength(d, font=f13)) + 12))
    title = "The heroes with a source, each in its own body at its own scale (the one-body rule, HEROES_V2 3.6), " \
        "beside the test rig"
    W = max(sum(slots) + gap * (len(heroes) + 1) + 150, int(measure.textlength(title, font=font(18))) + 40)
    heights = [max(im.height for im, _, _ in row) for row in cells]
    top = 46
    H = top + sum(h + 60 for h in heights) + 10
    sheet = backdrop(W, H, 30)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), title, fill=(250, 250, 250, 255), font=font(18), stroke_width=2, stroke_fill=(40, 60, 90, 255))
    y = top
    for (label, _, _, _, _), row, h in zip(rows, cells, heights):
        d.text((12, y + h // 2), label, fill=(250, 250, 250, 255), font=f13, stroke_width=2,
               stroke_fill=(30, 50, 26, 255))
        feet_y = y + max(fr for _, fr, _ in row)
        x = gap + 140
        for (im, fr, fc), slot, a in zip(row, slots, heroes):
            ix = x + (slot - im.width) // 2
            sw = int(7.0 * scale * 2.2)
            sh = max(10, sw // 3)
            sheet.alpha_composite(shadow((sw, sh)), (ix + fc - sw // 2 - 8, feet_y - sh // 2 - 8))
            sheet.alpha_composite(im, (ix, feet_y - fr))
            if row is cells[0]:
                n, dsc = label_of(a)
                d.text((x + slot // 2, feet_y + 12), n, fill=(250, 250, 250, 255), font=f16, anchor="ma",
                       stroke_width=2, stroke_fill=(30, 50, 26, 255))
                d.text((x + slot // 2, feet_y + 32), dsc, fill=(250, 250, 250, 255), font=f13, anchor="ma",
                       stroke_width=2, stroke_fill=(30, 50, 26, 255))
            x += slot + gap
        y += h + 60
    save(sheet, path)
    return path


def lineup_path():
    return os.path.join(OUT_ROOT, "lineup.png")


def check_lineup(path=None):
    import tempfile
    path = path or lineup_path()
    with tempfile.TemporaryDirectory() as tmp:
        fresh = lineup(os.path.join(tmp, "lineup.png"))
        with open(fresh, "rb") as f:
            new = f.read()
    if not os.path.exists(path):
        return ["lineup.png: missing"]
    with open(path, "rb") as f:
        return [] if f.read() == new else ["lineup.png: differs from a fresh render"]


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
    if "wing_r" in a.geo.bones:
        p = os.path.join(out, "wings.png")
        wings_sheet(a, p)
        paths["wings.png"] = p
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
