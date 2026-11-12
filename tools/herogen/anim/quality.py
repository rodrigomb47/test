"""Animation quality rules (HEROES_V2.md 13.6 "Quality rules", 14.2), run by `herogen check` and the tests.

Per clip:
  keys        every channel has a key at 0 and none after the end; values are finite; easings are GeckoLib names
  loop        a loop's channels end where they start (first key = last key)
  markers     on whole ticks, inside the clip; the clip is not shorter than its last marker
  class       the first hit or cast comes at least the class's minimum wind-up after the start, and the recovery after
              the last one is at least the class's minimum (6.4: light 0.4 / 0.3, heavy 0.8 / 0.8, area 1.0 / 0.8,
              ultimate 1.5 / 1.5)
  face        face_ctrl values are whole frame numbers in range (eyes 0-12, mouth 0-8, cheeks 0-4), and every change
              is a jump (pre/post), never a slide through other frames
  joints      the rig's joint limits (elbow and knee flex one way 0-150, head yaw 80 and pitch 60, neck yaw 60 and
              pitch 40, waist twist 45), sampled every tick
  settle      an action ends, on every channel it keys, on the first key of the clip it settles on (the style's
              combat.idle or the core's move.idle) or on the rest pose where that clip keys nothing: GeckoLib snaps,
              not blends, when the action controller stops (4.8)
  feet        on all six bases: a planted clip (idles, stances, actions over them) keeps every sole corner within
              0.5 px of where it starts; a grounded clip keeps its lowest sole within 0.6 px of the ground; a gait
              never sinks a sole more than 0.6 px below it
  root        no root motion: the root's x and z offsets stay within 1.5 px (movement is physical on the server)
  bones       keyed bones are rig bones (or, in a hero's own file, bones its parts declare)
Per file: a hero's own file has at most the rig's `own_animations` (24) animations.
Across files: the server timing file says exactly what the animation file's markers say (check_timing).
"""
import math

from anim import skeleton as S
from anim.clip import TICK, layered, on_tick
from anim.recipes import FACE_BONE
from face import CHEEK_FRAMES, EYE_FRAMES, MOUTH_FRAMES
from rig import BASE_IDS, load_rig

CLASS_MIN = {"light": (0.4, 0.3), "heavy": (0.8, 0.8), "area": (1.0, 0.8), "ultimate": (1.5, 1.5)}
FEET_DRIFT = 0.5
GROUND_TOL = 0.6
ROOT_XZ = 1.5
LOOP_TOL = 0.01
SETTLE_TOL = 0.01


def _ticks(clip):
    n = int(round(clip.length / TICK))
    return [i * TICK for i in range(n + 1)]


def check_keys(clip):
    out = []
    for (bone, ch), tr in sorted(clip.tracks.items()):
        name = "%s %s.%s" % (clip.name, bone, ch)
        if not tr.keys:
            out.append("%s: no keys" % name)
            continue
        if tr.start > 1e-9:
            out.append("%s: the first key is at %.3f s, not 0" % (name, tr.start))
        if tr.end > clip.length + 1e-6:
            out.append("%s: a key at %.3f s is after the end (%.3f s)" % (name, tr.end, clip.length))
        for k in tr.keys:
            if not all(math.isfinite(x) for x in k.v + (k.pre or ())):
                out.append("%s: a key at %.3f s is not finite" % (name, k.t))
        if clip.loop == "loop":
            if abs(tr.end - clip.length) > 1e-6:
                out.append("%s: a loop channel without a key at the end" % name)
            a, b = tr.sample(0.0), tr.sample(clip.length)
            if max(abs(a[c] - b[c]) for c in range(3)) > LOOP_TOL:
                out.append("%s: the loop does not close (%s at 0, %s at the end)" % (name, _v(a), _v(b)))
    return out


def _v(v):
    return "[%s]" % ", ".join("%.2f" % x for x in v)


def check_markers(clip):
    out = []
    for e in clip.events:
        if not on_tick(e.t):
            out.append("%s: marker %s at %.3f s is not on a whole tick" % (clip.name, e.value, e.t))
        if e.t > clip.length + 1e-9:
            out.append("%s: marker %s at %.3f s is after the end" % (clip.name, e.value, e.t))
    hits = clip.hits()
    cls = clip.meta.get("class")
    if hits and cls:
        if cls not in CLASS_MIN:
            out.append("%s: class %r is unknown" % (clip.name, cls))
        else:
            wmin, rmin = CLASS_MIN[cls]
            if hits[0] < wmin - 1e-9:
                out.append("%s: the first hit at %.2f s is shorter than the %s wind-up minimum %.2f s" % (
                    clip.name, hits[0], cls, wmin))
            rec = clip.length - hits[-1] - TICK
            if rec < rmin - 1e-9:
                out.append("%s: the recovery %.2f s after the last hit is shorter than the %s minimum %.2f s" % (
                    clip.name, rec, cls, rmin))
    if hits and not cls:
        out.append("%s: has hit markers but no class" % clip.name)
    return out


