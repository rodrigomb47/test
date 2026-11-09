# Step 1: shared groundwork (commit 1109589, on top of f90ec43)

Build is clean (only the old deprecation warnings). Unit tests: 40 passed, 0 failed (none added; step 1 lists none).
GameTests: "All 48 required tests passed" (46 before this step + 2 new). Log:
impl/gt_1.log

## What was implemented

### work/Job.java (all jobs)
- `private long idleSince = -1; private String idleReason = ""` (transient, not saved).
- `protected void markIdle(String reason)`: sets idleSince only if unset; always stores the latest reason.
- `protected void markBusy()`: clears both.
- `public boolean idleFor(long ticks)`, `public String idleReason()`. The reason should read after "has had",
  e.g. "no place to dig a mine", "no trees or saplings", "no fishing water". The director prints
  "<name> (<old role>) has had <reason> for half a day and becomes a <role> (<need>)".
- `protected boolean beforeErrand()` (default true). Called in survival() before BOTH atStorage() errands (tool trip,
  meal trip) as `if (!beforeErrand() || !atStorage()) return true;`. Returning false = "still getting out, keep the
  errand pending". Step 3's MinerJob override goes here (MineSites.mineAt + leaveMine).
- `protected boolean isNight()`: dayTime % 24000 in [13000, 23000) (constants NIGHT_START/NIGHT_END).
- `protected boolean remoteAtNight(BlockPos p)`: FCConfig.REMOTE_WORK_IN_DAYLIGHT_ONLY.get() && isNight() &&
  Chebyshev(base.center, p) > half + 16 (NIGHT_SAFE_MARGIN). half = testHalf if >= 0, else BaseLayout.HALF for
  settlements, LairLayout.HALF for lairs. Computed inline with a "// step 2: BaseZones..." note: step 2 should
  switch it to BaseZones.chebyshev/BaseZones.half. Not called by anything yet.
- `protected int routeFailures()`: accessor for the existing counter (reset to 0 on ARRIVED).

