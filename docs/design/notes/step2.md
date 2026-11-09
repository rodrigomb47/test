# Step 2: zones and safety on the existing jobs (commit 98fb99f, on top of 535626b)

Build is clean (only the old deprecation warnings).
- Unit tests: 53 passed, 0 failed (40 before + ZonesTest 3 + TreeShapeTest 10).
- GameTests: "All 51 required tests passed" (49 before + minerNeverDigsProtectedBlocks + treeSurveyLeavesBuiltAndProtectedWood).
  Log: impl/gt_2.log

extend() is NOT changed (as the step says). Nothing calls reserved(), nearLair(), workRadius() or forageAllowed() yet.

## New classes and their public API

### logic/Zones.java (pure, ZonesTest)
- `chebyshev(ax, az, bx, bz)`, `insideHalf(dx, dz, half, margin)` (Chebyshev <= half + margin)
- `half(testHalf, layoutHalf)` (testHalf >= 0 wins), `workRadius(testRadius, configured)` (testRadius > 0 wins)
- `mapInside(cx, cz, radius, x, z, margin)`: radius <= 0 means always inside; else |x-cx|, |z-cz| <= radius - margin
- `mapClamp(cx, cz, radius, x, z, margin)` -> int[]{x, z}; each axis clamped on its own; radius 0 returns the point;
  a margin wider than the map clamps to the centre.

### campaign/BaseZones.java
- `half(BaseState)`: testHalf, else BaseLayout.HALF (24) for settlements and LairLayout.HALF (7) for lairs.
- `chebyshev(b, p)`, `insideWalls(b, p, margin)` (any y).
- `outsideAllWalls(d, p, margin)`: every non-destroyed base, own included.
- `inForeignArea(d, ownBaseId, p, margin)`: non-destroyed bases with id != own, `contains(p, margin)`; lairs
  `contains(p, margin + 8)`. NOTE: contains() has a y window (MIN.y-margin-32 .. MAX.y+margin+32), so deep tunnels
  under another base are only stopped by mayDig rule 5 (walls box +2 from centre.y-48 up), as the spec says.
- `nearLair(d, p, r)` and `nearKnownVillage(FactionState f, p, r)`: Chebyshev (horizontal). f may be null (false).
- `workRadius(b, configured)`, `forageAllowed(b)` (testRadius <= 0).

### campaign/MapBounds.java
- `inside(d, x, z, margin)`, `inside(d, BlockPos, margin)`, `clamp(d, p, margin)` (keeps y; returns p itself when inside).
  Uses CampaignData.center/radius (square map).

### work/WorkSafety.java
- `mayDig(ServerLevel, pos, @Nullable BaseState own, @Nullable WorkplaceState ownMine)`: false for destroy speed < 0,
  block entities, structural(), any non-destroyed base's planIndex, under a base (BaseZones.insideWalls(b, pos, 2) and
  y >= centre.y-48 for settlements / -16 for lairs, only when that base's planIndex is non-empty; own base included),
  BaseZones.inForeignArea(data, own.id, pos, 8) (own == null: every base counts as foreign), MineLayout.nearStairs of
  any MINE != ownMine (pre-check |dx|,|dz| <= 6), and Biomes.DEEP_DARK (level.getBiome does not load chunks).
- `structural(BlockState)` (cached per Block): STONE_BRICKS tag, reinforced/chiseled deepslate, chiseled tuff, and
  registry paths containing "brick", "deepslate_tile", starting "polished_deepslate"/"polished_tuff", "quartz" (not
  "ore"), "purpur", "sculk", "copper_grate", "copper_bulb", "chiseled_copper". So every brick family incl. stairs,
  slabs, walls and infested stone BRICKS (strongholds) is structural; plain infested_stone is not.
- `builtBlock(BlockState)`: the woodland STRUCTURE list (planks, wooden slabs/stairs, fences, gates, wooden doors and
  trapdoors, walls, c:glass_blocks/c:glass_panes, wool, carpets, beds, cobblestone (+stairs/slab), structural(),
  bookshelf, hay, torches, lanterns, signs, rails). Block entities are checked by the caller.
- `planned(CampaignData, pos)`: in any non-destroyed base's planIndex.
- `protectedWood(level, data, @Nullable own, pos)`: planIndex; settlement walls box +2 (own included); foreign area +8
  (lairs +16); outside MapBounds (margin 0); a VILLAGE report of own.faction within 32 (Chebyshev).
- `enum Use {MINE_SITE, GROVE, FISH_STAND}`, `reserved(d, own, p, u)`: GROVE = within 6 of any MINE origin or the own
  road strip (|dz| <= 3, |dx| <= half+30); FISH_STAND = within 8 of any MINE origin; MINE_SITE = nothing yet.
  Hooks marked "// step 4/5/6" for the fishery, woodland and sapling parts.

