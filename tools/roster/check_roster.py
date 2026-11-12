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
- no name uses a token rejected by a clearance review (REJECTED_TOKENS; ROSTER_PLAN.md section 8, step 4);
- mobility (MOBILITY_AND_WORK.md sections 1 and 14): a primary trait the archetype allows (ARCH_MOBILITY) with tier 1-3,
  an optional secondary of another family with tier 1-2 and at most the primary's; tier 3 only for epic and legendary
  heroes; flight only for archetypes with a flight carrier and never with a glider or hover secondary; hover and glider
  never together; snowstride needs frost (or a snow spirit), ember_stride fire (or a phoenix); a look with wings flies
  or glides; a signature that leaps, hops, pounces, vaults, wall-runs or dives has an airborne trait, one that charges
  has charger; flyers are 10-15% of the roster with a gender split within 3; every primary family 8-50 heroes; the
  slice's primaries cover at least 5 families and include a flyer;
- work mastery: a line starting with the hero's duty, 20-170 characters.
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
# Mobility (MOBILITY_AND_WORK.md section 1). Primary families, and the secondary-only elemental footings.
MOBILITY_PRIMARY = ["runner", "charger", "leaper", "acrobat", "climber", "glider", "flyer", "hover", "swimmer"]
MOBILITY_SECONDARY = [m for m in MOBILITY_PRIMARY if m != "flyer"] + ["snowstride", "ember_stride"]
AIRBORNE = {"leaper", "acrobat", "glider", "flyer"}
# Primary traits each archetype may take (the first is the format-2 default when a hero file has no mobility block).
ARCH_MOBILITY = {
    "cat": ["acrobat", "climber", "runner"], "fox": ["runner", "leaper", "glider", "acrobat"],
    "wolf": ["runner", "charger"], "tanuki": ["charger", "leaper", "glider", "runner"],
    "moon_rabbit": ["leaper", "acrobat"], "oni": ["charger", "runner", "leaper"], "tengu": ["flyer", "glider"],
    "snow_spirit": ["hover"], "kappa": ["swimmer"], "spider_weaver": ["climber"],
    "east_dragon": ["flyer", "swimmer", "acrobat"], "west_dragon": ["flyer", "glider", "leaper", "charger"],
    "komainu": ["charger", "leaper", "acrobat"], "plant_spirit": ["climber", "glider", "charger"],
    "shrine": ["runner", "leaper", "charger"], "onmyoji": ["glider", "hover", "flyer"],
    "samurai": ["charger", "leaper", "runner"], "wanderer": ["runner", "acrobat", "charger"],
    "ninja": ["acrobat", "climber", "glider"], "warrior_monk": ["charger", "leaper", "runner", "acrobat"],
    "fan_dancer": ["acrobat", "glider"], "delinquent": ["runner", "charger", "leaper"],
    "battle_chef": ["runner", "charger"], "storm_drummer": ["leaper", "runner", "glider"],
    "sword_cultivator": ["flyer", "hover", "acrobat", "runner"], "martial_artist": ["acrobat", "leaper", "charger"],
    "jiangshi": ["leaper", "charger"], "phoenix": ["flyer"], "knight": ["charger"], "paladin": ["charger", "flyer"],
    "battle_priest": ["runner", "charger"], "witch": ["flyer", "glider", "hover"],
    "elf": ["runner", "climber", "acrobat"], "vampire": ["flyer", "glider", "charger"],
    "demon_lord": ["flyer", "acrobat", "runner", "hover"], "angel": ["flyer"], "reaper": ["hover", "runner"],
    "necromancer": ["hover", "runner"], "beast_tamer": ["runner", "charger"], "dragoon": ["leaper", "charger"],
    "berserker": ["charger", "leaper"], "arbalist": ["runner", "charger", "climber"], "corsair": ["swimmer"],
    "bard": ["runner", "acrobat"], "alchemist": ["runner", "leaper"], "siren": ["swimmer"],
    "idol": ["acrobat", "runner"], "magical": ["flyer", "hover", "charger"], "maid_butler": ["runner"],
    "noble_fencer": ["acrobat", "runner"], "android": ["hover", "glider", "flyer"], "tactician": ["runner"],
    "smith": ["charger"], "grappler": ["leaper", "charger", "runner"],
}
# What carries a flyer (a part of the parts library: wings, or a `mounts/` part; MOBILITY_AND_WORK.md 2.5, 4.3).
FLIGHT_CARRIER = {"tengu": "crow wings", "east_dragon": "cloud", "west_dragon": "dragon wings",
                  "onmyoji": "paper crane", "sword_cultivator": "flying sword", "phoenix": "ember wings",
                  "paladin": "light wings", "witch": "broom", "vampire": "bat wings", "demon_lord": "demon wings",
                  "angel": "feathered wings", "magical": "star wings", "android": "jets"}
