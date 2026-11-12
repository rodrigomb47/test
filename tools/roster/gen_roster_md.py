#!/usr/bin/env python3
"""Writes docs/heroes-v2/ROSTER_PLAN.md from roster_data.py (run check_roster.py --json first; it must print OK).

  python3 -I gen_roster_md.py
"""
import collections
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import check_roster as cr  # noqa: E402

GROUPS = {"A": "Animal-kin and yokai", "B": "Shrine, court and martial Japan", "C": "Wuxia and East Asian myth",
          "D": "Western fantasy", "E": "Modern, pop and science fiction"}
BASE_STYLE = {
    "cat": "tide_port", "fox": "cherry_court", "wolf": "grove_lodge", "tanuki": "mud_town",
    "moon_rabbit": "ivory_palace", "oni": "ember_fortress", "tengu": "stone_hold", "snow_spirit": "frost_hall",
    "kappa": "tide_port", "spider_weaver": "dark_manor", "east_dragon": "coral_port", "west_dragon": "deepslate_keep",
    "komainu": "shrine_pavilion", "plant_spirit": "jungle_haven", "shrine": "shrine_pavilion",
    "onmyoji": "shrine_pavilion", "samurai": "deepslate_keep", "wanderer": "bamboo_village", "ninja": "bamboo_village",
    "warrior_monk": "stone_hold", "fan_dancer": "cherry_court", "delinquent": "mud_town", "battle_chef": "mud_town",
    "storm_drummer": "stone_hold", "sword_cultivator": "ivory_palace", "martial_artist": "mud_town",
    "jiangshi": "dark_manor", "phoenix": "desert_court", "knight": "stone_hold", "paladin": "ivory_palace",
    "battle_priest": "ivory_palace", "witch": "dark_manor", "elf": "grove_lodge", "vampire": "dark_manor",
    "demon_lord": "ember_fortress", "angel": "ivory_palace", "reaper": "dark_manor", "necromancer": "deepslate_keep",
    "beast_tamer": "grove_lodge", "dragoon": "stone_hold", "berserker": "grove_lodge", "arbalist": "stone_hold",
    "corsair": "coral_port", "bard": "cherry_court", "alchemist": "tuff_workshop", "siren": "coral_port",
    "idol": "cherry_court", "magical": "ivory_palace", "maid_butler": "ivory_palace", "noble_fencer": "ivory_palace",
    "android": "tuff_workshop", "tactician": "tuff_workshop", "smith": "tuff_workshop", "grappler": "mud_town"}
GROUP_NOUN = {
    "proud_noble": "Court", "delinquent": "Crew", "righteous": "Order", "schemer": "Circle", "genki": "Troupe",
    "stoic": "Watch", "gruff_veteran": "Hold", "battle_maniac": "Warband", "tyrant": "Dominion",
    "sweet_natured": "Hearth", "gentle_giant": "Haven", "ancient_sage": "Conclave", "eccentric_genius": "Academy",
    "trickster": "Revel", "merchant": "Company", "glutton": "Guild", "hot_blooded": "Vanguard",
    "cool_headed": "Bureau", "tsundere": "League", "shy": "Commune", "yandere": "Covenant", "lone_wolf": "Pack",
    "chuunibyou": "Legion", "airhead": "Parade", "big_sibling": "Household", "sleepy": "Lodge",
    "melancholic": "Vigil", "perfectionist": "Academy", "charmer": "Salon", "coward_who_rises": "Fellowship"}
