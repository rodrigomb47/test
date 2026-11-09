# Step 4: free-form sites and hutless shafts (commit 553e881, on top of b7bb3b8)

The build is clean: only the old deprecation warnings remain.
- Unit tests: 103 passed, 0 failed (96 before + 7 new in MineSiteRulesTest, which now has 9).
- GameTests: "All 63 required tests passed" (61 before + minerDigsItsOwnShaftAndSealsTheGroundRing and
  minerFindsNoSiteOnShallowGround). Passed on 3 runs: gt_4.log, gt_4_run1.log, gt_4_run2.log (in impl/).
  gt_4_neg.log is the negative check: with the old ring rule (skip y >= origin.y-1) the new shaft test fails
  ("the ground ring at ... should be plugged, is air").

README, BALANCE and docs are untouched (step 7). The tree plot and its fence are still built (step 6).

## What was implemented

### logic/MineSiteRules.java (pure; MineSiteRulesTest)
- `facingsAwayFrom(dx, dz)` -> List<int[]{fx, fz}>: the dominant outward axis first, then the two perpendicular
  ones (more outward first), dropping any with a negative dot product with (dx, dz) (pointing back at the base).
  Empty at (0, 0). Due east -> E, S, N; north-east -> E, N.
- `candidates(cx, cz, rMin, rMax, step)` -> List<int[]{x, z}>: square rings at Chebyshev rMin, rMin+step, ... <= rMax,
  inner first, a point every `step` along each ring, corners included, no duplicates. 32..64 by 4: 864 points.
  A test base (half 2, radius 11): ring 10 only, 20 points.
- `maxRoundsThatFit(ox, oz, fx, fz, maxReach, List<Rect> obstacles, Rect map)`: the largest r in 0..maxReach whose whole
  `corridor` overlaps no (already inflated) obstacle and lies inside `map` (null = no edge); -1 if r=0 fails.
- `score(gateDistance, penalty, relief, loose)` = gate + 2*penalty + 4*relief + (loose ? 10 : 0); constants
  PENALTY_WEIGHT, RELIEF_WEIGHT, LOOSE_COST.

### work/MineSites.java (world adapter)
- `record Site(BlockPos origin, Direction facing, int maxRounds, double score)`.
- `static final class Search` (`new Search(base, badSites)`, `step(level, data, base, budget)` -> true when done,
  `done()`, `best()`, `evaluated()`, `allTooShallow()` = evaluated >= 8 and every evaluated point failed the depth
  rule). Candidates = `candidates(centre, half(base)+8, workRadius(base, mineSiteRadius), 4)`; PER_TICK = 64.
  Points in unloaded chunks or outside MapBounds (margin 16) are skipped, not counted.
- `evaluate(level, data, base, x, z, badSites)` -> Site or null (public; the Search uses the package-private
  `judge`, which also says why: NOT_LOADED / SHALLOW / UNFIT / OK).
  Order: loaded+map, depth (MOTION_BLOCKING_NO_LEAVES feet y, MinePlanner.deepEnough), zones (outsideAllWalls 8,
  inForeignArea 16, known village 48, reserved(MINE_SITE)), other MINE origins < 24 (all, worked out too), bad sites
  within 8, CaveWork.standable(O); then per facing from facingsAwayFrom: maxRoundsThatFit (obstacles =
  `obstacles(data, base, null)`, map = campaign square less 8), collar (f 0..3, s -1..2: height within +-1, MB == MB_NO_LEAVES,
  ground Terrain.isSolidGround and not leaves/log/sapling/farmland/dirt_path/ice/barrier/block entity/planIndex; shaft
  cells f 1..2 s 0..1 open at y and y+1), surroundings (f -3..6, s -4..5: water/lava where MB > OCEAN_FLOOR on every
  second column, built blocks g-1..g+2 on every column), corridorPenalty (f 3..42, s -10..11 every 6: +6 water, +3 dip
  below y-3, unloaded 0) < 30. Lowest penalty wins, tie -> more rounds. Score with the nearer gate (centre if none).
