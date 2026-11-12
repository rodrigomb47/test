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
