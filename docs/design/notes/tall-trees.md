# Tall trees: felled from a scaffold pillar

The owner's goal from the worker redesign: woodcutters "can cut and replant any tree as they like". Until now a tree
whose top log was more than 8 above the stump was left standing (reach from the ground: eyes at feet + 1.62,
TRUNK_REACH 7.5 to the block centre). That covered mega spruces, giant (2x2) and tall single jungle trees, some fancy
oaks and super birches, so bases in mega taiga or jungle got their wood mostly from the grove.

Now a woodcutter with dirt fells such a tree the way a player "nerd-poles". It stands beside the trunk and jumps,
placing a block under its feet each time, until the top is in reach. It fells the tree top down and clears the canopy
within reach. Then it breaks the pillar block under its feet one at a time on the way down, and the blocks go back
into its backpack. Without dirt, tall trees are not even looked for, as before.

Build: clean (only the old deprecation warnings). Unit tests: 144 (139 + 5). GameTests: "All 100 required tests
passed" (94 + 6). Logs are in the session scratchpad under `impl/` (`gt_tall*.log`). Not checked in a campaign yet:
see "Campaign check" below.

## What changed, by file

### logic/TreeShape.java (pure)
- **Flood bounds:** MAX_RISE 24 → 32 and MAX_LOGS 96 → 192. A giant jungle tree is up to 31 tall with about 150 logs;
  a mega spruce is up to 29 with about 115. A 3x3 dark-oak farm is still TOO_BIG (about 270 logs). MAX_SPREAD stays 7:
  a giant jungle tree's branch ends lie from -2 to +5 of its north-west corner.
- **Height limit:** `survey(cells, x, y, z, resume, maxDy)`. TOO_TALL now means a log above `maxDy`: MAX_FELL_DY (8)
  from the ground, MAX_TALL_DY (32) for a woodcutter with dirt. The 5-argument `survey` keeps the ground limit. The
  contact, natural-leaves, soil and same-log rules apply to tall trees too.
- **`fromGround(dx, dy, dz)`:** dy ≤ 8 and (fromStumpColumn or fromBelow). This is what today's ground flow can cut.
  Every tree that passed before passes this rule for all its logs, so the ground flow is unchanged for them.
- **`Pillar(x, z, height)`, `Room` and `pillarSpot(logs, sx, sy, sz, big, room, groundToo)`:**
  - The candidates are the columns beside the trunk (`besideTrunk`): 8 around a 1x1 trunk, 12 around a 2x2 one.
  - `room.at(x, z)` is the highest pillar the world allows in that column (negative: none).
  - A column qualifies when every log is reachable from it at some integer height between 0 and the pillar's height.
    `standRange` gives that window: eyes to the log's centre ≤ PILLAR_REACH, which is REACH − 0.25 = 7.25, because the
    woodcutter may stand up to 0.25 off the column's middle. Logs that `fromGround` reaches are exempt when
    `groundToo` is set; they are cut afterwards from the ground, as before.
  - The pillar height is the highest "lowest stand" among the logs that need it. Ties go first to the lowest pillar,
    then to the column that reaches the most logs itself, then to a column straight beside the trunk.
  - The result is null when no column reaches every log (wide branches on all sides, no room): such a tree is left
    whole. This is the "survey rejects a tree whose logs cannot all be reached from one pillar" option. A second pillar
    is not needed for vanilla trees: the south-east corner column of a giant jungle tree reaches every possible branch
    end.
- **`scaffoldNeed(topDy)`:** the blocks to carry before walking to a tree with that top. It is the worst column's
  height (a diagonal corner, 3 across from the far trunk column) plus PILLAR_SPARE 2; it is 0 for ground trees.
- **Constants (PROPOSED):** MAX_TALL_DY 32, MAX_PILLAR 28, PILLAR_SPARE 2, PILLAR_REACH 7.25.

