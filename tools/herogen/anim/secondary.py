"""The secondary-motion bake (HEROES_V2.md 13.6): hair, ears, tails, sleeves, skirt panels and capes follow their
parents through damped springs, and the result is written into the clip as keys.

Model: each link of a group is a point mass at the link's tip (pivot + length x rest direction, carried by the bone's
authored pose). It is pulled toward where the authored animation puts that tip by a spring with natural frequency
f = 0.8 + 3.5 x stiffness Hz and damping ratio 0.8 x damping, and damped relative to the target's own velocity, so a
steady motion produces no lag and every acceleration of the parent (a step, a bob, a turn, a swing) leaves the tip
behind and lets it swing through. The spring answers the parent's motion only: the bone's own authored rotation (an
ear twitch, the walk's tail sway) is intended and is added on top unchanged, while the bones below it feel it.
A group with `hang` (0-1) also pulls its tip toward the bind direction in the world, as gravity does: a wide sleeve
keeps hanging when the forearm is raised, a cape and a ponytail fall back toward vertical when the body leans
(`hang_fade` fades that pull out as the parent points the link up, so a bell sleeve on a raised arm stays around it;
`lag` caps how far the spring trails its target). A clip with `wind` (art review: flight) also pulls the tips of the
groups it names toward a direction in the world, the air streaming past a hero who flies or glides: a cape, a tail,
a ponytail or a skirt panel trails behind instead of hanging toward the ground or standing up off a level body. The
tip stays at the link's length from the pivot. The bone's
extra rotation is the turn from the authored direction to the simulated one (clamped to the group's limit),
expressed in the bone's own frame as file angles and added to the authored angles, as GeckoLib adds animation
angles. Links of a chain (a tail, a ponytail, a cape) are solved root first, so each one hangs from the swung one
before it.

The input is sampled every tick (20 Hz, the game's rate) with 4 sub-steps; the result is keyed every 2 ticks (plus
the authored keys' own times) and simplified: keys a straight line reproduces within 0.5 degrees are dropped, and a
bone whose follow-through stays under 0.5 degrees gets no keys at all. A loop is simulated through enough warm-up
cycles (3 s) to reach its steady swing, then its residual is spread over the cycle so the loop closes exactly.

The library bakes the rig's chain bones (tail_*, tail_hair_*, ear_*, hair_back/l/r, sleeve_*, skirt_*, cape_*) on
the reference skeleton (rig pivots of the teen form): rotations transfer to every form. An action is simulated over
its settle clip as authored (authored_view: that clip's own bake is not counted twice) and bakes only the chain bones
below the bones it keys (the others keep the base controller's follow-through). A part's own chain bones
(twin tails, scarf ends) and its stiffness and damping are written to visual.json for the procedural lag (A2).
"""
import math
import os

import numpy as np

from anim.clip import TICK, Key, Track, layered, simplify
from rot import about, align, file_angles, file_matrix
from util import HEROSRC, HerogenError, load_json

CONFIG = os.path.join(HEROSRC, "anim", "secondary.json")
SUBSTEPS = 4
KEY_EVERY = 2               # ticks
TOL = 0.5                   # degrees
WARM_SECONDS = 3.0


class Link:
    def __init__(self, bone, direction, length, group):
        self.bone = bone
        d = np.asarray(direction, dtype=float)
        self.dir = d / np.linalg.norm(d)
        self.length = float(length)
        self.group = group


class Group:
    def __init__(self, name, spec, skeleton):
        self.name = name
        self.hang = float(spec.get("hang", 0.0))
        self.stiffness = float(spec["stiffness"])
        self.damping = float(spec["damping"])
        self.limit = float(spec["limit"])
        self.lag = float(spec.get("lag", self.limit))
        fade = spec.get("hang_fade")
        self.hang_fade = (float(fade[0]), float(fade[1])) if fade else None
        self.omega = 2.0 * math.pi * (0.8 + 3.5 * self.stiffness)
        self.zeta = 0.8 * self.damping
        tip = np.asarray(spec["tip"], dtype=float)
        tip_len = float(spec["tip_len"])
        self.links = []
        if "chain" in spec:
            names = [b for b in spec["chain"] if skeleton.has(b)]
            for i, b in enumerate(names):
                if i + 1 < len(names):
                    v = skeleton.pivot[names[i + 1]] - skeleton.pivot[b]
                    self.links.append(Link(b, v, np.linalg.norm(v), self))
                else:
                    self.links.append(Link(b, tip, tip_len, self))
        else:
            for b in spec["bones"]:
                if skeleton.has(b):
                    d = np.array(tip)
                    if spec.get("mirror_x") and skeleton.pivot[b][0] < 0:
                        d[0] = -d[0]
                    self.links.append(Link(b, d, tip_len, self))


