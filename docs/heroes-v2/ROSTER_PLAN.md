# Heroes v2: roster plan (200 original heroes)

Status: plan, 2026-11-12. Companion of `HEROES_V2.md` (the design) and `SLICE_PLAN.md` (the first milestone). The tables are generated from `roster/roster_data.py` by `roster/gen_roster_md.py`; `roster/check_roster.py` enforces every count and uniqueness rule below and prints `OK` for this version. `roster/roster.json` is the machine-readable copy that the hero scaffolder (`herogen new <n>`, HEROES_V2.md section 13.7) reads.

## 1. Rules this roster follows

- **Original characters only.** Every hero is built from generic archetypes (folklore creature types, occupations, genre roles, personality tropes). No existing character, franchise, game, anime or real person is named, described or imitated. What makes a hero original is the combination: archetype, personality, element, weapon, signature mechanic, silhouette, palette and name. Combinations that are the premise of a known work were avoided while drafting, and every row passes the originality review of section 8 in its batch before it ships.
- **Adults, tasteful outfits.** Every hero is an adult (petite builds included); outfits are modest, so the mod suits players of any age. Youthful looks are a style, not an age.
- **Exactly 100 female and 100 male heroes.** Each archetype has the gender split shown in section 2.3; archetypes that media usually codes one way have at least one hero of the other gender (shrine priests, a male fan dancer, butlers, female samurai and grapplers, and so on). Every role is split evenly (section 2.1).
- **No two heroes share a look and kit.** `check_roster.py` rejects a repeated look key (archetype + palette), kit key (archetype + weapon style + element), full key (archetype, role, style, element, temperament), signature line, name or id. At authoring time the format validator adds a second check over the finished kits (same archetype, element, style, ultimate form and signature move effects: HEROES_V2.md section 14.1).
- **Balance targets (enforced):** each role 28-30 heroes with a split within one; each element 24-26; each weapon style 9-17; each temperament 5-9; each of the 35 talents at least twice; every duel style present.
- **Working names.** Names follow the hero's culture (Japanese-style for yokai and Japanese archetypes, Chinese-style for wuxia, invented European-style for Western fantasy, mixed for modern ones). They are working names until the clearance step of section 8 passes for the batch.
- **The slice heroes are marked [S]**; they are designed in full in `SLICE_PLAN.md`.

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

Rarity sets the draw weight (leaders 4/4/2/1 for common/rare/epic/legendary, tokens 8/4/2/1) and the boss phase count (common and rare: 2 phases, the third unlocks at level 15; epic and legendary: 3 phases; only legendary heroes may use `rebirth`). It does not change raw numbers beyond the boss `health_scale` default (HEROES_V2.md section 6.2). All eight slice heroes are epic so the slice exercises three phases.

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

**Specialty** is the hero's primary non-combat talent (HEROES_V2.md section 7.2), then the duty it works when it is not leading (`court` replaces the duty while it leads). **R** is the rarity (L, E, R, C). **B** is the content batch (S = the slice, 1-8 = section 6). **Leader seed** is the faction group noun its temperament suggests and the base style family its archetype suggests; the hero file's `leader` block has the final faction names, motto, colours and banner (HEROES_V2.md section 5.3).

## 4. The vertical slice (8 heroes, 4 female and 4 male)

| # | Name | G | Archetype | Role | Style | Element | Temperament | Faction it leads (SLICE_PLAN.md) |
|---|---|---|---|---|---|---|---|---|
| 1 | Natsuho Komezaki | F | cat | striker | fists | physical | tsundere | Bellwhisker Harbour |
| 6 | Yugen Kagaribi | M | fox | marksman | focus | arcane | schemer | Veiled Lantern Exchange |
| 22 | Benika Akagane | F | oni | vanguard | hammer | fire | battle_maniac | Crimson Anvil Warband |
| 56 | Hinae Shirasuzu | F | shrine | support | focus | radiant | sweet_natured | Hearth of the White Bell |
| 65 | Kagenori Tsurumine | M | samurai | striker | katana | shock | stoic | House of the Still Crane |
| 74 | Tobimaru Usugumo | M | ninja | skirmisher | twin_blades | blight | lone_wolf | Quiet Ash Clan |
| 108 | Ilsabet Frostmere | F | knight | vanguard | sword_shield | frost | righteous | Order of the Rime Vow |
| 121 | Ambrose Nettlewick | M | witch | controller | staff | venom | eccentric_genius | Nettlewick Conclave |

They cover all eight elements, five of the eight duel styles (rushdown, zoner, grappler, counter, trapper), six of the seven roles (two vanguards and two strikers in different duel styles; the summoner role is first exercised by batch 1), eight different temperaments chosen for contrast as leaders, and seven weapon styles (fists, focus, hammer, katana, twin_blades, sword_shield, staff).

## 5. The 200 heroes

