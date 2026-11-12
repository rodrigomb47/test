"""The sound catalogue: reading `herosrc/sfx/<set>.sfx.json`, naming events, resolving variants and rendering a
generated sound to samples. SOUND.md section 4 is the format reference; this module is its implementation.

A file:
    {"format": 1, "set": "cue" | "<hero id>", "title": "...", "defaults": {...},
     "sounds": {"<name>": {<sound>}, ...},
     "hero": {voice, moves, phases, states, passives, weak_points, quirks, intro, steps}   (hero files only),
     "preview": ["<name>", ...]}

A sound is generated ("layers": [...], optional "fx", "variants", "vary", "each"), a vanilla reference
("vanilla": [{"event": "minecraft:...", "pitch": p, "volume": v, "weight": w}]) or an alias of other catalogue
sounds ("alias": [{"sound": "set:name", "pitch": p, ...}], zero bytes: a `type: event` entry in sounds.json). Common fields: kind, subtitle,
plays (server | client), category, range, pitch / volume (play-time random ranges), loudness (generated only), rate,
loop, with (companions: [{"sound": name, "delay": ticks}]), note.
"""
import copy
import hashlib
import json
import math
import os
import re

import numpy as np

import dsp
import synth
from paths import SRC

FORMAT = 1
SR = 44100

KINDS = {
    # kind: default loudness (momentary max, LUFS-like), SoundSource category, who plays it, attenuation range
    # (blocks), longest allowed length (s)
    "voice": dict(loudness=-24.0, category="voice", plays="client", range=12, max_dur=0.3),
    "emote": dict(loudness=-21.0, category="voice", plays="client", range=16, max_dur=2.0),
    "swing": dict(loudness=-21.0, category="hostile", plays="client", range=16, max_dur=1.2),
    "cast": dict(loudness=-20.0, category="hostile", plays="client", range=16, max_dur=2.5),
    "hit": dict(loudness=-17.0, category="hostile", plays="server", range=16, max_dur=1.6),
    "heavy": dict(loudness=-13.0, category="hostile", plays="server", range=24, max_dur=3.0),
    "windup": dict(loudness=-19.0, category="hostile", plays="server", range=24, max_dur=2.5),
    "telegraph": dict(loudness=-18.0, category="hostile", plays="server", range=24, max_dur=2.0),
    "sting": dict(loudness=-13.0, category="hostile", plays="server", range=32, max_dur=4.0),
    "chime": dict(loudness=-18.0, category="hostile", plays="server", range=24, max_dur=3.0),
    "mobility": dict(loudness=-17.0, category="neutral", plays="server", range=16, max_dur=2.0),
    "foley": dict(loudness=-27.0, category="neutral", plays="client", range=12, max_dur=2.0),
    "quirk": dict(loudness=-25.0, category="neutral", plays="client", range=12, max_dur=2.5),
    "loop": dict(loudness=-26.0, category="hostile", plays="client", range=16, max_dur=4.0),
}
POST_DEFAULTS = {
    # long reverberant kinds end where the tail is 48 dB under the peak, with a longer fade, so stings do not ring
    # over the next beat and stay small in the jar
    "sting": {"trim": -46.0, "fade_out": 0.35},
    "chime": {"trim": -48.0, "fade_out": 0.3},
    "telegraph": {"trim": -50.0, "fade_out": 0.12},
    "heavy": {"trim": -50.0, "fade_out": 0.15},
}
CATEGORIES = ("hostile", "neutral", "voice", "players", "ambient", "master")
PLAYERS = ("server", "client")
NAME_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z0-9_]+)*$")
SET_RE = re.compile(r"^(cue|[a-z][a-z0-9_]{1,40})$")
SOUND_FIELDS = {"kind", "subtitle", "plays", "category", "range", "pitch", "volume", "loudness", "rate", "loop",
                "with", "note", "layers", "fx", "variants", "vary", "vanilla", "event", "preload", "seed", "post",
                "loop_crossfade", "length", "each", "alias"}
LAYER_COMMON = {"type", "at", "gain", "pitch", "fx", "seed", "note", "hard"}
MAX_VARIANTS = 12
MAX_COMPANIONS = 2