SIG_AIRBORNE = re.compile(r"\b(leap|leaps|hop|hops|hopping|jump|pounce|vault|vaulting|wall-run|backflips?|"
                          r"diving|dives from|wing dives|fallen dive)\b", re.I)
SIG_CHARGE = re.compile(r"(?<!long )\bcharges?\b(?! (?:a |tell))", re.I)  # a run, not charging a spell or a beam
# Rows whose signature words describe something other than the hero itself.
FIT_EXEMPT = {171: "the dolphin spirits leap from her song, not she"}
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
        if len(p) != 15:
            raise SystemExit(f"bad row ({len(p)} fields): {line}")
        n, g, arch, role, style, prop, elem, temp, talent, duel, name, sig, look, mob, work = p
        n = int(n)
        rows.append(dict(n=n, g=g, arch=arch, role=role, style=style, prop=prop, elem=elem, temp=temp,
                         talent=talent, duel=duel, name=name, sig=sig, look=look, mob=mob, work=work,
                         mobility=parse_mobility(mob),
                         id=re.sub(r"[^a-z0-9]+", "_", name.lower().replace("'", "")).strip("_"),
                         rarity="legendary" if n in LEGENDARY else "epic" if n in EPIC
                         else ("rare" if n % 2 == 0 else "common"),
                         slice=n in SLICE))
    return rows


def parse_mobility(text):
    """'acrobat:3+climber:1' -> {'primary': {'trait': 'acrobat', 'tier': 3}, 'secondary': {...} or None}."""
    parts = []
    for chunk in text.split("+"):
        m = re.fullmatch(r"([a-z_]+):([0-9])", chunk.strip())
        parts.append({"trait": m.group(1), "tier": int(m.group(2))} if m else {"trait": chunk.strip(), "tier": 0})
    return {"primary": parts[0], "secondary": parts[1] if len(parts) > 1 else None, "extra": parts[2:]}


def mobility_traits(r):
    m = r["mobility"]
    return {m["primary"]["trait"]} | ({m["secondary"]["trait"]} if m["secondary"] else set())


