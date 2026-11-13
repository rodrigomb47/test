"""Procedural motions (HEROES_V2.md 13.6 "Primitives"): Python functions with parameters that return a clip.

A recipe uses a primitive as a layer: `add: true` adds its channels to the pose keys (breathing, a weight shift, a
glance, an ear twitch), `add: false` replaces the channels it keys (a walk cycle drives the legs, arms and body).
Smooth motions are sampled every tick and simplified (keys a straight line reproduces within 0.1 degrees or 0.01 px
are dropped), short sharp ones (an ear twitch) are written as a few eased keys.

Rotations are file degrees and positions file pixels, with the sign conventions of poses.py. Everything that depends
on leg lengths (the root height that keeps a foot on the ground while the legs swing, the leg tilt that keeps the
feet planted under a hip shift) is solved on all three forms and set to the compromise that minimises the worst
error: one file serves every form, and the chibi, teen and adult stay within half a pixel (tests check all six
bases).

Implemented for G3: breathe, weight_shift, look, ear_twitch, walk_cycle, run_cycle. G4 (the core library) adds
crouch_cycle and carry_cycle (gaits with form-scaled root heights, anim/clip.py), oscillate (sways, waves, nods,
shakes, pumps, stirring circles, a dizzy head circle, a swimmer's bob) and flinch (an impulse: a hit, a jolt). The
design's keyed motions (dodge, hurt, stagger, kneel, death, greet_bow, wave, cheer, work strokes) are pose keys in
herosrc/anim/core with these layers on top; swing, thrust, slam, spin, leap, land, cast, draw_bow, throw, guard,
taunt and play_instrument come with the style libraries (G5).
"""
import math

from anim.clip import TICK, Clip, Key, Track, dense_track, simplify
from util import HerogenError, Rng

ROT_TOL = 0.1
POS_TOL = 0.01
TAU = 2.0 * math.pi


class Ctx:
    def __init__(self, length, skeleton, seed="", loop=True, forms=None):
        self.length = float(length)
        self.skeleton = skeleton
        self.forms = list(forms or [skeleton])     # one skeleton per body form: offsets are solved over all of them
        self.seed = seed
        self.loop = loop


def compromise(values):
    """The value that minimises the worst error over the forms' own solutions (the middle of their range)."""
    return 0.5 * (min(values) + max(values))


def _wrap(d):
    return (d + 0.5) % 1.0 - 0.5


def bump(phase, centre, width):
    """A smooth periodic bump of height 1 at `centre` (phases in cycles)."""
    d = _wrap(phase - centre)
    return math.exp(-0.5 * (d / width) ** 2)


def smoothstep(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3.0 - 2.0 * x)


def envelope(t, t0, t1, t2, t3):
    """0 before t0, eased up to 1 at t1, held to t2, eased down to 0 at t3."""
    if t <= t0 or t >= t3:
        return 0.0
    if t < t1:
        return smoothstep((t - t0) / (t1 - t0))
    if t <= t2:
        return 1.0
    return 1.0 - smoothstep((t - t2) / (t3 - t2))


def _dense_clip(name, ctx, fns, rot_tol=ROT_TOL, pos_tol=POS_TOL, kfns=None):
    """A clip from {(bone, channel): fn(t) -> 3-vector}, sampled every tick over the clip and simplified; kfns gives
    the FORM_VAR coefficient of form-scaled channels (anim/clip.py)."""
    clip = Clip(name, ctx.length, "loop" if ctx.loop else "once")
    for (bone, ch), fn in sorted(fns.items()):
        kfn = (kfns or {}).get((bone, ch))
        tr = simplify(dense_track(fn, 0.0, ctx.length, kfn=kfn), rot_tol if ch == "rotation" else pos_tol)
        if tr.max_abs() > 1e-6:
            clip.tracks[(bone, ch)] = tr
    return clip


def form_fit(values):
    """(a, b): the line a + b x FORM_VAR through [(FORM_VAR, value)] of the forms (least squares)."""
    n = float(len(values))
    md = sum(d for d, _ in values) / n
    mv = sum(v for _, v in values) / n
    var = sum((d - md) ** 2 for d, _ in values)
    b = sum((d - md) * (v - mv) for d, v in values) / var if var > 1e-12 else 0.0
    return mv - b * md, b


