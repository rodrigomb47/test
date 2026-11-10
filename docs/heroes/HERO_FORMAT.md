# Faction Clash hero format, version 1 (authoritative specification)

Status: final design for ROADMAP phase 4. Nothing here is implemented yet. Numbers marked PROPOSED go to BALANCE.md
when built. The format, the catalogue and every example in this document are generic and original.

This document merges two design studies: the "expressive" catalogue (elements, multi-target delivery, lasting
zones, marks, an empowered state, passives that join an ally's attack, summons, non-combat perks) wherever it can be
validated and implemented, and the "robust" study's rules (few required fields, one unit per kind of number, safe defaults,
balance caps the mod enforces, forgiving loading with strict, plain-language reporting).

Companion files (same folder):
- `hero-prompt.txt`: the paste-ready prompt the owner gives another chatbot.
- `hero.schema.json`: the JSON Schema of Appendix A (draft 2020-12), pretty-printed.
- `examples/bram_ostler.json`, `examples/sela_quickfern.json`, `examples/maudie_brine.json`: three complete
  original heroes (a frontline protector, a ranged damage dealer, a healer). All three validate against the schema and
  pass the rule checks of sections 13 to 17.

---

## 0. Summary and decisions

A **hero** is a named, unique faction NPC with its own skin, stats, element, gear, behaviour, combat kit (a basic
attack with on-hit effects and a combo finisher, up to 4 skills with cooldowns, one ultimate charged by energy, up to
4 passives, an optional empowered state), non-combat **base abilities** (work auras, healing between fights, a
lookout, cheaper recruits, drill), an out-of-combat **duty** (guard, scout or any worker job), dialogue lines, levels,
a starter hero per faction, hero tokens from bosses, and a 10-minute revival.

Heroes are **pure data**. Abilities are composed from a fixed catalogue: 14 delivery **forms**, 21 **effect types**,
14 **conditions**, 15 passive **triggers**, 8 **elements**. There is no scripting. Every value is checked when the
file loads, and every problem is reported with the file, line, field path, what is wrong and what is allowed.

Decisions this design makes (the owner confirms or corrects them; section 34 lists the open ones):

1. **One folder, no datapack, no resource pack.** `config/factionclash/heroes/` holds `<id>.json` and `<id>.png` side by
   side. The server sends skins to clients. ROADMAP phase 4 currently describes a datapack plus resource pack; its
   text must be changed to this folder design.
2. **A hero is a `FactionNpc` with a hero id**, not a new entity type. Jobs, hunger, gear, navigation, stuck recovery,
   saving and the war council keep working. Its `NpcRole` is its duty role.
3. **Few required fields.** A valid file needs `format`, `id`, `name`, `behaviour.combat_role` and
   `basic_attack.style`. Everything else has a default, mostly from the combat role.
4. **One unit per kind of number.** Time in seconds, distance in blocks, health and damage in health points (2 = one
   heart), every chance, share and bonus in whole percent.
5. **The mod enforces balance.** Per-slot caps on damage, healing, shields and control; a cooldown floor computed from
   how strong a skill is; potion and stat caps; a server-wide `powerScale`. Out-of-range values are clamped with a
   message, not rejected.
6. **Forgiving loading, strict reporting.** Four outcomes per problem: NOT LOADED (only a broken core), PART LEFT OUT
   (one ability, line or gear item is skipped), ADJUSTED (a value clamped or a word corrected), NOTE.
7. **The validator is pure Java** (`logic/hero/`), so the same check runs in unit tests, on the server and offline
   from the repository (`./gradlew checkHeroes`). Vanilla ids come from fixed allowlists (Appendix B), so the offline
   check needs no game registries.
8. **Player-like rules hold.** Vanilla damage, armour, potion effects and projectiles; movement abilities are
   physical pushes, never teleports; abilities never place, break or ignite blocks; kit items never multiply.
9. **Game time drives timers.** Revival is 12000 game ticks (10 minutes), so `/tick sprint` campaigns stay consistent.

---

## 1. The owner's workflow

The owner writes heroes with a chatbot (using `docs/heroes/hero-prompt.txt`), makes or obtains each skin, and keeps
the pairs (`<id>.json` + `<id>.png`) in their own place: their server's `config/factionclash/heroes/` folder, and a
private repository of their own (`rodrigomb47/faction-heros`) as the master copy. Hero files written by the owner are
**never committed to this mod repository and never put in the jar or any download**: the jar ships only the mod's own
original example heroes.

Checking and testing:

1. The owner drops the files into a server's `config/factionclash/heroes/`, runs `/fc hero reload`, reads the summary
   in chat and `/fc hero check <id>` for details, or runs the offline checker on a folder
   (`./gradlew checkHeroes -PheroDir=<folder>`, the same code the server runs).
2. When the owner asks for a test, Claude copies the owner's files unchanged from the private repository into its
   test server's `config/factionclash/heroes/` (`HEROES_DIR=<folder> tools/setup_server.sh <server>`), runs
   `/fc hero reload` and `/fc hero check all` over RCON and the campaign check (section 31.3), and reports every
   problem with the checker's message and what the rule allows.
3. Claude does not edit, rename, split or fix the owner's hero files, and does not write character content. The owner
   (or the chatbot that wrote the file, given the report lines) fixes them; `_report.txt` is written for exactly that.
   Problems that come from the hero *system* (a valid file that misbehaves, a crash, a wrong message) are fixed in the
   mod's code.

## 2. Installing heroes on a server

### 2.1 The folder

```
<server>/config/factionclash/heroes/   created at first start (FMLPaths.CONFIGDIR + "factionclash/heroes")
  bram_ostler.json                     a hero
  bram_ostler.png                      its skin: same name, .png
  my_pack/                             one level of sub-folders is scanned too
    maudie_brine.json
    maudie_brine.png
  _README.txt                          written by the mod: what goes here, the commands
  _PROMPT.txt                          written by the mod: the chatbot prompt (hero-prompt.txt)
  _hero.schema.json                    written by the mod: Appendix A
  _example.json                        written by the mod: a complete example hero
  _report.txt                          written by the mod after every load: every problem, grouped per file
  _disabled/                           anything whose name starts with "_" is never loaded
```

Rules:
- **The file name is the hero id.** It is normalised: lowercase, spaces and `-` become `_`, other characters are
  dropped, 3 to 32 characters (`Bram Ostler.json` is read as `bram_ostler`, ADJUSTED). If the `id` inside differs, the
  file name wins (ADJUSTED). The skin is the PNG with the same normalised name.
- A file may hold one hero object, or a JSON list of hero objects. In a list each hero needs its own `id`, and its
  skin is `<that id>.png` in the same folder.
- Files and folders whose name starts with `_` or `.` are ignored. Moving a hero into `_disabled/` switches it off.
- Two files with the same id: the first in alphabetical path order loads; the other is NOT LOADED with a message
  naming both files.
- At most `heroes.maxLoaded` heroes (64). The rest are NOT LOADED in alphabetical order.
- The folder path is a config value (`heroes.folder`).

### 2.2 Built-in example heroes

The jar contains the three example heroes (JSON plus original skins drawn with `tools/gen_assets.py`) under
`src/main/resources/factionclash/heroes/`, read as class-loader resources, not as a datapack. They load first. A file
in the folder with the same id replaces the built-in hero, so the owner can edit a copy. `heroes.builtinHeroes =
false` turns them off.

### 2.3 When files are read

- **Server start** (`ServerStartingEvent`, before the campaign ticks): every file is read; the summary and every
  problem go to the log; `_report.txt` is rewritten; the guide files are written if missing.
- **`/fc hero reload`** (operator): every file is read again. The reload is atomic: the new set replaces the old one
  only after every file has been read. A hero whose file is now NOT LOADED keeps its **last good definition** (section
  25.5). A living hero picks up its new definition at once: stats re-applied, cooldowns of removed abilities dropped,
  energy kept, a cast in progress cancelled. Changed skins are sent to every player.
- **`/fc hero check <id|all>`** (operator): reads the file(s) now and validates them without applying anything.

---

## 3. File conventions

| Convention | Rule |
|---|---|
| Format | One JSON object per hero. Field names and enum words are `snake_case`. Top field `"format": 1`. |
| Time | Seconds, decimals allowed (rounded to the nearest tick). |
| Distance | Blocks, decimals allowed. |
| Health, damage, healing, shields | Health points: 2 = one heart. A player and a zombie have 20. An iron sword hit is 6, a diamond sword 7, a fully drawn bow shot about 6 to 9. |
| Percent | Every chance, share, bonus and speed is a whole number of percent (`25` = 25%). |
| Ids | Hero id: lowercase letters, digits, `_`; 3-32 characters; starts with a letter. Mark ids: the same, 2-24 characters. |
| Vanilla ids | Items, potions, particles and sounds are written without `minecraft:` (`iron_sword`, `slowness`, `flame`, `entity.blaze.shoot`); the prefix is accepted. Only the allowlists of Appendix B exist. |
| Text | Plain text: `§` codes and control characters are removed. Limits are in the field tables. |
| Comments | Any key starting with `_` is ignored anywhere (`"_note": "..."`). |
| Defaults | Every optional field has a default. Leaving a field out is always safe. |

### 3.1 What the loader forgives (each fix is reported)

- A UTF-8 byte-order mark, CRLF line ends, and a surrounding markdown code fence are removed (NOTE). A file that
  holds a whole pasted chat answer (text around a ```json block) is read from its first JSON code block (NOTE:
  "read the code block; the text around it was ignored").
- `//` and `/* */` comments and trailing commas before `}` or `]` are accepted (NOTE).
- `"8"`, `"8s"`, `"8 seconds"` where seconds are expected become 8; `"25%"` where percent is expected becomes 25;
  `"true"` and `"false"` as text become booleans (ADJUSTED).
- Enum words ignore case, spaces and hyphens (`"Slow Falling"` = `slow_falling`). Synonym tables (Appendix C) read
  common chatbot words: `tank` as `vanguard`, `aoe` as `nova`, `burn` as `ignite` (ADJUSTED).
- A misspelled field name close to exactly one real field that is absent from that object is read as that field
  (`cooldwon` read as `cooldown`, ADJUSTED). Close means edit distance 1 for names of 4 letters or fewer (`up`, `to`,
  `id`, `hits`) and 2 for longer ones. Any other unknown field, or one close to two fields, is ignored (NOTE, with a
  did-you-mean).
- A number outside its range is clamped to the nearest allowed value (ADJUSTED). A decimal where a whole number is
  expected is rounded (ADJUSTED).
- An unknown word in an optional enum field (an `element`, a `to`, a `focus`, a particle, a sound) falls back to that
  field's default (ADJUSTED, with the allowed list). An unknown word where the word decides what the part is (an
  effect `type`, an ability `form`, a passive `trigger`, a base ability `type`) leaves that part out (PART LEFT OUT).
  A missing or unknown `behaviour.combat_role` or `basic_attack.style` is NOT LOADED.

Not accepted (NOT LOADED, with the position and a plain explanation): broken JSON (missing comma, missing quote,
curly quotes `“ ”` used as JSON quotes, single quotes, unclosed brackets, text after the end), duplicate keys, a file
over 128 KB, nesting deeper than 8, strings over 1000 characters, YAML.

---

## 4. Top-level fields

| Field | Type | Req. | Default | Allowed / meaning |
|---|---|---|---|---|
| `format` | integer | yes | - | `1`. A higher number: NOT LOADED, "this file is for a newer Faction Clash". |
| `id` | id | yes | file name | Must match the file name; the file name wins (ADJUSTED). Required so the chatbot states the file name. |
| `name` | text 1-24 | yes | - | Name tag, chat, hero sheet. |
| `title` | text 0-32 | no | `""` | Epithet after the name: `★ Bram Ostler · the Gatewarden`. |
| `description` | text 0-300 | no | `""` | Hero sheet and `/fc hero info`. |
| `rarity` | `common`, `rare`, `epic`, `legendary` | no | `rare` | Weight in random token draws 8 / 4 / 2 / 1. Legendary heroes are never random starters. |
| `starter_for` | faction id | no | none | `ember_legion`, `grove_covenant`, `tide_alliance`, `stoneguard` (section 25.2). |
| `factions` | list of 1-4 faction ids | no | all four | Factions that may own the hero (tokens of other factions skip it). The Hollow cannot have heroes in v1. |
| `skin_model` | `classic`, `slim` | no | `classic` | Arm width the PNG was painted for: classic 4 px, slim 3 px. |
| `color` | `"#RRGGBB"` | no | faction colour | Dust particles, chat lines, boss bar tint. |
| `element` | element | no | `physical` | Default element of the hero's damage (section 6). |
| `stats` | object | no | by role | Section 5. |
| `resistances` | map element to whole % -30..60 | no | `{}` | Less damage taken from that element; negative = weakness. |
| `gear` | list of 0-6 gear items | no | the style's basic item | The hero's own kit (section 7). |
| `behaviour` | object | yes | - | Section 8. Only `combat_role` is required inside it. |
| `basic_attack` | object | yes | - | Section 9. Only `style` is required inside it. |
| `skills` | list of 0-4 skills | no | `[]` | Section 10. |
| `ultimate` | ultimate | no | none | Section 10; charged by energy (section 14). |
| `passives` | list of 0-4 passives | no | `[]` | Section 17. |
| `empowered` | object | no | none | The state the `empower` effect enters (section 18). |
| `base_abilities` | list of 0-3 | no | `[]` | Non-combat abilities (section 21). |
| `lines` | map event to lines | no | built-in generic lines | Dialogue (section 23). |

Limits per file: 4 skills, 1 ultimate, 4 passives, 3 base abilities, 6 gear items, 5 effects per ability, 3 effects
per passive, 3 effects on the basic attack's `on_hit` and 3 in its finisher, 8 lines per event.

---

## 5. Stats, roles and growth

### 5.1 `stats`

| Field | Type | Range | Default | Maps to |
|---|---|---|---|---|
| `health` | number | 20-40 | by role | `MAX_HEALTH` base at level 1 (a player has 20). |
| `attack` | number | 0-4 | by role | Added to `ATTACK_DAMAGE` (like the Might affix): every melee hit and the hero's attack power (section 6.2). |
| `armor` | number | 0-8 | by role | Natural `ARMOR` on top of worn armour (like Toughness). |
| `speed` | whole % | 90-120 | by role | Walking speed, percent of a player's (`MOVEMENT_SPEED` 0.1 × speed / 100). |
| `knockback_resistance` | whole % | 0-60 | by role | `KNOCKBACK_RESISTANCE` = value / 100. |
| `size` | whole % | 85-110 | 100 | `generic.scale`. A hero at 110% is 1.98 blocks tall (the NPC is 1.8) and still fits 2-high mine tunnels. With the empowered `size` the product is capped at 110%. |
| `growth` | word | see 5.3 | by role | How the hero grows per level. |

### 5.2 Combat role presets

`behaviour.combat_role` sets the defaults for stats, growth and behaviour. Any field the file sets wins.

| Role | Plays like | health | attack | armor | speed | kb res. | growth | focus | keep_distance | protects | aggression | retreat_below | raids |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `vanguard` | front line, draws enemies, protects | 34 | 1 | 3 | 95 | 30 | tough | threat_to_allies | 0 | allies | steady | 20 | leads |
| `striker` | melee damage | 28 | 2 | 1 | 100 | 10 | fierce | weakest | 0 | none | bold | 25 | leads |
| `skirmisher` | hit and run: after each melee hit steps back 3 blocks until its next swing is ready | 26 | 2 | 0 | 108 | 0 | swift | weakest | 4 | none | bold | 30 | joins |
| `marksman` | ranged damage, kites | 24 | 0 | 1 | 100 | 0 | balanced | weakest | 10 | none | cautious | 40 | joins |
| `support` | stays behind its side, heals, shields, buffs | 26 | 0 | 1 | 100 | 0 | mystic | threat_to_allies | 8 | allies | cautious | 40 | joins |

Synonyms (ADJUSTED): `tank`, `guardian`, `defender`, `protector` → vanguard; `fighter`, `warrior`, `dps`, `brawler`,
`assassin`, `berserker` → striker; `rogue`, `duelist`, `skirmish` → skirmisher; `archer`, `ranger`, `sniper`,
`gunner`, `caster`, `mage` → marksman; `healer`, `buffer`, `medic`, `bard`, `sustain` → support.

### 5.3 Growth profiles (per level above 1; PROPOSED)

| `growth` | Health | Attack | Armour | Speed | Ability power |
|---|---|---|---|---|---|
| `balanced` | +0.5 | +0.06 | +0.06 | - | +1% |
| `tough` | +0.8 | +0.03 | +0.12 | - | +0.5% |
| `fierce` | +0.3 | +0.12 | +0.03 | - | +1% |
| `swift` | +0.4 | +0.06 | +0.03 | +0.3% | +1% |
| `mystic` | +0.4 | +0.03 | +0.03 | - | +2% |

Growth is applied as attribute modifiers with fixed ids next to the affix ones in `FactionNpc.reapplyAffixes`, so a
reload or a level-up never stacks them. Ability power multiplies every Amount (section 12).

---

## 6. Elements, attack power and the damage formula

### 6.1 Elements

Eight elements. Each maps to two mod damage types shipped in the jar (`data/factionclash/damage_type/hero_<element>.json`
and `hero_<element>_dot.json`, the second with `no_knockback`), tagged so vanilla's own rules apply.

| Element | Default particle | Vanilla behaviour through damage-type tags | No damage to | Extra damage ×1.3 (PROPOSED) |
|---|---|---|---|---|
| `physical` | `crit` | armour, shields and Protection apply | - | - |
| `fire` | `flame` | `is_fire`: Fire Protection reduces it | fire-immune mobs, Fire Resistance | snow golems |
| `frost` | `snowflake` | not tagged `is_freezing` (that tag would multiply ×5 against blazes) | `#freeze_immune_entity_types` | `#freeze_hurts_extra_types` (blaze, magma cube, strider) |
| `shock` | `electric_spark` | `is_lightning` is not used (it would hurt items and turn creepers) | - | targets in water or rain |
| `venom` | dust `#5BAE3C` | - | `#ignores_poison_and_regen` (undead) | - |
| `blight` | dust `#2E2A33` | `wither_immune_to` | the wither, wither skeletons | - |
| `arcane` | `enchant` | `bypasses_armor`, `bypasses_shield`, `witch_resistant_to` (witches take 15%) | - | - |
| `radiant` | `end_rod` | - | - | `#sensitive_to_smite` (undead) |

Each element has a death message ("X was scorched by Bram Ostler") from lang keys in the jar.

**Resistances.** A hero's `resistances` and incoming damage of other origins: vanilla damage maps to elements for
hero targets only (mob and player attacks and arrows → physical; fire, lava, hot floor → fire; freezing → frost;
lightning → shock; poison → venom; wither → blight; magic → arcane). Normal NPCs, players and mobs have no innate
resistances; they have armour.

### 6.2 Attack power

Amounts may scale with the hero's **attack power** (section 12):

- `melee` style: the `ATTACK_DAMAGE` attribute, exactly the number a player's hit with the held weapon uses (base 1 +
  weapon + `stats.attack` + growth + Might affixes + Strength/Weakness + `attack` stat modifiers).
- `bow` 6 (+1 per Power level), `crossbow` 7, `trident` 8, `thrown` the basic attack's `amount`; each plus the same
  bonuses (`ATTACK_DAMAGE` minus its base 1 when no melee weapon is held).

Gear therefore matters: a hero with an enchanted diamond sword from the storehouse hits harder with attack-scaled
abilities. For caps and cooldown floors the mod uses a **reference attack power**: melee 6 (an iron sword), bow 6,
crossbow 7, trident 8, thrown `amount`, each plus `stats.attack`.

