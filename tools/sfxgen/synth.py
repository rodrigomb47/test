"""Instruments: the generators a recipe layer names in `type`. Each takes the layer's parameters and a context
(rate, seeded random generator, `use` resolver) and returns mono samples. Parameters accept the forms of
`dsp.param` (constant, [from, to] glide, breakpoints, LFO) where the docstring says "param".

| type | what |
|---|---|
| tone | oscillator (sine, tri, saw, square, pulse) with harmonics, vibrato, unison |
| fm | two-operator FM (metallic, bell-like, growly) |
| noise | coloured noise through an optional (swept) filter |
| bell | inharmonic partials with per-partial decay and a strike (presets: brass_small, temple, chime, crystal, iron, gong, kagura) |
| membrane | drum: circular-membrane modes with a pitch drop and a stick click (taiko, frame drum) |
| whoosh | noise through a band-pass that sweeps along a path, with a swell |
| impact | thump with a pitch drop, crunch noise, click and debris |
| crackle | random grains (fire, ice cracks, electricity) |
| shimmer | a cloud of slowly beating sines that rises |
| sparkle | granular pings on a scale |
| vox | voice: a glottal, chirp, buzz or breath source through vowel formants (blips, huffs, growls, caws, laughs) |
| pluck | plucked string by additive partials (koto, shamisen with `buzz`) |
| brass | detuned saws through a filter that opens with the envelope (horns, stings) |
| clap | hand clap: a few noise bursts and a short tail |
| bubbles | rising-pitch sine bubbles (cauldrons, fizz) |
| click | a short filtered transient |
| group | nested layers with their own fx |
| use | another sound of the same set (optionally a variant), as a layer |
"""
import math

import numpy as np

import dsp

TWO_PI = 2 * math.pi

BELL_PRESETS = {
    # ratio, amplitude, decay factor (x the bell's decay)
    "brass_small": [(1, 1, 1.0), (2.32, .6, .7), (4.25, .4, .45), (5.4, .25, .3), (6.8, .18, .22), (8.9, .1, .15)],
    "temple": [(0.5, .55, 1.3), (1, 1, 1), (1.19, .45, .8), (1.5, .35, .6), (2.0, .45, .55), (2.52, .22, .4),
               (2.66, .28, .38), (3.0, .16, .3), (4.07, .12, .22)],
    "chime": [(1, 1, 1), (2.756, .5, .6), (5.404, .25, .35), (8.933, .12, .2)],
    "crystal": [(1, 1, 1), (2.0, .25, .55), (3.01, .14, .4), (4.16, .2, .3), (5.43, .1, .22), (6.79, .05, .15)],
    "iron": [(1, 1, 1), (1.48, .4, .5), (2.76, .8, .6), (5.4, .7, .4), (8.93, .5, .25), (13.34, .3, .15)],
    "gong": [(1, 1, 1), (1.47, .7, .9), (1.98, .6, .85), (2.44, .5, .7), (2.92, .45, .6), (3.6, .35, .5),
             (4.22, .3, .4), (5.1, .2, .3), (6.3, .12, .2)],
    "kagura": [(1, 1, 1), (2.41, .7, .6), (3.87, .45, .4), (5.12, .35, .3), (7.3, .2, .2)],
}

VOWELS = {
    # F1, F2, F3 (Hz, adult male reference) and their relative gains (dB)
    "a": ((730, 1090, 2440), (0, -4, -14)),
    "e": ((530, 1840, 2480), (0, -8, -12)),
    "i": ((300, 2290, 3010), (0, -12, -14)),
    "o": ((570, 840, 2410), (0, -3, -20)),
    "u": ((300, 870, 2240), (0, -8, -24)),
    "ae": ((660, 1720, 2410), (0, -5, -12)),
    "uh": ((520, 1190, 2390), (0, -5, -15)),
    "er": ((490, 1350, 1690), (0, -6, -10)),
    "m": ((250, 1000, 2200), (0, -22, -30)),
    "n": ((280, 1600, 2500), (0, -20, -26)),
    "crow": ((1300, 2100, 3300), (0, -4, -10)),
}

MEMBRANE = [(1.0, 1.0, 1.0), (1.594, .55, .7), (2.136, .35, .55), (2.296, .3, .5), (2.653, .2, .4),
            (2.918, .15, .35), (3.156, .1, .3)]


