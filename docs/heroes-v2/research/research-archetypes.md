# Hero archetype research for Faction Clash heroes v2

Research input for designing about 200 **original** anime-styled heroes who lead factions and fight like bosses.

**Scope and the originality rule.** This file catalogues *archetypes* (folklore creature types, occupations, genre
roles, personality tropes). It names no existing character, franchise, game or anime, and a hero designed from it must
not imitate one. Folklore creature types (oni, tengu, kitsune, kappa, jiangshi...) are generic public categories. What
makes a hero original is the **combination**: archetype + personality + element + weapon + signature mechanic +
original name, palette and backstory. Nothing here should be used as "a hero who is like X from Y".

**How the research was done.** Web searches on trope and archetype references (trope wikis, folklore
explainers, game-design articles; see Part 9). Direct page fetches from en.wikipedia.org and tvtropes.org were
blocked by this session's network allowlist, so those pages were read through search-result summaries only. Folklore
details vary between sources; where they conflict this file uses the most common version.

**Vocabulary.** Kit patterns are written in the v1 hero-format vocabulary (`docs/heroes/HERO_FORMAT.md`), so they
can be turned into kit files without inventing names:
- forms `melee` `shot` `volley` `bolt` `chain` `cone` `line` `nova` `zone` `leap` `dash` `self` `ally` `allies`;
- effects `damage` (with `drain`) `dot` `ignite` `freeze` `heal` `shield` `cleanse` `effect` `stat` `knockback`
  `pull` `stun` `taunt` `dispel` `move_self` `energy` `cooldown_cut` `mark`/`clear_marks` `empower` `summon`;
- passive triggers `on_hit` `on_crit` `on_hurt` `on_kill` `on_skill` `on_low_health` `on_ally_low_health`
  `on_ally_attack` `on_mark_full` `every` ...; conditions `target_health_below`, `target_has_mark`, `self_empowered`...;
- elements `physical` `fire` `frost` `shock` `venom` `blight` `arcane` `radiant`;
- vanilla weapons sword, axe, mace, bow, crossbow, trident, shield; projectile looks (`feather`, `amethyst_shard`,
  `wind_charge`, `blaze_powder`, `snowball`, `fire_charge`, `flint`, `bone`, `echo_shard`...); particles and sounds
  from Appendix B of the v1 format (`cherry_leaves`, `soul_fire_flame`, `gust`, `note`, `witch`, `block.bell.use`,
  `entity.wolf.howl`, `item.mace.smash_ground`...).

Anything v1 cannot express is tagged **[P:name]** and collected in Part 6 as proposals for the v2 format.

**Visual weapon vs mechanical weapon.** With animated models the prop the hero holds can be drawn as anything
(katana, naginata, fan, scythe, staff, lute, drum, umbrella, cleaver) while the mechanics use the nearest vanilla
item, so damage, enchantments, shields and armour keep vanilla rules. Entries write this as "katana (sword)",
"naginata (trident)", "scythe (axe)", "mallet (mace)", "talisman cards (thrown `feather` look)".

**Player-like rule kept.** No teleporting: "blinks" become `dash`, `leap`, `move_self` or a few seconds of
self-`invisibility`; decoys and clones are visual only [P:decoy]; vanilla potion effects stay inside the v1 caps.

---

## Part 1. Key findings

1. **Appearance is read silhouette first, then colour blocks, then detail.** Shape language: round reads friendly,
   square reads sturdy, triangles and spikes read dangerous. Two or three main colours plus one signature accent per
   hero; the design must still read in greyscale. For 200 heroes on a block-game scale this means the *head
   silhouette* (ears, horns, hats, hair shape, halos, tails, wings) carries identity more than costume detail.
2. **Animal-ear (kemonomimi) and yokai-derived characters dominate anime fantasy casts.** Cat ears are the most
   common, then fox, wolf/dog, rabbit; folklore yokai (oni, tengu, kitsune, kappa, yuki-onna, tanuki, jorōgumo)
   provide horns, wings, tails and a ready-made signature mechanic (foxfire, gale fan, iron club, frost breath).
3. **Occupation archetypes come with props that are also weapons:** shrine maiden (gohei wand, bells, paper
   talismans for sealing and exorcism), onmyōji (paper familiars, five-element star seal), ninja (smoke, short blade,
   thrown stars), samurai and iai wanderer (the single quick-draw cut), maid/butler (a servant secretly a bodyguard),
   idol and bard (songs that buff), magical girl (transformation, wand, mascot, ribbons).
4. **Role vocabulary from RPGs and gacha games** splits fighters into tank, main damage, sub-damage (damage over time,
   off-field pressure), healer, shielder, buffer, debuffer and crowd control (controller), plus summoners. The v1
   presets cover tank (`vanguard`), damage (`striker`, `skirmisher`, `marksman`) and `support`; research suggests
   adding `controller` and `summoner` presets [P:roles].
5. **Fighting-game styles give each boss a "feel" independent of its team role:** rushdown (pressure up close),
   zoner (keeps you out with projectiles), grappler (throws, pulls, punishes standing next to it), footsies/counter
   (spacing, parries), setplay/trapper (lays zones and traps), all-rounder (a projectile plus an anti-air), stance
   switcher (changes moveset by stance or phase), puppet/summoner (fights through minions).
6. **Boss-fight craft:** every attack is anticipation -> attack -> recovery; the anticipation (the tell) must be
   readable by animation, sound and ground markers, and its length grows with the danger (light hits about
   0.4-0.6 s, heavy ones longer); recovery is the punish window. Phases should add a *new mechanic*, not just more
   numbers; two or three phases suit most bosses. Fake-outs are fair only when the tell itself is clear.
7. **Personality tropes are a small shared vocabulary** (tsundere, kuudere, dandere, deredere, yandere, genki, stoic,
   proud noble, delinquent, chuunibyou, airhead, big sister/brother, trickster...). Each one maps naturally onto AI
   aggression, retreat thresholds and dialogue tone, and onto how a faction leader decides (Part 5).

---

## Part 2. Two axes: team role and duel style

A hero needs a **team role** (how it helps its faction in a war) and a **duel style** (how it feels to fight alone
against a player). Combining them keeps 200 heroes distinct.

| Team role | Job in a battle | v1 preset | Typical archetypes |
|---|---|---|---|
| Vanguard (tank) | holds the front, taunts, shields allies | `vanguard` | knight, komainu, oni, sōhei, golem-kin, battle butler |
| Striker (main damage, melee) | kills priority targets | `striker` | samurai, berserker, wolf-kin, yankii, dragonkin |
| Skirmisher (hit and run) | dives, retreats, finishes | `skirmisher` | nekomimi, ninja, moon rabbit, pirate, dragoon |
| Marksman (ranged damage) | damage from range, kites | `marksman` | elf ranger, arbalist, kitsune caster, witch |
| Support (healer, shielder, buffer) | keeps the side alive | `support` | miko, priest/nun, idol, bard, chef, dryad |
| Controller (debuffer, crowd control) | slows, stuns, zones | none [P:roles] | yuki-onna, spider-weaver, onmyōji, fan dancer |
| Summoner | fights through creatures | none [P:roles] | beast tamer, necromancer, onmyōji, wolf-kin |

| Duel style | How it feels | Typical tells | Punish window |
|---|---|---|---|
| Rushdown | it never stops coming; short combos, dashes | crouch or step-in before each dash | after a whiffed combo ender |
| Zoner | projectiles keep you at range; close the gap to win | raised wand/fan, particles gathering | while it re-positions or reloads |
| Grappler | stay out of reach or be pulled, stunned, thrown | arms spread, grab stance | after a missed grab (long recovery) |
| Counter / footsies | patient; punishes button mashing | guard stance glints | wait out the stance, hit after its swing |
| Setplay / trapper | fills the arena with zones and marks | ground markers, threads, circles | between set-ups |
| All-rounder | a projectile, a rush, an anti-air | varied | varied |
| Stance switcher | changes moveset (stance or phase) | visible stance change, colour swap | during the switch animation |
| Puppet / summoner | fight the summons or dive the summoner | summon circle, paper dolls, howl | after the summons die |

---

## Part 3. Boss-fight grammar for every hero

Common rules so every hero fight feels like a boss and stays fair in Minecraft (players have shields, bows, potions,
and the v1 caps already stop stun-locks and one-shots).

**Phases (two or three, by health):**
- *Phase 1 (100-60%)* teaches: the basic attack and two skills, each with a clear tell.
- *Transition at about 60%* (`on_low_health 60`): a 1-1.5 s transformation animation and a line of dialogue
  (horns glow, tails fan out, helmet goes on, wings spread), then `empower` with a new look. No invulnerability: the
  hero only gains knockback resistance during the animation, so players can punish it.
- *Phase 2 (60-25%)* adds **one new mechanic** (summons, an arena-wide zone, a counter stance, a second element).
- *Phase 3 (below 25%, optional, for the strongest or leader heroes)*: desperation; the ultimate comes back faster,
  tells shorten but never below the minimums below.

**Tell minimums (PROPOSED for the v2 animation layer):**

| Attack | Wind-up (anticipation) | Recovery (punish window) | Tell channels |
|---|---|---|---|
| Light melee | 0.4-0.6 s | 0.3 s | animation only |
| Heavy melee, leap, dash | 0.8-1.0 s | 0.8-1.2 s | animation + sound |
| Area (cone, nova, zone) | 1.0 s | 0.8 s | animation + ground marker (`zone` delay ring) + sound |
| Ultimate | 1.5-2.0 s | 1.5-2.0 s | all of the above + a spoken line + a screen-wide sound |

**Readability:** each hero gets one signature colour for its particles and ground markers, one signature sound, and
one signature pose. Phase changes are visible on the model (a colour swap, glowing eyes, more tails, wings out).
**Faces:** eyes narrow during wind-ups; blinking stops during a channel (a readable "it is concentrating" cue); eyes
glow in the final phase; wince on hurt; the reaction to a kill or a defeat follows the personality (smug grin,
apologetic bow, tearful pout). **Boss bar:** when a hero targets a player, show a vanilla boss bar in the hero's
colour to players within 32 blocks [P:bossbar]. **Defeat beats:** heroes kneel and say a line instead of a plain
death, which also fits the v1 revival rule (back after 10 minutes).

---

## Part 4. Archetype catalogue (54 archetypes)

Each entry gives:
- **Gender**: F, M or both, and the counterpart that keeps the 50/50 split possible;
- **Look**: silhouette, hair, ears/horns/tails, outfit, palette, props;
- **Anim**: idle quirk, facial cue, combat tell (for the animated model);
- **Kits**: two or three Minecraft-fitting combat patterns (pick one per hero, or mix);
- **Personality**: tropes that fit (Part 5 defines them);
- **Non-combat**: talents, mapped to the mod's jobs, affixes (`diligence`, `prospector`, `regrowth`, `green_thumb`,
  `mason`, `marksman`, `thrifty`, `hardy`, `duelist`, `angler`, `haggler`) and base abilities (`aura`, `mend`,
  `lookout`, `rally`, `drill`);
- **Boss**: signature pattern, phases, tells, punish window;
- **Leader**: faction naming pattern with original example names, decision biases, base building style (blocks
  for the faction `Style`: wall, frame, roof, banner wool).

### A. Animal-kin and yokai

#### 1. Nekomimi (cat-eared)
- **Gender:** both (most often F in media; cat-eared boys are equally established).
- **Look:** small, agile silhouette; swivelling cat ears, long thin tail; slit pupils, one fang; bell collar or choker,
  short jacket or hoodie with paw motifs, or a short kimono; palettes calico (white/orange/black), tuxedo, grey tabby;
  claw gauntlets.
- **Anim:** ears flick toward sounds, tail S-sway idle, tail puffs when hurt, slow "cat blink" when relaxed,
  hip-wiggle crouch before a pounce.
- **Kits:** (a) *Pounce striker*: claw gauntlets (sword, sweeping edge); `leap` (range 8, radius 1.5) with a physical
  `dot` bleed (2 stacks); passive `on_crit` -> `stat speed +15%`; particles `crit`, `sweep_attack`.
  (b) *Nine lives*: `on_low_health 30` -> `shield` + `effect speed II` + `move_self back 6`, once per fight
  [P:once_per_fight]. (c) *Ambusher*: 4 s self-`invisibility`, the next hit is an `empower`ed crit (+30% `crit_damage`).
