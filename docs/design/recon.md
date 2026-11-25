# Focused reconnaissance: a council judges a defence only from a scout sent to watch it

Design, 2026-11-25, reviewed the same day (section 13 lists what the review changed). Not built yet; tasks R1 to R9
(section 11) build it. It replaces the estimate model of `docs/design/knowledge.md` section 1 (the prior, the losses seen
at home, every passing look).

## 0. The owner's decisions, and the design in brief

The owner's words (2026-11-25):

* "Its fine to keep a hard defence, i believe as more tools and hero presence are introduced things will change"
* "Only estimate enemy defence when the scout or other related role makes a report of what he saw while focused on this
  particilar task"
* "Lets remove the pacts thing, if a faction gets eliminated, all of their members dies" (built by another workflow;
  nothing here uses pacts).

The design in brief:

1. A war council estimates an enemy settlement's or lair's defence **only from a focused report**: a scout ordered at
   home to watch that one base, which walks there, finds a vantage, watches for a minute and carries what it saw home to
   the station. Nothing else feeds the estimate. There is no prior, no loss counting, no raid party's look and no
   passing look.
2. A scout outside 5-high walls sees none of the yard (section 3, table 3.2). A **dirt pillar** (the woodcutter's
   `Scaffold`, a player's nerd-pole) of 10 to 20 blocks (12 to 14 on a gate side), 61 to 72 blocks from the centre, sees
   32 to 60% of it. The scout climbs block by block, looking after each block, and stops two blocks above the first
   height where the look is good enough. At 61 blocks or more no place in the yard within the guards' 48-block reach has
   a line of sight to it, so a player standing there would be just as safe from the yard's guards (section 3.5; soldiers
   passing outside the walls are the one difference left, section 8).
3. A look is **good enough** only when at least 8 of BaseWatch's 25 yard points (32%) and the yard's middle are in view.
   A worse look is a **blind report**: "could not see inside". It never becomes "about 0 defenders" and never lets a
   council launch.
4. The scout **counts faces**: each soldier is counted once by its entity, as a player tells soldiers apart by their name
   tags, over a 1200-tick watch. Guards change posts about every 8 to 10 s. In the model, from the height where the scout
   actually stops (two blocks above its first good look), this finds 99% of a garrison on average and at least 95% from
   the worst vantage (section 3.4). The estimate is that count with no extrapolation (question 1).
5. A report is **fresh for half a day**. The council orders a recon half a day before its raid interval ends. A council
   with no fresh, good report on a target does not launch on it.
6. The **counter-attack** keeps its 3 to 2 rule but now rests on an urgent recon. Enemy soldiers seen at our walls no
   longer lower any estimate: they make the council send a scout to their base once the fight is over (section 6).
7. Replaying the 16 logged council launches (section 5.4): 9 would still go with the same party, and at least 7 of
   those 9 failed in the log. 5 of the 7 that wait face a base with a hero. Expect about 55 to 70% of today's launches.
   Fewer parties die in absolute numbers (no launch on a false "about 0"), but a launch fails about as often as today,
   because the hard defence the owner keeps decides it, not the estimate.

## 1. Who does a recon

| Who | Recon? | Why |
|---|---|---|
| **Scout** (`NpcRole.SCOUT`, `ScoutJob`) | **yes** | the role whose job is to go and look; it already walks far, steps round monsters and reports at the station |
| **A hero on the scout duty** (`behaviour.duty: scout`; `HeroLoadout.dutyRoleId` makes it a scout, and it runs `ScoutJob`) | **yes**, exactly as a scout | a hero on a worker duty does that job as that worker does (A4). Its flight or climbing as a vantage is left for later (section 8) |
| The talents `SPYMASTER`, `CARTOGRAPHER` (a leader's, `logic/leader/Talents`; a scout-duty hero's mastery, `logic/hero/WorkMastery`; both pure rules that the game does not read yet) | not now | the hooks for later: SPYMASTER would shorten the watch or name the heroes seen. Its leader text today says "base reports carry garrison counts"; when it is built it may only improve a focused recon, never give a passing report a count (section 9) |
| Soldiers on a raid, raiding heroes (`FactionState.raiders`, `HeroRaid`) | **no** | they are attacking, not watching: their looks never feed an estimate. They still bring home the ruins they saw or made (section 7) |
| Scouts on sector trips (exploring) | **no** | a passing look tells **where** a base is and that it stands (walls seen). It carries no count |
| Cave explorers, traders, fishermen, miners, woodcutters | **no** | not their task; their trips never go to watch a base |
| Guards at home | **no** | they see enemy soldiers at our walls; that **triggers** an urgent recon (section 6), it is not a report on the enemy's home |
| A member player | no change | players file no base reports today; a player-filed recon is a later item |
| The Hollow | n/a | it estimates no defence (section 8) |

