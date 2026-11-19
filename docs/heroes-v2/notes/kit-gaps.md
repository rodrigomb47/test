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

## A3 decisions on the ability core (2026-11-13)

A3 (the moves in the game: `HeroCombat`, `HeroAbilityGoal`, `HeroEffects`, `HeroDamage`, `HeroStatus`, the forms,
projectiles and zones; HEROES_V2.md 15.2 "A3 as built") decided what the kits and the format leave open:

- **`target_behind` has two readings**, as the kits use it: for the move choice and reactions it is a target standing
  behind the hero (Yugen's and Ilsabet's "behind him/her"); in an effect's `if` it is the hero standing behind the
  receiver (Tobimaru's backstab, "60% stronger when he is behind you"). An effect's `if` is checked for each receiver,
  the receiver as the sense's target.
- **Only whole moves run.** A move whose form or any effect is a later task's is never picked, answered or followed
  until then (`EffectRouting.runs`), so nothing plays half a move. Waiting for A4 (stun, taunt, dispel, empower,
  summon) and A5 (charge, wave, rain, sweep, grab, counter, barrage and the format-2 effects such as barrier,
  guard_break, spawn_add, call_guards): Ambrose's potion barrage and both rains; Benika's charge, stomp, grab and
  crash waves; Hinae's sealing circle, exorcise, seven talismans and dance; Ilsabet's bash, rime wall, sweep, lance
  charge and the call; Kagenori's iai stance and guard splitter; Natsuho's cat reflex, zoomies and midnight frenzy;
  Tobimaru's clones and log swap; Yugen's mirage, foxfire rain and ninefold lantern. The rest of every slice kit
  runs now (Natsuho: claw string, pounce, hiss, scratch dash).
- **Allies include the hero.** `to: allies` and the `ally` and `allies` forms count the hero as one of its side (v1
  10.2), so healing or shielding itself fills its energy as healing an ally does (v1 14, 1 per 2 points, at most 8 a
  use). `to: allies` in a nova, zone or leap reaches the allies inside that area; elsewhere those within the
  effect's `radius` of the hero (8 when it gives none).
- **Ranged basic hits.** A bow or crossbow hero's own arrows are tagged; one that lands is its basic hit (5 energy,
  `on_hit`, the n-th hit's finisher), as a melee blow is. A `thrown` hero throws its projectile look (a vanilla item
  until A9) for its `amount` plus its attack bonuses (attack damage above the base 1), of the basic attack's element
  or its own.
- **Cancel and interrupt.** A target that dies, goes or moves more than 2 blocks past the move's range during the
  wind-up calls the move off with its charge kept (v1 10.3); a wind-up stopped by anything else (the goal losing its
  turn; A4's stun and A6's escape will take the same path) gives back half the cooldown.
- **What a save keeps**: every move's charges and recharge as game times, the last ultimate, energy, the basic-hit
  count, and every status on any entity (attachment `factionclash:hero_status`, expiries as game times, stat
  modifiers put back on load). The fight's own clock (gap, string, reactions), running moves, projectiles and zones
  are not saved: a reload ends the fight. A recharge saved longer than the move's cooldown now (a reload that
  shortened it) comes no later than that cooldown.
- **Hazard-aware pushes for everyone** (6.12): a knock-back that would put any receiver, player or NPC, in lava or
  over a drop of more than 3 blocks is cut to a third; a knock-up lifts only a receiver on the ground, at most 1 block.
- **Looks for now**: projectiles look like vanilla items, zones and novas are particle rings, a wind-up shows a smoke
  ring where a nova, zone or leap will land, an ultimate with a `lightning` look calls a visual-only bolt. The decals,
  the timing files' frames and custom models are A5, A6 and A9's.

## A4 decisions on control, passives, states and behaviour (2026-11-13)

A4 (stun, taunt, dispel, empower and summon, states, passives, the target goal, spacing, duties, base abilities and
combat XP: `HeroStatus`, `HeroStates`, `HeroPassives`, `HeroSummons`, `HeroTalents`, `HeroControlEvents`,
`combat/HeroTargetGoal`, `combat/StunnedGoal`; HEROES_V2.md 15.2 "A4 as built") decided what the kits and the format
leave open:

- **A worker duty makes the hero that worker.** 3.2 gives a hero its style's soldier role; with a worker `duty`
  (fisherman, miner, woodcutter, ...) its role is now that worker (`HeroLoadout.dutyRoleId`): it counts toward the
  base's needs and roster as one, takes the duty's tool from the storehouse and works the job between fights. Its bound
  kit weapon goes to its backpack while it holds the tool (never dropped, never lost) and is drawn again within 5
  ticks of having a target. It still fights like a soldier (no civilian flight, no half-health stand-down, no
  withdrawal), and alarms call it whatever its duty. `guard` and `court` keep the style's soldier role. The sheet shows
  both ("fights as X, works as Y").
- **A stun on a player** leaves the camera free and lets a knock-back move them, as it does an NPC; everything a
  player could do with their hands (attack, use, interact, break, place) is cancelled while it lasts. A stunned NPC's
  or player's melee blow is cancelled as damage too, so nothing slips through between the two.
- **A taunted hero glows for everyone** (vanilla Glowing, presentation only): the taunted player sees whom to hit, and
  so does everyone else.
- **Healing XP is for others.** Healing or shielding another (player or NPC of its side) earns 1 XP per 5 points,
  the remainder carried to the next heal; healing itself earns none (energy still counts self-healing, A3).
- **Passive triggers raised inside another entity's hurt, heal or shield block answer on the hero's next tick**
  (`on_ally_hurt`, `on_ally_low_health`, `on_ally_attack`, `on_target_heal`, `on_guard_broken`, and `on_hit`, which a
  hero's arrow raises inside its target's hurt); the hero's own events (`on_hurt`, `on_low_health`, `on_crit` after
  its blow has landed, `on_kill`, `on_skill`, `on_ultimate`, `on_mark_full`, `on_fight_start`) answer at once. A passive
  never deals damage inside another entity's hurt still being worked out, which would overwrite that victim's last
  damage source and break vanilla's hurt bookkeeping. One tick (50 ms) is not visible.
- **Melee keep distance.** v1 8 says a ranged hero backs away inside half its keep distance; for a melee hero with a
  keep distance that is no skirmisher (a support or controller with a reaching move) A4 reads it as: hold that
  distance while a move of its that reaches that far is ready, back away inside half of it, close in to melee when
  nothing is ready. A skirmisher steps 3 blocks back after each landed blow and comes in again when its swing is
  ready.
- **The base abilities are the five talents** (`aura`, `rally`, `drill`, `mend`, `lookout`), with 7.2's tier numbers
  and its "strongest copy at a base" rule, at the hero's home base and never on a raid. `mend` heals players and NPCs
  of its side alike.
- **A won raid's XP waits for heroes on raids.** Heroes are never drafted into a raid party yet (A1a), so the 100 XP of
  a raid won is in place but no hero earns it until L4/A12 send them; a defence that held pays its 30 now.
- **Triggers wired later.** `on_phase`, `on_enrage`, `on_stagger`, `on_counter`, `on_intro`, `on_escape`,
  `on_last_stand` and `on_add_died` are read by `PassiveBook` now; A5, A6 and A12 call `HeroCombat.trigger` when those
  events exist.
- **What a state shows.** Entering plays its `state.<n>` clip through `DATA_ACTION` and holds its face; the bones it
  shows or hides need a client state flag and are A5's with the timing files. `guard_break` is a format-2 effect (A5).
- **Moves that run now.** With A4's effects in, these slice moves join those A3 ran: Hinae's sealing circle and
  exorcise, Ilsabet's shield bash and rime wall, Natsuho's Midnight Frenzy. Still waiting for A5's forms and effects:
  Ambrose's potion barrage, great brew and grand experiment; Benika's shoulder charge, stomp, grab and crash waves;
  Hinae's seven talismans and purifying dance; Ilsabet's turning sweep, lance charge and the call; Kagenori's iai
  stance and guard splitter; Natsuho's cat reflex and zoomies; Tobimaru's clones and log swap; Yugen's mirage, foxfire
  rain and ninefold lantern.

## A5 decisions on the move runner, the new forms and the new effects (2026-11-13)