def _periodic_check(ctx, period, what):
    n = ctx.length / period
    if ctx.loop and abs(n - round(n)) > 1e-6:
        raise HerogenError("%s: the clip length %.3g s is not a whole number of %.3g s periods" % (what, ctx.length,
                                                                                                 period))


# ------------------------------------------------------------------ idle layers

def breathe(ctx, period=3.0, depth=1.0):
    """Breathing (additive): the chest lifts and tips back a little, the shoulders rise, the head stays level."""
    _periodic_check(ctx, period, "breathe")

    def b(t):
        return 0.5 - 0.5 * math.cos(TAU * t / period)

    d = float(depth)
    return _dense_clip("breathe", ctx, {
        ("chest", "rotation"): lambda t: (-0.9 * d * b(t), 0.0, 0.0),
        ("chest", "position"): lambda t: (0.0, 0.14 * d * b(t), 0.0),
        ("waist", "rotation"): lambda t: (-0.25 * d * b(t), 0.0, 0.0),
        ("neck", "rotation"): lambda t: (1.15 * d * b(t), 0.0, 0.0),
        ("arm_r", "rotation"): lambda t: (0.0, 0.0, 1.2 * d * b(t)),
        ("arm_l", "rotation"): lambda t: (0.0, 0.0, -1.2 * d * b(t)),
        ("forearm_r", "rotation"): lambda t: (-0.8 * d * b(t), 0.0, 0.0),
        ("forearm_l", "rotation"): lambda t: (-0.8 * d * b(t), 0.0, 0.0),
    }, rot_tol=0.05, pos_tol=0.005)


def weight_shift(ctx, shifts=(), amount=0.4):
    """Weight shifts onto one leg (additive): the hips slide over the standing foot and drop on the free side, the
    chest and head counter-tilt, the legs tilt so the feet stay where they are. shifts: [[t0, t1, t2, t3, side]]
    (eased in from t0 to t1, held to t2, out by t3; side "r" or "l")."""
    hips = [sk.env["hip_y"] for sk in ctx.forms]

    def s(t):
        v = 0.0
        for t0, t1, t2, t3, side in shifts:
            v += (1.0 if side == "r" else -1.0) * envelope(t, t0, t1, t2, t3)
        return v

    def dx(t):
        return -float(amount) * s(t)            # onto the right foot = toward -x

    def leg_tilt(t):
        # the foot moves dx - hip x sin(tilt): the tilt that leaves the smallest drift on the shortest and the
        # longest legs
        return math.degrees(math.asin(max(-1.0, min(1.0, 2.0 * dx(t) / (min(hips) + max(hips))))))

    return _dense_clip("weight_shift", ctx, {
        ("root", "position"): lambda t: (dx(t), 0.0, 0.0),
        ("body", "rotation"): lambda t: (0.0, 1.5 * s(t), 1.8 * s(t)),
        ("chest", "rotation"): lambda t: (0.0, -0.8 * s(t), -1.2 * s(t)),
        ("neck", "rotation"): lambda t: (0.0, -0.7 * s(t), -0.6 * s(t)),
        ("leg_r", "rotation"): lambda t: (0.0, 0.0, leg_tilt(t)),
        ("leg_l", "rotation"): lambda t: (0.0, 0.0, leg_tilt(t)),
        ("foot_r", "rotation"): lambda t: (0.0, 0.0, -leg_tilt(t)),
        ("foot_l", "rotation"): lambda t: (0.0, 0.0, -leg_tilt(t)),
        ("arm_r", "rotation"): lambda t: (0.0, 0.0, -1.5 * s(t)),
        ("arm_l", "rotation"): lambda t: (0.0, 0.0, -1.5 * s(t)),
    })


