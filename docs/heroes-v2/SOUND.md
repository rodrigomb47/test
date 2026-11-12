# Heroes v2: sound

How heroes sound: the design, the catalogue format, the generator (`tools/sfxgen`), the content of the slice, and what
the engine side (task S2) has to do. Companion of `HEROES_V2.md` (sections 4.1, 4.8, 6.4, 6.13, 10.3, 12.14),
`SLICE_PLAN.md` section 3 and `MOBILITY_AND_WORK.md` sections 2.2 and 7.3.

Status, 2026-11-12: S1 done (this document, the generator, the shared cues and the eight slice heroes' sounds, the
previews). Nothing plays yet: S2 (Java) registers the events and plays them. The generated `.ogg` files, `sounds.json`
and the index are in `src/main/resources/assets/factionclash/`; the client loads `sounds.json` already, and an event
nobody registers or plays does nothing.

---

## 0. The owner's decision

On 2026-11-12 the owner listened to five sounds synthesised from code (a small brass bell jingle, chirpy voice blips,
a katana swing, a foxfire whoosh-sparkle, a heavy club slam) and said: *"These are good, you can also use minecraft
sounds when it makes more sense."* So:

- every hero sound is either **generated** by our own code (original, no samples) or a **vanilla sound event
  referenced by its id** (`minecraft:entity.cat.hiss`); vanilla `.ogg` files are never copied, transcoded or
  bundled; pitch, volume and layering of a vanilla event are fine;
- vanilla is used where it is the natural sound (a cat's hiss, a fox's blink, fire, ice and glass, metal and anvils,
  potions, explosions, armour, a goat horn, the mace); generated sounds cover what vanilla lacks (signature magic,
  voices, ultimate wind-ups, telegraph cues, phase stings, the crow);
- the five prototypes are in the catalogue as they were liked: Natsuho's `bell` and `voice`, Kagenori's
  `katana_swing`, Yugen's `foxfire_volley`, Benika's `club_slam` (recipes ported from the prototype script, same
  partials, sweeps, envelopes and mix).

## 1. Principles