A5 (the move runner, format 2's forms and effects: `HeroCombat`, `HeroMoveEvents`, `HeroStatus`, `entity/HeroDecoy`,
`logic/hero/MoveTiming`, `MovePlan`, `FormGeometry`, `StatusBook`; HEROES_V2.md 15.2 "A5 as built") decided what the
kits and the format leave open:

- **Frames come from the clip the client plays.** A move's wind-up, active part, recovery and hit ticks are read from
  the hero's timing files, through the same animation chain the client plays from, for the clip its `DATA_ACTION` code
  names (`move.<id>` by the move's place in its `visual.json` list, `ultimate`, `ultimate.awakened`); the clip's `hit`
  markers are the hits when there are as many as the form has (a combo's strikes, a barrage's shots). Without a clip the
  move's own frames are used and it holds A4's telegraph pose, so the test rig and the fixtures still run.
- **One speed for the clip and the plan.** Haste and Mining Fatigue move it by 10% a level (vanilla's attack-speed
  share), Slowness by 15% a level (vanilla's walking share), the `attack_speed` stat by its percent, 0.5-2.0. It is held
  so that neither the wind-up nor the recovery ends under its class minimum, then rounded down to a `DATA_ACTION` speed
  step, so the client's clip and the server's plan divide by exactly the same number; the plan's ticks round up (the
  server never lands a hit before the clip shows it).
- **What stops a move**: a stun or a stagger at any stage, a knock-back stronger than 0.5 after the hero's knock-back
  resistance in a wind-up (a plain blow is 0.4, a sprinting one 0.5: neither; a `super_armor` move and an ultimate shrug
  it off), damage of a channelled move's `channel` share in its wind-up or active part (a 2 s recovery follows, nothing
  comes back: it is a punish), an escape (A6/A12: a wind-up is cancelled, an active part plays out its tick), death.
  Half the cooldown comes back only for a wind-up stopped by force. A target that dies, goes or leaves reach in a
  wind-up still calls the move off with its charge kept (v1), except a grab (reaching for someone who stepped back is
  the miss the kit describes, with its 1.2 s recovery) and a counter (its stance answers whoever strikes).
- **The channel share is the move's own**: a move with `channel` breaks at its `interrupt` share (15% when it gives
  none); the weak point `channel` of 6.9 names the same thing on the sheet (A6b may give channelled moves without the
  field a default).
- **Charge**: 100% speed is 5 blocks a second (about a sprint), so Benika's 160% shoulder charge runs 10 blocks in 25
  ticks and Natsuho's zoomies 14 blocks in 32; it runs straight along its aim at release (a target that sidesteps in
  the wind-up is missed), hits each enemy within 1.5 blocks of its path once and stops at a wall, a drop, water or lava.
- **Wave**: the ring is centred where the hero stood at its release and rolls on by itself (a stun after the release
  does not stop a ring already rolling); it reaches feet within 1.5 blocks of its height; one hit per wave and receiver.
- **Rain**: each impact is called at its turn of the duration around where the target stands then (the rain follows
  its target), on the ground there, and lands its `delay` later (never shortened by speed: it is the tell); it hits
  enemies within 1.5 blocks; the use's caps are a zone's (x1.5), as the balance already counted.
- **Sweep**: the line turns from -arc/2 to +arc/2 of the facing at its release, evenly over its duration (scaled by
  speed); each tick it covers the wedge since the last, so a fast sweep skips nobody; a block between the hero's eyes
  and a receiver stops it there.
- **Grab**: reach is measured to the target's box; the hand is a block in front of the hero, 0.6 up; the hold is a
  velocity toward it each tick (half the gap, at most 0.8 a tick), never a teleport; the throw tries straight ahead,
  then 45, 90 and 135 degrees to either side, then behind, and takes the first whose path and landing hold no lava, fire
  or deep drop (6.12, the same for every receiver); with none safe it throws ahead softened to 0.5. The move's effects
  land at the throw ("hurls you away in a burst of fire"), unblockable.
- **Counter**: the stance is the wind-up; the first melee blow from an enemy in it (an arrow only with `ranged`) is
  caught after shields and armour: its `absorb` share goes, the riposte (the move's effects on that attacker) answers
  on the hero's next tick (A4's rule: nothing strikes into a hurt still being worked out; 50 ms) and the move goes on
  to its active part and recovery; a hero's damage over time is no blow. A stance nobody hits releases nothing and plays
  its full recovery (Kagenori's weak point).
- **Barrage**: every shot carries the move's `effects` and its `per_shot` list in turn (Ambrose's 3 damage on each vial
  plus poison, slowness, weakness), all one use under one cap.
