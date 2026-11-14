"""Animation quality rules (HEROES_V2.md 13.6 "Quality rules", 14.2), run by `herogen check` and the tests.

Per clip:
  keys        every channel has a key at 0 and none after the end; values are finite; easings are GeckoLib names
  loop        a loop's channels end where they start (first key = last key)
  markers     on whole ticks, before the clip's last tick (GeckoLib never fires an event on the frame a clip ends)
  class       the first hit or cast comes at least the class's minimum wind-up after the action's start, and the
              recovery after the last one is at least the class's minimum (6.4: light 0.4 / 0.3, heavy 0.8 / 0.8, area
              1.0 / 0.8, ultimate 1.5 / 1.5). C1: the wind-up counts from the action's start, the controller's blend
              included, as the server's frames and the kit validator count it (writer.frames_of, HEROES_V2 4.8); it
              counted clip time before, 2 ticks stricter than the kit, so a kit at the class minimum (Natsuho's pounce,
              0.8 s) could not be matched within one tick
  face        face_ctrl values are whole frame numbers in range (eyes 0-12, mouth 0-8, cheeks 0-4), and every change
              is a jump (pre/post), never a slide through other frames
  joints      the rig's joint limits (elbow and knee flex one way 0-150, head yaw 80 and pitch 60, neck yaw 60 and
              pitch 40, waist twist 45), sampled every tick
  settle      an action ends, on every channel it keys, on the first key of the clip it settles on (the style's
              combat.idle or the core's move.idle) or on the rest pose where that clip keys nothing: GeckoLib snaps,
              not blends, when the action controller stops (4.8)
  start       an action that starts on that clip's pose on every channel it authors starts on it on its baked
              chain bones too (a bake that counted the base's follow-through twice pops a sleeve at the start)
  feet        on all six bases: a planted clip (idles, stances, actions over them) keeps every sole corner within
              0.5 px of where it starts; a grounded clip keeps its lowest sole within 0.6 px of the ground; a gait
              never sinks a sole more than 0.6 px below it. G4: a clip with `contact: body` takes the lowest point of
              the base body (BODY_CONTACT: all but the head) instead of the soles (a kneel, a sit, lying down) and
              never sinks it, and a stepping clip (`steps`)
              may lift and move a foot but never slides one along the ground (check_slide: a sole corner on the
              ground stays within 0.5 px of where it touched down until it lifts). Form-scaled keys are evaluated
              with each base's FORM_VAR (anim/clip.py), as HeroModel sets it
  root        no root motion: the root's x and z offsets stay within 1.5 px (movement is physical on the server)
  bones       keyed bones are rig bones (or, in a hero's own file, bones its parts declare)
Per file: a hero's own file has at most the rig's `own_animations` (24) animations.
Across files: the server timing file says exactly what the animation file's markers say (check_timing).
"""
import math

from anim import skeleton as S
from anim.clip import FORM_SPAN, TICK, layered, on_tick
from anim.recipes import FACE_BONE
from anim.writer import TRANSITION_TICKS
from face import CHEEK_FRAMES, EYE_FRAMES, MOUTH_FRAMES
from rig import BASE_IDS, load_rig

CLASS_MIN = {"light": (0.4, 0.3), "heavy": (0.8, 0.8), "area": (1.0, 0.8), "ultimate": (1.5, 1.5)}
FEET_DRIFT = 0.5
GROUND_TOL = 0.6
CONTACT = 0.3               # a sole corner this close to the ground is on it (check_slide)
# the base body cubes of the body contact rule: every one but the head, which is 10 px deep on every form while a
# chibi's torso is 3.5, so a hero lying on its back rests its head into the pillow (or the grass) by up to 2 px
BODY_CONTACT = ["body", "waist", "chest", "neck", "arm_*", "forearm_*", "hand_*", "leg_*", "shin_*", "foot_*"]
SLIDE = 0.5                 # how far a corner on the ground may move before it lifts
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
            for d in ((0.0, -FORM_SPAN, FORM_SPAN) if tr.scaled() else (0.0,)):
                a, b = tr.sample(0.0, d), tr.sample(clip.length, d)
                if max(abs(a[c] - b[c]) for c in range(3)) > LOOP_TOL:
                    out.append("%s: the loop does not close (%s at 0, %s at the end)" % (name, _v(a), _v(b)))
                    break
    return out


