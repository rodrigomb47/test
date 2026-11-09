# Worker redesign — final design spec

> Written before implementation by a design panel (three designs, three judges, one synthesis), at commit f90ec43.
> The implementation follows it with the deviations recorded in notes/ (step1–6, fixes). Where the code and this
> spec disagree, the code, README.md and BALANCE.md are right. File:line references are against f90ec43.

## Overview

FINAL IMPLEMENTATION SPEC: free-form mines, fenceless woodland, real-water fishing.

BASIS. This spec starts from the 'robust' design, which scored highest (22). It fixes every factual error the judges found and adds the strongest ideas from 'player' and 'economy'. I checked it against src/main/java/com/factionclash at commit f90ec43 and against the vanilla 1.21.1 sources in the scratchpad (mcsrc, nfsrc).

WHAT THE OWNER GETS
1. 'Miners can dig down any place to create their mining route'.
- No mine is built with the base any more. A miner without a mine chooses its own spot: flat, dry, solid ground 32-64 blocks from the base centre, with tunnels pointing away from home. Old saves keep their hut mine.
- It looks first in the chunks that are already loaded. If that finds nothing, it walks out to 4 survey points. It then digs its own 2x2 spiral staircase from the surface (the existing SpiralMine geometry).
- The 'mining route' is that staircase plus a branch mine on every level it opens.
2. 'They will go layers down as they see fit'. There are no tier payments. A pure planner, logic/MinePlanner, picks the layer from:
- what the storehouse lacks (iron, diamonds, fuel, gold, and lapis when there is an enchanting table);
- the pickaxe in hand (wood: coal; stone: iron and lapis; iron or better: gold and the deep diamond/redstone layer);
- vanilla 1.21.1 ore bands: iron y=16, lapis y=0, gold y=-16 (absolute anchors, as vanilla OrePlacements), the deep layer at minBuildHeight+8 (y=-56, the floor the current code already uses), coal 6 below the surface.
Each level keeps its own progress. The miner finishes a level before it switches. In order of preference it reuses, lengthens or opens levels, digs deeper, rests the mine until it has a better pickaxe, or retires the mine and finds a new site.
3. 'Woodcutters will no longer have a fence, they can cut and replant any tree as they like'.
- The fence, the plot grid and the plot upgrades are gone. Old fences are taken down into storage.
- The woodcutter fells any natural tree within 64 blocks of the base centre. A tree counts as natural when it has non-persistent leaves and stands on #dirt, outside every base's walls, other factions' land and known villages, and touches no structure.
- It replants the same species where the tree stood (dark oak only as a 2x2), from a persisted replant queue. It bone-meals its own saplings. It keeps 8 grove saplings growing in the base's force-ticked belt so that treeless biomes still get wood.
4. 'The fisherman is using the water from the farm to fish, it shouldn't be like that'.
- The fishing spot is moved off the farm channel and re-searched on real water: a body of at least 25 surface blocks, at least 2 deep, under open sky, 28 or more blocks from every base centre, with no farmland within 5 blocks.
- The farm channel is left as it is.
- With no such water, the fisherman forages, gives up, is retrained, and the base does not hire one again for 2 days.
- Vanilla treasure drops in open water, exactly as for a player.

PRINCIPLES
- Keep the proven mechanisms: the spiral, branch plans, preSeal/sealShaft, walkInMine, DigOut (unchanged), BlockWork.Breaker, the vanilla loot tables and the existing wild-tree code path. Change where and when they run.
- One digging predicate (work/WorkSafety.mayDig) and one zone definition (campaign/BaseZones). These are based on the walls box (HALF), not on MIN/MAX, which settles contradiction 3.
- All decisions are pure and unit-tested: MinePlanner, LegacyMine, MineSiteRules, ForestRules, TreeShape, FishingWater, Zones. This is required because GameTests run in a FLAT world near y=-60, where no layer is legal.
- Every job idles when base()==null.
- The BaseState MIN/MAX numbers stay exactly as they are (cx-58..cx+78, cz±25), for old and new bases. The forced, randomly ticking chunk area is therefore unchanged, and becomes the 'work belt' where the grove grows. contains(), deepInside(), the hauler, scouts, raids and ChunkLoading see the same boxes as today, so there is no ticket migration and no leak (contradiction 4).
- Old campaigns migrate in WorkplaceState.load, keyed on the new NBT keys being absent. Nothing in a saved enum is removed or renamed.

FACTUAL ERRORS FIXED (all verified)
(1) muddy_mangrove_roots IS in #dirt (VanillaBlockTagsProvider.java:229-240). Mangroves are excluded by species, and because their lowest log stands on mangrove_roots, which is not #dirt.
(2) The warden is not a reason to avoid the deep dark: sculk shriekers react only to ServerPlayer sources. The deep_dark refusal stays only to leave ancient cities intact.
(3) The ore bands are not all relative to minBuildHeight. Iron (triangle -24..56), lapis (-32..32) and gold (-64..32) use absolute anchors. Only diamond and redstone_lower use aboveBottom. The planner uses absolute targets, plus minY+8 for the deep layer.
(4) For minY 0 and surface 120 the deep layer lands at y=9, not y=8. Tests assert y in [minY+8, minY+10].
(5) StructureManager.getStructureWithPieceAt calls level.getChunk(STRUCTURE_REFERENCES/STARTS), so it is not a loaded-only read. It is not used. Villages are protected through scout reports (Report.Kind.VILLAGE), the tree structure-contact abort, and the 'no built blocks near the mine mouth' rule.
(6) At runtime WORLD_SURFACE counts grass, flowers and snow (NOT_AIR), while OCEAN_FLOOR counts only blocksMotion. The runtime water test is MOTION_BLOCKING > OCEAN_FLOOR. Ground height uses MOTION_BLOCKING_NO_LEAVES. The _WG heightmaps stay only in SiteFinder, where chunks are at SURFACE status.
(7) Reach is measured from the eyes (feet+1.62) to the block centre (BlockWork.inReach). Standing in the stump column, the highest reachable log is stump+8: 8.5-1.62 = 6.88, within 7.5. A log at +9 is 7.88 away and out of reach. From CHOP's 4.0 stop, about 3.8 blocks out, only +7 is safe. Hence: a closer approach, a final pass from the stump column, and a cap at +8, with the tree abandoned before the first cut if it is taller.
(8) The economy design's deep layer was impossible for half of all surface heights. stepFor falls back to landingAtOrAbove, and a unit test covers every surface height.
(9) A 1-block collar does not keep mobs out: they step up 1 block. No collar is built.
(10) Lily pads are not motion-blocking, so MOTION_BLOCKING_NO_LEAVES-1 is already the water. There is no 'top-2' case.
(11) The day-13 dump does not show iron as the binding resource: Ember had raw_iron 44. The planner counts raw ore plus ingots (Logistics.measure IRON).
(12) The last tunnel cell is at f = 2+40(r+1), that is 202 at r=4.
(13) findBlocked treats every non-air, non-torch cell as blocked (MinerJob.passable), so snow layers on open steps get cleared.
(14) robust's step-2 interim loop is avoided: mayDig reaches extend() only in step 3, together with shaftLimit, so the old considerGoingDeeper cannot undo a stopped descent.

GRAFTED IDEAS
- From player:
  - holderRoleMatches, which frees records held by retrained NPCs in old saves;
  - the full corridor reserved at site time (maxRounds that fit), with robust's corridorClear as a backstop;
  - survey walks;
  - persisted per-role barrenUntil;
  - the TreeShape same-species flood with a structure-contact abort;
  - vanilla rain bite bonus;
  - the night rule;
  - REST for mines that need a better pickaxe;
  - GameTest isolation (own batches, registered off-centre fake bases, clean-up).
- From economy:
  - releasing workplaces inside FactionNpc.role() (one choke point; FactionNpc.load sets DATA_ROLE directly, so it is safe);
  - CaveWork.ownWorkings by corridor rectangles;
  - transient test radius overrides on BaseState;
  - the hiring gate retried every 2 days.
- From robust:
  - explicit exits for every stuck state;
  - unbounded nearStairs and inStairwell;
  - the throttled pickaxe-tier re-check;
  - deepEnough;
  - the step order.

DELIBERATELY OUT OF SCOPE (with reasons)
- Nerd-poling: it would turn DigOut's recovery teleport into routine work, and strand NPCs on dirt pillars that DigOut.perched does not see.
- Headframes and covers.
- Per-sapling forestry tickets: the belt already ticks.
- A MineIndex: record counts stay small, and distance pre-checks are enough.
- MapPayload marker kinds: no protocol change.
- Hiring for ore shortages.
- Junk limits for copper.
- A water bonus in base siting.

## Mining

A. RECORDS, OWNERSHIP AND CAPS
- Each mine site is one WorkplaceState of Type.MINE. The enum name is unchanged, the label becomes 'Mine', and maxTier becomes 1.
  - New mines are created at runtime by the miner in the SITE state, via CampaignData.addWorkplace.
  - Legacy hut mines are migrated (see saveCompat).
- Active means !workedOut && restTier == 0. A miner works at most one mine:
  - MinerJob.wp() = Workplaces.claim(npc, MINE, w -> !w.workedOut && (w.restTier == 0 || w.restTier <= pickTier())).
  - The own-first rule now honours the predicate.
  - Claiming a resting mine sets restTier = 0.
- Base cap: FCConfig workplaces.maxOpenMines, default 3, counts active MINE records of the base (MineSites.openMines).
- Worked-out and resting records are kept forever:
  - DigOut, noticeOres and WorkSafety keep protecting their stairs.
  - Their reserved corridors keep blocking overlapping sites.
  - BaseInfo summarises them on one line.
- Only two helpers write these states:
  - MineSites.retire(level, data, wp, why) is the only writer of workedOut for MINE: workedOut = true, assigned = null, note = why, setDirty, faction message 'Mine #id is worked out (why)'.
  - MineSites.rest(data, wp, tier) sets restTier = tier and assigned = null, and sends 'Mine #id rests until an iron pickaxe'.

B. DATA MODEL (WorkplaceState; MINE-only NBT block written when type == MINE)
NBT keys:
- 'mineV' (int 1): the new format. If absent, the record is migrated (saveCompat).
- 'hut' (boolean): a legacy hut mine. It selects the hut entrance and the leaveMine hut box.
- 'bottom' (int): the deepest spiral step dug. Steps 1..bottom are all dug.
- 'cur' (int): index of the level being worked; -1 means none planned.
- 'shaftLimit' (int): 0 means no limit, otherwise the shaft may not go below this step.
- 'maxRounds' (int, 0..4): lengthening rounds reserved for this site's corridor.
- 'restTier' (int): 0 means not resting, otherwise the pickaxe tier needed to come back.
- 'opened' (long).
- 'workedOut' and 'note': now saved for MINE too.
- 'levels': a ListTag of compounds {step int, prog int, rounds int, band int (MinePlanner.Band ordinal, -1 = legacy, derived with bandAt), cut boolean}. This replaces parallel arrays.
Other fields:
- upgradeStep keeps its meaning: an index into SpiralMine.spiralPlan(bottom, levels[cur].step) while the shaft is dug down to the current level (step > bottom); otherwise -1.
- tier stays 1 in the record (the JSON dump writes tier = levels.size()).
- subLevel, reach, revisit and progress are only read by the migration; for MINE, progress is saved as 0.
In memory:
- public final List<MineLevel> levels.
- public static final class MineLevel {int step, progress, rounds, band; boolean cut}, with no Minecraft types.
- On load, levels with step <= 0 are dropped and cur is clamped to -1 when out of range.

C. GEOMETRY (structure/MineLayout.java; SpiralMine geometry unchanged)
- Kept: local, forwardOf, sideOf, stand, spiralPos, inShaft, stepAt, wallTorchFor, and HUT_BACK/HUT_FRONT (legacy only).
- Kept unbounded in depth: nearStairs and inStairwell. Stairs dug during a long descent stay protected, and npcClimbsABrokenMineStairwell needs no change.
- New or changed:
  - MineLevel current(wp)
  - BlockPos landing(wp, MineLevel lv) = stand(wp, lv.step)
  - int landingSide(MineLevel lv) = SpiralMine.cell(lv.step)[1]
  - List<Cell> tunnelPlan(MineLevel lv) = SpiralMine.cachedPlan(landingSide(lv), lv.rounds)
  - BlockPos tunnelPos(wp, lv, cell) = local(c.f, c.s, -lv.step + c.dy)
  - MineLevel tunnelLevelAt(wp, p) and nearLevelAt(wp, p): iterate levels with step <= bottom, y = origin.y - step, same tolerances as today.
  - int shaftSteps(wp) = upgrading ? max(bottom, current.step) : bottom
  - BlockPos entrance(wp) = hut ? local(-3,0,0) : local(0,0,0). The latter is the origin, checked standable at site choice.
  - boolean inMine(wp, p) = p.y < origin.y-1 && p.y >= origin.y - shaftSteps - 4 && forward in [-2, MineSiteRules.corridorEnd(maxRounds)] && side in [-11, 12]
  - boolean inHut(wp, p) = hut && today's hut box (f -1..5, |s| <= 2, within 7).
- Deleted:
  - level(wp) returning an int, deepest, landingSteps(wp,int), landing(wp,int), landingSide(wp,int), tunnelPlan(wp), tunnelPos(wp,c);
  - upgradePickTier and UPGRADE_TORCHES_PER_8_STEPS (step 3);
  - buildInitial (step 4).

D. SITE CHOICE (MinerJob state SITE; world adapter work/MineSites.java; pure logic/MineSiteRules.java)
When: only from run(), and only when all of these hold:
- base() != null;
- the miner holds no active mine, and none is claimable (active or resting at its tier);
- caveWork() returned null;
- openMines < maxOpenMines;
- now >= nextSiteSearch;
- it has a pickaxe; otherwise it goes on the normal STORAGE trip.
workplaceInfo() and keep() never create anything; they use held().
Phases (transient):
1. LEAVE: if MineSites.mineAt(data, base, pos) != null, leaveMine(it), with a 1200-tick budget.
2. UNLOAD: if hasCargo(), atStorage and unloadNow.
3. SEARCH: a MineSites.Search over MineSiteRules.candidates(cx, cz, half+8, R, 4), where half = BaseZones.half(base) (24) and R = BaseZones.workRadius(base, mineSiteRadius=64).
   - That is a 4-block grid in the Chebyshev ring 32..64, about 780 points.
   - 64 points per tick (about 13 ticks), status 'Looking for a place to dig a mine'.
   - It reads only columns where level.hasChunkAt holds, inside MapBounds (margin 16).
4. SURVEY: when nothing was found, the miner walks to survey point i (0..3) = MapBounds.clamp(centre + (44,-44) / (44,44) / (-44,44) / (-44,-44), margin 16).
   - goTo reach 8, with a 2400-tick limit.
   - Its 3x3 traveller ticket loads that corner, then SEARCH runs again.
5. FAIL: after 4 points, or at once when every evaluated candidate (at least 8) failed only the depth rule:
   - nextSiteSearch = now + 6000;
   - markIdle('no place to dig a mine') or markIdle('the ground here is too shallow for a mine');
   - waitFor(200).

MineSites.evaluate(level, data, base, x, z) returns Site(origin, facing, maxRounds, score), or null. y = MOTION_BLOCKING_NO_LEAVES at (x, z), and O = (x, y, z) is the feet position.
(a) Depth: MinePlanner.deepEnough(y, minY), i.e. y - (minY+8) >= 6. Flat GameTest worlds always fail here.
(b) Collar, per facing: f 0..3 and s -1..2 (16 columns). For every column:
   - its MOTION_BLOCKING_NO_LEAVES is within ±1 of y;
   - the ground block is Terrain.isSolidGround with no fluid;
   - the ground is not leaves, a log, a sapling, farmland, dirt_path or ice, has no block entity, and is not in any planIndex;
   - MOTION_BLOCKING == MOTION_BLOCKING_NO_LEAVES (no canopy over the hole).
   The shaft cells f 1..2, s 0..1 at y and y+1 are CaveWork.open, and O is CaveWork.standable.
