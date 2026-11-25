# Deaths far from home: missing, presumed dead, known dead

The owner's request (2026-11-13, HANDOFF section 2 item 9): "when a npc dies and it is far away from the base, the
faction shouldnt automatically know about it, but if someone whos is meant to return base doesnt return after a set
amount of time then the faction should assume that they died and replace them". ROADMAP phase 1 row "Deaths far from
home are not known at once". Built 2026-11-19; this is the design as built.

The rule is the scouts' report model applied to deaths: **a faction knows only what its people saw, and knowledge
travels home with someone.** One rule for every member, ordinary NPC or hero. The same rule for everything else a
faction's AI reads (the war council's view of enemy bases, raids' plans, a miner's shift, retraining, the blacksmith,
the lookout and more) is `docs/design/knowledge.md`, the knowledge review of 2026-11-19.

## 1. Truth and knowledge

Each roster entry (`campaign/NpcRecord`) holds two kinds of fields.

* **Knowledge** (what the faction believes), read by the faction's AI: `alive` (the member counts: needs,
  recruitment, retraining, workplaces held, the war council's garrison, the food reserve), `where` (the state below),
  `leftAt`, `expectedBy`, `since`, `seenHomeAt`.
* **Truth** (what happened), read only by the engine and by operators: `dead` (its entity died), `vanished` (its entity
  is gone from a loaded place without dying), `diedAt`, `diedPos`, `witnessed`. The engine reads it for chunk tickets
  (an NPC presumed dead but alive far away keeps its travelling area, so it can walk home), a hero's revival timer, and
  the raid party's own roll (section 6); `/fc admin roll`, `/fc admin dump` and `sim.py` show it.

`alive` is true exactly in the states HERE, AWAY and MISSING.

## 2. States (the faction's view, `logic/campaign/Whereabouts.State`)

| State | Meaning | Counted (`alive`) |
|---|---|---|
| HERE | last seen inside a home area of its faction | yes |
| AWAY | seen leaving; expected back by `expectedBy` | yes |
| MISSING | not back by `expectedBy` + grace (`since` = when it went missing) | yes: not replaced yet; its workplace stays held |
| PRESUMED_DEAD | missing for the presumed-dead wait | no: replaced; its record is kept |
| KNOWN_DEAD | a death the faction learned of | no |

"Returned" is an event, not a state: a MISSING or PRESUMED_DEAD member seen at home again becomes HERE.

**Home area**: within `campaign.homeAreaRadius` (64) blocks, horizontally (Chebyshev), of the centre of any standing
settlement of the member's faction, at any height (a GameTest base: its own work radius; never less than its walls
plus 4). This is the land where its own workers fell trees, dig mines and fish (64 is the default forest, mine and
fishing radius), where the people at home see and hear what happens.

## 3. Leaving, the trip's plan and coming back

Every 40 ticks (the record timer that already writes `lastPos`) a member's whereabouts are updated:

* **Inside a home area**: `seenHomeAt` = now. AWAY becomes HERE quietly; MISSING becomes HERE with "X is back";
  PRESUMED_DEAD becomes HERE and `alive` again with "X, presumed dead, has come back and rejoins". The news the member
  carries is told (section 5).
* **Outside every home area while HERE**: it leaves. `leftAt` = now, `expectedBy` = now + its trip's plan, state AWAY.
  The plan is fixed when it leaves: a change of plan made away from home is not known at home.
* **The edge band** (knowledge review of 2026-11-19, `docs/design/knowledge.md` 4): a member seen at home leaves only
  past the edge plus an eighth of the radius (8 blocks for 64); an away member is home again only that far inside the
  edge (a missing or presumed-dead one anywhere inside). Deaths still use the edge itself.

The trip's plan (`Job.tripPlanTicks`, pure numbers in `Whereabouts`), in ticks from leaving:

