# Fixes: Mining (commit b8bc11a)

Build is clean (only the old deprecation warnings). Unit tests: 133 pass (126 before, 7 new). GameTests: "All 83 required tests passed" twice (76 before, 7 new). Logs: impl/gt_fix_mining.log and impl/gt_fix_mining_2.log.

Sabotage runs:
- impl/gt_fix_mining_neg.log: all fixes reverted at once. All 7 new GameTests fail, and so do 3 of the new unit tests (shortCorridorDigsMoreLayersOfTheOre, shaftStoppedAboveAnOreTakesItsNearestHeight, scorePrefersRoomToLengthen). The 76 old GameTests still pass.
- impl/gt_fix_mining_neg2.log: only the clearing of a fluid shaft cell in extend() is reverted. The aquifer test still fails.
- impl/gt_fix_mining_neg3.log: only the emptying of water in tunnel cells already dug is reverted. The flooded-tunnel test fails ("the tunnel behind was walled shut").

## extend-digs-fluid-cell (and campaign (b))
**Shaft cells.** In extend(), once preSeal succeeds, a cell that is pure fluid (water, lava, kelp, seagrass, bubble column; `MinerJob.fluidCell`) is set to air, sealShaft runs, and upgradeStep advances. A waterlogged block is broken normally, and the water it leaves is cleared on the next tick.

**preSeal.** An in-shaft fluid neighbour is now plugged with a seal block when the descent has not dug that cell yet. If the miner has no block, or the miner stands in that cell, it is cleared as before. When the descent reaches the plugged cell, it is dug like any other cobble.
- Whether a cell is dug comes from new pure geometry: `SpiralMine.shaftStepOf(f,s,y)`, `SpiralMine.spiralIndex(from,k,y)` and `MineLayout.shaftCellDug(wp,p)`.

**Tunnels.** mine() handles a flooded tunnel cell the same way: it bridges the floor and advances progress. preSeal also empties fluid from the current level's tunnel cells that are already dug (`MinerJob.dugTunnelCell`) instead of plugging them, which used to wall the miner's own way out shut. Lava is handled the same as water.

**Watchdog.** STUCK_STEP_TICKS (2400) now counts only ticks spent with the cell in reach (preSeal and dig). Walking toward the cell has its own progress-based budget, DESCENT_WALK_TICKS = 1200 (PROPOSED): ticks without getting 0.5 closer. The budget resets when the miner gets closer, and after a gap of more than WALK_GAP ticks (an errand or a fight). When it runs out, abandonDescent runs with "the bottom of the shaft could not be reached".

**Tests:**
- GameTest `minerDigsItsShaftThroughAnAquifer` (batch mine_aquifer, skyAccess). The lower two rows of step 3 and the rock beside them are water sources, and step 3's last cell is flooded again just before its turn. The descent must reach step 3 within 900 ticks, with no shaft limit and every stair cell open.
- GameTest `minerClearsAFloodedTunnelCell` (batch mine_tunnel). The second main-tunnel cell and the dug cell behind it are flooded just before the miner reaches them. The level must not be cut, the cell must end up open, and the cell behind must not be cobblestone.
- Unit tests in SpiralMineTest: `shaftStepOfNamesTheStepOfEveryOpenCell` and `spiralIndexMatchesThePlan`.

## first-descent-climbs-out
In travel(), while upgrading, a miner already in the shaft below the bottom (`MineLayout.stepAt(wp, pos) > wp.bottom`) switches to EXTEND at once, instead of climbing to stand(bottom), which is the surface for a new mine. The walk no longer counts toward STUCK_STEP_TICKS (see the Watchdog paragraph above).
- Test: GameTest `minerGoesOnDownFromWhereItIsInAHalfDugShaft` (batch mine_descent). Steps 1 and 2 are dug, bottom is 0, and the miner stands on step 2. It must never reach the mouth's height, and the shaft must reach step 3.
- Note: GameTestHelper.relativePos is wrong for unrotated tests in 1.21.1 (it mirrors the position 180°), so these tests spawn at literal relative positions and check them against MineLayout.stand.

## rest-fetch-pins-miner
site() now counts only pickaxes the miner would really equip: tier >= need and `gearScore` above its own pickaxe's. If a fetch still leaves pickTier < need, the miner does not try again for 1200 ticks (`nextRestFetch`).
- BlacksmithJob order 2 no longer counts a Silk Touch iron pickaxe as the miners' iron pickaxe (it must score above a plain stone one), and likewise a Silk Touch diamond pickaxe (it must score above a plain iron one).
- Test: GameTest `minerLooksForANewSiteWhenTheRestingMinesPickaxeIsNoUse` (batch mine_restfetch). A mine rests for tier 2, and storage holds only a Silk Touch iron pickaxe. The miner, 22 blocks from the storehouse, must never get within 10 blocks of it. It must go idle with TOO_SHALLOW, the mine must still rest and the pickaxe must still be in storage.

