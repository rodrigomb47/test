# Arrows: archers use them up, and bases start with plenty

Design of 2026-11-25. Status: **designed and critiqued, not built.**

The owner's request (2026-11-25, verbatim): "archers should consume arrows normally, starting faction bases start with a
large amount of arrows in their storage".

Read with this: `docs/design/knowledge.md` (a faction knows only what its people saw and carried home) and
`docs/design/missing_npcs.md` (the roll call). The war balance stays as the owner left it ("keep a hard defence for
now"). There are no pacts, and a fallen faction's members die with it (`docs/HANDOFF.md` 5).

## 0. Summary

Today an archer or crossbowman holds a single arrow in a slot of its own. That arrow is never used up, and nobody can
pick up its shots. After this change:

| Area | Decision |
|---|---|
| Where arrows live | In the NPC's backpack, as a player's live in its inventory. The separate never-used-up arrow slot stays only for a hero's kit arrow. |
| Shooting | A bow needs an arrow in the backpack, and each shot uses one. A crossbow loads one arrow when its charge completes. Vanilla's `EnchantmentHelper.processAmmoUse` decides what a shot costs, so Infinity spares plain arrows only, exactly as for a player. |
| The arrow it fires | An ordinary vanilla arrow with pickup **allowed**, as a player's shot is. A shot that cost nothing (Infinity, a hero's kit arrow) cannot be picked up, as vanilla marks it. |
| Which arrow | Special arrows first (spectral, tipped with harmful or neutral effects), never one that would help its target: no beneficial tipped arrow at all, and no Harming arrow at the undead it would heal. |
| Picking arrows up | Every faction NPC picks up an arrow it touches, in the same box and under the same rules as a player (`playerTouch`). After a fight, soldiers fetch the arrows they can see within 12 blocks while no enemy is in sight. |
| Carrying | 48 arrows at home and 64 on a raid. A raider stops at the storehouse before it marches and never takes the stock below a reserve of 64. |
| Out of arrows | At home, it runs to the storehouse if the stand is within 32 blocks. Otherwise it fights with a sidearm (a spare sword or axe from the storehouse), else bare-handed. A raider with no arrows and no sidearm leaves the raid as a wounded raider does. |
| Starting stock | **512 arrows** (8 stacks) in each starting settlement's storehouse, from the config key `campaign.starterArrows`. An older save gets them once, at the first director pass with this build. |
| Resupply | Pickups, plus drops from skeletons. The blacksmith crafts arrows from flint, sticks and feathers, and digs flint out of the miners' gravel. The pen keeps chickens (an older save's pen gets the pair once), and the butcher's culls give feathers. The trader buys arrows from fletcher villagers it meets. No new role. |
| Heroes | No change. A bow or crossbow hero, and a dormant hero with a ranged role, keeps its bound kit arrow, never used up, because the kit is its abilities (owner question 2). Its shots become intangible, so a kit is never a source of free arrows. |
| Vanilla mobs | No change. Skeletons, strays and pillagers keep vanilla's unlimited arrows, which cannot be picked up. |
| No off switch | Arrows are always used up. A switch back to the old never-used-up arrow would be a rule only NPCs get (owner question 3; built without it). |

### 0.1 What was asked, and what is added

The owner welcomes additions as long as they are named. Each row says which it is, and the task that builds it.

| Part | Asked, needed, or added | Task |
|---|---|---|
| One arrow a shot, as for a player | asked ("consume arrows normally") | T4 |
| 512 arrows in each starting storehouse, older saves included | asked ("a large amount of arrows in their storage") | T2 |
| Arrows in the backpack, restock at the storehouse, raiders' reserve | needed: an archer that only holds one slot cannot use arrows up | T3 |
| Shots pickable by anyone, NPCs pick up by touch | needed: "normally" means as for a player, whose arrows anyone can pick up | T4, T5 |
| Out of arrows: run home, sidearm, spent raider | needed: says what an archer does when "normally" leaves it empty | T7 |
| The blacksmith crafts arrows (vanilla recipe, unused today) | needed: without any resupply the ranged half of every garrison is gone after about a week | T9 |
| A beneficial or Harming arrow is never shot at a target it would help | added: fixes today's latent fault (a Healing arrow taken as an upgrade) | T1, T3, T4 |
| Sweep after a fight | added | T6 |
| Flint dug from gravel, chickens in the starting pen, the trader buying arrows | added (more resupply) | T9, T10, T11 |
| Council: dry archers counted by their sidearm, ranged hire needs a quiver | added | T8 |

## 1. What exists today (read from the code)

| Fact | Where |
|---|---|
| Roles: `ARCHER` (bow) and `CROSSBOWMAN` (crossbow) are `ranged` combatants. Both run `GuardJob`. | `npc/NpcRole.java:20,26`, `work/Jobs.java:20` |
| One arrow is held in `FactionNpc.ammo`, saved as `FcAmmo` and dropped at death. `setAmmo` keeps one copy, and the arrow it replaces disappears. | `npc/FactionNpc.java:84,471-473,1334-1336,1373-1374,1418` |
| `performRangedAttack` copies the arrow. It never shrinks it, and it sets `arrow.pickup = DISALLOWED`. Bow shot speed is 1.6; crossbow bolt speed is 2.2 with less spread. Marksman scales damage. Each shot wears the weapon by 1. | `FactionNpc.java:554-585` |
| Fire cycle: a bow draws 20 ticks; a crossbow charges for `CrossbowItem.getChargeDuration` (25 ticks, 5 fewer per Quick Charge level). Then 20 ticks of cooldown. Range is 16 for a bow and 20 for a crossbow, line of sight only, 5 blocks when blinded. The NPC never calls the crossbow's `releaseUsing`, so vanilla never loads it. | `combat/NpcRangedAttackGoal.java:16-18,116-179` |
| An archer with no arrow is not `armed()`. The melee goal then takes it, and it swings its bow (fist damage). | `NpcRangedAttackGoal.java:63-65`, `combat/NpcMeleeAttackGoal.java:50-53` |
| Out of reach, an archer holds a spot in sight of the target. It lets the target go beyond `Pursuit.WATCH` (24). | `combat/Pursuit.java:76,971` |
| The equipment check prefers any non-plain arrow, which includes a tipped arrow of Healing (every shot would heal the enemy). It runs at most once a minute, at the storehouse. | `work/Job.java:627-629,666-675` |
| A ranged recruit pays 1 arrow, or flint + stick + feather, in which case the other 3 of the batch of 4 go back to storage. | `campaign/Recruitment.java:180-206` |
| Starter supplies: 1 bow, **4 arrows**, 2 flint, 2 feathers, 16 sticks and more. They are granted once (`BaseState.starterGranted`), and what does not fit is logged and lost. The starting roster has no archer. | `campaign/BaseBuilder.java:218-239`, `NpcRole.java:61-63` |
| The garrison is 40% ranged (`recruitment.rangedShare` 0.4) up to 12 soldiers, so at most 5 archers and crossbowmen per base. A ranged recruit is hired only when there is a bow (or 3 string) or a crossbow (or 2 iron and 2 string). | `campaign/FactionDirector.java:214-226` |
| Guards pick up item drops (not arrow entities) within 2 blocks every 10 ticks (`pickUpLoot`). They visit the storehouse every 6000 ticks, where they keep 1 golden apple and 8 climbing blocks. They raid as `RAID_TRAVEL` → `RAID_ASSAULT` → `RAID_LOOT` → `RAID_RETURN`; `run` forces any other state to `RAID_TRAVEL` while the NPC is a raider. They leave a raid under 35% health. `RAID_RETURN` unloads at the storehouse and goes on to `PATROL`. | `work/GuardJob.java:59-65,67-80,85-95,99-104,132-135,164-182,199` |
| A job is paused while its NPC has a living target (`WorkGoal.free`): anything an NPC must do in a fight cannot live in its job. | `work/WorkGoal.java:18-31` |
| `Crafting.ARROWS` (flint + stick + feather gives 4 arrows) exists but nothing uses it. The blacksmith's plan makes bows for archers that lack one. It never makes arrows. It forges one batch per 40 ticks. | `work/Crafting.java:46`, `work/BlacksmithJob.java:149-153,285-291` |
| Miners bring gravel home (it is junk only once the storehouse holds 512). Gravel gives flint 10% of the time with no Fortune, whatever breaks it, so miners already bring a little flint; nobody turns stored gravel into flint. | `work/MinerJob.java:69-71`, vanilla `blocks/gravel` (0.1, 0.143, 0.25, 1.0 by Fortune level) |
| The pen supports chickens (seeds), but the starting pen has 2 cows and 2 pigs. The carer lures only species already kept (and cows and pigs). The cap is 8 animals a species, and the butcher keeps 4 breeding adults; it is hired once a species has more than 5 adults. A chicken drops 0-2 feathers (mean 1, Looting +0-1) and 1 raw chicken. | `BaseBuilder.java:202-214`, `work/PenJob.java:31-35`, `work/AnimalCarerJob.java:34,111-118`, `work/ButcherJob.java:23`, `FactionDirector.java:147`, vanilla `entities/chicken` |
| The trader sells flint and feathers above 32 each and sticks above 64. It buys only ranked gear (iron or better, bows, crossbows; at most 2 of a kind) and bread while rationing. It takes only one-price offers paid in emeralds. At the village, `wanted` reads the home storehouse (a knowledge leak this design does not copy). A village report counts trading villagers; it does not say which professions they have. | `work/TraderJob.java:54-79,306-318,381-400`, `campaign/Report.java:90-91` |
| Vanilla fletchers: a novice holds 2 of {32 sticks for 1 emerald; **1 emerald for 16 arrows**, 12 uses; 10 gravel + 1 emerald for 10 flint}, so 2 novice fletchers in 3 sell arrows. A master sells 5 tipped arrows for 2 emeralds + 5 arrows. | vanilla `VillagerTrades` (FLETCHER level 1), `Villager` (2 offers a level) |
| Skeletons drop 0-2 arrows and 0-2 bones; their own shots are `DISALLOWED`. Hollow lairs spawn 40% skeletons. | vanilla `entities/skeleton`, `BaseBuilder.java:297` |
| Heroes: a bow or crossbow hero (`HeroLoadout.needsArrow`), and a dormant hero with a ranged role, gets a kit-marked arrow in the `ammo` slot. Its shots are tagged as its basic hit. No shipped hero uses a bow (Natsuho, Hinae, the test rig). GameTests make a bow hero with `HeroRegistry.fixtureDefinition(id, name, "bow", "")`. `ROSTER_PLAN.md` plans 13 bow and 15 crossbow heroes. | `hero/HeroEntity.java:488-516`, `logic/hero/HeroLoadout.java:94-97`, `gametest/HeroDefinitionGameTests.java:457-460`, `ROSTER_PLAN.md:60-61` |
| GameTests that arm an archer or assert today's rule: 7 `setAmmo` calls (`FCGameTests` 319, 511, 537, 595; `CombatReachGameTests` 104; `HeroRaidGameTests` 304; `EliminationGameTests` 673, a hero), and three tests that assert the old rule: `oneArrowIsNeverConsumed` (the spec's acceptance 9), `crossbowmanChargesAndFires` ("the arrow was used up") and `saveReloadKeepsIdentityWithoutStacking` (the arrow back in the slot after a load, loading the tag twice). `HeroMoveGameTests` and `HeroMobilityGameTests` only build `Arrow` entities; `HeroDefinitionGameTests:460` and `HeroGameTests:226` read a hero's kit arrow. | `gametest/` |

