# NPCs stuck in pits, pacing at a mine landing and in a gateway

Seen in a 15-day campaign (seed 20261107, map radius 256), investigated on a copy of the day-15 world with temporary
`-Dfc.dbg` logging in `Job.goTo`/`digOut`, `NpcNavigator` and `DigOut` (removed again).

## Case 1: two miners (and later a woodcutter) in a hole beside Grove's south wall

**Ground.** Grove Covenant's base (centre 528,64,144) sits on a 20-block-deep ravine. The pad's south edge (z=169,
top y=63, filled with dirt down to y=50 by `Terrain.level`) is a one-block strip outside the wall (z=168); right
beyond it (z=170 and on, x 500-525) the ground drops to y=38-44. `Terrain.skirt` should have sloped the five
columns beyond the pad, but it only looks 19 blocks down for ground: finding none it left those columns alone, so
the wall had a sheer 20-block drop beside it. The ravine's south side is a slope of ledges: easy to walk down,
impossible to walk up on the north side.

**How the NPCs got in.** Long trips use a waypoint 24 blocks ahead, placed on the heightmap. From the west gate
toward mine #51 the waypoint was 491,32,163: the ravine floor. The partial path toward it led down the slope, in one
case to 515,49,157 under the base. Short trips (under 40 blocks) with the ravine in between did the same with the
destination itself: the nearest node a search reached was down in the ravine.

**Why they stayed.** From the ravine floor the miner dug and pillared up toward the west gate (its route target)
and ended in a hole it had dug at 508,60,169, under the pad's protected top layer, right below the gate's direction.
Then a loop, every ~300 ticks, for 8 days:
1. In the hole it is "shut in" (under cover), so `Job.digOut` runs. `DigOut.step` heads out from under the
   settlement (its floor band may not be dug) and steps to 508,61,170: open air on its own pillar.
2. There it counted as free (on the heightmap surface) and only 3 blocks below the gate (`CLIMB` was 4), so the
   digging ended at once.
3. Walking: no route; the vanilla partial path ends at the node nearest the gate, which is the hole. Back in.
Each pass also re-ran a full path search every tick (the path was "done" at once: one node, where the NPC stood).
Two more NPCs ended up in the same hole (the hole is the nearest point to the gate from anywhere in the ravine).

**Fix.**
* `Job.digOut`: only a walking route (`canWalkToward`) ends the digging; in the open it is looked for after every
  step, under cover every 4th. `DigOut.CLIMB` 4 -> 1: an NPC more than a step below its destination with no route
  is shut in, so it digs/builds continuously instead of one step per failed trip (between single steps the walk
  took it straight back into the hole).
* `DigOut.step`: out in the open below the destination it first tries only ways up (a rising bridge step toward it,
  a step dug up in any direction, a pillar), and only then level steps. Before, the first move was a level bridge
  toward the destination, which in this geometry filled the hole's floor and stepped back under the pad.
* Escalation: a round of 64 steps that ends without a route, or with no possible step, makes the next round head
  16 blocks off to one side, then the other side, then away, then toward again (`DigOut.escapeHeading`), always to
  the destination's height; reset on arrival. A safety net: in the live runs every NPC got out in round 0.
* Prevention: `NpcNavigator.waypoint` skips a turn whose ground lies more than `PIT_DEPTH` (8) below both the NPC
  and the destination (a valley where every turn is low still gets the first one). `NpcNavigator.repath`: a partial
  path whose end lies that far below both ends is not followed; the NPC walks to a point off to one side (left
  first, at most once per 100 ticks) whose path does not lead into it. `Terrain.skirt` fills a column with no
  ground within reach like a too-low one (new bases only).
* A path that leads nowhere (one node, partial) is searched again after 20 ticks, not every tick.

**Test.** `npcClimbsOutOfAPitBesideProtectedGround` (batch `pit_escape`, sky access): a test settlement whose
centre lies off the platform so its protected floor band covers a raised pad (z <= 11, top y=5, walked on at 6) but
not the pit (x 6-11, z 12-16, floor y=1) or the hill around it; a three-high wall of plan blocks along z=10 with a
gap at the east end; the storehouse stand behind the wall at (4, 6, 4). A miner with cobblestone, a pickaxe and
raw iron starts in the pit. Before the fix it looped between a hole under the pad and the pit exactly as in the
campaign and timed out (2400 ticks); with the fix it digs steps up into the hill side, steps onto the pad and walks
round the wall to the storehouse, never digging the pad's edge or the wall. Sabotage run on the final code (digging
ends again once out in the open, `CLIMB` back to 4): the test fails, nothing else does.

