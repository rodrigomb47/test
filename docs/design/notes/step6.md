# Step 6: fenceless woodland (commit c40b2d1, on top of eb5d37d)

The build is clean: only the old deprecation warnings remain.
- Unit tests: 109 passed, 0 failed. That is 103 before, plus 6 in the new ForestRulesTest.
- GameTests: "All 69 required tests passed". That is 64 before, plus the 4 spec woodcutter tests and 1 extra
  (groveSpotsFollowTheRules). legacyWorkplacesLoad gained its TREE_PLOT part.
  - It passed on 3 runs: gt_6.log, gt_6_run1.log and gt_6_run2.log (in impl/).
  - gt_6_neg.log is the negative check. Four sabotages were applied at once:
    - the replant species forced to OAK;
    - TOUCHES_STRUCTURE treated as OK;
    - the fence posts never found;
    - 'felling' dropped on load.
    Each of the 4 new woodcutter tests failed with its own message. The code was restored afterwards.

README, BALANCE and docs/ are untouched; step 7 does those. I touched the class docs of BaseLayout, SiteFinder and
TreePlotLayout only where they named the tree plot.

## New classes and their public API

### logic/ForestRules.java (pure; ForestRulesTest)
- `enum Species {OAK, BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, CHERRY}`. Its members:
  - `log()` gives "oak_log", `sapling()` gives "oak_sapling", `label()` gives "dark oak";
  - `saplingsPerTree()` is 4 for dark oak and 1 otherwise;
  - `byName(String)` returns null for an unknown name.
- `fromLog(id)` and `fromSapling(id)` take "minecraft:x" or a bare "x". Another namespace, a stripped log, a wood block,
  mangrove, a nether stem, azalea or a propagule gives null.
- `GROVE_ORDER = [BIRCH, SPRUCE, OAK, CHERRY, ACACIA]`.
- Constants: `REPLANT_PATIENCE 12000`, `REPLANT_GIVE_UP 48000`, `GROVE_SPACING 5`, `GROVE_WALLS_MARGIN 4`,
  `ROAD_HALF_WIDTH 3`, `ROAD_LENGTH 30`.
- `enum Action {SAME, SUBSTITUTE, WAIT, DROP}` and `record Replant(action, species, twoByTwo, count)`.
- `replant(want, big, sameCarried, substitute, waited)` follows woodcutting F:
  - dark oak is SAME, 2x2, 4 saplings, only when 4 are carried;
  - any other species is SAME with 1 sapling (never 2x2);
  - otherwise DROP at waited >= 48000, then SUBSTITUTE (1, never dark oak) at >= 12000 with a substitute, else WAIT.
- `substitute(ToIntFunction<Species> spare)` returns the first of GROVE_ORDER with a spare sapling, then JUNGLE, else null.
- `groveSpecies(spare)` returns the first of GROVE_ORDER, or null.
- `enum Ground {DIRT, FARMLAND, PATH, OTHER}`, `onGroveLattice(dx, dz)`, `onRoadStrip(dx, dz, half)`.
- `groveSpotAllowed(dx, dz, half, inBelt, ground, reserved)` is the pure grove rule. It requires:
  - the lattice;
  - Chebyshev >= half+4;
  - not on the road strip;
  - inside the belt;
  - DIRT;
  - not reserved.

### work/ForestScan.java (world adapter)
- `record Found(BlockPos stump, boolean big, Species species)`. A 2x2 trunk is found at its NW corner.
- `speciesOf(BlockState)`, `saplingItem(Species)`, `saplingBlock(Species)` and `saplingSpecies(Item)`.
- `findTree(level, data, base, npc, around, radius, badWild, scan, Predicate<BlockPos> skip)` follows woodcutting B:
  - **Columns.** Step 2 with rotating parity: phase = scan%4 gives (0,0), (1,1), (1,0), (0,1). The circle has radius
    `radius` around `around`. It reads loaded chunks only (`getChunkNow`, cached) and stays inside MapBounds.
  - **Column rule.** The top block, leaves not counted, is a log that fromLog knows. The same block runs down at most
    30. The trunk has 3..9 logs (dy 2..8), stands on #dirt, and has natural leaves near the top.
  - **2x2.** A square of the same log on #dirt is found at its NW corner.
  - **Skips.** badWild, claimedByOther, `skip` (the night rule), and protectedWood.
  - **Score.** dist(centre) + 0.5·dist(npc), horizontal; the lowest wins.
