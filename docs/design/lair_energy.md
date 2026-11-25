# The Hollow's energy: lairs that gather, grow, breed and watch

Design, 2026-11-25, with a critique the same day (section 11). Not built yet; tasks T1 to T9 (section 9; cite them as
"lair_energy T1" and so on, since `HEROES_V2.md` 15.9 has T1 to T3 of its own) build it. It answers the open question
about the Hollow's fixed 300-block sense of settlements (`knowledge.md` 9; `recon.md` 8, whose revision of 2026-11-25
already points here in its answer 3) and replaces the 300-block sense: the sense is kept, but its range now grows with a
building (section 3.4).

## 0. The request, and the design in brief

The owner's words (2026-11-25): "monster lairs can automatically gather energy and spend energy. Its energy can be spent
on extra buildings nearby, more monsters or increasing detection range".

1. **Energy** is a number each lair keeps (`BaseState.lairEnergy`, saved), 0 to 300. Every 400 ticks the lair gathers 1,
   or 3 at night (about 110 a day). A person (a faction NPC, a hero or a player) who dies on the lair's own ground adds 10,
   and so does a kill its monster makes away from home, once that monster walks back. Each lair keeps its own energy.
2. **Buildings.** The Hollow has no builders. Its lair **grows** them block by block, one block every 5 seconds, as
   vanilla's sculk spreads from a catalyst. Each grown block is shown with sculk particles and the sculk spread sound.
   All blocks are vanilla, and anyone breaks them at their own hardness and gets vanilla drops. There are two kinds:
   * the **watch spire**, a bone column on the hall's roof above the heart (54 blocks), which is how detection grows;
   * up to two **nests**, low mounds of mossy stone and bone in the lair's diagonal quadrants, wholly on its 28-block
     ground (53 blocks each). Each nest raises the monster cap by 4 and is where new monsters come out.
   A broken block loses its share of the effect, with no refund, and the lair grows it back. At the lair's fall its
   buildings stay as ruins.
3. **More monsters cost energy**: a zombie 12, a skeleton 16. This replaces today's free respawn and the cap's growth of
   one a day. The cap is now 5, plus 4 for each working nest, at most 12. Spawning keeps today's rules: vanilla zombies
   and skeletons, vanilla loot tables, the lair's mark, the 10-block leash, and one spawn per 400 ticks. A spawn needs a
   free cell to stand in; a lair with none, or in peaceful difficulty, spawns nothing and pays nothing.
4. **Detection** stays a sense, the kept exception, but it is no longer 300 for free. It starts at **96** and grows by
   **5 for each standing block of the spire's column**, up to **256**. A lair always has room to grow its spire, because
   the spire stands on its own roof, so every lair can come to sense any settlement within 256. Breaking the spire blinds
   the lair again until it regrows.
5. **The Hollow's choice** is a pure, testable rule run every 100 ticks. In order: defend when its monsters fight on their own ground;
   keep at least 4 monsters; repair a finished building that lost blocks; grow the spire while it senses no settlement;
   buy monsters up to the cap; grow nests; grow the spire higher; save. A rule that cannot act this period (no free
   spawn cell, no cell it can grow) gives way to the next; a rule that cannot pay waits. Growth keeps 16 energy in
   reserve once a settlement is sensed. Nothing is said in chat. Players see the growth itself, and operators see the
   decision.
6. **Raids are kept**: once every 30000 ticks, with at least 4 monsters, on the nearest settlement it senses, sending half
   its monsters. Losses now cost energy, so every raid is an investment the lair has to rebuild.
7. **Balance** (a model, section 5.2). The raid clock is unchanged and both live lairs sense their settlement by day 0.55
   and 0.69 after they are built, so the first raid comes when it does today. The first party has 2 monsters (today 3),
   the next two 3 to 5. Lairs hold 7 to 9 monsters on day 3 and 10 to 12 on day 5 (today 8 and 10), and send 5 or 6 from
   the fourth raid (today 4 to 5). Monsters killed at a lair now cost it energy to replace (today they come back within
   400 ticks). Lairs buy only zombies and skeletons, the kinds walls stop today, so the settlements' hard defence is
   unchanged.

Added beyond the three things asked for (the owner welcomes additions when told): deaths on a lair's ground and kills
its monsters carry home also give energy (2.2); the lair answers a fight at home first (4.3, rule 1); a raid party keeps
the lair's mix of kinds (4.4); a `war.lairEnergy` switch gives today's lair back for comparison runs (6.8).

Considered and left out: a bone wall round the lair (it would blind a recon watching from the ground, `recon.md` 3.7),
outposts (a second, smaller lair; question 3), monsters that build (section 3.2.7), and **spiders and creepers** for
sale: they climb or blast walls, and the owner keeps the hard defence for now ("Its fine to keep a hard defence, i
believe as more tools and hero presence are introduced things will change"). They belong with the breaching round
(`breaching.md` X1 to X3), when settlements get their answers to breaches.

## 1. Today (what the code does)

| What | Today | Where |
|---|---|---|
| The lair | a 15 x 15 hall: walls of mossy cobblestone and cobblestone 4 high (a third of the top course broken) with a 3-wide, 3-high gap in the middle of each side, a roof at y+4 over the inside (one roof block in nine left out at random), the heart at the centre, the spawn point at (3, 0, 3), the ground levelled ±8 and cleared 16 up; no plan (`structures` is empty) | `LairLayout`, `BaseBuilder.buildLair` |
| Monsters | vanilla zombies (60%) and skeletons, `finalizeSpawn(EVENT)`, a leather helmet with drop chance 0 when they spawned without one, persistent, marked `factionclash_lair` = lair id, leashed 10 blocks from the centre (`restrictTo` + `MoveTowardsRestrictionGoal`) | `BaseBuilder.spawnLairMob`, `FCEvents.onJoin` |
| How many | 5 at the start (`lairBaseCap`), +1 every 24000 ticks to 12 (`lairMaxCap`); a missing one is replaced **for free**, one every 400 ticks | `FactionDirector.lair` |
| Losses | a monster that falls within 28 blocks (`LAIR_GROUND`) is missed at once; one that falls further off after 6000 ticks away | `FCEvents.onDeath`, `FactionDirector.lair` |
| Raids | every 30000 ticks (`lairRaidIntervalTicks`) from its building, with at least 4 monsters, on the nearest standing settlement within **300 blocks** (3D distance), sending max(2, half) of them; raiders walk to the heart (`restrictTo(heart, 4)`), each one within 4.5 of it adds 100 a period, 3000 breaks it; the raid ends at the target's fall, with no raider left, or after 24000 ticks | `FactionDirector.lairRaid` |
| The sense | "The Hollow senses settlements within its territory": an exception to "a faction knows only what its people saw", kept for the owner's word | `knowledge.md` 9, `recon.md` 8 |
| Damage | zombies and skeletons deal half damage to NPCs (spec 14); spiders full | `CombatRules.isUndeadHalfDamageSource` |
| Chunks | the chunks of the hall's MIN/MAX box (±8) are force-loaded, ticking (`ChunkLoading.forceBase`); a monster more than 28 blocks out carries a 3 x 3 ticking area. Chunks one or two away from forced ones are loaded but tick no entities (vanilla ticks entities only at ticket level 31, `ChunkLevel`) | `ChunkLoading` |
| The director | the lair's step runs every 100 ticks only while a campaign is started (`CampaignManager.tick`), so GameTests call it themselves | `FactionDirector.tick` |

**Live numbers** (seed 20261107, `fc start 256`, the verification campaign of `knowledge.md` 12, and the logs of the two
before it):

| lair | centre | settlements by distance |
|---|---|---|
| 5 | (272, 64, -48) | Tide Alliance 132, Stoneguard 143, Ember Legion 244, Grove Covenant 320 |
| 6 | (208, 69, 240) | Ember Legion 172, Stoneguard 295, Grove Covenant 334, Tide Alliance 417 |

Lairs are placed at least 100 blocks from every settlement (`CampaignStarter.tooClose`). On this map 300 blocks
reaches all but one or two settlements, so the "sense" is in effect "knows every settlement".

| day | lair monsters (both lairs, verification run) | Hollow raids launched (monsters sent) |
|---|---|---|
| 1 | 6, 6 | 1.28: lair 5 on Tide (3), lair 6 on Ember (3) |
| 3 | 8, 8 | 2.53: 3 and 2; 3.78: 3 and 4 |
| 5 | (lair 5 razed at 4.04), 10 | 5.03: lair 6 (4) |
| 7 | 12 | 6.28: lair 6 (5) |

Days here are the world's clock (`gameTime` / 24000, as `rolltrack.py` logs it). The raid days are the server log's
"Hollow lair N raids" lines placed between the `FC_DUMP` lines' `gameTime` round them (30331 to 31196 for the first,
then 60428 to 61090, 90239 to 91094, 120195 to 120878, 150624 to 151210): the lairs were built at about day 0.025, and
each raid came 30000 ticks after the last, the first 30000 after the building, exactly the code's clock.

