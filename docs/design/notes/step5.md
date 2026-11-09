# Step 5: fishing on real water (commit 0f3211d, on top of 4cd23f7)

The build is clean: only the old deprecation warnings remain.
- Unit tests: 64 passed, 0 failed. That is 53 before, plus 11 in FishingWaterTest.
- GameTests: "All 58 required tests passed". That is 51 before, plus 7 new.
  - Log: impl/gt_5.log.
  - I ran the suite 4 more times (gt_5_rep1..3.log and one earlier run). All 58 passed every time.

The farm channel, farm_min/farm_max and FarmerJob are untouched. README and docs are untouched; step 7 does those.

## New classes and their public API

### logic/FishingWater.java (pure; FishingWaterTest)
- Constants:
  - `UNSEARCHED = 0, VALID = 1, NONE = 2` (the saved `spot` values);
  - `MIN_BODY 25`, `BODY_CAP 400`, `FARMLAND_CLEARANCE 5`;
  - `STAND_MIN 2`, `STAND_MAX 5`, `STAND_RISE_MIN 1`, `STAND_RISE_MAX 2`;
  - `NONE_MEMORY 48000`.
- `interface Cells` has these methods:
  - `waterSource`;
  - `aboveWater` (air or a lily pad);
  - `insideWater` (vanilla INSIDE_WATER);
  - `farmland`, `sky`;
  - `sturdyGround` (a sturdy top that is not farmland, leaves, ice or fluid);
  - `clear` (no collision and no fluid);
  - `sight(sx,sy,sz,bx,by,bz)`;
  - plus `default boolean standAllowed(x,y,z)`. See deviation 1.
- `record Spot(bx, by, bz, sx, sy, sz, boolean open, int body)`.
- `evaluate(c, x, y, z)` checks:
  - the water is a source with air or a lily pad above and a source below (at least 2 deep);
  - sky above it;
  - a body of at least 25;
  - no farmland within 5;
  - a stand exists.
  It returns a Spot or null.
- `stillFishable(c, bobber, stand)` is the cheap check made at every cast. It requires:
  - water at the bobber with air above;
  - a sturdy floor and 2 clear blocks at the stand;
  - no farmland within 5.
- `bodySize(c, x, y, z, cap)` floods 4 ways over surface sources on the bobber's layer.
- `farmlandNear(c, x, y, z, r)` checks a box of ±r sideways and from 1 below to 1 above.
- `openWater(c, x, y, z)` replicates FishingHook.calculateOpenWater exactly: the per-layer reduce and the layer order rules.
- `stand(c, x, y, z)` returns the nearest stand as {x, y, z}:
  - the order is horizontal distance², then rise, then dx, then dz;
  - Chebyshev 2..5 from the bobber and 1..2 above it;
  - sturdy ground, 2 clear blocks, `standAllowed` and `sight`.
- `waterKnown(spot, searchedAt, now)` returns true for VALID or UNSEARCHED, or when `now - searchedAt >= 48000`.

### work/FishingSpots.java (world adapter, search, checks, director upkeep)
- `fishery(data, base)` returns the base's FISHERY or null.
- `reach(base, foraging)` = `BaseZones.workRadius(base, FISHING_RADIUS)` (+40 when foraging).
- `search(level, data, base, around, radius, reach, Collection<Long> bad)` returns a Spot or null:
  - **Columns.** It scans every second column in a circle, with a rotating parity per base (a transient map). It reads loaded chunks only (`getChunkNow`, cached) and stays inside MapBounds.
  - **Water columns.** top = MOTION_BLOCKING_NO_LEAVES. The column must be a water source with air or a lily pad above, and top minus the OCEAN_FLOOR top block must be at least 2.
  - **Shore.** A cardinal column 2 or 4 away whose top is not water.
  - **Prefilter.** The zone rules and the 2-block bad-spot radius.
  - **Candidates.** One per 8x8 cell, the one nearest the centre. The 12 nearest are evaluated.
  - **Best.** Open water first, then the shorter distance from the nearer gate to the stand, then the larger body.
  - **Then castFurther** (deviation 2).
