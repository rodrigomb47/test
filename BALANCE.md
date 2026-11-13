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
* After two failed trips an NPC digs. Shut in (under cover, up a tree on 3+ blocks of wood or leaves, on top of a bare
  pillar (no side to step off within 3 down and a longer drop on two sides or more, as on a woodcutter's scaffold its
  job no longer looks after), or more than 1 block below its destination: a pit, a ravine floor, the foot of a cliff) it keeps digging (or building) its way,
  one step per 20 ticks and at most 64 steps per round: up if the destination is above, down if below, otherwise
  level. Only a walking route ends the digging (looked for after every step in the open, every 4th under cover,
  never before a round's first step): being out in the open is not enough, a pit a few blocks below the
  destination is still a dead end. A round that
  ends without a route (64 steps spent, or no step possible) makes the next one head 16 blocks off to one side,
  then the other side, then straight away, then toward the destination again (PROPOSED), always to the
  destination's height; arriving anywhere resets this. Under a settlement's edge it first heads out from under the
  walls. Never dug: faction structures, a settlement's walls and floor band, any mine's spiral stairs, containers,
  unbreakable blocks and anything touching a fluid. Up a tree or a bare pillar it breaks the leaves, log or block
  underfoot (it goes into its backpack), a block every 20 ticks, until a drop of 3 or less is left; on a pillar it is
  building itself up toward a destination above, it goes on building.
* Recovery teleports never land on leaves, on top of a tall trunk or on a bare pillar.
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
| `maxOpenMines` | 3 (1–8) | Mines a base works at once (unfinished and not resting); each has a miner of its own, and while every site is taken a spare miner helps in one (see "Sharing a mine"). |
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
  a new site, else (every site taken, or none found for now) helping in an open mine of its base.
* Sharing a mine (`MinePlanner.forHelper`, `MinerJob.joinAsHelper`): a mine has at most one helper besides its
  holder. A miner helps only in an open mine of its base with a living holder that has a level planned (one
  between levels, or waiting for its helper, chooses first), no helper yet and its stairs dug (`bottom` 1 or more),
  and never in a mine it gave up. Its level follows the holder's rules (the wanted order
  above, then the default one; its pickaxe must harvest the ore): the level near the band's height (worked, or
  lengthened while `maxRounds` allows and the corridor is clear), a new branch mine there at least 6 steps
  (MIN_SPACING) from every level, or further layers within BAND_SPREAD where the corridor is short; failing that, any
  unfinished level it may work, the shallowest first. Only levels the stairs already pass (step at most `bottom`),
  never the holder's current level (also while the holder digs down to it) and never one within 3 steps of it
  (HELPER_CLEARANCE, PROPOSED: 2-high tunnels 3 apart share no cell, floor or roof). Of several mines, the most
  wanted ore first, then the nearest mouth. The holder's planner skips the helper's level (and those within 3 of
  it); when only that level (or a lengthening of it) is left, the holder waits at the storehouse (`WAIT`, looked at
  again every 600 ticks, idle meanwhile) instead of resting or retiring the mine. Neither follows an ore vein into
  the other's level layer (its two rows, the floor and the roof). The helper never digs or repairs the shaft except
  stairs in its way, and its integrity check covers only its own level.
* A help ends (the helper climbs out first) when its level is done or cut, the mine is set aside or rests (both
  release the helper's record), the holder works that level or one within 3 of it, its pickaxe is too weak for the
  level, it has a mine or cave of its own, or the stairs stay blocked (then no help anywhere for 6000 ticks,
  SITE_RETRY_TICKS). When the holder dies or is retrained (its record released), the helper takes the mine over at
  once and its level becomes the mine's current one (unless a descent was under way). The relation is saved in the
  mine's record (`helper`, `helperStep`; an older save has none) and handed back on death or retraining like a
  workplace.
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
  most ±7 sideways, +32 up and 192 logs: a giant jungle tree is up to 31 tall with about 150 logs, a mega spruce up to
  29 with about 115; wood running past those bounds is TOO_BIG and left whole, never cut in part). A tree is felled
  only with 3 or more logs (MIN_LOGS), natural (not persistent) leaves within 2 of its top, the stump on dirt, and no
  built block (planks, cobblestone, fences, glass, wool, torches, signs, rails, any structural block), block entity
  (a bee nest) or log of another species touching it, and none of its own wood in protected wood (below), not even
  diagonally (a giant jungle tree's branch end by the walls: the rest would be felled and that log left hanging). Its top log may be at most 8 above the stump (MAX_FELL_DY;
  with the 7.5 reach, every log can then be cut from the ground), or up to 32 (MAX_TALL_DY) when the woodcutter has
  the dirt for a scaffold (below). Species: oak, birch, spruce, jungle, acacia, dark oak and cherry; not mangrove,
  nether stems, stripped logs or other mods' logs.
