# Scouts: villages, pillager outposts, woodland mansions; stepping aside from monsters

Commit 8abcd06 (local, not pushed), on top of 6bf098d.

The owner asked for two things:
- scouts should report villages, pillager outposts and woodland mansions;
- scouts should avoid confrontation with monsters without retreating home.

Results:
- **Build:** clean. Only the deprecation warnings that were already there remain.
- **Unit tests:** 125 passed. That is the 109 from before, plus 11 in EscapeRouteTest and 5 in StructureRulesTest.
- **GameTests:** "All 75 required tests passed". That is the 70 from before plus 5 new ones.
  - Logs: gt_scout.log, gt_scout2.log, gt_scout3.log and gt_scout_final.log, all in impl/.
- **Negative checks:**
  - gt_scout_neg.log: monsters were made to send the scout home as before, and "badly hurt" was disabled. Both new
    behaviour tests failed, each with its own message.
  - gt_scout_neg2.log: every monster was made to count as seeing the scout. The line-of-sight test failed.
  - The real code was restored after both checks.

Nothing was started or stopped on the dedicated server, and scratchpad/server was not touched.

## 1. Structure reports

### Pure rules: logic/StructureRules.java
StructureRulesTest covers this class.
- `classify(id, villageTag)`:
  - anything in the village tag is a VILLAGE;
  - `minecraft:pillager_outpost` is a PILLAGER_OUTPOST;
  - `minecraft:mansion` is a WOODLAND_MANSION;
  - anything else is null.
  - Abandoned (zombie) villages are not a structure of their own in Java Edition. They are village structures built
    from zombie pieces, so the tag covers them.
- `gap` and `inSight`: a structure is in sight when its bounding box is within **48 blocks** of the scout,
  horizontally.
- `villageName`: "minecraft:village_snowy" gives "snowy village".
- `mergedTraders(older, newer)`: the newer count, unless the newer look saw 0 traders.
- Constants:
  - `CHUNK_RADIUS = 3`;
  - `SAME_STRUCTURE = 64`. Villages, outposts and mansions are at least 8 chunks apart, so two different ones never
    fall within 64 blocks.

### World adapter: work/StructureSightings.java
`look(level, eye, skip)` runs on observe()'s 40-tick cadence and works like this:
1. It takes the **FULL-loaded chunks** within ±3 chunks of the scout, using `getChunkSource().getChunkNow`. That is
   49 lookups.
2. It reads each chunk's `getAllReferences()` and classifies the Structure through the structure registry and
   `StructureTags.VILLAGE`. The result is cached for the pass.
3. For each reference, it reads the `StructureStart` from the start chunk. It takes `getChunkNow` if that chunk is
   fully loaded. Otherwise it takes `chunkMap.getVisibleChunkIfPresent(..).getLatestChunk()`, but only when that
   chunk's persisted status is at least STRUCTURE_REFERENCES.
   - Nothing is ever loaded or generated.
   - This step matters because the scout's 3x3 ticking ticket gives FULL chunks only out to ±3. A big village's
     start chunk is often further away, but it is still in memory as a proto-chunk.
4. It keeps the structures whose bounding box is within 48 blocks of the scout. Start chunks in `skip` are left out:
   these are the structures already seen on this trip.

`report(...)` builds the Report:
- **Position:**
  - a village is reported at its **town centre**: the centre of the first piece, where the bell and meeting point are;
  - outposts and mansions are reported at the **centre of the bounding box**;
  - the height is the surface (MOTION_BLOCKING_NO_LEAVES) when that column is FULL-loaded. Otherwise it is the
    scout's own y.
- **Count and note:** only people the scout can see are counted. That means within the structure's bounds plus 8
  blocks, and within 48 blocks of the scout.
  - Village: count = trading villagers (`TraderJob::trades`).
    - Note "plains village, 4 villagers with trades (farmer, mason)".
    - Or "desert village, abandoned (2 zombie villagers), no villagers with trades".
    - Or "... no villagers with trades seen".
  - Outpost: count = pillagers. Note "3 pillagers seen".
  - Mansion: count = illagers. Note "no illagers seen".

### ScoutJob
- In observe(), structure sightings run before the trader-count observation.
  - Each structure is reported **once per trip**. The `sightings` map is keyed by start chunk. It is not saved:
    after a reload the scout sees the structure again and the reports merge.
