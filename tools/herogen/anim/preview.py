"""Animation previews for the owner: filmstrips (frames side by side, one row per hero) and animated GIFs, drawn by
the preview renderer (render/ortho.py) from the built geometry and texture, with GeckoLib's bone transforms and the
face frames the animation's face_ctrl keys pick. An action is shown over the clip it settles on (the base controller
keeps playing under the action controller), with the weapon drawn.
"""
import io
import json
import os

import numpy as np
from PIL import Image, ImageDraw

import build
import previews
from anim import library
from anim.clip import layered
from geo import GeoModel
from render import ortho
from util import ROOT

ANIM_PREVIEW_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "anim")
BG = previews.BG
INK, DIM = previews.INK, previews.DIM


def load_hero(hero):
    """(geometry, texture, hero) of a hero id, or of an in-memory hero object (a preview variant)."""
    hero_id = hero["id"] if isinstance(hero, dict) else hero
    files, b = build.build_files(hero_id, hero if isinstance(hero, dict) else None)
    paths = build.out_paths(hero_id)
    geo = GeoModel(json.loads(files[paths["geo"]]))
    tex = np.array(Image.open(io.BytesIO(files[paths["texture"]])).convert("RGBA"))
    return geo, tex, b.hero


_CLIPS = {}


def _resolve(hero, name):
    key = (hero["id"], json.dumps(hero.get("animations"), sort_keys=True), name)
    if key not in _CLIPS:
        _CLIPS[key] = library.resolve(hero, name)
    return _CLIPS[key]


def pose_at(hero, name, t):
    """(pose for the renderer, face frames, weapon drawn) of animation `name` at time t on this hero."""
    clip = _resolve(hero, name)
    if clip is None:
        raise KeyError("%s has no animation %s" % (hero["id"], name))
    drawn = clip.meta.get("kind") == "action" or name.startswith("combat.")
    if clip.meta.get("kind") == "action":
        base = _resolve(hero, clip.meta.get("settle_to", "move.idle"))
        bt = None
        if base is not None and base.loop == "loop":
            bt = t % base.length
        pose = layered(base, clip, t, bt)
    else:
        pose = clip.sample(t)
    face = (0, 0, 0)
    fc = pose.get("face_ctrl", {}).get("position")
    if fc:
        face = tuple(int(round(v)) - 1 if abs(v - round(v)) < 0.05 and round(v) >= 1 else 0 for v in fc)
    return pose, face, drawn


def render(geo, tex, hero, name, t, yaw=-90.0, scale=4, pitch=8.0, bounds=None):
    pose, face, drawn = pose_at(hero, name, t)
    pose = {b: v for b, v in pose.items() if b in geo.bones}
    hidden = previews.view_hidden(geo, drawn=drawn)
    shift = previews.face_shift(*face)
    img = ortho.render(geo, tex, yaw=yaw, scale=scale, hidden=hidden, uv_shift=shift, tint=previews.tint_of(geo),
                       bounds=bounds, pitch=pitch, pose=pose)
    return Image.fromarray(img, "RGBA")


def bounds_over(geo, hero, name, times, yaw, pitch):
    xs, ys = [], []
    for t in times:
        pose, _, drawn = pose_at(hero, name, t)
        pose = {b: v for b, v in pose.items() if b in geo.bones}
        b = ortho.model_bounds(geo, [yaw], previews.view_hidden(geo, drawn=drawn), pitch, pose)
        xs += [b[0], b[1]]
        ys += [b[2], b[3]]
    return min(xs), max(xs), min(ys), max(ys)


def _ground_bounds(b, foot_pad=1.0):
    """Bounds whose bottom is just below the ground (y 0), so every frame's feet sit on one line."""
    return (b[0], b[1], min(b[2], -foot_pad), b[3])