### 5.1 Animal-kin and yokai (55 heroes: 27 F, 28 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Natsuho Komezaki **[S]** | F | cat | striker | fists: calico claw gauntlets | physical | tsundere | angler_lord, fisherman | rushdown | E | S | Pounce that bleeds; at 30% 'nine lives' rolls away with a shield and speed | bob cut, tufted ears, bell choker; calico white/orange/black | League · tide_port |
| 2 | Shirotae Mikage | F | cat | controller | focus: silver yarn spools | arcane | sleepy | pathfinder, scout | trapper | E | 1 | Cat's-cradle threads tether foes between two marks; naps to heal (breakable channel) | long white low tails, droopy ears, big cardigan; white/lavender | Lodge · tide_port |
| 3 | Kuroha Yamikane | F | cat | marksman | crossbow: hand crossbow | blight | lone_wolf | lookout, guard | zoner | C | 2 | Night eyes: marked foes glow and take more from her bolts in darkness | hooded black coat with ear slits, one fang; black/gold | Pack · tide_port |
| 4 | Rokusuke Amanawa | M | cat | summoner | twin_blades: claw knives and a bell whistle | shock | delinquent | beastmaster, animal_carer | summoner | R | 1 | Alley king: three street cats answer his whistle; static fur shocks whoever hits him | broad tabby, headband, bell on a cord; grey tabby/red | Crew · tide_port |
| 5 | Torakichi Fumizuki | M | cat | skirmisher | twin_blades: curved claw daggers | venom | trickster | spymaster, scout | rushdown | C | 2 | Afterimage pounce: three dashes, each leaves a smoke decoy | messy hair, torn ear, striped scarf; tuxedo black/white/green | Revel · tide_port |
| 6 | Yugen Kagaribi **[S]** | M | fox | marksman | focus: foxfire paper lanterns | arcane | schemer | merchant_prince, trader | zoner | E | S | Foxfire volleys; tails 3, 6, 9 by phase; decoys that cast no shadow | silver half-up hair, fox mask on the side, plum court robe; plum/silver | Circle · cherry_court |
| 7 | Inaho Kogane | F | fox | support | fan: golden dance fans | fire | ancient_sage | harvest_blessing, farmer | all_rounder | L | 3 | Fox-wedding rain: sunshower zones heal allies and scorch foes, stronger in rain | red-gold hair, four fluffy tails, rice-straw hairpin; vermilion/white/gold | Conclave · cherry_court |
| 8 | Kazato Kurobi | M | fox | skirmisher | polearm: cross-bladed spear | shock | genki | pathfinder, scout | rushdown | R | 3 | Zigzag dashes: three Z-pattern spear thrusts, ears perk before each | spiky black hair, crimson tail tips, short coat; black/crimson | Troupe · cherry_court |
| 9 | Sayoe Kagamura | F | fox | controller | focus: bronze hand mirror | blight | charmer | envoy, guard | trapper | E | 4 | Mirror decoys repeat her casts; the real one carries the cracked mirror | black hime cut, black ears with white tips; black/plum/silver | Salon · cherry_court |
| 10 | Hakuen Toriimae | M | fox | summoner | staff: ringed priest staff | radiant | big_sibling | revival_rite, guard | summoner | R | 4 | Paper fox familiars; a staff ring calls them back to shield him | white fox priest, red eye-liner marks; white/red/gold | Household · cherry_court |
| 11 | Torvald Ashpelt | M | wolf | summoner | axe: bearded axe | frost | gruff_veteran | beastmaster, animal_carer | summoner | C | 5 | Two named wolves flank his marks; if both fall he staggers (broken bond) | grey shaggy hair, scar, frost-rimed fur cloak; grey/navy | Hold · grove_lodge |
| 12 | Ylva Rimeclaw | F | wolf | striker | greatblade: bone-hilted greatsword | frost | hot_blooded | forester, woodcutter | rushdown | E | 5 | Moon frenzy: at night or under half health she grows, locks skills and never stops swinging | white wild hair, amber eyes, snow-fur mantle; white/amber | Vanguard · grove_lodge |
| 13 | Rusca Brannick | F | wolf | skirmisher | bow: recurve hunting bow | venom | lone_wolf | cartographer, scout | zoner | C | 6 | Scent marks leave glowing tracks; she dashes to the most-marked foe | russet braid, notched ear, hunter's hood; russet/black | Pack · grove_lodge |
| 14 | Hyogo Arashiba | M | wolf | support | instrument: bone war horn | fire | yandere | rally, guard | all_rounder | R | 6 | Den horn: a howl strengthens his pack; whoever wounds his favoured ally becomes his only target | charcoal mane, broad build, bone horn on a strap; charcoal/ember orange | Covenant · grove_lodge |
| 15 | Tonbei Morikawa | M | tanuki | support | instrument: belly drum and gourd | physical | glutton | master_chef, chef | all_rounder | C | 7 | Belly drum: a booming ring knocks foes back and speeds allies; two belly pats are the tell | round build, straw hat, leaf on his head; brown/leaf green | Guild · mud_town |
| 16 | Mamemi Tsuzumi | F | tanuki | vanguard | hammer: giant gourd mallet | venom | glutton | merchant_prince, trader | grappler | R | 7 | Giant form in phase 2 shrugs off knockback; a belly-flop slam; sake-mist puddles | short brown bob, round ears, oversized merchant coat; brown/teal | Guild · mud_town |
| 17 | Kobun Hachiya | M | tanuki | summoner | fan: leaf fan | arcane | airhead | quartermaster, hauler | summoner | C | 8 | Leaf fan conjures three tanuki helpers that bite, then pop like decoys | huge straw hat, round belly sash; brown/red | Parade · mud_town |
| 18 | Kinue Mochizuki | F | moon_rabbit | skirmisher | hammer: rice-cake mallet | arcane | genki | master_chef, chef | rushdown | R | 8 | Moon drop: a huge leap with a growing shadow; the mallet sticks after | long white hair, one floppy ear, short kimono; white/pink | Troupe · ivory_palace |
| 19 | Tsukime Hanagata | F | moon_rabbit | summoner | staff: mochi-pounding pestle staff | radiant | shy | harvest_blessing, farmer | summoner | C | 1 | Moon-bunny helpers pound rice cakes that heal allies and kick foes | silver twin buns, ribbons on the ears, kimono apron; silver/peach | Commune · ivory_palace |
| 20 | Shogetsu Tobita | M | moon_rabbit | marksman | bow: short moon bow | frost | coward_who_rises | lookout, guard | zoner | R | 1 | Crescent volley lands as a crescent of frost zones; jumpy until an ally falls, then steady | tall white ears, navy hooded cloak; navy/white | Fellowship · ivory_palace |
| 21 | Tetsuhei Hanetsuki | M | moon_rabbit | striker | katana: moon-steel katana | shock | hot_blooded | drill, guard | rushdown | C | 2 | Hopping draw-cuts: each leap ends in a falling slash, higher each time | short black hair, black ears, track jacket over a sash; black/red | Vanguard · ivory_palace |
| 22 | Benika Akagane **[S]** | F | oni | vanguard | hammer: iron-studded club | fire | battle_maniac | surveyor, miner | grappler | E | S | Overhead club slam (the club sticks); phase 2 rage; phase 3 grab and throw | single red horn, wild black hair, crimson coat over wraps; crimson/black | Warband · ember_fortress |
| 23 | Sekka Aoizumi | F | oni | vanguard | greatblade: cleaver-sized long blade | frost | gruff_veteran | architect, builder | all_rounder | E | 2 | Frost-breath cone, then she plants the blade; ice armour that breaks after enough hits | twin small blue horns, white hair, indigo trousers; ice blue/indigo | Hold · ember_fortress |
| 24 | Kiyome Hanakado | F | oni | skirmisher | fists: iron bracers | physical | hot_blooded | quartermaster, hauler | rushdown | R | 3 | Rolling shoulder charges; slam her into a wall to stagger her | two short yellow horns, high ponytail, rope belt; mustard/charcoal | Vanguard · ember_fortress |
| 25 | Ganzo Ishidoro | M | oni | vanguard | sword_shield: temple-door shield and club | physical | gentle_giant | architect, builder | counter | C | 3 | Door-shield wall: the planted door shelters allies; attack his sides | huge build, two curved green horns, straw cape; moss green/brown | Haven · ember_fortress |
| 26 | Gonroku Sakabayashi | M | oni | skirmisher | axe: twin hatchets and a gourd | venom | delinquent | renown, guard | rushdown | R | 4 | Throwing hatchets on an off-beat rhythm; a poisoned mist cone from his gourd | blue skin tint, one broken horn, open festival coat; blue/white | Crew · ember_fortress |
| 27 | Kagehisa Tobikiri | M | tengu | skirmisher | fan: feather war fan | physical | proud_noble | cartographer, scout | zoner | C | 5 | Gale-fan cones push foes; dives from above onto a landing marker | black crow wings, small black cap, one-tooth clogs; black/red | Court · stone_hold |
| 28 | Tsubame Kakeyama | F | tengu | marksman | bow: mountain longbow | shock | lone_wolf | lookout, guard | zoner | R | 4 | Arrows call harmless lightning flashes on marked foes, then a bolt chain | red long-nosed mask pushed up, white hair, crow wings; red/white | Pack · stone_hold |
| 29 | Shizuki Minekaze | F | tengu | support | fan: feather fan | frost | stoic | mend, guard | all_rounder | C | 5 | Healing gusts from her feather fan carry cleanses along a line; meditates mid-fight (breakable) | pom-pom sash, small cap, white robe, grey wings; white/sage | Watch · stone_hold |
| 30 | Ojin Atagomaru | M | tengu | vanguard | polearm: feathered glaive | shock | righteous | warden_of_walls, guard | counter | E | 6 | Wind wall turns arrows aside while he advances; get round it | red long-nosed mask, huge brown wings, monk armour; brown/gold | Order · stone_hold |
| 31 | Sayuki Shimotsuki | F | snow_spirit | controller | fan: white frost fan | frost | melancholic | purifier, guard | trapper | C | 6 | Whiteout: freezing zones and a breath cone; she drifts back as you close in | very long white hair, snowflake kimono, floating hem; white/ice blue | Vigil · frost_hall |
| 32 | Koyuki Wataboshi | F | snow_spirit | summoner | focus: snow lanterns | frost | coward_who_rises | quartermaster, hauler | summoner | R | 7 | Snow children (snow golems drawn as sprites) shield her; she turns brave when they fall | short white bob, snow-rabbit hood, mittens; white/pink | Fellowship · frost_hall |
| 33 | Itetsu Shirane | M | snow_spirit | marksman | polearm: ice javelins | frost | melancholic | forester, woodcutter | zoner | E | 7 | Hurls ice javelins that leave frozen ground; a glacier-skin shell when cornered | tall, pale, crown of ice shards; pale blue/grey | Vigil · frost_hall |
| 34 | Genkichi Kawabe | M | kappa | vanguard | sword_shield: turtle-shell shield and short spear | frost | big_sibling | drill, guard | counter | R | 8 | Shell wall; a critical hit from above spills his head-dish and staggers him | green-tinted skin, shell on the back, dish on his head; green/yellow | Household · tide_port |
| 35 | Minamo Kawazoe | F | kappa | skirmisher | polearm: fishing trident | frost | genki | angler_lord, fisherman | rushdown | C | 8 | Slides fast along water; reels foes in with a fishing line (tether) | teal bob with a dish hairclip, shell backpack, raincoat; teal/yellow | Troupe · tide_port |
| 36 | Soan Kawaguchi | M | kappa | support | staff: lotus staff | venom | gentle_giant | triage, guard | all_rounder | R | 1 | Bone-setter: heals by holding an ally still; slime puddles slow foes | lotus-leaf hat, shell, medicine box; green/white | Haven · tide_port |
| 37 | Ayaori Kumode | F | spider_weaver | controller | focus: silk threads | venom | yandere | aura (hardy), hauler | trapper | E | 1 | Web fields tether; six spectral legs wind up an unblockable grab | black-and-gold kimono, six thin legs on the back, hime cut; black/gold | Covenant · dark_manor |
| 38 | Itoha Kurozome | F | spider_weaver | skirmisher | twin_blades: silk-bound sickles | blight | schemer | spymaster, scout | trapper | R | 2 | Drops on a thread from above and retreats up it; hidden traps | pale, black braid, eye-motif hair ornaments; black/violet | Circle · dark_manor |
| 39 | Joroku Itosaki | M | spider_weaver | summoner | scythe: silk-reel sickle | venom | tyrant | tribute_master, trader | summoner | C | 2 | Spiderling broods hatch from web sacs; reeling silk drags them onto his mark | tall, white hair with a black streak, grey robes; grey/black | Dominion · dark_manor |
| 40 | Mizuchi Haruaki | M | east_dragon | support | staff: cloud-pearl staff | frost | ancient_sage | harvest_blessing, farmer | all_rounder | L | 3 | An orbiting pearl heals allies it passes and strikes foes | long antlered horns, flowing jade hair, whiskers; jade/gold | Conclave · coral_port |
| 41 | Shotatsu Korin | M | east_dragon | striker | katana: dragon-tooth sword | fire | merchant | forgemaster, blacksmith | all_rounder | E | 4 | Pearl-roar cone; coils with super armour before a spinning cleave | red antlers, red scales on the cheeks, golden robe; red/gold | Company · coral_port |
| 42 | Raishin Kumokiri | M | east_dragon | marksman | bow: cloud bow | shock | cool_headed | sage_of_letters, enchanter | zoner | R | 5 | Storm arrows chain lightning between wet targets (rain, water) | black hair, short horns, scholar's robe; black/teal | Bureau · coral_port |
| 43 | Seika Ryugasaki | F | east_dragon | summoner | katana: dragon-pearl sword | arcane | proud_noble | envoy, guard | summoner | L | 3 | Two cloud-serpent wisps orbit her and strike where her sword points | tall, white hair with blue tips, long horns; white/azure | Court · coral_port |
| 44 | Shion Mikumari | F | east_dragon | striker | fists: scaled bracers | radiant | battle_maniac | steward, guard | rushdown | R | 4 | Dragon-palm string ends in a rising uppercut; a tail sweep catches jumpers | short teal hair, horn nubs, sleeveless training top; teal/orange | Warband · coral_port |
| 45 | Vexa Thornscale | F | west_dragon | vanguard | hammer: wyrm-tail mace | venom | tyrant | surveyor, miner | all_rounder | E | 5 | Tail-sweep circle and an acid wing buffet; chained hop-slams in phase 3 | curled horns, green scales, dark armour; emerald/bronze | Dominion · deepslate_keep |
| 46 | Ignatia Vermillor | F | west_dragon | marksman | crossbow: dragon-bone arbalest | fire | chuunibyou | ember_keeper, smelter | zoner | R | 6 | Every third bolt bursts into a fire cone; she strikes a pose and announces her ultimate by name before it fires | black hair with red streaks, small horns, a too-long high-collared scarlet cape; black/scarlet | Legion · deepslate_keep |
| 47 | Brannoc Emberhide | M | west_dragon | striker | axe: obsidian greataxe | fire | hot_blooded | forgemaster, blacksmith | rushdown | C | 6 | Molten core: fire builds heat stacks, then an eruption ring | crown-like horns, glowing magma cracks; black/orange | Vanguard · deepslate_keep |
| 48 | Aurelian Wyrmholt | M | west_dragon | summoner | axe: drake-handler's hatchet | arcane | eccentric_genius | sage_of_letters, enchanter | summoner | E | 7 | Hatchling drakes orbit him and breathe sparks at his marks | scholar's coat, small wings, monocle, silver hair; silver/purple | Academy · deepslate_keep |
| 49 | Goen Ishigaki | M | komainu | vanguard | fists: stone paws | physical | stoic | warden_of_walls, guard | counter | C | 8 | Open-mouthed roar cone; stone skin while he stands still | curly mane, stone grey with moss, shrine rope; stone/moss | Watch · shrine_pavilion |
| 50 | Unme Ishigaki | F | komainu | vanguard | sword_shield: stone tablet shield and short sword | radiant | shy | lookout, guard | counter | R | 7 | Closed-mouth seal: a silence aura on enemy heroes; her tablet shield shelters allies | curly-mane bob, stone grey with red rope; stone/red | Commune · shrine_pavilion |
| 51 | Koshi Kagurazaka | M | komainu | striker | instrument: lion-dance head and cymbals | fire | genki | festival_host, guard | rushdown | C | 1 | Lion-dance bites on a drum rhythm; the cymbal crash stuns | big red lion-dance head over his shoulder, green cloth; red/green | Troupe · shrine_pavilion |
| 52 | Hanae Momozono | F | plant_spirit | vanguard | hammer: oak-root maul | venom | gentle_giant | harvest_blessing, farmer | all_rounder | R | 8 | Root wall: plants herself and grows bark armour; flowers bloom on allies she shelters | flower crown, leafy hair with petals, petal dress; pink/leaf green | Haven · jungle_haven |
| 53 | Corwin Barkhollow | M | plant_spirit | skirmisher | rapier: thorn whip-sword | venom | coward_who_rises | forester, woodcutter | rushdown | C | 2 | Thorn-whip lashes from four blocks away; when an ally falls he roots, blooms and turns bold | bark-skin arms, moss hair, small branch antlers; brown/green | Fellowship · jungle_haven |
| 54 | Calyx Wren | F | plant_spirit | summoner | instrument: flower flute | venom | airhead | aura (regrowth), woodcutter | summoner | R | 1 | Seedling sprites sprout where her flute notes land; vines tether whoever stays | orchid hair, vine-lace sleeves; violet/green | Parade · jungle_haven |
| 55 | Morel Underbough | M | plant_spirit | summoner | scythe: mushroom-harvest sickle | blight | sleepy | forester, woodcutter | summoner | C | 3 | Spore sprites rise from his rot zones | big mushroom-cap hat, mossy cloak, glowing spots; brown/teal | Lodge · jungle_haven |