## Case 2: Hale Stone "walking to the tunnel face", days 4-8 (mine #10) and again on day 15 (mine #65)

Not a dead stall on days 4-8: mine #10's level y=15 kept progressing (1,406 -> 2,440 cells) but at a third of
another miner's rate, with ~25 recoveries a day. Its face was 63 blocks out from the shaft, under a flooded cavern
(water y=13-16, open cave above). The day-4 dump shows the miner at y=17 in that cavern, not in its tunnel. The
same mechanism showed live on the day-15 world at mine #65 (face 73 blocks out, tunnel sealed against a pond):
* Vanilla path searches reach about 48 blocks, and every cell beside water costs 8 more (`WATER_BORDER`, as for a
  river bank). The tunnel past the pond was dearer than a detour up the stairs and over the surface, so the search
  ended on the surface (483,64,83). The miner stepped onto the first stair, where `walkInMine` no longer counted it
  as on the level and sent it back to the landing: a flip every ~10 ticks until the 600-tick watchdog, then one-block
  dig-out steps along the open tunnel. 257 recoveries in a day.

**Fix.** `MinerJob.walkInMine` walks a level in stretches (`TUNNEL_LEG` = 24): to the dug, walkable tunnel cell
within 24 blocks that lies nearest the destination (at least 4 closer), also from the landing and on the way back.
`NpcNavigator.search`: underground, `WATER_BORDER` costs nothing extra (on the surface vanilla's 8 stays). Live: the
miner walks landing -> 86 -> 64 -> 42 -> face in about 340 ticks, 0 recoveries in a day.

## Case 3: Vela Thorne "going back and forth" between mine #51 and the storehouse

Two causes, both seen live:
* The waypoint into the ravine (case 1): from the west gate toward mine #51 the first waypoint was the ravine floor
  at y=32; the NPC paced at the rim or went down into it. Fixed by the pit-aware waypoints.
* Gateways: under a gate's arch the heightmap says "underground", so long trips got no waypoint; from just outside
  the gate (x=552) the direct search to a mine 70 blocks off ended back inside the walls, and one block in (x=551)
  the NPC counted as inside and was sent out to the gate again: a flip every 3 ticks (seen with Cira Reed at the east
  gate; Vela's day-7 position was the west gate). Also `walkInMine` under the arch aimed at a stair deep in the shaft
  instead of the mine's mouth. Fixed: in or right around its own walls box at floor level an NPC is never
  "underground" for path planning (`NpcNavigator.atHome`), and a miner 14+ blocks from its shaft and outside its
  mine heads for the mouth whatever is overhead.

## Live result (world copy of day 15, final jar)

* Cira Reed, Vela Thorne and Hale Ember (woodcutter, fell in on day 15) pillared out beside the pad's edge and were
  at the storehouse about 1,000-1,100 ticks after start; all three then went back to work (the miners in their
  mines #48 and #51 within the next 1,000 ticks; at the end of the day they had 8, 7 and 12 recoveries).
* One full day from the same start: stuck recoveries of all NPCs 592 (old jar) -> 97; highest single NPC 22 (a Tide
  hauler, 19 with the old jar). Grove's stock that day: raw iron 128 -> 204, coal 0 -> 96.
* Fresh campaign, same seed, two days: the ground beside Grove's south wall now slopes 62, 61, 60, 59, 58 over the
  five columns beyond the pad instead of dropping to y=40; 199 recoveries after two days (274 in the earlier fresh
  run). 4.8-5.2 ms per tick.

Not addressed (seen in passing): NPCs walking onto a tree canopy at hill level (Cira on an oak at 483,77,186) still
get down by the existing "perched" rule after a stall; the Tide pen gate gives a few one-block recovery hops a day.

## Review (independent, after commit 707d69f)

**Re-checked.**
* Root cause, on a fresh copy of the day-15 world with the committed jar and temporary `-Dfc.dbg` logging: Cira
  Reed, Vela Thorne and Hale Ember (all three at 508,60,169, problem "going back and forth") started a dig round,
  pillared up to 508,63,170 in 3, 3 and 6 steps, found a walking route there and were walking to their mines and
  the woodland within 1,200 ticks. That matches the hole/pad-edge loop described above.
* The new GameTest is meaningful: the commit's `FCGameTests.java` on top of the pre-fix commit b9a1ec5 fails exactly
  one test, `npcclimbsoutofapitbesideprotectedground` (timeout after 2400 ticks); on the fix it passes.
* Build, 139 unit tests and all 94 GameTests green, on the commit and again with the review fixes.
* Blocks: every block dug or placed while digging out over two in-game days was logged (145 cells). None was a
  structure block; 8 lay within a walls box, all right outside Grove's south wall at y=62 (the pad's dirt under
  its protected top layer, and pillars beside it), which the rules allow.
* Cost: 4.1-4.3 ms per tick while sprinting a full day.

**Found and fixed** (commit "Review fixes: ...").
1. Empty dig rounds (a regression of the commit). `Job.digOut` looked for a walking route before a round's first
   step; the old code always dug 3 steps first. Under cover on a long trip, `canWalkToward` searches toward the first
   surface waypoint (whose chunk `waypoint()` loads), while the walk itself searches toward the destination, and
   vanilla's ground navigation returns no path at all when the destination's chunk is not loaded. Seen live: the Tide
   woodcutter Aldo Ember under a tree at 206,71,-190 went "no path" (every tick, 6 times) -> trip failed -> dig round
   ended at once with 0 steps because a route "existed" -> again: 13 empty rounds in about 130 ticks, some 7 path
   searches each, and it never moved. Fix: no route check before a round's first step (under cover the first check comes
   after 4 steps, in the open after every step). Live after the fix: no empty round in a day.
2. Detours back inside the NPC's own walls. `aroundPit` had no walls rule (`waypoint` has one). Seen live: the cave
   explorer Tamsin Brook, leaving through the west gate for a cave at 562,69,207 (south-east, with the ravine along
   the south wall). Every waypoint turn outside the walls lay in the ravine, so the path led into it; `aroundPit` then
   chose 509,64,167, just inside the south wall. The explorer walked back in, was sent out to the west gate again,
   and so on: 91 detours, and it was still at the gate at the end of the day. Fix: `aroundPit` skips points within
   its own walls box (half + 2) when the trip leaves or passes the walls, like `waypoint`; with no detour left the
   partial path is followed, as before the commit. Live after the fix: one detour search, no detour chosen; the
   explorer went on and surveyed that cave (never reached in the original campaign, nor with the committed jar) and
   six more that day, digging itself out of a cave passage on the way back from one with a 31-step staircase. With
   the committed jar it surveyed one cave that day.

One day each from the same start (day-15 world): committed jar 125 recoveries in all (Aldo's empty rounds, Tamsin
pacing at the gate with 9); review fixes 164 (Tamsin 51 of them, mostly dig steps out of a cave it surveyed; the
runs are not deterministic, so the totals say little). The three NPCs in the hole got out in both runs within about
1,000 ticks; in the second run an animal carer herding at the rim fell in too and pillared out in 3 steps.

**Not changed, worth knowing.**
* `CLIMB` = 1 is measured against the route target, which on a long trip is the far destination. An NPC whose trips
  fail twice anywhere more than a block below its destination's height now digs or builds rounds of up to 64 steps
  (ways up first: rising bridge, dug steps, a pillar) until a route to its first waypoint exists, where before it took
  one step per failed trip for 2 to 4 blocks. Not seen in the two days checked (every continuous round was a pit, a
  cave, a tree or a canopy), but a far valley could get a short pillar. Comparing with the first waypoint's height
  on long trips would be the refinement.
* The gate choice (shortest straight walk through a gate) knows nothing about pits: the explorer's trip above leaves
  by the west gate, whose only ways south-east lead past the ravine, where the east gate would have been shorter.
* `digRounds` is reset only on arrival, not per destination: after unrelated failed rounds a new dig-out may start
  sideways. `canWalkToward` and the walk still disagree underground on long trips (waypoint vs destination); with fix
  1 that costs a few dig steps per round, as before the commit.
* `Terrain.skirt` fills a column with no ground from 19 blocks below the pads; over a deeper ravine that fill is an
  overhang with air under it (walkable, looks odd).
* The review fixes have no GameTest (they need a far, unloaded destination under cover, or a settlement with gates
  beside a ravine); they were checked live only.