- **Personality:** aloof/kuudere, lazy-sleepy, mischievous, genki, tsundere.
- **Non-combat:** fishing (`angler` aura), pest control and night watch (`lookout`), a nose for food (`thrifty`).
- **Boss:** circling hit-and-run. Tell: crouch and wiggle 0.6 s, then the pounce. Phase 2 "second life": at 30% she
  collapses, rolls away and rises with a shield and speed. Phase 3: claw flurry. Punish: after each pounce she
  lands and licks a paw for 1 s.
- **Leader:** "<colour/moon> + Paw/Whisker + Society/Guild" (Moonlit Whisker Guild, Calico Lantern Society).
  Independent, few pacts, opportunistic night raids on the weakest, fishing-heavy economy. Style: birch, orange or
  white wool.

#### 2. Kitsune (fox spirit)
- **Gender:** both (often F; fox-eared young men are common too).
- **Look:** tall fluffy fox ears; 1-9 tails (tail count shows power and can show the phase); fox mask worn on the
  side of the head; layered shrine or court kimono; palettes gold/white/vermilion or silver/indigo with blue foxfire;
  folding fan, orbiting foxfire wisps, prayer beads.
- **Anim:** wisps orbit; tails fan into a radial display on a phase change; sly half-lidded blink; ear tilt;
  sleeve-to-mouth laugh.
- **Kits:** (a) *Foxfire caster*: `volley` of 3-5 `blaze_powder` projectiles, particle `soul_fire_flame`, fire `dot`;
  ultimate: a foxfire ring `zone`. (b) *Illusionist*: 3 s self-`invisibility` + `move_self back`, leaving a decoy
  [P:decoy]; `effect blindness` (players); `mark "bewitched"` -> `on_mark_full` 1 s `stun`.
  (c) *Charmer*: `cone` `weakness` + `slowness`; charmed enemy NPCs briefly fight each other [P:charm].
- **Personality:** playful trickster, ancient sage in a youthful body, sly flirt, gentle guardian, haughty noble.
- **Non-combat:** trading (`haggler`), harvest blessing on rice and wheat (`green_thumb`), diplomacy [P:diplomacy],
  fortune telling (`lookout`).
- **Boss:** zoner with foxfire volleys. Tails grow by phase (3 -> 6 -> 9). Phase 2: decoys appear; the real one is
  the one whose tail shadow shows (the folklore "a fox cannot hide its tail"). Phase 3: foxfire storm zones. Tell:
  tails flare before each volley. Punish: after the storm she sits fanning herself.
- **Leader:** "<colour> + Fan/Lantern/Tail + Court/House" (Vermilion Fan Court, Ninefold Lantern House). Many pacts,
  breaks them when it profits (low honour), trade-heavy, rare but surgical raids. Style: cherry planks, red/white
  wool.

#### 3. Wolf-kin (ōkami-mimi)
- **Gender:** both (often M).
- **Look:** shaggy hair, upright wolf ears, bushy tail, fur-collared cloak, a scar, amber eyes; palettes grey/white/
  navy or russet/black; twin hand-axes or a greatsword, bone charms.
- **Anim:** sniffing head-tilt idle; howl on fight start (`entity.wolf.howl`); bared-teeth snarl; ears pin back
  when angry.
- **Kits:** (a) *Pack leader*: `summon wolf` x2-3 (named); `allies` howl -> `stat attack +15%`; `on_ally_attack`
  joins every hit (pack tactics). (b) *Tracker*: `mark "scent"` with `glow`, `dash` to the marked target, damage
  per mark stack. (c) *Moon frenzy*: `empower` at night only, size 110%, skills locked [P:time_condition].
- **Personality:** loyal, stoic lone wolf, hot-blooded, gruff big brother, feral child.
- **Non-combat:** tracking and scouting (`lookout`, pathfinding), hunting and butchery, animal care (wolves),
  guard drills (`drill`).
- **Boss:** never alone: wolves flank while it holds the front. Tell: 1 s howl pose before every buff. Phase 2 at
  night or below 50%: frenzy. Punish: when its wolves die it staggers for 1.5 s (broken bond) [P:summon_link].
- **Leader:** "<colour> + Fang/Howl + Pack/Clan" (Grey Hollow Pack, Howling Ridge Clan). Territorial, very defensive,
  holds grudges (raids whoever killed its members), loyal to pacts. Style: spruce, stripped logs, grey wool.

#### 4. Tanuki (raccoon-dog trickster)
- **Gender:** both.
- **Look:** round, soft silhouette; round ears; thick ringed tail; a leaf balanced on the head (the transformation
  charm); straw hat; merchant's coat or a belly sash; sake gourd; palette brown/cream with leaf green.
- **Anim:** the leaf wobbles; belly-drum idle; puff of smoke on transformation; eyes go comically round when caught.
- **Kits:** (a) *Trickster*: `nova` of `poof` smoke (`effect blindness` on players, `slowness`) + `move_self back`;
  disguise as a barrel or lantern [P:disguise]. (b) *Belly drum*: `nova` `knockback 3`, `allies` `speed` buff,
  sound `block.note_block.bell`. (c) *Sticky gourd*: `shot` with `slime_ball` look, `slowness II`.
- **Personality:** jolly glutton, lazy, cunning merchant, prankster, big-hearted uncle or aunt.
- **Non-combat:** trading (`haggler`), brewing and cooking (chef, `thrifty`), storytelling morale [P:morale].
- **Boss:** comic but slippery. Phase 1 smoke and decoys; phase 2 "giant form" (size 110%, knockback resistance);
  phase 3 drum barrage. Tell: pats its belly twice before a shockwave. Punish: after the giant form it shrinks and
  sits dazed.
- **Leader:** "<jolly word> + Lantern/Gourd + Company/Merchants" (Jolly Gourd Company, Leaf-and-Lantern Merchants).
  Trade first, pacts with everyone, avoids war until cornered, then ambushes. Style: dark oak, brown/green wool.

#### 5. Moon rabbit (usagi-mimi)
- **Gender:** both (often F).
- **Look:** tall long ears (one floppy as a quirk), cotton tail, small stature, red eyes; white/pink/silver palette
  with crescent-moon motifs; a rice-cake mallet as the weapon (mace).
- **Anim:** nose twitch; ears droop when sad and spring up when alert; bunny-hop idle; mallet spin.
- **Kits:** (a) *Mallet hop*: mace (density); `leap` (range 10) with self `jump_boost`; landing `nova` `knockback`
  up 1; sound `item.mace.smash_ground`. (b) *Rice-cake healer*: `ally` `heal` + `regeneration` [P:ally_projectile
  for a thrown heal]. (c) *Moon tide*: stronger on full-moon nights [P:moon_condition].
- **Personality:** genki, timid/dandere (startles easily), cheerful baker, airhead.
- **Non-combat:** baking and cooking (chef), carrot and wheat farming (`green_thumb`), herbal care (`mend`).
- **Boss:** bouncy and hard to pin. Tell: ears go flat and she crouches 0.8 s before the slam; a ring of `white_ash`
  marks the landing [P:landing_marker]. Phase 2: three chained leaps. Phase 3: the "moon drop", one huge leap with a
  long shadow tell. Punish: the mallet sticks in the ground for 1.2 s.
- **Leader:** "Moon/Silver + Mallet/Burrow + Bakery/Warren" (Moonmallet Warren, Silver Burrow). Peaceful, food-rich,
  recruits a lot (`rally`), defends rather than raids. Style: birch, white or pink wool.

#### 6. Oni (horned ogre)
- **Gender:** both.
- **Look:** big, broad silhouette; one or two horns; red, blue, green or yellow skin tint; wild hair; tiger-stripe
  garments, rope belt, gourd; iron-studded club (mace); palettes red/black/gold or blue/white; fangs.
- **Anim:** shoulder-roll idle, drinks from the gourd, horns glow when enraged, ground stomp.
- **Kits:** (a) *Iron club*: mace (density, breach); `cone` slam with `knockback 3` + 1 s `stun`; `on_hurt` ->
  `stat attack +10%`, `max_stacks 3` (rage). (b) *Brawler*: `dash` shoulder charge; gourd drink = self `heal` +
  `regeneration`. (c) *Coloured oni*: red = fire (`ignite`), blue = frost (`freeze`), yellow = shock (`chain`).
  Folklore weakness: thrown beans; here, snowballs or eggs that hit it during the rage break the rage
  [P:item_weakness].
- **Personality:** hot-blooded battle lover, jovial drinker, gentle giant, honourable brute, bully.
- **Non-combat:** heavy hauling and mining (`diligence`, `prospector`), smithing, construction (`mason`).
- **Boss:** slow, heavy telegraphs: the club rises overhead for 1 s; big area hits. Phase 2: horns glow, faster.
  Phase 3: grabs and throws [P:grab]. Punish: the club sticks in the ground after the overhead slam.
- **Leader:** "<colour/metal> + Horn/Club + Clan/Band" (Iron Horn Clan, Crimson Club Band). Raids often, respects
  only strength, allies only with strong factions, recruits fighters. Style: polished blackstone, red wool.

#### 7. Tengu (mountain crow spirit)
- **Gender:** both (often M).
- **Look:** black crow wings (or the red long-nosed mask variant); mountain-ascetic robes with pom-pom sash, small
  black cap, tall one-tooth wooden clogs; feather fan, ringed staff; palette black/white/red/orange.
- **Anim:** wing flare, fan flick, bird-like head tilt, sheds feathers when hurt.
- **Kits:** (a) *Gale fan*: `cone` with `gust` particles and `knockback 3` up 1; `shot` with `wind_charge` look;
  sound `entity.breeze.wind_burst`. (b) *Mountain swordmaster*: sword striker, `dash`, `stat attack_speed +20%`.
  (c) *Sky step*: `leap` + self `slow_falling`, dive strike [P:glide].
- **Personality:** proud/arrogant, stern mentor, mischievous, ascetic hermit.
- **Non-combat:** martial training (`drill`), mountain scouting (`lookout`), woodcutting.
- **Boss:** aerial. Leaps, hovers with slow falling, dives. Tell: wings spread and the fan is raised. Phase 2:
  whirlwind `zone` that `pull`s. Phase 3: feather volleys (`volley`, `feather` look). Punish: after a dive it folds
  its wings and catches its breath.
- **Leader:** "<peak/wind> + Crow/Gale + Order/Monastery" (Crow Peak Order, High Gale Hermitage). Elitist, small elite
  army, trains hard, refuses pacts until it respects you. Style: spruce, stone bricks, black wool.

#### 8. Yuki-onna (snow spirit)
- **Gender:** both (often F; male counterpart: a snow hermit or frost lord).
- **Look:** very pale skin; long black or white hair; white kimono fading to ice blue; bare feet or straw boots;
  breath mist; icicle hair pins; palette white/ice-blue/silver; ice fan or paper umbrella.
- **Anim:** breath fog, slow serene blink with frosted lashes, hair drifting in an unfelt wind, frost spreads from
  the hem.
- **Kits:** (a) *Blizzard controller*: `zone` frost with `freeze` 2 s + `slowness II`, `snowflake` particles;
  `summon snow_golem` x2. (b) *Frozen breath*: `bolt` frost with `drain 20`. (c) *Ice armour*: `shield` +
  `stat damage_taken -20`, stronger in snowy biomes [P:biome_condition].
- **Personality:** kuudere, melancholic, aloof beauty, quietly kind, vengeful.
- **Non-combat:** food preservation (`thrifty`), ice fishing (`angler`), cold storage [P:job].
- **Boss:** the arena becomes a storm. Phase 1 opens with a polite warning line; phase 2 the snowstorm zone covers
  the arena and snow golems appear; phase 3 a freeze-lock ultimate. Tell: frost particles crawl along the ground
  1 s before each zone. Punish: after the ultimate she turns translucent and slow for 2 s.
- **Leader:** "Pale/Silent + Snow/Drift + Court/Veil" (Silent Drift Court, Pale Veil). Isolationist, defensive, high
  honour, punishes pact breakers relentlessly. Style: spruce, packed ice details, white wool.

