"""Shared helpers: deterministic JSON, PNG writing, hashing and a portable random generator.

Everything herogen writes goes through `dumps_json` and `save_png`, so the same inputs give byte-identical files:
sorted keys, numbers rounded to 3 decimals (whole numbers without ".0", never "-0"), UTF-8, a trailing newline;
PNGs are RGBA without metadata chunks.
"""
import hashlib
import io
import json
import math
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
HEROSRC = os.path.join(ROOT, "herosrc")
GENERATOR = "herogen 1"


class HerogenError(Exception):
    """A problem in a source file, reported to the author as one line."""


# ------------------------------------------------------------------ numbers and JSON

def num(x):
    """Round to 3 decimals; whole values become ints; -0 becomes 0."""
    if isinstance(x, bool):
        return x
    v = round(float(x), 3)
    if v == 0:
        return 0
    if v == math.floor(v) and abs(v) < 1e12:
        return int(v)
    return v


def _norm(obj):
    if isinstance(obj, bool) or obj is None or isinstance(obj, str):
        return obj
    if isinstance(obj, (int, float)):
        return num(obj)
    if isinstance(obj, dict):
        return {str(k): _norm(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_norm(v) for v in obj]
    raise TypeError("cannot write %r to JSON" % (type(obj),))


# Keys written first, in this order (readability of geometry files); every other key follows sorted.
KEY_ORDER = ("format_version", "minecraft:geometry", "description", "identifier", "name", "parent", "pivot",
             "rotation", "origin", "size", "inflate", "uv", "uv_size")
_RANK = {k: i for i, k in enumerate(KEY_ORDER)}


def _keys(d):
    return sorted(d, key=lambda k: (_RANK.get(k, len(_RANK)), k))


def _compact(obj):
    if isinstance(obj, dict):
        return "{" + ", ".join(json.dumps(k, ensure_ascii=False) + ": " + _compact(obj[k]) for k in _keys(obj)) + "}"
    if isinstance(obj, list):
        return "[" + ", ".join(_compact(v) for v in obj) + "]"
    return json.dumps(obj, ensure_ascii=False)


def _pretty(obj, level, width, indent):
    flat = _compact(obj)
    if not isinstance(obj, (dict, list)) or len(flat) + level * indent <= width or not obj:
        return flat
    pad = " " * ((level + 1) * indent)
    end = " " * (level * indent)
    if isinstance(obj, dict):
        items = [pad + json.dumps(k, ensure_ascii=False) + ": " + _pretty(obj[k], level + 1, width, indent)
                 for k in _keys(obj)]
        return "{\n" + ",\n".join(items) + "\n" + end + "}"
    items = [pad + _pretty(v, level + 1, width, indent) for v in obj]
    return "[\n" + ",\n".join(items) + "\n" + end + "]"


def dumps_json(obj, width=110, indent=2):
    """Deterministic, diff-friendly JSON: a fixed key order (KEY_ORDER first, the rest sorted), short values on one
    line."""
    return _pretty(_norm(obj), 0, width, indent) + "\n"


def write_text(path, text):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def write_json(path, obj):
    write_text(path, dumps_json(obj))


def load_json(path):
    """Strict JSON. Keys starting with '_' are comments (format convention) and are dropped."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        raise HerogenError("%s: file not found" % rel(path))
    except json.JSONDecodeError as e:
        raise HerogenError("%s:%d:%d: %s" % (rel(path), e.lineno, e.colno, e.msg))
    return strip_comments(data)


def strip_comments(data):
    if isinstance(data, dict):
        return {k: strip_comments(v) for k, v in data.items() if not (isinstance(k, str) and k.startswith("_"))}
    if isinstance(data, list):
        return [strip_comments(v) for v in data]
    return data


def rel(path):
    try:
        return os.path.relpath(path, ROOT)
    except ValueError:
        return path


# ------------------------------------------------------------------ PNG

def png_bytes(img):
    """RGBA PNG without metadata (Pillow writes IHDR, IDAT and IEND only when no info is passed)."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    buf = io.BytesIO()
    img.save(buf, format="PNG", compress_level=9)
    return buf.getvalue()


def save_png(path, img):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as f:
        f.write(png_bytes(img))


# ------------------------------------------------------------------ hashing and randomness

def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def seed_of(text):
    return int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "little")


_MASK = (1 << 64) - 1


def mix64(z):
    """splitmix64 finaliser: a portable integer hash."""
    z = (z + 0x9E3779B97F4A7C15) & _MASK
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & _MASK
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & _MASK
    return z ^ (z >> 31)


def hash_ints(seed, *values):
    """Deterministic hash of a seed and integers (negative ones too)."""
    h = seed & _MASK
    for v in values:
        h = mix64(h ^ (int(v) & _MASK))
    return h


def hash_unit(seed, *values):
    """A float in [0, 1) from a seed and integers."""
    return (hash_ints(seed, *values) >> 11) / float(1 << 53)


class Rng:
    """splitmix64 generator, identical on every platform and Python version (unlike `random` helpers)."""

    def __init__(self, seed):
        self.state = seed_of(seed) if isinstance(seed, str) else int(seed) & _MASK

    def next64(self):
        self.state = (self.state + 0x9E3779B97F4A7C15) & _MASK
        return mix64(self.state ^ 0x5DEECE66D)

    def random(self):
        return (self.next64() >> 11) / float(1 << 53)

    def randint(self, a, b):
        return a + self.next64() % (b - a + 1)
