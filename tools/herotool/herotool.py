#!/usr/bin/env python3
"""Hero format v1 tool: the catalogue (single source), the JSON Schema generator and a semantic checker.

  python3 herotool.py schema <out.json> [<out.compact.json>]
  python3 herotool.py check <hero.json>...

The checker mirrors HERO_FORMAT.md: schema validation, then the loader's sense rules (form/effect compatibility,
slot caps at the level-1 reference, cooldown floors, potion and stat caps, placeholders, trigger/form rules).
"""
import json
import math
import re
import sys

# ------------------------------------------------------------------ catalogue
FACTIONS = ["ember_legion", "grove_covenant", "tide_alliance", "stoneguard"]
ELEMENTS = ["physical", "fire", "frost", "shock", "venom", "blight", "arcane", "radiant"]
RARITIES = ["common", "rare", "epic", "legendary"]
ROLES = ["vanguard", "striker", "skirmisher", "marksman", "support"]
GROWTH = ["balanced", "tough", "fierce", "swift", "mystic"]
FOCUS = ["nearest", "weakest", "strongest", "ranged_first", "threat_to_allies", "heroes_first"]
AGGRESSION = ["cautious", "steady", "bold"]
PROTECTS = ["none", "allies", "workers", "players", "heart"]
RAIDS = ["leads", "joins", "stays_home"]
WORKER_ROLES = ["builder", "miner", "farmer", "woodcutter", "blacksmith", "chef", "smelter", "animal_carer", "butcher",
                "fisherman", "cave_explorer", "hauler", "enchanter", "trader"]
DUTIES = ["guard", "scout"] + WORKER_ROLES
ALL_ROLE_IDS = WORKER_ROLES + ["scout", "swordsman", "archer", "crossbowman", "axe_fighter"]
STYLES = ["melee", "bow", "crossbow", "trident", "thrown"]
ITEM_LOOKS = ["snowball", "fire_charge", "wind_charge", "slime_ball", "magma_cream", "amethyst_shard",
              "prismarine_shard", "prismarine_crystals", "echo_shard", "blaze_powder", "feather", "bone", "flint",
              "heart_of_the_sea", "nautilus_shell"]
PROJECTILES = ["arrow", "spectral_arrow", "trident"] + ITEM_LOOKS
HELD_PROPS = ["stick", "blaze_rod", "breeze_rod", "book", "amethyst_shard", "echo_shard", "prismarine_shard",
              "heart_of_the_sea", "nautilus_shell", "feather", "bone", "spyglass", "compass", "clock", "lantern",
              "soul_lantern", "poppy", "cornflower"]
FORMS = ["melee", "shot", "volley", "bolt", "chain", "cone", "line", "nova", "zone", "leap", "dash", "self", "ally",
         "allies"]
FRIENDLY_FORMS = {"self", "ally", "allies"}
ZONE_AT = ["target", "self", "follow_self"]
USE_WHEN = ["always", "self_health_below", "ally_health_below", "target_health_below", "enemies_near", "target_far",
            "target_close", "big_fight", "out_of_combat"]
USE_VALUE = {"self_health_below": (10, 90, 50), "ally_health_below": (10, 90, 60), "target_health_below": (10, 90, 30),
             "enemies_near": (2, 6, 2), "target_far": (4, 16, 6), "target_close": (2, 6, 3)}
TO = ["targets", "self", "allies", "ally"]
CONDITIONS = ["target_health_below", "target_health_above", "self_health_below", "self_health_above",
              "target_has_mark", "self_has_mark", "target_burning", "target_freezing", "target_slowed",
              "target_stunned", "target_has_dot", "target_is", "self_empowered", "self_shielded"]
KINDS = ["monster", "undead", "arthropod", "illager", "player", "npc", "hero", "boss"]
EFFECT_TYPES = ["damage", "dot", "heal", "shield", "effect", "stat", "knockback", "pull", "ignite", "freeze", "stun",
                "taunt", "cleanse", "dispel", "energy", "cooldown_cut", "move_self", "mark", "clear_marks",
                "empower", "summon"]
ENEMY_ONLY = {"damage", "dot", "knockback", "pull", "ignite", "freeze", "stun", "taunt", "dispel"}
ALLY_ONLY = {"heal", "shield", "cleanse", "energy"}
SELF_ONLY = {"cooldown_cut", "move_self", "empower", "summon"}
STATS = ["attack", "damage_dealt", "damage_taken", "armor", "speed", "attack_speed", "healing_received",
         "knockback_resistance", "crit_damage", "energy_gain"]
# stat: (min, max); helpful when the sign is "good" for the receiver
STAT_CAPS = {"attack": (-30, 40), "damage_dealt": (-30, 40), "damage_taken": (-40, 30), "armor": (-50, 50),
             "speed": (-40, 40), "attack_speed": (-30, 30), "healing_received": (-50, 50),
             "knockback_resistance": (0, 60), "crit_damage": (0, 50), "energy_gain": (0, 50)}
HELPFUL_POTIONS = {"speed": (2, 20), "haste": (2, 30), "strength": (2, 12), "regeneration": (2, 10),
                   "resistance": (2, 10), "fire_resistance": (1, 30), "water_breathing": (1, 30),
                   "jump_boost": (2, 20), "slow_falling": (1, 10), "invisibility": (1, 6)}
HARMFUL_POTIONS = {"slowness": (3, 8), "weakness": (2, 8), "mining_fatigue": (2, 10), "poison": (2, 8),
                   "wither": (2, 6), "glowing": (1, 15), "levitation": (1, 3), "blindness": (1, 5),
                   "darkness": (1, 6)}
POSES = ["none", "swing", "block", "bow_draw", "spear_throw", "crossbow_charge", "horn", "spyglass"]
SUMMONS = {"wolf": (3, 1.0), "snow_golem": (2, 0.5), "iron_golem": (1, 3.0)}
TRIGGERS = ["always", "on_hit", "on_crit", "on_hurt", "on_kill", "on_skill", "on_ultimate", "on_fight_start",
            "on_low_health", "on_ally_low_health", "on_ally_hurt", "on_ally_attack", "on_ally_ultimate",
            "on_mark_full", "every"]
