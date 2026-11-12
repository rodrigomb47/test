# Roadmap

Agreed with the owner on 2026-11-10. Phases are in order; within a phase, items are roughly in priority order.
Every item is "done" only when it is implemented, covered by tests, checked in a multi-day campaign on a dedicated
server, documented (README/BALANCE) and shipped in the download jar. Keep the Status column current.

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
| Forward outposts and perimeter expansion (§11) | Watchtowers near resources or contested ground; wall expansion; more variety in builders' buildings | planned |
| Radio / communication tower (§12) | Scouts report from a distance instead of walking back to the station | planned |
| Nether expeditions (§15) | A portal, Nether wart, quartz, blaze rods; unlocks potions and better enchanting | planned |
| Overworld/Nether dungeons and bosses, reward chests, hero tokens (§15, §17) | Ties into Phase 4 heroes | planned |

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
The owner also set that every hero, leaders included, respawns 10 minutes after its death.
The owner then answered that design's questions: heroes may use their abilities to make things "unfair" (they cross
hostile walls and reach players anywhere; soldiers still need the gate), the 27 flyers stay, every hero has all of its
abilities from level 1 at a power that grows to full at level 20, and an escaping hero may make pursuit impossible
(`MOBILITY_AND_WORK.md` section 19).

| Milestone | Status |
|---|---|
| Research and design (archetypes, animation stack, hero-led factions, boss mechanics, roster of 200, slice plan) | done |
| Vertical slice: engine (hero entity, GeckoLib models and faces, kits and boss mode, hero-led factions) + 8 heroes, screenshots for the owner | in progress: GeckoLib 4.9.3 vendored and embedded in the jar (A0, 2026-11-12); next the hero entity (A1a) and its client model (A2) for checkpoint 1 |
| Batches 1-8 of 24 heroes each, reviewed and arena-tested | planned |
| Campaign checks and balance | planned |

## Phase 5: play and polish

| Item | Status |
|---|---|
| A real play session by the owner (joining, commands, screens, early game) and fixes from it | planned |
| Balance pass from those impressions | planned |
| Larger maps (radius 512+) and multiplayer checks | planned |
