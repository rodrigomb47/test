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
7. **Soldiers that cannot reach a player** (owner, playing: a player in an animal pen, or behind a broken door with a
   block put in the doorway, killed soldiers that stood within reach unable to do anything). Soldiers now open fence
   gates (closing them behind), break blocks in the way that are not anyone's planned building, build themselves up or
   break the block under a player on a pillar, and otherwise wait out of reach, watching, calling the archers
   (`docs/design/notes/combat-unreachable.md`). Checked live with a real client as the player (that note's "Live
   check"): pen, broken doorway, pillar, hole; five faults found there were fixed (pen exit after a long fight, a
   factionless player's blocks, the pit trap, standing in the opened doorway, broken doors never repaired). A second
   live check in a fresh campaign ("Live check 2") fixed three more: a player against a pen's far fence (soldiers now
   go round through the gate), home guards held for good on a monster they could not get at, and wounded workers
   shut in a pit with the player.

## 3. Architecture map (`src/main/java/com/factionclash`)

| Package | What lives there |
|---|---|
| `campaign/` | Saved campaign state and the per-base "brain". `CampaignData` (SavedData, FORMAT 2), `BaseState` (one settlement or lair: centre, points, MIN/MAX work belt, `barrenUntil`, shortages), `FactionState` (reports, explored sectors, raid target, pact), `WorkplaceState` (MINE / TREE_PLOT / FISHERY / FARM / PEN / CAVE records; per-type NBT blocks keyed `mineV`, `woodV`, `fishV`; old saves convert in `load`), `FactionDirector` (needs, recruitment, retraining, war council, `FishingSpots.maintain`), `BaseBuilder` (builds settlements; `ensureWorkplaces`), `CampaignStarter`/`CampaignManager`, `Diplomacy` (pacts), `Logistics`, `Report`, `BaseZones` + `MapBounds` (walls-box zone maths, map clamp). |
| `work/` | One `Job` per role (`Jobs.create`). `Job` = shared helpers: `goTo`, `atStorage`, survival errands, `markIdle/markBusy/idleFor`, `beforeErrand`, night rule. Big ones: `MinerJob` (+`MineSites` site search/retire/rest, `WorkSafety.mayDig`), `WoodcutterJob` (+`ForestScan`, which also holds `TreeClaims`; `Scaffold`, the dirt pillar it fells tall trees from), `FishermanJob` (+`FishingSpots`), `ScoutJob` (+`StructureSightings`), `CaveMinerJob`/`CaveWork`, `TraderJob`, `BlacksmithJob`, `BuilderJob`, `HaulerJob`, `CookingJob`/`ChefJob`/`SmelterJob`. `BlockWork` = player-speed breaking (`Breaker`), reach, wear. `Workplaces` = claim/held/release (role-aware holder check). |
| `logic/` | Pure, unit-tested rules (no Minecraft classes): `SpiralMine` (shaft + branch geometry), `MinePlanner` (which layer next), `MineSiteRules`, `LegacyMine` (old hut-mine maths, migration only), `TreeShape`, `ForestRules`, `FishingWater`, `EscapeRoute`, `StructureRules`, `Zones`, `CaveSurvey`, `NpcHunger`, `Ledger`, `FoodPayment`, `AffixRoller`, `XpLevels`, `StuckRecovery`, `ScoutRules`, `Pads`. |
| `npc/` | `FactionNpc` (the entity: inventory, hunger, gear, affixes, role changes release workplaces), `NpcNavigator` (travel/ARRIVED/MOVING/FAILED, stall and no-progress watchdogs, danger-zone waypoints), `DigOut` (digging/bridging/pillaring out when stuck; `perched` also knows a bare pillar), `NpcRole`, `ToolKind`. |
| `structure/` | `BaseLayout` (settlement blueprint: 49x49 walls, gates W/E, grove pad W, 10-block landing E, `WORK_AREA_WEST/EAST`), `Blueprint`, `Terrain` (level/skirt pads), `SiteFinder` (base sites), `MineLayout` (per-level mine geometry, `nearStairs`), `TreePlotLayout` (legacy fences only), `LairLayout`, `ProjectLayouts`. |
| `combat/` | Goals and rules: `CombatRules` (who is hostile, `sameSide` incl. pact allies), melee/ranged goals, `CivilianFleeGoal`, `CreeperEvadeGoal`, `WithdrawGoal`, `Alerts`. `Pursuit` (per NPC: a target it cannot walk up to: work through a `logic/ReachPlanner` plan or wait out of reach), `Reach` (what a fighter may break or build), `OpenGateGoal` (fence gates on a fighting NPC's path; `npc/NpcPathNavigation` makes them passable while fighting). |
| `command/` | `FCCommands`: `/fc start [radius]`, `/fc status|join|leave|base|info|map|achievements`, admin: `/fc admin dump|npcs|spawn|recruit|info|probe|mine <id>|blocks <from> <to>|destroy`. |
| `config/` | `FCConfig` (NeoForge common config; sections campaign, movement, survival, achievements, workplaces, recruitment, war). |
| `info/`, `net/`, `client/` | Station/info screen text (`BaseInfo`), map payload (`MapPayload` marker kinds: … VILLAGE 7, PILLAGER_OUTPOST 8, WOODLAND_MANSION 9, STALE flag 16), client screens (`MapScreen`, `InfoScreen`). |
| `gametest/` | `FCGameTests` (all GameTests), `GameTestBases` (register an isolated test base, clean-up). |
| `hero/` (Heroes v2, `docs/heroes-v2/HEROES_V2.md`) | `HeroEntity` (a `FactionNpc` subclass and GeckoLib `GeoEntity`: hero id and the synced fields `DATA_HERO/ACTION/STATE/MOOD/LOCO`, hitbox from the model, kit never dropped, `FcHero` NBT), `HeroRegistry` (every `assets/factionclash/heroes/*.visual.json` read from the mod file, config overrides in `config/factionclash/heroes/`, `registerTransient` fixtures for GameTests), `HeroVisual`/`HeroEntry` (what a hero looks like and where its files are), `HeroManager` (give, release, death and revival after `heroes.reviveSeconds`, one living entity per id, preview copies), `HeroKit`, `HeroEvents`, `HeroLook` (the visual file's bone groups, face table, blink, phases, move names, chains), `HeroAnimator` (the three GeckoLib controllers, common code), `HeroTimingFiles` (the server timing files through a hero's chain). Preview fixtures: `config/factionclash/hero_fixtures/*.visual.json` on a server. Campaign record: `campaign/HeroState` in `CampaignData.heroes` (format 3); a hero's roster `NpcRecord` has `heroId`. Pure rules in `logic/hero/` (`HeroSize`, `HeroActions`, `HeroIdentity`, `HeroRevival`, `FaceRules`, `BlinkSchedule`, `FaceClock`, `HeroLod`, `HeroClips`, `Sway`, `MoodRules`, `HeroTimings`, `JsonTree`); commands in `command/HeroCommands` (`/fc hero list\|info`, `/fc admin hero give\|release\|revive\|preview\|lineup\|pose`); GameTests in `gametest/HeroGameTests` and `HeroLookGameTests`. |
| `client/hero/` | `HeroModel` (files and the animation chain with missing files dropped, `_fallback` for a missing model; after the animation pass: head look, face, flinch, ears, chain sway, bone visibility, LOD), `HeroRenderer` (face-frame UV shift, sash tint, layers, `-Dfactionclash.heroRenderStats`), `HeroFace`, `HeroClientState`, `HeroGlowLayer`, `HeroItemLayer`, `ClientHeroVisuals` (a resource pack's hero visual files become preview fixtures on the client). HEROES_V2 15.2 "A2 as built". |
| `tools/` (repo root) | `sim.py` (drive a server over RCON, print campaign summaries), `rcon.py`, `days.sh`, `setup_server.sh`, `gen_assets.py`. |
| `libs/maven/` (repo root) | Vendored GeckoLib 4.9.3 (the hero models' animation library; jar, POM, `.module`, a hand-written `maven-metadata.xml`, `LICENSE-geckolib.txt`). The build reads the group `software.bernie.geckolib` only from here and embeds the jar (jar-in-jar). Hashes in section 5. |

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
- **GeckoLib vendored and embedded** (Heroes v2 task A0, 2026-11-12, `docs/heroes-v2/HEROES_V2.md` 4.2). Downloaded
  once from `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` (HTTP 200) into
  `libs/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/4.9.3/`. Checked after the download, all equal to the
  values the design recorded and to the published `.sha1`/`.md5` files:
  - `geckolib-neoforge-1.21.1-4.9.3.jar`: 634,531 bytes, SHA-1 `5cfe835472a5c5457ae9ac192d9d7b317454d5ac`,
    SHA-256 `20a1995e4074f387ff549e2c57ea79dd7d41ee83c6afb9dd6a6e840e592d1c47`, MD5 `ddb8a5ef680c3a1df09ef98dc8b7c116`
    (its `.module` file lists the same SHA-1, SHA-256 and MD5);
  - `.pom`: SHA-1 `1584949c103cdc35f1cc4f74943a43be2b06210e`, SHA-256
    `eedbe316a4139d5e3bee192dde83eda793a26a1b3c9e91810dbb9ee22c95207f`;
  - `.module`: SHA-1 `dac3d3e8823ea8008a8216269416a776f8d62bc0`, SHA-256
    `620d417479935e29fc6b8474a653824853b3f543bbdea206d3adb0216d9f2b7d` (no dependencies in any variant);
  - `maven-metadata.xml` is hand-written (lists only 4.9.3); `libs/maven/LICENSE-geckolib.txt` and
    `src/main/resources/META-INF/licenses/geckolib-LICENSE.txt` are the jar's own `GECKOLIB_LICENSE` (MIT).

  Re-check with `sha256sum libs/maven/software/bernie/geckolib/geckolib-neoforge-1.21.1/4.9.3/*`. Versions:
  `geckolib_version=4.9.3` and `geckolib_version_range=[4.9.3,4.10)` in `gradle.properties`; the mods.toml template
  (`src/main/templates/META-INF/neoforge.mods.toml`) requires `geckolib` in that range on both sides. The mod jar
  carries `META-INF/jarjar/geckolib-neoforge-1.21.1-4.9.3.jar` (byte-identical to the vendored one, 0.63 MB of the
  jar's 1.64 MB). Verified: 174 unit tests and 137 GameTests green (one earlier run lost
  `soldiersLeaveThePenThroughTheGateAfterALongFight`, the known flake of `docs/design/notes/combat-unreachable.md`,
  while a server was starting beside it; the next two runs passed); a dedicated server from `tools/setup_server.sh`
  with only our jar in `mods/` logs `Found mod file "geckolib-neoforge-1.21.1-4.9.3.jar" [parent: factionclash-0.1.0.jar,
  locator: jarinjar ...]` and lists `GeckoLib 4 4.9.3 (geckolib)`; the Xvfb `clientViewer` loads GeckoLib and joins
  it. In development runs (`runClient`, `runClientViewer`, `runGameTestServer`) GeckoLib comes from the classpath (the
  vendored jar), not from inside our jar; only an installed server or client uses the jar-in-jar copy. GeckoLib's
  log line `Reference map 'geckolib.refmap.json' ... could not be read` appears in both and is harmless (NeoForge runs on
  Mojang's names, so no reference map is needed). The download jar (README line 13) was not rebuilt for this: it has no
  GeckoLib until the next release.
- **The hero entity** (Heroes v2 task A1a, 2026-11-12; `HEROES_V2.md` 15.2 has the as-built notes). A hero is a
  `factionclash:hero` entity: a faction NPC that counts as one soldier of the role its weapon style fits (the test rig's
  katana: swordsman) for needs, garrison and war council, is never drafted into a raid party or retrained, fights with
  that role's basic attacks and a bound kit weapon, and has a hitbox from its model (test rig 1.82 blocks; floor 1.2,
  cap 1.98). Only the art pipeline's `test_rig` exists so far; operators give it with `/fc admin hero give test_rig
  <faction> [pos]` (from RCON it appears at the base's spawn point). **191 unit tests and 145 GameTests** (8 new: the
  registry, synced fields, size, the raid rule, uniqueness, a fight, saving and death/return). Checked on a dedicated
  server built by `tools/setup_server.sh` (fresh world, `fc start 256`): the registry logs `Heroes: 1 loaded (test_rig)`
  with no problems; the test rig, given to Ember, killed a summoned husk (`slain by Test Rig` and Ember's Monster Hunter
  advancement credited to it); after `save-all` and a restart it was back with the same UUID and kit; `/kill` gave
  "has fallen ... returns in 10:00", no sword on the ground, `/fc hero list` "returns in 9:58", and
  `/fc admin hero revive` brought it back as a new entity (one hero entity in the world throughout); `release` removed
  it. The Xvfb viewer drew it next to a swordsman with hitboxes on (F3+B): `docs/screenshots/hero_test_rig_a1a.png`
  (bind pose; the phase-2 halo still shows until A2 sets bone visibility). `docs/factionclash-common.toml` was
  regenerated (new `[heroes] reviveSeconds`). The download jar was not rebuilt (heroes are not playable yet).
  Six full GameTest runs, with the art workflow's Python jobs loading the machine: three green (the last two on the
  final code), three lost one or two combat tests that ran with no hero batch beside them (batches run one at a
  time): `soldiersHoldOutOfReachOfAPlayerTheyCannotReach`, `soldiersLeaveThePenThroughTheGateAfterALongFight` and
  `soldiersDoNotKeepBreakingABlockThePlayerPutsBack` (known flakes, `docs/design/notes/combat-unreachable.md`), and
  once `aGuardWaitingOnAMonsterTurnsToAnIntruder` (the guard was still in CHASE, not HOLD, at its tick-100 check; not
  seen failing before; watch it).
- **The hero client model** (Heroes v2 task A2, 2026-11-12; checkpoint 1 "pipeline alive" with A0 and A1a; `HEROES_V2.md`
  15.2 "A2 as built"). Heroes animate: three GeckoLib controllers (`hero/HeroAnimator`: base from how the hero moves,
  action from `DATA_ACTION` with the timing files' blends, secondary), a face that blinks, talks and shows 12 moods
  (`client/hero/HeroFace` on pure `FaceRules`/`BlinkSchedule`/`FaceClock`), the faction-tinted sash, the glow mask,
  vanilla tools in the hands, phase looks and LOD. **230 unit tests and 149 GameTests** (39 and 4 new). Checked in the
  Xvfb viewer against a dedicated server from `tools/setup_server.sh` (the A1a world): the test rig idles, walks
  (forced with `/fc admin hero pose test_rig base walk`), swings (`hit` logged at animation tick 10.36-10.72 for the
  file's 10.00, at 1x and 2x), blinks (caught in a frame burst), shows all 12 moods, talks, takes each faction's
  sash colour, holds a pickaxe and mines, shows the phase-2 halo and glowing eyes (day and night) and drops its
  `fine_` bones beyond 20 blocks; the art pipeline's six style fixtures stand beside it in a lineup. Screenshots and
  GIFs: `docs/screenshots/hero_a2_*`. Screenshot tools: `/fc admin hero preview|lineup|pose` (operators; previews are
  display copies the campaign never counts). To show the style fixtures: build them into a scratch folder with the
  art pipeline's own `build.build_files(id)` / `build.write_files(files, root=...)` (a short Python script with
  `tools/herogen` on its path; nothing there is edited), copy their `visual.json` files to the server's
  `config/factionclash/hero_fixtures/`, zip the `assets` folder with a `pack.mcmeta` (format 34) into
  `run/resourcepacks/` and add it to `resourcePacks` in `run/options.txt`. The `clientViewer` run now logs hero markers
  and render times (`factionclash.heroMarkers`, `factionclash.heroRenderStats`). Found in the viewer and fixed: a
  restarted action was dropped on its first frame (GeckoLib still reports the previous clip as finished), and
  offsets added after GeckoLib's pass built up frame after frame (GeckoLib leaves a bone its setters marked as
  changed; heads slowly turned round). Measured 0.84-2.3 ms per hero render on the software-GL viewer.

## 6. Known open items and ideas (none blocking)

- Movement (see `docs/design/notes/stuck-pits.md`): NPCs that fall into a ravine beside a base now climb out
  (dig-out continues until a walking route exists, ways up first); waypoints and partial paths avoid pits; new
  bases slope the ground beside the walls even over ravines. Left: the gate choice does not know about pits; the
  dig-out escalation rounds (`DigOut.escapeHeading`) have no GameTest (never needed live); NPCs on tree canopies
  get down only through the "perched" rule after a stall.
- Builders keep 24 cobblestone/cobbled deepslate/blackstone for stone tools (a base once built its last cobble
  away and its mine rested forever).

- Soldiers against a target they cannot walk up to (request 7, `docs/design/notes/combat-unreachable.md`): tested
  (174 unit tests, 137 GameTests, sabotage checks), reviewed (a server crash in `OpenGateGoal` fixed; a 1x1 hole,
  a block put back in a doorway, a player just behind a fence, soldiers shut in a pen after the fight, arrows while
  waiting, workers, allies' edits: see the notes' "Review" section), and **checked live with a real client as the
  player** (the notes' "Live check": pen, doorway, pillar, hole, pit; the pit trap, the pen exit after a long fight,
  a factionless player's pillar, a 2 s wait in the opened doorway and never-repaired doors were fixed there). Not yet
  checked in a multi-day campaign with a player, nor a player on a wall or roof, or arrows. Found on the way: the melee
  swing cooldown is 20 ticks for every weapon (vanilla's goal ignored the weapon interval the docs describe); kept, the
  owner's choice. A gate left open by an NPC that dies in it stays open until the animal carer next goes through it.
  Soldiers (and archers) jump into a player's 1x1 hole and fight there; a pit left in a yard is not repaired (not
  part of any plan). Second live check ("Live check 2"): fixed the pen's far fence, monsters held at home and wounded
  workers in a pit; left for the owner: a lone soldier walking straight at a player who hits it every 12 ticks is
  kept off by the knock-back (vanilla mobs too; a player's 3-block melee reach for soldiers would end it but changes
  every fight's balance), and animals can slip out of a pen while many soldiers go in and out over a long fight (the
  carer brings them back). Live testing tip: `/fc admin dump` now gives each NPC's exact position (`xyz`), its `pursuit`
  mode and counters and its `path`; a client can be driven with `xdotool` (installable with apt) to place blocks and
  hit (`tp` with explicit yaw/pitch to aim; `tp ... facing` takes the feet, not the eyes).
- A base keeps at most `maxOpenMines` (3) active mines. Roadmap phase 1: a miner left without a site now helps in
  an open mine of its base, on another level (`docs/design/notes/phase1.md`, "Two miners share a mine"); it is still
  retrained after half a day without a mine of its own when the base needs another role. Tested (160 unit tests,
  116 GameTests); not yet checked in a campaign.
- Tall trees (request 6, `docs/design/notes/tall-trees.md`), what is left:
  - **An unexplained trunk loss.** One hand-grown giant jungle tree lost its trunk below the crown with no
    woodcutter, builder or miner near it. The crown and 8 logs were left floating. It looks like fire under the
    canopy in a thunderstorm; watch for it.
  - **Dirt supply depends on the ground.** Ember's miners brought 727 dirt, but Grove had 21 and Stoneguard none. A
    woodcutter keeps up to 32 once fetched, while builders also use dirt. Roadmap phase 1: a woodcutter now digs its
    own from flat open ground when the storehouse has too little (`docs/design/notes/phase1.md`); a base on bare stone
    still has none. Reviewed (night rule, unreachable patches, the blacksmith's axe count fixed); not yet checked
    in a campaign.
  - **Mega tree farms.** A 2x2 stump replanted as four saplings grows a mega tree again (bone meal), so the same 10
    sites gave 29 fellings in six days. That is good wood, but woodcutters spend most of their time there.
  - **Still left standing:** mangroves, huge mushrooms, trees no single pillar reaches, and trees with any log in
    protected wood.
  - **A woodcutter that dies on its pillar** leaves the half-felled tree on its stump. Its pillar is taken down by the
    next woodcutter.
  - **No direct test for the deferred Regrowth.** The deferral waits until the woodcutter is out of the trunk's room.
    It is random, so it has no GameTest of its own; the bone meal GameTest covers the same room check.
  - **The flake is explained.** `woodcutterComesDownItsScaffoldForAnErrand` failed when the suite reached night at
    its start: the woodcutter went home after one empty search pattern and packed bread, so it later ate on its pillar
    instead of coming down. The test now takes such food back, and `decide()` finishes the search before going home
    for the night (`docs/design/notes/phase1.md`).
- Scouts retreat home from enemy *soldiers* (by choice, see §2.5). The drowned rule (only trident drowned are
  archers; swimming monsters count only when close or hunting) came after the last campaign: re-check in the next
  one that scouts no longer make hundreds of sidesteps along rivers.
- Structure detection was verified live for villages and an outpost (hand-placed scout); woodland mansions only by
  GameTest (none within reach of the test maps). The map legend and markers were checked on a real client.
- A base with no needs keeps a fisherman whose rod broke until string comes in (by design, documented).
- A woodcutter's long walk to a far tree is not restarted after a survival errand (it can skip the tree for 12000
  ticks). `MinerJob.site()` counts storage pickaxes every tick while a mine rests (cheap).
- The agreed plan for what comes next is `docs/ROADMAP.md` (phases 1-5; keep its Status column current). Smaller
  ideas not on it: water bonus in base siting, headframes/covers over mine mouths.

## 7. Release checklist

1. `./gradlew build` + `./gradlew test` + GameTests green; campaign check for behaviour changes.
2. README/BALANCE updated (test counts in README "Testing"), `docs/factionclash-common.toml` regenerated if config
   changed (copy the file a fresh server writes).
3. `cp build/libs/factionclash-0.1.0.jar download/` and put `sha256sum` on README line 13 (`SHA-256: \`...\``).
4. Commit with the rules in `/CLAUDE.md`, push, report to the owner in English (what changed, what was verified,
   what is left).