class Ctx:
    def __init__(self, sr, rng, use=None):
        self.sr = sr
        self.rng = rng
        self.use = use

    def n(self, dur):
        return max(1, int(round(float(dur) * self.sr)))


def _get(p, k, d):
    v = p.get(k, d)
    return d if v is None else v


def _vibrato(freq, p, ctx):
    v = p.get("vibrato")
    if not v:
        return freq
    t = dsp.times(len(freq), ctx.sr)
    depth = float(v.get("depth", 0.3))  # semitones
    rate = float(v.get("rate", 5.5))
    delay = float(v.get("delay", 0.0))
    ramp = np.clip((t - delay) / max(float(v.get("ramp", 0.05)), 1e-3), 0, 1)
    return freq * 2 ** (depth * ramp * np.sin(TWO_PI * rate * t + ctx.rng.uniform(0, TWO_PI)) / 12.0)


# ---------------------------------------------------------------- instruments

def tone(p, ctx):
    n = ctx.n(p.get("dur", 0.3))
    f = _vibrato(dsp.param(_get(p, "freq", 440.0), n, ctx.sr), p, ctx)
    wave = p.get("wave", "sine")
    uni = p.get("unison")
    voices = [(0.0, 1.0)]
    if uni:
        k = int(uni.get("voices", 3))
        cents = float(uni.get("cents", 12))
        voices = [((i - (k - 1) / 2) / max((k - 1) / 2, 1) * cents, 1.0 / math.sqrt(k)) for i in range(k)]
    out = np.zeros(n)
    for c, g in voices:
        fv = f * 2 ** (c / 1200.0)
        if p.get("harmonics"):
            out += g * dsp.additive(fv, ctx.sr, p["harmonics"], ctx.rng)
        else:
            out += g * dsp.osc(fv, ctx.sr, wave, ctx.rng.uniform(0, TWO_PI) if uni else 0.0, p.get("pw", 0.5))
    return out * dsp.envelope(p.get("env"), n, ctx.sr)


def fm(p, ctx):
    n = ctx.n(p.get("dur", 0.4))
    f = _vibrato(dsp.param(_get(p, "freq", 440.0), n, ctx.sr), p, ctx)
    idx = dsp.param(_get(p, "index", 2.0), n, ctx.sr)
    y = dsp.fm(f, ctx.sr, float(p.get("ratio", 1.0)), idx)
    return y * dsp.envelope(p.get("env"), n, ctx.sr)


def _filter_spec(x, spec, ctx):
    if not spec:
        return x
    mode = spec.get("mode", "bp")
    f = spec.get("f", 1000)
    q = spec.get("q", 0.7071)
    if isinstance(f, (int, float)) and isinstance(q, (int, float)):
        y = dsp.biquad(x, mode, f, ctx.sr, q, float(spec.get("gain", 0)))
        return y * (1.0 if mode != "bp" else 1.0)
    fa = dsp.param(f, len(x), ctx.sr)
    qa = dsp.param(q, len(x), ctx.sr)
    return dsp.svf(x, fa, ctx.sr, qa, mode)


def noise(p, ctx):
    n = ctx.n(p.get("dur", 0.3))
    x = dsp.noise(n, ctx.rng, p.get("colour", "white"))
    x = _filter_spec(x, p.get("filter"), ctx)
    return x * dsp.envelope(p.get("env"), n, ctx.sr)


