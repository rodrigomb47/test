"""Preview sheets drawn with the G2 renderer (HEROES_V2.md 13.8): a turnaround per hero, lineups on grass, face
strips with the moods, and the contact sheet (24 heroes per sheet, 6 x 4 cells). Every hero is drawn in the stance
the game shows when it stands (the first frame of move.idle, or combat.idle with the weapon drawn), lit by vanilla's
entity light with the hero facing south and the camera going round it, at one model-pixel scale per sheet (one world
scale, since rig v3 draws every form at one render scale).
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from render import raster
from render.scene import PITCH, SASH_TINT, to_image

BG = (156, 168, 186, 255)          # a light blue-grey, so dark clothes and light hair both read
INK = (22, 26, 34, 255)
DIM = (48, 54, 66, 255)
TURN_VIEWS = (("front", 0.0), ("front left", -45.0), ("left", -90.0), ("back left", -135.0), ("back", 180.0),
              ("back right", 135.0), ("right", 90.0), ("front right", 45.0))
FACE_STRIP = (("calm", "calm", None), ("blink (half)", "calm", 1), ("blink (closed)", "calm", 2),
              ("happy", "happy", None), ("angry", "angry", None))
NIGHT = 0.25                        # light map brightness of a moonlit night (the glow layer ignores it)
SHEET_FACES = (("open", "calm", None), ("blink", "calm", 2), ("happy", "happy", None), ("angry", "angry", None),
               ("focused", "focused", None), ("pain", "hurt", None), ("talk", "calm", "talk"))


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:          # Pillow < 10.1
        return ImageFont.load_default()


def save(img, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def backdrop(w, h, horizon):
    """A flat sky gradient over a blocky grass field (tiles growing toward the viewer, a little noise),
    deterministic."""
    img = np.zeros((h, w, 4), dtype=np.uint8)
    t = np.linspace(0.0, 1.0, max(1, horizon))[:, None]
    top, low = np.array([126, 168, 236]), np.array([186, 212, 250])
    img[:horizon, :, :3] = (top + (low - top) * t).astype(np.uint8)[:, None, :] if horizon else 0
    yy, xx = np.mgrid[horizon:h, 0:w]
    depth = (yy - horizon) / max(1.0, h - horizon)
    tile = (6 + 22 * depth).astype(int)
    tx, ty = xx // np.maximum(tile, 1), (yy - horizon) // np.maximum(tile, 1)
    noise = ((tx * 73856093) ^ (ty * 19349663)) % 7
    base = np.array([96, 150, 64])
    shade = (noise - 3) * 5 + (depth * 16).astype(int)
    for c in range(3):
        img[horizon:, :, c] = np.clip(base[c] + shade * (1.0 if c != 2 else 0.6), 0, 255).astype(np.uint8)
    img[..., 3] = 255
    return Image.fromarray(img, "RGBA")


def shadow(size, alpha=90):
    w, h = size
    im = Image.new("RGBA", (w + 16, h + 16), (0, 0, 0, 0))
    ImageDraw.Draw(im).ellipse([8, 8, 8 + w, 8 + h], fill=(20, 40, 16, alpha))
    return im.filter(ImageFilter.GaussianBlur(3))


def ground_row(view, bounds, pad=2):
    """Screen row of the ground point (0, 0, 0) in an image with these bounds."""
    v = view.to_view(np.zeros((1, 3)))[0]
    return int(round((bounds[3] - v[1]) * view.scale + pad))


def ground_col(view, bounds, pad=2):
    v = view.to_view(np.zeros((1, 3)))[0]
    return int(round((bounds[1] - v[0]) * view.scale + pad))


def height_blocks(a):
    v = a.visual
    return v["height_px"] * v["render_scale"] * v.get("scale", 1.0) / 16.0


def cubes_beyond_fine(a):
    """Cubes still drawn beyond the fine distance (not under a fine_ bone)."""
    hs = raster.hidden_set(a.geo, a.bones("fine"))
    return sum(1 for c in a.geo.cubes if c.bone not in hs)


def stats_line(a):
    v = a.visual
    return "%s (%s, %s build): %d bones, %d cubes (%d beyond the fine distance), %dx%d texture, %g px tall " \
           "(%.2f blocks)" % (a.id, v["base"], v["build"], len(a.geo.order), len(a.geo.cubes), cubes_beyond_fine(a),
                              v["texture"]["width"], v["texture"]["height"], v["height_px"], height_blocks(a))


# ------------------------------------------------------------------ head renders

def head_bounds(a, view, pose, phase=1):
    """Bounds that frame the head and what hangs from it, shoulders up."""
    hy = a.visual["head_y"]
    full = a.bounds([view], (pose,), phase)
    return (max(full[0], -12.0), min(full[1], 12.0), hy - 4.0, min(full[3], hy + 20.0))


def head_image(a, view, pose, face, phase=1, tint=SASH_TINT, bounds=None):
    b = bounds or head_bounds(a, view, pose, phase)
    return to_image(a.render(view, pose, face=face, phase=phase, tint=tint, bounds=b, pad=4))


def face_frames(a, mood, special):
    """(eyes, mouth, cheeks) of a strip cell: a mood, with a blink frame (1 or 2) or the talk mouth."""
    e, m, c = a.mood(mood)
    if special == "talk":
        return (e, 2, c)
    if isinstance(special, int):
        return (special, m, c)
    return (e, m, c)


# ------------------------------------------------------------------ turnaround

def turnaround(a, path, scale=7, pitch=PITCH, tint=SASH_TINT, title=None):
    """Eight views round the hero (45 degrees apart) and one from above, feet on one line, then the weapon drawn and
    the far level of detail when they differ."""
    pose = a.stance()
    views = [(label, raster.View(yaw, pitch, scale)) for label, yaw in TURN_VIEWS]
    yb = a.bounds([v for _, v in views], (pose,))[2:]
    cells = []
    for label, v in views:
        xb = a.bounds([v], (pose,))
        cells.append((label, to_image(a.render(v, pose, tint=tint, bounds=(xb[0], xb[1], yb[0], yb[1])))))
    extra = []
    top = raster.View(0.0, 89.0, scale)
    extra.append(("from above (front down)", to_image(a.render(top, pose, tint=tint))))
    if a.bones("weapon_drawn"):
        d = raster.View(-34.0, pitch, scale)
        dpose = a.stance(drawn=True)
        xb = a.bounds([d], (dpose,), drawn=True)
        extra.append(("weapon drawn", to_image(a.render(d, dpose, drawn=True, tint=tint,
                                                        bounds=(xb[0], xb[1], min(yb[0], xb[2]), max(yb[1], xb[3]))))))
    far = raster.View(-34.0, pitch, scale)
    xb = a.bounds([far], (pose,), far=True)
    extra.append(("far: fine_ hidden", to_image(a.render(far, pose, far=True, tint=tint,
                                                         bounds=(xb[0], xb[1], yb[0], yb[1])))))
    phases = a.visual.get("phases") or []
    if phases:
        ph = max(p["phase"] for p in phases)
        face = (10, 0, 0) if any(p.get("eyes_glow") for p in phases) else (0, 0, 0)
        xb = a.bounds([far], (pose,), phase=ph)
        extra.append(("phase %d at night (glow layer)" % ph, to_image(a.render(
            far, pose, face=face, phase=ph, tint=tint, brightness=NIGHT, bounds=(xb[0], xb[1], min(yb[0], xb[2]),
                                                                                  max(yb[1], xb[3]))))))
    f18, f13 = font(18), font(13)
    gap = 14
    row1_w = sum(im.width for _, im in cells) + gap * (len(cells) - 1)
    row2_w = sum(im.width for _, im in extra) + gap * (len(extra) - 1)
    row1_h = max(im.height for _, im in cells)
    row2_h = max(im.height for _, im in extra)
    W = max(row1_w, row2_w, 900) + 32
    H = 70 + row1_h + 30 + row2_h + 40
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 10), title or "%s: turnaround" % a.name, fill=INK, font=f18)
    d.text((16, 34), stats_line(a) + "; idle stance; vanilla entity light, the hero faces south and the camera goes "
                                     "round it", fill=DIM, font=f13)
    x, y = 16, 62
    for label, im in cells:
        d.text((x + im.width // 2, y), label, fill=DIM, font=f13, anchor="ma")
        sheet.alpha_composite(im, (x, y + 18 + row1_h - im.height))
        x += im.width + gap
    x, y = 16, 62 + 18 + row1_h + 16
    for label, im in extra:
        d.text((x + im.width // 2, y), label, fill=DIM, font=f13, anchor="ma")
        sheet.alpha_composite(im, (x, y + 18 + row2_h - im.height))
        x += im.width + gap
    save(sheet, path)
    return sheet


# ------------------------------------------------------------------ lineup

def lineup(heroes, path, scale=10, yaw=-24.0, pitch=12.0, tints=None, gap=26, labels=True, title=None):
    """Heroes side by side on grass under a sky, three-quarter view from a little above, feet on one line and one
    scale (so the forms' heights compare), with soft ground shadows."""
    rows = []
    for a in heroes:
        v = raster.View(yaw, pitch, scale)
        pose = a.stance()
        rows.append((a, v, pose, a.bounds([v], (pose,))))
    ytop = max(r[3][3] for r in rows)
    ybot = min(r[3][2] for r in rows)
    pad = 2
    imgs = []
    for a, v, pose, bx in rows:
        tint = (tints or {}).get(a.id, SASH_TINT)
        b = (bx[0], bx[1], ybot, ytop)
        imgs.append((a, to_image(a.render(v, pose, tint=tint, bounds=b, pad=pad)), ground_row(v, b, pad),
                     ground_col(v, b, pad)))
    f16 = font(16)
    measure = ImageDraw.Draw(Image.new("RGBA", (1, 1)))

    def label_of(a):
        form = a.visual["form"]
        return "%s (%s%.2f blocks)" % (a.name, "" if form in a.name else form + ", ", height_blocks(a))

    # each slot is as wide as its image or its label, whichever is wider
    slots = [max(im.width, int(measure.textlength(label_of(a), font=f16)) + 12 if labels else 0)
             for a, im, _, _ in imgs]
    body_h = max(i[1].height for i in imgs)
    W = sum(slots) + gap * (len(imgs) + 1) + 40
    top = 40 if not title else 64
    lowest = max(i[2] + 40 for i in imgs)
    H = max(body_h + 150, top + lowest + 44)
    sheet = backdrop(W, H, int(H * 0.42))
    d = ImageDraw.Draw(sheet)
    if title:
        d.text((16, 12), title, fill=(250, 250, 250, 255), font=font(18), stroke_width=2,
               stroke_fill=(40, 60, 90, 255))
    x = gap + 20
    for (a, im, feet, foot_x), slot in zip(imgs, slots):
        ix = x + (slot - im.width) // 2
        hips = 6.0 if a.visual["form"] == "chibi" else 7.0
        sw = int(hips * scale * 2.4)
        sh = max(10, sw // 3)
        sheet.alpha_composite(shadow((sw, sh)), (ix + foot_x - sw // 2 - 8, top + feet - sh // 2 - 8))
        sheet.alpha_composite(im, (ix, top))
        if labels:
            d.text((ix + foot_x, top + feet + sh + 10), label_of(a), fill=(250, 250, 250, 255), font=f16,
                   anchor="ma", stroke_width=2, stroke_fill=(30, 50, 26, 255))
        x += slot + gap
    save(sheet, path)
    return sheet


# ------------------------------------------------------------------ face strip

def face_strip(heroes, path, scale=13, cells=FACE_STRIP, three_quarter=True, title=None):
    """One row per hero: its head from the front in each mood of `cells` (a blink midway is the half-closed frame the
    blink passes through), then a three-quarter view."""
    f18, f13 = font(18), font(13)
    rows = []
    for a in heroes:
        pose = a.stance()
        front = raster.View(0.0, 4.0, scale)
        hb = head_bounds(a, front, pose)
        imgs = [(label, head_image(a, front, pose, face_frames(a, mood, special), bounds=hb))
                for label, mood, special in cells]
        if three_quarter:
            tq = raster.View(-32.0, 8.0, scale)
            imgs.append(("3/4", head_image(a, tq, pose, a.mood("calm"))))
        rows.append((a, imgs))
    cw = max(im.width for _, imgs in rows for _, im in imgs)
    heights = [max(im.height for _, im in imgs) for _, imgs in rows]
    ncol = max(len(imgs) for _, imgs in rows)
    sheet = Image.new("RGBA", (ncol * (cw + 10) + 30, sum(heights) + 56 * len(rows) + 50), BG)
    d = ImageDraw.Draw(sheet)
    d.text((16, 12), title or "Face strip: the moods from the front (frames from visual.json, as HeroFace picks "
                              "them), a blink midway and closed, and a 3/4 view", fill=INK, font=f18)
    y = 46
    for (a, imgs), ch in zip(rows, heights):
        d.text((16, y), "%s (%s)" % (a.name, a.visual["base"]), fill=INK, font=f13)
        x = 16
        for label, im in imgs:
            sheet.alpha_composite(im, (x + (cw - im.width) // 2, y + 20 + (ch - im.height)))
            d.text((x + cw // 2, y + 22 + ch), label, fill=DIM, font=f13, anchor="ma")
            x += cw + 10
        y += ch + 56
    save(sheet, path)
    return sheet


# ------------------------------------------------------------------ contact sheet

CELL_W, CELL_H = 440, 384


def _palette_swatch(a, w, h=14):
    pal = ((a.hero or {}).get("art") or {}).get("palette") or {}
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if not pal:
        return im
    d = ImageDraw.Draw(im)
    keys = [k for k in ("skin", "hair", "eyes", "primary", "secondary", "accent", "trim", "metal", "leather", "glow")
            if k in pal]
    sw = w // max(1, len(keys))
    for i, k in enumerate(keys):
        c = pal[k]
        d.rectangle([i * sw, 0, (i + 1) * sw - 2, h - 1], fill=tuple(int(c[j:j + 2], 16) for j in (1, 3, 5)) + (255,))
    return im


def _cell(a, tint):
    """One contact-sheet cell (CELL_W x CELL_H)."""
    cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(cell)
    f15, f12 = font(15), font(12)
    pose = a.stance()
    main_h = 228
    # 3/4 front view, scaled so the tallest form fills main_h (one scale for every cell: 60 px tall at 3.7)
    scale = 3.7
    v = raster.View(-34.0, PITCH, scale)
    b = a.bounds([v], (pose,))
    b = (b[0], b[1], min(b[2], -0.5), max(b[3], b[2] + main_h / scale - 1))
    main = to_image(a.render(v, pose, tint=tint, bounds=(b[0], b[1], b[3] - (main_h - 4) / scale, b[3])))
    main = main.crop((0, max(0, main.height - main_h), main.width, main.height))
    cell.alpha_composite(main, (6, 30 + main_h - main.height))
    x = 12 + main.width
    back_v = raster.View(160.0, PITCH, scale / 2)
    back = to_image(a.render(back_v, pose, tint=tint))
    cell.alpha_composite(back, (x, 30 + main_h - back.height))
    d.text((x + back.width // 2, 30 + main_h + 2), "back", fill=DIM, font=f12, anchor="ma")
    x += back.width + 8
    phases = a.visual.get("phases") or []
    if phases:
        ph = max(p["phase"] for p in phases)
        glow_eyes = any(p.get("eyes_glow") for p in phases)
        pv = raster.View(-34.0, PITCH, scale / 2)
        face = (10, 0, 0) if glow_eyes else (0, 0, 0)
        pim = to_image(a.render(pv, pose, face=face, phase=ph, tint=tint))
        cell.alpha_composite(pim, (x, 30 + main_h - pim.height))
        d.text((x + pim.width // 2, 30 + main_h + 2), "phase %d" % ph, fill=DIM, font=f12, anchor="ma")
    # the face strip: the head only, larger
    fv = raster.View(0.0, 3.0, 3.4)
    hb = (-7.5, 7.5, a.visual["head_y"] - 1.5, a.visual["head_y"] + 11.0)
    fx = 6
    fy = 30 + main_h + 20
    for label, mood, special in SHEET_FACES:
        im = head_image(a, fv, pose, face_frames(a, mood, special), tint=tint, bounds=hb)
        cell.alpha_composite(im, (fx, fy))
        d.text((fx + im.width // 2, fy + im.height + 1), label, fill=DIM, font=f12, anchor="ma")
        fx += im.width + 2
    # name and facts
    hero = a.hero or {}
    d.text((6, 4), a.name, fill=INK, font=f15)
    weapon = ((hero.get("art") or {}).get("weapon") or {}).get("prop", "-")
    facts = "%s / %s / %s / %s" % (hero.get("archetype", "-"), hero.get("role", "-"), hero.get("element", "-"),
                                   weapon.split("/")[-1])
    d.text((CELL_W - 6, 6), facts, fill=DIM, font=f12, anchor="ra")
    sw = _palette_swatch(a, CELL_W - 12)
    cell.alpha_composite(sw, (6, CELL_H - sw.height - 4))
    return cell


def contact_sheet(heroes, path, tints=None, cols=6, rows=4, title=None):
    """24 heroes per sheet (6 x 4 cells): each cell has the 3/4 front view, a small back view, the phase look, the
    face strip (open, blink, happy, angry, focused, pain, talk), the palette, and the name, archetype, role, element
    and weapon. More heroes than fit write path_2.png, path_3.png, ... Returns the paths."""
    per = cols * rows
    out = []
    for n in range(0, max(1, len(heroes)), per):
        group = heroes[n:n + per]
        nrows = (len(group) + cols - 1) // cols
        ncols = min(cols, len(group))
        sheet = Image.new("RGBA", (ncols * (CELL_W + 10) + 22, nrows * (CELL_H + 10) + 52), BG)
        d = ImageDraw.Draw(sheet)
        d.text((14, 12), title or "Contact sheet: 3/4 front, back, phase look, face strip, palette", fill=INK,
               font=font(18))
        for i, a in enumerate(group):
            cx = 12 + (i % cols) * (CELL_W + 10)
            cy = 42 + (i // cols) * (CELL_H + 10)
            d.rectangle([cx, cy, cx + CELL_W - 1, cy + CELL_H - 1], fill=(170, 181, 198, 255))
            sheet.alpha_composite(_cell(a, (tints or {}).get(a.id, SASH_TINT)), (cx, cy))
        p = path if n == 0 else path.replace(".png", "_%d.png" % (n // per + 1))
        save(sheet, p)
        out.append(p)
    return out