(c) Surface fluid, per facing: over f -3..6, s -4..5, every 2nd column, MOTION_BLOCKING > OCEAN_FLOOR means water or lava, and the facing is rejected.
(c2) Built blocks, per facing: over the same box, ground-1..ground+2, any WorkSafety.structural block or block entity rejects the facing. This protects unreported villages, ruins and player builds.
(d) Zones:
   - BaseZones.outsideAllWalls(data, O, 8): Chebyshev > half+8 from every settlement centre, the own one included;
   - !BaseZones.inForeignArea(data, base.id, O, 16);
   - no Report.Kind.VILLAGE of the faction within 48;
   - !WorkSafety.reserved(data, base, O, MINE_SITE).
(e) Chebyshev >= 24 from every MINE origin of every base, worked-out ones included.
(f) Not within 8 of the job's badSites.
(g) Facings come from MineSiteRules.facingsAwayFrom(x-cx, z-cz): the dominant outward one first, then the two perpendicular ones, never toward the base. For each facing:
   - r = MineSiteRules.maxRoundsThatFit(ox, oz, fx, fz, MAX_REACH=4, obstacles, map), with these obstacles:
     - other mines' corridor(origin, facing, maxRounds), inflated 2;
     - every settlement walls box (half), inflated 4; the own one is included, so tunnels never run under the own walls;
     - other bases' MIN/MAX x/z boxes, inflated 8;
     - lair boxes (LairLayout.HALF), inflated 16;
     - the map square (MapBounds, margin 8).
   - If r < 0 the facing is rejected.
   - penalty = MineSites.corridorPenalty, sampled every 6 blocks over f 3..42, s -10..11:
     - +6 where MOTION_BLOCKING > OCEAN_FLOOR (water over the tunnels);
     - +3 where MOTION_BLOCKING_NO_LEAVES < y-3 (the ground dips toward the first level);
     - 0 for an unloaded sample.
   - A facing with penalty >= 30 is rejected. Keep the lowest penalty; on a tie, the larger r.
Score: MineSiteRules.score(gateDistance, penalty, collarRelief, loose) = gateDistance + 2·penalty + 4·relief + (loose ? 10 : 0). Lowest wins.
- gateDistance is the horizontal distance from O to the nearer of gate_w/gate_e (the centre if there are no gates).
- relief is the collar's max-min height.
- loose means some collar ground block is a FallingBlock. Sand is allowed, because preSeal and sealShaft plug it.
Create:
- new WorkplaceState{type MINE, mineV 1, origin O, facing, hut false, maxRounds r, bottom 0, cur -1, opened now, assigned npc}, then data.addWorkplace(base, w).
- Run plan(w) at once.
- Faction message '<name> opens a mine at x,y,z (tunnels run <dir>)'.
- State TRAVEL.
badSites (job NBT 'badSites', long[] of at most 32, FIFO) gets the origin of every mine retired as unreachable or unsafe.

E. SHAFT, ENTRANCE, OPENING
- The shaft is the existing 2x2 spiral, dug from step 0 by extend() with from = bottom = 0. extend() has no sky check; only mine() at MinerJob.java:745 and approach/bridgeNearbyHoles have one.
- sealShaft(wp, dug, steps) skips neighbours with y >= origin.y - (hut ? 1 : 0). For hutless mines the ground ring at origin.y-1 around steps 1..3 is plugged where it is air, fluid or a FallingBlock (the gap at MinerJob.java:1001). Open air at y >= origin.y is still skipped.
- preSeal is unchanged: every fluid neighbour is plugged before a cell opens.
- No hut, roof, collar or trapdoor. A 1-high ring would not stop mobs, which step up 1 block, and fence gates would block NPC paths. Instead:
  - on the first EXTEND tick of a fresh site (bottom == 0 && upgradeStep == 0), the miner places a standing torch from its backpack at local(0,-1,0), if that cell is air on a sturdy floor;
  - wall torches every 8 steps stay as they are.
- A fall into the spiral is at most 3 blocks (no damage), and the spiral can be walked out of.
- leaveMine:
  - underground = MineLayout.inMine(wp,pos) || MineLayout.inHut(wp,pos). This replaces the 3D closerThan(origin, 64), which missed levels more than 64 blocks deep.
  - It walks to entrance(wp) through walkInMine.
  - After a 1200-tick budget it returns true, and plain navigation or DigOut takes over.
- walkInMine:
  - New first leg: at the surface (stepAt 0, depth <= 1) with destStep > 0 and more than 3 blocks from stand(0), goTo(stand(0), 1.2, 'the mine entrance') first.
  - The 'beside the shaft' branch uses shaftSteps(wp).
  - Stair clearing uses preSeal(q, shaftSteps).
- Errands: Job.survival calls beforeErrand() before both of its atStorage() calls. MinerJob overrides it:
  - w = MineSites.mineAt(data, base, npc.blockPosition()); return w == null || leaveMine(w);
  - Hunger and tool trips from y=-56 therefore climb the spiral leg by leg, even out of a mine that was just retired.

F. LAYER CHOICE (new pure logic/MinePlanner.java; no Minecraft imports)
Bands and constants:
- enum Band(pickTier), append-only because the ordinal is saved: COAL(0), IRON(1), LAPIS(1), GOLD(2), DEEP(2). DEEP stands for diamonds and redstone. Copper and redstone have no consumer in the mod, so neither is a target.
- Constants: MIN_DEPTH 6, MIN_SPACING 6, REUSE_WITHIN 5, MAX_REACH 4, floorY(minY) = minY + 8.
Functions:
- static int targetY(Band b, int surfaceY, int minY): COAL surfaceY-6; IRON 16; LAPIS 0; GOLD -16; DEEP minY+8.
- static boolean deepEnough(int surfaceY, int minY) = surfaceY - floorY(minY) >= 6.
- static int stepFor(Band b, int surfaceY, int minY):
  - maxN = surfaceY - floorY(minY); return -1 if maxN < 6;
  - n = clamp(surfaceY - targetY, 6, maxN);
  - k = SpiralMine.landingAtOrBelow(n), which is made public; if k > maxN then k = SpiralMine.landingAtOrAbove(n), which is new;
  - return k. It is always in [6, maxN] with (k-1)%4 in {1,2}.
- static Band bandAt(int y, int surfaceY, int minY): the nearest targetY; ties go to the shallower band. Used for legacy levels.
- record Stock(int iron, int diamonds, int gold, int lapis, int fuel, boolean enchantingTable, boolean fuelOrWoodShort).
  - iron = Logistics.measure IRON (ingots + raw);
  - fuel = measure FUEL;
  - diamonds = measure DIAMOND;
  - gold = raw_gold + gold_ingot;
  - lapis = lapis_lazuli;
  - table = the BaseState.ENCHANTING point holds an enchanting table;
  - fuelOrWoodShort = base.shortages contains FUEL or WOOD.
- static List<Band> wanted(Stock s): in order, deduplicated:
  1. IRON if iron < 8
  2. DEEP if diamonds < 8
  3. IRON if iron < 32
  4. COAL if fuel < 32 || fuelOrWoodShort
  5. GOLD if gold < 16
  6. LAPIS if table && lapis < 32
- record Level(int step, int progress, int rounds, boolean cut, Band band), with planSize = SpiralMine.planSize(rounds).
- enum Kind {WORK, LENGTHEN, NEW_LAYER, REST, WORKED_OUT}; record Decision(Kind kind, int level, int step, Band band, int needTier).
- static Decision next(int surfaceY, int minY, List<Level> levels, int current, int bottom, int shaftLimit, int pickTier, List<Band> wanted, int maxRounds):
  - maxStep = shaftLimit > 0 ? min(shaftLimit, maxN) : maxN.
  - (1) Stickiness: if current >= 0, the level is not cut, progress < planSize, band.pickTier <= pickTier and step <= maxStep, return WORK(current). Stock fluctuations therefore never make it thrash.
  - (2) For each band in wanted, then DEFAULT = IRON, DEEP, GOLD, LAPIS, COAL (deduplicated), skipping bands with band.pickTier > pickTier:
    - s = stepFor; skip when s < 0 or s > maxStep.
    - L = the nearest level with |step-s| <= 5.
    - If L is cut, go to the next band.
    - If L is unfinished, return WORK(L).
    - If L is finished and rounds < maxRounds, return LENGTHEN(L).
    - If L is finished and maxed, go to the next band.
    - With no L (every level is then at least 6 steps away), return NEW_LAYER(s, band).
  - (3) If pickTier < 2: for t = pickTier+1 up to 2, if next(... pickTier=t ...) is not WORKED_OUT or REST, return REST(needTier=t).
  - (4) WORKED_OUT.
Worked examples:
- Surface 70, minY -64: COAL 6 (y=64), IRON 54 (y=16), LAPIS 70 (y=0), GOLD 86 (y=-16), DEEP 126 (y=-56).
- Surface 66: IRON 50, LAPIS 66, GOLD 82, DEEP 122.
- Surface 68: DEEP 123 (y=-55).
- minY 0, surface 120: DEEP 111 (y=9).
- Flat GameTest world (surface -60, minY -64): every band gives -1 and the result is WORKED_OUT.
pickTier = ToolKind.tierOf(tool) for a pickaxe, else -1. Wood/gold is 0, stone 1, iron 2, diamond 3. This matches BlockWork.canHarvest, and closes today's hole where floors below tier 4 had no pickaxe check.