def check_face(clip):
    tr = clip.tracks.get((FACE_BONE, "position"))
    if tr is None:
        return []
    out = []
    limits = (len(EYE_FRAMES), len(MOUTH_FRAMES), len(CHEEK_FRAMES))
    prev = None
    for k in tr.keys:
        for c in range(3):
            v = k.v[c]
            if abs(v - round(v)) > 1e-9 or not 0 <= round(v) <= limits[c]:
                out.append("%s: face_ctrl %s = %g at %.2f s is not a frame number (0 = none, 1-%d)" % (
                    clip.name, "xyz"[c], v, k.t, limits[c]))
        if k.ease != "linear":
            out.append("%s: face_ctrl key at %.2f s is eased (%s); frames must jump" % (clip.name, k.t, k.ease))
        if prev is not None and k.v != prev:
            if k.pre is None or max(abs(a - b) for a, b in zip(k.pre, prev)) > 1e-9:
                out.append("%s: face_ctrl slides from %s to %s before %.2f s (write the change as a jump)" % (
                    clip.name, _v(prev), _v(k.v), k.t))
        prev = k.v
    return out


def joint_limits(rig=None):
    rig = rig or load_rig()
    return {k: v for k, v in rig["joint_limits"].items() if not k.startswith("_")}


def check_joints(clip, rig=None):
    out = []
    limits = joint_limits(rig)
    axes = {"x": 0, "y": 1, "z": 2}
    for bone, lim in sorted(limits.items()):
        tr = clip.tracks.get((bone, "rotation"))
        if tr is None:
            continue
        checks = []
        if "flex" in lim:
            checks.append(("flex", axes[lim.get("axis", "x")], float(lim.get("sign", 1)), lim["flex"]))
        for name, axis in (("yaw", 1), ("pitch", 0), ("twist", 1)):
            if name in lim:
                checks.append((name, axis, 1.0, lim[name]))
        for t in _ticks(clip):
            v = tr.sample(t)
            for name, axis, sign, (lo, hi) in checks:
                x = sign * v[axis]
                if x < lo - 0.05 or x > hi + 0.05:
                    out.append("%s: %s %s %.1f at %.2f s is outside %g..%g" % (clip.name, bone, name, x, t, lo, hi))
                    break
            else:
                continue
            break
    return out


def check_settle(clip, target):
    if clip.meta.get("kind") != "action":
        return []
    out = []
    for bc, tr in sorted(clip.tracks.items()):
        goal = target.tracks[bc].sample(0.0) if target is not None and bc in target.tracks else (0.0, 0.0, 0.0)
        end = tr.keys[-1]
        if abs(end.t - clip.length) > 1e-6:
            out.append("%s: %s.%s has no key at the end" % (clip.name, bc[0], bc[1]))
        elif max(abs(end.v[c] - goal[c]) for c in range(3)) > SETTLE_TOL:
            out.append("%s: %s.%s ends on %s, not on %s's first key %s" % (
                clip.name, bc[0], bc[1], _v(end.v), target.name if target else "the rest pose", _v(goal)))
    return out


def _pose_fn(clip, base):
    if base is None or clip.meta.get("kind") != "action":
        return clip.sample
    return lambda t: layered(base, clip, t, t % base.length if base.loop == "loop" else min(t, base.length))


def check_feet(clip, base=None, bases=BASE_IDS):
    """Planted, grounded and gait rules on every base."""
    out = []
    planted = clip.meta.get("planted", False)
    ground = clip.meta.get("ground", False)
    steps = clip.meta.get("steps", False)
    if not (planted or ground or steps):
        return out
    pose_at = _pose_fn(clip, base)
    times = _ticks(clip)
    poses = [pose_at(t) for t in times]
    for base_id in bases:
        sk = S.for_base(base_id)
        start = None
        worst_drift = worst_low = worst_high = 0.0
        at = {}
        for t, pose in zip(times, poses):
            soles = sk.soles(pose)
            pts = [p for s in ("r", "l") for p in soles[s]]
            low = min(float(p[1]) for p in pts)
            if start is None:
                start = pts
            drift = max(math.dist(a, b) for a, b in zip(pts, start))
            if drift > worst_drift:
                worst_drift, at["drift"] = drift, t
            if low < worst_low:
                worst_low, at["low"] = low, t
            if low > worst_high:
                worst_high, at["high"] = low, t
        if planted and not steps and worst_drift > FEET_DRIFT:
            out.append("%s on %s: a foot moves %.2f px (at %.2f s); planted feet may move %.1f" % (
                clip.name, base_id, worst_drift, at["drift"], FEET_DRIFT))
        if (ground or steps) and worst_low < -GROUND_TOL:
            out.append("%s on %s: a sole sinks %.2f px below the ground (at %.2f s)" % (
                clip.name, base_id, -worst_low, at["low"]))
        if ground and worst_high > GROUND_TOL:
            out.append("%s on %s: both feet float %.2f px above the ground (at %.2f s)" % (
                clip.name, base_id, worst_high, at["high"]))
    return out


