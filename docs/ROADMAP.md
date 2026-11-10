# Roadmap

Agreed with the owner on 2026-11-10. Phases are in order; within a phase, items are roughly in priority order.
Every item is "done" only when it is implemented, covered by tests, checked in a multi-day campaign on a dedicated
server, documented (README/BALANCE) and shipped in the download jar. Keep the Status column current.

## Phase 1: finish what is half done

| Item | Why | Status |
|---|---|---|
| Woodcutters dig their own dirt when the storehouse has none | Tall-tree felling needs dirt for the scaffold; bases on stone (Tide, Stoneguard) had almost none, so they rarely felled tall trees | implemented and tested (`docs/design/notes/phase1.md`); campaign check pending |
| Two miners can share one mine | With `maxOpenMines` (3) reached, extra miners stand idle ("Every mine site of the base is taken") | planned |
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
| Forward outposts and perimeter expansion (§11) | Watchtowers near resources or contested ground; wall expansion; more variety in builders' buildings | planned |
| Radio / communication tower (§12) | Scouts report from a distance instead of walking back to the station | planned |
| Nether expeditions (§15) | A portal, Nether wart, quartz, blaze rods; unlocks potions and better enchanting | planned |
| Overworld/Nether dungeons and bosses, reward chests, hero tokens (§15, §17) | Ties into Phase 4 heroes | planned |

## Phase 4: heroes (§2, §16), to start right after phase 1

The owner's main feature. The format is final: `docs/heroes/HERO_FORMAT.md` (version 1), with the chatbot prompt
`docs/heroes/hero-prompt.txt`, the schema, original examples and the checker `tools/herotool/`. Each hero is
`<id>.json` + `<id>.png` dropped into the server's `config/factionclash/heroes/`, loaded and validated on start and on
`/fc hero reload`; the server sends skins to clients. The owner writes their heroes (Honkai: Star Rail / Zenless
Zone Zero characters) with a chatbot and keeps them in their private repository `rodrigomb47/faction-heros`; those
files are never committed to this repository or shipped in the jar. Claude can install copies on its test server,
check and test them, and report problems; it does not edit or author them.

| Item | Status |
|---|---|
| Format, prompt, schema, examples, offline checker (`docs/heroes/`, `tools/herotool/`) | done |
| Loader and validator (`/fc hero reload`, `check`, `_report.txt`), skin sync to clients | planned |
| Hero entity, stats, gear, behaviour, basic attack, skills, ultimate/energy, passives, base abilities | planned |
| Progression, dialogue, starter hero, hero tokens, 10-minute revival, war council and raids | planned |

## Phase 5: play and polish

| Item | Status |
|---|---|
| A real play session by the owner (joining, commands, screens, early game) and fixes from it | planned |
| Balance pass from those impressions | planned |
| Larger maps (radius 512+) and multiplayer checks | planned |