AUTO_22K_DB = -32.0  # rate "auto": 22.05 kHz when the energy above 9.5 kHz is this far under the total


class RecipeError(ValueError):
    pass


def event_id(set_id, name):
    """The sound event path (without the namespace) for a sound of a set: hero.cue.<name>, hero.<id>.<name>, and
    hero.voice.<id> for a hero's sound named "voice"."""
    if set_id == "cue":
        return "hero.cue." + name
    if name == "voice":
        return "hero.voice." + set_id
    return "hero.%s.%s" % (set_id, name)


def file_stem(set_id, name):
    return "hero/%s/%s" % (set_id, name.replace(".", "_"))


def seed_of(*parts):
    h = hashlib.sha256("#".join(str(p) for p in parts).encode("utf-8")).digest()
    return int.from_bytes(h[:8], "little")


class Sound:
    def __init__(self, set_id, name, spec, defaults):
        self.set = set_id
        self.name = name
        self.spec = spec
        merged = dict(defaults)
        merged.update(spec)
        self.kind = merged.get("kind", "hit")
        if self.kind not in KINDS:
            raise RecipeError("%s.%s: unknown kind %r" % (set_id, name, self.kind))
        kd = KINDS[self.kind]
        self.event = event_id(set_id, name)
        self.subtitle_text = merged.get("subtitle")
        self.plays = merged.get("plays", kd["plays"])
        self.category = merged.get("category", kd["category"])
        self.range = int(merged.get("range", kd["range"]))
        self.pitch = list(merged.get("pitch", [1.0, 1.0]))
        self.volume = list(merged.get("volume", [1.0, 1.0]))
        self.loudness = float(merged.get("loudness", kd["loudness"]))
        self.rate = merged.get("rate", "auto")
        self.loop = bool(merged.get("loop", False))
        self.companions = list(merged.get("with", []))
        self.preload = bool(merged.get("preload", self.kind in ("voice", "telegraph")))
        self.generated = "layers" in spec
        self.vanilla = spec.get("vanilla")
        self.alias = spec.get("alias")
        self.variants = int(spec.get("variants", 1)) if self.generated else 0
        self.max_dur = kd["max_dur"]
        self.note = spec.get("note")

    @property
    def subtitle_key(self):
        return "subtitles.factionclash." + self.event

    def file_names(self):
        if not self.generated:
            return []
        stem = file_stem(self.set, self.name)
        if self.variants == 1:
            return [stem]
        return ["%s_%d" % (stem, i + 1) for i in range(self.variants)]


class SoundSet:
    def __init__(self, path, data):
        self.path = path
        self.data = data
        self.id = data.get("set")
        self.title = data.get("title", self.id)
        self.defaults = data.get("defaults", {})
        self.sounds = {}
        for name, spec in data.get("sounds", {}).items():
            self.sounds[name] = Sound(self.id, name, spec, self.defaults)
        self.hero = data.get("hero")
        self.preview = data.get("preview")

    @property
    def is_cue(self):
        return self.id == "cue"