### work/MinerJob.java
- Its own idleSince/idleFor are gone. markBusy() where it reset idleSince (cave branch, normal work);
  markIdle("nothing left to dig in mine #N and no cave to work") in the fully-worked-out branch;
  markIdle("no mine or cave to work") in the 'No free mine to work' branch. Step 3 replaces these with the SITE
  reasons ('no place to dig a mine', 'the ground here is too shallow for a mine', 'every mine site of the base is
  taken').
- workplaceInfo() now uses Workplaces.held(MINE) (no claim as a side effect).

### work/Workplaces.java
- `claim(npc, type, ok)`: own record returned only if ok.test(w), otherwise skipped (`continue`); a free one must
  pass ok and have !holderValid.
- `static NpcRole roleFor(Type)`: MINE/CAVE -> MINER, TREE_PLOT -> WOODCUTTER, FISHERY -> FISHERMAN, else null.
- `public static boolean holderValid(CampaignData, WorkplaceState)`: assigned != null, record exists, alive, and
  (roleFor == null || record.role == roleFor). `unworked = !holderValid`.
- `public static void release(CampaignData, WorkplaceState)`: assigned = null + setDirty (only when it was set).
- `public static void releaseAll(CampaignData, UUID)`: releases every workplace in data.workplaces held by it.
- CaveMinerJob's worked-out cave hand-back now calls Workplaces.release (same effect).

### npc/FactionNpc.java
- role(newRole): on a real change and on a ServerLevel, Workplaces.releaseAll(CampaignData.get(server), uuid).
  readAdditionalSaveData still sets DATA_ROLE directly, so loading keeps workplaces.

### campaign/BaseState.java
- `public final Map<String, Long> barrenUntil` keyed by NpcRole.id, saved as compound "barrenUntil" (missing = empty).
- Helpers: `long barrenUntil(NpcRole)`, `boolean barren(NpcRole, long now)` (now < until),
  `void markBarren(NpcRole, long until)` (merge with max).
- `public transient int testHalf = -1, testRadius = -1` (never saved). MIN/MAX, contains(), deepInside() unchanged.

### campaign/FactionDirector.java
- 'replacing the missing X' loop skips a role while b.barren(role, t).
- recruitment(): the `top.role() != NpcRole.MINER` gate is gone; retrainIdleWorker(level, data, f, b, top, t).
- retrainIdleWorker: any alive non-combatant of the base whose role != top; eligible when job.idleFor(12000) or
  job.missingToolFor(12000). When idle and NOT toolless: b.markBarren(oldRole, t + BARREN_TICKS (48000)).
  Then r.role = top, n.role(top) (which releases workplaces). Message uses idleReason ("nothing to do" if empty;
  "no tool for the job" when only toolless).
- New public constant FactionDirector.BARREN_TICKS = 48000 (RETRAIN_IDLE_TICKS stays 12000).
- NOT done here (later steps): FISHERMAN gating (step 5), 'an open mine has no miner' (step 3), ensureWorkplaces /
  FishingSpots.maintain (steps 5/6).

### work/BlacksmithJob.java
- membersNearBase() replaced by members(): roster entries alive && baseId == b.id, resolved with
  level().getEntity(uuid) as a living FactionNpc, any y, horizontal (Euclidean) distance <= CREW_RANGE (256) from
  the centre. Unused AABB import removed. Order 1 (mine upgrade payment) is still there: step 3 deletes it.

### config/FCConfig.java
- New section [workplaces] (between achievements and recruitment): MINE_SITE_RADIUS (mineSiteRadius 64, 40..128),
  MAX_OPEN_MINES (maxOpenMines 3, 1..8), FOREST_RADIUS (forestRadius 64, 32..128), GROVE_SIZE (groveSize 8, 0..32),
  FISHING_RADIUS (fishingRadius 64, 32..128), FISHING_TREASURE (fishingTreasure true),
  REMOTE_WORK_IN_DAYLIGHT_ONLY (remoteWorkInDaylightOnly true). Only REMOTE_WORK_IN_DAYLIGHT_ONLY is read (by
  Job.remoteAtNight, itself unused so far). docs/factionclash-common.toml is NOT regenerated (step 7).

### gametest/GameTestBases.java (new)
- `GameTestBases.register(h, faction, half, radius, timeoutTicks)` and an overload with `BlockPos storageStand`
  (relative; default (4,2,4)). Creates a SETTLEMENT BaseState with id 990100+n (static counter), centre rel
  (12,2,12), SPAWN = centre, STORAGE_STAND, MIN = centre-(half+1, 1, half+1), MAX = centre+(half+1, 8, half+1),
  testHalf/testRadius set, the 4 fakeBase chests at rel (2..5,2,2) (FOOD first, then GENERAL), put in data.bases.
  Schedules handle.cleanup() with h.runAfterDelay(timeoutTicks-2).
- `GameTestBases.Handle` (fields helper, data, base):
  - `FactionNpc npc(x, z, role)`: spawn at rel (x,2,z), faction, role, then register;
  - `FactionNpc register(FactionNpc)`: setBaseId + Recruitment.register (roster entry) and remembers it;
  - `WorkplaceState addWorkplace(WorkplaceState)` (origin defaults to the centre) and
    `addWorkplace(Type, BlockPos absOrigin)` (extent = origin, facing EAST) via data.addWorkplace;
  - `Runnable guard(Runnable)`: runs a check, cleans up and rethrows if it throws;
  - `onEachTick(Runnable)` = helper.onEachTick(guard(r));
  - `assertTrue(cond, msg)`, `succeed()`, `fail(msg)`: all clean up first;
  - `cleanup()`: idempotent; removes the base's workplaces from data.workplaces, the base from data.bases, the
    roster entries of registered NPCs, AND discards those NPC entities (addition to the spec, so no phantom
    workers keep running).
  Note: a failing h.assertTrue inside an h.onEachTick lambda ends the test without running the scheduled cleanup
  (vanilla stops ticking a finished test), so job tests must use handle.onEachTick / guard / handle.assertTrue, or
  try/finally for synchronous tests.

### GameTests added (gametest/FCGameTests.java, batch "workers_static", template platform, timeout 40)
- retrainingReleasesTheWorkplace: fisherman claims a FISHERY, n.role(MINER) -> assigned == null; a TREE_PLOT
  assigned to a living SCOUT record is unworked and a registered woodcutter claims it.
- claimSkipsOwnWorkedOutMine: miner holds MINE A (workedOut = true, in memory only; MINE does not save workedOut
  until step 3) and MINE B is free -> claim(MINE) returns A, claim(MINE, w -> !w.workedOut) returns B and assigns it.

## Deviations from the spec
1. workplaceInfo() uses held() in MinerJob, WoodcutterJob, FishermanJob and FarmerJob, and WoodcutterJob.keep()
   uses held() (shared 3: "Every workplaceInfo() and keep() uses held()"). The step 1 list did not name these, but
   shared 3 does and it only removes claim side effects. Steps 5 and 6 rewrite those jobs anyway: keep held() there.
2. markBarren merges with max instead of a plain put (never shortens an existing mark).
3. Barren is set only for "idle and not toolless" (spec: "for an idle (not toolless) worker"); if a worker is both,
   the message still uses the idle reason (the old code also put the idle-miner text first).
4. GameTestBases.cleanup also discards the registered NPC entities; the handle also offers guard/onEachTick/
   assertTrue/succeed/fail helpers.
5. Spec says the GameTest count was 47 at f90ec43; it was 46 (`grep -c "@GameTest("`; the README claims 40). It is
   now 48. Expect 46 + new tests in later steps (spec target "63" is off by one accordingly).
6. MinerJob idle reasons for the current fixed-mine code are "no mine or cave to work" and "nothing left to dig in
   mine #N and no cave to work" (step 3 replaces them).

## Notes for later steps
- Step 2: replace the inline half/Chebyshev in Job.remoteAtNight with BaseZones (marked "// step 2").
  BaseZones.half(b) must honour b.testHalf (field exists now); workRadius/forageAllowed use b.testRadius.
- Step 3: MinerJob.beforeErrand override; markIdle only in SITE/no-site and 'every mine site taken'; MINE NBT gets
  workedOut (claimSkipsOwnWorkedOutMine already uses the in-memory field); MineSites.retire should call
  Workplaces.release; delete BlacksmithJob order 1; 'an open mine has no miner' should use Workplaces.unworked
  (now role-aware).
- Steps 5/6: FishermanJob/WoodcutterJob markIdle reasons 'no fishing water' / 'no trees or saplings'; the director's
  FISHERMAN need should check `t >= b.barrenUntil(NpcRole.FISHERMAN)` (or `!b.barren(NpcRole.FISHERMAN, t)`); use
  routeFailures() and remoteAtNight(); use GameTestBases.register(h, faction, 2, 11, timeout) with their own batch.
- FactionDirector.BARREN_TICKS (48000) and RETRAIN_IDLE_TICKS (12000) are public constants for docs (step 7).

## Review (commit 535626b, on top of 1109589)

I checked every bullet of steps[0] and of shared 3-7, 11 and 14 against the code paths. All of them are in, apart
from the items the notes above already hand on to later steps (BlacksmithJob order 1 and 'an open mine has no miner' go
to step 3; the FISHERMAN gate goes to step 5). I also checked these, and none needed a change:
- Workplaces.claim honours the predicate, and its only predicated callers (the MinerJob CAVE claims) run with no cave held.
- Only Workplaces writes `assigned`. CAVE is held only by miners, so `roleFor` is safe.
- NBT is symmetric, and an old tag without the new key loads as empty.
- role() runs releaseAll only on the server, and readAdditionalSaveData still bypasses it.
- members() costs O(roster) per planning pass.
- Both new tests are synchronous, and neither is vacuous: NpcRecord.alive defaults to true.
- DigOut and NpcNavigator are untouched.

Findings and what was done:
1. MAJOR, fixed: barren was set for any idle retrain, even when other workers of that role were still busy. Example:
   a surplus miner idle with 'no mine or cave to work', or later 'every mine site of the base is taken'. If the busy
   miners then died, 'replacing the missing miner' stayed off for up to 48000 ticks. The free mine got no miner,
   because 'the constructed/open mine has no miner' needs living(MINER) > 0.
   - Fix: FactionDirector.othersBusy. The retrain marks barren only when no other living worker of the old role at
     the base is busy. An NPC that is not loaded, or whose job is not idleFor(12000), counts as busy.
   - Barren only matters when living == 0, so the truly barren case is unchanged: the last idle worker sets the mark.
   - Deviation from the spec's plain 'idle -> barren'. The cost is at most one wasted trial recruit, when an idle
     worker dies before its own retrain.
2. MINOR, fixed: GameTestBases scheduled cleanup with runAfterDelay(timeout-2), which is relative to the tick of the
   call. A base registered after tick 0 was cleaned up only after the timeout. It now uses
   h.runAtTickTime(timeout-2), the test's absolute tick, and skips scheduling when that tick is already past.
3. MINOR, fixed: Job.isNight now uses Math.floorMod. A negative dayTime would otherwise give a negative remainder.
4. MINOR, fixed: the station screen (BaseInfo.baseLines) showed an old-save holder who has since taken another job
   as the worker. It now prints 'Name (now a <role>, free)' when !Workplaces.holderValid. Steps 3, 5 and 6 rewrite
   these lines; keep the marker for the generic types.
5. Test gap, fixed: nothing tested the barren gate in needs() or its NBT. New GameTest barrenRoleIsNotReplaced
   (workers_static, synchronous) checks four things:
   - needs() wants a woodcutter, not while barren (also at until-1), and again at until;
   - markBarren never shortens a mark;
   - the mark survives BaseState save/load, while testHalf and testRadius are not saved;
   - a tag without 'barrenUntil' loads as empty.
6. Not changed (by design or noted):
   - held() returns the first record of a type. After a predicated claim, an NPC can hold two MINEs; workplaceInfo
     would show the older one. Step 3's MineSites.retire must release worked-out mines, as the spec says, so this
     never happens in play.
   - The FARM and PEN holder check is alive-only, as specified.
   - Removing the MINER gate lets a toolless non-miner be retrained into MINER without a pickaxe. Such a worker then
     waits for the blacksmith, the same as retrainees into other tool roles. This is intended by the spec.
   - WoodcutterJob.leafInTheWay still claims through wp(). Step 6 rewrites it.

Totals after review:
- unit tests: 40 passed, 0 failed;
- GameTests: "All 49 required tests passed" (46 old + retrainingReleasesTheWorkplace, claimSkipsOwnWorkedOutMine
  and barrenRoleIsNotReplaced). Log: impl/gt_1r.log.

For later steps:
- Expect 49 + new tests.
- Barren is set only when the whole role is idle at the base. A GameTest that wants a mark should call
  b.markBarren(...) directly, as the spec's noFishermanRecruitedWithoutWater does.