* Scaffold (PROPOSED, `TreeShape.pillarSpot`, `Scaffold`, `WoodcutterJob`): a tree with a log the ground does not
  reach (higher than 8, or a branch end neither the stump's column nor the ground under it reaches) is felled from a
  pillar of dirt or coarse dirt (only earth: stone, cobblestone and netherrack drop nothing without a pickaxe, and the
  builders' tool-stone reserve is never touched). The pillar stands in one of the columns beside the trunk (8
  around a 1x1 trunk, 12 around a 2x2) on firm ground at the stump's level, with every cell above it clear (air,
  plants, natural leaves or the tree's own logs) for its height + 2, no part of a plan and not protected wood; from
  it every such log must be within 7.25 (PILLAR_REACH, the 7.5 reach less a quarter block) of the eyes at some
  height on the way down; the lowest such pillar wins, then the one reaching most logs, then one straight beside the
  trunk. No column reaching them all: the tree is left whole (a giant jungle tree's branch ends, from -2 to +5 of its
  north-west corner, are all reached from the south-east corner column). Height at most 28 (MAX_PILLAR); blocks
  needed: height + 2 (PILLAR_SPARE). Before walking to a tall tree the woodcutter carries at least
  `scaffoldNeed(top)` blocks (the worst column's height + 2), else fetches up to 32 (Scaffold.CARRY, dirt and coarse
  dirt together, kept in the backpack when unloading) at most every 2400 ticks; with too few in store it digs its own
  (below). With no trip allowed yet, or no ground to dig, the tree is skipped for 12000 ticks. Without at least 4
  blocks carried or stored, and with digging on hold (DIG_RETRY_TICKS), no tree taller than 8 is even looked for; a
  tree found while foraging (beyond the forest radius) needs dirt carried or stored. Building: it stands over
  the column's middle, cuts a vine out of the cell its feet are in (standing in vines a jump lifts it only half a
  block; giant jungle trunks are hung with them on every side) and natural leaves or the tree's logs in the cell
  above its head, jumps, and places a block in the cell its feet left once they are above it (vanilla jump: about 9
  ticks a block); 6 jumps without a block (MAX_MISSES) end the climb. On the pillar: the highest log within 7.5 first, strictly top down (while a log above 8
  stands out of reach at that log's height or higher, nothing below it is cut, so a climb cut short leaves nothing
  hanging), then natural leaves of the crown
  (within 5/6 of the stump) within 5.5; nothing left in reach, it breaks the block underfoot at player speed (dirt by
  hand: 15 ticks) and drops one block. A block missing below is put back first. On the ground the rest is felled as
  for any tree. It comes down early (without cutting) when hurt, at 1 free backpack slot (then unloads), at nightfall
  far out, when the tree is claimed by another, for a meal errand, or when its base or woodland is gone; the tree is
  resumed later with a new pillar (3 cut-short climbs per tree at most, MAX_PILLARS_PER_TREE). A started tree that no
  pillar can finish (no column, no dirt, or 3 climbs cut short) is left standing as it is, its top already felled;
  it is never cut from its foot. Up there it ignores
  fights, does not flee or dodge creepers and takes no knockback. Scaffold blocks left standing (it was moved off
  its pillar) are taken down from beside, from a pillar of its own when out of reach (3 tries, MAX_STRAY_TRIES); a
  block someone stands on is not broken. The woodland remembers every column where scaffold may stand
  (WorkplaceState.scaffolds, at most 16, PROPOSED): a woodcutter taking the woodland over (the last one died or was
  retrained up there) takes what is left down, looking every 200 ticks. The pillar (column, height, phase) and the
  climbs per tree are saved with the job.
