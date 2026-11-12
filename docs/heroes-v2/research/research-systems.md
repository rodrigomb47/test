# Heroes v2: game-system design research (hero-led factions, boss heroes, talents, progression)

Research and design input for the owner's request: original anime-styled heroes, each a boss to fight, about 200 of
them (half female, half male) with complex behaviour and non-combat skills, and every faction founded and led by a
random hero whose personality names the faction and drives its decisions.

Scope of this file: the **game systems** those heroes plug into, designed from the current code (read on
2026-11-11 from `/home/user/test/src/main/java/com/factionclash`). Appearance, models and animation tooling are
covered elsewhere (archetype research: `research-archetypes.md` in this folder, whose temperament list and `[P:...]`
proposal tags this file reuses). Every hero, faction name, word bank and example here is original; nothing names or
imitates an existing character, franchise, game or anime.

Conventions (as in `docs/heroes/HERO_FORMAT.md`, "v1" below): time in seconds, distance in blocks, health in health
points (2 = one heart), shares in whole percent. Personality traits are written 0-100 in files and used as
`t = value / 100` (0..1) in formulas; 0.5 is neutral. Numbers marked PROPOSED go to BALANCE.md when built.

---

## 0. Summary and decisions

1. **Factions are slots with a generated identity.** `FactionType` stays an enum (it is in saves by id, in network
   sync by ordinal, and in about 150 lines of GameTests), but it becomes a *slot*: name, colours, banner, base style
   and leader live in a new saved `FactionIdentity`. The four legacy slots keep their ids; four more slots are
   appended after `HOLLOW` (ordinals 0-4 unchanged), so a campaign can have 2-8 hero-led factions (default 4).
2. **At campaign start N heroes are drafted as leaders** (seeded, with diversity rules: distinct archetype, distinct
   colours, at least one of each gender, not all warmongers). Each leader's file supplies its faction names, title,
   motto, palette, banner, base style and naming culture; a generator fills anything missing.
3. **A personality model of 8 traits** (aggression, honour, ambition, caution, greed, sociability, loyalty,
   volatility) plus one of 30 temperament labels. Every FactionDirector, Diplomacy, Recruitment, Logistics, trader
   and player-treatment knob becomes a published formula of these traits. **With every trait at 50 the formulas
   return today's constants**, and a config `leaders.personalityStrength` (0-200%) scales all of it, 0 making every
   faction neutral. With that at 0 and the new systems switched off (`leaders.diplomacy`, `strangerStance`,
   `morale`, `edicts`) the campaign plays exactly as today. This keeps every existing test valid and gives a
   regression switch.
4. **Diplomacy v2**: per-pair relations (War, Peace with optional expiry, Pact), an opinion ledger (grudges are
   negative opinion), a public reputation, daily leader councils that propose, accept, keep or break agreements
   (honourable leaders declare first, schemers betray by surprise raid), and the existing "pact against a conqueror"
   folded in as a fear-driven coalition each leader decides to join or not.
5. **Players get a standing with each faction** (opinion per player): strangers can be welcomed, ignored, warned or
   attacked depending on the leader; joining can require standing; members earn ranks, can petition the leader,
   duel heroes, recruit wandering heroes and spare or finish a defeated hero.
6. **Morale per base** (0-100) carries the emotional weight of events (leader death, victories, festivals,
   rationing) into work speed, retreat thresholds and recruitment.
7. **Leader death is a blow, not the end**: morale falls everywhere, raids stop or turn into vengeance depending on
   personality, the leader revives after 15 minutes (1.5 x the v1 hero time) at its capital. A faction ends only
   when its last base falls (unchanged); its leader then becomes an exiled free hero.
8. **Boss mode**: a hero fights NPC wars with v1-scale numbers ("field mode") and switches to **boss mode** when a
   player engages it: boss bar, intro title, health scaled 3-6x and by the number of players (current share kept),
   a per-hit damage cap, poise and stagger, 2-3 health phases with transitions, a reactive move selector with
   telegraphed wind-ups drawn as ground decals, enrage, arena awareness and anti-cheese, adds, and a yield beat at
   0 health where the killer may spare or finish the hero.
9. **Format 2 adds** `personality`, `leader`, `boss` (phases, moves, reactions, poise, weak points, enrage, intro,
   defeat), new forms (`combo`, `charge`, `wave`, `rain`, `sweep`, `grab`, `counter`, `barrage`), new effects,
   conditions and triggers, `talents`, `relationships` and `recruit`. Every format 1 file still loads.
10. **Talents** (non-combat skills) come from a fixed catalogue of 35 with mod-defined tier numbers (I/II/III at
    levels 1/10/20), so 200 authored heroes cannot break the economy: work auras, production, logistics, people,
    healing, scouting, diplomacy, crafting and defence, each hooked to a named method that exists today.
11. **Recruiting more heroes**: tokens (v1), wandering heroes with personality-driven conditions (duel, payment,
    reputation, gifts, visits, rescue), heroes of fallen factions, and defection when loyalty is low; a
    compatibility score with the leader decides who joins and who later leaves.

---

## 1. What the code does today (facts that constrain the design)

| Area | Fact | Where |
|---|---|---|
| Faction identity | `enum FactionType { EMBER_LEGION, GROVE_COVENANT, TIDE_ALLIANCE, STONEGUARD, HOLLOW }` holds `id`, `displayName`, `ChatFormatting color`, `rgb`, `human`, `Style` (8 blocks) | `faction/FactionType.java` |
| Saves | Factions, bases, reports, players, pact members are saved by `type.id` and read with `FactionType.byId` (unknown id: the record is dropped) | `CampaignData`, `FactionState`, `BaseState`, `Report`, `FactionNpc.readAdditionalSaveData` |
| Network | The entity syncs the faction as a byte **ordinal** (`DATA_FACTION`, read with `floorMod(b, values().length)`) | `FactionNpc.getFaction/faction` |
| Spread | `FactionType` is used in 20 other main files; about 60 uses read `displayName`, `color`, `rgb` or `style`; `FCGameTests` has about 150 lines naming the four human constants | grep |
| Count | `campaign.humanFactions` 2-4 (default 4); `FactionType.humans()` is a fixed array of the four | `FCConfig`, `CampaignStarter` |
| Looks | NPC textures `textures/entity/npc/<faction id>_<0..3>.png` (20 files); base blocks from `FactionType.Style`; the "banner" is a wool block; the station sign prints `displayName`; base name `displayName + " Keep"` | `FactionNpcRenderer`, `BaseLayout`, `BaseBuilder` |
| Repairs | A planned block is repaired from its material group (any `STONE_BLOCKS` member, any planks, logs, stairs...); anything else (wool, carpet, terracotta, banners) is `EXACT` and needs that exact item | `work/MaterialRules` |
| Raids | Interval `raidIntervalTicks` 36000, none before one interval has passed; counter-attack after a quarter interval when outnumbering 3:2; keep `max(homeDefenders 3, (soldiers+2)/3)` at home; attack when party power is at least 2/3 of the target's defence power; target = least `defence x 1000 + distance`; the party falls back after losing more than half; 72000-tick limit | `FactionDirector.raids` |
| Recruitment | Fixed priority list; 3 home swordsmen; soldiers up to 12; `rangedShare` 0.4; trader at 14 people, hauler at 12; enchanter when the XP bank reaches level 10 and 6 lapis; interval `recruitment.intervalTicks` 1200; food reserve = population x 8 | `FactionDirector.needs/recruitment`, `Logistics.foodReserve` |
| Building | A house when free beds are 1 or fewer; storage expansion above 70% full; at most one project every 2400 ticks | `FactionDirector.projects` |
| Diplomacy | Only one automatic pact: when a human faction conquers another and two or more survive, they swear against the conqueror (allied = `sameSide`, shared base reports "told by survivors"); ends when the conqueror falls or fewer than two remain | `campaign/Diplomacy` |
| Players | A player without a faction is never an enemy (`Diplomacy.hostile` needs two factions); NPCs only retaliate when hit (`FactionHurtByTargetGoal` + `Alerts.callGuards`, radius 20); `/fc join` accepts any surviving human faction | `CombatRules.isEnemyPlayer`, `FCCommands.join` |
| Combat | Civilians flee, soldiers withdraw under 30% and return over 60% (`WithdrawGoal`); melee leash 56 blocks; i-frames vanilla; players' sword sweeps and jump crits copied for NPCs | `combat/*`, `FactionNpc.doHurtTarget` |
| War council | Defence counted from real roster entries at the base (not from scout counts) | `FactionDirector.defenders` |
| Station | Shows "Great People: not implemented in this build" | `info/BaseInfo` |
| v1 heroes | Designed, not built: a hero is a `FactionNpc` with a hero id; starter heroes per faction; tokens; 10-minute revival; `maxPerFaction` 4; a boss bar while it fights; 64x64 skins (superseded by animated models) | `docs/heroes/HERO_FORMAT.md` |

---

## 2. Hero-led factions

### 2.1 Life cycle at a glance

```
/fc start [radius] [factions]
  -> LeaderDraft picks N leaders (seeded, diversity rules)                       2.2
  -> FactionIdentity per slot from the leader (name, colours, banner, style)    2.3
  -> CampaignStarter finds sites, BaseBuilder builds each base in its style,
     spawns the starting roster AND the leader beside the station               2.9
daily, per faction: the leader's council (diplomacy, edicts, raid posture)      2.5-2.7
every 100 ticks, per base: FactionDirector with personality-weighted knobs      2.5
leader dies -> morale blow, posture change, revival in 15 min at the capital     2.9
last base falls -> faction eliminated, leader exiled, heroes freed               2.9, 5.3
```

### 2.2 The leader draft

- **Pool**: every loaded hero whose `leader.eligible` is not false (all 200 built-in heroes are authored as
  eligible). Weights by rarity 4 / 4 / 2 / 1 (common, rare, epic, legendary; PROPOSED, flatter than the v1 token
  weights so leaders are not mostly commons). `leaders.allowLegendary` (default true) can exclude legendaries.