### 5.2 Shrine, court and martial Japan (38 heroes: 19 F, 19 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 56 | Hinae Shirasuzu **[S]** | F | shrine | support | focus: purification wand and talismans | radiant | sweet_natured | mend, guard | zoner | E | S | Talisman fans and sealing circles; a barrier of seven talismans in phase 2 | long black hair tied low with white paper, red trousers, bell cluster; red/white | Hearth · shrine_pavilion |
| 57 | Yuzuri Hojo | F | shrine | controller | instrument: bell staff | arcane | stoic | purifier, guard | trapper | E | 2 | Bell rings stun spirits; rotating rings of bells form zones | silver hair, white kimono with gold, bell staff; white/gold | Watch · shrine_pavilion |
| 58 | Chigusa Hiwada | F | shrine | striker | polearm: curved glaive | fire | righteous | drill, guard | all_rounder | R | 3 | Purifying-flame glaive sweeps; a kneeling vow before her ultimate | high ponytail, tied-back sleeves, headband; red/black | Order · shrine_pavilion |
| 59 | Masaomi Tomine | M | shrine | support | staff: paper-streamer wand | radiant | big_sibling | revival_rite, guard | all_rounder | C | 4 | Purifying sweep dispels foes and cleanses allies; prays (breakable) | pale blue robe, purple trousers, streamer wand; pale blue/purple | Household · shrine_pavilion |
| 60 | Kozen Torii | M | shrine | vanguard | hammer: sacred-rope mallet | physical | gruff_veteran | warden_of_walls, guard | grappler | R | 5 | Sacred rope binds foes inside a boundary; holds the gate | shaved head, sacred rope over the shoulders, white robes; white/straw | Hold · shrine_pavilion |
| 61 | Harutaka Hoshinomiya | M | onmyoji | summoner | focus: paper familiars | blight | cool_headed | sage_of_letters, enchanter | summoner | E | 6 | Shadow-paper beasts fight for him; a five-point seal binds the arena edge | tall black court cap, white and indigo hunting robe; white/indigo | Bureau · shrine_pavilion |
| 62 | Tsukasa Yomei | F | onmyoji | controller | focus: star-seal talismans | arcane | eccentric_genius | aura (prospector), miner | stance | E | 4 | Five phases: her element cycles each cast; the robe colour shows which | violet court cap tilted, round glasses, star-embroidered robe; violet/white | Academy · shrine_pavilion |
| 63 | Kotoyo Kamo | F | onmyoji | summoner | fan: paper-crane fan | frost | ancient_sage | harvest_blessing, farmer | summoner | L | 5 | Paper cranes fold out of her fan; divination marks the next foe to strike | long grey hair, youthful face, black court robe; black/gold | Conclave · shrine_pavilion |
| 64 | Seimon Hoshikage | M | onmyoji | controller | staff: astrolabe staff | blight | schemer | spymaster, scout | trapper | E | 7 | Star prison: a five-point zone stuns when its lines close; stay outside them | white hair, star-map robe, eye patch; black/silver | Circle · shrine_pavilion |
| 65 | Kagenori Tsurumine **[S]** | M | samurai | striker | katana: long and short swords | shock | stoic | drill, guard | counter | E | S | Quick-draw counter stance and a thunderclap draw; helmet in phase 2, two swords in phase 3 | high ponytail, indigo coat with crane crest; indigo/white | Watch · deepslate_keep |
| 66 | Chiyo Akazonae | F | samurai | vanguard | polearm: straight spear | physical | righteous | rally, guard | counter | R | 6 | Lord's-banner zone strengthens allies; a spear-wall sweep | red lacquer armour, wave-crest helmet; red/black | Order · deepslate_keep |
| 67 | Asahi Nagakura | F | samurai | striker | greatblade: long field sword | fire | hot_blooded | mentor, guard | rushdown | E | 7 | Rising-sun cut: a spinning cleave with a fire trail; enrages early | short red hair, white trousers, long sword on the back; white/red | Vanguard · deepslate_keep |
| 68 | Shiori Mitsukado | F | samurai | marksman | bow: tall longbow | shock | perfectionist | lookout, guard | zoner | R | 8 | Kneel-and-aim piercing shots; each miss sharpens her focus | long black hair tied with purple cord, archery chest guard; black/purple | Academy · deepslate_keep |
| 69 | Sokichi Ishizumi | M | samurai | support | katana: brush blade | blight | proud_noble | mentor, guard | all_rounder | C | 8 | Ink strokes from his blade write zones that bless allies or curse foes; a tea ceremony heals all | neat side-part, black formal vest; black/beige | Court · deepslate_keep |
| 70 | Saburo Kazamaki | M | wanderer | striker | katana: sheathed blade | physical | melancholic | forester, woodcutter | counter | E | 1 | Single stroke: 1.5 s of stillness, then a line cut; the re-sheathe is the opening | wide straw hat, worn brown kimono, chin scar; brown/teal | Vigil · bamboo_village |
| 71 | Kozue Hagino | F | wanderer | skirmisher | katana: short blade and needles | venom | charmer | pathfinder, scout | rushdown | C | 1 | Petal step: slips past foes leaving petal cuts; the veiled hat flips first | veiled travelling hat, dusty-rose kimono; dusty rose/grey | Salon · bamboo_village |
| 72 | Rindo Akizuki | F | wanderer | striker | greatblade: horse-cutter blade | physical | battle_maniac | aura (duelist), guard | all_rounder | R | 2 | Wide sweeps with long recoveries; drinks between bouts | tall, scar across the nose, travelling cloak; ochre/black | Warband · bamboo_village |
| 73 | Teppei Hinawa | M | wanderer | marksman | crossbow: matchlock | fire | sleepy | intimidation, guard | zoner | C | 2 | Slow aim with a glint tell, one heavy shot, a long reload to punish | unshaven, black coat, matchlock on the shoulder; black/rust | Lodge · bamboo_village |
| 74 | Tobimaru Usugumo **[S]** | M | ninja | skirmisher | twin_blades: short blade and kunai | blight | lone_wolf | cartographer, scout | rushdown | E | S | Vanishes (footstep puffs give him away), strikes from behind; decoys in phase 2 | navy mask, long red scarf, high ponytail; navy/red | Pack · bamboo_village |
| 75 | Sumire Ayatsuki | F | ninja | marksman | focus: throwing stars | venom | glutton | pathfinder, scout | zoner | C | 3 | Poisoned star fans and caltrop fields; she stops mid-fight for a snack (punish window) | lavender hair, short scarf, star pouch; lavender/black | Guild · bamboo_village |
| 76 | Iori Shinobazu | F | ninja | controller | scythe: chain sickle | blight | schemer | spymaster, scout | trapper | R | 4 | Smoke field darkens; her chain sickle drags foes into it | black bob, half mask, coiled chain; black/yellow | Circle · bamboo_village |
| 77 | Mayu Hyosa | F | ninja | skirmisher | scythe: sickle on a chain | frost | yandere | lookout, guard | rushdown | C | 5 | Frost sickle chain pulls and cuts; an ice-slide dash closes the gap | white braid, pale blue scarf; white/ice blue | Covenant · bamboo_village |
| 78 | Zenji Karakuri | M | ninja | controller | crossbow: repeating hand crossbow and tripwires | shock | trickster | tactician, guard | trapper | R | 3 | Tripwire grid: shock lines between posts; a smoke escape | goggles on the forehead, utility vest; olive/brown | Revel · bamboo_village |
| 79 | Kakuzen Toyama | M | warrior_monk | vanguard | polearm: monk's glaive | physical | perfectionist | drill, guard | counter | C | 4 | Iron body: shield and taunt; a half-circle glaive sweep | white head wrap, robes over armour, big prayer beads; white/saffron | Academy · stone_hold |
| 80 | Jikai Morino | M | warrior_monk | support | fists: prayer-bead fists | radiant | gentle_giant | mend, guard | counter | R | 5 | Chanting aura heals; meditation stance answers melee | bald, saffron robes, prayer beads; saffron/brown | Haven · stone_hold |
| 81 | Soka Yamanobe | F | warrior_monk | skirmisher | staff: iron staff | shock | chuunibyou | pathfinder, scout | rushdown | C | 6 | Staff-spin barrage and a pole-vault leap | short hair, white ascetic robe, beads; white/teal | Legion · stone_hold |
| 82 | Kotoha Fujinami | F | fan_dancer | controller | fan: twin dance fans | arcane | charmer | envoy, guard | trapper | E | 7 | Petal dance: rotating cones; charmed soldiers briefly turn on their own side | updo with hairpins, long-sleeved layered kimono; pink/gold | Salon · cherry_court |
| 83 | Natsuri Hanabishi | F | fan_dancer | support | fan: ribbon fans | radiant | genki | festival_host, guard | all_rounder | C | 8 | Festival dance: ally buffs pulse on each beat; tempo builds | twin ponytails with ribbons, festival coat; red/white | Troupe · cherry_court |
| 84 | Kikuzo Hanaoka | M | fan_dancer | skirmisher | fan: steel war fans | physical | proud_noble | renown, guard | counter | R | 6 | Frozen-pose counter, then a whirling fan dash | stage face-paint stripes, flowing mane wig; red/black | Court · cherry_court |
| 85 | Sakie Kurogami | F | delinquent | striker | hammer: spiked wooden bat | physical | delinquent | intimidation, guard | rushdown | C | 1 | Turf war: stronger inside her walls; calls her crew of guards | long sailor skirt, mask pulled down, bleached hair; navy/yellow | Crew · mud_town |
| 86 | Gosuke Banba | M | delinquent | striker | fists: taped fists | shock | delinquent | drill, guard | rushdown | R | 7 | Coat-cape fling blinds; a crackling flying headbutt; a counter-taunt | pompadour, long school coat worn as a cape; black/gold | Crew · mud_town |
| 87 | Daimon Sakaishi | M | delinquent | summoner | katana: wooden practice sword | shock | tyrant | renown, guard | summoner | C | 8 | Calls the whole crew (real guards) with a raised wooden sword and orders them to surround the target | spiky white hair, leather jacket; white/purple | Dominion · mud_town |
| 88 | Koshiro Nabeyama | M | battle_chef | striker | twin_blades: twin cleavers | fire | glutton | master_chef, chef | rushdown | R | 1 | Knife flurry; a wok-toss fire cone | tall chef's hat, white coat, bandana; white/red | Guild · mud_town |
| 89 | Hotaru Nabeshima | F | battle_chef | vanguard | hammer: frying pan | fire | sweet_natured | master_chef, chef | counter | C | 2 | Frying-pan wall: parries, then a lunch-box toss heals allies | ponytail, patched apron, oven mitt; yellow/white | Hearth · mud_town |
| 90 | Ryuzo Kosho | M | battle_chef | controller | katana: slicing knife | venom | perfectionist | quartermaster, hauler | trapper | R | 2 | Spice-dusted cuts weaken and slow; a judging glare marks the sloppy | glasses, black chef coat, knife roll; black/green | Academy · mud_town |
| 91 | Raijiro Ikazuchi | M | storm_drummer | controller | instrument: ring of drums | shock | hot_blooded | war_drummer, guard | zoner | E | 3 | Drum-beat waves: expanding shock rings to jump over | ring of drums on the back, lightning tattoo; yellow/black | Vanguard · stone_hold |
| 92 | Tsuzumi Otowa | F | storm_drummer | support | instrument: festival drum and sticks | fire | big_sibling | war_drummer, guard | all_rounder | R | 3 | Rhythm: allies attack faster on her beat; the raised stick is the tell | festival headband, short coat; blue/white | Household · stone_hold |
| 93 | Futa Kazabukuro | M | storm_drummer | controller | fan: wind sack and fan | physical | trickster | pathfinder, scout | zoner | C | 4 | Wind-sack gusts push in cones; a vacuum pulls foes back | cloud scarf, wind sack over the shoulder; green/white | Revel · stone_hold |

