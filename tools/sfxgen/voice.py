"""Spoken lines: voice syllables strung together the way S2's `HeroVoice` plays them (SOUND.md section 6), for the
previews and for the line-loudness rule of `check`.

A hero's syllables are short, so one syllable measures quiet; a line of them, one every `rate` ticks, measures 4-7 dB
louder. Players hear lines, so the rule (and the hero map's `voice.line_loudness`) is about the line.
"""
import numpy as np

import analyze
import dsp
import recipe

SR = 44100
SENTENCE = [3, 2, 4, 0, 2, 3, 1, 5, 0, 3, 2]  # syllables per word; 0 = a comma pause
LINE_LOUDNESS = -21.0  # default loudness of a spoken line (momentary max): between a villager and a wandering trader


def sentence(r, voice, rate_ticks, seed, jitter=0.03, melody=(0,)):
    """Voice syllables strung like a line of dialogue: a syllable every `rate_ticks` ticks (a random variant, a
    random step of the hero's melody, a little jitter), words separated by a syllable's gap, the last syllable
    rising 12%. Mirrors how S2 plays blips (SOUND.md section 6)."""
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


def hero_line(r, st):
    """The reference line of a hero set (its voice map's rate, melody and jitter; a seed from the set id)."""
    v = (st.hero or {}).get("voice", {})
    return sentence(r, st.sounds["voice"], int(v.get("rate", 3)), recipe.seed_of(st.id, "sentence"),
                    float(v.get("jitter", 0.03)), v.get("melody", [0]))


def line_loudness(r, st):
    """(measured, target) loudness of the hero's reference line."""
    v = (st.hero or {}).get("voice", {})
    lm, _ls = analyze.loudness(hero_line(r, st), SR)
    return lm, float(v.get("line_loudness", LINE_LOUDNESS))