### logic/TreeShape.java (pure, TreeShapeTest)
- `enum Kind {AIR, SAME_LOG, OTHER_LOG, NATURAL_LEAVES, PERSISTENT_LEAVES, GROUND, STRUCTURE, OTHER}`
- `enum Verdict {OK, TOO_SMALL, NOT_NATURAL, TOUCHES_STRUCTURE, TOO_TALL}`
- `interface Cells { Kind at(int x, int y, int z); }` (SAME_LOG is relative to the stump's block: the adapter decides)
- `record Tree(Verdict verdict, List<int[]> logs /* {x,y,z} top-down, stable */, boolean big, int topDy)` + `ok()`
- `survey(cells, x, y, z, boolean resume)`: flood as before (dx,dz -1..1, dy 0..1; +-7, 0..+24, 96 logs), SAME_LOG only.
  Check order: TOUCHES_STRUCTURE (any face neighbour STRUCTURE or OTHER_LOG, the stump's floor included), TOO_TALL
  (topDy > MAX_FELL_DY = 8), then unless resume: TOO_SMALL (< MIN_LOGS = 3), NOT_NATURAL (no NATURAL_LEAVES in
  top +-2 / -1..+2, or the stump not on GROUND). Stump not SAME_LOG: resume -> OK with no logs, else TOO_SMALL.
- `twoByTwoCorner(cells, x, y, z)` -> {x, z} of the NW corner (min x, min z) of a 2x2 SAME_LOG square on GROUND
  containing the stump, else null. `big` = that is non-null. Step 6's ForestScan can use it for the spot move.
- `naturalLeavesNear(cells, x, y, z)`, constants MAX_FELL_DY, MIN_LOGS, MAX_SPREAD, MAX_RISE, MAX_LOGS, LEAF_RADIUS.

## Changes to existing jobs
- work/Job.java: remoteAtNight uses BaseZones.chebyshev/half (the step-1 "// step 2" note is gone).
- work/MinerJob.java:
  - private `mayDig(wp, pos)` wraps WorkSafety.mayDig(level, pos, base(), wp) and remembers the last OK cell and its
    BlockState (a cell takes many ticks to break).
  - mine(): a non-passable plan cell that fails mayDig -> `cutPlan(wp, plan)`: main line (s == landingSide) sets
    progress = plan.size() (marked "// step 3: also mark the level as cut"), a branch skips the rest of that branch
    side. The face watchdog now uses the same cutPlan. The check runs before the walk to the cell.
  - vein: polled when mayDig fails. clear(): polled when mayDig fails. findBlocked(): cells failing mayDig are not
    queued (so a protected cell never triggers CLEAR every 1200 ticks).
  - walkInMine stair clearing: a clearance cell failing mayDig is skipped (continue).
- work/CaveMinerJob.java: WorkSafety.mayDig(level, c, base(), null) replaces the destroy-speed check in tunnelToward
  (skip the ore) and prospecting (endDirection); also before mining a marked ore (skip "it is protected") - see
  deviation 1.
- work/WoodcutterJob.java:
  - `public static TreeShape.Tree surveyTree(level, data, own, stump, boolean wild, boolean relaxed)`: the world Cells
    adapter (marked "// step 6: this adapter moves to ForestScan.cells"). Unloaded or planned cells -> STRUCTURE; block
    entities -> STRUCTURE; logs: protectedWood (wild only) -> STRUCTURE, same Block as the stump -> SAME_LOG, else
    OTHER_LOG; leaves split by PERSISTENT; builtBlock -> STRUCTURE; #dirt -> GROUND; else OTHER. A planned stump, or
    a wild stump on protected wood, returns TOUCHES_STRUCTURE at once.
  - chop(): collectLogs is gone; the survey runs once per tree when logs is empty; any verdict other than OK ->
    leaveTree (wild: badWild, cache cleared; plot tree: skipUntil now+6000) before the first cut.
  - New transient-but-saved `felling` (stump of a tree whose first log was cut) and `fellingWild`; NBT keys "felling"
    (long, same meaning as step 6's) and "fellingWild". decide() resumes a started tree first (after the STORAGE and
    UPGRADE triggers); the resume survey is relaxed. felling is cleared when the tree is done or given up.
  - cuttableLeaves(p): #leaves, not PERSISTENT, not planned. Used by the leaves-underfoot rule, leafInTheWay and
    findLeaves. leafInTheWay takes wp as a parameter (no claim any more).
  - wildTreeAround: protectedSpot is replaced by WorkSafety.protectedWood(level, data, base, stump); trunks taller
    than MAX_FELL_DY are skipped in the scan (deviation 3). protectedSpot is deleted.
  - decide(): an orphan plot tree honours skipUntil (needed, else a rejected orphan would loop every tick).

## Deviations from the spec and why
1. CaveMinerJob also checks mayDig before mining a marked ore (the spec names only :341 and :445). Mining I says
   mayDig serves CaveMinerJob; a marked ore can sit in another mine's stair ring or under a base, so it is skipped.
2. The base's own plot trees (wild == false, including orphans) are surveyed `relaxed` (size, leaf and soil rules
   skipped) and without protectedWood; the contact (same block, structure, other log) and height (+8) rules apply.
   Why: in this step the plot still exists and is the base's own land; a stub from an interrupted felling (or a tree
   near a known village) would otherwise block a grid spot for good. Rejected plot trees get skipUntil now+6000
   (like unreachable ones), so a too-tall fancy oak stays standing and its spot is lost until step 6 removes the plot.