def bell(p, ctx):
    dur = float(p.get("dur", 1.2))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    f0 = float(p.get("f0", 880))
    partials = p.get("partials", "brass_small")
    if isinstance(partials, str):
        partials = BELL_PRESETS[partials]
    decay = float(p.get("decay", dur * 0.5))
    beat = float(p.get("beat", 0.0))
    bright = float(p.get("bright", 1.0))
    bloom = float(p.get("bloom", 0.0))
    glide = p.get("glide")  # semitones over the length (gongs bend down)
    fcurve = f0 * (2 ** (dsp.param(glide, n, ctx.sr) / 12.0) if glide is not None else np.ones(n))
    out = np.zeros(n)
    for i, (ratio, amp, dk) in enumerate(partials):
        if f0 * ratio >= 0.45 * ctx.sr:
            continue
        a = amp * (bright ** i)
        ph = ctx.rng.uniform(0, TWO_PI)
        env = np.exp(-t / max(dk * decay, 1e-3))
        if bloom:
            env *= 1 - np.exp(-t / bloom)
        s = np.sin(dsp.phase_of(fcurve * ratio, ctx.sr, ph))
        if beat:
            s = 0.5 * s + 0.5 * np.sin(dsp.phase_of(fcurve * ratio + beat * (1 + i * 0.3), ctx.sr, ph + 1.3))
        out += a * s * env
    strike = float(p.get("strike", 0.3))
    if strike:
        sn = ctx.n(min(dur, 0.03))
        cl = dsp.noise(sn, ctx.rng) * np.exp(-dsp.times(sn, ctx.sr) * 400)
        cl = dsp.biquad(cl, "bp", min(f0 * 4, 0.4 * ctx.sr), ctx.sr, 1.5)
        out[:sn] += strike * cl * 2
    return out * dsp.envelope(p.get("env"), n, ctx.sr) if p.get("env") else out


def membrane(p, ctx):
    dur = float(p.get("dur", 0.8))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    f0 = float(p.get("f0", 90))
    drop = float(p.get("drop", 0.25))  # fraction the pitch falls from at the start
    decay = float(p.get("decay", dur * 0.35))
    fcurve = f0 * (1 + drop * np.exp(-t * 30))
    out = np.zeros(n)
    for i, (ratio, amp, dk) in enumerate(MEMBRANE):
        out += amp * np.sin(dsp.phase_of(fcurve * ratio, ctx.sr, ctx.rng.uniform(0, TWO_PI))) * np.exp(
            -t / max(decay * dk, 1e-3))
    stick = float(p.get("stick", 0.4))
    if stick:
        sn = ctx.n(0.02)
        cl = dsp.noise(sn, ctx.rng) * np.exp(-dsp.times(sn, ctx.sr) * 250)
        out[:sn] += stick * dsp.biquad(cl, "bp", float(p.get("stick_f", 2500)), ctx.sr, 0.9) * 2
    skin = float(p.get("skin", 0.25))
    if skin:
        nz = dsp.biquad(dsp.noise(n, ctx.rng, "pink"), "lp", f0 * 6, ctx.sr) * np.exp(-t * 18)
        out += skin * nz
    return out * dsp.envelope(p.get("env"), n, ctx.sr) if p.get("env") else out


def whoosh(p, ctx):
    dur = float(p.get("dur", 0.5))
    n = ctx.n(dur)
    f = dsp.param(_get(p, "f", [400, 2400, 700]), n, ctx.sr, dur)
    q = dsp.param(_get(p, "q", 1.2), n, ctx.sr, dur)
    x = dsp.noise(n, ctx.rng, p.get("colour", "pink"))
    y = dsp.svf(x, f, ctx.sr, q, "bp")
    air = float(p.get("air", 0.0))
    if air:
        y += air * dsp.svf(dsp.noise(n, ctx.rng, "white"), np.minimum(f * 3.0, 0.45 * ctx.sr), ctx.sr, 0.9, "bp")
    body = float(p.get("body", 0.0))
    if body:
        y += body * np.sin(dsp.phase_of(np.maximum(f / 6.0, 40.0), ctx.sr)) * 0.5
    peak = float(p.get("peak", 0.5))
    env = p.get("env") or {"swell": [dur * peak, dur * (1 - peak)]}
    y *= dsp.envelope(env, n, ctx.sr)
    fl = p.get("flutter")
    if fl:
        y = dsp.tremolo(y, ctx.sr, float(fl.get("rate", 14)), float(fl.get("depth", 0.6)), fl.get("shape", "sine"))
    return y


