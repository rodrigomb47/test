"""The tail rule (owner, 2026-11-19): a tail never hangs between the legs.

The owner, looking at the Ender Dragon's wings sheet: "her tail downwards while she is flying, and it kinda looks like a
male genitalia like this, can you make it so her tail will not go downwards when she flies". A tail that hangs below
the hips between or in front of the legs reads as something obscene from the front and from 3/4, so `herogen check`
refuses it for every hero whose model has a tail (cubes on the rig's `tail_1` or any bone below it: a cat's, a
fox's, the dragon's scaled tail, any later tail part; C2: and on every other tail chain beside it, a fox's copies and
their level-of-detail copies, `tail_bones`):

- **The zone**, per frame in world (file) space: below the crotch (the lowest point of the base body's hips, the
  `body` bone's base cube), between the outer edges of the two legs (the base legs' cubes, measured along the hips'
  own left-right axis) and from the body's back plane forward (the hips cube's back face, along the hips' own
  front-back axis), starting `BACK_TOL` px in front of that face (a tail that runs down the seat is behind her). No
  point of a tail cube's centre line (the spine of each segment, sampled at five points along its long side) may lie
  in it; brushing it with a corner of a thick segment is not a tail between the legs, a segment running through it
  is.
- **The frames**: every sampled tick (0.05 s) of every airborne locomotion clip the hero plays (`airborne_clips`,
  MOBILITY_AND_WORK 4.1: everyone's jump, leap, fall and ladder climb; an acrobat's air jump, wall kick and mantle; a
  climber's cling and climb down; a glider's and a flyer's glide, flight, take-off, landing and shot-down clip; the
  boss flyer's circle; a hover hero's hover; riding with a mount carrier; the default dive with a `dive` move), and
  every tick of any other clip of the hero's chain where the base body is off the ground (its lowest point more than
  `AIR_TOL` px up: a leap inside a move, a floating hero's idle); each with the secondary loop and the wing loop the
  game lays over it (as the previews do). Actions play over their base. Every phase's tail cubes count.
- **The standing turnaround**: the stance (the turnaround's pose) passes the same zone, and from the front (the
  turnaround's front view, every phase look) the tail shows below the crotch between the legs' outer edges over no
  more than one model pixel's area (`FRONT_PX`: a hairline of a tail behind her seen through the gap between the
  thighs is not a tail between the legs).
- **A NOTE** (not an error) for a tail tip pointing straight down (within `DOWN_DEG` of plumb) below the knees in
  any of those frames.

A tail that trails behind (level with or above the hips, continuing the body's line) passes: it leaves the zone
through the back plane. `docs/heroes-v2/notes/artgen.md`, "No tail between the legs".
"""
import math
import re

import numpy as np

TICK = 0.05
AIR_TOL = 1.0               # px: the base body's lowest point this far above the ground is off it
SPINE = (0.05, 0.275, 0.5, 0.725, 0.95)   # where along a tail cube's centre line it is sampled
BACK_TOL = 0.5              # px: the zone starts this far in front of the hips' back face (a tail may run down the seat)
DOWN_DEG = 20.0             # a tip within this many degrees of plumb points straight down
FRONT_PITCH = 10.0          # the turnaround's camera pitch (render.scene.PITCH)
FRONT_SCALE = 6
FRONT_PX = FRONT_SCALE * FRONT_SCALE        # one model pixel's area in the front view

# the airborne locomotion clips and who plays them (MOBILITY_AND_WORK 4.1): everyone jumps, leaps, falls and climbs
# a ladder (and a dive move without its own clip plays move.air_dive); the rest by mobility trait, the riding clips
# with a mount carrier. A hero source without a mobility block has no trait (MOBILITY_AND_WORK 1).
EVERYONE = ("move.jump", "move.leap", "move.fall", "move.climb")
DIVE = "move.air_dive"      # a hero with a `dive` move (its generated default, MOBILITY_AND_WORK 7.3)
BY_TRAIT = {
    "acrobat": ("move.air_jump", "move.wall_kick", "move.mantle"),
    "climber": ("move.climb_idle", "move.climb_down", "move.mantle"),
    "glider": ("move.glide_start", "move.glide", "move.gust", "move.downed"),
    "flyer": ("move.takeoff", "move.fly", "move.fly_fast", "move.fly_hover", "move.fly_land", "move.glide_start",
              "move.glide", "move.gust", "move.circle", "move.downed"),
    "hover": ("move.hover", "move.drift", "move.downed"),
}
MOUNTED = ("move.ride", "move.ride_cast")
AIRBORNE = tuple(dict.fromkeys(EVERYONE + (DIVE,) + sum(BY_TRAIT.values(), ()) + MOUNTED))
# airborne actions and the base they play over (the core's `over`)
AIRBORNE_OVER = {"move.air_dive": "move.leap"}
LEG_BONES = ("leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l")


