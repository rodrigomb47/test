# Breaching, explosives and lethal terrain (ordinary NPCs)

How faction NPCs use explosives in war, how they defend against them, and how ordinary NPCs live with a battlefield
where anyone may be knocked into lava or off a cliff. Heroes follow the same world rules; their own parts (moves that
push into lava, moves that are explosions) are in `docs/heroes-v2/HEROES_V2.md` 6.17 and 6.18.

Status: design, 2026-11-18, reviewed the same day (section 15). Nothing here is implemented. It designs ROADMAP
phase 2's "TNT expert" row and the ordinary-NPC side of the owner's battle answers. Numbers marked PROPOSED go to
BALANCE.md when they are built; the work breakdown is section 12, the proposed further tactics section 13.

---

## 0. The request and the decisions

The owner's words (2026-11-18, `docs/HANDOFF.md` section 2, request 11), answering question 3 of
`docs/heroes-v2/BOSS_HEROES.md` 11 (the Wither's breach): "its ok for any hero or npc that usrs explosives to breach
walls, theres no need for anyone to be "civil" in battle, so they may use the resources they have to full advantage".
And later the same day: "its fine for heroes abilities to push someone of a cliff or to lava".

The spec's TNT expert (spec §4 and §15; ROADMAP's row: "Crafts TNT (gunpowder from creepers, sand) and uses it to
breach walls during raids; never near its own base") becomes the craftsman and specialist of a wider rule: **anyone
who carries explosives may use them to breach**.

Decisions (PROPOSED; the owner may still correct them, section 14):

1. **TNT is the explosive of war.** It is the one vanilla explosive that a player crafts from common things
   (gunpowder and sand), carries, places and lights anywhere. Section 2 says what else is in and out, and why.
2. **Player-like from start to end.** Gunpowder comes from creepers killed at night (and from witches, and later
   ghasts); sand is dug at player speed; TNT is crafted at a crafting table from 5 gunpowder and 4 sand; a flint and
   steel is crafted from an iron ingot and flint knapped out of gravel. The TNT is carried in a backpack, held in the
   hand on the way in, placed as a block within a player's reach and lit with the flint and steel. Its 4 s fuse,
   its blast and what it breaks are vanilla's.
3. **A new role, the TNT expert**, keeps each base's TNT stock and leads breaches on raids. It fights as a swordsman
   when it is not breaching (section 4).
4. **Any carrier may breach.** The TNT expert first, then a soldier the war council hands TNT to, a hero that carries
   it, or anyone who picked up a fallen breacher's TNT and has a way to light it (section 5).
5. **The breach is planned**: a wall segment away from the gate, facing the party, opening into the target's yard
   and as close to its heart as the walls allow, never next to water or lava (section 6.2).
6. **Never near its own base**, and never lit while its own side stands close. Collateral is real: whoever walks
   into a blast is hurt by it, friend or foe (section 6.5).
7. **The defenders answer like players**: they shoot the carrier first, pour water on a lit charge, and rebuild the
   hole afterwards (today's repair of explosion damage already does that, section 7).
8. **Ordinary NPCs defend themselves against lethal terrain** as a careful player does: they keep away from lava and
   edges while a pushing enemy is near, get out of lava at once, drink Fire Resistance when they carry it, and break
   a fall with a water bucket when they carry one (section 8).
9. **A death by terrain is the pusher's kill.** A fall, lava, fire or the void within 15 s of an enemy's push or blow
   counts as that enemy's kill, with vanilla's wording ("was doomed to fall by", "tried to swim in lava to escape").
   The rule is shared with heroes (`HEROES_V2.md` 6.17).
10. **Operators can turn it off** with `war.explosives` (on by default). The game rule `mobGriefing` keeps gating only
    what vanilla gates, mob explosions (a hero's explosive moves), not the TNT a breacher places, which is a player's
    TNT (section 10). The owner wants no civility, so it is on.

**Improvements added on our own** (the owner welcomes them; each is said here so it can be refused):
- the defenders' water answer and the archers' priority on a TNT carrier;
- the ordinary NPCs' defence against lethal terrain (the edge sense, getting out of lava, Fire Resistance, the water
  landing), which the owner's lethal-terrain answer makes necessary: without it heroes would farm soldiers at every
  cliff;
- the flint knapping (gravel broken until it gives flint), the player's way to a flint and steel;
- the list of further tactics in section 13, as proposals only.

---

## 1. Principles

- **Player-like.** Everything an NPC does with explosives a player could do with the same items in the same time:
  real items from real sources, a block placed within 4.5 blocks, a flint and steel that wears, a 4 s fuse, vanilla's
  blast. No NPC throws TNT, drops it from the air or lights it from afar.
- **Equal treatment.** A player's TNT and an NPC's TNT are the same TNT. A player may break into an NPC base with TNT,
  steal a breacher's TNT, pour water on a charge or rebuild a hole, and an NPC may do the same to a player's base. No
  rule here reads whether anyone is a player.
- **No civility, but no stupidity.** Raiders blow walls, defenders kill carriers, heroes push people into lava. What
  a player would not do on purpose, an NPC does not either: blow up its own base, light a charge beside its friends,
  stand next to its own fuse.
- **Vanilla stays whole.** TNT, its recipe, its blast, the drop rules and the game rules are vanilla's; the mod adds
  only who uses them and when.

---

## 2. What counts as an explosive (in and out)

| Explosive | In or out | Why |
|---|---|---|
| TNT (block, lit by flint and steel or a fire charge) | **in** | crafted from common things, carried, placed and lit anywhere; the classic breach |
| TNT dropped by a breacher, looted from a storehouse, or found in a desert pyramid's trap | **in** | it is TNT; whoever holds it and can light it may use it |
| Minecart with TNT | out for now | it needs rails to the wall and a push; it comes with ROADMAP phase 2's rail engineer as a proposal (13) |
| End crystals | out for now | they can only be placed on obsidian or bedrock, so a breach means placing obsidian against the wall first; the crystal costs a ghast tear and an eye of ender. A late, costly tactic: a proposal for after the Nether and End expeditions (13). Against the Ender Dragon they are hers, not a tool |
| A creeper led on a lead | out | vanilla refuses a lead on any hostile mob (`Mob.canBeLeashed` is false for every `Enemy`), so no player can do it; luring creepers to a wall is too unreliable to plan |
| Beds outside the Overworld | out for breaching | they explode only in the Nether and the End, where factions have no walls to breach; a proposal for the End raid against the dragon, as players use them (13) |
| Respawn anchors outside the Nether | out for now | charged with glowstone and used in the Overworld they explode like a bed, but they need crying obsidian and glowstone from the Nether, and the user stands beside the blast; a proposal after Nether expeditions (13) |
| Wind charges | out for breaching | they break no blocks; they push people and open doors and trapdoors, which is a different tactic (13) |
| Firework rockets with stars | out for breaching | they hurt people, never blocks (a crossbow tactic, 13) |
| Fire charges | in, as a lighter only | a fire charge lights TNT as a flint and steel does (it needs blaze powder, so it comes after Nether expeditions) |

---

## 3. Getting TNT (the faction's supply chain)

All PROPOSED. Each part uses a job or item flow the mod already has where it can.

- **Gunpowder.**
  - Creepers: the TNT expert (and any guard) hunts creepers at dusk and at night within the base's work belt, as a
    guard patrols; a creeper is struck, then the NPC jumps back from the fuse (`CreeperEvadeGoal`, today's
    creeper jump-back) and strikes again. Vanilla's loot table drops 0-2 gunpowder (Looting adds one); the killer
    picks it up as it picks up any drop, and it goes to the storehouse.
  - Witches drop gunpowder too (the same rule), and ghasts once Nether expeditions exist (ROADMAP phase 3).
  - Loot: gunpowder and TNT found in raided storehouses, dungeons and desert pyramids (whose trap holds 9 TNT, dug
    out at player speed once the pressure plate is broken) go home as loot.
  - Trade: no villager sells gunpowder in vanilla; a wandering trader may, which is a proposal for the trader (13).
- **Sand.** The TNT expert digs sand (or red sand) with a shovel at player speed when the storehouse has fewer than
  `war.sandReserve` (16): first within the work belt, then the nearest beach, river bed or desert it knows (a scout's
  sighting or its own search), at most 160 blocks out, as the woodcutters dig their own dirt (ROADMAP phase 1).
- **Flint and steel.** The blacksmith crafts it from 1 iron ingot and 1 flint. Flint comes from gravel: miners bring
  gravel home; with no flint stored, the TNT expert knaps it: it places a gravel block and breaks it with a shovel,
  again, until a flint drops (10% a break, more with Fortune; vanilla's loot table), as a player does.
- **Crafting.** At the workshop's crafting table: 5 gunpowder and 4 sand make 1 TNT. The TNT expert keeps
  `war.tntStock` (6, PROPOSED) TNT per base, never spending gunpowder or sand another order has claimed. TNT lies
  in the storehouse like any block; in a chest it is inert (vanilla), so a stock is no danger to its own base.

---

## 4. The TNT expert

- **Role** `NpcRole.TNT_EXPERT`: a soldier role. It carries a sword and a shield and fights as a swordsman whenever it
  is not breaching; it counts by heads as one soldier in recruitment, the garrison and a raid party, and its power is
  a swordsman's (the war council's weighing is unchanged).
- **Hiring** (PROPOSED): one per base once the base has 16 people (`war.tntExpertPopulation`) and the faction has
  raided at least once or been raided; a second at the capital when its leader's aggression is 65 or more. Recruited
  with real items like any soldier. A leader's personality moves the population threshold as it moves other hires
  (`LeaderBiases`, a new knob: bold leaders hire it sooner, cautious ones later; neutral equals 16).
- **At home**: the supply chain of section 3 (sand, knapping, crafting, night creeper hunts in the work belt); it
  answers alarms as a guard does.
- **On a raid**: it is a breacher (section 6) and joins the assault after its breach.
- **Levels and affixes**: like any soldier. A new affix fits it: **Steady Hands** (PROPOSED: its charge never misses
  the planned cell and it knaps flint with half as many breaks).

---

## 5. Who may breach

A **breacher** is any NPC of a raiding party that carries at least 1 TNT and a lighter (a flint and steel, or a fire
charge). At the muster the war council names at most 2 breachers per party (`war.breachersPerRaid`), in this order:

1. the TNT expert(s) of the raiding base;
2. a hero of the party whose raid goal is "open the way" (`MOBILITY_AND_WORK.md` 5.2) and who carries TNT: a hero
   is a faction NPC and breaches exactly as one, at the same speed, by the same rules (placing within reach, lighting
   by hand, standing clear). A hero with that goal and no explosive move of its own takes `war.tntPerBreacher` TNT and
   a flint and steel at the muster when the base's stock allows, as a swordsman would, so "any hero" can really
   breach;
3. a swordsman the council hands TNT to when the party has no TNT expert: it takes `war.tntPerBreacher` (2) TNT and a
   flint and steel from the storehouse at the muster, as guards take building blocks today.

Anyone else who picks up TNT during a raid (a fallen breacher's, a raided storehouse's) and carries a lighter may
finish a breach that was planned and not yet blown. A defender who picks it up keeps it as loot and never uses it at
home (6.5).

---

## 6. The breach on a raid

### 6.1 When the council plans one

A raid plans a breach only when its party carries TNT (section 5) and explosives are allowed (section 10), and at
least one of these holds when the party reaches the rally point and its breachers can see the target's walls:

- the gateway is not a way in for NPCs: an iron door with no button or lever on the outside, a gateway a player
  blocked, or any gate the path search cannot pass;
- the party was beaten back at this base's gate in its last raid on it (a raid memory per target, kept 5 in-game
  days);
- the heart is far from every gate (more than 24 blocks along the inside path), so a breach on its side shortens the
  assault;
- the leader is bold (aggression 65 or more) and the party carries 2 TNT or more: a second way in, so the defenders
  holding the gate are split.

Without TNT nothing changes: soldiers take the gate, and heroes open the way (`MOBILITY_AND_WORK.md` 5.2).

### 6.2 Where: the breach spot (`logic/war/BreachPlanner`, pure)

The planner reads the target's wall blocks as the breachers see them (a player looks at a wall before blowing it),
and scores each outside cell at the foot of the wall ring on the party's side:

| Factor | Rule (PROPOSED) |
|---|---|
| Away from the gates | at least 6 blocks from any gate; defenders gather there |
| Toward the heart | shorter inside path from the opening to the heart is better |
| Out of the archers' sight | fewer known defender archer posts (the gate posts, the station plaza: `GuardJob.choosePost`) with a line to the spot is better |
| Breakable | the expected opening of one TNT (vanilla's ray rule, power 4) must be at least 1 wide and 2 high: walls of stone bricks, cobblestone, deepslate bricks, planks or bricks (blast resistance 6 or less) open about 3 wide and 3 high; end stone (9) barely; obsidian, crying obsidian, reinforced deepslate, anvils and anything of blast resistance above about 16 never break, so the planner never picks them |
| Dry | no water or lava within 2 blocks of the charge cell: a fluid absorbs the blast (vanilla) |
| Into the yard | the cell behind the wall is open yard, not a building's room, a farm, a pen or a mine shaft |
| Reachable | the outside cell is standable and on the party's path, not over a drop |

The best cell wins; with none, no breach. The spot is shown to nobody (players learn it when the charge is placed).

### 6.3 How: place, light, stand clear

1. **The approach.** At the assault signal (today's `RAID_ASSAULT`: 70% of the party within 12 blocks of the target),
   each breacher walks to its spot holding the TNT in its main hand, so anyone who looks can see what it carries. The
   two nearest swordsmen escort it with shields raised toward the wall; the party's archers shoot the defenders on
   and behind the wall; the rest of the party presses the gate as raids do today, so the defence is split.
2. **Placing.** From within 4.5 blocks (a player's reach) it places one TNT on the ground against the wall's outside
   face (a real block place from its backpack; the cell must be empty and its floor solid).
3. **Lighting.** It waits until no member of its own side (an NPC, a hero or a member player) but itself and its
   two escorts, who leave with it, is within 4 blocks of the charge, at most 3 s; then it uses its flint and steel
   on it (one point of wear, vanilla's click) and says "Charge set! Clear the wall!" (a speech bubble for players
   near; presentation).
4. **Standing clear.** It and its escorts move at least `war.tntClear` (8) blocks away within the 4 s fuse (vanilla's
   TNT hurts living things within 8 blocks), shields toward the charge.
5. **The blast.** Vanilla's TNT explosion (power 4). The igniter is the TNT's owner, so a kill counts as the
   breacher's ("was blown up by"). Drops follow `tntExplosionDropDecay` (false by default: every broken block drops).
   The target's planned blocks that broke are queued for repair at once (today's `ExplosionEvent.Detonate` hook).
6. **Through.** The raiders' path search now finds the opening; they go in through it or the gate, whichever is
   shorter. Hand-breaking rules are unchanged: a raider still never breaks a planned block by hand (`Reach.mayBreak`).
7. **A second charge.** If the path is still closed 2 s after the blast (a thicker wall, a fluid that absorbed it),
   the breacher places its second TNT in the crater and repeats steps 3-5. At most 2 charges per breach spot.
8. **After.** The breacher joins the assault as a swordsman. Left-over TNT stays in its backpack for the next breach
   or goes home with it.

### 6.4 Timing with the assault

The breach runs inside the assault, not before it: the party is already at the walls when the charge goes off, so
the defenders cannot rebuild the hole before the raiders reach it (repairs happen once the alarm is over, as today).
A breacher that dies before lighting drops its TNT; the nearest party member with a lighter takes over (5). The raid
does not wait for a breach: a party that gets in by the gate goes on, and an unused breach is called off.

### 6.5 Never near its own side

- **Never at home.** No NPC places TNT, lit or not, inside or within 16 blocks of the walls box of a base of its own
  faction or of a pact ally (`war.tntHomeMargin`). Defenders never use TNT at their own base, even against raiders
  inside (a player does not blow up their own base on purpose).
- **Never beside its friends.** A charge is lit only with no member of its own side within 4 blocks but the breacher
  and its escorts, who stand clear with it (6.3). After that, collateral is real: a friend who walks into the blast is
  hurt by it, as a player's friend would be.
- **Never on someone else's walls by accident**: a breach spot is always on the target's wall ring; nobody places TNT
  against a neutral village, a factionless player's house or the Hollow's lairs (lairs have no walls; raids on them
  are unchanged).

---

## 7. The defenders' answers

1. **Shoot the carrier.** An enemy holding TNT in its hand within 32 blocks of a base's walls is the first target of
   that base's archers and crossbowmen in sight and in range, before an airborne hero (`MOBILITY_AND_WORK.md` 5.2's
   `AirborneHeroTargetGoal` gains this step above its own). The defence alarm hands the two nearest free swordsmen a
   carrier within 16 blocks of the wall. The same for a player carrier and an NPC carrier.
2. **Water on the charge.** A guard who carries a water bucket and sees a lit charge within 6 blocks of its base's
   wall, close enough to reach it before the fuse ends, empties the bucket on it and picks the water up again once it
   has gone off. A charge in water breaks no blocks (vanilla: the water absorbs the blast); it still hurts whoever
   stands close, the guard included if it stays. Guards take one water bucket each from the storehouse when it has
   buckets (`war.guardWaterBuckets`), filled at the base's water.
3. **Rebuild.** Today's repair: planned blocks broken by an explosion are queued for repair at once and the builders
   rebuild them from the storehouse once the alarm is over. A broken door is put back even when none is stored (the
   builder crafts one).
4. **Remember.** A breached base notes the side and the material that failed; a cautious leader may answer with
   stronger walls or a moat later (proposals in 13).

A player defending a base may do all of this too, and a player raiding one meets all of it.

---

## 8. Lethal terrain for ordinary NPCs

Heroes may now push, throw and pull anyone into lava, fire, the void or off a cliff, on purpose (`HEROES_V2.md`
6.17), and soldiers with a Knockback sword or a Punch bow, ravagers, hoglins, iron golems and wind charges did so
already. Ordinary NPCs answer as a careful player does. None of this is new movement: they walk, swim and use items.

### 8.1 The edge sense (`logic/combat/EdgeSafety`, pure)

- A **lethal edge** is a cell that holds lava, fire, a magma block or a campfire, opens on the void, or drops more
  than 4 blocks (vanilla's mobs take fall damage after 3; 4 keeps steps and ledges usable), within the reach of the
  pushes near: 6 blocks of a standing cell while a hero is among the pushers (a hero's knockback 3, the strongest,
  throws about 6 blocks on flat ground: vanilla's knockback of 0.4 a point, `HEROES_V2.md` 6.17), else 4 blocks (a
  Knockback II sword, a Punch bow, a ravager's roar or a wind charge). The review of 2026-11-18 widened it from a flat
  3 blocks, which a hero's push outranged twice over.
- A **pusher** is an enemy within 8 blocks that can push: any hero; any NPC or player holding a weapon with Knockback
  or a bow with Punch; ravagers, hoglins, zoglins, iron golems and breezes; anyone holding wind charges.
- While fighting with a pusher near, an NPC (soldier, guard, worker, TNT expert) prefers cells with no lethal edge
  behind it, away from that pusher: its path to its target costs 4 more per such cell (PROPOSED), its waiting ring,
  cover spot and an archer's back-off spot skip them, and a melee fighter strikes from the side of its target that
  keeps the edge to its flank, not its back. The same rule serves heroes, whose own movement already never steps into
  a hazard (`MOBILITY_AND_WORK.md` 5.1.13).
- It never makes an NPC refuse a fight: with no safe cell in reach it fights where it is.

### 8.2 Getting out of lava (`LavaEscapeGoal`, priority 0 beside `FloatGoal`)

An NPC in lava stops everything else and heads for the nearest standable cell that is not lava, jumping and swimming
as vanilla's movement allows (a player jumps out at once; vanilla mobs flounder). On the way out it drinks a potion of
Fire Resistance if it carries one (the 1.6 s drink, as a player). Out of the lava and still burning, it steps into
water within 6 blocks if there is any, else goes back to its fight.

### 8.3 Fire Resistance

Any NPC that carries a potion of Fire Resistance drinks it when it is in lava, or when it has burnt for 3 s with no
water within 6 blocks. Soldiers going to the Nether (ROADMAP phase 3: expeditions and fortress raids) take one each
from the storehouse once the faction brews them (Nether wart and magma cream at a brewing stand, as a player brews;
the field medic of phase 2 brews them when the faction has the ingredients).

### 8.4 The water landing

An NPC that carries a water bucket and falls more than 5 blocks over a solid floor empties the bucket on the floor
when it is 2 to 3 blocks above it, lands in the water unhurt, and picks the water up again (`war.waterLanding`, on).
Not over lava (water poured there sets as stone or obsidian, not a pool), not in the Nether (water evaporates there),
and not over the void. It is the player's well-known trick, done with a real bucket at the same
moment a player would; it takes the bucket out of the backpack as a player switches hotbar slots.

### 8.5 Credit, chat and items

- **Kill credit** (shared with heroes, `HEROES_V2.md` 6.17): a death by a fall, lava, fire, a magma block, the void,
  drowning, freezing, suffocation or a cactus within 15 s of an enemy's push, throw, pull or blow counts as that
  enemy's kill. A push counts even when it dealt no damage. The death message is vanilla's own with that killer:
  "Ash Rook was doomed to fall by Benika Akagane", "... tried to swim in lava to escape ...", "... walked into fire
  while fighting ...", "... didn't want to live in the same world as ..." (the void), "... drowned while trying to
  escape ..." (vanilla's `.player` death keys). Vanilla keeps an attacker 5 s; the mod refreshes the pushed
  receiver's last attacker every second while it falls, burns or is in lava, the void or water, up to the 15 s, so
  vanilla's own message (a player's death) names the pusher too.
- **Chat**: the faction is told of the death as its knowledge allows (ROADMAP phase 1: at once near a base or
  through a witness), with the killer's name when it knows it.
- **Items**: an NPC's backpack drops where it dies, as today. In lava, ordinary items burn and experience orbs with
  them (vanilla); fire-resistant items (netherite) float up out of it; in the void everything is lost. Artifacts
  and hero tokens follow their own designs (`docs/heroes-v2/ARTIFACTS.md` 11: they never burn and float up out of
  lava; `BOSS_HEROES.md` 3.4-3.5: a token lost in lava or the void comes back to its chest spot).

---

## 9. Heroes and explosives

- **Heroes as breachers**: section 5. A hero carries TNT and breaches by hand like any NPC; its mobility does not
  change the act (it may not drop TNT from the air or place it beyond reach).
- **Explosive moves**: a move that is an explosion by nature breaks blocks as a vanilla explosion of its power does
  (`HEROES_V2.md` 6.18). On a raid with soldiers held outside, a hero with such a move may blast the breach spot of
  6.2 instead of placing TNT (the one use of a move on a wall, and only in that goal). None of the eight slice heroes
  has one.
- **The Wither** breaches by the rule above, with no exception of her own (`docs/heroes-v2/BOSS_HEROES.md` 4.5,
  revised 2026-11-18): her blue skulls (Ruin Skull) are an explosive move of power 1 that keeps the vanilla blue
  skull's rule (every block but the wither-immune ones counts as no stronger than 0.8), so to the planner of 6.2 every
  wall is breakable by her but bedrock-class blocks, obsidian included; she casts it at the spot up to three times
  (one skull bites less than one TNT), then joins the assault. Her hands no longer breach.
- **Pushes into lava and off cliffs**: `HEROES_V2.md` 6.17; ordinary NPCs defend as section 8 says.

---

## 10. Config, game rules, knowledge

- **`war.explosives`** (true): false stops all planned breaching (no TNT expert is hired, no breacher is named, no
  hero blasts a wall). TNT already lit goes off as vanilla's.
- **The game rule `mobGriefing`** gates what vanilla gates and nothing more: mob explosions, so a hero's explosive
  moves (the Wither's blue skulls among them, `HEROES_V2.md` 6.18) and the Wither's box break-out. A breacher's TNT is
  a player's TNT, placed and lit by hand, which vanilla never gates by that rule; an NPC's hand-breaking (`Reach`)
  ignores it today too, and a player's TNT keeps working with it off, so an NPC's must as well (equal treatment,
  player-like). `war.explosives` false is the switch that stops planned breaching. (The first draft asked the owner
  whether `mobGriefing` should also stop NPC TNT; the review settled it this way, section 15.)
- **Other keys** (PROPOSED, `[war]`): `tntExpertPopulation` 16, `tntStock` 6, `sandReserve` 16, `tntPerBreacher` 2,
  `breachersPerRaid` 2, `tntClear` 8, `tntHomeMargin` 16, `guardWaterBuckets` true, `waterLanding` true.
- **Knowledge** (player-like): a faction knows its own TNT stock; rivals do not, unless a scout sees a breacher on the
  march (a carrier holding TNT is visible). A breach on a base is known to that base at once (its people are there).
  Breaching is not priced into the attack odds at first, as heroes' wall crossing is not; the campaign checks count
  the raids a breach decided (11.3), and the council's weighing is tuned if breaches make raids too easy.

---

## 11. Tests

### 11.1 Unit (pure)

- `BreachPlannerTest`: the spot is 6+ blocks from a gate, never next to water or lava, never into a building, never
  in or within 16 blocks of an own-side base; stone bricks give an opening, obsidian never; the inside path to the
  heart decides between two otherwise equal spots.
- `TntSupplyTest`: what the TNT expert does next for a given storehouse (dig sand, knap flint, craft, hunt).
- `EdgeSafetyTest`: lethal edges (lava, fire, magma, the void, a drop of 5) found and a drop of 3 not; with a hero
  among the pushers an edge 6 blocks behind counts, with only a Knockback sword near one 5 blocks behind does not; the
  path cost only with a pusher near; the strike side.
- `KillCreditTest` (with `HEROES_V2.md` 6.17): the 15 s window, a push with no damage credited, the newest pusher
  wins, the vanilla message key for each terrain death.

### 11.2 GameTests (each its own `batch`; spawns at known relative positions on the flat test world; test bases through `GameTestBases.register`, cleaned up)

- `tntExpertCraftsTnt`: 5 gunpowder and 4 sand in a test base's storehouse become 1 TNT at its crafting table.
- `tntExpertKnapsFlint`: with gravel and no flint, it places and breaks gravel until a flint drops.
- `breacherBlowsAWall`: a test base with a planned stone-brick wall; the breacher places a TNT against it, lights
  it, stands 8 blocks clear and is unhurt; the wall has an opening at least 1 wide and 2 high; the broken planned
  blocks are in the base's repair queue.
- `breacherNeverLightsBesideItsSide`: with its own soldier standing next to the charge cell it waits, and lights once
  the soldier has moved off.
- `breacherNeverPlacesAtItsOwnBase`: a raid plan whose only wall cells are within 16 blocks of its own base picks no
  spot.
- `waterStopsABreach`: a guard with a water bucket empties it on the lit charge; no block breaks.
- `archersShootTheCarrierFirst`: with an NPC carrier holding TNT and an NPC swordsman both in range, the archers take
  the carrier; the same with a mock player holding TNT (equal treatment).
- `tntKillCountsForTheIgniter`: a soldier killed by a breacher's blast is the breacher's kill ("was blown up by").
- `npcKeepsTheLavaEdgeAway`: a soldier fighting a hero beside a lava pool approaches from the side that keeps the
  lava off its back; with no hero near it walks the short way.
- `npcClimbsOutOfLava`: a soldier set in a lava pool is out within 3 s and drinks its Fire Resistance potion.
- `waterLandingBreaksAFall`: a soldier with a water bucket dropped from 12 blocks lands unhurt and has its bucket
  back with water.
- `breachWorksWithMobGriefingOff`: with `mobGriefing` false a breacher's TNT still opens the wall, as a mock
  player's does; with `war.explosives` false no breacher is named.
- `heroBreacherTakesTntAtMuster`: a hero whose raid goal is "open the way" and who has no explosive move takes 2 TNT
  and a flint and steel at the muster from a stocked storehouse.
- `pushedIntoLavaIsThePushersKill`: an NPC soldier and a mock player each knocked into lava by an NPC with a
  Knockback II sword die as its kill, with vanilla's lava message naming it (the hero version is in `HEROES_V2.md`
  14.5).

### 11.3 Campaign checks (`docs/HANDOFF.md` section 4)

Two campaigns of 6 days with `war.explosives` on: breaches per raid, how many opened a way, blocks broken and
repaired per day, NPCs killed by their own side's TNT (expected under one per campaign day), TNT placed within 16
blocks of an own base (must be 0), carriers shot before placing, water defusals, deaths by terrain and their
credit, NPCs stuck in lava (must be 0), tick time. `sim.py` prints a breach line per raid.

---

## 12. Work breakdown (ROADMAP phase 2, "TNT expert")

| ID | Task | Depends | Size | Done when |
|---|---|---|---|---|
| X1 | Pure rules: `BreachPlanner` (spot scoring, vanilla's ray rule for the expected opening), `EdgeSafety`, `KillCredit` (shared with heroes), the TNT supply decisions | - | M | the unit tests of 11.1. **`KillCredit` done 2026-11-19** with `HEROES_V2.md` T1's (`logic/hero/KillCredit`, `KillCreditTest`; `HEROES_V2.md` 15.2 "Shipped heroes, kill credit and the kill feed, mobility polish"); the rest open |
| X2 | The TNT expert: role, hiring and the `LeaderBiases` knob, sand digging, flint knapping, creeper hunts, crafting at the workshop, the stock, the Steady Hands affix | X1 | M | `tntExpertCraftsTnt`, `tntExpertKnapsFlint` |
| X3 | The breach on raids: breachers named at the muster, the approach with escorts, place and light, standing clear, the second charge, timing with the assault, the own-side rules, heroes as breachers, `war` config, `sim.py` lines | X1, X2 | L | the breach GameTests of 11.2 |
| X4 | The defenders: archers' carrier priority, the alarm's carrier hand-off, water buckets for guards and the water answer, the breach memory | X3 | M | `waterStopsABreach`, `archersShootTheCarrierFirst` |
| X5 | Lethal terrain for ordinary NPCs: the edge sense in paths and spot choice, `LavaEscapeGoal`, Fire Resistance, the water landing, the kill credit refresh and chat (also `HEROES_V2.md` 15.9 T3) | X1 | M | the terrain GameTests of 11.2. **The kill credit refresh done 2026-11-19** (`combat/KillCredits`: a push as a hit of 0, the last attacker refreshed every second while the receiver falls, burns or is in lava, the void or water, up to 15 s, the credit at a death, for every receiver; the faction chat by knowledge waits for ROADMAP phase 1); the edge sense, `LavaEscapeGoal`, Fire Resistance and the water landing are open |
| X6 | Campaign checks of 11.3, README ("Combat behaviour", the roles table), BALANCE rows, the config file, jar and SHA-256 | X1-X5 | M | the release checklist (`docs/HANDOFF.md` section 7) |

X5 should land with or before `HEROES_V2.md` T1 (heroes' lethal pushes), so soldiers can defend themselves the day
heroes start aiming at edges.

---

## 13. Using what they have to full advantage: further tactics (PROPOSED, not committed)

The owner's "they may use the resources they have to full advantage" opens more player-like tactics. Each line is a
proposal for the owner to pick from; none is planned work until the owner chooses it.

- **Lava buckets**: raiders pour lava into a gateway or onto defenders on a wall; defenders pour it down on a breach.
- **Fire**: a flint and steel on an enemy's wooden houses, doors and lumber stacks; Flame bows setting roofs alight.
- **Splash and lingering potions**: Harming, Poison, Weakness and Slowness thrown by soldiers once the faction brews.
- **Cobweb traps**: webs placed in a gateway or a breach to hold raiders under the archers (webs from shears).
- **Pit traps**: a pit dug under a gateway, covered by an open trapdoor or a carpet.
- **Tripwire traps**: dispensers with arrows, or buried TNT under a pressure plate, on the approach to the gate.
- **Water moats** dug round the walls so a charge at the wall's foot sits in water and breaks nothing.
- **Blast-resistant walls**: an obsidian-faced gate or wall for a faction that was breached (obsidian made with water
  on lava, or mined with a diamond pickaxe).
- **Piston gates**: a sticky-piston gate that defenders close from inside (with the tech tree of phase 6).
- **Sapping**: tunnelling under the wall at player speed to come up inside the yard.
- **Scaling**: soldiers pillar up the outside of a wall with dirt, or place ladders, to get over it.
- **Ender pearls**: thrown over a wall by a raider (the mod's rule against NPC teleporting would need an exception).
- **Wind charges**: knock defenders off a wall top, or open a door or press a button from range (from trial chambers).
- **Fishing rods**: hook a defender on a wall and pull it off.
- **Snowballs and eggs**: knock a player or a soldier off a ledge (they push, as vanilla's do).
- **Crossbow fireworks**: a crossbowman's firework rockets with stars against massed raiders.
- **End crystals**: obsidian placed against a wall, a crystal on it, and an arrow from range (after Nether and End
  expeditions).
- **Respawn anchors** as an Overworld bomb (after Nether expeditions).
- **Beds and end crystals against the Ender Dragon**, as players fight her (ROADMAP phase 3's End raid).
- **TNT minecarts** sent down rails into a base (with the rail engineer of phase 2).
- **TNT cannons**: dispensers and water that throw lit TNT over a wall (with the tech tree of phase 6).
- **Darkness**: breaking an enemy base's torches at night so monsters spawn inside.
- **Gunpowder by trade**: the trader buys gunpowder from a wandering trader near the base, when one offers it.
- **Economic raids**: trampling or burning an enemy's farms, killing its pen animals, blocking its mine shafts.
- **Luring**: leading the Hollow's monsters, or a summoned Wither, toward an enemy base.
- **Boats and minecarts as traps**: a monster or a soldier pushed into a boat cannot walk out (vanilla's own cheese,
  so heroes would need an answer before NPCs use it).

---

## 14. Questions for the owner (settled by the review)

No question needs the owner now. The first draft asked two; the review (section 15) settled both with the reasons
below, and the owner may still overturn either:

1. **A TNT expert role, or any soldier making TNT?** *Settled: the role.* The spec itself names a TNT expert (spec §4
   and §15, ROADMAP phase 2), and the owner's "any hero or npc that usrs explosives" is kept by section 5: any carrier
   may breach, a hero included. One specialist per base keeps the gunpowder economy readable (one NPC hunts, digs and
   crafts) and gives raids a visible target to shoot.
2. **Should `mobGriefing` false also stop NPC TNT?** *Settled: no.* The draft recommended yes. But a breacher's TNT is
   a player's TNT, placed and lit by hand; vanilla never gates TNT by that rule, a player's TNT keeps working with it
   off, and the mod's NPCs already break blocks by hand regardless of it (`Reach`). Gating only the NPC's TNT would
   treat a player and an NPC differently in a fight. `war.explosives` is the switch (section 10); `mobGriefing` still
   gates mob explosions, so a hero's explosive moves (`HEROES_V2.md` 6.18).

The further tactics of section 13 are proposals, not questions: none is planned until the owner picks one, and none
is needed for the TNT expert.

---

## 15. Review (2026-11-18)

An adversarial pass over this file and the heroes' half of the battle rules (`HEROES_V2.md` 6.17 and 6.18), against
the owner's answer 5 and the lethal-terrain remark, the earlier owner decisions and the engine's and vanilla's real
numbers (`HeroEffects.knockback`, `FormGeometry.KNOCKBACK_PER_STRENGTH` 0.4, `FCEvents.onExplosion`, `Reach.mayBreak`;
vanilla's `LivingEntity.hurt`, `CombatTracker`, `Mob.canBeLeashed`, `WitherSkull` and the explosion ray rule in the
1.21.1 sources). **The owner's answers hold**: any carrier breaches, nobody is civil (raiders blow walls, defenders
kill carriers, heroes aim at edges), and vanilla's TNT, blast and drop rules are untouched. **Earlier decisions hold**:
equal treatment (a player's and an NPC's TNT are the same TNT, a mock player is a test subject beside every NPC),
player-like NPCs (real gunpowder, sand, flint, a 4.5-block reach, a hand-lit 4 s fuse, a water bucket used as a player
uses it), vanilla stays whole. **Fixed**:
- *The edge sense was too short*: it looked for lethal edges within 3 blocks, but a hero's knockback 3 throws about 6
  blocks on flat ground (knockback strength 1 is vanilla's 0.4; stepping vanilla's ground and air friction tick by
  tick gives about 2.1 blocks a point, and the engine's own look-ahead assumes 2.5), so soldiers 4-6 blocks from a cliff
  would have died while "safe". It now watches 6 blocks while a hero is near, 4 otherwise (8.1, `EdgeSafetyTest`).
- *`mobGriefing` and NPC TNT*: the draft gated NPC TNT by it, which no vanilla rule does and which would treat a
  player's TNT and an NPC's differently; settled the other way, with `war.explosives` the switch (10, 14).
- *"Any hero" could not really breach*: only TNT experts and swordsmen were handed TNT, so a hero breached only with TNT
  it happened to carry. A hero with the "open the way" goal and no explosive move now takes TNT at the muster (5).
- The two owner questions of 14 are settled, with reasons; section 13 stays a list of proposals.

**Balance, re-checked**: *raids are not made too easy*. Soldiers already open fence gates and doors (README, "Combat
behaviour"), so a breach mostly changes the route and splits the defence; it is planned only when a gate is closed to
NPCs, a raid failed at the gate, the heart is far from every gate, or a bold leader carries 2 TNT; one charge opens
about 3 x 3 in stone bricks (vanilla's ray rule: a power-4 blast at the face has at most 5.2 - 0.63 left, enough for any
block of resistance under about 15) and nothing in obsidian; 2 breachers with 2 charges each is the most a raid brings.
Breaches are not priced into the attack odds; 11.3's campaigns count the raids a breach decided before that changes.
*The defenders' answers are real*: archers take a visible TNT carrier first within 32 blocks, two swordsmen are sent at
it, a guard's water bucket on a lit charge leaves the blast breaking nothing (vanilla: water absorbs it), and planned
blocks are queued for repair the moment an explosion breaks them (today's `ExplosionEvent.Detonate` hook). *Lethal
terrain stays beatable*: every hero push waits for its wind-up and ground mark, and for the walk round (up to 3 s) when
aimed; a raised shield stops the damage of a blocked blow but not its push, in vanilla too, so the answers are distance
from the edge, stepping out of the mark, knockback resistance, a water bucket and Fire Resistance, the same for a
player and a soldier. **Engine fit**: X1-X6 and `HEROES_V2.md` T1-T4 cover every piece, with their tests in 11; kill
credit needs no mixin (`CombatTracker.recordDamage` for a push of 0, `setLastHurtByMob` refreshed each second, and
vanilla's own fall and `.player` death keys, which name the pusher when the fall is over 5 blocks or the last attacker
is fresh). No new owner question.