3. wildTreeAround also skips trunks with top - base > 8 (step 6's column rule), so the woodcutter does not walk to a
   tree only to reject it. It only removes trees the survey would reject anyway.
4. reserved(GROVE) already includes the own road strip (pure geometry, no later-step data). Nothing calls it yet.
5. Extra GameTest treeSurveyLeavesBuiltAndProtectedWood (workers_static, synchronous) tests the world adapter:
   natural oak OK (4 logs, top first), trunk touching planks TOUCHES_STRUCTURE, persistent-leaf pillar NOT_NATURAL,
   10-log oak TOO_TALL, birch on stone NOT_NATURAL, a wild oak 3 from the centre (walls +2) TOUCHES_STRUCTURE while
   the same tree with wild=false is OK, protectedWood true/false, and the survey cuts nothing. Its helper
   `testTree(h, x, z, log, leaves, height, persistent, ground)` builds a 3x3 canopy (DISTANCE 1) at the top; reuse it.
6. Only the closer approach / deferred logs / stump-column pass of woodcutting D are NOT done (step 6): logs out of
   TRUNK_REACH from the 4.0 stop are still dropped from the list as before, so a +8 log can still float (no worse
   than before this step).

## GameTest minerNeverDigsProtectedBlocks (workers_static, synchronous)
Registered base (half 2, radius 11) with a one-block StructureRecord at rel (13,2,13); MINE `own` at rel (4,5,20)
and `other` at rel (18,5,4), both facing the rotated EAST; their stair cells local(1,0,-2) set to stone. Asserts:
plain stone at rel (20,2,16) true; chest, planned block, centre.below(10), other's stairs, sculk shrieker, deepslate
bricks false; own stairs true with ownMine = own and false with ownMine = null; structural() false for stone,
infested stone and planks, true for tuff brick stairs, waxed oxidized copper grate, reinforced deepslate and cracked
stone bricks.

## Notes for the next steps
- Step 3: cutPlan's main-line branch is where `lv.cut = true` goes (the comment is there). Until then a cut main
  line can be revisited by lengthening and its new segment skipped cell by cell (60 ticks each; old behaviour for
  face-watchdog cuts too). extend() still has no mayDig; abandonDescent should use MinerJob.mayDig(wp, cell).
  walkInMine's skipped stair cell is where stairBlockedTicks goes.
- Step 4: WorkSafety.reserved(MINE_SITE) is empty (hook comment). BaseZones.outsideAllWalls / inForeignArea /
  nearKnownVillage and MapBounds.inside/clamp are ready for MineSites.evaluate and the survey points.
- Step 5: FISH_STAND (mine origins within 8) is in; add VALID fisheries to GROVE/MINE_SITE; BaseZones.nearLair is
  Chebyshev from the lair centre.
- Step 6: move the surveyTree adapter to ForestScan.cells (signature can stay); TreeShape.twoByTwoCorner gives the
  NW corner; job NBT key "felling" already exists with the step-6 meaning ("fellingWild" can go: all trees are wild
  then). protectedWood is already the wild-tree rule. cuttableLeaves is the 'non-persistent, not planIndex' rule.
- Expect 51 GameTests + new ones, 53 unit tests + new ones.

## Review (commit 4cd23f7, on top of 98fb99f)

I checked every bullet of steps[1], shared 1-2, woodcutting C and D.2, mining I and the three listed tests against
the code paths. I read MinerJob, CaveMinerJob, WoodcutterJob, WorkSafety, BaseZones, MapBounds, Zones and TreeShape
in full, not only the diff. All bullets are in, apart from the items the notes above hand on to steps 3 and 6.

These were checked and needed no change:
- extend() is untouched. DigOut, NpcNavigator, the other jobs, combat, raids and logistics are not in the diff.
- mayDig rules 1-8 match shared 2:
  - the walls box uses BaseZones.half, which honours testHalf;
  - rule 5 needs a non-empty planIndex;
  - lairs are refused from 16 below the centre;
  - own == null makes every base foreign.
- The legacy hut mine sits outside every rule:
  - the spiral stays outside the own walls box + 2 (x >= cx+30 against 26);
  - repairablePart leaves air out of the plan, so the hut's open stair cells are never refused by the plan rule.
- Every mine() refusal moves progress (cutPlan always advances it), so no loop. The face watchdog shares cutPlan.
  The refusal check runs before the walk, so a deep-dark level is cut on its first cell.
- NBT: 'felling' and 'fellingWild' are written only when set and read back as null/false from old tags.
- getBiome does not load chunks: LevelReader.getNoiseBiome uses getChunk(..., false).
- The survey adapter cannot get a false STRUCTURE from an unloaded cell next to the woodcutter. The stump is within 4
  blocks and the flood within 8 of it, so every cell is in the NPC's own chunk or a neighbour, and those are loaded
  while it ticks.
- The STRUCTURAL cache is a ConcurrentHashMap. No client classes are used.
- The unit tests are not vacuous:
  - TreeShapeTest covers the 10 spec cases with synthetic cells;
  - ZonesTest covers the 28 boundary and map radius 0.

Findings and what was done:
1. MAJOR, fixed (WoodcutterJob):
   - When the new survey rejected a wild tree (leaveTree), or a wild tree was given up as unreachable, wildCache was
     cleared but wildTree() did not scan again until its 600-tick timer ran out.
   - decide() therefore fell through to forage(), and the woodcutter walked 72 blocks out (for up to 2400 ticks)
     even with other trees nearby.
   - The survey makes rejections common: bee nests, built blocks, other species touching, and trees too tall.
   - Fix: both paths set nextWildScan = now(), so the next decide() scans at once (spec B: "or when the cache is
     empty"). Each rejected tree is in badWild, so this costs at most one scan per rejected tree.
2. MINOR, fixed (WoodcutterJob): D.2's "for a big tree, the canopy box is ±5" was missing. The survey's big flag is
   kept (bigTree), and findLeaves uses ±5 for a 2x2 trunk.
3. MINOR, fixed (WoodcutterJob):
   - `wild` was not saved. After a reload in CHOP, LEAVES, COLLECT or PLANT, a wild tree became a plot tree:
     - for a tree not yet started, the relaxed plot survey skipped protectedWood and the size, leaf and soil rules;
     - the plot PLANT branch forced dirt and planted at the wild stump.
   - The PLANT problem is an old bug; step 2's relaxed plot survey made the CHOP case matter.
   - Fix: 'wild' is now saved, and it loads true only when a spot is saved too. An old tag loads false, as before.
4. MINOR, fixed (CaveMinerJob): the extra mayDig check on a marked ore ran only once it was in reach. A cave miner
   therefore walked (up to 600 ticks), and even tunnelled, toward an ore it would then refuse. The check now runs
   before the walk.
5. TEST, fixed (minerNeverDigsProtectedBlocks):
   - The planned block sat inside the walls box + 2, so rule 5 refused it on its own and the plan rule (4) was never
     tested. It now lies at Chebyshev 8, and the test first checks that it is diggable before it joins the plan.
   - The "rock under the base" was centre.below(10). Tests are placed at y=-59, so that is about y=-67, below the
     bottom of the world.
   - That check now uses a stone cell 3 below the floor at Chebyshev 4 (refused), with a control stone cell at
     Chebyshev 5 (allowed).
6. Not changed (transitional or by design):
   - A main tunnel refused by mayDig gets only progress = plan.size(). A later lengthening round of that level lies
     beyond the undug gap and is skipped cell by cell: 61 ticks per cell, about 500 cells per round.
     - Before this step, only face-watchdog cuts did that. Now dungeon spawners, trial chambers and strongholds cut
       main tunnels too.
     - Step 3's lv.cut and plan() remove it; the hook is in cutPlan.
   - A stair cell refused by mayDig (for example a refill in a deep-dark stretch of the shaft) is skipped with no
     exit. Step 3's stairBlockedTicks covers it.
   - Plot fancy oaks taller than +8 keep their grid spot blocked until step 6 removes the plot. This is per spec.
   - decide()'s isTree(felling) can load a chunk synchronously once, if the half-felled tree's chunk was unloaded by
     a reload.
   - A skipped orphan hides other orphans (orphanCache) for its 6000 ticks. Orphans are rare, and step 6 deletes them.
   - CaveMinerJob calls WorkSafety.mayDig uncached every tick while walking to or breaking a marked ore. The cost is
     O(bases + workplaces), like DigOut.isProtected.

Totals after review:
- unit tests: 53 passed, 0 failed;
- GameTests: "All 51 required tests passed". Log: impl/gt_2r.log.

For step 6: the job NBT now also has 'wild' (boolean). v2 can drop it together with 'fellingWild'.