- `survey(level, data, own, start, boolean zones, boolean resume)` and `cells(level, data, own, Block log, zones)`. This
  is the step-2 adapter, moved from WoodcutterJob.surveyTree, which is deleted. `zones = false` applies only the shape
  rules; only a GameTest uses that.
- `inBelt(BaseState, BlockPos)` is the MIN/MAX box in x and z.
- `static final class GroveSearch`, held by each job:
  - `next(level, data, base)` returns a grove spot (the sapling cell) or null;
  - `reject(pos)` takes a spot out for good;
  - the lattice points come from the pure rule over the belt, the walls' side first (by Chebyshev);
  - at most 200 points are looked at per call; a found spot is cached while it still fits; after a full fruitless round
    it rests 600 ticks;
  - world checks: a loaded chunk; the ground from MOTION_BLOCKING_NO_LEAVES (one step down over a replaceable top such
    as a snow layer); its kind (farmland, path, #dirt, other); the spot air or replaceable with no fluid; canSeeSky; no
    log, leaves or sapling within 2 sideways at dy 0..4; neither the spot nor the ground in any planIndex;
    `!reserved(GROVE)`.
- `static final class TreeClaims` is a static ConcurrentHashMap from stump to {uuid, until}:
  - `CLAIM_TICKS = 1200`;
  - `claimedByOther(stump, me, now)`, `claim(stump, me, now)` (claims or renews) and `release(stump, me)`;
  - a claim more than CLAIM_TICKS in the future (a world with a later clock) counts as void.

### campaign/WorkplaceState.java (TREE_PLOT block)
- TREE_PLOT is labelled "Woodland" with maxTier 1. New fields:
  - `List<Long> planted` and `List<Replant> replant`;
  - `int felled` and `boolean legacyFence`;
  - `MAX_PLANTED 64` and `MAX_REPLANT 32`.
- `record Replant(long pos, String species, boolean big, long since)`.
- `addPlanted(pos)` moves an existing entry to the end and drops the oldest. `addReplant(r)` replaces an entry at the
  same place and drops the oldest.
- The NBT has 'woodV' 1, 'planted' (long[]), 'replant' (a ListTag of {p, s, big, at}), 'felled' and 'legacyFence'.
- A record without 'woodV' gets legacyFence = true and planted = `TreePlotLayout.spots(w, tier)`, with the tier clamped
  to 1..3. replant is empty and felled is 0.
- Every TREE_PLOT load sets tier = 1 and upgradeStep = -1, and caps both lists.

### work/WoodcutterJob.java (rewritten)
- States: CHECK, STORAGE, CHOP, LEAVES, COLLECT, PLANT, BONEMEAL, DISMANTLE.
- Guard: with no base or no TREE_PLOT it shows "No woodland" and waits 200 ticks.
- `decide()` takes the first step that applies:
  1. STORAGE (no axe and the check is due, cargo >= 48, or a full backpack);
  2. DISMANTLE when legacyFence;
  3. resume `felling` (at night a remote one waits for daylight);
  4. the replant queue;
  5. COLLECT around the last stump;
  6. the best tree;
  7. BONEMEAL;
  8. the grove;
  9. at night `nightWait`; otherwise finish the scan round (see deviation 2), then forage;
  10. "Saplings are growing", with the idle rule from O.
- CHOP:
  - Approach: inReach(spot, 4.0), else goTo(spot, 2.5). leafInTheWay applies near the tree or when
    ticksWithoutProgress > 100. Past 400 walking ticks the tree goes to badWild.
  - In reach: stop, take the claim (a failed claim means another tree; a started tree's `felling` is dropped), then
    survey with resume = (spot == felling).
  - Any verdict other than OK sends the tree to badWild before the first cut.
  - Cutting is top-down with the held tool, +1 XP per log. On the first log out of reach (7.5) it walks to within 1.5 of
    the stump for up to 200 ticks; logs still out of reach are deferred.
  - When the last bottom cell breaks (all 4 for a 2x2): felled++ and the replant entry {stump, species,
    big || dark oak, now}.
  - Final pass: goTo(spot, 0.3) for up to 200 ticks, then it cuts the deferred logs. Anything still out of reach is left.
  - Resume begins at the lowest log of a felled species in the stump column(s), up to +8.
- LEAVES: natural leaves (not in planIndex) within ±3 (±5 for a 2x2) up to +12, at 5.5 reach.
- COLLECT: the stump box inflated 6/2/6 (7 for a 2x2) plus 10 up. The 160/400-tick timeouts are kept.
- PLANT (replant entry or grove):
  - goTo within 3;
  - every cell must be air or replaceable with no fluid, on #dirt, not farmland, and canSurvive; otherwise the entry is
    dropped or the grove spot rejected;
  - it places the sapling(s) with the grass sound and a swing, consumes them, adds them to `planted` and removes the
    entry;
  - REGROWTH rolls growNow(spot, 2) on the NW corner.
- BONEMEAL: round-robin over planted saplings that are loaded and still saplings, those outside the belt first. Walk
  within 3, 10 ticks, growCrop, particles 1505.
- The grove runs when groveSize > 0, fewer than groveSize planted saplings lie in the belt, and a GROVE_ORDER sapling is
  spare beyond the queue's reservations.
- DISMANTLE:
  - the posts are the union of the tier 1..3 rings that are OAK_FENCE now and not in any planIndex; when any ring cell
    is not loaded, it waits;
  - it takes the nearest post, walks within 3, and breaks it with the Breaker (the fence goes into the backpack);
  - a post is skipped after 300 walking ticks or 3 new route failures;
  - when none is left it sets legacyFence = false and sends "<name> takes down the old tree-plot fence".
- STORAGE: unload (keep 8 saplings per species and 16 bone meal), upgradeEquipment and the axe notice. It takes each
  queued species' saplings (1, or 4 for dark oak, minus carried). If the grove is short and fewer than 4 GROVE_ORDER
  saplings are carried, it takes up to 6 in GROVE_ORDER. Bone meal < 8 is topped up to 16.
- Forage:
  - it goes from the centre to `MapBounds.clamp(centre + 72·dir(k·45°), 16)`, reach 8 or 2400 ticks, then
    `findTree(npc pos, 40)`;
  - forageDir advances only on a miss, so a forest that was found is visited again;
  - after 8 misses it rests 6000 ticks;
  - it never forages for GameTest bases or at night.
- Idle: `markIdle("no trees or saplings")` when forage rests (or is not allowed), planted and replant are empty, and no
  sapling is carried or stored. Any real work calls markBusy.
- Job NBT v2: v, state, spot, felling, big, badWild (FIFO 128) and forageDir/forageFails.
- Test accessors: `stateName()`, `spot()`, `felling()`, `gaveUp(stump)`.
- PROPOSED constants (for BALANCE.md in step 7):
  - CARGO_LIMIT 48, TRUNK_REACH 7.5, CHOP_REACH 4.0, APPROACH 2.5, WALK_GIVE_UP_TICKS 400;
  - CLOSER_REACH 1.5, CLOSER_TICKS 200, STUMP_COLUMN 0.3, FINAL_PASS_TICKS 200;
  - RESCAN_TICKS 600, SCAN_PATTERNS 4, PATTERN_TICKS 20, MAX_BAD_WILD 128, SAPLING_TRIP_TICKS 2400;
  - FORAGE 72/40/8/2400, FORAGE_REST_TICKS 6000, POST_GIVE_UP_TICKS 300, POST_ROUTE_FAILURES 3;
  - SPOT_GIVE_UP_TICKS 600, SPOT_RETRY_TICKS 2400, KEEP_SAPLINGS 8, KEEP_BONE_MEAL 16;
  - ForestScan: STEP 2, MAX_TRUNK_WALK 30, GroveSearch POINTS_PER_CALL 200, REST_TICKS 600, CLEARANCE 2,
    CLEAR_HEIGHT 4, TreeClaims.CLAIM_TICKS 1200;
  - WorkplaceState MAX_PLANTED 64, MAX_REPLANT 32.

## Changed files
- **campaign/BaseBuilder.java.**
  - There is no fence ring and no workplaceManaged.
  - The woodland comes from `newWoodland(base)`: origin centre+(-32,0,0), facing WEST, legacyFence false.
  - 4 starter oak saplings stand at (cx-35, cz±5) and (cx-40, cz±5), with dirt below only if the ground is not #dirt.
    The first two are grown (advanceTree up to 6 times), and all 4 are in planted.
  - `ensureWorkplaces` creates a missing TREE_PLOT.
  - The west pad levelling, the skirt and MIN/MAX are unchanged.
- **work/CookingJob.java.** `woodReserve` is always LOG_RESERVE (16). SmelterJob is unchanged (24); only its comment changed.
- **work/WorkSafety.java.** `reserved()`:
  - MINE_SITE: within 6 of any woodland's planted saplings or replant entries (`nearGrownTrees`; TreePlotLayout is no
    longer used);
  - FISH_STAND: within 1 of a planted sapling (SAPLING_STAND_RADIUS);
  - GROVE: the road strip now uses `ForestRules.onRoadStrip`.
- **info/BaseInfo.java.** `woodlandLine` gives "Woodland #id (worker): F trees felled, P saplings growing (G in the
  grove), Q stumps to replant (N dark oak(s) waiting for 4 saplings), old fence being taken down; output N".
