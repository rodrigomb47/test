"""GeckoLib .animation.json writer and the server timing file.

Animation file (format_version 1.8.0, what Blockbench's GeckoLib export writes; parsed by GeckoLib 4.9.3's
BakedAnimationsAdapter and KeyFramesAdapter):
  {"format_version": "1.8.0", "animations": {name: {
      "loop": true | "hold_on_last_frame"            (absent: play once)
      "animation_length": seconds,
      "bones": {bone: {"rotation" | "position" | "scale": {time: key}}},
      "timeline": {time: "hit;vfx:slash_arc"},       custom-instruction keyframes, joined with ";"
      "sound_effects": {time: {"effect": sound id}},
      "particle_effects": {time: {"effect": id, "locator": bone}}}}}
A key is {"vector": [x, y, z]} (linear), {"vector": [...], "easing": "easeOutCubic"} or, for a jump,
{"pre": [x, y, z], "post": [x, y, z]}. A form-scaled component (anim/clip.py, G4) is the Molang string
"v + k * v.fc_dhip" (or "v - k * v.fc_dhip"): GeckoLib compiles it once and evaluates it every frame with the
variable HeroModel sets for the model (0, the middle-form compromise, where it sets none). Times are strings ("0.0",
"0.45", "24.0") and every keyframe map is written in ascending time order (GeckoLib reads them in file order).

Event times and the controller blend (GeckoLib 4.9.3, AnimationController.process and processCurrentAnimation, read
with `javap -c`): when a controller starts an animation it first blends from the current pose to the animation's
first frame for its transition length T, then plays the clip from 0, so the pose of clip time p shows T after the
start. Keyframe events (timeline, sound, particle) are not delayed: they fire once `clip tick + T >= event tick`,
on every pass of a loop too. An event written at clip time p would therefore fire T before its pose, and the hit
would land on the server T before the swing shows it. herogen writes every event at p + T (T = TRANSITION_TICKS of
the clip's controller, HEROES_V2 4.4): the event then fires on the tick its pose shows, and the file's event time is
the time since the controller started the animation, the frame of reference of the server, which sets the action
code on the wind-up's first tick. A playback speed s scales the controller's whole clock, the blend included
(adjustTick multiplies it), so both then come at (T + p) / s. Clips and recipes keep clip time.

Server timing file (src/main/resources/data/factionclash/hero_timings/<file>.json, one per animation file; the
server resolves an animation through the same chain as the client: the hero's file, its style library, the core):
  {"format": 2, "generator", "file", "source_sha256", "tick": 0.05,
   "animations": {name: {"length", "ticks", "loop", "kind", "class", "transition_ticks", "total_ticks",
                         "events": [{"t", "tick", "type", "value"}],
                         "hits": [t, ...],
                         "frames": {"windup", "active", "recovery"}, "frame_ticks": {...}}}}
Times are the file's event times (clip time + the blend, so the time since the action started); `length` and
`ticks` are the clip's, `total_ticks` = ticks + transition_ticks (the blend and the clip). `frames` (only for
animations with a hit or cast marker) is what HEROES_V2 4.8 and 12.18 define, from the action's start: wind-up = the
first hit, active = last hit - first hit + one tick, recovery = the rest of the blend and the clip. At a playback
speed s every frame divides by s (4.8), the blend included, as GeckoLib's clock does.
"""
from anim.clip import FORM_VAR, TICK, fmt_time
from util import GENERATOR, Ordered

ANIM_FORMAT = "1.8.0"
TIMING_FORMAT = 2
LOOP_JSON = {"loop": True, "hold": "hold_on_last_frame"}
# The controllers' transition lengths in ticks (HEROES_V2 4.4: base 4, action 2). HeroModel's controllers must use
# these; every timing file states its animations' value (transition_ticks) so a Java test can compare.
TRANSITION_TICKS = {"base": 4, "action": 2}


def transition(clip):
    """The blend (s) GeckoLib plays before this clip: its controller's transition length."""
    return TRANSITION_TICKS[clip.meta.get("kind", "base")] * TICK


def file_time(clip, t):
    """Where an event of clip time t is written (module docstring): t plus the controller's blend."""
    return round(t + transition(clip), 4)


def _num(x):
    r = round(float(x), 3)
    if r == 0:
        return "0"
    return ("%.3f" % r).rstrip("0").rstrip(".")


