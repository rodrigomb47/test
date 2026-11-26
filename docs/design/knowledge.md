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

**Now (since 2026-11-26): focused reconnaissance.** The design and its as-built notes are `docs/design/recon.md`
(tasks R1 to R12); where it and the code differ, the code is the truth. The owner's rule (2026-11-25): "Only estimate
enemy defence when the scout or other related role makes a report of what he saw while focused on this particilar
task". In short:

* **Orders.** Half a day before its raid interval ends (or urgently, once the fight at home is over, after an enemy
  struck our walls) the council orders a recon of a target it has never judged, whose last recon is stale or blind
  (`ReconOrders`, at most one ordinary and one urgent order a faction). A scout takes the order only at home, at the
  station or the storehouse; a second scout is hired for an order nobody took in 6000 ticks.
* **The watch.** The scout walks to the target, round it outside a standoff of 61 blocks (a lair: 46), finds a column
  in the band out to 72 blocks (96 with a vanilla spyglass it carries), climbs a dirt pillar of up to 20 blocks block by
  block, looking after each, stops two blocks above its first good look and watches for a minute, counting each soldier
  once by its face (UUID). A look is **good** with 8 of the yard's 25 points and its middle in view; a worse one is a
  **blind** report ("could not see inside"), never "about 0". A lair is watched from the ground on a gap's axis. It
  carries the report home apart from its others and tells it first at the station (`ReconTrip`, `ScoutJob`).
* **The estimate** (`Intel.estimate`): only a good recon no older than half a day; the faces counted, their mean
  visible power, the heroes seen in that watch. No prior, no drift, no losses, no passing look's count (a passing look
  tells only where a base is and that it stands), no raid party's look (a raid brings home only the ruins it saw).