- `apply(data, w, spot)` writes origin = bobber, extent = stand, open, and VALID.
- `announce(level, base, w)` sends "<base>: fishing water found at x, y, z (open water|still water)".
- The cheap and full checks:
  - `stillFishable(level, data, base, w)`: the cheap check, made at every cast;
  - `waterAt(level, data, base, w)`: made at reel-in;
  - `stillValid(level, data, base, w)`: the full check, made every 20th cast. It applies the zones to the bobber and the current stand, runs evaluate and the cheap check, and recomputes `open`.
- `maintain(level, data, b, t)` and `maintain(..., boolean force)`:
  - if the FISHERY is not VALID, it searches with radius = reach = fishingRadius, at most every 6000 ticks per base (a transient map);
  - on success it applies the spot and announces it;
  - it never writes NONE.
- The zone rules (WorldCells.zoneOk / standAllowed) for both the bobber and the stand:
  - inside MapBounds;
  - within `reach` (Euclidean) of the centre;
  - `BaseZones.outsideAllWalls(d, p, 3)`, i.e. Chebyshev ≥ half+4 from every base;
  - `!inForeignArea(own, p, 8)`;
  - `!nearLair(p, 24)`.
  For the stand only, also `!WorkSafety.reserved(FISH_STAND)`.
- An unloaded cell is never water, ground, room or sight, and counts as farmland. Nothing loads a chunk.

## Changed files
- campaign/WorkplaceState.java:
  - FISHERY fields `int spot` (UNSEARCHED by default), `boolean open`, `long searched`, `List<Long> badSpots`;
  - `MAX_BAD_SPOTS = 32` and `addBadSpot(pos)`, which moves an entry to the end and drops the oldest;
  - NBT for FISHERY only: 'fishV' = 1, 'spot', 'open', 'searched', 'bad' (long[]);
  - no 'fishV' means legacy: spot = UNSEARCHED, with id, base, assigned, output and origin as loaded;
  - an out-of-range spot loads as UNSEARCHED, and 'bad' is capped at 32 on load;
  - the Type.FISHERY javadoc is rewritten.
- campaign/BaseBuilder.java:
  - `ensureAnimalAndFishingWorkplaces` → `ensureWorkplaces(data, base)`;
  - the PEN branch is unchanged;
  - a missing FISHERY is created as UNSEARCHED at the centre;
  - every FISHERY that is not VALID has its origin and extent moved to `base.center` (setDirty only on change);
  - the farm-channel derivation is deleted;
  - a "// step 6: a missing TREE_PLOT ..." hook is left;
  - buildSettlement calls `ensureWorkplaces`.
- campaign/FactionDirector.java:
  - settlement() calls `BaseBuilder.ensureWorkplaces` and then `FishingSpots.maintain(level, data, b, t)`;
  - needs() FISHERMAN additionally requires a FISHERY, `FishingWater.waterKnown(spot, searched, t)` and `!b.barren(FISHERMAN, t)`;
  - the retrain barren mark comes from step 1 (othersBusy).