STYLE_TEXT = {
    "katana": ("iron sword", "one-handed long blade: draw, slash strings, quick-draw cuts, sheathe"),
    "greatblade": ("iron sword", "two-handed great sword or long field blade: wide arcs, overheads, slow recoveries"),
    "sword_shield": ("iron sword + shield", "blade and shield: guard, bash, short cuts, shield charge"),
    "twin_blades": ("iron sword", "a pair of short blades (daggers, cleavers, kunai): fast strings, reverse grip"),
    "rapier": ("iron sword", "thrusting blade or whip-sword: lunges, ripostes, flicks"),
    "polearm": ("trident", "spear, glaive, lance, trident: thrusts, sweeps, vaults, throws"),
    "hammer": ("mace", "club, mallet, maul, pan: overheads, ground slams, wind-up swings"),
    "axe": ("iron axe", "hand axes, greataxe, cleaver, spade: chops, throws, spins"),
    "scythe": ("iron axe", "scythe, sickle, chain sickle: reaping sweeps, hooks, pulls"),
    "fists": ("iron sword, drawn as fists", "gauntlets, claws, bare hands, kicks: combos, grabs, throws"),
    "bow": ("bow", "long or short bow: draw, volley, kneeling aim"),
    "crossbow": ("crossbow", "hand, heavy or repeating crossbow and gun-like props: aim, fire, reload"),
    "staff": ("thrown projectile + staff prop", "two-handed staff, wand-staff, broom: casting, spins, bonks"),
    "focus": ("thrown projectile + held prop", "one-handed caster prop (talismans, lantern, tome, mirror, flasks)"),
    "fan": ("thrown projectile + fan prop", "war or dance fans, ribbons: dance steps, gusts, cuts"),
    "instrument": ("thrown projectile + instrument prop", "drum, lute, harp, horn, flute, microphone: beats, chords"),
}
DUEL_TEXT = {
    "rushdown": "never stops coming: short strings and dashes; punish after a whiffed ender",
    "zoner": "keeps you out with projectiles; close the gap to win",
    "grappler": "grabs, throws and slams; stay out of reach or punish a missed grab",
    "counter": "patient; guard stances answer melee; bait the stance, hit after the swing",
    "trapper": "fills the arena with zones, marks and tethers; strike between set-ups",
    "all_rounder": "a projectile, a rush and an anti-air; no single weakness",
    "stance": "switches movesets (stance or transformation); punish the switch",
    "summoner": "fights through allies or summons; kill them or dive the summoner",
}
RARITY_CODE = {"legendary": "L", "epic": "E", "rare": "R", "common": "C"}


def talent_text(t):
    if t.startswith("aura_"):
        return f"aura ({t[5:]})"
    return t


def esc(s):
    return s.replace("|", "/")


