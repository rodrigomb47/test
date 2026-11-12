# Soldiers that cannot reach their target

**The owner's report** (playing on a server): "after finding an enemy base, I was able to infiltrate it, then I
entered their animal fence and none of the npcs trying to attack me could get in the animal fence and I was able to
easily kill them", and "I [placed] a block close to their door and destroyed the door, the npcs trying to attack me
would just stand there without being able to do anything and also being easily killed."

Goal: hostile faction NPCs are never farmable like this, by player-like means only.

## Reproduction (old code)

Seven GameTests in `gametest/CombatReachGameTests` (each in its own batch: a real player in the level draws every
soldier and monster near it). The player is a mock survival `ServerPlayer` of another faction, placed with the player
list like vanilla's `makeMockServerPlayerInLevel` but in survival, with 1000 health, removed again at the end. A
mock player has no client, so nothing ticks its body: the tests count down its hurt cooldown and, where a block
under it is broken, drop it a block (`gravity`).

Run on the unchanged mod (only the test class added), `impl/gt_reach_repro.log` in the session scratchpad:

| Test | What the soldiers did |
|---|---|
| `soldiersOpenThePenGateToReachAPlayer` (player in a fenced pen, closed oak gate) | walked round to the fence nearest the player and stood there 4.7 blocks from its eyes; never went in; gate never opened |
| `soldiersBreakAPlayersBlockInABrokenDoorway` (planned stone wall, door gone, the player's dirt in the doorway, the player 2 blocks outside) | stood in front of the doorway 2.7 blocks from the player's eyes (within its reach) for 25 s; never broke the dirt; after 10 s they dropped the target and went back to their posts, then picked it up again |
| `soldierReachesAPlayerOnATwoHighPillar` (player on 2 dirt blocks) | stood at the foot of the pillar 1.9 blocks from the player's eyes, never hit it |
| `soldiersHoldOutOfReachOfAPlayerTheyCannotReach` (sealed pen of planned fences, the player 2 blocks inside) | the two soldiers stood at the fence within the player's reach for 467 of the 480 soldier-ticks measured, and walked off to the storehouse whenever the target went stale |

All four failed (`4 required tests failed`).

## Root causes

1. **Fence gates.** Vanilla's `WalkNodeEvaluator.getPathTypeFromState` returns `FENCE` (impassable) for a closed
   fence gate, and vanilla's `OpenDoorGoal` only opens `DoorBlock`s. So no path ever led through a gate; the melee
   goal followed the partial path to the closest point, the fence nearest the player.
2. **Nothing is ever broken in a fight.** The doorway with a block in its bottom half and the wall's lintel above
   has no walking route (stepping up onto the block needs head room above the doorway). No combat code breaks
   blocks, so the partial path's end in front of the doorway was the end of it.
3. **Melee reach is sideways only.** `Mob.getAttackBoundingBox()` grows the NPC's box by 0.83 sideways and 0
   upward. A target standing 2 blocks up (on a pillar) cannot be hit from the ground; a player's 3-block reach
   (4 accepted by the server) hits the NPC easily.
4. **No idea of "unreachable".** Vanilla's `MeleeAttackGoal` walks the partial path to its end, which is usually
   right against the obstacle and so inside the player's reach, and searches the same failing path again every 4 to
   10 ticks (up to 768 nodes each time). The 10-second stale-target rule then sent soldiers back to their jobs,
   walking past the player, and the target finder picked the player again: the loop the owner saw.

## What they do now

In order of preference, each where it applies:

1. **Gates and doors.** `npc/NpcPathNavigation` (the NPC's navigation) uses a `WalkNodeEvaluator` in which a closed
   fence gate counts as a wooden door while the NPC is fighting (it has a living target). Work paths never go through
   gates, so workers keep out of the pen as before. `combat/OpenGateGoal` (no goal flags, beside vanilla's door
   opening) opens the gate when the path reaches it (away from the opener, as for a player), and closes it once the
   NPC is through (on the far side, or 3 blocks away, or after 4 s), never while anyone stands in the gateway, with
   1 s before it opens a gate again. Doors were already opened and closed by vanilla's `OpenDoorGoal`.
2. **Break what is not anyone's to keep.** `logic/ReachPlanner` (pure, unit-tested) is a cheapest-first search over
   a small box round the fighter and the target (4 blocks of margin, 3 down). Moves: walk a block, step up one (head
   room above both), drop up to 3, and build up a block with one from the backpack; each move clears the cells it
   passes through, at the cost of breaking them. Costs are rough ticks: 5 a block walked, 8 a step up, 12 a block
   built, plus the ticks to break each block with what the fighter holds. A plan ends where the fighter can hit the
   target: a cell beside its column (diagonals with an open side at eye level) at most a block up or down. The
   Minecraft side, `combat/Reach`, says what each cell holds; `Reach.mayBreak`:
   - never a block of any base's plan that is the planned block (walls, doors, fences, houses, the heart);
   - a different block in a planned cell (the raider's dirt in a doorway) may be broken, unless a player of that
     base's own faction put it there (`playerEdits`) and the fighter is of that faction;
   - in the walls box of a base of its own side (its own or an ally's) an unplanned block only when a hostile player
     placed it there: `BaseState.intruderBlocks` (saved, at most 4096; `FCEvents.onPlace` records blocks placed in a
     base by players hostile to it, any break forgets them). An enemy's fighters may break unplanned blocks in a
     base, as raiding players would;
   - never a base's ground (inside its walls box, below its floor level: the yard and the rock under it);
   - never a block with a block entity (chests, beds...), a block holding or touching water or lava, a mine's
     stairs, an unbreakable block;
   - never a block that takes longer than 100 ticks (5 s) with what it holds: dirt by hand is 15, planks 60, glass 9;
     cobblestone by hand (200) is left alone.

   A broken door's cell is then empty, and the repair scan puts the planned door back as before.
3. **Climb.** A target a block up is hit from the ground beside it (no change, now a regression test). A target
   two or more up: the planner may build the fighter up beside it (at most 3 blocks, never higher than the target,
   only within 2 blocks of its column, never in a planned cell or in a base of its own side, where it would leave a
   pillar in its own yard), or break the block under the target (from where it can reach it, 4.5 blocks from the
   eyes) when that block may be broken: the target drops a block and can be hit. Guards now carry up to 8 blocks
   of dirt (or cobblestone / cobbled deepslate) from the storehouse when it holds at least 64 of that block beyond
   what they take, so builders and the blacksmith never go short (`GuardJob.stockClimbingBlocks`).
4. **Ranged.** Archers and crossbowmen already shoot over fences and out of sight lines; they no longer walk up to a
   target they cannot reach when it is out of sight or range: they go to a spot 9 blocks away in sight of it if they
   can (`Pursuit.approachRanged`). A melee soldier that has to wait calls the archers and crossbowmen of its
   faction within 32 blocks that have no target (`Alerts.callArchers`), when it starts waiting and every 5 s. No
   melee role switches to a bow (none carries one).
5. **Wait out of reach.** With no plan within 600 ticks of work, the fighter waits (`Pursuit` HOLD): on a ring 6.5
   blocks round the target (at least 5 horizontally, beyond a player's reach with a margin), at the spot nearest the
   end of the path toward it (the way out: the gate, the doorway), apart from the spots the other fighters of its
   faction hold. It looks at the target; while the target is in sight within 24 blocks it keeps the target (the
   10-second stale rule counts this as engaged). Every 2 s it checks once for a way (one path search) and plans
   again; as soon as there is a way it goes in. A target that comes within its swing is hit. Away from home (more
   than 16 blocks outside its walls: a raid, an escort) it gives up after a minute and ignores that target for a
   minute, so a player in a box does not hold up a raid. A civilian does not stand guard: it lets the target be
   (its job takes over) and steps out of its reach. Wounded civilians still flee as before.

Path searches: while chasing, vanilla's 4 to 10 ticks; once the path comes back partial the next search waits at
least 15 more ticks and the fighter plans or waits; while waiting, one search toward the target every 40 ticks, at
most 3 to choose a spot (at most once a second), and the planner (a few thousand cells at most) at most every
40 ticks; while working, one search every 20 ticks to the work spot. The hold test checks that a waiting soldier
searches at most 15 paths in 200 ticks (vanilla's goal searched 10 to 50 times in that time against the fence).

The melee goal was rewritten as a plain `Goal` (vanilla's `MeleeAttackGoal` keeps its path and swing timers private,
so it could not be told to stop chasing); fighting is unchanged: the same chase, the critical-hit jump, a swing every
20 ticks (see "Found on the way" below), the leash for home guards.

## Tests

- Unit (`ReachPlannerTest`, 13): walks when nothing is in the way; strikes from where it stands; breaks the block in a
  doorway and nothing else (from in front of it); no way through a fixed wall; walks round rather than break slowly;
  gives up over the cost cap; a one-high perch needs no work; breaks the block under a target on a two-high pillar
  (and not when not asked); builds itself up beside a perch it may not break (one block for a target two up, none
  without blocks); never builds higher than the target; goes through a gate, not over a fence, and a sealed pen of
  fixed fences has no way in; a corner hides a diagonal target; a step up needs head room.
- GameTests (`CombatReachGameTests`, 7):
  - `soldiersOpenThePenGateToReachAPlayer`: through the gate, the player hit, the gate closed again behind them with
    nobody in it, the cow still in the pen, at most 4 open-close cycles by the two soldiers;
  - `soldiersBreakAPlayersBlockInABrokenDoorway`: the dirt broken, the player hit, every planned wall block intact
    every tick;
  - `soldierReachesAPlayerOnATwoHighPillar`: breaks the block under the player, who drops, and hits it;
  - `soldierBuildsItselfUpToAPlayerOnAPlannedPerch`: the perch is part of the player's base's plan; the soldier
    builds itself up with the dirt it carries and hits the player; the perch is never broken;
  - `soldierHitsAPlayerOneBlockUpWithoutBreakingAnything`: struck from the ground, nothing broken or built;
  - `soldiersHoldOutOfReachOfAPlayerTheyCannotReach`: the soldiers start at the fence within the player's reach; from
    tick 100 to 400 neither is ever within it, both keep the player as target, stay within 10 blocks, hold more than
    1.5 blocks apart, search at most 15 paths in 200 ticks; the archer (no AI until then, so it cannot have seen the
    player itself) has been called by tick 150 and hits the player with an arrow; no planned fence broken; then a
    fence is removed and they go in and hit the player;
  - `fightersBreakOnlyWhatIsNotTheirsToKeep` (static): the `mayBreak` and `mayPlace` rules above, for a fighter of
    the base's faction and an enemy.
- **Sabotage check.** With the gates never passable and `Pursuit.unreachable`/`watchStuck` turned into no-ops,
  `impl/gt_reach_sabotage.log`: `5 required tests failed` (door, pen, pillar, planned perch, hold); the one-high
  perch and the static rules test pass, as they should. Restored afterwards.
- Final: unit tests 173 (160 + 13), GameTests "All 123 required tests passed" (116 + 7), `impl/gt_unreach.log`, and
  three more full runs `impl/gt_unreach_r1.log` to `r3`.

Not yet checked in a campaign on a dedicated server (the owner asked for no server run in this step).

## Found on the way (not changed)

- **The swing cooldown is 20 ticks for every weapon.** README and BALANCE say melee timing follows the weapon's
  attack speed (a sword about every 12 ticks), and `NpcMeleeAttackGoal.getAttackInterval()` computes that, but
  vanilla's `MeleeAttackGoal.resetAttackCooldown()` always used 20 ticks and never called it. The rewritten goal keeps
  20 ticks so that this change does not shift the war balance; using the weapon's interval is a one-line change
  (`checkAndPerformAttack`) for the owner to decide.
- A block placed in a planned cell by anyone is not "damage" to the repair scan (`FactionDirector.isDamaged` only
  counts air, fluid, fire and snow), so a raider's block left in a doorway stays until someone breaks it; the
  soldiers now do when they need the way.

## Open items

- Campaign check: soldiers against a real player (pens, doorways, perches, a player on a wall), and that guards
  holding at home do not disturb patrols and repairs.
- A gate left open by an NPC that dies in it is not closed by anyone else (the animal carer leads strays back).
- Pillar blocks built outside the bases are left standing, as a player's would be.
- A soldier with only a sword does not break cobblestone or stone (over 5 s); it waits and calls the archers. Giving
  soldiers a pickaxe for that would be a further step.

## Review

An adversarial review of the commit above (43ab107): its code paths read against the owner's two reports and the
other ways a player could farm soldiers it cannot reach (a 1x1 hole, a 2-3 high pillar, water, a fence with no gate,
a roof, a block put back), and against new harm (griefing, gates left open, loops, stuck NPCs, cost). Found and fixed:

1. **A server crash in `OpenGateGoal`.** Vanilla ticks running goals every tick but checks `canContinueToUse` only
   every other tick. After the goal shut a gate (and cleared it) on a full tick, the next tick ran `tick()` again on
   the cleared gate: a `NullPointerException` in an entity tick, which stops a dedicated server. About every second
   gate closing could do it; the earlier runs were lucky. The new pen-exit test crashed the test server with it.
   `tick()` now returns when the gate is cleared.
2. **A player in a 1x1 hole two deep** (in stone, or in a base's yard, where nothing may be dug). The path reaches the
   player's cell, so the soldier walked to the edge, where it cannot hit down and the player hits it. After 60 ticks
   it backed off, and 40 ticks later the "reachable" path sent it back, again and again (the GameTest counted 5 and
   more blows in a row). Now: close below or above the target (1.5+ blocks) it gives up walking up after 20 ticks
   without getting 2 path steps further (being knocked back is no progress), and that cell of the target's is
   remembered as one it cannot get at: a path that "reaches" it no longer counts (`Pursuit.futile`), at most 3 blows.
3. **A block the player puts back.** The soldier broke the dirt in the doorway, the player put it back, and the
   soldier broke it again, within the player's reach every time. Now a cell it broke for a target and that is filled
   again is fixed for that target (`Reach.world`'s `refused`), and a failure while working (a block put back, out of
   reach, someone in the pillar cell) waits out of reach and plans again 2 s later instead of re-planning at once
   (which could also run the planner every tick: a cost spike).
4. **Blows it cannot answer.** A general net for geometries nobody listed: 3 blows from its target while it works its
   way in, has no path, or stands close below or above it, and the soldier backs off and marks that cell; after 6 in
   all from one target it plans no more breaking or building toward it. A landed blow clears all of it. What it
   learnt is kept per target for 5 minutes, also when the target goes stale and comes back (otherwise every guard
   sent by the defence alarm walked up to the hole again).
5. **Planner and the swing disagreeing.** The planner counts any cell next to the target as striking; the real hit
   box is short of a target at the far side of its cell. The planner said "strike from here", the swing missed, and
   the soldier stayed in CHASE in the player's reach. Now that gives one 20-tick try, then the cell is marked. The
   planner's start was also the NPC's block position, which is the fence's cell when it is pressed against a fence
   (its middle slides past the post): now the open cell its body is in. Breaking the block under a target is planned
   from 4.0 blocks (it may stand anywhere in its cell; the check when breaking stays a player's 4.5).
6. **Shut in the pen after the fight.** Gates were passable only while the NPC had a target, so soldiers that killed
   the player inside a pen could not path out; they stood at the fence until the stuck recovery hopped them over it.
   For 600 ticks after going through a gate in a fight their paths may still use gates (`FactionNpc.gatesUntil`), so
   they walk out the way they came and shut the gate.
7. **Workers.** A civilian "stepping away" was cancelled the same tick (the attack goal stopped and stopped the
   navigation), so it stayed at the fence in the player's reach. Workers now never plan (no breaking or building
   toward anyone): they step out of reach (at most 5 s) and then let the target be.
8. **Arrows while waiting.** A soldier waiting 6.5 blocks off in plain sight was easy to shoot from a box. Shot by its
   target while waiting, it now waits out of the target's sight (or farther off), keeping the target.
9. **Smaller things.** An ally's soldier could break the base's own player's edit in a planned cell (now any fighter
   of the base's side leaves it); a pillar block could go into a mine's stairwell (now never); a flying or falling
   target (no block under it within 2) is chased as before, never built or dug toward; at home, a waiting guard keeps
   an intruder it cannot see (in a hole) as its target while it is within 24 blocks; the hold spot search no longer
   queries the nearby fighters and casts rays inside the sort comparator (up to ~130 entity queries per pick).

Checked and left as they are: gates open only on a fighting path, so working NPCs never walk through pens; a pen
gate opened by a soldier closes behind it, not on anyone in the gateway, with a cooldown against flapping; planned
blocks, containers, fluids, mine stairs and base ground are never broken; pillars are never built in a base of its
own side; the planner is bounded (a box of at most ~21x21x14 cells, a 600-tick budget) and runs at most once per
partial path search (every 19+ ticks) or per 40-tick check while waiting; water (paths swim), a fence with no gate
(planned: wait; a player's own: broken, 60 ticks with a sword), a roof (planned: wait and call the archers).

### Tests

- New GameTests (`CombatReachGameTests`), each with the mock player striking back every 12 ticks when a soldier is in
  its reach and sight: `soldiersDoNotTradeBlowsWithAPlayerInAHole` (at most 4 blows in a row, nobody in reach after
  tick 300), `soldiersDoNotKeepBreakingABlockThePlayerPutsBack` (broken at most twice, at most 4 blows in a row, the
  wall intact, out of reach after tick 300), `soldiersLeaveThePenThroughTheGateAfterTheFight` (out through the gate,
  shut behind them, the cow still in), `aWaitingSoldierTakesCoverWhenShotAt` (behind a wall or 9+ blocks off, still
  targeting), `aWorkerStepsOutOfReachOfAPlayerItCannotGetAt`; the static rules test also checks an ally and a mine's
  stairwell. New unit test `underminesOnlyFromHalfABlockWithinReach`.
- **Sabotage check**: with the review's memory, refused cells, blow counting, gate memory and the workers' step-away
  switched off, all five new behaviour tests fail (`review/gt_sabotage.log` in the session scratchpad; one mining test,
  `secondMinerHelpsInAMineAtTheCap`, also failed once there and passed in all the other runs).
- Final: unit tests 174, GameTests "All 128 required tests passed" in six runs (`review/gt5.log` to `gt9.log` and
  `gt_final.log`; the last two with every change, the first four before the ally rule and the 6-blow budget).

### Still open

- Checked against a real player client on a dedicated server (pens, doorways, a 2-high pillar, a 1x1 hole and pit:
  see "Live check" below), not yet in a multi-day campaign with a player, nor walls, arrows or a player on a roof.
- Vanilla's hurt-by target memory still lets go of an attacker that has been out of sight for 3 s, so a guard
  watching a player in a hole goes back to its post; the next time it is hit it goes straight back to waiting.
- A player hopping along a 2-deep trench gets at most one short walk-up (about 1-2 blows) per cell per soldier.
- A guard that took 3 blows from a player on a ledge it could reach by a long way round (stairs outside the
  planner's box) does not walk up to that spot again while the player stays there; the archers deal with it.

## Live check (dedicated server, a real client as the player)

A fresh campaign (`fc start 256`, seed 20261107), one day sprinted, then the client viewer (`runClientViewer`, the
"Viewer" player) in survival with diamond armour, Resistance IV and Regeneration so it could watch, a member of Ember
Legion in Grove Covenant's base. A script played the player: every 0.3-0.6 s it read the NPCs (`fc admin dump`, which
now also gives each NPC's exact position, its `Pursuit` mode and counters and its current path) and, when a soldier's
box was within 3 blocks of the eyes, turned the view onto it and left-clicked in the client (the client's own pick
decides whether the blow lands; a stick, so the soldiers lived long enough to watch). Blocks were placed and pillars
built with the client's right click (so the server saw a real player place them). Logs and screenshots are in the
session scratchpad (`live/`).

What the soldiers did:

| Case | Seen |
|---|---|
| Player in the pen, gate closed (the owner's first report) | The first guard opened the gate, went in and closed it; all three were in the pen and hitting the player within 10 s; the gate was shut, 2 cows and the pigs stayed in. |
| Player in a house, the door broken and his dirt in the doorway (the owner's second report) | The first soldier to arrive broke the dirt (WORK, about 1 s with a sword) and they came in and fought. Also with the dirt in front of the doorway (outside, an unplanned cell of the yard): broken as an enemy's block. |
| Player on a 2-high pillar of his dirt in the yard | A soldier broke the top block (the player dropped to 1 up) and they hit him from the ground. |
| Player in a 1x1 hole two deep | The soldiers jumped in with him and fought at close quarters (they hit him). |
| Player hitting from the edge of that pit, soldiers in it | **Exploit, fixed** (below). |

Faults found and fixed (commit "Live fixes: ..."):

1. **Shut in a pen after a long fight.** After a 40 s fight in the pen the soldiers could not path out (the 600-tick
   gate memory counted from opening the gate) and the stuck recovery hopped all three over the fence. The memory now
   counts from the end of the fight (`FactionNpc` tops it up while it fights) and a fighter that walks through a
   gateway another one opened learns it too. Live: after a 60 s fight they walked out through the gate in 2 s, no
   recovery hop, gate shut, animals in. GameTest `soldiersLeaveThePenThroughTheGateAfterALongFight`.
2. **A factionless player's pillar.** Blocks were recorded as an intruder's only for players of a hostile faction, so a
   player of no faction who attacked the guards (they fight back) could pillar up in their yard and they could only
   wait. Now any player not of the base's side (`FCEvents.outsider`). Live: the guard broke the top block within
   2 s. GameTest `blocksAnOutsiderPutsInABaseAreItsSoldiersToBreak`.
3. **The pit trap.** Soldiers that jumped into a 1x1 pit two deep after the player (or are knocked in), with the player
   then hitting from the edge, could not hit back (two up), dig (the base's ground), build (their own yard) or walk
   out: they stood there in HOLD until killed (live: three soldiers taken from 15-20 to 0-1 hp with a stick). Three
   causes: building in its own base was forbidden even below the floor; nothing noticed it could not move; and the
   blows knocked it up so that, at the top of each bounce, it "could strike" and every stuck check was reset. Now:
   filling a hole below its own base's floor is allowed (`Reach.mayPlace`), so a soldier with blocks builds itself
   out (and strikes from its block); one waiting boxed in within the target's reach for 30 ticks uses the stuck
   recovery toward the waiting ring (`Pursuit.watchBoxedIn`, at most 3 per target); `canStrikeNow` counts only while
   it stands; a decision put off by a jump is taken on landing (and marks the spot when the stuck rule asked for it);
   the attack goal also starts while it is in the air (no path is searched then); a fresh NPC no longer counts as
   having just landed a blow (`lastAttackTime` started at 0). Live with the pit fixes in their final form: of the two soldiers in the
   pit one built a block within 1 s and climbed out, the other stepped onto that block and was out within 5 s; both
   then fought the player on the surface. (With the version before the last change, which still let a lucky blow
   from the pit reset the boxed-in check, two soldiers hopped out after 4 and 6 s and one took 12 s.) GameTests `aSoldierInAPitGetsOutInsteadOfTakingBlows` and
   `aSoldierInAPitBuildsItselfOutWithItsBlocks`; the static test checks the hole rule.
4. **Standing in the open doorway.** Once the dirt was broken the soldiers stood in the doorway, in the player's reach,
   for about 2 s: the switch from WORK to CHASE was seen in the same tick, so the old path-search timer ran on. Also
   on arriving at the end of a partial path they waited for that timer before planning. Now the attack goal compares
   the mode it started the tick in, and it searches again at once when its path has been walked to the end. Live:
   in through the doorway 0-5 ticks after the block broke. The doorway GameTest now requires a hit within 30 ticks
   of the break (fails without the fix).
5. **A broken door was never put back** when the storehouse had no door (the builder took repair parts ready-made
   only; doors are crafted only for projects): Grove's door stayed broken for 67,000 ticks. A builder now crafts a
   door, fence, torch or chest for a repair from the storehouse's materials (logs for planks and sticks). Live: put
   back within a minute of the restart. GameTest `aBrokenDoorIsPutBackWithNoDoorInTheStorehouse`.

Sabotage checks: with fixes 1 and 2 switched off, exactly their two GameTests fail; with the pit fixes (boxed-in
recovery, building below the floor) off, the two pit tests and the static rules test fail; with the old mode
bookkeeping, the doorway test fails. Final: unit tests 174, GameTests "All 133 required tests passed" in the last
three runs. Two flakes were seen on the way, once each: `soldiersDoNotKeepBreakingABlockThePlayerPutsBack` (the soldier
had let go of the player at tick 495: vanilla's 3 s unseen rule, see "Still open") in 1 of 6 runs with the final
code, and `soldiersLeaveThePenThroughTheGateAfterALongFight` in 1 of 9 runs (its failure message now prints each
soldier's gate memory, gate cycles and path state for the next time).

Seen and left as it is:

- A guard that is pushed onto a 1x1 pit's edge by a crowd sometimes jumps in after the others; with the fixes they get
  out again. A pit dug in a yard stays (it is not part of any plan); a soldier's block left in it fills it.
- Several soldiers in one 1x1 pit block each other's pillar block (someone stands in the cell); the others hop out
  and the last builds up.
- The player's melee damage against a soldier that is breaking its way in is real: a soldier in the doorway breaking
  the dirt takes the blows of a player standing behind it for about 1 s, as a raiding player would.
- Archers walk into a 1x1 hole after the player like the swordsmen (they then shoot at point blank).

## Live check 2 (a fresh campaign, the final code, a real client as the player)

A new campaign (`fc start 256`, one day sprinted), the Viewer client in survival with diamond armour, Resistance III and
Regeneration II, a member of Ember Legion, in Grove Covenant's base. The previous run's fixes (above) were reviewed,
tested (174 unit tests, 133 GameTests) and committed first ("Live fixes: pit trap, pen exit after a long fight,
outsiders' blocks, doorway wait, door repair"). The player script (`player.py` in the session scratchpad) read the
NPCs every 0.3-0.6 s and left-clicked in the client at any faction NPC within 3 blocks of the eyes; blocks were
broken and placed with the client's own clicks. To set a case up, the NPCs nearby were frozen (`NoAI`) for a few
seconds and the soldiers put where they would come from.

| Case | Seen |
|---|---|
| Player in the pen, gate shut | Soldiers opened the gate, went in, shut it and fought; the gate was shut at every 4 s check. Wounded soldiers fell back out through the gate (opening and shutting it) and came back healed; a Grove archer shot from outside the pen. With five fighters going in and out over several fights, two cows got out of the pen at some point (the animal carer leads strays back). |
| Player pressed against the pen's far fence, soldiers coming from that side | **Fault, fixed** (1 below). With the fix: a soldier started outside the far corner, took one blow passing the fence, walked round to the gate on the other side and in (its path ended in the cell inside, "reach"); 67 of the player's 70 clicks were on soldiers inside the pen. |
| Door broken and the player's dirt in the doorway (the owner's second report) | An axe fighter broke the dirt about 3 s after the soldiers arrived, they came in and fought; the builder put the door back about 40 s later, and soldiers coming later opened it and came in. |
| Player on a 2-high pillar of his dirt by the west wall | The top block was broken within 4 s, the bottom within 8 s; they fought him on the ground. |
| Player in a 1x1 hole two deep in the yard | Soldiers, and workers the player had hit, dropped into the hole with him and fought at close quarters (the player was hit). **Fault, fixed** (3 below) for the wounded workers. |
| Monsters | **Fault, fixed** (2 below): two home guards waited on a creeper shut in a pocket of the ground under the yard. |

Faults found and fixed (commit "Live fixes: pen gate on the far side, monsters held at home, workers in a pit"):

1. **The pen's far fence.** A player pressed against a fence stands, by its block position, in the fence's cell. From
   outside, the cell just outside the fence was as near to that cell as the one inside, so the attack path ended
   outside; the planner took that cell as "next to" the player, the soldier walked there, could not hit across the
   fence (the player could), took three blows and then waited out of reach for good, that spot of the player's marked
   as one it cannot get at. Paths and plans now aim at the target's body cell (`Pursuit.targetCell`: the open cell its
   body is mostly in when its block position holds a fence, wall or pane). That alone was not enough: vanilla's path
   search overwrites a node's walked distance with a worse one while it explores the dead end by the fence, and the
   way round to the gate (32 steps) was cut off at the follow range (48); with 64 it is found
   (`NpcPathNavigation.createLongPath`, `Pursuit.PATH_RANGE`; the node budget, and so the cost of a failing search, is
   unchanged). GameTest `soldiersGoRoundThroughTheGateToAPlayerPressedAgainstTheFence` (the player hits across the
   fence; fails with the old aim: 4 blows across the fence, then waiting).
2. **Monsters held at home.** At home a target was kept engaged even out of sight (meant for an intruder hiding in the
   base), so two guards waited on a creeper shut in the ground five blocks under the yard; when it went stale the
   defence alarm (which hands intruders to free guards without line of sight) handed it straight back, while an
   enemy player was in the base. Now only an intruder (a player or an enemy soldier) is kept unseen at home; a
   monster it waits on is ignored for a minute when it goes stale, and given up after a minute of waiting at home as
   away; a guard waiting on a monster turns to an enemy player or soldier in sight within 16 blocks; the alarm hands
   out raiders and players before monsters, skips monsters more than 2 blocks below the yard and guards that gave the
   monster up. GameTests `aGuardWaitingOnAMonsterTurnsToAnIntruder` and `aGuardLetsGoOfAMonsterItCanNeitherSeeNorGetAt`
   (both fail with the old rules).
3. **Wounded workers in a pit.** Workers the player hit while it stood in a 1x1 pit two deep jumped in after it,
   were wounded and could not flee (no way up): a cave explorer went from 8 to 2 hp there. A fleeing worker with no
   way away and its threat within 4 blocks now uses the stuck recovery toward the station stand. GameTest
   `aWoundedWorkerInAPitWithThePlayerGetsOut` (killed in the pit without it).

Seen and left as it is:

- **Knock-back.** A lone soldier walking straight at a player who hits it every 12 ticks is knocked back each time
  and closes in very slowly (a GameTest showed 18 blows in a row inside the pen against a player pressed into the
  fence); a mob's melee reach is shorter than a player's 3 blocks. Vanilla mobs have the same weakness and several
  soldiers together break it (live, the soldiers in the pen hit the player regularly). Giving soldiers a player's
  3-block melee reach would end it but changes the balance of every fight: the owner's call.
- Animals can slip out of the pen while many soldiers go in and out over a long fight (the gate is shut behind each
  of them and never on anyone in the gateway); the animal carer brings strays back.
- A 1-deep hole right in front of a doorway (dug by the player) made soldiers stand in it, unable to path up into the
  doorway for a moment, and take a blow or two before backing off and coming round again.
- Flakes over 34 full GameTest runs here: `soldiersLeaveThePenThroughTheGateAfterALongFight` once (noted above),
  `soldiersDoNotKeepBreakingABlockThePlayerPutsBack` once (the soldier let go of the player: vanilla's 3 s unseen
  rule, noted above), `soldiersHoldOutOfReachOfAPlayerTheyCannotReach` once with intermediate code, and the mining
  test `minerClearsAFloodedTunnelCell` twice (it also failed now and then in earlier sessions' logs; not touched by
  these changes).
