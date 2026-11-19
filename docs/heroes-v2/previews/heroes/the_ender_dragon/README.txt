The Ender Dragon's own preview set (B5, the look gate), written by `python3 tools/herogen/herogen.py hero-previews
the_ender_dragon` (`--check` re-renders the set and compares it byte for byte). She has one body, adult_f standard at
108% (1.97 blocks; BOSS_HEROES 5.2, HEROES_V2 3.6), and every image shows her in that body only. Her kit is not written
yet (`"kit": "pending"`): there are no move clips, so no move GIFs and no ultimate.gif; they come with the move step,
against the B1 kit fixture.

turnaround.png: eight views, from above, the claws on her hands (worn: they are her hands), far (fine_ bones hidden), phase 3 at night (glow layer); the wings folded as at rest
wings.png: her wings folded at rest and under her own fold loop (as on the ground in game), spread on the ground, and in flight: the glide (move.glide under the glide loop), the top and the bottom of the beat (move.fly under the flap loop), from the front, 3/4, the side, behind and above
faces.png: the 12 moods of visual.json's table with her overrides (her "happy" a closed-eye smile, her "angry" narrowed slits and an open mouth whose throat glows purple, her "flustered" without a blush), a blink, both talk frames, a 3/4 view; cat-slit purple irises in the glow mask, the cheek scales, both fangs
phases.png: phases 1-3 front and back (phase 2: the veins of the wing membranes and the horn tips glow; phase 3: her eyes and her dorsal spines glow end-stone pale), phase 3 at night
sheet.png: every clip of her own animation file (her variant's flight set, her perch, sec.loop, the quirks and the moments) as a filmstrip, each in the phase it belongs to, with the wing loop its mode plays
string.gif: the fists style's string attack.1-3 with her talons (her own claw string comes with the kit)
moments.gif: intro (she lands with her wings spread, folds them, tilts her head), phase.2 (wings spread, the roar), phase.3 (her eyes and spines ignite, a puff of dragon's breath), last_stand (no code yet; she fights to the death, so there is no escape clip)
idles.gif: personal.1-3 over move.idle (she preens her left wing, curls her tail round her feet, gazes up at the sky)
locomotion.gif: walk > run, takeoff (the tell: the wings half open and rise, then the push) > fly > fly_fast (wings half swept back) > fly_hover > fly_land (the wings flared to brake), glide > gust > glide > circle (banking round her target, BOSS_HEROES 5.6), rest.perch (perched on a high point, wings half mantled)

Her wings are folded at rest (the part's own bones), lying over her long hair; her flight clips (the dragon_wings
variant) open them, while the wings controller's loops beat or hold them on wing_r and wing_l, as in game: her own
sec.wings_fold on the ground (the wings flat over her hair, a breath and a ruffle; the shared loop would turn them in
across each other behind her head), the shared flap loop in powered flight with its take-off and landing, the shared
glide loop in the glide and the circle. The Spirecore pendant turns on
sec.loop under every frame. Two views per frame: 3/4 front and her right side. Actions play over their base with the
action controller's 2-tick blend. Spirecore at her collarbone is drawn here (she comes with her signature in her one
slot); in game it shows only while she wears it (visual.json bones.artifacts).
The lineup of the heroes with a source beside the test rig is ../lineup.png (`hero-previews --lineup`).
