# Faction Clash — proposed balance values

The original project's numbers (map size, loot tables, upgrade bills, recruitment prices, affix caps, …) were
not available, so every value below is a **PROPOSED** default, chosen to make the simulation progress at a
watchable pace. Most are configurable in `config/factionclash-common.toml`; the rest are constants named in the
source.

## Campaign (config `campaign`)
| Value | Default | Effect |
|---|---|---|
| `mapRadius` | 320 | Half-width of the campaign map around world spawn. The start-up search may enlarge it ×1.5 if a faction's sector has no dry land (logged). |
| `humanFactions` / `monsterLairs` | 4 / 2 | Ember Legion, Grove Covenant, Tide Alliance, Stoneguard; two Hollow lairs. |
| `keepFactionAreasLoaded` | true | Bases, workplaces and travelling NPCs keep ticking without players (bounded chunk tickets). |

## Constructed mines (`MineLootTable`, `MineLayout`)
Weights per output (ore weights ×(1 + 0.25·(tier−1)); ineligible weight falls back to cobblestone):

| Output | Weight | Min mine tier | Min pickaxe | XP |
|---|---|---|---|---|
| Cobblestone | 55 | 1 | wood | 0 |
| Coal | 18 | 1 | wood | 2 |
| Raw copper | 10 | 1 | stone | 2 |
| Raw iron | 9 | 1 | stone | 4 |
| Lapis | 3 | 2 | stone | 4 |
| Redstone | 4 | 2 | iron | 3 |
| Raw gold | 3 | 2 | iron | 6 |
| Diamond | 1.5 | 3 | iron | 10 |
| Emerald | 0.5 | 4 | iron | 12 |

* Cycle: `mineCycleTicks` 140 ticks ÷ pickaxe speed (wood 1.0, stone 1.15, iron 1.3, diamond 1.5, netherite 1.6) ÷ (1 + Diligence).
* Depth: tier 1 = 4 steps down, each further tier +6 steps and a new 5×5 chamber (max tier 4, ~22 blocks deep).
* Upgrade bill: 1 pickaxe (consumed; tier 2 needs stone+, tier 3 iron+, tier 4 diamond+), 6 torches, 6 stone stairs, ~24 sealing blocks. NPCs craft stairs (6 cobblestone → 4) and torches (coal + stick → 4) at the workshop.

## Tree plots (`TreePlotLayout`)
* Tier 1/2/3: 2×2 / 3×3 / 4×4 trees, spaced 6 blocks; fence grows away from the base.
* Upgrade: only with a wood surplus (≥ net fences + 96 plank-equivalents), ≥ 64 logs produced and no project waiting.
* Chopping: `chopTicksPerLog` 24 ticks ÷ (1 + 0.2·axe tier); tending: `tendTicks` 120 ticks per vanilla growth step.
* Leaves: sapling 1/20 (vanilla), apple 1/200, 1/120, 1/80 by tier.

## Recruitment (config `recruitment`)
* `foodValue` 20 hunger points per recruit, paid with stored food at each item's normal nutrition (bread 5, cooked beef 8, raw beef 3, …). The planner finds the smallest overpayment and spends cheaper food first. Golden apples and harmful foods are excluded.
* Equipment: the role's tool/weapon — the finished item if stored (soldiers get the best, civilians the cheapest adequate), otherwise its vanilla crafting materials (e.g. stone pickaxe = 3 cobblestone + 2 sticks; sticks may be paid as planks or logs). Archers also take 1 arrow (or flint + stick + feather; the 3 spare arrows return to storage).
* `intervalTicks` 1200 between recruits per base; `rangedShare` 0.4 archers among soldiers; garrison cap 12; population cap = beds/bedrolls.

## Levels and affixes (`AffixRoller`, `Affix`)
* Level n → n+1 costs 40 + 30n XP; max level 30; one affix roll per level; job affixes weighted ×2.
* Caps: Vitality +2 HP ×5, Swiftness +3% ×5, Might +1 ×5, Toughness +1 ×5, Diligence +6% ×5, Prospector +5% extra ore ×5, Regrowth +8% instant growth ×5, Green Thumb +10% extra crop ×5, Pathfinder +16 range ×4, Mason +10% build speed ×5, Marksman +10% arrow damage ×5.
* XP: mined resource XP above (cobblestone 0); 1 per log/harvest; 1 per repaired block; 25 + blocks/20 per project; scouts: report base value (enemy base 12, lair 12, cave 8, ore 6, hostiles 4, forest 3, water 2) + 1 per 32 blocks from home.

## War (config `war`)
* Scouts: range 160 (+Pathfinder), at least 96 blocks out, 4 sectors per expedition, overdue after 24000 ticks.
* Raids: interval 36000 ticks, party 3, 3 home defenders kept; assault takes 400 ticks at the target heart.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; NPCs regenerate 1 HP per 4 s out of combat (they have no hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.