A hero on the wall top of the base it watches is not a vantage, for two reasons. First, no code puts it there:
`WallEntry` runs only for a hero in a raid party, after the muster, when the party is 24 blocks from the walls
(`HeroRaid`); it chooses a way over the wall and down inside (flight, a climb, a glide), never a stand on the top, and
its jump-up links reach 3 blocks, not today's 5-high wall. Second, the wall top is inside the base's alarm box
(`defenceAlarm`: the walls plus 4, up to 16 above the floor), so a hero standing there is an intruder that any member
within 24 blocks with a line of sight hands to the guards. Its coverage, for the record, is in table 3.3. The unbuilt
"scouting ahead" of `MOBILITY_AND_WORK.md` (a flyer on a raid circling the target and feeding the faction's report)
is a raid party's look and must not feed an estimate either (section 9).

## 2. When the council orders a recon

### 2.1 Orders, and where they sit in `FactionDirector.raids`

`raids()` returns early in five places: while a raid is out, on the `raidEndNews` wait, under `interval / 4`, while
`t - startedAt < interval`, and when `party <= 0`. The recon orders run **after the first two and before the last
three**, with the soldier census moved up so the party size is known:

```
raids(): [raid out: unchanged, watchTarget keeps only ruins] return
         [raidEndNews wait] return
         census (soldiers, draftable, party)              <- moved up, unchanged
         ReconOrders.settle(...)                          <- lapse orders whose scout is missing; drop fallen targets
         nextRaidAt = max(f.lastRaidAt, data.startedAt) + interval
         if party > 0 and (t >= nextRaidAt - RECON_LEAD or an urgent recon is due, section 6): ReconOrders.place(...)
         [interval / 4 gate] [data.startedAt + interval gate] [party <= 0]
         choose a target among reports with a USABLE focused recon (2.3); none: LAST_COUNCIL "waiting for a scout's report"
         go / wait as today (two thirds at the interval; 3 to 2 inside it, only against a faction that struck us, 5.4)
```

**What makes the council want a judgement (the order window):**

* **Interval nearly ripe:** `t >= nextRaidAt - RECON_LEAD`, where `nextRaidAt` is the end of the raid interval (the
  first war's gate, `data.startedAt + interval`, before any raid). `RECON_LEAD` = 12000 ticks, half a day.
* **The enemy struck us** (urgent, section 6): enemy soldiers of a faction were seen at home since our last raid
  (`struckBy[faction] > lastRaidAt`), the counter-attack window is open (`t >= lastRaidAt + interval / 4`), the base's
  alarm has been quiet for `NOTICE_MEMORY` (600 ticks: the fight is over, so the scout does not walk out through the
  attackers), and no good recon of that faction's nearest base was made since the strike. A strike seen while our own
  raid was out is acted on at the first pass after the raid's end, as long as the window is still open.
* Inside the window, a target qualifies when it was **never judged**, its last focused report is **stale**
  (older than `RECON_FRESH`) or **blind**, and it is not backing off (2.4).

**Which target, when several qualify (one order at a time):** an urgent target first; then a target never judged,
nearest first; then a stale one, the lowest last estimate first (the likely target), then the nearest. Lairs and
settlements are in one list.

**How many at once:** at most one ordinary and one urgent order open per faction (`FactionState.reconOrders`, at most 2).
Each is taken by one scout. A target is never ordered twice while its report is fresh or an order on it is open.

**Orders are given at home.** The order is written at the station. A scout takes it only at home: at the station when
it reports (`REPORT`), or at the storehouse before it sets out again (`ScoutJob`'s `HOME` state, where it unloads and
picks its next trip; an open order wins over exploring). A scout out exploring does not know of it until it is home.
This is player-like: news reaches people where they are.

**How an order ends** (`ReconOrders.settle`, every pass; each ending is one of the faction's own observations):

| ending | counts as |
|---|---|
| its scout reports at the station with a good recon of the target | done |
| its scout reports with a blind recon | a failed attempt (back-off) |
| its scout reports with no recon of it (it retreated, or found no vantage within reach) | a failed attempt |
| its scout goes MISSING, or is known or presumed dead | a failed attempt (lost) |
| its scout is no longer a scout (retrained) | reopened; not a failure |
| untaken while the target's report turned fresh by other means, or the target was seen in ruins | dropped |
| untaken for `RECON_FRESH` | dropped; the next pass may place it again |

Without these endings, an order whose scout came home empty-handed would block every later order, since 2.1 allows one
open order per target and two per faction.

### 2.2 What the council does while it waits

It launches on no target without a usable report. It may launch on another target whose report is fresh and good. Its
line says so, for example `LAST_COUNCIL`: "waiting for a scout's report on stoneguard base 3 (ordered day 4.10, Edrin
Cole on the way)". With no usable report on any target, it does not launch at all. This is the owner's rule, not a
fault.

### 2.3 A usable report

A report is usable when it holds a **good** focused look (section 3.4: at least 8 of the 25 points and the middle in
view, watched at least `RECON_MIN_WATCH`) and that look is **fresh**: `now - recon.at <= RECON_FRESH` (12000).

`RECON_FRESH = RECON_LEAD`, so a report made in answer to an order is still fresh when the interval ends: it cannot be
made before the order. A council that judged "wait" re-judges at most every half day, each time on a new recon. A report
does not drift toward anything as it ages. Stale means "order a new recon", never "assume something".

### 2.4 Deadlocks and spam

| Case | What happens |
|---|---|
| No scout alive | the existing need "no scout left" hires one; it takes the open order at home |
| The only scout is away exploring | an order not taken within `RECON_WAIT` (6000) adds the need "a scout to watch the X settlement", while the base has fewer than `RECON_MAX_SCOUTS` (2) scouts |
| The recon scout dies (unseen) | the roll call reports it MISSING by its recon plan plus the grace (2.5). The order lapses as **lost** and the target backs off |
| Scouts die every time at one target | the back-off doubles with each consecutive failure: 12000, 24000, then 48000 at most. Other targets are reconned first, and each loss costs one recruit at the most every half day |
| A recon comes back blind | the target backs off `RECON_RETRY` (12000). Its next recon starts at the first side not yet tried (3.5), so after two trips all four sides have been tried |
| A target blind on every side (trees, a hill, a wall too high for the 20-block cap) | it stays unjudged and backs off up to 48000 at a time: a council never launches on it. This is the owner's rule, not a fault; the live check counts the targets that never got a good recon (10.4) |
| Every target is blind or backing off | no launch: the councils wait. The live check counts and reports this time (10.4) |
| Enemy soldiers pass the pillar (the vantage is on the gate axis, the enemy's road) | the scout holds still on top and keeps watching; it does not come down into them (3.5). The hold is bounded by `RECON_HOLD_MAX`, and the trip's plan allows for it (2.5), so a live scout does not go MISSING for waiting |
| Spam | one open order per target, two per faction. An urgent order at most once per `interval / 4` per enemy faction. No orders while the faction's own raid is out |

### 2.5 The recon trip's plan (roll call)

`ScoutJob.tripPlanTicks` gets a recon branch: `Whereabouts.reconPlan(now, departedAt, blocks)` = `walkTicks(blocks)` +
`RECON_VANTAGE_TICKS` (4800: two sides, each a climb and a descent of up to 20 blocks, about 660 ticks; the walk round
between the two gate sides, about 200 blocks, 2000 ticks at the plan's 10 ticks a block; and a hold of up to
`RECON_HOLD_MAX`, 1200) + `RECON_WATCH_TICKS` (1200) + `walkTicks(blocks)`, from departure. It is fixed when the scout
leaves, like every trip. For a target 250 blocks away that is 11000 ticks; MISSING comes at 13400 (with the 2400 grace)
and PRESUMED_DEAD 12000 later. The real trip takes about
4500 to 6500 ticks: 1250 ticks each way at a walk, about 170 to climb 14 blocks, 1200 to watch and about 300 to take the
pillar down by hand (15 ticks a block of dirt, and the drop), plus about 1000 for a second side.

The need "follow-up for overdue scout" today reads `departedAt + SCOUT_OVERDUE_TICKS`. It reads the roll call instead:
the base's only scout is MISSING. That works for both trip kinds.

## 3. How a recon sees a yard behind 5-high walls

### 3.1 The model

Throwaway Python scripts (in the design session's scratchpad, not kept in the repository; every number they produced is
below) reproduce `BaseWatch.look`:

* the 25 yard points: `reach = half - 2` (22), at `centre + 0.5 + {-22, -11, 0, 11, 22}` on both axes, 1.5 above the
  floor;
* rays from the observer's eyes (1.62 above its feet), stopped by every block with a collision shape (`ClipContext.Block.COLLIDER`).
  The test is exact: a voxel walk with box tests;
* the blocks from `BaseLayout`: the wall ring y 0 to 4 with crenellations at y 5 every other block, closed gate doors
  (`OpenDoorGoal(this, true)` shuts them behind everyone), the station, storehouse, workshop and two houses (walls 0 to 3,
  a roof at 4), the lantern posts, and in the **grown** layout the five 7x7 expansion houses (`ProjectLayouts.house`);
* flat, treeless land at the base's floor level outside. Real ground has hills (better) and trees, which stop rays
  (worse; the grove pad lies outside the west gate). This is why the procedure in 3.5 climbs and **looks** rather than
  trusting a height from these tables;
* eyes at 1.62 for every faction NPC (`ModEntities`: `eyeHeight(1.62F)`); a scout-duty hero whose form changes its eye
  height looks from its own eyes, and the climb finds its own height.

The review rebuilt this model independently from the same sources (an exact segment-and-box test, the same blocks, the
doors' and panes' thin shapes) and got the same tables to the point, except on the diagonal, where the column's rounding
moves a height by one block. Section 3.3's tables are the review's, for the band the design now uses.

### 3.2 From the ground: nothing (the live result reproduced)

Share of the 25 points in view, eyes 1.62 above flat ground:

| distance from the centre (from the wall face) | 32 (7.5) | 40 (15.5) | 48 (23.5) | 56 (31.5) | 64 (39.5) |
|---|---|---|---|---|---|
| north, south, west, east | 0% | 0% | 0% | 0% | 0% |
| north-west (a corner; 32 on the diagonal is inside the wall ring) | inside | 0% | 0% | 0% | 0% |
| the gate line, west, **doors open** | 20% | 20% | 20% | 20% | 20% |

(An earlier version showed 36% for the north-west corner at 32 blocks: that column, (-23, -23), stands inside the wall
ring. From the ground outside the walls no side sees any of the 25 points.) The doors are shut behind everyone, so the
gate line is a glimpse, not a vantage. This matches the last live campaign: 23 of 26 passing looks saw none of the yard.

### 3.3 From a height: pillar or high ground (N blocks above the floor)

Share of the 25 points in view; `*` marks a look that also has the **middle** (the plaza point) in view. **S** is the
start layout and **G** the grown one (five expansion houses). The band a recon uses is 61 to 72 blocks from the centre
(3.5); the rows at 52 to 60 are the designer's first band, kept for comparison.

| side, distance | N=8 S/G | N=10 S/G | N=12 S/G | N=14 S/G | N=16 S/G | N=18 S/G | N=20 S/G |
|---|---|---|---|---|---|---|---|
| west (gate), 52 | 20/16 | 40*/32* | 40*/32* | 40*/36* | 56*/44* | | 56*/44* |
| west (gate), 56 | 20/16 | 28/24 | 40*/32* | 40*/32* | 44*/36* | | 56*/44* |
| west (gate), 61 | | | 40*/32* | 40*/32* | 40*/36* | 56*/44* | 56*/44* |
| west (gate), 68 | | | 36/28 | 40*/32* | 40*/32* | 40*/36* | 52*/40* |
| south (houses), 56 | 16/8 | 20/20 | 40*/20 | 40*/20 | 40*/32* | | 60*/40* |
| south (houses), 61 | | | 40*/20 | 40*/20 | 40*/20 | 52*/32* | 60*/40* |
| south (houses), 68 | | | 28/20 | 40*/20 | 40*/20 | 40*/20 | 48*/32* |
| north (halls), 56 | 12/0 | 32/12 | 40/20 | 40/20 | 52*/24* | | 60*/40* |
| north (halls), 61 | | | 40/20 | 40/20 | 40/20 | 60*/32* | 60*/40* |
| north (halls), 68 | | | 40/20 | 40/20 | 40/20 | 40/20 | 60*/32* |
| north-west, 56 | 20/8 | 24/8 | 40*/20* | 40*/24* | 40*/24* | | 40*/24* |

The east side gives the same numbers as the west, to the point. Smallest pillar (0 to 24 tried) that gives a **good**
look (at least 8 of the 25 points and the middle in view), on the side's axis, with the range over the columns up to 8
blocks to either side of the axis in brackets:

| side | 61 S / G | 64 S / G | 68 S / G | 72 S / G |
|---|---|---|---|---|
| west or east (a gate) | 12 (10-12) / 12 (10-16) | 13 (12-13) / 13 (12-17) | 13 (11-13) / 13 (13-18) | 14 / 14 |
| south | 12 / 17 | 13 / 18 | 13 / 19 | 14 / 21 |
| north | 17 / 18 (17-18) | 18 / 19 (18-19) | 19 / 20 (20-21) | 21 / 21 |

(The designer's first band, for comparison: gate sides 10 to 13 at 52 to 64 blocks, south 10 to 13 / 15 to 18, north
15 to 18 / 15 to 19, the north-west corner 11 to 13 on the start layout and never on the grown one.)

So on a gate side a pillar of 12 to 14 blocks on the axis is good from 61 to 72 blocks on both layouts; columns off the
axis on a grown base need up to 18 (the expansion houses at the gate's corners). The other two sides need 17 to 21 on a
grown base, which is why the cap is 20 and the gate sides come first (3.5). In the band, no single vantage sees more
than 60% (start) or 48% (grown) of the 25 points, because several points lie in 2-block alleys between the halls and the
wall: the near side's row of points, 1.5 blocks inside the wall, is never in view from outside. Every point is seen from
**some** side. Two opposite vantages see 68 to 80% (start) and 40 to 60% (grown). A finer grid over the open yard only
(81 points, those inside a building or under a roof left out) gives nearly the same shares: 42 to 58% from one gate-side
vantage at N 12 to 16, 73 to 79% from two. The 25-point grid stays (no change to `BaseWatch`'s grid for settlements).

Other vantages:

| vantage | 25-point share, start / grown | used? |
|---|---|---|
| a hero on the wall top: west wall at the gate / north middle / south middle / NW corner | 60 / 24 / 64 / 12% and 40 / 4 / 16 / 4% | no: inside the alarm box (section 1) |
| a flying hero hovering N blocks up | as a pillar of N | later (section 8) |
| a tree top | as high ground, if it can stand there; leaves stop rays | not sought: NPCs do not climb trees, and woodcutters thin the trees near a base |

### 3.4 Watching over time: counting faces

`GuardJob` sends each guard to a random post (the inner side of each gate, the plaza, the station stand **indoors**, the
staging chest). It stays until its timer passes `160 + random(200)`, a bound drawn afresh every tick, so a stay ends
after about 160 to 200 ticks (rarely more), and then walks to another. Every 6000 ticks it visits the storehouse
(indoors). Guards keep their posts at night (faction NPCs do not sleep). Model: guards walking at 0.2 blocks a tick in
straight lines, a look every 40 ticks (the scout's cadence), and 12 to 40 trials per cell. Garrison counted, as a share
of the truth, from one vantage at 56 blocks with N=14:

| vantage (coverage) | watch | distinct faces / coverage | max(faces, mean in sight / coverage) | **distinct faces** |
|---|---|---|---|---|
| west (48% open yard) | 1200 | 2.09 | 1.29 to 1.33 | **1.00** |
| south (42%) | 1200 | 2.37 | 1.13 to 1.22 | **1.00** |
| north (44%; every post hidden) | 1200 | 2.17 to 2.20 | 0.95 to 0.96 | **0.95 to 0.96** |
| north (44%) | 2400 | 2.28 | 1.00 | **1.00** |
| west + east, split watch (73%) | 1200 | 1.37 | 1.33 to 1.35 | **1.00** |
| north + south (73%) | 1200 | 1.37 | 1.00 to 1.04 | **1.00** |

Spreading the faces over the unseen share of the yard (the lead's example) counts the garrison 1.4 to 2.4 times. Guards
who walk through the part in view are already counted, so the area correction counts them again. **Distinct faces**
finds the garrison. Over many vantages (5 sides, 52 to 60 blocks, N 4 to 16, both layouts, 6 guards), the share of the
garrison counted against the vantage's coverage:

| 25-point coverage | watch 600 | watch 1200 |
|---|---|---|
| 0 to 9% | 5% | 8% |
| 10 to 19% | 34% | 51% |
| 20 to 29% | 65% | 83% |
| 30 to 39% | 81% | 93% (worst vantage 29%) |
| 40 to 49% | 87% | 96% (worst vantage 71%) |
| **at least 8 points (32%) and the middle in view** | 94% (worst vantage 72%) | **99.1% (worst vantage 92%, worst single watch 67%)** |

Coverage alone is not enough: a vantage that sees 40% of the yard but only its corners misses the guards' routes. The
**middle** decides. Hence the rule: a look is **good** when it has at least 8 of the 25 points (32%) and the middle in
view, held for a watch of at least `RECON_MIN_WATCH` (600) ticks. The planned watch is `RECON_WATCH_TICKS` (1200).

The scout stops climbing at a height it chose, not at a random good vantage. At the height the procedure actually
reaches (17 vantages: west, south, north and north-west at 53, 56 and 60, both layouts; 6 guards; 30 trials each):

| where it stops | watch 1200: mean (worst vantage, worst watch) | watch 600 |
|---|---|---|
| the first good height | 98.5% (93.3%, 67%) | 91.0% (72.2%, 33%) |
| **two blocks above it** (the procedure, 3.5) | **99.1% (96.1%, 67%)** | 93.3% (77.2%, 33%) |

The review rewrote the guard model on its own (the posts and routes of `GuardJob`, the station and storehouse reached
through their doors, the per-tick stay rule above, 0.2 blocks a tick, a sample every 40 ticks, eye-to-eye sight on a
half-block grid, 6 guards, 40 watches per vantage) and ran it on the band the design now uses:

| vantages (cap 20) and where the scout stops | watch 1200: mean (worst vantage, worst watch) | watch 600 |
|---|---|---|
| gate sides at 60 to 63, both layouts: the first good height | 98.0% (89.2%, 67%) | 89.7% (74.2%, 33%) |
| gate sides at 60 to 63, both layouts: two blocks above it | 99.1% (94.6%, 67%) | 94.2% (80.0%, 33%) |
| **every side within the cap, 61 to 72 blocks, both layouts** (121 vantages), two blocks above | **99.4% (94.6%, 67%)** | 95.0% (79.6%, 33%) |
| the same, start layout only (63) | 99.9% (98.8%, 83%) | 97.4% (93.3%, 50%) |
| the same, grown layout only (58) | 98.8% (94.6%, 67%) | 92.5% (79.6%, 33%) |

Seven columns of the band have no good look under the cap (north at 68 to 72 on the grown layout, north and south at
72): the column search moves on to another column or side. A **small garrison**: from the gate sides at 61 blocks, a
garrison of 3 was never counted as 0 (nor as fewer than 2 at 1200 ticks) in 300 watches per vantage; a lone soldier was
missed in up to 16% of 600-tick watches on a grown base and up to 3% of 1200-tick ones. Every council keeps at least
`homeDefenders` (3) at home, so a good recon's "about 0" means the garrison is gone.

This is a model of guards only. Soldiers who stay indoors the whole watch are not counted, and heroes and the Hollow's
monsters move by other rules. The live check measures the count against the truth (10.4).

### 3.5 The vantage procedure (settlements)

1. **Blocks.** Before leaving, the scout takes `RECON_BLOCKS` (22) dirt or coarse dirt (`Scaffold.isItem`) from its
   storehouse and keeps them when it unloads (its `keep` rule, as a woodcutter keeps `Scaffold.CARRY`). If the storehouse
   is short, it digs the rest by hand from open ground outside its own walls box, under `DirtDig`'s rules, as a
   woodcutter does (`DirtDig` speaks of the woodcutter's forest radius; the scout uses its own base's home area instead).
2. **The side.** It looks for a side with a gate (doors in the wall, seen as it approaches; `BaseLayout` puts them west
   and east), the one facing home first, then the other gate side, then the side facing home, then the last. At most
   `RECON_SIDES` (2) sides per trip. A further attempt on the same target starts at the first side in that order not
   yet tried (the report keeps a 4-bit mask of the sides tried, `reconSidesTried`, cleared by a good recon), so a blind
   side is not tried first twice and two trips cover all four. It walks round from one side to the next outside the
   standoff, never across the alarm box.
3. **The column.** On that side's axis through the gate, ±8 blocks sideways, at a Chebyshev distance from the centre of
   `RECON_STANDOFF` (61) or more and a horizontal distance of `RECON_SIGHT` (72) or less. Among the columns it can walk
   to, it prefers the axis and the ground nearest the standoff, and on uneven land the highest standing ground (higher
   ground means a shorter pillar), with a solid top (not water or leaves), open sky for 22 blocks and a loaded chunk, in
   no base's plan or alarm box. A column it cannot walk onto is not ground: the top of an old pillar, a tree, a roof.
   * **Why 61: the guards' sight, the same for a player.** Guards pick a target with vanilla's
     `NearestAttackableTargetGoal` (`FOLLOW_RANGE` 48, a line of sight eye to eye). Checked over every place a guard
     can stand in the yard (a 1-block grid, eyes 1.62 up), for every column 8 blocks to either side of each axis and
     every pillar height from 6 to 22, both layouts (run at 59, 60, 61, 64, 68 and 72 blocks): from 60 blocks out no
     place within 48 blocks has a line of sight to the scout's eyes; at 59 up to 9 places do, and at the designer's 53
     between 11 and 21 places did at the height where the scout stops (the staging chest's guard post among them, on the
     east side at N 10 to 12). The walls hide the scout from the near half of the yard, and the far half is more than
     48 blocks away. 61 keeps one block of margin.
   * The designer's band (53 blocks) was safe only because of a quirk of vanilla's targeting: for a target that is not a
     player, `NearestAttackableTargetGoal.getTargetSearchArea` is `inflate(range, 4.0, range)` (read in the 21.1.209
     sources), so a guard never picks an NPC 6 or more blocks above it, while a **player** on the same pillar is found
     by `getNearestPlayer` at any height and would be attacked. A rule that keeps an NPC scout safe where a player
     would not be breaks equal treatment, so the standoff does not rely on it for the yard's guards. (Soldiers passing
     below the pillar are the one case the quirk still decides: "If enemies come near", below, and section 8.)
   * 61 is also outside the alarm box (the walls' half 24 + 4 = 28) and outside the box in which a base notices an
     approaching hero (that box + `FightMobility.APPROACH_SIGHT` 24 = 52, only for heroes).
   * `RECON_SIGHT` (72) is the focused look's reach; a passing look keeps `BaseWatch.SIGHT` (64). A player sees much
     farther; the band 61 to 72 gives the column search 12 blocks of depth instead of 3.
   * The east vantage lies inside the enemy's work belt (`BaseLayout.WORK_AREA_EAST` reaches 78 blocks), the west one
     just outside it (58): enemy workers may pass; they are civilians and do not attack.
4. **Climb and look.** At the foot it looks once from the ground: natural high ground may already be good. Then it pillars
   up as a woodcutter does (`Scaffold.place`, `Scaffold.holdStill`, a `Scaffold.Pillar` saved with the job), looking
   (`BaseWatch` recon sample, 3.6) after each block from 4 up. At the first height whose look is good it climbs
   `RECON_CLIMB_PAST` (2) more blocks while under the cap (3.4: the worst vantage then counts 95% rather than 89%), then
   stops. The cap is `RECON_PILLAR_MAX` (20; table 3.3: 12 to 14 on a gate side's axis, 17 to 21 on the other sides
   of a grown base).
5. **No good look at the cap.** It takes the pillar down and walks round to the next side (step 2). After 2 sides it goes
   home with a blind report ("could not see inside", with the best coverage it had).
6. **Watch** `RECON_WATCH_TICKS` (1200) from the top (3.6).
7. **Down.** It breaks the pillar block by block under its feet, the blocks going back into its backpack (as a woodcutter
   does, 15 ticks a block of dirt by hand), and leaves nothing behind. Then it goes home (`RETURN`, then `REPORT`).

**If enemies come near.** The vantage is on the gate axis, which is the road the enemy's parties and workers use, so
soldiers will pass below. A player on a nerd-pole stays up while a patrol passes, and so does the scout:

* An enemy soldier, hero or player in sight within `RECON_THREAT_RANGE` (16) blocks: the scout holds still on top and
  goes on watching (what it sees still counts); it does not start down while one is that close.
* It comes down early only when it is **hurt** (an arrow, a blow, a monster's shot) or a block of its pillar is broken
  under it. Then the watch ends, it comes down block by block (a drop would hurt more) and retreats home as today. What
  it saw counts only when the watch had already lasted `RECON_MIN_WATCH` and was good; otherwise the report is blind.
* A watch that ends with an enemy still within 16 blocks waits on the pillar for it to leave, at most `RECON_HOLD_MAX`
  (1200 ticks), then comes down regardless. The hold is part of `RECON_VANTAGE_TICKS` in the trip's plan (2.5), so a
  scout that waits is not reported missing for it.
* **The one difference from a player left.** The yard's guards cannot see the scout (above), but a soldier passing below
  can. Vanilla's target search (`inflate(range, 4.0, range)` for a target that is not a player) means it never picks an
  NPC 6 or more blocks above it, while a player on the same pillar would be attacked. This quirk is older than this
  design and holds for every NPC on a pillar (a woodcutter's too). The hold rule is written so that it does not matter:
  if the guards' search is ever made the same for NPCs and players (a combat item for later, section 8), a passing
  soldier will shoot or break the pillar, and the hit or the broken block brings the scout down as for a player.
* Monsters are handled by its usual step-aside rules on the ground. On the pillar it cannot step aside: a hit ends the
  watch as above. The pillar of a scout killed up there stays standing (a player's would too).

### 3.6 What a watch records

Every 40 ticks (the existing `observe` cadence) the scout takes one `BaseWatch` sample of the target. Over the watch it
keeps:

* the **union of the yard points** seen (a 25-bit mask); the coverage is its share;
* whether the **middle** point was seen at least once;
* the **distinct soldiers** seen, by entity id (as a player tells faces apart by name tag), each with its visible power
  (`FactionDirector.power`: armour worn and weapon held) at its last sighting;
* the **heroes** seen, by hero id, with their war power at full strength (as `BaseWatch.seenHeroPower` does today);
* the watch's first and last sample times, and the vantage ("pillar 12", "high ground", "ground").

`BaseWatch.look` is split in two. A **passing look** (exploring, or on the way) reports the base's position and that its
walls or people were seen; it carries no count and no heroes. A **recon sample** returns the point mask, the middle flag,
and the soldier and hero ids with powers. It uses the same rays and the same `hasLineOfSight` for soldiers.

### 3.7 Lairs

`LairLayout` contradicts "lairs have no walls". A lair has 4-high walls with 3-wide, 3-high gaps in the middle of each
side and a roof over the 13x13 hall. `BaseBuilder.spawnLairMob` spawns zombies (60%) and skeletons at the lair's spawn
point inside the hall and leashes them 10 blocks from the centre (`restrictTo`; `FCEvents` adds
`MoveTowardsRestrictionGoal`). `FCEvents` also gives every zombie and skeleton (`CombatRules.huntsNpcs`), lair monsters
included, a goal to target faction NPCs. A zombie's `FOLLOW_RANGE` is 35 (vanilla), and its search box reaches 4 blocks
up and down, so a scout watching from the ground is in it. Today's grid (`reach = half - 2` = 5) lies inside the roofed
hall, where a pillar sees less (the roof stops it). The monsters live on the ground round the hall.

| lair grid, vantage | ground | N=4 | N=8 | N=12 |
|---|---|---|---|---|
| hall grid (today), on a gap's axis, 24 / 48 | 20%* / 20%* | 12%* / 20%* | 4% / 12%* | 0% / 8% |
| **ground grid (reach 10)**, on a gap's axis, 32 / 48 | **52%* / 60%*** | 48%* / 60%* | 40% / 52%* | 56% / 56% |
| ground grid, a corner (north-west), 48 | 36% | 36% | 36% | 36% |

Decision: lairs follow the **same rule** (an enemy defence; a lair is a target). The differences:

* the lair grid is the monsters' ground: `reach` = the leash (10). The middle is the point by the heart;
* the vantage is the **ground** on one of the four gap axes, at `RECON_LAIR_STANDOFF` (46: the leash 10 + a zombie's 35 +
  1) to 64 blocks from the centre. No pillar: the roof hides the middle from above. If one axis is blind (a hill, trees),
  the next axis;
* the count is distinct monsters by entity, power 1 each.

The guard model says nothing about wandering monsters, so for lairs there is no evidence yet that counting faces finds
them all; the live check measures it (10.4). Three more things the review checked in the code and the 21.1.209 sources:

* **The roof has holes.** `LairLayout` leaves one roof block in nine out at random and breaks a third of the walls' top
  course. From the ground it changes nothing (the review's runs on three random lairs: 60% with the middle at 46 to 60
  blocks on a gap axis, every time; the gaps decide); from 8 to 12 blocks up the holes move the share between 52 and
  68%. The table above is the lair with no holes.
* **46 holds for most zombies, not all.** `Zombie.handleAttributes` may multiply a zombie's follow range by up to 2.5 at
  spawn when the regional difficulty's special multiplier (`DifficultyInstance.getSpecialMultiplier`) is above two
  thirds. That multiplier grows with the world's age (from day 3, slowly), the moon and the chunk's inhabited time; on
  normal difficulty a young world stays below it, on hard a long-inhabited chunk can pass it. The live check counts
  recon scouts attacked at lairs (10.4). Equal treatment holds here: a zombie finds a player with the same follow
  range, so a player at 46 blocks is as safe as the scout.
* **Every hostile monster in the box counts.** The sample counts every hostile monster in sight near the lair, a wild one
  at night too (lair monsters wear a leather helmet when they spawned without one, but wild ones may wear one too, so a
  player could not tell them apart either). At night the count errs high, so the council waits rather than launches.

## 4. The report and its way home

**`Intel.Recon`** (pure record, replaces `Intel.Look`): `soldiers` (distinct), `power` (mean visible power of those
soldiers, 0 when none), `heroes` (id to war power, kept as today's `id=power@time` string), `coverage` (0 to 1, the
union), `middle` (boolean), `watchTicks`, `at` (the watch's last sample), `vantage` (text) and `by` (the scout's name).
`good()` = at least `RECON_MIN_POINTS` (8) of the 25 points and middle and watchTicks at least `RECON_MIN_WATCH`. A
report that is not good is **blind**.

**Carried.** The focused report is held apart from the scout's `pending` list (`ScoutJob.reconReport`, saved with the
job). It is exempt from the 24-report cap (`addPending` drops reports past 24). It is told only at the station, at
`report()`, first. A scout that dies carries it into death: it is lost, as everything it carried is (`missing_npcs.md`
5). A scout presumed dead that walks home later still delivers it. If it is stale by then, it is stored but never used to
launch.

**Lines.** When the scout takes the order: "Scout Edrin Cole sets out to watch the Stoneguard settlement". At the
station: "Scout Edrin Cole returned from watching the Stoneguard settlement: about 4 soldiers and Hinae Shirasuzu seen
(40% of the yard in view, watched 60 s)" or "...: could not see inside the walls".

**Merging (the worst bug must not come back as a new one).** A base's report in `FactionState.knowledge` gets
`recon` (the newest **good** focused look), `reconFailedAt` and `reconFailures` (the back-off) and `reconSidesTried`
(a 4-bit mask of the sides tried since the last good recon, 3.5).

| newer report | effect on the kept report |
|---|---|
| a passing look (position only), on the same trip or later | position and `observedAt` refreshed; **`recon` kept untouched** (never cleared, aged or replaced) |
| a good recon, newer than the kept one | replaces `recon`; `reconFailures` = 0; `reconSidesTried` cleared |
| a good recon, older than the kept one (a late scout) | ignored for `recon` |
| a blind recon | `recon` kept; `reconFailedAt` = its time; `reconFailures` + 1; its sides added to `reconSidesTried` |
| ruins (`fallen`) | ruins for good, as today; `recon` dropped |

`Report.mergeBase`, `ScoutJob.mergeOnTrip` and `FactionState.addReport` follow this table. `addReport` orders by
`observedAt` today; for a `recon` it compares `recon.at`.

## 5. The estimate

### 5.1 The rule (pure, `Intel.estimate(Recon r, long now)`)

(R1 adds it beside the old estimate, as `estimateRecon`; R5 deletes the old one and may take its name.)

* `null` (no estimate) when there is no recon, when it is blind, or when `now - r.at > RECON_FRESH`.
* Otherwise: `soldiers = r.soldiers` (the faces counted, no extrapolation; question 1), `power = max(1, r.power)` (1, a
  bare soldier, when none was seen), `heroPower = the sum of the heroes' war power seen in that watch`.
* No prior, no drift, no losses, no fading inside the freshness window, no hero carried over from an older report.

A good recon that counted 0 soldiers is a real "about 0": the scout saw the middle of the yard and a third of it for a
minute and nobody was there (the garrison marched out, or the faction fell). That is the "garrison seen gone" case. A
blind look is never 0. It is no estimate at all.

### 5.2 Ageing

Stale after `RECON_FRESH` (12000), which means a new recon. Nothing in between. Today's `Intel.FORGET_TICKS` (48000),
`weight` and the hero sightings' fading go.

### 5.3 Heroes and lairs

Heroes count only if seen in the watch, by their war power at full strength, under the soldier backbone
(`HeroRules.countedPower`) as today. A hero indoors for the whole minute is not counted, as a player would not know of
it. Lairs: monsters counted, power 1 (3.7).

### 5.4 The decision, the launch line and the war rate

The decision is unchanged in form: the target with the least counted defence, then the nearest, among usable reports;
two thirds at the interval (`Intel.twoThirds`), `Intel.partySize` for the party. **Inside the interval** (after
`interval / 4`), the 3 to 2 of `Intel.threeToTwo` applies only to a target whose faction struck us since our last raid
(`struckBy` after `lastRaidAt`; section 6). Today `counter = !intervalOver && threeToTwo(...)` fires on any estimate.
Ordinary recons now arrive a few thousand ticks before the interval ends (2.1), so without this condition the council
would launch early at 3 to 2 whenever a recon happened to arrive early. The counter-attack stays a reply to a strike.

Launch line: "Launching a raid with 6 soldiers (against about 4 defenders and 1 hero, as scout Edrin Cole saw on day 5)
on the Stoneguard settlement reported at x, y, z". "As seen on day D" takes the recon's `at`. The phrase "a garrison
none of ours has seen" can no longer occur.

**Expected war rate: a replay of the logged launches** (`knowledge.md` 12; script `replay.py`). The estimate is taken as
the truth a recon would count, heroes at 6.8 war power (a common hero at level 1) and soldiers at power 1.5. The party is
the one logged; in the first run, where parties were not logged, it is the smallest party the old estimate let go.

| launch (old basis, "about") | truth | defence (soldier units) | party | new rule | logged outcome |
|---|---|---|---|---|---|
| verif 3.94 Tide → lair (look, 7) | 8 monsters | 8.0 | 3 | wait (borderline: goes with gear 1.7 or better) | won |
| verif 4.10 Ember → Stoneguard (prior, 7) | 3 + Hinae | 7.5 | 4 | **wait** | 4 of 4 dead |
| verif 4.34 Stoneguard → Ember (losses, 1; counter) | 5 | 5.0 | 3 | no counter; goes at its interval | 3 of 3 dead |
| verif 5.44 Tide → Stoneguard (prior, 7) | 0 | 0 | 4 | **go** | won |
| verif 5.47 Grove → Ember (prior, 7) | 9 + Natsuho | 13.5 | 4 | **wait** | 4 of 4 dead |
| first run, 6 prior launches | 3; 5+h; 3+h; 9+h; 2+h; 4+h | 3.0 to 13.5 | 3 to 7 | 4 go, 2 wait | 5 lost every raider, 1 won |
| first run, 2 look launches | 4; 6 | 4.0; 6.0 | 3; 3 | 1 go, 1 wait | both lost every raider |
| first run, 3 loss launches (2 counters) | 3+h; 4; 4+h | 7.5; 4.0; 8.5 | 3; 3; 5 | no counter goes; 2 go at the interval | 2 lost every raider, 1 beaten back |

The 16 are every council launch on a settlement in the two logged campaigns, plus the verification's lair raid. Left
out: the first run's two lair raids (both won without a loss; their monster counts were not logged), its lost branch's
one launch, and the operator's forced raid.

**9 of the 16 logged council launches would still go** with the same party. Of the 7 that wait, 5 face a hero. None of
the three loss-based counter-attacks goes as a counter. Read against the logged outcomes (the first run's are logged by
basis, not by launch, so the counts are bounds): of the 9 that go, at most 2 won (verification 5.44, and at most one of
the four prior launches of the first run) and at least 7 lost every raider or were beaten back. Of the 7 that wait, one
had won (the verification's lair raid at 3.94, a borderline case) and at least 4 had lost every raider. So the new rule
cuts the number of parties sent to die, but not the share of launches that fail: that share is set by the hard defence
the owner keeps (walls, archers, the alarm, the heroes), not by the estimate. Parties grow while councils wait, and a
recon taken while a garrison is out raiding shows it empty (verification 5.44), so some waits turn into later launches.
There are new delays too: a recon about 0.2 to 0.3 day after the order, up to `RECON_WAIT` more while no scout is home,
and a day's back-off after lost scouts.

**Expect about 55 to 70% of today's launches** (3 to 4 in a 7-day campaign like the verification's 5, 6 to 8 where the
first run had 11), with fewer parties wiped out in number and no launch "against about 0" on a full garrison. This is a
replay with assumptions, not a measurement. The live check measures it (10.4). Nothing here tunes the defence: the
5-high walls, the two-thirds rule, the party rule and the hero weighting stay as the owner keeps them.

## 6. What replaces the 3-to-2 counter-attack's input

Losses seen at home are gone as an input. The same sighting is now a **trigger**:

* `BaseWatch.enemyFell` keeps its witness rule (an enemy soldier falling in our home area, or seen by a member standing
  in it). It no longer touches any report. It records `FactionState.struckBy[enemyFaction] = now`.
* The alarm also records it: an enemy faction's soldier noticed inside our walls box (`defenceAlarm`'s `remembered`).
* A later council pass places an **urgent** recon of that faction's reported settlement nearest to our base (2.1), at
  most once per `interval / 4` per enemy faction, once the alarm has been quiet for 600 ticks (the fight is over) and
  the counter-attack window is open; a strike seen while our own raid was out waits for the raid's end. If none of
  their settlements is reported, nothing happens: the faction does not know where they live. (`struckBy` is the
  faction's own observation, saved; it never lowers an estimate.)
* The counter-attack decision is today's (inside the interval, after `interval / 4`, at 3 to 2), on that fresh report,
  and only against a faction that struck us since our last raid (5.4).
  If the recon finds the garrison weakened by the beaten raid, the counter-attack goes. If it finds it whole, the council
  waits.

A raid beaten at our walls therefore leads to a recon about 0.2 to 0.3 day later, and a counter-attack only if the scout
saw the enemy weakened. Today the council attacked "about 0" while the enemy still kept its third at home.

## 7. Deleted, kept, save format

Every deletion below lands in one task, R5 (the council's switch), together with the tests it breaks and their
rewrites; R1 to R4 only add, so the build and both suites pass after each task (section 11).

**Deleted (code):**

* `Intel.Prior`, `Intel.lairPrior`, `Intel.estimate(Look, losses, lossAt, heroPower, Prior, now)`, `Intel.better`,
  `SAME_VIEW`, `FORGET_TICKS`, `weight`, `heroPower(Collection, now)` with its fading, `mergeHeroes` and `Intel.Look`
  (replaced by `Intel.Recon`). Kept: `Estimate`, `twoThirds`, `threeToTwo`, `partySize`, `TIE`, the hero list's parse and
  format.
* `Report.look`, `Report.losses`, `Report.lossAt`, `Report.seenAt()`; the look and loss logic in `Report.mergeBase`.
  Passing reports lose their heroes and counts.
* `BaseWatch.enemyFell`'s loss counting (it becomes the strike notice, section 6); passing looks' soldier and hero counts.
* `FactionDirector.estimate`, `likeOurs`, `seenText`; `watchTarget`'s looks (it keeps only the ruins: `seesFall` within
  `HEART_SIGHT` and the party's own heart break); `deliverRaidIntel` delivers only ruins; `forceRaid`'s prior (an
  operator's raid uses the faction's usable recon if it has one, else says "no scout's report").
* `ScoutJob.mergeOnTrip`'s look merge.
* `/fc admin dump`'s `look` and `losses` fields; `BaseInfo`'s "seen falling at our walls" text.

**Deleted (tests):** in `IntelTest`, the prior, the partial view, ageing and drift, never below what was seen, losses
and their fading, heroes' fading, `better` and the lair prior; in `KnowledgeGameTests`, the garrison that leaves unseen
is believed at home (rewritten, 10.2), losses lowering the estimate and the counter-attack on them, losses fading, heroes
fading and the lair's prior; `FCGameTests` line 404 (`groveSaw.look`) and `MissingNpcGameTests` line 508 (`seen.look`)
are rewritten with a recon; the hero raid draft's "report with a look" becomes "a report with a recon".

**Kept:** ruins facts (`Report.fallen`, `BaseWatch.ruins`, `seesFall`, the raid party's ruins, "ruins stay ruins"), base
positions from any look, `BaseWatch.SIGHT` (64), the raid's plan (`raidPlanTicks`, `raidTurnHomeAt`), the operators'
truth (`LAST_COUNCIL`'s `[truth: …]`, `FactionDirector.defenders`, `defencePower`, `heroDefence`, `heroWarPower`) and
`KnowledgeIsolationTest` (which now also checks that the council reads only `Report.recon`).

**Save format** (`CampaignData.FORMAT` 5, logged only):

* `Report`: new tag `recon` {soldiers, power, heroes, coverage, middle, watch, at, vantage, by}, `reconFailedAt`,
  `reconFailures`, `reconSidesTried`. Old `look`, `losses`, `lossAt` and `heroes` tags are **ignored on load** (from R5;
  until then the old council still reads them): an old report is a position only, and its base is judged again by a
  recon.
* `FactionState`: new `reconOrders` (at most 2: target position, subject, kind, home base, orderedAt, urgent, scout UUID,
  takenAt) and `struckBy` (faction to time). An old `raidIntel` is kept only if it is ruins.
* `ScoutJob`: the recon state, its target, the side tried, the pillar (`Scaffold.Pillar.save`), the watch accumulator and
  `reconReport`. An old job loads as today's.

## 8. Interactions

* **Missing scouts and the roll call.** The recon trip has its own plan (2.5). The council learns that a recon failed
  only by its scout going MISSING (its order lapses, and the target backs off). A death a recon scout saw is carried home
  as news, as on any trip.
* **The raid plan** (`raidPlanTicks`, `raidPlannedBy`, `raidTurnHomeAt`): unchanged. While its raid is out a faction
  places no recon orders (the counter-attack cannot launch then anyway).
* **Heroes on a raid.** `HeroRaid.choose` computes `homeHeroKnown` from a home hero within 64 blocks (an open item,
  `knowledge.md` 10). It should read "the recon this raid was launched on saw a hero" (R9, small).
* **The Hollow.** It **keeps** its 300-block sense. It estimates no defence: it raids the nearest settlement whenever a
  lair has 4 monsters and its interval passed, and the owner's rule is about estimating a defence. Its monsters never
  leave the lair to look, so without the sense lairs would never raid (`knowledge.md` 9). Question 3.
* **Elimination kills every member.** A recon scout of a faction that is eliminated dies where it is, with its report.
  Orders of a dead faction are dropped. A recon of a base whose faction fell watches an empty yard: a good report of
  "about 0". The party sent there finds the ruins at 48 blocks ("found in ruins"), a player-like mistake. The heart
  stands inside the station hall, so no look from outside sees it.
* **Pacts** are gone (the other workflow): no joint offensive enters the decision, and no sworn enemy narrows the
  target list.
* **A scout-duty hero's mobility** (a flyer hovering, a climber on a cliff) as a vantage is left for later. It would
  need a stamina rule for hovering a minute. The wall top is excluded (section 1). A scout-duty hero runs `ScoutJob`
  (`Jobs` maps the scout role to it; `HeroLoadout.dutyRoleId` gives the hero that role), so it does a recon exactly as
  a scout does, pillar included.
* **Equal treatment.** The vantage is safe from the yard's guards because none within its 48-block reach can see it
  (3.5), a rule that protects a player standing there just as well. One difference is left, older than this design:
  vanilla's target search never picks an NPC 6 or more blocks above a soldier, while it picks a player at any height,
  so an enemy soldier passing below the pillar ignores the scout where it would attack a player. Making the search the
  same for NPCs and players is a combat item for later, outside this design (it would change every NPC on a pillar,
  woodcutters too); if it is done, passing soldiers will attack the scout and the hit rule brings it down (3.5), and the
  yard's guards still cannot see it. A member player of a faction files no recon (players file no base reports today); a
  player-filed recon at the station is a later item.
* **Night, rain and monsters.** Rays do not depend on light: the scout sees the yard at night as by day, and a player
  sees a lit yard too (lanterns and torches). Faction NPCs do not sleep, so guards keep their posts at night and the
  count holds. The scout walks at night as scouts do today (it is not remote work under `remoteWorkInDaylightOnly`;
  the woodcutter's nightfall rule does not apply). Rain changes nothing. Monsters: on the ground, the usual step-aside
  rules; on the pillar, a hit ends the watch (3.5). A monster's own target search (vanilla's, the same quirk) does not
  pick an NPC 6 or more blocks up, and a spider that climbs the pillar and hits it ends the watch like any hit. At a
  lair, the night's wild monsters raise the count (3.7).
* **Performance.** One sample is 25 rays plus a line of sight per soldier in the box, every 40 ticks, for each scout on a
  watch: the same as a passing look today. The climb adds one sample per block from 4 up (at most 17 a side), and the
  column search reads at most 17 x 12 heightmap columns per side once. With at most two open orders per faction, that
  is at most a few hundred rays a second across a campaign of four factions.

## 9. PROPOSED constants (BALANCE.md, "War") and docs to rewrite

| Name | Value | Why |
|---|---|---|
| `Recon.STANDOFF` (settlement) | **61** (Chebyshev, from the centre block to the column) | from 60 out no yard place within the guards' `FOLLOW_RANGE` (48) sees the scout at any height up to 22, any side, both layouts (3.5); one block of margin. Also outside the alarm box (28) and the hero approach box (52) |
| `Recon.SIGHT` | **72** (horizontal, from the centre) | the focused look's reach; gives the column search a band 12 blocks deep |
| `Recon.LAIR_STANDOFF` | **46** | the leash (10) + a zombie's follow range (35) + 1 (3.7: a zombie may get more at a high regional difficulty) |
| `BaseWatch.SIGHT` | 64 (unchanged) | the passing look's reach |
| `Recon.PILLAR_MAX` | **20** | table 3.3: 12 to 14 on a gate side's axis, 17 to 21 on the other sides of a grown base |
| `Recon.CLIMB_PAST` | **2** | blocks above the first good look: the worst vantage counts 95% rather than 89% (3.4) |
| `Recon.BLOCKS` | **22** dirt | 20 + `TreeShape.PILLAR_SPARE` (2) |
| `Recon.SIDES` | **2** a trip | a blind side, then another |
| `Recon.MIN_POINTS` | **8** of the 25 points (32%), and the middle in view | 3.4: 99.4% counted, worst vantage 94.6% where the scout stops |
| `Recon.WATCH_TICKS` | **1200** | 3.4: one minute counts 99.4%; 600 counts 95.0% |
| `Recon.MIN_WATCH_TICKS` | **600** | a watch cut short at 600 still counts 95% on average (worst vantage 80%) |
| `Recon.FRESH_TICKS` | **12000** | half a day; equal to the lead, so an answered order is fresh at the interval |
| `Recon.LEAD_TICKS` | **12000** | the trip takes about 4500 to 6500; the scout may be away up to 6000 more |
| `Recon.RETRY_TICKS` | **12000**, doubling to **48000** | a blind or lost recon; stops a scout a day being fed to one target |
| `Recon.WAIT_TICKS` | **6000** | an order untaken this long asks for a second scout |
| `Recon.MAX_SCOUTS` | **2** a base | capacity for watching and exploring |
| `Recon.VANTAGE_TICKS` | **4800** | the plan's allowance: two sides (a climb and descent of up to 20 each, about 660 ticks), the walk round (about 200 blocks, 2000 ticks at the plan's 10 ticks a block), a hold (1200) |
| `Recon.THREAT_RANGE` | **16** | an enemy in sight this close: the scout on its pillar holds still and does not start down (3.5) |
| `Recon.HOLD_MAX` | **1200** | the longest it waits on the pillar after its watch for an enemy to leave; inside `VANTAGE_TICKS` |
| `Recon.QUIET_TICKS` | **600** (`NOTICE_MEMORY`) | an urgent order waits until the alarm has been quiet this long (the fight is over) |
| `Recon.URGENT_GAP` | `interval / 4` (9000) | one urgent recon per enemy faction per counter-attack window |
| lair grid `reach` | **10** (the leash) | the monsters' ground, not the roofed hall |
| removed | `Intel.FORGET_TICKS` 48000, `SAME_VIEW` 0.1, the lair prior | no prior, no drift |

**Docs the implementation rewrites:** `knowledge.md` section 1 (the whole estimate model becomes a pointer here),
section 8 (tests), section 9 (exceptions: "what a look counts", the pact bullet), section 10 (heroes on a raid), and
section 11's items 3 and 4 plus its open items on the 24-report cap, raiders' kills, the loss count and "never at 6".
Section 12 stays as history with a note. Also `missing_npcs.md` 3 (the plan table: the recon trip) and 6 (the last
bullet), README (war), BALANCE.md (War), HANDOFF (architecture row for `BaseWatch`, `Intel`; how to watch recons),
ROADMAP status, and `docs/factionclash-common.toml` if a config key is added (none is proposed). Two more, found by the
review: `docs/heroes-v2/MOBILITY_AND_WORK.md` 5.2, "Scouting ahead" (a flyer on a raid feeding the faction's report of
the target: a raid party's look, which no longer feeds an estimate; a scouting hero's look counts only as a focused
recon, on the scout duty and sent to watch), and the `SPYMASTER` text in `logic/leader/Talents` ("base reports carry
garrison counts"), which must become an improvement of the focused recon when that talent is built.

## 10. Tests

### 10.1 Unit (pure: `logic/campaign/ReconTest`, `IntelTest`, `WhereaboutsTest`)

1. `Recon.good`: 8 of 25 with the middle passes; 7 of 25 fails; 20 of 25 without the middle fails; a watch of 599 fails.
2. `Intel.estimate`: none for no recon, a blind one (0% coverage: **never** "about 0") and a stale one (12001); otherwise
   the faces counted with no extrapolation, power 1 when none were seen, and only the heroes of that watch.
3. Merging (table in section 4): a passing look never clears, ages or replaces a recon; a blind recon keeps the good
   one and raises the back-off; a newer good one replaces it; an older one does not; ruins drop it.
4. Orders: the window (the lead, the first war's gate); no order while a raid is out or with party 0; priority (urgent,
   never judged nearest, stale lowest); one per target, two per faction; back-off doubling and its cap; the urgent gap;
   the second-scout need after 6000; every ending of 2.1's table (an empty-handed scout, a dead or retrained one, an
   order left untaken).
5. The counter-attack: inside the interval, 3 to 2 goes only against a faction in `struckBy` since our last raid. A
   fresh ordinary recon at 3 to 2 inside the interval waits.
6. The watch accumulator: distinct ids counted once over 30 samples, the point mask's union, the middle seen once is
   enough, a hero's power kept.
7. The vantage ring: candidates at Chebyshev 61 or more and Euclidean 72 or less, the axis first, then the nearest the
   standoff; side order (the gate side facing home, then the other, then the side facing home, then the last; a further
   attempt starts at the first side not yet tried, so two trips cover all four); a lair's gap axes at 46 to 64;
   climbing two past the first good height, never past 20.
   The hold: an enemy within 16 keeps the scout up; a hit or a broken pillar block brings it down; the hold after the
   watch never passes `RECON_HOLD_MAX`, and the plan (item 8) covers it.
8. `Whereabouts.reconPlan` and the follow-up need on MISSING.
9. Kept as they are: `twoThirds`, `threeToTwo`, `partySize`.

### 10.2 GameTests

Each test has its own `batch`, uses `skyAccess = true` where a heightmap is read, and removes every pillar and wall it
built in its cleanup. Three facts of the GameTest world shape them (checked by the review):

* **Height and sky.** A test's area is boxed in barriers (`StructureUtils.encaseStructure`): on its four sides always,
  and over its top, at the template's height, unless the test has `skyAccess = true`. Without it a pillar on the 8-high
  `platform` stops at the barrier ceiling and the column search finds no open sky. So every test that climbs or reads a
  heightmap has `skyAccess = true`. The 32 x 24 x 32 `mobility_field` (`HeroMobilityGameTests.FIELD`, where
  `GameTestBases.register` already works) gives room for two bases and a vantage; the 24 x 24 `platform` is tight for
  that.
* **Two bases.** `GameTestBases.register` centres the home base at relative (12, 2, 12) and gives it no station; the
  test places a `REPORTING_STATION` and its point as `aSurveyIsTheFactionsOnlyWhenTheExplorerIsHome` does, and puts the
  target at another centre with the `enemyBase` helper of `KnowledgeGameTests` (its own `testHalf`). The real standoff
  (61) does not fit: the target carries a transient `testStandoff` (like `testHalf` and `testRadius`). Measured with the
  review's model: a 4-high ring of half 4 seen from 10 blocks off its centre (6 outside the ring) gives a good look
  (15 of 25 points and the middle) at N = 8; from 12 blocks, at N = 9.
* **No reliance on the guards' targeting.** The defenders are soldiers with no AI (`setNoAi`), placed by the test, and
  the one that must walk in and out of view is moved by the test each tick. Nothing in a test depends on whether a guard
  would pick the scout as a target (3.5).

The climb, look, watch, descent and report run for real.

1. **reconOfAWalledBase**: a target with a 4-high ring (half 4) and 3 soldiers inside; a scout with the order and
   22 dirt, vantage column 6 blocks outside the ring. It climbs, stops two above the first good height, watches (a
   test-shortened watch), takes the pillar down (no dirt left in the world, the blocks back in its backpack), walks to
   the station, and the faction's report has a good recon counting 3.
2. **distinctFacesCountedOnce**: one soldier walking in and out of view across the watch counts 1, not one per sample;
   a soldier shut in a stone box the whole watch is not counted.
3. **aBlindReconIsNotUsed**: a ring too high for the pillar cap; the report says "could not see inside", and the council
   does not launch and backs off.
4. **noLaunchWithoutAFocusedReport**: a passing look that saw the yard empty; the council does not launch and places an
   order.
5. **lossesAtOurWallsDoNotMoveTheEstimate**: enemy soldiers killed at our walls in sight of home; the fresh recon's
   count is unchanged and an urgent order goes to their nearest base.
6. **theRaidPartysLookIsNotAnEstimate**: a party at the target sees the yard; no recon results. The ruins it makes still
   come home (`aPartyThatBreaksTheHeartBringsTheRuinsHome` kept).
7. **aStaleReportOrdersANewRecon**: a good recon aged past 12000; no launch; an order is placed.
8. **aDeadScoutsReportIsLost**: the recon scout killed on the way home; nothing is delivered. When it goes MISSING the
   order lapses as lost and the target backs off.
9. **theOrderIsTakenAtTheStation**: an order placed while the only scout is away is taken only when it reports.
10. **oldReportsLoadAsPositions**: a saved report with a `look` and `losses` loads with no recon.
11. **aPatrolDoesNotBringItDown** and **aWatchCutShort**: an enemy soldier walks past the pillar's foot within 16
    blocks; the scout stays up, keeps watching and comes down only after it has gone. Then a hit (the test damages the
    scout) mid-watch: it comes down and goes home, and its report is good only past 600 ticks.
12. **lairRecon**: a lair test base; the scout watches from the ground on a gap axis and counts its monsters.
13. Kept: `aScoutCountsOnlySoldiersInSight` (now on a recon sample), `ruinsSeenStayRuins…`, the raid's plan tests, and
    `tenSoldiersAttackAGarrisonLikeTheirOwn…` rewritten on a recon of the same garrison.

### 10.3 Sabotages (each must fail a test)

An estimate from a passing look (4); the coverage gate removed (3); the middle requirement removed (unit 1 and a
GameTest variant where only a corner is in view); counting per sample instead of per face (2); losses still subtracted
(5); the party's look written as a recon (6); freshness ignored (7); a 3-to-2 launch inside the interval with no strike
(unit 5); an order never ended when its scout comes home empty-handed (unit 4); a report written at the watch's end
instead of at the station (8, 9); a passing look clearing a recon on merge (unit 3); the pillar left standing (1); the
standoff back at 53 (unit 7); the scout coming down for a passing soldier (11); a hold with no bound (unit 7).

### 10.4 Live acceptance (campaign check; `rolltrack.py` and `warcheck.py`)

`rolltrack.py` logs recon orders (placed, taken, lapsed), each watch's samples summary and each recon delivered, with the
truth: the soldiers in the target's walls and its heroes at the watch's last sample. `warcheck.py` adds:

* **every launch rests on a usable focused report**: basis "recon N% day D"; any launch without one is a failure;
* **the recon against the truth** at the watch's end: the mean and worst miss in soldiers, for settlements and lairs
  apart. Target: mean miss at most 1 soldier; no good recon above the truth;
* **coverage per focused look** (a histogram) and the share that were good. Target: at least 70% good, none blind
  because it was too close to see over the wall (pillar height and side reported);
* **orders**: placed, taken, delivered, lost and blind; the wait from order to taking; scouts lost on recons;
* **the longest gap without a launch**, and why: no usable report (waiting, back-off, blind) or "wait" on strength;
* **interrupted watches** by cause, and pillar blocks left behind (0 except a scout killed on its pillar);
* **recon scouts attacked** at the vantage, by whom (a guard from the yard must be 0: the standoff's promise; a passing
  party, a hero, a monster, a player), the holds on the pillar and their length, and the targets that never got a good
  recon on any side (trees, hills);
* **heroes**: every hero at the target's base at the watch's end against the heroes the recon saw (a hero indoors or
  away on its duty is missed, 5.3);
* regressions as before: recruitment, stuck recoveries, tick time.

## 11. Implementation tasks (in order; one agent each)

Each task builds, passes both suites and carries its own tests. R1 to R4b only **add**: the old council keeps judging
by the old estimate until R5 switches it, so no build in between has a council with nothing to judge by, and no build
has a broken compile. Every deletion of section 7 lands in R5 with the tests it breaks.

| Task | Size | Content | Tests in the task |
|---|---|---|---|
| **R1** | S | Pure rules, additive: `Intel.Recon` (record, `good()`), a new `Intel.estimateRecon` beside the old estimate, the merge rules of section 4 as a pure function, `logic/campaign/ReconRules` (window, priority, back-off, urgent gap and quiet, second-scout need, side order, the vantage band 61 to 72, climb past, the hold's bound), `Whereabouts.reconPlan`. Deletes nothing | unit 10.1 items 1, 2, 4, 5, 7, 8 |
| **R2** | M | Records, additive: `BaseWatch` recon sample beside the passing look (point mask, middle, ids and powers, `RECON_SIGHT` 72, the lair grid's reach 10); `Report.recon`, `reconFailedAt`, `reconFailures` with the merge and their save (`CampaignData.FORMAT` 5; the old `look` and `losses` tags are still read, for the old council, until R5); `FactionState.reconOrders` and `struckBy`, saved; `enemyFell` and the alarm also record `struckBy` (they still count losses until R5) | unit 10.1 items 3, 6; GameTests: a recon sample variant of `aScoutCountsOnlySoldiersInSight`, a save and reload of a recon and of the orders |
| **R3** | M | Orders: `ReconOrders.place` and `settle` in `raids()` (census moved up, the window, the urgent order after the quiet, every ending of 2.1), the `LAST_COUNCIL` order line. The decision itself is unchanged. (No scout takes an order before R4b, so the needs that react to untaken orders come with R4b) | GameTests with a scout's record driven by the test: an order placed in the window and not outside it, one per target and two per faction, each ending |
| **R4a** | M | The shared pillar: lift the climb and descent driver out of `WoodcutterJob` (jump and place, hold still, break underfoot, misses, save and resume) into `work/PillarClimb` beside `Scaffold`, with a "look at each block" hook. Woodcutters use it unchanged. The vantage column search (`work/ReconVantage`: side order, band, axis first, ground it can walk to, plan and alarm boxes) | every woodcutter tall-tree GameTest unchanged; a column-search GameTest in `mobility_field` (a hill, a tree, a base plan in the band) |
| **R4b** | M | `ScoutJob` recon trip on R4a, and the recruitment needs it brings (a second scout after `RECON_WAIT`, the follow-up on a MISSING scout instead of `departedAt + SCOUT_OVERDUE_TICKS`): take the order at home, keep or dig 22 dirt, travel, climb and look, climb two past, watch (the accumulator), the hold and the hits, down, the next side, `reconReport` held apart and delivered first at the station, `tripPlanTicks` recon branch, save and load; lairs on the ground on a gap axis | GameTests 10.2 items 1, 2, 3 (the blind report delivered), 8, 9, 11, 12 in `mobility_field`; the second-scout need |
| **R5** | M | The switch and every deletion: `raids()` judges only usable recons (waiting line, launch line, the counter-attack's 3 to 2 only after a strike, on the urgent recon); `watchTarget` and `deliverRaidIntel` ruins only; `forceRaid`'s line; delete the prior, the drift, the losses, `better`, `Intel.Look`, the passing look's counts and heroes, `enemyFell`'s loss counting, the old save tags (ignored on load), the dump's and `BaseInfo`'s loss text; rewrite the old tests (`IntelTest`, `KnowledgeGameTests`, `FCGameTests`, `MissingNpcGameTests`, the hero raid draft); `KnowledgeIsolationTest` (the council reads only `Report.recon`) | GameTests 10.2 items 3 (the council side), 4, 5, 6, 7, 10, 13; every sabotage of 10.3 |
| **R6** | S | Operators and tools: `/fc admin dump` (recon, orders, `struckBy`), `BaseInfo` station lines, `sim.py`, `rolltrack.py` (orders, watches, holds, truth at the watch's end), `warcheck.py` (basis "recon", the recon against the truth, gaps, scouts attacked) | the tools run on a short campaign's logs |
| **R7** | S | Docs: `knowledge.md` (section 9's list), `missing_npcs.md`, `MOBILITY_AND_WORK.md` 5.2, README, BALANCE.md, HANDOFF, ROADMAP; the jar and README's SHA once R8 passes | none |
| **R8** | L | Campaign verification (10.4) on a fresh world, the same seed and setup as the last two; report the replay's prediction (section 5.4) against what happened | the live checks of 10.4 |
| **R9** | S | `HeroRaid.homeHeroKnown` from the launching recon's heroes (instead of a hero within 64 blocks) | a hero raid GameTest with and without a hero in the recon |

Order: R1, R2, R3, then R4a (it can start beside R2 and R3), R4b, R5. R6 can start after R2 for the logging and must
finish after R5; R7 after R5; R8 after R6 and R7; R9 any time after R5.

## 12. Questions for the owner

1. **How the scout turns what it saw into a number.** Recommended: **count the soldiers it saw** (each once, by face)
   over a one-minute watch, with no guess for the yard it could not see. In the model (checked twice, independently)
   this finds 99% of a guard garrison on average, and at least 95% from the worst vantage.
   The other way, spreading what it saw over the unseen yard, counted 1.4 to 2.4 times the garrison in the same model.
   Either way the numbers are lower and truer than today's guess "like our own garrison". Raids go more often against
   small garrisons and less often against bases with a hero (section 5.4: about 9 of 16 past launches would still go).
2. **A second scout for watching.** Recommended: **yes**. When an order to watch a base waits a quarter day because the
   only scout is out exploring, the base hires a second scout (at most two). Without it, the first raid of each interval
   can slip by up to half a day.
3. **The Hollow's sense.** Recommended: **keep** its 300-block sense of settlements. It never estimates a defence (it
   raids the nearest settlement when it has 4 monsters), and its monsters never leave to look, so without the sense lairs
   would never raid.

## 13. Critique (2026-11-25)

An adversarial review of this design, the same day. It rebuilt the ray model and the guard model independently (from
`BaseWatch.look`, `BaseLayout`, `ProjectLayouts.house`, `LairLayout`, `GuardJob` and the 21.1.209 sources), read the
code each claim rests on, and changed the design where it was wrong. The scripts stay in the review's scratchpad; every
number they gave is in the sections above.

**Checked and kept.** The owner's rule is followed: only a focused report estimates a defence; no prior (none for
settlements, none for lairs, no default garrison), no losses, no raid party's or passing look, no hero carried over; a
blind look is no estimate and never "about 0". (The order priority reads a stale recon's count only to choose which
base to watch first, never to launch.) The ground table (0% from outside the walls, the live 23 of 26 reproduced), the
pillar tables, the lair tables and the face count were reproduced: 99.4% of a guard garrison on average at the scout's
stop height, 94.6% from the worst vantage, and a garrison of 3 never counted as 0. Vanilla's 4-block vertical target
search for non-players, the zombie's 35-block follow range and the eye height 1.62 were read in the sources.

**Changed:**

1. **The standoff, 53 to 61, for equal treatment (3.5).** At 53, 11 to 21 yard places within the guards' 48-block
   reach saw the scout at its stop height (the staging chest's post among them). It was safe only because vanilla never
   picks an NPC target 6 or more blocks above a guard, while a player on the same pillar would be attacked. From 60 out
   no yard place within 48 sees it, at any height up to 22, on any side and both layouts (every column 8 to either side,
   a 1-block grid, checked at 60, 61, 64, 68 and 72); 61 keeps a block of margin. The focused look now reaches 72
   (`RECON_SIGHT`) so the band is 12 blocks deep; the passing look keeps 64. One difference from a player remains and is
   named (3.5, 8): an enemy soldier passing below the pillar ignores an NPC scout by the same quirk; the hold rule is
   written so that an equalised search would simply bring the scout down by its hit rule.
2. **The pillar cap, 16 to 20, and the dirt, 18 to 22 (3.3, 3.5).** At 61 to 72 a gate side needs 12 to 14 on its axis
   (up to 18 off the axis of a grown base) and the other sides 17 to 21. Table 3.3 now shows the band used; its old first
   column (52) lay inside the old standoff.
3. **The threat rule was backwards (3.5).** The vantage sits on the gate axis, the enemy's road; "a soldier within 16:
   come down at once" sent the scout down into every passing party. It now holds on top and keeps watching, comes down
   only when hit or when its pillar is broken, and waits at most `RECON_HOLD_MAX` (1200) inside its plan.
4. **The trip's plan (2.5).** `RECON_VANTAGE_TICKS` 2400 to 4800: the walk round between the gate sides alone is about
   200 blocks, 2000 ticks at the plan's pace, the two climbs and descents about 1300 and the hold up to 1200; with 2400
   a live scout on its second side could be reported missing.
5. **The side rotation (3.5).** "Each attempt starts one side later" with two sides a trip gave sides 1-2, then 2-3: a
   blind side was tried again and the fourth side only on a third trip. The next attempt now starts at the first side
   not yet tried (a 4-bit mask on the report).
6. **Facts corrected.** The 36% north-west ground look was taken from inside the wall ring (3.2); "30%" is 8 of 25 points,
   32%; guards' stays last about 160 to 200 ticks, not up to 360 (the bound is drawn every tick; 3.4); `WallEntry` puts no
   hero on a wall top, it only takes a raider over the wall (1); the scout takes an order at the station or the
   storehouse, its `HOME` state (2.1); the order window is `nextRaidAt - RECON_LEAD` (2.1); a lair's roof has holes and a
   zombie's follow range can grow at a high regional difficulty, and wild monsters count at night (3.7).
7. **The urgent recon (2.1, 6).** It waits until the alarm has been quiet 600 ticks (the scout must not walk out through
   the attackers), and a strike seen while our own raid was out is acted on after the raid's end.
8. **The replay's claim (0, 5.4).** "Fewer parties wiped out" was true only in number: of the 9 launches that would still
   go, at least 7 failed in the log. The share of launches that fail is set by the hard defence, as the owner chose.
9. **Missing interactions (8).** Equal treatment, night, rain and monsters, a scout-duty hero (it runs `ScoutJob`), the
   performance of the climb and column search; and two more docs to change: `MOBILITY_AND_WORK.md` 5.2's "scouting
   ahead" (a raid's look) and `SPYMASTER`'s text (9).
10. **GameTests (10.2).** A test's area gets a barrier ceiling at its template's height unless it has `skyAccess = true`
   (read in `StructureUtils.encaseStructure`), so every climbing test needs it; the 32 x 32 `mobility_field` gives room
   for two bases and a vantage. The home base gets a station placed by the test and the target its own centre (the
   `enemyBase` helper); defenders have no AI, so no test depends on guard targeting. Two tests were added to 10.2 item
   11 and three sabotages to 10.3.
11. **The task order (11).** As written, R1 deleted the prior and losses that `FactionDirector`, `Report` and `BaseWatch`
    still used (no compile until R3), R3 switched the council before any scout could make a recon (a build with no
    wars), and the GameTests waited for R5. Now R1 to R4b only add, each task carries its own tests, the scouts make
    recons (R4b) before the council needs them, and every deletion lands in R5 with the tests it breaks.

**Left for the live check (10.4):** trees and hills at real vantages, the count at lairs, heroes missed because they were
indoors or away, recon scouts attacked at the vantage (a guard from the yard must be 0), and the war rate against the
replay's 55 to 70%.