- work/FishermanJob.java was rewritten:
  - **States.** STORAGE, SEARCH, FORAGE and FISH, saved and honoured. FISH turns into SEARCH on its first tick when the spot is not VALID.
  - **Job NBT.** state, forageDir, forageFails, restUntil, casts.
  - **Guard.** No base or no FISHERY gives "No fishing spot" and waitFor(400).
  - **Night (nightAway).**
    - It applies to a VALID spot when the state is FISH or STORAGE and remoteAtNight(extent) holds.
    - The fisherman unloads any cargo first, then waits at the storehouse: "Home for the night", markBusy, waitFor(600).
    - It also applies to forage legs. See deviation 6.
  - **STORAGE.** Unload, upgradeEquipment; no rod means waitFor(400). Then VALID goes to FISH, anything else to SEARCH.
  - **SEARCH.** It runs every 600 ticks:
    - found: apply, announce, markBusy, FISH;
    - not found: FORAGE, or NONE straight away when `!BaseZones.forageAllowed`.
  - **FORAGE.**
    - Resting: "No fishing water within reach", markIdle("no fishing water"), waitFor(1200). When the rest is over: SEARCH. See deviation 7.
    - Otherwise each leg targets `MapBounds.clamp(centre + 72·dir(k·45°), 16)`, with goTo reach 8 or a 2400-tick limit. Then it runs `search(npc pos, 40, reach R+40)`.
    - After 8 failures: NONE, searched = now, restUntil = now+6000, markIdle.
  - **FISH.**
    - Full backpack, 32 or more cargo, or no rod: STORAGE.
    - Pick-up takes only items within 3 of the stand that are not in water. It may walk to a dry item; one not reached within 200 ticks is left lying.
    - Travel: more than 1200 walking ticks, or `routeFailures() >= 3`, makes it a bad spot. See deviation 8.
    - At every cast it runs `stillFishable`, and every 20th cast `stillValid`. At reel-in it runs `waterAt`.
    - Any failure makes a bad spot: `addBadSpot(origin)`, UNSEARCHED, origin and extent back to the centre, then SEARCH. The status is "<why> - looking for other water".
    - The countdown is −1 a tick, plus −1 with 25% chance when `isRainingAt(bobber.above())`.
  - **Loot.**
    - `public static Entity lootEntity(boolean open, FactionNpc npc)` returns a cached, never-spawned `new FishingHook(EntityType.FISHING_BOBBER, level)` when `open && FISHING_TREASURE`, else the npc.
    - reelIn moves the hook to the bobber with setPos. It keeps ORIGIN, TOOL, luck, Angler, wear, XP and fishy_business, and adds ATTACKING_ENTITY = npc.
  - **Catch flight.** `static Vec3 catchMotion(from, to)`. See deviation 3.
  - **Info.** `workplaceInfo()` goes through held(). `public int casts()` exists for the GameTest.
- work/BlockWork.java: a new overload `pickUp(npc, keep, Predicate<ItemEntity> where)`.
- work/WorkSafety.java, reserved():
  - MINE_SITE: within 12 of a VALID fishery's origin or extent;
  - GROVE: within 6 of a VALID fishery's origin or extent;
  - FISH_STAND: within 8 of a MINE origin, as before;
  - a "// step 6" note covers the sapling and replant parts.
- info/BaseInfo.java, fisheryLine():
  - "Fishing spot #id at x, y, z (open water|still water), worker X, N caught";
  - "Fishing spot #id: looking for fishing water";
  - "Fishing spot #id: no fishing water within reach (searched N.N days ago)".
- command/FCCommands.java: the snapshot gets FISHERY origin, extent, spot (the string "VALID"/"NONE"/"UNSEARCHED"), open and searched.
- tools/sim.py: prints spot, origin, stand and open for the FISHERY. It prints ERROR for a VALID fishery within 28 (Chebyshev) of any non-destroyed settlement centre.

## Deviations from the spec and why
1. **Cells.standAllowed** is an extra method with a default of true. The stand search applies the stand zone rules itself, so a forbidden nearest stand does not reject water that has another allowed stand. With the zones applied after evaluate, the nearest stand would often sit inside the 28-block zone, and the spot would be lost.
2. **castFurther.** Candidates are shore columns: the shore test plus the per-cell candidate nearest the centre. Their 5x5 nearly always reaches the bank, so open water was almost never chosen.
   - When the best spot is not open water, the bobber is moved 1, then 2 blocks further from the stand, along the stand-to-bobber direction.
   - It moves only when the water there is open and every rule passes again: zones, bad spots and evaluate. The stand may change.
   - This costs at most 2 extra evaluations per search.