- **Rules**, applied by a seeded greedy draw with up to 200 retries, relaxed from the last to the first if they
  cannot all hold:
  1. No two leaders share an archetype.
  2. Primary colours differ by at least 25 (CIE76 delta-E, PROPOSED) so factions read apart on the map and in chat.
  3. No two leaders share a temperament.
  4. With N >= 2, both genders are present (the owner's half-and-half intent, at the leader level too).
  5. War balance with N >= 3: not every leader has aggression >= 70, and at least one has aggression >= 55 (a
     campaign of pacifists stalls; a campaign of warmongers is over by day 3).
- **Seed**: `leaders.seed` if set, else world seed XOR campaign start game time, so `/fc start` twice gives different
  casts while a seed reproduces one for tests and bug reports.
- **Overrides**: `leaders.fixed = ["<hero id>", ...]` (config) and `/fc start <radius> <factions> <hero id>...`
  (operators, tab-completed) fill slots first; the draw fills the rest.
- Distance: N factions need room. Bases are placed on a ring at about 0.55 of the map radius at least 150 blocks
  apart, so `radius >= 45 x N + 40` (PROPOSED; 220 for 4, 400 for 8). `/fc start` warns and raises the radius when
  it is smaller; `CampaignStarter`'s existing widening phases stay as the fallback.

### 2.3 Faction identity from the leader

New saved record `FactionIdentity` (in `FactionState`, synced to clients):

| Field | Source (first that exists) | Use |
|---|---|---|
| `name` (3-32 chars) | `leader.faction_names[i]` (first unused in this campaign) -> generator | chat prefix, station, map, status, death messages |
| `short_name` (1-14) | `leader.short_name` -> the name's key word | sign lines, name tags, the base name |
| `slug` (`[a-z0-9_]`, 3-24) | from `short_name`, made unique | `/fc join <slug>`, command arguments |
| `leader_title` (0-40) | `leader.title` -> temperament x archetype table | "Kaede, Shrine Mother of the Lantern Court" |
| `motto` (0-80) | `leader.motto` -> none | station header, join message |
| `primary`, `secondary`, `accent` (RGB) | `leader.colors` -> hero `color` + derived | chat, name tags, map markers, boss bar, NPC tabards |
| `dye_primary`, `dye_secondary` | nearest `DyeColor` to the RGB (Lab distance) | wool, carpets, banners, beds |
| `chat_fallback` | nearest of the 16 `ChatFormatting` colours | where only 16 colours exist (scoreboard team glow) |
| `banner` | `leader.banner` -> emblem table | real wall banners at the station and gates, a banner item for members |
| `style` | `leader.base_style` -> archetype table -> element table | `BaseLayout`, `ProjectLayouts` |
| `seat` (Keep, Shrine, Court, Hall, Lodge, Port, Manor, Pavilion, Hold, Workshop, Haven) | the style family | base name "Lantern Court Shrine", sign |
| `culture` (`east`, `west`, `mixed`, PROPOSED) | `leader.culture` -> archetype | `NpcNames.random(culture, rng)`: recruits' names fit the faction |
| `leader_hero` | the drafted hero id | 2.9 |

**Name generator** (pure `logic/leader/FactionNameGen`, seeded; used only when a file gives no unused name):

| Template (weights by temperament group) | Example output (original) |
|---|---|
| `{Colour} {Motif} {Group}` | "Vermilion Lantern Court" |
| `The {Group} of the {Adjective} {Motif}` | "The Order of the Silent Bell" |
| `{Given}'s {Group}` (proud, delinquent, genki, tyrant) | "Hana's Wild Parade" |
| `House {Family}` (noble, ancient) | "House Amberlake" |
| `{Motif}{suffix} {Group}` | "Stormdrum Troupe" |

Word banks (original, generic English): colours from the palette name table (vermilion, azure, jade, ivory, ash,
umber, plum, saffron, teal, coral, onyx, rose, indigo, silver, gold); motifs from the archetype (cat: paw, bell,
whisker; fox: lantern, foxfire, tail; oni: horn, club, ember; shrine: bell, talisman, rope; knight: oath, lance,
crest; vampire: rose, moon, chalice; idol: star, stage, encore; witch: cauldron, hex, broom; dragon: scale, pearl,
wyrm; ninja: shadow, smoke, blade; maid/butler: teacup, silver, tray; and so on); group nouns from the temperament
(proud/noble: Court, House, Crown; delinquent: Crew, Gang, Pack; righteous: Order, Vow, Guard; schemer: Circle, Veil,
Web; genki/idol: Troupe, Parade, Band; stoic/veteran: Watch, Wall, Hold; battle maniac/tyrant: Warband, Host,
Legion; gentle/deredere: Hearth, Haven, Commune; sage/genius: Lodge, Conclave, Academy; trickster: Masquerade, Den,
Revel; merchant/glutton: Company, Guild, Exchange). Uniqueness within the campaign is enforced; the authored names of
the 200 heroes are reviewed by hand (and searched) for collisions with existing works, and the generator only builds
compound phrases from the generic banks.

**Banner**: base `dye_primary`, 2-4 vanilla pattern layers (`BannerPatternLayers` on a placed banner block entity)
from an emblem table keyed by archetype family: beast-kin `triangles_top` + `circle`; shrine and priest
`straight_cross` + `stripe_center`; knight and paladin `cross` + `border`; flora `flower` + `gradient_up`; undead and
dark `skull` + `curly_border`; storm and wind `guster` / `stripe_downleft`; sea `flow`; craft and tech `bricks` +
`square_top_left`; star and idol `rhombus` + `circle`; dragon `triangles_bottom` + `gradient`. The `mojang` pattern
(a company logo) is never used. Banners and other dyed accents are decoration: they are left out of the repair plan
(as beds and signs are today), so a base never waits for a blue banner.

**Base style families** (Appendix B lists the blocks): the four legacy styles plus 12 new ones (shrine pavilion,
cherry court, bamboo village, frost hall, desert court, dark manor, ivory palace, jungle haven, tuff workshop,
mud-brick town, deepslate keep, coral port). Rule for every style: walls, floors, roofs and perimeters use blocks of
an existing `MaterialRules` group, or the block is added to `STONE_BLOCKS` (cut sandstone, smooth stone, mud bricks,
tuff bricks, polished tuff, red sandstone, polished diorite, quartz bricks: 8 additions), so builders can repair
with substitutes exactly as today. Accent colour comes only from wool, carpet, banners and glass (decor).

**NPC look**: the 20 per-faction textures are replaced by neutral worker and soldier skins plus a tint layer
(tabard, sash or headband) drawn with the faction's `primary` through `renderToBuffer(..., color)`, so any number of
slots and any colour work without new textures. Variant skins per `culture` keep the base population looking like
the leader's people. Heroes use their own animated models.

### 2.4 The personality model

Every hero file has a `personality` block (format 2). The leader's personality runs its faction; a non-leader
hero's personality drives its own fight AI, dialogue, loyalty and recruitment conditions.

| Trait | Low (0) | High (100) | Main effects |
|---|---|---|---|
| `aggression` (a) | avoids war | seeks battle | raid rate, attack odds, soldier share, stranger hostility, own fight AI (`aggression` word, retreat) |
| `honour` (h) | anything goes | keeps its word | betrayal, declarations, sparing the beaten, duels, ambushes |
| `ambition` (m) | content | expansionist | recruitment pace, building ahead, glory targets, enchanting, tribute |
| `caution` (c) | reckless | careful | garrison kept, attack threshold, food reserve, repairs, ranged share |
| `greed` (g) | generous | acquisitive | loot weight, storage, trade terms, payment conditions, betrayal pull |
| `sociability` (s) | isolationist | outgoing | pact proposals, opinion recovery, traders, festivals, stranger welcome |
| `loyalty` (l) | fickle | steadfast | joining allies, leaving losing allies, defection, raid cohesion |
| `volatility` (v) | predictable | whimsical | noise on every decision (sigma 0.2 x v), whims (2.7 edicts) |

**Temperament** (one of 30, Appendix A): sets default values for all eight traits, the fight-AI defaults, dialogue
tone, face defaults and at most one *special rule* (for example tsundere: refuses the first pact offer from each
faction; righteous: grudges against any faction that destroyed a base; airhead: doubled noise and the `luck` whim).
Authoring rule (validator): each trait may differ from its temperament default by at most 20 (ADJUSTED otherwise),
so the label stays true. `likes` and `dislikes` (item ids, 0-6 each) feed gifts and recruitment.

**Fight AI defaults from personality** (any v1 `behaviour` field the file sets wins): `aggression` word = bold when
a >= 0.65, cautious when a <= 0.35, else steady; `retreat_below` = the role's default (v1 table 5.2) + 20 (c - 0.5)
- 10 (a - 0.5), rounded and clamped 0-60;
`focus` = `heroes_first` for hot-blooded and proud, `strongest` for battle maniac and righteous, `weakest` for
schemer and trickster, else by role; never attacks a yielding enemy when h >= 0.6 (2.9 and 3.12).

**Scaling switch**: every formula reads `t_eff = 0.5 + k x (t - 0.5)` with `k = leaders.personalityStrength / 100`
(0-2, default 1). `k = 0` turns every faction neutral, which reproduces today's director numbers (the new systems
of 2.6-2.8 have their own switches).

### 2.5 Exactly how the traits weight the director's decisions

All in one pure class, `logic/leader/LeaderBiases`, with one method per knob, unit-tested for: neutral equals the
current constant, monotonic in each listed trait, clamps hold. `clamp(x, lo, hi)`; traits are `t_eff` values.

**Raids** (`FactionDirector.raids`):

| Knob | Today | Formula | Range |
|---|---|---|---|
| Raid interval multiplier | 1 | `1 + (0.5 - a) + 0.4 (c - 0.5)` | 0.5-1.8 |
| No raids before (from campaign start) | 1 interval | `interval x (1 + 0.6 (h - 0.5) + 0.6 (c - 0.5))` | 0.5-1.6 intervals |
| Counter-attack window (share of interval) | 0.25 | `0.25 + 0.25 (c - a)` | 0.1-0.5 |
| Counter-attack odds needed (party : defence) | 1.5 | `1.5 + 0.8 (c - 0.5) - 0.6 (a - 0.5)` | 1.0-2.2 |
| Attack odds needed | 0.667 | `0.667 + 0.6 (c - 0.5) - 0.4 (a - 0.5)` | 0.4-1.2 |
| Garrison share kept home | 1/3 | `0.333 + 0.3 (c - 0.5) - 0.1 (a - 0.5)` | 0.15-0.6 |
| Minimum home defenders | `homeDefenders` (3) | `homeDefenders + round(2 (c - 0.5))` | config 0-20 |
| Party falls back after losing | 50% | `0.5 + 0.3 (a - 0.5) - 0.3 (c - 0.5) + 0.2 (l - 0.5)` | 30-80% |
| Leader goes with the raid | - | `leads` if a >= 0.65 and c < 0.6; `stays_home` if c >= 0.7 or a < 0.35; else `joins` only joint and counter raids | - |

**Target choice** (lower cost wins; today `defence x 1000 + distance`):

```
cost(target) = 1000 x defencePower x fd
             + distance x fdist
             - 20 x grudge x (1.5 - s)                  grudge = max(0, -opinion), 0-100
             - 2000 x lootEstimate x max(0, g - 0.5) x 2
             - 1000 x isLair x ((h - 0.5) + (c - 0.5) - (a - 0.5))
fd    = 1 - 2 x max(0, a x m - 0.25)     glory: a bold and ambitious leader seeks strong rivals (fd < 0 prefers them)
fdist = 1 + 4 x max(0, territorial)      territorial = 1 for delinquent and lone wolf temperaments, else 0
```

`lootEstimate` (0-1) uses only what the faction knows: the target's reported population band and base age from
scout reports (a lair is 0.2). The attack-odds check above still applies, so glory never means suicide. Hard rules
(not weights): h >= 0.7 never raids a faction it has Peace or a Pact with (it must declare first, 2.6), and spares a
faction that lost a base in the last in-game day unless grudge >= 60; h <= 0.3 gets an **ambush** bonus (cost -1500)
against a faction whose army its scouts saw marching (a delivered `HOSTILES` report of that faction's soldiers more
than 64 blocks from their base).

**Recruitment** (`FactionDirector.needs/recruitment`, `Logistics`):

| Knob | Today | Formula | Range |
|---|---|---|---|
| Soldier cap | 12 | `12 x (1 + 0.8 (a - 0.5) + 0.4 (m - 0.5) - 0.2 (g - 0.5))`, multiplier clamped 0.6-1.6, rounded | 7-19 |
| Home swordsmen minimum | 3 | `3 + round(2 (c - 0.5))` | 2-4 |
| Ranged share | `rangedShare` 0.4 | `rangedShare + 0.3 (c - 0.5) - 0.2 (a - 0.5)` | 0.15-0.7 |
| Trader at population | 14 | `14 - 10 (s - 0.5) - 6 (g - 0.5)` rounded | 6-20 |
| Enchanter at XP level | 10 | `10 - round(6 (m - 0.5))` | 7-13 |
| Recruitment interval multiplier | 1 | `1 - 0.6 (m - 0.5)` | 0.7-1.3 |
| Food reserve multiplier | 1 (pop x 8) | `1 + 0.8 (c - 0.5) - 0.4 (m - 0.5)` | 0.6-1.5 |
| Need order | fixed | roles in the leader's `economy_focus` (authored, or derived: Appendix A) move ahead of other civilian needs; a focus role may have a second worker when its workplaces allow (mines, caves, a second fishery) | - |

**Building and expansion** (`FactionDirector.projects`, ROADMAP phase 3 items when they land):

| Knob | Today | Formula |
|---|---|---|
| Build a house when free beds <= | 1 | `1 + round(4 x max(0, m - 0.5))` (up to 3) |
| Storage expansion when fuller than | 70% | `0.7 - 0.3 (g - 0.5)` (55-85%) |
| Repair scan budget per run | 384 | `384 x (1 + 0.6 (c - 0.5))` |
| Forward outposts (phase 3) | - | only when m >= 0.6 and food is not short |
| Watchtowers, wall extensions (phase 3) | - | priority by c; monuments (morale, 2.8) by s and proud temperaments |

**Economy and trade** (`TraderJob`, `MinePlanner`): trip interval (12000 today) x `1 - 0.8 (s - 0.5)` (0.6-1.4);
amounts kept back from selling x `1 + 0.8 (c - 0.5) - 0.6 (g - 0.5)` (0.5-1.5); buys weapons first when a > c,
armour first otherwise; greedy leaders (g >= 0.65) buy only offers at or under the base price (no demand surcharge).
Miners' layer choice adds a greed bias toward gold and diamond layers (`MinePlanner` weight + `0.5 (g - 0.5)`).

**Players**: section 2.7.

**Worked examples** (Appendix A defaults; `k = 1`):

| Knob | Neutral | Battle maniac (a95 h55 m70 c5 g25 s45 l55 v60) | Schemer (a45 h15 m85 c70 g70 s45 l30 v25) | Sweet-natured (a25 h75 m40 c50 g25 s90 l80 v35) |
|---|---|---|---|---|
| Raid interval | 36000 | 18000 (clamped 0.5) | 40680 (x1.13) | 45000 (x1.25) |
| Attack odds needed | 0.667 | 0.4 (clamped) | 0.807 | 0.767 |
| Garrison kept | 1/3, 3 minimum | 15%, 2 minimum | 40%, 3 minimum | 36%, 3 minimum |
| Soldier cap | 12 | 18 | 13 | 10 |
| Ranged share | 0.4 | 0.18 | 0.47 | 0.45 |
| Trader at | 14 | 16 | 13 | 12 |
| Pact offers (2.6) | only facing a stronger third faction (T > 1.6) | almost never (P about -0.31) | to weaker neighbours (P about 0.26), broken once 1.5x stronger | even without a threat (P about 0.36) |
| Strangers (2.7) | ignored (start 0, hostile at -30) | warned on sight (start -8.5, hostile at -18.5) | warned on sight (start -7, hostile at -17.5) | welcomed (start 31, hostile at -47.5) |

### 2.6 Diplomacy v2