def load_groups(skeleton, path=CONFIG):
    check_config(path)
    spec = load_json(path)["groups"]
    return [Group(name, spec[name], skeleton) for name in sorted(spec)]


def chain_bones(path=CONFIG):
    """Every bone the library bake may key."""
    out = []
    for g in load_json(path)["groups"].values():
        out += list(g.get("chain", g.get("bones", [])))
    return sorted(out)


def _local(skeleton, bone, rot, pos):
    rest = skeleton.rest[bone]
    m = file_matrix((rest[0] + rot[0], rest[1] + rot[1], rest[2] + rot[2]))
    piv = skeleton.pivot[bone]
    return m, np.asarray(pos, dtype=float) + piv - m @ piv


def _rotate_toward(a, b, max_deg):
    """b turned back toward a so the angle between them is at most max_deg."""
    c = float(np.clip(np.dot(a, b), -1.0, 1.0))
    ang = math.degrees(math.acos(c))
    if ang <= max_deg:
        return b
    m = align(a, b)
    axis = np.array([m[2, 1] - m[1, 2], m[0, 2] - m[2, 0], m[1, 0] - m[0, 1]])
    n = np.linalg.norm(axis)
    if n < 1e-12:
        return a
    return about(axis / n, max_deg) @ a


def authored_view(clip):
    """The clip as authored, before its own bake: its chain bones carry only the keys its recipe gave them (an ear
    twitch), not the follow-through baked into them. An action is simulated over this view of the clip it plays
    over, because its own bake recomputes the follow-through from the whole pose: over the baked base it would add
    the base's lag and hang a second time (a wide sleeve hanging 130 degrees off the forearm instead of 65)."""
    authored = clip.meta.get("authored")
    if authored is None:
        return clip
    view = clip.copy()
    chain = set(chain_bones())
    for bc in [bc for bc in view.tracks if bc[0] in chain]:
        del view.tracks[bc]
    for bc, tr in authored.items():
        view.tracks[bc] = tr
    return view


def hang_weight(group, carried, bind):
    """How strongly gravity pulls this link toward its bind direction. With `hang_fade` [lo, hi] the pull fades out
    as the carried direction turns up past the bind's horizontal (the cosine to the bind direction from hi down to
    lo): a rigid bell sleeve on a forearm raised overhead stays around the forearm, because hanging it 85 degrees off
    the arm shows its open end as a disc; it droops once the forearm comes down toward horizontal."""
    if not group.hang:
        return 0.0
    if group.hang_fade is None:
        return group.hang
    lo, hi = group.hang_fade
    x = (float(np.dot(carried, bind)) - lo) / (hi - lo)
    x = min(1.0, max(0.0, x))
    return group.hang * x * x * (3.0 - 2.0 * x)


def below(skeleton, bone, movers):
    """True when the bone or one of its ancestors is in `movers` (the bones an action keys)."""
    return any(b in movers for b in skeleton.chain_to_root(bone))


def pose_function(clip, base=None, d=0.0):
    """The pose shown at time t on a model whose FORM_VAR is d (anim.clip): the clip alone, or the clip over a base
    clip (an action over its idle)."""
    if base is None:
        return lambda t: clip.sample(t, d)

    def pose(t):
        return layered(base, clip, t, base_t=t % base.length if base.loop == "loop" else min(t, base.length), d=d)
    return pose


class Wind:
    """The air streaming past a flying or gliding hero (a recipe's `wind`, art review): a direction in the world (file
    space: +z is behind the hero, +y up), a strength 0-1 (how far the tip leans from where its parent and gravity put
    it toward that direction, like `hang`), the groups it acts on, a limit that replaces a smaller group limit for
    those groups (a tail may trail 80 degrees off its pose in the air, while 35 keeps it tidy on the ground), and a
    `ramp` [t0, t1] (the strength rises from 0 to full: a take-off) or a `fade` [t0, t1] (full to 0: a landing)."""

    def __init__(self, spec, where="wind"):
        unknown = sorted(set(spec) - {"dir", "strength", "groups", "limit", "ramp", "fade"})
        if unknown:
            raise HerogenError("%s: unknown field(s) %s" % (where, ", ".join(unknown)))
        d = np.asarray(spec.get("dir", [0, 0, 1]), dtype=float)
        if d.shape != (3,) or np.linalg.norm(d) < 1e-9:
            raise HerogenError("%s: dir must be a non-zero [x, y, z]" % where)
        self.dir = d / np.linalg.norm(d)
        self.strength = float(spec.get("strength", 0.6))
        if not 0.0 < self.strength <= 1.0:
            raise HerogenError("%s: strength must be in (0, 1]" % where)
        self.groups = set(spec.get("groups", WIND_GROUPS))
        self.limit = float(spec.get("limit", 80))
        self.ramp = tuple(spec["ramp"]) if "ramp" in spec else None
        self.fade = tuple(spec["fade"]) if "fade" in spec else None
        for k in ("ramp", "fade"):
            r = getattr(self, k)
            if r is not None and not (len(r) == 2 and r[0] < r[1]):
                raise HerogenError("%s: %s must be [t0, t1] with t0 < t1" % (where, k))

    def at(self, t):
        s = self.strength
        if self.ramp is not None:
            s *= min(1.0, max(0.0, (t - self.ramp[0]) / (self.ramp[1] - self.ramp[0])))
        if self.fade is not None:
            s *= 1.0 - min(1.0, max(0.0, (t - self.fade[0]) / (self.fade[1] - self.fade[0])))
        return s

    def limit_of(self, group):
        return max(group.limit, self.limit) if group.name in self.groups else group.limit


