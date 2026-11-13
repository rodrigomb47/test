"""Parts sheets (G6a/b, HEROES_V2 13.3 and 15.2): the parts library and the sample outfits on all six bases, drawn
by the G2 renderer in the idle stance, for review. Written to docs/heroes-v2/previews/parts/ by
`herogen parts-previews` (`--check` renders them in a temporary folder and fails on a stale sheet).

- `head_<category>.png` (hair, ears and horns, tails and wings, headwear): one row per part, one column per base
  (chibi, teen, adult; female and male) seen from the front left, then the back and the side on the teen; head parts
  are cropped to the shoulders up (they are form-independent, so the crop is the same on every form), tails and
  wings show the whole body.
- `body_<group>.png` (tops and robes, bottoms and legwear, armour and back): one row per part on the six bases and a
  back view.
- `outfits.png`: every sample outfit of herosrc/style/outfits.json (the slice heroes' looks) on the six bases, with a
  back view.
- G6c: `details_accessories.png` (every accessory and marking on the six bases, cropped to where it is worn),
  `weapons.png` (every weapon part on the six bases, drawn, in the stance of its style, then sheathed from the front
  and the back, and from above), `weapons_poses.png` (each slice hero's sample outfit on its own base: sheathed, then
  the weapon in every hand pose of its style from two sides, grips.py, and its special look: both swords, the staff
  ridden, the glow at night) and `patterns_emblems.png` (every pattern and cutout of the painter and every emblem
  stamp at each of its sizes, enlarged texels).
Each part is worn by the lint mannequin (lint.lint_hero: short hair, neutral body layer, the part in the lint
palette); the outfits carry their own palettes and faces. The sash is drawn in the preview faction blue.
"""
import os

from PIL import Image, ImageDraw

import grips
import lint
from render import raster
from render.scene import PITCH, HeroAssets, to_image
from render.sheet import BG, DIM, INK, font, save
from rig import BASE_IDS
from util import ROOT

OUT_DIR = os.path.join(ROOT, "docs", "heroes-v2", "previews", "parts")
BASE_ORDER = ("chibi_f", "chibi_m", "teen_f", "teen_m", "adult_f", "adult_m")
FRONT = -34.0
# (file, title, categories, crop: model px shown below the chin, or 0 for the whole body)
HEAD_SHEETS = (("head_hair.png", "Hair", ("hair",), 17.0),
               ("head_ears_horns.png", "Ears and horns", ("ears", "horns"), 5.0),
               ("head_tails_wings.png", "Tails and wings", ("tails", "wings"), 0),
               ("head_headwear.png", "Headwear", ("headwear",), 6.0))
BODY_SHEETS = (("body_tops_robes.png", "Outfit tops and robes", ("outfit_top", "robes")),
               ("body_bottoms_legwear.png", "Outfit bottoms and legwear", ("outfit_bottom", "legwear")),
               ("body_armour_back.png", "Armour and back", ("armour", "back")))
DETAIL_SHEET = ("details_accessories.png", "Accessories and markings", ("accessories", "markings"))
WEAPON_SHEET = "weapons.png"
POSES_SHEET = "weapons_poses.png"
SWATCH_SHEET = "patterns_emblems.png"
assert set(BASE_ORDER) == set(BASE_IDS)


def _render(a, yaw, scale, head, band=None, pose=None, **kw):
    """One view of a hero in a pose (default: its idle stance). `head`: model px shown below the chin (a head
    part); `band` (G6c): (below, above) model px shown round the waist line (a small accessory)."""
    pose = a.stance() if pose is None else pose
    view = raster.View(yaw, PITCH, scale)
    full = a.bounds([view], (pose,), **{k: v for k, v in kw.items() if k in ("phase", "drawn", "riding")})
    if head:
        hy = a.visual["head_y"]
        b = (max(full[0], -14.0), min(full[1], 14.0), hy - head, min(full[3], hy + 24.0))
    elif band:
        wy = a.built.model.env["waist_y"]
        b = (full[0], full[1], max(full[2], wy - band[0]), min(full[3], wy + band[1]))
    else:
        b = full
    return to_image(a.render(view, pose, bounds=b, pad=3, **kw))