def impact(p, ctx):
    dur = float(p.get("dur", 0.8))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    fa, fb = (float(v) for v in p.get("f", [160, 45]))
    drop = float(p.get("drop", 0.055))
    body_decay = float(p.get("body_decay", 0.2))
    freq = fb + (fa - fb) * np.exp(-t / max(drop, 1e-3))
    thump = np.sin(dsp.phase_of(freq, ctx.sr)) * np.exp(-t / max(body_decay, 1e-3))
    attack = float(p.get("attack", 0.0015))
    thump *= np.clip(t / max(attack, 1e-4), 0, 1)
    out = float(p.get("body", 1.0)) * thump
    crunch = float(p.get("crunch", 0.8))
    if crunch:
        cn = dsp.noise(n, ctx.rng, p.get("crunch_colour", "white"))
        if int(p.get("crunch_poles", 2)) == 1:  # a gentler one-pole slope keeps more bite (the prototype slam)
            cz = dsp.onepole_lp(cn, float(p.get("crunch_lp", 1800)), ctx.sr)
        else:
            cz = dsp.biquad(cn, "lp", float(p.get("crunch_lp", 1800)), ctx.sr, 0.8)
        out += crunch * cz * np.exp(-t / max(float(p.get("crunch_decay", 0.07)), 1e-3))
    click = float(p.get("click", 0.3))
    if click:
        cn = ctx.n(0.012)
        c = dsp.noise(cn, ctx.rng) * np.exp(-dsp.times(cn, ctx.sr) * 600)
        out[:cn] += click * dsp.biquad(c, "hp", float(p.get("click_f", 2500)), ctx.sr) * 2
    debris = int(p.get("debris", 0))
    if debris:
        lo, hi = p.get("debris_f", [1500, 4000])
        span = float(p.get("debris_span", 0.45))
        for _ in range(debris):
            s = int(ctx.sr * ctx.rng.uniform(0.04, span))
            L = ctx.n(ctx.rng.uniform(0.015, 0.04))
            if s + L < n:
                g = dsp.noise(L, ctx.rng) * np.exp(-dsp.times(L, ctx.sr) * 120)
                out[s:s + L] += dsp.biquad(g, "bp", ctx.rng.uniform(lo, hi), ctx.sr, 4) * ctx.rng.uniform(.15, .4) * \
                    float(p.get("debris_gain", 1.0))
    drv = float(p.get("drive", 0.0))
    if drv:
        out = dsp.drive(out, drv)
    return out


def crackle(p, ctx):
    dur = float(p.get("dur", 1.0))
    n = ctx.n(dur)
    rate = dsp.param(_get(p, "rate", 30), n, ctx.sr, dur)
    lo, hi = (float(v) for v in p.get("f", [1500, 6000]))
    grain = float(p.get("grain", 0.012))
    q = float(p.get("q", 3.0))
    out = np.zeros(n)
    t = 0.0
    while True:
        i = int(t * ctx.sr)
        if i >= n:
            break
        r = max(float(rate[min(i, n - 1)]), 0.1)
        t += ctx.rng.exponential(1.0 / r)
        i = int(t * ctx.sr)
        if i >= n:
            break
        L = ctx.n(grain * ctx.rng.uniform(0.5, 1.5))
        L = min(L, n - i)
        if L < 4:
            continue
        g = dsp.noise(L, ctx.rng) * np.exp(-dsp.times(L, ctx.sr) * (5.0 / max(grain, 1e-3)))
        fc = math.exp(ctx.rng.uniform(math.log(lo), math.log(hi)))
        g = dsp.biquad(g, "bp", fc, ctx.sr, q)
        out[i:i + L] += g * (ctx.rng.uniform(0.2, 1.0) ** float(p.get("spread", 1.5)))
    return out * dsp.envelope(p.get("env"), n, ctx.sr)


def _scale_freqs(base, scale, octaves, rng, count):
    notes = [base * 2 ** ((s + 12 * o) / 12.0) for o in range(octaves) for s in scale]
    return [notes[int(rng.integers(0, len(notes)))] for _ in range(count)]


