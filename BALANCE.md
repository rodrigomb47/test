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
  4 blocks below its destination) it keeps digging a staircase, one step per 20 ticks and at most 64 steps: up if
  the destination is above, down if below, otherwise level. It stops when it is out or a walking route exists
  (checked every 4 steps). Under a settlement's edge it first heads out from under the walls. Never dug: faction
  structures, a settlement's walls and floor band, any mine's spiral stairs, containers, unbreakable blocks and
  anything touching a fluid. Up a tree it breaks the leaves or log underfoot (the wood goes into its backpack).
* Recovery teleports never land on leaves or on top of a tall trunk.
* Leaving a settlement, NPCs head for the gate with the shortest walk to the destination; long trips past their own
  base put their waypoints around the walls.

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

## Mines (`SpiralMine`, `MineLayout`, `MinerJob`)
| Level | Depth | Needed to dig down to it |
|---|---|---|
| Tier 1 | ≈ 6 below the hut floor | built with the base |
| Tier 2 | y = 32 (iron band) | a pickaxe (consumed), stone or better |
| Tier 3 | y = 0 (diamond band) | a pickaxe (consumed) and an iron pickaxe in hand |
| Tier 4 | y = −48 | a pickaxe (consumed) and an iron pickaxe in hand |
| Further levels | 6 deeper each, down to y = −56 | as tier 4 |

* Shaft: 2×2 spiral staircase, one block down per step, a landing every 4 steps, wall torch every 8 steps.
* Branch mine per level: main tunnel 40 blocks long and 2 high from the landing, side branches of 10 blocks every
  4 blocks on both sides, torches every 8 blocks.
* After the deepest level, "infill" branch mines are dug in the middle of every gap of 12+ blocks between levels
  (deepest first), reached through the existing spiral; no pickaxe payment.
* Lengthening: once every level (regular and infill) is dug, the mine is lengthened up to 4 times. Each round the
  miner revisits the regular levels from the deepest up and continues each main tunnel another 40 blocks out, with
  its branches (so main tunnels reach 200 blocks after 4 rounds). No payment.
* Stairs: a spiral step whose floor is gone is repaired with a stone block from the miner's backpack.
* The miner digs down to the next level once 60% of the current level's plan is dug and the payment pickaxe is in
  storage; when the level is completely dug and no payment is available, it digs down without one (it still
  needs an iron pickaxe in hand for tier 3 and deeper).
* Junk: once storage holds 512 cobblestone, cobbled deepslate, dirt, gravel or similar, the miner keeps at most 64
  of that item in its backpack and tosses the rest it digs (the miner keeps 64 sealing blocks and 32 torches).
* Ore XP: coal/copper 2, redstone 3, iron/lapis 4, gold 6, diamond 10, emerald 12; ordinary stone 0.

## Tree plots (`TreePlotLayout`, `WoodcutterJob`)
* Tier 1/2/3: 2×2 / 3×3 / 4×4 trees, spaced 6 blocks; fence grows away from the base.
* Upgrade: only with a wood surplus (≥ net fences + 96 plank-equivalents), ≥ 64 logs produced and no project waiting.
  If the fences cannot be made when the work starts (the wood went elsewhere), the woodcutter cuts trees for
  2400 ticks, keeping up to 64 logs and planks for the fence, and tries again.
* An overgrown plot: when walking gets no closer for 100 ticks inside the plot, the woodcutter cuts through the
  leaves toward its tree; stuck outside the fence for 200 ticks, it goes round to the opening.
* Saplings grow naturally; the woodcutter uses bone meal (vanilla growth step) when it has some. The blacksmith
  turns bones into bone meal (1 → 3) while fewer than 16 are stored.
* Leaves drop by their own loot table (vanilla oak: sapling 1/20, apple 1/200, sticks).

## Caves (`CaveSurvey`, `CaveExplorerJob`, `CaveMinerJob`, `CaveWork`)
* Scouts report a cave only where they can see a way in (a mouth, pit or ravine opening); caves within 128 blocks
  of a base, outside the base and its own mine, are surveyed.