#### 9. Kappa (river sprite)
- **Gender:** both.
- **Look:** green-blue skin tint; turtle shell backpack; the water-filled dish on the head; webbed hands; raincoat
  or swim gear; palette teal/green/yellow; trident.
- **Anim:** the head-dish sloshes; bows politely; blinks with a second eyelid.
- **Kits:** (a) *River wrestler*: `pull 2` + 1 s `stun` (grab), trident thrust. (b) *Water mage*: shock-element `shot` with
  `prismarine_shard` look, `splash` particles; shock damage gets the v1 bonus against targets in water or rain.
  (c) *Shell guard*: self `shield`, high knockback resistance. Folklore weakness: if a player crouches (bows) in
  front of it, it bows back and spills its power (a brief self-debuff) [P:etiquette_weakness].
- **Personality:** polite but mischievous, wrestling-mad braggart, shy.
- **Non-combat:** fishing (`angler`), irrigation and farming (`green_thumb`), cucumber-loving trader (`haggler`).
- **Boss:** strongest near water. Grapples; lures you into rivers. Phase 2: the dish spills, it gets angrier and
  weaker. Tell: arms spread wide before a grab. Punish: a missed grab leaves it face-down for 1.2 s.
- **Leader:** "River/Reed + Shell + Clan/Lodge" (Riverbend Shell Lodge). Fishing and trade economy, wants waterfront
  sites, raids rarely. Style: prismarine, mangrove, cyan wool.

#### 10. Spider-weaver (jorōgumo type)
- **Gender:** both (often F; male counterpart: a silk-weaving spider lord).
- **Look:** elegant; extra eyes as jewellery or a spectral set of spider legs on the back; silk kimono with a web
  pattern; palette black/violet/gold; glinting silk threads.
- **Anim:** fingers weave in idle; threads glint; multi-eye blink; head tilt.
- **Kits:** (a) *Web trapper*: `zone` venom with `slowness III` (web shown with particles, no blocks placed).
  (b) *Fangs*: melee venom `dot`, `max_stacks 3`. (c) *Tether*: `line` `pull 2` from range.
- **Personality:** patient schemer, seductive manipulator, aloof, sinister mother figure.
- **Non-combat:** weaving and tailoring (armour upkeep) [P:job], trapping and watch (`lookout`), silk trade.
- **Boss:** setplay. The arena fills with web zones and marked threads. Tell: glittering thread lines appear 1 s
  before a pull. Phase 2: climbs walls and drops in [P:wall_climb]. Punish: after the drop she re-spins for 1.5 s.
- **Leader:** "Silken/Veil + House/Web" (Silken Veil House). Spy network (more scouts) [P:scout_bonus], manipulative
  pacts, ambushes. Style: dark oak, black/purple wool.

#### 11. Eastern dragonkin (rain and sea dragon heir)
- **Gender:** both.
- **Look:** antler-like horns; long whiskered tail; scales on the cheeks; flowing robes in jade, azure and gold;
  a floating pearl; palettes jade/gold or azure/silver.
- **Anim:** the pearl orbits; whiskers sway; scales shimmer when angry; pupils turn to gold slits in phase 2.
- **Kits:** (a) *Rain caller*: `zone` shock pulses with `electric_spark`, `chain` lightning (no real lightning).
  (b) *Pearl*: `bolt` arcane; `allies` `shield`. (c) *Serpent coil*: `dash` through a line of enemies + `knockback`.
- **Personality:** proud noble, benevolent ruler, lazy hoarder, serene sage.
- **Non-combat:** rain blessing for crops (`green_thumb`, `regrowth`), fishing (`angler`), counsel and diplomacy.
- **Boss:** zoner caster. Phase 2 calls rain [P:weather], which powers up shock. Tell: the pearl brightens 1 s
  before each strike. Punish: after the storm it coils to rest.
- **Leader:** "Jade/Azure + Tide/Cloud + Dynasty/Throne" (Jade Tide Dynasty). Imperial: expects tribute
  [P:tribute], large army, takes pacts only as the senior partner. Style: prismarine, gold blocks as trim, cyan wool.

#### 12. Western dragonkin (wyrm-blooded warrior)
- **Gender:** both.
- **Look:** heavy curved horns, folded leathery wings, thick tail, scale patches, claw-motif armour; palettes
  crimson/black/gold or emerald/bronze; greatsword or greataxe.
- **Anim:** tail lashes, smoke from the nostrils, wings flare on a phase change, eyes glow.
- **Kits:** (a) *Dragon breath*: `cone` fire with `ignite` + `dot`, particles `flame`, `dragon_breath`; sound
  `entity.ender_dragon.growl`. (b) *Wing buffet*: `nova` `knockback 4` + `move_self up`. (c) *Hoard guard*:
  stronger near its own base's storage [P:near_home].
- **Personality:** proud, greedy hoarder, honourable, tsundere, battle-hungry.
- **Non-combat:** smithing and smelting (forge fire), treasure mining (`prospector`).
- **Boss:** phase 1 grounded melee; phase 2 wing leaps and breath; phase 3 hoard rage. Tell: the chest glows orange
  during a 1 s inhale before the breath. Punish: it coughs smoke for 1 s afterwards.
- **Leader:** "Ember/Ash + Scale/Wing + Dominion/Hoard" (Ember Scale Dominion). Greedy: raids rich factions for
  loot, hoards gold, expands. Style: deepslate tiles, red wool, gold trim.

#### 13. Guardian lion-dog (komainu) and stone guardians
- **Gender:** both (often M; komainu come in pairs, so male-female pairs fit well).
- **Look:** curly mane, lion-dog ears, curled tail, stone-like armour, one open mouth and one closed (the pair);
  a bell; palette stone grey/jade/gold. Variant: a living stone golem, gentle giant.
- **Anim:** statue-still idle where only the eyes blink; mane bristles before a roar.
- **Kits:** (a) *Gate guardian*: shield, `taunt`, `allies` `shield`. (b) *Twin roar*: `cone` 1 s `stun` + `knockback`;
  sound `entity.ravager.roar`. (c) *Stone skin*: `empower` with `damage_taken -30`, `speed -20`.
- **Personality:** dutiful, stoic, earnest rookie, gruff veteran, gentle giant.
- **Non-combat:** gate watch (`lookout`), building (`mason`), training soldiers (`drill`).
- **Boss:** a wall that counters. Phase 2 stone skin: nearly immobile and armoured; players learn to wait it out.
  Tell: mane bristles 1 s before the roar. Punish: leaving stone skin takes 1.5 s.
- **Leader:** "Twin/Stone + Gate + Wardens/Watch" (Twin Gate Wardens). Defensive, rarely raids, high honour,
  protects weaker factions in pacts. Style: stone bricks, green wool.

#### 14. Plant spirit (dryad, flower maiden, thorn knight)
- **Gender:** both.
- **Look:** leaves or blossoms in the hair, bark-textured armour, vines; greens with one blossom accent (cherry pink,
  sunflower yellow); a living-wood staff or a thorn whip.
- **Anim:** flowers open and close with mood; leaves fall when hurt; petal eyelashes.
- **Kits:** (a) *Bloom healer*: `allies` `heal` + `regeneration`, particles `cherry_leaves`, `spore_blossom_air`.
  (b) *Thorn whip*: `line` venom + `pull`. (c) *Root snare*: `zone` `slowness III` then 1 s `stun`.
- **Personality:** gentle motherly, shy, wild child, ancient grump.
- **Non-combat:** farming (`green_thumb`, `regrowth`), forestry and replanting, herbalism (`mend`).
- **Boss:** the arena slowly grows root zones. Phase 2: a self-heal "bloom" channel that players must interrupt
  [P:interruptible_channel]. Tell: buds glow before the roots. Punish: a broken bloom leaves it wilted for 2 s.
- **Leader:** "Verdant/Bloom + Circle/Grove" (Verdant Bloom Circle). Peaceful, protective of forests, defensive.
  Style: oak, moss, flowering azalea details, green wool.

### B. Shrine, court and martial Japan

#### 15. Shrine maiden (miko) and shrine priest
- **Gender:** F as miko; the male counterpart is the shrine priest (same kit, robes in white and pale blue or
  purple instead of red).
- **Look:** white kimono top, red pleated trousers (hakama), long hair tied low with white paper or a red ribbon,
  sometimes detached sleeves; gohei wand (paper streamers on a stick), a cluster of kagura bells, paper talismans
  (ofuda); palette red/white with a gold accent.
- **Anim:** bell shake; slow, graceful dance steps; talismans fanned between the fingers; prayer pose with eyes
  closed (blinking stops while channelling); sleeves flutter.
- **Kits:** (a) *Talisman thrower*: `volley` of talismans (`feather` look) with radiant damage (extra against undead);
  `mark "seal"` -> `on_mark_full` 1 s `stun`. (b) *Barrier*: `allies` `shield`; a sanctuary ring that cleanses and
  regenerates allies [P:ally_zone]. (c) *Purification*: `dispel` on enemies + `cleanse` on allies; sound
  `block.bell.use`.
- **Personality:** dutiful and serious, gentle, lazy shrine-sweeper who is secretly strong, comically greedy for
  offerings, cheerful.
- **Non-combat:** healing (`mend`), charm-making and enchanting (enchanter), divination (`lookout`), festivals for
  morale [P:morale], keeping the shrine (`mason`).
- **Boss:** a bullet-pattern zoner. Fans of talismans, then sealing circles on the ground (`zone` with a 1 s delay
  and an `enchant` ring). Phase 2: a barrier that must be broken by hits before damage gets through [P:break_shield].
  Phase 3: a purifying dance of rotating zones. Tell: a bell ring before each circle. Punish: after the dance she
  kneels to pray for 1.5 s.
- **Leader:** "<colour> + Gate/Bell + Shrine/Sisterhood" (Vermilion Gate Shrine, Clear Bell Sanctum). Defensive,
  hunts undead and monster factions, high honour, allies with peaceful neighbours. Style: cherry planks, red and
  white wool.

#### 16. Yin-yang diviner (onmyōji)
- **Gender:** both (often M).
- **Look:** tall black court cap; layered hunting robe in white/indigo/violet; five-pointed star crest; paper
  dolls; folding fan; palette white/indigo/black.
- **Anim:** fingers form hand seals; a paper doll unfolds into a summon; a star traced in the air (particle trail).
- **Kits:** (a) *Familiar summoner*: `summon wolf` x2 shown as paper beasts [P:summon_skin]. (b) *Five phases*: the
  element rotates with each skill (wood/venom -> fire -> earth/physical -> metal/shock -> water/frost) [P:element_cycle].
  (c) *Star seal*: `zone` with `enchant` particles, 1 s `stun`, `slowness`.
- **Personality:** aloof genius, calm strategist, sly court politician, bookish.
- **Non-combat:** scholarship and astronomy (enchanter XP), divination and scouting (`lookout`), calendar-keeping
  for farms (`green_thumb`).
- **Boss:** summoner plus caster. Phase 2: the element cycle starts, shown by the robe's colour. Phase 3: star
  seals lock the arena edges. Tell: visible hand seals for 1 s. Punish: re-folding a destroyed familiar takes 2 s.
- **Leader:** "Star/Seal + Bureau/Office/Observatory" (Starseal Bureau). Planner: raids at well-chosen moments,
  many scouts, formal pacts. Style: dark oak, purple/white wool.

#### 17. Samurai (sworn retainer)
- **Gender:** both.
- **Look:** lacquered plate armour, or trousers and a crested half-coat; topknot or high ponytail; long and short
  swords; palettes lacquer red/black or indigo/white; a crested helmet that goes on in phase 2.
- **Anim:** bows before the duel; hand rests on the hilt; eyes close, then open sharp.
- **Kits:** (a) *Sword striker*: sword (sharpness, sweeping edge), a three-hit chain [P:combo], `dash` cut.
  (b) *Counter stance*: parry and riposte with 1 s `stun` [P:counter]. (c) *Lord's banner*: `allies`
  `stat damage_dealt +15%`.
