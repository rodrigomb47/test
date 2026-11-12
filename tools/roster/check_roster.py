#!/usr/bin/env python3
"""Checks the Heroes v2 roster (roster_data.py) and prints the balance tables.

  python3 -I check_roster.py [--strict]

Rules (HEROES_V2.md section 14.3 and ROSTER_PLAN.md sections 2 and 8):
- exactly 200 rows, numbered 1..200, exactly 100 female and 100 male;
- every archetype from the catalogue used, its gender split as planned;
- names and ids unique; signatures unique;
- look key (archetype + palette) unique; kit key (archetype + style + element) unique;
- full key (archetype, role, style, element, temperament) unique;
- balance: each role 28-30 with a gender split within 1, each element 24-26, each weapon style 9-17,
  each temperament 5-9, each talent at least 2 and at most 12, each duel style at least 4 times;
- the slice: 4 F + 4 M, all eight elements, eight different temperaments;
- no name uses a token rejected by a clearance review (REJECTED_TOKENS; ROSTER_PLAN.md section 8, step 4).
"""
import collections
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

ARCHETYPES = {  # id: (group, planned F, planned M)
    "cat": ("A", 3, 2), "fox": ("A", 2, 3), "wolf": ("A", 2, 2), "tanuki": ("A", 1, 2),
    "moon_rabbit": ("A", 2, 2), "oni": ("A", 3, 2), "tengu": ("A", 2, 2), "snow_spirit": ("A", 2, 1),
    "kappa": ("A", 1, 2), "spider_weaver": ("A", 2, 1), "east_dragon": ("A", 2, 3), "west_dragon": ("A", 2, 2),
    "komainu": ("A", 1, 2), "plant_spirit": ("A", 2, 2),
    "shrine": ("B", 3, 2), "onmyoji": ("B", 2, 2), "samurai": ("B", 3, 2), "wanderer": ("B", 2, 2),
    "ninja": ("B", 3, 2), "warrior_monk": ("B", 1, 2), "fan_dancer": ("B", 2, 1), "delinquent": ("B", 1, 2),
    "battle_chef": ("B", 1, 2), "storm_drummer": ("B", 1, 2),
    "sword_cultivator": ("C", 2, 2), "martial_artist": ("C", 2, 2), "jiangshi": ("C", 2, 1), "phoenix": ("C", 1, 2),
    "knight": ("D", 3, 2), "paladin": ("D", 2, 2), "battle_priest": ("D", 2, 2), "witch": ("D", 3, 2),
    "elf": ("D", 3, 2), "vampire": ("D", 2, 2), "demon_lord": ("D", 2, 2), "angel": ("D", 2, 2),
    "reaper": ("D", 1, 2), "necromancer": ("D", 1, 2), "beast_tamer": ("D", 2, 1), "dragoon": ("D", 1, 2),
    "berserker": ("D", 1, 2), "arbalist": ("D", 2, 1), "corsair": ("D", 1, 2), "bard": ("D", 1, 2),
    "alchemist": ("D", 2, 1), "siren": ("D", 2, 1),
    "idol": ("E", 2, 2), "magical": ("E", 2, 2), "maid_butler": ("E", 2, 2), "noble_fencer": ("E", 1, 2),
    "android": ("E", 2, 2), "tactician": ("E", 2, 1), "smith": ("E", 1, 2), "grappler": ("E", 2, 1),
}
ROLES = ["vanguard", "striker", "skirmisher", "marksman", "support", "controller", "summoner"]
ELEMENTS = ["physical", "fire", "frost", "shock", "venom", "blight", "arcane", "radiant"]
STYLES = ["katana", "greatblade", "sword_shield", "twin_blades", "rapier", "polearm", "hammer", "axe", "scythe",
          "fists", "bow", "crossbow", "staff", "focus", "fan", "instrument"]
TEMPERAMENTS = ["hot_blooded", "stoic", "cool_headed", "tsundere", "shy", "sweet_natured", "yandere", "genki",
                "lone_wolf", "proud_noble", "delinquent", "chuunibyou", "airhead", "big_sibling", "trickster",
                "sleepy", "battle_maniac", "gentle_giant", "schemer", "righteous", "melancholic", "glutton",
                "perfectionist", "charmer", "ancient_sage", "coward_who_rises", "gruff_veteran",
                "eccentric_genius", "merchant", "tyrant"]
TALENTS = ["aura", "master_chef", "forgemaster", "surveyor", "forester", "harvest_blessing", "beastmaster",
           "angler_lord", "ember_keeper", "quartermaster", "rally", "mentor", "renown", "drill", "festival_host",
           "steward", "mend", "triage", "revival_rite", "purifier", "lookout", "cartographer", "spymaster",
           "pathfinder", "envoy", "intimidation", "merchant_prince", "tribute_master", "sage_of_letters",
           "architect", "alchemist", "engineer", "warden_of_walls", "tactician", "war_drummer"]
