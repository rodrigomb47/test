"""A heavy tail that trails behind in the air (owner, 2026-11-19; `animations.tail_trail` of a hero source).

The library's tail follow-through (secondary.py) is baked once, on the reference skeleton, whose tail rises from the
small of the back like a cat's. A tail part built another way (the Ender Dragon's scaled tail falls from the small of
the back to the ground and trails behind) turns by the same angles into another shape: in the core and variant
flight clips her tail hung straight down between her legs ("it kinda looks like a male genitalia", the owner, looking
at her wings sheet). `tail_trail` re-keys the rig's tail chain of the clips it names, in the hero's own file, on the
hero's OWN skeleton (one body for good, HEROES_V2 3.6), so the tail trails behind her in the air like the vanilla
dragon's: each link of `tail_1`..`tail_5` is turned so it points along the trail direction in the world, with a
travelling wave for life. A clip the hero's file does not have yet (the core's `move.jump`, `move.fall`, ...) is
copied in from its style or the core first, every other track, event and marker unchanged (no hit tick moves; the
copy counts toward the own file's clip budget).

    "tail_trail": {
      "lift": 4,                   degrees above the horizontal the trail points at least (the body's line when it
                                   points higher: a dive head first trails the tail up behind)
      "wave": [amp, period, lag],  degrees the trail swings up and down, its period in seconds (a loop's period is
                                   rounded so the loop closes) and the lag from one link to the next (radians)
      "sway": [amp, period],       degrees side to side (optional)
      "clips": {name: {"ramp": [t0, t1], "fade": [t0, t1]}}
                                   the clips to re-key; `ramp` blends from the clip's own tail (0) to the trail (1)
                                   over [t0, t1] (a take-off leaves the ground), `fade` back (a landing), both
                                   optional
    }

The trail direction: straight behind the hero in the world (+z, the way she flies from) raised `lift` degrees, or the
body's line (from the hips toward the feet) when it points behind and higher (a dive head first).
Rotations are solved root first; the bone's rest rotation must be zero (the rig's tail chain has none).
"""
import math

import numpy as np

from anim.clip import TICK, Key, Track, layered, simplify
from rot import about, align, file_angles, file_matrix
from util import HerogenError

CHAIN = ("tail_1", "tail_2", "tail_3", "tail_4", "tail_5")
KEY_EVERY = 2               # ticks
TOL = 0.5                   # degrees: keys a straight line reproduces this closely are dropped
FIELDS = ("lift", "wave", "sway", "clips", "_comment")
CLIP_FIELDS = ("ramp", "fade", "_comment")


def check_spec(spec, where):
    unknown = sorted(set(spec) - set(FIELDS))
    if unknown:
        raise HerogenError("%s: unknown field(s) %s" % (where, ", ".join(unknown)))
    if not isinstance(spec.get("clips"), dict) or not spec["clips"]:
        raise HerogenError("%s: clips must name the clips to re-key" % where)
    for name, opts in spec["clips"].items():
        bad = sorted(set(opts or {}) - set(CLIP_FIELDS))
        if bad:
            raise HerogenError("%s: clips.%s: unknown field(s) %s" % (where, name, ", ".join(bad)))
        for k in ("ramp", "fade"):
            r = (opts or {}).get(k)
            if r is not None and not (len(r) == 2 and r[0] < r[1]):
                raise HerogenError("%s: clips.%s.%s must be [t0, t1] with t0 < t1" % (where, name, k))
    w = spec.get("wave", [0, 1, 0])
    if len(w) != 3 or w[1] <= 0:
        raise HerogenError("%s: wave must be [amplitude, period > 0, lag]" % where)
    s = spec.get("sway", [0, 1])
    if len(s) != 2 or s[1] <= 0:
        raise HerogenError("%s: sway must be [amplitude, period > 0]" % where)