- `create(level, data, base, miner, site)`: new MINE {origin = extent = O, facing, hut false, maxRounds r, bottom 0,
  cur -1, upgradeStep -1, opened now, assigned miner}, data.addWorkplace, faction message
  "<name> opens a mine at x, y, z (tunnels run east)". (mineV is written by WorkplaceState.save as for every MINE.)
- `corridorPenalty`, `gateDistance`, `mapRect` are package-private helpers. New PROPOSED constants: GRID 4,
  RING_MARGIN 8, PER_TICK 64, SITE_WALLS_MARGIN 8, SITE_FOREIGN_MARGIN 16, VILLAGE_RADIUS 48, MINE_SPACING 24,
  BAD_SITE_RADIUS 8, SITE_MAP_MARGIN 16, MAX_PENALTY 30, WATER_PENALTY 6, DIP_PENALTY 3, PENALTY_STEP 6, SHALLOW_PROOF 8.

### work/MinerJob.java
- State SITE (saved; loads as CHECK; a mine claimed while in SITE goes through CHECK). `noMine` became `site(b)`:
  LEAVE (mineAt + leaveMine), UNLOAD, the step-3 resting-mine pickaxe fetch, then: openMines >= maxOpenMines ->
  idle "every mine site of the base is taken"; now < nextSiteSearch -> idle with the last reason; no pickaxe -> the
  storehouse (upgradeEquipment) and wait; SEARCH (64 points a tick, status "Looking for a place to dig a mine");
  found -> create, markBusy, plan(w) at once, then TRAVEL (or STORAGE, see deviation 7); all too shallow -> FAIL
  "the ground here is too shallow for a mine"; else SURVEY point i (0..3) = MapBounds.clamp(centre + 44*(+-1, +-1),
  16) at ground height when loaded, goTo reach 8 within 2400 ticks, then SEARCH again; after 4 points (or at once
  for a GameTest base) FAIL "no place to dig a mine". FAIL: nextSiteSearch = now + 6000, markIdle, waitFor(200).
- Job NBT: "badSites" (long[], FIFO, at most 32) and "nextSite" (long). A tag without them loads empty / 0.
- badSites gets the origin of every mine this miner retires as unreachable ("its mouth cannot be reached"), unsafe
  ("the ground below is unsafe", abandonDescent with bottom < 6) or with blocked stairs.
- Unreachable site (travel, while upgrading with bottom == 0 and more than 3 from stand(0)): walking ticks counted
  (a gap counts at most 101) past 2400 -> bad site + retire + CHECK.
- Mouth torch: on the EXTEND tick with bottom == 0 && upgradeStep == 0 of a hutless mine, a standing torch from the
  backpack at local(0, -1, 0) when that cell is air on a sturdy floor and in reach.
- sealShaft: neighbours are skipped from origin.y - (hut ? 1 : 0) up, so a hutless mine plugs the ground ring at
  origin.y - 1 where it is air, fluid or a FallingBlock; air at or above origin.y is left.
- workplaceInfo without a mine: "no mine yet - looking for a place to dig one" while searching or surveying.
- New PROPOSED constants: SITE_RETRY_TICKS 6000, SURVEY_POINTS 4, SURVEY_OFFSET 44, SURVEY_TICKS 2400,
  SURVEY_MAP_MARGIN 16, UNREACHABLE_SITE_TICKS 2400, MAX_BAD_SITES 32.

### Other files
- work/CaveWork.java: `ownWorkings` = b.contains(pos, 16), or inside some MINE of b's workings
  (`inMineWorkings`: forward -4..corridorEnd(reachRounds)+4, side -15..16, y from origin.y - max(bottom, 6) - 6 to
  origin.y + 2). Constants WORKINGS_MARGIN 4, WORKINGS_DEPTH 6.
- work/WorkSafety.java: reserved(MINE_SITE) also refuses within 6 (TREE_SITE_RADIUS) of a tree the woodcutter grows,
  for now every TREE_PLOT's grid spot (TreePlotLayout.spots) - see deviation 10. The VALID-fishery part came in step 5.
