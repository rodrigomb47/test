Hinae Shirasuzu's own preview set (C1), written by `python3 tools/herogen/herogen.py hero-previews hinae_shirasuzu`
(`--check` re-renders the set and compares it byte for byte). She has one body, adult_f standard at 96% (HEROES_V2
3.6), and every image shows her in that body only.

turnaround.png: eight views, from above, weapon drawn, far (fine_ bones hidden), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with her overrides (her "angry" is a worried pout, her "smug" a closed-eye smile), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 2: the ring of seven talismans; phase 3: glowing eyes and the wand's streamers turned to glowing gold), phase 3 at night
sheet.png: every clip of her own animation file as a filmstrip, each in the phase it belongs to (sec.loop over the combat stance in phase 2)
move_talisman_fan.gif, move_sealing_circle.gif, move_purify.gif, move_exorcise.gif, move_seven_talismans.gif (phase 2), move_purifying_dance.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
ultimate.gif: Thousand Bell Rite, drawn in phase 2 so the ring of talismans shows it widening and flaring at the hit
string.gif: her basic attack (a thrown talisman, attack.ranged, twice) and the focus string attack.1-3 (she has no followup chains)
moments.gif: intro, phase.2 (the ring unfolds), phase.3, taunt, victory, last_stand, escape (over the run)
idles.gif: personal.1-3 over move.idle (the bell shake, tidying up, the prayer)
locomotion.gif: walk > run (a runner); jump > leap > land; swim (a swimmer); climb

The ring's orbit (sec.loop, the secondary controller) plays under every film and sheet frame, as in game. Two views per
frame: 3/4 front and her right side. Actions play over their base with the action controller's 2-tick blend.
The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
