# Heroes v2: roster plan (200 original heroes)

Status: plan, 2026-11-12. Companion of `HEROES_V2.md` (the design), `SLICE_PLAN.md` (the first milestone) and `MOBILITY_AND_WORK.md` (mobility and work mastery). The tables are generated from `roster/roster_data.py` by `roster/gen_roster_md.py`; `roster/check_roster.py` enforces every count and uniqueness rule below and prints `OK` for this version. `roster/roster.json` is the machine-readable copy that the hero scaffolder (`herogen new <n>`, HEROES_V2.md section 13.7) reads.

## 1. Rules this roster follows

- **Original characters only.** Every hero is built from generic archetypes (folklore creature types, occupations, genre roles, personality tropes). No existing character, franchise, game, anime or real person is named, described or imitated. What makes a hero original is the combination: archetype, personality, element, weapon, signature mechanic, silhouette, palette and name. Combinations that are the premise of a known work were avoided while drafting, and every row passes the originality review of section 8 in its batch before it ships.
- **Adults, tasteful outfits.** Every hero is an adult (petite builds included); outfits are modest, so the mod suits players of any age. Youthful looks are a style, not an age.
- **Exactly 100 female and 100 male heroes.** Each archetype has the gender split shown in section 2.3; archetypes that media usually codes one way have at least one hero of the other gender (shrine priests, a male fan dancer, butlers, female samurai and grapplers, and so on). Every role is split evenly (section 2.1).
- **No two heroes share a look and kit.** `check_roster.py` rejects a repeated look key (archetype + palette), kit key (archetype + weapon style + element), full key (archetype, role, style, element, temperament), signature line, name or id. At authoring time the format validator adds a second check over the finished kits (same archetype, element, style, ultimate form and signature move effects: HEROES_V2.md section 14.1).
- **Balance targets (enforced):** each role 28-30 heroes with a split within one; each element 24-26; each weapon style 9-17; each temperament 5-9; each of the 35 talents at least twice; every duel style present.
- **Working names.** Names follow the hero's culture (Japanese-style for yokai and Japanese archetypes, Chinese-style for wuxia, invented European-style for Western fantasy, mixed for modern ones). They are working names until the clearance step of section 8 passes for the batch.
- **Superhuman mobility, heroes only** (the owner's request of 2026-11-12, `MOBILITY_AND_WORK.md`): every hero has a primary mobility trait with a tier ceiling (I-III; III only for epic and legendary heroes) and may have a secondary of another family (tier I-II, at most the primary's). The trait fits the archetype (`check_roster.py` `ARCH_MOBILITY`) and the kit: a look with wings flies or glides, a signature that leaves the ground has an airborne trait, one that charges has `charger`. Flyers are a minority (10-15%, gender split within 3) and every flyer has a flight carrier (wings, broom, cloud, sword, paper crane, jets); worn wings are named in the look, and every glider names what it glides on (wings, a cape or cloak, fans, a kite, a wind sack, a parasol, a mushroom cap). Ordinary NPCs stay player-like.
- **Work mastery**: heroes work their duty better than ordinary NPCs (a proficiency every hero has) and their primary talent adds a much stronger specialty; the last column says what it is and what the hero's mobility does at that work (`MOBILITY_AND_WORK.md` part 2).
- **The slice heroes are marked [S]**; they are designed in full in `SLICE_PLAN.md`, their mobility in `MOBILITY_AND_WORK.md` section 15.

## 2. Balance summary (generated)

### 2.1 Combat roles

| Role | F | M | Total |
|---|---|---|---|
| vanguard | 15 | 14 | 29 |
| striker | 15 | 15 | 30 |
| skirmisher | 14 | 14 | 28 |
| marksman | 14 | 14 | 28 |
| support | 14 | 15 | 29 |
| controller | 14 | 14 | 28 |
| summoner | 14 | 14 | 28 |
| **total** | **100** | **100** | **200** |

`controller` (crowd control: slows, tethers, zones, stuns) and `summoner` (fights through summons, adds or called guards) are the two role presets format 2 adds to v1's five (HEROES_V2.md section 12.4).

### 2.2 Elements, weapon styles, duel styles, rarity

| Element | F | M | Total |
|---|---|---|---|
| physical | 11 | 14 | 25 |
| fire | 10 | 15 | 25 |
| frost | 15 | 11 | 26 |
| shock | 9 | 16 | 25 |
| venom | 15 | 10 | 25 |
| blight | 11 | 13 | 24 |
| arcane | 12 | 13 | 25 |
| radiant | 17 | 8 | 25 |
| **total** | **100** | **100** | **200** |

| Weapon style | F | M | Total |
|---|---|---|---|
| katana | 4 | 8 | 12 |
| greatblade | 7 | 2 | 9 |
| sword_shield | 4 | 6 | 10 |
| twin_blades | 5 | 6 | 11 |
| rapier | 5 | 4 | 9 |
| polearm | 6 | 9 | 15 |
| hammer | 10 | 3 | 13 |
| axe | 1 | 9 | 10 |
| scythe | 4 | 5 | 9 |
| fists | 7 | 8 | 15 |
| bow | 7 | 6 | 13 |
| crossbow | 8 | 7 | 15 |
| staff | 6 | 9 | 15 |
| focus | 11 | 6 | 17 |
| fan | 8 | 5 | 13 |
| instrument | 7 | 7 | 14 |
| **total** | **100** | **100** | **200** |

| Duel style | F | M | Total |
|---|---|---|---|
| rushdown | 17 | 20 | 37 |
| zoner | 20 | 19 | 39 |
| grappler | 4 | 2 | 6 |
| counter | 11 | 19 | 30 |
| trapper | 11 | 10 | 21 |
| all_rounder | 18 | 14 | 32 |
| stance | 5 | 2 | 7 |
| summoner | 14 | 14 | 28 |
| **total** | **100** | **100** | **200** |

| Rarity | F | M | Total |
|---|---|---|---|
| legendary | 6 | 4 | 10 |
| epic | 19 | 21 | 40 |
| rare | 42 | 36 | 78 |
| common | 33 | 39 | 72 |
| **total** | **100** | **100** | **200** |

Rarity sets the draw weight (leaders 4/4/2/1 for common/rare/epic/legendary, tokens 8/4/2/1) and the boss phase count (common and rare: 2 phases, or 3 when the file authors a third, there from level 1; epic and legendary: 3 phases; only legendary heroes may use `rebirth`). It does not change raw numbers beyond the boss `health_scale` default (HEROES_V2.md section 6.2). All eight slice heroes are epic so the slice exercises three phases.

### 2.3 Archetypes (54) and their gender split

| Group | Archetype | F | M | Default base style (leader) |
|---|---|---|---|---|
| A Animal-kin and yokai | cat | 3 | 2 | tide_port |
| A Animal-kin and yokai | fox | 2 | 3 | cherry_court |
| A Animal-kin and yokai | wolf | 2 | 2 | grove_lodge |
| A Animal-kin and yokai | tanuki | 1 | 2 | mud_town |
| A Animal-kin and yokai | moon_rabbit | 2 | 2 | ivory_palace |
| A Animal-kin and yokai | oni | 3 | 2 | ember_fortress |
| A Animal-kin and yokai | tengu | 2 | 2 | stone_hold |
| A Animal-kin and yokai | snow_spirit | 2 | 1 | frost_hall |
| A Animal-kin and yokai | kappa | 1 | 2 | tide_port |
| A Animal-kin and yokai | spider_weaver | 2 | 1 | dark_manor |
| A Animal-kin and yokai | east_dragon | 2 | 3 | coral_port |
| A Animal-kin and yokai | west_dragon | 2 | 2 | deepslate_keep |
| A Animal-kin and yokai | komainu | 1 | 2 | shrine_pavilion |
| A Animal-kin and yokai | plant_spirit | 2 | 2 | jungle_haven |
| B Shrine, court and martial Japan | shrine | 3 | 2 | shrine_pavilion |
| B Shrine, court and martial Japan | onmyoji | 2 | 2 | shrine_pavilion |
| B Shrine, court and martial Japan | samurai | 3 | 2 | deepslate_keep |
| B Shrine, court and martial Japan | wanderer | 2 | 2 | bamboo_village |
| B Shrine, court and martial Japan | ninja | 3 | 2 | bamboo_village |
| B Shrine, court and martial Japan | warrior_monk | 1 | 2 | stone_hold |
| B Shrine, court and martial Japan | fan_dancer | 2 | 1 | cherry_court |
| B Shrine, court and martial Japan | delinquent | 1 | 2 | mud_town |
| B Shrine, court and martial Japan | battle_chef | 1 | 2 | mud_town |
| B Shrine, court and martial Japan | storm_drummer | 1 | 2 | stone_hold |
| C Wuxia and East Asian myth | sword_cultivator | 2 | 2 | ivory_palace |
| C Wuxia and East Asian myth | martial_artist | 2 | 2 | mud_town |
| C Wuxia and East Asian myth | jiangshi | 2 | 1 | dark_manor |
| C Wuxia and East Asian myth | phoenix | 1 | 2 | desert_court |
| D Western fantasy | knight | 3 | 2 | stone_hold |
| D Western fantasy | paladin | 2 | 2 | ivory_palace |
| D Western fantasy | battle_priest | 2 | 2 | ivory_palace |
| D Western fantasy | witch | 3 | 2 | dark_manor |
| D Western fantasy | elf | 3 | 2 | grove_lodge |
| D Western fantasy | vampire | 2 | 2 | dark_manor |
| D Western fantasy | demon_lord | 2 | 2 | ember_fortress |
| D Western fantasy | angel | 2 | 2 | ivory_palace |
| D Western fantasy | reaper | 1 | 2 | dark_manor |
| D Western fantasy | necromancer | 1 | 2 | deepslate_keep |
| D Western fantasy | beast_tamer | 2 | 1 | grove_lodge |
| D Western fantasy | dragoon | 1 | 2 | stone_hold |
| D Western fantasy | berserker | 1 | 2 | grove_lodge |
| D Western fantasy | arbalist | 2 | 1 | stone_hold |
| D Western fantasy | corsair | 1 | 2 | coral_port |
| D Western fantasy | bard | 1 | 2 | cherry_court |
| D Western fantasy | alchemist | 2 | 1 | tuff_workshop |
| D Western fantasy | siren | 2 | 1 | coral_port |
| E Modern, pop and science fiction | idol | 2 | 2 | cherry_court |
| E Modern, pop and science fiction | magical | 2 | 2 | ivory_palace |
| E Modern, pop and science fiction | maid_butler | 2 | 2 | ivory_palace |
| E Modern, pop and science fiction | noble_fencer | 1 | 2 | ivory_palace |
| E Modern, pop and science fiction | android | 2 | 2 | tuff_workshop |
| E Modern, pop and science fiction | tactician | 2 | 1 | tuff_workshop |
| E Modern, pop and science fiction | smith | 1 | 2 | tuff_workshop |
| E Modern, pop and science fiction | grappler | 2 | 1 | mud_town |

### 2.4 Temperaments (30) and talents (35)

| Temperament | F | M | Total |
|---|---|---|---|
| hot_blooded | 5 | 4 | 9 |
| stoic | 4 | 4 | 8 |
| cool_headed | 4 | 3 | 7 |
| tsundere | 3 | 3 | 6 |
| shy | 6 | 0 | 6 |
| sweet_natured | 7 | 0 | 7 |
| yandere | 4 | 1 | 5 |
| genki | 5 | 2 | 7 |
| lone_wolf | 5 | 2 | 7 |
| proud_noble | 2 | 4 | 6 |
| delinquent | 2 | 4 | 6 |
| chuunibyou | 3 | 3 | 6 |
| airhead | 2 | 4 | 6 |
| big_sibling | 5 | 3 | 8 |
| trickster | 3 | 5 | 8 |
| sleepy | 3 | 3 | 6 |
| battle_maniac | 3 | 2 | 5 |
| gentle_giant | 2 | 5 | 7 |
| schemer | 3 | 4 | 7 |
| righteous | 3 | 4 | 7 |
| melancholic | 3 | 4 | 7 |
| glutton | 2 | 3 | 5 |
| perfectionist | 5 | 3 | 8 |
| charmer | 4 | 3 | 7 |
| ancient_sage | 3 | 3 | 6 |
| coward_who_rises | 3 | 3 | 6 |
| gruff_veteran | 1 | 7 | 8 |
| eccentric_genius | 2 | 6 | 8 |
| merchant | 1 | 4 | 5 |
| tyrant | 2 | 4 | 6 |
| **total** | **100** | **100** | **200** |

| Talent (primary specialty) | F | M | Total |
|---|---|---|---|
| aura | 10 | 2 | 12 |
| master_chef | 3 | 2 | 5 |
| forgemaster | 2 | 4 | 6 |
| surveyor | 2 | 2 | 4 |
| forester | 3 | 6 | 9 |
| harvest_blessing | 4 | 1 | 5 |
| beastmaster | 1 | 3 | 4 |
| angler_lord | 4 | 1 | 5 |
| ember_keeper | 1 | 1 | 2 |
| quartermaster | 4 | 3 | 7 |
| rally | 4 | 2 | 6 |
| mentor | 4 | 3 | 7 |
| renown | 2 | 4 | 6 |
| drill | 4 | 6 | 10 |
| festival_host | 3 | 3 | 6 |
| steward | 2 | 2 | 4 |
| mend | 5 | 1 | 6 |
| triage | 2 | 3 | 5 |
| revival_rite | 3 | 3 | 6 |
| purifier | 4 | 1 | 5 |
| lookout | 7 | 5 | 12 |
| cartographer | 2 | 3 | 5 |
| spymaster | 4 | 3 | 7 |
| pathfinder | 5 | 2 | 7 |
| envoy | 5 | 1 | 6 |
| intimidation | 1 | 4 | 5 |
| merchant_prince | 1 | 3 | 4 |
| tribute_master | 1 | 3 | 4 |
| sage_of_letters | 1 | 7 | 8 |
| architect | 1 | 2 | 3 |
| alchemist | 1 | 1 | 2 |
| engineer | 1 | 2 | 3 |
| warden_of_walls | 1 | 7 | 8 |
| tactician | 1 | 3 | 4 |
| war_drummer | 1 | 1 | 2 |
| **total** | **100** | **100** | **200** |

