# Focused reconnaissance: a council judges a defence only from a scout sent to watch it

Design, 2026-11-25, reviewed the same day (section 13 lists what the review changed), updated the same day with the
owner's answers to its questions (section 14 lists what they changed), and that update reviewed (section 15). Not built
yet; tasks R1 to R12 (section 11) build it. It replaces the estimate model of `docs/design/knowledge.md` section 1 (the
prior, the losses seen at home, every passing look).

## 0. The owner's decisions, and the design in brief

The owner's words (2026-11-25):

* "Its fine to keep a hard defence, i believe as more tools and hero presence are introduced things will change"
* "Only estimate enemy defence when the scout or other related role makes a report of what he saw while focused on this
  particilar task"
* "Lets remove the pacts thing, if a faction gets eliminated, all of their members dies" (built by another workflow;
  nothing here uses pacts).
* The answers to the first version's three questions (section 14): "they can assume that what they saw is not the enemy
  mainforce, also when the tech phase arrives, there will also be items that should facilitate like drones or
  binoculars"; a second scout for watching: "ok"; the Hollow's sense: "monster lairs can automatically gather energy
  and spend energy. Its energy can be spent on extra buildings nearby, more monsters or increasing detection range"
  (designed in `docs/design/lair_energy.md`).

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
4. The scout **counts faces**: each soldier is counted once, by its UUID, over a 1200-tick watch. This models a player
   who watches a yard for a minute and keeps track of each soldier by where it walks and what it wears (not by name
   tags: vanilla shows a mob's name tag only under the crosshair, a few blocks away, and never beyond 64 blocks).
   Guards change posts about every 8 to 10 s. In the model, from the height where the scout actually stops (two blocks
   above its first good look), this finds 99% of a garrison on average and at least 95% from the worst vantage (section
   3.4). The estimate is that count with no extrapolation, and the council treats it as a **floor**, never the whole
   army (item 8).
5. A report is **fresh for half a day**. The council orders a recon half a day before its raid interval ends. A council
   with no fresh, good report on a target does not launch on it.
6. The **counter-attack** keeps its 3 to 2 rule but now rests on an urgent recon, against the soldiers counted (and,
   with R11, the strikers our people saw walk away from our walls). Inside the raid interval the council picks its target
   only among the factions that struck us, so a weaker bystander cannot block it. Enemy soldiers seen at our walls no
   longer lower any estimate: they make the council send a scout to their base once the fight is over (section 6).
7. Replaying the 16 logged council launches with the reserve of item 8 (section 5.4): 6 would still go with the same
   party (9 without the reserve). The 3 it stops were parties of 3 sent against 4 or 5 soldiers, and every raider who
   set out died; both logged wins still go. Of the 6 that go, 4 failed, 3 of them against Hinae. Expect about 35 to 50%
   of today's launches. Fewer parties are sent against more soldiers than they number, but the hard defence the owner
   keeps, not the estimate, still decides most launches.
8. **The count is a floor** (the owner's answer, section 5.5). The council plans against the count plus a **reserve**:
   half again the soldiers counted. At half again, two thirds of the plan is the count itself: a council attacks with
   at least as many soldiers as its scout counted. A base seen empty gets no share, so the raid on an emptied base stays
   possible. Lairs get no share for now. **Added beyond the answer** (the owner welcomes additions when told; question 1
   asks to keep it): the reserve becomes the soldiers of that faction its own people saw away from that base in the
   last half day and did not see fall, when they are more than the share, and the enemy heroes its people saw in that
   time that the watch missed join the plan. These **sightings** are built last (R11), changed none of the 16 logged
   launches, and never make an estimate: without a fresh good recon nobody launches.
   The launch line says what the plan rests on: "against about 4 defenders and Hinae Shirasuzu (scout Edrin Cole, day
   5.2) and a reserve of 2 (half again the 4 counted)".
9. **Optics** (section 5.6). The vanilla spyglass is never behind a tech level. A scout carrying one watches from up to
   96 blocks instead of 72: a deeper band with more clear columns and hills, not a shorter pillar. It is built after the
   live check if trees and hills often blind the band (R12). Phase 6's drones (over the middle of the yard: 80 to 88% of
   it) and scouting vehicles slot in as rows of data; what binoculars give beyond a spyglass is Phase 6's to set, since
   under these rules an optic at the eye changes only the reach.
10. The owner agreed to a **second scout** for watching (section 2.4). How the Hollow finds settlements now belongs to
    the lair-energy design (section 8); the Hollow still estimates no defence.

## 1. Who does a recon

| Who | Recon? | Why |
|---|---|---|
| **Scout** (`NpcRole.SCOUT`, `ScoutJob`) | **yes** | the role whose job is to go and look; it already walks far, steps round monsters and reports at the station |
| **A hero on the scout duty** (`behaviour.duty: scout`; `HeroLoadout.dutyRoleId` makes it a scout, and it runs `ScoutJob`) | **yes**, exactly as a scout | a hero on a worker duty does that job as that worker does (A4). Its flight or climbing as a vantage is left for later (section 8) |
| The talents `SPYMASTER`, `CARTOGRAPHER` (a leader's, `logic/leader/Talents`; a scout-duty hero's mastery, `logic/hero/WorkMastery`; both pure rules that the game does not read yet) | not now | the hooks for later: SPYMASTER would shorten the watch or name the heroes seen. Its leader text today says "base reports carry garrison counts"; when it is built it may only improve a focused recon, never give a passing report a count (section 9) |
| Soldiers on a raid, raiding heroes (`FactionState.raiders`, `HeroRaid`) | **no** | they are attacking, not watching: their looks never feed an estimate. They still bring home the ruins they saw or made (section 7). They file no sightings for the reserve yet (5.5) |
| Scouts on sector trips (exploring) | **no** | a passing look tells **where** a base is and that it stands (walls seen). It carries no count. The enemy soldiers and heroes it meets away from their base come home as **sightings** for the council's reserve (5.5), never as a count of a base |
| Cave explorers, traders, fishermen, miners, woodcutters | **no** | not their task; their trips never go to watch a base |
| Guards at home, and any member at home | **no** | they see enemy soldiers at our walls; that **triggers** an urgent recon (section 6), and each enemy soldier or hero seen is a **sighting** for the reserve (5.5); it is not a report on the enemy's home |
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
         inside the interval with no faction that struck us since our last raid: return (no target, no line)
         choose a target among reports with a USABLE focused recon (2.3), the least plan first (5.4); inside the
           interval only among the settlements of factions that struck us since our last raid (5.4, 6);
           none: LAST_COUNCIL "waiting for a scout's report"
         go / wait on the plan, 5.5 (two thirds at the interval; 3 to 2 inside it, only against a faction that struck us, 6)
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
fault. A target with a usable report whose plan its party cannot meet waits with the plan's line (5.4).

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
| The only scout is away exploring | an order not taken within `RECON_WAIT` (6000) adds the need "a scout to watch the X settlement", while the base has fewer than `RECON_MAX_SCOUTS` (2) scouts. Decided: the owner agreed on 2026-11-25 ("ok", section 14) |
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
`homeDefenders` (3) at home, so a good recon's "about 0" means the garrison is gone from the yard; where it went, and
whether it may come back, is the reserve's question (5.5).

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
   * **As built (task R4a, `work/ReconVantage`).** The search returns every column of a side it can stand on, ranked by
     `ReconRules.GROUND_ORDER`: the axis first, then each line one block farther off it (the left before the right);
     along a line the highest standing ground first, then the nearest the standoff. So higher ground wins along a line
     but never pulls the scout off the axis (a column off the axis of a grown base needs up to 4 blocks more of pillar,
     table 3.3). "Can walk to" is read from the land: the band and 4 blocks round it, walked from that edge by steps of
     at most one block up and drops of at most three, under leaves too; a column's top must be solid and not leaves, a
     log, a block entity or a built block (`WorkSafety.builtBlock`: planks, cobblestone, glass, a roof). The base's plan
     is tested block by block (the ground and the 22 cells over it), not on the plan's bounding box: a settlement's
     plan holds its outside paths (the east landing and the path to the grove pad, 31 to 34 blocks out), so its box
     would cover open ground. The alarm box is the walls' half plus 4 of every base still standing, the scout's own and
     the target included (a GameTest base's own half; `FactionDirector.defenceAlarm` uses the layout's half for every
     base, the same for real ones). The ground under a solid overhang is read at the overhang's top (the heightmap's),
     so a lair's vantage under a rock ledge is not found; one under leaves is. The walk itself is R4b's: when the scout
     cannot get to the first column, it takes the next. `PillarClimb` (beside `Scaffold`) is the climb and descent
     the woodcutter drives, lifted out of `WoodcutterJob` unchanged, with the look at each block as a hook.
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
* the **distinct soldiers** seen, by UUID (the model of a player keeping track of each soldier it watches; the runtime
  entity id is not used, since it changes when the chunk reloads and the saved accumulator would then count one soldier
  twice), each with its visible power (`FactionDirector.power`: armour worn and weapon held) at its last sighting;
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
union), `middle` (boolean), `watchTicks`, `at` (the watch's last sample), `vantage` (text), `by` (the scout's name) and
`faces` (the UUIDs of the soldiers counted, so a sighting of the same face is not counted again in the plan, 5.5).
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
* Otherwise: `soldiers = r.soldiers` (the faces counted, no extrapolation: a floor, 5.5), `power = max(1, r.power)` (1, a
  bare soldier, when none was seen), `heroPower = the sum of the heroes' war power seen in that watch`.