TAIL_ROOT = re.compile(r"^(?:fine_|p[1-9]_)*tail_1(?:_[a-k])?$")
SHELL_PREFIXES = ("fine_", "p1_", "p2_", "p3_", "p4_")


def tail_bones(parents):
    """Every tail chain's bones ({bone: parent}): the rig's `tail_1` and every other chain root a tail part builds
    beside it (C2: a fox's copies `tail_1_b`.., their level-of-detail copies `fine_tail_1_d`.., a phase part's own
    `p2_tail_1`..), each with every bone below it; empty for a model without a tail. Before C2 only `tail_1`'s chain
    counted, so a fox's copies (children of `body`) were never checked."""
    roots = {b for b in parents if TAIL_ROOT.match(b)}
    if not roots:
        return set()
    out = set(roots)
    grew = True
    while grew:
        grew = False
        for b, p in parents.items():
            if p in out and b not in out:
                out.add(b)
                grew = True
    return out


def _spine(o, s):
    """Points along a cube's centre line, the long way (a tail segment's spine): `SPINE` shares of its length,
    through the centres of its other two sides."""
    o, s = np.asarray(o, float), np.asarray(s, float)
    k = int(np.argmax(s))
    pts = np.tile(o + s / 2.0, (len(SPINE), 1))
    pts[:, k] = o[k] + np.array(SPINE) * s[k]
    return pts