def shimmer(p, ctx):
    dur = float(p.get("dur", 1.2))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    count = int(p.get("count", 10))
    rise = dsp.param(_get(p, "rise", 0.0), n, ctx.sr, dur)  # semitones over time
    if p.get("scale"):
        freqs = _scale_freqs(float(p.get("base", 1200)), p["scale"], int(p.get("octaves", 2)), ctx.rng, count)
    else:
        lo, hi = (float(v) for v in p.get("f", [1500, 5000]))
        freqs = [math.exp(ctx.rng.uniform(math.log(lo), math.log(hi))) for _ in range(count)]
    trem = float(p.get("trem", 7.0))
    out = np.zeros(n)
    for f in freqs:
        fc = f * 2 ** (rise / 12.0)
        fc = np.minimum(fc, 0.45 * ctx.sr)
        am = 0.55 + 0.45 * np.sin(TWO_PI * trem * ctx.rng.uniform(0.6, 1.4) * t + ctx.rng.uniform(0, TWO_PI))
        out += np.sin(dsp.phase_of(fc, ctx.sr, ctx.rng.uniform(0, TWO_PI))) * am / math.sqrt(count)
    return out * dsp.envelope(p.get("env") or {"swell": [dur * 0.4, dur * 0.6]}, n, ctx.sr)


def sparkle(p, ctx):
    dur = float(p.get("dur", 1.0))
    n = ctx.n(dur)
    count = int(p.get("count", 12))
    grain = float(p.get("grain", 0.12))
    glide = float(p.get("glide", 0.0))  # semitones per grain
    if p.get("scale"):
        freqs = _scale_freqs(float(p.get("base", 1500)), p["scale"], int(p.get("octaves", 2)), ctx.rng, count)
    else:
        lo, hi = (float(v) for v in p.get("f", [2000, 6000]))
        freqs = [math.exp(ctx.rng.uniform(math.log(lo), math.log(hi))) for _ in range(count)]
    order = p.get("order")  # "up" / "down" sorts the pitches against time
    tspan = p.get("span", [0.0, max(dur - grain, 0.0)])
    starts = sorted(ctx.rng.uniform(float(tspan[0]), float(tspan[1])) for _ in range(count))
    if order == "up":
        freqs = sorted(freqs)
    elif order == "down":
        freqs = sorted(freqs, reverse=True)
    out = np.zeros(n)
    for st, f in zip(starts, freqs):
        L = min(ctx.n(grain), n - int(st * ctx.sr))
        if L < 8:
            continue
        tt = dsp.times(L, ctx.sr)
        fc = f * 2 ** (glide * tt / grain / 12.0)
        fc = np.minimum(fc, 0.45 * ctx.sr)
        ph = dsp.phase_of(fc, ctx.sr)
        g = (np.sin(ph) + 0.3 * np.sin(2.76 * ph)) * np.exp(-tt * 5.0 / grain)
        g *= np.clip(tt / 0.002, 0, 1)
        i = int(st * ctx.sr)
        out[i:i + L] += g * ctx.rng.uniform(0.4, 1.0)
    return out * dsp.envelope(p.get("env"), n, ctx.sr)