* No prior, no drift, no losses, no fading inside the freshness window, no hero carried over from an older report (the
  plan takes known heroes from the sightings, 5.5, a watch's heroes among them). The estimate is what the scout
  counted. What the council prepares for beyond it is the plan's reserve (5.5), which never lowers the estimate and
  never stands in for it.

A good recon that counted 0 soldiers is a real "about 0": the scout saw the middle of the yard and a third of it for a
minute and nobody was there (the garrison marched out, or the faction fell). That is the "garrison seen gone" case;
whether an army of theirs seen elsewhere may come back is the plan's question (5.5). A blind look is never 0. It is no
estimate at all.

### 5.2 Ageing

Stale after `RECON_FRESH` (12000), which means a new recon. Nothing in between. Today's `Intel.FORGET_TICKS` (48000),
`weight` and the hero sightings' fading go. The sightings behind the plan's reserve age the same way: in for
`RECON_FRESH`, then out (5.5).

### 5.3 Heroes and lairs

In the estimate, heroes count only if seen in the watch, by their war power at full strength, under the soldier backbone
(`HeroRules.countedPower`) as today. A hero indoors or away for the whole minute is not in the estimate, as a player
would not know of it there; a hero the faction's own people saw elsewhere in the last half day joins the plan's reserve
(5.5). Lairs: monsters counted, power 1 (3.7), and no reserve for now (5.5).

### 5.4 The decision, the launch line and the war rate

The decision keeps its form but reads the **plan** of 5.5, not the bare count. Among the targets with a usable report it
takes the one with the least planned defence, then the nearest: choosing by the count alone could pick a base whose
reserve then holds the council back while another base would go. Two thirds of the plan at the interval
(`Intel.twoThirds`), `Intel.partySize` for the party. **Inside the interval** (after `interval / 4`), the 3 to 2 of
`Intel.threeToTwo` applies only to a target whose faction struck us since our last raid (`struckBy` after `lastRaidAt`;
section 6), against the counter-attack's own plan (section 6). Today `counter = !intervalOver && threeToTwo(...)` fires
on any estimate. Ordinary recons now arrive a few thousand ticks before the interval ends (2.1), so without this
condition the council would launch early at 3 to 2 whenever a recon happened to arrive early. The counter-attack stays a
reply to a strike. So **inside the interval the target is chosen only among the settlements of factions that struck us**
since our last raid: `raids()` picks one target and then tests it, and today it picks the least defence over every
report, so a weaker base of a faction that did not strike us would be picked, fail the strike test, and block a
counter-attack the striker's base passes. At the interval every usable report is a candidate again.

Lines (in the first and last, 4 counted, Hinae at 6.8 and a reserve of 2 make a plan whose two thirds is 7 soldiers):

* launch: "Launching a raid with 7 soldiers on the Stoneguard settlement reported at 400, 67, 16, against about 4
  defenders and Hinae Shirasuzu (scout Edrin Cole, day 5.2) and a reserve of 2 (half again the 4 counted)";
* with sightings (R11): "... against about 2 defenders (scout Edrin Cole, day 5.2) and a reserve of 3 (3 Stoneguard
  soldiers seen at our walls on day 4.9, not seen to fall) and Hinae Shirasuzu (seen at our walls on day 4.8)";
* an empty yard: "... against an empty yard (scout Edrin Cole, day 5.4) and no soldier of theirs seen away";
* `LAST_COUNCIL` while it waits: "waiting on the Stoneguard settlement: 6 soldiers to send, 7 needed against about 4
  defenders and Hinae Shirasuzu (scout Edrin Cole, day 5.2) and a reserve of 2 (half again the 4 counted)".

"Day D" is the recon's `at`, or a sighting's time. A known hero that the soldier backbone leaves at nothing (no soldier
counted or in the reserve beside it, 5.5) is named "(alone: not counted)". The phrase "a garrison none of ours has seen"
can no longer occur.

**Expected war rate: a replay of the logged launches** (`knowledge.md` 12; scripts `replay.py` and, for the reserve,
`replay2.py`, in the design sessions' scratchpads). The count is taken as the truth a recon would count: the soldiers in
the target's walls at the launch and its heroes there, heroes at 6.8 war power (a common hero at level 1) and soldiers at
power 1.5. The party is the one logged (the knowledge live check's launch table). No launcher had seen soldiers of the
target's faction away from home, alive, in the half day before any of these launches: every party that struck a launcher
died at its walls in sight, and the one hero seen at a launcher's walls (Hinae at Ember's, day 4.78 of the first run)
preceded no Ember launch on Stoneguard within half a day. The logs keep no field sightings (a scout meeting soldiers,
Natsuho fishing), so the reserve below is the share alone, and a "go" is an upper bound. The review checked it from the
timelines' positions (every poll, 660 to 890 ticks apart, of the half day before each launch; nearness only, no line of
sight, so it over-counts): for the 6 launches that go, no member of the launcher came within 32 blocks at home, or a
scout within 64, of a soldier or hero of the target's faction outside its walls box, except three Stoneguard soldiers
at Grove's walls at day 6.77, dead by Grove's launch of 7.07 and counted in its "losses" basis, so seen to fall. Of the
waits, only Grove's 4.08 had one: a Grove scout near Natsuho fishing at day 4.01, which keeps that launch waiting. No
row changes.

| launch (logged basis, "about") | counted: truth in the walls | reserve | soldiers needed, without / with the reserve | party | without the reserve | with it | logged outcome |
|---|---|---|---|---|---|---|---|
| verif 3.94 Tide → lair (look, 7) | 8 monsters | 0 (lairs) | 4 / 4 | 3 | wait (borderline: goes with gear 1.7 or better) | wait | won |
| verif 4.10 Ember → Stoneguard (prior, 7) | 3 + Hinae | 2 | 5 / 6 | 4 | wait | wait | 4 of 4 dead |
| verif 4.34 Stoneguard → Ember (losses, 1) | 5 | 3 | 3 / 5 | 3 | go | **wait** | 3 of 3 dead |
| verif 5.44 Tide → Stoneguard (prior, 7) | 0 | 0 | 0 / 0 | 4 | go | go | won |
| verif 5.47 Grove → Ember (prior, 7) | 9 + Natsuho | 5 | 9 / 12 | 4 | wait | wait | 4 of 4 dead |
| first 3.10 Stoneguard → Ember (prior, 5) | 3 | 2 | 2 / 3 | 3 | go | go | 3 of 3 dead |
| first 4.59 Stoneguard → Ember (prior, 6) | 5 + Natsuho | 3 | 6 / 8 | 4 | wait | wait | 3 set out, 3 dead |
| first 4.77 Tide → Stoneguard (prior, 10) | 3 + Hinae | 2 | 5 / 6 | 6 | go | go | 6 of 6 dead |
| first 5.56 Grove → Ember (prior, 10) | 9 + Natsuho | 5 | 9 / 12 | 6 | wait | wait | 6 of 6 dead |
| first 5.56 Tide → Stoneguard (prior, 10) | 2 + Hinae | 1 | 3 / 5 | 6 | go | go | 6 of 6 dead |
| first 7.07 Tide → Stoneguard (prior, 11) | 4 + Hinae | 2 | 5 / 7 | 7 | go | go | won, 1 dead |
| first 4.08 Grove → Ember (look 52%, 5) | 4 | 2 | 2 / 4 | 3 | go | **wait** | 3 of 3 dead |
| first 6.74 Stoneguard → Grove (look 4%, 5) | 6 | 3 | 4 / 6 | 3 | wait | wait | 2 set out, 2 dead |
| first 4.70 Ember → Stoneguard (losses, 0) | 3 + Hinae | 2 | 5 / 6 | 3 | wait | wait | 3 of 3 dead |
| first 5.59 Ember → Grove (losses, 0; a counter-attack) | 4 | 2; as a counter 0 | 2 / 4; as a counter 6 | 3 | goes at its interval, not as a counter | **wait**, and no counter | 1 set out, 1 dead |
| first 7.07 Grove → Stoneguard (losses, 8) | 4 + Hinae | 2 | 5 / 7 | 7 | go | go | beaten back, 5 of 7 dead |

The 16 are every council launch on a settlement in the two logged campaigns, plus the verification's lair raid: the
first run's clean run and its continuation, with the clean run's 4.59 and 4.70 counted once (the continuation replayed
them as 4.61 and 4.70) and the lost branch's one launch (4.77) included. Left out: the first run's two lair raids (8
monsters against a party of 3 and 12 against 8, both won without a loss; the rule waits on the first and goes on the
second, with or without the reserve) and the operator's forced raid. The logs hold one counter-attack, 5.59 (the chat
log's "Counter-attack!").

**6 of the 16 logged council launches would still go** with the same party (9 without the reserve). The three it stops
(4.34, 4.08, 5.59) were parties of 3 sent against 4 or 5 soldiers, and every raider who set out died. Of the 6 that go, 2
won (5.44, the emptied Stoneguard; 7.07, the Tide Alliance's 7 against 4 and Hinae, in the same hour as Grove's 7 on the
same base) and 4 failed: 3.10 (3 against 3) and three against Hinae (4.77, 5.56, and Grove's 7.07, beaten back). A hero
counts 6.8, about four and a half soldiers, and Hinae behind walls was worth more: the hero weighting and the hard
defence are the owner's (`HEROES_V2.md` 8.1), not this design's. Of the 10 that wait, 5 face a hero; one had won (the
lair raid at 3.94, a borderline case) and 9 had lost every raider who set out. Parties grow while councils wait (a base
hires a soldier about every quarter day while it has losses to make up), and a recon taken while a garrison is out
raiding shows it empty (verification 5.44), so some waits turn into later launches with larger parties. There are new
delays too: a recon about 0.2 to 0.3 day after the order, up to `RECON_WAIT` more while no scout is home, and a day's
back-off after lost scouts.

**Expect about 35 to 50% of today's launches** (2 or 3 in a 7-day campaign like the verification's 5, 4 or 5 where the
first run had 11), with fewer parties sent against more soldiers than they number and no launch "against about 0" on a
full garrison. This is a replay with assumptions, not a measurement. The live check measures it (10.4). Nothing here
tunes the defence: the 5-high walls, the two-thirds rule, the party rule and the hero weighting stay as the owner keeps
them.

### 5.5 The plan: what the scout counted is a floor (the owner's answer 1)

The owner, on counting only the soldiers the scout saw: "they can assume that what they saw is not the enemy
mainforce". The estimate (5.1) stays the faces counted. The council **plans** against more: what it counted, and a
reserve for what it could not see.

