"""Preview sheets for the owner (G1): the six base bodies and the test rig in orthographic views, the face frames and
moods, and the texture layout. They are rendered from the built files (geometry + PNG), not from the in-memory model.
G2 replaces the renderer with the full one (animations, vanilla lighting, contact sheets, GIFs).
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import build
import face as F
from geo import GeoModel
from render import ortho
from rig import BASE_IDS, load_rig
from util import ROOT

PREVIEW_DIR = os.path.join(ROOT, "docs", "heroes-v2", "notes", "previews")
BG = (58, 62, 70, 255)
INK = (232, 232, 236, 255)
DIM = (168, 172, 182, 255)
SASH_TINT = (74, 118, 196)
VIEWS = (("front", 0.0), ("3/4", -34.0), ("side", -90.0), ("back", 180.0))

# A mannequin for the bare bases: neutral base layer, painted short hair, default face of the family.
MANNEQUIN_PALETTE = {"skin": "#EDC9AE", "hair": "#4A3F3A", "eyes": "#5B7BC8", "secondary": "#8E96AA",
                     "accent": "#5A6072", "leather": "#4B3D35"}


def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:          # Pillow < 10.1
        return ImageFont.load_default()


def mannequin(base_id):
    family = "female" if base_id.startswith("f_") else "male"
    return {"format": 2, "id": "base_" + base_id, "name": base_id, "gender": family,
            "art": {"base": family, "build": base_id.split("_", 1)[1], "palette": MANNEQUIN_PALETTE,
                    "parts": [{"part": "hair/short_crop", "color": "hair"}]},
            "animations": {"blink": "normal"}}


def built_geo(files, hero_id):
    return GeoModel(json.loads(files[build.out_paths(hero_id)["geo"]]))


def view_hidden(geo, phase=1, drawn=False):
    hidden = [n for n in geo.order if n.startswith("p2_") and phase < 2 or n.startswith("p3_") and phase < 3]
    if drawn:
        hidden.append("weapon_back")
    else:
        hidden += ["weapon_r", "weapon_l"]
    return hidden


def tint_of(geo):
    return {n: SASH_TINT for n in geo.order if n == "sash" or n.startswith("fine_sash")}


def render_views(geo, tex, views, scale, phase=1, drawn=False, eyes=0, mouth=0, cheeks=0):
    """Images of several views with a shared vertical scale (feet on one line)."""
    hidden = view_hidden(geo, phase, drawn)
    shift = {}
    rig = load_rig()
    for plane, k in (("eyes", eyes), ("mouth", mouth), ("cheeks", cheeks)):
        u, v, _, _ = F.frame_rect(rig, plane, k)
        u0, v0, _, _ = F.frame_rect(rig, plane, 0)
        shift[plane] = (u - u0, v - v0)
    out = []
    for label, yaw in views:
        xb = ortho.model_bounds(geo, [yaw], hidden)
        yb = ortho.model_bounds(geo, [v for _, v in views], hidden)
        img = ortho.render(geo, tex, yaw=yaw, scale=scale, hidden=hidden, uv_shift=shift, tint=tint_of(geo),
                           bounds=(xb[0], xb[1], yb[2], yb[3]))
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


def bases_sheet(path, scale=6):
    views = (("front", 0.0), ("side", -90.0), ("back", 180.0))
    rows = []
    for base_id in BASE_IDS:
        hero = mannequin(base_id)
        files, b = build.build_files(hero["id"], hero)
        geo = built_geo(files, hero["id"])
        rows.append((base_id, render_views(geo, b.image, views, scale), b.model))
    font, small = _font(16), _font(12)
    cell_w = max(_row(r[1])[0] for r in rows) + 24
    cell_h = max(_row(r[1])[1] for r in rows) + 64
    cols = 3
    sheet = Image.new("RGBA", (cell_w * cols + 24, cell_h * 2 + 70), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), "Hero rig v1: the six base bodies (front, side, back; mannequin palette, painted short hair)",
           fill=INK, font=font)
    for n, (base_id, images, model) in enumerate(rows):
        x0 = 12 + (n % cols) * cell_w
        y0 = 44 + (n // cols) * cell_h
        dims = model.base["dims"]
        d.text((x0 + 8, y0), base_id, fill=INK, font=font)
        d.text((x0 + 8, y0 + 20), "chest %gx%g  waist %gx%g  hips %gx%g  arm %g  leg %g" % (
            dims["chest_w"], dims["chest_d"], dims["waist_w"], dims["waist_d"], dims["hips_w"], dims["hips_d"],
            dims["arm_w"], dims["leg_w"]), fill=DIM, font=small)
        _paste_views(sheet, d, x0 + 8, y0 + 38, images, small)
    _save(sheet, path)


def hero_sheet(hero_id, path, scale=8):
    files, b = build.build_files(hero_id)
    geo = built_geo(files, hero_id)
    font, small = _font(16), _font(12)
    groups = [render_views(geo, b.image, VIEWS, scale), render_views(geo, b.image, (("blink", 0.0),), scale, eyes=2)]
    art = b.hero["art"]
    if art.get("phases"):
        tex2 = b.p2 if b.p2 is not None else b.image
        glow_eyes = any(p.get("eyes_glow") for p in art["phases"])
        groups.append(render_views(geo, tex2, (("phase 2" + (", weapon drawn" if art.get("weapon") else ""),
                                                -34.0),), scale, phase=2, drawn=bool(art.get("weapon")),
                                   eyes=10 if glow_eyes else 0))
    elif art.get("weapon"):
        groups.append(render_views(geo, b.image, (("weapon drawn", -34.0),), scale, drawn=True))
    w = 16 + sum(_row(g)[0] + 12 + 24 for g in groups) + 48      # _paste_views' trailing gap, group gap, label room
    h = max(_row(g)[1] for g in groups) + 64
    sheet = Image.new("RGBA", (max(w, 720), h), BG)
    d = ImageDraw.Draw(sheet)
    m = b.model
    d.text((16, 12), "%s: %s, %d bones, %d cubes, %dx%d texture (sash tinted as a blue faction would)" % (
        hero_id, m.base_id, len(m.bones), len(m.cubes), m.tex_w, m.tex_h), fill=INK, font=font)
    x = 16
    for g in groups:
        x = _paste_views(sheet, d, x, 44, g, small) + 24
    _save(sheet, path)


def faces_sheet(hero_id, path, zoom=8):
    files, b = build.build_files(hero_id)
    tex = b.image
    rig = b.model.rig
    table = F.mood_table(b.spec)
    font, small = _font(16), _font(12)
    cell = 16 * zoom
    sheet = Image.new("RGBA", (6 * (cell + 16) + 24, 2 * (cell + 34) + 3 * (8 * zoom // 2 + 30) + 210), BG)
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
    ez = zoom // 2 * 2
    for k in range(12):
        u, v, w, h = F.frame_rect(rig, "eyes", k)
        im = Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * ez // 2, h * ez // 2), Image.NEAREST)
        x = 16 + (k % 6) * (im.width + 16)
        yy = y + (k // 6) * (im.height + 26)
        d.text((x, yy), "%d %s" % (k, F.EYE_FRAMES[k]), fill=DIM, font=small)
        sheet.alpha_composite(im, (x, yy + 14))
    y += 2 * (8 * ez // 2 + 26) + 8
    d.text((16, y), "mouth and cheek frames", fill=INK, font=font)
    y += 22
    x = 16
    for k in range(8):
        u, v, w, h = F.frame_rect(rig, "mouth", k)
        im = Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * zoom, h * zoom), Image.NEAREST)
        d.text((x, y), F.MOUTH_FRAMES[k], fill=DIM, font=small)
        sheet.alpha_composite(im, (x, y + 14))
        x += im.width + 12
    y += 4 * zoom + 34
    x = 16
    for k in range(4):
        u, v, w, h = F.frame_rect(rig, "cheeks", k)
        bgc = Image.new("RGBA", (w * zoom, h * zoom), (236, 206, 186, 255))
        bgc.alpha_composite(Image.fromarray(tex[v:v + h, u:u + w], "RGBA").resize((w * zoom, h * zoom),
                                                                                    Image.NEAREST))
        d.text((x, y), F.CHEEK_FRAMES[k], fill=DIM, font=small)
        sheet.alpha_composite(bgc, (x, y + 14))
        x += bgc.width + 12
    _save(sheet, path)


def texture_sheet(hero_id, path, zoom=4):
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
        if key.startswith("_"):
            continue
        d.rectangle([u * zoom, v * zoom, (u + rw) * zoom - 1, (v + rh) * zoom - 1], outline=(255, 210, 80, 255))
    _save(sheet, path)


def _save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def write_all(out_dir=PREVIEW_DIR):
    paths = {
        "bases": os.path.join(out_dir, "g1_bases.png"),
        "test_rig": os.path.join(out_dir, "g1_test_rig.png"),
        "faces": os.path.join(out_dir, "g1_test_rig_faces.png"),
        "texture": os.path.join(out_dir, "g1_test_rig_texture.png"),
        "fallback": os.path.join(out_dir, "g1_fallback.png"),
    }
    bases_sheet(paths["bases"])
    hero_sheet("test_rig", paths["test_rig"])
    faces_sheet("test_rig", paths["faces"])
    texture_sheet("test_rig", paths["texture"])
    hero_sheet("_fallback", paths["fallback"])
    return paths
