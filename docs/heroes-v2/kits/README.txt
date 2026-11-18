Faction Clash - hero kit sheets
====================================================================================================

WHAT THESE FILES ARE
One plain-text sheet per hero, named after the hero's id (for example natsuho_komezaki.txt), written
for players: who the hero is, the look, every move with its tell, how to counter it and its exact
numbers, the boss fight, how to beat the hero, movement, work and talents, how the hero leads a
faction, personality and lines, animations and sounds.
They are made from the hero design documents in this folder's parent (SLICE_PLAN.md section 3,
MOBILITY_AND_WORK.md sections 7.7, 11 and 15, HEROES_V2.md sections 3.4, 5, 6, 7.2 and 8). Nothing
in them is invented: where the design leaves something open, the sheet says "set when she is built"
or "set when he is built".

STATUS
Kit version 3, 2026-11-18: the eight heroes of milestone 1 (the vertical slice) are designed, not in
the game yet. All numbers are first values, tuned after play. Version 3 follows the owner's answers
of 2026-11-18 ("theres no need for anyone to be civil in battle"; "its fine for heroes abilities to
push someone of a cliff or to lava"): each sheet has a TERRAIN AND BLOCKS section. A hero's pushes
now land at full strength even into lava or off a cliff, it aims them there on purpose, and a death
that way is its kill; none of the eight moves is an explosion, so none breaks blocks.
Version 2 (2026-11-13) followed the owner's decisions of 2026-11-12 and 2026-11-13: a hero fights
the same against players and NPCs (one combat mode, with the same health, caps and rules whoever it
fights; no extra damage to players and no protection from a one-hit kill), any number of heroes may
gang up on one target, and no hero yields: each sheet's "When ... is losing" says whether that hero
escapes or fights to the death, and why. Version 1 (2026-11-12) had a player-only boss mode and a
yield.

WHEN THEY CHANGE
The sheets are regenerated whenever a hero is built or its kit changes, and each new or changed
sheet is sent to the owner. From the first hero task (C1) on, the hero generator (herogen) writes
each sheet from the hero's source file, so the sheet always matches the game.

THE EIGHT HEROES
  natsuho_komezaki.txt      Natsuho Komezaki, the harbour cat (striker, physical)
  yugen_kagaribi.txt        Yugen Kagaribi, the lantern fox (marksman, arcane)
  benika_akagane.txt        Benika Akagane, the iron club oni (vanguard, fire)
  hinae_shirasuzu.txt       Hinae Shirasuzu, the white bell shrine maiden (support, radiant)
  kagenori_tsurumine.txt    Kagenori Tsurumine, the still crane samurai (striker, shock)
  tobimaru_usugumo.txt      Tobimaru Usugumo, the quiet ash ninja (skirmisher, blight)
  ilsabet_frostmere.txt     Ilsabet Frostmere, the rime vow knight (vanguard, frost)
  ambrose_nettlewick.txt    Ambrose Nettlewick, the cauldron warlock (controller, venom)

OTHER SHEETS
  artifacts_overview.txt    Artifacts: items only heroes can wear, slots that open as a hero levels,
                            how each hero chooses, how tinkerers upgrade them, the ones that grow,
                            and the 61 of the catalogue (kit version 4, 2026-11-18, after a
                            second review: asking for an artifact names whom it is for, and
                            five names that sounded like other games' terms were changed)

THE BOSS HEROES (outside the 200; design of 2026-11-18, docs/heroes-v2/BOSS_HEROES.md)
  the_wither.txt            The Wither, Queen of Three Skulls (marksman, blight)
  the_ender_dragon.txt      The Ender Dragon, Empress of the End (vanguard, arcane)
  Kit version 4 (2026-11-18, after a second review): how far their pushes throw you (about 2 blocks
  a point of knockback) and what helps against them. Kit version 3 (the same day, your answers): no
  personal names; back to the token when their faction falls; the Wither's blue skulls and Birth
  Blast break blocks and both push you into lava or off a cliff (TERRAIN AND BLOCKS); Hollow Star
  grows and Spirecore can be upgraded.

HOW TO READ A SHEET
- Numbers in [brackets] are the exact design values; the plain words around them say what you see in
  the game.
- Frames are wind-up / active / recovery in seconds: the wind-up is your warning, the recovery your
  chance to hit back.
- Damage is a share of the hero's attack power (150% = one and a half normal hits) or plain health
  points (2 points = 1 heart). Ranges are in blocks: close up to 4, mid 4 to 10, far 10 to 24.
- Every hero has all of its abilities from level 1; levels 1 / 10 / 20 only change how strong they
  are.
- "You" is the reader, but nothing a hero does depends on whether its enemy is a player: a soldier
  raising a shield, an NPC archer on a pillar or an NPC hit on the hero counts exactly as yours.
  Only what you see and hear (the boss bar, the intro title, the ground marks, the sounds and the
  lines) is for players.
