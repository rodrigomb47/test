# What a faction knows: the knowledge review of 2026-11-19

The owner's rule (CLAUDE.md, "player-like"): NPCs only know what scouts saw. The roll call
(`docs/design/missing_npcs.md`) applied it to deaths: a faction knows a death only near home or through a witness, and
knowledge travels home with someone. Its sections 13 and 14 left six leaks open. This review looked for every place
where a faction's AI still read the truth, and fixed them. This is the design as built.

**The rule.** A faction's AI (the director, the war council, recruitment, logistics, the jobs, a hero's decisions) may
only use what its own people know:

* what its members at home see (inside a home area, 64 blocks round a settlement, `campaign.homeAreaRadius`);
* what members away saw and carried home (the roll call's news);
* what its scouts reported (`FactionState.knowledge`, delivered at the station);
* its own stores, buildings and workplaces;
* what it decided itself (its raid's plan, its own soldiers' gear).

Operator commands (`/fc admin dump`, `/fc admin roll`), `sim.py`, debug lines and player-side presentation (chat for
players, the kill feed, boss bars) may show the truth. **Equal treatment**: the same rules hold for every faction,
whether or not a player belongs to it, and for the Hollow too, with the exceptions in section 9.

## 1. The war council judges a garrison by what its people saw (known leak 1)

**Before.** The council ranked the reported bases and decided whether to attack from the enemy's own records: its
roster (who is alive at the base, minus its live raid party), its average soldier gear (`soldierPower`, which the
enemy's own council wrote), its heroes' records and the hero entities' live health wherever they were, and a lair's
true list of monsters. The scouts' `count` was ignored. Live (`missing_npcs.md` 14): when Stoneguard's six soldiers and
Hinae left on a raid, Tide's and Grove's councils struck the emptied base within five seconds, and Tide destroyed it.

**Now.** A report of an enemy settlement or lair carries a **look** at its defence (`Report.look`, pure
`logic/campaign/Intel.Look`): the soldiers seen with a line of sight (a lair: the hostile monsters there), the share
of the yard in view, the mean visible power of the soldiers seen (`FactionDirector.power`: the armour worn and the
weapon held, which anyone can see), the heroes seen (`Report.heroes`, id and war power at full strength) and the time.
`campaign/BaseWatch.look` makes it:

* within 64 blocks (horizontally) of the base's centre;
* the yard: 25 look-out points over the walls' inside at eye height, each tested with a ray from the observer's eyes
  (blocks with a collision shape stop it; the walls are 5 high, so a scout outside sees little of a yard);
* the soldiers: the base's faction's fighters within the walls box plus 8 blocks, each with a line of sight;
* the base is reported only when something of it is in view (a wall's top, a yard point, its heart's place or one of
  its people); behind a hill it is not seen at all.

On one trip a scout keeps its best look at each base (the most of the yard in view, else the newer;
`Intel.better`), with every hero seen on any look. At home the newest report replaces the older one, keeping the
heroes seen before (a hero indoors is still there) and the losses seen since its look (`Report.mergeBase`).

The council's **estimate** (`Intel.estimate`):

* soldiers = the soldiers seen + the prior times the share of the yard **out of view**;
* as the look ages it drifts up toward the prior over `Intel.FORGET_TICKS` (48000, two days), never below what was
  seen;
* minus the soldiers of that settlement the faction **saw fall** since the look (below), each loss counting for less
  as it ages over the same two days (the base hires replacements; section 11);
* their power: the seen soldiers' visible power, the prior's for the rest; the heroes seen count by their war power,
  each sighting for less as it ages over two days (section 11).

The **prior** (what the council assumes where nobody looked): for a settlement, a garrison like its own (its ordinary
soldiers at home, not its heroes, and its own `soldierPower`; "assume they are as strong as we are"); for a lair, as grown as the campaign's days
allow (`lairBaseCap` plus one a day up to `lairMaxCap`: the rule every faction knows). A report saved before this
review has no look: the prior.

The decision is unchanged in form (the target with the least defence, then the nearest; attack at two thirds, a
counter-attack inside the interval only at 3 to 2), but every number is the estimate, and since section 11 two thirds
is counted in whole soldiers (`Intel.twoThirds`) and the 3 to 2 to the nearest one (`Intel.threeToTwo`). A garrison that **marches out
unseen is still believed at home**; one a scout saw gone is struck. The launch line says what it rests on: "against
about 4 defenders, as seen on day 3" or "a garrison none of ours has seen". `LAST_COUNCIL` (in `/fc admin dump`) adds
the truth for operators (`[truth: …]`).

**Losses seen at home** (`BaseWatch.enemyFell`, from `FactionNpc.die`): an enemy soldier (not a hero: it rises again)
that falls inside a faction's home area, or in sight of one of its members standing in it (the roll call's witness
rule), counts as one loss against that faction's report of the nearest settlement of the dead soldier's faction (its
sash shows the faction, not the base). This is what makes a counter-attack possible after a raid is beaten at the walls.

**Raid intel** (`FactionState.raidIntel`): while a raid is on, up to three raiders at a time look at the target each
director pass; the party keeps its best look, or the ruins. It reaches home with the raid's end: told at once when a
raider is home, else when the last one gets there (`RollCall.tell`); lost with a party nobody lived to report on.

**Pact offensives** (`FactionState.pactOffensives`): a pact member that launches against the sworn enemy tells its
allies its target, its party's power and its plan's end; an ally's council joins from that notice while it lasts. It
no longer reads the ally's live raid party or soldier power.

`FactionDirector.defenders`, `defencePower`, `heroDefence` and `heroWarPower` remain for operators (the dump, the
council line's truth) and the hero GameTests; the war council never calls them (`KnowledgeIsolationTest` scans its
methods).

## 2. A fall is known when someone sees the ruins

**Before.** A base destroyed by anyone was dropped from every council's targets at once, raiders far off on the march
turned to loot, raiding heroes turned home, the scouts' frontier grew at once, and a raid's faction said "ended in
victory" for a base someone else razed.

**Now.** `BaseState.destroyedBy` records who broke a heart (truth). A report stays standing until one of the faction's
people sees the ruins: a scout (or raider) with the heart's place in view reports it **fallen** (`Report.fallen`,
"… in ruins, its heart gone"), which replaces the standing report, for good: no base is ever rebuilt, so a later look
that sees the walls or the yard but not the heart's place does not raise the ruins again (section 11). The council never targets a fallen report and may
target a base that fell unseen (a raid sent to ruins is the player-like mistake). Raiders and raiding heroes learn the
fall only when they see the heart's place within 48 blocks (`BaseWatch.seesFall`; `GuardJob`, `HeroRaid`), then loot
what is left. "Has ended in victory" only when the faction's own people broke the heart; "its settlement was found in
ruins" when the party saw ruins it did not make. The scouts' `rivalKnown` reads only the faction's reports.
An escaping hero whose home falls reacts only if it is in that base's home area (`HeroBossEvents.baseLost`).