G. DECISION CYCLE (MinerJob; states CHECK, STORAGE, CRAFT, TRAVEL, MINE, EXTEND, CLEAR, SITE)
run():
- base()==null: status 'No home base', markIdle, waitFor(200).
- cave = caveWork() != null: markBusy, caveJob.tick().
- wp = wp() == null: site().
- Otherwise: markBusy, collect(wp) (held mine only), then the state switch.
CHECK: STORAGE if there is no pickaxe, 1 free slot or fewer, or cur == -1; else TRAVEL.
STORAGE: as today (leaveMine, atStorage, unload, upgradeEquipment, junk filter, torches, seals, noSealBlocks). Then, if !upgrading, plan(wp). Then torch crafting as today, then TRAVEL.
plan(wp):
- Build Stock and wanted.
- Map levels to MinePlanner.Level, deriving the band for -1.
- d = MinePlanner.next(origin.y, minBuildHeight, ..., wp.maxRounds). Act on d:
  - WORK(i): cur = i.
  - LENGTHEN(i):
    - If MineSites.corridorClear(level, data, wp, rounds+1) (the new segment against the map, walls+4, other bases' boxes +8 and other mines' corridors +2): rounds++, cur = i, +20 NPC XP, message 'lengthens the y=Y branch mine (round r)'.
    - Otherwise: rounds = maxRounds, and plan again (at most 16 iterations).
  - NEW_LAYER(step, band):
    - Append a MineLevel{step, 0, 0, band} and set cur to it.
    - If step > bottom: upgradeStep = 0, so TRAVEL walks to stand(bottom) and then EXTEND.
    - Otherwise (a depth the spiral already passes, like today's infill): +20 XP and the message 'opens a branch mine at y=Y for <band>'.
  - REST(t): MineSites.rest, then state CHECK.
  - WORKED_OUT: MineSites.retire(..., 'every layer it can reach is dug').
TRAVEL:
- As today, using the current level.
- When upgrading: walkInMine(stand(bottom), bottom), then EXTEND.
- Unreachable site: while bottom == 0, upgrading and more than 3 blocks from stand(0), count ticks. Past 2400: retire('unreachable') and add to badSites.
- Tier check, throttled with nextTierCheck every 2400 ticks: if pickTier() < band(cur).pickTier, go to STORAGE. There, upgradeEquipment may fetch a better pick; otherwise rule (1) fails and the planner picks a level the pick can harvest. This covers Job.survival equipping a stone spare after an iron pick broke.
MINE:
- As today, but on the current MineLevel: lv.progress replaces wp.progress everywhere. The same throttled tier check applies.
- Before digging any cell, if !WorkSafety.mayDig(level, pos, base, wp):
  - main line (s == landingSide): lv.cut = true and lv.progress = plan.size();
  - branch: skip that branch side (the face-watchdog code).
- A face-watchdog cut of the main tunnel also sets lv.cut.
- When the plan is done: STORAGE, wait 100.
- Vein ores that fail mayDig are polled.
EXTEND:
- from = bottom, to = cur.step. If to <= from: upgradeStep = -1, TRAVEL.
- On completion: bottom = to, upgradeStep = -1, +20 XP, message 'Mine #id reaches y=Y for <band> (<name>)', TRAVEL.
- MineSites.abandonDescent(wp, cell) runs when:
  - the next cell fails mayDig (a protected or structure block, deep_dark, a block entity); or
  - the same upgradeStep has lasted 2400 ticks; or
  - preSeal has failed 3 times in a row while noSealBlocks.
  It does this:
  - k = stepOfCell;
  - bottom = k-1 (steps are dug top-down, so 1..k-1 are complete);
  - shaftLimit = k-1;
  - remove the current level; cur = -1; upgradeStep = -1;
  - STORAGE.
  If bottom < 6, it retires the mine ('the ground below is unsafe') and adds it to badSites.
findBlocked and CLEAR:
- Spiral cells 0..bottom.
- Dug plan cells of the current level, only when cur.step <= bottom.
- Cells failing mayDig are dropped from the queue.
walkInMine stair clearing: a stair cell failing mayDig adds to stairBlockedTicks. Past 600: retire('stairs blocked') and leave by plain navigation.
Per-level progress ends today's loss of the undug ~40% (progress was reset at MinerJob.java:871). Lengthening keeps progress valid because the longer plan starts with exactly the shorter one (SpiralMine.tunnelPlan; tested by lengtheningKeepsTheDugPart...).
XP:
- NPC: +20 per layer reached or opened, +20 per lengthening.
- Ore XP, vanilla orbs and Prospector are unchanged.
- totalOutput is unchanged.

H. CAVES
- caveWork():
  - held(CAVE), if any;
  - else null if the miner holds an active mine;
  - else null if wp() can claim an active or tier-eligible resting mine;
  - else claim(CAVE, !workedOut && !marks.isEmpty()).
  SITE runs only after this. A miner whose mine retired works a surveyed cave before opening a new site. This keeps the cave-hire intent of the existing need, and MAX_CAVE_MINERS stays 2.
- CaveMinerJob is unchanged, except WorkSafety.mayDig(level, c, base, null) at CaveMinerJob.java:341 (tunnelToward: a refused cell ends the attempt, like an unbreakable one) and at :445 (prospecting: it ends that direction).
- CaveWork.ownWorkings(data, b, pos) is true when:
  - b.contains(pos, 16); or
  - for some MINE of b: forward in [-4, corridorEnd(maxRounds)+4], side in [-15, 16] and y in [origin.y - max(bottom,6) - 6, origin.y + 2].
  This replaces '64 blocks around every MINE origin', which would blind scouts and explorers more and more as worked-out mines pile up.

I. PROTECTION: WorkSafety.mayDig (rules in shared) is called in mine(), the vein, extend(), clear(), walkInMine stair clearing and CaveMinerJob. DigOut.isProtected and climbStairs stay private and unchanged.

J. RETRAINING AND RECRUITMENT
- markIdle only in SITE with no site, and when maxOpenMines is reached with nothing claimable ('every mine site of the base is taken'). Otherwise markBusy.
- FactionDirector.needs:
  - 'an open mine has no miner': MINE with !workedOut && restTier == 0 && Workplaces.unworked && living(MINER) > 0. It replaces 'the constructed mine has no miner'.
  - 'replacing the missing miner' is skipped while now < base.barrenUntil(MINER).
  - The cave need is unchanged.
  - No hiring for ore shortages.

K. BLACKSMITH
- Delete order 1, the 'mine upgrade payment' (BlacksmithJob.java:218-241), together with MineLayout.upgradePickTier.
- Order 2 (miner pick upgrades: stone, then iron, then diamond) is unchanged and now drives layer access.
- The crew comes from the roster (see shared).

L. DELETED
- MinerJob: exhausted, startLengthening, nextLengthening, blockedDeeper, considerGoingDeeper, MAX_REACH (moved to MinePlanner), its own idleSince/idleFor (moved to Job), and the tier/subLevel text in extend and workplaceInfo.
- MineLayout: the methods listed in C.
- SpiralMine: TIER_TARGET_Y, MIN_Y, landingSteps, ALL_LANDINGS, regularLandings, infillLandings, isRegular, regularLevels and levelY move verbatim to logic/LegacyMine, which is used only by the migration.
- BaseBuilder: the mine pad and its skirt entry, MINE creation, the buildInitial call, and sealMine.
- BaseLayout: MINE_ORIGIN_X and the east path.
- SiteFinder: mineAreaPenalty and its call.
- BlacksmithJob: order 1.
- FactionDirector: the 'constructed mine' need.

## Woodcutting

A. THE WOODLAND RECORD
TREE_PLOT keeps its enum name; its label becomes 'Woodland' and maxTier becomes 1. There is one per base, claimed by the base's woodcutter through Workplaces.claim, as today.

TREE_PLOT-only NBT:
- 'woodV' = 1. If absent, the record is migrated (see saveCompat).
- 'planted' long[]: saplings this base's woodcutters planted, at most 64, oldest dropped first.
- 'replant': a ListTag of {p long, s String (species name), big byte, at long (time queued)}, at most 32, oldest dropped first.
- 'felled' int: the real number of trees felled. totalOutput counts every item, so it cannot serve (critique gap 18).
- 'legacyFence' boolean.
upgradeStep is always -1 and tier is 1. origin and facing are kept only for the legacy fence geometry; a new base stores centre+(-32,0,0) facing WEST.

Job NBT:
- 'v' = 2, 'state', 'spot', 'felling' (long, the stump of a tree being felled), 'big', 'badWild' (long[], at most 128, FIFO), 'forageDir', 'forageFails'.
- A tag without 'v' (an old save) loads as CHECK and its spot is dropped, so an old mid-cut wild tree is never treated as a plot tree.
- States: CHECK, STORAGE, CHOP, LEAVES, COLLECT, PLANT, BONEMEAL, DISMANTLE. The old names CRAFT and UPGRADE, and any unknown name, load as CHECK.

Guards: with base()==null or no TREE_PLOT, the status is 'No woodland' and the job waits 200 ticks, so woodcutters without a base in GameTests stay passive. workplaceInfo uses held(); keep() and leafInTheWay never call wp().

B. TREE SEARCH (new work/ForestScan.java, using pure logic/TreeShape and logic/ForestRules)
- Centre and radius: base.center (not wp.origin), radius R = BaseZones.workRadius(base, forestRadius=64), Euclidean.
- Sampling: step 2 with a rotating parity. Phase = scanCount%4 gives the offsets (0,0), (1,1), (1,0), (0,1), so every column is covered once every 4 scans. Each scan costs about 3200 columns, the same as today, and runs synchronously.
- Timing: a rescan every 600 ticks (the wildTree cache is kept), or when the cache is empty.
- Only columns where level.hasChunkAt holds, inside MapBounds, are read.

Column rule (cheap):
- top = MOTION_BLOCKING_NO_LEAVES-1 is #logs, with ForestRules.fromLog(block id) != null;
- walk down only through the same block (at most 30) to the base;
- at least 3 logs;
- base.below() is #dirt;
- top - base <= 8;
- naturalLeavesNear(top), as today (non-persistent leaves within top±2, -1..+2).
The column is also skipped when any of these holds: it is in badWild; TreeClaims.claimedByOther(base, npc); WorkSafety.protectedWood(level, data, base, base); or remoteAtNight(base).

2x2 trees: if one of the 4 squares containing the base has the same log at base.y over #dirt in all 4 columns, then big = true and the spot is moved to the square's NW (min x, min z) corner. Dark oak is always 2x2.

Score: dist(base.center) + 0.5·dist(npc). The lowest wins, so the forced belt next to the walls is cleared first and regrowth happens where chunks tick.

TreeClaims: a static ConcurrentHashMap from stump to (uuid, until). A claim lasts 1200 ticks, is refreshed every CHOP tick, and is released when the tree is done or given up. It works across factions, so two woodcutters never fell one trunk. It is not persisted; the target lives in the job NBT and is claimed again on load.

C. NATURAL TREE RULE AND PROTECTION
TreeShape.survey(Cells, x, y, z, boolean resume) is pure and returns Tree(Verdict, List<int[]> logs sorted top-down, boolean big, int topDy). The flood:
- follows only SAME_LOG: the stump's exact block, any axis. Stripped logs, wood blocks and other species never join;
- uses the same neighbours as today (dx,dz -1..1, dy 0..+1), bounded to ±7 horizontally, 0..+24 up and 96 logs;
- returns TOUCHES_STRUCTURE if any flooded log has a face neighbour of kind STRUCTURE or OTHER_LOG;
- returns TOO_TALL if any flooded log is more than MAX_FELL_DY = 8 above the stump;
- unless resume, returns TOO_SMALL with fewer than 3 logs, and NOT_NATURAL when no NATURAL_LEAVES are within 2 of the top log or the stump is not on GROUND.
The world adapter (ForestScan.cells) classifies blocks:
- STRUCTURE: any position in any non-destroyed base's planIndex; any block entity (bee nests included, so no angry bees); planks; wooden slabs, stairs, fences, fence gates, doors and trapdoors; walls; glass and panes; wool and carpets (BlockTags.WOOL_CARPETS); beds; cobblestone; bricks of every family; bookshelves; hay bales; torches, lanterns, signs and rails.
- OTHER_LOG: other #logs.
- NATURAL_LEAVES and PERSISTENT_LEAVES: #leaves, split by PERSISTENT.
- GROUND: #dirt. This includes mud and muddy_mangrove_roots.
- AIR, or OTHER: stone, vines, cocoa, mossy cobblestone boulders, snow and the rest.
This protects faction frame logs (FactionType.java:10-22), old hut corners and village houses (critique gap 3).

WorkSafety.protectedWood(level, data, own, pos) is true when:
- pos is in any planIndex;
- pos is within any settlement's walls box + 2 (Chebyshev <= half+2), the own base included. The own MIN/MAX box no longer protects anything, so old plot trees and the old mine strip are fair game;
- BaseZones.inForeignArea(data, own.id, pos, 8), that is other bases' contains(pos, 8) and lairs at +16;
- pos is outside MapBounds;
- a Report.Kind.VILLAGE of the faction is within 32.

Cuttable species: OAK (azalea trees included, since their logs are oak), BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, CHERRY. Not cut: MANGROVE (trunks on roots), huge mushrooms (not logs), nether stems, unknown modded logs, and trees taller than +8, which are left standing (see risks).

D. FELLING (CHOP)
1. Approach as today: inReach(spot, 4.0), else goTo(spot, 2.5).
   - leafInTheWay cuts only non-persistent leaves that are not in planIndex.
   - 'Overgrown' now means navigator.ticksWithoutProgress() > 100 only.
   - More than 400 walking ticks: the tree goes to badWild and its claim is released.
2. In reach: stop navigation, take the TreeClaims claim, run TreeShape.survey.
   - Any verdict other than OK sends the tree to badWild before the first cut, so it can never be left with floating logs.
   - For a big tree, the canopy box is ±5.
3. Cut top-down at player speed with the held item (Breaker, +1 XP per log).
   - If the top log is out of TRUNK_REACH 7.5 on the first try, walk to within 1.5 of the stump first (at most 200 ticks).
   - Logs still out of reach move to a deferred list.
4. When the stump block breaks (all 4 bottom logs for a big tree):
   - push the replant entry {spot, species, big, now};
   - felled++;
   - felling = null.
5. Final pass: if deferred logs remain, goTo(spot, 0.3), standing in the cut stump column, and cut them. From there +8 is 6.88 away, within reach. Anything still unreachable is left (rare).
Resume: when felling != null (after a full backpack, combat or a reload), decide() goes back to CHOP. It surveys again with resume = true from the lowest SAME_LOG within the stump column(s) up to +8.

E. CANOPY AND DROPS
- LEAVES: findLeaves/breakLeaves as today, natural leaves only, within 5.5 reach, in a box of ±3 (±5 for big trees) from the stump up to +12.
- COLLECT: everything uses the wild box (stump inflated 6/2/6, plus 10 up; 7 for big trees). plotArea is deleted.
- The 160-tick per-item and 400-tick phase timeouts are kept.
- The leaves-underfoot rule in run() fires only for non-persistent leaves that are not in planIndex. Anything else is left to DigOut.perched (critique gap 4).

F. REPLANTING THE SAME SPECIES
ForestRules.replant(Species want, boolean big, int sameCarried, Species substitute, long waited) returns Replant(Action, Species, boolean twoByTwo, int count). substitute is the best carried 1x1 sapling in GROVE_ORDER, then JUNGLE; never DARK_OAK.
- DARK_OAK: SAME, twoByTwo, 4 saplings, when sameCarried >= 4. A lone dark oak is never planted.
- Any other species: SAME with 1 sapling when sameCarried >= 1. For a big entry it is planted at the NW corner, never 2x2, so no mega spruce or jungle tree grows that nobody can fell.
- Otherwise:
  - SUBSTITUTE (1 at NW) once waited >= REPLANT_PATIENCE = 12000;
  - DROP once waited >= REPLANT_GIVE_UP = 48000;
  - else WAIT. On WAIT, if storage holds that species and now >= nextSaplingTrip, one STORAGE trip (nextSaplingTrip = now + 2400); otherwise the entry is skipped for now.
Felling never waits for a sapling, which ends today's livelock (WoodcutterJob.java:206-212, 422-429).
PLANT:
- goTo(spot, 3.0).
- Every target cell must be air or canBeReplaced (short grass, flowers, snow layer), with no fluid. The block below must be #dirt and not FARMLAND, and canSurvive must hold. If not, the entry is dropped.
- Place the sapling(s) with setBlock, the grass place sound and a swing; consume them.
- Add the spot(s) to planted and remove the queue entry.
- REGROWTH rolls on every replant and grove planting: growNow(spot, 2), or on the NW corner for a 2x2.
- No terrain edits: no forced dirt and no free removal of leaves or blocks. Today's lines :781-801 are deleted.

G. TENDING (BONEMEAL)
- The mechanics are unchanged: walk within 3, wait 10 ticks, vanilla BoneMealItem.growCrop (45%), particles 1505.
- It works round-robin over planted entries that are loaded and are still a SaplingBlock, those outside the MIN/MAX belt first (inside it, random ticks do the work).
- A loaded entry that is no longer a sapling is pruned (grown or destroyed). Unloaded entries are kept.
- Bone meal still comes from the blacksmith's bone order.

H. GROVE (sustainability, and the answer for treeless biomes)
When: decide() step 8. The woodcutter carries spare saplings (beyond what queued entries reserve: 1 each, 4 for dark oak), and fewer than groveSize (8) planted entries lie inside the base's MIN/MAX box. It is off when groveSize = 0, and it never runs on GameTest fake bases, whose MIN/MAX lies inside the walls rule.
Spot (ForestScan.groveSpot; cached; at most 200 lattice points per call):
- inside MIN/MAX, the forced ticking belt;
- Chebyshev >= half+4 from the centre;
- on the lattice (x-cx)%5 == 0 && (z-cz)%5 == 0 (GROVE_SPACING 5);
- not in the road strip |dz| <= 3 for |dx| <= half+30, which keeps both gates and the west path clear;
- ground is #dirt and not farmland or dirt_path;
- the spot is air or replaceable, with level.canSeeSky(spot). Vanilla SaplingBlock needs getMaxLocalRawBrightness >= 9, which open sky gives in daylight;
- no log, leaves or sapling within 2 horizontally at dy 0..4;
- neither the spot nor the block below is in planIndex;
- !WorkSafety.reserved(GROVE).
The levelled west pad and the 5-block skirt are grass in every biome, deserts included, so grove ground exists without terrain edits.
Species, ForestRules.GROVE_ORDER: BIRCH (5-7 logs) > SPRUCE (5-8) > OAK (10% fancy oak, which may exceed the cap) > CHERRY > ACACIA. Never JUNGLE (up to 12 tall) or DARK_OAK. Grove saplings go into planted, and are later felled and replanted like any tree.
New bases: BaseBuilder plants 4 oak saplings on the west pad at the lattice spots (cx-35, cz±5) and (cx-40, cz±5), with dirt set below only if the block is not #dirt (construction, as today). It grows 2 of them (advanceTree up to 6 times) and records all 4 in planted. There is no fence.

I. RANDOM TICKS AND CHUNK LOADING
- No new forced chunks. The MIN/MAX belt, kept in shared, gives forced random ticks to the grove and to every replant inside it (nfsrc ServerChunkCache.java:364 shouldForceTicks).
- Replants elsewhere grow near players, inside the woodcutter's own 3x3 traveller ticket while it works nearby, or with bone meal.

J. STORAGE TOP-UP (critique gap 15)
- Unload: keep() is now 8 saplings per species and 16 bone meal, so old fences (up to 256), planks, logs and sticks go to storage, where the builders use fences.
- upgradeEquipment and the axe notice, as today.
- For each queued species not carried: take 1 (4 for dark oak).
- If the grove needs saplings and fewer than 4 are carried: take GROVE_ORDER saplings up to 6.
- Bone meal < 8: take up to 16.
- The oak-first rule is deleted.

K. OLD FENCES (DISMANTLE)
- If wp.legacyFence, the posts are the union of TreePlotLayout.fence(wp, t) for t = 1..3 that are OAK_FENCE right now and not in any planIndex. This also covers stray posts from an interrupted upgrade.
- decide() step 2 takes the nearest post: goTo within 3, break it with the Breaker at axe speed (the fence drops itself into the backpack).
- A post is skipped after 300 ticks or 3 route failures.
- When none is left: legacyFence = false, message '<name> takes down the old tree-plot fence'.
- Old grid trees and saplings are ordinary natural trees and saplings; the migration seeds planted with the old grid spots.

L. WOOD RESERVE
- CookingJob.woodReserve(data, b) always returns LOG_RESERVE (16) and no longer streams data.workplaces (critique gap 19).
- SmelterJob keeps max(24, reserve) = 24 logs before it makes charcoal.

M. NIGHT
With remoteWorkInDaylightOnly, between dayTime 13000 and 23000:
- only trees, queue entries and grove spots within Chebyshev half+16 of the centre are chosen, and forage is not started;
- if nothing is left: STORAGE when carrying cargo, else wait at the storage stand with status 'Waiting for daylight' (waitFor 600, markBusy).

N. DECIDE ORDER
1. STORAGE triggers, unchanged: no axe and the check is due, cargo >= 48, or a full backpack.
2. DISMANTLE.
3. Resume felling.
4. The replant queue: the nearest entry acted on per F.
5. COLLECT near the last stump.
6. Fell the best eligible tree.
7. BONEMEAL, when carrying bone meal.
8. The grove.
9. forage(): anchored at base.center; target = MapBounds.clamp(centre + 72·dir(k·45°), 16); goTo reach 8 or 2400 ticks; then scan radius 40 around itself; 8 failures mean a 6000-tick rest. It is disabled when BaseZones.forageAllowed(base) is false (GameTests).
10. 'Saplings are growing', waitFor(100).

O. IDLE AND RETRAINING
- markIdle('no trees or saplings') only when all of these hold: no tree was found, forage is resting, planted and replant are both empty, and no sapling is carried or stored. Otherwise markBusy.
- Retraining releases the TREE_PLOT (through FactionNpc.role) and sets base.barrenUntil(WOODCUTTER) = now + 48000.
- Woodcutters still work bare-handed (needsToolToWork excludes them).

P. DELETED
- WoodcutterJob:
  - plotArea, Vec3Min/Max, orphanTree and its cache, the grid loops, tendCursor, inPlot, viaOpening, plotWalk, skipUntil, the wild flag, protectedSpot;
  - the plot PLANT branch (forced dirt, deleted leaves, free debris removal), the oak-first top-up;
  - fenceNet, craftFences, upgrade, upgradeSteps, prepareColumn, levelNewGround, upgradeBlockedUntil, nextUpgradeCheck, groundPrepared;
  - the CRAFT and UPGRADE states, the fence/plank/log/stick keep() rules, and WILD_RANGE (now config forestRadius).
- TreePlotLayout: insideInner is deleted; grid, half, depth, local, spots and fence stay for the legacy conversion and DISMANTLE.
- BaseBuilder: the fence ring, workplaceManaged, and the grid starter saplings.
- CookingJob: the plot term.

## Fishing

A. RECORD
There is one FISHERY per base. It is moved in place: id, assigned and totalOutput are kept.
FISHERY-only NBT:
- 'fishV' = 1. If it is absent the record is legacy (see saveCompat).
- 'spot' int: 0 UNSEARCHED, 1 VALID, 2 NONE.
- 'open' boolean: the spot is vanilla open water.
- 'searched' long: when a fisherman's full search and forage last failed. This is the NONE memory.
- 'bad' long[]: at most 32, oldest dropped first.
origin is the bobber water block and extent is the stand (feet). While the spot is not VALID, origin = extent = base.center as a placeholder. They are never null, because WorkplaceState.save calls origin.asLong() (WorkplaceState.java:79).
BaseBuilder.ensureWorkplaces is renamed from ensureAnimalAndFishingWorkplaces; its PEN branch is unchanged. It:
- creates a missing FISHERY as UNSEARCHED at the centre;
- moves the placeholder of any record that is not VALID back to the centre;
- never derives a spot from farm_min/farm_max again (BaseBuilder.java:157-164 is deleted).

B. SPOT CRITERIA (new pure logic/FishingWater.java, with a CaveSurvey-style Cells interface)
Cells:
- waterSource(x,y,z)
- aboveWater: air or a lily pad (vanilla ABOVE_WATER)
- insideWater: a water source with empty collision, such as kelp, seagrass or a bubble column (vanilla INSIDE_WATER)
- farmland
- sky
- sturdyGround: a sturdy top face that is not farmland, leaves, ice or fluid
- clear: no collision and no fluid
- sight(stand eye to bobber centre)
Functions:
- int bodySize(c, x, y, z, cap 400): 4-neighbour flood over waterSource cells with aboveWater above, on B's layer.
- boolean farmlandNear(c, x, y, z, r=5): any farmland in x±5, y-1..y+1, z±5. Vanilla FarmBlock.isNearWater reaches 4, so no block that hydrates farmland can ever qualify, player farms included.
- boolean openWater(c, x, y, z): a replica of FishingHook.calculateOpenWater (FishingHook.java:383-421). Four 5x5 layers y-1..y+2; each layer is all INSIDE_WATER or all ABOVE_WATER; the first layer may not be ABOVE_WATER; INSIDE may not follow ABOVE; a mixed layer is invalid.
- int[] stand(c, x, y, z): the nearest S with Chebyshev 2..5 from B, S.y - B.y in 1..2, sturdyGround below, clear(S) and clear(S+1), and sight. null if there is none.
- Spot evaluate(c, x, y, z), which requires:
  - waterSource(B), aboveWater(B+1) and waterSource(B-1), so the water is at least 2 deep;
  - sky(B+1);
  - bodySize >= MIN_BODY (25);
  - !farmlandNear(5);
  - a stand.
  It returns Spot(bx, by, bz, sx, sy, sz, open, body).
- boolean waterKnown(int spot, long searchedAt, long now) = spot == VALID || spot == UNSEARCHED || now - searchedAt >= 48000.
The farm channel fails three independent rules: it is 1 deep, its body is 5, and farmland is within 5. It also lies inside the walls.
Zone rules, applied in the world adapter to B and S:
- Chebyshev >= half+4 (28) from every settlement centre, the own one included;
- !inForeignArea(…, 8);
- more than 24 from every lair centre;
- inside MapBounds;
- within fishingRadius (64) of base.center horizontally, or within radius+40 for spots found while foraging;
- not within 2 of any badSpots entry;
- S not in WorkSafety.reserved(FISH_STAND): not within 8 of any MINE origin, not within 1 of a planted sapling.

C. SEARCH (new work/FishingSpots.java)
search(level, data, base, around, radius, bad):
- Columns at step 2 with a rotating parity per base. Only columns where level.hasChunkAt holds, inside MapBounds.
- top = MOTION_BLOCKING_NO_LEAVES-1 must be a water source with air or a lily pad above.
- depth = top - (OCEAN_FLOOR-1) must be at least 2.
- Shore candidate: a cardinal column 2 or 4 blocks away whose top block is not water.
- Prefilter: the zone rules for B.
- One candidate per 8x8 cell, the one nearest base.center. The 12 nearest get FishingWater.evaluate plus the stand zone rules.
- Best: open water first, then the shorter distance from the nearer of gate_w/gate_e, then the larger body.
- Cost: about 3200 columns plus 12 evaluations (flood at most 400 cells, farmland box 363 reads, open water 100 reads, stand search).
- It runs at most every 600 ticks per fisherman and every 6000 ticks per base for the director.
FishingSpots.maintain(level, data, b, t) is called from FactionDirector.settlement:
- if the FISHERY is not VALID and t >= the next director search for this base (transient map, +6000), it runs search(b.center, fishingRadius);
- on success it writes VALID and sends the message 'fishing water found at x,y,z (open water)';
- it never writes NONE.
Old farm fisheries are therefore re-searched even with no fisherman, and a base can know about water before it hires one.

D. FishermanJob (states STORAGE, SEARCH, FORAGE, FISH; the state is saved and honoured on load; FISH loads as SEARCH when the spot is not VALID)
Guard: no FISHERY or no base gives 'No fishing spot', waitFor(400). This keeps the baseless GameTest guard.
Night: when VALID and remoteAtNight(extent) (Chebyshev > half+16 between 13000 and 23000), go to STORAGE if carrying cargo, else wait at the storehouse with 'Home for the night' (waitFor 600, markBusy).
- STORAGE: as today (unload, upgradeEquipment, no rod: wait 400 and the toolless path). Then VALID goes to FISH, anything else to SEARCH.
- SEARCH: if now >= nextSearch: nextSearch = now+600 and run search(base.center, R), where R = BaseZones.workRadius(base, fishingRadius).
  - Found: write origin/extent/open, VALID, markBusy, message, then FISH.
  - Not found: FORAGE. If forage is not allowed (GameTests), go straight to NONE.
- FORAGE:
  - While resting (now < restUntil): status 'No fishing water within reach', markIdle('no fishing water'), waitFor(1200).
  - Otherwise target = MapBounds.clamp(centre + 72·dir(k·45°), 16); goTo reach 8, or give up after 2400 ticks.
  - Then search(npc position, 40), accepted up to fishingRadius+40 from the centre. Found: VALID, FISH.
  - Not found: k++. After 8 failures: spot = NONE, searched = now, restUntil = now+6000, markIdle.
- FISH:
  - Backpack full or cargo >= 32: STORAGE.
  - Travel with goTo(extent, 1.0). More than 1200 ticks without arriving, or 3 'Cannot reach' results (Job gets a protected routeFailures() accessor), means: add origin to bad, spot = UNSEARCHED, SEARCH. Remote lakes can no longer trigger endless DigOut.
  - Pick up only items within 3 of the stand that are not in water (!isInWater). It never swims after catches, and never takes the farm's harvest.
  - Every cast, a cheap check: waterSource(B) with aboveWater, the stand still standable, !farmlandNear(5).
  - Every 20th cast, the full evaluate plus zones (open is recomputed).
  - Any failure: add to bad, spot = UNSEARCHED, SEARCH. The permanent 'The fishing spot has no water' block (FishermanJob.java:80-83) is gone.
  - Cast timing as today. The countdown follows vanilla catchingFish (FishingHook.java:284-292): -1 per tick, plus another -1 with 25% chance when level.isRainingAt(B.above()). The no-sky slow-down cannot happen, because open sky is required.
Job NBT: 'state', 'forageDir', 'forageFails', 'restUntil', 'casts'.

E. LOOT
reelIn keeps BuiltInLootTables.FISHING: ORIGIN = bobber (so the jungle bamboo entry still works), TOOL = rod, luck = Luck of the Sea, the Angler re-roll, rod wear, a 1-6 XP orb and the fishy_business criterion.
THIS_ENTITY = static FishermanJob.lootEntity(open, npc):
- If open && fishingTreasure: a cached FishingHook built with the public new FishingHook(EntityType.FISHING_BOBBER, level()) (FishingHook.java:76), positioned at the bobber with setPos and never added to the world. Its openWater defaults to true (:62), so FishingHookPredicate matches the treasure entry exactly as it would for a player.
- Otherwise: the npc, which gives fish and junk only, as today.

F. NO WATER IN RANGE
- The fisherman never falls back to the farm or to any water inside the walls.
- FactionDirector.needs adds FISHERMAN only when all of these hold:
  - living(FISHERMAN) == 0;
  - food is low, and there is a rod or at least 2 string (both as today);
  - FishingWater.waterKnown(spot, searched, t);
  - t >= base.barrenUntil(FISHERMAN).
  A waterless base therefore pays for at most one trial recruit every 2 days, and the rationing filter, which takes the first food producer, can no longer pick a fisherman with nowhere to fish.
- Retraining: FishermanJob.idleFor(12000) holds after FORAGE has failed. The FISHERY is released through FactionNpc.role, and barrenUntil(FISHERMAN) is set to t+48000.

G. FARM IRRIGATION UNTOUCHED
The channel (BaseLayout.java:223-224), farm_min/farm_max and FarmerJob are not modified. The farmer's collect box no longer overlaps the fisherman, and the fisherman no longer collects the harvest.

H. DISPLAY
- BaseInfo:
  - 'Fishing spot #id at x,y,z (open water | still water), worker X, N caught';
  - 'Fishing spot #id: looking for fishing water';
  - 'Fishing spot #id: no fishing water within reach (searched N.N days ago)'.
- JSON: origin, extent, spot, open, searched.
- tools/sim.py flags any VALID FISHERY within half+4 of a base centre.

I. DELETED
- The farm-channel origin/extent derivation and its javadoc.
- The permanent no-water wait.
- The AABB(extent).inflate(4) chase box.
- README 'Fishermen stand at the farm's water channel'.

## Shared

1. ZONES (new campaign/BaseZones.java; pure maths in new logic/Zones.java)
Every check is based on the centre and HALF, never on MIN/MAX. This settles contradiction 3.
- static int half(BaseState b) = b.testHalf >= 0 ? b.testHalf : (settlement ? BaseLayout.HALF : LairLayout.HALF)
- chebyshev(BaseState b, BlockPos p)
- boolean insideWalls(b, p, margin): Chebyshev <= half(b) + margin, at any y.
- boolean outsideAllWalls(CampaignData d, BlockPos p, int margin): checks every non-destroyed base.
- boolean inForeignArea(d, int ownBaseId, p, margin): any non-destroyed base with id != own and contains(p, margin); lairs use contains(p, margin+8).
- boolean nearLair(d, p, r)
- boolean nearKnownVillage(FactionState f, p, r): from Report.Kind.VILLAGE.
- int workRadius(BaseState b, int configured) = b.testRadius > 0 ? b.testRadius : configured
- boolean forageAllowed(b) = b.testRadius <= 0
The own base's MIN/MAX box (contains) is never used as an exclusion by the three jobs.
New campaign/MapBounds.java:
- inside(d, x, z, margin): true when d.radius == 0, else |x-cx| and |z-cz| <= radius - margin.
- clamp(d, BlockPos p, margin).
Every far target (survey points, forage points, lakes) is clamped, because NpcNavigator.waypoint() and surfaceExit() generate chunks (NpcNavigator.java:309, :335).

2. DIG AND WOOD SAFETY (new work/WorkSafety.java)
- public static boolean mayDig(ServerLevel level, BlockPos pos, @Nullable BaseState own, @Nullable WorkplaceState ownMine) returns false for:
  (1) getDestroySpeed < 0;
  (2) hasBlockEntity (chests, spawners, vaults, beds, shriekers);
  (3) structural(state): stone, mossy, cracked and chiseled bricks, deepslate bricks and tiles, chiseled and polished deepslate, tuff bricks and polished or chiseled tuff, mud bricks, bricks, nether bricks, end stone and prismarine bricks, purpur, polished blackstone bricks, quartz blocks, reinforced deepslate, the sculk family (sculk, vein, sensor, calibrated sensor, shrieker, catalyst), and copper grates, bulbs and chiseled copper (every oxidation level, waxed included). Mineshaft planks, rails and infested stone stay diggable, as for a player;
  (4) any position in a non-destroyed base's planIndex;
  (5) under a settlement: Chebyshev(pos, centre) <= half+2 and y >= centre.y-48, only when that base's planIndex is not empty. For lairs: <= LairLayout.HALF+2 and y >= centre.y-16;
  (6) BaseZones.inForeignArea(data, own.id, pos, 8);
  (7) MineLayout.nearStairs(w, pos) for any MINE w != ownMine, after a pre-check |dx|,|dz| <= 6 from w.origin;
  (8) level.getBiome(pos).is(Biomes.DEEP_DARK). This keeps ancient cities intact; a warden is not the reason, since shriekers react only to players.
- protectedWood(level, data, own, pos): see woodcutting C.
- enum Use {MINE_SITE, GROVE, FISH_STAND}; reserved(CampaignData d, BaseState own, BlockPos p, Use u) is the single registry of reserved zones (contradiction 6):
  - MINE_SITE: within 12 (Chebyshev) of a VALID fishery origin or extent of any base, or within 6 of any woodland planted sapling or replant entry.
  - GROVE: within 6 of any MINE origin, within 6 of a VALID fishery origin or extent, or in the own road strip |dz| <= 3 for |dx| <= half+30.
  - FISH_STAND: within 8 of any MINE origin, or within 1 of a planted sapling.
- Cost: loops over at most about 6 bases and all MINE records, with distance pre-checks, once per dug cell. Today DigOut.isProtected already streams every workplace per cell. No MineIndex.

3. WORKPLACES (work/Workplaces.java)
- claim(npc, type, ok):
  - The NPC's own record is returned only if ok.test(w): `if (own) { if (ok.test(w)) return w; continue; }` (critique gap 1).
  - A holder counts as present only if holderValid(data, w): the NpcRecord exists, is alive, and its role fits the type (MINE/CAVE: MINER; TREE_PLOT: WOODCUTTER; FISHERY: FISHERMAN; other types: alive only). Records still held by NPCs retrained in old saves become free.
  - unworked(data, w) = !holderValid.
- New release(CampaignData, WorkplaceState): assigned = null, setDirty.
- New releaseAll(CampaignData, UUID): releases every workplace assigned to that UUID.
- Every workplaceInfo() and keep() uses held(). Workplaces are created only in explicit job states (MinerJob SITE) and in BaseBuilder (critique gap 2).

4. ROLE CHANGE (npc/FactionNpc.java)
role(newRole): when changed && level() instanceof ServerLevel, call Workplaces.releaseAll(CampaignData.get(server), getUUID()). This is safe for new NPCs, which hold nothing, and for loading, which sets DATA_ROLE directly (FactionNpc.java:998).

5. JOB (work/Job.java)
- private long idleSince = -1; private String idleReason = "".
- protected markIdle(String reason): sets idleSince if it is not set.
- protected markBusy().
- public boolean idleFor(long ticks).
- public String idleReason().
- protected boolean beforeErrand() { return true; }: called in survival() right before both atStorage() errands.
- protected boolean isNight(): dayTime%24000 in [13000, 23000).
- protected boolean remoteAtNight(BlockPos p) = FCConfig.REMOTE_WORK_IN_DAYLIGHT_ONLY && isNight() && BaseZones.chebyshev(base, p) > half(base)+16. Same window as TraderJob.
- protected int routeFailures().
The idle state is transient, as today.

6. FACTION DIRECTOR (campaign/FactionDirector.java)
- settlement(): BaseBuilder.ensureWorkplaces(data, b) (PEN; FISHERY as UNSEARCHED; TREE_PLOT if missing), then FishingSpots.maintain(level, data, b, t), then the rest as today.
- needs():
  - The 'replacing the missing X' loop skips a role while t < b.barrenUntil(role).
  - FISHERMAN is gated as in fishing F.
  - 'an open mine has no miner' replaces 'the constructed mine has no miner'.
  - The cave need is unchanged.
- recruitment(): remove the `top.role() != NpcRole.MINER` gate at :309 (critique gap 8). The r.role == top.role() skip already stops same-role retraining.
- retrainIdleWorker:
  - Candidates are non-combatants whose role != top.role.
  - Eligible when job.idleFor(RETRAIN_IDLE_TICKS = 12000) or job.missingToolFor(12000).
  - For an idle (not toolless) worker: b.barrenUntil.put(oldRole, t+48000).
  - Then r.role = top and n.role(top), which releases its workplaces.
  - The message uses job.idleReason(), for example 'no place to dig a mine', 'no trees or saplings', 'no fishing water'.

7. BaseState (campaign/BaseState.java)
- Map<String, Long> barrenUntil, saved as the compound 'barrenUntil'; if missing it is empty. Helper barren(NpcRole, now).
- Transient int testHalf = -1 and testRadius = -1, set only by GameTests and never saved.
- MIN/MAX, contains() and deepInside() are unchanged.

8. LAYOUT AND BUILDER (new campaigns)
- BaseLayout:
  - delete MINE_ORIGIN_X and the east outside path (:250-252);
  - keep PLOT_ORIGIN_X and the west path to the grove pad;
  - add WORK_AREA_WEST = 58 and WORK_AREA_EAST = 78;
  - class doc: 'grove pad outside the west gate; miners, woodcutters and fishermen choose their own places'.
- BaseBuilder.buildSettlement:
  - level the walled pad and the west pad (grove ground);
  - skirt over those two pads only;
  - no MINE, buildInitial, sealMine or mine pad;
  - TREE_PLOT created with woodV 1 and no fence;
  - FARM as today;
  - the 4 starter saplings (woodcutting H);
  - ensureWorkplaces;
  - MIN/MAX overwrite with identical numbers, MAX.x = cx+78 and MIN.x = cx-58 (comment: 'forced, randomly ticking work belt: groves and nearby mines keep ticking');
  - starter supplies unchanged.
- SiteFinder.settlement() = Footprint(PLOT_ORIGIN_X-26, HALF+2, -HALF-2, HALF+2). mineAreaPenalty and its call are deleted; its logic lives on in MineSites.corridorPenalty, on runtime heightmaps. The dry-site preference is unchanged.
- Terrain and CampaignStarter keep their code; their docs are updated.

9. NAVIGATION
- NpcNavigator is unchanged; the comment at :267 is updated.
- walkInMine gets its first leg; leaveMine uses inMine; beforeErrand routes errands out of mines.
- Only the W/E gates exist; mine sites and fishing spots score the walk from the nearer gate.

10. CHUNK LOADING
ChunkLoading is unchanged. The MIN/MAX numbers are unchanged, so no forceBase migration is needed and no ticket leaks. Remote workers keep the 3x3 traveller tickets.

11. BLACKSMITH (work/BlacksmithJob.java)
- membersNearBase() is replaced by members(): the faction roster entries with alive && baseId == b.id, resolved with level.getEntity(uuid) as FactionNpc, at any y, within 256 blocks horizontally of the centre. This covers deep miners, remote fishermen and foraging woodcutters (critique gap 6).
- Order 1 is deleted.

12. COOKING: CookingJob.woodReserve = LOG_RESERVE (16) always; SmelterJob is unchanged (24).

13. DISPLAY AND DIAGNOSTICS
- BaseInfo.baseLines (info/BaseInfo.java:88-105):
  - Active MINE: 'Mine #id at x,y,z facing east (worker): y=16 iron 120/463 r0/4, y=64 coal 463/463 r1/4; shaft to y=-56 [, digging down to y=Y step n]; output N'.
  - Worked-out and resting mines on one line: 'Worked-out mines: #4, #9 (output M); resting until an iron pickaxe: #7' (critique gap 9).
  - TREE_PLOT: 'Woodland #id (worker): F trees felled, P saplings growing (G in the grove), Q stumps to replant (1 dark oak waiting for 4 saplings)[, old fence being taken down]; output N'.
  - FISHERY: see fishing H.
  - Other types unchanged.
- MapPayload and MapScreen are unchanged: no new marker kinds, so no client/server protocol change.
- FCCommands.snapshot keeps id, type, tier, upgradeStep, output and assigned (tools/sim.py keeps working), and adds:
  - for MINE: origin, facing, hut, bottom, shaftLimit, maxRounds, cur, restTier, workedOut, note, levels [{y, band, progress, size, rounds, cut}], and tier = levels.size();
  - for TREE_PLOT: origin, felled, planted, replant, legacyFence;
  - for FISHERY: origin, extent, spot, open, searched.
- mineDump prints the level list and the current plan window, using MineLayout.tunnelPos(wp, cur, c).
- tools/sim.py:
  - stock line: 'logs' = the sum of every *_log key, 'saplings' = the sum of every *_sapling key (critique gap 14), plus gold_ingot and lapis_lazuli;
  - prints the new workplace fields;
  - prints ERROR for a VALID FISHERY within half+4 of its base centre.

14. GAMETEST SUPPORT
New gametest/GameTestBases.java, with register(h, faction, half, radius, timeoutTicks). It:
- creates a BaseState with id 990100+n, centre at rel (12,2,12), SPAWN, and STORAGE_STAND (parameter, default rel (4,2,4));
- sets MIN/MAX = centre ± (half+1), with y -1/+8;
- sets testHalf and testRadius;
- places 4 storage chests as fakeBase does;
- puts the base in data.bases;
- returns a handle whose cleanup() removes the base, its workplaces and the roster entries of NPCs registered through it. cleanup() is called on every succeed or fail path and is also scheduled with h.runAfterDelay(timeout-2) to cover a timeout.

## Save compatibility

POLICY
Old campaigns load and keep playing. This reverses the README.md:405-406 precedent, and the README note is rewritten.
- Nothing in a saved enum is removed or renamed: WorkplaceState.Type, NpcRole and faction types all keep their names. Only labels change.
- Each workplace converts itself in WorkplaceState.load. The conversion is keyed on the absence of the new marker key ('mineV', 'woodV', 'fishV'), runs once per record, and is pure (no world access).
- Follow-ups that need the world run in existing hooks: the per-period hook (FactionDirector.java:76, renamed BaseBuilder.ensureWorkplaces), FishingSpots.maintain, and the jobs themselves.
- CampaignData.FORMAT becomes 2. load() reads 'format' into a field that is only logged ('loading campaign format 1 - migrating workplaces') and never used as a gate.
- Unknown job states keep falling back through the existing valueOf/catch paths.

MINE (no 'mineV': a legacy hut mine; exactly one per settlement, origin cx+32, facing east)
LegacyMine.migrate(surfaceY = origin.y, tier, subLevel, progress, reach, revisit, upgradeStep) is pure. It uses the old SpiralMine regular and infill math, moved verbatim into logic/LegacyMine, and returns Migrated(levels {step, progress, rounds}, bottom, cur, upgradeStep):
- n = tier + subLevel. For i = 1..n, step_i = LegacyMine.landingSteps(surfaceY, i). The list is truncated at the first -1.
- Old current level oc = revisit > 0 ? revisit : n.
- Rounds:
  - Regular levels (i <= regularLevels(surfaceY)) with revisit == 0 get reach.
  - With revisit > 0: levels i >= revisit get reach; levels i < revisit get reach-1 (never below 0).
  - Infill levels get 0.
- Progress: level oc keeps wp.progress, which stays a valid index because longer plans start with the shorter ones. Every other level gets 0. mine() fast-forwards dug, passable cells at 1 per tick without walking, and digs the old undug ~40%.
- bottom = max(step_i).
- Descent in progress (upgradeStep >= 0, revisit == 0): next = landingSteps(surfaceY, n+1).
  - next > bottom: append {next, 0, 0} as cur, keeping upgradeStep. spiralPlan(bottom, next) is the same list the old EXTEND used, since the old from = landing(deepest) = bottom.
  - 0 < next <= bottom (an infill level): append it as cur with upgradeStep = -1.
  - next < 0: upgradeStep = -1.
- n == 0 (corrupt): no levels, bottom 0.
- The record gets hut = true, maxRounds = 4, cur = the index of oc (or the appended level), band = -1 for every level, workedOut = false, mineV = 1. tier, subLevel, reach and revisit are read only here and never written again.
The hut, its torches and the east path stay in the old Settlement StructureRecord. Builders keep repairing them, DigOut keeps protecting them, and leaveMine keeps the hut door because hut = true. The old MIN/MAX (cx-58..cx+78) are not touched.
Example, surface 66: the legacy levels are at steps 6, 34, 66, 114, 122. The planner then reuses 66 (y=0) for LAPIS and 122 (y=-56) for DEEP, and opens IRON at step 50 (y=16), 16 steps from its neighbours. A mine that was already worked out with reach 4 plans WORKED_OUT or REST, and its miner moves on to caves or a new site.

TREE_PLOT (no 'woodV')
- legacyFence = true.
- planted = TreePlotLayout.spots(w) for its tier. Entries that are not saplings are pruned the first time they are visited while loaded.
- replant is empty; felled = 0.
- upgradeStep = -1 and tier = 1. The fence geometry comes from origin and facing (DISMANTLE checks the rings of tiers 1..3, so an interrupted upgrade is covered).
- Woodcutters carrying fences, planks, logs or sticks unload them, because keep() no longer keeps them.
- The levelled pad and the west path stay as plain ground and path. Old plot trees are cut as natural trees, since the own MIN/MAX box is no longer protected.

FISHERY (no 'fishV')
- spot = UNSEARCHED; id, assigned and totalOutput are kept.
- The next ensureWorkplaces moves the placeholder origin and extent to base.center.
- maintain() or the fisherman's SEARCH then finds real water, or the forage and retrain path takes over.
- The farm channel is never touched.
- FishermanJob.load used to ignore its state; it now honours it, and FISH becomes SEARCH unless the spot is VALID.

BaseState
'barrenUntil' missing means empty. Points, structures and forced chunk tickets are unchanged; the new bases' MIN/MAX numbers equal the old ones.

NPC job NBT
- MinerJob adds SITE (loads as CHECK) and 'badSites'. MINE, EXTEND, CLEAR and CRAFT still load as TRAVEL, so a miner standing in its old tunnels walks the migrated level list. The 'cave' sub-tag is unchanged.
- WoodcutterJob: a tag without 'v' loads as CHECK with its spot dropped (CRAFT and UPGRADE included).
- FishermanJob: see above.

Old-save holders
Records still assigned to NPCs that were retrained before this change are freed by Workplaces.holderValid.

Verification
- GameTest legacyWorkplacesLoad: load, save and load again on hand-built old tags.
- A dedicated-server load of the old-format world in the scratchpad (world_run3). Expect:
  - no exceptions;
  - every MINE shows migrated levels and its miner continues;
  - fences are gone within a day;
  - no FISHERY is left at C+(15,-1,8);
  - new mine sites open only once the old mine is worked out.

Docs
README.md:405-406 is replaced by: 'Old campaigns load and continue: the hut mine is migrated and keeps being worked, old tree-plot fences are taken down into storage, and fishermen leave the farm for real water. Documented fixed-seed results came from the old layout and are historical.'

## Config

New section in FCConfig (config/FCConfig.java): b.comment('Workers: where miners, woodcutters and fishermen work (PROPOSED defaults)').push('workplaces').

| key | default | range | meaning |
| mineSiteRadius | 64 | 40..128 | Farthest Chebyshev distance from the base centre at which a miner opens a mine. The inner limit is fixed at walls half-size + 8 (32). |
| maxOpenMines | 3 | 1..8 | Active (unfinished, not resting) mines a base may have. Each miner works one. |
| forestRadius | 64 | 32..128 | Woodcutters fell natural trees within this many blocks of the base centre. Replaces WoodcutterJob.WILD_RANGE (which was anchored on the plot gate). Foraging goes 72 blocks further out. |
| groveSize | 8 | 0..32 | Saplings the woodcutter keeps growing in the base's force-ticked work belt. 0 = replanting only. |
| fishingRadius | 64 | 32..128 | Fishing water is searched for within this many blocks of the base centre. Spots found while foraging may be up to 40 further. |
| fishingTreasure | true | bool | In open water (vanilla 5x5, 2 deep, open sky) the vanilla treasure catches can drop, exactly as for a player. |
| remoteWorkInDaylightOnly | true | bool | Between dusk and dawn (dayTime 13000..23000), woodcutters and fishermen do not work more than 16 blocks outside the walls. |

The section is added in step 1, with no readers yet. docs/factionclash-common.toml is regenerated from FCConfig in step 7. That also fixes its known staleness: the missing war.pacts entry, the misplaced '#Recruitment' comment, and the survival and achievements headers.

PROPOSED constants that stay in code and are documented in BALANCE.md. They are tied to vanilla worldgen or are internal tuning, and a config value would invite inconsistent setups.

Mine layers (MinePlanner):
- Targets: COAL surface-6; IRON y=16; LAPIS y=0; GOLD y=-16 (vanilla absolute anchors); DEEP minBuildHeight+8 (y=-56). The floor is minY+8, deepEnough needs 6 steps, levels sit at least 6 steps apart, an existing level within 5 steps is reused, and MAX_REACH is 4 rounds of +40 blocks.
- wanted thresholds: iron < 8 (critical), diamonds < 8, iron < 32, fuel < 32 or a FUEL/WOOD shortage, gold < 16, lapis < 32 with an enchanting table.
- Pickaxe tiers: COAL 0; IRON and LAPIS 1; GOLD and DEEP 2.

Mine sites (MineSites):
- Grid 4, 64 candidates per tick, 4 survey walks at (±44, ±44) with 2400 ticks each, retry after 6000.
- Collar f 0..3 by s -1..2 with relief ±1; no fluid or built block within 3; walls +8, foreign areas +16, known villages 48, fishery 12, saplings 6, other mine origins 24 (Chebyshev).
- Corridor obstacles: other corridors +2, walls +4, other bases +8, lairs +16, map margin 8.
- Facing rejected at penalty >= 30 (water +6, dip +3, sampled every 6 blocks).
- badSites 32, unreachable after 2400 ticks, stairs blocked after 600.

Shaft:
- The same step stuck for 2400 ticks, or 3 seal failures, abandons the descent.
- Tier re-check every 2400 ticks; leaveMine budget 1200 ticks.

Woodcutting:
- MAX_FELL_DY 8 (TRUNK_REACH 7.5 is unchanged), MIN_LOGS 3, closer approach to 1.5 for up to 200 ticks.
- GROVE_SPACING 5, grove at Chebyshev half+4 or more and inside MIN/MAX, road strip |dz| <= 3.
- REPLANT_PATIENCE 12000, REPLANT_GIVE_UP 48000; planted cap 64, replant cap 32, badWild cap 128.
- TreeClaims last 1200 ticks; sapling trips every 2400; rescan every 600 at step 2 with rotating parity.
- Forage 72 out, 40 scan, 8 directions, rest 6000.

Fishing:
- MIN_BODY 25 (flood cap 400), depth 2 or more, farmland clearance 5, walls +4 (Chebyshev 28), lairs 24.
- Stand 2..5 away and 1..2 above.
- Fisherman search every 600 ticks, director search every 6000; full revalidation every 20 casts; travel gives up after 1200 ticks or 3 route failures.
- badSpots 32; NONE remembered for 48000 ticks.

Shared:
- barrenUntil 48000; RETRAIN_IDLE_TICKS 12000 (unchanged).
- Night window 13000..23000 with a safe radius of half+16.
- LOG_RESERVE 16 flat (charcoal 24).
- Blacksmith crew within 256 blocks.

Removed:
- MinerJob.MAX_REACH (moved to MinePlanner.MAX_REACH).
- WoodcutterJob.WILD_RANGE (now forestRadius).
- SpiralMine.TIER_TARGET_Y and MIN_Y (moved to LegacyMine, migration only).
- MineLayout.UPGRADE_TORCHES_PER_8_STEPS.

## Tests

UNIT TESTS
All in src/test/java/com/factionclash/logic. They are pure, with no Minecraft bootstrap, in the style of CaveSurveyTest and SpiralMineTest.

SpiralMineTest (trimmed)
- Kept: everyColumnKeepsAFloorBetweenSteps, consecutiveStepsAreWalkable, branchMineIsTwoHighAndCoversBranchesEveryFourBlocks, and lengtheningKeepsTheDugPartAndAddsMoreTunnel (without its regularLevels assertion).
- New landingHelpersFaceTheTunnels: for n in 1..300, landingAtOrBelow(n) and landingAtOrAbove(n) both satisfy (k-1)%4 in {1,2}. They are the nearest such k at or below n, and at or above n, respectively.

LegacyMineTest (new)
- Moved unchanged: levelsReachOreBandsAndFaceTheTunnels, infillLevelsFitBetweenExistingOnesOnceTheBottomIsReached, and regularLevelsOf70AtLeastFour (the old assertion).
- migrateTier3Mine: surface 66, tier 3, subLevel 0, progress 100, reach 0 → steps [6,34,66], bottom 66, cur 2, progress [0,0,100], rounds [0,0,0], upgradeStep -1.
- migrateLengtheningMine: surface 66, tier 4, subLevel 1, reach 2, revisit 3, progress 700 → steps [6,34,66,114,122], rounds [1,1,2,2,2], cur 2 with progress 700, the others 0.
- migrateWhileDiggingDeeper: surface 66, tier 2, subLevel 0, upgradeStep 37, progress 300 → steps [6,34,66], bottom 34, cur 2 (step 66), upgradeStep 37, level 1 progress 300.
- migrateInfillUpgradeIsImmediate: surface 66, tier 4, subLevel 1, upgradeStep 0 → infill step 90 appended as cur, upgradeStep -1, bottom 122, level 4 keeps its progress.
- migrateCorruptOrTooLow: tier 0 → no levels, bottom 0. Surface -55 → no levels.

MinePlannerTest (new)
- stepsFollowOreBands: surface 70, minY -64 → COAL 6, IRON 54, LAPIS 70, GOLD 86, DEEP 126 (y 64/16/0/-16/-56).
- everyStepFacesTheTunnels: for surface -40..320 and every band, the step is -1, or 6 <= step <= surface-(minY+8) with (step-1)%4 in {1,2}.
- deepBandFeasibleForEverySurface: surface >= minY+14 → DEEP y in [minY+8, minY+10].
- deepBandFollowsMinBuildHeight: minY 0, surface 120 → DEEP y = 9.
- flatGameTestWorldHasNoLayer: surface -60, minY -64 → every stepFor is -1, deepEnough is false, next is WORKED_OUT (not REST).
- woodenPickOnlyCoal: tier 0 never yields IRON, LAPIS, GOLD or DEEP.
- stonePickNoGoldOrDeep: tier 1 never yields GOLD or DEEP.
- criticalIronFirst: tier 2, iron 3, diamonds 0 → NEW_LAYER IRON step 54.
- diamondsBeforeIronWhenIronIsNotCritical: tier 2, iron 20, diamonds 0 → NEW_LAYER DEEP step 126.
- lapisOnlyWithTable.
- coalWhenFuelOrWoodShort.
- staysOnUnfinishedLevel: stickiness wins over a changed stock.
- switchesWhenThePickIsTooLow: current DEEP level unfinished, tier 1 → not WORK(current).
- lengthensFinishedLevelBeforeTheNextBand: wanted [IRON], IRON level finished at rounds 0 → LENGTHEN.
- reusesALevelWithinFiveSteps: a legacy level at step 52 and IRON at 54 → WORK or LENGTHEN of that level, never NEW_LAYER.
- newLayersKeepSixStepsApart: randomised level lists; NEW_LAYER is never within 5 of an existing step.
- shallowerLayerNeedsNoDescent: bottom 126, IRON → NEW_LAYER step 54 <= bottom.
- respectsShaftLimit: shaftLimit 60 → never DEEP, IRON still offered.
- cutLevelIsNeverWorkedAgain.
- restWhenOnlyABetterPickHelps: tier 1 with COAL, IRON and LAPIS maxed → REST(needTier 2). Tier 2 with everything maxed → WORKED_OUT.
- wantedOrder: Stock → the documented list order.

MineSiteRulesTest (new)
- corridorRectForEachFacing: rounds r gives f -2..2+40(r+1) and s -11..12, mapped to world axes for all 4 facings (side = clockwise).
- facingsNeverPointAtTheBase: east of the centre → EAST first, never WEST. North-east → only EAST, NORTH and their perpendiculars that face away.
- overlappingCorridorsRejected and parallelCorridorsFarApartAccepted.
- ownWallsBlockTunnelsUnderTheBase: origin at Chebyshev 32 facing outward fits; facing along the wall at 32 does not.
- maxRoundsShrinksNearTheMapEdge: near the edge r < 4. When r=0 does not fit → -1.
- candidatesStayInTheRing: every candidate has Chebyshev in [32, 64] and sits on the 4-grid.

ForestRulesTest (new)
- speciesFromLog: oak, birch, spruce, jungle, acacia, dark_oak and cherry map to themselves. mangrove_log, stripped logs, wood blocks and crimson_stem give null.
- darkOakNeedsFourAndIsNeverPlantedAlone: 3 carried → WAIT; 4 → SAME twoByTwo. After patience with an oak carried → SUBSTITUTE oak count 1, never dark oak.
- bigSpruceIsReplantedAsOneSapling.
- replantTimeline: SAME; WAIT at 11999; SUBSTITUTE at 12000; DROP at 48000.
- groveLatticeAndExclusions: on lattice (multiples of 5), walls +4 rejected, road strip rejected, outside the belt rejected, farmland or path rejected, reserved rejected.
- groveOrderNeverJungleOrDarkOak.

TreeShapeTest (new, synthetic Cells)
- naturalOakIsFelledWhole: every log, top-down; stump on GROUND; verdict OK.
- frameLogTouchingPlanksAborts → TOUCHES_STRUCTURE.
- logTouchingAnotherSpeciesAborts.
- strippedLogDoesNotJoinTheFlood.
- persistentLeavesOnlyIsNotNatural.
- tooTallTreeIsRejectedBeforeTheFirstCut: top at dy 9 → TOO_TALL; dy 8 → OK.
- twoByTwoStumpIsFound: big = true.
- floodBoundsHold: ±7, +24, 96.
- acaciaDiagonalBranchesFollowed.
- resumeSkipsTheGroundRule.

FishingWaterTest (new, synthetic grid)
- farmChannelReplicaRejected: 1x5, depth 1, farmland on both sides.
- pond7x7x3Accepted: openWater true, stand found.
- puddle3x3AndPond4x4Rejected: body < 25.
- farmlandWithinFiveRejected: a 10x10x3 lake with farmland 4 blocks from the bobber.
- roofedPondRejected: no sky.
- standRules: solid floor, 2 clear, 2..5 away, 1..2 above, sight.
- openWaterReplica: lily pad counts as air, kelp as water, a mixed layer → false, air below water → false.
- flowingWaterIsNotABobber.
- waterKnownGate: VALID, UNSEARCHED, NONE fresh → false, NONE after 48000 → true.

ZonesTest (new)
- chebyshev.
- insideHalfMargins: 24+4 = 28 boundary.
- mapInsideAndClamp: radius 0 → always inside.

Unchanged: CaveSurveyTest, AffixRollerTest (REGROWTH stays woodcutter-only), PadsTest, LedgerTest, FoodPaymentTest, NpcHungerTest, ScoutRulesTest, StuckRecoveryTest, XpLevelsTest. The JUnit total goes from 40 to about 94; the README gets the exact Gradle count.

GAMETESTS (gametest/FCGameTests.java; helper gametest/GameTestBases.java)
Each job-running test has its own batch. Static checks share the batch 'workers_static'. Every registered base uses testHalf 2 and testRadius 11 around rel (12,2,12), so scans never leave the 24-block platform (neighbouring platforms are 29 blocks apart and are never cleared). Leaves are placed with DISTANCE 1 and PERSISTENT false, so they are natural but do not decay during the test.

Unchanged and required to pass, because they guard the base()==null idle rule and DigOut:
- npcClimbsABrokenMineStairwell (nearStairs and inStairwell stay unbounded);
- npcOutOfBlocksWalksBackOffItsBridge, npcOnTallTrunkChopsItsWayDown, sealedNpcDigsDownTowardALowerGoal;
- realMiningDropsIntoBackpack, minedOreExperienceGoesToTheFaction, breakTimesMatchPlayerFormula;
- factionAdvancementsFollowVanillaRequirements, recruitmentChargesFoodAndOneEquipmentRoute;
- all the others.

New, mining:
- minerDigsItsOwnShaftAndSealsTheGroundRing (batch mine_shaft, timeout 400).
  - Setup: stone at rel (3..9, 2..4, 3..8); STORAGE_STAND at rel (12,2,16); MINE via data.addWorkplace with mineV 1, hut false, origin rel (4,5,5), facing EAST (rotated), maxRounds 4, levels [{step 3, prog planSize(4), rounds 4}], cur 0, bottom 0, upgradeStep 0, assigned to the miner; water at local(2,-1,-1) and air at local(1,-1,-1); miner with a stone pickaxe and 16 cobblestone, registered.
  - Assert, when bottom == 3: the stair cells of steps 1..3 are passable; local(2,-1,-1) and local(1,-1,-1) are cobblestone. Then clean up at once, so the job idles before any planner or tunnel work.
- minerFindsNoSiteOnShallowGround (batch mine_nosite, timeout 200): a registered base in the flat world, a miner with a pickaxe and no MINE.
  - Assert: idleReason() contains 'too shallow'; no MINE with baseId b.id was added; the platform blocks in rel 0..23 x 1 x 0..23 are unchanged; the miner never left the platform for a survey point.
- minerNeverDigsProtectedBlocks (workers_static). WorkSafety.mayDig is false for: a chest; a planIndex block of a registered base with a StructureRecord; a cell inside that base's walls box at y0-10; another MINE's stair ring; a sculk shrieker; deepslate bricks. It is true for plain stone and for the own mine's stair ring.
- legacyWorkplacesLoad (workers_static): hand-built old tags, then WorkplaceState.load → save → load.
  - MINE tier 3, subLevel 1, upgradeStep 5, origin y 66 → hut, levels at the legacy steps, cur and upgradeStep kept.
  - TREE_PLOT tier 2, upgradeStep 3 → legacyFence true, upgradeStep -1, planted = old grid.
  - FISHERY with farm coordinates → spot UNSEARCHED; id, assigned and output kept.
  - An unknown type still returns null.

New, woodcutting:
- woodcutterFellsATreeAndReplantsTheSameSpecies (batch wood_fell, timeout 1200).
  - Setup: grass at rel (18,1,18); birch logs at y 2..6; natural birch leaves around the top; woodcutter with a stone axe carrying 2 birch and 2 oak saplings; registered TREE_PLOT (woodV 1).
  - Assert: no birch log in the column or the canopy box; at least 5 birch logs in the backpack or storage; a BIRCH sapling at (18,2,18); planted contains it; felled == 1.
- woodcutterLeavesProtectedWoodAlone (batch wood_protect, timeout 1000).
  - Setup: (a) a 4-log oak pillar with persistent leaves on grass; (b) a natural birch on stone; (c) a natural oak whose branch log touches an oak_planks block; (d) a plain natural oak.
  - Assert: (d) is felled while every log of (a), (b) and (c) remains.
- woodcutterDismantlesAnOldFence (batch wood_fence, timeout 1500).
  - Setup: TREE_PLOT legacyFence true, origin rel (5,2,12) facing EAST (rotated); OAK_FENCE on all 47 tier-1 ring posts.
  - Assert: no OAK_FENCE remains on the ring; fences in the backpack or storage >= 47 minus skipped posts; legacyFence == false.
- woodcutterResumesAfterReload (workers_static): save the job NBT after the first log of a tree is cut, then load it into a new WoodcutterJob → felling and spot kept, state CHOP. A tag without 'v' and with state UPGRADE loads as CHECK with spot null.

New, fishing:
- fishermanNeverPicksFarmIrrigation (workers_static).
  - Setup: farmland strip and a 1-wide, 5-long, 1-deep channel at rel x 5, z 9..13; a 7x7 pond at rel x 15..21, z 15..21, water y 2..3, stone rim y 2..3.
  - FishingSpots.search(level, data, b, b.center, 11, empty) → bobber inside the pond, stand standable, dry, 2..5 away, never in the channel.
- legacyFarmFisheryIsRelocated (workers_static): legacy FISHERY NBT pointing at the channel, plus the pond → after ensureWorkplaces and FishingSpots.maintain (forced), origin is in the pond, spot VALID, and id, assigned and output are unchanged.
- fishermanCatchesAtAPond (batch fish_catch, timeout 900): Lure III rod, VALID spot at the pond → totalOutput > 0 and a catch in the backpack.
- fishermanLeavesWhenTheWaterIsGone (batch fish_gone, timeout 900): after the first cast the bobber block becomes STONE → within 2 casts spot != VALID and bad contains the origin; the status never contains 'has no water'.
- noFishermanRecruitedWithoutWater (workers_static): low food and a rod in storage. FactionDirector.needs has no FISHERMAN with spot NONE and searched = now; it has one with spot VALID; none while barrenUntil(FISHERMAN) > now.
- openWaterUsesTheHookForLoot (workers_static): FishermanJob.lootEntity(true, npc) is a FishingHook with isOpenWaterFishing(); lootEntity(false, npc) is the npc.

New, shared:
- retrainingReleasesTheWorkplace (workers_static).
  - A fisherman holding the FISHERY calls n.role(MINER) → FISHERY.assigned == null.
  - A TREE_PLOT assigned to a living SCOUT record is unworked, and a woodcutter can claim it.
- claimSkipsOwnWorkedOutMine (workers_static): an NPC holds a workedOut MINE A and a free active MINE B exists → Workplaces.claim(npc, MINE, w -> !w.workedOut) returns B.

GameTest total goes from 47 to 63; the README is corrected (it claims 40 today).

CAMPAIGN CHECKS (step 8; dedicated server, fresh campaign, several in-game days, tools/sim.py)
- Every base has an active self-dug mine whose levels follow its picks: iron near y=16 with stone picks, deep levels near y=-56 once iron picks exist.
- No mine origin lies within half+8 of any base centre.
- No VALID FISHERY lies within half+4 of a centre or next to farmland.
- The woodland felled count keeps rising; logs of several species are in stock; there is never a lone dark oak sapling; old fences come down.
- No worker shows the same 'Blocked' status for more than 12000 ticks.
- The forced chunk count equals today's.
- Tick time stays within 1.5x of today's 3.9-6.4 ms.
- The old world_run3 save loads and migrates (see saveCompat).

## Implementation steps

1. SHARED GROUNDWORK. Gameplay is unchanged, except that retraining now frees workplaces and old-save holders are freed.
- work/Job.java: idleSince, markIdle(reason), markBusy, idleFor, idleReason, beforeErrand() hook before both atStorage() calls in survival(), isNight, remoteAtNight, routeFailures().
- work/MinerJob.java: drop its own idleSince/idleFor; markIdle in the 'No free mine' and 'fully worked out' branches, markBusy otherwise.
- work/Workplaces.java: own-first claim honours the predicate; holderValid with role fit; unworked uses it; release; releaseAll.
- npc/FactionNpc.java: role() calls releaseAll on a real change.
- campaign/BaseState.java: barrenUntil with its NBT; transient testHalf and testRadius.
- campaign/FactionDirector.java:
  - the replacement loop honours barrenUntil;
  - the MINER gate at :309 is removed;
  - retrainIdleWorker is generic (job.idleFor or missingToolFor), sets barrenUntil for idle workers, and its message uses idleReason.
- work/BlacksmithJob.java: members() from the roster replaces membersNearBase.
- config/FCConfig.java: the [workplaces] section with its 7 keys (nothing reads them yet).
- New gametest/GameTestBases.java.
- GameTests retrainingReleasesTheWorkplace and claimSkipsOwnWorkedOutMine.
- ./gradlew build test runGameTestServer stays green.

2. ZONES AND SAFETY on the existing jobs.
- New logic/Zones.java with ZonesTest.
- New campaign/BaseZones.java and campaign/MapBounds.java.
- New work/WorkSafety.java: mayDig, structural, protectedWood, and reserved (in this step reserved knows only MINE origins).
- New logic/TreeShape.java with TreeShapeTest.
- work/MinerJob.java: mayDig in mine() (a refused main-line cell ends the level, progress = plan.size(); a refused branch cell skips that branch side), in the vein (poll), in clear() (poll) and in walkInMine stair clearing (skip the cell). extend() is NOT changed yet, so the old considerGoingDeeper cannot loop.
- work/CaveMinerJob.java: mayDig at :341 and :445.
- work/WoodcutterJob.java:
  - collectLogs is replaced by TreeShape.survey through a world Cells adapter: same block only, structure-contact abort, protectedWood, abandon before the first cut when a log is above +8;
  - leafInTheWay and the leaves-underfoot rule skip persistent and planIndex leaves;
  - protectedSpot is replaced by WorkSafety.protectedWood for wild trees.
- GameTest minerNeverDigsProtectedBlocks.
- Build and tests green.

3. MINE LEVEL LIST, MIGRATION AND PLANNER, still on the fixed hut mine.
- New logic/LegacyMine.java: the old SpiralMine tier and infill code moved verbatim, plus migrate().
- logic/SpiralMine.java: remove the moved code; make landingAtOrBelow public; add landingAtOrAbove.
- New logic/MinePlanner.java.
- campaign/WorkplaceState.java: the MINE NBT block (mineV, hut, bottom, cur, shaftLimit, maxRounds, restTier, opened, levels ListTag, workedOut, note) with the legacy migration in load(); labels 'Mine' and maxTier 1.
- structure/MineLayout.java: the per-level API, shaftSteps, entrance by hut, inMine, inHut; level, deepest and landingSteps deleted; buildInitial reads LegacyMine.landingSteps(y, 1) for now; upgradePickTier deleted.
- work/MinerJob.java:
  - plan(wp) at STORAGE replaces considerGoingDeeper, blockedDeeper, exhausted, startLengthening and nextLengthening;
  - per-level progress; extend() from bottom to cur.step;
  - abandonDescent (mayDig, a stuck step after 2400 ticks, 3 seal failures) with shaftLimit;
  - throttled tier re-check in travel() and mine();
  - stairBlockedTicks leads to retire;
  - leaveMine uses inMine/inHut with a 1200-tick budget;
  - walkInMine uses shaftSteps and the first leg;
  - beforeErrand override via MineSites.mineAt;
  - caveWork in its new order.
- New work/MineSites.java with retire, rest, mineAt, openMines and corridorClear (corridor rectangles come from a minimal logic/MineSiteRules: corridorEnd, corridor, overlaps).
- work/BlacksmithJob.java: order 1 deleted.
- campaign/FactionDirector.java: 'an open mine has no miner'.
- campaign/BaseBuilder.java: the fixed mine gets hut=true and its levels from LegacyMine.migrate(y0,1,0,0,0,0,-1); sealMine uses LegacyMine.
- info/BaseInfo.java and command/FCCommands.java: mine line, JSON levels, mineDump.
- tools/sim.py: mine fields.
- Tests: SpiralMineTest trimmed; LegacyMineTest and MinePlannerTest added.
- GameTest legacyWorkplacesLoad (MINE part); npcClimbsABrokenMineStairwell unchanged.
- Build and tests green.

4. FREE-FORM SITES AND HUTLESS SHAFTS.
- logic/MineSiteRules.java completed (facingsAwayFrom, candidates, maxRoundsThatFit, score) with MineSiteRulesTest.
- work/MineSites.java: the incremental Search, evaluate (depth, collar, fluid, built blocks, zones, spacing, badSites, facings, corridorPenalty on MOTION_BLOCKING/OCEAN_FLOOR/MOTION_BLOCKING_NO_LEAVES), create.
- work/MinerJob.java:
  - the SITE state with LEAVE, UNLOAD, SEARCH, SURVEY and FAIL;
  - badSites and nextSite in the job NBT;
  - the hutless sealShaft ring rule;
  - the mouth torch;
  - unreachable-site retire.
- work/CaveWork.java: ownWorkings by corridor rectangles.
- work/WorkSafety.java: reserved(MINE_SITE) checks fishery origins and extents and woodland entries (in this step any FISHERY origin counts; it becomes VALID-only in step 5).
- campaign/BaseBuilder.java: remove the MINE workplace, buildInitial, sealMine, and the mine pad and skirt; MIN/MAX via BaseLayout.WORK_AREA_WEST/EAST (same numbers).
- structure/MineLayout.java: delete buildInitial and UPGRADE_TORCHES_PER_8_STEPS.
- structure/BaseLayout.java: delete MINE_ORIGIN_X and the east path; class doc.
- structure/SiteFinder.java: footprint maxX = HALF+2; delete mineAreaPenalty.
- campaign/CampaignStarter.java: doc only.
- GameTests minerDigsItsOwnShaftAndSealsTheGroundRing and minerFindsNoSiteOnShallowGround.
- Build and tests green.

5. FISHING ON REAL WATER.
- New logic/FishingWater.java with FishingWaterTest.
- campaign/WorkplaceState.java: the FISHERY NBT block (fishV, spot, open, searched, bad) with its legacy conversion.
- New work/FishingSpots.java: search, quick and full checks, maintain.
- work/FishermanJob.java rewritten:
  - states STORAGE, SEARCH, FORAGE, FISH, saved and honoured;
  - revalidation, travel give-up and bad spots;
  - land-only pickup within 3;
  - vanilla rain countdown bonus;
  - lootEntity with the FishingHook;
  - night rule, markIdle and markBusy;
  - workplaceInfo via held().
- campaign/BaseBuilder.java: ensureAnimalAndFishingWorkplaces renamed ensureWorkplaces (PEN unchanged; FISHERY UNSEARCHED placeholder; placeholder moved to the centre; never derived from the farm).
- campaign/FactionDirector.java: call ensureWorkplaces and FishingSpots.maintain; FISHERMAN need gated by waterKnown and barrenUntil.
- work/WorkSafety.java: reserved uses VALID fisheries only; FISH_STAND.
- info/BaseInfo.java, command/FCCommands.java and tools/sim.py: the fishery line and the fishery location check.
- GameTests: fishermanNeverPicksFarmIrrigation, legacyFarmFisheryIsRelocated, fishermanCatchesAtAPond, fishermanLeavesWhenTheWaterIsGone, noFishermanRecruitedWithoutWater, openWaterUsesTheHookForLoot, and the FISHERY part of legacyWorkplacesLoad.
- Build and tests green.

6. FENCELESS WOODLAND.
- New logic/ForestRules.java with ForestRulesTest.
- campaign/WorkplaceState.java: the TREE_PLOT NBT block (woodV, planted, replant, felled, legacyFence) with its legacy conversion; label 'Woodland', maxTier 1, upgradeStep -1.
- New work/ForestScan.java: findTree with rotating parity and 2x2 detection; the TreeShape world adapter; groveSpot; TreeClaims.
- work/WoodcutterJob.java rewritten:
  - states CHECK, STORAGE, CHOP, LEAVES, COLLECT, PLANT, BONEMEAL, DISMANTLE and the new decide() order;
  - closer approach, deferred logs and the stump-column final pass;
  - replant queue and grove;
  - bone meal over planted;
  - DISMANTLE;
  - forage anchored at the centre and clamped;
  - night rule; idle and barren;
  - v2 NBT;
  - every plot and upgrade method deleted; held() in workplaceInfo; keep() is 8 saplings per species and 16 bone meal.
- structure/TreePlotLayout.java: delete insideInner.
- campaign/BaseBuilder.java: no fence ring or workplaceManaged; 4 starter oak saplings on the lattice at (cx-35, cz±5) and (cx-40, cz±5), 2 of them grown, recorded in planted; TREE_PLOT woodV 1; ensureWorkplaces creates a missing TREE_PLOT.
- work/CookingJob.java: flat LOG_RESERVE (SmelterJob unchanged).
- work/WorkSafety.java: reserved(GROVE) and planted-sapling checks.
- info/BaseInfo.java, command/FCCommands.java, and tools/sim.py (all *_log and *_sapling).
- GameTests: woodcutterFellsATreeAndReplantsTheSameSpecies, woodcutterLeavesProtectedWoodAlone, woodcutterDismantlesAnOldFence, woodcutterResumesAfterReload, and the TREE_PLOT part of legacyWorkplacesLoad.
- Build and tests green.

7. CONFIG FILE AND DOCUMENTATION.
- docs/factionclash-common.toml: regenerated from FCConfig (the workplaces section, plus the war.pacts and comment fixes).
- campaign/CampaignData.java: FORMAT = 2, read and logged.
- README.md:
  - Mines: self-chosen sites, layers by need and pickaxe;
  - Cave mining: no 'constructed mine';
  - Tree plots becomes Woodland;
  - Fishing: real water;
  - Starting bases; Logistics wood reserve; recruitment and retraining (barren roles); blacksmith (no mine-upgrade pickaxes);
  - status rows; test counts;
  - the compatibility note replacing :405-406; fixed-seed runs labelled historical.
- BALANCE.md: Mines (layer table, site rules, caps), Woodland (search, natural rule, replant, dark oak, grove, belt), Wood and fuel, Terrain and sites, Animals and fishing (spot rules, treasure, rain, search, forage, NONE memory, night), Tool wear (roster crew), XP (+20 per layer and lengthening; no plot XP), and every PROPOSED constant.
- In-code docs:
  - WorkplaceState field and type docs, BaseLayout and BaseBuilder (ensureWorkplaces), SiteFinder;
  - MineLayout and SpiralMine class docs (no hut), TreePlotLayout (legacy only);
  - FishermanJob, CaveWork, CookingJob, and the NpcNavigator comment at :267.
- Build and tests green.

8. VERIFICATION AND PUBLISHING.
- ./gradlew build test runGameTestServer: every unit test and GameTest passes; record the real counts in the README.
- Load the old-format world in the scratchpad (world_run3) on the dedicated server and check the saveCompat expectations.
- Run a fresh multi-day campaign with tools/sim.py and check the campaign criteria listed under tests.
- Commit on the feature branch and push when the user asks.


## Risks

Deep travel and lava.
- Cost: a deep level is about 120 spiral steps down. That is about 20 walkInMine legs each way, and deepslate breaks at half speed, so ore per hour falls compared with today's fixed tiers.
- Lava: the lava aquifer level is y=-54, so tunnels at y=-56 can breach lava.
- Mitigations: 40-block rounds; the throttled tier check; beforeErrand, so errands climb out properly; preSeal plugs lava like water; abandonDescent after 3 seal failures; a cut main line ends that level.
- Watch miner deaths in the campaign. Raising the deep target to minY+11 is a one-constant change.

Mining stuck states. Each has an explicit exit:
- no site: survey walks, then idle, then retraining after 12000 ticks, then 48000 ticks of barren role;
- re-picking a failed site: badSites (32) plus retiring unreachable or unsafe sites;
- blocked descent (a protected cell, deep_dark, 3 seal failures, 2400 ticks on one step): shaftLimit and a re-plan, and a site with bottom < 6 is retired;
- protected stairs: 600 ticks, then retire and plain navigation;
- a pickaxe too weak for the level: a STORAGE trip at most every 2400 ticks;
- the own-first claim returning a finished mine: the predicate fix plus a single retire() writer.
Residual risk: a base surrounded by water or mountains, or squeezed by neighbours, ends up without a miner for 2 days at a time (one trial recruit every ~2.5 days).

Protection is stricter.
- Miners and cave miners now refuse block entities, structure blocks, other mines' stairs and the deep dark, so some tunnels and branch lines end early and yield less ore.
- Tunnels can still pass under player builds that contain no structure blocks or block entities, as today.

The hutless opening.
- The open 2x2 L-shaped mouth lets mobs, NPCs and players step into the spiral. Falls are at most 3 blocks, and the spiral can be walked out of.
- The mouth torch, the sealed ground ring, the no-surface-water rule and torches every 8 steps limit spawns and flooding.
- Snow layers on open steps are cleared by findBlocked/CLEAR.
- It is cosmetically less tidy than the hut; a builder-made cover could be added later.

Woodcutting coverage. Trees taller than 9 logs are left standing because they cannot be fully felled from the ground within TRUNK_REACH 7.5: mega spruce and jungle, about a third of jungle trees, some pines, super birches and fancy oaks. Mangroves, trees touching structures, and trees with bee nests are skipped too.
- A mega-taiga or jungle start gets wood more slowly from the smaller trees there and from the grove.
- This is documented as the woodcutter choosing trees it can fell whole.
- Nerd-poling was left out on purpose: it would turn DigOut's recovery teleport into routine work and strand NPCs on dirt pillars that DigOut.perched does not see.

Wood throughput changes. The fixed 4/9/16-tree plot is replaced by roaming, the replant queue and an 8-sapling grove in the forced belt. Replants outside the belt grow only near players, inside traveller tickets, or with bone meal. WOOD and FUEL alerts may fire more often early in a campaign; re-measure the ledgers.

Structure detection is a block list.
- Unusual builds made only of logs and natural leaves could still be cut.
- Villages the scouts never reported are protected only by the structure-contact abort and the rule against built blocks near a mine mouth.
- Trees in other factions' land beyond contains+8 are fair game.

Fishing availability.
- SiteFinder still prefers dry sites and Terrain.sealRing erases ponds next to pads, so many bases will have no qualifying water within 64 blocks. They will hire one trial fisherman every 2 days, and otherwise none: correct, but less variety of food.
- Remote spots are fished by day only beyond 16 blocks outside the walls, because guards cover only half+4.

Fishing loot. Open-water treasure (about 5% of catches: enchanted books, bows and rods, name tags, saddles, nautilus shells) changes gear supply; archers may prefer enchanted bows through gearScore. This is vanilla-faithful and can be turned off with fishingTreasure=false.

Night rule. Remote woodcutters and fishermen lose about a third of each day. Setting remoteWorkInDaylightOnly=false trades that for more deaths at remote spots.

Migration accuracy.
- The conversion relies on LegacyMine reproducing the old landing lists exactly. The code is moved verbatim and its old tests move with it.
- Migrated levels other than the current one start again at progress 0. They are fast-forwarded at 1 tick per dug cell; caved-in or flooded old tunnels cost face-watchdog time.
- An old woodcutter caught mid-UPGRADE leaves a partly moved ring; DISMANTLE checks every tier ring.

Record growth and cost.
- Worked-out MINE records are never deleted, so DigOut.isProtected, noticeOres and mayDig loop over a slowly growing list per dug cell. That is O(mines) with distance pre-checks, about one mine per miner every few days.
- BaseInfo summarises them on one line. Watch tick time in the campaign; a spatial index can be added later without changing behaviour.

Performance spikes. These all read loaded chunks only:
- the woodcutter's synchronous ~3200-column scan every 600 ticks (today's cost);
- the fisherman and director water searches (~3200 columns plus 12 floods, at most every 600 and 6000 ticks);
- the mine-site search (~780 candidates at 64 per tick, at most once per 6000 ticks per idle miner).

