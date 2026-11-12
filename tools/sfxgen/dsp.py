"""Deterministic signal processing for sfxgen: parameters and envelopes, oscillators, noise colours, filters (static
biquads and swept state-variable filters), effects (drive, crush, echo, reverb, tremolo, ring, resampling), mixing,
loudness normalisation with a soft peak limiter, edge fades and DC removal.

Every function is a pure function of its arguments; randomness only comes from the `numpy.random.Generator` passed
in (seeded by the recipe reader from the event name and variant), so a build is repeatable. Arrays are mono float64.
"""
import math

import numpy as np

import analyze

TWO_PI = 2.0 * math.pi


# ---------------------------------------------------------------- parameters and envelopes

def times(n, sr):
    return np.arange(n, dtype=np.float64) / sr


def _shape(u, shape):
    u = np.clip(u, 0.0, 1.0)
    if shape == "lin":
        return u
    if shape == "cos":
        return 0.5 - 0.5 * np.cos(math.pi * u)
    if shape == "in":  # slow start
        return u * u
    if shape == "out":  # fast start
        return 1.0 - (1.0 - u) ** 2
    if shape == "step":
        return (u >= 1.0).astype(float)
    raise ValueError("unknown segment shape %r" % shape)


def breakpoints(points, n, sr, shape="lin"):
    """A curve through [[t, v], ...] (seconds). `shape` is lin, cos, in, out, step or exp (exp interpolates in the log
    domain, so it needs positive values: frequency glides). A point may carry its own shape as a third item."""
    t = times(n, sr)
    pts = [list(p) for p in points]
    if not pts:
        return np.zeros(n)
    out = np.full(n, float(pts[0][1]))
    for i in range(len(pts) - 1):
        t0, v0 = float(pts[i][0]), float(pts[i][1])
        t1, v1 = float(pts[i + 1][0]), float(pts[i + 1][1])
        sh = pts[i + 1][2] if len(pts[i + 1]) > 2 else shape
        m = (t >= t0) & (t < t1) if t1 > t0 else np.zeros(n, bool)
        if not np.any(m):
            continue
        u = (t[m] - t0) / (t1 - t0)
        if sh == "exp":
            if v0 <= 0 or v1 <= 0:
                raise ValueError("exp segment needs positive values")
            out[m] = v0 * (v1 / v0) ** u
        else:
            out[m] = v0 + (v1 - v0) * _shape(u, sh)
    out[t >= float(pts[-1][0])] = float(pts[-1][1])
    return out


def param(spec, n, sr, dur=None):
    """A parameter as an array of n samples.

    - a number: constant;
    - [a, b]: an exponential glide from a to b over the whole length when both are positive, else linear;
    - {"from": a, "to": b, "shape": "exp|lin|cos|in|out", "start": s0, "end": s1}: a glide between two times;
    - {"points": [[t, v], ...], "shape": ...}: breakpoints;
    - {"sine": {"mid": m, "depth": d, "rate": hz}}: an LFO.
    """
    if spec is None:
        return None
    if isinstance(spec, (int, float)):
        return np.full(n, float(spec))
    total = n / sr if dur is None else dur
    if isinstance(spec, list):
        a, b = float(spec[0]), float(spec[-1])
        sh = "exp" if a > 0 and b > 0 else "lin"
        if len(spec) > 2:
            k = len(spec) - 1
            return breakpoints([[total * i / k, float(v)] for i, v in enumerate(spec)], n, sr,
                               "exp" if all(float(v) > 0 for v in spec) else "lin")
        return breakpoints([[0, a], [total, b]], n, sr, sh)
    if isinstance(spec, dict):
        if "points" in spec:
            return breakpoints(spec["points"], n, sr, spec.get("shape", "lin"))
        if "sine" in spec:
            s = spec["sine"]
            return float(s.get("mid", 0)) + float(s.get("depth", 1)) * np.sin(
                TWO_PI * float(s.get("rate", 1)) * times(n, sr) + float(s.get("phase", 0)))
        a, b = float(spec["from"]), float(spec["to"])
        s0, s1 = float(spec.get("start", 0.0)), float(spec.get("end", total))
        sh = spec.get("shape", "exp" if a > 0 and b > 0 else "lin")
        return breakpoints([[s0, a], [s1, b]], n, sr, sh)
    raise ValueError("bad parameter %r" % (spec,))