- **Unblockable and `breaks_guard`**: an unblockable move (every grab) goes through a raised shield, a player's and an
  NPC's alike; a `breaks_guard` move knocks a raised shield aside for 5 s (the axe rule) unless it has its own
  `guard_break`, whose seconds then hold (Kagenori's guard splitter: 4 s).
- **`guard_break`** lands only on a raised shield; the receiver's status keeps any shield from going up for its seconds
  (an NPC that tries every tick and a player alike), and a player's shield also gets vanilla's cooldown (the hotbar
  shows it).
- **`tether`**: to the spot where the receiver stood when it landed; past its length the receiver is pulled back each
  tick (at least 0.15, at most 0.6 blocks a tick), whoever it is. Cleansed like any harmful status.
- **`silence`**: on a hero it blocks new moves (picks, reactions and followups) for its seconds, never a move already
  running; on anyone else it changes nothing. Cleansed like any harmful status.
- **`poise`**: fills an enemy hero's poise bar (nobody else has one); the stagger it can cause is the hook A6a
  completes (A5: the move is interrupted, no move for 2.5 s, the `stagger` clip, `on_stagger`). A charge into a wall with
  `wall_stun` fills the hero's own bar by 40. A new fight empties the bar.
- **`barrier`**: blocks all damage to the hero but `/kill` and the void; a blow from anyone (damage with an attacker,
  not a hero's damage over time) costs it one hit, fire and falls are blocked without counting; a dispel takes it, a
  cleanse leaves it. With a `focus` add (milestone 1: a decoy) its last hit holds while the focus lives, and killing the
  focus breaks it and staggers the hero.
- **`spawn_add`**: milestone 1 spawns decoys only (a move with a spirit add is not picked until A8); at most 3 a hero, a
  new one popping the oldest; a decoy is of its hero's side (an enemy hero's moves strike it, its own hero's never), has
  1 health, pops at any hit or after 10 s, walks to a spot 3-5 blocks away, attacks nobody, drops nothing and is never
  saved; its pop is `on_add_died`. Its look (the hero's own model, its action 2 ticks late, no shadow, no footsteps) and
  NPC soldiers taking it as a target are A7's; until then the client draws a plain humanoid.
- **`call_guards`**: up to `count` real soldiers of its faction within 32 blocks (never heroes), the free ones first,
  then the nearest, take its target; a blinded soldier answers only within its sight; nothing is spawned.
- **Moves that run now**: every move of the eight slice fixtures (no slice hero has a `dive`, which waits for A12).

## A6a decisions on the boss controller's first part (2026-11-13)

A6a (the fight clock, the hurt cooldown per attacker, the cap, poise and the stagger, phases and decals: `HeroBoss`,
`HeroBossEvents`, `HeroTelegraphs`, `combat/HeroHoldGoal`, `client/hero/HeroDecalRenderer`, `logic/hero/BossRules`,
`Decals`; HEROES_V2.md 15.2 "A6a as built") decided what the design and the kits leave open:

- **Two fights, one for the rules and one for the lines.** The boss rules (the move gap, poise, once-per-fight passives,
  `enemies_engaged`) run on 6.1's fight of engaged enemies; the lines (`fight_start`, `victory`) and the name tag keep
  v1's fight on the hero's target, so what a hero says did not change.
- **An attempted blow engages.** An attacker is engaged from the moment it strikes at the hero, even when the barrier
  stops the blow; an ally's stray blow is remembered but never counts, being no enemy.
- **Vanilla compares, the ledger remembers.** The hurt cooldown per attacker sets the attacker's own record on the entity
  and lets vanilla's own check decide (it compares the amount after shields and the incoming hooks, which the game alone
  sees), then stores what vanilla made of it.
- **Where the cap sits**: after armour, resistances and a counter's absorb (the damage event), before absorption hearts;
  the windows multiply first (6.9's order: "the damage-taken factor, then the per-hit cap"). A hero's moves on another
  hero are capped too; only damage with no attacker and `/kill`/the void are not.
- **Poise from what struck**: a melee blow's weapon is the attacker's main hand, a projectile's is the projectile (a
  thrown trident, an arrow, a crossbow bolt); a hero's move counts 1.0; a hero's damage over time fills no poise (as it
  costs a barrier no hit). The weak points `iron` and `gold` come with this path (they need nothing but the attacker's
  main hand); A6b keeps the event weak points.
- **The parry belongs to the move, not to the hit**: any shield block of a heavy or heavier move that is running, a
  player's or an NPC's shield up 5-11 ticks, lands 25 on the hero's next tick (a hit inside the blocker's hurt changes
  no hero state).
- **A transition waits for a free hero** (6.3 puts TRANSITION beside NEUTRAL): never inside a move, a stagger or a stun,
  so a move's recovery (the punish window) is never cut short by super armour; a phase reached meanwhile plays its
  transition as soon as the hero is free, and two thresholds crossed together play one. The look changes when it
  begins; the stats, the state and the adds come when it ends.
- **Phase stats add up, phase states replace.** Every phase reached adds its own stat bonuses (Kagenori's phase-2 armour
  stays in phase 3); a hero is in one state at a time, so the deepest phase that names one enters it, and a phase that
  names none keeps the one it has (Benika's rage from phase 2 on). A phase's state lasts the rest of the fight: an hour,
  or until the re-arm takes it away.
- **The shockwave pushes everyone near** but the hero, its own decoys, display copies, armour stands and players outside
  the fight's modes (creative, spectator): allies too, with no damage, as 6.7 says "everyone".
- **Healing waits for quiet**: 30 s out of a fight and 30 s since the last damage of any kind, so lava or a fall with no
  enemy near does not start the healing at once.
- **Decals are drawn by the client** instead of 6.4's block displays: the server sends the shapes, colour and wind-up,
  so nothing is spawned in the world, nothing outlives a crash or is saved with a chunk, and no access transformer is
  needed; each shape lies on the ground under its centre near the hero's or the target's height. A rain draws nothing in
  its wind-up (its impacts are not known yet) and one disc per impact as each is called (F3's "one disc per impact").

## A6b decisions on the boss controller's second part (2026-11-13)

A6b (reactions, the event weak points, enrage, the arena, the intro, the bar and title, the end of a fight and the
rewards, the operator's hooks: `HeroBoss`, `HeroEscape`, `HeroBossBars`, `HeroRewards`, `HeroBossEvents`,
`combat/HeroEscapeGoal`; HEROES_V2.md 15.2 "A6b as built") decided what the design and the kits leave open:

- **8, the douse's rate and what comes back.** A snowball and water douse at most once in 20 s (Natsuho's sheet gives
  20 s for water; the snowball uses the same). A fire hero's state ends (Benika's Rage); a state a phase holds comes back
  when the douse's 5 s end (she rages again), a state from a move or a passive does not. A hero that is not of fire
  flinches 1 s instead (Natsuho's "flinch"): a wind-up is cut as a stun cuts it. Both deal 10% less for 5 s.
- **8, light.** Tobimaru's 10% is less damage dealt (as the douse), for 10 s, renewed by each new light, no other
  limit; only an enemy's placement counts (a player's or an NPC's torch alike).
- **The bell** is any ring of a bell (a hand, a projectile, redstone, an NPC through the bell's own ring), as 6.9 says
  "by anyone"; 1 s stun through the receiver's stun immunity, once in 30 s.
- **`crit_above` and `charge_wall` stagger at once** without filling the bar; neither staggers in a transition, the
  intro, an ultimate's wind-up or a `super_armor` move, except that a charge into a wall staggers through its own
  charge's armour (it is that charge that ended).
- **Channel defaults are not needed**: every channelled move of the slice authors its `channel` field, so a hero's
  `channel` weak point gives no default share (left for a future hero that needs one).
- **The intro** is looked for during a fight's first 3 s once the hero is free (no move, stagger, stun or transition),
  and only when a person (a player, a faction NPC, a hero) is engaged; the day is the world's day (`dayTime`), so
  sleeping through a night brings a new day. It holds the hero still with super armour; its length is its `intro`
  clip's, held to 1.0-1.5 s (1.25 s without one).
- **Enrage's +25% cap**: the enrage adds what is left of +25% after the hero's other damage bonuses at that moment, at
  most 15; a bonus that comes after it is not trimmed. Hard triggers enrage only a hero in a fight; `favoured_hurt` reads
  a friendly bond (friend, sibling, mentor, student, admirer) in its relationships until leaders (L4) add "its leader".
- **The end of a fight** is read after every hit that lands and once a tick while fighting; an operator's `escape`
  starts one whatever the hero's kind. The stand at home is inside its home's walls plus 16 blocks while the heart stands;
  "its leader fighting near" waits for leaders (L4), and the rebirth for its legendary heroes (none in the slice), so
  neither holds anyone yet. A lost base sends an escaping hero there off at once (`baseLost`).
- **Running on foot**: the escape heads for its refuge's spawn point or, in the wild, 72 blocks from its enemies'
  middle (so "away" is away from them), with the hero's own navigation a waypoint at a time (never the stuck recovery's
  hop); it is cornered after 3 s with no way (no path, or under half a block moved in a second) and then fights the
  nearest pursuer within 16 blocks; a way is looked for every second. Those it fled are not taken as targets for its away
  time and until it is above half health. Its fight ends at once, so no bar, intro or move while it runs. A12 swaps the
  movement for its mobility.
- **The winning side** is the last person whose blow hurt it in its last 15 s, the killing blow included; a factionless
  player is a side of one. The crest is a registered item (`factionclash:hero_crest`, named "Crest of <name>", its
  title and "Won by <winner> on day N"), once per hero per side per in-game day, kept in `HeroState.crests`.
- **Lines**: the intro, enrage, escape and last-stand lines ignore the dialogue cooldown (each comes at most once a fight,
  a day or ten minutes), so a skill's line a moment before never hides them.

## Review of A3-A6 decisions (2026-11-13)

Taken in the review of A3-A6 and S2 (HEROES_V2 15.2 "Review of A3-A6 and S2"), each to make the game do what the kit
sheets say:

- **Followups of a move that reaches no enemy.** 6.3 says a followup may start "when a move hits"; a `self`, `ally` or
  `allies` move has nobody to hit, so it counts as landed once its active part has played. Natsuho's Midnight Frenzy goes
  on into Pounce, Tobimaru's Shadow Step into Backstab and Yugen's Vanish into Foxfire Volley (70%), each when the
  followup has its charge and its reach then. A followup still starts at once or not at all: Shadow Step taken from mid
  range ends its string when Backstab (close) is out of reach as the step ends, which is for C3's content or A12's
  movement to settle.
- **The allies of an area move.** v1 10.2 sends `to: allies` in a nova, zone or leap to the allies inside that area; an
  effect that gives its own `radius` now reaches that far from the area's centre (Hinae's rite heals and shields within
  10 while it strikes within 6; Ilsabet's vow shields within 8 while it freezes within 6). Without a radius the area's
  holds. Ilsabet's Rime Wall (shields within 6, taunts within 5) can now be written as the kit says: radius 5 for the
  move, `radius: 6` on its shield (C4).
- **A sweep's gifts to its side** land as it starts, so Hinae's dance regenerates her allies whether or not the line
  passes an enemy; the line's own effects reach only the enemies it passes, tick by tick (each tick that strikes renews
  the allies' effect).
- **Zones ignore shields** (SLICE_PLAN 3.4): a zone bursts from the ground under its receivers, so its damage goes through
  a raised shield, the same for a player and an NPC. Waves and rain keep vanilla's shield test (nothing in the kits says
  otherwise).
- **A hero's food bar does not heal it.** Its health comes back by the reset of 6.10 (1% of its base a second, 30 s out
  of a fight and unhurt), never in a fight (6.2), and by authored heals, drains and potion effects (a golden apple's
  regeneration included). It still eats, so it never starves. A dormant hero heals from food as the plain soldier it is.
- **No stun-lock**: poise damage is ignored during any stagger, whatever caused it (a full bar, a weak point, a broken
  barrier), so a second stagger never extends a kneel.
