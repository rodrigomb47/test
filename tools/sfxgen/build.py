"""`build`: render every generated sound, encode it to Ogg Vorbis, and write sounds.json and the index S2 reads.

Outputs (all generated, never edited by hand):
- `src/main/resources/assets/factionclash/sounds/hero/<set>/<name>[_<n>].ogg`
- `src/main/resources/assets/factionclash/sounds.json`: one entry per event (generated files with their attenuation
  distance, vanilla references as `"type": "event"` entries with pitch and volume), with its subtitle key
- `src/main/resources/assets/factionclash/sounds/hero_sfx.json`: the index (SOUND.md section 7): per event who plays
  it, its category, range, play-time pitch and volume ranges, companions, loop flag, length; per hero the voice, the
  move, phase, state, passive, weak-point and quirk maps; the shared cue map.
"""
import concurrent.futures as cf
import json
import os

import codec
import recipe
from paths import ASSETS, INDEX_JSON, NAMESPACE, SOUNDS_DIR, SOUNDS_JSON, SRC

INDEX_FORMAT = 1


def dumps(obj):
    return json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def _render_job(args):
    src, events = args
    cat = recipe.load_all(src)
    by = cat.by_event()
    r = recipe.Renderer(cat)
    out = []
    for ev in events:
        s = by[ev]
        for k, stem in enumerate(s.file_names()):
            x, rate = r.final(s, k)
            out.append((ev, k, stem, rate, codec.encode_ogg(x, rate), len(x) / rate))
    return out


def render_all(cat, src=SRC, jobs=None, only=None):
    """[(event, k, stem, rate, ogg bytes, seconds)] for every generated sound (or the events in `only`)."""
    events = [s.event for s in cat.sounds() if s.generated and (only is None or s.event in only)]
    jobs = jobs or min(8, os.cpu_count() or 1)
    if jobs <= 1 or len(events) < 4:
        return _render_job((src, events))
    # round-robin chunks keep long and short sounds spread over the workers
    chunks = [events[i::jobs * 3] for i in range(jobs * 3)]
    res = []
    with cf.ProcessPoolExecutor(max_workers=jobs) as ex:
        for part in ex.map(_render_job, [(src, c) for c in chunks if c]):
            res.extend(part)
    res.sort(key=lambda r: (r[0], r[1]))
    return res


def sounds_json(cat, rendered):
    lengths = {}
    for ev, k, stem, rate, data, sec in rendered:
        lengths.setdefault(ev, []).append(stem)
    out = {}
    for s in cat.sounds():
        entries = []
        if s.generated:
            for stem in s.file_names():
                e = {"name": "%s:%s" % (NAMESPACE, stem)}
                if s.range != 16:
                    e["attenuation_distance"] = s.range
                if s.preload:
                    e["preload"] = True
                entries.append(e)
        elif s.alias:
            for a in s.alias:
                ref = cat.lookup(a["sound"], s.set)
                e = {"name": "%s:%s" % (NAMESPACE, ref.event if ref else a["sound"]), "type": "event"}
                if float(a.get("pitch", 1.0)) != 1.0:
                    e["pitch"] = round(float(a["pitch"]), 4)
                if float(a.get("volume", 1.0)) != 1.0:
                    e["volume"] = round(float(a["volume"]), 4)
                if int(a.get("weight", 1)) != 1:
                    e["weight"] = int(a["weight"])
                entries.append(e)
        else:
            for v in s.vanilla:
                e = {"name": v["event"], "type": "event"}
                if float(v.get("pitch", 1.0)) != 1.0:
                    e["pitch"] = round(float(v["pitch"]), 4)
                if float(v.get("volume", 1.0)) != 1.0:
                    e["volume"] = round(float(v["volume"]), 4)
                if int(v.get("weight", 1)) != 1:
                    e["weight"] = int(v["weight"])
                entries.append(e)
        out[s.event] = {"sounds": entries, "subtitle": s.subtitle_key}
    return out


