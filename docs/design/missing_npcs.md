# Deaths far from home: missing, presumed dead, known dead

The owner's request (2026-11-13, HANDOFF section 2 item 9): "when a npc dies and it is far away from the base, the
faction shouldnt automatically know about it, but if someone whos is meant to return base doesnt return after a set
amount of time then the faction should assume that they died and replace them". ROADMAP phase 1 row "Deaths far from
home are not known at once". Built 2026-11-19; this is the design as built.

The rule is the scouts' report model applied to deaths: **a faction knows only what its people saw, and knowledge
travels home with someone.** One rule for every member, ordinary NPC or hero.

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

The trip's plan (`Job.tripPlanTicks`, pure numbers in `Whereabouts`), in ticks from leaving:

| Who | Plan |
|---|---|
| Scout | its expedition's own bound: departure + `war.scoutOverdueTicks` (24000; it turns home at half of it) − now |
| Raider | the raid's own bound: launch + 72000 (a raid ends by then) + the walk back from the target − now |
| Trader | its trip's bound: start + 24000 (it gives a trip up after a day on the road) + the walk back from 320 blocks − now |
| Cave explorer | its trip's bound: departure + 12000 (`CaveExplorerJob.MAX_TRIP_TICKS`) + the walk back from its cave (+64) − now |
| Anyone else (a woodcutter foraging, a hero's duty) | 12000 (half a day) |

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
  the raid's own bound (launch + 72000) has not passed (`Whereabouts.raidStillOutAtHome`). A party nobody lived to
  report on therefore keeps the council waiting until the bound; one whose every raider died in sight of home does not.
* The **raid party's roll** (`FactionState.raiders`) is the party's own knowledge on the spot: a raider leaves the
  party when the faction no longer counts it (`alive` false) or when it died and a member saw it (`witnessed`). The raid
  ends as before, or when every raider is truly gone (no one is left to carry it on; nothing is told at home).
* Not changed (open item): the council's estimate of an *enemy* base still reads that enemy's own roster and heroes
  (pre-existing; the scouts' reports do not yet carry a garrison count).

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

## 8. The kill feed

`hero/HeroKillFeed` is player-side presentation: every player sees every hero death at once, in chat. No campaign,
work or NPC code reads it, nor `combat/KillCredits` (a unit test, `KnowledgeIsolationTest`, scans those packages).
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
wherever the hero stands.
