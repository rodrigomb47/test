# Heroes v2: artifacts

Named items that only work for heroes, built up from vanilla materials or found in the world's dangerous places.

Status: design, 2026-11-18. Nothing here is implemented. Numbers marked PROPOSED go to BALANCE.md when they are built.
Companions: `HEROES_V2.md` (the hero design; section 7.5 points here and section 15.7 holds the tasks of section 19
below), `BOSS_HEROES.md` (the Wither and Ender Dragon heroes, whose two signature artifacts use the format of 16.4),
`MOBILITY_AND_WORK.md` (stamina and work lines), `SOUND.md` (the sound set of 12.6) and
`kits/artifacts_overview.txt` (the owner-facing overview, written from this file).

Reading rules: the hero design's own rules hold here unless this file says otherwise. In particular the owner's
decisions of `HEROES_V2.md` section 6 (one combat mode, equal treatment, no attack tokens, no yield), the 10-minute
revival with its 30 s floor (3.4), "every ability from level 1, power growing to full at level 20" (section 8), the
one-body rule (3.6) and "ordinary NPCs stay player-like" all bind every artifact.

---

## 0. The request and the decisions

The owner's words (2026-11-18, `HANDOFF.md` section 2, request 10): "artifacts, these are items that are only effective
to heroes, artifacts are named items and each can enhance something, think of something like the items from league of
legends for example, normal npcs can have these artifacts in their inventory and transport them around but it has no
effect on them. If a hero dies, their artifacts are dropped." And for the two boss heroes: "they come with an unique
artifact that cant be crafted and are best suited for them, but they are also dropped if they die."

What we take from a MOBA's item system is its **shape**, not its content: items with a few stat lines, one named unique
passive or active per item, build paths from cheap components to finished items, unique passives that do not stack,
items that suit roles, and a build that a player can read at a glance. Every name, look and effect below is our own,
made from Minecraft materials, the mod's factions and the hero archetypes and elements. A clearance pass (as
`ROSTER_PLAN.md` section 8 does for heroes) checks the names before they ship.

Decisions (PROPOSED; the owner confirms or corrects them, section 20):

1. **An artifact is an ordinary item** with its own id (`factionclash:kiln_heart`), stack size 1, fire-proof, never
   despawning once dropped, with a 16x16 icon and a tooltip in its tier's colour (section 1). Fifty of them ship, in
   five tiers: 12 components, 20 advanced, 10 greater, 6 rare finds and 2 signature artifacts (section 7).
2. **Only a hero wearing it in an artifact slot gets anything from it.** Anyone may carry, store, haul, drop, pick up
   and trade it hand to hand: a player, a soldier, a hauler, a hero's own backpack, a chest, a hopper. None of them gets
   any effect. Players and ordinary NPCs are treated exactly alike: neither can wear one (section 2).