def vox(p, ctx):
    """A voice: source (glottal: band-limited pulse with a spectral tilt; chirp: sine plus two harmonics, the
    prototype blip; buzz: square; breath: noise) through three vowel formants (a, e, i, o, u, ae, uh, er, m, n,
    crow, or a list of [f1, f2, f3]), shifted by `shift` (1.0 adult male, about 1.18 adult female). `contour` is the
    pitch path in semitones (a list spread over the length, or a param), `jitter` random pitch wobble (semitones),
    `rough` an amplitude roughness {rate, depth} for growls and caws, `breath` mixes noise in."""
    dur = float(p.get("dur", 0.1))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    f0 = float(p.get("f0", 220))
    contour = p.get("contour", 0.0)
    semi = dsp.param(contour, n, ctx.sr, dur) if not isinstance(contour, list) or len(contour) != 1 else np.full(
        n, float(contour[0]))
    if isinstance(contour, list) and len(contour) > 1:
        k = len(contour) - 1
        semi = dsp.breakpoints([[dur * i / k, float(v)] for i, v in enumerate(contour)], n, ctx.sr, "cos")
    jit = float(p.get("jitter", 0.0))
    if jit:
        j = dsp.biquad(dsp.noise(n, ctx.rng), "lp", 18.0, ctx.sr)
        j /= (np.max(np.abs(j)) or 1.0)
        semi = semi + jit * j
    f = _vibrato(f0 * 2 ** (semi / 12.0), p, ctx)
    src = p.get("source", "glottal")
    if src == "chirp":
        ph = dsp.phase_of(f, ctx.sr)
        s = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)
    elif src == "buzz":
        s = dsp.osc(f, ctx.sr, "square", pulse_width=float(p.get("pw", 0.35)))
    elif src == "breath":
        s = dsp.noise(n, ctx.rng, "pink")
    elif src == "bell":
        ph = dsp.phase_of(f, ctx.sr)
        s = np.sin(ph) + 0.35 * np.sin(2.0 * ph) + 0.2 * np.sin(3.0 * ph) + 0.25 * np.sin(4.2 * ph) * np.exp(-t * 25)
    else:
        tilt = float(p.get("tilt", 1.2))
        hs = []
        top = int(min(40, 0.45 * ctx.sr / max(float(np.max(f)), 1)))
        for k in range(1, max(top, 1) + 1):
            hs.append((k, 1.0 / k ** tilt))
        s = dsp.additive(f, ctx.sr, hs)
    breath = float(p.get("breath", 0.0))
    if breath and src != "breath":
        s = s + breath * dsp.noise(n, ctx.rng, "pink") * float(np.std(s) or 1.0)
    vowel = p.get("vowel", "a")
    shift = float(p.get("shift", 1.0))
    if vowel and vowel != "none":
        if isinstance(vowel, str):
            fr, gains = VOWELS[vowel]
        else:
            fr, gains = vowel, (0, -6, -14)
        q = float(p.get("fq", 6.0))
        y = np.zeros(n)
        v2 = p.get("vowel_to")
        for i, (ff, gd) in enumerate(zip(fr, gains)):
            fc = ff * shift
            if v2:
                fc2 = VOWELS[v2][0][i] * shift
                fcurve = dsp.breakpoints([[0, fc], [dur, fc2]], n, ctx.sr, "cos")
                y += 10 ** (gd / 20.0) * dsp.svf(s, fcurve, ctx.sr, q, "bp")
            else:
                y += 10 ** (gd / 20.0) * dsp.biquad(s, "bp", fc, ctx.sr, q)
        s = y + float(p.get("dry", 0.08)) * s
    rough = p.get("rough")
    if rough:
        rr = float(rough.get("rate", 40))
        dep = float(rough.get("depth", 0.5))
        am = 1 - dep * (0.5 + 0.5 * np.sin(TWO_PI * rr * t + 0.4 * np.sin(TWO_PI * rr * 0.37 * t)))
        s = s * am
    a = float(p.get("attack", 0.012))
    r = float(p.get("release", 0.03))
    env = p.get("env") or {"points": [[0, 0], [a, 1, "cos"], [max(dur - r, a), float(p.get("sustain", 0.8))],
                                      [dur, 0, "cos"]]}
    out = s * dsp.envelope(env, n, ctx.sr)
    drv = float(p.get("drive", 0.0))
    if drv:
        out = dsp.drive(out / (np.max(np.abs(out)) or 1.0), drv)
    return out


def pluck(p, ctx):
    dur = float(p.get("dur", 1.0))
    n = ctx.n(dur)
    t = dsp.times(n, ctx.sr)
    f0 = float(p.get("f0", 330))
    decay = float(p.get("decay", 0.6))
    damp = float(p.get("damp", 0.5))  # higher partials die faster
    inh = float(p.get("inharm", 0.0004))
    pos = float(p.get("pos", 0.18))  # pick position (fraction of the string)
    out = np.zeros(n)
    for k in range(1, 40):
        fk = f0 * k * math.sqrt(1 + inh * k * k)
        if fk >= 0.45 * ctx.sr:
            break
        amp = abs(math.sin(math.pi * k * pos)) / k
        tau = decay / (1 + damp * (k - 1) ** 1.3)
        out += amp * np.sin(TWO_PI * fk * t + ctx.rng.uniform(0, 0.3)) * np.exp(-t / max(tau, 1e-3))
    out *= np.clip(t / 0.0015, 0, 1)
    buzz = float(p.get("buzz", 0.0))  # shamisen sawari
    if buzz:
        out = (1 - buzz) * out + buzz * dsp.drive(out / (np.max(np.abs(out)) or 1), 3.0, "fold") * np.max(np.abs(out))
    return out * dsp.envelope(p.get("env"), n, ctx.sr) if p.get("env") else out