1. **Readable combat.** Every telegraph class of HEROES_V2 6.4 has its own audible wind-up cue, distinct by pitch
   direction, envelope and timbre, so a player who is not looking still knows what is coming:

   | Class | Cue | How it sounds | Length |
   |---|---|---|---|
   | `light` | `hero.cue.telegraph.light` | a bright double tick, high and short (quiet: -25) | 0.15 s |
   | `heavy` | `hero.cue.telegraph.heavy` | a low gritty "whum" that rises and peaks just before the hit | 0.78 s |
   | `area` | `hero.cue.telegraph.area` | a wide fifth that pulses faster and faster, a ground rumble, a ringing top | 1.0 s |
   | `ultimate` | `hero.cue.telegraph.ultimate` | taiko and gong, a rising choir chord, a reversed cymbal into the release; heard 48 blocks away | 1.5 s (+ tail) |
   | grab (unblockable) | `hero.cue.telegraph.unblockable` | a dissonant tritone double stab with a metal ring (the purple decal's sound) | 0.7 s |
   | `dive` | `hero.cue.dive` | a falling whistle aimed at the landing decal | 0.85 s |

   `sfxgen check` measures the six cues against each other (spectral shape, length, where the peak sits) and notes
   any pair that comes too close. The hero's own wind-up sound plays together with the class cue: the cue says
   *what kind* of attack, the hero's sound says *which* attack.
2. **Heroes are recognisable by ear.** Each hero has a voice of its own (section 6) and a sound palette from its
   element, weapon and archetype: Natsuho is bells, claws and cat; Yugen cold foxfire, paper and fan; Benika iron,
   forge fire and a laugh; Hinae kagura bells, paper and claps; Kagenori steel rings, clicks and thunder; Tobimaru
   whispers, smoke and kunai; Ilsabet ice, shield and horn; Ambrose bubbles, glass and a crow.
3. **Vanilla where natural** (section 0), so heroes sit in Minecraft's world and players already know half of the
   sounds.
4. **Levels fit vanilla.** Loudness targets come from measured vanilla sounds (section 8): a hero hit is as loud as
   a player's critical hit, a slam as loud as a small explosion, a voice blip quieter than a villager.
5. **Player-like and honest.** Sounds only tell; they never decide anything. Decoys have no footsteps (6.11), a
   hero's steps are vanilla block steps at a hero-specific volume, invisible Tobimaru leaves audible smoke puffs.
6. **Small.** Every file costs about 3.4 KB of Vorbis headers before any audio, so variants are few, pitch
   randomisation does the rest, and later heroes reuse sounds through aliases (section 11).

## 2. Generated or vanilla: the rules

- Generated: anything that is the hero's identity or the mod's language: voices, signature magic, every telegraph
  and boss cue, ultimates, phase moments, stings, the hero's own swings and hits when their character matters.
- Vanilla: a sound Minecraft already has for exactly that thing. The slice uses 29 vanilla events (and the player's
  hurt and death sounds):

| Vanilla event | Used for |
|---|---|
| `entity.cat.hiss`, `entity.cat.purr`, `entity.cat.stray_ambient` | Natsuho's hiss wind-up, her purr, the midnight frenzy yowl |
| `entity.fox.teleport`, `entity.fox.screech` | Yugen's vanish, the scream under his phase 3 flare |
| `entity.player.attack.sweep`, `.strong`, `.knockback` | Ilsabet's sword, katana and blade hits, knockback hits |
| `item.mace.smash_ground` | Benika's club hits (her kit weapon is a mace) |
| `item.firecharge.use`, `entity.generic.explode`, `block.fire.extinguish` | fire under Benika's slams, the meteor blast, steam from her horn, her fire going out (snowball weak point) |
| `entity.generic.drink`, `entity.player.burp` | the gourd swig |
| `block.glass.break`, `block.anvil.place`, `block.iron_trapdoor.close` | ice and barriers shattering, the frozen standard planted, a visor snapping shut |
| `item.shield.block`, `item.armor.equip_iron`, `item.armor.equip_netherite` | shield bash and plant, Kagenori's helmet, Ilsabet's bulwark |
| `item.goat_horn.sound.5` ("call") | Ilsabet's call the guard and phase 3 |
| `entity.lightning_bolt.impact` | the flash along heaven split |
| `entity.breeze.wind_burst` | the phase transition shockwave (with a generated sub thump) |
| `entity.arrow.hit`, `block.wood.break` | kunai hits, the log of Tobimaru's log swap |
| `entity.splash_potion.throw`, `.break`, `block.brewing_stand.brew`, `entity.slime.squish_small` | Ambrose's vials, the brew, the cauldron, the venom puddle |
| `entity.player.hurt`, `entity.player.death` | every hero's hurt and death, pitched by `voice.hurt_pitch` |

A vanilla sound and a generated one can play together: a sound's **companions** (`with`, section 4.6) are other
events started with it, for example `vial_slow` = vanilla potion break + the generated slow-bubble tell, `frost_hit`
= generated ice + vanilla glass.

## 3. Event names

| Event | What |
|---|---|
| `factionclash:hero.cue.<name>` | the shared set: telegraph cues, boss beats, mobility (`herosrc/sfx/cue.sfx.json`) |
| `factionclash:hero.<hero id>.<name>` | a hero's own sounds (`herosrc/sfx/<hero id>.sfx.json`) |
| `factionclash:hero.voice.<hero id>` | a hero's voice blips (the sound named `voice` in its file) |

Names are lower case with `_` and `.` (`telegraph.heavy`, `club_slam`). Files: `sounds/hero/<set>/<name>[_<n>].ogg`
(dots become underscores; `_<n>` for variants). Subtitle keys: `subtitles.factionclash.<event>`. A fourth
namespace is reserved for the roster: `factionclash:hero.kit.<name>` (a shared set of element and style sounds,
section 11).

## 4. The catalogue: `herosrc/sfx/<set>.sfx.json`

One file per set (`cue` or a hero id). The generator reads all of them; `sfxgen check` validates them.

```json
{
  "format": 1,
  "set": "natsuho_komezaki",
  "title": "Natsuho Komezaki, the harbour cat (tsundere, fists, physical)",
  "hero": { ...the hero map, section 4.7... },
  "preview": ["voice", "bell", "claw", ...],
  "sounds": {
    "bell": {
      "kind": "quirk", "subtitle": "A small bell jingles", "loudness": -24, "variants": 2, "pitch": [0.98, 1.02],
      "vary": {"layers.0.f0": [1450, 1510], "layers.1.at": [0.09, 0.14]},
      "note": "Her choker bell (the owner's prototype)...",
      "layers": [
        {"type": "bell", "partials": "brass_small", "f0": 1480, "dur": 1.6, "decay": 0.8, "strike": 0.3},
        {"type": "bell", "partials": "brass_small", "f0": 1560, "dur": 1.6, "at": 0.11, "decay": 0.8, "gain": -2}
      ]
    },
    "hiss": {
      "kind": "windup", "subtitle": "Natsuho hisses",
      "vanilla": [{"event": "minecraft:entity.cat.hiss", "pitch": 1.15}]
    }
  }
}
```

### 4.1 Fields of a sound

| Field | Meaning |
|---|---|
| `kind` | sets the defaults below: `voice`, `emote`, `swing`, `cast`, `hit`, `heavy`, `windup`, `telegraph`, `sting`, `chime`, `mobility`, `foley`, `quirk`, `loop` |
| `subtitle` | English subtitle text (required; S2 writes it to the language file) |
| `plays` | `server` (the server plays it at the hero: everyone in range hears it) or `client` (an animation keyframe or client code plays it; nothing on the network) |
| `category` | the vanilla sound category (`hostile`, `neutral`, `voice`...), so players' volume sliders apply |
| `range` | attenuation distance in blocks (generated sounds; S2 registers the event with this fixed range) |
| `pitch`, `volume` | play-time random ranges `[low, high]` S2 picks from on every play |
| `loudness` | target momentary-max loudness (generated; defaults per kind, section 8) |
| `rate` | `auto` (22.05 kHz when less than -32 dB of the energy is above 9.5 kHz), `44100` or `22050` |
| `loop`, `length`, `loop_crossfade` | a seamless loop of `length` seconds (the end cross-faded into the start) |
| `with` | companions: `[{"sound": name or "set:name", "delay": 0-10 ticks}]`, at most 2, nesting at most 2 deep |
| `post` | `{trim, fade_in, fade_out}` overrides; `transients: true` declares deliberate clicks (crackle, knuckle cracks, debris) so the interior click rule skips the sound |
| `note` | what it is for (kept in the source only) |
| one of `layers` / `vanilla` / `alias` | the sound itself: generated (4.2), a vanilla reference (4.4) or an alias (4.5) |

Kind defaults (loudness, category, who plays it, range, longest length):

| Kind | Loudness | Category | Plays | Range | Max |
|---|---|---|---|---|---|
| `voice` | -24 | voice | client | 12 | 0.3 s |
| `emote` | -21 | voice | client | 16 | 2.0 s |
| `swing` | -21 | hostile | client | 16 | 1.2 s |
| `cast` | -20 | hostile | client | 16 | 2.5 s |
| `hit` | -17 | hostile | server | 16 | 1.6 s |
| `heavy` | -13 | hostile | server | 24 | 3.0 s |
| `windup` | -19 | hostile | server | 24 | 2.5 s |
| `telegraph` | -18 | hostile | server | 24 | 2.0 s |
| `sting` | -13 | hostile | server | 32 | 4.0 s |
| `chime` | -18 | hostile | server | 24 | 3.0 s |
| `mobility` | -17 | neutral | server | 16 | 2.0 s |
| `foley` | -27 | neutral | client | 12 | 2.0 s |
| `quirk` | -25 | neutral | client | 12 | 2.5 s |
| `loop` | -26 | hostile | client | 16 | 4.0 s |

### 4.2 Generated recipes

`layers` is a list of instruments mixed at their `at` time (seconds); every layer may also have `gain` (dB),
`pitch` (semitones, by resampling), `fx` (an effect chain), `seed` and `hard` (no 0.8 ms fade-in and 3 ms fade-out:
every layer gets them otherwise, so no layer starts on a step or stops dead). The sound's own `fx` chain runs on the
mix; then DC removal (2nd-order high-pass at 18 Hz), tail trim, fades, normalisation to the loudness target with a
soft peak limiter (-1.5 dBFS true peak), 22.05 kHz when the rate says so, and 256 samples of silence before and 1024
after (Vorbis pre-echo settles in the first; decoders that drop the last partial packet lose only the second).

Parameters marked *param* take a number, `[from, to]` (an exponential glide when both are positive),
`{"from", "to", "shape", "start", "end"}`, breakpoints `{"points": [[t, v, shape]...]}` or an LFO
`{"sine": {"mid", "depth", "rate"}}`. Envelopes (`env`) are ADSR `{a, d, s, r}`, `{"perc": [attack, decay]}`,
`{"swell": [rise, fall]}`, `{"arch": p}` (sin^p, the blip shape) or breakpoints.

| Instrument | Parameters |
|---|---|
| `tone` | `freq` (param), `wave` sine/tri/saw/square/pulse (band-limited), `harmonics` [[ratio, amp]], `vibrato` {rate, depth semitones, delay}, `unison` {voices, cents}, `env` |
| `fm` | `freq` (param), `ratio`, `index` (param), `vibrato`, `env` |
| `noise` | `colour` white/pink/brown/blue/violet, `filter` {mode lp/bp/hp/notch/peak, `f` (param: swept), `q`}, `env` |
| `bell` | `f0`, `partials` (brass_small, temple, chime, crystal, iron, gong, kagura, or [[ratio, amp, decay]]), `decay`, `strike`, `beat` (Hz), `bright`, `bloom`, `glide` (semitones), `env` |
| `membrane` | drum: `f0`, `drop`, `decay`, `stick`, `skin` |
| `whoosh` | swept band-pass noise: `f` path (param), `q`, `peak`, `air`, `body`, `flutter` {rate, depth}, `env` |
| `impact` | thump with a pitch drop `f` [start, end], `drop`, `body_decay`, `crunch` (+ `crunch_lp`, `crunch_poles`, `crunch_decay`), `click`, `debris` (+ `debris_f`, `debris_span`), `drive` |
| `crackle` | random grains: `rate` (param), `f` [lo, hi], `grain`, `q`, `env` |
| `shimmer` | beating sines: `f` range or `scale` + `base`, `count`, `rise` (param, semitones), `trem` |
| `sparkle` | granular pings: `count`, `scale` + `base` or `f`, `grain`, `glide`, `order` up/down, `span` |
| `vox` | voice: `source` glottal/chirp/buzz/breath/bell, `f0`, `contour` (semitones), `vowel` a/e/i/o/u/ae/uh/er/m/n/crow or [F1, F2, F3], `vowel_to`, `shift` (formant scale), `fq`, `tilt`, `breath`, `jitter`, `rough` {rate, depth}, `vibrato`, `attack`, `release`, `drive` |
| `pluck` | string by partials: `f0`, `decay`, `damp`, `inharm`, `pos`, `buzz` (sawari) |
| `brass` | detuned saws through an opening filter: `freq` (param), `cutoff`, `open`, `res`, `detune`, `env` |
| `clap`, `bubbles`, `click` | hand clap; rising sine bubbles (`rate`, `f`, `tau`, `chirp`); a short filtered transient with an optional `ring` |
| `group` | nested `layers` with their own `fx` |
| `use` | another generated sound of the catalogue (`sound`, optional `variant`), as processed |
| `silence` | `dur` |

Effects: `lp`, `hp`, `bp`, `notch`, `peak`, `lowshelf`, `highshelf` (a param frequency sweeps), `drive` (tanh, hard,
fold), `crush`, `reverb` (generated impulse response: `size`, `damp`, `mix`), `echo`, `trem`, `ring`, `env`, `gain`,
`speed`, `bend`, `reverse`, `fade`, `pad`, `len`.

### 4.3 Variants

`variants` (1-12) files per event; the game picks one at random each play (sounds.json), and S2 adds the play-time
`pitch` and `volume` ranges. `vary` spreads a value over the variants evenly (dotted paths into the recipe; the key
`pitch` shifts the whole sound in semitones); `each` lists explicit per-variant overrides (the voices' syllables).
Seeds come from SHA-256 of the event name, the variant and the layer path, never from Python's `hash()`.

Rule of thumb for the budget: 3 variants for a basic attack's swing and hit (they repeat every second), 2 for
frequent hits, 1 for everything else.

### 4.4 Vanilla references

`"vanilla": [{"event": "minecraft:...", "pitch": p, "volume": v, "weight": w}]` becomes a `"type": "event"` entry in
sounds.json. The game multiplies the referenced sound's own pitch and volume by these, keeps the vanilla sound's
attenuation (16 blocks x max(1, volume)), and clamps the final volume to 1. Several entries pick at random (Ambrose's
`brew_drop` is one potion break at three pitches). The id must exist in 1.21.1 (checked) and the final pitch must
stay within 0.5-2.0 (the client clamps).

### 4.5 Aliases

`"alias": [{"sound": "set:name", "pitch": p}]` makes an event that plays another catalogue sound (a `type: event`
entry pointing at `factionclash:...`): zero bytes. For later heroes: a batch hero's voice can be another voice at a
new pitch, a style's swing can serve many heroes.

### 4.6 Companions

`with` starts other events at the same place, `delay` ticks later (0-10). Used to layer vanilla and generated
(`charge_hit` = vanilla knockback + `thud`) and to chain short beats (`gourd_swig` + `burp` after 10 ticks). S2 plays
companions with the same position, each with its own pitch and volume ranges.

### 4.7 The hero map

The `hero` block tells S2 (and the animation authors of C1-C4) which sound belongs to which moment:

```json
"hero": {
  "voice": {"talk": "voice", "rate": 2, "melody": [0, 0, 2, 3, 5, -2], "jitter": 0.04, "fallback_blip": "xylophone",
            "hurt": {"event": "minecraft:entity.player.hurt", "pitch": 1.35}, "death": {...},
            "emotes": {"hmph": "hmph", "yelp": "flinch"}},
  "moves": {
    "basic": {"class": "light", "release": "claw", "impact": "claw_hit"},
    "pounce": {"class": "heavy", "windup": "pounce_wiggle", "release": "pounce_leap", "impact": "pounce_land", "end": "paw_lick"},
    "potion_barrage": {"class": "light", "release": "vial_throw", "impact": ["vial_poison", "vial_slow", "vial_weak"]},
    "great_brew": {"class": "area", "windup": "brew_windup", "loop": "brew_loop", "release": "brew_burst", "impact": "brew_drop", "broken": "brew_fizzle"},
    "midnight_frenzy": {"class": "ultimate", "windup": "frenzy_yowl", "release": "frenzy_sting"}
  },
  "states": {"frenzy": "frenzy_sting"}, "phases": {"2": "phase2", "3": "phase3"},
  "passives": {"nine_lives": "nine_lives"}, "weak_points": {"water": "flinch"},
  "mobility": {"takeoff": "broom_takeoff"}, "intro": "intro",
  "quirks": {"personal.1": "paw_lick", "idle": "bell"}, "steps": {"volume": 0.6, "with": "cue:step.heavy"}
}
```

Move slots: `windup` (server, at the wind-up's start, with the class cue), `release` (client keyframe where the
action starts: the swing, the throw, the leap), `impact` (server, each time damage or the zone pulse lands; a list
cycles per shot), `loop` (client, while a channel lasts), `end` (client keyframe in the recovery), `broken` (server,
when the channel is broken). The cue set's map (`"cues"`) names the shared events the same way.

## 5. Markers (HEROES_V2 4.8)

Animation recipes name sounds in two ways, both already read by the art generator (`tools/herogen/anim/recipes.py`):
a key's `"sound": "<id>"`, or a `sfx:<id>` part of a marker (`"hit;sfx:factionclash:hero.natsuho_komezaki.claw"`).
G3 writes them as GeckoLib `sound_effects` keyframes, at the pose's time plus the controller's blend (like every
event), so the sound fires on the frame where its pose shows.

- Ids are full: `minecraft:<event>` or `factionclash:hero.<set>.<name>` (a bare id is read as `factionclash:`).
- `sfxgen check` reads every recipe in `herosrc/anim` and `herosrc/heroes` and fails on an id that is neither a
  1.21.1 sound event nor in the catalogue. Today's markers (`attack.1`'s sweep, the fishing cast and reel) are
  vanilla and pass.
- Which marker goes where: the `release` and `end` slots of the hero map (section 4.7), the `phases` sounds in
  `phase.2` / `phase.3`, `intro` in `intro`, `quirks` in `personal.N`. A move's `windup` and `impact` are not
  markers: the server plays them (section 7), so they are heard by every player even when the client skips the
  animation (late trackers, LOD).
- One sound per keyframe time (the GeckoLib writer refuses two): layer at render time (`use`) or with companions.
- The style libraries keep vanilla sounds until a style kit exists (`_style_katana`'s `attack.1` keeps
  `entity.player.attack.sweep`; C3 may point Kagenori's own `attack` overrides at `katana_swing`).

## 6. Voices

The note-block voice of HEROES_V2 12.14 is replaced by generated blips: each hero has `hero.voice.<id>`, 6 syllable
variants (vowel and contour) of 0.06-0.16 s. While a line shows (the talking flag), the client plays one syllable
every `rate` ticks: a random variant, pitched by a random step of the hero's `melody` (semitones) and a `jitter`, the
last syllable before a `?` 12% higher, a pause at `,` `.` `!` `?` and between words. The old `voice` field stays:
`blip` and `pitch` are the fallback for a hero without a generated voice (an operator's custom hero, a batch hero
before its sounds exist), and `rate` is shared.

| Hero | Temperament | Old blip | Generated voice | f0 | Rate | Melody |
|---|---|---|---|---|---|---|
| Natsuho | tsundere | xylophone 1.35 | the owner's chirps: sine and two harmonics, a 30 Hz flutter, sin^1.6 arch; quick | 620 Hz | 2 | 0 0 +2 +3 +5 -2 |
| Yugen | schemer | flute 1.0 | soft and breathy, gliding contours, slow vibrato | 233 Hz | 3 | 0 0 -2 +2 -3 |
| Benika | battle maniac | guitar 1.1 | buzzy pulse with a little drive and rasp, punchy | 262 Hz | 2 | 0 0 +2 +4 -2 |
| Hinae | sweet-natured | bell 1.4 | bell-tinged, round vowels, gentle attacks | 587 Hz | 3 | 0 0 +2 +4 -1 |
| Kagenori | stoic | bass 0.85 | dark and level, no vibrato, short | 131 Hz | 3 | 0 0 0 -1 +1 |
| Tobimaru | lone wolf | hat 0.95 | a whisper with a trace of tone (quieter: -26) | 175 Hz | 3 | 0 0 -1 +1 |
| Ilsabet | righteous | chime 1.15 | clear and steady with a ring | 440 Hz | 3 | 0 0 +2 -2 +3 |
| Ambrose | eccentric genius | pling 1.05 | bright, a 7 Hz wobble, big jumps | 311 Hz | 2 | 0 +3 -3 +5 +7 -2 |

Emotes (`voice.emotes`) are short generated vocal sounds in the same timbre: Natsuho's `hmph`, yelp and yawn,
Yugen's sleeve laugh, Benika's laugh and roar, Hinae's humming, Kagenori's `hm` and kiai, Tobimaru's `hm`, Ilsabet's
shout, Ambrose's "a-HA!" (and his crow). Hurt and death stay vanilla player sounds at `hurt_pitch`.

`docs/heroes-v2/previews/sound/voices.mp3` plays the eight voices speaking the same sentence shape.

## 7. Who plays what

The server plays what every nearby player must hear and what decides nothing on the client (HEROES_V2 10.3:
`level.playSound`, positional, the hero's category); the client plays what follows an animation frame or a local
state. A *notification* is `ServerPlayer.playNotifySound` to each engaged player (heard in the head, not positioned),
for the beats that are about the fight, not a place.

| Moment | Sound | Who |
|---|---|---|
| A move's wind-up starts | `cues.telegraph.<class>` (grabs: `.unblockable`; `dive`: `.dive`) + the move's `windup` | server, at the hero |
| A hit lands, a zone pulses, a projectile hits | the move's `impact` | server, at the target or the impact point |
| The swing, throw, leap, cast | the move's `release` | client keyframe (`sfx:` marker) |
| A channel lasts (Hinae's rite, Ambrose's brew) | the move's `loop` | client, a looping sound following the hero |
| A channel is broken | `cues.channel_break` + the move's `broken` | server |
| Recovery details (paw lick, re-sheathe, kneel) | the move's `end` | client keyframe |
| A player parries (shield raised in the last 6 ticks) | `cues.parry` (over vanilla's own shield block) | server, at the player |
| A counter stance answers | `cues.counter` | server |
| Poise passes 75% (once per cycle) | `cues.poise.crack` | server |
| Poise breaks: stagger | `cues.poise.break`, then `cues.stagger` (2.4 s of dizzy stars) | server |
| Phase transition | `cues.phase` (notification), `cues.shockwave` and `cues.super_armor` at the hero; the hero's `phases.N` from its `phase.N` animation | server + client keyframe |
| Enrage | `cues.enrage` (notification and at the hero) | server |
| Boss intro (first engagement in a day) | `cues.intro` (notification, with the title); the hero's `intro` from its `intro` animation | server + client keyframe |
| Ultimate becomes ready (the boss bar's lightning) | `cues.ultimate_ready` | server, at the hero, quiet |
| A weak point triggers | `cues.weak_point` + the hero's `weak_points.<kind>` | server |
| Yield, spared | `cues.yield`, `cues.spared` | server |
| A charge hits a wall | `cues.charge_wall` | server |
| A decoy pops; a barrier takes a hit, breaks | `cues.decoy_pop`; `cues.barrier.hit`, `.break` | server |
| States, passives | `states.<name>`, `passives.<name>` | server (or the state animation's keyframe) |
| Take-off (0.6 s tell) | `cues.takeoff` + the hero's `mobility.takeoff` | server |
| Heavy landing (leap, dive, flight) | `cues.landing` | server |
| Gliding | `cues.glide` (a 2 s seamless loop) while `DATA_LOCO` says glide | client |
| Shot down | `cues.downed` | server |
| Footsteps | vanilla block steps x `steps.volume`, plus `steps.with` (the heavy step thud) for heavy heroes | server (the entity's own step sound) |
| Talking | the voice blips | client |
| Idles | `quirks.*` from `personal.N` animations | client keyframe |

## 8. Loudness and attenuation

Loudness is measured as the loudest 400 ms window of the K-weighted signal (BS.1770 filters, no gating; short sounds
are zero-padded, so a blip measures quieter than a tone, as it sounds), "Lm" below. Vanilla sounds measured with the
same meter from the 1.21.1 assets (in memory, never copied), multiplied by their own sounds.json volume:

| Vanilla | Lm | | Ours (kind target) |
|---|---|---|---|
| `block.stone.step` | -34 | | foley -27, quirk -25 |
| `entity.villager.ambient` | -25 | | voice -24 (Tobimaru -26) |
| `entity.player.attack.sweep` | -19 | | swing -21 |
| `entity.cat.hiss` | -19.5 | | emote -21, cast -20 |
| `entity.player.hurt`, `.attack.crit` | -20, -18 | | hit -17 |
| `entity.evoker.prepare_attack` | -15 | | windup -19, telegraph -18 (ultimate -14) |
| `entity.player.levelup` | -14 | | sting -13, chime -18 |
| `item.shield.block`, `entity.generic.explode` | -13, -10 | | heavy -13 (the biggest -11) |

Every generated file lands within 1 dB of its target (or up to 3 dB under when the peak limiter holds a transient
back), with a true peak under -0.3 dBFS after decoding.

Attenuation: generated sounds carry their `range` as the sounds.json `attenuation_distance` (voice and foley 12,
swings and hits 16, heavy, wind-ups and telegraphs 24, stings 32, ultimate wind-ups and releases 48), and S2
registers each event with that fixed range so the server sends it that far. Vanilla references keep vanilla's 16
blocks, more with a play volume above 1 (the goat horn plays at volume 2: 32 blocks).

## 9. Checks (`sfxgen check`)

Errors fail; notes advise. Audio rules run on the SHIPPED files (decoded by ffmpeg).

- Format: fields, kinds, names, `each` lengths, exactly one of layers / vanilla / alias, subtitle text present.
- References: every `use`, companion and alias resolves; no use cycles; companions nest at most 2 deep and never
  loop; vanilla ids exist in 1.21.1 (from the game's own `sounds.json` in the Gradle asset cache, or
  `tools/sfxgen/vanilla_sound_ids.txt`, a names-only list written by `sfxgen vanilla-ids`); final pitch within
  0.5-2.0.
- Markers: every `sound` key and `sfx:` part in `herosrc/anim` and `herosrc/heroes` names a vanilla or catalogue event.
- Hero maps: a voice (4+ syllables of at most 0.25 s, a rate of 2-4, a melody within an octave, a note-block
  fallback), valid hurt and death ids, every slot names a sound, move classes valid; notes for heavy, area and
  ultimate moves without a hero wind-up, moves with neither release nor impact, a missing phase sound, and (once
  `herosrc/heroes/<id>.hero.json` exists) moves of the hero file without sounds.
- Cue map: all of telegraph light, heavy, area, ultimate, unblockable, dive, parry, counter, poise crack and break,
  stagger, phase, shockwave, enrage, intro, yield, spared, take-off, landing, glide, decoy pop, weak point, channel
  break.
- Files: every expected file exists and nothing stale; sounds.json and the index are current; mono; 44.1 or
  22.05 kHz; length within the kind's limit (loops exactly `length`); the first and last sample under -45 dBFS; a
  fade-in and a fade-out (the content's first 0.5 ms and last 1 ms well under its peak); no clicks inside (a burst of
  high-frequency energy 30 times the 30 ms around it that is neither an onset nor part of a texture; loops checked
  across the seam); true peak under -0.3 dBFS; DC under 0.003; loudness on target and within -34..-9; no more than
  18 dB lost without bass (inaudible on laptop speakers; a note from 12 dB).
- Telegraph cues sound different from each other (note); vanilla references play near their kind's loudness (note,
  measured from the game files); the jar budget (section 11).

## 10. Tools

`python3 tools/sfxgen/sfxgen.py <command>` (Python 3.10+, NumPy, Pillow, ffmpeg with libvorbis and libmp3lame;
nothing else):

| Command | What |
|---|---|
| `build [--check]` | render, encode and write the `.ogg` files, sounds.json and the index (about 50 s on 4 cores); `--check` compares instead |
| `check [-v] [--fast]` | section 9 (`--fast` skips decoding) |
| `preview [sets]` | the owner's previews (section 12) |
| `render <set or event>... [--wav] [--mp3]` | render to `build/sfxgen/` with a sheet and the measurements, for authoring |
| `measure <file or minecraft:id>...` | measure a file or a vanilla event (decoded in memory) |
| `subtitles` | the `en_us` subtitle lines for S2 |
| `stats` | events, files and kilobytes per set |
| `vanilla-ids` | rewrite `vanilla_sound_ids.txt` from the game artifacts |

Tests: `python3 -m unittest discover tools/sfxgen/tests` (25: byte-identical builds twice and in another process,
the click, edge, clipping, DC, loudness and small-speaker rules catching broken sounds, the format, variants, loops,
sounds.json and the index, and the committed catalogue passing its rules).

**Determinism.** The same recipes, NumPy and ffmpeg/libvorbis give byte-identical files (ffmpeg's `+bitexact` fixes
the Ogg serial number and drops the version tag). Another libvorbis may encode differently; then `build --check`
reports the files as different and a rebuild is the fix.

Code: `dsp.py` (parameters, envelopes, oscillators, noise, filters, effects, limiter, loudness normalisation),
`synth.py` (instruments), `recipe.py` (format, variants, rendering), `analyze.py` (measurements), `codec.py`
(ffmpeg), `vanilla.py` (ids and measurement), `build.py`, `check.py`, `preview.py` and `previews.py`, `cli.py`.

## 11. Jar size

Each Vorbis file carries about 3.4 KB of codebook headers whatever its length (a 70 ms blip is 3.6 KB), so the file
count matters more than the seconds. The slice as built: 296 files, 2.09 MB (cue set 302 KB, heroes 176-245 KB each).
Budgets in `check` (errors): the cue set 320 KB, a slice hero 256 KB, the slice 2.4 MB.

For the 200-hero roster that rate would be about 45 MB, so batch heroes follow a different rule:

- a shared **kit** set (`herosrc/sfx/kit.sfx.json`, events `hero.kit.<name>`), started by moving the slice's generic
  element and style sounds into it (fire wave, frost hit, chain zap, talisman throw, club swing, blade swish, smoke
  puff...), the slice heroes keeping aliases with their old names;
- per batch hero at most about 24 KB of its own files: an ultimate wind-up and release, one signature sound, its
  phase moments made from kit sounds with `use`; the voice either its own 4 syllables or an alias of an existing
  voice at a new pitch; moves from the kit, vanilla and aliases;
- that gives about 0.6 MB for the cues and kit, 2.1 MB for the slice and 4.6 MB for 192 heroes: roughly 7 MB of
  sound. HEROES_V2 11 budgets the whole jar at 12 MB without sound; the owner decides between a larger jar (about
  19 MB) and fewer per-hero sounds (open item).

## 12. Previews for the owner

`docs/heroes-v2/previews/sound/`: per set an MP3 of its key sounds (first variant, short gaps; a hero's starts with
a spoken sentence) and a spectrogram sheet (log frequency 40 Hz-20 kHz, waveform, length, loudness, peak, rate; the
vanilla references listed by id), plus `voices.mp3`. Vanilla sounds are not in the MP3s (they are Mojang's audio;
the sheets name them, and they sound in the game as they always do).

## 13. Subtitles

Every event has `subtitles.factionclash.<event>` and an English text in its recipe. S2 merges the output of `sfxgen
subtitles` into `en_us.json`. Companions repeat their main sound's subtitle, so the overlay shows one line.

## 14. Licence

Every generated sound is original: synthesised from code in this repository (oscillators, noise and filters), with
no samples or recordings of any kind, under the mod's licence. Vanilla sounds are referenced by their event ids and
played by the game from the player's own Minecraft assets; no vanilla audio is copied, transcoded or redistributed
(the previews contain generated sounds only, and `vanilla_sound_ids.txt` lists names only).

## 15. S2: the engine side (mechanical)

1. **Registration** (`hero/HeroSounds`, common): read `assets/factionclash/sounds/hero_sfx.json` from the mod file
   (as `HeroRegistry` reads `visual.json`) in the mod constructor and register every key of `events` in a
   `DeferredRegister<SoundEvent>`: `SoundEvent.createFixedRangeEvent(id, range)` for generated events (range from
   the index) and `createVariableRangeEvent(id)` for vanilla references and aliases. `sounds.json` needs nothing.
2. **Subtitles**: merge `sfxgen subtitles` into `assets/factionclash/lang/en_us.json`.
3. **Playing** (`HeroSounds.play(level, pos, eventId, source)`): look the event up in `events`, pick pitch and
   volume uniformly from their ranges, multiply the volume by the `heroes.soundVolume` config, map `category` to
   `SoundSource`, `level.playSound(null, x, y, z, event, source, volume, pitch)`; then each `with` companion after
   its `delay` ticks (a small per-level queue ticked by the server tick event), recursively. A notification is
   `ServerPlayer.playNotifySound` to each engaged player.
4. **Hooks** (server): the move runner's wind-up start (class cue from `cues.telegraph` + `heroes.<id>.moves.<move>.windup`),
   damage application and zone pulses (`impact`; a list cycles per shot), channel breaks (`broken` +
   `cues.channel_break`); the boss controller's intro, phase, poise, stagger, parry, counter, enrage, yield, spare,
   ultimate ready, weak point, wall stun, decoy, barrier beats (section 7); `HeroMovement`'s take-off, heavy landing
   and downing; `HeroEntity.playStepSound` (vanilla step x `steps.volume`, plus `steps.with`); `getHurtSound`,
   `getDeathSound` and `getVoicePitch` from `voice.hurt` / `voice.death`. A unit test asserts every slot of every hero
   map resolves to a registered event.
5. **Client**: a GeckoLib sound keyframe handler on the three controllers (`effect` is the event id; ranges from the
   index, 1.0 for vanilla ids it does not list; companions through a client queue); `HeroVoice` (section 6: blips
   per syllable from the speech bubble's text while the talking flag is set, the note-block fallback when the
   index has no voice for the hero, the `heroes.voiceBlips` config); a looping `AbstractTickableSoundInstance`
   that follows the hero for `loop` slots and `cues.glide` (start and stop with the action code or `DATA_LOCO`);
   Tobimaru's `quirks.invisible_step` at his steps while he is invisible.
6. **Config** (`[heroes]`): `soundVolume` (0.0-2.0, default 1.0), `voiceBlips` (true), `telegraphCues` (true; off
   leaves the hero's own wind-up sounds).
7. **Verification**: GameTests that a wind-up plays its class cue and the hero's wind-up (a test hook records
   plays), that a decoy plays no steps, and a listening pass by the owner on the real client (V2).

## 16. Open items

- **Jar budget for the roster** (section 11): a larger jar or fewer sounds per batch hero; the owner decides when
  batch 1 starts.
- **In game**: loudness against real vanilla mixes and distances, the voice rates with real dialogue, and the
  telegraph cues in a crowded fight are judged in the S2 verification and the owner's listening pass.
- The kit set and the style libraries' own swing sounds come with batch 1 and G8.
- C1-C4 put the `release`, `end`, phase, intro and quirk sounds into the animation recipes as markers (section 5).