Survey and forage walks still make NpcNavigator generate chunks along the path (NpcNavigator.java:309, :335). Targets are clamped inside the campaign map, so generation stays within it, as for a walking player.

GameTest isolation. Neighbouring test platforms are only 29 blocks apart and are never cleared between batches.
- Every job test must use testHalf/testRadius, its own batch and GameTestBases clean-up (also scheduled for timeouts).
- A test that forgets this can pick up another test's trees or water, or leave a phantom base that changes zone checks.

Behaviour drift.
- Dropping the mine pad shrinks the SiteFinder footprint east to HALF+2, so the documented fixed-seed campaigns no longer reproduce; the README labels them historical.
- The forced east belt (to cx+78) now lies over unlevelled terrain. It costs the same number of chunks as today, but hosts only mines, trees and water that happen to be there.


## Decisions

### Q1 Save compatibility: migrate old campaigns, or declare them incompatible as README.md:405-406 did? Where does the migration run, and is FORMAT read?

**Decision.** Migrate.
- Pure conversions run in WorkplaceState.load, keyed on the absent markers 'mineV', 'woodV' and 'fishV':
  - LegacyMine.migrate rebuilds the old hut mine's levels from the frozen old landing math;
  - TREE_PLOT gets legacyFence and its old grid as planted;
  - FISHERY becomes UNSEARCHED.
