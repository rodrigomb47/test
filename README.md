# Faction Clash

An autonomous faction-war simulation mod for **Minecraft Java 1.21.1 / NeoForge 21.1.209 / Java 21**, written
from scratch from the Faction Clash master specification. Four human factions — **Ember Legion, Grove
Covenant, Tide Alliance, Stoneguard** — and the monster faction **The Hollow** build, mine, farm, scout, recruit,
repair and raid on a bounded campaign map without the player having to manage them.

## Download

**[⬇ Download factionclash-0.1.0.jar](https://github.com/rodrigomb47/test/raw/claude/minecraft-mod-build-9amjyg/download/factionclash-0.1.0.jar)**
(NeoForge 21.1.209, Minecraft 1.21.1 — put it in the `mods` folder of the server and every client)

SHA-256: `a81ba868c3617648f496d4ad4a84fb1bbe8d29b7fd15fe075f02c8ba22de2bf0`

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

* **Gates with doors**: each base wall has a west and an east gate with wooden double doors that players and NPCs
  open to go in and out (monsters cannot open them). NPCs coming home from outside head for the nearest gate;
  NPCs leaving take the gate with the shortest walk to where they are going, and long trips past their own base
  go around its walls.
* **Getting unstuck like a player**: an NPC first gets a small validated hop (≤ 4 across / ≤ 2 up-down by
  default, configurable). If it truly has no way (a sealed cave pocket, the floor of a ravine or pit, a tree
  canopy or the top of a tall trunk) it **digs its own way out**, a staircase step by step toward its
  destination's height (or chops the leaves/log it stands on), until it can walk again. It never digs faction
  structures, walls, mine stairs, containers or anything next to water or lava. A watchdog also catches NPCs that
  "walk" for 30 s without leaving the same spot.
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
    right tool, ×100 without), shows the crack animation, and gives the block's own loot only when the tool can
    harvest it (a wooden pickaxe gets nothing from iron ore). What a miner, woodcutter or farmer breaks goes
    straight into its backpack, as a player's mined blocks go into the inventory; only what does not fit (or junk
    a miner no longer wants) drops at its feet. Things that fall on their own (decaying leaves, eggs, wool) are
    walked over and picked up.
  * Output goes into the worker's backpack and is carried to storage; full storage pauses work without deleting
    anything.
* **Mines** are dug by the miner: a roofed hut over a 2×2 spiral staircase (one step down per block, a landing
  every 4 steps), then a **branch mine** on each level (a 40-block main tunnel with 10-block side branches every
  4 blocks, torches every 8 blocks). Levels: tier 1 ≈ 6 below the surface, tier 2 at y=32 (iron), tier 3 at y=0
  (diamonds), tier 4 at y=−48, then more levels 6 deeper down to y=−56. When ~60% of a level is mined, the miner
  digs the spiral down to the next one; tiers 2–4 are paid with a pickaxe, and tier 3+ needs an iron pickaxe in
  hand. When the bottom is reached, more branch mines are dug between levels that are 12+ blocks apart, so a
  miner keeps finding new rock. A fully worked-out mine sends its miner to a surveyed cave if there is one;
  otherwise the miner **lengthens its branch mines** like a player would: it goes back to each regular level
  (deepest first) and continues the main tunnel 40 blocks further with new branches (up to 4 rounds). Stair
  steps whose floor was dug away are repaired with stone from the miner's backpack.
  Miners follow exposed ore veins, plug water, lava and loose sand before opening a cell (or dig through
  falling sand like a player), bridge cave holes in the tunnel floor, stop a branch where it breaks out of a
  hillside, and stop collecting cobblestone-type junk once storage holds 512 of it.