| Who | Plan |
|---|---|
| Scout | its expedition's own bound: departure + `war.scoutOverdueTicks` (24000; it turns home at half of it) − now |
| Raider | the raid's plan, kept at launch (knowledge review of 2026-11-19, `docs/design/knowledge.md` 3): the walk to the rally point + the muster's longest wait (6000) + the assault (6000) + the walk home from the target; launch + that − now. A raid launched before the plan was kept: launch + 72000 + the walk back |
| Trader | its trip's bound: start + 24000 (it gives a trip up after a day on the road) + the walk back from 320 blocks − now |
| Cave explorer | its trip's bound: departure + 12000 (`CaveExplorerJob.MAX_TRIP_TICKS`) + the walk back from its cave (+64) − now |
| Fisherman at far water, woodcutter in a far wood | its day's remote work: until the coming nightfall (day time 13000) + the walk home from where it leaves, never under 12000; 12000 with `survival.remoteWorkInDaylightOnly` off (section 14) |
| Miner at a far face or in a far cave | its shift, fixed at its last storehouse visit: until the coming nightfall, at least half a day; what is left of it + the walk home (knowledge review of 2026-11-19, known leak 6); past its shift outside the home area it goes back to the storehouse |
| Anyone else (a hero's duty) | 12000 (half a day) |

The walk is `Whereabouts.walkTicks(blocks)` = 10 ticks a block (0.1 blocks a tick: half a walking NPC's pace, for
detours and nights).

**Waits** (checked by the director every 100 ticks for each settlement's members): AWAY past `expectedBy` +
`campaign.missingGraceTicks` (2400) becomes MISSING; MISSING for `campaign.presumedDeadTicks` (12000) becomes
PRESUMED_DEAD.

**A presumed-dead member who comes back** rejoins: `alive` again, its own gear (it never dropped it), its role. Its
replacement stays: the base has one more of that role, and the existing rules sort it out (a workplace taken by the
replacement leaves the returner to claim another free one; an idle extra is retrained after its idle window). A
returning raider is no longer in the raid party (section 6): it is a guard at home.

## 4. What is known at once

A member's death (`FactionNpc.die`, `RollCall.onDeath`) is known at once when:

1. it dies inside a home area of its faction; or
2. a **witness** at home saw it: a living member of its faction (an NPC or a hero; not a display copy) within
   `campaign.witnessRange` (32) blocks with a clear line of sight (eye to eye), standing inside a home area itself; or
3. the dead member is a hero rising at home (its revival, section 7).

Known at once: KNOWN_DEAD, `alive` false, "X (Role) was killed" (as before).

Otherwise the faction does not know: the record keeps its state (HERE becomes AWAY with its plan if it had not been
seen leaving yet), `alive` stays true, nothing is said. `dead` and `diedAt` are set (truth).

## 5. News travels with someone

* Each witness away from home (section 4's rule, outside every home area) **carries the news** (`FactionState.news`,
  by carrier). A member entering a home area tells everything it carries: KNOWN_DEAD, "W brings news: X (Role) was
  killed near x, z on day N" (or, presumed dead before: "W brings news: X (Role), presumed dead, was killed near x, z on
  day N"). The first carrier home tells it; the others' copies are dropped.
* **A scout's report carries it too**: a scout is a member like any other, so a death it saw on its expedition is told
  when it comes home with its report.
* **A dead witness carries nothing**: when a carrier dies, its news dies with it (a death it saw is not passed on).
* A **member player** is a person: what it sees (its screen, chat, the kill feed) is its own business and never reaches
  the faction's AI. Its own report counts on the same terms as an NPC witness's, and only so: a death it saw under the
  same sight rule (32 blocks, line of sight, at the moment of death) is carried by it, and is told when it chooses to
  report at a reporting station of its faction (using the station; an NPC's AI tells on arrival, a player decides). A
  player who dies loses what it carried, as any dead witness. It cannot report what it did not see.
* Other lines the faction could only learn from someone there travel the same way: a raid's "has gathered", "beaten
  back" and "has ended" lines are carried by the raiders there at that moment (none alive: never told; the raiders go
  missing on schedule), and "X of the Y has fallen to Z" (an enemy hero killed by one of ours) is carried by the killer
  unless it is at home (a player killer: told at once, the player saw it).
* A line about a member itself (`News.Kind.OWN`, told as it is): a level it gains away from home ("X reached level
  4 ...") is said when it comes home (`RollCall.ownLine`); at home, at once.
* A hero's own speech: its lines for its faction (`HeroSpeech.Audience.FACTION`: its fall, its level, its revival...)
  reach the whole faction only when it stands in a home area; away, only the players near hear them, as its other
  lines (`HeroSpeech.audience(event, atHome)`; with `heroes.dialogue` "faction" every line goes to the faction, the
  operator's choice).

## 6. The war council, recruitment and the raid party

* Recruitment (`FactionDirector.needs`), retraining, workplaces and the food reserve read `alive`: a member dead far
  away keeps counting until the faction knows or presumes it dead.
* The war council's own garrison, party and soldiers are what is at home (entities seen at home) and `alive`.
* The **council hears of its raid's end only through its people** (review of 2026-11-19): when the party ends its raid
  on the spot, the end's news id is kept (`FactionState.raidEndNews`, with the party as launched in `lastParty`), and
  the council plans no new raid while that news has not been told at home, it still counts someone of the party, and
  the raid's plan plus `missingGraceTicks` has not passed (`Whereabouts.raidStillOutAtHome`; the plan since the
  knowledge review of 2026-11-19, the raid's 72000-tick limit before). A party nobody lived to report on therefore keeps
  the council waiting until its raiders are overdue and missing; one whose every raider died in sight of home does not.
* **The last raider to leave the party alive saw its end** (live check, section 14): a raider leaves the party when it
  is home at its storehouse with its loot or falls back wounded (`GuardJob`), a hero when it reaches home
  (`HeroRaid`), through `FactionState.leaveRaid`; the one whose leaving empties the party is kept
  (`FactionState.raidLast`, saved) and carries the raid's end (`RollCall.raidEndCarriers`): told at once when it is
  home, else when it gets there.
* The **raid party's roll** (`FactionState.raiders`) is the party's own knowledge on the spot: a raider leaves the
  party when it died and a member saw it (`witnessed`; every death with `farDeathsUnknown` off). What home believes is
  not the party's to know: a raider home reports missing or presumes dead stays on the roll (review of the knowledge
  review, 2026-11-19: home's belief pulled live raiders off the party where they fought). The raid ends as before, when
  every raider is truly gone (no one is left to carry it on; nothing is told at home), or when the party must turn home
  to be back by the council's plan (`Whereabouts.raidTurnHomeAt`, `docs/design/knowledge.md` 3).
* The council's estimate of an *enemy* base reads only what its people saw (knowledge review of 2026-11-19,
  `docs/design/knowledge.md` 1): its scouts' and raiders' looks at the garrison, aged, a prior where nobody looked, and
  the enemy soldiers seen falling at home.

## 7. Heroes

Heroes follow the same rule; their revival is unchanged (`HeroManager`: 600 s after the fall, at home). A hero's death
known at once says the existing "X has fallen at x, z and returns in M:SS"; one seen by an away witness is told by the
witness ("W brings news: X has fallen near x, z on day N"); one nobody saw is learned when the hero **rises at home**:
"X fell where none of the faction saw it and returns to Base". The revival makes a new entity and roster entry
(HERE); the old entry and any news of it are dropped. A hero that rises **where it fell** (`revival.where:
where_it_fell`) outside every home area, from a fall nobody saw, is seen by nobody: nothing is said, its new entry keeps
the old one's view (away, missing or presumed dead, the same expected return), and it carries its own fall home
(`Whereabouts.ownFall`: "X fell near x, z on day N where none of the faction saw it, and has risen again"). A hero
that vanished from a loaded place is the engine's "fallen" (it revives) whatever the faction believes (`vanished`, not
`alive`, drives that).

**A faction's fall** (owner, 2026-11-25: "if a faction gets eliminated, all of their members dies"): when its last base
falls, every member dies with it and the roll call marks every entry known dead at once (`RollCall.factionFell`). A
member still away in an unloaded place is dead for the engine too (its record's `dead`), so no traveller's chunk is
kept or brought back for it; its entity dies where it stands when its chunk next loads (`CampaignManager.diesWithFaction`,
from `FactionNpc.tick`), and its death tells nobody (there is nobody left at home; `RollCall.onDeath`). Its heroes never
rise again: each is freed with its level kept (`HeroManager.factionFell`), a hero waiting to rise included.

## 8. The kill feed

`hero/HeroKillFeed` is player-side presentation: every player sees every hero death at once, in chat. No campaign,
work or NPC code reads it, nor `combat/KillCredits` (a unit test, `KnowledgeIsolationTest`, scans those packages). A
hero that dies with its fallen faction in an unloaded place is told at the fall (`HeroKillFeed.fellAway`, vanilla's
"was killed" with its faction), once.
The faction's records and its AI follow only section 4-5.

## 9. Config (`[campaign]`; the scouts' bound is in `[war]`)

| Key | Default | Meaning |
|---|---|---|
| `farDeathsUnknown` | true | false: every death is known at once (the old behaviour); the next roll call knows the deaths not yet known |
| `homeAreaRadius` | 64 | the home area round each settlement's centre (blocks, Chebyshev) |
| `witnessRange` | 32 | how far a member sees a death (with a clear line of sight) |
| `missingGraceTicks` | 2400 | past its expected return plus this, an away member is missing |
| `presumedDeadTicks` | 12000 | this long missing, it is presumed dead and replaced |
| `war.scoutOverdueTicks` | 24000 | (existing, in `[war]`) a scout's expedition bound, its trip's plan |

## 10. Save format

`NpcRecord`: `dead`, `vanished`, `where`, `left`, `expected`, `since`, `home`, `witnessed`, `diedAt`, `diedPos`. An old
record has none: `dead` = not `alive`; `where` = KNOWN_DEAD when not alive, AWAY for a scout with a departure (expected
back by departure + `scoutOverdueTicks`), else HERE. `FactionState.news`: carrier, news id, victim, kind (DEATH, LINE, OWN), line, place,
time; absent in old saves (no news). `FactionState.raidEndNews` and `lastParty` (review of 2026-11-19): absent in older
saves (the council waits for nothing). `CampaignData.FORMAT` 4 (logged only).

## 11. Operators and sim.py

`/fc admin roll [faction]`: every member not HERE (state, since, expected back, truth), and the news each carrier
holds. The reporting station's information shows the faction's own view: "Away N; missing: X (Role) since day D;
presumed dead: Y (Role)", and a workplace's worker as "(missing)" or "(presumed dead)" (never the truth). `/fc admin dump` adds the record fields and the news; `sim.py` prints a `roll` line per faction
(here/away/missing/presumed/known dead, deaths not yet known, news carried).

## 12. Tests

Unit (`WhereaboutsTest`): the state steps and waits, plans, walk times, who knows at once, news delivery and its
lines, old-record defaults. `KnowledgeIsolationTest`: no campaign/work/npc source reads the kill feed or kill credit.
GameTests (`gametest/MissingNpcGameTests`, each its own batch): a scout killed far away is not known at once, goes
missing, then presumed dead, then replaced (the base's needs ask for a scout); a death near the base, or seen by a member
at home, is known at once; a witness who sees a death and comes home reports it; a dead witness carries nothing; a
member player reports what it saw at the station and loses it when it dies; a raider presumed dead who comes back
rejoins with its own gear beside its replacement; a hero killed far away revives at home after its unchanged revival
time and that is how its faction learns (a hero killed at home is told at once); old records and factions load.
Eight sabotages (every death known at once, no missing, a dead witness keeping its news, no telling on coming home, no
rejoining, a hero's rising not telling, old scouts not away, a faction class naming the kill feed) each made a test
fail.

## 13. Review of 2026-11-19

An adversarial review looked for every path where a faction still learned the truth at once. Found and fixed, each with
a test that fails without the fix:

1. **The war council heard of its raid's end at once**: a party whose every raider died unseen ended on the spot, and
   the council could send the next raid at once. Now section 6's wait (`theCouncilWaitsForNewsOfARaidNobodyLivedToReportOn`).
2. **`farDeathsUnknown` turned off left the unknown deaths counted for ever** (holding their workplaces, never
   replaced). The roll call now knows them (`Whereabouts.knownUnderOldRule`, `aDeathNotYetKnownIsKnownOnceFarDeathsAreKnownAgain`).
3. **A hero rising where it fell, far away, told its faction at once** (and reset its expected return). Section 7
   (`aHeroRisingUnseenFarFromHomeTellsItsFallWhenItIsHome`).
4. **Faction lines from members away were said at once**: a level gained (a member presumed dead was heard levelling
   up), the raid party's gathering at the rally point, a hero's faction lines (its dying words reached its faction from
   wherever it fell). Section 5 (`aLevelGainedAwayIsToldWhenTheMemberIsHome`, `theRaidPartysGatheringIsCarriedHome`,
   `HeroSpeechTest.aFactionLineSaidAwayIsHeardOnlyNearby`).
5. **The station said "(dead)" of a presumed-dead worker** and nothing of the missing. Section 11
   (`theStationTellsWhoIsAwayMissingOrPresumedDead`).
6. **"Adventure" (killed by something) was made by the death event**, naming the dead at once wherever it fell; it is
   now made when the faction learns of the death (`anUnseenDeathMakesNoAdventureUntilItIsKnown`).
7. `KnowledgeIsolationTest` now also scans `info` and `achieve`, and checks that no faction-side class reads a roster
   entry's truth (`dead`, `vanished`, `witnessed`, `diedAt`, `diedPos`, `gone()`) outside `NpcRecord` and `RollCall`,
   but for the raid party's two engine lines, which say so (`// truth:`).

Checked and found sound: workplaces are held by `alive` (knowledge), so a dead member's job is not freed at once and a
returning member never shares one with its replacement (jobs find their workplace by its `assigned`); no double
replacement (presumed and known dead are both uncounted, a later report only confirms); chunk tickets follow the truth
so a presumed-dead member alive far away keeps its area and can walk home; the hero kill feed (`HeroKillFeed`,
`KillCreditEvents`) is unchanged since the owner's scope of 2026-11-19 and read by no faction-side code.

Left open (pre-existing, not about deaths): a cave explorer's survey is written to its workplace at the cave, so the
faction's miners can use it before the explorer is home; the council judges an enemy base by that enemy's own roster
and hero state; the retraining reads a far worker's live job and the blacksmith a far member's gear; `/fc hero info`
shows a member player the hero's true place and state; the hero lookout's "spots N enemies" line is said at once
wherever the hero stands. All but `/fc hero info` (player-side) were taken up by the knowledge review of 2026-11-19
(`docs/design/knowledge.md`; the survey turned out to be handed in at the station already).

## 14. Live check of 2026-11-19

A fresh campaign on a dedicated server (seed 20261107, `fc start 256`, four factions and the Hollow), days 0 to 6.94,
with Natsuho Komezaki given to Ember Legion and Hinae Shirasuzu to Stoneguard (`fc admin hero give`). A tracker
(`tools/rolltrack.py`) polled `fc admin dump` every 8 s and logged every change of each member's record (the faction's
view beside the truth), every crossing of a home area's edge, news carried and dropped, and the heroes' deaths and
revivals; `sim.py`'s `roll` line was read each day; chat was captured with the Xvfb viewer
(`docs/screenshots/rollcall/`).

Seen as designed, against the truth:

* **Near home, known at once**: Tide's farmer Pell Grey killed by the operator at home: "Pell Grey (Farmer) was killed"
  the same tick, a farmer recruited at once. Every death in a base fight (Grove's and Stoneguard's last days) was known
  at once, most of them seen by a member.
* **Far, unseen: missing, then presumed dead**: Ember's scout Edrin Cole died to monsters at night at 431, 65, 53 (day
  0.98): nothing said; a follow-up scout recruited for the overdue scout, missing at day 2.02 (its expedition's bound +
  grace), presumed dead at 2.52. Stoneguard's scout Isla Oakes, killed by the operator far away at day 1.27: missing at
  2.29, presumed dead at 2.79. Stoneguard's scout Rhea Lowe (killed by Natsuho near Ember, day 2.67, no feed line):
  missing 3.71, presumed dead 4.21. Tide's cave explorer Orla North (killed by Stoneguard's returning raiders, 2.72):
  missing 3.37, presumed dead 3.87. Ember's three raiders Hinae smote at Stoneguard (day 1.37, no feed line; Yara's death
  seen only by raiders who died, so the news died with them): missing at 4.48 (launch + 72000 + the walk back + grace),
  presumed dead at 4.98.
* **Presumed dead, then back**: Tide's scout Elsa Thorne, held alive by the operator in a barrier box at 371, 62, -302
  (NoAI): missing 1.89, presumed dead 2.35 ("is presumed dead (missing since day 2)"; the station: "Away 2; presumed
  dead: Elsa Thorne (Scout)"), released at day 2.39: "Elsa Thorne (Scout), presumed dead, has come back and rejoins",
  then her report; the follow-up scout Soren Flint, recruited while she was overdue, stayed.
* **Witnesses carry news home**: the operator killed Tide's raider Zeno Stone beside Nils Stone at 274, -46: Nils
  carried it and told it at home ("Nils Stone brings news: Zeno Stone (Swordsman) was killed near 274, -46 on day 2"),
  with the raid's "beaten back" and "has ended". At day 6.64 Natsuho fell at Ember's rally point before Grove, seen by
  Nils Grey and Hale Reed: "Nils Grey brings news: Natsuho Komezaki has fallen near 501, 145 on day 7 and returns in
  7:33"; "Dara Ives brings news: Beck Reed (Swordsman) was killed near 518, 127 on day 7". The raid party's "has
  gathered" came home with Natsuho and with Hinae.
* **Heroes**: Natsuho, slain at Stoneguard in a raid (day 1.42, feed: "Natsuho Komezaki (Ember Legion) was slain by
  Kael Oakes (Stoneguard)"), rose at home 10 minutes later: "Natsuho Komezaki fell where none of the faction saw it and
  returns to Ember Legion Keep". Hinae killed at home by the operator (1.98): feed "Hinae Shirasuzu (Stoneguard) was
  killed" and "[Stoneguard] Hinae Shirasuzu has fallen at 400, 16 and returns in 10:00" at once.
* **The kill feed**: four lines in seven days, all hero deaths ("was slain by Kael Oakes (Stoneguard)", "was killed");
  none for the 92 other named deaths in the server log, among them Hinae's three kills of Ember raiders and Natsuho's
  of Rhea Lowe. No faction code reads it (`KnowledgeIsolationTest`).
* **A miner stuck far out is missing while alive**: Grove's miner Pell Vale, stuck at a tunnel face at 565, 15, 211
  (67 blocks out, 52 stuck recoveries), was missing at 2.70 and back ("is back") at 2.98: right from the faction's view.
  Every other member reported missing (13 in all) was truly dead, or held by the operator (Elsa Thorne).
  Miners working branch mines that cross the 64-block edge (Ember's Kael Wells, Stoneguard's Elsa Stone) went in and
  out many times a day, each crossing setting a new half-day plan; none was reported missing while working (since the
  knowledge review of 2026-11-19 a miner's trip is its shift, fixed at the storehouse, and the edge has a band:
  `docs/design/knowledge.md` 4).

Found and fixed (the tests fail without the fixes; the fixes were checked again live on a new jar from day 5.44, the
world loading with the new `raidLast` field absent):

1. **A raid party that came home one by one told its end to nobody**: each raider leaves the party at its storehouse,
   so the party emptied with no raider "alive in the party" to carry the end; the end's news had no carrier and the
   council waited out the raid's whole bound (Stoneguard's won raid on the Hollow at day 2.6 never said "has ended in
   victory" and its council stayed silent). Now the last to leave carries it (section 6); live with the new jar:
   "[Ember Legion] Raid on The Hollow has ended in victory" said at home as the last raider arrived.
   `MissingNpcGameTests.aRaidPartyThatComesHomeTellsItsEnd` (the raiders' own guard jobs at the storehouse and falling
   back wounded).
2. **A day's remote work risked a false "missing"**: Natsuho (a fisherman, her spot about 70 blocks out) was away 13716
   ticks from dawn to dusk against 14400 (half a day + the grace): 700 ticks from being reported missing while fishing.
   Fishermen and woodcutters stop remote work at nightfall, so their plan is now "until nightfall + the walk home"
   (section 3); live with the new jar, her dawn trip was planned 13320 ticks.
   `WhereaboutsTest.aDaysRemoteWorkIsExpectedBackAtNightfall`, `MissingNpcGameTests.aDaysRemoteWorkIsExpectedBackAtNightfall`.
3. **Wording**: the recruitment reason "replacing the missing farmer" read as the roll call's "missing" (a member not
   back yet); it is now "no farmer left".

Seen and left open:

* **The council judges an enemy base by that enemy's own roster** (the open item of section 6): at day 6.13 the operator
  sent Stoneguard's six soldiers and Hinae against a Hollow lair; within five seconds Tide's and Grove's councils both
  launched raids on the emptied Stoneguard ("against about 4 defenders"), and Tide's destroyed it by about 6.2. Neither
  had a report of the garrison leaving. Fixed by the knowledge review of 2026-11-19 (`docs/design/knowledge.md` 1): the
  scouts' reports carry a look at the garrison and the council uses only those.
* **A raid wiped out unseen waits out its bound**: Ember's raid of day 1.3 (three raiders and Natsuho, all dead at
  Stoneguard) held Ember's council back until day 4.3 (its next raid of its own went out about day 5), and the three
  raiders were presumed dead only at 4.98.
  Fixed by the knowledge review of 2026-11-19 (`docs/design/knowledge.md` 3): a raider is expected back by the raid's
  own plan (the march there, the muster, the assault, the march home), about 0.75 day for a 300-block raid.
* A hero that dies keeps no news (like a member player who dies): Natsuho, last of her raid to fall, rose at home
  knowing nothing of how it went. Equal to the player's case, so left as is.

Tick time: 6.62 ms a tick on day 1 (151 ticks/s in sprint, no viewer), 9.9 ms with the viewer on, 9-12 ms over days
5-7 with the art workflow's Python jobs on two of the four cores. Workers' mean stuck recoveries 11.4 (day 4) and 13.3
(day 5), as in earlier campaigns (12.9); the heroes' own recoveries 0. Workers, recruitment (follow-up scouts for
overdue scouts, replacements for the presumed dead), raids (four by Ember, three by Tide, two lairs and two factions
destroyed) and pacts (removed by the owner on 2026-11-25) went on as before.