- **Personality:** honourable stoic, earnest rookie, strict disciplinarian, loyal retainer.
- **Non-combat:** training (`drill`), leadership (`rally`), calligraphy and tea ceremony for morale [P:morale].
- **Boss:** footsies duel. Announces its name before fighting. Phase 2 puts on the helmet (armour up); phase 3 two
  swords. Tell: the click of the guard as the blade is loosened. Punish: after a counter whiffs it is open for 1 s.
- **Leader:** "House of the <crest>" (House of the Crane Crest). Honour-bound: declares war before raiding
  [P:declaration], never attacks a faction that is already beaten, keeps every pact. Style: dark oak, white wool,
  red trim.

#### 18. Quick-draw wanderer (iai swordsman, masterless)
- **Gender:** both (often M; the travelling swordswoman with a veiled hat is equally classic).
- **Look:** worn travelling kimono, wide straw hat, scar, stubble or loose hair; a sheathed blade at the hip;
  palette faded brown/grey with one accent.
- **Anim:** eyes hidden under the hat until the strike; total stillness before the draw; a slow re-sheathe.
- **Kits:** (a) *Single stroke*: 1.5 s of stillness, then an instant `line` cut (range 8) with heavy damage; sound
  `entity.player.attack.sweep`. (b) *Drifter*: permanent `speed`, retreats after each exchange (skirmisher).
  (c) *Sake cup*: self `regeneration` between bouts.
- **Personality:** cynical drifter, laid-back, haunted by the past, flirt, stoic.
- **Non-combat:** campfire cooking (chef), woodcutting, bodyguard work (`duelist`).
- **Boss:** a rhythm of stillness and one strike. Tell: hand on hilt, a glint particle, 1.5 s still; dodge sideways.
  Phase 2: a double draw; phase 3: afterimages on each cut. Punish: the 1 s re-sheathe after every draw.
- **Leader:** "Wandering/Drifting + Blade/Hat + League" (Drifting Hat League). Mercenary: sells alliance for goods
  [P:paid_pact], opportunistic raids, a small elite force. Style: spruce, brown wool.

#### 19. Ninja and kunoichi
- **Gender:** both.
- **Look:** slim dark silhouette, scarf or mask, high ponytail, a short reverse-grip blade, kunai, thrown stars;
  palette black/navy with a red or purple scarf.
- **Anim:** the scarf trails behind; hand seals; vanishing crouch; only the eyes show.
- **Kits:** (a) *Shadow step*: 3 s self-`invisibility`, `dash` to the target's back, crit hit. (b) *Star volley*:
  `volley` of thrown stars [P:projectile_look] with a venom `dot`. (c) *Smoke bomb*: `nova` `blindness`/`darkness`
  (players) + `move_self back`; a log decoy [P:decoy].
- **Personality:** silent professional, cheerful show-off, loyal shadow, mischievous.
- **Non-combat:** scouting and espionage (`lookout`, pathfinding), sabotage [P:sabotage], quick hauling.
- **Boss:** stealth. It vanishes; footstep puffs and a smoke puff give it away before it reappears. Phase 2:
  decoy clones; phase 3: a chain of dashes. Punish: after the dash chain it kneels to reset.
- **Leader:** "Veiled/Quiet + Smoke/Shadow + Clan" (Quiet Smoke Clan). Many scouts, night raids, sabotage, distrusts
  pacts. Style: dark oak, black wool.

#### 20. Warrior monk (temple guard, mountain ascetic)
- **Gender:** both (often M).
- **Look:** hood or head wrap, monk robes over armour, large prayer beads, a glaive/naginata (trident); palette
  white/grey/saffron.
- **Anim:** chanting mouth, clicking beads, polearm spin.
- **Kits:** (a) *Glaive sweep*: `cone` wide arc with `knockback`. (b) *Iron body*: self `shield` + `taunt`.
  (c) *Sutra chant*: `allies` `regeneration` + `resistance`.
- **Personality:** stern, jovial big brother, zealous, serene.
- **Non-combat:** healing (`mend`), temple building (`mason`), meditation drills (`drill`).
- **Boss:** a spinning wall of polearm. Phase 2: chant aura that heals nearby allies (kill or separate them).
  Tell: the beads glow before the chant. Punish: the polearm spin ends with 1 s of dizziness.
- **Leader:** "Mountain/Temple + Brotherhood/Sisterhood" (Cloudstep Temple). Defensive protector, keeps pacts,
  fights monster factions. Style: stone, orange wool.

#### 21. Fan dancer (sacred dancer, stage dancer)
- **Gender:** both (often F; the male counterpart is a stage dancer in bold make-up).
- **Look:** flowing layered silks, twin folding fans or long ribbons, hair ornaments; pastel or vivid gradients.
- **Anim:** constant turning; the fan snaps open as the tell; a wink.
- **Kits:** (a) *Tempo buffer*: `allies` `speed`/`haste`; tempo stacks that rise while it keeps dancing [P:tempo].
  (b) *Fan blades*: `chain` or `volley`. (c) *Bewitching dance*: `cone` `weakness` + `slowness`.
- **Personality:** graceful, flirtatious, elegant noble, cheerful performer.
- **Non-combat:** festivals and morale [P:morale], trade, diplomacy [P:diplomacy].
- **Boss:** rhythm-based. Attacks land on a beat shown with `note` particles, so players dodge on the beat. Phase 2:
  the tempo rises. Tell: the fan snap. Punish: the final pose of each sequence (1 s).
- **Leader:** "Silk/Fan + Troupe/Pavilion" (Silken Fan Pavilion). Diplomatic, holds festivals, trades culture.
  Style: cherry, pink wool.

#### 22. Delinquent brawler (yankii, sukeban)
- **Gender:** both.
- **Look:** long open coat with embroidered mottos (original glyphs), bleached or sculpted hair, or a long skirt and
  a face mask; a bat or pipe (mace or axe) or bandaged fists; palette black/white/purple/gold.
- **Anim:** the deep squat idle, chewing gum, glare, knuckle crack.
- **Kits:** (a) *Street brawler*: `dash` shoulder charge with `knockback 3`, fast punches. (b) *Gang call*: `allies`
  `attack` buff, or allied thugs [P:summon_npc]. (c) *Never back down*: `on_low_health 30` -> `empower` "Second Wind".
- **Personality:** tough with a heart of gold, hot-blooded, loyal, tsundere, bully.
- **Non-combat:** hauling (strength), secretly great cooking (chef), gang leadership (`rally`).
- **Boss:** rushdown with taunts. Phase 2: coat off, faster; phase 3: the gang arrives. Tell: the knuckle crack
  before the charge. Punish: after the charge it skids and turns slowly.
- **Leader:** "<flower/beast> + Gang/Crew" (Black Lily Gang, Thunder Lane Crew). Territorial, picks fights with
  neighbours, respects whoever beats it (offers a pact after a defeat) [P:respect_pact]. Style: dark wood, purple
  wool.

#### 23. Battle chef
- **Gender:** both.
- **Look:** chef's coat, bandana or tall hat, knives (sword), a cleaver (axe), a wok or pan used as a shield;
  palette white/red.
- **Anim:** knife twirl, tasting, a sweat drop.
- **Kits:** (a) *Cleaver combo*: axe with a bleed `dot`. (b) *Feast*: `allies` `heal` + `regeneration`.
  (c) *Spice bomb*: `cone` fire with `ignite`.
- **Personality:** hot-blooded perfectionist, cheerful glutton, gruff kitchen tyrant.
- **Non-combat:** cooking (chef, `thrifty`), farming, fishing, feeding recruits (`rally`).
- **Boss:** fast slicing with food breaks: it eats to heal mid-fight unless you press it. Tell: the knife twirl.
  Punish: while it is eating.
- **Leader:** "Golden/Iron + Ladle/Wok + Guild/Kitchen" (Golden Ladle Guild). Food trade, cheap recruitment, peaceful.
  Style: oak, white wool.

#### 24. Storm drummer (thunder spirit)
- **Gender:** both.
- **Look:** a ring of drums on a frame behind the back, wild hair, small horns optional, tiger stripes or cloud
  patterns; palette yellow/purple/black with lightning motifs.
- **Anim:** drumsticks twirl; each drum glows as it is struck; hair stands up before thunder.
- **Kits:** (a) *Thunder beat*: `nova` shock + `knockback` per beat. (b) *Drum chain*: `chain` lightning (3-5 jumps).
  (c) *Storm*: `zone` shock pulses; stronger in rain [P:weather].
- **Personality:** boisterous, hot-blooded, festival lover, short-tempered.
- **Non-combat:** festival morale [P:morale], rallying recruits (`rally`).
- **Boss:** rhythm and counting: the number of glowing drums shows how many strikes are coming. Phase 2: calls a
  storm. Tell: drums light up one by one. Punish: after the final beat it pants with its sticks lowered.
- **Leader:** "Thunder/Storm + Drum + Band/Festival" (Rolling Thunder Festival). Loud and aggressive, raids in
  storms. Style: spruce, yellow wool.

### C. Wuxia, xianxia and wider East Asian myth

#### 25. Sword cultivator (wandering immortal)
- **Gender:** both.
- **Look:** flowing robes with long sleeves, a hair crown or jade pin, a straight double-edged sword, floating
  swords behind the back; palette white/azure/jade; a faint aura.
- **Anim:** sleeves billow; flying swords orbit; floats cross-legged in meditation idle.
- **Kits:** (a) *Flying swords*: `volley` of swords [P:projectile_look] or a `line` sword-beam. (b) *Light-body
  leap*: long `leap` + self `slow_falling`. (c) *Breakthrough*: `empower` "next realm" with stat boosts and a new
  aura colour.
- **Personality:** aloof immortal, righteous hero, arrogant young master or mistress, serene sage.
- **Non-combat:** elixirs and herbalism (`mend`), teaching disciples (`drill`), meditation.
- **Boss:** phase 1 swordplay; phase 2 an orbit of swords that fire one by one; phase 3 a rain of swords (`zone`).
  Tell: the swords align toward the target. Punish: the swords return to the sheath.
- **Leader:** "<colour> + Cloud/Peak + Sect" (Azure Cloud Sect). Recruits talent, trains hard, looks down on weak
  factions. Style: birch, quartz-like white, light blue wool.

#### 26. Martial artist (fist, palm and kick schools)
- **Gender:** both.
- **Look:** high-collared fitted top with frog buttons and side-split skirt or wide trousers, hair buns or a braid,
  hand wraps; or a sleeveless training gi and headband; palette red/gold/black or white/blue.
- **Anim:** stance shifts; light bouncing; fists glow with inner energy.
- **Kits:** (a) *Flurry*: unarmed-style fast hits (`stat attack_speed +20%`), `dash` palm strike with `knockback 4`.
  (b) *Energy wave*: `line` arcane or `shot`. (c) *Rising kick*: `knockback` up 2 against leaping enemies, an
  anti-air [P:anti_air].
- **Personality:** genki, disciplined, cocky, gentle master.
- **Non-combat:** training (`drill`), hauling, cooking dumplings (chef).
- **Boss:** all-rounder footsies. Phase 2: switches between two animal stances (power and evasion) [P:stance].
  Tell: the stance pose. Punish: after the combo ender.
- **Leader:** "<metal> + Palm/Fist + School" (Iron Palm School). Challenges rivals to duels, trains everyone.
  Style: dark oak, red wool.

#### 27. Talisman-bound revenant (jiangshi)
- **Gender:** both (often F in modern anime-styled art).
- **Look:** an old official's robe and round hat, a paper talisman stuck to the forehead (covering one eye), arms
  held out stiffly, pale blue-grey skin, long sleeves; palette navy/red with the yellow talisman.
- **Anim:** hops instead of walking; the talisman flutters on each hop; eyes blank or closed; arms stay forward.
- **Kits:** (a) *Hop slam*: repeated short `leap`s. (b) *Life drain*: melee `damage` with `drain 30`.
  (c) *Bound*: the talisman is a weak point: a hit on the head freezes it for 1 s [P:weak_point]. Folklore: it
  senses breath, so sneaking players are harder for it to find [P:sneak_stealth].