def index_json(cat, rendered):
    secs = {}
    rates = {}
    for ev, k, stem, rate, data, sec in rendered:
        secs[ev] = max(secs.get(ev, 0.0), sec)
        rates[ev] = rate
    events = {}
    for s in cat.sounds():
        e = {
            "set": s.set,
            "kind": s.kind,
            "plays": s.plays,
            "category": s.category,
            "range": s.range,
            "pitch": [round(v, 4) for v in s.pitch],
            "volume": [round(v, 4) for v in s.volume],
            "source": "generated" if s.generated else ("alias" if s.alias else "vanilla"),
            "subtitle": s.subtitle_key,
        }
        if s.loop:
            e["loop"] = True
        if s.generated:
            e["variants"] = s.variants
            e["length"] = round(secs.get(s.event, 0.0), 3)
        elif s.alias:
            e["refs"] = ["%s:%s" % (NAMESPACE, cat.lookup(a["sound"], s.set).event) for a in s.alias]
        else:
            e["refs"] = [v["event"] for v in s.vanilla]
        if s.companions:
            e["with"] = [{"event": "%s:%s" % (NAMESPACE, cat.lookup(c["sound"], s.set).event),
                          "delay": int(c.get("delay", 0))} for c in s.companions]
        events["%s:%s" % (NAMESPACE, s.event)] = e
    heroes = {}
    cues = {}
    for sid, st in sorted(cat.sets.items()):
        if st.is_cue:
            cues = _resolve_map(cat, st, st.data.get("cues", {}))
            continue
        if st.hero:
            heroes[sid] = _resolve_map(cat, st, st.hero)
    return {"format": INDEX_FORMAT, "note": "generated by tools/sfxgen (SOUND.md section 7); do not edit",
            "events": events, "cues": cues, "heroes": heroes}


def _resolve_map(cat, st, m):
    """Replace sound names with full event ids inside a hero or cue map (strings that name a sound of the set or
    `set:name`; vanilla ids and numbers pass through)."""
    if isinstance(m, dict):
        return {k: _resolve_map(cat, st, v) for k, v in m.items()}
    if isinstance(m, list):
        return [_resolve_map(cat, st, v) for v in m]
    if isinstance(m, str) and not m.startswith("minecraft:"):
        s = cat.lookup(m, st.id)
        if s is not None:
            return "%s:%s" % (NAMESPACE, s.event)
    return m


def plan(cat, src=SRC, jobs=None):
    """Everything `build` writes: {absolute path: bytes}."""
    rendered = render_all(cat, src, jobs)
    files = {}
    for ev, k, stem, rate, data, sec in rendered:
        files[os.path.join(SOUNDS_DIR, stem + ".ogg")] = data
    files[SOUNDS_JSON] = dumps(sounds_json(cat, rendered)).encode("utf-8")
    files[INDEX_JSON] = dumps(index_json(cat, rendered)).encode("utf-8")
    return files, rendered


def owned_files():
    """The generated files on disk (so stale ones can be removed)."""
    out = set()
    hero_dir = os.path.join(SOUNDS_DIR, "hero")
    for root, _dirs, names in os.walk(hero_dir):
        for n in names:
            if n.endswith(".ogg"):
                out.add(os.path.join(root, n))
    for p in (SOUNDS_JSON, INDEX_JSON):
        if os.path.exists(p):
            out.add(p)
    return out


def write(files):
    stale = owned_files() - set(files)
    for p in sorted(stale):
        os.remove(p)
    changed = 0
    for p, data in sorted(files.items()):
        old = open(p, "rb").read() if os.path.exists(p) else None
        if old != data:
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "wb") as f:
                f.write(data)
            changed += 1
    # remove empty folders left by stale files
    hero_dir = os.path.join(SOUNDS_DIR, "hero")
    for root, dirs, names in sorted(os.walk(hero_dir), reverse=True):
        if root != hero_dir and not os.listdir(root):
            os.rmdir(root)
    return changed, len(stale)


def compare(files):
    """Differences between a plan and the files on disk: (missing, different, stale)."""
    missing, different = [], []
    for p, data in sorted(files.items()):
        if not os.path.exists(p):
            missing.append(p)
        elif open(p, "rb").read() != data:
            different.append(p)
    stale = sorted(owned_files() - set(files))
    return missing, different, stale


def rel(p):
    return os.path.relpath(p, os.path.dirname(os.path.dirname(ASSETS)))