AURA_AFFIXES = ["diligence", "prospector", "regrowth", "green_thumb", "mason", "marksman", "thrifty", "hardy",
                "duelist", "angler", "haggler"]
DUELS = ["rushdown", "zoner", "grappler", "counter", "trapper", "all_rounder", "stance", "summoner"]
STYLE_MECH = {"katana": "iron_sword", "greatblade": "iron_sword", "sword_shield": "iron_sword+shield",
              "twin_blades": "iron_sword", "rapier": "iron_sword", "polearm": "trident", "hammer": "mace",
              "axe": "iron_axe", "scythe": "iron_axe", "fists": "iron_sword (drawn as fists)", "bow": "bow",
              "crossbow": "crossbow", "staff": "thrown + staff prop", "focus": "thrown + held prop",
              "fan": "thrown + fan prop", "instrument": "thrown + instrument prop"}
TALENT_DUTY = {"master_chef": "chef", "forgemaster": "blacksmith", "surveyor": "miner", "forester": "woodcutter",
               "harvest_blessing": "farmer", "beastmaster": "animal_carer", "angler_lord": "fisherman",
               "ember_keeper": "smelter", "quartermaster": "hauler", "merchant_prince": "trader",
               "tribute_master": "trader", "sage_of_letters": "enchanter", "architect": "builder",
               "cartographer": "scout", "spymaster": "scout", "pathfinder": "scout", "alchemist": "enchanter",
               "engineer": "builder", "aura_prospector": "miner", "aura_green_thumb": "farmer",
               "aura_regrowth": "woodcutter", "aura_mason": "builder", "aura_diligence": "builder",
               "aura_hardy": "hauler", "aura_thrifty": "smelter", "aura_marksman": "guard", "aura_duelist": "guard"}
# Name parts rejected by a clearance review (desk review 2026-11-12, CRITIQUE.md): each matched a known character or
# a real performer in the same archetype, so no later row may reuse it as a given name or surname.
REJECTED_TOKENS = {"kagetora", "kagero", "hanayagi", "kikunosuke", "goji", "thalorien", "noctis", "copperpot",
                   "ririka", "hoshizora"}
SLICE = {1, 6, 22, 56, 65, 74, 108, 121}
LEGENDARY = {7, 40, 43, 63, 95, 105, 135, 137, 139, 141}
EPIC = SLICE | {2, 9, 12, 23, 30, 33, 37, 41, 45, 48, 57, 61, 62, 64, 67, 70, 82, 91, 94, 96, 104, 106, 112, 113,
                125, 127, 131, 143, 146, 152, 172, 179}