### 5.3 Wuxia and East Asian myth (14 heroes: 7 F, 7 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 94 | Lin Mingxuan | M | sword_cultivator | marksman | focus: flying swords | arcane | proud_noble | sage_of_letters, enchanter | zoner | E | 5 | Sword array: orbiting swords launch one after another | white-blue robe, hair crown; white/azure | Court · ivory_palace |
| 95 | Bai Yunxi | F | sword_cultivator | support | staff: jade whisk | radiant | ancient_sage | revival_rite, guard | all_rounder | L | 4 | Qi mending and a lotus barrier; a breakthrough glow in phase 3 | white hair bun with jade pin, white robes; white/jade | Conclave · ivory_palace |
| 96 | Qiao Yanling | F | sword_cultivator | striker | rapier: straight sword | shock | cool_headed | mentor, guard | counter | E | 5 | Lightning sword-heart: each parried hit charges a thunder thrust | high ponytail with a silver crescent pin, violet robes with silver cloud trim; violet/silver | Bureau · ivory_palace |
| 97 | Zhou Wenshan | M | sword_cultivator | controller | polearm: formation banner spear | arcane | schemer | tactician, guard | trapper | C | 6 | Plants four banners; the square between them becomes a slowing formation | scholar's hat, scroll case; grey/indigo | Circle · ivory_palace |
| 98 | Hua Lianmei | F | martial_artist | striker | fists: silk-wrapped fists | physical | hot_blooded | drill, guard | counter | R | 6 | Eight-trigram palms: counter stance into palm-push strings | single high bun tied with red cord, sleeveless plum training vest with frog buttons, wide white trousers; plum/white | Vanguard · mud_town |
| 99 | Mei Xiaodie | F | martial_artist | skirmisher | twin_blades: butterfly swords | frost | trickster | pathfinder, scout | rushdown | C | 7 | Butterfly-sword flurry and a wall-run kick | short bob, green training clothes; green/white | Revel · mud_town |
| 100 | Shi Gangtie | M | martial_artist | support | fists: qi palms | arcane | ancient_sage | mentor, guard | counter | R | 7 | Qi palms: each strike heals the ally behind its target; iron-shirt stance while he stands still | shaved sides, topknot, bandaged chest; bronze/brown | Conclave · mud_town |
| 101 | Liu Zuixian | M | martial_artist | striker | staff: loose-limbed staff | venom | glutton | festival_host, guard | all_rounder | C | 8 | Off-rhythm staff string; a gourd-breath cone | red cheeks, gourd at the hip, loose robe; brown/orange | Guild · mud_town |
| 102 | Lu Ningshuang | F | jiangshi | striker | fists: long-nailed hands | blight | sleepy | aura (hardy), hauler | rushdown | R | 8 | Hops in chains; striking the talisman on her forehead stuns her | official's hat with talisman, long sleeves; dark blue/red | Lodge · dark_manor |
| 103 | Yin Suling | F | jiangshi | summoner | focus: bell and talismans | blight | melancholic | revival_rite, guard | summoner | C | 1 | Her bell guides two hopping servants | white mourning robes, pale, bell; white/grey | Vigil · dark_manor |
| 104 | Gao Qinglong | M | jiangshi | vanguard | sword_shield: coffin-lid shield and sabre | blight | gruff_veteran | warden_of_walls, guard | counter | E | 1 | Coffin wall; each blocked hit drains life back | general's armour, talisman, grey skin; grey/teal | Hold · dark_manor |
| 105 | Feng Zhaoming | M | phoenix | skirmisher | fan: ember-feather fan | fire | chuunibyou | ember_keeper, smelter | rushdown | L | 2 | Rebirth once per fight as a final phase; flaming wing dives | flame-red hair, feather cape that becomes wings; red/gold | Legion · desert_court |
| 106 | Huang Jinlu | F | phoenix | support | fan: ember fan | radiant | sweet_natured | revival_rite, guard | all_rounder | E | 2 | Rebirth ward: an ally's next lethal hit is stopped once | gold feather hair ornaments, white-gold dress; gold/white | Hearth · desert_court |
| 107 | Zhu Yanchi | M | phoenix | marksman | bow: firebird bow | fire | chuunibyou | lookout, guard | zoner | C | 3 | Sun-arrow rain leaves burning ground | gold crown feathers, crimson coat; crimson/gold | Legion · desert_court |

