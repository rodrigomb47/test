#!/usr/bin/env python3
"""Cross-check cases for the Java hero checker (HEROES_V2 15.2 F1).

Writes mutated copies of the three v1 example heroes (docs/heroes/examples) into
src/test/resources/heroes/crosscheck/<case>/<id>.json, runs this folder's reference checker (herotool.py) on each,
and records what it reports in src/test/resources/heroes/crosscheck/expected.tsv (case, outcome, path). The Java unit
test HeroCrossCheckTest reads the same files with HeroReader and must report the same outcomes at the same places.

  python3 tools/herotool/crosscheck.py          (needs the jsonschema package, as herotool.py does)

Each case breaks one rule the reference checker knows, and keeps the rest of the hero valid, so its semantic checks
run (a schema error stops herotool.py before them). The cases avoid the few places where the two checkers knowingly
differ (see HeroCrossCheckTest's comment).
"""
import copy
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import herotool  # noqa: E402

EXAMPLES = os.path.join(ROOT, "docs", "heroes", "examples")
OUT = os.path.join(ROOT, "src", "test", "resources", "heroes", "crosscheck")
DEL = object()


def load(name):
    with open(os.path.join(EXAMPLES, name + ".json"), encoding="utf-8") as fh:
        return json.load(fh)


def at(obj, path):
    for p in path[:-1]:
        obj = obj[p]
    return obj, path[-1]


def put(hero, path, value):
    o, k = at(hero, path)
    if value is DEL:
        del o[k]
    elif isinstance(o, list) and k == len(o):
        o.append(value)
    else:
        o[k] = value


