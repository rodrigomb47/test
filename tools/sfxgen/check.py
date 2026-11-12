"""`check`: the quality and consistency rules of SOUND.md section 9.

ERROR fails the check (exit 1); NOTE is advice. Measurements are taken on the SHIPPED files (the .ogg decoded by
ffmpeg), so what is checked is what players hear.
"""
import concurrent.futures as cf
import json
import math
import os
import re

import numpy as np

import analyze
import build
import codec
import recipe
import vanilla
from paths import INDEX_JSON, NAMESPACE, ROOT, SOUNDS_DIR, SOUNDS_JSON

# loudness and level limits (momentary max, LUFS-like; dBFS for peaks)
LOUDNESS_TOL = 1.0          # generated sounds land within this of their target
LIMITED_TOL = 3.0           # ... or this far under it when the peak limiter held them back
LIMITED_PEAK = -2.6         # "held back" = the decoded true peak is within about 1 dB of the -1.5 ceiling
LOUDNESS_WINDOW = (-34.0, -9.0)
PEAK_MAX = -0.3             # decoded true peak (Vorbis overshoots the -1 dB ceiling a little)
DC_MAX = 0.003
EDGE_MAX = -45.0            # first and last decoded sample (dBFS)
HEAD_MAX = -6.0             # peak of the content's first 0.5 ms, relative to the sound's peak (a fade-in exists)
TAIL_MAX = -10.0            # peak of the content's last 1 ms, relative to the sound's peak (a fade-out exists)
SEAM_MAX = 0.05             # loops: |last - first| sample step
SMALL_SPEAKER_ERROR = 18.0  # dB the sound loses without bass (ERROR above: inaudible on laptop speakers)
SMALL_SPEAKER_NOTE = 12.0
SUBTITLE_NOTE = 60
VANILLA_LOUDNESS_NOTE = 8.0  # |effective vanilla loudness - kind target|
PITCH_RANGE = (0.5, 2.0)     # the client clamps play pitch to this
MAX_COMPANION_DEPTH = 2      # a companion may bring its own companions, one more level

# jar budget (KB of Ogg): the slice's shared set and each slice hero (SOUND.md section 11)
BUDGET_KB = {"cue": 320, "hero": 256, "total": 2400}

REQUIRED_CUES = ["telegraph.light", "telegraph.heavy", "telegraph.area", "telegraph.ultimate",
                 "telegraph.unblockable", "telegraph.dive", "parry", "counter", "poise.crack", "poise.break", "stagger",
                 "phase", "shockwave", "enrage", "intro", "yield", "spared", "takeoff", "landing", "glide",
                 "decoy_pop", "weak_point", "channel_break"]
MOVE_SLOTS = ("windup", "release", "impact", "loop", "end", "broken")
MOVE_CLASSES = ("light", "heavy", "area", "ultimate")
EVENT_RE = re.compile(r"^hero\.(cue|voice|[a-z][a-z0-9_]*)\.[a-z0-9_][a-z0-9_.]*$")
ANIM_SOURCES = [os.path.join(ROOT, "herosrc", "anim"), os.path.join(ROOT, "herosrc", "heroes")]


class Report:
    def __init__(self):
        self.errors = []
        self.notes = []

    def error(self, where, msg):
        self.errors.append("ERROR %s: %s" % (where, msg))

    def note(self, where, msg):
        self.notes.append("NOTE  %s: %s" % (where, msg))


def _decode_job(args):
    path, = args
    x, sr = codec.decode(path)
    m = analyze.measure(x, sr)
    m["samples"] = codec.ogg_samples(path)
    return path, x, sr, m, codec.probe_channels(path)


def run(cat, verbose=False, decode=True, jobs=None):
    rep = Report()
    check_catalogue(cat, rep)
    check_markers(cat, rep)
    if decode:
        check_files(cat, rep, jobs)
    for line in rep.errors + (rep.notes if verbose or not rep.errors else rep.notes):
        print(line)
    print("check: %d sounds in %d sets, %d errors, %d notes" % (sum(1 for _ in cat.sounds()), len(cat.sets),
                                                                len(rep.errors), len(rep.notes)))
    return 1 if rep.errors else 0


# ---------------------------------------------------------------- catalogue rules (no audio needed)

