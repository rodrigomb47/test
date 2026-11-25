Yugen Kagaribi's own preview set (C2), written by `python3 tools/herogen/herogen.py hero-previews yugen_kagaribi`
(`--check` re-renders the set and compares it byte for byte). He has one body, adult_m slim at 100% (HEROES_V2 3.6,
SLICE_PLAN 3.3), and every image shows him in that body only. His tails all grow from the base of his back and stay
behind and above his legs in every clip (the tail rule of `herogen check` covers all nine).

turnaround.png: eight views, from above, fan drawn, far (fine_ bones hidden), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with his overrides ("calm", "focused" and "angry" keep a thin smile or a flat mouth: he never shouts), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 1: three tails; phase 2: six fanned wide; phase 3: all nine, the middle three tipped with foxfire, glowing eyes), phases 2 and 3 at night
sheet.png: every clip of his own animation file as a filmstrip, each in the phase it belongs to (sec.loop, the lantern orbit, under every frame)
move_foxfire_volley.gif, move_lantern_ring.gif, move_fan_cut.gif, move_vanish.gif, move_mirage.gif (phase 2), move_foxfire_rain.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
ultimate.gif: Ninefold Lantern (the lantern ring stands up behind him as a wheel and turns; nine shots, one per hit tick)
string.gif: his basic attack (thrown foxfire, attack.ranged, twice), the focus string attack.1-3, then Fan Cut > Vanish
  (its followup, 80%) and Vanish > Foxfire Volley (its followup, 70%); these films start the followup after the whole
  first clip, as the shared followup films do
moments.gif: intro (a bow, the closed fan to his lips), phase.2 (six tails fan out), phase.3 (nine tails, the lanterns flare, his eyes light), taunt, victory, last_stand, escape (over the run)
idles.gif: personal.1-3 over move.idle (a laugh behind the sleeve, a lantern spun on one finger, a flick of the tail fan)
locomotion.gif: walk > run (a runner); run > sprint (his fox lope, G10's variant); crouch_jump > leap > land_heavy (a leaper); climb

The three paper lanterns orbit his chest (sec.loop, the secondary controller) under every film and sheet frame, as in
game. Two views per frame: 3/4 front and his right side. Actions play over their base with the action controller's
2-tick blend. The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
