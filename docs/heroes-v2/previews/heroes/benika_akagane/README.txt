Benika Akagane's own preview set (C2), written by `python3 tools/herogen/herogen.py hero-previews benika_akagane`
(`--check` re-renders the set and compares it byte for byte). She has one body, adult_f large at 108% (HEROES_V2 3.6,
SLICE_PLAN 3.3), and every image shows her in that body only. She has no tail.

turnaround.png: eight views, from above, weapon drawn, far (fine_ bones hidden), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with her overrides (her "happy", "focused" and "determined" bare a fang grin), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 2: the horn and the arm stripes flare ember and glow; phase 3: glowing eyes), phases 2 and 3 at night
sheet.png: every clip of her own animation file as a filmstrip, each in the phase it belongs to
move_overhead_slam.gif, move_shoulder_charge.gif, move_ground_stomp.gif, move_gourd_swig.gif, move_grab_throw.gif (phase 2), move_meteor_club.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
move_crash_waves.gif: Crash Waves is the ultimate's followup only (move weight 0), so its film is the ultimate with Crash Waves starting at the end of the ultimate's active part, as the engine starts a followup
ultimate.gif: Iron Mountain Crash (the leap, the apex, the meteor drop, the stuck club pried out) and its followup Crash Waves
string.gif: her basic attack, the hammer style's string attack.1-3, then Meteor Club > Ground Stomp (its followup, 100%;
  this film starts the stomp after the whole meteor clip, as the shared followup films do; in game it starts at the end of
  the meteor's active part)
moments.gif: intro, phase.2 (the horn flares) and then state.rage as the transition ends, phase.3 (steam from the horn, glowing eyes), taunt, victory, last_stand (she never escapes, so there is no escape clip)
idles.gif: personal.1-3 over move.idle (the shoulder roll, a sip from the gourd, the club spin)
locomotion.gif: walk > run; charge_run > skid (a charger's traversal charge); crouch_jump > leap > land_heavy (a leaper); climb

Two views per frame: 3/4 front and her right side. Actions play over their base with the action controller's 2-tick
blend. The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
