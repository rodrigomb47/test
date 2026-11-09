# Faction Clash — proposed balance values

The original project's numbers (map size, loot tables, upgrade bills, recruitment prices, affix caps, …) were
not available, so every value below is a **PROPOSED** default, chosen to make the simulation progress at a
watchable pace. Most are configurable in `config/factionclash-common.toml` (created in your Minecraft or server
`config` folder on first launch; defaults in [docs/factionclash-common.toml](docs/factionclash-common.toml)); the rest are constants named in the
source.

## Movement (config `movement`)
| Value | Default | Effect |
|---|---|---|
| `unstuckHorizontal` | 4 (1–8) | How far across the stuck-recovery teleport may move an NPC to a safe spot closer to its goal. |
| `unstuckVertical` | 2 (1–4) | How far up or down it may move it. |

Not configurable (`NpcNavigator`, `DigOut`, `Job`):
* A trip fails after repeated trouble: no path, no progress for 300 ticks (getting no closer), or 600 ticks of
  travelling without leaving a 3-block radius (flipping between destinations). Switching destinations while stuck
  in the same spot keeps the count.
* After two failed trips an NPC digs. Shut in (under cover, up a tree on 3+ blocks of wood or leaves, or more than
  1 block below its destination: a pit, a ravine floor, the foot of a cliff) it keeps digging (or building) its way,
  one step per 20 ticks and at most 64 steps per round: up if the destination is above, down if below, otherwise
  level. Only a walking route ends the digging (looked for after every step in the open, every 4th under cover,
  never before a round's first step): being out in the open is not enough, a pit a few blocks below the
  destination is still a dead end. A round that
  ends without a route (64 steps spent, or no step possible) makes the next one head 16 blocks off to one side,
  then the other side, then straight away, then toward the destination again (PROPOSED), always to the
  destination's height; arriving anywhere resets this. Under a settlement's edge it first heads out from under the
  walls. Never dug: faction structures, a settlement's walls and floor band, any mine's spiral stairs, containers,
  unbreakable blocks and anything touching a fluid. Up a tree it breaks the leaves or log underfoot (the wood goes
  into its backpack).
* Recovery teleports never land on leaves or on top of a tall trunk.
* Bridging: where the step toward the destination has no floor, an NPC places a block of stone, cobblestone,
  deepslate, dirt, andesite, diorite, granite or tuff from its backpack (else any full solid block it carries:
  logs, planks...) and steps onto it (rising one block per step when the destination is above); never inside
  protected ground.
* Climbing, in order: a dug step up toward the destination, a level step, a bridge step in any other direction, a
  pillar (a block placed underfoot, the cell two above must be clear), and last a step back down. Out in the open
  (not under cover) it first tries only ways up: a rising bridge step toward the destination, a step dug up in any
  direction, then a pillar; level steps come after (beside a settlement's floor, which may not be dug, a level step
  led into a hole under it and back). Stepping away
  from the destination or back down marks the spot left as a dead end it will not return to; it also never steps
  back onto one of the last 6 spots it left. Both are forgotten on arrival or 12000 ticks after the last step.
* In a mine stairwell (the 2x2 spiral column below a mine's mouth, or an old mine's hut floor) it climbs the
  spiral one step at a time and puts back a missing stair floor with a block from its backpack. Miners do not
  follow ore veins into the stairs or the rock ring around them.
* Leaving a settlement, NPCs head for the gate with the shortest walk to the destination; long trips past their own
  base put their waypoints around the walls. Under a roof at home (a gate's arch, a house) counts as above ground
  for this.
* Pits (PROPOSED, `NpcNavigator.PIT_DEPTH` = 8): a long-trip waypoint whose ground lies more than 8 blocks below
  both the NPC and its destination (the floor of a ravine) is not used while another turn (up to ±85 degrees) is
  not one. A path that only gets as far as such a pit floor is not followed either: the NPC first walks to a point
  24 blocks off to one side (left first) whose path does not lead down into it, at most once per 100 ticks; never
  a point back inside its own walls when the trip leaves or passes them. With no such point the path is followed.
* A path search that finds nothing but the spot the NPC stands on is repeated after 20 ticks, not every tick.
* Underground (a mine, a cave), cells beside water cost no more than others in path searches (vanilla adds 8, as
  for a river bank, which made a mine's tunnels sealed against an aquifer cost more than a way round over the
  surface); on the surface the vanilla cost stays.
* Miners walk along a mine level in stretches (PROPOSED, `MinerJob.TUNNEL_LEG` = 24): to a destination more than
  24 blocks away (a tunnel face far from the shaft, or back), they walk to the dug tunnel cell within 24 blocks that
  lies nearest it, at least 4 blocks closer, then on from there (vanilla path searches reach about 48 blocks). A
  miner well away from its mine (14+ blocks from the shaft and outside its tunnels) heads for the mouth first,
  whatever is overhead.
* Base terrain: the 5-block slope round the levelled pads (one block per block) is also made where a column has no
  ground within its reach (19 blocks below the pads' surface, a ravine beside the walls): it is filled with dirt.

## Campaign (config `campaign`)
| Value | Default | Effect |
|---|---|---|
| `mapRadius` | 320 | Half-width of the campaign map around world spawn. The start-up search may enlarge it ×1.5 if a faction's sector has no dry land (logged). |
| `humanFactions` / `monsterLairs` | 4 / 2 | Ember Legion, Grove Covenant, Tide Alliance, Stoneguard; two Hollow lairs. |
| `keepFactionAreasLoaded` | true | Bases, workplaces and travelling NPCs keep ticking without players (bounded chunk tickets). |

## Player-like work (`BlockWork`)
* Break time per tick = tool speed (+ Efficiency, Haste/Mining Fatigue, ×0.2 underwater without Aqua Affinity,
  ×0.2 when not on the ground, × (1 + Diligence)) ÷ hardness ÷ 30 with a tool that can harvest the block (÷ 100
  without). This is the vanilla player formula: stone with a stone pickaxe 12 ticks, with an iron pickaxe 8.
* Drops come from the block's own loot table, only with a tool that can harvest it, and go straight into the
  breaking worker's backpack (overflow drops at its feet). Item pickup box for things lying around: the NPC's
  box grown by 1 x 0.5 x 1 blocks, like a player's.
* Reach: 4.5 blocks for mining, 7.5 for a tree trunk from the ground (tall trunks), 5.5 for leaves.

## Workplaces (config `workplaces`)
| Value | Default | Effect |
|---|---|---|
| `mineSiteRadius` | 64 (40–128) | Farthest distance (Chebyshev) from the base centre at which a miner opens a mine. The search starts at the walls' half-size + 8 (32), which a mouth must lie beyond, so the nearest mouth is 36 out. |
| `maxOpenMines` | 3 (1–8) | Mines a base works at once (unfinished and not resting); each miner works one. |
| `forestRadius` | 64 (41–128) | Woodcutters fell natural trees within this many blocks of the base centre. At least 41 so the starter saplings (40.3 out) are inside it. |
| `groveSize` | 16 (0–32) | Saplings kept growing in the base's always-ticking belt, twice as many (up to 32) while the base is short of wood. 0 = only replant felled trees. A config file written by an earlier build keeps its old default of 8: set 16 by hand. |
| `fishingRadius` | 64 (32–128) | Fishing water is searched for within this many blocks of the base centre; water found while foraging may lie 40 further. |
| `fishingTreasure` | true | In open water (vanilla's 5x5, 2-deep, open-sky test) the vanilla treasure catches can be fished, as by a player. |
| `remoteWorkInDaylightOnly` | true | Between dusk and dawn (day time 13000–23000) woodcutters and fishermen do no work more than 16 blocks (Chebyshev) beyond the walls, where the guards cannot protect them. Turning it off gives them back about two fifths of their day (10000 of 24000 ticks) for more deaths at remote spots. |

## Mines (`MinePlanner`, `MineSiteRules`, `MineSites`, `MinerJob`, `SpiralMine`, `MineLayout`)
Each miner chooses its own mine site and digs its own shaft; there is no hut, no tier and no pickaxe payment (old hut
mines are migrated, see [Historical values](#historical-values-before-the-worker-redesign)).

Layers (PROPOSED, `MinePlanner`): a layer is put where vanilla 1.21.1 ore is richest for it.

| Layer | Height | Pickaxe needed | Wanted while the storehouse holds |
|---|---|---|---|
| Coal | 6 below the mine's surface | any (wood or gold) | fewer than 32 fuel (coal + charcoal, 9 per coal block), or the base is short of fuel or wood |
| Iron | y = 16 (vanilla) | stone or better | fewer than 8 iron (ingots + raw; most urgent of all), else fewer than 32 |
| Lapis | y = 0 (vanilla) | stone or better | fewer than 32 lapis, once there is an enchanting table |
| Gold | y = −16 (vanilla) | iron or better | fewer than 16 gold (raw + ingots) |
| Diamonds and redstone | 8 above the world's bottom (y = −56) | iron or better | fewer than 8 diamonds |

* Order: iron under 8, diamonds, iron under 32, coal, gold, lapis; then, with nothing short, iron, diamonds, gold,
  lapis, coal. The miner takes the first one its pickaxe can harvest.
* Depth (counted in spiral steps below the mine's surface): a mine needs its surface at least 6 steps above the
  floor (MIN_DEPTH; the floor is 8 above the world's bottom). A layer's step is clamped between 6 and the floor and
  put on the nearest landing (a step whose cell faces the tunnels). A level within 5 steps of the wanted height is
  used for it (REUSE_WITHIN); a new level lies at least 6 steps from every other (MIN_SPACING), and the shaft is dug
  down to it first.
* The level being worked is finished before the miner switches (a cut level counts as finished), so a changing
  stock never makes it go back and forth.
* Lengthening: a finished level is lengthened by a round (its main tunnel 40 blocks further, with its branches)
  while its rounds are below the mine's `maxRounds`, at most 4 (MAX_REACH; main tunnels then reach 200 blocks).
  `maxRounds` is what the mine's corridor has room for; when a new stretch does not fit, `maxRounds` drops to the
  rounds the level has.
* More layers (BAND_SPREAD = 12, PROPOSED): when a band's layer is done or cut and the corridor is short
  (`maxRounds` below 4), or the band's height lies below the shaft's limit, the miner works further layers of the
  same ore within 12 blocks of its height (nearer to it than to any other band's), at least 6 steps from every
  level, nearest and shallower first. A band's layers together hold no more tunnel than one layer lengthened 4 times
  (the sum of rounds + 1 at most 5). Vanilla iron, lapis, gold and diamonds are still plentiful 12 blocks out.
* Rest: when only a better pickaxe (stone, then iron) would give work, the mine is released and rests until a
  miner with that pickaxe takes it up; resting mines do not count toward `maxOpenMines`. A miner without a mine
  fetches such a pickaxe when the storehouse has one it would equip (a Silk Touch one scores lower and does not
  count; tried again at most every 1200 ticks), else it looks for another site. Worked out: every layer it can
  reach is dug; the miner moves on to a cave or a new site.
* Order of work for a miner: a held cave, else an open mine of its base, else a surveyed cave with marked ores, else
  a new site.
* Branch mine per level: main tunnel 40 blocks long and 2 high from the landing, side branches of 10 blocks every
  4 blocks on both sides, torches every 8 blocks.
* Shaft: 2x2 spiral staircase, one block down per step, wall torch every 8 steps. Before the first step of a new
  mine the miner sets a standing torch beside the mouth and plugs the ground ring around the open mouth (one below
  the surface) where it is air, fluid or a falling block; nothing at or above the surface is touched. It first goes
  to the storehouse when it carries fewer than 8 torches or 16 plugging blocks.
* Fluids: before a shaft or tunnel cell is opened, water, lava and loose sand beside it are plugged (shaft cells the
  descent has not dug yet are plugged as well; dug cells of the current level are emptied instead, so the miner's
  own way out is never walled shut); a cell that is pure fluid (water, lava, kelp, seagrass, a bubble column) is
  set to air and its floor bridged. This digs shafts on through aquifers. A lava aquifer can still be met near
  y = −54; raising the deep layer is a one-constant change (FLOOR_ABOVE_BOTTOM).
* Stairs: a spiral step whose floor is gone is repaired with a stone block from the miner's backpack.
* A plan cell not done after 1200 ticks near it (2400 while still more than 10 blocks away; vein mining excluded)
  is given up: a cut main tunnel ends the level (marked cut, never lengthened), a cut branch moves on to the next.
* Junk: once storage holds 512 cobblestone, cobbled deepslate, dirt, gravel or similar, the miner keeps at most 64
  of that item in its backpack and tosses the rest it digs (the miner keeps 64 sealing blocks and 32 torches).
* Ore XP: coal/copper 2, redstone 3, iron/lapis 4, gold 6, diamond 10, emerald 12; ordinary stone 0. Plus 20
  (LEVEL_XP) per layer reached or opened and per lengthening round.

Digging safety (`WorkSafety.mayDig`; miners and cave miners): never unbreakable blocks, block entities (chests,
spawners, vaults, beds, shriekers), built blocks (every brick family, polished and tiled deepslate and tuff, quartz,
purpur, sculk, copper grates and bulbs: dungeons, strongholds, trial chambers, ancient cities), any base's plan
blocks, the ground under a base (2 beyond its walls, from 48 below a settlement's floor or 16 below a lair's up),
other bases' land + 8 (lairs + 16), another mine's stairs, or the deep dark. A refused tunnel cell cuts the level or
branch; a refused shaft cell stops the descent above it.

Mine sites (PROPOSED, `MineSites`, `MineSiteRules`, `MinerJob`):
* Search: points every 4 blocks (GRID) on square rings from the walls + 8 (Chebyshev 32 for a settlement) out to
  `mineSiteRadius`, inner rings first, 64 points a tick (PER_TICK), loaded chunks only, 16 inside the map edge. A
  ring at 32 itself is still within walls + 8, so mouths lie 36–64 out.
* A point is fit when: its surface (heightmap without leaves) is deep enough (MIN_DEPTH); it is 8 beyond every
  base's walls, 16 out of other bases' land, 48 from villages the faction knows and 48 (`BaseZones.DANGER_RADIUS`)
  from known pillager outposts and woodland mansions; 24 from every other mine's mouth (worked-out ones too; 8 from
  a mine given up before anything was dug); 8 from the miner's own bad sites; 12 from a fishing spot with water
  (bobber or stand) and 6 from the woodland's planted saplings and waiting stumps; and a miner can stand there.
* Facings: the one pointing most directly away from the base first, then the two at right angles (the more outward
  first); never one pointing back at the base. Per facing: the corridor must fit with at least 0 rounds; the collar
  (forward 0..3, side −1..2) within ±1 block of the mouth's height, on solid ground (no leaves, logs, saplings,
  farmland, path, ice, mushroom blocks, barriers, block entities or plan blocks) with the shaft cells clear; no
  surface water or lava (every second column) and no built block or block entity (every column) in forward −3..6,
  side −4..5; a corridor penalty below 30 (MAX_PENALTY): sampled every 6 blocks over forward 3..42, side −10..11,
  +6 where water lies over the tunnels and +3 where the ground dips more than 3 below the mouth.
* Score, lower is better (`MineSiteRules.score`): the walk from the nearer gate, + 2 × the penalty, + 4 × collar
  unevenness, + 10 for sand or gravel at the mouth, + 15 (ROUND_COST) for each lengthening round of the 4 that the
  corridor has no room for. A layer with no room to lengthen gives 463 cells and is worked out quickly, so a site with room is
  worth a walk up to 15 blocks longer per round. The best facing by full score wins, ties to more rounds. The site
  is evaluated again (and the open-mine cap checked again) in the tick the mine is opened, so two miners searching
  at once never open the same mouth.
* Corridor: a mine reserves the rectangle its tunnels can take: forward −2 .. 2 + 40 × (rounds + 1) (202 at 4
  rounds), sideways −11 .. 12. It keeps 2 from other mines' corridors, 4 from any settlement's walls (its own
  included), 8 from other bases' land, 16 from lairs' walls, and 8 inside the map edge. An open or resting mine
  keeps its whole corridor; a mine set aside for good keeps only what it dug: nothing when its shaft was never begun,
  the ground around its stairs (forward −2..5, side −3..4) when no tunnel was started, else its tunnels as far as
  they were lengthened.
* No fit point near: 4 survey walks (SURVEY_POINTS) to the base centre ± 44 blocks on the diagonals (16 inside the
  map), 2400 ticks each, searching from there; then the miner is idle ("no place to dig a mine", or "no ground deep
  enough for a mine" when 8 or more points all failed only for depth) and looks again every 6000 ticks
  (SITE_RETRY_TICKS), waiting within the walls + 16. At the cap: idle ("no free mine site").
* Bad sites: a mine given up as unreachable, unsafe or with blocked stairs puts its mouth on the miner's list of
  32 (oldest forgotten first); points within 8 of one are skipped.

Shaft and mine watchdogs (PROPOSED, `MinerJob`):

| Value | Default | Effect |
|---|---|---|
| `STUCK_STEP_TICKS` | 2400 | Ticks at one shaft cell, within reach of it (plugging around it and breaking it), before the descent stops there. |
| `DESCENT_WALK_TICKS` | 1200 | Ticks walking toward the shaft cell without getting 0.5 closer before the descent stops ("the bottom of the shaft could not be reached"); a trip away (an errand, a fight) starts it afresh. |
| `SEAL_FAILURES` | 3 | Failed plugs in a row, with nothing to plug with at home either, before the descent stops. |
| `TIER_CHECK_TICKS` | 2400 | How often the miner checks that its pickaxe still harvests its level's ore (else a storehouse trip). |
| `STAIRS_BLOCKED_TICKS` | 600 | Ticks the stairs may stay blocked by a block nobody may dig before the holder gives the mine up. |
| `LEAVE_BUDGET_TICKS` | 1200 | Ticks climbing out of a mine without getting closer to the way out (distance to the mouth + 2 × depth) before plain walking and digging out take over. |
| `UNREACHABLE_SITE_TICKS` | 2400 | Walking ticks toward a new mine's mouth, before its first step is dug, before the site is given up. |
| `FACE_GIVE_UP_TICKS` | 1200 | Ticks at one plan cell before it is given up (see above). |

* A stopped descent (also: a shaft cell nobody may dig) sets the shaft's limit one step above that cell, drops the
  level it was heading for and plans again. A mine whose shaft stops within 6 steps of the surface is given up ("the
  ground below is unsafe"); so is one whose mouth cannot be reached or whose stairs stay blocked. A given-up mine is
  announced as given up, not worked out.
* A miner leaving a mine for an errand (food, a tool) climbs out by its own stairs first.

## Woodland (config `workplaces`; `TreeShape`, `ForestRules`, `ForestScan`, `WoodcutterJob`)
There is no tree plot, fence or upgrade (see [Historical values](#historical-values-before-the-worker-redesign)):
woodcutters fell the natural trees around the base, replant them, and keep a grove.
* What is felled (PROPOSED, `TreeShape`): from the stump, logs of the same block are followed up and sideways (at
  most ±7 sideways, +24 up and 96 logs; wood running past those bounds is TOO_BIG and left whole, never cut in
  part). A tree is felled only with 3 or more logs (MIN_LOGS), no log more than 8 above the stump (MAX_FELL_DY;
  with the 7.5 reach, every log can then be cut from the ground), natural (not persistent) leaves within 2 of its
  top, the stump on dirt, and no built block (planks, cobblestone, fences, glass, wool, torches, signs, rails, any
  structural block), block entity (a bee nest) or log of another species touching it. Species: oak, birch, spruce,
  jungle, acacia, dark oak and cherry; not mangrove, nether stems, stripped logs or other mods' logs. Mega spruces,
  tall jungle trees and fancy oaks with a 12–14 trunk (about 2.5% of oak saplings) are left standing: felling them
  would need pillaring along the crown, which was left out on purpose.
* Protected wood (`WorkSafety.protectedWood`): any base's plan, 2 around a settlement's walls (its own included),
  other bases' land + 8 (lairs + 16), outside the map, within 32 of a village or 48 of a pillager outpost or
  woodland mansion the faction knows.
* Search (`ForestScan`): every second column (one of 4 rotating patterns per scan) in a circle of `forestRadius`
  around the base centre, loaded chunks only; a column counts when its top block (leaves aside) is a known log whose
  trunk of 3–9 logs of the same block stands on dirt, with natural leaves near the top; a 2x2 trunk is found at
  its north-west corner. Best: distance from the centre + 0.5 × distance from the woodcutter. Scans run at most
  every 600 ticks (RESCAN_TICKS); after an empty one the next pattern follows 20 ticks later, and only a whole empty
  round of 4 sends the woodcutter foraging. A tree another woodcutter claimed is skipped (claims last 1200 ticks).
* Felling (PROPOSED): the walk ends within 4.0 of the stump (2.5 approach); logs are cut top-down from where it
  stands within 7.5 (TRUNK_REACH); at the first log out of reach it steps within 1.5 of the stump (200 ticks at
  most); logs still out of reach are cut at the end standing in the stump's column (0.3, 200 ticks per log) or, a
  branch end, standing within 1.0 of the ground under it (UNDER_BRANCH). Leaves: natural ones within ±3 (±5 for a
  2x2) up to +12, at 5.5 reach. Drops are collected 6 (7 for a 2x2) around the stump, 2 down and 10 up. +1 XP per
  log. 48 logs, sticks and apples carried (CARGO_LIMIT) send it to the storehouse.
* Walk budgets (`ForestRules.walkBudget`): a tree 400 ticks + 8 per block of the way, a planting or bone-meal spot
  600 + 8 per block, or 3 new route failures; a tree not reached is skipped for 12000 ticks (TREE_RETRY_TICKS), a
  spot for 2400 (SPOT_RETRY_TICKS). Only what a tree is (its survey) leaves it standing for good: 128 such trees are
  remembered (oldest first out), and one of them is surveyed again every 200 ticks (RECHECK_TICKS) in case it
  changed.
* Replanting (`ForestRules.replant`): the same species at the stump, 1 sapling (big spruces and jungle trees too);
  a dark oak gets 4 as a 2x2, only when 4 are carried. A stump waiting 12000 ticks (REPLANT_PATIENCE) for its own
  sapling gets a substitute when one is spare (birch, spruce, oak, cherry, acacia, then jungle; never dark oak);
  after 48000 (REPLANT_GIVE_UP) it is forgotten. At most one storehouse trip for a stump's sapling every 2400 ticks;
  the storehouse is looked into for it at most every 200.
* Grove (PROPOSED): spots on a 5-block lattice around the centre (GROVE_SPACING, so grown trees do not touch), at
  least 4 beyond the walls, off the road strip (|dz| at most 3 for |dx| up to the walls + 30), inside the base's
  always-ticking belt (x from 58 west to 78 east of the centre, z within the walls + 1) and within `forestRadius`;
  grass or dirt (not farmland or path) under open sky, no log, leaves or sapling within 2 sideways and 4 up, not in
  a plan, and 6 from a mine mouth or a fishing spot with water. Target `groveSize` (16), twice that up to 32 while
  the base is short of wood. Species, best first: birch (5–7 logs), spruce, oak, cherry, acacia; never jungle or dark
  oak. Up to 8 grove saplings are taken from the storehouse at once (GROVE_TAKE), on a trip of its own when the
  grove is short. 200 lattice points are looked at per call; a fruitless round rests 600 ticks. Why 16: in the
  ticking belt a sapling gets 3 random ticks per 4096 blocks per tick and grows a stage on 1 in 7 of them while lit
  (about half the day), so two stages take about 36,000 ticks; 16 saplings give about 10 trees (about 50 logs) a day,
  what a base's builder, smiths and furnaces use; 8 gave half that.
* Bees: before an oak, birch or cherry sapling is planted, every `#flowers` block in the 5x3x5 box vanilla's tree
  grower checks (plan blocks and persistent leaves aside) is picked, so no tree grows with a bee nest.
* Bone meal: used round-robin on planted saplings, those outside the belt first. Unloading keeps 8 saplings of each
  species and 16 bone meal (topped up to 16 below 8). The blacksmith turns bones into bone meal (1 → 3) while fewer
  than 16 are stored. Records: 64 planted saplings and 32 waiting stumps (oldest dropped).
* Forage: 72 blocks out from the centre in one of 8 directions (16 inside the map; a direction near a known outpost
  or mansion is skipped), at most 2400 ticks a leg, then a look 40 around; the direction moves on only after a miss;
  after 8 misses it rests 6000 ticks. Never at night or from a GameTest base.
* Night: a woodcutter does no work beyond the walls + 16 between day time 13000 and 23000 (config
  `remoteWorkInDaylightOnly`); a tree started there waits for daylight. Forage legs, and night waits with no tree
  started, do not reset its idle time (waiting to finish a started tree counts as busy).
* Idle and retraining: "no trees or saplings" when foraging rests (or is not allowed), nothing is planted or waiting
  to be replanted and no sapling is carried or stored; a second woodcutter of a base whose woodland is held: "no
  woodland of its own".
* Old fences: the tier 1–3 rings of an old plot that still hold oak fence (not in a plan) are broken into the
  backpack, nearest post first; a post not reached in 300 walking ticks or 3 route failures is left.
* Starter saplings: four oaks at (−35, ±5) and (−40, ±5) from the centre, on the grove lattice of the west pad; two
  are grown at once.
* Leaves drop by their own loot table (vanilla oak: sapling 1/20, apple 1/200, sticks).

## Caves (`CaveSurvey`, `CaveExplorerJob`, `CaveMinerJob`, `CaveWork`)
* Scouts report a cave only where they can see a way in (a mouth, pit or ravine opening); caves within 128 blocks
  of a base are surveyed, except in the base itself (its belt + 16) and in its own mines' workings (each mine's
  corridor + 4, from 2 above the mouth to 6 below the shaft): those are its cellars and tunnels.
* Explorer: sees ore up to 10 blocks from its eyes with a clear line of sight to an exposed face; survey spots at
  least 7 blocks apart, each 6–24 walking steps from the last; at most 48 spots, 64 blocks from the entrance,
  half a day (12000 ticks) and 32 placed step blocks per trip; turns back when out of torches, wounded (below 60%
  health) or with 2+ monsters close. It takes 32 torches (crafted in hand from coal and sticks if needed) and 32
  cobblestone/dirt for steps. A worked-out cave is surveyed again after two days.
* Cave mining: at most 2 cave miners per base; a marked ore is reached from a standing spot within 4.5 blocks
  or by tunnelling at most 12 blocks toward it; then 4 prospecting tunnels, 16 blocks long and 2 high, from the
  cave wall nearest the deepest explored spot, torches every 8 blocks.

## Experience and enchanting (`EnchanterJob`, `XpLevels`)
* Experience is real orbs, picked up by any faction NPC within 4 blocks into the faction's experience: ores mined
  with a tool that harvests them (vanilla amounts, e.g. diamond 3–7, coal 0–2), furnace and smoker output taken
  (vanilla recipe experience), animals bred (1–7).
* Hiring (PROPOSED): when the enchanting table stands, the faction has at least level 10 worth of experience
  (160 points), 6+ lapis and at least one unenchanted good item in storage.
* The enchanter takes all of the faction's experience as its own points (vanilla level curve) and works only from
  level 5 (PROPOSED), spending 60 ticks at the table per item. Each enchantment is the vanilla table's: power from
  the bookshelves (10 in the starting workshop), three random offers, the best affordable one taken, paid with
  1–3 levels and 1–3 lapis.
* Gear worth enchanting: iron, diamond and netherite weapons, tools and armour, chainmail, bows and crossbows
  (soldiers' gear first, then pickaxes).
* Gear choice everywhere: material first, then the total enchantment level; a Silk Touch tool scores below a plain
  one of its material.

## Hunger (config `survival`, `NpcHunger`)
* The vanilla food bar: 20 food, saturation up to the food level, 4 exhaustion = 1 saturation (or food) point.
  Regeneration: every 10 ticks while saturated at 20 food (heals saturation/6, costs as much exhaustion), else
  every 80 ticks at 18+ (1 health, 6 exhaustion). At 0 food: 1 damage every 80 ticks while health > 10 (Easy),
  > 1 (Normal), always (Hard). Peaceful refills food and health. Natural regeneration gamerule respected.
* Exhaustion: vanilla action costs (block broken 0.005, attack 0.1, damage taken by source, jump 0.05, swimming
  0.01/m) plus `workExhaustionPerDay` (default 30, spread over the day).
* Meals (PROPOSED): 2 carried (3 for soldiers, scouts, explorers), the most filling ones kept when unloading; raw
  meat, fish and potatoes stay for the chef; golden apples are for fights. Eating below 18 food when the meal fits
  (or below 13 anyway), harmful food only at 6 or less. At 6 with no meal: a trip home, retried every 30 s; with
  nothing at home, work goes on (checked again after 2 minutes).
* Hurt (PROPOSED): at 75% health or less below 18 food a meal is eaten even if part of it is wasted; at 50% or less
  two extra meals are packed; at 30% or less below 18 food with no meal, a trip home for food.

## Tool wear and repairs (config `survival.toolWear`)
* Vanilla wear (Unbreaking applies): see the README. A block only wears the held tool when that tool mines it
  faster than a hand (an axe on leaves, a pickaxe on dirt or a sword on stone wear nothing). Gear under 25% durability counts below a fresh one of its
  material, so it is swapped at the storehouse (and returned there); miners carry one spare pickaxe.
* Errand for a tool when the work cannot be done by hand (miners, fishermen, soldiers, scouts, explorers,
  butchers): retried every 1200 ticks; after 12000 ticks without one, a civilian is retrained. The time without a
  tool is cleared whenever the role's tool is in hand, however it got there.
* Blacksmith replacements, one per tool kind per plan: iron if there is enough, else stone, else wooden. A miner
  with a wooden pickaxe gets a stone one as soon as there are 3 cobblestone; then an iron one (3 ingots), which opens
  the gold and diamond layers, and a diamond one (6 diamonds stored), which only digs faster. A Silk Touch pickaxe in
  storage does not count as one (miners rate it below a plain one). The blacksmith's crew is every living member of
  its base on the roster (soldiers too) within 256 blocks (horizontally) of the centre, wherever it works (CREW_RANGE).
* Anvil (vanilla): each material unit repairs a quarter; cost = units + prior work; prior work becomes
  2 × prior + 1; "Too Expensive" from 40 levels. Repaired only when worth it (PROPOSED): enchanted, or iron,
  diamond, netherite or chainmail gear needing fewer units than its recipe (pickaxe/axe 3, sword/hoe 2, shovel 1,
  helmet 5, chestplate 8, leggings 7, boots 4). Levels paid from the faction's experience (the enchanter keeps at
  most level 30 of it). 12% chance per use to damage the anvil; a broken anvil is re-forged from 31 iron.

## Wood and fuel
* Wood reserve (PROPOSED, `CookingJob.LOG_RESERVE`): a flat 16 logs (and 16 planks) kept out of the cooks' fires
  for building and crafting. With no plot to enlarge there are no fences to save up for.
* Smelters make charcoal from logs when the storehouse has fewer than 8 coal/charcoal and more logs than 24
  (`SmelterJob.CHARCOAL_LOG_RESERVE`, at least the wood reserve).
* A WOOD shortage (Logistics) doubles the woodland's grove (up to 32 saplings), and a FUEL or WOOD shortage makes
  miners want coal.

## Cooking stations (`CookingJob`)
* One station with items waiting, cold and without fuel is enough to send the worker for fuel.
* Fuel order: coal or charcoal (6), a coal block, then logs (keeping the wood reserve; 2 at a time, crafted into
  8 planks first: 6 smelts per log instead of 1.5), then planks (keeping 16).
* With nothing to burn and no station busy, the worker waits at the storehouse (600 ticks) with a clear status;
  items a station cannot cook are taken out of it.

## Achievements (`FactionAchievements`)
* Criteria and requirements come from the vanilla advancement data; a faction meets a criterion through its NPCs
  (items reaching its backpacks or storehouse, meals, crops planted, animals bred in its pen, fish caught, kills,
  arrow hits, crossbow shots, enchanting, shield blocks, biomes reached - checked every 200 ticks) or through a
  member player's own advancement (all criteria at once). Announced only if vanilla announces it.

## Logistics (`Logistics`, `Ledger`)
* One sample a minute (1200 ticks) of food (hunger points), wood (logs + planks/4), stone, iron (ingots + raw),
  fuel (coal + charcoal + 9 per coal block) and diamonds; 32 samples kept; trend over the last day (at least a
  tenth of a day of samples needed).
* Food need (PROPOSED): 8 hunger points per NPC per day. Rationing starts under 1 day of meals in store or under 1.5
  days left at the current rate, and ends at 1.5 days in store and not falling fast. Recruitment keeps 1 day's meals
  in reserve. Wood alert under 8 logs (not rising), cleared at 16; fuel alert with no coal/charcoal and under 24 logs.
* Hauler fuel rounds: any workshop furnace or smoker with fewer than 4 coal/charcoal is topped up to 8.

## Combat behaviour
* Critical: a melee NPC jumps when its swing is 1-7 ticks from ready (60% of the time, not in water) and hits
  while falling for x1.5 (vanilla crit rule: falling, not on the ground, a ladder or in water).
* Sweep (swords, on the ground, not a critical): enemies within 1 block of the target and 3 of the NPC take
  1 + Sweeping Edge ratio x attack damage and 0.4 knockback.
* Shield (swordsmen): raised against a ranged attacker more than 3.5 blocks away, and within 8 ticks after its own
  swing at close range; lowered to strike. Wear: 1 + damage for blocked hits of 3+. An axe hit disables it for
  100 ticks. Blacksmith: 6 planks + 1 iron per shield, up to 2 per plan.
* Archers: back away (10 blocks) from a melee enemy within 6; every 2 s switch to a visible enemy in range with
  at least 2 health less than the current target.
* Withdrawal: under 30% health with no golden apple, fall back toward the storehouse (or 12 blocks away from the
  attacker near home) until over 60% or for at most 1200 ticks.
* War council (PROPOSED): target = the reported, still-standing base with the fewest soldiers at home (lair: its
  monsters), then the nearest. Party = every soldier beyond max(`homeDefenders`, a third of the garrison), at
  least `raidPartySize`. Strength is power, not heads: a soldier counts 1 + armour/20 (at most 1) + weapon material
  tier/4 (wood 0 … diamond 0.75, netherite 1); a faction's average power is noted whenever its garrison is home, and
  rivals judge its defenders by it (a lair's monsters count 1 each). A raid goes when the interval has passed and
  the party's power is at least 2/3 of the defence; a counter-attack may go after a quarter of the interval at 3/2.
* Pacts (config `war.pacts`, on): after a conquest of a human faction by a human faction, the other surviving human
  factions (2+) are allied until the conqueror falls or fewer than 2 members remain. Members learn the
  conqueror's bases, target only it, and join a member's raid on the same base when their combined power is at
  least 2/3 of the defence (after a quarter of the interval).
* Traders (PROPOSED): hired with 14+ people and a reported village with traders within 320 blocks; one trip per 12000 ticks, by
  day (setting out between 23500 and 9000 day time), abandoned after 24000 ticks. Packs up to 8 kinds × 64 of
  surplus above the reserves (wheat, potatoes, carrots 64; beetroot 32; sticks 64; string, leather 16; feathers,
  flint, cod, salmon 32; stone 64; granite, andesite, diorite 128; coal 96; white wool 8; pumpkins, melons, rotten
  flesh, paper, clay, rabbit hide 0; no food while rationing) and up to 192 emeralds. Buys only gear of iron or
  better (or a bow/crossbow) when the storehouse has none of that kind at that rank or better, at most 2 per kind
  per trip; bread only while rationing. One trade per second.
* Raids: rally point 28 blocks short of the target toward home; the assault starts when 70% of the living raiders
  are within 12 blocks of it or after 6000 ticks; a raid that has lost more than half its party is called off.

## Hauler (`HaulerJob`)
* Recruited once a base has 12 NPCs. Picks up items that have lain inside the walls for 10 seconds (not in the
  farm or pen, whose workers collect their own), and moves stacks out of the overflow chest or a wrong chest into
  their category's chests when those have room.

## Terrain and sites
* Pads levelled to the base's height: the walls' square, the grove pad outside the west gate (the old tier-1
  plot's size: x from 26 to 45 west of the centre, |dz| at most 7) and the east landing (EAST_APRON = 10: x from
  26 to 34 east of the centre, |dz| at most 4, with a path across it from the gate). Around them the ground is
  blended over 5 blocks (at most one block of height per block of distance), so the pads' edges are slopes, not
  cliffs or trenches. The landing replaces the old mine pad, which had levelled that ground: without it a gate
  could open onto a drop or a lake (one base's east gate opened onto a one-block ledge four blocks above a lake,
  and four NPCs were stuck there).
* Site choice samples the settlement's footprint: x from 58 west to 36 east of the centre (the grove pad, the walls
  and the landing), z ±26. Mines are not part of it (no mine-area penalty any more): miners choose their own sites.
* The work belt (the base's MIN/MAX box, kept loaded and randomly ticking): x from 58 west to 78 east of the centre
  (`BaseLayout.WORK_AREA_WEST`/`WORK_AREA_EAST`), z within the walls + 1. The grove grows there, and mines in it keep
  working without players; it now lies over unlevelled ground and holds whatever trees and water happen to be there.
* Night window for remote work (`Job`): day time 13000–23000; "remote" is more than 16 (Chebyshev) beyond the walls.

## Animals and fishing (`AnimalCarerJob`, `ButcherJob`, `FishermanJob`)
* Breeding: one food item per adult (vanilla `isFood`), only adults that are ready (age 0); vanilla handles the
  baby and the 5-minute cooldown. At most 8 animals per species per pen.
* Feed kept in storage for other uses: 24 wheat seeds, 4 wheat, 4 carrots/potatoes/beetroot.
* Milking: a bucket per cow at most every 6000 ticks. Shearing: vanilla `readyForShearing`.
* Butcher: keeps 4 breeding adults per species, never kills babies or animals in love; melee hit every 12 ticks.
* Fishing water (PROPOSED, `FishingWater`): the bobber cell is a water source with nothing to bump into (kelp and
  seagrass count; waterlogged slabs, stairs and the like never) with air or a lily pad above and a source below (2 or
  more deep), under open sky, in a body of at least 25 surface sources (MIN_BODY; counted up to 400), with no
  farmland within 5 sideways and 1 up or down (FARMLAND_CLEARANCE: the farm's channel and any irrigation never
  qualify). The stand is the nearest spot 2–5 blocks (Chebyshev) from the bobber and 1–2 above it, on sturdy ground
  that is not farmland, leaves, ice or fluid, with 2 clear blocks and a line of sight to the bobber.
* Zones (`FishingSpots`), for the bobber and the stand: within `fishingRadius` of the centre (+40 for water found
  while foraging), beyond every base's walls + 4 (28 from a settlement centre), 8 out of other bases' land, 24 from
  a lair, 48 from known pillager outposts and woodland mansions, inside the map; the stand also 8 from a mine mouth
  and 1 from a planted sapling.
* Search: every second column (a rotating pattern per base) within the radius, loaded chunks only; a column counts
  when its top is a water source at least 2 above the floor with a bank (a column that is not water) 2 or 4 blocks
  away. One candidate per 8x8 cell (the one nearest the centre); the 12 nearest are checked in full. Best: open
  water, then the shorter walk from the nearer gate to the stand, then the bigger body; a still-water pick is moved
  1 or 2 blocks further out when that is open water and passes every rule again. Water within 2 of a spot found
  unfit is skipped; 32 unfit spots are remembered (oldest first out).
* Cadence: a fisherman searches at most every 600 ticks; for a base whose spot has no known water the director also
  searches, at most every 6000 ticks. Every cast checks the water at the bobber, the stand and the farmland rule;
  the reel-in checks the water again; every 20th cast checks every rule again (open water included). Any failure
  marks the spot unfit and the search starts again.
* Getting there: the walk to the stand is given up (the spot marked unfit) after 1200 walking ticks or 3 new route
  failures. Arrived means within 0.7 sideways and 0.6 up or down of the stand, on the ground and out of the water:
  a fisherman swimming up to the bank climbs out first.
* Foraging: with no water within reach it walks 72 blocks out from the centre in one of 8 directions (16 inside
  the map), at most 2400 ticks a leg, and searches 40 around there (reach `fishingRadius` + 40). After 8 misses the
  base's spot is NONE: for 48000 ticks (NONE_MEMORY, two days) no fisherman is hired there (the director still
  looks), and the fisherman rests 6000 ticks at the storehouse, idle ("no fishing water"), then searches again; it is
  retrained after half a day idle. Many bases have no such water within 64 blocks: SiteFinder prefers dry land.
* Night: a spot more than 16 beyond the walls is not fished between day time 13000 and 23000
  (`remoteWorkInDaylightOnly`): the fisherman unloads and waits at the storehouse; no foraging at night.
* Catching: 100–600 ticks + 20–80 ticks per catch (Lure −5 s per level), and while it rains on the water one tick
  more off the wait with a 1 in 4 chance each tick (vanilla); the vanilla fishing loot table with Luck of the Sea.
  In open water (vanilla's 5x5, 2-deep, open-sky test) with `fishingTreasure` on, the loot is rolled with a real,
  never-spawned fishing hook, so the treasure catches can come up as for a player (about 5% of catches: enchanted
  books, bows and rods, name tags, saddles, nautilus shells); otherwise only fish and junk. The catch flies to the
  fisherman's feet; items within 3 of the stand that are not in water are picked up (an item not reached in 200
  ticks is left); the fisherman unloads at 32 items.

## Recruitment (config `recruitment`)
* `foodValue` 20 hunger points per recruit, paid with stored food at each item's normal nutrition (bread 5, cooked beef 8, raw beef 3, …). The planner finds the smallest overpayment and spends cheaper food first. Golden apples and harmful foods are excluded.
* Equipment: the role's tool/weapon — the finished item if stored (soldiers get the best, civilians the cheapest adequate), otherwise its vanilla crafting materials (e.g. stone pickaxe = 3 cobblestone + 2 sticks; sticks may be paid as planks or logs). Archers also take 1 arrow (or flint + stick + feather; the 3 spare arrows return to storage).
* `intervalTicks` 1200 between recruits per base; `rangedShare` 0.4 archers among soldiers; garrison cap 12; population cap = beds/bedrolls.
* Retraining (PROPOSED, `FactionDirector`): a civilian worker whose job has been idle for 12000 ticks
  (RETRAIN_IDLE_TICKS; the job says why: "no place to dig a mine", "no ground deep enough for a mine", "no free mine
  site", "no trees or saplings", "no woodland of its own", "no fishing water") or without its tool for 12000 ticks
  takes up the base's most needed role instead of a recruit, keeping its level and affixes, and hands back every
  workplace it held. When it was idle (not toolless) and no other worker of its old role at the base still has work,
  that role is barren for 48000 ticks (BARREN_TICKS, two days): "replacing the missing ..." is not raised for it and
  no fisherman is hired. A barren base therefore tries one new worker in that role about every two and a half days.
* Miners: "an open mine has no miner" and "a surveyed cave with ore to mine" are raised only while no miner of the
  base is free (holding neither an open mine nor a cave); at most 2 cave miners per base.
* Fishermen: hired only where the fishing spot has water, has not been searched yet, or was found to have none
  48000+ ticks ago (and not while the role is barren), with stored food under 15 recruits' worth and a rod or 2
  string in storage.

## Levels and affixes (`AffixRoller`, `Affix`)
* Level n → n+1 costs 40 + 30n XP; max level 30; one affix roll per level; job affixes weighted ×2.
* Caps: Vitality +2 HP ×5, Swiftness +3% ×5, Might +1 ×5, Toughness +1 ×5, Diligence +6% dig/build speed ×5, Prospector +5% chance an ore drops twice ×5, Regrowth +8% instant growth ×5, Green Thumb +10% chance a crop drops twice ×5, Pathfinder +16 range ×4, Mason +10% build speed ×5, Marksman +10% arrow damage ×5, Thrifty 8% chance a wear event is skipped ×4, Hardy −8% exhaustion ×4, Duelist +10% critical damage (×1.5 → ×2.0 at 5) ×5 (swordsmen, axe fighters), Angler 8% chance the fishing loot is rolled twice ×5 (fishermen), Haggler −5% emerald prices ×4 (traders).
* XP: ore XP above (stone 0); 1 per log/harvest/breeding feed; 2 per butchered animal; 20 per mine layer reached or opened and per lengthening round; 1 per repaired block; 25 + blocks/20 per project; scouts: report base value (enemy base 12, lair 12, woodland mansion 12, pillager outpost 10, cave 8, village 8, ore 6, hostiles 4) + 1 per 32 blocks from home. Trees and water are not reported.

## Soldiers (`NpcMeleeAttackGoal`, `NpcRangedAttackGoal`)
* Melee: one hit every 20 / attack speed ticks (the player's attack speed, set by the held weapon), clamped to
  10–40 ticks: swords 12–13, axes 20–25, bare hands 10.
* Bows: full draw (20 ticks), shot speed 1.6, range 16, 20 ticks between shots. Crossbows: real charge time
  (25 ticks, shortened by Quick Charge), bolt speed 2.2 with less spread (more damage), range 20, 20 ticks
  between shots. Both use the one equipped arrow that is never used up.
* Garrison mix: 40% ranged; every third ranged recruit is a crossbowman when a crossbow is stored or 2 iron
  ingots + 2 string are; every third melee recruit (beyond the 3 home swordsmen) is an axe fighter. A crossbow
  paid from materials costs 4 sticks, 2 string, 2 iron ingots and 1 plank (crossbow + tripwire hook).

## Golden apples
* Soldiers take one golden apple from storage when one is stored. Any NPC carrying one eats it when its health
  drops below 40% while it has a target: 32 ticks of eating (as a player), then the apple's vanilla effects
  (Regeneration II 5 s, Absorption 2 min); 10 s before another. The blacksmith crafts one (8 gold ingots + 1
  apple) while fewer than one per two soldiers are stored.

## War (config `war`)
* Scouts: range 160 (+Pathfinder), at least 96 blocks out, 4 sectors per expedition, overdue after 24000 ticks.
  When every sector in range is explored and no living rival settlement is known, the faction's range grows by 64
  (saved, up to the far edge of the map); otherwise the explored sectors are forgotten and scouted again.
* Scouts and structures (PROPOSED): a village (village tag), pillager outpost or woodland mansion is reported once
  per trip when its built bounds (its pieces) come within 48 blocks (loaded chunks within 3 of the scout only);
  villages at their town centre, others at the middle of their bounds. Reports of one structure merge within 64
  blocks; a look that saw no traders keeps the traders seen before. Over the 160-report cap the oldest report that is
  not a structure is forgotten first. No exploration target, mine site, tree, fishing spot, forage leg or travel
  waypoint within 48 blocks (Chebyshev) of a known outpost or mansion (a travel detour stays on the campaign map).
* Scouts and monsters (PROPOSED): checked every 10 ticks; a monster counts within 6 blocks, or within its keep-away
  distance (12; creepers 16; skeletons, pillagers, witches and drowned holding a trident 18) when seen or hunting the
  scout (+4); a monster swimming (a drowned in a river beside the path) counts only within 6 or when hunting it. The scout runs at
  1.3x to a point 16/20/12 blocks out in one of 16 directions, clear of every monster (2 beyond its keep-away
  distance), passing none closer than 4, scored by clearance (up to 4 over the keep-away) + 8 x the target side + 1 x
  away from the group - 6 x reversing the last escape; firm dry ground in a loaded chunk, 8 inside the map edge, with
  a walking route (4 path searches at most). A monster within 6 counts only on about the scout's level (3 up or
  down) or in sight. A run lasts at most 160 ticks. It retreats home when under 40% health with monsters about,
  after 3 monster hits in one encounter, after 4 escapes in a row that did not shake them, after 10 escapes in one
  encounter, or after 40 escapes on one expedition (a typical trip needs well under 20); cornered with a monster within 3.5 blocks it fights back for 100 ticks while over half health. An
  encounter ends after 100 quiet ticks. Enemy soldiers: retreat home as before (2 threats, or 1 under 60% health).
* Raids: interval 36000 ticks, party at least 3 (see the war council above), 3 home defenders kept; assault takes 400 ticks at the target heart.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; with `survival.hunger` off, NPCs regenerate 1 HP per 4 s once 10 s have passed since they were last hurt; with it on (the default) they heal only through the food bar (see Hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.

## Historical values (before the worker redesign)
These numbers belonged to the old fixed workplaces. New bases no longer have them; they matter only for campaigns
saved by an earlier version, which load and convert (`CampaignData.FORMAT` 2; each record converts itself in
`WorkplaceState.load`, keyed on its missing `mineV`, `woodV` or `fishV`), and for the fixed-seed results in the
README, which came from this layout.
* **Hut mine** (one per settlement, its origin 32 east of the centre, tunnels running east; `LegacyMine`): a roofed
  hut over the spiral. Levels by tier: tier 1 about 6 below the hut floor (built with the base), tier 2 at y = 32
  (paid with a stone or better pickaxe), tier 3 at y = 0 and tier 4 at y = −48 (each paid with a pickaxe, with an
  iron pickaxe in hand), then further levels 6 deeper down to y = −56; the miner dug down at 60% of a level with the
  payment in storage (or at 100% without). Then "infill" levels in the middle of every gap of 12+ blocks between
  levels, and up to 4 lengthening rounds of the regular levels, deepest first. On load the hut, its torches and its
  path are kept (and repaired); every old level becomes a layer (its ore taken from the nearest layer height), with
  the rounds it was lengthened; only the level being worked keeps its progress, the others are fast-forwarded through
  the cells already dug; a descent under way continues. The mine is then worked like any other (layers by need and
  pickaxe, no payments) until it is worked out; the base's other miners open sites of their own, as at a new base.
* **Tree plot**: tier 1/2/3 with 2×2 / 3×3 / 4×4 trees spaced 6, a fence growing away from the base, enlarged with
  a wood surplus (≥ net fences + 96 plank-equivalents), ≥ 64 logs produced and no project waiting; wild trees cut
  within 64 blocks of the plot's gate while its saplings grew. On load the plot becomes a woodland: its grid spots
  are its planted saplings, its woodcutter takes the old fence down into storage first, and its old trees are felled
  like any natural tree.
* **Wood reserve**: 64 logs while a tree plot was below tier 3 (16 when rationing), so a coal-less base could save
  up for its fences.
* **Fishing**: fishermen stood at the farm's water channel. On load the fishing spot is marked "not searched", moved
  off the farm to the base centre as a placeholder, and real water is looked for at once; the farm channel is never
  touched.
* **Site choice** scored the ground over the hut mine's first level (50 blocks east of the base): +6 per sample with
  water over the tunnels, +3 where the ground dipped toward the tunnel depth. Old bases keep their levelled mine pad
  east of the walls; new bases level the smaller east landing there instead.
* **Blacksmith**: paid the mine's tier upgrades with pickaxes (order 1, removed).
