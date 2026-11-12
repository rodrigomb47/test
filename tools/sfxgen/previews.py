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
import voice

SR = 44100


def set_items(cat, st):
    r = recipe.Renderer(cat)
    names = st.preview or [n for n, s in st.sounds.items() if s.generated]
    items = []
    if not st.is_cue and "voice" in st.sounds:
        items.append(("voice: a sentence", voice.hero_line(r, st), SR))
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
