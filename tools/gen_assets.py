#!/usr/bin/env python3
"""Generates Faction Clash's original textures, models and data files.

Run from the repository root:  python3 tools/gen_assets.py
All art is procedurally drawn here (no third-party assets).
"""
import gzip
import io
import json
import os
import random
import struct

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "factionclash")
D = os.path.join(ROOT, "data", "factionclash")
MOD = "factionclash"


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


def noise_tex(base, var, seed, size=16):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    for x in range(size):
        for y in range(size):
            d = rnd.randint(-var, var)
            img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in base) + (255,))
    return img


def border(img, color, width=1):
    s = img.size[0]
    for i in range(s):
        for k in range(width):
            img.putpixel((i, k), color)
            img.putpixel((i, s - 1 - k), color)
            img.putpixel((k, i), color)
            img.putpixel((s - 1 - k, i), color)


def save(img, *parts):
    p = os.path.join(A, "textures", *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


# ---------------------------------------------------------------- block textures
def block_textures():
    wood = (122, 88, 52)
    # reporting station: wooden desk with a bell/brass plate on top
    side = noise_tex(wood, 10, 1)
    border(side, (70, 48, 28, 255))
    for x in range(3, 13):
        side.putpixel((x, 5), (190, 160, 70, 255))
        side.putpixel((x, 10), (90, 62, 35, 255))
    save(side, "block", "reporting_station_side.png")
    top = noise_tex((150, 120, 80), 8, 2)
    border(top, (70, 48, 28, 255))
    for x in range(4, 12):
        for y in range(4, 12):
            top.putpixel((x, y), (235, 225, 190, 255))
    for x in range(5, 11):
        top.putpixel((x, 6), (60, 60, 60, 255))
        top.putpixel((x, 8), (60, 60, 60, 255))
    top.putpixel((11, 11), (200, 40, 40, 255))
    save(top, "block", "reporting_station_top.png")
    # world map board: parchment with land/water shapes
    rnd = random.Random(3)
    mp = Image.new("RGBA", (16, 16), (225, 210, 165, 255))
    for x in range(16):
        for y in range(16):
            v = (x - 8) ** 2 + (y - 7) ** 2 + rnd.randint(-10, 10)
            if v > 40:
                mp.putpixel((x, y), (95, 140, 190, 255))
            elif v > 30:
                mp.putpixel((x, y), (200, 190, 140, 255))
            else:
                mp.putpixel((x, y), (110, 160, 90, 255))
    for (x, y) in [(5, 5), (10, 9), (7, 11)]:
        mp.putpixel((x, y), (190, 30, 30, 255))
    border(mp, (95, 65, 35, 255))
    save(mp, "block", "world_map_board.png")
    save(noise_tex(wood, 10, 4), "block", "world_map_board_side.png")
    # faction heart: dark stone frame with a glowing crimson core
    heart = noise_tex((50, 45, 55), 8, 5)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 4.5:
                g = int(255 - d * 30)
                heart.putpixel((x, y), (g, int(g * 0.25), int(g * 0.2), 255))
            elif d < 5.5:
                heart.putpixel((x, y), (120, 90, 40, 255))
    border(heart, (25, 22, 28, 255))
    save(heart, "block", "faction_heart.png")
    # bedroll: woven straw
    straw = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            c = (210, 180, 90) if (x + y) % 4 < 2 else (185, 150, 70)
            straw.putpixel((x, y), c + (255,))
    for y in range(16):
        straw.putpixel((3, y), (150, 110, 60, 255))
        straw.putpixel((12, y), (150, 110, 60, 255))
    save(straw, "block", "bedroll.png")


# ---------------------------------------------------------------- NPC skins (64x64 player layout)
FACTIONS = {
    "ember_legion": ((170, 40, 35), (60, 30, 25), (210, 170, 60)),
    "grove_covenant": ((50, 120, 55), (70, 55, 35), (200, 200, 120)),
    "tide_alliance": ((40, 95, 160), (35, 45, 70), (230, 230, 240)),
    "stoneguard": ((110, 110, 115), (55, 55, 60), (180, 140, 60)),
}
SKIN = [(236, 192, 160), (201, 150, 110), (150, 100, 70), (98, 66, 46)]
HAIR = [(60, 40, 25), (25, 20, 18), (170, 120, 60), (120, 60, 30)]


def rect(img, x0, y0, x1, y1, c):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def box(img, ox, oy, wdt, hgt, dep, fill):
    """Paints a cuboid's unwrapped faces (Minecraft box UV) using fill(face, u, v) -> color."""
    faces = {
        "top": (ox + dep, oy, wdt, dep), "bottom": (ox + dep + wdt, oy, wdt, dep),
        "right": (ox, oy + dep, dep, hgt), "front": (ox + dep, oy + dep, wdt, hgt),
        "left": (ox + dep + wdt, oy + dep, dep, hgt), "back": (ox + dep * 2 + wdt, oy + dep, wdt, hgt),
    }
    for name, (x0, y0, fw, fh) in faces.items():
        for u in range(fw):
            for v in range(fh):
                c = fill(name, u, v, fw, fh)
                img.putpixel((x0 + u, y0 + v), tuple(c) + (255,))


def skin(faction, variant):
    main, dark, accent = FACTIONS[faction]
    rnd = random.Random(hash((faction, variant)) & 0xffff)
    skin_c = SKIN[variant % len(SKIN)]
    hair = HAIR[(variant * 3 + len(faction)) % len(HAIR)]
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def head(face, u, v, fw, fh):
        if face == "top":
            return hair
        if face == "bottom":
            return skin_c
        if face == "back":
            return hair if v < 6 else skin_c
        if face in ("left", "right"):
            return hair if v < 3 or (v < 5 and u in (0, 7) if face == "right" else v < 5 and u in (0, 7)) else skin_c
        # front
        if v < 2:
            return hair
        if v == 4 and u in (1, 2, 5, 6):
            return (255, 255, 255) if u in (1, 6) else (40, 50, 90)
        if v == 6 and 3 <= u <= 4:
            return (150, 90, 80)
        return skin_c

    box(img, 0, 0, 8, 8, 8, head)

    def body(face, u, v, fw, fh):
        if face in ("top", "bottom"):
            return main
        if v >= 10:
            return dark
        if v == 9:
            return (50, 35, 20)
        if face == "front" and 3 <= u <= 4 and 2 <= v <= 4:
            return accent
        if face == "front" and (u == 0 or u == 7):
            return tuple(max(0, c - 25) for c in main)
        return main

    box(img, 16, 16, 8, 12, 4, body)

    def arm(face, u, v, fw, fh):
        if face == "top":
            return main
        if face == "bottom":
            return skin_c
        return main if v < 7 else skin_c

    box(img, 40, 16, 4, 12, 4, arm)
    box(img, 32, 48, 4, 12, 4, arm)

    def leg(face, u, v, fw, fh):
        if face == "top":
            return dark
        if face == "bottom":
            return (40, 30, 20)
        return (60, 42, 28) if v >= 9 else dark

    box(img, 0, 16, 4, 12, 4, leg)
    box(img, 16, 48, 4, 12, 4, leg)
    return img


def skins():
    for f in FACTIONS:
        for v in range(4):
            save(skin(f, v), "entity", "npc", f"{f}_{v}.png")
    # The Hollow uses vanilla mobs; a fallback skin keeps any stray NPC renderable
    save(skin("stoneguard", 0), "entity", "npc", "hollow_0.png")
    for v in range(1, 4):
        save(skin("stoneguard", v), "entity", "npc", f"hollow_{v}.png")


# ---------------------------------------------------------------- models, states, lang, loot, recipes
def models():
    def simple(name, model):
        w(os.path.join(A, "blockstates", f"{name}.json"), {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        w(os.path.join(A, "models", "block", f"{name}.json"), model)
        w(os.path.join(A, "models", "item", f"{name}.json"), {"parent": f"{MOD}:block/{name}"})

    simple("reporting_station", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{MOD}:block/reporting_station_top", "bottom": f"{MOD}:block/reporting_station_side",
        "side": f"{MOD}:block/reporting_station_side"}})
    simple("world_map_board", {"parent": "minecraft:block/cube", "textures": {
        "north": f"{MOD}:block/world_map_board", "south": f"{MOD}:block/world_map_board",
        "east": f"{MOD}:block/world_map_board", "west": f"{MOD}:block/world_map_board",
        "up": f"{MOD}:block/world_map_board_side", "down": f"{MOD}:block/world_map_board_side",
        "particle": f"{MOD}:block/world_map_board_side"}})
    simple("faction_heart", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/faction_heart"}})
    simple("bedroll", {"parent": "minecraft:block/carpet", "textures": {"wool": f"{MOD}:block/bedroll"}})

    w(os.path.join(A, "lang", "en_us.json"), {
        "itemGroup.factionclash": "Faction Clash",
        "block.factionclash.reporting_station": "Reporting Station",
        "block.factionclash.world_map_board": "World Map Board",
        "block.factionclash.faction_heart": "Faction Heart",
        "block.factionclash.bedroll": "Straw Bedroll",
        "entity.factionclash.faction_npc": "Faction NPC",
        "entity.factionclash.hero": "Hero",
    })

    for name in ("reporting_station", "world_map_board", "bedroll"):
        w(os.path.join(D, "loot_table", "blocks", f"{name}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # The heart drops nothing: a destroyed base cannot be re-created by re-placing it.
    w(os.path.join(D, "loot_table", "blocks", "faction_heart.json"), {"type": "minecraft:block", "pools": []})

    w(os.path.join(D, "recipe", "bedroll.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["WWW"], "key": {"W": {"item": "minecraft:wheat"}},
        "result": {"id": f"{MOD}:bedroll", "count": 1}})
    w(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
      {"replace": False, "values": [f"{MOD}:faction_heart"]})
    w(os.path.join(ROOT, "data", "minecraft", "tags", "block", "needs_iron_tool.json"),
      {"replace": False, "values": [f"{MOD}:faction_heart"]})
    w(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
      {"replace": False, "values": [f"{MOD}:reporting_station", f"{MOD}:world_map_board"]})


# ---------------------------------------------------------------- GameTest structure (NBT)
def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def nbt_payload(tag, value):
    if tag == 3:
        return struct.pack(">i", value)
    if tag == 8:
        return nbt_string(value)
    if tag == 9:
        etype, items = value
        out = struct.pack(">bi", etype, len(items))
        for it in items:
            out += nbt_payload(etype, it)
        return out
    if tag == 10:
        out = b""
        for k, (t, v) in value.items():
            out += struct.pack(">b", t) + nbt_string(k) + nbt_payload(t, v)
        return out + b"\x00"
    raise ValueError(tag)


def structure(name, sx, sy, sz):
    blocks = []
    for x in range(sx):
        for z in range(sz):
            blocks.append({"pos": (9, (3, [x, 0, z])), "state": (3, 0)})
    root = {
        "DataVersion": (3, 3955),
        "size": (9, (3, [sx, sy, sz])),
        "palette": (9, (10, [{"Name": (8, "minecraft:smooth_stone")}])),
        "blocks": (9, (10, blocks)),
        "entities": (9, (10, [])),
    }
    raw = struct.pack(">b", 10) + nbt_string("") + nbt_payload(10, root)
    p = os.path.join(D, "structure", f"{name}.nbt")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as g:
        g.write(raw)
    with open(p, "wb") as f:
        f.write(buf.getvalue())


if __name__ == "__main__":
    block_textures()
    skins()
    models()
    structure("platform", 24, 8, 24)
    structure("runway", 40, 6, 9)
    print("assets generated")
