# Kit gaps found while writing the slice kit sheets (2026-11-12)

Kit version 2 (2026-11-13: equal treatment, no attack tokens, no yield; HEROES_V2.md section 6) closes item 10's
refuge clash and adds each hero's end-of-fight lines (item 2). The other points stand.

The kit sheets in `docs/heroes-v2/kits/` were written from SLICE_PLAN.md section 3, MOBILITY_AND_WORK.md and
HEROES_V2.md, and checked number by number. These are the points the design leaves open or contradicts. C1-C4 (the
hero source files) decide each one, update SLICE_PLAN.md section 3 and regenerate the kit sheet. (The 15-minute
leader revival in SLICE_PLAN was fixed to 10 minutes when this list was made.)

1. Followups that repeat the same move clash with HEROES_V2 6.3, which rejects cyclic followup chains and strings longer than 4: Natsuho's zoomies (follows itself 50%), Tobimaru's shadow_dash (string of 4), Ambrose's broom_hops (string of 3). It is also not stated whether a chained dash replays its wind-up and ground mark.
2. Values not set for the seven heroes other than Kagenori: move cooldowns and weights, reaction cooldowns, and phase-transition lengths. For all eight, ultimate energy and charge rate and the minimum gap between ultimates (20-60 s) are open. Natsuho, Yugen, Hinae, Tobimaru and Ilsabet have no ultimate line, although HEROES_V2 6.4 requires one. No hero has its own enrage, stagger or announcement lines; the temperament fallback banks cover these. (Since kit version 2, 2026-11-13, each hero has its own end-of-fight lines from SLICE_PLAN section 3: escape, last stand and fallen as its kind needs; Yugen's escape line will be heard most.)
3. Boss bar title: the top-level title field is set only for Kagenori ('the Still Crane'); the other seven have none.
4. Ground mark shapes are missing for heavy self, counter, melee and chain moves (cat_reflex, mirage, seven_talismans, shadow_step, shadow_clones, iai_stance, guard_splitter, chain_draw), for sweeps (turning_sweep, purifying_dance), and for self-state ultimates (midnight_frenzy, nightfall).
5. Ambiguous numbers: is fan_cut's '60%' per strike or the total? Which claw_string strike carries the bleed? Is Benika's 'three fire waves of 8' their reach or their damage (and what are the crash damage and the wave interval)? Is backstab's '+60% more' added or multiplied? Does a zone's 'delay 1.0' (lantern_ring, sealing_circle, cauldron) run inside the wind-up or after it?
6. Durations and stacks not set: the bewitched, seal and hex marks; the storm mark, which nothing in Kagenori's kit reads; the nine_lives shield; the cauldron's Poison I; the Speed I on Tobimaru's unseen; the impact damage and stacking of kunai_fan; the length of Hinae's ultimate channel.
7. Reactions that name a move missing from the current phase: Benika's blocking -> grab_throw and Kagenori's blocking -> guard_splitter (both moves only exist in phases 2-3). Kagenori's phase 3 list: the SLICE table includes iai_stance and crane_step, but the HEROES_V2 12.18 example leaves them out. guard_break lasts 4 s in the kit but 5 s in the HEROES_V2 6.9 rule.
8. Weak points: snowball (Benika) and light (Tobimaru) have no rate limit. It is unclear whether Rage comes back after a snowball, and whether light lowers his damage dealt or raises his damage taken. The size of Ilsabet's Bulwark-doubled back weakness is not given, nor whether the back bonus applies outside recoveries. The broken-channel outcome differs: Ambrose's great_brew says 'staggers 2 s', the rule says '2 s recovery'. Hinae's 'dance prayer' channel weak point has no channel field. Most heroes lack the hint line or prop that HEROES_V2 6.9 requires for each weak point. Natsuho's water trigger includes rain starting (isInWaterRainOrBubble); confirm this is intended.
9. Work mastery: does Presence apply to a hero whose duty is not guard (Natsuho's lookout, Benika's drill)? Does mentor's personal effect apply to Kagenori, a guard? Does Ilsabet get warden_of_walls' personal effect, and how does its repair budget combine with her LeaderBiases budget? Does Tobimaru get the spymaster specialty? MOBILITY_AND_WORK 12.4 gives no proficiency part for the scout or enchanter jobs. Yugen's backpack is 21-27 by proficiency and 30-36 by specialty, with no rule for which wins. It is not said what base-wide talents do while their hero is away trading. Ambrose's sage_of_letters (XP level 7) and his LeaderBiases enchanter level (8) do not say how they combine. The eccentric-genius rule 'crafting and lore projects first' is not defined.
10. Raid goals: Yugen matches both 'storehouse first' (greed 70) and 'clear the archers' (marksman, zoner), and Hinae (a zoner) also matches clearing the archers; no priority is given. The refuge for a non-leader who refuses the yield at its own home base clashes with 'never the base it flees'. *Resolved 2026-11-13: there is no yield; a hero that escapes from a fight at its own home base takes another base of its faction, else the wild (MOBILITY_AND_WORK.md 7.7), and Natsuho and Hinae never run from their home base at all (`stands_for: home`, HEROES_V2.md 6.13).*
11. Rounding is not specified for half values: trader thresholds of 11.5 (Hinae), 17.5 (Kagenori) and 13.5 (Ambrose), and retreat_below results that are not whole numbers (6.5, 22.5, 28, 31.5, 37.5, 44.5) for a field defined as a whole %. (The escape threshold made from it, `flee_at`, rounds a half up and is whole: HEROES_V2.md 6.13.)
12. Enrage for non-leaders: SLICE_PLAN gives Benika, Hinae and Ilsabet only '240 s as leader'; the sheets use the 180 s default for non-leaders.
13. Ambrose: the phase 2 flight with broom_dive is only a proposal in MOBILITY_AND_WORK 15.8 and is not in SLICE_PLAN 3.8. Three puddles exceed the v1 limit of 2 zones. His broom bristles are a phase 2 part, yet he flies the staff-broom in every phase.
14. Ilsabet: her shield blocking is not specified. Her phase 3 line names the Rime Vow even when she serves another faction (run A). Her base style is frost_hall in SLICE_PLAN but stone_hold in ROSTER_PLAN.
15. Idle animations differ between the look and the own-animation list for Natsuho, Yugen, Benika and Hinae; the sheets flag each one. ROSTER_PLAN row 22 puts Benika's grab in phase 3, where SLICE_PLAN has it in phase 2.

## F2 decisions on the format (2026-11-13)

F2 (the format-2 validator, HEROES_V2.md 15.2 "F2 as built") decided the points that are rules of the format, in the
spirit of the design; C1-C4 still decide the content points (numbers, lines, looks) and update the kit sheets.

- **1, followups**: a move follows itself only when it is `repeatable`; a self-followup's `max` (1-3) caps its
  repeats; the string limit of 4 moves counts every move played, repeats included (zoomies runs at most 4 times in
  a row, broom_hops with `max: 2` hops 3 times, shadow_dash's string of 4 is legal). The validator refuses loops
  through other moves and checks the longest string through other moves. Every move of a string plays its own
  wind-up, sound and ground mark; only the recovery between two of them is cut (HEROES_V2 6.3).
- **2, values not set**: a move's weight defaults to 5, a reaction's chance to 50% and its cooldown to 10 s, a phase
  transition to 1 s, energy gain to 10 and charge rate to 100%; the gap between ultimates is clamp(weight, 20, 60).
  An ultimate without a line loads with a NOTE. Cooldowns stay the authors'; under the floor they are raised
  (ADJUSTED).
- **4, ground marks**: from the form when a move names none (`HeroCatalogue.decalOf`): a disc for self, melee, combo,
  chain, nova and zone moves, a strip for lines, dashes, charges, shots, bolts, barrages and grabs, a fan for cones,
  volleys and sweeps, a ring for waves and counter stances, a landing disc for leaps, one disc per impact for rain.
  A light move shows only its animation; `none` on a heavier or an unblockable move is read as the form's mark.
- **5, ambiguous numbers**: a zone's delay runs inside its wind-up (the wind-up is raised to the delay, the delay
  becomes 0); each combo strike carries its own effects, so "60%" is per strike; "+60% from behind" is a second
  damage effect with a `target_behind` condition (added, not multiplied). Which strike bleeds and what Benika's
  "waves of 8" are stay content choices (the test fixtures put the bleed on the third strike and read 8 as the
  waves' reach).
- **7, reactions in the wrong phase**: a reaction whose move is not in the current phase waits for a phase that has
  it, with no message (Benika's and Kagenori's answers to a shield start in phase 2).
- **8, weak points**: `channel` is a move field (`true` is 15%, a number 5-30, or `{ interrupt }`); a `channel` weak
  point with no channelled move is a NOTE. Rate limits, hints and the broken-channel outcome stay content.
- **11, rounding**: a `retreat_below` made from the personality is rounded half up to a whole percent (22.5 is 23,
  44.5 is 45), and `flee_at` rounds a half up too.
- **12, enrage**: 180 s by default, 240 s as a leader, for every hero.
- **13, Ambrose**: a flyer's aerial phases come from `mobility.aerial` (his: phase 2, MOBILITY_AND_WORK 8.4); the
  `dive` form itself is F5/A12's. The test fixture makes broom_hops' puddle the landing's slowness, so he has no
  third zone.

### F2: numbers the test fixtures wrote at a cap or a floor

The eight slice kits are written as format-2 test fixtures (`src/test/resources/heroes/slice/`, test data, not the
authored heroes). They must validate clean, so where SLICE_PLAN section 3's number is over a cap the fixture writes
the cap, and `SliceKitsTest.theNumbersTheFixturesLoweredAreReallyOverTheirCaps` shows each one is adjusted when put
back:

- Natsuho's `nine_lives` shield: 4, not 8 (a passive's shield cap is 4).
- Natsuho's `frenzy` "every hit bleeds": 0.5 per second for 2 s, not her basic bleed's 1 per second for 3 s (a
  state's on-hit effects share the on-hit cap with the basic attack's, and her basic bleed uses most of it).
- Kagenori's `three_cut` cooldown: 10 s, not HEROES_V2 12.18's 6 (its cooldown floor at its weight).

Cooldowns SLICE_PLAN leaves open were set at or above their floors (for example Benika's `overhead_slam` 11 s,
Ilsabet's `turning_sweep` 14 s); C1-C4 choose the real ones.

## F3 decisions on the fight rules (2026-11-13)

F3 (the boss rules, the move selector and the decal geometry, HEROES_V2.md 15.2 "F3 as built") decided the points of
this list that are rules of a fight, in the spirit of HEROES_V2 6; C1-C4 still choose the content.

- **4, ground marks: where each shape lies and how large it is** (`logic/hero/Decals`). A mark covers the ground the
  move can hit, never less, so whoever stands outside it is safe:
  - a disc is two squares, the second turned 45 degrees, of side 2r cos 22.5 degrees: the star they draw never falls
    inside the circle of radius r (its points reach about 31% beyond it);
  - heavy self moves (`cat_reflex`, `mirage`, `seven_talismans`, `shadow_step`, `shadow_clones`) and self-state
    ultimates (`midnight_frenzy`, `nightfall`, in red): a disc on the hero's own ground, its radius or at least 1.5;
  - heavy melee (`guard_splitter`) and combo moves: a disc in front of the hero out to its reach (3.5) plus its splash;
  - chains (`chain_draw`): a disc around the target of the chain's jump radius (where the lightning can spread);
  - counter stances (`iai_stance`): a ring at the reach it answers (4 blocks) around the hero;
  - sweeps (`turning_sweep`, `purifying_dance`): a fan of ribs from the hero across the arc, one per 30 degrees,
    3-5, its range long; cones the same across their angle; a volley's fan its arrows' spread (10 degrees apart);
  - waves: a ring of 6-12 strips tangent outside the furthest front; leaps: a disc of their radius where they land;
    rain: one disc of 1.5 blocks per impact (6.5's "1-2 blocks", PROPOSED);
  - strips from the hero along the aim: a line its width, a dash, charge or grab one body wide, a shot, bolt or
    barrage a narrow track;
  - strips grow from the hero outward, discs and rings from their centre; at most 12 pieces per hero (a disc falls
    back to one square of side 2r, a fan or a ring thins, the rest waits).
- **8, the back bonus outside recoveries**: the "+15% more from behind" is part of the punish window (6.9: "during
  recovery ... and +15% more from behind"), so a hit from behind outside a recovery lands as one from the front; a
  `back` weak point doubles it inside the window (Ilsabet's Bulwark-doubled back is a content number for C4).
- **Reactions**: each occurrence of a reaction's condition gets one roll of its chance (a condition held on gets a
  new roll each time the reaction's cooldown passes), so a 50% reaction answers about half the time instead of almost
  always over the six checks of a gap; the answer replaces the next pick and waits for the gap (never before the
  opening gap); its cooldown starts when the answer starts.
- **Followups**: a followup needs its own charge and reach but not the gap or the 1 s between starts; a move
  repeating itself in a string is the same use (zoomies' four bounds and broom_hops' three hops cost one charge).
- **Postures** (6.3's "offensive" and "zoning and defensive" moves): self, ally, allies and counter moves are
  defensive; zones, waves and rain zoning; every other form offensive.
- **The `phase` condition** (6.6) is the current phase exactly ("phase 2" is true in phase 2 only).
- **Poise under super armour**: during a transition, an ultimate's wind-up or a `super_armor` move the bar fills to
  just short of its maximum, so the first hit after the protection staggers; during a stagger poise damage is
  ignored (no stun-lock), and each stagger raises the maximum by 20% for the rest of the fight.

## F4 decisions on the leader rules (2026-11-13)

F4 (personality, the leader's director knobs, the draft, names, colours, banners, base styles and talents,
HEROES_V2.md 15.2 "F4 as built") checked the eight kit sheets' leader numbers against `LeaderBiases`: every number
of every sheet agrees (`LeaderBiasesTest.theSliceKitSheetsAgreeNumberForNumber`). It decided the points left open:

- **11, rounding in the knobs**: a half rounds up, below zero too (-0.5 is 0). The sheets' open halves land where
  they printed them: Hinae's trader at 11.5 is 12, Kagenori's 17.5 is 18, Ambrose's 13.5 is 14, Ambrose's repair
  budget 395.5 is 396.
- **9, talents beside the knobs**: `sage_of_letters` and the leader's enchanter level take the lower (Ambrose: 8 from
  his ambition, 7 from the talent at tier I: 7); `warden_of_walls` multiplies the leader's repair budget (Ilsabet: 407
  x1.5 = 611 at tier I). The other points of 9 (Presence for non-guards, the spymaster specialty, the backpack sizes,
  talents while their hero trades away) stay with L5m.
- **The eccentric genius rule** ("crafting and lore projects first") stays undefined until projects have a priority
  list (L4); it changes no knob.

## F5 decisions on mobility and work mastery (2026-11-13)

F5 (`MobilityRules`, `LeapSolver`, `FlightPlanner`, `HeroLinks`, `WallEntry`, `EscapeRules`' last parts,
`WorkMastery` and the `dive` form, HEROES_V2.md 15.2 "F5 as built") decided the points MOBILITY_AND_WORK leaves open:

- **Leap limits**: a hero's vertical impulse is bounded by its jump height and its horizontal impulse by the slowest
  leap over its widest gap on level ground, so a 3-block leaper cannot carry 8 blocks on a flat, fast leap. A leap
  over a rise needs about 0.07 more vertical, so a leaper whose jump is at its floor may lack the rise its gap allows.
- **Who can reach whom** (the reach clock's ground input, 5.1.15): an ordinary person, a player or an NPC soldier
  alike, walks, steps up 1, towers up 2 and steps over, drops 3, swims and sprint-jumps a gap of up to 3 landing no
  higher; an enemy hero adds its own links (fight rules: no risky gap). The search stays within 32 blocks.
- **The strike from a link** (3.7): from a cling when the hero climbs to the cell under the target's feet, from the
  apex when the target is within 3 blocks of the top of its jump with its air jumps, from the air for a flyer.
- **The cruise layer** looks one segment ahead: a point clears the segment it ends and the one it starts, so the hero
  is over an obstacle when it reaches the obstacle's station; the ceiling is measured over that terrain, so the
  6-10-block layer always fits under it. A waypoint with no ground route at all counts as a detour (fly).
- **The way in** reads the hero's whole mobility (a climber secondary climbs like a primary) in the order flight, climb,
  a jump onto the top, a glide; a charger leads the breach only when the gate does not open for soldiers. A flyer's
  flight over the wall must fit the raid's 10 s per take-off at the powered drain.
- **The goal inside**, in order: open the way while the party is held outside; hunt the home hero for a
  `heroes_first` focus with a home hero known to be in, or when one is seen inside; clear the archers for a
  `marksman` role or a `zoner` duel style when the base has archers; the storehouse for greed 65 or more; the heart.
- **Proficiency** applies at the hero's duty only (its backpack too); on guard duty the alarm reach replaces the work
  speed and the rest applies. Thrifty and Hardy multiply with the proficiency's cut (then the 40% and 50% floors).
  An `aura_<affix>` hero counts its own stacks within the cap plus 2 / 3 / 4.
- **One or the other** (13): the expected count with the virtual levels against the tool's own (ores vanilla's
  1 + F(F+1) / 2(F+2), crops the binomial bonus of carrots and potatoes, leaves the sapling chance, kills 1 + L/2)
  against 1 + the roll's chance; a tie goes to the roll. Luck of the Sea and Lure change no count: the main catch
  carries them and a second catch rolls with the real rod.
- **Presence** lifts the base-wide part only; personal effects stay at the hero's own tier; a quantity 11.2 gives no
  tier-IV number for keeps its tier-III value (tactician's "25%" is its joint-raid cut).
- **The dive**: `from` defaults to the air for a hero that flies or glides, else the apex; "a grab in an aerial
  window" is a dive that names a grab as a followup, and that dive is NOT LOADED (the grab stays for the ground); a
  dive ultimate left out takes its awakened twin; with no `frames` its recovery is 1.0 s silently, a shorter authored
  one is raised (ADJUSTED). The dive counts as an area form (a landing radius) and swings in its look.
- **The scout's departure**: enemy soldiers seen, or under 40% among monsters, send it home (a retreat); a monster
  about or in the way makes it step aside and go on; both use an escape's movement only.
- **Morale** of an escape or a death counts at the hero's home base, a leader's at every base of its faction; nothing
  anywhere else.

## Review of F1-F5 and A1b (2026-11-13)

The review (HEROES_V2.md 15.2, "Review of F1-F5 and A1b") compared the eight slice fixtures with SLICE_PLAN section 3
move by move and checked what the format now decides. Where each point of the list stands:

| Point | Decided by the format | Still open (who decides) |
|---|---|---|
| 1, followups | all (F2) | - |
| 2, values not set | the defaults (F2) | each hero's real cooldowns, weights and lines (C1-C4) |
| 3, boss bar title | the fallback (`boss.intro.title`, the title, the name) | each hero's title (C1-C4) |
| 4, ground marks | all (F2 shapes, F3 geometry) | - |
| 5, ambiguous numbers | zones, combo strikes, the back bonus (F2) | which strike bleeds, Benika's waves (C1, C2) |
| 6, durations and stacks | - | all (C1-C4) |
| 7, reactions in the wrong phase | all (F2) | Kagenori's phase 3 list (C3) |
| 8, weak points | `channel`, the back bonus window (F2, F3); `poise: 0` (below) | rate limits, hints, the broken-channel outcome (C1-C4) |
| 9, work mastery | the knobs and talents beside them (F4), proficiency at the duty and Presence (F5) | the spymaster specialty, backpack sizes, talents while their hero trades away (L5m) |
| 10, raid goals and refuge | all (F5, no yield) | - |
| 11, rounding | all (F2, F4) | - |
| 12, enrage | all (F2) | - |
| 13, Ambrose | aerial phases, the dive form (F2, F5) | broom_dive (C3), the bristles (art) |
| 14, Ilsabet | - | her shield, her phase 3 line, her base style (C4) |
| 15, idle animations | - | all (art) |

What the review decided or fixed in the format and the fixtures:

- **"By melee"** (Yugen's `contract`, `on_hurt` by melee 25%): written as the passive's `if` with `target_close` at the
  close band's 4 blocks (for a passive the target is the attacker, v1 11). The fixture had lost it, so an archer's
  arrows would have marked the archer.
- **The great brew** rains for 2 s after its 3 s channel (`duration` 2; the fixture had 3). "One of his three effects
  at random" has no pick-one list in the format: the fixture gives each effect a 33% chance (0 to 3 effects per
  splash, one on average). C3 decides whether that is close enough or the format needs a `one_of` list.
- **Ilsabet's `rime_wall`** shields allies within 6 but taunts within 5: one move has one radius, so the fixture uses
  5 for both. C4 decides (a radius on the shield effect, or 5 for both).
- **`boss.poise: 0`** (12.10, a hero with another weakness) is a hero with no poise bar: it never staggers
  (`BossRules.Poise.none`); before the fix the rules would have staggered it on every hit.
- **A dive closes the gap** for 6.12's reach NOTE, as a leap does.
- **An operator's override that would break its hero** is left out and the built-in hero loads (`override_breaks`).
- **Unknown words of long lists** (archetypes, talents, format-2 conditions and line events, banner patterns) name the
  document that lists them instead of HERO_FORMAT's Appendix B.
- Open for L4: 6.7 gives every leader three phases; a rare or common hero that leads still has its file's two.
