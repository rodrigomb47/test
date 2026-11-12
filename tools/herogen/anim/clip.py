"""Keys, tracks and clips with GeckoLib 4.9.3's keyframe semantics.

What GeckoLib does with an animation file (read from its BakedAnimationsAdapter, KeyFramesAdapter and EasingType with
`javap -c`, jar SHA-256 20a1995e...1c47, the one HEROES_V2.md 4.2 records):
- each bone has `rotation`, `position` and `scale` channels; a channel is a map of time strings to keys, read in FILE
  ORDER: a key's segment runs from the previous key's time to its own, so the map must be in ascending time order, and
  a "0" key that is not the first entry is an error;
- a key is a 3-vector, or {"vector": [...], "easing": name, "easingArgs": [...]}, or Bedrock's {"pre": [...], "post":
  [...]} (a jump: `pre` is placed 0.001 s before the key, `post` at it);
- the easing of a key shapes the segment that ARRIVES at it; the value before the first key is the first key's value,
  after the last key the last key's value; values are interpolated per component;
- rotation keys are file degrees (GeckoLib negates x and y and converts to radians, like a bone's rest rotation), so
  they add to the bone's rest rotation exactly as rot.file_matrix describes; position keys are file pixels added to
  the bone's position;
- easing names are matched lower-case; an unknown name falls back to linear. `step` divides the segment into 2 steps
  by default: the value sits at the MIDPOINT for the second half of the segment, so a jump between two face frames
  through `step` would show a third frame for half the segment. herogen writes jumps as pre/post keys instead.
- a component may be a Molang expression instead of a number (MathParser.parseJson; a non-constant position value is
  used as it is, in pixels). An unknown variable is created on first use with the value 0 (MolangQueries
  .getVariableFor), so a file never fails to load for a variable the model does not set.

Form-scaled keys (G4): a pixel offset that depends on the legs' length (the root drop of a kneel, a sit, a crouch) has
no single value that fits a chibi (hip 12 px) and an adult (hip 24.5 px). Such a key carries `k`, pixels per pixel of
FORM_VAR, the model's hip height minus HIP_MID (the middle of the three forms' hip heights): its value on a model is
v + k x FORM_VAR. HeroModel sets FORM_VAR every frame from the baked model (the `leg_r` bone's pivot y is the hip
height); on a model that does not set it the value is v, the middle-form compromise (the variable reads 0). The writer
turns such a component into "v + k * v.fc_dhip".
"""
import math

TICK = 0.05                     # one game tick in seconds
FORM_VAR = "v.fc_dhip"          # the Molang variable of form-scaled keys: the model's hip height minus HIP_MID (px)
HIP_MID = 18.25                 # the middle of the three forms' hip heights (chibi 12, adult 24.5): FORM_VAR = 0
FORM_SPAN = 6.25                # the largest |FORM_VAR| of the six bases (simplify's error bound for k)
TIME_EPS = 1e-6
CHANNELS = ("rotation", "position", "scale")
LOOPS = ("once", "loop", "hold")


# ------------------------------------------------------------------ easings (GeckoLib 4.9.3 EasingType)

def _sine(t):
    return 1.0 - math.cos(t * math.pi / 2.0)


def _quad(t):
    return t * t


def _cubic(t):
    return t * t * t


def _quart(t):
    return t ** 4


def _quint(t):
    return t ** 5


def _expo(t):
    return 2.0 ** (10.0 * (t - 1.0))


def _circ(t):
    return 1.0 - math.sqrt(max(0.0, 1.0 - t * t))


def _back(t, n=1.70158):
    return t * t * ((n + 1.0) * t - n)


def _ease_out(f):
    return lambda t: 1.0 - f(1.0 - t)


def _ease_in_out(f):
    return lambda t: f(t * 2.0) / 2.0 if t < 0.5 else 1.0 - f((1.0 - t) * 2.0) / 2.0