def look(ctx, glances=()):
    """Glances (additive, on the neck: the head itself stays free for the procedural head look). glances:
    [[t0, t1, t2, t3, yaw, pitch]]: turn from t0 to t1, hold to t2, back by t3."""

    def v(t):
        y = p = 0.0
        for t0, t1, t2, t3, yaw, pitch in glances:
            e = envelope(t, t0, t1, t2, t3)
            y += yaw * e
            p += pitch * e
        return (p, y, -0.12 * y)

    return _dense_clip("look", ctx, {("neck", "rotation"): v})


EAR_FLICK = (-14.0, 16.0, -24.0)        # right ear: back, opening turned out, tipped out (left: mirrored)


def ear_times(ctx, every=(3.0, 6.0), seed=None):
    """Deterministic twitch times every 3-6 s (seeded by the clip name), alternating ears, now and then a double."""
    rng = Rng("ear_twitch:" + (seed or ctx.seed))
    out, t, side = [], 0.0, "r"
    while True:
        t += every[0] + (every[1] - every[0]) * rng.random()
        if t > ctx.length - 0.6:
            break
        out.append([round(round(t / TICK) * TICK, 4), side])
        if rng.random() < 0.25 and t + 0.5 < ctx.length - 0.6:
            out.append([round(round((t + 0.3) / TICK) * TICK, 4), side])
            t += 0.3
        side = "l" if side == "r" else "r"
    return out


def ear_twitch(ctx, twitches=None, every=(3.0, 6.0), flick=EAR_FLICK, seed=None):
    """One ear flicks back and out: up in one tick, held one tick, back in three (the references' ~24 degree flick
    for 2 ticks, reference-study 11.8). twitches: [[t, side]] or None for seeded times every 3-6 s."""
    tw = twitches if twitches is not None else ear_times(ctx, every, seed)
    clip = Clip("ear_twitch", ctx.length, "loop" if ctx.loop else "once")
    for side in ("r", "l"):
        mine = sorted(float(t) for t, s in tw if s == side)
        if not mine:
            continue
        f = tuple(flick) if side == "r" else (flick[0], -flick[1], -flick[2])
        tr = Track([Key(0.0, (0, 0, 0)), Key(ctx.length, (0, 0, 0))])
        for t in mine:
            if t - TICK < 0 or t + 5 * TICK > ctx.length:
                raise HerogenError("ear_twitch at %.2f s does not fit in the clip" % t)
            tr.put(Key(t - TICK, (0, 0, 0)))
            tr.put(Key(t, f, "easeOutQuad"))
            tr.put(Key(t + TICK, f))
            tr.put(Key(t + 4 * TICK, (0, 0, 0), "easeInOutSine"))
        clip.tracks[("ear_" + side, "rotation")] = tr
    clip.meta["twitches"] = tw
    return clip


# ------------------------------------------------------------------ locomotion