**State per pair** (`CampaignData.relations`, keyed by the two slot ids): `WAR` (default between factions that have
met; today's behaviour: NPCs fight on sight, raids allowed), `PEACE` (not hostile, not allied: no raids, NPCs do not
target each other; optional `until` game time: a timed peace is a truce), `PACT` (allied: `sameSide`, no friendly
fire, joint raids, shared reports). `Diplomacy.hostile(a, b)` = state is `WAR`; `allied(a, b)` = same faction or
`PACT`. The existing "pact against a conqueror" becomes a set of `PACT` relations among coalition members plus the
marker `coalitionAgainst`.

**Contact rule** (scouts only know what they saw): a leader may only deal with factions its faction knows: a
delivered `ENEMY_BASE` report of it, or a fight between their people in the last two days. Diplomatic messages are
carried by the reports system ("told by envoys"), as the coalition already does.

**Opinion** `O(A -> B)` in -100..100, saved per faction:

| Event | Delta (PROPOSED) |
|---|---|
| Start value at first contact | `40 (s - 0.5) - 40 (a - 0.5)` + temperament affinity (Appendix A `admires` +20, `clashes` -20) |
| B raided A | -25 (x1.5 for proud temperaments) |
| B destroyed a base of A | -60 |
| B killed A's hero / leader | -10 / -25 |
| B broke a pact or peace with A by surprise | -80 |
| B broke one with anyone by surprise | -20 (public reputation, below) |
| Joint raid with B | +15 |
| Each day a pact or peace with B holds | +2 |
| B's leader or a B member spared A's hero (3.12) | +20 |
| Gift or tribute from B | +10 |
| Drift toward the start value, per day | `2 + 6 s` points (righteous and melancholic: never for their top grudge) |

**Reputation** of each faction (0-100, start 50, public to every faction that knows it): +1 per day per kept
agreement (max +3/day), -40 per surprise betrayal, -5 per declared break, +5 per spared hero.

**The leader's council** (once per in-game day at a slot-specific hour, plus right after a raid on it or a base
loss), for each known faction B, with `T` = the strongest *other* known faction's power divided by A's own power
(power = soldiers x soldier power + 2 per hero + 3 for the leader; the existing `power()` scale) and noise
`N = v x 0.2 x gaussian`:

```
propose PACT       P = 0.35(s-0.5) + 0.30(c-0.5) - 0.35(a-0.5) + 0.25 clamp(T-1, 0, 2) + O/200 + N
                       (+0.2 when A is a schemer and B is weaker than A; -0.3 for lone wolf)
                   offer when P > 0.15
accept (B's view)  Acc = P computed for B toward A + (rep(A) - 50)/200 + envoy talent of A (+0.2)
                   accept when Acc > 0.10 (tsundere: the first offer from each faction is refused)
offer PEACE        after a failed raid on A or a base loss of A: A offers peace for 1-3 days when
                   c >= 0.5 or a <= 0.4; the raider accepts when its Acc > 0 (honourable leaders accept a
                   beaten foe's first truce)
break (A's view)   inclination = 0.5(m-0.5) + 0.5(g-0.5) + 0.4(a-0.5) - 0.8(h-0.5) - 0.6(l-0.5)
                   opportunity = clamp(power(A)/power(B) - 1, 0, 1)
                                 + 0.5 [A's scouts saw B's army marching, a HOSTILES report of B's soldiers]
                   Br = inclination x opportunity - O/200
                        - 0.3 [B is A's only ally against the strongest faction] + N
                   break when Br > 0.25 and the agreement is at least 1 day old
```