- World follow-ups run in BaseBuilder.ensureWorkplaces (the renamed per-period hook at FactionDirector.java:76), FishingSpots.maintain and the jobs (DISMANTLE, SEARCH).
- FORMAT becomes 2. It is read and logged, but never used as a gate.

**Why.** The mod keeps long campaigns running, and the data needed to convert is fully present in the records. Keying on missing keys is idempotent and needs no world access, and the old SpiralMine math is moved verbatim, so migrated levels line up with the tunnels already dug. Gating on FORMAT would add a second source of truth for no benefit.

### Q2 Mine ownership: one active mine per miner or a per-base pool? What caps mines, what marks one finished, and is a finished mine released?

**Decision.** One active mine per miner (active = !workedOut && restTier == 0), claimed with an own-first claim that now honours the predicate.
- Each base may have maxOpenMines (3) active mines.
- Records are kept forever.
- MineSites.retire is the only writer of workedOut. It always sets assigned = null, and it runs when:
  - the planner returns WORKED_OUT;
  - the site is unreachable, unsafe (bottom < 6) or its stairs are blocked.
- REST (only a better pickaxe would help) also releases the mine, with restTier = 2; it can be reclaimed by a miner with an iron pickaxe or better.
- The 'open mine has no miner' need counts only active, unworked mines.