PASSIVE_FORMS = ["self", "trigger_enemy", "trigger_ally", "ally", "allies", "nova", "bolt", "shot"]
TRIGGER_FORMS = {
    "always": ["self"],
    "on_hit": ["trigger_enemy", "self", "nova", "allies", "bolt", "shot"],
    "on_crit": ["trigger_enemy", "self", "nova", "allies", "bolt", "shot"],
    "on_hurt": ["trigger_enemy", "self", "nova", "allies", "bolt", "shot"],
    "on_kill": ["self", "nova", "allies"],
    "on_skill": ["self", "ally", "allies", "nova"],
    "on_ultimate": ["self", "allies", "nova"],
    "on_fight_start": ["self", "allies", "nova"],
    "on_low_health": ["self", "allies", "nova"],
    "on_ally_low_health": ["trigger_ally", "allies", "self"],
    "on_ally_hurt": ["trigger_ally", "trigger_enemy", "self", "bolt", "shot"],
    "on_ally_attack": ["trigger_enemy", "bolt", "shot"],
    "on_ally_ultimate": ["self", "allies", "trigger_ally"],
    "on_mark_full": ["trigger_enemy", "self", "nova"],
    "every": ["self", "ally", "allies", "nova"],
}
TRIGGER_DEFAULT_FORM = {"always": "self", "on_hit": "trigger_enemy", "on_crit": "trigger_enemy",
                        "on_hurt": "trigger_enemy", "on_kill": "self", "on_skill": "self", "on_ultimate": "self",
                        "on_fight_start": "self", "on_low_health": "self", "on_ally_low_health": "trigger_ally",
                        "on_ally_hurt": "trigger_ally", "on_ally_attack": "trigger_enemy",
                        "on_ally_ultimate": "self", "on_mark_full": "trigger_enemy", "every": "self"}
TRIGGER_VALUE = {"on_low_health": (10, 60, 35), "on_ally_low_health": (10, 60, 35), "every": (3, 30, 10)}
AFFIXES = ["diligence", "prospector", "regrowth", "green_thumb", "mason", "marksman", "thrifty", "hardy", "duelist",
           "angler", "haggler"]
BASE_TYPES = ["aura", "mend", "lookout", "rally", "drill"]
EVENTS = ["summoned", "greeting", "idle", "fight_start", "skill", "ultimate", "low_health", "kill", "ally_fallen",
          "victory", "raid_start", "base_attacked", "level_up", "unlock", "fallen", "revived"]
PLACEHOLDERS = {"summoned": ["name", "faction"], "greeting": ["name", "player", "faction"],
                "idle": ["name", "player", "faction"], "fight_start": ["name", "enemy"],
                "skill": ["name", "enemy", "ability"], "ultimate": ["name", "enemy", "ability"],
                "low_health": ["name"], "kill": ["name", "enemy"], "ally_fallen": ["name", "ally"],
                "victory": ["name"], "raid_start": ["name", "faction"], "base_attacked": ["name", "faction"],
                "level_up": ["name", "level"], "unlock": ["name", "level", "ability"], "fallen": ["name"],
                "revived": ["name"]}
PARTICLES = ["angry_villager", "ash", "bubble_pop", "cherry_leaves", "cloud", "crimson_spore", "crit",
             "damage_indicator", "dragon_breath", "dust", "electric_spark", "enchant", "enchanted_hit", "end_rod",
             "explosion", "firework", "flame", "glow", "gust", "happy_villager", "heart", "large_smoke", "nautilus",
             "note", "poof", "portal", "reverse_portal", "scrape", "sculk_soul", "small_flame", "small_gust", "smoke",
             "snowflake", "sonic_boom", "soul", "soul_fire_flame", "spore_blossom_air", "splash", "sweep_attack",
             "totem_of_undying", "trial_spawner_detection", "warped_spore", "wax_on", "white_ash", "witch"]
SOUNDS = ["block.amethyst_block.chime", "block.amethyst_cluster.break", "block.anvil.land", "block.beacon.activate",
          "block.beacon.power_select", "block.bell.use", "block.conduit.activate", "block.enchantment_table.use",
          "block.fire.ambient", "block.glass.break", "block.note_block.bell", "block.respawn_anchor.charge",
          "block.respawn_anchor.deplete", "block.sculk_shrieker.shriek", "entity.arrow.shoot", "entity.blaze.ambient",
          "entity.blaze.shoot", "entity.breeze.shoot", "entity.breeze.wind_burst", "entity.dolphin.splash",
          "entity.elder_guardian.curse", "entity.ender_dragon.growl", "entity.evoker.cast_spell",
          "entity.evoker.prepare_attack", "entity.evoker.prepare_summon", "entity.experience_orb.pickup",
          "entity.generic.explode", "entity.ghast.shoot", "entity.glow_squid.squirt", "entity.goat.ram_impact",
          "entity.illusioner.cast_spell", "entity.illusioner.mirror_move", "entity.illusioner.prepare_blindness",
          "entity.iron_golem.attack", "entity.lightning_bolt.thunder", "entity.player.attack.crit",
          "entity.player.attack.knockback", "entity.player.attack.strong", "entity.player.attack.sweep",
          "entity.player.hurt_freeze", "entity.player.levelup", "entity.player.splash", "entity.ravager.roar",
          "entity.snowball.throw", "entity.vex.charge", "entity.warden.sonic_boom", "entity.wind_charge.wind_burst",
          "entity.wither.shoot", "entity.wolf.howl", "event.raid.horn", "item.book.page_turn", "item.crossbow.shoot",
          "item.firecharge.use", "item.flintandsteel.use", "item.mace.smash_ground", "item.shield.block",
          "item.totem.use", "item.trident.hit", "item.trident.riptide_1", "item.trident.throw", "item.trident.thunder"]