def _gait(ctx, name, p, flight=None):
    """A walk or run cycle: the right foot strikes at phase 0, the left at 0.5. G4: `bend` bends both knees through the
    whole cycle (a crouch) and the root height is then a form-scaled key fitted over the forms (exact on each, where
    the G3 gaits keep the compromise); `upper` 0 leaves the arms to the recipe's pose keys (a carry)."""
    L = ctx.length
    bend = p.get("bend", 0.0)
    scaled = bend > 0
    A, knee, load, arms, flex, flex_swing = p["stride"], p["knee"], p["load"], p["arms"], p["elbow"], p["elbow_swing"]
    lean, twist, tilt, sway, toe, heel = p["lean"], p["twist"], p["tilt"], p["sway"], p["toe_off"], p["heel"]
    knee_at, knee_w = p["knee_at"], p["knee_width"]

    def ph(t, side):
        return t / L + (0.0 if side == "r" else 0.5)

    def leg(t, side):
        return -A * math.cos(TAU * ph(t, side))

    def leg_bent(t, side):
        return leg(t, side) - bend

    def shin(t, side):
        x = ph(t, side)
        return load * bump(x, 0.07, 0.07) + knee * bump(x, knee_at, knee_w) + 2.0 * bend

    def foot(t, side):
        x = ph(t, side)
        return -(leg_bent(t, side) + shin(t, side)) + toe * bump(x, 0.5, 0.06) - heel * bump(x, 0.0, 0.05)

    def legs_pose(t):
        return {"leg_r": {"rotation": (leg_bent(t, "r"), 0, 0)}, "shin_r": {"rotation": (shin(t, "r"), 0, 0)},
                "foot_r": {"rotation": (foot(t, "r"), 0, 0)}, "leg_l": {"rotation": (leg_bent(t, "l"), 0, 0)},
                "shin_l": {"rotation": (shin(t, "l"), 0, 0)}, "foot_l": {"rotation": (foot(t, "l"), 0, 0)}}

    fits = {}

    def lift(t):
        key = round(t, 6)
        if key not in fits:
            pose = legs_pose(t)
            if scaled:
                fits[key] = form_fit([(f.d, -f.lowest_sole(pose)) for f in ctx.forms])
            else:
                fits[key] = (compromise([-f.lowest_sole(pose) for f in ctx.forms]), 0.0)
        return fits[key]

    def root(t):
        y = lift(t)[0]
        if flight:
            y += flight * (bump(t / L, p["flight_at"], 0.07) + bump(t / L, p["flight_at"] + 0.5, 0.07))
        y += p.get("bob", 0.0) * math.cos(2.0 * TAU * t / L)
        return (-sway * math.sin(TAU * t / L), y, 0.0)

    def root_k(t):
        return (0.0, lift(t)[1], 0.0)

    def c(t):
        return math.cos(TAU * t / L)

    def s(t):
        return math.sin(TAU * t / L)

    def arm(t, side):
        k = 1.0 if side == "r" else -1.0
        return (k * arms * c(t), 0.0, k * p["arm_out"])

    def forearm(t, side):
        k = 1.0 if side == "r" else -1.0
        return (-(flex + flex_swing * 0.5 * (1.0 - k * c(t))), 0.0, 0.0)

    fns = {
        ("root", "position"): root,
        ("body", "rotation"): lambda t: (lean, -twist * c(t), tilt * s(t)),
        ("waist", "rotation"): lambda t: (p["waist_lean"], 0.4 * twist * c(t), -0.3 * tilt * s(t)),
        ("chest", "rotation"): lambda t: (p["chest_lean"], 0.6 * twist * c(t), -0.4 * tilt * s(t)),
        ("neck", "rotation"): lambda t: (-(lean + p["waist_lean"] + p["chest_lean"]) * p["head_level"], 0.0,
                                         -0.3 * tilt * s(t)),
        ("skirt_f", "rotation"): lambda t: (0.45 * min(leg(t, "r"), leg(t, "l")), 0.0, 0.0),
        ("skirt_b", "rotation"): lambda t: (0.45 * max(leg(t, "r"), leg(t, "l")), 0.0, 0.0),
        ("skirt_r", "rotation"): lambda t: (0.35 * leg(t, "r"), 0.0, 0.0),
        ("skirt_l", "rotation"): lambda t: (0.35 * leg(t, "l"), 0.0, 0.0),
        ("tail_1", "rotation"): lambda t: (p["tail_lift"], p["tail_sway"] * s(t), 0.0),
    }
    if p.get("upper", 1.0):
        fns[("arm_r", "rotation")] = lambda t: arm(t, "r")
        fns[("arm_l", "rotation")] = lambda t: arm(t, "l")
        fns[("forearm_r", "rotation")] = lambda t: forearm(t, "r")
        fns[("forearm_l", "rotation")] = lambda t: forearm(t, "l")
    for side in ("r", "l"):
        fns[("leg_" + side, "rotation")] = (lambda sd: lambda t: (leg_bent(t, sd), 0.0, 0.0))(side)
        fns[("shin_" + side, "rotation")] = (lambda sd: lambda t: (shin(t, sd), 0.0, 0.0))(side)
        fns[("foot_" + side, "rotation")] = (lambda sd: lambda t: (foot(t, sd), 0.0, 0.0))(side)
    clip = _dense_clip(name, ctx, fns, kfns={("root", "position"): root_k} if scaled else None)
    clip.meta["steps"] = True
    return clip


