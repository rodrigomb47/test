"""The G2 preview set for the owner (docs/heroes-v2/previews/render/), drawn from the built files by the G2 renderer:

  forms_lineup.png         the six bases (three forms x two families) side by side on grass
  lineup.png               the six style characters and the test rig on grass
  turnaround_<id>.png      each test character: eight views round it, from above, the weapon drawn, far LOD, and a
                           phase look at night (the glow layer) where it has one
  faces.png                the face strip: calm, a blink midway (half) and closed, happy, angry, and a 3/4 view
  contact_sheet.png        every test character and the fallback in contact-sheet cells
  idle_blink.gif           move.idle's whole 24 s loop (10 fps) on the three forms, blinking as HeroFace would
  idle_blink_faces.gif     the heads at 20 fps (one frame a tick: every blink frame shows), 6 s
  walk.gif, run.gif        one cycle each on the three forms, 20 fps
  swing.gif                the katana attack.1 over combat.idle on four heroes, the hit written on its frame
  face_plane_light.png     evidence: the face planes lit apart from the head when the face points down or aside
  coplanar_faces.png       evidence: coplanar faces of different colour (z-fighting in game) in magenta, by bone pair
"""
import os

from render import gif, sheet
from render.scene import HeroAssets
from util import ROOT

OUT_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "render")
FORMS_SET = ("style_tansy", "style_wren", "test_rig")          # chibi, teen, adult (ears, tails, long hair)
SWING_SET = ("style_tansy", "style_wren", "test_rig", "style_garrick")


def test_characters():
    """The style fixtures in their lineup order, then the test rig, and the faction tints of the fixtures."""
    import previews
    ids, tints = previews.style_order()
    return list(ids) + ["test_rig"], tints


