"""G7: the file-level half of the asset checks (HEROES_V2.md 14.2), on the files the mod ships.

`herogen check` runs these on each hero's committed files (on the in-memory build for the style fixtures) beside the
rules that need the generator (anim/quality.py on the built clips, the coplanar rule, `build --check`). Everything here
reads only written files: the geometry, the textures, `visual.json`, the animation files of the hero's chain with
their server timing files, the hero's server definition and the action-code table of the Java side. That is also what
the Java `HeroAssetsTest` can read, so the functions here are the ones it mirrors (HEROES_V2 15.2, row G7).

Rules (each returns problem strings; `check_files` also returns NOTEs):
  json        every JSON file of the hero and its chain parses (strict: no NaN or Infinity)
  geometry    format_version 1.12.0, then validate.check_built: budgets (bones, cubes, cubes outside fine_ bones,
              texture size), bone names and the required bones, whole-texel UVs inside the texture and not
              overlapping, the face planes on their fixed regions, the alpha holes under the eyes and mouth planes, the
              clear face, every face frame present and distinct, the eyes mirroring unless `visual.json`
              `face.asymmetric` says the face is asymmetric by design
  visual      `visual.json` texture size equals the geometry's; its face regions are the rig's; the textures it names
              exist (glow mask, phase texture)
  chain       `animation_chain` is [the hero, its style library, the core], and every file of it exists with its
              timing file (GeckoLib throws when a file of the chain is missing)
  channels    every keyframe channel of every animation file: times ascend in file order, the first is 0 and none is
              after the end; a looping animation keys every channel at its end with the value it starts with
  face_keys   face_ctrl keys are whole frame numbers in range and every change is a jump (Bedrock pre/post), 4.6
  timing      each timing file says what its animation file's markers say (anim/quality.check_timing)
  codes       every action code the server can send this hero resolves through its chain (the code table is read
              from HeroActions.java as text)
  lists       `visual.json` moves and states equal the server definition's (moves without a clip in the chain may
              be left out: they play their look's telegraph pose; a move whose definition gives frames may not)
  frames      every move, the ultimate and the awakened ultimate whose definition gives `frames` (or a `generated`
              entry): the clip's wind-up, active and recovery ticks (timing file, from the action's start, the
              controller's blend included) equal the definition's within one tick
The server definition is data/factionclash/heroes/<id>.json, or, until the authoring tasks write it, the slice's
format-2 fixture src/test/resources/heroes/slice/<id>.json.
"""
import io
import json
import os
import re

import numpy as np
from PIL import Image

import validate
from geo import FORMAT_VERSION, GeoModel
from rig import load_rig
from util import ROOT, HerogenError

ACTIONS_JAVA = "src/main/java/com/factionclash/logic/hero/HeroActions.java"
DEF_DIR = "src/main/resources/data/factionclash/heroes"
SLICE_DIR = "src/test/resources/heroes/slice"
TICK = 0.05
FACE_RANGES = (12, 8, 4)        # face_ctrl position x, y, z: eye frame + 1 (0-12), mouth (0-8), cheeks (0-4)
LOOP_TOL = 0.01
MAX_STATES = 3


# ------------------------------------------------------------------ inputs

def _strict(name):
    raise ValueError("%s is not JSON" % name)


def parse_json(data):
    return json.loads(data.decode("utf-8") if isinstance(data, bytes) else data, parse_constant=_strict)


def out_paths(hero_id):
    import build
    return build.out_paths(hero_id)


def chain_paths(name):
    from anim import library as animlib
    return animlib.out_paths(name)


def committed_files(hero_id, root=ROOT):
    """{path: bytes} of what the mod ships for this hero: its outputs and the animation and timing files of the chain
    its visual.json names (a missing file is simply absent: the chain rule reports it)."""
    files = {}
    wanted = list(out_paths(hero_id).values())
    visual = os.path.join(root, out_paths(hero_id)["visual"])
    if os.path.exists(visual):
        try:
            with open(visual, "rb") as f:
                chain = parse_json(f.read()).get("animation_chain", []) or []
        except ValueError:
            chain = []
        for name in chain:
            if isinstance(name, str):
                wanted += list(chain_paths(name).values())
    for p in wanted:
        full = os.path.join(root, p)
        if os.path.exists(full):
            with open(full, "rb") as f:
                files[p] = f.read()
    return files