def check_root(clip):
    tr = clip.tracks.get(("root", "position"))
    if tr is None or clip.meta.get("moves"):
        return []
    for t in _ticks(clip):
        v = tr.sample(t)
        if abs(v[0]) > ROOT_XZ or abs(v[2]) > ROOT_XZ:
            return ["%s: root moves %s at %.2f s (root motion beyond %.1f px; movement is physical)" % (
                clip.name, _v(v), t, ROOT_XZ)]
    return []


def check_bones(clip, known):
    bad = sorted(b for b in clip.bones() if b not in known)
    return ["%s: keys unknown bones %s" % (clip.name, ", ".join(bad))] if bad else []


def check_clip(clip, settle_target=None, known_bones=None, feet=True):
    out = check_keys(clip) + check_markers(clip) + check_face(clip) + check_joints(clip) + check_root(clip)
    if clip.meta.get("kind") == "action":
        out += check_settle(clip, settle_target)
    if known_bones is not None:
        out += check_bones(clip, known_bones)
    if feet:
        out += check_feet(clip, settle_target)
    return out


def rig_bone_names(rig=None):
    rig = rig or load_rig()
    return {b["name"] for b in rig["bones"]}


def check_built(built, lookup, extra_bones=(), own_file=False, feet=True):
    """Problems of one animation file (anim.library.Built). lookup(name) resolves settle targets."""
    out = []
    known = rig_bone_names() | set(extra_bones)
    for clip in built.clips:
        target = lookup(clip.meta["settle_to"]) if clip.meta.get("kind") == "action" else None
        out += check_clip(clip, target, known, feet)
    if own_file:
        cap = load_rig()["budgets"].get("own_animations", 24)
        if len(built.clips) > cap:
            out.append("%s: %d animations in the hero's own file (at most %d)" % (built.file, len(built.clips), cap))
    return out


def check_timing(anim_json, timing_json):
    """The timing file agrees with the animation file's markers (the HeroAssetsTest rule, within one tick)."""
    out = []
    anims = anim_json.get("animations", {})
    timed = timing_json.get("animations", {})
    if sorted(anims) != sorted(timed):
        out.append("timing file lists %s, the animation file %s" % (sorted(timed), sorted(anims)))
    for name, a in sorted(anims.items()):
        t = timed.get(name)
        if t is None:
            continue
        if abs(float(a.get("animation_length", 0)) - t["length"]) > TICK / 2:
            out.append("%s: length %s in the animation, %s in the timing file" % (name, a.get("animation_length"),
                                                                                  t["length"]))
        marks = []
        for ts, instr in a.get("timeline", {}).items():
            for part in str(instr).split(";"):
                if part:
                    marks.append((round(float(ts), 4), part))
        for ts, snd in a.get("sound_effects", {}).items():
            marks.append((round(float(ts), 4), "sfx:" + snd["effect"]))
        for ts, p in a.get("particle_effects", {}).items():
            marks.append((round(float(ts), 4), "particle:" + p["effect"]))
        timed_marks = []
        for e in t.get("events", []):
            label = e["type"] if not e.get("value") else "%s:%s" % (e["type"], e["value"])
            timed_marks.append((round(e["t"], 4), label))
            if abs(e["tick"] * TICK - e["t"]) > 1e-6:
                out.append("%s: event %s tick %d does not match its time %.3f" % (name, label, e["tick"], e["t"]))
        if sorted(marks) != sorted(timed_marks):
            out.append("%s: markers %s in the animation, %s in the timing file" % (name, sorted(marks),
                                                                                   sorted(timed_marks)))
        hits = sorted(ts for ts, m in marks if m in ("hit", "cast"))
        if hits:
            fr = t.get("frames")
            if not fr:
                out.append("%s: hit markers but no frames in the timing file" % name)
            elif abs(fr["windup"] - hits[0]) > TICK + 1e-9:
                out.append("%s: wind-up %.2f s in the timing file, first hit at %.2f s" % (name, fr["windup"],
                                                                                         hits[0]))
            elif abs(fr["windup"] + fr["active"] + fr["recovery"] - t["length"]) > 1e-6:
                out.append("%s: frames do not add up to the length" % name)
        elif t.get("frames"):
            out.append("%s: frames in the timing file but no hit markers" % name)
    return out
