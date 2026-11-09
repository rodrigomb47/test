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
* Bridging: where the step toward the destination has no floor, an NPC places a block of stone, cobblestone,
  deepslate, dirt, andesite, diorite, granite or tuff from its backpack (else any full solid block it carries:
  logs, planks...) and steps onto it (rising one block per step when the destination is above); never inside
  protected ground.
* Climbing, in order: a dug step up toward the destination, a level step, a bridge step in any other direction, a
  pillar (a block placed underfoot, the cell two above must be clear), and last a step back down. Stepping away
  from the destination or back down marks the spot left as a dead end it will not return to; it also never steps
  back onto one of the last 6 spots it left. Both are forgotten on arrival or 12000 ticks after the last step.
* In a mine stairwell (the 2x2 spiral column below the hut floor) it climbs the spiral one step at a time and puts
  back a missing stair floor with a block from its backpack. Miners do not follow ore veins into the stairs or the
  rock ring around them.
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
* A plan cell not done after 1200 ticks near it (2400 while still more than 10 blocks away; vein mining excluded)
  is given up: a cut main tunnel ends the level, a cut branch moves on to the next branch.
* Retraining: a miner with no mine or cave to work for 12000 ticks (half a day) takes up the base's most needed
  role instead of a new recruit.
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
* An overgrown plot: when walking gets no closer for 100 ticks inside the plot (or 200 ticks of trying), the
  woodcutter cuts through the leaves toward its tree; stuck outside the fence for 300 ticks, it goes round to the
  opening and steps in; a tree it cannot reach in 600 ticks is left alone for 6000 ticks.
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
  butchers): retried every 1200 ticks; after 12000 ticks without one, a civilian is retrained.
* Blacksmith replacements, one per tool kind per plan: iron if there is enough, else stone, else wooden. A miner
  with a wooden pickaxe gets a stone one as soon as there are 3 cobblestone.
* Anvil (vanilla): each material unit repairs a quarter; cost = units + prior work; prior work becomes
  2 × prior + 1; "Too Expensive" from 40 levels. Repaired only when worth it (PROPOSED): enchanted, or iron,
  diamond, netherite or chainmail gear needing fewer units than its recipe (pickaxe/axe 3, sword/hoe 2, shovel 1,
  helmet 5, chestplate 8, leggings 7, boots 4). Levels paid from the faction's experience (the enchanter keeps at
  most level 30 of it). 12% chance per use to damage the anvil; a broken anvil is re-forged from 31 iron.

## Wood and fuel
* Woodcutters with nothing to fell near the plot walk 72 blocks out (a different direction each time) and look
  for wild trees within 40 blocks there; after 8 tries in vain they rest for 6000 ticks.
* Smelters make charcoal from logs when the storehouse has fewer than 8 coal/charcoal and more logs than the wood
  reserve (at least 24).
* Wood reserve (PROPOSED): 64 logs while one of the base's tree plots is below tier 3 (its enlargement starts at
  about 56 logs), else 16; 16 whenever the base is rationing food.

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
* Caps: Vitality +2 HP ×5, Swiftness +3% ×5, Might +1 ×5, Toughness +1 ×5, Diligence +6% dig/build speed ×5, Prospector +5% chance an ore drops twice ×5, Regrowth +8% instant growth ×5, Green Thumb +10% chance a crop drops twice ×5, Pathfinder +16 range ×4, Mason +10% build speed ×5, Marksman +10% arrow damage ×5, Thrifty 8% chance a wear event is skipped ×4, Hardy −8% exhaustion ×4, Duelist +10% critical damage (×1.5 → ×2.0 at 5) ×5 (swordsmen, axe fighters), Angler 8% chance the fishing loot is rolled twice ×5 (fishermen), Haggler −5% emerald prices ×4 (traders).
* XP: ore XP above (stone 0); 1 per log/harvest/breeding feed; 2 per butchered animal; 20 per new mine level; 1 per repaired block; 25 + blocks/20 per project; scouts: report base value (enemy base 12, lair 12, woodland mansion 12, pillager outpost 10, cave 8, village 8, ore 6, hostiles 4) + 1 per 32 blocks from home. Trees and water are not reported.

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
  distance (12; creepers 16; skeletons, pillagers, witches 18) when seen or hunting the scout (+4). The scout runs at
  1.3x to a point 16/20/12 blocks out in one of 16 directions, clear of every monster (2 beyond its keep-away
  distance), passing none closer than 4, scored by clearance (up to 4 over the keep-away) + 8 x the target side + 1 x
  away from the group - 6 x reversing the last escape; firm dry ground in a loaded chunk, 8 inside the map edge, with
  a walking route (4 path searches at most). A monster within 6 counts only on about the scout's level (3 up or
  down) or in sight. A run lasts at most 160 ticks. It retreats home when under 40% health with monsters about,
  after 3 monster hits in one encounter, after 4 escapes in a row that did not shake them, or after 10 escapes in one
  encounter; cornered with a monster within 3.5 blocks it fights back for 100 ticks while over half health. An
  encounter ends after 100 quiet ticks. Enemy soldiers: retreat home as before (2 threats, or 1 under 60% health).
* Raids: interval 36000 ticks, party at least 3 (see the war council above), 3 home defenders kept; assault takes 400 ticks at the target heart.
* Hollow lairs: start with 5 monsters, +1 per day up to 12; raid every 30000 ticks on the nearest settlement within 300 blocks.
* Damage: zombies, skeletons and variants deal ×0.5 to NPCs; NPCs regenerate 1 HP per 4 s out of combat (they have no hunger).
* Victory rule (proposed): a base falls when its **Faction Heart** is destroyed; a faction with no standing bases is eliminated; the last faction standing (The Hollow included) wins. The reporting station is not the defeat core.