## 3. A raid's expected return is its plan (known leak 5)

**Before.** A raider's expected return, and the council's wait for news of the raid's end, were the raid's hard limit
(launch + 72000 ticks, three days). A raid wiped out unseen froze the faction's war for three days (live: Ember's raid
of day 1.3 held its council until day 4.3).

**Now.** At launch the council keeps its own plan (`FactionState.raidPlanTicks`,
`Whereabouts.raidPlanTicks`): the walk to the rally point, the muster's longest wait (6000, `RAID_MUSTER_TICKS`), the
assault and the loot (PROPOSED 6000, `RAID_ASSAULT_TICKS`: the heart takes 400 ticks of blows, plus the fight and
the walk to the storehouse) and the walk home from the target, at the roll call's 10 ticks a block. A 300-block raid is
planned at about 18000 ticks. Its raiders are expected back by launch + plan; the council waits for the end's news
until the plan plus `missingGraceTicks`, when its raiders go missing. The party on the spot keeps the same plan: it
gives the raid up and turns home at launch + plan - the walk home (`Whereabouts.raidTurnHomeAt`, "its time is up, the
party turns home"), never after the 72000 limit (section 11; before, it raided on to the limit while home reported its
live raiders missing, presumed them dead and replaced them). A raid launched before this review keeps the old bound
(`raidPlanTicks` 0: the limit).