### logic/ForestRules.java (pure)
- `Species.growsHuge()` is true for spruce and jungle. `saplingsFor(species, big)` is 4 for dark oak and for a big
  spruce or jungle, and 1 otherwise.
- `replant()`:
  - A 2x2 spruce or jungle stump gets **4 saplings in a square** when 4 are carried, so the mega tree grows back.
  - With 1 to 3 carried it waits HUGE_PATIENCE (2400 ticks, PROPOSED) before planting 1 at the north-west corner.
    Meanwhile the drops from its canopy are collected, and the storehouse is checked for the rest.
  - With none carried, the old timeline applies: a substitute after 12000 ticks, forgotten after 48000.
  - Dark oak and single trunks are unchanged.
  - WAIT now reports 4 for such stumps, so the storehouse trip fetches 4.
- The grove is unchanged: GROVE_ORDER is the same and grove spots take one sapling each, so no mega tree grows there.

### work/Scaffold.java (new)
- **Items:** dirt and coarse dirt only (`isItem`). `isBlock` also accepts a grass block, for dirt that grass spread
  onto. Stone, cobblestone, cobbled deepslate and netherrack need a pickaxe to drop anything. A woodcutter has no
  pickaxe, so those blocks would take about 200 ticks each to break by hand and would be lost. That is why the
  builders' tool-stone reserve (`BuilderJob.TOOL_STONE_RESERVE`) is never touched; it was one of the options the
  task listed.
- **CARRY 32:** blocks fetched, and kept in the backpack when unloading (`WoodcutterJob.keep`).
- **`place(npc, at)`:** a vanilla-style placement, dirt first. It only goes into air or a block that gives way, never
  into fluid or where another living entity stands (`BlockWork.obstructed`). It plays the placement sound and swings.
- **`holdStill`:** stops navigation, sets the move control to the column's middle at speed 0, zeroes sideways motion,
  and re-centres the woodcutter when it is more than 0.05 off. It stands for a player holding still on a pillar.