- **Personality:** cheerful undead (comic), obedient servant, melancholic.
- **Non-combat:** tireless night work [P:night_shift], mining without fear (`diligence`).
- **Boss:** relentless hopping. Phase 2: the talisman tears off and it goes berserk, faster but unguided. Tell: a
  crouch before each long hop. Punish: after landing it wobbles for 0.8 s.
- **Leader:** best for the monster faction: "Lantern of the Restless", "Paper Seal Procession". Never sleeps, raids
  at night, recruits undead [P:undead_recruits]. Style: dark oak, blue wool.

#### 28. Phoenix-kin (fire bird)
- **Gender:** both.
- **Look:** plume crests, hair that flickers like flame at the tips, a feather cape, a golden mask; palette
  orange/gold/crimson.
- **Anim:** flame flickers in the hair; feathers ignite on an attack; folds into its cape when hurt.
- **Kits:** (a) *Fire volley*: `volley` with `fire_charge` look and `ignite`. (b) *Healing ember*: `allies` `heal`
  + `fire_resistance`. (c) *Rebirth*: once per fight at 0 health, it bursts into flame and returns at 30%
  [P:rebirth].
- **Personality:** proud, warm-hearted, dramatic, wise elder.
- **Non-combat:** smelting (smelter), cooking, warming the base in winter.
- **Boss:** the rebirth is the final phase: after the "kill" it rises from ash with a new pattern. Tell: wings of
  fire spread 1 s before the dive. Punish: after rebirth it is fragile for 2 s.
- **Leader:** "Ashen/Rising + Flame/Plume + Court" (Rising Plume Court). Resilient: rebuilds fast after losses,
  bold raids. Style: red sandstone-like warm blocks, orange wool.

### D. Western fantasy through an anime lens

#### 29. Knight (sword and shield)
- **Gender:** both (the noble-born lady knight and the veteran knight commander are both stock types).
- **Look:** plate armour, cape, plume or ribbon, heraldic tabard; palette steel plus a house colour.
- **Anim:** raises the shield with a ring; salutes with the blade; visor snaps down in phase 2.
- **Kits:** (a) *Shield wall*: real shield blocking, `taunt`, `allies` `shield`. (b) *Shield bash*: `dash` with 1 s
  `stun`. (c) *Oath*: `on_ally_low_health` -> `stat damage_taken -20` on that ally.
- **Personality:** chivalrous, strict, earnest, proud, clumsy outside battle.
- **Non-combat:** training (`drill`), leadership (`rally`), equipment upkeep (blacksmith).
- **Boss:** front hits bounce off the shield; flank it. Phase 2 charges across the arena. Tell: shield raised high
  before the bash. Punish: after the charge it plants its shield to stop.
- **Leader:** "Order of the <flower/metal>" (Order of the Silver Rose). Lawful, honours pacts, protects the weak.
  Style: stone bricks, blue or white wool.

#### 30. Paladin (holy knight)
- **Gender:** both.
- **Look:** gilded armour, white cloak, a halo-like crest, warhammer (mace) or longsword; palette white/gold/blue.
- **Anim:** the weapon glows; kneels to pray; light wings flare in phase 2.
- **Kits:** (a) *Smite*: mace with radiant damage (extra against undead). (b) *Healing touch*: `ally` `heal`
  + `cleanse`. (c) *Consecrate*: `zone` radiant on the ground around it.
- **Personality:** righteous, kind, zealous, self-sacrificing.
- **Non-combat:** healing (`mend`), recruitment (`rally`), training (`drill`).
- **Boss:** radiant pillars around the arena. Phase 2: wings of light and stronger heals. Tell: hammer raised to the
  sky. Punish: after a heal it is open.
- **Leader:** "Radiant/Dawn + Oath/Vigil" (Dawn Vigil). Crusades against monster factions, keeps pacts.
  Style: white stone, yellow wool.

#### 31. Battle priest, battle nun and field medic
- **Gender:** both (priest/nun; the medic or apothecary, sometimes beaked-masked, is either).
- **Look:** habit and veil or cassock, rosary or charm string, a heavy tome, a cross-shaped mace or a crossbow;
  medic variant: satchel, vials, bandages; palette black/white/gold or white/red.
- **Anim:** tome pages turn (`item.book.page_turn`); a calm smile that turns into a frightening one.
- **Kits:** (a) *Holy bolts*: crossbow with radiant `bolt`s. (b) *Triage*: `ally` `heal` + `cleanse`.
  (c) *Exorcism*: `dispel` + radiant `damage`, extra against undead.
- **Personality:** strict, gentle, secretly violent (the gap between look and fighting is the joke), drunkard
  priest, calm doctor.
- **Non-combat:** healing (`mend`), scholarship (enchanter), charity (`rally`), herbalism.
- **Boss:** a healer that turns brutal in phase 2 (puts the book away and takes up the mace). Tell: the book snaps
  shut. Punish: re-opening the book.
- **Leader:** "Sisters/Brothers of the <virtue>" (Sisters of the Quiet Lamp). Charitable, defensive, many pacts.
  Style: white stone, light grey wool.

#### 32. Witch and warlock
- **Gender:** both.
- **Look:** wide-brimmed pointed hat, cloak, broom or staff, potion vials on the belt, a familiar (cat, crow, toad);
  palette purple/black/green.
- **Anim:** the hat brim tips; potions bubble; the familiar rides the shoulder.
- **Kits:** (a) *Potion barrage*: `shot`s that leave short `zone`s of `poison`, `slowness` or `weakness`, each a
  different colour. (b) *Cauldron*: `zone` venom that pulses. (c) *Familiar*: a summoned cat or crow [P:summon_types].
- **Personality:** mischievous, grumpy old soul, cheerful apprentice, scholarly, chuunibyou.
- **Non-combat:** brewing [P:brewing_job], herbalism, enchanting (enchanter).
- **Boss:** the potion colour is the tell (green poison, grey slowness, black weakness). Phase 2 flies on the broom
  [P:glide]. Punish: reaching for new vials.
- **Leader:** "<moon/herb> + Coven" (Moonbrew Coven). Secretive, trades potions, curses rivals. Style: dark oak,
  purple wool.

#### 33. Elf ranger (and dark-elf spellblade)
- **Gender:** both.
- **Look:** long pointed ears; light leather and a green hooded cloak; longbow; braided hair; palette green/brown/
  silver. Dark-elf variant: grey or dusky skin, white hair, violet runes, curved twin blades.
- **Anim:** ears twitch; draws an arrow in one fluid motion; kneels to aim.
- **Kits:** (a) *Volley*: bow with `volley` of 3-5 arrows. (b) *Hunter's mark*: `spectral_arrow` `mark` with `glow`,
  then a piercing `line` shot. (c) Dark-elf: blight `dot` blades with `empower` "spellblade".
- **Personality:** aloof ancient, proud, curious, serious; dark-elf: ambitious, cold.
- **Non-combat:** scouting (`lookout`), forestry and replanting, hunting, archery training (`marksman` aura).
- **Boss:** kiting zoner. Phase 2: arrow rain `zone`; phase 3: a charged piercing shot. Tell: a long draw with a
  `glow` line toward the target. Punish: after the charged shot it nocks again.
- **Leader:** "<leaf/moon> + Wardens" (Silverleaf Wardens). Isolationist, protects forests, good scouts.
  Style: spruce or birch, green wool.

#### 34. Vampire noble
- **Gender:** both.
- **Look:** pale skin, red eyes, small fangs, high-collared aristocratic coat or gothic dress, cape, goblet,
  bats; palette black/crimson/white.
- **Anim:** cape swirl; sips from the goblet; eyes glow red; dissolves into bats when dodging (visual only).
- **Kits:** (a) *Blood drain*: melee `damage` with `drain 30`. (b) *Bat swarm*: `volley` or summoned bats
  [P:summon_types]. (c) *Mist step*: `dash` + 2 s self-`invisibility`. Weak in sunlight [P:time_condition].
- **Personality:** proud noble, lonely ancient, sadistic, childish elder in a young body.
- **Non-combat:** night watch (`lookout`), aristocratic diplomacy, trade.
- **Boss:** fights best at night. Phase 2: blood moon `empower`. Tell: the cape rises before the swarm. Punish:
  after the drain it savours the moment for 1 s.
- **Leader:** "<crimson/moon> + Court/Manor" (Crimson Moon Manor). Night raids, feudal pacts with vassals.
  Style: deepslate bricks, red wool.

#### 35. Demon lord (and the little demon)
- **Gender:** both.
- **Look:** horns, dark regal cape, a throne-room crown, clawed gauntlets; palette black/purple/gold. The "little
  demon" variant: small horns and a spade-tipped tail, mischievous.
- **Anim:** sits on an invisible throne idle; dark flames rise; laughs with a hand on the face.
- **Kits:** (a) *Dark sovereign*: blight and arcane `zone`s, summons. (b) *True form*: `empower` with size 110% and
  new attacks. (c) *Command*: `allies` `stat damage_dealt +20%`.
- **Personality:** arrogant, secretly kind, chuunibyou, tired administrator, theatrical villain.
- **Non-combat:** command (`rally`, `drill`), intimidation.
- **Boss:** the classic multi-phase ruler: phase 1 seated casting, phase 2 stands and fights, phase 3 true form.
  Tell: a monologue line before each phase. Punish: during the monologue (it is vain).
- **Leader:** "<dark word> + Dominion/Throne" (Nightfall Throne). Conquest, expansion, demands submission.
  Style: blackstone, purple wool.

#### 36. Angel and fallen angel
- **Gender:** both.
- **Look:** halo, feathered wings (white, or black and red for the fallen), robes or armour; palette white/gold or
  black/crimson.
- **Anim:** wings fold and spread; the halo spins faster when angry or cracks when fallen.
- **Kits:** (a) *Judgement*: `line` radiant beam. (b) *Grace*: `allies` `heal` + `regeneration`.
  (c) Fallen: blight `zone`s and `wither`.
- **Personality:** serene, strict, naive, rebellious (fallen), melancholic.
- **Non-combat:** healing (`mend`), guidance and scouting from above (`lookout`).
- **Boss:** phase 2 takes to the air [P:flight]; light pillars rain down. Tell: the halo flares. Punish: after the
  beam the wings droop.
- **Leader:** "Choir/Host of the <light>" (Choir of First Light). Strict and lawful; fallen: rebellious,
  breaks pacts. Style: white stone, yellow wool.

#### 37. Reaper (soul guide, death bureaucrat)
- **Gender:** both.
- **Look:** hooded cloak, scythe (axe), soul lanterns, skull motifs, an hourglass; palette black/bone/teal.
- **Anim:** the scythe twirls; soul flames flicker in the lantern; tilts the head.
- **Kits:** (a) *Harvest*: axe sweep that hits harder below 30% health (`if target_health_below 30`).
  (b) *Soul mark*: `mark "soul"` -> `on_mark_full` blight burst. (c) *Lantern*: `soul_fire_flame` `zone`.
- **Personality:** deadpan, bored civil servant, gentle guide, eerie.
- **Non-combat:** record keeping, butchery, graveyard keeping.
- **Boss:** execute pressure: the lower your health, the more dangerous it is. Tell: the scythe raised behind the
  head. Punish: the scythe's long follow-through.
- **Leader:** "<last/pale> + Lantern + Office" (Pale Lantern Office). Patient, waits for others to weaken, then
  strikes. Style: dark oak, black wool, soul lanterns.

#### 38. Necromancer (bone summoner)
- **Gender:** both.
- **Look:** dark robes, bone jewellery, a skull-topped staff, sunken eyes; palette grey/violet/green.
- **Anim:** bones rattle; the staff's eye socket glows; hunched casting.
- **Kits:** (a) *Raise*: allied skeletons or zombies [P:summon_types]. (b) *Wither bolt*: `bolt` blight +
  `wither`. (c) *Bone wall*: self `shield`.
- **Personality:** cold scholar, lonely misfit, cheerful about death, megalomaniac.
- **Non-combat:** bone meal for farms (`green_thumb`), anatomy and healing (`mend`).
- **Boss:** puppet fight: kill the summons or dive the summoner. Tell: a summon circle on the ground. Punish:
  after summoning it is drained for 1.5 s.
- **Leader:** monster-faction leader: "<bone/grave> + Choir/Court" (Hollow Bone Court). Grows by raising the
  dead, raids graves. Style: mossy cobble, purple wool.

