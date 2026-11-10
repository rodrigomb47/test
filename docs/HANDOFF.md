# Handoff: state of the project and how to take over

Written 2026-11-09 at the end of a long working session, so a new session can continue without the old chat. Read
this file, then README.md (player-facing description, campaign results) and BALANCE.md (every number). Rules for
commits, language and testing are in `/CLAUDE.md`.

## 1. The owner and how they work

- Owner: rodrigomb47 (GitHub repo `rodrigomb47/test`). Portuguese speaker who asked for the chat
  and reports to be in **English** (automatic messages after a usage-limit pause may come in Portuguese; answer in English).
- They describe features in plain words and expect the agent to fill in the design sensibly ("do as you see fit").
  When an interpretation is uncertain, state it briefly and proceed; they confirm or correct.
- They explicitly allowed adding things the agent finds worthwhile ("if you find something that you want to add,
  like a npc role or an affix or anything, feel free to do so, also feel free if you want to improve something that i
  asked"). Say what was added.
- They usually work with "ultracode" on: substantial tasks are run as multi-agent workflows (design panel →
  implement step by step with a reviewer per step → adversarial review → fixes → campaign verification). Token cost
  is not their concern; correctness is.
- Usage limits sometimes interrupt a session; they come back with "continue from where you left off".

## 2. History of requests (newest last)

1. The original spec ("spec §1–§21", referenced in code comments as `spec N`): a campaign of 4 human factions
   (Ember Legion, Grove Covenant, Tide Alliance, Stoneguard) plus The Hollow (monster lairs) on a square map;
   safe starting settlements with a central reporting station, storage, workplaces; worker roles; scouts that
   only report what they saw; recruitment paid with real items; raids; levels and XP. Built as a "foundation
   build" first, then extended.
2. Player-like work: real block breaking at player speed, drops into backpacks, spiral-staircase mines with branch
   mining, real woodcutting and farming, animal carer/butcher/fisherman, cave explorers and cave miners, bridging,
   dig-out when stuck, hunger (vanilla food bar), tool/armour wear and anvil repair, enchanter, achievements
   (vanilla advancements per faction), logistics (shortages, haulers), custom combat behaviour.
3. Diplomacy and roles the agent added on its own: pacts against a conqueror, trader role (trades with villagers
   like a player), affixes (Thrifty, Hardy, Duelist, Angler, Haggler…), war council weighing gear.
4. **Worker redesign** (owner's words: "miners can dig down any place to create their mining route, and they will
   go layers down as they see fit, and woodcutters will no longer have a fence, they can cut and replant any tree as
   they like, also, the fisherman is using the water from the farm to fish, it shouldn't be like that"):
   - miners pick their own mine site and dig their own spiral shaft; layers chosen by stock needs and pickaxe;
   - woodcutters have no fenced plot; they fell natural trees around the base, replant the same species, and keep a
     grove (default 16 saplings) in the always-ticking work belt; old fences are taken down;
   - fishermen fish real water outside the walls (never farm irrigation), forage for water, open-water treasure.
   Design: `docs/design/worker-redesign-spec.md`; per-step notes and review/fix notes: `docs/design/notes/`.
5. **Scouts** (owner: "scouts should also be able to report villages, pillager outposts and woodland mansions, and
   they should be able to avoid confrontation with monsters without retreating home"): structure reports (new
   report kinds PILLAGER_OUTPOST, WOODLAND_MANSION; structure-based VILLAGE with trader counts), known
   outposts/mansions avoided by work sites and travel, scouts step aside from monsters and keep exploring. Choice
   made by the agent and told to the owner: enemy *soldiers* still send a scout home (urgent intel); easy to change
   if the owner prefers. Notes: `docs/design/notes/scouts.md`.
6. **Tall trees** (the owner's goal from request 4: woodcutters "can cut and replant any tree as they like"): trees too
   tall to fell from the ground are felled from a scaffold pillar, as a player nerd-poles (see §6 and
   `docs/design/notes/tall-trees.md`).

## 3. Architecture map (`src/main/java/com/factionclash`)

| Package | What lives there |
|---|---|
| `campaign/` | Saved campaign state and the per-base "brain". `CampaignData` (SavedData, FORMAT 2), `BaseState` (one settlement or lair: centre, points, MIN/MAX work belt, `barrenUntil`, shortages), `FactionState` (reports, explored sectors, raid target, pact), `WorkplaceState` (MINE / TREE_PLOT / FISHERY / FARM / PEN / CAVE records; per-type NBT blocks keyed `mineV`, `woodV`, `fishV`; old saves convert in `load`), `FactionDirector` (needs, recruitment, retraining, war council, `FishingSpots.maintain`), `BaseBuilder` (builds settlements; `ensureWorkplaces`), `CampaignStarter`/`CampaignManager`, `Diplomacy` (pacts), `Logistics`, `Report`, `BaseZones` + `MapBounds` (walls-box zone maths, map clamp). |
| `work/` | One `Job` per role (`Jobs.create`). `Job` = shared helpers: `goTo`, `atStorage`, survival errands, `markIdle/markBusy/idleFor`, `beforeErrand`, night rule. Big ones: `MinerJob` (+`MineSites` site search/retire/rest, `WorkSafety.mayDig`), `WoodcutterJob` (+`ForestScan`, which also holds `TreeClaims`; `Scaffold`, the dirt pillar it fells tall trees from), `FishermanJob` (+`FishingSpots`), `ScoutJob` (+`StructureSightings`), `CaveMinerJob`/`CaveWork`, `TraderJob`, `BlacksmithJob`, `BuilderJob`, `HaulerJob`, `CookingJob`/`ChefJob`/`SmelterJob`. `BlockWork` = player-speed breaking (`Breaker`), reach, wear. `Workplaces` = claim/held/release (role-aware holder check). |
| `logic/` | Pure, unit-tested rules (no Minecraft classes): `SpiralMine` (shaft + branch geometry), `MinePlanner` (which layer next), `MineSiteRules`, `LegacyMine` (old hut-mine maths, migration only), `TreeShape`, `ForestRules`, `FishingWater`, `EscapeRoute`, `StructureRules`, `Zones`, `CaveSurvey`, `NpcHunger`, `Ledger`, `FoodPayment`, `AffixRoller`, `XpLevels`, `StuckRecovery`, `ScoutRules`, `Pads`. |
| `npc/` | `FactionNpc` (the entity: inventory, hunger, gear, affixes, role changes release workplaces), `NpcNavigator` (travel/ARRIVED/MOVING/FAILED, stall and no-progress watchdogs, danger-zone waypoints), `DigOut` (digging/bridging/pillaring out when stuck; `perched` also knows a bare pillar), `NpcRole`, `ToolKind`. |
| `structure/` | `BaseLayout` (settlement blueprint: 49x49 walls, gates W/E, grove pad W, 10-block landing E, `WORK_AREA_WEST/EAST`), `Blueprint`, `Terrain` (level/skirt pads), `SiteFinder` (base sites), `MineLayout` (per-level mine geometry, `nearStairs`), `TreePlotLayout` (legacy fences only), `LairLayout`, `ProjectLayouts`. |
| `combat/` | Goals and rules: `CombatRules` (who is hostile, `sameSide` incl. pact allies), melee/ranged goals, `CivilianFleeGoal`, `CreeperEvadeGoal`, `WithdrawGoal`, `Alerts`. |
| `command/` | `FCCommands`: `/fc start [radius]`, `/fc status|join|leave|base|info|map|achievements`, admin: `/fc admin dump|npcs|spawn|recruit|info|probe|mine <id>|blocks <from> <to>|destroy`. |
| `config/` | `FCConfig` (NeoForge common config; sections campaign, movement, survival, achievements, workplaces, recruitment, war). |
| `info/`, `net/`, `client/` | Station/info screen text (`BaseInfo`), map payload (`MapPayload` marker kinds: … VILLAGE 7, PILLAGER_OUTPOST 8, WOODLAND_MANSION 9, STALE flag 16), client screens (`MapScreen`, `InfoScreen`). |
| `gametest/` | `FCGameTests` (all GameTests), `GameTestBases` (register an isolated test base, clean-up). |
| `tools/` (repo root) | `sim.py` (drive a server over RCON, print campaign summaries), `rcon.py`, `days.sh`, `setup_server.sh`, `gen_assets.py`. |

## 4. Campaign verification (do this for any behaviour change)

GameTests cannot show real terrain, real structures or multi-day economies; every significant change so far was
checked on a dedicated server, and several real bugs were only found there.

```bash
./gradlew build -x test                                   # builds build/libs/factionclash-0.1.0.jar
tools/setup_server.sh /path/to/server                      # installs NeoForge 21.1.209 once (needs maven.neoforged.net), copies the jar
cd /path/to/server && (nohup ./run.sh nogui > console.log 2>&1 &)   # wait for "Done (" in logs/latest.log
python3 tools/rcon.py "fc start 256"                       # new campaign, map radius 256 (prep takes ~1-2 min; log says "campaign started")
tools/days.sh /path/to/server 8 days.log                   # 8 in-game days, a full summary per day, "DONE" at the end
python3 tools/sim.py /path/to/server summary               # summary of the current state at any time
python3 tools/rcon.py "stop"
```

- The server uses seed 20261107, RCON port 25575, password `fctest` (`rcon.py` defaults). `/tick sprint` runs the
  game as fast as the CPU allows (~100-200 ticks/s; one in-game day ≈ 2-4 min). Delete `config/factionclash-common.toml`
  in the server folder to get fresh config defaults (an existing file keeps old values, e.g. `groveSize = 8`).
- `sim.py` summary: per faction (alive, NPC roles, reports, raids), per base (stock, every workplace with its
  levels/fields, recruitment line), status histogram of all NPCs, recoveries (stuck-recovery count; a high number
  on one NPC means it is stuck somewhere). `/fc admin dump` writes `world/factionclash_dump.json` and an `FC_DUMP`
  JSON line into `logs/latest.log` each time (good for tracking an NPC across days).
- Debugging an NPC: `python3 tools/rcon.py "data get entity <uuid>"` shows its saved job state (`FcJob`), status,
  position. `/fc admin mine <id>` logs a mine's levels and plan window; `/fc admin blocks x1 y1 z1 x2 y2 z2` logs a
  block map. For a stuck loop, a temporary `LOGGER.info` guarded by a `-D` system property (added to the server's
  `user_jvm_args.txt`) is the fastest way; remove it before committing.
- Old-save migration: keep a copy of a world from before a format change and load it with the new jar.
- Screenshots: `build.gradle` has a `clientViewer` run (a real client that joins `localhost:25565` as "Viewer",
  offline mode). With the server up: `Xvfb :99 -screen 0 1280x720x24 &`, then
  `DISPLAY=:99 ./gradlew runClientViewer &`; once "Viewer joined the game" is logged, use RCON (`op Viewer`,
  `gamemode spectator Viewer`, `execute as Viewer run fc join <faction>`, `tp Viewer x y z yaw pitch`,
  `execute as Viewer run fc info` / `fc map` to open the screens) and capture with `DISPLAY=:99 import -window
  root shot.png`; the game window is the 854x480 box at (213,120) of the 1280x720 screen.
- `/locate structure minecraft:pillager_outpost` (or `#minecraft:village`, `minecraft:mansion`) via RCON finds
  structures to test scouts; a scout can be teleported next to one (`tp <uuid> x y z`) and back home to deliver.

## 5. State at handoff (branch head after commit 9ee3f4a + this handoff)

- 139 unit tests, 94 GameTests, all green. Download jar rebuilt; SHA-256 on README line 13.
- After the tall-trees change (scaffold pillars, request 6), its review fixes and the live fixes from its campaign check:
  **145 unit tests, 108 GameTests**, all green; jar rebuilt (SHA-256 on README line 13). Checked in a fresh 14-day
  campaign with 21 hand-grown trees (`docs/design/notes/tall-trees.md` → "Live check"): 13 hand-grown tall trees and,
  over days 7 to 13, 29 tall regrown trees felled from pillars 1 to 19 high, no log or dirt left, no woodcutter hurt
  or fallen, a restart mid-pillar resumed. Three faults found there are fixed: vines on giant jungle trunks, a branch
  end in the wall margin, and a suffocation from an instant Regrowth.
- Final 8-day fresh campaign (results in README "The final check"): 11 self-chosen mines, all shafts reached the
  diamond layer through aquifers; 104-247 trees felled per base; all fisheries on real water; scouts reported a real
  village (2 traders) and, when placed by an outpost by hand, the outpost; 4.6-10.4 ms per tick.
- An old-format world (day 19 of an earlier campaign) loaded and converted: hut mine levels migrated and worked on,
  fences taken down, fishing spots moved off the farm.

## 6. Known open items and ideas (none blocking)

- Movement (see `docs/design/notes/stuck-pits.md`): NPCs that fall into a ravine beside a base now climb out
  (dig-out continues until a walking route exists, ways up first); waypoints and partial paths avoid pits; new
  bases slope the ground beside the walls even over ravines. Left: the gate choice does not know about pits; the
  dig-out escalation rounds (`DigOut.escapeHeading`) have no GameTest (never needed live); NPCs on tree canopies
  get down only through the "perched" rule after a stall.
- Builders keep 24 cobblestone/cobbled deepslate/blackstone for stone tools (a base once built its last cobble
  away and its mine rested forever).

- A base keeps at most `maxOpenMines` (3) active mines; extra miners stay idle ("Every mine site of the base is
  taken") until the base needs another role, then they are retrained. A second miner sharing a mine could be added.
- Tall trees (request 6, `docs/design/notes/tall-trees.md`), what is left:
  - **An unexplained trunk loss.** One hand-grown giant jungle tree lost its trunk below the crown with no
    woodcutter, builder or miner near it. The crown and 8 logs were left floating. It looks like fire under the
    canopy in a thunderstorm; watch for it.
  - **Dirt supply depends on the ground.** Ember's miners brought 727 dirt, but Grove had 21 and Stoneguard none. A
    woodcutter keeps up to 32 once fetched, while builders also use dirt.
  - **Mega tree farms.** A 2x2 stump replanted as four saplings grows a mega tree again (bone meal), so the same 10
    sites gave 29 fellings in six days. That is good wood, but woodcutters spend most of their time there.
  - **Still left standing:** mangroves, huge mushrooms, trees no single pillar reaches, and trees with any log in
    protected wood.
  - **A woodcutter that dies on its pillar** leaves the half-felled tree on its stump. Its pillar is taken down by the
    next woodcutter.
  - **No direct test for the deferred Regrowth.** The deferral waits until the woodcutter is out of the trunk's room.
    It is random, so it has no GameTest of its own; the bone meal GameTest covers the same room check.
  - **A possible flake.** `woodcutterComesDownItsScaffoldForAnErrand` failed once in about 12 suite runs. That run
    was unusual: a broken test was spinning in it.
- Scouts retreat home from enemy *soldiers* (by choice, see §2.5). The drowned rule (only trident drowned are
  archers; swimming monsters count only when close or hunting) came after the last campaign: re-check in the next
  one that scouts no longer make hundreds of sidesteps along rivers.
- Structure detection was verified live for villages and an outpost (hand-placed scout); woodland mansions only by
  GameTest (none within reach of the test maps). The map legend and markers were checked on a real client.
- A base with no needs keeps a fisherman whose rod broke until string comes in (by design, documented).
- A woodcutter's long walk to a far tree is not restarted after a survival errand (it can skip the tree for 12000
  ticks). `MinerJob.site()` counts storage pickaxes every tick while a mine rests (cheap).
- Ideas not started: water bonus in base siting, raids on known outposts, headframes/covers over mine mouths.

## 7. Release checklist

1. `./gradlew build` + `./gradlew test` + GameTests green; campaign check for behaviour changes.
2. README/BALANCE updated (test counts in README "Testing"), `docs/factionclash-common.toml` regenerated if config
   changed (copy the file a fresh server writes).
3. `cp build/libs/factionclash-0.1.0.jar download/` and put `sha256sum` on README line 13 (`SHA-256: \`...\``).
4. Commit with the rules in `/CLAUDE.md`, push, report to the owner in English (what changed, what was verified,
   what is left).