- The existing trader observation still runs: 2 or more trading villagers within 32 blocks.
  - When their centroid lies within a village seen on this trip (its box plus 16 blocks), the report takes that
    village's position and name. Both observations then have the same position and merge.
  - Otherwise the report is placed at the centroid, as before.
- Merging on one trip (`mergeOnTrip`): a village keeps the **highest** trader count seen at once. A glance from afar
  sees fewer.

### Report and FactionState
- `Report.Kind` gets two new values, **appended**: `PILLAGER_OUTPOST("Pillager outpost", 10, false)` and
  `WOODLAND_MANSION("Woodland mansion", 12, false)`.
  - Kinds are saved by name, so old saves load unchanged.
  - Structures never go stale.
  - Experience fits between VILLAGE (8) and LAIR (12).
  - New helpers: `structure()` and `dangerousGround()`.
- `sameSubject` merge radius: **64** for the three structure kinds. HOSTILES stays at 24 and the other kinds at 40.
  - With 64, old centroid-placed VILLAGE reports merge with the new town-centre reports instead of appearing twice.
- `Report.merge(older, newer)` keeps the newer report, with one exception. A VILLAGE look that saw 0 traders keeps
  the older count and note.
  - FactionState.addReport uses it in both orders. An older delivery that saw traders still lifts a newer report
    that saw none. Otherwise the result is the same as before: the newer report wins.
- `Report.tradingVillage()` is `kind == VILLAGE && count > 0`. Two places use it:
  - **TraderJob.nearestVillage**: a structure-only or abandoned village never gets a trip;
  - **FactionDirector.villageInReach**: no trader is hired for such a village.
  - Old VILLAGE reports always had a count of 2 or more, so they keep working.

### Map and station
- **MapPayload:** `PILLAGER_OUTPOST = 8` and `WOODLAND_MANSION = 9`. STALE is still 16, and the codec is unchanged:
  the kind is still a VAR_INT.
- **MapScreen:**
  - Colours: indigo 0x5C6BC0 for outposts and dark-oak brown 0x8D6E63 for mansions.
  - Markers are size 3, like lairs, with a dark rim that marks dangerous ground.
  - The legend has the new names. It goes into two columns when the right-hand panel is at least 220 px wide, so the
    written report list keeps its height. The client is not covered by tests: check it by eye.
- **BaseInfo:**
  - The map switch has the new kinds.
  - The station screen has a new line, coloured 0xFFCC80, that lists villages nearest first. Example: "Known places:
    village 140 blocks NE (4 traders), village 260 blocks S (no traders seen), pillager outpost 210 blocks W (keep
    away)".
  - The map's written list already prints every kind's label and note.

### Using the knowledge: dangerous ground
`BaseZones.DANGER_RADIUS = 48` (Chebyshev from the reported position). `BaseZones.nearKnownDanger(f, pos, r)` checks
it, and these places use it:
- **ScoutJob.pickTarget:**
  - no sector whose centre is within 48 blocks of a known outpost or mansion, or of one seen on this trip;
  - a target that turns out to be near one is picked again;
  - escape points avoid them too.
- **MineSites.judge** (next to nearKnownVillage): no mine sites there.
- **WorkSafety.protectedWood**: no trees there. ForestScan.findTree goes through this.
- **FishingSpots zoneOk** (next to nearLair): no bobbers or stands there.
- **Woodcutter and fisherman forageTargetFor**: a forage direction whose point is dangerous is skipped. If every
  direction is dangerous, it falls back to the old one.
- **NpcNavigator.waypoint**, the easy existing hook for travellers:
  - On long trips (over 40 blocks), the 24-block waypoint loop already tries turns of 0, ±0.5, ±1, ±1.5 rad to go
    around its own walls. It now also rejects waypoints in a danger zone.
  - This only applies when neither the NPC nor the destination is inside one, so raids and escapes out of a zone
    still work.
  - This covers traders, soldiers, scouts and foragers.
- Not done: MinerJob's four diagonal survey points. Mine sites near danger are rejected anyway.

## 2. Stepping aside from monsters (ScoutJob)

### Pure rules: logic/EscapeRoute.java
EscapeRouteTest covers this class.
- **Candidates:** 16 directions × run lengths **16, 20 and 12** blocks.
- **Run rule:** the straight run never passes a monster closer than **4 blocks**. A monster already closer than 4 must
  never be approached further.