## 4. A miner's trip is its shift (known leak 6)

**Before.** A miner at a branch face just past the 64-block edge crossed it dozens of times a day; each crossing set a
fresh half-day plan from that moment, so the expectation was noise.

**Now.**

* **The shift** (`MinerJob.shiftEnd`, `Whereabouts.shiftEnd`): fixed when the miner leaves the storehouse (in a mine or
  a cave): until the coming nightfall, at least half a day, as a day's remote work. Its trip's plan when seen leaving
  is what is left of the shift plus the walk home, the same expected return however often it crosses the edge. Before
  its first storehouse visit (an old save), a day's remote work from now.
* **The end of the shift**: a miner past its shift outside every home area goes back to the storehouse (a cave miner
  walks out along its route first), as it planned; inside the home area it works on.
* **The edge band** (`Whereabouts.edgeBand`, `sighted`): a member seen at home leaves only past the edge plus an
  eighth of the radius (8 blocks for 64); an away member is home again only that far inside (or anywhere inside when
  it was missing or presumed dead). A death's knowledge still uses the edge itself.

## 5. Retraining and the blacksmith see members at home (known leak 3)

Retraining considers only a worker the base sees at home: its record HERE and its entity in a home area
(`FactionDirector.seenAtHome`). A worker away is busy as far as the base knows (so a role is barren only when every
worker of it is seen idle at home). The blacksmith plans gear for the same crew (the 256-block crew range is gone): a
member away gets its worn tool replaced when it is home and seen.

## 6. Lines and news

* **The lookout** (known leak 4): a lookout hero's "spots N enemies" line is heard only when the hero stands in a
  home area; out on the work belt it still calls the guards near.
* **The cave survey** (known leak 2): stale as described: the survey reaches the faction's CAVE workplace only when
  the explorer hands it in at the station (a GameTest now holds it). The one line still said at the cave, "found no
  way into the cave", is now the explorer's own news (`RollCall.ownLine`).
* **An enemy hero's fall** ("X of the Y has fallen to Z"): carried by the killer's side's members who saw it (the
  witness rule), told at once only when one of them is at home; a player killer tells it at once. A summon's or a
  decoy's kill, or a pusher that walked off, no longer tells its faction from anywhere.
* **The station's raid line** shows the faction's view: "Raid under way on X: 5 soldiers set out on day 3, expected back
  by day 4; no news yet", not the party's roll on the spot.

## 7. Other work and engine fixes

* **Experience** picked up away from home is carried (`FactionNpc.carriedXp`, saved) and banked when the member is
  in a home area; one that dies drops it as orbs. The enchanter's hiring and the anvil's levels read only banked
  experience.
* **Fishing water** found on a far forage leg, and "no water within reach" after a round, reach the fishery record when
  the fisherman is back at the storehouse (`FishermanJob.foundAway`, `noWaterAway`, saved); a forager learns of water
  the director found only at home.
* **A hungry worker away** judges whether the storehouse has food by what it saw there at its last visit
  (`Job.storehouseFood`); at home it counts it.
* **The alarm**: an intruder inside the walls box is handed to the guards only when one of the base's people sees it
  (one of the four nearest within 24 blocks with a line of sight), stands within 3 blocks of it, was hurt by it in the
  last 100 ticks, or it is at the heart (within 4.5 blocks: its blows on the heart are heard across the base); one
  noticed stays raised for half a minute (600 ticks), so the guards do not lose it behind a corner or a door it shut.
  One hidden in a house or a tunnel that nobody noticed is not raised.
