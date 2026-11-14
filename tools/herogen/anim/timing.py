"""Move timings for the server definition (HEROES_V2.md 4.8, 12.1, 12.18): the `generated` block.

The kit step (F2) writes data/factionclash/heroes/<id>.json; its `generated.moves` comes from here, from the same
clips the animation files are written from, so the server's wind-up ends on the tick the client's `hit` marker plays
and the swing shows the cut (frames count from the action's start, the action controller's blend included: writer.py):
  {"moves": [{"id", "anim", "file", "frames": {windup, active, recovery}, "frame_ticks": {...}, "transition_ticks"}],
   "ultimate": {...} | None, "notes": [...]}
A move's animation is `move.<id>` (the ultimate's `ultimate`), resolved through the hero's chain (its own file, its
style library, the core). A move with no animation, or no hit or cast marker in it, gets a NOTE and no frames (the
validator then uses the class minimums); frames below the class minimums get a NOTE (the validator raises them,
6.4: "ADJUSTED").
"""
from anim import library
from anim.quality import CLASS_MIN
from anim.writer import frames_of, ticks, transition

FORM_CLASS = {
    "melee": "light", "combo": "light", "shot": "light", "volley": "light", "bolt": "light", "barrage": "light",
    "counter": "light", "self": "light", "ally": "light", "allies": "light",
    "leap": "heavy", "dash": "heavy", "charge": "heavy", "grab": "heavy", "line": "heavy", "chain": "heavy",
    "cone": "area", "nova": "area", "zone": "area", "wave": "area", "rain": "area", "sweep": "area",
}


def move_class(move, ultimate=False):
    if ultimate:
        return "ultimate"
    return move.get("class") or FORM_CLASS.get(move.get("form"), "light")


def _entry(hero, move, anim, cls, notes):
    hid = hero["id"]
    for built in (library.hero(hero), library.style(library.style_of(hero)), library.core()):
        if anim in built.by_name:
            clip, where = built.by_name[anim], built.file
            break
    else:
        notes.append("%s: move %s has no animation %s" % (hid, move.get("id"), anim))
        return {"id": move.get("id"), "anim": anim, "file": None, "frames": None}
    fr = frames_of(clip)
    if fr is None:
        notes.append("%s: %s (%s) has no hit or cast marker" % (hid, anim, where))
    else:
        # the minimums count from the action's start, the controller's blend included, as the server's frames do
        # (quality.check_markers; C1)
        wmin, rmin = CLASS_MIN[cls]
        windup = fr["windup"]
        if windup < wmin - 1e-9 or fr["recovery"] < rmin - 1e-9:
            notes.append("%s: %s wind-up %.2f s / recovery %.2f s below the %s minimums %.2f / %.2f" % (
                hid, anim, windup, fr["recovery"], cls, wmin, rmin))
    out = {"id": move.get("id"), "anim": anim, "file": where, "class": cls, "frames": fr}
    if fr:
        out["frame_ticks"] = {k: ticks(v) for k, v in fr.items()}
        out["transition_ticks"] = ticks(transition(clip))
    return out


def generated(hero):
    notes = []
    moves = [_entry(hero, m, "move." + m["id"], move_class(m), notes) for m in hero.get("moves", []) or []]
    ult = hero.get("ultimate")
    ultimate = _entry(hero, ult, "ultimate", move_class(ult, True), notes) if ult else None
    return {"moves": moves, "ultimate": ultimate, "notes": notes}
