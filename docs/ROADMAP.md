# Roadmap

Agreed with the owner on 2026-11-10. Phases are in order; within a phase, items are roughly in priority order.
Every item is "done" only when it is implemented, covered by tests, checked in a multi-day campaign on a dedicated
server, documented (README/BALANCE) and shipped in the download jar. Keep the Status column current.

Additions of 2026-11-13. The owner asked for these "when it's a good fit in the development state, so no need to
rush it":
- Nether fortress raids, netherite tools and armour.
- Wither and Ender Dragon boss raids; against the dragon, factions know to destroy the end crystals.
- Vehicles: light and heavy, customised by the faction, built at their own building.
- First bases far apart, with factions clashing as they explore and found more bases.
- Procedural buildings from templates fitted to the terrain and the resources at hand.

They are placed below (Phase 3 and the new Phase 6). Suggested fit:
- Far-apart starts and templated buildings go with the hero slice's leadership work (L4 founds the hero-led
  factions and gives each its base style), or right after it.
- The Nether, netherite and the boss raids come after the Nether expeditions they build on.
- Vehicles come last, after their own design pass.

## Phase 1: finish what is half done

| Item | Why | Status |
|---|---|---|
| Woodcutters dig their own dirt when the storehouse has none | Tall-tree felling needs dirt for the scaffold; bases on stone (Tide, Stoneguard) had almost none, so they rarely felled tall trees | implemented and tested (`docs/design/notes/phase1.md`); campaign check pending |
| Two miners can share one mine | With `maxOpenMines` (3) reached, extra miners stand idle ("Every mine site of the base is taken") | implemented and tested (`docs/design/notes/phase1.md`); campaign check pending |
| Gate choice avoids pits | An NPC leaving for a target on the far side of a ravine can still pick the gate that leads along it | planned |
| 30-day soak campaign | Find late-game problems: the ring of mines around a base filling up, saved records growing, tick time, economy drift | planned |

## Phase 2: the spec's missing roles

| Item | Notes | Status |
|---|---|---|
| Field medic (§4, §14) | Heals the wounded in and after fights. v1 uses food and golden apples; healing/regeneration potions come after Nether expeditions (Phase 3) provide Nether wart, brewed at a brewing stand like a player | planned |
| TNT expert (§4, §15) | Crafts TNT (gunpowder from creepers, sand) and uses it to breach walls during raids; never near its own base | planned |
| Rail engineer (§4, §15) | Lays rails from the base to far mines and outposts so haulers can use minecarts; the largest of the three | planned |

## Phase 3: world and war