* **The Hollow's roll call**: a lair monster that falls on the lair's own ground (28 blocks) is missed at once; one
  that falls far off is missed only after it has stayed away 6000 ticks, so the lair does not replace it at once.
* **The pact** hands the allies the fallen faction's own reports of the conqueror (with when they were seen and by
  whom), not the conqueror's true list of bases.

## 8. Tests

* Unit: `IntelTest` (the prior, a full and a partial view, a garrison seen gone and the look's ageing, never below
  what was seen, losses, heroes, the better look, a lair's prior); `WhereaboutsTest` (the raid's plan and a wipe-out
  given up by it, the edge band, a miner's shift); `KnowledgeIsolationTest` (the war council's methods read no
  enemy roster, soldier power, raid party, hero record, lair list or true fall; `hero/` reads kill credit and the kill
  feed only for presentation lines marked `// truth:`).
* GameTests (`gametest/KnowledgeGameTests`, each its own batch): a garrison that leaves unseen is still believed at
  home and not raided, one the scout saw gone is raided; enemy soldiers seen falling at home lower the estimate and
  bring a counter-attack, one killed unseen does not; a base razed by another faction is still a target until a scout
  sees the ruins, and a raider sees a fall only near it; a raid wiped out unseen is taken as lost by its plan, its
  raiders missing then, not at the 3-day limit; a miner crossing the edge twice keeps its shift's expected return; a
  cave survey is the faction's only when handed in at the station; retraining and the blacksmith see only members at
  home; a worker away knows the storehouse's food as it last saw it; experience picked up away is carried home and
  dropped at death; the alarm needs someone to see the intruder;
  the lookout's line is heard only at home; a lair misses a monster that fell far off only later; an ally is told of
  an offensive as it is launched. Updated: the council's wait (`MissingNpcGameTests`), the raider's expected return,
  the pact's reports (`FCGameTests`), mined-ore experience at a base, the hero raid draft (a report with a look).

The review of this review (section 11) added seven GameTests and seven unit tests (a party that turns home by its plan,
its roll deaf to home's belief, ruins that stay ruins, losses and hero sightings that fade, two thirds in whole
soldiers, the 3 to 2 to the nearest one, the party rule, and a pin: a scout counts no soldier behind a wall).

Fifteen sabotages, each caught by a test: the council reading the enemy's true defenders, the 3-day wait, a miner's
half-day plan at each crossing, no edge band, retraining or the blacksmith judging members away, ruins raided, every
enemy death counted as seen, experience banked anywhere, the alarm raising anyone, the lookout's line anywhere, a lair
missing a far death at once, the pact handing over the true bases, the yard out of view ignored, the raid's plan made
its limit.

## 9. Exceptions kept, and why

* **The pact** is diplomacy between the factions' leaders, announced to the world: it is sworn, and raids between the
  new allies called off, the moment a faction is conquered, and allies stop fighting at once. What the allies learn of
  the conqueror is only what the fallen faction's scouts had seen. The owner may want the news of a conquest to travel
  (each faction joining when its people learn of it); that would need per-member joining and a recall carried to
  parties in the field.
* **The Hollow senses settlements within 300 blocks** of a lair (its raid target): its monsters never leave the lair to
  scout, so without this sense lairs would never raid. Documented as an exception for the owner's decision.
* **A raid party's roll on the spot** (`FactionState.raiders`) and the raid's end on the spot read the truth (who is
  left to carry the raid on); the council at home hears of them only through its people (`missing_npcs.md` 6).