def definition_of(hero_id, root=ROOT):
    """(definition dict, relative path) of a hero's server definition, or (None, None): the built-in definition, else
    the slice's format-2 test fixture (the slice's real kit until C1-C4 write the definitions)."""
    for d in (DEF_DIR, SLICE_DIR):
        p = "%s/%s.json" % (d, hero_id)
        full = os.path.join(root, p)
        if os.path.exists(full):
            with open(full, "rb") as f:
                return parse_json(f.read()), p
    return None, None


def slice_ids(root=ROOT):
    d = os.path.join(root, SLICE_DIR)
    return sorted(n[:-5] for n in os.listdir(d) if n.endswith(".json")) if os.path.isdir(d) else []


# ------------------------------------------------------------------ the action code table (HeroActions.java)

_ARRAY = re.compile(r"String\[\]\s+(\w+)\s*=\s*\{(.*?)\};", re.S)
_INTS = re.compile(r"public\s+static\s+final\s+int\s+([^;]+);")


def action_table(root=ROOT):
    """{code: name} for every code HeroActions.actionName names, read from the Java source as text (the arrays FIXED,
    WORK and EMOTES and the constants FIRST_STATE, FIRST_WORK, FIRST_EMOTE, FIRST_MOVE, MOVE_SLOTS, ULTIMATE,
    ULTIMATE_END, ULTIMATE_AWAKENED) and laid out as its actionName does. `state.N` and `move.N` are slots."""
    path = os.path.join(root, ACTIONS_JAVA)
    with open(path, encoding="utf-8") as f:
        src = f.read()
    arrays = {}
    for name, body in _ARRAY.findall(src):
        toks = re.findall(r'null|"[^"]*"', body)
        arrays[name] = [None if t == "null" else t[1:-1] for t in toks]
    ints = {}
    for decl in _INTS.findall(src):
        for part in decl.split(","):
            m = re.match(r"\s*(\w+)\s*=\s*(\d+)\s*$", part)
            if m:
                ints[m.group(1)] = int(m.group(2))
    need = ("FIRST_STATE", "FIRST_WORK", "FIRST_EMOTE", "FIRST_MOVE", "MOVE_SLOTS", "ULTIMATE", "ULTIMATE_END",
            "ULTIMATE_AWAKENED")
    missing = [n for n in ("FIXED", "WORK", "EMOTES") if n not in arrays] + [n for n in need if n not in ints]
    if missing:
        raise HerogenError("%s: cannot read the action code table (%s not found)" % (ACTIONS_JAVA, ", ".join(missing)))
    table = {}
    for c, name in enumerate(arrays["FIXED"]):
        if c > 0 and name:
            table[c] = name
    for i in range(MAX_STATES):
        table[ints["FIRST_STATE"] + i] = "state.%d" % (i + 1)
    for i, name in enumerate(arrays["WORK"]):
        table[ints["FIRST_WORK"] + i] = name
    for i, name in enumerate(arrays["EMOTES"]):
        table[ints["FIRST_EMOTE"] + i] = name
    for i in range(ints["MOVE_SLOTS"]):
        table[ints["FIRST_MOVE"] + i] = "move.%d" % i
    table[ints["ULTIMATE"]] = "ultimate"
    table[ints["ULTIMATE_END"]] = "ultimate.end"
    table[ints["ULTIMATE_AWAKENED"]] = "ultimate.awakened"
    table["_move_slots"] = ints["MOVE_SLOTS"]
    table["_first_move"] = ints["FIRST_MOVE"]
    table["_first_state"] = ints["FIRST_STATE"]
    return table


# ------------------------------------------------------------------ the chain