def check_catalogue(cat, rep):
    ids, source = vanilla.sound_ids()
    if ids is None:
        rep.note("vanilla", "no vanilla sound list (no game artifacts, no vanilla_sound_ids.txt): ids not checked")
    by_event = cat.by_event()
    for s in cat.sounds():
        w = s.event
        if not EVENT_RE.match(s.event):
            rep.error(w, "event id does not follow hero.<set>.<name>")
        if not s.subtitle_text:
            rep.error(w, "no subtitle text")
        elif len(s.subtitle_text) > SUBTITLE_NOTE:
            rep.note(w, "subtitle longer than %d characters" % SUBTITLE_NOTE)
        lo, hi = s.pitch
        if s.generated:
            if lo < PITCH_RANGE[0] or hi > PITCH_RANGE[1]:
                rep.error(w, "play pitch %s outside %s" % (s.pitch, PITCH_RANGE))
        elif s.alias:
            for a in s.alias:
                r = cat.lookup(a["sound"], s.set)
                if r is None:
                    rep.error(w, "alias of a missing sound %r" % a["sound"])
                elif r.alias:
                    rep.error(w, "alias of an alias (%s): point at the sound itself" % r.event)
                elif r.loop != s.loop:
                    rep.error(w, "alias and target disagree on loop")
                p = float(a.get("pitch", 1.0))
                if p * lo < PITCH_RANGE[0] - 1e-9 or p * hi > PITCH_RANGE[1] + 1e-9:
                    rep.error(w, "pitch %.2f x play %s leaves %s" % (p, s.pitch, PITCH_RANGE))
        else:
            for v in s.vanilla:
                if ids is not None and v["event"] not in ids:
                    rep.error(w, "unknown vanilla sound event %s (checked against %s)" % (v["event"], source))
                p = float(v.get("pitch", 1.0))
                if p * lo < PITCH_RANGE[0] - 1e-9 or p * hi > PITCH_RANGE[1] + 1e-9:
                    rep.error(w, "pitch %.2f x play %s leaves %s" % (p, s.pitch, PITCH_RANGE))
            if s.range != 16 and s.range > 16 * max(1.0, s.volume[1]) + 1e-6:
                rep.note(w, "a vanilla reference carries 16 x volume blocks (%.0f), not range %d" % (
                    16 * max(1.0, s.volume[1]), s.range))
        for c in s.companions:
            if cat.lookup(c["sound"], s.set) is None:
                rep.error(w, "companion %r does not exist" % c["sound"])
        depth = _companion_depth(cat, s, ())
        if depth is None:
            rep.error(w, "companions form a cycle")
        elif depth > MAX_COMPANION_DEPTH:
            rep.error(w, "companions nest %d deep (at most %d)" % (depth, MAX_COMPANION_DEPTH))
        if s.generated:
            for ref in _uses(s.spec.get("layers", [])):
                r = cat.lookup(ref, s.set)
                if r is None or not r.generated:
                    rep.error(w, "use of %r, which is not a generated sound" % ref)
        if s.loop and not s.generated:
            rep.error(w, "a loop must be generated (seamless)")
        if s.kind == "voice" and s.generated:
            if s.variants < 4:
                rep.error(w, "a voice needs at least 4 syllable variants")
            if s.spec["layers"] and any(float(l.get("dur", 0.1)) > 0.25 for l in s.spec["layers"]):
                rep.error(w, "a voice syllable is at most 0.25 s")
    # sets
    for sid, st in sorted(cat.sets.items()):
        if st.is_cue:
            cues = st.data.get("cues", {})
            flat = dict(_flatten(cues))
            for k in REQUIRED_CUES:
                if k not in flat:
                    rep.error("cue", "the shared cue map has no %s" % k)
            for k, v in flat.items():
                if cat.lookup(v, "cue") is None:
                    rep.error("cue", "cue %s names a missing sound %r" % (k, v))
            continue
        check_hero(cat, st, rep, ids)
    # previews name sounds that exist
    for sid, st in sorted(cat.sets.items()):
        for n in st.preview or []:
            if n not in st.sounds:
                rep.error(sid, "preview lists a missing sound %r" % n)