* Explorer: sees ore up to 10 blocks from its eyes with a clear line of sight to an exposed face; survey spots at
  least 7 blocks apart, each 6–24 walking steps from the last; at most 48 spots, 64 blocks from the entrance,
  half a day (12000 ticks) and 32 placed step blocks per trip; turns back when out of torches, wounded (below 60%
  health) or with 2+ monsters close. It takes 32 torches (crafted in hand from coal and sticks if needed) and 32
  cobblestone/dirt for steps. A worked-out cave is surveyed again after two days.
* Cave mining: at most 2 cave miners per base; a marked ore is reached from a standing spot within 4.5 blocks
  or by tunnelling at most 12 blocks toward it; then 4 prospecting tunnels, 16 blocks long and 2 high, from the
  cave wall nearest the deepest explored spot, torches every 8 blocks.

## Hauler (`HaulerJob`)
* Recruited once a base has 12 NPCs. Picks up items that have lain inside the walls for 10 seconds (not in the
  farm or pen, whose workers collect their own), and moves stacks out of the overflow chest or a wrong chest into
  their category's chests when those have room.

## Terrain and sites
* Around the levelled base, mine and plot pads the ground is blended over 5 blocks (at most one block of height
  per block of distance), so the pads' edges are slopes, not cliffs or trenches.
* Site choice also scores the ground over the mine's first level (50 blocks east of the base): +6 per sample with
  water over the tunnels, +3 where the ground dips toward the tunnel depth.
* Woodcutters fell wild trees within 64 blocks of their plot when the plot has nothing to do.

## Animals and fishing (`AnimalCarerJob`, `ButcherJob`, `FishermanJob`)
* Breeding: one food item per adult (vanilla `isFood`), only adults that are ready (age 0); vanilla handles the
  baby and the 5-minute cooldown. At most 8 animals per species per pen.
* Feed kept in storage for other uses: 24 wheat seeds, 4 wheat, 4 carrots/potatoes/beetroot.
* Milking: a bucket per cow at most every 6000 ticks. Shearing: vanilla `readyForShearing`.
* Butcher: keeps 4 breeding adults per species, never kills babies or animals in love; melee hit every 12 ticks.
* Fishing: 100–600 ticks + 20–80 ticks per catch (Lure −5 s per level), vanilla fishing loot table with Luck of
  the Sea; the fisherman unloads at 32 items.

## Recruitment (config `recruitment`)
* `foodValue` 20 hunger points per recruit, paid with stored food at each item's normal nutrition (bread 5, cooked beef 8, raw beef 3, …). The planner finds the smallest overpayment and spends cheaper food first. Golden apples and harmful foods are excluded.
* Equipment: the role's tool/weapon — the finished item if stored (soldiers get the best, civilians the cheapest adequate), otherwise its vanilla crafting materials (e.g. stone pickaxe = 3 cobblestone + 2 sticks; sticks may be paid as planks or logs). Archers also take 1 arrow (or flint + stick + feather; the 3 spare arrows return to storage).
* `intervalTicks` 1200 between recruits per base; `rangedShare` 0.4 archers among soldiers; garrison cap 12; population cap = beds/bedrolls.

## Levels and affixes (`AffixRoller`, `Affix`)
* Level n → n+1 costs 40 + 30n XP; max level 30; one affix roll per level; job affixes weighted ×2.
* Caps: Vitality +2 HP ×5, Swiftness +3% ×5, Might +1 ×5, Toughness +1 ×5, Diligence +6% dig/build speed ×5, Prospector +5% chance an ore drops twice ×5, Regrowth +8% instant growth ×5, Green Thumb +10% chance a crop drops twice ×5, Pathfinder +16 range ×4, Mason +10% build speed ×5, Marksman +10% arrow damage ×5.
* XP: ore XP above (stone 0); 1 per log/harvest/breeding feed; 2 per butchered animal; 20 per new mine level; 1 per repaired block; 25 + blocks/20 per project; scouts: report base value (enemy base 12, lair 12, cave 8, ore 6, hostiles 4) + 1 per 32 blocks from home. Trees and water are not reported.

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
* Raids: interval 36000 ticks, party 3, 3 home defenders kept; assault takes 400 ticks at the target heart.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; NPCs regenerate 1 HP per 4 s out of combat (they have no hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.