- **command/FCCommands.java.** The snapshot has, for TREE_PLOT: origin, felled, planted (count), replant (count) and
  legacyFence.
- **tools/sim.py.**
  - The stock line ends with `logs=<sum of every *_log> {per species} saplings=<sum of every *_sapling> {per species}`.
    oak_log and oak_sapling left the fixed key list.
  - A TREE_PLOT line shows origin, felled, planted and replant, and "OLD-FENCE" while the old fence stands.
- **structure/TreePlotLayout.java.** `insideInner` is deleted, and the class doc says it is for legacy only.
- **gametest/FCGameTests.java.** treeSurveyLeavesBuiltAndProtectedWood now calls ForestScan.survey (zones true or false);
  its assertions are unchanged.

## Tests
- **ForestRulesTest (6).** speciesFromLog, darkOakNeedsFourAndIsNeverPlantedAlone, bigSpruceIsReplantedAsOneSapling,
  replantTimeline, groveLatticeAndExclusions, groveOrderNeverJungleOrDarkOak.
- **woodcutterFellsATreeAndReplantsTheSameSpecies.** Batch wood_fell, 1200 ticks, skyAccess. The spec setup, with the
  woodcutter at rel (8,2,12). It asserts all of these:
  - a BIRCH sapling at the stump;
  - no birch log in the box 15..21 x 2..9;
  - 5 or more birch logs held or stored;
  - planted contains the stump;
  - felled == 1 and replant empty.
