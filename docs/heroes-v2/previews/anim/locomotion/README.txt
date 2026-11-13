Mobility set previews (G10), written by `python3 tools/herogen/herogen.py anim-previews --locomotion`.

jumps.gif / jumps.png: Jumps and landings: crouch_jump (the tell) > leap > land; jump > air_jump (the flip) > land_roll; jump > land_heavy; wall_kick; mantle
climb_glide.gif / climb_glide.png: Climbing and gliding: climb_idle, climb_down, glide_start > glide > gust > land
flight.gif / flight.png: Flight with worn wings (feathered; their loops on the secondary controller): takeoff > fly > fly_fast > fly_hover > fly_land; winded; downed
ground.gif / ground.png: Runner and charger: sprint > skid, charge_run > skid; the generic dive (move.air_dive)
ride.gif / ride.png: Riding a carrier (a mounts/broom on the mount bone): takeoff > ride > ride_cast > fly_land
hero_natsuho.gif / hero_natsuho.png: Natsuho's cat twist (move.air_jump, variant cat_twist) (teen_f, chibi_f)
hero_yugen.gif / hero_yugen.png: Yugen's fox lope (move.sprint, variant fox_lope) (adult_m, chibi_m)
hero_kagenori.gif / hero_kagenori.png: Kagenori's crane leap (move.leap, variant crane_leap) (adult_m, chibi_m)
hero_ambrose.gif / hero_ambrose.png: Ambrose on his staff-broom (variant broom: side-saddle) (adult_m, chibi_m)

Core groups on style_tansy (chibi_f) and style_maren (adult_f); flight adds feathered wings, ride a mounts/broom (preview variants built in memory). One-shots and loops play as the base controller's queue (4-tick blends), actions over it (2 ticks), wing loops on the secondary controller (6 ticks).