3. **Three artifact slots for every hero, from level 1**, whatever its rarity, role or whether it leads. Slots are not
   unlocked by level (the owner's "all abilities from level 1"); instead an artifact's stat lines grow with the
   wearer's level, 60% at level 1 to full at level 20, the curve heroes already use for mobility (section 3, 4.2).
4. **A fixed stat vocabulary** of 19 lines that map onto stats heroes already have (attack, health, armour, ability
   power, speed, energy, poise, element damage and resistance...) plus a few new ones (recharge, drain, heal power,
   resolve, stamina, work). Each line has a **cap for the whole build**, so three artifacts can never stack one stat
   past it, and every existing cap of the boss rules still holds on top (section 4).
5. **Nothing an artifact does touches the boss rules that make a fight fair**: the 12% per-hit cap, the telegraph
   minimums, the opening gap, the phase gaps, the hurt cooldown per attacker, the stun limits and immunity, the
   `heroes.maxHit` ceiling, and "no code path cancels lethal damage" (section 4.4).
6. **One named passive or active per artifact** (components have none), written with format 2's own triggers, forms,
   effects and conditions, held to the passive and skill caps and floors; the few small engine additions are listed in
   5.3. Passives of one **family** never stack (section 8).
7. **Crafted the player-like way**: components at a crafting table, finished artifacts at a smithing table with an
   Artificer's Sigil as the template, then **woken** at an anvil with experience levels and lapis. The faction's
   blacksmith does all three in its workshop, from real vanilla materials and the faction's real experience (section 9).
   Rare finds come only from vanilla structures and bosses (and Hollow lairs); signature artifacts come only with their
   hero.
8. **The faction AI plans builds**: what to craft, and which hero wears what, from each hero's kit, role and element,
   what its scouts have seen of the enemy, the hero's own personality and the leader's (greed, caution, ambition,
   sociability). Heroes equip and swap at a base out of a fight, never mid-fight; haulers carry artifacts between bases;
   winning sides loot the fallen's artifacts (section 10).
9. **A hero that dies drops every artifact it wears** where it died, as a player drops an inventory. The drops never
   despawn and survive fire, lava, explosions and cactus; only the void and commands destroy them. Its own faction can
   recover them, an enemy can steal them, a player can pick them up (section 11). An escaping hero keeps them.
10. **War power counts artifacts**: a hero's power is multiplied by 1 + its artifact rating (at most +30%), and rivals
    know a hero's artifacts only from what their scouts saw (section 13).
11. **Players see them** as small coloured pips on the boss bar and in full on the hero sheet; the owner's kit sheets get
    an "Artifacts" section (section 12).
12. **The two signature artifacts** (the Wither's and the Ender Dragon's) use a signature tier with a **bond**: base
    lines anyone may use, plus a bond (extra lines, a stronger passive and up to two changes to her own moves) only for
    the hero it belongs to. One copy exists per world; if it is destroyed it re-forms with its hero. `BOSS_HEROES.md`
    designs their content (section 7.5, 16.4).
13. **The tech tree comes later and gates only what the mod adds** (ROADMAP phase 6): when it exists, research may gate
    the Sigil and the greater tier; until then everything here is open, and nothing depends on it (section 9.5).

**Improvements added on our own** (the owner welcomes them; each is said here so it can be refused):
- work and leadership artifacts, so artifacts matter in peace too (Prospector's Loupe, Steward's Seal);
- a death-related artifact, the Chorus-Bloom Pendant, whose wearer returns 30% sooner (the owner allows death-related
  abilities to change the 10-minute revival; never under 30 s);
- the wake-at-the-anvil step, which spends the faction's real experience as a player would;
- personality flavour in what a hero likes to wear (a glutton likes to gorge, a merchant likes gold);
- an optional `artifacts` hint block in the hero file and an `artifact` line event (16.6).

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
- Data components (`factionclash:artifact_state`): `dormant` (true until woken, 1.4) and, for a signature only,
  `instance` (a UUID: 15.1). The slot it sits in, and who pinned it there, belong to the hero, not to the item.
- A separate item, `factionclash:artificers_sigil`, is the smithing template (9.1); it is not an artifact.

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
- generated, not hand-painted: a small icon generator (`tools/herogen icons`, task R6) builds each icon from a spec
  (`herosrc/artifacts/icons.json`: a shape from a library of about 30 silhouettes, a material ramp, a gem colour, an
  accent), deterministically, and writes a contact sheet for the owner's review (`docs/heroes-v2/previews/artifacts/`).

### 1.3 Tiers, colours and the tooltip

| Tier | Name colour | Count | Glint | Made |
|---|---|---|---|---|
| Component | yellow (vanilla uncommon) | 12 | no | crafting table |
| Advanced | aqua (vanilla rare) | 20 | no | smithing table, then woken at an anvil |
| Greater | light purple (vanilla epic) | 10 | no | smithing table, then woken at an anvil |
| Rare find | light purple | 6 | yes | found only (9.3), already awake |
| Signature | gold | 2 | yes, and a pulse | comes with its hero (7.5) |

Tooltip (the advanced fire focus as an example; blue lines are stat lines as vanilla shows attribute bonuses, gold the
passive's name, grey its text, dark purple the lore):

```
Kiln Heart
Advanced artifact · fire
 +12% fire damage
 +6% ability power
 +10% fire resistance
Banked Embers (primed): after a move, the next basic hit within
6 s deals 2 fire damage and sets the target alight for 3 s.
Wakes only for a hero in an artifact slot: 60% of its power at
hero level 1, full at level 20.
Built from: Lapis Lens + Block of Coal
"Banked coals that never quite go out."
```

On a hero's sheet the same lines show the wearer's real numbers ("+9.5% fire damage at level 10, 79%"). A dormant
artifact shows its name in grey with "Dormant: wake it at an anvil (5 levels and 2 lapis lazuli)".

### 1.4 Dormant and awake

An advanced or greater artifact comes off the smithing table **dormant**: it gives a hero nothing until it is woken at
an anvil (9.1). Components, rare finds and signatures are never dormant. The step exists so that the strongest
artifacts cost experience as enchanting does, which ties them into the faction's existing experience economy (the
enchanter and the blacksmith's repairs already share it), and so that the tooltip's "wakes only for a hero" has a
visible first half.

---

## 2. Who benefits

- **A hero wearing it in one of its artifact slots**: the stat lines, the passive or active, and (signature only) the
  bond. Nothing else in the world gets anything from an artifact.
- **Carriers get nothing**, and all carriers are equal: a player (inventory, hand, off hand, ender chest), an ordinary
  NPC (soldier, worker, hauler, trader: backpack), a hero's own backpack, a chest, a hopper, an item frame. A player
  cannot wear one (players have no artifact slots); an ordinary NPC cannot either. This is the owner's "normal npcs can
  have these artifacts in their inventory and transport them around but it has no effect on them", and it treats
  players and NPCs the same.
- **Who an artifact's effects reach** follows the format's own rules (`HEROES_V2.md` 6.15): an enemy-only effect reaches
  every enemy alike (a player, an NPC soldier, a hero, a monster), an ally effect every ally alike (member players
  included). `silence` stops only those that have moves (heroes and casting NPCs), as 6.6 says. Nothing in this file
  reads whether anyone is a player; only presentation (bars, tooltips, sounds, the screen) is for players.
- **Whose hero it is does not matter**: an AI faction's hero and a player's faction's hero wear artifacts by the same
  rules; a free (exiled or wandering) hero keeps what it wears.

---

## 3. Slots

**Three slots per hero, from level 1** (`heroes.artifacts.slots`, 3, allowed 0-5). Any artifact fits any slot.

Why three, and why not by rarity or level:
- **Not by rarity.** Rarity already sets boss health (common 250 to legendary 400, `HEROES_V2.md` 6.2) and with it war
  power; more slots for rarer heroes would compound the gap. A common hero with three good artifacts should be a fair
  match for a bare epic one.
- **Not by level.** The owner's rule is that a hero has everything from level 1 and grows in power. Slots unlocking at
  levels 5, 10 and 15 would be a gate. Instead every artifact's stat lines grow with the wearer's level (4.2), so a
  level-1 hero in three greater artifacts is still well short of a level-20 hero (14, case 6), and a faction still
  wants its best artifacts on its highest-level heroes.
- **Three, not six.** Each artifact carries two to four stat lines and one named passive; with the per-stat caps of
  4.1 a fourth slot would mostly overflow the caps, and six slots of smaller items would be a lot to read on a boss bar
  and to plan for an AI. Three is one offensive, one defensive and one utility piece, or a focused build of three of a
  kind. The config allows 0 (artifacts off for heroes) to 5.
- **No extra slot for leaders**: a leader already gets +50 boss health scale.

**The slots live on the hero entity** (saved in its `FcHero` NBT, 17.2). A member player of the hero's faction can open
them from the hero sheet (a small container screen with the vanilla hopper layout: three slots open, two locked unless
the config raises the count) when the hero is out of a fight and within 4 blocks. What a player puts in is **pinned**:
the faction AI never takes it out, until the player takes it out or the hero dies. A player may also take artifacts
out (its own faction's heroes). No one else can open them.

**Attuning.** Putting an artifact on takes 2 s (PROPOSED): the hero holds it to its chest (`artifact.attune`, a core
animation, 12.7) with a faint glint and a chime; damage interrupts it, and only out of a fight can it start. Its lines
apply when the 2 s are over. Taking one off is instant.

---

## 4. Stats

### 4.1 The vocabulary

One point is the unit of an artifact's budget (section 6). Caps are for the whole build (all worn artifacts together,
after attunement), PROPOSED.

| Line (key) | One point | Build cap | Maps to (existing where it says so) |
|---|---|---|---|
| Health (`health`) | +5% | +20% | `MAX_HEALTH`, a multiplied-total modifier `factionclash:artifact/health` after the boss modifier, so it multiplies boss health; the health share is kept on change, as the boss modifier does |
| Attack (`attack`) | +5% | +15% | `ATTACK_DAMAGE` x (1 + p), as v1's `stat attack`: basic hits and `attack_percent` Amounts (still under v1 6.2's 1.5 x reference attack power) |
| Ability power (`ability_power`) | +6% | +20% | added to the ability-power multiplier of every Amount (damage, damage over time, heal, shield): v1 6.3, 4.3 below |
| Armour (`armor`) | +1 | +4 | `ARMOR`, added. Strong against many small hits (soldiers' swords), weak against a hero's big ones; hence a small cap |
| Recharge (`recharge`) | +5% | +20% | the hero's move and artifact-active cooldowns x (1 - recharge). Not the phase gap, the opening gap, the global cooldown, wind-ups, recoveries, or the 20-60 s gap between ultimates (4.4) |
| Attack speed (`attack_speed`) | +5% | +15% | `ATTACK_SPEED` x (1 + p). A5's runner reads the attribute for move frames too, held to the class minimums of 6.4, as for the Haste potion |
| Speed (`speed`) | +3% | +8% | `MOVEMENT_SPEED` x (1 + p), in and out of fights (not a mobility travel bonus) |
| Energy gain (`energy`) | +8% | +25% | v1's `energy_gain` stat on every energy gain |
| Poise (`poise`) | +10% | +25% | the boss poise maximum (6.9) x (1 + p) |
| Element damage (`element_damage`) | +6% | +20% | damage dealt by the hero's moves and passives **of its own element** (an element artifact names its element, 7.2, and only helps a hero of that element; the "build bucket" of 4.3) |
| One resistance (`resist_<element>`) | +10% | +25% each | added to the hero's resistance to that element (v1 6.1; the clamp -30..+60 stays) |
| All resistances (`resist_all`) | +4% | +10% | all eight elements, physical included (soldiers' blows and arrows are physical against a hero, v1 6.1) |
| Drain (`drain`) | +2% | +6% | heals the hero by that share of the damage it really deals (basic hits, moves, passives), as v1's `drain` field does for one effect; under the healing ceiling of 4.5 |
| Heal power (`heal_power`) | +8% | +20% | heals and shields the hero gives, to allies and itself |
| Resolve (`resolve`) | +10% | +25% | stun, freeze, slowness, tether and silence on the hero last that much shorter (never under 0.5 s); its immunity after a stun stays twice the full length (6.14 rule 2) |
| Knockback resistance (`knockback_resistance`) | +10 points | +20 points | `KNOCKBACK_RESISTANCE` + 0.1 per point |
| Stamina (`stamina`) | +10% | +25% | flight stamina, the longest glide and the stamina regained (`MOBILITY_AND_WORK.md` 2.1, 1.3); the fight's air budget (5.1.4) does not change |
| Work speed (`work`) | +5% | +15% | `workSpeed()` at its duty (`MOBILITY_AND_WORK.md` 12.1), inside the 2.0x work cap (13 there) |
| Critical damage (`crit_damage`) | +10% | +25% | v1's `crit_damage` stat (jump critical hits) |

Each line is shown with its sign and unit; a line at its cap shows "(capped)" on the hero sheet, so a player sees when a
fourth health line would be wasted.

### 4.2 Attunement: power grows with the wearer's level

The stat lines of every artifact a hero wears are multiplied by its level power, the same curve as mobility
(`MOBILITY_AND_WORK.md` 1.1):

```
p(L) = 0.6 + 0.4 x (min(L, 20) - 1) / 19        60% at level 1, 68% at 5, 79% at 10, 89% at 15, 100% from 20
```

(`heroes.artifacts.attunementAtOne` 60 and `fullPowerLevel` 20, PROPOSED; levels 21-30 add nothing here.) It applies to
stat lines only. A passive or an active is there in full from level 1, like the hero's own: its Amounts grow with
ability power (the hero's growth plus the artifact layer), its chances, durations and cooldowns do not. Work, talent
and revival hooks (Prospector's Loupe, Steward's Seal, Chorus-Bloom Pendant) are whole from level 1.

### 4.3 Order of operations and the caps on top

1. Sum the raw lines of every worn artifact (one copy per id, section 8; a signature's bond lines only for its own hero).
2. Multiply by `p(L)` and by `heroes.artifacts.powerPercent` (100).
3. Clamp each line to its build cap (4.1).
4. Apply: attribute modifiers with fixed ids `factionclash:artifact/<line>` (health, attack, armour, attack speed,
   speed, knockback resistance), re-applied on every change (equip, remove, level, config reload) so they never stack;
   the other lines as the hero's **build stats** in `HeroStatus`, read by the systems that own them.
5. Every existing cap still holds on top, unchanged:

```
raw   = Amount at hit time  (its attack part at most 1.5 x the reference attack power, v1 6.2)
        x (1 + growth ability power x (level - 1) + artifact ability power)  x  heroes.powerScale / 100
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
damage bonus that an artifact passive grants (Rising Tempo's attack speed is not one; Rising Echo's +3% stacks are)
falls in the **fight bucket** with the hero's own passives, states and enrage, under the +25% that 6.10 already sets.

### 4.4 What artifacts never touch

These are structural rules of `HEROES_V2.md` section 6 and `MOBILITY_AND_WORK.md` part 1. The artifact validator
(16.5) refuses any stat, passive, active or bond that would change them (PART LEFT OUT, "artifacts do not change the
boss rules"):

- the per-hit cap (`max_hit_share`), `health_scale` and the role factor (health lines multiply the result instead);
- the hurt cooldown per attacker, the per-hit `heroes.maxHit`, the 2% `target_max_health` rule against heroes;
- wind-up and recovery minimums of every class (6.4), the opening gap, the phase gaps, the 1 s global cooldown, the
  ultimate's 20-60 s minimum gap and its energy of 100;
- the stun caps (2 s per skill, 3 per ultimate, 1 per passive) and the receiver's immunity window;
- poise decay, the stagger's length and its +25% damage taken (Settled Weight changes only the poise growth after a
  stagger, 7.2);
- the fight's air budget, altitude ceilings and reach clock (5.1 there); no artifact grants flight, a glide or any
  mobility family or tier the hero does not have;
- the escape threshold, `when_losing`, `stands_for` and the 5-minute stay away;
- lethal damage: **no artifact cancels a killing blow** (there is no totem-like artifact), and no artifact changes a
  revival except through its own death trigger, never under 30 s (3.4);
- teleporting, placing, breaking or igniting blocks, and anything that acts through a non-hero wearer.

### 4.5 The healing ceiling

In a fight, everything an artifact heals its own wearer (drain, Gorge, Soothing Smoke on itself, Second Hunger) adds up
to at most **10% of the wearer's maximum health in any 30 s** (`heroes.artifacts.healingCeilingPercent`, PROPOSED);
the rest is lost. Out of a fight there is no ceiling (heroes heal out of fights anyway, 6.10). Heals an artifact gives
to others are not under this ceiling (they are under the passive caps). Reason: with no out-of-combat regeneration in a
fight, drain is the strongest durability line a boss can get, and a hero cut down by six soldiers at about 18-22 a second
should not out-heal them; against a single player, whom a hero hurts slowly, the ceiling is rarely reached (14, cases 3
and 5).

---

## 5. Passives and actives

### 5.1 Passives

- A passive is v1 section 17's passive (trigger, `chance`, `cooldown`, `form`, `radius`, `if`, 1-3 effects) with the
  format-2 triggers of `HEROES_V2.md` 6.6, written in the artifact file (16.1). It may have **two parts** that share one
  name (a set-up part and a pay-off part: "after a move, the next basic hit..."), each a full passive entry.
- **Caps and floors**: every part is checked against the **passive row** of the slot caps (8 damage, 8 damage over
  time, 4 heal, 4 shield, 1 s stun, at the level-1 reference) and gets v1 17.4's cooldown floor, exactly as the hero's
  own passives (HeroBalance, ADJUSTED with the usual messages).
- **The loop guard** (v1 17.3) counts artifact passives with the hero's own: damage from passives never fires `on_hit`,
  `on_crit` or `on_ally_attack`; at most 8 passive firings per hero per second.
- **Family** (`unique`): passives with the same family id do not stack on one hero; only the stronger fires (section 8).
- Passive Amounts scale with ability power like the hero's own (4.2).

### 5.2 Actives

An active is a **move** (`HEROES_V2.md` 12.7: form, class, effects, cooldown, `use_when`, telegraph) that joins the
hero's move list in every phase while the artifact is worn:

- the **skill row** of the slot caps (16 damage, 8 heal, 2 s stun...) and the cooldown floor apply; its class is
  inferred from its form and never lighter (6.4), so it has a wind-up of at least 0.8 s (heavy) or 1.0 s (area), its
  ground mark and the class cue: the same warning everyone gets;
- the move selector (6.3) picks it like a move, with weight 3 by default, so it does not crowd out the hero's own kit;
  at most one artifact active starts in any 10 s (PROPOSED);
- it uses one of three shared core animations (12.7) with the hero's own style library, so no hero needs new clips:
  `artifact.raise` (a raise and slam: Downpull, Updraft), `artifact.cast` (a point forward: Fang Line, Gluefall),
  `artifact.attune` (equipping, 3);
- recharge (4.1) shortens its cooldown; an escaping hero uses none (it starts no moves while it escapes, 6.13);
- four actives ship (Downpull, Gluefall, Updraft, Fang Line); a hero wearing two artifacts with actives uses both.

### 5.3 New engine pieces

Everything else in the catalogue is built from format 1 and 2 pieces that exist (triggers `on_hit`, `on_crit`,
`on_hurt`, `on_kill`, `on_skill`, `on_ultimate`, `on_fight_start`, `on_low_health`, `on_ally_low_health`, `every`,
`always`, `on_mark_full`, `on_add_died`, `on_escape`, `on_stagger`; effects `damage`, `dot`, `ignite`, `heal`,
`shield`, `effect`, `stat`, `knockback`, `pull`, `taunt`, `energy`, `cooldown_cut`, `move_self`, `mark`,
`clear_marks`, `poise`, `silence`, `barrier`; conditions `self_has_mark`, `self_health_below`, `target_health_below`).
New:

| Piece | Kind | What it does | Used by |
|---|---|---|---|
| The build stats (`recharge`, `drain`, `heal_power`, `resolve`, `element_damage`, `resist_all`, `stamina`, `work`, the artifact `ability_power` and `poise`) | stats | 4.1; read by `HeroDamage`, `HeroCombat` cooldowns, `HeroStatus` control timers, `HeroMovement` stamina, the work API | all |
| `on_move_hit` | trigger | one of its moves (not a basic attack, not a passive) lands its first damage on an enemy; once per move use | Arc Jump, Withering Touch |
| `on_control` | trigger | it applies a stun, freeze, slowness, tether or silence to an enemy | Snag |
| `on_death` | trigger | the wearer dies; only `revival_cut` may follow | Returning Bloom |
| `jump` | passive form | the nearest other enemy within `radius` of the event's enemy | Arc Jump |
| `adapt` | effect | +`percent` resistance, for `seconds`, to the element of the hit that set it off (non-physical; at most two elements at once) | Weathered |
| `revival_cut` | effect | the wearer's revival comes `percent` sooner; multiplies with `revival_rite` and a hero's own `revival`; never under `heroes.minReviveSeconds` (30) | Returning Bloom |
| `self_carries` | condition | the hero's backpack holds at least `value` of an item or tag (blocks of it count 9) | Hoard-Pride |
| Hooks | engine | `stagger_growth` (Settled Weight), talent tier +1 (Office), virtual levels through the work API (Keen Eye, `MOBILITY_AND_WORK.md` 12.2), the bond's `kit_mods` (16.4) | as named |

Each is equal for every receiver: `on_control` fires the same whether the one it slowed is a player or an NPC, `adapt`
reads the element of the damage, not who dealt it.

---

## 6. Tiers, budgets and costs

| Tier | Budget | Passive or active | How it is made or found | Woken at the anvil |
|---|---|---|---|---|
| Component | 2 points | none | crafting table, vanilla materials | - |
| Advanced | 4 points | one passive | smithing table: Sigil + a component + a component or a vanilla material | 5 levels and 2 lapis lazuli |
| Greater | 5 points | one major passive or an active | smithing table: Sigil + an advanced artifact + a rare vanilla material | 10 levels and 2 lapis lazuli |
| Rare find | 5 points | one major passive or an active | found only (9.3) | arrives awake |
| Signature | 5 points + bond (at most 3 points and 2 kit changes, for its hero only) | one major passive | comes with its hero | arrives awake |

A passive is not priced in points: the tiers say how strong it may be (a minor passive on advanced artifacts, a major
one on greater, rare and signature ones), and the passive caps and floors bound it. The validator (16.5) scales an
over-budget artifact's lines down in proportion (ADJUSTED) as it does over-cap Amounts.

What the budgets mean on a level-20 hero (full attunement): three components are about +10% effective strength, three
advanced about +20%, three greater about +25-30% (the passives included); at level 1 each is about 60% of that (14).

---

## 7. The catalogue

Fifty artifacts. Ids are snake case (`kiln_heart`). Stats are the full values at level 20; "P" marks the passive, "A" an
active. Recipes: crafting-table recipes give one artifact unless said; "S:" is a smithing recipe (template: the
Artificer's Sigil; base; addition). Suits lists the planner's tags (10.2).

### 7.1 Components (12, crafting table)

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

### 7.2 Advanced (20, smithing table, woken with 5 levels)

The eight **element foci** share a shape (element damage +12%, a second line, +10% resistance to their own element)
and only their element's heroes profit from the damage line (the planner gives them to no one else).

| Artifact (id) | Recipe (S: base + addition) | Stats | Passive | Suits |
|---|---|---|---|---|
| Kiln Heart (`kiln_heart`) | Lapis Lens + Block of Coal | fire damage +12%, ability power +6%, fire resistance +10% | **Banked Embers** (family `primed`): after it uses a move, its next basic hit within 6 s deals 2 fire and ignites 3 s (once per 4 s). `on_skill` self mark `banked` 6 s; `on_hit` if `self_has_mark` `banked`: `damage` 2 fire, `ignite` 3, `clear_marks` self | fire; melee |
| Rimeglass Prism (`rimeglass_prism`) | Lapis Lens + Packed Ice | frost damage +12%, ability power +6%, frost resistance +10% | **Hoarfrost**: every third basic hit on the same enemy within 5 s slows it (Slowness I, 2 s; Slowness I does not down a flyer). `on_hit` mark `rime` (max 3, 5 s); `on_mark_full`: `effect` slowness I 2 s, `clear_marks` | frost |
| Stormrod Spindle (`stormrod_spindle`) | Lapis Lens + Lightning Rod | shock damage +12%, attack speed +5%, shock resistance +10% | **Arc Jump**: when one of its moves hits, a spark jumps to the nearest other enemy within 5 blocks for 3 shock (once per 6 s). `on_move_hit`, form `jump` radius 5: `damage` 3 shock | shock |
| Bog Phial (`bog_phial`) | Lapis Lens + Fermented Spider Eye | venom damage +12%, ability power +6%, venom resistance +10% | **Seeping Venom**: a quarter of its basic hits poison: 1 venom a second for 4 s, up to 2 stacks (undead are immune, vanilla). `on_hit` 25%: `dot` 1/s 4 s, `max_stacks` 2 (cooldown 2 s) | venom |
| Bone-Ash Urn (`bone_ash_urn`) | Lapis Lens + Bone Block | blight damage +12%, ability power +6%, blight resistance +10% | **Withering Touch**: enemies its moves hit heal 25% less for 5 s. `on_move_hit`, `trigger_enemy`: `stat` `healing_received` -25 5 s (cooldown 2 s) | blight; anti-support |
| Farsight Pearl (`farsight_pearl`) | Lapis Lens + Ender Pearl | arcane damage +12%, recharge +5%, arcane resistance +10% | **Quickened Mind**: each move it uses takes 1 s off its other cooldowns (once per 6 s; v1's `cooldown_cut` never takes a cooldown under half). `on_skill`: `cooldown_cut` 1 | arcane; move-heavy kits |
| Daybreak Brooch (`daybreak_brooch`) | Lapis Lens + Block of Gold | radiant damage +12%, heal power +8%, radiant resistance +10% | **Morning Grace**: when an ally within 12 blocks drops under 35% health, it heals that ally 3 (once per 20 s; a member player is an ally like any other). `on_ally_low_health` 35, `trigger_ally`: `heal` 3 | radiant; support |
| Ironbound Whetstone (`ironbound_whetstone`) | Knapped Flint + Block of Iron | physical damage +12%, attack +5%, physical resistance +10% | **Honed**: its jump critical hits deal 2 more and add 10 poise to a hero they hit (once per 2 s). `on_crit`, `trigger_enemy`: `damage` 2 physical, `poise` 10 | physical; melee |
| Ravenous Torc (`ravenous_torc`) | Spider-Eye Vial + Knapped Flint | drain +4%, attack +5%, health +5% | **Gorge**: each enemy it kills heals it 4 (once per 5 s; under the healing ceiling). `on_kill`, `self`: `heal` 4 | striker, vanguard; glutton |
| Quickstep Anklet (`quickstep_anklet`) | Copper Coil + Fletched Charm | attack speed +10%, speed +6% | **Rising Tempo**: each basic hit gives +4% attack speed for 3 s, up to 3 stacks. `on_hit`, `self`: `stat` `attack_speed` +4 3 s, `max_stacks` 3 | skirmisher, marksman |
| Runebound Signet (`runebound_signet`) | Lapis Lens + Knapped Flint | ability power +6%, attack +5%, recharge +5%, health +5% | **Primed Strike** (family `primed`): after it uses a move, its next basic hit within 6 s deals 5 extra damage of its element (once per 3 s). `on_skill` self mark `primed` 6 s; `on_hit` if `self_has_mark` `primed`: `damage` 5, `clear_marks` self | striker, skirmisher; any element |
| Ironhide Girdle (`ironhide_girdle`) | Hale Locket + Riveted Buckle | health +10%, armour +2 | **Braced Hide** (family `last_ditch`): the first time in a fight it drops under half health: a 4-point shield for 10 s and +30% knockback resistance for 8 s. `on_low_health` 50, `once_per_fight`: `shield` 4, `stat` `knockback_resistance` +30 8 s | vanguard; cautious heroes |
| Cactus-Spine Mantle (`cactus_spine_mantle`) | Riveted Buckle + Cactus | armour +2, health +5%, all resistances +4% | **Prickle**: when hurt, enemies within 3 blocks take 2 physical (once per 1.5 s). `on_hurt`, form `nova` radius 3: `damage` 2 physical | vanguard against swarms |
| Hearthwool Shawl (`hearthwool_shawl`) | Tuff Ward + Hale Locket | all resistances +8%, health +10% | **Weathered**: when an element other than physical hurts it, it resists that element 15% more for 8 s (two elements at most). `on_hurt`, `self`: `adapt` 15 8 s (cooldown 2 s) | anyone facing casters |
| Anchor Chain (`anchor_chain`) | Deepslate Ballast + Chain | poise +20%, resolve +20% | **Settled Weight**: after each stagger its poise maximum grows 30% instead of 20% for the rest of the fight (hook `stagger_growth`) | vanguard, grappler |
| Mender's Censer (`menders_censer`) | Glistering Charm + Hale Locket | heal power +16%, health +10% | **Soothing Smoke**: every 10 s in a fight, it and allies within 6 blocks heal 2. `every` 10, form `allies` radius 6: `heal` 2 | support |
| Shepherd's Lantern (`shepherds_lantern`) | Clockwork Pin + Amethyst Tuner | recharge +10%, energy gain +16% | **Kindred Light**: when one of its summons or adds dies, it gains 8 energy (once per 4 s). `on_add_died`, `self`: `energy` 8 | summoner |
| Snarewire Spool (`snarewire_spool`) | Clockwork Pin + Tripwire Hook | recharge +10%, ability power +6%, resolve +10% | **Snag**: enemies it stuns, freezes, slows, tethers or silences lose 15% armour for 4 s (once per 4 s). `on_control`, `trigger_enemy`: `stat` `armor` -15 4 s | controller |
| Wind-Kite Tassel (`wind_kite_tassel`) | Fletched Charm + Paper | stamina +20%, speed +6% | **Tailwind**: when it breaks off to escape, Speed I for 6 s. `on_escape`, `self`: `effect` speed I 6 s | flyers, gliders; escaping kind |
| Prospector's Loupe (`prospectors_loupe`) | Knapped Flint + Spyglass | work speed +15%, stamina +10% | **Keen Eye**: at its duty its tool counts one virtual level higher for the loot: Fortune on ores, crops and leaves, Luck of the Sea when fishing (the rules and caps of `MOBILITY_AND_WORK.md` 12.2 and 13: at most level III with the tool's own and its specialty, never on Silk Touch, never with an extra roll on the same drop) | miners, woodcutters, farmers, fishers; greed |

### 7.3 Greater (10, smithing table, woken with 10 levels)

| Artifact (id) | Recipe (S: base + addition) | Stats | Passive or active | Suits |
|---|---|---|---|---|
| Breeze-Cut Talon (`breeze_cut_talon`) | Ironbound Whetstone + Breeze Rod | attack +10%, attack speed +5%, critical damage +10%, speed +3% | P **Gale Finish** (family `finisher`): its basic hits on an enemy under 30% health deal 10% of the health it is missing (at most 8, the passive cap; once per 2 s). `on_hit` if `target_health_below` 30: `damage` `target_missing_health_percent` 10 | striker, skirmisher |
| Blackforge Girdle (`blackforge_girdle`) | Ironhide Girdle + Netherite Scrap | health +15%, armour +2 | A **Downpull**: it slams the ground and drags enemies within 5 blocks toward it, taunting them for 2 s (class area: 1.0 s wind-up, a ring, the area cue; recovery 0.8 s; cooldown 40 s; used when 2 or more enemies are near). Form `nova` 5: `pull` 2, `taunt` 2 | vanguard |
| Deepecho Signet (`deepecho_signet`) | Runebound Signet + Echo Shard | ability power +12%, recharge +10%, health +5% | P **Rising Echo**: each move it uses adds +3% damage for 10 s, up to 4 stacks (the fight bucket's +25% holds). `on_skill`, `self`: `stat` `damage_dealt` +3 10 s, `max_stacks` 4 | controller, marksman, support with damage moves |
| Blaze-Rod Bracer (`blaze_rod_bracer`) | Quickstep Anklet + Blaze Rod | attack speed +10%, attack +5%, element damage +6%, speed +3% | P **Third Shot**: every third basic hit on the same enemy within 4 s deals 4 extra damage of its element. `on_hit` mark `kindle` (max 3, 4 s); `on_mark_full`: `damage` 4, `clear_marks` | marksman, skirmisher |
| Lifebound Chime (`lifebound_chime`) | Mender's Censer + Totem of Undying | heal power +16%, health +10%, recharge +5% | P **Mercy Toll**: when an ally within 12 blocks drops under 20% health, it shields that ally for 4 for 10 s (once per 30 s; a shield, never a saved life). `on_ally_low_health` 20, `trigger_ally`: `shield` 4 | support |
| Ghast-Tear Torc (`ghast_tear_torc`) | Ravenous Torc + Ghast Tear | drain +4%, health +10%, attack +5% | P **Second Hunger**: under 40% health its drain is 4% higher (the drain cap and the healing ceiling hold). `always` if `self_health_below` 40: `stat` `drain` +4 | striker, vanguard |
| Slimebound Spool (`slimebound_spool`) | Snarewire Spool + Slime Block | recharge +10%, ability power +6%, resolve +10%, health +5% | A **Gluefall**: a disc of slime of radius 3 at its target; after a 1.0 s wind-up (a growing disc), it pulses three times over 2 s: Slowness II 2 s each pulse (Slowness II downs a flyer, `MOBILITY_AND_WORK.md` 2.4; class area, cooldown 45 s, used when 2 or more enemies are near). Form `zone` radius 3, duration 2, interval 1 | controller; anti-air |
| Gale-Rider's Cord (`gale_riders_cord`) | Wind-Kite Tassel + Wind Charge | stamina +20%, speed +6%, knockback resistance +10 | A **Updraft**: a burst of wind knocks enemies within 3 blocks back and lifts it 4 blocks (no fall damage from it; class area: 1.0 s wind-up with a ring; cooldown 30 s; used when an enemy is within 3 blocks). Form `nova` 3: `knockback` 2; `move_self` up 4 | skirmisher, marksman, flyers |
| Shulker-Shell Ward (`shulker_shell_ward`) | Hearthwool Shawl + Shulker Shell | all resistances +8%, health +10%, resolve +10% | P **Shell Up** (family `last_ditch`): the first time in a fight it drops under 40%, it ignores the next 3 hits within 5 s. `on_low_health` 40, `once_per_fight`: `barrier` 3 hits, 5 s | vanguard; cautious heroes |
| Tidewell Conch (`tidewell_conch`) | Shepherd's Lantern + Nautilus Shell | energy gain +16%, recharge +5%, ability power +6%, health +5% | P **Swell**: every 15 s in a fight it gains 5 ultimate energy (never in `on_ultimate`, v1 14). `every` 15, `self`: `energy` 5 | ultimate-led kits, summoner |

### 7.4 Rare finds (6, found only, awake)

| Artifact (id) | Where (9.3) | Stats | Passive or active | Suits |
|---|---|---|---|---|
| Hushstone (`hushstone`) | Ancient city chests | ability power +12%, recharge +10%, resolve +10% | P **Deep Quiet**: when it uses its ultimate, enemies within 8 blocks cannot start moves for 1.5 s (silence: it stops heroes and casting NPCs; players and soldiers have no moves to stop). `on_ultimate`, `nova` 8: `silence` 1.5 | anyone who fights heroes |
| Bastion Hoard-Ring (`bastion_hoard_ring`) | Bastion treasure and bastion chests | health +10%, attack +10%, armour +1 | P **Hoard-Pride**: while its backpack holds 16 or more gold ingots (a gold block counts 9), +8% damage dealt and -8% damage taken. The gold is real and drops if it dies. `always` if `self_carries` gold 16: `stat` `damage_dealt` +8, `damage_taken` -8 | greed; merchants |
| Chorus-Bloom Pendant (`chorus_bloom_pendant`) | End city chests | health +10%, all resistances +8%, stamina +10% | P **Returning Bloom**: if its wearer dies wearing it, it returns 30% sooner (never under 30 s); the pendant drops with the rest. `on_death`: `revival_cut` 30 | leaders, last-stand heroes |
| Fangspeaker Charm (`fangspeaker_charm`) | Woodland mansion chests; evokers | ability power +12%, health +10%, recharge +5% | A **Fang Line**: a line of fangs bursts from the ground toward its target, 10 blocks long: 6 arcane to each enemy on it (class heavy: 0.8 s wind-up with a strip on the ground and the evoker's cue; cooldown 25 s; used when the target is 5 or more blocks away). Form `line` 10, width 1, `look.fangs` | marksman, controller, zoners |
| Elderscale Lens (`elderscale_lens`) | Elder guardians (50% when one dies) | ability power +6%, all resistances +8%, energy gain +16% | P **Fatigue Gaze**: when a fight starts, enemies within 8 blocks get Mining Fatigue I for 8 s (vanilla: slower attacks and digging; an NPC soldier's swing reads `ATTACK_SPEED` as a player's does, which `war_drummer`'s modifier already assumes). `on_fight_start`, `nova` 8: `effect` mining_fatigue I 8 s | controller, vanguard |
| Steward's Seal (`stewards_seal`) | Pillager outpost chests | health +10%, work speed +10%, recharge +5% | P **Office**: its talents count one tier higher (at most III; Presence's tier IV for guard and court talents is not raised, the higher of the two counts, `MOBILITY_AND_WORK.md` 11.2), and its realm talent works at the faction's other bases at tier II instead of I | leaders; talent heroes |

### 7.5 Signature artifacts (2, with their heroes)

The Wither hero's and the Ender Dragon hero's own artifacts (`BOSS_HEROES.md`, which chooses their ids, names, lines,
passives and bonds). They use the signature tier (6) and the bond format (16.4): base lines and a passive any hero may
use, a bond only for the hero they belong to ("best suited for them"), one instance per world, re-formed if destroyed,
dropped like any artifact when the hero dies (the owner's "they are also dropped if they die").

### 7.6 The build tree

```
Knapped Flint ──┬─ Ironbound Whetstone (+ iron block) ── Breeze-Cut Talon (+ breeze rod)
                ├─ Ravenous Torc (+ Spider-Eye Vial) ─── Ghast-Tear Torc (+ ghast tear)
                ├─ Runebound Signet (+ Lapis Lens) ───── Deepecho Signet (+ echo shard)
                └─ Prospector's Loupe (+ spyglass)
Lapis Lens ────┬─ the seven element foci (+ coal block, packed ice, lightning rod, fermented spider eye,
               │                           bone block, ender pearl, gold block)
               └─ Runebound Signet
Copper Coil ── Quickstep Anklet (+ Fletched Charm) ── Blaze-Rod Bracer (+ blaze rod)
Fletched Charm ┬─ Quickstep Anklet
               └─ Wind-Kite Tassel (+ paper) ──────── Gale-Rider's Cord (+ wind charge)
Hale Locket ───┬─ Ironhide Girdle (+ Riveted Buckle) ─ Blackforge Girdle (+ netherite scrap)
               ├─ Hearthwool Shawl (+ Tuff Ward) ───── Shulker-Shell Ward (+ shulker shell)
               └─ Mender's Censer (+ Glistering Charm) ─ Lifebound Chime (+ totem of undying)
Riveted Buckle ┬─ Ironhide Girdle
               └─ Cactus-Spine Mantle (+ cactus)
Deepslate Ballast ── Anchor Chain (+ chain)
Clockwork Pin ─┬─ Shepherd's Lantern (+ Amethyst Tuner) ─ Tidewell Conch (+ nautilus shell)
               └─ Snarewire Spool (+ tripwire hook) ──── Slimebound Spool (+ slime block)
Rare finds and signatures: no tree
```

The seven foci, Cactus-Spine Mantle, Anchor Chain and Prospector's Loupe are finished at the advanced tier: cheaper,
narrower picks beside the greater artifacts.

### 7.7 Example builds (what the planner of section 10 tends to choose, PROPOSED)

| Hero | Build | What it does |
|---|---|---|
| Benika Akagane (vanguard, fire, battle maniac) | Kiln Heart, Ironhide Girdle -> Blackforge Girdle, Ghast-Tear Torc | burns, drags enemies in, lives longer in the middle of them |
| Natsuho Komezaki (striker, physical, tsundere) | Ironbound Whetstone -> Breeze-Cut Talon, Quickstep Anklet, Ravenous Torc | fast claws that finish the weakened |
| Yugen Kagaribi (marksman, arcane, schemer) | Farsight Pearl, Blaze-Rod Bracer, Fangspeaker Charm (if found) | shorter cooldowns, a third-shot burst, a fang line to zone |
| Hinae Shirasuzu (support, radiant, sweet-natured) | Daybreak Brooch, Mender's Censer -> Lifebound Chime, Hale Locket | heals and saves allies |
| Ambrose Nettlewick (controller, venom, eccentric genius) | Bog Phial, Snarewire Spool -> Slimebound Spool, Wind-Kite Tassel (he flies) | slows, armour cuts, longer flights |

---

## 8. Stacking rules

1. **One of each artifact per hero.** A second copy of an id is refused by the slot ("already worn"); a hero's backpack
   may hold any number.
2. **Families do not stack.** Passives with the same `unique` family fire only from the stronger artifact (the higher
   tier, then the higher budget, then the first slot): `primed` (Banked Embers, Primed Strike), `last_ditch` (Braced
   Hide, Shell Up), `finisher` (Gale Finish; reserved for later finishers). The weaker artifact keeps its stat lines.
3. **Stat lines add up** across different artifacts, then attunement, then the build cap (4.3).
4. **Marks are global ids** (v1 19): the catalogue's marks (`banked`, `primed`, `rime`, `kindle`) are artifact marks no
   hero kit uses, checked by the validator, so an artifact never feeds a hero's own mark finisher by accident.
5. **Talents and specialties**: Steward's Seal raises tiers, it does not add a second copy of a talent; the base still
   counts the strongest copy of each talent (7.2 there). Prospector's Loupe is a virtual level under the work caps.
6. **Revival**: Returning Bloom, `revival_rite` and a hero's own `revival` multiply; the result is never under 30 s.

---

## 9. How a faction gets them

### 9.1 The crafting chain (the blacksmith, in the workshop)

| Step | Station | Inputs | Who (NPC) | Time (PROPOSED, before the hero's or the blacksmith's work speed) |
|---|---|---|---|---|
| Artificer's Sigil | crafting table | 1 gold ingot in the middle, 4 lapis lazuli on the edges, 4 redstone dust in the corners: 2 sigils | blacksmith | as any craft (`Crafting`) |
| Component | crafting table | 7.1 | blacksmith | as any craft |
| Advanced or greater artifact | **smithing table** | Sigil (template) + base + addition (7.2, 7.3): a vanilla `smithing_transform` recipe whose result carries `dormant: true` | blacksmith | 5 s |
| Wake | **anvil** | the dormant artifact + 2 lapis lazuli; 5 levels (advanced) or 10 (greater) from the faction's experience, as the blacksmith's repairs already pay theirs (`BlacksmithJob`, the enchanter's share first) | blacksmith | 3 s; the anvil may wear as vanilla's does (12% a use), and the blacksmith forges a new one when it breaks, as today |

- A **player** does each step with the same vanilla screens: the crafting table, the smithing table (template, base,
  addition) and the anvil (a NeoForge `AnvilUpdateEvent` gives the woken artifact for the levels and lapis).
- The **workshop gets a smithing table** (a new `BaseState` point beside the anvil and the crafting table, placed by the
  builder; ROADMAP phase 3's netherite row needs the same table and shares it). An existing base gets it the first time
  its planner wants an advanced artifact.
- The `forgemaster` talent's specialty applies: crafting faster, and a wake costs 1 / 2 / 3 levels less (at least 1), as
  for its repairs (`MOBILITY_AND_WORK.md` 11.1).
- Why the blacksmith and not a new NPC: it already works the crafting table and the anvil with real materials and the
  faction's experience; a separate "artificer" role can come with the tech tree (9.5).

### 9.2 Materials

Every input is a vanilla item an NPC economy can come by player-like, sooner or later:

| Materials | How a faction gets them |
|---|---|
| flint, iron, gold, copper, lapis, redstone, coal, tuff, cobbled deepslate, string, leather, feathers, glass, paper, golden carrots, glistering melon, clocks, chains, tripwire hooks, iron, gold and coal blocks, spyglasses, cactus, bone blocks | today's miners, woodcutters, farmers, animal carers, butchers, smelters and the blacksmith; soldiers' kills at night (string, spider eyes, bones) |
| amethyst shards | miners and cave explorers who cut through a geode; traders (wandering traders sell some) |
| packed ice, ender pearls | traders: wandering traders sell packed ice, cleric villagers sell ender pearls (`TraderJob` trades like a player); endermen killed at night |
| nautilus shells | fishermen's treasure catches (more with `angler_lord`), drowned |
| slime blocks | slimes killed in swamps or slime chunks (night guards, raids through swamps) |
| totem of undying | woodland mansion raids (evokers; ROADMAP phase 3) |
| breeze rods, wind charges | trial chambers (breezes; a trial-chamber raid comes with ROADMAP phase 3's dungeons) |
| blaze rods, ghast tears, netherite scrap | Nether expeditions and fortress raids (ROADMAP phase 3) |
| echo shards | ancient city expeditions (deep dark; later) |
| shulker shells | End expeditions (ROADMAP phase 3's dragon raid and after) |

So components and advanced artifacts arrive with today's economy, and greater artifacts arrive one by one as the
faction reaches the Nether, the trial chambers, the mansions and the End: the item system follows the ROADMAP's world
progression without needing it. A player can shortcut any of it.

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

Other loot: **a dead hero's artifacts** (section 11); **a raided storehouse** (10.8); components are never loot (they
are cheap to make).

### 9.4 Trade and diplomacy

- Villagers and wandering traders never buy or sell artifacts (no vanilla trade exists for them, and we add none).
  Traders buy the materials (9.2).
- Between factions: milestone 2's diplomacy (`HEROES_V2.md` 5.7) may carry an artifact in a tribute or a peace offer
  (a greedy leader values one at its war rating, 13); a hauler carries it on foot, as tributes do. Not before then.
- A member player gives artifacts to its faction's heroes through the slot screen (3), or into the storehouse.

### 9.5 The tech tree later

Artifacts are the mod's own addition, so when ROADMAP phase 6's tech tree exists it may gate them, and only them:
PROPOSED nodes "Artifice I" (the Sigil and the advanced tier) and "Artifice II" (the greater tier), researched at the
lab, perhaps with an artificer NPC taking the three steps over from the blacksmith. Components stay open (they are
plain crafts), rare finds and signatures are never gated (they are found or come with a hero), and nothing vanilla is
touched (the owner's rule). Until then everything in this file is open; no task here waits for the tree.

---

## 10. The AI

All of it is pure logic in `logic/hero/ArtifactPlanner` (unit-tested; no Minecraft classes), called by the director
for each faction and fed only with what the faction knows: its own heroes, stores and materials, and what its scouts
have reported of its enemies (the mod's player-like knowledge rule).

### 10.1 A hero's profile

Read from its definition once per load: role, element, basic-attack style (melee or ranged), duty, `when_losing`, and
kit tags computed from its moves and passives: `heals` (any heal or shield on allies), `summons` (summon, `spawn_add`),
`control` (stun, freeze, slowness, tether, silence), `moves` (6 or more moves), `ultimate_led` (an ultimate with
`charge_rate` 120 or more, or both ultimates), `flyer` / `glider` (mobility primary), `basic_attacks` (strikers,
skirmishers, marksmen whose kit has `on_hit` passives). A hero file may add `artifacts.prefer` and `artifacts.avoid`
(16.6).

### 10.2 Scoring an artifact for a hero

```
value(artifact, hero) = Σ over its lines   role_weight[role][line] x tag_bonus x points(line) x headroom(line)
                      + passive_value                 2 points x fit: 1 when its suits tags match the hero, 0.5 some, 0
                      then x personality(hero, artifact) x (1 + noise)
headroom(line) = the share of that line, after attunement, that still fits under its build cap given the other two slots
```

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
| knockback resistance | 1.0 | 0.7 | 0.4 | 0.3 | 0.4 | 0.4 | 0.4 |
| stamina | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 | 0.2 |
| work speed | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 |
| critical damage | 0.4 | 1.0 | 1.0 | 0.7 | 0.1 | 0.1 | 0.1 |

Tag bonuses: heal power x0 without `heals`; stamina x4 for `flyer` and `glider`, x2 for the escaping kind and a scout's
duty; work speed x2 for a gatherer duty that matches Prospector's Loupe, and x2 more with greed 60 or more or a leader
whose economy focus fits; element damage x0 for another element (the focus's other lines still count).

### 10.3 Personality

The hero's own traits (raw 0-100, as the move selector's posture: `HEROES_V2.md` 6.3) tilt what it likes to wear:

| Trait | Lines and passives it favours | Factor |
|---|---|---|
| caution | health, armour, resistances, resolve, `last_ditch` passives | 1 + 0.6 (c - 0.5) |
| aggression | attack, ability power, attack speed, element damage, drain, critical damage, finishers | 1 + 0.6 (a - 0.5) |
| sociability | passives that help allies (Morning Grace, Soothing Smoke, Mercy Toll) | 1 + 0.6 (s - 0.5) |
| greed | work speed, Prospector's Loupe, Bastion Hoard-Ring | 1 + 0.8 (g - 0.5) |
| ambition | greater, rare and signature tiers | 1 + 0.4 (m - 0.5) |
| volatility | noise | sigma = 0.2 x v, as the director's |

Temperament flavour (x1.15 on its favourites, PROPOSED): `glutton` the torcs (Gorge, Second Hunger); `merchant` the
Hoard-Ring and the Loupe; `chuunibyou` actives and rare finds; `perfectionist` finishing a build (upgrading an
advanced artifact it wears into its greater one before anything new); `big_sibling` and `sweet_natured` ally help;
`airhead` twice the noise.

### 10.4 Who gets what: the leader's distribution

The leader's personality decides the order in which the faction's heroes take from the common store (`LeaderBiases`,
the same raw traits):

| Policy | When | Order |
|---|---|---|
| hoard | greed 65 or more, or a `tyrant` | the leader first, then by war power |
| champion | ambition 65 or more (and not hoard) | the highest war power first |
| share | otherwise (the neutral leader) | the hero with the lowest artifact rating first |

Assignment runs in rounds, MOBA-like: in policy order each hero takes the best available artifact for one of its slots
(scored with its current build), one per hero per round, until slots are full or nothing scores above 0.5 point. A
pinned artifact (3) is never moved. The plan is made at dawn and when something changes (an artifact stored or lost, a
hero revived, returned from an escape or five levels higher, a scout report naming an enemy hero's element), at most
once every 2 minutes per faction (PROPOSED).

### 10.5 What to craft

For each hero in policy order the planner picks a **goal build**: its best three among what the faction holds and what
it can craft from its stores (the recipe tree of 7.6), and orders the cheapest next step toward it: a missing component,
then the smithing step when both inputs are held, then the wake. Gates (PROPOSED):

- the blacksmith takes artifact orders only when its own order book is empty (tools, weapons, armour, repairs and a
  broken anvil come first: soldiers' iron comes before heroes' trinkets);
- materials are spent only above the faction's reserves (the logistics shortage rules) plus an artifact reserve: 9 gold
  ingots and 8 lapis lazuli are never spent on artifacts;
- a wake needs the levels in the faction's experience with 5 levels left over for repairs;
- at most one smithing step and one wake per base per in-game day (`heroes.artifacts.craftsPerDay` 1; +1 with a
  `craft` or `military` economy focus), components when the blacksmith is idle;
- no artifact the faction already holds unused, and no element focus for an element none of its heroes has.

### 10.6 Equipping and swapping

- **At a base, out of a fight**: when a hero has been out of a fight for 30 s at its home base (or any base of its
  faction) and its plan differs from what it wears by at least 10% of its build value (hysteresis), it goes to the
  storehouse as for a gear upgrade (`Job.upgradeEquipment`'s path, `FactionStorage.extractExact`), attunes each new
  artifact (2 s each, 3), and puts what it took off into the storehouse.
- **Never mid-fight.** No attune starts in a fight; one in progress stops when the fight starts.
- **In the field it only fills an empty slot**: a hero that picked up an artifact (10.8) and has a free slot may attune
  it after 30 s out of a fight, if the planner scores it above 0.5 point for that hero; swaps wait for a base.
- **A revived hero** comes back with empty slots (its artifacts dropped where it died) and re-equips from the store at
  its first visit.
- **A dormant hero** (its definition is broken: 3.4 there) gets nothing from what it wears; at its base the planner
  takes the artifacts off it for the faction's working heroes.

### 10.7 Hauling

An artifact a hero at another base needs becomes a hauler delivery (`Logistics`), ahead of bulk goods. A hero already
travelling there may take it instead. Haulers carry artifacts as any goods: no effect, and dropped if the hauler dies.

### 10.8 Loot, recovery and raids

- **Picking up**: after a fight (no engaged enemy for 10 s), the heroes and soldiers of the side holding the ground
  pick up artifact items within 16 blocks before any other loot; an artifact never stays on the ground for want of
  room (the least valuable backpack stack is dropped to make room). Soldiers carry them home to the storehouse; a hero
  keeps them in its backpack until a base (10.6). This is how a faction recovers its own hero's artifacts and how it
  steals an enemy's.
- **Recovery trips**: when the faction knows where one of its heroes fell (it fell at or near a base, or a witness
  brought the news home: the ROADMAP phase 1 knowledge rule) and nobody of its own has picked the drops up, the planner
  may send the revived hero, or two soldiers when no hero is free, to fetch them, unless the spot is within 48 blocks of
  a known enemy base. Whether an enemy already took them, the faction learns only by going there.
- **Raids**: raiders looting a storehouse (`GuardJob` `RAID_LOOT`, after the heart falls) take artifacts first; a greedy
  hero's "storehouse first" raid goal (`MOBILITY_AND_WORK.md` 5.2) does the same while the base still stands.
- Traders never sell artifacts; workers never deposit them anywhere but the storehouse.

---

## 11. Death drops and theft

- **When a hero dies, every artifact it wears drops where it died**, as a player's inventory does (each with a small
  random push), together with its backpack (artifacts in it included) and its non-kit gear (v1 25.4). Kit items still
  vanish and return (they are never artifacts: the validator refuses an artifact in `gear`). This holds for every
  death, whoever or whatever killed it, and for leaders. An **escape** is not a death: an escaping hero keeps
  everything.
- **The drops never despawn** (`ItemEntity.setUnlimitedLifetime`, saved by vanilla). They do not burn, survive lava
  (they float up out of it, as netherite does), explosions and cactus. The void and commands (`/kill`, `/clear`)
  destroy them; a destroyed artifact is gone for good, except a signature (15.1).
- **Findable**: a dropped artifact gives off a small particle in its tier's colour every 2 s for players within 32
  blocks (presentation only; NPCs find items by their own pickup scan, 10.8).
- **Who gets them**: whoever picks them up: the side that holds the ground (10.8), a player, a passing hero of any
  faction, even a zombie (a vanilla mob that picks up an item keeps it and drops it when killed). The hero's own faction
  gets them back only by recovering them; if an enemy took them they are in that enemy's storehouse and on its heroes.
- **The kill feed and chat**: the death message is unchanged (the planned hero kill feed). The hero's own faction is
  told what dropped only when it knows of the death ("Benika Akagane fell at (120, -340); her artifacts lie there: Kiln
  Heart, Blackforge Girdle, Ghast-Tear Torc"); the killers are told what they picked up when they pick it up.
- **Tokens**: `BOSS_HEROES.md`'s hero tokens may reuse these drop rules (never despawning, fire- and blast-proof).

---

## 12. Showing them

### 12.1 The boss bar

After the phase pips, one small square per worn artifact in its tier's colour (presentation, `HEROES_V2.md` 6.1):
`★ Benika Akagane · Warlord of the Crimson Anvil  ◆◆◇ ⚡ ■■■`, the squares yellow, aqua, purple or gold. A player reads
at a glance how built a hero is; the names are on the sheet.

### 12.2 The hero sheet

Right-clicking a hero (`NpcInspector`, the hero sheet of `HEROES_V2.md` 7.4): an "Artifacts" section with each slot's
artifact, tier, attunement ("79% at level 10"), its lines at the wearer's real numbers (a line at its build cap marked
"(capped)"), the passive or active with its numbers and cooldown, and "pinned by <player>" where one is; then the
build's totals; then "In the backpack: ...". Member players get the "Artifacts..." button that opens the slots (3).

### 12.3 The station

The reporting station's Heroes section adds each hero's artifact count and rating; a new line "Artifacts in store:
Kiln Heart, Hale Locket x2, ..." and the planner's current order ("Crafting: Ironhide Girdle for Benika Akagane").

### 12.4 Kit sheets for the owner

`herogen` writes an "ARTIFACTS" section in each kit sheet: what the planner would choose for that hero at levels 1, 10
and 20 from the full catalogue, and why (its top three lines and passives). `kits/artifacts_overview.txt` describes the
whole system and catalogue in the sheets' voice.

### 12.5 Chat and lines

Faction chat (to its member players): "Benika Akagane now wears Blackforge Girdle", "The blacksmith woke a Ghast-Tear
Torc (10 levels)", the death line of 11. A new optional line event `artifact` (16.6): said when the hero puts on a
greater, rare or signature artifact, rate-limited ("Heh. This one bites back."); the temperament banks fill it in.

### 12.6 Sounds (`SOUND.md` rules: vanilla by id where natural, generated otherwise)

A new sound set `herosrc/sfx/artifact.sfx.json` with events `factionclash:artifact.<name>` (a new namespace beside
`hero.cue`, added to `SOUND.md` section 3 when built). Vanilla ids are checked by `sfxgen check` when the set is built.

| Moment | Sound |
|---|---|
| Attune (equip) | `block.amethyst_block.chime`, pitched by tier (component 0.8 to signature 1.4) |
| Wake at the anvil | `block.anvil.use` with `block.enchantment_table.use` |
| An artifact drops at a death | generated `artifact.scatter` (a short falling chime, quiet) |
| Passive procs | the artifact's own: Banked Embers `item.firecharge.use`, Hoarfrost `entity.player.hurt_freeze`, Arc Jump generated `artifact.arc`, Withering Touch `entity.wither.shoot` (quiet), Quickened Mind and Rising Echo `block.amethyst_block.chime`, Morning Grace `block.beacon.power_select`, Honed `entity.player.attack.crit`, Primed Strike `entity.player.attack.strong`, Braced Hide `item.armor.equip_netherite`, Prickle `enchant.thorns.hit`, Kindred Light `block.respawn_anchor.charge`, Tailwind `entity.wind_charge.wind_burst`, Gale Finish `entity.player.attack.sweep`, Mercy Toll `item.totem.use` (quiet), Shell Up `entity.shulker.close`, Swell `block.conduit.activate`, Deep Quiet `block.sculk_shrieker.shriek` (quiet), Fatigue Gaze `entity.elder_guardian.curse`, Returning Bloom `item.chorus_fruit.teleport` at the revival |
| Actives (wind-up cue from the class, as every move) | Downpull `item.mace.smash_ground`; Gluefall `block.slime_block.place` and `entity.slime.squish`; Updraft `entity.wind_charge.wind_burst`; Fang Line `entity.evoker.prepare_attack` then `entity.evoker_fangs.attack` |

### 12.7 Animations

Three core clips (`_core` library, task R6): `artifact.attune` (2 s, the hero holds the artifact to its chest, eyes
closed a moment), `artifact.raise` (an area move: raise both hands, bring them down; its `hit` marker on the release)
and `artifact.cast` (a point forward; heavy). Style libraries may override them like any core clip. Artifacts are **not
drawn on the hero's model**: no new parts, so the one-body rule and the cube budget stay as they are; the bar, the
sheet and the glint say what a hero wears. A signature artifact may name a `worn_part` from the parts library for its
own hero only (16.4), because it is part of that hero's look.

---

## 13. War power and the war council

A hero's war power (`HEROES_V2.md` 8.1) gets one factor:

```
rating  = min(warRatingCap, p(L) x Σ tier value)      component 0.03, advanced 0.07, greater 0.09, rare find 0.10,
                                                      signature 0.12; warRatingCap 0.30 (PROPOSED)
power   = 2.25 x sqrt(H0 / 20) x (1.5 + L / 30) x (1 + rating) x war.heroPowerPercent / 100
H0      = the hero's maximum health without the artifact health modifier (so health lines are not counted twice)
```

| Hero | Artifacts | p(L) | Rating | Power (8.1's without) |
|---|---|---|---|---|
| a common marksman, level 1 | none | 0.60 | 0 | 6.8 |
| the same | three components | 0.60 | 0.054 | 7.2 |
| the same | three greater | 0.60 | 0.162 | 7.9 |
| Benika as a leader, level 10 | three advanced | 0.79 | 0.166 | 13.8 (11.8) |
| Benika as a leader, level 20 | three greater | 1.00 | 0.27 | 19.4 (15.3) |
| a legendary vanguard leader, level 30 | two greater and a signature | 1.00 | 0.30 (cap) | 26.3 (20.2) |

- Everything else in 8.1 stands: the soldier backbone (a side's heroes count for at most 1.5 times its soldiers), half
  power under half health, out of the count while dead or away after an escape.
- **What rivals know**: a scout that sees a hero records the tiers of what it wears (what the boss bar would show); a
  rival council uses the rating its scouts last saw, or 0.10 for a hero they have never seen. Its own heroes it knows.
- `sim.py` prints per hero its build and rating, and per faction per day the artifacts crafted, woken, worn, dropped,
  recovered and stolen.

---

## 14. Balance on paper (PROPOSED numbers; measured in the campaigns of 18.3)

The estimates use 8.1's model: a hero against n soldiers who all reach it holds about n = sqrt(2 H D / (h d)), with H
its health, D what it deals a soldier per second, h an iron soldier's 36 points of hero damage and d an iron soldier's
3.7 a second on a hero. Builds are what the planner would choose.

| Case | On paper | Verdict |
|---|---|---|
| 1. A level-1 common marksman (78 health, D about 9) against iron soldiers: bare, then in three greater artifacts (Blaze-Rod Bracer, Breeze-Cut Talon, Ghast-Tear Torc) | Bare it holds about 3.3 soldiers. Built, at 60% attunement: attack +12% (5 + 10 + 5, x0.6), attack speed +9%, health +6%, drain 2.4%; with Third Shot and Gale Finish its D is about 1.4 times, so it holds about 3.25 x sqrt(1.06 x 1.4) = 4.0. The council counts 7.9 against 6.8 (+16%; the estimate says +22%). | It holds four soldiers instead of three: artifacts make a weak hero stand, not win a war. As meant. |
| 2. The same marksman against a player | Bare: about 17 s of a player's landed iron-sword hits; built: 18 s, and its own hits are about 1.4 times as strong. Its moves are under the same slot caps and wind-ups. | A harder fight, not a different one. As meant. |
| 3. Benika as a level-20 leader (197 health), fully built for durability (Blackforge Girdle, Shulker-Shell Ward, Ghast-Tear Torc), against soldiers | Health 15 + 10 + 10 = 35%, capped at +20%: 236. Armour 5.3 + 2 = 7.3 cuts an iron sword's 6 by 17% instead of 9%, and all resistances +8% (physical included) by 8% more: soldiers deal about 0.84 times as much. Drain 4% of her 15.75 a second is 0.63 (the ceiling would allow 0.79). n = sqrt(2 x 236 x 15.75 / (36 x 3.1)) = about 8.2 against 6.7 bare (+23%, a little more with drain and Shell Up's three ignored hits); the council counts 19.4 against 15.3 (+27%). Six iron soldiers who all reach her now need about 13 s of landed blows instead of 9. | The strongest single case in the catalogue, and still beaten by six soldiers; the soldier backbone and the 10-minute revival are unchanged. As meant. |
| 4. That built Benika against a player in iron | About 65 s of landed iron-sword hits instead of 45 (+44%: 20% more health and about 16% less per hit, with no worn armour on her); a duel of two to three times that is about 130-195 s, under her 240 s leader enrage. Her damage is unchanged (a durability build). In diamond with Sharpness the hitting time is about a third shorter. | The endgame boss. Long, readable, beatable: the 12% per-hit cap (of a larger maximum), poise, weak points and every telegraph are unchanged. Hard; no change. |
| 5. A level-20 striker fully built for damage (Breeze-Cut Talon, Deepecho Signet, Blaze-Rod Bracer) against a player | Basic hits about 1.3 times (attack +15%, attack speed +15%, critical damage +10%). A skill at the 16 cap: ability power 1 + 0.19 (growth) + 0.12 = 1.31: 21 instead of 19, about 17 health through iron armour instead of 15. The ultimate at the 30 cap: 39 instead of 36, under `heroes.maxHit` 40: through plain diamond about 23 instead of 20, so a player at full health in plain diamond dies to the built hero and survives the bare one by a hair (a mystic hero at level 20 already reaches the 40 ceiling without artifacts); through diamond with Protection IV about 8 instead of 7. | Artifacts add about a sixth to a hit that already had the ceiling within reach; the ceiling, the 1.5 s ultimate wind-up, its line, sound, red mark and darkening are the same, and this build has no drain. Acceptable. |
| 6. A level-1 hero in three greater artifacts against a bare level-20 hero of the same kind | Epic vanguard: 119 x 1.12 = 133 health against 172; its moves at growth 0 and +7% artifact ability power against +9.5% growth; attack +6% against the level-20's +0.57 attack. | The level-20 hero wins about three times in four; artifacts close about a third of the gap. Levels stay the main progression. As meant. |
| 7. Hero against hero, equal levels (Benika in Kiln Heart, Ironhide Girdle, Ravenous Torc at level 10 against a bare Kagenori) | Her fire damage +9.5%, health +11.9%, armour +1.6, drain 3.2% (all x0.79); the duel still runs 45-90 s (the 12% per-hit cap and the 2% `target_max_health` rule are unchanged). | She wins about 60-65% instead of about half; both built, it is even again and about a tenth longer. MOBA-like: builds matter, they do not decide. As meant. |
| 8. Drain against a swarm | A level-20 hero with drain at the 6% cap dealing 15 a second would heal 0.9 a second; the ceiling (10% of 236 per 30 s) holds it to 0.79, against the 18-22 a second six soldiers deal. | The ceiling binds where it should (swarms) and rarely against a lone player. As meant. |
| 9. Artifacts in NPC wars over a campaign | A faction makes about one smithing step and one wake a day per base once its blacksmith is idle; three advanced artifacts per hero take about a week, greater ones wait for Nether, trial-chamber, mansion and End materials. | Builds grow over a campaign, roughly with hero levels. `sim.py` checks the rate (18.3). |

---

## 15. Edge cases

### 15.1 Duplicates and uniqueness

- Two copies of a craftable artifact or a rare find may exist in a world (two ancient cities); one hero wears at most one
  of each (8). Families keep two similar passives from stacking.
- **A signature exists once per world.** `CampaignData.signatures` keeps each signature's current `instance` UUID. A
  copy with another instance (a duplicated world, an item duplication, a creative copy) is a **faded copy**: grey name,
  "a faded copy", no lines, no passive, no bond; it stays an item and is never deleted.
- **A destroyed signature re-forms** (void, `/kill`, a despawn by another mod): the world notes it lost, and a new
  instance appears in its hero's first free slot at her next revival, or at the next dawn if she is alive (with no free
  slot, in her backpack). An old instance found later is then a faded copy. `BOSS_HEROES.md` may also make the hero's
  token give the signature when she first joins.

### 15.2 Places and people

- **An artifact in a chest that enemies raid**: raiders and greedy raiding heroes take it first (10.8); it is theirs
  now. The faction notices at its next planner run (its own storehouse is its own knowledge).
- **A player's chest**: the faction AI never takes from containers that are not its storehouse; a player can of course
  raid anyone's.
- **A hero changes faction** (exiled when its faction's last base falls, then joins another by a token or, in milestone
  3, by recruitment): what it wears goes with it.
- **The faction loses the base whose store holds its artifacts**: they are loot for the raiders like everything else.
- **A dormant hero** (10.6): inert slots; artifacts taken off at its base; dropped if it dies.
- **A free or wandering hero**: wears what it has; its own simple rule picks from its backpack (the planner with itself
  as the only hero and no crafting).

### 15.3 Saves, reloads, config

- Slots and pins are saved with the hero (`FcHero.artifacts`); a reload re-applies the stat modifiers from them (the
  health share kept); an attune in progress restarts after a reload.
- A dropped artifact keeps its no-despawn flag in the item entity's own saved data.
- **An artifact whose data file is removed** (an operator or a datapack): the item stays, its tooltip says "an
  unknown artifact", it gives nothing, and nothing is deleted (the dormant-hero rule, v1 25.5).
- **`heroes.artifacts.slots` lowered**: artifacts in removed slots go to the backpack at the next tick (or drop at the
  hero's feet if it is full). `heroes.artifacts.enabled = false`: slots give nothing and the planner stops; artifacts
  still drop at death (they are items).
- **Level change**: attunement re-applies at once. **Config reload**: the same.

### 15.4 Combat

- **A hero dies mid-attune**: the artifact in its hands drops with the rest.
- **A stun on a hero with resolve 25%**: a 2 s stun lasts 1.5 s; its immunity lasts 4 s, as without (6.14 rule 2).
- **Recharge on a cooldown already at its floor**: 20% shorter at most, so never under 80% of the floor.
- **Two heroes with Downpull** pull the same soldiers: each taunt is its own; v1's taunt rules (the last one holds)
  apply.
- **Fang Line or Gluefall in a 3-block tunnel**: the arena rules of 6.12 apply as to any move.
- **Updraft under a low ceiling**: skipped under a ceiling of 3 blocks or less, as leaps are (6.12); under a roof lower
  than its lift it stops at the roof (walls and roofs stop `move_self`, v1 13.4).
- **An escaping hero**: no actives, its passives keep firing (Tailwind fires on `on_escape`), it keeps everything.

---

## 16. Format

### 16.1 The artifact file (`data/factionclash/artifacts/<id>.json`)

```jsonc
{
  "format": 1,
  "id": "kiln_heart",                     // must match a registered item id
  "name": "Kiln Heart",                   // 2-24 characters; the clearance pass checks it (section 0)
  "tier": "advanced",                     // component, advanced, greater, rare, signature
  "element": "fire",                      // optional: the element its element lines and passive belong to
  "lore": "Banked coals that never quite go out.",      // 0-80 characters
  "stats": { "element_damage": 12, "ability_power": 6, "resist_fire": 10 },   // keys of 4.1, whole numbers
  "passive": {                            // optional; components have none
    "name": "Banked Embers",
    "unique": "primed",                   // optional family (section 8)
    "description": "After a move, the next basic hit within 6 s deals 2 fire damage and ignites 3 s.",
    "parts": [                            // 1-2 v1/format-2 passive entries sharing the name
      { "trigger": "on_skill", "form": "self",
        "effects": [ { "type": "mark", "mark": "banked", "to": "self", "seconds": 6 } ] },
      { "trigger": "on_hit", "cooldown": 4, "form": "trigger_enemy",
        "if": { "condition": "self_has_mark", "mark": "banked" },
        "effects": [ { "type": "damage", "amount": 2, "element": "fire" }, { "type": "ignite", "seconds": 3 },
                     { "type": "clear_marks", "mark": "banked", "to": "self" } ] }
    ]
  },
  "active": null,                         // or a move (HEROES_V2 12.7) with "anim": "artifact.raise" | "artifact.cast"
  "suits": ["fire", "melee", "basic_attacks"],
  "source": "craft",                      // craft, loot, signature
  "sounds": { "proc": "factionclash:artifact.banked_embers" }
}
```

### 16.2 Recipes

Vanilla recipe files in `data/factionclash/recipe/artifact/`: `crafting_shaped` for the components and the Sigil,
`smithing_transform` for the advanced and greater artifacts (template `factionclash:artificers_sigil`; the result
carries `factionclash:artifact_state` `{ "dormant": true }`). The wake is code (`AnvilUpdateEvent`: left a dormant
artifact, right 2 lapis lazuli; cost from the tier; output the same artifact awake). `recipe/advancement` unlocks
follow vanilla's pattern (the recipe book shows a recipe once a player has held an input).

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
signature gives its base lines and base passive only. Budget: 5 points and a major passive, plus the bond.

### 16.5 Validation (`logic/hero/ArtifactReader`, the v1 message format and outcomes)

- NOT LOADED: no `id` or `tier`, an id with no registered item, an unknown tier, `format` newer than 1.
- PART LEFT OUT: an unknown stat key; a passive or active that breaks the recipient rules, uses a refused effect
  (anything in 4.4: a lethal cancel, a teleport, block changes, a change to a boss rule) or a trigger it may not use
  (`revival_cut` outside `on_death`, `energy` in `on_ultimate`); a mark id that a hero kit also uses; a bond on a
  non-signature.
- ADJUSTED: lines over the tier budget scaled down in proportion; a line value rounded to whole numbers; passive and
  active Amounts over their caps scaled down; cooldowns raised to their floors; synonyms read (`cdr`, `cooldown` ->
  `recharge`; `lifesteal`, `life_steal`, `vamp` -> `drain`; `tenacity`, `cc_reduction` -> `resolve`; `hp` -> `health`;
  `ap` -> `ability_power`).
- NOTE: an element line on an artifact with no `element`; `suits` tags the planner does not know; a passive whose
  trigger can never fire for the roles it suits.

Example: `kiln_heart.json, line 6: stats: 7 points; an advanced artifact has 4. Scaled every line down to fit. ADJUSTED.`
`./gradlew checkHeroes` checks the artifact files with the heroes; `/fc artifact check` in game.

### 16.6 What the hero file adds (format 2, optional)

- `artifacts: { "prefer": [ids], "avoid": [ids] }`: x1.3 and x0 in that hero's scoring (10.2). Used where a kit
  needs something the tags cannot see; a NOTE for an unknown id.
- The line event `artifact` (`{name}`, `{item}`), with temperament fallback lines (12.5).
- `gear` may not name an artifact (PART LEFT OUT: "artifacts are not kit items; they drop when the hero dies").

---

## 17. Config, saving, networking, performance

### 17.1 Config (`[heroes.artifacts]`, PROPOSED)

| Key | Default | Range | What |
|---|---|---|---|
| `enabled` | true | | false: slots give nothing, the planner stops |
| `slots` | 3 | 0-5 | slots per hero |
| `attunementAtOne` | 60 | 30-100 | % of an artifact's lines at hero level 1 |
| `fullPowerLevel` | 20 | 1-30 | level of full attunement |
| `powerPercent` | 100 | 0-200 | scales every artifact line and passive Amount |
| `healingCeilingPercent` | 10 | 0-50 | 4.5, per 30 s |
| `warRatingCap` | 30 | 0-100 | 13 |
| `craftsPerDay` | 1 | 0-5 | smithing steps and wakes per base per in-game day |
| `wakeLevels` | `advanced=5,greater=10` | 0-30 each | the anvil's level cost |

### 17.2 Saving

| Where | What | Missing on load |
|---|---|---|
| Entity `FcHero.artifacts` | the slots: item stacks (with their components) and the pinning player's UUID | empty slots |
| `CampaignData.signatures` | per signature id: the current instance UUID, `lost` and `reformAt` | a fresh instance at its hero's next revival |
| Item stack component `factionclash:artifact_state` | `dormant`, `instance` | awake, no instance |

### 17.3 Networking and performance

- No new synced entity field: the boss bar text is composed on the server; the hero sheet and the station travel in the
  existing `InfoPayload`; the slot screen is a vanilla menu.
- The build is recomputed only on a change (equip, remove, level, config); stat lines are attribute modifiers or cached
  numbers; passives use the existing dispatch. The planner runs at most once every 2 minutes per faction and scores
  at most 50 artifacts x 6 heroes x 3 slots: microseconds.

---

## 18. Tests to write later

### 18.1 Unit (pure, `src/test/java/com/factionclash/logic/hero/`)

`ArtifactReaderTest` (one case per message, the 50 shipped files load clean, 1,000 seeded damaged files load within
every budget and cap), `ArtifactRulesTest` (`buildSumsAttunesAndCaps`, `attunementIsTheMobilityCurve`,
`healthLineMultipliesBossHealth`, `rechargeNeverUnderEightyPercentOfFloor`, `resolveFloorsAtHalfSecond`,
`healingCeilingHolds`, `familiesDoNotStack`, `secondCopyRefused`, `warRatingAndCap`, `warPowerUsesHealthWithoutArtifacts`),
`ArtifactPlannerTest` (`neutralLeaderSharesByNeed`, `greedyLeaderHoards`, `ambitiousLeaderChampions`,
`elementFocusOnlyForItsElement`, `scoutedElementRaisesResistance`, `noArtifactOrdersWhileSmithHasGearOrders`,
`reservesAreKept`, `hysteresisStopsSwapping`, `pinnedNeverMoved`, `perfectionistFinishesBuild`,
`dormantHeroArtifactsReassigned`), `BondValidationTest` (a kit mod keeps the move inside its caps and class), and a
test that no method of the artifact logic takes "is a player" as an input (`HEROES_V2.md` 15.6).

### 18.2 GameTests (each job-running test its own `batch`; spawns at known relative positions; test bases through `GameTestBases.register`, cleaned up)

- `artifactOnlyWorksInHeroSlot`: the same Kiln Heart in a mock player's hand, an NPC soldier's backpack and a hero's
  backpack changes nothing; in the hero's slot its modifiers and passive are there.
- `artifactPassiveSameOnNpcAndPlayer`: Snag and Fatigue Gaze give the same numbers on an NPC soldier and a mock player.
- `artifactActiveHasTelegraph`: Downpull plays its 1.0 s wind-up, ring decal and cue before it pulls.
- `heroDropsArtifactsAtDeath`: all three slots and the backpack drop at the death spot; the items never despawn and
  survive lava and an explosion; the void destroys them.
- `escapeKeepsArtifacts`.
- `soldiersCarryDroppedArtifactsHome`: the winning side's soldiers pick them up first and store them.
- `heroEquipsAtBaseNeverInFight`: an attune does not start in a fight and stops when one starts; at the base it runs
  after 30 s.
- `heroFillsEmptySlotInField`, `memberPlayerPinsArtifact` (the planner leaves it), `dormantHeroArtifactsInert`.
- `blacksmithCraftsSmithsAndWakes`: real recipes, the smithing table and the anvil, the faction's experience spent,
  the anvil's wear.
- `raidersLootArtifactsFirst`.
- `signatureFadedCopyAndReform`: a second instance is faded; a destroyed one re-forms at the revival.
- `artifactSlotsSurviveReload`, `bossBarShowsArtifactPips`, `warCouncilCountsArtifactRating`,
  `returningBloomShortensRevivalNeverUnder30s`.

### 18.3 Campaign checks (`docs/HANDOFF.md` section 4)

An 8-day campaign with artifacts on: every faction with a blacksmith crafts components by day 2 and wears advanced
artifacts by about day 5; no blacksmith gear order waits on an artifact; the faction's experience never drops under the
repair reserve; artifacts dropped at deaths are picked up (by someone) within an in-game day when the spot is visited;
heroes swap at most once a day without a reason; `sim.py`'s raid odds with and without artifacts stay within the
soldier backbone; tick time unchanged within noise.

---

## 19. Work breakdown

The same rows are in `HEROES_V2.md` section 15.7. Sizes as there (S under half a session, M about one, L one to two).
R1-R4 can start once milestone 1's combat engine (A3-A6) is in, beside milestone 2; R5 needs the leadership hooks
(L3) and the work API (L5m); the two signature artifacts are content of `BOSS_HEROES.md`'s tasks on top of R1-R3.

| ID | Task | Depends | Size | Done when |
|---|---|---|---|---|
| R1 | Format and pure rules: `ArtifactDef`, `ArtifactReader` (16.5, the v1 messages), the catalogue additions of 5.3 (build stats, `on_move_hit`, `on_control`, `on_death`, `jump`, `adapt`, `revival_cut`, `self_carries`), `ArtifactRules` (sum, attunement, caps, healing ceiling, recharge bound, war rating), the bond's validation, schema and `checkHeroes` | F2, F3, F4 | M | the unit tests of 18.1 for the reader and rules; the 50 data files load clean |
| R2 | Items and the world: `ModArtifacts` (50 items and the Sigil), `ArtifactItem` (fire-, blast- and cactus-proof, never despawning, glint particle), `artifact_state` component, tooltips and lang, the recipes (crafting, smithing, the anvil wake), the loot modifiers and the Hollow lair roll, the signature registry with faded copies and re-forming | R1, A1 | M | `blacksmithCraftsSmithsAndWakes` (its recipe half), `heroDropsArtifactsAtDeath` (its item half), `signatureFadedCopyAndReform` |
| R3 | The hero side: slots, saving and the slot screen with pins, attuning, the build stats in `HeroDamage`, `HeroCombat` cooldowns, `HeroStatus` control and `HeroMovement` stamina, passives through `HeroPassives` with families, actives through the move runner, the bond's kit mods, the healing ceiling, death drops, the boss bar pips, the hero sheet, the war rating in `HeroRules.heroPower` and the councils | R1, R2, A4, A5, A6, A10 | L | the GameTests of 18.2 on the hero; every "NPC and mock player" test gives the same numbers for both |
| R4 | `ArtifactPlanner` (pure): profiles, scoring, personality, distribution, goal builds and orders, hysteresis | R1, F4 | M | `ArtifactPlannerTest` |
| R5 | Faction AI: the planner in the director, the smithing table in the workshop (shared with ROADMAP phase 3's netherite row), the blacksmith's artifact orders and wakes with the faction's experience, equip and swap at bases, hauler deliveries, loot priority, recovery trips, raid looting, chat lines, the station section, `sim.py` lines | R3, R4, L3, L5m | L | the remaining GameTests of 18.2; the campaign check of 18.3 |
| R6 | Art and sound: `herogen icons` with the shape library and the 51 icons (contact sheet for the owner), the three core clips (`artifact.attune`, `.raise`, `.cast`), the `artifact` sound set (`SOUND.md` additions) | G1, G4, S1, R1 | M | icons pass `herogen check`; the sheet is sent to the owner; `sfxgen check` clean |
| R7 | Docs and release: README "Artifacts", BALANCE rows, `docs/factionclash-common.toml`, the "ARTIFACTS" section in every kit sheet, the overview regenerated from the data, jar and SHA-256 | R5, R6 | S | the release checklist (`HANDOFF.md` section 7) |

---

## 20. Open questions for the owner

1. **Three slots for every hero** (no more for rarer heroes or leaders; power grows with level instead). Would you
   rather have more slots with smaller artifacts (four to six, MOBA-like), or fewer?
2. **Waking at the anvil.** Advanced and greater artifacts cost experience levels to wake, as enchanting does. Keep it,
   or let them come off the smithing table ready?
3. **On the model.** Artifacts show on the boss bar (coloured squares) and the hero sheet, not on the hero's body
   (except, perhaps, the two signature artifacts). Do you want a small visible charm on heroes too?
4. **Players and their faction's heroes.** A member player can put artifacts on its faction's heroes (the AI then keeps
   them there) and take them off again. Is that the control you want, or should the faction AI always decide?