**Why.** Per-site records keep all existing geometry, DigOut, noticeOres and protection code keyed on MINE records unchanged. Release plus the claim fix closes critique gap 1. Keeping records preserves stair protection and corridor reservation.

### Q3 Where may a miner dig: distances, map, spacing, other bases, villages, fishery, saplings; do tunnels under the own base stay forbidden?

**Decision.** The origin lies in the Chebyshev ring HALF+8 (32) .. mineSiteRadius (64) around the base centre, inside the map (margin 16), and at least 24 from every MINE origin. It also needs:
- a flat, solid, dry collar, with no fluid or built block within 3;
- outside every walls box +8, other factions' areas +16, 48 from known villages, 12 from a valid fishery, 6 from planted saplings and replant entries;
- tunnels facing away from the base.
The corridor is reserved at the largest maxRounds that fits (f -2..2+40(r+1), s -11..12). It must not overlap:
- other mines' reserved corridors +2;
- any walls box +4, the own one included;
- other bases' MIN/MAX +8;
- lairs +16.
Tunnels under the own base stay forbidden, and WorkSafety also refuses digging within HALF+2 of any settlement from y0-48 up.

**Why.** Walls-box (HALF) rules avoid the contains() contradiction for old widened boxes. Reserving the full corridor at creation means lengthening never needs a later overlap fix; corridorClear stays as a backstop. These numbers are mutually consistent: at Chebyshev 32 the outward corridor's back edge is at 30, clear of the walls box +4, which ends at 28.