In none of the three campaigns did the Hollow raze a settlement. The polls (every 659 ticks) saw five one-monster dips
across both lairs in their first four days, and none at lair 6 after day 4. The free respawn puts a loss back within
400 ticks, so most dips fall between two polls, and a raider missed 6000 ticks after its death looks the same as a loss
at home: the record gives no rate of losses at home. Councils raided lairs: the Tide Alliance launched on lair 5 at day
3.94 with 3 soldiers against 8 monsters (3 of them away on the lair's own raid of 3.78), razed it by day 4.04 and lost
none. In the first run the Ember Legion broke the Hollow's last lair at day 7.19 with 8 soldiers.

## 2. Energy

### 2.1 What it is

`BaseState.lairEnergy`: an int per lair, saved under `"lairEnergy"`, from 0 to `ENERGY_MAX` (300). A new lair starts
with 0. An old save's lair also starts with 0 (the key is missing, section 6.7). Gathering beyond the cap is lost.

### 2.2 How it is gathered

| Source | Gives | Decided | Why |
|---|---|---|---|
| A base rate | **1 every 400 ticks** by day | yes | the lair "breathes": a steady income that makes the request's "automatically" true. 400 ticks is today's spawn tick, so nothing new runs more often |
| Night | **3 every 400 ticks** from dayTime 13000 to 23000 (`Job.isNight`'s window) | yes | monsters are of the night, as in vanilla (they spawn and stay out at night); 35 day pulses + 25 night pulses = **110 a day** |
| A person dying on the lair's ground | **+10** for a faction NPC, a hero or a player that dies within `LAIR_GROUND` (28, horizontal, `FactionDirector.atLair`) of the centre, whatever killed it, except a generic kill (`DamageTypes.GENERIC_KILL`: a faction's fall killing its members, an operator's `/kill`), which is no death in the world | yes | vanilla's sculk catalyst feeds on deaths near it (whoever caused them); a raid that fails at a lair feeds it. The lair knows of it at once, as it already knows of its own monsters' deaths on that ground (the roll call's rule) |
| A kill its monster makes away | **+10**, carried by that monster (`factionclash_carried` in its data, the killer being the death's source entity, a skeleton for its arrow) and given when it is back within 28 blocks; lost if it dies first. A death on the ground counts once, by the row above | yes | the Hollow knows only what its own people bring home (`knowledge.md`; the same as an NPC's experience carried home) |
| Animals, wild monsters, its own monsters' deaths | 0 | no | a kill farm of cows or wild zombies at a lair would feed it for nothing |
| Monsters alive | 0 | no | more monsters giving more energy compounds: a full lair would only grow faster |
| Darkness or biome | 0 | no | a lair in a dark forest would out-earn one on plains, and the map, not the play, would decide |
| Buildings | 0 | no | same compounding; buildings are what energy buys, not where it comes from |

The pulse is pure: `LairRules.gain(night)` returns 1 or 3; `LairRules.add(energy, n)` clamps to 300.

### 2.3 Several lairs

**Each lair keeps its own** energy. No energy moves between lairs.

* Lairs stand 100 blocks or more apart. Sharing would need a carrier walking between them, or a second sense.
* A lair that falls loses its own energy and nothing of another's (sections 3.2.6 and 6.3).
* Each lair raids its own nearest settlement, so their needs are separate anyway.

## 3. Spending

### 3.1 Costs

| Item | Cost | Notes |
|---|---|---|
| zombie | **12** | the cheapest |
| skeleton | **16** | ranged, the lair's better defender |
| a building block | **2** | spire and nest alike, paid as each block grows |
| the watch spire, whole | 108 (54 blocks) + 2 per roof hole filled under it | section 3.2.1 |
| a nest, whole | 106 (53 blocks) + 2 per root block under it | section 3.2.2 |

In peaceful difficulty a lair neither spawns nor pays: vanilla discards a monster there on its next tick
(`Mob.checkDespawn`), so the lair checks the difficulty first and the spawn gives way to the next rule (4.3). Elsewhere
a spawn needs a **free spawn cell**: the cell and the one above it hold no solid block and no fluid, and nobody stands
there. Without that check a player's block on the spawn point would make every bought monster suffocate as it appears,
and the lair would pour its energy into deaths. The lair tries its spawn points in turn (4.3 and 3.3); with none free,
there is no spawn and no payment. A spawn is paid once `addFreshEntity` succeeds.

### 3.2 Buildings

#### Who builds them

**The lair grows them.** No monster builds anything.

* The Hollow has no hands. Vanilla zombies and skeletons have no goal for carrying or placing blocks. A zombie
  laying bone blocks would be new AI with no basis in the game.
* Vanilla already has a world that grows by itself: sculk spreads from a catalyst, with nobody placing it. The lair
  grows in the same way: from the heart outward, one block at a time, with sculk's particles and sound.
* The standing rules are about NPCs: they break and place blocks at player speed, and the same rules apply to NPCs and
  players. Growth is not an NPC acting. It is how this one faction builds, the way settlements build with builders and
  stone. Players cannot join the Hollow (`/fc join` takes human factions only), so no player ever has, or lacks, this
  way of building. It is still a Hollow-only rule beside the sense, so question 2 asks the owner.

How growth looks and behaves (`LairGrowth.step`, once per director period):

* **One block per lair per 100 ticks** (5 s), at most: slower than a builder at work. A watching player sees a spire
  rise about 12 blocks a minute.
* In plan order: plinth, then column and ribs bottom-up for the spire; walls, then roof, then the cobwebs for a nest. A
  block always grows on or beside a solid block, its own or anyone's, never floating. So a foreign block in the spire's
  column is grown round (the column goes on above it), and a cell whose block was broken grows back in place, as any
  repaired plan block does.
* A block grows only into **air or a replaceable cell** (grass, a flower, a snow layer, a fern). It never replaces a
  solid block or a fluid. It never grows into a cell where an entity stands: the builders' `BlockWork.obstructed` check,
  so nobody is walled in. It never grows in an unloaded chunk.
* A cell held by **someone else's block** (a player's dirt, a fallen gravel block) is **skipped**: the lair has no hands
  to clear it, and growth goes on with the next cell. A foreign block costs the spire 5 of range and a nest one of its
  53 (3.2.2 says how many must stand), so one block cannot stop a building. A player who fills six cells of a nest
  before they grow keeps that nest from working until the blocks go: that is counterplay anyone has, and the lair grows
  its other nest meanwhile.
* A cell held by an entity, or in an unloaded chunk, is passed over **for this period** only. When no cell of a building
  can grow this period, the rule that chose it gives way to the next (4.3), so a monster or a player standing in a
  broken cell never stops the lair's spawning.
* Each grown block: `level.setBlock(..., UPDATE_ALL)`, a burst of 6 `ParticleTypes.SCULK_SOUL` at the block, and
  `SoundEvents.SCULK_BLOCK_SPREAD` at volume 1 (vanilla's 16-block range).
* Growth ignores `mobGriefing`, as builders' placing does: it is the faction's building, not a mob changing the world.
  A creeper's blast keeps obeying it.

#### 3.2.1 The watch spire

**Where.** On the hall's roof, above the heart. One per lair. The roof is levelled with the lair, force-loaded, inside
the lair's own land and inside nobody else's, so the spire never needs a site search and can never fail to find a place.
So every lair can come to sense any settlement within 256 (`lairDetectMax`): lairs start at least 100 blocks from every
settlement, and the base range is 96. A lair whose nearest settlement is farther than `lairDetectMax` never raids, as
today beyond 300; on the larger maps of ROADMAP Phase 3's far-apart starts the key must grow with the map.

**Plan** (relative to the heart at (0, 0, 0); the roof is y = 4):

| part | cells | block |
|---|---|---|
| roots | any of the 9 roof cells under the plinth (x, z in -1..1, y = 4) that is air (the hall's random roof holes) | mossy cobblestone |
| plinth | 3 x 3 at y = 5 | cobbled deepslate (9) |
| column | (0, y, 0) for y = 6 to 37 | bone block, axis Y (32) |
| ribs | at y = 13, 21 and 29 (column heights 8, 16, 24): the four cells beside the column | mossy cobblestone wall (12) |
| crown | (0, 38, 0) | soul lantern (1): a light seen from afar at night |

That makes 54 blocks, plus 0 to 9 roots. A rib grows right after the column passes its height, and the crown grows last.
The hall's interior (y 0 to 3) is untouched, so the heart, the raiders' way to it and the monsters' room do not change.
`Terrain.level` cleared the air only up to 16 above the floor. Higher up, a cell held by terrain (an overhanging cliff,
a tree's leaves) is skipped like a foreign block.

**Effect:** the detection range (section 3.4): 96 plus 5 for each **standing bone block of the column**, at most 256. It
counts the standing column cells, not the unbroken run from the plinth, so one broken block costs 5 blocks of range, not
the whole spire.

#### 3.2.2 Nests

**Where.** In the lair's **diagonal quadrants**. A building cell (dx, dz) from the centre must have
**min(|dx|, |dz|) ≥ 12** and lie **on the lair's ground**, dx² + dz² ≤ 28² (`FactionDirector.atLair`, the same 28
blocks the roll call, the kill energy and the threat use). The ground is a circle: a square corner out to 26, as first
written here, reached 36.8 blocks from the centre at (26, 26), off the ground, so a death or a fight in that nest
would not have been the lair's. With the 5 x 5 footprint, 34 origins per quadrant fit, and every nest cell lies within
22 of the centre on either axis. One building per quadrant and at most 2 nests, so two quadrants always stay free
(section 3.2.3 has the rest of the rule).

**Plan** (a 5 x 5 footprint, its mouth facing the lair):

| part | cells | block |
|---|---|---|
| roots | under any footprint cell whose ground lies below the site's floor (spread ≤ 2, so at most 2 per column) | coarse dirt |
| walls | the 16-cell ring at y = 0 and y = 1, minus a 3-wide, 2-high mouth in the middle of the side nearest the hall (ties: the side facing along x) | mossy cobblestone (26) |
| roof | 5 x 5 at y = 2: the ring bone block, the 3 x 3 inside mossy cobblestone | bone block (16), mossy cobblestone (9) |
| inside | the two inner corners away from the mouth, at y = 1 | cobweb (2) |

That makes 53 blocks plus roots. The inside is 3 x 3 and 2 high, and the mouth 3 wide and 2 high, so a zombie or a
skeleton can walk out. **Spawn point:** the inside's middle cell, at y = 0.

**Effect:** while at least **48 of its 53** planned blocks stand (90%), a nest **works**. A working nest raises the
lair's cap by **4**, and new monsters come out of its mouth (with two nests, they take turns). Below 48 the nest stops
working until it is grown back to 48. The roots do not count toward the 48.

#### 3.2.3 Where a nest may go

`LairRules` has the pure geometry and scoring; `LairSites` reads the world. A nest's origin is valid when every cell of
its footprint and plan:

1. lies in one quadrant, as defined above;
2. stands on ground whose height varies by **at most 2** across the footprint, and that ground is within 6 blocks of the
   lair's floor. The nest's floor (y = 0) is one above the highest ground under its footprint, and each lower cell gets
   roots up to it;
3. is air or replaceable from the floor up to the roof (no leaves, logs or stone in the way). There is no water or lava
   in the footprint, or on the ground under it;
4. is in no base's plan; is outside every other base's land plus 8 (`BaseZones.inForeignArea`, the workers' own
   margin); and lies more than the **settlement clearance** (Chebyshev) from every standing settlement's centre: the
   largest of the configured `mineSiteRadius`, `forestRadius`, `fishingRadius` and `homeAreaRadius` plus 8, so **72**
   with the defaults (64 each; each key goes up to 128 or 256, so a constant 72 would not hold). Nests therefore never
   sit in a base's plan, land or work area;
5. lies more than 8 from every workplace record's origin (a mine's mouth, a fishing stand); and is on the map
   (`MapBounds`, margin 8).

**Choice:** the valid origin with the least height spread, then the one nearest the centre; quadrants are tried in a
fixed order (north-east, south-east, south-west, north-west). The search reads heightmaps for one quadrant per
director period, 34 origins at a time. Every nest cell lies within one chunk of the hall's forced chunks, so those
chunks are loaded (ticket level 32 or 33) and readable with no player near even before the lair forces its whole
ground (3.2.4, "Chunks"). A quadrant with no valid origin is **barren for a day** (24000 ticks, saved like a base's
`barrenUntil`). A lair with no nest site keeps a cap of 5 and still raids.

**Why diagonal quadrants.** A look along one of the hall's four gap axes stays clear. Take any vantage on an axis, say
(0, d), and any point (gx, gz) with |gx| ≤ 11: every point of the line between them has |x| ≤ 11, and a nest cell has
min(|dx|, |dz|) ≥ 12, so it is never on that line. That covers today's passing look (`BaseWatch.look`: a grid of
reach 5 inside the hall, monsters counted in a ±15 box), the leash (10), and the recon of `recon.md` 3.7 (a design,
not built: a ground vantage on a gap axis 46 to 64 blocks out, a grid of reach 10). The spire stands on the roof's
centre, behind the hall's walls and roof for any ground ray. A look from a diagonal, as a passing scout may take, can
be blocked by a nest, as a player's view would be. The four gap axes also stay open: the monsters' way out, and
raiders' way in. A unit test pins this geometry (section 7.1, item 9).

