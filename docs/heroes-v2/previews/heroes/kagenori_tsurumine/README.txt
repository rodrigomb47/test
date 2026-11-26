Kagenori Tsurumine's own preview set (C3), written by `python3 tools/herogen/herogen.py hero-previews kagenori_tsurumine`
(`--check` re-renders the set and compares it byte for byte). He has one body, adult_m standard at 102% (HEROES_V2 3.6,
SLICE_PLAN 3.5), and every image shows him in that body only. He has no tail.

turnaround.png: eight views, from above, sword drawn, far (fine_ bones hidden), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with his override ("happy" keeps his focused eyes with a faint smile), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 2: the plain helmet; phase 3: glowing eyes and blade edges, the short sword), phases 2 and 3 at night
sheet.png: every clip of his own animation file as a filmstrip, each in the phase it belongs to
move_three_cut.gif, move_iai_stance.gif, move_thunderclap_draw.gif, move_crane_step.gif, move_guard_splitter.gif (phase 2), move_chain_draw.gif (phase 2), move_falling_crane.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
ultimate.gif: Heaven-Splitting Draw (the blade slid home, the eyes closed through the stillness, opened sharp, one rising draw at the hit)
string.gif: the katana string attack.1-3, then Thunderclap Draw > Crane Step (its followup, 30%), Crane Step > Three
  Cuts (70%) and Falling Crane > Three Cuts (100%); these films start the followup after the whole first clip, as the
  shared followup films do
moments.gif: intro (the blade slid home, a bow, the hand on the hilt, the quick draw), phase.2 (the helmet goes on), phase.3 (the short sword drawn from its scabbard) and state.twin_sky after it, taunt, victory, last_stand (both blades)
idles.gif: personal.1-3 over move.idle (the hand resting on the hilt, two slow breaths with the eyes closed, re-tying the ponytail)
locomotion.gif: walk > run (a runner); crouch_jump > leap (his crane leap, G10's variant) > land_roll (a leaper II); climb

His draws rest on one trick: in a move the server keeps the weapon drawn, so to sheathe he lays the blade in his hand
exactly where the sheathed copy hangs (the hilt within 0.6 px of it, the guard within 0.9 px), and draws it out of
there. Two views per frame: 3/4 front and his right side. Actions play over their base with the action controller's
2-tick blend. The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
