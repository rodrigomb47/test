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
Each part is worn by the lint mannequin (lint.lint_hero: short hair, neutral body layer, the part in the lint
palette); the outfits carry their own palettes and faces. The sash is drawn in the preview faction blue.
"""
import os

from PIL import Image, ImageDraw

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
assert set(BASE_ORDER) == set(BASE_IDS)


def _render(a, yaw, scale, head):
    pose = a.stance()
    view = raster.View(yaw, PITCH, scale)
    full = a.bounds([view], (pose,))
    if head:
        hy = a.visual["head_y"]
        b = (max(full[0], -14.0), min(full[1], 14.0), hy - head, min(full[3], hy + 24.0))
    else:
        b = full
    return to_image(a.render(view, pose, bounds=b, pad=3))


def _cells(hero_for, cols, scale, head):
    """[(label, image)] for the columns: (label, base, yaw)."""
    out = []
    cache = {}
    for label, base_id, yaw in cols:
        if base_id not in cache:
            cache[base_id] = HeroAssets.build(hero_for(base_id))
        out.append((label, _render(cache[base_id], yaw, scale, head)))
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
    params = ", ".join("%s %g-%g" % (k, v["min"], v["max"]) for k, v in sorted(part.get("params", {}).items()))
    return "%d cubes%s" % (len(cubes), "\n" + params if params else "")


# params a part is shown with on the sheets (its feature off by default)
SHEET_PARAMS = {"hair/bob": {"tufts": 1, "ahoge": 1}, "tails/fox": {"count": 3}, "legwear/tabi_boots": {"socks": 1},
                "outfit_bottom/trousers": {"wraps": 1}}
PART_COLS = [(b.replace("_", " "), b, FRONT) for b in BASE_ORDER] + [("back (teen f)", "teen_f", 180.0),
                                                                      ("side (teen f)", "teen_f", -90.0)]


def _part_row(job):
    pid, head, scale = job
    params = SHEET_PARAMS.get(pid)
    cells = _cells(lambda b: lint.lint_hero(pid, b, params, sheet=True), PART_COLS, scale, head)
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
    if not only or "outfits.png" in only:
        outfits = lint.load_outfits()
        rows = outfit_rows(outfits, 3, workers)
        paths["outfits.png"] = _grid("Sample outfits (the slice heroes' looks, herosrc/style/outfits.json) on the six "
                                     "bases", rows, os.path.join(out, "outfits.png"),
                                     "front left on each base, then the back on the outfit's own base (the slice "
                                     "plan's form for the hero); weapons and small details come with G6c")
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