- **`Pillar`:** the base cell (the stump's level), goal, height (the blocks standing under its feet), phase
  (WALK, CLIMB, WORK, DOWN), `down` (why it comes down early, or null) and `strays`. It is saved and loaded as a
  compound. Unknown phases load as WORK.

### work/ForestScan.java
- `findTree(..., maxDy)`: the column rule accepts trunks up to `maxDy`. MAX_TRUNK_WALK is MAX_TALL_DY + 2, and
  `Found` now carries `topDy`.
- `survey(..., maxDy)` is a new overload.
- `pillarSpot(level, data, own, stump, big, logs, groundToo)` and `pillarRoom(...)` are the world side of the pure
  choice. The column needs:
  - firm ground under it at the stump's level (no leaves, log or fluid);
  - wood there that is not protected (`WorkSafety.protectedWood`: no pillar by the walls, in foreign land, by a known
    village, outpost or mansion, or off the map);
  - every cell up to the height + 2 clear: air or a plant that gives way, natural leaves, or one of the tree's own
    logs, which are cut on the way up;
  - no block entity, and no cell in any base's plan or unloaded.

### work/WoodcutterJob.java
- **New state SCAFFOLD** (the pillar engine) and the fields `pillar`, `canopy`, `pillars`, `strays`, plus the scaffold
  fetch bookkeeping.
- **Finding trees:** `maxTreeDy()` returns 32 while it carries at least `scaffoldNeed(9)` (4) blocks, or while the
  storehouse holds that many and a trip is allowed; otherwise it returns 8. It is used by the search, the forage
  search, the survey and the badWild recheck (`fellable()` also needs a pillar spot). Without dirt, tall trees are not
  found, exactly as before.
- **Before walking (`scaffoldReady`):** a tall tree needs `scaffoldNeed(topDy)` blocks carried. If it has fewer, it
  makes a storehouse trip (`fetchScaffold`, at most one every SAPLING_TRIP_TICKS, 2400), and `storage()` tops the
  dirt up to 32, then coarse dirt. With too few in store, the tree is skipped for TREE_RETRY_TICKS (12000), not given
  up for good.
- **Survey on arrival:** `startPillar()` runs when any log fails `fromGround`. It takes the plan from
  `ForestScan.pillarSpot`.
  - No plan for a new tree: "Leaving a tree standing (no room for a scaffold)", sent to badWild and rechecked from
    afar as before.
  - Fewer than height + 2 blocks: fetch them, or skip the tree for a while.
  - A started tree (resume) is never left with floating logs: it fetches blocks regardless of the trip limit, or
    goes on from the ground as far as it can.
  - Deferred logs are merged into the pillar's list.
- **Engine:**
  - WALK: cuts natural leaves or the tree's own logs filling the column's two lowest cells. It walks to the foot
    (reach 0.7), then stands right over the middle (a sub-block adjustment).
  - CLIMB: on the ground it clears the cell over its head (leaves, or a log of the tree), then calls
    `getJumpControl().jump()`. In the air, once its feet are above the cell it left, `Scaffold.place` puts a block
    there, and it lands on the block. Vanilla's jump delay makes this about 9 ticks a block. The climb ends at the
    goal height, after 6 jumps without a block (MAX_MISSES), or with no blocks left. Something unexpected overhead
    also ends the climb there.
  - WORK: it cuts the highest log of the tree within TRUNK_REACH, then the highest natural leaf of the crown within
    LEAF_REACH 5.5. The crown is read once, from the top: natural leaves within 5 (6 for a 2x2) of the stump. Leaves
    that drop above its reach are pruned as it descends. The saplings go straight into the backpack, which is what
    makes the 2x2 replant work. With nothing in reach it moves to DOWN. On the ground the pillar is done, and the rest
    goes through the ordinary CHOP flow (the stump, low branch ends, the final pass).
  - DOWN: it breaks the block underfoot with the breaker at player speed (dirt by hand takes 15 ticks; no tool wear),
    and the dirt goes into the backpack. It drops one block and lands, then goes back to WORK. If the block below has
    gone missing, a block from the backpack is put back first, so no drop is ever more than one block.
  - Statuses: "Building a scaffold up the spruce (12 high)", "Felling the spruce from its scaffold (40 logs left)",
    "Clearing the canopy from its scaffold (35 leaves)", "Taking the scaffold down (6 high[, why])".
- **Interruptions** (`interruption()`, checked every tick while it works). Each one makes it come down without
  cutting anything more; the tree stays `felling` and gets a fresh survey and pillar later:
  - hurt (`hurtTime`): "under attack";
  - 1 free backpack slot or fewer: "backpack full", followed by a storehouse trip;
  - `remoteAtNight(spot)`: "nightfall";
  - the claim lost to another woodcutter: "the tree is taken", which drops the tree;
  - a survival errand: `beforeErrand()` brings it down first ("hungry");
  - no base or woodland any more.
  A pillar not yet climbed is simply dropped. Climbs cut short count toward MAX_PILLARS_PER_TREE (3); after that the
  rest is cut from the ground as far as it goes.
- **Safety while up:**
  - `onScaffold()` is true while the pillar stands under it, or while it jumps from the ground.
  - `ignoresCombat()` and `handlesOwnSafety()` follow it. So `WorkGoal` drops targets, melee and hurt-by-target goals
    stay off, and the civilian flight stays off.
  - `CreeperEvadeGoal` stays off, and `FactionNpc.knockback` does nothing while it is up (like a player crouched at
    the edge). These two make sure "nothing moves it off the pillar"; I chose that over letting arrows knock it into a
    20-block fall.
- **Off its pillar anyway** (an explosion's push, an admin teleport): `scaffold()` sees it more than 0.8 from the
  column's middle and records the blocks still standing as `strays`. `decide()` then takes them down from beside
  (`startStrayCleanup`): a plan with `groundToo` false, so it works from the ground when the highest block is within
  reach there, or from a pillar of its own beside them. It gets 3 tries (MAX_STRAY_TRIES).
- **Reload:** the pillar, `pillars` and `strays` are saved. `logs`, `deferred` and `species` are saved whenever a
  pillar exists, not only once a log has been cut. A loaded job with a pillar resumes in SCAFFOLD exactly where it
  was. A pillar loaded with no tree (never written so) only comes down. Old saves have no pillar key and load as
  before.
- Replant reservations and the storehouse fetch use `saplingsFor`, so a big spruce or jungle stump reserves and
  fetches 4.

### npc/DigOut.java: other NPCs never stranded
- `perched()` is now `perchedOnTree` (the old rule) or **`onPillar`**. `onPillar` means: standing on something solid;
  no side to step off with ground at most 3 down (leaves do not count as ground); and a longer drop on two sides or
  more. A walled side, such as the trunk, counts as neither. Plain ground, a ledge, a staircase, a bridge, a pit floor
  and a one-block hole are not pillars. On ordinary ground the test returns after one block read.
- Effects:
  - `free()` is false up there, so `shutIn` digs.
  - `step()` breaks the block underfoot, one every 20 ticks, until a drop of 3 or less is left; it then walks off.
  - Recovery teleports never land on a pillar top (`isSafeStandingSpot`).
- Exception: an NPC that is building itself up beside a wall toward a destination above (DigOut's own `pillar()`
  move) keeps building. The dig-down branch skips `onPillar` when the target is more than a block above. Without
  this, the stuck-pits climb-outs would undo themselves.
- This covers a woodcutter whose job changed under it and any other job's NPC that ends up on a pillar.

### Other
- `Job.onScaffold()` defaults to false. `CreeperEvadeGoal` and `FactionNpc.knockback` check it.

## Tests

### Unit tests (5 new, 1 rewritten)
- TreeShapeTest:
  - `tallTreesAreFelledOnlyWithinTheScaffoldBounds`: TOO_TALL from the ground, OK with a scaffold, 124-log giant OK,
    a 34-tall trunk TOO_BIG, the leaf and contact rules still apply.
  - `aPillarBesideTheTrunkReachesEveryLog`: every log is reached, one block less is not, a single jungle trunk gets a
    pillar of 4 straight beside it, no room or too little room gives null.
  - `wideBranchesMustAllBeReachedFromOnePillar`: giant-jungle branch ends; the north-west corner alone gives null.
  - `lowBranchEndsAreLeftToTheGroundUnlessEverythingMustBeReached`: the `groundToo` switch, and stray blocks 12 high
    give a pillar of 3.
  - `scaffoldBlocksCarriedCoverThePillar`: `scaffoldNeed` covers every 2x2 height up to 33, plus `standRange`.
- ForestRulesTest: `bigSpruceIsReplantedAsOneSapling` is replaced by `bigSpruceAndJungleAreReplantedAsASquareOfFour`
  (`saplingsFor`, `growsHuge`, the HUGE_PATIENCE timeline, the grove free of mega species).

### GameTests (6 new; own batches, skyAccess, `GameTestBases.register` with clean-up)
- `woodcutterFellsAMegaSpruceFromAScaffold` (wood_tall_spruce):
  - The tree is a 2x2 spruce 19 logs tall with natural leaves (distance 1, not persistent) round its top.
  - The woodcutter carries 4 spruce saplings and no dirt; the storehouse has 32 dirt.
  - It fetches the dirt and builds a 10-block pillar.
  - At the end: no spruce log and no scaffold block is left, all 32 dirt is back, it stands on the ground unhurt (it
    is never hurt at any point), the 4 stump cells hold spruce saplings, and one tree is counted as felled.
  - The trace showed: fetch at tick 1, climb at ticks 125-216, 60 logs cut at height 10, about 60 leaves, then down
    while cutting the rest, and the 2x2 replant at tick 1727.
- `woodcutterFellsATallJungleTreeFromAScaffold` (wood_tall_jungle): a single trunk 13 logs tall, 8 dirt carried, a
  pillar of 4, one jungle sapling replanted, nothing left behind.
- `woodcutterResumesItsScaffoldAfterAReload` (wood_tall_reload): the whole NPC is saved and loaded (`saveWithoutId` /
  `load`) twice, once mid-climb at 3 high and once working from the top. The loaded job must be a new object in
  SCAFFOLD with an identical pillar compound. It finishes cleanly.
- `woodcutterComesDownItsScaffoldForAnErrand` (wood_tall_errand): made hungry while working from the top, with bread
  in the storehouse. It must come down with the tree unfinished and leave no scaffold standing while away, fetch
  food, then come back and finish.
- `woodcutterWithoutScaffoldBlocksLeavesATallTree` (wood_tall_none): no dirt anywhere. It fells the birch beside the
  tree; the 13-log jungle tree stands whole for 400 more ticks, nothing is built, and it never has a pillar.
- `npcOnABarePillarDigsItsWayDown` (pillar_down): a farmer on a 6-high dirt pillar is perched, not free, and its spot
  is not safe for a recovery. `DigOut.step` breaks its way down to 3 or less, the dirt goes into its backpack, and it
  takes no damage. Standing beside the pillar or in a one-block hole is not perched.

### Sabotage
- `gt_tall_neg.log`: `maxTreeDy()` always returns 8. The spruce, jungle, reload and errand tests all time out.
- `gt_tall_neg2.log`: four sabotages at once. Each test fails with its own message:
  - the pillar not read on load: the reload test fails ("the loaded job should be on its pillar: CHECK");
  - `beforeErrand` not coming down: the errand test times out;
  - `perched` without `onPillar`: the pillar test fails ("should count as stranded");
  - a big spruce replanted as one sapling: the spruce test times out.
- The code was restored afterwards (checked with grep).

### Flakes seen while repeating the suite (about 25 full runs)
- **woodcutterFellsAMegaSpruceFromAScaffold, fixed:** it failed once with felled = 2. The 76 logs give the woodcutter
  a level, and the level-up rolls a random affix (`ThreadLocalRandom`). Regrowth (8% a replant) then grew the fresh
  2x2 square into a new mega spruce at once, which it felled again. This is a real feature, not a bug. A probe with
  Regrowth at 5 stacks reproduced "felled 2" on the third run. The test now puts the woodcutter at the level cap
  (`noLevelUps`), so no affix is rolled mid-test.
- **minerClearsAFloodedTunnelCell, pre-existing:** it failed 2 times in about 24 runs with my changes ("the flooded
  cell should be open, is water[level=0]") and 0 times in 9 baseline runs. That gap is not statistically
  meaningful, and earlier session logs (`gt_fix_mining_dbg/neg*.log`) show the test failing before. I traced it: the
  miner stays in its tunnel and never teleports, and `onPillar` cannot apply in that 3-high stone block (water counts
  as a wall, and the drop to the platform is 3). The only other changes it could meet are `knockback` and
  `CreeperEvadeGoal`, which only act while a woodcutter is on a scaffold. So I left it alone. It looks like a
  fluid-timing race between the test's water and the miner.

## Limitations and choices told to the owner
- **Earth only for the scaffold.** Explained above: a woodcutter cannot get stone back without a pickaxe.
- **Knockback and creeper dodges are off while on the pillar.** A player could be knocked off; the owner's rule "no
  fall damage" won.
- **A woodcutter killed on its pillar leaves the pillar and the half-felled tree.** A dead woodcutter already left its
  half-felled tree before this change. The job's state dies with it, and no replacement woodcutter knows the pillar.
  A woodland-level record could be added if it shows up in campaigns.
- **Leaves above the pillar's reach** at the very top of the crown decay naturally. Their drops land on the ground and
  are collected if the woodcutter is still about.
- **One pillar per tree** (re-planned up to 3 times when a climb is cut short). No vanilla tree needs two, but a
  modded or player-built shape might be refused.

## Campaign check (for the next agent, on a dedicated server)
1. Find a base in old growth taiga or jungle: seed 20261107 with `fc start 256`, or `/locate biome` and a hand-placed
   base.
2. Watch the woodcutter statuses: "Fetching dirt for a scaffold", "Building a scaffold up the spruce (N high)",
   "Taking the scaffold down".
3. `/fc admin blocks` around a felled mega tree: no dirt column, no floating logs, and four spruce saplings or one at
   the corner.
4. `sim.py summary`: felled counts go up, no woodcutter deaths, and recoveries do not climb for woodcutters.
5. Check that miners' dirt stock covers the 32 a woodcutter keeps.
6. Check a reload in the middle of a pillar.

## Review (adversarial pass over 8f5c028)

Every BUILD point was checked against the code paths. Three real defects were found (one confirmed by a GameTest
that fails without the fix), plus four smaller ones. Fixed in the "Review fixes" commit.

### Defects fixed
1. **Logs left hanging after a climb cut short.** From the pillar it cut "the highest log within reach". A climb
   that stops short of the planned height has that problem: a block over its head that was not there at the survey,
   a mob in the cell it places into (6 missed jumps), or no blocks left. The woodcutter then cut the middle of the
   trunk (everything up to about 7.5 above its eyes) and left the top hanging. The next pillar was planned only from
   a fresh survey of the stump, which no longer reaches those logs, so they could stay up for good.
   - Now `nextLog` fells strictly from the top. While a log the ground cannot reach (above 8) stands out of reach at
     the same height or higher, nothing below it is cut.
   - With a complete climb this never blocks: each log's reach window is centred on its own height, so a higher
     log's window never lies wholly below a lower one's.
   - Logs the ground reaches never block; they are cut afterwards from the ground, as before.
   - Proof: `woodcutterLeavesNoLogHangingWhenItsClimbIsCutShort`. A stone appears over the woodcutter's head 2
     blocks up its first pillar. Every tick, the test checks that each log of the trunk stands on a log.
2. **A started tree was finished "from the ground as far as it can".** This happened when it still needed a
   scaffold but got none (no column, no dirt, or 3 climbs cut short). It meant cutting the foot of the trunk and
   leaving everything above 8 hanging. Such a tree is now left standing as it is (`leaveTree`): its top is already
   felled (point 1), and the rest stands on its stump. An untouched tree that fails 3 climbs is also left; it is
   rechecked from afar as before, and felled again once fellable.
3. **A woodcutter that died (or was retrained) on its pillar left the pillar for good.** The job state died with it,
   and nobody else knew the column.
   - The woodland now remembers it: `WorkplaceState.scaffolds`, at most 16 columns, saved as `scaffolds` in the
     woodland block. Old saves have none.
   - The column is recorded when the first block is placed, and forgotten when the pillar comes down empty
     (`endPillar`).
   - Every 200 ticks, while it has no pillar, a woodcutter sweeps the remembered columns (`sweepScaffolds`). Blocks
     still standing become `strays` and are taken down from beside (`startStrayCleanup`). An empty column is
     forgotten. Strays it gives up on (no room beside them, cannot get there, 3 tries) forget their columns, so
     there is no endless retry.
   - The scan reads up the column and stops at two cells in a row without a scaffold block, so natural dirt above a
     pillar column (an overhang) is never taken for scaffold.
   - A stray block that someone stands on (an NPC retrained up there, a mob) is not broken from under them.
   - This also covers the up to 3 blocks that a stranded NPC of any job leaves after `DigOut` breaks its way down.
   - Proof: `nextWoodcutterTakesDownAPillarLeftByADeadOne`. The first woodcutter is killed 5 or more blocks up.
     Another woodcutter takes over the woodland, takes the pillar down, and the woodland forgets the column.
4. **Unbreakable block underfoot when stepping down** (a block entity or bedrock-hard block someone put there). The
   pillar was dropped without noting the dirt below it. It is now recorded as strays (`strayPillar`).
5. **`pillars` (climbs cut short on this tree) was only saved while a pillar stood.** A reload between two pillars
   reset the per-tree cap. It is now saved while a tree is started.
6. **`keep()`** kept 32 dirt and 32 coarse dirt (64 in all). It now keeps 32 of the two together.
7. **The stray clean-up pillar** asked for just its height in blocks. It now asks for height + PILLAR_SPARE, like a
   felling pillar, so a block missing below can be put back.

### Checked and found sound (no change)
- **Interruptions.** Hurt, 1 free slot, nightfall far out, the claim lost, a meal errand (`beforeErrand` drives the
  descent from `survival()`) and no woodland all set `down`. From then on nothing is cut, and it steps down block by
  block. A pillar not climbed yet is dropped.
- **Reload and NBT.** The pillar compound round-trips (the GameTest compares the saved compounds). `logs`,
  `deferred` and `species` are saved while a pillar stands. Old saves load with no pillar. Unknown phases load as
  WORK.
- **Movement.** In CLIMB, WORK and DOWN the job never calls `goTo`, so neither `NpcNavigator`'s watchdog nor
  `DigOut` runs. `WorkGoal` cannot be pre-empted on the pillar: flight, melee, creeper dodge and hurt-by targeting
  are all off, and the ranged goal is for archer roles only. Recovery teleports never land on a pillar top (`perched` →
  `onPillar`). `DigOut`'s own pillaring toward a higher target is left alone.
- **Protected places.** `pillarRoom` checks `protectedWood` for the column and `planned` for every cell. A missing
  block is only ever put back into its own column.
- **Scaffold supply.** Earth only, so the builders' tool-stone reserve is never touched. The fetch is at most every
  2400 ticks, except for a started tree.
- **Per-tick cost.** `nextLog` stops at the first log that blocks or at the first lower height. `interruption()`
  is a few lookups. The canopy is read once per pillar. The sweep runs at most every 200 ticks, only without a
  pillar, and reads at most about 30 blocks per column. Recovery-spot checks read at most about 25 blocks more per
  candidate.
- **2x2 replant.** Four saplings in a square with 4 carried, else one at the north-west corner after
  HUGE_PATIENCE, else the old timeline. The storehouse trip fetches 4. The grove plants one sapling per spot and its
  order is unchanged.
- **The implementer's tests are meaningful.** Their sabotage logs (`gt_tall_neg*.log`) show each one failing.

### Limits left as they are (told to the owner)
- **An explosion or an enderman can still take the block under its feet.** A creeper that blows up the foot of the
  pillar, or an enderman that takes the block it stands on, can drop it more than 3. Knockback and creeper dodges
  are off up there (a dodge would be a fall too). The blocks left standing are taken down afterwards (strays).
- **The half-felled tree of a dead woodcutter** stands on its stump (no floating logs). The heir only fells it
  again if it still passes the survey as a tree (natural leaves near its top).
- **Two far-out branch tips** whose reach windows are each under a block wide could, in theory, block each other
  (rounding). It would re-plan, and after 3 climbs leave the tree standing. No vanilla tree was seen to do this.
- **Builders' dirt.** Builders use dirt for grass and dirt repairs. A woodcutter keeps up to 32 once it has
  fetched them.

### Tests
- 2 new GameTests (own batches `wood_tall_short` and `wood_tall_heir`, skyAccess, `GameTestBases.register`). The
  stone obstacle is removed by the short-climb test itself.
- Sabotage run (`impl/gt_review_neg.log`, scratchpad): the old `nextLog` rule plus the sweep switched off. Both new
  tests fail: "a log was left hanging over a cut trunk", and the heir test times out. The code was restored
  afterwards (checked with grep).
- Counts: 144 unit tests, 102 GameTests (`impl/gt_review1.log` and three final runs, `impl/gt_review_final*.log`).
- No dedicated-server campaign was run in this review (not allowed in this step). The campaign check above still
  stands.