- **woodcutterLeavesProtectedWoodAlone.** Batch wood_protect, 1000 ticks, skyAccess. The trees are (a) a persistent
  pillar at (19,6), (b) a birch on stone at (19,18), (c) an oak at (12,19) with a branch log at (13,4,19) touching planks
  at (14,4,19), and (d) a natural oak at (5,18).
  - Every tick, the logs of (a), (b) and (c) must stand.
  - It passes when (d) is gone, felled == 1, and the job has given up on (c) (`gaveUp`), so (c) was really surveyed.
- **woodcutterDismantlesAnOldFence.** Batch wood_fence, 1500 ticks. The spec setup: origin rel (5,2,12), facing the
  rotated EAST, 47 posts. The woodcutter has an iron axe. Once legacyFence is false, no post is left and 47 or more
  fences are held or stored.
- **woodcutterResumesAfterReload.** Batch wood_resume (see deviation 3), 600 ticks, skyAccess. Once `felling` is set,
  save and load into a new job: felling and spot are kept, the state is CHOP, and v is 2. An old tag (no v, state
  UPGRADE, spot) loads as CHECK with spot null, and so does an old CHOP tag.
- **legacyWorkplacesLoad, TREE_PLOT part.** The old tier 2 plot with upgradeStep 3, origin (100,64,0), facing west,
  becomes legacyFence true, upgradeStep -1 and tier 1, with planted equal to the 9 tier-2 grid spots. The fields hold
  over 2 rounds, and 'woodV' is written. A new-format round trip covers felled, legacyFence, the planted cap of 64
  (newest kept), the replant cap of 32 (newest kept, one entry per place) and the entry fields.