- **A fight's opening blow keeps its poise**: a new fight still starts from an empty bar, but poise that landed in the
  last 2 ticks (the blow, or the parry, that started the fight) is kept.
- **A barrier's focus** is the last of a hero's decoys to go when its own `spawn_add` keeps the cap of 3; killing it
  staggers the hero on its next tick through 6.9's protections (not in a transition, the intro, an ultimate's wind-up, a
  super-armour move, or while it already kneels).

## Live check of the combat engine (2026-11-13)

Seen with Natsuho's and Hinae's kits on the test rig on a dedicated server with the real client (HEROES_V2 15.2
"Combat engine in game"); left for the owner or C content:

- **Five basic soldiers kill a slice hero in about 10 s.** Natsuho at level 2 (118.9 health) lost about 11 a second to
  five Ember swordsmen (stone 4.96, iron 5.95 a blow): each soldier lands within its own hurt window (6.2), and nothing
  in 6.15 sets a hero apart against a squad. Whether a slice hero should last longer against basic soldiers is a
  balance call (6.16 measured one player against heroes).
- **A thrown hero keeps a melee weapon's damage in its throw.** `attackPower` for `thrown` is the amount plus the attack
  attribute minus 1, so Hinae's feathers took the test rig's iron sword (+5) when her kit was worn over the rig's gear.
  A thrown hero's own file should give no melee weapon (C content), or the rule should read the hero's bare attack.
- **Natsuho's `water` weak point on fisherman duty.** She wades in to fish and flinches (at most once in 20 s; out of a
  fight it costs only the flinch). In character for a cat; C1 may prefer a fishing stand on the bank.

## A10 decisions on the mobility engine (2026-11-14)

A10 (`HeroMovement`, `MovementRules`, `HEROES_V2.md` 15.2 "A10 as built") decided the points MOBILITY_AND_WORK leaves open:

- **The leap's take-off tick is airborne** (vanilla's travel would drag it by the block's friction, 0.546 on stone):
  the solver's arc is then the real one from any block, ice included, and a GameTest measures it.
- **Air poise counts the hero's own airtime only** (flight, a glide, its leap, jump, air jump or wall kick, the
  mantle): a hero lifted by a blow's knock-back or falling off a ledge is not "in the air" for 2.4. A stagger in the air
  is a downing only then, too.
- **Wall kicks do not spend air jumps**, but never kick off the same face twice in a row: a chimney is climbed, one wall
  is not.
- **`DATA_LOCO`'s one-shots**: each mode and kind names one clip (`HeroClips.oneShotClip`), and the one-shot keeps its
  naming mode for its length, so a wall kick is `cling` + special, the mantle `climb` + special, the landing roll
  `ground` + special and the heavy landing `airborne` + end.