def _step(t, n=2):
    """GeckoLib's step(n): 0 up to the first step, then k/n, never 1 inside the segment."""
    if t < 0:
        return 0.0
    s = 1.0 / n
    if t > (n - 1) * s:
        return (n - 1) * s
    return max(0, math.ceil(t / s - 1e-12) - 1) * s


_KINDS = {"Sine": _sine, "Quad": _quad, "Cubic": _cubic, "Quart": _quart, "Quint": _quint, "Expo": _expo,
          "Circ": _circ, "Back": _back}
EASINGS = {"linear": lambda t: t, "step": _step}            # canonical (camelCase) name -> function of [0, 1]
for _k, _f in _KINDS.items():
    EASINGS["easeIn" + _k] = _f
    EASINGS["easeOut" + _k] = _ease_out(_f)
    EASINGS["easeInOut" + _k] = _ease_in_out(_f)
# Every name GeckoLib 4.9.3 registers (lower-case); the elastic, bounce and catmullrom ones are valid in a file but
# herogen neither writes nor samples them.
GECKO_EASINGS = frozenset(["linear", "none", "step", "catmullrom"] + [
    "ease%s%s" % (m, k) for m in ("in", "out", "inout")
    for k in ("sine", "quad", "cubic", "quart", "quint", "expo", "circ", "back", "elastic", "bounce")])
_BY_LOWER = {k.lower(): k for k in EASINGS}


def canonical_easing(name):
    """The camelCase name herogen writes for an easing (GeckoLib reads names case-insensitively)."""
    if name is None:
        return "linear"
    key = _BY_LOWER.get(str(name).lower())
    if key is None:
        raise ValueError("easing %r is not one herogen writes (%s)" % (name, ", ".join(sorted(EASINGS))))
    return key


def ease(name, t):
    return EASINGS[canonical_easing(name)](min(1.0, max(0.0, t)))


# ------------------------------------------------------------------ keys and tracks

def _vec(v):
    if isinstance(v, (int, float)):
        return (float(v), float(v), float(v))
    if len(v) != 3:
        raise ValueError("a key needs 3 values, got %r" % (v,))
    return tuple(float(x) for x in v)


class Key:
    """One keyframe: time (s), value (3 floats), the easing that arrives at it and, for a jump, the value just before
    it (`pre`, written as a Bedrock pre/post key). `k` (3 floats or None) scales the value with the model's legs: the
    value on a model is v + k x FORM_VAR (module docstring)."""
    __slots__ = ("t", "v", "ease", "pre", "k")

    def __init__(self, t, v, ease="linear", pre=None, k=None):
        self.t = float(t)
        self.v = _vec(v)
        self.ease = canonical_easing(ease)
        self.pre = _vec(pre) if pre is not None else None
        k = _vec(k) if k is not None else None
        self.k = k if k is not None and any(abs(x) > 1e-9 for x in k) else None

    def at(self, d=0.0):
        """The value on a model whose FORM_VAR is d."""
        if self.k is None or not d:
            return self.v
        return tuple(self.v[c] + self.k[c] * d for c in range(3))

    def copy(self):
        return Key(self.t, self.v, self.ease, self.pre, self.k)

    def __repr__(self):
        return "Key(%g, %r, %s%s%s)" % (self.t, self.v, self.ease, ", pre=%r" % (self.pre,) if self.pre else "",
                                       ", k=%r" % (self.k,) if self.k else "")