def load_file(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    problems = validate_file(data, path)
    if problems:
        raise RecipeError("\n".join(problems))
    return SoundSet(path, data)


def set_paths(src=SRC):
    if not os.path.isdir(src):
        return []
    return sorted(os.path.join(src, n) for n in os.listdir(src) if n.endswith(".sfx.json"))


def load_all(src=SRC):
    sets = [load_file(p) for p in set_paths(src)]
    ids = [s.id for s in sets]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        raise RecipeError("duplicate set ids: %s" % ", ".join(sorted(dup)))
    return Catalogue(sets)


class Catalogue:
    def __init__(self, sets):
        self.sets = {s.id: s for s in sets}

    def sounds(self):
        for sid in sorted(self.sets):
            for name in self.sets[sid].sounds:
                yield self.sets[sid].sounds[name]

    def lookup(self, ref, from_set):
        """`name` (same set) or `set:name`."""
        sid, name = ref.split(":", 1) if ":" in ref else (from_set, ref)
        s = self.sets.get(sid)
        return s.sounds.get(name) if s else None

    def by_event(self):
        return {s.event: s for s in self.sounds()}


# ---------------------------------------------------------------- validation (structure; check.py does the rest)

def validate_file(data, path="<file>"):
    p = []
    where = os.path.basename(path)
    if data.get("format") != FORMAT:
        p.append("%s: format must be %d" % (where, FORMAT))
    sid = data.get("set")
    if not isinstance(sid, str) or not SET_RE.match(sid):
        p.append("%s: bad set id %r" % (where, sid))
        return p
    exp = sid + ".sfx.json"
    if os.path.basename(path) != exp and path != "<file>":
        p.append("%s: a set's file must be named %s" % (where, exp))
    sounds = data.get("sounds")
    if not isinstance(sounds, dict) or not sounds:
        p.append("%s: no sounds" % where)
        return p
    for name, spec in sounds.items():
        w = "%s %s" % (where, name)
        if not NAME_RE.match(name):
            p.append("%s: bad sound name (lower case, digits, _ and . only)" % w)
        if name == "voice" and sid == "cue":
            p.append("%s: the cue set has no voice" % w)
        unknown = set(spec) - SOUND_FIELDS
        if unknown:
            p.append("%s: unknown fields %s" % (w, ", ".join(sorted(unknown))))
        gen, van, ali = "layers" in spec, "vanilla" in spec, "alias" in spec
        if gen + van + ali != 1:
            p.append("%s: give exactly one of layers (generated), vanilla (a vanilla event) or alias (another "
                     "sound of the catalogue)" % w)
        if ali:
            if not isinstance(spec["alias"], list) or not spec["alias"]:
                p.append("%s: alias must be a non-empty list" % w)
            else:
                for a in spec["alias"]:
                    if not isinstance(a, dict) or not a.get("sound") or set(a) - {"sound", "pitch", "volume", "weight"}:
                        p.append("%s: an alias entry is {sound, pitch, volume, weight}" % w)
        if not spec.get("subtitle") and not data.get("defaults", {}).get("subtitle"):
            p.append("%s: no subtitle text" % w)
        kind = spec.get("kind", data.get("defaults", {}).get("kind", "hit"))
        if kind not in KINDS:
            p.append("%s: unknown kind %r" % (w, kind))
        for key in ("pitch", "volume"):
            v = spec.get(key)
            if v is not None and (not isinstance(v, list) or len(v) != 2 or v[0] > v[1] or v[0] <= 0):
                p.append("%s: %s must be [low, high] with 0 < low <= high" % (w, key))
        if spec.get("plays", "server") not in PLAYERS:
            p.append("%s: plays must be server or client" % w)
        if spec.get("category", "hostile") not in CATEGORIES:
            p.append("%s: unknown category %r" % (w, spec.get("category")))
        if gen:
            v = spec.get("variants", 1)
            if not isinstance(v, int) or not 1 <= v <= MAX_VARIANTS:
                p.append("%s: variants must be 1-%d" % (w, MAX_VARIANTS))
            if "each" in spec and (not isinstance(spec["each"], list) or len(spec["each"]) != v):
                p.append("%s: each must list one override object per variant" % w)
            if spec.get("rate", "auto") not in ("auto", 44100, 22050):
                p.append("%s: rate must be auto, 44100 or 22050" % w)
            _validate_layers(spec["layers"], w, p)
        if van:
            if not isinstance(spec["vanilla"], list) or not spec["vanilla"]:
                p.append("%s: vanilla must be a non-empty list" % w)
            else:
                for v in spec["vanilla"]:
                    if not isinstance(v, dict) or not str(v.get("event", "")).startswith("minecraft:"):
                        p.append("%s: a vanilla entry needs an event minecraft:<id>" % w)
                    elif set(v) - {"event", "pitch", "volume", "weight"}:
                        p.append("%s: unknown vanilla fields %s" % (w, sorted(set(v) - {"event", "pitch", "volume",
                                                                                         "weight"})))
            for k in ("loudness", "variants", "vary", "fx", "rate"):
                if k in spec:
                    p.append("%s: %s applies to generated sounds only" % (w, k))
        comp = spec.get("with", [])
        if not isinstance(comp, list) or len(comp) > MAX_COMPANIONS:
            p.append("%s: with takes at most %d companions" % (w, MAX_COMPANIONS))
        else:
            for c in comp:
                if not isinstance(c, dict) or "sound" not in c or not 0 <= int(c.get("delay", 0)) <= 10:
                    p.append("%s: a companion is {sound, delay 0-10 ticks}" % w)
    return p


def _validate_layers(layers, w, p):
    if not isinstance(layers, list) or not layers:
        p.append("%s: layers must be a non-empty list" % w)
        return
    for i, l in enumerate(layers):
        t = l.get("type")
        if t not in synth.INSTRUMENTS and t not in synth.SPECIAL:
            p.append("%s layer %d: unknown type %r" % (w, i, t))
        if t in ("group", "seq"):
            _validate_layers(l.get("layers", []), "%s layer %d" % (w, i), p)
        if t == "use" and not l.get("sound"):
            p.append("%s layer %d: use needs a sound" % (w, i))


# ---------------------------------------------------------------- variants

def _set_path(spec, path, value):
    parts = path.split(".")
    cur = spec
    for k in parts[:-1]:
        cur = cur[int(k)] if isinstance(cur, list) else cur[k]
    last = parts[-1]
    if isinstance(cur, list):
        cur[int(last)] = value
    else:
        cur[last] = value


def variant_spec(sound, k):
    """The spec of variant k (0-based): `each[k]` (explicit per-variant overrides, dotted paths) applies first; then
    each `vary` key gets an evenly spread value, the order shuffled per key (deterministically), so N variants cover
    the range without two sharing a value. "pitch" is special in both: a whole-sound shift in semitones applied
    after rendering (the two add)."""
    spec = copy.deepcopy(sound.spec)
    vary = spec.pop("vary", {}) or {}
    each = spec.pop("each", None)
    if each:
        for key, val in sorted(each[k].items()):
            if key == "pitch":
                continue
            _set_path(spec, key, copy.deepcopy(val))
    n = max(sound.variants, 1)
    shift = 0.0
    for key in sorted(vary):
        lo, hi = (float(v) for v in vary[key])
        rng = np.random.default_rng(seed_of(sound.event, "vary", key))
        perm = rng.permutation(n)
        val = lo + (hi - lo) * (perm[k] + 0.5) / n
        if key == "pitch":
            shift = val
        else:
            _set_path(spec, key, round(val, 6))
    if each and "pitch" in each[k]:
        shift += float(each[k]["pitch"])
    return spec, shift


# ---------------------------------------------------------------- rendering

def apply_fx(x, fx_list, ctx, where):
    for i, fx in enumerate(fx_list or []):
        x = _one_fx(x, fx, ctx, "%s fx %d" % (where, i))
    return x


FILTER_KEYS = ("lp", "hp", "bp", "notch", "peak", "lowshelf", "highshelf")


def _one_fx(x, fx, ctx, where):
    sr = ctx.sr
    for kind in FILTER_KEYS:
        if kind in fx:
            f = fx[kind]
            q = fx.get("q", 0.7071)
            if isinstance(f, (int, float)) and isinstance(q, (int, float)):
                return dsp.biquad(x, kind, f, sr, q, float(fx.get("gain", 0.0)))
            if kind in ("lowshelf", "highshelf"):
                raise RecipeError("%s: shelves cannot sweep" % where)
            return dsp.svf(x, dsp.param(f, len(x), sr), sr, dsp.param(q, len(x), sr), kind)
    if "drive" in fx:
        return dsp.drive(x / (np.max(np.abs(x)) or 1.0), float(fx["drive"]), fx.get("mode", "tanh")) * (
            np.max(np.abs(x)) or 1.0)
    if "crush" in fx:
        pk = np.max(np.abs(x)) or 1.0
        return dsp.crush(x / pk, int(fx["crush"]), int(fx.get("hold", 1))) * pk
    if "reverb" in fx:
        r = fx["reverb"]
        return dsp.reverb(x, sr, ctx.rng, float(r.get("size", 1.2)), float(r.get("damp", 0.5)),
                          float(r.get("mix", 0.25)), float(r.get("predelay", 0.012)))
    if "echo" in fx:
        e = fx["echo"]
        return dsp.echo(x, sr, float(e.get("delay", 0.12)), float(e.get("feedback", 0.35)), int(e.get("count", 3)),
                        float(e.get("damp", 4000)), float(e.get("mix", 0.35)))
    if "trem" in fx:
        t = fx["trem"]
        return dsp.tremolo(x, sr, float(t.get("rate", 8)), float(t.get("depth", 0.5)), t.get("shape", "sine"))
    if "ring" in fx:
        r = fx["ring"]
        return dsp.ring(x, sr, float(r.get("freq", 30)), float(r.get("mix", 1.0)))
    if "env" in fx:
        return x * dsp.envelope(fx["env"], len(x), sr)
    if "gain" in fx:
        return x * 10 ** (float(fx["gain"]) / 20.0)
    if "speed" in fx:
        return dsp.resample(x, float(fx["speed"]))
    if "bend" in fx:
        return dsp.pitch_curve_resample(x, dsp.param(fx["bend"], len(x), sr))
    if "reverse" in fx:
        return x[::-1].copy() if fx["reverse"] else x
    if "fade" in fx:
        a, b = fx["fade"]
        return dsp.fade(x, sr, float(a), float(b))
    if "pad" in fx:
        a, b = fx["pad"]
        return np.concatenate([np.zeros(int(float(a) * sr)), x, np.zeros(int(float(b) * sr))])
    if "len" in fx:
        n = int(float(fx["len"]) * sr)
        return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])
    raise RecipeError("%s: unknown effect %s" % (where, sorted(fx)))


