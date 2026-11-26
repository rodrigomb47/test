Ilsabet Frostmere's own preview set (C4), written by `python3 tools/herogen/herogen.py hero-previews ilsabet_frostmere`
(`--check` re-renders the set and compares it byte for byte). She has one body, adult_f standard at 104% (HEROES_V2 3.6,
SLICE_PLAN 3.7), and every image shows her in that body only. She has no tail.

turnaround.png: eight views, from above, sword and shield drawn, far (fine_ bones hidden), phase 3 at night (the visor down, the standard on her back)
faces.png: the 12 moods of visual.json's table with her overrides (angry is stern: focused eyes over a frown; smug is a plain smile), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 2: the visored helm, its visor down, her hair under it; phase 3: the standard on her back and the frost-lit blade), phase 3 at night
sheet.png: every clip of her own animation file as a filmstrip, each in the phase it belongs to (sec.loop holds the visor down on its hinge)
move_shield_bash.gif, move_oath_strike.gif, move_rime_wall.gif, move_turning_sweep.gif, move_frost_lance_charge.gif (phase 2), move_frozen_standard.gif (phase 3), move_call_the_guard.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
ultimate.gif: Vow of Winter (the vow with the sword before her face, the sword raised over her head while the ice ring grows, swept down to the ground at the hit)
string.gif: the sword_shield string attack.1-3 (cut, cut, shield bash); she has no followups
moments.gif: intro (the salute, then the sword lowered: "Lower your weapon and I will lower mine"), phase.2 (the visor snaps down) and state.bulwark after it, phase.3 (the standard rises, the blade glows), taunt, victory, last_stand
idles.gif: personal.1-3 over move.idle, the sword and shield carried (the shield tap behind her, the salute with the fist over her heart, the glance back at her allies)
locomotion.gif: walk > run; charge_run > skid (a charger's traversal charge); jump > leap > land (no leaping trait); climb

Her visor stays down from phase 2 because her sec.loop, which the secondary controller plays all the time and after
every other controller, holds the helm's visor_hinge bone at +75 degrees; phase.2 swings the visor itself shut on it.
The stills (turnaround, phases, lineup) lay what sec.loop holds over the stance. Two views per frame: 3/4 front and her
right side. Actions play over their base with the action controller's 2-tick blend. The lineup of the heroes with a
source beside the test rig is ../lineup.png (`hero-previews --lineup`).