def table(rows):
    out = ["| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament "
           "| Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |",
           "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        mark = " **[S]**" if r["slice"] else ""
        out.append(
            f"| {r['n']} | {esc(r['name'])}{mark} | {r['g']} | {r['arch']} | {r['role']} | {r['style']}: "
            f"{esc(r['prop'])} | {r['elem']} | {r['temp']} | {talent_text(r['talent'])}, {r['duty']} | "
            f"{r['duel']} | {RARITY_CODE[r['rarity']]} | {r['batch'] or 'S'} | {esc(r['sig'])} | {esc(r['look'])} | "
            f"{GROUP_NOUN[r['temp']]} · {BASE_STYLE[r['arch']]} |")
    return "\n".join(out)


def count_table(title, counter_by_g, keys):
    out = [f"| {title} | F | M | Total |", "|---|---|---|---|"]
    for k in keys:
        f, m = counter_by_g[k]["F"], counter_by_g[k]["M"]
        out.append(f"| {k} | {f} | {m} | {f + m} |")
    tf = sum(counter_by_g[k]["F"] for k in keys)
    tm = sum(counter_by_g[k]["M"] for k in keys)
    out.append(f"| **total** | **{tf}** | **{tm}** | **{tf + tm}** |")
    return "\n".join(out)


def by_g(rows, key):
    c = collections.defaultdict(collections.Counter)
    for r in rows:
        c[key(r)][r["g"]] += 1
    return c


def main():
    rows = cr.load_rows()
    errs, _ = cr.check(rows, True)
    if errs:
        raise SystemExit("roster check failed; run check_roster.py")
    b = cr.batches(rows)
    for r in rows:
        r["batch"] = 0 if r["slice"] else b[r["n"]]
        r["duty"] = cr.TALENT_DUTY.get(r["talent"], "guard")
    md = []
    w = md.append
    w("# Heroes v2: roster plan (200 original heroes)")
    w("")
    w("Status: plan, 2026-11-12. Companion of `HEROES_V2.md` (the design) and `SLICE_PLAN.md` (the first milestone). "
      "The tables are generated from `roster/roster_data.py` by `roster/gen_roster_md.py`; `roster/check_roster.py` "
      "enforces every count and uniqueness rule below and prints `OK` for this version. `roster/roster.json` is the "
      "machine-readable copy that the hero scaffolder (`herogen new <n>`, HEROES_V2.md section 13.7) reads.")
    w("")
    w("## 1. Rules this roster follows")
    w("")
    w("- **Original characters only.** Every hero is built from generic archetypes (folklore creature types, occupations, "
      "genre roles, personality tropes). No existing character, franchise, game, anime or real person is named, "
      "described or imitated. What makes a hero original is the combination: archetype, personality, element, weapon, "
      "signature mechanic, silhouette, palette and name. Combinations that are the premise of a known work were "
      "avoided while drafting, and every row passes the originality review of section 8 in its batch before it ships.")
    w("- **Adults, tasteful outfits.** Every hero is an adult (petite builds included); outfits are modest, so the mod "
      "suits players of any age. Youthful looks are a style, not an age.")
    w("- **Exactly 100 female and 100 male heroes.** Each archetype has the gender split shown in section 2.3; "
      "archetypes that media usually codes one way have at least one hero of the other gender (shrine priests, a "
      "male fan dancer, butlers, female samurai and grapplers, and so on). Every role is split evenly (section 2.1).")
    w("- **No two heroes share a look and kit.** `check_roster.py` rejects a repeated look key (archetype + palette), "
      "kit key (archetype + weapon style + element), full key (archetype, role, style, element, temperament), "
      "signature line, name or id. At authoring time the format validator adds a second check over the finished kits "
      "(same archetype, element, style, ultimate form and signature move effects: HEROES_V2.md section 14.1).")
    w("- **Balance targets (enforced):** each role 28-30 heroes with a split within one; each element 24-26; each "
      "weapon style 9-17; each temperament 5-9; each of the 35 talents at least twice; every duel style present.")
    w("- **Working names.** Names follow the hero's culture (Japanese-style for yokai and Japanese archetypes, "
      "Chinese-style for wuxia, invented European-style for Western fantasy, mixed for modern ones). They are working "
      "names until the clearance step of section 8 passes for the batch.")
    w("- **The slice heroes are marked [S]**; they are designed in full in `SLICE_PLAN.md`.")
    w("")
    w("## 2. Balance summary (generated)")
    w("")
    w("### 2.1 Combat roles")
    w("")
    w(count_table("Role", by_g(rows, lambda r: r["role"]), cr.ROLES))
    w("")
    w("`controller` (crowd control: slows, tethers, zones, stuns) and `summoner` (fights through summons, adds or "
      "called guards) are the two role presets format 2 adds to v1's five (HEROES_V2.md section 12.4).")
    w("")
    w("### 2.2 Elements, weapon styles, duel styles, rarity")
    w("")
    el = by_g(rows, lambda r: r["elem"])
    w(count_table("Element", el, cr.ELEMENTS))
    w("")
    st = by_g(rows, lambda r: r["style"])
    w(count_table("Weapon style", st, cr.STYLES))
    w("")
    du = by_g(rows, lambda r: r["duel"])
    w(count_table("Duel style", du, cr.DUELS))
    w("")
    ra = by_g(rows, lambda r: r["rarity"])
    w(count_table("Rarity", ra, ["legendary", "epic", "rare", "common"]))
    w("")
    w("Rarity sets the draw weight (leaders 4/4/2/1 for common/rare/epic/legendary, tokens 8/4/2/1) and the boss "
      "phase count (common and rare: 2 phases, the third unlocks at level 15; epic and legendary: 3 phases; only "
      "legendary heroes may use `rebirth`). It does not change raw numbers beyond the boss `health_scale` default "
      "(HEROES_V2.md section 6.2). All eight slice heroes are epic so the slice exercises three phases.")
    w("")
    w("### 2.3 Archetypes (54) and their gender split")
    w("")
    ar = by_g(rows, lambda r: r["arch"])
    out = ["| Group | Archetype | F | M | Default base style (leader) |", "|---|---|---|---|---|"]
    for a, (g, f, m) in cr.ARCHETYPES.items():
        out.append(f"| {g} {GROUPS[g]} | {a} | {ar[a]['F']} | {ar[a]['M']} | {BASE_STYLE[a]} |")
    w("\n".join(out))
    w("")
    w("### 2.4 Temperaments (30) and talents (35)")
    w("")
    te = by_g(rows, lambda r: r["temp"])
    w(count_table("Temperament", te, cr.TEMPERAMENTS))
    w("")
    ta = by_g(rows, lambda r: cr.talent_base(r["talent"]))
    w(count_table("Talent (primary specialty)", ta, cr.TALENTS))
    w("")
    w("Each hero's primary specialty is listed; the finished hero file adds one or two more talents (at most 3, "
      "HEROES_V2.md section 7.2). `alchemist` and `engineer` stay inactive until the brewing and rail jobs exist "
      "(ROADMAP phases 2 and 3); the heroes that carry them get a working second talent.")
    w("")
    w("## 3. Column legend")
    w("")
    w("**Weapon styles.** The style picks the shared animation library (HEROES_V2.md section 13.6) and the mechanical "
      "vanilla item that keeps damage, enchantments, shields and armour on vanilla rules. The prop is what the model "
      "draws.")
    w("")
    out = ["| Style | Mechanical item | Animation family |", "|---|---|---|"]
    for s in cr.STYLES:
        mech, text = STYLE_TEXT[s]
        out.append(f"| `{s}` | {mech} | {text} |")
    w("\n".join(out))
    w("")
    w("**Duel styles** (`behaviour.duel_style`, how a one-on-one fight with a player feels; HEROES_V2.md section 12.3):")
    w("")
    for d in cr.DUELS:
        w(f"- `{d}`: {DUEL_TEXT[d]}.")
    w("")
    w("**Specialty** is the hero's primary non-combat talent (HEROES_V2.md section 7.2), then the duty it works when it "
      "is not leading (`court` replaces the duty while it leads). **R** is the rarity (L, E, R, C). **B** is the "
      "content batch (S = the slice, 1-8 = section 6). **Leader seed** is the faction group noun its temperament "
      "suggests and the base style family its archetype suggests; the hero file's `leader` block has the final "
      "faction names, motto, colours and banner (HEROES_V2.md section 5.3).")
    w("")
    w("## 4. The vertical slice (8 heroes, 4 female and 4 male)")
    w("")
    sl = [r for r in rows if r["slice"]]
    out = ["| # | Name | G | Archetype | Role | Style | Element | Temperament | Faction it leads (SLICE_PLAN.md) |",
           "|---|---|---|---|---|---|---|---|---|"]
    fac = {1: "Bellwhisker Harbour", 6: "Veiled Lantern Exchange", 22: "Crimson Anvil Warband",
           56: "Hearth of the White Bell", 65: "House of the Still Crane", 74: "Quiet Ash Clan",
           108: "Order of the Rime Vow", 121: "Nettlewick Conclave"}
    for r in sl:
        out.append(f"| {r['n']} | {r['name']} | {r['g']} | {r['arch']} | {r['role']} | {r['style']} | {r['elem']} | "
                   f"{r['temp']} | {fac[r['n']]} |")
    w("\n".join(out))
    w("")
    w("They cover all eight elements, five of the eight duel styles (rushdown, zoner, grappler, counter, trapper), six of the seven roles (two vanguards and two "
      "strikers in different duel styles; the summoner role is first exercised by batch 1), eight different "
      "temperaments chosen for contrast as leaders, and seven weapon styles (fists, focus, hammer, katana, "
      "twin_blades, sword_shield, staff).")
    w("")
    w("## 5. The 200 heroes")
    w("")
    for g, title in GROUPS.items():
        grp = [r for r in rows if cr.ARCHETYPES[r["arch"]][0] == g]
        f = sum(1 for r in grp if r["g"] == "F")
        w(f"### 5.{list(GROUPS).index(g) + 1} {title} ({len(grp)} heroes: {f} F, {len(grp) - f} M)")
        w("")
        w(table(grp))
        w("")
    w("## 6. Content batches (after the slice)")
    w("")
    w("The 192 remaining heroes ship in 8 batches of 24 (12 female, 12 male). Batches are drawn by striding through "
      "the roster, so each batch mixes archetype groups, roles and elements and gives the reviewer a varied contact "
      "sheet. Every batch ends with the review gates of HEROES_V2.md section 14.4.")
    w("")
    out = ["| Batch | Heroes (#) | Roles | Elements |", "|---|---|---|---|"]
    for k in range(1, 9):
        br = [r for r in rows if r["batch"] == k]
        roles = collections.Counter(r["role"] for r in br)
        els = collections.Counter(r["elem"] for r in br)
        out.append(f"| {k} | {', '.join(str(r['n']) for r in br)} | "
                   f"{', '.join(f'{x} {roles[x]}' for x in cr.ROLES if roles[x])} | "
                   f"{', '.join(f'{x} {els[x]}' for x in cr.ELEMENTS if els[x])} |")
    w("\n".join(out))
    w("")
    w("## 7. From a row to a finished hero")
    w("")
    w("1. `herogen new <n>` scaffolds `herosrc/heroes/<id>.hero.json` from the row, the archetype template "
      "(`herosrc/archetypes/<archetype>.json`: default parts, ears and tails, base style, banner emblem, naming motifs, "
      "kit patterns) and the temperament template (`herosrc/temperaments/<temperament>.json`: trait defaults, fight-AI "
      "defaults, expression overrides, fallback lines).")
    w("2. The author (an agent, one batch task per 4 heroes) turns the row's signature into a kit: 4-6 moves over 2-3 "
      "phases with one new mechanic per phase, a signature tell (pose, sound, particle colour), a punish window, at "
      "least one weakness, reactions to blocking, bows and eating; then the art block (parts, palette, face, weapon, "
      "phase looks), the signature animation recipes, personality traits, the leader block (two faction names, short "
      "name, motto, colours, banner, base style, culture, economy focus), 2-3 talents and about 50 dialogue lines.")
    w("3. `herogen build` generates the model, texture, animations and server data; `herogen check` and "
      "`./gradlew checkHeroes` validate them (HEROES_V2.md section 14).")
    w("4. `herogen sheet` renders the batch contact sheet; the in-game lineup screenshots follow (section 14.4 there).")
    w("")
    w("## 8. Name and originality clearance (per batch, before shipping)")
    w("")
    w("1. **Name search**: each full name, and each faction name the hero brings, is searched on the web together with "
      "its archetype words. A hit on a character, a real person (streamers' personas included) or a published "
      "group with a similar concept replaces the name. Common given names are acceptable when the full name and the "
      "concept are not a match.")
    w("2. **Concept search**: the archetype + signature + palette + weapon combination is searched; a match with a "
      "known character changes at least two of palette, weapon, signature or silhouette.")
    w("3. **Visual review**: the reviewer (a separate agent) compares the contact sheet against the rule above and "
      "lists any hero that reads as a known character; the owner's screenshot review is the final gate.")
    w("4. Results are recorded in `herosrc/clearance/<batch>.md` (name, queries, verdict), so later batches do not "
      "reuse a rejected name.")
    w("5. **What a search is looking for** (from the first desk review, `CRITIQUE.md`): an invented given name or "
      "surname shared with a known character of the same archetype or gender-swapped twin (a female ninja surnamed "
      "like a known ninja retainer, a magical girl named like a magical-girl series lead, an elf sharing a rare "
      "invented elf name); a surname that is a real performer's stage name in the same art (kabuki, dance schools); "
      "and a look that repeats a famous design's two or three defining marks (hair style, outfit colours and the "
      "signature prop together). That review already renamed rows 5, 75, 82, 84, 86, 129, 132, 169, 178 and 180 and "
      "changed the look of rows 46, 96, 98, 188 and 189; the web searches of steps 1-2 are still due for every batch.")
    w("")
    w("## 9. Leaders")
    w("")
    w("Every hero is authored as leader-eligible: it carries a full `leader` block, so any of the 200 can found a "
      "faction. The leader draft (HEROES_V2.md section 5.2) picks distinct archetypes, distinct colours, both genders "
      "and a mix of aggressive and peaceful temperaments. Examples of contrasting four-leader casts this roster "
      "supports: battle maniac oni (22) + schemer fox (6) + sweet-natured shrine maiden (56) + stoic samurai (65) "
      "(the slice's campaign run A); tsundere cat (1) + righteous knight (108) + eccentric warlock (121) + lone-wolf "
      "ninja (74) (run B); tyrant demon queen (135) + gentle-giant oni (25) + merchant dragon (41) + yandere vampire "
      "(131).")
    w("")
    path = os.path.join(HERE, "..", "..", "docs", "heroes-v2", "ROSTER_PLAN.md")
    with open(path, "w") as f:
        f.write("\n".join(md) + "\n")
    print("wrote", os.path.normpath(path), len(md), "blocks")


if __name__ == "__main__":
    main()