* Dirt dug for a scaffold (PROPOSED, `DirtPatch`, `DirtDig`, `WoodcutterJob`): only when a tall tree within the
  forest radius waits for its scaffold and carried + stored dirt is short of its need; the storehouse's dirt is then
  left to the builders and the woodcutter digs the whole shortfall, up to the need (`scaffoldNeed(top)`, its spare
  included; at most 32). It digs the top block of grass, dirt, coarse dirt or podzol (they drop dirt; a shovel with
  Silk Touch is never used, and a Silk Touch axe goes into the backpack) by hand or with the best shovel it carries
  (switched into its hand for the digging, the axe back afterwards; 4 ticks a grass block with stone, 17 by hand),
  reach 4.5 (DIG_REACH), into its backpack. Where (all PROPOSED):
  * Seeds on a lattice 5 apart (SEED_SPACING) within 24 (SEARCH_RADIUS) of the tree, nearest first, 4 a tick
    (SEEDS_PER_TICK); each lays a patch over the 7x7 window around it (WINDOW 3) in rows along x or z (the way that
    gives more), two rows dug and the third left standing (ROW_PERIOD 3), so every dug cell has a side neighbour never
    dug, one block up: its step out. Cells nearest the seed first, at most the shortfall. The first seed giving all of
    it wins; else the largest patch of at least 4 cells (MIN_CELLS) or of all that is still needed; another patch is
    looked for when one is used up (3 per tree, MAX_DIG_SEARCHES).
  * A cell is dug only when its top is earth with a firm floor under it (no fluid, sand or gravel, leaves), only a
    plant that gives way on it (no flower, sapling or crop), open sky over it, and all eight neighbours ground one
    stands on (a full solid top, two free cells, no fluid) at its own height: so never beside water, a drop, a step
    up, a path or farmland, and never on or beside an earlier patch (its cells lie a block below their rims), so
    nothing is ever dug two deep. Every cell is checked again just before it is dug.
  * Never within 2 (TREE_CLEARANCE) of a log, leaves or a sapling (from a block below to 4 above); never within 4 of
    any base's walls (WALLS_MARGIN), in any plan, in protected wood (above), where a miner may not dig
    (`WorkSafety.mayDig`), within 6 of a fishing spot with water, 8 of a mine origin, 3 of a planted sapling or a stump
    waiting for one (`WorkSafety.reserved`, DIRT_PIT), on the road strip, within 1 of a grove lattice point of the work
    belt or of a scaffold column the woodland remembers, outside the forest radius or the map, or in unloaded chunks.
    At night (`remoteWorkInDaylightOnly`) nothing beyond the walls + 16 is dug, as for trees: a patch can lie 27 blocks
    from a tree by the walls, so the search and every cell keep to that, and a cell left beyond it at nightfall is
    dropped.
  * A patch it cannot get to (its walk runs out: 600 ticks + 8 a block, or 3 failed routes) is left, and so is the
    ground within 6 (DIG_AVOID_RADIUS, Chebyshev) of the patch's middle for 12000 ticks (DIG_AVOID_TICKS, at most 16
    such places, not saved), so the next search looks elsewhere instead of walking at the same cliff top again.
  * No patch: no digging for 2400 ticks (DIG_RETRY_TICKS) and the tree is skipped for 12000. Digging for one tree
    stops after 3600 ticks (DIG_GIVE_UP_TICKS); it stops at once (keeping its dirt) when the tree is gone or taken, at
    nightfall when the tree is far out, or with a full backpack (it unloads first). Not saved: after a reload it looks
    for the tree again.
* Protected wood (`WorkSafety.protectedWood`): any base's plan, 2 around a settlement's walls (its own included),
  other bases' land + 8 (lairs + 16), outside the map, within 32 of a village or 48 of a pillager outpost or
  woodland mansion the faction knows.
* Search (`ForestScan`): every second column (one of 4 rotating patterns per scan) in a circle of `forestRadius`
  around the base centre, loaded chunks only; a column counts when its top block (leaves aside) is a known log whose
  trunk of 3–9 logs of the same block (up to 33 with dirt for a scaffold) stands on dirt, with natural leaves near the
  top; a 2x2 trunk is found at its north-west corner. Best: distance from the centre + 0.5 × distance from the woodcutter. Scans run at most
  every 600 ticks (RESCAN_TICKS); after an empty one the next pattern follows 20 ticks later, and only a whole empty
  round of 4 sends the woodcutter foraging. A tree another woodcutter claimed is skipped (claims last 1200 ticks).
* Felling from the ground (PROPOSED): the walk ends within 4.0 of the stump (2.5 approach); logs are cut top-down from where it
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
* Replanting (`ForestRules.replant`): the same species at the stump, 1 sapling; a dark oak gets 4 as a 2x2, only
  when 4 are carried; a 2x2 spruce or jungle tree gets 4 as a 2x2 when 4 are carried (so a mega spruce or giant
  jungle tree grows back), else, after 2400 ticks (HUGE_PATIENCE) of waiting for them, 1 at its north-west corner. A stump waiting 12000 ticks (REPLANT_PATIENCE) for its own
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
* Growing room (PROPOSED): vanilla grows a tree whatever stands in its trunk, so a woodcutter standing half in a
  sapling's column (ground to 3 up; the whole square for saplings side by side) steps straight out of it before its
  bone meal, and an instant Regrowth earned there waits until it has stepped clear (REGROW_WAIT_TICKS 200, then the
  bonus is lost); hemmed in for 40 ticks (STEP_CLEAR_TICKS) it leaves that sapling for a while.
* Bone meal: used round-robin on planted saplings, those outside the belt first. Unloading keeps 8 saplings of each
  species and 16 bone meal (topped up to 16 below 8). The blacksmith turns bones into bone meal (1 → 3) while fewer
  than 16 are stored. Records: 64 planted saplings and 32 waiting stumps (oldest dropped).
* Forage: 72 blocks out from the centre in one of 8 directions (16 inside the map; a direction near a known outpost
  or mansion is skipped), at most 2400 ticks a leg, then a look 40 around; the direction moves on only after a miss;
  after 8 misses it rests 6000 ticks. Never at night or from a GameTest base.