WALK = {"stride": 21.0, "knee": 42.0, "load": 9.0, "knee_at": 0.68, "knee_width": 0.12, "arms": 17.0, "arm_out": 6.5,
        "elbow": 9.0, "elbow_swing": 13.0, "lean": 2.5, "waist_lean": 0.0, "chest_lean": 0.5, "head_level": 1.0,
        "twist": 4.0, "tilt": 1.6, "sway": 0.22, "toe_off": 14.0, "heel": 7.0, "tail_sway": 4.0, "tail_lift": 0.0}
RUN = {"stride": 36.0, "knee": 96.0, "load": 20.0, "knee_at": 0.64, "knee_width": 0.12, "arms": 32.0, "arm_out": 7.0,
       "elbow": 72.0, "elbow_swing": 14.0, "lean": 9.0, "waist_lean": 1.5, "chest_lean": 2.5, "head_level": 0.85,
       "twist": 6.5, "tilt": 2.0, "sway": 0.18, "toe_off": 22.0, "heel": 4.0, "flight_at": 0.42, "tail_sway": 5.0,
       "tail_lift": -12.0, "upper": 1.0}


def _params(defaults, given, what):
    out = dict(defaults)
    for k, v in (given or {}).items():
        if k not in defaults:
            raise HerogenError("%s: unknown parameter %r (%s)" % (what, k, ", ".join(sorted(defaults))))
        out[k] = float(v)
    return out


def walk_cycle(ctx, **params):
    """A walk cycle in place (1.0 s in the core library): legs swing +-21 degrees with a knee lift in the swing, the
    feet roll heel to toe, the root follows the lower foot so a sole stays on the ground, the hips turn and tip with
    the stride and the shoulders counter-turn, the arms swing opposite the legs, the skirt panels follow the legs
    and the tail sways (the springs of secondary.py add the follow-through)."""
    return _gait(ctx, "walk_cycle", _params(WALK, params, "walk_cycle"))


def run_cycle(ctx, flight=0.7, **params):
    """A run cycle in place (0.6 s): a longer stride, a high knee in the swing, a 9 degree forward lean, arms bent
    and pumping, a short flight phase after each push-off. `upper` 0 (G5) leaves the arms to the recipe's pose keys:
    a style's run that carries its weapon."""
    return _gait(ctx, "run_cycle", _params(RUN, params, "run_cycle"), flight=float(flight))


CROUCH = dict(WALK, stride=14.0, knee=30.0, load=4.0, arms=7.0, arm_out=10.0, elbow=30.0, elbow_swing=8.0, lean=18.0,
              chest_lean=3.0, head_level=0.9, twist=3.0, tilt=1.0, sway=0.15, toe_off=8.0, heel=4.0, tail_sway=7.0,
              tail_lift=-6.0, bend=38.0, bob=0.0, upper=1.0)
CARRY = dict(WALK, stride=16.0, knee=34.0, load=11.0, lean=-3.0, chest_lean=-1.5, head_level=1.0, twist=2.0,
             tilt=2.4, sway=0.3, toe_off=10.0, heel=6.0, tail_sway=3.0, bend=0.0, bob=0.0, upper=0.0)


def crouch_cycle(ctx, **params):
    """A sneaking walk in place (1.2 s in the core library): both knees bent about 38 degrees through the cycle, an 18
    degree forward lean with the head kept up, a short careful stride, arms held a little out and forward with small
    swings. The root height is form-scaled (exact on every form, anim/clip.py): no compromise holds a deep bend."""
    return _gait(ctx, "crouch_cycle", _params(CROUCH, params, "crouch_cycle"))


def carry_cycle(ctx, **params):
    """A loaded walk in place (carry.loop): a shorter, heavier stride with more side-to-side tip, the body leaning a
    little back against the load; the arms are left to the recipe's pose keys (holding the load)."""
    return _gait(ctx, "carry_cycle", _params(CARRY, params, "carry_cycle"))


# ------------------------------------------------------------------ G4: oscillations and impulses