def _v(v):
    return "[%s]" % ", ".join("%.2f" % x for x in v)


def check_markers(clip):
    out = []
    for e in clip.events:
        if not on_tick(e.t):
            out.append("%s: marker %s at %.3f s is not on a whole tick" % (clip.name, e.value, e.t))
        if e.t > clip.length - TICK + 1e-9:
            # GeckoLib ends (or restarts) a clip on the frame it reaches its length before it fires that frame's
            # events, so a marker on the last tick never fires
            out.append("%s: marker %s at %.3f s is not before the last tick (it would never fire)" % (
                clip.name, e.value, e.t))
    hits = clip.hits()
    cls = clip.meta.get("class")
    if hits and cls:
        if cls not in CLASS_MIN:
            out.append("%s: class %r is unknown" % (clip.name, cls))
        else:
            wmin, rmin = CLASS_MIN[cls]
            blend = TRANSITION_TICKS[clip.meta.get("kind", "base")] * TICK
            if hits[0] + blend < wmin - 1e-9:
                out.append("%s: the first hit at %.2f s (%.2f s from the action's start, the blend included) is "
                           "shorter than the %s wind-up minimum %.2f s" % (clip.name, hits[0], hits[0] + blend, cls,
                                                                         wmin))
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
    if clip.loop != "once" or (clip.meta.get("kind") != "action" and not clip.meta.get("settle_to")):
        return []      # an action loop plays until the server stops it (4.8) and closes; a held clip (death) stays;
        # a base one-shot with settle_to (G10: a jump, a landing) ends on the loop the base controller queues next
    out = []
    for bc, tr in sorted(clip.tracks.items()):
        goal = target.tracks[bc].sample(0.0) if target is not None and bc in target.tracks else (0.0, 0.0, 0.0)
        end = tr.keys[-1]
        if abs(end.t - clip.length) > 1e-6:
            out.append("%s: %s.%s has no key at the end" % (clip.name, bc[0], bc[1]))
        elif max(abs(end.v[c] - goal[c]) for c in range(3)) > SETTLE_TOL:
            out.append("%s: %s.%s ends on %s, not on %s's first key %s" % (
                clip.name, bc[0], bc[1], _v(end.v), target.name if target else "the rest pose", _v(goal)))
        else:
            gk = target.tracks[bc].sample_k(0.0) if target is not None and bc in target.tracks else (0.0,) * 3
            ek = tr.sample_k(clip.length)
            if max(abs(ek[c] - gk[c]) for c in range(3)) * FORM_SPAN > SETTLE_TOL:
                out.append("%s: %s.%s ends on another form scaling than %s's first key" % (
                    clip.name, bc[0], bc[1], target.name if target else "the rest pose"))
    return out


def check_start(clip, target):
    """An action that starts on its settle clip's pose (every channel it authors within 0.5 degrees or 0.05 px of
    that clip's first key) starts there on its baked chain bones too: a follow-through bake that counted the base's
    own bake again would pop a sleeve or a ponytail through the 2-tick blend into the action."""
    if clip.meta.get("kind") != "action" or target is None:
        return []
    chain = set(secondary_chain_bones())

    def first(tr):
        return tr.sample(0.0)

    def goal(bc):
        return first(target.tracks[bc]) if bc in target.tracks else (0.0, 0.0, 0.0)

    def off(bc, tr):
        g = goal(bc)
        return max(abs(a - b) for a, b in zip(first(tr), g))

    authored = [(bc, tr) for bc, tr in clip.tracks.items() if bc[0] not in chain and bc[0] != FACE_BONE]
    if any(off(bc, tr) > (0.5 if bc[1] == "rotation" else 0.05) for bc, tr in authored):
        return []
    return ["%s: %s.%s starts %.1f degrees off %s's first key, though every authored channel starts on it" % (
        clip.name, bc[0], bc[1], off(bc, tr), target.name)
        for bc, tr in sorted(clip.tracks.items()) if bc[0] in chain and off(bc, tr) > 1.0]