### 5.4 Western fantasy (65 heroes: 33 F, 32 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 108 | Ilsabet Frostmere **[S]** | F | knight | vanguard | sword_shield: longsword and kite shield | frost | righteous | rally, guard | counter | E | S | Shield wall and bash; visor down and a charge in phase 2; a frozen standard in phase 3 | steel plate, braided crown of ash-blonde hair, blue-white tabard; steel/frost blue | Order · stone_hold |
| 109 | Odile Varenne | F | knight | vanguard | greatblade: claymore | fire | perfectionist | forgemaster, blacksmith | all_rounder | C | 3 | Oath of the blade: blocked hits stack resolve; at five, a crescent cleave wave | silver plate, scarlet cape, short black hair; silver/scarlet | Academy · stone_hold |
| 110 | Tamsin Rook | F | knight | vanguard | polearm: banner lance | radiant | big_sibling | rally, guard | counter | R | 4 | War banner: the planted banner shelters allies and calls guards; a lance wall | red ponytail, banner, light armour; red/white | Household · stone_hold |
| 111 | Barnaby Thistledown | M | knight | vanguard | hammer: warhammer and tower shield | physical | airhead | aura (mason), builder | counter | C | 4 | Tower-shield advance; his bell helmet rings and stuns whoever strikes it | visored great helm with plume, round build; brass/green | Parade · stone_hold |
| 112 | Corvin Ashlocke | M | knight | striker | greatblade: black two-handed sword | blight | melancholic | mentor, guard | stance | E | 5 | Cursed cleaves drain; a fallen-knight stance in phase 2 | black armour, torn cape, white hair; black/grey | Vigil · stone_hold |
| 113 | Celestine Brightwater | F | paladin | support | hammer: radiant warhammer | radiant | sweet_natured | mend, guard | all_rounder | E | 5 | Consecrated ground and healing touch; wings of light in phase 2 | white-gold armour, halo-like crest, long golden hair; white/gold | Hearth · ivory_palace |
| 114 | Aldric Dawnmere | M | paladin | vanguard | sword_shield: sun sword and shield | radiant | righteous | warden_of_walls, guard | counter | R | 6 | Radiant pillars ring the arena; a dawn shield | gold armour, white cloak, short blond hair; gold/white | Order · ivory_palace |
| 115 | Lucan Pyreholt | M | paladin | striker | greatblade: holy greatsword | fire | tyrant | intimidation, guard | rushdown | C | 7 | Judgement brands the guilty, then detonates the brands | red-white surcoat, bucket helm; red/white | Dominion · ivory_palace |
| 116 | Ottilie Lanthorn | F | paladin | summoner | greatblade: lantern greatsword | radiant | gentle_giant | rally, guard | summoner | R | 6 | Two spectral squires guard her; the lantern on her blade calls them back | tall, white veil helm, lantern; white/silver | Haven · ivory_palace |
| 117 | Agathe Morrow | F | battle_priest | striker | hammer: cross-shaped mace | radiant | shy | purifier, guard | stance | C | 7 | Heals with her tome in phase 1; snaps it shut and takes up the mace in phase 2 | habit and veil, tome on a chain; black/white | Commune · ivory_palace |
| 118 | Matthias Grell | M | battle_priest | support | crossbow: holy crossbow | radiant | gruff_veteran | triage, guard | zoner | R | 8 | Holy bolts heal allies they pass and burn foes | cassock, bandolier, stubble; black/purple | Hold · ivory_palace |
| 119 | Ilse Rabenmark | F | battle_priest | support | rapier: lancet sword | venom | cool_headed | triage, guard | all_rounder | C | 8 | Lancet thrusts inject antidote into allies and poison into foes | beaked mask, long coat; black/teal | Bureau · ivory_palace |
| 120 | Ansel Kade | M | battle_priest | striker | twin_blades: twin blessed daggers | radiant | hot_blooded | purifier, guard | rushdown | R | 1 | Exorcism strings with twin daggers; a rosary chain pull | short hair, cassock with rolled sleeves, rosary; black/gold | Vanguard · ivory_palace |
| 121 | Ambrose Nettlewick **[S]** | M | witch | controller | staff: gnarled cauldron staff | venom | eccentric_genius | sage_of_letters, enchanter | trapper | E | S | Potion barrage (the colour is the tell); cauldron zones; broom hops in phase 2 | tilted wide-brimmed hat, purple coat with vials, round glasses, crow on the shoulder; purple/green | Academy · dark_manor |
| 122 | Pippa Hexley | F | witch | marksman | focus: star-tipped wand | arcane | chuunibyou | sage_of_letters, enchanter | zoner | R | 1 | Hex bubbles trap and pop; a mirror hex repeats her last cast | short purple twin tails, small witch hat, cape; violet/pink | Legion · dark_manor |
| 123 | Hazel Brambleworth | F | witch | support | scythe: herb sickle | venom | big_sibling | aura (green_thumb), farmer | all_rounder | C | 2 | Herbal brews heal allies and poison foes; her sickle reaps the marks | freckles, straw witch hat with flowers, apron; green/brown | Household · dark_manor |
| 124 | Corvina Blackfen | F | witch | summoner | twin_blades: ritual knives | blight | trickster | lookout, guard | summoner | R | 3 | A crow swarm pecks and blinds; ritual knives mark its next target | black hair, crow-feather cloak; black/teal | Revel · dark_manor |
| 125 | Cassius Vane | M | witch | controller | focus: grimoire | blight | schemer | sage_of_letters, enchanter | trapper | E | 2 | Rune circles: delayed glyph traps | cropped ash hair, high-collared black robes, grimoire; black/silver | Circle · dark_manor |
| 126 | Aelwyn Sylvaris | F | elf | marksman | bow: longbow | physical | cool_headed | forester, woodcutter | zoner | R | 4 | Hunter's mark, then a piercing shot | long pointed ears, green hooded cloak, blonde braid; green/silver | Bureau · grove_lodge |
| 127 | Nyxara Duskveil | F | elf | skirmisher | twin_blades: curved runeblades | blight | tsundere | spymaster, scout | stance | E | 5 | Spellblade state: blight blades; a shadow step | dusky skin, white hair, violet runes; grey/violet | League · grove_lodge |
| 128 | Lirielle Fernsong | F | elf | support | bow: blossom bow | venom | sweet_natured | aura (regrowth), woodcutter | zoner | R | 6 | Healing arrows for allies, thorn arrows for foes | pink-blonde hair, flower circlet; white/pink | Hearth · grove_lodge |
| 129 | Faelan Oakshade | M | elf | controller | staff: druid staff | venom | ancient_sage | forester, woodcutter | trapper | C | 3 | Entangling roots and a thorn wall | antler circlet, long brown hair, leaf cloak; brown/green | Conclave · grove_lodge |
| 130 | Caedric Wintersong | M | elf | skirmisher | bow: short bow and knife | frost | lone_wolf | cartographer, scout | rushdown | R | 4 | Rime arrows stack freeze; vaulting backflips | silver hair, blue cloak; blue/silver | Pack · grove_lodge |
| 131 | Ottavia Sanguine | F | vampire | skirmisher | rapier: blood rapier | blight | yandere | envoy, guard | counter | E | 7 | Draining cuts and a bat-swarm dash | silver ringlets, crimson coat, red eyes; crimson/black | Covenant · dark_manor |
| 132 | Lazare Vespertine | M | vampire | controller | scythe: blood scythe | blight | charmer | merchant_prince, trader | trapper | R | 5 | Blood-moon zone drains; charmed foes falter | slicked black hair, high-collared cape; black/wine | Salon · dark_manor |
| 133 | Bettina Nachtigall | F | vampire | summoner | fan: bat-wing fan | blight | sleepy | aura (thrifty), smelter | summoner | C | 8 | Sleepy by day, bold at night; bat swarms blind | short black hair, bat hair clips, cardigan; black/purple | Lodge · dark_manor |
| 134 | Radomir Grauwald | M | vampire | marksman | bow: blood bow | blight | gruff_veteran | intimidation, guard | zoner | R | 6 | Blood arrows fill a pool he drinks from to heal; a blood-armour shell | white hair, scarred, crimson plate; crimson/white | Hold · dark_manor |
| 135 | Belphira Ashenthrone | F | demon_lord | controller | polearm: three-pronged sceptre | fire | tyrant | tribute_master, trader | zoner | L | 1 | Hellfire sigils rain; imps answer her call | curled ram horns, black-red regal coat, spade tail; black/crimson | Dominion · ember_fortress |
| 136 | Pim Brimsby | F | demon_lord | skirmisher | twin_blades: twin tail-daggers | arcane | trickster | renown, guard | rushdown | R | 2 | Prank hops and dagger pokes; a tail grab | teal hair, tiny horns, tail; teal/black | Revel · ember_fortress |
| 137 | Malrecht Vorthane | M | demon_lord | skirmisher | scythe: demon scythe | fire | tyrant | intimidation, guard | rushdown | L | 7 | Throne-step dashes; scythe arcs leave fire trails | tall, long black horns, black armour, cloak; black/purple | Dominion · ember_fortress |
| 138 | Mephis Gallow | M | demon_lord | summoner | katana: contract blade | arcane | merchant | tribute_master, trader | summoner | R | 8 | Contract seals bind imps to fight for him; breaking a seal staggers him | noble coat, swept horns; red/black | Company · ember_fortress |
| 139 | Amariel Lumen | F | angel | support | staff: halo staff | radiant | airhead | mend, guard | all_rounder | L | 3 | Halo barrier on allies; a feather rain heals | white wings, halo, long blonde hair; white/gold | Parade · ivory_palace |
| 140 | Ishara Morn | F | angel | striker | katana: black blade | blight | melancholic | lookout, guard | rushdown | R | 4 | Fallen dive and a black-feather volley | black wings, broken halo, white hair; black/silver | Vigil · ivory_palace |
| 141 | Ariston Highgate | M | angel | vanguard | sword_shield: flaming sword and round aegis | fire | righteous | warden_of_walls, guard | counter | L | 1 | Gate guardian: flaming sweeps; the aegis turns bolts aside | two pairs of small wings, gold armour; gold/red | Order · ivory_palace |
| 142 | Halvard Skye | M | angel | marksman | bow: light bow | radiant | chuunibyou | lookout, guard | zoner | R | 2 | Judgement arrows fall from above | white hair, two small wings, visor; white/blue | Legion · ivory_palace |
| 143 | Mortlake Vey | M | reaper | striker | scythe: great scythe | blight | melancholic | revival_rite, guard | counter | E | 3 | Soul lantern marks; the scythe sweep reaps marked foes | hooded black cloak, lantern, pale; black/teal | Vigil · dark_manor |
| 144 | Elowen Hollowmere | F | reaper | controller | scythe: lantern scythe | arcane | shy | purifier, guard | trapper | R | 5 | The soul lantern on her scythe slows and tethers | short navy hair, oversized hood; navy/pale blue | Commune · dark_manor |
| 145 | Silas Underhill | M | reaper | support | axe: grave spade | frost | airhead | architect, builder | all_rounder | C | 4 | Grave mounds rise to shield allies; shovelled frost waves slow foes | top hat, long coat, spade; grey/brown | Parade · dark_manor |
| 146 | Morden Kesh | M | necromancer | summoner | focus: bone tome | blight | eccentric_genius | sage_of_letters, enchanter | summoner | E | 5 | Raises two skeleton servants; bone spears | hood, ribcage armour, green fire; black/green | Academy · deepslate_keep |
| 147 | Hespera Gravesend | F | necromancer | summoner | axe: bone cleaver | venom | yandere | quartermaster, hauler | summoner | C | 6 | Zombie servants and rot zones | black twin braids, skull hairpin; black/sickly green | Covenant · deepslate_keep |
| 148 | Osric Mallow | M | necromancer | controller | instrument: bone flute | arcane | sleepy | alchemist, enchanter | zoner | R | 6 | Bone-flute march slows the living | thin, patched coat, bone flute; grey/green | Lodge · deepslate_keep |
| 149 | Wren Halloway | F | beast_tamer | summoner | rapier: whip | physical | big_sibling | beastmaster, animal_carer | summoner | C | 7 | Commands two wolves; a whip crack taunts | fur hat, whip, ranger coat; tan/red | Household · grove_lodge |
| 150 | Merith Talon | F | beast_tamer | summoner | bow: falconer bow | shock | lone_wolf | cartographer, scout | summoner | R | 8 | Her hawk dives at marked foes | leather glove, feathered hood; brown/sky blue | Pack · grove_lodge |
| 151 | Bjarke Honeyford | M | beast_tamer | summoner | axe: woodsman's axe | physical | gentle_giant | beastmaster, animal_carer | summoner | C | 7 | A bear companion (iron golem drawn as a bear) | bear-pelt cloak, braided beard; brown/honey | Haven · grove_lodge |
| 152 | Torin Highspear | M | dragoon | skirmisher | polearm: dragoon lance | shock | stoic | lookout, guard | rushdown | E | 8 | Jump: leaves the ground high and lands with a shockwave on a marker | dragon helm, scale armour; red/black | Watch · stone_hold |
| 153 | Kestrel Ardent | F | dragoon | striker | polearm: wyvern spear | frost | perfectionist | drill, guard | all_rounder | C | 1 | Diving-thrust chain; each dive goes higher | silver ponytail, wyvern helm; silver/teal | Academy · stone_hold |
| 154 | Roderic Lancefield | M | dragoon | vanguard | polearm: lance and pavise | physical | gruff_veteran | warden_of_walls, guard | counter | R | 1 | Lance charge across the arena; stops hard against walls | plumed helm, heavy lance; steel/green | Hold · stone_hold |
| 155 | Hrafn Bloodmane | M | berserker | striker | axe: twin axes | fire | battle_maniac | forester, woodcutter | rushdown | C | 2 | Rage: damage taken becomes attack; never retreats | wild red beard, fur cloak, war paint; red/brown | Warband · grove_lodge |
| 156 | Runa Ironvow | F | berserker | vanguard | sword_shield: axe and round shield | frost | coward_who_rises | forester, woodcutter | counter | R | 2 | Shield-maiden: trembles at first, then her war cry turns every block into a counter | blonde braids, bear pelt, round shield; brown/blue | Fellowship · grove_lodge |
| 157 | Ulfgar Stonejaw | M | berserker | skirmisher | axe: throwing hatchets | shock | delinquent | surveyor, miner | rushdown | C | 3 | Hatchets that return like boomerangs; a thunder leap to close in | shaggy hair, stone-studded armour; grey/brown | Crew · grove_lodge |
| 158 | Clementine Vask | F | arbalist | marksman | crossbow: repeating crossbow | physical | perfectionist | aura (marksman), guard | zoner | R | 3 | Bolt barrage, then a piercing bolt | goggles, long coat, ponytail; brown/brass | Academy · stone_hold |
| 159 | Odessa Quarrel | F | arbalist | marksman | crossbow: heavy arbalest and pavise | venom | stoic | warden_of_walls, guard | zoner | C | 4 | Poison bolts from behind a planted pavise | heavy coat, helm, pavise; olive/steel | Watch · stone_hold |
| 160 | Fennick Brass | M | arbalist | controller | crossbow: net launcher | shock | eccentric_genius | engineer, builder | trapper | R | 4 | Net bolts tether; shock nets | goggles, brass arm brace; brass/blue | Academy · stone_hold |
| 161 | Ezra Blackwake | M | corsair | support | rapier: cutlass | shock | merchant | merchant_prince, trader | all_rounder | C | 5 | Storm captain: each cutlass hit sings a shanty buff; he calls guards as his crew | tricorne, long coat, eye scar; navy/gold | Company · coral_port |
| 162 | Nerys Saltmarsh | F | corsair | marksman | crossbow: harpoon launcher | frost | lone_wolf | angler_lord, fisherman | zoner | R | 5 | Harpoon tether pulls foes in | harpoon, sea coat, braided hair; teal/sand | Pack · coral_port |
| 163 | Jory Flint | M | corsair | controller | crossbow: blunderbuss | fire | trickster | tribute_master, trader | trapper | C | 6 | Powder shots leave fuse kegs that burst (no block damage) | bandana, bomb belt; red/brown | Revel · coral_port |
| 164 | Florian Ashvale | M | bard | support | instrument: lute | arcane | charmer | envoy, guard | all_rounder | R | 7 | Ballads buff allies; a discord chord weakens foes | feathered cap, red doublet; red/cream | Salon · cherry_court |
| 165 | Linnea Harrowgate | F | bard | marksman | bow: harp bow | frost | shy | festival_host, guard | zoner | C | 6 | Harp-bow notes arc like arrows; a lullaby cone puts foes to sleep (stun) | long wavy hair, harp; ice blue/silver | Commune · cherry_court |
| 166 | Pell Wickham | M | bard | skirmisher | twin_blades: flute and stiletto | venom | trickster | spymaster, scout | rushdown | R | 8 | Flute trills mark foes; stiletto lunges at the marks | patchwork coat, flute; brown/green | Revel · cherry_court |
| 167 | Ottoline Brassbury | F | alchemist | controller | focus: flask grenades | venom | eccentric_genius | alchemist, enchanter | trapper | C | 7 | Acid flasks burn over time; foam flasks slow | goggles, lab coat, braid; white/green | Academy · tuff_workshop |
| 168 | Dorothea Mixwell | F | alchemist | support | crossbow: syringe crossbow | radiant | shy | triage, guard | zoner | R | 8 | Medicine bolts heal allies at range | white coat, satchel; white/pink | Commune · tuff_workshop |
| 169 | Ignaz Coggleby | M | alchemist | summoner | hammer: wrench hammer | shock | eccentric_genius | engineer, builder | summoner | C | 1 | A clockwork golem (iron golem drawn as clockwork) | goggles on the head, leather apron; copper/brown | Academy · tuff_workshop |
| 170 | Maris Coralline | F | siren | controller | instrument: conch | frost | charmer | angler_lord, fisherman | zoner | R | 1 | Siren song pulls foes in; tidal waves | teal hair, fin ears, scale skirt; teal/pearl | Salon · coral_port |
| 171 | Ondine Shellbright | F | siren | summoner | instrument: whale-song shell | radiant | sweet_natured | mend, guard | summoner | C | 2 | Dolphin spirits leap from her song to shield allies | pearl hair beads, coral staff; coral/white | Hearth · coral_port |
| 172 | Thalassor Wake | M | siren | marksman | polearm: trident | frost | merchant | angler_lord, fisherman | zoner | E | 2 | Throws tridents that drag foes in a whirlpool; a tidal charge | fin ears, scale armour, trident; deep blue/silver | Company · coral_port |

