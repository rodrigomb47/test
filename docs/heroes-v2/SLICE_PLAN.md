# Heroes v2, milestone 1: the vertical slice

Status: plan, 2026-11-12. Design: `HEROES_V2.md` ("design §n" below). Roster: `ROSTER_PLAN.md`.

**Goal.** Eight complete original heroes (4 female, 4 male) with animated models, faces that blink and change
expression, signature combat animations, boss fights and non-combat work, leading hero-led factions in a real
multi-day campaign on a dedicated server, with a screenshot gallery for the owner. Every engine piece the other 192
heroes need is built and proven here, so the content batches afterwards are "only" content.

**Done when** every acceptance criterion of section 5 holds, the tests of section 6 pass, the owner has the gallery of
section 7, and the release checklist (HANDOFF section 7) is complete.

---

## 1. Scope

### 1.1 In scope

| Area | What ships in the slice | Design | Tasks (design §15.2) |
|---|---|---|---|
| Dependency | GeckoLib 4.9.3 embedded and vendored | §4.2 | A0 |
| Format and pure logic | v1 kit format in Java; format 2 (moves, frames, classes, bands, followups, the 8 new forms, the 7 new effects, new conditions and triggers, states, boss block, personality, leader, talents, voice, new line events); `BossRules`, `MoveSelector`, `HeroActions`; `Personality`, `Temperaments`, `LeaderBiases`, `LeaderDraft`, `FactionNameGen`, `Palette`, `BannerDesign`, `BaseStyles`, `Talents`; `checkHeroes` | §12, §14.1 | F1-F4 |
| Hero entity and runtime | `HeroEntity`, synced fields, saving, `HeroRegistry`, commands, ability core, passives, states, summons, move runner, boss controller, `HeroDecoy` | §3, §6 | A1, A3-A7 |
| Client | `HeroModel`, `HeroRenderer`, controllers, `HeroFace` (blink, 12 moods, talking), glow and item layers, the sash tint, LOD, `_fallback` model, lineup/preview/pose commands | §4 | A2 |
| Art generator | rig v1 and six bases, UV template, packer, painter, face painter, geo writer, animation generator with secondary bake, preview renderer and sheets, asset validator | §13, §14.2 | G1-G3, G7 |
| Libraries | the core animation library; 7 style libraries (fists, focus, hammer, katana, twin_blades, sword_shield, staff); about 82 parts | §13.3, §13.6 | G4-G6 |
| Leadership | identity layer and slugs (M1, M3), neutral NPC skins and tint (M4), `LeaderBiases` hooks (M6), leader draft, identity from the leader, 6 new base styles, banners, leader spawn, `court` duty, leader death and revival, announcements, leaders for old campaigns (M5) | §5.1-5.6 | L1-L4 |
| Talents | the 15 milestone-1 talents | §7.2 | L5 |
| Content | the 8 heroes of section 3 | §12, §13 | C1-C4 |
| Verification | about 81 new GameTests (four test-only fixture heroes included), unit tests, two 8-day campaigns, a regression run, an old-world load, render measurements, screenshots, review | §14 | V1-V5 |

### 1.2 Out of scope (later milestones)