* **The HeroState health and level snapshot** stays for the engine (revival) and operators; no council reads it.
* **What a look counts**: a soldier's visible power and a hero's war power at full strength are read from the entity in
  sight (what a player sees: armour, weapon, the hero's bar and level).

## 10. Left open

* **Work-site choice reads other factions' records**: mine sites, fishing stands, groves and the woodcutters' tree
  claims keep clear of every base and lair, including ones never reported, of other factions' mine corridors and of
  other factions' woodcutters' claims (`BaseZones`, `WorkSafety`, `MineSites`, `ForestScan.TreeClaims`). Mostly
  invisible (foreign land is far from a work belt) and it touches every worker's site search; splitting the physical
  rule (never break a built block in front of you) from knowledge is a larger change.
* **A cave miner's changes** (ores marked, faces, progress) and a mine's worked-out or resting state are written to the
  workplace at the face, so the director's needs react a trip early; the mine's lines are said from the face.
* **The woodcutter's scaffold columns and far replant entries** are written at the tree and a later woodcutter sweeps
  far columns it cannot see.
* **A miner reads the storehouse's pickaxes** for a resting mine before walking home (cheap and rare). A hungry worker
  away now judges the storehouse's food by what it saw at its last visit (`Job.storehouseFood`).
* **Heroes on a raid** choose their goal with a home hero found within 64 blocks and archers within 48 without a line
  of sight, and count party members anywhere; a hero's path keeps a berth round every hostile base, reported or not
  (`HeroNodeEvaluator`); an escaping hero's refuge is chosen among its faction's bases that truly stand. Today heroes
  join raids only by operator command; these belong with the leaders' raids (L4).
* **`/fc hero info`** shows a member player its hero's true place and state (player-side, kept).

## 11. The review of the knowledge review (2026-11-19)

An adversarial review re-audited every entity query and every read of another faction's state in the faction-side code
(`campaign/`, `work/`, `npc/`, `combat/`, `hero/`, `info/`, `achieve/`) and checked the fixes above for behaviour
problems. The audit found no new leak beyond section 10; nearby-entity queries are senses (a guard sees an intruder, a
scout sees a monster). Five behaviour problems were found, each shown by a GameTest that failed before its fix
(`KnowledgeGameTests`, the last six tests) and by unit tests (`IntelTest`, `WhereaboutsTest`):

1. **The party raided on past the plan home expected it by** (`aRaidPartyTurnsHomeByItsPlanAndHomeNeverGivesUpALiveRaid`).
   Section 3 shortened home's expectation to the raid's plan but left the party on the spot raiding until the 3-day
   limit: a raid that stalled at the walls had its live raiders reported missing at the plan + 2400 ticks and presumed
   dead (and replaced) at + 12000 more, while still fighting. Worse, the party's own roll dropped a raider "its faction
   no longer counts", so home's belief pulled presumed-dead raiders off the party where they stood and sent them home
   (`homesBeliefDoesNotPullARaiderOffItsParty`). Now the party turns home at launch + plan - the walk home
   (`Whereabouts.raidTurnHomeAt`), and its roll drops only a raider whose death a member saw.
2. **Ruins seen were rebuilt by a later look** (`ruinsSeenStayRuinsWhenOnlyTheYardIsSeenAgain`). `Report.mergeBase` let a
   newer standing look replace a fallen report, and on one trip (or in the raid party's look) the later look's `fallen`
   won. A scout outside the walls sees the walls but not the heart's place (the walls are 5 high), so ruins once seen
   became a standing base again and the council sent raid after raid to them. Ruins now stay ruins.
3. **Losses never faded** (`soldiersSeenFallingLongAgoAreReplacedInTheEstimate`): eight of nine enemy soldiers seen
   falling at our walls three days ago still made the garrison one soldier strong (the look drifts to the prior, the
   losses stayed subtracted), and the council struck a garrison long rebuilt. Each loss now counts for less as it ages
   over `Intel.FORGET_TICKS` from the last one seen (`Intel.weight`).
4. **A hero seen once was counted for ever** (`aHeroSeenLongAgoCountsForLessAsTheSightingAges`): `Report.mergeBase`
   kept every hero seen at full strength, so a council never struck a base where a strong hero had once been seen, even
   one passing through. A report now keeps each hero's newest sighting with its time (`id=power@time`; an old
   `id=power` entry is dated by its report's look) and the council counts each by `Intel.heroPower`, fading over
   `Intel.FORGET_TICKS`.
5. **The council's rule waited at 10 or 11 soldiers where it attacked at 9** (`tenSoldiersAttackAGarrisonLikeTheirOwnWhereNineDo`).
   Against a garrison nobody has seen (the prior, like its own), the party (every soldier beyond a third, the third
   rounded up) was compared with two thirds of the whole garrison: it matched only when 3 divided the garrison, and
   then at exact equality, so the float rounding of `soldierPower` (1.35 for leather and a bare hand) could stop it
   too. Two thirds is now counted in whole soldiers (`Intel.twoThirds`: the floor, the same rounding the council uses
   for the third it keeps), so a council attacks a garrison like its own from 7 soldiers whatever their gear, never at
   6; the counter-attack's 3 to 2 is counted to the nearest whole soldier (`Intel.threeToTwo`), and strengths within a
   hundredth of a soldier are equal (`Intel.TIE`). The prior counts the council's ordinary soldiers, not its heroes
   (heroes nobody saw are not assumed, and its own never join the party). The party rule itself moved to
   `Intel.partySize` unchanged.

Sabotages, each caught: the party raiding to the limit (GameTest), the turn-home time as the limit (unit), home's
belief on the party's roll (GameTest), a standing look rebuilding ruins (GameTest), losses that never fade (unit),
hero sightings that never fade (unit), an old hero entry dated by the newer report (GameTest), two thirds of a
fractional head (unit), a counter-attack at exact 3 to 2 (unit and GameTest), a look counting soldiers without a line
of sight (GameTest `aScoutCountsOnlySoldiersInSight`, the pin for "scouts that report what is behind a wall": two
soldiers behind a 2-high wall are not counted, nor the yard behind it). A test base's cleanup now drops the news its
NPCs carried (a raid's end told to raiders still away).

Checked and left as they are:

* **The cave survey** is written only in `CaveExplorerJob.report` at the station; an explorer that dies on the way home
  loses it, as a scout loses its reports (the rule working). The same for a fisherman's water found away, the explorer's
  "no way in" line and the raid party's look.
* **The balance risk** of section 1's prior remains to be measured in a campaign (the council now attacks an unseen
  settlement from 7 soldiers at home; before the knowledge review it read the enemy's true roster).

Found and left open (not fixed here, low severity):

* **A scout's pending reports are capped at 24** (`ScoutJob.addPending`, pre-existing): on a long trip full of hostile
  groups or exposed ore, a later look at an enemy base is dropped, and the base is not reported at all.
* **A fisherman that finds water on a far leg walks home to the storehouse before it fishes there** (section 7), a
  round trip a player would not make; its own knowledge and the fishery record are one field.
* **The raiders' kills at the target do not lower the estimate**: only enemy soldiers seen falling at home count as
  losses (`BaseWatch.enemyFell`); a raid beaten back after killing half the garrison brings home a look, but not the
  kills it saw after that look.
* **Losses are kept as a count and the last one's time**: a loss seen before a newer look but merged after it can count
  twice, and older losses fade with the newest one.
* **Two more reads of the truth, not listed in sections 9 and 10**: a Hollow raid ends the moment its target is destroyed,
  whoever razed it (`FactionDirector.lairRaid`, with the Hollow's 300-block sense); a raiding hero heading home reads its
  own base's fall from afar (`HeroRaid.home`), as an escaping hero's refuge does.
* **"Never at 6" holds without a home hero**: the party still sizes off the garrison with its heroes by heads
  (`Intel.partySize(soldiers.size(), ...)`), the prior off the soldiers alone, so six soldiers and a hero at home send
  four against a prior of six, and go.