Vanilla facts used below (checked against the 1.21.1 sources and data in this build):

- An arrow that hurts a living target is discarded. One that is refused (a raised shield, the hurt cooldown) bounces off and
  can land.
- An arrow in the ground despawns after 1200 ticks.
- `playerTouch` picks up an arrow that is in the ground and has stopped shaking, and only if its `pickup` allows it:
  `ALLOWED` for anyone, `CREATIVE_ONLY` for creative players.
- `setOwner(Player)` turns `DISALLOWED` into `ALLOWED`. A mob's arrow from `ProjectileUtil.getMobArrow` stays
  `DISALLOWED`.
- `ProjectileWeaponItem.useAmmo` asks `EnchantmentHelper.processAmmoUse`. Infinity's data (`minecraft:ammo_use` set to 0)
  applies only to `minecraft:arrow`. A free shot's stack is marked `INTANGIBLE_PROJECTILE`, and the arrow made from it is
  `CREATIVE_ONLY`.
- Multishot fires 3 for 1, and the extra 2 are intangible.
- A tipped arrow applies its potion to what it hits (`Arrow.doPostHurtEffects`). Instant Damage heals a target with
  `isInvertedHealAndHarm()` (zombies, skeletons, the Hollow's monsters), so a Harming arrow's hit on them nets nothing.
- A player picks things up in its box inflated by (1.0, 0.5, 1.0).
- `AbstractArrow.inGround` is protected in 1.21.1, and NeoForge's own access transformer does not open it, so the NPC
  pickup needs one of ours (section 2.4).

## 2. Consumption

### 2.1 The quiver is the backpack

An NPC's arrows are the arrow stacks in its 18-slot backpack (`FactionNpc.inventory`). There is no separate quiver
item and no new slot. A new helper, `npc/Quiver`, reads and draws from the backpack:

- `count(npc)`: all arrows it would shoot, plus a hero's kit arrow.
- `has(npc, target)`: an arrow it would shoot at this target (a kit arrow always counts).
- `draw(npc, weapon, target)`: takes one arrow for a shot, or returns a free copy.

The `ammo` slot and `setAmmo` stay only for a hero's bound kit arrow (`HeroKit.isKit`); no other code calls `setAmmo`.

Arrows are classed by `ArrowRules.kind` from the effect categories of their `PotionContents`:

| Kind | Arrows | Shot at an enemy? |
|---|---|---|
| `PLAIN` | `minecraft:arrow`; a tipped arrow with no effect | yes |
| `SPECIAL` | spectral arrows; tipped arrows whose effects are all harmful or neutral (Slowness, Poison, Weakness, Harming, Glowing) | yes, **first**, as a player loads its best arrows (the order today's equipment check already uses); but a Harming arrow is never drawn against a target with `isInvertedHealAndHarm()` (it would heal it), and a plain arrow is drawn before a Poison one there (the undead ignore poison: vanilla's `ignores_poison_and_regen` tag) |
| `NEVER` | a tipped arrow with any beneficial effect (Healing, Regeneration, Strength, ...) | never. Soldiers do not take them, and they stay in the storehouse (a field medic, ROADMAP phase 2, could use them on allies later). This fixes today's latent fault of a Healing arrow equipped as an upgrade. |

The pure rule is `ArrowRules.usable(kind, harming, poison, undeadTarget)`. An archer whose only arrows are Harming
arrows has nothing to shoot at a zombie: for that target it is out of arrows (3.3).

### 2.2 One arrow per shot

`performRangedAttack` calls `Quiver.draw(npc, weapon, target)` and uses what it gets:

1. **A crossbow that is already loaded** (`CHARGED_PROJECTILES` not empty, such as a player's crossbow stored
   charged) fires its loaded arrow first and takes nothing from the backpack, as vanilla does. The component is then
   cleared.
2. Otherwise it picks the first usable arrow by class (2.1): `SPECIAL` before `PLAIN`, never `NEVER`.
3. `cost = EnchantmentHelper.processAmmoUse(level, weapon, arrow, 1)`, the same call vanilla's `useAmmo` makes. It also
   honours NeoForge's `ArrowItem.isInfinite`:
   - With `cost == 0` (Infinity with a plain arrow), the shot is a copy marked `INTANGIBLE_PROJECTILE`. The backpack
     keeps the arrow.
   - Otherwise one arrow is split off the stack.
4. The arrow is made by `ProjectileUtil.getMobArrow(npc, shot, power, weapon)` as today. Piercing and Power come from
   the weapon as for any shooter.
5. With **no usable arrow** there is no shot. `NpcRangedAttackGoal.armed()` and `NpcMeleeAttackGoal.ranged()` read
   `Quiver.has(npc, target)` instead of `ammo()`, so the ranged goal never even draws.

| Weapon | Arrow use | Notes |
|---|---|---|
| Bow | one per shot, taken at the release | as a player: it needs an arrow to draw at all |
| Bow with Infinity | none for a plain arrow (it needs at least one in the backpack); one per shot for a special arrow | vanilla's data-driven rule, not ours |
| Crossbow | one per shot, taken when its charge completes (that is when it loads; the NPC fires on the same tick, as today) | a charge broken off before it completes takes nothing |
| Crossbow with Multishot | one per shot, one bolt as today | the NPC fires a single bolt today. Firing vanilla's three (two intangible) would be a war-balance change and is left out (section 6.13). |

### 2.3 The arrow it fires

`arrow.pickup` is `ALLOWED`, exactly what vanilla gives a survival player's shot (`setOwner(Player)`). The exceptions
follow vanilla's own exceptions:

| Shot | `pickup` | Why |
|---|---|---|
| An NPC's shot that used an arrow | `ALLOWED` | equal treatment: a player's shot is pickable by anyone |
| A free shot (Infinity, a hero's kit arrow) | `CREATIVE_ONLY` (`INTANGIBLE_PROJECTILE`) | no free arrows from a free shot, as with a player's Infinity bow |
| A skeleton's, stray's or pillager's | `DISALLOWED` (vanilla, unchanged) | vanilla mob |
| A player's | `ALLOWED` (vanilla, unchanged) | the NPCs may now pick these up too |

The pickup item is the arrow drawn (a tipped arrow keeps its potion), so an arrow comes back as what it was.

### 2.4 Picking arrows up

**Touch (everyone, always).** Every faction NPC (workers, soldiers, heroes) does once every 10 ticks, in
`FactionNpc.aiStep` (not in a job, which is paused while it fights), what `Player.aiStep` does each tick:

- It takes each `AbstractArrow` in its bounding box inflated by (1.0, 0.5, 1.0) that is **in the ground** (or has no
  physics), has `shakeTime <= 0`, and is `ALLOWED`.
- It puts `getPickupItemStackOrigin().copy()` into its backpack if there is room, calls `npc.take(arrow, 1)` for the
  animation and discards the arrow.

Reading `inGround` needs the project's first access transformer
(`public net.minecraft.world.entity.projectile.AbstractArrow inGround` in `src/main/resources/META-INF/accesstransformer.cfg`,
which ModDevGradle should pick up by its default path (confirm in T5), and an `[[accessTransformers]]` entry in
`src/main/templates/META-INF/neoforge.mods.toml` for the built jar). A cached reflected field would work too (1.21.1
NeoForge runs with Mojang names, as the GameTests' reflection already relies on). The AT is cleaner.

A worker's arrows are cargo and go to the storehouse at its next unload. A ranged soldier keeps them up to its quiver
(section 3.1). An NPC picks up enemy shots too, as a player does. A hero with a job unloads them with its cargo; a hero
without one carries them until it dies (they drop with its pack).

**The sweep (soldiers, after a fight).** Arrows despawn 1200 ticks after they land. So a soldier sweeps when all of
these hold:

- it shot or struck within the last 1200 ticks;
- it has no target (its job runs only then anyway);
- no enemy it can see is within 24 blocks (`Pursuit.WATCH`);
- it is not raiding;
- at night, the spot is inside its walls.

It then walks (by path, `NpcNavigator`) to the `ALLOWED` arrows in the ground within 12 blocks of where it stands that
it **can see** (a line of sight from its eyes to the arrow, as it sees an enemy): an arrow behind a wall is not known to
it. Touch does the picking up. It sweeps for at most 600 ticks and stops at once if an enemy comes into sight. The
sweep is part of `GuardJob`'s patrol, not a saved state; a reload ends it.

Haulers do not sweep (they keep the storehouse). Raiders only touch, because a raid has its plan, and its march home
and the target's yard are never safe in this sense.

## 3. Carrying and restock

### 3.1 How many, and the keep rule

| Situation | Arrows carried | Why |
|---|---|---|
| At home (guard duty) | **48** (`war.quiverArrows`) | At 29 shots a minute, a bow empties 48 in about 100 s of continuous fire. That is more than one archer fires in a typical defended raid (section 4.2), and three quarters of a stack, so one slot. |
| On a raid | **64** (`war.raidQuiverArrows`) | A full stack: a raider cannot restock away from home. |
| Bow with Infinity | 1 plain arrow, plus any special arrows it holds | It needs one plain arrow, and the stock is not tied up in its backpack. |

`GuardJob.keep` keeps arrows up to that target across **all arrow kinds together**. Today's `keptPerSlot` map is keyed
by item, so arrows (`ARROW`, `TIPPED_ARROW`, `SPECTRAL_ARROW`) need one shared key. Arrows above the target, and any
`NEVER` arrow, are cargo and are deposited.

### 3.2 When it restocks

All restocks take from its own base's storehouse with `FactionStorage.extract`, which takes at most the count asked
for (never a whole stack by mistake).
The pure rule is `ArrowRules.take(carried, target, stored, reserve)`.

| When | What | Rule |
|---|---|---|
| Every `STORAGE` visit (on arrival, and every 6000 ticks on patrol), and the storehouse stop of `RAID_RETURN` | tops up to the home quiver, `SPECIAL` first, then `PLAIN`; deposits what is above it | home archers may take the stock to 0 |
| Below **16** arrows on patrol, not fighting | goes to the storehouse now (status "Low on arrows - going to the storehouse"), not at the next 6000-tick visit, **if the storehouse had arrows when it last saw it**; after a visit that found none it waits for the next regular visit (no loop on an empty storehouse) | `ArrowRules.restockNow(carried, 16, atHome, hasTarget, lastSeenStock)`; the stock it saw is kept in the job as `Job.storehouseFood` keeps food |
| Drafted into a raid | new first step `RAID_STOCK`: walks to the storehouse stand and tops up to 64, never taking the stock below the **reserve of 64** (`war.arrowReserve`); then `RAID_TRAVEL` | `GuardJob.run` must let `RAID_STOCK` stand (today it forces every non-raid state to `RAID_TRAVEL`), and a raid called off during `RAID_STOCK` goes to `STORAGE`, not `RAID_RETURN`. The stop takes a few hundred ticks and fits inside the muster's 6000-tick allowance (`Whereabouts.RAID_MUSTER_TICKS`, counted from the launch), so the council's raid plan is unchanged. A raider that cannot reach the stand in 600 ticks marches with what it has. |
| A new recruit | pays 1 arrow (or its materials) as today, then starts in `STORAGE` and fills its quiver there | the director hires a ranged soldier only when the storehouse can fill a quiver after the bill (section 6.3) |
| A member player hands it arrows (sneak + right-click) | the whole held stack goes into its backpack, up to room | as handing a friend a stack. Today one arrow replaces the old one. |

### 3.3 Out of arrows

As a player archer would, it fetches more when they are close, and otherwise draws its sword:

1. **At home, the storehouse close.** A new `ResupplyGoal` at priority 2 (the `WithdrawGoal`'s) fires when all of these
   hold:
   - the NPC is a ranged fighter with no arrow it can shoot at its target;
   - it stands within **32 blocks** of its storehouse stand, in its home area;
   - the storehouse had arrows when it last saw it (3.2).

   It breaks off, walks to the stand, takes its quiver and goes back to its target. A visit that finds no arrows sets
   the stock it saw to 0, so it does not go again. A new rule of its own (the `WithdrawGoal` has no such check): it is
   not taken while an enemy stands within 4 blocks, since it would be cut down turning its back.
2. **A sidearm.** Archers and crossbowmen carry one spare melee weapon: the best sword or axe the storehouse can spare.
   One definition of a spare serves the archer and the blacksmith (`ArrowRules.spareSidearms`): the swords and axes
   stored, less the swordsmen and axe fighters at home with no sword or axe of their own. An archer takes one when
   that is at least 1. With arrows gone, `Quiver.armForFight` (run from `FactionNpc`'s tick while it has a target,
   since the job is paused then) swaps the sidearm into its hand, as a player switches hotbar slots; the bow waits in
   the backpack and it fights with the melee goal. It takes the bow back when it has a usable arrow and no enemy is
   within its kite distance, or when the fight ends. `GuardJob.usesOtherTool` says yes while it is dry and holds its
   sidearm, so `Job.survival` does not put the bow back meanwhile. `GuardJob.keep` keeps one sword or axe for a ranged
   soldier (today it keeps none, so the sidearm would go into the storehouse at the next unload). A raider uses it the
   same way.
3. **Bare hands.** With neither, it fights with the bow in hand (fist damage), as today.
4. **A spent raider.** A raider with no arrow and no sidearm leaves the raid when its fight ends, that is the next time
   its job runs (`f.leaveRaid`, `RAID_RETURN`, "out of arrows"), as a raider under 35% health does. It counts in the
   party's roll as any raider walking home.

An archer that runs dry adds 1 to its faction's `ranDry` count (section 6.11).

### 3.4 What the faction knows

- Its own storehouse: allowed knowledge. The blacksmith, the council and the director count it at home.
- Its members at home: `FactionDirector.seenAtHome`, which the blacksmith already uses.
- Each NPC knows its own backpack, and the storehouse's arrows as it last saw them.
- A worker away (the trader) uses what it saw at its last visit home, as `storehouseFood` does.
- Nothing reads another faction's arrows. The council's estimate of an enemy base is unchanged: archers seen count as
  soldiers.

**One measure of stock.** "The base's arrows" is the arrows in its storehouse **plus** those carried by its members at
home (`seenAtHome`). Filling quivers moves arrows; it does not use them. So `arrowTarget`, the blacksmith's order, the
trader's budget, the shortage line, the `ARROWS` ledger and the live check all read this one measure. A raid party's
quivers are out of the count until it is home, so a launch can start a crafting batch; the overshoot is at most the
party's quivers, once.

Two rules are about what is physically in the chests, and read the storehouse alone:

- the raiders' reserve;
- the director's ranged hire, which needs a quiver it can fill.

## 4. The starting stock

### 4.1 What the code bounds

| Quantity | Value | From |
|---|---|---|
| Bow cycle | 41 ticks (20-tick draw, 20-tick cooldown, 1 tick to start again), so **29 shots a minute** | `NpcRangedAttackGoal.tick` |
| Crossbow cycle | 46 ticks (25-tick charge), so **26 a minute**; Quick Charge III: 31 ticks, 39 a minute | same, `CrossbowItem.getChargeDuration` |
| Damage per hit before armour | bow 4 (3 about a quarter of the time: speed 1.6, about 1.48 at impact, × base 2.0 + 0.22 on Normal, rounded up); crossbow 5 (4 or 6 a sixth of the time each); Marksman +10% a stack | `getMobArrow` → `setBaseDamageFromMob`, vanilla `ceil(speed × base)` |
| Ranged soldiers at a full garrison | 5 (`round(0.4 × 12)`) | `FactionDirector` |
| One full refill of every quiver | 5 × 48 = 240 arrows | section 3.1 |

### 4.2 What a fight costs (an estimate; the live check measures it)

The war logs (`knowledge.md` 12, `missing_npcs.md` 14) count launches and losses, not shots, and night fire at
monsters is not logged at all. So consumption is estimated from the damage a fight needs. The assumptions are marked,
and section 9.4 measures them.

Assumptions:

- 2 arrows in 3 hit (an assumption).
- Archers do about half the killing in a mixed fight (an assumption).
- Armour per vanilla's formula: 4 points (part leather) take about 8% off a 4-damage arrow, a full leather set (7)
  20%, full iron (15) about 52%.
- A soldier that withdraws to eat and comes back costs about half again.

| Event | Targets | Arrows (whole fight, all archers) |
|---|---|---|
| Zombie or skeleton at the walls | 20 health, 5-6 hits | 8-9 per monster |
| Raider in leather | 20 health, 6-7 hits | 9-11, up to 16 with a withdrawal |
| Raider in iron | 20 health, 11 hits | about 16, up to 25 with a withdrawal |
| A defended settlement raid (live parties: 3-4 raiders) | half the killing by arrows | **30-80** |
| The same with an enemy hero (about 120 health at level 1, `ARTIFACTS.md` table 6, more as it levels; capped at 12% a hit) | arrows hit a hero in the air first | **+60-150** |
| A raid of its own (1-2 archers in the party) | a quiver's worth each | **48-128** |
| A lair raid (8 monsters, 40% skeletons) | half the killing | **about 35** (and about 3 arrows back from skeleton drops) |
| A night at home (monsters that come in sight inside 16 blocks) | unknown; assumed a few | **10-30** |

The two knowledge campaigns launched 6 raids in 5.18 days and 5 in 7.25 days across the four factions, that is 1.2 to
2.0 launches per faction per week, and as many defences received. With a lair raid every other week and 20 arrows a
night, a faction uses about **330-440 arrows a week** (own raids at 88, defences at 55, a lair at 35; the first draft's
3 × 60 + 35 + 7 × 20 ≈ 360 is inside that), plus about 100 for each fight with an enemy hero. That counts no pickups: arrows that hit are gone, and
those on the ground are gone 60 s after landing unless someone collects them.

### 4.3 The number: 512

**`campaign.starterArrows` = 512**: 8 stacks, in the starting settlement's WEAPONS chests (a double chest, 54 slots).
It replaces the 4 arrows of today's starter list. The flint, feathers and bow stay.

- 512 is about **2 full refills of a full ranged garrison's quivers** (2 × 240) plus 32.
- By the estimate it is **about 1.2 to 1.5 weeks of the war seen in the live campaigns**, or 4-6 hard defended fights,
  with no resupply at all.
- The starting roster has no archer, and ranged recruits arrive over the first days. Early stock is barely touched, so
  the resupply chain (section 5) has several days to start before it is needed.
- It goes to the campaign's starting settlements only. A base founded later (ROADMAP phase 3) gets none, as the
  owner's words say.

Config range: 0-4096. With 0, a base starts with no arrows and its first ranged recruits wait for crafting or trade.

**Older saves.** Their settlements already have `starterGranted`, and their archers start using arrows up with this
build. A new saved flag, `BaseState.arrowsGranted` (absent means false), marks the grant, and `BaseState.arrowsOwed`
(absent means 0) holds what is still to be delivered:

- The grant runs in the director's pass for a base, never while the campaign data loads: `FactionStorage` sees only
  chests in loaded chunks, and at load the base's chunks may not be.
- It runs once for every settlement (`isSettlement()`, not `destroyed`; lairs and ruins get nothing) with
  `starterGranted` and no `arrowsGranted`: it sets `arrowsOwed = starterArrows` and `arrowsGranted`.
- Each director pass inserts what fits of `arrowsOwed` and lowers it, so a full storehouse delays the arrows instead of
  losing them (`grantStarterSupplies` logs and drops what does not fit).
- A new campaign's `grantStarterSupplies` sets `arrowsGranted` and owes whatever of the 512 did not fit, so no later
  pass adds another 512.

Separately (T3), each NPC's old `FcAmmo` arrow moves into its backpack when the NPC loads. A campaign begun before the
change therefore gets the same stock as a new one.

## 5. Resupply after the stock

Sources in order of how much they bring:

| Source | New or existing | Yield (estimate) |
|---|---|---|
| **Fletcher villagers through the trader** | trade code existing; buying arrows **new** | up to 192 per fletcher restock (1 emerald for 16, 12 uses). The trader buys at most 128 per trip (8 emeralds), one trip at most each half day (`TRIP_INTERVAL` 12000), from whatever fletcher it meets in the village it goes to: the faction does not know beforehand which village has one (reports count trading villagers only). The base must have a trader (14 people and a trading village within 320 blocks, as today). |
| **Crafting by the blacksmith** | the recipe exists but is unused; the order is **new** | limited by feathers: about 32 arrows a day from the pen's chickens from day 3 (5.3), plus any feathers the trader no longer sells |
| **Pickups and the sweep** | **new** | misses, bounced arrows and fallen archers' quivers. 0-25% of the arrows fired, measured live; not counted in section 4. |
| **Skeleton drops** | existing (guards pick up drops within 2 blocks) | 0-2 per skeleton killed (mean 1). Lair raids and monsters at the walls. |
| **Raid loot** | existing (`RAID_LOOT` takes from the enemy storehouse) | a won raid can carry off the enemy's arrows |

### 5.1 The blacksmith fletches

A new order in `BlacksmithJob.planOrders` comes after "archer bows" (5): **"arrows for the archers"**.

- It runs when the base's arrows (storehouse plus carried at home, 3.4) are fewer than the target (7: `war.arrowTarget`,
  **256**, and never less than a full ranged garrison's quivers).
- Batches: `ArrowRules.arrowCrafts(baseArrows, target, flint, feathers, sticksAvail)` = min(ceil((target − baseArrows) /
  4), flint, feathers, sticks, **16**), so at most 64 arrows a plan.
- It uses the existing `Crafting.ARROWS` at the workshop table. Sticks come from planks and logs through
  `craftWithIntermediates`, as for every recipe.
- At 40 ticks a batch, 16 batches take 640 ticks.

While the base's `ARROWS` shortage is on (6.10), the order moves ahead of swords and armour (step 3): ammunition
first when it is nearly gone.

**Flint from gravel** is new, and is what a player does: place gravel, dig it, keep the flint.

- When it runs: arrows are below target, feathers are more than flint, the storehouse has gravel, and the blacksmith has
  no other order.
- Setup: it takes up to min(16, feathers − flint, gravel) gravel. It also takes a spare shovel if one is stored (Fortune
  preferred: III gives flint every time). Otherwise it works by hand.
- Where: a free floor cell beside the workshop stand, chosen once and kept in the job.
- The loop: it places a gravel block and digs it at player speed (`BlockWork.Breaker`), picking up the drop, and
  repeats.
- Yield (vanilla loot): each dig gives flint 10% of the time (14%, 25%, 100% with Fortune I-III), else the gravel back.
  So each gravel block becomes exactly one flint, after 10 digs on average.
- Time: gravel (hardness 0.6) takes 5 ticks with a stone shovel and 18 by hand (vanilla's speed / hardness / 30 a tick,
  which `BlockWork.progressPerTick` follows), so about 10 and 23 ticks a cycle with
  the placing and the pickup: about 100 ticks a flint with a stone shovel (16 flint in about 1.5 minutes), about 230 by
  hand (about 3 minutes).
- The session is not saved: a gravel block left at the spot is dug first next time. The shovel goes back with its
  cargo.

**Sidearms.** A new low-priority order, "sidearms for the archers", makes max(0, ranged members at home with no sword or
axe − `spareSidearms`) stone swords (2 cobblestone + 1 stick each), the same count of spares the archers take by (3.3),
so every archer it makes one for can take it. The miners always bring cobblestone.

### 5.2 Sticks

Existing. Woodcutters bring logs, and the blacksmith keeps 16 sticks ("stick stock"). Arrows need one stick per 4
arrows, about 8 a day.

### 5.3 Feathers: the pen's chickens

- **New:** the starting pen gets **2 chickens** (a breeding pair) beside its 2 cows and 2 pigs
  (`BaseBuilder.spawnAnimals`).
- **New, for older saves:** a standing settlement whose pen holds no chicken gets the same pair once, in the director's
  pass (`BaseState.chickensGranted`), as the arrows are granted once (4.3). Without it an older save has no feather
  source: its carer lures only species it already keeps, and cows and pigs (`AnimalCarerJob.java:115-116`).
- **New:** chickens join cows and pigs as species the carer always lures back, so an escaped hen is not lost.
- **New:** chickens' own cap is **16** (`AnimalCarerJob.CHICKEN_CAP`; other species keep 8). The 9 × 5 pen then holds at
  most 8 cows, 8 pigs and 16 chickens (a chicken is 0.4 blocks wide).
- Existing: the carer breeds them with seeds (the farmer's) while the species is under its cap
  (`AnimalCarerJob.java:252`), and the butcher culls adults over the 4 kept for breeding. The vanilla loot table drops
  0-2 feathers (mean 1) and 1 raw chicken (food).
- Yield: the 4 breeding adults make 2 chicks every 6000 ticks, and a chick grows in 24000, so with a prompt butcher the
  flock settles at about 12 (4 adults and about 8 chicks) and about **8 chickens a day** are culled from day 3: about
  8 feathers, so **about 32 arrows a day**, plus 8 raw chicken. With today's cap of 8 the same flock gives only 4 a
  day. The cap of 16 is slack for a slow butcher: grown hens waiting to be culled count against the cap and stop the
  breeding, and the butcher does not work at night. With half a day of delay a cap of 12 gives about 6.7 a day and 16
  still 8 or more.
- The butcher is hired as today ("surplus livestock" once a species has more than 5 adults).

### 5.4 The trader buys arrows

- **Budget, fixed at home.** In `PLAN`, at home, the trader sets
  `arrowBudget = ArrowRules.tradeBudget(baseArrowsSeen, target, cap 128)` from the base's arrows as it sees them there
  (3.4), saved with its job. At the village it spends
  from that budget, not from a look at the storehouse from afar (`knowledge.md`'s rule, as `storehouseFood` does).
- **Buying.** `wanted()` accepts an `ItemsForEmeralds` offer of plain arrows while the budget lasts. That is the vanilla
  fletcher novice offer: 1 emerald for 16 arrows, still in stock, Haggler discount as today. Each purchase lowers the
  budget by what it got. Tipped arrows (the master's two-price offer) are not bought, since the trader takes only
  one-price offers.
- **Selling.** While the base's arrows it saw are below target, it does not sell flint or feathers (the `SELL` keep
  counts are ignored for those two). Above target, it sells them as today. It still sells sticks above 64, and a
  fletcher buys them (32 for 1 emerald), which pays for its arrows.

### 5.5 Weighed and not taken

| Option | Why not (now) |
|---|---|
| A new **fletcher** role | The chain's limit is feathers, not labour. A day's arrows take the blacksmith under 2 minutes of crafting and 1.5 of digging. A fletcher would be one more bed and one more hire rule for no more arrows. Decided: the blacksmith now; if the live check (9.4) shows the blacksmith falling behind on tools while fletching, a fletcher (crafting, flint, wild-chicken hunting) is a clean later addition, said to the owner then. |
| Hunting wild chickens | Wild chickens do not respawn in any number. The few near a base are better lured into the pen by the carer (existing). |
| The fletcher's gravel-and-emerald trade for flint | It is a two-price offer the trader cannot take. Digging is cheaper and needs no emeralds. |
| Arrows from loot chests, and strays' tipped arrows | Strays drop tipped arrows only when a player kills them (vanilla `killed_by_player`). Chest loot is not a planned source. |

## 6. Interactions

### 6.1 The blacksmith

Its arrow order counts the base's arrows: its storehouse plus its members at home (3.4). Its sidearm order counts
ranged members at home (`seenAtHome`), as its other orders count members at home. It fletches only below the target, so
512 starting arrows mean no arrows are crafted until half the starting stock has been shot.

Until then, the trader keeps selling flint and feathers above 32 each, as today. So the blacksmith starts fletching with
up to 32 of each in store (128 arrows' worth) plus whatever the pen and the miners brought since the trader's last trip.
Once below target, the trader keeps them all.

### 6.2 The war council

`FactionDirector.power(n)` **stays as it is**: what anyone sees (armour worn, weapon held). Two other readers use it:

- `BaseWatch.look` uses it on **enemy** soldiers (`BaseWatch.java:84`), so it must not read their arrows (that would
  leak their stock);
- `f.soldierPower`, the council's prior for a garrison nobody has seen, comes from it. A prior lowered by the council's
  own dry archers would make every unseen enemy look weaker too.

A new **`FactionDirector.partyPower(n, share)`**, used only for the council's own drafted soldiers (the party's sum and
the pick order), reads the soldier's effective weapon:

- The stock above the reserve is handed out in pick order (`ArrowRules.partyShares`): each archer in turn gets
  min(64 − carried, what is left), so two archers never count the same arrows.
- An archer with at least 16 arrows carried plus its share counts its bow (tier 1).
- Otherwise it counts its sidearm's tier, and with neither tier 0 (fists).

A bow is tier 1 like a stone sword, so this moves a party's power by at most 0.25 of a soldier per dry archer (down
for one with no sidearm, up for one with an iron sword, which then fights as a swordsman does). Its main use is the
pick: archers that get less than 16 arrows sort last. No new rule weighs arrows against the enemy: the council never
knows the enemy's stock. All of this reads its own soldiers at home and its own storehouse.

### 6.3 Recruitment

- The director adds a "ranged support" need only when the storehouse holds a quiver after the recruit's bill
  (**49**: 48 + the 1 arrow it pays, `ArrowRules.rangedHireOk`). Otherwise it hires a melee soldier for "garrison
  growth".
- The recruit's bill is unchanged: 1 arrow, or its materials.
- This cannot stall the ranged hire for good while the crafting target is above the full ranged garrison's quivers
  (7: at least 5 × 48 + 1 = 241): with a ranged place free, the quivers at home hold at most 4 × 48, so a store under
  49 keeps the base's arrows at 240 or less, under the target, and the blacksmith fletches, as long as it has flint
  and feathers (5.1, 5.3); without them the hire waits for the trader or for pickups.

### 6.4 Raids

- Raiders carry 64 arrows (with the reserve kept at home).
- Their arrows drop where they die (`dropCustomDeathLoot` drops the whole backpack), and the defenders' guards pick
  the drops up.
- Their shots are pickable by the defenders too.
- A raider out of arrows draws its sidearm, or leaves the raid (3.3).
- The raid plan, the muster and the roll call are unchanged.

### 6.5 The hard defence

This change tilts the war further toward the defender:

- Defenders restock mid-fight within 32 blocks of their storehouse and sweep their walls afterwards.
- Raiders carry what they took and cannot restock.

The owner keeps the hard defence for now, so no counterweight is proposed. The live check compares settlement raid
outcomes with the baseline of the two knowledge campaigns: 2 of 15 settlement raids took their target.

### 6.6 Elimination

When a faction falls, every member dies by the generic kill:

- Its backpack, quiver included, drops where it stands, at once or when its chunk loads.
- A hero's kit arrow vanishes with its kit.
- The ruins' storehouse keeps its arrows for whoever loots it.

Nothing new is needed.

### 6.7 Heroes

No change to any hero's arrow: none of the shipped heroes shoots a bow.

- A bow or crossbow hero (a test fixture today; 28 planned in `ROSTER_PLAN.md`) keeps its **bound kit arrow** in the
  `ammo` slot, never used up, because a hero's kit is its abilities (HEROES_V2: the kit is bound and never dropped).
  Owner question 2 asks whether that should change when the bow heroes come.
- A **dormant** hero with a ranged role keeps the kit arrow it gets today (`HeroEntity.java:499-500`), so a broken
  hero file never leaves a hero unable to shoot.
- Its shots become `CREATIVE_ONLY`, as an Infinity shot is, so a hero's kit gives nobody free arrows.
- Heroes pick up arrows by touch like anyone (2.4).

### 6.8 Vanilla mobs

Skeletons, strays, pillagers and piglins keep vanilla's unlimited arrows, which cannot be picked up. Equal treatment is
between faction members (NPCs, heroes, players). Vanilla monsters are the world and keep vanilla's rules.

### 6.9 Players

Vanilla already applies: a player's bow uses its arrows, and its shots are pickable by anyone, NPCs now included. A
member player may hand arrows to an archer (3.2). A player who baits archers drains their arrows, as it would drain
another player's. No rule here is for NPCs only or players only.

### 6.10 Logistics, station and dump

- A new `Logistics.Resource.ARROWS` ("Arrows", "arrows in the storehouse and quivers at home"): the base's arrows
  (3.4), sampled each minute with the others and saved by name (no format change). `Logistics.measure` gains the
  campaign data it needs to find the members at home.
- Its shortage line: "arrows are running low (N, about D days at this rate)", with "- the blacksmith fletches, the
  trader buys" added by the tasks that make those true (T9, T11). It fires below 128 (about half the quivers of a full
  ranged garrison) and recovers from 192, with hysteresis like the wood line's.
- The station's logistics report shows it.
- The dump gives each NPC `arrows` (backpack count) and `kitArrow`, in place of `ammo`, and each faction its
  `arrowStats`.
- `sim.py` prints an `arrows` line per base: store, carried by members, fired, picked up, crafted, bought, ran dry.

### 6.11 Save format

| What | Key | Old save |
|---|---|---|
| A hero's kit arrow | `FcAmmo` (written only for a kit arrow) | a non-kit `FcAmmo` moves into the backpack at load, after the backpack is read from the same tag, so loading a tag twice still gives one arrow; if the backpack is full it is dropped at the NPC's feet on its first tick, not in the load (HANDOFF 5: no drop while an entity joins with its chunk) |
| The one-time grant | `BaseState` `arrowsGranted` (boolean), `arrowsOwed` (int) | absent means false and 0: granted once (4.3) |
| The pen's pair for an older save | `BaseState` `chickensGranted` (boolean) | absent means false: a pen with no chicken gets 2 once (5.3) |
| Operator counters | `FactionState` `arrowStats` (int[5]: fired, picked up, crafted, bought, ran dry) | absent means zeros. No AI reads it. |
| Raid stop | `GuardJob` state `RAID_STOCK` (enum name, appended) | never present |
| Arrows seen at the storehouse | `GuardJob` `arrowsSeen` (int, -1 unknown) | absent means unknown: the next visit counts |
| Trader budget | `TraderJob` `arrowBudget` | absent means 0 (next plan sets it) |
| Arrow ledger | `BaseState.ledgers["ARROWS"]` | absent means a new ledger |

`CampaignData.FORMAT` is unchanged.

### 6.12 Performance

- The touch scan is one entity query over a box of about 3 × 2 × 3 per NPC every 10 ticks. That is the cost of today's
  guard loot scan, now for about 100 NPCs: well under 0.05 ms a tick.
- The sweep runs one 12-block query, a line of sight per arrow found and a few paths, for a soldier just after a fight.
- The ledger adds one storage count per base per minute.
- Arrow entities in the world: no more than today. NPC shots already lay 60 s in the ground; pickups remove some sooner.

### 6.13 Left out

- Multishot's three bolts (6.2 would weigh it; a balance change).
- Fireworks in crossbows.
- Shooting special arrows only at hard targets.
- A fletcher role (5.5).
- Endermen dodging arrows: the arrows still land and can be swept.

## 7. Config keys and PROPOSED constants (for BALANCE.md)

| Name | Value | Kind | Why |
|---|---|---|---|
| `campaign.starterArrows` | 512 (0-4096) | config | "a large amount" (4.3): 2 full refills of a full ranged garrison, about 1.2-1.5 weeks of the live war |
| `war.quiverArrows` | 48 (8-64) | config | 100 s of continuous fire; one slot |
| `war.raidQuiverArrows` | 64 (16-128) | config | a full stack; no restock away |
| `war.arrowReserve` | 64 (0-512) | config | raiders leave this for the home archers |
| `war.arrowTarget` | 256 (0-2048) | config | the blacksmith fletches and the trader buys while the base's arrows (store + quivers at home) are below it; half the starting stock. The rule uses max(`arrowTarget`, ranged cap × `quiverArrows` + 1), the ranged cap being `round(rangedShare × 12)` (5), so a config cannot stall the ranged hire (6.3) |
| `ArrowRules.RESTOCK_AT` | 16 | constant | under a third of a quiver: fetch more now, not at the next visit (only if the storehouse had arrows when last seen) |
| `ArrowRules.RESUPPLY_RANGE` | 32 blocks | constant | a mid-fight run to the storehouse only when it is close |
| `ArrowRules.RESUPPLY_CLEAR` | 4 blocks | constant | no run while an enemy is this close |
| `ArrowRules.SWEEP_RADIUS` | 12 blocks | constant | where most misses land around a fighter at range 16 |
| `ArrowRules.SWEEP_TICKS` | 600 | constant | a sweep is short; arrows despawn 1200 ticks after landing |
| `ArrowRules.SWEEP_WINDOW` | 1200 | constant | after the last shot or blow; vanilla's arrow lifetime in the ground |
| `ArrowRules.ARROW_ORDER_MAX` | 16 crafts | constant | at most 64 arrows a blacksmith plan, so tools never wait long |
| `ArrowRules.FLINT_SESSION` | 16 gravel | constant | about 1.5 minutes with a stone shovel |
| `ArrowRules.TRADE_BUY_MAX` | 128 a trip | constant | 8 emeralds at the novice price |
| `ArrowRules.RANGED_HIRE` | `quiverArrows` + 1 | rule | hire a ranged soldier only when the storehouse can fill its quiver after its bill |
| `AnimalCarerJob.CHICKEN_CAP` | 16 | constant | chickens are the feather source (5.3); 12 gives the full 8 culls a day only with a prompt butcher, 16 keeps it through half a day of delay (the night) |
| starting chickens | 2 | constant | a breeding pair in the starting pen, and once in an older save's pen with none |
| arrow shortage | base's arrows below 128, recovered from 192 | constant | about half a full ranged garrison's quivers; hysteresis as for wood. While it is on, the blacksmith fletches ahead of swords. |

## 8. Proposed README and BALANCE text

**README** (replaces "Archers hold one arrow that is never used up (tipped/spectral effects apply to every shot, and
shots cannot be picked up)." in the gear paragraph):

> Archers and crossbowmen **use arrows up like a player**: each shot takes one arrow from the backpack (a crossbow loads
> one as its charge completes), Infinity spares plain arrows as in vanilla, and an NPC with none cannot shoot. They carry
> 48 (64 on a raid), restock at the storehouse, run back for more during a fight at home, and otherwise draw a spare
> sword or axe. They never shoot an arrow that would help its target (a beneficial tipped arrow, or Harming at the
> undead). Their shots can be picked up by anyone, as a player's can, and every faction member picks up arrows it walks
> over; soldiers collect the arrows they can see round them after a fight. Each starting storehouse holds **512
> arrows** (`campaign.starterArrows`). After that, the blacksmith crafts arrows (flint dug out of the miners' gravel,
> feathers from the pen's chickens, sticks) and the trader buys them from fletcher villagers. Bow heroes keep their
> bound kit arrow.

**BALANCE**, section "Soldiers", replaces "Both use the one equipped arrow that is never used up." with "Both draw
arrows from the backpack, one per shot (section Arrows)". Recruitment's line keeps "Archers also take 1 arrow ...". The
hero kit line becomes "Bow and crossbow heroes, and a dormant hero with a ranged role, carry the one never-used-up kit
arrow; their shots cannot be picked up". Plus a new section:

> ## Arrows (config `campaign.starterArrows`, `war.*`; `logic/combat/ArrowRules`, `npc/Quiver`) — 2026-11-25
> * Starting stock 512 per starting settlement (8 stacks, WEAPONS chests); older saves get it once, what does not fit
>   owed and delivered as room is made.
> * One arrow a shot (`EnchantmentHelper.processAmmoUse`: Infinity spares plain arrows); special arrows (spectral, tipped
>   with only harmful or neutral effects) first, but no Harming arrow at the undead; tipped arrows with a beneficial
>   effect are never shot or taken.
> * Quiver 48 at home, 64 on a raid; restock at every storehouse visit and below 16 (if the storehouse had arrows when
>   last seen); raiders leave 64 in the stock.
> * Out of arrows: back to the storehouse within 32 blocks (at home, no enemy within 4), else the sidearm, else bare
>   hands; a raider with neither leaves the raid.
> * Pickup: shots `ALLOWED` (free shots `CREATIVE_ONLY`); NPCs pick up as a player does (box +1.0/+0.5/+1.0, in the
>   ground, not shaking); soldiers sweep the arrows they see within 12 blocks for up to 600 ticks within 1200 ticks of
>   their last shot, no enemy in sight within 24.
> * The base's arrows = storehouse + quivers of members at home; target 256 (always above 5 quivers); shortage below 128
>   (recovered from 192).
> * Resupply: blacksmith fletches below the target (at most 16 crafts a plan; ahead of swords during a shortage) and digs
>   flint from gravel (vanilla 10%/14%/25%/100%); pen chickens cap 16, a starting pair (older saves' pens get one once), always
>   lured back; trader buys up to 128 a trip below
>   the target and keeps flint and feathers meanwhile.
> * Council: in its own party a dry archer counts its sidearm's tier (`partyPower`, the stock above the reserve shared
>   out in pick order); what scouts see (`power`) is unchanged; a ranged recruit needs 49 arrows in the storehouse.

`docs/factionclash-common.toml` is regenerated with the new keys. The download jar and README line 13 are **not** touched
in this phase (the owner deferred the jar until heroes are playable).

## 9. Tests

### 9.1 Unit tests (`src/test/java/com/factionclash/logic/combat/ArrowRulesTest`)

- `take`: tops up to the target; never below the reserve on a raid; home takes to 0; nothing when full; a stock smaller
  than the need gives what is above the reserve.
- `quiverTarget`: 48 at home, 64 raiding, 1 plain with Infinity.
- `restockNow`: below 16 at home with no target only; not while fighting; not away; not when the stock last seen was 0.
- `kind`: plain, spectral, Slowness and Poison are `SPECIAL`, Healing and a mixed Strength + Poison are `NEVER`, an empty
  tipped arrow is `PLAIN`; draw order is special before plain.
- `usable`: Harming is not usable against an undead target and is against a living one; against the undead a plain
  arrow comes before Poison; only Harming left against a zombie means none usable.
- `arrowCrafts`: limited by each input and by 16; zero at or above target.
- `effectiveTarget`: max(`arrowTarget`, ranged cap × quiver + 1); with `quiverArrows` 64 and `arrowTarget` 256 it is 321.
- `tradeBudget`: (target − seen) capped at 128; zero above target.
- `rangedHireOk`: at 49, not at 48.
- `sweepSafe`: false with an enemy in sight within 24, while raiding, outside the walls at night, or past the
  1200-tick window. (The sight rule is tested here: in a GameTest a soldier targets any monster it sees within its
  follow range of 48, and its job, where the sweep runs, is paused while it has a target.)
- `spareSidearms`: swords and axes stored less melee soldiers at home without one; the blacksmith's sidearm count and
  the archers' takes agree (N archers without one, no spare: N made, N taken).
- `partyShares` and `effectiveTier` (for `partyPower`): two archers and a stock 36 above the reserve: the first gets 36,
  the second 0; bow with 16+, sidearm, fists.
- `shortage`: on below 128, stays on until 192.
- `spent`: raider with no arrow and no sidearm.

### 9.2 GameTests (`gametest/ArrowGameTests`, each job-running test in its own `batch = "arrows_..."`, bases through `GameTestBases.register`)

Platforms are about 29 blocks apart and never cleared, and an arrow flies farther than that. So every test that shoots
puts a backstop (a 3-high wall of blocks) behind its target, and every test counts only the arrow entities it made or
its shooter fired (by UUID or owner), never all arrows in a box.

| Test | Shows |
|---|---|
| `anArcherWithNoArrowsDoesNotShoot` | a bow archer with an empty backpack and a zombie in range: no arrow entity in 200 ticks, it fights in melee |
| `aBowShotUsesOneArrow` | 5 arrows: after the first shot 4 remain and one `ALLOWED` arrow flies; after three, 2 |
| `aCrossbowLoadsOneArrow` | the same for a crossbowman; a crossbow stored charged fires its load first and takes none |
| `infinitySparesPlainArrowsOnly` | Infinity bow, 1 plain arrow: several shots, still 1, shots `CREATIVE_ONLY`; with a Slowness arrow, it is used up |
| `arrowsThatWouldHelpTheTargetAreNotShot` | a backpack with only a Healing tipped arrow: no shot, and the storehouse's Healing arrows are not taken; Harming and plain arrows: at a zombie the plain ones go, at an enemy soldier the Harming ones first |
| `aPlayerPicksUpAnNpcArrow` | an NPC's arrow in the ground: `arrow.playerTouch(fakePlayer)` (a survival `FakePlayer`, as `HeroControlGameTests` makes them; a fake player is not ticked) puts it in the player's inventory |
| `anNpcPicksUpAPlayersArrowButNotASkeletons` | a worker walking over a player's arrow takes it; a skeleton's (`DISALLOWED`) and an Infinity shot stay |
| `anArcherRestocksFromTheStorehouse` | storehouse 100, archer 2: it leaves with 48, the storehouse holds 54; below 16 it goes at once; with the storehouse emptied it goes once, finds none and waits for its regular visit (no second trip within 400 ticks) |
| `aRaiderTakesAFullQuiverButLeavesTheReserve` | storehouse 100, two drafted archers: they take 36 between them and 64 stay; both then march |
| `aStartingBaseHasItsArrows` | a granted test settlement holds `starterArrows`; a second grant adds none; an old-save base (`starterGranted` true, no flag) gets them once at the director pass; with its chests all but full it gets what fits and the rest after room is made |
| `anOldSavesArrowMovesToTheBackpack` | an NPC loaded with `FcAmmo` has 1 arrow in its backpack and none in the slot, also when the tag is loaded twice; with a full backpack the arrow lies at its feet after its first tick |
| `anArcherOutOfArrowsRunsToTheStorehouse` | at home, empty, a target, storehouse 20 blocks away with arrows: it fetches 48 and shoots again |
| `anArcherDrawsItsSidearm` | empty, a stone sword in the backpack, a zombie: the sword is in its hand within 20 ticks and it fights with it; given arrows and the zombie dead, the bow is back in hand; the sword survives an unload |
| `aSpentRaiderLeavesTheRaid` | a raider with no arrow and no sidearm, its fight over, goes `RAID_RETURN` |
| `soldiersSweepTheirArrowsAfterAFight` | arrows in the ground 8 blocks off inside the walls, in sight: a soldier that shot within the last 1200 ticks picks them up within 600; a soldier that never fought does not walk to them (400 ticks); an arrow behind a wall is left |
| `theBlacksmithFletches` | storehouse 10 arrows, 4 flint, 4 feathers, sticks: the plan holds "arrows for the archers" for 4 (read as the existing blacksmith test reads `planOrders`), then 16 more arrows in storage |
| `theBlacksmithDigsFlintFromGravel` | 4 gravel, a Fortune III shovel stored (100% flint, deterministic): 4 flint, no gravel left at the spot |
| `theTraderBuysArrowsFromAFletcher` | at `tradeOnce` level, as the existing trader test: a fletcher villager with the arrow offer and the trader with emeralds and a saved budget of 32: it buys 32 and then stops; with the storehouse filled above target after the budget was set, it still buys up to its budget (it does not look home); above target its plan sets no budget and packs feathers; below target it packs none |
| `theStartingPenHasChickensAndTheButcherCullsThem` | `BaseBuilder.spawnAnimals` (made package-visible) at a pen marker on the platform gives 2 chickens; a test base flagged as an older save (`starterGranted`, no `chickensGranted`) with an empty pen gets 2 at the director pass, and none at the next; a pen of 10 adult chickens: the butcher culls down to 4; feathers in its cargo (10 culls, P(no feather) under 1e-4) |
| `aDryArcherCountsItsSidearm` | `partyPower()` of an archer with 0 arrows and an empty storehouse is its sidearm's, while `power()` (what a scout sees) is unchanged |
| `noRangedHireWithoutAQuiver` | storehouse 48 arrows and a bow: the need is melee "garrison growth"; with 49, "ranged support" |
| `aBowHerosKitArrowStaysAndCannotBePickedUp` | a bow hero from `HeroRegistry.fixtureDefinition(id, name, "bow", "")`: shots leave the kit arrow, each shot `CREATIVE_ONLY` |

Existing GameTests that change (section 1's list):

- T3 moves the 6 `setAmmo` calls on ordinary archers (`FCGameTests` 319, 511, 537, 595; `CombatReachGameTests` 104;
  `HeroRaidGameTests` 304) to a helper `GameTestBases.quiver(npc, n)` that puts n arrows in the backpack, and rewrites
  `saveReloadKeepsIdentityWithoutStacking` to find the spectral arrow in the backpack after its double load.
  `oneArrowIsNeverConsumed` and `crossbowmanChargesAndFires` read the backpack: still 5 after 5 shots in T3, where shots
  are free.
- T4 replaces `oneArrowIsNeverConsumed` (the spec's acceptance 9, now superseded by the owner's request) with
  `aBowShotUsesOneArrow`, and `crossbowmanChargesAndFires` asserts one arrow used per bolt.
- `EliminationGameTests` 673/692 (a hero's plain arrow in the slot drops at its death), `HeroDefinitionGameTests` 460
  and `HeroGameTests` 226 (kit arrows) stay as they are.

### 9.3 Sabotage targets (each must fail its test)

| Sabotage | Caught by |
|---|---|
| the shot does not shrink the stack | `aBowShotUsesOneArrow` |
| shots stay `DISALLOWED` | `aPlayerPicksUpAnNpcArrow` |
| Infinity ignored (every shot costs) | `infinitySparesPlainArrowsOnly` |
| `NEVER` arrows treated as special | `arrowsThatWouldHelpTheTargetAreNotShot` |
| Harming arrows shot at the undead | `arrowsThatWouldHelpTheTargetAreNotShot` |
| NPC touch ignores `pickup` | `anNpcPicksUpAPlayersArrowButNotASkeletons` |
| keep rule keyed per item (arrows deposited) | `anArcherRestocksFromTheStorehouse` |
| the below-16 trip ignores the stock last seen (loops on an empty storehouse) | `anArcherRestocksFromTheStorehouse` |
| reserve ignored | `aRaiderTakesAFullQuiverButLeavesTheReserve` |
| starter grant skipped for old saves, or its overflow dropped | `aStartingBaseHasItsArrows` |
| `FcAmmo` dropped at load, or added twice on a double load | `anOldSavesArrowMovesToTheBackpack` |
| the sidearm swap left to the job (paused in the fight) | `anArcherDrawsItsSidearm` |
| the sweep ignores its 1200-tick window | `soldiersSweepTheirArrowsAfterAFight` |
| the sweep fetches arrows it cannot see | `soldiersSweepTheirArrowsAfterAFight` |
| the sweep ignores enemies in sight | `ArrowRulesTest.sweepSafe` |
| the trader reads the storehouse at the village | `theTraderBuysArrowsFromAFletcher` (the storehouse is filled after its budget is set) |
| two archers counted on the same stock | `ArrowRulesTest.partyShares` |
| the hero kit arrow consumed | `aBowHerosKitArrowStaysAndCannotBePickedUp` |

### 9.4 Live acceptance (dedicated server, `docs/HANDOFF.md` 4)

Run a fresh 7-day campaign (seed 20261107, `fc start 256`, Natsuho with Ember and Hinae with Stoneguard), followed by
`tools/rolltrack.py` and `sim.py`'s new `arrows` line each day. Then an older save loaded with the new jar for one day.

Measure, per faction per day:

- arrows in the storehouse (the ledger) and carried;
- fired, picked up, crafted, bought (`arrowStats`);
- archers that ran dry;
- archer-minutes of fire, which is fired / 29;
- shots per settlement fight and per lair fight (from `war.log`'s launch and end times).

Pass:

1. Day 0: every starting storehouse holds 512. The old save gains 512 once (owed arrows delivered within a day), and
   its archers' single arrows are in their backpacks.
2. No archer holds a bow in a fight with arrows in its storehouse within 32 blocks for more than 600 ticks (`rolltrack`
   positions plus status "Low on arrows" / "Out of arrows").
3. The flows add up for each base within the arrows lost at deaths:
   Δ(store + carried) ≈ crafted + bought + picked up + looted + skeleton drops − fired.
4. Resupply works: a base with chickens crafts arrows by day 4 once below target, and a trader buys arrows wherever its
   village has a fletcher with the offer.
5. War rate and outcomes are compared with the knowledge campaigns (launches by day 5, and the 2-of-15 settlement raid
   baseline), and reported to the owner; no pass mark, since the hard defence stays.
6. Tick time within 0.5 ms of the knowledge campaigns' 5.8-8.8 ms.
7. The measured fight costs replace section 4.2's estimates. All thresholds use the base's arrows (storehouse plus
   quivers at home, 3.4), so filling quivers counts as no use. If any base's arrows fall below the target before day
   3, or none is below 400 by day 7, `starterArrows` is reconsidered with the owner.
8. The blacksmith's tool orders keep up while it fletches (no worker waits for a tool more than a day longer than in the
   knowledge campaigns); if not, the fletcher role (5.5) is proposed to the owner.

## 10. Questions for the owner

1. **Is 512 arrows (8 stacks) "a large amount"?** By the estimate it covers about 1.2 to 1.5 weeks of the live
   campaigns' war, or 2 full refills of a full ranged garrison, before resupply is needed. Recommended default:
   **512**, set by `campaign.starterArrows`, revisited with the live counts.
2. **Do bow and crossbow heroes count as "archers"?** No shipped hero uses a bow; 28 are planned. Recommended default:
   **no, for now**: a hero keeps its bound kit arrow, never used up, because its kit is its abilities (its shots cannot
   be picked up, so it is no source of arrows); this is settled again with the first bow hero's design.
3. **An off switch back to the old rule?** A config `war.arrowsUsedUp = false` would restore the spec's single arrow
   that is never used up, as `survival.toolWear` and `survival.hunger` restore theirs, but while off it is a rule only
   NPCs get, against equal treatment. Recommended default: **no switch** (the tasks are built without one; the
   knowledge campaigns' logs are the comparison baseline).

## 11. Tasks

The owner's sentence comes first: stock, carry and restock land before any shot costs an arrow. Each task builds green
with its own tests and the full suites, updates the BALANCE/README lines of what it adds, and regenerates
`docs/factionclash-common.toml` when it adds a key. No task rebuilds the download jar or touches README line 13.

| Task | Size | What | Tests |
|---|---|---|---|
| T1 | M | Pure rules `logic/combat/ArrowRules`: `take`, `quiverTarget`, `restockNow` (with the stock last seen), `kind` (from effect categories), `usable` (Harming at the undead), `arrowCrafts`, `effectiveTarget`, `tradeBudget`, `rangedHireOk`, `sweepSafe`, `spareSidearms`, `partyShares`, `effectiveTier`, `spent`, `shortage`, the constants of 7. No behaviour change. | `ArrowRulesTest` (9.1) |
| T2 | M | Starting stock: `campaign.starterArrows` (512) replaces the 4 starter arrows; `grantStarterSupplies` sets `BaseState.arrowsGranted` and owes what did not fit; the old-save grant on the director's pass (settlements standing only) with `arrowsOwed` delivered as room is made; `Logistics.Resource.ARROWS` on the base's arrows (store + backpacks of members at home) with its shortage line (without the resupply clause); dump `arrowStats` (zeros yet) and `sim.py`'s `arrows` line. Archers unchanged. | `aStartingBaseHasItsArrows` (no second grant, the overflow owed and delivered) |
| T3 | L | Carry and restock, with shots still free and `DISALLOWED`. `npc/Quiver` over the backpack with the **read side switched over**: `armed()`, `ranged()` and `performRangedAttack` read `Quiver.has(npc, target)` and draw a free copy (no shrink), so archers keep shooting, never a `NEVER` arrow nor Harming at the undead; `FcAmmo` migration at NPC load (idempotent, overflow dropped on the first tick), with the slot kept for kit arrows only; `GuardJob.keep` with a shared arrow key; top-up at `STORAGE` and at `RAID_RETURN`'s stop; the below-16 trip with the stock last seen; `RAID_STOCK` with the reserve (in `run`'s raid-state list; a called-off raid goes to `STORAGE`); replace the arrow block of `Job.upgradeEquipment` (with `NEVER` arrows left alone); `Recruitment`, `NpcInspector` (a stack goes into the backpack), `fc admin spawn` (a 48 quiver) and the dump (`arrows`, `kitArrow`) on the backpack; `GameTestBases.quiver` and the existing tests of 9.2 moved to it. | `anArcherWithNoArrowsDoesNotShoot` (no arrow in the backpack, no shot), `anArcherRestocksFromTheStorehouse`, `aRaiderTakesAFullQuiverButLeavesTheReserve`, `anOldSavesArrowMovesToTheBackpack`, the rewritten `saveReloadKeepsIdentityWithoutStacking`, the whole existing suite green |
| T4 | M | Consumption: the shrink. `Quiver.draw` costs `processAmmoUse` (Infinity), the crossbow's load and a stored charge, the draw order (special first, `usable` against the target); shots `ALLOWED` (free shots `CREATIVE_ONLY`); a hero's kit shots `CREATIVE_ONLY` (dormant heroes keep their kit arrow); `arrowStats.fired` and `ranDry`; `oneArrowIsNeverConsumed` replaced, `crossbowmanChargesAndFires` asserts one arrow a bolt. | `aBowShotUsesOneArrow`, `aCrossbowLoadsOneArrow`, `infinitySparesPlainArrowsOnly`, `arrowsThatWouldHelpTheTargetAreNotShot`, `aBowHerosKitArrowStaysAndCannotBePickedUp` |
| T5 | M | Pickup by touch: the access transformer for `AbstractArrow.inGround` (the cfg file and its `neoforge.mods.toml` entry); every faction NPC picks up as a player does, every 10 ticks, from `FactionNpc.aiStep`; `arrowStats.pickedUp`. | `aPlayerPicksUpAnNpcArrow`, `anNpcPicksUpAPlayersArrowButNotASkeletons` |
| T6 | M | The post-fight sweep in `GuardJob` patrol (12 blocks, arrows in sight only, 600 ticks, the 1200-tick window, safety, night inside the walls). | `soldiersSweepTheirArrowsAfterAFight` (inside the window, past it, an arrow behind a wall) |
| T7 | M | Out of arrows: `ResupplyGoal` (priority 2, 32 blocks, the stock last seen, no enemy within 4); sidearms (taken by `spareSidearms`, kept by `GuardJob.keep`, swapped by `Quiver.armForFight` from the NPC's tick, `usesOtherTool` while dry, the bow back); a spent raider leaves the raid. | `anArcherOutOfArrowsRunsToTheStorehouse`, `anArcherDrawsItsSidearm` (also: the sidearm survives an unload), `aSpentRaiderLeavesTheRaid` |
| T8 | S | Council and director: a new `partyPower()` by `partyShares` and `effectiveTier` for the party's sum and the pick order (archers short of 16 last). `power()`, `BaseWatch.look` and `soldierPower` stay as they are. The ranged hire needs 49 in store. | `aDryArcherCountsItsSidearm`, `noRangedHireWithoutAQuiver` |
| T9 | M | The blacksmith: the arrow order on the base's arrows against `effectiveTarget` (ahead of swords while the `ARROWS` shortage is on), flint from gravel at its spot with a stored shovel, the sidearm order by `spareSidearms`; the shortage line's "the blacksmith fletches"; `arrowStats.crafted`. | `theBlacksmithFletches`, `theBlacksmithDigsFlintFromGravel` |
| T10 | S | Feathers: 2 chickens in the starting pen and once in an older save's pen with none (`chickensGranted`, director pass); chickens always lured back; `CHICKEN_CAP` 16. | `theStartingPenHasChickensAndTheButcherCullsThem` |
| T11 | M | The trader: `arrowBudget` fixed at home and saved; buying plain arrows from `ItemsForEmeralds` offers within it; flint and feathers kept while below target; the shortage line's "the trader buys"; `arrowStats.bought`. | `theTraderBuysArrowsFromAFletcher` |
| T12 | S | Docs: the README and BALANCE text of section 8 consolidated, the regenerated toml, `docs/HANDOFF.md` (history item 15, section 5 entry), `docs/ROADMAP.md` row "Archers use arrows up" with its Status. | doc review only |
| T13 | L | Live acceptance (9.4): the 7-day campaign plus the old-save day; fix what it finds, with a test that fails first; report the measured fight costs and the stock curve to the owner. | 9.4's pass list |

## 12. Critique (2026-11-25)

An adversarial read of the first draft against the code, vanilla 1.21.1 and the owner's rules. The numbers were rerun in
the scratch model `designs/arrows/arrow_model.py` (session scratchpad, not in the repository).

| Changed | Why |
|---|---|
| Section 1: file:line for every fact; "13 call sites" replaced by the real list (7 `setAmmo` calls; three tests that assert the old rule); the bow hero fixture named as the tests make it (`fixtureDefinition(..., "bow", ...)`; there is no `fixture_bow`) | The first draft's list named classes that only build `Arrow` entities and missed the three tests that would go red. |
| No off switch: `war.arrowsUsedUp`, its test and its README sentence taken out of the build; asked as owner question 3 with "no switch" recommended | Off, it is a rule only NPCs get (never-used-up arrows for them, not for players), against the equal-treatment rule; the knowledge campaigns are the comparison baseline anyway. |
| Dormant heroes keep their kit arrow (6.7, T4) | The draft changed them while claiming "no change for heroes"; the code gives them one (`HeroEntity.java:499-500`). Whether hero archers should use arrows up is owner question 2. |
| Harming arrows are not shot at the undead (2.1, 2.2, `usable`) | Harming heals zombies and skeletons (`isInvertedHealAndHarm`), the commonest targets: the same fault as the Healing arrow the draft fixed. |
| Touch pickup and the sidearm swap live in `FactionNpc`, not in a job; `usesOtherTool` only stops `survival` from swapping back | `WorkGoal.free` pauses the job while the NPC has a target, so a job-side swap would never happen in a fight. |
| The below-16 trip and `ResupplyGoal` remember the arrows last seen at the storehouse | Otherwise an archer at 15 arrows walks to an empty storehouse at every patrol tick. |
| `RAID_STOCK` joins `GuardJob.run`'s raid-state list; a called-off stock stop goes to `STORAGE`; `RAID_RETURN`'s storehouse stop tops up | `run` forces every non-raid state to `RAID_TRAVEL`, which would skip the stop; a returning raider waited up to 6000 ticks for its home quiver. |
| One definition of a spare sidearm for the archer and the blacksmith | With "one more must stay stored" for the archer and "make one when none is spare" for the blacksmith, the last archer never got its sword. |
| The old-save grant runs on the director's pass, settlements standing only, with `arrowsOwed` for what does not fit | `FactionStorage` sees no chest in an unloaded chunk at load time, and `grantStarterSupplies` drops what does not fit (old storehouses are often full). |
| `FcAmmo` migration idempotent; its overflow drop waits for the first tick | The existing save test loads a tag twice; HANDOFF 5 forbids drops while an entity joins with its chunk. |
| `partyPower` shares the stock above the reserve in pick order; its effect stated (at most 0.25 of a soldier) | Each archer counted the same arrows; a bow is tier 1 like a stone sword. |
| The ranged hire needs 49 (the quiver after the bill) and the crafting target is always above 5 quivers (max with 5 × `quiverArrows` + 1) | A hire at 48 left the recruit at 47; with 5 × `quiverArrows` at or above `arrowTarget` (a `quiverArrows` of 64 gives 320 against 256) no arrow was crafted and no archer hired, for good. |
| The sweep fetches only arrows it can see; its GameTest negative is the 1200-tick window, its sight rule a unit test | An entity query found arrows behind walls (line-of-sight rule); a soldier targets any monster within 48 and its job pauses, so the draft's zombie case could not fail under its sabotage. |
| Trader: no "known village with a fletcher"; its test at `tradeOnce` level | Reports count trading villagers, not professions; the existing trader test works at that level. |
| Pen test through `spawnAnimals`; shooting tests with a backstop and arrows counted by UUID | No GameTest builds a 49 × 49 base on a 24 × 24 platform; arrows fly past the 29-block spacing. |
| Chicken cap kept at 16, with its reason; the flock settles at about 12 with 8 culls a day from day 3 | "Reaches its cap by day 3" was not so. 12 gives the same yield only with a prompt butcher; waiting hens count against the cap (`AnimalCarerJob.java:252`), and with half a day of delay 12 gives 6.7 a day, 16 still 8. |
| An older save's pen gets the breeding pair once; chickens always lured | Its pen has no chicken and the carer lures only species kept (and cows and pigs), so an older save had no feather source and would never fletch. |
| Numbers: damage 4 (bow, 3 a quarter of the time) and 5 (crossbow); full leather is 7 points; weekly use 330-440 (so 512 is 1.2-1.5 weeks); hero health about 120 at level 1; flint by hand about 3 minutes; tick-time baseline 5.8-8.8 ms | Rerun from the code and the logs (`knowledge.md` 12). The 512 conclusion stands. |
| "Like the withdrawal ... within 4 blocks" corrected to a new rule; scope table 0.1; the fletcher question made a decision with a live trigger (9.4 item 8); T3 sized L | `WithdrawGoal` has no such check; the owner asked for two things and the additions should be named; the question slots went to decisions the owner must make. |