Each hero's primary specialty is listed; the finished hero file adds one or two more talents (at most 3, HEROES_V2.md section 7.2). `alchemist` and `engineer` stay inactive until the brewing and rail jobs exist (ROADMAP phases 2 and 3); the heroes that carry them get a working second talent.

### 2.5 Mobility (primary trait, tier ceiling, secondary)

| Primary mobility | F | M | Total |
|---|---|---|---|
| runner | 21 | 23 | 44 |
| charger | 17 | 20 | 37 |
| leaper | 12 | 9 | 21 |
| acrobat | 14 | 9 | 23 |
| climber | 5 | 4 | 9 |
| glider | 5 | 8 | 13 |
| flyer | 14 | 13 | 27 |
| hover | 8 | 8 | 16 |
| swimmer | 4 | 6 | 10 |
| **total** | **100** | **100** | **200** |

| Primary | Ceiling I | Ceiling II | Ceiling III | Secondaries it carries |
|---|---|---|---|---|
| runner | 14 | 26 | 4 | climber 7, swimmer 7, charger 6, acrobat 5, leaper 4, snowstride 2, ember_stride 1, none 12 |
| charger | 6 | 27 | 4 | runner 8, leaper 7, ember_stride 3, swimmer 3, snowstride 2, climber 1, none 13 |
| leaper | 2 | 16 | 3 | charger 6, acrobat 4, runner 4, glider 2, hover 1, none 4 |
| acrobat | 1 | 17 | 5 | climber 9, runner 5, leaper 3, snowstride 2, swimmer 2, glider 1, none 1 |
| climber | 0 | 8 | 1 | acrobat 5, runner 1, none 3 |
| glider | 2 | 9 | 2 | climber 6, runner 4, acrobat 1, leaper 1, none 1 |
| flyer | 2 | 17 | 8 | ember_stride 5, charger 3, swimmer 2, climber 1, none 16 |
| hover | 4 | 9 | 3 | runner 3, snowstride 3, acrobat 1, charger 1, none 8 |
| swimmer | 1 | 7 | 2 | charger 2, runner 2, acrobat 1, climber 1, leaper 1, none 3 |

**Flyers** (27, 13% of the roster; 14 F, 13 M) and what carries them: 27 Kagehisa Tobikiri (crow wings); 28 Tsubame Kakeyama (crow wings); 29 Shizuki Minekaze (crow wings); 30 Ojin Atagomaru (crow wings); 40 Mizuchi Haruaki (cloud); 42 Raishin Kumokiri (cloud); 43 Seika Ryugasaki (cloud); 46 Ignatia Vermillor (dragon wings); 48 Aurelian Wyrmholt (dragon wings); 63 Kotoyo Kamo (paper crane); 94 Lin Mingxuan (flying sword); 105 Feng Zhaoming (ember wings); 106 Huang Jinlu (ember wings); 107 Zhu Yanchi (ember wings); 113 Celestine Brightwater (light wings); 121 Ambrose Nettlewick (broom); 122 Pippa Hexley (broom); 123 Hazel Brambleworth (broom); 131 Ottavia Sanguine (bat wings); 135 Belphira Ashenthrone (demon wings); 139 Amariel Lumen (feathered wings); 140 Ishara Morn (feathered wings); 141 Ariston Highgate (feathered wings); 142 Halvard Skye (feathered wings); 177 Momoka Akebono (star wings); 180 Akito Hoshinaga (star wings); 191 Tetsu-9 (jets).