GEAR = ["wooden_sword", "stone_sword", "iron_sword", "golden_sword", "wooden_axe", "stone_axe", "iron_axe",
        "golden_axe", "mace", "bow", "crossbow", "trident", "shield", "turtle_helmet"] + [
    f"{m}_{p}" for m in ["leather", "chainmail", "iron", "golden"] for p in ["helmet", "chestplate", "leggings", "boots"]]
ENCHANTS = {"sharpness": 2, "smite": 3, "bane_of_arthropods": 3, "knockback": 1, "fire_aspect": 1, "sweeping_edge": 2,
            "density": 2, "breach": 1, "power": 2, "punch": 1, "flame": 1, "quick_charge": 2, "piercing": 2,
            "multishot": 1, "impaling": 2, "loyalty": 1, "protection": 2, "fire_protection": 2, "blast_protection": 2,
            "projectile_protection": 2, "thorns": 1, "feather_falling": 2, "respiration": 1, "aqua_affinity": 1,
            "depth_strider": 2, "unbreaking": 3}

ROLE_DEFAULTS = {
    "vanguard": dict(health=34, attack=1, armor=3, speed=95, knockback_resistance=30, growth="tough",
                     focus="threat_to_allies", keep_distance=0, protects="allies", aggression="steady",
                     retreat_below=20, raids="leads"),
    "striker": dict(health=28, attack=2, armor=1, speed=100, knockback_resistance=10, growth="fierce",
                    focus="weakest", keep_distance=0, protects="none", aggression="bold", retreat_below=25,
                    raids="leads"),
    "skirmisher": dict(health=26, attack=2, armor=0, speed=108, knockback_resistance=0, growth="swift",
                       focus="weakest", keep_distance=4, protects="none", aggression="bold", retreat_below=30,
                       raids="joins"),
    "marksman": dict(health=24, attack=0, armor=1, speed=100, knockback_resistance=0, growth="balanced",
                     focus="weakest", keep_distance=10, protects="none", aggression="cautious", retreat_below=40,
                     raids="joins"),
    "support": dict(health=26, attack=0, armor=1, speed=100, knockback_resistance=0, growth="mystic",
                    focus="threat_to_allies", keep_distance=8, protects="allies", aggression="cautious",
                    retreat_below=40, raids="joins"),
}
STYLE_POWER = {"melee": 6, "bow": 6, "crossbow": 7, "trident": 8}

# slot caps in level-1 reference health points: damage, dot (amount x seconds), heal, shield, stun seconds
CAPS = {"on_hit": dict(damage=4, dot=4, heal=2, shield=2, stun=0),
        "nth_hit": dict(damage=8, dot=8, heal=4, shield=4, stun=1),
        "skill": dict(damage=16, dot=16, heal=8, shield=8, stun=2),
        "ultimate": dict(damage=30, dot=24, heal=14, shield=12, stun=3),
        "passive": dict(damage=8, dot=8, heal=4, shield=4, stun=1)}

# ------------------------------------------------------------------ schema


def mcid(names):
    return {"type": "string", "pattern": "^(minecraft:)?(" + "|".join(re.escape(n) for n in names) + ")$"}


def text(lo, hi):
    return {"type": "string", "minLength": lo, "maxLength": hi}


def num(lo, hi):
    return {"type": "number", "minimum": lo, "maximum": hi}


def intg(lo, hi):
    return {"type": "integer", "minimum": lo, "maximum": hi}


def obj(props, required=None, **extra):
    o = {"type": "object"}
    if required:
        o["required"] = required
    o["properties"] = props
    o["patternProperties"] = {"^_": {}}
    o["additionalProperties"] = False
    o.update(extra)
    return o


def ref(name):
    return {"$ref": "#/$defs/" + name}


def typed_union(types, spec, common_props, common_names):
    """An object whose "type" picks the allowed fields: all names are declared (so additionalProperties catches
    typos), each type's if/then adds its required fields, its value rules and the list of names it accepts."""
    names = list(common_names)
    for props, _ in spec.values():
        for k in props:
            if k not in names:
                names.append(k)
    props = {"type": {"enum": types}}
    props.update(common_props)
    for k in names:
        props.setdefault(k, {})
    branches = []
    for t in types:
        own, req = spec[t]
        then = {"properties": own,
                "propertyNames": {"anyOf": [{"enum": list(common_names) + list(own)}, {"pattern": "^_"}]}}
        if req:
            then["required"] = req
        branches.append({"if": {"required": ["type"], "properties": {"type": {"const": t}}}, "then": then})
    return {"type": "object", "required": ["type"], "properties": props, "patternProperties": {"^_": {}},
            "additionalProperties": False, "allOf": branches}


