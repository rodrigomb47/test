# Heroes v2: the boss heroes (the Wither and the Ender Dragon)

Two heroes outside the 200, won by beating the vanilla bosses. The first time the Wither or the Ender Dragon dies in
a world, a chest appears on the ground where it fell, holding that boss's hero token. A member of a faction who
brings the token inside one of the faction's bases makes the hero join that faction. Until then the token can be
fought over, dropped and stolen.

Status: design, 2026-11-18, reviewed the same day (section 12), revised the same day to the owner's answers (the
note below; sections 11 and 12.1) and the revision reviewed again the same day (12.2). Nothing here is implemented.
Numbers marked PROPOSED go to BALANCE.md when they are built.
Companions: `HEROES_V2.md` (section 8.2 points here, section 15.8 holds the tasks of section 10 below; 6.14, 6.17 and
6.18 are the fight's fairness, terrain and explosive rules), `ARTIFACTS.md` (the signature tier and bond format of 7.5
and 16.4, slots by level and fixed strength of 3 and 4.2, each hero's own choice of 10, growth of 7.8, upgrades of
9.8, the death drops of 11), `MOBILITY_AND_WORK.md` (flight and its fairness rules), `docs/design/breaching.md` (how
anyone breaches a wall), `SLICE_PLAN.md` section 3 (how a hero is specified; sections 4 and 5 below follow it),
`SOUND.md` (sound sets and vanilla references), `notes/kit-gaps.md` (sections "Boss heroes" and "Boss heroes
revision": the format pieces these two kits need), and the owner-facing kit sheets `kits/the_wither.txt` and
`kits/the_ender_dragon.txt`.

**Revision of 2026-11-18** (the owner's answers to section 11, `HANDOFF.md` section 2, request 11; the two revisions
before it: `ARTIFACTS.md`'s and the battle rules of `HEROES_V2.md` 6.17 and 6.18):
- **No personal names** ("3 no personal names"): they are "The Wither" and "The Ender Dragon", nothing more (2.3).
- **Back to her token** ("4 token"): a boss hero whose faction loses its last base turns back into her token where she
  stands, keeping her level. Settled; the switch that made her a free hero instead is gone (3.8, 3.13).
- **The Wither's terrain under the new rules** ("5 its ok for any hero or npc that usrs explosives to breach walls"):
  her breach is no longer an exception. Her blue skulls (Ruin Skull, Stray Skulls) and Birth Blast are explosions by
  nature, as the vanilla boss's are, and break blocks by `HEROES_V2.md` 6.18: the blue skulls with vanilla's own
  blue-skull rule (they bite through anything that is not wither-immune, obsidian included), Birth Blast at the
  ceiling of 6.18 (power 4, TNT's, inside its red disc). Her black skulls break nothing. Walled in, her body withers
  the blocks around it, vanilla's own rule. On a raid she breaches like anyone, with her blue skulls at the planned
  breach spot; her hands are a miner's, never a breach tool (4.5).
- **The Ender Dragon and the edge** ("its fine for heroes abilities to push someone of a cliff or to lava"): Wing
  Gust, Crescent Tail, her dives and her phase shockwave push at full strength and are aimed at cliffs, lava and an
  End island's rim (6.17). Her crystals' shatter pushes at full strength and still breaks no block; she does not smash
  blocks in flight as the vanilla dragon does; none of her moves is an explosion (5.5, 5.6).
- **The signature artifacts under the artifact revision**: fixed strength, her one slot at level 1, each hero's own
  choice. Each takes one of the two ways an artifact may now get stronger: **Hollow Star grows** by its own design
  (it feeds on the withering its wearer deals) and is never upgraded; **Spirecore is upgraded** at the Artificer's
  Bench with Artifice 2 research and never grows (4.13, 5.13). She keeps hers by her own choice; one held by an ally is
  held as lent by her; an enemy hero wearing it is her first target (4.13).
- The fairness check of 6.4, the balance of 7, the format pieces of 8, the tests of 9, the tasks of 10, the questions
  of 11 and both kit sheets (kit version 3) follow. The looks of 4.2 and 5.2 are unchanged.

Reading rules: the hero design's rules hold here unless this file says otherwise. In particular the owner's decisions
of `HEROES_V2.md` section 6 (one combat mode, equal treatment, no attack tokens, no yield, no civility in battle), the
10-minute revival with its 30 s floor (3.4), every ability from level 1 with power growing to full at level 20 (section
8; it concerns abilities: artifacts follow the owner's own answer instead, slots by level and fixed strength), one body
form for good (3.6), "heroes may be unfair" (`MOBILITY_AND_WORK.md` 19.1), "ordinary NPCs stay player-like" and
"vanilla things stay available" bind both heroes, their tokens and their artifacts.

---

## 0. The request and the decisions

The owner's words (2026-11-18, `HANDOFF.md` section 2, request 10): "2 more specific heroes, the wither and the ender
dragon. When the vanilla wither and ender dragon are defeated for the first time a chest will spawn at the ground
respective to their death location, inside the chest will have a wither or ender dragon hero token (based on who was
defeated) and if anyone from the faction brings the token inside a base it will consume it and the hero will join the
faction, this token may be dropped on death so another faction can steal it before they can be used and use it for
themselves. The wither and the ender dragon have female appearances based on the boss design and they come with an
unique artifact that can't be crafted and is best suited for them, but it is also dropped if they die. Make their
kit the same way you've been doing with the other bosses, unique and complex, and base it on the boss itself."

**Where the vanilla facts come from.** Every vanilla number below was read in the 1.21.1 sources of the NeoForge
21.1.209 build (`WitherBoss`, `WitherSkull`, `EnderDragon` and its phase classes, `EndCrystal`, `DragonFireball`,
`EndDragonFight`, `SpikeFeature`, `SoundEvents`), because the wiki was not reachable from this session. Section 1
lists them next to what each becomes.

Then, answering section 11 (request 11): "3 no personal names 4 token 5 its ok for any hero or npc that usrs
explosives to breach walls, theres no need for anyone to be "civil" in battle, so they may use the resources they have
to full advantage"; "Also, its fine for heroes abilities to push someone of a cliff or to lava"; and for artifacts
"Heroes may have more slots as they level up but artifact strenght doesnt change", "heroes decide if they want to use,
replace, or leave for someone else the artifact", tinkerers "may upgrade the artifact", "an artifact may become
stronger of its in the artifact design" and "some artifacts may nees tech level to be built and/or upgraded".

Decisions (PROPOSED unless the owner's words decide them; section 11):

1. **Two legendary heroes outside the roster**: `the_wither` and `the_ender_dragon`, both female, adult, three phases
   and an awakened second ultimate, as complete as a slice hero (sections 4 and 5). They are not among the 200, so
   the roster's 100/100 gender split, its balance counts and `check_roster.py` leave them out; the game then has 102
   female and 100 male heroes. They are never drafted as leaders, never drawn by a random hero token, never wanderers
   (milestone 3), and never lead a faction (`leader.eligible: false`).
2. **Their names say plainly what they are**: "The Wither" and "The Ender Dragon", with the titles "Queen of Three
   Skulls" and "Empress of the End", and no personal names (the owner's answer of 2026-11-18).
3. **One of each per world**, always: as a token, as a living or dead hero, never both.
4. **The first-kill chest** (section 3.1-3.3): the first death of a vanilla Wither (any dimension) or of the Ender
   Dragon, whoever or whatever killed it, places a chest on solid ground below the death point (for the dragon, beside
   the exit portal; never over the void), holding that boss's hero token. Later deaths give no chest.
5. **The token** (3.4-3.5) is a unique item that glows, never despawns and survives fire, lava, explosions and
   cactus. Lost to the void, a command or 30 s in lava, it returns to the chest spot 2 minutes later.
6. **Using it** (3.6): a token is consumed **at once** when a member of a faction (a player who belongs to it, or any
   NPC or hero of it) holds it inside the walls of one of that faction's standing bases, or when it lies inside such
   a base for 5 s. The hero then forms at that base's reporting station in a 10 s "calling" (presentation only: the
   Wither's soul-sand summoning, the dragon's egg hatching) and joins the faction. No ritual can be interrupted: the
   race for the token happens outside the walls.
7. **Dropped at death, always** (3.7): whoever carries a token drops it where they die, even with `keepInventory`.
   Anyone may pick it up: a player, an NPC of any faction, a hero, a zombie.
8. **When her faction falls she returns to her token** (3.8; the owner's answer "token"): the faction that holds a
   boss hero loses her only with its last base; she then unbinds into a new token on the spot (keeping her level), so
   conquering that faction is the way to take her.
9. **Player-like knowledge and an AI that goes after tokens** (3.9-3.10): factions learn of a chest or a carrier only
   through witnesses and what their scouts see; the war council weighs a token as a prize, sends expeditions to
   chests, intercepts known carriers and escorts its own; a player's own faction does the same.
10. **The Wither** (section 4): a hovering ranged queen with two skull familiars as her side heads, the Wither effect,
    the blue skull, a regeneration, wither armour from half health that stops arrows (she comes down to fight close),
    the Bedrock-style dash, her familiars unbound in phase 3, and a **rebirth** at 30% (vanilla's spawn charge starts
    at a third of the Wither's health). Her ultimate, Birth Blast, replays the spawn charge's blast. **Her blue skulls
    and Birth Blast are explosions by nature and break blocks** (`HEROES_V2.md` 6.18: the blue skulls by vanilla's
    blue-skull rule, through anything not wither-immune; Birth Blast at power 4, inside its red disc); her black skulls
    break nothing; walled in, her body withers the blocks around it, as the vanilla Wither's does. She breaches an enemy
    wall on a raid as anyone with explosives does, never near her own side's blocks; her hands mine at a diamond
    pickaxe's speed (4.5).
11. **The Ender Dragon** (section 5): a winged vanguard who circles, spits ender fireballs, breathes lingering dragon's
    breath, buffets with her wings, dives, and **perches** (arrows glance off her while she is perched, as vanilla).
    Her central mechanic is the **end crystals** she raises on obsidian spires: the nearest crystal in line of sight
    heals her, any hit breaks an open crystal, caged crystals stop projectiles and need climbing, and breaking the one
    that is healing her hurts and staggers her. Soldiers and heroes read crystals and break them (equal treatment).
    Her wings, tail and dives push at full strength and she aims them at cliffs, lava and the void past an End
    island's rim (`HEROES_V2.md` 6.17); none of her moves breaks a block, and she does not smash through blocks in
    flight as the vanilla dragon does (5.6).
12. **Both fight to the death** (their traits give it; the files pin it): the vanilla bosses never flee, and a death is
    how their signature artifacts change hands.
13. **The signature artifacts** (4.13, 5.13): **Hollow Star** (the Wither) and **Spirecore** (the Ender Dragon), in
    `ARTIFACTS.md`'s signature format: base lines and a passive for any hero, a bond (more lines, a stronger passive
    and two changes to her own moves) for its own hero; one instance per world; worn in her one slot when she first
    joins at level 1; dropped when she dies. Their numbers are fixed (the same on any hero at any level); Hollow Star
    grows by its own design and is never upgraded, Spirecore is upgraded by a tinkerer with research and never grows;
    each heroine keeps hers by her own choice.
14. **Revival stays the rule**: 10 minutes after a death, at her home base. The dragon egg and the Wither's soul-sand
    summoning are the look of that return, not a death ability (no `revival` block).
15. **New format pieces** are listed precisely in section 8 and `notes/kit-gaps.md` ("Boss heroes"): projectile-immune
    states, the lingering zone, the crystal and familiar adds, focus adds as a weak point, the `smite` and `blast`
    weak points, a rebirth with its own content, perching, circling, a flyer that hovers, and a few smaller ones.

**Improvements added on our own** (the owner welcomes them; each is said here so it can be refused):
- the 10 s "calling" when a token is consumed, and the soul-sand and egg looks of the 10-minute return;
- the Wither's break-out when walled in (vanilla's own rule, kept for the trap it was made for, 4.5). The first
  design's breach (a 2x3 opening broken by hand, the one hero allowed to break a faction's walls) is gone with the
  owner's answer: anyone with explosives breaches now, and she does it with her blue skulls;
- from the revision: Hollow Star grows and Spirecore takes a tinkerer's upgrades (one way each, 4.13 and 5.13); a
  signature held by another hero of her faction counts as lent by her; an enemy hero wearing her signature is her
  first target (4.13);
- the vanilla tie-ins as weak points and fight rules: Smite enchantments hurt the Wither more, explosions (beds, TNT)
  hurt the dragon more, snowballs and eggs break open crystals, a perched dragon shrugs off arrows;
- NPC damage that hurts the vanilla dragon (6.6): vanilla ignores any damage to the Ender Dragon that does not come
  from a player or an explosion, which would make ROADMAP phase 3's End raids impossible for NPCs; the fix is noted
  here because the first-kill chest depends on who can kill the dragon;
- the familiars and crystals as visible adds anyone can kill; the two heroes as rivals (`relationships`);
- from the review (section 12): archers and crossbowmen carry a sword as a sidearm and draw it against a target their
  arrows cannot hurt (6.3), so an archers-only side can still beat the armoured Wither, and so can ROADMAP phase 3's
  raid on the vanilla Wither.

---

## 1. The vanilla bosses, read in the sources, and what each part becomes

### 1.1 The Wither (`WitherBoss`, `WitherSkull`)

| Vanilla 1.21.1 | Value in the sources | In the hero |
|---|---|---|
| Health, armour | 300, armour 4; boss bar purple, darkens the screen | legendary boss health (4.4); her bar asks for purple; the ultimate darkens the screen as every ultimate does |
| Spawn charge | 220 ticks (11 s) invulnerable, health set to a third, +10 health every 10 ticks, then an explosion of power 7 at its eyes (interaction `MOB`) and the global spawn sound | three echoes: the **rebirth** (lethal damage instead re-forms her at 30%, a third), the **Birth Blast** ultimate (a charge you can break, then a radius-7 burst that is also a blast of power 4, `HEROES_V2.md` 6.18's ceiling, 4.5), and the 10 s **calling** when her token is used (no blast). A hero is never invulnerable (`HEROES_V2.md` 6.7) |
| Three heads | the main head fires at its target every 40 ticks (range 20); the two side heads each pick their own random target within 20 blocks and fire every 40-60 ticks at targets within 30 blocks in sight | her own head and two **skull familiars**; the passive **Three Heads, Three Hungers** makes each familiar fire at a different enemy |
| Wither skull | 8 damage; Wither II for 10 s (Normal) or 40 s (Hard), none on Easy; a power-1 explosion (interaction `MOB`) that breaks only soft blocks (dirt, sand, glass, leaves, some wood; never stone); heals the Wither 5 when it kills | black skulls (basic attack, **Skull Volley**, her familiars' shots, **Skullfall**) with Wither I; **Undying Hunger** heals her on a kill. They break no blocks (4.5: the one vanilla blast she does not keep). Hero caps: Wither at most II for 6 s |
| Blue skull | 0.1% of the main head's shots; on Normal and Hard an idle side head fires one at a random point within 10 blocks; slower (inertia 0.73); its power-1 explosion counts every block that is not air or `#wither_immune` as no stronger than 0.8 (`WitherSkull.getBlockExplosionResistance` with NeoForge's `canEntityDestroy`), so it breaks obsidian | **Ruin Skull** (a slow heavy skull with a burst) and, in the rebirth, **Stray Skulls** (blue skulls raining at random around her). Both are `explosive` power 1 with the blue skull's own rule (4.5) |
| Regeneration | +1 health every 20 ticks | **Undying Hunger**: 1% of her maximum every 5 s in a fight (under the passive caps) |
| Wither armour | at half health or less (`isPowered`): arrows (tridents included) and wind charges do nothing; it no longer rises 5 blocks over its target, so it comes down to fight | phase 2 from 50%: the **wither armour** state (arrows, bolts, thrown tridents, wind charges and physical hero projectiles do nothing to her); she leaves the air and fights close |
| Hovering | flying move control; with a target it climbs while lower than the target, or lower than target + 5 while unpowered | flyer III that always floats (a hover secondary), aerial windows in phase 1 only, 6 blocks up at most (the fight's ceiling) |
| Block breaking | when hurt, 1 s later it destroys (with drops) every block from its feet up through its height and one block out on each side, but not `#wither_immune` ones, if mob griefing is on (`destroyBlocksTick`) | kept for the trap it answers: **walled in** in a fight, her body withers the blocks round it at once, by that rule (4.5); not on every hurt. Her **withering hands** mine at a diamond pickaxe's speed at her duty and dig as every hero digs (6.12); never her own side's planned blocks |
| Immunities | no mob effects at all; never hurt by undead (`#wither_friends`) or by Wither damage | immune to the Wither effect only (a hero must stay open to Slowness II and the other counters); blight resistance 60 |
| Drops | a nether star (extended lifetime), 50 XP | her signature artifact, **Hollow Star**, a nether star gone dark |
| Sounds | `entity.wither.spawn` (global), `.shoot`, `.ambient`, `.hurt`, `.death`, `.break_block` | used by id (4.11) |
| Bedrock only | a dash that smashes through blocks, wither skeleton summons and a blast at half health | the dash is **Soul Dash**, which breaks nothing (6.18 never lets a charge blast) and staggers her against a wall instead (her `charge_wall` weak point); wither skeleton summons are left out (they would drop wither skeleton skulls, a free supply for summoning more Withers), her unbound familiars are her adds instead |

### 1.2 The Ender Dragon (`EnderDragon`, its phases, `EndCrystal`, `DragonFireball`, `EndDragonFight`, `SpikeFeature`)

| Vanilla 1.21.1 | Value in the sources | In the hero |
|---|---|---|
| Health, bar | 200; boss bar pink | legendary boss health (5.4); her bar asks for pink |
| Holding pattern | circles the pillars; the fewer crystals stand, the more often it lands (1 in crystals + 3 each pass) | **circling** in her aerial windows; **Perch** is picked more often when no crystal of hers stands |
| Strafe | fires a dragon fireball when facing its target within 10 degrees | **Ender Fireball** (light, usable in the air) |
| Dragon fireball | on impact a cloud of radius 3 growing to 7 over 30 s, Instant Damage II, `entity.dragon_fireball.explode` | the fireball leaves a **lingering cloud** (the new `linger` effect) |
| Perching | lands on the fountain; sitting phases: flaming, scanning, roar (40 ticks, growl); **arrows and wind charges do nothing while it sits** (they are set alight); takes off after 25% of its health in damage while sitting | **Perch**: she lands on high ground her enemies can reach; while perched arrows glance off her and melee hurts more; she takes off after 8 s or 15% of her health |
| Dragon's breath | a cloud of radius 5 for 10 s with Instant Damage, from the perch | **Dragon Breath** (a cone that leaves a cloud), **Breath of the End** (the ultimate sweep) |
| Wing knockback | wings push everything near by 4 and hurt 5 (not while sitting); players are thrown off the island into the void | **Wing Gust** (knockback 3, full strength, aimed at edges: off a cliff, into lava, off an End island into the void; `HEROES_V2.md` 6.17) |
| Block smashing | its head, neck and body remove every block they pass through that is not air, fire or `#dragon_immune` (obsidian, crying obsidian, end stone, iron bars, bedrock...), with no drops, if mob griefing is on (`checkWalls`) | none: she is a two-block flyer with a flight planner and flies round what is in her way; she breaks blocks only by every hero's dig (5.6) |
| Head and body | the head and neck hurt 10 on contact; damage to body parts is cut to a quarter + 1 | **Void Dive**; a single hero hitbox, no parts (the per-hit cap already plays that role) |
| Charge | charges a player | **Void Dive** (a `dive`, with its landing mark) |
| End crystals | 10 on obsidian pillars 76-103 blocks high, 42 blocks out; the 2nd and 3rd shortest pillars are caged in iron bars; the nearest crystal within 32 blocks heals the dragon 1 health every 10 ticks; any damage breaks a crystal (snowballs too) with a power-6 explosion; breaking the crystal that is healing it hurts the dragon 10 | **Raise Crystal**: crystals on spires; only the nearest in line of sight heals her (0.75% of her maximum per second, at most 35% in a fight); any hit breaks an open one; caged ones stop projectiles and need climbing; breaking the one healing her hurts her 6% and adds 40 poise; a small shatter burst instead of the explosion: it pushes at full strength and breaks no block (5.5) |
| Who can hurt it | only damage from a player, or a damage type in `#always_hurts_ender_dragons` (explosions) | the hero takes damage from anyone (one mode); the vanilla dragon gets a fix for NPC raids (6.6) |
| Death | 200 ticks rising with light beams, 12,000 XP the first time (500 later), the exit portal, the dragon egg on the first kill, the global death sound | her **death** plays a short beam burst (presentation); a **dragon egg** marks her 10-minute return at her base (flavour, not a death ability) |
| Sounds | `entity.ender_dragon.growl`, `.flap`, `.shoot`, `.ambient`, `.hurt`, `.death`, `entity.dragon_fireball.explode`; **no end crystal sound exists** (crystals use the generic explosion) | used by id; the crystal's hum, beam and shatter are generated (5.11) |

---

## 2. The boss heroes in the mod

### 2.1 Outside the roster

- **Ids and files**: `herosrc/heroes/the_wither.hero.json` and `the_ender_dragon.hero.json` (format 2), built like any
  hero into `data/factionclash/heroes/` and the client assets. Rarity `legendary`, `gender: female`.
- **Archetypes**: two new archetype ids in a new group "E Boss heroes", outside the 54 the roster balances:
  `wither` (defaults: the skull familiars, bone crown, smoke gown; banner emblem `skull` + `curly_border`) and
  `ender_dragon` (defaults: dragon wings, swept horns, scaled tail; `hybrid` reads `west_dragon`'s look defaults).
  `check_roster.py` skips the group; the format reader accepts it (kit-gaps).
- **Never** in the leader draft, in a random token draw (v1 25.3, milestone 3), among wanderers or recruits (their
  `recruit.condition` is the new `token`: only their token brings them), or in a defection (milestone 3: a bound
  hero cannot defect; her loyalty still drives her lines and fight AI).
- **Batches**: they are their own content task (section 10, B7), not part of a batch of 24.

### 2.2 One of each per world

`CampaignData.bossHeroes[hero]` holds one state per boss hero:

| State | Meaning | How it ends |
|---|---|---|
| `dormant` | the vanilla boss has not died yet (since the mod was added) | the first defeat: `chest` |
| `chest` | the token is in the first-kill chest | someone takes it: `loose` |
| `loose` | the token is somewhere in the world (an inventory, the ground, a container) | consumed: `bound`; lost: `returning` |
| `returning` | the token was destroyed; a new one forms at the chest spot | after 120 s: `chest` |
| `bound` | she belongs to a faction (alive, or dead and waiting for her revival) | her faction falls: `loose` (3.8) |

The hero entity follows v1's uniqueness rule (one living entity per hero id; a stale copy is discarded). The token's
instance id (3.4) is the world's single real token; any other copy is hollow.

### 2.3 Names and titles

- `name`: "The Wither" and "The Ender Dragon"; `title`: "Queen of Three Skulls" and "Empress of the End". Name tag:
  `★ The Wither · Queen of Three Skulls [Fighting 80%]`; boss bar: `★ The Wither · Queen of Three Skulls  ◆◇◇◆`.
- Death messages and the kill feed use the name with the faction: "Steve was withered by The Wither (Ember Legion)".
- **No personal names** (the owner's answer of 2026-11-18: "no personal names"): the name is the boss's own and the
  title says the rest, everywhere (name tag, bar, chat, the kill feed, the hero sheet, the token's tooltip). The
  first design's working proposals are withdrawn, and no name search is needed for them.

### 2.4 What they share with every hero

Everything in `HEROES_V2.md` sections 3-8 that is not changed below: one combat mode (the same health, caps,
reactions and weak points against a player, NPC soldiers, a hero or monsters), no attack tokens, no yield, boss
health (its 6.2), the per-hit cap of 12%, telegraph minimums, the hurt cooldown per attacker, poise and stagger,
enrage (180 s; they never lead, so never 240), the 10-minute revival at their home base (never under 30 s), artifact
slots that open with the level (one at level 1, one more every five levels, five from level 20: `ARTIFACTS.md` 3),
combat XP and levels 1-30, talents and a duty, work mastery, mobility with its fairness rules, terrain as a weapon and
explosive moves (its 6.17 and 6.18), the war council's war power (its 8.1) and its limits, the kill feed. They join
at **level 1** (all abilities from level 1, power growing to full at level 20; one artifact slot, which her signature
fills until level 5); a boss hero who returns to her token keeps her level, and so her slots, for the next faction.

### 2.5 Not leaders

`leader: { "eligible": false }` and nothing else (kit-gaps: the reader must accept an ineligible leader block with no
faction names). Reason: the owner's "the hero will join the faction" makes them prizes that serve the faction that
called them; a faction's identity, name and base style stay its leader's. They have no `court` duty and no realm
talent.

---

## 3. The first-kill chest and the tokens

### 3.1 The first defeat

- **The Wither**: the death (`LivingDeathEvent`) of any vanilla `minecraft:wither`, in any dimension.
- **The Ender Dragon**: the end of the dragon's 10 s death (`EndDragonFight.setDragonKilled`; a dragon outside the
  fight: its removal with reason `KILLED`). The chest appears with the exit portal.
- **Any killer**: a player of any faction or none, an NPC faction's party (ROADMAP phase 3's boss raids), monsters, a
  fall, lava. Only `/kill` (damage type `generic_kill`) does not count, so an operator test does not use up the chest
  (`/fc admin bosshero chest` places one on purpose).
- **First since the mod was added**: the mod's own flag counts, not vanilla's `previouslyKilled`, so an old world whose
  dragon died before the mod gets its chest at the next dragon's death (a respawned one).
- **Only once**: later deaths of a Wither or a dragon give no chest. (Milestone 3's random hero tokens of v1 25.3 may
  still drop from them; on the first defeat the chest replaces that drop.)
- With `bossHeroes.enabled = false` nothing happens and the flag stays unset, so turning it on later gives a chest at
  the next defeat.

### 3.2 Where the chest appears

`logic/boss/ChestSpot` (pure, over a column reader so it is unit-tested; PROPOSED):

**The Wither** (and the fallback for the dragon):

1. From the block where it died, go down the column to the first cell that is air or a replaceable plant (grass, snow
   layer, flowers) with a sturdy top face under it.
2. **The Nether roof**: a death above the bedrock roof searches under the roof, from 6 blocks below its underside
   down, so the chest is never on the roof.
3. A cell fails when it is lava, fire, powder snow, water deeper than 2 blocks, next to a cactus, inside a
   `#wither_immune` block or a portal, or when the column under it opens to the void (no block within 3 below and no
   ground at all down to the minimum height).
4. When the column fails, search square rings outward to 16 blocks, the nearest cell first, the same height or lower
   before higher.
5. Still nothing: the nearest passing cell within 64 blocks; then the dimension's spawn area (the Overworld's world
   spawn, the End's obsidian platform).
6. The chest replaces only air or a replaceable plant, faces the death point, and is never placed in a base's heart,
   station or storehouse block. Inside a faction's base is allowed (3.12).

**The Ender Dragon**:

1. The podium centre (`EndPodiumFeature.getLocation(fight origin)`, normally 0, 0): the first standable cell on rings
   of radius 6 to 10 around the exit portal, outside its bedrock rim, starting toward the obsidian spawn platform
   (east) and going round. Standable: the block under it is end stone (or another sturdy block) with at least 3 solid
   blocks below that, so the chest is never on a ledge over the void.
2. Never on the portal, the egg, the gateway, a pillar or its crystal.
3. A custom End without the island: the Wither's rule from the death point, which never accepts a column over the
   void.

### 3.3 The chest

- A vanilla chest named "The Wither's Remains" or "The Dragon's Hoard" (gold), with the token alone in its middle
  slot. No lock: anyone may open it, take the token, break it (the token drops) or pull the token out with a hopper.
- **Presentation**: while the token is inside, a column of soul-fire particles (the Wither) or a thin pale beam of end
  rod sparks (the dragon) rises from the chest every second, drawn from 64 blocks away (long-distance particles), with
  a low hum within 16 blocks. Witnesses (players within 64 blocks of the death) get the title "The Wither has fallen"
  with the subtitle "Something remains where she fell" ("The Ender Dragon has fallen" / "A hoard waits by the
  fountain"). There is no chat message to other players: knowledge stays where people saw it (3.9).
- **NPCs open it**: the rule that a faction's AI takes things only from its own storehouse (`ARTIFACTS.md` 15.2) has
  this one exception, while the chest holds a token. Opening it is a player's action (the NPC stands at it, opens it,
  takes the token).
- Once empty it is an ordinary chest; the particles stop.

### 3.4 The token item

- **Two items**: `factionclash:wither_token` ("Wither Hero Token") and `factionclash:ender_dragon_token` ("Ender Dragon
  Hero Token"). Stack size 1, epic rarity, the enchantment glint. Tooltip: "Bring it inside a base of your faction:
  the Wither joins it." / "There is one in the world. It drops when its carrier dies." They reuse v1's token ideas
  (25.3: a named token, the `summoned` line, the hero walking out of the station) with this file's rules of use.
- **Component** `factionclash:boss_token`: `{ hero, instance }` (a UUID). `CampaignData.bossHeroes[hero].tokenInstance`
  names the one real token; a copy with another instance (a duplicated world, `/give`, an old copy after a return) is
  a **hollow token**: grey name "Hollow Wither Token", no effect, never deleted. A token is real only while its
  instance matches **and** its hero's state is `chest` or `loose`, so a copy with the same instance (a creative clone,
  an item duplication) is real only until the first use: that use binds her, and every other copy is hollow from then
  on. A token is never used twice.
- **On the ground**: never despawns (`setUnlimitedLifetime`); fire-proof (it floats up out of lava as netherite does);
  refuses damage from explosions, fire, lava, cactus and lightning; drawn with the glowing outline so players can find
  it; a small particle puff every second (soul for the Wither, portal for the dragon).
- **Never hidden from the world**: it refuses ender chests, shulker boxes and bundles, so a carrier can never put it
  where no one could take it. `canFitInsideContainerItems` false keeps it out of shulker boxes and bundles (vanilla
  checks it in both). Vanilla's ender chest has no slot hook, so when a player closes an ender chest the mod moves a
  token found in it back to the player's inventory, or to their feet when it is full, with the line "The token will
  not stay hidden." Every other container is allowed, and can be raided.
- **Never held in creative or spectator mode**: it drops at once at such a player's feet (that player is never an
  enemy, so no one could ever take it back).
- **Icons** (16x16, vanilla style, by `herogen icons`): a charcoal disc with three small skulls and soul-blue eye
  sockets; a black egg-shaped crystal flecked with purple. A slow 4-frame shimmer, as signature artifacts have.

### 3.5 A lost token comes back

- **Lost**: fallen into the void, destroyed by a command (`/kill`, `/clear`), removed by another mod, or left in lava
  for 30 s (it floats, but a lava lake can hold it out of reach). The world notes it lost at once.
- **Return**: after `bossHeroes.tokenReturnSeconds` (120, PROPOSED) a new instance lies in a chest at the chest spot
  (a new chest when the old one is gone; the spot is searched again by 3.2 if something now fills it). An old copy
  found later is hollow. The state is `returning` meanwhile (2.2).
- **How the world notices a loss** (3.14): an item entity holding the real token leaves the world with its stack
  still in it, for any removal reason but `UNLOADED_TO_CHUNK` or `CHANGED_DIMENSION` (the void, `/kill`, a despawn by
  another mod; a pickup empties the stack first, so it is not a loss); it has lain 30 s in lava; or a command that
  names its last known holder (`/clear`, `/kill`, `/item`) left that holder without it at the end of the tick. Anything
  else an operator settles with `/fc admin bosshero restore <hero>`.
- **Not lost**: a token in an unloaded chunk, in a chest, in a player's inventory while the world is closed. (In a
  multiplayer world a carrier who leaves drops it at their feet; single player is the goal, but this keeps the token
  in the world.)

### 3.6 Using a token: inside a base, at once

**The rule.** A token is consumed the moment either holds:

- it is held (in an inventory, a hand, or the backpack of an NPC or a hero) by a **member** of a faction while the
  holder is **inside** a **standing base** of that faction; or
- it **lies inside** a standing base (on the ground, or in any container there, the storehouse included) for 5 s; it
  then joins that base's faction.

A **member** is a player who belongs to the faction, or any NPC or hero of it. **Inside** is the base's walls box (its
ring's footprint, from 8 blocks under its lowest floor to 24 above, the box the defence alarm watches without its
4-block margin); a base whose walls are not built yet: within 24 blocks of its heart. **Standing**: its heart stands.
The Hollow's lairs are not bases of a faction that can hold heroes. Whose base it is decides, never who brought it
there: a token dropped by an enemy raider who dies inside a base joins that base's faction, and a factionless
player's gift thrown over the wall works too.

**At once, then the calling** (the owner's "it will consume it and the hero will join"). Nothing can interrupt the
use: the base is the safe place, and the contest happens outside the walls. On consumption:

1. The token vanishes in a burst of soul fire (the Wither) or ender particles (the dragon); the hero's state is
   `bound`, her faction and home base set to that base's.
2. **The calling** (10 s, `bossHeroes.callingSeconds`; presentation only) at that base's reporting station:
   - the Wither: four soul sand blocks in a T and three wither skeleton skulls appear as block displays (not blocks),
     glow blue-white for 10 s (vanilla's charge colour) with a rising heartbeat, then burst with the vanilla spawn
     sound (heard within 64 blocks), no damage and no block changes; she stands up out of the burst;
   - the Ender Dragon: a dragon egg (a block display) settles on the plaza, thin purple beams converge into it from
     above as crystals beam the vanilla dragon, it cracks at 10 s, and she unfolds her wings with a growl.
3. She says her `summoned` line to the faction. Full health, energy 0, level 1 on her first joining (or the level she
   kept, 3.8, with the slots that level opens); on her **first** joining her signature artifact is in her first
   artifact slot, the only one she has until level 5 (`ARTIFACTS.md` 3 and 15.1). On a later joining she comes with
   nothing worn and chooses from what the faction holds (`ARTIFACTS.md` 10.4), her own signature first if it is there.
4. Chat: her faction's players, and every player within 64 blocks of the base, read "The Wither joins the Ember
   Legion."

She is a non-leader hero of the faction: home base the base of the calling, her duty and talents from her file. She
does not count against `heroes.maxPerFaction` (milestone 3: she is outside the roster), and one faction may hold both
boss heroes.

### 3.7 Carrying, dropping and stealing

- **Anyone may carry it**: a player of any faction or none, a soldier, worker, hauler or scout, a hero, a vanilla mob
  that picks up items (a zombie keeps it and drops it when it dies).
- **Dropped at every carrier's death**, where it died, whoever killed it, even with `keepInventory` (the token is moved
  out before the death's inventory handling) and for a hero carrier (a hero's death drops what it carries; an escape
  keeps everything, as for artifacts).
- **A carrier shows it**: a trail of soul particles (the Wither's token) or portal particles (the dragon's) around a
  carrier every half second, whatever slot holds the token. Players see the trail; NPCs recognise a carrier by line of
  sight within 24 blocks (3.9). The same rule for a player carrier and an NPC carrier.
- **Carriers are first targets**: a soldier or a hero within 24 blocks of an enemy carrier it can see takes the
  carrier before other targets (an enemy hero diving at it comes first), as a player would go for the thief.
- **Raid loot**: raiders take a token before anything else, as they do artifacts (`ARTIFACTS.md` 10.8).

### 3.8 When her faction falls: back to her token

The owner's answer of 2026-11-18 ("4 token"), settled. When the faction that holds a boss hero loses its last base
(the elimination rule of `HEROES_V2.md` 5.6), she does not become an exiled free hero:

- **Alive**: when the fight she is in ends (at once if she is not fighting) she **unbinds** (3 s, presentation: the
  Wither sinks into soul sand and skulls, the dragon folds into an egg) and a new token (a new instance) lies where she
  stood. Her artifacts drop there, as at a death.
- **Dead** (waiting for her revival): the token appears where her faction's last heart fell; her revival is
  cancelled.
- Her saved state (level, XP, affixes, kills, deaths) is kept with the token, so the next faction gets her as she was,
  with the artifact slots her level opens (empty); her signature artifact is wherever it now is, with its growth or
  upgrades on it (it re-forms only if destroyed, and then at her next joining, as new: `ARTIFACTS.md` 15.1).
- The conquerors usually hold that ground: taking a boss hero's faction's last base is how a rival takes her.
- Her `unbound` line; the state is `loose` (2.2). An operator can force it: `/fc admin bosshero release <hero>`.

### 3.9 Knowledge (player-like)

A faction knows about a token only through its own people (the ROADMAP phase 1 knowledge rule):

- **Witnesses**: every NPC or hero within 48 blocks with a line of sight to the boss when it dies, and within 32
  blocks of the chest when it appears, knows the chest's position. A witness away from home brings the news home.
- **Scouts and travellers**: the chest's particle column is a landmark; a scout (or any NPC on a trip) with a clear
  line to it within 64 blocks reports `BOSS_TOKEN_CHEST` (new report kind: position, which boss, when). A later
  sighting of the chest without its column reports it empty.
- **Carriers**: an NPC that sees a carrier within 24 blocks reports `TOKEN_CARRIER` (who, which faction, position,
  heading, when); such a report is stale after 2 in-game hours.
- **Its own carriers**: a member carrying a token, an NPC or a player alike, is known to the faction while it is
  within 64 blocks of one of the faction's bases or of another of its NPCs (an expedition's escort knows its own
  carrier); farther off, the faction learns of it as of anything else, from those who come home.
- No global message reaches NPC factions, and players get only what they witness (3.3, 3.11).

### 3.10 The AI: going after a token

- **Weighing** (the war council, `FactionDirector`, with the report): a known token's value is the boss hero's war power
  at her kept level (`HEROES_V2.md` 8.1; at level 1 about 10.4 for the Wither and 10.1 for the dragon on a first
  joining, with her signature's flat 0.12; 9.3 and 9.0 for a token she returned to, which brings no artifact) times
  `bossHeroes.prizeWeight` (2.0: a permanent unique hero is worth more than a raid's loot); the risk is the distance
  (per 100 blocks), the known enemy power near the spot or around the carrier, and what the faction can spare without
  breaking its garrison's `keep`. Personality: ambition and greed raise the value (up to +30% each), caution raises the
  risk (up to +30%), aggression lowers it. A sweet-natured or shy leader still sends for an unclaimed chest (it is not
  a war), never into an enemy base.
- **Expedition** to a known chest: a party like a raid party, smaller: 3-5 soldiers and, when one is free, a hero
  (flyers first: they are fastest). It musters, travels, opens the chest, and the first member to take the token heads
  home by the shortest safe route with the party as its escort. It fights whoever contests it, falls back at the raid
  fall-back share, and ends at a time limit (36,000 ticks, PROPOSED).
- **Interception** of a known enemy carrier: a hunting party (fast heroes first, then 2-4 soldiers) to the carrier's
  last known position, then along the line to the carrier's faction's nearest known base. It gives up when the
  carrier is inside a base (the token is used) or after 10 minutes without a sighting.
- **Escort**: while a member carries a token outside its bases, its faction's soldiers within 48 blocks follow and
  guard the carrier (an NPC carrier drops its job and goes straight home), and the director sends up to 3 soldiers to
  meet a carrier it knows of, a member player included. An enemy within 32 blocks of a carrier near its walls raises
  the defence alarm.
- **The player's own faction** follows the same rules: its NPCs fetch chests its scouts find, escort a member player
  who carries a token, and take tokens from enemies. The player can also just walk the token in.
- **A rival who holds a boss hero** is 25% more attractive as a raid target (PROPOSED) when the raid could take its last
  base (the token comes back there, 3.8); her war power still counts in its defence (8.1).
- **Never**: the AI never destroys a token, never drops it in the void or lava, never stores it outside its bases.
- `sim.py` prints each token's state per day, expeditions, interceptions, carriers and who holds which hero.

Until ROADMAP phase 3's boss raids exist, NPC factions do not kill the bosses themselves; the chests then come from the
player's kills, and the factions still race for them. Until phase 3 also gives NPC parties Nether and End travel, an
expedition goes only to an Overworld chest: a chest in the Nether or the End (the dragon's always is) is for whoever
can get there, the player first, and the factions contest that token once it is carried into the Overworld, where
their people can see its trail.

### 3.11 Chat, the kill feed and presentation

- The first defeat: the witnesses' titles (3.3).
- Picked up: the carrier's faction's players read "Steve carries the Wither Hero Token" (a member) or, when the faction
  knows of it, "The Wither Hero Token was taken by the Grove Covenant".
- Dropped: the faction of a dead carrier is told where it lies only when it knows of the death (the knowledge rule).
- Used: 3.6. Unbound: "The Wither returns to her token" to the faction's players and players within 64 blocks.
- **The kill feed** (`HEROES_V2.md`'s planned hero kill feed): the boss heroes' deaths and kills are announced like
  every hero's, worded like a vanilla player's death message, to all players, following `showDeathMessages`.

### 3.12 Edge cases

- **Both bosses die at once**: two independent states; two chests.
- **The chest spot inside a faction's base**: the token lies inside that base, so it joins that faction 5 s later; a
  Wither killed in a faction's yard is that faction's prize.
- **The Wither dies in the End over the void**: the column fails; the ring search, then the End's obsidian platform.
- **A token carried into another dimension** stays a token; a base exists only in the Overworld today, so it must come
  back to be used.
- **A token in a minecart chest, an item frame, an armour stand or a hopper chain**: in the world, takeable; inside a
  base it counts as lying there.
- **The carrier's faction is eliminated while it carries the token**: the carrier dies with its faction (every member
  does, the owner's rule of 2026-11-25) and the token drops where it fell, as at any death; a carrier away in an
  unloaded place drops it where it stands when its chunk loads (an NPC carrier dies there then; a hero carrier was told
  dead at the fall, and what it carried, the token among it, drops where its copy stood as the copy is discarded,
  `HeroManager.mayJoin`). The token stays in the world, takeable.
- **The hero's file is removed** (an operator): a bound hero goes dormant as any hero (v1 25.5); a token whose hero has
  no definition says so in its tooltip and cannot be used until the file is back.
- **`heroes.enabled = false` with a bound boss hero**: as any hero; the token rules pause.
- **A save and reload during the calling**: she is bound from the moment the token was used (3.6), so after the
  reload the calling is skipped and she stands at the station.
- **A pact**: a member of one faction holding a token inside a pact ally's base does not use it (it is not its own
  base); a token put down there lies inside the ally's base and joins the ally after 5 s. (Allies may bargain over a
  token in milestone 2's diplomacy.)

### 3.13 Config, commands, saving

`[heroes.bossHeroes]` (PROPOSED):

| Key | Default | Range | What |
|---|---|---|---|
| `enabled` | true | | false: no chest, no token rules; bound boss heroes stay |
| `firstKillChest` | true | | false: the first defeat gives nothing (operators place tokens) |
| `tokenReturnSeconds` | 120 | 30-1200 | a lost token's return to the chest spot |
| `lavaReturnSeconds` | 30 | 10-300 | a token in lava this long counts as lost |
| `lyingConsumeSeconds` | 5 | 1-60 | a token lying inside a base joins that base's faction |
| `callingSeconds` | 10 | 2-20 | the calling's length (presentation) |
| `prizeWeight` | 2.0 | 0-5 | the council's value of a token |

The first design's `unbindOnFall` (false: an exiled free hero instead of the token) is gone: the owner chose the token,
and no other path is designed or tested.

Commands (operator): `/fc admin bosshero status` (each state, chest spot, holder, owner faction), `/fc admin bosshero
chest <wither|ender_dragon> [pos]` (places the chest now, without a kill), `/fc admin bosshero token <...> [player]`
(moves the real token into a player's inventory), `/fc admin bosshero release <...>` (unbinds), `/fc admin bosshero
reset <...>` (back to `dormant`).

Saving: `CampaignData.bossHeroes[hero]`: `state`, `chestPos` (dimension and position), `tokenInstance`, `returnAt`,
`ownerFaction`, the kept hero state while it is a token. The token's component carries its instance.

### 3.14 Hooks (vanilla and NeoForge events; no mixin)

| Rule | Hook |
|---|---|
| The Wither's first defeat | `LivingDeathEvent` on a `WitherBoss`; its damage source tells `/kill` (`generic_kill`) apart |
| The dragon's first defeat | `LivingDeathEvent` on the `EnderDragon` notes the killer and the damage source; the chest is placed when that entity leaves the level with the removal reason `KILLED` at the end of its 200-tick death (`EntityLeaveLevelEvent`), the tick vanilla forms the exit portal |
| The chest | `ChestSpot` (pure) picks the cell; `level.setBlock` places a chest facing the death point, its `ChestBlockEntity` named and holding the token in its middle slot |
| Never despawning, glowing | `EntityJoinLevelEvent` on any `ItemEntity` holding a token (or an artifact, `ARTIFACTS.md` 11): `setUnlimitedLifetime`, `setGlowingTag(true)` |
| Fire, lava, blasts, cactus, lightning | the item's fire-resistant component and its `canBeHurtBy` refusing every other damage type; the 30 s lava count in its `onEntityItemUpdate` |
| Shulker boxes and bundles; the ender chest | `canFitInsideContainerItems` false; `PlayerContainerEvent.Close` on a menu over the player's ender chest |
| A creative or spectator holder; the carrier's trail | the item's `inventoryTick` for players, the backpack tick for NPCs and heroes, every 10 ticks |
| Dropped at death, `keepInventory` too | `LivingDeathEvent`, before the inventory is handled: the token leaves it as an item entity at the death spot |
| A carrier who logs out (multiplayer only) | `PlayerLoggedOutEvent`: dropped at their feet |
| Consumed inside a base | the holder's tick and the item entity's `onEntityItemUpdate` test the walls box; a token in a container is found by the base's once-a-second scan of the containers inside its walls box |
| A loss by command | `CommandEvent` for `clear`, `kill` or `item` naming the last known holder, checked at the end of the tick |

---

## 4. The Wither (F, wither, marksman, blight, tyrant, legendary)

| Field | Value |
|---|---|
| Id, name, title | `the_wither`, "The Wither", "Queen of Three Skulls" |
| Gender, archetype, rarity | female, `wither` (group E, outside the roster), legendary |
| Role, duel style | `marksman` (phase 1: a turret in the air), `zoner`; from phase 2 she comes down and fights close |
| Style, prop | `focus`: a wither-rose sceptre (thrown basic attack); her skulls are the projectiles |
| Element, resistances | blight; blight +60, fire +30, radiant -20 (her elemental weakness) |
| Temperament | `tyrant`; when losing: last stand |
| Mobility | flyer III that always floats (secondary hover II, a format gap), carrier: her smoke gown |
| Duty, talents | miner; `surveyor`, `purifier`, `intimidation` |
| Leader | not eligible |

### 4.1 Concept

The Wither is a weapon that learned to want things. She was built, not born: soul sand, three skulls and a will
forced into one body, and she woke into the world in an explosion. Now she is a cold, imperious queen who talks about
"withering" the way others talk about weather, flanked by two floating skulls that are her side heads: one sneers,
one hungers, and both obey her. She fights the way the boss does: she hangs in the air raining black skulls on
everything at once, grows an armour that turns arrows when she is half spent and comes down to tear at you, sets
her heads loose when she is nearly beaten, and when you finally kill her she summons herself again, at a third of
her strength, as the vanilla Wither rises from its spawn charge. Bound to the faction that carried her token, she
keeps her bargain with a tyrant's pride: she mines for them with hands that wither stone, draws the withering out
of her own people, and makes every rival think twice about their walls.

### 4.2 Look

- **Body**: base `female`, form `adult` (her one body for good: an adult woman, `HEROES_V2.md` 3.6), build `slim`
  (gaunt, the boss's spine-and-ribs silhouette), scale 104: 1.89 blocks. Modest throughout: a high-necked bodice, long
  sleeves, a floor-length gown.
- **Floating**: her feet never touch the ground; the gown's hem trails into wisps of black smoke and soul-blue sparks
  (`vfx_feet` particles), and every idle and move is animated a hand's breadth above the ground (her hover, 4.6).
- **Parts** (library names where the part exists or is planned; NEW parts are new work for the art pipeline):

| Slot | Part | Status | What it is |
|---|---|---|---|
| hair | `hair/hime_long` | built | long straight charcoal hair at its longest, the lowest strands ash grey (`hair_accent`), tips cut `tip_jagged` so they read as fraying into smoke |
| headwear | `headwear/bone_crown` | NEW | a low crown of three short charcoal-bone prongs, the middle one set with a soul-blue gem (glow) |
| accessories | `accessories/skull_familiars` | NEW | **her two side heads**: wither skulls of 6x6x6 px, charcoal with bone-white teeth and glowing soul-blue eye sockets, on their own bones `familiar_l` and `familiar_r` (parent `chest`), floating 12 px out from her head's centre at eye height, each with a small smoke trail; a `sec.familiars` bob on the secondary controller keys only their position offsets; every move clip keys their rotation so they aim with her; hidden while they are unbound (phase 3) |
| outfit_top | `outfit_top/ribbed_corset` | NEW | a high-collared charcoal bodice whose bone-white stays read as a ribcage; long fitted sleeves |
| robes | `robes/smoke_gown` | NEW | a floor-length gown built with the `strands` generator, charcoal fading to ash at a jagged hem that lifts and flutters with her hover; it is her flight carrier (4.6) |
| back | `back/cape_torn` | planned (full library), NEW work | a tattered charcoal cape with a soul-blue lining, split down the middle to show her spine |
| markings | `markings/spine_ridge` | NEW | seven small bone vertebrae down her back between the cape's halves: the boss's spine |
| armour | `armour/bone_bracers` | NEW | bone-white forearm guards, each with three short spines |
| legwear | `legwear/tights_boots` | built | charcoal, mostly hidden by the gown |
| weapon | `weapons/wither_rose_sceptre` | NEW | a slender black sceptre topped by a wither rose (black petals, a soul-blue glow at its heart), right hand, carried at her hip when sheathed |
| (signature) | `accessories/hollow_star_brooch` | NEW | Hollow Star's `worn_part`: a dark star brooch at her collar, a faint blue light inside; drawn only while she wears it (4.13) |

- **Palette**: skin `#E3DFDA` (bone pale), skin_shade `#ABA59F`, hair `#2A282D`, hair_accent `#8E8A93`, eyes `#9FE8FF`,
  primary `#232127` (wither charcoal), secondary `#E6E0D2` (bone), accent `#3CC6E8` (soul fire), trim `#5E5A63`
  (ash), metal `#34313A`, leather `#29262B`, glow `#7FE2FF`.
- **Face**: `sharp` eyes (tsurime), glowing soul-blue irises with **no pupils** (`pupil: none`, `iris_style: glossy`,
  eyes in the glow mask), lashes 2, `thin_arched` brows, a small `soft` mouth, no blush; a charcoal shadow under each
  eye (face paint) that echoes the skull's sockets. Blink `calm` (a slow, bored blink). Mood overrides: `happy` is a
  thin half-lidded smile (she never grins); `angry` brightens the eye glow.
- **Idle quirks**: the familiars bicker (they turn to each other and snap their jaws), she turns a wither rose in her
  fingers, she drifts in a slow half-turn with her arms folded.
- **Phase looks**: phase 2 (the wither armour): her rib stays, crown gem and gown hem glow soul blue (`_p2.png` and its
  glow mask) and a faint blue shimmer of `soul` particles hangs on her outline. Phase 3: the familiars are gone from
  her side (they are adds, 4.3), her hair's ash tips burn with soul fire (glow), her eyes blaze. The rebirth: the gown
  and the cape's lining blaze blue-white, vanilla's charge colour.
- **Budget**: about 240 cubes (hair 80, gown strands 50, cape 20, familiars 12, crown 7), about 115 outside `fine_`
  bones, 256x256 texture (the gown, cape and familiars need the room), within 4.3's limits.

### 4.3 Kit

**Basic attack**: a black wither skull thrown from her open hand (projectile look `wither_skull`, a new look that
reuses vanilla's skull renderer), 4 blight every 1.5 s, range 16, flying straight; `on_hit`: 25% chance of Wither I
for 3 s. It also fires from the air in her aerial windows (a thrown basic attack is her light ranged attack; kit-gaps).

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `skull_volley` | volley 3, range 18 (10 degrees apart), projectile `wither_skull` | light | mid, far | 0.5 (she raises the sceptre, the crown gem flares) / 0.3 / 0.4 | 4 blight + Wither I 3 s each; cooldown 6 s, weight 3, doubled in phase 1 (`prefer_when` `phase` 1); usable in the air (at most 3 light ranged per take-off) | 1-3, R |
| `ruin_skull` | shot, range 20, slow (60% of a shot's speed), projectile `wither_skull_blue`, burst radius 3 on impact | heavy | mid, far | 1.0 (a blue skull gathers between her palms, a rising glassy hum; a strip shows its line) / 0.2 / 0.8 | 10 blight + Wither II 4 s + knockback 1 to each enemy in the burst; `explosive` power 1 with the blue skull's rule (`resistance_cap` 0.8: it bites one to three blocks out of whatever it hits, obsidian included, never a `#wither_immune` block; 4.5); the strip ends in a disc of the blast's reach; cooldown 14 s, weight 4 | 1-3, R |
| `withering_pulse` | nova radius 4 | area | close | 1.0 (she draws a breath; a disc grows under her) / 0.1 / 0.8 | 4 blight + Wither I 3 s + knockback 2; cooldown 10 s, weight 3, prefers a close target | 1-3, R |
| `rose_bloom` | zone at the target, radius 3, 4 s, a pulse every 1 s (the zone's delay is inside its wind-up) | area | mid | 1.0 (she points the sceptre; a disc of black petals grows) / 4.0 / 0.8 | each pulse 2 blight + Wither I 2 s; cooldown 16 s, weight 4; ignores shields (zones do) | 1-3, R |
| `soul_dash` | charge 12, speed 180%, `wall_stun` | heavy | mid, far | 0.9 (she leans back, the familiars scream, a strip grows) / 0.6 / 1.0 | 9 blight + knockback 2 along the path; a wall at the end staggers her (weak point); cooldown 12 s, weight 5 | 2-3, R |
| `grave_grasp` | combo 3 (her withering hands), `breaks_guard` | light | close | 0.45 / 0.6 / 0.4 | strikes of 3, 3 and 5 blight, the third with Wither I 3 s; a raised shield is disabled 5 s; cooldown 8 s, weight 6 | 2-3, R |
| `unbound_heads` | self, `spawn_add` `skull_familiar` x2 (30 s) | heavy | any | 0.8 (she tears the familiars from her side) / 0.2 / 0.8 | re-forms killed familiars and sends both out again (the phase 3 transition sends them the first time); cooldown 35 s, weight 5 | 3, R |
| `stray_skulls` | rain 6 around herself, radius 7, 2.5 s, impact delay 0.9 | area | any | 1.0 (her eyes roll back, the crown sparks; one disc per impact, each at least the blast's reach) / 2.5 / 1.0 | 4 blight + Wither I 3 s per impact, so all six on one receiver stay at a rain's cap (1.5 x the skill cap: 24); blue skulls landing at random around her, as an idle vanilla head's (a rain centred on herself, kit-gaps 2), each `explosive` power 1 with the blue skull's rule (six blasts, 6.18's most per use; 4.5); cooldown 18 s, weight 5 | R |

"R" is the rebirth's final phase (4.4). Cooldowns and weights are first values, raised to their floors if under.

**Ultimate `birth_blast`** ("Birth Blast"; class ultimate; 1.6 / 3.0 + 0.2 / 1.8): she rises a block, arms wide, and
her whole body turns the blue-white of the vanilla spawn charge; a red disc of radius 7 grows under her for the
1.6 s wind-up with her line, a wide sound and the darkened screen for players who see her bar. Then she **channels**
for 3 s (eleven quickening heartbeats), healing 14 over the channel (the ultimate's heal cap), and bursts: every enemy
within 7 blocks takes 24 blight, Wither II for 5 s and knockback 3 up 1 (full strength, `HEROES_V2.md` 6.17). The
burst is also a blast, `explosive` power 4 at her body (TNT's power, 6.18's ceiling for an ultimate; vanilla's spawn
charge is 7): its block damage reaches about 6.8 blocks, inside the red disc (4.5). The channel is her `channel` weak
point: 15% of her maximum health in damage during it breaks it, cancels the burst and its blast and leaves her open
for 2 s. Line: "Eleven heartbeats. Count them with me."

**Awakened ultimate `skullfall`** ("Skullfall"; class ultimate; 1.6 / 3.0 / 1.5; from level 1 beside the first, as
every awakened ultimate): ten skulls rain on a circle of radius 6 around her target over 3 s, one disc per impact,
each 4 blight and Wither I for 3 s. Black skulls: they break no blocks (4.5), so Skullfall is the ultimate she uses
inside her own walls. She may wind it up in an aerial window (a `rain`, 5.1.5). The selector picks it over Birth Blast
when her target is far (band mid, far), she is airborne, or Birth Blast scores 0 near her own side's blocks. Line:
"Look up. All of you."

Both share the energy bar (100) and the minimum gap between ultimates (30 s, PROPOSED).

**Passives**

- **Three Heads, Three Hungers** (`three_hungers`): while the familiars are at her side, every 3 s in a fight each
  familiar fires a black skull at a random engaged enemy within 20 blocks in its sight, a different one from her target
  when there is one (vanilla's side heads): 3 blight + Wither I 2 s. Two parts, one per familiar. Format: trigger
  `every` 3, the new passive form `shot_random` (kit-gaps), `if` `adds_alive` 0: idle while the familiars are out as
  adds.
- **Undying Hunger** (`undying_hunger`): every 5 s in a fight she heals 1% of her maximum health (vanilla's
  regeneration), and each kill heals her 4 (vanilla's skull kill heals 5). Both within the passive heal cap (4).
- **Withered Blood**: immune to the Wither effect (the new `immune_effects: ["wither"]`), blight resistance 60. Every
  other effect reaches her, so Slowness II still downs her in the air.

**States**

- **`wither_armour`** (phase 2 on, and the rebirth): arrows, crossbow bolts, thrown tridents, wind charges and hero
  projectiles of the physical element do nothing to her (no damage, no ground poise: they glance off with a blue
  spark), from anyone; element projectiles, splash potions, melee and everything else hit as usual. Arrows still add
  **air** poise if she is airborne (a bounce still knocks a flyer), so a reach flight can still be shot down. While in
  it she takes no aerial windows, her `keep_distance` is 2 (she comes down to fight, as the powered vanilla Wither
  stops rising over its target), and her `skull_volley` has lost phase 1's preference (half as likely). Format: the
  state fields `projectile_immune` and `keep_distance` (kit-gaps). Line on entering: phase 2's.
- **`unbound`** (phase 3 while the familiars are out): speed +15%, `grave_grasp` preferred (x2: `prefer_when`
  `self_state` `unbound`), `three_hungers` idle.

**The skull familiar add** (`skull_familiar`, a `HeroAdd` of `HEROES_V2.md` 6.11, milestone 2's add system): the same
skull as her accessory part with a longer smoke trail. Each picks a different engaged enemy (her target last), floats
3 blocks from it and at most 3 blocks above the ground, and fires a black skull every 2.5 s (3 blight + Wither I 2 s;
no blast).
Health 10% of her maximum each (17 at level 10: three or four sword hits); arrows hit them (they have no wither
armour). After 30 s, or when she dies, they fly back to her side. A familiar killed by anyone opens her **broken
crown** weak point (4.4). No loot, no XP; they count in the add limits (4 alive per hero, 12 per fight).

### 4.4 The boss block

- **Health** (always, against anyone: `HEROES_V2.md` 6.2): `stats.health` 28, growth `balanced`, health scale 400
  (the legendary default; no leader bonus, she never leads), the marksman factor 1.3:

| Level | 1 | 10 | 20 |
|---|---|---|---|
| Max health | 146 | 169 | 195 |
| With the rebirth's 30% | +44 | +51 | +59 |

  At level 10 a player with an iron sword (6.2's reference rate) needs about 37 s of landed hits before the rebirth
  and about 11 s more after it, plus what Undying Hunger heals (about 0.34 a second): a longer fight than any slice
  hero (24-39 s), as the first legendary should be, still under her 180 s enrage once her wind-ups are dodged. Six
  iron soldiers who all reach her need about 7 s of landed blows between them, 10 s with the rebirth (but see her
  armour: from phase 2 arrows glance off and NPC archers draw their sidearms, 6.3; in phase 1 she is in the air part
  of the time, and arrows bring her down).
- **Poise**: 90 (the marksman's 70 raised: she fights in melee from phase 2). Max hit share 12%.
- **Phases**:

| Phase | At | What it adds | Transition |
|---|---|---|---|
| 1 | 100% | `skull_volley`, `ruin_skull`, `withering_pulse`, `rose_bloom`; aerial windows; Three Heads | - |
| 2 | 50% | the `wither_armour` state (arrows glance off; she comes down and fights close), `soul_dash`, `grave_grasp` | 1.5 s: the blue shell flares over her ribs. "Half of me is still more than all of you. Your arrows are nothing now." |
| 3 | 20% | the familiars unbound (the transition sends both out as adds), `unbound_heads` to send them again, the `unbound` state | 1.2 s: she tears the skulls from her side. "Go, my heads. Feed." |
| Rebirth | lethal damage, once per fight | she re-forms at 30% of her maximum: the familiars return to her side, `stray_skulls`, every earlier move | 2.0 s (the rebirth's re-forming: she sinks to her knees in soul sand and three skulls, the blue charge glow, then stands; 50% less damage and no stagger, never invulnerable). "You think the Wither dies once? I was built to be summoned." |

  The bar shows four pips from the start (three phases and the rebirth, `◆◇◇◆`), so the rebirth is never a
  surprise (`HEROES_V2.md` 6.7). Format: `boss.rebirth` as an object with its own `at`, moves and transition (kit-gaps).
  After the rebirth no earlier phase re-arms: 6.10's re-arm (health 10% above a phase's threshold) would otherwise
  replay phase 3 at 20%, since she re-forms at 30%. R is final; her familiars, back at her side, go out again only by
  `unbound_heads`.
- **Reactions** (each answer replaces her next pick; the same against a player and an NPC):

| When | Answer | Chance, cooldown | Phases |
|---|---|---|---|
| a close target | `withering_pulse` | 70%, 10 s | all |
| the target raises a shield | `rose_bloom` (zones ignore shields) | 40%, 10 s | 1 |
| the target raises a shield | `grave_grasp` (breaks the guard) | 50%, 10 s | 2-R |
| the target draws a bow | `skull_volley` | 50%, 8 s | 1 (from phase 2 her armour is the answer) |
| the target eats | `ruin_skull` | 60%, 12 s | all |
| the target is above her or airborne | `skull_volley` | 60%, 8 s | all |
| 3 or more enemies engaged | `ruin_skull` (its burst) | 40%, 14 s | all |
| a far target | `soul_dash` | 50%, 12 s | 2-R |

  An answer that the pick scores 0 is skipped and she picks as usual: Ruin Skull near her own side's blocks (6.18's
  own-side rule, 4.5), a move on cooldown or outside its phase.
- **Weak points** (4, the most a hero has; each hinted):
  - `smite` (NEW): a weapon's Smite enchantment counts against her as against an undead mob, +2.5 damage per level,
    whoever wields it (a player or an NPC soldier whose sword the enchanter gave Smite), under the per-hit cap. Hint:
    "Holy steel? How quaint. ...How it stings."
  - `channel`: Birth Blast breaks after 15% of her maximum health in damage (two hits or more: one capped hit is
    12%) and leaves her open for 2 s. Hint: her heartbeat line and the blue-white glow.
  - `charge_wall`: a `soul_dash` that ends against a wall staggers her (poise +40 on herself). Hint: "Out of my way,
    or into the wall!", and her stagger line "Walls... I hate walls."
  - **broken crown** (`focus_add` NEW, add `skull_familiar`): each familiar killed by anyone gives her 30 poise and
    +15% damage taken for 6 s. Hint: the familiars glow; "Careful with them. They are the only ones who listen."
  - and the element: radiant -20.
- **Enrage**: soft after 180 s (+15% damage, gap x0.7, poise maximum x1.25, a red bar); hard trigger
  `ally_hero_died` (a hero of her side dies within 24 blocks).
- **Intro** (1.2 s, super armour): she rises from a crouch in the air with her arms folded, the familiars snap to her
  sides and her eyes ignite. "Who summoned me this time? ...You? Then kneel, and wither."
- **Bar**: purple asked for (the vanilla Wither's); if the nearest-colour rule picks another, `boss.bar_color`
  (kit-gaps) sets it.

### 4.5 Terrain: what she breaks, what she pushes (revised 2026-11-18)

The owner's answer ("its ok for any hero or npc that usrs explosives to breach walls, theres no need for anyone to be
"civil" in battle, so they may use the resources they have to full advantage") ends the first design's two
exceptions: her moves no longer leave every block alone, and her hands are no longer a breach tool. The vanilla
Wither's own rules are the guide, held to `HEROES_V2.md` 6.18's limits.

**Her explosive moves** (6.18's `explosive` property: each blast is terrain only, hurting and pushing nobody; the
move's own effects do that, under every cap):

| Move | The vanilla original | Her blast | What it breaks |
|---|---|---|---|
| Ruin Skull (heavy) | the blue skull: a power-1 explosion that counts every block but air and `#wither_immune` as no stronger than 0.8 | power 1 with that rule (`resistance_cap` 0.8), one blast at the impact | one to three blocks of the face it hits, one block deep (rarely two), in anything but `#wither_immune` (bedrock, barriers, portals and their frames, reinforced deepslate, light, command, structure and jigsaw blocks, a moving piston): stone, deepslate, obsidian and crying obsidian alike. Two or three skulls open a 1-wide, 2-high gap in a 1-thick wall |
| Stray Skulls (area, rebirth only) | an idle side head's blue skull at a random point within 10 blocks | six blasts of power 1 with the same rule, one per impact (6.18's most per use) | six such bites at random within 7 blocks of her, each under its disc |
| Birth Blast (ultimate) | the spawn charge's power-7 explosion at its eyes, after 11 invulnerable seconds | power 4 with ordinary resistance (the spawn explosion has no blue-skull rule), one blast at her body when the channel completes; a broken channel blasts nothing | a TNT crater round her: dirt and sand about 3 blocks deep, stone and bricks 1-2, never obsidian; its block damage reaches about 6.8 blocks, inside the red disc of radius 7 |

- **Why power 4 for Birth Blast, not 7**: 6.18's ceiling for an ultimate holds for every hero, and the reason fits her
  too. A power-7 blast breaks blocks about 12 blocks out, past the 7-block red disc that is the warning: a player or a
  soldier standing outside the disc, as the tell told it to, would lose the floor under it. At power 4 the crater stays
  inside the disc. (Vanilla's charge is 11 s long with the boss invulnerable; hers is 4.6 s and breakable.)
- **Why the blue skulls keep their rule**: it is vanilla's own (`WitherSkull.getBlockExplosionResistance`, read in
  the 1.21.1 sources with NeoForge's `canEntityDestroy`) and the Wither's mark: nothing but the blocks vanilla keeps
  from her shelters from her. It stays readable and slow: one bite per Ruin Skull, at most every 14 s, behind a 1.0 s
  wind-up, a strip and a disc, flying at 60% of a shot's speed; Stray Skulls only in the rebirth, every 18 s at most.
  It needs one new field (`explosive.resistance_cap`, kit-gaps), which the validator allows only at power 1 on a
  legendary. To check in B3: vanilla reads a water cell through the same method, so a moat may not shield a wall from
  a blue skull, as in vanilla.
- **Her black skulls break nothing** (basic attack, Skull Volley, Three Heads, the familiars' shots, Skullfall).
  Vanilla's are power-1 blasts too, and this is the one vanilla blast she does not keep: 6.18 makes every blast at
  least a heavy move with a decal, and her basic skull (every 1.5 s), her volley (light, her move in the air) and her
  familiars' shots would break pillars and bridges under people with no tell; what they would break (dirt, sand,
  glass, leaves, some wood, never stone) decides no fight and opens no wall; and Skullfall's ten impacts are more than
  6.18's six blasts per use, which leaves it the ultimate she may use at home.
- **Never her own side** (6.18): the pick scores 0 an explosive move whose blast would reach her own side's planned
  blocks or anything inside the walls box of a base of her faction or of a pact ally: Ruin Skull at its target's place,
  Stray Skulls round herself (radius 7 plus the blast's reach), Birth Blast at her body. Defending inside her own walls
  she fights with the rest of her kit (basic skulls, Skull Volley, Withering Pulse, Rose Bloom, Soul Dash, Grave Grasp,
  Unbound Heads, Skullfall). Once a wind-up starts the blast is real wherever the move lands.

**Withering body: the trap break-out** (vanilla's `destroyBlocksTick`, kept for the trap it answers). In a fight, when
she is **walled in** (every cell beside her hitbox blocked and no path to any engaged enemy for 1 s: the classic box for
a Wither), the blocks from her feet up to the cell above her head and one block out on each side (a box 3 wide, 4 high
and 3 deep round her body; never below her feet, as vanilla's starts at the feet, so she never digs the floor away)
wither at once and drop as if mined, with vanilla's `entity.wither.break_block`. Never a `#wither_immune` block, never a
planned block of her own side or a pact ally; only with `mobGriefing` on (vanilla's own check); at most once a second.
Planned blocks of an enemy base that it withers go into that base's repair queue at once, as an explosion's do
(`FCEvents.onExplosion` queues those; no event fires for a mob's own block break, so B3 calls the queue directly).
Vanilla does it 1 s after every hurt; she does it only when boxed. On every hurt it would erase every wall and doorway
she fights beside, a breach nobody aims and nobody sees coming (6.14's tells, 6.18's decals), and the doorway and tunnel
answers of `HEROES_V2.md` 6.16 and of her own sheet would be gone.

**Withering hands** (her `BlockWork`, as before): the break speed and harvest level of an unenchanted **diamond
pickaxe** for every block a pickaxe, axe or shovel suits, with that tool's vanilla drops and no wear; never a
`#wither_immune` block. She uses them at her duty (a miner, 4.9, under the work caps of `MOBILITY_AND_WORK.md` 13), to
reach an unreachable target after 10 s as every hero does (`HEROES_V2.md` 6.12, under `Reach`'s may-break rule), to
break out of a box at once while `mobGriefing` is off, and to open a gateway a player blocked, as any hero's "open the
way" does from inside. They never break a faction's planned walls: the first design's 2x3 breach by hand is gone.

**The breach on a raid**, no longer an exception. When her party's soldiers are held outside (the "open the way" goal
of `MOBILITY_AND_WORK.md` 5.2) she takes the way that opens soonest, as any hero: over the wall to the gate when it
opens from inside; else the breach spot that `docs/design/breaching.md` 6.2 picks, blasted with Ruin Skull (6.18's one
use of a move against a block, in that goal only). She casts it at the spot up to three times, its 14 s cooldown
setting the pace (about 30 s for a gap in a 1-thick wall), then joins the assault. To the planner her blue skull makes
every wall breakable but the wither-immune: TNT cannot open obsidian; she can. She may also carry TNT and breach by
hand like any breacher (`docs/design/breaching.md` 5). The defenders answer as they answer any breach (shoot the
breacher: an airborne hero is already their second target; rebuild the hole after the alarm), and she never blasts
within 16 blocks of a base of her own side (`war.tntHomeMargin`, as breachers).

**Switches**: the game rule `mobGriefing` false keeps every block whole against her blasts and her body, as it does
against the vanilla Wither's (her moves still hurt and push; walled in she digs out with her hands; she plans no blast
breach), and so does `heroes.explosiveBlocks` false for her moves; TNT she carries is a player's TNT, which that game
rule does not touch, as for every breacher; `war.explosives` false stops planned breaches, hers and TNT's included
(`docs/design/breaching.md` 10).

**Her pushes** (`HEROES_V2.md` 6.17): Withering Pulse (knockback 2), Ruin Skull (1), Soul Dash (2; a charge: its line
runs through the target toward the edge and she stops short of it), Birth Blast (3 up 1) and her phase shockwave land
at full strength wherever they send an enemy, a player, an NPC or a hero alike, and she walks round to aim them at
lava, fire, a cliff or the void, as any hero does (the walk round is the tell). Her own side is never pushed, and her
own body never goes into a hazard (her hover drifts only over gaps she can cross; her dash stops short).

**Pushed herself**: she flies, so a cliff is nothing to her (6.17 ignores drops for a flyer; the void stays harmful);
lava and fire hurt her in full, with no per-hit cap (fire resistance 30); her knockback resistance is the marksman's
0, so she flies far when hit hard. A death by terrain within 15 s of an enemy's push is that enemy's kill, and her
Hollow Star floats up out of lava (`ARTIFACTS.md` 11).

### 4.6 Mobility

- **Traits**: primary `flyer` tier 3 (true flight with stamina; legendary, so III is allowed), secondary `hover` tier 2:
  on the ground she always floats about a block up, never tramples farmland, drifts over small gaps and stays within
  melee reach (MOBILITY 1.11). A flyer may not take `hover` today (1.14); kit-gaps proposes allowing it for a flyer
  whose look never touches the ground. Carrier: `levitation`, part `robes/smoke_gown` (the gown lifts and smoke pours
  from the hem at take-off: the 0.6 s tell).
- **Aerial windows**: phase 1 only (`aerial.phases [1]`, `max_air` 8, `dives` 1, weight 5): the vanilla turret phase.
  In a window she rises at most 6 blocks over the ground or the highest engaged enemy, fires `skull_volley` (at most
  3 light ranged moves per take-off) and her basic skulls, may wind up `skullfall`, and lands within 8 s; at least 6
  s on the ground between take-offs; at most 40% of any 30 s in the air; at most 4 s out of every enemy's reach. From
  phase 2 the armour keeps her low: no windows, only reach flights (to a target on a pillar or a wall), where arrows
  still add air poise.
- **Numbers** (flyer III; hover II), level 1 / 10 / 20:

| | 1 | 10 | 20 |
|---|---|---|---|
| Stamina (s of powered flight) | 36 | 47.4 | 60 |
| Cruise (blocks per second) | 6.5 | 7.2 | 8.0 |
| Climb (blocks per second) | 2.4 | 3.2 | 4.0 |
| Glide: sink, forward (b/s) | 3.3, 7.7 | 2.5, 8.8 | 2.0, 10.0 |
| Glide time (s), gusts | 12, 2 | 15.8, 2 | 20, 2 |
| Take-off needs | 33% | 25% | 20% |
| Stamina back on the ground (per s) | 1.8 | 2.4 | 3.0 |
| Hover height (blocks), gap drift, step | 0.6, 2, 1.3 | 0.8, 2, 1.4 | 1.0, 3, 1.5 |

- **Out of fights**: she flies the long legs of her trips and to her mine; in a thunderstorm she floats on foot.
- **Raids**: as a non-leader marksman she joins her base's raids (`raids: joins`). She scouts first as a flyer (circling
  the target 8-24 blocks out), crosses the wall at the raid ceiling when she has a way out, and her goal is "open the
  way" when her soldiers are held outside (the gate from inside, else her blue skulls at the breach spot: 4.5), else
  clearing the defenders' archers (a marksman, `MOBILITY_AND_WORK.md` 5.2). Defending, she patrols over her walls in
  phase-1 style and meets airborne intruders with her skulls, without her explosive moves inside her own walls (4.5).

### 4.7 The end of a fight

**She fights to the death.** Nerve = aggression 80 + honour 45 - caution 35 = 90, well over 65: a tyrant's pride and a
weapon's purpose. The file pins it (`when_losing: last_stand`) so that tuning her traits never flips it: the vanilla
Wither never flees, and her death is how her Hollow Star changes hands. Her `retreat_below` would be 34 (marksman 40,
+20 x (0.35 - 0.5), -10 x (0.80 - 0.5)), so her last-stand beat comes at 10% (a third of it, kept 10 under her last
phase's 20%): her `last_stand` line, a `determined` face, once per fight. Then the rebirth (once per fight) and the
final phase; at zero health after that she dies like anyone, nothing cancels the blow, and the bar shows "fallen". She
returns 10 minutes later at her home base: the soul-sand T and three skulls assemble beside the station over the last
10 s (block displays) and she bursts out of them (presentation; no death ability changes the time).

### 4.8 Personality

- **Temperament** `tyrant`; traits a 80, h 45 (+15: she keeps the bargain of whoever carried her token), m 90, c 35,
  g 65, s 20, l 55 (+15: bound by the token), v 45; each within 20 of the tyrant's defaults.
- **Fight AI**: bold (a 80): offensive moves x1.18, zoning and defensive ones x0.91; focus `weakest` (the marksman's);
  honour under 60, so she pursues a hero that escapes her.
- **Likes**: soul sand, wither roses, bones. **Dislikes**: milk buckets ("A cure. How insulting."), golden apples.
- **Quirks**: "her two skulls bicker in whispers; she never says which one is right"; "counts the wither roses that
  bloom where her enemies fell."
- **Relationships**: `rival` the_ender_dragon (milestone 3 bonds: -0.2 compatibility; their lines needle each other).
- **Recruit**: `token` only (kit-gaps).

### 4.9 Duty, talents and work

- **Duty**: `miner` (when not fighting). She works it with her withering hands (4.5) and a miner's proficiency (+15 /
  25 / 35% work speed, 21 / 24 / 27 backpack slots, +0.5 / 1 / 1.5 reach, -10 / 15 / 20% work hunger, +10 / 20 / 30%
  work XP; no tool wear at all), and answers every alarm.
- **Talents** (tiers I / II / III at levels 1, 10, 20; a talent works at the base where she is, not on a raid; the
  strongest copy at a base counts):
  - `surveyor` (her specialty, milestone 1): mine-site search radius +8 / 16 / 24, the diamond layer one level sooner;
    when she mines herself, virtual Fortune +1 / +2 / +3 on the ores and vein following one block wider.
  - `purifier` (milestone 1): allies at her base (NPCs, heroes and member players alike) lose poison, wither and
    hunger effects every 30 / 20 / 10 s: the queen of withering draws it out of her own people. Presence (11.2):
    one tier higher at home; personal touch: Slowness, Weakness and Mining Fatigue lifted from allies within 8 blocks
    of her once every 60 / 45 / 30 s.
  - `intimidation` (milestone 2): rival war councils see her base's defence x1.15 / 1.3 / 1.45; strangers' hostile
    threshold -10; Presence's personal touch: raiders who see her on the walls fall back 10 / 15 / 20% sooner.
- At home she uses the shared mining clips with her hands (`work.mine` overridden: she presses a palm to the stone
  and it greys and crumbles).

### 4.10 Voice and lines

- **Voice**: generated (S1), a low breathy female voice with two faint harmonies, a fifth below and an octave above
  (her familiars); fallback blip `bass`, pitch 0.85, rate 3 ticks per syllable; hurt `entity.wither.hurt` (4.11).
- **Lines** (format events of `HEROES_V2.md` 12.15 and v1 23; `{faction}`, `{enemy}`, `{player}` as there; the new
  events `rebirth` and `unbound` are kit-gaps):

| Event | Lines |
|---|---|
| `summoned` (the calling) | "Who carries my token carries my debt. Very well, {faction}. Point me at something." / "Soul sand, three skulls, one will. I am yours, {faction}. Try to deserve it." |
| `greeting` | "{player}. You may speak. Briefly." / "Ah, {player}. My skulls were just discussing you." |
| `idle` | "The left one wants to wither the chickens. The right one agrees. I am undecided." / "Everything ends. I just arrive early." |
| `work` | "Stone is only slow decay. I hurry it along." / "Another seam. The ground keeps its treasures so badly." |
| `night` | "Night. Finally, a colour that suits me." |
| `small_talk` | "Do not stand so close. Things near me tend to... wilt." / "I was summoned to destroy. You have me digging. How droll." |
| `fight_start` | "Something to wither. Good." / "Heads, wake up. We have guests." |
| `intro` | "Who summoned me this time? ...You? Then kneel, and wither." / "Look at you, {enemy}. Already greying." |
| `skull_volley` | "Three heads, three hungers." |
| `ruin_skull` | "This one is special." |
| `soul_dash` | "Out of my way, or into the wall!" |
| `ultimate` (Birth Blast) | "Eleven heartbeats. Count them with me." |
| `ultimate` (Skullfall) | "Look up. All of you." |
| `phase_2` | "Half of me is still more than all of you. Your arrows are nothing now." |
| `phase_3` | "Go, my heads. Feed." |
| `rebirth` | "You think the Wither dies once? I was built to be summoned." / "Again. I was always meant to happen twice." |
| `enrage` | "Enough. Wither, all of you, wither!" |
| `stagger` | "Walls... I hate walls." / "Insolent stone." |
| `kill` | "Withered." / "One more rose." |
| `ally_fallen` | "Who dares spend what is mine?" |
| `victory` | "Quiet again. The way I like it." |
| `last_stand` | "Run? The Wither does not run. The Wither ends things." |
| `fallen` | "Soul sand... remembers. I will be summoned again." / "Count to six hundred, little ones." |
| `revived` | "Soul sand, three skulls, one will. I am back." |
| `raid_start` | "Their walls will be stone dust by noon." |
| `base_attacked` | "Someone is knocking at my queendom. Let us answer." |
| `level_up` | "Stronger. The skulls approve." |
| `artifact` | "A trinket. It will do." / (Hollow Star) "My heart. Give it back next time you find it." |
| `artifact_ask` | (her Hollow Star, held by an ally) "That star is mine. You were only keeping it warm." |
| `unbound` | "No walls, no banner, no bargain. I return to my skulls." |
| `escape` | none: she never escapes (temperament lines cover a forced escape by an operator hook) |

### 4.11 Sound map (`herosrc/sfx/the_wither.sfx.json`; `SOUND.md` rules: vanilla by id where it is exactly that thing)

| Moment | Sound | Slot (who plays) |
|---|---|---|
| Voice | generated `voice` (three-voice blips) | `voice.talk` (client) |
| Hurt, death | `entity.wither.hurt` pitch 1.5 volume 0.5; `entity.wither.death` pitch 1.3 volume 0.7 | `voice.hurt`, `voice.death` |
| Basic skull | release `entity.wither.shoot` pitch 1.3 volume 0.5; impact generated `skull_hit` with `entity.generic.explode` pitch 1.8 volume 0.2 | `moves.basic` |
| Skull Volley | wind-up generated `skulls_gather`; release three `entity.wither.shoot` (companions at 0, 2 and 4 ticks); impact `skull_hit` | `moves.skull_volley` |
| Ruin Skull | wind-up generated `blue_charge` (a rising glassy hum); release `entity.wither.shoot` pitch 0.8; impact `entity.generic.explode` pitch 1.2 volume 0.5 with generated `blue_burst` (the blast itself is vanilla's explosion, sound and particles by id, 6.18) | `moves.ruin_skull` |
| Withering Pulse | wind-up generated `wither_inhale`; impact generated `wither_pulse` with `particle.soul_escape` | `moves.withering_pulse` |
| Rose Bloom | wind-up generated `rose_whisper`; each pulse `particle.soul_escape` volume 0.6 | `moves.rose_bloom` |
| Soul Dash | wind-up generated `dash_shriek` with `entity.wither.ambient` pitch 0.8; impact `entity.player.attack.knockback` with generated `bone_crash`; a wall: `cues.charge_wall` | `moves.soul_dash` |
| Grave Grasp | release generated `claw_rake` (three variants); impact `entity.player.attack.sweep` pitch 0.8 | `moves.grave_grasp` |
| Unbound Heads | wind-up generated `heads_tear` with `entity.wither_skeleton.ambient` pitch 1.5; the familiars' shots `entity.wither.shoot` pitch 1.6 volume 0.4 | `moves.unbound_heads`, add sounds |
| Stray Skulls | wind-up generated `stray_chatter`; impacts `entity.generic.explode` pitch 1.6 volume 0.3 (each blast vanilla's, 6.18) | `moves.stray_skulls` |
| Birth Blast | wind-up `entity.wither.ambient` pitch 0.6 with generated `birth_charge` (wide, 48 blocks); loop generated `heartbeat_loop` (eleven quickening beats under a rising whine); release `entity.wither.spawn` pitch 1.1 with `entity.generic.explode` pitch 0.9; broken: `cues.channel_break` and generated `charge_fizzle` | `moves.birth_blast` |
| Skullfall | wind-up generated `skullfall_call`; impacts `entity.wither.shoot` pitch 1.2 with `entity.generic.explode` pitch 1.7 volume 0.25 | `moves.skullfall` |
| Phase 2 | generated `armour_rise` with `item.armor.equip_netherite` pitch 0.6 | `phases.2` |
| Phase 3 | generated `heads_unbound` with `entity.wither_skeleton.ambient` pitch 1.4 | `phases.3` |
| Rebirth | `entity.wither.spawn` pitch 0.8 with generated `rebirth_swell` (wide) | new `rebirth` slot |
| Arrows glancing off her armour | `item.shield.block` pitch 1.6 volume 0.5 | `states.wither_armour` |
| Weak points | `cues.weak_point` with `entity.wither_skeleton.hurt` pitch 1.2 (smite); a familiar killed: `entity.wither_skeleton.death` pitch 1.5 | `weak_points.*` |
| Breaking out of a box (her body), mining by hand | `entity.wither.break_block` pitch 1.2 volume 0.6 (the box: vanilla's level event 1022, as the vanilla Wither's) | `mobility.break` (new) |
| Take-off | generated `soul_lift` (a whoosh of smoke) with `cues.takeoff` | `mobility.takeoff` |
| Intro | generated `intro` (a three-voiced low laugh) with `entity.wither.ambient` pitch 1.3 volume 0.6 | `intro` |
| Idles | `entity.wither.ambient` pitch 1.4 volume 0.25 (a sigh); the familiars' bicker `entity.wither_skeleton.ambient` pitch 1.7 volume 0.4 | `quirks.*` |
| The calling, the revival | `block.soul_sand.place` x4 (the T), `entity.wither_skeleton.ambient` x3 (the skulls), generated `calling_charge` (10 s), then `entity.wither.spawn` (within 64 blocks); the revival adds `cues.revived` | new `calling` slot |
| Steps | none (she floats) | `steps.volume` 0 |

### 4.12 Own animations

`move.skull_volley`, `move.ruin_skull`, `move.withering_pulse`, `move.rose_bloom`, `move.soul_dash`,
`move.grave_grasp`, `move.unbound_heads`, `move.stray_skulls`, `ultimate` (+ `ultimate.loop` for the 3 s charge),
`ultimate.awakened`, `phase.2` (the blue shell flares across her ribs), `phase.3` (she tears the skulls from her side),
`rebirth` (kneeling into soul sand and rising; a new action code, kit-gaps), `intro`, `victory` (she turns her back,
the familiars cackle), `taunt` (a slow beckoning finger), `last_stand`, `personal.1` (the familiars bicker),
`personal.2` (turning a wither rose), `personal.3` (a slow floating half-turn, arms folded), `state.wither_armour`,
`work.mine` override (palm to the stone). The familiars are keyed in every clip so they aim with her; `sec.familiars`
bobs them when no clip keys them. Overrides of the core locomotion: `move.idle`, `move.walk` and `move.run` are her
floating glide (the gown sways, the feet hang), so she never "walks".

### 4.13 Her artifact: Hollow Star (signature; revised 2026-11-18)

A nether star burnt black, with a soul-blue light caught inside, worn as a brooch at her collar. It cannot be
crafted; it comes with her the first time she joins a faction and drops where she dies (`ARTIFACTS.md` 11). One
instance per world: a copy is a faded copy, and a destroyed one re-forms with her (15.1 there). **It grows by its own
design**: the dark star brightens on the withering its wearer deals, the owner's "an artifact that needs blood to get
stronger" in the Wither's own terms (the vanilla Wither feeds: it regenerates, and a skull kill heals it).

```jsonc
// data/factionclash/artifacts/hollow_star.json (ARTIFACTS.md 16.1, 16.4 and 7.8)
{
  "format": 2, "id": "hollow_star", "name": "Hollow Star", "tier": "signature", "element": "blight",
  "lore": "The Wither's heart, dimmed but never out.",
  "stats": { "ability_power": 12, "drain": 2, "resist_blight": 10, "health": 5 },        // 2 + 1 + 1 + 1 = 5 points
  "growth": {                                                     // NEW on a signature (kit-gaps)
    "counts": "withering",       // NEW counter: Wither-effect damage its wearer gave to enemies (kit-gaps)
    "base": 60,                  // steps at 60, 180 and 360 (x 1, 3, 6); three steps, since it is never upgraded
    "steps": [ { "ability_power": 6 }, { "drain": 2 }, { "health": 5 } ]                 // 1 point each: 8 when full
  },
  "passive": {
    "name": "Withering Brand", "unique": "withering",
    "description": "The first hit of each of its moves withers the enemy (Wither I, 3 s); an enemy it withered that dies within 3 s heals it 3.",
    "parts": [
      { "trigger": "on_move_hit", "form": "trigger_enemy",
        "effects": [ { "type": "effect", "id": "wither", "level": 1, "seconds": 3 },
                     { "type": "mark", "mark": "hollow_brand", "seconds": 3 } ] },
      { "trigger": "on_kill", "form": "self", "if": { "condition": "target_has_mark", "mark": "hollow_brand" },
        "effects": [ { "type": "heal", "amount": 3 } ] } ]
  },
  "suits": ["blight", "casters", "moves"], "source": "signature",
  "signature": {
    "hero": "the_wither", "reforms": true, "worn_part": "accessories/hollow_star_brooch",
    "bond": {
      "stats": { "element_damage": 6, "health": 5, "drain": 2 },                       // +3 points, her own only
      "passive": { "name": "Third Hunger", "unique": "withering",
        "description": "Her brand is Wither II for 3 s; an enemy she withered that dies heals her 4 and gives 10 energy." },
      "kit_mods": [
        { "move": "skull_volley", "count": 1 },                                          // 3 skulls become 4 (4 x 4 = 16, the skill cap)
        { "move": "ruin_skull", "radius": 1 }                                            // its burst 3 -> 4 blocks (its blast stays power 1)
      ]
    }
  },
  "sounds": { "proc": "minecraft:entity.wither.shoot", "step": "minecraft:entity.wither.ambient" }
}
```

- **Fixed strength** (`ARTIFACTS.md` 4.2): its numbers are its own, the same on any hero at any level (the first
  design's attunement, 60% at level 1, is gone). **Any other hero**: ability power +12%, drain +2%, blight resistance
  +10%, health +5% and Withering Brand, plus whatever its growth has added. Best on blight casters (Ambrose's venom kit
  gets the brand, not the element line: the element line belongs to blight only, `ARTIFACTS.md` 4.1).
- **The Wither herself**: all of that, plus her bond: element damage +6%, health +5%, drain +2% (within the build
  caps), Third Hunger instead of Withering Brand (the same family), four skulls in Skull Volley and a 4-block Ruin Skull
  burst. Its blight line does nothing on her (her own blight resistance 60 is already at the resistance clamp, -30 to
  +60, of v1 6.1), which the bond's three points more than make up for. Healing from it counts under the artifact
  healing ceiling (10% of her maximum per 30 s). A kit mod never changes a move's blast (16.4 there).
- **Growth: the star brightens** (`ARTIFACTS.md` 7.8's rules, the same as the eight growing artifacts'):
  - *What counts* (`withering`): each point of damage the Wither effect deals to an enemy when that Wither came from
    its wearer (its basic attacks, moves and passives, the brand included; never its adds' or summons'), only while a
    living hero wears it in a slot; nothing asks whether the enemy is a player. On her that means the side heads'
    shots count while they float at her side (Three Heads, Three Hungers is her passive), and stop counting once she
    sends them out as adds in phase 3. Vanilla's Wither I deals 1 point every 2 s, Wither II every second.
  - *Steps* at 60, 180 and 360 points, each adding one point, in order: ability power +6%, drain +2%, health +5%. Full,
    it is 8 points: ability power +18%, drain +4%, blight resistance +10%, health +10% (on her, with the bond: drain at
    the 6% cap, health +15%). Three steps is its cap: two plus its upgrade level, which stays I.
  - *How fast*: she feeds it fastest, since nearly every move of hers withers: about 40-80 points in a fight with a raid
    party, so it is full after five to eight such fights. A blight caster with the brand alone feeds it about 10-20 a
    fight, twenty fights or more.
  - *It keeps its growth* through her death, a theft, a hand-over or her return to her token; only destroying it (the
    void, a command) loses it, and a re-formed star starts again at step 0.
  - *Shown*: the tooltip ("Brightens with the withering its wearer sows: step 1 of 3 (75 / 180)") and the hero sheet.
    Its icon keeps the signature's slow pulse with no fill states, and the brooch on her body does not change, so the
    icon and the look already drawn stay as they are.
- **Never upgraded**: no tinkerer can work it (it cannot be crafted, and no bench recipe knows it); brightening is its
  way to get stronger. The validator refuses an `upgrade` on a signature that grows (`ARTIFACTS.md` 16.5).
- **War rating** 0.12, plus 0.015 a growth step: 0.165 when full (`ARTIFACTS.md` 13).
- **Her choice** (the owner's "heroes decide"; `ARTIFACTS.md` 10.4). No rule pins it to her: she chooses it, and by
  the same formulas as every hero she always does.
  - *Use*: its worth to her is its lines, her bond's lines, Third Hunger and two of her own moves made better (which no
    other artifact gives and 10.2 does not price, so 10.4 counts her want as unbounded), and a tyrant's ambition (90)
    favours the signature tier (x1.16). At level 1 it fills her one slot; from level 5 she chooses around it.
  - *Swap, pass on, ask*: she never takes it off for another. No other hero gets her bond, so none wants it 1.5 times
    as much as she does: she never passes it on, and an ask for it always fails, a leader's wish included.
  - *Held by an ally*: after her death another hero of her faction may pick it up and wear it (its own choice). It is
    then held as **lent by her** (a standing lent mark: kit-gaps): at her first choice moment after her revival she
    asks for it back; a holder with honour 50 or more gives it back at once, as any lent artifact; any other weighs the
    ask by its generosity toward her (10.4), helped by the leader's particular wish "a signature back to its own hero"
    (10.5). A greedy, disloyal ally may keep it a while; she asks again at each choice moment.
  - *In the store*: she claims it first while she is in the faction.
  - *Worn by an enemy hero*: she knows her own heart when she sees it. That hero is her preferred target in any fight
    while she sees it wearing the star (a focus rule like a marked enemy's: kit-gaps), the only way to get it back
    being that hero's death (it drops) or a raid on the store that holds it. Worn by anyone else, it gives the base
    lines, the growth and Withering Brand, never the bond.
- It changes hands at her death (it drops where she fell) or at her return to her token (it drops where she stood).

### 4.14 The hero file (abridged sketch)

```jsonc
{
  "format": 2, "id": "the_wither", "name": "The Wither", "title": "Queen of Three Skulls", "gender": "female",
  "archetype": "wither", "rarity": "legendary", "element": "blight",
  "resistances": { "blight": 60, "fire": 30, "radiant": -20 },
  "immune_effects": ["wither"],                                                        // NEW (kit-gaps)
  "stats": { "health": 28 },
  "behaviour": { "combat_role": "marksman", "duel_style": "zoner", "duty": "miner", "raids": "joins",
                 "break_out": "box" },                         // NEW: vanilla's box rule when walled in (4.5)
  "work": { "tool": "diamond_pickaxe" },                         // NEW: her hands as the BlockWork tool
  "basic_attack": { "style": "thrown", "amount": 4, "interval": 1.5, "range": 16, "projectile": "wither_skull",
                    "on_hit": [ { "type": "effect", "id": "wither", "level": 1, "seconds": 3, "chance": 25 } ] },
  "moves": [ /* the eight of 4.3; ruin_skull and stray_skulls carry "explosive": { "power": 1,
                "resistance_cap": 0.8 } (resistance_cap NEW: the blue skull's rule) */ ],
  "ultimate": { "id": "birth_blast", "explosive": { "power": 4 }, /* ... */
                "awakened": { "id": "skullfall" /* black skulls: no blast */ } },
  "passives": [ /* three_hungers (form shot_random, NEW), undying_hunger */ ],
  "states": [ { "name": "wither_armour", "projectile_immune": true, "keep_distance": 2 },  // NEW fields
              { "name": "unbound", "stats": { "speed": 15 } } ],
  "boss": {
    "health_scale": 400, "poise": 90,
    "phases": [ { "at": 100 }, { "at": 50, "states": ["wither_armour"] },
                { "at": 20, "adds": [ { "id": "skull_familiar", "count": 2 } ], "states": ["unbound"] } ],
    "rebirth": { "at": 30, "moves": ["stray_skulls"], "transition": { "seconds": 2.0 } },  // NEW: an object
    "weak_points": [ "smite", "channel", "charge_wall",                                    // smite NEW
                     { "kind": "focus_add", "add": "skull_familiar", "poise": 30,         // NEW
                       "damage_taken": 15, "seconds": 6 } ],
    "enrage": { "after": 180, "triggers": ["ally_hero_died"] }, "bar_color": "purple"    // bar_color NEW
  },
  "personality": { "temperament": "tyrant", "when_losing": "last_stand",
                   "traits": { "a": 80, "h": 45, "m": 90, "c": 35, "g": 65, "s": 20, "l": 55, "v": 45 } },
  "leader": { "eligible": false },
  "talents": [ { "id": "surveyor" }, { "id": "purifier" }, { "id": "intimidation" } ],
  "recruit": { "condition": "token" },                                                  // NEW
  "relationships": [ { "hero": "the_ender_dragon", "kind": "rival" } ],
  "mobility": { "primary": { "trait": "flyer", "tier": 3 }, "secondary": { "trait": "hover", "tier": 2 },
                "carrier": { "kind": "levitation", "part": "robes/smoke_gown" },
                "aerial": { "phases": [1], "max_air": 8, "dives": 1, "weight": 5 } },
  "voice": { "blip": "bass", "pitch": 0.85, "rate": 3 },
  "art": { "form": "adult", "build": "slim", "scale": 104 /* parts, palette and face of 4.2 */ }
}
```

---

## 5. The Ender Dragon (F, ender dragon, vanguard, arcane, proud noble, legendary)

| Field | Value |
|---|---|
| Id, name, title | `the_ender_dragon`, "The Ender Dragon", "Empress of the End" |
| Gender, archetype, rarity | female, `ender_dragon` (group E, outside the roster; `hybrid: west_dragon` for look defaults), legendary |
| Role, duel style | `vanguard`, `summoner` (she fights through her crystals: break them or dive her) |
| Style, prop | `fists`: obsidian dragon claws (the mechanical item is an iron sword drawn as claws) |
| Element, resistances | arcane; arcane +40, fire +40 (the vanilla dragon is fire-immune); no elemental weakness (her weak points carry it) |
| Temperament | `proud_noble`; when losing: last stand |
| Mobility | flyer III, carrier: her dragon wings (worn), aerial windows in all three phases, circling and perching |
| Duty, talents | guard; `revival_rite`, `lookout`, `cartographer` |
| Leader | not eligible |

### 5.1 Concept

The Empress of the End has ruled an island of pale stone over the void for longer than anyone has counted, and she
does not think a token makes her anyone's servant: she has decided to grant a faction the honour of her company. She
is regal, vain, curious about the green world and amused by its people, and she fights as the boss does: she circles
overhead spitting ender fireballs that leave poisoned clouds, dives at whoever annoys her, lands on the highest perch
in reach to breathe dragon's breath across the ground (arrows glance off her while she sits there), and raises end
crystals on obsidian spires that heal her through a purple beam until someone breaks them. The fight is about her
crystals: shoot the open ones, climb to the caged ones, and catch her when she lands. When she dies an egg waits at
her home base, and ten minutes later she hatches from it.

### 5.2 Look

- **Body**: base `female`, form `adult` (her one body for good), build `standard`, scale 108: 1.97 blocks (under the
  1.98 cap). Tall and strong; modest: a high collar, long sleeves and a long skirt split only to the knee.
- **Parts**:

| Slot | Part | Status | What it is |
|---|---|---|---|
| hair | `hair/long_layered` | built | long, black with a deep-purple under-layer (`hair_accent`), swept back from the horns |
| horns | `horns/swept_dragon` | planned (full library) | two horns swept back from the temples, black, their tips purple (glowing in phase 3) |
| wings | `wings/dragon` | planned (full library), NEW work | large dragon wings: black finger bones, purple membrane; folded against her back on the ground, about 2.2 blocks across open; the flap, glide and fold loops of G10 |
| tails | `tails/scaled` | planned (full library) | a long scaled tail (a five-bone chain plus a second short chain for its tip), black, with pale spines along its top |
| markings | `markings/scales` | planned (full library) | black scale patches on the cheekbones, the forearms and the shins |
| markings | `markings/dorsal_spines` | NEW | a row of pale end-stone-coloured spines from her nape down between the wings to the tail: the vanilla dragon's back |
| outfit_top | `outfit_top/scale_bodice` | NEW | a high-collared black scale-mail bodice with purple trim, long sleeves |
| outfit_bottom | `outfit_bottom/long_skirt` | planned (full library) | a long skirt, black over purple, split to the knee at the front, on the skirt panel bones |
| armour | `armour/spined_pauldrons` | NEW | obsidian-black pauldrons, each with three pale spines |
| legwear | `legwear/boots` | built | black, the toe caps painted as claws |
| weapon | `weapons/dragon_claws` | NEW | obsidian talons over both hands (`pair`), a purple glow inside; no sheath (they are her hands) |
| (signature) | `accessories/spirecore_pendant` | NEW | Spirecore's `worn_part`: a tiny caged crystal on a chain at her collarbone, slowly turning; drawn only while she wears it (5.13) |

- **Palette**: skin `#E9DDE6` (pale, a lilac undertone), skin_shade `#B9A3B8`, hair `#1B1720`, hair_accent `#7A3FB8`,
  eyes `#D08BFF`, primary `#1C1A22` (End black), secondary `#3B2A4F` (deep purple), accent `#C77DFF` (ender purple),
  trim `#E3E2B4` (end stone), metal `#2A2433` (obsidian), leather `#2E2632`, glow `#E3A8FF` (dragon's breath).
- **Face**: `cat` eyes with slit pupils, a glossy purple iris that glows (the vanilla dragon's eyes), lashes 3, `bold`
  brows, a `soft` mouth with both fangs, no blush; small scales on the cheekbones (markings). Blink `calm`. Mood
  overrides: `happy` is a closed-eye, chin-up smile; `angry` narrows the slits and lights her throat purple.
- **Idle quirks**: she preens a wing, curls her tail round her feet, gazes up at the sky.
- **Phase looks**: phase 2: faint purple veins glow in the wing membranes (glow mask), the horn tips glow. Phase 3:
  her eyes glow, dragon's-breath motes rise from her mouth (`vfx_head`), the dorsal spines glow end-stone pale.
- **Budget**: about 270 cubes (hair 80, skirt 40, wings 40, tail 25), about 125 outside `fine_` bones, a 256x256
  texture (wings and tail), within 4.3's limits.

### 5.3 Kit

**Basic attack**: a claw string (`fists` style, three strikes: rake, rake, a two-handed slash), melee on the ground
only; every third hit adds 3 arcane (`every_nth_hit` 3).

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `ender_fireball` | shot, range 20, projectile `dragon_fireball`, splash radius 2 | light | mid, far | 0.5 (she draws her head back, purple light in her throat) / 0.2 / 0.4 | 4 arcane + `linger`: a cloud of radius 2.5 at the impact for 5 s, 2 arcane each second inside; cooldown 5 s, weight 6; usable in the air (at most 3 light ranged per take-off) | 1-3 |
| `dragon_breath` | cone 6, 70 degrees | area | close, mid | 1.0 (she rears; her throat glows magenta; a fan of ribs grows) / 0.8 / 1.0 | 6 arcane (arcane passes armour and shields) + `linger` at the cone's end: radius 3 for 6 s, 2 arcane each second; cooldown 12 s, weight 5, doubled while perched (`prefer_when` `self_state` `perched`) | 1-3 |
| `wing_gust` | nova radius 5 | area | close | 1.0 (her wings rise high; a disc) / 0.2 / 0.8 | 4 physical + knockback 3 (full strength: off a ledge, into lava or the void past an End island's rim, and aimed there, `HEROES_V2.md` 6.17); cooldown 10 s, weight 3, prefers a close target | 1-3 |
| `void_dive` | dive from the air, range 12, radius 2.5; touch-and-go strings of up to 3 in one window | heavy | mid, far | 1.0 (she folds her wings in the air; the landing disc grows) / 0.4 / 1.2 (on the ground) | 10 physical + knockback 2 (full strength; she lands on the side of her target away from the nearest edge, so the push sends it over, kit-gaps); cooldown 10 s, weight 5 | 1-3 |
| `raise_crystal` | self, `spawn_add` `end_crystal` 1 (5.5) | heavy | any | 1.2 (she lifts a claw; a pillar of purple light climbs where the spire will rise, a disc at its foot) / 0.3 / 0.8 | a crystal on its spire (caged every second one from phase 2); cooldown 20 s, weight 5, doubled when none of hers stands (`prefer_when` `adds_alive` 0); none once her beams have healed all they may in the fight (5.5) | 1-3 |
| `perch` | self, the new `perch` form: lands on high ground (5.6) and enters `perched` | heavy | any | 0.8 (the swoop; a landing disc on the perch) / 0.2 / 0.8 | the `perched` state for up to 8 s; cooldown 18 s, weight 4, doubled when none of her crystals stands (`prefer_when` `adds_alive` 0; vanilla lands more often with fewer crystals) | 1-3 |
| `void_roar` | nova radius 8 | area | close, mid | 1.0 (she throws her head back; a wide ring) / 0.5 / 1.0 | Slowness I 3 s + silence 1.5 s (it stops heroes and casting NPCs; others have no moves to stop); cooldown 20 s, weight 3, doubled while perched (`prefer_when` `self_state` `perched`) | 2-3 |
| `crescent_tail` | sweep 5, 180 degrees, 1 s (she turns first: the half-circle where her target is) | area | close | 1.0 (the tail coils; ribs across the arc) / 1.0 / 0.8 | 6 physical + knockback 2 (full strength, aimed at edges); cooldown 9 s, weight 3 | 3 |
| `end_gravity` | nova radius 7 | area | close, mid | 1.0 (a shulker-like hum, purple motes rising from the ground; a disc) / 0.2 / 0.8 | 3 arcane + Levitation I 3 s (never renewed before the receiver lands, so it lifts about 2.5 blocks and nobody falls far: a kit choice that stays; it is not a push and is not aimed at edges); followup `ender_fireball` 70%; cooldown 22 s, weight 4 | 3 |

**Ultimate `breath_of_the_end`** ("Breath of the End"; class ultimate; 1.6 / 2.5 / 1.5): she rears up (`prefer_when`
`self_state` `perched`: twice as likely while she is perched), a red fan of ribs 10 blocks long across 120 degrees
grows with her line, a wide sound and the darkened screen; then she sweeps a torrent of breath across the arc for
2.5 s: 6 arcane each time it passes over someone, and three clouds of radius 3 settle along the arc, 7 blocks apart,
for 8 s (2 arcane each second inside; the clouds of one use never stack on a receiver, kit-gaps 5). Line:
"Breathe it in. This is what the End tastes like."

**Awakened ultimate `outer_isles_fall`** ("Fall of the Outer Isles"; class ultimate; 1.8 / 0.4 / 1.6): only in an
aerial window. She climbs through the wind-up while a red landing disc of radius 4 grows on her target, then dives:
22 arcane, knockback 3 up 1 (full strength, aimed at edges as Void Dive is) and a cloud of radius 4 for 6 s where she
lands (2 arcane each second inside). Line: "From the outer
isles, I fall!" The two share the energy bar and a 30 s minimum gap.

**Passives**

- **The Spires Answer** (`spire_beam`): the beam of 5.5 (an add behaviour, listed here so the sheet shows it).
- **Wrath of the Spires** (`spire_wrath`): when one of her crystals is broken, whoever broke it is marked for 6 s
  (glowing for everyone) and becomes her preferred target (vanilla's dragon strafes the player who broke a crystal).
  Format: trigger `on_add_died` with its killer as the trigger's enemy, mark with `glow`, and a focus rule for a
  marked enemy (kit-gaps).

**States**

- **`perched`** (5.6): arrows, crossbow bolts, thrown tridents, wind charges and physical hero projectiles glance off
  her and are set alight (vanilla's sitting dragon), from anyone; every hit that still lands on her does +15% (her
  head is low: blades, spells, explosions; the state's `damage_taken` +15); her breath and roar are preferred (x2);
  no take-off until it ends; it ends after 8 s, or once she has taken 15% of her maximum health since landing, and
  she takes off with a `wing_gust` if an enemy is within 5 blocks.
- **`heart_of_the_end`** (phase 3): her clouds are 1 block wider and last 2 s longer; speed +10%.

### 5.4 The boss block

- **Health** (always, against anyone): `stats.health` 34 (the vanguard preset: level for level she has a legendary
  vanguard's health, with her crystals on top), growth `tough`, health scale 400 (the legendary default), the
  vanguard factor 1.0:

| Level | 1 | 10 | 20 |
|---|---|---|---|
| Max health | 136 | 165 | 197 |
| A crystal's beam (per second) | 1.02 | 1.24 | 1.48 |
| All beams in one fight, at most (35%) | 48 | 58 | 69 |

  At level 10 a player with an iron sword needs about 36 s of landed hits while no crystal of hers stands, and at most
  about 48 s if her crystals beam her all they may (35% of her maximum in one fight, 5.5): break the crystals or the
  fight lasts up to a third longer. Six iron soldiers who reach her need about 7 s of landed blows between them (while
  she is on the ground or perched; in the air only arrows reach her).
- **Poise**: 140 (the vanguard's). Max hit share 12%.
- **Phases**:

| Phase | At | What it adds | Transition |
|---|---|---|---|
| 1 | 100% | `ender_fireball`, `dragon_breath`, `wing_gust`, `void_dive`, `raise_crystal` (open crystals, at most 2), `perch`; aerial windows with circling | - |
| 2 | 60% | **the spires rise**: crystals on tall spires, every second one caged, at most 3 standing; `void_roar` | 1.5 s: she lands, wings spread, and roars. "Rise, spires! Let them climb if they dare." |
| 3 | 25% | `crescent_tail`, `end_gravity`, the `heart_of_the_end` state | 1.5 s: her eyes and spines ignite. "Then taste the breath of the End itself." |

- **Reactions** (the same against a player and an NPC):

| When | Answer | Chance, cooldown | Phases |
|---|---|---|---|
| the target draws a bow while she is airborne | `void_dive` (land or dive, the flyers' default) | 60%, 8 s | all |
| a close target | `wing_gust` | 60%, 10 s | all |
| the target is behind her | `crescent_tail` | 70%, 9 s | 3 |
| the target raises a shield | `dragon_breath` (arcane passes shields) | 50%, 10 s | all |
| the target eats | `ender_fireball` | 60%, 10 s | all |
| no crystal of hers stands | `raise_crystal` | 70%, 15 s | all |
| 3 or more enemies engaged | `void_roar` | 50%, 20 s | 2-3 |
| the target is above her | `ender_fireball` | 50%, 8 s | all |

- **Weak points** (3; each hinted):
  - **beam snap** (`focus_add` NEW, add `end_crystal`, only the crystal beaming her at that moment): breaking it hurts
    her 6% of her maximum health (counted as a hit from whoever broke it, under the 12% cap) and adds 40 poise
    (vanilla's 10 damage). Hint: the visible beam; "Every spire is my heartbeat."
  - `blast` (NEW): explosion damage from anyone (TNT, a creeper, an end crystal of vanilla's, a bed or a respawn
    anchor that explodes, firework stars) hurts her 25% more (under the per-hit cap) and adds double poise, because
    players blow the vanilla dragon up with beds. Hint: her dislike of beds and "You brought a BED?" A hero's
    `explosive` move is terrain only and not `#is_explosion` damage (`HEROES_V2.md` 6.18), so it does not count: the
    Wither's blasts never find this weak point.
  - `recovery`: after a dive and after her breath she shakes her head (the punish bonus doubles: +30% during her
    recovery, +45% from behind).
- **Enrage**: soft after 180 s; hard trigger `heart_attacked` (the heart of a base she defends: she guards it as she
  guarded her fountain).
- **Intro** (1.4 s, super armour): she lands with her wings spread, folds them, and tilts her head. "You stand on my
  island. Every island is mine."
- **Bar**: pink asked for (the vanilla dragon's); `boss.bar_color` if the rule picks another (kit-gaps).

### 5.5 The crystals (her central mechanic)

- **What they are**: `end_crystal`, one of her adds (`HeroAdd`, `HEROES_V2.md` 6.11), **not** a vanilla `EndCrystal`:
  it never explodes with power 6, never changes a block, never heals or respawns the vanilla dragon, and never counts
  in `EndDragonFight`. It is drawn with vanilla's end crystal model and texture (a small renderer over its model
  layer), on a **spire**: an obsidian column drawn by the add (`adds/end_spire`, a GeckoLib model whose segments make
  its height) with a bedrock cap, solid for movement like a shulker (nothing walks through it) but not a block
  (nothing mines it or places against it). From phase 2 every second crystal is **caged** in iron bars
  (`adds/crystal_cage`).
- **Where**: within 6-14 blocks of her, on the highest ground there whose foot an ordinary person can reach (F5's
  "who can reach whom"), with room for the spire plus 2 blocks of air; never on lava, water, fire, powder snow or a
  column over the void; at least 4 blocks from another crystal. Inside an enemy base is allowed (she raises spires on
  your walls), but never where a spire would close a way narrower than 3 blocks (a gate, a door, a corridor, a
  1-wide bridge), so a spire never shuts anyone in or out. A spire crumbles when its crystal breaks or fades.
- **Height**: phase 1 a spire of 1 block, the crystal 1 to 3 blocks up: a sword reaches it from the ground, a
  soldier's included (an NPC's swing reaches no higher than its own height, about 2 blocks), and an arrow always.
  Phases 2-3 a spire of 4-6 blocks, the crystal 5-7 up: a shot, or a pillar of 2-4 blocks beside the spire (a soldier
  needs a block or two more than a player), or a hero's leap, climb or flight. These crystals are the one exception to
  `HEROES_V2.md` 6.11's "never more than 3 blocks above the ground" for adds: they never move, and anyone can pillar
  up beside their spire (kit-gaps 6).
- **How many**: at most 2 standing in phase 1 and 3 in phases 2-3; at most 8 raised in one fight; each stands 60 s
  (the add limit) and then fades; `raise_crystal` every 20 s at most.
- **Breaking one**: an open crystal breaks to any hit at all, melee or projectile, a snowball or an egg included (as
  vanilla's); a caged crystal's cage stops every projectile and breaks after 3 melee hits (any damage), then the
  crystal breaks to one more. Her enemies of every kind can break them, a player, a soldier, a hero, even a monster
  that hits one; her allies cannot.
- **The beam** (`spire_beam`): each second the **nearest** of her crystals within 24 blocks with a clear line from its
  core to her body beams her, and she heals 0.75% of her maximum health (one crystal at a time, as vanilla's nearest
  crystal). A purple beam is drawn (vanilla's crystal beam). A block or a solid entity in the line cuts it: building a
  wall between them is fair counterplay. The beam heals her only, never her allies or anyone else. It counts as an
  authored heal (6.2: no regeneration in a fight, authored heals apply). **All her beams together heal her at most 35%
  of her maximum health in one fight** (`per_fight_percent`, PROPOSED: the "up to a third longer" a fight is meant to
  last when her crystals are ignored). Once that is spent, her standing crystals dim (no beam, no beam snap; they still
  shatter when broken) and she raises no more in that fight, so the crystals never make her immortal, whoever fights
  her.
- **Beam snap**: breaking the crystal that is beaming her is her weak point (5.4).
- **Shatter**: any crystal that breaks bursts in purple glass: her enemies within 3 blocks take 4 arcane and knockback
  2, the price of breaking one by hand. The push lands at full strength now (`HEROES_V2.md` 6.17: a passive's push),
  so a climber who breaks a caged crystal from its pillar may be thrown off it (a fall of 5-7 blocks, a few hearts);
  it is not aimed (it bursts outward from the crystal), and a spire never stands over the void. (The vanilla power-6
  explosion would kill most soldiers and crater the ground; this keeps the vanilla lesson, "stand back when it
  breaks", without either.)
- **The shatter breaks no block** (decided in the revision of 2026-11-18). Vanilla's crystal blast is an explosion by
  nature, but the shatter is not a move of hers: her enemies set it off, where and when they break the crystal, at a
  spot she chose, and she raises spires on her enemies' walls. A blast there would turn the fight's one counterplay
  into damage to the breaker's own walls, or teach every soldier to leave crystals standing, and `HEROES_V2.md` 6.18
  keeps blasts to moves with a heavy tell. Beds and vanilla end crystals as the raiders' own explosives against the
  vanilla dragon stay proposals (`docs/design/breaching.md` 13).
- **Wrath of the Spires**: the one who broke it glows and becomes her target for 6 s (5.3).
- **Who answers them** (equal treatment): section 6.2. Soldiers shoot the open ones, climb to the caged ones as a
  player pillars up, and heroes reach them with their mobility; nothing reads whether the breaker is a player.

### 5.6 Mobility: circling and perching

- **Traits**: primary `flyer` tier 3, no secondary. Carrier: `wings`, part `wings/dragon` (worn: folded on the ground,
  spread at the 0.6 s take-off with a heavy flap).
- **Aerial windows**: all three phases (the legendary flyer default, `aerial.phases [1, 2, 3]`), `max_air` 8, `dives`
  3, weight 6 (the most airborne hero of all, inside the same rules): at most 8 s per take-off, at least 6 s on the
  ground between (4 s in phase 3), at most 40% of any 30 s in the air, at most 6 blocks over the ground or the highest
  engaged enemy, at most 4 s out of every enemy's reach unless winding up a telegraphed move. Arrows, bolts, tridents,
  wind charges and Slowness II down her as any flyer (air poise: five arrows).
- **Circling** (new `aerial.pattern: circle`, kit-gaps): in a window she orbits her target at 8-12 blocks, 4-6 up,
  banking, between her light ranged moves (`ender_fireball`, at most 3 per take-off) and her dives (`void_dive`, up
  to 3 touch-and-go), or winds up `outer_isles_fall`. While one of her crystals stands she keeps her orbit inside its
  beam's 24 blocks and sight when she can.
- **Perching** (the new `perch` form, kit-gaps): the perch is the highest standable cell within 12 blocks of her
  target that her engaged enemies can reach or strike (its top at most 1 block above ground an engaged enemy stands on
  or can walk to beside it: a step, a low wall, a rock, a stair top; so a sword reaches her there, a soldier's
  included, since an NPC's swing reaches no higher than its own height), with 2 blocks of headroom and room for her
  folded wings, never on
  lava, fire, farmland or powder snow; with none, the ground beside her nearest crystal. She flies or walks there and
  lands (the move's 0.8 s wind-up is the swoop onto a landing disc). Perched time is ground time for the air budget,
  and the reach rule holds (a perch is always within her enemies' reach).
- **Numbers** (flyer III), level 1 / 10 / 20: stamina 36 / 47.4 / 60 s; cruise 6.5 / 7.2 / 8.0 blocks a second; climb
  2.4 / 3.2 / 4.0; glide sink 3.3 / 2.5 / 2.0 and forward 7.7 / 8.8 / 10.0 blocks a second, 12 / 15.8 / 20 s, 2 gusts;
  take-off at 33 / 25 / 20% stamina; stamina back on the ground 1.8 / 2.4 / 3.0 a second.
- **Out of fights**: she flies her patrols (a flyer guard patrols over its walls), perches on the highest point of her
  base at rest, and walks in a thunderstorm.
- **Raids**: a vanguard leads (`raids: leads`, by its own behaviour, never drafted by the soldier sort). She scouts the
  target first by circling it 8-24 blocks out, crosses the wall at the raid ceiling when she has a way out, and her
  goal inside is to hunt the defending hero (`heroes_first`, a proud noble) or the heart with her party; out of a
  fight she rests on the highest tower in reach, and in one she perches a block above the defenders, by the fight's
  rule, and breathes on the yard. Defending, she meets airborne intruders over her walls.
- **Terrain** (revised 2026-11-18):
  - *No move of hers is an explosion*: the vanilla dragon's fireball leaves a cloud and breaks nothing, its breath is
    a cloud, and its charge and landings break nothing; so are hers (Ender Fireball, Dragon Breath, Breath of the End,
    Void Dive, Fall of the Outer Isles: a dive's impact, which breaks no blocks as vanilla's mace does not).
  - *She does not smash blocks in flight.* The vanilla dragon removes every block its head, neck and body pass through
    unless it is `#dragon_immune` (obsidian, end stone, iron bars...), with no drops (`EnderDragon.checkWalls`). That is
    how a 16-block body that flies fixed paths with no pathfinding gets anywhere. She is a two-block flyer with A11's
    flight planner, who flies round what is in her way, takes off only with 4 blocks of headroom and lands on cells
    she can stand on. Smashing would give her a free breach of every wall she crosses and every pillar or bridge she
    passes, on every flight, with no tell and no limit, which no other hero has and 6.14 and 6.18 forbid to moves.
  - *She breaks blocks only as every hero does*: the dig after 10 s of `HEROES_V2.md` 6.12 to reach a target, with
    her claws counting as an iron pickaxe (no special speed); on a raid she opens the way through the gate from inside
    like any flyer, or with TNT she carries (`docs/design/breaching.md` 5). The vanilla dragon's smashing stays
    vanilla's (ROADMAP phase 3's End raid meets it as players do).
  - *Her pushes* (6.17): Wing Gust (knockback 3), Void Dive (2), Crescent Tail (2), Fall of the Outer Isles (3 up 1)
    and her phase shockwave land at full strength wherever they send an enemy, and she aims them at a cliff, lava or
    the void past an End island's rim: on the ground she walks round to the far side of her target (the tell), and her
    dives land on the side of the target away from the edge (the landing disc shows it). The vanilla dragon's wings
    throw players off the island; hers do too. Her own side is never pushed and her own body never goes into a hazard
    (a flyer, she glides over drops; her landings are never in lava, fire or the void).
  - *Pushed herself*: she flies, so drops do not hurt her (the void stays harmful); lava and fire hurt her in full
    (fire resistance 40); her knockback resistance is the vanguard's 30.

### 5.7 The end of a fight

**She fights to the death.** Nerve = aggression 60 + honour 75 - caution 45 = 90: a sovereign does not flee her own
fight. The file pins `when_losing: last_stand` for the same reason as the Wither's (the vanilla dragon never flees; her
death is how Spirecore changes hands). Her `retreat_below` would be 18 (vanguard 20, then +20 x (0.45 - 0.5) and
-10 x (0.60 - 0.5)), so her last-stand beat comes at 8% (a third of it, raised to the floor of 8). No rebirth. At zero
health she dies like anyone (in the air she falls); her death plays a short burst of white-purple light beams for 3 s
(presentation, the vanilla death's beams) and the bar shows "fallen".

**Her return**: a **dragon egg** (a block display, not a block: nobody can take it) appears beside the reporting station
of her home base when she dies, pulsing faintly; ten minutes later it cracks and she hatches from it, wings unfolding
(`block.end_portal.spawn` and `cues.revived`). The egg is the look of the ordinary 10-minute revival, not a death
ability; her own `revival_rite` talent does not shorten her own return (a talent works where its hero is, and the dead
are nowhere), though another `revival_rite` hero of her faction's would.

### 5.8 Personality

- **Temperament** `proud_noble`; traits a 60, h 75, m 85, c 45, g 70 (+10: a dragon's hoard), s 40, l 60, v 30.
- **Fight AI**: steady (a 60): offensive moves x1.06, zoning and defensive x0.97; focus `heroes_first` (a proud
  temperament); honour 75, so she lets a hero that escapes her go.
- **Likes**: chorus fruit, amethyst shards, end rods. **Dislikes**: beds ("I know what you do with beds."), glass
  bottles ("You came to bottle my breath?").
- **Quirks**: "circles the base at dawn and dusk, counting what is hers"; "sleeps curled round the warmest block in the
  base, her tail over her nose."
- **Relationships**: `rival` the_wither. **Recruit**: `token` only.

### 5.9 Duty, talents and work

- **Duty**: `guard`. A flyer guard patrols over her walls and perches on the highest point; her proficiency answers
  alarms 8 / 12 / 16 blocks farther (instead of a work speed), with the larger backpack and the rest of 10.
- **Talents** (tiers I / II / III at levels 1, 10, 20; at her base; Presence counts them a tier higher at home):
  - `revival_rite` (milestone 2): fallen heroes of her faction return 20 / 30 / 40% sooner (never under 30 s);
    Presence tier IV 50%; personal touch: revived allies rise with 4 / 6 / 8 absorption for 2 minutes. The egg that
    hatches her taught her how.
  - `lookout` (milestone 1): line-of-sight alarm within 32 / 40 / 48 blocks; tier IV 56; personal touch: from 6 or
    more blocks up (perched on a tower, or flying) her sight alarm reaches 16 blocks farther.
  - `cartographer` (milestone 1): the base's scouts reach +16 / 32 / 48 blocks and each report marks the 3x3 sectors
    around it explored: she has seen the land from above.

### 5.10 Voice and lines

- **Voice**: generated, a warm resonant female voice with a low growl under the vowels; fallback blip `chime`, pitch
  0.95, rate 3; hurt `entity.ender_dragon.hurt` (5.11).

| Event | Lines |
|---|---|
| `summoned` (the calling) | "A token, carried this far? Then {faction} has earned a dragon. Do not waste her." / "I have hatched. Bow if you like; I will allow it, {faction}." |
| `greeting` | "Ah, {player}. Come closer. I do not bite. Often." / "{player}. You smell of the green world. Delightful." |
| `idle` | "Your sky is so small. I keep flying into the top of it." / "Everything here is so... warm." |
| `work` | "Nothing moves on my walls without my knowing." |
| `night` | "Finally, a sky with stars in it. The End has none, you know." |
| `small_talk` | "Do you know what you people do with beds in the End? I do." / "Bring me chorus fruit and I may tell you about the void." |
| `fight_start` | "Something dares. How interesting." / "Into the sky, then." |
| `intro` | "You stand on my island. Every island is mine." / "Kneel to the Empress of the End, {enemy}, or fall before her." |
| `raise_crystal` | "Rise." / "Every spire is my heartbeat." |
| `perch` | "Let me look at you properly." |
| `ultimate` (Breath of the End) | "Breathe it in. This is what the End tastes like." |
| `ultimate` (Fall of the Outer Isles) | "From the outer isles, I fall!" |
| `phase_2` | "Rise, spires! Let them climb if they dare." |
| `phase_3` | "Then taste the breath of the End itself." |
| `enrage` | "Enough! I have outlived empires of dust!" |
| `stagger` | "Who taught you to break my crystals?!" / "You brought a BED?" |
| `kill` | "To the void with you." / "Small." |
| `ally_fallen` | "Mine. That one was mine." |
| `victory` | "The sky is mine again." |
| `last_stand` | "Flee? I am the End. There is nowhere past me to run to." |
| `fallen` | "The egg remembers... I will hatch again." / "Keep my perch warm." |
| `revived` | "The shell breaks. Did you miss me?" |
| `raid_start` | "Show me their tallest tower. I will sit on it." |
| `base_attacked` | "They come to MY nest?" |
| `level_up` | "Larger. As is proper." |
| `artifact` | "Shiny. It will join the hoard." / (Spirecore) "My heart's spire. Do not lose it again." |
| `artifact_ask` | (her Spirecore, held by an ally) "That is my heart's spire. Return it, and I shall forget you touched it." |
| `unbound` | "Your kingdom is ash. I will wait in my egg for a worthier hand." |
| `escape` | none: she never escapes |

### 5.11 Sound map (`herosrc/sfx/the_ender_dragon.sfx.json`)

| Moment | Sound | Slot |
|---|---|---|
| Voice | generated `voice` | `voice.talk` |
| Hurt, death | `entity.ender_dragon.hurt` pitch 1.4 volume 0.5; `entity.ender_dragon.death` pitch 1.2 volume 0.6 with generated `death_beams` | `voice.hurt`, `voice.death` |
| Claws | release `entity.player.attack.sweep` pitch 1.1; impact generated `claw_hit` | `moves.basic` |
| Ender Fireball | release `entity.ender_dragon.shoot` pitch 1.2; impact `entity.dragon_fireball.explode` | `moves.ender_fireball` |
| A lingering cloud | generated `breath_cloud` (a quiet hissing loop while it lasts) | new `linger` slot (client loop) |
| Dragon Breath | wind-up `entity.ender_dragon.growl` pitch 1.3 volume 0.6 with generated `breath_inhale`; release generated `breath_roar`; impact generated `breath_sizzle` (quiet) | `moves.dragon_breath` |
| Wing Gust | wind-up `entity.ender_dragon.flap` pitch 0.9 with generated `wing_rise`; impact `entity.wind_charge.wind_burst` pitch 0.8 | `moves.wing_gust` |
| Void Dive | wind-up generated `dive_scream` with `cues.telegraph.dive`; impact `entity.generic.explode` pitch 1.4 volume 0.4 with `cues.landing` | `moves.void_dive` |
| Raise Crystal | wind-up generated `crystal_call` with `block.end_portal_frame.fill`; release `block.beacon.activate` pitch 1.6 | `moves.raise_crystal` |
| A crystal standing; its beam | generated `crystal_hum` (a loop at the crystal); generated `crystal_beam` (a loop while it beams) | add sounds (new) |
| A cage hit, broken | `block.chain.hit`; `block.metal.break` with `block.chain.break` | add sounds |
| A crystal shatters; beam snap | `block.glass.break` pitch 0.8 with `entity.generic.explode` pitch 1.5 volume 0.5 and generated `crystal_shatter`; the snap adds `block.respawn_anchor.deplete` pitch 1.2 | add sounds, `weak_points.focus_add` |
| Perch | `entity.ender_dragon.flap` pitch 0.8 with `cues.landing`; arrows glancing off: `item.shield.block` pitch 1.4 volume 0.5 | `moves.perch`, `states.perched` |
| Void Roar | wind-up `entity.ender_dragon.growl` pitch 0.9 with generated `roar_swell` | `moves.void_roar` |
| Crescent Tail | release `entity.player.attack.sweep` pitch 0.7; impact `entity.player.attack.knockback` | `moves.crescent_tail` |
| End Gravity | wind-up `entity.shulker.shoot` pitch 0.8 with generated `gravity_hum`; impact `entity.shulker_bullet.hit` | `moves.end_gravity` |
| Breath of the End | wind-up `entity.ender_dragon.growl` pitch 0.8 with generated `ultimate_inhale` (wide); loop generated `great_breath`; release `entity.ender_dragon.shoot` pitch 0.7 | `moves.breath_of_the_end` |
| Fall of the Outer Isles | wind-up generated `skyfall_rise`; release `entity.ender_dragon.growl`; impact `entity.generic.explode` with `entity.dragon_fireball.explode` | `moves.outer_isles_fall` |
| Phase 2, phase 3 | `entity.ender_dragon.growl` with generated `spires_rise`; generated `heart_of_end` with `entity.ender_dragon.ambient` | `phases.2`, `phases.3` |
| Take-off, wings | `entity.ender_dragon.flap` pitch 1.2 with `cues.takeoff`; her wing loop in flight `entity.ender_dragon.flap` pitch 1.3 volume 0.5 per beat | `mobility.takeoff`, `mobility.wings` (new) |
| Weak point blast | `cues.weak_point` with generated `scales_crack` | `weak_points.blast` |
| Intro | `entity.ender_dragon.growl` pitch 1.1 with generated `intro` | `intro` |
| Idles | `entity.ender_dragon.ambient` pitch 1.5 volume 0.2 (a purr); a wing preen `entity.ender_dragon.flap` pitch 1.6 volume 0.3 | `quirks.*` |
| The calling; the egg; the revival | generated `calling_egg` (10 s of converging hums) and an egg crack, then `entity.ender_dragon.growl`; the egg's settle generated `egg_settle`; the hatch `block.end_portal.spawn` pitch 1.5 volume 0.6 with `cues.revived` | new `calling` slot |
| Steps | vanilla steps with `cue:step.heavy` volume 0.7 | `steps` |

### 5.12 Own animations

`move.ender_fireball` (ground and air versions by `DATA_LOCO`), `move.dragon_breath`, `move.wing_gust`,
`move.void_dive`, `move.raise_crystal`, `move.perch` (the swoop and landing) and `state.perched` (head low, wings
half open), `move.void_roar`, `move.crescent_tail`, `move.end_gravity`, `ultimate` (+ `ultimate.loop`, the sweep),
`ultimate.awakened`, `phase.2`, `phase.3`, `intro`, `victory` (a slow wing stretch), `taunt` (a beckoning claw),
`last_stand`, `personal.1` (preens a wing), `personal.2` (curls her tail), `personal.3` (gazes at the sky), and
overrides of the core flight clips: a heavier take-off push, a banking circle glide (`move.circle`, new) and a
landing with the wings flared.

### 5.13 Her artifact: Spirecore (signature; revised 2026-11-18)

A palm-sized end crystal caged in obsidian and iron bars, worn on a chain; it turns slowly and hums. It cannot be
crafted; it comes with her the first time she joins a faction and drops where she dies. One instance per world. **A
tinkerer can upgrade it**: it is a built thing, a crystal in a cage, as the vanilla crystals stand caged on their
pillars, so an artificer can re-cage and re-tune it at the Artificer's Bench, with the research a rare find's upgrade
needs (the owner's "some npcs or hero with can tinker with artefacts may upgrade the artifact" and "some artifacts may
nees tech level to be built and/or upgraded").

```jsonc
// data/factionclash/artifacts/spirecore.json (ARTIFACTS.md 16.1, 16.4 and 9.8)
{
  "format": 2, "id": "spirecore", "name": "Spirecore", "tier": "signature", "element": "arcane",
  "lore": "Every spire is a heartbeat. This one is hers.",
  "stats": { "health": 10, "ability_power": 6, "energy": 8, "stamina": 10 },            // 2 + 1 + 1 + 1 = 5 points
  "upgrade": { "material": "minecraft:end_crystal", "counts": [1, 2] },    // NEW on a signature (kit-gaps)
  "tech": { "upgrade_2": "artifice_2", "upgrade_3": "artifice_2" },       // as a rare find's (ARTIFACTS.md 9.5)
  "passive": {
    "name": "Spire Mend", "unique": "spire",
    "description": "Once per fight, when it drops under 40% health: a shield of 4 for 8 s and Regeneration I for 6 s.",
    "parts": [ { "trigger": "on_low_health", "trigger_value": 40, "once_per_fight": true, "form": "self",
                 "effects": [ { "type": "shield", "amount": 4, "seconds": 8 },
                              { "type": "effect", "id": "regeneration", "level": 1, "seconds": 6 } ] } ]
  },
  "suits": ["vanguard", "flyers", "ultimate"], "source": "signature",
  "signature": {
    "hero": "the_ender_dragon", "reforms": true, "worn_part": "accessories/spirecore_pendant",
    "bond": {
      "stats": { "health": 5, "recharge": 5, "ability_power": 6 },                       // +3 points, her own only
      "passive": { "name": "Heart of the Spires", "unique": "spire",
        "description": "Spire Mend, and her running move cooldowns are cut by 10 s (never under half), so her next crystal comes sooner.",
        "parts": [ { "trigger": "on_low_health", "trigger_value": 40, "once_per_fight": true, "form": "self",
                     "effects": [ { "type": "shield", "amount": 4, "seconds": 8 },
                                  { "type": "effect", "id": "regeneration", "level": 1, "seconds": 6 },
                                  { "type": "cooldown_cut", "seconds": 10 } ] } ] },
      "kit_mods": [
        { "move": "raise_crystal", "cooldown_percent": -20 },                            // 20 s -> 16 s
        { "move": "ender_fireball", "radius": 1 }                                        // splash 2 -> 3 blocks
      ]
    }
  },
  "sounds": { "proc": "minecraft:block.respawn_anchor.charge" }
}
```

- **Fixed strength** (`ARTIFACTS.md` 4.2): the same numbers on any hero at any level (the first design's attunement
  is gone). **Any other hero**: health +10%, ability power +6%, energy gain +8%, stamina +10% and Spire Mend: best on
  durable flyers and ultimate-led kits.
- **The Ender Dragon herself**: all of that plus her bond, health +5%, recharge +5%, ability power +6% (within the
  build caps), Heart of the Spires, crystals every 16 s and wider fireball splashes. Its heals count under the artifact
  healing ceiling (10% of her maximum per 30 s); a faster Raise Crystal does not raise her beams' per-fight cap (5.5).
- **Upgrades** (`ARTIFACTS.md` 9.8, at the price of a rare find's, at the Artificer's Bench, by an artificer, a hero
  at the artificer duty or a player, never failing):

| Level | Lines (health, ability power, energy gain, stamina) | Passive | Cost | Research |
|---|---|---|---|---|
| I (as she brings it) | +10%, +6%, +8%, +10% | Spire Mend: shield 4 | - | - |
| II | +13%, +8%, +10%, +13% | the same | 4 lapis lazuli, 1 diamond, 1 end crystal; 10 levels; 120 s | Artifice 2 |
| III | +15%, +9%, +12%, +15% | tuned: its shield 4.8 (Spire Mend has no cooldown, so its Amounts grow a fifth) | 4 lapis lazuli, 1 netherite scrap, 2 end crystals; 15 levels; 180 s | Artifice 2 |

  An upgrade multiplies the base lines only, rounded half up and within the build caps; the bond's lines and kit mods
  are hers and stay as they are, and tuning at III tunes whichever passive is in use (her Heart of the Spires' shield
  is 4.8 too; the cooldown cut and the Regeneration are not Amounts and stay). The end crystal is vanilla's (7 glass, an
  eye of ender and a ghast tear: a faction gets one after its Nether expeditions). Until ROADMAP phase 6 adds the
  Artifice research (`heroes.artifacts.techGate` research, the default) it stays at I; `techGate = open` lets an
  operator try it sooner. The upgrade level is on the item: it stays through her death, a theft and her token, and a
  re-formed Spirecore starts again at I. Its name shows the level ("Spirecore II"); the icon and the pendant on her
  body do not change.
- **Never grows**: a crystal stays what it is; the dragon's growth in a fight is her spires, and one way each keeps
  the two signatures within a step of each other and of the strongest crafted artifacts (Hollow Star 8 points full,
  Spirecore 7.5 at III, a greater artifact 7.5 at III). The validator refuses a `growth` on a signature that upgrades.
- **War rating** 0.12, plus 0.02 an upgrade level: 0.16 at III (`ARTIFACTS.md` 13).
- **Her choice**: as Hollow Star's (4.13): she always wears it and never gives it up, an ally who holds it holds it as
  lent by her, she claims it first from the store, and an enemy hero she sees wearing it is her preferred target. To be
  upgraded it leaves her slot for the bench's time: the one time she takes it off, at her own wish (a proud noble's
  ambition, 85, wishes upgrades of what she wears; `ARTIFACTS.md` 10.7 and 15.5).

### 5.14 The hero file (abridged sketch)

```jsonc
{
  "format": 2, "id": "the_ender_dragon", "name": "The Ender Dragon", "title": "Empress of the End",
  "gender": "female", "archetype": "ender_dragon", "hybrid": "west_dragon", "rarity": "legendary", "element": "arcane",
  "resistances": { "arcane": 40, "fire": 40 },
  "stats": { "health": 34 },
  "behaviour": { "combat_role": "vanguard", "duel_style": "summoner", "duty": "guard", "raids": "leads" },
  "basic_attack": { "style": "melee", "combo": 3,
                    "every_nth_hit": { "n": 3, "effects": [ { "type": "damage", "amount": 3, "element": "arcane" } ] } },
  "moves": [ /* the nine of 5.3; ender_fireball and dragon_breath carry the NEW linger effect; perch the NEW perch form */ ],
  "ultimate": { "id": "breath_of_the_end", /* ... */ "awakened": { "id": "outer_isles_fall" /* ... */ } },
  "passives": [ /* spire_wrath: on_add_died with the killer as trigger_enemy (NEW) */ ],
  "states": [ { "name": "perched", "projectile_immune": true, "stats": { "damage_taken": 15 } },   // NEW field
              { "name": "heart_of_the_end", "stats": { "speed": 10 } } ],
  "boss": {
    "health_scale": 400, "poise": 140,
    "adds": [ { "id": "end_crystal", "count": 1 } ],                                      // NEW add kind (beam_heal)
    "phases": [ { "at": 100 }, { "at": 60, "moves": ["void_roar"] },
                { "at": 25, "moves": ["crescent_tail", "end_gravity"], "states": ["heart_of_the_end"] } ],
    "weak_points": [ { "kind": "focus_add", "add": "end_crystal", "only": "beaming",          // NEW
                       "damage_percent": 6, "poise": 40 }, "blast", "recovery" ],             // blast NEW
    "enrage": { "after": 180, "triggers": ["heart_attacked"] }, "bar_color": "pink"
  },
  "personality": { "temperament": "proud_noble", "when_losing": "last_stand",
                   "traits": { "a": 60, "h": 75, "m": 85, "c": 45, "g": 70, "s": 40, "l": 60, "v": 30 } },
  "leader": { "eligible": false },
  "talents": [ { "id": "revival_rite" }, { "id": "lookout" }, { "id": "cartographer" } ],
  "recruit": { "condition": "token" },
  "relationships": [ { "hero": "the_wither", "kind": "rival" } ],
  "mobility": { "primary": { "trait": "flyer", "tier": 3 }, "carrier": { "kind": "wings", "part": "wings" },
                "aerial": { "phases": [1, 2, 3], "max_air": 8, "dives": 3, "weight": 6,
                            "pattern": "circle", "radius": 10 } },                                // NEW pattern
  "voice": { "blip": "chime", "pitch": 0.95, "rate": 3 },
  "art": { "form": "adult", "build": "standard", "scale": 108 /* parts, palette and face of 5.2 */ }
}
```

---

## 6. Equal treatment, fairness, and how NPCs answer these kits

### 6.1 Nothing reads whether anyone is a player

Every rule of sections 3-5 is written for "a member", "an enemy", "a carrier" or "whoever broke it": a token joins
the faction of whoever holds it inside its base, player or NPC; a carrier is a first target whoever carries it; the
Wither's armour turns a player's arrows and an NPC archer's alike; a perched dragon shrugs off both; any enemy breaks a
crystal and pays the same shatter; the beam snap, the broken crown, `smite` and `blast` count the same from anyone;
Void Roar's silence stops heroes and casting NPCs, and a player and an ordinary soldier, who have no moves, lose
nothing to it (as `HEROES_V2.md` 6.6 says). Terrain is the same for everyone too: their pushes send a player, a soldier
and a hero over the same edge, the Wither's blasts break a player's house and a faction's hall alike, and an NPC
holder of her signature is asked for it back by the same rule as a hero. Only presentation is for players: the chest's
particle column, the titles,
the token's glow and trail, the boss bars, decals, sounds and lines. The test of 15.6 (no method of the pure logic
takes "is a player" as an input) covers the new logic too (9.1).

### 6.2 Crystals: what soldiers and heroes do (`CrystalTargetGoal`, shared with the vanilla End raid)

The owner's wish of 2026-11-13 ("in the fight with the ender dragon, they should know how to destroy the crystals")
becomes one goal that serves both her crystals and vanilla's (ROADMAP phase 3's End raid):

- **Who**: ordinary soldiers of a side fighting her (or the vanilla dragon), within 32 blocks of a crystal.
- **Archers and crossbowmen**: an open crystal in sight and in range is their first target, the one beaming her
  before the others, then an airborne hero, then the rest (above `AirborneHeroTargetGoal` of `MOBILITY_AND_WORK.md`
  5.2); at most two shoot at one crystal; a cage stops their shots, so they leave caged crystals to the climbers.
- **Swordsmen**: an open crystal they can strike from where they stand is struck at once; a caged or high one gets
  one or two swordsmen (the nearest free) who pillar up beside its spire with `DigOut`'s pillar, as a player does
  (real blocks from their backpacks, at a player's pace), break the cage (3 hits) and the crystal, then come down.
  The rest keep fighting her.
- **Heroes**: a hero whose enemy has crystals treats the beaming crystal as a target in its band: its basic attacks
  and any move that can hit it may be aimed there; a leaper, climber, acrobat or flyer reaches a caged one at once
  (the reach of `MOBILITY_AND_WORK.md` 3.7), others pillar as soldiers do after `HEROES_V2.md` 6.12's 10 s.
- **Her side** does not guard crystals beyond fighting whoever comes near.
- Against the vanilla dragon the same goal reads vanilla `EndCrystal`s and their iron-bar cages: archers on the open
  pillars, climbers on the caged ones (pillaring up 76-103 blocks is a long climb: the End raid's planner sends its
  best builders, as a player would).

### 6.3 Projectile immunity (the Wither's armour, the perched dragon)

- **Ranged NPCs** do not waste shots on a target whose state is projectile-immune: they take another enemy in range
  (her familiars and crystals first, then soldiers); with none, they draw their **sidearm** and fight in melee as a
  swordsman does until the state ends or another target appears. Archers and crossbowmen carry no sidearm today, so
  this design adds one (an ordinary-NPC rule and player-like: a player switches to a sword): every ranged soldier
  keeps a sword in its backpack, the best the faction's stores give (the blacksmith's weapon orders, a stone sword at
  worst), and swaps to it as a player switches hotbar slots. Without it an archers-only side could never hurt the
  armoured Wither from 50% down; ROADMAP phase 3's raid on the vanilla Wither, arrow-proof below half health, needs it
  too.
- **Heroes**: the move selector scores 0 a move whose damage is only physical projectiles (a physical `shot`,
  `volley` or `barrage`) against a projectile-immune target, as it does a move with no valid target; element
  projectiles fire as usual.
- **Players** hear and see the glance (a shield-block ping and a blue or purple spark) and the kit sheets say it.
- **Never unreachable**: neither hero is projectile-immune and out of melee reach at once. The Wither in armour fights
  close and takes no aerial windows (a reach flight still takes air poise from arrows); the perched dragon's perch is
  always within her enemies' reach, a soldier's swing included (a block up at most, 5.6).

### 6.4 The fairness rules of `HEROES_V2.md` 6.14, checked

Checked again with the revision, against 6.14 as revised (rule 5 is now "terrain is the same for every receiver") and
the explosive rules of 6.18:

| Rule | The Wither | The Ender Dragon |
|---|---|---|
| 1. Telegraph minimums; no unblockable move without a mark | every frame in 4.3 at or above its class; nothing unblockable; each blast has its decal (Ruin Skull's strip and disc, a disc per Stray Skull, Birth Blast's red disc, which holds its crater) | every frame in 5.3 at or above its class; nothing unblockable |
| 2. Control the same for everyone, stuns heavy and capped | no stun: the Wither effect and knockbacks only | no stun; Slowness I, silence 1.5 s (heroes), Levitation I 3 s that never stacks into fall damage (not a push, not aimed) |
| 3. Caps both ways | the 12% cap per hit on her, the slot caps on her moves, Birth Blast 24 at the 30 cap; blasts hurt nobody (6.18), so no cap moves | the same; beam snap 6% sits under the cap; the crystal heal is an authored heal |
| 4. No limit on gangs; hurt cooldown per attacker | unchanged | unchanged |
| 5. Terrain is the same for every receiver (6.17) | Withering Pulse, Ruin Skull, Soul Dash, Birth Blast and the phase shockwave at full strength, aimed at edges after the walk round (the tell); never her own side; her own body never into a hazard | Wing Gust, Void Dive, Crescent Tail, Fall of the Outer Isles, the phase shockwave and the shatter at full strength, the void past an island's rim included; dives land away from the edge, in their disc; never her own side |
| 6. Creative and spectator players never enemies | and never carriers (3.4) | same |
| 7. Mobility counterplay | air windows in phase 1 only, the air budget, hover within melee reach | air budget, reach clock, perch within reach, air poise (five arrows down her) |
| 6.18 Explosive moves | Ruin Skull (heavy) and Stray Skulls (area): power 1, the blue skull's rule; Birth Blast (ultimate): power 4; six blasts per use at most; never a light move, a basic attack or a charge; scored 0 near her own side's blocks | none: no move of hers is an explosion |
| Base safety | no blast near her own side (6.18); her body breaks out only when boxed, never her own side's blocks; her hands never break a faction's planned walls (4.5) | no move touches blocks; no smashing in flight; crystals and spires are entities, and the shatter breaks nothing (5.5, 5.6) |

### 6.5 Why the two kits keep the vanilla lessons

A player who has beaten the vanilla bosses already knows how to beat these two: fight the Wither close and in a
tunnel once she is armoured (arrows fail), bring Smite, keep out of the blast's 7 blocks or break its charge, and do not
trust a box or an obsidian wall to hold her (only bedrock-class blocks stop her blue skulls); break the dragon's
crystals first, climb to the caged ones, hit her when she perches (arrows fail then), keep beds in mind, and keep away
from the island's edge when her wings rise. The soldiers know it too (6.2, 6.3, and the edge sense of
`docs/design/breaching.md` 8).

### 6.6 The vanilla Ender Dragon and NPC damage (a note for ROADMAP phase 3)

In 1.21.1, `EnderDragon.hurt(part, source, amount)` applies damage only when the source's entity is a `Player` or the
damage type is in `#minecraft:always_hurts_ender_dragons` (explosions). A faction soldier's sword (`mob_attack`) or
arrow (`arrow`) does nothing at all, so an NPC End raid could never kill the dragon and the first-kill chest could only
ever come from a player. The fix belongs to the boss-raid row of ROADMAP phase 3 (and is listed as B4's prerequisite
check, section 10): a mod damage type `factionclash:faction_strike` (and `faction_arrow`) tagged into
`#always_hurts_ender_dragons`, used for every faction NPC's and hero's hit on an `EnderDragon` or an
`EnderDragonPart`, so the vanilla rules then treat a soldier like a player (its other rules stay: a quarter + 1 on the
body, arrows set alight while it sits). No mixin: `EnderDragon.hurt(part, source, amount)` drops other damage before
any event fires, so the source itself must carry the tag. Melee: `FactionNpc.doHurtTarget` (and a hero's blow) builds
the `faction_strike` source when its target is the dragon or one of its parts. Arrows: NeoForge's
`ProjectileImpactEvent` on a faction NPC's or hero's arrow that hits the dragon or a part cancels the impact and deals
the arrow's damage again with `faction_arrow`, then removes the arrow as a hit arrow is removed (a cancelled impact
alone would let it fly on). The vanilla dragon also picks only players as targets in its phases; whether
it should attack NPC raiders too is that row's design.

---

## 7. Balance on paper (PROPOSED; measured in the campaigns of 9.3)

The model and reference rates are `HEROES_V2.md` 6.2, 6.16 and 8.1's (iron gear, level 10).

| Case | On paper | Verdict |
|---|---|---|
| 1. A player in iron against the Wither | 169 health and a 51-health rebirth: about 37 s + 11 s of landed iron-sword hits, a duel of 100-150 s with her wind-ups dodged, under her 180 s enrage. Birth Blast at the cap (24 blight, about 21 through iron armour, plus Wither II for 5 s) kills an iron-armoured player from full health, after a 1.6 s wind-up and a 3 s breakable channel with a radius-7 red disc: 4.6 s of warning, the longest of any ultimate. With Hollow Star worn (fixed strength now: health +10% with her bond, +15% once it has grown full) she has 186-194 and the hits take about a tenth longer; ability power and blight damage raise Birth Blast to about 28, still under the 30 cap. | Hard, legendary, readable. No change. |
| 2. The Wither against six iron soldiers and two archers | Phase 1 in the air (8 s windows): the archers down her in about 4 s of each window (air poise), the swordsmen reach her on the ground (she floats within reach); her familiars hit two extra soldiers. From phase 2 arrows glance off her: the archers shoot her familiars or draw their sidearms (6.3); six swords land about 22 a second: about 7 s of blows, 10 s with the rebirth. She takes three or four soldiers with her. | Like an epic leader in an NPC war (8.1), with a sharper archer-proof middle. Her war power (12.0 at level 10; 13.4 with Hollow Star at its flat 0.12, 14.0 once it has grown full) does not count the rebirth: the campaigns check whether she decides raids. |
| 3. Archers only against the Wither (a base's defenders, or a party) | From 50% her armour turns every arrow. The archers draw their sidearms (6.3) and fight her close as swordsmen with poorer swords (stone deals about a sixth less than iron); her broken crown and Smite count for them as for anyone. | Fixed by the review: before it, an archers-only side could not hurt her from 50% down at all. The director's ranged share (0.40 neutral, at most about 0.5) keeps swordsmen in any case; a player switches to a sword. |
| 4. A player in iron against the Ender Dragon | 165 health; about 36 s of landed hits with no crystal standing, at most about 48 s however long her crystals beam her (35% of her maximum per fight). Open crystals break to one arrow; a caged one (phase 2) costs about 6 s (2-4 blocks of pillar, 3 cage hits, one more), and she raises one every 20 s at most: a player who keeps ahead of them spends about a quarter of the fight on crystals. | The fight is about the crystals, as asked. No change. |
| 5. Can the crystals out-heal anyone? | One beam at a time: 0.75% a second (1.24 at level 10) against a lone iron-sword player's 4.6 landed a second, or six soldiers' 22, and all beams together at most 35% of her maximum in one fight (58 at level 10); then she raises no more. A player who ignores the crystals and can hit her only on the ground still wins; the enrage at 180 s ends a stall. | Fixed by the review: before the cap, a weak attacker who ignored the crystals could be out-healed for as long as she kept raising them (8 crystals of 60 s). Never a stalemate. |
| 6. The Ender Dragon in an NPC raid | Five iron soldiers and two archers: the archers break open crystals first (the beaming one first) and down her when she circles; the swordsmen hit her when she perches (+15%, a block up at most) and climb to caged crystals. | Like a vanguard leader with a heal the soldiers learn to stop. No change. |
| 7. Their signature artifacts on other heroes | Five points and a major passive, as a greater artifact, the same on any hero at any level; no bond. War rating 0.12. Grown full, Hollow Star is 8 points (0.165); Spirecore at III, with Artifice 2 research, is 7.5 points (0.16); a greater artifact at III is 7.5. | The two strongest single artifacts, one of each per world, at most half a point over a crafted greater artifact at III, and the build caps bind them like any (Hollow Star's 18% ability power leaves 2% of the 20% cap). In line with `ARTIFACTS.md` 14. |
| 8. The token race | An expedition (3-5 soldiers and a hero) against an interception party is a small raid: the raid maths of 8.1 apply, the soldier backbone included. A factionless player carrier is hunted by every faction that sees the trail. | As meant: the token is worth a fight. |
| 9. One faction holding both | Two legendary heroes' war power (about 24 at level 10 bare, about 27 in their signatures) under the soldier backbone cap (at most 1.5 times its soldiers' power); both return to tokens only with its last base. Rivals rate it 25% more attractive when they could end it. | A strong faction, not an unbeatable one. Measured in 9.3. |
| 10. The Wither's blasts against a base (revision) | Raiding, Ruin Skull bites one to three blocks out of whatever it hits every 14 s at most; two or three open a 1-wide, 2-high gap in a 1-thick wall of anything but bedrock-class blocks, about 30 s at the breach spot, where one TNT opens about 3 x 3 in stone bricks in 4 s but never obsidian. Birth Blast craters about 3 blocks of dirt or 1-2 of stone round her, inside its disc, once or twice a fight; Stray Skulls six small bites in the rebirth. The defenders' repair scan rebuilds planned blocks after the alarm. | A slow breacher who opens what TNT cannot; most of her damage to a base is a few craters in the yard. Equal for a player's house. The campaigns count blocks broken per raid (9.3). |
| 11. The Wither defending her own base (revision) | Inside her own walls the pick scores her three explosive moves 0 (6.18): no Ruin Skull, no Stray Skulls, no Birth Blast. She keeps her basic skulls, Skull Volley, Withering Pulse, Rose Bloom, Soul Dash, Grave Grasp, Unbound Heads, Skullfall, her armour, rebirth and passives; her answers to eating and to crowds (Ruin Skull) fall back to her ordinary pick. Her damage over a fight drops by about a sixth; her health, armour and rebirth are unchanged. | Weaker at home than abroad, the price of not blowing up her own walls, as a player would not. Acceptable: her home strength is `intimidation` and her durability. |
| 12. The Ender Dragon at an edge (revision) | Wing Gust throws everyone within 5 blocks about 6 blocks on flat ground (knockback 3: vanilla's knockback of 1.2, about 2 blocks a point; a vanguard's 30 or netherite armour's 40 knockback resistance cuts it to about 4), at full strength: beside a cliff, lava or an End island's rim it is lethal, and she walks round her target to aim it (the walk is the tell, at most 3 s). An iron soldier with the edge sense (`docs/design/breaching.md` 8) keeps the drop off its back while she is near; a player keeps away from edges or carries a water bucket; a flyer or glider hero shrugs off drops. On the vanilla island her own end crystals are not there (hers are her adds), and a spire never stands over the void. | The vanilla dragon's own lesson, now true: fight her away from the rim. Kills there are hers ("was doomed to fall by"). No change to her numbers. |
| 13. Their signatures grown or upgraded, on themselves (revision) | The Wither in a full Hollow Star at level 10: 194 health, ability power +18%, drain at the 6% cap, blight damage +6%. The dragon in Spirecore III: 198 health (the 20% cap), ability power +15%, energy gain +12%, recharge +5%. Each is about 3-5% more effective health and damage than at step 0 or upgrade I. | Small steps by design: their bonds already gave them most of it, and the caps bind. As meant. |

---

## 8. New format pieces these kits need

Each is a precise entry in `notes/kit-gaps.md`, section "Boss heroes (2026-11-18)", for the format tasks (B1, B3):

1. `mobility.secondary: hover` on a flyer (1.14 refuses it today).
2. Projectile looks `wither_skull`, `wither_skull_blue`, `dragon_fireball` (vanilla renderers reused), and a shot's
   `speed` (the blue skull flies at 60%).
3. A thrown basic attack from the air in an aerial window (5.1.5 is silent on basic attacks).
4. State fields `projectile_immune` and `keep_distance`.
5. The effect `linger` (a lingering cloud left where a move lands); the clouds of one use never stack on a receiver.
6. Add kinds `end_crystal` (behaviour `beam_heal` with its per-fight cap, spire, cage, shatter, a spire never in a way
   narrower than 3 blocks, the crystals' exception to 6.11's 3-block height) and `skull_familiar` (orbiter and
   turret); phase `adds` that are sent out at the transition.
7. Weak points `focus_add`, `smite`, `blast`.
8. `boss.rebirth` as an object (its own moves and transition) and an action code `rebirth`; no earlier phase re-arms
   after it.
9. The `perch` form, its top at most 1 block above an engaged enemy's ground.
10. `mobility.aerial.pattern: circle` with a radius.
11. `immune_effects`.
12. The passive form `shot_random`; `on_add_died` naming its killer as the trigger's enemy; a focus rule for a marked
    enemy.
13. `boss.bar_color`.
14. The archetype group E (`wither`, `ender_dragon`), `leader.eligible: false` with no other leader fields,
    `recruit.condition: token`, and the line events `rebirth` and `unbound`. (The first design's raid goal `breach`
    for one named hero is gone with the revision: she breaches with 6.18's explosive moves, 4.5.)
15. A `rain` centred on the hero (`centre: self`), for Stray Skulls.
16. Not a format piece: ranged soldiers carry a sidearm (6.3), an ordinary-NPC rule for B4 and ROADMAP phase 3.

From the revision of 2026-11-18 (`notes/kit-gaps.md`, "Boss heroes revision"):

17. `explosive.resistance_cap` (0.1-1.0): the blue skull's rule, every block but air and `#wither_immune` counted as
    no stronger than it; allowed only at power 1 on a legendary (otherwise left out with a reader message).
18. `behaviour.break_out: box`: vanilla's Wither rule when walled in (4.5), only on a hero whose file names it, a
    NOTE on a non-legendary; and `work.tool` (the hands' `BlockWork` tool: `diamond_pickaxe` for her, `iron_pickaxe`
    for the dragon's claws).
19. A signature's `growth` (Hollow Star, the new counter `withering`) or `upgrade` with `tech` (Spirecore), never
    both; the bond's lines and kit mods never multiplied by an upgrade; a kit mod never changes a move's `explosive`.
20. A signature's standing lent mark to its own hero, and a focus rule for an enemy hero wearing her own signature.
21. The aim of a `dive` (and a `leap`) at an edge: the landing point on the side of the target away from the edge,
    within the landing disc (6.17 names the aim of knockbacks, charges, grabs and pulls, not of landings).

Move weights that change with a state or a situation (the Wither's volley in phase 1, Grave Grasp while unbound, the
dragon's breath, roar and ultimate while perched, her crystal and perch when none stands) are written with the
existing `prefer_when` (x2) and the conditions `phase`, `self_state` and `adds_alive`: no new field.

---

## 9. Tests to write later

### 9.1 Unit (pure, `src/test/java/com/factionclash/logic/`)

- `ChestSpotTest`: a column with plain ground, a canopy, water 1 and 3 deep, lava, fire, a cactus neighbour, the
  Nether roof (a death above it), the void under the death point, the End podium's rings (the eastern start, the
  portal rim skipped, a ledge refused), a custom End without the island.
- `BossTokenRulesTest`: the state machine of 2.2; consumed by a member holding it inside a standing base, not by a
  non-member, not in a pact ally's base for the holder (it lies there and joins that base's faction after 5 s); a lying
  token after 5 s; a fallen heart stops it; hollow copies, a same-instance copy hollow after the first use; the return
  timer; lava for 30 s; unbinding keeps the level; creative holders drop it.
- `CrystalRulesTest`: only the nearest crystal in sight beams; the 24-block range; the heal rate; 2 / 3 / 8 caps;
  placement within reach and never over the void or in a way narrower than 3 blocks; cage hits; the snap only for the
  beaming crystal; the per-fight cap of 35% and no crystal after it; the spire gone with its crystal.
- `PerchSpotTest`: the highest cell within 12 blocks whose top is at most 1 block above an engaged enemy's ground,
  with headroom, never lava, fire, farmland or powder snow; else beside her nearest crystal.
- `BossKitsTest`: both kits load clean as fixtures with the new pieces; every frame at or above its class; the
  rebirth object with no re-arm after it; the add limits; Stray Skulls at the rain cap (6 of 4); no stun in either
  kit; a projectile-immune state never with an aerial window; her blasts within 6.18 (Ruin Skull and Stray Skulls power
  1 with `resistance_cap` 0.8, Birth Blast power 4, six blasts per use at most, none on a light move, the basic attack
  or a charge; `resistance_cap` left out on a non-legendary or above power 1); no `explosive` in the dragon's kit;
  `break_out: box` read for the Wither only.
- `WitherBlocksTest` (pure, over a block reader): the blue skull's expected bite (obsidian yes, bedrock and reinforced
  deepslate never); Birth Blast's reach inside its 7-block disc; the box of the trap break-out (from her feet up, one
  out, never below); "walled in" only when every side cell is blocked and no path is found for 1 s; never a planned
  block of her own side or an ally; the own-side score of 0 for each explosive move.
- `SignatureChoiceTest` (with `ArtifactChoiceTest`): her signature always worn first and never passed on, swapped or
  given to an ask; held by an ally it counts as lent by her (an honour-50 holder returns it at her ask, a greedy one
  weighs it); an enemy hero wearing it is her preferred target; Hollow Star's `withering` count (only Wither damage
  her own effects gave, never her adds'), its steps at 60 / 180 / 360 and its cap of three; Spirecore's upgrades only
  with Artifice 2, its bond lines never multiplied; neither both grows and upgrades.
- `ProjectileImmunityRulesTest`: which sources glance off; element projectiles hit; air poise still counts.
- `TokenKnowledgeTest`: witnesses (48 blocks, sight), the chest landmark at 64, a carrier at 24, staleness after 2
  in-game hours.
- The scan of `HEROES_V2.md` 15.6 extended to `logic/boss/`: no method takes "is a player" as an input.

### 9.2 GameTests (each job-running test its own `batch`; spawns at known relative positions on the flat test world; test bases through `GameTestBases.register`, cleaned up)

`witherFirstKillPlacesChestWithToken` (and a second Wither places none), `chestSpotSkipsLava`,
`tokenConsumedByMemberInsideBase` (an NPC soldier, then a mock player of the faction), `tokenNotConsumedByNonMember`,
`tokenLyingInsideBaseJoinsAfterFiveSeconds`, `tokenDropsAtCarrierDeathEvenWithKeepInventory` (NPC and mock player),
`tokenSurvivesFireExplosionCactusAndReturnsFromVoid`, `tokenRefusesEnderChestShulkerAndBundle` (the ender chest
gives it back when closed),
`bossHeroCallingAndSignatureInFirstSlot`, `bossHeroUnbindsWhenFactionFalls`, `witherArmourSameForNpcArcherAndPlayer`,
`archersDrawSidearmsAgainstArmouredWither`, `perchedDragonInSoldierReach`,
`ruinSkullBitesObsidianNotBedrock`, `birthBlastCratersInsideItsDisc` (and a broken channel breaks nothing),
`witherExplosivesSpareHerOwnBase` (inside her own walls she picks none of the three), `witherBlastsTheBreachSpot` (a
test base's stone-brick wall opened by her blue skulls at the planned spot while her soldiers wait outside),
`witherBreaksOutOfABox` (a 1x2 box of obsidian round her withers at once; with `mobGriefing` off she digs out by hand),
`witherBlackSkullsBreakNothing`, `wingGustThrowsIntoLavaAtFullStrength` (an NPC soldier and a mock player each, the
kill hers), `crystalShatterBreaksNoBlock`, `dragonFlightBreaksNoBlocks` (a flight through a test base's yard leaves
every block), `hollowStarGrowsFromWithering`, `signatureLentBackToItsHero`, `familiarDeathOpensBrokenCrown`,
`witherRebirthOncePerFight`, `crystalHealsOnlyNearestInSight`, `crystalBreaksToSnowballCageStopsArrows`,
`soldiersShootOpenCrystalsFirst`, `soldierPillarsToCagedCrystal`, `perchedDragonDeflectsArrowsTakesMoreMelee`,
`beamSnapHurtsAndAddsPoise`, `factionNpcDamageHurtsVanillaDragon` (with phase 3's fix), `scoutReportsTokenChest`,
`expeditionFetchesTokenAndEscortsHome`, `interceptionGoesForKnownCarrier`. The dragon's chest beside the fountain
needs the End, which the GameTest world does not have: its branch is unit-tested (9.1) and checked in a campaign.

### 9.3 Campaign checks (`docs/HANDOFF.md` section 4)

- An 8-day campaign with `/fc admin bosshero chest wither` 150 blocks from two factions: their scouts find the column
  (`sim.py` prints the report day), expeditions go, one brings the token home or loses it to an interception, the
  Wither joins and mines; her blue skulls open a breach on a raid and the defenders rebuild it; blocks broken per raid
  by her blasts, her own base untouched by them, Hollow Star's growth per day; no token is ever duplicated or lost for
  good (`sim.py` counts instances).
- A second run with both tokens, the player's faction among the four, the player carrying one token home.
- A staged fight on a dedicated server with the real client: the dragon's crystals broken by NPC soldiers (archers on
  the open ones, a climber on a caged one), her perch, Wing Gust beside a lava pool and a cliff, the Wither's armour,
  blasts, box break-out and rebirth; screenshots for the owner.
- A real End: kill the vanilla dragon with the player, see the chest beside the portal; tick time unchanged.

---

## 10. Work breakdown

The same rows are in `HEROES_V2.md` section 15.8. Sizes as there (S under half a session, M about one, L one to
two). B1-B3 can start once milestone 1's engine (A3-A6, A10-A12) is in, beside milestone 2 (they need A8's adds and
R1-R3's artifact rules for the signatures); B3 also needs `HEROES_V2.md` 15.9's T1 (full-strength pushes, edge aim)
and T2 (explosive moves), which the revision builds on; B4 needs the leadership hooks (L3), R4's choice rules and
ROADMAP phase 1's knowledge rule. Revised on 2026-11-18 (the owner's answers): the breach by hand is gone from B3,
which takes the Wither's blasts, her box break-out and the dragon's edge aim instead; B1, B4 and B7 take the
signatures' growth, upgrade and choice.

| ID | Task | Depends | Size | Done when |
|---|---|---|---|---|
| B1 | Format additions of section 8 in the reader, catalogue, balance and schema (`HeroCatalogue`, `HeroReaderV2`, `HeroBalanceV2`, the JSON Schema, `checkHeroes`), the revision's included (`explosive.resistance_cap`, `break_out`, `work.tool`, a signature's `growth` or `upgrade` in `ArtifactReader`); both kits as test fixtures | F2, F3, F5, R1, T2 | M | `BossKitsTest`; the fixtures load clean; every new piece has a reader message for its misuse |
| B2 | Tokens and the first-kill chest: the two items with the `boss_token` component, `ChestSpot`, the chest and its column, the return and hollow copies (a same-instance copy hollow after the first use), the consumption rule, the calling, unbinding into the token when her faction falls (the owner's answer; no free-hero switch), the hooks of 3.14 (loss detection, the ender chest's return), `CampaignData.bossHeroes`, commands, config | A1b, R2 | M | `ChestSpotTest`, `BossTokenRulesTest`, the token GameTests of 9.2 |
| B3 | Engine pieces: projectile-immune states, `keep_distance` in a state, `linger` (never stacking), `focus_add`, `smite`, `blast`, the crystal and familiar adds (spire and its placement, cage, beam with its per-fight cap, shatter at full strength and breaking nothing, orbit), `perch` (a block up at most), circling, a hovering flyer, the rebirth object (no re-arm after it), a rain round the hero, `immune_effects`, `shot_random`, the killer of `on_add_died`; from the revision: the blue skull's `resistance_cap` in T2's blast calculator, the Wither's box break-out (`break_out: box`, vanilla's rule when walled in), her hands as a `work.tool`, her blue skulls on the breach spot (with `docs/design/breaching.md` X3: up to three casts), an enemy's planned blocks her box break-out withers queued for repair, a dive's landing aimed away from an edge, no block smashing in the dragon's flight | A5, A6, A8, A10-A12, T1, T2 | L | `CrystalRulesTest`, `ProjectileImmunityRulesTest`, `WitherBlocksTest`, the fight and terrain GameTests of 9.2; every "NPC and mock player" test gives the same numbers |
| B4 | The AI: token knowledge (the two reports), the council's weighing, expeditions, interceptions, escorts, the raid-target bonus, `CrystalTargetGoal` (shared with the vanilla End raid), archers and heroes against projectile immunity with the ranged soldiers' sidearm (6.3), the signature's hold on its heroine with R4 (the standing lent mark, the ask back, her focus on an enemy hero wearing it), `sim.py` lines (blocks her blasts break per raid, Hollow Star's growth); check phase 3's dragon-damage fix (6.6) | B2, B3, L3, R4, ROADMAP phase 1's knowledge rule | L | `TokenKnowledgeTest`, `SignatureChoiceTest`, the AI GameTests of 9.2, the first campaign of 9.3 |
| B5 | Art: the NEW parts of 4.2 and 5.2 (with G8's planned `wings/dragon`, `horns/swept_dragon`, `tails/scaled`, `back/cape_torn`, `outfit_bottom/long_skirt`, `markings/scales`), the add models (`end_crystal` over vanilla's model, `end_spire`, `crystal_cage`, `skull_familiar`), the calling's and the revival's displays, about 24 own clips each, the token icons, previews for the owner | G8, G10b, A8, A9 | L | `herogen check` and `lint` clean; both heroes' previews and the add sheet sent to the owner |
| B6 | Sound: `the_wither.sfx.json`, `the_ender_dragon.sfx.json` and the add sounds of 4.11 and 5.11; the new slots (`rebirth`, `calling`, `linger`, `mobility.break`, `mobility.wings`) | S1, S2 | M | `sfxgen check` clean; previews for the owner |
| B7 | Content: the two hero source files (kits, lines, art, signatures), `hollow_star.json` (format 2, growing by `withering`) and `spirecore.json` (format 2, upgraded with Artifice 2), the kit sheets regenerated from them (kit version 3 by hand until then) | B1, B5, R1 | M | `herogen check` and `checkHeroes` clean; 14.4's gates 1-3 |
| B8 | Verification and release: the GameTests of 9.2, the campaigns of 9.3, README "Boss heroes", BALANCE rows, `docs/factionclash-common.toml`, jar and SHA-256 | all | M | the release checklist (`HANDOFF.md` section 7) |

---

## 11. The owner's answers (2026-11-18) and what is still open

### 11.1 The three questions, answered

The first design asked three questions here; the owner answered them on 2026-11-18 (`HANDOFF.md` section 2, request
11), with two questions of `ARTIFACTS.md`. Each answer, and where it went:

1. **Personal names** (the first design proposed one for each). Answer: "no personal names". They are "The Wither"
   and "The Ender Dragon", with their titles, everywhere (2.3); the proposals are withdrawn, and the review of the
   revision (12.2) took the two withdrawn names out of this file too, so no later task picks them up.
2. **When her faction falls**: back to her token, or a free hero who wanders? Answer: "token". She turns back into
   her token where she stands, keeping her level (3.8); the config switch for the other path is gone (3.13).
3. **The Wither's breach** (a 2x3 opening by hand, the one hero allowed to break a faction's walls). Answer: "its ok
   for any hero or npc that usrs explosives to breach walls, theres no need for anyone to be "civil" in battle, so
   they may use the resources they have to full advantage". Her breach is no longer an exception: her blue skulls and
   Birth Blast are explosions by nature and break blocks by `HEROES_V2.md` 6.18, and she breaches with them by the
   shared rules (4.5). With the owner's further answer ("its fine for heroes abilities to push someone of a cliff or
   to lava") both heroines' pushes land at full strength and are aimed at edges (4.5, 5.6).

And the artifact answers, for the signatures: fixed strength, slots by level (her one slot at level 1 holds it), each
hero's own choice, growth by design and tinkerers' upgrades with research (4.13, 5.13).

### 11.2 Open

No question needs the owner now. Settled by the revision with a recommendation the owner may still overturn:

- **Birth Blast at power 4**, not vanilla's 7: `HEROES_V2.md` 6.18's ceiling, and it keeps the crater inside the red
  disc that warns of it (4.5).
- **Her black skulls break nothing**; only the blue skulls and Birth Blast do (4.5). Say if you want the black skulls'
  small blasts too: they would need a heavy tell on her basic attack, which would change her kit.
- **The blue skulls keep vanilla's rule** and bite through obsidian; only bedrock-class blocks stop them (4.5).
- **Walled in, she withers her box at once** (vanilla's rule), but not every time she is hurt, as the vanilla Wither
  does (4.5).
- **The Ender Dragon does not smash blocks in flight**, and her crystals' shatter breaks no block (5.5, 5.6).
- **Hollow Star grows, Spirecore is upgraded**, one way each; Spirecore's upgrades wait for the faction labs of ROADMAP
  phase 6 (4.13, 5.13).
- **An ally who picks up her signature holds it as lent by her** and decides whether to give it back; an enemy hero
  wearing it is her first target (4.13).

Settled by the review, because the owner's own words or rules decide them (say if you disagree):

- **The token is used at once** inside one of the faction's bases, with a 10 s calling that is only a show: the
  owner's "if anyone from the faction bring the token inside a base it will consume it".
- **Both fight to the death**: no hero yields and each ends a fight by its own nature; theirs is a last stand (nerve
  90), as the vanilla bosses never flee.
- **Only the first kill gives a chest**: the owner's "defeated for the first time". Later kills may still give
  milestone 3's random hero token (v1 25.3).

---

## 12. Review (2026-11-18)

An adversarial pass over sections 0-11 and the two kit sheets, against the owner's decisions (`HEROES_V2.md` section
6, ROADMAP phase 4, request 10 of `HANDOFF.md` section 2), the engine's numbers (6.2-6.14, 8.1, v1 16.1) and the
format's real pieces. **Originality**: both heroes are original anime renditions of Minecraft's own bosses, the
intended reference; every mark of their looks comes from the vanilla models (the three heads and their soul-blue
light, the spine and ribs, the black-and-purple dragon with end-stone spines and crystals), not from a fan design,
and the clearance search of `ROSTER_PLAN.md` section 8 still runs on both looks before B5 (and would have on any
personal name; the owner chose none).
Two dragon moves carried the names and effects of World of Warcraft's classic dragon-boss abilities (a wing
knock-back called Wing Buffet, and Tail Sweep), words a roster dragon (row 45) also uses: they are now **Wing Gust**
and **Crescent Tail**. **Balance**: the dragon's base health was 32, under the vanguard preset of 34 with no reason
given, so a roster legendary vanguard had more health level for level and a player killed her faster than Benika
while no crystal stood; it is now 34 (136 / 165 / 197). With the dragon's crystals and the Wither's rebirth, level for
level they are now the most durable heroes: about 220 effective health each at level 10, against 185 for the
strongest roster hero (a legendary vanguard leader). Fights stay sensible: a player in iron needs about 52 s of landed
hits on the Wither (her rebirth and regeneration included) and 36-48 s on the dragon, two to three times that in a
duel, under the 180 s enrage; six iron soldiers need 7-10 s of landed blows. The crystals could heal her 0.75% a
second for as long as she kept raising them (8 a fight, 60 s each); all beams now heal at most 35% of her maximum in
one fight, then she raises no more, and open crystals still break to any hit (an arrow, a snowball, an egg) and caged
ones to three blows after a climb, so they cannot make her immortal. Two places let a hero be safe from arrows and
out of a soldier's reach at once: an NPC's melee reaches only about 2 blocks up (vanilla's attack box is not raised),
so a perch 3 blocks up and phase-1 crystals 3-4 blocks up were in reach of a player's sword only. The perch is now at
most a block above an enemy's ground, and phase-1 crystals stand on a 1-block spire. The armoured Wither could not be
hurt at all by an archers-only side (no NPC archer carries a melee weapon today): ranged soldiers now carry a sword as
a sidearm and draw it (6.3), which ROADMAP phase 3's raid on the vanilla Wither needs too. Stray Skulls (8 impacts of
6) was twice a rain's cap per receiver and is now 8 of 3. The rebirth at 30% would have re-armed phase 3 (20% + 10%);
no phase re-arms after a rebirth now. **Engine fit**: every frame is at or above its class minimum (checked move by
move); move-weight changes are written with `prefer_when`; the clouds of one use never stack on a receiver; Heart of
the Spires now says what v1's `cooldown_cut` does (every running cooldown, never under half); a spire never closes a
gate or a corridor and crumbles with its crystal; the new pieces are in section 8, `notes/kit-gaps.md` and the B rows.
**Tokens**: the rules now name their hooks (3.14), all vanilla and NeoForge events with no mixin; vanilla's ender
chest has no slot hook, so a closed ender chest hands the token back. A creative clone of the real token carried the
same instance and could have been used twice; a token is now real only while her state is `chest` or `loose`. A
faction knew its NPC carriers anywhere but its player carriers only within 64 blocks; both are now known the same way.
The other edge cases hold: two players (one real token; the first use wins), shulker boxes and bundles (refused),
other dimensions (a token is used only in an Overworld base, and NPC expeditions reach only Overworld chests until
ROADMAP phase 3), a save during the calling, a fallen faction (3.8), creative and operator players (never holders;
operators use the commands). **The owner's rules** hold: one combat mode and no rule that reads whether anyone is a
player; no attack tokens; no yield (both stand by their nature, nerve 90, and the files pin it); the 10-minute revival
(the egg and the soul-sand T are only its look); every ability from level 1; one adult body each; ordinary NPCs stay
player-like (a sidearm is a player's habit); vanilla stays whole (the chest is placed beside it; the egg and the
nether star still drop); ROADMAP phase 3's boss raids (with the dragon-damage fix of 6.6); the hero kill feed; the
knowledge rule for far deaths. The questions were cut to the three of section 11, which the owner has since answered
(11.1).

### 12.1 Check of the revision (2026-11-18)

- **The owner's answers**: no personal name is left anywhere (2.3, the sheets); the token on a faction's fall is settled
  and its config switch gone (3.8, 3.13); the Wither's breach exception is gone from every section (0, 1.1, 4.5, 4.6,
  6.4, 8, 9, 10, the sheets, `docs/design/breaching.md` 9, `HEROES_V2.md` 6.18 and 8.2); both heroines push at full
  strength and aim at edges (4.5, 5.3, 5.6); the signatures follow fixed strength (no attunement is left: 4.13, 5.13,
  section 7, the sheets), slots by level (2.4, 3.6) and each hero's own choice (4.13, 5.13).
- **Fairness** (`HEROES_V2.md` 6.14 as revised, and 6.18), re-checked row by row in 6.4: every blast rides a move of
  class heavy or above with a decal that covers it (Ruin Skull's strip and disc, a disc per Stray Skull, Birth Blast's
  red disc of 7 round a crater of about 6.8, the reason for power 4 and not 7); blasts hurt and push nobody, so every
  cap stands; the light and basic skulls stay blast-free; no blast near her own side; the box break-out only when she
  is boxed, never her own side's blocks. Pushes keep their tells (the walk round is the tell) and her own body never
  goes into a hazard; End Gravity stays a lift that drops nobody far. Stray Skulls is now six impacts of 4 (six
  blasts at most per use), still at the rain cap of 24 a receiver.
- **Equal treatment**: blocks are blocks (a player's house and a faction's hall alike); a push sends a player, a soldier
  and a hero over the same edge; an NPC holder of her signature is asked for it back by the same rule as a hero; only
  presentation is the player's.
- **Player-like and vanilla**: the blue-skull rule, the box break-out and the spawn blast come from vanilla's own code
  (read in the 1.21.1 sources: `WitherSkull.getBlockExplosionResistance`, `WitherBoss`'s `destroyBlocksTick` and spawn
  explosion, `EnderDragon.checkWalls`, NeoForge's `canEntityDestroy`, the `#wither_immune` and `#dragon_immune` tags);
  `mobGriefing` turns her block breaking off as it does the vanilla Wither's. Ordinary NPCs are unchanged and defend
  against edges by `docs/design/breaching.md` 8; the vanilla bosses keep every vanilla behaviour.
- **Balance**: her war power with her signature is flat (13.4 at level 10, 14.0 grown); a grown or upgraded signature
  is at most half a point over a greater artifact at III, and the build caps bind it; at home she fights without her
  explosive moves (7, case 11); abroad she breaches slowly, but through obsidian (case 10).
- **Originality**: nothing new is named after another game's item or mechanic, and no rule copies one: the star counts
  the withering its wearer sows (not kills or takedowns), loses no growth at a death and turns into nothing else; the
  upgrades are `ARTIFACTS.md` 9.8's bench rules, which never fail.
- **Looks**: 4.2 and 5.2 are untouched; the brooch and the pendant do not change with growth or upgrades, and the two
  signature icons keep their pulse, so the art being drawn stays valid.
- **Engine fit**: the new pieces are section 8's items 17-21 and kit-gaps' "Boss heroes revision"; the tasks are in 10
  and `HEROES_V2.md` 15.8 (B3 now after T1 and T2).

### 12.2 Review of the revision (2026-11-18)

An adversarial pass over the revision, both kit sheets and the battle rules they use (`HEROES_V2.md` 6.17-6.18,
`docs/design/breaching.md`, `ARTIFACTS.md`'s signatures), against the owner's answers of request 11, the earlier owner
decisions and the engine's numbers (`HeroEffects.knockback`: strength 1 is vanilla's knockback of 0.4; read in the
code, with vanilla's `WitherSkull`, `LivingEntity.hurt` and `CombatTracker` in the 1.21.1 sources).
**The owner's answers hold**: no personal name is left (the two withdrawn proposals are gone from 11.1 too); the token
on a faction's fall; her blasts are explosions by nature under 6.18's limits, and nobody is "civil" (her blue skulls go
through obsidian, the dragon's wings throw anyone off a rim); the signatures are fixed-strength and grow or upgrade by
their own design. **Earlier decisions hold**: one combat mode (every rule reads "an enemy"), no yield (both last
stands), the 10-minute revival (egg and soul sand are only its look), one adult body each, player-like NPCs (the
sidearm, pillaring to crystals), vanilla left whole (the dragon fix only tags the mod's own damage). **Fixed**:
- *Push distances*: case 12 said Wing Gust throws about 3 blocks. Knockback 3 is vanilla's knockback of 1.2, about 6
  blocks on flat ground (about 4 with a vanguard's or netherite's knockback resistance): the sheets now give the
  distance, and the ordinary NPCs' edge sense (`docs/design/breaching.md` 8.1) now watches as far as a hero can push.
- *Hollow Star's count*: "the familiars' shots do not feed it" contradicted "its passives count", since Three Heads is
  her passive while the side heads float beside her. They count there, and not once they are adds in phase 3 (4.13).
- *An enemy's walls*: her box break-out may wither an enemy base's planned blocks (never her own side's), but no
  NeoForge event fires for a mob's own block break, so they would never have been rebuilt. B3 queues them for repair as
  explosions' are (4.5).
- *The dragon-damage fix* (6.6) needed a hook: vanilla's `EnderDragon.hurt(part, ...)` drops other damage before any
  event, so the faction's melee source and, through `ProjectileImpactEvent`, its arrows carry the tagged damage type
  themselves. No mixin.
- *Switches*: `mobGriefing` gates her blasts and her body as vanilla gates the vanilla Wither's; TNT she carries is a
  player's TNT, which that rule never gated (the battle rules' review settled this for every breacher, 4.5).
- *Originality*: Spirecore's improvement at III is "tuned", not "honed" (Lost Ark's upgrade system is "honing";
  `ARTIFACTS.md` 21.2).
**Balance, re-checked**: her war power (10.4 / 13.4 / 14.0, the dragon 10.1 / 13.3 / 13.7) is 8.1's formula exactly;
Birth Blast's reach at power 4 is at most 1.3 x 4 / 0.75 = 6.9 blocks along air, inside its 7-block disc; one Ruin
Skull bites one block deep at power 1 with the 0.8 cap (0.33 off the ray per sampled block against at most 1.3), rarely
two; Stray Skulls' six power-1 blasts are 6.18's limit. A player beside a cliff is still safe with play: the walk round
(up to 3 s) and every wind-up and disc come first, a raised shield does not stop a push (vanilla's blocked blow still
knocks back) but stepping out of the disc, keeping a few blocks from the edge, knockback resistance, a water bucket and
Fire Resistance all do. No new owner question.