def secondary_chain_bones():
    from anim.secondary import chain_bones
    return chain_bones()


def _pose_fn(clip, base, d=0.0):
    if base is None or clip.meta.get("kind") != "action":
        return lambda t: clip.sample(t, d)
    return lambda t: layered(base, clip, t, t % base.length if base.loop == "loop" else min(t, base.length), d=d)


def check_feet(clip, base=None, bases=BASE_IDS):
    """Planted, grounded, gait and stepping rules on every base."""
    out = []
    planted = clip.meta.get("planted", False)
    ground = clip.meta.get("ground", False)
    steps = clip.meta.get("steps", False)
    stepping = clip.meta.get("stepping", False)
    body = clip.meta.get("contact") == "body"
    if not (planted or ground or steps or stepping or body):
        return out
    times = _ticks(clip)
    shared = None
    for base_id in bases:
        sk = S.for_base(base_id)
        if clip.scaled() or (base is not None and base.scaled()) or shared is None:
            pose_at = _pose_fn(clip, base, sk.d)
            poses = [pose_at(t) for t in times]
            if not (clip.scaled() or (base is not None and base.scaled())):
                shared = poses
        else:
            poses = shared
        start = None
        worst_drift = worst_low = worst_high = 0.0
        at = {}
        soles_by_tick = []
        for t, pose in zip(times, poses):
            soles = sk.soles(pose)
            soles_by_tick.append(soles)
            pts = [p for s in ("r", "l") for p in soles[s]]
            low = min(float(p[1]) for p in pts)
            if body:
                low = min(low, sk.lowest_body(pose, BODY_CONTACT)[0])
            if start is None:
                start = pts
            drift = max(math.dist(a, b) for a, b in zip(pts, start))
            if drift > worst_drift:
                worst_drift, at["drift"] = drift, t
            if low < worst_low:
                worst_low, at["low"] = low, t
            if low > worst_high:
                worst_high, at["high"] = low, t
        what = "the body" if body else "a sole"
        if planted and not steps and worst_drift > FEET_DRIFT:
            out.append("%s on %s: a foot moves %.2f px (at %.2f s); planted feet may move %.1f" % (
                clip.name, base_id, worst_drift, at["drift"], FEET_DRIFT))
        if (ground or steps or stepping or body) and worst_low < -GROUND_TOL:
            out.append("%s on %s: %s sinks %.2f px below the ground (at %.2f s)" % (
                clip.name, base_id, what, -worst_low, at["low"]))
        if ground and worst_high > GROUND_TOL:
            out.append("%s on %s: %s floats %.2f px above the ground (at %.2f s)" % (
                clip.name, base_id, "the body" if body else "both feet", worst_high, at["high"]))
        if stepping:
            out += check_slide(clip.name, base_id, times, soles_by_tick)
    return out


def check_slide(name, base_id, times, soles_by_tick):
    """A sole corner on the ground (within CONTACT px) stays within SLIDE px (horizontally) of where it touched down
    until it lifts: a foot may pivot on a corner and step, never skate."""
    for side in ("r", "l"):
        for i in range(4):
            anchor = None
            for t, soles in zip(times, soles_by_tick):
                p = soles[side][i]
                if p[1] > CONTACT:
                    anchor = None
                    continue
                if anchor is None:
                    anchor = p
                    continue
                if math.hypot(p[0] - anchor[0], p[2] - anchor[2]) > SLIDE:
                    return ["%s on %s: the %s foot slides on the ground at %.2f s (%.2f px)" % (
                        name, base_id, "right" if side == "r" else "left", t,
                        math.hypot(p[0] - anchor[0], p[2] - anchor[2]))]
    return []


MOUNT_TOL = 0.6