**Gear and buff cap (PROPOSED).** At hit time an Amount uses at most 1.5 × the reference attack power and 1.5 × the
reference armour (`stats.armor` + 15). Gear and buffs still matter (up to +50%), but netherite, Strength II, Might
affixes and an `attack` buff stacked together cannot turn a skill capped at 16 into a 40-point hit.

### 6.3 The damage formula (one place, `HeroDamage`)

```
raw    = Amount at hit time × (1 + ability power per level × (level - 1)) × heroes.powerScale / 100
dealt  = raw × (1 + Σ attacker damage_dealt %)           clamped -50% .. +80%
             × element against the target                  0 (immune) or 1.3 (weak) or 1
             × (1 - target resistance to the element %)    clamped -30% .. +60%
             × (1 + Σ target damage_taken %)               clamped -60% .. +50%
the product of these four factors is clamped to 0 .. 2.0 (PROPOSED), so stacked buffs, weaknesses and debuffs at
most double a hit; one hit is clamped to heroes.maxHit (40, PROPOSED); the target_max_health part to 2% against bosses
then   target.hurt(hero damage source(element), dealt)     vanilla armour, enchantments, shields, absorption apply
```

**Invulnerability frames.** Vanilla ignores most damage for half a second after a hit, which would swallow multi-hits
and damage over time. `HeroDamage` saves the target's `invulnerableTime` and `lastHurt`, clears them for the hero hit
and restores them after, so hero hits neither get blocked by i-frames nor block a soldier's next sword hit.
(Tagging the hero damage types `minecraft:bypasses_cooldown` alone is not enough: in 1.21.1 `LivingEntity.hurt` still
sets `invulnerableTime = 20` and `lastHurt` for such a hit, so the save and restore is needed either way.)

---

## 7. Gear (the hero's kit)

`gear` is a list of up to 6 items, each written as an id (`"iron_sword"`) or as an object:

```json
{ "item": "iron_sword", "name": "Toll Breaker", "enchantments": { "knockback": 1 } }
```

| Field | Rule |
|---|---|
| `item` | From the gear allowlist (Appendix B.4): wooden, stone, iron and golden swords and axes, mace, bow, crossbow, trident, shield, leather, chainmail, iron and golden armour, turtle helmet. **Iron is the best a hero starts with**: diamond and netherite items are PART LEFT OUT ("the hero can still take one from the storehouse"). |
| `name` | 1-24 characters, shown as the item's custom name. |
| `enchantments` | Up to 3 per item from the enchantment allowlist (Appendix B.5), each at most its listed level; the item must accept it under vanilla's rules (`#minecraft:enchantable/*` item tags); at most 6 enchantment levels in the whole kit. |

- One item per equipment slot; a second one for the same slot is PART LEFT OUT.
- If the kit lacks what `basic_attack.style` needs, the basic item is added (melee: stone sword; bow, crossbow,
  trident) with an ADJUSTED note. Ranged styles also get the one never-used-up arrow, as archers do.
- **Kit items are bound to the hero** (hidden data component `factionclash:hero_kit`): they never drop, are never
  deposited, cannot be taken, and come back on revival. A hero who dies 40 times does not flood the world with
  swords. Better storehouse gear is still taken over them, as for any NPC (`GuardJob.upgradeEquipment`), and that
  gear drops normally on death.

---

## 8. Behaviour (`behaviour`)

| Field | Type | Default | Allowed | Meaning |
|---|---|---|---|---|
| `combat_role` | word | required | `vanguard`, `striker`, `skirmisher`, `marksman`, `support` | Section 5.2. |
| `focus` | word | by role | `nearest`, `weakest`, `strongest`, `ranged_first`, `threat_to_allies`, `heroes_first` | Which enemy it picks; re-checked every 2 s. `weakest` = fewest health points left; `strongest` = most max health; `threat_to_allies` = an enemy attacking someone it protects, else the nearest. |
| `keep_distance` | blocks 0-16 | by role | | 0 closes to melee. Ranged and support heroes back away like archers when an enemy comes closer than half this. |
| `aggression` | word | by role | `cautious`, `steady`, `bold` | Search radius, chase leash, retreat default (table below). |
| `protects` | word | by role | `none`, `allies`, `workers`, `players`, `heart` | It prefers enemies attacking them. `allies` = NPCs and heroes of its side; `workers` = civilians of its base; `players` = the faction's players within 24 blocks; `heart` = during a base defence it stays within 16 blocks of the Faction Heart. |
| `retreat_below` | whole % 0-60 | by role | | Falls back (`WithdrawGoal`) under this share of health; 0 = never. A ready self or ally heal is used first. |
| `raids` | word | by role | `leads`, `joins`, `stays_home` | `leads`: always in its base's raid party, picked first; `joins`: like a soldier; `stays_home`: a home defender. |
| `duty` | word | `guard` | `guard`, `scout`, `builder`, `miner`, `farmer`, `woodcutter`, `blacksmith`, `chef`, `smelter`, `animal_carer`, `butcher`, `fisherman`, `cave_explorer`, `hauler`, `enchanter`, `trader` | What it does when there is no fight: the existing job of that role (a miner-duty hero really mines). It counts as that worker for the base's needs and is never retrained. Whatever its duty, a hero always fights and always answers base alarms. |