#### 39. Beast tamer
- **Gender:** both.
- **Look:** fur trims, a whip or crook, feathers, animal companions riding on it; palette earthy.
- **Anim:** whistles; pets its animals; the companion copies its face.
- **Kits:** (a) *Wolf pack*: `summon wolf` x3. (b) *Golem friend*: `summon iron_golem`. (c) *Command*: `mark` a target
  that summons focus [P:summon_focus].
- **Personality:** cheerful, shy with people, wild child, gentle.
- **Non-combat:** animal care and breeding (animal carer), riding, hunting.
- **Boss:** fight the beasts or the tamer. Tell: a whistle (`entity.wolf.howl` from the beasts). Punish: re-calling
  beasts.
- **Leader:** "<beast> + Riders/Herd" (Horn and Hoof Riders). Herding economy. Style: spruce, brown wool.

#### 40. Dragoon (jumping lancer)
- **Gender:** both.
- **Look:** sleek armour with a dragon-motif helmet, a long lance (trident); palette deep blue/silver or red.
- **Anim:** crouches deep before a jump; spins the lance.
- **Kits:** (a) *High jump*: `leap` (range 10) with landing damage. (b) *Thrust*: trident `line`.
  (c) *Throw*: `shot` with `trident` look.
- **Personality:** stoic, proud, reckless, loyal.
- **Non-combat:** scouting from heights (`lookout`), mounted patrols.
- **Boss:** jumps out of sight and lands on a marker [P:landing_marker]. Tell: the deep crouch. Punish: the lance
  stuck after landing.
- **Leader:** "<sky/wyvern> + Lances" (Skyspear Lances). Fast strikes on distant targets. Style: stone, blue wool.

#### 41. Berserker
- **Gender:** both (often M; the tiny girl with a huge axe is its own popular sub-type).
- **Look:** huge axe or club, fur, war paint, scars, wild hair; palette red/brown.
- **Anim:** heavy breathing; eyes glow red in rage; laughs.
- **Kits:** (a) *Rage*: `on_hurt` -> `stat attack`, `max_stacks 5`. (b) *Frenzy*: `empower` with `skills_locked`.
  (c) *Cleave*: `cone` with `knockback`.
- **Personality:** battle maniac, gentle giant outside battle, grieving, hot-blooded.
- **Non-combat:** woodcutting, hauling, mining (`diligence`).
- **Boss:** gets stronger the more it is hit; burst it down or kite it. Tell: a roar before the frenzy. Punish:
  the frenzy ends in exhaustion.
- **Leader:** "<blood/axe> + Horde/Clan" (Redaxe Horde). Raids constantly. Style: spruce, red wool.

#### 42. Arbalist (crossbow gunslinger)
- **Gender:** both.
- **Look:** long coat, wide hat or goggles, one or two crossbows, ammunition belts; palette leather brown/brass.
- **Anim:** twirls the crossbow; reloads with a flourish; tips the hat.
- **Kits:** (a) *Fan the hammer*: crossbow (multishot, quick charge), `volley`. (b) *Piercing shot*: `line` with
  `piercing`. (c) *Trick shot*: a ricochet `chain`.
- **Personality:** cool drifter, cocky, bounty hunter, laid-back.
- **Non-combat:** hunting, crossbow upkeep (blacksmith), bounty scouting (`lookout`).
- **Boss:** a duel at range. Tell: a reload sound and a glint before the big shot. Punish: the reload.
- **Leader:** "<dust/brass> + Posse/Company" (Brass Posse). Mercenary, raids for bounties. Style: birch, brown wool.

#### 43. Corsair (pirate captain)
- **Gender:** both.
- **Look:** tricorne hat, long coat, cutlass (sword), sometimes a hook or an eyepatch; palette navy/red/gold.
- **Anim:** spins the cutlass; drinks from a flask; captain's laugh.
- **Kits:** (a) *Cutlass*: sword combo with `dash`. (b) *Cannon*: `shot` with `fire_charge` look and knockback
  (no block damage). (c) *Plunder*: `dispel` enemy buffs and gain `stat damage_dealt`.
- **Personality:** charismatic rogue, greedy, honourable thief, brash.
- **Non-combat:** fishing, trade (`haggler`), sailing and coastal scouting.
- **Boss:** loot-themed: it steals buffs. Tell: aiming the cannon. Punish: reloading it.
- **Leader:** "<colour/sea> + Corsairs/Crew" (Red Tide Corsairs). Raids for loot, coastal bases. Style: dark oak,
  blue wool.

#### 44. Bard and minstrel
- **Gender:** both.
- **Look:** lute, harp or flute, feathered cap, colourful clothes; palette bright.
- **Anim:** strums constantly; sways to the music; winks.
- **Kits:** (a) *Battle song*: `allies` `speed` + `strength`, `note` particles. (b) *Dissonance*: `cone`
  `weakness`. (c) *Ballad*: `allies` `regeneration`.
- **Personality:** charming flirt, cheerful, melancholic poet, show-off.
- **Non-combat:** morale [P:morale], diplomacy [P:diplomacy], trade.
- **Boss:** rhythm-based like the dancer, but it buffs its side; silence it first. Tell: the chord change.
  Punish: retuning.
- **Leader:** "<song> + Company/Troupe" (Lark Song Company). Diplomatic, trade, festivals. Style: oak, yellow wool.

#### 45. Alchemist and inventor
- **Gender:** both.
- **Look:** goggles, apron or lab coat, satchel of flasks, gadgets, a mechanical arm; palette brass/teal/white.
- **Anim:** pushes the goggles up; a flask bubbles; something explodes and its face is sooty.
- **Kits:** (a) *Bombs*: `shot` with `fire_charge` look, small `zone` (no block damage). (b) *Elixir*: `allies`
  `haste` + `strength`. (c) *Gadget*: a turret (snow golem as a stand-in) [P:summon_types].
- **Personality:** eccentric genius, cheerful, absent-minded, megane.
- **Non-combat:** smelting, brewing [P:brewing_job], enchanting, inventing tools (blacksmith).
- **Boss:** a gadget fight. Tell: the flask colour. Punish: refilling.
- **Leader:** "<brass/gear> + Workshop/Society" (Brassgear Society). Industry-first. Style: copper-coloured blocks,
  orange wool.

#### 46. Siren and merfolk (tide singer)
- **Gender:** both.
- **Look:** fin-like ears, scales at the wrists, a shell crown, flowing seaweed hair, trident; palette aqua/coral.
- **Anim:** hair drifts as if underwater; sings with eyes closed.
- **Kits:** (a) *Tide trident*: trident throws (`shot`, `trident` look). (b) *Song*: `cone` `slowness` +
  `pull`. (c) *Wave*: `nova` `knockback` with `splash`.
- **Personality:** serene, mischievous, lonely, proud.
- **Non-combat:** fishing (`angler`), water scouting.
- **Boss:** in and near water; the song pulls. Tell: the first note. Punish: breath between songs.
- **Leader:** "<tide/coral> + Choir/Court" (Coral Choir). Coastal, trades fish. Style: prismarine, cyan wool.

### E. Modern, pop and science-fiction archetypes

#### 47. Idol (stage singer)
- **Gender:** both (girl and boy idols are equally established).
- **Look:** stage outfit with frills, ribbons, sequins or a sharp jacket; headset microphone or a microphone
  stand used as a staff; star hair clips; glow sticks; palette pastel with neon accents, or one signature colour.
- **Anim:** waves to the crowd; poses with a wink; a hand heart; dances between attacks; sparkles on a smile.
- **Kits:** (a) *Live show*: `allies` `speed`/`regeneration`, `note` and `firework` particles; `every 10` -> a
  small `allies` `heal`. (b) *Encore*: `on_ally_low_health` -> `shield` on that ally. (c) *Feedback*: `cone`
  sonic damage (`sonic_boom` particle, small `knockback`).
- **Personality:** genki, professional perfectionist, shy off-stage and bold on-stage, narcissist, sweet deredere.
- **Non-combat:** morale and recruitment (`rally`), diplomacy and fame [P:diplomacy], trade (merchandise).
- **Boss:** a "concert": phase changes are song changes, each with its own pattern; the audience (allies) gets
  buffed while the idol keeps singing. Tell: the count-in ("one, two!"). Punish: the bow at the end of a song.
- **Leader:** "<star/heart> + Stage/Unit/Agency" (Starlit Stage Unit). Popularity politics: many pacts, recruits
  fast, avoids looking like the aggressor. Style: quartz-white, pink or light blue wool.

#### 48. Magical girl and transforming hero
- **Gender:** F as magical girl; M counterpart: the magical boy or the transforming armoured hero (both are
  established genre types). The "dark" variant (a rival with a costly contract) suits either.
- **Look:** two forms. Civilian: school uniform or casual. Transformed: frills, ribbons and bows, a wand, sceptre
  or charm, a mascot familiar; the hero variant wears a helmeted suit with a belt device. Palette per hero
  (pink/white, blue/silver, black/violet for the dark variant).
- **Anim:** the transformation sequence (a 1.5 s spin with light ribbons) is the phase-1 -> phase-2 transition;
  the mascot floats beside the shoulder and reacts.
- **Kits:** (a) *Wand beam*: `line` radiant or arcane. (b) *Ribbon bind*: `stun` + `pull`. (c) *Transformation*:
  `empower` with stats and a new basic attack, plus a model swap [P:model_swap].
- **Personality:** cheerful, earnest, clumsy, kind, tragic (dark variant), rival tsundere.
- **Non-combat:** morale [P:morale], healing (`mend`), protecting the town (`lookout`).
- **Boss:** fights untransformed first, transforms at 60%; the dark variant transforms again at 25% into a
  corrupted form. Tell: the charm glows before each spell. Punish: the pose after a finisher.
- **Leader:** "<star/heart/moon> + Guardians" (Heartlight Guardians). Protective, idealistic, high honour.
  Style: white stone, pink wool.

#### 49. Maid and butler (battle servant)
- **Gender:** maid F, butler M (the female butler in a suit is a known variant).
- **Look:** maid: long or short black dress, white apron, headband, sometimes cat or rabbit ears added; butler:
  tailcoat, white gloves, monocle; hidden weapons: knives in the apron, a sword in an umbrella, a mop or broom
  handle (trident or sword); palette black/white plus one accent.
- **Anim:** a curtsy or bow before fighting; adjusts gloves; cleans dust off the master after combat.
- **Kits:** (a) *Hidden knives*: `volley` of thrown knives [P:projectile_look] with `mark`. (b) *Bodyguard*:
  `on_ally_hurt` -> `taunt` that attacker and `shield` the ally (vanguard). (c) *Tea time*: `allies`
  `regeneration` + `cleanse`.
- **Personality:** perfect and cold, clumsy but devoted, cheerful, overprotective, secretly dangerous.
- **Non-combat:** housekeeping and logistics (hauler, `diligence`), cooking (chef), managing stores (`thrifty`).
- **Boss:** precise and polite: it announces each attack ("excuse me"). Tell: the bow. Phase 2: the gloves come
  off. Punish: it straightens its clothes after each combo.
- **Leader:** if it leads, it serves an absent master or the faction itself: "The <estate> Household"
  (The Larkspur Household). Orderly, efficient economy, defensive. Style: stone bricks, white/black wool.

#### 50. Noble fencer (young lady or young lord)
- **Gender:** both (the rich young lady with an exaggerated laugh and the pampered young lord).
- **Look:** elaborate curls or ringlets, a ribbon or a feathered hat, ruffled collar, gloves, a rapier (sword),
  a folding fan; palette gold/white/rose or royal blue.
- **Anim:** the high-pitched three-stage laugh with a hand at the mouth; flicks hair; points the rapier.
- **Kits:** (a) *Fencer*: precise single hits, `dash` lunge, `stat crit_damage`. (b) *Riposte*: counter stance
  [P:counter]. (c) *Retainers*: `allies` `stat` buff, a bodyguard summon [P:summon_npc].