def build_schema():
    d = {}
    d["id"] = {"type": "string", "pattern": "^[a-z][a-z0-9_]{2,31}$"}
    d["mark_id"] = {"type": "string", "pattern": "^[a-z][a-z0-9_]{1,23}$"}
    d["element"] = {"enum": ELEMENTS}
    d["color"] = {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}$"}
    d["particle"] = mcid(PARTICLES)
    d["sound"] = mcid(SOUNDS)
    d["amount"] = {"oneOf": [
        num(0.5, 30),
        obj({"base": num(0, 30), "attack_percent": intg(0, 300), "max_health_percent": intg(0, 40),
             "armor_percent": intg(0, 100), "target_max_health_percent": intg(0, 20),
             "target_missing_health_percent": intg(0, 30), "mark": ref("mark_id"), "per_mark_stack": num(0, 6)},
            minProperties=1, dependentRequired={"per_mark_stack": ["mark"], "mark": ["per_mark_stack"]})]}
    d["condition"] = {
        "type": "object", "required": ["condition"],
        "properties": {"condition": {"enum": CONDITIONS}, "value": intg(1, 99), "mark": ref("mark_id"),
                       "kind": {"enum": KINDS}},
        "patternProperties": {"^_": {}}, "additionalProperties": False,
        "allOf": [
            {"if": {"required": ["condition"], "properties": {"condition": {"enum": [
                "target_health_below", "target_health_above", "self_health_below", "self_health_above"]}}},
             "then": {"required": ["value"]}},
            {"if": {"required": ["condition"], "properties": {"condition": {"enum": [
                "target_has_mark", "self_has_mark"]}}}, "then": {"required": ["mark"]}},
            {"if": {"required": ["condition"], "properties": {"condition": {"const": "target_is"}}},
             "then": {"required": ["kind"]}}]}
    fx = {
        "damage": ({"amount": ref("amount"), "element": ref("element"), "drain": intg(0, 50)}, ["amount"]),
        "dot": ({"amount": ref("amount"), "seconds": num(2, 10), "element": ref("element"),
                 "max_stacks": intg(1, 5)}, ["amount"]),
        "heal": ({"amount": ref("amount")}, ["amount"]),
        "shield": ({"amount": ref("amount"), "seconds": num(2, 30)}, ["amount"]),
        "effect": ({"id": mcid(list(HELPFUL_POTIONS) + list(HARMFUL_POTIONS)), "level": intg(1, 3),
                    "seconds": num(1, 30)}, ["id", "seconds"]),
        "stat": ({"stat": {"enum": STATS}, "percent": intg(-50, 60), "seconds": num(2, 30),
                  "max_stacks": intg(1, 5)}, ["stat", "percent"]),
        "knockback": ({"strength": num(1, 5), "up": num(0, 3)}, []),
        "pull": ({"strength": num(1, 3)}, []),
        "ignite": ({"seconds": num(1, 8)}, []),
        "freeze": ({"seconds": num(1, 6)}, []),
        "stun": ({"seconds": num(0.5, 2)}, []),
        "taunt": ({"seconds": num(1, 8)}, []),
        "cleanse": ({}, []),
        "dispel": ({}, []),
        "energy": ({"amount": intg(5, 30)}, ["amount"]),
        "cooldown_cut": ({"seconds": num(1, 10)}, ["seconds"]),
        "move_self": ({"direction": {"enum": ["back", "forward", "up"]}, "distance": num(2, 8)}, []),
        "mark": ({"mark": ref("mark_id"), "stacks": intg(1, 5), "max_stacks": intg(1, 10), "seconds": num(2, 30),
                  "glow": {"type": "boolean"}}, ["mark"]),
        "clear_marks": ({"mark": ref("mark_id")}, ["mark"]),
        "empower": ({"seconds": num(3, 20)}, ["seconds"]),
        "summon": ({"creature": {"enum": list(SUMMONS)}, "count": intg(1, 3), "seconds": num(10, 60),
                    "name": text(1, 24)}, ["creature"]),
    }
    assert list(fx) == EFFECT_TYPES
    common = ["type", "to", "radius", "chance", "if"]
    d["effect"] = typed_union(EFFECT_TYPES, fx, {"to": {"enum": TO}, "radius": num(2, 12), "chance": intg(1, 100),
                                                 "if": ref("condition")}, common)
    d["effects"] = {"type": "array", "minItems": 1, "maxItems": 5, "items": ref("effect")}
    d["look"] = obj({"particle": ref("particle"), "color": ref("color"), "sound": ref("sound"),
                     "lightning": {"type": "boolean"}, "fangs": {"type": "boolean"},
                     "pose": {"enum": POSES}})
    core = {
        "name": text(1, 24), "description": text(0, 200), "form": {"enum": FORMS}, "range": num(2, 24),
        "radius": num(0, 12), "count": intg(2, 5), "angle": intg(30, 120), "width": num(1, 3), "hits": intg(1, 5),
        "hit_interval": num(0.2, 1), "projectile": mcid(PROJECTILES), "zone_at": {"enum": ZONE_AT},
        "delay": num(0, 3), "duration": num(0, 10), "interval": num(0.5, 3), "windup": num(0, 2),
        "use_when": {"enum": USE_WHEN}, "use_value": num(2, 90), "unlock_level": intg(1, 30),
        "effects": ref("effects"), "look": ref("look"), "line": text(1, 120)}
    d["skill"] = obj(dict(core, cooldown=num(2, 120), charges=intg(1, 3), energy_gain=intg(0, 30)),
                     ["name", "form", "effects"])
    d["ultimate"] = obj(dict(core, charge_rate=intg(50, 150)), ["name", "form", "effects"])
    d["passive"] = obj({
        "name": text(1, 24), "description": text(0, 200), "trigger": {"enum": TRIGGERS},
        "trigger_value": num(3, 60), "mark": ref("mark_id"), "chance": intg(1, 100), "cooldown": num(1, 120),
        "form": {"enum": PASSIVE_FORMS}, "radius": num(2, 12), "projectile": mcid(PROJECTILES),
        "if": ref("condition"), "effects": {"type": "array", "minItems": 1, "maxItems": 3, "items": ref("effect")},
        "look": ref("look"), "line": text(1, 120), "unlock_level": intg(1, 30)},
        ["name", "trigger", "effects"],
        allOf=[{"if": {"required": ["trigger"], "properties": {"trigger": {"const": "on_mark_full"}}},
                "then": {"required": ["mark"]}}])
    d["stat_bonus"] = obj({"stat": {"enum": STATS}, "percent": intg(-30, 30)}, ["stat", "percent"])
    d["empowered"] = obj({
        "name": text(1, 24), "description": text(0, 200),
        "stats": {"type": "array", "minItems": 1, "maxItems": 4, "items": ref("stat_bonus")},
        "on_hit": {"type": "array", "minItems": 1, "maxItems": 2, "items": ref("effect")},
        "particle": ref("particle"), "color": ref("color"), "size": intg(100, 110),
        "skills_locked": {"type": "boolean"}, "line": text(1, 120)}, ["name"])
    base = {
        "aura": ({"affix": {"enum": AFFIXES}, "stacks": intg(1, 3), "radius": num(8, 32),
                  "roles": {"type": "array", "minItems": 1, "items": {"enum": ALL_ROLE_IDS}, "uniqueItems": True}},
                 ["affix"]),
        "mend": ({"percent": intg(1, 10), "every": num(5, 30), "radius": num(8, 24)}, []),
        "lookout": ({"radius": num(16, 48)}, []),
        "rally": ({"percent": intg(5, 25)}, []),
        "drill": ({"xp": intg(1, 3), "radius": num(8, 24)}, []),
    }
    d["base_ability"] = typed_union(BASE_TYPES, base, {"name": text(1, 24), "unlock_level": intg(1, 30)},
                                    ["type", "name", "unlock_level"])
    d["gear_id"] = mcid(GEAR)
    d["gear_item"] = {"oneOf": [
        ref("gear_id"),
        obj({"item": ref("gear_id"), "name": text(1, 24),
             "enchantments": {"type": "object", "propertyNames": mcid(list(ENCHANTS)),
                              "additionalProperties": intg(1, 3), "maxProperties": 3}}, ["item"])]}
    d["line_list"] = {"type": "array", "minItems": 1, "maxItems": 8, "items": text(1, 120)}
    d["lines"] = obj({e: ref("line_list") for e in EVENTS})

    top = {
        "format": {"const": 1},
        "id": ref("id"),
        "name": text(1, 24),
        "title": text(0, 32),
        "description": text(0, 300),
        "rarity": {"enum": RARITIES},
        "starter_for": {"enum": FACTIONS},
        "factions": {"type": "array", "minItems": 1, "maxItems": 4, "uniqueItems": True,
                     "items": {"enum": FACTIONS}},
        "skin_model": {"enum": ["classic", "slim"]},
        "color": ref("color"),
        "element": ref("element"),
        "stats": obj({"health": num(20, 40), "attack": num(0, 4), "armor": num(0, 8), "speed": intg(90, 120),
                      "knockback_resistance": intg(0, 60), "size": intg(85, 110), "growth": {"enum": GROWTH}}),
        "resistances": obj({e: intg(-30, 60) for e in ELEMENTS}),
        "gear": {"type": "array", "maxItems": 6, "items": ref("gear_item")},
        "behaviour": obj({"combat_role": {"enum": ROLES}, "focus": {"enum": FOCUS}, "keep_distance": num(0, 16),
                          "aggression": {"enum": AGGRESSION}, "protects": {"enum": PROTECTS},
                          "retreat_below": intg(0, 60), "raids": {"enum": RAIDS}, "duty": {"enum": DUTIES}},
                         ["combat_role"]),
        "basic_attack": obj({"style": {"enum": STYLES}, "projectile": mcid(ITEM_LOOKS), "held_item": mcid(HELD_PROPS),
                             "amount": num(2, 10), "interval": num(0.8, 3), "element": ref("element"),
                             "on_hit": {"type": "array", "minItems": 1, "maxItems": 3, "items": ref("effect")},
                             "every_nth_hit": obj({"n": intg(2, 6), "effects": {
                                 "type": "array", "minItems": 1, "maxItems": 3, "items": ref("effect")}},
                                 ["n", "effects"])}, ["style"]),
        "skills": {"type": "array", "maxItems": 4, "items": ref("skill")},
        "ultimate": ref("ultimate"),
        "passives": {"type": "array", "maxItems": 4, "items": ref("passive")},
        "empowered": ref("empowered"),
        "base_abilities": {"type": "array", "maxItems": 3, "items": ref("base_ability")},
        "lines": ref("lines"),
    }
    schema = {"$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "urn:factionclash:hero:1",
              "title": "Faction Clash hero file, format 1",
              "description": "One hero per file: config/factionclash/heroes/<id>.json next to <id>.png. "
                             "Keys starting with _ are comments.",
              "type": "object",
              "required": ["format", "id", "name", "behaviour", "basic_attack"],
              "properties": top,
              "patternProperties": {"^_": {}},
              "additionalProperties": False,
              "$defs": d}
    return schema