* Night: a woodcutter does no work beyond the walls + 16 between day time 13000 and 23000 (config
  `remoteWorkInDaylightOnly`); a tree started there waits for daylight. With nothing else to do it goes home for the
  night only once its tree search has read every column (4 patterns, 20 ticks apart), so a tree near the walls is
  still felled at night. Forage legs, and night waits with no tree started, do not reset its idle time (waiting to
  finish a started tree counts as busy).
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
  A worker lacks its tool when there is none of its kind in its hand or its backpack (a woodcutter digging dirt with
  a shovel has its axe put away meanwhile), or the one it holds is worn.
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
  attacker near home) until over 60% or for at most 1200 ticks. Soldiers only: a hero never withdraws (it escapes for
  good under its escape threshold or fights to the death, `HEROES_V2.md` 6.13).
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

## Reaching a target (`Pursuit`, `ReachPlanner`, `Reach`, `OpenGateGoal`)
A fighter whose path to its target comes back partial (it cannot walk up to it and hit it) plans a way or waits out
of reach; see `docs/design/notes/combat-unreachable.md`. All values PROPOSED.
* Gates: while fighting, and for 600 ticks (GATE_MEMORY) after a fight in which it went through a gate (so it walks
  back out of the pen when the fight is over), a closed fence gate counts as a door on the path. Live check: the 600
  ticks now count from the end of the fight, however long it lasted (they counted from opening the gate, and after a
  40 s fight in a pen soldiers were hopped over the fence by the stuck recovery), and a fighter that walks through a
  gateway another one opened counts as having gone through a gate. Opened when the path's next nodes reach
  it within 2.25 blocks; closed once the NPC is through (far side), 3 blocks away or 80 ticks after opening, never
  while anyone stands in the gateway; then 20 ticks before that NPC opens a gate again.
* Planner: only for a target within 12 blocks (horizontal) and 5 up or down; box 4 blocks round both, 3 down.
  Costs in rough ticks: walk 5, step up 8, drop 5 + depth (at most 3), build up a block 12, plus breaking. A plan
  over 600 ticks is no plan. At most 16 blocks broken per target, at most 3 built up per climb, never higher than the
  target and only within 2 blocks of its column. Strikes from beside the target's column (diagonals with an open
  side at eye level), at most 1 block up or down. Breaking the block under a target 2+ up: planned from within
  4.0 blocks of the eyes (the cell's middle), done from within 4.5 (a player's block reach). A worker (civilian)
  never plans: it steps out of reach (at most 100 ticks) and lets the target be.
* Breaking (`Reach.mayBreak`): never a planned block, a block entity, a block in or beside a fluid, a mine's stairs,
  a base's ground (its walls box + 1, below its floor), or anything over 100 ticks to break with the held item; a
  foreign block in a planned cell yes (not the base's own player's edit for that faction or its allies); in a base of
  its own side an unplanned block only if a player not of its side placed it (`intruderBlocks`, at most 4096 per
  base; live check: also a player of no faction, whose pillar in a yard the soldiers could not touch before). A
  block it broke for a target and that is put back is not broken again for that target.
* Building up (`Reach.mayPlace`): an empty cell outside every plan, not in a base of its own side (walls box + 1)
  except below its floor (filling a hole in its ground: a pit it is shut in), not in a mine's stairwell.
* Boxed in (live check, a 1x1 pit two deep in a base's yard, the player hitting from the edge): waiting within 4.7
  blocks (horizontal) of the target, unable to strike from where it stands, and not getting 1 block away (horizontal)
  for 30 ticks (BOXED_IN), a melee fighter uses the stuck recovery (a hop of at most 4 across and 2 up or down, the
  configured `movement` range) toward the waiting ring; at most 3 times per target (MAX_UNBOX). With blocks it
  builds itself out first (the planner). Its strike check counts only while it stands (in the air, knocked up by a
  blow, its hit box reaches higher than from where it stands).
* Waiting: on a ring 6.5 blocks round the target (archers 9, in sight of it), at least 5 blocks away horizontally,
  nearest the end of the path toward it, 2+ blocks from another fighter's spot; staying put when already 5 to 9 away.
  One path search toward the target every 40 ticks (then the planner again), a new spot at most once a second when
  the target moved 2 blocks (at once when it is within 4.7), at most 3 path searches per new spot. Archers are called
  (32 blocks, those with no target) on starting and every 100 ticks. Close to the target (3 blocks) for 60 ticks
  without a blow although the path reaches it (20 ticks when the target is 1.5+ blocks above or below, as in a 1x1
  hole), not getting 2 path steps further meanwhile: treated as out of reach. Away from home (more than 16 blocks
  outside its walls) a fighter gives up after 1200 ticks and ignores that target for 1200. A civilian stops fighting
  instead.
* Spots it cannot get at (review): when walking up did not work (the stuck rule above), the planner said it could
  strike where the swing does not land (one 20-tick try), or it took 3 blows from the target it could not answer
  (working its way in, no path, or 1.5+ blocks below/above within 3), the target's cell is remembered: it waits out
  of reach and does not walk up to that cell again; a cell marked with no path to it is cleared when a path opens.
  After 6 such blows from one target in all it plans no more breaking or building toward it. Kept per target for
  6000 ticks after it last learnt something (also when the target leaves and comes back); a landed blow clears it.
* Cover (review): a waiting melee fighter shot by its target waits out of the target's sight (900 penalty for a spot
  in sight, rings 6.5 and 10, farther preferred), or 12 blocks off when there is no such spot.
