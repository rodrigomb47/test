"""Animation files read back the way GeckoLib 4.9.3 reads them, and HeroModel's two controllers emulated, so a
preview shows what the written files make the game show (HEROES_V2.md 4.4, 4.5, 13.8).

Reading (BakedAnimationsAdapter and KeyFramesAdapter; the same rules as anim/clip.py and the port in
tests/test_anim.py): `loop` true loops, "hold_on_last_frame" holds, anything else plays once; a key is a vector, a
{"vector", "easing"} object or a Bedrock {"pre", "post"} jump (pre 0.001 s before the key); keyframe maps are read in
file order. Timeline, sound and particle keys become events at their FILE time, which herogen writes as the time
since the controller started the animation (its blend included, anim/writer.py).

Playing (Playback): the base controller loops its animation from time 0 (it is taken to have been running, so no
blend in); an action started at wall time `action_at` first blends for the action controller's transition (2 ticks,
writer.TRANSITION_TICKS) from the pose the hero had at that moment to the action's first frame, linearly, for every
channel the action keys, then plays the clip; while it plays, its channels replace the base's, bone by bone and
channel by channel (GeckoLib replaces per keyframe stack); once it has finished the base shows again. A keyframe event
of the action fires at action_at + its file time (on the frame its pose shows).
"""
import json
import re

from anim.clip import FORM_VAR, TICK, Clip, Event, Key, Track
from anim.writer import TRANSITION_TICKS

LOOP_MODES = {True: "loop", "loop": "loop", "hold_on_last_frame": "hold"}
# the one Molang form herogen writes (anim/writer.py): "v + k * v.fc_dhip" or "v - k * v.fc_dhip"
_FORM = re.compile(r"^\s*(-?\d+(?:\.\d+)?)\s*([+-])\s*(\d+(?:\.\d+)?)\s*\*\s*" + re.escape(FORM_VAR) + r"\s*$")


def _component(x, where):
    """(value, FORM_VAR coefficient) of one component: a number or herogen's form-scaled Molang string."""
    if isinstance(x, (int, float)) and not isinstance(x, bool):
        return float(x), 0.0
    if isinstance(x, str):
        m = _FORM.match(x)
        if m:
            k = float(m.group(3))
            return float(m.group(1)), (k if m.group(2) == "+" else -k)
    raise ValueError("%s: a key component must be a number or \"v + k * %s\", got %r" % (where, FORM_VAR, x))


def _vec_k(v, where):
    if isinstance(v, (int, float)) and not isinstance(v, bool):
        return (float(v),) * 3, None
    if not isinstance(v, list) or len(v) != 3:
        raise ValueError("%s: a key value must be 3 components, got %r" % (where, v))
    parts = [_component(x, where) for x in v]
    k = tuple(p[1] for p in parts)
    return tuple(p[0] for p in parts), (k if any(k) else None)


def _vec(v, where):
    vec, k = _vec_k(v, where)
    if k is not None:
        raise ValueError("%s: a jump's pre value cannot be form-scaled, got %r" % (where, v))
    return vec


def parse_track(keys, where):
    """A channel's {time: key} map, in file order, as a Track."""
    tr = Track()
    last = None
    for ts, value in keys.items():
        t = float(ts)
        if last is not None and t <= last:
            raise ValueError("%s: key %s is not after %s (GeckoLib reads keys in file order)" % (where, ts, last))
        last = t
        if isinstance(value, dict) and "vector" in value:
            v, k = _vec_k(value["vector"], where)
            tr.put(Key(t, v, value.get("easing", "linear"), k=k))
        elif isinstance(value, dict) and ("pre" in value or "post" in value):
            post, k = _vec_k(value.get("post", value.get("pre")), where)
            pre = _vec(value["pre"], where) if "pre" in value and t > 0 else None
            tr.put(Key(t, post, value.get("lerp_mode", "linear"), pre, k=k))
        else:
            v, k = _vec_k(value, where)
            tr.put(Key(t, v, k=k))
    return tr


def parse_clip(name, d):
    loop = LOOP_MODES.get(d.get("loop"), "once")
    clip = Clip(name, float(d.get("animation_length", 0.0)), loop)
    for bone, chans in (d.get("bones") or {}).items():
        for ch, keys in chans.items():
            if ch not in ("rotation", "position", "scale"):
                continue
            if not isinstance(keys, dict):
                keys = {"0.0": keys}
            clip.tracks[(bone, ch)] = parse_track(keys, "%s %s.%s" % (name, bone, ch))
    for ts, text in (d.get("timeline") or {}).items():
        for instr in str(text).split(";"):
            if instr.strip():
                clip.events.append(Event(float(ts), "instruction", instr.strip()))
    for ts, v in (d.get("sound_effects") or {}).items():
        clip.events.append(Event(float(ts), "sound", v.get("effect", "")))
    for ts, v in (d.get("particle_effects") or {}).items():
        clip.events.append(Event(float(ts), "particle", v.get("effect", ""), v.get("locator")))
    return clip