def _window(ctx, window):
    if window is None:
        return lambda t: 1.0
    if len(window) != 4 or not all(isinstance(x, (int, float)) for x in window):
        raise HerogenError("window must be [t0, t1, t2, t3] (eased in from t0 to t1, held to t2, out by t3)")
    t0, t1, t2, t3 = (float(x) for x in window)
    return lambda t: envelope(t, t0, t1, t2, t3)


def oscillate(ctx, bones=None, period=1.0, window=None, channel="rotation", shape="sine", phase=0.0):
    """Periodic motion added to bones (additive): bones {bone: [ax, ay, az]} or {bone: {"amp": [...], "phase":
    cycles}}; `shape` "sine" swings both ways, "pulse" (1 - cos) / 2 only one way (a nod down, a pump up), "circle"
    turns x and z a quarter cycle apart (a stirring hand, a dizzy head: amp [ax, 0, az]). `window` [t0, t1, t2, t3]
    fades it in and out; without one the clip must hold a whole number of periods (a loop)."""
    if channel not in ("rotation", "position"):
        raise HerogenError("oscillate: channel must be rotation or position")
    if shape not in ("sine", "pulse", "circle"):
        raise HerogenError("oscillate: shape must be sine, pulse or circle")
    if window is None:
        _periodic_check(ctx, float(period), "oscillate")
    env = _window(ctx, window)
    fns = {}
    for bone, spec in sorted((bones or {}).items()):
        if isinstance(spec, dict):
            amp, ph = spec["amp"], float(spec.get("phase", 0.0))
        else:
            amp, ph = spec, 0.0
        amp = tuple(float(a) for a in amp)
        ph += float(phase)

        def fn(t, amp=amp, ph=ph):
            x = TAU * (t / float(period) + ph)
            e = env(t)
            if shape == "sine":
                f = (math.sin(x),) * 3
            elif shape == "pulse":
                f = (0.5 - 0.5 * math.cos(x),) * 3
            else:
                f = (math.sin(x), math.cos(x), math.cos(x))
            return tuple(amp[c] * f[c] * e for c in range(3))
        fns[(bone, channel)] = fn
    return _dense_clip("oscillate", ctx, fns, rot_tol=0.05, pos_tol=0.005)


def flinch(ctx, t=0.0, bones=None, rise=0.08, decay=0.22, rebound=0.25, channel="rotation"):
    """An impulse added to bones (additive): each bone turns to its peak offset in `rise` seconds (ease out), then
    returns over about 3 x `decay` with a small swing past rest (`rebound` x the peak): a hit, a jolt, a recoil.
    bones {bone: [rx, ry, rz]} peaks (file degrees, or pixels with channel position)."""
    t0, rise, decay, rebound = float(t), float(rise), float(decay), float(rebound)

    def f(tt):
        x = tt - t0
        if x <= 0:
            return 0.0
        if x < rise:
            u = x / rise
            return 1.0 - (1.0 - u) * (1.0 - u)
        y = (x - rise) / decay
        return math.exp(-y) * math.cos(min(y, 3.0) * (math.pi / 2.0 + math.atan(rebound * 4.0)) / 1.5)

    fns = {}
    for bone, peak in sorted((bones or {}).items()):
        pk = tuple(float(a) for a in peak)
        fns[(bone, channel)] = lambda tt, pk=pk: tuple(pk[c] * f(tt) for c in range(3))
    clip = _dense_clip("flinch", ctx, fns, rot_tol=0.05, pos_tol=0.005)
    return clip


PRIMITIVES = {
    "breathe": breathe,
    "weight_shift": weight_shift,
    "look": look,
    "ear_twitch": ear_twitch,
    "walk_cycle": walk_cycle,
    "run_cycle": run_cycle,
    "crouch_cycle": crouch_cycle,
    "carry_cycle": carry_cycle,
    "oscillate": oscillate,
    "flinch": flinch,
}


def run(name, ctx, params):
    if name not in PRIMITIVES:
        raise HerogenError("primitive %r is unknown (%s)" % (name, ", ".join(sorted(PRIMITIVES))))
    try:
        return PRIMITIVES[name](ctx, **(params or {}))
    except TypeError as e:
        raise HerogenError("primitive %s: %s" % (name, e))