* Stale targets: the 10 s without trading blows rule counts working toward the target, waiting in sight of it, in
  cover from it, or, for an intruder (a player or an enemy soldier), at home (within 24 blocks) as engaged. Vanilla's
  hurt-by target memory still lets go of an attacker unseen for 3 s. Live check 2: a monster it waits on (HOLD) and
  that goes stale is also ignored for 1200 ticks (IGNORE_TICKS), and at home a monster it cannot get at is given up
  after 1200 ticks of waiting as away from home (a creeper shut in a pocket of the ground under the yard held two
  guards for good). Waiting on a monster, at each 40-tick look it turns to the nearest enemy player or enemy soldier
  in sight within 16 blocks (INTRUDER_RANGE).
* Defence alarm (live check 2): raiders and enemy players are handed to free guards before monsters; a monster counts
  only when it is no more than 2 blocks below the yard floor (not in a cave under the base), and a guard is not
  handed a target it gave up on.
* Target cell (live check 2): paths and plans aim at the target's block position, unless that holds a fence, wall or
  pane it is only pressed against: then the open cell beside it that its body is mostly in (a player against a pen's
  far fence was "in" the fence cell, and the cell outside the fence was as near as the one inside). A fighter's path
  search toward its target may wander 64 blocks walked (PATH_RANGE; vanilla stops at the follow range, 48, and
  overwrites walked distances while exploring a dead end, which cut off the way round a pen to its gate); the node
  budget is unchanged.
* Wounded workers (live check 2): a fleeing worker with no way away and its threat within 4 blocks (UNBOX_RANGE, a 1x1
  pit it jumped into after a player) uses the stuck recovery toward the station stand (or 8 blocks away from the
  threat), at most once per 40-tick look.
* Guards carry up to 8 dirt, cobblestone or cobbled deepslate (each kind) for building up, taken only while the
  storehouse keeps 64 of that block.
* Repairs (live check: a door a raider broke was never put back when the storehouse held no door): a builder fetching
  parts for repairs crafts a door, fence, torch or chest from the storehouse's materials (logs for planks and sticks)
  when none is stored, as it does for a project.

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
  base is free (holding neither an open mine nor a cave); at most 2 cave miners per base. A helper holds neither, so
  it counts as free: no miner is hired because helpers exist, and an open mine whose miner is gone is taken over by
  its helper. A helper that has had no mine or cave of its own for 12000 ticks is a spare hand and may be retrained
  like an idle worker ("no mine of its own (only another miner's to help in)"); a holder waiting for its helper is
  idle ("no level of its own left in its mine (only its helper's)").
* Fishermen: hired only where the fishing spot has water, has not been searched yet, or was found to have none
  48000+ ticks ago (and not while the role is barren), with stored food under 15 recruits' worth and a rod or 2
  string in storage.

## Levels and affixes (`AffixRoller`, `Affix`)
* Level n → n+1 costs 40 + 30n XP; max level 30; one affix roll per level; job affixes weighted ×2.
* Caps: Vitality +2 HP ×5, Swiftness +3% ×5, Might +1 ×5, Toughness +1 ×5, Diligence +6% dig/build speed ×5, Prospector +5% chance an ore drops twice ×5, Regrowth +8% instant growth ×5, Green Thumb +10% chance a crop drops twice ×5, Pathfinder +16 range ×4, Mason +10% build speed ×5, Marksman +10% arrow damage ×5, Thrifty 8% chance a wear event is skipped ×4, Hardy −8% exhaustion ×4, Duelist +10% critical damage (×1.5 → ×2.0 at 5) ×5 (swordsmen, axe fighters), Angler 8% chance the fishing loot is rolled twice ×5 (fishermen), Haggler −5% emerald prices ×4 (traders).
* XP: ore XP above (stone 0); 1 per log/harvest/breeding feed; 2 per butchered animal; 20 per mine layer reached or opened and per lengthening round; 1 per repaired block; 25 + blocks/20 per project; scouts: report base value (enemy base 12, lair 12, woodland mansion 12, pillager outpost 10, cave 8, village 8, ore 6, hostiles 4) + 1 per 32 blocks from home. Trees and water are not reported.