def parse_file(doc):
    """{name: Clip} of an animation file (a parsed document or its bytes)."""
    if isinstance(doc, (bytes, str)):
        doc = json.loads(doc)
    return {name: parse_clip(name, d) for name, d in (doc.get("animations") or {}).items()}


class AnimChain:
    """The animation files of a hero in lookup order (visual.json `animation_chain`: its own, its style, the core);
    a name resolves to the first file that has it, as HeroModel's fallback does."""

    def __init__(self, files):
        self.files = [(name, parse_file(doc)) for name, doc in files]

    def get(self, name):
        for _, clips in self.files:
            if name in clips:
                return clips[name]
        return None

    def file_of(self, name):
        for fname, clips in self.files:
            if name in clips:
                return fname
        return None

    def names(self):
        out = []
        for _, clips in self.files:
            out += [n for n in clips if n not in out]
        return out


def _lerp(a, b, f):
    return tuple(x + (y - x) * f for x, y in zip(a, b))


class Playback:
    """A base animation looping from wall time 0 and, optionally, an action started at `action_at`, on a model whose
    FORM_VAR (anim/clip.py: its hip height minus 18.25 px, what HeroModel sets for form-scaled keys) is `d`."""

    def __init__(self, chain, base, action=None, action_at=0.0, d=0.0):
        self.chain = chain
        self.d = float(d)
        self.base = chain.get(base) if base else None
        if base and self.base is None:
            raise KeyError("no animation %r in %s" % (base, ", ".join(f for f, _ in chain.files)))
        self.action = chain.get(action) if action else None
        if action and self.action is None:
            raise KeyError("no animation %r in %s" % (action, ", ".join(f for f, _ in chain.files)))
        self.action_at = float(action_at)
        self.blend = TRANSITION_TICKS["action"] * TICK

    def base_pose(self, t):
        return self.base.sample(t, self.d) if self.base is not None else {}

    def action_phase(self, t):
        """None (no action or before it), "blend", "play" or "done"."""
        if self.action is None or t < self.action_at - 1e-9:
            return None
        dt = t - self.action_at
        if dt < self.blend - 1e-9:
            return "blend"
        if dt <= self.blend + self.action.length + 1e-9 or self.action.loop != "once":
            return "play"
        return "done"

    def pose(self, t):
        """{bone: {channel: value}} the hero shows at wall time t."""
        pose = self.base_pose(t)
        phase = self.action_phase(t)
        if phase is None or phase == "done":
            return pose
        if phase == "blend":
            start = self.base_pose(self.action_at)          # the snapshot the controller blends from
            first = self.action.sample(0.0, self.d)
            f = (t - self.action_at) / self.blend
            for bone, chans in first.items():
                for ch, v in chans.items():
                    rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
                    pose.setdefault(bone, {})[ch] = _lerp(start.get(bone, {}).get(ch, rest), v, f)
            return pose
        for bone, chans in self.action.sample(t - self.action_at - self.blend, self.d).items():
            pose.setdefault(bone, {}).update(chans)
        return pose

    def events(self, t0, t1):
        """The action's keyframe events that fire at wall times in [t0, t1): (wall time, kind, value). GeckoLib fires
        an event on the first frame at or after its time, so the frame at t shows the events of (t - dt, t]."""
        if self.action is None:
            return []
        out = []
        for e in self.action.events:
            at = self.action_at + e.t
            if t0 <= at < t1:
                out.append((at, e.kind, e.value))
        return sorted(out)

    def windup_until(self):
        """Wall time of the action's first hit or cast (its wind-up ends there), or None."""
        if self.action is None:
            return None
        hits = sorted(e.t for e in self.action.events if e.kind == "instruction" and e.value in ("hit", "cast"))
        return self.action_at + hits[0] if hits else None

    def length(self):
        """The wall time the action needs (blend and clip), or the base's loop length."""
        if self.action is not None:
            return self.action_at + self.blend + self.action.length
        return self.base.length if self.base is not None else 0.0


def authored_face(pose):
    """HeroFace rule 2 (HEROES_V2 4.6): face_ctrl's position after the animation pass; each component overrides its
    plane (frame = value - 1) only when it is within 0.05 of a whole number of at least 1. Returns (eyes, mouth,
    cheeks), None where nothing overrides."""
    pos = (pose.get("face_ctrl") or {}).get("position")
    if not pos:
        return (None, None, None)
    out = []
    for x in pos:
        r = round(x)
        out.append(int(r) - 1 if r >= 1 and abs(x - r) <= 0.05 else None)
    return tuple(out)
