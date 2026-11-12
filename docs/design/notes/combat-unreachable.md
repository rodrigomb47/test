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

- Not checked in a campaign or against a real player (pens, doorways, holes, perches, walls, arrows).
- Vanilla's hurt-by target memory still lets go of an attacker that has been out of sight for 3 s, so a guard
  watching a player in a hole goes back to its post; the next time it is hit it goes straight back to waiting.
- A player hopping along a 2-deep trench gets at most one short walk-up (about 1-2 blows) per cell per soldier.
- A guard that took 3 blows from a player on a ledge it could reach by a long way round (stairs outside the
  planner's box) does not walk up to that spot again while the player stays there; the archers deal with it.