class Chain:
    """The animation files of a hero's chain as written: [(file name, animation json, timing json)], in look-up
    order (the hero's own file, its style library, the core)."""

    def __init__(self, entries):
        self.entries = entries

    @classmethod
    def of(cls, visual, files):
        entries = []
        for name in visual.get("animation_chain", []) or []:
            p = chain_paths(name)
            a = files.get(p["animation"])
            t = files.get(p["timing"])
            entries.append((name, _maybe_json(a), _maybe_json(t)))
        return cls(entries)

    def names(self):
        return [e[0] for e in self.entries]

    def find(self, anim):
        """(file name, animation entry, timing entry) of the first file that has `anim`, or None."""
        for name, a, t in self.entries:
            if a and anim in a.get("animations", {}):
                return name, a["animations"][anim], (t or {}).get("animations", {}).get(anim)
        return None

    def has(self, anim):
        return self.find(anim) is not None


def _maybe_json(data):
    if data is None:
        return None
    try:
        return parse_json(data)
    except ValueError:
        return None


# ------------------------------------------------------------------ rules

def check_json(files):
    out = []
    for path, data in sorted(files.items()):
        if path.endswith(".json"):
            try:
                parse_json(data)
            except (ValueError, UnicodeDecodeError) as e:
                out.append("%s: not valid JSON (%s)" % (path, e))
    return out


def check_geometry(hero_id, files, visual):
    """(problems, GeoModel or None): format_version, then validate.check_built on the geometry and the texture."""
    paths = out_paths(hero_id)
    for key in ("geo", "texture"):
        if paths[key] not in files:
            return ["%s: missing" % paths[key]], None
    try:
        data = parse_json(files[paths["geo"]])
    except (ValueError, UnicodeDecodeError):
        return [], None                 # check_json reports it
    if data.get("format_version") != FORMAT_VERSION:
        return ["%s: format_version %r, GeckoLib's geometry is %s" % (paths["geo"], data.get("format_version"),
                                                                     FORMAT_VERSION)], None
    try:
        geo = GeoModel(data)
        tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
    except (HerogenError, KeyError, TypeError, ValueError, OSError) as e:
        return ["geometry or texture unreadable: %s" % e], None
    asym = bool(((visual or {}).get("face") or {}).get("asymmetric", False))
    return validate.check_built(geo, tex, asymmetric=asym), geo


def check_visual(hero_id, files, visual, geo):
    out = []
    paths = out_paths(hero_id)
    tex = visual.get("texture") or {}
    if geo is not None and (tex.get("width"), tex.get("height")) != (geo.tex_w, geo.tex_h):
        out.append("visual.json texture %sx%s, the geometry %dx%d" % (tex.get("width"), tex.get("height"),
                                                                     geo.tex_w, geo.tex_h))
    if tex.get("glowmask") and paths["glowmask"] not in files:
        out.append("visual.json names a glow mask, %s is missing" % paths["glowmask"])
    wants_p2 = tex.get("p2") or any(p.get("texture") == "p2" for p in visual.get("phases", []) or [])
    if wants_p2 and paths["p2"] not in files:
        out.append("visual.json names a phase texture, %s is missing" % paths["p2"])
    rig = load_rig()
    tpl, planes = rig["uv_template"], rig["face"]["planes"]
    regions = ((visual.get("face") or {}).get("regions")) or {}
    for plane in ("eyes", "mouth", "cheeks"):
        p = planes[plane]
        want = {"u": tpl[plane][0], "v": tpl[plane][1], "w": p["frame"][0], "h": p["frame"][1],
                "columns": p["grid"][0], "frames": p["frames"]}
        if regions.get(plane) != want:
            out.append("visual.json face.regions.%s is %s, the rig's is %s" % (plane, regions.get(plane), want))
    return out


def check_chain(hero_id, visual, files):
    from anim import library as animlib
    out = []
    chain = visual.get("animation_chain")
    style = visual.get("style")
    want = [hero_id, animlib.style_file(style) if style else None, animlib.CORE]
    if chain != want:
        out.append("visual.json animation_chain is %s, expected %s (the hero's file, its style library, the core)"
                   % (chain, want))
    for name in chain or []:
        for kind, p in sorted(chain_paths(name).items()):
            if p not in files:
                out.append("%s: missing (%s file of the chain; GeckoLib needs every file of it)" % (p, kind))
    return out