| `aggression` | Looks for enemies within | Chases up to (from its base when defending) |
|---|---|---|
| `cautious` | 12 blocks | 32 |
| `steady` | 20 | 56 (the soldiers' leash) |
| `bold` | 28 | 80 |

`retreat_below` defaults to the role's value (table 5.2) whatever the aggression.

---

## 9. Basic attack (`basic_attack`)

| Field | Type | Default | Allowed | Notes |
|---|---|---|---|---|
| `style` | word | required | `melee`, `bow`, `crossbow`, `trident`, `thrown` | |
| `projectile` | word | `snowball` | item looks (Appendix B.6) | `thrown` only. What the thrown thing looks like. |
| `held_item` | word | none | props (Appendix B.7) | `thrown` only. An item held as a prop (a staff, a book, a shell); bound to the hero like kit items. |
| `amount` | health points 2-10 | 4 | | `thrown` only: damage per hit. |
| `interval` | seconds 0.8-3 | 1.5 | | `thrown` only: time between throws. Raised so that `amount` / `interval` is at most 5 damage per second, about a bow's rate (ADJUSTED: "10 damage every 0.8 s is faster than a bow; the interval was raised to 2 s"). |
| `element` | element | hero `element` | | `thrown` only. Melee and arrow hits stay physical; add element damage through `on_hit`. |
| `on_hit` | 1-3 effects | none | slot `on_hit` caps | Applied to the enemy each landed basic hit hits (an imbue, a mark, a chance to ignite). `"to": "self"` sends one to the hero. |
| `every_nth_hit` | `{ "n": 2-6, "effects": [1-3] }` | none | slot `nth_hit` caps | A combo finisher: every n-th landed hit also applies these. |

| Style | How it fights (existing code where possible) |
|---|---|
| `melee` | `NpcMeleeAttackGoal` with the held weapon: real attack speed, jump critical hits, sword sweeps, shield work. |
| `bow` / `crossbow` | `NpcRangedAttackGoal`: real draw or charge time, the one never-used-up arrow, focus fire, kiting at `keep_distance`. |
| `trident` | Throws a copy that cannot be picked up (the held trident stays), range 14, 1.2 s between throws, vanilla trident damage; melee with it when an enemy is within 3 blocks. |
| `thrown` | For caster-like heroes: throws the `projectile` look every `interval` for `amount` damage of `element`, range 14, straight flight. Never sets blocks alight. |

A `thrown` style in a melee role, or `melee` in `marksman`, is allowed with a NOTE ("a marksman that fights in melee;
is that intended?").

---

## 10. Abilities: `skills` and `ultimate`

Both share one shape. A skill has a cooldown; the ultimate fires when its energy is full (section 14).

| Field | Type | Default | Allowed | Notes |
|---|---|---|---|---|
| `name` | text 1-24 | required | | Shown on the sheet and in announcements. |
| `description` | text 0-200 | `""` | | Sheet only; the mod adds the numbers it computed. |
| `form` | word | required | section 10.1 | Who is affected and how the ability gets there. |
| `range` | blocks | by form | by form | How far the target may be. |
| `radius` | blocks | by form | by form | Area size, or splash around the hit. |
| `count` | whole 2-5 | by form | `volley`, `chain` | Projectiles in a volley; targets in a chain. |
| `angle` | degrees 30-120 | 70 | `cone` | Cone width. |
| `width` | blocks 1-3 | 1 | `line` | Line width. |
| `hits` | whole 1-5 | 1 | `melee`, `bolt`, `nova`, `cone`, `line` | The effects land this many times. |
| `hit_interval` | seconds 0.2-1 | 0.3 | with `hits` | Time between hits. |
| `projectile` | word | `arrow` | Appendix B.6 | `shot`, `volley`. Look and flight only; what it does is the effects. |
| `zone_at` | `target`, `self`, `follow_self` | `target` | `zone` | Where the area is; `follow_self` moves with the hero. |
| `delay` | seconds 0-3 | 1 at the target, else 0 | `zone` | Warning time before the first pulse: the circle shows, players can step out. |
| `duration` | seconds 0-10 | 0 | `zone` | 0 = one burst; otherwise it pulses for this long. |
| `interval` | seconds 0.5-3 | 1 | `zone` | Time between pulses. Pulses = 1 + floor(duration / interval). |
| `windup` | seconds 0-2 | skills 0, ultimate 0.5, leap 0.3 | | The hero stops and gathers (look particles, sound, pose). |
| `cooldown` | seconds 2-120 | 8 | skills only | Raised to the cooldown floor if too short (section 15). |
| `charges` | whole 1-3 | 1 | skills only | Uses stored; each recharges over `cooldown`. |
| `energy_gain` | whole 0-30 | 10 | skills only | Energy the hero gains when it uses the skill. |
| `charge_rate` | whole % 50-150 | 100 | ultimate only | Multiplies every energy gain. |
| `use_when` | word | by form | section 10.4 | When the AI uses it. |
| `use_value` | number | by `use_when` | by `use_when` | The one number `use_when` needs. |
| `unlock_level` | whole 1-30 | 1 | | The hero gains it at this level (section 22.3). |
| `effects` | 1-5 effects | required | section 13 | Applied in order to each affected entity. |
| `look` | object | none | section 10.5 | Particles, colour, sound, pose, visual lightning, visual fangs. |
| `line` | text 1-120 | none | | Said when it is used (rate-limited). |

A field that the form does not use gets a NOTE ("radius is not used by form self") and is ignored.

### 10.1 Forms

| `form` | Who is affected | `range` (default) | `radius` (default) | Other |
|---|---|---|---|---|
| `melee` | the target in reach (3.5 blocks); radius > 0 adds enemies that close to the target | fixed 3.5 | 0-3 (0) | A real swing that replaces the next basic hit; shields block it like any frontal hit. `hits`. |
| `shot` | the enemy one projectile hits; radius > 0 adds enemies that close to the impact | 4-24 (16) | 0-4 (0) | `projectile`. |
| `volley` | each enemy hit by one of `count` projectiles in a fan (10° apart) | 4-24 (16) | 0-3 (0) splash | `count` 2-5 (3), `projectile`. |
| `bolt` | the target, instantly (needs line of sight); radius > 0 adds enemies that close to it | 4-20 (12) | 0-3 (0) | A particle line from the hero. `hits`. |
| `chain` | the target, then up to `count` - 1 more enemies, each within `radius` of the last, never the same twice | 4-16 (10) | 2-6 (4) | `count` 2-5 (3). Instant; 0.15 s per jump. |
| `cone` | enemies in front of the hero within `range` and `angle` | 2-8 (5) | - | `angle`, `hits`. |
| `line` | enemies along a line toward the target; stops at the first solid block | 4-16 (8) | - | `width`, `hits`. |
| `nova` | every enemy within `radius` of the hero | - | 2-8 (4) | `hits`. |
| `zone` | every enemy within `radius` of `zone_at`, after `delay`, each pulse | 4-20 (12) at the target | 2-6 (3) | `zone_at`, `delay`, `duration`, `interval`. At most 2 zones per hero; the oldest ends. |
| `leap` | the hero jumps to the target (a physical arc) and, on landing, every enemy within `radius` | 3-10 (8) | 1-4 (2.5) | Not from water or a ladder; lands short against a wall; no fall damage from its own leap. |
| `dash` | the hero dashes toward the target along the ground (a 0.5 s push) and every enemy within 1.5 blocks of its path | 3-8 (6) | - | Stops at walls and drops. |
| `self` | the hero | - | - | |
| `ally` | the most hurt ally within `range` (may be the hero) | 4-16 (10) | - | |
| `allies` | the hero and every ally within `radius` | - | 4-12 (8) | |

**Enemies** are exactly what `FactionNpc.isEnemy` says: hostile monsters, NPCs of hostile factions (pacts respected),
enemy players (never creative or spectator), and enemy summons. **Allies** are what `CombatRules.sameSide` says: the
hero's faction and pact allies, players included, plus own summons. Animals, villagers, iron golems that are not
summons, and bystanders are never affected.

`self`, `ally` and `allies` are **friendly forms**; the others are **enemy forms**.

### 10.2 Who receives each effect: `to`

Every effect may say who receives it:

| `to` | Receiver |
|---|---|
| `targets` (default) | Whoever the form affects. |
| `self` | The hero (once per use). |
| `allies` | In `nova`, `zone` and `leap`: allies inside the same area. Otherwise: the hero and allies within the effect's `radius` (2-12, default 8) of the hero. |
| `ally` | The most hurt ally within the effect's `radius` (default 8), possibly the hero. |

An ally-only effect (section 13) in an enemy form without `to` goes to the hero, with a NOTE. This is how "hit them
and shield myself" is written.

### 10.3 Hits, pulses and timing

- With `hits` > 1, every effect except the self-only ones (`move_self`, `cooldown_cut`, `empower`, `summon`) lands
  on each hit, `hit_interval` apart. Amounts are per hit.
- A lasting `zone` applies its effects to everyone inside at each pulse.
- A **windup** of 0.75 s or more shows a telegraph (a particle ring for areas, a line for lines and dashes, the cone's
  outline) so players can dodge. A windup above 0.5 s is cancelled by a stun or a knockback above 0.5; half the
  cooldown is refunded.
- The target dying or leaving range during the windup cancels the ability; the cooldown is not spent.

### 10.4 `use_when` (when the AI uses an ability)

| `use_when` | `use_value` (range, default) | Used when the ability is ready and ... |
|---|---|---|
| `always` | - | a valid target is in range (friendly forms: while fighting). |
| `self_health_below` | % 10-90 (50) | the hero is under that share of health. |
| `ally_health_below` | % 10-90 (60) | an ally in reach is under that share. |
| `target_health_below` | % 10-90 (30) | the target is under that share (a finisher). |
| `enemies_near` | count 2-6 (2) | at least that many enemies are in the ability's area (its radius, or 6 blocks around the target for single-target forms). |
| `target_far` | blocks 4-16 (6) | the target is farther than that (a gap closer). |
| `target_close` | blocks 2-6 (3) | an enemy is closer than that (an escape). |
| `big_fight` | - | 3 or more enemies within 12 blocks, or a target with 40+ max health or a hero or boss, or the hero under half health. |
| `out_of_combat` | - | no enemy for 5 s (heals and buffs between fights). Friendly forms only; otherwise read as `always` (ADJUSTED). |

Defaults: `ally` and `allies` forms → `ally_health_below` 60; `self` → `always`; other skills → `always`; the
ultimate → `big_fight`. `/fc hero info` prints the use rule the mod settled on.

**How the AI picks** (`HeroAbilityGoal`, every 5 ticks while fighting):
1. Nothing starts while the hero is stunned, eating, casting, withdrawing (except a heal on itself or an ally),
   swimming (no `leap` or `dash`), on a scaffold pillar, or within the **global cooldown** (1 s after any ability).
2. The ultimate first: energy 100, unlocked, its `use_when` holds, target in range.
3. Then the skills in file order: the first unlocked skill with a charge ready whose `use_when` holds and whose
   target is valid and in range and sight.
4. Otherwise the basic attack, and movement toward `keep_distance`.

### 10.5 `look`

| Field | Allowed | Default |
|---|---|---|
| `particle` | Appendix B.2, or `dust` | the element's particle |
| `color` | `"#RRGGBB"` (for `dust`) | the hero's `color` |
| `sound` | Appendix B.3 | none |
| `pose` | `none`, `swing`, `block`, `bow_draw`, `spear_throw`, `crossbow_charge`, `horn`, `spyglass` | `swing` for melee forms, `none` otherwise |
| `lightning` | `true`/`false`; ultimates only | `false`. A visual-only lightning bolt at the target or the hero: no fire, no damage. |
| `fangs` | `true`/`false`; `line`, `nova`, `zone` | `false`. Vanilla evoker fangs rise along the line or the ring, as a visual (their own damage is cancelled). |

Particles play during the windup, along the path of `bolt`, `chain`, `line` and `dash`, as a projectile trail, and on
each affected entity. Particle counts are fixed by the mod, so a file cannot flood clients. Poses use vanilla's own
`HumanoidModel.ArmPose` values (block, bow and arrow, spear throw, crossbow charge, horn, spyglass), held for the
windup or 0.5 s.

---

## 11. Conditions (`if`)

Any effect, and any passive, may carry one condition: `"if": { "condition": "...", ... }`. For an effect it is
checked per receiver; for a passive when the trigger fires (the target is the trigger's enemy, if any).

| `condition` | Needs | True when |
|---|---|---|
| `target_health_below` / `target_health_above` | `value` % | the receiver's health share is under / over the value. |
| `self_health_below` / `self_health_above` | `value` % | the hero's health share. |
| `target_has_mark` | `mark`, `value` = least stacks (default 1) | the receiver carries that many stacks of the mark. |
| `self_has_mark` | `mark`, `value` | the hero does (self marks work as a personal resource). |
| `target_burning` / `target_freezing` / `target_slowed` / `target_stunned` | - | on fire / any freeze build-up / has Slowness / stunned. |
| `target_has_dot` | - | carries any hero damage over time. |
| `target_is` | `kind`: `monster`, `undead`, `arthropod`, `illager`, `player`, `npc`, `hero`, `boss` | vanilla entity tags (`#undead`, `#arthropod`, `#illager`), the mod's classes, or the boss list (ender dragon, wither, warden, elder guardian). |
| `self_empowered` | - | the hero is in its empowered state. |
| `self_shielded` | - | the hero has absorption above 0. |

A conditional effect counts three quarters in the cooldown weight (section 15).

---

## 12. Amounts

`damage`, `dot`, `heal` and `shield` take an **Amount**: a plain number of health points, or an object whose parts
are added together.

```json
"amount": 6
"amount": { "attack_percent": 120 }
"amount": { "base": 2, "armor_percent": 20 }
"amount": { "base": 1, "mark": "toll", "per_mark_stack": 1.5 }
```

| Part | Range | Adds |
|---|---|---|
| `base` | 0-30 | Fixed health points. A bare number means `base`. |
| `attack_percent` | 0-300 | That percent of the hero's attack power (section 6.2): `100` = one weapon hit. |
| `max_health_percent` | 0-40 | Of the **hero's** max health (tank skills, shields). |
| `armor_percent` | 0-100 | Of the hero's armour points (worn + natural). |
| `target_max_health_percent` | 0-20 | Of the **receiver's** max health (heals on allies; damage, at most 2% against bosses). |
| `target_missing_health_percent` | 0-30 | Of the health the receiver is missing (finishers on enemies, emergency heals). |
| `mark` + `per_mark_stack` | id, 0-6 | That many health points per stack of the mark on the receiver (always both). |

The total is then multiplied by ability power (growth per level) and `heroes.powerScale`, and goes through the damage
formula of section 6.3 for damage. A part that is invalid (`attack_percent: 500`) is clamped (ADJUSTED).

**Reference value.** For caps and cooldown floors (sections 15 and 16) the mod evaluates an Amount at level 1 with
the reference attack power, the hero's own `stats.health`, armour = `stats.armor` + 15 (full iron), a receiver with
20 max health missing 10, and the mark at its `max_stacks` from this file (5 if the file never applies it).

---

## 13. Effect catalogue

Every effect is `{ "type": "<name>", ...its fields }` with these optional common fields:

| Common field | Default | Meaning |
|---|---|---|
| `to` | `targets` | Section 10.2. |
| `radius` | 8 | Only with `"to": "allies"` or `"ally"`: 2-12 blocks. |
| `chance` | 100 | Whole % 1-100, rolled per receiver. |
| `if` | none | A condition (section 11), checked per receiver. |

**Recipient classes.** *Enemy-only* effects (`damage`, `dot`, `knockback`, `pull`, `ignite`, `freeze`, `stun`,
`taunt`, `dispel`, a harmful `effect` or `stat`) only reach enemies: they are PART LEFT OUT in friendly forms or with
any `to` other than `targets`. *Ally-only* effects (`heal`, `shield`, `cleanse`, `energy`, a helpful `effect` or
`stat`) only reach allies. *Self-only* effects (`cooldown_cut`, `move_self`, `empower`, `summon`) always act on the
hero and ignore `to`. `mark` and `clear_marks` reach enemies or, with `"to": "self"`, the hero.

### 13.1 Damage

| `type` | Fields (range, default) | What it does | Implementation |
|---|---|---|---|
| `damage` | `amount` Amount (required); `element` (hero's); `drain` % 0-50 (0) | Hurts each receiver through the formula of 6.3. `drain` heals the hero by that share of the damage really dealt. At most 2 `damage` effects per ability. | `HeroDamage.hit`; drain = health before/after, `hero.heal` |
| `dot` | `amount` Amount per second (required); `seconds` 2-10 (4); `element` (hero's); `max_stacks` 1-5 (1) | Damage over time, one tick per second, using the element's `_dot` type (no knockback). With `max_stacks` 1 a new application refreshes; above 1 each application adds a stack (damage × stacks, duration refreshed). Fire shows the target burning, frost adds freeze build-up, others show element particles. | `HeroStatus` DoT list ticked by `HeroStatusTicker`; per hero and target at most `heroes.maxDotPerSecond` (6) |
| `ignite` | `seconds` 1-8 (4) | Sets the receiver on fire (vanilla fire: water puts it out, Fire Resistance ignores it). Never blocks. | `igniteForSeconds` |
| `freeze` | `seconds` 1-6 (3) | Keeps the receiver fully frozen as powder snow does: vanilla slowing, frost overlay, freeze damage. Leather armour and freeze-immune mobs ignore it (vanilla). | `HeroStatus` holds `setTicksFrozen(required + 2)` |

### 13.2 Healing and protection

| `type` | Fields | What it does | Implementation |
|---|---|---|---|
| `heal` | `amount` Amount (required) | Heals each receiver, whatever its hunger bar says. Counts for the hero's energy and XP. | `LivingEntity.heal` |
| `shield` | `amount` Amount (required); `seconds` 2-30 (10) | Absorption (golden hearts) for `seconds`; at most 60% of the receiver's max health; a stronger shield replaces a weaker one, they never add. | transient `MAX_ABSORPTION` modifier + `setAbsorptionAmount`; `HeroStatus` removes what is left at expiry |
| `cleanse` | - | Removes harmful potion effects, hero DoTs, enemy marks, fire, freezing and stuns. | `removeEffect`, `clearFire`, `setTicksFrozen(0)`, `HeroStatus` |

### 13.3 Potion effects and stat modifiers

| `type` | Fields | What it does | Implementation |
|---|---|---|---|
| `effect` | `id` (required, Appendix B.1); `level` 1-3 (1); `seconds` 1-30 (required) | A vanilla potion effect, capped per effect (level and seconds, Appendix B.1). Helpful ids reach allies, harmful ids enemies. `invisibility` is self only. Vanilla immunities apply. | `addEffect(new MobEffectInstance(holder, ticks, level - 1), hero)` |
| `stat` | `stat` (required); `percent` (required, whole, not 0); `seconds` 2-30 (8); `max_stacks` 1-5 (1) | A temporary percent modifier (table below); works on heroes, NPCs, players and mobs. Repeated uses refresh it, or add stacks up to `max_stacks`. Whether it is helpful or harmful follows from the stat and the sign. | transient `AttributeModifier` with id `factionclash:hero_stat/<hero>/<slot>`, or `HeroStatus.stats` read by the damage and heal events |

| `stat` | Range | Helpful when | Applied through |
|---|---|---|---|
| `attack` | -30..+40 | positive | `ATTACK_DAMAGE` × (1 + p) |
| `damage_dealt` | -30..+40 | positive | all damage the entity deals (`LivingIncomingDamageEvent`, by source entity) |
| `damage_taken` | -40..+30 | negative | all damage the entity takes |
| `armor` | -50..+50 | positive | `ARMOR` × (1 + p) |
| `speed` | -40..+40 | positive | `MOVEMENT_SPEED` × (1 + p) |
| `attack_speed` | -30..+30 | positive | `ATTACK_SPEED` × (1 + p) |
| `healing_received` | -50..+50 | positive | `LivingHealEvent` |
| `knockback_resistance` | 0..+60 | positive | `KNOCKBACK_RESISTANCE` + p / 100 |
| `crit_damage` | 0..+50 | positive | jump critical hits: `FactionNpc.doHurtTarget`'s crit modifier; players via NeoForge `CriticalHitEvent` |
| `energy_gain` | 0..+50 | positive | heroes only: every energy gain |

### 13.4 Control and movement

| `type` | Fields | What it does | Implementation |
|---|---|---|---|
| `knockback` | `strength` 1-5 (2); `up` blocks 0-3 (0) | Pushes away from the hero, optionally upward. Knockback resistance applies. `up` only lifts a receiver standing on the ground, so multi-hit abilities cannot juggle anyone into fall damage. | `LivingEntity.knockback(0.4 × strength, dx, dz)` + a vertical impulse; `hurtMarked` for players |
| `pull` | `strength` 1-3 (1) | Pulls toward the hero. | `setDeltaMovement` toward the hero, `hurtMarked` |
| `stun` | `seconds` 0.5-2 (1) | Mobs and NPCs cannot move, attack or cast. Players get Slowness IV and Mining Fatigue II instead (they keep control). Bosses are immune. After a stun the receiver cannot be stunned again for twice the duration. At most 2 s of stun per skill, 3 per ultimate. | `StunnedGoal` (priority 0, MOVE/LOOK/JUMP) added to goal-based mobs on join; NPCs check `HeroStatus`; brain-based mobs (piglins, hoglins, villagers) get Slowness V |
| `taunt` | `seconds` 1-8 (3) | Monsters and enemy NPCs target the hero and keep re-targeting it. Players are not forced; the hero glows for them. Bosses and brain-based mobs ignore it. | `Mob.setTarget(hero)` re-asserted each second by `HeroStatus` |
| `dispel` | - | Removes helpful potion effects (absorption included) and positive hero `stat` modifiers from enemies. | `HeroStatus` + `removeEffect` |
| `move_self` | `direction` `back` / `forward` / `up` (`back`); `distance` 2-8 (4) | A physical hop of the hero, never a teleport; walls stop it. At most one per ability; not in `leap` or `dash`. | velocity impulse, `resetFallDistance` |

### 13.5 Resources, marks, the empowered state and summons

| `type` | Fields | What it does |
|---|---|---|
| `energy` | `amount` 5-30 (required) | Gives ultimate energy to the receiving heroes (or with `"to": "self"` the hero). Not in an `on_ultimate` passive. |
| `cooldown_cut` | `seconds` 1-10 (required) | Shortens every running skill cooldown of the hero by that much, but never to less than half of that skill's full cooldown since its last use, so cooldown floors still hold (at most twice the rate). |
| `mark` | `mark` id (required); `stacks` 1-5 (1); `max_stacks` 1-10 (5); `seconds` 2-30 (10, refreshed by new stacks); `glow` (false; true: the receiver glows for everyone while marked) | Puts named, stacking marks on enemies, or on the hero with `"to": "self"`. Reaching `max_stacks` fires the hero's `on_mark_full` passives (section 17). |
| `clear_marks` | `mark` (required) | Removes that mark from the receivers. Placed after a `damage` with `per_mark_stack`, it makes a "spend the stacks" finisher. |
| `empower` | `seconds` 3-20 (required) | The hero enters its `empowered` state (section 18). Needs `empowered` at the top level, else PART LEFT OUT. |
| `summon` | `creature` `wolf` / `snow_golem` / `iron_golem` (required); `count` 1-3 (1; snow golems at most 2, iron golems 1); `seconds` 10-60 (20); `name` 1-24 | Vanilla creatures that fight for the hero's side for `seconds` (section 20). Only in skills and the ultimate (in a passive or the basic attack: PART LEFT OUT). Off when `heroes.summons = false`. |

### 13.6 Left out on purpose

Teleport and blink (NPCs only teleport in stuck recovery; use `dash`, `leap`, `move_self`), block changes (no placing,
breaking, igniting or exploding blocks; fire and lightning touch entities only), instant kills, mind control,
resurrecting enemies, healing enemies, damaging allies, flight, transformation into other models, commands and
arbitrary entities. Section 33 says why and how a later format could add some.

---

## 14. Energy and the ultimate

- Energy runs from 0 to 100. The ultimate needs 100 and uses all of it.
- Gains (each multiplied by the ultimate's `charge_rate` and the `energy_gain` stat):

| Event | Energy |
|---|---|
| a basic attack lands | 5 |
| it uses a skill | the skill's `energy_gain` (10) |
| it takes damage | 1 per 2 health points lost, at most 8 per hit |
| it kills an enemy | 10 |
| it heals or shields allies | 1 per 2 points restored, at most 8 per use |
| an `energy` effect | its `amount` |

- No decay: a hero keeps its charge between fights; the ultimate's `use_when` (`big_fight` by default) keeps it from
  being spent on a lone zombie. A reload keeps it; a revival starts at 0.
- At least `clamp(weight, 20, 60)` seconds between two ultimates of one hero, whatever the energy (weight as in
  section 15; PROPOSED). This guards against energy loops between heroes and keeps a big area ultimate from firing
  every 20 s in a long fight: the examples get 27 s (Hold the Gate), 45 s (Thornfall) and 32 s (Spring Tide).
- Using the ultimate fires the hero's `on_ultimate` passives and every other ally hero's `on_ally_ultimate` passives
  within 24 blocks.
- Shown on the name tag while fighting (`★ Bram Ostler · the Gatewarden [Fighting 80%]`, `heroes.showEnergy`) and on
  the hero's boss bar (section 25.8), which turns gold when the ultimate is ready.

A typical fight: about 6 basic hits and 2 skills (30 + 20), plus damage taken, fills the bar in one long fight or two
short ones.

---

## 15. Cooldowns and the cooldown floor

- Per skill, in game ticks, saved as "ready at" game times, so a restart never resets or skips a cooldown.
- **Global cooldown**: 1 s between any two abilities of one hero (`heroes.globalCooldown`).
- **The floor.** The mod computes a skill's **weight** and raises a too-short `cooldown` to
  `(2 + weight / 2) × (1 + 0.5 × (charges - 1))` seconds (ADJUSTED: "cooldown 2 s is too short for this skill;
  raised to 9 s"). Authors never do this arithmetic; `/fc hero check` prints each weight and floor.

Weight = the sum over the ability's effects of the table below, each × A (who it reaches) × hits or pulses (not for
self-only effects) × `chance` / 100 × 0.75 if it has an `if`; plus 2 for a `leap` or `dash` form.

| Effect | Weight |
|---|---|
| `damage` | reference Amount |
| `dot` | reference Amount × seconds × 0.75 |
| `heal` | reference Amount × 1.25 |
| `shield` | reference Amount |
| `effect` | level × seconds / 4 |
| `stat` | \|percent\| / 10 × seconds / 4 |
| `knockback`, `pull` | strength / 2 |
| `ignite`, `freeze`, `taunt` | seconds / 2 |
| `stun` | seconds × 4 |
| `cleanse`, `dispel` | 1 |
| `energy` | amount / 5 |
| `cooldown_cut` | seconds |
| `move_self` | 1 |
| `mark` | 0.5 × stacks |
| `empower` | seconds / 4 × (1 + Σ \|empowered stat percent\| / 20) |
| `summon` | count × seconds / 10 × (wolf 1, snow golem 0.5, iron golem 3) |

A = 1 when the effect reaches one entity (`melee`, `shot`, `bolt` without radius, `dash`, `self`, `ally`, `to: self`,
`to: ally`); 1.5 when it can reach several (`nova`, `cone`, `line`, `zone`, `chain`, `leap`, `allies`, any splash
radius, `to: allies`); 0.75 × `count` for `volley`.

The ultimate has no cooldown: its slot caps, the energy rules and the minimum gap of section 14 limit it. Passive
floors are in section 17.4. The
example heroes' weights and floors (from the reference checker):

| Ability | Weight | Floor | Given |
|---|---|---|---|
| Bram Ostler: Shield Slam | 8.8 | 6.4 s | 9 s |
| Bram Ostler: Gate Call | 10.2 | 7.1 s | 14 s |
| Bram Ostler: Brace | 5.5 | 4.8 s | 16 s |
| Sela Quickfern: Pinning Shot | 10.9 | 7.5 s | 10 s |
| Sela Quickfern: Thornfan | 19.5 | 11.8 s | 14 s |
| Sela Quickfern: Slip Away | 2.5 | 3.2 s | 10 s |
| Maudie Brine: Mending Draught | 7.5 | 5.8 s | 8 s |
| Maudie Brine: Clear Water | 3.4 | 3.7 s | 18 s |
| Maudie Brine: Undertow | 4.8 | 4.4 s | 11 s |

---

## 16. Balance caps

### 16.1 Slot caps (level-1 reference health points, per use and per receiver; PROPOSED)

| Slot | Damage (all `damage` × hits) | DoT (amount × seconds, one application) | Heal | Shield | Stun seconds |
|---|---|---|---|---|---|
| basic attack `on_hit` (per hit), empowered `on_hit` | 4 | 4 | 2 | 2 | not allowed |
| basic attack `every_nth_hit` | 8 | 8 | 4 | 4 | 1 |
| skill | 16 | 16 | 8 | 8 | 2 |
| ultimate | 30 | 24 | 14 | 12 | 3 |
| passive | 8 | 8 | 4 | 4 | 1 |

- A lasting zone may deal or heal up to 1.5 × the slot cap per receiver over all its pulses (enemies can step out).
- Over a cap, every Amount part of that kind in the ability is scaled down in proportion (ADJUSTED, "Shield Slam deals
  up to 44 at level 1; the skill cap is 16; scaled down to 16").
- Effects per ability 1-5, per passive 1-3, per `on_hit` 1-3, per finisher 1-3, per empowered `on_hit` 1-2.
- The basic `on_hit` and the empowered `on_hit` share one per-hit cap: while empowered, both lists together stay
  within the `on_hit` row.

### 16.2 Other caps

- Potion levels and seconds per effect: Appendix B.1.
- Stat modifiers: the ranges in section 13.3; `always` passives and the empowered state: ±20% and ±30%.
- Control: stun as above with diminishing returns; taunt at most 8 s; one hero's DoTs on one target at most 6 health
  points per second (`heroes.maxDotPerSecond`).
- One hero hit at most `heroes.maxHit` (40); `target_max_health_percent` at most 2% against bosses.
- Summons: at most 3 alive per hero (at most 1 of them an iron golem), 12 per faction; only skills and the ultimate
  summon.
- Rates the file controls directly: `thrown` basic attacks at most 5 damage per second (section 9); `cooldown_cut` never
  more than halves a skill's cooldown (13.5); `every` passives wait at least their passive floor (17.4); ultimates at
  least 20-60 s apart (14).
- Gear and buffs: Amounts use at most 1.5 × the reference attack power and armour (6.2); the damage factors together at
  most double a hit (6.3).

### 16.3 Server-wide tuning

- `heroes.powerScale` (100%, 25-200): multiplies every Amount of every hero after the caps. The owner tunes all heroes
  at once without editing files.
- `heroes.maxPerFaction` (4): a faction holds at most this many heroes (starter and tokens).
- A hero's war-council weight is finite (section 25.7), so a garrison still matters.

---

## 17. Passives

A passive is set off by an event (its **trigger**), has an internal cooldown, and delivers 1-3 effects.

| Field | Type | Default | Allowed |
|---|---|---|---|
| `name` | text 1-24 | required | |
| `description` | text 0-200 | `""` | |
| `trigger` | word | required | section 17.1 |
| `trigger_value` | number | by trigger | by trigger |
| `mark` | mark id | - | required for `on_mark_full` |
| `chance` | whole % 1-100 | 100 | |
| `cooldown` | seconds 1-120 | 1 | raised to the passive floor (17.4) |
| `form` | word | by trigger | section 17.2, allowed per trigger |
| `radius` | blocks 2-12 | `nova` 4, `allies` 8 | |
| `projectile` | Appendix B.6 | `arrow` | `shot` form |
| `if` | condition | none | checked when the trigger fires |
| `effects` | 1-3 effects | required | slot `passive` caps |
| `look`, `line`, `unlock_level` | | | as for abilities |

### 17.1 Triggers

| `trigger` | `trigger_value` (range, default) | Fires when | Allowed `form` (default first) |
|---|---|---|---|
| `always` | - | continuously, while its `if` holds (checked every second) | `self` (effects: `stat` without `seconds`, ±20%) |
| `on_hit` | - | its basic attack lands | `trigger_enemy`, `self`, `nova`, `allies`, `bolt`, `shot` |
| `on_crit` | - | its melee jump critical hit lands | same as `on_hit` |
| `on_hurt` | - | an enemy damages it | `trigger_enemy` (the attacker), `self`, `nova`, `allies`, `bolt`, `shot` |
| `on_kill` | - | it kills an enemy | `self`, `nova`, `allies` |
| `on_skill` | - | it uses any skill | `self`, `ally`, `allies`, `nova` |
| `on_ultimate` | - | it uses its ultimate | `self`, `allies`, `nova` (no `energy`) |
| `on_fight_start` | - | it gets a target after 10 s without one | `self`, `allies`, `nova` |
| `on_low_health` | % 10-60 (35) | its health drops below the value (re-arms above value + 20) | `self`, `allies`, `nova` |
| `on_ally_low_health` | % 10-60 (35) | an ally within 12 blocks drops below it (once per ally per 20 s) | `trigger_ally`, `allies`, `self` |
| `on_ally_hurt` | - | an ally within 12 blocks is damaged by an enemy | `trigger_ally`, `trigger_enemy` (the attacker), `self`, `bolt`, `shot` |
| `on_ally_attack` | - | an ally (NPC, hero or player) within 12 blocks hits an enemy with a weapon or arrow: the hero **joins the attack** on that enemy | `trigger_enemy` (that enemy), `bolt`, `shot` |
| `on_ally_ultimate` | - | another hero of its side uses its ultimate within 24 blocks | `self`, `allies`, `trigger_ally` |
| `on_mark_full` | - (`mark` required) | a mark this hero applies reaches its `max_stacks` on an enemy (or on the hero) | `trigger_enemy` (the marked one), `self`, `nova` |
| `every` | seconds 3-30 (10) | every that many seconds while fighting | `self`, `ally`, `allies`, `nova` |

Permanent bonuses are `stats`, not passives. Synonyms (ADJUSTED): `on_attack` → on_hit; `on_damaged`, `when_hit`
→ on_hurt; `on_cast` → on_skill; `periodic`, `interval` → every; `on_combat_start` → on_fight_start; `follow_up`
→ on_ally_attack.

### 17.2 Passive forms

| `form` | Receivers |
|---|---|
| `self` | the hero |
| `trigger_enemy` | the enemy of the event (the one hit, the attacker, the marked one) |
| `trigger_ally` | the ally of the event (the hurt or low ally, the ally who used an ultimate) |
| `ally` | the most hurt ally within `radius` |
| `allies` | the hero and allies within `radius` |
| `nova` | enemies within `radius` of the hero |
| `bolt` / `shot` | an instant bolt / a projectile at the event's enemy (or the hero's target), range 16 |

`self`, `trigger_ally`, `ally` and `allies` are friendly; `trigger_enemy`, `nova`, `bolt`, `shot` are enemy forms. The
`to` and recipient-class rules of section 13 apply.

### 17.3 The loop guard

Damage from passives and summons never fires `on_hit`, `on_crit` or `on_ally_attack` passives of any hero, so two
heroes whose passives answer each other's attacks cannot ping-pong. A passive's effects never fire that same passive. At most 8
passive firings per hero per second; the excess is dropped with one log warning.

### 17.4 Passive floors

The internal cooldown is raised to at least `max(1, weight × chance / 100 / 2)` seconds (weight as in section 15),
at least 2 s for `on_ally_attack`. `every` has no cooldown of its own: its `trigger_value` (3-30 s) is the period,
raised to the same floor (ADJUSTED), so "8 damage to every enemy around me every 3 s" becomes every 6 s.

---

## 18. The empowered state

An optional temporary mode: an enhanced form, a defensive stance, a battle trance. The `empower` effect enters it for
its `seconds`; entering again refreshes it.

| Field | Type | Default | Allowed / meaning |
|---|---|---|---|
| `name` | text 1-24 | required | Announced, shown on the sheet and the name tag. |
| `description` | text 0-200 | `""` | |
| `stats` | 1-4 `{ "stat", "percent" }` | none | Stat modifiers while empowered, each ±30% (section 13.3 list). |
| `on_hit` | 1-2 effects | none | Added to the basic attack's `on_hit` while empowered (on-hit caps). |
| `particle` | Appendix B.2 | the element's | An aura repeated every 0.5 s on the hero. |
| `color` | `"#RRGGBB"` | hero colour | For `dust`. |
| `size` | whole % 100-110 | 100 | The hero grows while empowered (`generic.scale`); `stats.size` × this is capped at 110%, so it still fits 2-high tunnels (NOTE when the cap applies). |
| `skills_locked` | boolean | `false` | When true, only basic attacks are used while empowered (a berserk state); the ultimate stays available. |
| `line` | text 1-120 | none | Said on entering. |

The condition `self_empowered` lets skills and passives behave differently in the state. The AI does not cast an
ability whose only effect is `empower` while already empowered.

---

## 19. Marks

- A mark is a named, stacking tag (id, stacks, expiry, who applied it) kept in `HeroStatus`, a transient NeoForge
  data attachment on any living entity (not saved; rebuilt empty on load).
- **Mark ids are global.** Two heroes that use the same id share it on purpose: one brands targets with `pinned`,
  another hero's passive reacts to `pinned`. The checker notes a mark that no loaded hero applies.
- At most 32 statuses (marks, DoTs, stat modifiers, shields, control) per entity; the oldest goes first.
- Marks show as small particles above the head every second; with `glow` the vanilla Glowing effect is used.

---

## 20. Summons

- `wolf`, `snow_golem` and `iron_golem`: real vanilla mobs spawned next to the hero. Each gets persistent data
  `factionclash:summon = { owner, faction, expires }`. Its target goals are replaced by "enemies of the faction" (the
  NPC guards' predicate) plus "follow the hero" (leash 16 blocks).
- `CombatRules.factionOf` returns the summon's faction, so `sameSide`, the friendly-fire cancel in
  `FCEvents.onIncomingDamage`, sweeps and alarms treat it as an ally. Own guards never attack it.
- It vanishes with a puff of smoke when its time runs out, when the hero dies, or beyond 48 blocks from the hero. It
  never drops loot or experience and never counts for the war council or recruitment.
- `FCEvents.onJoin` re-adopts summons after a chunk reload, or discards them when expired or ownerless.
- Snow golems leave no snow trail: their mob-griefing check (`EventHooks.canEntityGrief`, NeoForge's
  `EntityMobGriefingEvent`) is denied for summons, so no block changes. They still melt in warm biomes, as in vanilla.
- At most one iron golem alive per hero; a new one replaces the old one.
- Off with `heroes.summons = false` (the effect is then PART LEFT OUT with a message saying so).

---

## 21. Base abilities (non-combat)

They work while the hero is alive, inside its home base's walls or within 16 blocks of them, and not on a raid. Each
may have a `name` (1-24) for the sheet and an `unlock_level`.

| `type` | Fields (range, default) | What it does | Hook |
|---|---|---|---|
| `aura` | `affix` (required: `diligence`, `prospector`, `regrowth`, `green_thumb`, `mason`, `marksman`, `thrifty`, `hardy`, `duelist`, `angler`, `haggler`); `stacks` 1-3 (1); `radius` 8-32 (24); `roles` (all; a list of role ids) | NPCs of those roles within `radius` count `stacks` extra stacks of the affix (the affix's own cap and job rule still apply): faster digging and building, extra ore and crops, instant regrowth, less wear and hunger, better trades. | `FactionNpc.affix(a)` adds the highest aura of each kind in reach, never the sum of several heroes; cached per base every 40 ticks |
| `mend` | `percent` 1-10 (5); `every` seconds 5-30 (10); `radius` 8-24 (16) | Out of combat, heals hurt allies within `radius` by `percent` of their max health every `every` seconds (heart particles). Players too. | `HeroAuras` |
| `lookout` | `radius` 16-48 (32) | Enemies the hero can **see** within `radius` raise the base defence alarm and give a faction chat line ("Sela Quickfern spots 3 enemies to the east", at most once a minute). It never reveals the map: only line of sight. | `FactionDirector.defenceAlarm` path |
| `rally` | `percent` 5-25 (10) | Recruits at the hero's base cost that much less food. | `Recruitment.canAfford` / `recruit`: `needFood = round(foodValue × (1 - rally))` |
| `drill` | `xp` 1-3 (1); `radius` 8-24 (16) | Soldiers within `radius` at the base gain `xp` work XP per in-game hour (1000 ticks) by day. | `FactionNpc.awardXp` |

Affixes that are attributes or scouting range (vitality, swiftness, might, toughness, pathfinder) are not allowed in
auras (PART LEFT OUT: "use a passive or a stat instead"). Synonyms: `work_aura`, `buff_workers` → aura;
`heal_between_fights`, `medic`, `regen` → mend; `watch`, `sentry` → lookout; `leadership`, `recruit_discount` → rally;
`training`, `drill_master` → drill.

---

## 22. Progression

### 22.1 Levels and XP

Heroes use the NPC curve (`AffixRoller`: level n → n+1 costs 40 + 30n, max level 30) and earn work XP from their duty
exactly like other NPCs, plus combat XP (PROPOSED, `heroes.xpRate` 100%):

| Source | XP |
|---|---|
| a kill | 10 + the victim's max health / 2 (a zombie 20) |
| using the ultimate | 5 |
| healing or shielding allies | 1 per 5 points |
| a won raid it survived | 100 |
| a base defence it survived | 30 |
| each in-game day alive | 40 |

About 300 XP a day in an active war: level 10 after about 6 days, 20 after 22, 30 after 47. Every level also rolls one
affix, as for any NPC, from its duty job (`AffixRoller.roll(role.affixJob, ...)`). Level, XP and affixes live in the
saved hero state, so they survive death and revival.

### 22.2 Growth

Section 5.3. Effective numbers are printed by `/fc hero info` and `/fc hero check` at level 1, the current level and
level 30.

### 22.3 Unlocks

`unlock_level` on skills, the ultimate, passives and base abilities. A locked ability shows as "unlocks at level N" and
is announced when gained (the `unlock` line, then "Sela Quickfern learned Thornfall"). The default (1) gives the full
kit at once.

### 22.4 Duplicate tokens

Using a named token for a hero the faction already has gives that hero XP worth two levels at its current level.

---

## 23. Dialogue (`lines`)

`"lines": { "<event>": ["line", ...] }`: 1-8 lines per event, 1-120 characters each, no line breaks. When the event
happens a random line is said, unless the hero is rate-limited.

| Event | When | Placeholders | Heard by |
|---|---|---|---|
| `summoned` | it joins a faction (start, token, admin) | `{name}`, `{faction}` | the faction |
| `greeting` | a faction player right-clicks it | `{name}`, `{player}`, `{faction}` | that player |
| `idle` | at its base, at most every 15 minutes, a faction player within 12 blocks | `{name}`, `{player}`, `{faction}` | nearby |
| `fight_start` | it gets a target after 10 s without one (50%) | `{name}`, `{enemy}` | nearby |
| `skill` | it uses a skill with no `line` of its own (25%) | `{name}`, `{enemy}`, `{ability}` | nearby |
| `ultimate` | it uses its ultimate with no `line` of its own | `{name}`, `{enemy}`, `{ability}` | nearby |
| `low_health` | its health drops under 30% | `{name}` | nearby |
| `kill` | it kills an enemy (30%) | `{name}`, `{enemy}` | nearby |
| `ally_fallen` | an NPC, hero or player of its side dies within 24 blocks | `{name}`, `{ally}` | nearby |
| `victory` | a fight ends with no enemy left nearby | `{name}` | nearby |
| `raid_start` | its raid party sets out | `{name}`, `{faction}` | the faction |
| `base_attacked` | its base's defence alarm goes off | `{name}`, `{faction}` | the faction |
| `level_up` | it gains a level | `{name}`, `{level}` | the faction |
| `unlock` | it gains an ability by level | `{name}`, `{level}`, `{ability}` | the faction |
| `fallen` | it dies | `{name}` | the faction |
| `revived` | it comes back | `{name}` | the faction |

- "Nearby" = players within `heroes.dialogueRadius` (32) blocks. The line appears in chat as `<★ Bram Ostler> Eyes on
  me!` in the hero colour, and as a vanilla text display above the hero's head for 3 s (`heroes.speechBubbles`; no
  client assets).
- Rate limit: one line per hero per `heroes.dialogueCooldownSeconds` (20); `summoned`, `fallen`, `revived`,
  `ultimate` and `greeting` (5 s) ignore it.
- `{enemy}` is the enemy's name ("Zombie", an NPC's name, a player's name); `{ally}` the fallen one's; `{ability}` the
  ability's `name`.
- An unknown placeholder (`{target}`) leaves that line out (PART LEFT OUT, with the allowed list); a known placeholder
  not available for the event becomes "someone" (NOTE). An unknown event is PART LEFT OUT with a did-you-mean.
  Synonyms: `join`, `recruited` → summoned; `hello`, `talk` → greeting; `death`, `dies` → fallen; `respawn`,
  `return` → revived; `win` → victory; `levelup` → level_up.
- Events with no lines use short built-in generic lines (`heroes.defaultLines`).

---

## 24. Skins

### 24.1 What the PNG must be

A standard Minecraft player skin: a 64×64 PNG with the classic (4 px) or slim (3 px) arm layout, matching
`skin_model`. The outer layer (hat, jacket, sleeves, trousers) may use transparency; the base layer should be fully
opaque. The old 64×32 layout is accepted and converted the way vanilla converts it (ADJUSTED). Anything else (128×128,
32×32, a JPEG renamed to .png, a file over 64 KB) is rejected for the skin only: the hero loads with its faction's
default look and a clear message.

### 24.2 Server side (`logic/hero/SkinCheck`, pure Java)

1. Read at most 64 KB. Check the PNG signature and the IHDR width and height **before** decoding, so a tiny file that
   claims 30000×30000 pixels can never exhaust memory.
2. Decode with `javax.imageio` (works on a headless server); convert 64×32 if needed.
3. Force the base-layer regions (head, body, arms, legs) opaque, as vanilla does for downloaded skins, so holes never
   show as invisible limbs; the outer layer keeps its transparency.
4. Arm check: with `skin_model: classic` and the 4th arm column empty in every row (x 54-55, y 20-31 for the right
   arm), NOTE "this skin looks slim; set skin_model to slim"; the reverse is checked too.
5. Keep the result as 64 × 64 × 4 = 16384 RGBA bytes plus its SHA-256.

### 24.3 Sending to clients (`net/`)

Registered with NeoForge 21.1's `PayloadRegistrar` (`FCNetwork.register`); the `common*` methods serve both the
configuration and the play phase.

| Payload | Direction | Content |
|---|---|---|
| `HeroSkinManifest` | server → client (`commonToClient`) | for each loaded hero: id, SHA-256, slim flag |
| `HeroSkinRequest` | client → server (`commonToServer`) | the hashes it has no cached copy of |
| `HeroSkinData` | server → client (`commonToClient`) | hash + exactly 16384 bytes (`ByteBufCodecs.byteArray(16384)`) |
| `HeroSkinForget` | server → client (`commonToClient`) | hero ids removed by a reload |

- **While connecting**: a `HeroSkinsTask` (a NeoForge `ICustomConfigurationTask`, registered in
  `RegisterConfigurationTasksEvent`)
  sends the manifest, waits for the request, sends the missing skins and finishes (`IPayloadContext.finishCurrentTask`).
  Each skin is about 16 KB, far below the 1 MiB payload limit; a client with a warm cache receives nothing but the
  manifest. The client always answers, with an empty request when it has every skin; if no answer comes within 10 s
  the task finishes anyway and the skins follow in the play phase, so a client can never hang on "Joining world".
- **After `/fc hero reload`**: the same exchange in the play phase for every player; removed heroes get
  `HeroSkinForget`.
- The client never decodes a PNG from the server: it accepts exactly 16384 bytes of pixels whose SHA-256 matches.
- Single player works the same (the integrated server sends to its own client).

### 24.4 Client side (`client/HeroSkinCache`, `client/FactionNpcRenderer`)

- Cache: memory, plus `<game dir>/factionclash/skin_cache/<sha256>.rgba` (16384 bytes, hash checked on read, trimmed
  to 256 files).
- Registration on the render thread: a `NativeImage(64, 64)` filled from the bytes (note `NativeImage` stores ABGR),
  wrapped in a `DynamicTexture`, registered as `factionclash:hero_skin/<sha256>`. The hash is in the name, so a
  changed skin is a new texture and the old one is released.
- Renderer: `FactionNpcRenderer` today is one `HumanoidMobRenderer` with a classic `PlayerModel` and faction textures
  `textures/entity/npc/<faction>_<variant>.png`. It becomes a dispatcher holding two inner renderers, one with
  `PlayerModel(ModelLayers.PLAYER, false)` and one with `PlayerModel(ModelLayers.PLAYER_SLIM, true)`, each with the
  armour layer. An NPC whose synced hero id has a cached skin uses the matching model and the hero texture; otherwise
  today's faction texture and the classic model. The player model's translucent render type shows outer-layer
  transparency as for players.
- Ability poses: a synced byte `DATA_HERO_POSE` sets the model's arm poses during windups (section 10.5).
- A skin not yet received renders the faction look until it arrives. Any client-side failure (decode, missing
  payload, hash mismatch) draws the faction look and logs once. Nothing crashes.

---

## 25. Campaign hooks

### 25.1 Uniqueness and saved state

`CampaignData` gains `heroes: Map<String, HeroState>`. A hero id has at most one living entity and belongs to at most
one faction. When a `FactionNpc` carrying a hero id joins the level (`EntityJoinLevelEvent`) with a UUID other than
its `HeroState`'s, it is discarded with a log line (a stale copy from an old chunk or a copied world). A hero entity
with no `HeroState` (a world copied without data) is adopted.

| Where | What |
|---|---|
| `CampaignData.heroes[id]` (`HeroState`) | owner faction or free, base id, entity UUID, alive, `reviveAt`, level, xp, affixes, energy, kills, deaths, casts per ability, joined at, definition hash |
| `FactionState` | `starterHeroGiven` |
| `NpcRecord` | `heroId` (empty for normal NPCs) |
| entity NBT `FcHero` | hero id, cooldown ready-at times, charges, empowered until, low-health re-arm flags (status timers are seconds long and not saved) |
| entity synced data | `DATA_HERO` (string id, `""` for normal NPCs), `DATA_HERO_POSE` (byte) |
| `world/factionclash_heroes/<id>.json` | the last valid definition of every owned hero (section 25.5) |

### 25.2 Starter heroes

- At campaign start, after each human base is built (`BaseBuilder`, with the starting roster), the faction's starter
  is summoned beside the reporting station, with its kit and its `summoned` line. Which hero:
  1. config `heroes.starters` (`["ember_legion=bram_ostler", ...]`), else
  2. the loaded hero whose `starter_for` names the faction (several: the first by id, NOTE naming the others), else
  3. with `heroes.randomStarters` (on), a random free `common` or `rare` hero allowed for the faction, else none.
- A campaign started before heroes existed: on the first tick with heroes enabled, each living human faction with
  `starterHeroGiven` false gets one at its first base, announced in chat.

### 25.3 Hero tokens

- Item `factionclash:hero_token` (stack 1, epic rarity, glint) with an optional data component `factionclash:hero` (a
  hero id) or `factionclash:hero_rarity`. Names: "Hero Token (Bram Ostler)", "Hero Token (epic)", "Hero Token".
- **Use**: a faction member right-clicks their own faction's Reporting Station or Faction Heart with it. A named token
  brings that hero if it is free; an unnamed token draws a free hero allowed for the faction (weights 8 / 4 / 2 / 1 by
  rarity, or only the token's rarity). The hero walks out of the station with its kit and says its `summoned` line.
  The token is kept, with the reason, when the hero serves another faction, nobody suitable is free, or the faction
  is at `maxPerFaction`. A named token for a hero the faction already has gives it XP (section 22.4).
- **Used by NPCs**: a token in a faction's storehouse (raiders and guards pick up loot) is used by the director at
  its next run at that base: "A hero token was brought to the station: Sela Quickfern joins". AI factions get heroes
  too.
- **Sources**: config `heroes.tokenDrops`, entries `source=chance%:rarity`, default `minecraft:wither=100:epic`,
  `minecraft:ender_dragon=100:legendary`, `minecraft:warden=50:epic`, `minecraft:elder_guardian=25:rare`,
  `hollow_lair=25:rare` (a destroyed Hollow lair drops one at its heart for the raiding party). ROADMAP phase 3's
  dungeon bosses and reward chests add sources later. `/fc admin hero token` for tests.

### 25.4 Revival (10 minutes)

- On death: kit items vanish (they return on revival); everything else drops as for any NPC; statuses, summons,
  zones and energy are cleared; `HeroState.alive = false`, `reviveAt = game time + heroes.reviveSeconds × 20` (600 s =
  12000 ticks); the `fallen` line; the faction is told "Bram Ostler has fallen at (x, z) and returns in 10:00"; the
  killer's faction is told too.
- `HeroManager` checks once a second (from `FactionDirector.tick`). A due hero comes back at its home base's spawn
  point beside the reporting station (or the nearest standing base of its faction), at full health, energy 0, kit
  re-equipped, level, XP and affixes restored, with totem particles and its `revived` line. This is a respawn like a
  player's at a bed, not a teleport of a living NPC. It then re-equips from the storehouse.
- The faction has no standing base: the hero is released (free for tokens) and the faction told.
- The station screen and `/fc hero list` show "returns in 6:12".
- An entity missing from the world for 2 in-game days while its chunk was loaded (the `MISSING_SINCE` rule of
  `rosterUpkeep`) is treated as fallen.

### 25.5 Last known good and dormant heroes

- After each successful load the world keeps a copy of every owned hero's definition in
  `world/factionclash_heroes/<id>.json`. If the folder file later breaks or disappears, the hero in the world keeps
  running that copy, and operators are told on login: "Bram Ostler's file is now invalid; he keeps the version loaded
  on 2026-11-10. Fix the file or use /fc admin hero release bram_ostler."
- A hero in the campaign with no valid definition at all becomes **dormant**: it keeps its entity, faction, level and
  saved state, fights as a plain soldier of its duty with the faction look and no abilities, and its name tag says
  `(dormant)`. Nothing is ever deleted because of a broken file.

### 25.6 Hero behaviour in the existing systems

- `isGuardDuty()` is true for every hero: it looks for enemies within its aggression radius whatever its duty, and
  the civilian rules (flee, fight only up close, stop at half health) never apply to it.
- `retrainIdleWorker` never retrains a hero. `Alerts.callGuards` and the defence alarm call heroes of any duty.

### 25.7 War

- `FactionDirector.power(n)` for a hero: the soldier formula + 1 + level / 10 (a level-20 hero in full iron is 5.25,
  about 2.3 soldiers in iron at 2.25 each). `defenders(...)` counts a hero at home as 2, whatever its duty.
- Raid parties pick `raids: leads` heroes first, then `joins` heroes with the soldiers (sorted by power), never
  `stays_home`; a duty pauses while raiding.
- A hero killing an enemy hero is announced to both factions.

### 25.8 Screens and presentation

- Reporting station (`BaseInfo`): the line "Great People: not implemented in this build" becomes a Heroes section:
  name, title, level, duty, state, energy, time to return.
- Right-clicking a hero (`NpcInspector`) opens the hero sheet: title, level and XP, energy, each ability with its
  computed numbers, unlock level and cooldown, the empowered state, base abilities, active statuses. Sneak-give
  equipment works as for NPCs.
- Name tag: `★ Bram Ostler · the Gatewarden [Fighting 80%]`.
- Boss bar: while a hero fights, players within 32 blocks see a vanilla boss bar with its name and health in the hero
  colour; it turns gold when the ultimate is ready (`heroes.bossBar`).

---

## 26. Validation

### 26.1 Pipeline (all in `logic/hero/`, pure Java, unit-tested)

1. **`HeroJson`**: a small JSON reader that keeps the line and column of every key and value, applies the forgiveness
   of section 3.1, and rejects the rest with the exact position and a plain explanation.
2. **`HeroReader`**: tree to `HeroDef`, driven by `HeroCatalogue` (every field, type, default, range, enum, synonym,
   allowlist and cap lives there and only there). Tracks the field path.
3. **Sense rules**: recipient classes and forms (section 13), `to` values, at most 2 `damage` and 1 `move_self` per
   ability, trigger/form pairs (17.1), `empower` without `empowered`, `mark` required for `on_mark_full`,
   `out_of_combat` on enemy forms, fields a form does not use, a style that does not fit the role, potion and stat
   classes, `summon` outside skills and the ultimate.
4. **`HeroBalance`**: slot caps (16.1), potion and stat caps, cooldown and passive floors (15, 17.4), with each change
   recorded.
5. **`SkinCheck`**: section 24.2.
6. **Across files**, after all files: duplicate ids, `starter_for` conflicts, marks checked but never applied by any
   loaded hero (NOTE), more than `heroes.maxLoaded` heroes.
7. In game only: a sanity check that every allowlisted id exists in the live registries. A failure is logged as a mod
   bug, never blamed on the file.

`HeroSchemaGen` writes `_hero.schema.json` from `HeroCatalogue`, and `HeroDocs` writes `_README.txt` and checks the
prompt, so the schema, the guide and the validator cannot disagree (unit-tested against golden files).

### 26.2 Outcomes

| Outcome | Shown as | Happens when | Effect |
|---|---|---|---|
| NOT LOADED | red | the file cannot be read; `format` missing or newer; `name`, `behaviour.combat_role` or `basic_attack.style` missing or invalid; duplicate id; over the hero limit | The hero is unavailable; a hero already in the world keeps its last good definition or becomes dormant. |
| PART LEFT OUT | gold | one ability, passive, effect, base ability, gear item, event or line is invalid (unknown form or effect type, a recipient rule broken, a required field missing inside it, a bad placeholder) | That part is skipped; the rest of the hero loads. An ability with no valid effect left is skipped as a whole. |
| ADJUSTED | yellow | a value clamped or scaled to a cap, a cooldown raised to its floor, a synonym or misspelling read, a decimal rounded, a kit item added, the file name normalised | Loads with the corrected value. |
| NOTE | gray | an unknown field ignored, a field the form does not use, a style that does not fit the role, comments or trailing commas, no skin, a mark nobody applies | Loads unchanged. |

### 26.3 Message format

Written for non-programmers: the path is spelled out with 1-based numbers and names, and every message says what is
wrong, what is allowed and what the mod did. The log adds the machine path.

```
<file>, line <L>: <friendly path>: <what is wrong>. <what is allowed>. <OUTCOME>.
```

```
sela_quickfern.json, line 14, column 9: a comma is missing before "stats". In JSON, fields are separated by commas. NOT LOADED.
sela_quickfern.json, line 3: "name" is missing. Add the hero's display name, for example "name": "Sela Quickfern". NOT LOADED.
sela_quickfern.json, line 22: behaviour > combat_role: "wizard" is not a combat role. Allowed: vanguard, striker, skirmisher, marksman, support. NOT LOADED.
sela_quickfern.json, line 22: behaviour > combat_role: "ranger" was read as "marksman". ADJUSTED.
bram_ostler.json, line 41: skill 1 "Shield Slam" > "cooldwon" was read as "cooldown". ADJUSTED.
bram_ostler.json, line 44: skill 1 "Shield Slam" > effect 1 > element: "wind" is not an element. Allowed: physical, fire, frost, shock, venom, blight, arcane, radiant. Used the hero's element, "physical". ADJUSTED.
bram_ostler.json, line 44: skill 1 "Shield Slam" > effect 1 > element: "lightning" was read as "shock". ADJUSTED.
bram_ostler.json, line 47: skill 1 "Shield Slam" > cooldown: 2 seconds is too short for a skill this strong. Raised to 6.4 seconds, the shortest allowed for it. ADJUSTED.
bram_ostler.json, line 80: ultimate "Hold the Gate" > effect 4: "damage" only reaches enemies, but this ultimate affects allies (form "allies"). Put it in an ability that hits enemies. Effect PART LEFT OUT.
bram_ostler.json, line 52: skill 2 "Gate Call" > effect 2 > amount: "max_health_percent": 60 is above its range. Allowed 0 to 40. Set to 40. ADJUSTED.
bram_ostler.json, line 60: skill 1 "Shield Slam" deals up to 44 health points at level 1; the skill cap is 16. Scaled down to 16. ADJUSTED.
maudie_brine.json, line 70: ultimate "Spring Tide" > effect 2 > seconds: "slowness" on enemies lasts at most 8 seconds. Set to 8. ADJUSTED.
maudie_brine.json, line 12: gear 3: "diamond_chestplate": heroes start with iron gear at most (they can still take better gear from the storehouse). PART LEFT OUT.
maudie_brine.json, line 98: lines > kill > line 2: {target} is not a placeholder. Allowed for "kill": {name}, {enemy}. Line PART LEFT OUT.
maudie_brine.json, line 31: skill 2 "Clear Water" > effect 1: "type" is missing. Allowed: damage, dot, heal, shield, effect, stat, knockback, pull, ignite, freeze, stun, taunt, cleanse, dispel, energy, cooldown_cut, move_self, mark, clear_marks, empower, summon. Effect PART LEFT OUT.
maudie_brine.png: the skin is 128 x 128 pixels. Allowed: 64 x 64 (or the old 64 x 32). Maudie Brine uses the Tide Alliance look until the skin is fixed.
Maudie Brine.json: the file name was read as the id "maudie_brine"; its skin must be maudie_brine.png. ADJUSTED.
```

"Did you mean" uses edit distance at most 2 (3 for words longer than 10) and the synonym tables.

### 26.4 Where problems are shown

- **Log** (`logs/latest.log`): every problem, one line each, prefixed `[Heroes]`, with the machine path.
- **Chat**: after `/fc hero reload` operators get the summary ("Heroes: 6 loaded (2 adjusted), 1 with parts left out,
  1 not loaded") and every NOT LOADED and PART LEFT OUT line, each hero name clickable to run `/fc hero check <id>`.
  Operators logging in see one line if the last load had NOT LOADED or PART LEFT OUT problems.
- **`_report.txt`** in the heroes folder, rewritten after every load and grouped per file, headed "Paste the lines for
  one file into the chatbot that wrote it and ask it to fix only those lines." Claude reads the same report.
- **`/fc hero check <id|all>`**: the problems, then what the mod made of the hero: role, stats at levels 1, current and
  30, and each ability with its final numbers and floor ("Shield Slam: 5.8 damage, stuns 1 s at 3+ Toll; weight 8.8,
  floor 6.4 s, cooldown 9 s; at level 30: 6.6 damage").

### 26.5 Safety limits (a file can never hurt the server or clients)

64 heroes, 128 KB per JSON, 64 KB per PNG, IHDR checked before decoding, one level of sub-folders, symbolic links not
followed, ids limited to `[a-z0-9_]`, no file paths taken from file contents (the skin is always `<id>.png`), text
stripped of `§` codes and control characters, fixed particle counts, rate-limited dialogue, raw-pixel skin payloads of
exactly 16384 bytes, bounded area queries (radius at most 12, at most 16 receivers).

---

## 27. Commands

| Command | Who | What |
|---|---|---|
| `/fc hero list` | all | Every loaded hero: owner faction or free, alive or "returns in mm:ss", level. Operators also see dormant heroes and problem counts. |
| `/fc hero info <id>` | all | The hero sheet (`InfoPayload` screen), numbers as the mod computed them, including the settled `use_when`. |
| `/fc hero check <id\|all>` | op | Validates the file(s) now, without applying; prints every problem and the computed numbers (26.4). |
| `/fc hero reload` | op | Reloads the folder (2.3); summary in chat; `_report.txt` rewritten; skins re-sent. |
| `/fc hero guide` | op | (Re)writes `_README.txt`, `_PROMPT.txt`, `_hero.schema.json` and `_example.json`. |
| `/fc admin hero give <faction> <id>` | op | The hero joins that faction now (frees it from another owner first). |
| `/fc admin hero token [id\|rarity] [player]` | op | Gives a token. |
| `/fc admin hero revive <id>` | op | Revives a fallen hero now. |
| `/fc admin hero release <id>` | op | Frees the hero (removes its entity, keeps its level for the next owner). |
| `/fc admin hero test <id> [1-8]` | op | Test arena: summons the hero without a faction (no XP) beside 1-8 dummies (husks in leather armour), runs 30 s, then prints damage, healing, casts and passive firings per ability, and removes everything. |

Hero ids are suggested by tab completion.

---

## 28. Config (`[heroes]` section of `factionclash-common.toml`, PROPOSED)

| Key | Default | Range | Meaning |
|---|---|---|---|
| `enabled` | true | | Heroes on or off (off: dormant; nothing deleted). |
| `folder` | `config/factionclash/heroes` | | Hero folder. |
| `builtinHeroes` | true | | Load the mod's example heroes. |
| `starterHeroes` | true | | Each faction gets a starter. |
| `randomStarters` | true | | A random hero for factions no file claims. |
| `starters` | `[]` | | `faction=hero` overrides. |
| `maxLoaded` | 64 | 1-64 | |
| `maxPerFaction` | 4 | 1-16 | |
| `reviveSeconds` | 600 | 30-3600 | |
| `powerScale` | 100 | 25-200 | % on every Amount (16.3). |
| `maxHit` | 40 | 10-100 | Clamp per hero hit. |
| `maxDotPerSecond` | 6 | 1-20 | Per hero and target. |
| `globalCooldown` | 1.0 | 0.25-3 | Seconds between two abilities of one hero. |
| `summons` | true | | The `summon` effect. |
| `xpRate` | 100 | 0-500 | % on hero combat XP. |
| `tokenDrops` | see 25.3 | | `source=chance%:rarity` list. |
| `dialogue` | `local` | `local`, `faction`, `off` | Who hears lines. |
| `dialogueRadius` | 32 | 8-128 | |
| `dialogueCooldownSeconds` | 20 | 5-300 | |
| `defaultLines` | true | | Generic lines for events a file leaves out. |
| `speechBubbles` | true | | Text above the hero's head. |
| `showEnergy` | true | | Energy % on the name tag in fights. |
| `bossBar` | true | | Boss bar while a hero fights. |

`docs/factionclash-common.toml` is regenerated from a fresh server when this lands (CLAUDE.md rule).

---

## 29. Implementation map

### 29.1 New pure logic (`src/main/java/com/factionclash/logic/hero/`, no Minecraft classes, unit-tested)

| Class | Role |
|---|---|
| `HeroJson` | Positioned, tolerant JSON reader (26.1). |
| `HeroCatalogue` | The single source: fields, types, defaults, ranges, enums, synonyms, allowlists, caps, role presets, growth profiles, trigger/form tables. |
| `HeroDef` + records `Stats`, `Behaviour`, `BasicAttack`, `Ability`, `Passive`, `Effect` (sealed, one record per type), `Amount`, `Condition`, `Look`, `Empowered`, `BaseAbility`, `GearItem`, `Lines` | The immutable, fully defaulted definition. |
| `HeroReader`, `Problem`, `Problems` | Tree to `HeroDef`; severity, file, line, column, friendly and machine path; `format()` for chat, log and report; did-you-mean. |
| `HeroBalance` | Reference Amounts, slot caps, weights, cooldown and passive floors, potion and stat caps. |
| `HeroRules` | Runtime arithmetic: the damage formula's clamps, energy gains, growth per level, ability power, combat XP, dialogue rate limit, rarity draw, revival due time, war power. |
| `SkinCheck` | PNG header check, decode, 64×32 conversion, base-layer alpha, arm check, hash. |
| `HeroSchemaGen`, `HeroDocs` | Writes the schema and the guide from the catalogue; checks the prompt names every catalogue value. |
| `HeroCheckMain` | CLI for `./gradlew checkHeroes -PheroDir=...` (a `JavaExec` task on the main source set): reader + balance + skin check over a folder, prints the report, exits non-zero on NOT LOADED or PART LEFT OUT. |

### 29.2 New game-side classes

| Class | Role |
|---|---|
| `hero/HeroRegistry` | Loads built-ins and the folder, atomic reload, last-known-good copies, `_report.txt`, guide files, registry sanity check. |
| `hero/HeroManager`, `hero/HeroState` | Ownership, uniqueness and dedupe, summoning, starters (new and old campaigns), tokens (player and NPC use, drops), revival, dormant handling, release. Ticked from `FactionDirector.tick`. |
| `hero/HeroCombat` | Per-entity runtime: energy, cooldowns, charges, cast in progress, empowered until, re-arm flags; saved as `FcHero`. |
| `hero/HeroForms` | Resolves receivers for each form and `to` (enemy and ally predicates from `FactionNpc.isEnemy` and `CombatRules.sameSide`; AABB queries capped at radius 12, 16 receivers). |
| `hero/HeroEffects` | One method per effect type (29.4). |
| `hero/HeroDamage` | The formula of 6.3, damage sources per element, i-frame handling, drain. |
| `hero/HeroStatus`, `hero/HeroStatusTicker` | Transient data attachment on any `LivingEntity`: marks, DoTs, stat modifiers, shields, stun, taunt, freeze hold. |
| `hero/HeroZones` | Server-side list of zones per level (not entities): telegraph, pulses, follow-self. |
| `hero/HeroShots`, `hero/entity/HeroArrow`, `HeroTrident`, `HeroItemProjectile` | Tagged projectiles that cannot be picked up; `ProjectileImpactEvent` applies the ability's effects and cancels block effects. Item looks use `ThrownItemRenderer`, arrows and tridents the vanilla renderers. |
| `hero/HeroSummons` | Summon tagging, goals, expiry, re-adoption. |
| `hero/HeroPassives` | Trigger dispatch from the hooks of 29.3, loop guard. |
| `hero/HeroAuras` | Base abilities; a 40-tick cache per base; `rally` read by `Recruitment`. |
| `hero/HeroDialogue` | Picks and sends lines, placeholders, rate limits, text displays. |
| `hero/HeroBossBars` | `ServerBossEvent` per fighting hero. |
| `hero/HeroSkinSender`, `net/HeroSkinsTask` | Configuration-phase and reload skin exchange, per-player hash memory. |
| `combat/HeroAbilityGoal` | The decision loop of 10.4, windups, telegraphs, `leap`/`dash` motion (priority 2; MOVE+LOOK only while casting). |
| `combat/HeroTargetGoal` | Target selector for heroes: `focus`, `protects`, aggression radius. |
| `combat/HeroThrowGoal` | `trident` and `thrown` basic attacks (priority 3, like the ranged goal). |
| `combat/StunnedGoal` | Added to goal-based mobs on join; active while `HeroStatus` says stunned. |
| `item/HeroTokenItem`, `registry/ModDataComponents` | The token; the `factionclash:hero`, `factionclash:hero_rarity` and `factionclash:hero_kit` components. |
| `net/HeroSkinManifest`, `HeroSkinRequest`, `HeroSkinData`, `HeroSkinForget` | 24.3. |
| `client/HeroSkinCache` | 24.4. |
| Jar data | `data/factionclash/damage_type/hero_*.json` (16 files), `data/minecraft/tags/damage_type/{is_fire,bypasses_armor,bypasses_shield,witch_resistant_to,wither_immune_to,no_knockback}.json` additions, death messages and token names in `assets/factionclash/lang/en_us.json`, the built-in heroes under `factionclash/heroes/`. |

### 29.3 Changes to existing classes

| Class | Change |
|---|---|
| `npc/FactionNpc` | `DATA_HERO` (String) and `DATA_HERO_POSE` (Byte) in `defineSynchedData`; `isHero()`, `heroDef()`, `heroCombat()`; `registerGoals` adds the hero goals (inactive for normal NPCs, because the hero id is only known after load); `tick` → `HeroCombat.tick`; `doHurtTarget` → `on_hit`/`on_crit` passives, basic `on_hit` effects and finisher, energy, the `crit_damage` stat on the existing `CRIT_ID` modifier, `on_ally_attack` for allies in reach; `performRangedAttack` tags the arrow so its hit counts as a basic hit and reads Marksman through `affix()`; `hurt` → `on_hurt`, `on_ally_hurt`, energy, low-health line; `die` → `HeroManager.fallen`; `awardXp` → level-up and unlock lines, `HeroState` sync; `reapplyAffixes` → hero base stats, growth and size with fixed ids; `affix(a)` → + aura stacks (capped by `Affix.maxStacks`, only where `a.appliesTo(job)`); `isGuardDuty()` → true for heroes; `refreshName` → `★ name · title [activity energy]`; `dropCustomDeathLoot` and `equip` → kit-bound items never drop or go to the backpack; `addAdditionalSaveData`/`readAdditionalSaveData` → `FcHero`. |
| `npc/NpcRole` | No new value: a hero's role is its duty role (`guard` → a soldier role from its style: melee → swordsman or axe_fighter, bow → archer, crossbow → crossbowman, thrown/trident → swordsman with its own goal). |
| `campaign/NpcRecord` | `heroId`. |
| `combat/NpcMeleeAttackGoal` | Leash from aggression instead of the fixed 56; the civilian half-health rule uses `fightsLikeSoldier()` (combatant role or hero); skirmisher step-back after a hit; gives way while a hero casts. |
| `combat/NpcRangedAttackGoal` | Kiting from `keep_distance`; focus from `behaviour.focus`; arrows tagged for `on_hit`. |
| `combat/CivilianFleeGoal` | Never for heroes. |
| `combat/WithdrawGoal` | Thresholds from `retreat_below` for heroes (0 = never); `combatant` check → `fightsLikeSoldier()`; a ready heal first. |
| `combat/Alerts` | `callGuards` also calls heroes of any duty. |
| `combat/CombatRules` | `factionOf` knows summons; `isHostileMonster` excludes summons; a `BOSSES` set. |
| `event/FCEvents` | `onIncomingDamage`: friendly-fire cancel extended to summons, cancel the visual fangs' damage, `HeroDamage` stats and resistances, shields, `on_hurt`/`on_ally_hurt`; `onDeath`: `on_kill`, `ally_fallen`, token drops, summon clean-up; `onJoin`: hero dedupe, summon re-adoption, `StunnedGoal`; `ServerStartingEvent` → `HeroRegistry.load`; `PlayerLoggedInEvent` → operator notice; `LivingHealEvent` → `healing_received`; `CriticalHitEvent` → players' `crit_damage`. |
| `campaign/FactionDirector` | `HeroManager.tick` (revival, NPC token use, starter migration); `power(n)` and `defenders(...)` for heroes; raid party order (`leads` first, never `stays_home`); `retrainIdleWorker` skips heroes; `defenceAlarm` path for `lookout`. |
| `campaign/Recruitment` | Food cost × (1 - rally). |
| `campaign/BaseBuilder` | Spawns the starter hero with the starting roster. |
| `campaign/CampaignData`, `FactionState` | `heroes` map; `starterHeroGiven`. |
| `block/ReportingStationBlock`, `block/FactionHeartBlock` | Token use. |
| `info/BaseInfo`, `npc/NpcInspector` | Heroes section; hero sheet. |
| `command/FCCommands` | `/fc hero ...` and `/fc admin hero ...`. |
| `config/FCConfig` | `[heroes]` section. |
| `net/FCNetwork` | Registers the four skin payloads (`commonToClient`/`commonToServer`) and raises the registrar version (`event.registrar("2")`), so a client with an older jar gets a clear version message instead of missing skins. |
| `client/FactionNpcRenderer`, `client/ClientSetup` | Classic/slim dispatch, hero textures, poses; renderers for the projectile entities. |
| `registry/ModEntities`, `registry/ModItems` | Projectile entity types; the token item. |
| `build.gradle` | `checkHeroes` JavaExec task. |
| `tools/setup_server.sh`, `tools/sim.py` | Optionally copy the heroes of a given folder (`HEROES_DIR`) into the server's heroes folder; hero lines in the summary (owner, level, state, kills, deaths, casts). |

### 29.4 Each primitive and its vanilla implementation

| Primitive | Implementation |
|---|---|
| `damage` | `target.hurt(heroSource(element), amount)` after `HeroDamage`; `drain` from health before and after. |
| `dot` | `HeroStatus` entry; each second `hurt(heroDotSource(element), amount × stacks)`. |
| `heal` | `LivingEntity.heal`. |
| `shield` | transient `MAX_ABSORPTION` modifier + `setAbsorptionAmount`; removed at expiry. |
| `effect` | `addEffect(new MobEffectInstance(holder, ticks, level - 1), hero)`. |
| `stat` | transient `AttributeModifier` (`ADD_MULTIPLIED_TOTAL` / `ADD_MULTIPLIED_BASE` / `ADD_VALUE`) or a `HeroStatus` value read by the events. |
| `knockback` / `pull` | `LivingEntity.knockback` + vertical impulse / `setDeltaMovement` toward the hero; `hurtMarked = true`. |
| `ignite` / `freeze` | `igniteForSeconds` / `setTicksFrozen` held by `HeroStatus`. |
| `stun` / `taunt` | `StunnedGoal` or slowness / `Mob.setTarget` re-asserted. |
| `cleanse` / `dispel` | `removeEffect`, `clearFire`, `setTicksFrozen(0)`, `HeroStatus`. |
| `energy` / `cooldown_cut` | `HeroCombat`. |
| `move_self`, `leap`, `dash` | `setDeltaMovement` impulses (`leap` a ballistic arc, `dash` a 10-tick push), `resetFallDistance`, landing by `onGround` or a 40-tick timeout. |
| `mark` / `clear_marks` | `HeroStatus.marks`; `on_mark_full` dispatch. |
| `empower` | `HeroCombat.empoweredUntil`; stat modifiers and the scale modifier with fixed ids. |
| `summon` | `EntityType.WOLF/SNOW_GOLEM/IRON_GOLEM.create`, tagged, goals replaced. |
| `shot`, `volley` | `HeroArrow` (an `AbstractArrow`, pickup disallowed), `HeroTrident`, `HeroItemProjectile` (a `ThrowableItemProjectile`, gravity 0); tagged with hero UUID and ability slot. |
| `bolt`, `chain` | instant, `hasLineOfSight`, a particle line. |
| `cone`, `line` | geometric selection; `line` stops at the first block (`level.clip`). |
| `zone` | `HeroZones` entry; particles during the delay and each pulse; AABB query. |
| `look.lightning` | `EntityType.LIGHTNING_BOLT.create(level)` + `setVisualOnly(true)`. |
| `look.fangs` | vanilla `EvokerFangs` tagged visual; their damage cancelled in `onIncomingDamage`. |
| particles / sounds / poses | `ServerLevel.sendParticles` with fixed counts / `level.playSound(null, ...)` / `DATA_HERO_POSE` → `HumanoidModel.ArmPose`. |

### 29.5 Performance

Abilities are evaluated every 5 ticks per fighting hero (at most 64 loaded, usually 4-16 in the world), passives on
events, auras every 40 ticks per base from a cache, area queries capped. Target: under +1 ms per tick for 16 heroes,
measured in the campaign check.

---

## 30. Data formats outside the hero file

- `heroes.tokenDrops` entries: `<entity id or hollow_lair>=<chance %>:<rarity>`.
- `heroes.starters` entries: `<faction id>=<hero id>`.
- `_report.txt`: per file a header line, then the messages of 26.3, NOT LOADED first.

---

## 31. Testing and verification

### 31.1 Unit tests (`src/test/java/com/factionclash/logic/hero/`)

- `HeroJsonTest`: key and value positions; BOM, CRLF, code fences, comments and trailing commas accepted; each syntax
  error class gives the right line, column and message; size, depth and string limits.
- `HeroReaderTest`: the minimal hero (5 fields); every required-field message; unknown and misspelled fields; every
  synonym table; text-for-number units; enum errors list the allowed values; each form's defaults and unused-field
  notes; the recipient rules both ways; trigger/form pairs; placeholders; gear allowlist and slot rules;
  enchantment applicability; file-name normalisation; a list of heroes in one file; duplicate ids.
- `HeroBalanceTest`: reference Amounts for every part; each slot cap and the zone bonus; the weight table and floors
  for the example abilities (the table of section 15); passive floors, `every` periods included; the ultimate gaps
  of section 14; the `thrown` rate cap; the shared `on_hit` cap; potion and stat caps; `powerScale`.
- `HeroRulesTest`: damage clamps and element multipliers, the 2.0 product clamp and the 1.5× gear cap; the
  `cooldown_cut` half-cooldown limit; energy gains and `charge_rate`; growth at levels 1/15/30;
  combat XP; dialogue rate limit; seeded rarity draws; revival due time; war power.
- `SkinCheckTest`: 64×64 accepted; 64×32 converted; 128×128 rejected; an IHDR claiming 30000×30000 rejected without
  decoding; a non-PNG rejected; base layer forced opaque, outer layer kept; the arm check; hash stable.
- `HeroDocsTest`: the generated schema equals the golden `docs/heroes/hero.schema.json`; the three example heroes and
  `_example.json` read with zero problems; the prompt contains every enum value of the catalogue.

### 31.2 GameTests (each job-running test its own `batch`; static checks share `workers_static`; test bases through `GameTestBases.register`, cleaned up; spawns at known relative positions on the flat test world)

`builtinHeroesLoad` (static), `heroSkinPayloadRoundTrip` (static: codec and hash),
`heroSkillDamagesEnemy`, `heroNovaSparesAlliesAndAnimals`, `heroHealsHurtAlly`, `heroShieldExpires`,
`heroUltimateAtFullEnergy`, `heroPassiveOnHitFires`, `heroFollowUpDoesNotLoop` (two heroes with `on_ally_attack`),
`heroDotTicksThroughIframes` (a DoT ticks and a soldier's sword still lands), `heroElementImmunity` (venom vs a
zombie, frost vs a stray, radiant ×1.3 vs a husk), `heroStunEndsOnTime`, `heroTauntRetargets`,
`heroProjectileLeavesNoFire` (a fire-charge look into a wooden wall: no fire block, no item), `heroLeapMovesPhysically`
(positions continuous, no jump over 1.5 blocks per tick), `heroZonePulses`, `heroMarkFullFires`, `heroEmpowerAppliesAndEnds`,
`heroSummonFightsAndVanishes`, `heroRevivesAtSpawnPoint` (short `reviveSeconds`), `heroTokenAtStation`,
`heroDuplicateEntityDiscarded`, `heroDormantWithoutDefinition`, `heroAuraRaisesWorkerAffix`, `heroMinerDutyMines`,
`heroKitNeverDrops`.

### 31.3 Campaign check (`docs/HANDOFF.md` section 4)

An 8-day campaign with the three example heroes plus the owner's pack as starters and tokens. Per hero, from
`/fc admin dump` and `sim.py`: casts per ability, kills, deaths, revivals, level; raids that included heroes; no hero
stuck (recoveries); tick time before and after. A restart mid-revival and mid-cast; a reload with one file broken on
purpose (the living hero keeps its last good definition and the report names the problem). On a real client (the
`clientViewer` run): classic and slim skins, outer-layer transparency, the fallback look for a hero with a bad skin, a
telegraph, a zone, a summon, the boss bar, the speech text and the hero sheet.

### 31.4 Docs

README "Heroes" (players: tokens, revival, lines; owners: the folder, the commands, the prompt), BALANCE "Heroes"
(generated numbers), ROADMAP phase 4 rewritten for the folder design and its status column kept current, HANDOFF
architecture map, `docs/heroes/` with this specification, the prompt and the schema, jar rebuilt with its SHA-256 on
README line 13.

---

## 32. Implementation phases (each reviewed and green before the next)

1. **Format and checking**: `HeroJson`, `HeroCatalogue`, `HeroDef`, `HeroReader`, `Problems`, `HeroBalance`,
   `HeroRules`, `SkinCheck`, `HeroSchemaGen`, `HeroDocs`, `HeroCheckMain` and `checkHeroes`, unit tests, the three
   examples as built-in resources. No gameplay yet.
2. **Loading and commands**: `HeroRegistry`, folder and guide files, `_report.txt`, `/fc hero list|info|check|reload|
   guide`, config section.
3. **Visible heroes**: `DATA_HERO`, `HeroState`, kit binding, skins (payloads, sender, client cache, renderer),
   `give`, starter heroes (new and old campaigns), name tags, dialogue, station and inspect screens, revival, dedupe,
   dormant. Basic attacks only.
4. **Ability core**: `HeroCombat`, `HeroAbilityGoal`, forms, `HeroStatus`, `HeroDamage` and elements, Amounts,
   conditions, energy, cooldowns and floors, and the effects `damage`, `dot`, `heal`, `shield`, `effect`, `stat`,
   `knockback`, `pull`, `ignite`, `freeze`, `cleanse`, `energy`, `cooldown_cut`, `move_self`, `mark`,
   `clear_marks`; projectiles, zones, chains.
5. **Control, passives and the rest**: `stun`, `taunt`, `dispel`, `empower`, `summon`, `leap`, `dash`, telegraphs and
   poses; passives with the loop guard; behaviour (`HeroTargetGoal`, keep distance, retreat, skirmisher, duties,
   alarms); base abilities; XP and growth.
6. **War and tokens**: war council, raids, tokens (item, components, station and heart use, NPC use, boss and lair
   drops), the test arena, the boss bar.
7. **Adversarial review, fixes, campaign check, docs, regenerated config, jar.**

---

## 33. Left out on purpose (and how a later format could add it)

| Left out | Why | Later |
|---|---|---|
| Teleport, blink, flight, invisibility of others | Break the player-like "walks everywhere" rule and the AI's assumptions. | - |
| Block-changing abilities (walls, pits, fire on the ground) | Base repairs, player-edit preservation and griefing rules. | A temporary-block effect that restores the blocks. |
| Item-creating abilities | "Nothing from nothing". | - |
| Custom models, animations, sounds, particle textures | Would need a resource pack on every client. | Possibly server-sent like skins. |
| Timed multi-step scripts, `and`/`or` conditions, per-ability energy costs, several stances | Each doubles what a chatbot can get wrong. | `format: 2`, if the owner's characters need them. |
| Summons other than wolves and golems (bees die after one sting, vexes fly through walls) | Vanilla behaviour that breaks the rules above. | More creatures with custom goals. |
| Hollow champions, player orders (follow me, hold here), escort duty | Need their own travel and chunk-loading rules. | `factions: ["hollow"]`, `/fc hero order`, `duty: escort`. |

New fields are always optional with defaults, so every `format: 1` file keeps loading under later versions.

---

## 34. Open questions for the owner, and decisions left to the implementation

For the owner:
1. **ROADMAP phase 4** describes a datapack plus resource pack; this design replaces both with the single server
   folder and server-sent skins. Confirm, and the ROADMAP text is updated.
2. **Gear on death**: kit items vanish and come back on revival; storehouse gear the hero picked up drops like any
   NPC's. The alternative is "drops everything like a player". Proposed: as written.
3. **Abilities against players**: proposed yes, with the limits (a stun only slows a player, telegraphs before big
   casts, taunt never controls a player, `powerScale`). The alternative is basic attacks only against players.
4. **A public repository** publishes everything committed to `heroes/` (JSON and skins). The owner decides what goes
   there; the mod's jar never includes the owner's pack.
5. **Hero cap per faction** 4, random starters for factions no file claims, AI factions using tokens: confirm.
6. **Summons** in v1 (proposed, behind `heroes.summons`) or later.

For the implementation:
1. `CampaignData` FORMAT: the new `heroes` key loads as empty from old saves, so no migration is needed; a bump to 3
   would stop an older jar from loading a world with heroes. Decide when building phase 3.
2. Confirm the configuration-task flow for skins on NeoForge 21.1.209 (`RegisterConfigurationTasksEvent.register` and
   the `commonToClient`/`commonToServer` registrar methods both exist in this version) and that a reload can re-run
   the exchange in the play phase.
3. The exact i-frame handling (6.3) against vanilla 1.21.1 `LivingEntity.hurt` (it compares with `lastHurt` while
   `invulnerableTime > 10`).
4. Stun and taunt on brain-based mobs (piglins, hoglins, wardens, villagers): slowness fallback as written, or a
   per-mob brain memory change.
5. Whether `size` above 100% causes pathing trouble in 2-high tunnels and doors in practice; cap lower if the
   campaign check shows it.
6. AI tuning of the ability loop (priority by order, the `big_fight` rule), and whether heroes should hold skills for
   bigger groups; to be settled from cast counts in the campaign check.
7. The speech bubble: a riding `Display.TextDisplay` per line, or chat only, if the display entity interferes with
   NPC movement or saving.
8. The rate and multiplier caps added in review (gear and buff cap 1.5× in 6.2, the 2.0 product clamp in 6.3, the
   `thrown` 5 damage per second in 9, the ultimate gap of 20-60 s in 14, `every` floors in 17.4, the `cooldown_cut`
   half-cooldown limit in 13.5): all PROPOSED; tune them from the per-ability damage and cast counts of the campaign
   check, and move them to BALANCE.md with the rest.

---

## Appendix A: JSON Schema of a hero file (draft 2020-12)

The same schema, pretty-printed, is `hero.schema.json`; the mod writes it as `_hero.schema.json`. It is the authoring contract: it rejects unknown fields (keys starting with `_` are allowed anywhere as comments) and values outside the widest allowed range. The loader is more forgiving (section 3.1) and adds the rules a schema cannot express: per-form ranges, recipient classes, slot caps, cooldown floors, potion and stat caps, placeholders per event, trigger/form pairs. Vanilla ids accept an optional `minecraft:` prefix.

```json
{
 "$schema":"https://json-schema.org/draft/2020-12/schema",
 "$id":"urn:factionclash:hero:1",
 "title":"Faction Clash hero file, format 1",
 "description":"One hero per file: config/factionclash/heroes/<id>.json next to <id>.png. Keys starting with _ are comments.",
 "type":"object",
 "required":["format","id","name","behaviour","basic_attack"],
 "properties":{
  "format":{"const":1},
  "id":{"$ref":"#/$defs/id"},
  "name":{"type":"string","minLength":1,"maxLength":24},
  "title":{"type":"string","minLength":0,"maxLength":32},
  "description":{"type":"string","minLength":0,"maxLength":300},
  "rarity":{"enum":["common","rare","epic","legendary"]},
  "starter_for":{"enum":["ember_legion","grove_covenant","tide_alliance","stoneguard"]},
  "factions":{"type":"array","minItems":1,"maxItems":4,"uniqueItems":true,"items":{"enum":["ember_legion","grove_covenant","tide_alliance","stoneguard"]}},
  "skin_model":{"enum":["classic","slim"]},
  "color":{"$ref":"#/$defs/color"},
  "element":{"$ref":"#/$defs/element"},
  "stats":{
   "type":"object",
   "properties":{
    "health":{"type":"number","minimum":20,"maximum":40},
    "attack":{"type":"number","minimum":0,"maximum":4},
    "armor":{"type":"number","minimum":0,"maximum":8},
    "speed":{"type":"integer","minimum":90,"maximum":120},
    "knockback_resistance":{"type":"integer","minimum":0,"maximum":60},
    "size":{"type":"integer","minimum":85,"maximum":110},
    "growth":{"enum":["balanced","tough","fierce","swift","mystic"]}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "resistances":{
   "type":"object",
   "properties":{
    "physical":{"type":"integer","minimum":-30,"maximum":60},
    "fire":{"type":"integer","minimum":-30,"maximum":60},
    "frost":{"type":"integer","minimum":-30,"maximum":60},
    "shock":{"type":"integer","minimum":-30,"maximum":60},
    "venom":{"type":"integer","minimum":-30,"maximum":60},
    "blight":{"type":"integer","minimum":-30,"maximum":60},
    "arcane":{"type":"integer","minimum":-30,"maximum":60},
    "radiant":{"type":"integer","minimum":-30,"maximum":60}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "gear":{"type":"array","maxItems":6,"items":{"$ref":"#/$defs/gear_item"}},
  "behaviour":{
   "type":"object",
   "required":["combat_role"],
   "properties":{
    "combat_role":{"enum":["vanguard","striker","skirmisher","marksman","support"]},
    "focus":{"enum":["nearest","weakest","strongest","ranged_first","threat_to_allies","heroes_first"]},
    "keep_distance":{"type":"number","minimum":0,"maximum":16},
    "aggression":{"enum":["cautious","steady","bold"]},
    "protects":{"enum":["none","allies","workers","players","heart"]},
    "retreat_below":{"type":"integer","minimum":0,"maximum":60},
    "raids":{"enum":["leads","joins","stays_home"]},
    "duty":{
     "enum":[
      "guard",
      "scout",
      "builder",
      "miner",
      "farmer",
      "woodcutter",
      "blacksmith",
      "chef",
      "smelter",
      "animal_carer",
      "butcher",
      "fisherman",
      "cave_explorer",
      "hauler",
      "enchanter",
      "trader"
     ]
    }
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "basic_attack":{
   "type":"object",
   "required":["style"],
   "properties":{
    "style":{"enum":["melee","bow","crossbow","trident","thrown"]},
    "projectile":{
     "type":"string",
     "pattern":"^(minecraft:)?(snowball|fire_charge|wind_charge|slime_ball|magma_cream|amethyst_shard|prismarine_shard|prismarine_crystals|echo_shard|blaze_powder|feather|bone|flint|heart_of_the_sea|nautilus_shell)$"
    },
    "held_item":{
     "type":"string",
     "pattern":"^(minecraft:)?(stick|blaze_rod|breeze_rod|book|amethyst_shard|echo_shard|prismarine_shard|heart_of_the_sea|nautilus_shell|feather|bone|spyglass|compass|clock|lantern|soul_lantern|poppy|cornflower)$"
    },
    "amount":{"type":"number","minimum":2,"maximum":10},
    "interval":{"type":"number","minimum":0.8,"maximum":3},
    "element":{"$ref":"#/$defs/element"},
    "on_hit":{"type":"array","minItems":1,"maxItems":3,"items":{"$ref":"#/$defs/effect"}},
    "every_nth_hit":{
     "type":"object",
     "required":["n","effects"],
     "properties":{"n":{"type":"integer","minimum":2,"maximum":6},"effects":{"type":"array","minItems":1,"maxItems":3,"items":{"$ref":"#/$defs/effect"}}},
     "patternProperties":{"^_":{}},
     "additionalProperties":false
    }
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "skills":{"type":"array","maxItems":4,"items":{"$ref":"#/$defs/skill"}},
  "ultimate":{"$ref":"#/$defs/ultimate"},
  "passives":{"type":"array","maxItems":4,"items":{"$ref":"#/$defs/passive"}},
  "empowered":{"$ref":"#/$defs/empowered"},
  "base_abilities":{"type":"array","maxItems":3,"items":{"$ref":"#/$defs/base_ability"}},
  "lines":{"$ref":"#/$defs/lines"}
 },
 "patternProperties":{"^_":{}},
 "additionalProperties":false,
 "$defs":{
  "id":{"type":"string","pattern":"^[a-z][a-z0-9_]{2,31}$"},
  "mark_id":{"type":"string","pattern":"^[a-z][a-z0-9_]{1,23}$"},
  "element":{"enum":["physical","fire","frost","shock","venom","blight","arcane","radiant"]},
  "color":{"type":"string","pattern":"^#[0-9A-Fa-f]{6}$"},
  "particle":{
   "type":"string",
   "pattern":"^(minecraft:)?(angry_villager|ash|bubble_pop|cherry_leaves|cloud|crimson_spore|crit|damage_indicator|dragon_breath|dust|electric_spark|enchant|enchanted_hit|end_rod|explosion|firework|flame|glow|gust|happy_villager|heart|large_smoke|nautilus|note|poof|portal|reverse_portal|scrape|sculk_soul|small_flame|small_gust|smoke|snowflake|sonic_boom|soul|soul_fire_flame|spore_blossom_air|splash|sweep_attack|totem_of_undying|trial_spawner_detection|warped_spore|wax_on|white_ash|witch)$"
  },
  "sound":{
   "type":"string",
   "pattern":"^(minecraft:)?(block\\.amethyst_block\\.chime|block\\.amethyst_cluster\\.break|block\\.anvil\\.land|block\\.beacon\\.activate|block\\.beacon\\.power_select|block\\.bell\\.use|block\\.conduit\\.activate|block\\.enchantment_table\\.use|block\\.fire\\.ambient|block\\.glass\\.break|block\\.note_block\\.bell|block\\.respawn_anchor\\.charge|block\\.respawn_anchor\\.deplete|block\\.sculk_shrieker\\.shriek|entity\\.arrow\\.shoot|entity\\.blaze\\.ambient|entity\\.blaze\\.shoot|entity\\.breeze\\.shoot|entity\\.breeze\\.wind_burst|entity\\.dolphin\\.splash|entity\\.elder_guardian\\.curse|entity\\.ender_dragon\\.growl|entity\\.evoker\\.cast_spell|entity\\.evoker\\.prepare_attack|entity\\.evoker\\.prepare_summon|entity\\.experience_orb\\.pickup|entity\\.generic\\.explode|entity\\.ghast\\.shoot|entity\\.glow_squid\\.squirt|entity\\.goat\\.ram_impact|entity\\.illusioner\\.cast_spell|entity\\.illusioner\\.mirror_move|entity\\.illusioner\\.prepare_blindness|entity\\.iron_golem\\.attack|entity\\.lightning_bolt\\.thunder|entity\\.player\\.attack\\.crit|entity\\.player\\.attack\\.knockback|entity\\.player\\.attack\\.strong|entity\\.player\\.attack\\.sweep|entity\\.player\\.hurt_freeze|entity\\.player\\.levelup|entity\\.player\\.splash|entity\\.ravager\\.roar|entity\\.snowball\\.throw|entity\\.vex\\.charge|entity\\.warden\\.sonic_boom|entity\\.wind_charge\\.wind_burst|entity\\.wither\\.shoot|entity\\.wolf\\.howl|event\\.raid\\.horn|item\\.book\\.page_turn|item\\.crossbow\\.shoot|item\\.firecharge\\.use|item\\.flintandsteel\\.use|item\\.mace\\.smash_ground|item\\.shield\\.block|item\\.totem\\.use|item\\.trident\\.hit|item\\.trident\\.riptide_1|item\\.trident\\.throw|item\\.trident\\.thunder)$"
  },
  "amount":{
   "oneOf":[
    {"type":"number","minimum":0.5,"maximum":30},
    {
     "type":"object",
     "properties":{
      "base":{"type":"number","minimum":0,"maximum":30},
      "attack_percent":{"type":"integer","minimum":0,"maximum":300},
      "max_health_percent":{"type":"integer","minimum":0,"maximum":40},
      "armor_percent":{"type":"integer","minimum":0,"maximum":100},
      "target_max_health_percent":{"type":"integer","minimum":0,"maximum":20},
      "target_missing_health_percent":{"type":"integer","minimum":0,"maximum":30},
      "mark":{"$ref":"#/$defs/mark_id"},
      "per_mark_stack":{"type":"number","minimum":0,"maximum":6}
     },
     "patternProperties":{"^_":{}},
     "additionalProperties":false,
     "minProperties":1,
     "dependentRequired":{"per_mark_stack":["mark"],"mark":["per_mark_stack"]}
    }
   ]
  },
  "condition":{
   "type":"object",
   "required":["condition"],
   "properties":{
    "condition":{
     "enum":[
      "target_health_below",
      "target_health_above",
      "self_health_below",
      "self_health_above",
      "target_has_mark",
      "self_has_mark",
      "target_burning",
      "target_freezing",
      "target_slowed",
      "target_stunned",
      "target_has_dot",
      "target_is",
      "self_empowered",
      "self_shielded"
     ]
    },
    "value":{"type":"integer","minimum":1,"maximum":99},
    "mark":{"$ref":"#/$defs/mark_id"},
    "kind":{"enum":["monster","undead","arthropod","illager","player","npc","hero","boss"]}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false,
   "allOf":[
    {
     "if":{
      "required":["condition"],
      "properties":{"condition":{"enum":["target_health_below","target_health_above","self_health_below","self_health_above"]}}
     },
     "then":{"required":["value"]}
    },
    {"if":{"required":["condition"],"properties":{"condition":{"enum":["target_has_mark","self_has_mark"]}}},"then":{"required":["mark"]}},
    {"if":{"required":["condition"],"properties":{"condition":{"const":"target_is"}}},"then":{"required":["kind"]}}
   ]
  },
  "effect":{
   "type":"object",
   "required":["type"],
   "properties":{
    "type":{
     "enum":[
      "damage",
      "dot",
      "heal",
      "shield",
      "effect",
      "stat",
      "knockback",
      "pull",
      "ignite",
      "freeze",
      "stun",
      "taunt",
      "cleanse",
      "dispel",
      "energy",
      "cooldown_cut",
      "move_self",
      "mark",
      "clear_marks",
      "empower",
      "summon"
     ]
    },
    "to":{"enum":["targets","self","allies","ally"]},
    "radius":{"type":"number","minimum":2,"maximum":12},
    "chance":{"type":"integer","minimum":1,"maximum":100},
    "if":{"$ref":"#/$defs/condition"},
    "amount":{},
    "element":{},
    "drain":{},
    "seconds":{},
    "max_stacks":{},
    "id":{},
    "level":{},
    "stat":{},
    "percent":{},
    "strength":{},
    "up":{},
    "direction":{},
    "distance":{},
    "mark":{},
    "stacks":{},
    "glow":{},
    "creature":{},
    "count":{},
    "name":{}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false,
   "allOf":[
    {
     "if":{"required":["type"],"properties":{"type":{"const":"damage"}}},
     "then":{
      "properties":{"amount":{"$ref":"#/$defs/amount"},"element":{"$ref":"#/$defs/element"},"drain":{"type":"integer","minimum":0,"maximum":50}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","amount","element","drain"]},{"pattern":"^_"}]},
      "required":["amount"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"dot"}}},
     "then":{
      "properties":{
       "amount":{"$ref":"#/$defs/amount"},
       "seconds":{"type":"number","minimum":2,"maximum":10},
       "element":{"$ref":"#/$defs/element"},
       "max_stacks":{"type":"integer","minimum":1,"maximum":5}
      },
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","amount","seconds","element","max_stacks"]},{"pattern":"^_"}]},
      "required":["amount"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"heal"}}},
     "then":{
      "properties":{"amount":{"$ref":"#/$defs/amount"}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","amount"]},{"pattern":"^_"}]},
      "required":["amount"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"shield"}}},
     "then":{
      "properties":{"amount":{"$ref":"#/$defs/amount"},"seconds":{"type":"number","minimum":2,"maximum":30}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","amount","seconds"]},{"pattern":"^_"}]},
      "required":["amount"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"effect"}}},
     "then":{
      "properties":{
       "id":{
        "type":"string",
        "pattern":"^(minecraft:)?(speed|haste|strength|regeneration|resistance|fire_resistance|water_breathing|jump_boost|slow_falling|invisibility|slowness|weakness|mining_fatigue|poison|wither|glowing|levitation|blindness|darkness)$"
       },
       "level":{"type":"integer","minimum":1,"maximum":3},
       "seconds":{"type":"number","minimum":1,"maximum":30}
      },
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","id","level","seconds"]},{"pattern":"^_"}]},
      "required":["id","seconds"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"stat"}}},
     "then":{
      "properties":{
       "stat":{
        "enum":["attack","damage_dealt","damage_taken","armor","speed","attack_speed","healing_received","knockback_resistance","crit_damage","energy_gain"]
       },
       "percent":{"type":"integer","minimum":-50,"maximum":60},
       "seconds":{"type":"number","minimum":2,"maximum":30},
       "max_stacks":{"type":"integer","minimum":1,"maximum":5}
      },
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","stat","percent","seconds","max_stacks"]},{"pattern":"^_"}]},
      "required":["stat","percent"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"knockback"}}},
     "then":{
      "properties":{"strength":{"type":"number","minimum":1,"maximum":5},"up":{"type":"number","minimum":0,"maximum":3}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","strength","up"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"pull"}}},
     "then":{
      "properties":{"strength":{"type":"number","minimum":1,"maximum":3}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","strength"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"ignite"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":1,"maximum":8}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"freeze"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":1,"maximum":6}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"stun"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":0.5,"maximum":2}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"taunt"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":1,"maximum":8}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"cleanse"}}},
     "then":{"properties":{},"propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if"]},{"pattern":"^_"}]}}
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"dispel"}}},
     "then":{"properties":{},"propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if"]},{"pattern":"^_"}]}}
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"energy"}}},
     "then":{
      "properties":{"amount":{"type":"integer","minimum":5,"maximum":30}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","amount"]},{"pattern":"^_"}]},
      "required":["amount"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"cooldown_cut"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":1,"maximum":10}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]},
      "required":["seconds"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"move_self"}}},
     "then":{
      "properties":{"direction":{"enum":["back","forward","up"]},"distance":{"type":"number","minimum":2,"maximum":8}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","direction","distance"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"mark"}}},
     "then":{
      "properties":{
       "mark":{"$ref":"#/$defs/mark_id"},
       "stacks":{"type":"integer","minimum":1,"maximum":5},
       "max_stacks":{"type":"integer","minimum":1,"maximum":10},
       "seconds":{"type":"number","minimum":2,"maximum":30},
       "glow":{"type":"boolean"}
      },
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","mark","stacks","max_stacks","seconds","glow"]},{"pattern":"^_"}]},
      "required":["mark"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"clear_marks"}}},
     "then":{
      "properties":{"mark":{"$ref":"#/$defs/mark_id"}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","mark"]},{"pattern":"^_"}]},
      "required":["mark"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"empower"}}},
     "then":{
      "properties":{"seconds":{"type":"number","minimum":3,"maximum":20}},
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","seconds"]},{"pattern":"^_"}]},
      "required":["seconds"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"summon"}}},
     "then":{
      "properties":{
       "creature":{"enum":["wolf","snow_golem","iron_golem"]},
       "count":{"type":"integer","minimum":1,"maximum":3},
       "seconds":{"type":"number","minimum":10,"maximum":60},
       "name":{"type":"string","minLength":1,"maxLength":24}
      },
      "propertyNames":{"anyOf":[{"enum":["type","to","radius","chance","if","creature","count","seconds","name"]},{"pattern":"^_"}]},
      "required":["creature"]
     }
    }
   ]
  },
  "effects":{"type":"array","minItems":1,"maxItems":5,"items":{"$ref":"#/$defs/effect"}},
  "look":{
   "type":"object",
   "properties":{
    "particle":{"$ref":"#/$defs/particle"},
    "color":{"$ref":"#/$defs/color"},
    "sound":{"$ref":"#/$defs/sound"},
    "lightning":{"type":"boolean"},
    "fangs":{"type":"boolean"},
    "pose":{"enum":["none","swing","block","bow_draw","spear_throw","crossbow_charge","horn","spyglass"]}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "skill":{
   "type":"object",
   "required":["name","form","effects"],
   "properties":{
    "name":{"type":"string","minLength":1,"maxLength":24},
    "description":{"type":"string","minLength":0,"maxLength":200},
    "form":{"enum":["melee","shot","volley","bolt","chain","cone","line","nova","zone","leap","dash","self","ally","allies"]},
    "range":{"type":"number","minimum":2,"maximum":24},
    "radius":{"type":"number","minimum":0,"maximum":12},
    "count":{"type":"integer","minimum":2,"maximum":5},
    "angle":{"type":"integer","minimum":30,"maximum":120},
    "width":{"type":"number","minimum":1,"maximum":3},
    "hits":{"type":"integer","minimum":1,"maximum":5},
    "hit_interval":{"type":"number","minimum":0.2,"maximum":1},
    "projectile":{
     "type":"string",
     "pattern":"^(minecraft:)?(arrow|spectral_arrow|trident|snowball|fire_charge|wind_charge|slime_ball|magma_cream|amethyst_shard|prismarine_shard|prismarine_crystals|echo_shard|blaze_powder|feather|bone|flint|heart_of_the_sea|nautilus_shell)$"
    },
    "zone_at":{"enum":["target","self","follow_self"]},
    "delay":{"type":"number","minimum":0,"maximum":3},
    "duration":{"type":"number","minimum":0,"maximum":10},
    "interval":{"type":"number","minimum":0.5,"maximum":3},
    "windup":{"type":"number","minimum":0,"maximum":2},
    "use_when":{
     "enum":["always","self_health_below","ally_health_below","target_health_below","enemies_near","target_far","target_close","big_fight","out_of_combat"]
    },
    "use_value":{"type":"number","minimum":2,"maximum":90},
    "unlock_level":{"type":"integer","minimum":1,"maximum":30},
    "effects":{"$ref":"#/$defs/effects"},
    "look":{"$ref":"#/$defs/look"},
    "line":{"type":"string","minLength":1,"maxLength":120},
    "cooldown":{"type":"number","minimum":2,"maximum":120},
    "charges":{"type":"integer","minimum":1,"maximum":3},
    "energy_gain":{"type":"integer","minimum":0,"maximum":30}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "ultimate":{
   "type":"object",
   "required":["name","form","effects"],
   "properties":{
    "name":{"type":"string","minLength":1,"maxLength":24},
    "description":{"type":"string","minLength":0,"maxLength":200},
    "form":{"enum":["melee","shot","volley","bolt","chain","cone","line","nova","zone","leap","dash","self","ally","allies"]},
    "range":{"type":"number","minimum":2,"maximum":24},
    "radius":{"type":"number","minimum":0,"maximum":12},
    "count":{"type":"integer","minimum":2,"maximum":5},
    "angle":{"type":"integer","minimum":30,"maximum":120},
    "width":{"type":"number","minimum":1,"maximum":3},
    "hits":{"type":"integer","minimum":1,"maximum":5},
    "hit_interval":{"type":"number","minimum":0.2,"maximum":1},
    "projectile":{
     "type":"string",
     "pattern":"^(minecraft:)?(arrow|spectral_arrow|trident|snowball|fire_charge|wind_charge|slime_ball|magma_cream|amethyst_shard|prismarine_shard|prismarine_crystals|echo_shard|blaze_powder|feather|bone|flint|heart_of_the_sea|nautilus_shell)$"
    },
    "zone_at":{"enum":["target","self","follow_self"]},
    "delay":{"type":"number","minimum":0,"maximum":3},
    "duration":{"type":"number","minimum":0,"maximum":10},
    "interval":{"type":"number","minimum":0.5,"maximum":3},
    "windup":{"type":"number","minimum":0,"maximum":2},
    "use_when":{
     "enum":["always","self_health_below","ally_health_below","target_health_below","enemies_near","target_far","target_close","big_fight","out_of_combat"]
    },
    "use_value":{"type":"number","minimum":2,"maximum":90},
    "unlock_level":{"type":"integer","minimum":1,"maximum":30},
    "effects":{"$ref":"#/$defs/effects"},
    "look":{"$ref":"#/$defs/look"},
    "line":{"type":"string","minLength":1,"maxLength":120},
    "charge_rate":{"type":"integer","minimum":50,"maximum":150}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "passive":{
   "type":"object",
   "required":["name","trigger","effects"],
   "properties":{
    "name":{"type":"string","minLength":1,"maxLength":24},
    "description":{"type":"string","minLength":0,"maxLength":200},
    "trigger":{
     "enum":[
      "always",
      "on_hit",
      "on_crit",
      "on_hurt",
      "on_kill",
      "on_skill",
      "on_ultimate",
      "on_fight_start",
      "on_low_health",
      "on_ally_low_health",
      "on_ally_hurt",
      "on_ally_attack",
      "on_ally_ultimate",
      "on_mark_full",
      "every"
     ]
    },
    "trigger_value":{"type":"number","minimum":3,"maximum":60},
    "mark":{"$ref":"#/$defs/mark_id"},
    "chance":{"type":"integer","minimum":1,"maximum":100},
    "cooldown":{"type":"number","minimum":1,"maximum":120},
    "form":{"enum":["self","trigger_enemy","trigger_ally","ally","allies","nova","bolt","shot"]},
    "radius":{"type":"number","minimum":2,"maximum":12},
    "projectile":{
     "type":"string",
     "pattern":"^(minecraft:)?(arrow|spectral_arrow|trident|snowball|fire_charge|wind_charge|slime_ball|magma_cream|amethyst_shard|prismarine_shard|prismarine_crystals|echo_shard|blaze_powder|feather|bone|flint|heart_of_the_sea|nautilus_shell)$"
    },
    "if":{"$ref":"#/$defs/condition"},
    "effects":{"type":"array","minItems":1,"maxItems":3,"items":{"$ref":"#/$defs/effect"}},
    "look":{"$ref":"#/$defs/look"},
    "line":{"type":"string","minLength":1,"maxLength":120},
    "unlock_level":{"type":"integer","minimum":1,"maximum":30}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false,
   "allOf":[{"if":{"required":["trigger"],"properties":{"trigger":{"const":"on_mark_full"}}},"then":{"required":["mark"]}}]
  },
  "stat_bonus":{
   "type":"object",
   "required":["stat","percent"],
   "properties":{
    "stat":{
     "enum":["attack","damage_dealt","damage_taken","armor","speed","attack_speed","healing_received","knockback_resistance","crit_damage","energy_gain"]
    },
    "percent":{"type":"integer","minimum":-30,"maximum":30}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "empowered":{
   "type":"object",
   "required":["name"],
   "properties":{
    "name":{"type":"string","minLength":1,"maxLength":24},
    "description":{"type":"string","minLength":0,"maxLength":200},
    "stats":{"type":"array","minItems":1,"maxItems":4,"items":{"$ref":"#/$defs/stat_bonus"}},
    "on_hit":{"type":"array","minItems":1,"maxItems":2,"items":{"$ref":"#/$defs/effect"}},
    "particle":{"$ref":"#/$defs/particle"},
    "color":{"$ref":"#/$defs/color"},
    "size":{"type":"integer","minimum":100,"maximum":110},
    "skills_locked":{"type":"boolean"},
    "line":{"type":"string","minLength":1,"maxLength":120}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  },
  "base_ability":{
   "type":"object",
   "required":["type"],
   "properties":{
    "type":{"enum":["aura","mend","lookout","rally","drill"]},
    "name":{"type":"string","minLength":1,"maxLength":24},
    "unlock_level":{"type":"integer","minimum":1,"maximum":30},
    "affix":{},
    "stacks":{},
    "radius":{},
    "roles":{},
    "percent":{},
    "every":{},
    "xp":{}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false,
   "allOf":[
    {
     "if":{"required":["type"],"properties":{"type":{"const":"aura"}}},
     "then":{
      "properties":{
       "affix":{"enum":["diligence","prospector","regrowth","green_thumb","mason","marksman","thrifty","hardy","duelist","angler","haggler"]},
       "stacks":{"type":"integer","minimum":1,"maximum":3},
       "radius":{"type":"number","minimum":8,"maximum":32},
       "roles":{
        "type":"array",
        "minItems":1,
        "items":{
         "enum":[
          "builder",
          "miner",
          "farmer",
          "woodcutter",
          "blacksmith",
          "chef",
          "smelter",
          "animal_carer",
          "butcher",
          "fisherman",
          "cave_explorer",
          "hauler",
          "enchanter",
          "trader",
          "scout",
          "swordsman",
          "archer",
          "crossbowman",
          "axe_fighter"
         ]
        },
        "uniqueItems":true
       }
      },
      "propertyNames":{"anyOf":[{"enum":["type","name","unlock_level","affix","stacks","radius","roles"]},{"pattern":"^_"}]},
      "required":["affix"]
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"mend"}}},
     "then":{
      "properties":{
       "percent":{"type":"integer","minimum":1,"maximum":10},
       "every":{"type":"number","minimum":5,"maximum":30},
       "radius":{"type":"number","minimum":8,"maximum":24}
      },
      "propertyNames":{"anyOf":[{"enum":["type","name","unlock_level","percent","every","radius"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"lookout"}}},
     "then":{
      "properties":{"radius":{"type":"number","minimum":16,"maximum":48}},
      "propertyNames":{"anyOf":[{"enum":["type","name","unlock_level","radius"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"rally"}}},
     "then":{
      "properties":{"percent":{"type":"integer","minimum":5,"maximum":25}},
      "propertyNames":{"anyOf":[{"enum":["type","name","unlock_level","percent"]},{"pattern":"^_"}]}
     }
    },
    {
     "if":{"required":["type"],"properties":{"type":{"const":"drill"}}},
     "then":{
      "properties":{"xp":{"type":"integer","minimum":1,"maximum":3},"radius":{"type":"number","minimum":8,"maximum":24}},
      "propertyNames":{"anyOf":[{"enum":["type","name","unlock_level","xp","radius"]},{"pattern":"^_"}]}
     }
    }
   ]
  },
  "gear_id":{
   "type":"string",
   "pattern":"^(minecraft:)?(wooden_sword|stone_sword|iron_sword|golden_sword|wooden_axe|stone_axe|iron_axe|golden_axe|mace|bow|crossbow|trident|shield|turtle_helmet|leather_helmet|leather_chestplate|leather_leggings|leather_boots|chainmail_helmet|chainmail_chestplate|chainmail_leggings|chainmail_boots|iron_helmet|iron_chestplate|iron_leggings|iron_boots|golden_helmet|golden_chestplate|golden_leggings|golden_boots)$"
  },
  "gear_item":{
   "oneOf":[
    {"$ref":"#/$defs/gear_id"},
    {
     "type":"object",
     "required":["item"],
     "properties":{
      "item":{"$ref":"#/$defs/gear_id"},
      "name":{"type":"string","minLength":1,"maxLength":24},
      "enchantments":{
       "type":"object",
       "propertyNames":{
        "type":"string",
        "pattern":"^(minecraft:)?(sharpness|smite|bane_of_arthropods|knockback|fire_aspect|sweeping_edge|density|breach|power|punch|flame|quick_charge|piercing|multishot|impaling|loyalty|protection|fire_protection|blast_protection|projectile_protection|thorns|feather_falling|respiration|aqua_affinity|depth_strider|unbreaking)$"
       },
       "additionalProperties":{"type":"integer","minimum":1,"maximum":3},
       "maxProperties":3
      }
     },
     "patternProperties":{"^_":{}},
     "additionalProperties":false
    }
   ]
  },
  "line_list":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":1,"maxLength":120}},
  "lines":{
   "type":"object",
   "properties":{
    "summoned":{"$ref":"#/$defs/line_list"},
    "greeting":{"$ref":"#/$defs/line_list"},
    "idle":{"$ref":"#/$defs/line_list"},
    "fight_start":{"$ref":"#/$defs/line_list"},
    "skill":{"$ref":"#/$defs/line_list"},
    "ultimate":{"$ref":"#/$defs/line_list"},
    "low_health":{"$ref":"#/$defs/line_list"},
    "kill":{"$ref":"#/$defs/line_list"},
    "ally_fallen":{"$ref":"#/$defs/line_list"},
    "victory":{"$ref":"#/$defs/line_list"},
    "raid_start":{"$ref":"#/$defs/line_list"},
    "base_attacked":{"$ref":"#/$defs/line_list"},
    "level_up":{"$ref":"#/$defs/line_list"},
    "unlock":{"$ref":"#/$defs/line_list"},
    "fallen":{"$ref":"#/$defs/line_list"},
    "revived":{"$ref":"#/$defs/line_list"}
   },
   "patternProperties":{"^_":{}},
   "additionalProperties":false
  }
 }
}
```


## Appendix B: allowlists and caps

All ids below were checked against the 1.21.1 sources of the NeoForge 21.1.209 build (`ParticleTypes`, `SoundEvents`, `MobEffects`, `Items`, `Enchantments`). The in-game registry check (26.1 step 7) confirms them again at every start.

### B.1 Potion effects (`effect` type)

| Helpful (allies, self) | Max level | Max seconds |
|---|---|---|
| `speed` | II | 20 |
| `haste` | II | 30 |
| `strength` | II | 12 |
| `regeneration` | II | 10 |
| `resistance` | II | 10 |
| `fire_resistance` | I | 30 |
| `water_breathing` | I | 30 |
| `jump_boost` | II | 20 |
| `slow_falling` | I | 10 |
| `invisibility` | I | 6 (self only) |

| Harmful (enemies) | Max level | Max seconds |
|---|---|---|
| `slowness` | III | 8 |
| `weakness` | II | 8 |
| `mining_fatigue` | II | 10 |
| `poison` | II | 8 |
| `wither` | II | 6 |
| `glowing` | I | 15 |
| `levitation` | I | 3 (not renewed until the receiver has landed, so zones and multi-hits cannot lift anyone into fall damage) |
| `blindness` | I | 5 (players only) |
| `darkness` | I | 6 (players only) |

Refused with a pointer: `absorption` (use `shield`), `instant_health` (use `heal`), `instant_damage` (use `damage`), `health_boost`, `saturation`, `hunger`, `nausea`, `night_vision`, `luck`, `unluck`, `conduit_power`, `dolphins_grace`, `bad_omen`, `raid_omen`, `trial_omen`, `hero_of_the_village`, `wind_charged`, `weaving`, `oozing`, `infested`: "not available to heroes". Vanilla immunities apply (undead ignore poison and regeneration, the wither ignores wither).

### B.2 Particles (`look.particle`, `empowered.particle`)

`angry_villager`, `ash`, `bubble_pop`, `cherry_leaves`, `cloud`, `crimson_spore`, `crit`, `damage_indicator`, `dragon_breath`, `dust`, `electric_spark`, `enchant`, `enchanted_hit`, `end_rod`, `explosion`, `firework`, `flame`, `glow`, `gust`, `happy_villager`, `heart`, `large_smoke`, `nautilus`, `note`, `poof`, `portal`, `reverse_portal`, `scrape`, `sculk_soul`, `small_flame`, `small_gust`, `smoke`, `snowflake`, `sonic_boom`, `soul`, `soul_fire_flame`, `spore_blossom_air`, `splash`, `sweep_attack`, `totem_of_undying`, `trial_spawner_detection`, `warped_spore`, `wax_on`, `white_ash`, `witch`. All are parameterless vanilla particle types except `dust`, which takes `color` (`DustParticleOptions`, size 1). Particles that need block or item data, or that only show under water, are left out.

### B.3 Sounds (`look.sound`)

`block.amethyst_block.chime`, `block.amethyst_cluster.break`, `block.anvil.land`, `block.beacon.activate`, `block.beacon.power_select`, `block.bell.use`, `block.conduit.activate`, `block.enchantment_table.use`, `block.fire.ambient`, `block.glass.break`, `block.note_block.bell`, `block.respawn_anchor.charge`, `block.respawn_anchor.deplete`, `block.sculk_shrieker.shriek`, `entity.arrow.shoot`, `entity.blaze.ambient`, `entity.blaze.shoot`, `entity.breeze.shoot`, `entity.breeze.wind_burst`, `entity.dolphin.splash`, `entity.elder_guardian.curse`, `entity.ender_dragon.growl`, `entity.evoker.cast_spell`, `entity.evoker.prepare_attack`, `entity.evoker.prepare_summon`, `entity.experience_orb.pickup`, `entity.generic.explode`, `entity.ghast.shoot`, `entity.glow_squid.squirt`, `entity.goat.ram_impact`, `entity.illusioner.cast_spell`, `entity.illusioner.mirror_move`, `entity.illusioner.prepare_blindness`, `entity.iron_golem.attack`, `entity.lightning_bolt.thunder`, `entity.player.attack.crit`, `entity.player.attack.knockback`, `entity.player.attack.strong`, `entity.player.attack.sweep`, `entity.player.hurt_freeze`, `entity.player.levelup`, `entity.player.splash`, `entity.ravager.roar`, `entity.snowball.throw`, `entity.vex.charge`, `entity.warden.sonic_boom`, `entity.wind_charge.wind_burst`, `entity.wither.shoot`, `entity.wolf.howl`, `event.raid.horn`, `item.book.page_turn`, `item.crossbow.shoot`, `item.firecharge.use`, `item.flintandsteel.use`, `item.mace.smash_ground`, `item.shield.block`, `item.totem.use`, `item.trident.hit`, `item.trident.riptide_1`, `item.trident.throw`, `item.trident.thunder`.

### B.4 Gear items (`gear`)

`wooden_sword`, `stone_sword`, `iron_sword`, `golden_sword`, `wooden_axe`, `stone_axe`, `iron_axe`, `golden_axe`, `mace`, `bow`, `crossbow`, `trident`, `shield`, `turtle_helmet`, `leather_helmet`, `leather_chestplate`, `leather_leggings`, `leather_boots`, `chainmail_helmet`, `chainmail_chestplate`, `chainmail_leggings`, `chainmail_boots`, `iron_helmet`, `iron_chestplate`, `iron_leggings`, `iron_boots`, `golden_helmet`, `golden_chestplate`, `golden_leggings`, `golden_boots`. Refused with a reason: diamond and netherite items ("iron at most to start; the hero can still take better gear from the storehouse"), anything else ("not a weapon or armour piece").

### B.5 Enchantments (`gear[].enchantments`; the item must accept it under vanilla rules; at most 6 levels in the kit)

`sharpness` II, `smite` III, `bane_of_arthropods` III, `knockback` I, `fire_aspect` I, `sweeping_edge` II, `density` II, `breach` I, `power` II, `punch` I, `flame` I, `quick_charge` II, `piercing` II, `multishot` I, `impaling` II, `loyalty` I, `protection` II, `fire_protection` II, `blast_protection` II, `projectile_protection` II, `thorns` I, `feather_falling` II, `respiration` I, `aqua_affinity` I, `depth_strider` II, `unbreaking` III. Not allowed: `mending`, curses, `frost_walker` (changes blocks), `channeling` (lightning starts fires), `riptide`, `wind_burst`, `looting`, `fortune`, `silk_touch`, `efficiency`, `infinity`, `luck_of_the_sea`, `lure`, `soul_speed`, `swift_sneak`.

### B.6 Projectile looks

Ability `projectile` (`shot`, `volley`, passive `shot`): `arrow`, `spectral_arrow`, `trident`, `snowball`, `fire_charge`, `wind_charge`, `slime_ball`, `magma_cream`, `amethyst_shard`, `prismarine_shard`, `prismarine_crystals`, `echo_shard`, `blaze_powder`, `feather`, `bone`, `flint`, `heart_of_the_sea`, `nautilus_shell`. `arrow`, `spectral_arrow` and `trident` fly like arrows (gravity); the item looks fly straight (no gravity) and show the item, like a thrown snowball. `basic_attack.projectile` (style `thrown`) takes the item looks only. All of them stop at blocks without changing them and pass through allies.

### B.7 Held props (`basic_attack.held_item`)

`stick`, `blaze_rod`, `breeze_rod`, `book`, `amethyst_shard`, `echo_shard`, `prismarine_shard`, `heart_of_the_sea`, `nautilus_shell`, `feather`, `bone`, `spyglass`, `compass`, `clock`, `lantern`, `soul_lantern`, `poppy`, `cornflower`. Bound to the hero like kit items; never dropped.

### B.8 Summons (`summon.creature`)

| Creature | Most per cast | Weight factor |
|---|---|---|
| `wolf` | 3 | 1 |
| `snow_golem` | 2 | 0.5 |
| `iron_golem` | 1 | 3 |

## Appendix C: synonyms the loader accepts (each reported as ADJUSTED)

| Field | Synonyms |
|---|---|
| `combat_role` | `tank`, `guardian`, `defender`, `protector` → vanguard; `fighter`, `warrior`, `dps`, `brawler`, `assassin`, `berserker` → striker; `rogue`, `duelist`, `skirmish` → skirmisher; `archer`, `ranger`, `sniper`, `gunner`, `caster`, `mage` → marksman; `healer`, `buffer`, `medic`, `bard`, `sustain` → support |
| `element` | `neutral`, `normal`, `true` → physical; `flame`, `heat` → fire; `ice`, `cold`, `water` → frost; `lightning`, `electric`, `thunder`, `storm` → shock; `poison`, `nature`, `toxic` → venom; `shadow`, `dark`, `decay`, `void` → blight; `magic`, `mystic`, `spirit` → arcane; `holy`, `light`, `sacred` → radiant |
| `form` | `strike`, `slash`, `hit` → melee; `projectile`, `arrow`, `fireball` → shot; `multishot`, `barrage`, `fan` → volley; `beam`, `ray`, `spell`, `instant` → bolt; `bounce` → chain; `breath`, `sweep`, `arc` → cone; `pierce`, `lance` → line; `aoe`, `burst`, `shockwave`, `around_self` → nova; `ground`, `circle`, `area`, `rain`, `field` → zone; `jump`, `slam` → leap; `charge`, `rush` → dash; `buff_self` → self; `heal_ally` → ally; `team`, `party`, `group` → allies |
| effect `type` | `dmg`, `attack`, `hit` → damage; `damage_over_time`, `bleed`, `burn_damage` → dot; `heal_percent`, `restore` → heal; `barrier`, `absorb`, `absorption` → shield; `potion`, `buff`, `debuff`, `status` → effect; `modifier`, `stat_buff` → stat; `push` → knockback; `burn`, `fire` → ignite; `chill`, `frost` → freeze; `daze`, `paralyze` → stun; `provoke` → taunt; `purify` → cleanse; `strip`, `purge` → dispel; `charge` → energy; `reset`, `refresh` → cooldown_cut; `hop`, `jump_back`, `evade` → move_self; `stack`, `brand` → mark; `consume_marks`, `detonate` → clear_marks; `transform`, `enhance`, `stance` → empower. A potion name used as a type (`"type": "slowness"`) is read as `{"type": "effect", "id": "slowness"}`. |
| `use_when` | `hp_below`, `low_health` → self_health_below; `ally_low_health`, `heal_needed` → ally_health_below; `execute`, `finisher` → target_health_below; `crowd`, `many_enemies` → enemies_near; `gap_close` → target_far; `escape`, `danger` → target_close; `boss` → big_fight; `idle`, `peace` → out_of_combat |
| `trigger` | `on_attack` → on_hit; `on_critical` → on_crit; `on_damaged`, `when_hit` → on_hurt; `on_cast` → on_skill; `periodic`, `interval` → every; `on_combat_start` → on_fight_start; `follow_up` → on_ally_attack; `on_stacks_full` → on_mark_full |
| base ability `type` | `work_aura`, `buff_workers` → aura; `heal_between_fights`, `medic`, `regen` → mend; `watch`, `sentry` → lookout; `leadership`, `recruit_discount` → rally; `training`, `drill_master` → drill |
| `lines` events | `join`, `recruited` → summoned; `hello`, `talk` → greeting; `death`, `dies` → fallen; `respawn`, `return` → revived; `win` → victory; `levelup` → level_up; `combat_start` → fight_start |

The synonym tables live in `HeroCatalogue`; a synonym never overrides a real value of the same field.

