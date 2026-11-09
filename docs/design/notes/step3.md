# Step 3: mine level list, migration and planner, still on the fixed hut mine (commit a53b30c, on top of fba3f33)

The build is clean: only the old deprecation warnings remain.
- Unit tests: 96 passed, 0 failed. Before this step there were 64. SpiralMineTest went from 6 to 5 tests; LegacyMineTest adds 9, MinePlannerTest 22 and MineSiteRulesTest 2.
- GameTests: "All 60 required tests passed". That is 59 before plus `minerDigsDownToItsLevelThenRetiresAShallowMine`. It passed on 3 runs.
  - Logs: gt_3.log, gt_3_run1.log and gt_3_run2.log.
  - gt_3dbg.log is the debug run that found deviation 4.

README, BALANCE and docs are untouched; step 7 does those. The hut mine is still built with new bases; step 4 removes it.

## New classes and their public API

### logic/LegacyMine.java (pure; LegacyMineTest)
- The following moved verbatim from SpiralMine: `TIER_TARGET_Y`, `MIN_Y`, `landingSteps(surfaceY, level)`, the `ALL_LANDINGS` cache, `regularLandings`, `infillLandings`, `isRegular`, `regularLevels` and `levelY`.
- `record Level(step, progress, rounds)` and `record Migrated(List<Level> levels, int bottom, int cur, int upgradeStep)`.
- `migrate(surfaceY, tier, subLevel, progress, reach, revisit, upgradeStep)` follows saveCompat MINE exactly:
  - levels are truncated at the first -1;
  - rounds: regular levels get reach, or reach-1 above `revisit`; infill levels get 0;
  - only level oc keeps the progress;
  - a descent appends the next level: a deeper one keeps its upgradeStep, an infill one gets -1;
  - when no level exists: no levels, bottom 0, cur -1, upgradeStep -1.

### logic/MinePlanner.java (pure; MinePlannerTest)
- `enum Band {COAL(0), IRON(1), LAPIS(1), GOLD(2), DEEP(2)}` carries `pickTier`, `label` ("coal", "iron", "lapis", "gold", "diamonds") and `byOrdinal(int)`, which returns null for -1 or an unknown value. New bands may only be appended.
- Constants:
  - MIN_DEPTH 6, MIN_SPACING 6, REUSE_WITHIN 5, MAX_REACH 4;
  - COAL_DEPTH 6, IRON_Y 16, LAPIS_Y 0, GOLD_Y -16, FLOOR_ABOVE_BOTTOM 8;
  - IRON_CRITICAL 8, DIAMONDS_WANTED 8, IRON_WANTED 32, FUEL_WANTED 32, GOLD_WANTED 16, LAPIS_WANTED 32.
- Functions: `floorY`, `targetY`, `deepEnough`, `stepFor`, `bandAt` (a tie goes to the shallower band), `record Stock` and `wanted(Stock)`.
- `record Level(step, progress, rounds, cut, band)` has `planSize()` and `unfinished()`.
- `enum Kind`, and `record Decision(kind, level, step, band, needTier)`.
- `next(surfaceY, minY, levels, current, bottom, shaftLimit, pickTier, wanted, maxRounds)` follows spec F (1)-(4).
  - `bottom` is accepted but not needed for the decision.

### logic/MineSiteRules.java (pure, minimal; step 4 completes it; MineSiteRulesTest)
- Constants: `CORRIDOR_BACK = -2`, `CORRIDOR_SIDE_MIN = -11`, `CORRIDOR_SIDE_MAX = 12`.
- `corridorEnd(rounds)` = 2 + 40(rounds+1), which is 202 at 4.
- `record Rect(minX, minZ, maxX, maxZ)` has `inflate`, `overlaps` and `contains`.
- `area(ox, oz, fx, fz, f0, f1, s0, s1)`, where side = clockwise = (-fz, fx).
- `corridor(ox, oz, fx, fz, rounds)`.
- `segment(ox, oz, fx, fz, round)` is the stretch that round `round` adds.
- `overlaps(a, b, margin)`.