# case name -> (example, [(path, value)])
CASES = {
    "unchanged_bram": ("bram_ostler", []),
    "unchanged_sela": ("sela_quickfern", []),
    "unchanged_maudie": ("maudie_brine", []),
    "cooldown_floor": ("bram_ostler", [(["skills", 0, "cooldown"], 2)]),
    "empower_no_state": ("bram_ostler", [(["empowered"], DEL)]),
    "potion_caps": ("sela_quickfern", [(["skills", 0, "effects", 1],
                                        {"type": "effect", "id": "weakness", "level": 3, "seconds": 3}),
                                       (["skills", 2, "effects", 1],
                                        {"type": "effect", "id": "speed", "level": 2, "seconds": 25})]),
    "invisibility_to_allies": ("maudie_brine", [(["skills", 1, "effects", 1],
                                                 {"type": "effect", "id": "invisibility", "seconds": 5})]),
    "stat_cap": ("bram_ostler", [(["skills", 2, "effects", 0, "percent"], -50)]),
    "stat_zero": ("bram_ostler", [(["skills", 2, "effects", 0, "percent"], 0)]),
    "summon_count": ("sela_quickfern", [(["skills", 2, "effects", 2],
                                         {"type": "summon", "creature": "iron_golem", "count": 2})]),
    "radius_unused": ("bram_ostler", [(["skills", 0, "effects", 0, "radius"], 4)]),
    "summon_in_passive": ("maudie_brine", [(["passives", 1, "effects"],
                                            [{"type": "summon", "creature": "wolf"},
                                             {"type": "energy", "amount": 10}])]),
    "out_of_combat": ("bram_ostler", [(["skills", 0, "use_when"], "out_of_combat")]),
    "fields_unused": ("sela_quickfern", [(["skills", 0, "hits"], 2)]),
    "projectile_unused": ("bram_ostler", [(["skills", 0, "projectile"], "arrow")]),
    "three_damage": ("bram_ostler", [(["skills", 0, "effects", 3], {"type": "damage", "amount": 1}),
                                     (["skills", 0, "effects", 4], {"type": "damage", "amount": 1})]),
    "two_moves": ("sela_quickfern", [(["skills", 2, "effects", 2], {"type": "move_self", "direction": "up"})]),
    "enemy_in_friendly_form": ("maudie_brine", [(["skills", 0, "effects", 1], {"type": "damage", "amount": 2})]),
    "enemy_to_self": ("bram_ostler", [(["skills", 0, "effects", 3], {"type": "knockback", "to": "self"})]),
    "ally_in_enemy_form": ("sela_quickfern", [(["skills", 0, "effects", 3], {"type": "heal", "amount": 2})]),
    "mark_in_allies_form": ("maudie_brine", [(["skills", 1, "effects", 2], {"type": "mark", "mark": "soaked"})]),
    "slot_cap": ("bram_ostler", [(["skills", 0, "effects", 0, "amount"], 30)]),
    "every_floor": ("maudie_brine", [(["passives", 0, "trigger_value"], 3),
                                     (["passives", 0, "effects", 0, "amount"], 6)]),
    "passive_floor": ("sela_quickfern", [(["passives", 0, "cooldown"], 1)]),
    "trigger_form": ("bram_ostler", [(["passives", 0, "form"], "allies")]),
    "always_seconds": ("bram_ostler", [(["passives", 1, "effects", 0, "seconds"], 5),
                                       (["passives", 1, "effects", 1], {"type": "stat", "stat": "armor",
                                                                        "percent": 10})]),
    "always_cap": ("bram_ostler", [(["passives", 1, "effects", 0, "percent"], -25)]),
    "energy_on_ultimate": ("maudie_brine", [(["passives", 1, "trigger"], "on_ultimate"),
                                            (["passives", 1, "effects", 1],
                                             {"type": "stat", "stat": "speed", "percent": 10, "seconds": 5})]),
    "thrown_rate": ("maudie_brine", [(["basic_attack", "amount"], 6), (["basic_attack", "interval"], 0.8)]),
    "thrown_only_fields": ("bram_ostler", [(["basic_attack", "amount"], 4), (["basic_attack", "interval"], 2)]),
    "shared_on_hit": ("bram_ostler", [(["basic_attack", "on_hit", 1], {"type": "damage", "amount": 3}),
                                      (["empowered", "on_hit", 1], {"type": "damage", "amount": 3})]),
    "size_note": ("bram_ostler", [(["stats", "size"], 110)]),
    "placeholder": ("bram_ostler", [(["lines", "kill", 1], "Bye, {target}.")]),
    "enchant_level": ("bram_ostler", [(["gear", 0, "enchantments", "knockback"], 2)]),
    "kit_enchant_total": ("sela_quickfern", [(["gear", 1], {"item": "leather_helmet", "enchantments":
                                                            {"protection": 2, "respiration": 1}}),
                                             (["gear", 2], {"item": "leather_chestplate", "enchantments":
                                                            {"protection": 2}})]),
    "mark_never_applied": ("bram_ostler", [(["skills", 0, "effects", 1, "if", "mark"], "tolls")]),
    "schema_range": ("bram_ostler", [(["stats", "health"], 50)]),
    "schema_required": ("bram_ostler", [(["name"], DEL)]),
}


def outcome(o, msg):
    if o != "SCHEMA":
        return o
    if "is a required property" in msg:
        return "NOT LOADED"
    if "greater than the maximum" in msg or "less than the minimum" in msg:
        return "ADJUSTED"
    return "SCHEMA"


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    lines = []
    for case, (example, edits) in CASES.items():
        hero = copy.deepcopy(load(example))
        for path, value in edits:
            put(hero, path, value)
        d = os.path.join(OUT, case)
        os.makedirs(d)
        f = os.path.join(d, example + ".json")
        with open(f, "w", encoding="utf-8") as fh:
            json.dump(hero, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        rep = herotool.check(f)
        items = [(outcome(o, m), p.replace("/", ".").replace(" + empowered.on_hit (shared cap)", "").replace(
            "basic_attack.on_hit", "empowered")) for o, p, m in rep.items
                 if o != "INFO" and not (o == "NOTE" and p == "id")]
        if not items:
            lines.append(f"{case}\t-\t-")
        for o, p in items:
            lines.append(f"{case}\t{o}\t{p}".rstrip("\t"))  # a problem of the hero as a whole has no path
    with open(os.path.join(OUT, "expected.tsv"), "w", encoding="utf-8") as fh:
        fh.write("# written by tools/herotool/crosscheck.py from herotool.py's report: case, outcome, path\n")
        fh.write("\n".join(lines) + "\n")
    print(f"{len(CASES)} cases, {len(lines)} lines -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
