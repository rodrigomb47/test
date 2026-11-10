# Roadmap phase 1: notes

One section per item of `docs/ROADMAP.md` phase 1, appended by whoever works on it.

## Woodcutters dig their own dirt for the scaffold

**Why.** A tall tree (top more than 8 above the stump) is felled from a dirt pillar (`tall-trees.md`). Until now the
dirt came only from the storehouse, and bases on stone had almost none: in the last campaign Stoneguard had none and
Tide's storehouse stayed near 0, so they rarely felled tall trees. Now a woodcutter digs its own, as a player would.

**What it does.** When a tall tree within the forest radius waits for its scaffold and the dirt carried plus stored is
short of what it needs, the woodcutter walks to open ground near the tree and digs the top block of a flat patch:
grass, dirt, coarse dirt or podzol (all drop dirt without Silk Touch). It digs by hand, or with a shovel it carries,
at player speed (`BlockWork.Breaker`), the drops going into its backpack. It stops at the scaffold's need
(`TreeShape.scaffoldNeed(top)`, the 2 spare blocks included), then goes back to the tree. Statuses: "Looking for
open ground to dig dirt", "Walking to open ground to dig dirt", "Digging dirt for a scaffold (3/7)". With no patch
anywhere near: "Leaving a tall tree for now (no dirt for a scaffold)", as before.

Build: clean (only the old deprecation warnings). Unit tests: 153 (145 + 8). GameTests: "All 110 required tests
passed" (108 + 2) in 10 full runs: `impl/gt_p1_final1.log` to `gt_p1_final5.log`, then, after the last code change
(the lazy place rules, the shorter dig hold, the forest-radius check), `gt_p1_final6.log` to `gt_p1_final10.log`, all
in the session scratchpad. Earlier runs: `gt_p1_dig1.log`, `gt_p1_errdbg*.log`, `gt_p1_night*.log`, `gt_p1_trace.log`.

### Where it digs, and why it never leaves a trap

The pure rules are in `logic/DirtPatch` (unit-tested), the world side in `work/DirtDig`.

- **Only the top block, only on flat ground.** A cell is dug only when all eight of its neighbours are ground one
  can stand on (a full solid top, two free cells above, no fluid) at its own height. So a dug cell is exactly one
  block below everything around it. It is never beside water, a drop, a step up, a path or farmland (those tops are
  not full blocks), or a trunk.
- **Never a second block deep, without saving anything.** The cells of an earlier patch lie a block below their rims,
  so neither they nor their rims pass the "eight neighbours at the same height" rule. A new patch can never touch an
  old one, whatever happened in between (a reload, another woodcutter). The unit test
  `anOldPatchIsNeverDugDeeperNorWidened` digs a patch and then tries every seed around it both ways.
- **A step out from every cell.** A patch is laid out in rows along x or z, two rows dug and the third left
  standing (`ROW_PERIOD` 3). Every dug cell therefore has a side neighbour that is never dug, one block up: a
  one-block step out, in whatever order the cells are dug. The ridges also keep the patch from being one wide
  hole: it is a set of shallow trenches spread over the 7x7 window round a seed, cells nearest the seed first.
- **Checked again at dig time.** Just before a cell is dug, `DirtPatch.mayDigNow` reads the world again. The cell
  must still be earth at the planned height. Its neighbours must be patch cells (dug or not) or ground at that
  height. One side neighbour outside the patch must still be ground to stand on. This made the second sabotage
  (below) harmless: with the selection rule switched off, the dig-time check still refused every bad cell.
- **Firm floor.** The block under the dug one must be sturdy, with no fluid, leaves, sand or gravel (a falling block
  could drop into a cave under it and make the cell deeper).
- **Open sky, nothing growing near.** Nothing that blocks motion over the cell (no canopy, no overhang). No log,
  leaves or sapling within 2 sideways, from a block below to 4 above (`TREE_CLEARANCE`). Only a plant that gives
  way stands on it (grass, a snow layer), never a flower, sapling or crop.