## open-mine-cap-race
In site(), after the evaluate re-check and before MineSites.create, the code checks openMines >= MAX_OPEN_MINES again and returns if so; the next tick then shows "every mine site taken".
- No test. A flat GameTest world never yields a site, so the create branch cannot be reached. The fix is a three-line guard.

## legacy-mine-held-while-working-cave
Option (a): caveWork() releases any MINE the miner still holds while it works a cave. Option (b), releasing the cave, would have left CaveMinerJob's route state (`at`) stale.
- Test: GameTest `minerWorkingACaveHandsBackItsOldMine` (batch mine_legacy). After 20 ticks the cave must still be the miner's, the migrated hut mine must have no assignee, and the director must want "an open mine has no miner".

## retrain-message-garbled-reasons
The miner's idle reasons are now constants that read after "has had":
- NO_SITE = "no place to dig a mine";
- TOO_SHALLOW = "no ground deep enough for a mine";
- NO_FREE_SITE = "no free mine site (the base works as many mines as it may)".

The status lines stay as sentences. A saved "siteWhy" holding the old clause is mapped to the new text on load. FactionDirector's retraining message now writes "becomes an Axe Fighter" (new public `withArticle`).
- Tests: the two existing shallow-ground GameTests now compare against MinerJob.TOO_SHALLOW, and `noMinerHiredWhileAMinerIsFree` checks withArticle.

## retire-message-says-worked-out
- `MineSites.retire` is now only for the WORKED_OUT case (constant `MineSites.WORKED_OUT`).
- The new `MineSites.giveUp` handles "its mouth cannot be reached", "the ground below is unsafe" and "its stairs are blocked". It sends "Mine #N is given up (why)" and sets the new saved flag `WorkplaceState.givenUp` (MINE NBT 'givenUp'). For records saved before the flag existed, it is derived from the note: workedOut with a note that does not start with "every layer".
- The station line reads "Worked-out mines: #a; given up: #b (output N)", or "Given-up mines: ..." when only given-up mines exist.
- `/fc admin mine` prints GIVEN UP, the JSON dump has "givenUp", and tools/sim.py shows GIVEN-UP.
- Test: GameTest `setAsideMinesKeepOnlyTheGroundTheyDug` (workers_static) covers giveUp vs retire, the describe line, and the flag across a save and an old-format load.

## retired-mines-reserve-ground-forever
`MineSites.kept(w)` sets what each mine keeps in obstacles(), and so in site choice and corridorClear. The policy is the pure `MineSiteRules.keptRounds`:
- an open or resting mine keeps its corridor exactly as before;
- a mine set aside with bottom 0 keeps nothing;
- one with a shaft but no tunnel dug keeps `stairsArea` (f -2..5, s -3..4);
- otherwise it keeps the corridor up to the most rounds of any level with tunnels dug.

nearMine keeps the 24-block spacing, except around a set-aside mine never dug, which keeps only BAD_SITE_RADIUS (8), so no miner of any base retries that spot.
- Tests: unit test `setAsideMinesKeepOnlyWhatWasDug`, and GameTest `setAsideMinesKeepOnlyTheGroundTheyDug` (corridorClear across all five cases).
- Left: the site ring is still a fixed 36..64. Letting it grow was optional and is not done.

## Campaign (a): short corridors, maxed iron
**Scoring.** `MineSiteRules.score(..., rounds)` adds ROUND_COST = 15 (PROPOSED, documented) for each lengthening round missing of MAX_REACH. judge() now picks the facing with the best full score, and on a tie the one with more rounds. Before, it picked on penalty alone, then rounds.

