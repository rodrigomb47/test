"""The artifact icons' contact sheet for the owner (R6, B5): `herogen icons sheet [--check]`.

- `sheet.png`: every icon grouped by tier (the growing and the engineered artifacts in groups of their own), its name
  in the tier's colour (ARTIFACTS.md 1.3), what it shows, the icon at 1x and 2x in a vanilla-style slot and enlarged;
  the pulsing ones with their four frames, the growing ones with their four growth states (full when enlarged).
- `inventory.png`: a mock inventory: our icons beside real vanilla item textures, at GUI scales 1, 2 and 3. The
  vanilla textures are read at render time, read-only, from the 1.21.1 client jar in the Gradle cache (never copied
  into the repository); without the jar this image is skipped with a note.
"""
import glob
import io
import os
import zipfile

from PIL import Image, ImageDraw

import icons
import util
from render.sheet import font

OUT_DIR = os.path.join(util.ROOT, "docs", "heroes-v2", "previews", "artifacts")
BG = (30, 28, 36, 255)                 # a tooltip-dark background, so the tier colours read as in game
INK = (236, 236, 240, 255)
DIM = (168, 166, 178, 255)
SLOT = (139, 139, 139, 255)            # vanilla's slot grey
SLOT_DARK = (55, 55, 55, 255)
SLOT_LIGHT = (255, 255, 255, 255)
PANEL = (198, 198, 198, 255)           # vanilla's container panel grey

TIER_ORDER = ("component", "advanced", "greater", "rare", "signature", "sigil", "token")
# The sheet's groups: (tier, kind), kind from the catalogue's heading (icons.CATALOGUE_TIERS) or a spec's growth.
GROUPS = (("component", None), ("advanced", None), ("advanced", "growing"), ("greater", None),
          ("greater", "engineered"), ("rare", None), ("signature", None), ("sigil", None), ("token", None))
KIND_LABELS = {"growing": "Advanced, growing: aqua; four icon states, empty to full (ARTIFACTS.md 1.2, 7.8)",
               "engineered": "Greater, engineered: light purple; built at the Artificer's Bench (ARTIFACTS.md 7.9)"}

# The mock inventory: rows of 9, vanilla item ids (from the jar) and our icon ids side by side.
INVENTORY_ROWS = (
    ("flint", "knapped_flint", "gold_nugget", "hale_locket", "lapis_lazuli", "lapis_lens", "copper_ingot",
     "copper_coil", "amethyst_tuner"),
    ("ender_pearl", "starless_pearl", "coal", "kiln_heart", "blaze_rod", "blaze_rod_bracer", "echo_shard",
     "deepecho_signet", "lifebound_chime"),
    ("iron_ingot", "riveted_buckle", "feather", "fletched_charm", "spider_eye", "spider_eye_vial",
     "glistering_melon_slice", "glistering_charm", "clockwork_pin"),
    ("nether_star", "hollow_star", "wither_token", "end_crystal", "spirecore", "ender_dragon_token",
     "netherite_upgrade_smithing_template", "artificers_sigil", "stewards_seal"),
    ("glass_bottle", "bloodglass_ampoule", "bloodglass_ampoule_3", "amethyst_shard", "veinseeker_geode_3",
     "soul_lantern", "vigil_lantern_3", "phantom_membrane", "skyroad_pennant_3"),
    ("bell", "graveward_bell_3", "lead", "rivals_cord_3", "hearthroot_seed_3", "scarbark_amulet_3", "observer_lens",
     "piston_bracer", "daylight_coil"),
)
STATE_WORDS = ("empty", "a third", "two thirds", "full")


def _hex(c):
    v = c.lstrip("#")
    return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def slot(scale, icon=None):
    """A vanilla-style 18x18 slot at a GUI scale (drawn here, not copied): dark top-left, light bottom-right."""
    s = 18 * scale
    im = Image.new("RGBA", (s, s), SLOT)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, s - 1, scale - 1), fill=SLOT_DARK)
    d.rectangle((0, 0, scale - 1, s - 1), fill=SLOT_DARK)
    d.rectangle((0, s - scale, s - 1, s - 1), fill=SLOT_LIGHT)
    d.rectangle((s - scale, 0, s - 1, s - 1), fill=SLOT_LIGHT)
    d.rectangle((0, s - scale, scale - 1, s - 1), fill=SLOT)
    d.rectangle((s - scale, 0, s - 1, scale - 1), fill=SLOT)
    if icon is not None:
        im.alpha_composite(icon.resize((16 * scale, 16 * scale), Image.NEAREST), (scale, scale))
    return im