def _companion_depth(cat, s, seen):
    """How deep a sound's companions nest (0 = none); None on a cycle."""
    if s.event in seen:
        return None
    best = 0
    for c in s.companions:
        ref = cat.lookup(c["sound"], s.set)
        if ref is None:
            continue
        d = _companion_depth(cat, ref, seen + (s.event,))
        if d is None:
            return None
        best = max(best, d + 1)
    return best


def _uses(layers):
    for l in layers:
        if l.get("type") == "use":
            yield l["sound"]
        if l.get("type") in ("group", "seq"):
            yield from _uses(l.get("layers", []))


def _flatten(m, prefix=""):
    for k, v in m.items():
        key = prefix + k
        if isinstance(v, dict):
            yield from _flatten(v, key + ".")
        else:
            yield key, v


def check_hero(cat, st, rep, ids):
    w = st.id
    h = st.hero or {}
    if not h:
        rep.error(w, "a hero set needs its hero map (voice, moves, phases...)")
        return
    if "voice" not in st.sounds:
        rep.error(w, "no voice (sound named voice: hero.voice.%s)" % st.id)
    v = h.get("voice", {})
    if v.get("talk") != "voice":
        rep.error(w, "hero.voice.talk must name the voice sound")
    mel = v.get("melody", [0])
    if not isinstance(mel, list) or not mel or any(abs(float(m)) > 12 for m in mel):
        rep.error(w, "hero.voice.melody must list semitone steps within an octave")
    if not 2 <= int(v.get("rate", 0)) <= 4:
        rep.error(w, "hero.voice.rate must be 2-4 ticks per syllable (HEROES_V2 12.14)")
    if v.get("fallback_blip") not in ("harp", "bell", "chime", "flute", "xylophone", "bit", "hat", "bass", "guitar",
                                      "pling"):
        rep.error(w, "hero.voice.fallback_blip must be a note-block instrument of 12.14")
    for key in ("hurt", "death"):
        e = v.get(key, {})
        if ids is not None and e.get("event") not in ids:
            rep.error(w, "hero.voice.%s needs a vanilla event" % key)

    def ref_ok(where, ref):
        if isinstance(ref, list):
            for r in ref:
                ref_ok(where, r)
            return
        if not isinstance(ref, str):
            rep.error(w, "%s: expected a sound name" % where)
            return
        if ref.startswith("minecraft:"):
            if ids is not None and ref not in ids:
                rep.error(w, "%s: unknown vanilla event %s" % (where, ref))
        elif cat.lookup(ref, st.id) is None:
            rep.error(w, "%s names a missing sound %r" % (where, ref))

    for name, e in v.get("emotes", {}).items():
        ref_ok("voice.emotes.%s" % name, e)
    moves = h.get("moves", {})
    if not moves:
        rep.error(w, "no moves")
    for mid, m in moves.items():
        where = "moves.%s" % mid
        if m.get("class") not in MOVE_CLASSES:
            rep.error(w, "%s: class must be one of %s" % (where, ", ".join(MOVE_CLASSES)))
        for slot in m:
            if slot == "class":
                continue
            if slot not in MOVE_SLOTS:
                rep.error(w, "%s: unknown slot %r" % (where, slot))
                continue
            ref_ok("%s.%s" % (where, slot), m[slot])
        if m.get("class") in ("heavy", "area", "ultimate") and "windup" not in m:
            rep.note(w, "%s: a %s move has no hero wind-up sound (the class cue plays alone)" % (where, m["class"]))
        if not any(k in m for k in ("release", "impact")):
            rep.note(w, "%s: neither release nor impact" % where)
        if m.get("class") == "ultimate":
            wu = cat.lookup(m.get("windup", ""), st.id) if isinstance(m.get("windup"), str) else None
            if wu is not None and wu.range < 32 and not (not wu.generated and wu.volume[1] >= 2.0):
                rep.note(w, "%s: an ultimate wind-up should carry at least 32 blocks" % where)
    for group in ("states", "phases", "passives", "weak_points", "quirks", "mobility"):
        for k, ref in h.get(group, {}).items():
            ref_ok("%s.%s" % (group, k), ref)
    if "intro" in h:
        ref_ok("intro", h["intro"])
    phases = h.get("phases", {})
    for ph in ("2", "3"):
        if ph not in phases:
            rep.note(w, "no phase %s sound" % ph)
    steps = h.get("steps", {})
    if "with" in steps:
        ref_ok("steps.with", steps["with"])
    # the hero's own source file, once it exists: every move has an entry
    hero_file = os.path.join(ROOT, "herosrc", "heroes", st.id + ".hero.json")
    if os.path.exists(hero_file):
        try:
            hd = json.load(open(hero_file, encoding="utf-8"))
            ids_in_file = [m.get("id") for m in hd.get("moves", []) if isinstance(m, dict)]
            ult = hd.get("ultimate", {})
            if isinstance(ult, dict) and ult.get("id"):
                ids_in_file.append(ult["id"])
            for mid in ids_in_file:
                if mid and mid not in moves:
                    rep.note(w, "move %s of %s has no sounds" % (mid, os.path.basename(hero_file)))
            hv = hd.get("voice") or {}
            if isinstance(hv, dict) and hv.get("rate") is not None and int(hv["rate"]) != int(v.get("rate", 0)):
                rep.note(w, "voice rate %s here, %s in the hero file (one rate drives both voices)" % (
                    v.get("rate"), hv["rate"]))
        except (ValueError, OSError) as e:
            rep.note(w, "could not read %s: %s" % (hero_file, e))
    else:
        rep.note(w, "no herosrc/heroes/%s.hero.json yet: moves checked against the sound map only" % st.id)