def _weight(opts, t):
    w = 1.0
    r = opts.get("ramp")
    if r is not None:
        w *= min(1.0, max(0.0, (t - r[0]) / (r[1] - r[0])))
    f = opts.get("fade")
    if f is not None:
        w *= 1.0 - min(1.0, max(0.0, (t - f[0]) / (f[1] - f[0])))
    return w


def _period(p, clip):
    """A loop's wave period rounded so a whole number of cycles fills the clip (the loop closes)."""
    if clip.loop != "loop":
        return p
    n = max(1, int(round(clip.length / p)))
    return clip.length / n


def trail_direction(r_body, lift):
    """The direction the tail trails in the world for a body whose world rotation is r_body: straight behind the
    hero (+z, the way she flies from), raised `lift` degrees, or along the body's line (hips toward feet) when that
    points behind and higher (a dive head first). Never along the body's line otherwise: lying on her back that runs
    toward her feet."""
    line = r_body @ np.array([0.0, -1.0, 0.0])           # hips toward feet
    min_elev = math.radians(lift)
    elev = math.asin(max(-1.0, min(1.0, float(line[1]))))
    if elev >= min_elev and float(line[2]) > 0.0:
        return line / np.linalg.norm(line)
    return np.array([0.0, math.sin(min_elev), math.cos(min_elev)])


def solve(skeleton, pose, d, offsets):
    """{bone: file angles} turning each chain link so it points along d (turned by its (pitch, yaw) offset in
    degrees about the world's horizontal axis across d and the world's up axis), root first."""
    world = skeleton.world(pose, only=["body"])
    rp = world[skeleton.parent[CHAIN[0]]][0]
    up = np.array([0.0, 1.0, 0.0])
    across = np.cross(up, d)
    if np.linalg.norm(across) < 1e-6:
        across = np.array([1.0, 0.0, 0.0])
    across /= np.linalg.norm(across)
    out = {}
    links = [b for b in CHAIN if skeleton.has(b)]
    for i, b in enumerate(links):
        if any(abs(a) > 1e-9 for a in skeleton.rest[b]):
            raise HerogenError("tail_trail: %s has a rest rotation" % b)
        nxt = links[i + 1] if i + 1 < len(links) else None
        if nxt is not None:
            bind = skeleton.pivot[nxt] - skeleton.pivot[b]
        else:
            kids = [k for k, p in skeleton.parent.items() if p == b and k.startswith("tail_tip")]
            bind = (skeleton.pivot[kids[0]] - skeleton.pivot[b]) if kids else (skeleton.pivot[b] -
                                                                              skeleton.pivot[links[i - 1]])
        bind = bind / np.linalg.norm(bind)
        pitch, yaw = offsets[i]
        want = about(up, yaw) @ about(across, -pitch) @ d
        local = rp.T @ want
        m = align(bind, local)
        out[b] = file_angles(m, digits=4)
        rp = rp @ file_matrix(out[b])
    return out


