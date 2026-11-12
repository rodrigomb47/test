"""Previews for the owner and for review: one MP3 per set bundling its key sounds with short gaps, and a spectrogram
sheet per set (log-frequency spectrogram, waveform envelope and the measurements of each sound).

Previews contain generated sounds only. Vanilla references are listed on the sheet by id and are never decoded into
a committed file (they are Mojang's audio; the game plays them by id).
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import analyze
import codec
import dsp

GAP = 0.45
TILE_H = 120
ENV_H = 26
LABEL_H = 30
PX_PER_S = 180
MIN_W = 120
SHEET_W = 1280
F_LO, F_HI = 40.0, 20000.0


def font(size=11):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def _lut():
    # a perceptual dark-to-bright ramp (black, indigo, magenta, orange, pale yellow)
    stops = [(0.0, (0, 0, 4)), (0.25, (60, 15, 110)), (0.5, (170, 45, 105)), (0.75, (245, 125, 30)),
             (1.0, (252, 250, 180))]
    lut = np.zeros((256, 3), np.uint8)
    for i in range(256):
        u = i / 255.0
        for (a, ca), (b, cb) in zip(stops, stops[1:]):
            if a <= u <= b:
                w = (u - a) / (b - a)
                lut[i] = [int(round(ca[j] + (cb[j] - ca[j]) * w)) for j in range(3)]
                break
    return lut


LUT = _lut()


def spectrogram(x, sr, width, height, floor_db=-72.0):
    """A log-frequency magnitude spectrogram image (height x width, uint8 0-255)."""
    nfft = 2048 if sr >= 44100 else 1024
    hop = max(1, len(x) // max(width, 1))
    pad = np.concatenate([np.zeros(nfft // 2), x, np.zeros(nfft)])
    win = np.hanning(nfft)
    cols = []
    for c in range(width):
        i = c * hop
        seg = pad[i:i + nfft]
        if len(seg) < nfft:
            seg = np.concatenate([seg, np.zeros(nfft - len(seg))])
        cols.append(np.abs(np.fft.rfft(seg * win)))
    m = np.array(cols).T  # bins x width
    freqs = np.fft.rfftfreq(nfft, 1.0 / sr)
    ys = np.exp(np.linspace(math.log(F_HI), math.log(F_LO), height))
    rows = np.empty((height, width))
    for r, f in enumerate(ys):
        if f > sr / 2:
            rows[r] = 0.0
            continue
        j = np.searchsorted(freqs, f)
        j = min(max(j, 1), len(freqs) - 1)
        rows[r] = m[j]
    ref = (nfft / 4.0)
    dbv = 20 * np.log10(np.maximum(rows / ref, 1e-9))
    u = np.clip((dbv - floor_db) / (-floor_db), 0, 1)
    return (u * 255).astype(np.uint8)


def tile(x, sr, label, sub):
    dur = len(x) / sr
    w = max(MIN_W, int(dur * PX_PER_S))
    img = Image.new("RGB", (w, LABEL_H + TILE_H + ENV_H), (18, 18, 22))
    sp = spectrogram(x, sr, w, TILE_H)
    img.paste(Image.fromarray(LUT[sp]), (0, LABEL_H))
    d = ImageDraw.Draw(img)
    # frequency guides at 100 Hz, 1 kHz, 10 kHz
    for f in (100.0, 1000.0, 10000.0):
        y = LABEL_H + int((math.log(F_HI) - math.log(f)) / (math.log(F_HI) - math.log(F_LO)) * (TILE_H - 1))
        d.line([(0, y), (6, y)], fill=(200, 200, 200))
    # waveform envelope (min/max per column)
    y0 = LABEL_H + TILE_H
    mid = y0 + ENV_H // 2
    n = len(x)
    for c in range(w):
        a, b = int(c * n / w), int((c + 1) * n / w)
        seg = x[a:max(b, a + 1)]
        if len(seg):
            lo, hi = float(np.min(seg)), float(np.max(seg))
            d.line([(c, mid - int(hi * (ENV_H // 2 - 1))), (c, mid - int(lo * (ENV_H // 2 - 1)))],
                   fill=(120, 200, 255))
    # time ticks every 0.25 s
    t = 0.0
    while t <= dur:
        px = int(t / dur * (w - 1)) if dur > 0 else 0
        d.line([(px, y0 + ENV_H - 4), (px, y0 + ENV_H - 1)], fill=(230, 230, 230) if abs(t % 1.0) < 1e-6 else
               (130, 130, 130))
        t += 0.25
    d.text((3, 2), label, fill=(240, 240, 240), font=font(11))
    d.text((3, 15), sub, fill=(170, 170, 170), font=font(10))
    return img


def sheet(items, title, notes=(), path=None):
    """items: [(label, samples, rate)] -> a contact sheet image (written to path when given)."""
    tiles = []
    for label, x, sr in items:
        m = analyze.measure(x, sr)
        sub = "%.2fs  Lm %.1f  pk %.1f  %dk" % (m["duration"], m["lufs_m"], m["true_peak_db"], sr // 1000)
        tiles.append(tile(np.asarray(x, float), sr, label, sub))
    rows, row, rw = [], [], 0
    for t in tiles:
        if row and rw + t.width + 6 > SHEET_W:
            rows.append(row)
            row, rw = [], 0
        row.append(t)
        rw += t.width + 6
    if row:
        rows.append(row)
    head = 34 + 14 * len(notes)
    h = head + sum(max(t.height for t in r) + 6 for r in rows) + 6
    img = Image.new("RGB", (SHEET_W, h), (8, 8, 10))
    d = ImageDraw.Draw(img)
    d.text((8, 6), title, fill=(255, 255, 255), font=font(16))
    for i, line in enumerate(notes):
        d.text((8, 28 + 14 * i), line, fill=(180, 180, 180), font=font(11))
    y = head
    for r in rows:
        x0 = 6
        for t in r:
            img.paste(t, (x0, y))
            x0 += t.width + 6
        y += max(t.height for t in r) + 6
    if path:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        img.convert("P", palette=Image.Palette.ADAPTIVE, colors=48).save(path, optimize=True)
    return img


def bundle(clips, sr=44100, gap=GAP):
    """Concatenate clips (resampled to sr) with gaps; returns samples."""
    parts = []
    for x, r in clips:
        if r != sr:
            x = dsp.resample(np.asarray(x, float), r / sr)
        parts.append(np.asarray(x, float))
        parts.append(np.zeros(int(gap * sr)))
    return np.concatenate([np.zeros(int(0.2 * sr))] + parts) if parts else np.zeros(1)


def write_mp3(clips, path, sr=44100, bitrate="56k"):
    codec.encode_mp3(bundle(clips, sr), sr, path, bitrate)
