# Faction Clash

An autonomous faction-war simulation mod for **Minecraft Java 1.21.1 / NeoForge 21.1.209 / Java 21**, written
from scratch from the Faction Clash master specification. Four human factions — **Ember Legion, Grove
Covenant, Tide Alliance, Stoneguard** — and the monster faction **The Hollow** build, mine, farm, scout, recruit,
repair and raid on a bounded campaign map without the player having to manage them.

## Download

**[⬇ Download factionclash-0.1.0.jar](https://github.com/rodrigomb47/test/raw/claude/minecraft-mod-build-9amjyg/download/factionclash-0.1.0.jar)**
(NeoForge 21.1.209, Minecraft 1.21.1 — put it in the `mods` folder of the server and every client)

SHA-256: `3d1d1d59025b773813779e6809d98ca112f5bff6bc842507922ffb23cb963ba6`

This is the **foundation build** (spec §21 priority order): safe starts, a working central station, useful
starting workers, movement, storage and uninterrupted workspace production first, then recruitment, upgrades,
construction, scouting, combat, levels and raids. **Workers do their jobs like players** — miners really dig,
woodcutters really chop, farmers harvest, animal carers breed by feeding, butchers and fishermen work with real
weapons and rods. Several later systems from the spec are not built yet — see [Status](#status) for an honest
breakdown.

## Install

1. Install **NeoForge 21.1.209** for Minecraft **1.21.1** (Java 21) — client and/or server.
2. Put `factionclash-0.1.0.jar` in the `mods` folder (it is needed on both the server and every client).
3. Start the world/server. As an operator run `/fc start` (or `/fc start <radius>`). Preparing a campaign takes
   1–2 minutes of background work; progress is announced in chat and the server keeps running.
4. Players: `/fc join <ember_legion|grove_covenant|tide_alliance|stoneguard>`, then `/fc base` to teleport home.

No other mods are required. Build from source with `./gradlew build` (jar in `build/libs/`).

## Playing

| Command | Who | What |
|---|---|---|
| `/fc start [radius]` | op | Start a new campaign (map centred on world spawn) |
| `/fc status` | all | Which factions still stand; the winner when there is one |
| `/fc join <faction>` / `/fc leave` | all | Join or leave a human faction |
| `/fc base` | member | Teleport to your faction's base (the player teleport; NPCs always travel physically) |
| `/fc info`, `/fc map` | member | Base report and your faction's delivered map knowledge |
| `/fc admin dump\|npcs\|info <f>\|spawn <f> <role>\|recruit <f> <role>\|probe <x> <z>\|mine <id>\|blocks <from> <to>\|destroy <id>` | op | Diagnostics, kept separate from player commands |

In the world:
* **Reporting Station** (right-click): opens a scrollable screen with population, stock, recruitment decision,
  workspaces and tiers, surveyed caves, projects, repairs, delivered reports and raids. Only members (and ops) see
  their own faction's records.
* **World Map Board** (right-click): opens a drawn map of the campaign area — explored sectors shaded, your
  bases, and every report your scouts physically delivered as a marker (enemy settlements, lairs, monsters, cave
  entrances, exposed ore, surveyed caves; hover a marker for details; moving things fade when *stale*), your own
  position, a legend and the written report list.
* **NPCs** (right-click): a screen with identity, job, equipment, backpack, current task, destination, workplace,
  level and affixes. Sneak + right-click with a better tool, weapon, armour piece or arrow to hand it over — the
  replaced item disappears.
* **Construction sign** next to the staging chest lists what the current project still needs.
* **Faction Heart**: destroy it and the base falls. Members' deliberate edits inside their own base are
  preserved by builders; damage from mobs, explosions or enemies is repaired.

Players respawn at their nearest surviving allied base when they have no bed.

## How the simulation works

* **Starting bases** are placed on dry, gentle land: each faction searches its own map sector on real generated
  terrain, widens to the whole map (and a 1.5× map) if needed, and levels the footprint including the mine and
  tree-plot pads. Every base starts with a finished reporting station, map board, faction heart, categorised
  storage (food, raw, building, tools, weapons, armour, special, general), workshop (crafting table, 2 furnaces,
  smoker, anvil, enchanting table with bookshelves), 10 beds, a farm, a pen with 2 cows and 2 pigs, walls with
  gates, lighting, and outside the walls a tier-1 constructed mine and a fenced tree plot. Starter supplies are
  finite and granted once (saved flag).
* **Ten starting NPCs**: builder, miner, farmer, woodcutter, blacksmith, chef, scout and three swordsmen. Animal
  carers, butchers, smelters, fishermen, cave explorers, extra miners for caves and haulers are recruited when
  the base needs them and has a bed free.
* **No hunger, no wear**: NPCs never eat or starve; equipped gear never loses durability; archers hold one arrow
  that is never used up (tipped/spectral effects apply to every shot, and shots cannot be picked up).
* **Workers work like players.** Nothing is produced by a timer or a loot roll: every item comes from a block a
  worker really broke, an animal it really bred or killed, or a vanilla loot table a player would also roll.
  * Breaking uses the player formula (tool speed, Efficiency, Haste, underwater and in-air penalties, ×30 with the
    right tool, ×100 without), shows the crack animation, and drops the block's own loot only when the tool can
    harvest it (a wooden pickaxe gets nothing from iron ore). Drops land in the world and the worker walks over
    and picks them up.
  * Output goes into the worker's backpack and is carried to storage; full storage pauses work without deleting
    anything.
* **Mines** are dug by the miner: a roofed hut over a 2×2 spiral staircase (one step down per block, a landing
  every 4 steps), then a **branch mine** on each level (a 40-block main tunnel with 10-block side branches every
  4 blocks, torches every 8 blocks). Levels: tier 1 ≈ 6 below the surface, tier 2 at y=32 (iron), tier 3 at y=0
  (diamonds), tier 4 at y=−48, then more levels 6 deeper down to y=−56. When ~60% of a level is mined, the miner
  digs the spiral down to the next one; tiers 2–4 are paid with a pickaxe, and tier 3+ needs an iron pickaxe in
  hand. Miners follow exposed ore veins, plug water, lava and loose sand before opening a cell (or dig through
  falling sand like a player), bridge cave holes in the tunnel floor, stop a branch where it breaks out of a
  hillside, and stop collecting cobblestone-type junk once storage holds 512 of it.
* **Tree plots** grow from 4 to 9 to 16 trees. Woodcutters fell trees log by log with their axe, break the
  canopy (the leaves' own loot drops saplings, sticks and apples), collect what fell, replant, and let saplings
  grow naturally — they only speed growth up with real bone meal (made by the blacksmith from bones). While the
  plot's saplings grow they fell wild trees within 40 blocks of the plot (never in a base or another plot) and
  replant a sapling where each one stood.
* **Cave explorers** go to cave entrances the scouts reported, find the way in, and explore the cave on foot:
  they light dark spots with real torches, note every ore they can actually see (an exposed face and a clear
  line of sight — nothing behind rock), and build steps with placed cobblestone or dirt down drops of two blocks
  or more so the way stays walkable both ways. They never mine. They turn back when out of torches, wounded,
  attacked or done, and hand the survey (the route walked and the ores seen) in at the reporting station.
* **Cave mining**: a miner without the constructed mine works a surveyed cave. It walks the explorer's route,
  mines the marked ores and every ore it uncovers, tunnels a short way to ores it cannot otherwise reach, then
  digs prospecting tunnels from the deepest spot into the cave walls. There is no artificial supply limit — the
  cave holds exactly the ore that is really in it; when it is worked out the miner hands it back. Bases take on
  up to two cave miners.
* **Haulers** (from 12 NPCs on) keep the base tidy: they pick up items lying around inside the walls and re-sort
  the storehouse, carrying stacks from the overflow chest or a wrong chest to the chests of their category.
* **Farmers** harvest ripe crops by breaking them, collect the drops, till, and replant; the first row of the
  farm is a carrot row so the pigs can be bred.
* **Animal carers** go into the pen through the gate (waiting for the animals to move away from it and closing
  it behind them), feed two adults the food their species eats (wheat for cows and sheep, carrots, potatoes or
  beetroot for pigs, seeds for chickens) so vanilla breeding makes a real baby, shear sheep, milk cows with
  buckets, pick up eggs and wool, and lure animals that got out back into the pen by holding their food. Pens
  stop breeding at 8 animals per species.
* **Butchers** kill only surplus adults with real sword hits — never babies, never animals that are breeding,
  and never below 4 breeding adults per species — and collect the meat and leather.
* **Fishermen** stand at the farm's water channel and fish with a rod: vanilla bite timing (100–600 ticks plus
  20–80, shortened by Lure) and the vanilla fishing loot table (Luck of the Sea applies). There is no visible
  bobber entity.
* **Farmer, chef, smelter, blacksmith** use the real farm, smoker, furnaces and crafting recipes. The blacksmith
  crafts batches from real needs: mine-upgrade pickaxes, better worker tools, iron swords and armour for soldiers
  first, bows, torches, sticks, diamond gear when diamonds exist.
* **Recruitment** replaces missing workers and grows the garrison (40% archers by default) up to the number of
  beds; it charges food by normal hunger value plus exactly one equipment payment route.
* **Builders** queue houses (with straw bedrolls made from wheat) and storage expansions, carry materials to the
  staging chest, build bottom-up from saved plans with substitutes for unavailable styled blocks, and repair any
  damaged plan block.
* **Scouts** explore unexplored map sectors, record only what they see and what is worth a trip (enemy
  settlements, lairs, monsters, cave entrances they can see a way into, exposed ore — not trees or water, which
  are easy to find), retreat home on danger along a fixed route, and publish by reaching the station.
* **War**: guards patrol, pick up loot and answer a base defence alarm; hostile vanilla mobs hunt NPCs; zombies
  and skeletons do half damage to NPCs; wounded civilians flee and call guards; NPCs jump back from priming
  creepers. Factions raid targets their scouts reported, keeping home defenders; raiders travel physically, break
  the target's heart, loot its storage and carry it home. Hollow lairs grow and raid nearby settlements.
* **Levels**: completed work earns XP for the NPC that did it; every level grants one capped affix (e.g. faster
  digging, instant sapling regrowth); reloads never re-stack bonuses.
* **Background simulation** is bounded: base areas and travelling NPCs hold ticking chunk tickets, so factions
  progress away from players without loading the whole map.

All numbers that the spec left open are documented as proposals in [BALANCE.md](BALANCE.md) and most are
configurable.

### Config file

The config is **not** part of this repository: NeoForge creates `config/factionclash-common.toml` inside your
Minecraft instance (or server) folder the first time the game starts with the mod. Edit that file while the game
is closed. A copy with the default values is in [docs/factionclash-common.toml](docs/factionclash-common.toml)
for reference; you can also place it in your `config` folder before the first launch.

## Status

Legend — **U** unit test, **G** in-game GameTest (headless server), **S** observed in the multi-day dedicated
server simulation, **—** implemented but not separately verified, **✗** not implemented.

| Spec area | State | Verified |
|---|---|---|
| §1 Factions, bounded map, join/teleport/respawn, last-faction-standing victory | Implemented (victory rule proposed: faction heart) | S (bases destroyed, a faction eliminated); player join/respawn — (no client was connected) |
| §2 No hunger, nutrition-weighted recruitment, no gear wear, equipment cost, one arrow, finished station at start | Implemented | U, G, S |
| §2/§16 Great People (HSR/ZZZ characters), starter hero, 10-minute revival | ✗ not implemented | — |
| §3 Safe starting bases, roster, supplies once, animals, enchanting setup | Implemented | S (incl. save/reload) |
| §4 Roles: builder, miner, farmer, woodcutter, blacksmith, chef, smelter, scout, swordsman, archer | Implemented | S |
| §4 Roles: animal carer, butcher, fisherman | Implemented (player-like) | G (breeding, culling rules), S |
| §4 Roles: cave explorer, hauler/quartermaster | Implemented | U (survey rules), G (ore sight, torches, sorting), S |
| §4 Roles: enchanter, axe fighter, crossbowman, field medic, TNT expert, rail engineer | ✗ not implemented | — |
| §5–6 Workspaces, mines dug by the miner (spiral shaft, branch mine, tier levels, pickaxe-gated drops) | Implemented (player-like) | U (geometry), G (break times, drops), S |
| §7 Natural cave mining | Implemented (surveyed by explorers, mined by miners; depletion is real ore running out) | U, G, S |
| §8 Tree plots + upgrades, farm, chef | Implemented (player-like woodcutting and farming) | S |
| §8 Pens/breeding/butchering, fishing | Implemented | G, S |
| §9 Golden apples used by NPCs | ✗ | — |
| §10 Categorised storage, blacksmith batches, soldier armour priority | Implemented (no hauler role) | G (storage), S |
| §11 Builder projects, staging chest + signs, repairs, player-edit preservation | Implemented (houses, storage expansion) | S |
| §11 Perimeter expansion, outposts, procedural building variety | ✗ | — |
| §12 Scouts, reports, map board, stale sightings, retreat, overdue follow-up | Implemented (chat-based map) | S |
| §12 Radio / communication tower | ✗ | — |
| §13 Player-equivalent speed, allies pass through each other, ≤2/≤1 stuck recovery, day/night continuity | Implemented | G (4.25 blocks/s; recovery), S |
| §14 Mob targeting, half undead damage, civilian flee, creeper jump-back, raids, defence alarm | Implemented | G, S |
| §14 Field medics, potions | ✗ | — |
| §15 Overworld/Nether dungeons, Nether expeditions, TNT, rails | ✗ (Hollow lairs are the only monster bases) | — |
| §17 Boss reward chests and hero tokens | ✗ | — |
| §18 Work XP, levels, random capped affixes, no re-stacking | Implemented | U, G, S |
| §19 Inspection, station/map info, signs | Implemented (station, map board and NPC screens) | S; screens checked in a real client |
| §20 Persistence, bounded background simulation, no duplication on reload | Implemented | G, S (restart comparison) |

Graphical check: a real 1.21.1 client (software OpenGL on a virtual display) joined the test server with the
mod and rendered the bases, blocks, NPC skins, armour and held items, and opened the station and map board
screens (screenshots below; the screens were opened with `/fc info` and `/fc map`, which send the same screens
as right-clicking the blocks). No interactive play session by a human player was performed.

![Ember Legion base](docs/screenshots/ember_legion_base.png)
![NPCs in the plaza](docs/screenshots/ember_legion_plaza_npcs.png)

The reporting station and world map board screens, captured in the same real client:

![Reporting station screen](docs/screenshots/station_screen.png)
![World map board screen](docs/screenshots/map_board_screen.png)

## Testing

* `./gradlew test` — 18 JUnit tests for the pure rules (spiral mine and branch-mine geometry, food payment,
  affix caps, XP curve, stuck-recovery selection, scout rules).
* `./gradlew runGameTestServer` — 17 GameTests on a real headless server: walking speed, no gear wear, half
  undead damage only, no friendly fire, one-arrow ranged attacks with effects, save/reload without affix
  stacking, local stuck recovery, creeper jump-back, mob targeting, full storage never deletes items,
  recruitment payment routes, guards engaging enemies, defence alarm, **player break times** (stone pickaxe on
  stone 12 ticks, iron 8, bare hand 150), **real drops and pickup** (a wooden pickaxe gets nothing from iron
  ore), **breeding by feeding** (two fed cows make a calf; babies can't be fed to breed) and **butcher rules**
  (babies and the breeding stock are spared).
* `tools/sim.py` + `tools/rcon.py` drive a real NeoForge dedicated server over RCON (`/tick sprint`) and print a
  per-day summary of every faction (see the source for usage).

### Dedicated-server simulation results (installed jar, NeoForge 21.1.209, fixed seed)

**Player-like workers (this version)** — 3-day campaign on a fresh world, nothing spawned by hand, with a server
restart after day 2:
* Mines are real holes in the world: three of the four miners dug their spiral and branch mines down to tier 4
  (y = −48) within two days, mining 1,650–1,900 real blocks each; storage filled with the actual drops (cobbled
  deepslate, redstone, lapis, raw iron, copper, and 3 diamonds at one base). The fourth mine lies under a lake in
  sand and progresses slowly, as the miner has to plug water with what it has.
* Builders added a house at every base (10 → 14 beds), which let every base recruit its own animal carer, plus
  smelters. Carers bred the pigs from 2 to 8 per pen and bred a calf; no pen lost an animal.
* In earlier runs with butchers and fishermen added by admin command: butchers culled only surplus pigs (each
  death is logged with its cause), and a fisherman caught ~60 items per in-game day from the vanilla fishing loot
  table.
* Woodcutters felled and replanted naturally growing trees (≈ 100–130 logs per plot in 3 days).
* No equipped tool or armour piece lost durability.
* Restart: all 53 roster entries and 52 NPCs came back with identical identities, levels and affixes, no
  duplicates, starter supplies not re-granted, storage and pens unchanged.

Because everything is produced the way a player would produce it, the economy is **much slower than in the
first version** — wood in particular (trees grow at vanilla speed), so recruits that need sticks or a stone sword
can wait for the woodcutter.

Known rough edges: scouts sometimes get trapped in caves or on coastal terrain and need the dig-out recovery
(which can take a while); a mine dug under a lake or through sand progresses slowly; animals can still slip out
of the pen occasionally (carers lure cows and pigs back with their food when they have it); fishing has no
visible bobber; the earlier 8-day results (raids destroying lairs, victory announcement) were measured with the
first version and were not re-run for this one.

**Mines from campaigns started with the first version are not compatible** with the new mine design — start a
new campaign (`/fc start`) after updating.

The original project's source, saves and balance numbers were not available, so this is a fresh implementation;
existing old-mod saves are not compatible with it.