- **groveSpotsFollowTheRules (extra).** workers_static, skyAccess. The belt is widened to ±11. Six lattice spots get
  grass or farmland:
  - one valid spot;
  - farmland;
  - Chebyshev 5;
  - the road strip;
  - next to a sapling;
  - beside a MINE origin.
  Only the valid one is offered; it stays cached; once planted nothing is offered. reserved(GROVE) holds for the mine
  and the road strip.

## Deviations from the spec and why
1. **`felling` is kept until the final pass is done**, not cleared when the stump breaks. The replant entry and
   felled++ still happen exactly when the last bottom cell breaks. That moment comes only once: a reload after it finds
   the stump already gone, so no second entry is made. Spec D's resume ("from the lowest SAME_LOG within the stump
   column(s) up to +8") only makes sense for this case: logs deferred above a cut stump. Keeping `felling` means a reload,
   a full backpack or a fight during the final pass still finishes those logs.
2. **Scan cadence.** With the rotating parity, a lone 1x1 trunk is only seen by one scan in four. At one scan per 600
   ticks it could take 1800+ ticks to be found, during which the woodcutter would forage straight past it, and the
   GameTests would time out.
   - After an empty scan, the next pattern follows 20 ticks later; after a full empty round of 4 the 600-tick cadence
     returns.
   - Forage starts only after a full empty round; meanwhile the status is "Looking for trees", with waitFor(20).
   - The cost: at most 4 scans per ~680 ticks where no tree grows at all (about 3200 columns each, loaded chunks only).
   - A found tree resets the round. A felled, rejected or given-up tree sets nextScan = now, as in step 2.
3. **woodcutterResumesAfterReload runs in its own batch, wood_resume** (the spec says workers_static). It runs the real
   job until the first log is cut, which takes many ticks. workers_static holds only synchronous checks.
4. **ForestScan.survey keeps a `zones` flag.** The existing step-2 GameTest checks "the same tree is fine where the zone
   rules do not apply". Production code always passes true.
5. **Loading.**
   - An old tag (no 'v') keeps its 'felling'. The spec only says the spot is dropped; 'felling' had the same meaning in
     step 2, and the resume survey re-checks contact, height and protectedWood.
   - PLANT, BONEMEAL and DISMANTLE load as CHECK, because their targets and species are transient and picked again.
     LEAVES finds its leaves again on the first tick.
6. **prunePlanted** drops loaded entries that are no longer saplings, at most every 200 ticks while deciding. The spec
   prunes "when visited". This keeps the grove count and the station line accurate.
7. **A planting spot or sapling not reached in 600 walking ticks** is skipped for 2400 ticks (`skipUntil`). Without
   this, a stump on an island would be chosen again every tick.
8. **The night rule.**
   - A started tree beyond half+16 waits for daylight; the woodcutter goes home.
   - DISMANTLE is not limited at night. The old plot lies at Chebyshev 32..45, so part of it is beyond the night margin;
     the spec does not mention it.
   - A forage leg under way is dropped at night.
9. **The fence message is sent when the last post is down.** The spec gives the text "<name> takes down the old
   tree-plot fence" for when none is left.
10. **A dark oak entry is always big**, so it is replanted as a square, even when the trunk found was 1x1 (spec:
    "Dark oak is always 2x2").
11. **The extra GameTest groveSpotsFollowTheRules.** No spec test exercises the grove in the world; its GameTest bases
    have no grove spots by design.
12. **The substitute excludes the wanted species** and counts only saplings beyond the other entries' reservations.
13. **TreeClaims voids a claim more than CLAIM_TICKS in the future**, after a world with a later clock. This is the same
    stale-clock guard as FishingSpots in step 5.
14. **The GameTest count.** 64 → 69: 4 spec tests and 1 extra. The spec's "63 total" was already off (see step 1).

