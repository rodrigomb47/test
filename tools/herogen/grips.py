"""Hand poses (HEROES_V2 13.3 and 15.2, task G6c): every weapon is shown and checked in each hand pose of its style.

A style's hand poses are the poses named `<style>.*` in the pose library (herosrc/anim/poses/<style>.json): its
stance first, then the key poses its animations are built on (a strike, a guard, a cast, a throw, a slam). G5 builds
the style libraries on these poses; G6c fits the props to them, so a prop that works here works in the animations.

A pose is held the way the game would hold it: a one-second loop of the pose expanded like any recipe, with the
secondary bake, so wide sleeves hang from a raised forearm and a cape falls back (a raw pose would stand them off the
arm as discs). `pose(name, d)` is that clip's first frame on a model whose form variable is d.

Checks (`problems`), for a hero with a weapon, in every hand pose of its style:
- **body**: no cube of the prop in a hand reaches into the head, the torso, a leg or the other arm by more than
  BODY_DEPTH px (the hand that holds it, its forearm, and for a two-handed prop the other hand and forearm are
  excepted: a gauntlet sits on the hand, a shield on the forearm, a reversed blade lies along it);
- **ground**: no cube of the prop lower than GROUND px under the ground (a slam rests the head on the ground);
- **second hand** (`hand: both`): the left hand's grip (the `item_l` locator) within GRIP2 px of the handle around the
  prop's `grip2` point, GRIP2_SLIDE px either way along it (G5: a hand that slides along the handle still holds it;
  a two-handed club is held by both hands in every pose);
- **coplanar faces**: with the weapon drawn, no two coplanar faces of different colour where one of them is the
  prop's (a bone carrying a weapon cube: the hand's prop, a worn glove, a scabbard), from six directions for the
  stance and three for the other poses (the rule of `herogen check`, HeroAssets.tie_report, on these poses).
The poses are solved on the teen form (rotations transfer, lengths do not), so the first three are measured with the
tolerances below on every form; `herogen lint` runs them on the weapon parts and the sample outfits.

G5: `animation_problems` runs the same body, ground and second-hand measures through every tick of a style library's
clips (an action over the base clip it plays over, the weapon drawn or sheathed as its markers say), so the swings
between the hand poses are checked too, not only their keys.
"""
import fnmatch
import functools

import numpy as np

from anim import library as animlib
from anim.skeleton import Skeleton
from parts import IDENT, P_REVERSE, weapon_defaults
from rot import file_matrix

BODY_DEPTH = 1.0        # px a prop may sink into the body (cloth and hair are thicker than that)
GROUND = 1.5            # px a prop may dip under the ground (the forms' arms differ; the teen is exact)
GRIP2 = 2.0             # px between the second hand and the prop's second grip
GRIP2_SLIDE = 1.2       # px the second hand may slide along the handle either way of grip2 (G5)
STEP = 0.45             # sampling step inside a prop cube
TIE_YAWS = (-40.0, 90.0, 180.0)
BODY_BONES = ("head", "chest", "waist", "body", "leg_r", "leg_l", "shin_r", "shin_l", "foot_r", "foot_l",
              "arm_r", "arm_l", "forearm_r", "forearm_l")


def style_poses(style):
    """The hand poses of a style: `<style>.stance` first, then the others by name."""
    if not style:
        return []
    lib = animlib.pose_library()
    names = [n for n in lib.names() if n.startswith(style + ".")]
    first = style + ".stance"
    return ([first] if first in names else []) + [n for n in names if n != first]


@functools.lru_cache(maxsize=None)
def held(name):
    """A pose held for a second, expanded like a base loop (the secondary bake included)."""
    recipe = {"length": 1.0, "loop": True, "keys": [{"t": 0, "pose": name}, {"t": 1.0, "pose": name}]}
    built = animlib._build("_held", {"hold": recipe}, animlib.pose_library(), [animlib.core()], "move.idle", [])
    return built.by_name["hold"]


def pose(name, d=0.0):
    """{bone: {channel: value}} of a held pose on a model whose form variable is d (scene.HeroAssets.form_var)."""
    return held(name).sample(0.0, d)


def style_of_weapon(hero):
    """(style, hand) the hero's weapon is held in: the hero's `animations.style`, the prop's own style otherwise."""
    weapon = (hero.get("art") or {}).get("weapon") or {}
    if not weapon.get("prop"):
        return None, None
    d = weapon_defaults(weapon["prop"])
    style = (hero.get("animations") or {}).get("style") or d["style"]
    return style, weapon.get("hand", d["hand"])


# ------------------------------------------------------------------ geometry

def _subtree(model, roots):
    out = set()
    for name, b in model.bones.items():
        n = name
        while n is not None:
            if n in roots:
                out.add(name)
                break
            n = model.bones[n].parent if n in model.bones else None
    return out


