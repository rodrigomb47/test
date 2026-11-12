"""Recipe -> clip (HEROES_V2.md 13.6 "Recipe").

A recipe:
  length      seconds
  loop        true (loop), false (play once, the default), "hold" (hold on the last frame)
  kind        "base" (the base controller: idles, locomotion) or "action" (the action controller: attacks, moves,
              work, emotes); default base for loops, action otherwise
  class       light, heavy, area, ultimate: the anticipation and recovery minimums the hit markers must meet (6.4)
  settle_to   the clip an action must end on (every channel it keys ends on that clip's first key): default
              "combat.idle" in a style library, "move.idle" elsewhere
  secondary   true (default), false, or a list of group names of herosrc/anim/secondary.json to bake
  planted     the feet must not move (default: true unless a layer is a gait); ground: a sole must touch y 0
  poses       extra poses for this recipe ({name: pose spec}, poses.py)
  keys        [{t, pose, ease, root, face, marker, sound, particle, overshoot, overshoot_time, settle, settle_time}]
  layers      [{primitive, params, add}]: procedural motion over the whole clip, added to the pose keys
              (add true, the default) or replacing the channels it keys (add false)
  markers     [{t, marker, sound, particle}] for markers between pose keys
  bones       globs: the bones the pose keys and layers may key (an attack that keys only the upper body leaves
              the legs to the base controller, so a hero can swing while it walks); the bake and face_ctrl are not
              filtered
Pose keys: every bone any key's pose mentions is keyed at every pose key (0 where a pose leaves it out), so a key is
a whole pose; `ease` (default easeInOutSine) shapes the motion that arrives at the key; `root` adds a root offset;
`overshoot` k pushes the key k x the last change past its pose and adds a return to the pose `overshoot_time`
(0.1 s) later; `settle` k adds a key `settle_time` (0.15 s) later that relaxes k x the last change back.
Face keys switch face_ctrl (eyes + 1, mouth + 1, cheeks + 1; 0 = no override, HEROES_V2 4.6) at once, written as
pre/post keys (see clip.py on why not `step`); `face` is a mood name, "none", or {eyes, mouth, cheeks}.
Markers: "hit;vfx:slash_arc" is split at ";": `sfx:<sound>` becomes a sound keyframe, the rest go to the timeline.
"""
import fnmatch

from anim import poses as P
from anim import primitives
from anim import secondary
from anim.clip import CHANNELS, TICK, Clip, Event, Key, Track, dense_track, on_tick, simplify
from anim.primitives import smoothstep
from face import CHEEK_FRAMES, EYE_FRAMES, MOOD_FRAMES, MOUTH_FRAMES
from util import HerogenError

KEY_FIELDS = ("t", "pose", "ease", "root", "face", "marker", "sound", "particle", "overshoot", "overshoot_time",
              "settle", "settle_time")
RECIPE_FIELDS = ("length", "loop", "kind", "class", "settle_to", "secondary", "planted", "ground", "poses", "keys",
                 "layers", "markers", "bones", "description")
DEFAULT_EASE = "easeInOutSine"
SETTLE_BLEND = 0.15
FACE_BONE = "face_ctrl"


def _loop_mode(v):
    if v is True or v == "loop":
        return "loop"
    if v in (False, None, "once"):
        return "once"
    if v == "hold":
        return "hold"
    raise HerogenError("loop must be true, false or \"hold\", not %r" % (v,))


def face_value(face, where):
    """face_ctrl position for a face key."""
    if face in (None, "none"):
        return (0.0, 0.0, 0.0)
    if isinstance(face, str):
        if face not in MOOD_FRAMES:
            raise HerogenError("%s: face %r is not a mood (%s)" % (where, face, ", ".join(sorted(MOOD_FRAMES))))
        e, m, c = MOOD_FRAMES[face]
        return (e + 1.0, m + 1.0, c + 1.0)
    out = []
    for plane, frames in (("eyes", EYE_FRAMES), ("mouth", MOUTH_FRAMES), ("cheeks", CHEEK_FRAMES)):
        v = face.get(plane)
        if v is None:
            out.append(0.0)
            continue
        if not isinstance(v, int) or not 0 <= v < len(frames):
            raise HerogenError("%s: face %s frame %r is out of range 0-%d" % (where, plane, v, len(frames) - 1))
        out.append(v + 1.0)
    return tuple(out)


def split_marker(text):
    instr, sounds = [], []
    for part in str(text).split(";"):
        part = part.strip()
        if not part:
            continue
        if part.startswith("sfx:"):
            sounds.append(part[4:])
        else:
            instr.append(part)
    return instr, sounds