* **The plan** (`Reserve.plan`): the count is a floor (the owner's answer: "they can assume that what they saw is not
  the enemy mainforce"). The council plans against the count plus a reserve, the larger of half again the count and the
  soldiers of that faction its own people saw away from that base in the last half day and did not see fall
  (`FactionState.sightings`), with the heroes the watch saw or its people know of; a lair and a counter-attack get no
  share. It judges every target it may raid and chooses one its party would go against now first, then the least
  plan in its own soldiers, then the nearest (`Reserve.RANK`, recon.md 17), and goes at two thirds of it.
* **Strikes.** Enemy soldiers seen falling at our walls lower nothing: they record a strike (`FactionState.struckBy`),
  which orders an urgent recon of the striker's nearest reported settlement. Inside the raid interval the council
  judges only the settlements of factions that struck us, at 3 to 2, and only on a recon made since the strike.
* **Lines.** "Launching a raid with 3 soldiers on the Grove Covenant settlement reported at 400, 67, 16, against about 4
  defenders and Hinae Shirasuzu (scout Edrin Cole, day 5) and a reserve of 2 (half again the 4 counted)"; while it waits,
  "waiting on ...: 3 soldiers to send, 6 needed against ..." or "waiting for a scout's report on ... (never watched)".

The model that stood from this review to 2026-11-26 is kept here as history, since sections 8, 11 and 12 refer to it.
A report carried a **look** (`Report.look`, `Intel.Look`): the soldiers seen with a line of sight, the share of the yard
in view, their mean power, the heroes seen; any passing scout's or raider's look within 64 blocks counted. The
**estimate** was the soldiers seen plus a **prior** (a garrison like the council's own) times the share of the yard out
of view, drifting up toward the prior over two days, minus the enemy soldiers seen falling at home (**losses**, fading
over two days), with the heroes seen fading the same way; a lair's prior was as grown as the campaign's days allowed.
The raid party's look came home with the raid's end (`FactionState.raidIntel`). The knowledge live check (section 12)
found 23 of 26 looks at settlements saw none of the yard, so most estimates were the prior; focused reconnaissance
replaced the whole model (recon.md 7 lists what was deleted).

**Pact offensives** (removed on 2026-11-25 with the pact itself, by the owner's word: "Lets remove the pacts thing"):
a pact member that launched against the sworn enemy told its allies its target, its party's power and its plan's end,
and an ally's council joined from that notice. There are no pacts now, so no council joins another's offensive.

`FactionDirector.defenders`, `defencePower`, `heroDefence` and `heroWarPower` remain for operators (the dump, the
council line's truth) and the hero GameTests; the war council never calls them (`KnowledgeIsolationTest` scans its
methods, and since task R5 checks that of a base's report it reads only the focused recon). The operators' view of
the recons, orders, strikes, sightings and the council's last plan is `/fc admin dump` (task R6), with the truth of a
watched yard at each watch's end in the server log (`FC_RECON`, read by `tools/rolltrack.py` and `tools/warcheck.py`).

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
what is left, and the party keeps the ruins it saw for home (`FactionDirector.partySawRuins`; before the live check of
2026-11-25 only a party that broke the heart itself kept them, so a party that found another faction's ruins came home
with a look of the yard and its council kept weighing them). "Has ended in victory" only when the faction's own people
broke the heart; "its settlement was found in ruins" when the party saw ruins it did not make. The scouts' `rivalKnown` reads only the faction's reports.
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
* **The pact** handed the allies the fallen faction's own reports of the conqueror (with when they were seen and by
  whom), not the conqueror's true list of bases. Removed with the pact on 2026-11-25: a fallen faction's reports go to
  nobody.

## 8. Tests

Since focused reconnaissance (2026-11-26) the war council's tests are recon.md 10's: unit `ReconTest`, `IntelTest`
(rewritten: the recon's estimate, good, blind and stale, the kept recon's merge and back-off), `ReserveTest` (the plan,
its lines, the target by its plan), `ReconOpticTest` and the sightings ledger; GameTests `KnowledgeGameTests` (the
council's switch: no launch on a passing look, a raid party's look or losses; the reserve; the counter-attack on a recon
since the strike; a weaker bystander does not block it; a lair with no reserve; an operator's raid), `ReconOrderGameTests`,
`ReconVantageGameTests`, `ReconTripGameTests`, `ReconOpticGameTests`, `SightingsGameTests` and `ReconOperatorsGameTests`
(the dump's recons, orders, strikes and plan, the station's war lines with no truth in them, the truth of a watched box).
The list below is this review's, as it was built; its estimate items (the prior, partial views, ageing, losses, heroes
fading, the better look, the lair's prior) were deleted or rewritten with task R5 (recon.md 7).

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
  the pact's reports (`FCGameTests`), mined-ore experience at a base, the hero raid draft (a report with a look). (The
  two pact tests went with the pact on 2026-11-25; `EliminationGameTests.elimConquestLeavesTheSurvivorsHostile` pins
  that the survivors of a conquest stay enemies and are handed nothing.)

The review of this review (section 11) added seven GameTests and seven unit tests (a party that turns home by its plan,
its roll deaf to home's belief, ruins that stay ruins, losses and hero sightings that fade, two thirds in whole
soldiers, the 3 to 2 to the nearest one, the party rule, and a pin: a scout counts no soldier behind a wall).

Fifteen sabotages, each caught by a test: the council reading the enemy's true defenders, the 3-day wait, a miner's
half-day plan at each crossing, no edge band, retraining or the blacksmith judging members away, ruins raided, every
enemy death counted as seen, experience banked anywhere, the alarm raising anyone, the lookout's line anywhere, a lair
missing a far death at once, the pact handing over the true bases, the yard out of view ignored, the raid's plan made
its limit.

## 9. Exceptions kept, and why

* **The pact** (no longer an exception: the owner removed pacts on 2026-11-25) was diplomacy between the factions'
  leaders, sworn the moment a faction was conquered. Now no faction is another's ally, and a faction whose last base
  falls dies with all its people (`CampaignManager.eliminate`); the "X has been eliminated!" line is told to everyone,
  as before, and is player-side presentation, never read by a council.
* **The Hollow senses settlements within 300 blocks** of a lair (its raid target): its monsters never leave the lair to
  scout, so without this sense lairs would never raid. Documented as an exception for the owner's decision; the owner's
  answer of 2026-11-25 moves it to the lair-energy design (`docs/design/lair_energy.md`: a lair gathers energy and may
  spend it on a larger detection range). The Hollow estimates no defence, so focused reconnaissance does not touch it.
* **A raid party's roll on the spot** (`FactionState.raiders`) and the raid's end on the spot read the truth (who is
  left to carry the raid on); the council at home hears of them only through its people (`missing_npcs.md` 6).
* **The HeroState health and level snapshot** stays for the engine (revival) and operators; no council reads it.
* **What a watch counts**: a soldier's visible power and a hero's war power at full strength are read from the entity in
  sight (what a player sees: armour, weapon, the hero's bar and level). Since focused reconnaissance only a recon watch
  counts anyone (recon.md 3.6); a passing look counts nobody, and the faces a watch counts are kept by UUID, as a player
  keeps track of each soldier by where it walks and what it wears.

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
* **Heroes on a raid** choose their goal with archers within 48 without a line of sight, and count party members
  anywhere (since task R9, 2026-11-26, a defending hero is known to be home only from the recon the raid was launched on,
  `FactionState.raidReconHeroes`, or seen inside the walls in the raider's own line of sight: recon.md 5.8); a hero's path keeps a berth round every hostile base, reported or not
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
3. (Superseded 2026-11-26: losses seen at home no longer count at all; they record a strike that orders a recon,
   recon.md 6.) **Losses never faded** (`soldiersSeenFallingLongAgoAreReplacedInTheEstimate`): eight of nine enemy soldiers seen
   falling at our walls three days ago still made the garrison one soldier strong (the look drifts to the prior, the
   losses stayed subtracted), and the council struck a garrison long rebuilt. Each loss now counts for less as it ages
   over `Intel.FORGET_TICKS` from the last one seen (`Intel.weight`).
4. (Superseded 2026-11-26: a hero counts only in the watch that saw her, or as a sighting for half a day, recon.md 5.3
   and 5.5.) **A hero seen once was counted for ever** (`aHeroSeenLongAgoCountsForLessAsTheSightingAges`): `Report.mergeBase`
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
  groups or exposed ore, a later look at an enemy base is dropped, and the base is not reported at all. (Since focused
  reconnaissance a recon's report is held apart and never capped, recon.md 4; a passing look, which now tells only where
  a base is, is still capped with the rest.)
* **A fisherman that finds water on a far leg walks home to the storehouse before it fishes there** (section 7), a
  round trip a player would not make; its own knowledge and the fishery record are one field.
* **The raiders' kills at the target do not lower the estimate**: (moot since 2026-11-26: no kill or loss lowers an
  estimate; a raid brings home only the ruins it saw, and the next judgement waits for a fresh recon.)
* **Losses are kept as a count and the last one's time**: (moot since 2026-11-26: losses are gone, recon.md 7.)
* **Two more reads of the truth, not listed in sections 9 and 10**: a Hollow raid ends the moment its target is destroyed,
  whoever razed it (`FactionDirector.lairRaid`, with the Hollow's 300-block sense); a raiding hero heading home reads its
  own base's fall from afar (`HeroRaid.home`), as an escaping hero's refuge does.
* **"Never at 6" holds without a home hero**: the party still sizes off the garrison with its heroes by heads
  (`Intel.partySize(soldiers.size(), ...)`), the prior off the soldiers alone, so six soldiers and a hero at home send
  four against a prior of six, and go. (Since 2026-11-26 there is no prior: a party goes against a recon's count plus
  its reserve, and with the reserve of half again two thirds of the plan is the count head for head, so a party of four
  goes against four counted and waits against five; the party's heads rule is unchanged.)

## 12. The live check (2026-11-19)

(History: the live check of the estimate model of this review, which focused reconnaissance replaced on 2026-11-26,
`docs/design/recon.md`. Its launch table, its "prior" and "losses" bases and its findings on the war's balance are the
evidence that design rests on, 5.4's replay among them; `tools/warcheck.py` now reads the recon's records too.)

A campaign on a dedicated server with the jar of the review above (seed 20261107, `fc start 256`, Natsuho Komezaki with
the Ember Legion and Hinae Shirasuzu with Stoneguard, no operator command), followed every 6 s by `tools/rolltrack.py`,
whose war record (`war.log`, `war.jsonl`, `chat.log`) sets each council's line, each launch and the report its estimate
rested on beside the truth; `tools/warcheck.py` reads it (`docs/HANDOFF.md` 4).

**The restart.** A container restart killed the server at day 5.18. The campaign data had last been saved at day 4.29,
so the campaign went on from there to day 8.34 (the war record to day 7.63, where `rolltrack.py` failed on Hinae
rising with no faction, below): the first run (days 0 to 5.18, unbroken) is the clean run, used for
rates, recruitment, recoveries and tick time; the continuation (4.29 to 8.34) replayed 4.29 to 5.18 from the same
save, and its launches of days 4.61 and 4.70 are the clean run's 4.59 and 4.70 again (counted once below). The hard kill
left the region and entity files ahead of the saved data: five members away (Natsuho at her fishing spot, a miner, two
scouts and a cave explorer) were in no saved chunk, vanished at the load and went missing, then presumed dead
(Natsuho "missing and presumed lost", back at home after her 600 s). These are the restart's, not the mod's.

**Launches.** 12 on the main line (the first run to day 4.29, then the continuation) by day 8.34, none after day
7.11 (every council's interval was still running), and one more in the lost branch (the first run's days 4.29 to 5.18),
all by the councils: 11 on settlements and 2 on Hollow lairs. The clean run launched 6
in 5.18 days (the first on day 3.10, as the raid interval allows); the roll call's live check, whose council read the
enemy's own roster, launched 4 by itself in 6.94 days (with 6 operator raids drawing soldiers away). War comes more
often, not less. It rarely works: of the 11 settlement raids, 1 took its target (the Tide Alliance razed Stoneguard on
day 7.21; Grove's party struck the same garrison in the same hour and lost 5 of 7), 1 was beaten back, 8 lost every
raider who set out, and 1 set out with one raider of its three (the frozen archers below), who died. Both lair raids
won without a loss (the Hollow was
eliminated on day 7.19, Stoneguard on day 7.21; the Ember Legion and the Grove Covenant then swore their pact against
the Tide Alliance).

What each settlement raid's estimate rested on (`warcheck.py`'s launch table; "the truth" is the soldiers in the
target's walls at the launch, heroes apart):

| basis | raids | the council's "about N defenders" | the truth | outcome |
|---|---|---|---|---|
| the prior (a look that saw none of the yard) | 6 | 5, 6, 10, 10, 10, 11 | 3, 5 + a hero, 3 + a hero, 9 + a hero, 2 + a hero, 4 + a hero | 5 lost every raider, 1 won |
| a look into the yard | 2 | 5 (52% of the yard seen), 5 (4% seen) | 4, 6 | both lost every raider |
| enemy soldiers seen falling at home | 3 | 0, 0, 8 | 3 + Hinae, 4, 4 + Hinae | 2 lost every raider, 1 beaten back |

Of 26 looks at settlements in the clean run, 23 saw none of the yard (a scout outside 5-high walls sees the walls);
the mean share in view was 4%. Lairs, without walls, were seen at 26%. So most estimates are the prior, and the prior
(a garrison like the council's own) overestimated the garrisons it was used on; the parties lost anyway. The estimates
built on losses seen at home were the worst: the council's own garrison less every enemy raider seen falling at its
walls gives "about 0" for a base whose raids failed, where every council keeps a third of its soldiers (at least
`homeDefenders`, 3) at home.

**The leak test** (known leak 1: in the roll call's live check two councils struck Stoneguard's emptied base within
five seconds of its garrison leaving). For every departure of 3 or more soldiers, every other council's estimate of
that base at the polls around it: none moved without a report of its own. Ember's estimate of Grove dropped from 14.4 to
0.1 at day 5.59 because Ember saw four of Grove's raiders fall at its own walls (a loss seen at home); its
counter-attack followed. The Tide Alliance and the Grove Covenant launched on Stoneguard in one poll at day 7.07 because
both raid intervals ran out then (each launched on day 5.56, one interval earlier): Grove's council had said "wait" from
day 6.93 to 7.04 while its estimate rose from 10.7 to 14.1; Tide's report was a look from day 5.83 that saw none of the
yard; Stoneguard held 4 soldiers and Hinae (its own party of three had been wiped at Grove by day 6.80). No council
struck an emptied base it had not seen.

**Raids' ends** (known leak 5). Every party wiped out unseen went missing at its launch plus 0.70 to 0.73 day (the
plan, about 15000 ticks for 160 to 190 blocks, plus the 2400-tick grace) and was presumed dead half a day later, not
after three days. Every member reported missing was dead but for the five the restart lost; no raider went missing while
its raid was on. The line "its time is up, the party turns home" was said once (Ember's counter-attack, day 6.16), by a
party that had never left home (below): the turn-home in the field was not seen live.

**Surveys and far workers** (known leaks 2 and 6). Every cave survey line (57 in the clean run, 39 in the continuation)
came while the explorer was home or bound for the station; the one line the polls could not place was checked in the
server log (the explorer walked in, handed in and went out between two polls). No miner's expected return changed while
it was away; crossings of the home edge no longer moved it. The Tide Alliance's miner Tamsin Kerr died unseen at a
branch
face 99 blocks out on day 5.40, went missing at 5.91 (its shift and the grace) and was presumed dead at 6.38.

**Found and fixed: archers frozen on a far target.** On day 4.78 Hinae chased Ember's raiders home to Ember's walls and
walked back to Stoneguard. Two of Ember's ranged soldiers (an archer and a crossbowman) kept her as their target, then
an enemy scout that had passed by, for 1.9 days: they stood at their hold spots in `NpcRangedAttackGoal` with targets
77 to 275 blocks away, their guard posts forgotten; drafted into Ember's counter-attack of day 5.59, they never set out
(one raider went, alone, and died at Grove). `Pursuit.approachRanged` kept a ranged fighter's target engaged at any
distance while it waited out of reach, so the stale-target rule (no blows for 10 s) never fired; a melee fighter's hold
already watched only within 24 blocks. Both now share `Pursuit.WATCH` (24): a target that walked off out of reach goes
stale and is let go (`CombatReachGameTests.anArcherLetsGoOfATargetThatWentFarOutOfReach`; the sabotage that keeps it
engaged at any distance fails it). The same method serves a hero's throw.

**Found and fixed: a won raid brought home a standing base.** The Tide Alliance's party broke Stoneguard's heart on day
7.21 and came home "in victory", and the Ember Legion's broke the Hollow's last lair on day 7.19, but the report each
party brought home was a look from before the fall (Tide's: day 7.19, 60% of the yard, Hinae seen; Ember's: day 7.16):
the director looks at the target through at most three raiders every 100 ticks, keeps the look that saw most of the
yard, and none of those looks had the heart's place in view after the fall (the breaker had gone on to the storehouse).
Tide's council went on weighing Stoneguard's ruins as its target (about 17 to 24 defenders, the prior and Hinae, "wait")
to day 8.33; in time it would have raided them. The raider that broke the heart saw it go: a party whose own raider
broke its target's heart now keeps the ruins (`BaseWatch.ruins`) and brings them home with the raid's end
(`KnowledgeGameTests.aPartyThatBreaksTheHeartBringsTheRuinsHome`; the sabotage that skips it fails it).

**Regressions looked for.** Recruitment replaced losses ("garrison growth", "home defence", "no X left"; 68 recruits in
the clean run) and the populations grew from 11 to 14 per faction to 21 to 25 by day 5. Stuck recoveries (`sim.py`'s
workers' mean):
11.1 at day 5.1 in the clean run, against 11 to 13 at days 5 to 7 in the roll call's live check: level; 15.4 over the
4 days after the restart (the count starts again at a load), most of it two Grove miners at a far branch mine (141 and
180, bursts of "going back and forth" by their mine's entrance, never stuck for good). Tick time 5.8 to 8.6 ms a tick
per day (earlier
6.6 on day 1 and 9 to 12 on days 5 to 7 with the art workflow's jobs). Tools: `sim.py` and `rolltrack.py` failed on a
hero with no faction (Hinae once Stoneguard fell; the dump leaves `owner` out); both read it as absent now.

**The verification campaign (2026-11-25).** A fresh world on a new server folder (same seed and config, same layout),
the jar of commit 7b8db3e (both fixes above), Natsuho with the Ember Legion and Hinae with Stoneguard, followed the same
way (`warcheck.py`): no operator command to day 5.19, then one forced raid (Stoneguard on Ember with Hinae, to draw the
archers onto a hero), then on to day 7.25; the server was stopped cleanly. The launches:

| day | launcher | target | basis | "about N" | the truth | out | dead |
|---|---|---|---|---|---|---|---|
| 3.94 | Tide Alliance | Hollow lair | a look (lair) | 7 | 8 monsters | 3 | 0, lair razed |
| 4.10 | Ember Legion | Stoneguard | the prior (blind look) | 7 | 3 + Hinae | 4 | 4 |
| 4.34 | Stoneguard | Ember Legion | losses seen at home | 1 | 5 | 3 | 3 |
| 5.20 | Stoneguard (forced) | Ember Legion | losses seen at home | 1 | 6 + Natsuho | 3 + Hinae | all |
| 5.44 | Tide Alliance | Stoneguard | the prior (blind look) | 7 | 0 | 4 | 0, Stoneguard razed |
| 5.47 | Grove Covenant | Ember Legion | the prior (blind look) | 7 | 9 + Natsuho | 4 | 4 |

* **The archers.** Ten fighters had a target more than 24 blocks off at some poll (24 to 43 blocks). Seven had let it
  go by the next poll; the other three (an Ember crossbowman, 33 blocks off, and two axe fighters) were on one enemy
  that came back to the walls' foot, about 24 blocks from them, where the crossbowman held it two more polls (the edge
  of the watch; positions rounded to the block) and then took another target. No ranged soldier stood frozen on a far
  target (the first run's archers kept theirs 1.9 days); every party set out whole (out = party). The original case (a
  hero walking home out of reach) did not come back: Hinae, forced on Ember, fell there at her escape (13%). The fix
  rests on its GameTest and the sabotage that fails it.
* **Won raids.** Both victories brought the ruins home: "REPORT hollow ...: ruins (by the raid party)" (day 4.10) and
  "REPORT stoneguard ...: ruins (by the raid party)" (day 5.66), and the Tide Alliance's council weighed neither again.
* **The rate.** Five launches by the councils in 7.25 days, three of them by day 5.19 (the first run: six by 5.18; the
  roll call's live check: four in 6.94 days), the first at day 3.94. None after day 5.55: Stoneguard's fall made the
  Ember Legion and the Grove Covenant swear their pact against the Tide Alliance, whose base neither had found (a
  sworn council weighs only the conqueror's bases, not even a lair), and the Tide Alliance knew nothing but ruins.
* **The leak test.** Four garrison departures of 3 or more; no other council's estimate moved without a report of its
  own. When Stoneguard's garrison and Hinae left on the forced raid (5.22), the Ember Legion's estimate of Stoneguard
  fell from 11.4 to 5.9 only as it saw Stoneguard's raiders fall at its own walls (losses 1, then 3); the Tide
  Alliance's stayed 12.3 (its look of day 4.40). The Tide Alliance struck the emptied base on day 5.44 because its raid
  interval ran out, on that look ("about 7"), and razed it.
* **The roll, surveys and far miners.** 38 roll changes to missing or presumed dead, every one a dead member; 70
  survey lines, all
  told with the explorer at home or bound for the station; no miner's expected return changed while it was away.
* **Regressions looked for.** Recruitment: 61 recruits by day 5.19 (the first run: 68), 94 by 7.25; populations from
  10 to 11 per faction to 17 to 21 by day 5. Tick time 6.6, 6.6, 8.6, 7.8, 8.1, 8.8, 8.6 ms on days 1 to 7. Stuck
  recoveries (workers' mean): 2.2, 2.6, 5.5, 9.5, 12.7 on days 1 to 5 (the first run 11.1 at day 5.1), then 19.0 and
  25.0 on days 6 and 7, against a median of 2: two Grove miners (198 and 194 at day 7.25) whose branch tunnels at
  y=14 to 15 run 120 to 170 blocks from home, the faces reached a few blocks a poll ("Walking to the tunnel face" for
  a quarter day at a time), a Tide hauler (122) and a Grove woodcutter (116); the same Grove mine led the first run's
  continuation (180). Not of this review: in the roll call's live check, before it, Grove's miner at the far face of
  the same mine #10 (570, 15, 323) had 169 by day 6.6 (Grove was eliminated before that campaign's end, so its final
  mean of 9.1 leaves him out), while miners in branch tunnels as long elsewhere (Ember's and Tide's, 2483 blocks) stayed
  under 30 in all three campaigns.

Left open, with the evidence above, for the owner:

* **The war's balance**: raids rarely work. Of the 15 settlement raids in both campaigns (the forced one apart), 2
  took their target, the Tide Alliance on Stoneguard both times (once in the same hour as Grove's party, once on a
  garrison already lost at Ember); the rest lost every raider or were beaten back. Most estimates rest on the prior
  (the blind look: a scout outside 5-high walls sees
  the walls), and the parties of two thirds meet walls, archers, the alarm and often a hero. The party rule, the prior
  and the defenders' edge are the owner's call.
* **The loss model (the estimates' calibration)**: the estimates built on losses seen at home were the worst in both
  campaigns: "about 0" against 3 + Hinae, 4 and 4 + Hinae (the first run), "about 1" against 5 and 6 + Natsuho (the
  verification): the council's own garrison less every enemy raider seen falling at its walls, where every council
  keeps a third of its soldiers (at least `homeDefenders`, 3) at home. Three ways to improve it, none taken: each loss
  fading from its own time (today the older ones fade with the newest: Ember's three losses of day 3.1 counted in full
  again at day 4.7); a look that saw none of the yard not replacing an older look nor clearing the losses seen since
  (today it does: a blind look on day 5.01 cleared Ember's seven losses on Stoneguard, another replaced Ember's 32% look
  at Grove); a floor of `homeDefenders` for a base whose raiders fell. The first two need a save format change for
  `Report`.
* **A pact against a conqueror nobody has found**: the pact's councils weighed only the conqueror's bases, so with none
  reported they launched nothing at all (no lair either) until a scout found one: 1.7 days without a launch here.
  Removed by the owner on 2026-11-25 (no more pacts; every member of a fallen faction dies with it): closed.
* **A miner at the storehouse overnight**: Stoneguard's miner stood at the storehouse stand from day 2.49 to 2.96 (its
  status "Walking to the storehouse", no path, its own stand one block off, a full backpack of 401 items, the
  storehouse's cobblestone at 511 of the 512 junk limit) and went back to its mine at dawn; once in 7 days, cause not
  found.
* **Grove's far branch faces**: the stuck recoveries above (mining navigation to faces 120 to 175 blocks from home at
  Grove's mines; seen before this review too).
