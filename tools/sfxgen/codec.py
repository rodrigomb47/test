"""ffmpeg wrappers: float buffers to Ogg Vorbis (mono, bit-exact container), decoding, MP3 previews.

Determinism: the Ogg muxer normally draws a random stream serial number and writes the library version into the
Vorbis comment header; `-fflags +bitexact -flags:a +bitexact` makes the serial number fixed and drops the version, so
the same samples and the same ffmpeg/libvorbis give the same bytes. A different libvorbis may encode differently: then
`build --check` reports the files as different, and a rebuild is the fix (SOUND.md, "Determinism").
"""
import os
import shutil
import subprocess

import numpy as np

QUALITY = 2  # libvorbis -q:a; each file also carries about 3.4 KB of codebook headers whatever its length


class CodecError(RuntimeError):
    pass


def ffmpeg():
    exe = shutil.which("ffmpeg")
    if not exe:
        raise CodecError("ffmpeg not found (needs ffmpeg with libvorbis and libmp3lame)")
    return exe


def _run(args, data=None):
    p = subprocess.run([ffmpeg(), "-hide_banner", "-loglevel", "error", "-nostdin"] + args, input=data,
                       capture_output=True)
    if p.returncode != 0:
        raise CodecError(p.stderr.decode("utf-8", "replace").strip() or "ffmpeg failed")
    return p.stdout


def to_pcm16(x):
    x = np.clip(np.asarray(x, float), -1.0, 1.0)
    return np.round(x * 32767.0).astype("<i2").tobytes()


def encode_ogg(x, sr, quality=QUALITY):
    """Mono float samples to Ogg Vorbis bytes."""
    return _run(["-f", "s16le", "-ar", str(sr), "-ac", "1", "-i", "pipe:0", "-map_metadata", "-1",
                 "-c:a", "libvorbis", "-q:a", str(quality), "-fflags", "+bitexact", "-flags:a", "+bitexact",
                 "-f", "ogg", "pipe:1"], to_pcm16(x))


def decode(path_or_bytes, sr=None, mono=True):
    """Any audio file (path or bytes) to float64 samples; returns (samples, rate)."""
    if isinstance(path_or_bytes, (bytes, bytearray)):
        src, data = "pipe:0", bytes(path_or_bytes)
    else:
        src, data = path_or_bytes, None
    if sr is None:
        sr = probe_rate(path_or_bytes)
    args = ["-i", src, "-f", "f32le", "-acodec", "pcm_f32le", "-ar", str(sr)]
    if mono:
        args += ["-ac", "1"]
    out = _run(args + ["pipe:1"], data)
    return np.frombuffer(out, dtype="<f4").astype(np.float64), sr


def probe_rate(path_or_bytes):
    exe = shutil.which("ffprobe")
    if exe is None:
        return 44100
    if isinstance(path_or_bytes, (bytes, bytearray)):
        p = subprocess.run([exe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=sample_rate",
                            "-of", "csv=p=0", "pipe:0"], input=bytes(path_or_bytes), capture_output=True)
    else:
        p = subprocess.run([exe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=sample_rate",
                            "-of", "csv=p=0", path_or_bytes], capture_output=True)
    try:
        return int(p.stdout.decode().strip().splitlines()[0])
    except (ValueError, IndexError):
        return 44100


def probe_channels(path):
    exe = shutil.which("ffprobe")
    if exe is None:
        return 1
    p = subprocess.run([exe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                        "-of", "csv=p=0", path], capture_output=True)
    try:
        return int(p.stdout.decode().strip().splitlines()[0])
    except (ValueError, IndexError):
        return 1


def ogg_samples(path_or_bytes):
    """The exact length of an Ogg Vorbis stream in samples: the granule position of its last page. (ffmpeg's
    decoders drop the last partial packet, up to a few hundred samples, so a decoded length can be shorter; the game's
    decoder honours the granule.)"""
    data = path_or_bytes if isinstance(path_or_bytes, (bytes, bytearray)) else open(path_or_bytes, "rb").read()
    i = 0
    gran = 0
    while i + 27 <= len(data):
        if data[i:i + 4] != b"OggS":
            raise CodecError("not an Ogg stream")
        g = int.from_bytes(data[i + 6:i + 14], "little", signed=True)
        if g >= 0:
            gran = g
        nseg = data[i + 26]
        segs = data[i + 27:i + 27 + nseg]
        i += 27 + nseg + sum(segs)
    return gran


def encode_mp3(x, sr, path, bitrate="96k"):
    """A preview MP3 (mono). Previews are for listening only, so they are written without bit-exact care."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    _run(["-y", "-f", "s16le", "-ar", str(sr), "-ac", "1", "-i", "pipe:0", "-map_metadata", "-1",
          "-c:a", "libmp3lame", "-b:a", bitrate, "-fflags", "+bitexact", "-flags:a", "+bitexact", path], to_pcm16(x))
