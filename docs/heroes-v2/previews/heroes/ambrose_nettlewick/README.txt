Ambrose Nettlewick's own preview set (C3), written by `python3 tools/herogen/herogen.py hero-previews ambrose_nettlewick`
(`--check` re-renders the set and compares it byte for byte). He has one body, adult_m slim at 100% (HEROES_V2 3.6,
SLICE_PLAN 3.8), and every image shows him in that body only. He has no tail.

turnaround.png: eight views, from above, staff drawn, far (fine_ bones hidden), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with his overrides ("calm" smiles, "focused" keeps the focused eyes with the smile, "angry" frowns), a blink, both talk frames, a 3/4 view
phases.png: phases 1-3 front and back (phase 2: broom bristles at the staff's foot; phase 3: the hat's brim lifted, glowing eyes), phase 3 at night
sheet.png: every clip of his own file as a filmstrip, each in the phase it belongs to; the broom clips (move.takeoff, move.ride, move.fly_land and move.ride_cast over the ride) are drawn riding, the staff on the mount bone under him, as in game
move_potion_barrage.gif, move_cauldron.gif, move_hex_mark.gif, move_fume_step.gif, move_broom_hops.gif (phase 2), move_great_brew.gif (phase 3): one per move, over combat.idle, with the hit and cast ticks
ultimate.gif: Grand Experiment (the cauldron lifted high, the crow's wings spread, the brew flung at the sky at the hit, slow circles while the rain falls); drawn in phase 1 by the shared preview rule
string.gif: his thrown basic attack (a vial, attack.ranged, twice) and the staff string attack.1-3. Broom Hops follows
  itself (up to twice); the shared films leave out a move's followup of itself, so move_broom_hops.gif shows one hop
moments.gif: intro (the hat tipped, the glasses pushed up, a palm held out), phase.2 (the bristles sprout, a test hop on the staff), phase.3 (the brim flicked up, the eyes glow), taunt, victory (the notebook read back), last_stand, escape (clutching his hat, over the run)
idles.gif: personal.1-3 over move.idle (the notebook scribble, the vial shake, the crow pecking his hat)
locomotion.gif: walk > run; takeoff > ride > ride_cast (a vial from the broom) > ride > fly_land on his staff-broom, the
  crow flapping beside him; the glide on the broom (the core's move.glide, when his flight stamina runs out)

The crow's glances (sec.loop, the secondary controller) play under every film and sheet frame, as in game. A rider
never plays move.fly*: every flight state shows the ride. Two views per frame: 3/4 front and his right side. Actions
play over their base with the action controller's 2-tick blend. The lineup of the heroes with a source beside the test
rig is ../lineup.png (`hero-previews --lineup`).