## Notes for the next steps
- **Step 7 (docs).**
  - README "Tree plots" becomes Woodland:
    - no fence; fells natural trees within forestRadius 64 of the centre;
    - the natural rule (same log, 3-9 logs, on #dirt, natural leaves, no structure or other log touching, nothing above
      +8, outside walls+2, foreign land +8, lairs +16, known villages 32);
    - replants the same species (dark oak as 4, big spruce and jungle as 1, a substitute after 12000, forgotten after
      48000);
    - bone meal; a grove of 8 in the belt (birch > spruce > oak > cherry > acacia);
    - old fences taken down; forage 72/40; the night rule.
  - Starting bases: 4 starter oak saplings on the west pad, 2 grown, no fence.
  - Logistics: the wood reserve is a flat 16 (charcoal 24).
  - Retraining reason "no trees or saplings".
  - BALANCE.md: the constants listed above.
  - The station line format; the tools/sim.py stock line.
  - WorkplaceState field docs for origin and facing are updated; the Type docs are done.
  - CookingJob's wood reserve doc is done.
- **Step 8 (campaign).** Watch for:
  - **New bases.** The woodcutter fells the 2 starter oaks (Chebyshev 35 from the centre), then replants them. The grove
    fills up to 8 saplings on lattice spots of the west and east belt (|dx| 30..55 or 30..75, |dz| 5..25).
  - **Old world_run3.** Each TREE_PLOT loads with legacyFence. The woodcutter takes the fence down first, which should
    take minutes, not a day. The station line shows "old fence being taken down", and the faction gets the message.
    Old fence items land in storage. sim.py prints OLD-FENCE until it is down.
  - **Wood.** `felled` keeps rising, and logs of several species show in the stock line. No replant entry is ever a
    lone dark oak, since dark oak entries are big and planted as 4.
  - **Cost.** The tick time while woodcutters scan. Expect 4 scans in ~80 ticks every ~680 ticks in treeless land, and
    1 per 600 otherwise.
- **Totals.** 109 unit tests and 69 GameTests.

## Review (commit 5f88643, on top of c40b2d1)

I checked every bullet of steps[5], the whole woodcutting section (A-P), saveCompat TREE_PLOT, shared 2, 13 and 14,
and the woodcutter tests against the code paths. I read WoodcutterJob, ForestScan, ForestRules, the TREE_PLOT block
of WorkplaceState, BaseBuilder, WorkSafety, CookingJob, SmelterJob, BaseInfo, FCCommands, sim.py and the new
GameTests, plus the Job, NpcNavigator, BlockWork.Breaker, ChunkLoading and TreeShape code they rely on.

What holds:
- Save and load are symmetric. Job NBT v2 and the woodland block round-trip, and old tags convert.
- Every base()==null and missing-TREE_PLOT path idles.
- The heightmap use is right: `ChunkAccess.getHeight` gives the top block, and snow layers do not block motion.
- No client classes are used. TreeClaims is a ConcurrentHashMap.
- DigOut, NpcNavigator and the other jobs are unchanged.
- sim.py runs on a sample dump with TREE_PLOT fields.

Results:
- Unit tests: 109 passed, 0 failed.
- GameTests: "All 70 required tests passed", on 3 runs (gt_6r_2.log, gt_6r_3.log, gt_6r_4.log; gt_6r_1.log was before
  the last two fixes). That is 69 before, plus the new woodcutterGoesBackToAStartedTree.
- Negative check (gt_6r_neg.log): with the old step 3 put back, the new test fails.

### Findings
1. MAJOR, fixed. A half-felled tree was forgotten after a trip to unload.
   - decide() step 3 resumed `felling` only if `resumeStart()` saw a log, and that needs the chunk loaded.
   - A woodcutter whose backpack fills mid-tree (CHOP goes to STORAGE with `felling` kept) walks home, and its
     traveller ticket moves with it. A tree outside the belt (|dz| > 25, or more than 78 east) is unloaded by the time
     it decides again. The felling was then dropped, leaving a leafless stub that the column rule never finds again,
     with no felled count and no replant entry.
   - Also, when the stump fell just before the backpack filled, the tree skipped LEAVES and COLLECT.
   - Fix: step 3 always goes back (CHOP). CHOP decides there, where the chunk is loaded: it resumes, or with no log
     left it goes through finishTree to the canopy and the drops.
   - New GameTest woodcutterGoesBackToAStartedTree (batch wood_return): a job loaded with `felling` on a stump with
     nothing left walks back, picks up the drops around it, and counts nothing twice.