### 5.5 Modern, pop and science fiction (28 heroes: 14 F, 14 M)

| # | Working name | G | Archetype | Role | Weapon (style: prop) | Element | Temperament | Specialty (talent, duty) | Duel | R | B | Signature | Look (silhouette; palette) | Leader seed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 173 | Hoshimi Utano | F | idol | support | instrument: star light-stick | radiant | genki | festival_host, guard | all_rounder | C | 3 | Encore: buffs grow with her combo; a spotlight zone | pink twin tails, star hair clips, stage dress; pink/white | Troupe · cherry_court |
| 174 | Kyoka Sonare | F | idol | controller | instrument: microphone stand | shock | cool_headed | renown, guard | zoner | R | 4 | Feedback waves from the microphone stand | black bob, rock-stage jacket; black/electric blue | Bureau · cherry_court |
| 175 | Haru Kanade | M | idol | skirmisher | fists: dance kicks | blight | charmer | renown, guard | rushdown | C | 3 | Shadow-dance strings of spin kicks; a pose counter | styled hair, tailed stage jacket; white/purple | Salon · cherry_court |
| 176 | Lucas Miyabe | M | idol | marksman | instrument: light-up keytar | frost | tsundere | festival_host, guard | zoner | R | 4 | Cold-light beams sweep the stage and leave frosted ground | slick silver hair, sequined coat; silver/ice blue | League · cherry_court |
| 177 | Momoka Akebono | F | magical | marksman | staff: heart wand | radiant | genki | mentor, guard | stance | C | 5 | Transformation at 60%: a new outfit and a ribbon bind | pink-red curls, frilly outfit; red/white | Troupe · ivory_palace |
| 178 | Sorane Hoshizaki | F | magical | vanguard | sword_shield: heart shield and ribbon blade | arcane | tsundere | envoy, guard | stance | R | 6 | Heart-shield bash; ribbons bind whoever strikes her shield | twin tails, long ribbon; blue/yellow | League · ivory_palace |
| 179 | Sho Kagayaki | M | magical | vanguard | sword_shield: transforming armour and sword | shock | righteous | rally, guard | stance | E | 5 | Armour forms at 60%; it grows stronger with each hit he blocks | spiky hair, visor, armour; white/red | Order · ivory_palace |
| 180 | Akito Hoshinaga | M | magical | summoner | staff: star sceptre | arcane | airhead | lookout, guard | summoner | R | 6 | Star familiars orbit him and fire on his marks | soft hair, star cape; navy/gold | Parade · ivory_palace |
| 181 | Edith Lindell | F | maid_butler | skirmisher | staff: reinforced broom | physical | perfectionist | quartermaster, hauler | rushdown | C | 7 | Sweeping strings and a dust cloud that blinds | classic long maid dress, headdress, glasses; black/navy | Academy · ivory_palace |
| 182 | Marianne Tilly | F | maid_butler | summoner | katana: blade hidden in a broom handle | radiant | sweet_natured | master_chef, chef | summoner | R | 8 | Head maid: rings for the house staff (guards) and two teapot helpers | short maid dress, apron, teapot; pink/white | Hearth · ivory_palace |
| 183 | Alistair Penwright | M | maid_butler | vanguard | fists: white gloves | physical | stoic | quartermaster, hauler | counter | C | 7 | Guards his leader: parries and counters, never leaves his side | tailcoat, white gloves, monocle; black/white | Watch · ivory_palace |
| 184 | Reginald Ashby | M | maid_butler | marksman | crossbow: cane crossbow | frost | tsundere | tactician, guard | zoner | R | 8 | Precise cane-crossbow shots; a polite bow before each | tailcoat, cane; grey/burgundy | League · ivory_palace |
| 185 | Lavinia de Montclair | F | noble_fencer | skirmisher | rapier: rapier | arcane | proud_noble | steward, guard | counter | C | 1 | Riposte and a lunge chain; her ringlets bounce on each lunge | ringlet curls, ruffled coat; lilac/white | Court · ivory_palace |
| 186 | Armand Valcourt | M | noble_fencer | striker | rapier: rapier and parrying dagger | physical | tsundere | aura (duelist), guard | counter | R | 1 | Duel challenge: marks one foe and fights it better | blond hair, blue doublet; blue/gold | League · ivory_palace |
| 187 | Percival Kingsley | M | noble_fencer | controller | rapier: cane sword | frost | coward_who_rises | steward, guard | counter | C | 2 | Cane-sword trips stun; a monocle mark | monocle, top hat, cane; grey/plum | Fellowship · ivory_palace |
| 188 | Selene-7 | F | android | vanguard | greatblade: arm blade | shock | stoic | aura (diligence), builder | counter | R | 2 | Shield matrix breaks after enough hits; the visor slides down | white synthetic skin seams, long straight cyan-tinted hair, a halo-ring antenna; white/cyan | Watch · tuff_workshop |
| 189 | Vesta Mk II | F | android | marksman | crossbow: shoulder lens cannon | shock | coward_who_rises | engineer, builder | zoner | C | 3 | Shoulder-lens beam with a long charge tell; her fear protocol overheats, then she steadies | metal panels, cable ponytail, a round lens over one eye; grey/lilac | Fellowship · tuff_workshop |
| 190 | Orrin-4 | M | android | support | focus: repair drones | arcane | eccentric_genius | triage, guard | all_rounder | R | 3 | Repair drones heal allies | boxy frame, antenna; teal/white | Academy · tuff_workshop |
| 191 | Tetsu-9 | M | android | marksman | fists: rocket fists | fire | battle_maniac | surveyor, miner | zoner | C | 4 | Rocket-punch shots and a rush | heavy frame, exhaust ports; red/steel | Warband · tuff_workshop |
| 192 | Sophia Glasswick | F | tactician | controller | focus: chess pieces | arcane | schemer | tactician, guard | trapper | R | 4 | Board control: marked squares become zones | glasses, long coat, chess-piece pin; black/white | Circle · tuff_workshop |
| 193 | Kasimir Vell | M | tactician | support | polearm: signal-flag spear | arcane | cool_headed | steward, guard | all_rounder | C | 5 | Orders: allies gain buffs; guards form up | military coat, flags; navy/red | Bureau · tuff_workshop |
| 194 | Ione Marchetti | F | tactician | marksman | crossbow: pocket crossbow | venom | merchant | spymaster, scout | zoner | R | 5 | Predicts a foe's dodge and fires where it will be | glasses, scarf, notebook; green/grey | Company · tuff_workshop |
| 195 | Dunstan Hearthe | M | smith | marksman | crossbow: rivet launcher | fire | gruff_veteran | forgemaster, blacksmith | zoner | C | 6 | Rivet bolts heat-stack; an anvil-drop finisher | leather apron, burn scars; brown/orange | Hold · tuff_workshop |
| 196 | Hilde Coalbright | F | smith | striker | hammer: tongs and hammer | fire | hot_blooded | forgemaster, blacksmith | rushdown | R | 6 | Heat stacks make her hammer glow; a quench burst | short hair, soot, apron; black/orange | Vanguard · tuff_workshop |
| 197 | Ewald Runeforge | M | smith | support | axe: rune axe | arcane | perfectionist | forgemaster, blacksmith | all_rounder | C | 7 | Rune armour on allies (shields) | braided beard, runic apron; grey/blue | Academy · tuff_workshop |
| 198 | Paloma Brava | F | grappler | vanguard | fists: wrestling gloves | physical | big_sibling | drill, guard | grappler | R | 7 | Grab-throws, a slam, and a flexing taunt | wrestling mask, cape; red/gold | Household · mud_town |
| 199 | Nagisa Kurogane | F | grappler | striker | fists: grip gloves | shock | delinquent | mentor, guard | grappler | C | 8 | Counter throws and grabs | training uniform, black belt; white/black | Crew · mud_town |
| 200 | Daisuke Onoyama | M | grappler | vanguard | fists: ring palms | radiant | gentle_giant | drill, guard | grappler | R | 8 | Slap barrage, belly push; a salt toss cleanses | topknot, wrestling belt; white/navy | Haven · mud_town |

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