- **Clear:** a point is clear when it is at least the keep-away distance from every monster.
- **Score:** min(clearance over the keep-away, **4**) + **8** × the dot product with the direction to the target +
  **1** × the dot product with the direction away from the monsters' centroid − **6** × reversal of the last escape
  direction.
  - The target side outweighs extra clearance, so the scout makes a forward detour rather than a U-turn.
  - The reversal penalty prevents ping-pong between two groups.
- **Ranking:** clear points come first, by score. Points that are not clear follow, by margin.

### World side
**Threats** are checked every 10 ticks.
- Monsters within 20 blocks (±8 vertically) that pass `CombatRules::isHostileMonster`.
- A monster counts when it is within **6** blocks.
- Further out, it counts within its keep-away distance:
  - 12 for most monsters;
  - **16** for creepers;
  - **18** for ranged mobs (`RangedAttackMob`: skeletons, pillagers, witches, drowned).
- Beyond 6 blocks it also has to see the scout (`hasLineOfSight`). One that is hunting the scout counts up to the
  keep-away + 4 even without line of sight.
- So line of sight matters: a skeleton behind a wall or a zombie down a cave does not count.

**Escape point.** It is checked in rank order and must meet all of these:
- inside MapBounds, 8 blocks from the edge;
- not near known danger;
- in a chunk from getChunkNow;
- standable ground, meaning all of:
  - the surface if that is within 6 blocks of the scout's level, else a spot within 2 of its level;
  - passes `NpcNavigator.isSafeStandingSpot`: not on water, lava, leaves or a trunk;
  - no drop of more than 3 blocks beside it, no water or lava beside it, and all four neighbours loaded;
- a vanilla path that can reach it, with at most **4** path searches per decision.

Runs are at most 20 blocks, below the navigator's 40-block long-trip threshold. The navigator therefore never loads
waypoint chunks for an escape.

**The run** is at speed **1.3**, via `navigator().travel` directly. That way a failure does not trigger goTo's
dig-out or its 100-tick wait. A run lasts at most **160 ticks**.

When the run ends:
- if no threat is left, failedEvasions resets to 0 and the scout goes back on its route;
- otherwise that counts as one failed evasion and the scout tries another escape.

If a monster comes to stand near the escape point (worse than the margin it had when chosen, by 2), the scout picks a
new point. That also counts as a failure.

**When it retreats home** (beginReturn(true, reason); the HOSTILES group goes on record):
- health under **40%** with a threat about;
- **3** monster hits within one encounter, counted from the last-hurt timestamp, hits within 20 ticks only;
- **4** failed escapes in a row.

**Cornered** means no acceptable escape point. Then:
- with a monster within **3.5** blocks and health at least 50%, it fights back for **100 ticks**:
  - it sets its target, and `ignoresCombat()` returns false so the melee goal runs;
  - civilians do not melee under 50% anyway;
- otherwise the attempt counts as a failure and the scout keeps walking its route.

An encounter ends after **100 quiet ticks**. That resets failures, hits and the last escape direction.

**Combat interplay:**
- `ignoresCombat()` is true while OUTBOUND or RETURN when the current target is a hostile monster, unless the scout
  is defending. A monster that hits the scout is therefore not fought back; the scout steps aside instead. Enemy
  soldiers and players are fought back as before.
- New hook `Job.handlesOwnSafety()`, which ScoutJob returns true for while OUTBOUND or RETURN. CivilianFleeGoal leaves
  such a scout alone. Before, a wounded scout fell into the 50% "run to the station" goal, which uses vanilla
  navigation and cannot cross hundreds of blocks.

**On the way home:** RETURN without a retreat now also runs the danger check, with the station as the target side.
Enemy soldiers turn it into a retreat. Monsters are stepped around.

**Inside its own walls** (BaseZones.insideWalls, margin 0) there is no danger check, because the guards are there.

**Overdue:** the check now runs before the evasion step every tick. A night full of monsters still ends with the
scout heading home on time. The forward bias means each escape also makes progress.

**Records:** an evasion with 2 or more threats records a HOSTILES report at their centroid, for example "3 monsters
(zombie, skeleton) near x, y, z". That matches today's threshold of 2. `observe()` still records groups of 3 or more
within 24 blocks.