def face_plane_light(path, hero_id="test_rig", scale=14):
    """Evidence for the face-plane lighting quirk (GeckoLib's fixInvertedFlatCube on the zero-thickness eyes, mouth
    and cheeks planes): the head at rest, pitched down, pitched up and turned both ways, with the light of the head
    front and of the eyes plane under each."""
    from PIL import Image, ImageDraw

    from render import raster
    from render.scene import to_image
    a = HeroAssets.committed(hero_id)
    base = a.stance()
    cases = (("rest", (0, 0, 0)), ("head down 20", (20, 0, 0)), ("head up 20", (-20, 0, 0)),
             ("turned left 30", (0, -30, 0)), ("turned right 30", (0, 30, 0)))
    cells = []
    for label, rot in cases:
        pose = dict(base)
        pose["head"] = {"rotation": rot}
        view = raster.View(-20.0, 6.0, scale)
        img = to_image(a.render(view, pose, bounds=sheet.head_bounds(a, view, pose)))
        qs = raster.quads(a.geo, a.hidden(), None, pose)
        front = next(q for q in qs if q.bone == "head" and q.face == "north")
        eyes = next(q for q in qs if q.bone == "eyes")
        cells.append((label, img, raster.quad_light(front), raster.quad_light(eyes)))
    f16, f13 = sheet.font(16), sheet.font(13)
    cw = max(c[1].width for c in cells) + 16
    ch = max(c[1].height for c in cells)
    out = Image.new("RGBA", (cw * len(cells) + 24, ch + 110), sheet.BG)
    d = ImageDraw.Draw(out)
    d.text((12, 8), "Face planes and GeckoLib's flat-cube normal fix: the eyes, mouth and cheeks planes (zero "
                    "thickness) get |x| and |y| of their world normal, so they stop matching the head front's light "
                    "when the face points down or to one side", fill=sheet.INK, font=f13)
    for i, (label, img, lf, le) in enumerate(cells):
        x = 12 + i * cw
        out.alpha_composite(img, (x, 34 + ch - img.height))
        d.text((x + img.width // 2, 40 + ch), label, fill=sheet.INK, font=f16, anchor="ma")
        d.text((x + img.width // 2, 62 + ch), "head front %.2f, eyes plane %.2f" % (lf, le),
               fill=sheet.DIM if abs(lf - le) < 0.01 else (170, 30, 30, 255), font=f13, anchor="ma")
    sheet.save(out, path)
    return path


TIE_COLOUR = (255, 0, 255, 255)


def coplanar_faces(heroes, path, scale=6):
    """Evidence for coplanar faces (raster.coplanar_ties): each hero from the front, three-quarter and back in its
    stance, the pixels that would z-fight in game painted magenta, and the bone pairs with the most such pixels."""
    from PIL import Image, ImageDraw

    from render import raster
    from render.scene import PITCH
    f16, f13, f12 = sheet.font(16), sheet.font(13), sheet.font(12)
    rows = []
    for a in heroes:
        pose = a.stance()
        views = [raster.View(yaw, PITCH, scale) for yaw in (0.0, -40.0, 180.0)]
        yb = a.bounds(views, (pose,))[2:]
        cells, pairs = [], {}
        for v in views:
            xb = a.bounds([v], (pose,))
            mask, pp, fr = a.ties(v, pose, bounds=(xb[0], xb[1], yb[0], yb[1]))
            rgba = fr.rgba.copy()
            rgba[mask] = TIE_COLOUR
            cells.append(Image.fromarray(rgba, "RGBA"))
            for k, n in pp.items():
                pairs[k] = pairs.get(k, 0) + n
        top = sorted(pairs.items(), key=lambda kv: (-kv[1], kv[0]))[:4]
        text = ", ".join("%s/%s %d" % (k[0], k[1], n) if k[0] != k[1] else "%s (own cubes) %d" % (k[0], n)
                         for k, n in top) or "none"
        rows.append((a, cells, sum(pairs.values()), text))
    gap = 10
    col_w = max(sum(c.width for c in cells) + gap * (len(cells) - 1) for _, cells, _, _ in rows)
    per_row = 4
    cell_h = max(c.height for _, cells, _, _ in rows for c in cells) + 58
    n_rows = (len(rows) + per_row - 1) // per_row
    out = Image.new("RGBA", (16 + per_row * (col_w + 24), 44 + n_rows * cell_h), sheet.BG)
    d = ImageDraw.Draw(out)
    d.text((12, 8), "Coplanar faces of different colour in magenta: the preview draws the earlier face, the game "
                    "z-fights (the pixel flickers between both surfaces, most at a distance). Front, 3/4 and back; "
                    "under each hero the bone pairs with the most such pixels (at %dx)" % scale, fill=sheet.INK, font=f13)
    for i, (a, cells, total, text) in enumerate(rows):
        x0 = 12 + (i % per_row) * (col_w + 24)
        y0 = 36 + (i // per_row) * cell_h
        x = x0
        h = max(c.height for c in cells)
        for c in cells:
            out.alpha_composite(c, (x, y0 + h - c.height))
            x += c.width + gap
        d.text((x0, y0 + h + 6), "%s: %d px" % (a.name, total), fill=sheet.INK, font=f16)
        d.text((x0, y0 + h + 28), text, fill=sheet.DIM, font=f12)
    sheet.save(out, path)
    return path


def check(out=OUT_DIR, gifs=True, only=None):
    """Rebuild the set in a temporary folder and list the committed files that differ from it (a stale preview is a
    wrong preview). Returns the problem lines."""
    import tempfile
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(tmp, gifs=gifs, only=only)
        for name in sorted(fresh):
            have = os.path.join(out, name)
            with open(fresh[name], "rb") as f:
                new = f.read()
            if not os.path.exists(have):
                problems.append("%s: missing" % name)
                continue
            with open(have, "rb") as f:
                if f.read() != new:
                    problems.append("%s: differs from a fresh render (run render-previews)" % name)
    return problems


def write_all(out=OUT_DIR, gifs=True, only=None):
    """Write the set; `only` limits it to some of: lineups, turnarounds, faces, contact, gifs. Returns {file name:
    path}."""
    import previews
    from anim.preview import sword_variant
    want = set(only or ("lineups", "turnarounds", "faces", "contact", "gifs"))
    ids, tints = test_characters()
    cast = {hid: HeroAssets.build(hid) for hid in ids}
    paths = {}

    def p(name):
        paths[name] = os.path.join(out, name)
        return paths[name]

    if "lineups" in want:
        from rig import BASE_IDS
        order = [b for f in ("chibi", "teen", "adult") for b in BASE_IDS if b.startswith(f)]
        bases = [HeroAssets.build(previews.mannequin(b)) for b in order]
        for a, b in zip(bases, order):
            a.name = b
        sheet.lineup(bases, p("forms_lineup.png"), scale=10, title="Body forms: chibi, teen and adult, female and "
                                                                    "male (standard builds, mannequin palette)")
        sheet.lineup([cast[h] for h in ids], p("lineup.png"), tints=tints)
    if "turnarounds" in want:
        for hid in ids:
            sheet.turnaround(cast[hid], p("turnaround_%s.png" % hid), tint=tints.get(hid, sheet.SASH_TINT))
    if "faces" in want:
        sheet.face_strip([cast[h] for h in ids], p("faces.png"))
        face_plane_light(p("face_plane_light.png"))
        coplanar_faces([cast[h] for h in ids], p("coplanar_faces.png"))
    if "contact" in want:
        fallback = HeroAssets.build("_fallback")
        fallback.name = "_fallback"
        sheet.contact_sheet([cast[h] for h in ids] + [fallback], p("contact_sheet.png"), tints=tints)
    if gifs and "gifs" in want:
        three = [cast[h] for h in FORMS_SET]
        gif.write_gif(gif.idle_film(three, 0.0, 24.0, fps=10), p("idle_blink.gif"), fps=10)
        gif.write_gif(gif.idle_film([cast[h] for h in ids], 2.5, 8.5, fps=20, scale=6, head_only=True),
                      p("idle_blink_faces.gif"), fps=20)
        gif.write_gif(gif.move_film(three, "move.walk"), p("walk.gif"), fps=20)
        gif.write_gif(gif.move_film(three, "move.run"), p("run.gif"), fps=20)
        swing = [HeroAssets.build(sword_variant(h)) if h in ("style_tansy", "style_wren") else cast[h]
                 for h in SWING_SET]
        gif.write_gif(gif.action_film(swing), p("swing.gif"), fps=20)
    return paths