- **Personality:** proud noble, tsundere, sheltered and naive, competitive rival.
- **Non-combat:** trade and wealth (`haggler`), diplomacy [P:diplomacy], patronage (`rally`).
- **Boss:** a duel of honour with a counter stance; it loses its composure in phase 2 (hair messed up, faster,
  sloppier). Tell: the rapier salute. Punish: the laugh.
- **Leader:** "House <surname>" with an original surname (House Valcrest). Wealthy, trade-heavy, proud, insulted
  easily (raids those who refuse its pacts). Style: quartz-white, gold trim, yellow wool.

#### 51. Android and automaton
- **Gender:** both.
- **Look:** a human-like face with seams at the joints, glowing eye rings, mechanical limbs or exposed panels,
  a cable ponytail or headphones; palette white/cyan/black, or brass for a clockwork automaton.
- **Anim:** a mechanical blink (shutter-like) and a status light; head-tilt "processing"; a glitch flicker when
  hurt.
- **Kits:** (a) *Arm cannon*: `bolt` shock with `electric_spark`. (b) *Targeting*: `mark "lock"` -> a `line`
  beam on the marked target. (c) *Overclock*: `empower` with `attack_speed` and `speed`, `skills_locked` until it
  cools down [P:overheat].
- **Personality:** emotionless (kuudere), learning emotions, cheerful robot, cold logic, loyal.
- **Non-combat:** tireless labour (`diligence`), mining (`prospector`), building (`mason`), calculation and
  logistics.
- **Boss:** analysis: it adapts to the player's habits [P:adaptive]. Phase 2 sheds armour plates (faster, more
  fragile). Tell: the eye light turns red. Punish: the cooling vents after overclock.
- **Leader:** "<unit/code> + Directive/Foundry" (Foundry Directive Nine). Efficient, maximises production, cold
  pacts. Style: smooth stone, iron blocks trim, light grey wool.

#### 52. Tactician and scholar (glasses-wearing strategist)
- **Gender:** both.
- **Look:** glasses (which flash white when it has a plan), a book or a pointer, scholar's robes or a military
  coat; palette navy/grey/ink.
- **Anim:** pushes the glasses up; the lenses glint; flips through pages.
- **Kits:** (a) *Battle plan*: `allies` `stat damage_dealt +` and `cooldown_cut` to allies [P:ally_cooldown].
  (b) *Exploit weakness*: `mark` -> allies deal more damage to it. (c) *Trap*: delayed `zone` with `stun`.
- **Personality:** calm genius, arrogant know-it-all, anxious, kind teacher.
- **Non-combat:** scholarship (enchanter XP), training (`drill`), logistics and planning.
- **Boss:** it fights badly alone but makes its side much stronger; isolate it. Tell: the glasses flash.
  Punish: while it consults the book.
- **Leader:** "<ink/quill> + Academy/Council" (Quill Council). Smart raids, prefers pacts, invests in workers.
  Style: bookshelves, oak, blue wool.

#### 53. Smith-warrior (forge hammer)
- **Gender:** both.
- **Look:** sturdy, short or broad build; leather apron, sooty arms, braided beard or braided hair, a forge hammer
  (mace), tongs; palette brown/orange/iron grey.
- **Anim:** hammer resting on the shoulder; wipes sweat; sparks fly from each hit (`scrape`/`crit`).
- **Kits:** (a) *Hammer*: mace smash with `stun`. (b) *Forge heat*: `ignite` on hit. (c) *Field repair*: `allies`
  `stat armor +20%`.
- **Personality:** gruff, proud of the craft, jovial, stubborn.
- **Non-combat:** smithing and repair (blacksmith), smelting, mining (`prospector`).
- **Boss:** slow and heavy, sparks are the tell. Punish: reheating the hammer.
- **Leader:** "<anvil/ember> + Hold/Forge" (Anvil Hold). Industry, trades tools and arms. Style: stone, deepslate,
  orange wool.

#### 54. Grappler (wrestler, sumo, pro wrestler)
- **Gender:** both (often M; female wrestlers are an established type).
- **Look:** large silhouette, a ceremonial apron belt or a wrestling mask and cape; palette bold primary colours.
- **Anim:** stomps and claps before the fight; flexes; arms spread wide in the grab stance.
- **Kits:** (a) *Grab*: `pull 3` + 1.5 s `stun` [P:grab for a real throw]. (b) *Body slam*: `leap` with a large
  landing radius. (c) *Iron stance*: `knockback_resistance` + `taunt`.
- **Personality:** jovial, honourable, showman, hot-blooded.
- **Non-combat:** hauling (`diligence`), training (`drill`), festivals [P:morale].
- **Boss:** stay out of arm's reach. Tell: the arms spread. Punish: a missed grab leaves it stumbling.
- **Leader:** "<mountain/thunder> + Stable/Ring" (Great Mountain Stable). Challenges rivals; fair fights only.
  Style: oak, red wool.

---

## Part 5. Personality tropes and what they change

Personality is the second half of a hero's identity. It drives **fight AI** (v1 `behaviour`: `aggression`
cautious/steady/bold, `retreat_below`, `focus`, `protects`, `raids`), **voice** (v1 `lines`), **face** (expression
set for the animated model) and, for a faction leader, **decisions**. Lines below are original examples of tone.

Leader decision knobs used below: **Raids** (how often and whom it attacks), **Pacts** (willingness to ally),
**Honour** (keeps pacts, spares beaten factions), **Economy** (which jobs it favours), **Growth** (recruiting and
expanding), **Defence** (garrison and walls). Several need new campaign hooks [P:leader_biases].

| Trope | Reads as | Fight AI | Voice sample | As leader | Face cues |
|---|---|---|---|---|---|
| Tsundere | prickly outside, caring inside | bold, retreat 25, focus `heroes_first` (proving itself) | "Don't misunderstand. I saved you because you owe me." | refuses pacts at first, accepts after repeated offers; raids whoever insulted it | pout, blush, looks away |
| Kuudere | cool, composed, rare warmth | steady, retreat 30, `weakest` (efficient) | "Noted. You will not get a second chance." | rational pacts, measured raids, high honour | still face, slow blink, tiny smile |
| Dandere | shy and quiet, opens up to trusted people | cautious, retreat 40, protects `allies` | "I... I'll hold them. Please stay behind me." | defensive, seeks protective pacts | averted eyes, fidgeting |
| Deredere | openly sweet and affectionate | steady, support-leaning | "Everyone made it? Then let's all go home!" | many pacts, generous trade | big smiles, frequent blinks |
| Yandere | sweet surface, possessive, dangerous | bold, never retreats while an ally it favours is hurt; focus whoever hurt them | "You touched what is mine. Stay still." | fiercely protective, vengeful raids, jealous of its allies' other pacts | smile with shadowed eyes, pupils shrink |
| Genki | boundless energy | bold, retreat 20, `nearest` | "A fight? Yes! Let's go, let's go!" | expands and recruits fast, impulsive raids | wide eyes, open grin, bouncing |
| Stoic | unshakeable, few words | steady, retreat 15, `strongest` | "..." / "Enough." | steady, keeps its word | almost no expression; eyes do the acting |
| Hot-blooded | passionate, shouts its moves | bold, retreat 10, `heroes_first` | "I'm not done yet! Not even close!" | raids rivals, accepts every challenge | gritted teeth, burning eyes |
| Lone wolf | aloof, works alone | steady, protects `none` | "Stay out of my way." | isolationist, few pacts | half-lidded, turns away |
| Proud noble | elegant, haughty, generous to inferiors | steady, `heroes_first` | "You may consider it an honour to fall to me." | trade and prestige; easily insulted | raised chin, closed-eye laugh |
| Delinquent | rough, territorial, loyal | bold, `nearest` | "This is our turf. Get lost." | territorial raids, respects whoever beats it | glare, chewing, smirk |
| Chuunibyou | theatrical self-made legend | bold, opens with its ultimate | "Tremble! The sealed power awakens!" | grand names, erratic but brave | covers one eye, dramatic poses |
| Airhead | spacey, kind, accidentally lucky | steady, forgets to retreat | "Oh? Were we fighting?" | random choices that often work out [P:luck] | blank smile, tilted head |
| Big sister / big brother | caring, teasing mentor | protects `allies`, retreat 30 | "Leave this to your big sister." | recruits and trains (`rally`, `drill`), protective pacts | warm smile, wink |
| Trickster / little devil | teasing, playful, sly | skirmisher, `weakest` | "Too slow! Over here~" | sabotage, deception, low honour | grin with a fang, tongue out |
| Sleepy / lazy | low energy, frightening when serious | cautious until hurt, then bold | "Five more minutes... fine." | few raids, very efficient economy | droopy eyes, yawns, sudden sharp stare |
| Battle maniac | lives for a strong opponent | bold, retreat 0 | "Finally, someone worth it!" | constant raids, seeks the strongest | wide manic grin |
| Gentle giant | kind and strong | steady, protects `workers` | "Please don't make me hurt you." | defensive, helps neighbours | soft eyes, sad frown when forced |
| Schemer | calculating, manipulative | cautious, `ranged_first` | "You moved exactly where I needed you." | pacts it intends to break, ambushes | narrow eyes, glasses glint |
| Righteous hero | justice first | steady, protects `allies`, `strongest` | "I won't let you hurt anyone else!" | attacks aggressors, shields the weak | determined brow |
| Melancholic | haunted, tragic | steady, low retreat | "I have lost enough already." | reclusive; one long grudge [P:grudge] | downcast eyes, slow blink |
| Glutton | food-motivated | retreats to eat, then returns | "Wait, let me finish my snack first." | food economy, trades for food | puffed cheeks, drool |
| Strict perfectionist | demanding, disciplined | steady | "Sloppy. Again." | heavy drills, efficiency, punishes failures | frown, adjusts clothing |
| Flirt / charmer | playful, smooth | skirmisher | "Shame to fight someone so lovely." | diplomacy first | wink, smirk |
| Ancient in a young body | sage, patient, old-fashioned speech | cautious, support | "Child, I was old when your forests were seeds." | patient long-term plans | knowing smile |
| Coward who rises | fearful but brave in a crisis | cautious, retreat 50; `on_ally_low_health` -> `empower` | "I'm scared... but I'm not running!" | avoids war, seeks pacts, fights hard when cornered | trembling, then resolute eyes |
| Gruff veteran | grumpy mentor | steady, `threat_to_allies` | "Kids these days. Watch and learn." | defence and drills | scowl, scar twitch |
| Eccentric genius | brilliant and scattered | cautious, gadgets | "Fascinating! Hit me again, I need data." | invests in industry and research | wild eyes, soot |

A hero can combine a main trope with a "gap" trope (a stoic who loves cute things, a delinquent who is a great
cook): the gap gives the idle animations and lines their charm.

---

## Part 6. Mechanics the archetypes ask for that v1 lacks (proposals for the v2 format)

Grouped; each tag appears in Part 4. These are research findings, not decisions.

**Combat mechanics.**
- [P:roles] `controller` and `summoner` presets.
- [P:combo] multi-hit chains with their own animations (three-hit sword string, flurry).
- [P:counter] a guard stance that answers the next melee hit with a riposte; [P:stance] switch between two
  movesets; [P:tempo] stacks that grow while a hero keeps attacking on rhythm; [P:overheat] a power state that ends
  in a forced cooldown.
- [P:grab] a real grab-and-throw (a pull plus a short throw arc, never into fall damage beyond the v1 rules).
- [P:anti_air] a hit that only triggers on an airborne or leaping enemy.
- [P:interruptible_channel] a channel (heal, big spell) cancelled by enough damage or a stun.
- [P:break_shield] a barrier that blocks damage until it has taken N hits.
- [P:weak_point] extra effect when hit in a spot (head talisman) - simplest form: a hit from above or from behind.
- [P:item_weakness], [P:etiquette_weakness] folklore weaknesses triggered by a thrown item or a player action
  (snowball hit, crouching in front of it).
- [P:decoy] a visual decoy that absorbs one hit and pops; [P:disguise] standing still as an object; [P:charm]
  enemy NPCs briefly fight each other.
- [P:element_cycle] the element changes with each skill; [P:rebirth] a once-per-fight revival as the final phase;
  [P:once_per_fight] limit for passives; [P:adaptive] counters the player's most-used attack.
