"""Preview sheets for the owner (rig v3 style set): the base bodies, each style character turned round (front, 3/4,
side, back, 3/4 back, a blink, the far level of detail, the weapon drawn), a lineup of the characters side by side on
grass under a sky (the way the owner's reference lineup is shot), face close-ups, the six calm faces side by side
and the texture layout. Every model is drawn at the same model-pixel scale, which is the same world scale because rig
v3 draws every form at one render scale. They are rendered from the built files (geometry + PNG), not from the in-memory model, so they show
what the game gets. G2 replaces the renderer with the full one (animations, vanilla lighting, contact sheets, GIFs).
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import build
import face as F
from geo import GeoModel
from render import ortho
from rig import BASE_IDS, load_rig
from util import ROOT

PREVIEW_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "style")
BG = (156, 168, 186, 255)          # a light blue-grey, so dark clothes and light hair both read
INK = (22, 26, 34, 255)
DIM = (48, 54, 66, 255)
SASH_TINT = (74, 118, 196)
VIEWS = (("front", 0.0), ("3/4", -34.0), ("side", -90.0), ("back", 180.0), ("3/4 back", 145.0))
PITCH = 10.0                 # camera a little above the hero, like a player looking at it
# The previews show the idle stance the animations will use (the arms a little out, an A-pose like the references), not
# the straight-armed rest pose of the geometry: file degrees added to the bones' rest rotation.
IDLE_POSE = {"arm_r": (0.0, 0.0, 9.0), "arm_l": (0.0, 0.0, -9.0)}

# A mannequin for the bare bases: neutral base layer, short hair, default face of the family.
MANNEQUIN_PALETTE = {"skin": "#F1D0B8", "hair": "#4A3F3A", "eyes": "#4F78C8", "secondary": "#A3AABB",
                     "accent": "#5A6072", "leather": "#4B3D35"}


def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:          # Pillow < 10.1
        return ImageFont.load_default()


def mannequin(base_id, build_name="standard"):
    form, fam = base_id.split("_")
    family = "female" if fam == "f" else "male"
    return {"format": 2, "id": "base_" + base_id, "name": base_id, "gender": family,
            "art": {"base": family, "form": form, "build": build_name, "palette": MANNEQUIN_PALETTE,
                    "parts": [{"part": "hair/short_crop", "color": "hair"}]},
            "animations": {"blink": "normal"}}


def built_geo(files, hero_id):
    return GeoModel(json.loads(files[build.out_paths(hero_id)["geo"]]))


def view_hidden(geo, phase=1, drawn=False, far=False):
    """Bones hidden in a view: other phases, the sheathed or the drawn weapon, and with far=True every fine_ bone
    (what the renderer still draws beyond the fine distance, HEROES_V2 4.9)."""
    hidden = [n for n in geo.order if n.startswith("p2_") and phase < 2 or n.startswith("p3_") and phase < 3]
    if far:
        hidden += [n for n in geo.order if n.startswith("fine_")]
    if drawn:
        hidden.append("weapon_back")
    else:
        hidden += ["weapon_r", "weapon_l"]
    return hidden


def tint_of(geo, tint=SASH_TINT):
    return {n: tint for n in geo.order if n == "sash" or n.startswith("fine_sash")}


def face_shift(eyes=0, mouth=0, cheeks=0):
    rig = load_rig()
    shift = {}
    for plane, k in (("eyes", eyes), ("mouth", mouth), ("cheeks", cheeks)):
        u, v, _, _ = F.frame_rect(rig, plane, k)
        u0, v0, _, _ = F.frame_rect(rig, plane, 0)
        shift[plane] = (u - u0, v - v0)
    return shift


def render_views(geo, tex, views, scale, phase=1, drawn=False, eyes=0, mouth=0, cheeks=0, pitch=PITCH,
                 tint=SASH_TINT, ybounds=None, pose=None, far=False):
    """Images of several views with a shared vertical scale (feet on one line)."""
    hidden = view_hidden(geo, phase, drawn, far)
    shift = face_shift(eyes, mouth, cheeks)
    out = []
    pose = IDLE_POSE if pose is None else pose
    yb = ybounds or ortho.model_bounds(geo, [v for _, v in views], hidden, pitch, pose)[2:]
    for label, yaw in views:
        xb = ortho.model_bounds(geo, [yaw], hidden, pitch, pose)
        img = ortho.render(geo, tex, yaw=yaw, scale=scale, hidden=hidden, uv_shift=shift, tint=tint_of(geo, tint),
                           bounds=(xb[0], xb[1], yb[0], yb[1]), pitch=pitch, pose=pose)
        out.append((label, Image.fromarray(img, "RGBA")))
    return out


def _row(images, gap=12):
    w = sum(i.width for _, i in images) + gap * (len(images) - 1)
    h = max(i.height for _, i in images)
    return w, h


def _paste_views(sheet, draw, x, y, images, font, gap=12):
    for label, im in images:
        sheet.alpha_composite(im, (x, y + 18))
        draw.text((x + im.width // 2, y + 2), label, fill=DIM, font=font, anchor="ma")
        x += im.width + gap
    return x


def _save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def _built(hero_id, hero=None):
    files, b = build.build_files(hero_id, hero)
    return built_geo(files, hero_id), b


# ------------------------------------------------------------------ backdrop (sky and grass, like the reference)

def backdrop(w, h, horizon):
    """A flat sky gradient over a blocky grass field (16-px tiles with a little noise), deterministic."""
    img = np.zeros((h, w, 4), dtype=np.uint8)
    t = np.linspace(0.0, 1.0, max(1, horizon))[:, None]
    top, low = np.array([126, 168, 236]), np.array([186, 212, 250])
    img[:horizon, :, :3] = (top + (low - top) * t).astype(np.uint8)[:, None, :] if horizon else 0
    yy, xx = np.mgrid[horizon:h, 0:w]
    depth = (yy - horizon) / max(1.0, h - horizon)
    tile = (6 + 22 * depth).astype(int)                     # tiles grow toward the viewer
    tx, ty = xx // np.maximum(tile, 1), (yy - horizon) // np.maximum(tile, 1)
    noise = ((tx * 73856093) ^ (ty * 19349663)) % 7
    base = np.array([96, 150, 64])
    shade = (noise - 3) * 5 + (depth * 16).astype(int)
    for c in range(3):
        img[horizon:, :, c] = np.clip(base[c] + shade * (1.0 if c != 2 else 0.6), 0, 255).astype(np.uint8)
    img[..., 3] = 255
    return Image.fromarray(img, "RGBA")


def _shadow(size, alpha=90):
    w, h = size
    im = Image.new("RGBA", (w + 16, h + 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([8, 8, 8 + w, 8 + h], fill=(20, 40, 16, alpha))
    return im.filter(ImageFilter.GaussianBlur(3))


def _feet_row(geo, hidden, yaw, pitch, scale, pad, ybounds):
    """Screen row of the ground point (0, 0, 0) in an image rendered with these bounds."""
    view = ortho.view_matrix(yaw, pitch)
    p = view @ np.array([0.0, 0.0, 0.0])
    return int((ybounds[1] - p[1]) * scale + pad)


# ------------------------------------------------------------------ sheets

def bases_sheet(path, scale=7):
    """The six bases (three forms x two families) as a lineup: front, side and back, feet on one line."""
    views = (("front", 0.0), ("side", -90.0), ("back", 180.0))
    built = []
    for base_id in BASE_IDS:
        geo, b = _built("base_" + base_id, mannequin(base_id))
        built.append((base_id, geo, b))
    hidden = view_hidden(built[0][1])
    ytop = max(ortho.model_bounds(g, [v for _, v in views], view_hidden(g), PITCH)[3] for _, g, _ in built)
    ybot = min(ortho.model_bounds(g, [v for _, v in views], view_hidden(g), PITCH)[2] for _, g, _ in built)
    font, small = _font(18), _font(13)
    cells = []
    for base_id, geo, b in built:
        cells.append((base_id, b, render_views(geo, b.image, views, scale, ybounds=(ybot, ytop))))
    cell_w = max(_row(c[2])[0] for c in cells) + 28
    cell_h = max(_row(c[2])[1] for c in cells) + 70
    order = [0, 2, 4, 1, 3, 5]                  # rows: female, male; columns: chibi, teen, adult
    sheet = Image.new("RGBA", (cell_w * 3 + 24, cell_h * 2 + 60), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "Hero rig v3: three forms x two families (standard builds, mannequin palette, short hair)",
           fill=INK, font=font)
    for slot, n in enumerate(order):
        base_id, b, images = cells[n]
        x0 = 12 + (slot % 3) * cell_w
        y0 = 46 + (slot // 3) * cell_h
        env = b.model.env
        d.text((x0 + 8, y0), "%s  (%s)" % (base_id, b.model.form), fill=INK, font=font)
        d.text((x0 + 8, y0 + 22), "%g px, %.2f blocks at render scale %g; head cube %.2f of the height" % (
            env["head_top"], env["head_top"] * env["render_scale"] / 16, env["render_scale"], 10 / env["head_top"]),
            fill=DIM, font=small)
        _paste_views(sheet, d, x0 + 8, y0 + 40, images, small)
    _save(sheet, path)
    del hidden


def hero_sheet(hero_id, path, scale=9, tint=SASH_TINT, title=None):
    geo, b = _built(hero_id)
    font, small = _font(18), _font(13)
    groups = [render_views(geo, b.image, VIEWS, scale, tint=tint)]
    art = b.hero["art"]
    extra = [("blink", 0.0)]
    groups.append(render_views(geo, b.image, extra, scale, eyes=2, tint=tint))
    groups.append(render_views(geo, b.image, (("far: fine_ hidden", -34.0),), scale, tint=tint, far=True))
    if art.get("weapon"):
        groups.append(render_views(geo, b.image, (("weapon drawn", -34.0),), scale, drawn=True, tint=tint))
    if art.get("phases"):
        tex2 = b.p2 if b.p2 is not None else b.image
        glow_eyes = any(p.get("eyes_glow") for p in art["phases"])
        groups.append(render_views(geo, tex2, (("phase 2", -34.0),), scale, phase=2, eyes=10 if glow_eyes else 0,
                                   tint=tint))
    w = 16 + sum(_row(g)[0] + 12 + 24 for g in groups) + 48
    h = max(_row(g)[1] for g in groups) + 96
    sheet = Image.new("RGBA", (max(w, 760), h), BG)
    d = ImageDraw.Draw(sheet)
    m = b.model
    d.text((16, 12), title or b.hero.get("name", hero_id), fill=INK, font=font)
    far = sum(1 for c in m.cubes if not _under_fine(m, c.bone))
    d.text((16, 36), "%s (%s, %s build): %d bones, %d cubes (%d beyond the fine distance), %dx%d texture, %g px tall "
                     "(%.2f blocks); sash tinted as its faction; idle stance (arms 9 degrees out)" % (
        hero_id, m.base_id, m.build, len(m.bones), len(m.cubes), far, m.tex_w, m.tex_h, m.env["head_top"],
        m.env["head_top"] * m.env["render_scale"] / 16), fill=DIM, font=small)
    x = 16
    for g in groups:
        x = _paste_views(sheet, d, x, 62, g, small) + 24
    _save(sheet, path)


def _under_fine(model, bone):
    while bone is not None:
        if bone.startswith("fine_"):
            return True
        bone = model.bones[bone].parent if bone in model.bones else None
    return False


def lineup(ids, path, scale=10, yaw=-24.0, pitch=12.0, tints=None, gap=26, labels=True):
    """The characters side by side on grass under a sky, in a three-quarter view from a little above, feet on one
    line and one scale for all (so the forms' heights compare), with soft ground shadows."""
    renders = []
    for n, hid in enumerate(ids):
        geo, b = _built(hid)
        hidden = view_hidden(geo)
        bx = ortho.model_bounds(geo, [yaw], hidden, pitch, IDLE_POSE)
        renders.append((hid, geo, b, hidden, bx))
    ytop = max(r[4][3] for r in renders)
    ybot = min(r[4][2] for r in renders)
    pad = 2
    imgs = []
    for n, (hid, geo, b, hidden, bx) in enumerate(renders):
        tint = (tints or {}).get(hid, SASH_TINT)
        img = ortho.render(geo, b.image, yaw=yaw, scale=scale, hidden=hidden, uv_shift=face_shift(),
                           tint=tint_of(geo, tint), bounds=(bx[0], bx[1], ybot, ytop), pitch=pitch, pad=pad,
                           pose=IDLE_POSE)
        imgs.append((hid, b, Image.fromarray(img, "RGBA"), _feet_row(geo, hidden, yaw, pitch, scale, pad,
                                                                     (ybot, ytop)), bx))
    body_h = max(i[2].height for i in imgs)
    W = sum(i[2].width for i in imgs) + gap * (len(imgs) + 1) + 40
    lowest = max(i[3] + max(10, int(i[1].model.env["hips_w"] * scale * 2.6) // 3) for i in imgs)
    H = max(body_h + 150, 40 + lowest + 44)
    horizon = int(H * 0.42)
    sheet = backdrop(W, H, horizon)
    d = ImageDraw.Draw(sheet)
    font = _font(16)
    x = gap + 20
    top = 40
    for hid, b, im, feet, bx in imgs:
        # the ground point (0, 0, 0) projects to screen x (bx_max - 0) * scale + pad in render space
        foot_x = x + int((bx[1] - 0.0) * scale + pad)
        sw = int(b.model.env["hips_w"] * scale * 2.6)
        sh = max(10, sw // 3)
        sheet.alpha_composite(_shadow((sw, sh)), (foot_x - sw // 2 - 8, top + feet - sh // 2 - 8))
        sheet.alpha_composite(im, (x, top))
        if labels:
            name = b.hero.get("name", hid)
            env = b.model.env
            d.text((foot_x, top + feet + sh + 10), "%s (%s, %.2f blocks)" % (
                name, b.model.form, env["head_top"] * env["render_scale"] / 16), fill=(250, 250, 250, 255),
                font=font, anchor="ma", stroke_width=2, stroke_fill=(30, 50, 26, 255))
        x += im.width + gap
    _save(sheet, path)


HEAD_MOODS = ("calm", "happy", "angry", "surprised", "sad", "smug")


def face_strip(ids, path, scale=13, moods=HEAD_MOODS):
    """Close-ups of each character's head: the moods from the front, then a three-quarter view (like the owner's
    close-up reference); one row per character."""
    font, small = _font(18), _font(13)
    rows = []
    for hid in ids:
        geo, b = _built(hid)
        table = F.mood_table(b.spec)
        hidden = view_hidden(geo)
        head_y = b.model.env["head_y"]
        cells = []
        for mood in moods:
            t = table[mood]
            img = _head_render(geo, b, hidden, head_y, 0.0, 4.0, scale, face_shift(t["eyes"], t["mouth"], t["cheeks"]))
            cells.append((mood, img))
        cells.append(("blink", _head_render(geo, b, hidden, head_y, 0.0, 4.0, scale, face_shift(2, 0, 0))))
        cells.append(("3/4", _head_render(geo, b, hidden, head_y, -32.0, 8.0, scale, face_shift())))
        rows.append((hid, b, cells))
    cw = max(im.width for _, _, cells in rows for _, im in cells)
    ch = max(im.height for _, _, cells in rows for _, im in cells)
    ncol = max(len(c) for _, _, c in rows)
    sheet = Image.new("RGBA", (ncol * (cw + 10) + 30, len(rows) * (ch + 56) + 50), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "Face close-ups: the 20x20 double-density face (eyes low and wide), the frames of six moods, a blink "
                     "and a 3/4 view", fill=INK, font=font)
    y = 46
    for hid, b, cells in rows:
        d.text((16, y), "%s (%s)" % (b.hero.get("name", hid), b.model.base_id), fill=INK, font=small)
        x = 16
        for label, im in cells:
            sheet.alpha_composite(im, (x + (cw - im.width) // 2, y + 20 + (ch - im.height)))
            d.text((x + cw // 2, y + 22 + ch), label, fill=DIM, font=small, anchor="ma")
            x += cw + 10
        y += ch + 56
    _save(sheet, path)


def _head_render(geo, b, hidden, head_y, yaw, pitch, scale, shift):
    """The head and what hangs from it, cropped from shoulders up."""
    full = ortho.model_bounds(geo, [yaw], hidden, pitch, IDLE_POSE)
    lo = head_y - 4.0
    img = ortho.render(geo, b.image, yaw=yaw, scale=scale, hidden=hidden, uv_shift=shift, tint=tint_of(geo),
                       bounds=(max(full[0], -12.0), min(full[1], 12.0), lo, min(full[3], head_y + 20.0)),
                       pitch=pitch, pad=4, pose=IDLE_POSE)
    return Image.fromarray(img, "RGBA")


def face_lineup(ids, path, scale=26):
    """The calm faces of every character side by side, large, front and 3/4: the sheet to judge whether the faces are
    clearly different. Under each: the face features that differ (eye shape, iris, pupil, lashes, brows, mouth,
    marks, wear)."""
    font, small = _font(18), _font(13)
    cells = []
    for hid in ids:
        geo, b = _built(hid)
        hidden = view_hidden(geo)
        hy = b.model.env["head_y"]
        front = _head_render(geo, b, hidden, hy, 0.0, 2.0, scale, face_shift())
        side = _head_render(geo, b, hidden, hy, -30.0, 6.0, scale * 3 // 4, face_shift())
        sp = b.spec
        marks = ", ".join(m["type"].replace("_", " ") for m in sp["marks"]) or "no marks"
        wear = sp["glasses"] and "%s glasses" % sp["glasses"] or (sp["eyepatch"] and "eye patch") or ""
        lines = ["%s eyes, %s iris, %s pupil" % (sp["eyes"], sp["iris_style"].replace("_", "-"), sp["pupil"]),
                 "lash %d (%s), %s brows" % (sp["lashes"], sp["flick"], sp["brows"].replace("_", " ")),
                 "%s mouth%s; %s%s" % (sp["mouth"], ", fang" if sp["fang"] != "none" else "", marks,
                                       ("; " + wear) if wear else "")]
        cells.append((b, front, side, lines))
    cw = max(f.width for _, f, _, _ in cells)
    sw = max(s.width for _, _, s, _ in cells)
    ch = max(max(f.height, s.height) for _, f, s, _ in cells)
    per_row = 3
    rows = (len(cells) + per_row - 1) // per_row
    W = per_row * (cw + sw + 40) + 30
    H = rows * (ch + 110) + 50
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "Faces side by side (calm): shape, iris, lash, brows, mouth, marks and wear differ for every hero",
           fill=INK, font=font)
    for n, (b, f, sd, lines) in enumerate(cells):
        x = 16 + (n % per_row) * (cw + sw + 40)
        y = 46 + (n // per_row) * (ch + 110)
        d.text((x, y), "%s (%s)" % (b.hero.get("name", b.hero["id"]), b.model.base_id), fill=INK, font=font)
        sheet.alpha_composite(f, (x, y + 26 + ch - f.height))
        sheet.alpha_composite(sd, (x + cw + 10, y + 26 + ch - sd.height))
        for k, line in enumerate(lines):
            d.text((x, y + 30 + ch + k * 16), line, fill=DIM, font=small)
    _save(sheet, path)


def faces_sheet(hero_id, path, zoom=8):
    files, b = build.build_files(hero_id)
    tex = b.image
    rig = b.model.rig
    table = F.mood_table(b.spec)
    font, small = _font(16), _font(12)
    cell = F.FACE * zoom // 2
    ez = zoom // 2
    sheet = Image.new("RGBA", (6 * (cell + 16) + 24, 2 * (cell + 34) + 2 * (F.EH * ez + 30) + 4 * zoom + 190), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "%s: the 12 moods (eyes, mouth and cheek frames over the head front)" % hero_id, fill=INK,
           font=font)
    y = 44
    for i, mood in enumerate(F.MOODS):
        t = table[mood]
        f = Image.fromarray(F.compose_face(tex, rig, t["eyes"], t["mouth"], t["cheeks"]), "RGBA")
        x = 16 + (i % 6) * (cell + 16)
        yy = y + (i // 6) * (cell + 34)
        sheet.alpha_composite(f.resize((cell, cell), Image.NEAREST), (x, yy + 16))
        d.text((x + cell // 2, yy), "%s (%d/%d/%d)" % (mood, t["eyes"], t["mouth"], t["cheeks"]), fill=DIM,
               font=small, anchor="ma")
    y += 2 * (cell + 34) + 8
    d.text((16, y), "eye frames", fill=INK, font=font)
    y += 22
    for k in range(12):
        u, v, w, h = F.frame_rect(rig, "eyes", k)
        im = Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * ez, h * ez), Image.NEAREST)
        x = 16 + (k % 6) * (im.width + 16)
        yy = y + (k // 6) * (im.height + 26)
        d.text((x, yy), "%d %s" % (k, F.EYE_FRAMES[k]), fill=DIM, font=small)
        sheet.alpha_composite(im, (x, yy + 14))
    y += 2 * (F.EH * ez + 26) + 8
    d.text((16, y), "mouth and cheek frames", fill=INK, font=font)
    y += 22
    x = 16
    for k in range(8):
        u, v, w, h = F.frame_rect(rig, "mouth", k)
        im = Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * ez, h * ez), Image.NEAREST)
        d.text((x, y), F.MOUTH_FRAMES[k], fill=DIM, font=small)
        sheet.alpha_composite(im, (x, y + 14))
        x += im.width + 12
    y += 4 * ez + 34
    x = 16
    for k in range(4):
        u, v, w, h = F.frame_rect(rig, "cheeks", k)
        bgc = Image.new("RGBA", (w * ez, h * ez), tuple(b.ramps["skin"][2]) + (255,))
        bgc.alpha_composite(Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * ez, h * ez), Image.NEAREST))
        d.text((x, y), F.CHEEK_FRAMES[k], fill=DIM, font=small)
        sheet.alpha_composite(bgc, (x, y + 14))
        x += bgc.width + 12
    _save(sheet, path)


def texture_sheet(hero_id, path, zoom=3):
    files, b = build.build_files(hero_id)
    tex = Image.fromarray(b.image, "RGBA")
    w, h = tex.size
    checker = np.zeros((h * zoom, w * zoom, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:h * zoom, 0:w * zoom]
    light = ((yy // 8 + xx // 8) % 2) == 0
    checker[..., :3] = np.where(light[..., None], 92, 72)
    checker[..., 3] = 255
    sheet = Image.fromarray(checker, "RGBA")
    sheet.alpha_composite(tex.resize((w * zoom, h * zoom), Image.NEAREST))
    d = ImageDraw.Draw(sheet)
    tpl = b.model.rig["uv_template"]
    for key, (u, v, rw, rh) in sorted(tpl.items()):
        d.rectangle([u * zoom, v * zoom, (u + rw) * zoom - 1, (v + rh) * zoom - 1], outline=(255, 210, 80, 255))
    _save(sheet, path)


def style_order():
    """The style fixtures in their lineup order (style_preview.order), with their faction tints."""
    rows = []
    tints = {}
    for hid in build.style_ids():
        sp = build.load_hero(hid).get("style_preview") or {}
        rows.append((sp.get("order", 99), hid))
        t = sp.get("faction_tint")
        if t:
            tints[hid] = tuple(int(t[i:i + 2], 16) for i in (1, 3, 5))
    return [hid for _, hid in sorted(rows)], tints


def write_all(out_dir=PREVIEW_DIR):
    """Every sheet of the style set. Returns {name: path}."""
    ids, tints = style_order()
    paths = {"bases": os.path.join(out_dir, "bases_v3.png"), "lineup": os.path.join(out_dir, "lineup.png"),
             "lineup_forms": os.path.join(out_dir, "lineup_three_forms.png"),
             "faces": os.path.join(out_dir, "face_closeups.png"),
             "face_lineup": os.path.join(out_dir, "face_lineup.png")}
    bases_sheet(paths["bases"])
    lineup(ids, paths["lineup"], tints=tints)
    # one character per form, larger, like the owner's reference shot (chibi, adult, teen)
    by_form = {}
    for hid in ids:
        by_form.setdefault(build.load_hero(hid)["art"].get("form", "adult"), hid)
    three = [by_form[f] for f in ("chibi", "adult", "teen") if f in by_form]
    lineup(three, paths["lineup_forms"], scale=15, yaw=-20.0, tints=tints)
    face_strip(ids, paths["faces"])
    face_lineup(ids, paths["face_lineup"])
    for hid in ids:
        paths[hid] = os.path.join(out_dir, "%s.png" % hid)
        hero_sheet(hid, paths[hid], tint=tints.get(hid, SASH_TINT))
        paths[hid + "_frames"] = os.path.join(out_dir, "%s_frames.png" % hid)
        faces_sheet(hid, paths[hid + "_frames"])
    for hid in ("test_rig", "_fallback"):
        paths[hid] = os.path.join(out_dir, "%s.png" % hid.lstrip("_"))
        hero_sheet(hid, paths[hid])
    paths["test_rig_texture"] = os.path.join(out_dir, "test_rig_texture.png")
    texture_sheet("test_rig", paths["test_rig_texture"])
    return paths