### Q4 What drives the layer choice; absolute or relative depths given flat GameTest worlds; when to open, deepen, lengthen or abandon?

**Decision.** logic/MinePlanner is pure.
- Inputs: storehouse counts (iron raw and ingots, diamonds, gold, lapis with a table, fuel or a FUEL/WOOD shortage) and the pickaxe tier in hand. A shaft limit caps the depth.
- Bands: COAL surface-6; IRON 16, LAPIS 0, GOLD -16 (absolute, as vanilla OrePlacements); DEEP minBuildHeight+8 (y=-56, aboveBottom-like, as diamonds and redstone).
- Rules: stickiness until the level's plan is done; then, per wanted band, reuse a level within 5 steps (work it, or lengthen it if finished), else open a new layer at least 6 steps away (dig down if deeper, at once if shallower); REST when only a better pickaxe helps; else WORKED_OUT.
- A descent is abandoned at a protected or structure cell, deep_dark, 3 seal failures or 2400 ticks on one step (shaftLimit). A cut main line ends a level.
- GameTests use pure unit tests; the flat world is asserted to give 'no layer'.

**Why.** It follows vanilla bands and harvest tiers like a survival player, and finishes a stretch before switching. Its numbers are testable without a world, and the deep layer is feasible for every surface height thanks to the landingAtOrAbove fallback.