def check_mobility(rows):
    errs = []
    for r in rows:
        m, n = r["mobility"], r["n"]
        pri, sec = m["primary"], m["secondary"]
        if m["extra"]:
            errs.append(f"{n}: mobility has more than a primary and a secondary ({r['mob']})")
        if pri["trait"] not in MOBILITY_PRIMARY:
            errs.append(f"{n}: unknown primary mobility {pri['trait']}")
            continue
        if not 1 <= pri["tier"] <= 3:
            errs.append(f"{n}: primary mobility tier {pri['tier']} (1-3)")
        if pri["trait"] not in ARCH_MOBILITY[r["arch"]]:
            errs.append(f"{n}: {r['arch']} may not take {pri['trait']} (allowed {ARCH_MOBILITY[r['arch']]})")
        if pri["tier"] == 3 and r["rarity"] not in ("epic", "legendary"):
            errs.append(f"{n}: tier 3 mobility on a {r['rarity']} hero (epic and legendary only)")
        if sec:
            if sec["trait"] not in MOBILITY_SECONDARY:
                errs.append(f"{n}: unknown or primary-only secondary mobility {sec['trait']}")
            if sec["trait"] == pri["trait"]:
                errs.append(f"{n}: secondary mobility repeats the primary")
            if not 1 <= sec["tier"] <= 2 or sec["tier"] > pri["tier"]:
                errs.append(f"{n}: secondary tier {sec['tier']} (1-2, at most the primary's {pri['tier']})")
            if pri["trait"] == "flyer" and sec["trait"] in ("glider", "hover"):
                errs.append(f"{n}: a flyer already glides and needs no {sec['trait']} secondary")
            if {pri["trait"], sec["trait"]} == {"glider", "hover"}:
                errs.append(f"{n}: hover and glider never together")
            if sec["trait"] == "snowstride" and r["elem"] != "frost" and r["arch"] != "snow_spirit":
                errs.append(f"{n}: snowstride needs the frost element")
            if sec["trait"] == "ember_stride" and r["elem"] != "fire" and r["arch"] != "phoenix":
                errs.append(f"{n}: ember_stride needs the fire element")
        if pri["trait"] == "flyer" and r["arch"] not in FLIGHT_CARRIER:
            errs.append(f"{n}: a flyer needs an archetype with a flight carrier")
        traits = mobility_traits(r)
        if "wings" in r["look"].lower() and not traits & {"flyer", "glider"}:
            errs.append(f"{n}: the look has wings, so it flies or glides")
        if SIG_AIRBORNE.search(r["sig"]) and not traits & AIRBORNE and n not in FIT_EXEMPT:
            errs.append(f"{n}: the signature leaves the ground, so it needs one of {sorted(AIRBORNE)}")
        if SIG_CHARGE.search(r["sig"]) and "charger" not in traits:
            errs.append(f"{n}: the signature charges, so it needs charger")
        duty = TALENT_DUTY.get(r["talent"], "guard")
        if not r["work"].startswith(duty + ": ") or not 20 <= len(r["work"]) <= 170:
            errs.append(f"{n}: work mastery line must start with '{duty}: ' and be 20-170 characters")
    pc = collections.Counter(r["mobility"]["primary"]["trait"] for r in rows)
    for t in MOBILITY_PRIMARY:
        if not 8 <= pc[t] <= 50:
            errs.append(f"primary mobility {t}: {pc[t]} want 8-50")
    fly = [r for r in rows if r["mobility"]["primary"]["trait"] == "flyer"]
    fg = collections.Counter(r["g"] for r in fly)
    if not 20 <= len(fly) <= 30:
        errs.append(f"flyers: {len(fly)} want 20-30 (10-15%)")
    if abs(fg["F"] - fg["M"]) > 3:
        errs.append(f"flyers' gender split {dict(fg)} (within 3)")
    sl = [r for r in rows if r["slice"]]
    sp = {r["mobility"]["primary"]["trait"] for r in sl}
    if len(sp) < 5 or "flyer" not in sp:
        errs.append(f"slice primaries {sorted(sp)}: at least 5 families and a flyer")
    return errs, pc


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
    merrs, mob = check_mobility(rows)
    errs += merrs
    sl = [r for r in rows if r["slice"]]
    if collections.Counter(r["g"] for r in sl) != collections.Counter({"F": 4, "M": 4}):
        errs.append("slice must be 4 F + 4 M")
    if len({r["elem"] for r in sl}) != 8: errs.append("slice must use all 8 elements")
    if len({r["temp"] for r in sl}) != 8: errs.append("slice temperaments must differ")
    return errs, dict(role_g=role_g, el=el, st=st, te=te, ta=ta, du=du, mob=mob)


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
    print("mobility (primary):", dict(s["mob"]))
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
            r["mobility"].pop("extra", None)
            if r["mobility"]["primary"]["trait"] == "flyer":
                r["mobility"]["carrier"] = FLIGHT_CARRIER[r["arch"]]
            del r["mob"]
        with open(os.path.join(HERE, "roster.json"), "w") as f:
            json.dump(rows, f, indent=1)
    print("OK" if not errs else f"{len(errs)} problems")
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