def load_rows():
    spec = importlib.util.spec_from_file_location("roster_data", os.path.join(HERE, "roster_data.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    rows = []
    for line in mod.ROWS.strip().splitlines():
        p = [x.strip() for x in line.split("|")]
        if len(p) != 13:
            raise SystemExit(f"bad row ({len(p)} fields): {line}")
        n, g, arch, role, style, prop, elem, temp, talent, duel, name, sig, look = p
        n = int(n)
        rows.append(dict(n=n, g=g, arch=arch, role=role, style=style, prop=prop, elem=elem, temp=temp,
                         talent=talent, duel=duel, name=name, sig=sig, look=look,
                         id=re.sub(r"[^a-z0-9]+", "_", name.lower().replace("'", "")).strip("_"),
                         rarity="legendary" if n in LEGENDARY else "epic" if n in EPIC
                         else ("rare" if n % 2 == 0 else "common"),
                         slice=n in SLICE))
    return rows


def talent_base(t):
    return "aura" if t.startswith("aura_") else t


def check(rows, strict):
    errs = []
    nums = [r["n"] for r in rows]
    if nums != list(range(1, 201)):
        errs.append("rows must be numbered 1..200 in order")
    g = collections.Counter(r["g"] for r in rows)
    if g["F"] != 100 or g["M"] != 100:
        errs.append(f"gender split {dict(g)} (need 100/100)")
    for r in rows:
        if r["arch"] not in ARCHETYPES: errs.append(f"{r['n']}: unknown archetype {r['arch']}")
        if r["role"] not in ROLES: errs.append(f"{r['n']}: unknown role {r['role']}")
        if r["style"] not in STYLES: errs.append(f"{r['n']}: unknown style {r['style']}")
        if r["elem"] not in ELEMENTS: errs.append(f"{r['n']}: unknown element {r['elem']}")
        if r["temp"] not in TEMPERAMENTS: errs.append(f"{r['n']}: unknown temperament {r['temp']}")
        tb = talent_base(r["talent"])
        if tb not in TALENTS: errs.append(f"{r['n']}: unknown talent {r['talent']}")
        if tb == "aura" and r["talent"][5:] not in AURA_AFFIXES: errs.append(f"{r['n']}: bad aura {r['talent']}")
        if r["duel"] not in DUELS: errs.append(f"{r['n']}: unknown duel style {r['duel']}")
        if r["g"] not in ("F", "M"): errs.append(f"{r['n']}: gender")
    per_arch = collections.defaultdict(collections.Counter)
    for r in rows:
        per_arch[r["arch"]][r["g"]] += 1
    for a, (_, f, m) in ARCHETYPES.items():
        if per_arch[a]["F"] != f or per_arch[a]["M"] != m:
            errs.append(f"archetype {a}: {dict(per_arch[a])}, planned F{f} M{m}")
    for key, label in [(lambda r: r["name"], "name"), (lambda r: r["id"], "id"), (lambda r: r["sig"], "signature"),
                       (lambda r: (r["arch"], r["look"].split(";")[-1].strip()), "look key (archetype+palette)"),
                       (lambda r: (r["arch"], r["style"], r["elem"]), "kit key (archetype+style+element)"),
                       (lambda r: (r["arch"], r["role"], r["style"], r["elem"], r["temp"]), "full key")]:
        c = collections.Counter(key(r) for r in rows)
        for k, v in c.items():
            if v > 1:
                errs.append(f"duplicate {label}: {k} x{v} (rows {[r['n'] for r in rows if key(r) == k]})")
    # balance
    role_g = collections.defaultdict(collections.Counter)
    for r in rows: role_g[r["role"]][r["g"]] += 1
    for ro in ROLES:
        t = role_g[ro]["F"] + role_g[ro]["M"]
        if not 28 <= t <= 30 or abs(role_g[ro]["F"] - role_g[ro]["M"]) > 1:
            errs.append(f"role {ro}: {t} (F{role_g[ro]['F']} M{role_g[ro]['M']}) want 28-30, split within 1")
    el = collections.Counter(r["elem"] for r in rows)
    for e in ELEMENTS:
        if not 24 <= el[e] <= 26: errs.append(f"element {e}: {el[e]} want 24-26")
    st = collections.Counter(r["style"] for r in rows)
    for s in STYLES:
        if not 9 <= st[s] <= 17: errs.append(f"style {s}: {st[s]} want 9-17")
    te = collections.Counter(r["temp"] for r in rows)
    for t in TEMPERAMENTS:
        if not 5 <= te[t] <= 9: errs.append(f"temperament {t}: {te[t]} want 5-9")
    ta = collections.Counter(talent_base(r["talent"]) for r in rows)
    for t in TALENTS:
        if not 2 <= ta[t] <= 12: errs.append(f"talent {t}: {ta[t]} want 2-12")
    du = collections.Counter(r["duel"] for r in rows)
    for d in DUELS:
        if du[d] < 4: errs.append(f"duel style {d}: {du[d]} want >= 4")
    for r in rows:
        bad = REJECTED_TOKENS & set(re.split(r"[^a-z0-9]+", r["name"].lower()))
        if bad:
            errs.append(f"{r['n']}: name uses a rejected token {sorted(bad)}")
    sl = [r for r in rows if r["slice"]]
    if collections.Counter(r["g"] for r in sl) != collections.Counter({"F": 4, "M": 4}):
        errs.append("slice must be 4 F + 4 M")
    if len({r["elem"] for r in sl}) != 8: errs.append("slice must use all 8 elements")
    if len({r["temp"] for r in sl}) != 8: errs.append("slice temperaments must differ")
    return errs, dict(role_g=role_g, el=el, st=st, te=te, ta=ta, du=du)


def batches(rows):
    """8 content batches of 24 (12 F + 12 M), slice excluded, archetypes spread by striding."""
    fs = [r for r in rows if r["g"] == "F" and not r["slice"]]
    ms = [r for r in rows if r["g"] == "M" and not r["slice"]]
    out = {}
    for k in range(8):
        for r in fs[k::8] + ms[k::8]:
            out[r["n"]] = k + 1
    return out


def main():
    strict = "--strict" in sys.argv
    rows = load_rows()
    errs, s = check(rows, strict)
    print("roles:", {k: dict(v) for k, v in s["role_g"].items()})
    print("elements:", dict(s["el"]))
    print("styles:", dict(s["st"]))
    print("temperaments:", dict(sorted(s["te"].items(), key=lambda x: x[1])))
    print("talents:", dict(sorted(s["ta"].items(), key=lambda x: x[1])))
    print("duel:", dict(s["du"]))
    b = batches(rows)
    bc = collections.Counter(b.values())
    print("batches:", dict(bc))
    for e in errs:
        print("ERROR", e)
    if "--json" in sys.argv:
        for r in rows:
            r["batch"] = 0 if r["slice"] else b[r["n"]]
            r["mech"] = STYLE_MECH[r["style"]]
            r["duty"] = TALENT_DUTY.get(r["talent"], "guard")
        with open(os.path.join(HERE, "roster.json"), "w") as f:
            json.dump(rows, f, indent=1)
    print("OK" if not errs else f"{len(errs)} problems")
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