def _samples(c):
    """Points inside a cube (its corners, edges and a grid STEP apart), in rest model coordinates after the cube's own
    rotation."""
    axes = []
    for o, s in zip(c.origin, c.size):
        n = max(1, int(np.ceil(s / STEP)))
        axes.append(np.linspace(o, o + s, n + 1))
    pts = np.array([(x, y, z) for x in axes[0] for y in axes[1] for z in axes[2]], dtype=float)
    if c.rotation is not None:
        pv = np.array(c.pivot, dtype=float)
        pts = pv + (pts - pv) @ file_matrix(c.rotation).T
    return pts


def prop_cubes(model, side):
    """The cubes of the prop in a hand: on weapon_<side> (and the visibility bones under it), and a worn prop's on
    the hand itself."""
    bones = _subtree(model, {"weapon_" + side})
    return [c for c in model.cubes if c.part.startswith("weapons/")
            and (c.bone in bones or c.bone == "hand_" + side)]


def _depth_inside(local, box):
    """How deep each point (in a box's own frame) lies inside the box (0 outside)."""
    lo = np.array(box[:3])
    hi = np.array(box[3:])
    d = np.minimum(local - lo, hi - local)
    return np.clip(d.min(axis=1), 0.0, None)


def measure(model, hero, pose_chans, sides=("r", "l")):
    """{"body": {(prop cube, body bone): depth px}, "low": lowest prop point, "grip2": distance or None} of the
    props in the hands in a pose."""
    sk = Skeleton.from_model(model)
    world = sk.world(pose_chans)
    weapon = hero["art"]["weapon"]
    d = weapon_defaults(weapon["prop"])
    hand = weapon.get("hand", d["hand"])
    body = [c for c in model.cubes if c.part == "base" and c.bone in BODY_BONES]
    out = {"body": {}, "low": None, "grip2": None}
    for side in sides:
        other = "l" if side == "r" else "r"
        skip = {"hand_r", "hand_l", "arm_" + side, "forearm_" + side}
        if hand == "both":
            skip |= {"arm_" + other, "forearm_" + other}
        for c in prop_cubes(model, side):
            r, t = world[c.bone]
            pts = _samples(c) @ r.T + t
            low = float(pts[:, 1].min())
            out["low"] = low if out["low"] is None else min(out["low"], low)
            for b in body:
                if b.bone in skip:
                    continue
                rb, tb = world[b.bone]
                local = (pts - tb) @ rb
                if b.rotation is not None:
                    pv = np.array(b.pivot, dtype=float)
                    local = pv + (local - pv) @ file_matrix(b.rotation)
                depth = float(_depth_inside(local, b.box).max())
                if depth > 0:
                    key = (c.name, b.bone)
                    out["body"][key] = max(out["body"].get(key, 0.0), depth)
    if hand == "both" and d["grip2"] is not None:
        perm = np.array(P_REVERSE if weapon.get("grip", d["grip"]) == "reverse" else IDENT, dtype=float)
        g = perm @ np.array([float(v) for v in d["grip2"]])
        pv = np.array(model.bones["weapon_r"].pivot, dtype=float)
        left = Skeleton.apply(world, "item_l", model.bones["item_l"].pivot)
        # the second hand may slide GRIP2_SLIDE px along the handle either way of grip2 (G5): it still holds it
        n = float(np.linalg.norm(g))
        u = g / n if n > 1e-9 else np.zeros(3)
        ends = [Skeleton.apply(world, "weapon_r", pv + g + k * GRIP2_SLIDE * u) for k in (-1.0, 1.0)]
        seg = ends[1] - ends[0]
        f = float(np.clip((left - ends[0]) @ seg / max(1e-9, float(seg @ seg)), 0.0, 1.0))
        out["grip2"] = float(np.linalg.norm(ends[0] + f * seg - left))
    return out


def problems(hero, built, assets=None, ties=True):
    """The hand-pose problems of a built hero (build.Built) with a weapon; `assets` (scene.HeroAssets of the same
    build) is needed for the coplanar rule."""
    from anim.clip import form_var
    style, hand = style_of_weapon(hero)
    if style is None:
        return []
    model = built.model
    d = form_var(model.env)
    prop_bones = {c.bone for c in model.cubes if c.part.startswith("weapons/")}
    out = []
    for n, name in enumerate(style_poses(style)):
        chans = pose(name, d)
        m = measure(model, hero, chans)
        worst = sorted(m["body"].items(), key=lambda kv: -kv[1])
        if worst and worst[0][1] > BODY_DEPTH:
            out.append("%s: the prop passes through the body (%s)" % (name, ", ".join(
                "%s %.1f px into %s" % (k[0].split(":")[-1], v, k[1]) for k, v in worst[:3] if v > BODY_DEPTH)))
        if m["low"] is not None and m["low"] < -GROUND:
            out.append("%s: the prop reaches %.1f px under the ground" % (name, -m["low"]))
        if m["grip2"] is not None and m["grip2"] > GRIP2:
            out.append("%s: the second hand is %.1f px from the prop's second grip" % (name, m["grip2"]))
        if ties and assets is not None:
            # the stance from six directions; the other poses from three (they differ in the arms only). Only pairs
            # with a bone of the prop count here: the body's own planes are the stance rule's (`herogen check`)
            pairs = assets.tie_report(stances=[(True, chans)], **({} if n == 0 else {"yaws": TIE_YAWS}))
            pairs = {k: v for k, v in pairs.items() if k[0] in prop_bones or k[1] in prop_bones}
            if pairs:
                top = sorted(pairs.items(), key=lambda kv: (-kv[1], kv[0]))[:4]
                out.append("%s: coplanar faces of different colour with the weapon drawn: %s" % (name, ", ".join(
                    "%s/%s %d" % (k[0], k[1], n) for k, n in top)))
    return out