def _wrap(text, f, width, measure):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if measure.textlength(t, font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def kind_of(icon_id, spec, cat):
    """The sheet group's kind of an icon: "growing" for a spec with growth states, else its catalogue heading's."""
    if spec.get("growth"):
        return "growing"
    entry = cat.get(icon_id)
    return entry.kind if entry is not None else None


def texture_image(name, shapes, materials, specs):
    """The first frame of a texture by its name (an icon id or a growth state's `<id>_<n>`), or None."""
    owner = icons.texture_owners(specs).get(name)
    if owner is None:
        return None
    icon_id, state = owner
    return icons.image_of(icon_id, specs[icon_id], shapes, materials, state).crop((0, 0, 16, 16))


def contact_sheet(path):
    shapes, materials, specs = icons.load()
    cat = icons.catalogue()
    cols, cw, ch = 4, 312, 132
    big = 6
    groups = [((t, kd), sorted((k for k, v in specs.items() if v["tier"] == t and kind_of(k, v, cat) == kd),
                               key=lambda k: specs[k]["name"]))
              for t, kd in GROUPS]
    groups = [(g, ids) for g, ids in groups if ids]
    n_textures = sum(len(icons.textures(k, v)) for k, v in specs.items())
    height = 100
    for _, ids in groups:
        height += 34 + ((len(ids) + cols - 1) // cols) * ch
    W = 16 + cols * cw
    out = Image.new("RGBA", (W, height + 12), BG)
    d = ImageDraw.Draw(out)
    f20, f14, f12 = font(20), font(14), font(12)
    d.text((16, 12), "Artifact icons (R6) and boss hero tokens (B5): %d items, %d item textures by herogen icons"
           % (len(specs), n_textures), fill=INK, font=f20)
    d.text((16, 40), "Each icon at 1x and 2x in a vanilla-style slot and at %dx, its name in its tier's colour "
                     "(ARTIFACTS.md 1.3). Rare finds, signatures and tokens also get the enchantment glint in game "
                     "(the item side)." % big, fill=DIM, font=f12)
    d.text((16, 56), "Signatures and tokens pulse slowly (their four frames at 2x); growing artifacts show their four "
                     "states at 2x (empty, a third, two thirds, full) and full at %dx." % big, fill=DIM, font=f12)
    d.text((16, 72), "Sources: herosrc/artifacts/icons.json and shapes.json. Textures: "
                     "assets/factionclash/textures/item/<id>.png, and <id>_1.png .. <id>_3.png for the growth states.",
           fill=DIM, font=f12)
    y = 100
    for (tier, kind), ids in groups:
        colour, label = icons.TIER_COLOURS[tier]
        label = KIND_LABELS.get(kind, label)
        d.text((16, y + 6), "%s (%d)" % (label, len(ids)), fill=_hex(colour), font=f14)
        d.line((16, y + 26, W - 16, y + 26), fill=(70, 66, 80, 255))
        y += 34
        for i, k in enumerate(ids):
            spec = specs[k]
            im = icons.image_of(k, spec, shapes, materials, icons.growth_states(spec)[0])
            first = im.crop((0, 0, 16, 16))
            states = [icons.image_of(k, spec, shapes, materials, st).crop((0, 0, 16, 16))
                      for st in icons.growth_states(spec)]
            shown = states[-1]                  # enlarged: the full look (a growing artifact's last state)
            x0 = 16 + (i % cols) * cw
            y0 = y + (i // cols) * ch
            tile = Image.new("RGBA", (16 * big + 8, 16 * big + 8), SLOT)
            tile.alpha_composite(shown.resize((16 * big, 16 * big), Image.NEAREST), (4, 4))
            out.alpha_composite(tile, (x0, y0))
            tx = x0 + 16 * big + 16
            d.text((tx, y0), spec["name"], fill=_hex(colour), font=f14)
            ly = y0 + 20
            for line in _wrap(spec["what"], f12, cw - (16 * big + 28), d)[:3]:
                d.text((tx, ly), line, fill=DIM, font=f12)
                ly += 15
            sy = y0 + 16 * big + 8 - 36
            out.alpha_composite(slot(1, first), (tx, sy + 18))
            n = im.height // 16
            if n > 1:                       # the pulse: its four frames at 2x (the first is the still look)
                for fr in range(n):
                    out.alpha_composite(slot(2, im.crop((0, 16 * fr, 16, 16 * fr + 16))), (tx + 24 + 38 * fr, sy))
            elif len(states) > 1:           # the growth states at 2x, empty to full
                for i_st, st_im in enumerate(states):
                    out.alpha_composite(slot(2, st_im), (tx + 24 + 38 * i_st, sy))
            else:
                out.alpha_composite(slot(2, first), (tx + 24, sy))
                if tier in icons.GLINT:
                    d.text((tx + 66, sy + 10), "glint in game", fill=DIM, font=f12)
        y += ((len(ids) + cols - 1) // cols) * ch
    util.save_png(path, out)
    return path


# ------------------------------------------------------------------ the mock inventory

def vanilla_jar():
    """The Minecraft 1.21.1 client jar (or NeoForge's client resources jar) in the Gradle cache, or None."""
    home = os.path.expanduser("~")
    cands = [os.path.join(home, ".gradle", "caches", "neoformruntime", "artifacts", "minecraft_1.21.1_client.jar")]
    cands += sorted(glob.glob(os.path.join(util.ROOT, "build", "moddev", "artifacts",
                                           "neoforge-*-client-extra-aka-minecraft-resources.jar")))
    cands += sorted(glob.glob(os.path.join(home, ".gradle", "caches", "**", "*1.21.1*client*.jar"), recursive=True))
    for c in cands:
        if os.path.isfile(c):
            return c
    return None


def vanilla_item(jar, name):
    with zipfile.ZipFile(jar) as z:
        data = z.read("assets/minecraft/textures/item/%s.png" % name)
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    return im.crop((0, 0, 16, 16))


def inventory(path, jar):
    shapes, materials, specs = icons.load()
    rows = []
    for row in INVENTORY_ROWS:
        cells = []
        for k in row:
            ours = texture_image(k, shapes, materials, specs)
            cells.append((ours, True) if ours is not None else (vanilla_item(jar, k), False))
        rows.append(cells)
    scales = (1, 2, 3)
    blocks = []
    for sc in scales:
        w = 9 * 18 * sc + 14 * sc
        h = len(rows) * 18 * sc + 14 * sc
        panel = Image.new("RGBA", (w, h), PANEL)
        pd = ImageDraw.Draw(panel)
        pd.rectangle((0, 0, w - 1, sc - 1), fill=SLOT_LIGHT)
        pd.rectangle((0, 0, sc - 1, h - 1), fill=SLOT_LIGHT)
        pd.rectangle((0, h - sc, w - 1, h - 1), fill=SLOT_DARK)
        pd.rectangle((w - sc, 0, w - 1, h - 1), fill=SLOT_DARK)
        for r, cells in enumerate(rows):
            for c, (im, _) in enumerate(cells):
                panel.alpha_composite(slot(sc, im), (7 * sc + c * 18 * sc, 7 * sc + r * 18 * sc))
        blocks.append((sc, panel))
    f20, f12 = font(20), font(12)
    W = 32 + sum(p.width for _, p in blocks) + 24 * (len(blocks) - 1)
    top = 72 + 16 * len(INVENTORY_ROWS)
    H = top + 8 + max(p.height for _, p in blocks)
    out = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(out)
    d.text((16, 12), "Artifact icons beside vanilla items (mock inventory, GUI scales 1, 2 and 3)", fill=INK,
           font=f20)
    owners = icons.texture_owners(specs)
    for r, row in enumerate(INVENTORY_ROWS):
        names = []
        for k in row:
            if k in owners:
                icon_id, state = owners[k]
                names.append(specs[icon_id]["name"] + (" (%s)" % STATE_WORDS[state] if state is not None else ""))
            else:
                names.append(k.replace("_", " "))
        d.text((16, 40 + 16 * r), "Row %d: %s" % (r + 1, ", ".join(names)), fill=DIM, font=f12)
    d.text((16, 40 + 16 * len(INVENTORY_ROWS)), "Ours are named in capitals. Vanilla textures read from the 1.21.1 "
                                                "client jar at render time (not stored in the repository); the slots "
                                                "are drawn, not copied. No glint or pulse here.", fill=DIM, font=f12)
    x = 16
    for sc, p in blocks:
        out.alpha_composite(p, (x, top))
        x += p.width + 24
    util.save_png(path, out)
    return path


def write_all(out=OUT_DIR):
    """{name: path} of the sheets written; `inventory.png` only when the vanilla jar is found."""
    paths = {"sheet.png": contact_sheet(os.path.join(out, "sheet.png"))}
    jar = vanilla_jar()
    if jar:
        paths["inventory.png"] = inventory(os.path.join(out, "inventory.png"), jar)
    return paths


def check(out=OUT_DIR):
    """(problems, notes): render in a temporary folder and compare with the committed sheets byte for byte."""
    import tempfile
    problems, notes = [], []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = write_all(tmp)
        if "inventory.png" not in fresh:
            notes.append("inventory.png: the Minecraft 1.21.1 client jar is not in the Gradle cache; not checked")
        for name, path in sorted(fresh.items()):
            have = os.path.join(out, name)
            if not os.path.exists(have):
                problems.append("%s: missing (run herogen icons sheet)" % name)
                continue
            with open(path, "rb") as f1, open(have, "rb") as f2:
                if f1.read() != f2.read():
                    problems.append("%s: differs from a fresh render (run herogen icons sheet)" % name)
    return problems, notes