#### 3.2.4 How a building is recorded, and what that changes

Each building is a `StructureRecord` in `lair.structures` (named `hollow_spire` and `hollow_nest_<quadrant>`), so the
lair has a **plan** like a settlement's. Beside it, `BaseState.lairBuildings` saves a small `LairBuilding` per building:
its kind, quadrant, origin, mouth side, roots, and `complete` (whether every growable cell once stood). The records are
saved as any structure is. The plans are regenerated from the `LairBuilding` only to check them.

Making the lair's plan non-empty has side effects. These are deliberate, and the same as for every faction's plan:

* **Workers** never dig or fell a planned cell (`WorkSafety.mayDig`). The `planIndex().isEmpty()` guard there now lets
  the "under a base" rule cover the hall: no worker digs within the hall's walls box plus 2, from 16 below its floor up.
  `MineSites` already keeps mines 16 from a lair, so nothing changes in practice.
* **Fighters** never break a planned block to reach a target (`Reach.mayBreak`: "a faction's own building", whatever
  faction). The same guard turns on the "base's ground" branch: no fighter breaks the hall's floor or the rock under it.
  Unplanned blocks inside the hall stay breakable to anyone, since no NPC is on the Hollow's side.
* **Stuck NPCs** never dig out through a planned cell, nor through the hall's box (±8, from its floor to 8 above):
  `DigOut`'s guard turns on for the lair as for a settlement. The hall's 3-wide gaps keep a way out.
* **Explosions** queue a planned cell inside a base's box for its builders (`FCEvents.onExplosion` ->
  `pendingRepairs`). A lair has no builders and repairs from its own plan (3.2.5), so `onExplosion` skips lairs; else
  the queue would only grow.
* **So, of the NPCs, only explosions break the Hollow's buildings**: a hero's explosive move (`HeroBlasts`, gated by
  `mobGriefing` and `heroes.explosiveBlocks`), and a wild creeper's blast. That is exactly how NPCs
  treat a settlement's planned walls today; breaching (`breaching.md` X1 to X3) will change it for both kinds of plan at
  once. **Players** break them by hand like any block.