# ------------------------------------------------------------------ G5: the style libraries' clips

ANIM_GRIP2 = 4.0        # px: the second hand between keys (G5: each arm interpolates on its own chain, so between two keys with the hand on the handle it drifts off for a few frames; the keys keep GRIP2)
# the clips that hold a two-handed prop in both hands throughout (the others free the left hand for a moment: a draw,
# a beckon, a victory, a reaction)
GRIP2_CLIPS = ("combat.idle", "combat.run", "attack.*", "guard", "guard.*")
# reactions that lay the body down or fold it (the core's restyled copies): the prop may rest on the ground there
GROUNDED_CLIPS = ("death", "defeated", "defeated.loop", "stagger", "spared")


def clip_drawn(clip, t, start):
    """The weapon flag at clip time t: `start`, swapped by the clip's weapon:drawn / weapon:sheathed markers (the
    server swaps the flag on those ticks, so the pose of that time is the first one with the other copy shown)."""
    state = start
    for e in sorted(clip.events, key=lambda e: e.t):
        if e.kind == "instruction" and e.value in ("weapon:drawn", "weapon:sheathed") and e.t <= t + 1e-9:
            state = e.value == "weapon:drawn"
    return state


def starts_drawn(clip):
    """Whether a style clip starts with the weapon in hand: everything but work strokes (a tool at item_r) and an
    action that plays over move.idle (a draw)."""
    if clip.name.startswith("work."):
        return False
    return clip.meta.get("over") != "move.idle"


def clip_poses(clip, lookup, d, every=1):
    """[(t, pose, drawn)] at every `every` ticks of a clip (and its last tick): an action layered over the base clip
    it plays over (GeckoLib's override per bone and channel), a base loop on its own."""
    from anim.clip import TICK, layered
    over = lookup(clip.meta["over"]) if clip.meta.get("kind") == "action" and clip.meta.get("over") else None
    n = int(round(clip.length / TICK))
    ticks = sorted(set(range(0, n + 1, max(1, every))) | {n})
    start = starts_drawn(clip)
    out = []
    for i in ticks:
        t = i * TICK
        if over is not None:
            bt = t % over.length if over.loop == "loop" else min(t, over.length)
            pose = layered(over, clip, t, bt, d=d)
        else:
            pose = clip.sample(t, d)
        out.append((round(t, 4), pose, clip_drawn(clip, t, start)))
    return out


def animation_problems(hero, built, style_lib, lookup, clips=None, every=1):
    """The props of a built hero with a weapon through the clips of its style library (anim.library.Built): no prop
    cube more than BODY_DEPTH px into the body, none lower than GROUND px under the ground (reactions that lay the
    body down excepted), and for a two-handed prop the second hand within ANIM_GRIP2 px of `grip2` in the clips that
    hold it with both hands. `lookup(name)` resolves the clips actions play over. One line per clip and problem (its
    worst frame)."""
    from anim.clip import form_var
    style, hand = style_of_weapon(hero)
    if style is None:
        return []
    model = built.model
    d = form_var(model.env)
    out = []
    for clip in style_lib.clips:
        if clips is not None and not any(fnmatch.fnmatchcase(clip.name, g) for g in clips):
            continue
        grip2 = any(fnmatch.fnmatchcase(clip.name, g) for g in GRIP2_CLIPS)
        worst_body, worst_low, worst_g2 = (0.0, None, None), (0.0, None), (0.0, None)
        for t, pose, drawn in clip_poses(clip, lookup, d, every):
            if not drawn:
                continue
            m = measure(model, hero, pose)
            for k, v in m["body"].items():
                if v > worst_body[0]:
                    worst_body = (v, k, t)
            if m["low"] is not None and -m["low"] > worst_low[0]:
                worst_low = (-m["low"], t)
            if grip2 and m["grip2"] is not None and m["grip2"] > worst_g2[0]:
                worst_g2 = (m["grip2"], t)
        if worst_body[0] > BODY_DEPTH:
            k = worst_body[1]
            out.append("%s: the prop passes %.1f px into %s (%s) at %.2f s" % (
                clip.name, worst_body[0], k[1], k[0].split(":")[-1], worst_body[2]))
        if worst_low[0] > GROUND and clip.name not in GROUNDED_CLIPS:
            out.append("%s: the prop reaches %.1f px under the ground at %.2f s" % (clip.name, worst_low[0],
                                                                                   worst_low[1]))
        if worst_g2[0] > ANIM_GRIP2:
            out.append("%s: the second hand is %.1f px from the prop's second grip at %.2f s" % (
                clip.name, worst_g2[0], worst_g2[1]))
    return out
