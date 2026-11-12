"""Measurements: loudness, peaks, DC, clicks at the edges and inside, spectral balance, envelope.

All functions take a mono float64 array in [-1, 1] and its sample rate. Loudness follows ITU-R BS.1770 K-weighting
(the high shelf and the RLB high-pass, designed for any sample rate as pyloudnorm does); the numbers are "LUFS-like"
(no gating, mono, no channel weights):

- `momentary_max`: the loudest 400 ms window (10 ms hop), zero-padded for sounds shorter than 400 ms, so a short blip
  measures quieter than a long tone of the same level, as it sounds. This is the number recipes and vanilla
  references are compared by (SOUND.md, "Loudness").
- `short_max`: the loudest 100 ms window, for transients.
"""
import math

import numpy as np

SILENCE_DB = -120.0


def db(x):
    return 20.0 * math.log10(max(float(x), 1e-12))


def _biquad(x, b0, b1, b2, a1, a2):
    y = [0.0] * len(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, xi in enumerate(x):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi
        y[i] = yi
    return y


def k_weight(x, sr):
    """BS.1770 K-weighting: a +4 dB high shelf at about 1.7 kHz, then a high-pass at about 38 Hz."""
    g, q, fc = 3.99984385397, 0.7071752369554193, 1681.9744509555319
    a = 10 ** (g / 40.0)
    w0 = 2 * math.pi * fc / sr
    al = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    sa = 2 * math.sqrt(a) * al
    b0 = a * ((a + 1) + (a - 1) * c + sa)
    b1 = -2 * a * ((a - 1) + (a + 1) * c)
    b2 = a * ((a + 1) + (a - 1) * c - sa)
    a0 = (a + 1) - (a - 1) * c + sa
    a1 = 2 * ((a - 1) - (a + 1) * c)
    a2 = (a + 1) - (a - 1) * c - sa
    y = _biquad(np.asarray(x, float).tolist(), b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    q, fc = 0.5003270373253953, 38.13547087613982
    w0 = 2 * math.pi * fc / sr
    al = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    a0 = 1 + al
    y = _biquad(y, (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    return np.asarray(y)


def _window_max(power, sr, win, hop=0.010):
    n = int(round(win * sr))
    h = max(1, int(round(hop * sr)))
    p = np.concatenate([power, np.zeros(n)])
    c = np.concatenate([[0.0], np.cumsum(p)])
    starts = np.arange(0, max(1, len(power)), h)
    sums = c[np.minimum(starts + n, len(p))] - c[starts]
    m = float(np.max(sums)) / n
    return -0.691 + 10 * math.log10(m) if m > 1e-14 else SILENCE_DB


def loudness(x, sr):
    """(momentary_max, short_max) in LUFS-like dB."""
    if len(x) == 0:
        return SILENCE_DB, SILENCE_DB
    k = k_weight(x, sr)
    pw = k * k
    return _window_max(pw, sr, 0.400), _window_max(pw, sr, 0.100)


def true_peak(x, oversample=4):
    """Peak after 4x band-limited oversampling (FFT zero-padding), close to a true-peak meter."""
    x = np.asarray(x, float)
    if len(x) == 0:
        return 0.0
    n = len(x)
    pad = 256
    xp = np.concatenate([np.zeros(pad), x, np.zeros(pad)])
    spec = np.fft.rfft(xp)
    m = len(xp) * oversample
    up = np.fft.irfft(spec, m) * oversample
    return float(max(np.max(np.abs(up)), np.max(np.abs(x))))


def edge_report(x, sr, content_db=-50.0):
    """Edge levels (dB): the file's first and last sample (dBFS), and, measured on the CONTENT (from the first to
    the last sample within `content_db` of full scale, so leading and trailing silence padding is skipped), the
    peak of its first 0.5 ms and of its last 1 ms relative to the sound's own peak. A fade-in keeps `head` well
    under 0 dB, a fade-out keeps `tail` there; a step or a cut puts either near 0."""
    x = np.asarray(x, float)
    if not len(x):
        return {"first": SILENCE_DB, "last": SILENCE_DB, "head": SILENCE_DB, "tail": SILENCE_DB}
    ax = np.abs(x)
    pk = float(np.max(ax)) or 1e-12
    on = np.nonzero(ax > 10 ** (content_db / 20.0))[0]
    if not len(on):
        return {"first": db(ax[0]), "last": db(ax[-1]), "head": SILENCE_DB, "tail": SILENCE_DB}
    a, b = int(on[0]), int(on[-1]) + 1
    h = max(1, int(sr * 0.0005))
    t = max(1, int(sr * 0.001))
    return {
        "first": db(ax[0]),
        "last": db(ax[-1]),
        "head": db(np.max(ax[a:a + h]) / pk),
        "tail": db(np.max(ax[max(a, b - t):b]) / pk),
    }


def small_speaker_drop(x, sr, fc=300.0):
    """How much quieter (dB, >= 0) the sound gets on a speaker with no bass: the loudness of the sound high-passed
    at 300 Hz (4th order) under the loudness of the whole sound. A thud that is all sub-bass drops a lot."""
    x = np.asarray(x, float)
    full, _ = loudness(x, sr)
    if full <= SILENCE_DB + 1:
        return 0.0
    w0 = 2 * math.pi * fc / sr
    al = math.sin(w0) / (2 * 0.7071)
    c = math.cos(w0)
    a0 = 1 + al
    coefs = ((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    hp = np.asarray(_biquad(_biquad(x.tolist(), *coefs), *coefs))
    high, _ = loudness(hp, sr)
    return round(max(0.0, full - high), 2)


def clicks(x, sr, ratio=30.0, floor_db=-60.0):
    """Times (s) of unintended discontinuities inside the sound.

    A discontinuity (a step, a cut, a phase jump) is a short burst of high-frequency energy that the material around
    it does not have. The signal is high-passed (4th order at 4 kHz, or 0.3 x the rate), its energy taken in 0.5 ms
    frames, and a frame is a candidate when it is `ratio` times the median of the 30 ms around it and above
    `floor_db` (RMS). Two kinds of candidates are legitimate and dropped:
    - onsets: the 5 ms after are at least 4 dB louder (broadband) than the 5 ms before (a hit, a strike, a grain
      starting from silence: attacks the recipes ask for; the sound's EDGES are checked separately);
    - textures: two or more other bursts (at a third of the ratio) within 40 ms (the resets of a low sawtooth, a
      buzz, a crackle).
    What remains is a step or a cut in sustained material."""
    x = np.asarray(x, float)
    if len(x) < int(sr * 0.02):
        return []
    fc = min(4000.0, 0.3 * sr)
    w0 = 2 * math.pi * fc / sr
    al = math.sin(w0) / (2 * 0.7071)
    c = math.cos(w0)
    a0 = 1 + al
    coefs = ((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    hf = _biquad(_biquad(x.tolist(), *coefs), *coefs)
    hf = np.asarray(hf)
    fl = max(2, int(sr * 0.0005))
    nf = len(hf) // fl
    if nf < 8:
        return []
    e = np.mean(hf[:nf * fl].reshape(nf, fl) ** 2, axis=1)
    half = max(2, int(0.015 / 0.0005))
    pad = np.concatenate([np.full(half, np.median(e)), e, np.full(half, np.median(e))])
    from numpy.lib.stride_tricks import sliding_window_view
    med = np.median(sliding_window_view(pad, 2 * half + 1), axis=1)[:nf]
    floor = 10 ** (floor_db / 10.0)
    cand = np.nonzero((e > ratio * med + 1e-15) & (e > floor))[0]
    if not len(cand):
        return []
    loose = np.nonzero((e > ratio / 3 * med + 1e-15) & (e > floor / 4))[0]
    sq = np.concatenate([[0.0], np.cumsum(x * x)])
    k = max(2, int(sr * 0.005))
    span = int(round(0.040 / 0.0005))
    own = 2
    out, last = [], -10 ** 9
    edge = int(sr * 0.005)
    loud = np.nonzero(np.abs(x) > 1e-3)[0]
    c0, c1 = (int(loud[0]), int(loud[-1])) if len(loud) else (0, len(x))
    for f in cand:
        j = f * fl
        if j < c0 + edge or j > c1 - edge:  # the content's edges have their own rule (edge_report)
            continue
        before = (sq[j] - sq[max(j - k, 0)]) / k
        after = (sq[min(j + fl + k, len(x))] - sq[j + fl]) / k
        if after > before * 2.5 + 1e-9:
            continue
        d = np.abs(loose - f)
        if np.count_nonzero((d <= span) & (d > own)) >= 2:
            continue
        if f - last > 20:
            out.append(round(float(j) / sr, 3))
        last = f
    return out


def spectrum_bands(x, sr):
    """Energy share (0-1) per band: sub (<60 Hz), low (60-250), mid (250-2k), presence (2k-6k), air (6k-12k),
    top (>12k); plus the spectral centroid in Hz and the energy above 10 kHz relative to the total (dB)."""
    x = np.asarray(x, float)
    if not len(x) or not np.any(x):
        return {"sub": 0, "low": 0, "mid": 0, "presence": 0, "air": 0, "top": 0, "centroid": 0, "above10k_db": SILENCE_DB}
    n = 1 << int(math.ceil(math.log2(max(len(x), 2))))
    p = np.abs(np.fft.rfft(x * np.hanning(len(x)), n)) ** 2
    f = np.fft.rfftfreq(n, 1.0 / sr)
    tot = float(np.sum(p)) or 1e-30
    edges = [("sub", 0, 60), ("low", 60, 250), ("mid", 250, 2000), ("presence", 2000, 6000), ("air", 6000, 12000),
             ("top", 12000, 1e9)]
    out = {k: round(float(np.sum(p[(f >= a) & (f < b)])) / tot, 4) for k, a, b in edges}
    out["centroid"] = round(float(np.sum(f * p) / tot), 1)
    hi = float(np.sum(p[f >= 10000]))
    out["above10k_db"] = round(10 * math.log10(hi / tot), 1) if hi > 0 else SILENCE_DB
    return out


def envelope(x, sr, hop=0.010):
    """RMS per 10 ms frame (linear)."""
    x = np.asarray(x, float)
    h = max(1, int(sr * hop))
    m = len(x) // h
    if m == 0:
        return np.array([float(np.sqrt(np.mean(x * x)))]) if len(x) else np.zeros(1)
    return np.sqrt(np.mean(x[:m * h].reshape(m, h) ** 2, axis=1))


def attack_time(x, sr):
    """Seconds from the start to the loudest 10 ms frame."""
    e = envelope(x, sr)
    return round(float(np.argmax(e)) * 0.010, 3)


def active_duration(x, sr, below=-40.0):
    """Seconds from the first to the last 10 ms frame within `below` dB of the loudest frame."""
    e = envelope(x, sr)
    if not np.any(e):
        return 0.0
    thr = np.max(e) * 10 ** (below / 20.0)
    on = np.nonzero(e >= thr)[0]
    return round((on[-1] - on[0] + 1) * 0.010, 3)


def measure(x, sr):
    """Everything `check` and the reports use, as a plain dict."""
    x = np.asarray(x, float)
    lm, ls = loudness(x, sr)
    sb = spectrum_bands(x, sr)
    return {
        "duration": round(len(x) / sr, 4),
        "rate": sr,
        "peak_db": round(db(np.max(np.abs(x))) if len(x) else SILENCE_DB, 2),
        "true_peak_db": round(db(true_peak(x)), 2),
        "rms_db": round(db(math.sqrt(float(np.mean(x * x)))) if len(x) else SILENCE_DB, 2),
        "lufs_m": round(lm, 2),
        "lufs_s": round(ls, 2),
        "dc": round(float(np.mean(x)) if len(x) else 0.0, 5),
        "edges": {k: round(v, 1) for k, v in edge_report(x, sr).items()},
        "clicks": clicks(x, sr),
        "bands": sb,
        "small_speaker_drop": small_speaker_drop(x, sr),
        "attack": attack_time(x, sr),
        "active": active_duration(x, sr),
    }