Worked: the schemer of 2.5 has inclination 0.655 and breaks once it is 1.5x stronger than its partner (or 1.2x
while the partner's army is away); the trickster (0.355) needs about 1.7x; the battle maniac (0.085) and every
leader with negative inclination never betray; neutral leaders (inclination 0) never do.

- **How a break happens**: h >= 0.75 never breaks by surprise: it **declares** (all members of both factions are
  told; Peace for one day, then War; opinion -20, reputation -5). Below that the break *is* a raid launched at once
  (opinion -80 for the victim, reputation -40). Pacts between the last surviving factions end after two days (the
  alliance of convenience is over) unless `leaders.sharedVictory` is on, so a campaign can still be won.
- **Loyalty in a pact**: when an ally launches a raid, a member joins with chance `l` (today it always joins a pact
  offensive when strong enough); when an ally loses its last-but-one base, a member with l < 0.35 leaves the pact
  (declared, no reputation loss beyond -5).
- **Coalition against a conqueror** (today automatic for all survivors): each survivor joins when
  `fear = (power(conqueror) / power(self)) x (0.5 + c) + (h - 0.5) x 0.5 - O(self -> conqueror)/100 > 0.8`. A
  survivor with `m x g > 0.5`, h < 0.4 and positive opinion of the conqueror instead offers the conqueror a pact
  (bandwagoning). At neutral traits a survivor joins unless the conqueror is clearly weaker than itself (power ratio
  under 0.8); with `k = 0` or no leader, every survivor joins, exactly today's rule.
- **Shared knowledge**: pact members receive each other's `ENEMY_BASE`, `LAIR`, `PILLAGER_OUTPOST` and
  `WOODLAND_MANSION` reports once a day, marked "told by envoys of <faction>".
- **The leader's voice**: council results are announced to the faction in the leader's words (new line events
  `pact_offer`, `pact_accepted`, `pact_refused`, `peace_offer`, `declare_war`, `betrayal`, `betrayed`) followed by
  the plain mechanical summary that today's messages give. Temperament fallback lines cover heroes without their own.

### 2.7 Treatment of players

**Standing**: `FactionState.playerOpinion: Map<UUID, Float>` (-100..100), for members and strangers.

| Event (only what the faction's people witness or suffer) | Delta |
|---|---|
| First seen within 48 blocks of a base (start value) | `40 (s - 0.5) - 40 (a - 0.5) + 20 (h - 0.5)` |
| Hits one of its NPCs / heroes | -10 (at most -30 a minute) |
| Kills an NPC / a hero / the leader | -40 / -60 / -80 |
| Breaks or takes a planned block of its base (non-member) | -2 each (at most -20 a day) |
| Opens one of its storehouse chests (non-member) | -5 (theft) |
| Kills an enemy (monster, hostile NPC) within 32 blocks of its base | +4 (max +20 a day) |
| Gives items at the station (food 1 per 10 hunger points, ore and ingots by value, `likes` x2) | up to +20 a day |
| Spares one of its heroes (3.12) | +25 |
| Drift toward the start value | 5 a day |

**Stances** derived from the leader: a stranger whose standing is at or below `hostileAt = -30 - 40 (h - 0.5) +
30 (a - 0.5)` is an enemy (`CombatRules.isEnemyPlayer` gains this check; guards attack on sight); below
`hostileAt + 30` the faction is **wary**: a guard walks up, says the leader's `stranger_warning` line once a minute,
and turns hostile if the player stays inside the walls 20 s or breaks a block. At or above it, today's rule (ignored
unless attacking). The start value is never below `hostileAt + 10`, so nobody is attacked at first sight (a
`closed_gates` edict, 4.3, is the only exception). Neutral leaders give -30, so one killed NPC (-40) makes a stranger
an enemy until standing recovers; honourable leaders need more, aggressive ones less.

**Joining**: `/fc join <slug>` needs standing >= `joinAt = -10 + 40 (0.5 - s) + 20 (h - 0.5)` (neutral -10: anyone
not hostile; an isolationist needs about +10, earned by gifts or help). A greedy leader (g >= 0.7) also asks an entry
gift at the station (food worth 40 hunger points, PROPOSED), taken from what the player hands over.

**Ranks for members** (contribution points from deliveries, kills of enemies, defence help; PROPOSED thresholds 0 /
100 / 400 / 1200): Recruit, Member, Officer, Champion. Officers may **petition** the leader at the station screen
(suggest a raid target, a pact, a festival); the leader accepts when the matching council utility, plus a bonus
(Officer +0.1, Champion +0.2, x (s + l)/2), passes its threshold. A Champion's suggestion of a duel with an enemy
leader is always carried to that leader (3.12, 5.4). A member whose standing falls under `hostileAt` is expelled
(today's `/fc leave` path) and told why.

### 2.8 Morale (per base)

`BaseState.morale` 0-100, start and drift target `55 + 20 (s - 0.5) + 10 (l - 0.5)` (perfectionist temperament -10),
drift 5 a day. Shown on the station.

| Event | Delta | Event | Delta |
|---|---|---|---|
| NPC of the base killed | -3 (max -15 a day) | Raid won (target base destroyed) | +15 |
| Hero of the base killed | -8 | Raid beaten back | -8 |
| Leader killed (all bases) | -20 | A base of the faction lost (all bases) | -15 |
| Leader returns (all bases) | +10 | Rationing, per day it lasts | -2 |
| Festival (edict, talent) | +15 | Three days of food in store, per day | +1 |
| A hero joins | +10 | Betrayed by an ally (all bases) | -15 |
| Pact sworn | +5 | Player gift worth 100 hunger points | +2 |

| Band | Effect |
|---|---|
| >= 80 Inspired | +1 Diligence stack for workers (within `Affix.maxStacks`), +5% damage for soldiers, recruitment interval x0.9 |
| 31-79 Steady | none |
| <= 30 Shaken | -6% work speed (a negative Diligence-sized modifier), `WithdrawGoal` leaves at 40% instead of 30%, recruitment interval x1.25 |
| <= 10 Despairing | no raids launched from this base; heroes of the faction with loyalty under 35 make a defection check (5.3) |

### 2.9 The leader in the world

- **Spawn**: `BaseBuilder.buildSettlement` spawns the leader beside the reporting station after the starting roster
  (it replaces v1's "starter hero", section 25.2 there). Old campaigns: on the first tick with heroes enabled, each
  living human faction without a leader gets one, chosen as the free hero whose primary colour is nearest the legacy
  colour; the legacy name stays unless `leaders.renameLegacy`.
- **Duty**: `court`, a new duty: the leader keeps to the capital's walls (patrols between the station, the heart and
  the gates; stands at the station when a member player is near so it can be talked to), always answers alarms
  (`protects: heart` during a defence), and raids as set by 2.5. A leader with a worker talent (a forgemaster, a
  chef) spends daylight at that workplace instead and counts as that worker.
- **Death**: announced to everyone ("<leader>, leader of <faction>, has fallen at x, z"); morale -20 at every base;
  a raid it was on falls back when `(c + 1 - l) / 2 > 0.5`; no new raids until it returns unless a >= 0.7, in which
  case one **vengeance raid** goes against the killer's faction (grudge +25); rivals with h < 0.5 lower their cost
  of attacking this faction by 500 while the seat is empty. Its council keeps running on the saved personality.
- **Revival**: `reviveSeconds x leaders.reviveMultiplier` (600 x 1.5 = 15 minutes, PROPOSED) at the capital (or the
  nearest standing base), morale +10, its `revived` line. `revival_rite` talents shorten it (4.2).
- **Elimination** (last base falls; `CampaignManager.eliminate` today kills every NPC): heroes are no longer killed.
  The leader becomes an **exiled** free hero (never a leader again this campaign; recruitable later, 5.3); the other
  heroes become wanderers after one in-game day (5.3); ordinary NPCs are removed as today.
- **Missing definition**: a leader whose file is removed keeps leading as a dormant hero (v1 25.5); the personality
  snapshot saved in `FactionState.leaderPersonality` keeps the council working.
- **Succession** (only with `leaders.permadeath`, off by default, or `/fc admin leader release`): the faction's
  highest-level hero with compatibility >= 0.5 (5.3) becomes leader; with none, a regent (the highest-level NPC)
  keeps the saved personality and the title "Regent of <faction>". The faction name never changes mid-campaign
  (players and maps rely on it).

### 2.10 Migration: from a fixed enum to hero-led slots, with minimal risk

| Step | Change | Risk control |
|---|---|---|
| M1 identity layer, no behaviour change | Add `FactionIdentity` (saved in `FactionState`, default = today's name, colour and style for the four legacy slots and the Hollow). Add `Factions.name(server, type)`, `Factions.style(...)`, `Factions.color(...)` (a `TextColor` from RGB) and route all ~60 reads of `displayName`, `color`, `rgb`, `style` through them; `FactionType` keeps its fields only as legacy defaults. Client: `FactionIdentityPayload` (slot ordinal -> name, short name, primary, secondary) sent at login and on change; `ClientFactions` cache; renderer, `MapScreen`, `InfoScreen` read it | Pure mechanical refactor; every unit test and GameTest must pass unchanged; a new GameTest checks a renamed identity shows in a name tag and a station title |
| M2 more slots | Append `FACTION_5`..`FACTION_8` after `HOLLOW` (ids `faction_5`..`faction_8`, `human = true`); `humans()` returns the eight human slots; `humanFactions` range 2-8; every `values()` loop already skips slots without bases (status, survivors) | Ordinals 0-4 unchanged, so entity sync and old saves are untouched; `EnumMap`/`EnumSet` are fine; an older jar loading a world with slot 5+ drops those records (`byId` returns null), which is acceptable and logged |
| M3 slugs and suggestions | `/fc join`, `/fc achievements`, admin commands accept the identity slug or the slot id; suggestions list living factions' slugs; `Messages.toFaction` prefixes the identity name in its RGB colour; the station sign prints `short_name` split over two lines | Slot ids keep working for scripts (`tools/sim.py`, `days.sh`) and tests |
| M4 neutral textures | Neutral skins + tint layer replace per-faction textures; the 20 old textures stay as legacy variants | Visual only; checked on the `clientViewer` client |
| M5 leaders | Leader draft, identity generation, base styles and banners in `CampaignStarter`/`BaseBuilder`; leader spawn; old campaigns get leaders as in 2.9 | Behind `leaders.enabled` (default true); off = legacy identities and no leader |
| M6 personality hooks | `LeaderBiases` read in director, recruitment, logistics, trader, combat rules | `personalityStrength = 0` reproduces today; unit tests pin neutral = current constants |
| M7 diplomacy v2, standing, morale | New saved maps; `Diplomacy.hostile/allied` over relation states; coalition rewritten on top | `CampaignData.FORMAT` 3; missing keys load as empty/neutral; an old save's pact converts to PACT relations + `coalitionAgainst` |

Rejected alternative: replacing the enum with an open `FactionId` class (unbounded factions). It changes every
signature, every `EnumMap`/`EnumSet`, the sync type and all ~150 GameTest lines, for a benefit (more than 8
factions) that map size and tick time do not allow anyway. Renaming the four legacy constants to `SLOT_1..4` is
possible later as a pure IDE rename (saves use the id strings, not the constant names), but buys nothing now.

---

## 3. Boss-like hero combat

### 3.1 What makes a fight feel like a boss in Minecraft

Collected from how vanilla bosses and boss-like mobs present themselves (wither, ender dragon, warden, elder
guardian, ravager, evoker) and from action-game boss craft (see also `research-archetypes.md` Part 3):

1. **Presence**: a boss bar with the name, an entrance, a title on screen, a sound that announces it.
2. **Durability with a per-hit ceiling**: enough health for a 60-120 s duel, and no one-shot by a mace fall attack
   or stacked crits (vanilla's wither and dragon have high health and special damage rules).
3. **Readable danger**: every big attack has a wind-up you can see and hear, often a ground marker; the length of
   the tell grows with the danger; after the attack a recovery is the punish window.
4. **Patterns that react**: a small moveset per phase used in recognisable strings, chosen by distance and by what
   the player does (blocking, shooting, eating, pillaring), never the same move three times in a row.
5. **Phases**: at health thresholds the boss transforms and adds a *new mechanic*, not just bigger numbers.
6. **A way to win by skill**: stagger windows, weak points, counters, elemental weaknesses, adds to kill first.
7. **Pressure over time**: enrage if the fight drags; a reset if the player runs away.
8. **Arena sense**: it does not leap into a 2-high tunnel, knocks the player around without cheap deaths, and cannot
   be cheesed from a dirt pillar.
9. **A defeat moment**: a kneel, a last line, a reward.

### 3.2 Two modes

- **Field mode** (default): v1 numbers, so a hero is a strong officer in NPC wars (v1 war power 5.25 for a level-20
  hero in iron, about 2.3 soldiers). Phases still trigger at their health shares (they are part of who the hero
  is), with no boss bar and no scaling.
- **Boss mode**: on while at least one player is *engaged* (the player damaged the hero in the last 15 s or is its
  target, and is within 32 blocks; creative and spectator never count). It ends 10 s after the last engaged player
  leaves. Entering and leaving keep the current health *share*, so the bar never jumps.

### 3.3 Health, damage and multiple players (`logic/hero/BossRules`, pure)

| Rule | Formula / value (PROPOSED) |
|---|---|
| Boss health | `fieldMaxHealth x boss.health_scale/100 x (1 + 0.5 (n - 1))`, n = engaged players capped at 5; `health_scale` default 300 (hero), 400 (leader), allowed 200-600; applied as one attribute modifier `factionclash:boss_health` (`ADD_MULTIPLIED_TOTAL`) and `setHealth(share x newMax)` |
| Example | a rare leader of 36 field health at level 10: 144 against one player, 216 against two |
| Damage cap from players | one hit from a player source at most `boss.max_hit_share` (default 12%) of boss max health (17 of 144): a mace smash or a netherite crit cannot end a phase; the excess goes to poise (3.8) instead |
| Damage to players | v1 slot caps hold; boss mode multiplies hero damage against players by `boss.damage_vs_players` (100-140%, default 115%) after caps |
| No one-shot | a hit never takes a player who was at full health below 1 health point (fairness rule 3.13) |
| Healing during the fight | none from out-of-combat rules; `heal` and `drain` effects as authored |
| Several heroes on one player | **attack tokens**: at most 2 heroes may *press* one player (melee and heavy moves) at a time; the others use ranged or support moves, guard their allies, or circle at 8-12 blocks; a token is returned after the move's recovery. Against NPCs, no tokens |

### 3.4 The fight loop and move selection

```
INTRO (first engagement of the day, 1.0-1.5 s) -> NEUTRAL (spacing, basic attacks, waits the phase's gap)
   -> MOVE (WINDUP -> ACTIVE -> RECOVERY; a combo string runs its strikes back to back)
   -> NEUTRAL ... | REACTION (a punisher overrides the next pick) | TRANSITION (phase) | STAGGER | YIELD
```

- Gap between moves (NEUTRAL time) by phase: 1.5-2.5 s, 1.0-2.0 s, 0.6-1.5 s; enraged x0.7. Basic attacks continue
  inside the gap.
- Each move has a **band** (`close` up to 4 blocks, `mid` 4-10, `far` 10-24; one or more), a **weight** 1-10, the
  phases it belongs to, optional `prefer_when` conditions and `followups`.
- Pick (every 5 ticks while NEUTRAL and the gap is over), seeded per entity:

```
score(move) = weight
            x band fit           1 in band, 0.25 in an adjacent band, 0 otherwise
            x prefer fit         2 when its prefer_when condition holds, else 1
            x novelty            0 if it was the last move (unless repeatable), 0.35 if one of the last 3
            x posture            (1 + 0.6 (a - 0.5)) for offensive moves, (1 + 0.6 (c - 0.5)) for zoning and defensive
pick by weighted random among moves with a ready cooldown and a valid target; none -> keep spacing and basic attacks
```

- **Followups**: when a move hits, each listed followup may start immediately (its own chance), which is how
  recognisable strings form ("dash, then the rising cut 70% of the time").
- The v1 rules stay: global cooldown 1 s between abilities, cooldown floors, the ultimate's 20-60 s gap, energy.

### 3.5 Telegraphs

| Move class | Minimum wind-up | Minimum recovery | Tell |
|---|---|---|---|
| `light` | 0.4 s | 0.3 s | animation |
| `heavy` (heavy melee, leap, dash, charge, grab) | 0.8 s | 0.8 s | animation + sound + line or landing decal |
| `area` (cone, nova, zone, wave, rain, sweep) | 1.0 s | 0.8 s | animation + ground decal + sound |
| `ultimate` | 1.5 s | 1.5 s | all of the above + a spoken line + a wide sound + screen darkening (boss bar `setDarkenScreen`, 2 s) |

- The validator raises a too-short wind-up or recovery to the class minimum (ADJUSTED); the class is inferred from
  the form when the file does not give it.
- **Ground decals**: vanilla `Display.BlockDisplay` entities (no client assets) laid flat (scale y 0.01) in the
  hero's accent colour (stained glass for normal tells, purple for unblockable grabs, red for the ultimate): a disc
  for circles, a stretched strip for lines and charges, a fan of 3-5 strips for cones, a ring for waves, a landing
  disc for leaps, one disc per impact for rain. They **grow from 0 to full size over the wind-up** (display
  transformation interpolation), a readable "charging" timer. Transformations are set through NBT or an access
  transformer (`Display.setTransformation` is private in 1.21.1). At most 12 per hero; tagged and removed at the hit,
  on the hero's death and on chunk load (left-overs from a crash).
- Particles in the signature colour and the signature sound at wind-up start; spatial, so players hear direction.

### 3.6 Phases

- 1-3 phases (`boss.phases`, each with `at` = health share at which it begins: 100, then for example 60 and 25).
  Leaders and epic/legendary heroes are authored with 3; others with 2 (the third may unlock at level 15).
- **Transition** (0.8-2.0 s): the hero is not invulnerable but takes 50% less damage and cannot be staggered
  ("super armour", shown by a glint), a shockwave pushes everyone within 4 blocks back (strength 2, no damage) to
  reset spacing, a line, a facial expression and a model change (sibling design: tails fan out, horns glow, mask
  on), then the new moveset, stats or state applies, and the boss bar name gains the phase pip (`◆◆◇`).
- Validator NOTE when a phase adds no new move, add, state or mechanic ("phase 2 only changes numbers").
- `rebirth` (legendary only, `[P:rebirth]`): once per fight, lethal damage instead starts a final phase at 30%; the
  bar shows it as an extra pip from the start, so it is never a surprise.

### 3.7 Reactions: reading the player

`boss.reactions`: a list of `{ when, move, chance, cooldown }` checked every 5 ticks; a true `when` with a ready
move and passed chance replaces the next pick (not a move in progress).

| `when` (new conditions) | Typical answer |
|---|---|
| `target_blocking` (shield raised) | a `breaks_guard` strike, a grab, or walking around the shield |
| `target_drawing_bow` / `target_far` | gap closer, `barrage` back, a stance that reflects nothing but walks in |
| `target_eating` (food or potion use) | a rush ("punish the heal"), from heroes with a > 0.6 |
| `target_airborne` / `target_above` | anti-air (`[P:anti_air]`), or `pull` |
| `target_behind` | a turning sweep or a backstep |
| `target_unreachable` | ranged band; see anti-cheese 3.10 |
| `low_health_self`, `ally_hero_low` | a defensive or protective move (personality: loyal heroes save allies first) |

### 3.8 Poise, stagger, weak points and counters

- **Poise** (`boss.poise`, 50-200, default 100; 0 only with another weakness): each player hit adds `damage x weapon
  factor` (sword 1.0, axe 1.6, mace 2.0 plus the fall bonus, trident 1.2, arrow 0.6, crossbow bolt 0.8); blocking a
  heavy move with a shield raised in the last 6 ticks (`getTicksUsingItem() <= 6`: a parry) adds 25; a `charge`
  that ends against a wall adds 40; damage over the per-hit cap converts 1:1 into poise. Poise decays 15 per second
  after 3 s without poise damage.
- **Stagger** at full poise: 2.5 s, the hero kneels dizzy (stars, `on_stagger` passives), takes +25% damage; poise
  resets and its maximum grows 20% for the rest of the fight (no stunlock). No stagger during transitions, the
  ultimate's wind-up and moves marked `super_armor`.
- **Punish windows**: during recovery the hero takes +15% damage, +15% more from behind (angle over 120 degrees from
  its facing).
- **Shields**: light moves are blockable as any hit; `breaks_guard` moves, when blocked, disable the shield 5 s
  through vanilla `Player.disableShield()` (the axe rule); `unblockable` moves (grabs) always use the purple decal.
- **Elements**: v1 `resistances`; the validator asks for at least one weakness (-10 or lower) or a weak point (NOTE).
- **Weak points** (`boss.weak_points`, `[P:weak_point]`, `[P:item_weakness]`): `back` (bigger backstab bonus),
  `recovery` (bigger recovery bonus), `charge_wall` (wall stagger), `channel` (a channelled move breaks after
  `interrupt` damage, default 15% of max, and gives a 2 s recovery), `iron` (+15% from iron weapons: folklore
  spirits), `gold` (+15% from golden weapons: undead and fiends), `snowball` and `water` (a snowball hit or a water
  splash potion douses a fire hero: its burning aura ends and it deals -10% for 5 s), `bell` (ringing a bell within
  8 blocks stuns a spirit hero 1 s, at most once per 30 s), `light` (a torch or a glowstone placed by a player within
  4 blocks weakens a shadow hero 10% for 10 s). Each is visible on the hero sheet and hinted by a dialogue line.
- **Barrier with a focus** (`[P:break_shield]`): a `barrier` effect blocks all damage until it has taken N hits; with
  a `focus` add (a talisman, a crystal, a drone) alive, it cannot drop below 1 hit; killing the focus breaks it and
  staggers the hero.
- **Counter stance** (`counter` form): a melee hit during the stance is answered at once and mostly absorbed; arrows
  are not countered unless the move says so, so the answer is ranged or waiting it out.

### 3.9 Enrage, reset, leash

- **Soft enrage**: after `boss.enrage.after` seconds in boss mode (default 240 for leaders, 180 for others; 120-300):
  +15% damage, gap x0.7, poise maximum x1.25, the bar turns red, an angry face and line. Total damage bonus never
  above +25% whatever stacks.
- **Hard enrage triggers** (any of): `ally_hero_died` nearby, `heart_attacked` while defending, `favoured_hurt` (the
  yandere rule: its favoured hero or the leader is hit), chosen per hero.
- **Reset**: no engaged player for 10 s: boss mode ends; out of combat for 30 s: heals 1% of field max health per
  second (field mode); a phase re-arms when health climbs 10% above its threshold. A leashed hero walks back to its
  post; it never teleports.

### 3.10 Arena awareness and anti-cheese

- Every 2 s in boss mode the hero samples its surroundings: ceiling height (3 blocks or less: no `leap`, no knock-up
  moves, prefer thrusts), water and lava along dash and charge paths (moves that would cross them are skipped),
  drops over 4 blocks along a path (dash and charge stop short), open sky (needed by visual-lightning and `rain`
  moves outdoors).
- **Hazard-aware knockback** on players: if the landing area 4 blocks away has lava, fire, a drop of more than 6 or
  the void, the push is cut to 0.5 strength. No cheap deaths.
- **Home ground**: inside its own walls a hero gets moves and lines that use them (the `in_home_base` condition: a
  bell rung to call guards, a stand at the heart).
- **Unreachable target** (no path, or no melee reach for 5 s): far-band moves; after 10 s the hero applies the
  NPCs' own stuck tools at player speed (`DigOut` pillaring and bridging, block breaking with `BlockWork`) to reach
  the player, exactly as a player would; after 30 s it disengages with a taunt line and resets. A hero without any
  far-band move gets a NOTE in the check ("it will only walk toward a far target").

### 3.11 Adds

| Kind | What | Limits |
|---|---|---|
| `call_guards` | real NPC soldiers of its faction within 32 blocks get the hero's target (`Alerts.callGuards` with a larger radius); nothing is spawned | 1-4 per use |
| vanilla creatures | v1 `summon` (wolf, snow golem, iron golem) plus fox, cat, and for dark heroes zombie or skeleton servants whose target goals are replaced (`[P:summon_types]`) | v1 caps: 3 per hero, 12 per faction |
| spirit adds | a new `HeroAdd` entity with an animated model per add type (wisp, paper familiar, shadow double, flower sprite, drone, focus crystal), stats from a mod catalogue (health 4-20, damage 2-6, one behaviour: chaser, turret, orbiter, decoy, focus) | at most 4 alive per hero, 12 per fight; 10-60 s; no loot, no XP; vanish with the hero; never fly higher than 3 blocks above ground |

### 3.12 Intro, boss bar, defeat

- **Intro**: the first engagement between a player and a hero in an in-game day: the intro animation (1.0-1.5 s,
  super armour, 50% damage reduction), title packets to the engaged players (`ClientboundSetTitleTextPacket` with
  the hero name in its colour, subtitle "title · faction", fade 10/40/10 ticks), the `intro` line.
- **Boss bar** (`ServerBossEvent`): name `★ Name · title  ◆◆◇` (phase pips) plus `⚡` when the ultimate is ready
  (v1 turned the bar gold; a colour change would hide the identity colour); colour = nearest of the 7 vanilla bar
  colours to the hero's primary, red when enraged; overlay `PROGRESS`; shown to players within 32 blocks while boss
  mode is on; a second hero adds a second bar.
- **Yield** (boss mode, killing blow by a player or a player's projectile): the death is cancelled at 1 health, the
  hero kneels for 5 s, invulnerable and passive, with an action-bar prompt to engaged players: "Sneak and use to
  spare, strike to finish". **Spare**: it withdraws home on foot (no combat for 10 minutes, counted as a defeat but
  not a death; no gear dropped); standing and opinions change by personality (honourable +20 toward the player and
  its faction, proud ones mark the player a rival, see `spared` lines). **Finish**: it dies (revival timer, v1 drops,
  morale effects). No choice in 5 s: it collapses (death). Killed by NPCs or in field mode: a 1 s defeat beat, then
  death.
- **Rewards** for a player-defeated hero: vanilla-style XP orbs (`7 x level`, at most 100), its non-kit gear (v1),
  a `hero_crest` trophy item (named, one per hero per player per day), a 25% chance of a token fragment (50% for a
  leader; 4 fragments make a token), enemy morale -10 (-20 for a leader), and the hero's faction remembers.

### 3.13 Fairness rules (enforced by the mod, not by authors)

1. Every telegraph meets the class minimum; no move is both unblockable and undecaled.
2. Stuns on players stay v1's slowness and mining fatigue; knock-ups lift at most 1 block (no fall damage beyond
   vanilla rules); grabs hold at most 1 s.
3. No single hero hit takes a player from full health to 0.
4. At most 2 heroes press one player (attack tokens).
5. Hazard-aware knockback (3.10).
6. Boss mode never starts against creative or spectator players; damage caps apply only to player sources, so NPC
   wars are not affected.

### 3.14 The animation and expression contract (server side)

The server drives state; clients animate from synced data, so a lost packet never desynchronises a fight. On a
`HeroEntity` (a subclass of `FactionNpc`, its own entity type, so ordinary NPCs carry none of this):

| Synced field | Content |
|---|---|
| `DATA_ACTION` (int) | move index (6 bits) + stage (3 bits: idle, windup, active, recovery, stagger, transition, yield, intro) + a 4-bit counter so the same move twice restarts |
| `DATA_PHASE` (byte) | 1-3, +8 when enraged |
| `DATA_EXPRESSION` (byte) | neutral, focused, angry, smug, hurt, pained, joyful, sad, surprised, sleepy, flustered, determined (+ intensity) chosen by a small priority stack: hurt wince 0.5 s > wind-up focus > phase or enrage > personality reaction (kill, spare, yield) > idle |
| `DATA_STANCE` (byte) | the active state or stance (v2 `states`) |

Stage lengths come from the move's `frames`; the client plays `<move>.windup/active/recovery` scaled to them. Blinking
is client-side (random 2-6 s), suppressed while the expression is `focused` during a wind-up or channel, which is
itself a readable tell. The model pipeline checks each animation's length against the frames it must fill.

### 3.15 What format 2 adds for bosses

Top level:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `moves` | list of 0-12 | v1 `skills` | v1 ability shape plus the move fields below; v1 `skills` load as moves |
| `boss` | object | from role and rarity | below |
| `states` | list of 0-3 | v1 `empowered` as one state | named stances or forms (`[P:stance]`); `empower` takes `"state": "<name>"` |

`boss` block: `health_scale` (200-600), `max_hit_share` (5-25), `damage_vs_players` (100-140), `poise` (0, 50-200),
`phases` (1-3 of `{ at, moves: [ids], states, adds, stats, transition: { seconds, line, anim, shockwave, effects } }`),
`reactions` (0-8), `weak_points` (0-4 from 3.8), `enrage` (`after`, `triggers`, `line`), `adds` (catalogue ids and
limits), `intro` (`line`, `anim`, `title`), `defeat` (`yield`: true, lines), `rebirth` (legendary only),
`arena` (`prefers`: `open`, `tight`, `home`; affects move weights), `fog` (darken the world in phase 3; default off).

Move fields (on top of v1 ability fields): `id`, `anim`, `class`, `frames { windup, active, recovery }`, `band`,
`weight`, `phases`, `prefer_when`, `followups [{ move, chance }]`, `repeatable`, `breaks_guard`, `unblockable`,
`super_armor`, `channel { interrupt }`, `marker` (decal shape override).

New forms:

| Form | Who is affected | Parameters (range, default) | Implementation |
|---|---|---|---|
| `combo` | the target in reach, per strike | `strikes`: 2-4, each with its own `frames`, `radius` 0-3 and 1-3 effects | strike timer in `HeroCombat`; total damage capped at 1.25 x the skill cap (each strike is dodgeable) |
| `charge` | everyone along a straight run | `range` 6-16 (10), `speed` 120-200% (160), `wall_stun` bool | ground push like `dash` over more ticks; stops at walls, drops, water; wall hit + `wall_stun` = poise +40 |
| `wave` | grounded enemies the expanding front passes | `radius` 6-16 (10), `speed` 6-12 blocks/s (8), `waves` 1-3, `interval` 0.6-1.5 s | radius grows per tick; hit when `onGround` and the front is within 0.75 of the entity; jump to dodge |
| `rain` | enemies within 1-2 blocks of each impact | `count` 3-12 (6), `radius` 3-8 (5) around the target, `duration` 1-4 s, impact `delay` 0.75-1.0 s | each impact gets its own decal; zone caps per receiver (1.5 x slot) |
| `sweep` | enemies the rotating line passes | `range` 4-12 (8), `arc` 60-180 degrees (120), `duration` 1-3 s, `width` 1-2 | ray each tick from the hero's eyes, stops at blocks (`level.clip`); dodge by leaving the arc or breaking line of sight |
| `grab` | the target, if reached | `range` 2-3 (2.5); `throw` 1-4 strength, `up` 0-1 | always `unblockable` and `heavy`; on a hit holds 1 s then throws; on a miss a 1.2 s recovery |
| `counter` | the attacker | `stance` 0.5-2 s, `absorb` 50-90 % (80), `ranged` bool | `HeroStatus` stance flag read in `onIncomingDamage`; riposte effects within the skill cap |
| `barrage` | the target, shot after shot | `count` 3-10 (5), `interval` 0.15-0.6 s, `projectile` | sequential v1 `shot`s re-aimed at fire time |

New effects: `guard_break` (2-5 s; players' shields through `disableShield`, heroes and NPCs lower theirs),
`poise` (amount 10-60, against heroes in boss fights with each other), `silence` (1-3 s; heroes and casting NPCs
cannot use abilities; nothing on players), `tether` (2-6 s, `max` 4-10 blocks: pulled back when farther), `barrier`
(`hits` 2-8, `seconds` 3-15, optional `focus` add), `spawn_add` (catalogue id, count), `call_guards` (count 1-4).

New conditions (`if`, `prefer_when`, `reactions.when`): `target_blocking`, `target_airborne`, `target_drawing_bow`,
`target_eating`, `target_behind`, `target_above` (blocks), `target_below`, `target_unreachable`, `target_is_player`,
`phase` (1-3), `enraged`, `players_engaged` (count), `allies_near` (count), `adds_alive` (count), `in_home_base`,
`is_night`, `is_raining`, `low_ceiling`, `in_water`.

New passive triggers: `on_phase` (value = phase), `on_enrage`, `on_stagger`, `on_counter`, `on_guard_broken` (its
own attack was blocked), `on_target_heal`, `on_add_died`, `on_yield`, `on_intro`; and `once_per_fight: true` on any
passive (`[P:once_per_fight]`).

Weights for cooldown floors (v1 section 15 table, new rows): `charge` +2 like `dash`; `wave` damage x 1.5 x waves;
`rain` damage x 1.5 x count / 3; `sweep` damage x 1.5 x (arc / 60); `grab` damage + throw strength / 2 + 2;
`counter` riposte weight x 0.75 (conditional); `barrage` damage x count x 0.75; `guard_break` seconds;
`silence` seconds x 2; `tether` seconds / 2; `barrier` hits; `call_guards` count x 2.

Validator additions (pure `HeroBalance`/`HeroRules`): tell minimums per class (ADJUSTED), phase novelty (NOTE), at
least one weakness (NOTE), at least one move per band the hero can be attacked from or a far answer (NOTE), the
uniqueness signature across files: two heroes with the same (archetype, element, weapon, signature move form and
effect types) get a NOTE naming both, which keeps 200 heroes distinct.

---

## 4. Non-combat skills (talents)

### 4.1 Rules

- Up to 3 `talents` per hero, chosen from the catalogue; the **numbers belong to the mod** (tiers I/II/III at hero
  levels 1/10/20), so authors pick ids and the economy stays balanced across 200 heroes. v1 `base_abilities`
  (`aura`, `mend`, `lookout`, `rally`, `drill`) remain valid and are catalogue entries.
- **Scope**: a talent works at the base where the hero is (inside the walls or within 16 blocks of them, not on a
  raid), as in v1. One talent of a leader may be marked `realm: true`: it also works at every other base of the
  faction at tier I.
- **Stacking**: the strongest copy of each talent at a base counts, never the sum of several heroes (v1 aura rule).
  Talents that add affix stacks obey `Affix.maxStacks`.
- **Player-like**: talents change speed, odds, choices, ranges and costs within the bounds the affixes already use;
  they never create items from nothing, place or break blocks outside the existing jobs, or teleport.

### 4.2 Catalogue (PROPOSED numbers by tier I / II / III)

Work and production:

| Id | Effect | Hook (existing code) |
|---|---|---|
| `aura` (v1) | NPCs of the listed roles count +1/+2/+3 stacks of one work affix (diligence, prospector, regrowth, green thumb, mason, marksman, thrifty, hardy, duelist, angler, haggler) | `FactionNpc.affix(a)` + `HeroAuras` cache per base (v1) |
| `master_chef` | meals the base cooks give the eater +10/15/20% saturation; the chef's batches are 10/20/30% faster | `CookingJob`/`ChefJob` work time; `NpcHunger` eat path |
| `forgemaster` | blacksmith work +15/25/35% faster; repairs choose the cheapest combining order; Thrifty +1 stack for smiths | `BlacksmithJob`, `Affix.THRIFTY` via aura |
| `surveyor` | mine site search radius +8/16/24; `MinePlanner` reaches the diamond layer one level sooner when the base has an iron pickaxe | `MineSites` search, `MinePlanner` |
| `forester` | grove size +4/8/12 saplings; Regrowth +1 stack | `FCConfig.GROVE_SIZE` read through the base; `WoodcutterJob` |
| `harvest_blessing` | Green Thumb +1/2/3 stacks for farmers; the farm is replanted first after a harvest | `FarmerJob` |
| `beastmaster` | the pen keeps 2/4/6 more breeding stock; the animal carer breeds as soon as vanilla allows (no idle wait) | `PenJob`, `ButcherJob.BREEDING_STOCK` read through the base |
| `angler_lord` | fishing water searched +16/32/48 blocks farther; Angler +1 stack | `FishingSpots`, `FCConfig.FISHING_RADIUS` read through the base |
| `ember_keeper` | smelter and furnace work +10/20/30% faster; furnaces are refuelled before they go cold | `SmelterJob`, `FurnaceWork` |

Logistics and people:

| Id | Effect | Hook |
|---|---|---|
| `quartermaster` | shortages are warned 0.5/1/1.5 days earlier; haulers carry one more stack; food reserve x0.9/0.85/0.8 | `Logistics.tick/foodReserve`, `HaulerJob` |
| `rally` (v1) | recruits cost 10/15/20% less food | `Recruitment.recruit` (v1 hook) |
| `mentor` | NPCs within 16 blocks gain +15/25/35% work XP; new recruits start at level 1/2/3 | `FactionNpc.awardXp`, `Recruitment.recruit` |
| `renown` | recruitment interval x0.9/0.8/0.7; each recruit gets one extra affix roll | `FactionDirector.recruitment`, `AffixRoller.roll` |
| `drill` (v1) | soldiers gain 1/2/3 work XP per in-game hour by day | `FactionNpc.awardXp` (v1 hook) |
| `festival_host` | unlocks the festival edict at any personality; festival morale +15/20/25 and a day of Inspired regardless of band | morale (2.8), edicts (4.3) |
| `steward` | a second base of the faction run by this hero uses the hero's personality for its director knobs (a governor) | `LeaderBiases` source per base |

Healing and care:

| Id | Effect | Hook |
|---|---|---|
| `mend` (v1) | out of combat, allies within 16 blocks heal 5/7/10% of max health every 10 s | `HeroAuras` (v1) |
| `triage` | in a fight, an ally NPC within 12 blocks under 30% health is healed 4/6/8 health once per 30 s per ally, paid with one food item from the storehouse or the hero's pack | new `HeroAuras` combat tick; `FactionStorage.extract` |
| `revival_rite` | fallen heroes of the faction return 20/30/40% sooner; the leader's revival too | `HeroManager` revival due time |
| `purifier` | NPCs at the base lose poison, wither and hunger effects every 30/20/10 s; zombie villagers brought to the base are cured as a player would (golden apple + weakness potion, when stored) | `FactionNpc.tick`; new job step for the cure |

Scouting and intelligence:

| Id | Effect | Hook |
|---|---|---|
| `lookout` (v1) | line-of-sight alarm within 32/40/48 blocks | `FactionDirector.defenceAlarm` path (v1) |
| `cartographer` | scouts of the base reach +16/32/48 blocks; each report covers the 3x3 sectors around it as explored | `ScoutJob` range, `FactionState.explored` |
| `spymaster` | scouts' base reports carry the garrison count and whether the leader is home; a faction's mustering army is reported as a `HOSTILES` sighting; enemy raids are announced 0/1/2 minutes before they reach the walls | `ScoutJob` report notes, `Report.count`; war council uses the counts (2.5) |
| `pathfinder` | NPCs of the base walk 5/8/10% faster outside the walls | a transient `MOVEMENT_SPEED` modifier while outside |

Diplomacy and trade:

| Id | Effect | Hook |
|---|---|---|
| `envoy` | pact and peace acceptance +0.1/0.2/0.3 for offers this faction makes; opinion of this faction drifts back twice as fast | `DiplomacyRules` (2.6) |
| `intimidation` | rival war councils see this base's defence x1.15/1.3/1.45 (deterrence); strangers' hostile threshold -10 | `FactionDirector` target cost (2.5) |
| `merchant_prince` | trader trips x0.8/0.65/0.5 interval; Haggler +1/2/2 stacks; also trades with wandering traders | `TraderJob` |
| `tribute_master` | peace treaties with weaker factions can carry a tribute (food and iron per day, carried by a hauler on foot); +10/20/30% more accepted | `Diplomacy` + `HaulerJob` trip (phase L7, section 8) |

Craft and lore:

| Id | Effect | Hook |
|---|---|---|
| `sage_of_letters` | the enchanter joins at XP level 7/6/5; the base commissions a library project (15 bookshelves around the table, built by builders from crafted books) | `FactionDirector.enchantingWorthwhile`, new `Project.Type.LIBRARY` in `ProjectLayouts` |
| `architect` | builders +10/15/20% faster (Mason stacks); +2/3/4 house lots; one style monument (a gate arch, a lantern, a statue: morale +5 when built) | `BuilderJob`, `BaseLayout` lots, `Project.Type.MONUMENT` |
| `alchemist` | after Nether wart is in store (ROADMAP phase 3): a brewing project and potions of healing and strength for soldiers | future `BrewerJob` (`[P:brewing_job]`) |
| `engineer` | rail engineer synergy (ROADMAP phase 2): rails laid 20/30/40% faster; minecart hauling unlocked sooner | future `RailEngineerJob` |

Defence and war:

| Id | Effect | Hook |
|---|---|---|
| `warden_of_walls` | defence alarm radius +8/16/24; repair scan budget x1.5/2/2.5 | `FactionDirector.defenceAlarm`, `repairScan` |
| `tactician` | raid parties fall back 10% later and gather at a rally point beside cover; joint pact raids need 10/15/20% less strength | `FactionDirector.raids` (2.5) |
| `war_drummer` | soldiers within 16 blocks fight with +5/8/10% attack speed during a defence; morale cannot drop below 30 at its base | `ATTACK_SPEED` transient modifier; morale floor |

### 4.3 Leader edicts (decisions made visible)

The council (2.6) also chooses **edicts**: faction-wide orders with a cause, a duration and a cost, announced in the
leader's words.

| Edict | Chosen when (utility includes `N`) | Effect |
|---|---|---|
| `festival` | s >= 0.6 or a festival talent; three days of food; not in the last 2 days | consumes 2 hunger points of food per NPC; morale +15 |
| `martial_law` | the base was raided twice in a day and (a >= 0.6 or c >= 0.7) | 1 day: workers stay inside the walls, +1 home defender, morale -5 |
| `conscription` | a >= 0.6 under threat (`T` > 1.3) | retrain 2 idle or civilian workers to soldiers now |
| `open_gates` / `closed_gates` | s >= 0.65 / s <= 0.35 or after a stranger killed an NPC | strangers' standing threshold +20 / -20 for 2 days |
| `trade_drive` | g >= 0.6 and storage above 60% | trader trip interval halved for a day; sells down to half of the usual keep amounts |
| `bounty` | grudge >= 60 against a faction | member players who kill that faction's heroes or leader get paid from the storehouse (real items: emeralds, iron, food) |
| `tribute_demand` | m >= 0.6, power ratio >= 2 against a known weaker faction at peace | a tribute is asked; refusal means War (later phase) |
| `fortify` | c >= 0.6 after a base loss | builders put repairs first for a day; watchtower project (when ROADMAP phase 3 exists) |

Whims (v >= 0.7, at most one a day, chance `0.2 x v`): an unplanned festival, a raid announcement with a grand name,
a gift to a random known faction (+10 opinion), or the `luck` whim (one random harmless good outcome: a miner's extra
ore roll, a trader's discount) for airhead and genki temperaments.

### 4.4 Format

```json
"talents": [
  { "id": "harvest_blessing", "realm": true },
  { "id": "festival_host" },
  { "id": "aura", "affix": "diligence", "roles": ["builder", "miner"] }
]
```

Unknown id: PART LEFT OUT with the list; a second `realm` talent: ADJUSTED (only the first counts); `aura` keeps the v1
fields and limits.

---

## 5. Progression, recruiting more heroes, and what players can do

### 5.1 Progression

- **Levels 1-30** on the NPC curve (`AffixRoller`: 40 + 30n per level) with v1 combat XP and growth profiles; a
  leader also earns XP from faction events (a base built +40, a raid won +100, a pact sworn +40, an advancement +20,
  a festival +20).
- **Unlocks**: talents tier II at 10 and III at 20; moves by `unlock_level`; the third boss phase at 15 when the file
  marks it `unlock_level: 15`; at 25 an **awakened ultimate** (a second ultimate variant the file may define; the
  first is replaced).
- **Duplicate tokens** (v1): XP worth two levels.
- **Bonds**: heroes of one faction who fight together gain bond points (+1 per shared fight, +5 per shared won raid);
  at 20 bond points a pair unlocks a small joint bonus (each gets +5% damage while the other is within 12 blocks)
  and bond dialogue; `relationships` in the file (friend, rival, sibling, mentor, student, sworn_enemy, admirer)
  start the bond at +10 (friendly kinds) or block it (rival, sworn enemy: they compete for kills instead, with lines).

### 5.2 A faction's heroes

- `heroes.maxPerFaction` raised to 6 (leader + 5; PROPOSED; 1-16). A hero above the cap cannot join.
- Non-leader heroes take **posts** the leader assigns at the council: `general` (leads raids; the highest a x level),
  `steward` (runs a second base; a talent or the highest level), `envoy` (diplomacy talents apply faction-wide at
  tier I), `warden` (stays home, `protects: heart`), or their own `duty` (v1). Posts show on the station.

### 5.3 Recruiting more heroes

| Source | How | Notes |
|---|---|---|
| Hero tokens (v1) | used at the station or heart by a member, or by the director from the storehouse | unchanged; compatibility below decides between candidates |
| Wandering heroes | every 2 in-game days (`heroes.wandererInterval`), while fewer than 3 wander, a free hero appears with a small camp (a campfire and a bedroll, removed when it leaves) on dry land at least 96 blocks from any base; it stays 3 days | scouts report it (new report kind `WANDERING_HERO`, appended); players see it only by finding it |
| Fallen factions | 2.9: heroes of an eliminated faction become wanderers after a day; the exiled leader too | loyal and honourable ones refuse the conqueror for 5 days |
| Rescue | a hero held in a pillager outpost or woodland mansion (a wanderer event variant) joins the faction whose raid clears it (ROADMAP phase 3 raids) | |
| Defection | a hero whose loyalty score `L` falls under 0.35 while its base morale is <= 10, or right after its faction betrayed someone, walks (physically) to the strongest known faction whose leader would accept it | announced to both factions; a member player who talks it down at the station (gift of a liked item) cancels it once |

**Recruitment conditions** come from the wanderer's personality (`recruit.condition`, authored; default by
temperament):

| Condition | Who (default) | Fulfilled by |
|---|---|---|
| `duel` | battle maniac, hot-blooded, delinquent | defeating it in a duel (yield, 5.4) by the faction's champion: a hero sent by the council, or a member player |
| `payment` | merchant, glutton, greedy (g >= 0.7) | goods carried to the camp: by an NPC envoy on foot from the storehouse, or by a player |
| `reputation` | righteous, gentle giant, stoic | the faction's reputation >= 60 and no surprise betrayal in its history |
| `gift` | deredere, charmer, airhead | three liked items given by a member player or an envoy |
| `visits` | shy, melancholic | visited on three different days by a member hero or player |
| `cause` | yandere, melancholic, avenger types | the faction is at war with the faction the hero hates (`relationships` sworn enemy or a grudge it carries) |
| `rescue` | captive variant | its prison cleared |

**Compatibility** with the faction's leader (pure `logic/leader/Compatibility`):

```
comp = 1 - (0.4 |h_hero - h_leader| + 0.3 |a_hero - a_leader| + 0.2 |s_hero - s_leader| + 0.1 |m_hero - m_leader|)
       + temperament affinity (Appendix A: admires +0.2, clashes -0.2)
       + relationship with the leader (friend, sibling, mentor, student, admirer +0.3; rival -0.2; sworn enemy -1)
joins when comp >= 0.45 (a `payment` condition lowers this to 0.3: mercenaries work for anyone)
loyalty L = 0.6 l_hero + 0.4 comp + bond with the leader / 100        defection check when L < 0.35 (above)
```

The council sends for a wanderer when it can meet the condition and the hero's best post (5.2) fills a gap the
director sees (no general, a needed talent, under the cap), weighted by ambition.

### 5.4 What players can do with heroes

| Action | Who | How |
|---|---|---|
| Talk | anyone near a hero | right-click: v1 hero sheet plus a talk tab with personality lines (greeting by standing, small talk, what the leader is planning when the player is an Officer or higher) |
| Give gifts | members | sneak-use an item on the hero: liked items raise its bond with the player and the player's standing (up to +20 a day); disliked ones lower them |
| Petition the leader | Officers and Champions | the station screen's council tab (2.7) |
| Spar | members | `/fc duel <hero>` beside a friendly hero: a non-lethal duel in a ring of decals (radius 12) that ends at the yield; both gain XP; the player learns the moves in a safe fight |
| Challenge an enemy leader or hero | anyone | `/fc duel <hero>` beside it, or a Champion's petition; accepted when `h >= 0.5` or the hero is a duellist temperament; guards stay out of the ring; the winner's side gains (enemy morale -15, a one-day peace offered by honourable losers, standing +30 with the winner's faction); refusing costs the refuser's faction morale -5 when it is a duellist temperament |
| Spare or finish | the killer in boss mode | 3.12 |
| Recruit a wanderer | members | meet its condition in person (5.3); the hero joins the player's faction |
| Use tokens | members | v1 |
| Ask for an escort | members with bond >= 20 and rank Member+ | `/fc hero follow <id>`: a non-leader hero follows the player for one in-game day within the map (leash 24 blocks, walks, chunk-loads as a traveller, returns home afterward); leaders refuse, proud temperaments refuse below bond 40 |
| Commission work | members | a forgemaster hero repairs the player's gear at the base anvil with the player's own materials (vanilla anvil costs); a master chef cooks the player's raw food |
| Bounties | members | edict `bounty` (4.3) |

---

## 6. Implementation map

### 6.1 New pure logic (no Minecraft classes, unit-tested)

| Class | Role |
|---|---|
| `logic/leader/Personality` | record of 8 traits + temperament; `effective(k)` |
| `logic/leader/Temperaments` | Appendix A table: defaults, special rules, affinities, economy focus, recruit condition |
| `logic/leader/LeaderBiases` | every knob of 2.5 and 2.7 as a function of `Personality` |
| `logic/leader/DiplomacyRules` | proposal, acceptance, break, coalition fear, peace offers (2.6); seeded noise |
| `logic/leader/OpinionLedger` | opinion events, drift, grudges, reputation (2.6, 2.7) |
| `logic/leader/Morale` | morale events, drift, bands (2.8) |
| `logic/leader/LeaderDraft` | seeded draw with the rules of 2.2 |
| `logic/leader/FactionNameGen`, `Palette`, `BannerDesign`, `BaseStyles` | 2.3: names, colour distance and nearest dye/chat colour, emblem table, style family table |
| `logic/leader/Compatibility` | 5.3 |
| `logic/hero/BossRules` | boss health, damage cap, poise, stagger, enrage, attack tokens, telegraph minimums (3.3-3.9) |
| `logic/hero/MoveSelector` | 3.4 scoring and seeded pick |
| `logic/hero/Talents` | catalogue and tier numbers (4.2) |

### 6.2 New game-side classes

| Class | Role |
|---|---|
| `faction/FactionIdentity`, `faction/Factions` | identity record and lookups (M1) |
| `net/FactionIdentityPayload`, `client/ClientFactions` | identity sync (M1) |
| `campaign/LeaderCouncil` | daily council per faction: diplomacy, edicts, posts, wanderer recruitment |
| `campaign/Relations` | relation states saved in `CampaignData` |
| `campaign/Wanderers` | wandering hero events and camps |
| `hero/HeroEntity` | `FactionNpc` subclass with the synced boss fields of 3.14 |
| `hero/BossController` | boss mode, phases, poise, enrage, reset, tokens, yield; ticked from `HeroEntity.tick` |
| `hero/Telegraphs` | decal display entities: create, grow, remove, clean-up on load |
| `hero/HeroAdd` (entity) | spirit adds |
| `hero/Duels` | duel rings, rules and outcomes |
| `combat/HeroMoveGoal` | replaces v1 `HeroAbilityGoal` for heroes with `moves`: the loop of 3.4 |

### 6.3 Changes to existing classes

| Class | Change |
|---|---|
| `faction/FactionType` | append 4 slots; `humans()` returns 8; fields become legacy defaults (M1-M2) |
| `campaign/CampaignData` | FORMAT 3; `relations`, `coalitionAgainst`; identities via `FactionState` |
| `campaign/FactionState` | `identity`, `leaderHero`, `leaderPersonality` snapshot, `opinion`, `reputation`, `playerOpinion`, `memberRank`, `edicts` |
| `campaign/BaseState` | `morale`, `stewardHero` |
| `campaign/CampaignStarter` | leader draft first; identity per job; radius check for N |
| `campaign/BaseBuilder` | style from identity; real banners; leader spawn; base name from `short_name` + `seat` |
| `campaign/FactionDirector` | every constant of 2.5 read through `LeaderBiases` for the base's ruler (leader or steward); vengeance raids; ambush rule; council hook; leaderless rules |
| `campaign/Diplomacy` | `hostile`/`allied` over relations; coalition rewritten; messages through identities |
| `campaign/Recruitment`, `campaign/Logistics` | reserve, interval and talent multipliers |
| `campaign/CampaignManager` | elimination frees heroes; victory with pacts (2.6) |
| `combat/CombatRules` | `isEnemyPlayer` adds the standing threshold (2.7); `factionOf` knows adds |
| `combat/WithdrawGoal`, `NpcMeleeAttackGoal` | morale-adjusted thresholds; heroes' retreat from personality; attack tokens |
| `event/FCEvents` | `onIncomingDamage`: boss damage cap, poise, counters, standing events; `LivingDeathEvent`: yield cancel; `PlayerLoggedInEvent`: identity payload; container open: theft event |
| `npc/FactionNpc` | name tag colour from identity RGB; morale stacks in `affix()`; talent hooks |
| `npc/NpcNames` | `random(culture, rng)` |
| `structure/BaseLayout`, `ProjectLayouts` | `BaseStyle` parameter; banner block entities; sign text from `short_name` |
| `work/MaterialRules` | 8 stone blocks added to `STONE_BLOCKS` (2.3) |
| `work/TraderJob`, `ScoutJob`, `MinePlanner`, `HaulerJob`, ... | the knob and talent reads of 2.5 and 4.2 |
| `info/BaseInfo`, `client/InfoScreen` | Leader & heroes section (replaces "Great People"), council line (current plans), relations, morale, the player's standing and rank; a rendered model of the leader in the screen |
| `net/MapPayload` | `Marker` gains an RGB colour (0 = by kind); registrar version raised |
| `command/FCCommands` | slugs; `/fc factions`, `/fc leader`, `/fc relations`, `/fc duel`, `/fc hero follow`; admin `leader set|release`, `personality <faction> <trait> <value>`, `relation <a> <b> <state>`, `morale <base> <value>`, `wanderer spawn [hero]`, `boss test <hero> [players]` |
| `config/FCConfig` | `[leaders]` and `[boss]` sections (6.5); `humanFactions` 2-8 |
| `tools/sim.py` | per faction: leader, temperament, relations, opinions, morale, edicts, raids, pacts made and broken; per hero: boss fights, phases reached, staggers, yields, spares |

### 6.4 Saved data (all new keys optional; missing = neutral or empty)

`CampaignData`: `format` 3, `relations` list `{a, b, state, since, until, declaredAt}`, `coalitionAgainst`.
`FactionState`: `identity` compound, `leader` (hero id), `leaderPersonality` (8 ints + temperament), `opinion` map,
`reputation`, `playerOpinion` map, `ranks` map, `edicts` list, `grudgeTop`. `BaseState`: `morale`, `steward`.
Hero state (v1 `HeroState`): `bond` map, `posts`, `exiled`, `spareCooldowns`. Wanderers: `wanderers` list
`{hero, camp pos, since, condition progress}`.

### 6.5 Config (PROPOSED)

`[leaders]`: `enabled` true, `seed` 0 (= random), `fixed` [], `allowLegendary` true, `personalityStrength` 100
(0-200), `reviveMultiplier` 150 (100-300), `permadeath` false, `renameLegacy` false, `diplomacy` true, `betrayal`
true, `sharedVictory` false, `strangerStance` true, `edicts` true, `morale` true.
`[boss]`: `enabled` true, `healthScale` 100 (25-300, multiplies every hero's `health_scale`), `maxHitShare` 12,
`damageVsPlayers` 115, `playerScale` 50 (% per extra player), `attackTokens` 2, `introTitles` true, `bossBar` true,
`yield` true, `telegraphDecals` true, `enrage` true.
`[heroes]` (v1 section) gains `maxPerFaction` 6, `wandererInterval` 48000, `maxWanderers` 3, `bonds` true.

---

## 7. Testing and campaign verification

Unit tests (`src/test/java/com/factionclash/logic/leader/`, `.../logic/hero/`):
- `LeaderBiasesTest`: all-50 personality returns every current constant (36000-tick interval, 0.667, 1/3, 3, 12,
  0.4, 14, 10, x1, 1 bed, 70%); monotonic in each listed trait; clamps; `k = 0` equals neutral for any personality.
- `DiplomacyRulesTest`: proposal and acceptance thresholds; break inclination x opportunity (the worked cases of
  2.6); honour >= 0.75 never surprise-breaks; tsundere first refusal; minimum agreement age; with `k = 0` every
  survivor joins the coalition (today's rule); the bandwagon case.
- `OpinionLedgerTest`, `MoraleTest`: event deltas, caps per day, drift, bands.
- `LeaderDraftTest`: seeded determinism; each rule; relaxation order; both genders; war balance.
- `FactionNameGenTest`, `PaletteTest`, `BannerDesignTest`: determinism, uniqueness, no `mojang` pattern, nearest dye.
- `CompatibilityTest`, `TalentsTest` (tier numbers, stacking rule).
- `BossRulesTest`: health scaling keeps the share, player count cap, damage cap, poise factors, parry window, stagger
  growth, enrage timing and the +25% ceiling, tell minimums, attack tokens.
- `MoveSelectorTest`: bands, novelty, followups, reactions override, seeded picks.
- Format: the reader and validator tests of v1 extended to every new field, form, effect, condition and trigger.

GameTests (own `batch` per job-running test, `GameTestBases.register`, spawns at known relative positions):
`identityRenamesNameTagAndStation` (static), `legacyIdentityUnchanged` (static), `leaderSpawnsAtStation`,
`truceStopsTargeting`, `pactMakesSameSide`, `strangerBelowThresholdIsAttacked`, `strangerAboveThresholdIgnored`,
`moraleShakenSlowsWork`, `talentAuraRaisesAffix`, `bossModeOnPlayerEngage` (mock player: bar shown, health scaled,
share kept), `bossDamageCapAndPoise`, `phaseTransitionAtThreshold`, `staggerOpensWindow`, `guardBreakDisablesShield`,
`waveMissesJumpingTarget`, `chargeWallStagger`, `yieldThenSpare`, `yieldThenFinish`, `telegraphDecalsCleanedUp`,
`attackTokensLimitPressers`, `wandererJoinsOnPayment`, `duelRingKeepsGuardsOut`.

Campaign check (`docs/HANDOFF.md` section 4), 8 days, 4 leaders forced to contrasting temperaments (battle maniac,
schemer, sweet-natured, stoic) with `leaders.fixed`:
- The personalities must show in the numbers: the battle maniac launches at least twice as many raids as the
  sweet-natured leader by day 6; the sweet-natured one offers a pact before day 4; the schemer breaks at least one
  agreement or never gets one; garrison sizes and ranged shares follow 2.5 within rounding.
- Morale traces per base, leader deaths and revivals, vengeance raids, no stuck leader (recoveries), tick time
  before and after (target: under +1 ms for 4 leaders and 12 other heroes).
- A second run with `personalityStrength = 0` and `diplomacy`, `strangerStance`, `morale`, `edicts` off must match
  the legacy campaign's raid timing and recruitment mix.
- Boss mode on the `clientViewer` client: intro title, bar, phase pips, decals growing and vanishing, a stagger, a
  yield with spare and with finish; two players (the viewer plus an RCON-driven second account if available, else a
  GameTest for the scaling).
- An old-format world (format 2) loads: legacy names kept, leaders assigned, the old pact converted.

---

## 8. Phasing and dependencies

The v1 implementation phases (`HERO_FORMAT.md` section 32: format and checking, loading, visible heroes, ability
core, control and passives, war and tokens) remain the base; animated models replace the skin part of v1 phase 3.

| Phase | Content | Needs |
|---|---|---|
| L1 | M1 identity layer (no behaviour change), client sync, slugs | nothing |
| L2 | `Personality`, `Temperaments`, `LeaderBiases` with director hooks (neutral = today) | L1 |
| L3 | M2 extra slots, leader draft, identity generation, base styles, banners, tint layer, leader spawn and court duty | L1-L2, v1 phases 1-3 (hero files, entity) |
| L4 | Diplomacy v2, opinion, reputation, coalition, standing and stances, ranks, morale, edicts | L2-L3 |
| L5 | Boss layer: `HeroEntity` sync, boss mode, moves and new forms, telegraph decals, phases, poise, reactions, enrage, arena checks, yield | v1 phases 4-5, the animation pipeline |
| L6 | Talents catalogue and hooks | v1 phase 5 (base abilities) |
| L7 | Wanderers, compatibility, defection, duels, escort, commissions, bonds | L4-L6 |
| L8 | Adversarial review, fixes, campaign checks (section 7), docs (README "Factions and leaders", "Heroes"; BALANCE "Leaders", "Bosses", "Talents"; ROADMAP phase 4 rewritten), regenerated config, jar | all |

L1 and L2 can start at once in parallel with the v1 format work: they are refactors that keep every test green.

---

## 9. Open questions for the owner (with the proposed answer)

1. **How many factions?** 2-8, default 4 (proposed). More than 8 needs maps of radius 400+ and costs tick time.
2. **Boss numbers only against players?** Proposed yes: heroes stay army-scale in NPC wars and become bosses when a
   player engages them. The alternative (always boss health) makes heroes dominate NPC wars.
3. **Betrayal on by default?** Proposed yes (schemers and tricksters are part of the drama), with `leaders.betrayal`.
4. **Strangers attacked on sight by hostile leaders?** Proposed yes, only below a standing threshold the player
   causes (2.7); never at first sight by default.
5. **Spare or finish** at a hero's defeat: proposed on (`boss.yield`).
6. **Leader permadeath** off (revival after 15 minutes) by default; on makes succession happen.
7. **Legacy campaigns**: keep their four names and get leaders (proposed), or be renamed after the leader
   (`renameLegacy`).
8. **The Hollow**: stays leaderless in this design; villain heroes as lair bosses ("Hollow tyrants") fit a later
   phase together with ROADMAP phase 3 dungeons.
9. **Players leading NPC raids** (a Champion calls the raid party to follow): left out here; a natural next step
   after ranks.

---

## Appendix A: temperaments (defaults, 0-100)

a = aggression, h = honour, m = ambition, c = caution, g = greed, s = sociability, l = loyalty, v = volatility.
"Focus" is the default `economy_focus`; "Recruit" the default wanderer condition. Admires/clashes give the
affinity of 2.6 and 5.3. Labels are plain English ids; `research-archetypes.md` Part 5 describes the same tropes under
their usual anime names (cool_headed, shy and sweet_natured are the cool, quiet and openly affectionate types).

| Temperament | a | h | m | c | g | s | l | v | Special rule | Focus | Recruit | Admires / clashes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| hot_blooded | 80 | 70 | 65 | 20 | 30 | 65 | 75 | 55 | always accepts duels; vengeance raids even when a < 0.7 | military | duel | stoic / schemer |
| stoic | 50 | 80 | 45 | 65 | 30 | 30 | 85 | 10 | never surprise-breaks (as h >= 0.75) | mining | reputation | gruff_veteran / airhead |
| cool_headed | 45 | 70 | 55 | 70 | 35 | 35 | 70 | 15 | opinion weighs half in pact maths (power first) | craft | reputation | ancient_sage / genki |
| tsundere | 65 | 65 | 60 | 45 | 40 | 45 | 70 | 45 | refuses the first pact offer from each faction | military | duel | hot_blooded / charmer |
| shy | 25 | 70 | 30 | 80 | 30 | 35 | 80 | 20 | seeks a pact with the strongest known neighbour | farming | visits | big_sibling / delinquent |
| sweet_natured | 25 | 75 | 40 | 50 | 25 | 90 | 80 | 35 | offers peace to every faction at first contact | food | gift | gentle_giant / tyrant |
| yandere | 70 | 40 | 55 | 35 | 35 | 40 | 95 | 60 | hard enrage when its favoured is hurt; never forgives its top grudge | military | cause | big_sibling / charmer |
| genki | 70 | 60 | 75 | 25 | 35 | 85 | 65 | 80 | builds ahead (+1 bed threshold); impulsive raids (whims) | growth | gift | charmer / sleepy |
| lone_wolf | 55 | 60 | 40 | 60 | 40 | 10 | 40 | 30 | never proposes pacts; accepts only when T >= 2 | scouting | duel | stoic / sweet_natured |
| proud_noble | 55 | 70 | 80 | 50 | 60 | 50 | 60 | 30 | opinion losses x1.5; builds monuments | trade | reputation | righteous / delinquent |
| delinquent | 75 | 55 | 55 | 30 | 45 | 55 | 85 | 55 | territorial (nearest rival first); respects whoever beats its leader in a duel (+30) | military | duel | hot_blooded / proud_noble |
| chuunibyou | 65 | 60 | 80 | 30 | 30 | 60 | 65 | 75 | opens boss fights with its ultimate when ready; grand raid names | military | duel | eccentric_genius / cool_headed |
| airhead | 35 | 60 | 40 | 40 | 30 | 75 | 60 | 95 | doubled noise; `luck` whim | food | gift | sweet_natured / perfectionist |
| big_sibling | 40 | 75 | 45 | 60 | 30 | 75 | 85 | 30 | protective pacts with weaker factions; `drill`-like XP at home | people | reputation | shy / tyrant |
| trickster | 55 | 25 | 60 | 45 | 55 | 65 | 40 | 70 | ambush bonus doubled; surprise breaks | trade | payment | charmer / righteous |
| sleepy | 25 | 60 | 25 | 55 | 30 | 45 | 60 | 20 | few raids; +10% work at home; bold when below half health | craft | visits | ancient_sage / genki |
| battle_maniac | 95 | 55 | 70 | 5 | 25 | 45 | 55 | 60 | targets the strongest beatable rival; accepts every duel | military | duel | hot_blooded / coward_who_rises |
| gentle_giant | 20 | 85 | 30 | 60 | 20 | 70 | 85 | 15 | spares beaten factions always; helps allies' defence | building | reputation | sweet_natured / tyrant |
| schemer | 45 | 15 | 85 | 70 | 70 | 45 | 30 | 25 | pacts with weaker neighbours, broken when strong | trade | payment | trickster / righteous |
| righteous | 60 | 95 | 50 | 45 | 15 | 60 | 80 | 25 | grudge against any conqueror; always joins coalitions | defence | reputation | gentle_giant / schemer |
| melancholic | 40 | 70 | 25 | 65 | 20 | 25 | 75 | 20 | its top grudge never drifts | craft | cause | stoic / genki |
| glutton | 40 | 55 | 45 | 50 | 65 | 70 | 60 | 50 | food focus; raids for food while rationing | food | payment | sweet_natured / perfectionist |
| perfectionist | 50 | 70 | 70 | 65 | 40 | 35 | 65 | 10 | morale baseline -10, Diligence +1 stack at home | craft | reputation | cool_headed / airhead |
| charmer | 35 | 50 | 55 | 45 | 45 | 90 | 45 | 50 | pact acceptance of its offers +0.15 | trade | gift | genki / tsundere |
| ancient_sage | 30 | 75 | 55 | 80 | 30 | 50 | 75 | 10 | no raids before day 3; coalition builder | lore | reputation | cool_headed / chuunibyou |
| coward_who_rises | 20 | 65 | 35 | 90 | 35 | 60 | 70 | 40 | avoids war; morale +15 and bold when its base is attacked | farming | visits | big_sibling / battle_maniac |
| gruff_veteran | 55 | 70 | 40 | 65 | 35 | 40 | 80 | 15 | defence and drills (`drill`-like XP) | defence | duel | stoic / chuunibyou |
| eccentric_genius | 35 | 50 | 75 | 55 | 50 | 40 | 50 | 70 | crafting and lore projects first | lore | gift | chuunibyou / gruff_veteran |
| merchant | 40 | 45 | 60 | 50 | 90 | 70 | 40 | 35 | peace for tribute; sells hard | trade | payment | glutton / righteous |
| tyrant | 85 | 30 | 95 | 35 | 70 | 25 | 40 | 40 | tribute demands; expands first | growth | payment | battle_maniac / gentle_giant |

## Appendix B: base style families

Wall / frame / floor / roof / roof stairs / perimeter, plus the seat noun; accents (wool, carpet, banners, glass)
come from the identity colours. Blocks marked * are added to `MaterialRules.STONE_BLOCKS` so repairs can substitute.

| Family | Wall | Frame | Floor | Roof (+ stairs) | Perimeter | Seat | Default archetypes |
|---|---|---|---|---|---|---|---|
| `ember_fortress` (legacy Ember) | bricks | dark oak log | dark oak planks | deepslate tiles | polished blackstone bricks | Keep | fire dragonkin, oni warlords |
| `grove_lodge` (legacy Grove) | oak planks | oak log | spruce planks | spruce planks | mossy stone bricks | Lodge | elves, dryads, beast tamers |
| `tide_port` (legacy Tide) | prismarine bricks | birch log | birch planks | dark prismarine | smooth sandstone | Port | sirens, corsairs, kappa |
| `stone_hold` (legacy Stoneguard) | stone bricks | spruce log | spruce planks | cobblestone (stone brick stairs) | stone bricks | Hold | knights, guardians |
| `shrine_pavilion` | birch planks | stripped mangrove log | spruce planks | deepslate tiles | stone bricks | Shrine | shrine maidens, priests, diviners, kitsune |
| `cherry_court` | cherry planks | stripped cherry log | birch planks | deepslate tiles | smooth stone* | Court | idols, fan dancers, magical girls |
| `bamboo_village` | bamboo planks | jungle log | bamboo planks | spruce planks | cobblestone | Village | ninja, wanderers, tanuki |
| `frost_hall` | polished diorite* | spruce log | spruce planks | dark prismarine (stairs) | stone bricks | Hall | snow spirits, frost knights |
| `desert_court` | smooth sandstone | acacia log | cut sandstone* | smooth sandstone (sandstone stairs) | sandstone | Court | sun priests, desert wanderers |
| `dark_manor` | polished blackstone bricks | dark oak log | dark oak planks | deepslate tiles | blackstone | Manor | vampires, demon lords, reapers |
| `ivory_palace` | quartz bricks* | birch log | birch planks | smooth stone* | polished diorite* | Palace | paladins, angels, noble fencers |
| `jungle_haven` | jungle planks | jungle log | jungle planks | mossy cobblestone | mossy stone bricks | Haven | beast-kin, druids |
| `tuff_workshop` | tuff bricks* | spruce log | spruce planks | deepslate tiles | polished tuff* | Workshop | androids, inventors, alchemists |
| `mud_town` | mud bricks* | acacia log | acacia planks | mud bricks* (stairs) | red sandstone* | Town | martial artists, merchants |
| `deepslate_keep` | deepslate bricks | dark oak log | spruce planks | deepslate tiles | cobbled deepslate | Keep | stoic warriors, oni |
| `coral_port` | dark prismarine | mangrove log | mangrove planks | prismarine bricks | sandstone | Port | sea dragonkin, merfolk |

## Appendix C: a format 2 excerpt (original, illustrative)

```json
{
  "format": 2,
  "id": "example_lantern_fox",
  "name": "Example Lantern Fox",
  "archetype": "kitsune",
  "gender": "female",
  "personality": {
    "temperament": "cool_headed",
    "traits": { "aggression": 40, "honour": 75, "ambition": 60, "caution": 75, "greed": 30,
                "sociability": 45, "loyalty": 70, "volatility": 20 },
    "likes": ["sweet_berries", "lantern"], "dislikes": ["rotten_flesh"]
  },
  "leader": {
    "title": "Keeper of the Ninefold Lantern",
    "faction_names": ["Ninefold Lantern Court", "The Quiet Lantern Court"],
    "short_name": "Lantern Court",
    "motto": "A lantern burns longer than a bonfire.",
    "colors": { "primary": "#D9582B", "secondary": "#F4ECD8", "accent": "#2E3A59" },
    "banner": { "base": "orange", "patterns": [ { "pattern": "triangles_top", "color": "white" },
                                                 { "pattern": "circle", "color": "blue" } ] },
    "base_style": "shrine_pavilion",
    "culture": "east",
    "economy_focus": ["lore", "food"]
  },
  "talents": [ { "id": "sage_of_letters", "realm": true }, { "id": "revival_rite" } ],
  "boss": {
    "health_scale": 400, "poise": 90,
    "weak_points": ["iron", "channel"],
    "phases": [
      { "at": 100, "moves": ["fan_cut_combo", "foxfire_barrage", "lantern_ring"] },
      { "at": 60, "moves": ["fan_cut_combo", "foxfire_rain", "lantern_ring", "mirror_counter"],
        "adds": [{ "id": "wisp", "count": 2 }],
        "transition": { "seconds": 1.2, "line": "Then let the lanterns speak.", "shockwave": { "radius": 4 } } },
      { "at": 25, "moves": ["foxfire_rain", "nine_tail_sweep", "mirror_counter"], "unlock_level": 15 }
    ],
    "reactions": [ { "when": "target_drawing_bow", "move": "foxfire_barrage", "chance": 60 },
                   { "when": "target_blocking", "move": "fan_cut_combo", "chance": 50 } ],
    "enrage": { "after": 240, "triggers": ["heart_attacked"] },
    "intro": { "line": "You walked a long way to be turned back." },
    "defeat": { "yield": true }
  }
}
```

(The `moves` entries, kit, model and lines are omitted from this excerpt; they follow 3.15 and v1.)