- campaign/BaseBuilder.java: no MINE, buildInitial, sealMine, east mine pad or its skirt; MIN.x = cx - WORK_AREA_WEST,
  MAX.x = cx + WORK_AREA_EAST (same numbers, 58 and 78; MIN.z rule unchanged).
- structure/BaseLayout.java: MINE_ORIGIN_X and the east path deleted; WORK_AREA_WEST = 58, WORK_AREA_EAST = 78; class doc.
- structure/SiteFinder.java: footprint maxX = HALF + 2; mineAreaPenalty and its call deleted; class doc.
- structure/MineLayout.java: buildInitial deleted (with the FactionType and LegacyMine imports).
- campaign/CampaignStarter.java: class doc only.
- tools/sim.py: ERROR when a hutless mine's origin is within Chebyshev 32 (half + 8) of any settlement centre.

## Tests
- MineSiteRulesTest (9): corridorRectForEachFacing, overlapsHonourTheMargin (step 3), facingsNeverPointAtTheBase,
  overlappingCorridorsRejected, parallelCorridorsFarApartAccepted, ownWallsBlockTunnelsUnderTheBase,
  maxRoundsShrinksNearTheMapEdge, candidatesStayInTheRing, scoreWeighsTheWalkWaterAndGround.
- GameTest minerDigsItsOwnShaftAndSealsTheGroundRing (batch mine_shaft, timeout 400, skyAccess): the spec setup plus
  4 torches and an extra air cell at local(2,2,-1) (deviation 13). At bottom == 3: stair cells open; local(2,-1,-1),
  local(1,-1,-1) and local(2,2,-1) are cobblestone; a torch stands at local(0,-1,0); then clean-up at once.
- GameTest minerFindsNoSiteOnShallowGround (batch mine_nosite, timeout 200, skyAccess): base half 2 radius 11, miner
  with a stone pickaxe. Each tick the miner must be on the platform; once idleReason contains "too shallow": no MINE
  of the base, and the 24x24 floor (rel y 1) unchanged.
- minerDigsDownToItsLevelThenRetiresAShallowMine: its last condition is now idleReason "too shallow" (after the
  retirement the miner looks for a site), was "no mine".

## Deviations from the spec and why
1. Survey walks are skipped for GameTest bases (BaseZones.forageAllowed false): FAIL "no place to dig a mine" at once,
   as the fisherman's forage is. The test "never left the platform" needs it.
2. candidates are rings with a point every 4 blocks along each ring (from rMin), not an absolute 4-grid. For 32..64
   this is the same set relative to the centre (864 points; the spec says "about 780"); for a test base's ring 10..11
   a 4-grid would give no point at all.
3. Chebyshev 32 itself fails outsideAllWalls(8) (BaseZones: inside when <= half + margin), so mouths end up at 36..64
   on the 4-step rings. Both spec rules are kept literally; sim.py's ERROR uses the same <= 32.
4. The built-block check (c2) uses WorkSafety.builtBlock (planks, cobblestone, fences, glass, wool, beds, torches,
   rails, signs ... and every structural block) plus block entities, not only structural blocks: village houses are
   mostly planks and cobblestone, and the spec's stated purpose is to protect unreported villages.
5. Built blocks are checked on every column of the box, water on every second (checkerboard); the box is cached per
   point across facings.
6. Unloaded columns: in the collar and the f -3..6 box the facing is rejected (nothing can be verified); in the
   corridor penalty they count 0 (spec).
7. After create the miner goes to STORAGE first when it carries fewer than 8 torches or 16 plugging blocks (spec:
   TRAVEL): the shaft needs both. The plan is already made, so STORAGE (upgrading) does not plan again.
8. SEARCH and SURVEY do not markBusy: a miner that retries every 6000 ticks keeps its idle stretch, so the
   12000-tick retraining still happens. markBusy only when a site is created (or a mine is claimed).
9. With a full storehouse the SITE unload no longer blocks the search: a PARTIAL_FULL unload is retried after 1200
   ticks (the step-3 noMine would have stood at the storehouse forever).
