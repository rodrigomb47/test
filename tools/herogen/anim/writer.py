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
{"pre": [x, y, z], "post": [x, y, z]}. Times are strings ("0.0", "0.45", "24.0") and every keyframe map is written
in ascending time order (GeckoLib reads them in file order).

Server timing file (src/main/resources/data/factionclash/hero_timings/<file>.json, one per animation file; the
server resolves an animation through the same chain as the client: the hero's file, its style library, the core):
  {"format": 1, "generator", "file", "source_sha256", "tick": 0.05,
   "animations": {name: {"length", "ticks", "loop", "kind", "class",
                         "events": [{"t", "tick", "type", "value"}],
                         "hits": [t, ...],
                         "frames": {"windup", "active", "recovery"}, "frame_ticks": {...}}}}
`frames` (only for animations with a hit or cast marker) is what HEROES_V2 4.8 and 12.18 define: wind-up = the first
hit, active = last hit - first hit + one tick, recovery = length - wind-up - active.
"""
from anim.clip import TICK, fmt_time
from util import GENERATOR, Ordered

ANIM_FORMAT = "1.8.0"
TIMING_FORMAT = 1
LOOP_JSON = {"loop": True, "hold": "hold_on_last_frame"}


def _key_json(k):
    if k.pre is not None:
        return {"pre": list(k.pre), "post": list(k.v)}
    d = {"vector": list(k.v)}
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
        d["timeline"] = Ordered((fmt_time(t), ";".join(v)) for t, v in sorted(instr.items()))
    sounds = sorted((e.t, e.value) for e in clip.events if e.kind == "sound")
    if sounds:
        if len({t for t, _ in sounds}) != len(sounds):
            raise ValueError("%s: two sounds at one time" % clip.name)
        d["sound_effects"] = Ordered((fmt_time(t), {"effect": v}) for t, v in sounds)
    parts = sorted((e.t, e.value, e.locator or "") for e in clip.events if e.kind == "particle")
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
    """{windup, active, recovery} in seconds from the clip's hit and cast markers, or None."""
    hits = clip.hits()
    if not hits:
        return None
    windup = hits[0]
    active = hits[-1] - hits[0] + TICK
    return {"windup": round(windup, 4), "active": round(active, 4),
            "recovery": round(clip.length - windup - active, 4)}


def clip_timing(clip):
    events = []
    for e in sorted(clip.events, key=lambda e: (e.t, e.kind, e.value)):
        typ = {"sound": "sfx", "particle": "particle"}.get(e.kind)
        value = e.value
        if typ is None:
            typ, _, rest = e.value.partition(":")
            value = rest
        ev = {"t": round(e.t, 4), "tick": ticks(e.t), "type": typ}
        if value:
            ev["value"] = value
        events.append(ev)
    d = {"length": clip.length, "ticks": ticks(clip.length), "loop": {"loop": "loop", "hold": "hold"}.get(clip.loop,
                                                                                                   "once"),
         "kind": clip.meta.get("kind", "base"), "events": events}
    if clip.meta.get("class"):
        d["class"] = clip.meta["class"]
    fr = frames_of(clip)
    if fr:
        d["hits"] = [round(t, 4) for t in clip.hits()]
        d["frames"] = fr
        d["frame_ticks"] = {k: ticks(v) for k, v in fr.items()}
    return d


def timing_file(file_name, clips, source_hash):
    return {"format": TIMING_FORMAT, "generator": GENERATOR, "file": file_name, "source_sha256": source_hash,
            "tick": TICK, "animations": {c.name: clip_timing(c) for c in clips}}