def _value(key):
    """The value a written key holds after its time (a vector or a jump's post)."""
    if isinstance(key, dict):
        if "post" in key:
            return key["post"]
        if "vector" in key:
            return key["vector"]
        return None
    return key


def _close(a, b):
    if not (isinstance(a, list) and isinstance(b, list)) or len(a) != len(b):
        return a == b
    for x, y in zip(a, b):
        if isinstance(x, (int, float)) and isinstance(y, (int, float)):
            if abs(x - y) > LOOP_TOL:
                return False
        elif x != y:
            return False
    return True


def check_channels(file_name, anim_json):
    """GeckoLib reads a channel's keys in file order, each segment running from the previous key's time, and throws
    on a "0" key that is not the first: so times ascend, the first is 0, none after the end; a loop ends where it
    starts."""
    out = []
    for name, a in sorted((anim_json or {}).get("animations", {}).items()):
        length = float(a.get("animation_length", 0))
        looping = a.get("loop") is True
        for bone, chans in sorted((a.get("bones") or {}).items()):
            for ch, keys in sorted(chans.items()):
                where = "%s: %s %s.%s" % (file_name, name, bone, ch)
                if not isinstance(keys, dict) or not keys:
                    out.append("%s: no keys" % where)
                    continue
                times = []
                for ts in keys:
                    try:
                        times.append(float(ts))
                    except ValueError:
                        out.append("%s: key time %r is not a number" % (where, ts))
                        times = None
                        break
                if times is None:
                    continue
                if any(b <= a_ for a_, b in zip(times, times[1:])):
                    out.append("%s: key times do not ascend in file order" % where)
                if times[0] > 1e-9:
                    out.append("%s: the first key is at %s s, not 0" % (where, times[0]))
                if times[-1] > length + 1e-6:
                    out.append("%s: a key at %s s is after the end (%s s)" % (where, times[-1], length))
                if looping:
                    vals = list(keys.values())
                    if abs(times[-1] - length) > 1e-6:
                        out.append("%s: a loop channel without a key at the end" % where)
                    elif not _close(_value(vals[0]), _value(vals[-1])):
                        out.append("%s: the loop does not close (%s at 0, %s at the end)" % (
                            where, _value(vals[0]), _value(vals[-1])))
    return out


def check_face_keys(file_name, anim_json):
    """face_ctrl position keys are whole frame numbers in range (eye frame + 1, mouth + 1, cheek + 1; 0 = no
    override) and every change is a jump: a {pre, post} key whose pre is the value before (4.6; GeckoLib's `step` is
    two steps and a linear key slides through other frames)."""
    out = []
    for name, a in sorted((anim_json or {}).get("animations", {}).items()):
        bones = (a.get("bones") or {}).get("face_ctrl")
        if not bones:
            continue
        where = "%s: %s face_ctrl" % (file_name, name)
        for ch in sorted(bones):
            if ch != "position":
                out.append("%s: keys %s (only position carries face frames)" % (where, ch))
        prev = None
        for ts, key in (bones.get("position") or {}).items():
            val = _value(key)
            if not (isinstance(val, list) and len(val) == 3 and all(isinstance(v, (int, float)) for v in val)):
                out.append("%s at %s: %r is not three numbers" % (where, ts, val))
                break
            if any(abs(v - round(v)) > 1e-6 or not 0 <= round(v) <= hi for v, hi in zip(val, FACE_RANGES)):
                out.append("%s at %s: %s is not whole frame numbers in range (eyes 0-12, mouth 0-8, cheeks 0-4)"
                           % (where, ts, val))
            if prev is not None and [round(v) for v in val] != [round(v) for v in prev]:
                pre = key.get("pre") if isinstance(key, dict) else None
                if pre is None or [round(v) for v in pre] != [round(v) for v in prev]:
                    out.append("%s at %s: changes %s -> %s without a jump (pre/post): it would slide through other "
                               "frames" % (where, ts, prev, val))
            prev = val
    return out