Diplomacy v2, player standing and stances, morale, edicts and whims (milestone 2); faction slots 5-8; spirit adds and
custom projectile models (only `decoy` ships now; projectiles use v1's vanilla item looks); the other 9 style
libraries, the full parts library, the other 6 base styles; the other 17 talents; wandering heroes, tokens v2,
recruitment of further heroes by the AI, duels, escorts, commissions, bonds, posts, awakened ultimates (milestone 3);
the other 192 heroes. The slice's own heroes join factions beyond their leaders only through `/fc admin hero give`.

### 1.3 Prerequisite

GeckoLib 4.9.3 can be downloaded from `dl.cloudsmith.io`: refused on 2026-11-11, it answered on 2026-11-12 and the
published jar's coordinates, hashes and metadata were checked (design §4.2). If it is refused again, the owner approves
the one-time network change (open question 4); until then the work that does not need GeckoLib proceeds: F1-F4, G1, G3 (Python only), L1-L3, and the hero source files. If the host
cannot be allowed at all, the slice switches to the hand-written fallback engine (design §4.1), which adds about two
agent sessions to A2 and loses marker keyframes (replaced by a sidecar timing file).

---

## 2. Engine pieces and what "done" means for each

| Piece | Done when |
|---|---|
| GeckoLib in the build | the jar embeds it; the dedicated server and the Xvfb client start with only our jar; every existing test passes |
| Hero entity | spawns, fights, works its duty, saves and reloads with its cooldowns and energy, dies and revives, is unique per id |
| Renderer and face | the test rig and all 8 heroes render with idle, walk, run, blink (a GIF shows it), all 12 moods, the talking mouth, phase looks, the faction sash, held work tools, and the LOD switch at 20 blocks |
| Move runner | `DATA_ACTION` is set at wind-up start; the hit lands exactly `frames.windup` ticks later; speed scaling and interrupts work |
| Boss controller | every rule of design §6.2-6.14 has a GameTest or a recorded viewer-session check (section 6.5) |
| Decoy | renders the owner's model, mimics its action, has no shadow, pops on a hit |
| Generator | `herogen build --check` is clean; the preview matches the game within 1 px; `check` catches a broken marker, an over-budget model and a missing face frame (three negative tests) |
| Identity | every player-visible faction name, colour, banner and style comes from `FactionIdentity`; legacy worlds look unchanged until they get leaders |
| Leaders | four drafted leaders found four factions; their personalities change the director's numbers as `LeaderBiases` says |
| Talents | each of the 15 shows its tier numbers on the hero sheet and has a GameTest of its effect |

---

## 3. The eight heroes

All eight are epic (three phases), leader-eligible, and cover all eight elements, six roles, five duel styles and seven
weapon styles. Their faction primaries are at least 27 apart (CIE76 delta-E, every pair; checked) and their banner base
dyes, which colour wool, carpets and beds, are all different (orange, purple, red, white, blue, gray, light blue,
green), so any four of them can lead one campaign without the draft relaxing its colour rule. What the eight do not
cover (rare and legendary phase rules, bows and crossbows, v1 summons) is covered by four test-only fixture heroes
(design §14.5); m_large and wings are covered by the parts sheets of G6a-c (every part on all six bases).

| # | Hero (id) | G | Archetype | Role | Style: prop (mechanical item) | Element | Temperament | Duel | Talents (* = realm) | Duty |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Natsuho Komezaki (`natsuho_komezaki`) | F | cat | striker | fists: claw gauntlets (iron sword drawn as claws) | physical | tsundere | rushdown | angler_lord*, lookout | fisherman |
| 6 | Yugen Kagaribi (`yugen_kagaribi`) | M | fox | marksman | focus: foxfire lanterns and a folding fan (thrown) | arcane | schemer | zoner | merchant_prince*, spymaster | trader |
| 22 | Benika Akagane (`benika_akagane`) | F | oni | vanguard | hammer: iron-studded club (mace) | fire | battle_maniac | grappler | surveyor*, drill | miner |
| 56 | Hinae Shirasuzu (`hinae_shirasuzu`) | F | shrine | support | focus: purification wand and talismans (thrown) | radiant | sweet_natured | zoner | mend, purifier* | guard |
| 65 | Kagenori Tsurumine (`kagenori_tsurumine`) | M | samurai | striker | katana: long and short swords (iron sword) | shock | stoic | counter | drill, mentor* | guard |
| 74 | Tobimaru Usugumo (`tobimaru_usugumo`) | M | ninja | skirmisher | twin_blades: short blade and kunai (iron sword; thrown kunai) | blight | lone_wolf | rushdown | cartographer*, spymaster | scout |
| 108 | Ilsabet Frostmere (`ilsabet_frostmere`) | F | knight | vanguard | sword_shield: longsword and kite shield | frost | righteous | counter | rally*, warden_of_walls | guard |
| 121 | Ambrose Nettlewick (`ambrose_nettlewick`) | M | witch | controller | staff: gnarled cauldron staff (thrown vials) | venom | eccentric_genius | trapper | sage_of_letters*, ember_keeper | enchanter |

Frames below are wind-up / active / recovery in seconds; every value meets its class minimum (design §6.4). Damage is
written as a share of attack power (design v1 §6.2) or as health points; the validator's caps and floors apply on top.
"P" lists the phases a move belongs to.

### 3.1 Natsuho Komezaki: the harbour cat (F, cat, striker, physical, tsundere)

**Concept.** A prickly dock brawler who insists she only fights for fish, and who would never admit she watches over
the harbour workers. She pounces, claws, and rolls out of reach.

**Look.** Base `female`, build `slim`, scale 92. Short bob with two tufts and an ahoge; tufted cat ears (left orange,
right black); long calico tail; short hooded jacket (orange) over a white wrap top; belted charcoal shorts; white thigh
socks and split-toe boots; a red ribbon choker with a gold bell; faint whisker stripes. Claw gauntlets on both hands.
Palette: skin `#F5D9C4`, hair `#EDE6DA` with `#E0823A` and `#2A2526` patches, eyes `#E2A33B`, primary `#E0823A`,
secondary `#F4EFE6`, accent `#2A2526`, trim `#C8323C`, metal `#C9CED6`. Face: `cat` eyes with slit pupils, lashes 2,
`thin_arched` brows, `cat` mouth, left fang, blush on. Mood overrides: `happy` shows a frown with a blush (she will not
smile openly); `flustered` is her most-used mood. Blink `normal`. Idle quirks: ear flick, tail S-sway, paw lick. Phase 2:
tail fur puffs (`p2_tail_puff`); phase 3: slit pupils glow amber.

**Kit.** Basic: fists string (jab, jab, claw); 30% chance on hit of a physical bleed (1 per second, 3 s, up to 2
stacks).

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `claw_string` | combo, 3 strikes | light | close | 0.45 / 0.5 / 0.35 | 45%, 45%, 70% + bleed 1/s 4 s (2 stacks) | 1-3 |
| `pounce` | leap, range 8, radius 1.5 | heavy | mid | 0.8 (crouch and hip wiggle) / 0.2 / 1.0 (paw lick) | 110% + knockback 1 | 1-3 |
| `hiss` | cone 4, 90 degrees | area | close | 1.0 (ears flat, tail puffed) / 0.1 / 0.8 | Weakness I 4 s + knockback 2 | 1-3 |
| `scratch_dash` | dash 6 | heavy | mid | 0.8 / 0.25 / 0.8 | 80% + bleed; followup `claw_string` 60% | 1-3 |
| `cat_reflex` | counter, stance 1.0 s, absorb 70% | heavy | close | 1.0 (the stance) / 0.1 / 0.8 | riposte 2 swipes 60% each + hop back 3 | 2-3 |
| `zoomies` | charge 14, speed 180%, wall stun | heavy | mid, far | 0.9 / 0.8 / 1.2 | 90% + knockback 2; repeatable, followup itself 50% | 3 |

Ultimate `midnight_frenzy` (class ultimate, 1.5 / 0.2 / 1.5: a long stretch and a yowl): enters state `frenzy` for 8 s
(attack speed +25%, speed +20%, every hit bleeds), then `pounce` follows at once. Passives: `nine_lives`
(`on_low_health` 30, once per fight: shield 8, Speed II 6 s, hop back 6); `cat_crit` (`on_crit`: speed +15% 3 s).

**Boss.** Health scale 350 (400 as leader), poise 80. Weak points: `water` (a water splash makes her flinch 1 s, once per
20 s, and deal 10% less for 5 s; hinted by "Don't you DARE get me wet!") and `recovery` (the paw lick).

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `claw_string`, `pounce`, `hiss`, `scratch_dash` | - |
| 2 | 60 | `cat_reflex` (a counter stance: a new mechanic) | "F-fine! I'll take you seriously, but only this once!" |
| 3 | 25 | `zoomies` (charges across the arena; slam her into a wall) | "Nobody... corners... a cat!" |

Reactions: blocking -> `scratch_dash` round the shield (50%); drawing a bow -> `pounce` (60%); eating -> `pounce`
(70%). Enrage after 180 s (240 as leader). Intro: "Huh? You're in my harbour. ...Fine. I'll show you out myself."

**Personality.** Tsundere; traits a 65, h 65, m 60, c 45, g 55, s 40, l 70, v 45. Likes cod, salmon, string; dislikes
pufferfish, water buckets. Quirk: "pretends not to listen, then remembers every word."

**Leader.** Title "Boss of the Harbour". Faction names "Bellwhisker Harbour", "The Calico Wharf League"; short name
"Bellwhisker"; motto "Nobody asked for your help." Colours primary `#E0823A`, secondary `#F4EFE6`, accent `#2A2526`.
Banner orange with `triangles_top` black and `circle` white. Base style `tide_port`; culture east; economy focus food,
scouting.

**Voice and lines.** Voice `xylophone`, pitch 1.35, rate 2. Founding: "This harbour's mine now. Don't get the wrong
idea, I'm not doing it for you." Greeting: "{player}? I wasn't waiting for you. ...Did you bring fish?" Kill: "Hmph.
Too slow." Yield: "W-whatever. I let you win." Spared: "...Thanks. Tell anyone and I'll scratch you."

**Own animations.** `move.claw_string`, `move.pounce`, `move.hiss`, `move.scratch_dash`, `move.cat_reflex`,
`move.zoomies`, `ultimate`, `state.frenzy`, `phase.2` (tail puff, flustered face), `phase.3` (eyes narrow and glow),
`intro` (arms crossed, looks away, glances back), `personal.1` paw lick, `personal.2` ear flick, `personal.3` stretch
and yawn.

### 3.2 Yugen Kagaribi: the lantern fox (M, fox, marksman, arcane, schemer)

**Concept.** A soft-spoken fox merchant-lord who never raises his voice and always has a contract ready. His foxfire
burns blue and cold, and you are never quite sure which of him is real.

**Look.** Base `male`, build `slim`, scale 100. Long silver hair, half up; tall silver fox ears with black tips; three
fluffy tails (six in phase 2 as `p2_` parts, nine in phase 3); a layered plum court robe with a silver crest; a white
fox mask with red marks worn on the side of the head; prayer beads; court shoes. A closed folding fan in the right hand
and three small paper lanterns orbiting him (`fine_lantern_1-3`, a secondary loop). Palette: skin `#F4DCCB`, hair
`#D8DCE6`, eyes `#8FB7FF`, primary `#6B2148` (plum), secondary `#D8DCE6`, accent `#5AA9FF` (foxfire), trim `#B23A48`,
glow `#7FC8FF`. Face: `narrow` half-lidded eyes, lashes 1, `thin_arched` brows, `soft` mouth, red eye-liner flicks.
Blink `calm` (a sly slow blink). Idle quirks: sleeve-to-mouth laugh, tail fan flick, lantern spin. Glow: lanterns
always, tail-tip foxfire in phase 3, eyes in phase 3.

**Kit.** Basic: thrown foxfire (item look `blaze_powder`, particle `soul_fire_flame`), 4 arcane every 1.5 s, range 14.

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `foxfire_volley` | volley 5, range 16 | light | mid, far | 0.5 (tails flare) / 0.3 / 0.4 | 4 arcane each | 1-3 |
| `lantern_ring` | zone at self, radius 4, delay 1.0, 3 s, pulse 1 s | area | close | 1.0 / 3.0 / 0.8 | 3 arcane per pulse + Slowness I 2 s; prefer target close | 1-3 |
| `fan_cut` | combo 2 (melee fan) | light | close | 0.4 / 0.3 / 0.3 | 60% + knockback 2; followup `vanish` 80% | 1-3 |
| `vanish` | self | light | close | 0.4 / 0.1 / 0.3 | Invisibility 2 s + hop back 6; followup `foxfire_volley` 70% | 1-3 |
| `mirage` | self, `spawn_add` decoy x2 | heavy | any | 0.8 / 0.2 / 0.8 | two decoys for 10 s that copy his casts (their foxfire does no damage); only he casts a shadow | 2-3 |
| `foxfire_rain` | rain 8, radius 5, 2 s, impact delay 0.9 | area | mid, far | 1.0 / 2.0 / 0.8 | 5 arcane per impact | 3 |

Ultimate `ninefold_lantern` (1.6 / 1.8 / 1.5; the lanterns gather into a ring behind him): a barrage of 9 foxfire, 0.2 s
apart, 3 arcane each and mark `bewitched`. Passives: `contract` (`on_hurt` by melee, 25%: mark `bewitched` on the
attacker); `bewitched_full` (`on_mark_full` `bewitched` at 3: stun 1 s, players get Slowness; cooldown 12 s).

**Boss.** Health scale 350, poise 70. Weak points: `iron` (+15% from iron weapons; hint "Iron... how crude.") and
`recovery`.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `foxfire_volley`, `lantern_ring`, `fan_cut`, `vanish` | - |
| 2 | 60 | `mirage` decoys; tails 3 -> 6 | "Which of me is lying? All of us, perhaps." |
| 3 | 25 | `foxfire_rain`; tails 9, eyes glow | "Enough games. Let the lanterns speak." |

Reactions: target close -> `vanish` (70%); drawing a bow -> `foxfire_volley` (50%); behind him -> `fan_cut` (60%).
Enrage 180 s (240 as leader). Intro: "A customer? How delightful. The price today is your patience."

**Personality.** Schemer; a 45, h 20, m 85, c 70, g 70, s 60, l 30, v 25. Likes gold ingots, emeralds, lanterns;
dislikes iron ingots. Quirk: "smiles most when he is lying, and he is always smiling."

**Leader.** Title "Master of the Veiled Lantern". Faction names "Veiled Lantern Exchange", "The Blue Lantern Company";
short "Veiled Lantern"; motto "Every promise has a price." Colours primary `#6B2148`, secondary `#D8DCE6`, accent
`#5AA9FF`. Banner purple with `circle` light blue and `triangles_top` white. Base style `cherry_court`; culture east;
economy focus trade, lore.

**Voice and lines.** Voice `flute`, pitch 1.0, rate 3. Founding: "Sign here, and here. Congratulations: you all work for
the Veiled Lantern now." Greeting: "Ah, {player}. Buying or selling? With me it is always both." Kill: "Paid in full."
Yield: "Mm. A poor bargain for me." Spared: "Mercy, freely given? I shall have to find out what it costs."

**Own animations.** One per move, `ultimate`, `phase.2` (tails fan out, a laugh behind the sleeve), `phase.3` (lanterns
flare, eyes glow), `intro` (a slow bow with the fan), personal idles: sleeve laugh, lantern spin, tail groom.

### 3.3 Benika Akagane: the iron club oni (F, oni, vanguard, fire, battle maniac)

**Concept.** A laughing oni smith-warrior who wants one thing: an opponent worth breaking her club on. Her settlement
rings with hammers and glows with forge fire.

**Look.** Base `female`, build `large`, scale 108. Wild long black mane with red tips; a single curved red horn on the
forehead (it glows from phase 2); an open crimson coat over chest wraps; a short black hakama with a gold rope belt;
iron gauntlets; a gourd at the hip; wrapped feet in sandals; red stripe markings on the arms (glowing in phase 2). An
iron-studded club in both hands, carried over the shoulder. Palette: skin `#E8B89A`, hair `#1B1717` with `#C0392B` tips,
eyes `#F2C14E`, primary `#A3202A`, secondary `#1B1717`, accent `#D4A23A`, metal `#6D6E73`, glow `#FF7A2A`. Face: `sharp`
eyes, lashes 1, `bold` brows, `wide` mouth, fangs both sides. Blink `normal`. Idle quirks: shoulder roll, gourd swig,
club on the shoulder. Phase 3: eyes glow, steam particles from the horn.

**Kit.** Basic: hammer string (overhead, side swing, slam) with the kit mace "Ironmaw" (Density I).

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `overhead_slam` | cone 4, 40 degrees | area (a damaging cone is never lighter than area) | close | 1.0 (club rises) / 0.15 / 1.2 (club stuck) | 150% fire + knock-up 1 | 1-3 |
| `shoulder_charge` | charge 10, speed 160%, wall stun | heavy | mid | 0.9 / 0.6 / 0.9 | 90% + knockback 3 | 1-3 |
| `ground_stomp` | wave, radius 8, speed 8, 1 wave | area | close, mid | 1.0 / 1.0 / 0.8 | 6 fire + ignite 2 s (jump the ring) | 1-3 |
| `gourd_swig` | self | light | any | 1.2 / 0.1 / 0.6 | heal 6 + attack +15% 8 s; prefer own health under 50% (a punish window) | 1-3 |
| `grab_throw` | grab 2.5, throw 3, up 1 | heavy | close | 0.9 / 1.0 / 1.2 | 8 fire | 2-3 |
| `meteor_club` | leap 10, radius 3 | heavy | mid, far | 1.0 / 0.6 / 1.2 | 120% fire; followup `ground_stomp` 100% | 3 |

Ultimate `iron_mountain_crash` (1.8 / 2.0 / 1.6; club raised high: "Hold still, this'll be fun!"): a leap of range 10 (the `leap` maximum),
radius 4, then three fire waves of 8. State `rage` (phase 2 on): attack +20%, knockback resistance +30, size 105%,
mood `angry`. Passive `battle_joy` (`on_hurt`: attack +5% for 10 s, up to 4 stacks).

**Boss.** Health scale 350 (400 as leader), poise 140. Weak points: `snowball` (a snowball hit ends `rage` and costs
her 10% damage for 5 s; hint "Ugh, cold! Who throws snow at an oni?!") and `charge_wall`.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `overhead_slam`, `shoulder_charge`, `ground_stomp`, `gourd_swig` | - |
| 2 | 60 | state `rage` (horn glows), `grab_throw` (unblockable) | "Now THIS is a fight!" |
| 3 | 25 | `meteor_club` into a stomp | "Don't you dare fall down yet!" |

Reactions: blocking -> `grab_throw` (70%); target far -> `shoulder_charge` (60%); eating -> `shoulder_charge` (70%).
Enrage 240 s as leader, also when her heart is attacked. Intro: "Oho! You look sturdy. Let's find out!"

**Personality.** Battle maniac; a 95, h 70, m 70, c 5, g 25, s 55, l 55, v 60 (an honourable fighter who spares the
beaten). Likes cake, honey bottles, iron ingots; dislikes snowballs. Quirk: "has a sweet tooth she defends with her
club."

**Leader.** Title "Warlord of the Crimson Anvil". Faction names "Crimson Anvil Warband", "The Red Horn Host"; short
"Crimson Anvil"; motto "Bring me someone worth breaking." Colours primary `#A3202A`, secondary `#1B1717`, accent
`#D4A23A`. Banner red with `triangles_bottom` black and `circle` yellow. Base style `ember_fortress`; culture east;
economy focus military, mining.

**Voice and lines.** Voice `guitar`, pitch 1.1, rate 2. Founding: "Listen up! From today we're the Crimson Anvil. We
work hard and we fight harder!" Greeting: "{player}! Arm-wrestle later? No? Coward!" Kill: "Next!" Yield: "Hah...
hah... that was GREAT." Spared: "You're soft. I like you anyway."

**Own animations.** One per move, `ultimate`, `state.rage`, `phase.2` (horn flares, a roar), `phase.3` (steam, a grin),
`intro` (plants the club, cracks her knuckles), personal idles: shoulder roll, gourd swig, club spin.

### 3.4 Hinae Shirasuzu: the white bell shrine maiden (F, shrine, support, radiant, sweet-natured)

**Concept.** A gentle shrine keeper who would rather talk than fight, keeps lanterns lit for travellers, and whose
talismans never miss when she finally throws them.

**Look.** Base `female`, build `standard`, scale 96. Long straight black hair tied low with white paper, straight
bangs; a white kimono top with plain sleeves; pleated red hakama; split-toe socks and clogs; talisman strips at the
belt. A purification wand with paper streamers and a cluster of bells in the right hand; a fan of talismans in the left.
Palette: skin `#F7DFD0`, hair `#1C1A22`, eyes `#B4413A`, primary `#F6F3EE`, secondary `#C8323C`, accent `#D9B44A`, trim
`#EADFC8`, glow `#FFE9A3`. Face: `round` eyes, lashes 2, `worried` brows, `soft` mouth, blush on. Blink `normal`. Idle
quirks: bell shake, sweeping, hands together and a small bow. Phase 2: seven talismans orbit her (`p2_talisman_ring`,
glowing); phase 3: eyes glow gold, streamers glow.

**Kit.** Basic: thrown talisman (item look `feather`, particle `end_rod`), 4 radiant every 1.5 s.

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `talisman_fan` | volley 5, range 16 | light | mid, far | 0.5 / 0.3 / 0.4 | 4 radiant + mark `seal` | 1-3 |
| `sealing_circle` | zone at target, radius 3, delay 1.0, one burst | area | mid | 1.0 (bell ring) / 0.1 / 0.8 | 6 radiant + Slowness II 3 s; with 3+ `seal`: stun 1 s and the marks clear | 1-3 |
| `purify` | allies, radius 8 | light | any | 0.6 / 0.1 / 0.4 | cleanse + heal 4; use when an ally is under 60% | 1-3 |
| `exorcise` | nova, radius 4 | area | close | 1.0 / 0.1 / 0.8 | 3 radiant + dispel + knockback 2; prefer target close | 1-3 |
| `seven_talismans` | self | heavy | any | 0.8 / 0.2 / 0.8 | `barrier` 7 hits, 12 s (each hit pops a talisman) | 2-3 |
| `purifying_dance` | sweep 8, 180 degrees, 2 s; allies Regeneration I 4 s | area | close, mid | 1.0 / 2.0 / 1.5 (kneels to pray) | 5 radiant per pass | 3 |

Ultimate `thousand_bell_rite` (1.6 / 0.3 / 1.5; channelled, breaks after 15% of her max health in damage): allies within
10 heal 10 and gain a shield of 8; enemies within 6 take 10 radiant and Slowness II 4 s. Passive `guardian_charm`
(`on_ally_low_health` 35: shield 4 on that ally; cooldown 15 s).

**Boss.** Health scale 350 (400 as leader), poise 80. Weak points: `channel` (the rite and the dance's prayer) and
`back`.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `talisman_fan`, `sealing_circle`, `purify`, `exorcise` | - |
| 2 | 60 | `seven_talismans` (a barrier you must break with hits) | "Please... stop. I don't want to hurt you." |
| 3 | 25 | `purifying_dance` | "Then I'll purify this fight myself!" |

Reactions: target close -> `exorcise` (70%); an allied hero under half health -> `purify` (80%); blocking ->
`sealing_circle` (zones ignore shields; 50%). Enrage 240 s as leader, also when her heart is attacked. Intro: "Welcome,
traveller. Will you put your weapon down? ...No? I'm sorry, then."

**Personality.** Sweet-natured; a 30, h 75, m 40, c 60, g 25, s 90, l 80, v 35. Likes poppies, sweet berries, lanterns;
dislikes rotten flesh. Quirk: "apologises to the monsters she defeats."

**Leader.** Title "Keeper of the White Bell". Faction names "Hearth of the White Bell", "White Bell Sanctum"; short
"White Bell"; motto "Every lantern lit, every door open." Colours primary `#F6F3EE`, secondary `#C8323C`, accent
`#D9B44A`. Banner white with `straight_cross` red and `stripe_center` yellow. Base style `shrine_pavilion`; culture
east; economy focus food, people.

**Voice and lines.** Voice `bell`, pitch 1.4, rate 3. Founding: "Everyone, welcome home. From today we are the Hearth of
the White Bell." Greeting: "{player}! Have you eaten? There's rice by the station." Kill: "Rest gently." Yield: "I...
see. You are very strong." Spared: "Thank you. I'll ring a bell for you tonight."

**Own animations.** One per move, `ultimate` (+`.loop`), `phase.2` (talismans unfold into a ring), `phase.3` (eyes open
glowing), `intro` (bows, raises the wand), personal idles: bell shake, sweeping, prayer.

### 3.5 Kagenori Tsurumine: the still crane samurai (M, samurai, striker, shock, stoic)

**Concept.** A quiet swordsman of few words who announces his name before every duel, never strikes a kneeling foe,
and fights like still water until the moment his blade moves.

**Look.** Base `male`, build `standard`, scale 102. Long black high ponytail; an indigo coat with a white crane crest;
white hakama; a steel shoulder plate on the right; wrist guards and sandals. Long and short swords at the hip (the long
one drawn); a plain helmet goes on in phase 2 (`p2_`); in phase 3 he draws the short sword too (`p3_weapon_l`).
Palette: skin `#F1D3BC`, hair `#1E1F2A`, eyes `#6C7BD0`, primary `#2E3A6B`, secondary `#E8E4D8`, accent `#9AA6B2`, trim
`#B8963E`, metal `#C9CED6`. Face: `sharp` eyes, lashes 0, `straight_thick` brows, `small` mouth; his `happy` is
focused eyes with a faint smile. Blink `calm`. Idle quirks: hand resting on the hilt, eyes closed breathing. Phase 3:
eyes glow, blade edges glow.

**Kit.** The source file in design §12.18. Basic: katana string; every third hit marks `storm`.

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `three_cut` | combo, 3 strikes | light | close | 0.45 / 0.45 / 0.35 | 55%, 55%, 80% + knockback 1 | 1-3 |
| `iai_stance` | counter, stance 1.5 s, absorb 80% | heavy | close | 1.5 (the stance: hand on hilt, a guard click) / 0.1 / 0.8 | riposte 140% + stun 1 s (players: Slowness); arrows are not countered | 1-3 |
| `thunderclap_draw` | line 8, width 1 | heavy | mid | 1.3 (stillness, a glint) / 0.05 / 0.85 (re-sheathe) | 150% shock; followup `crane_step` 30% | 1-3 |
| `crane_step` | dash 6 | heavy | mid | 0.8 / 0.25 / 0.8 | 80%; followup `three_cut` 70% | 1-3 |
| `guard_splitter` | melee, breaks guard | heavy | close | 0.9 / 0.1 / 0.9 | 110% + `guard_break` 4 s | 2-3 |
| `chain_draw` | chain 3, range 10, radius 4 | heavy | mid | 1.0 / 0.45 / 0.8 | 7 shock + Slowness I 3 s | 2-3 |
| `falling_crane` | leap 8, radius 2 | heavy | mid, far | 0.9 / 0.5 / 1.0 | 100% + knockback 2; followup `three_cut` 100% | 3 |

Ultimate `heaven_split` (2.0 / 0.1 / 1.6; eyes close, no blinking, then open sharp): a line 12 long and 2 wide, 22 shock,
a harmless lightning flash along it; dodge sideways. State `twin_sky` (phase 3): attack speed +20%, damage +10%. Passive
`still_mind` (`on_counter`: 15 energy).

**Boss.** Health scale 350 (400 as leader), poise 100. Weak points: `recovery` (after a whiffed counter or the
re-sheathe) and `back`.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `three_cut`, `iai_stance`, `thunderclap_draw`, `crane_step` | - |
| 2 | 60 | helmet on (armour +20%), `guard_splitter`, `chain_draw` | "Then I will fight you properly." |
| 3 | 25 | state `twin_sky` (two swords), `falling_crane` | "Forgive me, teacher." |

Reactions: blocking -> `guard_splitter` (60%); drawing a bow -> `crane_step` (70%). Enrage 180 s (240 as leader).
Intro: "Tsurumine Kagenori. Draw when you are ready."

**Personality.** Stoic; a 50, h 90, m 45, c 65, g 25, s 30, l 85, v 10. Likes bamboo, cherry saplings; dislikes rotten
flesh. Quirk: "re-ties his hair before every duel."

**Leader.** Title "Lord of the Still Crane". Faction names "House of the Still Crane", "Still Crane Watch"; short "Still
Crane"; motto "The blade waits. So do we." Colours primary `#2E3A6B`, secondary `#E8E4D8`, accent `#9AA6B2`. Banner
blue with `circle` white and `border` light grey. Base style `deepslate_keep`; culture east; economy focus military,
mining.

**Voice and lines.** Voice `bass`, pitch 0.85, rate 3. Founding: "From today this land is the Still Crane's. Keep your
blades clean." Greeting: "{player}. Walk with purpose." Kill: "Rest." Yield: "...Well struck." Spared: "I will
remember this mercy."

**Own animations.** One per move, `ultimate`, `state.twin_sky` (draws the second sword), `phase.2` (puts on the helmet),
`phase.3`, `intro` (a bow, then hand to hilt), personal idles: hilt rest, eyes-closed breathing, re-tying the hair.

### 3.6 Tobimaru Usugumo: the quiet ash ninja (M, ninja, skirmisher, blight, lone wolf)

**Concept.** A silent scout who maps every valley alone and trusts no footprints but his own. His long red scarf is the
only colour he allows himself.

**Look.** Base `male`, build `slim`, scale 98. Short dark-navy high ponytail; a navy cloth half mask (lowered in phase
3, revealing his mouth for the first time); a long red scarf that trails (a secondary chain); a navy wrap top, fitted
wrapped trousers, arm guards, split-toe socks; a kunai holster on the thigh. A reverse-grip short blade in the right
hand, a kunai in the left. Palette: skin `#EFD2BE`, hair `#1C2233`, eyes `#9AA4B8`, primary `#1E2740`, secondary
`#B3262E`, accent `#4A4E57` (ash), metal `#AEB4BE`. Face: `narrow` eyes, lashes 0, `straight` brows, mouth hidden by the
mask until phase 3. Blink `calm`. Idle quirks: scarf adjust, crouching scan, kunai twirl.

**Kit.** Basic: twin-blade string; every fourth hit a blight bleed (1 per second, 3 s).

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `kunai_fan` | volley 3, range 14 (look `flint`) | light | mid, far | 0.4 / 0.2 / 0.4 | blight damage over time 1.5/s 4 s | 1-3 |
| `shadow_step` | self | heavy | any | 0.8 (smoke puff) / 0.1 / 0.8 (skipped when `backstab` follows) | Invisibility 3 s and Speed I 3 s; footstep puffs give him away; followup `backstab` 100% | 1-3 |
| `backstab` | melee | light | close | 0.4 / 0.1 / 0.5 | 120%; +60% more when behind the target; weight 0 (followup only) | 1-3 |
| `smoke_bomb` | nova, radius 4 | area | close | 1.0 / 0.1 / 0.8 | Darkness 3 s (players), Slowness I 3 s, hop back 5; prefer target close | 1-3 |
| `shadow_clones` | self, `spawn_add` decoy x2 | heavy | any | 0.8 / 0.2 / 0.8 | two decoys for 10 s; only he casts a shadow | 2-3 |
| `log_swap` | self, `spawn_add` decoy x1 + hop back 4 | light | close | 0.4 / 0.1 / 0.3 | a decoy left where he stood; prefer target close | 2-3 |
| `shadow_dash` | dash 7, repeatable | heavy | mid | 0.8 / 0.3 / 1.5 (kneels to reset) | 70% blight; followup itself 100% (a string of 4) | 3 |

Ultimate `nightfall` (1.5 / 1.6 / 1.5; a column of smoke): enemies within 10 get Darkness 4 s (players); he vanishes 2 s,
then a combo of 4 strikes, 60% blight each, on his target. Passive `unseen` (`on_kill`: Invisibility 2 s and Speed I).

**Boss.** Health scale 350, poise 80. Weak points: `light` (a torch or glowstone placed by a player within 4 blocks:
-10% for 10 s; hint "...Too bright.") and `recovery` (the kneel after the dash string).

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `kunai_fan`, `shadow_step` (+`backstab`), `smoke_bomb` | - |
| 2 | 60 | `shadow_clones`, `log_swap` (decoys) | "...You see me? Then see three." |
| 3 | 25 | mask lowered, `shadow_dash` string | "No more hiding." |

Reactions: target far -> `kunai_fan` (60%); blocking -> `shadow_step` (60%); eating -> `shadow_step` (70%). Enrage 180
s (240 as leader). Intro: "Leave. This is the only warning."

**Personality.** Lone wolf; a 55, h 50, m 40, c 60, g 40, s 10, l 55, v 30. Likes maps, compasses; dislikes bells.
Quirk: "counts his steps out loud when he thinks nobody is near."

**Leader.** Title "Head of the Quiet Ash". Faction names "Quiet Ash Clan", "The Ashen Veil"; short "Quiet Ash"; motto
"Leave no footprint." Colours primary `#4A4E57`, secondary `#B3262E`, accent `#1E2740`. Banner grey with
`stripe_downleft` red and `border` black. Base style `bamboo_village`; culture east; economy focus scouting, military.

**Voice and lines.** Voice `hat`, pitch 0.95, rate 3; few lines. Founding: "Quiet Ash. That is all you need to know."
Greeting: "..." / "{player}. You're loud." Kill: "Gone." Yield: "...Hm." Spared: "I owe you one footprint."

**Own animations.** One per move, `ultimate`, `phase.2` (a hand seal, smoke), `phase.3` (pulls the mask down), `intro`
(drops from above into a crouch), personal idles: scarf adjust, crouching scan, kunai twirl.

### 3.7 Ilsabet Frostmere: the rime vow knight (F, knight, vanguard, frost, righteous)

**Concept.** A knight-commander who swore to keep the weak behind her shield. She never strikes a kneeling foe and never
breaks her word, and she will hold a gate until the last of her people is inside.

**Look.** Base `female`, build `standard`, scale 104. Ash-blonde hair in a braided crown; light steel plate with a long
blue-and-white tabard bearing a frost-lily emblem; a short blue cape; greaves. A longsword in the right hand and a kite
shield with the emblem in the left. A visored helm snaps down in phase 2 (`p2_`); a planted standard appears on her back
in phase 3 (`p3_`), and the blade edge glows with frost. Palette: skin `#F3D9C9`, hair `#D9CDB4`, eyes `#7FB7D9`,
primary `#B8C7D6` (steel), secondary `#EEF3F7`, accent `#3D6FA8`, trim `#CFAF5A`, metal `#C9D1D9`, glow `#A8E6FF`.
Face: `almond` eyes, lashes 2, `straight` brows, `soft` mouth. Blink `normal`. Idle quirks: shield tap, salute, a glance
back at her allies.

**Kit.** Basic: sword-and-shield string (cut, cut, bash) with real shield blocking.

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `shield_bash` | dash 3 | heavy | close | 0.8 (shield raised high) / 0.2 / 0.8 | 70% + stun 1 s + knockback 2 | 1-3 |
| `oath_strike` | combo 2 | light | close | 0.45 / 0.35 / 0.4 | 60%, then 80% + freeze 2 s | 1-3 |
| `rime_wall` | nova, radius 5 (taunt), with a `to: allies` shield within 6 (a taunt in a friendly form would be left out) | light | any | 0.6 / 0.1 / 0.5 | allies shield 6 for 10 s; enemies taunted 3 s; prefer 2+ allies near | 1-3 |
| `turning_sweep` | sweep 4, 180 degrees, 1.0 s (the `sweep` minimum) | area | close | 1.0 / 1.0 / 0.8 | 70% frost | 1-3 |
| `frost_lance_charge` | charge 14, speed 170%, wall stun | heavy | mid, far | 1.0 / 0.8 / 1.0 (plants the shield to stop) | 100% + freeze 2 s + knockback 2 | 2-3 |
| `frozen_standard` | zone at self, radius 6, 10 s, pulse 1 s | area | any | 1.2 / 0.1 / 0.8 | allies inside +15% damage; enemies inside Slowness I | 3 |
| `call_the_guard` | self, `call_guards` 3 | light | any | 0.6 / 0.1 / 0.4 | three soldiers of her faction take her target | 3 |

Ultimate `vow_of_winter` (1.6 / 0.2 / 1.5; sword raised, a ring of ice crystals grows on the ground): a nova of radius 6,
14 frost and freeze 3 s; allies within 8 get a shield of 10. State `bulwark` (phase 2 on): armour +25%, knockback
resistance +30, her `back` weakness doubles. Passive `oath_keeper` (`on_ally_low_health` 35: that ally takes 20% less
damage for 6 s; cooldown 15 s).

**Boss.** Health scale 350 (400 as leader), poise 140. Weak points: `back` and `charge_wall`. With honour 90 she never
strikes a yielding enemy.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `shield_bash`, `oath_strike`, `rime_wall`, `turning_sweep` | - |
| 2 | 60 | visor down, state `bulwark`, `frost_lance_charge` | "Then I will stand against you properly." |
| 3 | 25 | `frozen_standard`, `call_the_guard` | "To me, Rime Vow! Hold the line!" |

Reactions: behind her -> `turning_sweep` (70%); drawing a bow -> `frost_lance_charge` (50%, phase 2 on); an allied hero
under half health -> `rime_wall` (80%). Enrage 240 s as leader, also when her heart is attacked. Intro: "Lower your
weapon and I will lower mine. I keep my word."

**Personality.** Righteous; a 50, h 90, m 50, c 60, g 15, s 60, l 85, v 25. Likes shields, iron ingots, white tulips;
dislikes wither roses. Quirk: "polishes her shield every night, whether it needs it or not."

**Leader.** Title "Knight-Commander of the Rime Vow". Faction names "Order of the Rime Vow", "The Frostmere Guard";
short "Rime Vow"; motto "No one falls while we stand." Colours primary `#5E9BD1` (frost blue: her `#3D6FA8` accent was
only 23 from the Still Crane's indigo), secondary `#EEF3F7`, accent `#CFAF5A`.
Banner light blue with `cross` white and `border` yellow. Base style `frost_hall`; culture west; economy focus
defence, people.

**Voice and lines.** Voice `chime`, pitch 1.15, rate 3. Founding: "By my oath, this is the Order of the Rime Vow. No one
here will be left behind." Greeting: "{player}. Are you well? Good. Stay that way." Kill: "It is done." Yield: "I yield.
I keep my word, so I expect you to keep yours." Spared: "You have honour. I will not forget it."

**Own animations.** One per move, `ultimate`, `state.bulwark`, `phase.2` (visor snaps down), `phase.3` (plants the
standard), `intro` (salute with the blade), personal idles: shield tap, salute, glance back.

### 3.8 Ambrose Nettlewick: the cauldron warlock (M, witch, controller, venom, eccentric genius)

**Concept.** A cheerful, absent-minded potion scholar who treats every fight as an experiment and takes notes while he
wins, or loses. His crow is unimpressed with all of it.

**Look.** Base `male`, build `slim`, scale 100. Messy medium hair, dark green-black; a wide-brimmed pointed hat, tilted,
with a patched band; round glasses; a long purple coat with a vial bandolier; dark trousers and boots; a crow on his
left shoulder (its own small bones for a head-turn idle; visual only); freckles. A gnarled staff with a small cauldron
hanging from it; in phase 2 broom bristles sprout from its end (`p2_`), in phase 3 his eyes glow green and the hat brim
lifts. Palette: skin `#EBCFB8`, hair `#23302A`, eyes `#7BCB5E`, primary `#5B3A86`, secondary `#1F1A26`, accent `#7BCB5E`,
trim `#C69C4E`, leather `#6B4A33`, glow `#9DFF6B`. Face: `wide` eyes, lashes 1, `bold` brows, `wide` mouth. Blink
`nervous`. Idle quirks: notebook scribble, vial shake, the crow pecks his hat.

**Kit.** Basic: a thrown vial (item look `slime_ball`), 4 venom every 1.5 s.

| Move | Form | Class | Band | Frames | Effects | P |
|---|---|---|---|---|---|---|
| `potion_barrage` | barrage 3, 0.4 s apart, `per_shot` | light | mid | 0.6 / 0.8 / 0.8 (reaching for new vials) | each 3 venom plus, in order: Poison I 4 s (green), Slowness II 3 s (grey), Weakness I 4 s (black): the colour is the tell | 1-3 |
| `cauldron` | zone at target, radius 3, delay 1.0, 6 s, pulse 1 s | area | mid | 1.0 / 0.1 / 0.8 | venom damage over time 2/s 3 s per pulse + Poison I | 1-3 |
| `hex_mark` | bolt, range 12 | light | mid, far | 0.5 / 0.1 / 0.4 | mark `hex`; at 3: Weakness II and Glowing 6 s | 1-3 |
| `fume_step` | self: hop back 5 and a nova of radius 3 where he stood | light | close | 0.4 / 0.1 / 0.4 | Slowness II 3 s; prefer target close | 1-3 |
| `broom_hops` | leap 8, radius 2, repeatable | heavy | any | 0.8 / 0.5 / 0.8 | 4 venom on landing and a small puddle zone (radius 2, 3 s, Slowness I); followup itself 100% (3 hops) | 2-3 |
| `great_brew` | self, channel 3 s (breaks at 15%), then rain 10, radius 6 | area | any | 1.0 / 3.0 + 2.0 / 2.0 | 5 venom per impact plus a random one of his three effects; if broken he staggers 2 s | 3 |

Ultimate `grand_experiment` (1.6 / 3.0 / 1.5; he lifts the cauldron, the crow caws: "Hypothesis: you lose!"): rain of 12
impacts, radius 7, 3 s, 4 venom and a random effect each; he cleanses himself. Passive `field_notes` (`on_hurt` 20%:
+5% damage for 15 s, up to 4 stacks: he is learning).

**Boss.** Health scale 350 (400 as leader), poise 80. Weak points: `channel` (the great brew; hint "Big one coming,
please don't interrupt!") and `recovery`.

| Phase | At | Adds | Transition line |
|---|---|---|---|
| 1 | 100 | `potion_barrage`, `cauldron`, `hex_mark`, `fume_step` | - |
| 2 | 60 | `broom_hops` (mobility and puddles) | "Fascinating! Let's test mobility!" |
| 3 | 25 | `great_brew` (a breakable channel) | "Time for the big one. Please don't interrupt." |

Reactions: target close -> `fume_step` (70%); blocking -> `cauldron` (60%); eating -> `hex_mark` (50%). Enrage 180 s
(240 as leader). Intro: "Ah, a test subject! I mean, a guest. Hold still, please."

**Personality.** Eccentric genius; a 35, h 60, m 75, c 55, g 50, s 55, l 50, v 70. Likes glass bottles, books, glow
berries; dislikes milk buckets ("they ruin everything"). Quirk: "names every potion after the person it was tested on."

**Leader.** Title "Archivist of the Nettlewick Conclave". Faction names "Nettlewick Conclave", "The Bubbling Academy";
short "Nettlewick"; motto "Hypothesis: we win. Let us test it." Colours primary `#4E7A2E` (nettle green: his coat purple
was only 22 from the Still Crane's indigo, and a purple banner would share its dye with the Veiled Lantern), secondary
`#5B3A86`, accent `#7BCB5E`. Banner green with `circle` purple and `gradient_up` black. Base style `dark_manor`; culture west; economy
focus lore, craft.

**Voice and lines.** Voice `pling`, pitch 1.05, rate 2. Founding: "Welcome to the Nettlewick Conclave! Please do not
drink anything labelled 'maybe'." Greeting: "{player}! Perfect timing, I need someone to hold this. Don't sniff it."
Kill: "Data acquired." Yield: "Fascinating... I lost. Noted." Spared: "Mercy! An unexpected variable. Thank you."

**Own animations.** One per move, `ultimate` (+`.loop`), `phase.2` (bristles sprout, a hop onto the staff), `phase.3`
(hat brim lifts, eyes glow), `intro` (tips the hat, adjusts the glasses), personal idles: notebook scribble, vial
shake, crow peck.

### 3.9 Assets per hero and shared assets the slice adds

| Asset | Per hero | Shared |
|---|---|---|
| Model | 80-130 cubes, at most 72 bones (33 required; Yugen's nine tails and lanterns, the largest, about 67) | `_fallback.geo.json`, the test rig |
| Texture | 128x128 (Natsuho, Yugen and Ambrose may need 256x128 for tails, lanterns and the crow), glow mask, `_p2` where a palette changes | neutral NPC skins and tint masks |
| Animations | 10-16 own | `_core` (about 45), 7 style libraries (about 14 each) |
| Server definition | about 10-20 KB | - |
| `visual.json` | move list, blink profile, mood overrides, voice | - |

---

## 4. Hero-led factions in the slice

- **Start**: `/fc start 256` drafts four leaders (design §5.2) from the 8 slice heroes, or the ones in
  `leaders.fixed`; the seed is printed. Each faction takes its identity from its leader (name, short name, slug,
  colours, banner, base style, seat, culture). With four factions the map radius must be at least 220; 256 is used.
- **Bases**: built in the leader's style (the 4 legacy families plus `shrine_pavilion`, `cherry_court`,
  `deepslate_keep`, `bamboo_village`, `frost_hall`, `dark_manor`), with real banners at the station and gates, signs
  with the short name, and the repair plan unchanged (accents excluded). New stone blocks join `MaterialRules`
  (polished diorite, smooth stone; the other six with milestone 2's styles).
- **People**: NPCs wear the neutral skins with a tabard or sash in the faction primary; recruits' names follow the
  leader's culture.
- **The leader**: spawns at the station with its founding line; keeps `court` duty (Natsuho and Benika spend daylight at
  their fishery and mine, Ambrose at the enchanting table once the base has one; Yugen's and Tobimaru's first talents
  belong to travelling jobs, trader and scout, which a leader never takes, so they patrol and stand at the station with
  the others); answers alarms; raids as its biases say; dies
  and returns after 15 minutes at the capital; announcements in its voice.
- **Decisions**: `LeaderBiases` sets the raid interval and first-raid delay, the counter-attack window and odds, the
  attack odds, the garrison share and minimum, the fallback point, whether the leader joins raids, the soldier cap,
  home swordsmen, ranged share, trader and enchanter thresholds, recruitment interval, food reserve, need order, house
  and storage thresholds, repair budget, trader trips and keep amounts, the miners' wanted gold and diamond counts, and
  the raid target cost
  (glory, territorial, loot, lairs, honour and ambush rules). The coalition rule against a conqueror stays as today.
- **Other heroes**: in the campaign runs the four non-leaders join factions with `/fc admin hero give <slug> <id>`, so
  non-leader heroes are tested working their duty, defending and raiding.
- **Old campaigns**: a format-2 world gets leaders on its first tick (the free slice hero nearest each legacy colour),
  keeps its names, and its NPCs get the tint layer.

---

## 5. Acceptance criteria

**Build and tests**
1. `./gradlew build`, the unit tests and the GameTests pass; the counts are reported (today 145 unit tests and 108
   GameTests per CLAUDE.md, plus the phase-1 additions, plus about 81 new GameTests).
2. The jar embeds GeckoLib; a dedicated server from `tools/setup_server.sh` and the Xvfb client run with only our jar.
3. `herogen build --check` is clean; `herogen check --slice` reports no errors; `./gradlew checkHeroes` loads all 8
   heroes with no NOT LOADED and no PART LEFT OUT (ADJUSTED and NOTE lines are listed in the report).

**Art and animation**

4. Each hero is within budget (at most 140 cubes and 72 bones; 128x128 or 256x128) and has all 12 eye, 8 mouth and 4
   cheek frames, its glow mask and its phase looks.
5. In game: blinking is visible (a GIF), the 12 moods switch, eyes go `focused` with no blinking during wind-ups and
   channels, the mouth moves while a line shows, the sash and NPC tabards show the faction colour.
6. Every own animation of section 3 plays in game; `HeroAssetsTest` confirms every `hit` marker equals its server frame
   within one tick.
7. The preview renderer's silhouettes match in-game screenshots within one pixel at 4x for the test rig poses.
8. The owner has seen the gallery of section 7 and either approves it or lists changes, which are made before batch 1.

**Boss fights**

9. A player's damage turns boss mode on (bar with name, title, phase pips and the ultimate mark; the intro title once
   per in-game day) and leaving for 10 s turns it off; the health share never jumps.
10. For each hero: the per-hit cap converts excess damage to poise; full poise staggers it for 2.5 s; phases begin at
    60% and 25% with the transition (super armour, shockwave, line, look) and the listed new mechanic.
11. Every heavy, area and ultimate move draws a growing ground decal for its whole wind-up and removes it afterwards;
    no decal outlives its hero (GameTest and viewer session).
12. Reactions fire: a raised shield, a drawn bow and eating each trigger the listed answer at least once in the viewer
    session or a GameTest.
13. Yield works both ways: sparing sends the hero home with no fighting for 10 minutes; finishing kills it and starts
    the revival timer.
14. Fairness: no hero hit takes a full-health player to 0 (GameTest); at most 2 heroes press one player (GameTest);
    knockback toward lava or a drop is reduced (GameTest).
15. `BossRulesTest.referenceDuel` computes each slice hero's boss health at level 10 against a reference player (iron
    sword, 1.6 swings per second, 50% of swings landing, vanilla armour reduction, weak points that an iron sword
    triggers): between 20 and 60 s of landed hitting for one player, as a leader and as a non-leader (a fight lasts two
    to three times that once wind-ups are dodged). With the design's numbers (health scale 350, +50 as leader, role
    factor of design §6.2) the expected values are:

    | Hero | Boss health (leader) | Hitting time (leader) |
    |---|---|---|
    | Natsuho (striker) | 129 (147) | 27 s (31 s) |
    | Yugen (marksman; iron weakness +15%) | 130 (148) | 24 s (27 s) |
    | Benika (vanguard) | 144 (165) | 31 s (36 s) |
    | Hinae (support) | 130 (148) | 27 s (31 s) |
    | Kagenori (striker; chainmail and iron boots) | 129 (147) | 34 s (39 s) |
    | Tobimaru (skirmisher) | 130 (148) | 27 s (31 s) |
    | Ilsabet (vanguard; before her shield blocks) | 144 (165) | 31 s (36 s) |
    | Ambrose (controller) | 130 (148) | 27 s (31 s) |

    (The earlier target of 45-150 s of hitting could not hold: without the role factor the same heroes gave 18-36 s.)

**Factions and leadership**

16. `/fc start 256` creates four factions whose names, colours, banners, base styles and station signs come from their
    leaders; the same seed reproduces the same cast; the draft rules hold (unit tests and the start log).
17. Leaders spawn at the station, keep `court` duty, answer alarms, and return 15 minutes after death at the capital.
18. Personality shows in the numbers (run A, section 6.4): the battle maniac launches at least twice as many raids as the
    sweet-natured leader by day 6; every faction's garrison share, soldier cap, ranged share and trader threshold, as
    printed daily by `sim.py`, equal the `LeaderBiases` values for its leader within rounding.
19. With `leaders.personalityStrength = 0`, `sim.py` prints today's constants for every faction's knobs; with
    `leaders.enabled = false` a 3-day run behaves like the legacy campaign (no leaders, legacy identities, the first
    raid not before 36000 ticks, the legacy recruitment order).
20. An old world (format 2, the day-19 world kept from earlier checks or a fresh copy) loads: legacy names kept, leaders
    assigned and announced, no errors.
21. Leaders announce raids, attacks on their base and their own return in their own lines.

**Non-combat**

22. In runs A and B every non-leader slice hero works its duty with today's jobs: Natsuho fishes real water, Benika
    mines, Ambrose enchants once the base qualifies, Tobimaru scouts and delivers reports, Yugen trades with villagers
    when a village is known, the three guards patrol.
23. The 15 milestone-1 talents have their tier numbers in `TalentsTest`, a GameTest of their effect each (3 in the main
    list of design §14.5 and 12 `talent*` GameTests after it), and show their tier numbers on the hero sheet.
24. Work animations play for mining, chopping, fishing and smithing (screenshots).

**Performance**

25. Tick time rises by at most 1 ms with 4 leaders and 4 more heroes (runs A and B against a run with
    `heroes.enabled = false` on the same seed).
26. Render cost with 8 heroes on screen is measured on the Xvfb client (target under 1.2 ms per frame of CPU) and a
    64-hero lineup is measured and recorded; the jar size is recorded (target under 4 MB for the slice).

**Docs and release**

27. README ("Heroes", "Factions and leaders", credits for GeckoLib), BALANCE ("Heroes", "Bosses", "Leaders",
    "Talents"), ROADMAP (phase 4 rewritten for heroes v2, status kept), HANDOFF (architecture map, how to make a hero,
    the screenshot flow) are updated; `docs/factionclash-common.toml` regenerated; jar and SHA-256 on README line 13;
    pushed per CLAUDE.md.

---

## 6. Tests and verification

### 6.1 Unit tests

Java: the v1 format tests (`HeroJsonTest`, `HeroReaderTest`, `HeroBalanceTest`, `HeroRulesTest`), `HeroReaderV2Test`,
`HeroBalanceV2Test`, `MoveSelectorTest`, `BossRulesTest` (including `referenceDuel`), `HeroActionsTest`,
`PersonalityTest`, `TemperamentsTest`, `LeaderBiasesTest`, `LeaderDraftTest`, `FactionNameGenTest`, `PaletteTest`,
`BannerDesignTest`, `TalentsTest`, `BuiltinHeroesTest` (the 8 load cleanly; no duplicate ids; the slice is 4 F and 4
M), `HeroAssetsTest`. Python: `tools/herogen/tests` and `roster/check_roster.py`.

### 6.2 GameTests

The list of design §14.5 (51 hero, boss, leader, talent and fixture-hero tests plus the 4 static ones: 55), on top of
v1's ability tests built in A3 and A4 (about 26), so about 81 new GameTests. Every job-running test has its own `batch`; static checks share `workers_static`; test bases go
through `GameTestBases.register` and are cleaned up; spawns are at known relative positions on the flat test world.

### 6.3 Asset checks

`herogen check --slice` (design §14.2) plus three negative tests in `tools/herogen/tests`: a marker moved by 3 ticks, a
model over the cube budget and a missing eye frame each fail with a clear message.

### 6.4 Campaign runs (HANDOFF section 4)

Server: `tools/setup_server.sh /srv/fc` (seed 20261107, RCON 25575). `sim.py` gains per-faction lines (leader,
temperament, identity, the `LeaderBiases` knob values, raids launched and their targets) and per-hero lines (duty work
counts, fights, boss fights, casts per move, phases reached, staggers, yields, spares, deaths, revivals).

| Run | Setup | Length | Checks |
|---|---|---|---|
| A | `[leaders] fixed = ["benika_akagane", "yugen_kagaribi", "hinae_shirasuzu", "kagenori_tsurumine"]`, `fc start 256`; then `fc admin hero give <slug> <id>` for Natsuho (to the White Bell), Ilsabet (to the Still Crane), Ambrose (to the Veiled Lantern), Tobimaru (to the Crimson Anvil) | 8 days (`tools/days.sh /srv/fc 8 runA.log`) | criteria 16-18, 21, 22, 25; no stuck hero (recoveries); every move of every hero cast at least once in field mode (`sim.py`) |
| B | `fixed = ["natsuho_komezaki", "ilsabet_frostmere", "ambrose_nettlewick", "tobimaru_usugumo"]`; the other four given as second heroes | 8 days | as A, with the tsundere, righteous, eccentric and lone-wolf leaders; the phase-1 items still pending a campaign check (woodcutters digging their own dirt, two miners sharing a mine) are checked here too |
| C | `leaders.personalityStrength = 0`, then `leaders.enabled = false` | 3 days each | criterion 19 |
| Old world | a format-2 world from an earlier campaign | 1 day | criterion 20 |
| Baseline | `heroes.enabled = false`, same seed | 2 days | tick time for criterion 25 |

### 6.5 Boss sessions on the real client

With the server up and the Xvfb `clientViewer` joined (HANDOFF section 4): `gamemode survival Viewer`, `effect give
Viewer resistance infinite 4 true`, `effect give Viewer regeneration infinite 2 true`; bring a hero of a running campaign next to the viewer
with `tp <hero uuid> Viewer`; engage it with `damage <hero uuid> 1 minecraft:player_attack by Viewer`; take screenshots at
wind-ups (decals), at the intro title, at each phase (`fc admin hero health <id> <percent>` jumps to 61%, 59%, 26%,
24%), at a stagger (`fc admin hero poise <id> full`), and at the yield (`fc admin hero health <id> 1`, then a strike
for "finish" and a sneak-use for "spare", the latter through `fc admin hero yield <id> spare` since the viewer cannot be
scripted to sneak-use). The server log line `[Heroes] reaction <id> <when> -> <move>` counts reactions (criterion 12):
the viewer raises a shield and draws a bow through `item replace` and `/fc admin hero target-state <id> blocking|bow|
eating` test hooks, which set the condition flags the reactions read.

### 6.6 Performance

`-Dfactionclash.heroRenderStats=true` in the client JVM arguments; `fc admin hero lineup 0 8 3` for the 8 heroes, and 64 decoy entities (which render the same models)
for the 64 case; average microseconds per hero logged every 600
frames. Server tick time from `sim.py` (as today).

### 6.7 Reviews

Each content task (C1-C4) ends with the review gates of design §14.4 (checks, contact sheet review by a separate
reviewer agent, in-game screenshots). V4 is the adversarial review of the whole slice (code and content), followed by
fixes and a re-run of the affected checks.

---

## 7. Screenshots for the owner

Delivered as `docs/heroes/slice/*.png` and GIFs (committed), a contact sheet, and a short gallery section in the
report to the owner:

1. **Lineup**: the 8 heroes side by side, front and back (2 shots, in game).
2. **Portraits**: each hero's face close-up in four moods (calm, blink midway, happy, angry) as a strip (8 strips, in
   game, plus the preview's face sheet).
3. **Signature moves**: for each hero, one wind-up with its ground decal and one hit frame (16 shots) and a GIF of the
   signature move (8 GIFs from the preview renderer).
4. **Phases**: each hero's phase 2 and phase 3 look, mid-transition (16 shots).
5. **Boss moments**: the intro title, the boss bar with pips, a stagger, the yield kneel (4 shots).
6. **Factions**: each capital with its banners, base style and the leader at the station (8 shots over runs A and B);
   the map screen and the station info screen with the leader section (2 shots).
7. **People at work**: workers in faction tabards; Benika mining, Natsuho fishing, Ambrose at the enchanting table (4
   shots).
8. **The contact sheet** from the preview renderer for all 8.

---

## 8. Order of work and checkpoints

| Checkpoint | Reached when | Shown to the owner |
|---|---|---|
| 1. Pipeline alive | A0, G1, G3, A1, A2 done: the test rig renders in game, blinks and walks | a GIF of the test rig blinking and walking, and the six base bodies |
| 2. First hero end to end | Natsuho (C1) complete with every move, phase and face in game, boss mode on | her screenshots and GIFs; the owner's early feedback on the look is folded in before C2-C4 |
| 3. All eight | C1-C4 done, review gates passed | the contact sheet and lineup |
| 4. Campaigns | V1-V3 done | the full gallery and the campaign numbers |
| 5. Release | V4-V5 done | the report: what changed, what was verified, what is left |

Parallel tracks from the start: F1-F4, G1 and L1-L3 need neither GeckoLib nor each other; A0 needs only the download
(and the owner's network approval if the host is refused again).

---

## 9. Risks specific to the slice

| Risk | Fallback |
|---|---|
| The GeckoLib host is refused again and cannot be allowed | the hand-written engine (design §4.1); decide at checkpoint 1 at the latest |
| The look does not meet the owner's expectation at checkpoint 2 | adjust base proportions, the painter and face templates before more heroes are made; this is why Natsuho is finished first |
| Software GL on Xvfb is too slow for GIF capture in game | GIFs come from the preview renderer; the game provides still screenshots |
| Boss numbers feel wrong in the viewer session | all numbers are PROPOSED config values; tune `boss.healthScale`, `maxHitShare` and poise defaults before release |
| A leader personality stalls a campaign | the draft's war-balance rule; runs A and B use contrasting casts on purpose |
| Display-entity decals misbehave (interpolation, clean-up) | particles-only telegraphs behind `boss.telegraphDecals = false` |