class Renderer:
    """Renders sounds of a catalogue; `raw(sound, k)` is the processed 44.1 kHz buffer (cached), `final(sound, k)`
    the shipped samples and rate."""

    def __init__(self, catalogue):
        self.cat = catalogue
        self._raw = {}
        self._stack = []

    def _layer(self, layer, ctx_seed, sound, path):
        t = layer["type"]
        rng = np.random.default_rng(seed_of(sound.event, ctx_seed, path, layer.get("seed", 0)))
        ctx = synth.Ctx(SR, rng)
        if t in ("group", "seq"):
            parts = [(self._layer(l, ctx_seed, sound, "%s.%d" % (path, i)), float(l.get("at", 0.0)))
                     for i, l in enumerate(layer.get("layers", []))]
            y = dsp.place(parts, SR)
        elif t == "use":
            ref = self.cat.lookup(layer["sound"], sound.set)
            if ref is None or not ref.generated:
                raise RecipeError("%s: use of %r, which is not a generated sound" % (sound.event, layer["sound"]))
            v = layer.get("variant")
            if v and not 1 <= int(v) <= ref.variants:
                raise RecipeError("%s: use of %s variant %s, which has %d" % (sound.event, ref.event, v, ref.variants))
            k = (int(v) - 1) if v else (ctx_seed % max(ref.variants, 1))
            y = self.raw(ref, k).copy()
        elif t == "silence":
            y = np.zeros(ctx.n(layer.get("dur", 0.1)))
        else:
            params = {k: v for k, v in layer.items() if k not in LAYER_COMMON}
            y = synth.INSTRUMENTS[t](params, ctx)
        if layer.get("pitch"):
            y = dsp.resample(y, 2 ** (float(layer["pitch"]) / 12.0))
        y = apply_fx(y, layer.get("fx"), ctx, "%s %s" % (sound.event, path))
        if layer.get("gain") is not None:
            y = y * 10 ** (float(layer["gain"]) / 20.0)
        # every layer starts and stops smoothly (0.8 ms in, 3 ms out) unless it asks for a hard edge, so a sine that
        # starts at a random phase or a layer cut by its length never steps
        if not layer.get("hard") and len(y) > 8:
            y = dsp.fade(y, SR, 0.0008, 0.003)
        return y

    def raw(self, sound, k):
        key = (sound.event, k)
        if key in self._raw:
            return self._raw[key]
        if key in self._stack:
            raise RecipeError("use cycle: %s" % " -> ".join("%s#%d" % s for s in self._stack + [key]))
        self._stack.append(key)
        try:
            spec, shift = variant_spec(sound, k)
            parts = []
            for i, l in enumerate(spec["layers"]):
                parts.append((self._layer(l, k, sound, str(i)), float(l.get("at", 0.0))))
            x = dsp.place(parts, SR)
            ctx = synth.Ctx(SR, np.random.default_rng(seed_of(sound.event, k, "fx")))
            x = apply_fx(x, spec.get("fx"), ctx, sound.event)
            if shift:
                x = dsp.resample(x, 2 ** (shift / 12.0))
            x = self._post(sound, x, spec)
        finally:
            self._stack.pop()
        self._raw[key] = x
        return x

    def _post(self, sound, x, spec):
        x = dsp.dc_block(x, SR)
        if sound.loop:
            length = float(spec.get("length", len(x) / SR))
            xf = float(spec.get("loop_crossfade", 0.25))
            x = make_loop(x, SR, length, xf)
            return dsp.normalize(x, SR, sound.loudness)
        post = dict(POST_DEFAULTS.get(sound.kind, {}))
        post.update(spec.get("post", {}))
        x = dsp.trim_tail(x, SR, float(post.get("trim", -66.0)))
        fout = float(post.get("fade_out", 0.03))
        fout = min(fout, 0.3 * len(x) / SR)
        x = dsp.fade(x, SR, float(post.get("fade_in", 0.0015)), fout)
        x = dsp.normalize(x, SR, sound.loudness)
        return dsp.fade(x, SR, 0.0005, 0.002)

    def final(self, sound, k):
        """(samples, rate) as shipped: the raw buffer, halved to 22.05 kHz when the recipe says so or when `auto`
        finds less than AUTO_22K_DB of the energy above 9.5 kHz, then re-levelled and edge-faded."""
        x = self.raw(sound, k)
        rate = sound.rate
        if rate == "auto":
            rate = 22050 if high_energy_db(x, SR) < AUTO_22K_DB else 44100
        if rate == 22050:
            y = dsp.downsample2(x, SR)
            if sound.loop:
                return dsp.normalize(y, 22050, sound.loudness), 22050
            y = dsp.normalize(y, 22050, sound.loudness)
            return pad_edges(dsp.fade(y, 22050, 0.0005, 0.002)), 22050
        if sound.loop:
            return x, SR
        return pad_edges(x), SR