- **A glider opens its glide by itself** when it falls with 4 blocks or more under it (1.9's launch height), a flyer
  too; this is the one mobility action that runs in a campaign before A11 and A12 drive the rest.
- **The fight's air budget and ceiling are on from A10** for any flight in a fight, against any engaged enemy, so no
  flight escapes them before A12's aerial windows.
- **A heavy landing without the roll slows** to half speed for 0.5 s (1.6's "landing roll: no landing slowdown" needed a
  slowdown to spare).
- **The Multishot side bolt** is the bolt that cannot be picked up (vanilla gives the side bolts creative-only pickup).

## A11 decisions on navigation (2026-11-14)

A11 (`HeroNodeEvaluator`, `HeroGroundNavigation`, `HeroTravel`, `HEROES_V2.md` 15.2 "A11 as built") decided the points
MOBILITY_AND_WORK 3 leaves open:

- **A ditch 2 or more deep is a gap.** 3.2 offers links only where a worker's step fails; a worker can drop into a 2-deep
  ditch but nobody climbs out, so a leaper walked round ditches it could leap. Gap links are now offered over them too
  (`HeroLinks.DITCH_DEPTH`).
- **A climb down** joins 3.2's links: from a ledge over a face deeper than 3 and than the hero's safe fall, within its
  climb height. Without it a climber I could climb a 6-high wall and not get down the other side.
- **Jump-ups are a straight jump, not a solved arc.** A leap into the next cell 1.5 blocks up runs into the ledge's face
  for every horizontal speed the solver allows, so a jump-up is the crouch, a jump with the hero's jump strength
  pressing toward the ledge, an air jump at each apex under its top, and a step over the edge.
- **A link's action is the navigation's**: the hold goal leaves it be, so the hero's job and fight go on while it leaps
  or climbs, and the trip and the pursuit wait for it instead of counting it as stuck. A downing is never the
  navigation's.
- **A failed link is not dead when a blow or a downing ended it** (3.3 says a failed link is dead for 10 minutes; a hit
  is not the link's fault).
- **Flights for trips only, out of a fight**: the mode of 3.5 with one PROPOSED floor (a leg under 8 blocks that has a
  ground route is walked). A fight's flights (reach flights, aerial windows) are A12's.
- **"Loaded" is "its entities tick"**: the planner reads a column only when its chunk is loaded and an entity there
  would be ticked, so a flyer never flies into terrain where it would freeze.
- **The forward probe looks toward the point it flies to**, not along its speed (a wall stops it, and its speed is then
  nothing).
- **A trip's take-off needs the sky over the hero**, not only 3.5's 4 blocks of headroom: in the campaign a flyer took
  off inside a building whose ceiling was exactly 4 blocks up and ended 8 of 21 flights stuck under it. Under a roof or a
  canopy it walks out first. A fight's and an escape's take-offs (A12) are not decided here.
- **Climbing, it rises in place under an overhang ahead** (an eave, a canopy's edge) instead of climbing on the slant
  into it: the probe looks from its feet up to the height it climbs to, as far as it flies before the next probe.
- **The scaffold pillar stays** for a hero woodcutter: its climbing (or hovering) variant is the job's own work loop and
  goes with L5m's mobility at work (12.4).

## A12 decisions on fight mobility, raids and escape (2026-11-18)

A12 (`HeroFightMobility`, `HeroRaid`, `HeroEscapeMovement`, `HEROES_V2.md` 15.2 "A12 as built") decided the points
MOBILITY_AND_WORK 3.7, 5.1, 5.2 and 7.4-7.7 leave open:

- **The take-off's weight.** An `aerial` block with no weight weighs 4 against the summed scores of the moves the
  selector could start; a kit that wants more or less time in the air sets it.
- **Phase transitions wait during an escape.** A hero that broke off kneeling through its next phase's transition was
  caught every time; a transition still due plays once the escape is over.
- **A drawn bow lands a flyer 60% of the time** (once in 8 s) unless the kit authors a `target_drawing_bow` reaction,
  which then decides.
- **An escaping hero in the air flies on** under the travel ceiling with no fight budget (7.6); its downing and air
  poise still hold, so archers can still bring it down.
- **A hop "back" with nothing to hop back from.** `move_self` back or forward is measured from the running move's aim,
  else the target, else (new) the one whose blow set the passive off this tick: Natsuho's Nine Lives fires on the blow
  that starts her escape, after the escape has cleared her target.
- **Heroes join raids only by hand** (`/fc admin hero raid <id>`, tests) until L4's draft; `HeroRaid` runs whatever
  put the hero in `f.raiders`.
- **Not built**: the flyer's scouting circle (5.2 "Scouting ahead"), shutting a gate an intruder opened, breaking a
  breakable gate.

## Boss heroes: format gaps (2026-11-18, `BOSS_HEROES.md`)

The two boss heroes (the Wither and the Ender Dragon) need these format and engine pieces. Each names the rule it
proposes; B1 (format) and B3 (engine) of `HEROES_V2.md` 15.8 decide and build them, and their test fixtures use them.

1. **A flyer that hovers.** `MOBILITY_AND_WORK.md` 1.14 refuses `hover` beside a `flyer`. The Wither floats on the
   ground and flies. Proposed: `secondary: hover` (tier 1-2) is allowed on a flyer; on the ground the hover rules hold
   (its height, no trampling, the gap drift, always within melee reach), in the air the flyer's. Today it would be
   ADJUSTED away.
2. **Projectile looks and a slow shot.** v1 appendix B.6 lacks `wither_skull`, `wither_skull_blue` and
   `dragon_fireball`; they can be drawn by vanilla's own `WitherSkullRenderer` (both textures) and
   `DragonFireballRenderer`, so they need no A9 model. A `shot` gets an optional `speed` (40-100% of a shot's speed,
   default 100): the blue skull flies at 60%, as vanilla's slower dangerous skull. A `rain` gets an optional
   `centre: self` (default: the target), so the Wither's Stray Skulls rain at random around herself; its per-receiver
   cap stays 1.5 x the slot's (review: 8 impacts of 3).
3. **Thrown basic attacks from the air.** `MOBILITY_AND_WORK.md` 5.1.5 allows light ranged moves (at most 3 per
   take-off) and the wind-up of a `dive` or a `rain` in the air, and is silent on basic attacks. The Wither's turret
   phase throws her basic skulls from the air. Proposed: a thrown basic attack may fire in an aerial window at its own
   rate, not counted among the 3 light ranged moves; a melee basic attack still may not (3.7's pillar case aside).
   For A12 to confirm.
4. **State fields `projectile_immune` and `keep_distance`** (`HEROES_V2.md` 12.9). `projectile_immune: true`: arrows,
   crossbow bolts, thrown tridents, wind charges and physical-element hero projectiles deal no damage and no ground
   poise to the hero, whoever shot them (they glance off; vanilla's perched dragon sets arrows alight); air poise still
   counts; element projectiles, potions and melee are unaffected. Validator rule: a hero takes no aerial window while
   such a state is on (so no hero is projectile-immune and out of melee reach at once). `keep_distance` 0-12 replaces
   the behaviour's while the state lasts (the armoured Wither closes to 2, as the powered vanilla Wither descends).
5. **The effect `linger`**: `{ radius 2-4, seconds 2-8, interval 1, at: impact | target | self | front, effects }`
   leaves a lingering cloud where the move lands (the dragon's breath, drawn like vanilla's area effect cloud with the
   `dragon_breath` particle). It counts against v1's 2 zones per hero (the oldest ends), its pulses stay under 1.5 x
   the move's slot cap per receiver, and two new state stats `linger_radius` (+0-2) and `linger_seconds` (+0-4) let a
   state widen it (the dragon's `heart_of_the_end`). The clouds of one use never stack on a receiver: standing where
   two touch counts as one cloud (review).
6. **Two add kinds** for A8's catalogue (`HEROES_V2.md` 6.11):
   - `end_crystal`, behaviour `beam_heal`: each second the nearest of the owner's crystals within `range` (24) with a
     clear line from its core to the owner heals `percent` (0.75) of the owner's maximum health; a `spire` (height by
     phase, solid like a shulker, not a block), `caged_every` (2, from phase 2) with `cage_hits` (3) and a cage that
     stops projectiles, an open crystal broken by any hit (snowballs too), a `shatter` burst (radius 3, effects on the
     owner's enemies), `max_alive` by phase (2, then 3), `per_fight` (8), `lifetime` (60), and placement: 6-14 blocks
     from the owner, at least 4 apart, its foot reachable by an ordinary person, never over the void, lava or water,
     and never where its spire would close a way narrower than 3 blocks (a gate, a door, a corridor, a 1-wide
     bridge); a spire crumbles with its crystal. From the review: `per_fight_percent` (35), all beams together heal
     at most that share of the owner's maximum in one fight, then its crystals dim and no more are raised; and the
     crystals are the one exception to 6.11's "never more than 3 blocks above the ground" for adds, since they never
     move and anyone can pillar beside the spire (phase 1's spire is 1 block, so an NPC's swing, which reaches no
     higher than its own height, reaches the crystal);
   - `skull_familiar`, behaviour orbiter and turret: each picks a different engaged enemy, floats 3 blocks from it and
     at most 3 above the ground, fires its projectile every `interval` (2.5 s) with its effects, health
     `owner_max_health_percent` (10), returns to its owner after its lifetime.
   And: a phase's `adds` (12.10) are spawned by its transition.
7. **Three weak points** for 6.9's table: `focus_add` `{ add, only: any | beaming, damage_percent 0-8, poise 0-60,
   damage_taken 0-25, seconds 2-10 }` (an add of that kind dies to anyone: the damage counts as a hit from its killer,
   under the per-hit cap); `smite` (an attacker's Smite enchantment adds its vanilla 2.5 per level, as against
   `#sensitive_to_smite`); `blast` (damage in `#is_explosion` +25% under the cap, and twice the poise).
8. **A rebirth with its own content.** 12.10's `rebirth` is a boolean. Proposed: `rebirth: { at 20-40 (30), moves,
   states, adds, transition }`, still legendary only, its pip on the bar from the start; an action code `rebirth`
   (appendix D's reserved range) for its animation; the line event `rebirth`. After a rebirth no earlier phase
   re-arms: 6.10's re-arm would replay any phase whose threshold lies 10% or more under the rebirth's `at` (review).
9. **The `perch` form**: a self move `{ range 6-16 (12), seconds 4-10 (8), state, break_percent 5-25 (15),
   end_followup, radius }`. The perch is the highest standable cell within range of the target whose top is at most 1
   block above ground an engaged enemy stands on or can walk to beside it, with 2 blocks of headroom, never lava,
   fire, farmland or powder snow; else the ground beside the hero's nearest add. (The review lowered it from 3: an
   NPC's melee box reaches no higher than its own height, so a higher perch put a hero that arrows cannot hurt out of
   every soldier's reach.) Class heavy at least; the state ends after `seconds` or `break_percent` of the hero's
   maximum health, then `end_followup` plays when an enemy is within `radius`. Perched time is ground time for the air
   budget.
10. **`mobility.aerial.pattern`**: `hold` (today) or `circle` with `radius` 8-14: the hero orbits its target 4-6 blocks
    up between moves; while one of its `beam_heal` adds stands it keeps within that add's range and sight.
11. **`immune_effects`**: a list of vanilla mob effects that never apply to the hero (the Wither: `wither`). NOTE for
    any entry that removes a counter (`slowness`, which downs flyers; `glowing`, a tell): refused.
12. **Passive pieces**: the form `shot_random` (a projectile at a random engaged enemy in `range` and sight, preferring
    one that is not the hero's target: the Wither's side heads); `on_add_died` names the add's killer as
    `trigger_enemy`; a mark with `focus: true` makes the marked enemy the hero's preferred target while it lasts (the
    dragon's Wrath of the Spires).
13. **`boss.bar_color`**: one of the seven boss-bar colours, replacing the nearest-colour rule (presentation; the
    Wither's purple and the dragon's pink, as vanilla's).
14. **Boss-hero identity**: an archetype group E (`wither`, `ender_dragon`) outside `ROSTER_PLAN.md`'s counts and
    `check_roster.py`; `leader: { "eligible": false }` with no other leader fields (the reader asks a built-in for a
    full leader block today); `recruit.condition: token`; the line event `unbound`; the sound slots `rebirth`,
    `calling`, `linger`, `mobility.break` and `mobility.wings`; a hero's `work` override of `BlockWork`'s tool (the
    Wither's hands count as a diamond pickaxe) and a raid goal `breach` allowed only to a hero whose file names it.
15. **Not a format gap, an engine prerequisite** for ROADMAP phase 3: vanilla's `EnderDragon.hurt` ignores damage
    whose source is not a player or an explosion, so faction NPCs cannot hurt the vanilla dragon today
    (`BOSS_HEROES.md` 6.6).
16. **Not a format gap, an ordinary-NPC rule** for B4 and ROADMAP phase 3 (review): archers and crossbowmen carry a
    sword as a sidearm (the best the faction's stores give, a stone sword at worst) and draw it, as a player switches
    hotbar slots, when every enemy they could shoot is projectile-immune (the armoured Wither, a perched dragon, the
    vanilla Wither under half health). Today they never fight in melee, so an archers-only side could not hurt the
    armoured Wither at all (`BOSS_HEROES.md` 6.3).
17. **Move weights by state or situation** need no new field (review): `prefer_when` (x2) with the conditions
    `phase`, `self_state` and `adds_alive` writes them all (the Wither's volley in phase 1, Grave Grasp while unbound,
    the dragon's breath, roar and ultimate while perched, her crystal and perch while none stands).

## Battle without civility (2026-11-18, `HEROES_V2.md` 6.17 and 6.18, `docs/design/breaching.md`)

The owner's answers of 2026-11-18 ("its ok for any hero or npc that usrs explosives to breach walls, theres no need
for anyone to be "civil" in battle"; "its fine for heroes abilities to push someone of a cliff or to lava") reverse
three decisions already built and add two engine pieces. `HEROES_V2.md` 15.9 (T1-T4) carries the work; this list is
what the Java side must change, test by test, and what other documents' owners should revisit.

1. **Hazard-aware pushes are gone for enemies** (A3's "Hazard-aware pushes for everyone" above: a push toward lava or
   a drop of more than 3 cut to a third). `HeroEffects.knockback` no longer calls `hazardAhead` for an enemy; the cut
   survives only for a push a move gives its own side (none in the slice). `BossRules.safeKnockback`,
   `HAZARD_KNOCKBACK`, `HAZARD_LOOKAHEAD` and `HAZARD_DROP` stay only for that case; `BossRulesTest
   .hazardKnockbackAndStunsThatCannotChain` checks the opposite for an enemy (full strength toward lava).
2. **The grab throws toward lethal edges** (A5's "Grab" above: straight ahead, then 45, 90 and 135 degrees, then
   behind, the first whose landing held no lava, fire or deep drop; softened to 0.5 when none). `HeroCombat
   .throwGrabbed` now tries the directions whose landing is a lethal edge first (`HeroEffects.landingHazard` turns
   from a refusal into the search), nearest first, then straight ahead, never softened. GameTest
   `grabHoldsAndThrowsSafely` becomes `grabThrowsTowardALethalEdge`.
3. **The phase shockwave pushes only enemies, unsoftened** (`HeroBoss.shockwave` pushed everyone but the hero, its own
   adds and display copies, softened near lava). Its own side is left where it stands. GameTest
   `shockwavePushesOnlyEnemies`.
4. **A hero's own hop checks hazards** (new): `HeroCombat.hop` (`move_self`: Yugen's vanish, Natsuho's Nine Lives and
   cat reflex, Ambrose's fume step) is shortened to stop short of lava, fire, the void or a drop past the safe fall, as
   dashes and leaps already are (`MobilityRules.Rule.HAZARDS`, unchanged: it was always about the hero's own body).
5. **Aiming at edges** (new, `logic/hero/EdgePlay`, pure): the arena sample (6.12) gains lethal and harmful edges round
   each engaged enemy with the receiver's immunities; `MoveSelector` multiplies a push move's score by 1 + the edge's
   value; the spacing goal walks round to the far side (3 s, then 10 s off for that target); a charge's line runs
   through the target toward the edge; `heroes.edgePlay` (true). Unit test `EdgePlayTest`; GameTest
   `heroCirclesToPushTowardAnEdge`. *Items 1-5 built 2026-11-19* (`HEROES_V2.md` 15.9 "T1 and T3 as built"; item 2's
   throw tries the edges through `EdgePlay.throwTry`, `landingHazard` is gone).
6. **Kill credit** (new, `logic/hero/KillCredit`, pure, shared with ordinary NPCs): a death by terrain within 15 s of an
   enemy's push, throw, pull or blow is that enemy's kill; a push with no damage is recorded as a hit of 0 in the
   receiver's `CombatTracker`; the receiver's last attacker is refreshed every second while it falls, burns or is in
   lava, the void or water (vanilla forgets after 100 ticks); the hero kill feed uses vanilla's `.player` death keys
   (`death.fell.assist`, `death.attack.lava.player`, `death.attack.outOfWorld.player` ...). The rewards of 6.13 already
   counted "a fall" for the last side to hurt the hero; terrain of every kind and zero-damage pushes now count too.
   Unit test `KillCreditTest`; GameTest `terrainDeathIsThePushersKill`.
7. **Revival where it fell**: the nearest safe standable cell within 16 blocks, never lava, fire or over the void; at
   home after a death in the void (3.4, 12.3). GameTest `heroRisesSafeAfterALavaDeath`.
8. **The `explosive` move property** (new format piece, 12.7 and 6.18): `{ power 1-4, fire }`, a skill at most 2, an
   ultimate at most 4; only `shot`, `bolt`, `volley`, `barrage`, `nova`, `zone`, `rain`, `leap`, `dive`; at most 6
   blasts per use with powers adding up to 8 (a rain's, volley's or barrage's at power 1); class raised to `heavy`;
   `fire` only on a fire move. The blast is `Level.explode` with interaction `MOB` (NeoForge's mob-griefing check) and
   an `ExplosionDamageCalculator` whose `shouldDamageEntity` is false and `getKnockbackMultiplier` is 0 (checked in the
   1.21.1 sources: vanilla's explosion skips both for every entity, items included). The pick scores 0 an explosive
   move whose blast at the target would reach its own side's blocks; `heroes.explosiveBlocks` (true). No slice move
   has it; the test-only `fixture_bomber` carries the GameTests (`explosiveMoveBreaksBlocksNotPeople`,
   `explosiveMoveSparesItsOwnBase`).
9. **The kit sheets** are at kit version 3 by hand (a TERRAIN AND BLOCKS section in each of the eight). When herogen
   writes the sheets (C1-C4), its template needs that section: the hero's pushing moves with their strengths, its
   knockback resistance and safe fall or fall immunity, its fire-proofing, and whether any move is `explosive`.
10. **For `BOSS_HEROES.md`'s revision** (its owner decides; not edited here):
    - 4.3 and 4.5 say the Wither's moves never break blocks. Vanilla's black and blue skulls and the spawn charge are
      explosions by nature, so Ruin Skull, Stray Skulls and Birth Blast are candidates for `explosive`. Vanilla's
      spawn charge is power 7, over 6.18's cap of 4: either Birth Blast takes 4 or the cap rises for a legendary's
      ultimate (a reader rule to add with B1). Vanilla's blue skull treats every block that is not wither-immune as
      no stronger than 0.8, so it breaks obsidian; copying that needs a field of its own (`pierce_resistance`).
    - The owner's answer keeps the Wither's breach with her hands (question 3 of its section 11).
    - 5.3's Wing Gust "never into the void" and 6.4's rule 5 row ("hazard-aware knockback") now read: full strength,
      and the End's void is in play, as the vanilla dragon's wings throw players off the island. Her Levitation that
      "never stacks into fall damage" is a kit choice and may stay.
    - 6.4's "Base safety" row: an explosive move breaks blocks (6.18), never its own side's on purpose.
11. **For `ARTIFACTS.md`'s revision**: 4.4's "never placing, breaking or igniting blocks" stays an artifact rule unless
    that revision wants an explosive active (it would use 6.18's property and limits); artifact passives and actives
    that push (a pull, an updraft) land at full strength now, and the `knockback_resistance` line is worth more.
12. **For `ROSTER_PLAN.md` content batches**: row 163's "powder shots leave fuse kegs that burst (no block damage)" was
    written under decision 11; with 6.18 the kegs may be `explosive` (power 1-2) when its batch is authored, and row
    167's flask grenades likewise. A sapper or bomber archetype, if one is added, is the natural user.

## Artifacts revision: format and engine gaps (2026-11-18, `ARTIFACTS.md`)

The owner's answers of 2026-11-18 (slots by level and fixed strength, each hero's own choice, tinkering, growth, tech
levels) revised `ARTIFACTS.md`. These are the pieces R1-R5 (`HEROES_V2.md` 15.7) must add or decide; each names the
rule it proposes.

1. **Slots by level**: `heroes.artifacts.slotLevels` (`1,5,10,15,20`) replaces `slots`, `attunementAtOne` and
   `fullPowerLevel` (read and ignored with a log line). A slot that closes (a level lowered by command, the config)
   empties into the backpack, else at the hero's feet (`ARTIFACTS.md` 3, 15.3).
2. **Fixed strength** needs one flag in the damage path: an Amount from an artifact's passive or active is multiplied
   by the artifact ability power and `heroes.artifacts.powerPercent` only, never by the hero's growth ability power or
   `heroes.powerScale` (`ARTIFACTS.md` 4.3). `HeroDamage` and `HeroPassives` must know the Amount's source.
3. **New format pieces** (`ARTIFACTS.md` 5.3): triggers `on_marked` (an enemy's move starts its wind-up with a ground
   mark covering the wearer: from `HeroTelegraphs`' decal geometry, server side, the same for every caster) and
   `on_land` (a landing after `value` blocks of flight or glide, from `HeroMovement`); form `ally_lowest`; effect
   `ward_effect` (refused on Slowness and Glowing, the counters); conditions `target_bleeds` (with the data tag
   `#factionclash:bloodless`), `engaged_has` (reusing `target_is`' `hero` and `boss` kinds over the engaged set) and
   `in_daylight`. v1's `target_is` is already a condition (v1 conditions table), so Graveside Peal (Toll for the Dead
   before the review renamed it) needs nothing.
4. **Growth counters** (`ARTIFACTS.md` 7.8), each counted only for a living hero wearing the artifact in a slot, and
   written to the item's `artifact_work` component when a step is reached or a fight ends: `blood` (damage it really
   dealt, after the per-hit cap and armour, to a target that `target_bleeds`), `ore` (`BlockWork`'s break of a
   `#c:ores` block with a tool that harvests it), `nights` (at dawn: worn since dusk, not in a bed, alive), `flight`
   (`HeroMovement`'s horizontal distance while `DATA_LOCO` is flying or gliding), `undead` (a death of an
   `#minecraft:undead` entity whose killing blow or last hit within 3 s was the wearer's), `worthy_foes` and `scars`
   (from `HeroBoss`' end of a fight: an engaged enemy hero or vanilla boss died or escaped while the wearer lived; the
   wearer fell under a third and lived, an escape counting), `tending` (the farmer's harvest and replant, a woodcutter's
   sapling, an animal carer's bred pair, by the hero at its duty). Sparring and agreed bouts (`HEROES_V2.md` 7.4) count
   nothing.
5. **Smithing resets upgrades and growth**: vanilla's `SmithingTransformRecipe` copies the base's components to the
   result and then applies the result's patch, so each artifact recipe's result must carry `artifact_work`
   `{ "upgrade": 1, "growth": 0 }` explicitly (`ARTIFACTS.md` 9.8, 16.2).
6. **The Artificer's Bench** (`ARTIFACTS.md` 9.7): a block, block entity and menu (one artifact slot, three material
   slots, Begin), the recipe type `factionclash:bench` generated from the artifact files and the tier table, progress
   that runs by itself and moves faster while a tinkerer tends it (as `FurnaceWork`'s tending), the research check of
   the user's faction (none until ROADMAP phase 6; `heroes.artifacts.techGate`), and a new workshop point in
   `BaseState`.
7. **The artificer** (`ARTIFACTS.md` 9.6): `NpcRole.ARTIFICER` and `ArtificerJob`; the director turns the base's
   blacksmith of level 5 or more into it once the bench stands (a role change that keeps level, XP and affixes, and
   re-opens the blacksmith need), else hires one after 3 in-game days; one per base with a bench. For heroes: the duty
   `artificer`, and the talent `tinkerer` (`TALENT_DUTY` maps it to that duty) added to `HEROES_V2.md` 7.2's table,
   `MOBILITY_AND_WORK.md` 11.1's specialties and `logic/leader/Talents` (R5, R7).
8. **The choice AI** (`ARTIFACTS.md` 10): `ArtifactChoice` reads the authored `relationships` (`HEROES_V2.md` 12.16)
   before milestone 3 (they are already validated) and a player's standing from milestone 2 (0 until then). The
   station's ledger (claims, asks, wishes, the workshop's order) is a new `FactionState` record. The first design's
   pins and `ArtifactPlanner` are gone.
9. **Line events** `artifact_gift`, `artifact_refuse` and `artifact_ask` join `artifact` in `HEROES_V2.md` 12.15's list,
   with temperament fallback lines (`ARTIFACTS.md` 12.5, 16.6).
10. **Item 11 of "Battle without civility" above is answered**: no artifact has an explosive active (`ARTIFACTS.md`
    16.5 refuses 6.18's `explosive` on an artifact); an artifact's push (Updraft, Downpull's pull, the new Ram Strike)
    lands at full strength and is aimed at edges like any move's (6.17); the `knockback_resistance` role weights were
    raised by 0.2 in `ARTIFACTS.md` 10.2.
11. **For `BOSS_HEROES.md`'s revision** (its owner decides; not edited here): 4.13 and 5.13 say "(all x its
    attunement)" and the war rating "0.12 x attunement": with fixed strength the base lines and the bond are the same
    at every level, and the rating is a flat 0.12 (`ARTIFACTS.md` 4.2, 13). She joins at level 1, so her one slot holds
    her signature until level 5 (`ARTIFACTS.md` 3). "It stays with her" now holds by her own choice (`ARTIFACTS.md`
    10.4: her want for her own signature is unbounded, no leader's wish moves it), not by the planner. Signatures are
    never upgraded and never grow (16.4 refuses `upgrade`, `growth` and `tech` on them); their files may stay format
    1. The two boss kit sheets' "60% of that at level 1, all of it at 20" lines go.

## Boss heroes revision: format and engine gaps (2026-11-18, `BOSS_HEROES.md`)

The owner's answers of 2026-11-18 (no personal names, back to the token, explosives may breach, heroes may push anyone
off a cliff or into lava, and the artifact answers) revised `BOSS_HEROES.md`. Item 10 of "Battle without civility" and
item 11 of "Artifacts revision" above are answered here. B1, B3, B4 and B7 (`HEROES_V2.md` 15.8) carry the work; each
item names the rule it proposes.

1. **The Wither's blasts** (`BOSS_HEROES.md` 4.5): Ruin Skull and Stray Skulls are `explosive` power 1, Birth Blast
   power 4 (6.18's ceiling, not vanilla's 7, so that its crater, about 6.8 blocks, stays inside its 7-block red disc);
   no cap rises for a legendary. Stray Skulls becomes a rain of 6 impacts of 4 blight (6.18's six blasts per use; still
   24 a receiver, the rain cap; the review's "8 of 3" is replaced). Her black skulls (basic attack, Skull Volley, Three
   Heads, the familiars, Skullfall) carry no blast: light moves and basic attacks may not, and Skullfall's ten impacts
   pass six.
2. **`explosive.resistance_cap`** (new field, 0.1-1.0): every block but air and `#wither_immune` counts as no stronger
   than it, vanilla's blue skull rule (`WitherSkull.getBlockExplosionResistance`, which asks NeoForge's
   `canEntityDestroy`); in T2's `ExplosionDamageCalculator` it is `getBlockExplosionResistance` returning
   `min(cap, resistance)` for such a block. Allowed only at power 1 on a legendary (PART LEFT OUT otherwise). To check
   in B3: vanilla reads a fluid cell through the same method, so a water cell counts as 0.8 too.
3. **`behaviour.break_out: box`** (new): in a fight, walled in (every side cell blocked and no path to an engaged enemy
   for 1 s), the blocks from the hero's feet up to the cell above its head and one block out on each side are destroyed
   with drops at once (vanilla `WitherBoss`'s `destroyBlocksTick` box, which starts at the feet), never
   `#wither_immune`, never a planned block of its own side or a pact ally, only when `EventHooks.canEntityGrief` allows,
   at most once a second, with level event 1022. With mob griefing off, `HEROES_V2.md` 6.12's dig runs at once instead
   of after 10 s. A NOTE on a non-legendary.
4. **`work.tool`** (new): the hands' `BlockWork` tool for a hero with no held tool (`diamond_pickaxe` for the Wither,
   `iron_pickaxe` for the dragon's claws): break speed, harvest level and vanilla drops of that tool, no wear. This
   replaces item 14's "work override"; item 14's raid goal `breach` for one named hero is dropped.
5. **Her blue skulls on the breach spot**: the "open the way" raid goal (`MOBILITY_AND_WORK.md` 5.2, 6.18) lets a hero
   cast an explosive move at `docs/design/breaching.md` 6.2's spot up to three times (one blue skull bites less than
   one TNT; breaching's two-charge limit is for TNT); `BreachPlanner`'s "breakable" factor takes the breacher's blast
   (its power and `resistance_cap`), so obsidian is breakable to her. For X1 and X3.
6. **Aiming a dive or a leap at an edge**: 6.17 gives the aim of knockbacks, charges, grabs and pulls, not of landings.
   Proposed for `EdgePlay` (T1): with a lethal edge near the target, a `dive` or `leap` lands on the side of the target
   away from that edge, still inside its landing disc, so its radial push sends the target over (the dragon's Void
   Dive and Fall of the Outer Isles). *Not in T1 as built (2026-11-19)*: a leap's or dive's push counts in the pick
   and the walk round as any push away from the hero, but its landing spot is not chosen by the edge.
7. **No smashing in flight**: the dragon hero never removes blocks she flies through (vanilla
   `EnderDragon.checkWalls` stays vanilla's). Nothing to build; a GameTest (`dragonFlightBreaksNoBlocks`) guards it.
8. **The crystal shatter** pushes at full strength (6.17: a passive's push) and breaks no block; it is not an
   `explosive` (not a move). Item 6's `shatter` keeps its effects; its old "hazard-aware" knockback goes with T1.
9. **A signature that grows or upgrades** (`ARTIFACTS.md` 16.4 and 16.5, revised with this file): a signature may carry
   a `growth` block (7.8's rules, at most three steps, since it is never upgraded) or an `upgrade` block whose `tech` is
   `artifice_2` for both levels, never both. An upgrade multiplies the base lines only; the bond's lines and kit mods
   stay; tuning at III (the review's name; it was "honing") tunes the passive in use (the bond's or the base's). A kit
   mod never changes a move's `explosive`. Hollow Star: `growth` `withering`, base 60, steps ability power +6, drain +2,
   health +5. Spirecore: `upgrade` `minecraft:end_crystal` 1 / 2. Both files move to artifact format 2. The war value is
   0.12 + 0.015 a step or + 0.02 an upgrade level.
10. **The counter `withering`** (new, beside 7.8's eight): each point of damage the Wither effect deals to an enemy when
    its wearer gave that effect (its basic attacks, moves, passives and the brand, so the Wither's Three Heads while the
    side heads float at her side; never its adds' or summons', so not the familiars once unbound in phase 3). Vanilla
    does not keep who gave a mob effect, so `EffectRouting` (or `HeroStatus`) records the giver per receiver when a hero
    applies Wither, cleared when the effect ends; the count reads it on each Wither damage tick (`DamageTypes.WITHER`).
11. **A signature's standing lent mark**: worn by another hero of its own hero's faction, a signature counts as lent by
    its own hero (`ARTIFACTS.md` 10.4's lent rules: she asks it back at her next choice moment; honour 50 or more
    returns it at once). For R4 (`ArtifactChoice`).
12. **Her focus on her own signature**: an enemy hero she sees wearing her signature is her preferred target in a fight
    (the focus rule for a marked enemy of the "Boss heroes" item 12, fed by the signature registry's wearer). For B4.
13. **No `unbindOnFall`**: the owner chose the token; the switch is gone from `[heroes.bossHeroes]`.
14. **The kit sheets** are at kit version 3 by hand. When herogen writes the two boss sheets (B7), its template needs
    the TERRAIN AND BLOCKS section (blasts with power and what they break, the box break-out, pushes, fall and fire
    immunity) and the signature's growth or upgrade table.

## Review of the three revisions (2026-11-18, `ARTIFACTS.md` 21.2, `BOSS_HEROES.md` 12.2, `docs/design/breaching.md` 15, `HEROES_V2.md` 6.19)

An adversarial review of the artifacts, battle and boss heroes revisions against the owner's answers of request 11
and the engine's code. What the Java side must now do differently, each with the rule it sets:

1. **Shields do not stop pushes** (`HEROES_V2.md` 6.17). Today's `HeroEffects` runs a move's `knockback` as its own
   effect after the damage, whether or not a raised shield blocked it, and vanilla's `LivingEntity.hurt` knocks a
   blocker back too. 6.17 now says so; nothing changes in T1. A test in T1's set: `shieldStopsDamageNotPush` (an NPC
   soldier and a mock player with raised shields are pushed the same distance and take no damage). *Built 2026-11-19*
   in `HeroTerrainGameTests`: the move's push lands whole on both blockers alike; only vanilla's own knock-back of the
   blocked blow is vanilla's smaller one (0.15 for 0.4), so a blocker ends a little shorter than a bare receiver.
2. **How far a push carries**: knockback strength 1 is vanilla's 0.4 (`FormGeometry.KNOCKBACK_PER_STRENGTH`), about
   2.1 blocks on flat ground (vanilla's 0.546 ground and 0.91 air friction, 11 ticks aloft; *as built*
   `FormGeometry.knockbackDistance`, 2.07, knockback 2 measured at 4.13 in game); the old hazard look-ahead
   used 2.5 a point. `EdgeSafety` (T3, X5) looks 6 blocks out while a hero is among the pushers, 4 otherwise
   (`docs/design/breaching.md` 8.1); `EdgePlay` keeps its 8.
3. **`tuned`, not `honed`**: the artifact format key for the improvement at upgrade III is `tuned` (`ARTIFACTS.md`
   9.8, 16.1, 16.5; Lost Ark's gear upgrade is "honing"). No artifact file exists yet, so no synonym is kept. Test
   `tunedNeverTouchesFrames`.
4. **Passive renames** (data only): Assayer's Eye, Lamplit Eyes, Graveside Peal and Open Vein (were Keen Eye, Night
   Eyes, Toll for the Dead and Bloodied). No id changes, so the icons and `herogen check` are untouched.
5. **An artifact pushes at most knockback 3** (`ARTIFACTS.md` 4.4, 16.5): `ArtifactReader` lowers a stronger
   `knockback`, `pull` or throw on a passive or active to 3 (ADJUSTED). Ram Strike is knockback 3. Test
   `artifactPushAtMostThree`.
6. **The choice AI's noise is daily** (`ARTIFACTS.md` 10.3, 10.4): `ArtifactChoice` draws a hero's noise per artifact
   once per in-game day (seeded by hero, artifact and day), holds the hysteresis at 3% or more, and allows one swap per
   hero per day unless a slot opened or a new artifact, offer, upgrade or growth step reached it. Tests
   `noiseDrawnOncePerDay`, `hysteresisNeverUnderThreePercent`, `swapAtMostOncePerDayWithoutNews`.
7. **A player's Ask names its recipient** (`ARTIFACTS.md` 10.10): the hero sheet's Ask button opens a pick of the
   faction's heroes and "for me"; for a hero it is that hero's ask (10.4) with +0.1 x the player's `rel` on the
   generosity; "for me" gives only what the hero does not want and what that player lent. Tests
   `playerAskForAHeroWeighedAsThatHerosAsk`, `playerAskForSelfGivesOnlyUnwantedOrLent`.
8. **`on_marked` reads the mark, not the decal** (`ARTIFACTS.md` 5.3): the trigger fires from `Decals`' geometry of a
   move whose wind-up starts, never from `HeroTelegraphs`' decal entities, which exist only while a player is near.
   GameTest `readTheTellFiresWithNoPlayerNear`.
9. **The box break-out's repairs** (`BOSS_HEROES.md` 4.5): a mob's own block break fires no NeoForge event, so B3 adds
   the planned blocks of an enemy base that the Wither's body withers to that base's `pendingRepairs` itself, as
   `FCEvents.onExplosion` does for explosions.
10. **The vanilla dragon's NPC damage** (`BOSS_HEROES.md` 6.6): `EnderDragon.hurt(part, ...)` drops damage that is not a
    player's or tagged before any event fires, so the tagged type rides on the source: melee builds `faction_strike` in
    `FactionNpc.doHurtTarget` (and a hero's blow) when the target is the dragon or a part; arrows go through
    `ProjectileImpactEvent` (cancel, re-deal with `faction_arrow`, remove the arrow). No mixin.
11. **`mobGriefing` and NPC TNT** (`docs/design/breaching.md` 10, 14): the game rule gates mob explosions (a hero's
    explosive move, `Level.explode` with interaction `MOB`, vanilla's own check) and the Wither's box break-out, never a
    breacher's hand-placed TNT (interaction `TNT`, which vanilla does not gate either). `war.explosives` stops planned
    breaches. GameTest `breachWorksWithMobGriefingOff`.
12. **Heroes take TNT at the muster** (`docs/design/breaching.md` 5): a hero whose raid goal is "open the way" and who
    has no explosive move takes `war.tntPerBreacher` TNT and a flint and steel when the base's stock allows (X3).
    GameTest `heroBreacherTakesTntAtMuster`.
13. **ROADMAP phase 6 knows about Artifice**: the tech-tree row now names the Artifice line `ARTIFACTS.md` 9.5 asks for,
    so its design pass prices it.