10. reserved(MINE_SITE)'s woodland part uses the current equivalent of planted saplings: every TREE_PLOT's grid spot
    (the same list step 6 puts into `planted` for legacy plots). Hook comment "// step 6" in WorkSafety.nearGrownTrees.
11. Stairs-blocked retirement also adds the site to badSites (spec lists unreachable and unsafe). Note that rule (e)
    (24 from every MINE origin, worked-out ones included) already keeps a miner off its old sites, so badSites is a
    backstop.
12. Barrier ground is rejected in the collar. In GameTests without skyAccess the barrier ceiling (rel y 8, y=-52)
    is within a block of the depth threshold; real worlds have no barriers.
13. The shaft GameTest adds an air hole at local(2,2,-1), away from the water: the water at local(2,-1,-1) floods
    local(1,-1,-1), so preSeal alone plugs both spec cells and they cannot tell the new ring rule from the old one.
    Also 4 torches and the mouth-torch assertion.
14. inMineWorkings uses MineLayout.reachRounds(w) (the larger of maxRounds and every level's rounds), as step 3's
    corridor and inMine do, instead of maxRounds.
15. The unreachable-site watchdog counts walking ticks (gaps capped at 101), like the fisherman's travel give-up, so
    errands and "Cannot reach" waits do not count in full.

## Notes for the next steps
- Step 6:
  - WorkSafety.nearGrownTrees: swap TreePlotLayout.spots for the woodland's planted + replant entries (hook). The
    FISH_STAND "within 1 of a planted sapling" hook is still in reserved().
  - BaseBuilder still builds the fenced plot, its saplings and workplaceManaged; MIN.z keeps `cz - TreePlotLayout.half(3) - 3`
    (= cz - 14, so MIN.z stays at the walls' cz - 25). BaseLayout's class doc mentions the plot pad.
- Step 7 (docs): README mines (self-chosen sites, survey walks, too-shallow idle, no hut for new bases); BALANCE:
  the constants listed above (MineSites, MineSiteRules weights, MinerJob site constants, CaveWork workings margins,
  WorkSafety TREE_SITE_RADIUS 6); SiteFinder/BaseLayout/CampaignStarter docs are already updated in code.
- Step 8 (campaign): only loaded chunks are searched, so early sites will mostly lie in the forced belt east of the
  base (x +32..+78, |z| <= 25) or near the west plot's edges; survey walks load the diagonal corners. sim.py prints an
  ERROR for a mine mouth within 32 of a settlement centre. Watch for repeated "opens a mine" messages (a site retired
  as unreachable at once) and for "too shallow" in non-flat worlds (should never appear).
- Totals: 103 unit tests, 63 GameTests.

## Review (commit eb5d37d, on top of 553e881)

I read the code paths, not just the diff:
- MinerJob: run, caveWork, wp, site, siteFailed, surveyPoint, travel, siteUnreachable, extend, mouthTorch,
  sealShaft, abandonDescent, walkInMine, leaveMine, beforeErrand, save and load.
- MineSites: Search, judge, collar, surroundingsClear, builtColumn, corridorPenalty, obstacles, create.
- Also MineSiteRules, CaveWork.ownWorkings, WorkSafety.reserved and nearGrownTrees, BaseBuilder, BaseLayout,
  SiteFinder, CampaignStarter (the 150-block base spacing is unchanged), the director's miner needs, and the new
  GameTests.

I checked these against the vanilla sources:
- ServerChunkCache.hasChunk (a FULL ticket);
- Level.getHeight (top + 1, minBuildHeight when unloaded);
- the heightmap predicates (snow layers and grass are not motion-blocking; fluids are).

### Spec compliance
Every bullet of steps[3] is in, and so are mining D, E and the rest of L, and shared 8:
- the SITE phases LEAVE, UNLOAD, SEARCH, SURVEY and FAIL;
- evaluate rules (a) to (g), the score, create, and badSites;
- the hutless ring rule, the mouth torch, and the unreachable-site retire;
- ownWorkings by corridor rectangles;
- reserved(MINE_SITE);
- the BaseBuilder, BaseLayout, SiteFinder and MineLayout deletions, with MIN/MAX at the same numbers;
- the CampaignStarter doc;
- the two spec GameTests and MineSiteRulesTest.

I accept the implementer's deviations 1-15. Deviation 8 (SEARCH and SURVEY do not mark the miner busy) is needed: with the spec's literal "otherwise markBusy", a retry every 6000 ticks would reset the idle clock, and a miner with no site would never be retrained.

Not done, and not in this step's list: shared 9's "NpcNavigator comment at :267 is updated". The comment there is still accurate, so I left it for step 7.

### Findings
1. **MAJOR, fixed: a half-dug first shaft could be given up as "unreachable".**
   - Cause: the bottom of a new mine stays 0 until the whole first descent is dug. That can be 120 steps for a DEEP layer.
   - siteUnreachable counted every TRAVEL tick more than 3 blocks from the mouth. A miner that re-entered TRAVEL deep in that shaft and climbed back to the surface was counted as walking toward a mouth it never reached. That happens after a reload (EXTEND loads as TRAVEL) and after the stairs-blocked CHECK.
   - Past 2400 walking ticks, a good mine was retired and its site blacklisted.
   - Fix: count only before the descent begins (upgradeStep == 0). After that, the mouth has been reached.
   - New GameTest `minerKeepsAHalfDugShaftWhileWalkingBack` (batch mine_reach, 300 ticks, skyAccess). The walking allowance is already used up (set by reflection).
     - With upgradeStep 6 the mine must be kept for 30 ticks of TRAVEL.
     - Then, as a positive control, upgradeStep 0 must retire it ("its mouth cannot be reached") and add it to badSites.
     - Without the fix it fails on the first ticks (gt_4r_neg.log).
2. **MAJOR (a race), fixed: the site was not checked again when the mine was created.**
   - The search runs about 14 ticks, and longer after a survey walk.
   - Two miners searching at the same time see the same candidates in the same order with the same scores. Both would open a mine at the same mouth.
   - WorkSafety.mayDig rule (7) then refuses each one's stairs to the other, so both descents are abandoned at step 1. Both mines are retired as unsafe, and the site goes into both bad lists.
   - Nearby (not identical) picks could also overlap corridors.
   - Fix: in the tick a site is to be created, MinerJob re-runs MineSites.evaluate on it. If it no longer passes, a fresh search starts.
   - There is no GameTest, because the flat world never yields a site.
3. **Minor (cost), fixed: every candidate was judged in full.**
   - On good, flat ground that is up to 3 facings × about 1,100 chunk reads (heightmaps and block states) per point. At 64 points a tick, that is up to about 200k reads a tick for about 14 ticks.
   - Now judge() drops a deep-enough point after two reads when its gate distance alone is at least the best score found so far (new Verdict WORSE).
   - The result is exactly the same: score >= gate distance, and only a strictly lower score replaces the best. Because inner rings come first, most of the ring is dropped this way.
4. **Minor, fixed: an idle miner waited out in the open.**
   - After a failed search the miner idled for 6000 ticks wherever it was, up to about 62 blocks out at the last survey point, night included.
   - While it waits it now walks back to the storehouse when it is more than walls+16 from the centre (HOME_MARGIN).
5. **Minor, fixed: the reason for a failed search was lost on reload.**
   - After a reload during the 6000-tick wait, the idle reason (and so the retraining message) always said "no place to dig a mine", even when the ground was too shallow.
   - It is now saved in the job NBT as 'siteWhy', next to 'nextSite'. Old tags without it keep the default.
6. **Minor, fixed: a mouth could be placed on a huge mushroom cap.**
   - Mushroom blocks pass Terrain.isSolidGround, so a flat 7x7 cap passed the collar test, with air below for the shaft.
   - The collar now rejects brown and red mushroom blocks and mushroom stems.
7. **Noted, not changed (spec design): the best site is usually right outside a gate.**
   - The score favours the shortest walk from a gate, so the best site is usually about 11 blocks in front of the east or west gate. The open L-shaped mouth then sits on the main way out of the base.
   - The spec accepts the risk (falls of at most 3 blocks; the spiral can be walked out of).
   - Step 8: watch for NPCs (guards, raiders, haulers) dropping into a mouth by a gate.
8. **Noted, not changed (spec rule c2): desert builds are not protected by the built-block test.**
   - Desert pyramids and desert villages are built of sandstone variants and terracotta. Those are not built blocks, so a mouth can be chosen on or next to them. Exceptions: doors, beds and other block entities within 3, or a scout-reported village (48).
   - mayDig still refuses block entities, so chests and the pyramid's trap are left alone.
9. **Coverage gap (known, as the implementer said).** No GameTest finds an actual site.
   - The flat world fails the depth rule. Raising the ground does not help either: the corridor-dip penalty samples up to 42 blocks out, which lands on neighbouring platforms.
   - Real site finding is first seen in step 8.

### Checked and fine
- **NBT.** The job's badSites, nextSite and siteWhy are symmetric, and an old tag loads empty / 0 / the default. SITE loads as CHECK. A new MINE is saved with mineV 1 and hut false, so it is never migrated.
- **Null guards.** site() only runs with a base, mineAt guards base and pos, and nearKnownVillage takes a null faction.
- **Unloaded ground.** Every column judge reads is checked with hasChunk first. The collar and the surroundings box reject an unloaded column; the corridor penalty counts it 0.
- **Geometry.**
  - Rings of 36..64 on the 4-grid (32 fails outsideAllWalls(8), as the spec's "> half+8" says).
  - The corridor fits the own walls+4 from Chebyshev 32 outward.
  - The ring rule plugs (0,0), (1,-1), (2,-1), (3,0), (3,1), (2,2) and the (1,1) ceiling cell at origin.y-1. It never plugs a shaft cell or anything at origin.y or above.
  - The mouth torch at local(0,-1,0) is beside the walking line from stand(0) to step 1.
  - The heightmap comparisons use the same "top + 1" values throughout.
- **Loops.**
  - LEAVE runs on leaveMine's 1200-tick budget, and SURVEY on surveyUntil.
  - SEARCH is bounded by its points.
  - The re-check restarts at most once per search and only when something changed.
  - FAIL waits 6000 ticks, and the unreachable watchdog is bounded.
  - A full storehouse is retried every 1200 ticks.
- **Threads and sides.** No static state was added, and no client classes are used.
- **Things left unchanged.** DigOut and NpcNavigator are unchanged since f90ec43. Legacy hut mines keep sealShaft's old top (origin.y-1), get no mouth torch, and have a bottom above 0, so the unreachable watchdog does not apply to them.

### Tests
- Unit tests: 103 passed, 0 failed (unchanged).
- GameTests: "All 64 required tests passed" on 4 runs (gt_4r.log, gt_4r_1.log, gt_4r_2.log, gt_4r_3.log): the 63 before plus minerKeepsAHalfDugShaftWhileWalkingBack.
- The new tests of this step are not vacuous:
  - the shaft test's air hole tells the ring rules apart (implementer's gt_4_neg.log);
  - the shallow test runs the real search over the base's 20 ring points;
  - the new reach test fails without fix 1.

### For the next steps
- **Step 6:**
  - The WorkSafety.nearGrownTrees hook is unchanged (it uses the plot grid until the woodland's planted and replant entries exist).
  - The FISH_STAND sapling hook is still open.
- **Step 7:** BALANCE should also list MinerJob HOME_MARGIN 16 (the waiting place). MinerJob.UNREACHABLE_SITE_TICKS is now public, for the GameTest.
- **Step 8:** watch for:
  - the mouth placement by the gates (finding 7) and desert builds (finding 8);
  - "opens a mine" followed by an unsafe retire at step 1, which would point to a race the re-check missed;
  - the tick time while a miner searches.
- **Totals:** 103 unit tests and 64 GameTests.