- [P:ally_projectile], [P:ally_zone], [P:ally_cooldown] friendly versions of shot, zone and cooldown_cut.
- [P:glide], [P:flight], [P:wall_climb] movement styles (player-like: no teleport, still physics-bound).
- [P:landing_marker] a ground marker where a leap will land (the tell for jumps).

**Conditions.** [P:time_condition] day/night, [P:moon_condition] moon phase, [P:weather] rain or thunder (and
abilities that need it), [P:biome_condition] snowy, ocean, forest, mountain, [P:near_home] within the hero's base,
[P:sneak_stealth] sneaking targets are detected later.

**Summons and visuals.** [P:summon_types] more vanilla creatures as allies (cat, fox, bee, parrot, bat, allay-like
helper, skeleton, zombie, vex-like spirit) with the same leash and timer rules; [P:summon_skin] summons drawn as
paper beasts, spirits or gadgets; [P:summon_npc] temporary allied NPC soldiers; [P:summon_focus] summons attack the
hero's marked target; [P:summon_link] the hero staggers when its summons die; [P:projectile_look] custom projectile
models (thrown stars, knives, talisman cards, flying swords); [P:model_swap] a transformed model in a phase;
[P:bossbar] a boss bar when a hero fights players.

**Campaign and leadership.** [P:leader_biases] faction decisions read from the leader's personality;
[P:diplomacy] a talent that raises pact acceptance and pact length; [P:morale] a base-wide work or regeneration
bonus from festivals, music and ceremonies; [P:tribute] vassal pacts that pay resources; [P:declaration] honourable
leaders announce war before raiding; [P:paid_pact] alliances bought with goods; [P:respect_pact] a beaten leader
offers a pact to the victor; [P:pact_patience] repeated offers raise acceptance; [P:grudge] a lasting enemy
faction; [P:scout_bonus], [P:sabotage] espionage talents; [P:undead_recruits] the monster faction recruits
differently; [P:luck] small random good outcomes.

**Jobs.** [P:brewing_job] a brewer (potions for the base); [P:job] tailoring/armour crafting, cold storage;
[P:night_shift] workers that keep working at night.

---

## Part 7. Non-combat talents and where they plug in

| Talent | Archetypes it suits | Existing hook (v1 / the mod) | Proposed hook |
|---|---|---|---|
| Cooking | battle chef, moon rabbit, tanuki, maid, wanderer | chef duty; `aura thrifty`; `rally` (cheaper recruits) | food quality buffs [P] |
| Smithing and repair | smith-warrior, oni, western dragonkin, alchemist | blacksmith duty (anvil repair) | better repair, tool tiers [P] |
| Smelting | phoenix-kin, dragonkin, smith | smelter duty | faster smelting aura [P] |
| Mining | oni, android, smith, jiangshi | miner duty; `aura prospector`, `diligence` | - |
| Building | komainu, warrior monk, oni, android | builder duty; `aura mason` | - |
| Farming | dryad, kitsune, moon rabbit, kappa, eastern dragon | farmer duty; `aura green_thumb`, `regrowth` | - |
| Forestry | elf ranger, dryad, berserker | woodcutter duty; `aura diligence` | - |
| Fishing | nekomimi, kappa, siren, corsair | fisherman duty; `aura angler` | - |
| Animal care | beast tamer, wolf-kin | animal carer and butcher duties | - |
| Healing | miko, priest/nun, medic, paladin, dryad | `mend` base ability | - |
| Scouting and watch | ninja, elf, tengu, wolf-kin, dragoon | scout duty; `lookout`; pathfinding affix | espionage reports [P:scout_bonus] |
| Trade | tanuki, kitsune, corsair, noble fencer | trader duty; `aura haggler` | - |
| Scholarship | onmyōji, tactician, witch, alchemist | enchanter duty; `drill` XP | research [P] |
| Leadership | samurai, knight, demon lord, delinquent, idol | `rally`, `drill` | leader biases [P:leader_biases] |
| Logistics | maid/butler, android | hauler duty; `aura diligence`, `hardy` | - |
| Music, dance, festivals | idol, bard, fan dancer, storm drummer | none | morale [P:morale] |
| Diplomacy | kitsune, bard, idol, noble fencer, tactician | none (pacts exist) | [P:diplomacy] |
| Brewing | witch, alchemist, tanuki | none | [P:brewing_job] |
| Tailoring | spider-weaver, maid | none | [P:job] |

---

## Part 8. Reaching 200 heroes, half male and half female

**Every archetype above can be built in both genders.** The female-coded ones have established male
counterparts (miko -> shrine priest, fan dancer -> stage dancer, magical girl -> magical boy or transforming armoured
hero, maid -> butler), and the male-coded ones have established female versions (lady samurai, swordswoman
wanderer, female wrestler, shield-maiden berserker).

| Coding in media | Archetypes |
|---|---|
| F-coded, male counterpart exists | 15 miko, 21 fan dancer, 48 magical girl, 49 maid |
| Often F, both common | 1 nekomimi, 2 kitsune, 5 moon rabbit, 8 yuki-onna, 10 spider-weaver, 27 jiangshi |
| Often M, both common | 3 wolf-kin, 7 tengu, 13 komainu, 16 onmyōji, 18 quick-draw wanderer, 20 warrior monk, 41 berserker, 54 grappler |
| Neutral | the other 36 |

**Suggested allocation.** 54 archetypes x about 3.7 heroes = 200. Popular archetypes (nekomimi, kitsune, oni,
miko/priest, samurai, ninja, knight, witch, vampire, both dragonkin, elf, idol, maid/butler) get 5-6 heroes each
(roughly 3 F + 3 M); niche ones get 2-3. Balance the gender count per archetype so the F-coded and M-coded columns
do not skew the total: e.g. the four F-coded archetypes get at least 2 male heroes each, the eight M-coded ones at
least 2 female heroes each.

**Hybrids multiply variety.** An animal or yokai trait (Part 4 A) layered on an occupation (B-E): a fox-eared shrine
maiden, an oni samurai, a rabbit idol, a wolf-eared knight, a cat-eared ninja, a tengu warrior monk, a vampire
idol, an android butler. Avoid combinations that are the exact premise of a well-known work.

**Uniqueness rules for the 200.**
1. No two heroes share the same (archetype, element, mechanical weapon, signature mechanic).
2. Each hero has one *signature mechanic* (from Part 4 kits or the Part 6 proposals) that no other hero has in the
   same form, and one signature tell (pose + sound + particle colour).
3. Each hero has a distinct **head silhouette** element: cat/fox/wolf/rabbit ears, single/twin/antler/curved horns,
   halo, witch hat, straw hat, tricorne, court cap, chef's hat, crown, hood, mask, helmet crest, wings, tail type,
   twin tails, ringlet curls, side ponytail, braid crown...
4. Each hero has a palette pair (main + accent) not used by another hero of the same archetype.
5. Names, factions and backstories are original; run every name past a search for existing characters.

**Variation axes available:** 54 archetypes x 8 elements x 7 vanilla weapons x 7 team roles x 8 duel styles x
28 personalities, plus palette, silhouette and signature mechanic. That is far more than 200 distinct heroes.

**Leaders.** Each faction starts with one random hero as its leader. The leader's archetype gives the faction its
naming pattern and base style (Part 4 "Leader" lines); its personality gives the decision biases (Part 5). Leaders
should come from heroes with a full "Leader" profile; monster-faction leaders fit the jiangshi, necromancer, reaper,
demon lord, vampire and spider-weaver archetypes.

---

## Part 9. Sources (generic trope and archetype references)

Consulted through web search (summaries); direct fetches of Wikipedia and TV Tropes pages were blocked in this
session. Only generic archetype pages are listed.

Archetypes and personality types
- Common anime character archetypes: https://whatnerd.com/most-common-anime-character-archetypes-explaine
- Dere types (tsundere, kuudere, dandere, yandere, deredere): https://www.siliconera.com/all-dating-sim-types-explained/
- Dere types overview: https://thesmartlocal.jp/types-of-dere/amp
- Animal-eared characters (kemonomimi): https://tropedia.fandom.com/wiki/Little_Bit_Beastly
- Stock shonen hero and rival: https://tvtropes.org/pmwiki/pmwiki.php/Main/StockShonenHero,
  https://tvtropes.org/pmwiki/pmwiki.php/Main/StockShonenRival
- Noble and roguish male pairing: https://tropedia.fandom.com/wiki/Noble_Male,_Roguish_Male
- Rich young lady (ojou): https://tvtropes.org/pmwiki/pmwiki.php/Main/OJou
- Chuunibyou: https://tvtropes.org/pmwiki/pmwiki.php/Main/Chuunibyou
- Japanese delinquents and sukeban: https://tvtropes.org/pmwiki/pmwiki.php/Main/JapaneseDelinquent,
  https://www.japanesewithanime.com/2021/04/sukeban.html
- Battle butler and combat maid: https://tvtropes.org/pmwiki/pmwiki.php/Main/BattleButler,
  https://tvtropes.org/pmwiki/pmwiki.php/Main/NinjaMaid
- Ninja: https://tvtropes.org/pmwiki/pmwiki.php/Main/Ninja
- Quick-draw swordsman: https://tvtropes.org/pmwiki/pmwiki.php/Main/IaiJutsuPractitioner
- Idol singer: https://tvtropes.org/pmwiki/pmwiki.php/Main/IdolSinger,
  https://tvtropes.org/pmwiki/pmwiki.php/Main/MagicIdolSinger
- Magical girl: https://www.fanlore.org/wiki/Magical_Girl, https://tvtropes.org/pmwiki/pmwiki.php/Main/FrillsOfJustice
- Demon king: https://tvtropes.org/pmwiki/pmwiki.php/Main/MaouTheDemonKing

Folklore and occupations
- Shrine maiden: https://tvtropes.org/pmwiki/pmwiki.php/Main/Miko,
  https://www.japan-experience.com/plan-your-trip/to-know/understanding-japan/miko-shrine-maidens-japans-traditional-shinto-priestesses
- Paper talismans: https://allthetropes.fandom.com/wiki/Ofuda
- Onmyōdō and shikigami: https://tvtropes.org/pmwiki/pmwiki.php/UsefulNotes/Onmyodo, https://fanlore.org/wiki/Onmyouji_(trope)
- Kitsune: https://tropedia.fandom.com/wiki/Kitsune
- Oni and setsubun: https://aboutjapan.japansociety.org/setsubun
- Tengu: https://www.japan-experience.com/plan-your-trip/to-know/understanding-japan/tengu-demon-japan,
  https://web-japan.org/kidsweb/folk/spooky/tengu/tengu01.html
- Yokai overview (kappa, yuki-onna, nekomata, bakeneko): https://nihongomaster.com/blog/20-well-known-yokai-in-japanese-folklore,
  https://tvtropes.org/pmwiki/pmwiki.php/Main/Yokai
- Tanuki: https://tofugu.com/japan/tanuki
- Jiangshi: https://tropedia.fandom.com/wiki/Chinese_Vampire,
  https://uhlibraries.pressbooks.pub/chin3343fa23/chapter/hoppingvampire/

Roles, fighting styles and boss design
- Standard RPG combat roles: https://allthetropes.org/wiki/Standard_RPG_Combat_Roles
- Party tank: https://www.tvtropes.org/pmwiki/pmwiki.php/Main/PartyTank
- Fighting-game archetypes: https://fraytools.fandom.com/wiki/Character_Archetypes,
  https://compete.playstation.com/en-us/article/six-less-common-fighting-game-character-archetypes-guide,
  https://geekchamp.com/choose-a-fighting-game-archetype-shoto-rushdown-grappler-or-zoner/
- Telegraphing: https://gamedeveloper.com/design/enemy-attacks-and-telegraphing
- Boss phases: https://molbv.github.io/blog/2025-07-08-boss-phases.html,
  https://choostgames.com/blog/what-makes-a-good-boss-fight/
- Fair fake-outs: https://www.wayline.io/blog/the-art-of-the-fakeout-combat-design
- Shape language and readability: https://rocketbrush.com/blog/shape-language-in-game-character-design-how-to-make-characters-readable-and-consistent,
  https://bugnet.io/blog/character-design-basics-for-indie-devs