# the groups a wind acts on by default: the long hanging and trailing parts (ears, hair and sleeves keep their pose)
WIND_GROUPS = ("tail", "ponytail", "cape", "skirt")


def simulate(clip, skeleton, groups, base=None, movers=None, wind=None):
    """{bone: [offset (rx, ry, rz) per tick 0..N]} for the clip's length (N = length / tick). `base` is the clip an
    action plays over (its authored view, see authored_view); with `movers`, only the links below those bones are
    simulated; `wind` (a Wind) pulls the tips of its groups toward the air's direction."""
    pose_at = pose_function(clip, base, skeleton.d)
    links = [ln for g in groups for ln in g.links if movers is None or below(skeleton, ln.bone, movers)]
    if not links:
        return {}
    n_ticks = int(round(clip.length / TICK))
    loop = clip.loop == "loop"
    warm = int(math.ceil(WARM_SECONDS / clip.length)) if loop else 0
    total = warm * n_ticks + n_ticks
    state = {}
    prev = {}
    out = {ln.bone: [] for ln in links}
    for step in range(total + 1):
        t = (step % n_ticks) * TICK if loop else min(step, n_ticks) * TICK
        pose = pose_at(t)
        world = skeleton.world(pose, only=[skeleton.parent[ln.bone] for ln in links])
        final = {}
        for ln in links:
            parent = skeleton.parent[ln.bone]
            rp, tp = final.get(parent) or world[parent]
            chans = pose.get(ln.bone, {})
            arot = chans.get("rotation", (0.0, 0.0, 0.0))
            apos = chans.get("position", (0.0, 0.0, 0.0))
            # the spring answers the parent's motion; the bone's own authored turn (an ear twitch, a tail sway) is
            # intended motion and is added on top, so the bake neither smears nor lags it
            r_link = rp @ file_matrix(skeleton.rest[ln.bone])
            pivot = rp @ (np.asarray(apos) + skeleton.pivot[ln.bone]) + tp
            carried = r_link @ ln.dir
            g_hang = hang_weight(ln.group, carried, ln.dir)
            if g_hang > 0.0:
                # a hanging part (a wide sleeve, a cape, a ponytail) also leans toward its bind direction in the
                # world, as gravity would pull it when the parent lifts or tips it
                want = (1.0 - g_hang) * carried + g_hang * ln.dir
                n = np.linalg.norm(want)
                carried = _rotate_toward(carried, want / n if n > 1e-9 else carried, ln.group.limit)
            lim = wind.limit_of(ln.group) if wind is not None else ln.group.limit
            if wind is not None and ln.group.name in wind.groups:
                w = wind.at(t)
                if w > 0.0:
                    want = (1.0 - w) * carried + w * wind.dir
                    n = np.linalg.norm(want)
                    carried = _rotate_toward(carried, want / n if n > 1e-9 else carried, lim)
            target = pivot + carried * ln.length
            g = ln.group
            if ln.bone not in state:
                state[ln.bone] = (target.copy(), np.zeros(3))
                prev[ln.bone] = (pivot, target)
            x, v = state[ln.bone]
            p0, y0 = prev[ln.bone]
            ydot = (target - y0) / TICK
            pdot = (pivot - p0) / TICK
            h = TICK / SUBSTEPS
            for k in range(1, SUBSTEPS + 1):
                f = k / SUBSTEPS
                y = y0 + (target - y0) * f
                p = p0 + (pivot - p0) * f
                a = g.omega ** 2 * (y - x) + 2.0 * g.zeta * g.omega * (ydot - v)
                v = v + a * h
                x = x + v * h
                u = x - p
                nu = np.linalg.norm(u)
                if nu > 1e-9:
                    u = u / nu
                    x = p + u * ln.length
                    rel = v - pdot
                    v = v - u * float(np.dot(rel, u))
            da = r_link @ ln.dir                    # where the authored pose carries the tip
            db = (x - pivot) / ln.length
            db = _rotate_toward(carried, db / np.linalg.norm(db), g.lag)      # the lag behind the target
            db = _rotate_toward(da, db, lim)                                   # the turn off the authored pose
            x = pivot + db * ln.length
            state[ln.bone] = (x, v)
            prev[ln.bone] = (pivot, target)
            o_world = align(da, db)
            o_local = r_link.T @ o_world @ r_link
            off = file_angles(o_local, digits=4)
            if step >= warm * n_ticks:
                out[ln.bone].append(off)
            tot = (arot[0] + off[0], arot[1] + off[1], arot[2] + off[2])
            mf, tlf = _local(skeleton, ln.bone, tot, apos)
            final[ln.bone] = (rp @ mf, rp @ tlf + tp)
    if loop:
        for b, offs in out.items():
            first, last = np.array(offs[0]), np.array(offs[-1])
            n = len(offs) - 1
            out[b] = [tuple(np.array(o) - (last - first) * (i / n)) for i, o in enumerate(offs)]
    return out