* **The lair's land stays the hall's box** (MIN/MAX ±8). It is not widened to cover the nests, so `inForeignArea` (a
  lair's box plus margin plus 8) does not push into a nearby settlement's work square. Rule 4 already keeps nests clear
  of settlements' work areas, and `MineSites` keeps mines 16 beyond the hall's walls (±23), which holds every nest cell
  (within 22), so no mine opens under a nest.
* **Chunks:** today `ChunkLoading.forceBase` forces only the chunks of the hall's box (±8), ticking. The chunks round
  them are loaded but tick no entities (vanilla ticks entities only at ticket level 31: `ChunkLevel`,
  `ChunkMap.FORCED_TICKET_LEVEL`; NeoForge's ticking region ticket of radius 2 gives 31 to the forced chunk only). With
  no player near, a monster that comes out of a nest there would stand frozen, and so would one that follows a fight
  past the forced chunks while still on the ground (no traveller ticket within 28). A lair under the energy rules
  therefore forces its **whole ground**: the square ±28 round its centre, which holds every building it can grow. That
  is 16 to 25 chunks (a settlement's ±25 box forces about as many). The square is computed from the centre alone, so
  `forceBase(false)` at the fall clears exactly the chunks it forced. The lair applies it on its first director tick
  after each server start (forcing is idempotent), so an old save's lair gets it too; with `war.lairEnergy` false it
  releases the outer ring on that tick instead (forced tickets are saved, so turning the key off must undo them).

#### 3.2.5 When someone breaks a block

* The effect follows the standing blocks at once: the spire loses 5 range per column block, and a nest stops working
  below 48 (`LairRules.range`, `LairRules.nestWorks`, read every period).
* **No refund.** The energy became the block, and the block drops by its **vanilla loot table** to whoever broke it:
  bone block, cobbled deepslate, mossy cobblestone, the wall, the soul lantern (each needing a pickaxe, as vanilla
  says, `requiresCorrectToolForDrops`); a cobweb gives string, or itself to shears or silk touch. That is a raider's
  or a player's loot, as a settlement's broken walls are.
* The lair **grows it back** at the block's cost, as "repair" in its choice (section 4). A finished building
  (`complete`) that is missing growable cells is repaired before anything new is grown. Cells held by someone else's
  block are not counted as missing.
* A nest that drops the cap leaves the monsters it already has alive. None spawn until the count is under the new cap.

#### 3.2.6 At the lair's fall

* Its buildings **stay as ruins**, plain blocks, like the hall itself. A fallen base's plan is ignored by every check
  (`b.destroyed` is skipped in `mayDig`, `mayBreak`, `inForeignArea`), as for a fallen settlement, so anyone may mine
  them.
* No growth, no gathering, no spawning. Its energy is gone (section 2.3).
* Its chunks are unforced (the same ground square).
* Its monsters: unchanged from today. Those of a lair that is not the last live on near its ruins. At the Hollow's last
  lair, every lair monster dies wherever it is (built 2026-11-25, `CampaignManager.eliminate`). A monster carrying a kill
  home carries it into its death.

#### 3.2.7 Considered and left out

| Idea | Why not |
|---|---|
| A bone wall round the lair (a 2-high ring at 12 with gaps on the axes) | it would hide the monsters' ground from a recon on the ground at 46 to 64 blocks: a blind report, so no council could ever launch on a walled lair once `recon.md`'s rule is built. Its only effect is in the world (parties walk through its gaps), and its 168 blocks would starve the monsters |
| Cobweb snares at the gaps | they catch the lair's own monsters as much as raiders |
| An outpost (a second, smaller lair) | a new base changes elimination and the war far more than the rest of this design; question 3 |
| Monsters that build | section 3.2, "Who builds them" |
| Energy-earning buildings | compounding (section 2.2) |
| Spiders (climb walls, full damage to NPCs) and creepers (blast walls) for sale | they would take Hollow raids past the walls that stop them today, and the owner keeps the hard defence for now; with the breaching round (`breaching.md` X1 to X3). Not built, not even switched off: no dormant code |

### 3.3 More monsters

* **The cap:** `lairBaseCap` (5) **plus 4 for each working nest**, at most `lairMaxCap` (12): 5, 9, then 12 (13 with
  two nests is held at 12). It **replaces** today's +1 a day. `BaseState.lairCap` is kept as the cap last computed (the
  dump reads it). `lastLairGrowth` is still read from old saves and no longer used.
* **Paid spawning:** one monster per 400 ticks at most (today's cadence), while the count is under the cap, when the
  choice (section 4) says "spawn" and the energy covers its cost. The five starting monsters of a new lair are free,
  as today.
* **Which kind** (`LairRules.nextKind`, deterministic): the kind whose share of the lair's monsters, once it is added,
  falls furthest below its target. The targets are **7 zombies : 5 skeletons**: today's 60/40 rounded to a full lair of
  12. Ties go to the zombie.
* **Spawn rules:** today's (`spawnLairMob`): `EntityType.create`, `finalizeSpawn(MobSpawnType.EVENT)`, a leather helmet
  with drop chance 0 for a zombie or skeleton that spawned without a helmet (so it does not burn by day), persistent, the
  lair's mark, `restrictTo(centre, 10)`, vanilla loot tables at death. Only the place changes: a working nest's spawn
  point if the lair has one (with two, they take turns), otherwise the hall's (3, 0, 3); the first of these with a free
  spawn cell (3.1) is used, and with none free there is no spawn this period. A monster from a nest walks into the
  leash by itself (`MoveTowardsRestrictionGoal`); the forced ground (3.2.4) keeps it ticking on the way. Each spawn
  shows 8 `ParticleTypes.SOUL` at its feet.
* **The count** is today's `lairMobs`: living monsters, including raiders away not yet missed. A far death becomes known
  after 6000 ticks, and only then does the lair pay to replace it. That keeps the knowledge rule.

### 3.4 Detection range

**Decision: it stays a sense, the kept exception, but bounded, and grown through a building in the world.**

* The lair senses every **standing settlement whose centre lies within its range**, measured horizontally from the
  lair's centre. Today's check measures in 3D, with Y included.
* **range = 96 + 5 × (standing column blocks of its spire), at most 256** (`war.lairDetectBase` 96, `war.lairDetectMax`
  256, PROPOSED `DETECT_PER_BLOCK` 5). This replaces the fixed 300.
* What it reads: **only that a settlement stands there**, its position and its fall (a raid ends at its target's fall, as
  today). It never reads a defence, a roster or a hero, and the Hollow estimates nothing.

Why a sense and not a spire that "sees" (a line of sight from its top):

* the lines would be 100 to 256 blocks long and cross chunks nobody has loaded. Loading them costs a lot; skipping them
  would make the rule depend on which chunks happen to be loaded;
* a player on the spire's top sees only within its view distance (10 chunks, 160 blocks, by default) and through fog, so
  a "sees" rule would still not be a player's sight;
* the owner kept the sense, and the request is a sense that grows ("increasing detection range");
* tying the range to a tall, breakable spire gives everyone a counterplay in the world: climb it and break the column,
  and the lair is blind until it regrows.

What the lairs on the live map would sense (column height h, range 96 + 5h):

| lair | nothing (h < 8) | first settlement | then |
|---|---|---|---|
| 5 | 96 | Tide 132 at h = 8 (136) | Stoneguard 143 at h = 10; Ember 244 at h = 30 (246); Grove 320 never |
| 6 | 96 | Ember 172 at h = 16 (176) | Stoneguard 295 and the rest never |

Both lairs raid the same settlement they raid today (the nearest). At 256, lair 6 no longer senses Stoneguard (295),
which today's 300 reached but never raided. Growing to the first settlement costs lair 5 17 blocks (34 energy, day
0.55) and lair 6 29 blocks (58 energy, day 0.69), both well before the first raid (30000 ticks, day 1.25 from the
lair's building).

## 4. The Hollow's choice

### 4.1 When

Every director period (100 ticks, `FactionDirector.lair`) for each standing lair, as today. The decision is pure:
`LairPolicy.decide(LairView v)` returns a `Decision(action, building, kind, reason)`. Growth places at most one block
per period, and a spawn happens at most once per 400 ticks.

### 4.2 What it reads (only the lair's own)

| Input | From |
|---|---|
| energy | `lairEnergy` |
| monsters, by kind | `lairMobs` (living, loaded, with the raiders away not yet missed) |
| cap | `LairRules.cap(baseCap, maxCap, workingNests)` |
| buildings | each `LairBuilding`: planned, standing, growable missing cells, `complete`, works |
| range, and the nearest settlement it senses | `LairRules.range`, `LairRules.nearestSensed` |
| **threatened** | a monster of the lair **on its ground** (within 28 of the centre) and **not on a raid** targets a person who is also on the ground (a faction NPC or hero, or a player in survival or adventure mode), or was hurt by one (`getLastHurtByMob`, which vanilla keeps only 100 ticks, `LivingEntity.baseTick`: read every period, which is 100 ticks long); or a lair monster died on the ground. Each sets `lastThreatAt`, and the lair stays threatened for 600 ticks after it (`THREAT_TICKS`). Raiders fighting at their target are no threat to the lair. This is what its own monsters lived through at home, the same way the settlement alarm needs one of its people to see or hear an intruder |
| a raid out | `lairRaidTarget >= 0` |
| what can happen this period (for "can't act, can't pay", 4.3) | a spawn tick or not (one period in four); peaceful difficulty; whether any spawn point has a free spawn cell (3.1); for each building, whether any missing cell can grow now (not held by a foreign block, an entity or an unloaded chunk). The world-reading side fills these in; the choice itself stays pure |

### 4.3 The rules, in order

| # | When | Action | Reason shown |
|---|---|---|---|
| 1 | threatened and under the cap | **spawn**; if it cannot pay this period, only rule 4 may grow (the spire while nothing is sensed), and nothing else does | "defend" |
| 2 | fewer than **4** monsters (`MIN_HOME`, the raid threshold) | **spawn** | "rebuild the garrison" |
| 3 | a `complete` building misses growable cells | **grow** it (the spire first while nothing is sensed, else the nests first) | "repair" |
| 4 | no settlement sensed and the spire is unfinished | **grow the spire** | "seek" |
| 5 | under the cap | **spawn** | "muster" |
| 6 | fewer than 2 nests begun, or one unfinished, and a quadrant has a site | **grow a nest** | "brood" |
| 7 | the spire unfinished | **grow the spire** | "watch farther" |
| 8 | otherwise | **save** (up to 300) | "save" |

**Can't act, can't pay.** A rule whose action cannot happen this period gives way to the next rule: a spawn with no
free spawn cell (3.1) or in peaceful difficulty, a growth with no cell it can grow (every missing cell is held by a
foreign block, an entity or an unloaded chunk, 3.2). A rule that could act but cannot pay **waits**: the lair saves for
it and nothing below it runs that period. So a player standing in a broken nest cell, or a block on the hall's spawn
point, never stops the rest of the lair, and a lair never spends on a nest the energy a needed monster is waiting for.
A spawn on a period that is not a spawn tick (one in four) also waits.

**A threat never blinds a lair.** At the cap, a threat changes nothing and the other rules run. Under the cap, the
spire still grows while nothing is sensed. A scout lingering near a lair only makes it fill its cap, which rule 5 does
anyway once a settlement is sensed.

**The reserve:** once a settlement is sensed, growth (rules 3, 6 and 7) needs energy ≥ the block's cost + **16**
(`RESERVE`, a skeleton's price), so a loss can always be replaced. Rule 4 spends everything, because finding a target
matters most. Spawning spends everything.

What the owner's states turn into:

* no settlement detected: grow detection (4);
* a settlement detected: monsters for a raid (5), then a nest for more of them (6);
* under attack: monsters (1);
* losing buildings: rebuild once quiet (3).

The rule is short enough that each line is one unit test (section 7.1, item 6).

### 4.4 Raids: kept

The rule stays as it is today. A raid goes every `lairRaidIntervalTicks` (30000), with at least 4 monsters, on the
nearest settlement **it senses**. It sends max(2, half) of its monsters, who walk to the heart and break it by the
same progress. It ends at the target's fall, when no raider is left, or after 24000 ticks.

Why keep it:

* it is the pressure the live campaigns were measured with (section 5);
* the energy model already changes what a raid means: every raider lost costs the lair 12 to 16 to replace, so a raid
  is now spent strength. Before, it was a free trickle;
* the choice fills the lair to its cap before the interval runs out (rule 5), so a raid goes with what the lair could
  afford.

Two small changes:

* **The target must be sensed** (section 3.4).
* **The party keeps the lair's mix.** Raiders are taken in turn by kind (each time the kind furthest above its share at
  home), so those left at home keep the same share of zombies and skeletons. Today the party is whatever the set's
  order gives.

A raider that kills someone and later walks home (the raid over, the leash back at the lair) brings its 10 energy with
it.

### 4.5 What players see

* **In the world:** the spire rising on the roof with a soul lantern on top; nests growing in the diagonal quadrants; the sculk
  particles and sound at each grown block; soul particles where a monster comes out; a raid party marching. That is all.
* **Chat:** nothing. The Hollow has no members to tell, and its spending is not news.
* **Scouts:** a look at a lair (`BaseWatch.look`, and the recon's when built) adds the buildings in sight to the
  report's text. There is one ray to the spire's crown and one to each nest's roof centre: "Hollow lair, 4 monsters seen,
  24% of its ground in view, a spire about 20 high, 1 nest". The station's map tooltip shows that text. It is
  player-side presentation only: no council reads it (section 6.1).
* **Operators:** `/fc admin dump` per lair: `energy`, `range`, `cap`, each building's `standing/planned`, `works`,
  `complete`, the last decision and its reason. `sim.py` prints a lair line:
  `lair 5: mobs=8/9 energy=42 range=136 spire=8/32 nests=53/53,14/53 decide=brood`. One log line is written when the
  decision's reason changes, at most one per lair every 1200 ticks ("Hollow lair 5: brood (nest NE 14/53, energy 42)").

## 5. Balance

### 5.1 PROPOSED constants (BALANCE.md, "Hollow lairs")

| Name | Value | Why |
|---|---|---|
| `LairRules.ENERGY_MAX` | **300** | a whole nest and a full raid's replacements; more would let a quiet lair bank days of income |
| `LairRules.PULSE` | **400** ticks | today's spawn tick |
| `LairRules.GAIN_DAY` / `GAIN_NIGHT` | **1** / **3** | 110 a day; with 2 at night (85 a day) lairs fell to 9 monsters by day 7 under raid losses (5.2's model), well below today's 12 |
| `LairRules.KILL_ENERGY` | **10** | almost a zombie per person killed; a bonus, not relied on (no live Hollow raid has killed much) |
| `LairRules.RESERVE` | **16** | a skeleton, so rule 1 can always answer a loss |
| `LairRules.MIN_HOME` | **4** | the raid threshold |
| costs | zombie **12**, skeleton **16**, a block **2** | a monster is worth 6 to 8 building blocks; a nest (106) costs about 8 zombies, so the cap's +4 pays back over a few raids |
| `LairRules.NEST_CAP` | **+4** | 5, 9, 12 with two nests (the old `lairMaxCap`) |
| `LairRules.NEST_MAX` | **2** | leaves two quadrants free |
| `LairRules.NEST_WORKS` | **48** of 53 (90%) | a few foreign blocks or broken corners do not shut a nest; a real attack does |
| `LairRules.QUADRANT_MIN` | **12** | outside the leash (10) and every axis sight line (section 3.2.3); the outer edge is the lair's ground itself, `FactionDirector.LAIR_GROUND` (28, a circle), so a nest is never off the ground |
| `LairRules.SITE_SPREAD` | **2** | the most root blocks under a nest |
| `LairRules.CLEAR_MARGIN` | **8** | the settlement clearance is the largest configured work radius (mines, woodland, fishing, home area) + 8: 72 with the defaults |
| `LairRules.THREAT_TICKS` | **600** | the alarm's `NOTICE_MEMORY` |
| `LairRules.BARREN_TICKS` | **24000** | a quadrant with no site is tried again a day later |
| `war.lairDetectBase` | **96** | below the 100 at which lairs are placed, so every lair begins blind and its spire matters |
| `LairRules.DETECT_PER_BLOCK` | **5** | 32 column blocks reach 256 |
| `war.lairDetectMax` | **256** | less than today's 300; on a radius-256 map it still reaches most of the map |
| `lairBaseCap` / `lairMaxCap` | 5 / 12 (unchanged) | now "the cap with no nest" and "the most with nests" |
| `lairRaidIntervalTicks` | 30000 (unchanged) | |
| growth | one block per **100** ticks | the director's period; slower than a builder at work |

### 5.2 A lair's expected state

The model (a short script in this round's scratchpad: the designer's `designs/lair_model.py`, rerun by the critique as
`designs/lair_model_critic.py`; the scratchpad does not last) runs rules 2 and 4 to 8 of section 4 every 100 ticks.
There is no threat and no broken building in it, so rules 1 and 3 never apply. It uses the constants above, both live
lairs' distances, a campaign starting at dawn, the raid party taken in turn by kind (4.4), and no council raid on the
lair. Days count from the lair's building. Two losses are **assumptions**, not measurements:

* every raider dies at its target and is missed 7500 ticks after the launch (the march, the fight, the 6000);
* one monster is lost at home every 0.8 day per lair. The live record cannot give this rate (section 1), so the model
  also runs with no loss at home.

It counts the monsters at home before any raid at the same tick. In the table, "nest blocks" lists each nest's blocks
grown.

**Lair 5** (Tide Alliance at 132):

| day | monsters (today live) | cap | energy | range (column) | nest blocks |
|---|---|---|---|---|---|
| 1 | **5** (6) | 5 | 16 | 136 (8) | 24 / 53 |
| 3 | **8** (8) | 9 | 6 | 136 (8) | 53, 12 / 53 |
| 5 | **12** (razed at 4.04) | 12 | 6 | 136 (8) | 53, 48 |
| 7 | **12** | 12 | 6 | 161 (13) | 53, 53 |
| 10 | **12** | 12 | 64 | 256 (32) | 53, 53 |

**Lair 6** (Ember Legion at 172):

| day | monsters (today live) | cap | energy | range (column) | nest blocks |
|---|---|---|---|---|---|
| 1 | **5** (6) | 5 | 16 | 176 (16) | 12 / 53 |
| 3 | **9** (8) | 9 | 2 | 176 (16) | 53, 2 / 53 |
| 5 | **11** (10) | 12 | 10 | 176 (16) | 53, 48 |
| 7 | **12** (12) | 12 | 6 | 211 (23) | 53, 53 |
| 10 | **12** (12) | 12 | 92 | 256 (32) | 53, 53 |

**Raids** (monsters sent), model against today live:

| | 1st | 2nd | 3rd | 4th | 5th on |
|---|---|---|---|---|---|
| model (days from building) | 1.25: **2** | 2.50: **3 or 4** | 3.75: **4** | 5.00: **5 or 6** | **6** |
| today (verification run, world clock) | 1.28: 3, 3 | 2.53: 3, 2 | 3.78: 3, 4 | 5.03: 4 | 6.28: 5 |

The live row is on the world's clock, which ran 0.025 day before the lairs were built (section 1); the raid clock is
the same, so the raids come at the same times, and only the party sizes differ.

How much the table moves (the critique's variants, days 3 / 5 / 7, monsters at home, lair 5 and lair 6):

| variant | lair 5 | lair 6 | what changes |
|---|---|---|---|
| the table (one loss at home per 0.8 day) | 8 / 12 / 12 | 9 / 11 / 12 | full range (256) by day 8 |
| one loss per 1.6 day | 8 / 12 / 12 | 8 / 12 / 12 | range 241 on day 7, full by day 8 |
| no loss at home | 8 / 12 / 12 | 8 / 12 / 12 | full range by day 7 (lair 5) or 6 (lair 6) |
| half of each party comes home | 9 / 12 / 12 | 9 / 12 / 12 | full range by day 6, energy at 300 by day 10 |
| campaign starts at noon | 7 / 12 / 10 | 7 / 10 / 10 | the first settlement sensed at day 0.39 and 0.52 |
| campaign starts at dusk | 7 / 12 / 8 | 7 / 10 / 9 | sensed at 0.22 and 0.35 |
| night gain 2 instead of 3 | 7 / 9 / 9 | 5 / 9 / 9 | parties of 2 to 4 to day 7; why `GAIN_NIGHT` is 3 |

With the chosen constants (every row but the last) the parties hold within one monster: 2, then 3 or 4, then 4 or 5,
then 5 or 6. A count on a given day moves by up to 4 with the hour the campaign starts, because a lair refilling after
a raid gathers three times as fast at night: so the live check compares parties, and compares counts with a rerun of
the model on the campaign's own start time and losses (7.4). Raider survival has never been measured, so the live check
measures it.

### 5.3 What changes for the war

* **The early Hollow is today's.** The first raid comes when it does today, parties have 2 to 5 monsters for the first
  three raids, and there are 7 to 9 monsters on day 3. Monster kinds are unchanged, so walls keep the Hollow out as
  before (it razed no settlement live).
* **The mid-game Hollow is a little stronger.** It holds 10 to 12 monsters on day 5 (today 10; 12 only on day 7) and
  sends parties of 5 or 6 from its fourth raid. At the live launch on lair 5 (day 3.94: 3 soldiers, party power 5.6,
  against "about 7"; the lair counted 8, 3 of them away on its own raid of 3.78) the model's lair would count 9, 4 of
  them away on its raid of 3.75, so about as many at home as today. Lairs live longer only where a council looks when
  the lair is full. This is the request's point ("more monsters"), and the live check watches it.
* **Attrition now counts.** A council party that kills 8 monsters at a lair and is beaten back leaves the lair about 110
  energy (a day) short. Today it refilled for free, one every 400 ticks (8 in under three minutes). The people the
  party lost there feed the lair (+10 each).
* **The public prior** (`Intel.lairPrior`, "5 + 1 a day up to 12", read by the councils for a lair nobody has looked at)
  stays as it is. Under the model it errs by at most 2 in either direction until day 5: low on days 3 to 5, high on day
  1. Either landing order works. If `recon.md` R5 lands first, the prior is gone and nothing here touches it. If this
  lands first, the prior stays as the rule of thumb every faction knows, and it is no less true than today's for a lair
  that has been raided.

## 6. Interactions

### 6.1 Scouts and the recon of lairs (`recon.md` 3.7)

* The recon's vantage (`recon.md` 3.7, not built: the ground on a gap axis, 46 to 64 blocks out) and its grid (reach
  10) are untouched. No building crosses an axis sight line (section 3.2.3), and the spire stands on the roof's centre.
* Monsters from a nest walk into the leash within seconds and are counted there. One still inside a nest during a watch
  is missed, as a player would miss it.
* Buildings seen go into the report's **text** only (4.5). The council's estimate of a lair stays "monsters seen, power
  1 each". A nest says how many monsters a lair *can* have, not how many it has, and counting those is the owner's
  focused-report rule.

### 6.2 The war councils' lair targets

The rule is unchanged: a lair is a target. A council raids the lair with the fewest monsters seen, under the two-thirds
rule, and breaks its heart. Its raiders never break the lair's buildings (`Reach.mayBreak`, 3.2.4), and neither do a
settlement raid's raiders. A raid that breaks the heart leaves the spire and nests as ruins. People killed at the lair
feed it (+10 each). Heroes on a lair raid behave as on any raid, and their explosive moves can break the lair's blocks
(3.2.4).

### 6.3 Elimination (built 2026-11-25)

Unchanged and complete. When the Hollow's last lair falls, every lair monster dies wherever it is; one away in an
unloaded place dies when it loads (`CampaignManager.lairMonsterJoined`). Energy, growth and buildings add nothing to the
fall: buildings stay as ruins (3.2.6) and the energy is gone. A lair that is not the last falls alone: its monsters live
on near its ruins as today, and its energy is not handed to another lair.

### 6.4 The Hollow's roll call

Unchanged: a monster's far death becomes known after 6000 ticks. The energy model only makes this matter more, because
the lair pays for a replacement only once it knows of the loss. A kill carried home is given only by a monster that
reaches home (2.2).

### 6.5 Knowledge and the exception

`knowledge.md` 9's bullet becomes: "**The Hollow senses settlements within its detection range**: 96 blocks, plus 5 for
each standing block of its watch spire's column, at most 256. It reads only where a settlement stands and whether it has
fallen; its monsters never leave the lair to look." Everything else the Hollow uses is its own: energy, its own monsters'
fights and deaths (the threat), its own buildings, kills carried home, and deaths on its own 28-block ground, which it
knows of at once as it already knows of its own monsters' deaths there (the roll call's rule, `knowledge.md` 7).

### 6.6 Equal treatment

* All blocks are vanilla, broken at their own hardness by anyone, with vanilla loot tables.
* The Hollow's plan is protected from workers and fighters exactly as every faction's plan is (3.2.4), and players may
  break it as they may break a settlement's walls.
* The kill energy counts every person who dies on the ground, NPC or player alike, whoever killed it.
* The threat input counts a player and an NPC alike.
* Growth is the faction's way of building (3.2), and nobody else is denied it, because no one else can be of the Hollow
  (`/fc join` takes human factions only). It is still a second Hollow-only rule beside the sense, so question 2 puts it
  to the owner.
* The leftover asymmetry is older than this design and unchanged: zombies and skeletons deal half damage to NPCs, not
  to players (spec 14).

### 6.7 Saves

* New keys on a lair's `BaseState`: `lairEnergy` (int), `lairBuildings` (list: kind, quadrant, origin, mouth, roots,
  `complete`), `barrenQuadrants` (tick per quadrant), `lastThreatAt` (long). The plans live in `structures`, as any
  base's do.
* `CampaignData.FORMAT` stays 4. Every key is optional: missing means 0, none or empty. `recon.md` R2's FORMAT 5 is
  independent of this.
* **An old save's lair** loads with energy 0, no buildings and a range of 96: the new rules from its first tick. A raid
  already under way keeps its target. Its monsters stay, even above the new cap of 5 (no killing), and it buys none
  until it is under the cap. It begins its spire at once. Keeping 300 for old saves would leave two rules alive for a
  world nobody keeps (campaign worlds are test worlds), so it is not done.
* `lairCap` is still saved, as the last computed cap; `lastLairGrowth` is read and no longer used.

### 6.8 Config keys (section `war`, beside the lair keys there today)

| Key | Default | Meaning |
|---|---|---|
| `lairEnergy` | **true** | false: today's rules exactly (free respawn, +1 a day, the 300-block sense, no buildings, only the hall's box forced), for comparison runs |
| `lairDetectBase` | **96** (32 to 512) | the range with no spire |
| `lairDetectMax` | **256** (32 to 1024) | the most a spire gives (raise it with the map on far-apart starts) |

The existing `lairBaseCap`, `lairMaxCap` and `lairRaidIntervalTicks` keep their names. Only the first two's comments
change. The keys stay in `war`, because moving them to a new section would reset a server's existing values. The other
numbers are PROPOSED constants in `LairRules`.

### 6.9 Performance

Per lair, every 100 ticks:

* the pure choice;
* reading the standing count of its plans, at most 54 + 9 roots and 2 × (53 + 50 roots) cells, about 270 block reads;
* at most one block placed;
* the threat check, which reads its own monsters' targets and hurt times (they are already in the loop).

The nest site search reads the heightmap columns of the 106 cells of one quadrant per period, and only until a nest
has a site. Forced chunks are 16 to 25 per lair (today 4 for the hall's box): with two lairs, up to 42 more
entity-ticking chunks on the server, with nothing on most of them but grass and trees; the live check measures the tick
time (7.4, item 10). A spawn is one per 400 ticks, as today. No long rays: the sense is a distance check.

### 6.10 The standing rules

* Vanilla things stay available: the Hollow locks nothing, and only uses vanilla blocks and mobs.
* There are no pacts.
* The hard defence is untouched: lairs buy only the zombies and skeletons that walls stop today (no spiders or
  creepers, 3.2.7).
* Single-player only: the sound and particles are server-sent vanilla effects.

## 7. Tests

### 7.1 Unit tests (pure: `logic/campaign/LairRulesTest`, `LairPolicyTest`)

1. Gain: 1 by day, 3 at night (dayTime 12999 / 13000 / 22999 / 23000), the clamp at 300, kill energy added and clamped.
2. Range: 0 column blocks give 96, 8 give 136, 16 give 176, 32 give 256; the clamp at `lairDetectMax`; a smaller
   `lairDetectBase`.
3. Cap: no nest 5, one 9, two 12 (13 held at 12); a nest with 47 blocks standing does not work, one with 48 does.
4. Kind choice: from 3 zombies and 2 skeletons the sequence to 12 is the 7 : 5 mix; ties go to the zombie.
5. Costs: the table of 3.1. A spawn the lair cannot afford is no spawn.
6. The policy: one test per rule of 4.3, each with the inputs that make it the first to apply. Plus the reserve: growth
   with energy 17 is refused once something is sensed and allowed while nothing is; with energy 18 it is allowed. Rule 4
   spends to 0. Repair comes before a new nest. A threat under the cap stops all growth but the seeking spire; at the
   cap it changes nothing. A monster on a raid fighting at its target is no threat. **Can't act, can't pay:** a spawn
   with no free spawn cell, or in peaceful, gives way to the next rule; a building with no growable cell gives way; a
   spawn that cannot pay waits and nothing below it runs.
7. The nearest settlement sensed: by horizontal distance, fallen settlements skipped, none beyond range, the nearest of
   several.
8. The raid party: max(2, half); taken in turn by kind so the home mix holds.
9. Quadrant geometry: every cell of a nest valid at any origin in the four quadrants has min(|dx|, |dz|) ≥ 12 and
   dx² + dz² ≤ 28²; there are 34 such origins per quadrant. **For every vantage (0, ±d) or (±d, 0) with d from 24 to 64
   and every point with |x| (or |z|, for a vantage on the x axis) ≤ 11 within 15 of the centre, no nest cell lies on the
   segment between them.** The mouth faces the hall.
10. Plans: the spire has 54 blocks plus roots; the column's 32 cells and their order (each rib after its height, the
    crown last); a nest has 53 blocks plus roots, a 3 x 2 mouth, and the spawn cell inside.
11. Site score: spread ≤ 2 is accepted and 3 refused; the least spread wins, then the nearest; with the default radii a
    cell 72 from a settlement's centre is refused and 73 is accepted; with `mineSiteRadius` 100, 108 is refused and 109
    accepted.
12. Old save defaults: a `LairView` built from missing keys has energy 0, no buildings and range 96.

### 7.2 GameTests (each with its own `batch`; `GameTestBases.registerLair`)

Helpers. `GameTestBases.registerLair(h, faction HOLLOW, relCentre, ...)` builds a real `LairLayout` hall at a known
relative position (with a fixed random, so its roof has no holes unless the test asks for one) and registers the lair
with an id from the test range. It cleans up its monsters, buildings (the spire stands above the template, so the
framework does not clear it), forced chunks and records afterwards. A transient `BaseState.testGround` (the quadrants'
inner edge and the ground's radius), never saved, brings the quadrants within the template: for example 9 and 19 in
the 32 x 32 `mobility_field`, the way `testHalf` shrinks a settlement.

How the tests drive the lair:

* **They call the lair's step themselves.** The director runs only in a started campaign (section 1), and the real
  cadence (a block per 100 ticks, a spawn per 400, a pulse per 400) would make a nest take 4800 ticks. The lair's step
  (`LairRuntime.step(level, data, lair, t)`, the body of today's `FactionDirector.lair` made callable) and its parts
  (`LairEnergy.pulse(lair, dayTime)`, `LairGrowth.step`) are called directly, several periods per game tick where a test
  needs many, with the period's `t` and `dayTime` passed in. So no test changes the world's time, and none runs past a
  few hundred ticks. One test (7) checks the real cadence over 800 ticks.
* **`skyAccess = true`** for every test that grows the spire (the crown is 38 above the floor; the barrier ceiling
  would stop it) and for every test that searches for a nest site (heightmaps read the barrier ceiling otherwise).
* **Nothing marches.** A raid launched in a test is checked at launch (target, party) and ended in the same tick, so no
  monster walks 150 blocks across other tests' platforms. "Far" cases (60 blocks out) move the lair record's centre
  away instead of the monster, as `KnowledgeGameTests.aLairMissesAMonsterThatFellFarOffOnlyLater` does.
* Fake settlements for range tests are records only, with a centre and a heart point and no blocks, at 120 to 300
  blocks. Each test runs in its own batch.

1. `lairGathersByDayAndNight`: 10 pulses with dayTime 1000 give +10; 10 with dayTime 14000 give +30; the clamp at 300.
2. `aSpireGrowsBlockByBlockOnTheRoof` (skyAccess): energy 60 and no settlement. After 5 steps exactly 5 plan cells
   stand, plinth first. After 24 steps the plinth (9), the column to 8, its four ribs and 3 more column blocks stand:
   the column holds 11 and the range reads 151 (96 + 5 × column blocks). Energy has gone down by 2 per block, net of
   the pulses. A variant with one roof hole under the plinth grows that root first;
   another with a dirt block in column cell 3 grows the column on above it.
3. `breakingTheSpireLowersTheRangeAndItRegrows`: a mock player breaks 3 column blocks; the range drops by 15; the
   blocks drop as items; the lair grows them back (repair) before anything new.
4. `detectionGatesTheRaidTarget`: a fake settlement at 150; 5 monsters; the interval passed. With range 96 there is no
   target. Grow the column to 11 (151) and the raid goes to it (checked at launch, then ended). A fake settlement at
   140 that is fallen is never the target.
5. `aNestGrowsInAQuadrantAndRaisesTheCap` (`testGround`, skyAccess): a valid site is chosen in a diagonal quadrant,
   every cell on the ground; the nest grows a block per step; the cap reads 5 until 48 blocks stand, then 9. The next
   spawn comes out of the nest's inside.
6. `breakingANestRemovesItsEffect`: from 53 standing, a mock player breaks 6 (47 left): the cap is 5 again, the
   monsters above it live on, and no new spawn comes until the count is under 5. Regrown to 48, the cap is 9.
7. `monstersAreBoughtWithinTheCap`: cap 5 with 3 monsters and energy 100. Over 800 ticks of the real cadence, one
   spawn per 400 ticks, each paying its kind's cost, stopping at 5. With energy 5, none spawn. The kinds follow the mix.
   With a block on every spawn point, nothing spawns and nothing is paid (and, from T4 on, the lair goes on growing).
8. `nestsNeverInAnotherBasesLandOrWorkArea` (skyAccess): a settlement's record placed so that its clearance covers two
   quadrants: only the other two are used. A quadrant filled with stone is barren, and is not tried again until a day
   later.
9. `growthSkipsAForeignBlockAndNeverWallsSomeoneIn`: dirt placed in a plan cell is left there and growth goes on with
   the next cell; a mock player standing in a cell holds that cell until it moves; while a mock player stands in the
   only missing cell of a finished nest, the lair still spawns (repair gives way).
10. `workersAndFightersLeaveTheLairsPlanAlone`: on a grown nest block, `WorkSafety.mayDig` and `Reach.mayBreak` are both
    false; after the lair's fall both are true and the blocks are still there (ruins).
11. `aDeathOnTheGroundFeedsTheLairAndAKillIsCarriedHome`: an NPC killed 10 blocks from the centre gives +10, once. A
    lair monster that kills an NPC with the lair's centre 60 blocks away carries 10, which is given when the centre is
    moved back within 28 of it. If it dies away, nothing is given. A generic kill on the ground gives nothing.
12. `aThreatenedLairBuysDefendersAndGrowsNothing`: an enemy soldier on the ground hurts a monster. Until the cap is
    reached, or 600 quiet ticks pass, no nest grows, and spawns happen to the cap. The same fight with the lair's centre
    60 blocks away (a raider at its target) is no threat.
13. `lairEnergySavesAndOldSavesLoad`: a save and reload keeps energy, buildings, `complete`, barren quadrants and the
    plans. A lair NBT without the new keys loads with energy 0, no buildings and range 96, and keeps its monsters.
14. `lairEnergyOffIsTodaysLair`: with `war.lairEnergy` false there is free respawn, the cap +1 a day, the 300 sense, no
    growth, and only the hall's box is forced. It is written in T2 and extended by T4, T5 and T6 for what each of them
    changes.
15. `theLairsGroundIsForceLoaded`: under the energy rules the lair forces the chunks of its ±28 ground on its first
    step, so a monster spawned at a nest ticks and walks to the leash with no player near. At the fall the same chunks
    are unforced (the lair's forced-chunk record, a test accessor as `travellerRadius` is for travellers, goes back to
    empty); with `war.lairEnergy` turned false the outer ring is released.

### 7.3 Sabotages (each must fail a test; each is run in the task that builds what it breaks)

| Sabotage | Fails | Task |
|---|---|---|
| the reserve ignored | unit 6 | T1 |
| a quadrant cell at min(\|dx\|, \|dz\|) = 11, or one off the ground's circle | unit 9 | T1 |
| night gain the same as day | GameTest 1 | T2 |
| spawns without paying | GameTest 7 | T2 |
| a spawn onto a blocked spawn cell | GameTest 7 | T2 |
| the kill energy given without the carrier reaching home | GameTest 11 | T2 |
| growth replacing a foreign block | GameTest 9 | T3 |
| buildings left out of the plan, so a worker may dig them | GameTest 10 | T3 |
| a nest working below 48 | GameTest 6 | T4 |
| the ground not forced, so a nest's monster stands frozen | GameTest 15 | T4 |
| the range counting all spire blocks instead of the column | GameTests 2, 3 | T5 |
| the sense left at 300 | GameTest 4 | T5 |
| a repair that waits on an occupied cell and blocks the spawns | GameTest 9 (its last case) | T4 |

T6 reruns every row against the full choice.

### 7.4 Live acceptance (a campaign: seed 20261107, `fc start 256`, `days.sh` 8 days, `rolltrack.py` and `sim.py`)

For each lair, `sim.py`'s lair line once every quarter day. Check that:

1. its spire begins within the first game hour and senses its first settlement by day 0.8 after the lair is built
   (model: 0.55 and 0.69 from dawn; earlier from a later hour, 5.2);
2. its first raid comes 30000 ticks after the lair is built, once a settlement is sensed (the raid clock is unchanged:
   day 1.28 on this seed's world clock in the last campaign), on the same settlement as today (lair 5 on the Tide
   Alliance, lair 6 on the Ember Legion);
3. raid parties are within ±1 of the raid row of 5.2; monsters on days 1, 3, 5 and 7 are within ±2 of a **rerun of the
   model** with this campaign's start hour (its `dayTime` at the lairs' building) and the raider survival and home
   losses measured in item 4 (the table of 5.2 moves by up to 4 with the hour alone);
4. **raider survival**: the share of each party that comes home (the model assumes 0), and the home losses per day
   (the model assumes one per 0.8 day per lair), from a log of each lair monster's death with its place (on the ground
   or away) and its killer;
5. each lair's first nest is sited by day 1.5 (model: during day 1, with no player near); nests stand in diagonal
   quadrants, every cell within 28 of the lair's centre, none in a base's plan or land, none within the settlement
   clearance (72 with the defaults); every monster spawned at a nest reaches the leash within 600 ticks (none frozen);
   spires on the roofs, crowns lit at night;
6. no worker digs or fells a lair block; no NPC breaks one except by an explosion (log any lair block broken, with what
   broke it);
7. **settlements razed by the Hollow: 0** (today 0): lairs buy only zombies and skeletons;
8. council raids on lairs still happen (by day 7), and their outcome and losses are recorded against the lair's count at
   the launch;
9. passing looks at lairs list the buildings; any recon (when built) counts within its rule;
10. tick time per day within 0.5 ms of the last campaign (6.6 to 8.8 ms); forced chunk count per lair ≤ 25;
11. with the viewer: a player breaks 6 blocks of a nest, the cap drops, and the regrowth is seen and heard. Screenshots
    of a grown spire, a nest and a lair at night go to the owner.

## 8. Docs the implementation rewrites (none edited in this design round)

Each task that changes behaviour updates the README and BALANCE lines and the regenerated toml for what it changes
(CLAUDE.md); T8 rewrites the rest:

* `README.md` (The Hollow);
* `BALANCE.md` (the "Hollow lairs" bullet: energy, costs, cap, buildings, range; the war council's lair prior note);
* `knowledge.md` 9 (the sense's new wording, 6.5) and its section 8 test list;
* `recon.md` 8 ("The Hollow"): its revision of 2026-11-25 already points here; check the wording once both land;
* `HANDOFF.md`: the architecture row for `campaign/` and `logic/campaign` (`LairRules`, `LairPolicy`, `LairRuntime`,
  `LairEnergy`, `LairGrowth`, `LairSites`, `structure/LairBuildings`); how to watch lairs in section 4; section 6's
  open items, and the sense's line in it;
* `ROADMAP.md`: a row for this request with its status;
* `docs/factionclash-common.toml`, regenerated (the three new keys and the two changed comments).

The download jar and README's line 13 are **not** rebuilt in this phase (the owner deferred them until heroes are
playable).

## 9. Implementation tasks

Each task builds, passes both suites and carries its own tests and sabotages (7.3). Order rule: **no build shrinks the
sense before spires can grow, or the cap before nests can grow.** T2 keeps the 300 sense and the daily cap; T4 swaps the
cap for nests in the same task that grows them; T5 swaps the sense for the spire's range in the same task that grows
the spire. `war.lairEnergy` false gives today's lair in every build.

| Task | Size | Content | Tests in the task |
|---|---|---|---|
| **T1** | S | Pure rules, additive: `logic/campaign/LairRules` (gain, clamp, costs, cap, nest works, range, kind choice, party, nearest sensed, quadrant geometry on the ground's circle, site score with the clearance from the radii), `LairPolicy.decide` with `LairView` and `Decision` (the rules of 4.3 with "can't act, can't pay"); no game change | unit 7.1 items 1 to 12; sabotages: the reserve, the quadrant |
| **T2** | M | Energy in the game: the lair's step made callable (`LairRuntime.step`, today's `FactionDirector.lair`); `BaseState.lairEnergy`, `lastThreatAt` and their save (old save gives 0); the pulse (`LairEnergy.pulse(lair, dayTime)`); kill energy (`FCEvents.onDeath`: a death on the ground, a carried kill, no generic kill); the three config keys (`lairEnergy` read; the two range keys registered); **paid spawning** with the free spawn cell and peaceful checks, today's daily cap and today's 300 sense; dump `energy`; `sim.py`; the test helper `GameTestBases.registerLair` | GameTests 1, 7 (cap by days here), 11, 13 (energy part), 14; sabotages: night gain, unpaid spawns, blocked spawn cell, carried kill |
| **T3** | M | The growth engine, additive (after T2, whose helper it uses): `structure/LairBuildings` (spire and nest plans with roots), `LairBuilding` and its save, `LairGrowth.step` (plan order, the support rule, skip rules, `obstructed`, sound and particles, `complete`, regrowth in place), the plans in `structures` with their side effects (3.2.4: `WorkSafety`, `Reach`, `DigOut`, `onExplosion` skipping lairs), ruins at the fall, the transient `testGround`; buildings are placed only by tests in this task, so campaigns do not change | GameTests 9 (its first two cases), 10, 13 (buildings); a growth test on a test-placed nest; sabotages: foreign block, plan left out |
| **T4** | M | Nests in the campaign: the lair forces its ±28 ground (`ChunkLoading.forceBase`; the outer ring released when the key is off), `LairSites` (the quadrant search, a quadrant per period, barren quadrants), the nest effect (the cap becomes base + 4 per working nest, the daily growth removed), spawns from a nest; an interim choice: repair first, then spawn to the cap, else grow a nest, with "can't act, can't pay" | GameTests 5, 6, 7 (cap by nests), 8, 9 (its last case), 15; 14 extended (no nests, no ground forcing when off); sabotages: a nest below 48, the ground not forced, repair blocking spawns |
| **T5** | M | The spire and the sense: the spire's growth on the roof, `LairRules.range` used by `lairRaid` (300 replaced; horizontal distance), the interim choice extended (spire first while nothing is sensed) | GameTests 2, 3, 4 (skyAccess); 14 extended (the 300 sense when off); sabotages: range from all blocks, the sense left at 300 |
| **T6** | M | The full choice: `LairPolicy` wired in, with the threat input (`lastThreatAt`, 600 ticks), `MIN_HOME`, the reserve, the decision and its reason kept for the dump and the log line; the raid party by kind | GameTest 12; 14 extended; every sabotage of 7.3 rerun against the full choice |
| **T7** | S | Presentation and tools: buildings in a lair look's text (`BaseWatch`, the map tooltip), the dump's lair fields, `sim.py`'s lair line, `rolltrack.py` and `warcheck.py` lair columns (energy, range, buildings, decision, raid parties, survivors, each lair monster's death with place and killer) | a look GameTest with a nest and a spire in sight; the tools on a short run's logs |
| **T8** | S | Docs of section 8 not already updated by T2 to T7 (no jar, no README line 13) | none |
| **T9** | L | Campaign verification (7.4), a fresh world on the same seed and setup as the last campaigns; the model rerun on the campaign's start hour and measured losses (5.2) against what happened | the live checks of 7.4 |

Order: strictly T1 to T9. T3 needs T2's test helper; T5 extends T4's interim choice; T6 replaces it.

## 10. Questions for the owner

1. **Detection as a sense tied to the spire?** The lair senses settlements within 96 blocks, plus 5 for each block of
   its spire's column, up to 256, instead of 300 for free. The alternative is to make it fully "seen": a monster
   prowler that walks out and looks, like a scout, and reports when it gets home.
   **Recommended default: the spire sense.** It is cheap, it is visible in the world, and anyone can blind a lair by
   breaking its spire. A prowler could come later if you want the Hollow under the same knowledge rule as everyone.
2. **May a lair grow its buildings by itself?** The Hollow has no builders, so its lair grows the spire and the nests
   one vanilla block every 5 seconds, with sculk's particles and sound, the way sculk spreads in vanilla. Anyone breaks
   the blocks at their own hardness and gets vanilla drops. NPCs keep placing blocks at player speed; this is the
   Hollow's alone, like its sense. The alternative is monsters carrying and placing blocks, which vanilla zombies and
   skeletons have no behaviour for.
   **Recommended default: yes, grown like sculk.**
3. **Outposts?** Should a grown lair found a second, smaller lair nearby, a new base the Hollow must lose before it is
   eliminated? **Recommended default: not now.** It changes elimination and the war far more than the rest of this
   design. It could come after the live check shows how the energy lairs hold up.

## 11. Critique (2026-11-25)

An adversarial pass over this design the day it was written: every claim about the code checked against the source,
the model rerun, the rules tried for deadlocks, exploits and GameTest feasibility. What changed, and why:

| # | Problem found | Change | Sections |
|---|---|---|---|
| 1 | Nests could sit off the lair's ground. The ground is a circle of 28 (`FactionDirector.atLair`); the quadrant square out to 26 reached 36.8 at its corner, so a death or a fight in a far nest was not the lair's, and its kill energy and threat missed it | every nest cell on the ground (dx² + dz² ≤ 28²), min(\|dx\|, \|dz\|) ≥ 12; 34 origins per quadrant, all within 22, inside `MineSites`' lair margin; `QUADRANT_MAX` removed | 0, 3.2.2, 3.2.3, 3.2.4, 5.1, 7.1 |
| 2 | Spiders and creepers were specified, tested and switched off: more than asked, against the owner's "Its fine to keep a hard defence" | cut entirely (no dormant code, two config keys fewer, no question); listed under "left out" | 0, 3.1, 3.2.7, 3.3, 4.4, 6.1, 6.8, 6.10, 7.1, 9, 10 |
| 3 | Deadlocks and a drain: a block on the spawn point made every bought monster suffocate as it appeared; rule 3 or 5 stalled every rule below it while a cell was occupied or spawning was impossible (peaceful) | a free spawn cell before a spawn (none free: no spawn, no payment); "can't act, can't pay": a rule that cannot act this period gives way, one that cannot pay waits | 0, 3.1, 3.2, 3.3, 4.3, 7.1, 7.2 |
| 4 | A foreign block in the spire's column would have stopped the column ("grows on one already standing") | a block grows on or beside any solid block; six foreign blocks in a nest are stated as counterplay | 3.2 |
| 5 | The chunk-forcing reason was wrong ("the quadrants may never load"): they are loaded, one chunk from the forced hall, but tick no entities (level 32/33; vanilla ticks entities at 31), so a nest's monster would stand frozen | the reason corrected from `ChunkLevel` and NeoForge's `ForcedChunkManager`; the site search needs no forcing; turning the key off releases the ring; a live item for frozen monsters | 1, 3.2.3, 3.2.4, 6.9, 7.4 |
| 6 | The threat read `getLastHurtByMob` for 600 ticks; vanilla forgets the attacker after 100 (`LivingEntity.baseTick`) | read every 100-tick period, remembered by the lair in `lastThreatAt` for 600 | 4.2 |
| 7 | GameTests were not feasible as written: the director does not run outside a started campaign, a nest at the real cadence takes 4800 ticks, site searches read heightmaps without `skyAccess`, a launched raid would march 150 blocks over other platforms, and "60 blocks out" lands on another test | tests call the lair's step and pulse directly with `t` and `dayTime`; `skyAccess` for site tests; raids checked at launch and ended; far cases move the lair's centre | 1, 7.2 |
| 8 | Task order: T3's tests needed T2's helper while the order allowed T3 first; the regrowth asserted in T4 and T5 needed the repair rule that only T6 wired; every sabotage sat in T6 | strict order T1 to T9; repair first in T4's interim choice; each sabotage in the task it targets; tasks renamed from L to T (ROADMAP's L4 means the hero leaders) | 7.2, 7.3, 9 |
| 9 | Side effects of a lair plan missing: `DigOut`'s guard and `FCEvents.onExplosion` (a lair's repair queue that would only grow) | both listed; `onExplosion` skips lairs | 3.2.4 |
| 10 | Numbers: the model's raid party sent zombies first while 4.4 takes them in turn; the live raid days (1.42, 2.67, 3.92, 5.17, 6.42) were about 0.14 day late: the server log's raid lines, bracketed by the dumps' `gameTime`, give 1.28, 2.53, 3.78, 5.03 and 6.28, 30000 ticks after the lairs' building at day 0.025 and apart, the same clock as the model's; "a dip every 0.8 day" was read from polls 659 ticks apart that miss most dips; the day-7 snapshot moves by up to 4 with the hour the campaign starts | model rerun with the party by kind (tables updated); the live raid days corrected and the first raid stated as unchanged; home losses an assumption bracketed by "no loss"; a variant table; the live check compares parties and a rerun on the campaign's own start hour and measured losses | 0, 1, 5.2, 5.3, 7.4 |
| 11 | Smaller claims | the hall's walls are mixed mossy and plain cobblestone; the council launched on lair 5 at 3.94 and razed it by 4.04 (with 3 of its 8 away on the lair's own raid); a cobweb gives string, or itself to shears or silk touch (`loot_table/blocks/cobweb.json`), and every other lair block needs a pickaxe to drop (`Blocks`); the settlement clearance follows the configured radii, not a fixed 72; a lair beyond `lairDetectMax` never raids (far-apart starts); the builder-speed claim softened; added beyond the request (kill energy, threat, party mix, the off switch) said in 0 | 0, 1, 2.2, 3.2, 3.2.3, 3.2.5, 6.8 |
| 12 | Owner questions: spiders and creepers were already answered by the hard defence; the lair growing blocks with no hands is a new Hollow-only rule the owner has not seen | question 1 dropped (cut, item 2); a question on growth added | 10 |
| 13 | Two details a builder would trip on: GameTest 2 counted 11 column blocks after 20 steps, but the four ribs at height 8 grow first (9 + 8 + 3 = 20 leaves the column at 8), and 40 energy could not pay for 24 blocks; the pure choice could not tell "can't act" from "can't pay" without knowing what the world allows this period | GameTest 2 at 24 steps with 60 energy; four inputs added to the choice (spawn tick, peaceful, a free spawn cell, a growable cell per building) | 4.2, 7.2 |

Checked and found right: the lair code (`FactionDirector.lair` and `lairRaid`, `BaseBuilder.buildLair` and
`spawnLairMob`, `LairLayout`, `FCEvents.onJoin`/`onDeath`, `ChunkLoading`), the live distances (lair 5 to Tide 131.9
and Stoneguard 143.1, lair 6 to Ember 172.3, horizontal and 3D alike) and the column heights that reach them, the
110-a-day gathering, the plan block counts (54 and 53), `CampaignData.FORMAT` 4, the config section, `/fc join` taking
human factions only, peaceful's discard (`Mob.checkDespawn`), and the designer's model output.