def molang(v, k):
    """A form-scaled component: "v + k * v.fc_dhip" (numbers to 3 decimals, as the JSON writer rounds)."""
    k = round(float(k), 3)
    if k == 0:
        return round(float(v), 3)
    sign = "-" if k < 0 else "+"
    return "%s %s %s * %s" % (_num(v), sign, _num(abs(k)), FORM_VAR)


def _vector(v, k):
    if k is None:
        return list(v)
    return [molang(a, b) for a, b in zip(v, k)]


def _key_json(k):
    if k.pre is not None and k.t > 0:          # a jump at 0 has nothing before it: GeckoLib shows `post` from 0
        return {"pre": list(k.pre), "post": _vector(k.v, k.k)}
    d = {"vector": _vector(k.v, k.k)}
    if k.ease != "linear":
        d["easing"] = k.ease
    return d


def clip_json(clip):
    d = {"animation_length": clip.length}
    if clip.loop in LOOP_JSON:
        d["loop"] = LOOP_JSON[clip.loop]
    bones = {}
    for (bone, ch), tr in sorted(clip.tracks.items()):
        bones.setdefault(bone, {})[ch] = Ordered((fmt_time(k.t), _key_json(k)) for k in tr.keys)
    d["bones"] = bones
    instr = clip.instructions()
    if instr:
        d["timeline"] = Ordered((fmt_time(file_time(clip, t)), ";".join(v)) for t, v in sorted(instr.items()))
    sounds = sorted((file_time(clip, e.t), e.value) for e in clip.events if e.kind == "sound")
    if sounds:
        if len({t for t, _ in sounds}) != len(sounds):
            raise ValueError("%s: two sounds at one time" % clip.name)
        d["sound_effects"] = Ordered((fmt_time(t), {"effect": v}) for t, v in sounds)
    parts = sorted((file_time(clip, e.t), e.value, e.locator or "") for e in clip.events if e.kind == "particle")
    if parts:
        if len({t for t, _, _ in parts}) != len(parts):
            raise ValueError("%s: two particle keys at one time" % clip.name)
        d["particle_effects"] = Ordered((fmt_time(t), dict({"effect": v}, **({"locator": loc} if loc else {})))
                                        for t, v, loc in parts)
    return d


def animation_file(clips):
    return {"format_version": ANIM_FORMAT, "animations": {c.name: clip_json(c) for c in clips}}


def ticks(t):
    return int(round(t / TICK))


def frames_of(clip):
    """{windup, active, recovery} in seconds from the action's start (the controller's blend included) by the clip's
    hit and cast markers, or None."""
    hits = clip.hits()
    if not hits:
        return None
    blend = transition(clip)
    windup = hits[0] + blend
    active = hits[-1] - hits[0] + TICK
    return {"windup": round(windup, 4), "active": round(active, 4),
            "recovery": round(clip.length + blend - windup - active, 4)}


def clip_timing(clip):
    events = []
    for e in sorted(clip.events, key=lambda e: (e.t, e.kind, e.value)):
        typ = {"sound": "sfx", "particle": "particle"}.get(e.kind)
        value = e.value
        if typ is None:
            typ, _, rest = e.value.partition(":")
            value = rest
        ft = file_time(clip, e.t)
        ev = {"t": ft, "tick": ticks(ft), "type": typ}
        if value:
            ev["value"] = value
        events.append(ev)
    blend = ticks(transition(clip))
    d = {"length": clip.length, "ticks": ticks(clip.length), "loop": {"loop": "loop", "hold": "hold"}.get(clip.loop,
                                                                                                   "once"),
         "kind": clip.meta.get("kind", "base"), "transition_ticks": blend,
         "total_ticks": ticks(clip.length) + blend, "events": events}
    if clip.meta.get("class"):
        d["class"] = clip.meta["class"]
    fr = frames_of(clip)
    if fr:
        d["hits"] = [file_time(clip, t) for t in clip.hits()]
        d["frames"] = fr
        d["frame_ticks"] = {k: ticks(v) for k, v in fr.items()}
    return d


def timing_file(file_name, clips, source_hash):
    return {"format": TIMING_FORMAT, "generator": GENERATOR, "file": file_name, "source_sha256": source_hash,
            "tick": TICK, "animations": {c.name: clip_timing(c) for c in clips}}