def bake(clip, skeleton, groups=None, tol=TOL, base=None, movers=None, wind=None):
    """Add the follow-through to the clip's chain bones (in place); with `base`, the clip is an action simulated over
    that clip (as authored: authored_view) and a chain bone it does not key starts from the base's authored keys.
    With `movers` (an action: the bones it keys), only the chain bones below them are baked; the others keep the
    base controller's own follow-through (GeckoLib overrides per bone, so an action that keyed the skirt panels
    would freeze the walk's skirt sway under a swing). Returns {bone: peak offset}."""
    groups = groups if groups is not None else load_groups(skeleton)
    if base is not None:
        base = authored_view(base)
    offsets = simulate(clip, skeleton, groups, base, movers, wind)
    peaks = {}
    n_ticks = int(round(clip.length / TICK))
    for bone, offs in sorted(offsets.items()):
        peak = max(max(abs(a) for a in o) for o in offs)
        peaks[bone] = peak
        if peak < tol:
            continue
        own = clip.tracks.get((bone, "rotation"))
        under = base.tracks.get((bone, "rotation")) if base is not None and own is None else None

        def authored_at(t, own=own, under=under):
            if own is not None:
                return own.sample(t)
            if under is not None:
                return under.sample(t % base.length if base.loop == "loop" else min(t, base.length))
            return (0.0, 0.0, 0.0)

        times = {round(i * TICK, 6) for i in range(0, n_ticks + 1, KEY_EVERY)}
        times.add(round(clip.length, 6))
        keep = set()
        if own is not None:
            for k in own.keys:
                times.add(round(k.t, 6))
                keep.add(round(k.t, 6))
        if (own is not None and not own.is_linear()) or (under is not None and not under.is_linear()):
            times |= {round(i * TICK, 6) for i in range(n_ticks + 1)}   # sharp keys (an ear twitch): every tick

        def off_at(t):
            f = t / TICK
            i = min(int(math.floor(f)), n_ticks - 1)
            w = f - i
            a, b = offs[i], offs[i + 1]
            return tuple(a[c] + (b[c] - a[c]) * w for c in range(3))

        tr = Track()
        for t in sorted(times):
            a = authored_at(t)
            o = off_at(t)
            tr.put(Key(t, tuple(a[c] + o[c] for c in range(3))))
        clip.tracks[(bone, "rotation")] = simplify_keeping(tr, tol, keep)
    clip.meta["secondary_peaks"] = {b: round(float(p), 2) for b, p in peaks.items()}
    return peaks


def simplify_keeping(track, tol, keep_times):
    """simplify(), but the keys at `keep_times` survive (authored poses stay exact)."""
    if not keep_times:
        return simplify(track, tol)
    keys = track.keys
    cut = [i for i, k in enumerate(keys) if round(k.t, 6) in keep_times or i in (0, len(keys) - 1)]
    out = Track()
    for a, b in zip(cut, cut[1:]):
        seg = simplify(Track(keys[a:b + 1]), tol)
        for k in seg.keys:
            out.put(k)
    if len(cut) == 1:
        out.put(keys[0])
    return out


def check_config(path=CONFIG):
    spec = load_json(path)
    for name, g in spec["groups"].items():
        for k in ("stiffness", "damping", "limit", "tip", "tip_len"):
            if k not in g:
                raise HerogenError("%s: group %s has no %s" % (path, name, k))
        if ("chain" in g) == ("bones" in g):
            raise HerogenError("%s: group %s needs exactly one of chain, bones" % (path, name))
        fade = g.get("hang_fade")
        if fade is not None and not (len(fade) == 2 and -1.0 <= fade[0] < fade[1] <= 1.0):
            raise HerogenError("%s: group %s hang_fade must be [lo, hi] cosines with lo < hi" % (path, name))