def compact_dump(o, indent=0, width=150):
    """JSON with small objects/arrays on one line and a fixed 1-space indent, so it pastes in few lines."""
    one = json.dumps(o, separators=(",", ":"))
    if len(one) + indent <= width:
        return one
    pad = " " * (indent + 1)
    if isinstance(o, dict):
        parts = [pad + json.dumps(k) + ":" + compact_dump(v, indent + 1, width) for k, v in o.items()]
        return "{\n" + ",\n".join(parts) + "\n" + " " * indent + "}"
    if isinstance(o, list):
        parts = [pad + compact_dump(v, indent + 1, width) for v in o]
        return "[\n" + ",\n".join(parts) + "\n" + " " * indent + "]"
    return one


# ------------------------------------------------------------------ checker
class Report:
    def __init__(self, f):
        self.file = f
        self.items = []

    def add(self, outcome, path, msg):
        self.items.append((outcome, path, msg))


def strip_ns(v):
    return v[len("minecraft:"):] if isinstance(v, str) and v.startswith("minecraft:") else v


def amount_ref(a, hero, healer=True, marks=None):
    """Level-1 reference value of an Amount (HERO_FORMAT.md section 9.2)."""
    if isinstance(a, (int, float)):
        return float(a)
    s = hero["_stats"]
    marks = marks or {}
    v = a.get("base", 0)
    v += a.get("attack_percent", 0) / 100 * hero["_attack_ref"]
    v += a.get("max_health_percent", 0) / 100 * s["health"]
    v += a.get("armor_percent", 0) / 100 * (s["armor"] + 15)
    v += a.get("target_max_health_percent", 0) / 100 * 20
    v += a.get("target_missing_health_percent", 0) / 100 * 10
    if "per_mark_stack" in a:
        v += a["per_mark_stack"] * marks.get(a.get("mark"), 5)
    return v


def stat_helpful(stat, pct):
    return pct < 0 if stat == "damage_taken" else pct > 0