2. MAJOR, fixed. Woodcutters with no trees could never be retrained.
   - forage() called markBusy() on every tick of a forage leg, so the idle time was reset each round. Forage rests
     6000 ticks, and retraining needs 12000 idle. The night wait's markBusy also reset it every night.
   - So spec O's 'no trees or saplings' retraining could never happen where foraging is allowed (that is, outside
     GameTests).
   - Fix: forage legs leave the idle state alone, as the fisherman's do (step 5, deviation 6). nightWait(true) marks
     the woodcutter busy only for a started tree waiting for daylight; the step-9 night wait leaves the idle time as
     it was.
   - Deviation from spec M's literal 'markBusy': idle is only ever set by step 10's full condition, so this cannot
     retrain a woodcutter that has trees or saplings.
3. MAJOR, fixed. Grove trees beyond the forest radius were never felled.
   - The grove lattice covers the whole MIN/MAX belt, up to cx+78 and cz±25, which is about 79 from the centre. The
     tree search is a circle of forestRadius 64.
   - Grove spots fill monotonically, because felled trees are replanted in place. Once the nearer spots are taken,
     new grove trees grew where only foraging could ever reach them.
   - Fix: GroveSearch.lattice keeps only points within BaseZones.workRadius(base, forestRadius).
   - groveSpotsFollowTheRules now also asserts that no spot is offered when the radius (testRadius 11) stops short of
     the fitting spot (11.2 away). The rest of the test runs with testRadius 15.
   - Deviation: the grove rule gains this extra limit, which follows from spec H's "felled and replanted like any tree".
4. MINOR, fixed. The replant queue cost too much per tick.
   - On WAIT, `FactionStorage.count` (a scan of every chest) ran once per waiting entry, on every decide() call, once
     a sapling trip was due and storage lacked the sapling.
   - decide() runs every tick during a forage walk, so that was up to 32 chest scans per tick.
   - Fix: the count is made at most once per species per call, and storage is looked at again at most every
     STORAGE_LOOK_TICKS = 200 when no trip results.
5. MINOR, fixed. A cached tree that was gone could stall the woodcutter for up to 600 ticks.
   - When treeCache was dropped for any reason other than leaveTree or finishTree (taken by another woodcutter, cut by
     a player, remote at night), nextScan could still be ~600 ticks away, because a successful scan sets +600.
   - With emptyScans = 0 the woodcutter then sat in "Looking for trees" until then.
   - Fix: dropping the cache sets nextScan = now.
6. MINOR, fixed. A forage scan read only one of the 4 column patterns, so a lone tree at the forage point was missed
   3 times in 4. Fix: at the end of a leg it reads the patterns in turn until one finds a tree (at most about
   4 x 1260 columns per leg). ForestScan.PATTERNS is new and public.
7. MINOR, fixed. An unreachable grove spot stayed cached in GroveSearch.
   - It was retried every 2400+600 ticks, and no other grove spot was ever offered.
   - Fix: when PLANT gives up walking to a grove spot, it calls grove.reject(spot). Stumps still use skipUntil.
8. MINOR, fixed. skipUntil grew without bound. Expired entries are now dropped once it holds 64 (skipFor()).
9. MINOR, fixed. A second woodcutter of a base whose woodland is held showed "No woodland" forever and was never
   idle, so it was never retrained (this was already so before step 6). It now marks itself idle with "no woodland of
   its own", only when it has a base.

Not changed (noted):
- The final pass's 0.3 reach can use its 200-tick budget without a vanilla navigator stop that close.
  - That costs only time: from within 0.45 of the stump column, and even 1.5 away, +8 is within 7.5.
  - Deferred logs still out of reach are dropped as the spec says.
- A stump deferred as out of reach (the NPC above it) is left uncut, with no replant entry. This is rare.
- DISMANTLE sends its message even when some posts were skipped as unreachable.
- Old fence ring cells outside the platform in the GameTest (the tier 3 ring reaches rel x 29) are read but never
  hold fences.
- Same-species trees whose branches touch (acacia, fancy oak) are felled as one tree with one replant entry.
- Bone meal can be spent on a sapling that has no room to grow; the vanilla item is consumed the same way.

For step 7 (docs), in addition to the list above:
- forage legs and nights do not reset the idle time;
- grove spots lie within forestRadius as well as the belt;
- new constant STORAGE_LOOK_TICKS 200;
- idle reason "no woodland of its own";
- totals: 109 unit tests (unchanged) and 70 GameTests.
