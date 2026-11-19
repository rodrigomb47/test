The Wither's own preview set (B5, the look gate), written by `python3 tools/herogen/herogen.py hero-previews the_wither`
(`--check` re-renders the set and compares it byte for byte). She has one body, adult_f slim at 104% (BOSS_HEROES 4.2,
HEROES_V2 3.6), and every image shows her in that body only. Her kit is not written yet (`"kit": "pending"`): there are
no move clips, so no move GIFs and no ultimate.gif; they come with the move step, against the B1 kit fixture.

turnaround.png: eight views, from above, weapon drawn (the wither-rose sceptre in her right hand), far (fine_ bones hidden: the ribcage stays), phase 3 at night (glow layer)
faces.png: the 12 moods of visual.json's table with her overrides (her "happy" is a thin half-lidded smile, her "angry" blazing eyes, never a blush), a blink, both talk frames, a 3/4 view; pupil-less soul-blue irises in the glow mask, the socket shadows under her eyes
phases.png: phases 1-3 front and back (phase 2, the wither armour: the rib stays, the spine, the crown's gem and the hem glow soul blue; phase 3: the familiars are gone, her ash hair tips burn with soul fire, her eyes blaze), the rebirth look (the gown and the soul-fire accents blaze blue-white, the_wither_rebirth.png), phase 3 at night
sheet.png: every clip of her own animation file as a filmstrip, each in the phase it belongs to (sec.loop, the familiars' bob, over the combat stance; the rebirth in its own look)
string.gif: the focus style's string attack.1-3 with her sceptre (her own basic attack, a thrown skull, comes with the kit)
moments.gif: intro (the familiars cackle, the sceptre levelled), phase.2 (the wither armour flares), rebirth (no code yet: she sinks into the soul sand, curled over, and rises blazing blue-white), phase.3 (she tears the skulls away), last_stand (no code yet; she fights to the death, so there is no escape clip)
idles.gif: personal.1-3 over move.idle (the familiars bicker and snap their jaws, she turns a wither rose drawn out of her left hand, a slow half-turn with her arms folded)
locomotion.gif: walk > run (her drift and fast glide: she never walks), hover > drift (MOBILITY_AND_WORK 1.11, aliases of her idle and drift), takeoff (the tell: the gown's hem lifts) > fly > fly_fast > fly_hover > fly_land (her smoke gown is the carrier), glide

She floats a hand's breadth (4 px) above the ground in every idle and move; her feet never touch it (the rebirth's
sink into the soul sand is the one exception). The familiars' bob (sec.loop, the secondary controller) plays under
every film and sheet frame, as in game. Two views per frame: 3/4 front and her right side. Actions play over their
base with the action controller's 2-tick blend. Hollow Star's brooch at her collar is drawn here (she comes with her
signature in her one slot); in game it shows only while she wears it (visual.json bones.artifacts).
The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