3. **Catch flight.** Vanilla's sideways speed (0.1 × distance) falls short at the 2-5 blocks a stand lies from its bobber, with no height difference: about 1.7 of 3 blocks. The catch fell back into the water, where the fisherman, as specified, does not go after it.
   - `catchMotion` keeps vanilla's upward formula, lifted until the arc clears the feet by 0.3.
   - It sets the sideways speed from a simulation of item gravity and drag, so the catch lands at the fisherman's feet.
4. **A water check at reel-in** (`waterAt`), not only at the cast. No catch comes from a block that is no longer water, and "within 2 casts" holds even when the water vanishes mid-wait. FISH without a rod in hand goes to STORAGE; the old code could cast with an empty hand.
5. **The adapter's waterSource** = a water source with empty collision, which is the same as vanilla INSIDE_WATER. Kelp and seagrass count; waterlogged stairs, slabs and mangrove roots are never bobbers.
6. **The night rule also covers forage legs.** It is consistent with woodcutting, where forage is not started at night, and with the config text, under which no work happens more than 16 beyond the walls at night. Forage-night waits do not call markBusy, so a fisherman already idle after a failed round keeps its idle time.
7. **When the NONE rest ends** the fisherman returns to SEARCH, and forages another round if needed. The spec does not say what follows the rest.
8. **Travel give-up counts walking ticks**, not wall-clock time. A gap while the job did not run counts as at most 101 ticks: food errands and fights do not count, while a "Cannot reach" retry wait (100) does.
9. **Catch chasing** has a 200-tick limit per item (leftLying), so an unreachable dry item cannot pin the fisherman.
10. **reserved(MINE_SITE)** now includes the VALID-fishery part, though nothing calls it until step 4. The spec's step 5 says "reserved uses VALID fisheries only"; I did the fishery parts of all three uses.
11. **GameTests.**
    - The pond tests need `skyAccess = true`. Without it the GameTest framework puts a barrier ceiling over the structure (GameTestInfo:269, encaseStructure). Barriers block motion, so MOTION_BLOCKING_NO_LEAVES is the ceiling and no water column is ever seen. **Any later GameTest that relies on heightmaps or sky needs skyAccess = true.**
    - The walking tests (fish_catch, fish_gone) use a pond sunk into the platform: water at rel y 0..1 over stone at rel −1, so the surface is level with the floor and the stand is on the floor with no climbing. The static tests use the spec's raised pond: water and rim at y 2..3, stand on the rim at y 4.
    - fishermanLeavesWhenTheWaterIsGone uses a 5x5 pond (body 25). When the bobber turns to stone the body is 24, so no other spot exists and the assertions hold.
    - The fishing tests set the VALID spot with `FishingSpots.search` + `apply`, the spot the fisherman would find itself, and assert it with the stand checks.
12. **legacyWorkplacesLoad** has only:
    - the FISHERY part: hand-built tag → load → save → load, keeping id, base, assigned, output and origin, spot UNSEARCHED, and 'fishV' written;
    - a round trip of NONE, searched, open and the 32-entry bad cap, with the oldest dropped;
    - the unknown type returning null.
    Comment hooks mark where steps 3 (MINE) and 6 (TREE_PLOT) add their parts.
13. **FishingWaterTest has 11 tests**: the 9 listed plus `bodyCountStopsAtTheCap` and `stillFishableNoticesWhatChanged`.
14. **sim.py** checks each VALID fishery against every settlement centre, not only its own.