**Planner.** MinePlanner.next uses the new `moreLayers` when a band's layer at its own height is done or cut and the corridor is short (maxRounds < MAX_REACH), or when that height lies below the shaft's limit. It then works further layers of the same ore:
- each within BAND_SPREAD = 12 blocks (PROPOSED) of the band's height, and nearer that band's target than any other band's;
- each on a landing at least MIN_SPACING from every level and above the limit;
- nearest first, shallower first on a tie;
- in total no more tunnel than one layer lengthened MAX_REACH times (the sum of rounds+1 over the band's layers stays at most MAX_REACH+1).

Mines with full corridors behave exactly as before, so all the old planner tests pass unchanged.

In the campaign case (Grove #47, maxRounds 0, stone pickaxe, iron critical), the planner now opens a second and then a third iron layer, instead of working lapis and coal and then resting. After that, REST or WORKED_OUT still releases the mine, and the miner looks for a new site straight away. The miner is no longer pinned by the resting-mine fetch (see the rest-fetch test), and resting mines do not count toward maxOpenMines.

Grove's actual dead end in the campaign was #53, whose shaft was capped at y=42 by the aquifer bug, which is fixed above. With a capped shaft, the planner now also takes iron at the deepest reachable landing still in iron's band.

**Tests** (MinePlannerTest):
- `shortCorridorDigsMoreLayersOfTheOre`: more iron layers, the budget, and no change for full corridors;
- `shaftStoppedAboveAnOreTakesItsNearestHeight`;
- `moreLayersNeverBreakTheRules`: 5000 random cases checking spacing, tier, limit and lengthening;
- `scorePrefersRoomToLengthen` (in MineSiteRulesTest).

## Campaign (c): idle miners and hiring
The day-10 dumps show Joren Ember and Wren Flint idle from about tick 120k on. They were not retrained because Ember had no needs at all ("No recruitment needed": housing was full and the garrison was full). That is by design: idle workers only move into a role the base needs.

The real fault was that the base kept hiring miners it could not use. "A surveyed cave with ore to mine" was raised the moment a survey came in, in the same tick, before an idle miner could claim the cave. That is how Wren Flint was hired, and Elsa Wells was retrained from fisherman to miner, while Joren Ember and Wren Flint were idle.

FactionDirector.needs now raises neither "a surveyed cave with ore to mine" nor "an open mine has no miner" while any living miner of the base holds neither an open mine nor a cave. That miner takes the place up itself: a mine first, otherwise a cave.
- Test: GameTest `noMinerHiredWhileAMinerIsFree` (workers_static) checks both needs with a free miner, a busy miner, and a worked-out mine.
- Left: surplus idle miners still wait for a need in another role before they are retrained (the existing design). A base with full housing and a full garrison keeps them.

# Fixes: Woodcutting (commit bfae8c6)

Build is clean (only the old deprecation warnings). Unit tests: 139 pass (133 before, 6 new). GameTests: "All 88 required tests passed" (83 before, 5 new); logs impl/gt_fix_wood.log and impl/gt_fix_wood_final.log.

Sabotage runs (code restored afterwards):
- impl/gt_fix_wood_neg.log: final pass back to the stump column only, saved logs/deferred not read, "cannot get to it" back to badWild, the badWild recheck off, the flower picking off. All 5 new GameTests fail, each with its own message.
- impl/gt_fix_wood_neg2.log: only the grove's storehouse trip off. The grove test times out.
- TOO_BIG turned off: 2 unit tests fail (floodBoundsHold, woodRunningPastTheBoundsIsNeverCutInPart).
- The first GameTest run also caught a bug in my own change: a job loaded in CHOP had no walk allowance yet and gave up on the first tick. walkFailed() now starts the allowance lazily, and load() resets it.

## Campaign: why Grove Covenant had no wood (world_fresh_d10, centre 240,66,-176)
What the save shows:
- The woodcutter's badWild holds 15 stumps, all grove lattice points or starter oaks. Each one passes the survey offline.
- It is in CHOP for the grove tree at (275,61,-201) while standing at (325,-323), about 130 blocks out on a forage leg.
- It carries 8 oak saplings. The woodland's planted list has 4 entries; 25 oak saplings sit in storage.
- My map of the grove lattice (wood_fix/grove_map.py) shows about 20 grown trees on lattice spots. The west side beyond the pad is a village (paths, stairs) and sand; much of the east side is water. About 25 lattice spots are still free grass.

Causes, in order of weight:
1. **Every grove tree was given up from afar** (review item woodcutter-blacklists-own-trees-from-afar). While foraging, step 6 picked each newly grown grove tree, the flat 400-tick walk ran out on the way back, and leaveTree put it in badWild for good. So felled stayed at 25 and the stock reached 0 logs. Grown trees also leave the planted list (prunePlanted), so planted fell from 11 to 4 while their lattice spots stayed taken.
2. **Grove saplings came only on storehouse trips made for other reasons.** The only top-up was in storage(), and a woodcutter that fells nothing never fills its cargo. The 25 stored saplings were never fetched.
3. **A grove spot reached too late was rejected for good** (grove.reject after 600 walking ticks), which took more spots out of a lattice already short of ground.
4. **Foraging far while saplings grew** is the intended order of work: step 10 comes last. It only did harm because of cause 1. With the fix, a grove tree that grows while the woodcutter is out is walked back to and felled.

Fixes, besides the review items below:
- **The grove is filled from storage.** groveTarget() checks for a free grove spot first. If the woodcutter carries no grove sapling, or only one worse than storage holds (oak while birch is stored), it makes a STORAGE trip: at most one per SAPLING_TRIP_TICKS, storage looked into at most every STORAGE_LOOK_TICKS. In storage() the grove takes up to its shortfall (at most GROVE_TAKE = 8), best species first.
- **Home trees.** Grown grove and replant trees near home are walked back to (the walk allowance below). bestTree's score already ranks trees by distance from the centre.
- **groveSize default 8 → 16** (ForestRules.DEFAULT_GROVE_SIZE, config ceiling MAX_GROVE_SIZE 32). The vanilla numbers: in the forced, ticking belt a sapling gets 3 random ticks per 4096 blocks per tick and grows one stage on 1 in 7 of them. It needs 2 stages, and only while lit (raw brightness 9 or more, about 52% of the day). That is about 36,000 ticks, 1.5 days, per sapling. So 8 saplings give about 5 trees (25 logs) a day, and 16 give about 10 trees (about 50 logs) a day. 50 a day is what a base's builder projects (31 planks + 16 logs for one project in the campaign), smiths and furnaces use. Bone meal speeds this up. The unit test groveGrowsTwiceAsBigWhileShortOfWood checks this arithmetic.
- **Short of wood: plant twice as many.** With a WOOD shortage (Logistics), the grove target is 2 × groveSize, up to 32 (ForestRules.groveTarget).
- Tests: GameTest `woodcutterFillsTheGroveFromTheStorehouse` (batch wood_grove; the woodcutter carries nothing, storage holds 2 birch saplings, a poppy stands by the one grove spot) and unit test `groveGrowsTwiceAsBigWhileShortOfWood`.
- Left: an existing config file keeps `groveSize = 8`, because NeoForge writes the old default into it (run/config and scratchpad/server/config both do). Set 16 by hand before the next campaign. A forestRadius below 41 in an old file is reset to the default by NeoForge's range check.

## woodcutter-blacklists-own-trees-from-afar
- "Cannot get to it" is no longer permanent. cannotReachTree() calls skipFor(stump, TREE_RETRY_TICKS 12000). passOver() (badWild, skipping, remote at night) is used for the cached tree, the base scan and the forage scan. Only survey verdicts go to badWild (leaveTree).
- The walk budget scales with distance: `ForestRules.walkBudget(base, d)` = base + 8 ticks per block. Trees use base 400 and planting/bone-meal spots base 600. The woodcutter also gives up after 3 new route failures (walkFailed(), the same pattern as DISMANTLE). For a tree 130 blocks out the budget is 1440 ticks, against about 600 ticks of walking.
- When PLANT gives up walking to a grove spot, the spot is skipped for SPOT_RETRY_TICKS instead of grove.reject. GroveSearch.next takes a skip predicate, so another spot is offered meanwhile. grove.reject stays only for spots whose ground does not fit.
- **Old saves:** recheckBadWild() takes the oldest badWild entry every RECHECK_TICKS (200), when loaded, and surveys it from where the woodcutter stands. If the tree would now be felled whole it leaves the list and a scan follows at once. If it is gone it is forgotten. Otherwise it goes to the back of the list. This frees the 15 grove trees in Grove's save within about 3000 ticks, and Ember's too. It also frees a tree whose touching other-species neighbour has since been felled.
- Tests: GameTest `woodcutterLeavesAnUnreachableTreeForAWhile` (batch wood_cage: the woodcutter is shut in bedrock, nothing within the 4-block unstuck range; the tree must be skipped, never in badWild). GameTest `woodcutterLooksAgainAtTreesItLeftStanding` (batch wood_recheck: a job loaded with a fine tree in badWild fells it). Unit test `farWalksGetTheTimeTheyNeed`.

## forage-leg-timer-stale
- run() drops forageTarget whenever the state is not CHECK, that is, when any other work takes over. The next leg starts afresh, in the same direction (forageDir only advances on a miss), with its own timer. A leg's timer therefore never counts time spent felling or unloading, and an interrupted leg no longer counts as a miss.
- forageFails is reset when step 6 finds a tree.
- No test: forage never runs on GameTest bases (BaseZones.forageAllowed), and a forage leg goes 72 blocks, beyond the platform.

## resume-loses-deferred-logs
- startTree() keeps logs and deferred when resuming the tree it is felling (spot == felling).
- The resume survey merges in the remembered logs that are still logs of the species (treeLog: the same species, not planned) and leaves the deferred ones for the final pass.
- When resumeStart() finds nothing in the stump column, the final pass still runs on the deferred logs instead of finishTree.
- The job NBT saves `logs`, `deferred` (long[]) and `species` while felling. 'v' stays 2; absent arrays load empty.
- A tree another woodcutter claimed, or one given up, clears both lists.
- Test: GameTest `woodcutterFinishesLogsAboveACutGap` (batch wood_gap). A job saved mid-tree: the stump and the log above it stand, y 4..6 are cut, and the top log at y 7 is deferred. The top log must be felled and the tree counted once.

## survey-ignores-horizontal-reach
I took the verdict's preferred fix. In finalPass, a deferred log out of reach from where the woodcutter stands goes one of two ways:
- If `TreeShape.fromStumpColumn(dx,dy,dz)` holds (eye at the column centre, plus the 0.45 stop allowance), it is cut from the stump's column as before.
- Otherwise the woodcutter walks to within UNDER_BRANCH = 1.0 of the ground cell under the log (`TreeShape.fromBelow(dy)`: every log up to +8 is in reach from there) and cuts it. It cuts through natural leaves on the way (leafInTheWay now takes a target).

Each deferred log gets its own FINAL_PASS_TICKS. Only a log whose ground lies lower is still left (rare). TRUNK_REACH now comes from TreeShape.REACH.
- Tests: unit test `aCherryBranchEndIsCutFromBelowNotLeftFloating` (the (5,7) cherry tip, the acacia (3,8) top, the stop allowance) and GameTest `woodcutterCutsABranchEndFromBelow` (batch wood_branch: a cherry with its branch end 5 out and 7 up on the far side; no cherry log may be left).

## flood-truncation-returns-ok
TreeShape.survey returns a new verdict, TOO_BIG, in two cases:
- a SAME_LOG cell lies beyond ±7 sideways or +24 up;
- a SAME_LOG cell is found after 96 logs.

Out-of-bounds cells are now read with cells.at before the bounds check. This applies when resuming too, and the woodcutter leaves such wood standing.
- Tests: `floodBoundsHold` now also expects TOO_BIG. New `woodRunningPastTheBoundsIsNeverCutInPart`: a log of exactly 7 is OK and 8 is TOO_BIG, a planted 3x3 dark-oak farm fused at the canopy is TOO_BIG (also when resuming), and a lone dark oak is OK.

## forest-radius-min-excludes-starter-trees
- forestRadius minimum 32 → 41 (ForestRules.MIN_FOREST_RADIUS, used by FCConfig). The starter offsets now live in ForestRules.STARTER_OFFSETS, which BaseBuilder uses.
- Test: unit test `startersLieWithinTheSmallestForestRadius`. Every starter is within 41 and on an allowed grove spot, and all four lie beyond the old 32.

## grove-clogged-by-own-fancy-oaks (partly)
- **Bee nests.** Before planting an oak, birch or cherry sapling (`ForestRules.beesNearFlowers`), the woodcutter picks every small or tall flower and pink petals in the 5x3x5 box vanilla's TreeGrower.hasFlowers reads (flowers in a plan are left alone). So its own saplings no longer grow the *_BEES variants, whose nest is a block entity that keeps the tree standing for good. This applies to grove spots and replants alike.
- **Fewer oaks in the grove.** The storage trip and top-up above take birch, then spruce, before oak, even while it carries oak.
- **Left (documented):** about 25% of fancy oaks (trunk heights 12-14, j = h+2 ≥ 14) reach dy 9-11, about 2.5% of oak saplings. They are still not felled: the column rule and TOO_TALL stop at +8, and their branches at dy 9-10 lie up to 5 out, beyond any spot on the ground. Each one keeps its lattice spot. With a grove of 16 cycling about 10 trees a day, that is about 0.25 spots a day out of 45-130. Felling them would need the woodcutter to pillar up and move along the crown, which the spec left out on purpose.
- Tests: the flower half is in `woodcutterFillsTheGroveFromTheStorehouse` (the poppy by the spot must be picked) and unit test `flowersArePickedOnlyForSpeciesThatGrowBeeNests`.

## Also seen in the campaign (not changed, not woodcutting)
- Stoneguard's woodcutter (Arlo Pike) stood at (458,64,16), just outside the east gate, with "Walking to the storehouse, going back and forth" and 233 → 1034 recoveries, from day 1 until Stoneguard fell on day 4. Its base was built on filled water terrain. This is a navigation fault at the gate (NpcNavigator), not in the woodcutter's logic.

# Fixes: Fishing and shared (commit 740e927)

The build is clean (only the old deprecation warnings). Unit tests: 139 pass, no change. I added no unit tests: none of these fixes is pure logic. GameTests: "All 91 required tests passed" twice (88 before, 3 new). Logs: impl/gt_fix_fishing_1.log and impl/gt_fix_fishing_2.log.

Sabotage runs (code restored afterwards):
- impl/gt_fix_fishing_neg.log reverts all three fixes at once. All 3 new GameTests fail, each with its own message:
  - "it cast from the water at ...";
  - the resting test times out;
  - "with a rod in hand it is not toolless".
- impl/gt_fix_fishing_neg2.log reverts only the reset in survival(). The toolless test fails with "the time without a rod starts again when the new one breaks".
- impl/gt_fix_fishing_exp1.log is the first try, with only the 0.7 reach. The swim test failed there: the top of the climb out of the water came within 0.7 of the stand, 0.57 up, in mid-air, so the fisherman counted as arrived and cast before it had landed. That is why the fix also checks for the ground (see below).
- In the same runs I logged the walk to the stand on land in the two older catch tests: the first cast came at tick 72-91, with 0 recovery teleports. So the precise reach costs a walker nothing.

## fisher-arrives-in-water-catches-lost
- The walk to the stand uses `FishermanJob.STAND_REACH = 0.7`, NpcNavigator's precise reach: within 0.7 sideways and |dy| <= 0.6. A swimmer next to the bank is therefore never within reach.
- The new `onStand(w)` counts as arrived only when goTo succeeds and the fisherman is `onGround()` and not `isInWater()`.
  - If goTo succeeded but it is still in the air or in the water (the top of a climb out), it steers with MoveControl toward the middle of the stand ("Climbing out onto the bank"). This tick counts as walking time.
  - A stand it can never stand on dry still runs into the existing give-up (TRAVEL_GIVE_UP_TICKS) and is marked a bad spot.
- A bank 2 high is never "reached" from the water any more (|dy| > 0.6). The path then leads out of the water at a lower bank.
- Test: GameTest `fishermanClimbsOntoTheBankBeforeCasting` (batch fish_swim, skyAccess).
  - Setup: a sunken 7x7 pond, the spot set by hand with its stand on the west rim. The fisherman starts in the pond 4 blocks out, with a saved FISH state.
  - It must have been in the water first. At its first cast it must be out of the water, within 0.75 of the stand and |dy| < 0.6.
  - A catch must then reach its backpack.

## resting-fisherman-left-out-at-night
I took option 1. In forage()'s resting branch the fisherman calls markIdle("no fishing water"), then `if (!atStorage()) return;`, and only then the status line and waitFor(1200). It rests at the storehouse, by day or night. It stays idle the whole time, so the director can still retrain it after half a day.
- Test: GameTest `restingFishermanWaitsAtTheStorehouse` (batch fish_rest).
  - Setup: the fishery has spot NONE. The job is loaded with state FORAGE and restUntil = now+6000, standing about 24 blocks from the storehouse.
  - It must stay idle with "no fishing water", reach the storehouse stand and show "No fishing water within reach" there.

## retrain-message-garbled-reasons (remaining part)
The mining fix had already done the miner reasons and the article ("an Axe Fighter"). What remained was the toolless case. The faction heard "has had no tool for the job".
- New `ToolKind.noun()` ("pickaxe", "fishing rod", ...).
- The note now says "no <tool>": "Elsa Wells (Fisherman) has had no fishing rod for half a day and becomes a Miner (...)".
- The message is built by the new public `FactionDirector.retrainNote(name, old, why, need)`.
- The tool errand's status lines use the same noun: "No fishing rod in the storehouse - waiting for the blacksmith", where it used to say "No rod ...".

**A real shared defect found here (Job).** `toolMissingSince` was cleared only when the tool errand itself found a tool. A worker that got its tool another way kept the old mark, and half a day later `missingToolFor` was true while it was working, so the director retrained it as toolless. Other ways include a job's own storehouse visit (MinerJob.upgradeEquipmentNow while looking for a site, FishermanJob.storage) or a spare.
- survival() now clears the mark whenever the role's tool is in hand.
- missingToolFor() is also false while it is in hand.
- Test: GameTest `aWorkerWithItsToolAgainIsNotToolless` (batch workers_toolless).
  - A rodless fisherman at an empty storehouse: missingToolFor(0) turns true.
  - Then it is handed a rod (as a job's own visit would do): missingToolFor(0) must be false.
  - The rod is then taken away: missingToolFor(0) must still be false, because the time without one starts again.
  - The test also checks the retrainNote text.

## Campaign re-check (fresh_d10_server.log FC_DUMP and world_fresh_d10)
**Fisheries.** Script: fixfish/check_fisheries.py. It covers all 8 VALID fisheries the dumps show over the 10 days (4 bases; Tide's spot moved 4 times).
- Every bobber is at least 8 beyond the nearest base's walls (Chebyshev minus 24), and every stand at least 4.
  - The closest is Stoneguard's stand (460,64,33), exactly 28 from its centre. That is the rule's edge ("28 from a settlement centre"), so it is allowed.
- None has farmland within 8 blocks (the rule is 5), checked in the saved world.
- Every bobber column is water, water, air. Every stand is on sand or grass with room above.
  - Exception: Tide's old stand (534,63,107) has oak leaves at head height in the day-10 world. A tree grew there after that spot was given up at about 72k, so this is not a defect.

**Grove's fisherman, "No fishing rod in storage".** Script: fixfish/grove.py.
- He was hired legitimately: the hire needs a rod or 2 string in storage, and the recruitment paid "3x Stick, 2x String" for his rod. Grove held 6 string from the start; an archer's bow took 3 more.
- He fished 77 catches. The rod wore out (64 durability) at about day 9. Grove then had 1 string, so the blacksmith could not make another.
- He then waited at the storehouse. He was not retrained because Grove's recruit note was "No recruitment needed": retraining only goes into a role the base needs, the same documented design as the surplus idle miners.
- Ember's Elsa Wells is the same case at a base that had a need: she was retrained after half a day.
- **Verdict:** not a hiring defect, and not stuck for good. String reaches storage when guards pick up what spiders drop. The blacksmith's order 0 then makes a rod, and the next tool errand fetches it. Any need at the base retrains him.
- **Changed:** his status now says why: "No fishing rod in storage, and no string to make one", or "... - the blacksmith can make one".
- **Fixed (shared):** the toolless-mark defect above. Before it, a fisherman or miner whose tool came back by another path could later be retrained while working.

## Left
- README.md line 205 still says fishermen "stand at the farm's water channel". That predates the redesign. The other fix commits left the docs to the docs step too.
- A base with no needs keeps a rodless fisherman until string comes in. This is by design, as for idle miners.

# Final review (commit cfa8023)

I reviewed the three fix commits (b8bc11a mining, bfae8c6 woodcutting, 740e927 fishing) against each confirmed item in review_result.json. For each, I traced the code paths the item cites and checked what the verdict asked for: root cause, stalls and loops, floating logs, flooding, lost items, NBT symmetry, per-tick cost and old saves. I found one gap, in the bee-nest mitigation, and fixed it.

Re-runs after the fix:
- Build: clean (only the old growCrop deprecation warning).
- Unit tests: 139 pass, 0 fail.
- GameTests: "All 91 required tests passed :)", log impl/gt_fix_final.log. The baseline before my change also passed all 91 (impl/gt_final_base.log).
- Sabotage: impl/gt_final_neg.log, with the old flower check put back. The grove test fails with "the flowering azalea beside the grove spot should be picked first".
- I also confirmed that the agents' sabotage logs fail as they claimed: mining neg 7/1/1, wood neg 5/1, fishing neg 3/1.

## Fixed here
**grove-clogged-by-own-fancy-oaks (the bee half): the flower check was narrower than vanilla's.** TreeGrower.hasFlowers checks the whole #flowers tag over the 5x3x5 box. Besides small and tall flowers and pink petals, that tag holds flowering azalea (bush and leaves), cherry leaves, mangrove propagules, chorus flowers and spore blossoms. WoodcutterJob.flowerNear checked only the first three.
- Effect: an oak, birch or cherry sapling planted beside a flowering azalea (lush-cave surface) or low cherry leaves could still grow a *_BEES tree. Its nest is a block entity, so the tree would be TOUCHES_STRUCTURE and left standing for good.
- Change: flowerNear now tests BlockTags.FLOWERS. It still skips blocks in a base's plan, and it skips persistent (player-placed) leaves.
- Test: the GameTest woodcutterFillsTheGroveFromTheStorehouse now also places a flowering azalea in the box and asserts that it is picked before planting. It fails with the old check.

## Checked, no change needed
Mining (b8bc11a):
- **extend-digs-fluid-cell: root cause fixed.**
  - A pure-fluid cell is cleared after preSeal, in both extend() and mine().
  - preSeal plugs wet stair cells the descent has not dug yet (shaftCellDug, from shaftStepOf and spiralIndex; both are unit-tested against the plan over 21x30 steps). It still clears the cells that were dug.
  - The watchdog counts only ticks spent within reach of the cell. The walk has its own progress budget, and WALK_GAP=101 is just above goTo's waitFor(100), so a failed route does not reset that budget.
  - No loop: every fluid branch advances upgradeStep or lv.progress.
  - A waterlogged block is still broken normally, as before the fix.
  - tunnelPlan is cached (SpiralMine.cachedPlan), so the extra lookup in preSeal costs nothing.
- **first-descent-climbs-out: fixed.** stepAt > bottom sends the miner from TRAVEL straight to EXTEND only while upgrading. onPause and load map only MINE, EXTEND, CLEAR and CRAFT to TRAVEL, so a miner on its way to the storehouse is unaffected.
- **rest-fetch-pins-miner: fixed.**
  - The fetch condition counts only pickaxes with tier >= need and gearScore above the miner's own. upgradeEquipment takes the best gearScore in storage, so one such pickaxe always gets equipped.
  - There is a 1200-tick back-off after a fetch that brings nothing.
  - The blacksmith now treats Silk Touch pickaxes as missing (it compares scores against a plain stone pickaxe for iron, and a plain iron one for diamond).
- **open-mine-cap-race: fixed.** NPCs tick in sequence, so the re-check right before create closes the race.
- **legacy-mine-held-while-working-cave: fixed.** MineSites.rest and retire already release a mine, so only migrated saves can hold both a mine and a cave.
- **retrain-message-garbled-reasons: fixed.** The siteWhy mapping covers the one reason that was saved to NBT. The others are set again every tick.
- **retire-message-says-worked-out: fixed.** The givenUp flag is saved and loaded the same way. Older saves derive it from the note, and legacy hut mines load as not worked out.
- **retired-mines-reserve-ground-forever: fixed.** Open and resting mines keep their full corridor. Set-aside mines keep only the rounds they actually dug, or the stairs, or nothing. nearMine uses 8 blocks only around mines that were never dug.
- **Campaign (a):** the round cost leaves the score at or above the gate walk, so the Search bound still holds. moreLayers is limited to MAX_REACH+1 tunnel units per band, and plan() has a 16-step guard. The 5000-case unit test covers spacing, tier and limit.
- **Campaign (c):** a free miner reaches caveWork within its waitFor(200), since run() checks caves before site(). A toolless free miner holding back a need is consistent: a new miner would need a pickaxe too.

Woodcutting (bfae8c6):
- **woodcutter-blacklists-own-trees-from-afar: root cause fixed.**
  - Only survey verdicts go to badWild. "Cannot get to it" is a 12000-tick skip.
  - The walk budget grows with distance, and a walk gives up after 3 navigator failures. Each failure is MAX_FAILURES with the navigator reset, so it is a real failure.
  - recheckBadWild runs one survey per 200 ticks, which is cheap. It frees the old-save entries.
  - GroveSearch.next skips spots that are skipped for now and moves its cursor on, so an unreachable spot comes back only after a full lattice cycle.
- **resume-loses-deferred-logs: fixed.**
  - logs, deferred and species are saved only while felling and loaded only while felling. Absent arrays load empty, so v stays 2.
  - startTree keeps both lists only for the tree it is felling. A lost claim, dropTree and finishTree clear them.
  - When start is null, the deferred logs still get the final pass.
- **survey-ignores-horizontal-reach: fixed** with the verdict's preferred fix.
  - fromStumpColumn includes the 0.45 stop allowance, and fromBelow is checked against the 1.0 walk reach.
  - Each deferred log gets its own FINAL_PASS_TICKS, and every path removes the log or resets the timer, so there is no loop.
  - A log over ground lower than the stump is still left. That case is rare and documented.
- **flood-truncation-returns-ok: fixed.**
  - TOO_BIG is returned only when a SAME_LOG cell lies past the bounds, or when the count runs out.
  - The flood never goes down. On resume the start is the stump column at the same or a higher y, so a resume cannot turn TOO_BIG unless the world changed.
- **forage-leg-timer-stale: fixed.** The leg is dropped whenever state is not CHECK, and forageDir advances only on a miss. Survival errands do not change state, so a leg does keep running through a meal trip (minor).
- **forest-radius-min-excludes-starter-trees: fixed.** The minimum is 41, and the starter offsets moved to ForestRules unchanged.
- **campaign-grove-covenant-wood: fixed.** The grove storehouse trips are rate-limited (SAPLING_TRIP_TICKS, STORAGE_LOOK_TICKS). Saplings taken from storage stay within KEEP_SAPLINGS when unloading, so none are lost.

Fishing and shared (740e927):
- **fisher-arrives-in-water-catches-lost: fixed.**
  - The 0.7 reach needs |dy| <= 0.6, and onStand also needs onGround and not in water.
  - When the fisherman stands within 0.7 but its box touches water beside a same-level stand, the steering takes it to the stand's middle, where the box is clear of the water. It does not stall.
  - The climbing ticks count toward the existing travel give-up.
- **resting-fisherman-left-out-at-night: fixed.** markIdle runs before atStorage, so the director can still retrain the fisherman during the walk.
- **toolless-mark-stale: fixed.** survival() clears the mark whenever the tool is in hand. Roles without a tool never set the mark.
- **retrain-message-garbled-reasons (toolless part): fixed.** ToolKind.noun covers every value; NONE never reaches the note.
- **campaign-fisheries-outside-walls-and-farmland:** not a bug. I trust the offline script.
- **grove-fisherman-no-rod:** partly fixed, as reported; the rest is by design.

Tests: every new GameTest has a sabotage log that fails without its fix. Their setups match the findings: an aquifer step, a flooded tunnel, a half-dug descent, a Silk Touch pickaxe in storage, a legacy mine and cave pair, a cherry branch end, a cut gap after a reload, a caged tree, a recheck, a swim to the bank, a far rest, and the toolless phases.

## Left (not changed here)
- **Stoneguard east gate (campaign, not part of any fix).** This is not woodcutting and not a navigator bug alone.
  - The world shows the east gate (doors at 456,67-68,15-16) opening onto a 1-block grass ledge at x=457, y=66. Beyond it is a 4-block drop to the lake (water at y=62).
  - The NPCs stuck at (458,64,16) are in a pit their own DigOut dug beside the ledge. FC_DUMP shows them there: the woodcutter Arlo Pike (634 to 1034 recoveries), the scouts Hollis Stone (348 to 1149) and Beck Brook, and the fisherman Lio Stone.
  - Root cause: the base was built on filled water terrain, and nothing builds a walkable approach outside a gate.
  - This needs a separate fix in BaseBuilder (a ramp or causeway from each gate down to the ground), or a gate rule in NpcNavigator that skips a gate with no walkable approach.
- Optional ideas not done:
  - The woodcutter's walk allowance does not restart after a survival errand. A far walk interrupted near its end can run out and is then skipped for 12000 ticks, not for good.
  - MinerJob.site() still counts storage pickaxes every tick during survey walks while a mine rests. This predates the fix and is cheap.
  - The site ring does not grow.
  - Fancy oaks of trunk height 12-14 are still left standing (documented).
- Docs: README line 205 (fishermen at the farm's water channel). The new PROPOSED constants (DESCENT_WALK_TICKS, ROUND_COST, BAND_SPREAD, TREE_RETRY_TICKS, RECHECK_TICKS, UNDER_BRANCH, GROVE_TAKE, STAND_REACH, DEFAULT_GROVE_SIZE 16, MIN_FOREST_RADIUS 41) are for the docs step. Existing config files keep groveSize = 8.
