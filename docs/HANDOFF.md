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
| `npc/` | `FactionNpc` (the entity: inventory, hunger, gear, affixes, role changes release workplaces; hooks a hero overrides: `withdrawsToRecover`, `fightsBareHanded`, `needsRoleTool`, `takesStoreWeapons`, `takesShield`, and since A4 `fightsLikeSoldier`, `jobIgnoresCombat`, `rangedFighter`, `meleeLeash`, `kiteDistance`, `skirmishes`, `holdsDistance`; `senses` is blindness's 5-block sight), `NpcNavigator` (travel/ARRIVED/MOVING/FAILED, stall and no-progress watchdogs, danger-zone waypoints), `DigOut` (digging/bridging/pillaring out when stuck; `perched` also knows a bare pillar), `NpcRole`, `ToolKind`. |
| `structure/` | `BaseLayout` (settlement blueprint: 49x49 walls, gates W/E, grove pad W, 10-block landing E, `WORK_AREA_WEST/EAST`), `Blueprint`, `Terrain` (level/skirt pads), `SiteFinder` (base sites), `MineLayout` (per-level mine geometry, `nearStairs`), `TreePlotLayout` (legacy fences only), `LairLayout`, `ProjectLayouts`. |
| `combat/` | Goals and rules: `CombatRules` (who is hostile, `sameSide` incl. pact allies), melee/ranged goals, `CivilianFleeGoal`, `CreeperEvadeGoal`, `WithdrawGoal`, `Alerts`. `Pursuit` (per NPC: a target it cannot walk up to: work through a `logic/ReachPlanner` plan or wait out of reach), `Reach` (what a fighter may break or build), `HeroAbilityGoal` and `HeroThrowGoal` (a hero's moves and its thrown basic attack, A3), `HeroTargetGoal` (a hero's target: focus, protects, no attack tokens, A4), `StunnedGoal` (holds a stunned mob, A4) and `HeroHoldGoal` (holds a hero in a stagger or a phase transition, A6a), `OpenGateGoal` (fence gates on a fighting NPC's path; `npc/NpcPathNavigation` makes them passable while fighting). |
| `command/` | `FCCommands`: `/fc start [radius]`, `/fc status|join|leave|base|info|map|achievements`, admin: `/fc admin dump|npcs|spawn|recruit|info|probe|mine <id>|blocks <from> <to>|destroy`. |
| `config/` | `FCConfig` (NeoForge common config; sections campaign, movement, survival, achievements, workplaces, recruitment, war). |
| `info/`, `net/`, `client/` | Station/info screen text (`BaseInfo`), map payload (`MapPayload` marker kinds: … VILLAGE 7, PILLAGER_OUTPOST 8, WOODLAND_MANSION 9, STALE flag 16), client screens (`MapScreen`, `InfoScreen`). |
| `gametest/` | `FCGameTests` (all GameTests), `GameTestBases` (register an isolated test base, clean-up). |
| `hero/` (Heroes v2, `docs/heroes-v2/HEROES_V2.md`) | `HeroEntity` (a `FactionNpc` subclass and GeckoLib `GeoEntity`: hero id and the synced fields `DATA_HERO/ACTION/STATE/MOOD/LOCO`, hitbox from the model, kit never dropped, `FcHero` NBT), `HeroRegistry` (every `assets/factionclash/heroes/*.visual.json` read from the mod file, and since A1b the server definitions `data/factionclash/heroes/*.json` read by `HeroDefinitions`, a reload listener, with the operators' overrides from `config/factionclash/heroes/<id>.json` merged (one that would break its hero is left out) and the whole checked; `registerTransient` fixtures for GameTests with a minimal definition; a hero without a valid definition is dormant), `HeroVisual`/`HeroEntry` (what a hero looks like, where its files are, its checked definition `def`), `HeroManager` (give, release, death and revival after `heroes.reviveSeconds`, the hero's own revival block and the `revival_rite` talent, never under `heroes.minReviveSeconds`; one living entity per id, preview copies, `refreshAll` after a reload), `HeroKit` (the definition's gear as bound items), `HeroEvents`, `HeroLook` (the visual file's bone groups, face table, blink, phases, move names, chains), `HeroAnimator` (the three GeckoLib controllers, common code), `HeroTimingFiles` (the server timing files through a hero's chain). Preview fixtures: `config/factionclash/hero_fixtures/*.visual.json` on a server. Campaign record: `campaign/HeroState` in `CampaignData.heroes` (format 3); a hero's roster `NpcRecord` has `heroId`. Pure rules in `logic/hero/` (`HeroAssembly`, `HeroOverride`, `HeroLoadout`, `HeroSpeech`: what the game makes of a definition, A1b, section 5 "The game reads the hero definitions"; `HeroSize`, `HeroActions`, `HeroIdentity`, `HeroRevival`, `FaceRules`, `BlinkSchedule`, `FaceClock`, `HeroLod`, `HeroClips`, `Sway`, `MoodRules`, `HeroTimings`, `JsonTree`, `FormScale`; and the hero file format, not yet read by the game: `HeroJson`, `HeroCatalogue`, `HeroDef`, `HeroReader`, `Problems`, `HeroBalance`, `HeroRules`, section 5 "The hero file format"; format 2: `HeroReader2`, `EscapeRules`, `HeroSchemaGen`, `HeroCheckMain` for `./gradlew checkHeroes`, section 5 "Hero format 2"; the boss fight: `MoveSelector`, `Sense` and `Conditions`, read by the game since A3, `BossRules` and `Decals`, read since A6a, and `EscapeRules`' runtime half, not yet read, section 5 "The boss rules"; the ability core (A3, section 5 "The ability core"): `MovePlan` (a move's wind-up, hit ticks and recovery), `MoveTiming` (A5: speed, the playback step, the scaled plan with its class minimums, interrupts), `BossRules`' `Engagement`, `Phases` and `HurtLedger.remember` and `Decals`' packing and growth (A6a, read by the game), `UseLedger` (the slot caps per use and receiver), `StatusBook` (marks, damage over time, stats, shield, freeze, and since A4 stun, its immunity, taunt and dispel), `EffectRouting` (who receives each effect, which moves the engine runs), `FormGeometry` (cones, lines, volleys, leaps, dashes, knock-back and pull); control and behaviour (A4, section 5 "The control, passives, states and behaviour"): `PassiveBook` (triggers, cooldowns, the loop guard), `TargetRules` (target choice and spacing), `SummonRules` (summon caps); mobility and work mastery, also not yet read by the game:
`MobilityRules`, `LeapSolver`, `FlightPlanner`, `HeroLinks`, `WallEntry`, `WorkMastery`, section 5 "Mobility and work
mastery"); the ability core in the game (A3): `HeroCombat` (a hero's fight runtime: its move selector, energy gained and spent, ability power, the running move from wind-up to recovery, saved cooldowns), `HeroForms` (who a form reaches), `HeroEffects` (the effects), `HeroDamage` (element damage types, the factors, `heroes.maxHit`, the hurt cooldown cleared and restored), `HeroStatus` (a data attachment from `HeroAttachments` on any living entity) with `HeroCombatEvents`, `HeroSenses` (fills a `Sense` from the world), `HeroUse` (one use and its ledger), `entity/HeroProjectile` and `entity/HeroZone`, with `combat/HeroAbilityGoal` and `combat/HeroThrowGoal`; control, passives, states and behaviour (A4): `HeroStates` (format 2's states), `HeroPassives` (passive firing and forms), `HeroSummons` (summoned helpers), `HeroTalents` (v1's base abilities as talents), `HeroControlEvents` (a stun's cancelled player actions, the taunt factor, summons' events), with `combat/HeroTargetGoal` and `combat/StunnedGoal`; the move runner and format 2's forms and effects (A5): `HeroCombat` (`timingFor`: a move's frames from its clip in the timing files, its `DATA_ACTION` code and speed; `interrupt`, the `stagger` hook, `poiseDamage`, the counter, the barrier, decoys; waves and rain impacts as flights), `HeroMoveEvents` (the counter's catch, the knock-back interrupt, unblockable hits, a broken guard), `entity/HeroDecoy` (the `decoy` add; its look is A7's), `HeroTimingFiles.registerTransient` (a GameTest's own timing file); the boss controller's first part (A6a): `HeroBoss` (the engaged enemies and the fight clock, the hurt cooldown per attacker, the incoming path with the per-hit cap and poise, the parry, the stagger's hold, phases and their transitions, the out-of-fight healing and re-arm), `HeroBossEvents` (the damage event's cap, the parry on the shield event), `HeroTelegraphs` (the decals sent to the players near, their budget and clears; `net/HeroDecalPayload`), with `combat/HeroHoldGoal`; commands in `command/HeroCommands` (`/fc hero list\|info\|check\|reload`, `/fc admin hero give\|release\|revive\|preview\|lineup\|pose`); GameTests in `gametest/HeroGameTests`, `HeroLookGameTests`, `HeroDefinitionGameTests`, `HeroAbilityGameTests`, `HeroControlGameTests`, `HeroMoveGameTests` and `HeroBossGameTests`. How to see a hero in game: section 4, "Seeing a hero in game". |
| `logic/leader/` (Heroes v2 F4, not yet read by the game) | `Personality` (`t_eff`, the 20-point rule, noise, compatibility), `Temperaments` (Appendix A, affinity, the rules' flags), `LeaderBiases` (every director knob of `HEROES_V2.md` 5.5; neutral equals today's constants), `LeaderDraft`, `FactionNameGen`, `Palette` (Lab, delta-E, nearest dye, chat and bar colours), `BannerDesign`, `BaseStyles` (Appendix B), `Talents` (7.2's tier numbers). Section 5, "The leader rules". |
| `client/hero/` | `HeroModel` (files and the animation chain with missing files dropped, `_fallback` for a missing model; after the animation pass: head look, face, flinch, ears, chain sway, bone visibility, LOD), `HeroRenderer` (face-frame UV shift, sash tint, layers, `-Dfactionclash.heroRenderStats`), `HeroFace`, `HeroClientState`, `HeroGlowLayer`, `HeroItemLayer`, `ClientHeroVisuals` (a resource pack's hero visual files become preview fixtures on the client), `HeroDecalRenderer` (A6a: telegraph decals drawn from `net/HeroDecalPayload`, growing over the wind-up). HEROES_V2 15.2 "A2 as built". |
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
- **Seeing a hero in game** (Heroes v2; only the art pipeline's `test_rig` ships so far). With the server up and a
  campaign started (`fc start 256`): `python3 tools/rcon.py "fc admin hero give test_rig ember_legion"` puts the
  campaign's hero at Ember's spawn point (from RCON; a player operator gets it beside them, `[pos]` anywhere in the
  overworld); `fc hero info test_rig` prints where it is (operators and its own faction only) and its sheet; `fc hero check test_rig` checks
  its definition (`data/factionclash/heroes/test_rig.json`) now and `fc hero reload` applies a changed one, or an
  override in the server's `config/factionclash/heroes/test_rig.json` (for example `{"title": "the Campaign Model"}`), to
  the hero at once. Its lines go to the server log as `[Heroes] test_rig says (event): ...`. Join with the Xvfb
  viewer as above, `op Viewer`, `gamemode spectator Viewer`, `tp Viewer <x> <y+1> <z-4> 0 10` to stand in front of it.
  For screenshots: `execute as Viewer at Viewer run fc admin hero lineup` stands display copies of every hero (and of
  the preview fixtures) 4 blocks in front of the viewer, facing it; `fc admin hero pose test_rig base walk` walks it in
  place, `pose test_rig action attack.1 [speed]` swings, `pose test_rig mood <mood>` (12 moods), `talk on`, `phase 2`,
  `weapon drawn`, `pose all clear` undoes it all; `fc admin hero lineup clear` removes the copies. Add
  `-Dfactionclash.heroMarkers=true` to the server's `user_jvm_args.txt` for the server side of the hit timing; the
  `clientViewer` run already logs markers and render times. The style fixtures need their files built first (section 5,
  "The hero client model"). F3+B shows the hitbox (1.82 blocks for the test rig).

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
- **Checkpoint 1 checked in game** (2026-11-12; `HEROES_V2.md` 15.2 "Checkpoint 1 in game"; images in
  `docs/heroes-v2/previews/game/cp1/`). On the A1a world's dedicated server with the Xvfb viewer (window resized to
  1280x720 with `xdotool windowsize`; `run/options.txt`, which git ignores, set to `fovEffectScale:0.0`, `bobView:false`,
  `maxFps:260`, so the spectator camera's FOV is exactly 70): the test rig and the six style fixtures stand, walk, run, swing, blink, show
  12 moods, talk, take four sash colours, hold tools, a torch and a shield, drop `fine_` bones beyond 21 blocks, stay drawn
  at the screen edge, die lying flat and return, and survive a restart. The G2 fidelity test passed: game and preview
  silhouettes within one pixel in three poses and three views (frozen with `tick freeze`/`tick step`; the server
  pushes no entity updates while frozen, so move or tp a hero before freezing). Five Java fixes: `v.fc_dhip` set
  (`logic/hero/FormScale`), no GeckoLib death roll, face planes keep their true normals, left-hand items mirrored as a
  left hand, a teleport is not running (`HeroClips.TELEPORT_STEP`); the server logs timing-file hits under
  `-Dfactionclash.heroMarkers=true` (add it to a test server's `user_jvm_args.txt`). **233 unit tests, 149 GameTests.**
  Software-GL frame rates: no hero 10 fps, 1 hero 7-9, 16 heroes 5-7, 64 heroes 4-6 (a loaded 4-core machine).
- **Review of A0-A2 and checkpoint 1** (2026-11-12; `HEROES_V2.md` 15.2 "Review of A0-A2"). An adversarial review of
  everything since `67317d8`, GeckoLib read in the decompiled 4.9.3 jar (Vineflower is in the Gradle cache:
  `java -jar ~/.gradle/caches/modules-2/files-2.1/org.vineflower/vineflower/1.10.1/*/vineflower-1.10.1.jar <jar> <out>`).
  Fixed: `/fc hero info` showed every player an enemy hero's position and its home's coordinates (now operators and its
  own faction only); a hero saved during its death animation came back as a dying copy that revived its record, so the
  hero never returned (a dead or dying copy is now discarded on joining); per-frame allocations in the render path
  (chain lookups, the visibility plan, resource ids, the glow mask, the Molang supplier, the base candidates; now
  cached); `give` from another dimension; a preview's death announced to its sash faction; and, seen in the viewer,
  the campaign hero flipping between walk and run several times a second at its ordinary pace (now a 0.20 band and a
  6-tick hold, `HeroClips.PaceGate`). Tests strengthened: uniqueness on the chunk-load path
  (`ServerLevel.addLegacyChunkEntities`), the dead copy, a real blow for the 5 s weapon rule, three new unit tests;
  four sabotage checks each caught. Re-checked in the Xvfb viewer (lineup, walk, swing, moods, weapon bones, the hero's
  own walking, `/fc hero info` typed by a non-operator in and out of Ember). Confirmed: no client class on the server
  path, synced ids, face UVs over the baked texture size, GeckoLib scaling the blend with the speed (the checkpoint's
  2x hit excess is frame timing), old worlds, the director rule. Checkpoint 1 stands. **236 unit tests, 149 GameTests.**

- **The hero file format** (Heroes v2 task F1, 2026-11-13; `HEROES_V2.md` 15.2 "F1 as built"; the format is
  `docs/heroes/HERO_FORMAT.md`, "v1"). Seven pure classes in `logic/hero/`, with no game code reading them yet:
  `HeroJson` (a JSON reader that keeps lines and columns, forgives comments, trailing commas, code fences and a
  byte-order mark, and refuses the rest in plain words), `HeroCatalogue` (the single source of every field, word
  list, synonym, allowlist, cap and weight), `HeroDef` (the immutable definition), `HeroReader`
  (`HeroReader.read(path, text)` or `readAll(sources, options)` gives the loaded heroes, the ids not loaded and
  every problem with file, line, field path and outcome: NOT LOADED, PART LEFT OUT, ADJUSTED or NOTE), `Problems`
  (`report()` for a `_report.txt`, `summary()` for chat), `HeroBalance` (slot caps, cooldown and passive floors) and
  `HeroRules` (damage formula, energy, growth, XP, dialogue rate, rarity draws, revival). The owner's rules are in
  it: no word of the format and no input of the arithmetic tells players from NPCs (`protects: players` reads as
  `allies`, the `target_is` kinds `player` and `npc` as `person`); every hero returns 600 s after its death unless
  its own revival seconds or the faction's `revival_rite` tier change that, never under 30 s; nothing knows a yield
  or attack tokens. **`HeroRules.heroPower` is a hook**: it still returns v1's number behind
  `WAR_POWER_IS_PROVISIONAL`; `HEROES_V2.md` section 8 has the war power the design proposes. To check a hero file
  by hand until `/fc hero check` exists (A1b): a unit test in the style of `HeroReaderTest`, or
  `python3 tools/herotool/herotool.py check <file>` for the Python reference checker (it does not know the equal
  readings). `tools/herotool/crosscheck.py` rewrites the 38 cross-check cases under
  `src/test/resources/heroes/crosscheck/` (it needs the Python `jsonschema` package). **473 unit tests, 149
  GameTests** (237 unit tests new; the GameTests passed in one run).

- **Hero format 2** (Heroes v2 task F2, 2026-11-13; `HEROES_V2.md` 15.2 "F2 as built"; the format is `HEROES_V2.md`
  section 12). Still pure logic with no game code reading it: `HeroReader` peeks at a file's `format` and reads a
  format-2 hero through `HeroReader2` (moves with classes, frames, bands, followups and the eight new forms, the
  seven new effects, states, the boss block as the owner's decisions left it, personality and the end of a fight,
  leader, talents, voice, recruit, relationships, revival, mobility); format-1 files read as before and also map to
  format 2 (`HeroDef.v2()` is never null). `EscapeRules` decides from a hero's file whether it escapes or fights to
  the death and at what health it leaves (`flee_at`); the runtime choice is F5/A12's. `HeroBalance` adds the class
  minimums, the decals, the slot caps and weights of the new forms. Removed or player-only fields (`damage_vs_players`,
  `defeat`, tokens, `target_is_player`, `on_yield`, the `yield` lines, `unlock_level`) are read and ignored or read
  the equal way, each with a message. **`./gradlew checkHeroes`** (`HeroCheckMain`, part of `check`) validates
  `src/main/resources/data/factionclash/heroes` (not there yet: C1-C4 write it, so it passes with nothing to check;
  `-PheroDir=src/test/resources/heroes/slice` checks the eight slice kits, which are test fixtures, not the authored
  heroes). **`./gradlew heroSchema`** rewrites `docs/heroes-v2/hero2.schema.json` (a test fails when it is stale).
  The format decisions F2 took on `docs/heroes-v2/notes/kit-gaps.md` are listed at its end. **613 unit tests, 149
  GameTests** (140 unit tests new; one GameTest, `aGuardWaitingOnAMonsterTurnsToAnIntruder`, failed once and passed
  on the rerun; F2 changes no game code).

- **The boss rules** (Heroes v2 task F3, 2026-11-13; `HEROES_V2.md` 15.2 "F3 as built"; the rules are section 6 and
  `MOBILITY_AND_WORK.md` 2.3-2.4, 5.1 and 7.7). Pure logic in `logic/hero/`, not read by the game yet (A3-A6 and A12
  wire it in): `BossRules` (boss health with no attacker or player count, the per-hit cap with its excess to poise,
  the hurt cooldown kept per attacker in a `HurtLedger`, poise, parry and stagger, the damage-taken windows and weak
  points, the fight clock, enrage and its +25% ceiling, phases and the rebirth, the intro, the receiver's stun
  immunity, air poise, the fight's ceiling and the 4 s reach clock), `MoveSelector` with `Sense` and `Conditions` (the
  gaps and the opening gap, the score, charges, followup strings, reactions with one roll per occurrence, the arena
  filters, the frames' class minimums), `Decals` (the ground marks as flat block-display boxes, their growth and the
  budget of 12), and `EscapeRules`' runtime half (escape or the last-stand beat under `flee_at`, a lost raid, a lost
  base, the window, cornered, the refuge, the stay away). Rewards at a death are in `HeroRules`; `HeroActions` bit 7
  is `STATE_ESCAPING`. The fight rules F3 decided are in `docs/heroes-v2/notes/kit-gaps.md`, "F3 decisions" (decal
  shapes per form, the back bonus inside recoveries only, reactions' rolls, followups' charges, postures).
  `BossRulesTest.referenceDuel` gives SLICE_PLAN criterion 15's table to the second. **674 unit tests, 149 GameTests**
  (61 unit tests new; six sabotage checks, each caught; F3 changes no game code).

- **The leader rules** (Heroes v2 task F4, 2026-11-13; `HEROES_V2.md` 15.2 "F4 as built"; the rules are sections 5,
  7.2 and 8.1). Pure logic in the new `logic/leader/`, not read by the game yet (L1-L5 wire it in): `Personality`,
  `Temperaments`, `LeaderBiases` (one method per knob of 5.5; a neutral leader gives today's constants, which
  `LeaderBiasesTest` reads from `FCConfig`, `FactionDirector` and `TraderJob`, and `leaders.personalityStrength = 0`
  is neutral whatever the leader, focus and temperament rules included), `LeaderDraft`, `FactionNameGen`, `Palette`,
  `BannerDesign`, `BaseStyles` and `Talents`. The war power of 8.1 replaces F1's hook in `HeroRules` (`heroPower`,
  `countedPower` with the soldier backbone, `defencePower` at half under half health). The eight kit sheets' leader
  numbers agree with `LeaderBiases` to the last digit; F4's decisions (a half rounds up; `sage_of_letters` and
  `warden_of_walls` beside the knobs) are in `docs/heroes-v2/notes/kit-gaps.md`, "F4 decisions". **717 unit tests**
  (43 new; seven sabotage checks, each caught; F4 changes no game code).

- **Mobility and work mastery** (Heroes v2 task F5, 2026-11-13; `HEROES_V2.md` 15.2 "F5 as built"; the rules are
  `MOBILITY_AND_WORK.md` sections 1-3, 5, 7, 8 and 9-13). Pure logic in `logic/hero/`, not read by the game yet
  (A10-A12 and L5m wire it in): `MobilityRules` (ceilings and the level power, the attribute modifiers, stamina, the
  air budget, the altitude ceilings, which rule of 5.1 holds where, the reach clock's input from engaged enemies of any
  kind), `LeapSolver` (vanilla's air physics: jumps of 1.25 / 1.84 / 2.52 / 3.54 blocks and 1.6's leaps come out of
  it), `FlightPlanner` (the cruise layer over a height function, 24-block waypoints over loaded terrain only, the
  hostile-base berth, the stuck watch, fly or walk), `HeroLinks` (jump-up, gap, drop, climb, chimney and glide links on
  a grid; who can reach whom; the strike from a link), `WallEntry` (the way over a hostile wall, the way out, the goal
  inside), `EscapeRules`' last parts (the scout's step aside, pursuers, no rewards, morale) and `WorkMastery`
  (proficiency, specialties, Presence, the caps, never virtual levels and an extra roll on one drop). The format-2
  `dive` form is in the catalogue and the validator (`dive_rule`), and `checkHeroes` prints each hero's mobility at
  levels 1, 10 and 20. F5's decisions are in `docs/heroes-v2/notes/kit-gaps.md`, "F5 decisions". **793 unit tests,
  149 GameTests** (76 unit tests new; ten sabotage checks, each caught; F5 changes no game code).

- **The game reads the hero definitions** (Heroes v2 task A1b, 2026-11-13; `HEROES_V2.md` 15.2 "A1b as built"). The
  server reads `data/factionclash/heroes/*.json` with a reload listener (`hero/HeroDefinitions`; `/reload` reads them
  again; only the mod's own heroes load), merges an operator's override from `config/factionclash/heroes/<id>.json`
  (its top-level fields replace the built-in's; `logic/hero/HeroOverride`, each problem naming the override file and
  line) and checks everything (`logic/hero/HeroAssembly`); `config/factionclash/heroes/_report.txt` is rewritten after
  every load. `/fc hero check <id|all>` checks without applying and prints the hero's sheet; `/fc hero reload` applies
  at once to the heroes in the world. A hero whose definition breaks or goes is **dormant** (plain soldier, neutral
  model, `(dormant)`; a synced flag tells clients) until a good one comes back. The entity takes its definition
  (`logic/hero/HeroLoadout`): stats with growth, **boss health at all times** (the test rig: 84 at level 1; nothing reads
  who engages it), role, kit (a fists hero holds no weapon and never fetches one), the name tag `★ Name · title
  [Fighting N%]`, lines in chat and a 3 s speech bubble with v1's rate limit (`logic/hero/HeroSpeech`), its own revival
  block and the faction's `revival_rite` tier on the 10-minute rule, never under `heroes.minReviveSeconds` (30). The war
  council counts ordinary soldiers by heads and heroes by war power (`war.heroPowerPercent`), and `sim.py` prints a
  `council` line per faction and a `hero` line per hero. The test rig's definition
  (`data/factionclash/heroes/test_rig.json`) is hand-written. New config: `[heroes]` `minReviveSeconds`, `dialogue`,
  `dialogueRadius`, `dialogueCooldownSeconds`, `defaultLines`, `speechBubbles`, `showEnergy`; `[boss] healthScale`;
  `[war] heroPowerPercent` (`docs/factionclash-common.toml` regenerated). **829 unit tests, 159 GameTests** (36 and 10
  new; sixteen sabotage checks, each caught). GameTest runs: one run lost `soldiersHoldOutOfReachOfAPlayerTheyCannotReach`
  and another `soldiersDoNotKeepBreakingABlockThePlayerPutsBack` (known flakes, `docs/design/notes/combat-unreachable.md`;
  A1b changes nothing an ordinary NPC does), and one sabotage run `secondMinerHelpsInAMineAtTheCap` (not seen before:
  watch it); a run with a fresh config and the final run on the final code passed all 159. Checked on a dedicated
  server from `tools/setup_server.sh` (fresh world, `fc start 256`, this build's jar): the definitions load from the
  jar (`Heroes: 1 loaded (test_rig); definitions: 1 loaded`); `fc admin hero give test_rig ember_legion` says its
  summoned line; `fc hero check test_rig` prints its sheet (84 health at level 1, war power 7.1); an override
  `{"title": "the Campaign Model", "stats": {"health": 30}}` applies with `fc hero reload` (90 health, the title on its
  tag); a datapack replacing `test_rig.json` with a broken file makes it dormant on `/reload` (20 health, `(dormant)`,
  the check naming the datapack's file and line) and removing the datapack brings it back; `/kill` gives its fallen
  line and "returns in 10:00", `revive` its revived line; over two in-game days (`tools/days.sh`, 5.5-6.7 ms per tick)
  it fought a Hollow raid on its base and enemy NPCs (`sim.py`: `fights[monsters=1,soldiers=3] kills=2 energy=51`,
  `fight_start` and `victory` lines in the log; no war council had a target yet, so no `council` line); after a
  restart it came back with its UUID and its saved health clamped to the definition's 84. Found there and fixed:
  `/reload` kept the overrides of the last load (one removed from the config folder stayed applied until
  `/fc hero reload`); it now reads them again. The download jar was not rebuilt (heroes are not playable yet).

- **Review of F1-F5 and A1b** (2026-11-13; `HEROES_V2.md` 15.2 "Review of F1-F5 and A1b"). An adversarial pass of the
  hero format, the pure rules and the game side against HERO_FORMAT, `HEROES_V2.md` 3.5, 6, 12 and 14.1,
  `MOBILITY_AND_WORK.md`, SLICE_PLAN 3 and the kit sheets. Clean: no Minecraft class in `logic/`, no player branch in a
  rule (the `Player` checks left in `hero/` are who hears a line, the greeting, `sim.py`'s count and the existing
  hostility test), no attack token, no yield, every number of 6.2-6.13 the code holds, the eight fixtures move by move.
  Fixed: **heroes no longer withdraw like soldiers** (they inherited `WithdrawGoal` and, with boss health, stepped back
  to eat under 30%: `FactionNpc.withdrawsToRecover` is false for a hero, GameTest
  `heroNeverWithdrawsToEatAsSoldiersDo`); **an operator's override that would break its hero is left out** and the
  built-in hero loads (`HeroAssembly`, `override_breaks`; before, a parsable but breaking override made the hero
  dormant, while an unparsable one did not); `boss.poise: 0` means no poise bar instead of a stagger on every hit
  (`BossRules.Poise.none`); unknown words of long lists (archetypes, talents, format-2 conditions and line events,
  banner patterns) name the document that lists them; a reordered file's phase NOTE names the phase by fight order
  and health share; a `dive` counts as a gap closer for the reach NOTE; two fixture details (Yugen's melee-only
  `contract`, the great brew's 2 s of rain). Every place of every slice kit and v1 example was given each of 16 wrong
  values (57,232 reads, no exception, every loaded hero sane); a fast slice of that is now a unit test. Twelve sabotage
  checks, each caught. **836 unit tests, 160 GameTests** (7 and 1 new; the GameTests passed in one run). Left open: a
  rare or common leader's third phase (6.7, L4), Ilsabet's two radii on `rime_wall` and the great brew's pick-one
  effect (content, C3-C4).

- **The ability core** (Heroes v2 task A3, 2026-11-13; `HEROES_V2.md` 15.2 "A3 as built"; HERO_FORMAT 32 phase 4
  adapted to format 2). A hero now fights with its moves: `combat/HeroAbilityGoal` asks F3's `MoveSelector` for a move
  every 5 ticks while it fights (after the fight's opening gap, against any enemy, player or NPC alike) and
  `hero/HeroCombat` runs it select -> wind-up -> hit ticks -> recovery (`logic/hero/MovePlan`; A5 plugs the timing
  files' frames, `DATA_ACTION`, speed and interrupts in at `HeroCombat.planFor`, `Run.begin` and `stopRun`). v1's
  fourteen forms and the combo (`HeroForms`, `entity/HeroProjectile`, `entity/HeroZone`, chains, physical leaps and
  dashes), the effects damage, dot, heal, shield, effect, stat, knockback, pull, ignite, freeze, cleanse, energy,
  cooldown_cut, move_self, mark and clear_marks (`HeroEffects`, `logic/hero/EffectRouting`, `Conditions` per
  receiver), element damage types and resistances (`HeroDamage`, `data/factionclash/damage_type`), the slot caps per
  use and receiver whoever it is (`logic/hero/UseLedger`), marks and statuses on any entity, saved with it
  (`HeroStatus`, `logic/hero/StatusBook`); energy is gained (ranged and thrown hits too) and the ultimate spends it;
  cooldowns, charges and energy survive a save; a `thrown` hero throws with `combat/HeroThrowGoal`. A move whose form
  or an effect is A4's or A5's is not picked yet (kit-gaps "A3 decisions" lists them per slice hero). New config
  `[heroes]` `powerScale`, `maxHit`, `maxDotPerSecond` (`docs/factionclash-common.toml` regenerated from a fresh
  server). **877 unit tests, 183 GameTests** (41 and 23 new; every effect that can land on a player is checked on an NPC
  soldier and a mock player with the same numbers; six sabotage checks in the pure rules and 22 in the game, each
  caught; the clean runs passed them all, 182 before the bow test was added and 183 after). Not yet checked on a dedicated server in a campaign (only the test rig
  ships; its one move, a dash, runs now); the GameTest server is itself a dedicated server, so no client class loads
  on the common side.

- **The control, passives, states and behaviour** (Heroes v2 task A4, 2026-11-13; `HEROES_V2.md` 15.2 "A4 as built";
  HERO_FORMAT 32 phase 5 adapted to format 2). The effects stun, taunt, dispel, empower and summon run, each the same
  on a player and an NPC (6.15): a stun holds anyone (`factionclash:stun` speed and jump modifier; `combat/StunnedGoal`
  on a mob; a player's attacks, uses, interactions, breaking and placing cancelled by `hero/HeroControlEvents`), with
  the stun immunity of F3 kept by the receiver whoever stunned it, and interrupts a hero's wind-up; a taunt binds a
  mob's target and halves anyone's damage to others; blindness cuts an NPC's ranges to 5 blocks. Format 2's states
  (`hero/HeroStates`), passives with the loop guard (`logic/hero/PassiveBook`, `hero/HeroPassives`; world hooks answer
  on the hero's next tick), plain vanilla summons (`hero/HeroSummons`, `logic/hero/SummonRules`). Behaviour:
  `combat/HeroTargetGoal` (`logic/hero/TargetRules`: focus, protects, aggression radius, **no attack tokens**), the
  leash by aggression, the skirmisher's step back, a melee hero's held keep distance; a worker-duty hero is that
  worker (its role, tool and job) and still fights like a soldier, alarms call heroes of any duty (new `FactionNpc`
  hooks `fightsLikeSoldier`, `jobIgnoresCombat`, `rangedFighter`, `meleeLeash`, `kiteDistance`, `skirmishes`,
  `holdsDistance`, `senses`; ordinary NPCs answer as before); v1's base abilities as their five talents at home
  (`hero/HeroTalents`); combat XP (kills, ultimates, healing others, days, raids) and growth; telegraph poses; the ground
  leap and dash checked at drops and walls. Heroes still never withdraw. New config `[heroes]` `summons`, `xpRate`
  (`docs/factionclash-common.toml` regenerated from a fresh server). **911 unit tests, 203 GameTests** (34 and 20 new;
  every control checked on an NPC soldier and a mock player; 7 sabotage checks in the pure rules and 23 in the game,
  each caught). Left open: format-2 forms and effects with `DATA_ACTION` and a state's bones (A5), the boss triggers
  and decals (A6), summons' looks (A8), heroes on raids (so the won-raid XP waits, L4/A12). Not yet checked on a
  dedicated server in a campaign (only the test rig ships; A4 changes only its target choice there).

- **The move runner** (Heroes v2 task A5, 2026-11-13; `HEROES_V2.md` 15.2 "A5 as built"; SLICE_PLAN 2 "Move runner"). A
  move's wind-up, active part, recovery and hit ticks now come from its clip in the hero's timing files (the one its
  `DATA_ACTION` code names through the hero's animation chain; the clip's `hit` markers are the hits), else from its own
  frames; `DATA_ACTION` gets the move's code (`move.<slot>` by its `visual.json` list, `ultimate`, `ultimate.awakened`) on
  the tick its wind-up starts, and the hit lands exactly the wind-up later. Speed from Haste, Mining Fatigue, Slowness
  and the `attack_speed` stat, held to the class minimums and rounded down to a `DATA_ACTION` step, so clip and plan
  agree (`logic/hero/MoveTiming`). Interrupts: a stun, the stagger hook A6a completes, a knock-back over 0.5 in a
  wind-up (not through super armour or an ultimate), a broken channel (2 s recovery), an escape, death, a lost target
  (charge kept). Format 2's forms (charge, wave, rain, sweep, grab, counter, barrage beside A3's combo; the `dive` waits
  for A12) and effects (guard_break, poise, silence, tether, barrier, spawn_add with server-side decoys whose look is
  A7's, call_guards), `breaks_guard` and `unblockable`; triggers `on_counter`, `on_stagger`, `on_add_died`. Every move of
  the eight slice fixtures now runs. No new config. **937 unit tests, 224 GameTests** (26 and 21 new; the counter, the
  grab, the wave, the guard break, the barrier, the tether and the unblockable hit each checked on an NPC soldier and a
  mock player with the same numbers; 6 sabotage checks in the pure rules and 24 in the game, each caught; the three
  known flakes did not show). GameTest facts learned: a mob with `setNoAi(true)` does not move by its velocity (its
  `travel` needs effective AI), a hurt's last damage source is forgotten after 40 ticks, a mock player's own
  `teleportTo` goes to its fake client (use `moveTo`), and a new fight empties a hero's poise bar. Left open: the decals,
  the per-hit cap, poise from hits and the stagger's hold (A6a); the decoy's look and soldiers targeting decoys (A7); the
  `dive` (A12); a state's shown and hidden bones (still need a synced state slot). Not yet checked on a dedicated server
  in a campaign: only the test rig ships, and its one move has no clip, so it plays as before.

- **The boss controller, part one** (Heroes v2 task A6a, 2026-11-13; `HEROES_V2.md` 15.2 "A6a as built"; section 6 as one
  mode). A hero's fight now runs on its engaged enemies (a blow in the last 15 s or its target, within 32 blocks, of any
  kind) and ends 10 s after the last (`hero/HeroBoss`, `logic/hero/BossRules.Engagement`); vanilla's half-second hurt
  cooldown is kept per attacker, so six soldiers' blows all land (`HeroEntity.hurt`); every hit from an attacking entity
  is capped at 12% of its maximum after the damage-taken windows (stagger +25%, recovery +15% and +15% from behind, a
  transition's -50%), the excess and the weapon's factor going to poise (`HeroBossEvents`); a parry adds 25; full poise
  staggers it (the move interrupted, `stagger`, 2.5 s held by `combat/HeroHoldGoal`, stars); phases begin at their
  thresholds with a transition between moves (super armour, a hazard-aware shockwave on everyone near, the line, the face,
  `phase.N`, `DATA_STATE`'s phase that the renderer reads, `on_phase`, then the phase's stats, state and adds) and re-arm
  out of a fight while it heals 1% of its base health a second after 30 s; heavy, area and ultimate wind-ups send ground
  decals to the players within 32 blocks, drawn and grown by the client (`hero/HeroTelegraphs`, `net/HeroDecalPayload`,
  `client/hero/HeroDecalRenderer`; no block displays, no access transformer). New config `[boss] telegraphDecals`
  (`docs/factionclash-common.toml` regenerated). **945 unit tests, 231 GameTests** (8 and 7 new; the cap, the hurt
  cooldown, the stagger, the parry, the shockwave and the fight clock each with an NPC soldier and a mock player; 7
  sabotage checks in the pure rules and 20 in the game, each caught). Two A5 GameTests changed with the rules:
  `counterAbsorbsMelee` strikes after the arrows' hurt window, `channelBreaksAfterItsShare` uses two blows under the cap.
  GameTest facts learned: a move forced from a test callback lands its wind-up + 1 tick later (its first step is the next
  tick); a still (no-AI) mob's own tick slows its velocity by 2% (spawn what a test pushes before the pusher to read the
  push itself); a soldier lowers a shield it has no reason to raise (give it an archer target to raise it); clearing a
  hero's `invulnerableTime` no longer resets its hurt cooldown. Left open: A6b (reactions, the event weak points, enrage,
  the intro, the bar and title, `fled`/`fallen`, rewards, hooks), the `rebirth`, A12's downed window, S2's sound cues; the
  decals not yet seen on a real client; not yet checked on a dedicated server in a campaign (only the test rig ships).

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
  every fight's balance; on 2026-11-12 the owner chose to keep soldiers player-like: no knockback resistance, no longer
  reach), and animals can slip out of a pen while many soldiers go in and out over a long fight (the
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