**Text the player sees:**
- statuses: "Evading 3 monsters (zombie, skeleton)", "Evading a creeper", "Slipped past 3 monsters (zombie), going
  on", "Cornered by ... - fighting back", "Looking for a way past ...";
- workplace info: "Evading ... on the way to x, ?, z ...";
- retreat reasons: "badly hurt, 2 monsters (zombie) about", "kept being hit by ...", "could not shake ...",
  "cornered by ...";
- return message: "Scout X returned and reported 7 observations (3 new); new on the map: village, pillager outpost;
  slipped past monsters 2 times."

**Save and load:** the evasion state is **reset cleanly** and is not saved: the escape, failures, hits and direction
are cleared. After a reload the scout looks around afresh. Only the trip's `evasions` count is saved, for the return
message.

### Choice: enemy faction soldiers
Enemy faction soldiers keep today's behaviour: two threats near, or one while under 60% health, means an announced
retreat home with the urgent HOSTILES report, on a fixed route. The reasons:
- soldiers are what the war council needs to hear about;
- they are as fast as the scout, so it cannot step around them the way it steps around a zombie;
- monsters near soldiers still count toward the "2 threats".

## Deviations and things found
- **Bug found and fixed: scouts' danger checks never ran.** `dangerCheck()` and `observe()` shared one
  `observeTimer`, and both incremented it every OUTBOUND tick.
  - dangerCheck only ever saw odd values, so `% 10 == 0` never happened. After a server start, scouts therefore
    **never ran their danger check while outbound**.
  - After a RETURN with an odd number of ticks the parity flipped. Then observe() stopped firing while outbound.
  - The two now have separate timers.
- **Village position:** a village is reported at its town centre (the first piece), not at the centre of its bounding
  box. A trader trades with villagers within 48 blocks of the reported spot, and the bell is where they gather. Outposts
  and mansions use the centre of the bounding box.
- **Fallback height:** when the centre column is not FULL-loaded, the reported y is the scout's own y.
- **Single monsters:** one monster also triggers stepping aside when it counts as threatening (see above).
  - Before, one monster did nothing until it attacked. Then the scout fought it, which stopped the job and its danger
    checks.
  - "Avoid confrontation" seemed to call for this.
- **README.md and BALANCE.md:** the scout, trader and map passages are updated. BALANCE.md now has the new numbers,
  and the experience list gains village 8, outpost 10 and mansion 12.

## Tests
**JUnit** (pure):
- **EscapeRouteTest** (11 tests):
  - with monsters behind, the scout keeps going toward the target;
  - with monsters ahead, it makes a forward detour that stays clear;
  - with no target, it runs away from the group;
  - between two groups, it goes on between them;
  - it does not reverse its last escape;
  - creepers get 16 blocks;
  - no run passes within 4 blocks of a monster;
  - hemmed in on all sides, there is no run at all;
  - when nothing is clear, the least bad point beats standing still, ranked by margin;
  - clear points come first;
  - margin.
- **StructureRulesTest** (5 tests): classification, the gap to a box, the 48-block sight rule, names, and trader
  merging.

**GameTests.** Each one that runs a job has its own batch, a GameTestBases test base and skyAccess. The monsters are
NoAI with a helmet against the sun, and are discarded on cleanup through the new `Handle.track`.
- **scoutStepsAsideFromZombiesAndKeepsExploring** (scout_evade):
  - a scout heading for a target 400 blocks south, with 3 zombies 3-4 blocks away;
  - it must never enter RETURN or retreat, and must never target a zombie;
  - it must end up more than 12 blocks from them, and more than 6 further than it started;
  - log: "stepped aside 1 time(s) by tick 63: 4.1 -> 17.3 blocks".
- **badlyHurtScoutRetreatsFromAMonster** (scout_hurt): at 35% health with one zombie, it never steps aside; it
  retreats with "badly hurt" as the reason.
- **enemySoldiersStillSendAScoutHome** (scout_soldiers): two NoAI Ember swordsmen nearby lead to a retreat, as before.
- **scoutWalksOnPastAMonsterThatCannotSeeIt** (scout_sight): a skeleton 9-14 blocks away behind a stone wall. The
  scout walks the length of the wall without stepping aside.