The share is the answer to the owner's words. The **sightings** (`seenAway` and the known heroes below) are an addition
beyond them: they bring what the faction's people saw outside a focused report into the plan, only ever upward, and
only on top of a fresh good recon. Question 1 asks the owner to keep them. They are built last (R11, after the council's
switch R5); until then, and for good if the owner says no, the ledger is empty, `seenAway` is 0 and the plan's heroes are
the watch's.

**The rule** (pure, `logic/campaign/Reserve`, task R10):

```
plan soldiers = counted + reserve
reserve       = max( ceil(RESERVE_SHARE x counted), seenAway )
seenAway      = soldiers of that faction our people saw away from that base (outside its box, half + 8)
                within RECON_FRESH, not seen to fall, and not among the faces the watch counted
plan heroes   = the heroes the watch saw
                + the heroes of that faction our people saw within RECON_FRESH that the watch missed
plan          = HeroRules.countedPower(plan soldiers' power, plan heroes' war power)    (the soldier backbone)
```

* The share's soldiers count at the watch's mean power (5.1); when the seen-away soldiers are the larger term, each
  counts at its own visible power. A hero counts at its war power at full strength, as in a watch
  (`BaseWatch.seenHeroPower`).
* **The plan's unit** (one soldier, for `Intel.twoThirds` and `Intel.threeToTwo`) is the mean power of the plan's
  soldiers, counted and reserve together; 1, a bare soldier, when it holds none (an empty yard and nobody seen away).
* **The larger term, not the sum.** Both answer one question, how many soldiers are out of sight: the share assumes some,
  the sightings know of some. Adding them would count a party our people saw twice.
* Faces are told apart as in the watch (3.6): a soldier seen at our walls and later counted in its yard is counted once,
  in the count.
* **Sightings never make an estimate.** No fresh good recon, no plan and no launch, however many soldiers were seen away
  (2.3). The reserve only raises what the council prepares for: it never lowers the count and never stands in for it. A
  passing look still carries no count.