## Soldiers (`NpcMeleeAttackGoal`, `NpcRangedAttackGoal`)
* Melee: the held weapon sets the player's attack speed attribute (swords 1.6, axes 0.8–1.0, bare hands 4), and
  `getAttackInterval` turns it into 20 / speed ticks clamped to 10–40 (swords 12–13, axes 20–25, bare hands 10). Note:
  the swing cooldown actually used has always been 20 ticks for every weapon (vanilla's melee goal ignored that
  interval); the rewritten goal keeps 20 so the war balance does not shift. Using the interval is an open choice.
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
* Heroes in the war council (HEROES_V2 8.1, A1b): `soldierPower` is the average of the ordinary soldiers only; a
  base's defence is its soldiers at home x that power plus the war power of each hero whose home it is (alive, at
  home, not on a raid; **half** under half health), the heroes counting at most **1.5x** the soldiers' power (the
  soldier backbone). A hero's war power is `2.25 x sqrt(maxHealth / 20) x (1.5 + level / 30) x war.heroPowerPercent /
  100` (`heroPowerPercent` **100**, 0-200; 0 counts a hero as one soldier by heads, as before). The target score reads
  the defence in soldier units. With no hero on either side every decision is the same as before.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; with `survival.hunger` off, NPCs regenerate 1 HP per 4 s once 10 s have passed since they were last hurt; with it on (the default) they heal only through the food bar (see Hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.

## Heroes (config `heroes`, `boss`; `HeroSize`, `HeroManager`, `HeroIdentity`, `HeroLoadout`, `HeroSpeech`, `HeroClips`, `FaceRules`, `HeroLod`) — in development
Only the test model ships so far (its definition is `data/factionclash/heroes/test_rig.json`); an operator gives a hero to
a faction with `/fc admin hero give`.
* Definition (`HeroLoadout`, A1b): the stats of the hero's file with growth (v1 5.1-5.3) and **boss health at all times,
  whoever and however many it fights** (one mode): `MAX_HEALTH` base = the v1 health, one `factionclash:boss_health`
  modifier (`ADD_MULTIPLIED_TOTAL`) = `health_scale / 100 x role factor - 1`; `health_scale` by rarity **250 / 300 /
  350 / 400** (common to legendary; a leader +50 once leaders exist), times `boss.healthScale` **100%** (25-300);
  role factor vanguard **1.0**, striker **1.2**, skirmisher, support and controller **1.25**, marksman and summoner
  **1.3**. The test rig (a common striker) has 28 x 2.5 x 1.2 = **84** health at level 1. `stats.attack` and
  `stats.armor` are added like the Might and Toughness affixes, `stats.speed` multiplies the walking speed,
  `knockback_resistance` / 100 is its knock-back resistance (heroes only; ordinary NPCs keep a player's). A change of
  maximum (a level, a reload) keeps the share of health. A hero whose definition is broken or gone is **dormant**:
  20 health, nothing added, the neutral model, `(dormant)` on its name tag.
* Hitbox (`HeroSize`): the model's height, `height_px x render_scale x scale / 16` blocks (rig v3 at render scale 0.56:
  adult 1.82, teen 1.58, chibi 1.23), eyes at `(head_y + 3) x render_scale x scale / 16`; width 0.6 x scale. At least
  **1.2 blocks** (PROPOSED; an 85% chibi would be 1.04) and at most **1.98** (a 110% adult would be 2.00; the eyes drop
  in proportion), so every hero walks through a 2-high doorway; the model itself draws at its full scale. The culling
  box is 1.5 blocks larger all round (wings, tails, long weapons).
* Role: a hero counts as one soldier of the role its basic attack and weapon fit (bow archer, crossbow crossbowman;
  melee with an axe or a mace, or an axe, hammer or scythe style, axe fighter; every other melee, trident and thrown
  hero swordsman) for recruitment needs and the garrison by heads, but the raid party's soldier sort never picks it.
  It is never retrained. Until the kit engine (A3) it fights with that role's basic attacks.
* Kit: the `gear` of its file (names and enchantments included), bound to the hero: never dropped, never deposited,
  gone when better storehouse gear of the same kind replaces it, given again on its return. A **fists**-style hero
  holds no weapon: the stone sword the checker adds to a melee kit with none is left out, and it never fetches or
  takes a weapon (thrown heroes fight bare-handed too until A3). A hero takes a storehouse shield only when its kit
  has one; a hero holding a kit weapon of another kind than its role's tool (a mace) keeps it. Bow and crossbow heroes
  carry the one never-used-up arrow. A dormant hero gets its role's basic weapon as a plain soldier.
* Name tag: `★ Name · title` in the hero's colour (its file's `color`, else its faction's), `[Fighting 80%]` while it
  has a target (its energy; `heroes.showEnergy`), `(dormant)`. Energy (v1 14, minimal until A3): a landed melee
  basic hit **5**, a kill **10**, **1** per 2 health lost (at most 8 a hit), each x its ultimate's `charge_rate`;
  0-100, saved, 0 after a revival.
* Lines (`HeroSpeech`, v1 23): one line per hero per `heroes.dialogueCooldownSeconds` **20** (5-300); `summoned`,
  `fallen`, `revived` and `ultimate` always; greetings **5 s** apart, `idle` at most every **15 min**. Chances:
  `fight_start` **50%**, `kill` **30%**, `skill` 25%, others always. Heard by: nearby players within
  `heroes.dialogueRadius` **32** (8-128), the faction (summoned, fallen, revived, level-up, raid and base news) or the
  player greeted; `heroes.dialogue` `local` / `faction` / `off`. In chat as `<★ Name> line` and, with
  `heroes.speechBubbles`, in a text display **0.75** blocks above the head for **3 s** while the face talks. Events
  (PROPOSED where v1 is silent): a fight starts when it takes a target after **10 s** without one; it is won after
  **5 s** with no target and no hostile within **16** blocks; low health under **30%**, said again only after it has
  been back above **50%**; `idle` at its base with a faction player within **12** blocks; `ally_fallen` for a member of
  its side within **24**. Events a file has no line for use short generic lines (`heroes.defaultLines`).
* Death: `heroes.reviveSeconds` **600** (30-3600) later the hero returns at its home base's spawn point (or its
  faction's first standing base), at full health with its kit, level, experience and traits, as a new entity; a faction
  with no standing base loses it (released). Its own `revival` block sets its delay instead (30-3600 s) and where it
  rises (`home`, `where_it_fell`, `nearest_base`; `at: night` waits for nightfall); the faction's best `revival_rite`
  talent (any of its heroes; tier by the holder's level: I from 1, II from 10, III from 20) shortens it by **20 / 30 /
  40%**; never under `heroes.minReviveSeconds` **30** (30-3600; it can only be raised). A hero the director finds missing (gone from a loaded area for 2400
  ticks) counts as fallen. One living entity per hero id: any other copy is discarded when it joins the level
  (spawned or loaded with a chunk), and so is a dead or dying copy (saved within the second after its fall).
* Animation (`HeroClips`, client): standing below a ground speed of **0.015** blocks/tick, running from **0.245**
  (PROPOSED: between an NPC's walk, 0.216, and its sprint, 0.28); a run keeps running down to **0.20**
  (`RUN_TO_WALK`, PROPOSED) and a change between walk and run shows only after the new pace has held **6** ticks
  (`PACE_HOLD`, PROPOSED; starting and stopping show at once), because NPCs travel at about 0.24-0.26 and the clip
  otherwise flipped between walk and run several times a second. `move.walk` plays at ground speed / **0.11** and
  `move.run` at ground speed / **0.20** (PROPOSED, by eye: about 2x for a walking NPC, 1.4x for a sprint), clamped to
  **0.6-2.2** in steps of 0.1, changed only by **0.3** or more. Falling: off the ground and dropping faster than
  **0.25** blocks/tick. Controller blends **4 / 2 / 6** ticks (base, action, secondary; the timing files' own).
  A move of more than **3** blocks across the ground in one tick is a teleport and is left out of the ground speed
  (`HeroClips.TELEPORT_STEP`). Form-scaled keys (`FormScale`): `v.fc_dhip` = the model's hip height minus **18.25** px.
* Face (`FaceRules`, `BlinkSchedule`, `FaceClock`): blinks every **2.5-6 s** (nervous 1.5-3.5, calm 4-7, sleepy 3-5;
  from the hero's visual file), half/closed/half **1/2/1** ticks (sleepy 2/4/2), **15%** double blinks two open ticks
  apart, none during a wind-up or channel and an overdue blink **6** ticks after it; talking beats of **2-3** ticks
  (one step in five skipped); a mood change holds **10** ticks; the face freezes beyond **16** blocks.
* Mood and weapon (`MoodRules`, server): forced > hurt (vanilla's **10**-tick hurt time) > asleep > wind-up focus >
  fighting (determined) > below **30%** health (tired) > calm. The weapon is drawn while the hero has a target and
  **100** ticks after its last blow.
* Level of detail (`HeroLod`): `fine_` bones within **20** blocks, the face within **16**, the secondary controller and
  the chain sway within **32**, locator tracking within **24**, each with a **1**-block band.
* Procedural touches (client): head look pitch **+-60**, yaw **+-80** degrees; hit flinch **7** degrees on the chest;
  ears back **25** and out **15** while hurt or angry; chain sway at most **25** degrees (`Sway`: 0.8 + 3.5 x stiffness
  Hz, damping ratio 0.8 x damping).

## Hero mobility and work mastery (`MobilityRules`, `LeapSolver`, `FlightPlanner`, `HeroLinks`, `WallEntry`, `WorkMastery`) — pure rules, not yet read by the game
All PROPOSED (`docs/heroes-v2/MOBILITY_AND_WORK.md`); ordinary NPCs keep every number of today (no links, no
modifiers, 18 slots, 1 + Diligence).
* Power by level: `p(L) = 0.6 + 0.4 x (min(L, 20) - 1) / 19` (`mobility.levelPowerAtOne` **60**, 30-100;
  `mobility.fullPowerLevel` **20**, 10-30): 60% at level 1, 79% at 10, 100% from 20. A bonus over a worker's value
  scales as worker + p x (ceiling - worker), a reach a worker lacks as p x ceiling, a wait as ceiling / p; link sizes
  round down. Floors: a leaper jumps at least **1.8** blocks, a gap or hover gap is at least **2**.
* Full values (ceiling I / II / III): runner travel +10/15/20%, efficiency 0/0.5/1.0, travel hunger -20/35/50%,
  III sprint +40% for 3 s every 15 s; charger knockback +0.1/0.2/0.3 while travelling, safe fall 5/7/9, II-III
  charge +60% every 20/12 s; leaper jump 1.8/2.5/3.5 (strength 0.52/0.62/0.75), gaps 5/6/8, safe fall 6/10/16;
  acrobat air jumps 1/1/2 of 1.25 each, gaps 4/5/6, safe fall 8/16/none; climber 6/16/any; glider sink 3.0/2.4/2.0
  and forward 7/8/10 b/s for 10/15/20 s; flyer stamina 30/45/60 s, cruise 6/7/8, climb 2/3/4 b/s, take-off at
  30/25/20%, regain 2.0/2.5/3.0 a second after 1 s; hover 0.5/1.0/1.5 high over gaps of 2/3/4; swimmer x1.5/2.0/2.5.
* Jump heights in vanilla's air physics: strength 0.42 = **1.25** blocks, 0.52 = **1.84**, 0.62 = **2.52**, 0.75 =
  **3.54**; a leap lands within **0.15** of the cell centre (3 gap: 0.42, 0.52; 5: 0.50, 0.72; 6: 0.54, 0.82; 8: 0.63,
  1.00); a jump-up link is the jump (with air jumps) minus **0.3**, 1.5 to 3 blocks; at most **8** links a node.
* Flight: drain 1.0 a second powered, 0.5 cruising, 2.0 hovering, 0 gliding; x1.5 loaded, x1.5 fighting; head for a
  landing at **25%**, exhausted at 0 until **50%**; take-off 0.6 s, a 1.5-block push, **4** blocks of headroom over its
  footprint; reserve to the best landing seen **+25%**; cruise **6-10** blocks over the terrain, samples every **4**
  blocks and **2** to each side, one **24**-block waypoint (48 samples) at a time; a head bump for **10** ticks or under
  **1** block of progress in **2 s** ends a flight (dead for 10 minutes); fly a waypoint when the ground route is
  **1.5x** the line or the trip is over **40** blocks and flying is faster.
* Fights (any enemy): **8 s** a take-off (`mobility.bossAirSeconds`, 4-10), **6 s** on the ground (4 in phase 3), at
  most **40%** of any 30 s in the air; a raid's approach 10 s, 6 s; ceilings: travel and escape **24** above the
  ground (`mobility.travelAltitude`, 8-48), raid **10**, fight **6** above the ground or the highest engaged enemy.
* Walls (`WallEntry`): chosen **24** blocks from the walls after the muster; a goal ends after **60 s** without progress;
  a greedy raider (greed **65**) loots the storehouse first; a flyer's way out regains its take-off share within **20 s**.
* Escape: away **300 s** (`heroes.escapeAwaySeconds`), one in **600 s** (`heroes.escapeCooldownSeconds`), cornered
  after **3 s**, over at the refuge or **10 s** with no pursuer within **48**; in the wild at least **64** blocks from the
  fight; morale -4 at home (-6 everywhere for a leader) against -8 and -20 for a death.
* Work mastery at its duty (tier I / II / III at levels 1 / 10 / 20): work speed +15/25/35%, backpack 21/24/27, reach
  +0.5/1.0/1.5, wear -10/20/30%, hunger -10/15/20%, work XP +10/20/30%, guard alarm reach +8/12/16. Caps: work speed
  **2x**, an extra roll **75%** and only one, virtual levels within III with the tool's own (none with Silk Touch),
  wear **40%** and hunger **50%** of a worker's at least, **36** slots, prices **-40%**, furnaces **2x** vanilla, trips
  **+40%**; `work.outputCap` **3.0** (1.5-5.0). Specialties and Presence (tier IV) are the tables of
  MOBILITY_AND_WORK 11.1 and 11.2 (`WorkMastery`).
* The dive (format 2's ninth new form): range 4-12 (8), radius 1-3 (2), heavy, a landing decal, weight as a leap + 1,
  at least **1.0 s** of recovery on the ground.

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