def filmstrip(hero_ids, name, times, views=(("side", -90.0), ("3/4", -34.0)), scale=3, pitch=8.0, title=None,
              path=None):
    """One row per hero and view, one column per time."""
    font, small = previews._font(16), previews._font(12)
    rows = []
    for hid in hero_ids:
        geo, tex, hero = load_hero(hid)
        hid = hero["id"]
        for label, yaw in views:
            bb = _ground_bounds(bounds_over(geo, hero, name, times, yaw, pitch))
            frames = [render(geo, tex, hero, name, t, yaw, scale, pitch, bb) for t in times]
            rows.append(("%s (%s), %s" % (hero.get("name", hid), hero["art"].get("form", "adult"), label), frames))
    gap = 6
    cell_w = max(f.width for _, fr in rows for f in fr)
    w = 16 + 150 + len(times) * (cell_w + gap)
    lines = _wrap(title or name, max(40, (w - 24) // 8))
    top = 18 + 20 * len(lines)
    h = top + sum(max(f.height for f in fr) + 22 for _, fr in rows)
    sheet = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(sheet)
    for i, line in enumerate(lines):
        d.text((12, 8 + 20 * i), line, fill=INK, font=font)
    y = top
    for label, frames in rows:
        rh = max(f.height for f in frames)
        d.text((12, y + rh // 2), label, fill=DIM, font=small)
        x = 16 + 150
        for t, f in zip(times, frames):
            sheet.alpha_composite(f, (x + (cell_w - f.width) // 2, y + rh - f.height))
            d.text((x + cell_w // 2, y + rh + 4), "%.2f s" % t, fill=DIM, font=small, anchor="ma")
            x += cell_w + gap
        y += rh + 22
    if path:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sheet.save(path, format="PNG", optimize=True)
    return sheet


def _wrap(text, width):
    lines, cur = [], ""
    for word in text.split(" "):
        if cur and len(cur) + 1 + len(word) > width:
            lines.append(cur)
            cur = word
        else:
            cur = (cur + " " + word).strip()
    return lines + ([cur] if cur else [])


def gif(hero_ids, name, t0, t1, fps=20, yaw=-90.0, scale=3, pitch=8.0, path=None, label=True):
    """The heroes side by side playing `name` from t0 to t1."""
    n = max(1, int(round((t1 - t0) * fps)))
    times = [t0 + (t1 - t0) * i / n for i in range(n)]
    heroes = []
    for hid in hero_ids:
        geo, tex, hero = load_hero(hid)
        bb = _ground_bounds(bounds_over(geo, hero, name, times[::max(1, len(times) // 12)] + [times[-1]], yaw, pitch))
        bb = (bb[0] - 2, bb[1] + 2, bb[2], bb[3] + 1)
        heroes.append((geo, tex, hero, bb))
    frames = []
    small = previews._font(12)
    for t in times:
        cells = [render(g, tx, h, name, t, yaw, scale, pitch, bb) for g, tx, h, bb in heroes]
        w = sum(c.width for c in cells) + 12 * (len(cells) + 1)
        hgt = max(c.height for c in cells) + (40 if label else 16)
        im = Image.new("RGBA", (w, hgt), BG)
        x = 12
        for c in cells:
            im.alpha_composite(c, (x, hgt - c.height - 8))
            x += c.width + 12
        if label:
            ImageDraw.Draw(im).text((10, 8), "%s  %.2f s" % (name, t), fill=INK, font=small)
        frames.append(im.convert("RGB").convert("P", palette=Image.Palette.ADAPTIVE, colors=128))
    if path:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(round(1000 / fps)), loop=0,
                       optimize=False, disposal=1)
    return frames


def sword_variant(hero_id):
    """A style fixture with the test sword and the katana library, so the G3 swing can be shown on every form (a
    preview variant built in memory; the fixture files are unchanged)."""
    hero = build.load_hero(hero_id)
    hero = json.loads(json.dumps(hero))
    hero["id"] = hero_id + "_katana"
    hero["art"]["weapon"] = {"prop": "weapons/sword_plain", "hand": "right", "sheathed": "hip"}
    hero.setdefault("animations", {})["style"] = "katana"
    return hero


FORMS_SET = ("style_tansy", "style_wren", "test_rig")          # chibi, teen, adult (with ears, tails and long hair)


def write_all(out=ANIM_PREVIEW_DIR, gifs=True):
    """The G3 preview set: a filmstrip and a GIF of each generated animation on the three forms."""
    paths = {}
    idle = library.core().by_name["move.idle"]
    tw = [t for t, _ in idle.meta.get("twitches", [])]
    idle_times = [0.0, tw[0] if tw else 3.0, 5.6, 7.8, 12.6, 16.6, 18.0, 23.0]
    p = os.path.join(out, "filmstrip_idle.png")
    filmstrip(FORMS_SET, "move.idle", idle_times, views=(("3/4", -34.0),), scale=4,
              title="move.idle (24 s loop): rest, an ear twitch, weight on the right foot, a glance right, a look "
                    "down, weight on the left foot, a glance left, rest", path=p)
    paths["filmstrip_idle"] = p
    p = os.path.join(out, "filmstrip_walk.png")
    filmstrip(FORMS_SET, "move.walk", [i * 0.125 for i in range(8)], scale=3,
              title="move.walk (1.0 s loop): right foot strikes at 0, left at 0.5; springs on the tail, hair, "
                    "sleeves and skirt", path=p)
    paths["filmstrip_walk"] = p
    p = os.path.join(out, "filmstrip_run.png")
    filmstrip(FORMS_SET, "move.run", [i * 0.1 for i in range(6)], scale=3,
              title="move.run (0.6 s loop): forward lean, high knees, pumping arms, a flight phase", path=p)
    paths["filmstrip_run"] = p
    swing = [sword_variant("style_tansy"), sword_variant("style_wren"), "test_rig", "style_garrick"]
    p = os.path.join(out, "filmstrip_attack.png")
    filmstrip(swing, "attack.1", [0.0, 0.1, 0.2, 0.3, 0.35, 0.4, 0.45, 0.55, 0.7, 0.9],
              views=(("3/4", -34.0), ("side", -90.0)), scale=3,
              title="attack.1 (katana, 0.9 s): wind-up to 0.30, the cut lands at 0.40 (hit marker, tick 8), "
                    "follow-through, back on combat.idle at 0.90", path=p)
    paths["filmstrip_attack"] = p
    if gifs:
        for name, heroes, t0, t1, fps, yaw in (
                ("move.walk", FORMS_SET, 0.0, 1.0, 20, -55.0),
                ("move.run", FORMS_SET, 0.0, 0.6, 20, -55.0),
                ("attack.1", swing, 0.0, 0.9, 20, -34.0),
                ("move.idle", FORMS_SET, max(0.0, (tw[0] if tw else 3.0) - 0.5), 9.6, 10, -34.0)):
            p = os.path.join(out, "anim_%s.gif" % name.replace("move.", "").replace(".", "_"))
            gif(heroes, name, t0, t1, fps=fps, yaw=yaw, path=p)
            paths["gif_" + name] = p
    return paths