# ---------------------------------------------------------------- markers in animation recipes and hero files

SFX_RE = re.compile(r"sfx:([a-z0-9_:.\-/]+)")


def marker_refs():
    """[(file, sound id)] for every `"sound": "<id>"` and every `sfx:<id>` marker part in herosrc/anim and
    herosrc/heroes."""
    out = []
    for base in ANIM_SOURCES:
        for root, _d, names in os.walk(base):
            for n in sorted(names):
                if not n.endswith(".json"):
                    continue
                p = os.path.join(root, n)
                try:
                    data = json.load(open(p, encoding="utf-8"))
                except ValueError:
                    continue
                for key, val in _walk(data):
                    if key == "sound" and isinstance(val, str):
                        out.append((p, val))
                    if isinstance(val, str):
                        for m in SFX_RE.finditer(val):
                            out.append((p, m.group(1)))
    return out


def _walk(o, key=None):
    if isinstance(o, dict):
        for k, v in o.items():
            yield from _walk(v, k)
    elif isinstance(o, list):
        for v in o:
            yield from _walk(v, key)
    else:
        yield key, o


def check_markers(cat, rep):
    ids, _src = vanilla.sound_ids()
    events = {"%s:%s" % (NAMESPACE, e) for e in cat.by_event()}
    for path, ref in marker_refs():
        where = os.path.relpath(path, ROOT)
        if ":" not in ref:
            ref = "%s:%s" % (NAMESPACE, ref)
        if ref.startswith("minecraft:"):
            if ids is not None and ref not in ids:
                rep.error(where, "sound %s is not a 1.21.1 sound event" % ref)
        elif ref.startswith(NAMESPACE + ":"):
            if ref not in events:
                rep.error(where, "sound %s is not in the catalogue (herosrc/sfx)" % ref)
        else:
            rep.error(where, "sound %s: only minecraft: and factionclash: sounds" % ref)


# ---------------------------------------------------------------- the shipped files