- **structureReportsClassifyAndMerge** (static). Structures cannot be generated in the flat test world, so it checks:
  - through the real registry, that the five villages, the outpost and the mansion are recognised, and that
    stronghold, mineshaft, ancient city and trial chambers are not;
  - village merging, both on a trip and across deliveries in any order: one village, 4 traders kept;
  - that an abandoned village is not a trading village;
  - that outpost reports merge and dangerous ground is 48 around outposts and mansions, but not villages;
  - that the new kinds survive save/load and never go stale;
  - that the map kinds are distinct and below STALE.

Real detection of structures in a real world (loaded-chunk references, start chunks, positions, counts) is left to
the campaign check.

## What to look for in a live campaign
- **Structure reports on the map:**
  - "Village" markers with notes such as "plains village, N villagers with trades (...)" and "... no villagers with
    trades seen";
  - indigo "Pillager outpost" and brown "Woodland mansion" markers with a dark rim;
  - and the "Known places: ..." line in the station info.
- **One marker per village**, not one at the bell plus one where the villagers stood. Old saves may still hold a
  centroid report: it should merge (within 64 blocks) the next time a scout passes.
- **Distances and positions:**
  - a village's marker should sit at its bell;
  - an outpost's marker should be near the tower;
  - for a village seen from its edge, the y may be the scout's own y.
- **Trader:** only plans trips to villages with a count above 0. Abandoned or structure-only villages give "No village
  with traders reported".
- **Scouts at night:** look for "Evading N monsters (...)" statuses and "slipped past monsters N times" in the return
  messages.
  - Retreats from monsters should be rare and give a reason: badly hurt, kept being hit, could not shake.
  - Watch for scouts that oscillate: many evasions with little progress. Also watch that overdue scouts still come home.
- **No new chunk generation from scouts' escapes:** escapes stay inside loaded chunks. Waypoint detours around danger
  zones use the normal waypoint loading, as before.
- **After an outpost is known:**
  - no mines, felled trees, fishing spots or exploration targets within 48 blocks of it;
  - long trips curve around it.
- **Danger checks now actually run** for outbound scouts (the timer bug). Expect more HOSTILES reports than before,
  and retreats from enemy soldiers on the way out.

## Review (second pass, separate commit on top of 8abcd06)

What was checked against the code and the decompiled 1.21.1 sources:
- **Structure API:** `ChunkAccess.getAllReferences` / `getStartForStructure`, `ServerChunkCache.getChunkNow` (FULL only,
  server thread), `ChunkMap.getVisibleChunkIfPresent` + `GenerationChunkHolder.getLatestChunk` (never loads),
  `StructureTags.VILLAGE`, `minecraft:pillager_outpost`, `minecraft:mansion`. The jigsaw start piece is added first,
  so `pieces.get(0)` really is a village's town centre. `ChunkGenerator.createReferences` only puts a reference into
  the chunks that the start's (adjusted) box touches, so scanning the FULL chunks within 3 of the scout finds every
  structure whose bounds come within 48 blocks.
- **Once per trip:** the `sightings` map, keyed by start chunk, is the skip set. It is cleared only at departure. It
  is not saved; after a reload the reports merge.
- **Start chunk not loaded:** the start chunk is never more than 8 chunks from a chunk that the structure reaches. The
  scout's 3x3 ticking ticket keeps chunks FULL out to 3 and in memory about 10 further. So the start is in memory
  whenever a reference is seen. If it is not, the structure is looked at again on the next round. A fallback without
  the start (no bounds, no town centre) was judged not worth it.
- **Traders:** `nearestVillage` and `villageInReach` use `tradingVillage()` (count > 0). Merging keeps the traders.
  The map, legend and station line show the new kinds. The codec is unchanged.
- **Save/load:** old saves have no `evasions` (it reads as 0) and no new kinds. Evasion state is reset on load.
- **Enemy soldiers:** they still cause the announced retreat, as documented.

Fixed:
1. **(major) Endless evading on the way home.** In RETURN there is no overdue check. Every escape that "worked"
   reset `failedEvasions`. A monster that keeps standing on the way home, or a stream of monsters at night, could keep
   a scout stepping aside forever.
   - Now 10 escapes in one encounter make it retreat home: "could not get past ...". An encounter still ends after
     100 quiet ticks.
2. **(major) Known places could be forgotten.** `FactionState.addReport` capped knowledge at 160 by dropping the
   oldest report, whatever its kind. After enough trips full of ore and hostile groups, a known pillager outpost was
   forgotten, and miners or woodcutters could work beside it again. Villages were forgotten the same way, and so
   were trader trips.
   - Now the oldest report that is not a village, outpost or mansion goes first.
   - A static GameTest floods 180 ore reports and checks that all 4 places survive.