def effect_kind(e):
    """'enemy', 'ally', 'self', or 'either' (marks)."""
    t = e["type"]
    if t in ENEMY_ONLY:
        return "enemy"
    if t in ALLY_ONLY:
        return "ally"
    if t in SELF_ONLY:
        return "self"
    if t == "effect":
        return "ally" if strip_ns(e["id"]) in HELPFUL_POTIONS else "enemy"
    if t == "stat":
        return "ally" if stat_helpful(e["stat"], e["percent"]) else "enemy"
    return "either"  # mark, clear_marks


def check(path):
    import jsonschema
    rep = Report(path)
    with open(path, encoding="utf-8") as fh:
        hero = json.load(fh)
    schema = build_schema()
    v = jsonschema.Draft202012Validator(schema)
    errs = sorted(v.iter_errors(hero), key=lambda e: list(e.absolute_path))
    for e in errs:
        rep.add("SCHEMA", "/".join(map(str, e.absolute_path)), e.message[:300])
    if errs:
        return rep
    role = hero["behaviour"]["combat_role"]
    rd = ROLE_DEFAULTS[role]
    st = dict(health=rd["health"], attack=rd["attack"], armor=rd["armor"])
    st.update({k: hero.get("stats", {})[k] for k in ("health", "attack", "armor") if k in hero.get("stats", {})})
    hero["_stats"] = st
    ba = hero["basic_attack"]
    style = ba["style"]
    hero["_attack_ref"] = (ba.get("amount", 4) if style == "thrown" else STYLE_POWER[style]) + st["attack"]
    # marks applied in this file, with their max stacks
    marks = {}
    applied = set()

    def walk_effects(fn):
        def each(lst, p):
            for i, e in enumerate(lst or []):
                fn(e, f"{p}[{i}]")
        each(ba.get("on_hit"), "basic_attack.on_hit")
        if "every_nth_hit" in ba:
            each(ba["every_nth_hit"]["effects"], "basic_attack.every_nth_hit.effects")
        for i, s in enumerate(hero.get("skills", [])):
            each(s["effects"], f"skills[{i}].effects")
        if "ultimate" in hero:
            each(hero["ultimate"]["effects"], "ultimate.effects")
        for i, p in enumerate(hero.get("passives", [])):
            each(p["effects"], f"passives[{i}].effects")
        if "empowered" in hero:
            each(hero["empowered"].get("on_hit"), "empowered.on_hit")

    def collect(e, p):
        if e["type"] == "mark":
            marks[e["mark"]] = max(marks.get(e["mark"], 0), e.get("max_stacks", 5))
            applied.add(e["mark"])
    walk_effects(collect)

    def check_refs(e, p):
        for m in [e.get("mark") if e["type"] == "clear_marks" else None,
                  (e.get("if") or {}).get("mark"),
                  e.get("amount", {}).get("mark") if isinstance(e.get("amount"), dict) else None]:
            if m and m not in applied:
                rep.add("NOTE", p, f'mark "{m}" is never applied by this hero (fine if another hero applies it)')
        if e["type"] == "empower" and "empowered" not in hero:
            rep.add("PART LEFT OUT", p, "empower needs an \"empowered\" state at the top level")
        if e["type"] == "effect":
            pid = strip_ns(e["id"])
            lvl_cap, sec_cap = (HELPFUL_POTIONS.get(pid) or HARMFUL_POTIONS.get(pid))
            if e.get("level", 1) > lvl_cap:
                rep.add("ADJUSTED", p, f"{pid} level {e['level']} above cap {lvl_cap}")
            if e["seconds"] > sec_cap:
                rep.add("ADJUSTED", p, f"{pid} {e['seconds']} s above cap {sec_cap} s")
            if pid == "invisibility" and e.get("to") != "self":
                rep.add("PART LEFT OUT", p, "invisibility is self only")
        if e["type"] == "stat":
            lo, hi = STAT_CAPS[e["stat"]]
            if not lo <= e["percent"] <= hi:
                rep.add("ADJUSTED", p, f"stat {e['stat']} {e['percent']}% outside {lo}..{hi}")
            if e["percent"] == 0:
                rep.add("PART LEFT OUT", p, "stat percent 0 does nothing")
        if e["type"] == "summon":
            if e.get("count", 1) > SUMMONS[e["creature"]][0]:
                rep.add("ADJUSTED", p, f"{e['creature']} count above {SUMMONS[e['creature']][0]}")
        if "radius" in e and e.get("to") not in ("allies", "ally"):
            rep.add("NOTE", p, "radius is only used with \"to\": \"allies\" or \"ally\"")
        if e["type"] == "summon" and not (p.startswith("skills[") or p.startswith("ultimate")):
            rep.add("PART LEFT OUT", p, "summon only works in skills and the ultimate")
    walk_effects(check_refs)

    def eff_weight(e, a_mult, hits=1, pulses=1, slot=None, p=""):
        t = e["type"]
        k = effect_kind(e)
        reps = 1 if t in SELF_ONLY else hits * pulses
        to = e.get("to", "targets")
        A = 1.0 if to in ("self", "ally") or t in SELF_ONLY else (1.5 if to == "allies" else a_mult)
        w = 0.0
        if t == "damage":
            w = amount_ref(e["amount"], hero, marks=marks) * A
        elif t == "dot":
            w = amount_ref(e["amount"], hero, marks=marks) * e.get("seconds", 4) * 0.75 * A
        elif t == "heal":
            w = amount_ref(e["amount"], hero, marks=marks) * 1.25 * A
        elif t == "shield":
            w = amount_ref(e["amount"], hero, marks=marks) * A
        elif t == "effect":
            w = e.get("level", 1) * e["seconds"] / 4 * A
        elif t == "stat":
            w = abs(e["percent"]) / 10 * e.get("seconds", 8) / 4 * A
        elif t in ("knockback", "pull"):
            w = e.get("strength", 2 if t == "knockback" else 1) / 2 * A
        elif t in ("ignite", "freeze"):
            w = e.get("seconds", 4 if t == "ignite" else 3) / 2 * A
        elif t == "stun":
            w = e.get("seconds", 1) * 4 * A
        elif t == "taunt":
            w = e.get("seconds", 3) / 2 * A
        elif t in ("cleanse", "dispel"):
            w = 1 * A
        elif t == "energy":
            w = e["amount"] / 5 * A
        elif t == "cooldown_cut":
            w = e["seconds"]
        elif t == "move_self":
            w = 1
        elif t == "mark":
            w = 0.5 * e.get("stacks", 1) * A
        elif t == "empower":
            emp = hero.get("empowered", {})
            w = e["seconds"] / 4 * (1 + sum(abs(s["percent"]) for s in emp.get("stats", [])) / 20)
        elif t == "summon":
            w = e.get("count", 1) * e.get("seconds", 20) / 10 * SUMMONS[e["creature"]][1]
        w *= e.get("chance", 100) / 100
        if "if" in e:
            w *= 0.75
        return w * reps

    def slot_caps(effects, slot, hits=1, pulses=1, p=""):
        caps = CAPS[slot]
        mult = hits * pulses
        zone_bonus = 1.5 if pulses > 1 else 1.0
        tot = dict(damage=0.0, dot=0.0, heal=0.0, shield=0.0, stun=0.0)
        for i, e in enumerate(effects):
            t = e["type"]
            if t == "damage":
                tot["damage"] += amount_ref(e["amount"], hero, marks=marks) * mult
            elif t == "dot":
                tot["dot"] += amount_ref(e["amount"], hero, marks=marks) * e.get("seconds", 4)
            elif t == "heal":
                tot["heal"] += amount_ref(e["amount"], hero, marks=marks) * mult
            elif t == "shield":
                tot["shield"] += amount_ref(e["amount"], hero, marks=marks)
            elif t == "stun":
                tot["stun"] += e.get("seconds", 1) * mult
        for k, v in tot.items():
            cap = caps[k] * (zone_bonus if k in ("damage", "heal") else 1)
            if v > cap + 1e-9:
                rep.add("ADJUSTED", p, f"{slot} {k} total {v:.1f} above cap {cap:g} (scaled down)")
        return tot

    def check_compat(effects, form_friendly, p, area_form):
        dmg = sum(1 for e in effects if e["type"] == "damage")
        mv = sum(1 for e in effects if e["type"] == "move_self")
        if dmg > 2:
            rep.add("PART LEFT OUT", p, "more than 2 damage effects")
        if mv > 1:
            rep.add("PART LEFT OUT", p, "more than 1 move_self")
        for i, e in enumerate(effects):
            k = effect_kind(e)
            to = e.get("to", "targets")
            q = f"{p}[{i}]"
            if k == "enemy":
                if form_friendly or to != "targets":
                    rep.add("PART LEFT OUT", q, f"{e['type']} only works on enemies (form/to)")
            elif k == "ally":
                if not form_friendly and to == "targets":
                    rep.add("NOTE", q, f'{e["type"]} in an enemy form goes to the hero (write "to")')
            elif k == "either" and form_friendly and to not in ("self",):
                if e["type"] == "mark":
                    rep.add("PART LEFT OUT", q, "marks go on enemies or on the hero (to: self)")

    # skills / ultimate
    abil = [(f"skills[{i}]", s, "skill") for i, s in enumerate(hero.get("skills", []))]
    if "ultimate" in hero:
        abil.append(("ultimate", hero["ultimate"], "ultimate"))
    for p, s, slot in abil:
        form = s["form"]
        friendly = form in FRIENDLY_FORMS
        if s.get("use_when") == "out_of_combat" and not friendly:
            rep.add("ADJUSTED", p + ".use_when", "out_of_combat only with self/ally/allies; read as always")
        if "hits" in s and form not in ("melee", "bolt", "nova", "cone", "line"):
            rep.add("NOTE", p + ".hits", f"hits not used by form {form}")
        if "projectile" in s and form not in ("shot", "volley"):
            rep.add("NOTE", p + ".projectile", f"projectile not used by form {form}")
        hits = s.get("hits", 1) if form in ("melee", "bolt", "nova", "cone", "line") else 1
        pulses = 1
        if form == "zone":
            dur = s.get("duration", 0)
            pulses = 1 + int(dur // s.get("interval", 1)) if dur > 0 else 1
        if form == "volley":
            a_mult = 0.75 * s.get("count", 3)
        elif form in ("nova", "cone", "line", "zone", "chain", "leap", "allies") or s.get("radius", 0) > 0:
            a_mult = 1.5
        else:
            a_mult = 1.0
        check_compat(s["effects"], friendly, p + ".effects", a_mult > 1)
        slot_caps(s["effects"], slot, hits, pulses, p)
        w = sum(eff_weight(e, a_mult, hits, pulses) for e in s["effects"]) + (2 if form in ("leap", "dash") else 0)
        if slot == "skill":
            ch = s.get("charges", 1)
            floor = (2 + w / 2) * (1 + 0.5 * (ch - 1))
            cd = s.get("cooldown", 8)
            outcome = "ok" if cd >= floor else "RAISED"
            rep.add("INFO", p, f'"{s["name"]}" weight {w:.1f}, floor {floor:.1f} s, cooldown {cd} s -> {outcome}')
            if cd < floor:
                rep.add("ADJUSTED", p + ".cooldown", f"cooldown {cd} below floor {floor:.1f}")
        else:
            gap = min(60.0, max(20.0, w))
            rep.add("INFO", p, f'"{s["name"]}" weight {w:.1f}, at least {gap:.0f} s between two uses')
    # basic attack extras
    if "on_hit" in ba:
        check_compat(ba["on_hit"], False, "basic_attack.on_hit", False)
        slot_caps(ba["on_hit"], "on_hit", p="basic_attack.on_hit")
    if "every_nth_hit" in ba:
        check_compat(ba["every_nth_hit"]["effects"], False, "basic_attack.every_nth_hit.effects", False)
        slot_caps(ba["every_nth_hit"]["effects"], "nth_hit", p="basic_attack.every_nth_hit")
    if style != "thrown":
        for k in ("projectile", "held_item", "amount", "interval", "element"):
            if k in ba:
                rep.add("NOTE", "basic_attack." + k, "only used by style thrown")
    if "empowered" in hero and "on_hit" in hero["empowered"]:
        check_compat(hero["empowered"]["on_hit"], False, "empowered.on_hit", False)
        slot_caps(list(ba.get("on_hit", [])) + list(hero["empowered"]["on_hit"]), "on_hit",
                  p="basic_attack.on_hit + empowered.on_hit (shared cap)")
    if style == "thrown":
        amt, iv = ba.get("amount", 4), ba.get("interval", 1.5)
        if amt / iv > 5 + 1e-9:
            rep.add("ADJUSTED", "basic_attack.interval", f"{amt} damage every {iv} s is over 5 per second; "
                    f"interval raised to {amt / 5:g} s")
    if "stats" in hero or "empowered" in hero:
        size = hero.get("stats", {}).get("size", 100) * hero.get("empowered", {}).get("size", 100) / 100
        if size > 110 + 1e-9:
            rep.add("NOTE", "empowered.size", f"stats.size x empowered.size = {size:.0f}%; capped at 110% "
                    "(fits 2-high tunnels)")
    # passives
    for i, ps in enumerate(hero.get("passives", [])):
        p = f"passives[{i}]"
        trig = ps["trigger"]
        form = ps.get("form", TRIGGER_DEFAULT_FORM[trig])
        if form not in TRIGGER_FORMS[trig]:
            rep.add("PART LEFT OUT", p + ".form", f"form {form} not allowed for {trig}: {TRIGGER_FORMS[trig]}")
        if trig in TRIGGER_VALUE:
            lo, hi, dflt = TRIGGER_VALUE[trig]
            tv = ps.get("trigger_value", dflt)
            if not lo <= tv <= hi:
                rep.add("ADJUSTED", p + ".trigger_value", f"{tv} outside {lo}..{hi}")
        if trig == "always":
            for j, e in enumerate(ps["effects"]):
                if e["type"] != "stat" or "seconds" in e:
                    rep.add("PART LEFT OUT", f"{p}.effects[{j}]", "always passives hold stat effects without seconds")
                elif not -20 <= e["percent"] <= 20:
                    rep.add("ADJUSTED", f"{p}.effects[{j}]", "always-passive stats are capped at +-20%")
            continue
        friendly = form in ("self", "trigger_ally", "ally", "allies")
        a_mult = 1.5 if form in ("nova", "allies") else 1.0
        check_compat(ps["effects"], friendly, p + ".effects", a_mult > 1)
        slot_caps(ps["effects"], "passive", p=p)
        if trig == "on_ultimate" and any(e["type"] == "energy" for e in ps["effects"]):
            rep.add("PART LEFT OUT", p, "on_ultimate passives cannot give energy")
        w = sum(eff_weight(e, a_mult) for e in ps["effects"])
        floor = max(1.0, w * ps.get("chance", 100) / 100 / 2)
        if trig == "on_ally_attack":
            floor = max(floor, 2.0)
        cd = ps.get("cooldown", 1)
        if trig == "every":
            period = ps.get("trigger_value", 10)
            outcome = "ok" if period >= floor else "RAISED"
            rep.add("INFO", p, f'"{ps["name"]}" every {period} s, weight {w:.1f}, floor {floor:.1f} s -> {outcome}')
            if period < floor:
                rep.add("ADJUSTED", p + ".trigger_value", f"period {period} s below floor {floor:.1f} s")
        else:
            outcome = "ok" if cd >= floor else "RAISED"
            rep.add("INFO", p, f'"{ps["name"]}" weight {w:.1f}, floor {floor:.1f} s, cooldown {cd} s -> {outcome}')
            if cd < floor:
                rep.add("ADJUSTED", p + ".cooldown", f"cooldown {cd} below floor {floor:.1f}")
    # lines and placeholders
    for ev, lines in hero.get("lines", {}).items():
        if ev.startswith("_"):
            continue
        for j, ln in enumerate(lines):
            for ph in re.findall(r"\{([a-z_]+)\}", ln):
                if ph not in PLACEHOLDERS[ev]:
                    rep.add("PART LEFT OUT", f"lines.{ev}[{j}]", f"{{{ph}}} not allowed; allowed {PLACEHOLDERS[ev]}")
    # gear
    total_ench = 0
    for i, g in enumerate(hero.get("gear", [])):
        if isinstance(g, dict):
            for en, lv in g.get("enchantments", {}).items():
                en = strip_ns(en)
                total_ench += lv
                if lv > ENCHANTS[en]:
                    rep.add("ADJUSTED", f"gear[{i}].enchantments.{en}", f"level {lv} above cap {ENCHANTS[en]}")
    if total_ench > 6:
        rep.add("ADJUSTED", "gear", f"{total_ench} enchantment levels in the kit, cap 6")
    if hero["id"] != re.sub(r"[^a-z0-9_]", "", hero["name"].lower().replace(" ", "_").replace("-", "_")):
        rep.add("NOTE", "id", "id does not match the name (fine; the file name is what counts)")
    return rep


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "schema":
        s = build_schema()
        with open(sys.argv[2], "w", encoding="utf-8") as fh:
            json.dump(s, fh, indent=2)
            fh.write("\n")
        if len(sys.argv) >= 4:
            with open(sys.argv[3], "w", encoding="utf-8") as fh:
                fh.write(compact_dump(s) + "\n")
        print("schema written")
        return
    if len(sys.argv) >= 3 and sys.argv[1] == "check":
        bad = 0
        for f in sys.argv[2:]:
            rep = check(f)
            print("==", f)
            for o, p, m in rep.items:
                print(f"  {o:14} {p}: {m}")
                if o in ("SCHEMA", "PART LEFT OUT", "ADJUSTED"):
                    bad += 1
        print("problems:", bad)
        sys.exit(1 if bad else 0)
    print(__doc__)


if __name__ == "__main__":
    main()