def check_mount(clip, base=None, bases=BASE_IDS):
    """G10, a riding clip (`contact: mount`): on every base the lowest point of the seat bones (the thighs, by
    default) rests on the `mount` bone's pivot, the carrier's seat, within MOUNT_TOL px every tick (the mount is a child
    of the root, so a bob carries both). MOBILITY_AND_WORK 4.1: the riding poses keep the rider on the mount locator."""
    out = []
    seat = clip.meta.get("seat") or ["leg_*"]
    for base_id in bases:
        sk = S.for_base(base_id)
        if not sk.has("mount"):
            continue
        pose_at = _pose_fn(clip, base, sk.d)
        worst, at = 0.0, 0.0
        for t in _ticks(clip):
            pose = pose_at(t)
            w = sk.world(pose, only=["mount"])
            mount_y = float(sk.apply(w, "mount", sk.pivot["mount"])[1])
            low, _ = sk.lowest_body(pose, seat)
            if abs(low - mount_y) > abs(worst):
                worst, at = low - mount_y, t
        if abs(worst) > MOUNT_TOL:
            out.append("%s on %s: the seat %s %.2f px %s the mount (at %.2f s; at most %.1f)" % (
                clip.name, base_id, "+".join(seat), abs(worst), "above" if worst > 0 else "into", at, MOUNT_TOL))
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


def check_clip(clip, settle_target=None, known_bones=None, feet=True, over=None):
    """`settle_target`: the clip an action ends on; `over`: the base clip it plays over (default the same; they
    differ for a one-shot that leads into its .loop)."""
    over = over if over is not None else settle_target
    out = check_keys(clip) + check_markers(clip) + check_face(clip) + check_joints(clip) + check_root(clip)
    if clip.meta.get("kind") == "action":
        out += check_settle(clip, settle_target) + check_start(clip, over)
    elif clip.meta.get("settle_to"):
        out += check_settle(clip, settle_target)
    if clip.meta.get("contact") == "mount":
        out += check_mount(clip, over if clip.meta.get("kind") == "action" else None)
    if known_bones is not None:
        out += check_bones(clip, known_bones)
    if feet:
        out += check_feet(clip, over)
    return out


def rig_bone_names(rig=None):
    rig = rig or load_rig()
    return {b["name"] for b in rig["bones"]}


def check_built(built, lookup, extra_bones=(), own_file=False, feet=True):
    """Problems of one animation file (anim.library.Built). lookup(name) resolves settle targets."""
    out = []
    known = rig_bone_names() | set(extra_bones)
    for clip in built.clips:
        target = over = None
        if clip.meta.get("kind") == "action":
            target = lookup(clip.meta["settle_to"])
            over = lookup(clip.meta.get("over", clip.meta["settle_to"]))
        elif clip.meta.get("settle_to"):
            target = lookup(clip.meta["settle_to"])
            if target is None:
                out.append("%s: settle_to %s is not in the chain" % (clip.name, clip.meta["settle_to"]))
        out += check_clip(clip, target, known, feet, over=over)
    if own_file:
        cap = load_rig()["budgets"].get("own_animations", 24)
        if len(built.clips) > cap:
            out.append("%s: %d animations in the hero's own file (at most %d)" % (built.file, len(built.clips), cap))
    return out


def check_timing(anim_json, timing_json):
    """The timing file agrees with the animation file's markers (the HeroAssetsTest rule, within one tick). Both
    count from the controller's start: the file's event times include its blend (writer.py)."""
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
        kind = t.get("kind", "base")
        blend = t.get("transition_ticks")
        if blend != TRANSITION_TICKS.get(kind):
            out.append("%s: transition_ticks %s, the %s controller blends %s ticks" % (
                name, blend, kind, TRANSITION_TICKS.get(kind)))
            continue
        if t.get("total_ticks") != t.get("ticks", -1) + blend:
            out.append("%s: total_ticks %s is not ticks + transition_ticks" % (name, t.get("total_ticks")))
        hits = sorted(ts for ts, m in marks if m in ("hit", "cast"))
        if hits:
            fr = t.get("frames")
            if not fr:
                out.append("%s: hit markers but no frames in the timing file" % name)
            elif abs(fr["windup"] - hits[0]) > TICK + 1e-9:
                out.append("%s: wind-up %.2f s in the timing file, first hit at %.2f s" % (name, fr["windup"],
                                                                                         hits[0]))
            elif abs(fr["windup"] + fr["active"] + fr["recovery"] - t["length"] - blend * TICK) > 1e-6:
                out.append("%s: frames do not add up to the blend and the length" % name)
        elif t.get("frames"):
            out.append("%s: frames in the timing file but no hit markers" % name)
    return out