def brass(p, ctx):
    dur = float(p.get("dur", 0.8))
    n = ctx.n(dur)
    f = _vibrato(dsp.param(_get(p, "freq", 220.0), n, ctx.sr, dur), p, ctx)
    det = float(p.get("detune", 8.0))
    s = (dsp.osc(f * 2 ** (-det / 1200), ctx.sr, "saw", ctx.rng.uniform(0, TWO_PI)) +
         dsp.osc(f * 2 ** (det / 1200), ctx.sr, "saw", ctx.rng.uniform(0, TWO_PI))) * 0.5
    env = dsp.envelope(p.get("env") or {"a": 0.06, "d": 0.4, "s": 0.7, "r": 0.15}, n, ctx.sr)
    cut = float(p.get("cutoff", 400)) + float(p.get("open", 2500)) * env
    y = dsp.svf(s, cut, ctx.sr, float(p.get("res", 1.2)), "lp")
    return y * env


def clap(p, ctx):
    dur = float(p.get("dur", 0.25))
    n = ctx.n(dur)
    out = np.zeros(n)
    spacing = float(p.get("spacing", 0.011))
    for k in range(int(p.get("bursts", 3))):
        i = int(k * spacing * ctx.sr)
        L = min(ctx.n(0.012), n - i)
        if L > 0:
            out[i:i + L] += dsp.noise(L, ctx.rng) * np.exp(-dsp.times(L, ctx.sr) * 300) * (0.8 + 0.1 * k)
    i = int(int(p.get("bursts", 3)) * spacing * ctx.sr)
    L = n - i
    if L > 0:
        out[i:] += dsp.noise(L, ctx.rng) * np.exp(-dsp.times(L, ctx.sr) / float(p.get("tail", 0.04))) * 0.9
    return dsp.biquad(out, "bp", float(p.get("f", 1200)), ctx.sr, float(p.get("q", 1.1)))


def bubbles(p, ctx):
    dur = float(p.get("dur", 1.0))
    n = ctx.n(dur)
    rate = dsp.param(_get(p, "rate", 14), n, ctx.sr, dur)
    lo, hi = (float(v) for v in p.get("f", [300, 1200]))
    out = np.zeros(n)
    t = 0.0
    while True:
        i = int(t * ctx.sr)
        if i >= n:
            break
        t += ctx.rng.exponential(1.0 / max(float(rate[min(i, n - 1)]), 0.1))
        i = int(t * ctx.sr)
        if i >= n:
            break
        f0 = math.exp(ctx.rng.uniform(math.log(lo), math.log(hi)))
        tau = float(p.get("tau", 0.025)) * (600.0 / f0) ** 0.5
        L = min(ctx.n(tau * 6), n - i)
        if L < 8:
            continue
        tt = dsp.times(L, ctx.sr)
        fc = f0 * (1 + float(p.get("chirp", 6.0)) * tt)
        b = np.sin(dsp.phase_of(fc, ctx.sr)) * np.exp(-tt / tau) * np.clip(tt / 0.0015, 0, 1)
        out[i:i + L] += b * ctx.rng.uniform(0.3, 1.0)
    return out * dsp.envelope(p.get("env"), n, ctx.sr)


def click(p, ctx):
    dur = float(p.get("dur", 0.03))
    n = ctx.n(dur)
    x = dsp.noise(n, ctx.rng) * np.exp(-dsp.times(n, ctx.sr) * float(p.get("sharp", 500)))
    x = dsp.biquad(x, p.get("mode", "bp"), float(p.get("f", 3000)), ctx.sr, float(p.get("q", 2.0)))
    ring = p.get("ring")
    if ring:
        tt = dsp.times(n, ctx.sr)
        x += float(ring.get("gain", 0.5)) * np.sin(TWO_PI * float(ring["f"]) * tt) * np.exp(
            -tt / float(ring.get("decay", 0.01)))
    return x


INSTRUMENTS = {
    "tone": tone, "fm": fm, "noise": noise, "bell": bell, "membrane": membrane, "whoosh": whoosh, "impact": impact,
    "crackle": crackle, "shimmer": shimmer, "sparkle": sparkle, "vox": vox, "pluck": pluck, "brass": brass,
    "clap": clap, "bubbles": bubbles, "click": click,
}

SPECIAL = ("group", "use", "silence", "seq")