## GameTests added (7; all pass)
- fishermanNeverPicksFarmIrrigation: workers_static, skyAccess. It runs 4 searches, covering all 4 parities.
- legacyFarmFisheryIsRelocated: workers_static, skyAccess. It checks that ensureWorkplaces adds no second fishery and moves the placeholder to the centre, then runs a forced maintain.
- fishermanCatchesAtAPond: batch fish_catch, timeout 900, skyAccess, Lure III rod. Passes when totalOutput > 0 and the backpack is not empty.
- fishermanLeavesWhenTheWaterIsGone: batch fish_gone, timeout 900, skyAccess.
- noFishermanRecruitedWithoutWater: workers_static. It checks the NONE+now gate, NONE again after 48000, VALID, and barren.
- openWaterUsesTheHookForLoot: workers_static.
  - The hook is a FishingHook with isOpenWaterFishing and is never in the world.
  - 400 rolls with the hook bring up treasure; 400 rolls with the npc bring up none.
- legacyWorkplacesLoad: workers_static. The FISHERY part only, as in deviation 12.

## Notes for the next steps
- **Step 3.**
  - Add the MINE part to legacyWorkplacesLoad; the hook comment is in the test.
  - The FISHERY code reads only MINE origins, through reserved(FISH_STAND): any MINE origin within 8 of a stand.
- **Step 4.**
  - `WorkSafety.reserved(d, own, p, MINE_SITE)` already refuses within 12 of a VALID fishery's bobber or stand.
  - `FishingSpots.fishery(data, b)` is available.
- **Step 6.**
  - In reserved(): FISH_STAND within 1 of a planted sapling, and MINE_SITE within 6 of planted or replant entries. The "// step 6" note is there.
  - The TREE_PLOT creation hook is in `BaseBuilder.ensureWorkplaces`.
  - Add the TREE_PLOT part to legacyWorkplacesLoad.
  - The forage pattern and the night handling in FishermanJob (nightAway, forageTargetFor) can serve as a model.
- **Step 7.** README:
  - drop "Fishermen stand at the farm's water channel";
  - describe fishing on real water: forage, the two-day memory, treasure in open water, the night rule.
  BALANCE.md constants (all marked PROPOSED in code):
  - FishingWater: MIN_BODY 25, BODY_CAP 400, FARMLAND_CLEARANCE 5, stand 2..5 away and 1..2 up, NONE_MEMORY 48000;
  - FishingSpots: STEP 2, CELL 8, EVALUATED 12, WALLS_MARGIN 4, FOREIGN_MARGIN 8, LAIR_RADIUS 24, BAD_RADIUS 2, FORAGE_EXTRA 40, DIRECTOR_SEARCH_TICKS 6000;
  - FishermanJob: SEARCH_TICKS 600, TRAVEL_GIVE_UP_TICKS 1200, TRAVEL_ROUTE_FAILURES 3, FULL_CHECK_CASTS 20, PICKUP_RANGE 3, FORAGE 72/40/8 directions/2400, FORAGE_REST_TICKS 6000, CHASE_TICKS 200;
  - WorkplaceState.MAX_BAD_SPOTS 32.
- **Step 8.** On the old world, a FISHERY at C+(15,-1,8) loads as UNSEARCHED. The first director period moves it to the centre, and maintain looks for water at once, because the transient map starts empty after a restart. The station shows "looking for fishing water" until water is found.
- **Totals.** Expect 58 GameTests and 64 unit tests, plus what later steps add.

## Review (commit fba3f33, on top of 0f3211d)

I checked every bullet of steps[4] against the code paths: the fishing section A-I, saveCompat FISHERY, shared 2 (reserved), shared 6 (director), and the step's tests. All of them are in, apart from what is handed on to steps 3, 6 and 7. The spec's factual-error list holds too:
- heightmaps: ChunkAccess.getHeight returns the top block, so the depth test is `top - floor >= 2`;
- lily pads are not motion-blocking;
- the open-water replica matches FishingHook.calculateOpenWater and getOpenWaterTypeForBlock block for block;
- NeoForge's FISHING param set allows ATTACKING_ENTITY.