The tier is a ceiling, not an unlock gate (the owner's answer of 2026-11-12, "all abilities but not full power"): a hero has every mobility feature up to its ceiling from level 1, and its numbers grow from 60% at level 1 to 100% at level 20 (`MOBILITY_AND_WORK.md` section 1.1; the slice heroes by level in 15.9). Tier III is reserved for epic and legendary heroes.

## 3. Column legend

**Weapon styles.** The style picks the shared animation library (HEROES_V2.md section 13.6) and the mechanical vanilla item that keeps damage, enchantments, shields and armour on vanilla rules. The prop is what the model draws.

| Style | Mechanical item | Animation family |
|---|---|---|
| `katana` | iron sword | one-handed long blade: draw, slash strings, quick-draw cuts, sheathe |
| `greatblade` | iron sword | two-handed great sword or long field blade: wide arcs, overheads, slow recoveries |
| `sword_shield` | iron sword + shield | blade and shield: guard, bash, short cuts, shield charge |
| `twin_blades` | iron sword | a pair of short blades (daggers, cleavers, kunai): fast strings, reverse grip |
| `rapier` | iron sword | thrusting blade or whip-sword: lunges, ripostes, flicks |
| `polearm` | trident | spear, glaive, lance, trident: thrusts, sweeps, vaults, throws |
| `hammer` | mace | club, mallet, maul, pan: overheads, ground slams, wind-up swings |
| `axe` | iron axe | hand axes, greataxe, cleaver, spade: chops, throws, spins |
| `scythe` | iron axe | scythe, sickle, chain sickle: reaping sweeps, hooks, pulls |
| `fists` | iron sword, drawn as fists | gauntlets, claws, bare hands, kicks: combos, grabs, throws |
| `bow` | bow | long or short bow: draw, volley, kneeling aim |
| `crossbow` | crossbow | hand, heavy or repeating crossbow and gun-like props: aim, fire, reload |
| `staff` | thrown projectile + staff prop | two-handed staff, wand-staff, broom: casting, spins, bonks |
| `focus` | thrown projectile + held prop | one-handed caster prop (talismans, lantern, tome, mirror, flasks) |
| `fan` | thrown projectile + fan prop | war or dance fans, ribbons: dance steps, gusts, cuts |
| `instrument` | thrown projectile + instrument prop | drum, lute, harp, horn, flute, microphone: beats, chords |

**Duel styles** (`behaviour.duel_style`, how a one-on-one fight with a player feels; HEROES_V2.md section 12.3):

- `rushdown`: never stops coming: short strings and dashes; punish after a whiffed ender.
- `zoner`: keeps you out with projectiles; close the gap to win.
- `grappler`: grabs, throws and slams; stay out of reach or punish a missed grab.
- `counter`: patient; guard stances answer melee; bait the stance, hit after the swing.
- `trapper`: fills the arena with zones, marks and tethers; strike between set-ups.
- `all_rounder`: a projectile, a rush and an anti-air; no single weakness.
- `stance`: switches movesets (stance or transformation); punish the switch.
- `summoner`: fights through allies or summons; kill them or dive the summoner.

**Mobility** (`MOBILITY_AND_WORK.md` section 1; the numbers are the full values, at level 20, of ceilings I / II / III, and a hero has every feature up to its ceiling from level 1):

- `runner`: fleet of foot: faster travel, steps up blocks, crosses soul sand and honey unslowed.
- `charger`: heavy ground mover: sure footing, knockback resistance on the move, traversal charges, shrugs off cobwebs.
- `leaper`: big bounds: jumps 1.8 / 2.5 / 3.5 blocks high, leaps 5 / 6 / 8-block gaps, lands long falls.
- `acrobat`: air jumps (double, then triple), wall kicks, mantles, takes no fall damage at III.
- `climber`: climbs walls and trunks (6 / 16 / any blocks), clings and works while clinging.
- `glider`: glides from heights (2.3 / 3.3 / 5 blocks forward per block of drop), gusts at II-III.
- `flyer`: true flight with a stamina bar (30 / 45 / 60 s), lands to strike, can be shot down.
- `hover`: floats 0.5 / 1 / 1.5 blocks above ground or water, drifts over gaps, never tramples farmland.
- `swimmer`: fast swimming, long breath, dives and mines under water (II), breathes water (III).
- `snowstride`: (secondary) never freezes, powder snow does not slow it; faster on snow and ice at II.
- `ember_stride`: (secondary) fire- and lava-proof; walks on lava like a strider at II.

**Work mastery** (`MOBILITY_AND_WORK.md` part 2): every hero works its duty with a proficiency (work speed +15/25/35%, a larger backpack, longer reach, less tool wear and hunger); the column gives the duty, the specialty its primary talent adds when the hero does that work itself (guard and court talents instead count one tier higher while the hero is home, plus a personal effect), and what its mobility does at that work. Ranges such as +1-3 are tiers I-III.

**Specialty** is the hero's primary non-combat talent (HEROES_V2.md section 7.2), then the duty it works when it is not leading (`court` replaces the duty while it leads). **R** is the rarity (L, E, R, C). **B** is the content batch (S = the slice, 1-8 = section 6). **Leader seed** is the faction group noun its temperament suggests and the base style family its archetype suggests; the hero file's `leader` block has the final faction names, motto, colours and banner (HEROES_V2.md section 5.3).

## 4. The vertical slice (8 heroes, 4 female and 4 male)

| # | Name | G | Archetype | Role | Style | Element | Temperament | Mobility | Faction it leads (SLICE_PLAN.md) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Natsuho Komezaki | F | cat | striker | fists | physical | tsundere | acrobat III + climber I | Bellwhisker Harbour |
| 6 | Yugen Kagaribi | M | fox | marksman | focus | arcane | schemer | runner III + leaper I | Veiled Lantern Exchange |
| 22 | Benika Akagane | F | oni | vanguard | hammer | fire | battle_maniac | charger III + leaper I | Crimson Anvil Warband |
| 56 | Hinae Shirasuzu | F | shrine | support | focus | radiant | sweet_natured | runner II + swimmer I | Hearth of the White Bell |
| 65 | Kagenori Tsurumine | M | samurai | striker | katana | shock | stoic | leaper II + runner I | House of the Still Crane |
| 74 | Tobimaru Usugumo | M | ninja | skirmisher | twin_blades | blight | lone_wolf | acrobat III + climber II | Quiet Ash Clan |
| 108 | Ilsabet Frostmere | F | knight | vanguard | sword_shield | frost | righteous | charger III + snowstride I | Order of the Rime Vow |
| 121 | Ambrose Nettlewick | M | witch | controller | staff | venom | eccentric_genius | flyer II | Nettlewick Conclave |

They cover all eight elements, five of the eight duel styles (rushdown, zoner, grappler, counter, trapper), six of the seven roles (two vanguards and two strikers in different duel styles; the summoner role is first exercised by batch 1), eight different temperaments chosen for contrast as leaders, and seven weapon styles (fists, focus, hammer, katana, twin_blades, sword_shield, staff). Their primary mobility covers five families (acrobat, runner, charger, leaper and one flyer, Ambrose on his staff-broom) and their secondaries three more (climber, swimmer, snowstride), so milestone 1 proves flight, climbing and air jumps before any batch is authored.

## 5. The 200 heroes

### 5.1 Animal-kin and yokai (55 heroes: 27 F, 28 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | Mobility | R | B | Signature | Look (silhouette; palette) | Leader seed | Work mastery (duty: specialty; mobility at work) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Natsuho Komezaki **[S]** | F | cat | striker | fists: calico claw gauntlets | physical | tsundere | angler_lord, fisherman | rushdown | acrobat III + climber I | E | S | Pounce that bleeds; at 30% 'nine lives' rolls away with a shield and speed | bob cut, tufted ears, bell choker; calico white/orange/black | League · tide_port | fisherman: Luck of the Sea and Lure +1-3; hops rock to rock and climbs pier pilings to deep water |
| 2 | Shirotae Mikage | F | cat | controller | focus: silver yarn spools | arcane | sleepy | pathfinder, scout | trapper | climber II + acrobat I | E | 1 | Cat's-cradle threads tether foes between two marks; naps to heal (breakable channel) | long white low tails, droopy ears, big cardigan; white/lavender | Lodge · tide_port | scout: scouts 15-35% faster, never loses the way home; climbs cliffs for a lookout |
| 3 | Kuroha Yamikane | F | cat | marksman | crossbow: hand crossbow | blight | lone_wolf | lookout, guard | zoner | climber II + acrobat I | C | 2 | Night eyes: marked foes glow and take more from her bolts in darkness | hooded black coat with ear slits, one fang; black/gold | Pack · tide_port | guard: talent a tier up while home; alarm +16 blocks from high ground; climbs walls and works clinging |
| 4 | Rokusuke Amanawa | M | cat | summoner | twin_blades: claw knives and a bell whistle | shock | delinquent | beastmaster, animal_carer | summoner | acrobat II + runner I | R | 1 | Alley king: three street cats answer his whistle; static fur shocks whoever hits him | broad tabby, headband, bell on a cord; grey tabby/red | Crew · tide_port | animal_carer: grows calves by feeding them, 2-4 pairs a round, extra wool; vaults the pen fence instead of the gate |
| 5 | Torakichi Fumizuki | M | cat | skirmisher | twin_blades: curved claw daggers | venom | trickster | spymaster, scout | rushdown | acrobat II + climber I | C | 2 | Afterimage pounce: three dashes, each leaves a smoke decoy | messy hair, torn ear, striped scarf; tuxedo black/white/green | Revel · tide_port | scout: nears rival walls unseen, names the heroes it sees; double-jumps ravines |
| 6 | Yugen Kagaribi **[S]** | M | fox | marksman | focus: foxfire paper lanterns | arcane | schemer | merchant_prince, trader | zoner | runner III + leaper I | E | S | Foxfire volleys; tails 3, 6, 9 by phase; decoys that cast no shadow | silver half-up hair, fox mask on the side, plum court robe; plum/silver | Circle · cherry_court | trader: prices -10 to -30%, carries 30-36 slots; village trips at a run, hops field fences |
| 7 | Inaho Kogane | F | fox | support | fan: golden dance fans | fire | ancient_sage | harvest_blessing, farmer | all_rounder | glider III + runner I | L | 3 | Fox-wedding rain: sunshower zones heal allies and scorch foes, stronger in rain | red-gold hair, four fluffy tails, rice-straw hairpin; vermilion/white/gold | Conclave · cherry_court | farmer: Fortune +1-3 on crops, replants as it reaps; glides down from heights to its work |
| 8 | Kazato Kurobi | M | fox | skirmisher | polearm: cross-bladed spear | shock | genki | pathfinder, scout | rushdown | runner II + acrobat I | R | 3 | Zigzag dashes: three Z-pattern spear thrusts, ears perk before each | spiky black hair, crimson tail tips, short coat; black/crimson | Troupe · cherry_court | scout: scouts 15-35% faster, never loses the way home; covers ground at a run |
| 9 | Sayoe Kagamura | F | fox | controller | focus: bronze hand mirror | blight | charmer | envoy, guard | trapper | runner II + leaper I | E | 4 | Mirror decoys repeat her casts; the real one carries the cracked mirror | black hime cut, black ears with white tips; black/plum/silver | Salon · cherry_court | guard: talent a tier up while home; pacts carried in person +0.1-0.2; answers alarms at a run |
| 10 | Hakuen Toriimae | M | fox | summoner | staff: ringed priest staff | radiant | big_sibling | revival_rite, guard | summoner | leaper II + runner I | R | 4 | Paper fox familiars; a staff ring calls them back to shield him | white fox priest, red eye-liner marks; white/red/gold | Household · cherry_court | guard: talent a tier up while home; revived allies rise with absorption; bounds up to the wall walk |
| 11 | Torvald Ashpelt | M | wolf | summoner | axe: bearded axe | frost | gruff_veteran | beastmaster, animal_carer | summoner | runner II + snowstride I | C | 5 | Two named wolves flank his marks; if both fall he staggers (broken bond) | grey shaggy hair, scar, frost-rimed fur cloak; grey/navy | Hold · grove_lodge | animal_carer: grows calves by feeding them, 2-4 pairs a round, extra wool; errands at a run, steps up blocks |
| 12 | Ylva Rimeclaw | F | wolf | striker | greatblade: bone-hilted greatsword | frost | hot_blooded | forester, woodcutter | rushdown | runner III + snowstride II | E | 5 | Moon frenzy: at night or under half health she grows, locks skills and never stops swinging | white wild hair, amber eyes, snow-fur mantle; white/amber | Vanguard · grove_lodge | woodcutter: Fortune +1-3 on leaves (saplings, apples); runs between far groves |
| 13 | Rusca Brannick | F | wolf | skirmisher | bow: recurve hunting bow | venom | lone_wolf | cartographer, scout | zoner | runner II + leaper I | C | 6 | Scent marks leave glowing tracks; she dashes to the most-marked foe | russet braid, notched ear, hunter's hood; russet/black | Pack · grove_lodge | scout: scouts 32-64 blocks farther, maps 5x5 sectors; covers ground at a run |
| 14 | Hyogo Arashiba | M | wolf | support | instrument: bone war horn | fire | yandere | rally, guard | all_rounder | runner II + charger I | R | 6 | Den horn: a howl strengthens his pack; whoever wounds his favoured ally becomes his only target | charcoal mane, broad build, bone horn on a strap; charcoal/ember orange | Covenant · grove_lodge | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; answers alarms at a run |
| 15 | Tonbei Morikawa | M | tanuki | support | instrument: belly drum and gourd | physical | glutton | master_chef, chef | all_rounder | charger II + swimmer I | C | 7 | Belly drum: a booming ring knocks foes back and speeds allies; two belly pats are the tell | round build, straw hat, leaf on his head; brown/leaf green | Guild · mud_town | chef: smoker 20-40% faster, 15-35% chance of a second dish; sure-footed under heavy loads |
| 16 | Mamemi Tsuzumi | F | tanuki | vanguard | hammer: giant gourd mallet | venom | glutton | merchant_prince, trader | grappler | leaper II + charger I | R | 7 | Giant form in phase 2 shrugs off knockback; a belly-flop slam; sake-mist puddles | short brown bob, round ears, oversized merchant coat; brown/teal | Guild · mud_town | trader: prices -10 to -30%, carries 30-36 slots; bounds over fences and streams on village trips |
| 17 | Kobun Hachiya | M | tanuki | summoner | fan: leaf fan | arcane | airhead | quartermaster, hauler | summoner | glider II + runner I | C | 8 | Leaf fan conjures three tanuki helpers that bite, then pop like decoys | huge straw hat, round belly sash; brown/red | Parade · mud_town | hauler: carries 30-36 slots, sorts 25-55% faster; glides down from the walls to loot |
| 18 | Kinue Mochizuki | F | moon_rabbit | skirmisher | hammer: rice-cake mallet | arcane | genki | master_chef, chef | rushdown | leaper II + acrobat I | R | 8 | Moon drop: a huge leap with a growing shadow; the mallet sticks after | long white hair, one floppy ear, short kimono; white/pink | Troupe · ivory_palace | chef: smoker 20-40% faster, 15-35% chance of a second dish; bounds over gaps instead of bridging |
| 19 | Tsukime Hanagata | F | moon_rabbit | summoner | staff: mochi-pounding pestle staff | radiant | shy | harvest_blessing, farmer | summoner | leaper II + runner I | C | 1 | Moon-bunny helpers pound rice cakes that heal allies and kick foes | silver twin buns, ribbons on the ears, kimono apron; silver/peach | Commune · ivory_palace | farmer: Fortune +1-3 on crops, replants as it reaps; hops the irrigation ditches |
| 20 | Shogetsu Tobita | M | moon_rabbit | marksman | bow: short moon bow | frost | coward_who_rises | lookout, guard | zoner | acrobat II + leaper I | R | 1 | Crescent volley lands as a crescent of frost zones; jumpy until an ally falls, then steady | tall white ears, navy hooded cloak; navy/white | Fellowship · ivory_palace | guard: talent a tier up while home; alarm +16 blocks from high ground; vaults to any alarm |
| 21 | Tetsuhei Hanetsuki | M | moon_rabbit | striker | katana: moon-steel katana | shock | hot_blooded | drill, guard | rushdown | leaper II + acrobat I | C | 2 | Hopping draw-cuts: each leap ends in a falling slash, higher each time | short black hair, black ears, track jacket over a sash; black/red | Vanguard · ivory_palace | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 22 | Benika Akagane **[S]** | F | oni | vanguard | hammer: iron-studded club | fire | battle_maniac | surveyor, miner | grappler | charger III + leaper I | E | S | Overhead club slam (the club sticks); phase 2 rage; phase 3 grab and throw | single red horn, wild black hair, crimson coat over wraps; crimson/black | Warband · ember_fortress | miner: Fortune +1-3 on the ores it mines; takes mine stairs loaded without slowing |
| 23 | Sekka Aoizumi | F | oni | vanguard | greatblade: cleaver-sized long blade | frost | gruff_veteran | architect, builder | all_rounder | charger II + snowstride II | E | 2 | Frost-breath cone, then she plants the blade; ice armour that breaks after enough hits | twin small blue horns, white hair, indigo trousers; ice blue/indigo | Hold · ember_fortress | builder: builds 20-40% faster, places 2-4 blocks up without scaffolds; carries full loads up steps |
| 24 | Kiyome Hanakado | F | oni | skirmisher | fists: iron bracers | physical | hot_blooded | quartermaster, hauler | rushdown | charger II + runner I | R | 3 | Rolling shoulder charges; slam her into a wall to stagger her | two short yellow horns, high ponytail, rope belt; mustard/charcoal | Vanguard · ember_fortress | hauler: carries 30-36 slots, sorts 25-55% faster; sure-footed under heavy loads |
| 25 | Ganzo Ishidoro | M | oni | vanguard | sword_shield: temple-door shield and club | physical | gentle_giant | architect, builder | counter | charger II | C | 3 | Door-shield wall: the planted door shelters allies; attack his sides | huge build, two curved green horns, straw cape; moss green/brown | Haven · ember_fortress | builder: builds 20-40% faster, places 2-4 blocks up without scaffolds; carries full loads up steps |
| 26 | Gonroku Sakabayashi | M | oni | skirmisher | axe: twin hatchets and a gourd | venom | delinquent | renown, guard | rushdown | runner II + charger I | R | 4 | Throwing hatchets on an off-beat rhythm; a poisoned mist cone from his gourd | blue skin tint, one broken horn, open festival coat; blue/white | Crew · ember_fortress | guard: talent a tier up while home; Looting +1-3 on its own kills; answers alarms at a run |
| 27 | Kagehisa Tobikiri | M | tengu | skirmisher | fan: feather war fan | physical | proud_noble | cartographer, scout | zoner | flyer II | C | 5 | Gale-fan cones push foes; dives from above onto a landing marker | black crow wings, small black cap, one-tooth clogs; black/red | Court · stone_hold | scout: scouts 32-64 blocks farther, maps 5x5 sectors; scouts from the air, sees farther |
| 28 | Tsubame Kakeyama | F | tengu | marksman | bow: mountain longbow | shock | lone_wolf | lookout, guard | zoner | flyer II | R | 4 | Arrows call harmless lightning flashes on marked foes, then a bolt chain | red long-nosed mask pushed up, white hair, crow wings; red/white | Pack · stone_hold | guard: talent a tier up while home; alarm +16 blocks from high ground; patrols above its own walls |
| 29 | Shizuki Minekaze | F | tengu | support | fan: feather fan | frost | stoic | mend, guard | all_rounder | flyer I | C | 5 | Healing gusts from her feather fan carry cleanses along a line; meditates mid-fight (breakable) | pom-pom sash, small cap, white robe, grey wings; white/sage | Watch · stone_hold | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; patrols above its own walls |
| 30 | Ojin Atagomaru | M | tengu | vanguard | polearm: feathered glaive | shock | righteous | warden_of_walls, guard | counter | flyer II + charger I | E | 6 | Wind wall turns arrows aside while he advances; get round it | red long-nosed mask, huge brown wings, monk armour; brown/gold | Order · stone_hold | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; patrols above its own walls |
| 31 | Sayuki Shimotsuki | F | snow_spirit | controller | fan: white frost fan | frost | melancholic | purifier, guard | trapper | hover II + snowstride II | C | 6 | Whiteout: freezing zones and a breath cone; she drifts back as you close in | very long white hair, snowflake kimono, floating hem; white/ice blue | Vigil · frost_hall | guard: talent a tier up while home; also cleanses member players; drifts over water, mud and gaps |
| 32 | Koyuki Wataboshi | F | snow_spirit | summoner | focus: snow lanterns | frost | coward_who_rises | quartermaster, hauler | summoner | hover I + snowstride I | R | 7 | Snow children (snow golems drawn as sprites) shield her; she turns brave when they fall | short white bob, snow-rabbit hood, mittens; white/pink | Fellowship · frost_hall | hauler: carries 30-36 slots, sorts 25-55% faster; drifts over water, mud and gaps |
| 33 | Itetsu Shirane | M | snow_spirit | marksman | polearm: ice javelins | frost | melancholic | forester, woodcutter | zoner | hover III + snowstride I | E | 7 | Hurls ice javelins that leave frozen ground; a glacier-skin shell when cornered | tall, pale, crown of ice shards; pale blue/grey | Vigil · frost_hall | woodcutter: Fortune +1-3 on leaves (saplings, apples); drifts over water, mud and gaps |
| 34 | Genkichi Kawabe | M | kappa | vanguard | sword_shield: turtle-shell shield and short spear | frost | big_sibling | drill, guard | counter | swimmer II + charger I | R | 8 | Shell wall; a critical hit from above spills his head-dish and staggers him | green-tinted skin, shell on the back, dish on his head; green/yellow | Household · tide_port | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; guards the shore and the water gate |
| 35 | Minamo Kawazoe | F | kappa | skirmisher | polearm: fishing trident | frost | genki | angler_lord, fisherman | rushdown | swimmer II + runner I | C | 8 | Slides fast along water; reels foes in with a fishing line (tether) | teal bob with a dish hairclip, shell backpack, raincoat; teal/yellow | Troupe · tide_port | fisherman: Luck of the Sea and Lure +1-3; swims to the deepest water |
| 36 | Soan Kawaguchi | M | kappa | support | staff: lotus staff | venom | gentle_giant | triage, guard | all_rounder | swimmer II | R | 1 | Bone-setter: heals by holding an ally still; slime puddles slow foes | lotus-leaf hat, shell, medicine box; green/white | Haven · tide_port | guard: talent a tier up while home; carries its own 2-4 triage meals; guards the shore and the water gate |
| 37 | Ayaori Kumode | F | spider_weaver | controller | focus: silk threads | venom | yandere | aura (hardy), hauler | trapper | climber III | E | 1 | Web fields tether; six spectral legs wind up an unblockable grab | black-and-gold kimono, six thin legs on the back, hime cut; black/gold | Covenant · dark_manor | hauler: own hardy +2-4 stacks over the cap; climbs for loot stuck on roofs |
| 38 | Itoha Kurozome | F | spider_weaver | skirmisher | twin_blades: silk-bound sickles | blight | schemer | spymaster, scout | trapper | climber II + acrobat I | R | 2 | Drops on a thread from above and retreats up it; hidden traps | pale, black braid, eye-motif hair ornaments; black/violet | Circle · dark_manor | scout: nears rival walls unseen, names the heroes it sees; climbs cliffs for a lookout |
| 39 | Joroku Itosaki | M | spider_weaver | summoner | scythe: silk-reel sickle | venom | tyrant | tribute_master, trader | summoner | climber II | C | 2 | Spiderling broods hatch from web sacs; reeling silk drags them onto his mark | tall, white hair with a black streak, grey robes; grey/black | Dominion · dark_manor | trader: prices -10 to -20%, larger tribute loads; climbs over cliffs on village trips |
| 40 | Mizuchi Haruaki | M | east_dragon | support | staff: cloud-pearl staff | frost | ancient_sage | harvest_blessing, farmer | all_rounder | flyer III + swimmer II | L | 3 | An orbiting pearl heals allies it passes and strikes foes | long antlered horns, flowing jade hair, whiskers; jade/gold | Conclave · coral_port | farmer: Fortune +1-3 on crops, replants as it reaps; rides its cloud over terraced fields, lands without trampling |
| 41 | Shotatsu Korin | M | east_dragon | striker | katana: dragon-tooth sword | fire | merchant | forgemaster, blacksmith | all_rounder | swimmer III + leaper I | E | 4 | Pearl-roar cone; coils with super armour before a spinning cleave | red antlers, red scales on the cheeks, golden robe; red/gold | Company · coral_port | blacksmith: crafts 25-55% faster, repairs 30-40% per material; swims rivers and lakes, never detours |
| 42 | Raishin Kumokiri | M | east_dragon | marksman | bow: cloud bow | shock | cool_headed | sage_of_letters, enchanter | zoner | flyer II | R | 5 | Storm arrows chain lightning between wet targets (rain, water) | black hair, short horns, scholar's robe; black/teal | Bureau · coral_port | enchanter: takes the best of three offers, rerolls with a book; flies up to the library's high shelves |
| 43 | Seika Ryugasaki | F | east_dragon | summoner | katana: dragon-pearl sword | arcane | proud_noble | envoy, guard | summoner | flyer III + swimmer I | L | 3 | Two cloud-serpent wisps orbit her and strike where her sword points | tall, white hair with blue tips, long horns; white/azure | Court · coral_port | guard: talent a tier up while home; pacts carried in person +0.1-0.2; patrols above its own walls |
| 44 | Shion Mikumari | F | east_dragon | striker | fists: scaled bracers | radiant | battle_maniac | steward, guard | rushdown | acrobat II + swimmer I | R | 4 | Dragon-palm string ends in a rising uppercut; a tail sweep catches jumpers | short teal hair, horn nubs, sleeveless training top; teal/orange | Warband · coral_port | guard: talent a tier up while home; its talents at full tier at a stewarded base; vaults to any alarm |
| 45 | Vexa Thornscale | F | west_dragon | vanguard | hammer: wyrm-tail mace | venom | tyrant | surveyor, miner | all_rounder | leaper III + glider I | E | 5 | Tail-sweep circle and an acid wing buffet; chained hop-slams in phase 3 | curled horns, green scales, leathery wings, dark armour; emerald/bronze | Dominion · deepslate_keep | miner: Fortune +1-3 on the ores it mines; bounds up shaft steps, leaps cave gaps |
| 46 | Ignatia Vermillor | F | west_dragon | marksman | crossbow: dragon-bone arbalest | fire | chuunibyou | ember_keeper, smelter | zoner | flyer II + ember_stride I | R | 6 | Every third bolt bursts into a fire cone; she strikes a pose and announces her ultimate by name before it fires | black hair with red streaks, small horns, folded dragon wings under a too-long high-collared scarlet cape; black/scarlet | Legion · deepslate_keep | smelter: furnaces 25-55% faster on the same fuel, 1-2 more; works on the ground, flies between jobs |
| 47 | Brannoc Emberhide | M | west_dragon | striker | axe: obsidian greataxe | fire | hot_blooded | forgemaster, blacksmith | rushdown | charger II + ember_stride II | C | 6 | Molten core: fire builds heat stacks, then an eruption ring | crown-like horns, glowing magma cracks; black/orange | Vanguard · deepslate_keep | blacksmith: crafts 25-55% faster, repairs 30-40% per material; sure-footed under heavy loads |
| 48 | Aurelian Wyrmholt | M | west_dragon | summoner | axe: drake-handler's hatchet | arcane | eccentric_genius | sage_of_letters, enchanter | summoner | flyer II | E | 7 | Hatchling drakes orbit him and breathe sparks at his marks | scholar's coat, small wings, monocle, silver hair; silver/purple | Academy · deepslate_keep | enchanter: takes the best of three offers, rerolls with a book; flies up to the library's high shelves |
| 49 | Goen Ishigaki | M | komainu | vanguard | fists: stone paws | physical | stoic | warden_of_walls, guard | counter | charger II + leaper I | C | 8 | Open-mouthed roar cone; stone skin while he stands still | curly mane, stone grey with moss, shrine rope; stone/moss | Watch · shrine_pavilion | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 50 | Unme Ishigaki | F | komainu | vanguard | sword_shield: stone tablet shield and short sword | radiant | shy | lookout, guard | counter | charger II + runner I | R | 7 | Closed-mouth seal: a silence aura on enemy heroes; her tablet shield shelters allies | curly-mane bob, stone grey with red rope; stone/red | Commune · shrine_pavilion | guard: talent a tier up while home; alarm +16 blocks from high ground; holds the gate, shrugs off knockback |
| 51 | Koshi Kagurazaka | M | komainu | striker | instrument: lion-dance head and cymbals | fire | genki | festival_host, guard | rushdown | acrobat II + leaper I | C | 1 | Lion-dance bites on a drum rhythm; the cymbal crash stuns | big red lion-dance head over his shoulder, green cloth; red/green | Troupe · shrine_pavilion | guard: talent a tier up while home; its festivals last 25-50% longer; vaults to any alarm |
| 52 | Hanae Momozono | F | plant_spirit | vanguard | hammer: oak-root maul | venom | gentle_giant | harvest_blessing, farmer | all_rounder | charger II + climber I | R | 8 | Root wall: plants herself and grows bark armour; flowers bloom on allies she shelters | flower crown, leafy hair with petals, petal dress; pink/leaf green | Haven · jungle_haven | farmer: Fortune +1-3 on crops, replants as it reaps; carries whole harvests without slowing |
| 53 | Corwin Barkhollow | M | plant_spirit | skirmisher | rapier: thorn whip-sword | venom | coward_who_rises | forester, woodcutter | rushdown | climber II + acrobat I | C | 2 | Thorn-whip lashes from four blocks away; when an ally falls he roots, blooms and turns bold | bark-skin arms, moss hair, small branch antlers; brown/green | Fellowship · jungle_haven | woodcutter: Fortune +1-3 on leaves (saplings, apples); fells tall trees from the trunk, no scaffold |
| 54 | Calyx Wren | F | plant_spirit | summoner | instrument: flower flute | venom | airhead | aura (regrowth), woodcutter | summoner | glider II + climber I | R | 1 | Seedling sprites sprout where her flute notes land; vines tether whoever stays | orchid hair, vine-lace sleeves, a dandelion-seed parasol; violet/green | Parade · jungle_haven | woodcutter: own regrowth +2-4 stacks over the cap; glides down from heights to its work |
| 55 | Morel Underbough | M | plant_spirit | summoner | scythe: mushroom-harvest sickle | blight | sleepy | forester, woodcutter | summoner | glider I + climber I | C | 3 | Spore sprites rise from his rot zones | big mushroom-cap hat, mossy cloak, glowing spots; brown/teal | Lodge · jungle_haven | woodcutter: Fortune +1-3 on leaves (saplings, apples); glides down from heights to its work |

### 5.2 Shrine, court and martial Japan (38 heroes: 19 F, 19 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | Mobility | R | B | Signature | Look (silhouette; palette) | Leader seed | Work mastery (duty: specialty; mobility at work) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 56 | Hinae Shirasuzu **[S]** | F | shrine | support | focus: purification wand and talismans | radiant | sweet_natured | mend, guard | zoner | runner II + swimmer I | E | S | Talisman fans and sealing circles; a barrier of seven talismans in phase 2 | long black hair tied low with white paper, red trousers, bell cluster; red/white | Hearth · shrine_pavilion | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; runs the shrine steps to any alarm |
| 57 | Yuzuri Hojo | F | shrine | controller | instrument: bell staff | arcane | stoic | purifier, guard | trapper | runner II + leaper I | E | 2 | Bell rings stun spirits; rotating rings of bells form zones | silver hair, white kimono with gold, bell staff; white/gold | Watch · shrine_pavilion | guard: talent a tier up while home; also cleanses member players; answers alarms at a run |
| 58 | Chigusa Hiwada | F | shrine | striker | polearm: curved glaive | fire | righteous | drill, guard | all_rounder | leaper II + runner I | R | 3 | Purifying-flame glaive sweeps; a kneeling vow before her ultimate | high ponytail, tied-back sleeves, headband; red/black | Order · shrine_pavilion | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 59 | Masaomi Tomine | M | shrine | support | staff: paper-streamer wand | radiant | big_sibling | revival_rite, guard | all_rounder | runner II + swimmer I | C | 4 | Purifying sweep dispels foes and cleanses allies; prays (breakable) | pale blue robe, purple trousers, streamer wand; pale blue/purple | Household · shrine_pavilion | guard: talent a tier up while home; revived allies rise with absorption; answers alarms at a run |
| 60 | Kozen Torii | M | shrine | vanguard | hammer: sacred-rope mallet | physical | gruff_veteran | warden_of_walls, guard | grappler | charger II | R | 5 | Sacred rope binds foes inside a boundary; holds the gate | shaved head, sacred rope over the shoulders, white robes; white/straw | Hold · shrine_pavilion | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 61 | Harutaka Hoshinomiya | M | onmyoji | summoner | focus: paper familiars | blight | cool_headed | sage_of_letters, enchanter | summoner | glider III + runner I | E | 6 | Shadow-paper beasts fight for him; a five-point seal binds the arena edge | tall black court cap, white and indigo hunting robe, a folded paper kite on his back; white/indigo | Bureau · shrine_pavilion | enchanter: takes the best of three offers, rerolls with a book; glides down from heights to its work |
| 62 | Tsukasa Yomei | F | onmyoji | controller | focus: star-seal talismans | arcane | eccentric_genius | aura (prospector), miner | stance | hover II + runner I | E | 4 | Five phases: her element cycles each cast; the robe colour shows which | violet court cap tilted, round glasses, star-embroidered robe; violet/white | Academy · shrine_pavilion | miner: own prospector +2-4 stacks over the cap; drifts over water, mud and gaps |
| 63 | Kotoyo Kamo | F | onmyoji | summoner | fan: paper-crane fan | frost | ancient_sage | harvest_blessing, farmer | summoner | flyer III | L | 5 | Paper cranes fold out of her fan; divination marks the next foe to strike | long grey hair, youthful face, black court robe; black/gold | Conclave · shrine_pavilion | farmer: Fortune +1-3 on crops, replants as it reaps; rides her paper crane between far fields |
| 64 | Seimon Hoshikage | M | onmyoji | controller | staff: astrolabe staff | blight | schemer | spymaster, scout | trapper | glider II + runner I | E | 7 | Star prison: a five-point zone stuns when its lines close; stay outside them | white hair, star-map cloak, eye patch; black/silver | Circle · shrine_pavilion | scout: nears rival walls unseen, names the heroes it sees; glides off ridges into valleys |
| 65 | Kagenori Tsurumine **[S]** | M | samurai | striker | katana: long and short swords | shock | stoic | drill, guard | counter | leaper II + runner I | E | S | Quick-draw counter stance and a thunderclap draw; helmet in phase 2, two swords in phase 3 | high ponytail, indigo coat with crane crest; indigo/white | Watch · deepslate_keep | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 66 | Chiyo Akazonae | F | samurai | vanguard | polearm: straight spear | physical | righteous | rally, guard | counter | charger II + runner I | R | 6 | Lord's-banner zone strengthens allies; a spear-wall sweep | red lacquer armour, wave-crest helmet; red/black | Order · deepslate_keep | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; holds the gate, shrugs off knockback |
| 67 | Asahi Nagakura | F | samurai | striker | greatblade: long field sword | fire | hot_blooded | mentor, guard | rushdown | charger III + leaper I | E | 7 | Rising-sun cut: a spinning cleave with a fire trail; enrages early | short red hair, white trousers, long sword on the back; white/red | Vanguard · deepslate_keep | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; holds the gate, shrugs off knockback |
| 68 | Shiori Mitsukado | F | samurai | marksman | bow: tall longbow | shock | perfectionist | lookout, guard | zoner | runner II + climber I | R | 8 | Kneel-and-aim piercing shots; each miss sharpens her focus | long black hair tied with purple cord, archery chest guard; black/purple | Academy · deepslate_keep | guard: talent a tier up while home; alarm +16 blocks from high ground; answers alarms at a run |
| 69 | Sokichi Ishizumi | M | samurai | support | katana: brush blade | blight | proud_noble | mentor, guard | all_rounder | runner I | C | 8 | Ink strokes from his blade write zones that bless allies or curse foes; a tea ceremony heals all | neat side-part, black formal vest; black/beige | Court · deepslate_keep | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; answers alarms at a run |
| 70 | Saburo Kazamaki | M | wanderer | striker | katana: sheathed blade | physical | melancholic | forester, woodcutter | counter | runner III + swimmer I | E | 1 | Single stroke: 1.5 s of stillness, then a line cut; the re-sheathe is the opening | wide straw hat, worn brown kimono, chin scar; brown/teal | Vigil · bamboo_village | woodcutter: Fortune +1-3 on leaves (saplings, apples); runs between far groves |
| 71 | Kozue Hagino | F | wanderer | skirmisher | katana: short blade and needles | venom | charmer | pathfinder, scout | rushdown | acrobat II + runner I | C | 1 | Petal step: slips past foes leaving petal cuts; the veiled hat flips first | veiled travelling hat, dusty-rose kimono; dusty rose/grey | Salon · bamboo_village | scout: scouts 15-35% faster, never loses the way home; double-jumps ravines |
| 72 | Rindo Akizuki | F | wanderer | striker | greatblade: horse-cutter blade | physical | battle_maniac | aura (duelist), guard | all_rounder | charger II + runner I | R | 2 | Wide sweeps with long recoveries; drinks between bouts | tall, scar across the nose, travelling cloak; ochre/black | Warband · bamboo_village | guard: own duelist +2-4 stacks over the cap; holds the gate, shrugs off knockback |
| 73 | Teppei Hinawa | M | wanderer | marksman | crossbow: matchlock | fire | sleepy | intimidation, guard | zoner | runner I | C | 2 | Slow aim with a glint tell, one heavy shot, a long reload to punish | unshaven, black coat, matchlock on the shoulder; black/rust | Lodge · bamboo_village | guard: talent a tier up while home; raiders seeing it fall back sooner; answers alarms at a run |
| 74 | Tobimaru Usugumo **[S]** | M | ninja | skirmisher | twin_blades: short blade and kunai | blight | lone_wolf | cartographer, scout | rushdown | acrobat III + climber II | E | S | Vanishes (footstep puffs give him away), strikes from behind; decoys in phase 2 | navy mask, long red scarf, high ponytail; navy/red | Pack · bamboo_village | scout: scouts 32-64 blocks farther, maps 5x5 sectors; double-jumps ravines, climbs cliffs for a lookout |
| 75 | Sumire Ayatsuki | F | ninja | marksman | focus: throwing stars | venom | glutton | pathfinder, scout | zoner | acrobat II + climber I | C | 3 | Poisoned star fans and caltrop fields; she stops mid-fight for a snack (punish window) | lavender hair, short scarf, star pouch; lavender/black | Guild · bamboo_village | scout: scouts 15-35% faster, never loses the way home; double-jumps ravines |
| 76 | Iori Shinobazu | F | ninja | controller | scythe: chain sickle | blight | schemer | spymaster, scout | trapper | climber II + acrobat I | R | 4 | Smoke field darkens; her chain sickle drags foes into it | black bob, half mask, coiled chain; black/yellow | Circle · bamboo_village | scout: nears rival walls unseen, names the heroes it sees; climbs cliffs for a lookout |
| 77 | Mayu Hyosa | F | ninja | skirmisher | scythe: sickle on a chain | frost | yandere | lookout, guard | rushdown | acrobat II + snowstride II | C | 5 | Frost sickle chain pulls and cuts; an ice-slide dash closes the gap | white braid, pale blue scarf; white/ice blue | Covenant · bamboo_village | guard: talent a tier up while home; alarm +16 blocks from high ground; vaults to any alarm |
| 78 | Zenji Karakuri | M | ninja | controller | crossbow: repeating hand crossbow and tripwires | shock | trickster | tactician, guard | trapper | glider II + climber I | R | 3 | Tripwire grid: shock lines between posts; a smoke escape | goggles on the forehead, utility vest, a folding kite glider; olive/brown | Revel · bamboo_village | guard: talent a tier up while home; raid parties with it keep formation; glides from the walls to any alarm |
| 79 | Kakuzen Toyama | M | warrior_monk | vanguard | polearm: monk's glaive | physical | perfectionist | drill, guard | counter | charger II | C | 4 | Iron body: shield and taunt; a half-circle glaive sweep | white head wrap, robes over armour, big prayer beads; white/saffron | Academy · stone_hold | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; holds the gate, shrugs off knockback |
| 80 | Jikai Morino | M | warrior_monk | support | fists: prayer-bead fists | radiant | gentle_giant | mend, guard | counter | runner I + swimmer I | R | 5 | Chanting aura heals; meditation stance answers melee | bald, saffron robes, prayer beads; saffron/brown | Haven · stone_hold | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; answers alarms at a run |
| 81 | Soka Yamanobe | F | warrior_monk | skirmisher | staff: iron staff | shock | chuunibyou | pathfinder, scout | rushdown | leaper II + acrobat I | C | 6 | Staff-spin barrage and a pole-vault leap | short hair, white ascetic robe, beads; white/teal | Legion · stone_hold | scout: scouts 15-35% faster, never loses the way home; bounds over gaps and fences |
| 82 | Kotoha Fujinami | F | fan_dancer | controller | fan: twin dance fans | arcane | charmer | envoy, guard | trapper | acrobat III + glider I | E | 7 | Petal dance: rotating cones; charmed soldiers briefly turn on their own side | updo with hairpins, long-sleeved layered kimono; pink/gold | Salon · cherry_court | guard: talent a tier up while home; pacts carried in person +0.1-0.2; vaults to any alarm |
| 83 | Natsuri Hanabishi | F | fan_dancer | support | fan: ribbon fans | radiant | genki | festival_host, guard | all_rounder | acrobat II + runner I | C | 8 | Festival dance: ally buffs pulse on each beat; tempo builds | twin ponytails with ribbons, festival coat; red/white | Troupe · cherry_court | guard: talent a tier up while home; its festivals last 25-50% longer; vaults to any alarm |
| 84 | Kikuzo Hanaoka | M | fan_dancer | skirmisher | fan: steel war fans | physical | proud_noble | renown, guard | counter | glider II + acrobat I | R | 6 | Frozen-pose counter, then a whirling fan dash | stage face-paint stripes, flowing mane wig; red/black | Court · cherry_court | guard: talent a tier up while home; Looting +1-3 on its own kills; glides from the walls to any alarm |
| 85 | Sakie Kurogami | F | delinquent | striker | hammer: spiked wooden bat | physical | delinquent | intimidation, guard | rushdown | runner II + charger I | C | 1 | Turf war: stronger inside her walls; calls her crew of guards | long sailor skirt, mask pulled down, bleached hair; navy/yellow | Crew · mud_town | guard: talent a tier up while home; raiders seeing it fall back sooner; answers alarms at a run |
| 86 | Gosuke Banba | M | delinquent | striker | fists: taped fists | shock | delinquent | drill, guard | rushdown | leaper II + charger I | R | 7 | Coat-cape fling blinds; a crackling flying headbutt; a counter-taunt | pompadour, long school coat worn as a cape; black/gold | Crew · mud_town | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 87 | Daimon Sakaishi | M | delinquent | summoner | katana: wooden practice sword | shock | tyrant | renown, guard | summoner | charger I + runner I | C | 8 | Calls the whole crew (real guards) with a raised wooden sword and orders them to surround the target | spiky white hair, leather jacket; white/purple | Dominion · mud_town | guard: talent a tier up while home; Looting +1-3 on its own kills; holds the gate, shrugs off knockback |
| 88 | Koshiro Nabeyama | M | battle_chef | striker | twin_blades: twin cleavers | fire | glutton | master_chef, chef | rushdown | runner II | R | 1 | Knife flurry; a wok-toss fire cone | tall chef's hat, white coat, bandana; white/red | Guild · mud_town | chef: smoker 20-40% faster, 15-35% chance of a second dish; errands at a run, steps up blocks |
| 89 | Hotaru Nabeshima | F | battle_chef | vanguard | hammer: frying pan | fire | sweet_natured | master_chef, chef | counter | charger II | C | 2 | Frying-pan wall: parries, then a lunch-box toss heals allies | ponytail, patched apron, oven mitt; yellow/white | Hearth · mud_town | chef: smoker 20-40% faster, 15-35% chance of a second dish; sure-footed under heavy loads |
| 90 | Ryuzo Kosho | M | battle_chef | controller | katana: slicing knife | venom | perfectionist | quartermaster, hauler | trapper | runner I | R | 2 | Spice-dusted cuts weaken and slow; a judging glare marks the sloppy | glasses, black chef coat, knife roll; black/green | Academy · mud_town | hauler: carries 30-36 slots, sorts 25-55% faster; hauls at a run |
| 91 | Raijiro Ikazuchi | M | storm_drummer | controller | instrument: ring of drums | shock | hot_blooded | war_drummer, guard | zoner | leaper III + charger I | E | 3 | Drum-beat waves: expanding shock rings to jump over | ring of drums on the back, lightning tattoo; yellow/black | Vanguard · stone_hold | guard: talent a tier up while home; its drum speeds defenders 5-10%; bounds up to the wall walk |
| 92 | Tsuzumi Otowa | F | storm_drummer | support | instrument: festival drum and sticks | fire | big_sibling | war_drummer, guard | all_rounder | leaper II | R | 3 | Rhythm: allies attack faster on her beat; the raised stick is the tell | festival headband, short coat; blue/white | Household · stone_hold | guard: talent a tier up while home; its drum speeds defenders 5-10%; bounds up to the wall walk |
| 93 | Futa Kazabukuro | M | storm_drummer | controller | fan: wind sack and fan | physical | trickster | pathfinder, scout | zoner | glider II + leaper I | C | 4 | Wind-sack gusts push in cones; a vacuum pulls foes back | cloud scarf, wind sack over the shoulder; green/white | Revel · stone_hold | scout: scouts 15-35% faster, never loses the way home; glides off ridges into valleys |

### 5.3 Wuxia and East Asian myth (14 heroes: 7 F, 7 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | Mobility | R | B | Signature | Look (silhouette; palette) | Leader seed | Work mastery (duty: specialty; mobility at work) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 94 | Lin Mingxuan | M | sword_cultivator | marksman | focus: flying swords | arcane | proud_noble | sage_of_letters, enchanter | zoner | flyer III | E | 5 | Sword array: orbiting swords launch one after another | white-blue robe, hair crown; white/azure | Court · ivory_palace | enchanter: takes the best of three offers, rerolls with a book; rides his sword to the library's high shelves |
| 95 | Bai Yunxi | F | sword_cultivator | support | staff: jade whisk | radiant | ancient_sage | revival_rite, guard | all_rounder | hover III + acrobat II | L | 4 | Qi mending and a lotus barrier; a breakthrough glow in phase 3 | white hair bun with jade pin, white robes; white/jade | Conclave · ivory_palace | guard: talent a tier up while home; revived allies rise with absorption; drifts over water, mud and gaps |
| 96 | Qiao Yanling | F | sword_cultivator | striker | rapier: straight sword | shock | cool_headed | mentor, guard | counter | acrobat III + leaper I | E | 5 | Lightning sword-heart: each parried hit charges a thunder thrust | high ponytail with a silver crescent pin, violet robes with silver cloud trim; violet/silver | Bureau · ivory_palace | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; vaults to any alarm |
| 97 | Zhou Wenshan | M | sword_cultivator | controller | polearm: formation banner spear | arcane | schemer | tactician, guard | trapper | runner II | C | 6 | Plants four banners; the square between them becomes a slowing formation | scholar's hat, scroll case; grey/indigo | Circle · ivory_palace | guard: talent a tier up while home; raid parties with it keep formation; answers alarms at a run |
| 98 | Hua Lianmei | F | martial_artist | striker | fists: silk-wrapped fists | physical | hot_blooded | drill, guard | counter | acrobat II + climber I | R | 6 | Eight-trigram palms: counter stance into palm-push strings | single high bun tied with red cord, sleeveless plum training vest with frog buttons, wide white trousers; plum/white | Vanguard · mud_town | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; vaults to any alarm |
| 99 | Mei Xiaodie | F | martial_artist | skirmisher | twin_blades: butterfly swords | frost | trickster | pathfinder, scout | rushdown | acrobat II + climber II | C | 7 | Butterfly-sword flurry and a wall-run kick | short bob, green training clothes; green/white | Revel · mud_town | scout: scouts 15-35% faster, never loses the way home; double-jumps ravines |
| 100 | Shi Gangtie | M | martial_artist | support | fists: qi palms | arcane | ancient_sage | mentor, guard | counter | leaper II + charger I | R | 7 | Qi palms: each strike heals the ally behind its target; iron-shirt stance while he stands still | shaved sides, topknot, bandaged chest; bronze/brown | Conclave · mud_town | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; bounds up to the wall walk |
| 101 | Liu Zuixian | M | martial_artist | striker | staff: loose-limbed staff | venom | glutton | festival_host, guard | all_rounder | acrobat I + swimmer I | C | 8 | Off-rhythm staff string; a gourd-breath cone | red cheeks, gourd at the hip, loose robe; brown/orange | Guild · mud_town | guard: talent a tier up while home; its festivals last 25-50% longer; vaults to any alarm |
| 102 | Lu Ningshuang | F | jiangshi | striker | fists: long-nailed hands | blight | sleepy | aura (hardy), hauler | rushdown | leaper II | R | 8 | Hops in chains; striking the talisman on her forehead stuns her | official's hat with talisman, long sleeves; dark blue/red | Lodge · dark_manor | hauler: own hardy +2-4 stacks over the cap; bounds over gaps instead of bridging |
| 103 | Yin Suling | F | jiangshi | summoner | focus: bell and talismans | blight | melancholic | revival_rite, guard | summoner | leaper I + hover I | C | 1 | Her bell guides two hopping servants | white mourning robes, pale, bell; white/grey | Vigil · dark_manor | guard: talent a tier up while home; revived allies rise with absorption; bounds up to the wall walk |
| 104 | Gao Qinglong | M | jiangshi | vanguard | sword_shield: coffin-lid shield and sabre | blight | gruff_veteran | warden_of_walls, guard | counter | charger II + leaper I | E | 1 | Coffin wall; each blocked hit drains life back | general's armour, talisman, grey skin; grey/teal | Hold · dark_manor | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 105 | Feng Zhaoming | M | phoenix | skirmisher | fan: ember-feather fan | fire | chuunibyou | ember_keeper, smelter | rushdown | flyer III + ember_stride II | L | 2 | Rebirth once per fight as a final phase; flaming wing dives | flame-red hair, feather cape that becomes wings; red/gold | Legion · desert_court | smelter: furnaces 25-55% faster on the same fuel, 1-2 more; works on the ground, flies between jobs |
| 106 | Huang Jinlu | F | phoenix | support | fan: ember fan | radiant | sweet_natured | revival_rite, guard | all_rounder | flyer II + ember_stride I | E | 2 | Rebirth ward: an ally's next lethal hit is stopped once | gold feather hair ornaments, white-gold dress; gold/white | Hearth · desert_court | guard: talent a tier up while home; revived allies rise with absorption; patrols above its own walls |
| 107 | Zhu Yanchi | M | phoenix | marksman | bow: firebird bow | fire | chuunibyou | lookout, guard | zoner | flyer II + ember_stride I | C | 3 | Sun-arrow rain leaves burning ground | gold crown feathers, crimson coat; crimson/gold | Legion · desert_court | guard: talent a tier up while home; alarm +16 blocks from high ground; patrols above its own walls |

### 5.4 Western fantasy (65 heroes: 33 F, 32 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | Mobility | R | B | Signature | Look (silhouette; palette) | Leader seed | Work mastery (duty: specialty; mobility at work) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 108 | Ilsabet Frostmere **[S]** | F | knight | vanguard | sword_shield: longsword and kite shield | frost | righteous | rally, guard | counter | charger III + snowstride I | E | S | Shield wall and bash; visor down and a charge in phase 2; a frozen standard in phase 3 | steel plate, braided crown of ash-blonde hair, blue-white tabard; steel/frost blue | Order · stone_hold | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; holds the gate, ignores cobwebs |
| 109 | Odile Varenne | F | knight | vanguard | greatblade: claymore | fire | perfectionist | forgemaster, blacksmith | all_rounder | charger II | C | 3 | Oath of the blade: blocked hits stack resolve; at five, a crescent cleave wave | silver plate, scarlet cape, short black hair; silver/scarlet | Academy · stone_hold | blacksmith: crafts 25-55% faster, repairs 30-40% per material; sure-footed under heavy loads |
| 110 | Tamsin Rook | F | knight | vanguard | polearm: banner lance | radiant | big_sibling | rally, guard | counter | charger II + runner I | R | 4 | War banner: the planted banner shelters allies and calls guards; a lance wall | red ponytail, banner, light armour; red/white | Household · stone_hold | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; holds the gate, shrugs off knockback |
| 111 | Barnaby Thistledown | M | knight | vanguard | hammer: warhammer and tower shield | physical | airhead | aura (mason), builder | counter | charger I | C | 4 | Tower-shield advance; his bell helmet rings and stuns whoever strikes it | visored great helm with plume, round build; brass/green | Parade · stone_hold | builder: own mason +2-4 stacks over the cap; carries full loads up steps |
| 112 | Corvin Ashlocke | M | knight | striker | greatblade: black two-handed sword | blight | melancholic | mentor, guard | stance | charger III + leaper I | E | 5 | Cursed cleaves drain; a fallen-knight stance in phase 2 | black armour, torn cape, white hair; black/grey | Vigil · stone_hold | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; holds the gate, shrugs off knockback |
| 113 | Celestine Brightwater | F | paladin | support | hammer: radiant warhammer | radiant | sweet_natured | mend, guard | all_rounder | flyer II | E | 5 | Consecrated ground and healing touch; wings of light in phase 2 | white-gold armour, halo-like crest, long golden hair; white/gold | Hearth · ivory_palace | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; patrols above its own walls |
| 114 | Aldric Dawnmere | M | paladin | vanguard | sword_shield: sun sword and shield | radiant | righteous | warden_of_walls, guard | counter | charger II | R | 6 | Radiant pillars ring the arena; a dawn shield | gold armour, white cloak, short blond hair; gold/white | Order · ivory_palace | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 115 | Lucan Pyreholt | M | paladin | striker | greatblade: holy greatsword | fire | tyrant | intimidation, guard | rushdown | charger II + leaper I | C | 7 | Judgement brands the guilty, then detonates the brands | red-white surcoat, bucket helm; red/white | Dominion · ivory_palace | guard: talent a tier up while home; raiders seeing it fall back sooner; holds the gate, shrugs off knockback |
| 116 | Ottilie Lanthorn | F | paladin | summoner | greatblade: lantern greatsword | radiant | gentle_giant | rally, guard | summoner | charger I + runner I | R | 6 | Two spectral squires guard her; the lantern on her blade calls them back | tall, white veil helm, lantern; white/silver | Haven · ivory_palace | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; holds the gate, shrugs off knockback |
| 117 | Agathe Morrow | F | battle_priest | striker | hammer: cross-shaped mace | radiant | shy | purifier, guard | stance | runner I + charger I | C | 7 | Heals with her tome in phase 1; snaps it shut and takes up the mace in phase 2 | habit and veil, tome on a chain; black/white | Commune · ivory_palace | guard: talent a tier up while home; also cleanses member players; answers alarms at a run |
| 118 | Matthias Grell | M | battle_priest | support | crossbow: holy crossbow | radiant | gruff_veteran | triage, guard | zoner | runner I | R | 8 | Holy bolts heal allies they pass and burn foes | cassock, bandolier, stubble; black/purple | Hold · ivory_palace | guard: talent a tier up while home; carries its own 2-4 triage meals; answers alarms at a run |
| 119 | Ilse Rabenmark | F | battle_priest | support | rapier: lancet sword | venom | cool_headed | triage, guard | all_rounder | runner II + climber I | C | 8 | Lancet thrusts inject antidote into allies and poison into foes | beaked mask, long coat; black/teal | Bureau · ivory_palace | guard: talent a tier up while home; carries its own 2-4 triage meals; answers alarms at a run |
| 120 | Ansel Kade | M | battle_priest | striker | twin_blades: twin blessed daggers | radiant | hot_blooded | purifier, guard | rushdown | runner II + acrobat I | R | 1 | Exorcism strings with twin daggers; a rosary chain pull | short hair, cassock with rolled sleeves, rosary; black/gold | Vanguard · ivory_palace | guard: talent a tier up while home; also cleanses member players; answers alarms at a run |
| 121 | Ambrose Nettlewick **[S]** | M | witch | controller | staff: gnarled cauldron staff | venom | eccentric_genius | sage_of_letters, enchanter | trapper | flyer II | E | S | Potion barrage (the colour is the tell); cauldron zones; broom hops in phase 2 | tilted wide-brimmed hat, purple coat with vials, round glasses, crow on the shoulder; purple/green | Academy · dark_manor | enchanter: takes the best of three offers, rerolls with a book; rides his staff-broom to the library's high shelves |
| 122 | Pippa Hexley | F | witch | marksman | focus: star-tipped wand | arcane | chuunibyou | sage_of_letters, enchanter | zoner | flyer II | R | 1 | Hex bubbles trap and pop; a mirror hex repeats her last cast | short purple twin tails, small witch hat, cape; violet/pink | Legion · dark_manor | enchanter: takes the best of three offers, rerolls with a book; flies up to the library's high shelves |
| 123 | Hazel Brambleworth | F | witch | support | scythe: herb sickle | venom | big_sibling | aura (green_thumb), farmer | all_rounder | flyer I | C | 2 | Herbal brews heal allies and poison foes; her sickle reaps the marks | freckles, straw witch hat with flowers, apron; green/brown | Household · dark_manor | farmer: own green thumb +2-4 stacks over the cap; flies between far fields, lands without trampling |
| 124 | Corvina Blackfen | F | witch | summoner | twin_blades: ritual knives | blight | trickster | lookout, guard | summoner | glider II + climber I | R | 3 | A crow swarm pecks and blinds; ritual knives mark its next target | black hair, crow-feather cloak; black/teal | Revel · dark_manor | guard: talent a tier up while home; alarm +16 blocks from high ground; glides from the walls to any alarm |
| 125 | Cassius Vane | M | witch | controller | focus: grimoire | blight | schemer | sage_of_letters, enchanter | trapper | hover II + runner I | E | 2 | Rune circles: delayed glyph traps | cropped ash hair, high-collared black robes, grimoire; black/silver | Circle · dark_manor | enchanter: takes the best of three offers, rerolls with a book; drifts over water, mud and gaps |
| 126 | Aelwyn Sylvaris | F | elf | marksman | bow: longbow | physical | cool_headed | forester, woodcutter | zoner | runner II + climber I | R | 4 | Hunter's mark, then a piercing shot | long pointed ears, green hooded cloak, blonde braid; green/silver | Bureau · grove_lodge | woodcutter: Fortune +1-3 on leaves (saplings, apples); runs between far groves |
| 127 | Nyxara Duskveil | F | elf | skirmisher | twin_blades: curved runeblades | blight | tsundere | spymaster, scout | stance | acrobat III + climber I | E | 5 | Spellblade state: blight blades; a shadow step | dusky skin, white hair, violet runes; grey/violet | League · grove_lodge | scout: nears rival walls unseen, names the heroes it sees; double-jumps ravines |
| 128 | Lirielle Fernsong | F | elf | support | bow: blossom bow | venom | sweet_natured | aura (regrowth), woodcutter | zoner | runner I + swimmer I | R | 6 | Healing arrows for allies, thorn arrows for foes | pink-blonde hair, flower circlet; white/pink | Hearth · grove_lodge | woodcutter: own regrowth +2-4 stacks over the cap; runs between far groves |
| 129 | Faelan Oakshade | M | elf | controller | staff: druid staff | venom | ancient_sage | forester, woodcutter | trapper | climber II + runner I | C | 3 | Entangling roots and a thorn wall | antler circlet, long brown hair, leaf cloak; brown/green | Conclave · grove_lodge | woodcutter: Fortune +1-3 on leaves (saplings, apples); fells tall trees from the trunk, no scaffold |
| 130 | Caedric Wintersong | M | elf | skirmisher | bow: short bow and knife | frost | lone_wolf | cartographer, scout | rushdown | acrobat II + snowstride I | R | 4 | Rime arrows stack freeze; vaulting backflips | silver hair, blue cloak; blue/silver | Pack · grove_lodge | scout: scouts 32-64 blocks farther, maps 5x5 sectors; double-jumps ravines |
| 131 | Ottavia Sanguine | F | vampire | skirmisher | rapier: blood rapier | blight | yandere | envoy, guard | counter | flyer II + climber I | E | 7 | Draining cuts and a bat-swarm dash | silver ringlets, crimson coat, folded bat wings, red eyes; crimson/black | Covenant · dark_manor | guard: talent a tier up while home; pacts carried in person +0.1-0.2; patrols above its own walls |
| 132 | Lazare Vespertine | M | vampire | controller | scythe: blood scythe | blight | charmer | merchant_prince, trader | trapper | glider II + climber I | R | 5 | Blood-moon zone drains; charmed foes falter | slicked black hair, high-collared cape; black/wine | Salon · dark_manor | trader: prices -10 to -30%, carries 30-36 slots; glides down to valley villages |
| 133 | Bettina Nachtigall | F | vampire | summoner | fan: bat-wing fan | blight | sleepy | aura (thrifty), smelter | summoner | glider I + climber I | C | 8 | Sleepy by day, bold at night; bat swarms blind | short black hair, bat hair clips, cardigan; black/purple | Lodge · dark_manor | smelter: own thrifty +2-4 stacks over the cap; glides down from heights to its work |
| 134 | Radomir Grauwald | M | vampire | marksman | bow: blood bow | blight | gruff_veteran | intimidation, guard | zoner | charger II | R | 6 | Blood arrows fill a pool he drinks from to heal; a blood-armour shell | white hair, scarred, crimson plate; crimson/white | Hold · dark_manor | guard: talent a tier up while home; raiders seeing it fall back sooner; holds the gate, shrugs off knockback |
| 135 | Belphira Ashenthrone | F | demon_lord | controller | polearm: three-pronged sceptre | fire | tyrant | tribute_master, trader | zoner | flyer III + ember_stride II | L | 1 | Hellfire sigils rain; imps answer her call | curled ram horns, black-red regal coat, demon wings, spade tail; black/crimson | Dominion · ember_fortress | trader: prices -10 to -20%, larger tribute loads; flies to villages, lands outside |
| 136 | Pim Brimsby | F | demon_lord | skirmisher | twin_blades: twin tail-daggers | arcane | trickster | renown, guard | rushdown | acrobat II + climber I | R | 2 | Prank hops and dagger pokes; a tail grab | teal hair, tiny horns, tail; teal/black | Revel · ember_fortress | guard: talent a tier up while home; Looting +1-3 on its own kills; vaults to any alarm |
| 137 | Malrecht Vorthane | M | demon_lord | skirmisher | scythe: demon scythe | fire | tyrant | intimidation, guard | rushdown | runner III + ember_stride II | L | 7 | Throne-step dashes; scythe arcs leave fire trails | tall, long black horns, black armour, cloak; black/purple | Dominion · ember_fortress | guard: talent a tier up while home; raiders seeing it fall back sooner; answers alarms at a run |
| 138 | Mephis Gallow | M | demon_lord | summoner | katana: contract blade | arcane | merchant | tribute_master, trader | summoner | hover II + runner I | R | 8 | Contract seals bind imps to fight for him; breaking a seal staggers him | noble coat, swept horns; red/black | Company · ember_fortress | trader: prices -10 to -20%, larger tribute loads; drifts over water, mud and gaps |
| 139 | Amariel Lumen | F | angel | support | staff: halo staff | radiant | airhead | mend, guard | all_rounder | flyer III | L | 3 | Halo barrier on allies; a feather rain heals | white wings, halo, long blonde hair; white/gold | Parade · ivory_palace | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; patrols above its own walls |
| 140 | Ishara Morn | F | angel | striker | katana: black blade | blight | melancholic | lookout, guard | rushdown | flyer II | R | 4 | Fallen dive and a black-feather volley | black wings, broken halo, white hair; black/silver | Vigil · ivory_palace | guard: talent a tier up while home; alarm +16 blocks from high ground; patrols above its own walls |
| 141 | Ariston Highgate | M | angel | vanguard | sword_shield: flaming sword and round aegis | fire | righteous | warden_of_walls, guard | counter | flyer III + charger II | L | 1 | Gate guardian: flaming sweeps; the aegis turns bolts aside | two pairs of small wings, gold armour; gold/red | Order · ivory_palace | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; patrols above its own walls |
| 142 | Halvard Skye | M | angel | marksman | bow: light bow | radiant | chuunibyou | lookout, guard | zoner | flyer II | R | 2 | Judgement arrows fall from above | white hair, two small wings, visor; white/blue | Legion · ivory_palace | guard: talent a tier up while home; alarm +16 blocks from high ground; patrols above its own walls |
| 143 | Mortlake Vey | M | reaper | striker | scythe: great scythe | blight | melancholic | revival_rite, guard | counter | hover III | E | 3 | Soul lantern marks; the scythe sweep reaps marked foes | hooded black cloak, lantern, pale; black/teal | Vigil · dark_manor | guard: talent a tier up while home; revived allies rise with absorption; drifts over water, mud and gaps |
| 144 | Elowen Hollowmere | F | reaper | controller | scythe: lantern scythe | arcane | shy | purifier, guard | trapper | hover II | R | 5 | The soul lantern on her scythe slows and tethers | short navy hair, oversized hood; navy/pale blue | Commune · dark_manor | guard: talent a tier up while home; also cleanses member players; drifts over water, mud and gaps |
| 145 | Silas Underhill | M | reaper | support | axe: grave spade | frost | airhead | architect, builder | all_rounder | hover I | C | 4 | Grave mounds rise to shield allies; shovelled frost waves slow foes | top hat, long coat, spade; grey/brown | Parade · dark_manor | builder: builds 20-40% faster, places 2-4 blocks up without scaffolds; drifts across mud and water with materials |
| 146 | Morden Kesh | M | necromancer | summoner | focus: bone tome | blight | eccentric_genius | sage_of_letters, enchanter | summoner | hover II | E | 5 | Raises two skeleton servants; bone spears | hood, ribcage armour, green fire; black/green | Academy · deepslate_keep | enchanter: takes the best of three offers, rerolls with a book; drifts over water, mud and gaps |
| 147 | Hespera Gravesend | F | necromancer | summoner | axe: bone cleaver | venom | yandere | quartermaster, hauler | summoner | hover II | C | 6 | Zombie servants and rot zones | black twin braids, skull hairpin; black/sickly green | Covenant · deepslate_keep | hauler: carries 30-36 slots, sorts 25-55% faster; drifts over mud and water with full loads |
| 148 | Osric Mallow | M | necromancer | controller | instrument: bone flute | arcane | sleepy | alchemist, enchanter | zoner | hover I | R | 6 | Bone-flute march slows the living | thin, patched coat, bone flute; grey/green | Lodge · deepslate_keep | enchanter: best-of-three enchanting now, faster brewing later; drifts over water, mud and gaps |
| 149 | Wren Halloway | F | beast_tamer | summoner | rapier: whip | physical | big_sibling | beastmaster, animal_carer | summoner | runner II + swimmer I | C | 7 | Commands two wolves; a whip crack taunts | fur hat, whip, ranger coat; tan/red | Household · grove_lodge | animal_carer: grows calves by feeding them, 2-4 pairs a round, extra wool; errands at a run, steps up blocks |
| 150 | Merith Talon | F | beast_tamer | summoner | bow: falconer bow | shock | lone_wolf | cartographer, scout | summoner | runner II + climber I | R | 8 | Her hawk dives at marked foes | leather glove, feathered hood; brown/sky blue | Pack · grove_lodge | scout: scouts 32-64 blocks farther, maps 5x5 sectors; covers ground at a run |
| 151 | Bjarke Honeyford | M | beast_tamer | summoner | axe: woodsman's axe | physical | gentle_giant | beastmaster, animal_carer | summoner | charger II + swimmer I | C | 7 | A bear companion (iron golem drawn as a bear) | bear-pelt cloak, braided beard; brown/honey | Haven · grove_lodge | animal_carer: grows calves by feeding them, 2-4 pairs a round, extra wool; sure-footed under heavy loads |
| 152 | Torin Highspear | M | dragoon | skirmisher | polearm: dragoon lance | shock | stoic | lookout, guard | rushdown | leaper III + glider I | E | 8 | Jump: leaves the ground high and lands with a shockwave on a marker | dragon helm, scale armour, a long tattered cape; red/black | Watch · stone_hold | guard: talent a tier up while home; alarm +16 blocks from high ground; bounds up to the wall walk |
| 153 | Kestrel Ardent | F | dragoon | striker | polearm: wyvern spear | frost | perfectionist | drill, guard | all_rounder | leaper II + acrobat I | C | 1 | Diving-thrust chain; each dive goes higher | silver ponytail, wyvern helm; silver/teal | Academy · stone_hold | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 154 | Roderic Lancefield | M | dragoon | vanguard | polearm: lance and pavise | physical | gruff_veteran | warden_of_walls, guard | counter | charger II | R | 1 | Lance charge across the arena; stops hard against walls | plumed helm, heavy lance; steel/green | Hold · stone_hold | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 155 | Hrafn Bloodmane | M | berserker | striker | axe: twin axes | fire | battle_maniac | forester, woodcutter | rushdown | charger II + runner I | C | 2 | Rage: damage taken becomes attack; never retreats | wild red beard, fur cloak, war paint; red/brown | Warband · grove_lodge | woodcutter: Fortune +1-3 on leaves (saplings, apples); hauls full log loads without slowing |
| 156 | Runa Ironvow | F | berserker | vanguard | sword_shield: axe and round shield | frost | coward_who_rises | forester, woodcutter | counter | charger II + swimmer I | R | 2 | Shield-maiden: trembles at first, then her war cry turns every block into a counter | blonde braids, bear pelt, round shield; brown/blue | Fellowship · grove_lodge | woodcutter: Fortune +1-3 on leaves (saplings, apples); hauls full log loads without slowing |
| 157 | Ulfgar Stonejaw | M | berserker | skirmisher | axe: throwing hatchets | shock | delinquent | surveyor, miner | rushdown | leaper II + charger I | C | 3 | Hatchets that return like boomerangs; a thunder leap to close in | shaggy hair, stone-studded armour; grey/brown | Crew · grove_lodge | miner: Fortune +1-3 on the ores it mines; bounds up shaft steps, leaps cave gaps |
| 158 | Clementine Vask | F | arbalist | marksman | crossbow: repeating crossbow | physical | perfectionist | aura (marksman), guard | zoner | runner I + climber I | R | 3 | Bolt barrage, then a piercing bolt | goggles, long coat, ponytail; brown/brass | Academy · stone_hold | guard: own marksman +2-4 stacks over the cap; answers alarms at a run |
| 159 | Odessa Quarrel | F | arbalist | marksman | crossbow: heavy arbalest and pavise | venom | stoic | warden_of_walls, guard | zoner | charger I | C | 4 | Poison bolts from behind a planted pavise | heavy coat, helm, pavise; olive/steel | Watch · stone_hold | guard: talent a tier up while home; repairs breaches itself, 1.5x builder speed; holds the gate, shrugs off knockback |
| 160 | Fennick Brass | M | arbalist | controller | crossbow: net launcher | shock | eccentric_genius | engineer, builder | trapper | climber II | R | 4 | Net bolts tether; shock nets | goggles, brass arm brace; brass/blue | Academy · stone_hold | builder: builds 10-30% faster, faster rails later; builds walls while clinging |
| 161 | Ezra Blackwake | M | corsair | support | rapier: cutlass | shock | merchant | merchant_prince, trader | all_rounder | swimmer II + runner I | C | 5 | Storm captain: each cutlass hit sings a shanty buff; he calls guards as his crew | tricorne, long coat, eye scar; navy/gold | Company · coral_port | trader: prices -10 to -30%, carries 30-36 slots; swims rivers on village trips |
| 162 | Nerys Saltmarsh | F | corsair | marksman | crossbow: harpoon launcher | frost | lone_wolf | angler_lord, fisherman | zoner | swimmer II + climber I | R | 5 | Harpoon tether pulls foes in | harpoon, sea coat, braided hair; teal/sand | Pack · coral_port | fisherman: Luck of the Sea and Lure +1-3; swims to the deepest water |
| 163 | Jory Flint | M | corsair | controller | crossbow: blunderbuss | fire | trickster | tribute_master, trader | trapper | swimmer I + acrobat I | C | 6 | Powder shots leave fuse kegs that burst (no block damage) | bandana, bomb belt; red/brown | Revel · coral_port | trader: prices -10 to -20%, larger tribute loads; swims rivers on village trips |
| 164 | Florian Ashvale | M | bard | support | instrument: lute | arcane | charmer | envoy, guard | all_rounder | runner II | R | 7 | Ballads buff allies; a discord chord weakens foes | feathered cap, red doublet; red/cream | Salon · cherry_court | guard: talent a tier up while home; pacts carried in person +0.1-0.2; answers alarms at a run |
| 165 | Linnea Harrowgate | F | bard | marksman | bow: harp bow | frost | shy | festival_host, guard | zoner | runner I + swimmer I | C | 6 | Harp-bow notes arc like arrows; a lullaby cone puts foes to sleep (stun) | long wavy hair, harp; ice blue/silver | Commune · cherry_court | guard: talent a tier up while home; its festivals last 25-50% longer; answers alarms at a run |
| 166 | Pell Wickham | M | bard | skirmisher | twin_blades: flute and stiletto | venom | trickster | spymaster, scout | rushdown | acrobat II + climber I | R | 8 | Flute trills mark foes; stiletto lunges at the marks | patchwork coat, flute; brown/green | Revel · cherry_court | scout: nears rival walls unseen, names the heroes it sees; double-jumps ravines |
| 167 | Ottoline Brassbury | F | alchemist | controller | focus: flask grenades | venom | eccentric_genius | alchemist, enchanter | trapper | leaper I | C | 7 | Acid flasks burn over time; foam flasks slow | goggles, lab coat, braid; white/green | Academy · tuff_workshop | enchanter: best-of-three enchanting now, faster brewing later; bounds over gaps instead of bridging |
| 168 | Dorothea Mixwell | F | alchemist | support | crossbow: syringe crossbow | radiant | shy | triage, guard | zoner | runner I | R | 8 | Medicine bolts heal allies at range | white coat, satchel; white/pink | Commune · tuff_workshop | guard: talent a tier up while home; carries its own 2-4 triage meals; answers alarms at a run |
| 169 | Ignaz Coggleby | M | alchemist | summoner | hammer: wrench hammer | shock | eccentric_genius | engineer, builder | summoner | leaper II | C | 1 | A clockwork golem (iron golem drawn as clockwork) | goggles on the head, leather apron; copper/brown | Academy · tuff_workshop | builder: builds 10-30% faster, faster rails later; bounds up scaffolds two blocks at a time |
| 170 | Maris Coralline | F | siren | controller | instrument: conch | frost | charmer | angler_lord, fisherman | zoner | swimmer II | R | 1 | Siren song pulls foes in; tidal waves | teal hair, fin ears, scale skirt; teal/pearl | Salon · coral_port | fisherman: Luck of the Sea and Lure +1-3; swims to the deepest water |
| 171 | Ondine Shellbright | F | siren | summoner | instrument: whale-song shell | radiant | sweet_natured | mend, guard | summoner | swimmer II | C | 2 | Dolphin spirits leap from her song to shield allies | pearl hair beads, coral staff; coral/white | Hearth · coral_port | guard: talent a tier up while home; heals the most hurt NPC by hand every 20 s; guards the shore and the water gate |
| 172 | Thalassor Wake | M | siren | marksman | polearm: trident | frost | merchant | angler_lord, fisherman | zoner | swimmer III + charger I | E | 2 | Throws tridents that drag foes in a whirlpool; a tidal charge | fin ears, scale armour, trident; deep blue/silver | Company · coral_port | fisherman: Luck of the Sea and Lure +1-3; swims to the deepest water |

### 5.5 Modern, pop and science fiction (28 heroes: 14 F, 14 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | Mobility | R | B | Signature | Look (silhouette; palette) | Leader seed | Work mastery (duty: specialty; mobility at work) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 173 | Hoshimi Utano | F | idol | support | instrument: star light-stick | radiant | genki | festival_host, guard | all_rounder | acrobat II + runner I | C | 3 | Encore: buffs grow with her combo; a spotlight zone | pink twin tails, star hair clips, stage dress; pink/white | Troupe · cherry_court | guard: talent a tier up while home; its festivals last 25-50% longer; vaults to any alarm |
| 174 | Kyoka Sonare | F | idol | controller | instrument: microphone stand | shock | cool_headed | renown, guard | zoner | runner II + acrobat I | R | 4 | Feedback waves from the microphone stand | black bob, rock-stage jacket; black/electric blue | Bureau · cherry_court | guard: talent a tier up while home; Looting +1-3 on its own kills; answers alarms at a run |
| 175 | Haru Kanade | M | idol | skirmisher | fists: dance kicks | blight | charmer | renown, guard | rushdown | acrobat II | C | 3 | Shadow-dance strings of spin kicks; a pose counter | styled hair, tailed stage jacket; white/purple | Salon · cherry_court | guard: talent a tier up while home; Looting +1-3 on its own kills; vaults to any alarm |
| 176 | Lucas Miyabe | M | idol | marksman | instrument: light-up keytar | frost | tsundere | festival_host, guard | zoner | runner II | R | 4 | Cold-light beams sweep the stage and leave frosted ground | slick silver hair, sequined coat; silver/ice blue | League · cherry_court | guard: talent a tier up while home; its festivals last 25-50% longer; answers alarms at a run |
| 177 | Momoka Akebono | F | magical | marksman | staff: heart wand | radiant | genki | mentor, guard | stance | flyer II | C | 5 | Transformation at 60%: a new outfit and a ribbon bind | pink-red curls, frilly outfit; red/white | Troupe · ivory_palace | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; patrols above its own walls |
| 178 | Sorane Hoshizaki | F | magical | vanguard | sword_shield: heart shield and ribbon blade | arcane | tsundere | envoy, guard | stance | hover II | R | 6 | Heart-shield bash; ribbons bind whoever strikes her shield | twin tails, long ribbon; blue/yellow | League · ivory_palace | guard: talent a tier up while home; pacts carried in person +0.1-0.2; drifts over water, mud and gaps |
| 179 | Sho Kagayaki | M | magical | vanguard | sword_shield: transforming armour and sword | shock | righteous | rally, guard | stance | charger II + leaper I | E | 5 | Armour forms at 60%; it grows stronger with each hit he blocks | spiky hair, visor, armour; white/red | Order · ivory_palace | guard: talent a tier up while home; 15-35% chance a recruit payment brings two; holds the gate, shrugs off knockback |
| 180 | Akito Hoshinaga | M | magical | summoner | staff: star sceptre | arcane | airhead | lookout, guard | summoner | flyer II | R | 6 | Star familiars orbit him and fire on his marks | soft hair, star cape; navy/gold | Parade · ivory_palace | guard: talent a tier up while home; alarm +16 blocks from high ground; patrols above its own walls |
| 181 | Edith Lindell | F | maid_butler | skirmisher | staff: reinforced broom | physical | perfectionist | quartermaster, hauler | rushdown | runner II + acrobat I | C | 7 | Sweeping strings and a dust cloud that blinds | classic long maid dress, headdress, glasses; black/navy | Academy · ivory_palace | hauler: carries 30-36 slots, sorts 25-55% faster; hauls at a run |
| 182 | Marianne Tilly | F | maid_butler | summoner | katana: blade hidden in a broom handle | radiant | sweet_natured | master_chef, chef | summoner | runner II | R | 8 | Head maid: rings for the house staff (guards) and two teapot helpers | short maid dress, apron, teapot; pink/white | Hearth · ivory_palace | chef: smoker 20-40% faster, 15-35% chance of a second dish; errands at a run, steps up blocks |
| 183 | Alistair Penwright | M | maid_butler | vanguard | fists: white gloves | physical | stoic | quartermaster, hauler | counter | runner II + charger I | C | 7 | Guards his leader: parries and counters, never leaves his side | tailcoat, white gloves, monocle; black/white | Watch · ivory_palace | hauler: carries 30-36 slots, sorts 25-55% faster; hauls at a run |
| 184 | Reginald Ashby | M | maid_butler | marksman | crossbow: cane crossbow | frost | tsundere | tactician, guard | zoner | runner I + climber I | R | 8 | Precise cane-crossbow shots; a polite bow before each | tailcoat, cane; grey/burgundy | League · ivory_palace | guard: talent a tier up while home; raid parties with it keep formation; answers alarms at a run |
| 185 | Lavinia de Montclair | F | noble_fencer | skirmisher | rapier: rapier | arcane | proud_noble | steward, guard | counter | acrobat II + runner I | C | 1 | Riposte and a lunge chain; her ringlets bounce on each lunge | ringlet curls, ruffled coat; lilac/white | Court · ivory_palace | guard: talent a tier up while home; its talents at full tier at a stewarded base; vaults to any alarm |
| 186 | Armand Valcourt | M | noble_fencer | striker | rapier: rapier and parrying dagger | physical | tsundere | aura (duelist), guard | counter | runner II + acrobat I | R | 1 | Duel challenge: marks one foe and fights it better | blond hair, blue doublet; blue/gold | League · ivory_palace | guard: own duelist +2-4 stacks over the cap; answers alarms at a run |
| 187 | Percival Kingsley | M | noble_fencer | controller | rapier: cane sword | frost | coward_who_rises | steward, guard | counter | runner I | C | 2 | Cane-sword trips stun; a monocle mark | monocle, top hat, cane; grey/plum | Fellowship · ivory_palace | guard: talent a tier up while home; its talents at full tier at a stewarded base; answers alarms at a run |
| 188 | Selene-7 | F | android | vanguard | greatblade: arm blade | shock | stoic | aura (diligence), builder | counter | hover II + charger I | R | 2 | Shield matrix breaks after enough hits; the visor slides down | white synthetic skin seams, long straight cyan-tinted hair, a halo-ring antenna; white/cyan | Watch · tuff_workshop | builder: own diligence +2-4 stacks over the cap; drifts over water, mud and gaps |
| 189 | Vesta Mk II | F | android | marksman | crossbow: shoulder lens cannon | shock | coward_who_rises | engineer, builder | zoner | glider II | C | 3 | Shoulder-lens beam with a long charge tell; her fear protocol overheats, then she steadies | fold-out wing panels, cable ponytail, a round lens over one eye; grey/lilac | Fellowship · tuff_workshop | builder: builds 10-30% faster, faster rails later; glides down from heights to its work |
| 190 | Orrin-4 | M | android | support | focus: repair drones | arcane | eccentric_genius | triage, guard | all_rounder | hover I | R | 3 | Repair drones heal allies | boxy frame, antenna; teal/white | Academy · tuff_workshop | guard: talent a tier up while home; carries its own 2-4 triage meals; drifts over water, mud and gaps |
| 191 | Tetsu-9 | M | android | marksman | fists: rocket fists | fire | battle_maniac | surveyor, miner | zoner | flyer II + charger I | C | 4 | Rocket-punch shots and a rush | heavy frame, exhaust ports; red/steel | Warband · tuff_workshop | miner: Fortune +1-3 on the ores it mines; flies to far mine sites, walks the shafts |
| 192 | Sophia Glasswick | F | tactician | controller | focus: chess pieces | arcane | schemer | tactician, guard | trapper | runner I | R | 4 | Board control: marked squares become zones | glasses, long coat, chess-piece pin; black/white | Circle · tuff_workshop | guard: talent a tier up while home; raid parties with it keep formation; answers alarms at a run |
| 193 | Kasimir Vell | M | tactician | support | polearm: signal-flag spear | arcane | cool_headed | steward, guard | all_rounder | runner II + charger I | C | 5 | Orders: allies gain buffs; guards form up | military coat, flags; navy/red | Bureau · tuff_workshop | guard: talent a tier up while home; its talents at full tier at a stewarded base; answers alarms at a run |
| 194 | Ione Marchetti | F | tactician | marksman | crossbow: pocket crossbow | venom | merchant | spymaster, scout | zoner | runner I + climber I | R | 5 | Predicts a foe's dodge and fires where it will be | glasses, scarf, notebook; green/grey | Company · tuff_workshop | scout: nears rival walls unseen, names the heroes it sees; covers ground at a run |
| 195 | Dunstan Hearthe | M | smith | marksman | crossbow: rivet launcher | fire | gruff_veteran | forgemaster, blacksmith | zoner | charger I + ember_stride I | C | 6 | Rivet bolts heat-stack; an anvil-drop finisher | leather apron, burn scars; brown/orange | Hold · tuff_workshop | blacksmith: crafts 25-55% faster, repairs 30-40% per material; sure-footed under heavy loads |
| 196 | Hilde Coalbright | F | smith | striker | hammer: tongs and hammer | fire | hot_blooded | forgemaster, blacksmith | rushdown | charger II + ember_stride I | R | 6 | Heat stacks make her hammer glow; a quench burst | short hair, soot, apron; black/orange | Vanguard · tuff_workshop | blacksmith: crafts 25-55% faster, repairs 30-40% per material; sure-footed under heavy loads |
| 197 | Ewald Runeforge | M | smith | support | axe: rune axe | arcane | perfectionist | forgemaster, blacksmith | all_rounder | charger I | C | 7 | Rune armour on allies (shields) | braided beard, runic apron; grey/blue | Academy · tuff_workshop | blacksmith: crafts 25-55% faster, repairs 30-40% per material; sure-footed under heavy loads |
| 198 | Paloma Brava | F | grappler | vanguard | fists: wrestling gloves | physical | big_sibling | drill, guard | grappler | leaper II + charger I | R | 7 | Grab-throws, a slam, and a flexing taunt | wrestling mask, cape; red/gold | Household · mud_town | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; bounds up to the wall walk |
| 199 | Nagisa Kurogane | F | grappler | striker | fists: grip gloves | shock | delinquent | mentor, guard | grappler | charger II | C | 8 | Counter throws and grabs | training uniform, black belt; white/black | Crew · mud_town | guard: talent a tier up while home; same-job NPCs near it work 5-10% faster; holds the gate, shrugs off knockback |
| 200 | Daisuke Onoyama | M | grappler | vanguard | fists: ring palms | radiant | gentle_giant | drill, guard | grappler | charger II | R | 8 | Slap barrage, belly push; a salt toss cleanses | topknot, wrestling belt; white/navy | Haven · mud_town | guard: talent a tier up while home; spars at dawn: 2-4 soldiers gain XP; holds the gate, shrugs off knockback |

## 6. Content batches (after the slice)

The 192 remaining heroes ship in 8 batches of 24 (12 female, 12 male). Batches are drawn by striding through the roster, so each batch mixes archetype groups, roles and elements and gives the reviewer a varied contact sheet. Every batch ends with the review gates of HEROES_V2.md section 14.4.

| Batch | Heroes (#) | Roles | Elements |
|---|---|---|---|
| 1 | 2, 4, 19, 20, 36, 37, 51, 54, 70, 71, 85, 88, 103, 104, 120, 122, 135, 141, 153, 154, 169, 170, 185, 186 | vanguard 3, striker 7, skirmisher 2, marksman 2, support 1, controller 4, summoner 5 | physical 4, fire 4, frost 3, shock 2, venom 4, blight 2, arcane 3, radiant 2 |
| 2 | 3, 5, 21, 23, 38, 39, 53, 57, 72, 73, 89, 90, 105, 106, 123, 125, 136, 142, 155, 156, 171, 172, 187, 188 | vanguard 4, striker 3, skirmisher 5, marksman 4, support 2, controller 4, summoner 2 | physical 1, fire 4, frost 4, shock 2, venom 5, blight 3, arcane 2, radiant 3 |
| 3 | 7, 8, 24, 25, 40, 43, 55, 58, 75, 78, 91, 92, 107, 109, 124, 129, 139, 143, 157, 158, 173, 175, 189, 190 | vanguard 2, striker 2, skirmisher 4, marksman 4, support 6, controller 3, summoner 3 | physical 3, fire 5, frost 1, shock 5, venom 2, blight 4, arcane 2, radiant 2 |
| 4 | 9, 10, 26, 28, 41, 44, 59, 62, 76, 79, 93, 95, 110, 111, 126, 130, 140, 145, 159, 160, 174, 176, 191, 192 | vanguard 3, striker 3, skirmisher 2, marksman 5, support 3, controller 7, summoner 1 | physical 4, fire 2, frost 3, shock 3, venom 2, blight 3, arcane 2, radiant 5 |
| 5 | 11, 12, 27, 29, 42, 45, 60, 63, 77, 80, 94, 96, 112, 113, 127, 132, 144, 146, 161, 162, 177, 179, 193, 194 | vanguard 3, striker 3, skirmisher 3, marksman 5, support 5, controller 2, summoner 3 | physical 2, frost 6, shock 4, venom 2, blight 4, arcane 3, radiant 3 |
| 6 | 13, 14, 30, 31, 46, 47, 61, 66, 81, 84, 97, 98, 114, 116, 128, 134, 147, 148, 163, 165, 178, 180, 195, 196 | vanguard 4, striker 3, skirmisher 3, marksman 4, support 2, controller 4, summoner 4 | physical 3, fire 6, frost 2, shock 2, venom 3, blight 2, arcane 4, radiant 2 |
| 7 | 15, 16, 32, 33, 48, 50, 64, 67, 82, 86, 99, 100, 115, 117, 131, 137, 149, 151, 164, 167, 181, 183, 197, 198 | vanguard 4, striker 4, skirmisher 4, marksman 1, support 4, controller 3, summoner 4 | physical 6, fire 3, frost 3, shock 1, venom 2, blight 2, arcane 5, radiant 2 |
| 8 | 17, 18, 34, 35, 49, 52, 68, 69, 83, 87, 101, 102, 118, 119, 133, 138, 150, 152, 166, 168, 182, 184, 199, 200 | vanguard 4, striker 3, skirmisher 4, marksman 2, support 5, summoner 6 | physical 1, frost 3, shock 5, venom 4, blight 3, arcane 3, radiant 5 |

## 7. From a row to a finished hero

1. `herogen new <n>` scaffolds `herosrc/heroes/<id>.hero.json` from the row, the archetype template (`herosrc/archetypes/<archetype>.json`: default parts, ears and tails, base style, banner emblem, naming motifs, kit patterns) and the temperament template (`herosrc/temperaments/<temperament>.json`: trait defaults, fight-AI defaults, expression overrides, fallback lines).
2. The author (an agent, one batch task per 4 heroes) turns the row's signature into a kit: 4-6 moves over 2-3 phases with one new mechanic per phase, a signature tell (pose, sound, particle colour), a punish window, at least one weakness, reactions to blocking, bows and eating; then the art block (parts, palette, face, weapon, phase looks), the signature animation recipes, personality traits, the leader block (two faction names, short name, motto, colours, banner, base style, culture, economy focus), 2-3 talents and about 50 dialogue lines.
3. `herogen build` generates the model, texture, animations and server data; `herogen check` and `./gradlew checkHeroes` validate them (HEROES_V2.md section 14).
4. `herogen sheet` renders the batch contact sheet; the in-game lineup screenshots follow (section 14.4 there).

## 8. Name and originality clearance (per batch, before shipping)

1. **Name search**: each full name, and each faction name the hero brings, is searched on the web together with its archetype words. A hit on a character, a real person (streamers' personas included) or a published group with a similar concept replaces the name. Common given names are acceptable when the full name and the concept are not a match.
2. **Concept search**: the archetype + signature + palette + weapon combination is searched; a match with a known character changes at least two of palette, weapon, signature or silhouette.
3. **Visual review**: the reviewer (a separate agent) compares the contact sheet against the rule above and lists any hero that reads as a known character; the owner's screenshot review is the final gate.
4. Results are recorded in `herosrc/clearance/<batch>.md` (name, queries, verdict), so later batches do not reuse a rejected name.
5. **What a search is looking for** (from the first desk review, `CRITIQUE.md`): an invented given name or surname shared with a known character of the same archetype or gender-swapped twin (a female ninja surnamed like a known ninja retainer, a magical girl named like a magical-girl series lead, an elf sharing a rare invented elf name); a surname that is a real performer's stage name in the same art (kabuki, dance schools); and a look that repeats a famous design's two or three defining marks (hair style, outfit colours and the signature prop together). That review already renamed rows 5, 75, 82, 84, 86, 129, 132, 169, 178 and 180 and changed the look of rows 46, 96, 98, 188 and 189; the web searches of steps 1-2 are still due for every batch.

## 9. Leaders

Every hero is authored as leader-eligible: it carries a full `leader` block, so any of the 200 can found a faction. The leader draft (HEROES_V2.md section 5.2) picks distinct archetypes, distinct colours, both genders and a mix of aggressive and peaceful temperaments. Examples of contrasting four-leader casts this roster supports: battle maniac oni (22) + schemer fox (6) + sweet-natured shrine maiden (56) + stoic samurai (65) (the slice's campaign run A); tsundere cat (1) + righteous knight (108) + eccentric warlock (121) + lone-wolf ninja (74) (run B); tyrant demon queen (135) + gentle-giant oni (25) + merchant dragon (41) + yandere vampire (131).