**Choosing `RESERVE_SHARE`** (5.4's replay, the guard model of 3.4, and where the unseen soldiers really were):

| `RESERVE_SHARE` | launches that go, of 16 | of them won | losing launches stopped | wins stopped | guard-model watches whose plan covers the garrison |
|---|---|---|---|---|---|
| 0 (the first version) | 9 | 2 | | | 96.6% |
| 0.25 | 8 | 2 | 1 (4.34) | 0 | 100% |
| **0.5** | **6** | **2** | **3** (4.34, 4.08, 5.59) | **0** | 100% |
| 1.0 | 2 | 1 | 6 | 1 (the Tide Alliance's 7.07) | 100% |
| 2.0 (the raid party, two thirds of the army, always out) | 1 | 1 | 7 | 1 | 100% |

* **The guard model needs no more than a quarter.** Over 2040 watches of 1200 ticks from the height where the scout
  stops (51 vantages in the band, both layouts, 6 guards; the model of 3.4, `share.py`), 1970 counted all six guards
  and 70 counted five; none fewer. Section 3.4's own runs saw one watch count four of six, which a quarter leaves one
  short and a half covers. The model is of guards on their posts and says nothing of soldiers away; by itself it does
  not argue for more than 0.25.
* **Where the unseen soldiers were, live.** Over the knowledge live check's three timelines (509 polls of every NPC,
  `away.py`; the two heroes over the first run's and the verification's, `heroes.py`), soldiers outside their own
  walls box (half + 8) were 1.5 to 5.2% of all soldier positions,
  and in 92 to 98% of polls a faction had none out. Nearly every one out was on a raid (marching, at the rally point, at
  a heart, coming home); a few were wounded falling back. So an army out of sight is a raid party, out a few percent of
  the time and mostly dying where it went, or a hero on a work duty: Natsuho (fisherman duty) was outside her walls in
  59% of polls in the first run and 30% in the verification; Hinae (a guard) in 1%.
* **The replay** (5.4): a half stops the three launches made with fewer soldiers than the scout would have counted
  (parties of 3 against 4 or 5; every raider who set out died) and keeps both wins. A quarter stops one of them. As many
  again stops a win and leaves 2 launches in 16; assuming the raid party always out leaves 1. The war all but stops.
* **At a half, two thirds of the plan is the count.** For soldiers alone, two thirds of C + ceil(C / 2), in whole
  soldiers, is C for every C (checked from 1 to 11). A council attacks with at least as many soldiers as its scout
  counted; since its party is two thirds of its own garrison, it attacks once its garrison is about half again the
  count.

**Decision: `RESERVE_SHARE` = 0.5.** It is the planning margin the owner asked for, set by the replay and by that
reading; it is not a number the guard model needs (that would be 0.25). Seen-away was 0 in every row of the replay, so
the replay tests the share alone; the live check measures the seen-away and hero terms (10.4).

**A base seen empty** (0 counted, the middle in view for a minute). The share adds nothing: half of none is none, and an
empty yard watched a minute hides no garrison (every council keeps at least `homeDefenders` at home when it raids, so the
yard is empty only when its people are away or dead). The army seen away stays in the plan: it is exactly the force that
may come back. So the raid on an emptied base stays possible: a base emptied by an army nobody of ours saw leave is
"about 0" and any party goes (verification 5.44, won); a base whose army our people saw marching off is struck by a party
of at least two thirds of that army, the same odds the council takes against any garrison. The seen-away term is usually
0 by then anyway: a party beaten at our walls walks home before our recon can watch its base (the survivors leave at the
fight's end; the urgent recon waits 600 ticks of quiet, then walks out at about 5 ticks a block, 850 ticks for 170
blocks, and climbs), so its survivors are counted at home by face. (A floor for an empty yard would change little
anyway: two thirds of a plan of 3, a council's `homeDefenders`, is 2, below the smallest party of 3.)

**Heroes known but unseen. Decision: they join the plan.** A hero of that faction our people saw within `RECON_FRESH`
(at our walls, in the field, or in an earlier watch) and the watch missed is in the plan at its war power, by hero id,
once (R11; before it, the plan's heroes are the watch's).
One seen falling stays: it rises at home after `heroes.reviveSeconds` (600 s, 12000 ticks), sooner than a raid's plan
runs out (about 15000 ticks). Its sighting is dated at its expected return (the fall plus `heroes.reviveSeconds`, the
rule every faction knows, not a hero's own revival talent, which nobody of ours can see) and counts from the fall until
`RECON_FRESH` after that return. A watch files every hero it saw as a sighting too, so a hero seen in an earlier watch
and missed by the newest is still known. Why:
a hero is the largest single item in a defence (6.8 for a common hero at level 1, about four and a half soldiers) and
the one most often away (Natsuho, above). The soldier backbone (`HeroRules.countedPower`, `HEROES_V2.md` 8.1) still caps
heroes at 1.5 times the soldiers beside them: against an empty yard with no soldier seen away a known hero counts
nothing, and the lines name her "(alone: not counted)". Heroes get no share: they are few and known by name, and the
council plans for those it has heard of.

**Where sightings come from** (task R11; a faction may know less than its people saw, never more):

| who sees | what | the faction knows it | line |
|---|---|---|---|
| any member NPC or hero at home (the alarm's `noticed` reads only these) | an enemy soldier or hero the alarm noticed (`defenceAlarm`: inside our walls box, seen or heard), or one in our home area (64 blocks) that such a member within 32 blocks sees (the roll call's witness rule) | at once | "seen at our walls on day D" |
| a scout on any trip | an enemy soldier or hero in sight within 64 blocks, outside the walls box of every base of its faction the scout knows of or sees | at the station, carried as news; lost if the scout dies | "met by scout X on day D" |
| a recon scout on its watch | a soldier or hero of the target's faction in sight outside the target's box (a party leaving, a patrol below the pillar), and every hero it saw in the watch | with its report, at the station | "seen leaving by scout X on day D"; a hero in the yard "seen by scout X on day D" |
| raiders, far workers, member players | nothing yet | | |

A sighting keeps the entity's UUID (never the runtime entity id, which changes at a reload), its faction, a hero's id,
its visible power (a hero's war power at full strength), the time, the place, the source, and whether it was seen to
fall. A fall is marked through hooks that exist only in part today, so R11 builds each one:

* a soldier falling at home: `BaseWatch.enemyFell`'s rule (in our home area, or seen by a member NPC standing in it),
  which today counts a loss and from R5 records the strike; it now also marks the sighting fallen;
* a soldier a scout sees fall on its trip: carried home with its sightings, marked when told at the station
  (`enemyFell` takes only witnesses at home today, so this is new, and only scouts carry it, as the table says);
* a hero's fall: `enemyFell` returns at once for a `HeroEntity`, and `HeroManager`'s death path tells the fall only to
  the killer's faction (its witnesses, `RollCall.tellOrCarry`). R11 files the fall for every hostile faction whose
  members witnessed it (the roll call's witness rule: at once at home, carried home by a scout away).

A sighting counts for that faction's reported settlement nearest to where it was seen (as `BaseWatch.enemyFell` places
losses today; each faction has one settlement until Phase 3's new bases), and one made in a watch for the base watched.
`FactionState.sightings` keeps the newest sighting per UUID, at most `SIGHTINGS_MAX` (64), the oldest dropped first;
one older than `RECON_FRESH` is out (no fading, as 5.2).

**Lairs: no share for now** (`RESERVE_SHARE_LAIR` = 0; question 2) and no sightings (a lair's monsters cannot be told
from wild ones, 3.7). Lairs start at 5 monsters and grow to 12 (today by one a day; under the lair-energy design by the
energy they spend and the nests they grow). With a half, a party of 3, the smallest,
could raid only a lair counted at 4 or fewer (5 counted make a plan of 8, two thirds 5, against the party's 4.5);
without it, one of up to 7. All three logged lair raids were won without a loss (8 monsters against 3 twice, 12
against 8), and a lair's count already errs high at night (3.7). The lair-energy design (`docs/design/lair_energy.md`)
changes what a lair holds; a lair share belongs with it if the live check finds lair counts low (10.4).

**The counter-attack's plan** is this plan with a share of 0: the count, the seen-away term (the strikers seen
walking away, and any other soldier of theirs seen away) and the plan's heroes (section 6).

### 5.6 Optics and the tech phase's hooks (the owner's answer 1, second half)

The owner: "when the tech phase arrives, there will also be items that should facilitate like drones or binoculars".
Under these rules an instrument can change a recon in four ways only: the focused look's **reach** (how deep the band
goes), the **vantage** (a pillar or hill, or above the yard), the **coverage** that vantage gives (the walls set it, 3.3)
and so the **reserve share**. The watch's length is set by the guards' rounds (3.4), not by the eye. Each instrument is a
row of data (`logic/campaign/ReconOptic`: reach, vantage, share), created by the first task that builds an optic (R12
for the spyglass, else Phase 6); until then the eyes' values are `Recon`'s and `Reserve`'s constants. The report records
the optic its watch used (R12 adds the tag; none means the eyes). Only the eyes' and the spyglass's rows are decided
here; the Phase 6 rows are placeholders for that phase's design pass and go into BALANCE.md only when built.

| instrument | when | reach | vantage | coverage (25 points) | `RESERVE_SHARE` |
|---|---|---|---|---|---|
| the eyes | now | 72 | a pillar or high ground, 61 to 72 (3.5) | 32 to 60% (3.3) | 0.5 |
| a spyglass (vanilla) | in the rules now; built in R12 | 96 | a pillar or high ground, 61 to 96 | the same (the walls set it) | 0.5 |
| binoculars (Phase 6) | not built | 96 (vanilla's line of sight caps it) | as the spyglass | the same | 0.5 |
| a drone (Phase 6) | not built | its flight | over the middle of the yard (or 12 blocks off it on an axis), at least 36 above the floor | 80 to 88%, both layouts (68% over a grown base's corner quarter, 17 blocks off the middle, until 48 up) | 0.25 (a placeholder) |
| a balloon or scout helicopter (Phase 6 scouting vehicles) | not built | the eye's or spyglass's aboard | outside the walls, at any height | at most 56 to 60%, a pillar's | 0.5 |
| a radio tower (Phase 3) | not built | | | | (delivery only) |

**The spyglass: vanilla, so never behind a tech level.** The owner's rule "Vanilla things should stay always available"
keeps it out of the tech tree, and a player scouting a base uses one. Its effect here is its zoom: the focused look
reaches 96 instead of 72, so the band is 61 to 96, 35 blocks deep instead of 12. As a player must, the scout holds the
spyglass in its hand while it watches (and puts it away to climb or fight). That gives more columns clear of trees and
more natural high ground in range; it does **not** give a shorter pillar. At the same coverage a farther column needs a
taller one (below): on flat land the gate sides stay under the cap of 20 out to 96, while the north side (both layouts)
and the south of a grown base already need 21 at 72 (the north of a grown base 20 to 21 from 68), so on flat land the
deeper band helps only the gate sides and the south of a start layout. The reach stops at 96 because vanilla's line of
sight ends at 128 blocks for every entity (`LivingEntity.hasLineOfSight`, and `BaseWatch.clear` the same): from 96 the
yard's far side lies within about 120. It changes no count and no share.

| smallest good pillar on the side's axis, start / grown (`far.py`) | 61 | 72 | 80 | 88 | 96 |
|---|---|---|---|---|---|
| west or east (a gate) | 12 / 12 | 14 / 14 | 16 / 16 | 17 / 17 | 19 / 19 |
| south | 12 / 17 | 14 / 21 | 16 / 23 | 17 / 25 | 19 / 27 |
| north | 17 / 18 | 21 / 21 | 23 / 23 | 25 / 25 | 27 / 27 |

**Decision:** the spyglass row is part of the rules now, and the spyglass is built as R12 after the live check (R8). A
spyglass is two copper ingots and an amethyst shard; copper is mined and smelted today, but no job gathers amethyst (R12
adds it: a miner or cave miner that opens a geode takes the shards, as a player does, and the blacksmith crafts the
spyglass for a scout that has none). Its one gain matters only where trees or hills blind the 61 to 72 band, which R8
measures (10.4). If more than one recon order in five ends blind on every side tried, R12 comes next; otherwise it
waits.

**Binoculars** (the owner's own example) gain nothing over the spyglass from these rules alone: an optic at the eye
changes only the reach, and the 128-block line of sight above caps the reach near 100. What more they give is for the
Phase 6 design pass to set; seeing through walls is not one (it would break "see by line of sight"). Their row keeps the
spyglass's values until then.

**A drone** watching from above the yard sees every open point, the blind alleys along the walls included; what stays
out of sight is indoors and away, so a quarter is proposed for its share (a placeholder for Phase 6; the seen-away term
is unchanged). Its gain is its place over the yard, not its height: from outside the walls even 48 blocks up sees at
most 56 to 60%. Over a grown base it needs height too (25-point share, start / grown, the middle in view each time;
`drone.py`; over a grown base's corner quarter, 12 blocks off on both axes, 68% from 20 to 36 up and 80% at 48):

| a drone hovering over | 12 up | 20 | 28 | 36 | 48 |
|---|---|---|---|---|---|
| the middle | 80 / 60 | 80 / 60 | 80 / 68 | 80 / 80 | 80 / 80 |
| 12 blocks off the middle | 80 / 60 | 80 / 72 | 88 / 80 | 88 / 80 | 88 / 80 |
| 40 blocks out, outside the walls | 56 / 40 | 56 / 40 | 56 / 48 | 56 / 56 | 60 / 60 |
| 61 blocks out, the standoff | 40 / 32 | 56 / 44 | 56 / 48 | 56 / 56 | 56 / 56 |

The share is keyed by the instrument and its hover height (at least 36 above the floor), not by the coverage a watch
happened to get. A drone at 36 is above the alarm box (16 above the floor) but within the archers' 48 blocks: whether
soldiers shoot it down is Phase 6's balance.

**Scouting vehicles and the radio.** A balloon or scout helicopter gives height without a dirt pillar but stays outside
the walls: a pillar's row. Phase 3's radio tower lets a scout report from afar: the recon and its sightings reach the
station when sent, not when the scout walks in; the report's time stays its watch's. It changes when the faction knows,
not what.

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
  and only against a faction that struck us since our last raid (5.4). Inside the interval the council chooses its
  target among those factions' settlements only, so a weaker base of a faction that did not strike us cannot stand in
  the way (5.4). If the recon finds the garrison weakened by the beaten raid, the counter-attack goes. If it finds it
  whole, the council waits.
* **The counter-attack's plan** is 5.5's plan **with no share**: the count, the seen-away term (the strikers our people
  saw walk away from our walls and did not see fall, and any other soldier of theirs seen away) and the plan's heroes
  (the watch's, and once R11 is built the heroes known from sightings). The share stands for an army out of sight;
  this army was in sight at our walls, and what came, what fell and what walked away are known. Three to two of that: 5
  soldiers needed for 3 counted (4.5 rounds up), 6 for 4, 8 for 5. With the share as well it would be 8, 9 and 12, which
  a council's party (two thirds of its garrison) reaches only with a garrison of 12 to 18: counter-attacks would all but
  end. A launch at the interval keeps the full reserve (5.5); the counter-attack, though early, still asks at least half
  as much again as a launch at the interval would against the same report.
* **Survivors.** An enemy party beaten at our walls that walks home is in the reserve (R11) until a watch counts it in
  its yard (by face) or half a day passes. The survivors leave at the fight's end; the urgent recon waits 600 ticks of
  quiet, then walks out (about 850 ticks for 170 blocks at a walk, 5 ticks a block as in 2.5; the roll call's plan
  allows 10) and climbs, so the survivors are usually home before its watch, and counted there.
* In the replay (5.4) the logs' one counter-attack, Ember's 3 on Grove's 4 at 5.59, waits as a counter-attack (6 needed)
  and at its interval (4 needed); every raider who set out on it died.

A raid beaten at our walls therefore leads to a recon about 0.2 to 0.3 day later, and a counter-attack only if the scout
saw the enemy weakened: 3 to 2 against what it counted and the strikers seen walking away. Today the council attacked
"about 0" while the enemy still kept its third at home.

## 7. Deleted, kept, save format

Every deletion below lands in one task, R5 (the council's switch), together with the tests it breaks and their
rewrites; R1 to R4b and R10 only add, so the build and both suites pass after each task (section 11). R11 (sightings)
comes after the switch and deletes nothing.

**Deleted (code):**

* `Intel.Prior`, `Intel.lairPrior`, `Intel.estimate(Look, losses, lossAt, heroPower, Prior, now)`, `Intel.better`,
  `SAME_VIEW`, `FORGET_TICKS`, `weight`, `heroPower(Collection, now)` with its fading, `mergeHeroes` and `Intel.Look`
  (replaced by `Intel.Recon`). Kept: `Estimate`, `twoThirds`, `threeToTwo`, `partySize`, `TIE`, the hero list's parse and
  format.
* `Report.look`, `Report.losses`, `Report.lossAt`, `Report.seenAt()`; the look and loss logic in `Report.mergeBase`.
  Passing reports lose their heroes and counts.
* `BaseWatch.enemyFell`'s loss counting (it becomes the strike notice, section 6, and marks a sighting fallen, 5.5);
  passing looks' soldier and hero counts.
* `FactionDirector.estimate`, `likeOurs`, `seenText`; `watchTarget`'s looks (it keeps only the ruins: `seesFall` within
  `HEART_SIGHT` and the party's own heart break); `deliverRaidIntel` delivers only ruins; `forceRaid`'s prior (an
  operator's raid uses the faction's usable recon if it has one, else says "no scout's report").
* `ScoutJob.mergeOnTrip`'s look merge.
* `/fc admin dump`'s `look` and `losses` fields; `BaseInfo`'s "seen falling at our walls" text.

**Deleted (tests):** in `IntelTest`, the prior, the partial view, ageing and drift, never below what was seen, losses
and their fading, heroes' fading, `better` and the lair prior; in `KnowledgeGameTests`, the garrison that leaves unseen
is believed at home (rewritten, 10.2), losses lowering the estimate and the counter-attack on them, losses fading, heroes
fading and the lair's prior; `MissingNpcGameTests` line 508 and `HeroGameTests` line 263 (the hero raid draft; both
`seen.look`, an empty yard) are rewritten with a recon of an empty yard, which still launches under the reserve (half of
none is none). (`FCGameTests` no longer reads a look: its `groveSaw` test went with the pacts in f0c837f.) Every other
council test that launches on a count is recalibrated for the reserve: a party launches against C counted only with C
or more soldiers (5.5; 10.2 item 13).

**Kept:** ruins facts (`Report.fallen`, `BaseWatch.ruins`, `seesFall`, the raid party's ruins, "ruins stay ruins"), base
positions from any look, `BaseWatch.SIGHT` (64), the raid's plan (`raidPlanTicks`, `raidTurnHomeAt`), the operators'
truth (`LAST_COUNCIL`'s `[truth: …]`, `FactionDirector.defenders`, `defencePower`, `heroDefence`, `heroWarPower`) and
`KnowledgeIsolationTest` (which from R5 also checks that the council reads only `Report.recon`, and from R11 the
faction's own `FactionState.sightings`).

**Save format** (`CampaignData.FORMAT` 5, logged only):

* `Report`: new tag `recon` {soldiers, power, heroes, coverage, middle, watch, at, vantage, by, faces}, `reconFailedAt`,
  `reconFailures`, `reconSidesTried`. Old `look`, `losses`, `lossAt` and `heroes` tags are **ignored on load** (from R5;
  until then the old council still reads them): an old report is a position only, and its base is judged again by a
  recon.
* `FactionState`: new `reconOrders` (at most 2: target position, subject, kind, home base, orderedAt, urgent, scout UUID,
  takenAt, and from R3 what its scout told at the station, `reported`, with the sides an empty-handed trip tried,
  `reportedSides`: a taken order whose outcome a restart lost would never end), `struckBy` (faction to time),
  `reconUrgentAt` (R3: faction to the time of the last urgent order, for 2.4's one urgent order a quarter interval) and
  `sightings` (at most 64: entity UUID, faction, hero id, power, time, place, source, fallen; 5.5, task R11). An old
  `raidIntel` is kept only if it is ruins.
* `ScoutJob`: the recon state, its target, the side tried, the pillar (`Scaffold.Pillar.save`), the watch accumulator
  (soldiers by UUID: a runtime entity id changes at a reload and would count one soldier twice), `reconReport` and the
  sightings it carries home (R11). An old job loads as today's.
* `Report.recon`: an `optic` tag from R12 (none: the eyes).

## 8. Interactions

* **Missing scouts and the roll call.** The recon trip has its own plan (2.5). The council learns that a recon failed
  only by its scout going MISSING (its order lapses, and the target backs off). A death a recon scout saw is carried home
  as news, as on any trip.
* **The raid plan** (`raidPlanTicks`, `raidPlannedBy`, `raidTurnHomeAt`): unchanged. While its raid is out a faction
  places no recon orders (the counter-attack cannot launch then anyway).
* **Heroes on a raid.** `HeroRaid.choose` computes `homeHeroKnown` from a home hero within 64 blocks (an open item,
  `knowledge.md` 10). It should read "the recon this raid was launched on saw a hero" (R9, small).
* **The Hollow.** It estimates no defence: it raids the nearest settlement whenever a lair has 4 monsters and its
  interval passed, and the owner's rule is about estimating a defence. How it finds settlements (today a 300-block sense,
  `knowledge.md` 9) and how far it reaches now belong to the lair-energy design (`docs/design/lair_energy.md`: lairs
  gather energy and spend it on buildings nearby, more monsters or a larger detection range); nothing here changes it.
  It files no sightings, and its monsters are in no faction's reserve (they cannot be told from wild ones, 3.7).
* **Sightings and the roll call.** A scout's sightings travel home as its news does (`missing_npcs.md` 5): told at the
  station, lost with the scout if it dies, told late by one presumed dead that walks home (then aged by their own
  times). A soldier's fall a scout sees on its trip is carried home the same way and marks its sighting fallen; other
  members away carry no sightings and no falls yet (5.5's table). An enemy hero's fall is filed from `HeroManager`'s
  death path for every hostile faction whose members saw it (5.5), not only the killer's as today.
* **Sightings and a faction's fall.** Every member of a fallen faction dies where it is; nobody of ours sees most of
  those deaths, so their sightings stay until they age out (half a day), and a council may wait that long for an army
  that is dead. It learns of the fall from the ruins only (section 7), as today.
* **Sightings and a hero's return.** A hero seen falling stays in the plan: its sighting is dated at its expected
  return, the fall plus `heroes.reviveSeconds` (600 s, 12000 ticks), and counts until `RECON_FRESH` after it (5.5).
* **Elimination kills every member.** A recon scout of a faction that is eliminated dies where it is, with its report.
  Orders of a dead faction are dropped. A recon of a base whose faction fell watches an empty yard: a good report of
  "about 0" (with the plan of any of its soldiers our people saw away in the last half day, 5.5). The party sent there
  finds the ruins at 48 blocks ("found in ruins"), a player-like mistake. The heart stands inside the station hall, so no
  look from outside sees it.
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
  player-filed recon at the station is a later item, and so are a member player's sightings (5.5).
* **Night, rain and monsters.** Rays do not depend on light: the scout sees the yard at night as by day, and a player
  sees a lit yard too (lanterns and torches). Faction NPCs do not sleep, so guards keep their posts at night and the
  count holds. The scout walks at night as scouts do today (it is not remote work under `remoteWorkInDaylightOnly`;
  the woodcutter's nightfall rule does not apply). Rain changes nothing. Monsters: on the ground, the usual step-aside
  rules; on the pillar, a hit ends the watch (3.5). A monster's own target search (vanilla's, the same quirk) does not
  pick an NPC 6 or more blocks up, and a spider that climbs the pillar and hits it ends the watch like any hit. At a
  lair, the night's wild monsters raise the count (3.7).
* **Performance.** One sample is 25 rays plus a line of sight per soldier in the box, every 40 ticks, for each scout on a
  watch: the same as a passing look today. The climb adds one sample per block from 4 up (at most 17 a side), and the
  column search reads at most 25 x 20 columns per side once (the band's 17 x 12 and 4 blocks round it, task R4a). With at most two open orders per faction, that
  is at most a few hundred rays a second across a campaign of four factions. Sightings: at home, one pass a second over
  the enemies in the home area while there are any (with the alarm's own sweep), a line of sight from the nearest
  members within 32; a scout's, with its existing look every 40 ticks.

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
| `Recon.WAIT_TICKS` | **6000** | an order untaken this long asks for a second scout (the owner agreed, 2026-11-25) |
| `Recon.MAX_SCOUTS` | **2** a base | capacity for watching and exploring (the owner agreed, 2026-11-25) |
| `Recon.VANTAGE_TICKS` | **4800** | the plan's allowance: two sides (a climb and descent of up to 20 each, about 660 ticks), the walk round (about 200 blocks, 2000 ticks at the plan's 10 ticks a block), a hold (1200) |
| `Recon.THREAT_RANGE` | **16** | an enemy in sight this close: the scout on its pillar holds still and does not start down (3.5) |
| `Recon.HOLD_MAX` | **1200** | the longest it waits on the pillar after its watch for an enemy to leave; inside `VANTAGE_TICKS` |
| `Recon.QUIET_TICKS` | **600** (`NOTICE_MEMORY`) | an urgent order waits until the alarm has been quiet this long (the fight is over) |
| `Recon.URGENT_GAP` | `interval / 4` (9000) | one urgent recon per enemy faction per counter-attack window |
| lair grid `reach` | **10** (the leash) | the monsters' ground, not the roofed hall |
| `Reserve.SHARE` | **0.5** | 5.5: the planning margin the owner asked for. At a half, two thirds of the plan is the count head for head; the replay's 6 of 16 go (it stops three launches that lost every raider who set out and keeps both wins). The guard model alone needs 0.25 |
| `Reserve.SHARE_LAIR` | **0** | 5.5: at 0.5 a party of 3 could not raid a lair of 5 (lairs start at 5); all three logged lair raids won without a loss; `lair_energy.md` changes lairs (question 2) |
| `Reserve.SHARE_DRONE` | 0.25 (a placeholder for Phase 6; no task here builds it, and it goes into BALANCE.md only when the drone is built) | 5.6: a watch from at least 36 above the middle of the yard sees its blind alleys (80 to 88% of the points); indoors and away stay unseen |
| `Reserve.COUNTER_SHARE` | **0** | 6: the counter-attack's plan is the count and the strikers seen walking away; with the share, 3 counted would need 8 at 3 to 2 |
| `Sightings.FRESH_TICKS` | **12000** (= `Recon.FRESH_TICKS`; R11) | a sighting counts as long as a recon does; a hero seen falling rises at home in 600 s (12000 ticks) |
| `Sightings.MAX` | **64** a faction (R11) | several raid parties' worth, kept by UUID; the oldest dropped first |
| `Sightings.FIELD_SIGHT` | **64** (`BaseWatch.SIGHT`; R11) | a scout's sightings in the field: in sight within 64 |
| `Sightings.HOME_WITNESS` | **32** (the roll call's witness reach; R11) | an enemy in our home area is a sighting when a member within 32 sees it |
| `Recon.SIGHT_SPYGLASS` | **96** (R12) | 5.6: vanilla's line of sight ends at 128; from 96 the yard's far side lies within about 120 |
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

The owner's answers (section 14) add: ROADMAP Phase 6, the "Faction tech levels and research" row (recon instruments as
rows of `recon.md` 5.6: the drone, with its share and hover height; binoculars' own job, or their merging with the
spyglass) and the "Scouting vehicles" row (a balloon or helicopter is a vantage outside the walls with a pillar's
coverage, at most 60%; only a vantage over the yard sees more); ROADMAP Phase 3, the "Radio / communication tower" row
(a recon and its sightings reported by radio: known when sent, dated by the watch); BALANCE.md (War) the reserve, the
sightings and the spyglass's reach; README (war) the plan and its launch line; `missing_npcs.md` 5 (sightings are news
carried home). The spyglass stays a vanilla item in every one of them (never behind a tech level).

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
   fresh ordinary recon at 3 to 2 inside the interval waits. Inside the interval the target is chosen among the struck
   factions' settlements only: a weaker base of a faction that did not strike us, with a usable report, does not stop
   the counter-attack on the striker's base that passes 3 to 2; at the interval both are candidates again. Inside the
   interval with no faction that struck us since our last raid, there is no candidate and no waiting line (the council does
   nothing until the interval, so 10.4's "longest gap, and why" never counts that time as waiting for a report).
6. The watch accumulator: distinct UUIDs counted once over 30 samples (and once when the accumulator is saved and
   loaded mid-watch), the point mask's union, the middle seen once is enough, a hero's power kept.
7. The vantage ring: candidates at Chebyshev 61 or more and Euclidean 72 or less, the axis first, then the nearest the
   standoff; side order (the gate side facing home, then the other, then the side facing home, then the last; a further
   attempt starts at the first side not yet tried, so two trips cover all four); a lair's gap axes at 46 to 64;
   climbing two past the first good height, never past 20.
   The hold: an enemy within 16 keeps the scout up; a hit or a broken pillar block brings it down; the hold after the
   watch never passes `RECON_HOLD_MAX`, and the plan (item 8) covers it.
8. `Whereabouts.reconPlan` and the follow-up need on MISSING.
9. Kept as they are: `twoThirds`, `threeToTwo`, `partySize`.
10. `Reserve.plan` (5.5): 4 counted, nothing seen away: 6; 4 counted and 3 seen away: 7 (the larger term, not 9); 0
    counted, nothing seen away: 0; 0 counted and 5 seen away: 5; a face the watch counted is not in the reserve; one seen
    to fall is not; one seen 12000 ticks ago is in, 12001 out; two thirds of the plan is the count for 1 to 11 counted;
    the plan's unit is the mean power of its soldiers, 1 when it holds none; a hero seen at our walls and missed by the
    watch joins at its power, one filed by an earlier watch and missed by this one too, one the watch saw is counted
    once; a hero seen falling counts from its fall until 12000 after its expected return (the fall + 12000), not after;
    a known hero with no soldier beside it counts 0 under the backbone; a lair's share is 0; with an empty ledger (R5
    before R11) the plan is the count, its share and the watch's heroes. (The Phase 6 rows of 5.6 are tested when built.)
11. The sightings ledger (pure): the newest per UUID; a soldier's fall marks it and a later sighting does not undo it (a
    hero's does); the cap of 64 drops the oldest; a sighting counts for its faction's reported settlement nearest to
    where it was seen, a watch's for the base watched.
12. **Never an estimate**: no recon, a blind recon or a stale one gives no plan, whatever the sightings hold.
13. The counter-attack's plan: the count and the seen-away, no share; 3 counted need 5, 4 need 6, 5 need 8.
14. The target by its plan: of two bases, the one with the smaller count but the larger seen-away reserve is not chosen.
15. The lines: each form of 5.4 (the share's reason, sightings, a hero, "alone: not counted", an empty yard, the wait).

### 10.2 GameTests

Each test has its own `batch`, uses `skyAccess = true` where a heightmap is read, and removes every pillar and wall it
built in its cleanup. Items 1 to 3, 8, 9, 11 and 12 run a real scout and watch in `mobility_field`. The council tests
(3's council side, 4 to 7, 13 to 17) run on the `platform` as `KnowledgeGameTests`' council tests do today (`base(h,
40)`: half 2, radius 3, so a home area of 6 blocks, `RollCall.homeRadius` = the walls' half plus 4), with the target a
bare record 200 blocks off (`enemyBase`) and its recon delivered by the test as `deliver` hands in a report today; their
enemy soldiers and heroes are placed and moved by the test. Item 18 runs a real scout on the `platform`, outside that
small home area. Three facts of the GameTest world shape them (checked by the review):

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
    `tenSoldiersAttackAGarrisonLikeTheirOwnWhereNineDo` rewritten for the reserve: it cannot keep "a recon of the same
    garrison", since 10 counted make a plan of 15 and need a party of 10, and ten soldiers at home send 6. Rewritten:
    ten soldiers at home (party 6, same gear on both sides) launch against a recon counting 6 (plan 9, two thirds 6)
    and wait against one counting 7 (plan 11, two thirds 7). It pins the reserve's whole-soldier arithmetic. Also new:
    **aCounterAttackIsNotBlockedByAWeakerBystander**: a struck faction's base passes 3 to 2 inside the interval while a
    weaker base of a faction that did not strike us has a usable recon; the counter-attack goes to the striker.
14. **aSoldierSeenAtOurWallsJoinsTheReserve**: two enemy soldiers (no AI, moved by the test) stand in our home area in
    sight of a member, then are moved off; a good recon of their base counts 1. The plan holds 3, and the launch line
    reads "a reserve of 2 (2 ... soldiers seen at our walls on day D, not seen to fall)".
15. **aSoldierSeenToFallIsNoReserve** and **aFaceCountedInTheWatchIsCountedOnce**: one of the two is killed in sight
    of a member; the other's UUID is among the delivered recon's `faces` (the watch counted it in its yard). The plan is
    the count and its share.
16. **anEmptiedBase**: the target's yard empty in a good recon. With no sighting, the council launches its smallest
    party (R5). With 6 of its soldiers seen leaving, filed as the recon scout's watch sightings (both sides in the same
    gear), a council with a party of 3 waits and one with 4 goes (R11).
17. **aHeroSeenAtOurWallsJoinsThePlan**: an enemy hero fixture seen at our walls; the delivered recon counts 3 soldiers
    and no hero; the plan holds the hero and the launch line names it.
18. **aScoutCarriesItsSightingsHome**: a scout meets an enemy soldier in the field; the faction's sightings stay empty
    until it reports at the station; a scout killed before it reports brings nothing.

### 10.3 Sabotages (each must fail a test)

An estimate from a passing look (4); the coverage gate removed (3); the middle requirement removed (unit 1 and a
GameTest variant where only a corner is in view); counting per sample instead of per face (2); losses still subtracted
(5); the party's look written as a recon (6); freshness ignored (7); a 3-to-2 launch inside the interval with no strike
(unit 5); an order never ended when its scout comes home empty-handed (unit 4); a report written at the watch's end
instead of at the station (8, 9); a passing look clearing a recon on merge (unit 3); the pillar left standing (1); the
standoff back at 53 (unit 7); the scout coming down for a passing soldier (11); a hold with no bound (unit 7). For the
reserve: the reserve left out (14); the sum of the two terms instead of the larger (unit 10); a sighting kept when the
watch counted its face (15); a soldier seen to fall kept (15); sightings that never age (unit 10); a field sighting
known before the station (18); a plan or a launch from sightings with no recon (unit 12); the share in the
counter-attack's plan (unit 13); the target chosen by the bare count (unit 14); a known hero left out of the plan (17).
From the review of the update: the target chosen over every report inside the interval (unit 5 and 13's
`aCounterAttackIsNotBlockedByAWeakerBystander`); a waiting line written inside the interval with no strike (unit 5); the
game side keying the accumulator by the runtime entity id (R4b's save-and-load watch GameTest; a pure unit test cannot
see which id it is handed); the reserve left out of the decision (13's rewritten ten-soldier test, which then launches
against 7); a hero's fall filed only for the killer's faction (R11's hero-fall test).

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
* **the plan against the truth** (5.5): at each launch, the reserve and what it rested on (the share, the soldiers seen
  away, the heroes known) beside the target faction's soldiers truly outside its walls box and its heroes away; how
  often the seen-away term was the larger; sightings recorded by source (home, field, watch), and how many of them were
  dead or back home at the launch; launches that waited on the reserve alone;
* **lairs** (5.5): each lair's count against its monsters at the watch's end; if counts run low, a lair share for the
  lair-energy design;
* **blind bands** (5.6): recon orders that ended blind on every side tried, and why (trees, hills, the cap); more than
  one in five calls R12 (the spyglass) next;
* **the war rate** against the replay's 35 to 50% of the logged launches (5.4);
* regressions as before: recruitment, stuck recoveries, tick time.

## 11. Implementation tasks (in order; one agent each)

Each task builds, passes both suites and carries its own tests. R1 to R4b and R10 only **add**: the old council keeps
judging by the old estimate until R5 switches it, so no build in between has a council with nothing to judge by, and no
build has a broken compile. Every deletion of section 7 lands in R5 with the tests it breaks. R5 plans with an empty
sightings ledger (the count, its share and the watch's heroes); R11 then adds the sightings, the designer's addition of
question 1, so the switch does not wait on them and is whole without them. The download jar is not rebuilt in this
phase (the owner deferred it until heroes are playable): no task rebuilds it or touches README's SHA line.

| Task | Size | Content | Tests in the task |
|---|---|---|---|
| **R1** | S | Pure rules, additive: `Intel.Recon` (record, `good()`), a new `Intel.estimateRecon` beside the old estimate, the merge rules of section 4 as a pure function, `logic/campaign/ReconRules` (window, priority, back-off, urgent gap and quiet, second-scout need, side order, the vantage band 61 to 72, climb past, the hold's bound, and the council's candidate filter: inside the interval only the struck factions' settlements, none without a strike), `Whereabouts.reconPlan`. Deletes nothing | unit 10.1 items 1, 2, 4, 5, 7, 8 |
| **R2** | M | Records, additive: `BaseWatch` recon sample beside the passing look (point mask, middle, UUIDs and powers, `RECON_SIGHT` 72, the lair grid's reach 10); `Report.recon` (with the counted faces' UUIDs), `reconFailedAt`, `reconFailures` with the merge and their save (`CampaignData.FORMAT` 5; the old `look` and `losses` tags are still read, for the old council, until R5); `FactionState.reconOrders` and `struckBy`, saved; `enemyFell` and the alarm also record `struckBy` (they still count losses until R5) | unit 10.1 items 3, 6; GameTests: a recon sample variant of `aScoutCountsOnlySoldiersInSight`, a save and reload of a recon and of the orders |
| **R3** | M | Orders: `ReconOrders.place` and `settle` in `raids()` (census moved up, the window, the urgent order after the quiet, every ending of 2.1), the `LAST_COUNCIL` order line. The decision itself is unchanged. (No scout takes an order before R4b, so the needs that react to untaken orders come with R4b) | GameTests with a scout's record driven by the test: an order placed in the window and not outside it, one per target and two per faction, each ending |
| **R4a** | M | The shared pillar: lift the climb and descent driver out of `WoodcutterJob` (jump and place, hold still, break underfoot, misses, save and resume) into `work/PillarClimb` beside `Scaffold`, with a "look at each block" hook. Woodcutters use it unchanged. The vantage column search (`work/ReconVantage`: side order, band, axis first, ground it can walk to, plan and alarm boxes) | every woodcutter tall-tree GameTest unchanged; a column-search GameTest in `mobility_field` (a hill, a tree, a base plan in the band) |
| **R4b** | M | `ScoutJob` recon trip on R4a, and the recruitment needs it brings (a second scout after `RECON_WAIT`, the follow-up on a MISSING scout instead of `departedAt + SCOUT_OVERDUE_TICKS`): take the order at home, keep or dig 22 dirt, travel, climb and look, climb two past, watch (the accumulator), the hold and the hits, down, the next side, `reconReport` held apart and delivered first at the station, `tripPlanTicks` recon branch, save and load; lairs on the ground on a gap axis | GameTests 10.2 items 1, 2, 3 (the blind report delivered), 8, 9, 11, 12 in `mobility_field`; the second-scout need; a watch saved and loaded midway counts a soldier seen before and after the load once (its UUID) |
| **R5** | L | The switch and every deletion: `raids()` judges only usable recons, on the plan of 5.5 from R10 with an empty sightings ledger (the count, its share, the watch's heroes): the target by its plan, inside the interval only among the struck factions' settlements, two thirds of it at the interval, the counter-attack's 3 to 2 only after a strike, on the urgent recon and its own plan (6); the waiting and launch lines of 5.4 (all but the sightings' form); `watchTarget` and `deliverRaidIntel` ruins only; `forceRaid`'s line; delete the prior, the drift, the losses, `better`, `Intel.Look`, the passing look's counts and heroes, `enemyFell`'s loss counting, the old save tags (ignored on load), the dump's and `BaseInfo`'s loss text; rewrite the old tests (`IntelTest`, `KnowledgeGameTests`, `MissingNpcGameTests` line 508, `HeroGameTests` line 263, the hero raid draft) and recalibrate every council test that launches on a count for the reserve (7); `KnowledgeIsolationTest` (the council reads only `Report.recon`). Large, and not split: a deletion and the tests it breaks must land together for both suites to pass | GameTests 10.2 items 3 (the council side), 4, 5, 6, 7, 10, 13 (with the rewritten ten-soldier test and `aCounterAttackIsNotBlockedByAWeakerBystander`), 16 (its first half: no sighting); unit 10.1 item 15 (all but the sightings' form); every sabotage of 10.3 that needs no sighting |
| **R6** | S | Operators and tools: `/fc admin dump` (recon, orders, `struckBy`), `BaseInfo` station lines, `sim.py`, `rolltrack.py` (orders, watches, holds, truth at the watch's end), `warcheck.py` (basis "recon", the recon against the truth, gaps, scouts attacked; the plan's reserve and what it rested on beside the target's soldiers outside its walls and heroes away at the launch; sightings by source) | the tools run on a short campaign's logs |
| **R7** | S | Docs: `knowledge.md` (section 9's list), `missing_npcs.md`, `MOBILITY_AND_WORK.md` 5.2, README, BALANCE.md, HANDOFF, ROADMAP (status, and the Phase 6 and Phase 3 rows of section 9). The download jar is not rebuilt in this phase | none |
| **R8** | L | Campaign verification (10.4) on a fresh world, the same seed and setup as the last two; report the replay's prediction (section 5.4) against what happened, the plan's terms against the truth, and the blind bands that decide R12 | the live checks of 10.4 |
| **R9** | S | `HeroRaid.homeHeroKnown` from the launching recon's heroes (instead of a hero within 64 blocks) | a hero raid GameTest with and without a hero in the recon |
| **R10** | S | Pure rules, additive: `logic/campaign/Reserve` (the plan of 5.5: the larger of the share and the seen-away, faces counted once, the heroes known, the backbone, the lair's and the counter-attack's share of 0, the line's reason text; with an empty ledger the count, its share and the watch's heroes) and the pure sightings ledger (newest per UUID, fallen, age, the cap, the attribution). Nothing reads it yet | unit 10.1 items 10 to 13 |
| **R11** | M | Sightings, after the switch (only if the owner keeps them, question 1): `FactionState.sightings`, saved; written at home (the alarm's noticed intruders; enemies in the home area a member NPC or hero within 32 sees), by scouts on any trip (carried with their news, told at the station, lost at a death) and by the recon watch (the target's soldiers outside its box, and every hero it saw); falls marked through the hooks of 5.5 (`enemyFell` at home, a scout's carried fall, and an enemy hero's fall filed from `HeroManager`'s death path for every hostile faction whose members saw it); the council's plan reads the ledger (seen-away, known heroes, the sightings' line form); `/fc admin dump` lists them; `KnowledgeIsolationTest` also allows `FactionState.sightings` | GameTests 10.2 items 14, 15, 16 (its second half), 17, 18; unit 10.1 item 14 and 15's sightings form; a save and reload of sightings; a home sighting and a fall seen at home; an enemy hero's fall seen by a third faction's member is filed for that faction; the reserve's sabotages of 10.3 |
| **R12** | M | The spyglass (5.6), built only if R8 finds more than one recon order in five blind on every side tried: amethyst shards taken from a geode by a miner or cave miner (vanilla's loot table), the blacksmith's spyglass (two copper ingots, an amethyst shard) for a scout with none, the scout's carry rule (held in hand while it watches), `Recon.SIGHT_SPYGLASS` (96) and the band 61 to 96 when it carries one, the report's `optic` tag and `ReconOptic`'s first rows. Never behind a tech level | a column-search GameTest on a test base with a transient sight (the 96 / 72 ratio scaled to fit `mobility_field`: a hill just beyond the eyes' scaled reach is found only with a spyglass); a miner breaking amethyst clusters the test placed keeps their shards; the recipe and the carry rule |

Order: R1, R2, R3, then R4a (it can start beside R2 and R3), R4b, R10 (pure; any time before R5), R5 (after R4b and
R10), R11 (after R5; dropped if the owner says no to sightings). R6 can start after R2 for the logging and must finish
after R5 (and after R11 for its sightings columns, if R11 is built); R7 after R5 (and R11 if built); R8 after R6 and R7;
R9 any time after R5; R12 after R8, and only if R8 calls for it.

## 12. Questions for the owner

The first version's three questions (counting only what the scout saw, a second scout, the Hollow's sense) were
answered on 2026-11-25; section 14 has the answers and what they changed. New questions, from those answers:

1. **How large a reserve, and whether what our people saw elsewhere may raise it.** Recommended: **half again the
   soldiers the scout counted**, so a council attacks with at least as many soldiers as its scout counted. About 35 to
   50% of today's launches would go: 6 of the 16 logged; the three it stops all lost every raider who set out, and both
   wins still go. A quarter again: 8 of 16. As many again: 2 of 16, and one of the two wins would not have gone. The
   design adds one thing you did not ask for: enemy soldiers and heroes our own people saw away from that base in the
   last half day (at our walls, met by a scout, seen leaving from the pillar) raise the reserve when they are more, and
   only on top of a scout's fresh count. Recommended: **keep it**, built last (R11); it changed none of the 16 logged
   launches. If you say no, R11 is dropped and the reserve is the half alone.
2. **Lairs without a reserve, for now.** Recommended: **yes**. A lair is judged on the monsters counted. With the
   reserve a party of 3 could not raid a lair of 5, where lairs start, and all three logged lair raids were won without
   a loss. The lair-energy design changes lairs; a lair reserve can come with it if lairs turn out under-counted.
3. **The spyglass.** Recommended: **a vanilla item, never behind a tech level, built after the live check if trees and
   hills often leave a target unseen**. A scout carrying one watches from up to 96 blocks instead of 72: more room to
   find a clear column or a hill, not a shorter pillar. It needs amethyst, which no NPC gathers yet.

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

## 14. The owner's answers (2026-11-25)

The answers to the first version's three questions (section 12 as committed in 9cb6637), verbatim:

1. On counting only the soldiers the scout saw: "they can assume that what they saw is not the enemy mainforce, also
   when the tech phase arrives, there will also be items that should facilitate like drones or binoculars".
2. On a second scout for watching: "ok".
3. On the Hollow keeping its 300-block sense: "monster lairs can automatically gather energy and spend energy. Its
   energy can be spent on extra buildings nearby, more monsters or increasing detection range" (as quoted in
   `docs/design/lair_energy.md` section 0, the design written alongside this update).

What they changed:

| answer | change | sections |
|---|---|---|
| 1 | The count stays the estimate and becomes a **floor**. The council plans against the count plus a reserve: the larger of half again the count (`RESERVE_SHARE` 0.5) and the soldiers of that faction its people saw away from that base in the last half day and did not see fall, plus the enemy heroes seen in that time that the watch missed, under the soldier backbone. The target is chosen and judged by that plan. A base seen empty gets no share, only the army seen away. The counter-attack's plan is the count and the strikers seen walking away, with no share. Lairs get no share for now. Sightings come from home, from scouts and from the watch, carried home like news, and never make an estimate (the review of section 15, item 2, marks the sightings as an addition beyond the answer, puts them to the owner in question 1 and builds them in R11 after the switch R5). The lines say what the plan rests on | 0 (items 4 and 6 to 10), 1, 2.1, 2.2, 3.4 (its last sentence only), 5.1 to 5.5, 6, 7, 8, 9, 10, 11 (R5, R6, R8, R10, R11), 12 |
| 1, tech | The spyglass is vanilla, so never behind a tech level; its effect is a deeper band (reach 96), not a shorter pillar; built after the live check if blind bands are common (R12). Binoculars, drones, scouting vehicles and the radio as rows of data (reach, vantage, coverage, share); ROADMAP Phase 6 and Phase 3 rows for them | 0 (item 9), 5.6, 9, 10.4, 11 (R7, R12), 12 |
| 2 | The second scout for watching is decided | 0 (item 10), 2.4, 9 |
| 3 | "Keeps its 300-block sense" replaced with a pointer to `lair_energy.md`; the Hollow still estimates no defence | 0 (item 10), 8 |

Corrections to the first version's replay, found while redoing it with the reserve (5.4): the first run's parties were
logged (the knowledge live check's launch table), and its 7.07 Grove launch sent 7, not 5 (no row changes); the 16
include the lost branch's one launch (4.77), which the first version said was left out; the logs hold one
counter-attack (5.59), not three (4.34 and 4.70 were launches at the interval); the first run's two lair raids had 8 and
12 monsters logged. The rate claim changes from 55 to 70% to 35 to 50% of today's launches. R7 no longer rebuilds the
download jar (the owner deferred it until heroes are playable). Section 13 is kept as written; its "55 to 70%" is the
first version's.

The new numbers come from throwaway scripts in the design session's scratchpad, not kept in the repository; every number
they gave is above: the replay with the reserve (`replay2.py`), the guard model's per-watch counts against each share
(`share.py`, 2040 watches), soldiers outside their walls in the three live timelines and the two heroes in two of them
(`away.py`, `heroes.py`),
pillar heights from 61 to 96 blocks (`far.py`) and a drone's coverage (`drone.py`), the last two on the review's ray
model of 3.1.

## 15. Critique of the update (2026-11-25)

An adversarial review of the update of section 14, the same day. It reran every script the update cites (`replay2.py`,
`share.py`, `away.py`, `heroes.py`, `far.py`, `drone.py`) and got the same numbers: 9, 8, 6, 2 and 1 launches of 16 for
shares of 0, 0.25, 0.5, 1 and 2; 1970 of 2040 guard watches counting all six and 70 counting five; soldiers outside
their walls box 2.0%, 5.2% and 1.5% of positions in the three timelines, with none out in 97.5%, 92.4% and 97.1% of
faction polls; Natsuho out in 83 of 140 polls of the first run and 78 of 264 of the verification, Hinae in 0 of 140 and
2 of 184; and the pillar and drone tables. (`replay2.py` gives the lair row a share of 4 where the design gives lairs
none; the row waits either way.) It read the code each new claim rests on and checked the logs. The decided rules stand:
the estimate is the count, the share is half again, a blind or stale report gives no plan, lairs and the counter-attack
take no share. What it changed:

1. **Inside the raid interval the target was chosen among every report (2.1, 5.4, 6, 10.1 item 5, 10.2 item 13, 10.3).**
   `FactionDirector.raids` picks one target by the least defence over every report and then tests it; with the 3 to 2
   limited to a faction that struck us, a weaker base of a bystander would be picked, fail the strike test and block a
   counter-attack the striker's base passes. Inside the interval the council now chooses only among the struck factions'
   settlements (none without a strike: the council then does nothing and writes no waiting line, so the live check's
   longest-gap figure is not blurred), with the pure filter in R1, a unit case,
   `aCounterAttackIsNotBlockedByAWeakerBystander` and two sabotages. (This gap was in 9cb6637 already.)
2. **The sightings are an addition, and the switch no longer waits on them (0, 5.5, 7, 10, 11, 12).** The owner's words
   were "they can assume that what they saw is not the enemy mainforce"; the share answers them. The seen-away term and
   the known heroes bring what members saw outside a focused report into the go or wait (only upward, only on top of a
   fresh good recon), against the owner's first rule ("Only estimate enemy defence when the scout or other related role
   makes a report of what he saw while focused on this particilar task") read strictly. They are kept, labelled as an
   addition, and put to the owner in question 1. R5 now plans with an empty ledger and R11 comes after it, carrying
   GameTests 14, 15, 17, the second half of 16 and unit 14; before, R5 depended on R11. The replay and a new check of
   the timelines (below) show the term changed none of the 16 launches.
3. **The fall hooks R11 needs did not exist as described (5.5, 8, 11).** `BaseWatch.enemyFell` returns at once for a
   `HeroEntity` and accepts only witnesses at home; `HeroManager`'s death path tells a hero's fall only to the killer's
   faction. The design said "`enemyFell`'s witness rule" for every fall, and "a witness away carries it home" while its
   own table gives sightings to scouts only. R11 now names the three hooks: `enemyFell` at home, a scout's carried fall,
   and a hero's fall filed for every hostile faction whose members saw it.
4. **Ids are UUIDs (0, 3.6, 4, 5.5, 7, 10.1, 11).** The watch accumulator is saved with the job, and
   `Report.recon.faces` and `FactionState.sightings` are saved; a runtime entity id changes when a chunk reloads, so a
   watch resumed after a save would count one soldier twice and a ledger would keep stale ids. Unit 6 adds a save and
   reload mid-watch, and the sabotage that keys the game side by the runtime id is caught by a new R4b GameTest (a watch
   saved and loaded midway counts a soldier once).
5. **The ten-soldier test could not be "rewritten on a recon of the same garrison" (7, 10.2 item 13).**
   `tenSoldiersAttackAGarrisonLikeTheirOwnWhereNineDo` asserts a party of 6; under the reserve 10 counted need 10. It
   becomes: a party of 6 launches against 6 counted (plan 9, two thirds 6) and waits against 7 (plan 11, two thirds 7),
   which also catches a reserve left out of the decision. Every council test that launches on a count is recalibrated.
6. **Facts corrected.** Section 14 called its answers verbatim, but answer 3 was a paraphrase: it now quotes the owner
   ("monster lairs can automatically gather energy and spend energy. Its energy can be spent on extra buildings nearby,
   more monsters or increasing detection range", from `lair_energy.md` 0), as does section 0. The logs hold three lair
   raids, all won without a loss, not two (5.5, 9, question 2). `FCGameTests` line 404 (`groveSaw.look`) went with the
   pacts in f0c837f; the looks left to rewrite are `MissingNpcGameTests` line 508 and the hero raid draft at
   `HeroGameTests` line 263 (7, R5). The urgent recon's walk is about 850 ticks for 170 blocks (5 ticks a block, as
   2.5's 1250 for 250), not 2000; the conclusion that survivors are home before the watch holds (5.5, 6). "Any member at
   home" files home sightings, but the alarm's `noticed` reads only NPCs and heroes, and the table's last row gives
   member players nothing yet: now "any member NPC or hero" (5.5).
7. **Name tags (0, 3.6).** "As a player tells soldiers apart by their name tags" is not what vanilla allows:
   `MobRenderer` shows a mob's custom name only when it is the crosshair's pick, a few blocks away, and NeoForge's
   `nametag_distance` caps any name tag at 64 blocks; the yard lies 39 to 94 blocks from the vantage (39 to 118 with a
   spyglass, whose band is 61 to 96). The rule (distinct soldiers over a minute) and its numbers stand; its reason is
   now a player keeping track of each soldier by where it walks and what it wears.
8. **Optics (0, 5.6, 9, 10.1, 11).** The owner named binoculars, so "the design pass gives them another job or drops
   them" became "what more they give is Phase 6's to set". The drone's 80 to 88% holds over the middle of the yard or 12
   blocks off it on an axis; over a grown base's corner quarter it is 68% until 48 up (`drone.py`). `SHARE_DRONE` and
   the other Phase 6 rows are placeholders: no task builds them, their unit case left 10.1 item 10, and they go into
   BALANCE.md only when built; `ReconOptic` is created by the first optic built. The spyglass is held in hand while
   watching, as a player must use one. On flat land the north side and the south of a grown base already need 21 at 72,
   so the deeper band helps only the gate sides and a start layout's south there. R12's "hill at 90" cannot fit a
   32-block test field: it is scaled with a transient sight, and a GameTest for the amethyst shards is added.
9. **Tasks (10.2, 11).** R5 is L, not M (the switch, every deletion, the old tests' rewrite and the council tests'
   recalibration in one task, since a deletion and the tests it breaks must land together). 10.2 now says which tests
   run a real scout and watch in `mobility_field` and which drive the council on the `platform` with a delivered recon,
   as `KnowledgeGameTests` does today, so the home area (about 6 blocks) leaves room for item 18's field.

**Checked and kept.** The replay's claim that no launcher had seen the target's soldiers away and alive: the timelines'
positions (nearness only, no line of sight, an upper bound) show, for the 6 launches that go, only three Stoneguard
soldiers at Grove's walls at day 6.77, dead by Grove's 7.07 launch and counted in its "losses" basis, so seen to fall;
of the waits, a Grove scout near Natsuho at day 4.01 before Grove's 4.08, which waits anyway. The verification's one
counter-attack and the corrected 7.07 party of 7 match the chat logs. Two thirds of C plus half of C, in whole soldiers,
is C for every C from 1 to 12; the counter-attack's 5, 6 and 8 and the 8, 9 and 12 with a share match
`Intel.threeToTwo`'s rounding. `LivingEntity.hasLineOfSight`'s 128 blocks and `BaseWatch.clear`'s (line 154) are as
cited. A floor for an empty yard was considered and left out: two thirds of a plan of 3 is 2, below every party. Equal
treatment, the player-like rules and the vanilla rule hold: no new rule is NPC-only, the spyglass stays vanilla and out
of any tech level, and nothing changes the hard defence the owner keeps. The scripts of this review (`sight_proxy.py`
and the rerun outputs) are in the design session's scratchpad, not kept in the repository; every number they gave is
above.