### Q5 Shaft form, cover of the opening, and the entrance point leaveMine walks to?

**Decision.** Keep the 2x2 spiral and build no hut, roof, collar or trapdoor.
- sealShaft now plugs the ground ring at origin.y-1 for hutless mines.
- No surface fluid is allowed within 3 of the collar.
- A standing torch goes at the mouth.
- The entrance is the origin, stand(0), checked standable at site choice. Legacy hut mines keep the hut door.
- leaveMine uses inMine (the corridor and depth box) instead of the 3D 64-block radius.

**Why.** The spiral is what walkInMine, DigOut.climbStairs, nearStairs and sealing depend on. A 1-high collar does not stop mobs and fence gates block NPCs, so a torch and sealing give the real safety at no stall risk. A fall into the spiral is at most 3 blocks.

### Q6 Is the mining route only the shaft and branch plan, or free waypoints? What happens to caves and their priority?

**Decision.** The route is the spiral plus a branch plan on each level; there are no free waypoints. Surveyed caves and CaveMinerJob stay, with WorkSafety added. Priority for a miner without an active mine:
1. its held cave;
2. a claimable active or tier-eligible resting mine;
3. a marked cave;
4. a new site.
CaveWork.ownWorkings switches to corridor rectangles.

**Why.** Caves are already-surveyed ore, and the cave hiring need (MAX_CAVE_MINERS 2) expects cave hires to work caves, so caves before new sites avoids hire churn. Corridor-based ownWorkings keeps scouts and explorers from going blind as worked-out mines pile up.

### Q7 One digging-protection predicate for miners, cave miners and woodcutters; should it be depth-bounded and spatially indexed?

**Decision.** WorkSafety.mayDig(level, pos, own, ownMine) serves MinerJob and CaveMinerJob. It refuses:
- unbreakable blocks and block entities;
- structure blocks (bricks and tiles, the sculk family, reinforced deepslate, copper grates and bulbs);
- every planIndex;
- under any settlement from y0-48;
- lairs and foreign areas;
- other mines' stair rings;
- the deep_dark biome.
Woodcutters use protectedWood together with the TreeShape structure-contact abort. DigOut.isProtected stays unchanged. nearStairs and inStairwell stay unbounded, and there is no spatial index.

**Why.** One rule per kind of work, and DigOut's proven recovery rules stay as they are. Unbounded stairs stay protected during a long descent, whereas a bound on bottom would leave new stairs exposed. Record counts are small, distance pre-checks keep the cost low, and today's code already streams every workplace per cell.

### Q8 Retraining and recruitment when sites are unlimited; hire for ore shortages; does a role change release workplaces?

**Decision.** Job.idleFor is generic (markIdle and markBusy):
- miners are idle only with no site after the survey and no cave, or when every site slot is taken;
- woodcutters when there are no trees and no saplings;
- fishermen when there is no water.
retrainIdleWorker:
- applies to any non-combatant (the MINER gate is removed);
- every role change releases workplaces inside FactionNpc.role();
- an idle retrain sets a persisted base.barrenUntil[role] = now+48000, which suppresses 'replacing the missing X' and the fisherman need.
There is no ore-shortage hiring.

**Why.** This closes critique gaps 8 and 1, and avoids a retrain/re-hire loop into the same dead end. Persisting barrenUntil survives restarts, unlike robust's transient map. Ore-shortage hiring is beyond the owner's request and adds oscillation risk.

### Q9 Woodland record: keep TREE_PLOT as a fenceless per-base record or move the state into job NBT? How are trees claimed across woodcutters and factions?

**Decision.** Keep TREE_PLOT, one per base, labelled 'Woodland', claimed as today. It holds planted (64), replant (32), felled and legacyFence. Job NBT holds only the current tree, felling, badWild and forage. A transient TreeClaims map (stump to uuid, 1200 ticks, refreshed while chopping) stops two woodcutters of any faction from felling one trunk.

**Why.** The replant queue and planted saplings must survive a woodcutter's death or retraining. A per-base record does that, and loads from old saves with the same enum name.

### Q10 What does 'cut any tree as they like' include?

**Decision.** Included: every natural tree within forestRadius 64 of the centre that:
- has a trunk of the same log block (at least 3 logs) on #dirt;
- has non-persistent leaves within 2 of the top;
- touches no structure and no other species' log;
- has no log above stump+8;
- lies outside every walls box +2 (the own one included), outside other factions' areas +8 and lairs +16, at least 32 from known villages, and inside the map.
Oak, azalea (oak logs), birch, spruce, jungle, acacia, cherry and 2x2 dark oak are cut. Not cut: mangroves (trunks on roots), huge mushrooms, and trees taller than 9 logs, which are not fellable from the ground. Old plot trees and the old mine strip become fair game, and trees right outside the walls are cut.

**Why.** This matches the reach maths: from the stump column the highest reachable log is +8 (6.88 from the eyes, within 7.5). No floating logs are left and no new movement mechanic is needed. The movement rule allows only stuck-recovery teleports, so nerd-poling via DigOut.pillar was rejected. 'As they like' means the woodcutter chooses, and it chooses trees it can fell whole.

### Q11 Replant policy: same species, missing sapling, lone dark oak or mega saplings, Regrowth, groves and where?

**Decision.** Always the same species at the stump.
- Dark oak only as 2x2 with 4 saplings, never alone.
- Big spruce or jungle get 1 sapling at the NW corner, never 2x2, so no unfellable mega tree grows.
- With no matching sapling: wait and fetch it from storage. After 12000 ticks plant any carried 1x1 substitute (never dark oak). After 48000 ticks drop the entry.
- Regrowth rolls on every planting.
- Grove: 8 saplings on a 5-block lattice inside the force-ticked MIN/MAX belt.
  - Chebyshev at least HALF+4 from the centre; outside the road and gate strip;
  - #dirt ground (never farmland or path), open sky, nothing growing within 2;
  - away from mine origins and the fishery;
  - birch, then spruce, oak, cherry, acacia.

**Why.** A persisted queue means felling never waits for saplings, which ends today's livelock and keeps the forest the same species. The grove inside the forced belt grows under vanilla random ticks without players, which also covers treeless biomes.

### Q12 Sapling growth with no players, and the redefined wood reserve?

**Decision.** Keep the existing MIN/MAX numbers (cx-58..cx+78, cz±25) for old and new bases. The forced, randomly ticking belt is unchanged; the grove and nearby replants grow there. Other replants rely on bone meal, traveller tickets and players. woodReserve is a flat 16 (charcoal 24).

**Why.** This is the lowest-risk answer to contradiction 4: no new tickets, no migration, and contains(), deepInside(), the hauler and raids are unchanged. Vanilla saplings need random ticks, which forced ticking chunks get (ServerChunkCache.java:364).

### Q13 Old fenced plots: dismantle or leave; are old plot trees fair game?

**Decision.** Dismantle. DISMANTLE breaks every OAK_FENCE on the tier 1..3 rings that is not in planIndex with the Breaker, the fences go to storage, and a post is skipped after 300 ticks or 3 route failures. Then legacyFence = false. Old plot trees are natural trees and are fair game, because the own MIN/MAX box no longer protects anything.

**Why.** Fences block access and no longer have a purpose. Breaking them like a player returns real items, and builders use fences.

### Q14 New base layout and SiteFinder changes?

**Decision.** Drop:
- the mine pad, hut, sealMine and east path;
- the build-time MINE and mineAreaPenalty;
- the fence and workplaceManaged;
- the grid starters.
Keep:
- the levelled west pad as grass grove ground, and the west path;
- 4 starter oak saplings on lattice spots (cx-35/-40, cz±5), 2 of them pre-grown, recorded as planted;
- the MIN/MAX overwrite with identical numbers (BaseLayout.WORK_AREA_WEST and WORK_AREA_EAST).
SiteFinder footprint east becomes HALF+2 (west keeps the pad), and the dry-site preference is unchanged. Documented fixed-seed runs are labelled historical.

**Why.** The west pad gives grove ground in every biome without terrain edits, and the starters give wood on day 1. A water bonus in siting was left out to keep scope tight: fishing simply gates on real water.

### Q15 What counts as a proper fishing spot; search radius; forage; failure memory?

**Decision.** A bobber on a water source with air or a lily pad above and water below (at least 2 deep), under open sky, in a body of at least 25 surface sources. No farmland within 5 blocks (vanilla hydrates 4). Outside every walls box +4 (Chebyshev 28 or more), outside foreign areas +8, more than 24 from lairs, inside the map and within fishingRadius 64. A dry stand 2..5 away and 1..2 above, with line of sight. Open water is preferred. Rivers, lakes and the sea qualify; flowing water never does.
Search: step 2 with rotating parity, loaded chunks only, at most every 600 ticks per fisherman and 6000 per base for the director.
Forage: 8 directions 72 out, clamped, scanning 40 around.
After 8 failures: NONE plus searchedAt, remembered for 48000 ticks.

**Why.** Three independent rules (depth, body, farmland) and the walls rule each reject the farm channel and any irrigation. The director survey finds water before hiring, and the bounded forage finds it beyond loaded chunks.

### Q16 Treasure loot in open water; night return; avoid lairs and enemy bases?

**Decision.** Yes to vanilla treasure: in open water, and with config fishingTreasure, THIS_ENTITY is an un-spawned FishingHook (its public constructor; openWater defaults to true), and the vanilla rain bite bonus applies. The night rule applies with remoteWorkInDaylightOnly: fishermen and woodcutters do no work more than 16 blocks outside the walls between dayTime 13000 and 23000. Spots stay more than 24 from lairs and outside other factions' areas +8.

**Why.** Vanilla-faithful loot for the same water a player would fish. Guard coverage (defenceAlarm) is only HALF+4, so remote night work costs recruits. The window matches the TraderJob precedent.

### Q17 Fishery storage, the 'no water' state without a null origin, and how it is displayed?

**Decision.** Relocate the single FISHERY in place (id, assigned, totalOutput kept). spot is UNSEARCHED, VALID or NONE. While not VALID, origin = extent = base.center as a placeholder, never null. It is shown on the station line ('Fishing spot #id at x,y,z (open water)', 'looking for fishing water', 'no fishing water within reach (searched N days ago)') and in the JSON dump (origin, extent, spot, open, searched). No new map marker kinds.

**Why.** One record per base matches the single fisherman need and keeps old saves intact. A placeholder origin avoids changing WorkplaceState.save. Not touching MapPayload avoids a client/server protocol change.

### Q18 Config entries versus PROPOSED constants?

**Decision.** Seven FCConfig entries in [workplaces]: mineSiteRadius, maxOpenMines, forestRadius, groveSize, fishingRadius, fishingTreasure, remoteWorkInDaylightOnly. They are documented in a regenerated docs/factionclash-common.toml. Layer targets, stock thresholds, spacing, timers and caps stay PROPOSED constants in code, listed in BALANCE.md.

**Why.** Server owners can tune reach, load and loot. Constants tied to vanilla worldgen, such as ore bands and reach, would only invite inconsistent setups.

### Q19 GameTest isolation: idle without a base, no interference with shared arenas, which pure classes get unit tests?

**Decision.** Every job keeps an early base()==null idle guard. Site, tree and water searches run only with a base. New GameTests:
- register bases through GameTestBases with transient testHalf 2 and testRadius 11, so scans stay on the platform and forage is disabled;
- use their own batches (static checks share 'workers_static');
- clean up on every path, including a scheduled clean-up for timeouts.
The flat world is covered by pure tests and by a GameTest asserting 'too shallow, nothing dug'. Unit tests: MinePlanner, LegacyMine, MineSiteRules, ForestRules, TreeShape, FishingWater, Zones, and the trimmed SpiralMine.

**Why.** GameTest platforms sit only 29 blocks apart and are never cleared, and the world is flat near y=-60, where no layer is legal. So decisions must be pure and test scans must be bounded.

