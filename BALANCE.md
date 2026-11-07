# Faction Clash — proposed balance values

The original project's numbers (map size, loot tables, upgrade bills, recruitment prices, affix caps, …) were
not available, so every value below is a **PROPOSED** default, chosen to make the simulation progress at a
watchable pace. Most are configurable in `config/factionclash-common.toml` (created in your Minecraft or server
`config` folder on first launch; defaults in [docs/factionclash-common.toml](docs/factionclash-common.toml)); the rest are constants named in the
source.

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
* Drops come from the block's own loot table, only with a tool that can harvest it. Item pickup box: the NPC's
  box grown by 1 × 0.5 × 1 blocks, like a player's.
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
* The miner digs down to the next level once 60% of the current level's plan is dug and the payment pickaxe is in
  storage; when the level is completely dug and no payment is available, it digs down without one (it still
  needs an iron pickaxe in hand for tier 3 and deeper).
* Junk: cobblestone, cobbled deepslate, dirt, gravel and similar are not picked up once storage holds 512 of that
  item and the miner carries 64 (the miner keeps 64 sealing blocks and 32 torches for itself).
* Ore XP: coal/copper 2, redstone 3, iron/lapis 4, gold 6, diamond 10, emerald 12; ordinary stone 0.

## Tree plots (`TreePlotLayout`, `WoodcutterJob`)
* Tier 1/2/3: 2×2 / 3×3 / 4×4 trees, spaced 6 blocks; fence grows away from the base.
* Upgrade: only with a wood surplus (≥ net fences + 96 plank-equivalents), ≥ 64 logs produced and no project waiting.
* Saplings grow naturally; the woodcutter uses bone meal (vanilla growth step) when it has some. The blacksmith
  turns bones into bone meal (1 → 3) while fewer than 16 are stored.
* Leaves drop by their own loot table (vanilla oak: sapling 1/20, apple 1/200, sticks).

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

## War (config `war`)
* Scouts: range 160 (+Pathfinder), at least 96 blocks out, 4 sectors per expedition, overdue after 24000 ticks.
* Raids: interval 36000 ticks, party 3, 3 home defenders kept; assault takes 400 ticks at the target heart.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; NPCs regenerate 1 HP per 4 s out of combat (they have no hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.