def envelope(spec, n, sr):
    """Amplitude envelopes:
    - {"a": attack, "d": decay, "s": sustain level, "r": release} (ADSR, the release ends the sound; curves exp-like);
    - {"perc": [attack, decay]}: a fast rise then an exponential decay whose time constant is decay/5 (-43 dB at
      `decay`), so `decay` reads as "audible length";
    - {"swell": [rise, fall]}: a smooth rise and fall (cos shapes), the peak at `rise`;
    - {"arch": p}: sin(pi t / length) ** p over the whole length (the voice-blip shape);
    - {"points": [[t, v], ...], "shape": ...}: breakpoints;
    - None: flat 1.
    """
    if spec is None:
        return np.ones(n)
    t = times(n, sr)
    if "perc" in spec:
        a, d = (float(v) for v in spec["perc"])
        a = max(a, 1e-4)
        rise = 0.5 - 0.5 * np.cos(math.pi * np.clip(t / a, 0, 1))
        fall = np.exp(-np.maximum(t - a, 0.0) * 5.0 / max(d, 1e-4))
        return rise * fall
    if "arch" in spec:
        return np.sin(math.pi * np.clip(t / max(n / sr, 1e-6), 0, 1)) ** float(spec["arch"])
    if "swell" in spec:
        r, f = (float(v) for v in spec["swell"])
        return breakpoints([[0, 0], [r, 1, "cos"], [r + f, 0, "cos"]], n, sr)
    if "points" in spec:
        return breakpoints(spec["points"], n, sr, spec.get("shape", "lin"))
    a = max(float(spec.get("a", 0.005)), 1e-4)
    d = float(spec.get("d", 0.1))
    s = float(spec.get("s", 0.0))
    r = float(spec.get("r", 0.05))
    total = n / sr
    rel_start = max(total - r, a)
    e = np.empty(n)
    m = t < a
    e[m] = 0.5 - 0.5 * np.cos(math.pi * t[m] / a)
    m2 = t >= a
    e[m2] = s + (1 - s) * np.exp(-(t[m2] - a) * 5.0 / max(d, 1e-4))
    m3 = t >= rel_start
    if np.any(m3):
        lvl = s + (1 - s) * math.exp(-(rel_start - a) * 5.0 / max(d, 1e-4))
        u = np.clip((t[m3] - rel_start) / max(r, 1e-4), 0, 1)
        e[m3] = np.minimum(e[m3], lvl * (0.5 + 0.5 * np.cos(math.pi * u)))
    return e


# ---------------------------------------------------------------- oscillators

def phase_of(freq, sr, phase0=0.0):
    """Running phase (radians) of a frequency array."""
    return phase0 + TWO_PI * np.cumsum(freq) / sr - TWO_PI * freq[0] / sr if len(freq) else np.zeros(0)


def _poly_blep(tph, dt):
    """PolyBLEP residual for a normalised phase tph in [0,1) with increment dt (arrays)."""
    out = np.zeros_like(tph)
    m = tph < dt
    u = tph[m] / dt[m]
    out[m] = u + u - u * u - 1.0
    m2 = tph > 1.0 - dt
    u = (tph[m2] - 1.0) / dt[m2]
    out[m2] = u * u + u + u + 1.0
    return out


def osc(freq, sr, wave="sine", phase0=0.0, pulse_width=0.5):
    """Band-limited oscillators: sine, tri, saw (polyBLEP), square / pulse (polyBLEP)."""
    freq = np.asarray(freq, float)
    if wave == "sine":
        return np.sin(phase_of(freq, sr, phase0))
    dt = np.clip(freq / sr, 1e-6, 0.49)
    ph = (phase0 / TWO_PI + np.cumsum(dt) - dt[0]) % 1.0
    if wave == "saw":
        return (2.0 * ph - 1.0) - _poly_blep(ph, dt)
    if wave in ("square", "pulse"):
        pw = pulse_width
        sq = np.where(ph < pw, 1.0, -1.0)
        sq = sq + _poly_blep(ph, dt) - _poly_blep((ph - pw) % 1.0, dt)
        return sq - (2 * pw - 1)
    if wave == "tri":
        return 1.0 - 4.0 * np.abs(((ph + 0.25) % 1.0) - 0.5)
    raise ValueError("unknown wave %r" % wave)