* **Tree plots** grow from 4 to 9 to 16 trees. Woodcutters fell trees log by log with their axe, break the
  canopy (the leaves' own loot drops saplings, sticks and apples), collect what fell, replant, and let saplings
  grow naturally — they only speed growth up with real bone meal (made by the blacksmith from bones). While the
  plot's saplings grow they also cut trees outside their plot: wild trees within 64 blocks (never in a base or
  another faction's plot), replanting a sapling where each one stood.
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
* **Soldiers**: swordsmen, axe fighters, archers and crossbowmen. Melee timing follows the held weapon's real
  attack speed (a sword about every 12 ticks, an axe every 20–25 but harder, and axes knock shields down);
  crossbows charge for their real charge time and fire faster, straighter bolts a little farther than bows.
  Every third melee recruit carries an axe, and every third ranged recruit a crossbow when one can be paid for.
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
| §4 Roles: crossbowman, axe fighter | Implemented | G (crossbow fire, weapon attack speed) |
| §4 Roles: enchanter, field medic, TNT expert, rail engineer | ✗ not implemented | — |
| §5–6 Workspaces, mines dug by the miner (spiral shaft, branch mine, tier levels, pickaxe-gated drops) | Implemented (player-like) | U (geometry), G (break times, drops), S |
| §7 Natural cave mining | Implemented (surveyed by explorers, mined by miners; depletion is real ore running out) | U, G, S |
| §8 Tree plots + upgrades, farm, chef | Implemented (player-like woodcutting and farming) | S |
| §8 Pens/breeding/butchering, fishing | Implemented | G, S |
| §9 Golden apples used by NPCs | Implemented (soldiers carry one; eaten when badly hurt in a fight; blacksmith crafts them) | G |
| §10 Categorised storage, blacksmith batches, soldier armour priority | Implemented (no hauler role) | G (storage), S |
| §11 Builder projects, staging chest + signs, repairs, player-edit preservation | Implemented (houses, storage expansion) | S |
| §11 Perimeter expansion, outposts, procedural building variety | ✗ | — |
| §12 Scouts, reports, map board, stale sightings, retreat, overdue follow-up | Implemented (chat-based map) | S |
| §12 Radio / communication tower | ✗ | — |
| §13 Player-equivalent speed, allies pass through each other, stuck recovery (default ≤4 across / ≤2 up-down, configurable), day/night continuity | Implemented | G (4.25 blocks/s; recovery), U, S |
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

* `./gradlew test` — 32 JUnit tests for the pure rules (spiral mine, branch-mine and lengthening geometry, infill
  levels, cave survey walk graph, food payment, affix caps, XP curve, stuck-recovery selection, scout rules).
* `./gradlew runGameTestServer` — 27 GameTests on a real headless server, including **digging out** (sealed in
  rock it climbs out by a staircase; on a canopy or a tall trunk it chops its way down; just above its goal it digs
  down), cave explorers seeing only visible ore and placing real torches, haulers sorting the storehouse,
  crossbow charging, player attack speeds, golden apples and mined blocks going into the backpack, plus: walking speed, no gear wear, half
  undead damage only, no friendly fire, one-arrow ranged attacks with effects, save/reload without affix
  stacking, local stuck recovery, creeper jump-back, mob targeting, full storage never deletes items,
  recruitment payment routes, guards engaging enemies, defence alarm, **player break times** (stone pickaxe on
  stone 12 ticks, iron 8, bare hand 150), **real drops and pickup** (a wooden pickaxe gets nothing from iron
  ore), **breeding by feeding** (two fed cows make a calf; babies can't be fed to breed) and **butcher rules**
  (babies and the breeding stock are spared).
* `tools/sim.py` + `tools/rcon.py` drive a real NeoForge dedicated server over RCON (`/tick sprint`) and print a
  per-day summary of every faction (see the source for usage).

### Dedicated-server simulation results (installed jar, NeoForge 21.1.209, fixed seed)

**Long campaign with this version** — one world, nothing spawned by hand, run to **day 21.6** with about a dozen
server restarts (each one also tested save/reload) while fixes were rolled in:
* **War**: Ember Legion raided The Hollow from day 5; both monster lairs were destroyed by day 11, eliminating The
  Hollow. Ember went on to raid Grove Covenant (11 raids in all). Grove launched its first raid on day ~21. No
  winner yet: the four human factions were all alive with 14–28 NPCs each.
* **Mines**: every faction's mine reached tier 4 (y = −48) and all its infill levels. Once fully worked out (around
  day 12), miners started **lengthening** the branch mines and found new deepslate diamond and iron veins (Grove
  went from 3 to 24 diamonds in two days). Mine output by day 21: Tide 9,600 blocks, Ember 6,700, Stoneguard 5,000
  (its mine lies under a lake and was stuck for 11 days until the dig-out fix, then reached tier 4 and 45 diamonds
  in four days), Grove 4,800.
* **Caves**: explorers surveyed 47 caves (most had no visible ore); cave miners worked 13 of them, mining about
  1,900 blocks, and handed each back once worked out.
* **Wood**: Ember's tree plot reached tier 3 with 3,400 logs, Tide's 1,300. Grove's woodcutter was stuck for 15 days
  waiting for fence wood that only it could bring (fixed: it now cuts more first), then enlarged its plot.
* **Stuck NPCs**: before the dig-out fix a Grove explorer, two scouts and an Ember swordsman stayed frozen for days
  (cave ledge under the base, ravine floor, tree canopy, pocket under the gate). With it, all of them dug their way
  out within one in-game hour and went back to work.
* No equipped tool or armour piece lost durability; max NPC level 14.

Known rough edges (from this run): NPCs do not build bridges, so a few that end up on the far side of a ravine
next to their base dig around for a long time; a woodcutter stuck on a hill outside its plot's fence can stay
there; extra miners idle when there is no cave to work (the mine has one miner); animals can still slip out of the
pen occasionally; fishing has no visible bobber.

**Mines from campaigns started with the first version are not compatible** with the new mine design — start a
new campaign (`/fc start`) after updating.

The original project's source, saves and balance numbers were not available, so this is a fresh implementation;
existing old-mod saves are not compatible with it.