class TailRule:
    """The zone and the tip of one model (a rig.Model as build.build_files gives it)."""

    def __init__(self, model):
        from anim.skeleton import Skeleton
        from rot import file_matrix
        self.sk = Skeleton.from_model(model)
        self.env = model.env
        self.bones = tail_bones(dict(self.sk.parent))
        self.points = {}                        # tail bone -> (N, 3) sample points in its bone space
        for c in model.cubes:
            if c.bone not in self.bones:
                continue
            pts = _spine(c.origin, c.size)
            if c.rotation is not None:
                m, pv = file_matrix(c.rotation), np.array(c.pivot, float)
                pts = (m @ (pts - pv).T).T + pv
            self.points[c.bone] = np.vstack([self.points[c.bone], pts]) if c.bone in self.points else pts
        self.has_tail = bool(self.points)
        # the tips: each chain's deepest bone with cubes, and its sample point farthest from its pivot; `tip` is the
        # main chain's (the rig's tail_1, or the first chain), `tips` every chain's (C2: a fox's nine tails)
        self.tip, self.tips = None, []
        if self.has_tail:
            parents = dict(self.sk.parent)

            def depth(b):
                return len(self.sk.chain_to_root(b))

            def shell(b):
                # a fine_/pN_ shell nested over a chain bone (the same pivot), not the chain's continuation
                return any(b == pre + str(parents.get(b)) for pre in SHELL_PREFIXES)

            def root_of(b):
                while not TAIL_ROOT.match(b) or shell(b):
                    b = parents[b]
                return b

            chains = {}
            for b in self.points:
                chains.setdefault(root_of(b), []).append(b)
            for root in sorted(chains, key=lambda r: (r != "tail_1", r)):
                bones = chains[root]
                chain = [b for b in bones if not shell(b)] or bones
                tb = max(chain, key=lambda b: (depth(b), b))
                pv = self.sk.pivot[tb]
                far = self.points[tb][int(np.argmax(np.linalg.norm(self.points[tb] - pv, axis=1)))]
                self.tips.append((tb, pv, far))
            self.tip = self.tips[0]
        self.hips = np.array(self.sk.body["body"])
        self.legs = {b: np.array(self.sk.body[b]) for b in LEG_BONES if b in self.sk.body}
        self.hips_c = self.hips.mean(axis=0)

    # -------------------------------------------------------------- one frame

    def zone(self, world):
        """(crotch y, hips centre, lateral axis, lo, hi, back axis, back) of a frame's world transforms."""
        r, t = world["body"]
        hips = (r @ self.hips.T).T + t
        c = r @ self.hips_c + t
        lat, back_ax = r[:, 0], r[:, 2]
        legs = np.vstack([(world[b][0] @ p.T).T + world[b][1] for b, p in self.legs.items()])
        u = (legs - c) @ lat
        back = float(((hips - c) @ back_ax).max())
        return float(hips[:, 1].min()), c, lat, float(u.min()), float(u.max()), back_ax, back

    def hits(self, world):
        """{tail bone: points in the zone} for one frame (empty when clean)."""
        crotch, c, lat, lo, hi, back_ax, back = self.zone(world)
        out = {}
        for b, pts in self.points.items():
            r, t = world[b]
            w = (r @ pts.T).T + t
            d = w - c
            u, z = d @ lat, d @ back_ax
            inside = (w[:, 1] < crotch - 1e-6) & (u > lo + 1e-6) & (u < hi - 1e-6) & (z < back - BACK_TOL)
            k = int(inside.sum())
            if k:
                out[b] = k
        return out

    def tip_down(self, world):
        """The angle from plumb (degrees) of the tip that points most nearly straight down below the knees, else
        None (every chain's tip)."""
        knees = min(float((world[s][0] @ self.sk.pivot[s] + world[s][1])[1]) for s in ("shin_r", "shin_l"))
        best = None
        for b, pv, far in self.tips:
            r, t = world[b]
            p0, p1 = r @ pv + t, r @ far + t
            v = p1 - p0
            n = float(np.linalg.norm(v))
            if n < 1e-6:
                continue
            ang = math.degrees(math.acos(max(-1.0, min(1.0, -v[1] / n))))
            if ang <= DOWN_DEG and p1[1] < knees and (best is None or ang < best):
                best = ang
        return best

    def lowest(self, world):
        """The base body's lowest point (height) in a frame."""
        best = None
        for b, pts in self.sk.body.items():
            r, t = world[b]
            y = float(((r @ np.array(pts).T).T + t)[:, 1].min())
            best = y if best is None or y < best else best
        return best


# ------------------------------------------------------------------ the frames

def _times(length, loop):
    n = max(1, int(round(length / TICK)))
    if loop:
        return [i * TICK for i in range(n)]
    return [min(i * TICK, length) for i in range(n + 1)]


def airborne_clips(a):
    """The airborne locomotion clips this hero plays: everyone's, its traits', riding with a mount carrier and the
    default dive with a `dive` move."""
    mob = (a.hero or {}).get("mobility") or {}
    out = list(EVERYONE)
    for k in ("primary", "secondary"):
        trait = (mob.get(k) or {}).get("trait")
        out += BY_TRAIT.get(trait, ())
    if a.bones("mount"):
        out += MOUNTED
    if _has_form((a.hero or {}).get("moves") or [], "dive"):
        out.append(DIVE)
    return tuple(dict.fromkeys(out))


def _has_form(x, form):
    if isinstance(x, dict):
        return x.get("form") == form or any(_has_form(v, form) for v in x.values())
    if isinstance(x, list):
        return any(_has_form(v, form) for v in x)
    return False