def additive(freq, sr, partials, phase_rng=None):
    """Sum of partials [(ratio, amp)] over a fundamental array; partials above 0.45 sr are dropped."""
    freq = np.asarray(freq, float)
    out = np.zeros(len(freq))
    for i, (ratio, amp) in enumerate(partials):
        if np.max(freq) * ratio >= 0.45 * sr:
            continue
        ph0 = phase_rng.uniform(0, TWO_PI) if phase_rng is not None else 0.0
        out += amp * np.sin(phase_of(freq * ratio, sr, ph0))
    return out


def fm(carrier, sr, ratio=1.0, index=1.0, mod_freq=None):
    """Two-operator FM: sin(phase_c + index * sin(phase_m)); index may be an array (brightness envelope)."""
    carrier = np.asarray(carrier, float)
    mf = carrier * ratio if mod_freq is None else np.asarray(mod_freq, float)
    return np.sin(phase_of(carrier, sr) + np.asarray(index) * np.sin(phase_of(mf, sr)))


# ---------------------------------------------------------------- noise

def noise(n, rng, colour="white"):
    """white, pink (-3 dB/oct), brown (-6), blue (+3), violet (+6), normalised to unit RMS."""
    if n <= 0:
        return np.zeros(0)
    w = rng.standard_normal(n)
    if colour == "white":
        return w
    exp = {"pink": -0.5, "brown": -1.0, "blue": 0.5, "violet": 1.0}[colour]
    m = 1 << int(math.ceil(math.log2(n + 16)))
    spec = np.fft.rfft(np.concatenate([w, np.zeros(m - n)]))
    f = np.arange(len(spec), dtype=float)
    f[0] = 1.0
    spec *= f ** exp
    spec[0] = 0.0
    y = np.fft.irfft(spec, m)[:n]
    rms = math.sqrt(float(np.mean(y * y))) or 1.0
    return y / rms


# ---------------------------------------------------------------- filters