3. **(minor) Travel detours could leave the map or break the walls rule** (`NpcNavigator.waypoint`).
   - A turned waypoint that went around dangerous ground could lie off the campaign map. Walking there generates
     its chunk.
   - When no turn kept clear of the danger, the last turn tried (-1.5 rad) was used, even if it broke the rule about
     going around the NPC's own walls.
   - Now turned waypoints must be on the map. Failing that, the walls-clear waypoint that stays farthest from the
     danger is used (`BaseZones.dangerGap`, which `nearKnownDanger` now uses too).
4. **(minor) "In sight" was 60 blocks, not 48, for villages and outposts.** The StructureStart's box is inflated by
   12 for structures that reshape terrain.
   - Sight, the count area, `villageAround` and the outpost/mansion report position now use the built bounds, which
     is the union of the pieces (`StructureSightings.builtBounds`).
5. **(minor) Escape points right at the keep-away distance.** A point was "clear" at margin 0, but the scout stops
   within 1.5 blocks of it. It often stood just inside the keep-away distance, so the escape counted as failed, and
   four of those mean a retreat.
   - Clear now needs 2 blocks beyond the keep-away distance (`EscapeRoute.CLEAR_MARGIN`).
6. **(minor) Monsters underneath.** A monster within 6 blocks counted even when it was in a cave right under the
   scout's feet.
   - Now it must be within 3 blocks of the scout's level, or in sight.
7. **(minor) Accepting a start chunk mid-generation.** It required STRUCTURE_REFERENCES. Starts are final once
   STRUCTURE_STARTS is done, so that is now accepted.
8. **(minor) Text:**
   - The return message said "slipped past monsters N times", but it counted escapes started. It now reads
     "stepped aside from monsters N times".
   - "new on the map" lists by kind with counts ("2 villages, pillager outpost") instead of repeating.
   - The station's "Known places" line names at most 6 places ("... and N more (see the map)").

Tests:
- **New GameTest scoutSeesStructuresThroughLoadedChunksOnly** (default batch, one tick). It writes a desert village
  start, an outpost start in the next chunk, and a reference to a far, unloaded start chunk into the test's own loaded
  chunks, then restores them in a `finally`. It checks:
  - the village is found through the reference and named "desert village", reported at its first piece's centre;
  - the report counts 0 villagers with trades (not a trading village), then 2 after spawning a farmer and a librarian,
    and both looks are the same subject;
  - the village is skipped once seen on this trip;
  - the outpost is not in sight from 15 blocks west (its adjusted box would be, its pieces are not), and is in sight
    from 30 west, at the middle of its bounds;
  - the far start chunk is never loaded.
  This covers detection end to end except real world generation.
- **scoutStepsAsideFromZombiesAndKeepsExploring** now also requires the scout to walk on 8 more blocks toward its
  target after stepping aside, while still OUTBOUND and not evading.
- **structureReportsClassifyAndMerge** checks the report cap keeps places.
- **EscapeRouteTest:** a new test for the clearance margin (12 tests now).
- **Sabotage run** (gt_scout_rneg.log): three regressions were planted, and each was caught by its own test:
  - the start box used for sight (detection test);
  - FIFO pruning (static test);
  - the scout pausing after an escape (evade test, timeout).
  The code was restored afterwards.
- **Results:** unit 126/126; GameTests "All 76 required tests passed" (gt_scout_r2.log, gt_scout_rfinal.log).

Left as is (noted):
- **Count across trips:** a village keeps its last positive trader count, even if later looks all see 0 (for example
  after a zombie siege). The trader goes, finds no one and comes home.
- **Same start chunk:** sightings are keyed by start chunk. Vanilla never starts a village and an outpost or mansion
  in the same chunk; a data pack that did would hide the second.
- **Unchanged from the first pass:**
  - Miners' diagonal survey points do not check dangerous ground.
  - The scout does not path around an outpost it found on this trip; it is not yet in the faction's knowledge.
  - The evasion cap and hits counter are not covered by a GameTest. They need many real escapes.

Live campaign, also watch for:
- "could not get past" retreats (the new cap): they should be rare;
- scouts reporting villages only when within about 48 blocks of the houses;
- long campaigns keeping their outposts on the map.