def _cells(hero_for, cols, scale, head, band=None):
    """[(label, image)] for the columns: (label, base, yaw)."""
    out = []
    cache = {}
    for label, base_id, yaw in cols:
        if base_id not in cache:
            cache[base_id] = HeroAssets.build(hero_for(base_id))
        out.append((label, _render(cache[base_id], yaw, scale, head, band)))
    return out


def _grid(title, rows, path, note=None):
    """rows: [(row label, sub label, [(column label, image)])] -> one sheet."""
    f16, f13, f12 = font(16), font(13), font(12)
    ncol = max(len(r[2]) for r in rows)
    col_w = [0] * ncol
    for _, _, cells in rows:
        for i, (_, img) in enumerate(cells):
            col_w[i] = max(col_w[i], img.width)
    label_w = 190
    row_h = [max(img.height for _, img in cells) + 8 for _, _, cells in rows]
    head_h = 58 + 15 * (note.count("\n") if note else 0)
    W = label_w + sum(col_w) + 10 * ncol + 12
    H = head_h + sum(row_h) + 10
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((12, 8), title, fill=INK, font=f16)
    if note:
        for k, line in enumerate(note.split("\n")):
            d.text((12, 30 + 15 * k), line, fill=DIM, font=f12)
    x = label_w
    for i, (lab, _) in enumerate(rows[0][2]):
        d.text((x + col_w[i] // 2, head_h - 14), lab, fill=DIM, font=f12, anchor="ma")
        x += col_w[i] + 10
    y = head_h
    for (lab, sub, cells), h in zip(rows, row_h):
        d.line([(8, y), (W - 8, y)], fill=(140, 150, 168, 255))
        d.text((12, y + 10), lab, fill=INK, font=f13)
        if sub:
            for k, line in enumerate(sub.split("\n")):
                d.text((12, y + 30 + 15 * k), line, fill=DIM, font=f12)
        x = label_w
        for i, (_, img) in enumerate(cells):
            out.alpha_composite(img, (x + (col_w[i] - img.width) // 2, y + h - img.height - 4))
            x += col_w[i] + 10
        y += h
    save(out, path)
    return path


def _part_note(pid, a):
    from parts import load_part
    part = load_part(pid)
    m = a.built.model
    cubes = [c for c in m.cubes if c.part == pid]
    items = ["%s %g-%g" % (k, v["min"], v["max"]) for k, v in sorted(part.get("params", {}).items())]
    params = ", ".join(items)
    if len(params) > 40:
        # a long list (the crest's five switches) wraps to the label column's width
        lines, cur = [], ""
        for it in items:
            if cur and len(cur) + len(it) + 2 > 26:
                lines.append(cur + ",")
                cur = it
            else:
                cur = it if not cur else cur + ", " + it
        params = "\n".join(lines + [cur])
    return "%d cubes%s" % (len(cubes), "\n" + params if params else "")


# params a part is shown with on the sheets (its feature off by default)
SHEET_PARAMS = {"hair/bob": {"tufts": 1, "ahoge": 1}, "tails/fox": {"count": 3}, "legwear/tabi_boots": {"socks": 1},
                "outfit_bottom/trousers": {"wraps": 1}}
PART_COLS = [(b.replace("_", " "), b, FRONT) for b in BASE_ORDER] + [("back (teen f)", "teen_f", 180.0),
                                                                      ("side (teen f)", "teen_f", -90.0)]


# G6c: where a small accessory or a marking is worn: model px shown (below, above) the waist line, so it can be
# drawn larger than a whole body allows
BANDS = {"accessories/choker_bell": (0, 30), "accessories/prayer_beads": (2, 30), "accessories/crow": (0, 32),
         "accessories/vial_bandolier": (6, 26), "accessories/gourd": (12, 14), "accessories/rope_belt": (12, 14),
         "accessories/talisman_strips": (12, 14), "accessories/belt": (12, 14), "accessories/sash_obi": (12, 14),
         "accessories/sash_bow": (12, 14), "accessories/kunai_holster": (16, 8), "accessories/scarf": (4, 30),
         "accessories/foxfire_lanterns": (4, 34), "markings/arm_stripes": (10, 22), "markings/crest": (6, 26),
         "markings/calico": (0, 34)}


def _part_row(job):
    pid, head, scale = job
    params = SHEET_PARAMS.get(pid)
    band = BANDS.get(pid)
    cells = _cells(lambda b: lint.lint_hero(pid, b, params, sheet=True), PART_COLS, scale, head, band)
    a = HeroAssets.build(lint.lint_hero(pid, "teen_f", params, sheet=True))
    note = _part_note(pid, a)
    if params:
        note += "\nshown: " + ", ".join("%s %g" % kv for kv in sorted(params.items()))
    return (pid, note, cells)


def _outfit_row(job):
    o, scale = job
    own = o.get("base", "adult_f")
    cols = [(b.replace("_", " "), b, FRONT) for b in BASE_ORDER] + [("back (%s)" % own.replace("_", " "), own, 180.0)]
    cells = _cells(lambda b: lint.outfit_hero(o, b), cols, scale, 0)
    return (o.get("name", o["id"]), o.get("note", ""), cells)


# ------------------------------------------------------------------ G6c: weapons, hand poses, patterns, emblems

SIDE = 56.0                       # the second view of a hand pose: from the hero's right front
WEAPON_BASE = "teen_f"            # the base the hand poses are solved on


def _posed(a, name):
    return grips.pose(name, a.form_var())


def _weapon_row(job):
    """A weapon part: drawn in the stance of its style on the six bases, then on the teen from the right and from
    above, and sheathed from the front and the back."""
    pid, scale = job
    from parts import weapon_defaults
    d = weapon_defaults(pid)
    poses = grips.style_poses(d["style"])
    cells = []
    built = {}
    for b in BASE_ORDER:
        a = built[b] = HeroAssets.build(lint.lint_hero(pid, b, sheet=True))
        pose = _posed(a, poses[0]) if poses else a.stance(True)
        cells.append((b.replace("_", " "), _render(a, FRONT, scale, 0, pose=pose, drawn=True)))
    a = built[WEAPON_BASE]
    pose = _posed(a, poses[0]) if poses else a.stance(True)
    cells.append(("right side", _render(a, 90.0, scale, 0, pose=pose, drawn=True)))
    cells.append(("sheathed", _render(a, FRONT, scale, 0)))
    cells.append(("sheathed, back", _render(a, 160.0, scale, 0)))
    m = a.built.model
    n = len([c for c in m.cubes if c.part == pid and ("weapon_r" in c.bone or "weapon_l" in c.bone
                                                       or c.bone.startswith("hand_"))])
    note = "%s, %s hand%s\n%d cubes in the hand\nsheath: %s" % (
        d["style"] or "no style", {"pair": "each", "both": "both"}.get(d["hand"], d["hand"]),
        "s" if d["hand"] == "both" else "", n, d["sheath"]) + (", mount" if d["mount"] else "")
    return (pid, note, cells)


# what a slice hero's row ends with: (label, keywords of _render, pose name or None for the idle stance)
SPECIAL = {"natsuho": ("claws away (idle)", {}, None),
           "yugen": ("lanterns at night", {"brightness": 0.25}, None),
           "benika": ("phase 2 at night: stripes", {"phase": 2, "brightness": 0.25}, None),
           "hinae": ("far (fine detail hidden)", {"far": True, "drawn": True}, "focus.stance"),
           "kagenori": ("phase 3: both swords", {"phase": 3, "drawn": True}, "katana.stance"),
           "tobimaru": ("far (fine detail hidden)", {"far": True, "drawn": True}, "twin_blades.stance"),
           "ilsabet": ("phase 3: the standard", {"phase": 3, "drawn": True}, "sword_shield.stance"),
           "ambrose": ("phase 2, on the staff (mount)", {"phase": 2, "riding": True}, "ride.broom")}


def _pose_row(job):
    """A slice hero's sample outfit on its own base: sheathed (front and back), every hand pose of its style from
    the front left and the right front, and its special look."""
    o, scale = job
    base = o.get("base", "adult_f")
    a = HeroAssets.build(lint.outfit_hero(o, base))
    style = (o.get("animations") or {}).get("style")
    cells = [("sheathed", _render(a, FRONT, scale, 0)), ("sheathed, back", _render(a, 160.0, scale, 0))]
    for name in grips.style_poses(style):
        pose = _posed(a, name)
        short = name.split(".", 1)[1]
        cells.append((short, _render(a, FRONT, scale, 0, pose=pose, drawn=True)))
        cells.append((short + ", right", _render(a, SIDE, scale, 0, pose=pose, drawn=True)))
    if o["id"] in SPECIAL:
        label, kw, pose_name = SPECIAL[o["id"]]
        pose = _posed(a, pose_name) if pose_name else None
        cells.append((label, _render(a, FRONT, scale, 0, pose=pose, **kw)))
    w = o["art"].get("weapon", {})
    props = [w.get("prop", "-").split("/")[-1]] + ([w["offhand"]["prop"].split("/")[-1]] if w.get("offhand") else [])
    note = "%s on %s\n%s" % (style, base.replace("_", " "), "\n".join(props))
    return (o.get("name", o["id"]).split(" (")[0], note, cells)


def _free_grid(title, rows, path, note=None):
    """Like _grid, but every cell carries its own label (rows differ in what their columns show)."""
    f16, f13, f12, f11 = font(16), font(13), font(12), font(11)
    label_w = 150
    row_h = [max(img.height for _, img in cells) + 26 for _, _, cells in rows]
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))

    def cell_w(clab, img):
        """A cell is as wide as its image or its label, whichever is wider (labels never run together)."""
        return max(img.width, int(probe.textlength(clab, font=f11)) + 4)
    row_w = [sum(cell_w(c, img) + 10 for c, img in cells) for _, _, cells in rows]
    head_h = 40 + 15 * (note.count("\n") + 1 if note else 0)
    W = label_w + max(row_w) + 12
    H = head_h + sum(row_h) + 10
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((12, 8), title, fill=INK, font=f16)
    if note:
        for k, line in enumerate(note.split("\n")):
            d.text((12, 30 + 15 * k), line, fill=DIM, font=f12)
    y = head_h
    for (lab, sub, cells), h in zip(rows, row_h):
        d.line([(8, y), (W - 8, y)], fill=(140, 150, 168, 255))
        d.text((12, y + 10), lab, fill=INK, font=f13)
        if sub:
            for k, line in enumerate(sub.split("\n")):
                d.text((12, y + 30 + 15 * k), line, fill=DIM, font=f12)
        x = label_w
        for clab, img in cells:
            cw = cell_w(clab, img)
            out.alpha_composite(img, (x + (cw - img.width) // 2, y + h - img.height - 20))
            d.text((x + cw // 2, y + h - 16), clab, fill=DIM, font=f11, anchor="ma")
            x += cw + 10
        y += h
    save(out, path)
    return path


def _swatch_colours():
    import palette
    ramps = palette.resolve(dict(lint.SHEET_PALETTE), "female")
    return ramps


PATTERN_SWATCHES = (("stripes", {"size": 2}), ("stripes", {"size": 2, "dir": "v"}), ("stripes", {"size": 2, "dir": "d"}),
                    ("checks", {"size": 2}), ("plaid", {"size": 3}), ("dots", {"size": 4}), ("pleats", {"size": 3}),
                    ("waves", {"size": 6}), ("scales", {"size": 4}), ("hemp", {"size": 3}), ("petals", {"size": 5}),
                    ("patches", {"size": 5}))
CUTOUT_SWATCHES = ("jagged_2", "tip_jagged_2", "tri_down", "peak", "round_top", "round_bottom", "fur_tips", "torn",
                   "lace")


def swatches(path):
    """Every pattern and cutout of the painter on a 24 x 24 texel cloth panel, and every emblem at each of its
    sizes, enlarged (one texel = 6 screen pixels). Drawn straight from the painter's functions."""
    import emblems
    from paint import Painter, stamp_slot
    from rig import Cube
    ramps = _swatch_colours()
    Z, N = 6, 24
    f16, f12 = font(16), font(12)
    base, alt, alt2 = "primary", "secondary", "accent"

    def tile(fn):
        img = Image.new("RGBA", (N * Z, N * Z), (0, 0, 0, 0))
        px = img.load()
        for j in range(N):
            for i in range(N):
                c = fn(i, j)
                if c is None:
                    continue
                for y in range(Z):
                    for x in range(Z):
                        px[i * Z + x, j * Z + y] = tuple(c) + (255,)
        return img

    def pattern_tile(kind, extra):
        spec = dict({"type": kind, "slots": [] if kind == "pleats" else [alt, alt2], "seed": 0}, **extra)

        def at(i, j):
            slot, tone = Painter.pattern_at(spec, "north", (i + 0.5, N - j - 0.5, 0.0), 1)
            return ramps[slot or base][2 + tone]
        return tile(at)

    def cutout_tile(cut):
        cube = Cube(name="swatch", bone="chest", origin=(0.0, 0.0, 0.0), size=(float(N), float(N), 1.0), slot=base,
                    material="cloth", part="swatch", cutout=cut, taper=0.5 if cut.startswith("tip_") else None)

        def at(i, j):
            return ramps[base][2] if Painter.own_keep(cube, "north", i, j, N, N) else None
        return tile(at)

    def emblem_tile(name, size):
        rows = emblems.bitmap(name, size)
        w, h = len(rows[0]), len(rows)
        img = Image.new("RGBA", ((w + 2) * Z, (h + 2) * Z), tuple(ramps[base][2]) + (255,))
        px = img.load()
        st = {"slot": alt, "slot2": None, "tone": 0}
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                hit = stamp_slot(st, ch, base)
                if hit is None:
                    continue
                c = ramps[hit[0]][2 + hit[1]]
                for y in range(Z):
                    for x in range(Z):
                        px[(i + 1) * Z + x, (j + 1) * Z + y] = tuple(c) + (255,)
        return img

    groups = [("Patterns (cloth panel, 24 x 24 texels; main red, first slot cream, second dark)",
               [("%s %s" % (k, " ".join("%s %s" % kv for kv in sorted(e.items()))), pattern_tile(k, e))
                for k, e in PATTERN_SWATCHES]),
              ("Cutouts (alpha masks; the panel's lower edge is its free end)",
               [(c, cutout_tile(c)) for c in CUTOUT_SWATCHES])]
    per = {}
    for name in sorted(emblems.EMBLEMS):
        per[name] = [("%s %d" % (name, s), emblem_tile(name, s)) for s in emblems.sizes(name)]
    line, lines = [], []
    for name in sorted(per):
        if sum(i.width + 14 for _, i in line + per[name]) > 1180 and line:
            lines.append(line)
            line = []
        line += per[name]
    if line:
        lines.append(line)
    W = 1240
    blocks = []
    for title, cells in groups:
        rows_, cur = [], []
        for c in cells:
            if sum(i.width + 14 for _, i in cur + [c]) > W - 24 and cur:
                rows_.append(cur)
                cur = []
            cur.append(c)
        rows_.append(cur)
        blocks.append((title, rows_))
    blocks.append(("Emblem stamps (each drawn by hand at its sizes; # the stamp's colour, - a tone darker, + a tone "
                   "lighter or the second colour)", lines))
    H = 16 + sum(30 + sum(max(i.height for _, i in r) + 26 for r in rows_) for _, rows_ in blocks)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    y = 10
    for title, rows_ in blocks:
        d.text((12, y), title, fill=INK, font=f16)
        y += 28
        for r in rows_:
            x = 12
            h = max(i.height for _, i in r)
            for lab, img in r:
                out.alpha_composite(img, (x, y + h - img.height))
                d.text((x, y + h + 4), lab, fill=DIM, font=f12)
                x += max(img.width, int(d.textlength(lab, font=f12))) + 14
            y += h + 26
        y += 2
    save(out, path)
    return path


def _map(fn, jobs, workers):
    """Rows in order, built by `workers` processes (each row builds its hero on seven views)."""
    if workers <= 1 or len(jobs) <= 1:
        return [fn(j) for j in jobs]
    import multiprocessing
    with multiprocessing.get_context("fork").Pool(min(workers, len(jobs))) as pool:
        return pool.map(fn, jobs, chunksize=1)


def part_rows(pids, head, scale, workers=1):
    return _map(_part_row, [(pid, head, scale) for pid in pids], workers)


def outfit_rows(outfits, scale, workers=1):
    rows = _map(_outfit_row, [(o, scale) for o in outfits], workers)
    for r in rows:                       # the back view's column label names each outfit's own base
        r[2][-1] = ("back (own base)", r[2][-1][1])
    return rows


def write_all(out=OUT_DIR, only=None, workers=None):
    """Every parts sheet; `only` limits to file names. Returns {name: path}."""
    workers = workers or min(4, os.cpu_count() or 1)
    paths = {}
    ids = lint.part_ids()
    for name, title, cats, head in HEAD_SHEETS:
        if only and name not in only:
            continue
        pids = [p for p in ids if p.split("/")[0] in cats]
        rows = part_rows(pids, head, 4 if head and head < 10 else 3, workers)
        paths[name] = _grid("%s (%d parts) on the six bases, idle stance, lint mannequin" % (title, len(pids)), rows,
                            os.path.join(out, name), "front left on each base; then the back and the side on the teen "
                            "(female)\nhair, ears and tails in a mid brown (alt: a cream tip, a pink inner ear), "
                            "horns ivory, headwear in its own colours")
    for name, title, cats in BODY_SHEETS:
        if only and name not in only:
            continue
        pids = [p for p in ids if p.split("/")[0] in cats]
        rows = part_rows(pids, 0, 3, workers)
        paths[name] = _grid("%s (%d parts) on the six bases, idle stance, lint mannequin" % (title, len(pids)), rows,
                            os.path.join(out, name), "front left on each base; then the back and the side on the teen "
                            "(female)\nmain red, trim gold, alt cream, detail dark")
    name, title, cats = DETAIL_SHEET
    if not only or name in only:
        pids = [p for p in ids if p.split("/")[0] in cats]
        rows = part_rows(pids, 0, 4, workers)
        paths[name] = _grid("%s (%d parts) on the six bases, idle stance, lint mannequin" % (title, len(pids)), rows,
                            os.path.join(out, name), "front left on each base; then the back and the side on the teen "
                            "(female); cropped to where the part is worn\neach in its own default colours; a crest "
                            "stamps the mannequin's emblem (a ring round a diamond)")
    if not only or WEAPON_SHEET in only:
        pids = [p for p in ids if p.startswith("weapons/")]
        rows = _map(_weapon_row, [(pid, 3) for pid in pids], workers)
        paths[WEAPON_SHEET] = _free_grid(
            "Weapons (%d parts): drawn in the stance of the prop's style on the six bases, then sheathed" % len(pids),
            rows, os.path.join(out, WEAPON_SHEET),
            "lint mannequin, the part's own colours; the hand, the sheath and the grip are the part's defaults")
    if not only or POSES_SHEET in only:
        rows = _map(_pose_row, [(o, 3) for o in lint.load_outfits()], workers)
        paths[POSES_SHEET] = _free_grid(
            "The slice weapons in every hand pose of their style (herosrc/anim/poses/<style>.json)", rows,
            os.path.join(out, POSES_SHEET),
            "each sample outfit on its own base: sheathed, then each pose from the front left and from the right "
            "front (held as in game: sleeves and capes hang), then its special look")
    if not only or SWATCH_SHEET in only:
        paths[SWATCH_SHEET] = swatches(os.path.join(out, SWATCH_SHEET))
    if not only or "outfits.png" in only:
        outfits = lint.load_outfits()
        rows = outfit_rows(outfits, 3, workers)
        paths["outfits.png"] = _grid("Sample outfits (the slice heroes' looks, herosrc/style/outfits.json) on the six "
                                     "bases", rows, os.path.join(out, "outfits.png"),
                                     "front left on each base, then the back on the outfit's own base (the slice "
                                     "plan's form for the hero)\nweapons sheathed; accessories, markings and "
                                     "emblems of G6c")
    return paths


def check(out=OUT_DIR, only=None, workers=None):
    """Render the sheets in a temporary folder and list the committed ones that differ (stale) or are missing."""
    import tempfile
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(tmp, only, workers)
        for name, path in sorted(fresh.items()):
            have = os.path.join(out, name)
            if not os.path.exists(have):
                problems.append("%s: missing" % name)
                continue
            with open(path, "rb") as f1, open(have, "rb") as f2:
                if f1.read() != f2.read():
                    problems.append("%s: differs from a fresh render (run herogen parts-previews)" % name)
    return problems