class Expander:
    """Turns recipes into clips. `lookup(name)` returns an already built clip (for settle_to)."""

    def __init__(self, pose_lib, skeleton, lookup=None, default_settle="move.idle", forms=None):
        self.poses = pose_lib
        self.skeleton = skeleton
        self.forms = list(forms or [skeleton])
        self.lookup = lookup or (lambda name: None)
        self.default_settle = default_settle

    def expand(self, name, recipe, where=None):
        where = where or "animation %s" % name
        for k in recipe:
            if k not in RECIPE_FIELDS:
                raise HerogenError("%s: unknown field %r" % (where, k))
        length = float(recipe.get("length", 0))
        if length <= 0 or not on_tick(length):
            raise HerogenError("%s: length %r must be a positive whole number of ticks (0.05 s)" % (where, length))
        loop = _loop_mode(recipe.get("loop"))
        kind = recipe.get("kind", "base" if loop == "loop" else "action")
        if kind not in ("base", "action"):
            raise HerogenError("%s: kind must be base or action" % where)
        clip = Clip(name, length, loop, {"kind": kind})
        if recipe.get("class"):
            clip.meta["class"] = recipe["class"]
        lib = self.poses.with_specs(recipe.get("poses"), where) if recipe.get("poses") else self.poses
        self._pose_keys(clip, recipe.get("keys", []), lib, where)
        gait = False
        for i, layer in enumerate(recipe.get("layers", [])):
            gait |= layer.get("primitive") in ("walk_cycle", "run_cycle")
            self._layer(clip, layer, "%s layers[%d]" % (where, i))
        for i, m in enumerate(recipe.get("markers", [])):
            self._markers(clip, m, "%s markers[%d]" % (where, i))
        if recipe.get("bones"):
            globs = list(recipe["bones"]) + [FACE_BONE]
            for bc in list(clip.tracks):
                if not any(fnmatch.fnmatchcase(bc[0], g) for g in globs):
                    del clip.tracks[bc]
        clip.meta["planted"] = bool(recipe.get("planted", not gait))
        clip.meta["ground"] = bool(recipe.get("ground", kind == "base"))
        clip.meta["steps"] = gait
        base = None
        if kind == "action":
            clip.meta["settle_to"] = recipe.get("settle_to", self.default_settle)
            base = self.lookup(clip.meta["settle_to"])
        chain = set(secondary.chain_bones())
        clip.meta["authored"] = {bc: tr.copy() for bc, tr in clip.tracks.items() if bc[0] in chain}
        sec = recipe.get("secondary", True)
        if sec:
            groups = secondary.load_groups(self.skeleton)
            if isinstance(sec, list):
                groups = [g for g in groups if g.name in sec]
            movers = None
            if kind == "action":
                movers = {b for b, _ in clip.tracks if b != FACE_BONE}
            secondary.bake(clip, self.skeleton, groups, base=base, movers=movers)
        if kind == "action":
            self._settle(clip, base, where)
        self._finish(clip, where)
        return clip

    # -------------------------------------------------------------- pose keys

    def _pose_keys(self, clip, keys, lib, where):
        if not keys:
            return
        resolved = []
        for i, k in enumerate(sorted(keys, key=lambda k: float(k.get("t", 0)))):
            w = "%s keys[%d]" % (where, i)
            for f in k:
                if f not in KEY_FIELDS:
                    raise HerogenError("%s: unknown field %r" % (w, f))
            t = float(k.get("t", 0))
            if t < 0 or t > clip.length + 1e-9:
                raise HerogenError("%s: t %.3f is outside the clip (0-%.3f)" % (w, t, clip.length))
            chans = P.resolve(lib.get(k["pose"]), self.forms) if k.get("pose") else {}
            if k.get("root"):
                r = list(chans.get("root", {}).get("position", (0.0, 0.0, 0.0)))
                for c in range(3):
                    r[c] += float(k["root"][c])
                chans.setdefault("root", {})["position"] = tuple(r)
            resolved.append((t, k, chans, w))
        animated = sorted({(b, ch) for _, _, chans, _ in resolved if chans for b, cs in chans.items() for ch in cs})
        prev = {}
        for t, k, chans, w in resolved:
            if k.get("pose") or k.get("root"):
                ez = k.get("ease", DEFAULT_EASE)
                over = k.get("overshoot")
                over = 0.15 if over is True else float(over or 0)
                settle = k.get("settle")
                settle = 0.1 if settle is True else float(settle or 0)
                for bc in animated:
                    v = chans.get(bc[0], {}).get(bc[1], (0.0, 0.0, 0.0))
                    tr = clip.track(*bc)
                    p = prev.get(bc, v)
                    if over:
                        pushed = tuple(v[c] + over * (v[c] - p[c]) for c in range(3))
                        tr.set(t, pushed, ez)
                        tr.set(t + float(k.get("overshoot_time", 0.1)), v, DEFAULT_EASE)
                    else:
                        tr.set(t, v, ez)
                    if settle:
                        back = tuple(v[c] - settle * (v[c] - p[c]) for c in range(3))
                        tr.set(t + float(k.get("settle_time", 0.15)), back, DEFAULT_EASE)
                    prev[bc] = v
            if "face" in k:
                self._face(clip, t, k["face"], w)
            self._markers(clip, k, w)
        for tr in clip.tracks.values():
            if tr.end > clip.length + 1e-9:
                raise HerogenError("%s: an overshoot or settle key falls after the clip's end" % where)

    def _face(self, clip, t, face, where):
        v = face_value(face, where)
        tr = clip.track(FACE_BONE, "position")
        if not tr.keys:
            tr.set(0.0, (0.0, 0.0, 0.0))
        old = tr.sample(t)
        if t <= 0:
            tr.set(0.0, v)
        else:
            tr.put(Key(t, v, "linear", pre=old))

    def _markers(self, clip, k, where):
        t = float(k.get("t", 0))
        if any(f in k for f in ("marker", "sound", "particle")) and not on_tick(t):
            raise HerogenError("%s: a marker at %.3f s is not on a whole tick (0.05 s)" % (where, t))
        if k.get("marker"):
            instr, sounds = split_marker(k["marker"])
            for i in instr:
                clip.events.append(Event(t, "instruction", i))
            for s in sounds:
                clip.events.append(Event(t, "sound", s))
        if k.get("sound"):
            clip.events.append(Event(t, "sound", k["sound"]))
        if k.get("particle"):
            p = k["particle"]
            clip.events.append(Event(t, "particle", p["effect"], p.get("locator")))

    # -------------------------------------------------------------- layers

    def _layer(self, clip, layer, where):
        ctx = primitives.Ctx(clip.length, self.skeleton, seed=clip.name, loop=clip.loop == "loop", forms=self.forms)
        part = primitives.run(layer["primitive"], ctx, layer.get("params"))
        add = layer.get("add", True)
        for bc, tr in part.tracks.items():
            if not add or bc not in clip.tracks:
                clip.tracks[bc] = tr.copy()
                continue
            base = clip.tracks[bc]
            times = sorted({round(i * TICK, 6) for i in range(int(round(clip.length / TICK)) + 1)}
                           | {round(k.t, 6) for k in base.keys} | {round(k.t, 6) for k in tr.keys})
            out = Track()
            for t in times:
                a, b = base.sample(t), tr.sample(t)
                out.set(t, tuple(a[c] + b[c] for c in range(3)))
            clip.tracks[bc] = simplify(out, 0.05 if bc[1] == "rotation" else 0.005)
        for k, v in part.meta.items():
            clip.meta.setdefault(k, v)

    # -------------------------------------------------------------- settle and finish

    def _settle(self, clip, target, where):
        """End every channel the action keys on the settle clip's first key (or the rest pose where it keys none),
        easing out any difference over the last 0.15 s."""
        L = clip.length
        for bc, tr in sorted(clip.tracks.items()):
            goal = target.tracks[bc].sample(0.0) if target is not None and bc in target.tracks else (0.0, 0.0, 0.0)
            end = tr.sample(L)
            diff = tuple(goal[c] - end[c] for c in range(3))
            if max(abs(d) for d in diff) < 1e-4 and abs(tr.end - L) < 1e-9:
                continue
            if bc[0] == FACE_BONE:
                if max(abs(d) for d in diff) >= 1e-4:
                    tr.put(Key(L, goal, "linear", pre=end))
                else:
                    tr.set(L, goal)
                continue
            if max(abs(d) for d in diff) < 1e-4:
                tr.set(L, goal)
                continue
            # blend from the last key at least 0.15 s before the end, so no eased segment is cut in two
            before = [k.t for k in tr.keys if k.t <= L - SETTLE_BLEND + 1e-9]
            t0 = before[-1] if before else 0.0
            fixed = Track(k for k in tr.keys if k.t <= t0 + 1e-9)
            if not fixed.keys:
                fixed.set(0.0, tr.sample(0.0))
            for k in dense_track(lambda t: (0.0, 0.0, 0.0), t0, L).keys[1:]:
                w = smoothstep((k.t - t0) / (L - t0))
                base = tr.sample(k.t)
                fixed.set(k.t, tuple(base[c] + diff[c] * w for c in range(3)))
            clip.tracks[bc] = simplify(fixed, 0.05) if fixed.is_linear() else fixed
        clip.meta["settled"] = target.name if target is not None else "rest"

    def _finish(self, clip, where):
        L = clip.length
        for bc, tr in list(clip.tracks.items()):
            if bc[1] not in CHANNELS:
                raise HerogenError("%s: channel %s" % (where, bc[1]))
            if not tr.keys:
                del clip.tracks[bc]
                continue
            if tr.start > 1e-9:
                tr.put(Key(0.0, tr.keys[0].v))
            if clip.loop == "loop" and abs(tr.end - L) > 1e-9:
                tr.put(Key(L, tr.sample(0.0)))
            if clip.loop == "loop" and max(abs(a - b) for a, b in zip(tr.keys[0].v, tr.keys[-1].v)) < 1e-6:
                tr.keys[-1].v = tr.keys[0].v           # rounding noise only: close the loop exactly
            for k in tr.keys:
                k.t = round(k.t, 4)
        clip.events.sort(key=lambda e: (e.t, e.kind, e.value))
        for e in clip.events:
            if e.t > L - TICK + 1e-9:
                raise HerogenError("%s: a marker at %.3f s is not before the clip's last tick: GeckoLib ends the "
                                   "clip on that frame before it fires the frame's events" % (where, e.t))