class Track:
    """The keys of one bone channel, kept in time order (one key per time)."""

    def __init__(self, keys=()):
        self.keys = []
        for k in keys:
            self.put(k)

    def put(self, key):
        for i, k in enumerate(self.keys):
            if abs(k.t - key.t) < TIME_EPS:
                self.keys[i] = key
                return key
            if k.t > key.t:
                self.keys.insert(i, key)
                return key
        self.keys.append(key)
        return key

    def set(self, t, v, ease="linear", pre=None, k=None):
        return self.put(Key(t, v, ease, pre, k))

    def copy(self):
        return Track(k.copy() for k in self.keys)

    def _expanded(self, d=0.0):
        out = []
        for k in self.keys:
            if k.pre is not None and k.t > 0:     # GeckoLib shows a jump at 0 as its `post` value from the start
                out.append((k.t - 0.001, k.pre, "linear"))
            out.append((k.t, k.at(d), k.ease))
        return out

    def sample(self, t, d=0.0):
        """The value GeckoLib computes at time t (seconds) on a model whose FORM_VAR is d."""
        e = self._expanded(d)
        if not e:
            return (0.0, 0.0, 0.0)
        if t <= e[0][0]:
            return e[0][1]
        for i in range(1, len(e)):
            t1, v1, ez = e[i]
            if t < t1:
                t0, v0, _ = e[i - 1]
                span = t1 - t0
                f = ease(ez, (t - t0) / span) if span > 0 else 1.0
                return tuple(a + (b - a) * f for a, b in zip(v0, v1))
        return e[-1][1]

    def sample_k(self, t):
        """The FORM_VAR coefficient at time t (the value is linear in it: sample(t, d) = sample(t) + d x this)."""
        if not self.scaled():
            return (0.0, 0.0, 0.0)
        a, b = self.sample(t, 0.0), self.sample(t, 1.0)
        return tuple(b[c] - a[c] for c in range(3))

    def scaled(self):
        return any(k.k is not None for k in self.keys)

    @property
    def start(self):
        return self.keys[0].t if self.keys else 0.0

    @property
    def end(self):
        return self.keys[-1].t if self.keys else 0.0

    def is_linear(self):
        return all(k.ease == "linear" and k.pre is None for k in self.keys)

    def max_abs(self):
        return max((abs(x) for k in self.keys for x in k.v + (k.pre or ()) + tuple(FORM_SPAN * abs(c)
                                                                                    for c in (k.k or ()))),
                   default=0.0)


def dense_track(fn, t0, t1, step=TICK, kfn=None):
    """A linear track sampling fn(t) -> 3-vector every `step` seconds from t0 to t1 (both ends included); kfn(t), if
    given, the FORM_VAR coefficient of each key."""
    n = max(1, int(round((t1 - t0) / step)))
    tr = Track()
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        tr.set(round(t, 6), fn(t), k=kfn(t) if kfn else None)
    return tr