### work/MineSites.java
- `retire(level, data, wp, why)` is the only writer of MINE workedOut. It:
  - sets workedOut, upgradeStep -1 and the note, calls Workplaces.release and setDirty;
  - sends "Mine #id is worked out (why)";
  - does nothing for a mine that is already retired.
- `rest(level, data, wp, tier)` sets restTier = max(1, tier) and upgradeStep -1, releases the mine and sends "Mine #id rests until an iron pickaxe".
- `pickName(tier)`, and `bandOf(wp, lv, minY)`, which uses the saved band or bandAt for legacy levels.
- `inside(level, wp, p)` and `mineAt(level, data, base, pos)`. See deviation 4.
- `openMines(data, base)` counts active MINE records.
- `corridor(w)` is the reserved corridor at `MineLayout.reachRounds(w)`.
- `corridorClear(level, data, wp, rounds)` takes the new segment and checks it against these obstacles:
  - MapBounds with margin 8;
  - `obstacles(data, own, self)`: every settlement's walls +4 (its own included), lair walls +16, other bases' MIN/MAX +8, and every other MINE corridor +2 (worked-out ones included).

  Step 4's evaluate can reuse `obstacles`.
- `describe(data, base, minY, who)` gives the station lines:
  - "Mine #id at x, y, z facing east (worker): y=16 iron 120/463 r0/4, y=64 coal 463/463 r1/4 [cut]; shaft to y=-56[, digging down to y=Y step n]; output N". The current level is listed first.
  - "Worked-out mines: #4, #9 (output M); resting until an iron pickaxe: #7".

## Changed files
- **logic/SpiralMine.java.**
  - The legacy code is gone.
  - New public `isLanding(k)`, `landingAtOrBelow(n)` (the nearest landing at least as deep) and `landingAtOrAbove(n)` (the nearest one no deeper).
  - `planSize` uses the cached plan.
- **campaign/WorkplaceState.java.**
  - MINE has label "Mine" and maxTier 1.
  - New fields: `hut`, `bottom`, `cur` (-1), `shaftLimit`, `maxRounds`, `restTier`, `opened`, `levels` (`List<MineLevel>`), and `active()`.
  - `public static final class MineLevel {int step, progress, rounds, band; boolean cut}` has a constructor `(step, progress, rounds, band)`.
  - The MINE NBT has 'mineV' 1 and the keys hut, bottom, cur, shaftLimit, maxRounds, restTier, opened, levels (a ListTag of {step, prog, rounds, band, cut}), workedOut and note. 'progress' is saved as 0 for MINE.
  - A record without 'mineV' goes through `LegacyMine.migrate` and gets hut = true, maxRounds 4 and workedOut false. tier is always 1.
  - Sanitising on load:
    - levels with step <= 0 are dropped, and cur follows (or becomes -1);
    - rounds are clamped to 0..4, and maxRounds too;
    - with cur -1, upgradeStep becomes -1.
  - The fields `subLevel`, `reach` and `revisit` are removed: they are read only as locals of the migration, and "subLevel" is no longer written.