def frames(a, lookup, names=None):
    """Yield (clip name, t, pose, airborne by name) for every clip of the hero's chain, as the game plays it: a base
    clip on its own, an action over its base (its `over`, else combat.idle or move.idle) from its blend to its end;
    each pose with the secondary loop and the wing loop laid over it."""
    from render import hero_previews as hp
    from render.animfile import Playback
    d = a.form_var()
    plays = airborne_clips(a)
    for name in (names if names is not None else a.chain.names()):
        if name.startswith("sec.") or (name in AIRBORNE and name not in plays):
            continue
        clip = a.chain.get(name)
        if clip is None:
            continue
        built = lookup(name) if lookup else None
        meta = built.meta if built is not None else {}
        kind = meta.get("kind") or ("action" if clip.loop == "once" else "base")
        if name in AIRBORNE_OVER:
            kind = "action"
        if kind == "action":
            base = AIRBORNE_OVER.get(name) or meta.get("over") or (
                "combat.idle" if a.chain.get("combat.idle") is not None else "move.idle")
            if a.chain.get(base) is None:
                base = "move.idle"
            pb = Playback(a.chain, base, name, 0.0, d=d)
            times = _times(pb.blend + clip.length, False)
        else:
            pb = Playback(a.chain, name, None, 0.0, d=d)
            times = _times(clip.length, clip.loop == "loop")
        loop = hp.wing_loop_of(name)
        for t in times:
            yield name, t, hp.wings(a, hp.secondary(a, pb.pose(t), t), t, loop), name in plays


def front_pixels(a, rule, pose, phase=1):
    """Tail pixels the turnaround's front view shows below the crotch between the legs' outer edges."""
    from render import raster
    view = raster.View(0.0, FRONT_PITCH, FRONT_SCALE)
    fr = a.render(view, pose, phase=phase, frame=True)
    world = rule.sk.world(pose)
    r, t = world["body"]
    hips = (r @ rule.hips.T).T + t
    legs = np.vstack([(world[b][0] @ p.T).T + world[b][1] for b, p in rule.legs.items()])
    to = raster.to_render
    crotch_y = max(fr.to_screen(view, to(p))[1] for p in hips)
    xs = [fr.to_screen(view, to(p))[0] for p in legs]
    lo, hi = min(xs), max(xs)
    ids = fr.ids
    n = 0
    tails = rule.bones
    H, W = ids.shape
    for y in range(int(math.ceil(crotch_y)) + 1, H):
        row = ids[y]
        for x in range(max(0, int(math.ceil(lo))), min(W, int(math.floor(hi)) + 1)):
            i = int(row[x])
            if i >= 0 and fr.quads[i].bone in tails:
                n += 1
    return n


def check(a, model, lookup=None, names=None):
    """(problems, notes) of the tail rule for a hero (HeroAssets `a` with its chain, the built model; `lookup` gives
    the built clip of a name, for its kind and base; `names` limits the clips, for the tests)."""
    rule = TailRule(model)
    if not rule.has_tail:
        return [], []
    problems, notes = [], []
    worst = {}                                   # clip -> (points, t, bones)
    downs = {}
    need = sorted(set(rule.sk.body) | set(rule.points) | {"shin_r", "shin_l"})
    for name, t, pose, by_name in frames(a, lookup, names):
        world = rule.sk.world(pose, only=need)
        if not by_name and rule.lowest(world) <= AIR_TOL:
            continue
        h = rule.hits(world)
        if h:
            k = sum(h.values())
            if k > worst.get(name, (0,))[0]:
                worst[name] = (k, t, sorted(h, key=lambda b: -h[b]))
        ang = rule.tip_down(world)
        if ang is not None and name not in downs:
            downs[name] = (t, ang)
    for name in sorted(worst):
        k, t, bones = worst[name]
        problems.append("%s: the tail hangs between the legs below the crotch (%d points of %s at %.2f s; a tail in "
                        "the air trails behind, level with or above the hips)" % (name, k, ", ".join(bones[:3]), t))
    stance = a.stance()
    world = rule.sk.world(stance)
    h = rule.hits(world)
    if h:
        problems.append("the stance: the tail hangs between the legs below the crotch (%d points of %s)"
                        % (sum(h.values()), ", ".join(sorted(h)[:3])))
    phases = sorted({1} | {int(p["phase"]) for p in a.visual.get("phases", [])})
    px = max(front_pixels(a, rule, stance, phase=ph) for ph in phases)
    if px > FRONT_PX:
        problems.append("the turnaround's front view: %d tail pixels show between the legs below the crotch (at most "
                        "%d, one model pixel)" % (px, FRONT_PX))
    ang = rule.tip_down(world)
    if ang is not None:
        downs["the stance"] = (0.0, ang)
    for name in sorted(downs):
        t, ang = downs[name]
        notes.append("%s: the tail's tip points straight down below the knees (%.0f degrees from plumb at %.2f s)"
                     % (name, ang, t))
    return problems, notes