def _rbj(kind, f, q, gain_db, sr):
    f = min(max(float(f), 10.0), 0.49 * sr)
    w0 = TWO_PI * f / sr
    c, s = math.cos(w0), math.sin(w0)
    al = s / (2 * q)
    a = 10 ** (gain_db / 40.0)
    if kind == "lp":
        b = ((1 - c) / 2, 1 - c, (1 - c) / 2)
        aa = (1 + al, -2 * c, 1 - al)
    elif kind == "hp":
        b = ((1 + c) / 2, -(1 + c), (1 + c) / 2)
        aa = (1 + al, -2 * c, 1 - al)
    elif kind == "bp":
        b = (al, 0.0, -al)
        aa = (1 + al, -2 * c, 1 - al)
    elif kind == "notch":
        b = (1.0, -2 * c, 1.0)
        aa = (1 + al, -2 * c, 1 - al)
    elif kind == "peak":
        b = (1 + al * a, -2 * c, 1 - al * a)
        aa = (1 + al / a, -2 * c, 1 - al / a)
    elif kind in ("lowshelf", "highshelf"):
        sa = 2 * math.sqrt(a) * al
        if kind == "lowshelf":
            b = (a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa))
            aa = ((a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
        else:
            b = (a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa))
            aa = ((a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
    else:
        raise ValueError("unknown filter %r" % kind)
    return [v / aa[0] for v in b], [aa[1] / aa[0], aa[2] / aa[0]]


def biquad(x, kind, f, sr, q=0.7071, gain_db=0.0):
    """A static RBJ biquad (lp, hp, bp, notch, peak, lowshelf, highshelf)."""
    (b0, b1, b2), (a1, a2) = _rbj(kind, f, q, gain_db, sr)
    return np.asarray(analyze._biquad(np.asarray(x, float).tolist(), b0, b1, b2, a1, a2))


def svf(x, f, sr, q=0.7071, mode="lp"):
    """A zero-delay state-variable filter (Simper/TPT) whose cutoff `f` and `q` may change every sample: the swept
    filters of whooshes and wah sweeps. Modes: lp, bp (unity peak gain), hp, notch, peak."""
    x = np.asarray(x, float)
    n = len(x)
    f = np.broadcast_to(np.asarray(f, float), (n,))
    q = np.broadcast_to(np.asarray(q, float), (n,))
    g = np.tan(math.pi * np.clip(f, 10.0, 0.49 * sr) / sr)
    k = 1.0 / np.maximum(q, 0.05)
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    xs, a1s, a2s, a3s, ks = x.tolist(), a1.tolist(), a2.tolist(), a3.tolist(), k.tolist()
    lp = [0.0] * n
    bp = [0.0] * n
    ic1 = ic2 = 0.0
    for i in range(n):
        v3 = xs[i] - ic2
        v1 = a1s[i] * ic1 + a2s[i] * v3
        v2 = ic2 + a2s[i] * ic1 + a3s[i] * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        lp[i] = v2
        bp[i] = v1
    lp = np.asarray(lp)
    bp = np.asarray(bp)
    if mode == "lp":
        return lp
    if mode == "bp":
        return bp * k
    if mode == "hp":
        return x - k * bp - lp
    if mode == "notch":
        return x - k * bp
    if mode == "peak":
        return x - k * bp - 2 * lp
    raise ValueError("unknown svf mode %r" % mode)


def onepole_lp(x, f, sr):
    a = math.exp(-TWO_PI * min(float(f), 0.49 * sr) / sr)
    y = [0.0] * len(x)
    s = 0.0
    b = 1.0 - a
    for i, v in enumerate(np.asarray(x, float).tolist()):
        s = b * v + a * s
        y[i] = s
    return np.asarray(y)


def dc_block(x, sr, fc=18.0):
    """Remove the mean and everything under about 18 Hz (a 2nd-order high-pass), so no sound carries DC."""
    x = np.asarray(x, float)
    if not len(x):
        return x
    x = x - float(np.mean(x))
    return biquad(x, "hp", fc, sr, 0.7071)


def fir_lowpass(x, fc, sr, taps=127):
    """Linear-phase windowed-sinc low-pass (Blackman), delay-compensated."""
    m = np.arange(taps) - (taps - 1) / 2.0
    h = np.sinc(2 * fc / sr * m) * np.blackman(taps)
    h /= np.sum(h)
    y = np.convolve(np.asarray(x, float), h)
    d = (taps - 1) // 2
    return y[d:d + len(x)]


def downsample2(x, sr):
    """Halve the rate (44.1 to 22.05 kHz) after a 9.5 kHz linear-phase low-pass."""
    return fir_lowpass(x, 9500.0, sr, 255)[::2]


# ---------------------------------------------------------------- effects

def drive(x, amount=1.0, mode="tanh"):
    """Saturation with level compensation: tanh (warm), hard (clip), fold (wavefolder: metallic)."""
    x = np.asarray(x, float)
    k = max(float(amount), 1e-3)
    if mode == "tanh":
        return np.tanh(k * x) / math.tanh(k)
    if mode == "hard":
        return np.clip(k * x, -1, 1)
    if mode == "fold":
        y = k * x
        return np.sin(y * math.pi / 2)
    raise ValueError("unknown drive mode %r" % mode)


def crush(x, bits=8, hold=1):
    """Bit and sample-rate reduction (grit); `hold` repeats every n-th sample."""
    x = np.asarray(x, float)
    if hold > 1:
        idx = (np.arange(len(x)) // hold) * hold
        x = x[idx]
    q = 2 ** (bits - 1)
    return np.round(x * q) / q


def resample(x, factor):
    """Play back at `factor` x speed (pitch up by factor; length / factor), cubic-free linear interpolation on a
    band-limited copy when speeding up."""
    x = np.asarray(x, float)
    if abs(factor - 1.0) < 1e-9 or len(x) < 2:
        return x.copy()
    n = max(2, int(round(len(x) / factor)))
    pos = np.arange(n) * factor
    pos = pos[pos <= len(x) - 1]
    return np.interp(pos, np.arange(len(x)), x)


def pitch_curve_resample(x, ratio_curve):
    """Variable-speed playback: `ratio_curve` is the speed per output sample (tape-like bends)."""
    x = np.asarray(x, float)
    pos = np.cumsum(np.asarray(ratio_curve, float)) - ratio_curve[0]
    pos = pos[pos <= len(x) - 1]
    return np.interp(pos, np.arange(len(x)), x)


def tremolo(x, sr, rate=8.0, depth=0.5, shape="sine"):
    t = times(len(x), sr)
    if shape == "square":
        lfo = 0.5 + 0.5 * np.tanh(6 * np.sin(TWO_PI * rate * t))
    else:
        lfo = 0.5 + 0.5 * np.sin(TWO_PI * rate * t)
    return np.asarray(x) * (1 - depth + depth * lfo)


def ring(x, sr, freq=30.0, mix=1.0):
    t = times(len(x), sr)
    return np.asarray(x) * ((1 - mix) + mix * np.sin(TWO_PI * freq * t))


def echo(x, sr, delay=0.12, feedback=0.35, count=4, damp=4000.0, mix=0.35):
    """Discrete echoes, each darker than the last; the output grows by the echo tail."""
    x = np.asarray(x, float)
    d = int(round(delay * sr))
    out = np.zeros(len(x) + d * count)
    out[:len(x)] += x
    tap = x
    for i in range(1, count + 1):
        tap = onepole_lp(tap, damp, sr) if damp else tap
        out[i * d:i * d + len(x)] += mix * feedback ** (i - 1) * tap
    return out


def reverb(x, sr, rng, size=1.2, damp=0.5, mix=0.25, predelay=0.012):
    """Convolution with a generated impulse response: seeded noise in four bands whose decay times shorten with
    frequency (`damp` 0-1), early reflections as sparse taps; `size` is the RT60 in seconds of the lowest band.
    Returns the dry signal plus the tail (longer than the input by the tail)."""
    x = np.asarray(x, float)
    n_ir = int(sr * (size * 1.1 + predelay))
    t = times(n_ir, sr)
    ir = np.zeros(n_ir)
    bands = [(0, 400, 1.0), (400, 2000, 1.0 - 0.35 * damp), (2000, 6000, 1.0 - 0.6 * damp),
             (6000, sr / 2, 1.0 - 0.85 * damp)]
    w = noise(n_ir, rng, "white")
    m = 1 << int(math.ceil(math.log2(n_ir)))
    spec = np.fft.rfft(np.concatenate([w, np.zeros(m - n_ir)]))
    f = np.fft.rfftfreq(m, 1.0 / sr)
    for lo, hi, k in bands:
        sb = np.where((f >= lo) & (f < hi), spec, 0)
        b = np.fft.irfft(sb, m)[:n_ir]
        rt = max(size * k, 0.05)
        ir += b * np.exp(-6.91 * np.maximum(t - predelay, 0) / rt) * (t >= predelay)
    # early reflections
    for i in range(8):
        tt = predelay + rng.uniform(0.003, 0.05 * max(size, 0.3))
        j = int(tt * sr)
        if j < n_ir:
            ir[j] += rng.uniform(-1, 1) * 2.0
    fade_in = np.clip((t - predelay) / 0.004, 0, 1)
    ir *= fade_in
    ir /= math.sqrt(float(np.sum(ir * ir))) or 1.0
    L = len(x) + n_ir - 1
    M = 1 << int(math.ceil(math.log2(L)))
    wet = np.fft.irfft(np.fft.rfft(x, M) * np.fft.rfft(ir, M), M)[:L]
    out = np.zeros(L)
    out[:len(x)] += x * (1 - mix * 0.5)
    out += wet * mix
    return out


# ---------------------------------------------------------------- assembly

def place(layers, sr):
    """Mix [(samples, start_seconds)] into one buffer long enough for all of them."""
    end = 0
    for s, at in layers:
        end = max(end, int(round(at * sr)) + len(s))
    out = np.zeros(end)
    for s, at in layers:
        i = int(round(at * sr))
        out[i:i + len(s)] += s
    return out


def fade(x, sr, fin=0.002, fout=0.01):
    """Raised-cosine fades at both ends (fin and fout in seconds); the first and last samples become 0."""
    x = np.asarray(x, float).copy()
    n = len(x)
    a = min(n // 2, max(2, int(round(fin * sr))))
    b = min(n // 2, max(2, int(round(fout * sr))))
    if a:
        x[:a] *= 0.5 - 0.5 * np.cos(np.linspace(0, math.pi, a))
    if b:
        x[n - b:] *= 0.5 + 0.5 * np.cos(np.linspace(0, math.pi, b))
    return x


def trim_tail(x, sr, below=-70.0, keep=0.02):
    """Cut trailing near-silence (below `below` dB of the peak), keeping `keep` seconds after the last loud sample."""
    x = np.asarray(x, float)
    if not len(x):
        return x
    pk = float(np.max(np.abs(x))) or 1.0
    thr = pk * 10 ** (below / 20.0)
    idx = np.nonzero(np.abs(x) > thr)[0]
    if not len(idx):
        return x[:1]
    end = min(len(x), idx[-1] + int(keep * sr))
    return x[:end]


def limit(x, sr, ceiling_db=-1.0, lookahead=0.003, release=0.06):
    """A soft peak limiter: per-sample gain = min(1, ceiling/|x|), spread over a lookahead window (sliding minimum)
    and released smoothly, so peaks are pulled under the ceiling without a hard clip."""
    x = np.asarray(x, float)
    c = 10 ** (ceiling_db / 20.0)
    ax = np.abs(x)
    if not len(x) or float(np.max(ax)) <= c:
        return x
    need = np.minimum(1.0, c / np.maximum(ax, 1e-12))
    la = max(1, int(lookahead * sr))
    # sliding minimum over [i, i + la] via a reversed running window
    padded = np.concatenate([need, np.ones(la)])
    from numpy.lib.stride_tricks import sliding_window_view
    gmin = np.min(sliding_window_view(padded, la + 1), axis=1)[:len(x)]
    rel = math.exp(-1.0 / (release * sr))
    g = gmin.tolist()
    out = [0.0] * len(g)
    s = 1.0
    for i, v in enumerate(g):
        s = v if v < s else v + (s - v) * rel
        out[i] = s
    # smooth the attack too (a short moving average keeps the gain change itself click-free)
    gs = np.asarray(out)
    k = max(1, la // 2)
    gs = np.convolve(np.concatenate([np.full(k, gs[0]), gs, np.full(k, gs[-1])]), np.ones(2 * k + 1) / (2 * k + 1),
                     "valid")
    y = x * gs
    over = float(np.max(np.abs(y)))
    if over > c:
        y *= c / over
    return y


def normalize(x, sr, target_lufs, ceiling_db=-1.5):
    """Scale to a momentary-max loudness target (analyze.loudness), then keep the true peak under the ceiling with
    the limiter (-1.5 dBFS: Vorbis overshoots bright transients by up to about 1.2 dB, and the decoded file must
    stay under -0.3); passes repeat so the limiter's loss is made up when it is small."""
    x = np.asarray(x, float)
    for _ in range(3):
        lm, _ls = analyze.loudness(x, sr)
        if lm <= analyze.SILENCE_DB + 1:
            return x
        x = x * 10 ** ((target_lufs - lm) / 20.0)
        tp = analyze.db(analyze.true_peak(x))
        if tp > ceiling_db:
            x = limit(x, sr, ceiling_db - 0.3)
        lm2, _ = analyze.loudness(x, sr)
        if abs(lm2 - target_lufs) < 0.3:
            break
    return x