def check_files(cat, rep, jobs=None):
    # sounds.json and the files the catalogue implies
    expect = {}
    for s in cat.sounds():
        for k, stem in enumerate(s.file_names()):
            expect[os.path.join(SOUNDS_DIR, stem + ".ogg")] = (s, k)
    on_disk = build.owned_files() - {SOUNDS_JSON, INDEX_JSON}
    for p in sorted(set(expect) - on_disk):
        rep.error(build.rel(p), "missing: run sfxgen build")
    for p in sorted(on_disk - set(expect)):
        rep.error(build.rel(p), "stale file: run sfxgen build")
    want_sounds = build.dumps(build.sounds_json(cat, [])).encode("utf-8")
    if not os.path.exists(SOUNDS_JSON) or open(SOUNDS_JSON, "rb").read() != want_sounds:
        rep.error("sounds.json", "out of date: run sfxgen build")
    paths = sorted(p for p in expect if p in on_disk)
    jobs = jobs or min(8, os.cpu_count() or 1)
    results = {}
    if jobs > 1 and len(paths) > 8:
        with cf.ProcessPoolExecutor(max_workers=jobs) as ex:
            for path, x, sr, m, ch in ex.map(_decode_job, [(p,) for p in paths]):
                results[path] = (x, sr, m, ch)
    else:
        for p in paths:
            path, x, sr, m, ch = _decode_job((p,))
            results[path] = (x, sr, m, ch)
    rendered = []
    renderer = recipe.Renderer(cat)
    for p in paths:
        s, k = expect[p]
        x, sr, m, ch = results[p]
        synth = renderer.final(s, k)[0] if s.loop else None
        check_audio(s, k, x, sr, m, ch, rep, synth)
        rendered.append((s.event, k, os.path.relpath(p, SOUNDS_DIR)[:-4], sr, b"", m["samples"] / sr))
    if not rep.errors:
        want_index = build.dumps(build.index_json(cat, rendered)).encode("utf-8")
        if not os.path.exists(INDEX_JSON) or open(INDEX_JSON, "rb").read() != want_index:
            rep.error("hero_sfx.json", "out of date: run sfxgen build")
    check_budget(cat, expect, rep)
    check_vanilla_levels(cat, rep)
    check_telegraph_distinct(cat, results, expect, rep)