- **structure/MineLayout.java.**
  - New: `current(wp)`, `landing(wp, lv)`, `landingSide(lv)`, `tunnelPlan(lv)`, `tunnelPos(wp, lv, c)`, `shaftSteps(wp)` and `reachRounds(wp)` (the larger of maxRounds and every level's rounds).
  - `tunnelLevelAt` and `nearLevelAt` return a MineLevel and consider only levels with step <= bottom.
  - `entrance` depends on hut; there are also `inMine` and `inHut`.
  - `buildInitial` uses `LegacyMine.landingSteps(y0, 1)`.
  - Deleted: `level`, `deepest`, `landingSteps`, the int `landing`/`landingSide`, `tunnelPlan(wp)`, `tunnelPos(wp, c)`, `upgradePickTier` and `UPGRADE_TORCHES_PER_8_STEPS`.
  - `nearStairs` and `inStairwell` are unchanged and unbounded.
- **work/MinerJob.java.**
  - `wp()` claims with `!workedOut && (restTier == 0 || restTier <= pickTier())`, and claiming a resting mine sets restTier to 0.
  - `caveWork()` uses the new order: held cave; null while the miner holds an active mine; null when wp() can claim one; otherwise claim a marked cave.
  - run():
    - with no base: "No home base", markIdle, waitFor(200);
    - cave → markBusy;
    - no mine → `noMine` (deviation 7);
    - otherwise markBusy, `collect(wp)` and the state switch.
  - CHECK goes to STORAGE also when cur == -1.
  - STORAGE works as before; then, when not upgrading, it runs `plan(wp)`. A mine that ends up resting or retired leads to CHECK.
  - `plan(wp)` acts on Stock / wanted / MinePlanner.next as follows:
    - WORK → `choose`;
    - LENGTHEN → `corridorClear`, then rounds++, +20 XP and the message. When the corridor is not clear: maxRounds = rounds, and it plans again (16 tries at most);
    - NEW_LAYER → append the level. If it lies deeper than the bottom, upgradeStep = 0; otherwise +20 XP and the message "opens a branch mine at y=Y for <band>";
    - REST → `MineSites.rest`;
    - WORKED_OUT → `MineSites.retire(.., "every layer it can reach is dug")`.
  - TRAVEL:
    - when upgrading: `walkInMine(stand(bottom), bottom)`, then EXTEND;
    - the throttled `pickTooWeak` check (every 2400 ticks; skipped while upgrading) leads to STORAGE;
    - otherwise it walks to the level as before.
  - MINE works on `lv.progress` and has the same tier check.
    - `cutPlan(lv, plan)` sets `lv.cut` and progress = size for the main line, and skips the side for a branch.
    - The face watchdog is reset when the level changes too.
  - EXTEND digs `spiralPlan(bottom, cur.step)`. On completion: bottom = step, +20 XP and the message "Mine #id reaches y=Y for <band> (<name>)".
  - `abandonDescent(wp, cell, why)` runs on any of: a mayDig refusal; more than 2400 ticks on one upgradeStep; 3 seal failures in a row with noSealBlocks. It:
    - sets bottom = max(bottom, k-1) and shaftLimit = k-1;
    - removes the current level;
    - sets cur and upgradeStep to -1 and the state to STORAGE;
    - sends the message "the shaft stops at y=Y (why)";
    - retires the mine when bottom < 6.
  - walkInMine:
    - new first leg to stand(0) from the surface;
    - the beside-the-shaft branch uses `shaftSteps`;
    - stair clearing uses `preSeal(wp, q, shaftSteps)`;
    - a refused blocking stair cell counts toward `stairBlockedTicks`. Past 600 ticks: `retire("its stairs are blocked")`, the leave budget is marked spent (plain walking takes over) and the state goes to CHECK.
  - leaveMine: `MineSites.inside`, plus the budget of deviation 3.
  - `beforeErrand()`: `mineAt(...) == null || leaveMine(it)`.
  - findBlocked covers spiral cells 0..bottom, plus the current level's dug cells when its step <= bottom.
  - clear() stands at most at `bottom`.
  - preSeal, prospect and collect take the mine as a parameter, so they no longer claim on the side.
  - `workplaceInfo` (via held()): "Mine #id (N levels, shaft to y=..), level y=.. for iron, branch mine p/size, lengthened r x", or "digging down to it (step n)".
  - Deleted: `exhausted`, `startLengthening`, `nextLengthening`, `blockedDeeper`, `considerGoingDeeper` and `MAX_REACH`, along with the tier and subLevel texts.
- **work/BlacksmithJob.java.** Order 1 (the mine upgrade payment) and the MineLayout import are deleted. The order comments now start at "2."
- **campaign/FactionDirector.java.** The need is now "an open mine has no miner": an active MINE that is unworked, while living(MINER) > 0.
- **campaign/BaseBuilder.java.** The hut mine gets `hut = true`, maxRounds 4, `opened = now`, and its levels, bottom and cur from `LegacyMine.migrate(y0, 1, 0, 0, 0, 0, -1)`: one level at step 6, bottom 6, cur 0. `sealMine` uses `LegacyMine.landingSteps`.
- **info/BaseInfo.java.** The mine lines come from `MineSites.describe`; the other types are unchanged. The worker text is a shared lambda that keeps the step-1 "(now a X, free)" marker.
- **command/FCCommands.java.**
  - `/fc admin mine <id>` (mineDump) prints the header, the level list, and the plan window of the current level.
  - The JSON snapshot for MINE has origin, facing, hut, bottom, shaftLimit, maxRounds, cur, restTier, workedOut, note, levels [{y, band, progress, size, rounds, cut}] and tier = levels.size().
- **tools/sim.py.** It prints the MINE fields and one line per level (">" marks cur), and adds gold_ingot to the stock keys.

## Deviations from the spec and why
1. **LENGTHEN with the corridor not clear.** The spec says "rounds = maxRounds, and plan again". Taken literally, the level would become unfinished and the miner would dig into the blocked stretch. I read it as `wp.maxRounds = lv.rounds`: the corridor ends at this length for every level, since obstacles are the same at every depth.
   - So that a smaller maxRounds never hides tunnels already dug, `inMine` and the reserved corridor use `MineLayout.reachRounds(wp)` = max(maxRounds, every level's rounds).
2. **Band adoption in `choose()`.** When the planner works or lengthens a level for a band whose pickTier is lower than the level's own band (for example coal reusing an iron level 4 steps away at a low surface), the level takes on that band, and it is saved. Without this, the throttled tier check would send a wooden-pick miner home every 2400 ticks for nothing.
3. **Leave budget.** It is 1200 ticks without getting any closer to the way out (horizontal distance to the origin + 2 × depth), not 1200 ticks in all.
   - A real climb out of a 4-round tunnel at y=-56 (about 200 blocks of tunnel and 120 stair steps) takes more than 2000 ticks. A plain 1200-tick budget would hand that over to plain navigation and DigOut halfway up.
4. **Sky test for "in the mine".** `MineSites.inside(level, wp, p)` = inHut, or inMine and (nearStairs or no sky above).
   - `mineAt` takes the level and is used by leaveMine, beforeErrand and noMine.
   - In walkInMine, "on the surface" means depth <= 1 or sky visible. The beside-the-shaft branch runs only under cover.
   - Found by the new GameTest: a miner who had left the mine and dropped onto ground 3 below the mouth was inside the corridor box at the level's depth. It was taken for "underground" and walked back in to climb out, in a loop.
   - Real terrain dips like this within the 200-block corridor all the time, and the old 3D 64-block test had the same flaw.
   - **GameTests that rely on in-mine checks on the platform need `skyAccess = true`.**
5. **The stuck-step watchdog counts ticks spent in EXTEND on the same upgradeStep**, not wall-clock time. Storehouse trips, meals and fights do not count.
6. **stairBlockedTicks** counts only a refused stair cell that has a collision shape (a sculk vein does not block), and is reset by any leg without one.
7. **`noMine()`.** A miner without a mine first leaves the mine it stands in (mineAt + leaveMine), then unloads, then goes idle. This is the LEAVE/UNLOAD part of step 4's SITE.
   - It is needed now, because blocked stairs or an unsafe shaft retire a mine with the miner still inside.
   - Idle reasons: "every mine site of the base is taken" when openMines >= maxOpenMines, otherwise "no mine or cave to work". The "// step 4: ... (state SITE)" hook is there.
8. **The tier check is skipped while upgrading.** STORAGE does not plan during a descent, and digging the shaft works with any pickaxe; the check runs at the bottom.
9. **`rest()` takes the ServerLevel** (for the message); restTier is at least 1. `retire()` also sets upgradeStep to -1 and does nothing on a mine that is already retired.
10. **abandonDescent sends a faction message**, "Mine #id: the shaft stops at y=Y (why)", not listed in the spec.
11. **corridorClear also avoids lair walls +16**, as the site obstacles in (g) do.
12. **`MineLayout.UPGRADE_TORCHES_PER_8_STEPS` is deleted now.** It was unused; mining C puts it in step 3, the step list in step 4.
13. **inHut keeps today's box exactly** (f -2..5, |s| <= 2, within 7). The spec text says f -1..5.
14. **The REST search in `MinePlanner.next` starts at tier max(1, pickTier+1)**, so a REST never has needTier 0, which would read as "not resting". plan() never runs without a pickaxe anyway.
15. **Extra tests:**
    - LegacyMineTest.newHutMineStartsWithItsFirstLevel;
    - MinePlannerTest.bandAtPicksTheNearestTarget;
    - MineSiteRulesTest: corridorRectForEachFacing and overlapsHonourTheMargin;
    - GameTest minerDigsDownToItsLevelThenRetiresAShallowMine (batch mine_plan, skyAccess, timeout 1200). A hutless MINE record at rel (4,5,5) on the spec's stone block holds a finished coal level at step 3, with bottom 0 and upgradeStep 0. The miner, spawned at the origin with a stone pick and 16 cobblestone:
      - digs the spiral down to step 3 (the stair cells are asserted open);
      - walks out to the storehouse at rel (12,2,16);
      - plans. The flat world gives WORKED_OUT, so the mine is retired with note "every layer..." and assigned null;
      - goes idle with "no mine or cave to work".
16. **legacyWorkplacesLoad MINE part.** Old tier 3, subLevel 1, upgradeStep 5, progress 120, at y 66 gives:
    - levels [6, 34, 66, 114, 122], with bottom 114, cur 4, upgradeStep 5, hut, maxRounds 4 and tier 1;
    - only level 3 keeps progress 120; every band is -1;
    - 'mineV' is written, and there is no reach or revisit, with progress 0.

    This holds over 2 rounds. A new-format round trip covers the remaining fields, and a dropped step-0 level shifts cur. The spec says "cur and upgradeStep kept"; with the descent, cur is the appended level, as saveCompat defines.

## Old save check (for step 8)
The 4 MINE records of world_run3, migrated offline with LegacyMine:
- #1, surface 71, tier 4+10, reach 4: 14 levels, bottom 126. The current level is the last infill (step 14), finished.
- #6, surface 73: 13 levels, bottom 122.
- #11, surface 69, revisit 4: levels 1-3 have r3 and level 4 has r4, with progress 1978/2483.
- #16, surface 66, reach 3, revisit 1: level 1 has r3, with progress 1473/1978.

What the planner chooses with a stone pick: the IRON level near y=16 (an infill level at progress 0, fast-forwarded through the dug cells). With an iron pick: the DEEP level near y=-55. As the spec says, every level except the current one starts at progress 0 and is fast-forwarded at about 1 tick per dug cell, so worked-out old mines take a few in-game hours of fast-forwarding before they plan WORKED_OUT.

## Notes for the next steps
- **Step 4.**
  - **Hooks.** Each is marked by a comment:
    - noMine has "// step 4: ... (state SITE)". LEAVE and UNLOAD are already done there.
    - travel() has "// step 4: a fresh site whose mouth cannot be reached is given up here".
    - abandonDescent has "// step 4: the site also joins the miner's bad sites".
    - sealShaft has "// step 4: a mine without a hut also plugs the ground ring".
    - The mouth torch is not done: on the first EXTEND tick when bottom == 0 && upgradeStep == 0.
  - **What already works for hutless mines:**
    - `MineLayout.entrance` is the origin;
    - `inMine` uses the corridor;
    - EXTEND from bottom 0 (tested by the new GameTest);
    - `MinePlanner.deepEnough` for the depth rule;
    - `MineSites.obstacles` and `corridor` for maxRoundsThatFit;
    - `MineSiteRules.Rect`, `area` and `corridor`.
  - **The spec's GameTest minerDigsItsOwnShaftAndSealsTheGroundRing.** The miner cannot climb the 3-high stone block from the platform, so spawn it on top at the origin, as my test does. It also needs skyAccess. A finished level is enough to stop the job at the bottom, because the flat world then retires the mine.
  - **Deletions still to do:** BaseBuilder's hut MINE, buildInitial, sealMine and the mine pad; CaveWork.ownWorkings still uses the 64-block radius.
  - **WorkSafety.reserved(MINE_SITE)** still has no woodland part (that is step 6).
- **Step 6.** Nothing mining-related.
- **Step 7.**
  - BALANCE should cover:
    - the MinePlanner constants above;
    - MinerJob: STUCK_STEP_TICKS 2400, SEAL_FAILURES 3, TIER_CHECK_TICKS 2400, STAIRS_BLOCKED_TICKS 600, LEAVE_BUDGET_TICKS 1200 (without progress), LEVEL_XP 20;
    - MineSites: CORRIDOR_GAP 2, WALLS_GAP 4, BASE_GAP 8, LAIR_GAP 16, MAP_MARGIN 8.
  - README: "tier 4", "constructed mine", the blacksmith's "mine upgrade payment", and the retraining text with the new reasons.
  - The CaveWork.ownWorkings javadoc still says "constructed mine" (step 4 rewrites the method).
  - The order comments in BlacksmithJob start at "2.".
- **Totals.** Expect 96 unit tests and 60 GameTests, plus what later steps add.

## Review (commit b7bb3b8, on top of a53b30c)

I read the code paths, not just the diff: MinerJob (run, noMine, plan, choose, travel, mine, extend, abandonDescent, walkInMine, leaveMine, beforeErrand, findBlocked, clear), MineSites, MineLayout, MinePlanner, LegacyMine, MineSiteRules, the WorkplaceState MINE block, and the callers. I also checked the old MinerJob and MineLayout (fba3f33) for the migration semantics:
- the old lengthening went from the deepest regular level up, using `revisit`;
- tunnelPlan used `reach` rounds only while revisiting;
- the old descent ran from landing(deepest), which equals the bottom whenever a descent was possible.

The migration matches all of these.

### Spec compliance
Every bullet of steps[2] is in, and so are the step-3 parts of mining A, B, C, F, G, H, J, K and L, saveCompat MINE and the tests. The exceptions are the items the spec or the notes give to step 4: SITE, badSites, the hutless ground ring, the mouth torch, the unreachable-site retire, ownWorkings, and removing the hut, pad and sealMine. I accept the implementer's deviations 1-16 as reasonable.

### Findings
1. **MAJOR, fixed: a resting mine could wait forever with its pickaxe in the storehouse.** Two causes:
   - A miner with no claimable mine (noMine) only unloads. It never ran upgradeEquipment, so it never picked up the iron (or stone) pickaxe the blacksmith's order 2 made, and so never claimed the mine resting for it. It idled, was retrained, and the role went barren.
   - In storage(), upgradeEquipment is throttled to once per 1200 ticks. plan() could therefore rest a mine (releasing it) while a better pickaxe lay in the storehouse.

   Fixes:
   - New `Job.upgradeEquipmentNow()` (unthrottled). plan() calls it before a REST and plans again if the pickaxe improved.
   - noMine() now looks for the lowest restTier above the miner's pickaxe among the base's resting mines (`restingMineNeed`). When the storehouse has a pickaxe of that tier, the miner walks there and equips it, and the next tick claims the mine. This runs before the idle branch; **step 4: keep it before SITE**.
   - New GameTest `minerFetchesABetterPickaxeForARestingMine` (batch mine_rest, 400 ticks). It times out with the fetch disabled; I checked this (gt_3r_neg.log).
2. **Minor, fixed.** The stairs-blocked retire in walkInMine also ran when the miner was only passing through, for example climbing out of another miner's active mine (noMine or beforeErrand use mineAt over all of the base's mines). Now only the holder retires the mine; anyone else just marks its leave budget as spent.
3. **Minor, fixed.** The shaft stuck-step watchdog was keyed only on the upgradeStep index. A stale count from one mine could cut short a descent in another mine at the same index. It is now keyed on the mine id as well.
4. **Minor, fixed.** In travel(), a current level below the bottom with no descent under way (only possible from a damaged record) made walkInMine "clear" its way down undug stairs, without sealShaft. travel() now starts the descent (upgradeStep 0).
5. **Minor, fixed.** In travel(), a finished current level, for example after a reload (CHECK goes to TRAVEL), walked all the way down to the landing just to turn back. It now goes to STORAGE to plan. The planner never returns WORK for a finished level, so this cannot loop.
6. **Minor, fixed.** The "underground" test (deviation 4) used canSeeSky, which is sky light 15. Leaves lower the sky light, so a miner on the floor of a wood below the mouth, inside the corridor box, was taken for one in the mine. The new `MineSites.underground` uses the MOTION_BLOCKING_NO_LEAVES heightmap instead (the spec's convention, factual error 6). It is used by inside() and by walkInMine's surface test. GameTests on a barrier-roofed platform (no skyAccess) count as underground. Mine tests need skyAccess = true, as before.
7. **Minor, not fixed (per spec).** walkInMine stair clearing uses full mayDig. A gravel refill in the own stairs inside the deep dark, or any refused block with a collision shape, retires the mine after 600 ticks. Watch for this in step 8 with old deep mines.
8. **Minor, not fixed (per spec).** findBlocked checks the whole shaft down to the bottom, even while the miner works a shallow level. A refill far below sends the miner down to clear it.
9. **Minor, not fixed (pre-existing).** walkInMine's nearLevelAt has no surface test. A miner on open ground within 1 block of a level's height above that level's tunnels heads straight for the landing. The old code behaved the same way.
10. **Cosmetic, not fixed.** The BlacksmithJob order comments start at "2."; they were already irregular (6a, 6b, 6, 6c). Step 7 can renumber them.

### Checked and fine
- **NBT.** MINE save and load are symmetric over the new keys, and 'progress' is saved as 0. A record without 'mineV' migrates once.
- **Load sanitising.** Levels with step <= 0 are dropped and cur follows. An out-of-range cur becomes -1, and upgradeStep -1 goes with it.
- **Null guards.** run() checks base() == null, and so do beforeErrand, mineAt and plan (base comes from run).
- **Planner.**
  - No WORK for a finished or cut level.
  - The plan() loop is bounded: each LENGTHEN failure strictly lowers maxRounds, and the REST re-plan happens only when the pickaxe tier rises.
  - stepFor stays in [6, maxN].
  - abandonDescent keeps bottom = shaftLimit = k-1, which is at least the old bottom, so it never sets shaftLimit 0 ("no limit") on a mine that is kept.
- **Geometry.**
  - corridorEnd(r) = 2+40(r+1), the last tunnel cell.
  - inMine starts below origin.y-1.
  - stepOfCell holds for steps 1 and 2 (dy >= 0).
  - MapBounds is checked at two opposite corners of the segment, which is enough for an axis-aligned square.
- **Static caches and per-tick cost.** The static caches are ConcurrentHashMaps. The per-tick work is O(base workplaces). stock() runs 6 storehouse counts per planning pass.
- **Client classes.** None are used on the server.
- **Things left unchanged.** DigOut, NpcNavigator and CaveMinerJob are untouched. Other jobs are unaffected; Job only gains a helper.

### Tests
- Unit tests: 96 passed, 0 failed (unchanged).
- GameTests: all 61 required tests passed on 4 runs (gt_3r.log, gt_3r_1.log, gt_3r_2.log, gt_3r_final.log). That is 60 plus `minerFetchesABetterPickaxeForARestingMine`.

The new step-3 tests are not vacuous:
- LegacyMineTest pins the exact old landing lists.
- The MinePlannerTest random tests assert invariants over 2000-5000 cases.
- `minerDigsDownToItsLevelThenRetiresAShallowMine` asserts the dug stair cells before the retirement.

All job tests have their own batch and use GameTestBases with clean-up.

### For the next steps
- **Step 4.**
  - noMine order: leave the mine, unload, fetch a pickaxe for a resting mine, then SITE.
  - The SITE GameTests need `skyAccess = true`, because a barrier roof makes the whole platform "underground" for `MineSites.inside`.
- **Totals.** 96 unit tests and 61 GameTests.