The rest checked out as well:
- NBT is symmetric. A tag without 'fishV' loads as UNSEARCHED, keeping id, assigned, output and origin.
- Every job path guards base() == null.
- Nothing calls client classes.
- The static maps are touched on the server thread only.
- DigOut, NpcNavigator, FarmerJob and the farm layout are untouched. The only change to other jobs is the BlockWork overload.

Findings:
1. MAJOR, fixed: route failures left over from earlier walks counted against a new spot.
   - Cause: Job.routeFailures is reset only on ARRIVED. A forage leg that never reached its target (common, the leg ends on its 2400-tick limit) left the count at 3 or more. The first goTo toward a spot found at the end of that leg then returned MOVING, saw `routeFailures() >= 3`, and marked the spot bad straight away.
   - The same chain followed every "Could not reach the fishing spot": each next spot found was given up at once. The fisherman burned through the bad list and ended in NONE, wrongly recording that the base has no water.
   - Fix: FishermanJob keeps `travelFailBase`, the count when the walk to the stand began, re-based when an errand arrival resets the count. It gives up only on 3 new failures.
   - New GameTest fishermanIsNotPutOffByEarlierFailedRoutes (batch fish_resume, timeout 900, skyAccess). The job is loaded with state FISH and 5 leftover route failures (set by reflection). It must reach the pond and catch, and the spot must stay VALID with no bad entries. I checked that it fails on the old code ("Could not reach the fishing spot - looking for other water" on the first tick).
2. MINOR, fixed: forageFails was not reset when the director found water during a forage round, or during the rest. The next round then gave up after fewer than 8 directions. Also, a resting fisherman switched to FISH without markBusy, so it could be retrained on its way to new water.
   - Fix: a common waterKnownAgain() for found(), search() and forage(). It calls markBusy and resets state, bite, travel, restUntil, forageFails and forageTarget.
3. MINOR, fixed: FishingSpots.NEXT_DIRECTOR_SEARCH is static and never cleared. In single player, after opening a world with a lower game time, a base with the same id would not be searched until the old time came round, possibly days later. A stored time more than DIRECTOR_SEARCH_TICKS ahead is now treated as stale.
4. MINOR, fixed: the cached FishingHook was a static field holding its ServerLevel. That kept a closed single-player world in memory until another catch. lootEntity now makes a new, never-spawned hook per catch. This is a deviation from "a cached FishingHook"; the cost is one entity construction per bite.
5. MINOR, fixed: the full check every 20th cast did not re-check the line of sight from the current stand to the bobber. evaluate only proves that some stand sees it. FishingSpots.stillValid now checks `sight(stand, bobber)` too.
6. Test strength, fixed: fishermanLeavesWhenTheWaterIsGone checked "no other spot in the pond" in the very tick the spot was given up, when origin is always the centre, so that check could not fail. It now also requires that the spot is never taken up again, and succeeds only once the follow-up search records NONE.
7. Not changed (noted):
   - A lake whose stands are all unreachable could be cycled through slowly, at up to 1200 walking ticks per spot, with the 32-entry bad list forgetting the oldest. The fisherman counts as busy meanwhile. This is unlikely with DigOut, and the spec defines no limit.
   - Chasing a catch can still start DigOut after 2 failed routes, as the old chase did. It is limited to 200 ticks per item.
   - NONE / UNSEARCHED placeholders sit at the base centre. Nothing but the fishing code reads FISHERY positions; reserved() uses VALID only, and there are no map markers.

Totals after the review:
- unit tests: 64 passed, 0 failed (unchanged);
- GameTests: "All 59 required tests passed" (58 + fishermanIsNotPutOffByEarlierFailedRoutes), on 4 runs. Logs: gt_5r.log, gt_5r_1.log, gt_5r_2.log, and gt_5r_neg.log, which shows the new test failing on the old code.

For later steps:
- Expect 59 GameTests.
- In BALANCE.md, lootEntity is no longer "cached".