def rekey(clip, base, skeleton, spec, opts, d=0.0, target=None):
    """The clip with its tail chain re-keyed to trail (a copy; every other track and event unchanged). `target`: the
    re-keyed loop a one-shot settles on (its `settle_to`); the one-shot's wave then runs on that loop's clock, ending
    where the loop starts, and its last key is the loop's first (quality.check_settle)."""
    out = clip.copy()
    orig = {b: clip.tracks.get((b, "rotation")) for b in CHAIN}
    under = {b: base.tracks.get((b, "rotation")) if base is not None else None for b in CHAIN}
    for b in CHAIN:
        out.tracks.pop((b, "rotation"), None)
    lift = float(spec.get("lift", 0.0))
    amp, period, lag = (float(x) for x in spec.get("wave", [0, 1, 0]))
    samp, speriod = (float(x) for x in spec.get("sway", [0, 1]))
    clock = target if target is not None else clip
    period, speriod = _period(period, clock), _period(speriod, clock)
    n_ticks = int(round(clip.length / TICK))
    times = sorted({round(i * TICK, 6) for i in range(0, n_ticks + 1, KEY_EVERY)} | {round(clip.length, 6)})
    links = [b for b in CHAIN if skeleton.has(b)]
    tracks = {b: Track() for b in links}
    for t in times:
        if base is not None and clip.meta.get("kind") == "action":
            bt = t % base.length if base.loop == "loop" else min(t, base.length)
            pose = layered(base, out, t, bt, d)
        else:
            pose = out.sample(t, d)
        r_body = skeleton.world(pose, only=["body"])["body"][0]
        dirn = trail_direction(r_body, lift)
        if target is not None:
            tl = t - clip.length                    # the loop's clock: 0 where the one-shot hands over to it
        else:
            tl = t % clip.length if clip.loop == "loop" and clip.length else t
        offs = []
        for i in range(len(links)):
            ph = 2 * math.pi * tl / period - i * lag
            offs.append((amp * math.sin(ph), samp * math.sin(2 * math.pi * tl / speriod - i * lag + 1.0)))
        solved = solve(skeleton, pose, dirn, offs)
        w = _weight(opts or {}, t)
        for b in links:
            src = orig[b] if orig[b] is not None else under[b]
            if src is not None:
                st = t if src is orig[b] else (t % base.length if base.loop == "loop" else min(t, base.length))
                a = src.sample(st)
            else:
                a = (0.0, 0.0, 0.0)
            v = tuple(a[c] + (solved[b][c] - a[c]) * w for c in range(3))
            tracks[b].put(Key(t, v))
    for b, tr in tracks.items():
        keys = tr.keys
        if clip.loop == "loop":
            keys[-1] = Key(keys[-1].t, keys[0].v)            # the loop closes exactly
        elif target is not None and _weight(opts or {}, clip.length) >= 1.0:
            keys[-1] = Key(keys[-1].t, target.tracks[(b, "rotation")].sample(0.0))
        out.tracks[(b, "rotation")] = simplify(Track(keys), TOL)
    out.meta = dict(out.meta, tail_trail=True)
    return out


def apply(built, hero_obj, spec, fallbacks):
    """The hero's own file (an anim.library.Built) with the clips `spec` names re-keyed; a clip it does not have is
    copied from the first of `fallbacks` (its style, the core) that has it and appended, in `spec`'s order."""
    import build as buildmod
    from anim.library import Built
    from anim.skeleton import Skeleton
    where = "%s: animations.tail_trail" % hero_obj["id"]
    check_spec(spec, where)
    skeleton = Skeleton.from_model(buildmod.assemble(hero_obj))
    if not skeleton.has(CHAIN[0]):
        raise HerogenError("%s: the model has no tail" % where)

    def lookup(name):
        if name in by_name:
            return by_name[name]
        for fb in fallbacks:
            if name in fb.by_name:
                return fb.by_name[name]
        return None

    by_name = dict(built.by_name)
    clips = list(built.clips)
    d = skeleton.d                                  # FORM_VAR of her body
    def order(name):
        c = lookup(name)
        return (0 if c is not None and c.loop == "loop" else 1, list(spec["clips"]).index(name))

    for name in sorted(spec["clips"], key=order):
        opts = spec["clips"][name] or {}
        clip = lookup(name)
        if clip is None:
            raise HerogenError("%s: no clip %s in the hero's chain" % (where, name))
        base = lookup(clip.meta.get("over")) if clip.meta.get("kind") == "action" and clip.meta.get("over") else None
        settle = clip.meta.get("settle_to")
        target = by_name.get(settle) if settle in spec["clips"] and clip.loop != "loop" else None
        new = rekey(clip, base, skeleton, spec, opts, d, target)
        if name in built.by_name:
            clips[[c.name for c in clips].index(name)] = new
        else:
            clips.append(new)
        by_name[name] = new
    return Built(built.file, clips, built.source_hash)