def simplify(track, tol):
    """Drop keys of a linear track that a straight line between their neighbours reproduces within `tol` (on every
    component): Ramer-Douglas-Peucker, deterministic. Tracks with eased or jump keys are returned unchanged."""
    keys = track.keys
    if len(keys) <= 2 or not track.is_linear():
        return track.copy()
    keep = [False] * len(keys)
    keep[0] = keep[-1] = True
    stack = [(0, len(keys) - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        ka, kb = keys[a], keys[b]
        worst, at = -1.0, -1
        kka, kkb = ka.k or (0.0,) * 3, kb.k or (0.0,) * 3
        for i in range(a + 1, b):
            f = (keys[i].t - ka.t) / (kb.t - ka.t)
            ki = keys[i].k or (0.0,) * 3
            # a form-scaled key is kept if the line misses it on any form (|FORM_VAR| up to FORM_SPAN)
            err = max(abs(keys[i].v[c] - (ka.v[c] + (kb.v[c] - ka.v[c]) * f))
                      + FORM_SPAN * abs(ki[c] - (kka[c] + (kkb[c] - kka[c]) * f)) for c in range(3))
            if err > worst:
                worst, at = err, i
        if worst > tol:
            keep[at] = True
            stack.append((a, at))
            stack.append((at, b))
    return Track(Key(k.t, k.v, k=k.k) for k, kk in zip(keys, keep) if kk)


# ------------------------------------------------------------------ events and clips

class Event:
    """A marker: `instruction` (a GeckoLib custom-instruction keyframe in `timeline`: hit, cast, vfx:<id>, ...),
    `sound` (a sound keyframe, value = the sound event id) or `particle` (value = effect id, locator = bone)."""
    __slots__ = ("t", "kind", "value", "locator")

    def __init__(self, t, kind, value, locator=None):
        if kind not in ("instruction", "sound", "particle"):
            raise ValueError("event kind %r" % kind)
        self.t, self.kind, self.value, self.locator = float(t), kind, str(value), locator

    def __repr__(self):
        return "Event(%g, %s, %s)" % (self.t, self.kind, self.value)


class Clip:
    """One animation: length (s), loop mode (once, loop, hold), tracks keyed by (bone, channel), events and the
    generator's own facts in `meta` (kind base/action, class, settle_to, planted, ground, ...)."""

    def __init__(self, name, length, loop="once", meta=None):
        if loop not in LOOPS:
            raise ValueError("loop %r (%s)" % (loop, ", ".join(LOOPS)))
        self.name = name
        self.length = float(length)
        self.loop = loop
        self.tracks = {}
        self.events = []
        self.meta = dict(meta or {})

    def track(self, bone, channel="rotation"):
        if channel not in CHANNELS:
            raise ValueError("channel %r" % channel)
        return self.tracks.setdefault((bone, channel), Track())

    def keyed(self):
        return sorted(self.tracks)

    def bones(self):
        return sorted({b for b, _ in self.tracks})

    def copy(self, name=None):
        c = Clip(name or self.name, self.length, self.loop, self.meta)
        c.tracks = {k: t.copy() for k, t in self.tracks.items()}
        c.events = [Event(e.t, e.kind, e.value, e.locator) for e in self.events]
        return c

    def sample(self, t, d=0.0):
        """{bone: {channel: value}} at time t (a loop wraps t) on a model whose FORM_VAR is d."""
        if self.loop == "loop" and self.length > 0:
            t = t % self.length if t != self.length else self.length
        out = {}
        for (bone, ch), tr in self.tracks.items():
            out.setdefault(bone, {})[ch] = tr.sample(t, d)
        return out

    def scaled(self):
        """True when a key scales with the model's legs (FORM_VAR)."""
        return any(tr.scaled() for tr in self.tracks.values())

    def instructions(self):
        """{time: [instruction, ...]} of the timeline, in time order."""
        out = {}
        for e in sorted(self.events, key=lambda e: e.t):
            if e.kind == "instruction":
                out.setdefault(e.t, []).append(e.value)
        return out

    def hits(self):
        """Times of the `hit` and `cast` instructions (the moments the server applies the move)."""
        return sorted(e.t for e in self.events if e.kind == "instruction" and e.value in ("hit", "cast"))


def layered(base, action, t, base_t=None, d=0.0):
    """The pose GeckoLib shows while `action` plays on the action controller over `base` on the base controller: the
    action's channels override the base's, bone by bone and channel by channel (HEROES_V2 4.4)."""
    pose = base.sample(t if base_t is None else base_t, d) if base is not None else {}
    for bone, chans in action.sample(t, d).items():
        pose.setdefault(bone, {}).update(chans)
    return pose


def form_var(env):
    """FORM_VAR of a body (its hip height minus HIP_MID): what HeroModel sets for its form-scaled keys."""
    return round(float(env["hip_y"]) - HIP_MID, 4)


def form_var_of_geo(geo):
    """FORM_VAR of a written model, as HeroModel computes it: the `leg_r` bone's pivot y (the hip height) minus
    HIP_MID. `geo` is a geo.GeoModel."""
    return round(float(geo.bones["leg_r"]["pivot"][1]) - HIP_MID, 4)


def on_tick(t):
    return abs(t / TICK - round(t / TICK)) < 1e-6


def fmt_time(t):
    """A keyframe time as Blockbench writes it: "0.0", "0.25", "24.0" (at most 4 decimals)."""
    r = round(float(t), 4)
    if r == 0:
        return "0.0"
    if abs(r - round(r)) < 1e-9:
        return "%d.0" % round(r)
    return ("%.4f" % r).rstrip("0")