- **Places kept from digging** (`DirtDig.View.placeAllows`, asked last because it is the costly part):
  - outside every base's walls box + 4 (`WALLS_MARGIN`);
  - within the forest radius and on the map, loaded chunks only;
  - in no plan (the cell, above or below it); not protected wood (`WorkSafety.protectedWood`: other factions' land,
    by a known village, outpost or mansion); where a miner may dig (`WorkSafety.mayDig`: mine stairs, deep dark);
  - `WorkSafety.reserved` has a new use, `DIRT_PIT`: 6 from a fishing spot with water (stand and bobber), 8 from a
    mine origin (its mouth), 3 from a planted sapling or a stump waiting for one, and off the road strip;
  - not within 1 of a grove lattice point of the work belt (the grove's saplings stand there), nor of a scaffold
    column a woodland remembers (its footing).
- **Where to look.** Seeds 5 apart within 24 of the tree, nearest the tree first, 4 seeds a tick. Each seed gets
  the patch of whichever orientation gives more cells. The first seed giving the whole shortfall wins; otherwise
  the largest patch of at least 4 cells (or of all that is still needed). When a patch is used up and dirt is
  still short, another is looked for (3 per tree). No patch: no digging for 2400 ticks (`DIG_RETRY_TICKS`) and that
  tree is skipped for 12000 (`TREE_RETRY_TICKS`), as before. While digging is on hold and no dirt is carried or
  stored, tall trees are not even looked for, exactly as before. So a base on bare stone behaves as it did.

### Choices (told to the owner in the summary)

- **Builders keep the storehouse's dirt.** When the storehouse has some dirt but not enough for the tree, the
  woodcutter digs the whole shortfall and leaves the stored dirt to the builders (they use it for grass and dirt
  repairs). It never digs more than the scaffold needs, so it adds nothing to the storehouse either; the blocks
  stay in its backpack (it keeps up to 32, as before) and serve the next tall tree.
- **The tool.** A shovel it carries (the best one without Silk Touch) is switched into its hand for the digging, as a
  player picks it from the hotbar, and the axe goes into the shovel's slot. With no shovel it digs with what it holds:
  an axe digs earth exactly as fast as a hand and wears nothing. A Silk Touch axe would turn the drops into grass
  blocks, so then it digs bare-handed (the axe goes into a free slot). `Job.usesOtherTool()` (new, false by default)
  tells the shared survival check not to take the axe back while it digs; once it stops, that check puts the axe back
  into its hand on the next tick (also after a reload, which does not resume digging). Shovels are not fetched from
  the storehouse for this (builders need them), and a shovel in the backpack is unloaded as before.
- **Within the forest radius only.** A tall tree found while foraging (72 blocks out) still needs dirt carried or
  stored; foraging searches with `maxTreeDy(false)`, and `startDig` refuses a tree beyond the forest radius, so a
  far tree never puts digging on hold for the trees at home.
- **Not saved.** The dig state loads as CHECK. The woodcutter finds the tree again and looks for a new patch; the
  cells it had dug are excluded by the height rule.
- **It stops** when the tree is gone or another woodcutter took it, at nightfall when the tree is far out, with a full
  backpack (it unloads, keeping the dirt), and after 3600 ticks for one tree (`DIG_GIVE_UP_TICKS`). A tree it had
  started to fell is treated as before: it is gone back to, and with no dirt for the rest it is left standing on its
  stump.

### Files

- `logic/DirtPatch.java` (new, pure): `Column`, `Ground` (with `allowed`, asked last), `flatAround`, `ridge`,
  `patch` (one orientation, or the better of both), `worthDigging`, `mayDigNow`, `seeds`, `key`.
- `work/DirtDig.java` (new): `earth`, `digTool`, `silky`, `View` (heightmaps of loaded chunks, column cache, the
  place rules, cached), `Search` (incremental, over ticks), `mayDigNow`.
- `work/WoodcutterJob.java`: state `DIG` (`startDig`, `dig`, `nextDigCell`, `endDig`, `giveUpDig`, `holdDigTool`,
  `usesOtherTool`); `maxTreeDy(digToo)` looks for tall trees when it may dig; `scaffoldReady` and `startPillar` dig
  when carried + stored is short; `load` turns `DIG` into `CHECK`. Also the night order in `decide` (below).
- `work/Job.java`: `usesOtherTool()`.
- `work/WorkSafety.java`: `Use.DIRT_PIT` in `reserved`.

### Tests

- Unit (`DirtPatchTest`, 8): an open field is dug in pairs of rows with a ridge between them, nearest the seed
  first, never more than needed. Every dug cell keeps a step out one block up, in any digging order. Nothing is dug
  beside water, a drop of two, a step up or ground that is not seen. Stone beside a cell is ground to step out onto.
  A place kept from digging is not dug but is a rim for others, and the costly rule is asked only for cells that
  pass every other rule. An old patch is never dug deeper nor widened. The step out is checked again at dig time
  (a ridge dug by someone else, a cell already dug, a neighbour turned into a hole, an unloaded neighbour). The
  rows run the way that gives more; small patches are left. The seeds lie on a lattice, nearest first.
- GameTest `woodcutterDigsDirtForATallTreeFromOpenGround` (batch `wood_dig`): a Tide woodcutter, no dirt
  anywhere, a 13-log jungle tree, an open grass patch south of the base. Each tick: no dug cell deeper than one,
  each with a step out, the grass under the trunk never dug, nothing ever in the storehouse. At the end: it was in
  DIG, it dug 7 (the need), all 7 are back in its backpack after the pillar came down, no log or scaffold left.
  Trace (`impl/gt_p1_trace.log`): the search took 5 ticks, a row of 7 grass blocks at 17 ticks each by hand,
  digging done at tick 172, the tree replanted at tick 952.
- GameTest `woodcutterDigsDirtOnlyWhereItMay` (batch `wood_dig_safe`): grass in seven places it must not dig, each
  kept for one reason only: inside the walls + 4, on the road strip, round a water source, round a one-block hole,
  round the trunk and under the crown, by a stump waiting for its sapling, and by a mine's mouth. There is one legal
  patch with a farmland and a path block in it. A stone shovel is in its backpack. None of the other ground, the
  farmland or the path is touched. It digs with the shovel (4 ticks a block), its axe is back in its hand
  afterwards, and the shovel wore. The patch it dug was columns x 9, 10, 12, 13 of the legal grass, with x 11 left
  as a ridge.
- `woodcutterWithoutScaffoldBlocksLeavesATallTree` (unchanged, comment updated): the platform is stone, so it finds
  no patch and leaves the tall tree standing as before.
- **Sabotage** (scratchpad `impl/gt_p1_neg1..3.log`, code restored and compared byte for byte afterwards):
  1. `startDig` always refuses: both new tests time out.
  2. `flatAround` without the neighbour rule: `wood_dig_safe` times out. The dig-time check refused the bad cells,
     so nothing unsafe was dug, but no dirt came either.
  3. `placeAllows` always true: `wood_dig_safe` fails "ground it may not dig was dug at 19, 1, 13" (the road strip).
     `wood_dig` fails "dug 0": it dug the flat world off the platform, outside the forest radius.
     `wood_tall_none` fails: it dug the grass beside the trunks and built a scaffold.

### The flaky `woodcutterComesDownItsScaffoldForAnErrand`: explained and fixed

It failed in a run here ("it should have come down before the tree was done"). A per-tick trace of the woodcutter
(`impl/gt_p1_errdbg1.log`) showed the cause.

- The test started at night: day time 13444. GameTest worlds keep the daylight cycle, so where the night falls in a
  suite run depends on the batch order.
- The woodcutter's first search pattern missed the tree (each scan reads every second column). At night `decide()`
  then went home before finishing the search. At the storehouse it packed 2 bread, as every NPC does there, and
  waited 600 ticks for daylight.
- Made hungry on its pillar, it ate its own bread up there, which is correct and player-like. So it never came down,
  and the test's assumption failed.

Two fixes:

1. **The test** now puts any food the woodcutter packed back into the storehouse when it makes it hungry. That is
   what its comment always assumed ("with nothing to eat in its backpack").
2. **`WoodcutterJob.decide`**, a small behaviour change: the tree search now reads every column (4 patterns, 20 ticks
   apart) before it goes home for the night, as it already did before walking out to forage. A tree near the walls
   is felled at night too (only remote work waits for daylight). Before, one empty pattern sent it home until
   morning with a tree standing by the walls. The same night-dependence also made `woodcutterLooksAgainAtTreesItLeftStanding`
   time out when forced to start at night.

Proof: probe runs forced day time 13400 at the start of the errand, dig and recheck tests (`impl/gt_p1_night2.log`,
`gt_p1_night3.log`; `gt_p1_night.log` is from before the `decide` fix, with the recheck test timing out). All 110
passed. The probe lines were removed, and the 10 natural full runs above passed after that.

### For the campaign check (not done here: no dedicated server in this role)

1. A base on stone (Tide, Stoneguard; seed 20261107 `fc start 256`, or a hand-placed base) with tall trees (place
   some with `/place feature minecraft:mega_spruce`), no dirt in the storehouse. Watch for "Digging dirt for a
   scaffold (n/m)", then the pillar.
2. `/fc admin blocks` round each patch: trenches one block deep in pairs of rows, ridges between, no cell beside
   water, a drop, a trunk, a path or the walls, nothing on the road strip.
3. Watch the stuck-recovery counts of all NPCs near patches (they must not rise), and that the builders' dirt in
   store is not touched by woodcutters while they dig.
4. Stoneguard-like ground with no grass at all: "Leaving a tall tree for now (no dirt for a scaffold)" at most every
   2400 ticks, no tick-time cost (the search is 4 seeds a tick, a few ticks long).
5. The night order: woodcutters near the walls keep felling at night; none goes far out at night.

### Limits

- **Bare stone around a base gives nothing.** Stone needs a pickaxe, and the scaffold stays earth-only (see
  `tall-trees.md`), so a base with no grass, dirt or podzol within the forest radius still leaves its tall trees.
- **Trenches stay.** They are one block deep and walkable, and grass grows back over them, but the woodcutter does
  not fill them in. A patch is never dug a second time, so in a long campaign the woodcutter moves on to fresh ground.
  It needs dirt only rarely, because the blocks come back from every pillar and it keeps 32.
- **No line-of-sight check** for the dig reach (4.5), as for every other job's block breaking.