LEAD_SAMPLES = 256    # silence before a sound: Vorbis pre-echo of a hard attack settles in it, so sample 0 is silent
TAIL_SAMPLES = 1024   # silence after it: decoders that drop the last partial packet (ffmpeg) only lose silence


def pad_edges(x):
    """Silence at both ends of a one-shot sound (not loops, which must be exact): about 6 ms before (11.6 ms at
    22.05 kHz), well under a tick, and 23-46 ms after."""
    return np.concatenate([np.zeros(LEAD_SAMPLES), np.asarray(x, float), np.zeros(TAIL_SAMPLES)])


def high_energy_db(x, sr, f=9500.0):
    if not len(x) or not np.any(x):
        return -120.0
    spec = np.abs(np.fft.rfft(x)) ** 2
    fr = np.fft.rfftfreq(len(x), 1.0 / sr)
    tot = float(np.sum(spec)) or 1e-30
    hi = float(np.sum(spec[fr >= f]))
    return 10 * math.log10(hi / tot) if hi > 0 else -120.0


def make_loop(x, sr, length, xf):
    """A seamless loop of `length` seconds: the material past the loop end is cross-faded (equal power) into the
    start, so the last sample flows into the first. The recipe must render at least length + crossfade."""
    n = int(round(length * sr))
    m = int(round(xf * sr))
    if len(x) < n + m:
        x = np.concatenate([x, np.zeros(n + m - len(x))])
    out = x[:n].copy()
    w = np.linspace(0, math.pi / 2, m)
    out[:m] = x[:m] * np.sin(w) + x[n:n + m] * np.cos(w)
    return out
