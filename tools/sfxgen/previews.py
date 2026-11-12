"""`preview`: the owner's listening set in docs/heroes-v2/previews/sound/.

- `<set>.mp3`: the set's key sounds (its `preview` list, first variant each) with short gaps; a hero's file starts with
  a spoken sentence (voice syllables strung together at the hero's rate, as S2 will play them).
- `<set>.png`: a spectrogram sheet of the same sounds, with the vanilla references listed by id.
- `voices.mp3`: the eight voices one after another, the same sentence shape each.
Vanilla audio is never decoded into these files (SOUND.md, "Licence").
"""
import os

import numpy as np

import dsp
import preview
import recipe

SR = 44100
SENTENCE = [3, 2, 4, 0, 2, 3, 1, 5, 0, 3, 2]  # syllables per word; 0 = a comma pause


def sentence(r, voice, rate_ticks, seed, jitter=0.03, melody=(0,)):
    """Voice syllables strung like a line of dialogue: a syllable every `rate_ticks` ticks (a random variant, a
    random step of the hero's melody, a little jitter), words separated by a syllable's gap, the last syllable
    rising. Mirrors how S2 plays blips (SOUND.md section 6)."""
    rng = np.random.default_rng(seed)
    step = rate_ticks * 0.05
    t = 0.0
    parts = []
    for wi, n in enumerate(SENTENCE):
        if n == 0:
            t += step * 2
            continue
        for i in range(n):
            k = int(rng.integers(0, voice.variants))
            x = r.raw(voice, k)
            p = 2 ** (float(melody[int(rng.integers(0, len(melody)))]) / 12.0) * (1 + rng.uniform(-1, 1) * jitter)
            if wi == len(SENTENCE) - 1 and i == n - 1:
                p *= 1.12
            parts.append((dsp.resample(x, p), t))
            t += step
        t += step
    y = dsp.place(parts, SR)
    return dsp.fade(y, SR, 0.002, 0.02)


def set_items(cat, st):
    r = recipe.Renderer(cat)
    names = st.preview or [n for n, s in st.sounds.items() if s.generated]
    items = []
    if not st.is_cue and "voice" in st.sounds:
        v = (st.hero or {}).get("voice", {})
        items.append(("voice: a sentence", sentence(r, st.sounds["voice"], int(v.get("rate", 3)),
                                                    recipe.seed_of(st.id, "sentence"), float(v.get("jitter", 0.03)),
                                                    v.get("melody", [0])), SR))
    for n in names:
        s = st.sounds[n]
        if not s.generated:
            continue
        if s.kind == "voice":
            for k in range(min(4, s.variants)):
                x, sr = r.final(s, k)
                items.append(("%s #%d" % (n, k + 1), x, sr))
            continue
        x, sr = r.final(s, 0)
        if s.loop:
            x = np.concatenate([x, x])  # twice round, to hear the seam
        items.append((n, x, sr))
    notes = []
    van = [(n, s) for n, s in st.sounds.items() if s.vanilla]
    if van:
        line = "vanilla by id: " + "; ".join(
            "%s = %s" % (n, ", ".join("%s%s" % (v["event"].replace("minecraft:", ""),
                                                " x%.2f" % v["pitch"] if float(v.get("pitch", 1)) != 1 else "")
                                      for v in s.vanilla)) for n, s in van)
        # wrap to the sheet width
        words, cur = line.split(" "), ""
        for wd in words:
            if len(cur) + len(wd) > 230:
                notes.append(cur)
                cur = "    "
            cur += wd + " "
        notes.append(cur)
    return items, notes


def run(cat, sets, out):
    os.makedirs(out, exist_ok=True)
    chosen = sets or sorted(cat.sets)
    voices = []
    for sid in chosen:
        st = cat.sets[sid]
        items, notes = set_items(cat, st)
        preview.sheet(items, st.title, notes, os.path.join(out, sid + ".png"))
        preview.write_mp3([(x, sr) for _l, x, sr in items], os.path.join(out, sid + ".mp3"))
        if not st.is_cue and items and items[0][0].startswith("voice"):
            voices.append(items[0][1])
        print("preview %-22s %2d sounds -> %s.mp3, %s.png" % (sid, len(items), sid, sid))
    if not sets and voices:
        preview.write_mp3([(v, SR) for v in voices], os.path.join(out, "voices.mp3"))
        print("preview voices: %d heroes -> voices.mp3" % len(voices))
    return 0
