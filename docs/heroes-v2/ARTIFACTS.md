# Heroes v2: artifacts

Named items that only work for heroes, built up from vanilla materials or found in the world's dangerous places.

Status: design, 2026-11-18, reviewed the same day (section 21), revised the same day to the owner's answers (the
note below; section 20) and the revision reviewed again the same day (21.2). Nothing here is implemented. Numbers
marked PROPOSED go to BALANCE.md when they are built.
Companions: `HEROES_V2.md` (the hero design; section 7.5 points here and section 15.7 holds the tasks of section 19
below), `BOSS_HEROES.md` (the Wither and Ender Dragon heroes, whose two signature artifacts use the format of 16.4),
`MOBILITY_AND_WORK.md` (stamina and work lines), `SOUND.md` (the sound set of 12.6) and
`kits/artifacts_overview.txt` (the owner-facing overview, written from this file).

**Revision of 2026-11-18** (the owner's answers to section 20, `HANDOFF.md` section 2, request 11):
- **Slots open with levels; strength is fixed.** A hero has one artifact slot at level 1 and one more every five
  levels, five from level 20 (3). An artifact gives the same numbers on any hero: the first design's attunement
  (60% of an artifact's lines at level 1, full at level 20) is gone (4.2). Budgets, caps, the war rating (its cap now
  0.40) and the paper balance are re-derived for it (6, 13, 14).
- **Each hero decides.** The faction planner that handed out builds is gone: each hero chooses for itself to wear an
  artifact, swap it for one it wears, or leave it for someone else (pass it on, ask for one, refuse one), by its own
  personality, temperament, role, kit and relationships; the leader's old distribution policy is now a wish the heroes
  weigh, not an order (10). A member player offers and asks like a hero; the old pins are gone (10.10).
- **Tinkering.** A new ordinary NPC, the artificer, grows out of the blacksmith; heroes tinker through the artificer
  duty and a new `tinkerer` talent; a new block, the Artificer's Bench, raises an artifact to upgrade level II or III
  for real materials, experience levels and time, never failing (9.6-9.8). An artifact never levels with its wearer.
- **Growth by design.** Eight new growing artifacts get stronger from what their wearer does: blood drawn, ore broken,
  nights on watch, distance flown, undead laid to rest, worthy foes outlasted, fights survived at the edge, living
  things tended (7.8).
- **Tech levels.** ROADMAP phase 6's research gates upgrade level III, every upgrade of rare finds and three new
  engineered artifacts; until phase 6 exists those stay locked, and nothing else waits for it (9.5, 7.9).
- Every id of the first catalogue is kept (the art workflow's icons use them); 11 ids are new (7.8, 7.9) and get their
  icons later as data. Format, config, tests and tasks follow (16-19).
- **The signatures** (from `BOSS_HEROES.md`'s revision the same day): each takes one of the two new ways to grow
  stronger, Hollow Star its growth and Spirecore a tinkerer's upgrades with Artifice 2, and one held by an ally of its
  hero counts as lent by her (7.5, 10.4, 16.4).

Reading rules: the hero design's own rules hold here unless this file says otherwise. In particular the owner's
decisions of `HEROES_V2.md` section 6 (one combat mode, equal treatment, no attack tokens, no yield), the 10-minute
revival with its 30 s floor (3.4), "every ability from level 1, power growing to full at level 20" (section 8; it
concerns a hero's abilities: artifacts follow the owner's own answer of 2026-11-18 instead, slots by level and fixed
strength), the one-body rule (3.6) and "ordinary NPCs stay player-like" all bind every artifact.

---

## 0. The request and the decisions

The owner's words (2026-11-18, `HANDOFF.md` section 2, request 10): "artifacts, these are items that are only effective
to heroes, artifacts are named items and each can enhance something, think of something like the items from league of
legends for example, normal npcs can have these artifacts in their inventory and transport them around but it has no
effect on them. If a hero dies, their artifacts are dropped." And for the two boss heroes: "they come with an unique
artifact that cant be crafted and are best suited for them, but they are also dropped if they die."

Then, answering section 20 (request 11): "1 Heroes may have more slots as they level up but artifact strenght doesnt
change 2 heroes decide if they want to use, replace, or leave for someone else the artifact"; "Also for artifacts, some
npcs or hero with can tinker with artefacts may upgrade the artifact, but the artifacts doesnt level up with the hero,
though an artifact may become stronger of its in the artifact design, like an artifact that needs blood to get stronger
for example"; "Also some artifacts may nees tech level to be built and/or upgraded".

What we take from a MOBA's item system is its **shape**, not its content: items with a few stat lines, one named unique
passive or active per item, build paths from cheap components to finished items, unique passives that do not stack,
items that suit roles, and a build that a player can read at a glance. Every name, look and effect below is our own,
made from Minecraft materials, the mod's factions and the hero archetypes and elements. A clearance pass (as
`ROSTER_PLAN.md` section 8 does for heroes) checks the names before they ship.

Decisions (PROPOSED unless the owner's words decide them; section 20):

1. **An artifact is an ordinary item** with its own id (`factionclash:kiln_heart`), stack size 1, fire-proof, never
   despawning once dropped, with a 16x16 icon and a tooltip in its tier's colour (section 1). Sixty-one of them ship,
   in five tiers: 12 components, 28 advanced (8 of them growing), 13 greater (3 of them engineered), 6 rare finds and
   2 signature artifacts (section 7).
2. **Only a hero wearing it in an artifact slot gets anything from it.** Anyone may carry, store, haul, drop, pick up
   and trade it hand to hand: a player, a soldier, a hauler, a hero's own backpack, a chest, a hopper. None of them gets
   any effect, and none of them feeds a growing artifact. Players and ordinary NPCs are treated exactly alike: neither
   can wear one (section 2).
3. **Slots open with the hero's level** (the owner's answer 1): one at level 1, two at 5, three at 10, four at 15 and
   five from level 20, whatever its rarity, role or whether it leads (section 3).
4. **Fixed strength** (the owner's answer 1): an artifact's numbers are its own, the same on every hero at every
   level. Only its upgrade level (9.8) and, for a growing artifact, its growth (7.8) change them, and both stay with the
   item (4.2).
5. **A fixed stat vocabulary** of 19 lines that map onto stats heroes already have (attack, health, armour, ability
   power, speed, energy, poise, element damage and resistance...) plus a few new ones (recharge, drain, heal power,
   resolve, stamina, work). Each line has a **cap for the whole build**, so artifacts can never stack one stat past
   it, and every existing cap of the boss rules still holds on top (section 4).
6. **Nothing an artifact does touches the boss rules that make a fight fair**: the 12% per-hit cap, the telegraph
   minimums, the opening gap, the phase gaps, the hurt cooldown per attacker, the stun limits and immunity, the
   `heroes.maxHit` ceiling, and "no code path cancels lethal damage". No upgrade or growth changes that (section 4.4).
7. **One named passive or active per artifact** (components have none), written with format 2's own triggers, forms,
   effects and conditions, held to the passive and skill caps and floors; the few small engine additions are listed in
   5.3. Passives of one **family** never stack (section 8).
8. **Crafted the player-like way**: components at a crafting table, finished artifacts at a smithing table with an
   Artificer's Sigil as the template, then **woken** at an anvil with experience levels and lapis. The faction's
   blacksmith does all three in its workshop until the faction has an artificer, who then takes them over (section 9).
   Rare finds come only from vanilla structures and bosses (and Hollow lairs); signatures come only with their hero.
9. **Tinkering** (the owner's remark): the artificer, a hero at the artificer duty or a player raises an artifact to
   upgrade level II or III at the Artificer's Bench: its lines x1.25 at II and x1.5 at III (within every cap), its
   passive tuned at III, for real vanilla materials, experience levels and time; an upgrade never fails (9.6-9.8).
10. **Growth by design** (the owner's remark): eight growing artifacts get stronger from what their wearer does, in up
    to five steps; tinkering widens how far they can grow; their growth stays with the item through drops, theft and
    upgrades (7.8).
11. **Tech levels** (the owner's remark): ROADMAP phase 6's research, in one line called Artifice, gates upgrade level
    III, the upgrades of rare finds and the three engineered artifacts. Nothing vanilla and nothing else here is gated;
    until phase 6 the gated steps stay locked (9.5).
12. **Each hero decides** (the owner's answer 2): to use an artifact, swap it in, or leave it for someone else (pass it
    on, ask for one, refuse one), by its personality, temperament, role, kit and relationships; the leader's wishes are
    requests each hero weighs; the workshop makes what the heroes wish for, in the leader's order. Heroes equip and swap
    at a base out of a fight, never mid-fight; haulers carry artifacts between bases; winning sides loot the fallen's
    artifacts (section 10).
13. **A hero that dies drops every artifact it wears** where it died, as a player drops an inventory. The drops never
    despawn and survive fire, lava, explosions and cactus; only the void and commands destroy them. Its own faction can
    recover them, an enemy can steal them, a player can pick them up (section 11). An escaping hero keeps them.
14. **War power counts artifacts**: a hero's power is multiplied by 1 + its artifact rating (at most +40%), and rivals
    know a hero's artifacts only from what their scouts saw (section 13).
15. **Players see them** as small coloured pips on the boss bar and in full on the hero sheet; the owner's kit sheets
    get an "Artifacts" section (section 12).
16. **The two signature artifacts** (the Wither's and the Ender Dragon's) use a signature tier with a **bond**: base
    lines anyone may use, plus a bond (extra lines, a stronger passive and up to two changes to her own moves) only for
    the hero it belongs to. One copy exists per world; if it is destroyed it re-forms with its hero. Each takes one of
    the two ways an artifact may get stronger, never both: the Wither's Hollow Star grows by its own design and is
    never upgraded; the Ender Dragon's Spirecore is upgraded at the bench with Artifice 2 research and never grows
    (`BOSS_HEROES.md`'s revision of 2026-11-18). `BOSS_HEROES.md` designs their content (section 7.5, 16.4).

**Improvements added on our own** (the owner welcomes them; each is said here so it can be refused):
- work and leadership artifacts, so artifacts matter in peace too (Prospector's Loupe, Steward's Seal, and three of the
  growing ones: Veinseeker Geode, Hearthroot Seed, Vigil Lantern);
- a death-related artifact, the Chorus-Bloom Pendant, whose wearer returns 30% sooner (the owner allows death-related
  abilities to change the 10-minute revival; never under 30 s);
- the wake-at-the-anvil step, which spends the faction's real experience as a player would;
- personality flavour in what a hero likes to wear, gives away and asks for (a glutton likes to gorge, a merchant likes
  gold, a tsundere refuses a first gift, an honourable hero gives back what it was lent);
- the artificer and the Artificer's Bench (the owner asked for tinkerers; the role, the block and its rules are ours);
- three engineered artifacts for the tech tree, so research has something of its own to unlock (7.9);
- an optional `artifacts` hint block in the hero file and the `artifact` line events (16.6).

---

## 1. What an artifact is

### 1.1 The item

- **One registered item per artifact** (`registry/ModArtifacts`, a fixed list like the talent catalogue: the mod owns
  the catalogue and its numbers). Reason: vanilla item models and textures, recipes, loot tables, `/give`, the creative
  tab and advancements all work by item id, with no custom model loader. The numbers are data
  (`data/factionclash/artifacts/<id>.json`, section 16.1), read by a reload listener, so an operator or a datapack can
  tune them; the set of ids is code.
- Properties: stack size 1; `fireResistant()` (floats on lava like netherite); an `Item` subclass `ArtifactItem` whose
  `canBeHurtBy` also refuses explosions and cactus; rarity by tier (1.3); the enchantment glint for rare finds and
  signatures (`enchantment_glint_override`). Not enchantable, not repairable, not equipable in armour slots.
- Data components: `factionclash:artifact_state` (`dormant`, true until woken, 1.4; for a signature only `instance`, a
  UUID, and `faded`: 15.1) and `factionclash:artifact_work` (`upgrade`, 1-3, 9.8; `growth`, the count of a growing
  artifact, 7.8). Both travel with the item wherever it goes. The slot it sits in, and who lent it, belong to the hero,
  not to the item (17.2).
- A separate item, `factionclash:artificers_sigil`, is the smithing template (9.1); it is not an artifact. A block,
  `factionclash:artificers_bench`, is the tinkering station (9.7); it is not an artifact either.

### 1.2 The look: 16x16 icons

Vanilla item style, so artifacts sit beside vanilla items in an inventory:

- one object on a transparent 16x16 canvas, lit from the top left, a one-pixel darker outline only where vanilla items
  have one, at most about eight colours per material from the same ramps as the hero painter (`palette.py`);
- the object says what it is made of: a flint chip knotted on string, a golden locket, a lapis lens in a gold rim, a
  coil of copper wire, an iron buckle, a clock-hand pin, a tuff disc, a deepslate weight, an amethyst fork, a feathered
  charm, a stoppered spider-eye vial, a glistering melon charm;
- **tiers by material richness**: components are a single material; advanced artifacts add a setting and a gem or core
  in their element's colour (the eight element colours of the particle table); greater artifacts add a second precious
  material and a one-pixel highlight; rare finds show the enchantment glint; signatures show the glint and a slow
  4-frame pulse (an `.mcmeta` animation);
- **growing artifacts** (7.8) have four icon states, chosen by an item property from their growth as a bow's icon is
  chosen by its pull (empty, a third, two thirds, full of their current cap): a fuller ampoule looks fuller (the
  growing signature, Hollow Star, keeps its signature pulse instead; its growth shows in the tooltip). An upgrade
  level does not change the icon; the name's numeral says it (1.3);
- generated, not hand-painted: a small icon generator (`tools/herogen icons`, task R6) builds each icon from a spec
  (`herosrc/artifacts/icons.json`: a shape from a library of about 30 silhouettes, a material ramp, a gem colour, an
  accent), deterministically, and writes a contact sheet for the owner's review (`docs/heroes-v2/previews/artifacts/`).
  The 11 artifacts added by the revision and the bench get their specs later, as data under the same ids.

### 1.3 Tiers, colours and the tooltip

| Tier | Name colour | Count | Glint | Made |
|---|---|---|---|---|
| Component | yellow (vanilla uncommon) | 12 | no | crafting table |
| Advanced | aqua (vanilla rare) | 28 (8 growing) | no | smithing table, then woken at an anvil |
| Greater | light purple (vanilla epic) | 13 (3 engineered) | no | smithing table, then woken at an anvil; the engineered three at the bench (7.9) |
| Rare find | light purple | 6 | yes | found only (9.3), already awake |
| Signature | gold | 2 | yes, and a pulse | comes with its hero (7.5) |

Tooltip (the advanced fire focus at upgrade level II as an example; blue lines are stat lines as vanilla shows
attribute bonuses, gold the passive's name, grey its text, dark purple the lore):

```
Kiln Heart II
Advanced artifact · fire
 +15% fire damage
 +8% ability power
 +13% fire resistance
Banked Embers (primed): an enemy its moves hit smoulders for 6 s;
its next basic hit on it deals 2 fire and sets it alight for 3 s.
Works only for a hero wearing it in an artifact slot.
Next: upgrade III at an Artificer's Bench (needs Artifice 1).
Built from: Lapis Lens + Block of Coal
"Banked coals that never quite go out."
```

A growing artifact adds its growth: "Grows with the blood its wearer draws: step 2 of 3 (1,240 / 1,800)". The numbers
are the artifact's own and the same on every hero (4.2); the hero sheet marks a line that the hero's build has capped.
A dormant artifact shows its name in grey with "Dormant: wake it at an anvil (5 levels and 2 lapis lazuli)".

### 1.4 Dormant and awake

An advanced or greater artifact comes off the smithing table **dormant**: it gives a hero nothing until it is woken at
an anvil (9.1). Components, rare finds, signatures and the engineered artifacts (built awake at the bench) are never
dormant. The step exists so that the strongest artifacts cost experience as enchanting does, which ties them into the
faction's existing experience economy (the enchanter and the blacksmith's repairs already share it), and so that the
tooltip's "works only for a hero" has a visible first half.

### 1.5 Upgrade level and growth on the item

- **Upgrade level** I (as made), II or III, shown after the name as vanilla shows enchantment levels ("Kiln Heart II").
  It is the item's: it stays through every hand, chest, death drop and theft (9.8). A smithing step starts the new
  artifact again at I (9.8).
- **Growth** (growing artifacts only): a count of what its wearers have done while wearing it, and the step that count
  has reached, never above its cap (two steps plus its upgrade level). It is the item's too, and never decays (7.8).

---

## 2. Who benefits

- **A hero wearing it in one of its artifact slots**: the stat lines, the passive or active, and (signature only) the
  bond. Nothing else in the world gets anything from an artifact.
- **Carriers get nothing**, and all carriers are equal: a player (inventory, hand, off hand, ender chest), an ordinary
  NPC (soldier, worker, hauler, trader: backpack), a hero's own backpack, a chest, a hopper, an item frame. A player
  cannot wear one (players have no artifact slots); an ordinary NPC cannot either. This is the owner's "normal npcs can
  have these artifacts in their inventory and transport them around but it has no effect on them", and it treats
  players and NPCs the same. A growing artifact grows only for a hero wearing it: carried, it counts nothing (7.8).
- **Who an artifact's effects reach** follows the format's own rules (`HEROES_V2.md` 6.15): an enemy-only effect reaches
  every enemy alike (a player, an NPC soldier, a hero, a monster), an ally effect every ally alike (member players
  included). `silence` stops only those that have moves (heroes and casting NPCs), as 6.6 says. Nothing in this file
  reads whether anyone is a player; only presentation (bars, tooltips, sounds, the screen) is for players.
- **Whose hero it is does not matter**: an AI faction's hero and a player's faction's hero wear artifacts by the same
  rules and make the same choices (section 10); a free (exiled or wandering) hero keeps what it wears.

---

## 3. Slots

**Slots open with the hero's level** (the owner's answer of 2026-11-18: "Heroes may have more slots as they level up";
`heroes.artifacts.slotLevels`, PROPOSED):

| Hero level | 1-4 | 5-9 | 10-14 | 15-19 | 20-30 |
|---|---|---|---|---|---|
| Slots | 1 | 2 | 3 | 4 | 5 |

Any artifact fits any slot. A new slot opens at the level-up itself; the hero fills it at its next choice (10.6).

Why this curve:
- **One more every five levels**, so every stretch of levelling brings a visible step, and **five from level 20**, the
  level at which everything else about a hero is at full (its abilities' power, its talents' tier III, its mobility).
  Levels 21-30 add no slot.
- **Not by rarity.** Rarity already sets boss health (common 250 to legendary 400, `HEROES_V2.md` 6.2) and with it war
  power; more slots for rarer heroes would compound the gap.
- **Not by role, leadership or talent.** Every role gets value from the same five slots (the scoring of 10.2 finds each
  role its lines); a leader already gets +50 boss health scale and reaches level 20 sooner (it also earns XP from
  faction events, `HEROES_V2.md` 8); a talent that opened a slot would make one talent worth more than the others.
- **Five, not six.** With fixed strength every slot is a whole artifact. Five greater artifacts already reach most of
  the build caps a hero's role cares about (4.1, 14), and a sixth would mostly add a passive to a build that is
  already capped; five squares stay readable on a boss bar.
- **The level matters more than before.** A level-1 hero has one artifact at full strength where the first design gave
  it three at 60%; a level-20 hero has five at full where it had three. Artifacts now widen the gap that levels make,
  which is what "more slots as they level up" asks for (14, case 6).

**The slots live on the hero entity** (saved in its `FcHero` NBT, 17.2). A member player of the hero's faction sees them
from the hero sheet (a small screen with the vanilla hopper layout: the open slots, then the closed ones marked "opens
at level 10"), with an **Offer** slot and an **Ask** button by each artifact the hero holds (10.10). Nobody but the
hero puts an artifact on or takes one off: the first design's pins are gone (the owner's answer 2).

**Attuning.** Putting an artifact on takes 2 s (PROPOSED): the hero holds it to its chest (`artifact.attune`, a core
animation, 12.7) with a faint glint and a chime; damage interrupts it, and only out of a fight can it start. Its lines
apply when the 2 s are over. Taking one off is instant.

**A slot closes** only when the configuration or an operator's command lowers the hero's slots or level: its artifact
goes to the hero's backpack at the next tick (or drops at its feet if the backpack is full).

**The boss heroes** join at level 1 (`BOSS_HEROES.md`), so their one slot holds their signature artifact until level 5.

---

## 4. Stats

### 4.1 The vocabulary

One point is the unit of an artifact's budget (section 6). Caps are for the whole build (all worn artifacts together,
at their upgrade levels and growth steps), PROPOSED.

| Line (key) | One point | Build cap | Maps to (existing where it says so) |
|---|---|---|---|
| Health (`health`) | +5% | +20% | `MAX_HEALTH`, a multiplied-total modifier `factionclash:artifact/health` after the boss modifier, so it multiplies boss health; the health share is kept on change, as the boss modifier does |
| Attack (`attack`) | +5% | +15% | `ATTACK_DAMAGE` x (1 + p), as v1's `stat attack`: basic hits and `attack_percent` Amounts (still under v1 6.2's 1.5 x reference attack power) |
| Ability power (`ability_power`) | +6% | +20% | added to the ability-power multiplier of every Amount (damage, damage over time, heal, shield): v1 6.3, 4.3 below |
| Armour (`armor`) | +1 | +4 | `ARMOR`, added. Strong against many small hits (soldiers' swords), weak against a hero's big ones; hence a small cap |
| Recharge (`recharge`) | +5% | +20% | the hero's move and artifact-active cooldowns x (1 - recharge). Not the phase gap, the opening gap, the global cooldown, wind-ups, recoveries, or the 20-60 s gap between ultimates (4.4) |
| Attack speed (`attack_speed`) | +5% | +15% | `ATTACK_SPEED` x (1 + p), for **basic attacks only**: the move runner (A5) reads the hero's attack speed without the artifact modifier and without artifact passives' attack-speed stacks, so no move's wind-up or recovery gets shorter from an artifact (4.4; Haste and the hero's own stat still count as before) |
| Speed (`speed`) | +3% | +8% | `MOVEMENT_SPEED` x (1 + p), in and out of fights (not a mobility travel bonus) |
| Energy gain (`energy`) | +8% | +25% | v1's `energy_gain` stat on every energy gain |
| Poise (`poise`) | +10% | +25% | the boss poise maximum (6.9) x (1 + p) |
| Element damage (`element_damage`) | +6% | +20% | damage dealt by the hero's moves and passives **of its own element** (an element artifact names its element, 7.2, and only helps a hero of that element; the "build bucket" of 4.3) |
| One resistance (`resist_<element>`) | +10% | +25% each | added to the hero's resistance to that element (v1 6.1; the clamp -30..+60 stays) |
| All resistances (`resist_all`) | +4% | +10% | all eight elements, physical included (soldiers' blows and arrows are physical against a hero, v1 6.1) |
| Drain (`drain`) | +2% | +6% | heals the hero by that share of the damage it really deals (basic hits, moves, passives), as v1's `drain` field does for one effect; under the healing ceiling of 4.5 |
| Heal power (`heal_power`) | +8% | +20% | heals and shields the hero gives, to allies and itself |
| Resolve (`resolve`) | +10% | +25% | stun, freeze, slowness, tether and silence on the hero last that much shorter (never under 0.5 s); its immunity after a stun stays twice the full length (6.14 rule 2) |
| Knockback resistance (`knockback_resistance`) | +10 points | +20 points | `KNOCKBACK_RESISTANCE` + 0.01 per point of the line (+10 is +0.1), v1's whole-percent stat |
| Stamina (`stamina`) | +10% | +25% | flight stamina, the longest glide and the stamina regained (`MOBILITY_AND_WORK.md` 2.1, 1.3); the fight's air budget (5.1.4) does not change |
| Work speed (`work`) | +5% | +15% | `workSpeed()` at its duty (`MOBILITY_AND_WORK.md` 12.1), inside the 2.0x work cap (13 there) |
| Critical damage (`crit_damage`) | +10% | +25% | v1's `crit_damage` stat (jump critical hits) |

Each line is shown with its sign and unit; a line at its cap shows "(capped)" on the hero sheet, so a player sees when a
further health line would be wasted. The caps did not change with the revision: they bound a whole build whatever the
number of slots, and with five slots they bind more often, which is their purpose (6, 14).

### 4.2 Fixed strength (the owner's answer of 2026-11-18)

An artifact's numbers belong to the artifact, not to its wearer: they are the same on a level-1 and a level-30 hero, on
a common and a legendary one. The first design's attunement (`p(L)`: 60% of an artifact's lines at level 1, full at
level 20, the mobility curve) is gone, with its config keys. "The artifacts doesnt level up with the hero."

- **Stat lines** are as written (at the artifact's upgrade level and growth step). A percentage line is a percentage of
  the wearer's own stat: +10% attack is a tenth of whatever that hero's attack is, as vanilla's Speed effect (a
  multiplied +20% a level) speeds a fast mob and a slow one by the same share; the hero's level growth and gear are
  the hero's, not the artifact's. (The review of 2026-11-18 replaced a Strength example here: vanilla's Strength adds a
  flat +3 attack a level, which is not how these lines work.)
- **Passives and actives** are as written on every hero: their Amounts, chances, durations, radii and cooldowns. Their
  Amounts are multiplied only by the build's artifact ability power (the other artifacts' lines, at most +20%) and by
  `heroes.artifacts.powerPercent`, never by the hero's own level growth (4.3). Recharge shortens an active's cooldown
  as any build line does.
- **Work, talent and revival hooks** (Assayer's Eye, Office, Returning Bloom, Geode Hum) are as written.
- **What changes an artifact** is the artifact itself: its upgrade level (9.8) and, for a growing artifact, its growth
  (7.8). Both are on the item, so the same Kiln Heart II gives the same numbers on whichever hero wears it.
- **What a hero gains by levelling** is slots (3): one artifact at level 1, five from level 20.

### 4.3 Order of operations and the caps on top

1. Sum the lines of every worn artifact at its upgrade level and growth step (one copy per id, section 8; a
   signature's bond lines only for its own hero).
2. Multiply by `heroes.artifacts.powerPercent` (100).
3. Clamp each line to its build cap (4.1).
4. Apply: attribute modifiers with fixed ids `factionclash:artifact/<line>` (health, attack, armour, attack speed,
   speed, knockback resistance), re-applied on every change (equip, remove, upgrade, growth step, config reload) so
   they never stack; the other lines as the hero's **build stats** in `HeroStatus`, read by the systems that own them.
5. Every existing cap still holds on top, unchanged:

```
a hero's own move:
raw   = Amount at hit time  (its attack part at most 1.5 x the reference attack power, v1 6.2)
        x (1 + growth ability power x (level - 1) + artifact ability power)  x  heroes.powerScale / 100
an artifact's passive or active (fixed strength, 4.2):
raw   = Amount x (1 + artifact ability power) x heroes.artifacts.powerPercent / 100
then, for both:
dealt = raw x (1 + fight bonuses + build bonuses)        fight bonuses: states, passives, enrage, at most +25% (6.10)
                                                         build bonuses: artifact element damage, at most +20%
                                                         the sum clamped -50% .. +80% (v1 6.3)
            x element against the target                 0, 1 or 1.3
            x (1 - target resistance)                    a hero target: authored + artifact, clamped -30% .. +60%
            x (1 + target damage taken)                  clamped -60% .. +50%
        the product of the factors at most 2.0; one hit at most heroes.maxHit (40); target_max_health at most 2% on a hero
then on a hero target: the per-hit cap, 12% of its (artifact-raised) maximum, the excess to poise (6.2)
```

So the artifact layer can raise how often a hero reaches the ceilings, never the ceilings themselves. A temporary
damage bonus that an artifact passive grants (Gathering Pace's attack speed is not one; Rising Echo's +3% stacks are)
falls in the **fight bucket** with the hero's own passives, states and enrage, under the +25% that 6.10 already sets.

### 4.4 What artifacts never touch

These are structural rules of `HEROES_V2.md` section 6 and `MOBILITY_AND_WORK.md` part 1. The artifact validator
(16.5) refuses any stat, passive, active, bond, upgrade or growth step that would change them (PART LEFT OUT,
"artifacts do not change the boss rules"):

- the per-hit cap (`max_hit_share`), `health_scale` and the role factor (health lines multiply the result instead);
- the hurt cooldown per attacker, the per-hit `heroes.maxHit`, the 2% `target_max_health` rule against heroes;
- wind-up and recovery minimums of every class (6.4), the opening gap, the phase gaps, the 1 s global cooldown, the
  ultimate's 20-60 s minimum gap and its energy of 100;
- a move's frames at all: artifact attack speed speeds basic attacks only (4.1), so no wind-up, recovery or
  telegraph of a move gets shorter because of an artifact, even above its class minimum; a tuned active (9.8) gets a
  shorter cooldown, never a shorter wind-up;
- the stun caps (2 s per skill, 3 per ultimate, 1 per passive) and the receiver's immunity window;
- poise decay, the stagger's length and its +25% damage taken (Settled Weight changes only the poise growth after a
  stagger, 7.2);
- the fight's air budget, altitude ceilings and reach clock (5.1 there); no artifact grants flight, a glide or any
  mobility family or tier the hero does not have (the Skyroad Pennant grows only for a hero that already flies or
  glides, 7.8);
- the escape threshold, `when_losing`, `stands_for` and the 5-minute stay away;
- lethal damage: **no artifact cancels a killing blow** (there is no totem-like artifact), and no artifact changes a
  revival except through its own death trigger, never under 30 s (3.4);
- teleporting, placing, breaking or igniting blocks, and anything that acts through a non-hero wearer. The owner's
  answer of 2026-11-18 that anyone with explosives may breach walls is about what heroes and NPCs carry and use
  (`HEROES_V2.md` 6.18 and `docs/design/breaching.md`), not about artifacts: no artifact is an explosive (6.18's
  `explosive` property is refused on an artifact's active, 16.5). An artifact's push (Updraft's knockback, Downpull's
  pull, Ram Strike) is a move's push like any other: `HEROES_V2.md` 6.17 ("terrain is a weapon", the owner's answer
  that a hero's ability may send anyone off a cliff or into lava) lands it at full strength, and the hero's AI aims it
  at edges as it aims its own moves. The never-touch rules of 6.17 hold for it too: never its own side, never its own
  body into a hazard. An artifact's push is at most knockback 3 (about 6 blocks on flat ground, the Ender Dragon's
  Wing Gust; 16.5 lowers a stronger one), so no artifact gives any hero a longer push than the strongest kit has.

### 4.5 The healing ceiling

In a fight, everything an artifact heals its own wearer (drain, Gorge, Soothing Smoke on itself, Second Hunger,
Shared Harvest and Sun-Fed on itself) adds up to at most **10% of the wearer's maximum health in any 30 s**
(`heroes.artifacts.healingCeilingPercent`, PROPOSED); the rest is lost. Out of a fight there is no ceiling (heroes heal
out of fights anyway, 6.10). Heals an artifact gives to others are not under this ceiling (they are under the passive
caps). Reason: with no out-of-combat regeneration in a fight, drain is the strongest durability line a boss can get, and
a hero cut down by six soldiers at about 18-22 a second should not out-heal them; against a single player, whom a hero
hurts slowly, the ceiling is rarely reached (14, cases 3 and 8).

---

## 5. Passives and actives

### 5.1 Passives

- A passive is v1 section 17's passive (trigger, `chance`, `cooldown`, `form`, `radius`, `if`, 1-3 effects) with the
  format-2 triggers of `HEROES_V2.md` 6.6, written in the artifact file (16.1). It may have **two parts** that share one
  name (a set-up part and a pay-off part: "after a move, the next basic hit..."), each a full passive entry.
- **Caps and floors**: every part is checked against the **passive row** of the slot caps (8 damage, 8 damage over
  time, 4 heal, 4 shield, 1 s stun, at the level-1 reference) and gets v1 17.4's cooldown floor, exactly as the hero's
  own passives (HeroBalance, ADJUSTED with the usual messages). A tuned passive (9.8) is checked again after tuning.
- **The loop guard** (v1 17.3) counts artifact passives with the hero's own: damage from passives never fires `on_hit`,
  `on_crit` or `on_ally_attack`; at most 8 passive firings per hero per second.
- **Family** (`unique`): passives with the same family id do not stack on one hero; only the stronger fires (section 8).
- Passive Amounts are fixed (4.2): the build's artifact ability power multiplies them, the hero's level does not.

### 5.2 Actives

An active is a **move** (`HEROES_V2.md` 12.7: form, class, effects, cooldown, `use_when`, telegraph) that joins the
hero's move list in every phase while the artifact is worn:

- the **skill row** of the slot caps (16 damage, 8 heal, 2 s stun...) and the cooldown floor apply; its class is
  inferred from its form and never lighter (6.4), so it has a wind-up of at least 0.8 s (heavy) or 1.0 s (area), its
  ground mark and the class cue: the same warning everyone gets;
- the move selector (6.3) picks it like a move, with weight 3 by default, so it does not crowd out the hero's own kit;
  at most one artifact active starts in any 10 s (PROPOSED), however many the hero wears;
- it uses one of three shared core animations (12.7) with the hero's own style library, so no hero needs new clips:
  `artifact.raise` (a raise and slam: Downpull, Updraft), `artifact.cast` (a point forward: Fang Line, Gluefall, Ram
  Strike), `artifact.attune` (equipping, 3);
- recharge (4.1) shortens its cooldown; an escaping hero uses none (it starts no moves while it escapes, 6.13);
- five actives ship (Downpull, Gluefall, Updraft, Fang Line, and Ram Strike with the tech tree); a hero wearing several
  uses each, one per 10 s at most.

### 5.3 New engine pieces

Everything else in the catalogue is built from format 1 and 2 pieces that exist (triggers `on_hit`, `on_crit`,
`on_hurt`, `on_kill`, `on_skill`, `on_ultimate`, `on_fight_start`, `on_low_health`, `on_ally_low_health`, `every`,
`always`, `on_mark_full`, `on_add_died`, `on_escape`, `on_stagger`; effects `damage`, `dot`, `ignite`, `heal`,
`shield`, `effect`, `stat`, `knockback`, `pull`, `taunt`, `energy`, `cooldown_cut`, `move_self`, `mark`,
`clear_marks`, `poise`, `silence`, `barrier`; conditions `self_has_mark`, `self_health_below`, `target_health_below`,
`target_is`). New:

| Piece | Kind | What it does | Used by |
|---|---|---|---|
| The build stats (`recharge`, `drain`, `heal_power`, `resolve`, `element_damage`, `resist_all`, `stamina`, `work`, the artifact `ability_power` and `poise`) | stats | 4.1; read by `HeroDamage`, `HeroCombat` cooldowns, `HeroStatus` control timers, `HeroMovement` stamina, the work API | all |
| `on_move_hit` | trigger | one of its moves (not a basic attack, not a passive) lands its first damage on an enemy; once per move use | Arc Jump, Withering Touch, Banked Embers, Rune-Struck, Stoked Heat, Withering Brand |
| `on_control` | trigger | one of its moves or basic attacks applies a stun, freeze, slowness or silence to an enemy; never control from a passive (the loop guard of 5.1), and a tether does not count, so Snag cannot feed itself | Snag |
| `on_death` | trigger | the wearer dies; only `revival_cut` may follow | Returning Bloom |
| `on_marked` | trigger | an enemy's move starts its wind-up with a ground mark (6.4) that covers the wearer; the move is the event's. It reads the move's own mark geometry on the server (`logic/hero/Decals`), never the drawn decal: decals are spawned only while a player is near (`HEROES_V2.md` 6.1), and the trigger fires the same in a fight with no player in sight | Read the Tell |
| `on_land` | trigger | the wearer lands after flying or gliding at least `value` blocks | Road Memory |
| `jump` | passive form | the nearest other enemy within `radius` of the event's enemy | Arc Jump |
| `ally_lowest` | passive form | the ally (itself included) with the lowest health share within `radius` | Shared Harvest |
| `adapt` | effect | +`percent` resistance, for `seconds`, to the element of the hit that set it off (non-physical; at most two elements at once) | Weathered |
| `revival_cut` | effect | the wearer's revival comes `percent` sooner; multiplies with `revival_rite` and a hero's own `revival`; never under `heroes.minReviveSeconds` (30) | Returning Bloom |
| `ward_effect` | effect | a vanilla effect `id` lasts `percent` less on the wearer (100: it never takes hold); never on an effect that is a counter (Slowness, which downs flyers; Glowing, a tell: PART LEFT OUT) | Lamplit Eyes |
| `self_carries` | condition | the hero's backpack holds at least `value` of an item or tag (blocks of it count 9) | Hoard-Pride |
| `target_bleeds` | condition | the target is a living foe that bleeds: not undead and not in `#factionclash:bloodless` (golems, slimes and magma cubes, blazes, breezes, vexes, allays, shulkers, end crystals, adds) | Open Vein; the Bloodglass Ampoule's growth |
| `engaged_has` | condition | an engaged enemy is a hero or a vanilla boss (the Wither, the Ender Dragon, an elder guardian, the warden) | Measured |
| `in_daylight` | condition | day, and the wearer's block sees the sky | Sun-Fed |
| Growth counters | engine | the eight counts of 7.8 (blood, ore, nights, flight, undead, worthy foes, scars, tending), each fed by an existing event (16.1, 17.3) | the growing artifacts |
| Hooks | engine | `stagger_growth` (Settled Weight), talent tier +1 (Office), virtual levels through the work API (Assayer's Eye, `MOBILITY_AND_WORK.md` 12.2), vein following one block wider (Geode Hum, `surveyor`'s rule), the bond's `kit_mods` (16.4) | as named |

Each is equal for every receiver: `on_control` fires the same whether the one it slowed is a player or an NPC, `adapt`
reads the element of the damage, not who dealt it, `target_bleeds` reads the body (a player bleeds, a zombie does not),
and no growth counter asks whether the foe or the ally was a player.

---

## 6. Tiers, budgets and costs

| Tier | Budget at I | At II | At III | Passive or active | How it is made or found | Woken at the anvil |
|---|---|---|---|---|---|---|
| Component | 2 points | - | - | none | crafting table, vanilla materials | - |
| Advanced | 4 points | 5 | 6 | one passive; tuned at III | smithing table: Sigil + a component + a component or a vanilla material | 5 levels and 2 lapis lazuli |
| Growing (advanced tier, 7.8) | 2 points + 1 per growth step; at most 3 steps (5 points) | at most 4 steps (6) | at most 5 steps (7) | one minor passive | smithing table: Sigil + a component + a vanilla material | 5 levels and 2 lapis lazuli |
| Greater | 5 points | 6.25 | 7.5 | one major passive or an active; tuned at III | smithing table: Sigil + an advanced artifact + a rare vanilla material | 10 levels and 2 lapis lazuli |
| Engineered (greater tier, 7.9) | 5 points | 6.25 | 7.5 | one major passive or an active; tuned at III | the Artificer's Bench, with Artifice 2 research | arrives awake |
| Rare find | 5 points | 6.25 | 7.5 | one major passive or an active; tuned at III | found only (9.3) | arrives awake |
| Signature | 5 points + bond (at most 3 points and 2 kit changes, for its hero only) | either grows (Hollow Star: +1 point a step, at most 3 steps, 8 points full) or upgrades (Spirecore: 6.25 at II, with Artifice 2) | 7.5 (Spirecore, Artifice 2) | one major passive; tuned at III | comes with its hero | arrives awake |

- **Upgrade levels** (9.8): an upgrade multiplies the artifact's lines by 1.25 at II and 1.5 at III, each rounded half
  up to a whole number and clamped to its build cap (so one artifact never passes a cap on its own); at III its passive
  or active is **tuned**. A growing artifact's upgrade widens its cap by one growth step instead.
- A passive is not priced in points: the tiers say how strong it may be (a minor passive on advanced and growing
  artifacts, a major one on greater, engineered, rare and signature ones), and the passive caps and floors bound it.
- The validator (16.5) scales an over-budget artifact's lines down in proportion (ADJUSTED) as it does over-cap
  Amounts.

What the budgets mean at the hero's slots (fixed strength; the estimates of 14):

| Hero level and slots | Build | About |
|---|---|---|
| 1, one slot | a component / an advanced / a greater artifact | +3% / +7% / +9% |
| 10, three slots | three advanced / three greater | +20% / +25-30% |
| 20, five slots | five greater | +35-40%: the build caps bind more and more, so a fifth artifact mostly adds a passive and lines the others lack |
| any | the same build at upgrade level III | about 1.5 times its lines, under the same caps |

---

## 7. The catalogue

Sixty-one artifacts. Ids are snake case (`kiln_heart`); every id of the first catalogue is kept. Stats are the
artifact's own at upgrade level I, the same on every hero (4.2); "P" marks the passive, "A" an active. Recipes:
crafting-table recipes give one artifact unless said; "S:" is a smithing recipe (template: the Artificer's Sigil; base;
addition); "Bench:" is a build at the Artificer's Bench (9.7). "Upgrade" gives the artifact's own material for upgrade
levels II and III, on top of the common part of 9.8 (III needs Artifice 1 research; a rare find's and an engineered
artifact's upgrades need Artifice 2: 9.5). At III every passive or active is tuned by 9.8's rule. Suits lists the tags
of 10.2.

### 7.1 Components (12, crafting table; never upgraded)

| Artifact (id) | Recipe | Stats | Suits |
|---|---|---|---|
| Knapped Flint (`knapped_flint`) | 3 flint over 1 iron ingot over 1 string | attack +10% | striker, skirmisher, marksman |
| Hale Locket (`hale_locket`) | 1 golden carrot in the middle, 1 string above, 3 gold nuggets left, right and below | health +10% | everyone |
| Lapis Lens (`lapis_lens`) | 4 lapis lazuli around 1 glass pane | ability power +12% | support, controller, summoner, casters |
| Copper Coil (`copper_coil`) | 4 copper ingots around 1 redstone dust | attack speed +10% | striker, skirmisher, marksman |
| Riveted Buckle (`riveted_buckle`) | 4 iron ingots around 1 leather | armour +2 | vanguard |
| Clockwork Pin (`clockwork_pin`) | 1 gold nugget, 1 clock, 1 gold nugget in a row | recharge +10% | controller, summoner, support |
| Tuff Ward (`tuff_ward`) | 4 tuff around 1 copper ingot | all resistances +8% | vanguard, anyone facing casters |
| Deepslate Ballast (`deepslate_ballast`) | 4 cobbled deepslate around 1 iron ingot | poise +20% | vanguard, grappler |
| Amethyst Tuner (`amethyst_tuner`) | 3 amethyst shards over 1 copper ingot | energy gain +16% | ultimate-led kits |
| Fletched Charm (`fletched_charm`) | 3 feathers over 1 gold nugget over 1 string | stamina +10%, speed +3% | flyers, gliders, scouts |
| Spider-Eye Vial (`spider_eye_vial`) | 1 spider eye over 1 redstone dust over 1 glass bottle | drain +4% | striker, vanguard |
| Glistering Charm (`glistering_charm`) | 1 gold nugget, 1 glistering melon slice, 1 gold nugget in a row, 1 string above | heal power +16% | support (kits that heal or shield) |

Components are stepping stones: they are cheap to make again, so they are built into advanced artifacts rather than
upgraded.

### 7.2 Advanced (20, smithing table, woken with 5 levels)

The eight **element foci** share a shape (element damage +12%, a second line, +10% resistance to their own element)
and only their element's heroes profit from the damage line (no other hero values it: 10.2).

| Artifact (id) | Recipe (S: base + addition) | Stats | Passive | Upgrade (II / III) | Suits |
|---|---|---|---|---|---|
| Kiln Heart (`kiln_heart`) | Lapis Lens + Block of Coal | fire damage +12%, ability power +6%, fire resistance +10% | **Banked Embers** (family `primed`): an enemy its moves hit smoulders for 6 s; its next basic hit on that enemy deals 2 fire and sets it alight for 3 s (once per 4 s). `on_move_hit`, `trigger_enemy`: `mark` `ember` 6 s; `on_hit` if `target_has_mark` `ember`: `damage` 2 fire, `ignite` 3, `clear_marks` `ember` | block of coal 2 / 4 | fire; melee |
| Rimeglass Prism (`rimeglass_prism`) | Lapis Lens + Packed Ice | frost damage +12%, ability power +6%, frost resistance +10% | **Hoarfrost**: every third basic hit on the same enemy within 5 s slows it (Slowness I, 2 s; Slowness I does not down a flyer). `on_hit` mark `rime` (max 3, 5 s); `on_mark_full`: `effect` slowness I 2 s, `clear_marks` | packed ice 4 / 8 | frost |
| Stormrod Spindle (`stormrod_spindle`) | Lapis Lens + Lightning Rod | shock damage +12%, attack speed +5%, shock resistance +10% | **Arc Jump**: when one of its moves hits, a spark jumps to the nearest other enemy within 5 blocks for 3 shock (once per 6 s). `on_move_hit`, form `jump` radius 5: `damage` 3 shock | lightning rod 2 / 4 | shock |
| Bog Phial (`bog_phial`) | Lapis Lens + Fermented Spider Eye | venom damage +12%, ability power +6%, venom resistance +10% | **Seeping Venom**: a quarter of its basic hits poison: 1 venom a second for 4 s, up to 2 stacks (undead are immune, vanilla). `on_hit` 25%: `dot` 1/s 4 s, `max_stacks` 2 (cooldown 2 s) | fermented spider eye 2 / 4 | venom |
| Bone-Ash Urn (`bone_ash_urn`) | Lapis Lens + Bone Block | blight damage +12%, ability power +6%, blight resistance +10% | **Withering Touch**: enemies its moves hit heal 25% less for 5 s. `on_move_hit`, `trigger_enemy`: `stat` `healing_received` -25 5 s (cooldown 2 s) | bone block 2 / 4 | blight; anti-support |
| Starless Pearl (`starless_pearl`) | Lapis Lens + Ender Pearl | arcane damage +12%, recharge +5%, arcane resistance +10% | **Racing Thoughts**: each move it uses takes 1 s off its other cooldowns (once per 6 s; v1's `cooldown_cut` never takes a cooldown under half). `on_skill`: `cooldown_cut` 1 | ender pearl 2 / 4 | arcane; move-heavy kits |
| Daybreak Brooch (`daybreak_brooch`) | Lapis Lens + Block of Gold | radiant damage +12%, heal power +8%, radiant resistance +10% | **Morning Grace**: when an ally within 12 blocks drops under 35% health, it heals that ally 3 (once per 20 s; a member player is an ally like any other). `on_ally_low_health` 35, `trigger_ally`: `heal` 3 | gold ingot 4 / 8 | radiant; support |
| Ironbound Whetstone (`ironbound_whetstone`) | Knapped Flint + Block of Iron | physical damage +12%, attack +5%, physical resistance +10% | **Honed**: its jump critical hits deal 2 more and add 10 poise to a hero they hit (once per 2 s). `on_crit`, `trigger_enemy`: `damage` 2 physical, `poise` 10 | block of iron 1 / 2 | physical; melee |
| Marrow Torc (`marrow_torc`) | Spider-Eye Vial + Knapped Flint | drain +4%, attack +5%, health +5% | **Gorge**: each enemy it kills heals it 4 (once per 5 s; under the healing ceiling). `on_kill`, `self`: `heal` 4 | spider eye 4 / 8 | striker, vanguard; glutton |
| Quickstep Anklet (`quickstep_anklet`) | Copper Coil + Fletched Charm | attack speed +10%, speed +6% | **Gathering Pace**: each basic hit gives +4% attack speed for 3 s, up to 3 stacks (basic attacks only, 4.1). `on_hit`, `self`: `stat` `attack_speed` +4 3 s, `max_stacks` 3 | copper ingot 4 / 8 | skirmisher, marksman |
| Runebound Signet (`runebound_signet`) | Lapis Lens + Knapped Flint | ability power +6%, attack +5%, recharge +5%, health +5% | **Rune-Struck** (family `primed`): an enemy its moves hit carries a rune for 6 s; its next basic hit on that enemy deals 5 extra damage of its element and spends the rune (once per 3 s). `on_move_hit`, `trigger_enemy`: `mark` `rune` 6 s; `on_hit` if `target_has_mark` `rune`: `damage` 5, `clear_marks` `rune` | flint 4 / 8 | striker, skirmisher; any element |
| Ironhide Girdle (`ironhide_girdle`) | Hale Locket + Riveted Buckle | health +10%, armour +2 | **Braced Hide** (family `last_ditch`): the first time in a fight it drops under half health: a 4-point shield for 10 s and +30% knockback resistance for 8 s. `on_low_health` 50, `once_per_fight`: `shield` 4, `stat` `knockback_resistance` +30 8 s | leather 4 / 8 | vanguard; cautious heroes |
| Cactus-Spine Mantle (`cactus_spine_mantle`) | Riveted Buckle + Cactus | armour +2, health +5%, all resistances +4% | **Prickle**: when hurt, enemies within 3 blocks take 2 physical (once per 1.5 s). `on_hurt`, form `nova` radius 3: `damage` 2 physical | cactus 4 / 8 | vanguard against swarms |
| Hearthwool Shawl (`hearthwool_shawl`) | Tuff Ward + Hale Locket | all resistances +8%, health +10% | **Weathered**: when an element other than physical hurts it, it resists that element 15% more for 8 s (two elements at most). `on_hurt`, `self`: `adapt` 15 8 s (cooldown 2 s) | wool (any colour) 4 / 8 | anyone facing casters |
| Anchor Chain (`anchor_chain`) | Deepslate Ballast + Chain | poise +20%, resolve +20% | **Settled Weight**: after each stagger its poise maximum grows 30% instead of 20% for the rest of the fight (hook `stagger_growth`; a hook is not tuned) | chain 2 / 4 | vanguard, grappler |
| Mender's Brazier (`menders_brazier`) | Glistering Charm + Hale Locket | heal power +16%, health +10% | **Soothing Smoke**: every 10 s in a fight, it and allies within 6 blocks heal 2. `every` 10, form `allies` radius 6: `heal` 2 | glistering melon slice 2 / 4 | support |
| Shepherd's Lantern (`shepherds_lantern`) | Clockwork Pin + Amethyst Tuner | recharge +10%, energy gain +16% | **Kindred Light**: when one of its summons or adds dies, it gains 8 energy (once per 4 s). `on_add_died`, `self`: `energy` 8 | amethyst shard 4 / 8 | summoner |
| Snarewire Spool (`snarewire_spool`) | Clockwork Pin + Tripwire Hook | recharge +10%, ability power +6%, resolve +10% | **Snag**: an enemy it stuns, freezes, slows or silences is also snagged: tethered to it for 3 s and pulled back if it goes more than 8 blocks away (once per 6 s). `on_control`, `trigger_enemy`: `tether` 3 s, `max` 8 | tripwire hook 2 / 4 | controller |
| Wind-Kite Tassel (`wind_kite_tassel`) | Fletched Charm + Paper | stamina +20%, speed +6% | **Fair Wind**: when it breaks off to escape, Speed I for 6 s. `on_escape`, `self`: `effect` speed I 6 s | paper 4 / 8 | flyers, gliders; escaping kind |
| Prospector's Loupe (`prospectors_loupe`) | Knapped Flint + Spyglass | work speed +15%, stamina +10% | **Assayer's Eye**: at its duty its tool counts one virtual level higher for the loot: Fortune on ores, crops and leaves, Luck of the Sea when fishing (the rules and caps of `MOBILITY_AND_WORK.md` 12.2 and 13: at most level III with the tool's own and its specialty, never on Silk Touch, never with an extra roll on the same drop; a hook is not tuned) | spyglass 1 / 2 | miners, woodcutters, farmers, fishers; greed |

### 7.3 Greater (10, smithing table, woken with 10 levels)

| Artifact (id) | Recipe (S: base + addition) | Stats | Passive or active | Upgrade (II / III) | Suits |
|---|---|---|---|---|---|
| Breeze-Cut Talon (`breeze_cut_talon`) | Ironbound Whetstone + Breeze Rod | attack +10%, attack speed +5%, critical damage +10%, speed +3% | P **Gale Finish** (family `finisher`): its basic hits on an enemy under 30% health deal 10% of the health it is missing (at most 8, the passive cap; once per 2 s). `on_hit` if `target_health_below` 30: `damage` `target_missing_health_percent` 10 | breeze rod 1 / 2 | striker, skirmisher |
| Blackforge Girdle (`blackforge_girdle`) | Ironhide Girdle + Netherite Scrap | health +15%, armour +2 | A **Downpull**: it slams the ground and drags enemies within 5 blocks toward it, taunting them for 2 s (class area: 1.0 s wind-up, a ring, the area cue; recovery 0.8 s; cooldown 40 s; used when 2 or more enemies are near). Form `nova` 5: `pull` 2, `taunt` 2 | block of iron 2 / 4 | vanguard |
| Deepecho Signet (`deepecho_signet`) | Runebound Signet + Echo Shard | ability power +12%, recharge +10%, health +5% | P **Rising Echo**: each move it uses adds +3% damage for 10 s, up to 4 stacks (the fight bucket's +25% holds). `on_skill`, `self`: `stat` `damage_dealt` +3 10 s, `max_stacks` 4 | echo shard 1 / 2 | controller, marksman, support with damage moves |
| Blaze-Rod Bracer (`blaze_rod_bracer`) | Quickstep Anklet + Blaze Rod | attack speed +10%, attack +5%, element damage +6%, speed +3% | P **Stoked Heat**: each basic hit stokes it (up to 4 heat, all lost after 4 s without a hit); at 4 heat the first hit of its next move deals 4 extra damage of its element and spends the heat. `on_hit` self `mark` `heat` (max 4, 4 s); `on_move_hit` if `self_has_mark` `heat` 4: `damage` 4, `clear_marks` self | blaze rod 2 / 4 | marksman, skirmisher |
| Lifebound Chime (`lifebound_chime`) | Mender's Brazier + Totem of Undying | heal power +16%, health +10%, recharge +5% | P **Mercy Toll**: when an ally within 12 blocks drops under 20% health, it shields that ally for 4 for 10 s (once per 30 s; a shield, never a saved life). `on_ally_low_health` 20, `trigger_ally`: `shield` 4 | golden apple 1 / 2 | support |
| Ghast-Tear Torc (`ghast_tear_torc`) | Marrow Torc + Ghast Tear | drain +4%, health +10%, attack +5% | P **Second Hunger**: under 40% health its drain is 4% higher (the drain cap and the healing ceiling hold). `always` if `self_health_below` 40: `stat` `drain` +4 | ghast tear 1 / 2 | striker, vanguard |
| Slimebound Spool (`slimebound_spool`) | Snarewire Spool + Slime Block | recharge +10%, ability power +6%, resolve +10%, health +5% | A **Gluefall**: a disc of slime of radius 3 at its target; after a 1.0 s wind-up (a growing disc), it pulses three times over 2 s: Slowness II 2 s each pulse (Slowness II downs a flyer, `MOBILITY_AND_WORK.md` 2.4; class area, cooldown 45 s, used when 2 or more enemies are near). Form `zone` radius 3, duration 2, interval 1 | slime block 1 / 2 | controller; anti-air |
| Gale-Rider's Cord (`gale_riders_cord`) | Wind-Kite Tassel + Wind Charge | stamina +20%, speed +6%, knockback resistance +10 | A **Updraft**: a burst of wind knocks enemies within 3 blocks back and lifts it 4 blocks (no fall damage from it; class area: 1.0 s wind-up with a ring; cooldown 30 s; used when an enemy is within 3 blocks). Form `nova` 3: `knockback` 2; `move_self` up 4 | wind charge 4 / 8 | skirmisher, marksman, flyers |
| Shulker-Shell Ward (`shulker_shell_ward`) | Hearthwool Shawl + Shulker Shell | all resistances +8%, health +10%, resolve +10% | P **Shell Up** (family `last_ditch`): the first time in a fight it drops under 40%, it ignores the next 3 hits within 5 s. `on_low_health` 40, `once_per_fight`: `barrier` 3 hits, 5 s | shulker shell 1 / 2 | vanguard; cautious heroes |
| Tidewell Conch (`tidewell_conch`) | Shepherd's Lantern + Nautilus Shell | energy gain +16%, recharge +5%, ability power +6%, health +5% | P **Swell**: every 15 s in a fight it gains 5 ultimate energy (never in `on_ultimate`, v1 14). `every` 15, `self`: `energy` 5 | nautilus shell 1 / 2 | ultimate-led kits, summoner |

### 7.4 Rare finds (6, found only, awake; upgrades need Artifice 2)

| Artifact (id) | Where (9.3) | Stats | Passive or active | Upgrade (II / III) | Suits |
|---|---|---|---|---|---|
| Hushstone (`hushstone`) | Ancient city chests | ability power +12%, recharge +10%, resolve +10% | P **Deep Quiet**: when it uses its ultimate, enemies within 8 blocks cannot start moves for 1.5 s (silence: it stops heroes and casting NPCs; players and soldiers have no moves to stop). `on_ultimate`, `nova` 8: `silence` 1.5 | echo shard 2 / 4 | anyone who fights heroes |
| Bastion Hoard-Ring (`bastion_hoard_ring`) | Bastion treasure and bastion chests | health +10%, attack +10%, armour +1 | P **Hoard-Pride**: while its backpack holds 16 or more gold ingots (a gold block counts 9), +8% damage dealt and -8% damage taken. The gold is real and drops if it dies. `always` if `self_carries` gold 16: `stat` `damage_dealt` +8, `damage_taken` -8 | block of gold 1 / 2 | greed; merchants |
| Chorus-Bloom Pendant (`chorus_bloom_pendant`) | End city chests | health +10%, all resistances +8%, stamina +10% | P **Returning Bloom**: if its wearer dies wearing it, it returns 30% sooner (never under 30 s); the pendant drops with the rest. `on_death`: `revival_cut` 30 (tuned: 36) | chorus flower 2 / 4 | leaders, last-stand heroes |
| Fangspeaker Charm (`fangspeaker_charm`) | Woodland mansion chests; evokers | ability power +12%, health +10%, recharge +5% | A **Fang Line**: a line of fangs bursts from the ground toward its target, 10 blocks long: 6 arcane to each enemy on it (class heavy: 0.8 s wind-up with a strip on the ground and the evoker's cue; cooldown 25 s; used when the target is 5 or more blocks away). Form `line` 10, width 1, `look.fangs` | emerald 8 / 16 | marksman, controller, zoners |
| Elderscale Lens (`elderscale_lens`) | Elder guardians (50% when one dies) | ability power +6%, all resistances +8%, energy gain +16% | P **Fatigue Gaze**: when a fight starts, enemies within 8 blocks get Mining Fatigue I for 8 s (vanilla: slower attacks and digging; an NPC soldier's swing reads `ATTACK_SPEED` as a player's does, which `war_drummer`'s modifier already assumes). `on_fight_start`, `nova` 8: `effect` mining_fatigue I 8 s | wet sponge 1 / 2 | controller, vanguard |
| Steward's Seal (`stewards_seal`) | Pillager outpost chests | health +10%, work speed +10%, recharge +5% | P **Office**: its talents count one tier higher (at most III; Presence's tier IV for guard and court talents is not raised, the higher of the two counts, `MOBILITY_AND_WORK.md` 11.2), and its realm talent works at the faction's other bases at tier II instead of I (a hook is not tuned) | book 4 / 8 | leaders; talent heroes |

### 7.5 Signature artifacts (2, with their heroes)

The Wither hero's and the Ender Dragon hero's own artifacts (`BOSS_HEROES.md`, which chooses their ids, names, lines,
passives and bonds). They use the signature tier (6) and the bond format (16.4): base lines and a passive any hero may
use, a bond only for the hero they belong to ("best suited for them"), one instance per world, re-formed if destroyed,
dropped like any artifact when the hero dies (the owner's "they are also dropped if they die"). Designed in
`BOSS_HEROES.md` 4.13 and 5.13: **Hollow Star** (`hollow_star`, the Wither's: ability power, drain, blight
resistance and health, with Withering Brand) and **Spirecore** (`spirecore`, the Ender Dragon's: health, ability
power, energy and stamina, with Spire Mend); each bond adds three points of lines, a stronger passive in the same
family and two changes to her own moves.

- **Fixed strength** (4.2): its base lines are the same on any hero at any level (the first design's "x attunement" is
  gone); its bond lines too, on its own hero.
- **One way to grow stronger each** (`BOSS_HEROES.md` 4.13 and 5.13, revised 2026-11-18): Hollow Star grows by its
  own design, counting the withering its wearer deals (`withering`, by 7.8's rules, three steps at most since it is
  never upgraded: 8 points full); Spirecore is upgraded at the bench as a rare find is, with Artifice 2 research (9.5,
  9.8), and never grows. An upgrade multiplies its base lines only; the bond's lines and kit mods stay as they are.
  16.4 refuses a signature with both, or with an upgrade gated below Artifice 2.
- **Her one slot at level 1**: she joins at level 1 (`BOSS_HEROES.md`), so her signature fills her only slot until
  level 5 (3).
- **It stays with her by her own choice** (10.4): the bond makes her want it above anything, so she never swaps it
  out, passes it on or hands it to an ask, and no leader's wish moves it. It changes hands only at her death (it drops)
  or when she leaves the faction (her token, `BOSS_HEROES.md`). Held by another hero of her faction, it counts as lent
  by her (10.4).

### 7.6 The build tree

```
Knapped Flint ──┬─ Ironbound Whetstone (+ iron block) ── Breeze-Cut Talon (+ breeze rod)
                ├─ Marrow Torc (+ Spider-Eye Vial) ───── Ghast-Tear Torc (+ ghast tear)
                ├─ Runebound Signet (+ Lapis Lens) ───── Deepecho Signet (+ echo shard)
                ├─ Prospector's Loupe (+ spyglass)
                └─ Rival's Cord (+ lead)                                                   [grows]
Lapis Lens ────┬─ the seven element foci (+ coal block, packed ice, lightning rod, fermented spider eye,
               │                           bone block, ender pearl, gold block)
               └─ Runebound Signet
Copper Coil ── Quickstep Anklet (+ Fletched Charm) ── Blaze-Rod Bracer (+ blaze rod)
Fletched Charm ┬─ Quickstep Anklet
               ├─ Wind-Kite Tassel (+ paper) ──────── Gale-Rider's Cord (+ wind charge)
               └─ Skyroad Pennant (+ phantom membrane)                                     [grows]
Hale Locket ───┬─ Ironhide Girdle (+ Riveted Buckle) ─ Blackforge Girdle (+ netherite scrap)
               ├─ Hearthwool Shawl (+ Tuff Ward) ───── Shulker-Shell Ward (+ shulker shell)
               ├─ Mender's Brazier (+ Glistering Charm) ─ Lifebound Chime (+ totem of undying)
               └─ Hearthroot Seed (+ moss block)                                           [grows]
Riveted Buckle ┬─ Ironhide Girdle
               ├─ Cactus-Spine Mantle (+ cactus)
               └─ Scarbark Amulet (+ dark oak log)                                         [grows]
Deepslate Ballast ── Anchor Chain (+ chain)
Clockwork Pin ─┬─ Shepherd's Lantern (+ Amethyst Tuner) ─ Tidewell Conch (+ nautilus shell)
               ├─ Snarewire Spool (+ tripwire hook) ──── Slimebound Spool (+ slime block)
               └─ Vigil Lantern (+ soul lantern)                                           [grows]
Spider-Eye Vial ── Bloodglass Ampoule (+ redstone block)                                    [grows]
Amethyst Tuner ─── Veinseeker Geode (+ raw iron block)                                      [grows]
Glistering Charm ─ Graveward Bell (+ bell)                                                  [grows]
At the bench, with Artifice 2:  Starless Pearl ── Observer Lens;  Ironhide Girdle ── Piston Bracer;
                                Mender's Brazier ── Daylight Coil                           [engineered]
Rare finds and signatures: no tree
```

The seven foci, Cactus-Spine Mantle, Anchor Chain, Prospector's Loupe and the eight growing artifacts are finished at
the advanced tier: cheaper, narrower picks beside the greater artifacts; a growing one becomes as strong as a greater
artifact of its upgrade level once it is full (7.8).

### 7.7 Example builds (what each hero would tend to choose for itself, 10.4; PROPOSED)

With the whole catalogue in reach, as its kit sheet's "ARTIFACTS" section will show it (12.4). Slots by level: 1, 3
and 5.

| Hero | Level 1 | Level 10 | Level 20 | Why |
|---|---|---|---|---|
| Benika Akagane (vanguard, fire, battle maniac, miner) | Kiln Heart | Kiln Heart, Blackforge Girdle, Bloodglass Ampoule | the same, Ghast-Tear Torc, Rival's Cord | burns, drags enemies in, drinks from every brawl; her aggression (95) weighs damage, her caution (5) health little |
| Natsuho Komezaki (striker, physical, tsundere, fisherman) | Ironbound Whetstone | Breeze-Cut Talon, Quickstep Anklet, Marrow Torc | the same, Bloodglass Ampoule, Runebound Signet | fast claws that finish the weakened; she refuses the first gift from anyone and takes it the second time |
| Yugen Kagaribi (marksman, arcane, schemer, trader) | Starless Pearl | Starless Pearl, Blaze-Rod Bracer, Bastion Hoard-Ring (if found) | the same, Deepecho Signet, Fangspeaker Charm (if found) | shorter cooldowns, a stoked move after a run of shots; his greed (70) asks for the gold ring and keeps spares |
| Hinae Shirasuzu (support, radiant, sweet-natured, guard) | Daybreak Brooch | Daybreak Brooch, Lifebound Chime, Graveward Bell | the same, Vigil Lantern, Shulker-Shell Ward | heals and saves allies; her night watches feed the lantern and the bell; she gives away what others need more |
| Ambrose Nettlewick (controller, venom, eccentric genius, enchanter; he flies) | Bog Phial | Bog Phial, Slimebound Spool, Skyroad Pennant | the same, Deepecho Signet, Hushstone (if found) | slows, longer flights that grow the pennant; with the tech tree he wants the engineered Observer Lens |

### 7.8 Growing artifacts (8, smithing table, woken with 5 levels)

The owner's remark: "an artifact may become stronger of its in the artifact design, like an artifact that needs blood
to get stronger". Eight artifacts grow from what their wearer does. The rules, the same for all eight:

- **What counts**: each counts one kind of deed (the table), done by the hero **wearing it in a slot**, and only while
  that hero is alive. Carried in a backpack, a chest or a player's inventory it counts nothing (2). Its wearer's
  summons and adds do not feed it, and neither do sparring and agreed bouts (`HEROES_V2.md` 7.4): only real fights and
  real work. Nothing counted asks whether the foe or the ally was a player.
- **Steps**: step k is reached at the artifact's base count x 1, 3, 6, 10 and 15 (so its fifth step takes fifteen times
  its first). Each step adds one point of lines, in the order of its row.
- **Its cap**: two steps plus its upgrade level (three at I, four at II, five at III). A full artifact counts nothing
  more until a tinkerer widens it (9.8); the count stops at its cap's threshold, so nothing is stored unseen.
- **Strength**: 2 points and a minor passive at step 0, plus one point per step: 5, 6 and 7 points when full at I, II
  and III, the same as a greater artifact at the same upgrade level (6), earned by use instead of far materials.
- **It keeps its growth**: the count and step are the item's (`artifact_work`, 1.5). A death drop, a theft, a trade, a
  hand-over or a raid on the storehouse moves the artifact with its growth; a new wearer starts from where it was.
  Growth never decays and is never lost at a death. Only destroying the item (the void, a command) loses it.
- **Upgraded**: the count is kept and the cap widens by one step (9.8); an upgrade does not multiply a growing
  artifact's lines.
- **The AI** values the growth still to come by how fast the hero would feed it and how patient it is (10.2, 10.3).
  No hero seeks a fight, a mine or a field to feed an artifact: it grows from what the hero does anyway.
- **Equal treatment**: blood from a player or an NPC is the same blood; a worthy foe is a hero or a vanilla boss,
  whoever controls it.

| Artifact (id) | Recipe (S: base + addition) | Grows with (base count) | Step 0 | Each step adds, in order | Full at III | Passive | Upgrade (II / III) | Suits |
|---|---|---|---|---|---|---|---|---|
| Bloodglass Ampoule (`bloodglass_ampoule`): a stoppered ampoule of red glass that fills as its wearer draws blood | Spider-Eye Vial + Block of Redstone | `blood`: each point of damage its wearer deals to a living foe that bleeds (not undead, not `#factionclash:bloodless`, 5.3); 300 | attack +5%, drain +2% | drain +2%, attack +5%, health +5%, drain +2%, attack +5% | attack +15%, drain +6%, health +5% | **Open Vein**: an enemy that bleeds, which its hit leaves under half health, bleeds 1 physical a second for 3 s (once per 6 s per enemy). `on_hit` if `target_health_below` 50 and `target_bleeds`: `dot` 1/s 3 s | block of redstone 1 / 2 | striker, vanguard, skirmisher; heroes who raid |
| Veinseeker Geode (`veinseeker_geode`): a split geode whose crystals grow as its wearer mines | Amethyst Tuner + Block of Raw Iron | `ore`: each ore block (`#c:ores`, ancient debris included) its wearer breaks with a tool that harvests it; 48 | work speed +5%, poise +10% | work speed +5%, armour +1, work speed +5%, armour +1, health +5% | work speed +15%, poise +10%, armour +2, health +5% | **Geode Hum**: at its duty its vein following is one block wider (`surveyor`'s rule; with that talent at most two wider) | block of raw iron 1 / 2 | miners, cave explorers |
| Vigil Lantern (`vigil_lantern`): a small soul lantern that burns brighter for each night its wearer keeps watch | Clockwork Pin + Soul Lantern | `nights`: each night its wearer spends awake and out of a bed (on watch, on patrol, travelling or raiding), wearing it from dusk to dawn and alive at dawn; 1 | resolve +10%, health +5% | health +5%, resolve +10%, all resistances +4%, health +5%, all resistances +4% | health +15%, resolve +20%, all resistances +8% | **Lamplit Eyes**: Darkness never takes hold of it and Blindness on it lasts half as long (`ward_effect`) | soul lantern 1 / 2 | guards, scouts |
| Skyroad Pennant (`skyroad_pennant`): a long silk pennant that lengthens with every mile flown | Fletched Charm + Phantom Membrane | `flight`: each block its wearer travels in the air under its own power (flight or glide; not falls, knockback or riding); 1,500 | stamina +10%, speed +3% | stamina +10%, speed +3%, knockback resistance +10, resolve +10%, poise +10% | stamina +20%, speed +6%, knockback resistance +10, resolve +10%, poise +10% | **Road Memory**: when it lands after 20 or more blocks of flight or glide, Speed I for 4 s (once per 20 s). `on_land` 20: `effect` speed I 4 s | phantom membrane 2 / 4 | flyers, gliders |
| Graveward Bell (`graveward_bell`): a small iron bell that rings once for each undead its wearer lays to rest | Glistering Charm + Bell | `undead`: each undead (`#minecraft:undead`, undead heroes included) its wearer kills, by the killing blow or the last hit within 3 s of the death; 12 | heal power +8%, all resistances +4% | heal power +8%, health +5%, all resistances +4%, health +5%, recharge +5% | heal power +16%, all resistances +8%, health +10%, recharge +5% | **Graveside Peal**: its hits on undead deal 2 radiant more (once per second). `on_hit` if `target_is` undead: `damage` 2 radiant | gold ingot 4 / 8 | support, guards at night |
| Rival's Cord (`rivals_cord`): a braided red-and-white cord that gains a knot for every worthy foe its wearer outlasts | Knapped Flint + Lead | `worthy_foes`: each enemy hero or vanilla boss (the Wither, the Ender Dragon, an elder guardian, the warden) its wearer fought (hit it or was hit by it) that died or escaped while the wearer was still alive in that fight; once per foe per fight; 1 | attack +5%, poise +10% | critical damage +10%, attack +5%, resolve +10%, critical damage +10%, attack +5% | attack +15%, critical damage +20%, poise +10%, resolve +10% | **Measured**: when a fight with an enemy hero or a boss starts, +20% poise for 20 s. `on_fight_start` if `engaged_has` hero or boss: `stat` `poise` +20 20 s | lead 1 / 2 | strikers, duellists, chuunibyou and battle maniacs |
| Scarbark Amulet (`scarbark_amulet`): a disc of dark bark that thickens with every fight its wearer lives through at the edge | Riveted Buckle + Dark Oak Log | `scars`: each fight in which its wearer dropped under a third of its health and was alive when the fight ended (an escape counts); 1 | armour +1, health +5% | armour +1, all resistances +4%, health +5%, armour +1, knockback resistance +10 | armour +3, all resistances +4%, health +10%, knockback resistance +10 | **Old Wounds**: under a third of its health, +2 armour (the armour cap holds). `always` if `self_health_below` 33: `stat` `armor` +2 | dark oak log 4 / 8 | vanguard; escaping and last-stand heroes alike |
| Hearthroot Seed (`hearthroot_seed`): a living seed in a copper cage that sprouts as its wearer tends living things | Hale Locket + Moss Block | `tending`: each crop its wearer harvests, sapling it plants and pair of animals it breeds, at its duty; 64 | work speed +5%, health +5% | heal power +8%, work speed +5%, health +5%, work speed +5%, heal power +8% | work speed +15%, health +10%, heal power +16% | **Shared Harvest**: every 15 s in a fight, the ally with the lowest health share within 6 blocks (itself included) heals 2. `every` 15, form `ally_lowest` radius 6: `heal` 2 | moss block 2 / 4 | farmers, woodcutters, animal carers; support |

Thresholds at the base counts above: Bloodglass Ampoule 300 / 900 / 1,800 / 3,000 / 4,500 blood; Veinseeker Geode 48
/ 144 / 288 / 480 / 720 ores; Vigil Lantern, Rival's Cord and Scarbark Amulet 1 / 3 / 6 / 10 / 15; Skyroad Pennant
1,500 / 4,500 / 9,000 / 15,000 / 22,500 blocks; Graveward Bell 12 / 36 / 72 / 120 / 180 undead; Hearthroot Seed 64 /
192 / 384 / 640 / 960 (`heroes.artifacts.growthPercent` scales them all). How long each takes in a campaign is in 14,
case 10. One signature grows by the same rules: the Wither's Hollow Star counts `withering` (the Wither-effect damage
its wearer gave to enemies, never its adds'; base 60, three steps; `BOSS_HEROES.md` 4.13).

**Originality.** Known stacking items gain on kills or takedowns and lose part at their wearer's death, gain permanent
health from hits on champions, gain charges from enemies' spells nearby, or turn into another item when full. None of
the eight does any of these: each counts its own deed in steps of 1, 3, 6, 10 and 15 times a base, never loses growth,
and is widened by a tinkerer rather than transformed.

### 7.9 Engineered artifacts (3, the Artificer's Bench, with Artifice 2 research)

ROADMAP phase 6's tech tree gets artifacts of its own: devices built around vanilla redstone parts, made only at the
Artificer's Bench by a faction that has researched Artifice 2 (9.5). Until phase 6 exists they cannot be built (9.5).
They arrive awake from the bench (no anvil step).

| Artifact (id) | Bench build (base + materials; levels, time) | Stats | Passive or active | Upgrade (II / III) | Suits |
|---|---|---|---|---|---|
| Observer Lens (`observer_lens`) | Starless Pearl + 2 observers, 1 block of redstone, 1 diamond; 10 levels, 120 s | ability power +12%, recharge +10%, resolve +10% | P **Read the Tell**: when an enemy's move starts its wind-up with a ground mark that covers it, it takes 20% less damage from that move (once per 8 s; the mark, the wind-up and the cue are unchanged for everyone). `on_marked`: `stat` `damage_taken` -20 for that move | observer 2 / 4 | anyone who fights heroes; eccentric geniuses |
| Piston Bracer (`piston_bracer`) | Ironhide Girdle + 2 pistons, 1 block of redstone, 1 diamond; 10 levels, 120 s | attack +10%, armour +1, knockback resistance +10, health +5% | A **Ram Strike**: a piston-driven shove: enemies on a line 4 blocks long in front of it are knocked back about 6 blocks (class heavy: 0.8 s wind-up, a strip on the ground and a piston's cue; recovery 0.8 s; cooldown 20 s; used when an enemy is within 3 blocks in front). Form `line` 4, width 1: `knockback` 3 (the review lowered it from 4: 16.5's ceiling for an artifact's push). Its push lands at full strength and is aimed at edges like any move's (`HEROES_V2.md` 6.17, 4.4) | piston 2 / 4 | vanguard, grappler |
| Daylight Coil (`daylight_coil`) | Mender's Brazier + 2 daylight detectors, 1 block of redstone, 1 diamond; 10 levels, 120 s | health +10%, energy gain +16%, heal power +8% | P **Sun-Fed**: in daylight under the open sky, every 10 s in a fight it gains 4 energy and heals 2 (the healing ceiling holds). `every` 10 if `in_daylight`, `self`: `energy` 4, `heal` 2 | daylight detector 2 / 4 | support, ultimate-led kits |

---

## 8. Stacking rules

1. **One of each artifact per hero.** A second copy of an id is refused by the slot ("already worn"); a hero's backpack
   may hold any number. Two copies at different upgrade levels or growth steps are still one id.
2. **Families do not stack.** Passives with the same `unique` family fire only from the stronger artifact (the higher
   tier, then the higher budget at its upgrade level and growth, then the first slot): `primed` (Banked Embers,
   Rune-Struck), `last_ditch` (Braced Hide, Shell Up), `finisher` (Gale Finish; reserved for later finishers). The
   weaker artifact keeps its stat lines.
3. **Stat lines add up** across different artifacts (each at its upgrade level and growth step), then the build cap
   (4.3).
4. **Marks are global ids** (v1 19): the catalogue's marks (`ember`, `rune`, `rime`, `heat`, and the signatures'
   `hollow_brand`) are artifact marks no hero kit uses, checked by the validator, so an artifact never feeds a hero's
   own mark finisher by accident.
5. **Talents and specialties**: Steward's Seal raises tiers, it does not add a second copy of a talent; the base still
   counts the strongest copy of each talent (7.2 there). Prospector's Loupe is a virtual level under the work caps;
   Geode Hum widens vein following under `surveyor`'s own limit.
6. **Revival**: Returning Bloom, `revival_rite` and a hero's own `revival` multiply; the result is never under 30 s.
7. **Growth**: each growing artifact counts for itself; two different growing artifacts on one hero both count the
   same deed if both count it (none of the eight counts the same deed as another).

---

## 9. How a faction gets them

### 9.1 The crafting chain (the blacksmith, then the artificer, in the workshop)

| Step | Station | Inputs | Who (NPC) | Time (PROPOSED, before the worker's or hero's work speed) |
|---|---|---|---|---|
| Artificer's Sigil | crafting table | 1 gold ingot in the middle, 4 lapis lazuli on the edges, 4 redstone dust in the corners: 2 sigils | blacksmith, or the artificer once there is one | as any craft (`Crafting`) |
| Component | crafting table | 7.1 | the same | as any craft |
| Advanced, growing or greater artifact | **smithing table** | Sigil (template) + base + addition (7.2, 7.3, 7.8): a vanilla `smithing_transform` recipe whose result carries `dormant: true` and upgrade level I | the same | 5 s |
| Wake | **anvil** | the dormant artifact + 2 lapis lazuli; 5 levels (advanced and growing) or 10 (greater) from the faction's experience, as the blacksmith's repairs already pay theirs (`BlacksmithJob`, the enchanter's share first) | the same | 3 s; the anvil may wear as vanilla's does (12% a use), and the blacksmith forges a new one when it breaks, as today |
| Artificer's Bench | crafting table | 9.7 | blacksmith | as any craft; the builder places it |
| Upgrade to II or III | **Artificer's Bench** | 9.8 | the artificer, or a hero at the artificer duty | 9.8 |
| Engineered artifact | **Artificer's Bench**, with Artifice 2 | 7.9 | the same | 7.9 |

- A **player** does each step with the same vanilla screens: the crafting table, the smithing table (template, base,
  addition) and the anvil (a NeoForge `AnvilUpdateEvent` gives the woken artifact for the levels and lapis); and the
  bench's own screen for upgrades and engineered builds (9.7). Same inputs, same levels, same time.
- The **workshop gets a smithing table** (a new `BaseState` point beside the anvil and the crafting table, placed by the
  builder; ROADMAP phase 3's netherite row needs the same table and shares it). An existing base gets it the first time
  one of its heroes wishes for an advanced artifact (10.7). The bench comes later (9.7).
- The `forgemaster` talent's specialty applies to the whole chain, whoever works it (blacksmith or artificer):
  crafting faster, and a wake costs 1 / 2 / 3 levels less (at least 1), as for its repairs (`MOBILITY_AND_WORK.md`
  11.1). The new `tinkerer` talent applies to bench work (9.6).
- **Who works the chain**: the blacksmith, while the base has no artificer, and only when its own order book is empty
  (soldiers' iron comes before heroes' trinkets, 10.7); the artificer, once the base has one, as its whole job (9.6).

### 9.2 Materials

Every input is a vanilla item an NPC economy can come by player-like, sooner or later:

| Materials | How a faction gets them |
|---|---|
| flint, iron, gold, copper, lapis, redstone, coal, tuff, cobbled deepslate, string, leather, feathers, glass, paper, golden carrots, glistering melon, clocks, chains, tripwire hooks, iron, gold, coal and redstone blocks, raw iron blocks, spyglasses, cactus, bone blocks, spider eyes, wool, books, dark oak logs, leads (string and a slimeball) | today's miners, woodcutters, farmers, animal carers, butchers, smelters and the blacksmith; soldiers' kills at night (string, spider eyes, bones) |
| amethyst shards | miners and cave explorers who cut through a geode; traders (wandering traders sell some) |
| packed ice, ender pearls, moss blocks | traders: wandering traders sell packed ice and moss blocks, cleric villagers sell ender pearls (`TraderJob` trades like a player); endermen killed at night; moss from lush caves |
| soul lanterns | soul sand or soul soil (a Nether expedition, ROADMAP phase 3) with iron nuggets and a torch; until then, none: the Vigil Lantern waits for the Nether |
| phantom membranes | phantoms killed at night over a base whose people have not slept (soldiers and archers on night guard shoot them as players do) |
| bells | traders: armorer, toolsmith and weaponsmith villagers sell bells; villages found by scouts have one |
| diamonds | miners on the diamond layer (`MinePlanner`) |
| nautilus shells | fishermen's treasure catches (more with `angler_lord`), drowned |
| slime blocks | slimes killed in swamps or slime chunks (night guards, raids through swamps) |
| totems of undying, emeralds | woodland mansion and pillager outpost raids (ROADMAP phase 3); traders' surplus emeralds |
| breeze rods, wind charges | trial chambers (breezes; a trial-chamber raid comes with ROADMAP phase 3's dungeons) |
| blaze rods, ghast tears, netherite scrap | Nether expeditions and fortress raids (ROADMAP phase 3) |
| echo shards | ancient city expeditions (deep dark; later) |
| shulker shells, chorus flowers | End expeditions (ROADMAP phase 3's dragon raid and after) |
| wet sponges | elder guardians (an ocean monument raid; later) |
| observers, pistons, daylight detectors | crafted (observers and daylight detectors need nether quartz: a Nether expedition) |

So components, advanced and most growing artifacts arrive with today's economy, and greater artifacts arrive one by one
as the faction reaches the Nether, the trial chambers, the mansions and the End: the item system follows the ROADMAP's
world progression without needing it. A player can shortcut any of it.

### 9.3 Loot: rare finds and the rest

Rare finds are added to vanilla loot tables with global loot modifiers (`data/factionclash/loot_modifiers/`), so a
player opening the chest and an NPC looting it are the same:

| Rare find | Source (PROPOSED chances) |
|---|---|
| Hushstone | each ancient city chest 4% (about one per city) |
| Bastion Hoard-Ring | bastion treasure chest 25%, other bastion chests 2% |
| Chorus-Bloom Pendant | each end city chest 6% |
| Fangspeaker Charm | each woodland mansion chest 8%; an evoker's death 5% |
| Elderscale Lens | an elder guardian's death 50% |
| Steward's Seal | each pillager outpost chest 10% |
| any rare find, at random | a destroyed Hollow lair's heart 25% (for the raiding party, as v1's hero tokens), ROADMAP phase 3's dungeon reward chests (their design sets the chance) |

Other loot: **a dead hero's artifacts** (section 11); **a raided storehouse** (10.9); **a raided bench** with an
artifact in it (15.5); components are never loot (they are cheap to make).

### 9.4 Trade and diplomacy

- Villagers and wandering traders never buy or sell artifacts (no vanilla trade exists for them, and we add none).
  Traders buy the materials (9.2).
- Between factions: milestone 2's diplomacy (`HEROES_V2.md` 5.7) may carry an artifact in a tribute or a peace offer
  (a greedy leader values one at its war rating, 13); a hauler carries it on foot, as tributes do. Not before then.
- Within a faction, artifacts change hands by the heroes' own choices (10.4) and through the storehouse; a member
  player offers and asks like a hero (10.10).

### 9.5 Tech levels (ROADMAP phase 6)

The owner's remark: "some artifacts may nees tech level to be built and/or upgraded". ROADMAP phase 6 gives each
faction a tech tree researched at a lab, and the owner's rule there holds: research gates only what the mod adds, never
anything vanilla. This file asks phase 6 for one research line, **Artifice**, in two tiers (PROPOSED; phase 6's design
pass sets their research costs and times):

| Research | What it opens | What it needs |
|---|---|---|
| Artifice 1 | upgrade level III of advanced, growing and greater artifacts (9.8) | the lab's first tier |
| Artifice 2 | building the three engineered artifacts (7.9); upgrades II and III of rare finds, of engineered artifacts and of the Spirecore signature | Artifice 1 |

- **Never gated**: components, the Sigil, advanced, growing and greater artifacts, the wake, upgrade level II of
  advanced, growing and greater artifacts, a growing artifact's first four steps, the bench, the artificer and the
  `tinkerer` talent; using any artifact, rare finds and signatures included. (The first design's idea of gating the
  Sigil and the greater tier behind research is dropped: the owner asked for "some artifacts".)
- **Vanilla stays open**: the crafting table, smithing table and anvil work as always for everything vanilla, and
  nothing in this file makes a vanilla recipe or station harder.
- **Where it is checked**: only at the Artificer's Bench, since every gated step is a bench step. The bench reads the
  research of its user's faction: a player uses its own faction's research, an NPC its own faction's; a factionless
  player has none. The same rule for everyone.
- **Until phase 6 exists** (`heroes.artifacts.techGate`, PROPOSED): `research` (the default): no faction can have
  Artifice yet, so the gated steps stay locked; the bench shows them greyed ("needs Artifice 1: research comes with the
  faction labs") and no hero wishes for them (10.7). `open`: tech requirements are ignored, for operators and tests.
  No task in this file waits for phase 6.
- **Kept when research is lost** (phase 6's open point of stolen or destroyed knowledge): an artifact already made or
  upgraded keeps what it has; only new gated steps stop.
- **For phase 6's design pass** (suggestions, not decisions): the researcher studies Artifice from books, lapis,
  amethyst and redstone, and Artifice 2 also from an observer, a piston and a daylight detector; a scout's report of an
  enemy hero wearing an engineered artifact could count as a sample. The artificer stays the one who uses the knowledge
  (as the engineer of phase 6 builds what research unlocks).

### 9.6 Tinkering: who

The owner's remark: "some npcs or hero with can tinker with artefacts may upgrade the artifact". Upgrades and
engineered builds are done at the Artificer's Bench (9.7). Who works it:

- **The artificer** (a new ordinary NPC role, `NpcRole.ARTIFICER`, job `ArtificerJob`; PROPOSED). It **grows out of the
  blacksmith**: when a base's workshop has its bench (9.7) and the base's blacksmith has reached NPC level 5, the
  director makes that blacksmith the artificer (a role change: it keeps its name, level, XP and affixes), and the
  blacksmith need re-opens and is filled as usual (recruitment or a retrained idle worker). If no blacksmith of the base
  reaches level 5 within 3 in-game days after the bench stands, the director recruits an artificer directly, as it
  hires an enchanter. One artificer per base with a bench. Its job: the whole artifact chain of 9.1 (Sigils,
  components, smithing, wakes), upgrades and engineered builds at the bench, and tending the bench while it works.
  Player-like: real materials from the storehouse, the faction's real experience (the repair reserve kept), real time.
  Its Diligence speeds its work as any worker's.
- **A hero at the artificer duty**: any hero may have the duty `artificer` (as any worker job); the roster gives it to
  heroes whose primary talent is the new **`tinkerer`**. Such a hero counts as its base's artificer (no blacksmith is
  promoted while it is there) and works the bench better (proficiency, `MOBILITY_AND_WORK.md` 10, and its specialty).
  A leader with `tinkerer` spends daylight at the bench (`HEROES_V2.md` 5.6).
- **A player**: anyone at the bench, with the same materials, levels and time. A player has no work speed, so the
  bench runs at its own pace for a player; a tinkerer tending it makes it faster, as a smelter tending a furnace does
  (`MOBILITY_AND_WORK.md` 12.3). That is the work rule for heroes and workers, not a fight rule.
- **Nobody else** tinkers: other NPCs never touch the bench; a hero without the duty wishes for an upgrade (10.7).

The `tinkerer` talent (PROPOSED; added to `HEROES_V2.md` 7.2 and `MOBILITY_AND_WORK.md` 11.1 when R5 builds it):

| Tier I / II / III | Base-wide (7.2's talent rule) | Specialty (the hero at the bench itself) |
|---|---|---|
| `tinkerer` | upgrades at the base cost 1 / 2 / 3 fewer levels (at least 1) and take x0.9 / 0.8 / 0.7 of their bench time | bench work 25 / 40 / 55% faster (at most twice the bench's own pace, as furnaces); each upgrade it finishes hands back one unit of the artifact's own upgrade material 15 / 25 / 35% of the time (an extra roll, under `MOBILITY_AND_WORK.md` 13's 75% cap) |

### 9.7 The Artificer's Bench

A new block (`factionclash:artificers_bench`), the workshop's tinkering station (PROPOSED):

- **Recipe** (crafting table): iron ingot, block of lapis lazuli, iron ingot over amethyst shard, smithing table,
  amethyst shard over three polished deepslate. Everything in it comes with today's economy.
- **Where**: a new `BaseState` workshop point beside the smithing table and the anvil, placed by the builder. The
  capital gets it when the faction holds two or more awake advanced or greater artifacts and at least one hero lives
  there; any other base gets one when three or more heroes live there.
- **Its screen**: one artifact slot, three material slots, the cost in levels and the time, and a "Begin" button; steps
  the user's faction lacks the research for are shown greyed with what they need (9.5). The bench offers what the
  artifact in it can become: its next upgrade level, or, for the right base, an engineered build.
- **How it works** (like a brewing stand): "Begin" takes the materials and the levels at once (a player's own levels;
  an NPC's from the faction's experience), locks the artifact in, and the bench works for the time shown, by itself.
  A tinkerer who tends it (within 4 blocks, at its job) moves the progress on faster by its work speed, as furnace
  tending does (`MOBILITY_AND_WORK.md` 12.3). When it is done, the artifact is in the slot at its new level.
- **No failure** (PROPOSED): an upgrade or a build always succeeds; its price is the cost. Item upgrades that can fail,
  downgrade or destroy the item are a well-known rule of other games, and nothing in Minecraft's own crafting fails.
- **Broken mid-work** (a raid, an explosion, a player's pickaxe): the bench drops as an item and the artifact drops
  unchanged; the materials and levels already spent are lost. A bench with an artifact in it is loot for raiders, as a
  chest is (15.5).
- **Sounds**: `block.smithing_table.use` at "Begin", a soft `block.amethyst_block.resonate` while it works,
  `block.anvil.use` with `entity.player.levelup` (quiet) when it is done (12.6).

### 9.8 Upgrades: what and how much

- **Upgrade levels**: I (as made), II and III. Components are never upgraded (they are built into advanced artifacts);
  of the two signatures only Spirecore is, at a rare find's price and research, its bond untouched (Hollow Star grows
  instead: `BOSS_HEROES.md` 4.13 and 5.13). An artifact never levels with its wearer.
- **What an upgrade does** (PROPOSED): its stat lines x1.25 at II and x1.5 at III, each rounded half up to a whole
  number and clamped to its build cap (Kiln Heart: fire damage 12 / 15 / 18%, ability power 6 / 8 / 9%, fire resistance
  10 / 13 / 15%; Blackforge Girdle's health 15 / 19 / 20%, the cap). At III its passive or active is **tuned**: a
  cooldown a fifth shorter (never under its floor); a passive without a cooldown, its Amounts a fifth larger (within its
  caps; Returning Bloom 30% to 36% sooner). Hooks (Assayer's Eye, Office, Settled Weight, Geode Hum) are not tuned.
  Tuning never changes a class, a wind-up, a recovery, a telegraph, a stun's length or anything of 4.4; an artifact file
  may name its own tuning within those rules (16.1).
- **A growing artifact's upgrade** widens its cap by one growth step (three steps at I, four at II, five at III) and
  keeps its count; its lines are not multiplied (7.8).
- **The cost**: a common part, the artifact's own material (its "Upgrade" column in section 7), levels and bench time
  (PROPOSED):

| Upgrade | Common part | Own material | Levels | Bench time | Research |
|---|---|---|---|---|---|
| advanced or growing, to II | 2 lapis lazuli | the first count | 5 | 60 s | - |
| advanced or growing, to III | 4 lapis lazuli, 1 diamond | the second count | 8 | 90 s | Artifice 1 |
| greater, to II | 4 lapis lazuli, 1 diamond | the first count | 10 | 120 s | - |
| greater, to III | 4 lapis lazuli, 1 netherite scrap | the second count | 15 | 180 s | Artifice 1 |
| rare find, engineered or Spirecore, to II | 4 lapis lazuli, 1 diamond | the first count | 10 | 120 s | Artifice 2 |
| rare find, engineered or Spirecore, to III | 4 lapis lazuli, 1 netherite scrap | the second count | 15 | 180 s | Artifice 2 |

- **A smithing step starts again at I**: an upgraded advanced artifact smithed into its greater one gives a greater
  artifact at upgrade level I (the recipe's result sets `artifact_work` afresh; the tooltip of an upgraded base says
  so). The workshop never upgrades an artifact it plans to smith further (10.7).
- **How fast it goes in a campaign**: an artificer does at most two bench, smithing or wake jobs a day (10.7), each
  needing the materials above the faction's reserves; 14 case 9 gives the rate.

---

## 10. The AI: each hero's own choice

The owner's answer of 2026-11-18: "heroes decide if they want to use, replace, or leave for someone else the
artifact". The first design's faction planner, which handed out builds in the order the leader's personality set, is
gone. Each hero now chooses for itself what it wears, by the same rules for every hero (pure logic in
`logic/hero/ArtifactChoice`, unit-tested, no Minecraft classes). The faction's own part is what its workshop makes
(`logic/hero/ArtifactWorkshop`, 10.7): ordinary NPCs work for the faction, heroes choose. Both see only what the
faction knows: its own heroes, stores and station records, and what its scouts reported of enemies (the mod's
player-like knowledge rule). No rule here reads whether anyone is a player: a member player who offers or asks is
weighed exactly like a hero who does (10.10).

### 10.1 A hero's profile

Read from its definition once per load: role, element, basic-attack style (melee or ranged), duty, `when_losing`, and
kit tags computed from its moves and passives: `heals` (any heal or shield on allies), `summons` (summon, `spawn_add`),
`control` (stun, freeze, slowness, tether, silence), `moves` (6 or more moves), `ultimate_led` (an ultimate with
`charge_rate` 120 or more, or both ultimates), `flyer` / `glider` (mobility primary), `basic_attacks` (strikers,
skirmishers, marksmen whose kit has `on_hit` passives). A hero file may add `artifacts.prefer` and `artifacts.avoid`
(16.6). Read at each choice: its level (so its open slots, 3), what it wears and carries, and its **feed rates** for the
eight growing artifacts (10.2).

### 10.2 What an artifact is worth to a hero

```
value(artifact, hero) = Σ over its lines   role_weight[role][line] x tag_bonus x points(line) x headroom(line)
                      + passive_value                 2 points x fit: 1 when its suits tags match the hero, 0.5 some, 0
                      + growth_value                  a growing artifact: the points it would still add up to its cap
                                                      x feed(hero) x patience(hero)   (10.3)
headroom(line) = the share of that line that still fits under its build cap given the hero's other worn artifacts
points(line)   = the artifact's own numbers at its upgrade level and growth step (the same for every hero)
```

`feed(hero)` is how surely the hero would feed a growing artifact: 1 when its duty or kit feeds it every day (a guard
or scout for the Vigil Lantern; a miner or cave explorer for the Veinseeker Geode; a flyer or glider for the Skyroad
Pennant; a farmer, woodcutter or animal carer for the Hearthroot Seed; a hero whose `behaviour.raids` or leader rule
sends it raiding for the Bloodglass Ampoule, the Rival's Cord and the Scarbark Amulet; a guard at a base that sees
undead at night for the Graveward Bell), 0.5 when it would sometimes (a hero who fights only in defence), 0 when never
(a walker for the Skyroad Pennant).

Role weights per point (PROPOSED; 1.0 is "as useful as a point of health"):

| Line | vanguard | striker | skirmisher | marksman | support | controller | summoner |
|---|---|---|---|---|---|---|---|
| health | 1.4 | 1.0 | 0.9 | 0.8 | 1.0 | 1.0 | 1.0 |
| attack | 0.6 | 1.4 | 1.3 | 1.2 | 0.2 | 0.3 | 0.3 |
| ability power | 0.5 | 0.8 | 0.8 | 1.0 | 1.2 | 1.3 | 1.2 |
| armour | 1.3 | 0.8 | 0.6 | 0.5 | 0.6 | 0.7 | 0.6 |
| recharge | 0.7 | 0.9 | 1.0 | 0.9 | 1.2 | 1.3 | 1.4 |
| attack speed | 0.5 | 1.2 | 1.3 | 1.4 | 0.2 | 0.3 | 0.3 |
| speed | 0.6 | 0.9 | 1.3 | 0.8 | 0.7 | 0.7 | 0.6 |
| energy gain | 0.7 | 0.8 | 0.8 | 0.9 | 1.1 | 1.1 | 1.2 |
| poise | 1.3 | 0.9 | 0.6 | 0.5 | 0.6 | 0.6 | 0.6 |
| element damage (own element only) | 0.6 | 1.1 | 1.1 | 1.2 | 0.8 | 1.1 | 1.0 |
| one resistance | 0.3 (1.2 against an element its scouts saw on enemy heroes) | same | same | same | same | same | same |
| all resistances | 1.1 | 0.8 | 0.7 | 0.7 | 0.8 | 0.8 | 0.8 |
| drain | 1.0 | 1.3 | 1.1 | 0.9 | 0.3 | 0.4 | 0.4 |
| heal power | 0.3 | 0.1 | 0.1 | 0.1 | 1.5 | 0.4 | 0.4 |
| resolve | 1.0 | 0.9 | 0.9 | 0.6 | 0.6 | 0.7 | 0.6 |
| knockback resistance (worth more since pushes land at full strength, `HEROES_V2.md` 6.17) | 1.2 | 0.9 | 0.6 | 0.5 | 0.6 | 0.6 | 0.6 |
| stamina | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 |
| work speed | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 |
| critical damage | 0.4 | 1.0 | 1.0 | 0.7 | 0.1 | 0.1 | 0.1 |

Tag bonuses: heal power x0 without `heals`; stamina x4 for `flyer` and `glider`, x2 for the escaping kind and a scout's
duty; work speed x2 for a gatherer duty that matches Prospector's Loupe, the Veinseeker Geode or the Hearthroot Seed,
and x2 more with greed 60 or more or a leader whose economy focus fits; element damage x0 for another element (the
focus's other lines still count).

### 10.3 Personality, temperament and relationships

The hero's own traits (raw 0-100 read as 0-1, as the move selector's posture: `HEROES_V2.md` 6.3) tilt what it likes:

| Trait | Lines, passives and artifacts it favours | Factor |
|---|---|---|
| caution | health, armour, resistances, resolve, `last_ditch` passives, the Scarbark Amulet | 1 + 0.6 (c - 0.5) |
| aggression | attack, ability power, attack speed, element damage, drain, critical damage, finishers, the Bloodglass Ampoule | 1 + 0.6 (a - 0.5) |
| sociability | passives that help allies (Morning Grace, Soothing Smoke, Mercy Toll, Shared Harvest) | 1 + 0.6 (s - 0.5) |
| greed | work speed, Prospector's Loupe, Bastion Hoard-Ring, Veinseeker Geode | 1 + 0.8 (g - 0.5) |
| ambition | greater, rare, engineered and signature tiers; upgrade levels | 1 + 0.4 (m - 0.5) |
| volatility | noise | sigma = 0.2 x v, as the director's; drawn once per hero and artifact each in-game day (a mood, not a coin toss at every choice), so a whim lasts a day |

**Patience** weighs growth still to come: `patience = clamp(0.5 + 0.5 (c - 0.5) - 0.5 (v - 0.5) + 0.3 (m - 0.5),
0.2, 1)`. A careful, steady, ambitious hero grows an artifact; a whimsical one wants what is strong today.

**Temperament flavour** (x1.15 on its favourites, and its rule; PROPOSED): `glutton` the torcs and the Bloodglass
Ampoule; `merchant` the Hoard-Ring, the Loupe and the Geode; `chuunibyou` actives, rare finds and the Rival's Cord;
`battle_maniac` the Rival's Cord and the Bloodglass Ampoule; `gruff_veteran` the Scarbark Amulet; `stoic` the Vigil
Lantern; `eccentric_genius` engineered artifacts and upgrades; `perfectionist` upgrades of what it wears before
anything new (its wishes, 10.7) and half the swaps (hysteresis x1.5); `big_sibling` and `sweet_natured` ally help, and
they pass on more readily (generosity +0.15); `airhead` twice the noise and twice the swaps (hysteresis x0.5);
`proud_noble` refuses components from level 10 ("beneath me"); `tsundere` refuses the first offer from each giver and
accepts the second ("I-it's not like I wanted it!"); `lone_wolf` never asks and never passes on, and takes from the
store only what nobody else claims; `melancholic` never gives away an artifact a fallen friend wore; `yandere`, from
milestone 3, gives its favoured anything it asks for.

**Relationships**: `rel(hero, other)` is +1 for a friend, sibling, mentor, student or admirer, -1 for a rival; a sworn
enemy's gifts and asks are refused and the hero never gives to it. Until milestone 3 only the authored `relationships`
of the hero files count (`HEROES_V2.md` 12.16); from milestone 3 a bond of 20 or more counts as a friend. For a member
player, `rel` is the hero's opinion of that player (milestone 2's standing; 0 until it exists): the same scale, read the
same way.

```
want(hero, artifact, from) = value(artifact, hero) x like(hero, artifact) x giver(hero, from) x (1 + noise)
like  = the trait factors above x temperament flavour x prefer (x1.3) or avoid (x0) of the hero file (16.6)
giver = 1 + 0.2 x rel(hero, from)          from: who offers it; 1 for the store, a find or the hero's own backpack
```

### 10.4 The choices: use, swap, pass on, ask, refuse

At each choice moment (10.6) a hero runs these in order. Every threshold is PROPOSED.

1. **Use.** A free slot takes the artifact with the highest want among what the hero carries and what the store at the
   base holds unclaimed, if that want is at least 0.5 point. Taking from the store is a **claim** in the station's
   ledger (12.3); through the ledger it may also claim from another base's store, and a hauler brings it (10.8).
2. **Swap** ("replace"). A worn artifact gives way to one it carries or can claim when the build's value rises by at
   least its **hysteresis**: `10% x (1 + (c - 0.5) - (v - 0.5))` of its build value, never under 3% (about 4-17% across
   the temperaments' defaults; a perfectionist x1.5, up to about 23%; an airhead x0.5, held at the 3% floor). What comes
   off goes to the store at a base, or the backpack in the field. A hero swaps at most once per in-game day unless
   something new reached it (a slot opened, an artifact entered the store or its backpack, an offer, an upgrade or a
   growth step): with the daily noise above, this is what keeps an airhead from trading back and forth.
3. **Pass on** ("leave it for someone else"). For an artifact it carries or wears that another hero of its faction it
   knows of wants at least 1.5 times as much (estimated with the same scoring: the faction knows its own heroes), it
   gives it if its **generosity** toward that hero is at least 0.5:
   `G = 0.5 + 0.6 (s - 0.5) - 0.8 (g - 0.5) + 0.3 (h - 0.5) + 0.3 rel(hero, other) + wish (10.5)`.
   A carried spare it does not want goes to the store at the next base; a greedy hero (greed 70 or more) keeps up to
   two spares in its backpack instead.
4. **Ask.** For an artifact another hero wears or carries that it wants at least 1.5 times as much as that hero does
   (plus its own hysteresis), it asks if its **boldness** is at least 0.5:
   `B = 0.5 + 0.4 (m - 0.5) + 0.4 (g - 0.5) + 0.4 (s - 0.5) + 0.3 rel(hero, other) + wish`.
   The asked hero answers as in 3, with the asker's want. A lone wolf (B about 0.26) never asks; a merchant (about
   0.78) asks often; a sweet-natured hero (about 0.52) asks now and then. Asks happen face to face (both out of a fight
   at the same base, or within 8 blocks in the field) or through the station's ledger for a hero at another base, who
   answers at its next choice; a hauler then carries what it gives (10.8).
5. **Refuse.** Anything offered (a hero's gift, a member player's offer, a hauler's delivery, a leader's wish) is
   weighed as a use or a swap. It is **refused** when the hero would neither wear it nor keep it: a want under 0.5
   point, an `avoid` id, a sworn enemy's gift, or its temperament's rule (a tsundere's first offer, a proud noble's
   component). It **keeps** what it would wear soon (a slot opening within two levels) or, with greed 70 or more, any
   artifact. A refused artifact goes back to its giver's hands (a player's inventory, or its feet if full; a hero's
   backpack), or to the store when the giver is gone. Nothing is ever destroyed by a refusal.

**Two heroes claim the same stored artifact.** Each gives way if `D x want(other) >= want(self)`, with its
**deference** `D = 0.5 + 0.6 (s - 0.5) + 0.4 (h - 0.5) - 0.8 (g - 0.5) - 0.4 (m - 0.5) + 0.3 rel + wish`; if both
insist, the earlier claim in the station's ledger takes it (first asked, first served), and the other posts a wish to
the workshop (10.7). From milestone 3 the loser's opinion of the winner drops a little (a grudge, never a fight).

**Lent artifacts.** What a hero or a member player hands over is remembered as **lent** by that giver (`from`, 17.2)
until it is given back or its wearer dies. When the giver asks for it back, a hero with honour 50 or more returns it at
once ("I keep my word"); any other weighs the ask as in 4.

**Signatures stay with their own hero** (BOSS_HEROES 4.13 and 5.13): on its own hero a signature's bond makes it hers
above anything (her want for it counts as unbounded), so she never swaps it out, passes it on or gives it to an ask,
and no leader's wish moves it: a hoarding leader cannot take the Wither's Hollow Star from her. A signature that comes
back to the store is claimed by its own hero first while she is in the faction. Worn by another hero of her faction
(it picked it up after her death), it counts as **lent by her** (`BOSS_HEROES.md` 4.13): she asks for it back at her
next choice moment, a holder with honour 50 or more returns it at once, and any other weighs the ask as in 4, helped by
the leader's particular wish to return it (10.5). Worn by an enemy hero, it is an artifact like any other, and its own
hero takes that hero as her preferred target in a fight (`BOSS_HEROES.md` 4.13). Two copies of one instance are never
both worn (15.1).

**Worked example** (a neutral leader's faction; Appendix A defaults): Hinae (sweet-natured: s 90, g 25, h 75) holds a
Hale Locket she does not wear that Ilsabet (righteous) wants twice as much: G = 0.5 + 0.24 + 0.2 + 0.08 = 1.02, she
gives it. Yugen (schemer: s 45, g 70, h 15) holds a spare Kiln Heart that Benika wants: G = 0.5 - 0.03 - 0.16 - 0.1 =
0.21, he keeps it; Benika (battle maniac: m 70, g 25, s 55) asks: B = 0.5 + 0.08 - 0.1 + 0.02 = 0.50, she asks; he
answers with the same G and still keeps it, unless the leader wished it to her (10.5: Yugen's compliance is 0.06, so
the wish barely moves him). Benika posts a wish and the workshop makes her one.

### 10.5 The leader's wishes

The first design's distribution policies are now **wishes**: requests from the leader that each hero weighs. The
leader's personality (`LeaderBiases`, the same raw traits) picks its standing wish:

| Wish | When | Who it favours | Strength |
|---|---|---|---|
| "the best to me" | greed 65 or more, or a `tyrant` | the leader, then the heroes by war power | 0.6 |
| "the best to our strongest" | ambition 65 or more (and not the first) | the hero with the highest war power | 0.6 |
| "to whoever has least" | otherwise (the neutral leader) | the hero with the lowest artifact rating | 0.4 |

- **How a hero weighs it**: `wish = strength x compliance(hero)` is added to its generosity, boldness and deference
  toward the favoured hero, and taken away when it would act against the wish (asking the favoured hero for its
  artifact, insisting on a claim the favoured hero also made). Compliance:
  `clamp(0.5 + 0.6 (l - 0.5) + 0.4 (h - 0.5) - 0.4 (g - 0.5) - 0.3 (m - 0.5) + 0.3 rel(hero, leader), 0, 1)`.
  A loyal, honourable hero mostly follows it (Hinae about 0.9); a greedy, ambitious one mostly does not (a schemer about
  0.06). Nobody is forced: a hoarding leader whose heroes are greedy simply gets less than it wishes.
- **Particular wishes**: the leader may also wish one artifact onto one hero (strength 0.5): a resistance to an element
  after a scout report names an enemy hero of that element, a stamina artifact for a flyer sent on a far raid, a
  signature back to its own hero.
- **The leader is a hero too**: its own choices follow 10.4; its wishes do not bind it. It never wishes a signature
  away from its own hero, and nothing is taken from anyone in a fight.
- **The workshop is different**: what the faction makes is the leader's order to ordinary NPCs (10.7), who work for the
  faction as they always have.

### 10.6 When and where heroes choose

- **Choice moments**: at dawn at a base; on arriving at a base; when a slot opens (a level-up); when an artifact enters
  the store at its base or an offer or ask reaches it; on returning from a revival or an escape. At most one round of
  choices per hero every 2 minutes (`heroes.artifacts.choiceSeconds`, PROPOSED), so nobody shuffles.
- **At a base, out of a fight**: when a hero has been out of a fight for 30 s at a base of its faction, it carries out
  its choices: it goes to the storehouse as for a gear upgrade (`Job.upgradeEquipment`'s path,
  `FactionStorage.extractExact`), attunes each new artifact (2 s each, 3), and stores what it took off; it hands over
  or receives face to face (12.7's `artifact.give`).
- **Never mid-fight.** No choice is carried out and no attune starts in a fight; one in progress stops when a fight
  starts.
- **In the field** it only fills an empty slot from what it carries, after 30 s out of a fight; it passes on or asks
  only with a hero of its faction within 8 blocks, out of a fight. Swaps wait for a base.
- **A revived hero** comes back with empty slots (its artifacts dropped where it died) and claims from the store at its
  first visit.
- **A dormant hero** (its definition is broken: 3.4 there) gets nothing from what it wears and makes no choices; at its
  base its artifacts go into the store, for the faction's working heroes to claim.

### 10.7 What the workshop makes

- **Wishes**: each hero keeps up to two wishes in the station's ledger: an artifact it would wear that nobody holds
  unclaimed and the faction could make (the recipe tree of 7.6, the bench of 7.9 with the research it has), or an
  upgrade of one it wears. A perfectionist puts its upgrades first. Nobody wishes for a rare find (they are found), for
  a step its faction lacks the research for (9.5), or for an upgrade of an artifact it plans to smith further.
- **The order** is the leader's (the workshop's workers are ordinary NPCs): "the best to me" takes the leader's wishes
  first, then by war power; "the best to our strongest" the highest war power first; "to whoever has least" the hero
  with the lowest artifact rating first. For each wish in that order the workshop takes the cheapest next step toward
  it: a missing component, the smithing step when both inputs are held, the wake, the upgrade.
- **Gates** (PROPOSED):
  - without an artificer, the blacksmith takes artifact orders only when its own order book is empty (tools, weapons,
    armour, repairs and a broken anvil come first); the artificer's whole job is artifacts;
  - materials are spent only above the faction's reserves (the logistics shortage rules) plus an artifact reserve: 9
    gold ingots, 8 lapis lazuli and 2 diamonds are never spent on artifacts;
  - a wake or an upgrade needs its levels in the faction's experience with 5 levels left over for repairs;
  - per base and in-game day: the blacksmith does at most one smithing step or wake (`craftsPerDay` 1; +1 with a
    `craft` or `military` economy focus); an artificer at most two bench, smithing or wake jobs
    (`artificerJobsPerDay` 2); components when the smith is idle, at most two of each in the store;
  - no element focus for an element none of its heroes has, and nothing the store already holds unclaimed.

### 10.8 Hauling

An artifact a hero at another base asked for or claimed, or that the workshop made for a wish there, becomes a hauler
delivery (`Logistics`), ahead of bulk goods. A hero already travelling there may take it instead. Haulers carry
artifacts as any goods: no effect, no growth, and dropped if the hauler dies. The receiving hero still decides (10.4).

### 10.9 Loot, recovery and raids

- **Picking up**: after a fight (no engaged enemy for 10 s), the heroes and soldiers of the side holding the ground
  pick up artifact items within 16 blocks before any other loot; an artifact never stays on the ground for want of
  room (the least valuable backpack stack is dropped to make room). Soldiers carry them home to the storehouse. A hero
  that picks one up decides (10.4): it wears it in an empty slot if it wants it, else carries it home to the store (a
  greedy hero may keep two spares). This is how a faction recovers its own hero's artifacts and how it steals an
  enemy's.
- **Recovery trips**: when the faction knows where one of its heroes fell (it fell at or near a base, or a witness
  brought the news home: the ROADMAP phase 1 knowledge rule) and nobody of its own has picked the drops up, the
  director may send the revived hero, or two soldiers when no hero is free, to fetch them, unless the spot is within 48
  blocks of a known enemy base. Whether an enemy already took them, the faction learns only by going there.
- **Raids**: raiders looting a storehouse (`GuardJob` `RAID_LOOT`, after the heart falls) take artifacts first, and
  break a bench that holds one; a greedy hero's "storehouse first" raid goal (`MOBILITY_AND_WORK.md` 5.2) does the same
  while the base still stands.
- Traders never sell artifacts; workers never deposit them anywhere but the storehouse.

### 10.10 Member players

What a member player of the hero's faction can do now (the owner's answer 2 makes the hero the one who decides):

- **Offer**: sneak-use an artifact on a hero of its faction, or put it in the Offer slot of the hero sheet (out of a
  fight, within 4 blocks). The hero decides at once, by 10.4: it wears it (in a free slot, or swapping one out, which
  it hands to the player: a trade), keeps it in its backpack, or refuses and hands it back. It says a line either way
  (`artifact_gift` or `artifact_refuse`, 16.6).
- **Ask**: the Ask button beside an artifact the hero wears or carries, with a choice of whom it is for: another hero of
  the faction, or "for me" (to carry or store). For another hero the asked hero answers exactly as it answers that
  hero's own ask (10.4: that hero's want against its own, its generosity toward that hero), with the player's `rel`
  added as a small help (+0.1 x rel to the generosity), and the player then carries it over or a hauler does; the
  receiving hero still decides (its own refusal returns it to the player). "For me": the hero gives only what it does
  not want (a spare, or a want under 0.5 point) and what that player lent it (always, with honour 50 or more). So a
  player moves artifacts between heroes only where a hero would have moved them, and never strips a hero of what it
  wants: the owner's "heroes decide". (The first revision let a generous hero hand anything to a player who asked;
  the review of 2026-11-18 closed that, since it made an Ask a pin by another name.)
- **The storehouse**: a member player may put artifacts in the store, where heroes claim them, and may take from it,
  as from any container.
- **No pins**: the first design's pinned slots are gone. To move an artifact from one hero to another, a player asks the
  first for the second and offers it to the second; each decides.
- **The same rules for an NPC**: a hero offering or asking goes through the same 10.4; only the screen and the chat
  lines are the player's.

---

## 11. Death drops and theft

- **When a hero dies, every artifact it wears drops where it died**, as a player's inventory does (each with a small
  random push), together with its backpack (artifacts in it included) and its non-kit gear (v1 25.4). Kit items still
  vanish and return (they are never artifacts: the validator refuses an artifact in `gear`). This holds for every
  death, whoever or whatever killed it, and for leaders. An **escape** is not a death: an escaping hero keeps
  everything.
- **The drops keep what they are**: an artifact's upgrade level and growth are on the item (1.5) and go with it, to
  whoever picks it up. A dead hero's lent marks are cleared.
- **The drops never despawn** (`ItemEntity.setUnlimitedLifetime`, saved by vanilla; set in `EntityJoinLevelEvent` on
  every item entity that carries an artifact, however it got there: a death, a toss, a broken chest or bench, a
  hopper). They do not burn, survive lava (they float up out of it, as netherite does), explosions and cactus. The void
  and commands (`/kill`, `/clear`) destroy them; a destroyed artifact is gone for good, growth and upgrades with it,
  except a signature (15.1).
- **Findable**: a dropped artifact gives off a small particle in its tier's colour every 2 s for players within 32
  blocks (presentation only; NPCs find items by their own pickup scan, 10.9).
- **Who gets them**: whoever picks them up: the side that holds the ground (10.9), a player, a passing hero of any
  faction, even a zombie (a vanilla mob that picks up an item keeps it and drops it when killed). The hero's own faction
  gets them back only by recovering them; if an enemy took them they are in that enemy's storehouse and on its heroes.
  A stolen growing artifact keeps growing for its new wearer from where it was.
- **The kill feed and chat**: the death message is unchanged (the planned hero kill feed). The hero's own faction is
  told what dropped only when it knows of the death ("Benika Akagane fell at (120, -340); her artifacts lie there: Kiln
  Heart II, Blackforge Girdle, Bloodglass Ampoule"); the killers are told what they picked up when they pick it up.
- **Tokens**: `BOSS_HEROES.md`'s hero tokens may reuse these drop rules (never despawning, fire- and blast-proof).

---

## 12. Showing them

### 12.1 The boss bar

After the phase pips, one small square per worn artifact in its tier's colour (presentation, `HEROES_V2.md` 6.1):
`★ Benika Akagane · Warlord of the Crimson Anvil  ◆◆◇ ⚡ ■■■■■`, the squares yellow, aqua, purple or gold. A player reads
at a glance how built a hero is; closed slots show nothing; upgrade levels, growth and names are on the sheet.

### 12.2 The hero sheet

Right-clicking a hero (`NpcInspector`, the hero sheet of `HEROES_V2.md` 7.4): an "Artifacts" section with each open
slot's artifact (its name with its upgrade level, its tier, its lines with "(capped)" where the build caps one, the
passive or active with its numbers and cooldown, a growth bar for a growing artifact: "step 2 of 3, 1,240 / 1,800
blood", and "lent by <name>" where so), each closed slot with the level that opens it, the build's totals, "In the
backpack: ...", and the hero's wishes. Member players get the Offer slot and the Ask buttons (10.10).

### 12.3 The station

The reporting station's Heroes section adds each hero's artifact count and rating; a ledger: "Artifacts in store: Kiln
Heart, Hale Locket x2, ...", the claims and asks waiting, each hero's wishes, and the workshop's current job
("Artificer: upgrading Blackforge Girdle to II for Benika Akagane, 1:20 left").

### 12.4 Kit sheets for the owner

`herogen` writes an "ARTIFACTS" section in each kit sheet: what the hero would choose for itself at levels 1, 10 and
20 (one, three and five slots) from the full catalogue, and why (its top lines and passives, and the temperament rule
that shapes its giving and asking). `kits/artifacts_overview.txt` describes the whole system and catalogue in the
sheets' voice (kit version 3).

### 12.5 Chat and lines

Faction chat (to its member players): "Benika Akagane now wears Blackforge Girdle", "Hinae Shirasuzu gave Hale Locket
to Ilsabet Frostmere", "Natsuho Komezaki refused Ironbound Whetstone", "Yugen Kagaribi asked Benika Akagane for Kiln
Heart", "The artificer upgraded Kiln Heart to II (5 levels)", "The blacksmith woke a Ghast-Tear Torc (10 levels)",
"Bloodglass Ampoule (Benika Akagane) reached step 3", the death line of 11. Line events (16.6): `artifact` (it puts on
a greater, rare, engineered or signature artifact, or a III), `artifact_gift` (it accepts an offer: "Heh. This one
bites back."), `artifact_refuse` ("Not my style."), `artifact_ask` ("That would suit me better, wouldn't it?"), all
rate-limited; the temperament banks fill them in.

### 12.6 Sounds (`SOUND.md` rules: vanilla by id where natural, generated otherwise)

A new sound set `herosrc/sfx/artifact.sfx.json` with events `factionclash:artifact.<name>` (a new namespace beside
`hero.cue`, added to `SOUND.md` section 3 when built). Vanilla ids are checked by `sfxgen check` when the set is built.

| Moment | Sound |
|---|---|
| Attune (equip) | `block.amethyst_block.chime`, pitched by tier (component 0.8 to signature 1.4) |
| Wake at the anvil | `block.anvil.use` with `block.enchantment_table.use` |
| The bench begins, works, finishes | `block.smithing_table.use`; a soft `block.amethyst_block.resonate` loop; `block.anvil.use` with `entity.player.levelup` (quiet) |
| A growth step | the artifact's own: Bloodglass Ampoule `item.bottle.fill`, Veinseeker Geode `block.amethyst_cluster.break`, Vigil Lantern `block.soul_sand.place` (quiet) with `block.fire.ambient`, Skyroad Pennant `item.elytra.flying` (short), Graveward Bell `block.bell.use` (quiet), Rival's Cord `item.armor.equip_leather`, Scarbark Amulet `block.wood.place`, Hearthroot Seed `item.bone_meal.use` |
| A hand-over; a refusal | `entity.item.pickup`; generated `artifact.refuse` (a soft falling two-note) |
| An artifact drops at a death | generated `artifact.scatter` (a short falling chime, quiet) |
| Passive procs | the artifact's own: Banked Embers `item.firecharge.use`, Hoarfrost `entity.player.hurt_freeze`, Arc Jump generated `artifact.arc`, Withering Touch `entity.wither.shoot` (quiet), Racing Thoughts and Rising Echo `block.amethyst_block.chime`, Morning Grace `block.beacon.power_select`, Honed `entity.player.attack.crit`, Rune-Struck `entity.player.attack.strong`, Braced Hide `item.armor.equip_netherite`, Prickle `enchant.thorns.hit`, Kindred Light `block.respawn_anchor.charge`, Snag `block.tripwire.attach`, Fair Wind `entity.wind_charge.wind_burst`, Stoked Heat `entity.blaze.shoot` (quiet), Gale Finish `entity.player.attack.sweep`, Mercy Toll `item.totem.use` (quiet), Shell Up `entity.shulker.close`, Swell `block.conduit.activate`, Deep Quiet `block.sculk_shrieker.shriek` (quiet), Fatigue Gaze `entity.elder_guardian.curse`, Returning Bloom `item.chorus_fruit.teleport` at the revival, Open Vein `entity.player.hurt_sweet_berry_bush` (quiet), Graveside Peal `block.bell.resonate` (quiet), Measured `item.armor.equip_chain`, Old Wounds `block.wood.hit`, Shared Harvest `block.azalea_leaves.place`, Road Memory `entity.phantom.flap` (quiet), Read the Tell `block.comparator.click`, Sun-Fed `block.beacon.ambient` (quiet) |
| Actives (wind-up cue from the class, as every move) | Downpull `item.mace.smash_ground`; Gluefall `block.slime_block.place` and `entity.slime.squish`; Updraft `entity.wind_charge.wind_burst`; Fang Line `entity.evoker.prepare_attack` then `entity.evoker_fangs.attack`; Ram Strike `block.piston.extend` |

### 12.7 Animations

Four core clips (`_core` library, task R6): `artifact.attune` (2 s, the hero holds the artifact to its chest, eyes
closed a moment), `artifact.raise` (an area move: raise both hands, bring them down; its `hit` marker on the release),
`artifact.cast` (a point forward; heavy) and `artifact.give` (1 s, the hero holds an item out with one hand: a
hand-over, a refusal handing it back, a gift to a hero). Style libraries may override them like any core clip.
Artifacts are **not drawn on the hero's model**: no new parts, so the one-body rule and the cube budget stay as they
are; the bar, the sheet and the glint say what a hero wears. A signature artifact may name a `worn_part` from the parts
library for its own hero only (16.4), because it is part of that hero's look.

---

## 13. War power and the war council

A hero's war power (`HEROES_V2.md` 8.1) gets one factor, now without attunement (fixed strength):

```
rating  = min(warRatingCap, Σ over worn artifacts of its value)
value   = component 0.03, advanced 0.07, greater and engineered 0.09, rare find 0.10, signature 0.12;
          + 0.02 per upgrade level above I; a growing artifact 0.04 + 0.015 per growth step (0.115 when full at III);
          the growing signature 0.12 + 0.015 per step (0.165 full), the upgraded one 0.12 + 0.02 per level (0.16 at III)
warRatingCap 0.40 (PROPOSED; it was 0.30 with three slots at 60-100%)
power   = 2.25 x sqrt(H0 / 20) x (1.5 + L / 30) x (1 + rating) x war.heroPowerPercent / 100
H0      = the hero's maximum health without the artifact health modifier (so health lines are not counted twice)
```

The values are 0.015 per budget point plus a passive's weight (none, minor 0.01, major or active 0.015, a rare find's
0.025), so the rating follows the budgets of section 6. The slots now carry the level: a level-1 hero's rating is at
most one artifact's.

| Hero | Slots | Artifacts | Rating | Power (8.1's without) |
|---|---|---|---|---|
| a common marksman, level 1 | 1 | none | 0 | 6.8 |
| the same | 1 | a component | 0.03 | 7.0 |
| the same | 1 | a greater artifact | 0.09 | 7.4 |
| Benika as a leader, level 10 | 3 | three advanced | 0.21 | 14.3 (11.8) |
| the same | 3 | three advanced at II | 0.27 | 15.0 |
| Benika as a leader, level 20 | 5 | five greater | 0.45, capped 0.40 | 21.4 (15.3) |
| a legendary vanguard leader, level 30 | 5 | four greater and a signature | 0.48, capped 0.40 | 28.2 (20.2) |

- Everything else in 8.1 stands: the soldier backbone (a side's heroes count for at most 1.5 times its soldiers), half
  power under half health, out of the count while dead or away after an escape.
- **What rivals know**: a scout that sees a hero records the tiers of what it wears (what the boss bar shows); a rival
  council uses those tiers at upgrade level I (a growing artifact counts as advanced, 0.07; upgrades and growth are not
  seen), or 0.10 for a hero they have never seen. Its own heroes it knows.
- `sim.py` prints per hero its build, upgrade levels, growth and rating, and per faction per day the artifacts crafted,
  woken, upgraded, worn, given, asked for, refused, dropped, recovered and stolen.

---

## 14. Balance on paper (PROPOSED numbers; measured in the campaigns of 18.3)

The estimates use 8.1's model: a hero against n soldiers who all reach it holds about n = sqrt(2 H D / (h d)), with H
its health, D what it deals a soldier per second, h an iron soldier's 36 points of hero damage and d an iron soldier's
3.7 a second on a hero (less through armour and resistances, by vanilla's armour formula on a 6-point sword). A duel's
winner is the side whose effective health times damage per second is larger. Builds are what the hero would choose
(10.4). All re-derived for fixed strength and slots by level.

| Case | On paper | Verdict |
|---|---|---|
| 1. A level-1 common marksman (78 health, D about 9) against iron soldiers: bare, then in its one slot | Bare it holds about 3.25 soldiers. In one Blaze-Rod Bracer at full (attack +5%, attack speed +10%, element damage +6%, Stoked Heat) its D is about 1.2 times: about 3.55. The council counts 7.4 against 6.8 (+9%). The first design gave it about 4.0 in three greater artifacts at 60%. | A small, honest boost at level 1; artifacts matter more as slots open. As meant. |
| 2. The same marksman against a player | Bare: about 17 s of a player's landed iron-sword hits; in a Ghast-Tear Torc about 19 s; in the Bracer 17 s, with its own hits about 1.2 times as strong. Its moves are under the same slot caps and wind-ups. | A harder fight, not a different one. As meant. |
| 3. Benika as a level-20 leader (197 health), five slots built for durability (Blackforge Girdle, Shulker-Shell Ward, Ghast-Tear Torc, Cactus-Spine Mantle, Marrow Torc), against soldiers | Health 15 + 10 + 10 + 5 + 5 = 45%, capped at +20%: 236. Armour 5.3 + 4 (capped) = 9.3 cuts an iron sword's 6 by 25% instead of 9%, and all resistances 12%, capped at 10%, by 10% more: soldiers deal about 0.74 times as much. Attack +10%: D about 16.5. Drain 8%, capped at 6%: about 1.0 a second, held to 0.79 by the healing ceiling. n = sqrt(2 x 236 x 16.5 / (36 x 3.7 x 0.74)) = about 8.9 against 6.7 bare (+33%; the first design's three-slot build gave 8.2). Six iron soldiers who all reach her need about 15 s of landed blows instead of 9. The council counts 21.4 against 15.3. | The strongest durability build there is, and the caps did their work: two more slots added about 8% over three, because health, armour, resistances and drain were already near their caps. She beats six soldiers and about nine break even; a dozen soldiers or a second hero beat her. The soldier backbone and the 10-minute revival are unchanged. As meant. |
| 4. That built Benika against a player in iron | About 73 s of landed iron-sword hits instead of 45 (20% more health, about 26% less per hit, with no worn armour on her); a duel of two to three times that is about 145-220 s, at the edge of her 240 s leader enrage. Her damage is barely changed (a durability build). In diamond with Sharpness the hitting time is about a third shorter. | The endgame boss. Long, readable, beatable: the 12% per-hit cap (of a larger maximum), poise, weak points and every telegraph are unchanged, and the enrage ends a stall. Hard; no change. |
| 5. The strongest burst: a level-20 shock striker in five damage artifacts (Breeze-Cut Talon, Deepecho Signet, Blaze-Rod Bracer, Hushstone, Stormrod Spindle) against a player | Ability power 1 + 0.19 (growth) + 0.20 (24%, capped) = 1.39, so a skill at the 16 cap deals 22 instead of 19 before bonuses; with the whole fight bucket (+25%), its element damage (+12% and +6%: +18%) and an element advantage (x1.3, the product of factors at 1.86, under 2.0) it reaches the 40 ceiling, where bare it reached about 31. The ultimate at the 30 cap is 42 raw and stops at the 40 ceiling, against 36 bare. Through plain diamond the ceiling hit is 24 and the bare ultimate 20: a player at full health in plain diamond dies to the built hero's ultimate and survives the bare one's by a hair, as in the first design (a mystic hero at level 20 already reaches the ceiling without artifacts). Through diamond with Protection IV: about 9 instead of 7. Basic hits about 1.35 times (attack +15%, attack speed +15%, critical damage +10%, capped). | Five slots let more hits reach the ceiling; the ceiling, the 1.5 s ultimate wind-up, its line, sound, red mark and darkening are the same, a skill's wind-up and the opening gap too, and this build has no drain. Acceptable. |
| 6. A level-1 hero in its one slot against a level-20 hero in all five (an epic vanguard each) | Level 1: 119 health, 137 in a Blackforge Girdle, moves at growth 0. Level 20: 172 health, 206 with health capped, armour +2, all resistances +8%, Shell Up, drain; moves at +9.5% growth and +18% artifact ability power, fire damage +12%, Rising Echo. Effective health times damage: about 2.4 times the level-1 hero's (the first design's level-1 hero in three greater artifacts was within about 1.3 times). | The level-20 hero wins about 19 times in 20. Levels and the slots they open are the main progression; a low hero cannot buy its way up. As meant ("more slots as they level up"). |
| 7. Hero against hero, equal levels (Benika in Kiln Heart, Ironhide Girdle, Marrow Torc at level 10 against a bare Kagenori) | Her fire damage +12%, health +15%, armour +2, drain 4%, attack +5%, all at full (the first design gave 79% of that at level 10); the duel still runs 45-90 s (the 12% per-hit cap and the 2% `target_max_health` rule are unchanged). | She wins about 70% instead of about half; both built, it is even again and about a tenth longer. Builds matter, they do not decide. As meant. |
| 8. Drain against a swarm | A level-20 hero with drain at the 6% cap dealing 16.5 a second would heal 1.0 a second; the ceiling (10% of 236 per 30 s) holds it to 0.79, against the 16-22 a second six soldiers deal. | The ceiling binds where it should (swarms) and rarely against a lone player. As meant. |
| 9. Artifacts in NPC wars over a campaign | A faction's six heroes need 6 artifacts while they are under level 5, 18 at level 10 and 30 at level 20, so early demand is small. Components arrive by day 2, advanced artifacts from about day 4, the bench and the artificer once two artifacts are awake (about day 6-8), then about two jobs a day per artificer: three advanced artifacts at II for a hero take about a week. Greater artifacts wait for Nether, trial-chamber, mansion and End materials; III upgrades, rare-find upgrades and the engineered three wait for phase 6. | Builds grow with hero levels, which is what slots by level are for. `sim.py` checks the rate (18.3). |
| 10. Growing artifacts | A raiding striker deals about 12 a second to bleeding foes; 60 s of fighting on a raid is about 700 blood, so the Bloodglass Ampoule's three steps of I (1,800) take about three raids and its fifth step (4,500) about seven, after two upgrades. A guard's Vigil Lantern fills its three steps in 6 nights and its five in 15 (with III). A miner hero breaks 60-120 ores a day: the Veinseeker Geode's three steps (288) in three to five days. Full at III each is a greater artifact's 7 points with a minor passive. | Earned by use, on par with a greater artifact of the same upgrade level, never above it; a stolen one is a real prize. As meant. |
| 11. The extreme durability case (the review's): a legendary vanguard leader at level 30 (257 health), five durability artifacts | 308 health; armour 6.5 + 4 = 10.5 and all resistances 10%: iron soldiers' blows at about 0.63 of their force instead of 0.86. About 11.8 iron soldiers instead of 8.8; a player in iron needs about 106 s of landed hits instead of 65 (about 49 s in netherite with Sharpness V). | Beaten by a dozen soldiers or a few heroes, never unkillable; the per-hit cap and every telegraph hold. As the review found with three slots: the caps bind. |

---

## 15. Edge cases

### 15.1 Duplicates and uniqueness

- Two copies of a craftable artifact or a rare find may exist in a world (two ancient cities); one hero wears at most one
  of each (8). Families keep two similar passives from stacking.
- **A signature exists once per world.** `CampaignData.signatures` keeps each signature's current `instance` UUID. A
  copy with another instance (a duplicated world, an old copy after a re-forming) is a **faded copy**: grey name,
  "a faded copy", no lines, no passive, no bond; it stays an item and is never deleted. A copy with the **same**
  instance (a creative clone, an item duplication) cannot be told apart by its data, so the slot decides: only one
  hero at a time wears an instance (`CampaignData.signatures` records the wearer); attuning a second copy fails and
  writes `faded: true` into that copy's component.
- **A destroyed signature re-forms** (void, `/kill`, a despawn by another mod): the world notes it lost, and a new
  instance appears in its hero's first free slot at her next revival, or at the next dawn if she is alive (with no free
  slot, in her backpack), or at her next joining while she is a token. It re-forms as new: no growth, upgrade level I.
  An old instance found later is then a faded copy. `BOSS_HEROES.md` may also make the hero's token give the signature
  when she first joins.

### 15.2 Places and people

- **An artifact in a chest that enemies raid**: raiders and greedy raiding heroes take it first (10.9); it is theirs
  now. The faction notices at its next look at its ledger (its own storehouse is its own knowledge).
- **A player's chest**: no hero or NPC of the faction takes from containers that are not its storehouse; a player can
  of course raid anyone's.
- **A hero changes faction** (exiled when its faction's last base falls, then joins another by a token or, in milestone
  3, by recruitment): what it wears goes with it; its lent marks and wishes are cleared.
- **The faction loses the base whose store holds its artifacts**: they are loot for the raiders like everything else.
- **A dormant hero** (10.6): inert slots; no choices; its artifacts go to the store at its base; dropped if it dies.
- **A free or wandering hero**: wears what it has and chooses from its backpack and what it picks up (10.4 with no
  store, no leader and no workshop).
- **A lent artifact whose giver is gone**: a hero giver that died keeps its claim until it returns (an honourable
  wearer gives it back then); a player giver who left the faction, or a hero who changed faction, loses it.

### 15.3 Saves, reloads, config

- Slots, lent marks and wishes are saved with the hero (`FcHero.artifacts`); a reload re-applies the stat modifiers
  from them (the health share kept); an attune in progress restarts after a reload. The station's ledger is saved with
  the faction (17.2).
- A dropped artifact keeps its no-despawn flag in the item entity's own saved data, and its upgrade level and growth in
  its components.
- **An artifact whose data file is removed** (an operator or a datapack): the item stays, its tooltip says "an
  unknown artifact", it gives nothing, and nothing is deleted (the dormant-hero rule, v1 25.5); its upgrade level and
  growth stay on it for when the file returns.
- **`heroes.artifacts.slotLevels` changed**: artifacts in slots that close go to the backpack at the next tick (or
  drop at the hero's feet if it is full). `heroes.artifacts.enabled = false`: slots give nothing, nothing grows and
  nobody chooses; artifacts still drop at death (they are items).
- **Level change**: a slot opens or closes at once (3). **Config reload**: the build is re-applied.

### 15.4 Combat

- **A hero dies mid-attune**: the artifact in its hands drops with the rest.
- **A stun on a hero with resolve 25%**: a 2 s stun lasts 1.5 s; its immunity lasts 4 s, as without (6.14 rule 2).
- **Recharge on a cooldown already at its floor**: 20% shorter at most, so never under 80% of the floor; a tuned active
  (9.8) is checked against the same bound.
- **Two heroes with Downpull** pull the same soldiers: each taunt is its own; v1's taunt rules (the last one holds)
  apply.
- **Fang Line, Gluefall or Ram Strike in a 3-block tunnel**: the arena rules of 6.12 apply as to any move.
- **Updraft under a low ceiling**: skipped under a ceiling of 3 blocks or less, as leaps are (6.12); under a roof lower
  than its lift it stops at the roof (walls and roofs stop `move_self`, v1 13.4).
- **Ram Strike, Updraft or Downpull at a cliff or by lava**: their pushes land at full strength and the AI aims them
  at edges, like any move's (`HEROES_V2.md` 6.17, 4.4); a fire-proof or fall-proof receiver is not pushed there on
  purpose (6.17's ignored edges).
- **An escaping hero**: no actives, its passives keep firing (Fair Wind fires on `on_escape`), it keeps everything, and
  its growing artifacts keep counting (the Scarbark Amulet counts the escape as a fight survived).

### 15.5 Upgrades, growth, tech and choices

- **Upgrading a worn artifact**: the hero wished for it (10.7), so at the base it takes it off for the artificer, who
  brings it back when the bench is done; the hero re-attunes it (2 s). Its slot is empty meanwhile.
- **The bench is broken mid-work** (a raid, an explosion): the artifact drops unchanged; the materials and levels
  spent are lost (9.7). A bench holding an artifact is loot for raiders.
- **A player begins an upgrade at another faction's bench**: allowed; the bench reads the player's own faction's
  research for gated steps (9.5), and the player pays the same.
- **Growth at the cap**: nothing more counts until an upgrade widens it (7.8); a hero wearing a full artifact is not
  pushed to upgrade it (it may wish for it, 10.7).
- **Growth from a fight against a faction's own player**: impossible (members are allies, never foes).
- **Two growing artifacts that count the same thing**: none of the eight does (8, rule 7); a datapack that adds one counts
  for each.
- **A growing artifact in an escape, a revival or a hauler's pack**: counts while worn by a living hero, never while
  carried (7.8).
- **Smithing an upgraded artifact**: the result starts at I (9.8); the workshop never plans it, and a player is warned
  by the tooltip.
- **Research lost** (phase 6): artifacts keep their upgrades; only new gated steps stop (9.5).
- **A hero is offered an artifact it already wears a copy of**: it keeps the better copy (higher upgrade or growth)
  and passes on or stores the other.
- **Two heroes ask each other for the same thing in one round**: the earlier ask in the ledger is answered first.

---

## 16. Format

### 16.1 The artifact file (`data/factionclash/artifacts/<id>.json`)

```jsonc
{
  "format": 2,                            // 2 since the revision; format 1 files are read (upgrade from the tier, no growth)
  "id": "kiln_heart",                     // must match a registered item id
  "name": "Kiln Heart",                   // 2-24 characters; the clearance pass checks it (section 0)
  "tier": "advanced",                     // component, advanced, greater, rare, signature
  "element": "fire",                      // optional: the element its element lines and passive belong to
  "lore": "Banked coals that never quite go out.",      // 0-80 characters
  "stats": { "element_damage": 12, "ability_power": 6, "resist_fire": 10 },   // keys of 4.1, whole, at level I
  "passive": {                            // optional; components have none
    "name": "Banked Embers",
    "unique": "primed",                   // optional family (section 8)
    "description": "An enemy its moves hit smoulders for 6 s; its next basic hit on it deals 2 fire and ignites it 3 s.",
    "parts": [                            // 1-2 v1/format-2 passive entries sharing the name
      { "trigger": "on_move_hit", "form": "trigger_enemy",
        "effects": [ { "type": "mark", "mark": "ember", "seconds": 6 } ] },
      { "trigger": "on_hit", "cooldown": 4, "form": "trigger_enemy",
        "if": { "condition": "target_has_mark", "mark": "ember" },
        "effects": [ { "type": "damage", "amount": 2, "element": "fire" }, { "type": "ignite", "seconds": 3 },
                     { "type": "clear_marks", "mark": "ember" } ] }
    ]
  },
  "active": null,                         // or a move (HEROES_V2 12.7) with "anim": "artifact.raise" | "artifact.cast"
  "upgrade": {                            // optional; advanced, greater, rare and engineered only (9.8)
    "material": "minecraft:coal_block",   // its own upgrade material
    "counts": [2, 4],                     // for II and III; the common part, levels and time come from the tier (9.8)
    "tuned": { "cooldown_percent": -20 }  // optional; 9.8's rule otherwise (cooldown -20%, or Amounts +20%)
  },
  "tech": { "upgrade_3": "artifice_1" },  // optional ("build", "upgrade_2", "upgrade_3"); else the tier's, 9.5
  "suits": ["fire", "melee", "basic_attacks"],
  "source": "craft",                      // craft, bench, loot, signature
  "sounds": { "proc": "factionclash:artifact.banked_embers" }
}
```

A growing artifact (advanced tier only) adds a `growth` block; its `stats` are step 0:

```jsonc
// data/factionclash/artifacts/bloodglass_ampoule.json (abridged)
{
  "format": 2, "id": "bloodglass_ampoule", "name": "Bloodglass Ampoule", "tier": "advanced",
  "lore": "It is never quite full.",
  "stats": { "attack": 5, "drain": 2 },                                   // step 0: 2 points
  "growth": {
    "counts": "blood",                    // blood, ore, nights, flight, undead, worthy_foes, scars, tending (7.8)
    "base": 300,                          // step k at base x 1, 3, 6, 10, 15
    "steps": [ { "drain": 2 }, { "attack": 5 }, { "health": 5 }, { "drain": 2 }, { "attack": 5 } ]   // 1 point each
  },
  "passive": { "name": "Open Vein",
    "description": "An enemy that bleeds, which its hit leaves under half health, bleeds 1 a second for 3 s.",
    "parts": [ { "trigger": "on_hit", "cooldown": 6, "per_target": true, "form": "trigger_enemy",
                 "if": { "all": [ { "condition": "target_health_below", "value": 50 },
                                  { "condition": "target_bleeds" } ] },
                 "effects": [ { "type": "dot", "amount": 1, "seconds": 3, "element": "physical" } ] } ] },
  "upgrade": { "material": "minecraft:redstone_block", "counts": [1, 2] },  // widens the cap: 3, 4, 5 steps
  "suits": ["striker", "vanguard", "skirmisher", "raids"], "source": "craft",
  "sounds": { "proc": "minecraft:entity.player.hurt_sweet_berry_bush", "step": "minecraft:item.bottle.fill" }
}
```

An engineered artifact has `"source": "bench"` and a `bench` block: `{ "base": "<artifact id>", "materials": [ { "item":
"minecraft:observer", "count": 2 }, ... ], "levels": 10, "seconds": 120 }`, with `"tech": { "build": "artifice_2" }`.

### 16.2 Recipes

Vanilla recipe files in `data/factionclash/recipe/artifact/`: `crafting_shaped` for the components, the Sigil and the
Artificer's Bench, `smithing_transform` for the advanced, growing and greater artifacts (template
`factionclash:artificers_sigil`; the result carries `factionclash:artifact_state` `{ "dormant": true }` and
`factionclash:artifact_work` `{ "upgrade": 1, "growth": 0 }`, so a base's upgrade or growth never carries over, 9.8).
The wake is code (`AnvilUpdateEvent`: left a dormant artifact, right 2 lapis lazuli; cost from the tier; output the same
artifact awake). Upgrades and engineered builds are **bench recipes**, a new recipe type `factionclash:bench` in
`data/factionclash/recipe/bench/`, generated from the artifact files and the tier table of 9.8, so the bench, the
artificer and a datapack read the same costs. `recipe/advancement` unlocks follow vanilla's pattern (the recipe book
shows a recipe once a player has held an input).

### 16.3 Loot modifiers

`data/factionclash/loot_modifiers/artifact_<structure>.json` (NeoForge global loot modifiers): one per source of 9.3,
each adding one rare find with its chance to the named vanilla tables (`chests/ancient_city`, `chests/bastion_treasure`,
`chests/end_city_treasure`, `chests/woodland_mansion`, `chests/pillager_outpost`, `entities/elder_guardian`,
`entities/evoker`), and the Hollow lair heart's roll in code where the lair's reward is made.

### 16.4 Signature artifacts and the bond (for `BOSS_HEROES.md`)

A signature artifact is an artifact file with `"tier": "signature"`, `"source": "signature"` and:

```jsonc
"signature": {
  "hero": "<hero id>",                    // the hero it belongs to; a NOTE if no loaded hero has that id
  "reforms": true,                        // re-forms when destroyed (15.1); default true
  "worn_part": "accessories/<part>",      // optional: drawn on its own hero only, while worn (12.7)
  "bond": {                               // only for that hero
    "stats": { "...": 0 },                // at most 3 more points (4.1 keys); the build caps still hold
    "passive": { "...": "..." },          // optional: replaces the base passive for her, in the same family, at most
                                          // the major-passive strength; or adds a part to it
    "kit_mods": [                         // 0-2 changes to her own moves
      { "move": "<her move id>",
        "cooldown_percent": -20,          // -10 .. -25
        "radius": 1,                      // +0.5 .. +2 blocks, within the form's range (v1 10.1, 6.5)
        "count": 1,                       // +1, within the form's range (volley, chain, rain, barrage)
        "duration": 1,                    // +0.5 .. +2 s, within the form's range
        "add_effect": { "type": "..." } } // one effect; the move must stay within its slot cap
    ]
  }
}
```

Rules for the bond: a kit mod names a move of its hero (PART LEFT OUT otherwise); after the mods the validator checks
the move again against its class minimums, slot caps and cooldown floor (ADJUSTED where over); a kit mod never changes
a move's class, wind-up, recovery or telegraph, and never adds a stun beyond the cap. Worn by any other hero the
signature gives its base lines and base passive only. Budget: 5 points and a major passive, plus the bond. Since the
revision: its numbers are fixed like every artifact's (no attunement, 4.2); it may have **either** a `growth` block
(7.8's rules; three steps at most, since it is never upgraded; the counter its design names) **or** an `upgrade` block
with `tech` `artifice_2` for both levels (9.5, 9.8), never both (`BOSS_HEROES.md`'s revision: Hollow Star grows,
Spirecore upgrades); an upgrade multiplies its base lines only and tunes whichever passive is in use, while the bond's
lines and kit mods stay; a kit mod never changes a move's `explosive` property (`HEROES_V2.md` 6.18); and on its own
hero its bond is what keeps it with her (10.4: her want for it is unbounded; held by an ally of hers, it is lent by
her).

### 16.5 Validation (`logic/hero/ArtifactReader`, the v1 message format and outcomes)

- NOT LOADED: no `id` or `tier`, an id with no registered item, an unknown tier, `format` newer than 2.
- PART LEFT OUT: an unknown stat key; a passive or active that breaks the recipient rules, uses a refused effect
  (anything in 4.4: a lethal cancel, a teleport, block changes, a change to a boss rule) or a trigger it may not use
  (`revival_cut` outside `on_death`, `energy` in `on_ultimate`); a mark id that a hero kit also uses; a bond on a
  non-signature; both `upgrade` and `growth` on a signature, or a signature's `upgrade` gated below `artifice_2`;
  `upgrade` on a component; `growth` outside the advanced tier and the signatures, or with an unknown `counts`; a kit
  mod that changes a move's `explosive`; a `tuned` that would change a class, a wind-up, a recovery, a telegraph or a
  stun length; a `ward_effect` on a counter effect (5.3); the `explosive` property of `HEROES_V2.md` 6.18 on an
  artifact's active (4.4).
- ADJUSTED: lines over the tier budget scaled down in proportion; a line value rounded to whole numbers; passive and
  active Amounts over their caps scaled down (also after tuning); cooldowns raised to their floors; a knockback, pull or
  throw over strength 3 on a passive or active lowered to 3 (4.4: no artifact pushes farther than the Ender Dragon's
  Wing Gust); a growth step over one point scaled down; more than five steps cut to five; synonyms read (`cdr`,
  `cooldown` -> `recharge`; `lifesteal`, `life_steal`, `vamp` -> `drain`; `tenacity`, `cc_reduction` -> `resolve`; `hp`
  -> `health`; `ap` -> `ability_power`).
- NOTE: an element line on an artifact with no `element`; `suits` tags the profile does not know; a passive whose
  trigger can never fire for the roles it suits; an upgraded line that reaches its build cap on this artifact alone (it
  is clamped in play, 9.8); an unknown research id in `tech` (read as needing research nobody has).

Example: `kiln_heart.json, line 6: stats: 7 points; an advanced artifact has 4. Scaled every line down to fit. ADJUSTED.`
`./gradlew checkHeroes` checks the artifact files with the heroes; `/fc artifact check` in game.

### 16.6 What the hero file adds (format 2, optional)

- `artifacts: { "prefer": [ids], "avoid": [ids] }`: x1.3 and x0 in that hero's want (10.3). Used where a kit needs
  something the tags cannot see; a NOTE for an unknown id.
- Line events (`{name}`, `{item}`, `{giver}`), with temperament fallback lines (12.5): `artifact` (it puts on a greater,
  rare, engineered or signature artifact, or one at III), `artifact_gift` (it accepts an offer), `artifact_refuse` (it
  refuses one), `artifact_ask` (it asks another for one).
- The duty `artificer` and the talent `tinkerer` (9.6) join the catalogue's duties and talents.
- `gear` may not name an artifact (PART LEFT OUT: "artifacts are not kit items; they drop when the hero dies").

---

## 17. Config, saving, networking, performance

### 17.1 Config (`[heroes.artifacts]`, PROPOSED)

| Key | Default | Range | What |
|---|---|---|---|
| `enabled` | true | | false: slots give nothing, nothing grows, nobody chooses, the workshop makes no artifacts |
| `slotLevels` | `1,5,10,15,20` | 0-6 levels, each 1-30, never decreasing | the level at which each slot opens (3); `1,1,1,1,1` gives five from level 1; empty: no slots |
| `powerPercent` | 100 | 0-200 | scales every artifact line and passive Amount |
| `healingCeilingPercent` | 10 | 0-50 | 4.5, per 30 s |
| `warRatingCap` | 40 | 0-100 | 13 |
| `craftsPerDay` | 1 | 0-5 | a blacksmith's smithing steps and wakes per base per in-game day |
| `artificerJobsPerDay` | 2 | 0-6 | an artificer's bench, smithing and wake jobs per base per in-game day |
| `wakeLevels` | `advanced=5,greater=10` | 0-30 each | the anvil's level cost |
| `upgradeLevels` | `advanced=5/8,greater=10/15,rare=10/15` | 0-30 each | the bench's level cost for II / III (growing as advanced, engineered as rare) |
| `upgradeSeconds` | `advanced=60/90,greater=120/180,rare=120/180` | 0-600 each | the bench's time for II / III |
| `growthPercent` | 100 | 10-400 | how fast growing artifacts grow (their thresholds are divided by it) |
| `techGate` | `research` | `research`, `open` | 9.5: gated steps need the faction's research (none before ROADMAP phase 6), or are open |
| `choiceSeconds` | 120 | 30-1200 | at most one round of choices per hero this often (10.6) |

The first design's `slots`, `attunementAtOne` and `fullPowerLevel` are gone (read and ignored with a log line).

### 17.2 Saving

| Where | What | Missing on load |
|---|---|---|
| Entity `FcHero.artifacts` | the slots (item stacks with their components), the lent mark per artifact (`from`: a hero id or a player UUID), the hero's wishes (up to 2), the givers it refused once (a tsundere's rule) | empty slots, no marks, no wishes |
| `FactionState.artifactLedger` | the station's ledger: claims, asks waiting, the workshop's order and its current job | an empty ledger (rebuilt at the next choices) |
| `CampaignData.signatures` | per signature id: the current instance UUID, its wearer, `lost` and `reformAt` | a fresh instance at its hero's next revival |
| Item stack components | `factionclash:artifact_state` (`dormant`, `instance`, `faded`); `factionclash:artifact_work` (`upgrade`, `growth`) | awake, no instance, not faded; upgrade I, growth 0 |
| Block entity `artificers_bench` | its slots, the job (the artifact, the step, who began it, its faction, the progress) | an idle bench |

### 17.3 Networking and performance

- No new synced entity field: the boss bar text is composed on the server; the hero sheet and the station travel in the
  existing `InfoPayload`; the slot screen and the bench are vanilla-style menus.
- The build is recomputed only on a change (equip, remove, upgrade, a growth step, a level, config); stat lines are
  attribute modifiers or cached numbers; passives use the existing dispatch.
- Growth counters are a few integer adds in events that already fire (damage dealt, a block broken, a kill, the dawn
  check, the flight tick's distance, a fight's end, a harvest), only for a hero wearing a growing artifact; the item's
  component is written when a step is reached or the hero leaves a fight, not every tick.
- Choices run per hero at most once every 2 minutes and score at most 61 artifacts x 6 heroes x 5 slots: microseconds.

---

## 18. Tests to write later

### 18.1 Unit (pure, `src/test/java/com/factionclash/logic/hero/`)

`ArtifactReaderTest` (one case per message, the 61 shipped files load clean, format 1 files read, 1,000 seeded
damaged files load within every budget and cap), `ArtifactRulesTest` (`buildSumsFixedLinesAndCaps`,
`sameNumbersOnEveryHeroAndLevel`, `slotsOpenAtConfiguredLevels`, `closedSlotEmptiesToBackpack`,
`upgradeMultipliesAndClamps`, `tunedNeverTouchesFrames`, `smithingResetsUpgrade`, `growthStepsAndCap`,
`growthStopsAtCapUntilUpgraded`, `growthKeptOnDropAndTheft`, `growingFullEqualsGreaterOfItsLevel`,
`healthLineMultipliesBossHealth`, `rechargeNeverUnderEightyPercentOfFloor`, `resolveFloorsAtHalfSecond`,
`healingCeilingHolds`, `familiesDoNotStack`, `secondCopyRefused`, `warRatingFixedAndCapped`,
`warPowerUsesHealthWithoutArtifacts`, `attackSpeedLeavesMoveFramesAlone`, `onControlNeverFromPassivesOrTethers`,
`sameInstanceSignatureWornOnce`, `techGateLockedUntilResearch`, `techGateOpenIgnoresIt`, `artifactPushAtMostThree`,
`signatureGrowsOrUpgradesNeverBoth`, `reformedSignatureStartsNew`),
`ArtifactChoiceTest` (`takesBestForFreeSlot`, `swapNeedsHysteresis`, `perfectionistSwapsLess`, `generousHeroPassesOn`,
`greedyHeroKeepsSpares`, `loneWolfNeverAsks`, `askAnsweredByGenerosity`, `contestedClaimFirstAskedFirstServed`,
`leaderWishWeighedByCompliance`, `wishNeverMovesSignature`, `signatureNeverLeavesItsHero`, `signatureHeldByAllyIsLent`,
`honourableHeroReturnsLent`, `tsundereRefusesFirstOffer`, `proudNobleRefusesComponents`, `swornEnemyGiftRefused`,
`refusalReturnsToGiver`, `growthValuedByFeedAndPatience`, `elementFocusOnlyForItsElement`,
`noiseDrawnOncePerDay`, `hysteresisNeverUnderThreePercent`, `swapAtMostOncePerDayWithoutNews`,
`playerAskForAHeroWeighedAsThatHerosAsk`, `playerAskForSelfGivesOnlyUnwantedOrLent`,
`scoutedElementRaisesResistance`, `dormantHeroMakesNoChoice`),
`ArtifactWorkshopTest` (`wishesOrderedByLeader`, `noArtifactOrdersWhileSmithHasGearOrders`, `reservesAreKept`,
`artificerTakesOverTheChain`, `perDayLimits`, `noWishForLockedTech`, `noUpgradeBeforeSmithing`),
`BondValidationTest` (a kit mod keeps the move inside its caps and class), and a test that no method of the artifact
logic takes "is a player" as an input (`HEROES_V2.md` 15.6).

### 18.2 GameTests (each job-running test its own `batch`; spawns at known relative positions; test bases through `GameTestBases.register`, cleaned up)

- `artifactOnlyWorksInHeroSlot`: the same Kiln Heart in a mock player's hand, an NPC soldier's backpack and a hero's
  backpack changes nothing; in the hero's slot its modifiers and passive are there, the same numbers on a level-1 and a
  level-20 hero.
- `artifactPassiveSameOnNpcAndPlayer`: Snag, Fatigue Gaze and Open Vein give the same numbers on an NPC soldier and a
  mock player.
- `artifactActiveHasTelegraph`: Downpull plays its 1.0 s wind-up, ring decal and cue before it pulls.
- `slotOpensOnLevelUp`: a level-4 hero with one slot gets its second at level 5 and fills it at its next choice.
- `heroDropsArtifactsAtDeath`: all slots and the backpack drop at the death spot, upgrade levels and growth kept; the
  items never despawn and survive lava and an explosion; the void destroys them.
- `escapeKeepsArtifacts`.
- `soldiersCarryDroppedArtifactsHome`: the winning side's soldiers pick them up first and store them.
- `heroEquipsAtBaseNeverInFight`: an attune does not start in a fight and stops when one starts; at the base it runs
  after 30 s.
- `heroFillsEmptySlotInField`, `dormantHeroArtifactsInert`.
- `playerOfferHeroDecides` (a wanted artifact is worn, an unwanted one handed back to the mock player),
  `heroGivesToHeroWhoNeedsIt`, `heroAsksAndIsRefused`, `honourableHeroReturnsLent`.
- `blacksmithCraftsSmithsAndWakes`: real recipes, the smithing table and the anvil, the faction's experience spent,
  the anvil's wear.
- `artificerGrowsOutOfBlacksmith`: the bench stands, the level-5 blacksmith becomes the artificer, a new blacksmith is
  hired.
- `artificerUpgradesAtBench` and `playerUpgradesAtBenchSameCost`: the same materials, levels and time; a tending
  artificer is faster; no failure.
- `benchLocksTechSteps`: with `techGate = research` a III upgrade is greyed and refused; with `open` it runs.
- `bloodglassGrowsOnBleedingFoesOnly`: damage to a zombie counts nothing, to a soldier and a mock player the same;
  carried by an NPC it counts nothing.
- `growingArtifactStopsAtCapAndUpgradeWidens`.
- `raidersLootArtifactsFirst`.
- `signatureFadedCopyAndReform`: a second instance is faded; a destroyed one re-forms at the revival.
- `artifactSlotsSurviveReload`, `bossBarShowsArtifactPips`, `warCouncilCountsArtifactRating`,
  `returningBloomShortensRevivalNeverUnder30s`, `readTheTellFiresWithNoPlayerNear` (two NPC-only sides: no decal is
  drawn, the trigger still fires).

### 18.3 Campaign checks (`docs/HANDOFF.md` section 4)

An 8-day campaign with artifacts on: every faction with a blacksmith crafts components by day 2 and its heroes wear
advanced artifacts by about day 5 in the slots their levels open; no blacksmith gear order waits on an artifact; the
faction's experience never drops under the repair reserve; the bench and an artificer appear once two artifacts are
awake; artifacts dropped at deaths are picked up (by someone) within an in-game day when the spot is visited; heroes
swap at most once a day without a reason, give and ask by their personalities (`sim.py` counts gifts, asks, refusals
and contested claims per temperament), and no artifact ping-pongs between two heroes; growing artifacts reach their
first step within the days 14 case 10 expects; `sim.py`'s raid odds with and without artifacts stay within the soldier
backbone; tick time unchanged within noise.

---

## 19. Work breakdown

The same rows are in `HEROES_V2.md` section 15.7. Sizes as there (S under half a session, M about one, L one to two).
R1-R4 can start once milestone 1's combat engine (A3-A6) is in, beside milestone 2; R5 needs the leadership hooks
(L3) and the work API (L5m); the two signature artifacts are content of `BOSS_HEROES.md`'s tasks on top of R1-R3. The
tech gate needs nothing from phase 6: it reads "no research" until phase 6 adds the Artifice line.

| ID | Task | Depends | Size | Done when |
|---|---|---|---|---|
| R1 | Format and pure rules: `ArtifactDef`, `ArtifactReader` (16.5, the v1 messages, formats 1 and 2), the catalogue additions of 5.3 (build stats, `on_move_hit`, `on_control`, `on_death`, `on_marked`, `on_land`, `jump`, `ally_lowest`, `adapt`, `revival_cut`, `ward_effect`, `self_carries`, `target_bleeds`, `engaged_has`, `in_daylight`), `ArtifactRules` (sum at fixed strength, slots by level, upgrades and tuning, growth steps and caps, build caps, healing ceiling, recharge bound, war rating, the tech gate), the bond's validation, schema and `checkHeroes` | F2, F3, F4 | M | the reader and rules unit tests of 18.1; the 61 data files load clean |
| R2 | Items, blocks and the world: `ModArtifacts` (61 items and the Sigil), `ArtifactItem` (fire-, blast- and cactus-proof, never despawning, glint particle, the growth icon property), the `artifact_state` and `artifact_work` components, tooltips and lang, the recipes (crafting, smithing with the reset, the anvil wake, the `factionclash:bench` recipe type), the Artificer's Bench block, block entity and menu (begin, cost, time, tending hook, the research check), the loot modifiers and the Hollow lair roll, the signature registry with faded copies and re-forming | R1, A1 | L | `blacksmithCraftsSmithsAndWakes` (its recipe half), `playerUpgradesAtBenchSameCost`, `benchLocksTechSteps`, `heroDropsArtifactsAtDeath` (its item half), `signatureFadedCopyAndReform` |
| R3 | The hero side: slots by level and saving, the hero sheet's slot view with the Offer slot and Ask buttons, attuning, the build stats in `HeroDamage`, `HeroCombat` cooldowns, `HeroStatus` control and `HeroMovement` stamina, the move runner reading attack speed without the artifact layer (4.1), passives through `HeroPassives` with families (`on_control` only from moves and basic attacks), actives through the move runner, the eight growth counters, the bond's kit mods and one wearer per signature instance, the healing ceiling, death drops, the boss bar pips, the hero sheet, the war rating in `HeroRules.heroPower` and the councils | R1, R2, A4, A5, A6, A10 | L | the hero GameTests of 18.2; every "NPC soldier and mock player" test gives the same numbers for both |
| R4 | `ArtifactChoice` and `ArtifactWorkshop` (pure): profiles and feed rates, worth and want, personality, temperament and relationships, the five choices, contested claims, lent marks, the leader's wishes and compliance, the signature's hold, wishes and the workshop's order and gates | R1, F4 | M | `ArtifactChoiceTest`, `ArtifactWorkshopTest` |
| R5 | Faction AI and the artificer: choices in the game (choice moments, the storehouse path, face-to-face hand-overs, asks through the station's ledger), the workshop's smithing table and bench (shared with ROADMAP phase 3's netherite row), the artificer role growing out of the blacksmith, `ArtificerJob` (the chain, upgrades, engineered builds, tending), the `artificer` duty and the `tinkerer` talent, the blacksmith's artifact orders and wakes with the faction's experience, hauler deliveries, loot priority, recovery trips, raid looting, chat lines and line events, the station ledger, `sim.py` lines | R3, R4, L3, L5m | L | the remaining GameTests of 18.2; the campaign check of 18.3 |
| R6 | Art and sound: `herogen icons` with the shape library and the 51 first icons (contact sheet for the owner), then the 11 new artifacts' icons (the growing ones with their four fill states) and the bench's block texture, the four core clips (`artifact.attune`, `.raise`, `.cast`, `.give`), the `artifact` sound set (`SOUND.md` additions) | G1, G4, S1, R1 | M | icons pass `herogen check`; the sheet is sent to the owner; `sfxgen check` clean |
| R7 | Docs and release: README "Artifacts", BALANCE rows, `docs/factionclash-common.toml`, the "ARTIFACTS" section in every kit sheet (levels 1, 10 and 20), the overview regenerated from the data, the `tinkerer` talent in `HEROES_V2.md` 7.2 and `MOBILITY_AND_WORK.md` 11.1, jar and SHA-256 | R5, R6 | S | the release checklist (`HANDOFF.md` section 7) |

---

## 20. The owner's answers (2026-11-18) and what is still open

### 20.1 The five questions, answered

The first design asked two questions here and `BOSS_HEROES.md` section 11 three more; the owner answered all five on
2026-11-18 (`HANDOFF.md` section 2, request 11). Their answers, and where each went:

1. **Slots** ("Three slots for every hero, or more slots with smaller artifacts?"). Answer: "Heroes may have more slots
   as they level up but artifact strenght doesnt change". Slots open with levels, one to five (3); every artifact's
   numbers are fixed (4.2); budgets, war rating and balance re-derived (6, 13, 14).
2. **Players and their faction's heroes** ("A member player puts artifacts on its heroes and pins them; or should the
   faction AI always decide?"). Answer: "heroes decide if they want to use, replace, or leave for someone else the
   artifact". Neither the player nor the faction decides: each hero does (10.4); a member player offers and asks like
   anyone, and the pins are gone (10.10).
3. **Personal names for the boss heroes**. Answer: "no personal names". `BOSS_HEROES.md`, recorded with its revision.
4. **When a boss hero's faction falls**. Answer: "token": she turns back into her token. `BOSS_HEROES.md`.
5. **The Wither's breach**. Answer: "its ok for any hero or npc that usrs explosives to breach walls, theres no need for
   anyone to be "civil" in battle, so they may use the resources they have to full advantage". `BOSS_HEROES.md`,
   `HEROES_V2.md` 6.17 and 6.18 and `docs/design/breaching.md` (the TNT expert). For artifacts: no artifact is an
   explosive, and 4.4's "no block changes" stays; an artifact's push lands at full strength and is aimed at edges,
   by the owner's further answer that a hero's ability may push anyone off a cliff or into lava (4.4, 6.17).

And the owner's three remarks: tinkerers may upgrade artifacts, which never level with their hero (9.6-9.8); an
artifact may grow by its own design, like one that needs blood (7.8); some artifacts may need a tech level to be built
or upgraded (9.5, 7.9).

### 20.2 Open

No question needs the owner now. Settled by this revision with a recommendation the owner may still overturn:

- **Five slots at level 20** (one at level 1, one more every five levels). More or fewer is a config line
  (`slotLevels`, 3).
- **Upgrades never fail** (9.7): the price is the cost. Failing upgrades are a well-known rule of other games and
  nothing in Minecraft's crafting fails.
- **Growth is never lost**, at a death or a theft (7.8): a grown artifact is a prize to recover or steal.
- **Researched steps stay locked until ROADMAP phase 6** (9.5): III upgrades, rare-find upgrades and the engineered
  three wait for the faction labs; `techGate = open` opens them for anyone who wants them sooner.
- **The artificer grows out of the blacksmith** (9.6), and the Artificer's Bench is the one new block (9.7).
- From the review (21.2): **a player's Ask names whom it is for** and is weighed as that hero's own ask (10.10), so no
  player can strip a hero of what it wants; and **no artifact pushes farther than knockback 3** (4.4, 16.5).
- From the first design: **waking at the anvil** stays (say no, and advanced and greater artifacts come off the smithing
  table awake), and artifacts are **not drawn on the body** (only a signature draws its own part, on its own hero,
  12.7).

---

## 21. Review (2026-11-18)

An adversarial pass over this file, `kits/artifacts_overview.txt` and the signature half of `BOSS_HEROES.md`, against
the owner's decisions (`HEROES_V2.md` section 6, ROADMAP phase 4), the engine's numbers (6.2, 6.4, 8.1, v1 16.1) and
the format's real pieces. (It reviewed the first design: three slots from level 1, attunement and the faction planner,
which the revision above replaced; 21.1 checks the revision.) **Originality**: every name and effect was checked
against the item, rune and ability names of League of Legends, Dota 2 and other well-known games. Changed: Ravenous
Torc to **Marrow Torc** (League's Ravenous Hydra is a lifesteal item with the same adjective), Mender's Censer to
**Mender's Brazier** (League's Ardent Censer is a heal-and-shield-power support censer), Farsight Pearl to **Starless
Pearl** (League's Farsight Alteration trinket), Quickened Mind to **Racing Thoughts** (Dota 2's Quickening Charm, a
cooldown item), Rising Tempo to **Gathering Pace** (League's Lethal Tempo grants the same stacking attack speed) and
Tailwind to **Fair Wind** (a speed move of that name in Pokémon). Three effects were too close and were redesigned:
"every third hit deals bonus damage" (League's Kraken Slayer) became **Stoked Heat** (basic hits stoke the next move);
"after a move, the next basic hit deals bonus damage", in a family that does not stack, was League's Spellblade (Sheen
and its upgrades), so Banked Embers and Primed Strike (now **Rune-Struck**) mark the enemy a move hits and a basic hit
on that enemy cashes the mark; and Snag's "controlling an enemy cuts its armour" (close to League's Abyssal Mask) now
tethers the enemy instead. The other names are Minecraft materials and places in plain English; "Prospector's" is the
mod's own affix word and "ability power" v1's own stat. The clearance search of `ROSTER_PLAN.md` section 8 still runs
on the final names (R7). **Balance**: the extreme durability build, a legendary vanguard leader at level 30 (257
health) in Blackforge Girdle, Shulker-Shell Ward and Ghast-Tear Torc, has 308 health and takes iron soldiers' blows at
about 0.63 of their force instead of 0.86, so 8.1's estimate puts her at about 11.5 iron soldiers instead of 9: beaten
by a dozen soldiers or a few heroes, never unkillable, and a player in iron needs about 106 s of landed hits instead of
65 (about 49 s in netherite with Sharpness V), under the 12% per-hit cap and every telegraph. The soldier backbone of
8.1 caps what that changes in a council. The healing ceiling holds every self-heal an artifact gives, signatures
included (Third Hunger, Spire Mend's Regeneration); a hero's own passives stay under their own caps. One rule
contradicted the promise that no wind-up gets shorter: 4.1 let artifact attack speed shorten move frames down to the
class minimum through A5's attribute read. Artifact attack speed now speeds basic attacks only (4.1, 4.4). Recharge may
still take a cooldown to 80% of its v1 floor (15.4); the global cooldown, the opening and phase gaps and the
ultimate's gap are untouched. The nine cases of section 14 stood (Stoked Heat deals what Third Shot did, on a move
instead of every third shot). **Engine fit**: every passive and active uses v1 and format-2 pieces or those of 5.3;
`on_control` now fires only for control from moves and basic attacks and never for a tether, so Snag cannot feed
itself; the knockback line's unit is stated. **Signatures**: a same-instance duplicate (a creative clone) could have
been worn by two heroes at once, and a hoarding leader could have taken the Wither's Hollow Star from her; now one hero
wears an instance at a time, and a signature on its own hero is held by its bond (7.5, 10.4, 15.1). **The owner's
rules** held: only a hero in a slot benefits and players and NPCs carry alike; nothing reads whether anyone is a player
beyond the slot screen and presentation; no lethal-blow cancel and the 30 s revival floor (Returning Bloom); the tech
tree gates only these mod items; a faction is told what dropped only when it knows of the death, and the kill feed is
unchanged. The questions were cut to the two of section 20, which the owner has since answered (20.1).

### 21.1 Check of the revision (2026-11-18)

- **The owner's answers**: slots open with levels and no artifact's numbers depend on its wearer (no `p(L)` is left in
  sections 1-19: the tooltip, the hero sheet, the signatures, the war rating and the balance cases were all re-derived);
  each hero chooses and the leader only wishes; tinkerers upgrade; eight artifacts grow by design; research gates some
  builds and upgrades and nothing vanilla.
- **The boss rules are untouched**: upgrades and growth stay inside the budgets of 6 and the caps of 4.1, tuning never
  changes frames, stuns or classes (9.8, 16.5), and 4.4 now names upgrades and growth among what the validator checks.
  The burst case (14, case 5) reaches the 40 ceiling more often with five slots, never above it.
- **Equal treatment**: no rule reads whether anyone is a player. A member player's offer and ask go through the same
  choices as a hero's (10.10); the bench charges a player and an NPC the same; growth counts a player's blood as an
  NPC's, and a worthy foe is a hero or a boss whoever controls it. What stays player-side is presentation and the
  screens.
- **Player-like**: the artificer works real materials, real levels and real time; heroes know only their faction's own
  records and what scouts saw; no hero seeks a fight or a mine to feed an artifact.
- **Originality**: the new names are Minecraft materials and plain English (Bloodglass, Veinseeker, Vigil, Skyroad,
  Graveward, Rival's, Scarbark, Hearthroot; Observer Lens, Piston Bracer, Daylight Coil; the Artificer's Bench). Names
  of known items were avoided (no "sanguine", "thirster", "masterwork", "refine" or "temper"-named upgrade system; the
  revision's "honed" at III was a slip, renamed "tuned" by 21.2), and so were their rules: no stacks lost at death, no
  permanent health from hits on champions, no charges from enemy spells, no item that turns into another when full, no
  upgrade that can fail or break the item, no random rerolls (7.8, 9.7).
- **Ids**: every id of the first catalogue is kept, so the icon set being drawn now stays valid; the 11 new ids get
  icons later as data (1.2).

### 21.2 Review of the revision (2026-11-18)

An adversarial pass over the revision, `kits/artifacts_overview.txt`, the signature half of `BOSS_HEROES.md` and the
battle rules it leans on (`HEROES_V2.md` 6.17-6.18, `docs/design/breaching.md`), against the owner's answers of
request 11, every earlier owner decision and the engine's real numbers (`HeroEffects.knockback`,
`FormGeometry.KNOCKBACK_PER_STRENGTH` 0.4, `BossRules` 12% and the 40 ceiling, `HeroRules.heroPower`, read in the
code). **The owner's answers hold literally**: slots open by level and nothing scales an artifact by its wearer's level
(a percentage line is a share of the wearer's own stat; its example now names vanilla's multiplied Speed, since
Strength adds a flat +3, 4.2); upgrades come only from tinkerers, growth only from an artifact's own design, research
gates only mod items at the bench; equal treatment, no yield, the 30 s revival floor, one body, player-like NPCs and
vanilla staying open are untouched. **Fixed**:
- *A player's Ask was a pin by another name*: a generous hero handed anything it wore to any member player who asked.
  The Ask now names whom it is for and is weighed as that hero's own ask; "for me" gets only what the hero does not
  want or what that player lent (10.10). The owner's "heroes decide" now holds for players too.
- *Ping-pong*: the noise was redrawn at every choice (sigma up to 0.38 for an airhead) against a hysteresis that could
  fall to about 2%, so an airhead could swap every two minutes, which 18.3's campaign check forbids. The noise is now a
  daily mood, the hysteresis never under 3%, and a swap needs news or a new day (10.3, 10.4).
- *Ram Strike* pushed with knockback 4 (about 8 blocks on flat ground: 0.4 vanilla knockback a point), farther than
  any kit's push, on an artifact any hero can wear, now that pushes are lethal. It is knockback 3 (about 6 blocks, the
  Ender Dragon's Wing Gust), and 16.5 lowers any artifact push over 3 (4.4).
- *`on_marked`* would have read the drawn decal, which exists only while a player is near; it reads the move's mark
  geometry, so Read the Tell works the same in an NPC-only fight (5.3; GameTest `readTheTellFiresWithNoPlayerNear`).
- *Originality*: the upgrade at III was "honed", the name of Lost Ark's gear-upgrade system ("honing"), and 21.1 even
  claimed no "hone"-named system was used: it is **tuned** now (the format key `tuned`; the Ironbound Whetstone's
  passive keeps its plain name "Honed"). Four passive names were close to well-known game terms: Keen Eye (a Pokémon
  ability) is **Assayer's Eye**, Night Eyes (The Elder Scrolls' "Night Eye") **Lamplit Eyes**, Toll for the Dead
  (Dungeons & Dragons' "Toll the Dead", also a bell) **Graveside Peal**, and Bloodied (Dungeons & Dragons' name for a
  creature under half health, the very threshold of this passive) **Open Vein**. Their rules were already our own.
- *Arithmetic*: case 5's build has ability power +24% (two +12% lines), not 30%, and its factors multiply to 1.86, not
  1.88; the verdicts stand.

**Checked and kept**: the war rating table (13's formula over `HeroRules.heroPower` gives 11.8, 14.3, 15.0, 15.3, 21.4
and 28.2); cases 3,
6 and 11 (8.9 soldiers; the level-20 hero 2.4 times the level-1 one; 11.8 soldiers); the growth budgets (each of the
eight is 2 points at step 0 and 5 / 6 / 7 full, line by line); upgrades within the caps (a five-slot build at III
moves the burst case from x1.43 to x1.45 bonuses and the durability case only by its attack line, +10% to +15%,
because the other caps already bind);
the worked choice example's numbers; the smithing reset (vanilla copies the base's components, so the result's patch
must set `artifact_work`). **Engine fit**: every new piece is in R1-R7 and `notes/kit-gaps.md` ("Artifacts revision"
and "Review of the three revisions"); no mixin is needed (anvil, smithing and loot by data and events; the Darkness
ward by `MobEffectEvent.Applicable`; `on_marked` from the server's decal geometry). No new owner question.