| Item | Notes | Status |
|---|---|---|
| Raids on known pillager outposts and woodland mansions | Scouts already report them; soldiers clear them for loot (crossbows, totems, emeralds) | planned |
| Forward outposts and perimeter expansion (§11) | Watchtowers near resources or contested ground; wall expansion; builders' buildings come from the templates row below | planned |
| Far-apart starts, expansion and new bases (owner, 2026-11-13) | **Today:** the four first bases start too close together (the map's half-width is 320 blocks).<br>**Asked:** each faction's first base starts far from the others, on a much larger map (replaces Phase 5's "larger maps"). Factions grow by exploring with their scouts and founding new bases where the resources are. They find out about each other only through what their scouts see (player-like knowledge), and clash where their territories meet.<br>**Design points:** the start distance; when and where a faction founds a base; how bases share people and goods (haulers, the rail engineer of Phase 2); defending several bases; the war council's view of distant enemies | planned |
| Procedural buildings from templates (owner, 2026-11-13) | A building has no single fixed blueprint. Instead it has a template: its purpose, the rooms or parts it needs, its size range, material roles, and style rules taken from the leader's base style (Phase 4, L4). The builder fits it to the terrain (slope, water, trees, rock) and builds it from the materials the faction actually has. So two halls of the same faction differ, and a hall in the mountains differs from one by the sea | planned |
| Radio / communication tower (§12) | Scouts report from a distance instead of walking back to the station | planned |
| Nether expeditions (§15) | A portal, Nether wart, quartz, blaze rods; unlocks potions and better enchanting | planned |
| Nether fortress raids (owner, 2026-11-13) | Soldiers clear fortresses found by Nether scouts for blaze rods, Nether wart and wither skeleton skulls. They face blazes, wither skeletons and lava like a player would | planned |
| Netherite tools and armour (owner, 2026-11-13) | Miners dig ancient debris in the Nether at player speed with a diamond pickaxe. Netherite upgrade templates come from raided bastion remnants (or are copied with diamonds at a crafting table). The blacksmith smelts scrap, crafts ingots and upgrades diamond gear at a smithing table. The war council counts the stronger gear | planned |
| Overworld/Nether dungeons and bosses, reward chests, hero tokens (§15, §17) | Ties into Phase 4 heroes | planned |
| Boss raids: the Wither and the Ender Dragon (owner, 2026-11-13) | A faction may decide to go after a boss. The leader's personality and the war council weigh the reward against the risk.<br>**Wither:** summoned well away from any base once fortress raids have brought three skulls and soul sand; rewards the nether star (a beacon).<br>**Ender Dragon:** eyes of ender (blaze powder, ender pearls), scouts find the stronghold, the portal is filled, then an End expedition. Soldiers know the fight: destroy the end crystals first, archers shooting the open ones and climbers pillaring up to break the caged ones as a player does; then they fight the dragon and keep clear of its breath and of the void | planned |

## Phase 4: original heroes (§2, §16), the next milestone

The owner's main feature, redesigned on 2026-11-11: 200 ORIGINAL anime-style heroes (100 female, 100 male), each with
its own animated model (GeckoLib, bundled in the jar), facial animation (blinking, expressions), a unique boss-like
combat kit, a personality and non-combat talents. Each campaign's factions are led by randomly drawn heroes; a
faction's name, colours, banner, base style and decisions follow its leader's personality. The design is in
`docs/heroes-v2/` (`HEROES_V2.md` the design, `ROSTER_PLAN.md` the 200 heroes, `SLICE_PLAN.md` the first milestone,
`CRITIQUE.md` open points, `research/` the archetype, animation and systems research); the roster tools are in
`tools/roster/`. The earlier owner-supplied hero path (`docs/heroes/`, format 1 with 64x64 skins and a chatbot prompt)
is retired; format-1 files still load as a fallback for operator overrides.

Owner decisions (2026-11-12): the look is approved (Minecraft-style cube models with anime proportions, detailed outfits
and animated pixel-art faces); the archetype mix is approved; after the 8-hero slice is approved from screenshots,
EACH batch of 24 heroes waits for the owner's review of its screenshot gallery before the next batch starts.
Later the same day the owner approved art style pass 2 (rig v3: smaller heads, distinct faces, strand hair) from the
preview GIFs ("these designs are better, I like it"), and asked that heroes get superhuman mobility (higher and longer
jumps, flight for some) and work better than ordinary NPCs outside battle (`docs/heroes-v2/MOBILITY_AND_WORK.md`).
The owner also set that every hero, leaders included, respawns 10 minutes after its death, unless a death-related
ability (a talent or the hero's own rebirth) changes it. Heroes get their own sounds (generated, or vanilla sounds where
they fit better); the owner accepts a download of about 19 MB for them, or larger if needed. Also decided: no path for
owner-written heroes (built-ins only, operator overrides stay), the work-bonus sizes stay, a hero's third phase and
awakened ultimate are there from level 1, and ordinary soldiers get no extra knockback resistance (player-like).
Equal treatment (2026-11-12 and 2026-11-13): player-only rules are removed, good or bad for the player, so a hero
fights as a boss against anyone (one combat mode, the same health, caps and reactions for players and NPCs; only the
boss bar, title, decals, sounds and speech stay player-side), attack tokens are gone (any number of heroes may press
one target), and no hero yields: each escapes or fights to the death as its own personality decides
(`docs/heroes-v2/HEROES_V2.md` section 6).
The owner plays single-player only: nothing is tuned for multiplayer or co-op (it should not break, but it is not a
goal), and the dedicated server stays only as the test harness (single-player runs the same server code).
The owner then answered that design's questions: heroes may use their abilities to make things "unfair" (they cross
hostile walls and reach players anywhere; soldiers still need the gate), the 27 flyers stay, every hero has all of its
abilities from level 1 at a power that grows to full at level 20, and an escaping hero may make pursuit impossible
(`MOBILITY_AND_WORK.md` section 19).
The owner liked five sound prototypes synthesised from code and allowed Minecraft's own sounds where they fit: every
hero sound is generated by our code or a vanilla sound referenced by its id (`docs/heroes-v2/SOUND.md`).

| Milestone | Status |
|---|---|
| Research and design (archetypes, animation stack, hero-led factions, boss mechanics, roster of 200, slice plan) | done |
| Vertical slice: engine (hero entity, GeckoLib models and faces, kits and boss fights, hero-led factions) + 8 heroes, screenshots for the owner | in progress: GeckoLib 4.9.3 vendored and embedded in the jar (A0, 2026-11-12); the hero entity with the test rig, given by an operator, fighting, saving, dying and returning (A1a, 2026-11-12); its animated client model with faces, layers and LOD (A2, 2026-11-12): checkpoint 1 "pipeline alive" reached (the test rig renders, blinks and walks in game) and checked in game (fidelity with the preview renderer within 1 px, five renderer fixes, software-GL render measurements; `docs/heroes-v2/previews/game/cp1/`); A0-A2 reviewed adversarially (a dead copy reviving a hero's record, hero locations shown to every player, per-frame allocations in the renderer, walk/run flicker, `give` across dimensions fixed; uniqueness tested on the chunk-load path; `HEROES_V2.md` 15.2 "Review of A0-A2"): checkpoint 1 stands; the hero file format in Java (F1, 2026-11-13): reader, checker with plain messages, balance caps and floors and the runtime rules as pure logic under the owner's equal-treatment, no-yield and 10-minute revival rules, not yet read by the game (`HEROES_V2.md` 15.2 "F1 as built"); format 2 of the hero file (F2, 2026-11-13): moves, states, the boss block, personality and the end of a fight, leader, talents, mobility and the rest of section 12 read and balanced, `./gradlew checkHeroes`, the generated JSON Schema, the eight slice kits validated as test fixtures (`HEROES_V2.md` 15.2 "F2 as built"); the boss rules (F3, 2026-11-13): boss health, the hit cap and the hurt cooldown per attacker, poise and stagger, phases, enrage, the move selector with its opening gap, followups and reactions, ground-mark geometry and the runtime escape-or-last-stand decision, all pure logic not yet read by the game (`HEROES_V2.md` 15.2 "F3 as built"); the leader rules (F4, 2026-11-13): personality, temperaments, every director knob of a leader (neutral equals today), the leader draft, faction names, colours, banners, base styles, talent numbers and a hero's war power, pure logic not yet read by the game (`HEROES_V2.md` 15.2 "F4 as built"); mobility and work mastery (F5, 2026-11-13): ceilings and the level power, the leap solver on vanilla's air physics, the flight planner, the links, the way over a hostile wall and out, the rest of escape, proficiency, specialties and their caps, the `dive` form, pure logic not yet read by the game (`HEROES_V2.md` 15.2 "F5 as built"); the game reads the hero definitions (A1b, 2026-11-13): `data/factionclash/heroes` through a reload listener with operators' overrides, `/fc hero check` and `/fc hero reload`, a hero with a broken or removed file dormant, the entity's stats, boss health (the same against anyone), role and kit from its file (a fists hero holds no weapon), its name tag and lines with speech bubbles, its own revival time and the `revival_rite` talent (never under 30 s), the war councils weighing heroes by war power, `sim.py` hero and council lines (`HEROES_V2.md` 15.2 "A1b as built"); checked for two days on a dedicated server with the test rig; the ability core (A3, 2026-11-13): a hero picks its moves after the fight's opening gap and runs them through wind-up, hits and recovery, with v1's forms and the combo, projectiles, zones and chains, the phase-4 effects, marks and statuses on any entity, element damage and the slot caps the same against a player or an NPC, energy gained and spent by the ultimate, cooldowns and energy kept across a save, and thrown heroes throwing; moves with A4/A5 forms or effects wait for those tasks (`HEROES_V2.md` 15.2 "A3 as built"); control, passives, states and behaviour (A4, 2026-11-13): stun, taunt, dispel, empower and summon the same on a player and an NPC, format 2's states, passives with the loop guard, the target goal with no attack tokens, keep distance and the skirmisher, duties and alarms, the base abilities as talents, combat XP and growth, telegraph poses (`HEROES_V2.md` 15.2 "A4 as built"); the move runner (A5, 2026-11-13): a move's frames from its clip's timing file, its animation code set on the tick its wind-up starts and its hit exactly the wind-up later, speed from Haste, Slowness and the attack-speed stat held to the class minimums, interrupts (stun, stagger, knock-back, a broken channel, a lost target), format 2's charge, wave, rain, sweep, grab, counter and barrage and its guard break, barrier, decoys, call to the guards, tether, silence and poise, each the same on a player and an NPC (`HEROES_V2.md` 15.2 "A5 as built"); the boss controller, part one (A6a, 2026-11-13): the fight on engaged enemies of any kind, the hurt cooldown per attacker, the 12% cap per hit with its excess to poise, the parry, the stagger, phases with their transitions and re-arm, the out-of-fight healing and ground decals drawn for the players near, each the same against a player and an NPC (`HEROES_V2.md` 15.2 "A6a as built"); the boss controller, part two (A6b, 2026-11-13): reactions to an NPC's shield as to a player's, the event weak points (snowball, water, bell, light, a critical from above, a charge into a wall), the enrage and its reset, the intro once a day, the boss bar and title for the players near with "fled" and "fallen", the end of a fight with no yield (an escaping hero runs on foot from its threshold, a last-stand hero dies fighting, the stand at home), rewards only at a death (XP orbs and a hero's crest) and the operator's test hooks, each the same against a player and an NPC (`HEROES_V2.md` 15.2 "A6b as built"); the sound engine (S2, 2026-11-13): every hero sound registered with its range and subtitle, the shared cues and each hero's own sounds played at their moments (a move's wind-up with its class cue timed to end on the hit, impacts, the parry, stagger, phase, enrage, intro, the bar's new "fled" and "fallen" stings, the last stand, the revival), steps, hurt and death, voice blips while a hero speaks and looping channels, whoever causes them; `heroes.soundVolume`, `voiceBlips`, `telegraphCues` (`HEROES_V2.md` 15.2 "S2 as built"; the owner's listening pass is still to come); A3-A6 and S2 reviewed adversarially (2026-11-13: self moves now start their followups as the kits say, heroes no longer heal from their food bar, no stagger chaining, zones go through shields, area heals reach their own radius, a barrier's focus fixed; tests for the hero-on-hero cap, the enraged gap, the punish window, creative players, hero against hero, saves and deaths mid-move and sixteen heroes' tick cost; `HEROES_V2.md` 15.2 "Review of A3-A6 and S2") |
| Batches 1-8 of 24 heroes each, reviewed and arena-tested | planned |
| Campaign checks and balance | planned |

## Phase 5: play and polish

| Item | Status |
|---|---|
| A real play session by the owner (joining, commands, screens, early game) and fixes from it | planned |
| Balance pass from those impressions | planned |
| Larger maps: moved to Phase 3, "Far-apart starts, expansion and new bases" (2026-11-13) | moved |

## Phase 6: vehicles (owner, 2026-11-13)

Vehicles come after their own design pass (`docs/design/vehicles.md`, to be written). The owner asked for light and
heavy vehicles. A faction builds each vehicle at a building it must put up first (a vehicle workshop), and
customises it (its colours, plating, weapons, engine). The player-like rule here means that NPCs use vehicles as a
player would: built from real items, fuelled, repaired, and driven within the vehicle's limits.

| Item | Notes | Status |
|---|---|---|
| Design | **Models:** GeckoLib, like the heroes.<br>**Physics and handling** per kind; how NPCs drive (roads and terrain; air paths for the aircraft).<br>**Supplies:** fuel and ammunition from vanilla resources, an open question (for example coal or blaze rods for fuel, redstone for the electronics, iron and netherite for plating).<br>**Customisation** options; how the workshop and its tiers unlock kinds.<br>**Combat balance** against soldiers and heroes; whether the player may drive the faction's vehicles | planned |
| Light vehicles | A bike for scouts (faster scouting and reports) and light transport | planned |
| Heavy ground vehicles | A car with a turret, a tank | planned |
| Aircraft | A helicopter, jets | planned |