def check_timing(chain, files):
    from anim import quality
    out = []
    for name, a, t in chain.entries:
        if a is None or t is None:
            continue                    # check_chain / check_json report it
        out += ["%s: %s" % (name, p) for p in quality.check_timing(a, t)]
    return out


def _def_moves(definition):
    return [m for m in (definition or {}).get("moves", []) or [] if isinstance(m, dict) and m.get("id")]


def _def_states(definition):
    return [s.get("name") for s in (definition or {}).get("states", []) or [] if isinstance(s, dict)]


def _phases(definition):
    return len(((definition or {}).get("boss") or {}).get("phases", []) or [])


def _bare(name, prefix):
    return name[len(prefix):] if isinstance(name, str) and name.startswith(prefix) else name


def required_actions(visual, definition, chain, table):
    """[(code, animation name, why)] of every action the server can send this hero (HEROES_V2 appendix D): every
    fixed code but the conditional ones; `attack.ranged` with a ranged basic attack, `phase.2` and `phase.3` with that
    many boss phases; `state.<name>` and `move.<id>` for the slots visual.json names; `ultimate` and
    `ultimate.awakened` when the definition has them, `ultimate.end` when the chain has an `ultimate.loop` to end."""
    out = []
    basic = ((definition or {}).get("basic_attack") or {})
    ranged = bool(definition) and basic.get("style") not in (None, "melee")
    ult = (definition or {}).get("ultimate") if isinstance((definition or {}).get("ultimate"), dict) else None
    states = list(visual.get("states", []) or [])
    moves = list(visual.get("moves", []) or [])
    for code in sorted(k for k in table if isinstance(k, int)):
        name = table[code]
        if name.startswith("state."):
            i = int(name.split(".")[1]) - 1
            if i < len(states):
                out.append((code, "state." + _bare(states[i], "state."), "state slot %d" % (i + 1)))
        elif name.startswith("move.") and name[5:].isdigit():
            i = int(name[5:])
            if i < len(moves):
                out.append((code, "move." + _bare(moves[i], "move."), "move slot %d" % i))
        elif name == "attack.ranged":
            if ranged:
                out.append((code, name, "a ranged basic attack (%s)" % basic.get("style")))
        elif name in ("phase.2", "phase.3"):
            if _phases(definition) >= int(name[-1]):
                out.append((code, name, "%d boss phases" % _phases(definition)))
        elif name == "ultimate":
            if ult:
                out.append((code, name, "the ultimate %s" % ult.get("id")))
        elif name == "ultimate.awakened":
            if ult and isinstance(ult.get("awakened"), dict):
                out.append((code, name, "the awakened ultimate %s" % ult["awakened"].get("id")))
        elif name == "ultimate.end":
            if chain.has("ultimate.loop"):
                out.append((code, name, "the chain has ultimate.loop"))
        else:
            out.append((code, name, "every hero"))
    return out


def check_codes(visual, definition, chain, table):
    out = []
    for code, name, why in required_actions(visual, definition, chain, table):
        if not chain.has(name):
            out.append("action code %d (%s, %s) does not resolve through %s" % (code, name, why,
                                                                              ", ".join(chain.names())))
    return out


def check_lists(visual, definition, chain, table):
    """visual.json moves and states against the server definition (problems, notes)."""
    out, notes = [], []
    moves = list(visual.get("moves", []) or [])
    states = list(visual.get("states", []) or [])
    slots = table.get("_move_slots", 12)
    if len(moves) > slots:
        out.append("visual.json lists %d moves; the action codes have %d move slots" % (len(moves), slots))
    if len(states) > MAX_STATES:
        out.append("visual.json lists %d states; the action codes have %d state slots" % (len(states), MAX_STATES))
    if len(set(moves)) != len(moves):
        out.append("visual.json lists a move twice: %s" % moves)
    if definition is None:
        return out, notes
    bare = [_bare(m, "move.") for m in moves]
    dmoves = _def_moves(definition)
    with_clip = [m["id"] for m in dmoves if chain.has("move." + m["id"])]
    if bare != with_clip:
        out.append("visual.json moves %s, the definition's moves %s (its order; a move may be left out only when the "
                   "chain has no move.<id> for it)" % (bare, [m["id"] for m in dmoves]))
    for m in dmoves:
        if chain.has("move." + m["id"]):
            continue
        if m.get("frames") or _generated(definition, m["id"]):
            out.append("move %s: the definition gives frames but the chain has no move.%s" % (m["id"], m["id"]))
        else:
            notes.append("move %s has no clip (move.%s): the server plays its look's telegraph pose" % (m["id"],
                                                                                                  m["id"]))
    dstates = _def_states(definition)
    if [_bare(s, "state.") for s in states] != dstates:
        out.append("visual.json states %s, the definition's states %s" % (states, dstates))
    return out, notes