def check_audio(s, k, x, sr, m, ch, rep, synth=None):
    """Rules on one decoded file. `m["samples"]` is the stream's exact length (Ogg granule); for a loop, `synth` is
    the rendered buffer, whose seam is checked (ffmpeg's decoder drops the last partial packet, so the decoded end
    of a loop is not its real end)."""
    w = "%s#%d" % (s.event, k + 1)
    if ch != 1:
        rep.error(w, "not mono (%d channels): positional sounds must be mono" % ch)
    if sr not in (44100, 22050):
        rep.error(w, "sample rate %d (44100 or 22050)" % sr)
    dur = m.get("samples", len(x)) / float(sr)
    if s.loop:
        want = float(s.spec.get("length", dur))
        if abs(dur - want) > 0.01:
            rep.error(w, "loop length %.3f s, the recipe says %.3f s" % (dur, want))
        y = x if synth is None else synth
        step = abs(float(y[-1]) - float(y[0]))
        if step > SEAM_MAX:
            rep.error(w, "loop seam steps by %.3f" % step)
        n = min(len(y) // 2, int(0.05 * sr))
        seam = np.concatenate([y[-n:], y[:n]])
        if analyze.clicks(seam, sr):
            rep.error(w, "loop seam clicks")
    else:
        content = dur - (recipe.LEAD_SAMPLES + recipe.TAIL_SAMPLES) / float(sr)
        if content > s.max_dur + 1e-6:
            rep.error(w, "%.2f s is longer than the %s limit of %.1f s" % (content, s.kind, s.max_dur))
        e = m["edges"]
        if e["first"] > EDGE_MAX or e["last"] > EDGE_MAX:
            rep.error(w, "edge sample too loud (first %.1f, last %.1f dBFS): a click at the start or end" % (
                e["first"], e["last"]))
        if e["head"] > HEAD_MAX:
            rep.error(w, "no fade-in (the first 0.5 ms reach %.1f dB of the peak)" % e["head"])
        if e["tail"] > TAIL_MAX:
            rep.error(w, "no fade-out (the last 1 ms is at %.1f dB of the peak)" % e["tail"])
        if m["clicks"] and not s.spec.get("post", {}).get("transients"):
            rep.error(w, "clicks inside the sound at %s s" % ", ".join("%.3f" % c for c in m["clicks"][:5]))
    if m["true_peak_db"] > PEAK_MAX:
        rep.error(w, "true peak %.2f dBFS (max %.1f): clipping risk" % (m["true_peak_db"], PEAK_MAX))
    if abs(m["dc"]) > DC_MAX:
        rep.error(w, "DC offset %.4f" % m["dc"])
    lm = m["lufs_m"]
    if not LOUDNESS_WINDOW[0] <= lm <= LOUDNESS_WINDOW[1]:
        rep.error(w, "loudness %.1f outside %s" % (lm, LOUDNESS_WINDOW))
    diff = lm - s.loudness
    limited = m["true_peak_db"] >= LIMITED_PEAK and -LIMITED_TOL <= diff < 0
    if abs(diff) > LOUDNESS_TOL and not limited:
        rep.error(w, "loudness %.1f, target %.1f" % (lm, s.loudness))
    drop = m["small_speaker_drop"]
    if drop > SMALL_SPEAKER_ERROR:
        rep.error(w, "%.1f dB quieter without bass: inaudible on small speakers" % drop)
    elif drop > SMALL_SPEAKER_NOTE:
        rep.note(w, "%.1f dB quieter without bass (small speakers)" % drop)

    if sr == 44100 and recipe.high_energy_db(x, sr) < -50 and s.rate == 44100:
        rep.note(w, "almost nothing above 9.5 kHz: rate 22050 (or auto) would halve the file")


def check_budget(cat, expect, rep):
    per = {}
    for p, (s, k) in expect.items():
        if os.path.exists(p):
            per[s.set] = per.get(s.set, 0) + os.path.getsize(p)
    total = sum(per.values())
    for sid, size in sorted(per.items()):
        cap = BUDGET_KB["cue"] if sid == "cue" else BUDGET_KB["hero"]
        if size > cap * 1024:
            rep.error(sid, "%.0f KB of Ogg, over the %d KB budget" % (size / 1024.0, cap))
    if total > BUDGET_KB["total"] * 1024:
        rep.error("budget", "%.0f KB of Ogg in all, over %d KB" % (total / 1024.0, BUDGET_KB["total"]))


def check_vanilla_levels(cat, rep):
    """NOTE when a vanilla reference plays far from its kind's loudness (measured from the game files in memory)."""
    if vanilla.game_sounds_json() is None:
        return
    cache = {}
    for s in cat.sounds():
        if s.generated or s.alias:
            continue
        levels = []
        for v in s.vanilla:
            ev = v["event"]
            if ev not in cache:
                cache[ev] = [(analyze.loudness(x, 44100)[0], vol) for x, vol, pt in vanilla.vanilla_samples(ev)]
            for lm, vol in cache[ev]:
                # the game multiplies vanilla's own volume, our entry's and the play volume, then clamps to 1
                gain = min(1.0, vol * float(v.get("volume", 1.0)) * s.volume[1])
                levels.append(lm + analyze.db(max(gain, 1e-6)))
        if levels:
            eff = float(np.mean(levels))
            target = recipe.KINDS[s.kind]["loudness"]
            if abs(eff - target) > VANILLA_LOUDNESS_NOTE:
                rep.note(s.event, "vanilla plays at about %.1f, the %s target is %.1f" % (eff, s.kind, target))


def features(x, sr):
    """A small timbre-and-shape vector: log energy in 8 octave bands, normalised, plus duration and attack."""
    n = 1 << int(math.ceil(math.log2(max(len(x), 2))))
    p = np.abs(np.fft.rfft(x * np.hanning(len(x)), n)) ** 2
    f = np.fft.rfftfreq(n, 1.0 / sr)
    edges = [60, 120, 250, 500, 1000, 2000, 4000, 8000, 16000]
    e = np.array([np.sum(p[(f >= a) & (f < b)]) for a, b in zip(edges, edges[1:])]) + 1e-12
    e = 10 * np.log10(e / np.sum(e))
    env = analyze.envelope(x, sr)
    peak_pos = float(np.argmax(env)) / max(len(env), 1)
    return np.concatenate([e / 10.0, [len(x) / sr, peak_pos]])


def check_telegraph_distinct(cat, results, expect, rep):
    """The telegraph class cues must differ audibly from each other (6.4): spectral shape, length and where the peak
    sits. NOTE when two are closer than 1.0 in that feature space."""
    names = ["telegraph.light", "telegraph.heavy", "telegraph.area", "telegraph.unblockable", "telegraph.ultimate",
             "dive"]
    feats = {}
    for p, (s, k) in expect.items():
        if s.set == "cue" and s.name in names and k == 0 and p in results:
            x, sr, _m, _c = results[p]
            feats[s.name] = features(x, sr)
    keys = sorted(feats)
    for i, a in enumerate(keys):
        for b in keys[i + 1:]:
            d = float(np.linalg.norm(feats[a] - feats[b]))
            if d < 1.0:
                rep.note("cue", "%s and %s sound alike (distance %.2f)" % (a, b, d))