def _generated(definition, move_id, ultimate=False):
    gen = (definition or {}).get("generated") or {}
    if ultimate:
        u = gen.get("ultimate")
        return u.get("frames") if isinstance(u, dict) else None
    for g in gen.get("moves", []) or []:
        if isinstance(g, dict) and g.get("id") == move_id:
            return g.get("frames")
    return None


def _ticks(seconds):
    return int(round(float(seconds) / TICK))


def check_frames(definition, chain):
    """Each move's clip against the definition's frames, within one tick (HEROES_V2 4.8, 14.2)."""
    out = []
    items = [("move." + m["id"], m.get("frames"), _generated(definition, m["id"]), "move %s" % m["id"])
             for m in _def_moves(definition)]
    ult = (definition or {}).get("ultimate")
    if isinstance(ult, dict):
        items.append(("ultimate", ult.get("frames"), _generated(definition, None, True), "the ultimate"))
        aw = ult.get("awakened")
        if isinstance(aw, dict):
            items.append(("ultimate.awakened", aw.get("frames"), None, "the awakened ultimate"))
    for anim, authored, generated, what in items:
        found = chain.find(anim)
        if found is None:
            continue                        # lists / codes report a missing clip
        file_name, _, timing = found
        ft = (timing or {}).get("frame_ticks")
        for label, fr in (("frames", authored), ("generated frames", generated)):
            if not isinstance(fr, dict):
                continue
            if not ft:
                out.append("%s: %s (%s) has no hit or cast marker; the definition's %s are %s" % (
                    what, anim, file_name, label, fr))
                continue
            for k in ("windup", "active", "recovery"):
                if k not in fr:
                    continue
                want = _ticks(fr[k])
                if abs(ft.get(k, -99) - want) > 1:
                    hit = ""
                    if k == "windup":
                        hit = ", its first hit or cast at tick %d of the action" % ft.get(k, -1)
                    out.append("%s: %s %d ticks in %s (%s%s), %d in the definition's %s (%.2f s): more than one "
                               "tick apart" % (what, k, ft.get(k, -1), file_name, anim, hit, want, label,
                                               float(fr[k])))
    return out


def check_files(hero_id, files, definition=None, table=None):
    """Every file-level rule on one hero's files ({path: bytes}: its outputs and its chain's animation and timing
    files). Returns (problems, notes)."""
    table = table or action_table()
    problems = check_json(files)
    paths = out_paths(hero_id)
    visual = _maybe_json(files.get(paths["visual"]))
    if visual is None:
        problems.append("%s: missing or not JSON" % paths["visual"])
        return problems, []
    geo_problems, geo = check_geometry(hero_id, files, visual)
    problems += geo_problems
    problems += check_visual(hero_id, files, visual, geo)
    problems += check_chain(hero_id, visual, files)
    chain = Chain.of(visual, files)
    for name, a, _ in chain.entries:
        if a is not None:
            problems += check_channels(name, a) + check_face_keys(name, a)
    problems += check_timing(chain, files)
    problems += check_codes(visual, definition, chain, table)
    lists, notes = check_lists(visual, definition, chain, table)
    problems += lists
    problems += check_frames(definition, chain)
    return problems, notes


def check_committed(hero_id, root=ROOT, table=None):
    """check_files on the committed files and the hero's server definition (what HeroAssetsTest reads)."""
    definition, _ = definition_of(hero_id, root)
    return check_files(hero_id, committed_files(hero_id, root), definition, table or action_table(root))
